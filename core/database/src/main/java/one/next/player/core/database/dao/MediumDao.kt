package one.next.player.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import one.next.player.core.database.entities.AudioStreamInfoEntity
import one.next.player.core.database.entities.MediumEntity
import one.next.player.core.database.entities.SubtitleStreamInfoEntity
import one.next.player.core.database.entities.VideoStreamInfoEntity
import one.next.player.core.database.relations.MediumWithInfo

@Dao
interface MediumDao {

    @Upsert
    suspend fun upsert(medium: MediumEntity)

    @Upsert
    suspend fun upsertAll(media: List<MediumEntity>)

    @Query("SELECT * FROM media WHERE uri = :uri")
    suspend fun get(uri: String): MediumEntity?

    @Query("SELECT * FROM media WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): MediumEntity?

    @Query("SELECT * FROM media WHERE uri = :uri")
    fun getAsFlow(uri: String): Flow<MediumEntity?>

    @Query("SELECT * FROM media")
    fun getAll(): Flow<List<MediumEntity>>

    @Query("SELECT * FROM media WHERE parent_path = :directoryPath")
    fun getAllFromDirectory(directoryPath: String): Flow<List<MediumEntity>>

    @Transaction
    @Query("SELECT * FROM media WHERE uri = :uri")
    suspend fun getWithInfo(uri: String): MediumWithInfo?

    @Transaction
    @Query("SELECT * FROM media")
    fun getAllWithInfo(): Flow<List<MediumWithInfo>>

    @Transaction
    @Query("SELECT * FROM media WHERE parent_path = :directoryPath")
    fun getAllWithInfoFromDirectory(directoryPath: String): Flow<List<MediumWithInfo>>

    @Query("DELETE FROM media WHERE uri in (:uris)")
    suspend fun delete(uris: List<String>)

    @Upsert
    fun upsertVideoStreamInfo(videoStreamInfoEntity: VideoStreamInfoEntity)

    @Upsert
    fun upsertAudioStreamInfo(audioStreamInfoEntity: AudioStreamInfoEntity)

    @Upsert
    fun upsertSubtitleStreamInfo(subtitleStreamInfoEntity: SubtitleStreamInfoEntity)
}
