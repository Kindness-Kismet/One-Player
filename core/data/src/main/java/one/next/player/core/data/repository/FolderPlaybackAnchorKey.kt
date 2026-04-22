package one.next.player.core.data.repository

import java.net.URLDecoder

private const val REMOTE_FOLDER_PLAYBACK_ANCHOR_PREFIX = "oneplayer://folder"
private const val WEBDAV_PROTOCOL = "webdav"

fun buildRemoteFolderPlaybackAnchorKey(
    remoteProtocol: String?,
    remoteServerId: Long?,
    directoryPath: String?,
): String? {
    if (remoteProtocol?.lowercase() != WEBDAV_PROTOCOL) return null
    val serverId = remoteServerId?.takeIf { it > 0L } ?: return null
    val normalizedPath = directoryPath?.normalizeFolderPlaybackAnchorPath() ?: return null
    return "$REMOTE_FOLDER_PLAYBACK_ANCHOR_PREFIX/$WEBDAV_PROTOCOL/$serverId$normalizedPath"
}

fun String.normalizeFolderPlaybackAnchorPath(): String? {
    val raw = trim()
    if (raw.isBlank()) return null
    val decoded = URLDecoder.decode(raw, "UTF-8")
    val normalized = decoded
        .replace(Regex("/+"), "/")
        .removeSuffix("/")
        .ifBlank { "/" }
    return if (normalized.startsWith('/')) normalized else "/$normalized"
}
