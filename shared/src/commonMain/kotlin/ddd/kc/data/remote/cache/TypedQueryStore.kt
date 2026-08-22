package ddd.kc.data.remote.cache

import ddd.kc.data.local.dao.CacheDao
import ddd.kc.data.local.entity.CacheEntity
import ddd.kc.data.model.QueryState
import ddd.kc.data.remote.network.asException
import ddd.kc.data.remote.network.toQueryError
import ddd.kc.utils.currentTimeMs
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

private val typedCacheLog = KcLog.withTag("TypedQueryStore")
private const val MAX_DATABASE_CACHE_BODY_BYTES = 1024 * 1024

enum class CacheNamespace(val ttlMillis: Long) {
  Creators(24 * 60 * 60 * 1000L),
  Tags(24 * 60 * 60 * 1000L),
  PostList(60 * 60 * 1000L),
  Detail(60 * 60 * 1000L),
  Favorites(5 * 60 * 1000L),
}

private data class CachedResource<T : Any>(
    val value: T,
    val updatedAtMillis: Long,
)

/** Room-backed typed stale-while-revalidate cache with one network request per resource key. */
class TypedQueryStore<Key : Any, Output : Any>(
    private val cacheDao: CacheDao,
    private val json: Json,
    private val serializer: KSerializer<Output>,
    private val cacheKey: (Key) -> String,
    private val fetch: suspend (Key) -> Output,
    private val ttlMillis: Long,
) {
  private val mutexRegistryLock = Mutex()
  private val fetchMutexes = mutableMapOf<String, MutexEntry>()

  fun query(
      key: Key,
      refresh: Boolean = true,
      forceRefresh: Boolean = false,
  ): Flow<QueryState<Output>> = flow {
    val resolvedKey = cacheKey(key)
    val initial = readCached(resolvedKey)
    val initialStale = initial == null || isStale(initial)
    if (initial != null) {
      emit(
          QueryState(
              data = initial.value,
              isRefreshing = refresh && (forceRefresh || initialStale),
              isFromCache = true,
              isStale = initialStale,
              lastUpdatedAtMillis = initial.updatedAtMillis,
          )
      )
    } else {
      emit(QueryState(isLoading = true, isStale = true))
    }
    if (!refresh || (!forceRefresh && !initialStale)) return@flow

    val requestStartedAt = currentTimeMs()
    try {
      val fetchMutex = acquireMutex(resolvedKey)
      val fresh =
          try {
            fetchMutex.withLock {
              val latest = readCached(resolvedKey)
              if (
                  latest != null && latest.updatedAtMillis >= requestStartedAt && !isStale(latest)
              ) {
                latest
              } else {
                val value = fetch(key)
                val cachedAt = currentTimeMs()
                val body = json.encodeToString(serializer, value)
                val bodyBytes = body.encodeToByteArray().size
                if (bodyBytes <= MAX_DATABASE_CACHE_BODY_BYTES) {
                  cacheDao.upsert(CacheEntity(resolvedKey, body, cachedAt))
                } else {
                  cacheDao.delete(resolvedKey)
                  typedCacheLog.w {
                    "typed缓存跳过超大响应(keyHash=${resolvedKey.hashCode()},bytes=$bodyBytes)"
                  }
                }
                CachedResource(value, cachedAt)
              }
            }
          } finally {
            releaseMutex(resolvedKey, fetchMutex)
          }
      if (initial?.updatedAtMillis != fresh.updatedAtMillis || initialStale) {
        emit(
            QueryState(
                data = fresh.value,
                isFromCache = false,
                isStale = false,
                lastUpdatedAtMillis = fresh.updatedAtMillis,
            )
        )
      }
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      emit(
          QueryState(
              data = initial?.value,
              isFromCache = initial != null,
              isStale = initialStale,
              error = error.toQueryError(),
              lastUpdatedAtMillis = initial?.updatedAtMillis,
          )
      )
    }
  }

  suspend fun queryOnce(key: Key, forceRefresh: Boolean = false): Output {
    val state =
        query(key, forceRefresh = forceRefresh)
            .filter { it.data != null || it.error != null }
            .first()
    state.error?.let { throw it.asException() }
    return requireNotNull(state.data)
  }

  private suspend fun readCached(resolvedKey: String): CachedResource<Output>? {
    val cached = cacheDao.findByKey(resolvedKey) ?: return null
    return try {
      CachedResource(json.decodeFromString(serializer, cached.body), cached.cachedAtMs)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      typedCacheLog.w(error) { "typed缓存解析失败，删除损坏条目(keyHash=${resolvedKey.hashCode()})" }
      cacheDao.delete(resolvedKey)
      null
    }
  }

  private fun isStale(resource: CachedResource<Output>): Boolean =
      currentTimeMs() - resource.updatedAtMillis > ttlMillis

  private suspend fun acquireMutex(resolvedKey: String): Mutex =
      mutexRegistryLock.withLock {
        val entry = fetchMutexes.getOrPut(resolvedKey) { MutexEntry(Mutex()) }
        entry.borrowers++
        entry.mutex
      }

  private suspend fun releaseMutex(resolvedKey: String, mutex: Mutex) {
    mutexRegistryLock.withLock {
      val entry = fetchMutexes[resolvedKey] ?: return@withLock
      if (entry.mutex !== mutex) return@withLock
      entry.borrowers--
      if (entry.borrowers == 0) fetchMutexes.remove(resolvedKey)
    }
  }

  private class MutexEntry(val mutex: Mutex, var borrowers: Int = 0)
}

fun <Key : Any, Output : Any> typedQueryStore(
    cacheDao: CacheDao,
    json: Json,
    serializer: KSerializer<Output>,
    namespace: CacheNamespace,
    cacheKey: (Key) -> String,
    fetch: suspend (Key) -> Output,
): TypedQueryStore<Key, Output> =
    TypedQueryStore(
        cacheDao = cacheDao,
        json = json,
        serializer = serializer,
        cacheKey = cacheKey,
        fetch = fetch,
        ttlMillis = namespace.ttlMillis,
    )
