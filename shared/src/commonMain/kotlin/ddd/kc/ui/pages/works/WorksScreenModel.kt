package ddd.kc.ui.pages.works

import cafe.adriel.voyager.core.model.StateScreenModel
import ddd.kc.ui.state.ScrollPosition

data class WorksScreenState(
    val selectedIndex: Int = 0,
)

class WorksScreenModel : StateScreenModel<WorksScreenState>(WorksScreenState()) {
  var popularScroll: ScrollPosition = ScrollPosition()
    private set

  var searchScroll: ScrollPosition = ScrollPosition()
    private set

  var tagsScroll: ScrollPosition = ScrollPosition()
    private set

  fun selectTab(index: Int) {
    if (index == mutableState.value.selectedIndex) return
    mutableState.value = mutableState.value.copy(selectedIndex = index)
  }

  fun onPopularScrollChanged(index: Int, offset: Int) {
    popularScroll = ScrollPosition(index, offset)
  }

  fun onSearchScrollChanged(index: Int, offset: Int) {
    searchScroll = ScrollPosition(index, offset)
  }

  fun onTagsScrollChanged(index: Int, offset: Int) {
    tagsScroll = ScrollPosition(index, offset)
  }
}
