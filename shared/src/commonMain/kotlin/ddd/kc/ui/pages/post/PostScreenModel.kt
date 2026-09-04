package ddd.kc.ui.pages.post

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Comment
import ddd.kc.data.model.Creator
import ddd.kc.data.model.PageInfo
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostKey
import ddd.kc.data.model.QueryError
import ddd.kc.data.model.allFiles
import ddd.kc.data.model.creatorId
import ddd.kc.data.model.creatorKey
import ddd.kc.data.model.imageFiles
import ddd.kc.data.model.key
import ddd.kc.data.remote.network.toQueryError
import ddd.kc.data.remote.repository.CreatorRepository
import ddd.kc.data.remote.repository.PostRepository
import ddd.kc.data.remote.translation.TranslationBlockResult
import ddd.kc.data.remote.translation.TranslationEngine
import ddd.kc.ui.components.paging.ContentTranslationState
import ddd.kc.ui.components.paging.DEFAULT_PAGE_SIZE
import ddd.kc.ui.components.paging.OffsetPagingMachine
import ddd.kc.ui.components.paging.OffsetPagingState
import ddd.kc.ui.components.paging.PAGER_NEXT_PREFETCH_COUNT
import ddd.kc.ui.components.paging.PAGER_PREFETCH_DEBOUNCE_MS
import ddd.kc.ui.components.paging.PAGER_PREVIOUS_PREFETCH_COUNT
import ddd.kc.ui.components.paging.TranslationBlockState
import ddd.kc.ui.components.paging.TranslationStatus
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import ddd.kc.utils.logging.summarizePost
import ddd.kc.utils.logging.summarizePostFiles
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_SIZE = DEFAULT_PAGE_SIZE
private val log = KcLog.withTag("PostScreenModel")

private data class DetailPagingPage(
    val posts: List<Post>,
    val pageInfo: PageInfo?,
)

