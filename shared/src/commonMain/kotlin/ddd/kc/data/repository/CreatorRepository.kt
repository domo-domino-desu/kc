package ddd.kc.data.repository

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.model.Announcement
import ddd.kc.data.model.Creator
import ddd.kc.data.model.DM
import ddd.kc.data.model.Platform
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.Tag
import ddd.kc.data.network.KcApiClient
import ddd.kc.data.network.toQueryError
import ddd.kc.data.store.CacheNamespace
import ddd.kc.data.store.rawBodyQueryStore
import ddd.kc.util.logging.KcLog
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val log = KcLog.withTag("CreatorRepository")
private const val CREATORS_CACHE_KEY = "pawchive:creators:v1"

private data class CreatorScopedKey(val service: String, val creatorId: String)

private data class DmKey(val query: String, val offset: Int)

class CreatorRepository(
    private val api: KcApiClient,
    private val db: AppDatabase,
    private val json: Json,
    private val ioContext: CoroutineContext,
) {
  private val dao
    get() = db.cacheDao()

  private val announcementsStore by lazy {
    rawBodyQueryStore<CreatorScopedKey, List<Announcement>>(
        cacheDao = dao,
        namespace = CacheNamespace.Detail,
        fetcherName = "pawchive-creator-announcements",
        cacheKey = { key -> "pawchive:${key.service}:${key.creatorId}:announcements" },
        fetch = { key ->
          json.encodeToString(
              ListSerializer(Announcement.serializer()),
              api.getCreatorAnnouncements(service = key.service, creatorId = key.creatorId),
          )
        },
        parse = { _, body -> json.decodeFromString(body) },
    )
  }

  private val creatorTagsStore by lazy {
    rawBodyQueryStore<CreatorScopedKey, List<Tag>>(
        cacheDao = dao,
        namespace = CacheNamespace.Detail,
        fetcherName = "pawchive-creator-tags",
        cacheKey = { key -> "pawchive:${key.service}:${key.creatorId}:tags" },
        fetch = { key ->
          api.fetchCreatorTagsBody(service = key.service, creatorId = key.creatorId)
        },
        parse = { _, body -> api.parseCreatorTags(body) },
    )
  }

  private val creatorLinksStore by lazy {
    rawBodyQueryStore<CreatorScopedKey, List<Creator>>(
        cacheDao = dao,
        namespace = CacheNamespace.Detail,
        fetcherName = "pawchive-creator-links",
        cacheKey = { key -> "pawchive:${key.service}:${key.creatorId}:links" },
        fetch = { key ->
          json.encodeToString(
              ListSerializer(Creator.serializer()),
              api.getCreatorLinks(service = key.service, creatorId = key.creatorId),
          )
        },
        parse = { _, body -> json.decodeFromString(body) },
    )
  }

  private val dmsStore by lazy {
    rawBodyQueryStore<DmKey, List<DM>>(
        cacheDao = dao,
        namespace = CacheNamespace.PostList,
        fetcherName = "pawchive-dms",
        cacheKey = { key -> "pawchive:dms:${key.offset}:${key.query}" },
        fetch = { key -> api.fetchDmsBody(query = key.query, offset = key.offset) },
        parse = { _, body -> api.parseDms(body) },
    )
  }

  private val favoriteCreatorsStore by lazy {
    rawBodyQueryStore<Unit, List<Creator>>(
        cacheDao = dao,
        namespace = CacheNamespace.Favorites,
        fetcherName = "pawchive-favorite-creators",
        cacheKey = { "pawchive:favorites:creators" },
        fetch = {
          json.encodeToString(
              ListSerializer(Creator.serializer()),
              api.getFavorites(type = "artist"),
          )
        },
        parse = { _, body -> json.decodeFromString(body) },
    )
  }

  fun observeCreators(forceRefresh: Boolean = false): Flow<QueryState<List<Creator>>> = flow {
    val now = currentTimeMs()
    val cached =
        withContext(ioContext) {
          dao.readChunkedListCache<Creator>(
              json = json,
              key = CREATORS_CACHE_KEY,
              ttlMs = CacheNamespace.Creators.ttlMillis,
              nowMs = now,
          )
        }

    if (cached != null) {
      emit(
          QueryState(
              data = cached.items,
              isRefreshing = forceRefresh || cached.isStale,
              isFromCache = true,
              isStale = cached.isStale,
              lastUpdatedAtMillis = cached.cachedAtMs,
          )
      )
      if (!forceRefresh && !cached.isStale) return@flow
    } else {
      emit(QueryState(isLoading = true, isStale = true))
    }

    val refreshed = runCatching {
      withContext(ioContext) {
        val creators = api.parseCreators(api.fetchCreatorsBody())
        val cachedAtMs = currentTimeMs()
        dao.writeChunkedList(json, CREATORS_CACHE_KEY, creators, cachedAtMs)
        creators to cachedAtMs
      }
    }
    refreshed
        .onSuccess { (creators, cachedAtMs) ->
          emit(
              QueryState(
                  data = creators,
                  isFromCache = false,
                  isStale = false,
                  lastUpdatedAtMillis = cachedAtMs,
              )
          )
        }
        .onFailure { error ->
          emit(
              QueryState(
                  data = cached?.items,
                  isFromCache = cached != null,
                  isStale = cached?.isStale ?: true,
                  error = error.toQueryError(),
                  lastUpdatedAtMillis = cached?.cachedAtMs,
              )
          )
        }
  }

  suspend fun getAllCreators(platform: Platform, forceRefresh: Boolean): List<Creator> =
      observeCreators(forceRefresh).awaitData()

  fun observeCreatorAnnouncements(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<List<Announcement>>> =
      announcementsStore.query(CreatorScopedKey(service, creatorId), forceRefresh = forceRefresh)

  suspend fun getCreatorAnnouncements(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Announcement> = observeCreatorAnnouncements(service, creatorId).awaitData()

  fun observeCreatorTags(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<List<Tag>>> =
      creatorTagsStore.query(CreatorScopedKey(service, creatorId), forceRefresh = forceRefresh)

  suspend fun getCreatorTags(platform: Platform, service: String, creatorId: String): List<Tag> =
      observeCreatorTags(service, creatorId).awaitData()

  fun observeCreatorLinks(
      service: String,
      creatorId: String,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<List<Creator>>> =
      creatorLinksStore.query(CreatorScopedKey(service, creatorId), forceRefresh = forceRefresh)

  suspend fun getCreatorLinks(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Creator> = observeCreatorLinks(service, creatorId).awaitData()

  suspend fun getRecommendedCreators(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Creator> = emptyList()

  suspend fun getCreatorDMs(platform: Platform, service: String, creatorId: String): List<DM> =
      emptyList()

  fun observeDms(
      query: String = "",
      offset: Int = 0,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<List<DM>>> =
      dmsStore.query(DmKey(query.trim(), offset), forceRefresh = forceRefresh)

  suspend fun getRecentDMs(platform: Platform, offset: Int): List<DM> =
      observeDms(offset = offset).awaitData()

  suspend fun searchCreators(
      platform: Platform,
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
            getAllCreators(platform, forceRefresh)
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

  suspend fun searchDMs(platform: Platform, query: String, offset: Int): List<DM> =
      observeDms(query = query, offset = offset, forceRefresh = true).awaitData()

  fun observeFavoriteCreators(forceRefresh: Boolean = false): Flow<QueryState<List<Creator>>> =
      favoriteCreatorsStore.query(Unit, forceRefresh = forceRefresh)

  suspend fun getFavoriteCreators(platform: Platform): List<Creator> =
      observeFavoriteCreators().awaitData()

  suspend fun clearFavoritesCache(platform: Platform) =
      withContext(ioContext) { dao.delete("pawchive:favorites:creators") }

  fun hasSession(platform: Platform): Boolean = api.hasSession(platform)

  suspend fun isFavoriteCreator(platform: Platform, service: String, creatorId: String): Boolean =
      withContext(ioContext) {
        getFavoriteCreators(platform).any { it.service == service && it.id == creatorId }
      }

  suspend fun addFavoriteCreator(platform: Platform, service: String, creatorId: String) =
      withContext(ioContext) {
        log.i { "收藏Creator -> 添加(service=$service,creator=$creatorId)" }
        api.addFavoriteCreator(platform, service, creatorId)
        dao.delete("pawchive:favorites:creators")
      }

  suspend fun removeFavoriteCreator(platform: Platform, service: String, creatorId: String) =
      withContext(ioContext) {
        log.i { "收藏Creator -> 移除(service=$service,creator=$creatorId)" }
        api.removeFavoriteCreator(platform, service, creatorId)
        dao.delete("pawchive:favorites:creators")
      }
}
