package ddd.kc.ui.components

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/** Non-spoken observations for headful Compose tests; never replaces accessibility descriptions. */
val UiTestState = SemanticsPropertyKey<String>("UiTestState")
var SemanticsPropertyReceiver.uiTestState by UiTestState
