package ddd.kc.data.remote.cache

import ddd.kc.data.local.dao.CacheDao
import ddd.kc.data.local.entity.CacheEntity
import ddd.kc.utils.currentTimeMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class RawBodyQueryStoreTest {
  @Test
  fun queryUsesFreshCacheWithoutFetching() = runBlocking {
    var calls = 0
    val dao = FakeCacheDao()
    dao.upsert(CacheEntity("key:a", "cached", currentTimeMs()))
    val store =
        rawBodyQueryStore<String, String>(
            cacheDao = dao,
            namespace = CacheNamespace.PostList,
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
            cacheKey = { "key:$it" },
            fetch = {
              calls += 1
              "fresh"
            },
            parse = { _, body -> body },
        )

    val states = withTimeout(5_000) { store.query("a").filter { it.data != null }.take(2).toList() }

    assertEquals(listOf("cached", "fresh"), states.map { it.data })
    assertEquals(true, states.first().isStale)
    assertFalse(states.last().isStale)
    assertFalse(states.last().isFromCache)
    assertEquals(1, calls)
    coroutineContext.cancelChildren()
  }

  @Test
  fun corruptedCacheIsDeletedAndRecoveredFromNetwork() = runBlocking {
    var calls = 0
    val dao = FakeCacheDao()
    dao.upsert(CacheEntity("key:a", "corrupted", currentTimeMs()))
    val store =
        rawBodyQueryStore<String, String>(
            cacheDao = dao,
            namespace = CacheNamespace.PostList,
            cacheKey = { "key:$it" },
            fetch = {
              calls += 1
              "fresh"
            },
            parse = { _, body ->
              require(body != "corrupted")
              body
            },
        )

    val state = withTimeout(5_000) { store.query("a").filter { it.data != null }.first() }

    assertEquals("fresh", state.data)
    assertFalse(state.isStale)
    assertEquals(1, calls)
    assertEquals("fresh", dao.findByKey("key:a")?.dataJson)
    coroutineContext.cancelChildren()
  }

  @Test
  fun concurrentReadersShareOneFetchPerKey() = runBlocking {
    var calls = 0
    val dao = FakeCacheDao()
    val store =
        rawBodyQueryStore<String, String>(
            cacheDao = dao,
            namespace = CacheNamespace.PostList,
            cacheKey = { "key:$it" },
            fetch = {
              calls += 1
              delay(50)
              "fresh"
            },
            parse = { _, body -> body },
        )

    val first = async { store.query("a").filter { it.data != null }.first() }
    val second = async { store.query("a").filter { it.data != null }.first() }

    assertEquals("fresh", first.await().data)
    assertEquals("fresh", second.await().data)
    assertEquals(1, calls)
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
