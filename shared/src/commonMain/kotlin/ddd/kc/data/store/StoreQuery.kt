package ddd.kc.data.store

import ddd.kc.data.local.dao.CacheDao
import ddd.kc.data.local.entity.CacheEntity
import ddd.kc.data.model.QueryError
import ddd.kc.data.model.QueryState
import ddd.kc.data.network.toQueryError
import ddd.kc.data.repository.currentTimeMs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan
import org.mobilenativefoundation.store.store5.Converter
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.mobilenativefoundation.store.store5.StoreReadResponseOrigin

enum class CacheNamespace(val ttlMillis: Long) {
  Creators(24 * 60 * 60 * 1000L),
  Tags(24 * 60 * 60 * 1000L),
  PostList(60 * 60 * 1000L),
  Detail(60 * 60 * 1000L),
  Favorites(5 * 60 * 1000L),
}

data class CachedResource<T : Any>(
    val value: T,
    val updatedAtMillis: Long,
)

class RawBodyQueryStore<Key : Any, Output : Any>(
    private val cacheDao: CacheDao,
    private val cacheKey: (Key) -> String,
    private val parse: (Key, String) -> Output,
    private val ttlMillis: Long,
    private val store: Store<Key, CachedResource<Output>>,
) {
  fun query(
      key: Key,
      refresh: Boolean = true,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<Output>> = flow {
    val cached = cacheDao.findByKey(cacheKey(key))
    val stale = cached == null || currentTimeMs() - cached.cachedAtMs > ttlMillis
    val cachedResource = cached?.let { CachedResource(parse(key, it.dataJson), it.cachedAtMs) }
    if (cachedResource != null) {
      emit(
          QueryState(
              data = cachedResource.value,
              isRefreshing = refresh && (forceRefresh || stale),
              isFromCache = true,
              isStale = stale,
              lastUpdatedAtMillis = cachedResource.updatedAtMillis,
          )
      )
    } else {
      emit(QueryState(isLoading = true, isStale = true))
    }
    if (!forceRefresh && (!refresh || !stale)) return@flow
    store
        .stream(StoreReadRequest.freshWithFallBackToSourceOfTruth(key))
        .scan(
            QueryAccumulator(
                data = cachedResource?.value,
                updatedAtMillis = cachedResource?.updatedAtMillis,
                isFromCache = cachedResource != null,
                isStale = stale,
                isRefreshing = cachedResource != null,
                isLoading = cachedResource == null,
            )
        ) { accumulator, response ->
          accumulator.reduce(response)
        }
        .collect { accumulator ->
          val next = accumulator.toState()
          if (
              cachedResource == null ||
                  next.lastUpdatedAtMillis != cachedResource.updatedAtMillis ||
                  next.error != null
          ) {
            emit(next)
          }
        }
  }
}

fun <Key : Any, Output : Any> rawBodyQueryStore(
    cacheDao: CacheDao,
    namespace: CacheNamespace,
    fetcherName: String,
    cacheKey: (Key) -> String,
    fetch: suspend (Key) -> String,
    parse: (Key, String) -> Output,
): RawBodyQueryStore<Key, Output> {
  val fetcher = Fetcher.of(fetcherName) { key: Key -> fetch(key) }
  val updates = MutableSharedFlow<Key>(replay = 1, extraBufferCapacity = 64)
  val sourceOfTruth =
      object : SourceOfTruth<Key, String, CachedResource<Output>> {
        override fun reader(key: Key): Flow<CachedResource<Output>> = flow {
          cacheDao.findByKey(cacheKey(key))?.let { entity ->
            emit(CachedResource(parse(key, entity.dataJson), entity.cachedAtMs))
          }
          updates
              .filter { it == key }
              .collect {
                cacheDao.findByKey(cacheKey(key))?.let { entity ->
                  emit(CachedResource(parse(key, entity.dataJson), entity.cachedAtMs))
                }
              }
        }

        override suspend fun write(key: Key, value: String) {
          cacheDao.upsert(CacheEntity(cacheKey(key), value, currentTimeMs()))
          updates.emit(key)
        }

        override suspend fun delete(key: Key) {
          cacheDao.delete(cacheKey(key))
        }

        override suspend fun deleteAll() {
          cacheDao.deleteAll()
        }
      }
  val converter =
      object : Converter<String, String, CachedResource<Output>> {
        override fun fromNetworkToLocal(network: String): String = network

        override fun fromOutputToLocal(output: CachedResource<Output>): String =
            error("Raw response bodies cannot be reconstructed from parsed output")
      }
  return RawBodyQueryStore(
      cacheDao = cacheDao,
      cacheKey = cacheKey,
      parse = parse,
      ttlMillis = namespace.ttlMillis,
      store = StoreBuilder.from(fetcher, sourceOfTruth, converter).build(),
  )
}

private data class QueryAccumulator<T : Any>(
    val data: T? = null,
    val updatedAtMillis: Long? = null,
    val isFromCache: Boolean = false,
    val isStale: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: QueryError? = null,
) {
  fun reduce(response: StoreReadResponse<CachedResource<T>>): QueryAccumulator<T> =
      when (response) {
        StoreReadResponse.Initial -> copy(isLoading = data == null, error = null)
        is StoreReadResponse.Loading ->
            if (data == null) copy(isLoading = true, isRefreshing = false, error = null)
            else copy(isLoading = false, isRefreshing = true, error = null)
        is StoreReadResponse.Data ->
            copy(
                data = response.value.value,
                updatedAtMillis = response.value.updatedAtMillis,
                isFromCache = response.origin !is StoreReadResponseOrigin.Fetcher,
                isLoading = false,
                isRefreshing = false,
                error = null,
            )
        is StoreReadResponse.NoNewData -> copy(isLoading = false, isRefreshing = false)
        is StoreReadResponse.Error ->
            copy(isLoading = false, isRefreshing = false, error = response.toQueryError())
      }

  fun toState(): QueryState<T> =
      QueryState(
          data = data,
          isLoading = isLoading,
          isRefreshing = isRefreshing,
          isFromCache = isFromCache,
          isStale = isStale,
          error = error,
          lastUpdatedAtMillis = updatedAtMillis,
      )
}

private fun StoreReadResponse.Error.toQueryError(): QueryError =
    when (this) {
      is StoreReadResponse.Error.Exception -> error.toQueryError()
      is StoreReadResponse.Error.Message -> QueryError.Unknown(message)
      is StoreReadResponse.Error.Custom<*> -> {
        val value = error
        if (value is QueryError) value else QueryError.Unknown(value.toString())
      }
    }
