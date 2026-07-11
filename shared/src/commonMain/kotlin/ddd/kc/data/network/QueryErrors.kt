package ddd.kc.data.network

import ddd.kc.data.model.QueryError
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException

class QueryException(val error: QueryError) :
    RuntimeException("Query failed: ${error::class.simpleName}", error.cause)

fun QueryError.asException(): QueryException = QueryException(this)

fun Throwable.toQueryError(): QueryError =
    when (this) {
      is QueryException -> error
      is AuthRequiredException -> QueryError.Unauthorized(cause = this)
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
