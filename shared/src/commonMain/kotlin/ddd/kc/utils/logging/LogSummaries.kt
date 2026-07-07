package ddd.kc.utils.logging

import ddd.kc.data.model.Post
import ddd.kc.data.model.allFiles
import ddd.kc.data.model.creatorId
import ddd.kc.data.model.imageFiles

fun summarizePost(post: Post): String =
    "service=${post.service},creator=${post.creatorId},post=${post.id},titleLength=${post.title.length},contentLength=${post.content?.length ?: 0},file=${if (post.file != null) "有" else "无"},attachments=${post.attachments.size},images=${post.imageFiles().size},tags=${post.tags?.size ?: 0}"

fun summarizePostFiles(post: Post): String {
  val files = post.allFiles()
  val missingPath = files.count { it.path.isNullOrBlank() }
  val missingName = files.count { it.name.isNullOrBlank() }
  val deferred = files.count { it.deferred }
  return "files=${files.size},images=${post.imageFiles().size},missingPath=$missingPath,missingName=$missingName,deferred=$deferred"
}

fun summarizeThrowable(throwable: Throwable): String =
    throwable.message?.takeIf { it.isNotBlank() } ?: throwable.toString()

fun summarizeUrl(url: String): String = url.trim()
