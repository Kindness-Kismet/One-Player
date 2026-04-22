package one.next.player.feature.videopicker.screens.cloud

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.next.player.core.common.Dispatcher
import one.next.player.core.common.NextDispatchers
import one.next.player.core.data.models.RemotePlaybackInfo
import one.next.player.core.data.remote.SmbClient
import one.next.player.core.data.remote.WebDavClient
import one.next.player.core.data.repository.MediaRepository
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.data.repository.RemoteServerRepository
import one.next.player.core.data.repository.buildRemoteFolderPlaybackAnchorKey
import one.next.player.core.data.repository.buildRemotePlaybackStateKey
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.RemoteFile
import one.next.player.core.model.RemoteServer
import one.next.player.core.model.ServerProtocol

@HiltViewModel
class CloudBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RemoteServerRepository,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val webDavClient: WebDavClient,
    private val smbClient: SmbClient,
    @Dispatcher(NextDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val serverId: Long = savedStateHandle["serverId"] ?: 0L
    private val initialPath: String = (savedStateHandle["initialPath"] as? String)?.let(Uri::decode) ?: "/"

    private val _uiState = MutableStateFlow(CloudBrowseUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.applicationPreferences.collect { preferences ->
                _uiState.update { it.copy(preferences = preferences) }
            }
        }
        loadServer()
    }

    fun onEvent(event: CloudBrowseEvent) {
        when (event) {
            CloudBrowseEvent.Retry -> loadCurrentDirectory(forceRefresh = true)
            CloudBrowseEvent.RefreshPlaybackStates -> loadPlaybackStates()
        }
    }

    private fun loadServer() {
        viewModelScope.launch {
            val server = repository.getById(serverId)
            if (server == null) {
                _uiState.update { it.copy(isError = true, errorMessage = "Server not found") }
                return@launch
            }
            val startPath = initialPath.takeIf { it != "/" || server.path == "/" } ?: server.path
            _uiState.update {
                it.copy(
                    server = server,
                    currentPath = startPath,
                    isAtRoot = isAtServerRoot(startPath, server.path),
                )
            }
            loadCurrentDirectory()
        }
    }

    private fun loadCurrentDirectory(forceRefresh: Boolean = false) {
        val currentState = _uiState.value
        val server = currentState.server ?: return
        val path = currentState.currentPath

        if (currentState.isLoading) return
        if (!forceRefresh && currentState.hasLoadedDirectory) return

        _uiState.update {
            it.copy(
                isLoading = true,
                isRefreshing = forceRefresh && currentState.hasLoadedDirectory,
                isError = false,
            )
        }

        viewModelScope.launch(ioDispatcher) {
            when (server.protocol) {
                ServerProtocol.WEBDAV -> loadWebDavDirectory(server, path)
                ServerProtocol.SMB -> loadSmbDirectory(server, path)
            }
        }
    }

    private suspend fun loadWebDavDirectory(server: RemoteServer, path: String) {
        webDavClient.listDirectory(server, path)
            .onSuccess { files ->
                val browsableFiles = files.filterBrowsableFiles()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        files = browsableFiles,
                        isError = false,
                        errorMessage = "",
                        playbackStates = emptyMap(),
                        restoreTargetFilePath = null,
                        hasLoadedDirectory = true,
                    )
                }
                loadPlaybackStates(
                    server = server,
                    directoryPath = path,
                    files = browsableFiles,
                )
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isError = true,
                        errorMessage = error.message ?: "Unknown error",
                    )
                }
            }
    }

    private suspend fun loadSmbDirectory(server: RemoteServer, path: String) {
        smbClient.listDirectory(server, path)
            .onSuccess { files ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        files = files.filterBrowsableFiles(),
                        isError = false,
                        errorMessage = "",
                        playbackStates = emptyMap(),
                        restoreTargetFilePath = null,
                        hasLoadedDirectory = true,
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isError = true,
                        errorMessage = error.message ?: "Unknown error",
                    )
                }
            }
    }

    private fun loadPlaybackStates(
        server: RemoteServer? = _uiState.value.server,
        directoryPath: String = _uiState.value.currentPath,
        files: List<RemoteFile> = _uiState.value.files,
    ) {
        val currentServer = server ?: return
        val videoFiles = files.filter { !it.isDirectory }
        if (videoFiles.isEmpty()) {
            _uiState.update {
                it.copy(
                    playbackStates = emptyMap(),
                    restoreTargetFilePath = null,
                )
            }
            return
        }

        val protocol = when (currentServer.protocol) {
            ServerProtocol.WEBDAV -> "webdav"
            ServerProtocol.SMB -> return
        }

        val pathToKey = videoFiles.mapNotNull { file ->
            val key = buildRemotePlaybackStateKey(
                remoteProtocol = protocol,
                remoteServerId = currentServer.id,
                remoteFilePath = file.path,
            ) ?: return@mapNotNull null
            file.path to key
        }

        viewModelScope.launch(ioDispatcher) {
            val stateKeys = pathToKey.map { it.second }
            val states = mediaRepository.getRemotePlaybackStates(stateKeys)
            val keyToPath = pathToKey.associate { (path, key) -> key to path }
            val playbackStates = states.entries.associate { (key, info) ->
                (keyToPath[key] ?: key) to info
            }
            val restoreTargetFilePath = buildRemoteFolderPlaybackAnchorKey(
                remoteProtocol = protocol,
                remoteServerId = currentServer.id,
                directoryPath = directoryPath,
            )?.let { anchorKey ->
                preferencesRepository.applicationPreferences.value.remoteFolderLastPlayedMediaPaths[anchorKey]
            }
            _uiState.update { currentState ->
                if (currentState.currentPath != directoryPath) {
                    return@update currentState
                }
                currentState.copy(
                    playbackStates = playbackStates,
                    restoreTargetFilePath = restoreTargetFilePath,
                )
            }
        }
    }

    fun buildPlayUrl(file: RemoteFile): String? {
        val server = _uiState.value.server ?: return null
        return when (server.protocol) {
            ServerProtocol.WEBDAV -> webDavClient.buildFileUrl(server, file.path)
            ServerProtocol.SMB -> {
                val port = server.port ?: 445
                "smb://${server.host}:$port${file.path}"
            }
        }
    }

    fun buildAllVideoPlayUrls(): List<Uri> {
        val server = _uiState.value.server ?: return emptyList()
        return _uiState.value.files
            .filter { !it.isDirectory }
            .mapNotNull { file ->
                when (server.protocol) {
                    ServerProtocol.WEBDAV -> webDavClient.buildFileUrl(server, file.path)
                    ServerProtocol.SMB -> {
                        val port = server.port ?: 445
                        "smb://${server.host}:$port${file.path}"
                    }
                }
            }
            .map { Uri.parse(it) }
    }

    fun buildCurrentDirectoryDocumentId(): String? {
        val server = _uiState.value.server ?: return null
        return "${server.id}|${Uri.encode(_uiState.value.currentPath)}"
    }

    fun buildAuthHeaders(file: RemoteFile): Map<String, String> {
        val server = _uiState.value.server ?: return emptyMap()
        return when (server.protocol) {
            ServerProtocol.WEBDAV -> buildMap {
                putAll(webDavClient.buildAuthHeaders(server))
                put("_remote_server_id", server.id.toString())
                put("_remote_file_path", file.path)
                put("_remote_protocol", "webdav")
                if (server.username.isNotBlank()) {
                    put("_webdav_username", server.username)
                    put("_webdav_password", server.password)
                }
            }

            ServerProtocol.SMB -> buildMap {
                if (server.username.isNotBlank()) {
                    put("_smb_username", server.username)
                    put("_smb_password", server.password)
                }
            }
        }
    }

    private fun List<RemoteFile>.filterBrowsableFiles(): List<RemoteFile> = filter { file ->
        file.isDirectory || file.hasBrowsableVideoExtension()
    }

    private fun RemoteFile.hasBrowsableVideoExtension(): Boolean {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        if (extension.isBlank()) return false
        return extension.lowercase() in BROWSABLE_VIDEO_EXTENSIONS
    }

    // PROPFIND href 可能是 URL 编码的，server.path 是用户输入的原始文本
    private fun isAtServerRoot(currentPath: String, serverPath: String): Boolean {
        val decodedCurrent = URLDecoder.decode(currentPath.removeSuffix("/"), "UTF-8")
        val decodedRoot = URLDecoder.decode(serverPath.removeSuffix("/"), "UTF-8")
        return decodedCurrent == decodedRoot
    }

    companion object {
        private val BROWSABLE_VIDEO_EXTENSIONS = setOf(
            "3gp",
            "avi",
            "flv",
            "m2ts",
            "m4v",
            "mkv",
            "mov",
            "mp4",
            "mts",
            "ts",
            "webm",
            "wmv",
        )
    }
}

@Stable
data class CloudBrowseUiState(
    val server: RemoteServer? = null,
    val currentPath: String = "/",
    val files: List<RemoteFile> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLoadedDirectory: Boolean = false,
    val isError: Boolean = false,
    val isAtRoot: Boolean = true,
    val errorMessage: String = "",
    val playbackStates: Map<String, RemotePlaybackInfo> = emptyMap(),
    val restoreTargetFilePath: String? = null,
    val preferences: ApplicationPreferences = ApplicationPreferences(),
)

sealed interface CloudBrowseEvent {
    data object Retry : CloudBrowseEvent
    data object RefreshPlaybackStates : CloudBrowseEvent
}
