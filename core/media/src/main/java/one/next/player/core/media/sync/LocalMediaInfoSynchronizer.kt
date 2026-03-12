package one.next.player.core.media.sync

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import coil3.ImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.anilbeesetti.nextlib.mediainfo.AudioStream
import io.github.anilbeesetti.nextlib.mediainfo.MediaInfoBuilder
import io.github.anilbeesetti.nextlib.mediainfo.SubtitleStream
import io.github.anilbeesetti.nextlib.mediainfo.VideoStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import one.next.player.core.common.Dispatcher
import one.next.player.core.common.Logger
import one.next.player.core.common.NextDispatchers
import one.next.player.core.common.di.ApplicationScope
import one.next.player.core.database.dao.MediumDao
import one.next.player.core.database.entities.AudioStreamInfoEntity
import one.next.player.core.database.entities.SubtitleStreamInfoEntity
import one.next.player.core.database.entities.VideoStreamInfoEntity

class LocalMediaInfoSynchronizer @Inject constructor(
    private val mediumDao: MediumDao,
    private val imageLoader: ImageLoader,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.Default) private val dispatcher: CoroutineDispatcher,
) : MediaInfoSynchronizer {

    private val pendingSyncUris = LinkedHashSet<String>()
    private val activeSyncUris = mutableSetOf<String>()
    private val mutex = Mutex()
    private var queueProcessorJob: Job? = null

    override fun sync(uri: Uri) {
        val uriString = uri.toString()
        applicationScope.launch(dispatcher) {
            val shouldStartProcessor = mutex.withLock {
                if (uriString in activeSyncUris || uriString in pendingSyncUris) {
                    return@withLock false
                }

                pendingSyncUris += uriString
                queueProcessorJob?.isActive != true
            }

            if (shouldStartProcessor) {
                queueProcessorJob = applicationScope.launch(dispatcher) {
                    processPendingSyncs()
                }
            }
        }
    }

    override suspend fun clearThumbnailsCache() {
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
    }

    private suspend fun processPendingSyncs() {
        while (true) {
            val nextUriString = mutex.withLock {
                val nextUriString = pendingSyncUris.firstOrNull()
                if (nextUriString == null) {
                    queueProcessorJob = null
                    null
                } else {
                    pendingSyncUris.remove(nextUriString)
                    activeSyncUris += nextUriString
                    nextUriString
                }
            } ?: return

            try {
                performSync(nextUriString.toUri())
            } finally {
                mutex.withLock {
                    activeSyncUris.remove(nextUriString)
                }
            }
        }
    }

    // 全部走 FFmpeg，不使用 MediaMetadataRetriever
    private suspend fun performSync(uri: Uri) {
        val medium = mediumDao.getWithInfo(uri.toString()) ?: return
        val mediumEntity = medium.mediumEntity
        val needsMetadata = mediumEntity.duration <= 0 || mediumEntity.width <= 0 || mediumEntity.height <= 0
        val needsStreamInfo = medium.videoStreamInfo == null

        if (!needsMetadata && !needsStreamInfo) return

        val mediaInfo = runCatching {
            if (uri.scheme == "file") {
                val path = uri.path ?: throw IllegalArgumentException("No path in file URI: $uri")
                MediaInfoBuilder().from(filePath = path).build()
            } else {
                MediaInfoBuilder().from(context = context, uri = uri).build()
            } ?: throw NullPointerException("MediaInfoBuilder returned null for $uri")
        }.onFailure { throwable ->
            Logger.error(TAG, "Failed to read media info for $uri", throwable)
        }.getOrNull() ?: return

        try {
            var updatedMedium = mediumEntity
            if (needsMetadata) {
                updatedMedium = updatedMedium.copy(
                    duration = mediaInfo.duration.takeIf { it > 0 } ?: updatedMedium.duration,
                    width = mediaInfo.videoStream?.frameWidth?.takeIf { it > 0 } ?: updatedMedium.width,
                    height = mediaInfo.videoStream?.frameHeight?.takeIf { it > 0 } ?: updatedMedium.height,
                )
            }

            if (needsStreamInfo) {
                val videoStreamInfo = mediaInfo.videoStream?.toVideoStreamInfoEntity(updatedMedium.uriString)
                val audioStreamsInfo = mediaInfo.audioStreams.map {
                    it.toAudioStreamInfoEntity(updatedMedium.uriString)
                }
                val subtitleStreamsInfo = mediaInfo.subtitleStreams.map {
                    it.toSubtitleStreamInfoEntity(updatedMedium.uriString)
                }

                mediumDao.upsert(updatedMedium.copy(format = mediaInfo.format))
                videoStreamInfo?.let { mediumDao.upsertVideoStreamInfo(it) }
                audioStreamsInfo.onEach { mediumDao.upsertAudioStreamInfo(it) }
                subtitleStreamsInfo.onEach { mediumDao.upsertSubtitleStreamInfo(it) }
            } else if (updatedMedium != mediumEntity) {
                mediumDao.upsert(updatedMedium)
            }

            Logger.info(TAG, "performSync ok uri=$uri duration=${updatedMedium.duration}")
        } finally {
            mediaInfo.release()
        }
    }

    companion object {
        private const val TAG = "MediaInfoSynchronizer"
    }
}

private fun VideoStream.toVideoStreamInfoEntity(mediumUri: String) = VideoStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    frameRate = frameRate,
    frameWidth = frameWidth,
    frameHeight = frameHeight,
    mediumUri = mediumUri,
)

private fun AudioStream.toAudioStreamInfoEntity(mediumUri: String) = AudioStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    bitRate = bitRate,
    sampleFormat = sampleFormat,
    sampleRate = sampleRate,
    channels = channels,
    channelLayout = channelLayout,
    mediumUri = mediumUri,
)

private fun SubtitleStream.toSubtitleStreamInfoEntity(mediumUri: String) = SubtitleStreamInfoEntity(
    index = index,
    title = title,
    codecName = codecName,
    language = language,
    disposition = disposition,
    mediumUri = mediumUri,
)
