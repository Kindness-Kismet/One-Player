package one.next.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import one.next.player.core.database.entities.RemoteServerEntity

@Dao
interface RemoteServerDao {

    @Query("SELECT * FROM remote_server ORDER BY name ASC")
    fun getAll(): Flow<List<RemoteServerEntity>>

    @Query("SELECT * FROM remote_server WHERE id = :id")
    suspend fun getById(id: Long): RemoteServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RemoteServerEntity): Long

    @Update
    suspend fun update(entity: RemoteServerEntity)

    @Query("DELETE FROM remote_server WHERE id = :id")
    suspend fun deleteById(id: Long)
}