class PostScreenModel(
    private val postRepo: PostRepository,
    private val creatorRepo: CreatorRepository,
    private val translationService: TranslationEngine,
    initialPosts: List<Post>,
    startIndex: Int,
    private val initialOffset: Int = 0,
    initialHasMore: Boolean = false,
    private val pagingContext: PostPagingContext = PostPagingContext.None,
) :
    StateScreenModel<PostPagerUiState>(
        PostPagerUiState(
            paging =
                OffsetPagingState(
                    items = initialPosts,
                    startOffset = initialOffset,
                    offset = initialOffset + initialPosts.size,
                    hasMore = pagingContext != PostPagingContext.None && initialHasMore,
                ),
            currentIndex = startIndex,
        )
    ) {

  private val pagingReducer = OffsetPagingMachine<Post, PostKey> { it.key }
  private var prefetchJob: Job? = null
  private val updatingFavoritePosts = mutableSetOf<PostKey>()
  private val loadedDetailIds = mutableSetOf<PostKey>()
  private val loadingDetailIds = mutableSetOf<PostKey>()
  private val loadingCommentIds = mutableSetOf<PostKey>()
  private val favoriteRequestTokens = mutableMapOf<PostKey, Long>()

  private fun beginFavoriteRequest(key: PostKey): Long {
    val next = (favoriteRequestTokens[key] ?: 0L) + 1L
    favoriteRequestTokens[key] = next
    return next
  }

  private fun isCurrentFavoriteRequest(key: PostKey, token: Long): Boolean =
      favoriteRequestTokens[key] == token

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
    if (!pagingReducer.canLoadMore(state.paging, force = true)) return
    if (pagingContext == PostPagingContext.None) return
    val nextOffset = state.offset
    log.i { "加载更多Post -> 开始(offset=$nextOffset)" }
    mutableState.value = state.copy(paging = pagingReducer.beginAppend(state.paging))
    screenModelScope.launch {
      resultOfSuspend { fetchPagingPage(nextOffset, forceRefresh = false) }
          .onSuccess { page ->
            val current = mutableState.value
            mutableState.value =
                current.copy(
                    paging =
                        pagingReducer.reduceAppend(
                            current.paging,
                            page.posts,
                            page.pageInfo?.hasNext ?: (page.posts.size >= PAGE_SIZE),
                            nextOffset + page.posts.size,
                            page.pageInfo,
                        )
                )
            log.i {
              "加载更多Post -> 成功(count=${page.posts.size},merged=${mutableState.value.posts.size})"
            }
          }
          .onFailure {
            log.e(it) { "加载更多Post -> 失败" }
            mutableState.value =
                mutableState.value.copy(
                    paging = pagingReducer.reduceAppendError(mutableState.value.paging, it)
                )
          }
    }
  }

  private fun loadPrevious() {
    val state = mutableState.value
    if (!pagingReducer.canLoadPrevious(state.paging) || pagingContext == PostPagingContext.None)
        return
    val previousOffset = (state.startOffset - PAGE_SIZE).coerceAtLeast(0)
    log.i { "加载上一页Post -> 开始(offset=$previousOffset)" }
    mutableState.value = state.copy(paging = pagingReducer.beginPrepend(state.paging))
    screenModelScope.launch {
      resultOfSuspend { fetchPagingPage(previousOffset, forceRefresh = false) }
          .onSuccess { page ->
            val current = mutableState.value
            val oldFirstKey = current.posts.firstOrNull()?.key
            val paging =
                pagingReducer.reducePrepend(
                    current.paging,
                    page.posts,
                    current.paging.hasMore,
                    previousOffset,
                    page.pageInfo,
                )
            val prependedCount =
                oldFirstKey?.let { key ->
                  paging.items.indexOfFirst { it.key == key }.coerceAtLeast(0)
                } ?: 0
            mutableState.value =
                current.copy(
                    paging = paging,
                    currentIndex = current.currentIndex + prependedCount,
                )
            log.i { "加载上一页Post -> 成功(count=${page.posts.size},merged=${paging.items.size})" }
          }
          .onFailure {
            log.e(it) { "加载上一页Post -> 失败" }
            mutableState.value =
                mutableState.value.copy(
                    paging = pagingReducer.reducePrependError(mutableState.value.paging, it)
                )
          }
    }
  }

  private suspend fun fetchPagingPage(offset: Int, forceRefresh: Boolean): DetailPagingPage =
      when (val context = pagingContext) {
        PostPagingContext.None -> DetailPagingPage(emptyList(), null)
        is PostPagingContext.Popular -> {
          val page =
              postRepo.getPopularPostsPage(
                  date = context.date,
                  period = context.period,
                  offset = offset,
                  forceRefresh = forceRefresh,
                  aiFilter = context.aiFilter,
              )
          DetailPagingPage(page.posts, page.pageInfo)
        }
        is PostPagingContext.Search -> {
          if (context.query.isBlank()) {
            val page =
                postRepo.getPopularPostsPage(
                    date = context.defaultPopularDate,
                    period = "day",
                    offset = offset,
                    forceRefresh = forceRefresh,
                    aiFilter = context.aiFilter,
                )
            DetailPagingPage(page.posts, page.pageInfo)
          } else {
            val page =
                postRepo.searchPostsPage(
                    query = context.query,
                    offset = offset,
                    tag = null,
                    service = null,
                    forceRefresh = forceRefresh,
                    aiFilter = context.aiFilter,
                )
            DetailPagingPage(page.items, page.pageInfo)
          }
        }
        is PostPagingContext.Tag -> {
          val page = postRepo.getPostsByTagPage(context.tag, offset, forceRefresh)
          DetailPagingPage(page.items, page.pageInfo)
        }
        is PostPagingContext.Creator -> {
          val page =
              postRepo.getCreatorPostsPage(
                  service = context.service,
                  creatorId = context.creatorId,
                  offset = offset,
                  forceRefresh = forceRefresh,
              )
          DetailPagingPage(page.items, page.pageInfo)
        }
      }

  fun loadPostDetail(post: Post) {
    if (post.key in loadedDetailIds || post.key in loadingDetailIds) return
    loadingDetailIds += post.key
    mutableState.value =
        mutableState.value.copy(
            loadingDetailPostIds = mutableState.value.loadingDetailPostIds + post.key
        )
    log.i { "加载Post详情 -> 开始(service=${post.service},creator=${post.creatorId},post=${post.id})" }
    screenModelScope.launch {
      resultOfSuspend { postRepo.getPost(post.service, post.creatorId, post.id) }
          .onSuccess { detail ->
            loadedDetailIds += post.key
            val current = mutableState.value
            val index = current.posts.indexOfFirst { it.key == post.key }
            if (index >= 0) {
              val updated = current.posts.toMutableList()
              updated[index] = detail
              mutableState.value =
                  current.copy(
                      paging = current.paging.copy(items = updated),
                      loadingDetailPostIds = current.loadingDetailPostIds - post.key,
                  )
            } else {
              mutableState.value =
                  current.copy(loadingDetailPostIds = current.loadingDetailPostIds - post.key)
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
              "加载Post详情 -> 失败(service=${post.service},creator=${post.creatorId},post=${post.id})"
            }
            mutableState.value =
                mutableState.value.copy(
                    loadingDetailPostIds = mutableState.value.loadingDetailPostIds - post.key
                )
          }
      loadingDetailIds -= post.key
    }
  }

  fun loadComments(post: Post) {
    if (mutableState.value.postComments.containsKey(post.key)) return
    if (post.key in loadingCommentIds) return
    loadingCommentIds += post.key
    screenModelScope.launch {
      resultOfSuspend { postRepo.getPostComments(post.service, post.creatorId, post.id) }
          .onSuccess { comments ->
            val current = mutableState.value
            mutableState.value =
                current.copy(
                    postComments = current.postComments + (post.key to comments),
                    postCommentErrors = current.postCommentErrors - post.key,
                )
            log.i { "加载Post评论 -> 成功(post=${post.id},count=${comments.size})" }
          }
          .onFailure {
            log.w(it) { "加载Post评论 -> 失败(post=${post.id})" }
            mutableState.value =
                mutableState.value.copy(
                    postCommentErrors =
                        mutableState.value.postCommentErrors + (post.key to it.toQueryError())
                )
          }
      loadingCommentIds -= post.key
    }
  }

  fun getComments(post: Post): List<Comment> =
      mutableState.value.postComments[post.key] ?: emptyList()

  fun requestFullImage(postKey: PostKey, fullUrl: String) {
    if (fullUrl.isBlank()) return
    val current = mutableState.value
    val requested = current.requestedFullImageUrls[postKey].orEmpty()
    if (fullUrl in requested) return
    mutableState.value =
        current.copy(
            requestedFullImageUrls =
                current.requestedFullImageUrls + (postKey to (requested + fullUrl))
        )
  }

  fun requestFullImages(postKey: PostKey, fullUrls: Collection<String>) {
    val validUrls = fullUrls.filter { it.isNotBlank() }.toSet()
    if (validUrls.isEmpty()) return
    val current = mutableState.value
    val requested = current.requestedFullImageUrls[postKey].orEmpty()
    val merged = requested + validUrls
    if (merged == requested) return
    mutableState.value =
        current.copy(requestedFullImageUrls = current.requestedFullImageUrls + (postKey to merged))
  }

  suspend fun downloadFile(url: String): ByteArray = postRepo.downloadFile(url)

  fun loadCreatorInfo(post: Post) {
    val key = post.creatorKey
    if (mutableState.value.postCreators.containsKey(key)) return
    screenModelScope.launch {
      resultOfSuspend { creatorRepo.getCreator(key) }
          .onSuccess { creator ->
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

  fun getCreator(post: Post): Creator? = mutableState.value.postCreators[post.creatorKey]

  fun translateContent(post: Post) {
    if (!translationService.isEnabled()) return
    val content = post.content?.takeIf { it.isNotBlank() } ?: return
    val existing = mutableState.value.postTranslations[post.key]
    if (existing?.showTranslation == true) {
      mutableState.value =
          mutableState.value.copy(
              postTranslations =
                  mutableState.value.postTranslations +
                      (post.key to existing.copy(showTranslation = false))
          )
      return
    }
    if (existing != null && existing.blocks.isNotEmpty() && !existing.isTranslating) {
      mutableState.value =
          mutableState.value.copy(
              postTranslations =
                  mutableState.value.postTranslations +
                      (post.key to existing.copy(showTranslation = true))
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
                      (post.key to
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
      resultOfSuspend {
            translationService.translateBlocks(blocks) { index, result ->
              updatePostTranslationBlock(post.key, index, result)
            }
          }
          .onFailure { error ->
            log.e(error) { "翻译Post内容 -> 失败(post=${post.id})" }
            mutableState.value =
                mutableState.value.copy(
                    postTranslations =
                        mutableState.value.postTranslations +
                            (post.key to
                                (mutableState.value.postTranslations[post.key]
                                        ?: ContentTranslationState())
                                    .copy(
                                        blocks =
                                            (mutableState.value.postTranslations[post.key]?.blocks
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
                      (post.key to
                          (mutableState.value.postTranslations[post.key]
                                  ?: ContentTranslationState())
                              .copy(isTranslating = false, showTranslation = true))
          )
    }
  }

  fun loadFavoriteStatus(post: Post) {
    if (!postRepo.hasSession()) {
      log.d { "收藏状态 -> 跳过(未登录,service=${post.service},creator=${post.creatorId},post=${post.id})" }
      return
    }
    val requestToken = beginFavoriteRequest(post.key)
    screenModelScope.launch {
      resultOfSuspend { postRepo.isFavoritePost(post.service, post.creatorId, post.id) }
          .onSuccess { isFavorite ->
            if (!isCurrentFavoriteRequest(post.key, requestToken)) return@onSuccess
            val current = mutableState.value.favoritePostIds
            mutableState.value =
                mutableState.value.copy(
                    favoritePostIds = if (isFavorite) current + post.key else current - post.key,
                    favoriteError = null,
                )
            log.i {
              "收藏状态 -> 成功(service=${post.service},creator=${post.creatorId},post=${post.id},favorite=$isFavorite)"
            }
          }
          .onFailure {
            if (!isCurrentFavoriteRequest(post.key, requestToken)) return@onFailure
            log.w(it) {
              "收藏状态 -> 失败(service=${post.service},creator=${post.creatorId},post=${post.id})"
            }
            mutableState.value = mutableState.value.copy(favoriteError = it.toQueryError())
          }
    }
  }

  fun toggleFavoritePost(post: Post) {
    if (!postRepo.hasSession()) {
      log.w {
        "收藏Post -> 失败(未登录,service=${post.service},creator=${post.creatorId},post=${post.id})"
      }
      mutableState.value = mutableState.value.copy(favoriteError = QueryError.Unauthorized())
      return
    }
    if (!updatingFavoritePosts.add(post.key)) return
    val requestToken = beginFavoriteRequest(post.key)
    val current = mutableState.value
    val wasFavorite = post.key in current.favoritePostIds
    mutableState.value =
        current.copy(
            favoritePostIds =
                if (wasFavorite) current.favoritePostIds - post.key
                else current.favoritePostIds + post.key,
            favoriteError = null,
        )
    screenModelScope.launch {
      try {
        resultOfSuspend {
              if (wasFavorite) {
                postRepo.removeFavoritePost(post.service, post.creatorId, post.id)
              } else {
                postRepo.addFavoritePost(post.service, post.creatorId, post.id)
              }
            }
            .onSuccess {
              if (!isCurrentFavoriteRequest(post.key, requestToken)) return@onSuccess
              log.i {
                "收藏Post -> 成功(service=${post.service},creator=${post.creatorId},post=${post.id},favorite=${!wasFavorite})"
              }
            }
            .onFailure {
              if (!isCurrentFavoriteRequest(post.key, requestToken)) return@onFailure
              log.e(it) {
                "收藏Post -> 失败(service=${post.service},creator=${post.creatorId},post=${post.id})"
              }
              val latest = mutableState.value
              mutableState.value =
                  latest.copy(
                      favoritePostIds =
                          if (wasFavorite) latest.favoritePostIds + post.key
                          else latest.favoritePostIds - post.key,
                      favoriteError = it.toQueryError(),
                  )
            }
      } finally {
        updatingFavoritePosts -= post.key
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
      postKey: PostKey,
      index: Int,
      result: TranslationBlockResult,
  ) {
    val currentState = mutableState.value
    val translation = currentState.postTranslations[postKey] ?: return
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
            log.w(result.cause) { "翻译Post block -> 失败(post=$postKey,index=$index)" }
            updatedBlocks[index].copy(status = TranslationStatus.FAILURE)
          }
        }
    mutableState.value =
        currentState.copy(
            postTranslations =
                currentState.postTranslations +
                    (postKey to translation.copy(blocks = updatedBlocks, showTranslation = true))
        )
  }
}
