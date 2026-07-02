package ddd.kc.ui.pages.discord

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.DiscordChannel
import ddd.kc.data.model.DiscordPost
import ddd.kc.data.model.Platform
import ddd.kc.data.repository.DiscordRepository
import ddd.kc.ui.state.PaginationReducer
import ddd.kc.ui.state.PaginationSnapshot
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 150
private val log = KcLog.withTag("DiscordChannelScreenModel")

data class DiscordChannelPagerState(
    val channels: List<DiscordChannel>,
    val currentIndex: Int = 0,
    val channelSnapshots: Map<String, PaginationSnapshot<DiscordPost>> = emptyMap(),
) {
  val hasPrevious: Boolean
    get() = currentIndex > 0

  val hasNext: Boolean
    get() = currentIndex < channels.size - 1

  val currentChannel: DiscordChannel?
    get() = channels.getOrNull(currentIndex)
}

class DiscordChannelScreenModel(
    private val discordRepo: DiscordRepository,
    private val platform: Platform,
    channels: List<DiscordChannel>,
    startIndex: Int,
) :
    StateScreenModel<DiscordChannelPagerState>(
        DiscordChannelPagerState(channels = channels, currentIndex = startIndex)
    ) {
  private val reducer = PaginationReducer<DiscordPost, String> { it.id }

  fun onPageChanged(index: Int) {
    mutableState.value = mutableState.value.copy(currentIndex = index)
    val channel = mutableState.value.channels.getOrNull(index) ?: return
    loadChannelPosts(channel)
  }

  fun previous() {
    val current = mutableState.value.currentIndex
    if (current > 0) mutableState.value = mutableState.value.copy(currentIndex = current - 1)
  }

  fun next() {
    val current = mutableState.value.currentIndex
    if (current < mutableState.value.channels.size - 1) {
      mutableState.value = mutableState.value.copy(currentIndex = current + 1)
    }
  }

  fun loadChannelPosts(channel: DiscordChannel, forceRefresh: Boolean = false) {
    val existing = mutableState.value.channelSnapshots[channel.id] ?: PaginationSnapshot()
    if (!forceRefresh && (existing.items.isNotEmpty() || existing.loading)) return
    val loading = reducer.beginLoad(existing, forceRefresh)
    mutableState.value =
        mutableState.value.copy(
            channelSnapshots = mutableState.value.channelSnapshots + (channel.id to loading)
        )
    screenModelScope.launch {
      log.i {
        "Discord频道消息 -> 首屏开始(platform=${platform.name},channel=${channel.id},forceRefresh=$forceRefresh)"
      }
      runCatching { discordRepo.getChannelPosts(platform, channel.id, 0) }
          .onSuccess { posts ->
            val snap = reducer.reduceFirstPage(loading, posts, posts.size >= PAGE_SIZE, posts.size)
            mutableState.value =
                mutableState.value.copy(
                    channelSnapshots = mutableState.value.channelSnapshots + (channel.id to snap)
                )
            log.i { "Discord频道消息 -> 首屏成功(channel=${channel.id},count=${posts.size})" }
          }
          .onFailure {
            val snap = reducer.reduceFirstPageError(loading, it)
            mutableState.value =
                mutableState.value.copy(
                    channelSnapshots = mutableState.value.channelSnapshots + (channel.id to snap)
                )
            log.e(it) { "Discord频道消息 -> 首屏失败(channel=${channel.id})" }
          }
    }
  }

  fun loadMoreChannelPosts(channel: DiscordChannel) {
    val current = mutableState.value.channelSnapshots[channel.id] ?: return
    if (!reducer.canLoadMore(current)) return
    val appending = reducer.beginAppend(current)
    mutableState.value =
        mutableState.value.copy(
            channelSnapshots = mutableState.value.channelSnapshots + (channel.id to appending)
        )
    screenModelScope.launch {
      log.i { "Discord频道消息 -> 追加开始(channel=${channel.id},offset=${current.offset})" }
      runCatching { discordRepo.getChannelPosts(platform, channel.id, current.offset) }
          .onSuccess { posts ->
            val snap =
                reducer.reduceAppend(
                    appending,
                    posts,
                    posts.size >= PAGE_SIZE,
                    current.offset + posts.size,
                )
            mutableState.value =
                mutableState.value.copy(
                    channelSnapshots = mutableState.value.channelSnapshots + (channel.id to snap)
                )
            log.i { "Discord频道消息 -> 追加成功(channel=${channel.id},count=${posts.size})" }
          }
          .onFailure {
            val snap = reducer.reduceAppendError(appending, it)
            mutableState.value =
                mutableState.value.copy(
                    channelSnapshots = mutableState.value.channelSnapshots + (channel.id to snap)
                )
            log.e(it) { "Discord频道消息 -> 追加失败(channel=${channel.id})" }
          }
    }
  }

  fun getSnapshot(channel: DiscordChannel): PaginationSnapshot<DiscordPost> =
      mutableState.value.channelSnapshots[channel.id] ?: PaginationSnapshot()
}
