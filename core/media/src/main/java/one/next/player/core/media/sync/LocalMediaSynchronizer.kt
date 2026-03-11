package one.next.player.core.media.sync

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import coil3.ImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.next.player.core.common.Dispatcher
import one.next.player.core.common.Logger
import one.next.player.core.common.NextDispatchers
import one.next.player.core.common.di.ApplicationScope
import one.next.player.core.common.extensions.VIDEO_COLLECTION_URI
import one.next.player.core.common.extensions.getMediaFileContentUri
import one.next.player.core.common.extensions.getStorageVolumes
import one.next.player.core.common.extensions.prettyName
import one.next.player.core.common.extensions.scanPaths
import one.next.player.core.common.extensions.scanStorage
import one.next.player.core.common.hasManageExternalStorageAccess
import one.next.player.core.database.converter.UriListConverter
import one.next.player.core.database.dao.DirectoryDao
import one.next.player.core.database.dao.MediumDao
import one.next.player.core.database.dao.MediumStateDao
import one.next.player.core.database.entities.DirectoryEntity
import one.next.player.core.database.entities.MediumEntity
import one.next.player.core.database.entities.MediumStateEntity
import one.next.player.core.datastore.datasource.AppPreferencesDataSource
import one.next.player.core.media.model.MediaVideo

class LocalMediaSynchronizer @Inject constructor(
    private val mediumDao: MediumDao,
    private val mediumStateDao: MediumStateDao,
    private val directoryDao: DirectoryDao,
    private val imageLoader: ImageLoader,
    private val appPreferencesDataSource: AppPreferencesDataSource,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO) private val dispatcher: CoroutineDispatcher,
) : MediaSynchronizer {

    private var mediaSyncingJob: Job? = null

    override suspend fun refresh(path: String?): Boolean = path?.let { context.scanPaths(listOf(path)) }
        ?: context.getStorageVolumes().all { context.scanStorage(it.path) }

    override fun startSync() {
        if (mediaSyncingJob != null) return

        Logger.logInfo(TAG, "Starting media sync")
        mediaSyncingJob = combine(
            getMediaVideosFlow(),
            appPreferencesDataSource.preferences,
        ) { mediaStoreVideos, preferences ->
            mergeVisibleMedia(
                mediaStoreVideos = mediaStoreVideos,
                includeNoMediaDirectories = preferences.ignoreNoMediaFiles,
            )
        }.onEach { media ->
            Logger.logDebug(TAG, "Syncing ${media.size} media entries")
            applicationScope.launch { updateDirectories(media) }
            applicationScope.launch { updateMedia(media) }
        }.launchIn(applicationScope)
    }

    override fun stopSync() {
        mediaSyncingJob?.cancel()
        mediaSyncingJob = null
        Logger.logInfo(TAG, "Stopped media sync")
    }

    private suspend fun mergeVisibleMedia(
        mediaStoreVideos: List<MediaVideo>,
        includeNoMediaDirectories: Boolean,
    ): List<MediaVideo> = withContext(dispatcher) {
        if (!includeNoMediaDirectories) {
            return@withContext mediaStoreVideos
        }

        if (!hasManageExternalStorageAccess()) {
            Logger.logInfo(TAG, "Skipping .nomedia scan because all files access is not granted")
            return@withContext mediaStoreVideos
        }

        val noMediaVideos = context.getStorageVolumes().flatMap { volume ->
            volume.collectNoMediaVideos()
        }
        if (noMediaVideos.isEmpty()) {
            return@withContext mediaStoreVideos
        }

        Logger.logInfo(TAG, "Found ${noMediaVideos.size} videos inside .nomedia directories")
        return@withContext (mediaStoreVideos + noMediaVideos)
            .distinctBy(MediaVideo::data)
            .sortedBy(MediaVideo::data)
    }

    private suspend fun updateDirectories(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        val directories = context.getStorageVolumes().flatMap {
            getDirectoryEntities(currentFolder = it, media = media)
        }
        directoryDao.upsertAll(directories)

        val currentDirectoryPaths = directories.map { it.path }.toSet()
        val unwantedDirectories = directoryDao.getAll().first()
            .filterNot { it.path in currentDirectoryPaths }
        val unwantedDirectoriesPaths = unwantedDirectories.map { it.path }

        directoryDao.delete(unwantedDirectoriesPaths)
    }

    private fun getDirectoryEntities(
        parentFolder: File? = null,
        currentFolder: File,
        media: List<MediaVideo>,
    ): List<DirectoryEntity> {
        val hasMediaInCurrentFolder = media.any { it.data.startsWith("${currentFolder.path}/") }
        if (!hasMediaInCurrentFolder) return emptyList()

        val currentDirectoryEntity = DirectoryEntity(
            path = currentFolder.path,
            name = currentFolder.prettyName,
            modified = currentFolder.lastModified(),
            parentPath = parentFolder?.path ?: "/",
        )

        val subDirectories = currentFolder.listFiles { file ->
            file.isDirectory && media.any { it.data.startsWith(file.path) }
        }?.flatMap { file ->
            getDirectoryEntities(
                parentFolder = currentFolder,
                currentFolder = file,
                media = media,
            )
        } ?: emptyList()

        return listOf(currentDirectoryEntity) + subDirectories
    }

    private suspend fun updateMedia(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        // 单次查询替代 N 次 mediumDao.get()，同时复用于检测待清理记录
        val allWithInfo = mediumDao.getAllWithInfo().first()
        val existingMediaMap = allWithInfo.associate { it.mediumEntity.uriString to it.mediumEntity }

        val mediumEntities = media.mapNotNull { mediaVideo ->
            val file = File(mediaVideo.data)
            val parentPath = file.parent ?: return@mapNotNull null
            val mediumEntity = existingMediaMap[mediaVideo.uri.toString()]

            mediumEntity?.copy(
                path = file.path,
                name = file.name,
                size = mediaVideo.size,
                width = mediaVideo.width,
                height = mediaVideo.height,
                duration = mediaVideo.duration,
                mediaStoreId = mediaVideo.id,
                modified = mediaVideo.dateModified,
                parentPath = parentPath,
            ) ?: MediumEntity(
                uriString = mediaVideo.uri.toString(),
                path = mediaVideo.data,
                name = file.name,
                parentPath = parentPath,
                modified = mediaVideo.dateModified,
                size = mediaVideo.size,
                width = mediaVideo.width,
                height = mediaVideo.height,
                duration = mediaVideo.duration,
                mediaStoreId = mediaVideo.id,
            )
        }

        mediumDao.upsertAll(mediumEntities)

        val currentMediaUris = mediumEntities.map { it.uriString }.toSet()
        val unwantedMedia = allWithInfo.filterNot { it.mediumEntity.uriString in currentMediaUris }

        if (unwantedMedia.isEmpty()) return@withContext

        val unwantedMediaUris = unwantedMedia.map { it.mediumEntity.uriString }
        val recycleBinUnwantedMediaUris = unwantedMedia.mapNotNull { mediumWithInfo ->
            mediumWithInfo.mediumStateEntity
                ?.takeIf(MediumStateEntity::isInRecycleBin)
                ?.uriString
        }
        val removableMediaUris = unwantedMediaUris - recycleBinUnwantedMediaUris.toSet()

        if (removableMediaUris.isEmpty()) return@withContext

        mediumDao.delete(removableMediaUris)
        mediumStateDao.delete(removableMediaUris)

        unwantedMedia.filter { it.mediumEntity.uriString in removableMediaUris }.forEach { mediumWithInfo ->
            runCatching {
                imageLoader.diskCache?.remove(mediumWithInfo.mediumEntity.uriString)
            }.onFailure { throwable ->
                Logger.logError(TAG, "Failed to clear thumbnail cache for ${mediumWithInfo.mediumEntity.uriString}", throwable)
            }
        }

        launch {
            val currentMediaExternalSubs = mediumEntities.flatMap {
                val mediaState = mediumStateDao.get(it.uriString) ?: return@flatMap emptyList<String>()
                UriListConverter.fromStringToList(mediaState.externalSubs)
            }.toSet()

            unwantedMedia.filter { it.mediumEntity.uriString in removableMediaUris }.onEach { mediumWithInfo ->
                val mediumState = mediumWithInfo.mediumStateEntity ?: return@onEach
                for (sub in UriListConverter.fromStringToList(mediumState.externalSubs)) {
                    if (sub in currentMediaExternalSubs) continue

                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(sub, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }.onFailure { throwable ->
                        Logger.logError(TAG, "Failed to release subtitle permission for $sub", throwable)
                    }
                }
            }
        }
    }

    private fun getMediaVideosFlow(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = "${MediaStore.Video.Media.DISPLAY_NAME} ASC",
    ): Flow<List<MediaVideo>> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(getMediaVideo(selection, selectionArgs, sortOrder))
            }
        }
        context.contentResolver.registerContentObserver(VIDEO_COLLECTION_URI, true, observer)
        trySend(getMediaVideo(selection, selectionArgs, sortOrder))
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(dispatcher).distinctUntilChanged()

    private fun getMediaVideo(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): List<MediaVideo> {
        val mediaVideos = mutableListOf<MediaVideo>()
        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
            val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
            val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                mediaVideos.add(
                    MediaVideo(
                        id = id,
                        data = cursor.getString(dataColumn),
                        duration = cursor.getLong(durationColumn),
                        uri = ContentUris.withAppendedId(VIDEO_COLLECTION_URI, id),
                        width = cursor.getInt(widthColumn),
                        height = cursor.getInt(heightColumn),
                        size = cursor.getLong(sizeColumn),
                        dateModified = cursor.getLong(dateModifiedColumn),
                    ),
                )
            }
        }
        return mediaVideos.filter { File(it.data).exists() }
    }

    private fun File.collectNoMediaVideos(hasNoMediaAncestor: Boolean = false): List<MediaVideo> {
        if (!exists() || !isDirectory) return emptyList()

        val children = runCatching { listFiles()?.toList().orEmpty() }
            .getOrElse { return emptyList() }
        val isNoMediaDirectory = hasNoMediaAncestor ||
            children.any {
                it.isFile && it.name.equals(NO_MEDIA_FILE_NAME, ignoreCase = true)
            }

        val currentDirectoryVideos = if (isNoMediaDirectory) {
            children.filter { it.isVisibleVideoFile() }.mapNotNull { it.toHiddenMediaVideo() }
        } else {
            emptyList()
        }
        val nestedVideos = children.filter(File::isDirectory).flatMap { directory ->
            directory.collectNoMediaVideos(isNoMediaDirectory)
        }

        return currentDirectoryVideos + nestedVideos
    }

    private fun File.isVisibleVideoFile(): Boolean {
        if (!isFile) return false

        if (extension.equals(RECYCLE_BIN_EXTENSION, ignoreCase = true)) return true

        val extensionName = extension.lowercase()
        if (extensionName.isBlank()) return false

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionName)
        return mimeType?.startsWith("video/") == true
    }

    private fun File.toHiddenMediaVideo(): MediaVideo? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(path)
            MediaVideo(
                id = -path.hashCode().toLong().absoluteValue,
                uri = if (extension.equals(RECYCLE_BIN_EXTENSION, ignoreCase = true)) {
                    context.getMediaFileContentUri(path) ?: toUri()
                } else {
                    toUri()
                },
                size = length(),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                data = path,
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                dateModified = lastModified(),
            )
        }.onFailure { throwable ->
            Logger.logError(TAG, "Failed to read hidden media metadata for $path", throwable)
        }.getOrNull().also {
            retriever.release()
        }
    }

    companion object {
        private const val TAG = "LocalMediaSynchronizer"
        private const val NO_MEDIA_FILE_NAME = ".nomedia"
        private const val RECYCLE_BIN_EXTENSION = "optrash"

        val VIDEO_PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
    }
}
