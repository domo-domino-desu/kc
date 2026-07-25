package ddd.kc.ui.pages.dm

import ddd.kc.ui.components.paging.ScrollPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class DmScreenModelTest {
  @Test
  fun preservesExactScrollPosition() {
    val model = DmScreenModel()

    model.onScrollPositionChanged(index = 37, offset = 128)

    assertEquals(ScrollPosition(index = 37, offset = 128), model.state.value.scrollPosition)
  }
}
