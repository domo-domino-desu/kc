package ddd.kc.ui.pages.post

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import ddd.kc.data.local.ActivityHistoryRepository
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostKey
import ddd.kc.data.model.canLoadFullImage
import ddd.kc.data.model.creatorId
import ddd.kc.data.model.creatorKey
import ddd.kc.data.model.fullUrl
import ddd.kc.data.model.imageFiles
import ddd.kc.data.model.key
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.ui.app.LocalAppSettings
import ddd.kc.ui.app.navigation.LocalNavigationWindowStore
import ddd.kc.ui.app.navigation.nextRouteInstanceKey
import ddd.kc.ui.components.LocalShowToast
import ddd.kc.ui.components.platform.PlatformBinaryFileDestination
import ddd.kc.ui.components.platform.PlatformBinaryFileWriteRequest
import ddd.kc.ui.components.platform.PlatformBinaryFileWriteResult
import ddd.kc.ui.components.platform.buildPostDownloadTarget
import ddd.kc.ui.components.platform.rememberPlatformBinaryFileWriter
import ddd.kc.ui.components.state.ContentTranslationState
import ddd.kc.ui.pages.creator.CreatorRouteScreen
import ddd.kc.ui.pages.imageviewer.ImageViewerScreen
import ddd.kc.ui.pages.tagposts.TagPostsScreen
import ddd.kc.utils.logging.KcLog
import ddd.kc.utils.logging.summarizePost
import ddd.kc.utils.logging.summarizePostFiles
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.download_failed
import kc.shared.generated.resources.download_save_path_required
import kc.shared.generated.resources.download_saved
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

private val log = KcLog.withTag("PostRouteScreen")

