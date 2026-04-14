package one.next.player.core.data.repository

import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import one.next.player.core.common.extensions.canonicalPathOrSelf
import one.next.player.core.common.extensions.toCanonicalFilePathOrNull
import one.next.player.core.data.mappers.toFolder
import one.next.player.core.data.mappers.toVideo
import one.next.player.core.data.mappers.toVideoState
import one.next.player.core.data.models.RemotePlaybackInfo
import one.next.player.core.data.models.VideoState
import one.next.player.core.database.converter.UriListConverter
import one.next.player.core.database.dao.DirectoryDao
import one.next.player.core.database.dao.MediumDao
import one.next.player.core.database.dao.MediumStateDao
import one.next.player.core.database.entities.MediumStateEntity
import one.next.player.core.database.relations.DirectoryWithMedia
import one.next.player.core.database.relations.MediumWithInfo
import one.next.player.core.media.services.MediaService
import one.next.player.core.media.sync.MediaSynchronizer
import one.next.player.core.model.Folder
import one.next.player.core.model.Video

class LocalMediaRepository @Inject constructor(
    private val mediumDao: MediumDao,
    private val mediumStateDao: MediumStateDao,
    private val directoryDao: DirectoryDao,
    private val mediaService: MediaService,
    private val mediaSynchronizer: MediaSynchronizer,
) : MediaRepository {

    override fun getVideosFlow(): Flow<List<Video>> = mediumDao.getAllWithInfo().map { media ->
        media.map(MediumWithInfo::toVideo)
    }

    override fun getVideosFlowFromFolderPath(folderPath: String): Flow<List<Video>> = mediumDao
        .getAllWithInfoFromDirectory(folderPath)
        .map { media ->
            media.map(MediumWithInfo::toVideo)
        }

    override fun getRecycleBinVideosFlow(): Flow<List<Video>> = mediumDao.getAllWithInfo().map { media ->
        media.filter { it.isMarkedInRecycleBin() }.map(MediumWithInfo::toVideo)
    }

    override fun getFoldersFlow(): Flow<List<Folder>> = directoryDao.getAllWithMedia().map { it.map(DirectoryWithMedia::toFolder) }

    override suspend fun getVideoByUri(uri: String): Video? = findMediumWithInfo(uri)?.toVideo()

    override suspend fun getVideoState(uri: String): VideoState? = mediumStateDao.get(resolveCanonicalMediaUri(uri))?.toVideoState()

    override suspend fun getVideoState(uris: List<String>): VideoState? {
        val canonicalUris = uris.map { candidateUri -> resolveCanonicalMediaUri(candidateUri) }.distinct()
        if (canonicalUris.isEmpty()) return null
        val stateByUri = mediumStateDao.getAll(canonicalUris).associateBy(MediumStateEntity::uriString)
        return canonicalUris.firstNotNullOfOrNull { canonicalUri ->
            stateByUri[canonicalUri]?.toVideoState()
        }
    }

    override suspend fun getCanonicalMediaUri(uri: String): String = resolveCanonicalMediaUri(uri)

    override suspend fun getRemotePlaybackStates(stateKeys: List<String>): Map<String, RemotePlaybackInfo> {
        if (stateKeys.isEmpty()) return emptyMap()
        return mediumStateDao.getAll(stateKeys).associate { entity ->
            entity.uriString to RemotePlaybackInfo(
                playbackPosition = entity.playbackPosition,
                lastPlayedTime = entity.lastPlayedTime,
            )
        }
    }

    override suspend fun updateMediumLastPlayedTime(uri: String, lastPlayedTime: Long) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                lastPlayedTime = lastPlayedTime,
            ),
        )
    }

    override suspend fun updateMediumPosition(uri: String, position: Long) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)
        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                playbackPosition = position,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                playbackSpeed = playbackSpeed,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                audioTrackIndex = audioTrackIndex,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                subtitleTrackIndex = subtitleTrackIndex,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateMediumZoom(uri: String, zoom: Float) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                videoScale = zoom,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)
        val currentExternalSubs = UriListConverter.fromStringToList(stateEntity.externalSubs)
        val newSubtitleCanonicalPath = subtitleUri.toCanonicalFilePathOrNull()
        val hasSameSubtitle = currentExternalSubs.any { existingSubtitleUri ->
            when {
                existingSubtitleUri == subtitleUri -> true
                newSubtitleCanonicalPath == null -> false
                else -> existingSubtitleUri.toCanonicalFilePathOrNull() == newSubtitleCanonicalPath
            }
        }
        if (hasSameSubtitle) return
        val newExternalSubs = UriListConverter.fromListToString(urlList = currentExternalSubs + subtitleUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                externalSubs = newExternalSubs,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateExternalSubs(uri: String, externalSubs: List<Uri>) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)
        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                externalSubs = UriListConverter.fromListToString(externalSubs),
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateSubtitleDelay(uri: String, delay: Long) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                subtitleDelayMilliseconds = delay,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateSubtitleSpeed(uri: String, speed: Float) {
        val canonicalMediaUri = resolveCanonicalMediaUri(uri)
        val stateEntity = mediumStateDao.get(canonicalMediaUri) ?: MediumStateEntity(uriString = canonicalMediaUri)

        mediumStateDao.upsert(
            mediumState = stateEntity.copy(
                subtitleSpeed = speed,
                lastPlayedTime = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun moveVideosToRecycleBin(uris: List<String>) {
        if (uris.isEmpty()) return

        uris.distinct().forEach { uriString ->
            val medium = mediumDao.get(uriString) ?: return@forEach
            val currentState = mediumStateDao.get(uriString) ?: MediumStateEntity(uriString = uriString)
            val moved = mediaService.moveMediaToRecycleBin(uriString.toUri()) ?: return@forEach
            val movedUriString = moved.uri.toString()

            if (movedUriString != uriString) {
                mediumDao.delete(listOf(uriString))
                mediumStateDao.delete(listOf(uriString))
            }

            mediumDao.upsert(
                medium.copy(
                    uriString = movedUriString,
                    path = moved.path,
                    parentPath = moved.parentPath,
                    name = moved.fileName,
                ),
            )

            mediumStateDao.upsert(
                currentState.copy(
                    uriString = movedUriString,
                    isInRecycleBin = true,
                    originalPath = currentState.originalPath ?: medium.path,
                    originalParentPath = currentState.originalParentPath ?: medium.parentPath,
                    originalFileName = currentState.originalFileName ?: medium.name,
                ),
            )
            mediaSynchronizer.refresh(moved.path)
        }
    }

    override suspend fun restoreVideosFromRecycleBin(uris: List<String>) {
        if (uris.isEmpty()) return

        uris.distinct().forEach { uriString ->
            val currentState = mediumStateDao.get(uriString) ?: return@forEach
            val medium = mediumDao.get(uriString) ?: return@forEach
            val originalPath = currentState.originalPath ?: return@forEach
            val originalFileName = currentState.originalFileName ?: return@forEach
            val restored = mediaService.restoreMediaFromRecycleBin(
                uri = uriString.toUri(),
                originalPath = originalPath,
                originalFileName = originalFileName,
            ) ?: return@forEach
            val restoredUriString = restored.uri.toString()

            if (restoredUriString != uriString) {
                mediumDao.delete(listOf(uriString))
                mediumStateDao.delete(listOf(uriString))
            }

            mediumDao.upsert(
                medium.copy(
                    uriString = restoredUriString,
                    path = restored.path,
                    parentPath = restored.parentPath,
                    name = restored.fileName,
                ),
            )

            mediumStateDao.upsert(
                currentState.copy(
                    uriString = restoredUriString,
                    isInRecycleBin = false,
                    originalPath = null,
                    originalParentPath = null,
                    originalFileName = null,
                ),
            )
            mediaSynchronizer.refresh(restored.path)
        }
    }

    private suspend fun findMediumWithInfo(uri: String): MediumWithInfo? {
        mediumDao.getWithInfo(uri)?.let { return it }
        val path = uri.toPathOrNull() ?: return null
        val canonicalPath = path.canonicalPathOrSelf()
        return mediumDao.getByPath(canonicalPath)?.let { medium ->
            mediumDao.getWithInfo(medium.uriString)
        }
    }

    private suspend fun resolveCanonicalMediaUri(uri: String): String {
        if (uri.isRemotePlaybackStateKey()) return uri
        val medium = findMediumWithInfo(uri) ?: return uri
        return medium.mediumEntity.uriString
    }

    private fun String.toPathOrNull(): String? {
        val parsed = toUri()
        val rawPath = when (parsed.scheme) {
            "file" -> parsed.path
            else -> null
        } ?: return null
        return File(rawPath).path
    }

    private fun MediumWithInfo.isMarkedInRecycleBin(): Boolean = mediumStateEntity?.isInRecycleBin == true
}
