package ddd.kc.util

private val braceTemplateTokenRegex = Regex("""\{([A-Za-z0-9_]+)\}""")

fun renderBraceTemplate(template: String, values: Map<String, String>): String {
  if (template.isEmpty()) return template
  return braceTemplateTokenRegex.replace(template) { match ->
    val key = match.groupValues.getOrElse(1) { "" }
    if (key in values) values[key].orEmpty() else match.value
  }
}
