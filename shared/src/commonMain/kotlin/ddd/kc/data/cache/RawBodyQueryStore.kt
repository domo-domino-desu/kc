package ddd.kc.data.cache

import ddd.kc.data.local.dao.CacheDao
import ddd.kc.data.local.entity.CacheEntity
import ddd.kc.data.model.QueryState
import ddd.kc.data.network.toQueryError
import ddd.kc.data.repository.currentTimeMs
import ddd.kc.utils.logging.KcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val cacheLog = KcLog.withTag("RawBodyQueryStore")

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

/** Room-backed stale-while-revalidate cache with one network request per resource key. */
class RawBodyQueryStore<Key : Any, Output : Any>(
    private val cacheDao: CacheDao,
    private val cacheKey: (Key) -> String,
    private val fetch: suspend (Key) -> String,
    private val parse: (Key, String) -> Output,
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
    val initial = readCached(key, resolvedKey)
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
              val latest = readCached(key, resolvedKey)
              if (
                  latest != null && latest.updatedAtMillis >= requestStartedAt && !isStale(latest)
              ) {
                latest
              } else {
                val rawBody = fetch(key)
                val parsed = parse(key, rawBody)
                val cachedAt = currentTimeMs()
                cacheDao.upsert(CacheEntity(resolvedKey, rawBody, cachedAt))
                CachedResource(parsed, cachedAt)
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

  private suspend fun readCached(key: Key, resolvedKey: String): CachedResource<Output>? {
    val entity = cacheDao.findByKey(resolvedKey) ?: return null
    return try {
      CachedResource(parse(key, entity.dataJson), entity.cachedAtMs)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      cacheLog.w(error) { "缓存解析失败，删除损坏条目(keyHash=${resolvedKey.hashCode()})" }
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

fun <Key : Any, Output : Any> rawBodyQueryStore(
    cacheDao: CacheDao,
    namespace: CacheNamespace,
    cacheKey: (Key) -> String,
    fetch: suspend (Key) -> String,
    parse: (Key, String) -> Output,
): RawBodyQueryStore<Key, Output> =
    RawBodyQueryStore(
        cacheDao = cacheDao,
        cacheKey = cacheKey,
        fetch = fetch,
        parse = parse,
        ttlMillis = namespace.ttlMillis,
    )
