package ddd.kc.ui.components.platform

import ddd.kc.data.local.settings.AppSettings
import ddd.kc.data.local.settings.DownloadFileNameMode
import ddd.kc.data.local.settings.DownloadSubfolderMode
import ddd.kc.data.model.Post
import ddd.kc.data.model.PostFile
import ddd.kc.data.model.creatorId
import ddd.kc.utils.renderBraceTemplate

data class PostDownloadTarget(
    val relativeDirectories: List<String>,
    val fileName: String,
)

fun buildPostDownloadTarget(
    settings: AppSettings,
    post: Post,
    file: PostFile,
): PostDownloadTarget {
  val baseName =
      buildDownloadFileBaseName(
          settings = settings,
          post = post,
      )
  val extension =
      extractExtensionFromName(file.name)
          ?: extractExtensionFromName(file.path)
          ?: defaultDownloadExtension
  return PostDownloadTarget(
      relativeDirectories = resolveDownloadRelativeDirectories(settings, post),
      fileName = composeFileName(baseName, extension),
  )
}

private fun resolveDownloadRelativeDirectories(settings: AppSettings, post: Post): List<String> =
    when (settings.downloadSubfolderMode()) {
      DownloadSubfolderMode.FLAT -> emptyList()
      DownloadSubfolderMode.BY_USERNAME ->
          listOf(sanitizePathSegment(post.user).ifBlank { "unknown-user" })
    }

private fun buildDownloadFileBaseName(settings: AppSettings, post: Post): String {
  val template =
      when (settings.downloadFileNameMode()) {
        DownloadFileNameMode.ID_TITLE -> "{post_id}-{title}"
        DownloadFileNameMode.USERNAME_ID -> "{username}-{post_id}"
        DownloadFileNameMode.USERNAME_ID_TITLE -> "{username}-{post_id}-{title}"
        DownloadFileNameMode.CUSTOM -> settings.downloadCustomFileNameTemplate()
      }
  val rendered =
      renderBraceTemplate(
          template = template,
          values =
              mapOf(
                  "username" to post.user.trim(),
                  "title" to post.title.trim(),
                  "submission_id" to post.id,
                  "post_id" to post.id,
                  "creator_id" to post.creatorId,
                  "service" to post.service.trim(),
              ),
      )
  val normalized = normalizeTemplateResult(rendered)
  return cleanupSeparators(sanitizePathSegment(normalized)).ifBlank { "post-${post.id}" }
}

private fun composeFileName(baseName: String, extension: String): String {
  val safeBase = sanitizePathSegment(baseName).ifBlank { "download" }
  val safeExtension =
      extension.trim().trimStart('.').lowercase().ifBlank { defaultDownloadExtension }
  return "$safeBase.$safeExtension"
}

private fun normalizeTemplateResult(raw: String): String =
    raw.replace(Regex("""[-_\s]+"""), "-").trim('-', '_', ' ', '.')

private fun cleanupSeparators(raw: String): String =
    raw.replace(Regex("""[-_]{2,}"""), "-").trim('-', '_', ' ', '.')

private fun sanitizePathSegment(raw: String): String =
    raw.trim()
        .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '.')
        .take(180)

private fun extractExtensionFromName(fileName: String?): String? {
  val normalized = fileName?.trim().orEmpty()
  if (normalized.isBlank()) return null
  val base = normalized.substringAfterLast('/').substringAfterLast('\\').substringBefore('?')
  val extension = base.substringAfterLast('.', missingDelimiterValue = "").trim().lowercase()
  return extension.takeIf { value -> value.isNotBlank() && value.all(Char::isLetterOrDigit) }
}

private const val defaultDownloadExtension: String = "bin"
