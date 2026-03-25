package one.next.player.core.data.repository.fake

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import one.next.player.core.data.models.VideoState
import one.next.player.core.data.repository.MediaRepository
import one.next.player.core.model.Folder
import one.next.player.core.model.Video

class FakeMediaRepository : MediaRepository {

    val videos = mutableListOf<Video>()
    val directories = mutableListOf<Folder>()
    private val recycleBinUris = mutableSetOf<String>()
    private val originalPaths = mutableMapOf<String, String>()

    override fun getVideosFlow(): Flow<List<Video>> = flowOf(
        videos.map { video ->
            video.copy(isInRecycleBin = video.uriString in recycleBinUris)
        },
    )

    override fun getVideosFlowFromFolderPath(folderPath: String): Flow<List<Video>> = flowOf(
        videos.filter { it.parentPath == folderPath }.map { video ->
            video.copy(isInRecycleBin = video.uriString in recycleBinUris)
        },
    )

    override fun getRecycleBinVideosFlow(): Flow<List<Video>> = flowOf(
        videos.filter { it.uriString in recycleBinUris }.map { it.copy(isInRecycleBin = true) },
    )

    override fun getFoldersFlow(): Flow<List<Folder>> = flowOf(
        directories.map { folder ->
            folder.copy(
                mediaList = folder.mediaList.map { video ->
                    video.copy(isInRecycleBin = video.uriString in recycleBinUris)
                },
            )
        },
    )

    override suspend fun getVideoByUri(uri: String): Video? = videos.find { video ->
        video.uriString == uri || video.path == uri
    }

    override suspend fun getVideoState(uri: String): VideoState? = null

    override suspend fun getVideoState(uris: List<String>): VideoState? = null

    override suspend fun getCanonicalMediaUri(uri: String): String = videos.find { video ->
        video.uriString == uri || video.path == uri
    }?.uriString ?: uri

    override suspend fun updateMediumLastPlayedTime(uri: String, lastPlayedTime: Long) {
    }

    override suspend fun updateMediumPosition(uri: String, position: Long) {
    }

    override suspend fun updateMediumPlaybackSpeed(uri: String, playbackSpeed: Float) {
    }

    override suspend fun updateMediumAudioTrack(uri: String, audioTrackIndex: Int) {
    }

    override suspend fun updateMediumSubtitleTrack(uri: String, subtitleTrackIndex: Int) {
    }

    override suspend fun updateMediumZoom(uri: String, zoom: Float) {
    }

    override suspend fun addExternalSubtitleToMedium(uri: String, subtitleUri: Uri) {
    }

    override suspend fun updateExternalSubs(uri: String, externalSubs: List<Uri>) {
    }

    override suspend fun updateSubtitleDelay(uri: String, delay: Long) {
    }

    override suspend fun updateSubtitleSpeed(uri: String, speed: Float) {
    }

    override suspend fun moveVideosToRecycleBin(uris: List<String>) {
        uris.forEach { uri ->
            val video = videos.find { it.uriString == uri } ?: return@forEach
            originalPaths.putIfAbsent(uri, video.path)
        }
        recycleBinUris.addAll(uris)
    }

    override suspend fun restoreVideosFromRecycleBin(uris: List<String>) {
        recycleBinUris.removeAll(uris.toSet())
        uris.forEach(originalPaths::remove)
    }
}
