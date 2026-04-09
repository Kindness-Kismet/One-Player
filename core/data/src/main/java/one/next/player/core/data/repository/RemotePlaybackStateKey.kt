package one.next.player.core.data.repository

private const val REMOTE_PLAYBACK_STATE_PREFIX = "oneplayer://remote"
private const val WEBDAV_PROTOCOL = "webdav"

fun buildRemotePlaybackStateKey(
    remoteProtocol: String?,
    remoteServerId: Long?,
    remoteFilePath: String?,
): String? {
    if (remoteProtocol?.lowercase() != WEBDAV_PROTOCOL) return null
    val serverId = remoteServerId?.takeIf { it > 0L } ?: return null
    val normalizedPath = remoteFilePath.normalizeRemotePlaybackPath() ?: return null
    return "$REMOTE_PLAYBACK_STATE_PREFIX/$WEBDAV_PROTOCOL/$serverId$normalizedPath"
}

fun buildPlaybackStateCandidates(
    originalUri: String,
    remoteProtocol: String?,
    remoteServerId: Long?,
    remoteFilePath: String?,
): List<String> {
    val stableKey = buildRemotePlaybackStateKey(
        remoteProtocol = remoteProtocol,
        remoteServerId = remoteServerId,
        remoteFilePath = remoteFilePath,
    )
    return listOfNotNull(stableKey, originalUri).distinct()
}

fun String.isRemotePlaybackStateKey(): Boolean = startsWith("$REMOTE_PLAYBACK_STATE_PREFIX/")

private fun String?.normalizeRemotePlaybackPath(): String? {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return null
    val normalized = raw
        .replace(Regex("/+"), "/")
        .removeSuffix("/")
        .takeIf { it.isNotBlank() && it != "/" }
        ?: return null
    return if (normalized.startsWith('/')) normalized else "/$normalized"
}
