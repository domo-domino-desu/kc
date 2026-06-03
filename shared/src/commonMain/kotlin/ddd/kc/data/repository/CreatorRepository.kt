package ddd.kc.data.repository

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.local.entity.CacheEntity
import ddd.kc.data.model.Announcement
import ddd.kc.data.model.Creator
import ddd.kc.data.model.DM
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Tag
import ddd.kc.data.network.KcApiClient
import ddd.kc.util.logging.KcLog
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CREATORS_TTL_MS = 24 * 60 * 60 * 1_000L
private const val CREATOR_DETAIL_TTL_MS = 60 * 60 * 1_000L
private const val FAVORITE_CREATORS_TTL_MS = 5 * 60 * 1_000L
private val log = KcLog.withTag("CreatorRepository")

class CreatorRepository(
    private val api: KcApiClient,
    private val db: AppDatabase,
    private val json: Json,
    private val ioContext: CoroutineContext,
) {
  private val dao
    get() = db.cacheDao()

  suspend fun getAllCreators(platform: Platform, forceRefresh: Boolean): List<Creator> =
      withContext(ioContext) {
        val key = "${platform.name}:creators:v3"
        if (!forceRefresh) {
          val cached = dao.readChunkedList<Creator>(json, key, CREATORS_TTL_MS, currentTimeMs())
          if (cached != null) {
            log.d { "读取缓存 -> 命中(scope=creators,platform=${platform.name})" }
            return@withContext cached
          }
          log.d { "读取缓存 -> 未命中或过期(scope=creators,platform=${platform.name})" }
        } else {
          log.d { "读取缓存 -> 跳过(scope=creators,platform=${platform.name},forceRefresh=true)" }
        }
        log.i { "刷新Creators -> 开始(platform=${platform.name},forceRefresh=$forceRefresh)" }
        val creators = api.getCreators(platform)
        val chunks = dao.writeChunkedList(json, key, creators, currentTimeMs())
        log.i {
          "刷新Creators -> 成功(platform=${platform.name},count=${creators.size},chunks=$chunks)"
        }
        creators
      }

  suspend fun getCreatorAnnouncements(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Announcement> =
      withContext(ioContext) {
        val key = "${platform.name}:${service}:${creatorId}:announcements"
        val cached = dao.findByKey(key)
        if (cached != null && !isExpired(cached.cachedAtMs, CREATOR_DETAIL_TTL_MS)) {
          log.d {
            "读取缓存 -> 命中(scope=announcements,platform=${platform.name},service=$service,creator=$creatorId)"
          }
          return@withContext json.decodeFromString<List<Announcement>>(cached.dataJson)
        }
        log.i { "加载Creator公告 -> 开始(platform=${platform.name},service=$service,creator=$creatorId)" }
        val items = api.getCreatorAnnouncements(platform, service, creatorId)
        dao.upsert(CacheEntity(key, json.encodeToString(items), currentTimeMs()))
        log.i {
          "加载Creator公告 -> 成功(platform=${platform.name},service=$service,creator=$creatorId,count=${items.size})"
        }
        items
      }

  suspend fun getCreatorTags(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Tag> =
      withContext(ioContext) {
        val key = "${platform.name}:${service}:${creatorId}:tags"
        val cached = dao.findByKey(key)
        if (cached != null && !isExpired(cached.cachedAtMs, CREATOR_DETAIL_TTL_MS)) {
          log.d {
            "读取缓存 -> 命中(scope=creatorTags,platform=${platform.name},service=$service,creator=$creatorId)"
          }
          return@withContext json.decodeFromString<List<Tag>>(cached.dataJson)
        }
        log.i {
          "加载Creator Tags -> 开始(platform=${platform.name},service=$service,creator=$creatorId)"
        }
        val tags = api.getCreatorTags(platform, service, creatorId)
        dao.upsert(CacheEntity(key, json.encodeToString(tags), currentTimeMs()))
        log.i {
          "加载Creator Tags -> 成功(platform=${platform.name},service=$service,creator=$creatorId,count=${tags.size})"
        }
        tags
      }

  suspend fun getCreatorLinks(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Creator> =
      withContext(ioContext) {
        val key = "${platform.name}:${service}:${creatorId}:links:v2"
        val cached = dao.findByKey(key)
        if (cached != null && !isExpired(cached.cachedAtMs, CREATOR_DETAIL_TTL_MS)) {
          return@withContext json.decodeFromString<List<Creator>>(cached.dataJson)
        }
        val links = api.getCreatorLinks(platform, service, creatorId)
        dao.upsert(CacheEntity(key, json.encodeToString(links), currentTimeMs()))
        links
      }

  suspend fun getRecommendedCreators(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Creator> =
      withContext(ioContext) {
        val key = "${platform.name}:${service}:${creatorId}:recommended"
        val cached = dao.findByKey(key)
        if (cached != null && !isExpired(cached.cachedAtMs, CREATOR_DETAIL_TTL_MS)) {
          return@withContext json.decodeFromString<List<Creator>>(cached.dataJson)
        }
        val creators = api.getRecommendedCreators(platform, service, creatorId)
        dao.upsert(CacheEntity(key, json.encodeToString(creators), currentTimeMs()))
        creators
      }

  suspend fun getCreatorDMs(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<DM> = withContext(ioContext) { api.getCreatorDMs(platform, service, creatorId) }

  suspend fun getRecentDMs(platform: Platform, offset: Int): List<DM> =
      withContext(ioContext) { api.getRecentDMs(platform, offset) }

  suspend fun searchCreators(
      platform: Platform,
      query: String,
      service: String?,
      sortBy: String,
      order: String,
      forceRefresh: Boolean = false,
  ): List<Creator> =
      withContext(ioContext) {
        log.i {
          "搜索Creators -> 开始(platform=${platform.name},queryLength=${query.length},service=$service,sort=$sortBy,order=$order)"
        }
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
        val result = filtered.sortedWith(if (order == "asc") comparator else comparator.reversed())
        val first = result.firstOrNull()
        log.i {
          "搜索Creators -> 完成(platform=${platform.name},queryLength=${normalizedQuery.length},service=$service,sort=$sortBy,order=$order,count=${result.size},first=${first?.service}/${first?.id},firstFav=${first?.favorited})"
        }
        result
      }

  suspend fun searchDMs(platform: Platform, query: String, offset: Int): List<DM> =
      withContext(ioContext) { api.searchDMs(platform, query, offset) }

  suspend fun getFavoriteCreators(platform: Platform): List<Creator> =
      withContext(ioContext) {
        val key = "${platform.name}:favorites:creators"
        val cached = dao.findByKey(key)
        if (cached != null && !isExpired(cached.cachedAtMs, FAVORITE_CREATORS_TTL_MS)) {
          log.d { "读取缓存 -> 命中(scope=favoriteCreators,platform=${platform.name})" }
          return@withContext json.decodeFromString<List<Creator>>(cached.dataJson)
        }
        if (cached != null) log.d { "读取缓存 -> 过期(scope=favoriteCreators,platform=${platform.name})" }
        val creators = api.getFavorites(platform, "artist")
        dao.upsert(CacheEntity(key, json.encodeToString(creators), currentTimeMs()))
        creators
      }

  suspend fun clearFavoritesCache(platform: Platform) =
      withContext(ioContext) { dao.delete("${platform.name}:favorites:creators") }

  fun hasSession(platform: Platform): Boolean = api.hasSession(platform)

  suspend fun isFavoriteCreator(platform: Platform, service: String, creatorId: String): Boolean =
      withContext(ioContext) {
        getFavoriteCreators(platform).any { it.service == service && it.id == creatorId }
      }

  suspend fun addFavoriteCreator(platform: Platform, service: String, creatorId: String) =
      withContext(ioContext) {
        log.i { "收藏Creator -> 添加(platform=${platform.name},service=$service,creator=$creatorId)" }
        api.addFavoriteCreator(platform, service, creatorId)
        dao.delete("${platform.name}:favorites:creators")
      }

  suspend fun removeFavoriteCreator(platform: Platform, service: String, creatorId: String) =
      withContext(ioContext) {
        log.i { "收藏Creator -> 移除(platform=${platform.name},service=$service,creator=$creatorId)" }
        api.removeFavoriteCreator(platform, service, creatorId)
        dao.delete("${platform.name}:favorites:creators")
      }
}

private fun isExpired(cachedAtMs: Long, ttlMs: Long): Boolean = currentTimeMs() - cachedAtMs > ttlMs
