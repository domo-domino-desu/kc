package ddd.kc.platform

import ddd.kc.data.model.Platform

actual class PlatformShortcutManager {
  actual fun canPinShortcuts(): Boolean = false

  actual fun pinShortcut(platform: Platform) = Unit
}
