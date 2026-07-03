package ddd.kc.ui.platform

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ddd.kc.data.model.Platform

const val EXTRA_PLATFORM = "extra_platform"

actual class PlatformShortcutManager(
    private val context: Context,
    private val appIconResId: Int,
) {

  actual fun canPinShortcuts(): Boolean =
      ShortcutManagerCompat.isRequestPinShortcutSupported(context)

  actual fun pinShortcut(platform: Platform) {
    if (!canPinShortcuts()) return
    val baseIntent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
          putExtra(EXTRA_PLATFORM, platform.name)
        } ?: return
    val shortcut =
        ShortcutInfoCompat.Builder(context, "pin_${platform.name.lowercase()}")
            .setShortLabel("Open ${platform.displayName}")
            .setLongLabel("Open ${platform.displayName}")
            .setIntent(baseIntent)
            .setIcon(IconCompat.createWithResource(context, appIconResId))
            .build()
    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
  }
}
