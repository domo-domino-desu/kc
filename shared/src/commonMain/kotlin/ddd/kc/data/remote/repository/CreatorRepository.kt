package ddd.kc.data.remote.repository

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.local.entity.CreatorEntity
import ddd.kc.data.local.entity.CreatorSyncEntity
import ddd.kc.data.local.entity.toEntity
import ddd.kc.data.local.entity.toModel
import ddd.kc.data.model.AiFilterMode
import ddd.kc.data.model.Announcement
import ddd.kc.data.model.CommunityPage
import ddd.kc.data.model.Creator
import ddd.kc.data.model.CreatorKey
import ddd.kc.data.model.DM
import ddd.kc.data.model.PagedResult
import ddd.kc.data.model.QueryError
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.Tag
import ddd.kc.data.remote.cache.CacheNamespace
import ddd.kc.data.remote.cache.typedQueryStore
import ddd.kc.data.remote.network.PawchiveApi
import ddd.kc.data.remote.network.PawchiveApiException
import ddd.kc.data.remote.network.QueryException
import ddd.kc.data.remote.network.toQueryError
import ddd.kc.utils.currentTimeMs
import ddd.kc.utils.logging.KcLog
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val log = KcLog.withTag("CreatorRepository")
private const val CREATORS_SYNC_KEY = "all"

private data class DmKey(val query: String, val offset: Int)

private data class CommunityPageKey(
    val creator: CreatorKey,
    val loungeId: String?,
    val offset: Int,
)

private data class CreatorSnapshot(
    val creators: List<Creator>,
    val cachedAtMs: Long?,
    val didFetch: Boolean,
)

