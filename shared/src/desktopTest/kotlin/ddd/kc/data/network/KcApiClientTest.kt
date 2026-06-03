package ddd.kc.data.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ddd.kc.data.model.Platform
import ddd.kc.data.settings.AppSettings
import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

class KcApiClientTest {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
  }

  private fun settings(): AppSettings {
    val file = File.createTempFile("kc-test-settings", ".preferences_pb")
    file.delete()
    return AppSettings(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { file.absolutePath.toPath() },
        )
    )
  }

  private fun client(
      handler: (HttpRequestData) -> Pair<HttpStatusCode, String>,
  ): Pair<KcApiClient, List<HttpRequestData>> {
    val requests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
      requests += request
      val (status, body) = handler(request)
      respond(
          content = body,
          status = status,
          headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
      )
    }
    val httpClient =
        HttpClient(engine) {
          install(ContentNegotiation) {
            json(json)
            json(json, contentType = ContentType("text", "css"))
          }
        }
    val sessionStore =
        KcSessionStore(
            KSafe(fileName = "kc_test_${UUID.randomUUID().toString().replace("-", "_")}")
        )
    sessionStore.saveSession(Platform.KEMONO, "test_session")
    return KcApiClient(httpClient, settings(), sessionStore) to requests
  }

  @Test
  fun usesCurrentPublicEndpointsAndResponseShapes() = runBlocking {
    val (api, requests) =
        client { request ->
          HttpStatusCode.OK to
              when (request.url.encodedPath) {
                "/api/v1/creators" -> creatorsJson
                "/api/v1/posts" -> postsPageJson
                "/api/v1/posts/popular" -> popularPostsJson
                "/api/v1/posts/tags" -> tagsJson
                "/api/v1/dms" -> dmsJson
                "/api/v1/patreon/user/artist/posts" -> creatorPostsJson
                "/api/v1/patreon/user/artist/post/post1" -> postDetailJson
                "/api/v1/patreon/user/artist/post/post1/comments" -> commentsJson
                "/api/v1/patreon/user/artist/announcements" -> announcementsJson
                "/api/v1/patreon/user/artist/dms" -> creatorDmsJson
                "/api/v1/patreon/user/artist/tags" -> tagsJson
                "/api/v1/account/favorites" ->
                    if (request.url.parameters["type"] == "post") favoritePostsJson
                    else favoriteCreatorsJson
                "/api/v1/favorites/creator/patreon/artist" -> "{}"
                "/api/v1/favorites/post/patreon/artist/post1" -> "{}"
                "/api/v1/authentication/login" -> "{}"
                "/api/v1/authentication/logout" -> "{}"
                else -> error("Unexpected path ${request.url.encodedPath}")
              }
        }

    assertEquals(2, api.getCreators(Platform.KEMONO).size)
    assertEquals(listOf("post1"), api.getRecentPosts(Platform.KEMONO, 50).map { it.id })
    assertEquals(listOf("post1"), api.searchPosts(Platform.KEMONO, "art", 0).map { it.id })
    assertEquals(listOf("post1"), api.getPostsByTag(Platform.KEMONO, "wip", 0).map { it.id })
    assertEquals(listOf("post1"), api.getPopularPosts(Platform.KEMONO).map { it.id })
    assertEquals(listOf("wip"), api.getTags(Platform.KEMONO).map { it.tag })
    assertEquals(listOf("dm1"), api.getRecentDMs(Platform.KEMONO, 0).map { it.id })
    assertEquals(listOf("dm1"), api.searchDMs(Platform.KEMONO, "hello", 0).map { it.id })
    assertEquals(
        listOf("post1"),
        api.getCreatorPosts(Platform.KEMONO, "patreon", "artist", 0).map { it.id },
    )
    val detail = api.getPost(Platform.KEMONO, "patreon", "artist", "post1")
    assertEquals("post1", detail.id)
    assertEquals("detail text", detail.content)
    assertEquals(listOf("detail.jpg"), detail.attachments.map { it.name })
    assertEquals(
        listOf("comment1"),
        api.getPostComments(Platform.KEMONO, "patreon", "artist", "post1").map { it.id },
    )
    assertEquals(
        listOf("ann1"),
        api.getCreatorAnnouncements(Platform.KEMONO, "patreon", "artist").map { it.hash },
    )
    assertEquals(
        listOf("dm1"),
        api.getCreatorDMs(Platform.KEMONO, "patreon", "artist").map { it.id },
    )
    assertEquals(
        listOf("wip"),
        api.getCreatorTags(Platform.KEMONO, "patreon", "artist").map { it.tag },
    )
    assertEquals(listOf("artist"), api.getFavorites(Platform.KEMONO, "artist").map { it.id })
    assertEquals(listOf("post1"), api.getFavoritePosts(Platform.KEMONO).map { it.id })
    api.addFavoriteCreator(Platform.KEMONO, "patreon", "artist")
    api.removeFavoriteCreator(Platform.KEMONO, "patreon", "artist")
    api.addFavoritePost(Platform.KEMONO, "patreon", "artist", "post1")
    api.removeFavoritePost(Platform.KEMONO, "patreon", "artist", "post1")
    api.login(Platform.KEMONO, "user", "password")
    api.logout(Platform.KEMONO)

    val paths = requests.map { it.url.encodedPath }
    assertFalse(paths.any { it == "/api/v1/creators.txt" })
    assertFalse(paths.any { it == "/api/v1/posts/search" })
    assertFalse(paths.any { it == "/api/v1/tags/search" })
    assertTrue(
        requests.any { it.url.encodedPath == "/api/v1/posts" && it.url.parameters["q"] == "art" }
    )
    assertTrue(
        requests.any { it.url.encodedPath == "/api/v1/posts" && it.url.parameters["tag"] == "wip" }
    )
    assertTrue(
        requests.any {
          it.url.encodedPath == "/api/v1/account/favorites" && it.url.parameters["type"] == "artist"
        }
    )
    assertTrue(
        requests.any {
          it.url.encodedPath == "/api/v1/account/favorites" && it.url.parameters["type"] == "post"
        }
    )
    assertTrue(
        requests
            .filter {
              it.url.encodedPath.startsWith("/api/v1/account/") ||
                  it.url.encodedPath.startsWith("/api/v1/favorites/") ||
                  it.url.encodedPath == "/api/v1/authentication/logout"
            }
            .all { it.headers[HttpHeaders.Cookie] == "session=test_session" }
    )
    assertTrue(
        requests
            .filter {
              it.url.encodedPath == "/api/v1/posts" ||
                  it.url.encodedPath == "/api/v1/dms" ||
                  it.url.encodedPath == "/api/v1/creators" ||
                  it.url.encodedPath == "/api/v1/patreon/user/artist/post/post1"
            }
            .all { it.headers[HttpHeaders.Cookie] == null }
    )
  }

  @Test
  fun favorites401BecomesAuthRequired() = runBlocking {
    val (api, _) = client { HttpStatusCode.Unauthorized to "{}" }

    assertFailsWith<AuthRequiredException> { api.getFavorites(Platform.KEMONO, "artist") }
    Unit
  }

  @Test
  fun ddosGuard403BecomesDomainException() = runBlocking {
    val (api, _) =
        client {
          HttpStatusCode.Forbidden to
              """If you want to scrape, use "Accept: text/css" header in your requests for now."""
        }

    assertFailsWith<KcDdosGuardException> { api.getRecentDMs(Platform.KEMONO, 0) }
    Unit
  }

  @Test
  fun liveApiSmokeTestWhenEnabled() = runBlocking {
    if (System.getenv("KC_LIVE_API_TESTS") != "true") return@runBlocking
    val httpClient =
        HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
          install(ContentNegotiation) {
            json(json)
            json(json, contentType = ContentType("text", "css"))
          }
          defaultRequest {
            headers[HttpHeaders.Accept] = "text/css"
            headers[HttpHeaders.UserAgent] = "Mozilla/5.0 (compatible; KC/1.0)"
          }
        }

    suspend fun assertLive(url: String) {
      val response = httpClient.get(url)
      assertEquals(
          HttpStatusCode.OK,
          response.status,
          "$url body=${response.bodyAsText().take(200)}",
      )
    }

    suspend fun assertLiveAuthEndpointNotDdosGuard(url: String) {
      val response = httpClient.get(url)
      assertTrue(
          response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Unauthorized,
          "$url status=${response.status} body=${response.bodyAsText().take(200)}",
      )
    }

    listOf("https://kemono.cr/api", "https://coomer.st/api").forEach { base ->
      assertLive("$base/v1/creators")
      assertLive("$base/v1/posts")
      assertLive("$base/v1/posts?q=art&o=0")
      assertLive("$base/v1/posts/popular")
      assertLive("$base/v1/posts/tags")
      assertLive("$base/v1/dms")
      assertLiveAuthEndpointNotDdosGuard("$base/v1/account/favorites?type=post")
    }
    assertLive("https://kemono.cr/api/v1/patreon/user/728497/posts?o=0")
    assertLive("https://kemono.cr/api/v1/patreon/user/728497/post/61926225")
    assertLive("https://kemono.cr/api/v1/patreon/user/728497/post/61926225/comments")
  }

  private companion object {
    const val creatorsJson =
        """[
          {"id":"artist","name":"Artist One","service":"patreon","indexed":10,"updated":20,"favorited":5,"public_id":"artist_one"},
          {"id":"fan","name":"Fan Creator","service":"fanbox","indexed":30,"updated":10,"favorited":9}
        ]"""
    const val postsPageJson =
        """{"count":1,"true_count":1,"posts":[{"id":"post1","user":"artist","service":"patreon","title":"Post","file":{"name":"a.jpg","path":"/a.jpg"},"attachments":[]}]}"""
    const val popularPostsJson =
        """{"info":{},"props":{"today":"2026-06-01","count":1},"posts":[{"id":"post1","user":"artist","service":"patreon","title":"Popular"}]}"""
    const val creatorPostsJson =
        """[{"id":"post1","user":"artist","service":"patreon","title":"Creator Post"}]"""
    const val postDetailJson =
        """{"post":{"id":"post1","user":"artist","service":"patreon","title":"Detail","content":"detail text"},"attachments":[{"name":"detail.jpg","path":"/detail.jpg"}],"previews":[],"props":{"flagged":false,"revisions":[]}}"""
    const val tagsJson = """[{"tag":"wip","post_count":2}]"""
    const val dmsJson =
        """{"props":{"currentPage":"artists","count":1,"limit":50,"dms":[{"id":"dm1","service":"patreon","user":"artist","content":"hello"}]},"base":{}}"""
    const val creatorDmsJson =
        """[{"id":"dm1","service":"patreon","user":"artist","content":"hello"}]"""
    const val commentsJson = """[{"id":"comment1","content":"ok","commenter":"reader"}]"""
    const val announcementsJson =
        """[{"service":"patreon","user_id":"artist","hash":"ann1","content":"notice"}]"""
    const val favoriteCreatorsJson =
        """[{"id":"artist","name":"Artist One","service":"patreon","indexed":"2026-06-01T00:00:00","updated":"2026-06-01T00:00:00","favorited":5}]"""
    const val favoritePostsJson =
        """[{"id":"post1","artist_id":"artist","user":"artist","service":"patreon","title":"Favorite Post"}]"""
  }
}
