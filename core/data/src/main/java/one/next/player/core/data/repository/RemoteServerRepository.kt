package one.next.player.core.data.repository

import kotlinx.coroutines.flow.Flow
import one.next.player.core.model.RemoteServer

interface RemoteServerRepository {

    fun getAll(): Flow<List<RemoteServer>>

    suspend fun getById(id: Long): RemoteServer?

    suspend fun insert(server: RemoteServer): Long

    suspend fun update(server: RemoteServer)

    suspend fun deleteById(id: Long)
}
