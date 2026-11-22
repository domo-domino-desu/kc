package ddd.kc.data.remote.translation

import ddd.kc.data.model.IndexTranslateModel
import ddd.kc.data.model.OpenAiTranslationConfig
import ddd.kc.data.model.TranslationProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class IndexTranslateTranslationClientTest {
  @Test
  fun longChunksReceiveMoreThanTheScriptsShortTextOutputBudget() = runTest {
    val requests = mutableListOf<HttpRequestData>()
    val client = mockClient(requests) { HttpStatusCode.OK to completion("translation") }
    IndexTranslateTranslationClient(client)
        .translate(
            TranslationRequest(
                TranslationProvider.INDEX_TRANSLATE,
                "word ".repeat(1024),
            )
        )
    val payload = Json.parseToJsonElement((requests.single().body as TextContent).text).jsonObject
    assertEquals("8192", payload["max_tokens"]?.jsonPrimitive?.content)
    client.close()
  }

  @Test
  fun usesDedicatedPublicApiAndAllThreeRealModelIds() = runTest {
    val requests = mutableListOf<HttpRequestData>()
    val client =
        mockClient(requests) { HttpStatusCode.OK to completion("<think>reasoning</think>译文") }
    val port = TranslationDispatcher(client)
    for (model in IndexTranslateModel.entries) {
      assertEquals(
          "译文",
          port.translate(
              TranslationRequest(
                  provider = TranslationProvider.INDEX_TRANSLATE,
                  sourceText = "hello\n\n%%\n\nworld",
                  sourceLanguageCode = "en",
                  targetLanguageCode = "zh-CN",
                  indexTranslateModel = model,
                  openAiConfig = OpenAiTranslationConfig(apiKey = "not-for-index"),
              )
          ),
      )
      val request = requests.last()
      assertEquals(IndexTranslateTranslationClient.endpoint, request.url.toString())
      assertEquals("Index-Translate-Client/1.0", request.headers[HttpHeaders.UserAgent])
      assertNull(request.headers[HttpHeaders.Authorization])
      assertNull(request.headers[HttpHeaders.Cookie])
      val payload = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
      assertEquals(model.modelId, payload["model"]?.jsonPrimitive?.content)
      assertEquals("0.0", payload["temperature"]?.jsonPrimitive?.content)
      assertEquals("1024", payload["max_tokens"]?.jsonPrimitive?.content)
      assertEquals("false", payload["stream"]?.jsonPrimitive?.content)
      assertEquals(
          "false",
          payload["chat_template_kwargs"]
              ?.jsonObject
              ?.get("enable_thinking")
              ?.jsonPrimitive
              ?.content,
      )
      val messages = payload["messages"]!!.jsonArray
      assertEquals(1, messages.size)
      assertEquals("user", messages.single().jsonObject["role"]?.jsonPrimitive?.content)
      val prompt = messages.single().jsonObject["content"]!!.jsonPrimitive.content
      assertTrue(prompt.startsWith("请将以下英语文本翻译成中文"))
      assertTrue(prompt.contains("【源文】\nhello\n\n%%\n\nworld"))
      assertTrue(prompt.contains("【约束要求】"))
      assertTrue(prompt.contains("请严格保留段落分隔标记 %% 的数量和顺序。"))
      assertTrue(prompt.contains("译文使用简体中文。"))
    }
    client.close()
  }

  @Test
  fun googleHttpParseAndBlankFailuresFallBackToSelectedIndexModel() = runTest {
    for ((status, body) in
        listOf(
            HttpStatusCode.BadGateway to "unavailable",
            HttpStatusCode.OK to "invalid JSON",
            HttpStatusCode.OK to "[[]]",
        )) {
      val requests = mutableListOf<HttpRequestData>()
      val client =
          mockClient(requests) {
            if (it.url.host == "translate.googleapis.com") status to body
            else HttpStatusCode.OK to completion("translated")
          }
      assertEquals(
          "translated",
          TranslationDispatcher(client)
              .translate(
                  TranslationRequest(
                      TranslationProvider.GOOGLE,
                      "source",
                      targetLanguageCode = "en",
                      indexTranslateModel = IndexTranslateModel.THIRTY_FIVE_B,
                  )
              ),
      )
      assertEquals(
          listOf("translate.googleapis.com", "index-translate.bilibili.com"),
          requests.map { it.url.host },
      )
      val payload = Json.parseToJsonElement((requests.last().body as TextContent).text).jsonObject
      assertEquals("Index-Translate-35B-A3B", payload["model"]?.jsonPrimitive?.content)
      client.close()
    }
  }

  @Test
  fun successfulGoogleDoesNotContactIndexTranslate() = runTest {
    val requests = mutableListOf<HttpRequestData>()
    val client = mockClient(requests) { HttpStatusCode.OK to "[[[\"google result\",\"source\"]]]" }
    assertEquals(
        "google result",
        TranslationDispatcher(client)
            .translate(TranslationRequest(TranslationProvider.GOOGLE, "source")),
    )
    assertEquals(listOf("translate.googleapis.com"), requests.map { it.url.host })
    client.close()
  }

  @Test
  fun cancellationDoesNotStartFallback() = runTest {
    val requests = mutableListOf<HttpRequestData>()
    val client = mockClient(requests) { throw CancellationException("cancelled") }
    assertFailsWith<CancellationException> {
      TranslationDispatcher(client)
          .translate(TranslationRequest(TranslationProvider.GOOGLE, "source"))
    }
    assertEquals(listOf("translate.googleapis.com"), requests.map { it.url.host })
    client.close()
  }

  @Test
  fun indexFailuresAreNotReturnedAsTranslationsAndDoNotExposeResponseBodies() = runTest {
    for ((status, body) in
        listOf(
            HttpStatusCode.TooManyRequests to "private-response-text",
            HttpStatusCode.OK to completion(""),
            HttpStatusCode.OK to "{\"choices\":[]}",
            HttpStatusCode.OK to
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}]}",
        )) {
      val client = mockClient(mutableListOf()) { status to body }
      val error =
          assertFailsWith<IllegalStateException> {
            TranslationDispatcher(client)
                .translate(
                    TranslationRequest(TranslationProvider.INDEX_TRANSLATE, "private-source-text")
                )
          }
      assertFalse(error.message.orEmpty().contains("private-response-text"))
      assertFalse(error.message.orEmpty().contains("private-source-text"))
      client.close()
    }
  }

  @Test
  fun stripsThinkLikeTheReferenceAndMapsAppLanguageCodes() = runTest {
    for (content in
        listOf("  translation  ", "<think>translation", "reasoning</think>translation")) {
      val client = mockClient(mutableListOf()) { HttpStatusCode.OK to completion(content) }
      assertEquals(
          "translation",
          TranslationDispatcher(client)
              .translate(TranslationRequest(TranslationProvider.INDEX_TRANSLATE, "source")),
      )
      client.close()
    }
    val prompt =
        buildIndexTranslatePrompt(
            TranslationRequest(
                TranslationProvider.INDEX_TRANSLATE,
                " source ",
                targetLanguageCode = "en",
            )
        )
    assertTrue(prompt.startsWith("请将以下文本翻译成英语"))
    assertTrue(prompt.contains("【源文】\nsource\n"))
  }
}

private fun completion(text: String): String =
    "{\"choices\":[{\"message\":{\"content\":" +
        kotlinx.serialization.json.JsonPrimitive(text).toString() +
        "}}]}"

private fun mockClient(
    requests: MutableList<HttpRequestData>,
    reply: (HttpRequestData) -> Pair<HttpStatusCode, String>,
): HttpClient =
    HttpClient(
        MockEngine { request ->
          requests += request
          val (status, body) = reply(request)
          respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    )
