package ddd.kc.ui.pages.creator

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.application.translation.TranslationService
import ddd.kc.data.model.Announcement
import ddd.kc.data.model.Creator
import ddd.kc.data.model.DiscordChannel
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.Tag
import ddd.kc.data.network.AuthRequiredException
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.data.repository.DiscordRepository
import ddd.kc.data.repository.PostRepository
import ddd.kc.domain.translation.TranslationBlockResult
import ddd.kc.ui.state.ContentTranslationState
import ddd.kc.ui.state.PAGER_NEXT_PREFETCH_COUNT
import ddd.kc.ui.state.PaginationReducer
import ddd.kc.ui.state.PaginationSnapshot
import ddd.kc.ui.state.TranslationBlockState
import ddd.kc.ui.state.TranslationStatus
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50
private val log = KcLog.withTag("CreatorScreenModel")

class CreatorScreenModel(
    private val postRepo: PostRepository,
    private val creatorRepo: CreatorRepository,
    private val discordRepo: DiscordRepository,
    private val translationService: TranslationService,
    private val platform: Platform,
    initialCreators: List<Creator>,
    startIndex: Int,
) :
    StateScreenModel<CreatorPagerUiState>(
        CreatorPagerUiState(
            creators = initialCreators,
            currentIndex = startIndex,
            hasMore = initialCreators.size >= PAGE_SIZE,
        )
    ) {

  private var favoriteStatusDisabled = false
  private val postReducer = PaginationReducer<Post, String> { it.id }

  fun onPageChanged(index: Int) {
    log.d { "打开Creator -> 切换(index=$index)" }
    mutableState.value = mutableState.value.copy(currentIndex = index)
    if (index >= mutableState.value.creators.size - PAGER_NEXT_PREFETCH_COUNT) {
      loadMoreCreators()
    }
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

  fun getCreatorPostSnapshot(creator: Creator): PaginationSnapshot<Post> =
      mutableState.value.creatorPostSnapshots[creator.id] ?: PaginationSnapshot()

  fun loadCreatorAnnouncements(creator: Creator, forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.creatorAnnouncements.containsKey(creator.id)) return
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorAnnouncementIds =
                mutableState.value.loadingCreatorAnnouncementIds + creator.id
        )
    screenModelScope.launch {
      runCatching {
            creatorRepo.getCreatorAnnouncements(
                platform,
                creator.service,
                creator.id,
                forceRefresh,
            )
          }
          .onSuccess { announcements ->
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorAnnouncementIds =
                        mutableState.value.loadingCreatorAnnouncementIds - creator.id,
                    creatorAnnouncements =
                        mutableState.value.creatorAnnouncements +
                            (creator.id to announcements.sortedByDescending { it.added.orEmpty() }),
                )
            log.i {
              "加载Creator Announcements -> 成功(service=${creator.service},creator=${creator.id},count=${announcements.size})"
            }
          }
          .onFailure {
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorAnnouncementIds =
                        mutableState.value.loadingCreatorAnnouncementIds - creator.id
                )
            log.e(it) {
              "加载Creator Announcements -> 失败(service=${creator.service},creator=${creator.id})"
            }
          }
    }
  }

  fun getCreatorAnnouncements(creator: Creator): List<Announcement> =
      mutableState.value.creatorAnnouncements[creator.id] ?: emptyList()

  fun translateAnnouncement(creator: Creator, announcement: Announcement) {
    if (announcement.content.isBlank()) return
    val key = announcement.translationKey()
    val existing = mutableState.value.announcementTranslations[creator.id]?.get(key)
    if (existing?.showTranslation == true) {
      updateAnnouncementTranslation(creator.id, key, existing.copy(showTranslation = false))
      return
    }
    if (existing != null && existing.blocks.isNotEmpty() && !existing.isTranslating) {
      updateAnnouncementTranslation(creator.id, key, existing.copy(showTranslation = true))
      return
    }

    screenModelScope.launch {
      val blocks = translationService.extractBlocks(announcement.content)
      if (blocks.isEmpty()) return@launch
      updateAnnouncementTranslation(
          creator.id,
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
      runCatching {
            translationService.translateBlocks(blocks) { index, result ->
              updateAnnouncementTranslationBlock(creator.id, key, index, result)
            }
          }
          .onFailure { error ->
            log.e(error) { "翻译Announcement -> 失败(creator=${creator.id},key=$key)" }
            val failed =
                mutableState.value.announcementTranslations[creator.id]?.get(key)
                    ?: ContentTranslationState()
            updateAnnouncementTranslation(
                creator.id,
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
          mutableState.value.announcementTranslations[creator.id]?.get(key)
              ?: ContentTranslationState()
      updateAnnouncementTranslation(
          creator.id,
          key,
          done.copy(isTranslating = false, showTranslation = true),
      )
    }
  }

  fun translateAnnouncements(creator: Creator) {
    val announcements = getCreatorAnnouncements(creator).filter { it.content.isNotBlank() }
    if (announcements.isEmpty()) return
    val keys = announcements.map { it.translationKey() }
    val currentTranslations = mutableState.value.announcementTranslations[creator.id] ?: emptyMap()
    val allVisible = keys.all { currentTranslations[it]?.showTranslation == true }
    if (allVisible) {
      mutableState.value =
          mutableState.value.copy(
              announcementTranslations =
                  mutableState.value.announcementTranslations +
                      (creator.id to
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
                      (creator.id to
                          currentTranslations.mapValues { (_, state) ->
                            state.copy(showTranslation = true)
                          })
          )
      return
    }

    screenModelScope.launch {
      announcements.forEach { announcement ->
        val key = announcement.translationKey()
        val latest = mutableState.value.announcementTranslations[creator.id] ?: emptyMap()
        val existing = latest[key]
        if (existing != null && existing.blocks.isNotEmpty() && !existing.isTranslating) {
          updateAnnouncementTranslation(creator.id, key, existing.copy(showTranslation = true))
          return@forEach
        }

        val blocks = translationService.extractBlocks(announcement.content)
        if (blocks.isEmpty()) return@forEach
        updateAnnouncementTranslation(
            creator.id,
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
        runCatching {
              translationService.translateBlocks(blocks) { index, result ->
                updateAnnouncementTranslationBlock(creator.id, key, index, result)
              }
            }
            .onFailure { error ->
              log.e(error) { "翻译Announcement -> 失败(creator=${creator.id},key=$key)" }
              val failed =
                  (mutableState.value.announcementTranslations[creator.id]?.get(key)
                      ?: ContentTranslationState())
              updateAnnouncementTranslation(
                  creator.id,
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
            mutableState.value.announcementTranslations[creator.id]?.get(key)
                ?: ContentTranslationState()
        updateAnnouncementTranslation(
            creator.id,
            key,
            done.copy(isTranslating = false, showTranslation = true),
        )
      }
    }
  }

  fun loadCreatorPosts(creator: Creator, offset: Int = 0, forceRefresh: Boolean = false) {
    val existing = getCreatorPostSnapshot(creator)
    if (offset == 0 && !forceRefresh && (existing.items.isNotEmpty() || existing.loading)) return
    val loading = postReducer.beginLoad(existing, forceRefresh)
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds + creator.id,
            creatorPostSnapshots =
                mutableState.value.creatorPostSnapshots + (creator.id to loading),
        )
    screenModelScope.launch {
      log.i {
        "加载Creator Posts -> 开始(service=${creator.service},creator=${creator.id},offset=$offset)"
      }
      runCatching {
            postRepo.getCreatorPostsPage(
                platform,
                creator.service,
                creator.id,
                offset,
                forceRefresh,
            )
          }
          .onSuccess { page ->
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
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.id,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots + (creator.id to snapshot),
                )
            log.i {
              "加载Creator Posts -> 成功(service=${creator.service},creator=${creator.id},offset=$offset,count=${posts.size})"
            }
          }
          .onFailure {
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.id,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots +
                            (creator.id to
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
    val appending = postReducer.beginAppend(current)
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds + creator.id,
            creatorPostSnapshots =
                mutableState.value.creatorPostSnapshots + (creator.id to appending),
        )
    screenModelScope.launch {
      val offset = current.offset
      runCatching {
            postRepo.getCreatorPostsPage(platform, creator.service, creator.id, offset, false)
          }
          .onSuccess { page ->
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
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.id,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots + (creator.id to snapshot),
                )
          }
          .onFailure {
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.id,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots +
                            (creator.id to postReducer.reduceAppendError(appending, it)),
                )
          }
    }
  }

  fun loadPreviousCreatorPosts(creator: Creator) {
    val current = getCreatorPostSnapshot(creator)
    if (!postReducer.canLoadPrevious(current)) return
    val offset = (current.startOffset - PAGE_SIZE).coerceAtLeast(0)
    val prepending = postReducer.beginPrepend(current)
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds + creator.id,
            creatorPostSnapshots =
                mutableState.value.creatorPostSnapshots + (creator.id to prepending),
        )
    screenModelScope.launch {
      runCatching {
            postRepo.getCreatorPostsPage(platform, creator.service, creator.id, offset, false)
          }
          .onSuccess { page ->
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
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.id,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots + (creator.id to snapshot),
                )
          }
          .onFailure {
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.id,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots +
                            (creator.id to postReducer.reducePrependError(prepending, it)),
                )
          }
    }
  }

  fun jumpCreatorPostsToPage(creator: Creator, page: Int) {
    val current = getCreatorPostSnapshot(creator)
    val targetPage = page.coerceIn(1, current.pageInfo?.lastPage ?: page.coerceAtLeast(1))
    val offset = (targetPage - 1) * PAGE_SIZE
    val loading = postReducer.beginJump(current, offset)
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds + creator.id,
            creatorPostSnapshots =
                mutableState.value.creatorPostSnapshots + (creator.id to loading),
        )
    screenModelScope.launch {
      runCatching {
            postRepo.getCreatorPostsPage(platform, creator.service, creator.id, offset, false)
          }
          .onSuccess { pageResult ->
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
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.id,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots + (creator.id to snapshot),
                )
          }
          .onFailure {
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorPostIds = mutableState.value.loadingCreatorPostIds - creator.id,
                    creatorPostSnapshots =
                        mutableState.value.creatorPostSnapshots +
                            (creator.id to postReducer.reduceJumpError(current, it)),
                )
          }
    }
  }

  fun onCreatorPostVisibleIndex(creator: Creator, firstVisiblePostIndex: Int) {
    val current = getCreatorPostSnapshot(creator)
    val next =
        postReducer.updateVisiblePage(
            current,
            firstVisibleItemIndex = firstVisiblePostIndex,
            pageSize = PAGE_SIZE,
        )
    if (next === current || next == current) return
    mutableState.value =
        mutableState.value.copy(
            creatorPostSnapshots = mutableState.value.creatorPostSnapshots + (creator.id to next),
        )
  }

  fun loadCreatorTags(creator: Creator, forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.creatorTags.containsKey(creator.id)) return
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorTagIds = mutableState.value.loadingCreatorTagIds + creator.id
        )
    screenModelScope.launch {
      runCatching {
            creatorRepo.getCreatorTags(platform, creator.service, creator.id, forceRefresh)
          }
          .onSuccess { tags ->
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorTagIds = mutableState.value.loadingCreatorTagIds - creator.id,
                    creatorTags = mutableState.value.creatorTags + (creator.id to tags),
                )
            log.i {
              "加载Creator Tags -> 成功(service=${creator.service},creator=${creator.id},count=${tags.size})"
            }
          }
          .onFailure {
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorTagIds = mutableState.value.loadingCreatorTagIds - creator.id
                )
            log.e(it) { "加载Creator Tags -> 失败(service=${creator.service},creator=${creator.id})" }
          }
    }
  }

  fun getCreatorTags(creator: Creator): List<Tag> =
      mutableState.value.creatorTags[creator.id] ?: emptyList()

  fun loadCreatorLinks(creator: Creator, forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.creatorLinks.containsKey(creator.id)) return
    screenModelScope.launch {
      runCatching {
            creatorRepo.getCreatorLinks(platform, creator.service, creator.id, forceRefresh)
          }
          .onSuccess { links ->
            mutableState.value =
                mutableState.value.copy(
                    creatorLinks = mutableState.value.creatorLinks + (creator.id to links)
                )
          }
          .onFailure {
            log.w(it) { "加载Creator Links -> 失败(service=${creator.service},creator=${creator.id})" }
          }
    }
  }

  fun getCreatorLinks(creator: Creator): List<Creator> =
      mutableState.value.creatorLinks[creator.id] ?: emptyList()

  fun loadRecommendedCreators(creator: Creator) {
    if (mutableState.value.recommendedCreators.containsKey(creator.id)) return
    mutableState.value =
        mutableState.value.copy(
            loadingRecommendedCreatorIds =
                mutableState.value.loadingRecommendedCreatorIds + creator.id
        )
    screenModelScope.launch {
      runCatching { creatorRepo.getRecommendedCreators(platform, creator.service, creator.id) }
          .onSuccess { recommended ->
            mutableState.value =
                mutableState.value.copy(
                    loadingRecommendedCreatorIds =
                        mutableState.value.loadingRecommendedCreatorIds - creator.id,
                    recommendedCreators =
                        mutableState.value.recommendedCreators + (creator.id to recommended),
                )
          }
          .onFailure {
            mutableState.value =
                mutableState.value.copy(
                    loadingRecommendedCreatorIds =
                        mutableState.value.loadingRecommendedCreatorIds - creator.id
                )
            log.w(it) {
              "加载Recommended Creators -> 失败(service=${creator.service},creator=${creator.id})"
            }
          }
    }
  }

  fun getRecommendedCreators(creator: Creator): List<Creator> =
      mutableState.value.recommendedCreators[creator.id] ?: emptyList()

  fun loadFavoriteStatus(creator: Creator) {
    if (favoriteStatusDisabled) return
    if (!creatorRepo.hasSession(platform)) {
      log.d { "Creator收藏状态 -> 跳过(未登录,service=${creator.service},creator=${creator.id})" }
      return
    }
    screenModelScope.launch {
      runCatching { creatorRepo.isFavoriteCreator(platform, creator.service, creator.id) }
          .onSuccess { isFavorite ->
            val current = mutableState.value.favoriteCreatorIds
            mutableState.value =
                mutableState.value.copy(
                    favoriteCreatorIds =
                        if (isFavorite) current + creator.id else current - creator.id,
                    favoriteErrorMessage = null,
                )
            log.i {
              "Creator收藏状态 -> 成功(service=${creator.service},creator=${creator.id},favorite=$isFavorite)"
            }
          }
          .onFailure {
            log.w(it) { "Creator收藏状态 -> 失败(service=${creator.service},creator=${creator.id})" }
            if (it is AuthRequiredException) favoriteStatusDisabled = true
            mutableState.value = mutableState.value.copy(favoriteErrorMessage = it.message)
          }
    }
  }

  fun toggleFavoriteCreator(creator: Creator) {
    val current = mutableState.value
    val wasFavorite = creator.id in current.favoriteCreatorIds
    mutableState.value =
        current.copy(
            favoriteCreatorIds =
                if (wasFavorite) current.favoriteCreatorIds - creator.id
                else current.favoriteCreatorIds + creator.id,
            favoriteErrorMessage = null,
        )
    screenModelScope.launch {
      runCatching {
            if (wasFavorite) {
              creatorRepo.removeFavoriteCreator(platform, creator.service, creator.id)
            } else {
              creatorRepo.addFavoriteCreator(platform, creator.service, creator.id)
            }
          }
          .onSuccess {
            log.i {
              "收藏Creator -> 成功(service=${creator.service},creator=${creator.id},favorite=${!wasFavorite})"
            }
          }
          .onFailure {
            log.e(it) { "收藏Creator -> 失败(service=${creator.service},creator=${creator.id})" }
            val latest = mutableState.value
            mutableState.value =
                latest.copy(
                    favoriteCreatorIds =
                        if (wasFavorite) latest.favoriteCreatorIds + creator.id
                        else latest.favoriteCreatorIds - creator.id,
                    favoriteErrorMessage = it.message,
                )
          }
    }
  }

  fun loadDiscordChannels(creator: Creator, forceRefresh: Boolean = false) {
    if (!forceRefresh && mutableState.value.creatorDiscordChannels.containsKey(creator.id)) return
    mutableState.value =
        mutableState.value.copy(
            loadingCreatorDiscordChannelIds =
                mutableState.value.loadingCreatorDiscordChannelIds + creator.id
        )
    screenModelScope.launch {
      runCatching { discordRepo.getChannels(platform, creator.id) }
          .onSuccess { channels ->
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorDiscordChannelIds =
                        mutableState.value.loadingCreatorDiscordChannelIds - creator.id,
                    creatorDiscordChannels =
                        mutableState.value.creatorDiscordChannels + (creator.id to channels),
                )
            log.i { "加载Discord频道列表 -> 成功(creator=${creator.id},count=${channels.size})" }
          }
          .onFailure {
            mutableState.value =
                mutableState.value.copy(
                    loadingCreatorDiscordChannelIds =
                        mutableState.value.loadingCreatorDiscordChannelIds - creator.id
                )
            log.e(it) { "加载Discord频道列表 -> 失败(creator=${creator.id})" }
          }
    }
  }

  fun getDiscordChannels(creator: Creator): List<DiscordChannel> =
      mutableState.value.creatorDiscordChannels[creator.id] ?: emptyList()

  private fun loadMoreCreators() {
    val state = mutableState.value
    if (state.isLoadingMore || !state.hasMore) return
    log.i { "加载更多Creators -> 开始(current=${state.creators.size})" }
    mutableState.value = state.copy(isLoadingMore = true)
    screenModelScope.launch {
      runCatching { creatorRepo.getAllCreators(platform, false) }
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
      creatorId: String,
      announcementKey: String,
      translationState: ContentTranslationState,
  ) {
    val current = mutableState.value
    val creatorTranslations = current.announcementTranslations[creatorId] ?: emptyMap()
    mutableState.value =
        current.copy(
            announcementTranslations =
                current.announcementTranslations +
                    (creatorId to (creatorTranslations + (announcementKey to translationState)))
        )
  }

  private fun updateAnnouncementTranslationBlock(
      creatorId: String,
      announcementKey: String,
      index: Int,
      result: TranslationBlockResult,
  ) {
    val translation =
        mutableState.value.announcementTranslations[creatorId]?.get(announcementKey) ?: return
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
              "翻译Announcement block -> 失败(creator=$creatorId,key=$announcementKey,index=$index)"
            }
            updatedBlocks[index].copy(status = TranslationStatus.FAILURE)
          }
        }
    updateAnnouncementTranslation(
        creatorId,
        announcementKey,
        translation.copy(blocks = updatedBlocks, showTranslation = true),
    )
  }
}

fun Announcement.translationKey(): String =
    hash.ifBlank { added ?: "${service}:${userId}:${content.hashCode()}" }
