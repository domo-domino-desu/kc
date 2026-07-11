package ddd.kc.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import ddd.kc.data.model.QueryState
import kotlinx.coroutines.flow.Flow

class QueryResult<T>
internal constructor(
    val state: QueryState<T>,
    val refresh: () -> Unit,
)

@Composable
fun <T> rememberQuery(
    key: Any? = Unit,
    query: (forceRefresh: Boolean) -> Flow<QueryState<T>>,
): QueryResult<T> {
  var refreshToken by remember(key) { mutableIntStateOf(0) }
  val currentQuery by rememberUpdatedState(query)
  val state by
      produceState(QueryState<T>(isLoading = true), key, refreshToken) {
        currentQuery(refreshToken > 0).collect { value = it }
      }
  return QueryResult(state) { refreshToken++ }
}
