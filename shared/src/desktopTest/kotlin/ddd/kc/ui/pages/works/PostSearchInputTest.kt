package ddd.kc.ui.pages.works

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

class PostSearchInputTest {
  @get:Rule val ui = createComposeRule()

  @Test
  fun syntaxAppendsAtTheEndRefocusesAndWaitsForExplicitSubmit() {
    var query by mutableStateOf("fox")
    var submitted = 0
    ui.setContent {
      MaterialTheme { PostSearchInput(query, { query = it }, { submitted++ }, true) }
    }
    ui.onNodeWithTag("search-input").performClick().performTextInputSelection(TextRange(1))
    ui.onNodeWithTag("search-syntax-help").performClick()
    captureSyntaxDialog()
    ui.onNodeWithTag("search-syntax-intitle").performScrollTo().performClick()
    ui.onNodeWithTag("search-syntax-dialog").assertDoesNotExist()
    ui.onNodeWithTag("search-input").assertIsFocused().performTextInput("wolf")
    ui.runOnIdle {
      assertEquals("fox intitle:wolf", query)
      assertEquals(0, submitted)
    }
    ui.onNodeWithTag("search-submit").performClick()
    ui.runOnIdle { assertEquals(1, submitted) }
  }

  private fun captureSyntaxDialog() {
    val pixels = ui.onNodeWithTag("search-syntax-dialog").captureToImage().toPixelMap()
    val image = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
      image.setRGB(x, y, pixels[x, y].toArgb())
    }
    val file = File("build/reports/filter-samples/search-syntax-inline.png")
    file.parentFile.mkdirs()
    ImageIO.write(image, "png", file)
  }

  @Test
  fun operatorsAppendPrefixesAndFixedExamplesAppendCompleteValues() {
    var query by mutableStateOf("")
    var submitted = 0
    ui.setContent {
      MaterialTheme { PostSearchInput(query, { query = it }, { submitted++ }, true) }
    }
    val examples =
        listOf(
            "intitle" to "intitle:",
            "after" to "after:",
            "before" to "before:",
            "service-patreon" to "service:patreon",
            "service-fanbox" to "service:fanbox",
            "has-image" to "has:image",
            "has-video" to "has:video",
            "has-audio" to "has:audio",
            "has-archive" to "has:archive",
        )
    examples.forEach { (tag, _) ->
      ui.onNodeWithTag("search-syntax-help").performClick()
      ui.onNodeWithTag("search-syntax-$tag").performScrollTo().performClick()
      ui.onNodeWithTag("search-input").assertIsFocused()
    }
    ui.runOnIdle {
      assertEquals(examples.joinToString(" ") { it.second }, query)
      assertEquals(0, submitted)
    }
  }

  @Test
  fun quotedExamplesInsertBothQuotesAndTypingStartsBetweenThem() {
    var query by mutableStateOf("fox")
    var submitted = 0
    ui.setContent {
      MaterialTheme { PostSearchInput(query, { query = it }, { submitted++ }, true) }
    }
    for ((tag, expected) in
        listOf(
            "phrase" to "fox \"red fox\"",
            "exclude-phrase" to "fox -\"red fox\"",
            "title-phrase" to "fox intitle:\"red fox\"",
        )) {
      ui.runOnIdle { query = "fox" }
      ui.onNodeWithTag("search-syntax-help").performClick()
      ui.onNodeWithTag("search-syntax-$tag").performScrollTo().performClick()
      ui.onNodeWithTag("search-input").assertIsFocused().performTextInput("red fox")
      ui.runOnIdle {
        assertEquals(expected, query)
        assertEquals(0, submitted)
      }
    }
  }
}
