package ddd.kc.ui.navigation

import androidx.compose.ui.platform.UriHandler
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Platform
import ddd.kc.data.model.Post
import ddd.kc.ui.pages.creator.CreatorRouteScreen
import ddd.kc.ui.pages.post.PostRouteScreen

class KcLinkUriHandler(
    private val navigator: Navigator,
    private val fallback: UriHandler,
) : UriHandler {
  override fun openUri(uri: String) {
    val target = parseKcRouteTarget(uri)
    if (target == null) {
      fallback.openUri(uri)
      return
    }
    navigator.push(target)
  }
}

internal fun parseKcRouteTarget(url: String): Screen? {
  val route = parseKcRoute(url) ?: return null
  return when (route) {
    is KcRoute.PostRoute -> {
      val post =
          Post(
              id = route.postId,
              user = route.creatorId,
              artistId = route.creatorId,
              service = route.service,
          )
      PostRouteScreen(
          platform = route.platform,
          posts = listOf(post),
          startIndex = 0,
          source = "link",
      )
    }

    is KcRoute.CreatorRoute -> {
      val creator =
          Creator(
              id = route.creatorId,
              name = route.creatorId,
              service = route.service,
              publicId = route.creatorId,
          )
      CreatorRouteScreen(platform = route.platform, creators = listOf(creator), startIndex = 0)
    }
  }
}

private sealed interface KcRoute {
  val platform: Platform
  val service: String
  val creatorId: String

  data class CreatorRoute(
      override val platform: Platform,
      override val service: String,
      override val creatorId: String,
  ) : KcRoute

  data class PostRoute(
      override val platform: Platform,
      override val service: String,
      override val creatorId: String,
      val postId: String,
  ) : KcRoute
}

private fun parseKcRoute(url: String): KcRoute? {
  val trimmed = url.trim()
  if (trimmed.isBlank()) return null
  val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
  val afterScheme = trimmed.substringAfter("://", missingDelimiterValue = trimmed)
  val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
  val rawPath = "/" + afterScheme.substringAfter('/', missingDelimiterValue = "")
  val path = rawPath.substringBefore('?').substringBefore('#')
  val platform =
      when {
        scheme == "kc" && host == "pawchive" -> Platform.PAWCHIVE
        scheme in setOf("http", "https") &&
            host in setOf("pawchive.st", "www.pawchive.st", "pawchive.pw", "www.pawchive.pw") ->
            Platform.PAWCHIVE
        else -> return null
      }
  val segments = path.split('/').filter { it.isNotBlank() }
  if (segments.size < 3 || segments[1] != "user") return null
  val service = segments[0].takeIf { it.isNotBlank() } ?: return null
  val creatorId = segments[2].takeIf { it.isNotBlank() } ?: return null
  if (segments.size >= 5 && segments[3] == "post") {
    return KcRoute.PostRoute(
        platform = platform,
        service = service,
        creatorId = creatorId,
        postId = segments[4],
    )
  }
  return KcRoute.CreatorRoute(platform = platform, service = service, creatorId = creatorId)
}
