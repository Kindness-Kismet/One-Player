package one.next.player.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import one.next.player.core.data.repository.fake.FakeMediaRepository
import one.next.player.core.data.repository.fake.FakePreferencesRepository
import one.next.player.core.model.Sort
import one.next.player.core.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test

class GetSortedVideosUseCaseTest {

    private val mediaRepository = FakeMediaRepository()
    private val preferencesRepository = FakePreferencesRepository()

    val getSortedVideosUseCase = GetSortedVideosUseCase(mediaRepository, preferencesRepository)

    @Test
    fun testGetSortedVideosUseCase_whenSortByTitleAscending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.TITLE, sortOrder = Sort.Order.ASCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedBy { it.displayName.lowercase() })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortByTitleDescending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.TITLE, sortOrder = Sort.Order.DESCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedByDescending { it.displayName.lowercase() })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortByDurationAscending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.LENGTH, sortOrder = Sort.Order.ASCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedBy { it.duration })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortByDurationDescending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.LENGTH, sortOrder = Sort.Order.DESCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedByDescending { it.duration })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortByPathAscending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.PATH, sortOrder = Sort.Order.ASCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedBy { it.path.lowercase() })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortByPathDescending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.PATH, sortOrder = Sort.Order.DESCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedByDescending { it.path.lowercase() })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortBySizeAscending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.SIZE, sortOrder = Sort.Order.ASCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedBy { it.size })
    }

    @Test
    fun testGetSortedVideosUseCase_whenSortBySizeDescending() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(sortBy = Sort.By.SIZE, sortOrder = Sort.Order.DESCENDING)
        }

        mediaRepository.videos.addAll(testVideoItems.shuffled())

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(sortedVideos, testVideoItems.sortedByDescending { it.size })
    }

    @Test
    fun testGetSortedVideosUseCase_whenExcludedFolderIsParentOfVideoFolder() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(excludeFolders = listOf("/storage/emulated/0/Movies/Private"))
        }

        mediaRepository.videos.addAll(
            listOf(
                Video(
                    id = 1,
                    duration = 1000,
                    uriString = "content://media/external/video/media/1",
                    height = 1920,
                    nameWithExtension = "A.mp4",
                    parentPath = "/storage/emulated/0/Movies/Private/Camera",
                    width = 1080,
                    path = "/storage/emulated/0/Movies/Private/Camera/A.mp4",
                    size = 1000,
                ),
                Video(
                    id = 11,
                    duration = 1000,
                    uriString = "content://media/external/video/media/11",
                    height = 1080,
                    nameWithExtension = "Visible.mp4",
                    parentPath = "/storage/emulated/0/Movies/Public",
                    width = 1920,
                    path = "/storage/emulated/0/Movies/Public/Visible.mp4",
                    size = 1000,
                ),
            ),
        )

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(listOf("/storage/emulated/0/Movies/Public/Visible.mp4"), sortedVideos.map(Video::path))
    }

    @Test
    fun testGetSortedVideosUseCase_hidesRecycleBinVideosFromLibraryList() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(recycleBinEnabled = true)
        }
        mediaRepository.videos.addAll(testVideoItems)
        mediaRepository.moveVideosToRecycleBin(listOf(testVideoItems[0].uriString, testVideoItems[1].uriString))

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(testVideoItems.drop(2).map(Video::uriString), sortedVideos.map(Video::uriString))
    }

    @Test
    fun testGetSortedVideosUseCase_showsRecycleBinVideosWhenFeatureDisabled() = runTest {
        preferencesRepository.updateApplicationPreferences {
            it.copy(recycleBinEnabled = false)
        }
        mediaRepository.videos.addAll(testVideoItems)
        mediaRepository.moveVideosToRecycleBin(listOf(testVideoItems[0].uriString, testVideoItems[1].uriString))

        val sortedVideos = getSortedVideosUseCase().first()

        assertEquals(testVideoItems.map(Video::uriString), sortedVideos.map(Video::uriString))
    }

    @Test
    fun testGetSortedVideosUseCase_returnsRecycleBinVideosAndSupportsRestore() = runTest {
        mediaRepository.videos.addAll(testVideoItems)
        val recycleBinUris = listOf(testVideoItems[0].uriString, testVideoItems[1].uriString)
        mediaRepository.moveVideosToRecycleBin(recycleBinUris)

        val recycleBinVideos = getSortedVideosUseCase(recycleBinOnly = true).first()

        assertEquals(recycleBinUris, recycleBinVideos.map(Video::uriString))

        mediaRepository.restoreVideosFromRecycleBin(recycleBinUris)

        val restoredVideos = getSortedVideosUseCase().first()

        assertEquals(testVideoItems.map(Video::uriString), restoredVideos.map(Video::uriString))
    }
}

/**
 * Sorted video items by title in ascending order and duration in ascending order.
 */
val testVideoItems = listOf(
    Video(
        id = 1,
        duration = 1000,
        uriString = "content://media/external/video/media/1",
        height = 1920,
        nameWithExtension = "A.mp4",
        width = 1080,
        path = "/storage/emulated/0/Movies/Public/A.mp4",
        size = 1000,
    ),
    Video(
        id = 2,
        duration = 2000,
        uriString = "content://media/external/video/media/2",
        height = 1930,
        nameWithExtension = "B.mp4",
        width = 1080,
        path = "/storage/emulated/0/Movies/Public/B.mp4",
        size = 2000,
    ),
    Video(
        id = 3,
        duration = 3000,
        uriString = "content://media/external/video/media/3",
        height = 1940,
        nameWithExtension = "C.mp4",
        width = 1080,
        path = "/storage/emulated/0/Movies/Public/C.mp4",
        size = 3000,
    ),
    Video(
        id = 4,
        duration = 4000,
        uriString = "content://media/external/video/media/4",
        height = 1950,
        nameWithExtension = "D.mp4",
        width = 1080,
        path = "/storage/emulated/0/Movies/Public/D.mp4",
        size = 4000,
    ),
    Video(
        id = 5,
        duration = 5000,
        uriString = "content://media/external/video/media/5",
        height = 1960,
        nameWithExtension = "E.mp4",
        width = 1080,
        path = "/storage/emulated/0/Movies/Public/E.mp4",
        size = 5000,
    ),
    Video(
        id = 6,
        duration = 6000,
        uriString = "content://media/external/video/media/6",
        height = 1970,
        nameWithExtension = "F.mp4",
        width = 1080,
        path = "/storage/emulated/0/Movies/Public/F.mp4",
        size = 6000,
    ),
    Video(
        id = 7,
        duration = 7000,
        uriString = "content://media/external/video/media/7",
        height = 1980,
        nameWithExtension = "G.mp4",
        width = 1080,
        path = "/storage/emulated/0/Movies/Public/G.mp4",
        size = 7000,
    ),
    Video(
        id = 8,
        duration = 8000,
        uriString = "content://media/external/video/media/8",
        height = 1990,
        nameWithExtension = "H.mp4",
        width = 1080,
        path = "/storage/emulated/0/Movies/Public/H.mp4",
        size = 8000,
    ),
    Video(
        id = 9,
        duration = 9000,
        uriString = "content://media/external/video/media/9",
        height = 2160,
        nameWithExtension = "I.mp4",
        width = 1080,
        path = "/storage/emulated/0/Movies/Public/I.mp4",
        size = 9000,
    ),
    Video(
        id = 10,
        duration = 10000,
        uriString = "content://media/external/video/media/10",
        height = 2170,
        nameWithExtension = "J.mp4",
        width = 1080,
        path = "/storage/emulated/0/Movies/Public/J.mp4",
        size = 10000,
    ),
)
