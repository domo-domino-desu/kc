package ddd.kc.ui.pages.settings

import androidx.compose.runtime.Composable
import ddd.kc.data.local.settings.AppSettings
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.settings_error_api_key_empty
import kc.shared.generated.resources.settings_error_card_width_number
import kc.shared.generated.resources.settings_error_card_width_range
import kc.shared.generated.resources.settings_error_chunk_limit_number
import kc.shared.generated.resources.settings_error_chunk_limit_range
import kc.shared.generated.resources.settings_error_concurrency_number
import kc.shared.generated.resources.settings_error_concurrency_range
import kc.shared.generated.resources.settings_error_model_empty
import kc.shared.generated.resources.settings_error_openai_url_empty
import kc.shared.generated.resources.settings_error_openai_url_https
import kc.shared.generated.resources.settings_error_pawchive_url_empty
import kc.shared.generated.resources.settings_error_pawchive_url_https
import kc.shared.generated.resources.settings_error_pawchive_url_invalid
import kc.shared.generated.resources.settings_error_prompt_empty
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsValidationError.localizedMessage(): String =
    when (this) {
      SettingsValidationError.CARD_WIDTH_NOT_NUMBER ->
          stringResource(Res.string.settings_error_card_width_number)
      SettingsValidationError.CHUNK_LIMIT_NOT_NUMBER ->
          stringResource(Res.string.settings_error_chunk_limit_number)
      SettingsValidationError.CONCURRENCY_NOT_NUMBER ->
          stringResource(Res.string.settings_error_concurrency_number)
      SettingsValidationError.CARD_WIDTH_OUT_OF_RANGE ->
          stringResource(
              Res.string.settings_error_card_width_range,
              AppSettings.CELL_MIN_WIDTH_MIN,
              AppSettings.CELL_MIN_WIDTH_MAX,
          )
      SettingsValidationError.PAWCHIVE_URL_EMPTY ->
          stringResource(Res.string.settings_error_pawchive_url_empty)
      SettingsValidationError.PAWCHIVE_URL_NOT_HTTPS ->
          stringResource(Res.string.settings_error_pawchive_url_https)
      SettingsValidationError.PAWCHIVE_URL_INVALID ->
          stringResource(Res.string.settings_error_pawchive_url_invalid)
      SettingsValidationError.CHUNK_LIMIT_OUT_OF_RANGE ->
          stringResource(
              Res.string.settings_error_chunk_limit_range,
              AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MIN,
              AppSettings.TRANSLATION_CHUNK_WORD_LIMIT_MAX,
          )
      SettingsValidationError.CONCURRENCY_OUT_OF_RANGE ->
          stringResource(
              Res.string.settings_error_concurrency_range,
              AppSettings.TRANSLATION_MAX_CONCURRENCY_MIN,
              AppSettings.TRANSLATION_MAX_CONCURRENCY_MAX,
          )
      SettingsValidationError.OPENAI_URL_EMPTY ->
          stringResource(Res.string.settings_error_openai_url_empty)
      SettingsValidationError.OPENAI_URL_NOT_HTTPS ->
          stringResource(Res.string.settings_error_openai_url_https)
      SettingsValidationError.OPENAI_API_KEY_EMPTY ->
          stringResource(Res.string.settings_error_api_key_empty)
      SettingsValidationError.OPENAI_MODEL_EMPTY ->
          stringResource(Res.string.settings_error_model_empty)
      SettingsValidationError.OPENAI_PROMPT_EMPTY ->
          stringResource(Res.string.settings_error_prompt_empty)
    }
