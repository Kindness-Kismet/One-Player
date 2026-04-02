package one.next.player.feature.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import one.next.player.core.model.PlayerControl
import one.next.player.core.model.PlayerControlZone
import one.next.player.core.model.PlayerControlsLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerControlsDragDropTest {

    @Test
    fun dropDraggedControl_movesAcrossZonesBasedOnDraggedOffset() {
        val layout = PlayerControlsLayout()

        val moved = layout.dropDraggedControl(
            control = PlayerControl.SHUFFLE,
            dragOffset = Offset(0f, -2800f),
            itemBounds = mapOf(
                PlayerControl.PLAYLIST to Rect(560f, 144f, 724f, 312f),
                PlayerControl.PLAYBACK_SPEED to Rect(782f, 144f, 950f, 312f),
                PlayerControl.AUDIO to Rect(1006f, 144f, 1174f, 312f),
                PlayerControl.SUBTITLE to Rect(1230f, 144f, 1398f, 312f),
                PlayerControl.SCREENSHOT to Rect(768f, 2899f, 936f, 3067f),
                PlayerControl.BACKGROUND_PLAY to Rect(995f, 2899f, 1163f, 3067f),
                PlayerControl.LOOP to Rect(1224f, 2899f, 1392f, 3067f),
                PlayerControl.SHUFFLE to Rect(1020f, 2959f, 1069f, 3008f),
            ),
            zoneBounds = mapOf(
                PlayerControlZone.TOP_RIGHT to Rect(520f, 100f, 1410f, 380f),
                PlayerControlZone.BOTTOM_LEFT to Rect(40f, 2860f, 1410f, 3144f),
            ),
        )

        assertEquals(
            listOf(
                PlayerControl.PLAYLIST,
                PlayerControl.PLAYBACK_SPEED,
                PlayerControl.SHUFFLE,
                PlayerControl.AUDIO,
                PlayerControl.SUBTITLE,
            ),
            moved.controlsIn(PlayerControlZone.TOP_RIGHT),
        )
    }

    @Test
    fun drop_reordersWithinSameZoneByHorizontalPosition() {
        val layout = PlayerControlsLayout()

        val moved = layout.dropControl(
            control = PlayerControl.AUDIO,
            dropPosition = Offset(120f, 80f),
            itemBounds = mapOf(
                PlayerControl.PLAYLIST to Rect(100f, 60f, 180f, 140f),
                PlayerControl.PLAYBACK_SPEED to Rect(220f, 60f, 300f, 140f),
                PlayerControl.AUDIO to Rect(340f, 60f, 420f, 140f),
                PlayerControl.SUBTITLE to Rect(460f, 60f, 540f, 140f),
            ),
            zoneBounds = mapOf(
                PlayerControlZone.TOP_RIGHT to Rect(80f, 40f, 600f, 180f),
                PlayerControlZone.BOTTOM_LEFT to Rect(80f, 900f, 2000f, 1200f),
            ),
        )

        assertEquals(
            listOf(
                PlayerControl.AUDIO,
                PlayerControl.PLAYLIST,
                PlayerControl.PLAYBACK_SPEED,
                PlayerControl.SUBTITLE,
            ),
            moved.controlsIn(PlayerControlZone.TOP_RIGHT),
        )
    }

    @Test
    fun drop_movesControlAcrossZonesUsingNearestZoneAndInsertIndex() {
        val layout = PlayerControlsLayout()

        val moved = layout.dropControl(
            control = PlayerControl.SCALE,
            dropPosition = Offset(2400f, 100f),
            itemBounds = mapOf(
                PlayerControl.PLAYLIST to Rect(2200f, 60f, 2280f, 140f),
                PlayerControl.PLAYBACK_SPEED to Rect(2440f, 60f, 2520f, 140f),
                PlayerControl.AUDIO to Rect(2680f, 60f, 2760f, 140f),
                PlayerControl.SUBTITLE to Rect(2920f, 60f, 3000f, 140f),
                PlayerControl.LOCK to Rect(120f, 940f, 200f, 1020f),
                PlayerControl.SCALE to Rect(240f, 940f, 320f, 1020f),
            ),
            zoneBounds = mapOf(
                PlayerControlZone.TOP_RIGHT to Rect(2100f, 40f, 3150f, 220f),
                PlayerControlZone.BOTTOM_LEFT to Rect(80f, 900f, 2000f, 1200f),
            ),
        )

        assertEquals(
            listOf(
                PlayerControl.PLAYLIST,
                PlayerControl.SCALE,
                PlayerControl.PLAYBACK_SPEED,
                PlayerControl.AUDIO,
                PlayerControl.SUBTITLE,
            ),
            moved.controlsIn(PlayerControlZone.TOP_RIGHT),
        )
    }

    @Test
    fun drop_returnsOriginalLayoutWhenNoZoneBoundsAreAvailable() {
        val layout = PlayerControlsLayout()

        assertEquals(
            layout,
            layout.dropControl(
                control = PlayerControl.SCALE,
                dropPosition = Offset(0f, 0f),
                itemBounds = emptyMap<PlayerControl, Rect>(),
                zoneBounds = emptyMap<PlayerControlZone, Rect>(),
            ),
        )
    }

    @Test
    fun drop_movesControlToEndOfBottomZoneWhenDroppedPastLastItem() {
        val layout = PlayerControlsLayout()

        val moved = layout.dropControl(
            control = PlayerControl.PLAYLIST,
            dropPosition = Offset(1400f, 1080f),
            itemBounds = mapOf(
                PlayerControl.LOCK to Rect(120f, 940f, 200f, 1020f),
                PlayerControl.SCALE to Rect(240f, 940f, 320f, 1020f),
                PlayerControl.PIP to Rect(360f, 940f, 440f, 1020f),
                PlayerControl.SCREENSHOT to Rect(480f, 940f, 560f, 1020f),
                PlayerControl.BACKGROUND_PLAY to Rect(600f, 940f, 680f, 1020f),
                PlayerControl.LOOP to Rect(720f, 940f, 800f, 1020f),
                PlayerControl.SHUFFLE to Rect(840f, 940f, 920f, 1020f),
            ),
            zoneBounds = mapOf(
                PlayerControlZone.TOP_RIGHT to Rect(2100f, 40f, 3150f, 220f),
                PlayerControlZone.BOTTOM_LEFT to Rect(80f, 900f, 1600f, 1200f),
            ),
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
            moved.controlsIn(PlayerControlZone.BOTTOM_LEFT),
        )
    }
}
