package ddd.kc.data.remote.repository

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.Tag
import ddd.kc.data.remote.cache.CacheNamespace
import ddd.kc.data.remote.cache.rawBodyQueryStore
import ddd.kc.data.remote.network.PawchiveApi
import ddd.kc.utils.logging.KcLog
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val log = KcLog.withTag("TagRepository")

class TagRepository(
    private val api: PawchiveApi,
    private val db: AppDatabase,
    private val json: Json,
    private val ioContext: CoroutineContext,
) {
  private val tagsStore by lazy {
    rawBodyQueryStore<Unit, List<Tag>>(
        cacheDao = db.cacheDao(),
        namespace = CacheNamespace.Tags,
        cacheKey = { "pawchive:tags:v1" },
        fetch = { api.fetchTagsBody() },
        parse = { _, body -> api.parseTags(body) },
    )
  }

  fun observeTags(forceRefresh: Boolean = false): Flow<QueryState<List<Tag>>> = flow {
    if (forceRefresh) {
      withContext(ioContext) {
        db.cacheDao().deleteKeyAndPrefixed("pawchive:tags:v1", "pawchive:tags:v1%")
      }
    }
    emitAll(tagsStore.query(Unit))
  }

  suspend fun getAllTags(forceRefresh: Boolean): List<Tag> = observeTags(forceRefresh).awaitData()

  suspend fun searchTags(query: String): List<Tag> =
      withContext(ioContext) {
        val normalized = query.trim()
        log.i { "搜索Tags -> 本地过滤(queryLength=${normalized.length})" }
        getAllTags(false).filter { it.tag.contains(normalized, ignoreCase = true) }
      }
}
