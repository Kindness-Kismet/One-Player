package one.next.player.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import one.next.player.core.common.Logger
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.data.repository.RemoteServerRepository
import one.next.player.core.media.sync.MediaSynchronizer
import one.next.player.core.model.RemoteServer
import one.next.player.core.model.ServerProtocol

class DebugCommandReceiver : BroadcastReceiver() {

    // Debug 广播直接读写仓库，供设备自动化测试注入云端服务器配置


    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Thread {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    DebugCommandReceiverEntryPoint::class.java,
                )
                runBlocking {
                    when (intent.action) {
                        ACTION_SET_IGNORE_NOMEDIA -> setIgnoreNoMedia(entryPoint.preferencesRepository(), intent)
                        ACTION_REFRESH_LIBRARY -> refreshLibrary(entryPoint.mediaSynchronizer())
                        ACTION_ADD_REMOTE_SERVER -> addRemoteServer(entryPoint.remoteServerRepository(), intent)
                        ACTION_DELETE_REMOTE_SERVER -> deleteRemoteServer(entryPoint.remoteServerRepository(), intent)
                        else -> Logger.info(TAG, "Ignored unknown debug action: ${intent.action}")
                    }
                }
            } catch (throwable: Throwable) {
                Logger.error(TAG, "Failed to handle debug command: ${intent.action}", throwable)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private suspend fun setIgnoreNoMedia(
        preferencesRepository: PreferencesRepository,
        intent: Intent,
    ) {
        if (!intent.hasExtra(EXTRA_ENABLED)) return

        val shouldIgnoreNoMediaFiles = intent.getBooleanExtra(EXTRA_ENABLED, false)
        preferencesRepository.updateApplicationPreferences {
            it.copy(shouldIgnoreNoMediaFiles = shouldIgnoreNoMediaFiles)
        }
        Logger.info(TAG, "shouldIgnoreNoMediaFiles set to $shouldIgnoreNoMediaFiles")
    }

    private suspend fun addRemoteServer(
        remoteServerRepository: RemoteServerRepository,
        intent: Intent,
    ) {
        val protocolName = intent.getStringExtra(EXTRA_PROTOCOL)?.uppercase() ?: return
        val protocol = ServerProtocol.entries.firstOrNull { it.name == protocolName } ?: return
        val host = intent.getStringExtra(EXTRA_HOST)?.trim().orEmpty()
        if (host.isBlank()) return

        val server = RemoteServer(
            id = intent.getLongExtra(EXTRA_ID, 0L),
            name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            protocol = protocol,
            host = host,
            port = intent.getIntExtra(EXTRA_PORT, 0).takeIf { it > 0 },
            path = intent.getStringExtra(EXTRA_PATH)?.ifBlank { "/" } ?: "/",
            username = intent.getStringExtra(EXTRA_USERNAME).orEmpty(),
            password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty(),
            isProxyEnabled = intent.getBooleanExtra(EXTRA_PROXY_ENABLED, false),
            proxyHost = intent.getStringExtra(EXTRA_PROXY_HOST).orEmpty(),
            proxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, 0).takeIf { it > 0 },
        )

        val id = if (server.id > 0) {
            remoteServerRepository.update(server)
            server.id
        } else {
            remoteServerRepository.insert(server)
        }
        Logger.info(TAG, "Upserted remote server id=$id protocol=${server.protocol} host=${server.host} path=${server.path}")
    }

    private suspend fun deleteRemoteServer(
        remoteServerRepository: RemoteServerRepository,
        intent: Intent,
    ) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0L) return
        remoteServerRepository.deleteById(id)
        Logger.info(TAG, "Deleted remote server id=$id")
    }

    private suspend fun refreshLibrary(
        mediaSynchronizer: MediaSynchronizer,
    ) {
        Logger.info(TAG, "refreshLibrary start")
        mediaSynchronizer.refresh(null)
        mediaSynchronizer.startSync()
        Logger.info(TAG, "Triggered media library refresh")
    }

    companion object {
        private const val TAG = "DebugCommandReceiver"
        const val ACTION_SET_IGNORE_NOMEDIA = "one.next.player.debug.SET_IGNORE_NOMEDIA"
        const val ACTION_REFRESH_LIBRARY = "one.next.player.debug.REFRESH_LIBRARY"
        const val ACTION_ADD_REMOTE_SERVER = "one.next.player.debug.ADD_REMOTE_SERVER"
        const val ACTION_DELETE_REMOTE_SERVER = "one.next.player.debug.DELETE_REMOTE_SERVER"

        const val EXTRA_ID = "id"
        const val EXTRA_NAME = "name"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_PATH = "path"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_PROXY_ENABLED = "proxy_enabled"
        const val EXTRA_PROXY_HOST = "proxy_host"
        const val EXTRA_PROXY_PORT = "proxy_port"
        const val EXTRA_ENABLED = "enabled"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugCommandReceiverEntryPoint {
    fun preferencesRepository(): PreferencesRepository
    fun mediaSynchronizer(): MediaSynchronizer
    fun remoteServerRepository(): RemoteServerRepository
}
