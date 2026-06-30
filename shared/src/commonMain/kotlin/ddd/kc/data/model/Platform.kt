package ddd.kc.data.model

enum class Platform(val defaultBaseUrl: String, val displayName: String) {
  PAWCHIVE("https://pawchive.st", "Pawchive"),
}

fun Platform.defaultCdnUrl(): String = defaultBaseUrl.replace("://", "://img.")
