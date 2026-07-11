package ddd.kc.ui.pages.creator

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import ddd.kc.data.model.Creator
import ddd.kc.data.model.CreatorKey
import ddd.kc.data.model.creatorId
import ddd.kc.data.model.key
import ddd.kc.data.repository.ActivityHistoryRepository
import ddd.kc.ui.navigation.LocalNavigationWindowStore
import ddd.kc.ui.navigation.nextRouteInstanceKey
import ddd.kc.ui.pages.post.PostPagingContext
import ddd.kc.ui.pages.post.PostRouteScreen
import ddd.kc.ui.pages.tagposts.TagPostsScreen
import ddd.kc.utils.logging.KcLog
import ddd.kc.utils.logging.summarizePost
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

private val log = KcLog.withTag("CreatorRouteScreen")

class CreatorRouteScreen(
    private val windowId: String,
    private val resourceKey: CreatorKey,
    private val startIndex: Int,
    private val routeKey: String = nextRouteInstanceKey("creator"),
) : Screen {
  override val key: String = routeKey

  @OptIn(ExperimentalFoundationApi::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val navigationWindows = LocalNavigationWindowStore.current
    val creators =
        navigationWindows.creators(windowId)
            ?: listOf(
                Creator(
                    id = resourceKey.id,
                    name = resourceKey.id,
                    service = resourceKey.service,
                    publicId = resourceKey.id,
                )
            )
    val resolvedStartIndex = if (startIndex in creators.indices) startIndex else 0
    val screenModel =
        koinScreenModel<CreatorScreenModel> { parametersOf(creators, resolvedStartIndex) }
    val historyRepository = koinInject<ActivityHistoryRepository>()
    val state by screenModel.state.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val pagerState =
        rememberPagerState(
            initialPage = resolvedStartIndex,
            pageCount = { state.creators.size },
        )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(pagerState.currentPage) { screenModel.onPageChanged(pagerState.currentPage) }
    LaunchedEffect(state.currentIndex) {
      focusRequester.requestFocus()
      if (pagerState.currentPage != state.currentIndex) {
        pagerState.animateScrollToPage(state.currentIndex)
      }
    }

    Box(
        modifier =
            Modifier.fillMaxSize().focusRequester(focusRequester).focusable().onPreviewKeyEvent {
                event ->
              if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
              when (event.key) {
                Key.DirectionLeft -> {
                  screenModel.previous()
                  true
                }
                Key.DirectionRight -> {
                  screenModel.next()
                  true
                }
                else -> false
              }
            }
    ) {
      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val creator = state.creators.getOrNull(page) ?: return@HorizontalPager
        LaunchedEffect(creator.service, creator.id) {
          historyRepository.recordCreatorVisit(creator)
        }
        CreatorDetailPage(
            creator = creator,
            state = state,
            screenModel = screenModel,
            onPostClick = { post, posts, startOffset, hasMore ->
              log.i { "打开Post -> 点击来源(source=creator,${summarizePost(post)})" }
              navigator.push(
                  PostRouteScreen(
                      navigationWindows.putPosts(posts),
                      post.key,
                      posts.indexOf(post),
                      source = "creator",
                      initialOffset = startOffset,
                      initialHasMore = hasMore,
                      pagingContext = PostPagingContext.Creator(post.service, post.creatorId),
                  )
              )
            },
            onTagClick = { tag -> navigator.push(TagPostsScreen(tag)) },
            onCreatorListOpen = { title, list, showFavoriteCount ->
              navigator.push(
                  CreatorListRouteScreen(
                      title = title,
                      windowId = navigationWindows.putCreators(list),
                      showFavoriteCount = showFavoriteCount,
                  )
              )
            },
        )
      }
    }
  }
}
