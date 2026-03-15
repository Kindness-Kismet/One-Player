package one.next.player.feature.player.service

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import androidx.media3.common.Player.DISCONTINUITY_REASON_REMOVE
import androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.session.CommandButton
import androidx.media3.session.CommandButton.ICON_UNDEFINED
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleDelayMilliseconds
import io.github.anilbeesetti.nextlib.media3ext.renderer.subtitleSpeed
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.extractor.AssMatroskaExtractor
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import one.next.player.core.common.Logger
import one.next.player.core.common.extensions.deleteFiles
import one.next.player.core.common.extensions.getFilenameFromUri
import one.next.player.core.common.extensions.getLocalSubtitles
import one.next.player.core.common.extensions.getPath
import one.next.player.core.common.extensions.subtitleCacheDir
import one.next.player.core.data.repository.MediaRepository
import one.next.player.core.data.repository.PreferencesRepository
import one.next.player.core.model.DecoderPriority
import one.next.player.core.model.LoopMode
import one.next.player.core.model.PlayerPreferences
import one.next.player.core.model.Resume
import one.next.player.core.ui.R as coreUiR
import one.next.player.feature.player.PlayerActivity
import one.next.player.feature.player.R
import one.next.player.feature.player.engine.media3.MkvCuesParser
import one.next.player.feature.player.engine.media3.SeekMapInjectingExtractor
import one.next.player.feature.player.engine.media3.buildSeekMapFromCues
import one.next.player.feature.player.extensions.addAdditionalSubtitleConfiguration
import one.next.player.feature.player.extensions.audioTrackIndex
import one.next.player.feature.player.extensions.copy
import one.next.player.feature.player.extensions.getManuallySelectedTrackIndex
import one.next.player.feature.player.extensions.isApproximateSeekEnabled
import one.next.player.feature.player.extensions.playbackSpeed
import one.next.player.feature.player.extensions.positionMs
import one.next.player.feature.player.extensions.setExtras
import one.next.player.feature.player.extensions.setIsScrubbingModeEnabled
import one.next.player.feature.player.extensions.subtitleDelayMilliseconds
import one.next.player.feature.player.extensions.subtitleSpeed
import one.next.player.feature.player.extensions.switchTrack
import one.next.player.feature.player.extensions.uriToSubtitleConfiguration
import one.next.player.feature.player.extensions.videoZoom
import one.next.player.feature.player.subtitle.AssHandlerRegistry

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerService : MediaSessionService() {

    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSession? = null

    companion object {
        private const val TAG = "PlayerService"
        private const val FAST_SEEK_MIN_DELTA_MS = 2_000L
        private val ISO_639_2T_TO_1 = mapOf(
            "zho" to "zh", "chi" to "zh",
            "eng" to "en",
            "jpn" to "ja",
            "kor" to "ko",
            "fra" to "fr", "fre" to "fr",
            "deu" to "de", "ger" to "de",
            "spa" to "es",
            "por" to "pt",
            "rus" to "ru",
            "ara" to "ar",
            "tha" to "th",
            "vie" to "vi",
            "ita" to "it",
            "pol" to "pl",
            "nld" to "nl", "dut" to "nl",
            "tur" to "tr",
            "ind" to "id",
            "msa" to "ms", "may" to "ms",
        )
    }

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var mediaRepository: MediaRepository

    @Inject
    lateinit var imageLoader: ImageLoader

    private val playerPreferences: PlayerPreferences
        get() = preferencesRepository.playerPreferences.value

    private val customCommands = CustomCommands.asSessionCommands()

    private var isMediaItemReady = false

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentVolumeGain: Int = 0
    private val mediaParserRetried = mutableSetOf<String>()
    private var isPendingExternalSubAutoSelect = false
    private var assHandler: AssHandler? = null
    private var pendingPreciseSeekPromotionJob: Job? = null
    private lateinit var fastStartMediaSourceFactory: DefaultMediaSourceFactory
    private lateinit var preciseSeekMediaSourceFactory: DefaultMediaSourceFactory
    private lateinit var assSubtitleParserFactory: AssSubtitleParserFactory

    // mediaId 对应预解析 IndexSeekMap
    private val mkvSeekMapCache = ConcurrentHashMap<String, androidx.media3.extractor.SeekMap>()

    private var startupTimestamp = 0L
    private val startupAnalyticsListener = object : AnalyticsListener {
        override fun onPlaybackStateChanged(
            eventTime: AnalyticsListener.EventTime,
            state: Int,
        ) {
            if (state == Player.STATE_BUFFERING) {
                startupTimestamp = System.currentTimeMillis()
            }
            val label = when (state) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN($state)"
            }
            Logger.info(TAG, "startup state=$label t=${elapsed()}ms")
        }

        override fun onLoadStarted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
            retryCount: Int,
        ) {
            Logger.info(TAG, "startup loadStart t=${elapsed()}ms type=${mediaLoadData.dataType}")
        }

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
            mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
        ) {
            Logger.info(
                TAG,
                "startup loadDone t=${elapsed()}ms type=${mediaLoadData.dataType} bytes=${loadEventInfo.bytesLoaded}",
            )
        }

        override fun onRenderedFirstFrame(
            eventTime: AnalyticsListener.EventTime,
            output: Any,
            renderTimeMs: Long,
        ) {
            Logger.info(TAG, "startup firstFrame t=${elapsed()}ms")
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Logger.info(TAG, "startup decoderInit=$decoderName dur=${initializationDurationMs}ms t=${elapsed()}ms")
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Logger.info(TAG, "startup audioDecoder=$decoderName dur=${initializationDurationMs}ms t=${elapsed()}ms")
        }

        override fun onTracksChanged(
            eventTime: AnalyticsListener.EventTime,
            tracks: androidx.media3.common.Tracks,
        ) {
            val player = mediaSession?.player
            Logger.info(
                TAG,
                "startup tracksChanged t=${elapsed()}ms groups=${tracks.groups.size} seekable=${player?.isCurrentMediaItemSeekable} duration=${player?.duration}",
            )
        }
    }

    private fun elapsed(): Long = System.currentTimeMillis() - startupTimestamp

    private val playbackStateListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            pendingPreciseSeekPromotionJob?.cancel()
            pendingPreciseSeekPromotionJob = null
            isMediaItemReady = false
            isPendingExternalSubAutoSelect = false
            mediaItem?.mediaMetadata?.let { metadata ->
                mediaSession?.player?.run {
                    setPlaybackSpeed(metadata.playbackSpeed ?: playerPreferences.defaultPlaybackSpeed)
                    playerSpecificSubtitleDelayMilliseconds = metadata.subtitleDelayMilliseconds ?: 0L
                    playerSpecificSubtitleSpeed = metadata.subtitleSpeed ?: 1f
                }

                if (metadata.isApproximateSeekEnabled) {
                    Logger.info(TAG, "Skip resume seek for approximate-seek media item=${mediaItem.mediaId}")
                    preloadMkvCues(mediaItem)
                    return
                }

                metadata.positionMs?.takeIf { playerPreferences.resume == Resume.YES }?.let {
                    mediaSession?.player?.seekTo(it)
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            val oldMediaItem = oldPosition.mediaItem ?: return

            when (reason) {
                DISCONTINUITY_REASON_SEEK,
                DISCONTINUITY_REASON_AUTO_TRANSITION,
                -> {
                    if (newPosition.mediaItem == null || oldMediaItem == newPosition.mediaItem) return

                    val player = mediaSession?.player ?: return
                    val updatedPosition = oldPosition.positionMs.takeIf { reason == DISCONTINUITY_REASON_SEEK } ?: C.TIME_UNSET
                    val mediaItemToUpdate = player.getMediaItemAt(oldPosition.mediaItemIndex)
                        .takeIf { it.mediaId == oldMediaItem.mediaId }
                        ?: oldMediaItem

                    player.replaceMediaItem(
                        oldPosition.mediaItemIndex,
                        mediaItemToUpdate.copy(positionMs = updatedPosition),
                    )
                    serviceScope.launch {
                        mediaRepository.updateMediumPosition(
                            uri = oldMediaItem.mediaId,
                            position = updatedPosition,
                        )
                    }
                }

                DISCONTINUITY_REASON_REMOVE -> {
                    serviceScope.launch {
                        val durationMs = oldMediaItem.mediaMetadata.durationMs
                        val isAtEnd = durationMs != null && oldPosition.positionMs >= durationMs - 1000
                        mediaRepository.updateMediumPosition(
                            uri = oldMediaItem.mediaId,
                            position = if (isAtEnd) C.TIME_UNSET else oldPosition.positionMs,
                        )
                    }
                }

                else -> return
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)
            if (tracks.groups.isEmpty()) return

            if (isPendingExternalSubAutoSelect) {
                isPendingExternalSubAutoSelect = false
                val player = mediaSession?.player ?: return
                val textTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                if (textTracks.isNotEmpty()) {
                    val bestIndex = findBestSubtitleTrackIndex(textTracks)
                    player.switchTrack(C.TRACK_TYPE_TEXT, bestIndex)
                }
                return
            }

            if (isMediaItemReady) return
            isMediaItemReady = true

            val player = mediaSession?.player ?: return
            val metadata = player.mediaMetadata
            if (playerPreferences.shouldRememberSelections) {
                metadata.audioTrackIndex?.let { player.switchTrack(C.TRACK_TYPE_AUDIO, it) }
            }

            val textTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
            if (textTracks.isNotEmpty()) {
                val bestIndex = findBestSubtitleTrackIndex(textTracks)
                player.switchTrack(C.TRACK_TYPE_TEXT, bestIndex)
            } else {
                val mediaId = player.currentMediaItem?.mediaId ?: return
                loadExternalSubtitlesForCurrentItem(mediaId)
            }
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            super.onTrackSelectionParametersChanged(parameters)
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return

            val audioTrackIndex = player.getManuallySelectedTrackIndex(C.TRACK_TYPE_AUDIO)

            if (audioTrackIndex != null) {
                serviceScope.launch {
                    mediaRepository.updateMediumAudioTrack(
                        uri = currentMediaItem.mediaId,
                        audioTrackIndex = audioTrackIndex,
                    )
                }
            }

            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentMediaItem.copy(
                    audioTrackIndex = audioTrackIndex,
                ),
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            if (playbackState == Player.STATE_IDLE) {
                val player = mediaSession?.player ?: return
                player.trackSelectionParameters = TrackSelectionParameters.DEFAULT
                player.setPlaybackSpeed(playerPreferences.defaultPlaybackSpeed)
                return
            }

            if (playbackState == Player.STATE_ENDED) {
                val player = mediaSession?.player ?: return
                player.setPlaybackSpeed(playerPreferences.defaultPlaybackSpeed)
                return
            }

            if (playbackState == Player.STATE_READY) {
                val player = mediaSession?.player ?: return
                val mediaId = player.currentMediaItem?.mediaId ?: return
                serviceScope.launch {
                    mediaRepository.updateMediumLastPlayedTime(
                        uri = mediaId,
                        lastPlayedTime = System.currentTimeMillis(),
                    )
                }
            }
        }

        override fun onPlayWhenReadyChanged(shouldPlayWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(shouldPlayWhenReady, reason)
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) return

            val player = mediaSession?.player ?: return
            if (player.repeatMode != Player.REPEAT_MODE_OFF) {
                player.seekTo(0)
                player.play()
                return
            }
            player.clearMediaItems()
            player.stop()
            stopSelf()
        }

        override fun onRenderedFirstFrame() {
            super.onRenderedFirstFrame()
            val player = mediaSession?.player ?: return
            val currentMediaItem = player.currentMediaItem ?: return

            // 从 track format 读取视频尺寸，再通过 metadata extras 传给 MediaController
            val format = player.currentTracks.groups
                .firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                ?.getTrackFormat(0)
            val width = format?.width ?: 0
            val height = format?.height ?: 0
            val rotation = format?.rotationDegrees ?: 0
            Logger.info(
                TAG,
                "startup firstFrameReady format=${width}x$height rot=$rotation duration=${player.duration} seekable=${player.isCurrentMediaItemSeekable}",
            )

            val duration = player.duration.takeIf { it != C.TIME_UNSET }
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                currentMediaItem.copy(
                    durationMs = duration,
                    videoWidth = width,
                    videoHeight = height,
                    videoRotation = rotation,
                ),
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            mediaSession?.run {
                serviceScope.launch {
                    mediaRepository.updateMediumPosition(
                        uri = player.currentMediaItem?.mediaId ?: return@launch,
                        position = player.currentPosition,
                    )
                }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)
            serviceScope.launch {
                preferencesRepository.updatePlayerPreferences {
                    it.copy(
                        loopMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> LoopMode.OFF
                            Player.REPEAT_MODE_ONE -> LoopMode.ONE
                            Player.REPEAT_MODE_ALL -> LoopMode.ALL
                            else -> LoopMode.OFF
                        },
                    )
                }
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            super.onAudioSessionIdChanged(audioSessionId)
            if (!playerPreferences.isVolumeBoostEnabled) return
            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
            try {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                if (currentVolumeGain > 0) {
                    setEnhancerTargetGain(currentVolumeGain)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                loudnessEnhancer = null
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            retryWithFixedSource(error)
        }
    }

    // MP4 容器解析失败时，检测并修复结构问题后以容错模式重试
    private fun retryWithFixedSource(error: PlaybackException) {
        if (!hasParserExceptionCause(error)) return
        val player = mediaSession?.player as? ExoPlayer ?: return
        val currentItem = player.currentMediaItem ?: return
        if (!mediaParserRetried.add(currentItem.mediaId)) return

        val mediaId = currentItem.mediaId
        serviceScope.launch {
            val uri = mediaId.toUri()
            val skipRegion = withContext(Dispatchers.IO) { detectDuplicateMoov(uri) }

            withContext(Dispatchers.Main) {
                val currentPlayer = mediaSession?.player as? ExoPlayer ?: return@withContext
                if (currentPlayer.playerError == null) return@withContext

                val index = (0 until currentPlayer.mediaItemCount).firstOrNull {
                    currentPlayer.getMediaItemAt(it).mediaId == mediaId
                } ?: return@withContext

                val item = currentPlayer.getMediaItemAt(index)
                val dataSourceFactory = if (skipRegion != null) {
                    Logger.debug(
                        TAG,
                        "Duplicate moov at ${skipRegion.start}+${skipRegion.length}, retrying: $mediaId",
                    )
                    DataSource.Factory {
                        GapSkipDataSource(
                            upstream = DefaultDataSource.Factory(applicationContext)
                                .createDataSource(),
                            targetUri = uri,
                            gapStart = skipRegion.start,
                            gapLength = skipRegion.length,
                        )
                    }
                } else {
                    Logger.debug(TAG, "Retrying with lenient extractor: $mediaId")
                    DefaultDataSource.Factory(applicationContext)
                }

                val mediaSource = DefaultMediaSourceFactory(
                    dataSourceFactory,
                    LenientExtractorsFactory(),
                ).createMediaSource(item)

                currentPlayer.removeMediaItem(index)
                currentPlayer.addMediaSource(index, mediaSource)
                currentPlayer.seekTo(index, 0)
                currentPlayer.prepare()
                currentPlayer.playWhenReady = true
            }
        }
    }

    private fun hasParserExceptionCause(error: PlaybackException): Boolean {
        var cause: Throwable? = error.cause
        repeat(3) {
            val current = cause ?: return false
            if (current is ParserException) return true
            cause = current.cause
        }
        return false
    }

    private data class SkipRegion(val start: Long, val length: Long)

    private fun createPlaybackExtractorsFactory(
        assSubtitleParserFactory: AssSubtitleParserFactory,
        assHandler: AssHandler,
        shouldUseFastStart: Boolean,
    ): ExtractorsFactory {
        val baseFactory = DefaultExtractorsFactory()
        return ExtractorsFactory {
            val extractors = baseFactory.createExtractors()
            for (i in extractors.indices) {
                if (extractors[i] is MatroskaExtractor) {
                    extractors[i] = AssMatroskaExtractor(
                        assSubtitleParserFactory,
                        assHandler,
                    ).also { extractor ->
                        if (shouldUseFastStart) {
                            disableSeekForCues(extractor)
                        }
                    }
                }
            }
            extractors
        }
    }

    private fun createMediaSourceFactory(
        assSubtitleParserFactory: AssSubtitleParserFactory,
        assHandler: AssHandler,
        shouldUseFastStart: Boolean,
    ): DefaultMediaSourceFactory = DefaultMediaSourceFactory(
        DefaultDataSource.Factory(applicationContext),
        createPlaybackExtractorsFactory(
            assSubtitleParserFactory = assSubtitleParserFactory,
            assHandler = assHandler,
            shouldUseFastStart = shouldUseFastStart,
        ),
    ).setSubtitleParserFactory(assSubtitleParserFactory)

    private fun disableSeekForCues(extractor: MatroskaExtractor) {
        try {
            val field = MatroskaExtractor::class.java.getDeclaredField("seekForCuesEnabled")
            field.isAccessible = true
            field.set(extractor, false)
        } catch (e: Exception) {
            Logger.error(TAG, "disableSeekForCues failed", e)
        }
    }

    // 预热 MediaCodecUtil 解码器缓存，避免首次播放阻塞在 codec 枚举
    private fun warmUpCodecCache() {
        // 仅预热最常用的 MIME，减少 synchronized 占锁时间
        val mimeTypes = listOf(
            MimeTypes.VIDEO_H265,
            MimeTypes.VIDEO_H264,
            MimeTypes.AUDIO_AAC,
        )
        for (mimeType in mimeTypes) {
            try {
                MediaCodecUtil.getDecoderInfos(mimeType, false, false)
            } catch (_: MediaCodecUtil.DecoderQueryException) {
                // 仅为预热缓存
            }
        }
    }

    // 扫描 MP4 顶层 atom，检测连续出现的 moov box
    private fun detectDuplicateMoov(uri: Uri): SkipRegion? {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                var offset = 0L
                var hasSeenFirstMoov = false
                val header = ByteArray(8)

                while (true) {
                    if (!readFully(stream, header)) break

                    val size = ((header[0].toLong() and 0xFF) shl 24) or
                        ((header[1].toLong() and 0xFF) shl 16) or
                        ((header[2].toLong() and 0xFF) shl 8) or
                        (header[3].toLong() and 0xFF)
                    val type = String(header, 4, 4, Charsets.US_ASCII)

                    if (size < 8) break

                    if (type == "moov") {
                        if (hasSeenFirstMoov) {
                            return SkipRegion(start = offset, length = size)
                        }
                        hasSeenFirstMoov = true
                    }

                    if (type == "mdat") break

                    val bodySize = size - 8
                    var skipped = 0L
                    while (skipped < bodySize) {
                        val s = stream.skip(bodySize - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                    if (skipped < bodySize) break
                    offset += size
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to scan MP4 structure", e)
        }
        return null
    }

    // DataSource 包装器，读取时透明跳过 [gapStart, gapStart + gapLength) 区间
    private class GapSkipDataSource(
        private val upstream: DataSource,
        private val targetUri: Uri,
        private val gapStart: Long,
        private val gapLength: Long,
    ) : DataSource by upstream {

        private var isTarget = false
        private var hasCrossedGap = false
        private var bytesUntilGap = Long.MAX_VALUE
        private var currentDataSpec: DataSpec? = null

        override fun open(dataSpec: DataSpec): Long {
            currentDataSpec = dataSpec
            isTarget = dataSpec.uri == targetUri
            if (!isTarget) return upstream.open(dataSpec)

            val virtualPos = dataSpec.position
            if (virtualPos >= gapStart) {
                hasCrossedGap = true
                bytesUntilGap = Long.MAX_VALUE
                val adjustedSpec = dataSpec.buildUpon()
                    .setPosition(virtualPos + gapLength)
                    .build()
                return upstream.open(adjustedSpec)
            }

            hasCrossedGap = false
            bytesUntilGap = gapStart - virtualPos
            val length = upstream.open(dataSpec)
            if (length == C.LENGTH_UNSET.toLong()) return length

            val physicalEnd = virtualPos + length
            return when {
                physicalEnd > gapStart + gapLength -> length - gapLength
                physicalEnd > gapStart -> gapStart - virtualPos
                else -> length
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!isTarget) return upstream.read(buffer, offset, length)

            if (!hasCrossedGap && bytesUntilGap <= 0L) {
                upstream.close()
                upstream.open(
                    DataSpec.Builder()
                        .setUri(currentDataSpec!!.uri)
                        .setPosition(gapStart + gapLength)
                        .build(),
                )
                hasCrossedGap = true
            }

            val toRead = if (!hasCrossedGap) {
                minOf(length.toLong(), bytesUntilGap).toInt()
            } else {
                length
            }
            val bytesRead = upstream.read(buffer, offset, toRead)
            if (bytesRead > 0 && !hasCrossedGap) {
                bytesUntilGap -= bytesRead
            }
            return bytesRead
        }

        override fun close() {
            isTarget = false
            upstream.close()
        }
    }

    // 容错 ExtractorsFactory，Mp4 使用 LenientMp4Extractor，其余回退到默认实现
    private class LenientExtractorsFactory : ExtractorsFactory {
        override fun createExtractors(): Array<Extractor> {
            val defaults = DefaultExtractorsFactory().createExtractors()
            return Array(defaults.size + 1) { i ->
                if (i == 0) LenientMp4Extractor() else defaults[i - 1]
            }
        }
    }

    // 包装 Mp4Extractor，捕获 sample 级别的 ParserException
    private class LenientMp4Extractor : Extractor {

        private val delegate = Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED)

        override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

        override fun init(output: ExtractorOutput) = delegate.init(output)

        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = try {
            delegate.read(input, seekPosition)
        } catch (e: ParserException) {
            Logger.error(TAG, "Lenient extractor treating error as end of input", e)
            Extractor.RESULT_END_OF_INPUT
        }

        override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

        override fun release() = delegate.release()
    }

    private fun setEnhancerTargetGain(gain: Int) {
        val enhancer = loudnessEnhancer ?: return

        try {
            enhancer.setTargetGain(gain)
            enhancer.enabled = gain > 0
            currentVolumeGain = enhancer.targetGain.toInt()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands
                    .buildUpon()
                    .addSessionCommands(customCommands)
                    .build(),
                connectionResult.availablePlayerCommands,
            )
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future(Dispatchers.Default) {
            val updatedMediaItems = updatedMediaItemsWithMetadata(mediaItems)
            loadArtworkInBackground(updatedMediaItems)
            return@future MediaSession.MediaItemsWithStartPosition(updatedMediaItems, startIndex, startPositionMs)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future(Dispatchers.Default) {
            val updatedMediaItems = updatedMediaItemsWithMetadata(mediaItems)
            loadArtworkInBackground(updatedMediaItems)
            return@future updatedMediaItems.toMutableList()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = serviceScope.future {
            val command = CustomCommands.fromSessionCommand(customCommand)
                ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)

            when (command) {
                CustomCommands.ADD_SUBTITLE_TRACK -> {
                    val subtitleUri = args.getString(CustomCommands.SUBTITLE_TRACK_URI_KEY)?.toUri()
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    val player = mediaSession?.player
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    val currentMediaItem = player.currentMediaItem
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)

                    val newSubConfiguration = uriToSubtitleConfiguration(
                        uri = subtitleUri,
                        subtitleEncoding = playerPreferences.subtitleTextEncoding,
                    )
                    mediaRepository.updateMediumPosition(
                        uri = currentMediaItem.mediaId,
                        position = player.currentPosition,
                    )
                    mediaRepository.addExternalSubtitleToMedium(
                        uri = currentMediaItem.mediaId,
                        subtitleUri = subtitleUri,
                    )
                    player.addAdditionalSubtitleConfiguration(newSubConfiguration)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.PRECISE_SEEK_TO -> {
                    val targetPositionMs = args.getLong(CustomCommands.SEEK_POSITION_MS_KEY, C.TIME_UNSET)
                    if (targetPositionMs == C.TIME_UNSET) {
                        return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    }
                    return@future requestSeekForCurrentItem(targetPositionMs)
                }

                CustomCommands.SET_SKIP_SILENCE_ENABLED -> {
                    val isSkipSilenceEnabled = args.getBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY)
                    mediaSession?.player?.isSkipSilenceEnabledForPlayer = isSkipSilenceEnabled
                    mediaSession?.sessionExtras = Bundle().apply {
                        putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, isSkipSilenceEnabled)
                    }
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SKIP_SILENCE_ENABLED -> {
                    val isSkipSilenceEnabled = mediaSession?.player?.isSkipSilenceEnabledForPlayer ?: false
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.SKIP_SILENCE_ENABLED_KEY, isSkipSilenceEnabled)
                        },
                    )
                }

                CustomCommands.SET_IS_SCRUBBING_MODE_ENABLED -> {
                    val isScrubbingModeEnabled = args.getBoolean(CustomCommands.IS_SCRUBBING_MODE_ENABLED_KEY)
                    mediaSession?.player?.setIsScrubbingModeEnabled(isScrubbingModeEnabled)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.SET_PERSISTENT_PLAYBACK_SPEED -> {
                    val playbackSpeed = args.getFloat(CustomCommands.PLAYBACK_SPEED_KEY)
                    val player = mediaSession?.player
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    val currentMediaItem = player.currentMediaItem
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)

                    player.setPlaybackSpeed(playbackSpeed)
                    serviceScope.launch {
                        mediaRepository.updateMediumPlaybackSpeed(
                            uri = currentMediaItem.mediaId,
                            playbackSpeed = playbackSpeed,
                        )
                    }
                    player.replaceMediaItem(
                        player.currentMediaItemIndex,
                        currentMediaItem.copy(playbackSpeed = playbackSpeed),
                    )
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.SET_TRANSIENT_PLAYBACK_SPEED -> {
                    val playbackSpeed = args.getFloat(CustomCommands.PLAYBACK_SPEED_KEY)
                    val player = mediaSession?.player
                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                    player.setPlaybackSpeed(playbackSpeed)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED -> {
                    val isSupported = loudnessEnhancer != null
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putBoolean(CustomCommands.IS_LOUDNESS_GAIN_SUPPORTED_KEY, isSupported)
                        },
                    )
                }

                CustomCommands.SET_LOUDNESS_GAIN -> {
                    val gain = args.getInt(CustomCommands.LOUDNESS_GAIN_KEY, 0)
                    setEnhancerTargetGain(gain)
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_LOUDNESS_GAIN -> {
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putInt(CustomCommands.LOUDNESS_GAIN_KEY, currentVolumeGain)
                        },
                    )
                }

                CustomCommands.GET_SUBTITLE_DELAY -> {
                    val subtitleDelay = mediaSession?.player?.playerSpecificSubtitleDelayMilliseconds ?: 0
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putLong(CustomCommands.SUBTITLE_DELAY_KEY, subtitleDelay)
                        },
                    )
                }

                CustomCommands.SET_SUBTITLE_DELAY -> {
                    val subtitleDelay = args.getLong(CustomCommands.SUBTITLE_DELAY_KEY)
                    mediaSession?.player?.playerSpecificSubtitleDelayMilliseconds = subtitleDelay
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.GET_SUBTITLE_SPEED -> {
                    val subtitleSpeed = mediaSession?.player?.playerSpecificSubtitleSpeed ?: 0f
                    return@future SessionResult(
                        SessionResult.RESULT_SUCCESS,
                        Bundle().apply {
                            putFloat(CustomCommands.SUBTITLE_SPEED_KEY, subtitleSpeed)
                        },
                    )
                }

                CustomCommands.SET_SUBTITLE_SPEED -> {
                    val subtitleSpeed = args.getFloat(CustomCommands.SUBTITLE_SPEED_KEY)
                    mediaSession?.player?.playerSpecificSubtitleSpeed = subtitleSpeed
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }

                CustomCommands.STOP_PLAYER_SESSION -> {
                    mediaSession?.run {
                        serviceScope.launch {
                            mediaRepository.updateMediumPosition(
                                uri = player.currentMediaItem?.mediaId ?: return@launch,
                                position = player.currentPosition,
                            )
                        }
                        player.clearMediaItems()
                        player.stop()
                    }
                    stopSelf()
                    return@future SessionResult(SessionResult.RESULT_SUCCESS)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch(Dispatchers.IO) { warmUpCodecCache() }
        val renderersFactory = NextRenderersFactory(applicationContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                when (playerPreferences.decoderPriority) {
                    DecoderPriority.DEVICE_ONLY -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    DecoderPriority.PREFER_DEVICE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderPriority.PREFER_APP -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                },
            )

        val trackSelector = DefaultTrackSelector(applicationContext).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(playerPreferences.preferredAudioLanguage)
                    .setPreferredTextLanguage(playerPreferences.preferredSubtitleLanguage),
            )
        }

        val assHandler = AssHandler(renderType = AssRenderType.OVERLAY_OPEN_GL)
        this.assHandler = assHandler
        AssHandlerRegistry.register(assHandler)
        val assSubtitleParserFactory = AssSubtitleParserFactory(assHandler)
        this.assSubtitleParserFactory = assSubtitleParserFactory
        fastStartMediaSourceFactory = createMediaSourceFactory(
            assSubtitleParserFactory = assSubtitleParserFactory,
            assHandler = assHandler,
            shouldUseFastStart = true,
        )
        preciseSeekMediaSourceFactory = createMediaSourceFactory(
            assSubtitleParserFactory = assSubtitleParserFactory,
            assHandler = assHandler,
            shouldUseFastStart = false,
        )

        val player = ExoPlayer.Builder(applicationContext)
            .setMediaSourceFactory(fastStartMediaSourceFactory)
            .setRenderersFactory(renderersFactory.withAssSupport(assHandler))
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                playerPreferences.shouldRequireAudioFocus,
            )
            .setHandleAudioBecomingNoisy(playerPreferences.shouldPauseOnHeadsetDisconnect)
            .build()
            .also {
                assHandler.init(it)
                it.addListener(playbackStateListener)
                it.addAnalyticsListener(startupAnalyticsListener)
                it.pauseAtEndOfMediaItems = !playerPreferences.shouldAutoPlay
                it.repeatMode = when (playerPreferences.loopMode) {
                    LoopMode.OFF -> Player.REPEAT_MODE_OFF
                    LoopMode.ONE -> Player.REPEAT_MODE_ONE
                    LoopMode.ALL -> Player.REPEAT_MODE_ALL
                }
            }

        try {
            mediaSession = MediaSession.Builder(this, player).apply {
                setSessionActivity(
                    PendingIntent.getActivity(
                        this@PlayerService,
                        0,
                        Intent(this@PlayerService, PlayerActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setCallback(mediaSessionCallback)
                setCustomLayout(
                    listOf(
                        CommandButton.Builder(ICON_UNDEFINED)
                            .setCustomIconResId(coreUiR.drawable.ic_close)
                            .setDisplayName(getString(coreUiR.string.stop_player_session))
                            .setSessionCommand(CustomCommands.STOP_PLAYER_SESSION.sessionCommand)
                            .setEnabled(true)
                            .build(),
                    ),
                )
            }.build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player!!
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        pendingPreciseSeekPromotionJob?.cancel()
        pendingPreciseSeekPromotionJob = null
        assHandler?.let(AssHandlerRegistry::unregister)
        assHandler = null
        mediaSession?.run {
            player.clearMediaItems()
            player.stop()
            player.removeListener(playbackStateListener)
            player.release()
            release()
            mediaSession = null
        }
        subtitleCacheDir.deleteFiles()
        mediaParserRetried.clear()
        serviceScope.cancel()
    }

    private suspend fun updatedMediaItemsWithMetadata(
        mediaItems: List<MediaItem>,
    ): List<MediaItem> = supervisorScope {
        mediaItems.map { mediaItem ->
            async {
                val uri = mediaItem.mediaId.toUri()
                val video = mediaRepository.getVideoByUri(uri = mediaItem.mediaId)
                val videoState = mediaRepository.getVideoState(uri = mediaItem.mediaId)

                val externalSubs = videoState?.externalSubs ?: emptyList()
                val validExternalSubs = externalSubs.filter { subUri ->
                    try {
                        contentResolver.openInputStream(subUri)?.close()
                        true
                    } catch (_: Exception) {
                        Logger.debug(TAG, "Removing stale external subtitle: $subUri")
                        false
                    }
                }
                if (validExternalSubs.size != externalSubs.size) {
                    mediaRepository.updateExternalSubs(
                        uri = mediaItem.mediaId,
                        externalSubs = validExternalSubs,
                    )
                }
                val existingSubConfigurations = mediaItem.localConfiguration?.subtitleConfigurations ?: emptyList()

                // 先写入占位封面，后台再异步加载真实封面
                val artworkUri = getDefaultArtworkUri()

                val title = mediaItem.mediaMetadata.title ?: video?.nameWithExtension ?: getFilenameFromUri(uri)
                val positionMs = mediaItem.mediaMetadata.positionMs ?: videoState?.position
                val durationMs = mediaItem.mediaMetadata.durationMs
                    ?: video?.duration?.takeIf { it > 0L }
                    ?: extractDurationMs(uri)
                val videoScale = mediaItem.mediaMetadata.videoZoom ?: videoState?.videoScale
                val playbackSpeed = mediaItem.mediaMetadata.playbackSpeed ?: videoState?.playbackSpeed
                val audioTrackIndex = mediaItem.mediaMetadata.audioTrackIndex ?: videoState?.audioTrackIndex
                val subtitleDelay = mediaItem.mediaMetadata.subtitleDelayMilliseconds ?: videoState?.subtitleDelayMilliseconds
                val subtitleSpeed = mediaItem.mediaMetadata.subtitleSpeed ?: videoState?.subtitleSpeed
                // MediaStore 返回的宽高已考虑 rotation，用于预设屏幕方向
                val videoWidth = video?.width
                val videoHeight = video?.height
                val mediaPath = video?.path ?: videoState?.path ?: getPath(uri) ?: uri.path
                val isApproximateSeekEnabled = mediaPath?.endsWith(".mkv", ignoreCase = true) == true

                mediaItem.buildUpon().apply {
                    setSubtitleConfigurations(existingSubConfigurations)
                    setMediaMetadata(
                        MediaMetadata.Builder().apply {
                            setTitle(title)
                            setArtworkUri(artworkUri)
                            setDurationMs(durationMs)
                            setExtras(
                                positionMs = positionMs,
                                videoScale = videoScale,
                                playbackSpeed = playbackSpeed,
                                audioTrackIndex = audioTrackIndex,
                                subtitleDelayMilliseconds = subtitleDelay,
                                subtitleSpeed = subtitleSpeed,
                                videoWidth = videoWidth,
                                videoHeight = videoHeight,
                                isApproximateSeekEnabled = isApproximateSeekEnabled,
                            )
                        }.build(),
                    )
                }.build()
            }
        }.awaitAll()
    }

    // 从文件头快速提取时长，用于数据库无记录的外部文件
    private fun extractDurationMs(uri: Uri): Long? = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(applicationContext, uri)
        val duration = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION,
        )?.toLongOrNull()
        retriever.release()
        duration?.takeIf { it > 0L }
    } catch (_: Exception) {
        null
    }

    private fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val cachedSeekMap = mkvSeekMapCache[mediaItem.mediaId]
        if (cachedSeekMap != null) {
            // 使用预解析的 SeekMap 注入到 extractor，跳过慢速 Cues 加载
            val factory = DefaultMediaSourceFactory(
                DefaultDataSource.Factory(applicationContext),
                createSeekMapInjectedExtractorsFactory(cachedSeekMap),
            ).setSubtitleParserFactory(assSubtitleParserFactory)
            return factory.createMediaSource(mediaItem)
        }

        val mediaSourceFactory = if (mediaItem.mediaMetadata.isApproximateSeekEnabled) {
            fastStartMediaSourceFactory
        } else {
            preciseSeekMediaSourceFactory
        }
        return mediaSourceFactory.createMediaSource(mediaItem)
    }

    private fun promoteCurrentItemToPreciseSeek(targetPositionMs: Long): SessionResult {
        val player = mediaSession?.player as? ExoPlayer ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val currentItem = player.currentMediaItem ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex !in 0 until player.mediaItemCount) {
            return SessionResult(SessionError.ERROR_BAD_VALUE)
        }

        val maxPosition = currentItem.mediaMetadata.durationMs
            ?.takeIf { it > 0L }
            ?: player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val targetPosition = maxPosition?.let { targetPositionMs.coerceIn(0L, it) } ?: targetPositionMs.coerceAtLeast(0L)

        if (!currentItem.mediaMetadata.isApproximateSeekEnabled) {
            Logger.info(TAG, "Precise seek direct mediaId=${currentItem.mediaId} target=$targetPosition")
            player.seekTo(targetPosition)
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }

        val updatedMediaItems = buildList {
            for (index in 0 until player.mediaItemCount) {
                val mediaItem = player.getMediaItemAt(index)
                add(
                    if (index == currentIndex) {
                        mediaItem.copy(
                            positionMs = targetPosition,
                            isApproximateSeekEnabled = false,
                        )
                    } else {
                        mediaItem
                    },
                )
            }
        }
        val updatedMediaSources = updatedMediaItems.map(::createMediaSource)
        val shouldPlayWhenReady = player.playWhenReady
        Logger.info(
            TAG,
            "Promote current item to precise seek mediaId=${currentItem.mediaId} target=$targetPosition hasCachedSeekMap=${mkvSeekMapCache.containsKey(currentItem.mediaId)}",
        )
        player.setMediaSources(updatedMediaSources, currentIndex, targetPosition)
        player.prepare()
        player.playWhenReady = shouldPlayWhenReady
        serviceScope.launch {
            mediaRepository.updateMediumPosition(
                uri = currentItem.mediaId,
                position = targetPosition,
            )
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    private fun requestSeekForCurrentItem(targetPositionMs: Long): SessionResult {
        val player = mediaSession?.player as? ExoPlayer ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val currentItem = player.currentMediaItem ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val maxPosition = currentItem.mediaMetadata.durationMs
            ?.takeIf { it > 0L }
            ?: player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val targetPosition = maxPosition?.let { targetPositionMs.coerceIn(0L, it) } ?: targetPositionMs.coerceAtLeast(0L)

        if (!currentItem.mediaMetadata.isApproximateSeekEnabled) {
            Logger.info(TAG, "Precise seek direct mediaId=${currentItem.mediaId} target=$targetPosition")
            player.seekTo(targetPosition)
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }

        val startPosition = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L
        val startDelta = kotlin.math.abs(targetPosition - startPosition)
        if (startDelta < FAST_SEEK_MIN_DELTA_MS) {
            Logger.info(
                TAG,
                "Skip precise promotion mediaId=${currentItem.mediaId} target=$targetPosition start=$startPosition",
            )
            return SessionResult(SessionResult.RESULT_SUCCESS)
        }

        // approximate source 无法高效 seek，直接升级到 precise source
        Logger.info(
            TAG,
            "Promote to precise seek mediaId=${currentItem.mediaId} start=$startPosition target=$targetPosition",
        )
        promoteCurrentItemToPreciseSeek(targetPosition)
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }

    // 后台预解析 MKV Cues，为后续 seek 构建快速 SeekMap
    private fun preloadMkvCues(mediaItem: MediaItem) {
        val mediaId = mediaItem.mediaId
        if (mkvSeekMapCache.containsKey(mediaId)) return

        val durationMs = mediaItem.mediaMetadata.durationMs ?: return
        val uri = Uri.parse(mediaId)

        serviceScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val cuePoints = MkvCuesParser.parse(applicationContext, uri)
            val elapsed = System.currentTimeMillis() - startTime

            if (cuePoints == null) {
                Logger.debug(TAG, "MKV Cues pre-parse returned null for $mediaId (${elapsed}ms)")
                return@launch
            }

            val durationUs = durationMs * 1_000L
            val seekMap = buildSeekMapFromCues(cuePoints, durationUs)
            mkvSeekMapCache[mediaId] = seekMap
            Logger.info(
                TAG,
                "MKV Cues pre-parsed: ${cuePoints.size} cue points in ${elapsed}ms for $mediaId",
            )
        }
    }

    // 创建注入了预解析 SeekMap 的 ExtractorsFactory
    private fun createSeekMapInjectedExtractorsFactory(
        seekMap: androidx.media3.extractor.SeekMap,
    ): ExtractorsFactory = ExtractorsFactory {
        val baseFactory = DefaultExtractorsFactory()
        val extractors = baseFactory.createExtractors()
        for (i in extractors.indices) {
            if (extractors[i] is MatroskaExtractor) {
                val assExtractor = AssMatroskaExtractor(assSubtitleParserFactory, assHandler!!)
                disableSeekForCues(assExtractor)
                extractors[i] = SeekMapInjectingExtractor(assExtractor, seekMap)
            }
        }
        extractors
    }

    private fun getDefaultArtworkUri(): Uri = Uri.Builder().apply {
        val defaultArtwork = R.drawable.artwork_default
        scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        authority(resources.getResourcePackageName(defaultArtwork))
        appendPath(resources.getResourceTypeName(defaultArtwork))
        appendPath(resources.getResourceEntryName(defaultArtwork))
    }.build()

    private suspend fun loadArtworkForUri(uri: Uri): ByteArray? = try {
        val result = imageLoader.execute(
            ImageRequest.Builder(this@PlayerService)
                .data(uri)
                .build(),
        )
        (result as? SuccessResult)?.image?.toBitmap()?.toByteArray()
    } catch (_: Exception) {
        null
    }

    // 在播放列表中按 mediaId 查找对应的 Player、索引和 MediaItem
    private fun findMediaItemInSession(mediaId: String): Triple<Player, Int, MediaItem>? {
        val player = mediaSession?.player ?: return null
        val index = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId == mediaId
        } ?: return null
        return Triple(player, index, player.getMediaItemAt(index))
    }

    private fun loadArtworkInBackground(mediaItems: List<MediaItem>) {
        serviceScope.launch(Dispatchers.Default) {
            mediaItems.forEach { mediaItem ->
                launch {
                    val artworkData = loadArtworkForUri(mediaItem.mediaId.toUri()) ?: return@launch

                    withContext(Dispatchers.Main) {
                        val (player, index, currentMediaItem) = findMediaItemInSession(mediaItem.mediaId) ?: return@withContext
                        val updatedMediaItem = currentMediaItem.buildUpon()
                            .setMediaMetadata(
                                currentMediaItem.mediaMetadata.buildUpon()
                                    .setArtworkUri(null)
                                    .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                    .build(),
                            )
                            .build()
                        player.replaceMediaItem(index, updatedMediaItem)
                    }
                }
            }
        }
    }

    // 无内置字幕时加载外部字幕（同名文件 + 已持久化的用户添加字幕）
    private fun loadExternalSubtitlesForCurrentItem(mediaId: String) {
        serviceScope.launch(Dispatchers.IO) {
            val configurations = buildExternalSubtitleConfigurations(mediaId)
            if (configurations.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                val (player, index, currentMediaItem) = findMediaItemInSession(mediaId) ?: return@withContext
                val existingConfigs = currentMediaItem.localConfiguration?.subtitleConfigurations ?: emptyList()
                val mergedConfigs = mergeSubtitleConfigurations(existingConfigs, configurations)
                if (mergedConfigs.size == existingConfigs.size) return@withContext

                val updatedMediaItem = currentMediaItem.buildUpon()
                    .setSubtitleConfigurations(mergedConfigs)
                    .build()
                isPendingExternalSubAutoSelect = true
                player.replaceMediaItem(index, updatedMediaItem)
                Logger.info(TAG, "Applied external subtitles mediaId=$mediaId count=${configurations.size}")
            }
        }
    }

    private suspend fun buildExternalSubtitleConfigurations(
        mediaId: String,
    ): List<MediaItem.SubtitleConfiguration> {
        val uri = mediaId.toUri()
        val videoState = mediaRepository.getVideoState(uri = mediaId)
        val dbExternalSubs = videoState?.externalSubs ?: emptyList()

        // 发现同名本地字幕（排除已保存的）
        val localSubs = (videoState?.path ?: getPath(uri))?.let {
            File(it).getLocalSubtitles(
                context = this@PlayerService,
                excludeSubsList = dbExternalSubs,
            )
        } ?: emptyList()

        val allExternalSubs = dbExternalSubs + localSubs
        if (allExternalSubs.isEmpty()) return emptyList()

        return allExternalSubs.map { subtitleUri ->
            uriToSubtitleConfiguration(
                uri = subtitleUri,
                subtitleEncoding = playerPreferences.subtitleTextEncoding,
            )
        }
    }

    private fun mergeSubtitleConfigurations(
        existing: List<MediaItem.SubtitleConfiguration>,
        incoming: List<MediaItem.SubtitleConfiguration>,
    ): List<MediaItem.SubtitleConfiguration> {
        val mergedById = LinkedHashMap<String, MediaItem.SubtitleConfiguration>()
        existing.forEach { subtitleConfiguration ->
            mergedById[subtitleConfiguration.id ?: subtitleConfiguration.uri.toString()] = subtitleConfiguration
        }
        incoming.forEach { subtitleConfiguration ->
            mergedById[subtitleConfiguration.id ?: subtitleConfiguration.uri.toString()] = subtitleConfiguration
        }
        return mergedById.values.toList()
    }

    private fun findBestSubtitleTrackIndex(textTracks: List<Tracks.Group>): Int {
        val preferred = playerPreferences.preferredSubtitleLanguage
        if (preferred.isBlank()) return 0

        val normalizedPref = normalizeLanguageTag(preferred)
        for (i in textTracks.indices) {
            val format = textTracks[i].getTrackFormat(0)
            if (matchesPreferredLanguage(format, normalizedPref)) return i
        }
        return 0
    }

    private fun matchesPreferredLanguage(format: Format, preferred: String): Boolean {
        val trackLang = format.language?.let(::normalizeLanguageTag) ?: return false

        if (preferred.startsWith("zh-") && (trackLang == "zh" || trackLang.startsWith("zh-"))) {
            return matchesChineseVariantByLabel(format.label, preferred)
        }

        return trackLang.startsWith(preferred) || preferred.startsWith(trackLang)
    }

    private fun matchesChineseVariantByLabel(label: String?, preferred: String): Boolean {
        if (label == null) return preferred == "zh"
        val lower = label.lowercase()
        val isSimplified = preferred.contains("hans") || preferred.contains("cn")
        val isTraditional = preferred.contains("hant") || preferred.contains("tw") || preferred.contains("hk")

        return when {
            isSimplified -> lower.containsAny("简", "chs", "simplified")
            isTraditional -> lower.containsAny("繁", "cht", "traditional")
            else -> true
        }
    }

    private fun normalizeLanguageTag(tag: String): String {
        val lower = tag.lowercase().replace('_', '-')
        return ISO_639_2T_TO_1[lower] ?: ISO_639_2T_TO_1[lower.substringBefore('-')]?.let {
            it + lower.removePrefix(lower.substringBefore('-'))
        } ?: lower
    }

    private fun String.containsAny(vararg keywords: String): Boolean = keywords.any { contains(it, ignoreCase = true) }

    private fun Bitmap.toByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }
}

private fun readFully(stream: InputStream, buffer: ByteArray): Boolean {
    var pos = 0
    while (pos < buffer.size) {
        val read = stream.read(buffer, pos, buffer.size - pos)
        if (read < 0) return false
        pos += read
    }
    return true
}

@get:UnstableApi
@set:UnstableApi
private var Player.isSkipSilenceEnabledForPlayer: Boolean
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.skipSilenceEnabled
        else -> false
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.skipSilenceEnabled = value
        }
    }

@get:UnstableApi
@set:UnstableApi
private var Player.playerSpecificSubtitleDelayMilliseconds: Long
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.subtitleDelayMilliseconds
        else -> 0L
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.subtitleDelayMilliseconds = value
        }
    }

@get:UnstableApi
@set:UnstableApi
private var Player.playerSpecificSubtitleSpeed: Float
    @OptIn(UnstableApi::class)
    get() = when (this) {
        is ExoPlayer -> this.subtitleSpeed
        else -> 0f
    }
    set(value) {
        when (this) {
            is ExoPlayer -> this.subtitleSpeed = value
        }
    }
