package ddd.kc.data.repository

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Tag
import ddd.kc.data.network.KcApiClient
import ddd.kc.util.logging.KcLog
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val TAGS_TTL_MS = 6 * 60 * 60 * 1_000L
private val log = KcLog.withTag("TagRepository")

class TagRepository(
    private val api: KcApiClient,
    private val db: AppDatabase,
    private val json: Json,
    private val ioContext: CoroutineContext,
) {
  private val dao
    get() = db.cacheDao()

  suspend fun getAllTags(platform: Platform, forceRefresh: Boolean): List<Tag> =
      withContext(ioContext) {
        val key = "${platform.name}:tags:v2"
        if (!forceRefresh) {
          val cached = dao.readChunkedList<Tag>(json, key, TAGS_TTL_MS, currentTimeMs())
          if (cached != null) {
            log.d { "读取缓存 -> 命中(scope=tags,platform=${platform.name})" }
            return@withContext cached
          }
          log.d { "读取缓存 -> 未命中或过期(scope=tags,platform=${platform.name})" }
        } else {
          log.d { "读取缓存 -> 跳过(scope=tags,platform=${platform.name},forceRefresh=true)" }
        }
        log.i { "刷新Tags -> 开始(platform=${platform.name},forceRefresh=$forceRefresh)" }
        val tags = api.getTags(platform)
        val chunks = dao.writeChunkedList(json, key, tags, currentTimeMs())
        log.i { "刷新Tags -> 成功(platform=${platform.name},count=${tags.size},chunks=$chunks)" }
        tags
      }

  suspend fun searchTags(platform: Platform, query: String): List<Tag> =
      withContext(ioContext) {
        val normalized = query.trim()
        log.i { "搜索Tags -> 本地过滤(platform=${platform.name},queryLength=${normalized.length})" }
        getAllTags(platform, false).filter { it.tag.contains(normalized, ignoreCase = true) }
      }
}