class CreatorRepository(
    private val api: PawchiveApi,
    private val db: AppDatabase,
    private val json: Json,
    private val ioContext: CoroutineContext,
) {
  private val creatorsRefreshMutex = Mutex()

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
        cacheKey = { key -> "pawchive:${key.service}:${key.id}:tags:v2" },
        fetch = { key ->
          api.parseCreatorTags(api.fetchCreatorTagsBody(service = key.service, creatorId = key.id))
        },
    )
  }

  private val similarCreatorsStore by lazy {
    typedQueryStore<CreatorKey, List<Creator>>(
        cacheDao = dao,
        json = json,
        serializer = ListSerializer(Creator.serializer()),
        namespace = CacheNamespace.Detail,
        cacheKey = { key -> "pawchive:${key.service}:${key.id}:similar" },
        fetch = { key ->
          api.parseSimilarCreators(api.fetchSimilarCreatorsBody(key.service, key.id))
        },
    )
  }

  private val filteredCreatorsStore by lazy {
    typedQueryStore<AiFilterMode, List<Creator>>(
        cacheDao = dao,
        json = json,
        serializer = ListSerializer(Creator.serializer()),
        namespace = CacheNamespace.Creators,
        cacheKey = { "pawchive:creators:filter:${it.persistedValue}" },
        fetch = { api.parseCreators(api.fetchCreatorsBody(it)) },
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

  private val communityStore by lazy {
    typedQueryStore<CommunityPageKey, CommunityPage>(
        cacheDao = dao,
        json = json,
        serializer = CommunityPage.serializer(),
        namespace = CacheNamespace.Detail,
        cacheKey = { key ->
          "pawchive:${key.creator.service}:${key.creator.id}:community:" +
              "${key.loungeId.orEmpty()}:${key.offset}"
        },
        fetch = { key ->
          api.parseCreatorCommunity(
              api.fetchCreatorCommunityBody(
                  service = key.creator.service,
                  creatorId = key.creator.id,
                  loungeId = key.loungeId,
                  offset = key.offset,
              ),
              key.offset,
          )
        },
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

  fun observeCreators(
      forceRefresh: Boolean = false,
      aiFilter: AiFilterMode = AiFilterMode.SHOW,
  ): Flow<QueryState<List<Creator>>> = flow {
    if (aiFilter != AiFilterMode.SHOW) {
      if (forceRefresh) deleteCacheGroup("pawchive:creators:filter:${aiFilter.persistedValue}")
      emitAll(filteredCreatorsStore.query(aiFilter))
      return@flow
    }
    val initial = withContext(ioContext) { readCreatorSnapshot() }
    val stale = initial.cachedAtMs == null || isCreatorsStale(initial.cachedAtMs)
    if (initial.creators.isNotEmpty()) {
      emit(
          QueryState(
              data = initial.creators,
              isRefreshing = forceRefresh || stale,
              isFromCache = true,
              isStale = stale,
              lastUpdatedAtMillis = initial.cachedAtMs,
          )
      )
    } else {
      emit(QueryState(isLoading = true, isStale = true))
    }
    if (!forceRefresh && !stale) return@flow

    try {
      val fresh = withContext(ioContext) { refreshCreators(forceRefresh) }
      emit(
          QueryState(
              data = fresh.creators,
              isFromCache = !fresh.didFetch,
              isStale = false,
              lastUpdatedAtMillis = fresh.cachedAtMs,
          )
      )
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      emit(
          QueryState(
              data = initial.creators.takeIf { it.isNotEmpty() },
              isFromCache = initial.creators.isNotEmpty(),
              isStale = true,
              error = error.toQueryError(),
              lastUpdatedAtMillis = initial.cachedAtMs,
          )
      )
    }
  }

  suspend fun getAllCreators(forceRefresh: Boolean): List<Creator> =
      withContext(ioContext) {
        val sync = db.creatorDao().findSync(CREATORS_SYNC_KEY)
        if (forceRefresh || sync == null || isCreatorsStale(sync.cachedAtMs)) {
          refreshCreators(forceRefresh).creators
        } else db.creatorDao().listAll().map(CreatorEntity::toModel)
      }

  suspend fun getCreator(key: CreatorKey): Creator? =
      withContext(ioContext) {
        db.creatorDao().find(key.service, key.id)?.toModel()
            ?: run {
              refreshCreators(forceRefresh = false)
              db.creatorDao().find(key.service, key.id)?.toModel()
            }
      }

  private suspend fun refreshCreators(forceRefresh: Boolean): CreatorSnapshot =
      creatorsRefreshMutex.withLock {
        val creatorDao = db.creatorDao()
        val latestSync = creatorDao.findSync(CREATORS_SYNC_KEY)
        if (!forceRefresh && latestSync != null && !isCreatorsStale(latestSync.cachedAtMs)) {
          return@withLock CreatorSnapshot(
              creators = creatorDao.listAll().map(CreatorEntity::toModel),
              cachedAtMs = latestSync.cachedAtMs,
              didFetch = false,
          )
        }

        val remote = api.parseCreators(api.fetchCreatorsBody())
        check(remote.isNotEmpty()) { "Creator snapshot is empty; refusing to replace local data" }
        val remoteEntities = remote.map(Creator::toEntity)
        val localEntities = creatorDao.listAll()
        val localByKey = localEntities.associateBy { it.service to it.creatorId }
        val remoteKeys = HashSet<Pair<String, String>>(remoteEntities.size)
        val upserts =
            remoteEntities.filter { entity ->
              remoteKeys += entity.service to entity.creatorId
              localByKey[entity.service to entity.creatorId] != entity
            }
        val deletes = localEntities.filter { (it.service to it.creatorId) !in remoteKeys }
        val cachedAtMs = currentTimeMs()
        creatorDao.applyDiff(
            upserts = upserts,
            deletes = deletes,
            sync = CreatorSyncEntity(CREATORS_SYNC_KEY, cachedAtMs),
        )
        log.i {
          "Creator快照同步(total=${remoteEntities.size},upsert=${upserts.size},delete=${deletes.size})"
        }
        CreatorSnapshot(remote, cachedAtMs, didFetch = true)
      }

  private suspend fun readCreatorSnapshot(): CreatorSnapshot {
    val creatorDao = db.creatorDao()
    val sync = creatorDao.findSync(CREATORS_SYNC_KEY)
    return CreatorSnapshot(
        creators = creatorDao.listAll().map(CreatorEntity::toModel),
        cachedAtMs = sync?.cachedAtMs,
        didFetch = false,
    )
  }

  private fun isCreatorsStale(cachedAtMs: Long): Boolean =
      currentTimeMs() - cachedAtMs > CacheNamespace.Creators.ttlMillis

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

  fun observeSimilarCreators(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<List<Creator>>> = flow {
    if (forceRefresh) deleteCacheGroup("pawchive:$service:$creatorId:similar")
    emitAll(similarCreatorsStore.query(CreatorKey(service, creatorId)))
  }

  suspend fun getSimilarCreators(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): List<Creator> = observeSimilarCreators(service, creatorId, forceRefresh).awaitData()

  suspend fun getCreatorCommunityPage(
      service: String,
      creatorId: String,
      loungeId: String? = null,
      offset: Int = 0,
      forceRefresh: Boolean = false,
  ): CommunityPage? =
      try {
        if (forceRefresh) deleteCacheGroup("pawchive:$service:$creatorId:community:")
        communityStore.queryOnce(
            CommunityPageKey(CreatorKey(service, creatorId), loungeId, offset),
            forceRefresh = forceRefresh,
        )
      } catch (error: PawchiveApiException) {
        if (error.statusCode == 404 && loungeId == null) null else throw error
      } catch (error: QueryException) {
        if ((error.error as? QueryError.Http)?.code == 404 && loungeId == null) null
        else throw error
      }

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
