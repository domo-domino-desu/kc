package ddd.kc.data.repository

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.local.entity.CreatorHistoryEntity
import ddd.kc.data.local.entity.PostHistoryEntity
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post
import ddd.kc.data.model.creatorId
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ActivityHistoryRepository(
    private val db: AppDatabase,
    private val json: Json,
    private val ioContext: CoroutineDispatcher = Dispatchers.IO,
) {
  private val log = KcLog.withTag("ActivityHistoryRepository")
  private val dao
    get() = db.historyDao()

  suspend fun recordCreatorVisit(creator: Creator) =
      withContext(ioContext) {
        val key = creatorHistoryKey(creator.service, creator.id)
        log.d { "记录用户历史 -> key=$key" }
        dao.upsertCreator(
            CreatorHistoryEntity(
                historyKey = key,
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
        val creators = mutableListOf<Creator>()
        for (entity in dao.listCreatorsByLatest(MAX_HISTORY_COUNT)) {
          try {
            creators += json.decodeFromString<Creator>(entity.creatorJson)
          } catch (error: Throwable) {
            if (error is CancellationException) throw error
            log.w(error) { "解析用户历史失败 -> keyHash=${entity.historyKey.hashCode()}" }
            dao.deleteCreator(entity.historyKey)
          }
        }
        creators
      }

  suspend fun recordPostVisit(post: Post) =
      withContext(ioContext) {
        val key = postHistoryKey(post.service, post.creatorId, post.id)
        log.d { "记录帖子历史 -> key=$key" }
        dao.upsertPost(
            PostHistoryEntity(
                historyKey = key,
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
        val posts = mutableListOf<Post>()
        for (entity in dao.listPostsByLatest(MAX_HISTORY_COUNT)) {
          try {
            posts += json.decodeFromString<Post>(entity.postJson)
          } catch (error: Throwable) {
            if (error is CancellationException) throw error
            log.w(error) { "解析帖子历史失败 -> keyHash=${entity.historyKey.hashCode()}" }
            dao.deletePost(entity.historyKey)
          }
        }
        posts
      }

  private fun creatorHistoryKey(service: String, creatorId: String): String =
      listOf(service, creatorId).joinToString(":")

  private fun postHistoryKey(
      service: String,
      creatorId: String,
      postId: String,
  ): String = listOf(service, creatorId, postId).joinToString(":")

  companion object {
    const val MAX_HISTORY_COUNT = 300
  }
}
