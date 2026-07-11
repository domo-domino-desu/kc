package ddd.kc.data.repository

import ddd.kc.data.cache.CacheNamespace
import ddd.kc.data.cache.rawBodyQueryStore
import ddd.kc.data.local.AppDatabase
import ddd.kc.data.media.MediaDownloader
import ddd.kc.data.model.Comment
import ddd.kc.data.model.CreatorKey
import ddd.kc.data.model.PagedResult
import ddd.kc.data.model.PopularPage
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostKey
import ddd.kc.data.model.QueryState
import ddd.kc.data.model.creatorId
import ddd.kc.data.network.PawchiveApi
import ddd.kc.data.network.asException
import ddd.kc.utils.logging.KcLog
import ddd.kc.utils.logging.summarizePost
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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

private data class CreatorPostsKey(val creator: CreatorKey, val offset: Int)

class PostRepository(
    private val api: PawchiveApi,
    private val db: AppDatabase,
    private val json: Json,
    private val mediaDownloader: MediaDownloader,
    private val ioContext: CoroutineContext,
) {
  private val dao
    get() = db.cacheDao()

  private val recentStore by lazy {
    rawBodyQueryStore<OffsetKey, List<Post>>(
        cacheDao = dao,
        namespace = CacheNamespace.PostList,
        cacheKey = { "pawchive:posts:recent:${it.offset}" },
        fetch = { key -> api.fetchRecentPostsBody(offset = key.offset) },
        parse = { _, body -> api.parsePosts(body) },
    )
  }

  private val popularStore by lazy {
    rawBodyQueryStore<PopularKey, PopularPage>(
        cacheDao = dao,
        namespace = CacheNamespace.PostList,
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
    rawBodyQueryStore<CreatorPostsKey, PagedResult<Post>>(
        cacheDao = dao,
        namespace = CacheNamespace.PostList,
        cacheKey = { key ->
          "pawchive:${key.creator.service}:${key.creator.id}:posts:${key.offset}"
        },
        fetch = { key ->
          api.fetchCreatorPostsPageBody(
              service = key.creator.service,
              creatorId = key.creator.id,
              offset = key.offset,
          )
        },
        parse = { key, body -> api.parsePostCardsPage(body, key.offset) },
    )
  }

  private val postStore by lazy {
    rawBodyQueryStore<PostKey, Post>(
        cacheDao = dao,
        namespace = CacheNamespace.Detail,
        cacheKey = { key -> "pawchive:${key.service}:${key.creatorId}:post:${key.id}" },
        fetch = { key ->
          api.fetchPostBody(service = key.service, creatorId = key.creatorId, postId = key.id)
        },
        parse = { _, body -> api.parsePost(body) },
    )
  }

  private val favoritePostsStore by lazy {
    rawBodyQueryStore<Unit, List<Post>>(
        cacheDao = dao,
        namespace = CacheNamespace.Favorites,
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

  suspend fun getRecentPosts(offset: Int, forceRefresh: Boolean): List<Post> =
      observeRecentPosts(offset, forceRefresh).awaitData()

  suspend fun getPopularPosts(forceRefresh: Boolean): List<Post> =
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
      date: String?,
      period: String,
      offset: Int,
      forceRefresh: Boolean,
  ): PopularPage = observePopularPostsPage(date, period, offset, forceRefresh).awaitData()

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
      query: String,
      offset: Int,
      tag: String?,
      service: String?,
  ): List<Post> =
      observePostSearchPage(query, offset, tag, service, forceRefresh = true).awaitData().items

  suspend fun searchPostsPage(
      query: String,
      offset: Int,
      tag: String?,
      service: String?,
      forceRefresh: Boolean,
  ): PagedResult<Post> =
      observePostSearchPage(query, offset, tag, service, forceRefresh = forceRefresh).awaitData()

  suspend fun getPostsByTag(
      tag: String,
      offset: Int,
      forceRefresh: Boolean = false,
  ): List<Post> =
      observePostSearchPage(
              query = "",
              offset = offset,
              tag = tag,
              service = null,
              forceRefresh = forceRefresh,
          )
          .awaitData()
          .items

  suspend fun getPostsByTagPage(
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

  suspend fun getCreatorPostsPage(
      service: String,
      creatorId: String,
      offset: Int,
      forceRefresh: Boolean,
  ): PagedResult<Post> {
    if (forceRefresh) deleteCacheGroup("pawchive:$service:$creatorId:posts:")
    return creatorPostsStore
        .query(CreatorPostsKey(CreatorKey(service, creatorId), offset))
        .awaitData()
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

  suspend fun getFavoritePosts(forceRefresh: Boolean = false): List<Post> =
      observeFavoritePosts(forceRefresh).awaitData()

  fun hasSession(): Boolean = api.hasSession()

  suspend fun clearFavoritesCache() =
      withContext(ioContext) { dao.delete("pawchive:favorites:posts") }

  suspend fun isFavoritePost(
      service: String,
      creatorId: String,
      postId: String,
  ): Boolean =
      withContext(ioContext) {
        getFavoritePosts().any {
          it.service == service && it.id == postId && it.creatorId == creatorId
        }
      }

  suspend fun addFavoritePost(
      service: String,
      creatorId: String,
      postId: String,
  ) =
      withContext(ioContext) {
        api.addFavoritePost(service, creatorId, postId)
        dao.delete("pawchive:favorites:posts")
      }

  suspend fun removeFavoritePost(
      service: String,
      creatorId: String,
      postId: String,
  ) =
      withContext(ioContext) {
        api.removeFavoritePost(service, creatorId, postId)
        dao.delete("pawchive:favorites:posts")
      }

  suspend fun getPostComments(
      service: String,
      creatorId: String,
      postId: String,
  ): List<Comment> = withContext(ioContext) { api.getPostComments(service, creatorId, postId) }

  suspend fun downloadFile(url: String): ByteArray =
      withContext(ioContext) { mediaDownloader.download(url) }

  private suspend fun deleteCacheGroup(prefix: String) {
    withContext(ioContext) { dao.deleteKeyAndPrefixed(prefix, "$prefix%") }
  }
}

internal suspend fun <T : Any> Flow<QueryState<T>>.awaitData(): T {
  val state = filter { it.data != null || it.error != null }.first()
  state.error?.let { throw it.asException() }
  return requireNotNull(state.data)
}
