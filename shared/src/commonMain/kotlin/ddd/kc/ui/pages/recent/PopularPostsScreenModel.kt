package ddd.kc.ui.pages.recent

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.QueryState
import ddd.kc.data.network.PopularInfo
import ddd.kc.data.network.PopularPage
import ddd.kc.data.network.PopularProps
import ddd.kc.data.network.toQueryError
import ddd.kc.data.repository.PostRepository
import ddd.kc.data.repository.awaitData
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50
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
    val date: String? = null,
    val info: PopularInfo? = null,
    val props: PopularProps? = null,
    val offset: Int = 0,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val appendErrorMessage: String? = null,
) {
  val isLoading: Boolean
    get() = result.isLoading

  val errorMessage: String?
    get() = result.error?.message
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
        )
    screenModelScope.launch { loadPage(offset = 0, forceRefresh = forceRefresh) }
  }

  fun loadMore() {
    val state = mutableState.value
    if (state.isLoading || state.isLoadingMore || !state.hasMore) return
    mutableState.value = state.copy(isLoadingMore = true, appendErrorMessage = null)
    screenModelScope.launch { loadPage(offset = state.offset, forceRefresh = false) }
  }

  fun selectPeriod(period: PopularPeriod) {
    val date = currentTriple(period)?.second ?: mutableState.value.date
    submit(date = date, period = period)
  }

  fun selectBoundaryDate(boundary: PopularDateBoundary, date: String) {
    val period = mutableState.value.period
    val baseDate =
        when (period) {
          PopularPeriod.DAY -> date
          PopularPeriod.WEEK ->
              when (boundary) {
                PopularDateBoundary.START -> date
                PopularDateBoundary.END -> shiftIsoDate(date, PopularDateShiftUnit.DAY, -7) ?: date
              }
          PopularPeriod.MONTH -> firstDayOfMonth(date) ?: date
        }
    submit(date = baseDate, period = period)
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
    val next = shiftIsoDate(base.take(10), unit, delta) ?: return
    submit(date = next, period = mutableState.value.period)
  }

  private fun submit(date: String?, period: PopularPeriod) {
    val normalizedDate = normalizePopularBaseDate(date, period)
    mutableState.value =
        mutableState.value.copy(
            date = normalizedDate,
            period = period,
            posts = emptyList(),
            offset = 0,
            hasMore = true,
            result = QueryState(isLoading = true),
            isLoadingMore = false,
            appendErrorMessage = null,
        )
    screenModelScope.launch { loadPage(offset = 0, forceRefresh = false) }
  }

  private suspend fun loadPage(offset: Int, forceRefresh: Boolean) {
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
          val firstPage = offset == 0
          val merged =
              if (firstPage) page.posts
              else (mutableState.value.posts + page.posts).distinctBy { it.id }
          mutableState.value =
              mutableState.value.copy(
                  posts = merged,
                  result = QueryState(data = page),
                  info = page.info,
                  props = page.props,
                  date = page.info.date ?: state.date,
                  offset = merged.size,
                  hasMore = page.posts.size >= PAGE_SIZE,
                  isLoadingMore = false,
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
              mutableState.value.copy(
                  result =
                      mutableState.value.result.copy(
                          isLoading = false,
                          isRefreshing = false,
                          error = it.toQueryError(),
                      ),
                  isLoadingMore = false,
                  appendErrorMessage = if (offset == 0) null else it.message,
              )
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

private fun normalizePopularBaseDate(date: String?, period: PopularPeriod): String? =
    when (period) {
      PopularPeriod.DAY -> date?.take(10)
      PopularPeriod.WEEK -> date?.take(10)
      PopularPeriod.MONTH -> date?.take(10)?.let { firstDayOfMonth(it) }
    }

private fun firstDayOfMonth(date: String): String? {
  val parts = date.split('-')
  if (parts.size != 3) return null
  val year = parts[0].toIntOrNull() ?: return null
  val month = parts[1].toIntOrNull() ?: return null
  if (month !in 1..12) return null
  return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-01"
}

private fun shiftIsoDate(date: String, unit: PopularDateShiftUnit, delta: Int): String? {
  val parts = date.split('-')
  if (parts.size != 3) return null
  var year = parts[0].toIntOrNull() ?: return null
  var month = parts[1].toIntOrNull() ?: return null
  var day = parts[2].toIntOrNull() ?: return null
  when (unit) {
    PopularDateShiftUnit.DAY -> {
      var remaining = delta
      while (remaining != 0) {
        if (remaining > 0) {
          day += 1
          if (day > daysInMonth(year, month)) {
            day = 1
            month += 1
            if (month > 12) {
              month = 1
              year += 1
            }
          }
          remaining -= 1
        } else {
          day -= 1
          if (day < 1) {
            month -= 1
            if (month < 1) {
              month = 12
              year -= 1
            }
            day = daysInMonth(year, month)
          }
          remaining += 1
        }
      }
    }

    PopularDateShiftUnit.WEEK -> return shiftIsoDate(date, PopularDateShiftUnit.DAY, delta * 7)
    PopularDateShiftUnit.MONTH -> {
      month += delta
      while (month > 12) {
        month -= 12
        year += 1
      }
      while (month < 1) {
        month += 12
        year -= 1
      }
      day = day.coerceAtMost(daysInMonth(year, month))
    }
  }
  return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

private fun daysInMonth(year: Int, month: Int): Int =
    when (month) {
      1,
      3,
      5,
      7,
      8,
      10,
      12 -> 31
      4,
      6,
      9,
      11 -> 30
      2 -> if (isLeapYear(year)) 29 else 28
      else -> 30
    }

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
