package one.next.player.settings.screens.about

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.next.player.core.data.repository.AppUpdateChecker
import one.next.player.core.data.repository.PreferencesRepository

@HiltViewModel
class AboutPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val appUpdateChecker: AppUpdateChecker,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(AboutPreferencesUiState())
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                uiStateInternal.update {
                    it.copy(shouldCheckForUpdatesOnStartup = prefs.shouldCheckForUpdatesOnStartup)
                }
            }
        }
    }

    fun onEvent(event: AboutPreferencesUiEvent) {
        when (event) {
            is AboutPreferencesUiEvent.CheckForUpdates -> checkForUpdates(event.currentVersion)
            AboutPreferencesUiEvent.ToggleCheckOnStartup -> toggleCheckOnStartup()
            AboutPreferencesUiEvent.DismissStartupUpdateDialog -> dismissStartupUpdateDialog()
        }
    }

    fun maybeAutoCheck(currentVersion: String) {
        if (!uiStateInternal.value.shouldCheckForUpdatesOnStartup) return
        if (uiStateInternal.value.updateState != UpdateState.Idle) return
        checkForUpdates(currentVersion, fromStartup = true)
    }

    private fun checkForUpdates(currentVersion: String, fromStartup: Boolean = false) {
        if (uiStateInternal.value.updateState == UpdateState.Checking) return
        uiStateInternal.update { it.copy(updateState = UpdateState.Checking) }

        viewModelScope.launch {
            val updateInfo = appUpdateChecker.checkForUpdate(currentVersion)
            val result = when {
                updateInfo != null -> UpdateState.UpdateAvailable(
                    latestVersion = updateInfo.latestVersion,
                    releaseUrl = updateInfo.releaseUrl,
                )
                else -> UpdateState.UpToDate
            }
            uiStateInternal.update {
                it.copy(
                    updateState = result,
                    shouldShowStartupUpdateDialog = fromStartup && result is UpdateState.UpdateAvailable,
                )
            }
        }
    }

    private fun toggleCheckOnStartup() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldCheckForUpdatesOnStartup = !it.shouldCheckForUpdatesOnStartup)
            }
        }
    }

    private fun dismissStartupUpdateDialog() {
        uiStateInternal.update { it.copy(shouldShowStartupUpdateDialog = false) }
    }
}

@Stable
data class AboutPreferencesUiState(
    val updateState: UpdateState = UpdateState.Idle,
    val shouldCheckForUpdatesOnStartup: Boolean = false,
    val shouldShowStartupUpdateDialog: Boolean = false,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class UpdateAvailable(val latestVersion: String, val releaseUrl: String) : UpdateState
    data object Error : UpdateState
}

sealed interface AboutPreferencesUiEvent {
    data class CheckForUpdates(val currentVersion: String) : AboutPreferencesUiEvent
    data object ToggleCheckOnStartup : AboutPreferencesUiEvent
    data object DismissStartupUpdateDialog : AboutPreferencesUiEvent
}
