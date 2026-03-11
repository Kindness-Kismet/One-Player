package one.next.player.feature.videopicker.screens.mediapicker

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import one.next.player.core.model.ApplicationPreferences
import one.next.player.core.model.Folder
import one.next.player.core.model.Sort
import one.next.player.core.model.Video
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaPickerSnapshotCacheTest {

    private lateinit var cacheDir: File
    private lateinit var testScope: TestScope
    private lateinit var cache: MediaPickerSnapshotCache

    @Before
    fun setup() {
        cacheDir = File(System.getProperty("java.io.tmpdir"), "test_snapshots_${System.nanoTime()}")
        cacheDir.mkdirs()
        val dispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(dispatcher)
        cache = MediaPickerSnapshotCache(
            cacheDir = cacheDir,
            scope = testScope,
            ioDispatcher = dispatcher,
        )
    }

    @After
    fun teardown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun get_returnsNullForEmptyCache() {
        assertNull(cache.get(null, defaultPrefs()))
    }

    @Test
    fun putThenGet_returnsFolder() {
        val folder = testFolder("Movies", "/storage/Movies")
        cache.put("/storage/Movies", folder, defaultPrefs())

        val result = cache.get("/storage/Movies", defaultPrefs())
        assertEquals(folder, result)
    }

    @Test
    fun get_returnsNullForDifferentSortPreferences() {
        val folder = testFolder("Movies", "/storage/Movies")
        val titleSort = defaultPrefs().copy(sortBy = Sort.By.TITLE)
        val sizeSort = defaultPrefs().copy(sortBy = Sort.By.SIZE)

        cache.put("/storage/Movies", folder, titleSort)

        assertNotNull(cache.get("/storage/Movies", titleSort))
        assertNull(cache.get("/storage/Movies", sizeSort))
    }

    @Test
    fun get_returnsNullForDifferentRecycleBinPreference() {
        val folder = testFolder("Movies", "/storage/Movies")
        val recycleEnabled = defaultPrefs().copy(recycleBinEnabled = true)
        val recycleDisabled = defaultPrefs().copy(recycleBinEnabled = false)

        cache.put("/storage/Movies", folder, recycleEnabled)

        assertNotNull(cache.get("/storage/Movies", recycleEnabled))
        assertNull(cache.get("/storage/Movies", recycleDisabled))
    }

    @Test
    fun get_returnsNullForDifferentPath() {
        val folder = testFolder("Movies", "/storage/Movies")
        cache.put("/storage/Movies", folder, defaultPrefs())

        assertNull(cache.get("/storage/Downloads", defaultPrefs()))
    }

    @Test
    fun put_overwritesPreviousValueForSameKey() {
        val folder1 = testFolder("Old", "/storage/Movies")
        val folder2 = testFolder("New", "/storage/Movies")
        val prefs = defaultPrefs()

        cache.put("/storage/Movies", folder1, prefs)
        cache.put("/storage/Movies", folder2, prefs)

        assertEquals(folder2, cache.get("/storage/Movies", prefs))
        assertEquals(1, cache.size)
    }

    @Test
    fun trimToMaxSize_evictsOldestEntries() {
        val prefs = defaultPrefs()

        for (i in 0..MediaPickerSnapshotCache.MAX_SNAPSHOT_COUNT) {
            cache.put("/folder/$i", testFolder("F$i", "/folder/$i"), prefs)
        }

        assertNull(cache.get("/folder/0", prefs))
        assertNotNull(cache.get("/folder/${MediaPickerSnapshotCache.MAX_SNAPSHOT_COUNT}", prefs))
        assertEquals(MediaPickerSnapshotCache.MAX_SNAPSHOT_COUNT, cache.size)
    }

    @Test
    fun clear_removesAllEntries() {
        val prefs = defaultPrefs()
        cache.put("/a", testFolder("A", "/a"), prefs)
        cache.put("/b", testFolder("B", "/b"), prefs)

        cache.clear()

        assertNull(cache.get("/a", prefs))
        assertNull(cache.get("/b", prefs))
        assertEquals(0, cache.size)
    }

    @Test
    fun diskPersistence_newCacheReadsFromDisk() {
        val prefs = defaultPrefs()
        val folder = testFolder("Movies", "/storage/Movies")

        cache.put("/storage/Movies", folder, prefs)
        testScope.testScheduler.advanceUntilIdle()

        val cache2 = MediaPickerSnapshotCache(
            cacheDir = cacheDir,
            scope = testScope,
            ioDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
        )
        testScope.testScheduler.advanceUntilIdle()

        val result = cache2.get("/storage/Movies", prefs)
        assertNotNull(result)
        assertEquals(folder.name, result!!.name)
        assertEquals(folder.path, result.path)
    }

    @Test
    fun diskPersistence_clearRemovesDiskFiles() {
        val prefs = defaultPrefs()
        cache.put("/a", testFolder("A", "/a"), prefs)
        testScope.testScheduler.advanceUntilIdle()

        cache.clear()
        testScope.testScheduler.advanceUntilIdle()

        val cache2 = MediaPickerSnapshotCache(
            cacheDir = cacheDir,
            scope = testScope,
            ioDispatcher = UnconfinedTestDispatcher(testScope.testScheduler),
        )
        testScope.testScheduler.advanceUntilIdle()

        assertNull(cache2.get("/a", prefs))
    }

    @Test
    fun diskPersistence_memoryEntryTakesPrecedenceOverDisk() {
        val prefs = defaultPrefs()
        val diskFolder = testFolder("DiskVersion", "/storage/Movies")
        val memFolder = testFolder("MemVersion", "/storage/Movies")

        cache.put("/storage/Movies", diskFolder, prefs)
        testScope.testScheduler.advanceUntilIdle()

        cache.put("/storage/Movies", memFolder, prefs)

        assertEquals(memFolder, cache.get("/storage/Movies", prefs))
    }

    private fun defaultPrefs() = ApplicationPreferences()

    private fun testFolder(name: String, path: String): Folder = Folder(
        name = name,
        path = path,
        dateModified = 0L,
        mediaList = listOf(testVideo("$name.mp4", "$path/$name.mp4")),
    )

    private fun testVideo(name: String, path: String): Video = Video(
        id = path.hashCode().toLong(),
        path = path,
        parentPath = path.substringBeforeLast('/'),
        duration = 1_000L,
        uriString = "content://$path",
        nameWithExtension = name,
        width = 1920,
        height = 1080,
        size = 1_000L,
    )
}
