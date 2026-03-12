package one.next.player.feature.player.extensions

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.nio.charset.Charset
import one.next.player.core.common.Logger
import one.next.player.core.common.extensions.convertToUTF8
import one.next.player.core.common.extensions.getFilenameFromUri

fun Uri.getSubtitleMime(displayName: String? = null): String {
    val name = displayName ?: path ?: ""
    return when {
        name.endsWith(".ssa", ignoreCase = true) ||
            name.endsWith(".ass", ignoreCase = true) -> MimeTypes.TEXT_SSA
        name.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        name.endsWith(".ttml", ignoreCase = true) ||
            name.endsWith(".xml", ignoreCase = true) ||
            name.endsWith(".dfxp", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

val Uri.isSchemaContent: Boolean
    get() = ContentResolver.SCHEME_CONTENT.equals(scheme, ignoreCase = true)

suspend fun Context.uriToSubtitleConfiguration(
    uri: Uri,
    subtitleEncoding: String = "",
    isSelected: Boolean = false,
): MediaItem.SubtitleConfiguration {
    val charset = if (subtitleEncoding.isNotEmpty() && Charset.isSupported(subtitleEncoding)) {
        Charset.forName(subtitleEncoding)
    } else {
        null
    }
    val label = getFilenameFromUri(uri)
    val mimeType = uri.getSubtitleMime(displayName = label)
    val utf8ConvertedUri = convertToUTF8(uri = uri, charset = charset)
    Logger.debug(
        "SubtitleConfig",
        "uri=$uri, convertedUri=$utf8ConvertedUri, mime=$mimeType, label=$label",
    )
    return MediaItem.SubtitleConfiguration.Builder(utf8ConvertedUri).apply {
        setId(uri.toString())
        setMimeType(mimeType)
        setLabel(label)
        if (isSelected) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }.build()
}

fun Bundle.getParcelableUriArray(key: String): ArrayList<Uri>? = BundleCompat.getParcelableArrayList(this, key, Uri::class.java)
