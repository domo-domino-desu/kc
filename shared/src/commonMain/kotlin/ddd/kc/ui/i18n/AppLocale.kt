package ddd.kc.ui.i18n

import androidx.compose.runtime.Composable

@Composable
expect fun ProvideAppLocale(
    localeTag: String?,
    content: @Composable () -> Unit,
)
