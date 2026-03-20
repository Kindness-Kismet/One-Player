package one.next.player.settings.screens.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.next.player.core.common.extensions.round
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.model.ControlButtonsPosition
import one.next.player.core.model.PlayerControl
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.model.Resume
import one.next.player.core.model.ScreenOrientation

@HiltViewModel
class PlayerPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        PlayerPreferencesUiState(
            preferences = preferencesRepository.playerPreferences.value,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { preferences ->
                uiStateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun onEvent(event: PlayerPreferencesUiEvent) {
        when (event) {
            is PlayerPreferencesUiEvent.ShowDialog -> showDialog(event.value)
            is PlayerPreferencesUiEvent.UpdatePlaybackResume -> updatePlaybackResume(event.resume)
            PlayerPreferencesUiEvent.ToggleAutoplay -> toggleAutoplay()
            PlayerPreferencesUiEvent.ToggleAutoPip -> toggleAutoPip()
            PlayerPreferencesUiEvent.ToggleAutoBackgroundPlay -> toggleAutoBackgroundPlay()
            PlayerPreferencesUiEvent.ToggleRememberBrightnessLevel -> toggleRememberBrightnessLevel()
            is PlayerPreferencesUiEvent.UpdatePreferredPlayerOrientation -> updatePreferredPlayerOrientation(event.value)
            is PlayerPreferencesUiEvent.UpdatePreferredControlButtonsPosition -> updatePreferredControlButtonsPosition(event.value)
            is PlayerPreferencesUiEvent.UpdateDefaultPlaybackSpeed -> updateDefaultPlaybackSpeed(event.value)
            is PlayerPreferencesUiEvent.UpdateControlAutoHideTimeout -> updateControlAutoHideTimeout(event.value)
            is PlayerPreferencesUiEvent.UpdateHiddenPlayerControls -> updateHiddenPlayerControls(event.value)
        }
    }

    private fun showDialog(value: PlayerPreferenceDialog?) {
        uiStateInternal.update {
            it.copy(showDialog = value)
        }
    }

    private fun updatePlaybackResume(resume: Resume) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(
                    resume = resume,
                )
            }
        }
    }

    private fun toggleAutoplay() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldAutoPlay = !it.shouldAutoPlay)
            }
        }
    }

    private fun toggleAutoPip() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldAutoEnterPip = !it.shouldAutoEnterPip)
            }
        }
    }

    private fun toggleAutoBackgroundPlay() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldAutoPlayInBackground = !it.shouldAutoPlayInBackground)
            }
        }
    }

    private fun toggleRememberBrightnessLevel() {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(shouldRememberPlayerBrightness = !it.shouldRememberPlayerBrightness)
            }
        }
    }

    private fun updatePreferredPlayerOrientation(value: ScreenOrientation) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(playerScreenOrientation = value)
            }
        }
    }

    private fun updatePreferredControlButtonsPosition(value: ControlButtonsPosition) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(controlButtonsPosition = value)
            }
        }
    }

    private fun updateDefaultPlaybackSpeed(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(defaultPlaybackSpeed = value.round(1))
            }
        }
    }

    private fun updateControlAutoHideTimeout(value: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(controllerAutoHideTimeout = value)
            }
        }
    }

    private fun updateHiddenPlayerControls(value: Set<PlayerControl>) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(hiddenPlayerControls = value)
            }
        }
    }
}

@Stable
data class PlayerPreferencesUiState(
    val showDialog: PlayerPreferenceDialog? = null,
    val preferences: PlayerPreferences = PlayerPreferences(),
)

sealed interface PlayerPreferenceDialog {
    data object ResumeDialog : PlayerPreferenceDialog
    data object PlayerScreenOrientationDialog : PlayerPreferenceDialog
    data object ControlButtonsDialog : PlayerPreferenceDialog
}

sealed interface PlayerPreferencesUiEvent {
    data class ShowDialog(val value: PlayerPreferenceDialog?) : PlayerPreferencesUiEvent
    data class UpdatePlaybackResume(val resume: Resume) : PlayerPreferencesUiEvent
    data object ToggleAutoplay : PlayerPreferencesUiEvent
    data object ToggleAutoPip : PlayerPreferencesUiEvent
    data object ToggleAutoBackgroundPlay : PlayerPreferencesUiEvent
    data object ToggleRememberBrightnessLevel : PlayerPreferencesUiEvent
    data class UpdatePreferredPlayerOrientation(val value: ScreenOrientation) : PlayerPreferencesUiEvent
    data class UpdatePreferredControlButtonsPosition(val value: ControlButtonsPosition) : PlayerPreferencesUiEvent
    data class UpdateDefaultPlaybackSpeed(val value: Float) : PlayerPreferencesUiEvent
    data class UpdateControlAutoHideTimeout(val value: Int) : PlayerPreferencesUiEvent
    data class UpdateHiddenPlayerControls(val value: Set<PlayerControl>) : PlayerPreferencesUiEvent
}
