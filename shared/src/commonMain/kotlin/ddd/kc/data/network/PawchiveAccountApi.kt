package ddd.kc.data.network

import ddd.kc.data.model.Creator
import ddd.kc.data.model.Post
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json

/** Authenticated Pawchive account operations, isolated from public content scraping. */
class PawchiveAccountApi(
    private val gateway: PawchiveHttpGateway,
    private val json: Json,
) {
  fun hasSession(): Boolean = gateway.hasSession()

  suspend fun favoriteCreators(type: String): List<Creator> =
      json.decodeFromString(
          gateway.getText(
              "/api/v1/account/favorites",
              "请求Favorites(type=$type)",
              authenticated = true,
          ) {
            parameter("type", type)
          }
      )

  suspend fun favoritePosts(): List<Post> =
      json.decodeFromString(
          gateway.getText(
              "/api/v1/account/favorites",
              "请求Favorites(type=post)",
              authenticated = true,
          ) {
            parameter("type", "post")
          }
      )

  suspend fun setCreatorFavorite(service: String, creatorId: String, favorite: Boolean) {
    val path =
        "/api/v1/favorites/creator/${service.encodeURLPathPart()}/${creatorId.encodeURLPathPart()}"
    if (favorite) gateway.postUnit(path, "收藏Creator -> 添加", authenticated = true)
    else gateway.deleteUnit(path, "收藏Creator -> 移除", authenticated = true)
  }

  suspend fun setPostFavorite(
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
