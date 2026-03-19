package one.next.player.core.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import one.next.player.core.database.dao.RemoteServerDao
import one.next.player.core.database.entities.RemoteServerEntity
import one.next.player.core.model.RemoteServer
import one.next.player.core.model.ServerProtocol

class LocalRemoteServerRepository @Inject constructor(
    private val dao: RemoteServerDao,
) : RemoteServerRepository {

    override fun getAll(): Flow<List<RemoteServer>> = dao.getAll().map { entities -> entities.map { it.toModel() } }

    override suspend fun getById(id: Long): RemoteServer? = dao.getById(id)?.toModel()

    override suspend fun insert(server: RemoteServer): Long = dao.insert(server.toEntity())

    override suspend fun update(server: RemoteServer) = dao.update(server.toEntity())

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}

private fun RemoteServerEntity.toModel() = RemoteServer(
    id = id,
    name = name,
    protocol = ServerProtocol.valueOf(protocol),
    host = host,
    port = port,
    path = path,
    username = username,
    password = password,
    isProxyEnabled = isProxyEnabled,
    proxyHost = proxyHost,
    proxyPort = proxyPort,
)

private fun RemoteServer.toEntity() = RemoteServerEntity(
    id = id,
    name = name,
    protocol = protocol.name,
    host = host,
    port = port,
    path = path,
    username = username,
    password = password,
    isProxyEnabled = isProxyEnabled,
    proxyHost = proxyHost,
    proxyPort = proxyPort,
)
