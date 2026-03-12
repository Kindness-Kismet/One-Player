package one.next.player.settings.screens.medialibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.model.ApplicationPreferences

@HiltViewModel
class MediaLibraryPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(MediaLibraryPreferencesUiState())
    val uiState: StateFlow<MediaLibraryPreferencesUiState> = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect {
                uiStateInternal.update { currentState ->
                    currentState.copy(preferences = it)
                }
            }
        }
    }

    fun onEvent(event: MediaLibraryPreferencesUiEvent) {
        when (event) {
            is MediaLibraryPreferencesUiEvent.SetIgnoreNoMediaFiles -> setIgnoreNoMediaFiles(event.enabled)
            MediaLibraryPreferencesUiEvent.ResetRestrictedFeatures -> resetRestrictedFeatures()
            MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia -> toggleMarkLastPlayedMedia()
            MediaLibraryPreferencesUiEvent.ToggleRecycleBinEnabled -> toggleRecycleBinEnabled()
        }
    }

    private fun setIgnoreNoMediaFiles(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                if (it.ignoreNoMediaFiles == enabled) {
                    it
                } else {
                    it.copy(ignoreNoMediaFiles = enabled)
                }
            }
        }
    }

    private fun toggleMarkLastPlayedMedia() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(markLastPlayedMedia = !it.markLastPlayedMedia)
            }
        }
    }

    private fun resetRestrictedFeatures() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                val shouldResetIgnoreNoMediaFiles = it.ignoreNoMediaFiles
                val shouldResetRecycleBin = it.recycleBinEnabled

                if (!shouldResetIgnoreNoMediaFiles && !shouldResetRecycleBin) {
                    it
                } else {
                    it.copy(
                        ignoreNoMediaFiles = false,
                        recycleBinEnabled = false,
                    )
                }
            }
        }
    }

    private fun toggleRecycleBinEnabled() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(recycleBinEnabled = !it.recycleBinEnabled)
            }
        }
    }
}

data class MediaLibraryPreferencesUiState(
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface MediaLibraryPreferencesUiEvent {
    data class SetIgnoreNoMediaFiles(val enabled: Boolean) : MediaLibraryPreferencesUiEvent
    data object ResetRestrictedFeatures : MediaLibraryPreferencesUiEvent
    data object ToggleMarkLastPlayedMedia : MediaLibraryPreferencesUiEvent
    data object ToggleRecycleBinEnabled : MediaLibraryPreferencesUiEvent
}
