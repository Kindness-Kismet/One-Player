package one.next.player.core.data.mappers

import java.util.Date
import one.next.player.core.common.Utils
import one.next.player.core.database.entities.AudioStreamInfoEntity
import one.next.player.core.database.entities.SubtitleStreamInfoEntity
import one.next.player.core.database.relations.MediumWithInfo
import one.next.player.core.model.Video

fun MediumWithInfo.toVideo() = Video(
    id = mediumEntity.mediaStoreId,
    path = mediumEntity.path,
    parentPath = mediumEntity.parentPath,
    duration = mediumEntity.duration,
    uriString = mediumEntity.uriString,
    nameWithExtension = mediumEntity.name,
    width = mediumEntity.width,
    height = mediumEntity.height,
    size = mediumEntity.size,
    isInRecycleBin = mediumStateEntity?.isInRecycleBin == true,
    dateModified = mediumEntity.modified,
    format = mediumEntity.format,
    playbackPosition = mediumStateEntity?.playbackPosition ?: 0L,
    lastPlayedAt = mediumStateEntity?.lastPlayedTime?.let { Date(it) },
    formattedDuration = Utils.formatDurationMillis(mediumEntity.duration),
    formattedFileSize = Utils.formatFileSize(mediumEntity.size),
    videoStream = videoStreamInfo?.toVideoStreamInfo(),
    audioStreams = audioStreamsInfo.map(AudioStreamInfoEntity::toAudioStreamInfo),
    subtitleStreams = subtitleStreamsInfo.map(SubtitleStreamInfoEntity::toSubtitleStreamInfo),
)
