package one.next.player.feature.player.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeyboardControllerTest {

    @Test
    fun handleKeyEvent_seeksBackwardOnLeftKeyUp() {
        val state = FakeKeyboardState()
        val controller = createController(state)

        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0))
        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0))

        assertEquals(1, state.seekBackwardCalls)
        assertEquals(0, state.seekForwardCalls)
    }

    @Test
    fun handleKeyEvent_seeksForwardOnShortRightKeyPress() {
        val state = FakeKeyboardState()
        val controller = createController(state)

        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0))
        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0))

        assertEquals(0, state.temporarySpeedStartCalls)
        assertEquals(1, state.seekForwardCalls)
    }

    @Test
    fun handleKeyEvent_startsAndStopsTemporarySpeedOnSpaceLongPress() {
        val state = FakeKeyboardState()
        val controller = createController(state)

        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 0))
        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 1))
        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE, 1))

        assertEquals(1, state.temporarySpeedStartCalls)
        assertEquals(1, state.temporarySpeedStopCalls)
        assertEquals(0, state.seekForwardCalls)
        assertEquals(0, state.togglePlaybackCalls)
    }

    @Test
    fun handleKeyEvent_increasesAndDecreasesVolumeOnArrowKeys() {
        val state = FakeKeyboardState()
        val controller = createController(state)

        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0))
        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0))

        assertEquals(1, state.increaseVolumeCalls)
        assertEquals(1, state.decreaseVolumeCalls)
    }

    @Test
    fun handleKeyEvent_togglesPlaybackOnSpaceAndEnter() {
        val state = FakeKeyboardState()
        val controller = createController(state)

        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 0))
        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE, 0))
        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))

        assertEquals(2, state.togglePlaybackCalls)
    }

    @Test
    fun handleKeyEvent_ignoresCanceledKeyUpActions() {
        val state = FakeKeyboardState()
        val controller = createController(state)

        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 0))
        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE, 0, isCanceled = true))
        assertTrue(controller.handleKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, isCanceled = true))

        assertEquals(0, state.temporarySpeedStopCalls)
        assertEquals(0, state.togglePlaybackCalls)
    }

    @Test
    fun handleKeyEvent_returnsFalseForUnhandledKeys() {
        val state = FakeKeyboardState()
        val controller = createController(state)

        assertFalse(controller.handleKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0))
    }

    private fun createController(state: FakeKeyboardState): PlayerKeyboardController = PlayerKeyboardController(
        onSeekBackward = { state.seekBackwardCalls += 1 },
        onSeekForward = { state.seekForwardCalls += 1 },
        onIncreaseVolume = { state.increaseVolumeCalls += 1 },
        onDecreaseVolume = { state.decreaseVolumeCalls += 1 },
        onTogglePlayPause = { state.togglePlaybackCalls += 1 },
        onStartTemporarySpeed = {
            state.temporarySpeedStartCalls += 1
            true
        },
        onStopTemporarySpeed = { state.temporarySpeedStopCalls += 1 },
    )

    private data class FakeKeyboardState(
        var seekBackwardCalls: Int = 0,
        var seekForwardCalls: Int = 0,
        var increaseVolumeCalls: Int = 0,
        var decreaseVolumeCalls: Int = 0,
        var togglePlaybackCalls: Int = 0,
        var temporarySpeedStartCalls: Int = 0,
        var temporarySpeedStopCalls: Int = 0,
    )
}
