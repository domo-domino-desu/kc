package ddd.kc.data.network

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
import ddd.kc.util.logging.KcLog
import ddd.kc.util.logging.summarizePost
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val log = KcLog.withTag("KcApi")

@Serializable private data class PostsPage(val posts: List<Post> = emptyList())

@Serializable
data class PopularPage(
    val props: PopularProps = PopularProps(),
    val info: PopularInfo = PopularInfo(),
    val posts: List<Post> = emptyList(),
)

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

@Serializable
private data class DmsPage(val props: DmsProps = DmsProps()) {
  @Serializable data class DmsProps(val dms: List<DM> = emptyList())
}

@Serializable
private data class PostDetail(
    val post: Post,
    val attachments: List<PostFile> = emptyList(),
)

@Serializable private data class LoginRequest(val username: String, val password: String)

class KcApiClient(
    private val client: HttpClient,
    private val settings: AppSettings,
    private val sessionStore: KcSessionStore,
    private val json: Json,
) {

  private fun Platform.url() = settings.baseUrl(this)

  fun hasSession(platform: Platform): Boolean = sessionStore.hasSession(platform)

  private fun sessionCookie(platform: Platform): String {
    val session = sessionStore.getSession(platform) ?: throw AuthRequiredException()
    return "session=$session"
  }

  private fun saveSessionFromResponse(platform: Platform, response: HttpResponse) {
    val cookies = response.headers.getAll(HttpHeaders.SetCookie)
    log.d { "登录会话 -> 响应Cookie数量(platform=${platform.name},count=${cookies?.size ?: 0})" }
    val session =
        cookies?.firstNotNullOfOrNull { cookie ->
          cookie
              .substringAfter("session=", missingDelimiterValue = "")
              .substringBefore(";")
              .takeIf { it.isNotBlank() }
        }
    if (session != null) {
      sessionStore.saveSession(platform, session)
      log.i { "登录会话 -> 已保存(platform=${platform.name},sessionLength=${session.length})" }
    } else {
      log.w {
        "登录会话 -> 未找到session cookie(platform=${platform.name},cookies=${cookies?.joinToString()})"
      }
    }
  }

  private suspend inline fun <reified T> decode(response: HttpResponse, label: String): T {
    if (response.status == HttpStatusCode.Unauthorized) {
      log.w { "$label -> 需要登录" }
      throw AuthRequiredException()
    }
    if (!response.status.isSuccess()) {
      val text = runCatching { response.bodyAsText() }.getOrDefault("")
      if (response.status == HttpStatusCode.Forbidden && text.contains("Accept: text/css")) {
        log.w { "$label -> 被站点拦截(status=${response.status.value},bodySnippet=${text.take(200)})" }
        throw KcDdosGuardException()
      }
      log.w { "$label -> 失败(status=${response.status.value},bodyLength=${text.length})" }
      throw KcApiException(response.status.value, "请求失败：HTTP ${response.status.value}")
    }
    log.d { "$label -> 成功(status=${response.status.value})" }
    @Suppress("UNCHECKED_CAST") if (T::class == Unit::class) return Unit as T
    return json.decodeFromString<T>(response.bodyAsText())
  }

  suspend fun getCreators(platform: Platform): List<Creator> =
      decode<List<Creator>>(
              client.get("${platform.url()}/api/v1/creators"),
              "请求Creators(platform=${platform.name})",
          )
          .also { log.i { "请求Creators -> 成功(platform=${platform.name},count=${it.size})" } }

  suspend fun getRecentPosts(platform: Platform, offset: Int = 0): List<Post> =
      decode<PostsPage>(
              client.get("${platform.url()}/api/v1/posts") { parameter("o", offset) },
              "请求最近Posts(platform=${platform.name},offset=$offset)",
          )
          .posts
          .also {
            log.i { "请求最近Posts -> 成功(platform=${platform.name},offset=$offset,count=${it.size})" }
          }

  suspend fun getPopularPosts(
      platform: Platform,
      date: String? = null,
      period: String? = null,
      offset: Int? = null,
  ): PopularPage =
      decode<PopularPage>(
              client.get("${platform.url()}/api/v1/posts/popular") {
                date?.let { parameter("date", it) }
                period?.let { parameter("period", it) }
                offset?.takeIf { it > 0 }?.let { parameter("o", it) }
              },
              "请求热门Posts(platform=${platform.name},date=$date,period=$period,offset=$offset)",
          )
          .also { log.i { "请求热门Posts -> 成功(platform=${platform.name},count=${it.posts.size})" } }

  suspend fun searchPosts(
      platform: Platform,
      query: String,
      offset: Int = 0,
      tag: String? = null,
      service: String? = null,
  ): List<Post> =
      decode<PostsPage>(
              client.get("${platform.url()}/api/v1/posts") {
                parameter("q", query)
                parameter("o", offset)
                tag?.let { parameter("tag", it) }
                service?.let { parameter("service", it) }
              },
              "搜索Posts(platform=${platform.name},queryLength=${query.length},offset=$offset,tag=$tag,service=$service)",
          )
          .posts
          .also {
            log.i { "搜索Posts -> 成功(platform=${platform.name},offset=$offset,count=${it.size})" }
          }

  suspend fun getPostsByTag(platform: Platform, tag: String, offset: Int = 0): List<Post> =
      decode<PostsPage>(
              client.get("${platform.url()}/api/v1/posts") {
                parameter("tag", tag)
                parameter("o", offset)
              },
              "请求Tag Posts(platform=${platform.name},tagLength=${tag.length},offset=$offset)",
          )
          .posts
          .also {
            log.i { "请求Tag Posts -> 成功(platform=${platform.name},offset=$offset,count=${it.size})" }
          }

  suspend fun getRecentDMs(platform: Platform, offset: Int = 0): List<DM> =
      decode<DmsPage>(
              client.get("${platform.url()}/api/v1/dms") { parameter("o", offset) },
              "请求最近DMs(platform=${platform.name},offset=$offset)",
          )
          .props
          .dms
          .also {
            log.i { "请求最近DMs -> 成功(platform=${platform.name},offset=$offset,count=${it.size})" }
          }

  suspend fun searchDMs(platform: Platform, query: String, offset: Int = 0): List<DM> =
      decode<DmsPage>(
              client.get("${platform.url()}/api/v1/dms") {
                parameter("q", query)
                parameter("o", offset)
              },
              "搜索DMs(platform=${platform.name},queryLength=${query.length},offset=$offset)",
          )
          .props
          .dms
          .also {
            log.i { "搜索DMs -> 成功(platform=${platform.name},offset=$offset,count=${it.size})" }
          }

  suspend fun getTags(platform: Platform): List<Tag> =
      decode<List<Tag>>(
              client.get("${platform.url()}/api/v1/posts/tags"),
              "请求Tags(platform=${platform.name})",
          )
          .also { log.i { "请求Tags -> 成功(platform=${platform.name},count=${it.size})" } }

  suspend fun getCreatorPosts(
      platform: Platform,
      service: String,
      creatorId: String,
      offset: Int = 0,
  ): List<Post> =
      decode<List<Post>>(
              client.get("${platform.url()}/api/v1/$service/user/$creatorId/posts") {
                parameter("o", offset)
              },
              "请求Creator Posts(platform=${platform.name},service=$service,creator=$creatorId,offset=$offset)",
          )
          .also {
            log.i {
              "请求Creator Posts -> 成功(platform=${platform.name},service=$service,creator=$creatorId,offset=$offset,count=${it.size})"
            }
          }

  suspend fun getPost(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ): Post =
      decode<PostDetail>(
              client.get("${platform.url()}/api/v1/$service/user/$creatorId/post/$postId"),
              "请求Post详情(platform=${platform.name},service=$service,creator=$creatorId,post=$postId)",
          )
          .let { detail ->
            val merged =
                if (detail.post.attachments.isEmpty() && detail.attachments.isNotEmpty()) {
                  detail.post.copy(attachments = detail.attachments)
                } else {
                  detail.post
                }
            log.i { "请求Post详情 -> 成功(platform=${platform.name},${summarizePost(merged)})" }
            merged
          }

  suspend fun getPostComments(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ): List<Comment> =
      decode<List<Comment>>(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId/post/$postId/comments"),
          "请求Post评论(platform=${platform.name},service=$service,creator=$creatorId,post=$postId)",
      )

  suspend fun getCreatorAnnouncements(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Announcement> =
      decode<List<Announcement>>(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId/announcements"),
          "请求Creator公告(platform=${platform.name},service=$service,creator=$creatorId)",
      )

  suspend fun getCreatorDMs(platform: Platform, service: String, creatorId: String): List<DM> =
      decode<List<DM>>(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId/dms"),
          "请求Creator DMs(platform=${platform.name},service=$service,creator=$creatorId)",
      )

  suspend fun getCreatorTags(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Tag> =
      decode<List<Tag>>(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId/tags"),
          "请求Creator Tags(platform=${platform.name},service=$service,creator=$creatorId)",
      )

  suspend fun getCreatorLinks(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Creator> =
      decode<List<Creator>>(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId/links"),
          "请求Creator Links(platform=${platform.name},service=$service,creator=$creatorId)",
      )

  suspend fun getRecommendedCreators(
      platform: Platform,
      service: String,
      creatorId: String,
  ): List<Creator> =
      decode<List<Creator>>(
          client.get("${platform.url()}/api/v1/$service/user/$creatorId/recommended"),
          "请求Recommended Creators(platform=${platform.name},service=$service,creator=$creatorId)",
      )

  suspend fun getFavorites(platform: Platform, type: String): List<Creator> =
      decode<List<Creator>>(
              client.get("${platform.url()}/api/v1/account/favorites") {
                parameter("type", type)
                header(HttpHeaders.Cookie, sessionCookie(platform))
              },
              "请求Favorites(platform=${platform.name},type=$type)",
          )
          .also {
            log.i { "请求Favorites -> 成功(platform=${platform.name},type=$type,count=${it.size})" }
          }

  suspend fun getFavoritePosts(platform: Platform): List<Post> =
      decode<List<Post>>(
              client.get("${platform.url()}/api/v1/account/favorites") {
                parameter("type", "post")
                header(HttpHeaders.Cookie, sessionCookie(platform))
              },
              "请求Favorites(platform=${platform.name},type=post)",
          )
          .also {
            log.i { "请求Favorites -> 成功(platform=${platform.name},type=post,count=${it.size})" }
          }

  suspend fun addFavoriteCreator(platform: Platform, service: String, creatorId: String) {
    decode<Unit>(
        client.post("${platform.url()}/api/v1/favorites/creator/$service/$creatorId") {
          header(HttpHeaders.Cookie, sessionCookie(platform))
        },
        "收藏Creator -> 添加(platform=${platform.name},service=$service,creator=$creatorId)",
    )
  }

  suspend fun removeFavoriteCreator(platform: Platform, service: String, creatorId: String) {
    decode<Unit>(
        client.delete("${platform.url()}/api/v1/favorites/creator/$service/$creatorId") {
          header(HttpHeaders.Cookie, sessionCookie(platform))
        },
        "收藏Creator -> 移除(platform=${platform.name},service=$service,creator=$creatorId)",
    )
  }

  suspend fun addFavoritePost(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ) {
    decode<Unit>(
        client.post("${platform.url()}/api/v1/favorites/post/$service/$creatorId/$postId") {
          header(HttpHeaders.Cookie, sessionCookie(platform))
        },
        "收藏Post -> 添加(platform=${platform.name},service=$service,creator=$creatorId,post=$postId)",
    )
  }

  suspend fun removeFavoritePost(
      platform: Platform,
      service: String,
      creatorId: String,
      postId: String,
  ) {
    decode<Unit>(
        client.delete("${platform.url()}/api/v1/favorites/post/$service/$creatorId/$postId") {
          header(HttpHeaders.Cookie, sessionCookie(platform))
        },
        "收藏Post -> 移除(platform=${platform.name},service=$service,creator=$creatorId,post=$postId)",
    )
  }

  suspend fun login(platform: Platform, username: String, password: String) {
    val response =
        client.post("${platform.url()}/api/v1/authentication/login") {
          headers[HttpHeaders.ContentType] = ContentType.Application.Json.toString()
          setBody(json.encodeToString(LoginRequest(username = username, password = password)))
        }
    decode<Unit>(response, "登录(platform=${platform.name},usernameLength=${username.length})")
    saveSessionFromResponse(platform, response)
    log.i { "登录 -> 会话状态验证(platform=${platform.name},hasSession=${hasSession(platform)})" }
  }

  suspend fun logout(platform: Platform) {
    decode<Unit>(
        client.post("${platform.url()}/api/v1/authentication/logout") {
          header(HttpHeaders.Cookie, sessionCookie(platform))
        },
        "退出登录(platform=${platform.name})",
    )
    sessionStore.clearSession(platform)
    log.i { "退出登录 -> 已清理会话(platform=${platform.name})" }
  }

  suspend fun getDiscordChannels(platform: Platform, serverId: String): List<DiscordChannel> =
      decode<List<DiscordChannel>>(
              client.get("${platform.url()}/api/v1/discord/channel/lookup/$serverId"),
              "请求Discord频道列表(platform=${platform.name},server=$serverId)",
          )
          .also {
            log.i {
              "请求Discord频道列表 -> 成功(platform=${platform.name},server=$serverId,count=${it.size})"
            }
          }

  suspend fun getDiscordChannelPosts(
      platform: Platform,
      channelId: String,
      offset: Int = 0,
  ): List<DiscordPost> =
      decode<List<DiscordPost>>(
              client.get("${platform.url()}/api/v1/discord/channel/$channelId") {
                parameter("o", offset)
              },
              "请求Discord频道消息(platform=${platform.name},channel=$channelId,offset=$offset)",
          )
          .also {
            log.i {
              "请求Discord频道消息 -> 成功(platform=${platform.name},channel=$channelId,offset=$offset,count=${it.size})"
            }
          }
}
