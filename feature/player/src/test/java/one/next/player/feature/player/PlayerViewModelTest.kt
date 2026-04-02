package one.next.player.feature.player

import android.content.ContextWrapper
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.next.player.core.data.repository.ExternalSubtitleFontSource
import one.next.player.core.data.repository.ExternalSubtitleFontState
import one.next.player.core.data.repository.SubtitleFontRepository
import one.next.player.core.data.repository.fake.FakeMediaRepository
import one.next.player.core.data.repository.fake.FakePreferencesRepository
import one.next.player.core.domain.GetSortedPlaylistUseCase
import one.next.player.core.domain.GetSortedVideosUseCase
import one.next.player.core.model.PlayerControl
import one.next.player.core.model.PlayerControlZone
import one.next.player.core.model.PlayerControlsLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun updatePlayerControlsCustomization_updatesHiddenControlsAndLayout() = runTest(dispatcher) {
        val repository = FakePreferencesRepository()
        val updatedLayout = PlayerControlsLayout().move(
            control = PlayerControl.SCALE,
            toZone = PlayerControlZone.TOP_RIGHT,
            toIndex = 1,
        )
        val viewModel = PlayerViewModel(
            mediaRepository = FakeMediaRepository(),
            preferencesRepository = repository,
            subtitleFontRepository = FakeSubtitleFontRepository(),
            getSortedPlaylistUseCase = GetSortedPlaylistUseCase(
                getSortedVideosUseCase = GetSortedVideosUseCase(
                    mediaRepository = FakeMediaRepository(),
                    preferencesRepository = FakePreferencesRepository(),
                    defaultDispatcher = dispatcher,
                ),
                preferencesRepository = FakePreferencesRepository(),
                context = FakeContext(),
                defaultDispatcher = dispatcher,
            ),
        )

        viewModel.updatePlayerControlsCustomization(
            hiddenControls = setOf(PlayerControl.LOCK, PlayerControl.PIP),
            layout = updatedLayout,
        )
        advanceUntilIdle()

        assertEquals(
            setOf(PlayerControl.LOCK, PlayerControl.PIP),
            repository.playerPreferences.value.hiddenPlayerControls,
        )
        assertEquals(
            updatedLayout,
            repository.playerPreferences.value.playerControlsLayout,
        )
    }

    private class FakeSubtitleFontRepository : SubtitleFontRepository {
        override val state = MutableStateFlow(ExternalSubtitleFontState())
        override val source = MutableStateFlow<ExternalSubtitleFontSource?>(null)

        override suspend fun importFont(uri: Uri) = Unit

        override suspend fun clearFont() = Unit
    }

    private class FakeContext : ContextWrapper(null)
}
