package ddd.kc.ui.pages.works

import cafe.adriel.voyager.core.model.StateScreenModel
import ddd.kc.ui.components.state.ScrollPosition

data class WorksScreenState(
    val selectedIndex: Int = 0,
    val popularScroll: ScrollPosition = ScrollPosition(),
    val searchScroll: ScrollPosition = ScrollPosition(),
    val tagsScroll: ScrollPosition = ScrollPosition(),
)

class WorksScreenModel : StateScreenModel<WorksScreenState>(WorksScreenState()) {
  fun selectTab(index: Int) {
    if (index == mutableState.value.selectedIndex) return
    mutableState.value = mutableState.value.copy(selectedIndex = index)
  }

  fun onPopularScrollChanged(index: Int, offset: Int) {
    mutableState.value = mutableState.value.copy(popularScroll = ScrollPosition(index, offset))
  }

  fun onSearchScrollChanged(index: Int, offset: Int) {
    mutableState.value = mutableState.value.copy(searchScroll = ScrollPosition(index, offset))
  }

  fun onTagsScrollChanged(index: Int, offset: Int) {
    mutableState.value = mutableState.value.copy(tagsScroll = ScrollPosition(index, offset))
  }
}
