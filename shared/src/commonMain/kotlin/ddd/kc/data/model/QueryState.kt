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

fun <T> QueryState<T>.preserveRefreshUi(hasExistingData: Boolean): QueryState<T> =
    if (isLoading && hasExistingData) copy(isLoading = false, isRefreshing = true) else this

sealed interface QueryError {
  val cause: Throwable?

  data class Network(
      override val cause: Throwable? = null,
  ) : QueryError

  data class Unauthorized(
      override val cause: Throwable? = null,
  ) : QueryError

  data class InvalidCredentials(
      override val cause: Throwable? = null,
  ) : QueryError

  data class CfChallenge(
      val cfRay: String? = null,
      override val cause: Throwable? = null,
  ) : QueryError

  data class ChallengeCancelled(
      override val cause: Throwable? = null,
  ) : QueryError

  data class RateLimited(
      override val cause: Throwable? = null,
  ) : QueryError

  data class Forbidden(
      override val cause: Throwable? = null,
  ) : QueryError

  data class Http(
      val code: Int,
      override val cause: Throwable? = null,
  ) : QueryError

  data class Parse(
      override val cause: Throwable? = null,
  ) : QueryError

  data class Unknown(
      override val cause: Throwable? = null,
  ) : QueryError
}
