package one.next.player.feature.player.state

import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressPreviewPositionResolverTest {

    @Test
    fun resolvePreviewPosition_advancesWithElapsedTime() {
        val result = resolveLongPressPreviewPosition(
            startPositionMs = 10_000L,
            durationMs = 60_000L,
            elapsedSinceLongPressMs = 2_500L,
            longPressSpeed = 2.0f,
        )

        assertEquals(15_000L, result)
    }

    @Test
    fun resolvePreviewPosition_clampsToDuration() {
        val result = resolveLongPressPreviewPosition(
            startPositionMs = 58_000L,
            durationMs = 60_000L,
            elapsedSinceLongPressMs = 3_000L,
            longPressSpeed = 2.0f,
        )

        assertEquals(60_000L, result)
    }

    @Test
    fun resolvePreviewPosition_fallsBackWhenDurationIsUnknown() {
        val result = resolveLongPressPreviewPosition(
            startPositionMs = 10_000L,
            durationMs = 0L,
            elapsedSinceLongPressMs = 3_000L,
            longPressSpeed = 2.0f,
        )

        assertEquals(16_000L, result)
    }
}
