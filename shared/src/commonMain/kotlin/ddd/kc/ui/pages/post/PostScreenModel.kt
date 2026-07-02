package ddd.kc.ui.pages.post

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.application.translation.TranslationService
import ddd.kc.data.model.Comment
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.allFiles
import ddd.kc.data.model.creatorId
import ddd.kc.data.model.imageFiles
import ddd.kc.data.network.AuthRequiredException
import ddd.kc.data.network.PageInfo
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.data.repository.PostRepository
import ddd.kc.domain.translation.TranslationBlockResult
import ddd.kc.ui.state.ContentTranslationState
import ddd.kc.ui.state.PAGER_NEXT_PREFETCH_COUNT
import ddd.kc.ui.state.PAGER_PREFETCH_DEBOUNCE_MS
import ddd.kc.ui.state.PAGER_PREVIOUS_PREFETCH_COUNT
import ddd.kc.ui.state.TranslationBlockState
import ddd.kc.ui.state.TranslationStatus
import ddd.kc.util.logging.KcLog
import ddd.kc.util.logging.summarizePost
import ddd.kc.util.logging.summarizePostFiles
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50
private val log = KcLog.withTag("PostScreenModel")

private data class DetailPagingPage(
    val posts: List<Post>,
    val pageInfo: PageInfo?,
)

