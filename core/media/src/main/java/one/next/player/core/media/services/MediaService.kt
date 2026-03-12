package one.next.player.core.media.services

import android.net.Uri
import androidx.activity.ComponentActivity

interface MediaService {
    fun initialize(activity: ComponentActivity)
    suspend fun deleteMedia(uris: List<Uri>): Boolean
    suspend fun renameMedia(uri: Uri, to: String): Boolean
    suspend fun moveMediaToRecycleBin(uri: Uri): RecycleBinMoveResult?
    suspend fun restoreMediaFromRecycleBin(
        uri: Uri,
        originalPath: String,
        originalFileName: String,
    ): RecycleBinMoveResult?
    suspend fun shareMedia(uris: List<Uri>)

    companion object {
        fun willSystemAsksForDeleteConfirmation(): Boolean = true
    }
}

data class RecycleBinMoveResult(
    val uri: Uri,
    val path: String,
    val parentPath: String,
    val fileName: String,
)
