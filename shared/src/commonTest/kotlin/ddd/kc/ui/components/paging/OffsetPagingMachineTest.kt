package ddd.kc.ui.components.paging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OffsetPagingMachineTest {
  private val machine = OffsetPagingMachine<Int, Int> { it }

  @Test
  fun consumesOnlyMatchingNavigationEffect() {
    val state =
        OffsetPagingState(
            items = listOf(1),
            navigationEffect = PagingEffect.ScrollToTop(transactionId = 7),
        )

    assertEquals(
        state,
        machine.consumeNavigationEffect(state, transactionId = 6),
    )
    assertNull(machine.consumeNavigationEffect(state, transactionId = 7).navigationEffect)
  }
}
