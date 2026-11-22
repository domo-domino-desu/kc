package ddd.kc.ui.pages.works

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ddd.kc.generated.symbols.icons.materialsymbols.Icons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.InfoW400Outlined
import ddd.kc.generated.symbols.icons.materialsymbols.icons.SearchW400Outlined
import ddd.kc.ui.components.uiTestState
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.close
import kc.shared.generated.resources.search_action
import kc.shared.generated.resources.search_syntax_dates
import kc.shared.generated.resources.search_syntax_exclusions
import kc.shared.generated.resources.search_syntax_help
import kc.shared.generated.resources.search_syntax_media
import kc.shared.generated.resources.search_syntax_phrase
import kc.shared.generated.resources.search_syntax_service
import kc.shared.generated.resources.search_syntax_title
import kc.shared.generated.resources.search_syntax_words
import kc.shared.generated.resources.search_works_hint
import org.jetbrains.compose.resources.stringResource

/** Keep manually entered syntax intact, separating an appended token from the previous term. */
internal fun appendSearchToken(query: String, token: String): String =
    query + (if (query.isNotEmpty() && !query.last().isWhitespace()) " " else "") + token

@Composable
internal fun PostSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
) {
  val focusRequester = remember { FocusRequester() }
  var input by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
  var showHelp by rememberSaveable { mutableStateOf(false) }
  var focusAfterHelp by remember { mutableStateOf(false) }
  LaunchedEffect(value) {
    if (input.text != value) input = TextFieldValue(value, TextRange(value.length))
  }
  LaunchedEffect(showHelp, focusAfterHelp) {
    if (!showHelp && focusAfterHelp) {
      // Let the real dialog window dispose and return focus to the application's scene.
      withFrameNanos {}
      focusRequester.requestFocus()
      focusAfterHelp = false
    }
  }
  OutlinedTextField(
      value = input,
      onValueChange = {
        input = it
        onValueChange(it.text)
      },
      placeholder = { Text(stringResource(Res.string.search_works_hint)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("search-input").focusRequester(focusRequester),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { if (canSubmit) onSubmit() }),
      leadingIcon = {
        IconButton(
            onClick = { showHelp = true },
            modifier = Modifier.testTag("search-syntax-help"),
        ) {
          Icon(Icons.InfoW400Outlined, stringResource(Res.string.search_syntax_help))
        }
      },
      trailingIcon = {
        IconButton(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier.testTag("search-submit"),
        ) {
          Icon(Icons.SearchW400Outlined, stringResource(Res.string.search_action))
        }
      },
  )
  if (showHelp) {
    SearchSyntaxDialog(
        onDismiss = { showHelp = false },
        onTokenSelected = { token, caretOffset ->
          val next = appendSearchToken(input.text, token)
          input = TextFieldValue(next, TextRange(next.length - token.length + caretOffset))
          onValueChange(next)
          focusAfterHelp = true
          showHelp = false
        },
    )
  }
}

@Composable
private fun SearchSyntaxDialog(onDismiss: () -> Unit, onTokenSelected: (String, Int) -> Unit) {
  val scroll = rememberScrollState()
  AlertDialog(
      modifier = Modifier.testTag("search-syntax-dialog"),
      onDismissRequest = onDismiss,
      title = { Text(stringResource(Res.string.search_syntax_help)) },
      text = {
        Column(
            modifier =
                Modifier.testTag("search-syntax-content").verticalScroll(scroll).semantics {
                  uiTestState =
                      "offset=${scroll.value};max=${scroll.maxValue};scrolling=${scroll.isScrollInProgress}"
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          SyntaxRow(stringResource(Res.string.search_syntax_words)) {
            Text("asuka rei", fontFamily = FontFamily.Monospace)
          }
          SyntaxRow(stringResource(Res.string.search_syntax_phrase)) {
            SyntaxExample("\"", "exact phrase\"", onTokenSelected, "phrase", "\"\"", 1)
          }
          SyntaxRow(stringResource(Res.string.search_syntax_exclusions)) {
            SyntaxExample("-", "word", onTokenSelected)
            SyntaxExample("-\"", "a phrase\"", onTokenSelected, "exclude-phrase", "-\"\"", 2)
          }
          SyntaxRow(stringResource(Res.string.search_syntax_title)) {
            SyntaxExample("intitle:", "word", onTokenSelected, "intitle")
            SyntaxExample(
                "intitle:",
                "\"a phrase\"",
                onTokenSelected,
                "title-phrase",
                "intitle:\"\"",
                "intitle:\"".length,
            )
            SyntaxExample("-intitle:", "word", onTokenSelected)
          }
          SyntaxRow(stringResource(Res.string.search_syntax_dates)) {
            SyntaxExample("after:", "2026-01-01", onTokenSelected, "after")
            SyntaxExample("before:", "2026-06", onTokenSelected, "before")
          }
          SyntaxRow(stringResource(Res.string.search_syntax_service)) {
            SyntaxExample(
                "service:",
                "patreon",
                onTokenSelected,
                "service-patreon",
                "service:patreon",
            )
            SyntaxExample("service:", "fanbox", onTokenSelected, "service-fanbox", "service:fanbox")
          }
          SyntaxRow(stringResource(Res.string.search_syntax_media)) {
            SyntaxExample("has:", "image", onTokenSelected, "has-image", "has:image")
            SyntaxExample("has:", "video", onTokenSelected, "has-video", "has:video")
            SyntaxExample("has:", "audio", onTokenSelected, "has-audio", "has:audio")
            SyntaxExample("has:", "archive", onTokenSelected, "has-archive", "has:archive")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
      },
  )
}

@Composable
private fun SyntaxRow(description: String, examples: @Composable () -> Unit) {
  Column {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      examples()
    }
    Text(description, style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun SyntaxExample(
    prefix: String,
    example: String,
    onTokenSelected: (String, Int) -> Unit,
    tag: String? = null,
    insertion: String = prefix,
    caretOffset: Int = insertion.length,
) {
  Text(
      prefix + example,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.primary,
      textDecoration = TextDecoration.Underline,
      modifier =
          (if (tag == null) Modifier else Modifier.testTag("search-syntax-$tag")).clickable {
            onTokenSelected(insertion, caretOffset)
          },
  )
}
