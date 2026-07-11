package ddd.kc.data.remote.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import ddd.kc.data.model.PageInfo
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostFile

private const val PAGE_SIZE = 50

internal fun Element.toPostCard(): Post? {
  val postHref = selectFirst("a[href*=/post/]")?.attr("href")?.ifBlankOrNull()
  val pathParts = postHref?.substringBefore('?')?.split('/')?.filter { it.isNotBlank() }.orEmpty()
  val id = attr("data-id").ifBlankOrNull() ?: pathParts.idFromPostPath() ?: return null
  val service = attr("data-service").ifBlankOrNull() ?: pathParts.getOrNull(0) ?: return null
  val user = attr("data-user").ifBlankOrNull() ?: pathParts.valueAfter("user") ?: return null
  val title =
      selectFirst(".post-card__header")?.text()?.trim()?.ifBlankOrNull()
          ?: selectFirst("img.post-card__image")?.attr("alt")?.trim()?.ifBlankOrNull()
          ?: ""
  val published = selectFirst("time")?.attr("datetime")?.ifBlank { null }
  val imagePath = selectFirst("img.post-card__image")?.attr("src")?.toPostFilePath()
  val attachmentCount = text().parseAttachmentCount()
  val favoriteCount = text().parseFavoriteCount()
  return Post(
      id = id,
      user = user,
      service = service,
      title = title,
      favoriteCount = favoriteCount,
      published = published,
      added = published,
      file = imagePath?.let { PostFile(name = it.substringAfterLast('/'), path = it) },
      attachmentCount = attachmentCount,
  )
}

internal fun List<String>.valueAfter(name: String): String? {
  val index = indexOf(name)
  return if (index >= 0) getOrNull(index + 1)?.ifBlankOrNull() else null
}

internal fun List<String>.idFromPostPath(): String? {
  val index = indexOf("post")
  return if (index >= 0) getOrNull(index + 1)?.ifBlankOrNull() else null
}

internal fun String.toPostFilePath(): String? {
  val normalized =
      when {
        contains("/thumbnail/data") -> substringAfter("/thumbnail/data")
        contains("/data") -> substringAfter("/data")
        else -> this
      }
  return normalized.substringBefore('?').takeIf { it.startsWith("/") && it.isNotBlank() }
}

internal fun String.parseAttachmentCount(): Int? {
  if (Regex("""(?i)\bno\s+attachments\b""").containsMatchIn(this)) return 0
  return Regex("""(?i)\b(\d+)\s+attachments?\b""")
      .find(this)
      ?.groupValues
      ?.getOrNull(1)
      ?.toIntOrNull()
}

internal fun String.parseFavoriteCount(): Int? =
    Regex("""(?i)\b(\d+)\s+favorites?\b""").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

fun parsePageInfo(body: String, currentOffset: Int = 0): PageInfo? {
  val doc = Ksoup.parse(body)
  val countLastOffset =
      doc.selectFirst("""meta[name=count]""")?.attr("content")?.toIntOrNull()?.let { count ->
        ((count - 1).coerceAtLeast(0) / PAGE_SIZE) * PAGE_SIZE
      }
  val linkedLastOffset =
      doc.select("a[href*=o=]")
          .mapNotNull { hrefQueryParam(it.attr("href"), "o")?.toIntOrNull() }
          .maxOrNull()
  val lastOffset = countLastOffset ?: linkedLastOffset ?: return null
  val normalizedCurrentOffset = currentOffset.coerceAtLeast(0)
  val currentPage = normalizedCurrentOffset / PAGE_SIZE + 1
  val lastPage = lastOffset / PAGE_SIZE + 1
  return PageInfo(
      currentPage = currentPage.coerceIn(1, lastPage.coerceAtLeast(1)),
      lastPage = lastPage.coerceAtLeast(1),
      currentOffset = normalizedCurrentOffset,
      lastOffset = lastOffset.coerceAtLeast(0),
  )
}

internal fun hrefQueryParam(href: String, name: String): String? =
    href
        .substringAfter('?', "")
        .split('&')
        .firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")
        ?.ifBlank { null }

internal fun String?.ifBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }
