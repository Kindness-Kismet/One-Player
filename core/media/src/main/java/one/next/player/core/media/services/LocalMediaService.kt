package one.next.player.core.media.services

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import one.next.player.core.common.extensions.VIDEO_COLLECTION_URI
import one.next.player.core.common.extensions.getMediaContentUri
import one.next.player.core.common.extensions.getMediaFileContentUri
import one.next.player.core.common.extensions.getPath
import one.next.player.core.common.extensions.updateMedia

@Singleton
class LocalMediaService @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaService {

    private lateinit var activity: Activity
    private val contentResolver = context.contentResolver
    private var resultOkCallback: () -> Unit = {}
    private var resultCancelledCallback: () -> Unit = {}
    private var mediaRequestLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    override fun initialize(activity: ComponentActivity) {
        this.activity = activity
        mediaRequestLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> resultOkCallback()
                Activity.RESULT_CANCELED -> resultCancelledCallback()
            }
        }
    }

    override suspend fun deleteMedia(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            deleteMediaR(uris)
        } else {
            deleteMediaBelowR(uris)
        }
    }

    override suspend fun renameMedia(uri: Uri, to: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            renameMediaR(uri, to)
        } else {
            renameMediaBelowR(uri, to)
        }
    }

    override suspend fun moveMediaToRecycleBin(uri: Uri): RecycleBinMoveResult? = withContext(Dispatchers.IO) {
        moveMedia(
            uri = uri,
            displayName = createRecycleBinFileName(context.getPath(uri)?.let(::File)?.name ?: return@withContext null),
            mimeType = RECYCLE_BIN_MIME_TYPE,
            relativePath = "$RECYCLE_BIN_RELATIVE_PATH/",
        )
    }

    override suspend fun restoreMediaFromRecycleBin(
        uri: Uri,
        originalPath: String,
        originalFileName: String,
    ): RecycleBinMoveResult? = withContext(Dispatchers.IO) {
        val file = File(originalPath)
        val parentPath = file.parent ?: return@withContext null

        moveMedia(
            uri = uri,
            displayName = originalFileName,
            mimeType = resolveMimeType(uri, originalFileName),
            relativePath = buildRelativePath(parentPath) ?: return@withContext null,
        )
    }

    override suspend fun shareMedia(uris: List<Uri>) {
        val intent = Intent.createChooser(
            Intent().apply {
                type = "video/*"
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            },
            null,
        )
        activity.startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun launchWriteRequest(
        uris: List<Uri>,
        onResultCanceled: () -> Unit = {},
        onResultOk: () -> Unit = {},
    ) {
        resultOkCallback = onResultOk
        resultCancelledCallback = onResultCanceled
        MediaStore.createWriteRequest(contentResolver, uris).also { intent ->
            mediaRequestLauncher?.launch(IntentSenderRequest.Builder(intent).build())
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun launchDeleteRequest(
        uris: List<Uri>,
        onResultCanceled: () -> Unit = {},
        onResultOk: () -> Unit = {},
    ) {
        resultOkCallback = onResultOk
        resultCancelledCallback = onResultCanceled
        MediaStore.createDeleteRequest(contentResolver, uris).also { intent ->
            mediaRequestLauncher?.launch(IntentSenderRequest.Builder(intent).build())
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun deleteMediaR(uris: List<Uri>): Boolean = suspendCancellableCoroutine { continuation ->
        launchDeleteRequest(
            uris = uris,
            onResultOk = { continuation.resume(true) },
            onResultCanceled = { continuation.resume(false) },
        )
    }

    private suspend fun deleteMediaBelowR(uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        return try {
            val ids = uris.map { ContentUris.parseId(it) }
            val selection = "${MediaStore.Video.Media._ID} IN (${ids.joinToString()})"
            contentResolver.delete(VIDEO_COLLECTION_URI, selection, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun renameMediaR(uri: Uri, to: String): Boolean = suspendCancellableCoroutine { continuation ->
        val scope = CoroutineScope(Dispatchers.Default)
        launchWriteRequest(
            uris = listOf(uri),
            onResultOk = {
                scope.launch {
                    val result = contentResolver.updateMedia(
                        uri = uri,
                        contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, to)
                        },
                    )
                    continuation.resume(result)
                }
            },
            onResultCanceled = { continuation.resume(false) },
        )
        continuation.invokeOnCancellation { scope.cancel() }
    }

    private suspend fun renameMediaBelowR(uri: Uri, to: String): Boolean = runCatching {
        val oldFile = context.getPath(uri)?.let { File(it) } ?: throw Error()
        val newFile = File(oldFile.parentFile, to)
        oldFile.renameTo(newFile).also { success ->
            if (success) {
                contentResolver.updateMedia(
                    uri = uri,
                    contentValues = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.DISPLAY_NAME, to)
                        put(MediaStore.Files.FileColumns.TITLE, to)
                        put(MediaStore.Files.FileColumns.DATA, newFile.path)
                    },
                )
            }
        }
    }.getOrNull() ?: false

    private suspend fun moveMedia(
        uri: Uri,
        displayName: String,
        mimeType: String,
        relativePath: String,
    ): RecycleBinMoveResult? {
        val currentPath = context.getPath(uri) ?: return null
        val currentFile = File(currentPath)
        val writableUri = buildWritableMediaUri(uri)

        return moveMediaBelowR(
            uri = writableUri,
            currentFile = currentFile,
            displayName = displayName,
            mimeType = mimeType,
            relativePath = relativePath,
        )
    }

    private suspend fun moveMediaBelowR(
        uri: Uri,
        currentFile: File,
        displayName: String,
        mimeType: String,
        relativePath: String,
    ): RecycleBinMoveResult? = runCatching {
        val targetDirectory = File(EXTERNAL_STORAGE_PATH, relativePath.removeSuffix("/"))
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            return@runCatching null
        }

        val newFile = File(targetDirectory, displayName)
        if (!currentFile.renameTo(newFile)) {
            return@runCatching null
        }

        val updated = contentResolver.updateMedia(
            uri = uri,
            contentValues = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, newFile.name)
                put(MediaStore.Files.FileColumns.TITLE, newFile.nameWithoutExtension)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                put(MediaStore.Files.FileColumns.DATA, newFile.path)
            },
        )
        if (!updated) {
            newFile.renameTo(currentFile)
            return@runCatching null
        }

        val resultUri = resolveResultUri(
            path = newFile.path,
            mimeType = mimeType,
            fallbackUri = uri,
        )

        RecycleBinMoveResult(
            uri = resultUri,
            path = newFile.path,
            parentPath = targetDirectory.path,
            fileName = newFile.name,
        )
    }.getOrNull()

    private fun buildRelativePath(parentPath: String): String? {
        val normalizedExternalPath = EXTERNAL_STORAGE_PATH.path.replace('\\', '/')
        val normalizedParentPath = parentPath.replace('\\', '/')
        if (!normalizedParentPath.startsWith(normalizedExternalPath)) {
            return null
        }

        val relative = normalizedParentPath.removePrefix(normalizedExternalPath).trimStart('/')
        return relative.takeIf(String::isNotBlank)?.plus("/")
    }

    private fun resolveMimeType(
        uri: Uri,
        displayName: String,
    ): String = contentResolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(displayName.substringAfterLast('.', "").lowercase())
        ?: "video/*"

    private fun createRecycleBinFileName(
        originalFileName: String,
    ): String = originalFileName.substringBeforeLast('.') + "." + RECYCLE_BIN_EXTENSION

    private fun buildWritableMediaUri(uri: Uri): Uri = runCatching {
        ContentUris.withAppendedId(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            ContentUris.parseId(uri),
        )
    }.getOrElse { uri }

    private fun resolveResultUri(
        path: String,
        mimeType: String,
        fallbackUri: Uri,
    ): Uri = if (mimeType.startsWith("video/")) {
        context.getMediaContentUri(File(path).toUri()) ?: fallbackUri
    } else {
        context.getMediaFileContentUri(path) ?: fallbackUri
    }

    companion object {
        private const val RECYCLE_BIN_FOLDER_NAME = "_.bin"
        private const val RECYCLE_BIN_RELATIVE_PATH = "Movies/$RECYCLE_BIN_FOLDER_NAME"
        private const val RECYCLE_BIN_EXTENSION = "optrash"
        private const val RECYCLE_BIN_MIME_TYPE = "application/octet-stream"
        private val EXTERNAL_STORAGE_PATH = Environment.getExternalStorageDirectory()
    }
}
