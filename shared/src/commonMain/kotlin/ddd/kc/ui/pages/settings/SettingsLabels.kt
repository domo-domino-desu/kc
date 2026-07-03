package ddd.kc.ui.pages.settings

import androidx.compose.runtime.Composable
import ddd.kc.data.i18n.AppLanguage
import ddd.kc.data.settings.DownloadFileNameMode
import ddd.kc.data.settings.DownloadSubfolderMode
import ddd.kc.data.settings.ThemeMode
import ddd.kc.data.translation.TranslationProvider
import ddd.kc.data.translation.TranslationTargetLanguage
import kc.shared.generated.resources.Res
import kc.shared.generated.resources.download_file_name_mode_custom
import kc.shared.generated.resources.download_file_name_mode_id_title
import kc.shared.generated.resources.download_file_name_mode_username_id
import kc.shared.generated.resources.download_file_name_mode_username_id_title
import kc.shared.generated.resources.download_subfolder_mode_by_username
import kc.shared.generated.resources.download_subfolder_mode_flat
import kc.shared.generated.resources.language_en
import kc.shared.generated.resources.language_system
import kc.shared.generated.resources.language_zh
import kc.shared.generated.resources.theme_mode_dark
import kc.shared.generated.resources.theme_mode_light
import kc.shared.generated.resources.theme_mode_system
import kc.shared.generated.resources.translation_provider_google
import kc.shared.generated.resources.translation_provider_microsoft
import kc.shared.generated.resources.translation_provider_openai_compatible
import kc.shared.generated.resources.translation_target_english
import kc.shared.generated.resources.translation_target_zh_cn
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun themeModeLabel(mode: ThemeMode): String =
    when (mode) {
      ThemeMode.SYSTEM -> stringResource(Res.string.theme_mode_system)
      ThemeMode.LIGHT -> stringResource(Res.string.theme_mode_light)
      ThemeMode.DARK -> stringResource(Res.string.theme_mode_dark)
    }

@Composable
internal fun languageLabel(language: AppLanguage): String =
    when (language) {
      AppLanguage.SYSTEM -> stringResource(Res.string.language_system)
      AppLanguage.EN -> stringResource(Res.string.language_en)
      AppLanguage.ZH_HANS -> stringResource(Res.string.language_zh)
    }

@Composable
internal fun translationProviderLabel(provider: TranslationProvider): String =
    when (provider) {
      TranslationProvider.GOOGLE -> stringResource(Res.string.translation_provider_google)
      TranslationProvider.MICROSOFT -> stringResource(Res.string.translation_provider_microsoft)
      TranslationProvider.OPENAI_COMPATIBLE ->
          stringResource(Res.string.translation_provider_openai_compatible)
    }

@Composable
internal fun translationTargetLanguageLabel(language: TranslationTargetLanguage): String =
    when (language) {
      TranslationTargetLanguage.ZH_CN -> stringResource(Res.string.translation_target_zh_cn)
      TranslationTargetLanguage.EN -> stringResource(Res.string.translation_target_english)
    }

@Composable
internal fun downloadSubfolderModeLabel(mode: DownloadSubfolderMode): String =
    when (mode) {
      DownloadSubfolderMode.FLAT -> stringResource(Res.string.download_subfolder_mode_flat)
      DownloadSubfolderMode.BY_USERNAME ->
          stringResource(Res.string.download_subfolder_mode_by_username)
    }

@Composable
internal fun downloadFileNameModeLabel(mode: DownloadFileNameMode): String =
    when (mode) {
      DownloadFileNameMode.ID_TITLE -> stringResource(Res.string.download_file_name_mode_id_title)
      DownloadFileNameMode.USERNAME_ID ->
          stringResource(Res.string.download_file_name_mode_username_id)
      DownloadFileNameMode.USERNAME_ID_TITLE ->
          stringResource(Res.string.download_file_name_mode_username_id_title)
      DownloadFileNameMode.CUSTOM -> stringResource(Res.string.download_file_name_mode_custom)
    }
