package one.next.player.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LongPressOverlayUiStateTest {

    @Test
    fun overlayUiState_isHiddenWhenNoLongPressOrDebugOverrideIsActive() {
        val result = resolveLongPressOverlayUiState(
            isLongPressGestureInAction = false,
            longPressSpeed = 2.0f,
            longPressPreviewPositionMs = 15_000L,
        )

        assertNull(result)
    }

    @Test
    fun overlayUiState_showsSpeedAndPositionDuringLongPress() {
        val result = resolveLongPressOverlayUiState(
            isLongPressGestureInAction = true,
            longPressSpeed = 2.0f,
            longPressPreviewPositionMs = 15_000L,
        )

        assertEquals("2.0x", result?.speedText)
        assertEquals("00:15", result?.positionText)
    }
}
