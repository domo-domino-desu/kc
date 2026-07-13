package ddd.kc.ui.components.paging

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PagingDirection {
  REFRESH,
  APPEND,
  PREPEND,
  JUMP,
}

data class PagingAnchor(
    val itemKey: String? = null,
    val index: Int = 0,
    val offset: Int = 0,
)

sealed interface PagingIntent {
  data object Refresh : PagingIntent

  data object Append : PagingIntent

  data object Prepend : PagingIntent

  data class JumpToPage(val page: Int) : PagingIntent

  data class ViewportChanged(val anchor: PagingAnchor) : PagingIntent

  data class Select(val itemKey: String?) : PagingIntent

  data class Retry(val direction: PagingDirection) : PagingIntent
}

sealed interface PagingEffect {
  val transactionId: Long

  data class ScrollToTop(override val transactionId: Long) : PagingEffect

  data class RestoreViewport(
      val anchor: PagingAnchor,
      override val transactionId: Long,
  ) : PagingEffect

  data class RebaseSelection(
      val itemKey: String,
      override val transactionId: Long,
  ) : PagingEffect
}

data class PagingLoadState(
    val initial: Boolean = false,
    val refresh: Boolean = false,
    val append: Boolean = false,
    val prepend: Boolean = false,
    val jump: Boolean = false,
) {
  val any: Boolean
    get() = initial || refresh || append || prepend || jump
}

data class PagingFailure(val direction: PagingDirection, val cause: Throwable)

data class PagingViewState<Item>(
    val items: List<Item> = emptyList(),
    val selectedItemKey: String? = null,
    val currentPage: Int = 1,
    val lastPage: Int? = null,
    val canAppend: Boolean = false,
    val canPrepend: Boolean = false,
    val canJump: Boolean = false,
    val loadState: PagingLoadState = PagingLoadState(),
    val failure: PagingFailure? = null,
    val viewport: PagingAnchor = PagingAnchor(),
)

data class PagingPage<Item>(
    val page: Int,
    val items: List<Item>,
    val lastPage: Int? = null,
    val hasNext: Boolean = lastPage?.let { page < it } ?: items.isNotEmpty(),
    val hasPrevious: Boolean = page > 1,
)

fun interface PagingSource<Item> {
  suspend fun load(page: Int, forceRefresh: Boolean): PagingPage<Item>
}

interface PagingSession<Item> {
  val state: StateFlow<PagingViewState<Item>>
  val effects: Flow<PagingEffect>

  fun accept(intent: PagingIntent)
}

