package one.next.player.settings.screens.general

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.next.player.core.common.AppLanguageManager
import one.next.player.core.common.Logger
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.data.repository.SettingsBackupManager
import one.next.player.core.media.sync.MediaInfoSynchronizer
import one.next.player.core.model.SettingsBackup

@HiltViewModel
class GeneralPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
    private val settingsBackupManager: SettingsBackupManager,
) : ViewModel() {

    companion object {
        private const val TAG = "GeneralPreferencesViewModel"
    }

    private val uiStateInternal = MutableStateFlow(GeneralPreferencesUiState())
    val uiState = uiStateInternal.asStateFlow()

    fun onEvent(event: GeneralPreferencesUiEvent) {
        when (event) {
            is GeneralPreferencesUiEvent.ShowDialog -> showDialog(event.value)
            GeneralPreferencesUiEvent.ClearThumbnailCache -> clearThumbnailCache()
            GeneralPreferencesUiEvent.ResetSettings -> resetSettings()
            GeneralPreferencesUiEvent.BackupSettings -> backupSettings()
            GeneralPreferencesUiEvent.RestoreSettings -> restoreSettings()
            GeneralPreferencesUiEvent.ClearResultMessage -> clearResultMessage()
            is GeneralPreferencesUiEvent.OnBackupFileSelected -> onBackupFileSelected(event.context, event.uri)
            is GeneralPreferencesUiEvent.OnRestoreFileSelected -> onRestoreFileSelected(event.context, event.uri)
        }
    }

    private fun showDialog(value: GeneralPreferencesDialog?) {
        uiStateInternal.update { it.copy(showDialog = value) }
    }

    private fun clearThumbnailCache() {
        viewModelScope.launch {
            mediaInfoSynchronizer.clearThumbnailsCache()
        }
    }

    private fun backupSettings() {
        uiStateInternal.update { it.copy(pendingAction = GeneralPreferencesPendingAction.BackupSettings) }
    }

    private fun restoreSettings() {
        uiStateInternal.update { it.copy(pendingAction = GeneralPreferencesPendingAction.RestoreSettings) }
    }

    private fun onBackupFileSelected(context: Context, uri: Uri?) {
        uiStateInternal.update { it.copy(pendingAction = null) }
        if (uri == null) return

        viewModelScope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    settingsBackupManager.write(outputStream, preferencesRepository.exportSettings())
                } ?: error("Unable to open output stream")
            }.onSuccess {
                uiStateInternal.update { it.copy(resultMessage = GeneralPreferencesResultMessage.BackupSucceeded) }
            }.onFailure { throwable ->
                Logger.error(TAG, "Failed to back up settings", throwable)
                uiStateInternal.update { it.copy(resultMessage = GeneralPreferencesResultMessage.BackupFailed) }
            }
        }
    }

    private fun onRestoreFileSelected(context: Context, uri: Uri?) {
        uiStateInternal.update { it.copy(pendingAction = null) }
        if (uri == null) return

        viewModelScope.launch {
            runCatching {
                val settingsBackup = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    settingsBackupManager.read(inputStream)
                } ?: error("Unable to open input stream")
                require(settingsBackup.version == SettingsBackup.CURRENT_VERSION) {
                    "Unsupported backup version: ${settingsBackup.version}"
                }
                preferencesRepository.importSettings(settingsBackup)
                AppLanguageManager.applyToCurrent(settingsBackup.applicationPreferences.appLanguage)
            }.onSuccess {
                uiStateInternal.update { it.copy(resultMessage = GeneralPreferencesResultMessage.RestoreSucceeded) }
            }.onFailure { throwable ->
                Logger.error(TAG, "Failed to restore settings", throwable)
                uiStateInternal.update { it.copy(resultMessage = GeneralPreferencesResultMessage.RestoreFailed) }
            }
        }
    }

    private fun clearResultMessage() {
        uiStateInternal.update { it.copy(resultMessage = null) }
    }

    private fun resetSettings() {
        viewModelScope.launch {
            preferencesRepository.resetPreferences()
            AppLanguageManager.applyToCurrent("")
        }
    }
}

@Stable
data class GeneralPreferencesUiState(
    val showDialog: GeneralPreferencesDialog? = null,
    val pendingAction: GeneralPreferencesPendingAction? = null,
    val resultMessage: GeneralPreferencesResultMessage? = null,
)

sealed interface GeneralPreferencesPendingAction {
    data object BackupSettings : GeneralPreferencesPendingAction
    data object RestoreSettings : GeneralPreferencesPendingAction
}

sealed interface GeneralPreferencesResultMessage {
    data object BackupSucceeded : GeneralPreferencesResultMessage
    data object BackupFailed : GeneralPreferencesResultMessage
    data object RestoreSucceeded : GeneralPreferencesResultMessage
    data object RestoreFailed : GeneralPreferencesResultMessage
}

sealed interface GeneralPreferencesDialog {
    data object ClearThumbnailCacheDialog : GeneralPreferencesDialog
    data object ResetSettingsDialog : GeneralPreferencesDialog
}

sealed interface GeneralPreferencesUiEvent {
    data class ShowDialog(val value: GeneralPreferencesDialog?) : GeneralPreferencesUiEvent
    data class OnBackupFileSelected(val context: Context, val uri: Uri?) : GeneralPreferencesUiEvent
    data class OnRestoreFileSelected(val context: Context, val uri: Uri?) : GeneralPreferencesUiEvent
    data object ClearThumbnailCache : GeneralPreferencesUiEvent
    data object ResetSettings : GeneralPreferencesUiEvent
    data object BackupSettings : GeneralPreferencesUiEvent
    data object RestoreSettings : GeneralPreferencesUiEvent
    data object ClearResultMessage : GeneralPreferencesUiEvent
}
