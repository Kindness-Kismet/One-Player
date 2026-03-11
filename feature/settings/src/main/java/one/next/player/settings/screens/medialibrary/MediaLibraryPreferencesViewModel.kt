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
            MediaLibraryPreferencesUiEvent.ToggleMarkLastPlayedMedia -> toggleMarkLastPlayedMedia()
            MediaLibraryPreferencesUiEvent.ToggleRecycleBinEnabled -> toggleRecycleBinEnabled()
            MediaLibraryPreferencesUiEvent.ToggleShowRecycleBinIcon -> toggleShowRecycleBinIcon()
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

    private fun toggleRecycleBinEnabled() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(recycleBinEnabled = !it.recycleBinEnabled)
            }
        }
    }

    private fun toggleShowRecycleBinIcon() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(showRecycleBinIcon = !it.showRecycleBinIcon)
            }
        }
    }
}

data class MediaLibraryPreferencesUiState(
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface MediaLibraryPreferencesUiEvent {
    data class SetIgnoreNoMediaFiles(val enabled: Boolean) : MediaLibraryPreferencesUiEvent
    data object ToggleMarkLastPlayedMedia : MediaLibraryPreferencesUiEvent
    data object ToggleRecycleBinEnabled : MediaLibraryPreferencesUiEvent
    data object ToggleShowRecycleBinIcon : MediaLibraryPreferencesUiEvent
}
