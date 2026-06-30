package ddd.kc.data.model

data class QueryState<out T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isFromCache: Boolean = false,
    val isStale: Boolean = false,
    val error: QueryError? = null,
    val lastUpdatedAtMillis: Long? = null,
) {
  val hasData: Boolean
    get() = data != null
}

sealed interface QueryError {
  val message: String
  val cause: Throwable?

  data class Network(
      override val message: String = "网络连接失败，请稍后重试",
      override val cause: Throwable? = null,
  ) : QueryError

  data class Unauthorized(
      override val message: String = "请先配置 Pawchive session cookie",
      override val cause: Throwable? = null,
  ) : QueryError

  data class RateLimited(
      override val message: String = "请求过于频繁，请稍后再试",
      override val cause: Throwable? = null,
  ) : QueryError

  data class Forbidden(
      override val message: String = "当前 session 无权限访问该资源",
      override val cause: Throwable? = null,
  ) : QueryError

  data class Http(
      val code: Int,
      override val message: String = "请求失败：HTTP $code",
      override val cause: Throwable? = null,
  ) : QueryError

  data class Parse(
      override val message: String = "数据解析失败",
      override val cause: Throwable? = null,
  ) : QueryError

  data class Unknown(
      override val message: String = "加载失败",
      override val cause: Throwable? = null,
  ) : QueryError
}
