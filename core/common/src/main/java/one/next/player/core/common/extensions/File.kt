package one.next.player.core.common.extensions

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.next.player.core.common.Logger

suspend fun File.getSubtitles(): List<File> = withContext(Dispatchers.IO) {
    val mediaName = this@getSubtitles.nameWithoutExtension
    val parentDir = this@getSubtitles.parentFile ?: return@withContext emptyList()

    parentDir.listFiles()
        ?.filter { it.isFile }
        ?.filter { it.isSubtitle() }
        ?.filter { it.name.matchesSubtitleBase(mediaName) }
        .orEmpty()
}

suspend fun File.getLocalSubtitles(
    context: Context,
    excludeSubsList: List<Uri> = emptyList(),
): List<Uri> = withContext(Dispatchers.IO) {
    val excludeSubsPathSet = excludeSubsList.mapNotNull { context.getPath(it) }.toSet()

    getSubtitles().mapNotNull { file ->
        if (file.path !in excludeSubsPathSet) {
            file.toUri()
        } else {
            null
        }
    }
}

fun String.getThumbnail(): File? {
    val filePathWithoutExtension = this.substringBeforeLast(".")
    val imageExtensions = listOf("png", "jpg", "jpeg")
    for (imageExtension in imageExtensions) {
        val file = File("$filePathWithoutExtension.$imageExtension")
        if (file.exists()) return file
    }
    return null
}

fun File.isSubtitle(): Boolean {
    val subtitleExtensions = listOf("srt", "ssa", "ass", "vtt", "ttml")
    return extension.lowercase() in subtitleExtensions
}

fun String.matchesSubtitleBase(videoName: String): Boolean {
    val subtitleBase = substringBeforeLast('.', missingDelimiterValue = this)
    return subtitleBase == videoName || subtitleBase.startsWith("$videoName.", ignoreCase = true)
}

fun File.deleteFiles() {
    try {
        listFiles()?.onEach {
            it.delete()
        }
    } catch (e: Exception) {
        Logger.error("File", "Failed to delete files", e)
    }
}

val File.prettyName: String
    get() = this.name.takeIf { this.path != Environment.getExternalStorageDirectory()?.path } ?: "Internal Storage"
