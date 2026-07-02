package ddd.kc.data.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ddd.kc.data.model.Platform
import ddd.kc.data.model.PostFile
import ddd.kc.data.model.allFiles
import ddd.kc.data.model.fullUrl
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.data.settings.AppSettings
import ddd.kc.fake.TestFixtures
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
        PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() })
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
          headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
      )
    }
    val httpClient = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
    val sessionStore =
        KcSessionStore(
            KSafe(fileName = "kc_test_${UUID.randomUUID().toString().replace("-", "_")}")
        )
    sessionStore.saveSession(Platform.PAWCHIVE, "session=test_session; path=/")
    return KcApiClient(httpClient, settings(), sessionStore, json) to requests
  }

  @Test
  fun usesPawchiveJsonAndHtmlEndpoints() = runBlocking {
    val (api, requests) =
        client { request ->
          HttpStatusCode.OK to
              when (request.url.encodedPath) {
                "/api/v1/creators" -> creatorsJson
                "/api/v1/posts" -> postsJson
                "/posts" -> TestFixtures.read("pawchive.st:posts:service-patreon.html")
                "/posts/popular" -> TestFixtures.read("pawchive.st:posts:popular.html")
                "/posts/tags" -> TestFixtures.read("pawchive.st:posts:tags.html")
                "/dms" -> TestFixtures.read("pawchive.st:dms:search-test.html")
                "/api/v1/patreon/user/artist" -> creatorPostsJson
                "/patreon/user/artist" -> TestFixtures.read("pawchive.st:patreon:user:3295915.html")
                "/api/v1/patreon/user/artist/post/post1" -> postDetailJson
                "/api/v1/patreon/user/artist/post/post1/comments" -> commentsJson
                "/api/v1/patreon/user/artist/announcements" -> announcementsJson
                "/patreon/user/artist/tags" ->
                    TestFixtures.read("pawchive.st:patreon:user:3295915:tags.html")
                "/api/v1/account/favorites" ->
                    if (request.url.parameters["type"] == "post") favoritePostsJson
                    else favoriteCreatorsJson
                "/api/v1/favorites/creator/patreon/artist" -> "{}"
                "/api/v1/favorites/post/patreon/artist/post1" -> "{}"
                else -> error("Unexpected path ${request.url.encodedPath}")
              }
        }

    assertEquals(2, api.getCreators().size)
    assertEquals(listOf("post1"), api.getRecentPosts(offset = 50).map { it.id })
    assertTrue(api.searchPosts(query = "art", offset = 0).isNotEmpty())
    assertTrue(api.getPostsByTag(tag = "nsfw", offset = 0).isNotEmpty())
    assertTrue(api.getPopularPosts().posts.isNotEmpty())
    assertTrue(api.getTags().any { it.tag == "nsfw" && it.count > 0 })
    assertEquals(emptyList(), api.getRecentDMs())
    assertEquals(
        listOf("post1"),
        api.getCreatorPosts(Platform.PAWCHIVE, "patreon", "artist", 0).map { it.id },
    )
    assertEquals(
        listOf("Animation", "Chainsaw-Man", "Quoted Tag"),
        api.getCreatorPosts(Platform.PAWCHIVE, "patreon", "artist", 0).first().tags,
    )
    assertEquals(
        4,
        api.getCreatorPostsPageInfo(Platform.PAWCHIVE, "patreon", "artist", 0)?.lastPage,
    )
    val detail = api.getPost(Platform.PAWCHIVE, "patreon", "artist", "post1")
    assertEquals("post1", detail.id)
    assertEquals("detail text", detail.content)
    assertEquals(listOf("Tomoe Umari", "Vtuber", "winner"), detail.tags)
    assertEquals(listOf("detail.jpg"), detail.attachments.map { it.name })
    assertEquals(
        listOf("comment1"),
        api.getPostComments(Platform.PAWCHIVE, "patreon", "artist", "post1").map { it.id },
    )
    assertEquals(
        listOf("ann1"),
        api.getCreatorAnnouncements(Platform.PAWCHIVE, "patreon", "artist").map { it.hash },
    )
    assertEquals(
        true,
        api.getCreatorTags(Platform.PAWCHIVE, "patreon", "artist").any { it.tag == "Animation" },
    )
    assertEquals(listOf("artist"), api.getFavorites(type = "artist").map { it.id })
    assertEquals(listOf("post1"), api.getFavoritePosts().map { it.id })
    api.addFavoriteCreator(service = "patreon", creatorId = "artist")
    api.removeFavoriteCreator(service = "patreon", creatorId = "artist")
    api.addFavoritePost(service = "patreon", creatorId = "artist", postId = "post1")
    api.removeFavoritePost(service = "patreon", creatorId = "artist", postId = "post1")

    val paths = requests.map { it.url.encodedPath }
    assertTrue(paths.contains("/api/v1/patreon/user/artist"))
    assertTrue(paths.contains("/posts"))
    assertTrue(paths.contains("/posts/popular"))
    assertTrue(paths.contains("/posts/tags"))
    assertTrue(paths.contains("/dms"))
    assertTrue(
        requests
            .filter {
              it.url.encodedPath.startsWith("/api/v1/account/") ||
                  it.url.encodedPath.startsWith("/api/v1/favorites/")
            }
            .all { it.headers[HttpHeaders.Cookie] == "session=test_session" }
    )
  }

  @Test
  fun favorites401BecomesAuthRequired() = runBlocking {
    val (api, _) = client { HttpStatusCode.Unauthorized to "{}" }
    assertFailsWith<AuthRequiredException> { api.getFavorites(type = "artist") }
    Unit
  }

  @Test
  fun comments404BecomesEmptyList() = runBlocking {
    val (api, _) = client { HttpStatusCode.NotFound to """{"error":"not found"}""" }
    assertEquals(emptyList(), api.getPostComments(Platform.PAWCHIVE, "fanbox", "artist", "post1"))
  }

  @Test
  fun postFileUrlsUseFileAndImageSubdomains() {
    val file =
        PostFile(
            name = "5gTzmuRAtZ6PkriYNyM6sZZI.jpeg",
            path = "/6c/15/6c1582a2125bb308c8226ff469f22d1343f9de8881a2028295f6fe9585e7eeb1.jpeg",
        )

    assertEquals(
        "https://file.pawchive.st/data/6c/15/6c1582a2125bb308c8226ff469f22d1343f9de8881a2028295f6fe9585e7eeb1.jpeg?f=5gTzmuRAtZ6PkriYNyM6sZZI.jpeg",
        file.fullUrl("https://img.pawchive.st"),
    )
    assertEquals(
        "https://img.pawchive.st/thumbnail/data/6c/15/6c1582a2125bb308c8226ff469f22d1343f9de8881a2028295f6fe9585e7eeb1.jpeg",
        file.thumbnailUrl("https://img.pawchive.st"),
    )
  }

  @Test
  fun parsesPawchiveHtmlFixtures() {
    val (api, _) = client { HttpStatusCode.OK to "{}" }

    val popular = api.parsePopularPostsPage(TestFixtures.read("pawchive.st:posts:popular.html"))
    assertTrue(popular.posts.isNotEmpty())
    assertEquals(42, popular.posts.first().attachmentCount)
    assertEquals(101, popular.posts.first().favoriteCount)
    assertEquals("2026-06-30", popular.info.maxDate)
    assertTrue(popular.info.navigationDates?.day.orEmpty().isNotEmpty())
    assertEquals(10, popular.pageInfo?.lastPage)

    val servicePosts =
        api.parsePostCardsPage(TestFixtures.read("pawchive.st:posts:service-patreon.html"))
    assertTrue(servicePosts.items.isNotEmpty())
    assertTrue(servicePosts.items.all { it.service == "patreon" })
    assertTrue(servicePosts.items.any { it.file?.path?.startsWith("/") == true })
    assertEquals(1000, servicePosts.pageInfo?.lastPage)

    val tagPosts = api.parsePostCards(TestFixtures.read("pawchive.st:posts:tag-nsfw.html"))
    assertTrue(tagPosts.isNotEmpty())

    val tags = api.parseTags(TestFixtures.read("pawchive.st:posts:tags.html"))
    assertTrue(tags.any { it.tag == "nsfw" && it.count > 0 })

    val creatorTags =
        api.parseCreatorTags(TestFixtures.read("pawchive.st:patreon:user:3295915:tags.html"))
    assertTrue(creatorTags.any { it.tag == "Animation" && it.count > 0 })

    val dmsPage = api.parseDmsPage(TestFixtures.read("pawchive.st:dms:search-test.html"))
    assertEquals(emptyList(), dmsPage.items)
    assertEquals(null, dmsPage.pageInfo)

    assertEquals(
        4,
        parsePageInfo(TestFixtures.read("pawchive.st:patreon:user:3295915.html"))?.lastPage,
    )
  }

  @Test
  fun emptyPostFileIsNotCountedAsAttachment() {
    val post =
        json.decodeFromString<ddd.kc.data.model.Post>(
            """{"id":"p","user":"u","service":"s","file":{}}"""
        )
    assertEquals(emptyList(), post.allFiles())
  }

  @Test
  fun liveApiSmokeTestWhenEnabled() = runBlocking {
    if (System.getenv("KC_LIVE_API_TESTS") != "true") return@runBlocking
    val httpClient =
        HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
          install(ContentNegotiation) { json(json) }
          defaultRequest { headers[HttpHeaders.UserAgent] = "Mozilla/5.0 (compatible; KC/1.0)" }
        }

    suspend fun assertLive(url: String) {
      val response = httpClient.get(url)
      assertEquals(
          HttpStatusCode.OK,
          response.status,
          "$url body=${response.bodyAsText().take(200)}",
      )
    }

    assertLive("https://pawchive.st/api/v1/creators")
    assertLive("https://pawchive.st/api/v1/posts?o=0")
    assertLive("https://pawchive.st/posts/popular")
    assertLive("https://pawchive.st/posts/tags")
    assertLive("https://pawchive.st/dms")
  }

  private companion object {
    const val creatorsJson =
        """[
          {"id":"artist","name":"Artist One","service":"patreon","indexed":10,"updated":20,"favorited":5,"public_id":"artist_one"},
          {"id":"fan","name":"Fan Creator","service":"fanbox","indexed":30,"updated":10,"favorited":9}
        ]"""
    const val postsJson =
        """[{"id":"post1","user":"artist","service":"patreon","title":"Post","file":{"name":"a.jpg","path":"/a.jpg"},"attachments":[]}]"""
    const val creatorPostsJson =
        """[{"id":"post1","user":"artist","service":"patreon","title":"Creator Post","tags":"{Animation,Chainsaw-Man,\"Quoted Tag\"}"}]"""
    const val postDetailJson =
        """{"id":"post1","user":"artist","service":"patreon","title":"Detail","content":"detail text","attachments":[{"name":"detail.jpg","path":"/detail.jpg"}],"tags":"{\"Tomoe Umari\",Vtuber,winner}"}"""
    const val commentsJson = """[{"id":"comment1","content":"ok","commenter":"reader"}]"""
    const val announcementsJson =
        """[{"service":"patreon","user_id":"artist","hash":"ann1","content":"notice"}]"""
    const val favoriteCreatorsJson =
        """[{"id":"artist","name":"Artist One","service":"patreon","indexed":"2026-06-01T00:00:00","updated":"2026-06-01T00:00:00","favorited":5}]"""
    const val favoritePostsJson =
        """[{"id":"post1","artist_id":"artist","user":"artist","service":"patreon","title":"Favorite Post"}]"""
  }
}
