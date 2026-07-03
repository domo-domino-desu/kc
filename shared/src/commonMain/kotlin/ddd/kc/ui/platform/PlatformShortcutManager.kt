package ddd.kc.ui.platform

import ddd.kc.data.model.Platform

expect class PlatformShortcutManager {
  fun canPinShortcuts(): Boolean

  fun pinShortcut(platform: Platform)
}
