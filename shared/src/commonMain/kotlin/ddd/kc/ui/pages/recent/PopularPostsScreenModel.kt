package ddd.kc.ui.pages.recent

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.QueryState
import ddd.kc.data.network.PageInfo
import ddd.kc.data.network.PopularInfo
import ddd.kc.data.network.PopularPage
import ddd.kc.data.network.PopularProps
import ddd.kc.data.network.toQueryError
import ddd.kc.data.repository.PostRepository
import ddd.kc.data.repository.awaitData
import ddd.kc.ui.state.pageInfoForOffset
import ddd.kc.utils.logging.KcLog
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth

private const val PAGE_SIZE = 50
private const val SERVER_WEEK_START_ISO_DAY_NUMBER = 2
private val popularLog = KcLog.withTag("PopularPostsScreenModel")

enum class PopularPeriod(val apiValue: String, val label: String) {
  DAY("day", "Day"),
  WEEK("week", "Week"),
  MONTH("month", "Month"),
}

data class PopularPostsState(
    val result: QueryState<PopularPage> = QueryState(isLoading = true),
    val posts: List<Post> = emptyList(),
    val period: PopularPeriod = PopularPeriod.DAY,
    val date: String? = defaultPopularDate(PopularPeriod.DAY),
    val info: PopularInfo? = null,
    val props: PopularProps? = null,
    val startOffset: Int = 0,
    val offset: Int = 0,
    val pageInfo: PageInfo? = null,
    val visibleOffset: Int = startOffset,
    val autoPrependArmed: Boolean = startOffset <= 0,
    val hasMore: Boolean = true,
    val isLoadingPrevious: Boolean = false,
    val isLoadingMore: Boolean = false,
    val prependErrorMessage: String? = null,
    val appendErrorMessage: String? = null,
) {
  val isLoading: Boolean
    get() = result.isLoading

  val errorMessage: String?
    get() = result.error?.message

  val visiblePageInfo: PageInfo?
    get() = pageInfoForOffset(pageInfo, visibleOffset, PAGE_SIZE)

  val canAutoLoadPrevious: Boolean
    get() = startOffset > 0

  val canShiftPrevious: Boolean
    get() = canShiftPopularDate(this, delta = -1)

  val canShiftNext: Boolean
    get() = canShiftPopularDate(this, delta = 1)
}

