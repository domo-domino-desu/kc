package ddd.kc.data.repository

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.local.entity.CacheEntity
import ddd.kc.data.model.Comment
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.creatorId
import ddd.kc.data.network.KcApiClient
import ddd.kc.data.network.PopularPage
import ddd.kc.util.logging.KcLog
import ddd.kc.util.logging.summarizePost
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val POST_LIST_TTL_MS = 60 * 60 * 1_000L
private const val FAVORITE_POSTS_TTL_MS = 5 * 60 * 1_000L
private val log = KcLog.withTag("PostRepository")

class PostRepository(
    private val api: KcApiClient,
    private val db: AppDatabase,
    private val json: Json,
    private val ioContext: CoroutineContext,
) {
  private val dao
    get() = db.cacheDao()

  suspend fun getRecentPosts(platform: Platform, offset: Int, forceRefresh: Boolean): List<Post> =
      withContext(ioContext) {
        val key = "${platform.name}:posts:$offset"
        if (!forceRefresh) {
          val cached = dao.findByKey(key)
          if (cached != null && !cached.isExpired(POST_LIST_TTL_MS)) {
            log.d { "读取缓存 -> 命中(scope=recentPosts,platform=${platform.name},offset=$offset)" }
            return@withContext json.decodeFromString<List<Post>>(cached.dataJson)
          }
          if (cached != null) {
            log.d { "读取缓存 -> 过期(scope=recentPosts,platform=${platform.name},offset=$offset)" }
          }
        } else {
          log.d {
            "读取缓存 -> 跳过(scope=recentPosts,platform=${platform.name},offset=$offset,forceRefresh=true)"
          }
        }
        log.i {
          "刷新最近Posts -> 开始(platform=${platform.name},offset=$offset,forceRefresh=$forceRefresh)"
        }
        val posts = api.getRecentPosts(platform, offset)
        dao.upsert(CacheEntity(key, json.encodeToString(posts), currentMs()))
        log.i { "刷新最近Posts -> 成功(platform=${platform.name},offset=$offset,count=${posts.size})" }
        posts
      }

  suspend fun getPopularPosts(platform: Platform, forceRefresh: Boolean): List<Post> =
      withContext(ioContext) {
        val key = "${platform.name}:posts:popular"
        if (!forceRefresh) {
          val cached = dao.findByKey(key)
          if (cached != null && !cached.isExpired(POST_LIST_TTL_MS)) {
            log.d { "读取缓存 -> 命中(scope=popularPosts,platform=${platform.name})" }
            return@withContext json.decodeFromString<List<Post>>(cached.dataJson)
          }
          if (cached != null) log.d { "读取缓存 -> 过期(scope=popularPosts,platform=${platform.name})" }
        } else {
          log.d { "读取缓存 -> 跳过(scope=popularPosts,platform=${platform.name},forceRefresh=true)" }
        }
        log.i { "刷新热门Posts -> 开始(platform=${platform.name},forceRefresh=$forceRefresh)" }
        val posts = api.getPopularPosts(platform).posts
        dao.upsert(CacheEntity(key, json.encodeToString(posts), currentMs()))
        log.i { "刷新热门Posts -> 成功(platform=${platform.name},count=${posts.size})" }
        posts
      }

  suspend fun getPopularPostsPage(
      platform: Platform,
      date: String?,
      period: String,
      offset: Int,
      forceRefresh: Boolean,
  ): PopularPage =
      withContext(ioContext) {
        val key = "${platform.name}:posts:popular:$period:${date.orEmpty()}:$offset"
        if (!forceRefresh) {
          val cached = dao.findByKey(key)
          if (cached != null && !cached.isExpired(POST_LIST_TTL_MS)) {
            log.d {
              "读取缓存 -> 命中(scope=popularPostsPage,platform=${platform.name},period=$period,date=$date,offset=$offset)"
            }
            return@withContext json.decodeFromString<PopularPage>(cached.dataJson)
          }
        }
        log.i {
          "刷新热门Posts分页 -> 开始(platform=${platform.name},period=$period,date=$date,offset=$offset,forceRefresh=$forceRefresh)"
        }
        val page = api.getPopularPosts(platform, date = date, period = period, offset = offset)
        dao.upsert(CacheEntity(key, json.encodeToString(page), currentMs()))
        log.i { "刷新热门Posts分页 -> 成功(platform=${platform.name},count=${page.posts.size})" }
        page
      }

  suspend fun searchPosts(
      platform: Platform,
      query: String,
      offset: Int,
      tag: String?,
      service: String?,
  ): List<Post> =
      withContext(ioContext) {
        log.i {
          "搜索Posts -> 开始(platform=${platform.name},queryLength=${query.length},offset=$offset,tag=$tag,service=$service)"
        }
        api.searchPosts(platform, query, offset, tag, service).also {
          log.i { "搜索Posts -> 成功(platform=${platform.name},offset=$offset,count=${it.size})" }
        }
      }

  suspend fun getPostsByTag(platform: Platform, tag: String, offset: Int): List<Post> =
      withContext(ioContext) {
        log.i {
          "加载Tag Posts -> 开始(platform=${platform.name},tagLength=${tag.length},offset=$offset)"
        }
        api.getPostsByTag(platform, tag, offset).also {
          log.i { "加载Tag Posts -> 成功(platform=${platform.name},offset=$offset,count=${it.size})" }
        }
      }

  suspend fun getCreatorPosts(
      platform: Platform,
      service: String,
      creatorId: String,
      offset: Int,
      forceRefresh: Boolean,
  ): List<Post> =
      withContext(ioContext) {
        val key = "${platform.name}:${service}:${creatorId}:posts:$offset"
        if (!forceRefresh) {
          val cached = dao.findByKey(key)
          if (cached != null && !cached.isExpired(POST_LIST_TTL_MS)) {
            log.d {
              "读取缓存 -> 命中(scope=creatorPosts,platform=${platform.name},service=$service,creator=$creatorId,offset=$offset)"
            }
            return@withContext json.decodeFromString<List<Post>>(cached.dataJson)
          }
          if (cached != null) {
            log.d {
              "读取缓存 -> 过期(scope=creatorPosts,platform=${platform.name},service=$service,creator=$creatorId,offset=$offset)"
            }
          }
        } else {
          log.d {
            "读取缓存 -> 跳过(scope=creatorPosts,platform=${platform.name},service=$service,creator=$creatorId,offset=$offset,forceRefresh=true)"
          }
        }
        log.i {
          "加载Creator Posts -> 开始(platform=${platform.name},service=$service,creator=$creatorId,offset=$offset,forceRefresh=$forceRefresh)"
        }
        val posts = api.getCreatorPosts(platform, service, creatorId, offset)
        dao.upsert(CacheEntity(key, json.encodeToString(posts), currentMs()))
        log.i {
          "加载Creator Posts -> 成功(platform=${platform.name},service=$service,creator=$creatorId,offset=$offset,count=${posts.size})"
        }
        posts
      }

  suspend fun getPost(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ): Post =
      withContext(ioContext) {
        log.i {
          "加载Post详情 -> 开始(platform=${platform.name},service=$service,creator=$creatorId,post=$postId)"
        }
        api.getPost(platform, service, creatorId, postId).also {
          log.i { "加载Post详情 -> 成功(platform=${platform.name},${summarizePost(it)})" }
        }
      }

  suspend fun getFavoritePosts(platform: Platform): List<Post> =
      withContext(ioContext) {
        val key = "${platform.name}:favorites:posts"
        val cached = dao.findByKey(key)
        if (cached != null && !cached.isExpired(FAVORITE_POSTS_TTL_MS)) {
          log.d { "读取缓存 -> 命中(scope=favoritePosts,platform=${platform.name})" }
          return@withContext json.decodeFromString<List<Post>>(cached.dataJson)
        }
        if (cached != null) log.d { "读取缓存 -> 过期(scope=favoritePosts,platform=${platform.name})" }
        val posts = api.getFavoritePosts(platform)
        dao.upsert(CacheEntity(key, json.encodeToString(posts), currentMs()))
        posts
      }

  fun hasSession(platform: Platform): Boolean = api.hasSession(platform)

  suspend fun clearFavoritesCache(platform: Platform) =
      withContext(ioContext) { dao.delete("${platform.name}:favorites:posts") }

  suspend fun isFavoritePost(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ): Boolean =
      withContext(ioContext) {
        getFavoritePosts(platform).any {
          it.service == service && it.id == postId && it.creatorId == creatorId
        }
      }

  suspend fun addFavoritePost(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ) =
      withContext(ioContext) {
        log.i {
          "收藏Post -> 添加(platform=${platform.name},service=$service,creator=$creatorId,post=$postId)"
        }
        api.addFavoritePost(platform, service, creatorId, postId)
        dao.delete("${platform.name}:favorites:posts")
      }

  suspend fun removeFavoritePost(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ) =
      withContext(ioContext) {
        log.i {
          "收藏Post -> 移除(platform=${platform.name},service=$service,creator=$creatorId,post=$postId)"
        }
        api.removeFavoritePost(platform, service, creatorId, postId)
        val key = "${platform.name}:favorites:posts"
        val cached = dao.findByKey(key) ?: return@withContext
        val posts =
            json.decodeFromString<List<Post>>(cached.dataJson).filter {
              !(it.service == service && it.id == postId && it.creatorId == creatorId)
            }
        dao.upsert(CacheEntity(key, json.encodeToString(posts), cached.cachedAtMs))
      }

  suspend fun getPostComments(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ): List<Comment> =
      withContext(ioContext) { api.getPostComments(platform, service, creatorId, postId) }
}

private fun CacheEntity.isExpired(ttlMs: Long): Boolean = currentMs() - cachedAtMs > ttlMs

private fun currentMs(): Long = currentTimeMs()
