package one.next.player.feature.player.state

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import one.next.player.core.model.DoubleTapGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TapGestureStateTest {

    @Test
    fun resolveLongPressVariableSpeed_clampsWithinGlobalBounds() {
        assertEquals(1.9f, resolveLongPressVariableSpeed(baseSpeed = 2.0f, accumulatedDragAmount = -48f), 0.0f)
        assertEquals(0.2f, resolveLongPressVariableSpeed(baseSpeed = 2.0f, accumulatedDragAmount = -960f), 0.0f)
        assertEquals(2.5f, resolveLongPressVariableSpeed(baseSpeed = 2.0f, accumulatedDragAmount = 240f), 0.0f)
        assertEquals(4.0f, resolveLongPressVariableSpeed(baseSpeed = 3.8f, accumulatedDragAmount = 480f), 0.0f)
    }

    @Test
    fun handleLongPressHorizontalDrag_updatesTemporarySpeedInBothDirections() {
        val playerState = FakePlayerState(speed = 1.25f)
        val gestureState = createTapGestureState(
            player = createFakePlayer(playerState),
            shouldUseLongPressVariableSpeed = true,
            longPressSpeed = 2.0f,
        )

        gestureState.handleLongPress()
        gestureState.handleLongPressHorizontalDrag(-480f)

        assertEquals(1.0f, gestureState.currentLongPressSpeed, 0.0f)
        assertEquals(1.0f, playerState.speed, 0.0f)

        gestureState.handleLongPressHorizontalDrag(960f)

        assertEquals(3.0f, gestureState.currentLongPressSpeed, 0.0f)
        assertEquals(3.0f, playerState.speed, 0.0f)
    }

    @Test
    fun handleLongPressHorizontalDrag_doesNotResendSameRoundedSpeed() {
        val playerState = FakePlayerState(speed = 1.25f)
        val gestureState = createTapGestureState(
            player = createFakePlayer(playerState),
            shouldUseLongPressVariableSpeed = true,
            longPressSpeed = 2.0f,
        )

        gestureState.handleLongPress()
        gestureState.handleLongPressHorizontalDrag(10f)
        gestureState.handleLongPressHorizontalDrag(10f)

        assertEquals(1, playerState.speedUpdateCalls)
        assertEquals(2.0f, gestureState.currentLongPressSpeed, 0.0f)
        assertEquals(2.0f, playerState.speed, 0.0f)
    }

    @Test
    fun handleOnLongPressRelease_restoresOriginalPlaybackSpeed() {
        val playerState = FakePlayerState(speed = 1.25f)
        val gestureState = createTapGestureState(
            player = createFakePlayer(playerState),
            shouldUseLongPressVariableSpeed = true,
            longPressSpeed = 2.0f,
        )

        gestureState.handleLongPress()
        gestureState.handleLongPressHorizontalDrag(-480f)
        gestureState.handleOnLongPressRelease()

        assertFalse(gestureState.isLongPressGestureInAction)
        assertEquals(2.0f, gestureState.currentLongPressSpeed, 0.0f)
        assertEquals(1.25f, playerState.speed, 0.0f)
    }

    private fun createTapGestureState(
        player: Player,
        shouldUseLongPressVariableSpeed: Boolean,
        longPressSpeed: Float,
    ): TapGestureState = TapGestureState(
        player = player,
        seekIncrementMillis = 10_000L,
        shouldUseLongPressGesture = true,
        shouldUseLongPressVariableSpeed = shouldUseLongPressVariableSpeed,
        coroutineScope = CoroutineScope(SupervisorJob()),
        longPressSpeed = longPressSpeed,
        doubleTapGesture = DoubleTapGesture.BOTH,
        interactionSource = MutableInteractionSource(),
    )

    private fun createFakePlayer(state: FakePlayerState): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "getPlaybackParameters" -> PlaybackParameters(state.speed)
            "setPlaybackParameters" -> {
                state.speed = (args?.first() as PlaybackParameters).speed
                state.speedUpdateCalls += 1
                null
            }

            "setPlaybackSpeed" -> {
                state.speed = args?.first() as Float
                state.speedUpdateCalls += 1
                null
            }

            "isPlaying" -> state.isPlaying
            "play" -> {
                state.isPlaying = true
                null
            }

            "pause" -> {
                state.isPlaying = false
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
        var isPlaying: Boolean = true,
        var speed: Float = 1.0f,
        var speedUpdateCalls: Int = 0,
    )
}
