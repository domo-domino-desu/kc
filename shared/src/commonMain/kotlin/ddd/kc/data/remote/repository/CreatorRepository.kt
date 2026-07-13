package ddd.kc.data.remote.repository

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.model.Announcement
import ddd.kc.data.model.Creator
import ddd.kc.data.model.CreatorKey
import ddd.kc.data.model.DM
import ddd.kc.data.model.PagedResult
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.Tag
import ddd.kc.data.remote.cache.CacheNamespace
import ddd.kc.data.remote.cache.typedQueryStore
import ddd.kc.data.remote.network.PawchiveApi
import ddd.kc.utils.logging.KcLog
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val log = KcLog.withTag("CreatorRepository")
private const val CREATORS_CACHE_KEY = "pawchive:creators:v1"

private data class DmKey(val query: String, val offset: Int)

class CreatorRepository(
    private val api: PawchiveApi,
    private val db: AppDatabase,
    private val json: Json,
    private val ioContext: CoroutineContext,
) {
  private val dao
    get() = db.cacheDao()

  private val announcementsStore by lazy {
    typedQueryStore<CreatorKey, List<Announcement>>(
        cacheDao = dao,
        json = json,
        serializer = ListSerializer(Announcement.serializer()),
        namespace = CacheNamespace.Detail,
        cacheKey = { key -> "pawchive:${key.service}:${key.id}:announcements" },
        fetch = { key -> api.getCreatorAnnouncements(service = key.service, creatorId = key.id) },
    )
  }

  private val creatorTagsStore by lazy {
    typedQueryStore<CreatorKey, List<Tag>>(
        cacheDao = dao,
        json = json,
        serializer = ListSerializer(Tag.serializer()),
        namespace = CacheNamespace.Detail,
        cacheKey = { key -> "pawchive:${key.service}:${key.id}:tags" },
        fetch = { key ->
          api.parseCreatorTags(api.fetchCreatorTagsBody(service = key.service, creatorId = key.id))
        },
    )
  }

  private val creatorLinksStore by lazy {
    typedQueryStore<CreatorKey, List<Creator>>(
        cacheDao = dao,
        json = json,
        serializer = ListSerializer(Creator.serializer()),
        namespace = CacheNamespace.Detail,
        cacheKey = { key -> "pawchive:${key.service}:${key.id}:links" },
        fetch = { key -> api.getCreatorLinks(service = key.service, creatorId = key.id) },
    )
  }

  private val dmsStore by lazy {
    typedQueryStore<DmKey, PagedResult<DM>>(
        cacheDao = dao,
        json = json,
        serializer = PagedResult.serializer(DM.serializer()),
        namespace = CacheNamespace.PostList,
        cacheKey = { key -> "pawchive:dms:${key.offset}:${key.query}" },
        fetch = { key ->
          api.parseDmsPage(api.fetchDmsBody(query = key.query, offset = key.offset), key.offset)
        },
    )
  }

  private val favoriteCreatorsStore by lazy {
    typedQueryStore<Unit, List<Creator>>(
        cacheDao = dao,
        json = json,
        serializer = ListSerializer(Creator.serializer()),
        namespace = CacheNamespace.Favorites,
        cacheKey = { "pawchive:favorites:creators" },
        fetch = { api.getFavorites(type = "artist") },
    )
  }

  private val creatorsStore by lazy {
    typedQueryStore<Unit, List<Creator>>(
        cacheDao = dao,
        json = json,
        serializer = ListSerializer(Creator.serializer()),
        namespace = CacheNamespace.Creators,
        cacheKey = { CREATORS_CACHE_KEY },
        fetch = { api.parseCreators(api.fetchCreatorsBody()) },
    )
  }

  fun observeCreators(forceRefresh: Boolean = false): Flow<QueryState<List<Creator>>> =
      creatorsStore.query(Unit, forceRefresh = forceRefresh)

  suspend fun getAllCreators(forceRefresh: Boolean): List<Creator> =
      observeCreators(forceRefresh).awaitData()

  fun observeCreatorAnnouncements(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<List<Announcement>>> = flow {
    if (forceRefresh) deleteCacheGroup("pawchive:$service:$creatorId:announcements")
    emitAll(announcementsStore.query(CreatorKey(service, creatorId)))
  }

  suspend fun getCreatorAnnouncements(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): List<Announcement> = observeCreatorAnnouncements(service, creatorId, forceRefresh).awaitData()

  fun observeCreatorTags(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<List<Tag>>> = flow {
    if (forceRefresh) deleteCacheGroup("pawchive:$service:$creatorId:tags")
    emitAll(creatorTagsStore.query(CreatorKey(service, creatorId)))
  }

  suspend fun getCreatorTags(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): List<Tag> = observeCreatorTags(service, creatorId, forceRefresh).awaitData()

  fun observeCreatorLinks(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<List<Creator>>> = flow {
    if (forceRefresh) deleteCacheGroup("pawchive:$service:$creatorId:links")
    emitAll(creatorLinksStore.query(CreatorKey(service, creatorId)))
  }

  suspend fun getCreatorLinks(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): List<Creator> = observeCreatorLinks(service, creatorId, forceRefresh).awaitData()

  fun observeDmsPage(
      query: String = "",
      offset: Int = 0,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<PagedResult<DM>>> = flow {
    if (forceRefresh) deleteCacheGroup("pawchive:dms:")
    emitAll(dmsStore.query(DmKey(query.trim(), offset)))
  }

  suspend fun getRecentDMs(offset: Int): List<DM> =
      observeDmsPage(offset = offset).awaitData().items

  suspend fun searchCreators(
      query: String,
      service: String?,
      sortBy: String,
      order: String,
      forceRefresh: Boolean = false,
  ): List<Creator> =
      withContext(ioContext) {
        val normalizedQuery = query.trim()
        val comparator =
            when (sortBy) {
              "favorited" -> compareBy<Creator> { it.favorited }
              "indexed" -> compareBy { it.indexed }
              "updated" -> compareBy { it.updated }
              "name" -> compareBy { it.name.lowercase() }
              "service" ->
                  compareBy<Creator> { it.service.lowercase() }.thenBy { it.name.lowercase() }
              else -> compareBy { it.updated }
            }
        val filtered =
            getAllCreators(forceRefresh)
                .asSequence()
                .filter { service == null || it.service.equals(service, ignoreCase = true) }
                .filter {
                  normalizedQuery.isBlank() ||
                      it.name.contains(normalizedQuery, ignoreCase = true) ||
                      it.id.contains(normalizedQuery, ignoreCase = true) ||
                      it.publicId?.contains(normalizedQuery, ignoreCase = true) == true
                }
                .toList()
        filtered.sortedWith(if (order == "asc") comparator else comparator.reversed())
      }

  suspend fun searchDMs(query: String, offset: Int): List<DM> =
      observeDmsPage(query = query, offset = offset, forceRefresh = true).awaitData().items

  suspend fun searchDMsPage(
      query: String,
      offset: Int,
      forceRefresh: Boolean,
  ): PagedResult<DM> =
      observeDmsPage(query = query, offset = offset, forceRefresh = forceRefresh).awaitData()

  fun observeFavoriteCreators(forceRefresh: Boolean = false): Flow<QueryState<List<Creator>>> =
      flow {
        if (forceRefresh) deleteCacheGroup("pawchive:favorites:creators")
        emitAll(favoriteCreatorsStore.query(Unit))
      }

  suspend fun getFavoriteCreators(
      forceRefresh: Boolean = false,
  ): List<Creator> = observeFavoriteCreators(forceRefresh).awaitData()

  suspend fun clearFavoritesCache() =
      withContext(ioContext) { dao.delete("pawchive:favorites:creators") }

  fun hasSession(): Boolean = api.hasSession()

  suspend fun isFavoriteCreator(service: String, creatorId: String): Boolean =
      withContext(ioContext) {
        getFavoriteCreators().any { it.service == service && it.id == creatorId }
      }

  suspend fun addFavoriteCreator(service: String, creatorId: String) =
      withContext(ioContext) {
        log.i { "收藏Creator -> 添加(service=$service,creator=$creatorId)" }
        api.addFavoriteCreator(service, creatorId)
        dao.delete("pawchive:favorites:creators")
      }

  suspend fun removeFavoriteCreator(service: String, creatorId: String) =
      withContext(ioContext) {
        log.i { "收藏Creator -> 移除(service=$service,creator=$creatorId)" }
        api.removeFavoriteCreator(service, creatorId)
        dao.delete("pawchive:favorites:creators")
      }

  private suspend fun deleteCacheGroup(prefix: String) {
    withContext(ioContext) { dao.deleteKeyAndPrefixed(prefix, "$prefix%") }
  }
}
