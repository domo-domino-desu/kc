package ddd.kc.data.remote.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostFile
import ddd.kc.data.model.allFiles
import ddd.kc.data.model.canLoadFullImage
import ddd.kc.data.model.fullUrl
import ddd.kc.data.model.thumbnailUrl
import ddd.kc.fake.TestFixtures
import ddd.kc.fake.TestSecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath

class PawchiveApiTest {

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
  }

  private fun settings(): AppSettings {
    val file = File.createTempFile("kc-test-settings", ".preferences_pb")
    file.delete()
    return AppSettings(
        PreferenceDataStoreFactory.createWithPath(produceFile = { file.absolutePath.toPath() }),
        TestSecretStore(),
    )
  }

  private fun client(
      settings: AppSettings = settings(),
      headersFor: (HttpRequestData) -> Headers = {
        headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
      },
      handler: (HttpRequestData) -> Pair<HttpStatusCode, String>,
  ): Pair<PawchiveApi, List<HttpRequestData>> {
    val requests = mutableListOf<HttpRequestData>()
    val engine = MockEngine { request ->
      requests += request
      val (status, body) = handler(request)
      respond(
          content = body,
          status = status,
          headers = headersFor(request),
      )
    }
    val httpClient =
        HttpClient(engine) {
          followRedirects = false
          install(ContentNegotiation) { json(json) }
        }
    val sessionStore = KcSessionStore(TestSecretStore())
    sessionStore.saveSession("session=test_session; path=/")
    val gateway = PawchiveHttpGateway(httpClient, settings, sessionStore)
    return PawchiveApi(gateway, json) to requests
  }

  @Test
  fun usesPawchiveJsonAndHtmlEndpoints() = runBlocking {
    val (api, requests) =
        client { request ->
          HttpStatusCode.OK to
              when (request.url.encodedPath) {
                "/api/v1/creators" -> creatorsJson
                "/api/v1/posts" -> postsJson
                "/posts" -> TestFixtures.read("pawchive.st__posts__service-patreon.html")
                "/posts/popular" -> TestFixtures.read("pawchive.st__posts__popular.html")
                "/posts/tags" -> TestFixtures.read("pawchive.st__posts__tags.html")
                "/dms" -> TestFixtures.read("pawchive.pw__dms.html")
                "/api/v1/patreon/user/artist" -> creatorPostsJson
                "/patreon/user/artist" ->
                    TestFixtures.read("pawchive.st__patreon__user__3295915.html")
                "/api/v1/patreon/user/artist/post/post1" -> postDetailJson
                "/api/v1/patreon/user/artist/post/post1/comments" -> commentsJson
                "/api/v1/patreon/user/artist/announcements" -> announcementsJson
                "/patreon/user/artist/tags" ->
                    TestFixtures.read("pawchive.st__patreon__user__3295915__tags.html")
                "/api/v1/account/favorites" ->
                    if (request.url.parameters["type"] == "post") favoritePostsJson
                    else favoriteCreatorsJson
                "/api/v1/favorites/creator/patreon/artist" -> "{}"
                "/api/v1/favorites/post/patreon/artist/post1" -> "{}"
                else -> error("Unexpected path ${request.url.encodedPath}")
              }
        }

    assertEquals(2, api.parseCreators(api.fetchCreatorsBody()).size)
    assertEquals(
        listOf("post1"),
        api.parsePosts(api.fetchRecentPostsBody(offset = 50)).map { it.id },
    )
    assertTrue(api.parsePostCards(api.fetchPostSearchBody(query = "art", offset = 0)).isNotEmpty())
    assertTrue(
        api.parsePostCards(api.fetchPostSearchBody(query = "", offset = 0, tag = "nsfw"))
            .isNotEmpty()
    )
    assertTrue(api.parsePopularPostsPage(api.fetchPopularPostsBody()).posts.isNotEmpty())
    assertTrue(api.parseTags(api.fetchTagsBody()).any { it.tag == "nsfw" && it.count > 0 })
    assertTrue(api.parseDms(api.fetchDmsBody(offset = 0)).isNotEmpty())
    val creatorPage =
        api.parsePostCardsPage(api.fetchCreatorPostsPageBody("patreon", "artist", 0), 0)
    assertTrue(creatorPage.items.isNotEmpty())
    assertTrue(creatorPage.items.all { it.service == "patreon" })
    assertTrue(creatorPage.items.all { it.id.isNotBlank() && it.user.isNotBlank() })
    assertEquals(4, creatorPage.pageInfo?.lastPage)
    val detail = api.parsePost(api.fetchPostBody("patreon", "artist", "post1"))
    assertEquals("post1", detail.id)
    assertEquals("detail text", detail.content)
    assertEquals(listOf("Tomoe Umari", "Vtuber", "winner"), detail.tags)
    assertEquals(listOf("detail.jpg"), detail.attachments.map { it.name })
    assertEquals(
        listOf("comment1"),
        api.getPostComments("patreon", "artist", "post1").map { it.id },
    )
    assertEquals(
        listOf("ann1"),
        api.getCreatorAnnouncements("patreon", "artist").map { it.hash },
    )
    assertEquals(
        true,
        api.parseCreatorTags(api.fetchCreatorTagsBody("patreon", "artist")).any {
          it.tag == "Animation"
        },
    )
    assertEquals(listOf("artist"), api.getFavorites(type = "artist").map { it.id })
    assertEquals(listOf("post1"), api.getFavoritePosts().map { it.id })
    api.addFavoriteCreator(service = "patreon", creatorId = "artist")
    api.removeFavoriteCreator(service = "patreon", creatorId = "artist")
    api.addFavoritePost(service = "patreon", creatorId = "artist", postId = "post1")
    api.removeFavoritePost(service = "patreon", creatorId = "artist", postId = "post1")

    val paths = requests.map { it.url.encodedPath }
    assertTrue(paths.contains("/patreon/user/artist"))
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
  fun usesConfiguredBaseUrl() = runBlocking {
    val settings = settings()
    settings.save(settings.snapshot().copy(pawchiveBaseUrl = "https://pawchive.pw/"))
    val (api, requests) = client(settings) { HttpStatusCode.OK to creatorsJson }

    api.fetchCreatorsBody()

    assertEquals("pawchive.pw", requests.single().url.host)
    assertEquals("https", requests.single().url.protocol.name)
  }

  @Test
  fun permanentRedirectUpdatesBaseUrlAndRetriesGetWithQuery() = runBlocking {
    val settings = settings()
    val (api, requests) =
        client(
            settings = settings,
            headersFor = { request ->
              if (request.url.host == "pawchive.st") {
                headersOf(
                    HttpHeaders.Location,
                    "https://pawchive.pw${request.url.encodedPathAndQuery}",
                )
              } else {
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
              }
            },
        ) { request ->
          if (request.url.host == "pawchive.st") HttpStatusCode.MovedPermanently to ""
          else HttpStatusCode.OK to postsJson
        }

    api.fetchRecentPostsBody(offset = 50)

    assertEquals("https://pawchive.pw", settings.baseUrl())
    assertEquals("https://img.pawchive.pw", settings.cdnUrl())
    assertEquals(listOf("pawchive.st", "pawchive.pw"), requests.map { it.url.host })
    assertTrue(requests.all { it.url.parameters["o"] == "50" })
  }

  @Test
  fun canonicalEndpointCanSwitchBackToPreviousDomain() = runBlocking {
    val settings = settings()
    settings.save(settings.snapshot().copy(pawchiveBaseUrl = "https://pawchive.pw"))
    val (api, requests) =
        client(
            settings = settings,
            headersFor = { request ->
              if (request.url.host == "pawchive.pw") {
                headersOf(
                    HttpHeaders.Location,
                    "https://pawchive.st${request.url.encodedPathAndQuery}",
                )
              } else {
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
              }
            },
        ) { request ->
          if (request.url.host == "pawchive.pw") HttpStatusCode.MovedPermanently to ""
          else HttpStatusCode.OK to creatorsJson
        }

    api.fetchCreatorsBody()

    assertEquals("https://pawchive.st", settings.baseUrl())
    assertEquals(listOf("pawchive.pw", "pawchive.st"), requests.map { it.url.host })
  }

  @Test
  fun permanentRedirectPreservesPostAndDeleteMethodsAndSession() = runBlocking {
    suspend fun verify(method: HttpMethod, action: suspend (PawchiveApi) -> Unit) {
      val settings = settings()
      val (api, requests) =
          client(
              settings = settings,
              headersFor = { request ->
                if (request.url.host == "pawchive.st") {
                  headersOf(
                      HttpHeaders.Location,
                      "https://pawchive.pw${request.url.encodedPathAndQuery}",
                  )
                } else {
                  headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
              },
          ) { request ->
            if (request.url.host == "pawchive.st") HttpStatusCode.PermanentRedirect to ""
            else HttpStatusCode.OK to "{}"
          }

      action(api)

      assertEquals(listOf(method, method), requests.map { it.method })
      assertTrue(requests.all { it.headers[HttpHeaders.Cookie] == "session=test_session" })
      assertEquals("https://pawchive.pw", settings.baseUrl())
    }

    verify(HttpMethod.Post) { it.addFavoriteCreator("patreon", "artist") }
    verify(HttpMethod.Delete) { it.removeFavoriteCreator("patreon", "artist") }
  }

  @Test
  fun unknownHttpsCanonicalOriginIsAccepted() = runBlocking {
    val settings = settings()
    val (api, requests) =
        client(
            settings = settings,
            headersFor = { request ->
              if (request.url.host == "pawchive.st") {
                headersOf(
                    HttpHeaders.Location,
                    "https://archive.example${request.url.encodedPathAndQuery}",
                )
              } else {
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
              }
            },
        ) { request ->
          if (request.url.host == "pawchive.st") HttpStatusCode.MovedPermanently to ""
          else HttpStatusCode.OK to creatorsJson
        }

    api.fetchCreatorsBody()

    assertEquals("https://archive.example", settings.baseUrl())
    assertEquals(listOf("pawchive.st", "archive.example"), requests.map { it.url.host })
  }

  @Test
  fun temporarySameOriginRedirectDoesNotChangeSetting() = runBlocking {
    val settings = settings()
    val (api, requests) =
        client(
            settings = settings,
            headersFor = { request ->
              if (request.url.encodedPath == "/api/v1/creators") {
                headersOf(HttpHeaders.Location, "https://pawchive.st/maintenance")
              } else {
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
              }
            },
        ) { request ->
          if (request.url.encodedPath == "/api/v1/creators") HttpStatusCode.Found to ""
          else HttpStatusCode.OK to creatorsJson
        }

    api.fetchCreatorsBody()

    assertEquals(AppSettings.PAWCHIVE_DEFAULT_BASE_URL, settings.baseUrl())
    assertEquals(listOf("/api/v1/creators", "/maintenance"), requests.map { it.url.encodedPath })
  }

  @Test
  fun concurrentRedirectsConvergeOnOneCanonicalOrigin() = runBlocking {
    val settings = settings()
    val requests = java.util.Collections.synchronizedList(mutableListOf<HttpRequestData>())
    val engine = MockEngine { request ->
      requests += request
      if (request.url.host == "pawchive.st") {
        respond(
            content = "",
            status = HttpStatusCode.MovedPermanently,
            headers =
                headersOf(
                    HttpHeaders.Location,
                    "https://pawchive.pw${request.url.encodedPathAndQuery}",
                ),
        )
      } else {
        respond(content = creatorsJson, status = HttpStatusCode.OK)
      }
    }
    val httpClient = HttpClient(engine) { followRedirects = false }
    val gateway = PawchiveHttpGateway(httpClient, settings, KcSessionStore(TestSecretStore()))
    val api = PawchiveApi(gateway, json)

    coroutineScope { List(8) { async { api.fetchCreatorsBody() } }.awaitAll() }

    assertEquals("https://pawchive.pw", settings.baseUrl())
    assertEquals(8, requests.count { it.url.host == "pawchive.pw" })
  }

  @Test
  fun lateRedirectDoesNotOverwriteNewerUserEndpoint() = runBlocking {
    val settings = settings()
    val oldRequestStarted = CompletableDeferred<Unit>()
    val releaseOldResponse = CompletableDeferred<Unit>()
    val requests = java.util.Collections.synchronizedList(mutableListOf<HttpRequestData>())
    val engine = MockEngine { request ->
      requests += request
      if (request.url.host == "pawchive.st") {
        oldRequestStarted.complete(Unit)
        releaseOldResponse.await()
        respond(
            content = "",
            status = HttpStatusCode.MovedPermanently,
            headers =
                headersOf(
                    HttpHeaders.Location,
                    "https://pawchive.pw${request.url.encodedPathAndQuery}",
                ),
        )
      } else {
        respond(content = creatorsJson, status = HttpStatusCode.OK)
      }
    }
    val httpClient = HttpClient(engine) { followRedirects = false }
    val api =
        PawchiveApi(
            PawchiveHttpGateway(httpClient, settings, KcSessionStore(TestSecretStore())),
            json,
        )

    val request = async { api.fetchCreatorsBody() }
    oldRequestStarted.await()
    settings.save(settings.snapshot().copy(pawchiveBaseUrl = "https://custom.example"))
    releaseOldResponse.complete(Unit)
    request.await()

    assertEquals("https://custom.example", settings.baseUrl())
    assertEquals(listOf("pawchive.st", "custom.example"), requests.map { it.url.host })
  }

  @Test
  fun redirectLoopAndRedirectLimitAreExplicitFailures() = runBlocking {
    val loopSettings = settings()
    val (loopApi, loopRequests) =
        client(
            settings = loopSettings,
            headersFor = { request ->
              val target =
                  if (request.url.encodedPath == "/api/v1/creators") "/maintenance"
                  else "/api/v1/creators"
              headersOf(HttpHeaders.Location, target)
            },
        ) {
          HttpStatusCode.Found to ""
        }

    assertFailsWith<PawchiveApiException> { loopApi.fetchCreatorsBody() }
    assertEquals(3, loopRequests.size)
    assertEquals(AppSettings.PAWCHIVE_DEFAULT_BASE_URL, loopSettings.baseUrl())

    val limitSettings = settings()
    val (limitApi, limitRequests) =
        client(
            settings = limitSettings,
            headersFor = { request ->
              val next =
                  if (request.url.host == "pawchive.st") 1
                  else request.url.host.removePrefix("hop").substringBefore('.').toInt() + 1
              headersOf(
                  HttpHeaders.Location,
                  "https://hop$next.example${request.url.encodedPathAndQuery}",
              )
            },
        ) {
          HttpStatusCode.PermanentRedirect to ""
        }

    assertFailsWith<PawchiveApiException> { limitApi.fetchCreatorsBody() }
    assertEquals(MAX_EXPECTED_REDIRECT_REQUESTS, limitRequests.size)
  }

  @Test
  fun canonicalRedirectValidationRejectsUnsafeOrResourceSpecificTargets() {
    val source = Url("https://pawchive.st/api/v1/posts?o=50")

    assertNull(
        canonicalRedirectOrigin(
            source,
            Url("http://pawchive.pw/api/v1/posts?o=50"),
            HttpStatusCode.MovedPermanently,
        )
    )
    assertNull(
        canonicalRedirectOrigin(
            source,
            Url("https://user@pawchive.pw/api/v1/posts?o=50"),
            HttpStatusCode.MovedPermanently,
        )
    )
    assertNull(
        canonicalRedirectOrigin(
            source,
            Url("https://pawchive.pw/other?o=50"),
            HttpStatusCode.MovedPermanently,
        )
    )
    assertNull(
        canonicalRedirectOrigin(
            source,
            Url("https://pawchive.pw/api/v1/posts?o=51"),
            HttpStatusCode.MovedPermanently,
        )
    )
    assertNull(
        canonicalRedirectOrigin(
            source,
            Url("https://pawchive.pw/api/v1/posts?o=50"),
            HttpStatusCode.Found,
        )
    )
    assertEquals(
        "https://pawchive.pw",
        canonicalRedirectOrigin(
            source,
            Url("https://pawchive.pw/api/v1/posts?o=50"),
            HttpStatusCode.PermanentRedirect,
        ),
    )
  }

  @Test
  fun favorites401BecomesAuthRequired() = runBlocking {
    val (api, _) = client { HttpStatusCode.Unauthorized to "{}" }
    assertFailsWith<AuthRequiredException> { api.getFavorites(type = "artist") }
    Unit
  }

  @Test
  fun comments404BecomesEmptyComments() = runBlocking {
    val (api, _) = client { HttpStatusCode.NotFound to """{"error":"not found"}""" }
    assertEquals(emptyList(), api.getPostComments("fanbox", "artist", "post1"))
  }

  @Test
  fun commentsNon404RemainsAnExplicitHttpFailure() = runBlocking {
    val (api, _) = client { HttpStatusCode.InternalServerError to """{"error":"server"}""" }
    assertFailsWith<PawchiveApiException> { api.getPostComments("fanbox", "artist", "post1") }
    Unit
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
  fun parsesArchiveStateAndDisablesFullImageLoading() {
    val post =
        json.decodeFromString<Post>(
            """{
              "id":"p",
              "user":"u",
              "service":"patreon",
              "preview_state":"scraped",
              "has_full":false,
              "file":{"name":"cover.png","path":"/cover.png"},
              "attachments":[
                {"name":"ready.png","path":"/ready.png"},
                {"name":"missing.png","path":"/missing.png","deferred":true}
              ]
            }"""
        )

    assertEquals("scraped", post.previewState)
    assertEquals(false, post.hasFull)
    assertEquals(true, post.attachments[1].deferred)
    assertEquals(false, post.file!!.canLoadFullImage(post))
    assertEquals(false, post.attachments[0].canLoadFullImage(post))
    assertEquals(false, post.attachments[1].canLoadFullImage(post))

    val normalPost = post.copy(hasFull = true)
    assertEquals(true, normalPost.attachments[0].canLoadFullImage(normalPost))
    assertEquals(false, normalPost.attachments[1].canLoadFullImage(normalPost))
  }

  @Test
  fun parsesPawchiveHtmlFixtures() {
    val (api, _) = client { HttpStatusCode.OK to "{}" }

    val popular = api.parsePopularPostsPage(TestFixtures.read("pawchive.st__posts__popular.html"))
    assertTrue(popular.posts.isNotEmpty())
    assertEquals(42, popular.posts.first().attachmentCount)
    assertEquals(101, popular.posts.first().favoriteCount)
    assertEquals("2026-06-30", popular.info.maxDate)
    assertTrue(popular.info.navigationDates?.day.orEmpty().isNotEmpty())
    assertEquals(10, popular.pageInfo?.lastPage)

    val servicePosts =
        api.parsePostCardsPage(TestFixtures.read("pawchive.st__posts__service-patreon.html"))
    assertTrue(servicePosts.items.isNotEmpty())
    assertTrue(servicePosts.items.all { it.service == "patreon" })
    assertTrue(servicePosts.items.any { it.file?.path?.startsWith("/") == true })
    assertEquals(1000, servicePosts.pageInfo?.lastPage)

    val tagPosts = api.parsePostCards(TestFixtures.read("pawchive.st__posts__tag-nsfw.html"))
    assertTrue(tagPosts.isNotEmpty())

    val tags = api.parseTags(TestFixtures.read("pawchive.st__posts__tags.html"))
    assertTrue(tags.any { it.tag == "nsfw" && it.count > 0 })

    val creatorTags =
        api.parseCreatorTags(TestFixtures.read("pawchive.st__patreon__user__3295915__tags.html"))
    assertTrue(creatorTags.any { it.tag == "Animation" && it.count > 0 })

    val dmsPage = api.parseDmsPage(TestFixtures.read("pawchive.pw__dms.html"))
    assertTrue(dmsPage.items.isNotEmpty())
    assertEquals("patreon", dmsPage.items.first().service)
    assertTrue(dmsPage.items.first().user.orEmpty().isNotBlank())
    assertTrue(dmsPage.items.first().content.orEmpty().isNotBlank())
    assertTrue(dmsPage.items.any { it.artist?.name.orEmpty().isNotBlank() })
    assertEquals(null, dmsPage.pageInfo)

    assertEquals(
        4,
        parsePageInfo(TestFixtures.read("pawchive.st__patreon__user__3295915.html"))?.lastPage,
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
    const val MAX_EXPECTED_REDIRECT_REQUESTS = 6
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
