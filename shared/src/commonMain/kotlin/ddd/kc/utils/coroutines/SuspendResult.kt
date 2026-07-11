package ddd.kc.utils.coroutines

import kotlinx.coroutines.CancellationException

/** Captures ordinary failures while preserving structured-concurrency cancellation. */
suspend inline fun <T> resultOfSuspend(block: () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      Result.failure(error)
    }
