package one.next.player.feature.player.state

import androidx.compose.ui.geometry.Offset
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekGestureStateTest {

    @Test
    fun onSeek_initializesStateAndResetsAfterSeekEnd() {
        val playerState = FakePlayerState()
        val gestureState = createSeekGestureState(createFakePlayer(playerState))

        gestureState.onSeek(40_000L)

        assertTrue(gestureState.isSeeking)
        assertEquals(30_000L, gestureState.seekStartPosition)
        assertEquals(40_000L, gestureState.pendingSeekPosition)

        gestureState.onSeekEnd()

        assertFalse(gestureState.isSeeking)
        assertEquals(40_000L, playerState.currentPosition)
        assertEquals(1, playerState.seekToCalls)
    }

    @Test
    fun onDragStart_initializesStateAndStoresSeekStartPosition() {
        val playerState = FakePlayerState()
        val gestureState = createSeekGestureState(createFakePlayer(playerState))

        gestureState.onDragStart(Offset(120f, 0f))

        assertTrue(gestureState.isSeeking)
        assertEquals(30_000L, gestureState.seekStartPosition)
        assertEquals(30_000L, gestureState.pendingSeekPosition)

        gestureState.onDragEnd()

        assertFalse(gestureState.isSeeking)
        assertEquals(0, playerState.seekToCalls)
    }

    private fun createSeekGestureState(player: Player): SeekGestureState = SeekGestureState(
        player = player,
        sensitivity = 0.5f,
        isSeekGestureEnabled = true,
    )

    private fun createFakePlayer(state: FakePlayerState): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "getDuration" -> state.duration
            "getCurrentPosition" -> state.currentPosition
            "seekTo" -> {
                state.currentPosition = args?.firstOrNull() as? Long ?: state.currentPosition
                state.seekToCalls += 1
                null
            }

            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "FakePlayer"
            else -> defaultValue(method.returnType)
        }
    } as Player

    private fun defaultValue(returnType: Class<*>): Any? = when (returnType) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private data class FakePlayerState(
        val duration: Long = 120_000L,
        var currentPosition: Long = 30_000L,
        var seekToCalls: Int = 0,
    )
}
