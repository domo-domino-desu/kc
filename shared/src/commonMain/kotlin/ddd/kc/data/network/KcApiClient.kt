package ddd.kc.data.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import ddd.kc.data.model.Announcement
import ddd.kc.data.model.Comment
import ddd.kc.data.model.Creator
import ddd.kc.data.model.DM
import ddd.kc.data.model.DiscordChannel
import ddd.kc.data.model.DiscordPost
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostFile
import ddd.kc.data.model.Tag
import ddd.kc.data.settings.AppSettings
import ddd.kc.utils.logging.KcLog
import ddd.kc.utils.logging.summarizePost
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val log = KcLog.withTag("KcApi")
private const val PAGE_SIZE = 50

@Serializable
data class PopularPage(
    val props: PopularProps = PopularProps(),
    val info: PopularInfo = PopularInfo(),
    val posts: List<Post> = emptyList(),
    val pageInfo: PageInfo? = null,
)

@Serializable
data class PageInfo(
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val currentOffset: Int = 0,
    val lastOffset: Int = 0,
) {
  val hasPrevious: Boolean
    get() = currentPage > 1

  val hasNext: Boolean
    get() = currentPage < lastPage
}

@Serializable
data class PagedResult<T>(val items: List<T> = emptyList(), val pageInfo: PageInfo? = null)

@Serializable
data class PopularProps(
    val count: Int = 0,
    @kotlinx.serialization.SerialName("earliest_date_for_popular")
    val earliestDateForPopular: String? = null,
    val today: String? = null,
)

@Serializable
data class PopularInfo(
    val date: String? = null,
    @kotlinx.serialization.SerialName("max_date") val maxDate: String? = null,
    @kotlinx.serialization.SerialName("min_date") val minDate: String? = null,
    @kotlinx.serialization.SerialName("navigation_dates")
    val navigationDates: PopularNavigationDates? = null,
    @kotlinx.serialization.SerialName("range_desc") val rangeDesc: String? = null,
    val scale: String? = null,
)

@Serializable
data class PopularNavigationDates(
    val recent: List<String> = emptyList(),
    val day: List<String> = emptyList(),
    val month: List<String> = emptyList(),
    val week: List<String> = emptyList(),
)

