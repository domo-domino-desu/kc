package ddd.kc.ui.pages.creator

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Announcement
import ddd.kc.data.model.CommunityMessage
import ddd.kc.data.model.Creator
import ddd.kc.data.model.CreatorKey
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostKey
import ddd.kc.data.model.Tag
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
import ddd.kc.ui.components.paging.PagingAnchor
import ddd.kc.ui.components.paging.TranslationBlockState
import ddd.kc.ui.components.paging.TranslationStatus
import ddd.kc.utils.coroutines.resultOfSuspend
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_SIZE = DEFAULT_PAGE_SIZE
private val log = KcLog.withTag("CreatorScreenModel")

class CreatorScreenModel(
    private val postRepo: PostRepository,
    private val creatorRepo: CreatorRepository,
    private val translationService: TranslationEngine,
    initialCreators: List<Creator>,
    startIndex: Int,
) :
    StateScreenModel<CreatorPagerUiState>(
        CreatorPagerUiState(
            creators = initialCreators,
            currentIndex = startIndex,
            hasMore = false,
        )
    ) {
  private enum class RequestKind {
    ANNOUNCEMENTS,
    POSTS,
    TAGS,
    LINKS,
    SIMILAR,
    COMMUNITY,
  }

  private val requestTokens = mutableMapOf<Pair<RequestKind, CreatorKey>, Long>()
  private var prefetchJob: Job? = null

  private fun beginRequest(kind: RequestKind, key: CreatorKey): Long {
    val next = (requestTokens[kind to key] ?: 0L) + 1L
    requestTokens[kind to key] = next
    return next
  }

  private fun isCurrentRequest(kind: RequestKind, key: CreatorKey, token: Long): Boolean =
      requestTokens[kind to key] == token

  private val updatingFavoriteCreators = mutableSetOf<CreatorKey>()
  private val favoriteRequestTokens = mutableMapOf<CreatorKey, Long>()
  private val postReducer = OffsetPagingMachine<Post, PostKey> { it.key }
  private val communityReducer = OffsetPagingMachine<CommunityMessage, String> { it.key }

  private fun beginFavoriteRequest(key: CreatorKey): Long {
    val next = (favoriteRequestTokens[key] ?: 0L) + 1L
    favoriteRequestTokens[key] = next
    return next
  }

  private fun isCurrentFavoriteRequest(key: CreatorKey, token: Long): Boolean =
      favoriteRequestTokens[key] == token

  fun onPageChanged(index: Int) {
    log.d { "打开Creator -> 切换(index=$index)" }
    mutableState.value = mutableState.value.copy(currentIndex = index)
    schedulePrefetch(index)
    if (index >= mutableState.value.creators.size - PAGER_NEXT_PREFETCH_COUNT) {
      loadMoreCreators()
    }
  }

  private fun schedulePrefetch(index: Int) {
    prefetchJob?.cancel()
    prefetchJob =
        screenModelScope.launch {
          delay(PAGER_PREFETCH_DEBOUNCE_MS)
          val creators = mutableState.value.creators
          listOf(index + 1, index + 2, index - 1).forEach { candidateIndex ->
            creators.getOrNull(candidateIndex)?.let(::prefetchCreatorData)
          }
        }
  }

  private fun prefetchCreatorData(creator: Creator) {
    loadCreatorPosts(creator)
    loadCreatorAnnouncements(creator)
    loadCreatorTags(creator)
    loadCreatorLinks(creator)
    loadSimilarCreators(creator)
  }

  fun previous() {
    val current = mutableState.value.currentIndex
    if (current > 0) mutableState.value = mutableState.value.copy(currentIndex = current - 1)
  }

  fun next() {
    val current = mutableState.value.currentIndex
    val state = mutableState.value
    if (current < state.creators.size - 1) {
      mutableState.value = state.copy(currentIndex = current + 1)
    } else if (state.hasMore) {
      loadMoreCreators()
    }
  }

  fun getCreatorPosts(creator: Creator): List<Post> = getCreatorPostSnapshot(creator).items

  fun getCreatorPostSnapshot(creator: Creator): OffsetPagingState<Post> =
      mutableState.value.creatorPostSnapshots[creator.key] ?: OffsetPagingState()

  fun getCreatorCommunity(creator: Creator): CreatorCommunityUiState =
      mutableState.value.creatorCommunities[creator.key] ?: CreatorCommunityUiState()

  fun selectContentTab(creator: Creator, tab: CreatorContentTab) {
    val state = mutableState.value
    if (state.selectedContentTabs[creator.key] == tab) return
    mutableState.value =
        state.copy(selectedContentTabs = state.selectedContentTabs + (creator.key to tab))
    if (tab == CreatorContentTab.COMMUNITY) loadCreatorCommunity(creator)
  }

  fun loadCreatorCommunity(creator: Creator, forceRefresh: Boolean = false) {
    if (!creator.service.equals("patreon", ignoreCase = true)) {
      updateCommunity(creator, CreatorCommunityUiState(available = false))
      return
    }
    val existing = getCreatorCommunity(creator)
    if (
        !forceRefresh &&
            (existing.available == false || existing.loading || existing.selectedLoungeId != null)
    ) {
      return
    }
    val token = beginRequest(RequestKind.COMMUNITY, creator.key)
    updateCommunity(creator, existing.copy(loading = true, error = null))
    screenModelScope.launch {
      resultOfSuspend {
            creatorRepo.getCreatorCommunityPage(
                creator.service,
                creator.id,
                forceRefresh = forceRefresh,
            )
          }
          .onSuccess { page ->
            if (!isCurrentRequest(RequestKind.COMMUNITY, creator.key, token)) return@onSuccess
            if (page == null) {
              updateCommunity(creator, CreatorCommunityUiState(available = false))
              return@onSuccess
            }
            val snapshot =
                communityReducer.reduceFirstPage(
                    OffsetPagingState(pageSize = COMMUNITY_PAGE_SIZE, loading = true),
                    page.messages,
                    page.pageInfo?.hasNext ?: (page.messages.size >= COMMUNITY_PAGE_SIZE),
                    COMMUNITY_PAGE_SIZE,
                    page.pageInfo,
                )
            updateCommunity(
                creator,
                CreatorCommunityUiState(
                    available = true,
                    lounges = page.lounges,
                    selectedLoungeId = page.selectedLoungeId,
                    loungeSnapshots = mapOf(page.selectedLoungeId to snapshot),
                ),
            )
          }
          .onFailure { error ->
            if (!isCurrentRequest(RequestKind.COMMUNITY, creator.key, token)) return@onFailure
            updateCommunity(creator, existing.copy(loading = false, error = error.toQueryError()))
          }
    }
  }

  fun selectCommunityLounge(creator: Creator, loungeId: String) {
    val current = getCreatorCommunity(creator)
    if (current.selectedLoungeId == loungeId) return
    updateCommunity(creator, current.copy(selectedLoungeId = loungeId, error = null))
    if (loungeId !in current.loungeSnapshots) {
      loadCommunityWindow(creator, loungeId, offset = 0)
    }
  }

  fun refreshCreatorCommunity(creator: Creator) {
    val state = getCreatorCommunity(creator)
    val loungeId = state.selectedLoungeId ?: return loadCreatorCommunity(creator, true)
    loadCommunityWindow(
        creator,
        loungeId,
        offset = state.selectedSnapshot.startOffset,
        forceRefresh = true,
    )
  }

  fun loadMoreCommunity(
      creator: Creator,
      loungeId: String? = getCreatorCommunity(creator).selectedLoungeId,
      retry: Boolean = false,
  ) {
    val state = getCreatorCommunity(creator)
    loungeId ?: return
    val snapshot =
        state.selectedSnapshot.takeIf { state.selectedLoungeId == loungeId }
            ?: state.loungeSnapshots[loungeId]
            ?: return
    if (!communityReducer.canLoadMore(snapshot, force = retry)) return
    updateCommunitySnapshot(creator, loungeId, communityReducer.beginAppend(snapshot))
    loadCommunityWindow(creator, loungeId, snapshot.offset, append = true)
  }

  fun loadPreviousCommunity(
      creator: Creator,
      loungeId: String? = getCreatorCommunity(creator).selectedLoungeId,
  ) {
    val state = getCreatorCommunity(creator)
    loungeId ?: return
    val snapshot =
        state.selectedSnapshot.takeIf { state.selectedLoungeId == loungeId }
            ?: state.loungeSnapshots[loungeId]
            ?: return
    if (!communityReducer.canLoadPrevious(snapshot)) return
    val offset = (snapshot.startOffset - COMMUNITY_PAGE_SIZE).coerceAtLeast(0)
    updateCommunitySnapshot(creator, loungeId, communityReducer.beginPrepend(snapshot))
    loadCommunityWindow(creator, loungeId, offset, prepend = true)
  }

  fun jumpCommunityToPage(creator: Creator, page: Int) {
    val state = getCreatorCommunity(creator)
    val loungeId = state.selectedLoungeId ?: return
    val snapshot = state.selectedSnapshot
    val target = page.coerceIn(1, snapshot.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (target - 1) * COMMUNITY_PAGE_SIZE
    updateCommunitySnapshot(creator, loungeId, communityReducer.beginJump(snapshot, offset))
    loadCommunityWindow(creator, loungeId, offset, replace = true, rollback = snapshot)
  }

  fun onCommunityViewportChanged(creator: Creator, anchor: PagingAnchor) {
    val state = getCreatorCommunity(creator)
    val loungeId = state.selectedLoungeId ?: return
    updateCommunitySnapshot(
        creator,
        loungeId,
        communityReducer.updateViewport(
            state.selectedSnapshot,
            anchor.index,
            anchor.offset,
            anchor.itemKey,
            COMMUNITY_PAGE_SIZE,
        ),
    )
  }

  fun onCommunityNavigationEffectHandled(creator: Creator, transactionId: Long) {
    val state = getCreatorCommunity(creator)
    val loungeId = state.selectedLoungeId ?: return
    updateCommunitySnapshot(
        creator,
        loungeId,
        communityReducer.consumeNavigationEffect(state.selectedSnapshot, transactionId),
    )
  }

  private fun loadCommunityWindow(
      creator: Creator,
      loungeId: String,
      offset: Int,
      forceRefresh: Boolean = false,
      append: Boolean = false,
      prepend: Boolean = false,
      replace: Boolean = false,
      rollback: OffsetPagingState<CommunityMessage>? = null,
  ) {
    val token = beginRequest(RequestKind.COMMUNITY, creator.key)
    screenModelScope.launch {
      resultOfSuspend {
            requireNotNull(
                creatorRepo.getCreatorCommunityPage(
                    creator.service,
                    creator.id,
                    loungeId,
                    offset,
                    forceRefresh,
                )
            )
          }
          .onSuccess { page ->
            if (!isCurrentRequest(RequestKind.COMMUNITY, creator.key, token)) return@onSuccess
            val current =
                getCreatorCommunity(creator).loungeSnapshots[loungeId]
                    ?: OffsetPagingState(pageSize = COMMUNITY_PAGE_SIZE)
            val hasMore = page.pageInfo?.hasNext ?: (page.messages.size >= COMMUNITY_PAGE_SIZE)
            val next =
                when {
                  append ->
                      communityReducer.reduceAppend(
                          current,
                          page.messages,
                          hasMore,
                          offset + COMMUNITY_PAGE_SIZE,
                      )
                  prepend ->
                      communityReducer.reducePrepend(
                          current,
                          page.messages,
                          current.hasMore,
                          offset,
                      )
                  else ->
                      communityReducer.reduceFirstPage(
                          current,
                          page.messages,
                          hasMore,
                          offset + COMMUNITY_PAGE_SIZE,
                          page.pageInfo,
                          offset,
                      )
                }
            val community = getCreatorCommunity(creator)
            updateCommunity(
                creator,
                community.copy(
                    available = true,
                    lounges = page.lounges.ifEmpty { community.lounges },
                    loungeSnapshots = community.loungeSnapshots + (loungeId to next),
                    loading = false,
                    error = null,
                ),
            )
          }
          .onFailure { error ->
            if (!isCurrentRequest(RequestKind.COMMUNITY, creator.key, token)) return@onFailure
            val state = getCreatorCommunity(creator)
            val current =
                state.loungeSnapshots[loungeId] ?: OffsetPagingState(pageSize = COMMUNITY_PAGE_SIZE)
            val failed =
                when {
                  append -> communityReducer.reduceAppendError(current, error)
                  prepend -> communityReducer.reducePrependError(current, error)
                  replace && rollback != null -> communityReducer.reduceJumpError(rollback, error)
                  else -> communityReducer.reduceFirstPageError(current, error)
                }
            updateCommunity(
                creator,
                state.copy(
                    loungeSnapshots = state.loungeSnapshots + (loungeId to failed),
                    loading = false,
                    error = error.toQueryError(),
                ),
            )
          }
    }
  }

  private fun updateCommunitySnapshot(
      creator: Creator,
      loungeId: String,
      snapshot: OffsetPagingState<CommunityMessage>,
  ) {
    val state = getCreatorCommunity(creator)
    updateCommunity(
        creator,
        state.copy(loungeSnapshots = state.loungeSnapshots + (loungeId to snapshot)),
    )
  }

  private fun updateCommunity(creator: Creator, state: CreatorCommunityUiState) {
    mutableState.value =
        mutableState.value.copy(
            creatorCommunities = mutableState.value.creatorCommunities + (creator.key to state)
        )
  }

  fun loadCreatorAnnouncements(creator: Creator, forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.creatorAnnouncements.containsKey(creator.key)) return
    val requestToken = beginRequest(RequestKind.ANNOUNCEMENTS, creator.key)
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorAnnouncementIds =
                mutableState.value.loadingCreatorAnnouncementIds + creator.key,
            announcementErrors = mutableState.value.announcementErrors - creator.key,
        )
    screenModelScope.launch {
      resultOfSuspend {
            creatorRepo.getCreatorAnnouncements(
                creator.service,
                creator.id,
                forceRefresh,
            )
          }
          .onSuccess { announcements ->
            if (!isCurrentRequest(RequestKind.ANNOUNCEMENTS, creator.key, requestToken)) {
              return@onSuccess
            }
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorAnnouncementIds =
                        mutableState.value.loadingCreatorAnnouncementIds - creator.key,
                    creatorAnnouncements =
                        mutableState.value.creatorAnnouncements +
                            (creator.key to
                                announcements.sortedByDescending { it.added.orEmpty() }),
                    announcementErrors = mutableState.value.announcementErrors - creator.key,
                )
            log.i {
              "加载Creator Announcements -> 成功(service=${creator.service},creator=${creator.id},count=${announcements.size})"
            }
          }
          .onFailure {
            if (!isCurrentRequest(RequestKind.ANNOUNCEMENTS, creator.key, requestToken)) {
              return@onFailure
            }
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorAnnouncementIds =
                        mutableState.value.loadingCreatorAnnouncementIds - creator.key,
                    announcementErrors =
                        mutableState.value.announcementErrors + (creator.key to it.toQueryError()),
                )
            log.e(it) {
              "加载Creator Announcements -> 失败(service=${creator.service},creator=${creator.id})"
            }
          }
    }
  }

  fun getCreatorAnnouncements(creator: Creator): List<Announcement> =
      mutableState.value.creatorAnnouncements[creator.key] ?: emptyList()

  fun translateAnnouncement(creator: Creator, announcement: Announcement) {
    if (!translationService.isEnabled()) return
    if (announcement.content.isBlank()) return
    val key = announcement.translationKey()
    val existing = mutableState.value.announcementTranslations[creator.key]?.get(key)
    if (existing?.showTranslation == true) {
      updateAnnouncementTranslation(creator.key, key, existing.copy(showTranslation = false))
      return
    }
    if (existing != null && existing.blocks.isNotEmpty() && !existing.isTranslating) {
      updateAnnouncementTranslation(creator.key, key, existing.copy(showTranslation = true))
      return
    }

    screenModelScope.launch {
      val blocks = translationService.extractBlocks(announcement.content)
      if (blocks.isEmpty()) return@launch
      updateAnnouncementTranslation(
          creator.key,
          key,
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
          ),
      )
      resultOfSuspend {
            translationService.translateBlocks(blocks) { index, result ->
              updateAnnouncementTranslationBlock(creator.key, key, index, result)
            }
          }
          .onFailure { error ->
            log.e(error) { "翻译Announcement -> 失败(creator=${creator.id},key=$key)" }
            val failed =
                mutableState.value.announcementTranslations[creator.key]?.get(key)
                    ?: ContentTranslationState()
            updateAnnouncementTranslation(
                creator.key,
                key,
                failed.copy(
                    blocks =
                        failed.blocks.map {
                          if (it.status == TranslationStatus.PENDING)
                              it.copy(status = TranslationStatus.FAILURE)
                          else it
                        },
                    isTranslating = false,
                    showTranslation = true,
                ),
            )
          }
      val done =
          mutableState.value.announcementTranslations[creator.key]?.get(key)
              ?: ContentTranslationState()
      updateAnnouncementTranslation(
          creator.key,
          key,
          done.copy(isTranslating = false, showTranslation = true),
      )
    }
  }

  fun translateAnnouncements(creator: Creator) {
    if (!translationService.isEnabled()) return
    val announcements = getCreatorAnnouncements(creator).filter { it.content.isNotBlank() }
    if (announcements.isEmpty()) return
    val keys = announcements.map { it.translationKey() }
    val currentTranslations = mutableState.value.announcementTranslations[creator.key] ?: emptyMap()
    val allVisible = keys.all { currentTranslations[it]?.showTranslation == true }
    if (allVisible) {
      mutableState.value =
          mutableState.value.copy(
              announcementTranslations =
                  mutableState.value.announcementTranslations +
                      (creator.key to
                          currentTranslations.mapValues { (_, state) ->
                            state.copy(showTranslation = false)
                          })
          )
      return
    }

    val allReady =
        keys.all { key ->
          val state = currentTranslations[key]
          state != null && state.blocks.isNotEmpty() && !state.isTranslating
        }
    if (allReady) {
      mutableState.value =
          mutableState.value.copy(
              announcementTranslations =
                  mutableState.value.announcementTranslations +
                      (creator.key to
                          currentTranslations.mapValues { (_, state) ->
                            state.copy(showTranslation = true)
                          })
          )
      return
    }

    screenModelScope.launch {
      announcements.forEach { announcement ->
        val key = announcement.translationKey()
        val latest = mutableState.value.announcementTranslations[creator.key] ?: emptyMap()
        val existing = latest[key]
        if (existing != null && existing.blocks.isNotEmpty() && !existing.isTranslating) {
          updateAnnouncementTranslation(creator.key, key, existing.copy(showTranslation = true))
          return@forEach
        }

        val blocks = translationService.extractBlocks(announcement.content)
        if (blocks.isEmpty()) return@forEach
        updateAnnouncementTranslation(
            creator.key,
            key,
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
            ),
        )
        resultOfSuspend {
              translationService.translateBlocks(blocks) { index, result ->
                updateAnnouncementTranslationBlock(creator.key, key, index, result)
              }
            }
            .onFailure { error ->
              log.e(error) { "翻译Announcement -> 失败(creator=${creator.id},key=$key)" }
              val failed =
                  (mutableState.value.announcementTranslations[creator.key]?.get(key)
                      ?: ContentTranslationState())
              updateAnnouncementTranslation(
                  creator.key,
                  key,
                  failed.copy(
                      blocks =
                          failed.blocks.map {
                            if (it.status == TranslationStatus.PENDING)
                                it.copy(status = TranslationStatus.FAILURE)
                            else it
                          },
                      isTranslating = false,
                      showTranslation = true,
                  ),
              )
            }
        val done =
            mutableState.value.announcementTranslations[creator.key]?.get(key)
                ?: ContentTranslationState()
        updateAnnouncementTranslation(
            creator.key,
            key,
            done.copy(isTranslating = false, showTranslation = true),
        )
      }
    }
  }

  fun loadCreatorPosts(creator: Creator, offset: Int = 0, forceRefresh: Boolean = false) {
    val existing = getCreatorPostSnapshot(creator)
    if (offset == 0 && !forceRefresh && (existing.items.isNotEmpty() || existing.loading)) return
    val requestToken = beginRequest(RequestKind.POSTS, creator.key)
    val loading = postReducer.beginLoad(existing, forceRefresh)
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds + creator.key,
            creatorPostSnapshots =
                mutableState.value.creatorPostSnapshots + (creator.key to loading),
        )
    screenModelScope.launch {
      log.i {
        "加载Creator Posts -> 开始(service=${creator.service},creator=${creator.id},offset=$offset)"
      }
      resultOfSuspend {
            postRepo.getCreatorPostsPage(
                creator.service,
                creator.id,
                offset,
                forceRefresh,
            )
          }
          .onSuccess { page ->
            if (!isCurrentRequest(RequestKind.POSTS, creator.key, requestToken)) return@onSuccess
            val posts = page.items
            val snapshot =
                postReducer.reduceFirstPage(
                    getCreatorPostSnapshot(creator),
                    posts,
                    page.pageInfo?.hasNext ?: (posts.size >= PAGE_SIZE),
                    offset + posts.size,
                    page.pageInfo,
                )
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.key,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots + (creator.key to snapshot),
                    creatorCommunities =
                        mutableState.value.creatorCommunities +
                            (creator.key to
                                getCreatorCommunity(creator)
                                    .copy(
                                        available = page.communityAvailable,
                                        error = null,
                                    )),
                )
            log.i {
              "加载Creator Posts -> 成功(service=${creator.service},creator=${creator.id},offset=$offset,count=${posts.size})"
            }
          }
          .onFailure {
            if (!isCurrentRequest(RequestKind.POSTS, creator.key, requestToken)) return@onFailure
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.key,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots +
                            (creator.key to
                                postReducer.reduceFirstPageError(
                                    getCreatorPostSnapshot(creator),
                                    it,
                                )),
                )
            log.e(it) {
              "加载Creator Posts -> 失败(service=${creator.service},creator=${creator.id},offset=$offset)"
            }
          }
    }
  }

  fun loadMoreCreatorPosts(creator: Creator) {
    val current = getCreatorPostSnapshot(creator)
    if (!postReducer.canLoadMore(current)) return
    val requestToken = beginRequest(RequestKind.POSTS, creator.key)
    val appending = postReducer.beginAppend(current)
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds + creator.key,
            creatorPostSnapshots =
                mutableState.value.creatorPostSnapshots + (creator.key to appending),
        )
    screenModelScope.launch {
      val offset = current.offset
      resultOfSuspend { postRepo.getCreatorPostsPage(creator.service, creator.id, offset, false) }
          .onSuccess { page ->
            if (!isCurrentRequest(RequestKind.POSTS, creator.key, requestToken)) return@onSuccess
            val posts = page.items
            val snapshot =
                postReducer.reduceAppend(
                    appending,
                    posts,
                    page.pageInfo?.hasNext ?: (posts.size >= PAGE_SIZE),
                    offset + posts.size,
                    null,
                )
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.key,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots + (creator.key to snapshot),
                )
          }
          .onFailure {
            if (!isCurrentRequest(RequestKind.POSTS, creator.key, requestToken)) return@onFailure
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.key,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots +
                            (creator.key to postReducer.reduceAppendError(appending, it)),
                )
          }
    }
  }

  fun loadPreviousCreatorPosts(creator: Creator) {
    val current = getCreatorPostSnapshot(creator)
    if (!postReducer.canLoadPrevious(current)) return
    val requestToken = beginRequest(RequestKind.POSTS, creator.key)
    val offset = (current.startOffset - PAGE_SIZE).coerceAtLeast(0)
    val prepending = postReducer.beginPrepend(current)
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds + creator.key,
            creatorPostSnapshots =
                mutableState.value.creatorPostSnapshots + (creator.key to prepending),
        )
    screenModelScope.launch {
      resultOfSuspend { postRepo.getCreatorPostsPage(creator.service, creator.id, offset, false) }
          .onSuccess { page ->
            if (!isCurrentRequest(RequestKind.POSTS, creator.key, requestToken)) return@onSuccess
            val snapshot =
                postReducer.reducePrepend(
                    prepending,
                    page.items,
                    getCreatorPostSnapshot(creator).hasMore,
                    offset,
                    null,
                )
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.key,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots + (creator.key to snapshot),
                )
          }
          .onFailure {
            if (!isCurrentRequest(RequestKind.POSTS, creator.key, requestToken)) return@onFailure
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.key,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots +
                            (creator.key to postReducer.reducePrependError(prepending, it)),
                )
          }
    }
  }

  fun jumpCreatorPostsToPage(creator: Creator, page: Int) {
    val current = getCreatorPostSnapshot(creator)
    val targetPage = page.coerceIn(1, current.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    val requestToken = beginRequest(RequestKind.POSTS, creator.key)
    val loading = postReducer.beginJump(current, offset)
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds + creator.key,
            creatorPostSnapshots =
                mutableState.value.creatorPostSnapshots + (creator.key to loading),
        )
    screenModelScope.launch {
      resultOfSuspend { postRepo.getCreatorPostsPage(creator.service, creator.id, offset, false) }
          .onSuccess { pageResult ->
            if (!isCurrentRequest(RequestKind.POSTS, creator.key, requestToken)) return@onSuccess
            val posts = pageResult.items
            val snapshot =
                postReducer.reduceFirstPage(
                    getCreatorPostSnapshot(creator),
                    posts,
                    pageResult.pageInfo?.hasNext ?: (posts.size >= PAGE_SIZE),
                    offset + posts.size,
                    pageResult.pageInfo,
                )
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.key,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots + (creator.key to snapshot),
                )
          }
          .onFailure {
            if (!isCurrentRequest(RequestKind.POSTS, creator.key, requestToken)) return@onFailure
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.key,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots +
                            (creator.key to postReducer.reduceJumpError(current, it)),
                )
          }
    }
  }

  fun onCreatorPostViewportChanged(creator: Creator, anchor: PagingAnchor) {
    val current = getCreatorPostSnapshot(creator)
    val next =
        postReducer.updateViewport(
            current,
            firstVisibleItemIndex = anchor.index,
            firstVisibleItemScrollOffset = anchor.offset,
            anchorKey = anchor.itemKey,
            pageSize = PAGE_SIZE,
        )
    if (next === current || next == current) return
    mutableState.value =
        mutableState.value.copy(
            creatorPostSnapshots = mutableState.value.creatorPostSnapshots + (creator.key to next),
        )
  }

  fun loadCreatorTags(creator: Creator, forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.creatorTags.containsKey(creator.key)) return
    val requestToken = beginRequest(RequestKind.TAGS, creator.key)
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorTagIds = mutableState.value.loadingCreatorTagIds + creator.key,
            tagErrors = mutableState.value.tagErrors - creator.key,
        )
    screenModelScope.launch {
      resultOfSuspend { creatorRepo.getCreatorTags(creator.service, creator.id, forceRefresh) }
          .onSuccess { tags ->
            if (!isCurrentRequest(RequestKind.TAGS, creator.key, requestToken)) return@onSuccess
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorTagIds = mutableState.value.loadingCreatorTagIds - creator.key,
                    creatorTags = mutableState.value.creatorTags + (creator.key to tags),
                    tagErrors = mutableState.value.tagErrors - creator.key,
                )
            log.i {
              "加载Creator Tags -> 成功(service=${creator.service},creator=${creator.id},count=${tags.size})"
            }
          }
          .onFailure {
            if (!isCurrentRequest(RequestKind.TAGS, creator.key, requestToken)) return@onFailure
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorTagIds = mutableState.value.loadingCreatorTagIds - creator.key,
                    tagErrors = mutableState.value.tagErrors + (creator.key to it.toQueryError()),
                )
            log.e(it) { "加载Creator Tags -> 失败(service=${creator.service},creator=${creator.id})" }
          }
    }
  }

  fun getCreatorTags(creator: Creator): List<Tag> =
      mutableState.value.creatorTags[creator.key] ?: emptyList()

  fun loadCreatorLinks(creator: Creator, forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.creatorLinks.containsKey(creator.key)) return
    val requestToken = beginRequest(RequestKind.LINKS, creator.key)
    mutableState.value =
        mutableState.value.copy(linkErrors = mutableState.value.linkErrors - creator.key)
    screenModelScope.launch {
      resultOfSuspend { creatorRepo.getCreatorLinks(creator.service, creator.id, forceRefresh) }
          .onSuccess { links ->
            if (!isCurrentRequest(RequestKind.LINKS, creator.key, requestToken)) return@onSuccess
            mutableState.value =
                mutableState.value.copy(
                    creatorLinks = mutableState.value.creatorLinks + (creator.key to links),
                    linkErrors = mutableState.value.linkErrors - creator.key,
                )
          }
          .onFailure {
            if (!isCurrentRequest(RequestKind.LINKS, creator.key, requestToken)) return@onFailure
            mutableState.value =
                mutableState.value.copy(
                    linkErrors = mutableState.value.linkErrors + (creator.key to it.toQueryError())
                )
            log.w(it) { "加载Creator Links -> 失败(service=${creator.service},creator=${creator.id})" }
          }
    }
  }

  fun getCreatorLinks(creator: Creator): List<Creator> =
      mutableState.value.creatorLinks[creator.key] ?: emptyList()

  fun loadSimilarCreators(creator: Creator, forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.similarCreators.containsKey(creator.key)) return
    val requestToken = beginRequest(RequestKind.SIMILAR, creator.key)
    mutableState.value =
        mutableState.value.copy(similarErrors = mutableState.value.similarErrors - creator.key)
    screenModelScope.launch {
      resultOfSuspend { creatorRepo.getSimilarCreators(creator.service, creator.id, forceRefresh) }
          .onSuccess { creators ->
            if (!isCurrentRequest(RequestKind.SIMILAR, creator.key, requestToken)) return@onSuccess
            mutableState.value =
                mutableState.value.copy(
                    similarCreators =
                        mutableState.value.similarCreators + (creator.key to creators),
                    similarErrors = mutableState.value.similarErrors - creator.key,
                )
          }
          .onFailure {
            if (!isCurrentRequest(RequestKind.SIMILAR, creator.key, requestToken)) return@onFailure
            mutableState.value =
                mutableState.value.copy(
                    similarErrors =
                        mutableState.value.similarErrors + (creator.key to it.toQueryError())
                )
            log.w(it) { "加载相似Creator -> 失败(service=${creator.service},creator=${creator.id})" }
          }
    }
  }

  fun getSimilarCreators(creator: Creator): List<Creator> =
      mutableState.value.similarCreators[creator.key] ?: emptyList()

  fun loadFavoriteStatus(creator: Creator) {
    if (!creatorRepo.hasSession()) {
      log.d { "Creator收藏状态 -> 跳过(未登录,service=${creator.service},creator=${creator.id})" }
      return
    }
    val requestToken = beginFavoriteRequest(creator.key)
    screenModelScope.launch {
      resultOfSuspend { creatorRepo.isFavoriteCreator(creator.service, creator.id) }
          .onSuccess { isFavorite ->
            if (!isCurrentFavoriteRequest(creator.key, requestToken)) return@onSuccess
            val current = mutableState.value.favoriteCreatorIds
            mutableState.value =
                mutableState.value.copy(
                    favoriteCreatorIds =
                        if (isFavorite) current + creator.key else current - creator.key,
                    favoriteError = null,
                )
            log.i {
              "Creator收藏状态 -> 成功(service=${creator.service},creator=${creator.id},favorite=$isFavorite)"
            }
          }
          .onFailure {
            if (!isCurrentFavoriteRequest(creator.key, requestToken)) return@onFailure
            log.w(it) { "Creator收藏状态 -> 失败(service=${creator.service},creator=${creator.id})" }
            mutableState.value = mutableState.value.copy(favoriteError = it.toQueryError())
          }
    }
  }

  fun toggleFavoriteCreator(creator: Creator) {
    if (!updatingFavoriteCreators.add(creator.key)) return
    val requestToken = beginFavoriteRequest(creator.key)
    val current = mutableState.value
    val wasFavorite = creator.key in current.favoriteCreatorIds
    mutableState.value =
        current.copy(
            favoriteCreatorIds =
                if (wasFavorite) current.favoriteCreatorIds - creator.key
                else current.favoriteCreatorIds + creator.key,
            favoriteError = null,
        )
    screenModelScope.launch {
      try {
        resultOfSuspend {
              if (wasFavorite) {
                creatorRepo.removeFavoriteCreator(creator.service, creator.id)
              } else {
                creatorRepo.addFavoriteCreator(creator.service, creator.id)
              }
            }
            .onSuccess {
              if (!isCurrentFavoriteRequest(creator.key, requestToken)) return@onSuccess
              log.i {
                "收藏Creator -> 成功(service=${creator.service},creator=${creator.id},favorite=${!wasFavorite})"
              }
            }
            .onFailure {
              if (!isCurrentFavoriteRequest(creator.key, requestToken)) return@onFailure
              log.e(it) { "收藏Creator -> 失败(service=${creator.service},creator=${creator.id})" }
              val latest = mutableState.value
              mutableState.value =
                  latest.copy(
                      favoriteCreatorIds =
                          if (wasFavorite) latest.favoriteCreatorIds + creator.key
                          else latest.favoriteCreatorIds - creator.key,
                      favoriteError = it.toQueryError(),
                  )
            }
      } finally {
        updatingFavoriteCreators -= creator.key
      }
    }
  }

  private fun loadMoreCreators() {
    val state = mutableState.value
    if (state.isLoadingMore || !state.hasMore) return
    log.i { "加载更多Creators -> 开始(current=${state.creators.size})" }
    mutableState.value = state.copy(isLoadingMore = true)
    screenModelScope.launch {
      resultOfSuspend { creatorRepo.getAllCreators(false) }
          .onSuccess { all ->
            val current = mutableState.value
            val existing = current.creators
            val existingIds = existing.mapTo(HashSet()) { it.id }
            val newOnes = all.filter { it.id !in existingIds }
            mutableState.value =
                current.copy(
                    creators = existing + newOnes,
                    hasMore = false,
                    isLoadingMore = false,
                )
          }
          .onFailure { mutableState.value = mutableState.value.copy(isLoadingMore = false) }
    }
  }

  private fun updateAnnouncementTranslation(
      creatorKey: CreatorKey,
      announcementKey: String,
      translationState: ContentTranslationState,
  ) {
    val current = mutableState.value
    val creatorTranslations = current.announcementTranslations[creatorKey] ?: emptyMap()
    mutableState.value =
        current.copy(
            announcementTranslations =
                current.announcementTranslations +
                    (creatorKey to (creatorTranslations + (announcementKey to translationState)))
        )
  }

  private fun updateAnnouncementTranslationBlock(
      creatorKey: CreatorKey,
      announcementKey: String,
      index: Int,
      result: TranslationBlockResult,
  ) {
    val translation =
        mutableState.value.announcementTranslations[creatorKey]?.get(announcementKey) ?: return
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
            log.w(result.cause) {
              "翻译Announcement block -> 失败(creator=$creatorKey,key=$announcementKey,index=$index)"
            }
            updatedBlocks[index].copy(status = TranslationStatus.FAILURE)
          }
        }
    updateAnnouncementTranslation(
        creatorKey,
        announcementKey,
        translation.copy(blocks = updatedBlocks, showTranslation = true),
    )
  }
}

fun Announcement.translationKey(): String =
    hash.ifBlank { added ?: "${service}:${userId}:${content.hashCode()}" }
