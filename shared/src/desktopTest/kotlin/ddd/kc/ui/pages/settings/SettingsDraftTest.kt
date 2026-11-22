package ddd.kc.ui.pages.settings

import ddd.kc.data.local.settings.AppLanguage
import ddd.kc.data.local.settings.DownloadFileNameMode
import ddd.kc.data.local.settings.DownloadSubfolderMode
import ddd.kc.data.local.settings.ThemeMode
import ddd.kc.data.model.IndexTranslateModel
import ddd.kc.data.model.TranslationProvider
import ddd.kc.data.model.TranslationTargetLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDraftTest {
  @Test
  fun indexTranslateDraftSavesModelWithoutOpenAiCredentials() {
    for (model in IndexTranslateModel.entries) {
      val edited =
          draft()
              .copy(
                  translationEnabled = true,
                  translationProvider = TranslationProvider.INDEX_TRANSLATE,
                  indexTranslateModel = model,
              )
      assertEquals(null, edited.validationError())
      assertEquals(model, edited.toTranslationSettings().indexTranslateModel)
      assertEquals(TranslationProvider.INDEX_TRANSLATE, edited.toTranslationSettings().provider)
    }
  }

  @Test
  fun automaticEndpointChangeMergesIntoUnrelatedUserDraft() {
    val previous = draft()
    val edited = previous.copy(cardWidthInput = "300")
    val redirected = previous.copy(pawchiveBaseUrl = "https://pawchive.pw")

    assertEquals(
        edited.copy(pawchiveBaseUrl = "https://pawchive.pw"),
        mergePersistedSettingsDraft(edited, previous, redirected),
    )
  }

  @Test
  fun explicitEndpointEditWinsOverAutomaticEndpointChange() {
    val previous = draft()
    val edited = previous.copy(pawchiveBaseUrl = "https://custom.example")
    val redirected = previous.copy(pawchiveBaseUrl = "https://pawchive.pw")

    assertEquals(edited, mergePersistedSettingsDraft(edited, previous, redirected))
  }

  private fun draft() =
      SettingsDraft(
          themeMode = ThemeMode.SYSTEM,
          language = AppLanguage.SYSTEM,
          cardWidthInput = "220",
          pawchiveBaseUrl = "https://pawchive.st",
          translationEnabled = false,
          translationProvider = TranslationProvider.GOOGLE,
          translationTargetLanguage = TranslationTargetLanguage.ZH_CN,
          chunkWordLimitInput = "1024",
          maxConcurrencyInput = "3",
          openAiBaseUrl = "https://api.openai.com/v1",
          openAiApiKey = "",
          openAiModel = "gpt-4o-mini",
          openAiPromptTemplate = "{text}",
          downloadSavePath = "",
          downloadAllowMediaIndexing = true,
          downloadSubfolderMode = DownloadSubfolderMode.FLAT,
          downloadFileNameMode = DownloadFileNameMode.ID_TITLE,
          downloadCustomFileNameTemplate = "{post_id}-{title}",
      )
}
