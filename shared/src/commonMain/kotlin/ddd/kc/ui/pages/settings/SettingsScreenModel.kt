package ddd.kc.ui.pages.settings

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.local.settings.AppSettings
import ddd.kc.utils.coroutines.resultOfSuspend
import kotlinx.coroutines.launch

internal enum class SettingsSaveResult {
  SAVED,
  FAILED,
}

internal data class SettingsSaveState(
    val saving: Boolean = false,
    val result: SettingsSaveResult? = null,
)

internal class SettingsScreenModel(private val appSettings: AppSettings) :
    StateScreenModel<SettingsSaveState>(SettingsSaveState()) {

  fun clearResult() {
    mutableState.value = mutableState.value.copy(result = null)
  }

  fun save(draft: SettingsDraft, onSaved: (() -> Unit)? = null) {
    if (mutableState.value.saving || draft.validationError() != null) return
    mutableState.value = SettingsSaveState(saving = true)
    screenModelScope.launch {
      resultOfSuspend { appSettings.save(draft.toAppPreferences()) }
          .onSuccess {
            mutableState.value = SettingsSaveState(result = SettingsSaveResult.SAVED)
            onSaved?.invoke()
          }
          .onFailure { mutableState.value = SettingsSaveState(result = SettingsSaveResult.FAILED) }
    }
  }
}