class KcApiClient(
    private val client: HttpClient,
    private val settings: AppSettings,
    private val sessionStore: KcSessionStore,
    private val json: Json,
) {

  private fun Platform.url() = settings.baseUrl(this)

  fun hasSession(platform: Platform = Platform.PAWCHIVE): Boolean =
      sessionStore.hasSession(platform)

  private fun sessionCookie(platform: Platform = Platform.PAWCHIVE): String =
      sessionStore.cookieHeader(platform)

  private suspend fun requireSuccess(response: HttpResponse, label: String): String {
    if (response.status == HttpStatusCode.Unauthorized) {
      log.w { "$label -> 需要登录" }
      throw AuthRequiredException()
    }
    if (!response.status.isSuccess()) {
      val text = runCatching { response.bodyAsText() }.getOrDefault("")
      log.w { "$label -> 失败(status=${response.status.value},bodyLength=${text.length})" }
      throw KcApiException(response.status.value, "请求失败：HTTP ${response.status.value}")
    }
    return response.bodyAsText()
  }

  private suspend inline fun <reified T> decode(response: HttpResponse, label: String): T =
      requireSuccess(response, label).let { body ->
        @Suppress("UNCHECKED_CAST")
        if (T::class == Unit::class) Unit as T else json.decodeFromString(body)
      }

  suspend fun fetchCreatorsBody(platform: Platform = Platform.PAWCHIVE): String =
      requireSuccess(client.get("${platform.url()}/api/v1/creators"), "请求Creators")

  fun parseCreators(body: String): List<Creator> = json.decodeFromString(body)

  suspend fun getCreators(platform: Platform = Platform.PAWCHIVE): List<Creator> =
      parseCreators(fetchCreatorsBody(platform))

  suspend fun fetchRecentPostsBody(
      platform: Platform = Platform.PAWCHIVE,
      offset: Int = 0,
  ): String =
      requireSuccess(
          client.get("${platform.url()}/api/v1/posts") { parameter("o", offset) },
          "请求最近Posts(offset=$offset)",
      )

  fun parsePosts(body: String): List<Post> = json.decodeFromString(body)

  suspend fun getRecentPosts(platform: Platform = Platform.PAWCHIVE, offset: Int = 0): List<Post> =
      parsePosts(fetchRecentPostsBody(platform, offset))

  suspend fun fetchPopularPostsBody(
      platform: Platform = Platform.PAWCHIVE,
      date: String? = null,
      period: String? = null,
      offset: Int? = null,
  ): String =
      requireSuccess(
          client.get("${platform.url()}/posts/popular") {
            date?.let { parameter("date", it) }
            period?.let { parameter("period", it) }
            offset?.takeIf { it > 0 }?.let { parameter("o", it) }
          },
          "请求热门Posts(date=$date,period=$period,offset=$offset)",
      )

  fun parsePopularPostsPage(
      body: String,
      date: String? = null,
      period: String? = null,
      offset: Int = 0,
  ): PopularPage {
    val doc = Ksoup.parse(body)
    val posts = parsePostCards(body)
    val heading = doc.selectFirst(".site-section--popular-posts .site-section__heading")
    val rangeTitle = heading?.selectFirst("span[title]")?.attr("title")?.ifBlankOrNull()
    val headingText = heading?.text()
    val recentDates =
        doc.select("#paginator-dates a[href^=/posts/popular]")
            .map { it.attr("href") }
            .mapNotNull { hrefQueryParam(it, "date") }
            .distinct()
    val dayDates = popularDateLinks(doc.selectFirst("#daily"))
    val weekDates = popularDateLinks(doc.selectFirst("#weekly"))
    val monthDates = popularDateLinks(doc.selectFirst("#monthly"))
    val minDate = rangeTitle?.substringBefore(" to ")?.take(10)
    val maxDate = rangeTitle?.substringAfter(" to ", "")?.take(10)
    val info =
        PopularInfo(
            date = date ?: maxDate,
            minDate = minDate.ifBlankOrNull() ?: date,
            maxDate = maxDate.ifBlankOrNull() ?: date,
            rangeDesc = headingText,
            scale = period,
            navigationDates =
                PopularNavigationDates(
                    recent = recentDates,
                    day = dayDates,
                    week = weekDates,
                    month = monthDates,
                ),
        )
    return PopularPage(
        props = PopularProps(count = posts.size, today = date),
        info = info,
        posts = posts,
        pageInfo = parsePageInfo(body, offset),
    )
  }

  private fun popularDateLinks(element: Element?): List<String> =
      element
          ?.select("a[href^=/posts/popular]")
          ?.map { it.attr("href") }
          ?.mapNotNull { hrefQueryParam(it, "date") }
          ?.distinct()
          .orEmpty()

  suspend fun getPopularPosts(
      platform: Platform = Platform.PAWCHIVE,
      date: String? = null,
      period: String? = null,
      offset: Int? = null,
  ): PopularPage =
      parsePopularPostsPage(
          fetchPopularPostsBody(platform, date, period, offset),
          date,
          period,
          offset ?: 0,
      )

  suspend fun fetchPostSearchBody(
      platform: Platform = Platform.PAWCHIVE,
      query: String,
      offset: Int = 0,
      tag: String? = null,
      service: String? = null,
  ): String =
      requireSuccess(
          client.get("${platform.url()}/posts") {
            if (query.isNotBlank()) parameter("q", query)
            offset.takeIf { it > 0 }?.let { parameter("o", it) }
            tag?.let { parameter("tag", it) }
            service?.let { parameter("service", it) }
          },
          "搜索Posts(queryLength=${query.length},offset=$offset,tag=$tag,service=$service)",
      )

  fun parsePostCards(body: String): List<Post> {
    val doc = Ksoup.parse(body)
    return doc.select(".post-card").mapNotNull { it.toPostCard() }
  }

  fun parsePostCardsPage(body: String, offset: Int = 0): PagedResult<Post> =
      PagedResult(items = parsePostCards(body), pageInfo = parsePageInfo(body, offset))

  suspend fun searchPosts(
      platform: Platform = Platform.PAWCHIVE,
      query: String,
      offset: Int = 0,
      tag: String? = null,
      service: String? = null,
  ): List<Post> = parsePostCards(fetchPostSearchBody(platform, query, offset, tag, service))

  suspend fun searchPostsPage(
      platform: Platform = Platform.PAWCHIVE,
      query: String,
      offset: Int = 0,
      tag: String? = null,
      service: String? = null,
  ): PagedResult<Post> =
      parsePostCardsPage(fetchPostSearchBody(platform, query, offset, tag, service), offset)

  suspend fun getPostsByTag(
      platform: Platform = Platform.PAWCHIVE,
      tag: String,
      offset: Int = 0,
  ): List<Post> = searchPosts(platform, query = "", offset = offset, tag = tag)

  suspend fun fetchDmsBody(
      platform: Platform = Platform.PAWCHIVE,
      query: String = "",
      offset: Int = 0,
  ): String =
      requireSuccess(
          client.get("${platform.url()}/dms") {
            if (query.isNotBlank()) parameter("q", query)
            offset.takeIf { it > 0 }?.let { parameter("o", it) }
          },
          "请求DMs(queryLength=${query.length},offset=$offset)",
      )

  fun parseDms(body: String): List<DM> {
    val doc = Ksoup.parse(body)
    if (doc.selectFirst(".no-results") != null) return emptyList()
    return doc.select(".dm-card, article.dm, .card-list__items article").mapIndexedNotNull {
        index,
        element ->
      val content =
          element.selectFirst(".dm-card__content, .dm__content, .card__content")?.html()
              ?: element.text().takeIf { it.isNotBlank() }
      content?.let {
        DM(
            id = element.attr("data-id").ifBlank { "dm-$index-${it.hashCode()}" },
            service = element.attr("data-service").ifBlank { null },
            user = element.attr("data-user").ifBlank { null },
            content = it,
            added = element.selectFirst("time")?.attr("datetime"),
        )
      }
    }
  }

  fun parseDmsPage(body: String, offset: Int = 0): PagedResult<DM> =
      PagedResult(items = parseDms(body), pageInfo = parsePageInfo(body, offset))

  suspend fun getRecentDMs(platform: Platform = Platform.PAWCHIVE, offset: Int = 0): List<DM> =
      parseDms(fetchDmsBody(platform, offset = offset))

  suspend fun searchDMs(
      platform: Platform = Platform.PAWCHIVE,
      query: String,
      offset: Int = 0,
  ): List<DM> = parseDms(fetchDmsBody(platform, query, offset))

  suspend fun searchDMsPage(
      platform: Platform = Platform.PAWCHIVE,
      query: String = "",
      offset: Int = 0,
  ): PagedResult<DM> = parseDmsPage(fetchDmsBody(platform, query, offset), offset)

  suspend fun fetchTagsBody(platform: Platform = Platform.PAWCHIVE): String =
      requireSuccess(client.get("${platform.url()}/posts/tags"), "请求Tags")

  fun parseTags(body: String): List<Tag> {
    val doc = Ksoup.parse(body)
    return doc.select("#tag-container article a").mapNotNull { link ->
      val spans = link.select("span")
      val name = spans.getOrNull(0)?.text()?.trim().orEmpty()
      if (name.isBlank()) null
      else Tag(name, spans.getOrNull(1)?.text()?.trim()?.toIntOrNull() ?: 0)
    }
  }

  suspend fun getTags(platform: Platform = Platform.PAWCHIVE): List<Tag> =
      parseTags(fetchTagsBody(platform))

  suspend fun fetchCreatorPostsBody(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
      offset: Int = 0,
  ): String =
      requireSuccess(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId") {
            parameter("o", offset)
          },
          "请求Creator Posts(service=$service,creator=$creatorId,offset=$offset)",
      )

  suspend fun fetchCreatorPostsPageBody(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
      offset: Int = 0,
  ): String =
      requireSuccess(
          client.get("${platform.url()}/$service/user/$creatorId") {
            offset.takeIf { it > 0 }?.let { parameter("o", it) }
          },
          "请求Creator Posts分页(service=$service,creator=$creatorId,offset=$offset)",
      )

  suspend fun getCreatorPosts(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
      offset: Int = 0,
  ): List<Post> = parsePosts(fetchCreatorPostsBody(platform, service, creatorId, offset))

  suspend fun getCreatorPostsPageInfo(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
      offset: Int = 0,
  ): PageInfo? =
      parsePageInfo(fetchCreatorPostsPageBody(platform, service, creatorId, offset), offset)

  suspend fun fetchPostBody(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
      postId: String,
  ): String =
      requireSuccess(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId/post/$postId"),
          "请求Post详情(service=$service,creator=$creatorId,post=$postId)",
      )

  fun parsePost(body: String): Post = json.decodeFromString(body)

  suspend fun getPost(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
      postId: String,
  ): Post =
      parsePost(fetchPostBody(platform, service, creatorId, postId)).also {
        log.i { "请求Post详情 -> 成功(${summarizePost(it)})" }
      }

  suspend fun downloadFileBytes(url: String): ByteArray {
    val response = client.get(url)
    if (response.status == HttpStatusCode.Unauthorized) {
      log.w { "下载文件 -> 需要登录(urlLength=${url.length})" }
      throw AuthRequiredException()
    }
    if (!response.status.isSuccess()) {
      val text = runCatching { response.bodyAsText() }.getOrDefault("")
      log.w { "下载文件 -> 失败(status=${response.status.value},bodyLength=${text.length})" }
      throw KcApiException(response.status.value, "请求失败：HTTP ${response.status.value}")
    }
    return response.body()
  }

  suspend fun getPostComments(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
      postId: String,
  ): List<Comment> {
    val label = "请求Post评论(service=$service,creator=$creatorId,post=$postId)"
    val response =
        client.get("${platform.url()}/api/v1/$service/user/$creatorId/post/$postId/comments")
    if (response.status == HttpStatusCode.NotFound) return emptyList()
    return decode(response, label)
  }

  suspend fun getCreatorAnnouncements(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
  ): List<Announcement> =
      decode(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId/announcements"),
          "请求Creator公告(service=$service,creator=$creatorId)",
      )

  suspend fun getCreatorDMs(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
  ): List<DM> = emptyList()

  suspend fun fetchCreatorTagsBody(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
  ): String =
      requireSuccess(
          client.get("${platform.url()}/$service/user/$creatorId/tags"),
          "请求Creator Tags(service=$service,creator=$creatorId)",
      )

  fun parseCreatorTags(body: String): List<Tag> = parseTags(body)

  suspend fun getCreatorTags(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
  ): List<Tag> = parseCreatorTags(fetchCreatorTagsBody(platform, service, creatorId))

  suspend fun getCreatorLinks(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
  ): List<Creator> =
      decode(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId/links"),
          "请求Creator Links(service=$service,creator=$creatorId)",
      )

  suspend fun getCreatorProfile(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
  ): Creator =
      decode(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId/profile"),
          "请求Creator Profile(service=$service,creator=$creatorId)",
      )

  suspend fun getRecommendedCreators(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
  ): List<Creator> = emptyList()

  suspend fun getFavorites(platform: Platform = Platform.PAWCHIVE, type: String): List<Creator> =
      decode(
          client.get("${platform.url()}/api/v1/account/favorites") {
            parameter("type", type)
            header(HttpHeaders.Cookie, sessionCookie(platform))
          },
          "请求Favorites(type=$type)",
      )

  suspend fun getFavoritePosts(platform: Platform = Platform.PAWCHIVE): List<Post> =
      decode(
          client.get("${platform.url()}/api/v1/account/favorites") {
            parameter("type", "post")
            header(HttpHeaders.Cookie, sessionCookie(platform))
          },
          "请求Favorites(type=post)",
      )

  suspend fun addFavoriteCreator(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
  ) {
    decode<Unit>(
        client.post("${platform.url()}/api/v1/favorites/creator/$service/$creatorId") {
          header(HttpHeaders.Cookie, sessionCookie(platform))
        },
        "收藏Creator -> 添加(service=$service,creator=$creatorId)",
    )
  }

  suspend fun removeFavoriteCreator(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
  ) {
    decode<Unit>(
        client.delete("${platform.url()}/api/v1/favorites/creator/$service/$creatorId") {
          header(HttpHeaders.Cookie, sessionCookie(platform))
        },
        "收藏Creator -> 移除(service=$service,creator=$creatorId)",
    )
  }

  suspend fun addFavoritePost(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
      postId: String,
  ) {
    decode<Unit>(
        client.post("${platform.url()}/api/v1/favorites/post/$service/$creatorId/$postId") {
          header(HttpHeaders.Cookie, sessionCookie(platform))
        },
        "收藏Post -> 添加(service=$service,creator=$creatorId,post=$postId)",
    )
  }

  suspend fun removeFavoritePost(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
      postId: String,
  ) {
    decode<Unit>(
        client.delete("${platform.url()}/api/v1/favorites/post/$service/$creatorId/$postId") {
          header(HttpHeaders.Cookie, sessionCookie(platform))
        },
        "收藏Post -> 移除(service=$service,creator=$creatorId,post=$postId)",
    )
  }

  suspend fun flagPost(
      platform: Platform = Platform.PAWCHIVE,
      service: String,
      creatorId: String,
      postId: String,
  ) {
    decode<Unit>(
        client.post("${platform.url()}/api/v1/$service/user/$creatorId/post/$postId/flag") {
          if (hasSession(platform)) header(HttpHeaders.Cookie, sessionCookie(platform))
        },
        "Flag Post(service=$service,creator=$creatorId,post=$postId)",
    )
  }

  suspend fun login(platform: Platform = Platform.PAWCHIVE, username: String, password: String) {
    sessionStore.saveSession(platform, password.ifBlank { username })
  }

  suspend fun logout(platform: Platform = Platform.PAWCHIVE) {
    sessionStore.clearSession(platform)
  }

  suspend fun getDiscordChannels(
      platform: Platform = Platform.PAWCHIVE,
      serverId: String,
  ): List<DiscordChannel> = emptyList()

  suspend fun getDiscordChannelPosts(
      platform: Platform = Platform.PAWCHIVE,
      channelId: String,
      offset: Int = 0,
  ): List<DiscordPost> = emptyList()
}

