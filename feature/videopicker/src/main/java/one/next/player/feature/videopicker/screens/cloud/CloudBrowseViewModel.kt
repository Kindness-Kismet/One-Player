package one.next.player.feature.videopicker.screens.cloud

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import one.next.player.core.common.Dispatcher
import one.next.player.core.common.NextDispatchers
import one.next.player.core.data.remote.SmbClient
import one.next.player.core.data.remote.WebDavClient
import one.next.player.core.data.repository.RemoteServerRepository
import one.next.player.core.model.RemoteFile
import one.next.player.core.model.RemoteServer
import one.next.player.core.model.ServerProtocol

@HiltViewModel
class CloudBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RemoteServerRepository,
    private val webDavClient: WebDavClient,
    private val smbClient: SmbClient,
    @Dispatcher(NextDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val serverId: Long = savedStateHandle["serverId"] ?: 0L
    private val initialPath: String = savedStateHandle["initialPath"] ?: "/"

    private val _uiState = MutableStateFlow(CloudBrowseUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadServer()
    }

    fun onEvent(event: CloudBrowseEvent) {
        when (event) {
            is CloudBrowseEvent.NavigateToDirectory -> navigateTo(event.path)
            CloudBrowseEvent.NavigateUp -> navigateUp()
            CloudBrowseEvent.Retry -> loadCurrentDirectory()
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
            _uiState.update { it.copy(server = server, currentPath = startPath) }
            loadCurrentDirectory()
        }
    }

    private fun navigateTo(path: String) {
        _uiState.update { it.copy(currentPath = path) }
        loadCurrentDirectory()
    }

    private fun navigateUp() {
        val current = _uiState.value.currentPath
        val server = _uiState.value.server ?: return
        val rootPath = server.path.ensureTrailingSlash()

        if (current.removeSuffix("/") == rootPath.removeSuffix("/")) return

        val parent = current.removeSuffix("/").substringBeforeLast('/').ensureTrailingSlash()
        _uiState.update { it.copy(currentPath = parent) }
        loadCurrentDirectory()
    }

    private fun loadCurrentDirectory() {
        val server = _uiState.value.server ?: return
        val path = _uiState.value.currentPath

        _uiState.update { it.copy(isLoading = true, isError = false) }

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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        files = files.filterBrowsableFiles(),
                        isError = false,
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
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
                        files = files.filterBrowsableFiles(),
                        isError = false,
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isError = true,
                        errorMessage = error.message ?: "Unknown error",
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

    fun buildCurrentDirectoryDocumentId(): String? {
        val server = _uiState.value.server ?: return null
        return "${server.id}|${Uri.encode(_uiState.value.currentPath)}"
    }

    fun buildAuthHeaders(): Map<String, String> {
        val server = _uiState.value.server ?: return emptyMap()
        return when (server.protocol) {
            ServerProtocol.WEBDAV -> buildMap {
                putAll(webDavClient.buildAuthHeaders(server))
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

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

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
    val isError: Boolean = false,
    val errorMessage: String = "",
)

sealed interface CloudBrowseEvent {
    data class NavigateToDirectory(val path: String) : CloudBrowseEvent
    data object NavigateUp : CloudBrowseEvent
    data object Retry : CloudBrowseEvent
}
