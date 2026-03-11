package one.next.player.core.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import one.next.player.core.data.repository.fake.FakeMediaRepository
import one.next.player.core.data.repository.fake.FakePreferencesRepository
import one.next.player.core.model.Folder
import one.next.player.core.model.Sort
import one.next.player.core.model.Video
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetSortedFoldersUseCaseTest {

    private val mediaRepository = FakeMediaRepository()
    private val preferencesRepository = FakePreferencesRepository()

    @Test
    fun getSortedFolders_filtersEmptyAndExcludedFolders_thenSortsBySelectedOrder() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val useCase = GetSortedFoldersUseCase(mediaRepository, preferencesRepository, dispatcher)

        preferencesRepository.updateApplicationPreferences {
            it.copy(
                sortBy = Sort.By.TITLE,
                sortOrder = Sort.Order.ASCENDING,
                excludeFolders = listOf("/library/excluded"),
            )
        }
        mediaRepository.directories.addAll(
            listOf(
                testFolder(name = "Folder 10", path = "/library/folder10", videos = listOf(testVideo("folder10.mp4", "/library/folder10/folder10.mp4"))),
                testFolder(name = "Folder 2", path = "/library/folder2", videos = listOf(testVideo("folder2.mp4", "/library/folder2/folder2.mp4"))),
                testFolder(name = "Empty", path = "/library/empty", videos = emptyList()),
                testFolder(name = "Excluded", path = "/library/excluded", videos = listOf(testVideo("excluded.mp4", "/library/excluded/excluded.mp4"))),
            ),
        )

        val folders = useCase().first()

        assertEquals(listOf("Folder 2", "Folder 10"), folders.map(Folder::name))
    }

    @Test
    fun getSortedFolders_hidesFoldersContainingOnlyRecycleBinVideosWhenFeatureEnabled() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val useCase = GetSortedFoldersUseCase(mediaRepository, preferencesRepository, dispatcher)
        val visibleVideo = testVideo("visible.mp4", "/library/visible/visible.mp4")
        val recycledVideo = testVideo("recycled.mp4", "/library/recycled/recycled.mp4")

        preferencesRepository.updateApplicationPreferences {
            it.copy(recycleBinEnabled = true)
        }
        mediaRepository.directories.addAll(
            listOf(
                testFolder(name = "Visible", path = "/library/visible", videos = listOf(visibleVideo)),
                testFolder(name = "Recycled", path = "/library/recycled", videos = listOf(recycledVideo)),
            ),
        )
        mediaRepository.moveVideosToRecycleBin(listOf(recycledVideo.uriString))

        val folders = useCase().first()

        assertEquals(listOf("Visible"), folders.map(Folder::name))
    }

    private fun testFolder(name: String, path: String, videos: List<Video>): Folder = Folder(
        name = name,
        path = path,
        dateModified = 0L,
        parentPath = "/library",
        mediaList = videos,
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
