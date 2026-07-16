package ddd.kc.data.remote.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import ddd.kc.data.model.Announcement
import ddd.kc.data.model.Comment
import ddd.kc.data.model.Creator
import ddd.kc.data.model.DM
import ddd.kc.data.model.PagedResult
import ddd.kc.data.model.PopularInfo
import ddd.kc.data.model.PopularNavigationDates
import ddd.kc.data.model.PopularPage
import ddd.kc.data.model.PopularProps
import ddd.kc.data.model.Post
import ddd.kc.data.model.Tag
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

class PawchiveApi(
    private val gateway: PawchiveHttpGateway,
    private val json: Json,
) {
  fun hasSession(): Boolean = gateway.hasSession()

  private inline fun <reified T> decode(body: String): T = json.decodeFromString(body)

  suspend fun fetchCreatorsBody(): String = gateway.getText("/api/v1/creators", "请求Creators")

  fun parseCreators(body: String): List<Creator> = json.decodeFromString(body)

  suspend fun fetchRecentPostsBody(
      offset: Int = 0,
  ): String =
      gateway.getText(
          "/api/v1/posts",
          "请求最近Posts(offset=$offset)",
      ) {
        parameter("o", offset)
      }

  fun parsePosts(body: String): List<Post> = json.decodeFromString(body)

  suspend fun fetchPopularPostsBody(
      date: String? = null,
      period: String? = null,
      offset: Int? = null,
  ): String =
      gateway.getText(
          "/posts/popular",
          "请求热门Posts(date=$date,period=$period,offset=$offset)",
      ) {
        date?.let { parameter("date", it) }
        period?.let { parameter("period", it) }
        offset?.takeIf { it > 0 }?.let { parameter("o", it) }
      }

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

  suspend fun fetchPostSearchBody(
      query: String,
      offset: Int = 0,
      tag: String? = null,
      service: String? = null,
  ): String =
      gateway.getText(
          "/posts",
          "搜索Posts(queryLength=${query.length},offset=$offset,tag=$tag,service=$service)",
      ) {
        if (query.isNotBlank()) parameter("q", query)
        offset.takeIf { it > 0 }?.let { parameter("o", it) }
        tag?.let { parameter("tag", it) }
        service?.let { parameter("service", it) }
      }

  fun parsePostCards(body: String): List<Post> {
    val doc = Ksoup.parse(body)
    return doc.select(".post-card").mapNotNull { it.toPostCard() }
  }

  fun parsePostCardsPage(body: String, offset: Int = 0): PagedResult<Post> =
      PagedResult(items = parsePostCards(body), pageInfo = parsePageInfo(body, offset))

  suspend fun searchPostsPage(
      query: String,
      offset: Int = 0,
      tag: String? = null,
      service: String? = null,
  ): PagedResult<Post> =
      parsePostCardsPage(fetchPostSearchBody(query, offset, tag, service), offset)

  suspend fun fetchDmsBody(
      query: String = "",
      offset: Int = 0,
  ): String =
      gateway.getText(
          "/dms",
          "请求DMs(queryLength=${query.length},offset=$offset)",
      ) {
        if (query.isNotBlank()) parameter("q", query)
        offset.takeIf { it > 0 }?.let { parameter("o", it) }
      }

  fun parseDms(body: String): List<DM> {
    val doc = Ksoup.parse(body)
    if (doc.selectFirst(".no-results") != null) return emptyList()
    return doc.select(".dm-card, article.dm, .card-list__items article").mapNotNull { element ->
      val userHref = element.selectFirst(".dms__user-link[href]")?.attr("href").orEmpty()
      val pathParts = userHref.substringBefore('?').split('/').filter { it.isNotBlank() }
      val service = element.attr("data-service").ifBlankOrNull() ?: pathParts.getOrNull(0)
      val user = element.attr("data-user").ifBlankOrNull() ?: pathParts.valueAfter("user")
      val artistName = element.selectFirst(".dm-card__user")?.text()?.trim()?.ifBlankOrNull()
      val content =
          element.selectFirst(".dm-card__content, .dm__content, .card__content")?.html()
              ?: element.text().takeIf { it.isNotBlank() }
      content?.let {
        DM(
            id = element.attr("data-id").ifBlank { null },
            service = service,
            user = user,
            content = it,
            hash = element.attr("data-hash").ifBlankOrNull() ?: stableDmContentHash(it),
            added =
                element.selectFirst("time")?.attr("datetime")?.ifBlankOrNull()
                    ?: element
                        .selectFirst(".dm-card__added")
                        ?.text()
                        ?.substringAfter("Published:", "")
                        ?.trim()
                        ?.ifBlankOrNull(),
            artist =
                if (service != null && user != null && artistName != null) {
                  Creator(id = user, service = service, name = artistName)
                } else {
                  null
                },
        )
      }
    }
  }

  fun parseDmsPage(body: String, offset: Int = 0): PagedResult<DM> =
      PagedResult(items = parseDms(body), pageInfo = parsePageInfo(body, offset))

  suspend fun searchDMsPage(
      query: String = "",
      offset: Int = 0,
  ): PagedResult<DM> = parseDmsPage(fetchDmsBody(query, offset), offset)

  suspend fun fetchTagsBody(): String = gateway.getText("/posts/tags", "请求Tags")

  fun parseTags(body: String): List<Tag> {
    val doc = Ksoup.parse(body)
    return doc.select("#tag-container article a").mapNotNull { link ->
      val spans = link.select("span")
      val name = spans.getOrNull(0)?.text()?.trim().orEmpty()
      if (name.isBlank()) null
      else Tag(name, spans.getOrNull(1)?.text()?.trim()?.toIntOrNull() ?: 0)
    }
  }

  suspend fun fetchCreatorPostsPageBody(
      service: String,
      creatorId: String,
      offset: Int = 0,
  ): String =
      gateway.getText(
          "/${service.encodeURLPathPart()}/user/${creatorId.encodeURLPathPart()}",
          "请求Creator Posts分页(service=$service,creator=$creatorId,offset=$offset)",
      ) {
        offset.takeIf { it > 0 }?.let { parameter("o", it) }
      }

  suspend fun fetchPostBody(
      service: String,
      creatorId: String,
      postId: String,
  ): String =
      gateway.getText(
          "/api/v1/${service.encodeURLPathPart()}/user/${creatorId.encodeURLPathPart()}/post/${postId.encodeURLPathPart()}",
          "请求Post详情(service=$service,creator=$creatorId,post=$postId)",
      )

  fun parsePost(body: String): Post = json.decodeFromString(body)

  suspend fun getPostComments(
      service: String,
      creatorId: String,
      postId: String,
  ): List<Comment> {
    val label = "请求Post评论(service=$service,creator=$creatorId,post=$postId)"
    val body =
        try {
          gateway.getText(
              "/api/v1/${service.encodeURLPathPart()}/user/${creatorId.encodeURLPathPart()}/post/${postId.encodeURLPathPart()}/comments",
              label,
          )
        } catch (error: PawchiveApiException) {
          if (error.statusCode == 404) return emptyList()
          throw error
        }
    return decode(body)
  }

  suspend fun getCreatorAnnouncements(
      service: String,
      creatorId: String,
  ): List<Announcement> =
      decode(
          gateway.getText(
              "/api/v1/${service.encodeURLPathPart()}/user/${creatorId.encodeURLPathPart()}/announcements",
              "请求Creator公告(service=$service,creator=$creatorId)",
          )
      )

  suspend fun fetchCreatorTagsBody(
      service: String,
      creatorId: String,
  ): String =
      gateway.getText(
          "/${service.encodeURLPathPart()}/user/${creatorId.encodeURLPathPart()}/tags",
          "请求Creator Tags(service=$service,creator=$creatorId)",
      )

  fun parseCreatorTags(body: String): List<Tag> = parseTags(body)

  suspend fun getCreatorLinks(
      service: String,
      creatorId: String,
  ): List<Creator> =
      decode(
          gateway.getText(
              "/api/v1/${service.encodeURLPathPart()}/user/${creatorId.encodeURLPathPart()}/links",
              "请求Creator Links(service=$service,creator=$creatorId)",
          )
      )

  suspend fun getFavorites(type: String): List<Creator> =
      decode(
          gateway.getText(
              "/api/v1/account/favorites",
              "请求Favorites(type=$type)",
              authenticated = true,
          ) {
            parameter("type", type)
          }
      )

  suspend fun getFavoritePosts(): List<Post> =
      decode(
          gateway.getText(
              "/api/v1/account/favorites",
              "请求Favorites(type=post)",
              authenticated = true,
          ) {
            parameter("type", "post")
          }
      )

  suspend fun addFavoriteCreator(service: String, creatorId: String) =
      setCreatorFavorite(service, creatorId, favorite = true)

  suspend fun removeFavoriteCreator(service: String, creatorId: String) =
      setCreatorFavorite(service, creatorId, favorite = false)

  private suspend fun setCreatorFavorite(service: String, creatorId: String, favorite: Boolean) {
    val path =
        "/api/v1/favorites/creator/${service.encodeURLPathPart()}/${creatorId.encodeURLPathPart()}"
    if (favorite) gateway.postUnit(path, "收藏Creator -> 添加", authenticated = true)
    else gateway.deleteUnit(path, "收藏Creator -> 移除", authenticated = true)
  }

  suspend fun addFavoritePost(service: String, creatorId: String, postId: String) =
      setPostFavorite(service, creatorId, postId, favorite = true)

  suspend fun removeFavoritePost(service: String, creatorId: String, postId: String) =
      setPostFavorite(service, creatorId, postId, favorite = false)

  private suspend fun setPostFavorite(
      service: String,
      creatorId: String,
      postId: String,
      favorite: Boolean,
  ) {
    val path =
        "/api/v1/favorites/post/${service.encodeURLPathPart()}/${creatorId.encodeURLPathPart()}/${postId.encodeURLPathPart()}"
    if (favorite) gateway.postUnit(path, "收藏Post -> 添加", authenticated = true)
    else gateway.deleteUnit(path, "收藏Post -> 移除", authenticated = true)
  }

  suspend fun flagPost(service: String, creatorId: String, postId: String) {
    gateway.postUnit(
        "/api/v1/${service.encodeURLPathPart()}/user/${creatorId.encodeURLPathPart()}/post/${postId.encodeURLPathPart()}/flag",
        "Flag Post",
        authenticated = hasSession(),
    )
  }

  fun logout() = gateway.clearSession()
}
