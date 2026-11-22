package ddd.kc.ui.components.forms

import androidx.compose.ui.graphics.vector.ImageVector

/** Controlled field values; the panel owns presentation, menus and summary formatting. */
sealed interface FilterField {
  val key: String
  val label: String
  val summary: String

  data class Dropdown<T>(
      override val key: String,
      override val label: String,
      val value: T,
      val options: List<FilterOption<T>>,
      val onChange: (T) -> Unit,
      val fallbackLabel: String = "",
  ) : FilterField {
    override val summary: String
      get() = options.firstOrNull { it.value == value }?.label ?: fallbackLabel
  }

  data class MultiSelect<T>(
      override val key: String,
      override val label: String,
      val value: Set<T>,
      val options: List<FilterOption<T>>,
      val emptyLabel: String,
      val onChange: (Set<T>) -> Unit,
  ) : FilterField {
    override val summary: String
      get() = options.filter { it.value in value }.joinToString { it.label }.ifBlank { emptyLabel }
  }

  data class Input(
      override val key: String,
      override val label: String,
      val value: String,
      val onChange: (String) -> Unit,
      val emptyLabel: String,
      val placeholder: String = "",
      val onSubmit: (() -> Unit)? = null,
      val submitIcon: ImageVector? = null,
      val submitEnabled: Boolean = true,
  ) : FilterField {
    override val summary: String
      get() = value.ifBlank { emptyLabel }
  }

  data class Tags(
      override val key: String,
      override val label: String,
      val value: List<String>,
      val onChange: (List<String>) -> Unit,
      val emptyLabel: String,
      val checkIcon: ImageVector,
      val confirmLabel: String = "Confirm",
      val cancelLabel: String = "Cancel",
      val suggestions: List<String> = emptyList(),
      val tagLabel: (String) -> String = { it },
  ) : FilterField {
    override val summary: String
      get() = value.joinToString { tagLabel(it) }.ifBlank { emptyLabel }
  }
}

data class FilterOption<T>(val value: T, val label: String)
