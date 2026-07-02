package ddd.kc.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KcPullRefreshBox(
    enabled: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
  val pullToRefreshState = rememberPullToRefreshState()
  Box(
      modifier =
          modifier.pullToRefresh(
              state = pullToRefreshState,
              isRefreshing = refreshing,
              enabled = enabled,
              onRefresh = onRefresh,
          )
  ) {
    content()
    if (enabled || refreshing) {
      PullToRefreshDefaults.Indicator(
          modifier = Modifier.align(Alignment.TopCenter),
          isRefreshing = refreshing,
          state = pullToRefreshState,
      )
    }
  }
}