class PopularPostsScreenModel(
    private val postRepo: PostRepository,
) : StateScreenModel<PopularPostsState>(PopularPostsState()) {

  private var platform = Platform.PAWCHIVE

  fun load(platform: Platform, forceRefresh: Boolean = false) {
    val platformChanged = this.platform != platform
    this.platform = platform
    val state = mutableState.value
    if (!forceRefresh && !platformChanged && state.posts.isNotEmpty()) return
    mutableState.value =
        state.copy(
            result =
                state.result.copy(
                    isLoading = state.posts.isEmpty(),
                    isRefreshing = state.posts.isNotEmpty(),
                    error = null,
                ),
            appendErrorMessage = null,
            prependErrorMessage = null,
        )
    screenModelScope.launch { loadPage(offset = 0, forceRefresh = forceRefresh, replace = true) }
  }

  fun loadMore() {
    val state = mutableState.value
    if (state.isLoading || state.isLoadingMore || !state.hasMore) return
    mutableState.value = state.copy(isLoadingMore = true, appendErrorMessage = null)
    screenModelScope.launch {
      loadPage(offset = state.offset, forceRefresh = false, replace = false)
    }
  }

  fun loadPrevious() {
    val state = mutableState.value
    if (
        !state.canAutoLoadPrevious ||
            state.isLoading ||
            state.result.isRefreshing ||
            state.isLoadingPrevious ||
            state.isLoadingMore
    )
        return
    mutableState.value = state.copy(isLoadingPrevious = true, prependErrorMessage = null)
    val offset = (state.startOffset - PAGE_SIZE).coerceAtLeast(0)
    screenModelScope.launch {
      loadPage(offset = offset, forceRefresh = false, replace = false, prepend = true)
    }
  }

  fun jumpToPage(page: Int) {
    val state = mutableState.value
    val targetPage = page.coerceIn(1, state.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    mutableState.value =
        state.copy(
            result =
                state.result.copy(
                    isLoading = state.posts.isEmpty(),
                    isRefreshing = state.posts.isNotEmpty(),
                    error = null,
                ),
            startOffset = offset,
            visibleOffset = offset,
            autoPrependArmed = offset <= 0,
            isLoadingMore = false,
            isLoadingPrevious = false,
            appendErrorMessage = null,
            prependErrorMessage = null,
        )
    screenModelScope.launch {
      loadPage(offset = offset, forceRefresh = false, replace = true, rollbackState = state)
    }
  }

  fun onVisiblePostIndex(firstVisiblePostIndex: Int) {
    val state = mutableState.value
    if (state.posts.isEmpty()) return
    val relativeIndex = firstVisiblePostIndex.coerceAtLeast(0).coerceAtMost(state.posts.lastIndex)
    val absoluteOffset = state.startOffset + relativeIndex
    if (state.visibleOffset == absoluteOffset) return
    mutableState.value = state.copy(visibleOffset = absoluteOffset)
  }

  fun selectPeriod(period: PopularPeriod) {
    val state = mutableState.value
    val date = state.date ?: state.info?.date ?: state.props?.today
    submit(date = date, period = period)
  }

  @Suppress("UNUSED_PARAMETER")
  fun selectBoundaryDate(boundary: PopularDateBoundary, date: String) {
    submit(date = date, period = mutableState.value.period)
  }

  fun shift(period: PopularPeriod, slot: PopularNavSlot) {
    val triple = currentTriple(period) ?: return
    val date =
        when (slot) {
          PopularNavSlot.PREV -> triple.first
          PopularNavSlot.CURRENT -> triple.second
          PopularNavSlot.NEXT -> triple.third
        } ?: return
    submit(date = date, period = period)
  }

  fun shiftManual(unit: PopularDateShiftUnit, delta: Int) {
    val base =
        mutableState.value.date
            ?: mutableState.value.info?.date
            ?: mutableState.value.props?.today
            ?: return
    val next = shiftIsoDate(base, unit, delta) ?: return
    submit(date = next, period = mutableState.value.period)
  }

  private fun submit(date: String?, period: PopularPeriod) {
    val normalizedDate = normalizePopularBaseDate(date, period)
    mutableState.value =
        mutableState.value.copy(
            date = normalizedDate,
            period = period,
            posts = emptyList(),
            startOffset = 0,
            offset = 0,
            pageInfo = null,
            visibleOffset = 0,
            autoPrependArmed = true,
            hasMore = true,
            result = QueryState(isLoading = true),
            isLoadingPrevious = false,
            isLoadingMore = false,
            prependErrorMessage = null,
            appendErrorMessage = null,
        )
    screenModelScope.launch { loadPage(offset = 0, forceRefresh = false, replace = true) }
  }

  private suspend fun loadPage(
      offset: Int,
      forceRefresh: Boolean,
      replace: Boolean,
      prepend: Boolean = false,
      rollbackState: PopularPostsState? = null,
  ) {
    val state = mutableState.value
    runCatching {
          postRepo
              .observePopularPostsPage(
                  date = normalizePopularBaseDate(state.date, state.period),
                  period = state.period.apiValue,
                  offset = offset,
                  forceRefresh = forceRefresh,
              )
              .awaitData()
        }
        .onSuccess { page ->
          val firstPage = replace || (offset == 0 && !prepend)
          val merged =
              when {
                firstPage -> page.posts
                prepend -> (page.posts + mutableState.value.posts).distinctBy { it.id }
                else -> (mutableState.value.posts + page.posts).distinctBy { it.id }
              }
          val nextStartOffset =
              when {
                firstPage -> offset
                prepend -> offset
                else -> mutableState.value.startOffset
              }
          val nextOffset =
              when {
                firstPage -> offset + page.posts.size
                prepend -> mutableState.value.offset
                else -> offset + page.posts.size
              }
          mutableState.value =
              mutableState.value.copy(
                  posts = merged,
                  result = QueryState(data = page),
                  info = page.info,
                  props = page.props,
                  date = canonicalPopularDate(page.info, state.period, state.date),
                  startOffset = nextStartOffset,
                  offset = nextOffset,
                  pageInfo = if (firstPage) page.pageInfo else mutableState.value.pageInfo,
                  visibleOffset =
                      when {
                        firstPage -> nextStartOffset
                        prepend -> mutableState.value.visibleOffset
                        else -> mutableState.value.visibleOffset
                      },
                  autoPrependArmed =
                      when {
                        firstPage -> nextStartOffset <= 0
                        prepend -> true
                        else -> mutableState.value.autoPrependArmed
                      },
                  hasMore = page.pageInfo?.hasNext ?: (page.posts.size >= PAGE_SIZE),
                  isLoadingPrevious = false,
                  isLoadingMore = false,
                  prependErrorMessage = null,
                  appendErrorMessage = null,
              )
          popularLog.i {
            "热门Posts -> 加载成功(period=${state.period.apiValue},date=${state.date},offset=$offset,count=${page.posts.size})"
          }
        }
        .onFailure {
          popularLog.e(it) {
            "热门Posts -> 加载失败(period=${state.period.apiValue},date=${state.date},offset=$offset)"
          }
          mutableState.value =
              if (replace && rollbackState != null) {
                rollbackState.copy(
                    result =
                        rollbackState.result.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = it.toQueryError(),
                        ),
                    isLoadingPrevious = false,
                    isLoadingMore = false,
                    prependErrorMessage = null,
                    appendErrorMessage = null,
                )
              } else {
                mutableState.value.copy(
                    result =
                        mutableState.value.result.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = it.toQueryError(),
                        ),
                    isLoadingPrevious = false,
                    isLoadingMore = false,
                    prependErrorMessage = if (prepend) it.message else null,
                    appendErrorMessage = if (!prepend && offset != 0) it.message else null,
                )
              }
        }
  }

  private fun currentTriple(period: PopularPeriod): Triple<String?, String?, String?>? {
    val nav = mutableState.value.info?.navigationDates ?: return null
    val list =
        when (period) {
          PopularPeriod.DAY -> nav.day
          PopularPeriod.WEEK -> nav.week
          PopularPeriod.MONTH -> nav.month
        }
    if (list.isEmpty()) return null
    return Triple(list.getOrNull(0), list.getOrNull(2), list.getOrNull(1))
  }
}

