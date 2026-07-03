package ddd.kc.data.repository

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.local.entity.CreatorHistoryEntity
import ddd.kc.data.local.entity.PostHistoryEntity
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.creatorId
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ActivityHistoryRepository(
    private val db: AppDatabase,
    private val json: Json,
    private val ioContext: CoroutineDispatcher = Dispatchers.Default,
) {
  private val log = KcLog.withTag("ActivityHistoryRepository")
  private val dao
    get() = db.historyDao()

  suspend fun recordCreatorVisit(platform: Platform, creator: Creator) =
      withContext(ioContext) {
        val key = creatorHistoryKey(platform, creator.service, creator.id)
        log.d { "记录用户历史 -> key=$key" }
        dao.upsertCreator(
            CreatorHistoryEntity(
                historyKey = key,
                platform = platform.name,
                service = creator.service,
                creatorId = creator.id,
                creatorJson = json.encodeToString(creator),
                visitedAtMs = currentTimeMs(),
            )
        )
        dao.trimCreators(MAX_HISTORY_COUNT)
      }

  suspend fun loadCreatorHistory(): List<Creator> =
      withContext(ioContext) {
        dao.listCreatorsByLatest(MAX_HISTORY_COUNT).mapNotNull { entity ->
          runCatching { json.decodeFromString<Creator>(entity.creatorJson) }
              .onFailure { error -> log.w(error) { "解析用户历史失败 -> key=${entity.historyKey}" } }
              .getOrNull()
        }
      }

  suspend fun recordPostVisit(platform: Platform, post: Post) =
      withContext(ioContext) {
        val key = postHistoryKey(platform, post.service, post.creatorId, post.id)
        log.d { "记录帖子历史 -> key=$key" }
        dao.upsertPost(
            PostHistoryEntity(
                historyKey = key,
                platform = platform.name,
                service = post.service,
                creatorId = post.creatorId,
                postId = post.id,
                postJson = json.encodeToString(post),
                visitedAtMs = currentTimeMs(),
            )
        )
        dao.trimPosts(MAX_HISTORY_COUNT)
      }

  suspend fun loadPostHistory(): List<Post> =
      withContext(ioContext) {
        dao.listPostsByLatest(MAX_HISTORY_COUNT).mapNotNull { entity ->
          runCatching { json.decodeFromString<Post>(entity.postJson) }
              .onFailure { error -> log.w(error) { "解析帖子历史失败 -> key=${entity.historyKey}" } }
              .getOrNull()
        }
      }

  private fun creatorHistoryKey(platform: Platform, service: String, creatorId: String): String =
      listOf(platform.name, service, creatorId).joinToString(":")

  private fun postHistoryKey(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ): String = listOf(platform.name, service, creatorId, postId).joinToString(":")

  companion object {
    const val MAX_HISTORY_COUNT = 300
  }
}
