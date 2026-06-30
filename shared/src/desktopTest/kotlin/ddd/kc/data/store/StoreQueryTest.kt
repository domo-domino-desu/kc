package ddd.kc.data.store

import ddd.kc.data.local.dao.CacheDao
import ddd.kc.data.local.entity.CacheEntity
import ddd.kc.data.repository.currentTimeMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class StoreQueryTest {
  @Test
  fun queryUsesFreshCacheWithoutFetching() = runBlocking {
    var calls = 0
    val dao = FakeCacheDao()
    dao.upsert(CacheEntity("key:a", "cached", currentTimeMs()))
    val store =
        rawBodyQueryStore<String, String>(
            cacheDao = dao,
            namespace = CacheNamespace.PostList,
            fetcherName = "test-fresh",
            cacheKey = { "key:$it" },
            fetch = {
              calls += 1
              "fresh"
            },
            parse = { _, body -> body },
        )

    val state = withTimeout(5_000) { store.query("a").filter { it.data != null }.first() }

    assertEquals("cached", state.data)
    assertEquals(true, state.isFromCache)
    assertEquals(0, calls)
    coroutineContext.cancelChildren()
  }

  @Test
  fun queryEmitsStaleCacheThenFreshData() = runBlocking {
    var calls = 0
    val dao = FakeCacheDao()
    dao.upsert(CacheEntity("key:a", "cached", currentTimeMs() - 2 * 60 * 60 * 1000L))
    val store =
        rawBodyQueryStore<String, String>(
            cacheDao = dao,
            namespace = CacheNamespace.PostList,
            fetcherName = "test-stale",
            cacheKey = { "key:$it" },
            fetch = {
              calls += 1
              "fresh"
            },
            parse = { _, body -> body },
        )

    val values =
        withTimeout(5_000) {
          store.query("a").filter { it.data != null }.map { it.data!! }.take(2).toList()
        }

    assertEquals(listOf("cached", "fresh"), values)
    assertEquals(1, calls)
    coroutineContext.cancelChildren()
  }
}

private class FakeCacheDao : CacheDao {
  private val entries = mutableMapOf<String, CacheEntity>()

  override suspend fun findByKey(key: String): CacheEntity? = entries[key]

  override suspend fun upsert(entity: CacheEntity) {
    entries[entity.cacheKey] = entity
  }

  override suspend fun delete(key: String) {
    entries.remove(key)
  }

  override suspend fun deleteKeyAndPrefixed(key: String, prefix: String) {
    entries.keys
        .filter { it == key || it.startsWith(prefix.removeSuffix("%")) }
        .forEach(entries::remove)
  }

  override suspend fun deleteExpired(beforeMs: Long) {
    entries.values.filter { it.cachedAtMs < beforeMs }.map { it.cacheKey }.forEach(entries::remove)
  }

  override suspend fun deleteAll() {
    entries.clear()
  }
}
