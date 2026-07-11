package ddd.kc.ui.pages.recent

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.PopularInfo
import ddd.kc.data.model.PopularProps
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostKey
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.key
import ddd.kc.data.remote.repository.PostRepository
import ddd.kc.data.remote.repository.awaitData
import ddd.kc.ui.components.state.DEFAULT_PAGE_SIZE
import ddd.kc.ui.components.state.PaginationReducer
import ddd.kc.ui.components.state.PaginationSnapshot
import ddd.kc.utils.coroutines.resultOfSuspend
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

private const val PAGE_SIZE = DEFAULT_PAGE_SIZE
private const val SERVER_WEEK_START_ISO_DAY_NUMBER = 2
private val popularLog = KcLog.withTag("PopularPostsScreenModel")

enum class PopularPeriod(val apiValue: String) {
  DAY("day"),
  WEEK("week"),
  MONTH("month"),
}

data class PopularPostsState(
    val period: PopularPeriod = PopularPeriod.DAY,
    val date: String? = defaultPopularDate(PopularPeriod.DAY),
    val info: PopularInfo? = null,
    val props: PopularProps? = null,
    val paging: PaginationSnapshot<Post> = PaginationSnapshot(loading = true),
) {
  val posts
    get() = paging.items

  val result
    get() =
        QueryState<Unit>(
            isLoading = paging.loading,
            isRefreshing = paging.refreshing,
            error = paging.error,
        )

  val isLoading
    get() = paging.loading

  val error
    get() = paging.error

  val visiblePageInfo
    get() = paging.visiblePageInfo

  val canAutoLoadPrevious
    get() = paging.canAutoLoadPrevious

  val startOffset
    get() = paging.startOffset

  val offset
    get() = paging.offset

  val hasMore
    get() = paging.hasMore

  val isLoadingPrevious
    get() = paging.isLoadingPrevious

  val isLoadingMore
    get() = paging.isLoadingMore

  val prependError
    get() = paging.prependError

  val appendError
    get() = paging.appendError

  val canShiftPrevious: Boolean
    get() = canShiftPopularDate(this, delta = -1)

  val canShiftNext: Boolean
    get() = canShiftPopularDate(this, delta = 1)
}

class PopularPostsScreenModel(
    private val postRepo: PostRepository,
) : StateScreenModel<PopularPostsState>(PopularPostsState()) {
  private val reducer = PaginationReducer<Post, PostKey> { it.key }
  private var generation: Long = 0

  fun load(forceRefresh: Boolean = false) {
    val state = mutableState.value
    if (!forceRefresh && state.posts.isNotEmpty()) return
    generation++
    updatePaging(reducer.beginLoad(state.paging, forceRefresh))
    screenModelScope.launch { loadPage(offset = 0, forceRefresh = forceRefresh, replace = true) }
  }

  fun loadMore() {
    val state = mutableState.value
    if (!reducer.canLoadMore(state.paging)) return
    updatePaging(reducer.beginAppend(state.paging))
    screenModelScope.launch {
      loadPage(offset = state.offset, forceRefresh = false, replace = false)
    }
  }

  fun loadPrevious() {
    val state = mutableState.value
    if (!reducer.canLoadPrevious(state.paging)) return
    updatePaging(reducer.beginPrepend(state.paging))
    val offset = (state.startOffset - PAGE_SIZE).coerceAtLeast(0)
    screenModelScope.launch {
      loadPage(offset = offset, forceRefresh = false, replace = false, prepend = true)
    }
  }

  fun jumpToPage(page: Int) {
    val state = mutableState.value
    val targetPage = page.coerceIn(1, state.paging.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    generation++
    updatePaging(reducer.beginJump(state.paging, offset))
    screenModelScope.launch {
      loadPage(offset = offset, forceRefresh = false, replace = true, rollbackState = state)
    }
  }

  fun onVisiblePostIndex(firstVisiblePostIndex: Int) {
    updatePaging(
        reducer.updateVisiblePage(mutableState.value.paging, firstVisiblePostIndex, PAGE_SIZE)
    )
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
    generation++
    mutableState.value =
        mutableState.value.copy(
            date = normalizedDate,
            period = period,
            paging = PaginationSnapshot(loading = true),
        )
    screenModelScope.launch { loadPage(offset = 0, forceRefresh = false, replace = true) }
  }

  private suspend fun loadPage(
      offset: Int,
      forceRefresh: Boolean,
      replace: Boolean,
      prepend: Boolean = false,
      rollbackState: PopularPostsState? = null,
      requestGeneration: Long = generation,
  ) {
    val state = mutableState.value
    val requestDate = normalizePopularBaseDate(state.date, state.period)
    val requestPeriod = state.period
    resultOfSuspend {
          postRepo
              .observePopularPostsPage(
                  date = requestDate,
                  period = requestPeriod.apiValue,
                  offset = offset,
                  forceRefresh = forceRefresh,
              )
              .awaitData()
        }
        .onSuccess { page ->
          if (
              requestGeneration != generation ||
                  mutableState.value.period != requestPeriod ||
                  normalizePopularBaseDate(mutableState.value.date, requestPeriod) != requestDate
          ) {
            return@onSuccess
          }
          val firstPage = replace || (offset == 0 && !prepend)
          val hasMore = page.pageInfo?.hasNext ?: (page.posts.size >= PAGE_SIZE)
          val paging =
              when {
                firstPage ->
                    reducer.reduceFirstPage(
                        mutableState.value.paging,
                        page.posts,
                        hasMore,
                        offset + page.posts.size,
                        page.pageInfo,
                        offset,
                    )
                prepend ->
                    reducer.reducePrepend(
                        mutableState.value.paging,
                        page.posts,
                        mutableState.value.paging.hasMore,
                        offset,
                    )
                else ->
                    reducer.reduceAppend(
                        mutableState.value.paging,
                        page.posts,
                        hasMore,
                        offset + page.posts.size,
                    )
              }
          mutableState.value =
              mutableState.value.copy(
                  info = page.info,
                  props = page.props,
                  date = canonicalPopularDate(page.info, state.period, state.date),
                  paging = paging,
              )
          popularLog.i {
            "热门Posts -> 加载成功(period=${state.period.apiValue},date=${state.date},offset=$offset,count=${page.posts.size})"
          }
        }
        .onFailure {
          if (
              requestGeneration != generation ||
                  mutableState.value.period != requestPeriod ||
                  normalizePopularBaseDate(mutableState.value.date, requestPeriod) != requestDate
          ) {
            return@onFailure
          }
          popularLog.e(it) {
            "热门Posts -> 加载失败(period=${state.period.apiValue},date=${state.date},offset=$offset)"
          }
          updatePaging(
              when {
                replace && rollbackState != null ->
                    reducer.reduceJumpError(rollbackState.paging, it)
                replace -> reducer.reduceFirstPageError(mutableState.value.paging, it)
                prepend -> reducer.reducePrependError(mutableState.value.paging, it)
                else -> reducer.reduceAppendError(mutableState.value.paging, it)
              }
          )
        }
  }

  private fun updatePaging(paging: PaginationSnapshot<Post>) {
    mutableState.value = mutableState.value.copy(paging = paging)
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
