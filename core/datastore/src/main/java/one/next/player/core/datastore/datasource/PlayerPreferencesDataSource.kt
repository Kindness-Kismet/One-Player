package one.next.player.core.datastore.datasource

import androidx.datastore.core.DataStore
import javax.inject.Inject
import one.next.player.core.common.Logger
import one.next.player.core.model.PlayerPreferences

class PlayerPreferencesDataSource @Inject constructor(
    private val preferencesDataStore: DataStore<PlayerPreferences>,
) : PreferencesDataSource<PlayerPreferences> {

    companion object {
        private const val TAG = "PlayerPreferencesDataSource"
    }

    override val preferences = preferencesDataStore.data

    override suspend fun update(transform: suspend (PlayerPreferences) -> PlayerPreferences) {
        try {
            preferencesDataStore.updateData(transform)
        } catch (ioException: Exception) {
            Logger.error(TAG, "Failed to update app preferences: $ioException")
        }
    }
}
