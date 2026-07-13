package ddd.kc.ui.app.navigation

import androidx.compose.ui.platform.UriHandler
import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post
import ddd.kc.data.model.key
import ddd.kc.ui.pages.creator.CreatorRouteScreen
import ddd.kc.ui.pages.post.PostRouteScreen

class KcLinkUriHandler(
    private val openInternal: (AppScreen) -> Unit,
    private val fallback: UriHandler,
    private val navigationWindows: NavigationWindowStore,
) : UriHandler {
  override fun openUri(uri: String) {
    val target = parseKcRouteTarget(uri, navigationWindows)
    if (target == null) {
      fallback.openUri(uri)
      return
    }
    openInternal(target)
  }
}

internal fun parseKcRouteTarget(url: String, navigationWindows: NavigationWindowStore): AppScreen? {
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
          windowId = navigationWindows.putPosts(listOf(post)),
          resourceKey = post.key,
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
      CreatorRouteScreen(
          windowId = navigationWindows.putCreators(listOf(creator)),
          resourceKey = creator.key,
          startIndex = 0,
      )
    }
  }
}

private sealed interface KcRoute {
  val service: String
  val creatorId: String

  data class CreatorRoute(
      override val service: String,
      override val creatorId: String,
  ) : KcRoute

  data class PostRoute(
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
  val isPawchive =
      (scheme == "kc" && host == "pawchive") ||
          (scheme in setOf("http", "https") &&
              host in setOf("pawchive.st", "www.pawchive.st", "pawchive.pw", "www.pawchive.pw"))
  if (!isPawchive) return null
  val segments = path.split('/').filter { it.isNotBlank() }
  if (segments.size < 3 || segments[1] != "user") return null
  val service = segments[0].takeIf { it.isNotBlank() } ?: return null
  val creatorId = segments[2].takeIf { it.isNotBlank() } ?: return null
  if (segments.size >= 5 && segments[3] == "post") {
    return KcRoute.PostRoute(
        service = service,
        creatorId = creatorId,
        postId = segments[4],
    )
  }
  return KcRoute.CreatorRoute(service = service, creatorId = creatorId)
}
