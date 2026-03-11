package one.next.player.core.media.sync

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
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
    private val mediaInfoSynchronizer: MediaInfoSynchronizer,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO) private val dispatcher: CoroutineDispatcher,
) : MediaSynchronizer {

    private var mediaSyncingJob: Job? = null

    override suspend fun refresh(path: String?): Boolean = withContext(dispatcher) {
        path?.let {
            registerManualVideoPath(it)
            mergePendingManualVideoPaths()
            return@withContext context.scanPaths(listOf(it))
        }

        mergePendingManualVideoPaths()
        val additionalScanTargets = buildRefreshScanTargets()
        if (additionalScanTargets.isNotEmpty()) {
            registerUnindexedPaths(additionalScanTargets)
            context.scanPaths(additionalScanTargets)
        }
        true
    }

    override suspend fun registerManualVideoPath(path: String) {
        if (path.isBlank()) return

        val canonicalPath = runCatching { File(path).canonicalPath }.getOrDefault(path)
        Logger.logInfo(TAG, "registerManualVideoPath path=$canonicalPath")
        appPreferencesDataSource.update { preferences ->
            val pendingPaths = preferences.pendingExternalVideoPaths + canonicalPath
            preferences.copy(
                pendingExternalVideoPaths = pendingPaths.distinct(),
            )
        }
    }

    // 将文件系统发现但 MediaStore 未收录的路径持久化，确保同步时能兜底
    private suspend fun registerUnindexedPaths(paths: List<String>) {
        if (paths.isEmpty()) return
        Logger.logInfo(TAG, "registerUnindexedPaths count=${paths.size}")
        appPreferencesDataSource.update {
            it.copy(manualVideoPaths = (it.manualVideoPaths + paths).distinct())
        }
    }

    private suspend fun mergePendingManualVideoPaths() {
        val preferences = appPreferencesDataSource.preferences.first()
        if (preferences.pendingExternalVideoPaths.isEmpty()) return

        Logger.logInfo(
            TAG,
            "mergePendingManualVideoPaths pending=${preferences.pendingExternalVideoPaths.size} manual=${preferences.manualVideoPaths.size}",
        )
        appPreferencesDataSource.update {
            it.copy(
                manualVideoPaths = (it.manualVideoPaths + it.pendingExternalVideoPaths).distinct(),
                pendingExternalVideoPaths = emptyList(),
            )
        }
    }

    override fun startSync() {
        if (mediaSyncingJob != null) return

        Logger.logInfo(TAG, "Starting media sync")
        mediaSyncingJob = combine(
            getMediaVideosFlow(),
            appPreferencesDataSource.preferences,
        ) { mediaStoreVideos, preferences ->
            mergeVisibleMedia(
                mediaStoreVideos = mediaStoreVideos,
                manuallyDiscoveredPaths = preferences.manualVideoPaths.toSet(),
                includeNoMediaDirectories = preferences.ignoreNoMediaFiles,
            )
        }.onEach { media ->
            Logger.logInfo(TAG, "onEach syncing ${media.size} media entries")
            applicationScope.launch { updateDirectories(media) }
            applicationScope.launch {
                updateMedia(media)
                scheduleMediaInfoSync()
            }
        }.launchIn(applicationScope)
    }

    override fun stopSync() {
        mediaSyncingJob?.cancel()
        mediaSyncingJob = null
        Logger.logInfo(TAG, "Stopped media sync")
    }

    private suspend fun mergeVisibleMedia(
        mediaStoreVideos: List<MediaVideo>,
        manuallyDiscoveredPaths: Set<String>,
        includeNoMediaDirectories: Boolean,
    ): List<MediaVideo> = withContext(dispatcher) {
        if (manuallyDiscoveredPaths.isNotEmpty()) {
            Logger.logInfo(TAG, "mergeVisibleMedia manualPaths=${manuallyDiscoveredPaths.size}")
        }
        val manuallyDiscoveredVideos = manuallyDiscoveredPaths
            .asSequence()
            .map(::File)
            .filter(File::exists)
            .filter { it.isVisibleVideoFile() }
            .map { it.toBasicMediaVideo() }
            .toList()

        val combinedVisibleMedia = mediaStoreVideos + manuallyDiscoveredVideos
        Logger.logInfo(
            TAG,
            "mergeVisibleMedia result mediaStore=${mediaStoreVideos.size} manual=${manuallyDiscoveredVideos.size} combined=${combinedVisibleMedia.size} noMedia=$includeNoMediaDirectories manageAccess=${hasManageExternalStorageAccess()}",
        )
        if (!includeNoMediaDirectories || !hasManageExternalStorageAccess()) {
            return@withContext combinedVisibleMedia
                .distinctBy(MediaVideo::data)
                .sortedBy(MediaVideo::data)
        }

        val noMediaVideos = context.getStorageVolumes().flatMap { volume ->
            volume.collectNoMediaVideos()
        }
        if (noMediaVideos.isEmpty()) {
            return@withContext combinedVisibleMedia
                .distinctBy(MediaVideo::data)
                .sortedBy(MediaVideo::data)
        }

        Logger.logInfo(TAG, "Found ${noMediaVideos.size} videos inside .nomedia directories")
        return@withContext (combinedVisibleMedia + noMediaVideos)
            .distinctBy(MediaVideo::data)
            .sortedBy(MediaVideo::data)
    }

    private fun buildRefreshScanTargets(): List<String> {
        val indexedPaths = getMediaVideo(selection = null, selectionArgs = null, sortOrder = null)
            .map { it.data }
            .toHashSet()

        val targets = context.getStorageVolumes().flatMap { volume ->
            volume.collectVisibleUnindexedVideoPaths(indexedPaths)
        }.distinct()

        if (targets.isNotEmpty()) {
            Logger.logInfo(TAG, "Refreshing ${targets.size} unindexed video files")
        }
        return targets
    }

    private suspend fun updateDirectories(media: List<MediaVideo>) = withContext(Dispatchers.Default) {
        val directories = buildDirectoryEntities(media)
        directoryDao.upsertAll(directories)

        val currentDirectoryPaths = directories.map { it.path }.toSet()
        val unwantedDirectories = directoryDao.getAll().first()
            .filterNot { it.path in currentDirectoryPaths }
        val unwantedDirectoriesPaths = unwantedDirectories.map { it.path }

        directoryDao.delete(unwantedDirectoriesPaths)
    }

    private fun buildDirectoryEntities(media: List<MediaVideo>): List<DirectoryEntity> {
        if (media.isEmpty()) return emptyList()

        val storageVolumes = context.getStorageVolumes()
        val volumePaths = storageVolumes.map { it.path }.toSet()
        val directoryPaths = linkedSetOf<String>()

        media.forEach { mediaVideo ->
            var currentPath = File(mediaVideo.data).parent ?: return@forEach
            while (directoryPaths.add(currentPath)) {
                if (currentPath in volumePaths) break
                currentPath = File(currentPath).parent ?: break
            }
        }

        return directoryPaths.map { path ->
            val directory = File(path)
            val parentPath = directory.parent?.takeIf { it in directoryPaths } ?: "/"
            DirectoryEntity(
                path = path,
                name = directory.prettyName,
                modified = directory.lastModified(),
                parentPath = parentPath,
            )
        }
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
                width = mediaVideo.width.takeIf { it > 0 } ?: mediumEntity.width,
                height = mediaVideo.height.takeIf { it > 0 } ?: mediumEntity.height,
                duration = mediaVideo.duration.takeIf { it > 0 } ?: mediumEntity.duration,
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

    // 对缺少元数据的媒体自动排队 MediaInfo 同步
    private suspend fun scheduleMediaInfoSync() {
        val allWithInfo = mediumDao.getAllWithInfo().first()
        var count = 0
        allWithInfo.forEach { mediumWithInfo ->
            val entity = mediumWithInfo.mediumEntity
            val needsMetadata = entity.duration <= 0 || entity.width <= 0 || entity.height <= 0
            val needsStreamInfo = mediumWithInfo.videoStreamInfo == null
            if (needsMetadata || needsStreamInfo) {
                mediaInfoSynchronizer.sync(entity.uriString.toUri())
                count++
            }
        }
        if (count > 0) {
            Logger.logInfo(TAG, "scheduleMediaInfoSync queued $count items")
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

    private fun File.collectVisibleUnindexedVideoPaths(indexedPaths: Set<String>): List<String> {
        if (!exists() || !isDirectory) return emptyList()
        if (name.equals("Android", ignoreCase = true)) return emptyList()

        val children = runCatching { listFiles()?.toList().orEmpty() }
            .getOrElse { return emptyList() }
        val hasNoMedia = children.any {
            it.isFile && it.name.equals(NO_MEDIA_FILE_NAME, ignoreCase = true)
        }
        if (hasNoMedia) return emptyList()

        val currentPaths = children
            .filter { it.isVisibleVideoFile() && it.path !in indexedPaths }
            .map { it.path }
        val nestedPaths = children
            .filter(File::isDirectory)
            .flatMap { directory -> directory.collectVisibleUnindexedVideoPaths(indexedPaths) }

        return currentPaths + nestedPaths
    }

    private fun File.isVisibleVideoFile(): Boolean {
        if (!isFile) return false

        if (extension.equals(RECYCLE_BIN_EXTENSION, ignoreCase = true)) return true

        val extensionName = extension.lowercase()
        if (extensionName.isBlank()) return false
        if (extensionName in KNOWN_VIDEO_EXTENSIONS) return true

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensionName)
        return mimeType?.startsWith("video/") == true
    }

    // 不使用 MediaMetadataRetriever，避免大文件解析阻塞
    private fun File.toBasicMediaVideo(): MediaVideo = MediaVideo(
        id = -path.hashCode().toLong().absoluteValue,
        uri = toUri(),
        size = length(),
        width = 0,
        height = 0,
        data = path,
        duration = 0L,
        dateModified = lastModified(),
    )

    private fun File.toHiddenMediaVideo(): MediaVideo? = toMediaVideo(
        uri = toUri(),
        errorMessage = "Failed to read hidden media metadata for $path",
    )

    private fun File.toMediaVideo(
        uri: Uri,
        errorMessage: String,
    ): MediaVideo? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(path)
            MediaVideo(
                id = -path.hashCode().toLong().absoluteValue,
                uri = if (extension.equals(RECYCLE_BIN_EXTENSION, ignoreCase = true)) {
                    context.getMediaFileContentUri(path) ?: toUri()
                } else {
                    uri
                },
                size = length(),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                data = path,
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                dateModified = lastModified(),
            )
        }.onFailure { throwable ->
            Logger.logError(TAG, errorMessage, throwable)
        }.getOrNull().also {
            retriever.release()
        }
    }

    companion object {
        private const val TAG = "LocalMediaSynchronizer"
        private const val NO_MEDIA_FILE_NAME = ".nomedia"
        private const val RECYCLE_BIN_EXTENSION = "optrash"
        private val KNOWN_VIDEO_EXTENSIONS = setOf(
            "3gp",
            "asf",
            "avi",
            "flv",
            "m2ts",
            "m4v",
            "mkv",
            "mov",
            "mp4",
            "mpeg",
            "mpg",
            "mts",
            "rmvb",
            "ts",
            "webm",
            "wmv",
        )

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