class PostScreenModel(
    private val postRepo: PostRepository,
    private val creatorRepo: CreatorRepository,
    private val translationService: TranslationService,
    private val platform: Platform,
    initialPosts: List<Post>,
    startIndex: Int,
    private val initialOffset: Int = 0,
    initialHasMore: Boolean = false,
    private val pagingContext: PostPagingContext = PostPagingContext.None,
) :
    StateScreenModel<PostPagerUiState>(
        PostPagerUiState(
            posts = initialPosts,
            currentIndex = startIndex,
            startOffset = initialOffset,
            offset = initialOffset + initialPosts.size,
            hasMore = pagingContext != PostPagingContext.None && initialHasMore,
        )
    ) {

  private var prefetchJob: Job? = null
  private var favoriteStatusDisabled = false
  private val loadedDetailIds = mutableSetOf<String>()
  private val loadingDetailIds = mutableSetOf<String>()
  private val loadingCommentIds = mutableSetOf<String>()

  fun onPageChanged(index: Int) {
    val post = mutableState.value.posts.getOrNull(index)
    log.i {
      if (post == null) {
        "打开Post -> 切换(index=$index,post=空)"
      } else {
        "打开Post -> 切换(index=$index,${summarizePost(post)})"
      }
    }
    mutableState.value = mutableState.value.copy(currentIndex = index)
    post?.let { loadPostDetail(it) }
    schedulePrefetch(index)
    if (index >= mutableState.value.posts.size - PAGER_NEXT_PREFETCH_COUNT) {
      loadMore()
    }
    if (index <= PAGER_PREVIOUS_PREFETCH_COUNT) {
      loadPrevious()
    }
  }

  fun previous() {
    val current = mutableState.value.currentIndex
    if (current > 0) {
      mutableState.value = mutableState.value.copy(currentIndex = current - 1)
    }
  }

  fun next() {
    val current = mutableState.value.currentIndex
    val state = mutableState.value
    if (current < state.posts.size - 1) {
      mutableState.value = state.copy(currentIndex = current + 1)
      if (current + 1 >= state.posts.size - PAGER_NEXT_PREFETCH_COUNT) {
        loadMore()
      }
    }
  }

  private fun loadMore() {
    val state = mutableState.value
    if (state.isLoadingMore || state.isLoadingPrevious || !state.hasMore) return
    if (pagingContext == PostPagingContext.None) return
    val nextOffset = state.offset
    log.i { "加载更多Post -> 开始(offset=$nextOffset)" }
    mutableState.value = state.copy(isLoadingMore = true)
    screenModelScope.launch {
      runCatching { fetchPagingPage(nextOffset, forceRefresh = false) }
          .onSuccess { page ->
            val current = mutableState.value
            val merged =
                appendDetailPosts(
                    currentPosts = current.posts,
                    pagePosts = page.posts,
                    nextOffset = nextOffset,
                    pageInfo = page.pageInfo,
                    pageSize = PAGE_SIZE,
                )
            mutableState.value =
                current.copy(
                    posts = merged.posts,
                    offset = merged.offset,
                    hasMore = merged.hasMore,
                    isLoadingMore = false,
                )
            log.i { "加载更多Post -> 成功(count=${page.posts.size},merged=${merged.posts.size})" }
          }
          .onFailure {
            log.e(it) { "加载更多Post -> 失败" }
            mutableState.value = mutableState.value.copy(isLoadingMore = false)
          }
    }
  }

  private fun loadPrevious() {
    val state = mutableState.value
    if (
        state.isLoadingPrevious ||
            state.isLoadingMore ||
            state.startOffset <= 0 ||
            pagingContext == PostPagingContext.None
    )
        return
    val previousOffset = (state.startOffset - PAGE_SIZE).coerceAtLeast(0)
    log.i { "加载上一页Post -> 开始(offset=$previousOffset)" }
    mutableState.value = state.copy(isLoadingPrevious = true)
    screenModelScope.launch {
      runCatching { fetchPagingPage(previousOffset, forceRefresh = false) }
          .onSuccess { page ->
            val current = mutableState.value
            val merged =
                prependDetailPosts(
                    currentPosts = current.posts,
                    currentIndex = current.currentIndex,
                    previousOffset = previousOffset,
                    pagePosts = page.posts,
                )
            mutableState.value =
                current.copy(
                    posts = merged.posts,
                    currentIndex = merged.currentIndex,
                    startOffset = merged.startOffset,
                    isLoadingPrevious = false,
                )
            log.i { "加载上一页Post -> 成功(count=${page.posts.size},merged=${merged.posts.size})" }
          }
          .onFailure {
            log.e(it) { "加载上一页Post -> 失败" }
            mutableState.value = mutableState.value.copy(isLoadingPrevious = false)
          }
    }
  }

  private suspend fun fetchPagingPage(offset: Int, forceRefresh: Boolean): DetailPagingPage =
      when (val context = pagingContext) {
        PostPagingContext.None -> DetailPagingPage(emptyList(), null)
        is PostPagingContext.Popular -> {
          val page =
              postRepo.getPopularPostsPage(
                  platform = platform,
                  date = context.date,
                  period = context.period,
                  offset = offset,
                  forceRefresh = forceRefresh,
              )
          DetailPagingPage(page.posts, page.pageInfo)
        }
        is PostPagingContext.Search -> {
          if (context.query.isBlank()) {
            val page =
                postRepo.getPopularPostsPage(
                    platform = platform,
                    date = context.defaultPopularDate,
                    period = "day",
                    offset = offset,
                    forceRefresh = forceRefresh,
                )
            DetailPagingPage(page.posts, page.pageInfo)
          } else {
            val page =
                postRepo.searchPostsPage(
                    platform = platform,
                    query = context.query,
                    offset = offset,
                    tag = null,
                    service = null,
                    forceRefresh = forceRefresh,
                )
            DetailPagingPage(page.items, page.pageInfo)
          }
        }
        is PostPagingContext.Tag -> {
          val page = postRepo.getPostsByTagPage(platform, context.tag, offset, forceRefresh)
          DetailPagingPage(page.items, page.pageInfo)
        }
        is PostPagingContext.Creator -> {
          val page =
              postRepo.getCreatorPostsPage(
                  platform = platform,
                  service = context.service,
                  creatorId = context.creatorId,
                  offset = offset,
                  forceRefresh = forceRefresh,
              )
          DetailPagingPage(page.items, page.pageInfo)
        }
      }

  fun loadPostDetail(post: Post) {
    if (post.id in loadedDetailIds || post.id in loadingDetailIds) return
    loadingDetailIds += post.id
    mutableState.value =
        mutableState.value.copy(
            loadingDetailPostIds = mutableState.value.loadingDetailPostIds + post.id
        )
    log.i {
      "加载Post详情 -> 开始(platform=${platform.name},service=${post.service},creator=${post.creatorId},post=${post.id})"
    }
    screenModelScope.launch {
      runCatching { postRepo.getPost(platform, post.service, post.creatorId, post.id) }
          .onSuccess { detail ->
            loadedDetailIds += post.id
            val current = mutableState.value
            val index =
                current.posts.indexOfFirst { it.id == post.id && it.service == post.service }
            if (index >= 0) {
              val updated = current.posts.toMutableList()
              updated[index] = detail
              mutableState.value =
                  current.copy(
                      posts = updated,
                      loadingDetailPostIds = current.loadingDetailPostIds - post.id,
                  )
            } else {
              mutableState.value =
                  current.copy(loadingDetailPostIds = current.loadingDetailPostIds - post.id)
            }
            log.i { "加载Post详情 -> 成功(${summarizePost(detail)},${summarizePostFiles(detail)})" }
            if (detail.content.isNullOrBlank()) {
              log.w {
                "加载Post详情 -> 内容为空(service=${detail.service},creator=${detail.creatorId},post=${detail.id})"
              }
            }
            if (detail.allFiles().isEmpty()) {
              log.w {
                "加载Post详情 -> 附件为空(service=${detail.service},creator=${detail.creatorId},post=${detail.id})"
              }
            }
            val missingImageUrls = detail.imageFiles().count { it.path.isNullOrBlank() }
            if (missingImageUrls > 0) {
              log.w {
                "加载Post详情 -> 图片URL缺失(service=${detail.service},creator=${detail.creatorId},post=${detail.id},count=$missingImageUrls)"
              }
            }
          }
          .onFailure {
            log.e(it) {
              "加载Post详情 -> 失败(platform=${platform.name},service=${post.service},creator=${post.creatorId},post=${post.id})"
            }
            mutableState.value =
                mutableState.value.copy(
                    loadingDetailPostIds = mutableState.value.loadingDetailPostIds - post.id
                )
          }
      loadingDetailIds -= post.id
    }
  }

  fun loadComments(post: Post) {
    if (mutableState.value.postComments.containsKey(post.id)) return
    if (post.id in loadingCommentIds) return
    loadingCommentIds += post.id
    screenModelScope.launch {
      runCatching { postRepo.getPostComments(platform, post.service, post.creatorId, post.id) }
          .onSuccess { comments ->
            val current = mutableState.value
            mutableState.value =
                current.copy(postComments = current.postComments + (post.id to comments))
            log.i { "加载Post评论 -> 成功(post=${post.id},count=${comments.size})" }
          }
          .onFailure { log.w(it) { "加载Post评论 -> 失败(post=${post.id})" } }
      loadingCommentIds -= post.id
    }
  }

  fun getComments(post: Post): List<Comment> =
      mutableState.value.postComments[post.id] ?: emptyList()

  fun requestFullImage(postId: String, fullUrl: String) {
    if (fullUrl.isBlank()) return
    val current = mutableState.value
    val requested = current.requestedFullImageUrls[postId].orEmpty()
    if (fullUrl in requested) return
    mutableState.value =
        current.copy(
            requestedFullImageUrls =
                current.requestedFullImageUrls + (postId to (requested + fullUrl))
        )
  }

  fun requestFullImages(postId: String, fullUrls: Collection<String>) {
    val validUrls = fullUrls.filter { it.isNotBlank() }.toSet()
    if (validUrls.isEmpty()) return
    val current = mutableState.value
    val requested = current.requestedFullImageUrls[postId].orEmpty()
    val merged = requested + validUrls
    if (merged == requested) return
    mutableState.value =
        current.copy(requestedFullImageUrls = current.requestedFullImageUrls + (postId to merged))
  }

  suspend fun downloadFile(url: String): ByteArray = postRepo.downloadFile(url)

  fun loadCreatorInfo(post: Post) {
    val key = "${post.service}:${post.creatorId}"
    if (mutableState.value.postCreators.containsKey(key)) return
    screenModelScope.launch {
      runCatching { creatorRepo.getAllCreators(platform, false) }
          .onSuccess { creators ->
            val creator =
                creators.firstOrNull { it.service == post.service && it.id == post.creatorId }
            if (creator != null) {
              mutableState.value =
                  mutableState.value.copy(
                      postCreators = mutableState.value.postCreators + (key to creator)
                  )
            }
          }
          .onFailure {
            log.w(it) { "加载Creator信息 -> 失败(service=${post.service},creator=${post.creatorId})" }
          }
    }
  }

  fun getCreator(post: Post): Creator? =
      mutableState.value.postCreators["${post.service}:${post.creatorId}"]

  fun translateContent(post: Post) {
    val content = post.content?.takeIf { it.isNotBlank() } ?: return
    val existing = mutableState.value.postTranslations[post.id]
    if (existing?.showTranslation == true) {
      mutableState.value =
          mutableState.value.copy(
              postTranslations =
                  mutableState.value.postTranslations +
                      (post.id to existing.copy(showTranslation = false))
          )
      return
    }
    if (existing != null && existing.blocks.isNotEmpty() && !existing.isTranslating) {
      mutableState.value =
          mutableState.value.copy(
              postTranslations =
                  mutableState.value.postTranslations +
                      (post.id to existing.copy(showTranslation = true))
          )
      return
    }

    screenModelScope.launch {
      val blocks = translationService.extractBlocks(content)
      if (blocks.isEmpty()) return@launch
      mutableState.value =
          mutableState.value.copy(
              postTranslations =
                  mutableState.value.postTranslations +
                      (post.id to
                          ContentTranslationState(
                              blocks =
                                  blocks.map {
                                    TranslationBlockState(
                                        originalHtml = it.originalHtml,
                                        status = TranslationStatus.PENDING,
                                    )
                                  },
                              isTranslating = true,
                              showTranslation = true,
                          ))
          )
      runCatching {
            translationService.translateBlocks(blocks) { index, result ->
              updatePostTranslationBlock(post.id, index, result)
            }
          }
          .onFailure { error ->
            log.e(error) { "翻译Post内容 -> 失败(post=${post.id})" }
            mutableState.value =
                mutableState.value.copy(
                    postTranslations =
                        mutableState.value.postTranslations +
                            (post.id to
                                (mutableState.value.postTranslations[post.id]
                                        ?: ContentTranslationState())
                                    .copy(
                                        blocks =
                                            (mutableState.value.postTranslations[post.id]?.blocks
                                                    ?: emptyList())
                                                .map {
                                                  if (it.status == TranslationStatus.PENDING)
                                                      it.copy(status = TranslationStatus.FAILURE)
                                                  else it
                                                },
                                        isTranslating = false,
                                        showTranslation = true,
                                    ))
                )
          }
      mutableState.value =
          mutableState.value.copy(
              postTranslations =
                  mutableState.value.postTranslations +
                      (post.id to
                          (mutableState.value.postTranslations[post.id]
                                  ?: ContentTranslationState())
                              .copy(isTranslating = false, showTranslation = true))
          )
    }
  }

  fun loadFavoriteStatus(post: Post) {
    if (favoriteStatusDisabled) return
    if (!postRepo.hasSession(platform)) {
      log.d {
        "收藏状态 -> 跳过(未登录,platform=${platform.name},service=${post.service},creator=${post.creatorId},post=${post.id})"
      }
      return
    }
    screenModelScope.launch {
      runCatching { postRepo.isFavoritePost(platform, post.service, post.creatorId, post.id) }
          .onSuccess { isFavorite ->
            val current = mutableState.value.favoritePostIds
            mutableState.value =
                mutableState.value.copy(
                    favoritePostIds = if (isFavorite) current + post.id else current - post.id,
                    favoriteErrorMessage = null,
                )
            log.i {
              "收藏状态 -> 成功(platform=${platform.name},service=${post.service},creator=${post.creatorId},post=${post.id},favorite=$isFavorite)"
            }
          }
          .onFailure {
            log.w(it) {
              "收藏状态 -> 失败(platform=${platform.name},service=${post.service},creator=${post.creatorId},post=${post.id})"
            }
            if (it is AuthRequiredException) favoriteStatusDisabled = true
            mutableState.value = mutableState.value.copy(favoriteErrorMessage = it.message)
          }
    }
  }

  fun toggleFavoritePost(post: Post) {
    if (!postRepo.hasSession(platform)) {
      log.w {
        "收藏Post -> 失败(未登录,platform=${platform.name},service=${post.service},creator=${post.creatorId},post=${post.id})"
      }
      mutableState.value =
          mutableState.value.copy(favoriteErrorMessage = AuthRequiredException().message)
      return
    }
    val current = mutableState.value
    val wasFavorite = post.id in current.favoritePostIds
    mutableState.value =
        current.copy(
            favoritePostIds =
                if (wasFavorite) current.favoritePostIds - post.id
                else current.favoritePostIds + post.id,
            favoriteErrorMessage = null,
        )
    screenModelScope.launch {
      runCatching {
            if (wasFavorite) {
              postRepo.removeFavoritePost(platform, post.service, post.creatorId, post.id)
            } else {
              postRepo.addFavoritePost(platform, post.service, post.creatorId, post.id)
            }
          }
          .onSuccess {
            log.i {
              "收藏Post -> 成功(platform=${platform.name},service=${post.service},creator=${post.creatorId},post=${post.id},favorite=${!wasFavorite})"
            }
          }
          .onFailure {
            log.e(it) {
              "收藏Post -> 失败(platform=${platform.name},service=${post.service},creator=${post.creatorId},post=${post.id})"
            }
            val latest = mutableState.value
            mutableState.value =
                latest.copy(
                    favoritePostIds =
                        if (wasFavorite) latest.favoritePostIds + post.id
                        else latest.favoritePostIds - post.id,
                    favoriteErrorMessage = it.message,
                )
          }
    }
  }

  private fun schedulePrefetch(index: Int) {
    prefetchJob?.cancel()
    prefetchJob =
        screenModelScope.launch {
          delay(PAGER_PREFETCH_DEBOUNCE_MS)
          val posts = mutableState.value.posts
          listOf(index + 1, index + 2, index - 1).forEach { i ->
            posts.getOrNull(i)?.let { loadPostDetail(it) }
          }
        }
  }

  private fun updatePostTranslationBlock(
      postId: String,
      index: Int,
      result: TranslationBlockResult,
  ) {
    val currentState = mutableState.value
    val translation = currentState.postTranslations[postId] ?: return
    if (index !in translation.blocks.indices) return
    val updatedBlocks = translation.blocks.toMutableList()
    updatedBlocks[index] =
        when (result) {
          is TranslationBlockResult.Success ->
              updatedBlocks[index].copy(
                  translated = result.translatedText,
                  status = TranslationStatus.SUCCESS,
              )
          TranslationBlockResult.EmptyResult ->
              updatedBlocks[index].copy(status = TranslationStatus.EMPTY)
          is TranslationBlockResult.Failure -> {
            log.w(result.cause) { "翻译Post block -> 失败(post=$postId,index=$index)" }
            updatedBlocks[index].copy(status = TranslationStatus.FAILURE)
          }
        }
    mutableState.value =
        currentState.copy(
            postTranslations =
                currentState.postTranslations +
                    (postId to translation.copy(blocks = updatedBlocks, showTranslation = true))
        )
  }
}
