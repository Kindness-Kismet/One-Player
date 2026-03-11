package one.next.player.feature.videopicker.screens.mediapicker

import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.next.player.core.common.Logger
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.Folder
import one.next.player.core.model.MediaViewMode
import one.next.player.core.model.Sort

// 文件夹快照缓存，兼顾内存与磁盘持久化，冷启动后仍可命中
class MediaPickerSnapshotCache(
    private val cacheDir: File,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val snapshots = LinkedHashMap<SnapshotKey, Folder?>()

    init {
        scope.launch { loadFromDisk() }
    }

    @Synchronized
    fun get(folderPath: String?, preferences: ApplicationPreferences): Folder? = snapshots[buildKey(folderPath, preferences)]

    @Synchronized
    fun put(folderPath: String?, folder: Folder?, preferences: ApplicationPreferences) {
        val key = buildKey(folderPath, preferences)
        snapshots[key] = folder
        trimToMaxSize()
        scope.launch { writeToDisk(key, folder) }
    }

    @Synchronized
    fun clear() {
        snapshots.clear()
        scope.launch { clearDisk() }
    }

    internal val size: Int
        @Synchronized get() = snapshots.size

    @Synchronized
    private fun trimToMaxSize() {
        val evicted = mutableListOf<SnapshotKey>()
        while (snapshots.size > MAX_SNAPSHOT_COUNT) {
            val iter = snapshots.entries.iterator()
            if (iter.hasNext()) {
                evicted.add(iter.next().key)
                iter.remove()
            }
        }
        if (evicted.isNotEmpty()) {
            scope.launch { evicted.forEach { deleteFile(it) } }
        }
    }

    private suspend fun loadFromDisk() = withContext(ioDispatcher) {
        if (!cacheDir.exists()) return@withContext

        val files = cacheDir.listFiles { f -> f.extension == FILE_EXT } ?: return@withContext
        for (file in files) {
            try {
                ObjectInputStream(file.inputStream().buffered()).use { stream ->
                    val entry = stream.readObject() as? SnapshotEntry ?: return@use
                    synchronized(this@MediaPickerSnapshotCache) {
                        if (!snapshots.containsKey(entry.key)) {
                            snapshots[entry.key] = entry.folder
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.logError(TAG, "Failed to read snapshot ${file.name}", e)
                file.delete()
            }
        }
    }

    private suspend fun writeToDisk(key: SnapshotKey, folder: Folder?) = withContext(ioDispatcher) {
        try {
            cacheDir.mkdirs()
            val file = File(cacheDir, key.fileName())
            ObjectOutputStream(file.outputStream().buffered()).use { stream ->
                stream.writeObject(SnapshotEntry(key, folder))
            }
        } catch (e: Exception) {
            Logger.logError(TAG, "Failed to write snapshot", e)
        }
    }

    private suspend fun deleteFile(key: SnapshotKey) = withContext(ioDispatcher) {
        runCatching { File(cacheDir, key.fileName()).delete() }
    }

    private suspend fun clearDisk() = withContext(ioDispatcher) {
        runCatching { cacheDir.deleteRecursively() }
    }

    private fun buildKey(folderPath: String?, prefs: ApplicationPreferences): SnapshotKey = SnapshotKey(
        folderPath = folderPath,
        mediaViewMode = prefs.mediaViewMode,
        sortBy = prefs.sortBy,
        sortOrder = prefs.sortOrder,
        ignoreNoMediaFiles = prefs.ignoreNoMediaFiles,
        recycleBinEnabled = prefs.recycleBinEnabled,
        excludeFolders = prefs.excludeFolders,
    )

    private fun SnapshotKey.fileName(): String = "s_${hashCode().toUInt()}.$FILE_EXT"

    internal data class SnapshotKey(
        val folderPath: String?,
        val mediaViewMode: MediaViewMode,
        val sortBy: Sort.By,
        val sortOrder: Sort.Order,
        val ignoreNoMediaFiles: Boolean,
        val recycleBinEnabled: Boolean,
        val excludeFolders: List<String>,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    private data class SnapshotEntry(
        val key: SnapshotKey,
        val folder: Folder?,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        private const val TAG = "SnapshotCache"
        private const val FILE_EXT = "bin"
        internal const val MAX_SNAPSHOT_COUNT = 32
    }
}
