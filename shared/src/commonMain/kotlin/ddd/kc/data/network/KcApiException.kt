package ddd.kc.data.network

class KcApiException(
    val statusCode: Int,
    message: String,
) : RuntimeException(message)

class KcDdosGuardException(
    message: String = "站点临时拦截了请求，请稍后重试",
) : RuntimeException(message)
