@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package ddd.kc.desktop.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import ddd.kc.ui.components.UiTestState
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.delay

/** Suspending tests run on Tao's composition dispatcher; the native event loop stays responsive. */
internal class TaoUiDriver(private val adapter: TaoSceneAdapter) {
  var step: String = "startup"
    private set(value) {
      field = value
      println("E2E STEP $value")
    }

  var humanWaits: Int = 0
    private set

  private val timeoutMs = System.getProperty("e2e.timeoutSeconds", "90").toLong() * 1000
  private val humanTimeoutMs = System.getProperty("e2e.humanTimeoutSeconds", "900").toLong() * 1000
  private val scene
    get() = adapter.scene

  fun nodes(): List<SemanticsNode> {
    adapter.advanceFrame()
    return adapter.roots().asReversed().flatMap {
      it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
    }
  }

  fun find(tag: String): SemanticsNode? =
      nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag && visible(it) }

  private fun visible(node: SemanticsNode): Boolean {
    val bounds = node.boundsInWindow
    val size = scene.size ?: return false
    return !bounds.isEmpty &&
        bounds.right > 0 &&
        bounds.bottom > 0 &&
        bounds.left < size.width &&
        bounds.top < size.height
  }

  fun label(node: SemanticsNode): String =
      node.config
          .getOrNull(SemanticsProperties.ContentDescription)
          ?.joinToString(" ")
          ?.takeIf(String::isNotBlank)
          ?: node.config
              .getOrNull(SemanticsProperties.Text)
              ?.joinToString(" ") { it.text }
              ?.takeIf(String::isNotBlank)
          ?: node.children.joinToString(" ") { label(it) }

  fun state(tag: String): String = find(tag)?.config?.getOrNull(UiTestState).orEmpty()

  fun isFocused(tag: String): Boolean =
      find(tag)?.config?.getOrNull(SemanticsProperties.Focused) == true

  fun inputText(tag: String): String =
      find(tag)?.config?.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

  fun inputCursor(tag: String): Int? =
      find(tag)
          ?.config
          ?.getOrNull(SemanticsProperties.TextSelectionRange)
          ?.takeIf { it.collapsed }
          ?.start

  private fun challengeVisible(): Boolean =
      listOf("cf-challenge-status", "cf-challenge-overlay", "auth-screen", "auth-webview-panel")
          .any { find(it) != null }

  private suspend fun awaitHuman() {
    if (!challengeVisible()) return
    restoreManualFocus()
    humanWaits++
    val marker = File(System.getProperty("e2e.reportDir"), "human-required.txt")
    marker.parentFile.mkdirs()
    marker.writeText("Complete login / Cloudflare verification in the visible Tao window.\n")
    println("E2E HUMAN_REQUIRED: 请在桌面窗口完成登录 / Cloudflare 验证；测试会自动继续。")
    val deadline = System.nanoTime() + humanTimeoutMs * 1_000_000
    try {
      while (challengeVisible()) {
        check(System.nanoTime() < deadline) { "Manual login / Cloudflare verification timed out" }
        delay(250)
      }
      println("E2E HUMAN_COMPLETED")
      delay(300)
    } finally {
      marker.delete()
    }
  }

  suspend fun until(description: String, predicate: () -> Boolean) {
    step = description
    var remaining = timeoutMs * 1_000_000
    while (true) {
      awaitHuman()
      val start = System.nanoTime()
      if (predicate()) return
      if (remaining <= 0) {
        val knownTags =
            setOf(
                "nav-feed",
                "nav-browse",
                "nav-search",
                "nav-creators",
                "nav-works",
                "nav-dm",
                "nav-more",
                "auth-screen",
                "cf-challenge-status",
                "cf-challenge-overlay",
                "image-close",
                "browse-list",
                "page-menu",
                "detail-pager",
                "detail-scroll",
                "creator-content",
                "search-results",
            )
        val present =
            nodes()
                .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }
                .filter { it in knownTags }
                .distinct()
        println("E2E VISIBLE_TAGS " + present.joinToString(","))
        println("E2E SCENE_SIZE " + scene.size)
        for (tag in
            listOf(
                "browse-list",
                "page-menu",
                "detail-pager",
                "detail-scroll",
                "creator-content",
                "search-results",
                "image-pager",
                "zoom-image",
            )) {
          val safe =
              state(tag)
                  .split(';')
                  .filter { entry ->
                    val parts = entry.split('=', limit = 2)
                    parts.size == 2 &&
                        parts[0] in
                            setOf(
                                "index",
                                "offset",
                                "page",
                                "count",
                                "settled",
                                "scrolling",
                                "last",
                                "loading",
                                "expanded",
                                "scale",
                                "x",
                                "y",
                                "max",
                                "load",
                                "error",
                                "tab",
                                "pending",
                            ) &&
                        parts[1].matches(
                            Regex(
                                "[-0-9.eE+]+|true|false|null|Idle|Loading|Success|Error|None|Network|Http|Parse|RateLimited|Unauthorized|Forbidden|Unknown|POSTS|ANNOUNCEMENTS|TAGS|COMMUNITY"
                            )
                        )
                  }
                  .joinToString(";")
          if (safe.isNotEmpty()) println("E2E OBSERVE $tag $safe")
        }
        error("E2E timeout: $description")
      }
      delay(50)
      remaining -= System.nanoTime() - start
    }
  }

  suspend fun get(tag: String): SemanticsNode {
    until("find:$tag") { find(tag) != null }
    return checkNotNull(find(tag))
  }

  suspend fun click(tag: String) {
    get(tag)
    until("enabled:$tag") { find(tag)?.config?.contains(SemanticsProperties.Disabled) == false }
    clickNode(checkNotNull(find(tag)), "click:$tag")
  }

  fun enabled(tag: String): Boolean {
    val node = find(tag) ?: return false
    return node.config.getOrNull(SemanticsProperties.Disabled) == null
  }

  fun count(tag: String): Int =
      nodes().count { it.config.getOrNull(SemanticsProperties.TestTag) == tag && visible(it) }

  fun hasLabel(vararg labels: String): Boolean =
      nodes().any {
        visible(it) && label(it) in labels && it.config.getOrNull(SemanticsActions.OnClick) != null
      }

  suspend fun probe(seconds: Int = 5, predicate: () -> Boolean): Boolean {
    var remaining = seconds * 1000L
    while (remaining > 0) {
      awaitHuman()
      if (predicate()) return true
      delay(100)
      remaining -= 100
    }
    return predicate()
  }

  suspend fun clickNth(tag: String, index: Int) {
    get(tag)
    val candidates =
        nodes().filter { it.config.getOrNull(SemanticsProperties.TestTag) == tag && visible(it) }
    clickNode(candidates[index % candidates.size], "click-content-candidate")
  }

  suspend fun clickLabel(vararg labels: String) {
    until("find-label-control") {
      nodes().any {
        visible(it) && label(it) in labels && it.config.getOrNull(SemanticsActions.OnClick) != null
      }
    }
    val node =
        nodes().first {
          visible(it) &&
              label(it) in labels &&
              it.config.getOrNull(SemanticsActions.OnClick) != null
        }
    clickNode(node, "click-label-control")
  }

  private suspend fun settleFrames() {
    repeat(3) {
      delay(20)
      adapter.advanceFrame()
    }
  }

  private suspend fun clickNode(node: SemanticsNode, description: String) {
    step = description
    check(node.config.getOrNull(SemanticsProperties.Disabled) == null) {
      "E2E disabled control: $description"
    }
    val point = node.boundsInWindow.center
    val action = node.config.getOrNull(SemanticsActions.OnClick)?.action
    if (action != null) {
      check(action()) { "Compose click action failed" }
    } else {
      pointer(PointerEventType.Move, point)
      pointer(PointerEventType.Press, point, pressed = true)
      delay(35)
      pointer(PointerEventType.Release, point)
    }
    delay(350)
    settleFrames()
  }

  suspend fun type(tag: String, text: String) {
    step = "type:$tag"
    val node = get(tag)
    node.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
    check(
        node.config.getOrNull(SemanticsActions.SetText)?.action?.invoke(AnnotatedString(text)) ==
            true
    ) {
      "E2E text field rejected input: $tag"
    }
    delay(250)
    settleFrames()
    until("input-updated:$tag") {
      find(tag)?.config?.getOrNull(SemanticsProperties.EditableText)?.text == text
    }
  }

  suspend fun key(key: Key) {
    scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
    scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
    delay(400)
  }

  suspend fun root() {
    val navigation =
        listOf(
            "nav-feed",
            "nav-browse",
            "nav-search",
            "nav-creators",
            "nav-works",
            "nav-dm",
            "nav-more",
        )
    repeat(12) {
      if (find("image-close") != null) click("image-close")
      if (find("page-input") != null && hasLabel("Cancel", "取消")) clickLabel("Cancel", "取消")
      until("root-or-back-ready") {
        navigation.any { find(it) != null } || hasLabel("Back", "返回", "Go back", "Navigate back")
      }
      if (navigation.any { find(it) != null }) return
      back()
    }
    error("Could not restore the top-level navigation through the UI")
  }

  suspend fun back() = clickLabel("Back", "返回", "Go back", "Navigate back")

  private fun pointer(
      type: PointerEventType,
      position: Offset,
      pressed: Boolean = false,
      pointerType: PointerType = PointerType.Mouse,
  ) {
    adapter.advanceFrame()
    scene.sendPointerEvent(
        eventType = type,
        position = position,
        type = pointerType,
        timeMillis = System.nanoTime() / 1_000_000,
        buttons = PointerButtons(isPrimaryPressed = pressed),
        button =
            if (
                pointerType == PointerType.Mouse &&
                    (type == PointerEventType.Press || type == PointerEventType.Release)
            )
                PointerButton.Primary
            else null,
    )
  }

  suspend fun swipe(tag: String, forward: Boolean) {
    step = "swipe:$tag"
    val bounds = get(tag).boundsInWindow
    val start = Offset(bounds.left + bounds.width * if (forward) 0.8f else 0.2f, bounds.center.y)
    val end = Offset(bounds.left + bounds.width * if (forward) 0.2f else 0.8f, bounds.center.y)
    val pointerType = if (tag == "zoom-image") PointerType.Mouse else PointerType.Touch
    pointer(PointerEventType.Press, start, pressed = true, pointerType = pointerType)
    repeat(32) { i ->
      delay(16)
      val fraction = (i + 1) / 32f
      val remaining = 1f - fraction
      val eased = 1f - remaining * remaining * remaining
      pointer(
          PointerEventType.Move,
          start + (end - start) * eased,
          pressed = true,
          pointerType = pointerType,
      )
    }
    delay(80)
    pointer(PointerEventType.Release, end, pointerType = pointerType)
    delay(700)
  }

  suspend fun reveal(tag: String, scrollTag: String) {
    until("target-composed:$tag") {
      nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
    }
    repeat(30) {
      if (find(tag) != null) return
      val position = state(scrollTag)
      val offset = Regex("offset=(\\d+)").find(position)?.groupValues?.get(1)?.toIntOrNull()
      val maximum = Regex("max=(\\d+)").find(position)?.groupValues?.get(1)?.toIntOrNull()
      check(offset == null || maximum == null || offset < maximum) {
        "Target is not visible at the end of the scroll area"
      }
      scroll(scrollTag)
    }
    error("Target did not become visible through scrolling")
  }

  suspend fun scroll(tag: String = "browse-list", down: Boolean = true): String {
    until("initial-scroll-idle:$tag") { !state(tag).contains("scrolling=true") }
    val before = state(tag)
    val point = get(tag).boundsInWindow.center
    // Nested user headers consume wheel input before the content grid starts moving.
    for (batch in 0 until 5) {
      repeat(6) {
        scene.sendPointerEvent(
            PointerEventType.Scroll,
            point,
            scrollDelta = Offset(0f, if (down) 2f else -2f),
        )
        delay(50)
        adapter.advanceFrame()
      }
      if (state(tag) != before) break
    }
    until("scroll-changed:$tag") { state(tag).isNotBlank() && state(tag) != before }
    until("scroll-settled:$tag") { !state(tag).contains("scrolling=true") }
    println("E2E VIEWPORT $tag ${state(tag)}")
    return state(tag)
  }

  suspend fun scrollToStart(tag: String = "browse-list") {
    get(tag)
    repeat(30) {
      if (state(tag).contains("index=0;offset=0;scrolling=false")) {
        // At the list boundary, additional upward wheel input expands nested profile headers.
        val point = get(tag).boundsInWindow.center
        repeat(8) {
          scene.sendPointerEvent(PointerEventType.Scroll, point, scrollDelta = Offset(0f, -5f))
          delay(50)
          adapter.advanceFrame()
        }
        return
      }
      scroll(tag, down = false)
    }
    error("List did not reach its start through scrolling")
  }

  private fun contentFingerprint(): String {
    val content =
        nodes()
            .filter { visible(it) }
            .filter {
              it.config.getOrNull(SemanticsProperties.TestTag) in
                  setOf(
                      "post-card",
                      "creator-card",
                      "submission-waterfall-card",
                      "dm-card",
                      "community-card",
                  )
            }
            .map { label(it) }
            .filter(String::isNotBlank)
            .take(1)
            .joinToString("|")
    if (content.isBlank()) return ""
    // Only compare in memory. Never write account names, content titles or signed URLs to reports.
    return MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") {
      "%02x".format(it)
    }
  }

  private suspend fun expandPages(numbered: Boolean = true) {
    get("page-menu")
    if (!state("page-menu").contains("expanded=true")) click("page-toggle")
    get("page-next")
    if (numbered) get("page-current")
    until("page-ready") { state("page-menu").contains("loading=false") }
  }

  suspend fun pagination(jump: Boolean = true) {
    fun page(): Int? =
        Regex("page=(\\d+)").find(state("page-menu"))?.groupValues?.get(1)?.toIntOrNull()
    expandPages(jump)
    if (enabled("page-first")) {
      click("page-first")
      expandPages(jump)
      if (jump) until("first-page-ready") { page() == 1 }
    }
    if (!jump) {
      if (state("page-menu").contains("expanded=true")) click("page-toggle")
      scrollToStart()
      expandPages(false)
    }
    val originalPage = if (jump) checkNotNull(page()) else 0
    until("initial-live-content") { contentFingerprint().isNotEmpty() }
    val originalContent = contentFingerprint()
    click("page-next")
    expandPages(jump)
    until("next-page-and-content") {
      (!jump || page()?.let { it > originalPage } == true) &&
          contentFingerprint().let { it.isNotEmpty() && it != originalContent }
    }
    val secondPage = if (jump) checkNotNull(page()) else 0
    click("page-previous")
    expandPages(jump)
    if (!jump) {
      if (state("page-menu").contains("expanded=true")) click("page-toggle")
      scrollToStart()
      expandPages(false)
    }
    until("previous-page-restored") {
      if (jump) page() == originalPage else contentFingerprint() == originalContent
    }
    if (jump) {
      click("page-current")
      type("page-input", "3")
      click("page-confirm")
      expandPages()
      until("jump-to-third-page") { page() == 3 }
      click("page-previous")
      expandPages()
      until("jump-then-previous") { page() == secondPage }
      click("page-first")
      until("first-page-restored") {
        page() == 1 &&
            state("page-menu").contains("loading=false") &&
            !state("browse-list").contains("scrolling=true")
      }
      settleFrames()
    } else {
      check(find("page-current") == null) { "Cursor pagination must not expose a numbered jump" }
    }
    if (state("page-menu").contains("expanded=true")) click("page-toggle")
  }

  suspend fun pagerRoundtrip(tag: String = "detail-pager") {
    fun page(): Int? = Regex("page=(\\d+)").find(state(tag))?.groupValues?.get(1)?.toIntOrNull()
    fun idle(): Boolean = state(tag).contains("scrolling=false")
    until("pager-initial-idle") { idle() }
    val before = checkNotNull(page()) { "Pager position unavailable" }
    swipe(tag, forward = true)
    until("pager-forward-settled") { idle() && page() == before + 1 }
    swipe(tag, forward = false)
    until("pager-backward-settled") { idle() && page() == before }
  }

  suspend fun zoom() {
    get("zoom-image")
    until("original-image-loaded") { state("zoom-image").contains("Success") }
    val before = state("zoom-image")
    val point = get("zoom-image").boundsInWindow.center
    repeat(2) {
      pointer(PointerEventType.Press, point, pressed = true)
      delay(25)
      pointer(PointerEventType.Release, point)
      delay(75)
    }
    until("double-click-zoom") { state("zoom-image") != before }
    var last = state("zoom-image")
    var stable = 0
    until("zoom-animation-settled") {
      val current = state("zoom-image")
      stable = if (current == last) stable + 1 else 0
      last = current
      stable >= 3
    }
    val zoomed = state("zoom-image")
    val bounds = get("zoom-image").boundsInWindow
    val start = bounds.center
    // A long image can already be clamped against one edge after double-click zoom.
    for (direction in listOf(-1f, 1f)) {
      val end =
          Offset(
              start.x + direction * bounds.width * 0.15f,
              start.y + direction * bounds.height * 0.25f,
          )
      pointer(PointerEventType.Press, start, pressed = true)
      repeat(24) { i ->
        delay(16)
        pointer(PointerEventType.Move, start + (end - start) * ((i + 1) / 24f), pressed = true)
      }
      pointer(PointerEventType.Release, end)
      if (probe(2) { state("zoom-image") != zoomed }) break
    }
    until("zoom-pan") { state("zoom-image") != zoomed }
    click("image-close")
    until("zoom-closed") { find("zoom-image") == null }
  }
}
