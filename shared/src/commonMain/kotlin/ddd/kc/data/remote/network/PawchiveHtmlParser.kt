package ddd.kc.data.remote.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import ddd.kc.data.model.CommunityEmbed
import ddd.kc.data.model.CommunityLounge
import ddd.kc.data.model.CommunityMessage
import ddd.kc.data.model.CommunityPage
import ddd.kc.data.model.CommunityReply
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

fun parsePageInfo(body: String, currentOffset: Int = 0, pageSize: Int = PAGE_SIZE): PageInfo? {
  val doc = Ksoup.parse(body)
  val countLastOffset =
      doc.selectFirst("""meta[name=count]""")?.attr("content")?.toIntOrNull()?.let { count ->
        ((count - 1).coerceAtLeast(0) / pageSize) * pageSize
      }
  val linkedLastOffset =
      doc.select("a[href*=o=]")
          .mapNotNull { hrefQueryParam(it.attr("href"), "o")?.toIntOrNull() }
          .maxOrNull()
  val lastOffset = countLastOffset ?: linkedLastOffset ?: return null
  val normalizedCurrentOffset = currentOffset.coerceAtLeast(0)
  val currentPage = normalizedCurrentOffset / pageSize + 1
  val lastPage = lastOffset / pageSize + 1
  return PageInfo(
      currentPage = currentPage.coerceIn(1, lastPage.coerceAtLeast(1)),
      lastPage = lastPage.coerceAtLeast(1),
      currentOffset = normalizedCurrentOffset,
      lastOffset = lastOffset.coerceAtLeast(0),
  )
}

fun parseCommunityPage(body: String, currentOffset: Int = 0): CommunityPage {
  val doc = Ksoup.parse(body)
  val lounges =
      doc.select(".community__lounges .community__lounge[href]").mapNotNull { link ->
        val parts = link.attr("href").substringBefore('?').split('/').filter(String::isNotBlank)
        val id = parts.valueAfter("community") ?: return@mapNotNull null
        val name = link.selectFirst(".community__lounge-name")?.text()?.trim().orEmpty()
        if (name.isBlank()) return@mapNotNull null
        CommunityLounge(
            id = id,
            name = name,
            messageCount =
                link.selectFirst(".community__lounge-count")?.text()?.trim()?.toIntOrNull() ?: 0,
        )
      }
  val selectedLoungeId =
      doc.selectFirst(".community__lounge.is-active[href]")
          ?.attr("href")
          ?.substringBefore('?')
          ?.split('/')
          ?.filter(String::isNotBlank)
          ?.valueAfter("community") ?: lounges.firstOrNull()?.id.orEmpty()
  val groups = mutableListOf<MutableList<CommunityMessage>>()
  doc.select(".community__chat > .cmsg").forEachIndexed { index, element ->
    val startsGroup = element.hasClass("cmsg--head")
    val authorElement = element.selectFirst(".cmsg__author")
    val author =
        authorElement?.attr("title")?.ifBlankOrNull()
            ?: authorElement?.text()?.trim()?.ifBlankOrNull()
    val published = element.selectFirst("time.cmsg__time")?.attr("datetime")?.ifBlankOrNull()
    val avatar = element.selectFirst(".cmsg__avatar")
    val avatarUrl = avatar?.takeIf { it.tagName() == "img" }?.attr("src")?.ifBlankOrNull()
    val avatarInitial =
        avatar?.takeIf { it.hasClass("cmsg__avatar--letter") }?.text()?.trim()?.ifBlankOrNull()
    val content = element.selectFirst(".cmsg__text")?.html().orEmpty()
    val replyElement = element.selectFirst(".cmsg__reply")
    val image = element.selectFirst("a.cmsg__image[href]")
    val embedElement = element.selectFirst("a.cmsg__embed[href]")
    val keySource =
        listOf(
                selectedLoungeId,
                currentOffset.toString(),
                index.toString(),
                author,
                published,
                content,
            )
            .joinToString("\u001f")
    val message =
        CommunityMessage(
            key = stableDmContentHash(keySource),
            startsGroup = startsGroup,
            author = author,
            published = published,
            avatarUrl = avatarUrl,
            avatarInitial = avatarInitial,
            avatarColor =
                avatar?.attr("style")?.substringAfter("background:", "")?.trim()?.ifBlankOrNull(),
            content = content,
            isCreator = element.hasClass("cmsg--creator"),
            isDeleted = element.hasClass("cmsg--deleted"),
            reply =
                replyElement?.let {
                  CommunityReply(
                      author = it.selectFirst(".cmsg__reply-author")?.text()?.trim().orEmpty(),
                      text = it.selectFirst(".cmsg__reply-text")?.text()?.trim().orEmpty(),
                  )
                },
            imageUrl = image?.attr("href")?.ifBlankOrNull(),
            imageAlt = image?.selectFirst("img")?.attr("alt")?.ifBlankOrNull(),
            embed =
                embedElement?.let {
                  CommunityEmbed(
                      url = it.attr("href"),
                      site = it.selectFirst(".cmsg__embed-site")?.text()?.trim()?.ifBlankOrNull(),
                      title = it.selectFirst(".cmsg__embed-title")?.text()?.trim()?.ifBlankOrNull(),
                      description =
                          it.selectFirst(".cmsg__embed-desc")?.text()?.trim()?.ifBlankOrNull(),
                  )
                },
        )
    if (startsGroup || groups.isEmpty()) groups += mutableListOf(message)
    else groups.last() += message
  }
  return CommunityPage(
      lounges = lounges,
      selectedLoungeId = selectedLoungeId,
      messages = groups.asReversed().flatten(),
      pageInfo = parsePageInfo(body, currentOffset, pageSize = 25),
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

/** Pawchive DM cards currently expose no message id, so derive one from the source body. */
internal fun stableDmContentHash(content: String): String {
  var hash = -3750763034362895579L // FNV-1a 64-bit offset basis as a signed Long.
  content.forEach { char ->
    hash = hash xor char.code.toLong()
    hash *= 1099511628211L
  }
  return hash.toULong().toString(16)
}
