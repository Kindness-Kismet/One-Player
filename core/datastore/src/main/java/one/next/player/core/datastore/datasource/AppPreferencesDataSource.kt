package one.next.player.core.datastore.datasource

import androidx.datastore.core.DataStore
import javax.inject.Inject
import one.next.player.core.common.Logger
import one.next.player.core.model.ApplicationPreferences

class AppPreferencesDataSource @Inject constructor(
    private val appPreferences: DataStore<ApplicationPreferences>,
) : PreferencesDataSource<ApplicationPreferences> {

    companion object {
        private const val TAG = "AppPreferencesDataSource"
    }

    override val preferences = appPreferences.data

    override suspend fun update(
        transform: suspend (ApplicationPreferences) -> ApplicationPreferences,
    ) {
        try {
            appPreferences.updateData(transform)
        } catch (ioException: Exception) {
            Logger.error(TAG, "Failed to update app preferences: $ioException")
        }
    }
}
