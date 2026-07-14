package ddd.kc.data.remote.network

class PawchiveApiException(
    val statusCode: Int,
    message: String,
) : RuntimeException(message)

class PawchiveCfChallengeException(
    val cfRay: String?,
) : RuntimeException("Cloudflare challenge remains active")

class PawchiveChallengeCancelledException : RuntimeException("Cloudflare challenge was cancelled")