private fun Element.toPostCard(): Post? {
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

private fun List<String>.valueAfter(name: String): String? {
  val index = indexOf(name)
  return if (index >= 0) getOrNull(index + 1)?.ifBlankOrNull() else null
}

private fun List<String>.idFromPostPath(): String? {
  val index = indexOf("post")
  return if (index >= 0) getOrNull(index + 1)?.ifBlankOrNull() else null
}

private fun String.toPostFilePath(): String? {
  val normalized =
      when {
        contains("/thumbnail/data") -> substringAfter("/thumbnail/data")
        contains("/data") -> substringAfter("/data")
        else -> this
      }
  return normalized.substringBefore('?').takeIf { it.startsWith("/") && it.isNotBlank() }
}

private fun String.parseAttachmentCount(): Int? {
  if (Regex("""(?i)\bno\s+attachments\b""").containsMatchIn(this)) return 0
  return Regex("""(?i)\b(\d+)\s+attachments?\b""")
      .find(this)
      ?.groupValues
      ?.getOrNull(1)
      ?.toIntOrNull()
}

private fun String.parseFavoriteCount(): Int? =
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

private fun hrefQueryParam(href: String, name: String): String? =
    href
        .substringAfter('?', "")
        .split('&')
        .firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")
        ?.ifBlank { null }

private fun String?.ifBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }
