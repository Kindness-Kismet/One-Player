package one.next.player.core.data.repository

import kotlinx.coroutines.flow.StateFlow
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.model.SettingsBackup

interface PreferencesRepository {

    val applicationPreferences: StateFlow<ApplicationPreferences>

    val playerPreferences: StateFlow<PlayerPreferences>

    suspend fun updateApplicationPreferences(
        transform: suspend (ApplicationPreferences) -> ApplicationPreferences,
    )

    suspend fun updatePlayerPreferences(transform: suspend (PlayerPreferences) -> PlayerPreferences)

    suspend fun exportSettings(): SettingsBackup

    suspend fun importSettings(settingsBackup: SettingsBackup)

    suspend fun resetPreferences()
}
