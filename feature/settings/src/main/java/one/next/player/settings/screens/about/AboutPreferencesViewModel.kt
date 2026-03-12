package one.next.player.settings.screens.about

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.next.player.core.common.Logger
import one.next.player.core.data.repository.PreferencesRepository
import org.json.JSONObject

@HiltViewModel
class AboutPreferencesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "AboutPreferencesViewModel"
        private const val RELEASES_URL =
            "https://api.github.com/repos/Kindness-Kismet/One-Player/releases/latest"
    }

    private val uiStateInternal = MutableStateFlow(AboutPreferencesUiState())
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                uiStateInternal.update {
                    it.copy(checkForUpdatesOnStartup = prefs.checkForUpdatesOnStartup)
                }
            }
        }
    }

    fun onEvent(event: AboutPreferencesUiEvent) {
        when (event) {
            is AboutPreferencesUiEvent.CheckForUpdates -> checkForUpdates(event.currentVersion)
            AboutPreferencesUiEvent.ToggleCheckOnStartup -> toggleCheckOnStartup()
        }
    }

    fun maybeAutoCheck(currentVersion: String) {
        if (!uiStateInternal.value.checkForUpdatesOnStartup) return
        if (uiStateInternal.value.updateState != UpdateState.Idle) return
        checkForUpdates(currentVersion)
    }

    private fun checkForUpdates(currentVersion: String) {
        if (uiStateInternal.value.updateState == UpdateState.Checking) return
        uiStateInternal.update { it.copy(updateState = UpdateState.Checking) }

        viewModelScope.launch {
            val result = fetchLatestRelease(currentVersion)
            uiStateInternal.update { it.copy(updateState = result) }
        }
    }

    private suspend fun fetchLatestRelease(currentVersion: String): UpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateState.Error
            }

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val release = JSONObject(json)
            val tagName = release.optString("tag_name", "").removePrefix("v")
            val htmlUrl = release.optString("html_url", "")
            if (tagName.isEmpty()) return@withContext UpdateState.Error

            if (compareVersions(tagName, currentVersion) > 0) {
                UpdateState.UpdateAvailable(latestVersion = tagName, releaseUrl = htmlUrl)
            } else {
                UpdateState.UpToDate
            }
        }.getOrElse { throwable ->
            Logger.error(TAG, "Failed to check for updates", throwable)
            UpdateState.Error
        }
    }

    private fun toggleCheckOnStartup() {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(checkForUpdatesOnStartup = !it.checkForUpdatesOnStartup)
            }
        }
    }
}

// 正数表示 v1 更新，负数表示 v2 更新
private fun compareVersions(v1: String, v2: String): Int {
    val parts1 = v1.split('.').map { it.toIntOrNull() ?: 0 }
    val parts2 = v2.split('.').map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(parts1.size, parts2.size)
    for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) return p1 - p2
    }
    return 0
}

@Stable
data class AboutPreferencesUiState(
    val updateState: UpdateState = UpdateState.Idle,
    val checkForUpdatesOnStartup: Boolean = false,
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
}