class PostRouteScreen(
    private val windowId: String,
    private val resourceKey: PostKey,
    private val startIndex: Int,
    private val source: String = "unknown",
    private val initialOffset: Int = 0,
    private val initialHasMore: Boolean = false,
    private val pagingContext: PostPagingContext = PostPagingContext.None,
    private val routeKey: String = nextRouteInstanceKey("post"),
) : Screen {
  override val key: String = routeKey

  @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val navigationWindows = LocalNavigationWindowStore.current
    val posts =
        navigationWindows.posts(windowId)
            ?: listOf(
                Post(
                    id = resourceKey.id,
                    user = resourceKey.creatorId,
                    artistId = resourceKey.creatorId,
                    service = resourceKey.service,
                )
            )
    val resolvedStartIndex = if (startIndex in posts.indices) startIndex else 0
    val screenModel =
        koinScreenModel<PostScreenModel> {
          parametersOf(posts, resolvedStartIndex, initialOffset, initialHasMore, pagingContext)
        }
    val historyRepository = koinInject<ActivityHistoryRepository>()
    val state by screenModel.state.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val pagerState =
        rememberPagerState(
            initialPage = resolvedStartIndex,
            pageCount = { state.posts.size },
        )

    LaunchedEffect(Unit) {
      log.i { "打开Post -> 进入页面(source=$source,startIndex=$startIndex,count=${posts.size})" }
      posts.getOrNull(startIndex)?.let {
        log.i { "打开Post -> 初始Post(${summarizePost(it)},${summarizePostFiles(it)})" }
      }
      focusRequester.requestFocus()
    }
    LaunchedEffect(pagerState.currentPage) { screenModel.onPageChanged(pagerState.currentPage) }
    LaunchedEffect(state.currentIndex) {
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
      val appSettings = LocalAppSettings.current
      val cdnUrl = appSettings.cdnUrl()
      val baseUrl = appSettings.baseUrl()
      val downloadSavePath by
          appSettings.downloadSavePathFlow().collectAsState(appSettings.downloadSavePath())
      val writeBinaryFile = rememberPlatformBinaryFileWriter()
      val showToast = LocalShowToast.current
      val savePathRequiredMessage = stringResource(Res.string.download_save_path_required)
      val downloadSavedMessage = stringResource(Res.string.download_saved)
      val downloadFailedMessage = stringResource(Res.string.download_failed)
      val scope = rememberCoroutineScope()
      val downloadingUrls = remember { mutableStateMapOf<String, Boolean>() }
      // Tracks each page's LazyListState for scroll-to-top
      val pageListStates = remember { mutableStateOf<Map<Int, LazyListState>>(emptyMap()) }

      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val post = state.posts.getOrNull(page) ?: return@HorizontalPager
        val listState = rememberLazyListState()
        LaunchedEffect(post.service, post.creatorId, post.id) {
          historyRepository.recordPostVisit(post)
        }
        LaunchedEffect(page) { pageListStates.value = pageListStates.value + (page to listState) }
        PostDetailPage(
            post = post,
            cdnUrl = cdnUrl,
            baseUrl = baseUrl,
            listState = listState,
            isFavorite = post.key in state.favoritePostIds,
            favoriteError = state.favoriteError,
            isDetailLoading = post.key in state.loadingDetailPostIds,
            creator = state.postCreators[post.creatorKey],
            comments = screenModel.getComments(post),
            commentError = state.postCommentErrors[post.key],
            translationState = state.postTranslations[post.key] ?: ContentTranslationState(),
            requestedFullImageUrls = state.requestedFullImageUrls[post.key].orEmpty(),
            onFavoriteClick = { screenModel.toggleFavoritePost(post) },
            onTranslate = { screenModel.translateContent(post) },
            onFullImageRequested = { fullUrl -> screenModel.requestFullImage(post.key, fullUrl) },
            onFullImagesRequested = { fullUrls ->
              screenModel.requestFullImages(post.key, fullUrls)
            },
            onTagClick = { tag -> navigator.push(TagPostsScreen(tag)) },
            onArtistClick = {
              val creator = state.postCreators[post.creatorKey]
              if (creator != null) {
                navigator.push(
                    CreatorRouteScreen(
                        navigationWindows.putCreators(listOf(creator)),
                        creator.key,
                        0,
                    )
                )
              }
            },
            onScrollToTop = {
              scope.launch { pageListStates.value[pagerState.currentPage]?.scrollToItem(0) }
            },
            onImageClick = { imageIndex ->
              val images = post.imageFiles()
              val viewableImages =
                  images.mapNotNull { file ->
                    if (!file.canLoadFullImage(post)) return@mapNotNull null
                    file.fullUrl(cdnUrl)?.let { fullUrl ->
                      fullUrl to file.thumbnailUrl(cdnUrl).orEmpty()
                    }
                  }
              val urls = viewableImages.map { it.first }
              val thumbs = viewableImages.map { it.second }
              val startIndex =
                  images.take(imageIndex).count {
                    it.canLoadFullImage(post) && !it.fullUrl(cdnUrl).isNullOrBlank()
                  }
              log.i {
                "打开图片 -> 点击Post图片(service=${post.service},creator=${post.user},post=${post.id},index=$startIndex,count=${urls.size})"
              }
              if (urls.isNotEmpty() && startIndex in urls.indices) {
                navigator.push(
                    ImageViewerScreen(
                        windowId =
                            navigationWindows.putImages(
                                imageUrls = urls,
                                thumbnailUrls = thumbs,
                                onImageViewed = { fullUrl ->
                                  screenModel.requestFullImage(post.key, fullUrl)
                                },
                            ),
                        startIndex = startIndex,
                    )
                )
              }
            },
            isAttachmentDownloading = { file ->
              file.fullUrl(cdnUrl)?.let { downloadingUrls[it] == true } == true
            },
            onAttachmentDownload = download@{ file ->
                  val url = file.fullUrl(cdnUrl)
                  if (url == null) {
                    showToast(downloadFailedMessage.format("Missing file URL"))
                    return@download
                  }
                  val savePath = downloadSavePath.trim()
                  if (savePath.isBlank()) {
                    showToast(savePathRequiredMessage)
                    return@download
                  }
                  if (downloadingUrls[url] == true) return@download
                  scope.launch {
                    downloadingUrls[url] = true
                    try {
                      val bytes = screenModel.downloadFile(url)
                      val downloadTarget = buildPostDownloadTarget(appSettings, post, file)
                      when (
                          val result =
                              writeBinaryFile(
                                  PlatformBinaryFileWriteRequest(
                                      destination =
                                          PlatformBinaryFileDestination.Directory(
                                              path = savePath,
                                              relativeDirectories =
                                                  downloadTarget.relativeDirectories,
                                              allowMediaIndexing =
                                                  appSettings.downloadAllowMediaIndexing(),
                                          ),
                                      fileName = downloadTarget.fileName,
                                      bytes = bytes,
                                      mimeType = downloadTarget.fileName.guessMimeType(),
                                  )
                              )
                      ) {
                        is PlatformBinaryFileWriteResult.Saved ->
                            showToast(downloadSavedMessage.format(result.savedPath))
                        is PlatformBinaryFileWriteResult.Failure ->
                            showToast(downloadFailedMessage.format(result.message))
                      }
                    } catch (error: Throwable) {
                      if (error is CancellationException) throw error
                      log.e(error) { "下载附件 -> 失败(urlLength=${url.length})" }
                      showToast(downloadFailedMessage.format(error::class.simpleName ?: "I/O"))
                    } finally {
                      downloadingUrls.remove(url)
                    }
                  }
                },
        )
        LaunchedEffect(post.key) {
          screenModel.loadFavoriteStatus(post)
          screenModel.loadCreatorInfo(post)
          screenModel.loadComments(post)
        }
      }
    }
  }
}