enum class PopularNavSlot {
  PREV,
  CURRENT,
  NEXT,
}

enum class PopularDateBoundary {
  START,
  END,
}

enum class PopularDateShiftUnit {
  DAY,
  WEEK,
  MONTH,
}

private fun normalizePopularBaseDate(date: String?, period: PopularPeriod): String? {
  val baseDate = date?.toLocalDateOrNull() ?: return null
  val cappedBaseDate = minOf(baseDate, latestAllowedPopularDate())
  val normalizedDate =
      when (period) {
        PopularPeriod.DAY -> cappedBaseDate
        PopularPeriod.WEEK -> cappedBaseDate.startOfServerWeek()
        PopularPeriod.MONTH -> cappedBaseDate.startOfMonth()
      }
  return normalizedDate.toString()
}

private fun canonicalPopularDate(
    info: PopularInfo,
    period: PopularPeriod,
    fallbackDate: String?,
): String? {
  val actualDate = info.minDate ?: info.date ?: fallbackDate
  return normalizePopularBaseDate(actualDate, period)
}

private fun shiftIsoDate(date: String, unit: PopularDateShiftUnit, delta: Int): String? {
  val baseDate = date.toLocalDateOrNull() ?: return null
  val shiftedDate =
      when (unit) {
        PopularDateShiftUnit.DAY -> baseDate.plus(delta, DateTimeUnit.DAY)
        PopularDateShiftUnit.WEEK -> baseDate.plus(delta, DateTimeUnit.WEEK)
        PopularDateShiftUnit.MONTH -> baseDate.plus(delta, DateTimeUnit.MONTH)
      }
  if (shiftedDate > latestAllowedPopularDate()) return null
  return shiftedDate.toString()
}

private fun defaultPopularDate(period: PopularPeriod): String =
    normalizePopularBaseDate(latestAllowedPopularDate().toString(), period)
        ?: latestAllowedPopularDate().toString()

private fun canShiftPopularDate(state: PopularPostsState, delta: Int): Boolean {
  val baseDate =
      state.date ?: state.info?.date ?: state.props?.today ?: defaultPopularDate(state.period)
  val normalizedBase =
      normalizePopularBaseDate(baseDate, state.period)?.toLocalDateOrNull() ?: return false
  val navDates =
      when (state.period) {
            PopularPeriod.DAY -> state.info?.navigationDates?.day
            PopularPeriod.WEEK -> state.info?.navigationDates?.week
            PopularPeriod.MONTH -> state.info?.navigationDates?.month
          }
          .orEmpty()
          .mapNotNull { normalizePopularBaseDate(it, state.period)?.toLocalDateOrNull() }
  if (navDates.isNotEmpty()) {
    return if (delta < 0) {
      navDates.any { it < normalizedBase }
    } else {
      navDates.any { it > normalizedBase }
    }
  }
  val unit =
      when (state.period) {
        PopularPeriod.DAY -> PopularDateShiftUnit.DAY
        PopularPeriod.WEEK -> PopularDateShiftUnit.WEEK
        PopularPeriod.MONTH -> PopularDateShiftUnit.MONTH
      }
  return shiftIsoDate(normalizedBase.toString(), unit, delta) != null
}

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(take(10)) }.getOrNull()

private fun LocalDate.startOfServerWeek(): LocalDate =
    minus((dayOfWeek.isoDayNumber - SERVER_WEEK_START_ISO_DAY_NUMBER + 7) % 7, DateTimeUnit.DAY)

private fun LocalDate.startOfMonth(): LocalDate = yearMonth.firstDay

private fun latestAllowedPopularDate(): LocalDate =
    Clock.System.todayIn(TimeZone.currentSystemDefault())
