package one.next.player.settings.screens.appearance

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
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.ThemeConfig

@HiltViewModel
class AppearancePreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val uiStateInternal = MutableStateFlow(
        AppearancePreferencesUiState(
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

    fun onEvent(event: AppearancePreferencesEvent) {
        when (event) {
            is AppearancePreferencesEvent.ShowDialog -> showDialog(event.value)
            AppearancePreferencesEvent.ToggleDarkTheme -> toggleDarkTheme()
            is AppearancePreferencesEvent.UpdateThemeConfig -> updateThemeConfig(event.themeConfig)
            is AppearancePreferencesEvent.UpdateAppLanguage -> updateAppLanguage(event.languageTag)
            AppearancePreferencesEvent.ToggleUseDynamicColors -> toggleUseDynamicColors()
            AppearancePreferencesEvent.ToggleNavigateHomeOnTitleLongPress -> toggleNavigateHomeOnTitleLongPress()
        }
    }

    private fun showDialog(value: AppearancePreferenceDialog?) {
        uiStateInternal.update {
            it.copy(showDialog = value)
        }
    }

    private fun toggleDarkTheme() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(
                    themeConfig = if (it.themeConfig == ThemeConfig.ON) ThemeConfig.OFF else ThemeConfig.ON,
                )
            }
        }
    }

    private fun updateThemeConfig(themeConfig: ThemeConfig) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(themeConfig = themeConfig)
            }
        }
    }

    private fun updateAppLanguage(languageTag: String) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(appLanguage = languageTag)
            }
            AppLanguageManager.applyToCurrent(languageTag)
        }
    }

    private fun toggleUseDynamicColors() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(shouldUseDynamicColors = !it.shouldUseDynamicColors)
            }
        }
    }

    private fun toggleNavigateHomeOnTitleLongPress() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(
                    shouldNavigateHomeOnTitleLongPress = !it.shouldNavigateHomeOnTitleLongPress,
                )
            }
        }
    }
}

@Stable
data class AppearancePreferencesUiState(
    val showDialog: AppearancePreferenceDialog? = null,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface AppearancePreferencesEvent {
    data class ShowDialog(val value: AppearancePreferenceDialog?) : AppearancePreferencesEvent
    data object ToggleDarkTheme : AppearancePreferencesEvent
    data class UpdateThemeConfig(val themeConfig: ThemeConfig) : AppearancePreferencesEvent
    data class UpdateAppLanguage(val languageTag: String) : AppearancePreferencesEvent
    data object ToggleUseDynamicColors : AppearancePreferencesEvent
    data object ToggleNavigateHomeOnTitleLongPress : AppearancePreferencesEvent
}

sealed interface AppearancePreferenceDialog {
    data object Theme : AppearancePreferenceDialog
    data object AppLanguage : AppearancePreferenceDialog
}
