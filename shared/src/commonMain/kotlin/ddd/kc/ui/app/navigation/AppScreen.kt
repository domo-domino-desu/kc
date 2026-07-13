package ddd.kc.ui.app.navigation

import androidx.compose.runtime.compositionLocalOf
import cafe.adriel.voyager.core.screen.Screen

interface AppScreen : Screen

internal val LocalRootTabReselectRegistration = compositionLocalOf<(((() -> Unit)?) -> Unit)> { {} }
