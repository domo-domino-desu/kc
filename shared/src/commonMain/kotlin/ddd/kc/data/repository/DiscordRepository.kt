package ddd.kc.data.repository

import ddd.kc.data.model.DiscordChannel
import ddd.kc.data.model.DiscordPost
import ddd.kc.data.model.Platform
import ddd.kc.data.network.KcApiClient
import ddd.kc.utils.logging.KcLog
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

private val log = KcLog.withTag("DiscordRepository")

class DiscordRepository(
    private val api: KcApiClient,
    private val ioContext: CoroutineContext,
) {
  suspend fun getChannels(platform: Platform, serverId: String): List<DiscordChannel> =
      withContext(ioContext) {
        log.i { "获取Discord频道列表 -> 开始(platform=${platform.name},server=$serverId)" }
        api.getDiscordChannels(platform, serverId).also {
          log.i {
            "获取Discord频道列表 -> 成功(platform=${platform.name},server=$serverId,count=${it.size})"
          }
        }
      }

  suspend fun getChannelPosts(
      platform: Platform,
      channelId: String,
      offset: Int,
  ): List<DiscordPost> =
      withContext(ioContext) {
        log.i { "获取Discord频道消息 -> 开始(platform=${platform.name},channel=$channelId,offset=$offset)" }
        api.getDiscordChannelPosts(platform, channelId, offset).also {
          log.i {
            "获取Discord频道消息 -> 成功(platform=${platform.name},channel=$channelId,offset=$offset,count=${it.size})"
          }
        }
      }
}
