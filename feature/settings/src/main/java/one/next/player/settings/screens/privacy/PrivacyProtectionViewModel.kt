package one.next.player.settings.screens.privacy

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.model.ApplicationPreferences

@HiltViewModel
class PrivacyProtectionViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        PrivacyProtectionUiState(
            preferences = preferencesRepository.applicationPreferences.value,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                uiStateInternal.update { it.copy(preferences = preferences) }
            }
        }
    }

    fun onEvent(event: PrivacyProtectionUiEvent) {
        when (event) {
            PrivacyProtectionUiEvent.TogglePreventScreenshots -> togglePreventScreenshots()
            PrivacyProtectionUiEvent.ToggleHideInRecents -> toggleHideInRecents()
        }
    }

    private fun togglePreventScreenshots() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldPreventScreenshots = !it.shouldPreventScreenshots)
            }
        }
    }

    private fun toggleHideInRecents() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldHideInRecents = !it.shouldHideInRecents)
            }
        }
    }
}

@Stable
data class PrivacyProtectionUiState(
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface PrivacyProtectionUiEvent {
    data object TogglePreventScreenshots : PrivacyProtectionUiEvent
    data object ToggleHideInRecents : PrivacyProtectionUiEvent
}
