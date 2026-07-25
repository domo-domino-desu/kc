package ddd.kc.ui.pages.dm

import cafe.adriel.voyager.core.model.StateScreenModel
import ddd.kc.ui.components.paging.ScrollPosition

data class DmScreenState(
    val scrollPosition: ScrollPosition = ScrollPosition(),
)

class DmScreenModel : StateScreenModel<DmScreenState>(DmScreenState()) {
  fun onScrollPositionChanged(index: Int, offset: Int) {
    val position = ScrollPosition(index, offset)
    if (mutableState.value.scrollPosition == position) return
    mutableState.value = mutableState.value.copy(scrollPosition = position)
  }
}
