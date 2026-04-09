package one.next.player.feature.player

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.next.player.core.data.repository.ExternalSubtitleFontSource
import one.next.player.core.data.repository.MediaRepository
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.data.repository.SubtitleFontRepository
import one.next.player.core.data.repository.buildRemotePlaybackStateKey
import one.next.player.core.domain.GetSortedPlaylistUseCase
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.LoopMode
import one.next.player.core.model.PlayerControl
import one.next.player.core.model.PlayerControlsLayout
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.model.Video
import one.next.player.core.model.VideoContentScale
import one.next.player.feature.player.extensions.remoteFilePath
import one.next.player.feature.player.extensions.remoteProtocol
import one.next.player.feature.player.extensions.remoteServerId
import one.next.player.feature.player.state.SubtitleOptionsEvent
import one.next.player.feature.player.state.VideoZoomEvent

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val subtitleFontRepository: SubtitleFontRepository,
    private val getSortedPlaylistUseCase: GetSortedPlaylistUseCase,
) : ViewModel() {

    var shouldPlayWhenReady: Boolean = true

    private val internalUiState = MutableStateFlow(
        PlayerUiState(
            playerPreferences = preferencesRepository.playerPreferences.value,
            applicationPreferences = preferencesRepository.applicationPreferences.value,
            shouldPreventScreenshots = preferencesRepository.applicationPreferences.value.shouldPreventScreenshots,
            shouldHideInRecents = preferencesRepository.applicationPreferences.value.shouldHideInRecents,
        ),
    )
    val uiState = internalUiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.playerPreferences.collect { prefs ->
                internalUiState.update { it.copy(playerPreferences = prefs) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { prefs ->
                internalUiState.update {
                    it.copy(
                        applicationPreferences = prefs,
                        shouldPreventScreenshots = prefs.shouldPreventScreenshots,
                        shouldHideInRecents = prefs.shouldHideInRecents,
                    )
                }
            }
        }
        viewModelScope.launch {
            subtitleFontRepository.source.collect { source ->
                internalUiState.update { it.copy(externalSubtitleFontSource = source) }
            }
        }
    }

    suspend fun getPlaylistFromUri(uri: Uri): List<Video> = getSortedPlaylistUseCase.invoke(uri)

    suspend fun getVideoByUri(uri: String): Video? = mediaRepository.getVideoByUri(uri)

    fun updateVideoZoom(uri: String, zoom: Float) {
        viewModelScope.launch {
            mediaRepository.updateMediumZoom(uri, zoom)
        }
    }

    fun updatePlayerBrightness(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerBrightness = value) }
        }
    }

    fun updateVideoContentScale(contentScale: VideoContentScale) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(playerVideoZoom = contentScale) }
        }
    }

    fun setLoopMode(loopMode: LoopMode) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences { it.copy(loopMode = loopMode) }
        }
    }

    fun updatePlayerControlsCustomization(
        hiddenControls: Set<PlayerControl>,
        layout: PlayerControlsLayout,
    ) {
        viewModelScope.launch {
            preferencesRepository.updatePlayerPreferences {
                it.copy(
                    hiddenPlayerControls = hiddenControls,
                    playerControlsLayout = layout,
                )
            }
        }
    }

    fun onVideoZoomEvent(event: VideoZoomEvent) {
        when (event) {
            is VideoZoomEvent.ContentScaleChanged -> {
                updateVideoContentScale(event.contentScale)
            }
            is VideoZoomEvent.ZoomChanged -> {
                updateVideoZoom(event.mediaItem.resolvePlaybackStateUri(), event.zoom)
            }
        }
    }

    fun onSubtitleOptionEvent(event: SubtitleOptionsEvent) {
        when (event) {
            is SubtitleOptionsEvent.DelayChanged -> {
                updateSubtitleDelay(event.mediaItem.resolvePlaybackStateUri(), event.delay)
            }
            is SubtitleOptionsEvent.SpeedChanged -> {
                updateSubtitleSpeed(event.mediaItem.resolvePlaybackStateUri(), event.speed)
            }
        }
    }

    private fun updateSubtitleDelay(uri: String, delay: Long) {
        viewModelScope.launch {
            mediaRepository.updateSubtitleDelay(uri, delay)
        }
    }

    private fun updateSubtitleSpeed(uri: String, speed: Float) {
        viewModelScope.launch {
            mediaRepository.updateSubtitleSpeed(uri, speed)
        }
    }

    private fun MediaItem.resolvePlaybackStateUri(): String = buildRemotePlaybackStateKey(
        remoteProtocol = mediaMetadata.remoteProtocol,
        remoteServerId = mediaMetadata.remoteServerId,
        remoteFilePath = mediaMetadata.remoteFilePath,
    ) ?: mediaId
}

@Stable
data class PlayerUiState(
    val playerPreferences: PlayerPreferences? = null,
    val applicationPreferences: ApplicationPreferences = ApplicationPreferences(),
    val shouldPreventScreenshots: Boolean = false,
    val shouldHideInRecents: Boolean = false,
    val externalSubtitleFontSource: ExternalSubtitleFontSource? = null,
)

sealed interface PlayerEvent
