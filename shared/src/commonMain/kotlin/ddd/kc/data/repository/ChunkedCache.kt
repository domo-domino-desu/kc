package ddd.kc.data.repository

import ddd.kc.data.local.dao.CacheDao
import ddd.kc.data.local.entity.CacheEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CHUNKED_CACHE_VERSION = 1
private const val CHUNK_TARGET_JSON_CHARS = 350_000

@Serializable
internal data class ChunkedListCacheMeta(
    val version: Int = CHUNKED_CACHE_VERSION,
    val chunkCount: Int,
    val itemCount: Int,
)

internal suspend inline fun <reified T> CacheDao.readChunkedList(
    json: Json,
    key: String,
    ttlMs: Long,
    nowMs: Long,
): List<T>? {
  val metaEntity = findByKey(metaKey(key)) ?: return null
  if (nowMs - metaEntity.cachedAtMs > ttlMs) return null

  val meta = json.decodeFromString<ChunkedListCacheMeta>(metaEntity.dataJson)
  if (meta.version != CHUNKED_CACHE_VERSION) return null

  val items = ArrayList<T>(meta.itemCount)
  repeat(meta.chunkCount) { index ->
    val chunk = findByKey(chunkKey(key, index)) ?: return null
    items += json.decodeFromString<List<T>>(chunk.dataJson)
  }
  return items
}

internal suspend inline fun <reified T> CacheDao.writeChunkedList(
    json: Json,
    key: String,
    items: List<T>,
    cachedAtMs: Long,
): Int {
  deleteKeyAndPrefixed(key, "$key:%")

  val chunks = mutableListOf<List<T>>()
  var chunk = mutableListOf<T>()
  var chunkChars = 2
  for (item in items) {
    val itemChars = json.encodeToString(item).length + if (chunk.isEmpty()) 0 else 1
    if (chunk.isNotEmpty() && chunkChars + itemChars > CHUNK_TARGET_JSON_CHARS) {
      chunks += chunk
      chunk = mutableListOf()
      chunkChars = 2
    }
    chunk += item
    chunkChars += itemChars
  }
  if (chunk.isNotEmpty()) chunks += chunk

  chunks.forEachIndexed { index, values ->
    upsert(CacheEntity(chunkKey(key, index), json.encodeToString(values), cachedAtMs))
  }
  upsert(
      CacheEntity(
          metaKey(key),
          json.encodeToString(
              ChunkedListCacheMeta(
                  chunkCount = chunks.size,
                  itemCount = items.size,
              )
          ),
          cachedAtMs,
      )
  )
  return chunks.size
}

private fun metaKey(key: String): String = "$key:meta"

private fun chunkKey(key: String, index: Int): String = "$key:chunk:$index"
