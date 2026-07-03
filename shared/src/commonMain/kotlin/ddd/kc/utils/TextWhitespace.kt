package ddd.kc.utils

private val consecutiveBlankLinesRegex = Regex("""(?:\r?\n[ \t]*){3,}""")

fun collapseConsecutiveBlankLines(text: String): String =
    text.replace(consecutiveBlankLinesRegex, "\n\n")
