package ddd.kc.data.remote.network

import ddd.kc.data.model.QueryError
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

class QueryException(val error: QueryError) :
    RuntimeException("Query failed: ${error::class.simpleName}", error.cause)

fun QueryError.asException(): QueryException = QueryException(this)

fun Throwable.toQueryError(): QueryError =
    when (this) {
      is CancellationException -> throw this
      is QueryException -> error
      is PawchiveCfChallengeException -> QueryError.CfChallenge(cfRay = cfRay, cause = this)
      is PawchiveChallengeCancelledException -> QueryError.ChallengeCancelled(cause = this)
      is AuthRequiredException -> QueryError.Unauthorized(cause = this)
      is InvalidCredentialsException -> QueryError.InvalidCredentials(cause = this)
      is PawchiveApiException ->
          when (statusCode) {
            401 -> QueryError.Unauthorized(cause = this)
            403 -> QueryError.Forbidden(cause = this)
            429 -> QueryError.RateLimited(cause = this)
            else -> QueryError.Http(statusCode, cause = this)
          }
      is HttpRequestTimeoutException,
      is IOException -> QueryError.Network(cause = this)
      is kotlinx.serialization.SerializationException -> QueryError.Parse(cause = this)
      else -> QueryError.Unknown(this)
    }

fun HttpStatusCode.toQueryError(): QueryError =
    when (value) {
      401 -> QueryError.Unauthorized()
      403 -> QueryError.Forbidden()
      429 -> QueryError.RateLimited()
      else -> QueryError.Http(value)
    }
