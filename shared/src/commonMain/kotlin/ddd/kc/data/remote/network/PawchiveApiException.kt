package ddd.kc.data.remote.network

class PawchiveApiException(
    val statusCode: Int,
    message: String,
) : RuntimeException(message)

class PawchiveGuardException : RuntimeException("Request blocked by upstream guard")
