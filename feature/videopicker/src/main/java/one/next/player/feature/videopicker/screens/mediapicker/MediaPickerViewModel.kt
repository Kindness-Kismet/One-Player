package one.next.player.feature.videopicker.screens.mediapicker

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.next.player.core.common.extensions.prettyName
import one.next.player.core.data.repository.MediaRepository
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.domain.GetSortedMediaUseCase
import one.next.player.core.media.services.MediaService
import one.next.player.core.media.sync.MediaInfoSynchronizer
import one.next.player.core.media.sync.MediaSynchronizer
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.Folder
import one.next.player.core.ui.base.DataState
import one.next.player.feature.videopicker.navigation.FolderArgs
import one.next.player.feature.videopicker.navigation.MediaPickerScreenMode

@HiltViewModel
class MediaPickerViewModel @Inject constructor(
    getSortedMediaUseCase: GetSortedMediaUseCase,
    savedStateHandle: SavedStateHandle,
    private val mediaService: MediaService,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
    private val mediaSynchronizer: MediaSynchronizer,
    private val snapshotCache: MediaPickerSnapshotCache,
) : ViewModel() {

    private val folderArgs = FolderArgs(savedStateHandle)

    val folderPath = folderArgs.folderId
    private val screenMode = folderArgs.screenMode

    private val initialPreferences = preferencesRepository.applicationPreferences.value
    private val initialMediaDataState: DataState<Folder?> = snapshotCache.get(folderPath, initialPreferences)
        ?.takeIf { screenMode == MediaPickerScreenMode.LIBRARY }
        ?.let { folder -> DataState.Success(folder) }
        ?: DataState.Loading

    private val uiStateInternal = MutableStateFlow(
        MediaPickerUiState(
            folderName = folderPath?.let { File(folderPath).prettyName },
            mediaDataState = initialMediaDataState,
            preferences = initialPreferences,
            screenMode = screenMode,
        ),
    )
    val uiState = uiStateInternal.asStateFlow()

    init {
        viewModelScope.launch {
            getSortedMediaUseCase.invoke(
                folderPath = folderPath,
                recycleBinOnly = screenMode == MediaPickerScreenMode.RECYCLE_BIN,
            ).collect { folder ->
                if (screenMode == MediaPickerScreenMode.LIBRARY) {
                    snapshotCache.put(folderPath, folder, uiStateInternal.value.preferences)
                }
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        mediaDataState = DataState.Success(folder),
                    )
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect {
                uiStateInternal.update { currentState ->
                    currentState.copy(
                        preferences = it,
                    )
                }
            }
        }
    }

    fun onEvent(event: MediaPickerUiEvent) {
        when (event) {
            is MediaPickerUiEvent.DeleteFolders -> permanentlyDeleteFolders(event.folders)
            is MediaPickerUiEvent.DeleteVideos -> permanentlyDeleteVideos(event.videos)
            is MediaPickerUiEvent.MoveVideosToRecycleBin -> moveVideosToRecycleBin(event.videos)
            is MediaPickerUiEvent.RestoreVideos -> restoreVideos(event.videos)
            is MediaPickerUiEvent.PermanentlyDeleteVideos -> permanentlyDeleteVideos(event.videos)
            is MediaPickerUiEvent.ShareVideos -> shareVideos(event.videos)
            is MediaPickerUiEvent.ExcludeFolders -> excludeFolders(event.paths)
            is MediaPickerUiEvent.Refresh -> refresh()
            is MediaPickerUiEvent.RenameVideo -> renameVideo(event.uri, event.to)
            is MediaPickerUiEvent.AddToSync -> addToMediaInfoSynchronizer(event.uri)
            is MediaPickerUiEvent.UpdateMenu -> updateMenu(event.preferences)
            is MediaPickerUiEvent.CacheFolderSnapshot -> cacheFolderSnapshot(event.folder)
        }
    }

    private fun permanentlyDeleteFolders(folders: List<Folder>) {
        viewModelScope.launch {
            val uris = folders.flatMap { folder ->
                folder.allMediaList.map { video ->
                    video.uriString.toUri()
                }
            }
            val deleted = mediaService.deleteMedia(uris)
            if (deleted) {
                mediaSynchronizer.refresh()
            }
        }
    }

    private fun permanentlyDeleteVideos(uris: List<String>) {
        viewModelScope.launch {
            val deleted = mediaService.deleteMedia(uris.map { it.toUri() })
            if (deleted) {
                mediaSynchronizer.refresh()
            }
        }
    }

    private fun moveVideosToRecycleBin(uris: List<String>) {
        viewModelScope.launch {
            mediaRepository.moveVideosToRecycleBin(uris)
        }
    }

    private fun restoreVideos(uris: List<String>) {
        viewModelScope.launch {
            mediaRepository.restoreVideosFromRecycleBin(uris)
        }
    }

    private fun shareVideos(uris: List<String>) {
        viewModelScope.launch {
            mediaService.shareMedia(uris.map { it.toUri() })
        }
    }

    private fun addToMediaInfoSynchronizer(uri: Uri) {
        mediaInfoSynchronizer.sync(uri)
    }

    private fun renameVideo(uri: Uri, to: String) {
        viewModelScope.launch {
            mediaService.renameMedia(uri, to)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            uiStateInternal.update { it.copy(refreshing = true) }
            mediaSynchronizer.refresh()
            uiStateInternal.update { it.copy(refreshing = false) }
        }
    }

    private fun updateMenu(preferences: ApplicationPreferences) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences { preferences }
        }
    }

    private fun excludeFolders(paths: List<String>) {
        viewModelScope.launch {
            preferencesRepository.updateApplicationPreferences {
                it.copy(excludeFolders = it.excludeFolders + paths.filter { path -> path !in it.excludeFolders })
            }
        }
    }

    private fun cacheFolderSnapshot(folder: Folder) {
        snapshotCache.put(folder.path, folder, uiStateInternal.value.preferences)
    }
}

@Stable
data class MediaPickerUiState(
    val folderName: String?,
    val mediaDataState: DataState<Folder?> = DataState.Loading,
    val refreshing: Boolean = false,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
    val screenMode: MediaPickerScreenMode = MediaPickerScreenMode.LIBRARY,
)

sealed interface MediaPickerUiEvent {
    data class DeleteVideos(val videos: List<String>) : MediaPickerUiEvent
    data class DeleteFolders(val folders: List<Folder>) : MediaPickerUiEvent
    data class MoveVideosToRecycleBin(val videos: List<String>) : MediaPickerUiEvent
    data class RestoreVideos(val videos: List<String>) : MediaPickerUiEvent
    data class PermanentlyDeleteVideos(val videos: List<String>) : MediaPickerUiEvent
    data class ShareVideos(val videos: List<String>) : MediaPickerUiEvent
    data class ExcludeFolders(val paths: List<String>) : MediaPickerUiEvent
    data object Refresh : MediaPickerUiEvent
    data class RenameVideo(val uri: Uri, val to: String) : MediaPickerUiEvent
    data class AddToSync(val uri: Uri) : MediaPickerUiEvent
    data class UpdateMenu(val preferences: ApplicationPreferences) : MediaPickerUiEvent
    data class CacheFolderSnapshot(val folder: Folder) : MediaPickerUiEvent
}
