package ddd.kc.data.model

enum class Platform(val defaultBaseUrl: String, val displayName: String) {
  KEMONO("https://kemono.cr", "Kemono"),
  COOMER("https://coomer.st", "Coomer"),
}

fun Platform.defaultCdnUrl(): String = defaultBaseUrl.replace("://", "://img.")
