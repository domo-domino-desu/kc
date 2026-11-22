package ddd.kc.data.model

import kotlinx.datetime.LocalDate

enum class PostSearchMedia(val code: String) {
  ANY(""),
  IMAGE("image"),
  VIDEO("video"),
  AUDIO("audio"),
  ARCHIVE("archive"),
}

enum class PostSearchFilterError {
  INVALID_DATE,
  INVALID_RANGE,
}

data class PostSearchFilters(
    val titleOnly: Boolean = false,
    val after: String = "",
    val before: String = "",
    val service: String = "",
    val media: PostSearchMedia = PostSearchMedia.ANY,
) {
  val error: PostSearchFilterError?
    get() {
      val afterDate = parseDate(after)
      val beforeDate = parseDate(before)
      if ((after.isNotBlank() && afterDate == null) || (before.isNotBlank() && beforeDate == null))
          return PostSearchFilterError.INVALID_DATE
      if (afterDate != null && beforeDate != null && afterDate >= beforeDate)
          return PostSearchFilterError.INVALID_RANGE
      return null
    }

  fun query(text: String): String =
      buildList {
            val words = queryTerms.findAll(text.trim()).map { it.value }.toList()
            add(
                (if (!titleOnly) text.trim()
                else
                    words
                        .joinToString(" ") { term ->
                          if (!titleOnly || operator.containsMatchIn(term)) term
                          else if (term.startsWith("-")) "-intitle:${term.drop(1)}"
                          else "intitle:$term"
                        }
                        .takeIf { it.isNotBlank() }
                        .orEmpty())
            )
            if (after.isNotBlank()) add("after:${after.trim()}")
            if (before.isNotBlank()) add("before:${before.trim()}")
            if (service.isNotBlank()) add("service:$service")
            if (media != PostSearchMedia.ANY) add("has:${media.code}")
          }
          .filter { it.isNotBlank() }
          .joinToString(" ")

  companion object {
    private val queryTerms = Regex("""(?:[^\s"]|"[^"]*")+""")
    private val operator =
        Regex("""^-?(?:intitle|after|before|service|has):""", RegexOption.IGNORE_CASE)

    private fun parseDate(value: String): LocalDate? =
        value
            .trim()
            .takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
  }
}
