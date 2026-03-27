package one.next.player.settings.screens.gesture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import one.next.player.core.data.repository.fake.FakePreferencesRepository
import one.next.player.core.model.PlayerPreferences
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GesturePreferencesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun toggleUseLongPressVariableSpeed_keepsItDisabledWhenLongPressControlsAreOff() = runTest(dispatcher) {
        val repository = FakePreferencesRepository()
        repository.updatePlayerPreferences {
            it.copy(
                shouldUseLongPressControls = false,
                shouldUseLongPressVariableSpeed = false,
                longPressControlsSpeed = PlayerPreferences.DEFAULT_LONG_PRESS_CONTROLS_SPEED,
            )
        }
        val viewModel = GesturePreferencesViewModel(repository)

        viewModel.onEvent(GesturePreferencesUiEvent.ToggleUseLongPressVariableSpeed)
        advanceUntilIdle()

        assertFalse(repository.playerPreferences.value.shouldUseLongPressControls)
        assertFalse(repository.playerPreferences.value.shouldUseLongPressVariableSpeed)
    }
}
