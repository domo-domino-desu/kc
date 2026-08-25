package ddd.kc.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val ButtonGroupItemMinWidth = 144.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScrollableButtonGroup(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    // Keep every label on one line. When four items no longer fit, the group becomes horizontally
    // scrollable instead of compressing the buttons until Material's label wraps.
    val groupWidth = maxOf(maxWidth, ButtonGroupItemMinWidth * labels.size)
    LaunchedEffect(selectedIndex, labels.size, scrollState.maxValue) {
      if (labels.size > 1 && scrollState.maxValue > 0) {
        val fraction = selectedIndex.coerceIn(0, labels.lastIndex).toFloat() / labels.lastIndex
        scrollState.animateScrollTo((scrollState.maxValue * fraction).roundToInt())
      }
    }
    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.horizontalScroll(scrollState).width(groupWidth),
    ) {
      labels.forEachIndexed { index, label ->
        toggleableItem(
            checked = selectedIndex == index,
            label = label,
            onCheckedChange = { onSelected(index) },
            weight = 1f,
        )
      }
    }
  }
}
