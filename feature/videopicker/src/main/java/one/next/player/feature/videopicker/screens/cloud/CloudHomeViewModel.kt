package one.next.player.feature.videopicker.screens.cloud

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import one.next.player.core.data.repository.RemoteServerRepository
import one.next.player.core.model.RemoteServer
import one.next.player.core.model.ServerProtocol

@HiltViewModel
class CloudHomeViewModel @Inject constructor(
    private val repository: RemoteServerRepository,
) : ViewModel() {

    val uiState: StateFlow<CloudHomeUiState> = repository.getAll()
        .map { servers -> CloudHomeUiState(servers = servers) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CloudHomeUiState(),
        )

    fun onEvent(event: CloudHomeEvent) {
        when (event) {
            is CloudHomeEvent.SaveServer -> saveServer(event.server)
            is CloudHomeEvent.DeleteServer -> deleteServer(event.id)
        }
    }

    private fun saveServer(server: RemoteServer) {
        viewModelScope.launch {
            if (server.id == 0L) {
                repository.insert(server)
            } else {
                repository.update(server)
            }
        }
    }

    private fun deleteServer(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }
}

@Stable
data class CloudHomeUiState(
    val servers: List<RemoteServer> = emptyList(),
)

sealed interface CloudHomeEvent {
    data class SaveServer(val server: RemoteServer) : CloudHomeEvent
    data class DeleteServer(val id: Long) : CloudHomeEvent
}

// 新建服务器的默认模板
fun newServerTemplate(protocol: ServerProtocol) = RemoteServer(
    name = "",
    protocol = protocol,
    host = "",
    port = when (protocol) {
        ServerProtocol.WEBDAV -> null
        ServerProtocol.SMB -> 445
    },
    path = "/",
)
