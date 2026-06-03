package ddd.kc.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CreatorLink(
    val url: String = "",
    val service: String? = null,
    val name: String? = null,
) {
  val displayName: String
    get() =
        name ?: service ?: url.removePrefix("https://").removePrefix("http://").substringBefore("/")
}

fun CreatorLink.toLinkedCreatorOrNull(): Creator? {
  val parsed = parseCreatorPath(url)
  val resolvedService = parsed?.first ?: service
  val resolvedId =
      parsed?.second
          ?: url.substringBefore('?').substringBefore('#').substringAfterLast('/').takeIf {
            it.isNotBlank()
          }
  if (resolvedService.isNullOrBlank() || resolvedId.isNullOrBlank()) return null
  return Creator(
      id = resolvedId,
      name = displayName,
      service = resolvedService,
  )
}

private fun parseCreatorPath(url: String): Pair<String, String>? {
  val marker = "/user/"
  val beforeQuery = url.substringBefore('?').substringBefore('#').trimEnd('/')
  val markerIndex = beforeQuery.indexOf(marker)
  if (markerIndex <= 0) return null
  val service =
      beforeQuery.substring(0, markerIndex).substringAfterLast('/').takeIf { it.isNotBlank() }
  val id =
      beforeQuery.substring(markerIndex + marker.length).substringBefore('/').takeIf {
        it.isNotBlank()
      }
  return if (service != null && id != null) service to id else null
}
