package ddd.kc.platform

import ddd.kc.data.model.Platform

expect class PlatformShortcutManager {
  fun canPinShortcuts(): Boolean

  fun pinShortcut(platform: Platform)
}
