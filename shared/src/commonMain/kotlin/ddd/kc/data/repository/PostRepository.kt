package ddd.kc.data.repository

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.model.Comment
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.creatorId
import ddd.kc.data.network.KcApiClient
import ddd.kc.data.network.PagedResult
import ddd.kc.data.network.PopularPage
import ddd.kc.data.network.asException
import ddd.kc.data.store.CacheNamespace
import ddd.kc.data.store.rawBodyQueryStore
import ddd.kc.util.logging.KcLog
import ddd.kc.util.logging.summarizePost
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val log = KcLog.withTag("PostRepository")

private data class OffsetKey(val offset: Int)

private data class PopularKey(val date: String?, val period: String, val offset: Int)

private data class SearchKey(
    val query: String,
    val offset: Int,
    val tag: String?,
    val service: String?,
)

private data class CreatorPostsKey(val service: String, val creatorId: String, val offset: Int)

private data class PostKey(val service: String, val creatorId: String, val postId: String)

class PostRepository(
    private val api: KcApiClient,
    private val db: AppDatabase,
    private val json: Json,
    private val ioContext: CoroutineContext,
) {
  private val dao
    get() = db.cacheDao()

  private val recentStore by lazy {
    rawBodyQueryStore<OffsetKey, List<Post>>(
        cacheDao = dao,
        namespace = CacheNamespace.PostList,
        fetcherName = "pawchive-recent-posts",
        cacheKey = { "pawchive:posts:recent:${it.offset}" },
        fetch = { key -> api.fetchRecentPostsBody(offset = key.offset) },
        parse = { _, body -> api.parsePosts(body) },
    )
  }

  private val popularStore by lazy {
    rawBodyQueryStore<PopularKey, PopularPage>(
        cacheDao = dao,
        namespace = CacheNamespace.PostList,
        fetcherName = "pawchive-popular-posts",
        cacheKey = { key ->
          "pawchive:posts:popular:${key.period}:${key.date.orEmpty()}:${key.offset}"
        },
        fetch = { key ->
          api.fetchPopularPostsBody(date = key.date, period = key.period, offset = key.offset)
        },
        parse = { key, body -> api.parsePopularPostsPage(body, key.date, key.period, key.offset) },
    )
  }

  private val searchStore by lazy {
    rawBodyQueryStore<SearchKey, PagedResult<Post>>(
        cacheDao = dao,
        namespace = CacheNamespace.PostList,
        fetcherName = "pawchive-post-search",
        cacheKey = { key ->
          "pawchive:posts:search:${key.offset}:${key.service.orEmpty()}:${key.tag.orEmpty()}:${key.query}"
        },
        fetch = { key ->
          api.fetchPostSearchBody(
              query = key.query,
              offset = key.offset,
              tag = key.tag,
              service = key.service,
          )
        },
        parse = { key, body -> api.parsePostCardsPage(body, key.offset) },
    )
  }

  private val creatorPostsStore by lazy {
    rawBodyQueryStore<CreatorPostsKey, List<Post>>(
        cacheDao = dao,
        namespace = CacheNamespace.PostList,
        fetcherName = "pawchive-creator-posts",
        cacheKey = { key -> "pawchive:${key.service}:${key.creatorId}:posts:${key.offset}" },
        fetch = { key ->
          api.fetchCreatorPostsBody(
              service = key.service,
              creatorId = key.creatorId,
              offset = key.offset,
          )
        },
        parse = { _, body -> api.parsePosts(body) },
    )
  }

  private val postStore by lazy {
    rawBodyQueryStore<PostKey, Post>(
        cacheDao = dao,
        namespace = CacheNamespace.Detail,
        fetcherName = "pawchive-post-detail",
        cacheKey = { key -> "pawchive:${key.service}:${key.creatorId}:post:${key.postId}" },
        fetch = { key ->
          api.fetchPostBody(service = key.service, creatorId = key.creatorId, postId = key.postId)
        },
        parse = { _, body -> api.parsePost(body) },
    )
  }

  private val favoritePostsStore by lazy {
    rawBodyQueryStore<Unit, List<Post>>(
        cacheDao = dao,
        namespace = CacheNamespace.Favorites,
        fetcherName = "pawchive-favorite-posts",
        cacheKey = { "pawchive:favorites:posts" },
        fetch = {
          json.encodeToString(
              kotlinx.serialization.builtins.ListSerializer(Post.serializer()),
              api.getFavoritePosts(),
          )
        },
        parse = { _, body -> json.decodeFromString(body) },
    )
  }

  fun observeRecentPosts(offset: Int, forceRefresh: Boolean = false): Flow<QueryState<List<Post>>> =
      flow {
        if (forceRefresh) deleteCacheGroup("pawchive:posts:recent:")
        emitAll(recentStore.query(OffsetKey(offset)))
      }

  suspend fun getRecentPosts(platform: Platform, offset: Int, forceRefresh: Boolean): List<Post> =
      observeRecentPosts(offset, forceRefresh).awaitData()

  suspend fun getPopularPosts(platform: Platform, forceRefresh: Boolean): List<Post> =
      observePopularPostsPage(date = null, period = "day", offset = 0, forceRefresh = forceRefresh)
          .awaitData()
          .posts

  fun observePopularPostsPage(
      date: String?,
      period: String,
      offset: Int,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<PopularPage>> = flow {
    if (forceRefresh) {
      deleteCacheGroup("pawchive:posts:popular:$period:")
    }
    emitAll(popularStore.query(PopularKey(date, period, offset)))
  }

  suspend fun getPopularPostsPage(
      platform: Platform,
      date: String?,
      period: String,
      offset: Int,
      forceRefresh: Boolean,
  ): PopularPage = observePopularPostsPage(date, period, offset, forceRefresh).awaitData()

  fun observePostSearch(
      query: String,
      offset: Int,
      tag: String?,
      service: String?,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<List<Post>>> =
      observePostSearchPage(query, offset, tag, service, forceRefresh).map { state ->
        QueryState(
            data = state.data?.items,
            isLoading = state.isLoading,
            isRefreshing = state.isRefreshing,
            isFromCache = state.isFromCache,
            isStale = state.isStale,
            error = state.error,
            lastUpdatedAtMillis = state.lastUpdatedAtMillis,
        )
      }

  fun observePostSearchPage(
      query: String,
      offset: Int,
      tag: String?,
      service: String?,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<PagedResult<Post>>> = flow {
    if (forceRefresh) deleteCacheGroup("pawchive:posts:search:")
    emitAll(searchStore.query(SearchKey(query.trim(), offset, tag, service)))
  }

  suspend fun searchPosts(
      platform: Platform,
      query: String,
      offset: Int,
      tag: String?,
      service: String?,
  ): List<Post> = observePostSearch(query, offset, tag, service, forceRefresh = true).awaitData()

  suspend fun searchPostsPage(
      platform: Platform,
      query: String,
      offset: Int,
      tag: String?,
      service: String?,
      forceRefresh: Boolean,
  ): PagedResult<Post> =
      observePostSearchPage(query, offset, tag, service, forceRefresh = forceRefresh).awaitData()

  suspend fun getPostsByTag(
      platform: Platform,
      tag: String,
      offset: Int,
      forceRefresh: Boolean = false,
  ): List<Post> =
      observePostSearch(
              query = "",
              offset = offset,
              tag = tag,
              service = null,
              forceRefresh = forceRefresh,
          )
          .awaitData()

  suspend fun getPostsByTagPage(
      platform: Platform,
      tag: String,
      offset: Int,
      forceRefresh: Boolean = false,
  ): PagedResult<Post> =
      observePostSearchPage(
              query = "",
              offset = offset,
              tag = tag,
              service = null,
              forceRefresh = forceRefresh,
          )
          .awaitData()

  fun observeCreatorPosts(
      service: String,
      creatorId: String,
      offset: Int,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<List<Post>>> = flow {
    if (forceRefresh) deleteCacheGroup("pawchive:$service:$creatorId:posts:")
    emitAll(creatorPostsStore.query(CreatorPostsKey(service, creatorId, offset)))
  }

  suspend fun getCreatorPosts(
      platform: Platform,
      service: String,
      creatorId: String,
      offset: Int,
      forceRefresh: Boolean,
  ): List<Post> = observeCreatorPosts(service, creatorId, offset, forceRefresh).awaitData()

  suspend fun getCreatorPostsPage(
      platform: Platform,
      service: String,
      creatorId: String,
      offset: Int,
      forceRefresh: Boolean,
  ): PagedResult<Post> {
    val posts = getCreatorPosts(platform, service, creatorId, offset, forceRefresh)
    val pageInfo =
        withContext(ioContext) { api.getCreatorPostsPageInfo(platform, service, creatorId, offset) }
    return PagedResult(items = posts, pageInfo = pageInfo)
  }

  fun observePost(
      service: String,
      creatorId: String,
      postId: String,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<Post>> = flow {
    if (forceRefresh) deleteCacheGroup("pawchive:$service:$creatorId:post:$postId")
    emitAll(postStore.query(PostKey(service, creatorId, postId)))
  }

  suspend fun getPost(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ): Post =
      observePost(service, creatorId, postId).awaitData().also {
        log.i { "加载Post详情 -> 成功(${summarizePost(it)})" }
      }

  fun observeFavoritePosts(forceRefresh: Boolean = false): Flow<QueryState<List<Post>>> = flow {
    if (forceRefresh) deleteCacheGroup("pawchive:favorites:posts")
    emitAll(favoritePostsStore.query(Unit))
  }

  suspend fun getFavoritePosts(platform: Platform, forceRefresh: Boolean = false): List<Post> =
      observeFavoritePosts(forceRefresh).awaitData()

  fun hasSession(platform: Platform): Boolean = api.hasSession(platform)

  suspend fun clearFavoritesCache(platform: Platform) =
      withContext(ioContext) { dao.delete("pawchive:favorites:posts") }

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
        api.addFavoritePost(platform, service, creatorId, postId)
        dao.delete("pawchive:favorites:posts")
      }

  suspend fun removeFavoritePost(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ) =
      withContext(ioContext) {
        api.removeFavoritePost(platform, service, creatorId, postId)
        dao.delete("pawchive:favorites:posts")
      }

  suspend fun getPostComments(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ): List<Comment> =
      withContext(ioContext) { api.getPostComments(platform, service, creatorId, postId) }

  suspend fun downloadFile(url: String): ByteArray =
      withContext(ioContext) { api.downloadFileBytes(url) }

  private suspend fun deleteCacheGroup(prefix: String) {
    withContext(ioContext) { dao.deleteKeyAndPrefixed(prefix, "$prefix%") }
  }
}

internal suspend fun <T : Any> Flow<QueryState<T>>.awaitData(): T {
  val state = filter { it.data != null || it.error != null }.first()
  state.error?.let { throw it.asException() }
  return requireNotNull(state.data)
}