class DefaultPagingSession<Item>(
    private val scope: CoroutineScope,
    private val source: PagingSource<Item>,
    private val keyOf: (Item) -> String,
    initialPage: PagingPage<Item>? = null,
    initialSelectedItemKey: String? = null,
) : PagingSession<Item> {
  private val pages = sortedMapOf<Int, PagingPage<Item>>()
  private val mutableState = MutableStateFlow(PagingViewState<Item>())
  private val mutableEffects = MutableSharedFlow<PagingEffect>(extraBufferCapacity = 16)
  private var generation = 0L
  private var activeJob: Job? = null

  override val state: StateFlow<PagingViewState<Item>> = mutableState.asStateFlow()
  override val effects: Flow<PagingEffect> = mutableEffects.asSharedFlow()

  init {
    if (initialPage != null) {
      pages[initialPage.page] = initialPage
      mutableState.value =
          buildState(
              currentPage = initialPage.page,
              selectedItemKey = initialSelectedItemKey,
          )
    }
  }

  override fun accept(intent: PagingIntent) {
    when (intent) {
      PagingIntent.Refresh -> load(PagingDirection.REFRESH, state.value.currentPage, true)
      PagingIntent.Append ->
          if (state.value.canAppend)
              load(PagingDirection.APPEND, state.value.currentPage + 1, false)
      PagingIntent.Prepend ->
          if (state.value.canPrepend)
              load(PagingDirection.PREPEND, state.value.currentPage - 1, false)
      is PagingIntent.JumpToPage -> jump(intent.page)
      is PagingIntent.ViewportChanged ->
          mutableState.value = state.value.copy(viewport = intent.anchor)
      is PagingIntent.Select ->
          mutableState.value = state.value.copy(selectedItemKey = intent.itemKey)
      is PagingIntent.Retry ->
          when (intent.direction) {
            PagingDirection.REFRESH -> load(PagingDirection.REFRESH, state.value.currentPage, true)
            PagingDirection.APPEND ->
                load(PagingDirection.APPEND, state.value.currentPage + 1, false)
            PagingDirection.PREPEND ->
                load(PagingDirection.PREPEND, state.value.currentPage - 1, false)
            PagingDirection.JUMP -> load(PagingDirection.JUMP, state.value.currentPage, false)
          }
    }
  }

  private fun jump(requestedPage: Int) {
    val target =
        requestedPage.coerceAtLeast(1).let { page ->
          state.value.lastPage?.let { page.coerceAtMost(it) } ?: page
        }
    if (target == state.value.currentPage && pages.containsKey(target)) {
      val transaction = ++generation
      mutableEffects.tryEmit(PagingEffect.ScrollToTop(transaction))
      return
    }
    load(PagingDirection.JUMP, target, false)
  }

  private fun load(direction: PagingDirection, page: Int, forceRefresh: Boolean) {
    if (page < 1) return
    val rollback = Snapshot(pages.toMap(), state.value)
    val transaction = ++generation
    activeJob?.cancel()
    mutableState.value =
        when (direction) {
          PagingDirection.REFRESH ->
              state.value.copy(
                  loadState =
                      PagingLoadState(
                          refresh = state.value.items.isNotEmpty(),
                          initial = state.value.items.isEmpty(),
                      ),
                  failure = null,
              )
          PagingDirection.APPEND ->
              state.value.copy(loadState = PagingLoadState(append = true), failure = null)
          PagingDirection.PREPEND ->
              state.value.copy(loadState = PagingLoadState(prepend = true), failure = null)
          PagingDirection.JUMP ->
              state.value.copy(
                  items = emptyList(),
                  currentPage = page,
                  canAppend = false,
                  canPrepend = false,
                  loadState = PagingLoadState(jump = true),
                  failure = null,
                  viewport = PagingAnchor(),
              )
        }
    if (direction == PagingDirection.JUMP)
        mutableEffects.tryEmit(PagingEffect.ScrollToTop(transaction))
    activeJob =
        scope.launch {
          try {
            val result = source.load(page, forceRefresh)
            if (transaction != generation) return@launch
            when (direction) {
              PagingDirection.REFRESH,
              PagingDirection.JUMP -> {
                pages.clear()
                pages[result.page] = result
              }
              PagingDirection.APPEND,
              PagingDirection.PREPEND -> pages[result.page] = result
            }
            val selected = rollback.state.selectedItemKey
            mutableState.value = buildState(result.page, selected)
            if (direction == PagingDirection.PREPEND && selected != null) {
              mutableEffects.emit(PagingEffect.RebaseSelection(selected, transaction))
            }
          } catch (error: CancellationException) {
            throw error
          } catch (error: Throwable) {
            if (transaction != generation) return@launch
            pages.clear()
            pages.putAll(rollback.pages)
            mutableState.value =
                rollback.state.copy(
                    loadState = PagingLoadState(),
                    failure = PagingFailure(direction, error),
                )
            if (direction == PagingDirection.JUMP) {
              mutableEffects.emit(
                  PagingEffect.RestoreViewport(rollback.state.viewport, transaction)
              )
            }
          }
        }
  }

  private fun buildState(currentPage: Int, selectedItemKey: String?): PagingViewState<Item> {
    val merged = LinkedHashMap<String, Item>()
    pages.values.forEach { page -> page.items.forEach { item -> merged[keyOf(item)] = item } }
    val current = pages[currentPage] ?: pages.values.firstOrNull()
    val lastPage = pages.values.mapNotNull(PagingPage<Item>::lastPage).maxOrNull()
    return PagingViewState(
        items = merged.values.toList(),
        selectedItemKey = selectedItemKey?.takeIf(merged::containsKey),
        currentPage = current?.page ?: currentPage,
        lastPage = lastPage,
        canAppend = current?.hasNext == true,
        canPrepend = current?.hasPrevious == true,
        canJump = lastPage != null,
        viewport = state.value.viewport,
    )
  }

  private data class Snapshot<Item>(
      val pages: Map<Int, PagingPage<Item>>,
      val state: PagingViewState<Item>,
  )
}
