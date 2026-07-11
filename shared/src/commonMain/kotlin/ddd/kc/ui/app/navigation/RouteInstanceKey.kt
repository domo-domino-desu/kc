package ddd.kc.ui.app.navigation

private var nextRouteInstanceId = 0

internal fun nextRouteInstanceKey(prefix: String): String {
  nextRouteInstanceId += 1
  return "$prefix:${nextRouteInstanceId}"
}
