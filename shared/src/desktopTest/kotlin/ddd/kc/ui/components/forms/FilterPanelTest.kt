package ddd.kc.ui.components.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class FilterPanelTest {
  @get:Rule val ui = createComposeRule()

  @Test
  fun startsCollapsedTogglesAndKeepsAppValues() {
    ui.setContent { MaterialTheme { FilterSample(320) } }
    ui.onNodeWithContentDescription("AI").assertDoesNotExist()
    screenshot("collapsed-phone")
    ui.onNodeWithContentDescription("Open filters").assertHasClickAction().performClick()
    ui.onNodeWithContentDescription("Open filters").assertDoesNotExist()
    ui.onNodeWithContentDescription("AI").performClick()
    ui.onNodeWithText("Hide AI").performClick()
    ui.onNodeWithContentDescription("Filename").performTextInput("fox")
    val footer = ui.onNodeWithContentDescription("Close filters").fetchSemanticsNode().boundsInRoot
    val field = ui.onNodeWithTag("filename-cell").fetchSemanticsNode().boundsInRoot
    assertTrue(footer.top >= field.bottom)
    screenshot("expanded-phone")
    ui.onNodeWithContentDescription("Close filters").performClick()
    ui.onNodeWithContentDescription("AI").assertDoesNotExist()
    ui.onNodeWithContentDescription("Open filters").performClick()
    ui.onNodeWithText("fox").assertExists()
    ui.onNodeWithContentDescription("AI").assertExists()
  }

  @Test
  fun customActionDoesNotOpenDrawer() {
    var opens = 0
    ui.setContent {
      MaterialTheme {
        FilterPanelContent(
            listOf("Artwork"),
            funnel,
            chevron,
            "Open filters",
            expanded = false,
            onExpandedChange = { opens++ },
        ) {
          Text("Inline drawer")
        }
      }
    }
    repeat(2) { ui.onNodeWithContentDescription("Open filters").performClick() }
    ui.runOnIdle { assertEquals(2, opens) }
    ui.onNodeWithText("Inline drawer").assertDoesNotExist()
  }

  @Test
  fun narrowSummaryWrapsAndPairsShareTwoColumns() {
    ui.setContent { MaterialTheme { FilterSample(280) } }
    val first =
        ui.onNodeWithText("Patreon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val last =
        ui.onNodeWithText("Show AI", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue(last.top > first.top, "Summary pills should wrap on a narrow screen")
    ui.onNodeWithContentDescription("Open filters").performClick()
    val dropdown = ui.onNodeWithContentDescription("AI").fetchSemanticsNode().boundsInRoot
    val input = ui.onNodeWithContentDescription("Filename").fetchSemanticsNode().boundsInRoot
    assertEquals(dropdown.center.y, input.center.y, 0.5f)
    assertTrue(input.left > dropdown.right)
    screenshot("expanded-narrow")
  }

  @Test
  fun gridAdaptsFromOneToFourColumnsAndFillsAvailableWidth() {
    var width by mutableStateOf(136)
    ui.setContent { MaterialTheme { FilterSample(width) } }
    ui.onNodeWithContentDescription("Open filters").performClick()
    listOf(136 to 1, 280 to 2, 400 to 3, 720 to 4).forEach { (size, columns) ->
      ui.runOnIdle { width = size }
      val cells =
          listOf("platform", "sort", "ai", "filename").map {
            ui.onNodeWithTag("$it-cell").fetchSemanticsNode().boundsInRoot
          }
      assertEquals(
          columns,
          cells.count { kotlin.math.abs(it.center.y - cells.first().center.y) < 0.5f },
      )
      cells.forEach { assertTrue(it.width >= 120f) }
      assertEquals(size - 8f, cells[columns - 1].right, 0.5f)
      if (columns < cells.size) assertTrue(cells[columns].top > cells.first().bottom)
    }
  }

  @Test
  fun wideSample() {
    ui.setContent { MaterialTheme { FilterSample(720) } }
    screenshot("collapsed-desktop")
    ui.onNodeWithContentDescription("Open filters").performClick()
    screenshot("expanded-desktop")
  }

  @Test
  fun managedFieldsKeepSummaryInSyncWithSelectionAndInput() {
    ui.setContent {
      var ai by remember { mutableStateOf(false) }
      var query by remember { mutableStateOf("") }
      MaterialTheme {
        FilterPanel(
            fields =
                listOf(
                    FilterField.Dropdown(
                        "ai",
                        "AI",
                        ai,
                        listOf(FilterOption(false, "隐藏"), FilterOption(true, "显示")),
                        { ai = it },
                    ),
                    FilterField.Input("query", "搜索", query, { query = it }, "全部"),
                ),
            filterIcon = funnel,
            collapseIcon = chevron,
            openLabel = "Open filters",
            closeLabel = "Close filters",
        )
      }
    }
    ui.onNodeWithText("AI：隐藏", useUnmergedTree = true).assertExists()
    ui.onNodeWithContentDescription("Open filters").performClick()
    ui.onNodeWithText("AI：隐藏", useUnmergedTree = true).assertDoesNotExist()
    ui.onNodeWithContentDescription("AI").performClick()
    ui.onNodeWithText("显示").performClick()
    ui.onNodeWithContentDescription("搜索").performTextInput("fox")
    ui.onNodeWithContentDescription("Close filters").performClick()
    ui.onNodeWithText("AI：显示", useUnmergedTree = true).assertExists()
    ui.onNodeWithText("搜索：fox", useUnmergedTree = true).assertExists()
  }

  @Test
  fun tagFieldOpensDialogAndAppliesOrDiscardsSelection() {
    var included by mutableStateOf(listOf("fox"))
    ui.setContent {
      MaterialTheme {
        FilterPanel(
            fields =
                listOf(
                    FilterField.Tags(
                        key = "include",
                        label = "Species",
                        value = included,
                        onChange = { included = it },
                        emptyLabel = "Any",
                        checkIcon = check,
                        confirmLabel = "Confirm",
                        cancelLabel = "Cancel",
                        suggestions = listOf("fox", "wolf", "cat"),
                    )
                ),
            filterIcon = funnel,
            collapseIcon = chevron,
            openLabel = "Open filters",
            closeLabel = "Close filters",
        )
      }
    }
    ui.onNodeWithContentDescription("Open filters").performClick()
    // The field trigger shows the current selection, not a free-text box.
    ui.onNodeWithContentDescription("Species").assertHasClickAction().performClick()
    ui.onNodeWithText("wolf").assertExists()

    // Cancel discards the in-dialog change.
    ui.onNodeWithContentDescription("wolf").performClick()
    ui.onNodeWithText("Cancel").performClick()
    ui.runOnIdle { assertEquals(listOf("fox"), included) }

    // Confirm applies the toggled selection.
    ui.onNodeWithContentDescription("Species").performClick()
    ui.onNodeWithContentDescription("wolf").performClick()
    ui.onNodeWithContentDescription("fox").performClick()
    ui.onNodeWithText("Confirm").performClick()
    ui.runOnIdle { assertEquals(listOf("wolf"), included) }
  }

  private fun screenshot(name: String) {
    val pixels = ui.onNodeWithTag("sample").captureToImage().toPixelMap()
    val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until pixels.height) for (x in 0 until pixels.width) image.setRGB(
        x,
        y,
        pixels[x, y].toArgb(),
    )
    val file = File("build/reports/filter-samples/$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(image, "png", file)
  }
}

/** Runnable sample used for semantics checks and phone/desktop image review. */
@Composable
private fun FilterSample(width: Int) {
  var expanded by remember { mutableStateOf(false) }
  var ai by remember { mutableStateOf("Show AI") }
  var filename by remember { mutableStateOf("") }
  Column(Modifier.width(width.dp).testTag("sample")) {
    FilterPanelContent(
        listOf("Patreon", "Most popular · Descending", ai),
        funnel,
        chevron,
        "Open filters",
        "Close filters",
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
      FilterDropdownField("Platform", "Patreon", Modifier.testTag("platform-cell")) { dismiss ->
        DropdownMenuItem(text = { Text("Patreon") }, onClick = dismiss)
      }
      FilterDropdownField("Sort", "Most popular · Descending", Modifier.testTag("sort-cell")) {
          dismiss ->
        DropdownMenuItem(text = { Text("Most popular · Descending") }, onClick = dismiss)
      }
      FilterDropdownField("AI", ai, Modifier.testTag("ai-cell")) { dismiss ->
        listOf("Show AI", "Hide AI").forEach { option ->
          DropdownMenuItem(
              text = { Text(option) },
              onClick = {
                ai = option
                dismiss()
              },
          )
        }
      }
      FilterInputField(
          "Filename",
          filename,
          { filename = it },
          modifier = Modifier.testTag("filename-cell"),
          placeholder = "Search filenames",
      )
    }
  }
}

// Test fixture only; production consumers supply their SymbolCraft funnel.
private val funnel =
    ImageVector.Builder("Filter", 24.dp, 24.dp, 24f, 24f)
        .apply {
          path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 4f)
            lineTo(21f, 4f)
            lineTo(14f, 12f)
            lineTo(14f, 21f)
            lineTo(10f, 19f)
            lineTo(10f, 12f)
            close()
          }
        }
        .build()

private val chevron =
    ImageVector.Builder("Collapse", 24.dp, 24.dp, 24f, 24f)
        .apply {
          path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 14f)
            lineTo(12f, 8f)
            lineTo(18f, 14f)
            lineTo(16.6f, 15.4f)
            lineTo(12f, 10.8f)
            lineTo(7.4f, 15.4f)
            close()
          }
        }
        .build()

// Test fixture only; production consumers supply their SymbolCraft check mark.
private val check =
    ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f)
        .apply {
          path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 16.2f)
            lineTo(4.8f, 12f)
            lineTo(3.4f, 13.4f)
            lineTo(9f, 19f)
            lineTo(21f, 7f)
            lineTo(19.6f, 5.6f)
            close()
          }
        }
        .build()
