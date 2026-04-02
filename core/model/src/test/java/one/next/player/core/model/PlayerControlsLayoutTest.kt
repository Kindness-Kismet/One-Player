package one.next.player.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerControlsLayoutTest {

    @Test
    fun defaultLayout_placesCustomizableControlsInExpectedZones() {
        val layout = PlayerControlsLayout()

        assertEquals(
            listOf(
                PlayerControl.PLAYLIST,
                PlayerControl.PLAYBACK_SPEED,
                PlayerControl.AUDIO,
                PlayerControl.SUBTITLE,
            ),
            layout.controlsIn(PlayerControlZone.TOP_RIGHT),
        )
        assertEquals(
            listOf(
                PlayerControl.LOCK,
                PlayerControl.SCALE,
                PlayerControl.PIP,
                PlayerControl.SCREENSHOT,
                PlayerControl.BACKGROUND_PLAY,
                PlayerControl.LOOP,
                PlayerControl.SHUFFLE,
            ),
            layout.controlsIn(PlayerControlZone.BOTTOM_LEFT),
        )
    }

    @Test
    fun move_movesControlAcrossZonesAtRequestedIndex() {
        val movedLayout = PlayerControlsLayout().move(
            control = PlayerControl.SCALE,
            toZone = PlayerControlZone.TOP_RIGHT,
            toIndex = 1,
        )

        assertEquals(
            listOf(
                PlayerControl.PLAYLIST,
                PlayerControl.SCALE,
                PlayerControl.PLAYBACK_SPEED,
                PlayerControl.AUDIO,
                PlayerControl.SUBTITLE,
            ),
            movedLayout.controlsIn(PlayerControlZone.TOP_RIGHT),
        )
        assertEquals(
            listOf(
                PlayerControl.LOCK,
                PlayerControl.PIP,
                PlayerControl.SCREENSHOT,
                PlayerControl.BACKGROUND_PLAY,
                PlayerControl.LOOP,
                PlayerControl.SHUFFLE,
            ),
            movedLayout.controlsIn(PlayerControlZone.BOTTOM_LEFT),
        )
    }

    @Test
    fun normalized_restoresMissingControlsAndDropsUnsupportedOnes() {
        val normalizedLayout = PlayerControlsLayout(
            entries = listOf(
                PlayerControlLayoutEntry(PlayerControl.PLAYBACK_SPEED, PlayerControlZone.TOP_RIGHT),
                PlayerControlLayoutEntry(PlayerControl.BACK, PlayerControlZone.TOP_RIGHT),
                PlayerControlLayoutEntry(PlayerControl.LOCK, PlayerControlZone.BOTTOM_LEFT),
                PlayerControlLayoutEntry(PlayerControl.LOCK, PlayerControlZone.TOP_RIGHT),
            ),
        )

        assertEquals(
            listOf(
                PlayerControl.PLAYBACK_SPEED,
                PlayerControl.PLAYLIST,
                PlayerControl.AUDIO,
                PlayerControl.SUBTITLE,
            ),
            normalizedLayout.controlsIn(PlayerControlZone.TOP_RIGHT),
        )
        assertEquals(
            listOf(
                PlayerControl.LOCK,
                PlayerControl.SCALE,
                PlayerControl.PIP,
                PlayerControl.SCREENSHOT,
                PlayerControl.BACKGROUND_PLAY,
                PlayerControl.LOOP,
                PlayerControl.SHUFFLE,
            ),
            normalizedLayout.controlsIn(PlayerControlZone.BOTTOM_LEFT),
        )
    }

    @Test
    fun controlsIn_returnsNormalizedControlsOnly() {
        val layout = PlayerControlsLayout(
            entries = listOf(
                PlayerControlLayoutEntry(PlayerControl.BACK, PlayerControlZone.TOP_RIGHT),
                PlayerControlLayoutEntry(PlayerControl.PLAYBACK_SPEED, PlayerControlZone.TOP_RIGHT),
                PlayerControlLayoutEntry(PlayerControl.PLAYBACK_SPEED, PlayerControlZone.BOTTOM_LEFT),
                PlayerControlLayoutEntry(PlayerControl.LOCK, PlayerControlZone.BOTTOM_LEFT),
            ),
        )

        assertEquals(
            listOf(
                PlayerControl.PLAYBACK_SPEED,
                PlayerControl.PLAYLIST,
                PlayerControl.AUDIO,
                PlayerControl.SUBTITLE,
            ),
            layout.controlsIn(PlayerControlZone.TOP_RIGHT),
        )
        assertEquals(
            listOf(
                PlayerControl.LOCK,
                PlayerControl.SCALE,
                PlayerControl.PIP,
                PlayerControl.SCREENSHOT,
                PlayerControl.BACKGROUND_PLAY,
                PlayerControl.LOOP,
                PlayerControl.SHUFFLE,
            ),
            layout.controlsIn(PlayerControlZone.BOTTOM_LEFT),
        )
    }

    @Test
    fun move_returnsSameLayoutWhenControlIsNotCustomizable() {
        val layout = PlayerControlsLayout(
            entries = listOf(
                PlayerControlLayoutEntry(PlayerControl.PLAYBACK_SPEED, PlayerControlZone.TOP_RIGHT),
                PlayerControlLayoutEntry(PlayerControl.LOCK, PlayerControlZone.BOTTOM_LEFT),
            ),
        )

        assertEquals(
            layout,
            layout.move(
                control = PlayerControl.BACK,
                toZone = PlayerControlZone.TOP_RIGHT,
                toIndex = 0,
            ),
        )
    }

    @Test
    fun constructor_normalizesEntriesImmediately() {
        val layout = PlayerControlsLayout(
            entries = listOf(
                PlayerControlLayoutEntry(PlayerControl.BACK, PlayerControlZone.TOP_RIGHT),
                PlayerControlLayoutEntry(PlayerControl.PLAYBACK_SPEED, PlayerControlZone.TOP_RIGHT),
                PlayerControlLayoutEntry(PlayerControl.LOCK, PlayerControlZone.BOTTOM_LEFT),
                PlayerControlLayoutEntry(PlayerControl.LOCK, PlayerControlZone.TOP_RIGHT),
            ),
        )

        assertEquals(
            PlayerControlsLayout(
                entries = listOf(
                    PlayerControlLayoutEntry(PlayerControl.PLAYBACK_SPEED, PlayerControlZone.TOP_RIGHT),
                    PlayerControlLayoutEntry(PlayerControl.LOCK, PlayerControlZone.BOTTOM_LEFT),
                    PlayerControlLayoutEntry(PlayerControl.PLAYLIST, PlayerControlZone.TOP_RIGHT),
                    PlayerControlLayoutEntry(PlayerControl.AUDIO, PlayerControlZone.TOP_RIGHT),
                    PlayerControlLayoutEntry(PlayerControl.SUBTITLE, PlayerControlZone.TOP_RIGHT),
                    PlayerControlLayoutEntry(PlayerControl.SCALE, PlayerControlZone.BOTTOM_LEFT),
                    PlayerControlLayoutEntry(PlayerControl.PIP, PlayerControlZone.BOTTOM_LEFT),
                    PlayerControlLayoutEntry(PlayerControl.SCREENSHOT, PlayerControlZone.BOTTOM_LEFT),
                    PlayerControlLayoutEntry(PlayerControl.BACKGROUND_PLAY, PlayerControlZone.BOTTOM_LEFT),
                    PlayerControlLayoutEntry(PlayerControl.LOOP, PlayerControlZone.BOTTOM_LEFT),
                    PlayerControlLayoutEntry(PlayerControl.SHUFFLE, PlayerControlZone.BOTTOM_LEFT),
                ),
            ),
            layout,
        )
    }

    @Test
    fun move_clampsTargetIndexWithinZoneBounds() {
        val movedToFront = PlayerControlsLayout().move(
            control = PlayerControl.SHUFFLE,
            toZone = PlayerControlZone.TOP_RIGHT,
            toIndex = -1,
        )
        val movedToEnd = PlayerControlsLayout().move(
            control = PlayerControl.PLAYLIST,
            toZone = PlayerControlZone.BOTTOM_LEFT,
            toIndex = 999,
        )

        assertEquals(
            listOf(
                PlayerControl.SHUFFLE,
                PlayerControl.PLAYLIST,
                PlayerControl.PLAYBACK_SPEED,
                PlayerControl.AUDIO,
                PlayerControl.SUBTITLE,
            ),
            movedToFront.controlsIn(PlayerControlZone.TOP_RIGHT),
        )
        assertEquals(
            listOf(
                PlayerControl.LOCK,
                PlayerControl.SCALE,
                PlayerControl.PIP,
                PlayerControl.SCREENSHOT,
                PlayerControl.BACKGROUND_PLAY,
                PlayerControl.LOOP,
                PlayerControl.SHUFFLE,
                PlayerControl.PLAYLIST,
            ),
            movedToEnd.controlsIn(PlayerControlZone.BOTTOM_LEFT),
        )
    }

    @Test
    fun serialization_roundTripKeepsNormalizedLayout() {
        val json = Json
        val layout = PlayerControlsLayout(
            entries = listOf(
                PlayerControlLayoutEntry(PlayerControl.BACK, PlayerControlZone.TOP_RIGHT),
                PlayerControlLayoutEntry(PlayerControl.PLAYBACK_SPEED, PlayerControlZone.TOP_RIGHT),
                PlayerControlLayoutEntry(PlayerControl.LOCK, PlayerControlZone.BOTTOM_LEFT),
                PlayerControlLayoutEntry(PlayerControl.LOCK, PlayerControlZone.TOP_RIGHT),
            ),
        )

        val restoredLayout = json.decodeFromString<PlayerControlsLayout>(
            json.encodeToString(layout),
        )

        assertEquals(layout, restoredLayout)
        assertEquals(
            listOf(
                PlayerControl.PLAYBACK_SPEED,
                PlayerControl.PLAYLIST,
                PlayerControl.AUDIO,
                PlayerControl.SUBTITLE,
            ),
            restoredLayout.controlsIn(PlayerControlZone.TOP_RIGHT),
        )
    }
}
