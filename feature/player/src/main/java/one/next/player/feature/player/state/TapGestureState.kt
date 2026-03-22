package one.next.player.feature.player.state

import android.os.SystemClock
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import one.next.player.core.model.DoubleTapGesture
import one.next.player.feature.player.extensions.availableDurationMs
import one.next.player.feature.player.extensions.canSeekCurrentMediaItem
import one.next.player.feature.player.extensions.seekByRequestedOffset
import one.next.player.feature.player.service.setTransientPlaybackSpeed

internal fun resolveLongPressPreviewPosition(
    startPositionMs: Long,
    durationMs: Long,
    elapsedSinceLongPressMs: Long,
    longPressSpeed: Float,
): Long {
    val advancedPositionMs = startPositionMs + (elapsedSinceLongPressMs * longPressSpeed).roundToLong()
    if (durationMs <= 0L) return advancedPositionMs.coerceAtLeast(0L)
    return advancedPositionMs.coerceIn(0L, durationMs)
}

@UnstableApi
@Composable
fun rememberTapGestureState(
    player: Player,
    doubleTapGesture: DoubleTapGesture,
    seekIncrementMillis: Long,
    shouldUseLongPressGesture: Boolean,
    longPressSpeed: Float,
): TapGestureState {
    val coroutineScope = rememberCoroutineScope()
    val tapGestureState = remember {
        TapGestureState(
            player = player,
            doubleTapGesture = doubleTapGesture,
            seekIncrementMillis = seekIncrementMillis,
            shouldUseLongPressGesture = shouldUseLongPressGesture,
            longPressSpeed = longPressSpeed,
            coroutineScope = coroutineScope,
        )
    }
    return tapGestureState
}

@Stable
class TapGestureState(
    private val player: Player,
    private val seekIncrementMillis: Long,
    private val shouldUseLongPressGesture: Boolean = true,
    private val coroutineScope: CoroutineScope,
    val longPressSpeed: Float = 2.0f,
    val doubleTapGesture: DoubleTapGesture,
    val interactionSource: MutableInteractionSource = MutableInteractionSource(),
) {
    var seekMillis by mutableLongStateOf(0L)
    var isLongPressGestureInAction by mutableStateOf(false)
    var longPressStartPositionMs by mutableLongStateOf(0L)
        private set
    var longPressPreviewPositionMs by mutableLongStateOf(0L)
        private set

    private var resetJob: Job? = null
    private var currentSpeed: Float = player.playbackParameters.speed
    private var longPressStartedAtMs: Long = 0L
    private var longPressPreviewJob: Job? = null

    fun handleDoubleTap(offset: Offset, size: IntSize) {
        if (!player.canSeekCurrentMediaItem()) return

        val action = when (doubleTapGesture) {
            DoubleTapGesture.FAST_FORWARD_AND_REWIND -> {
                val viewCenterX = size.width / 2
                when {
                    offset.x < viewCenterX -> DoubleTapAction.SEEK_BACKWARD
                    else -> DoubleTapAction.SEEK_FORWARD
                }
            }

            DoubleTapGesture.BOTH -> {
                val eventPositionX = offset.x / size.width
                when {
                    eventPositionX < 0.35 -> DoubleTapAction.SEEK_BACKWARD
                    eventPositionX > 0.65 -> DoubleTapAction.SEEK_FORWARD
                    else -> DoubleTapAction.PLAY_PAUSE
                }
            }

            DoubleTapGesture.PLAY_PAUSE -> DoubleTapAction.PLAY_PAUSE

            DoubleTapGesture.NONE -> return
        }

        when (action) {
            DoubleTapAction.SEEK_BACKWARD -> {
                player.seekByRequestedOffset(-seekIncrementMillis)
                if (seekMillis > 0L) {
                    seekMillis = 0L
                }
                seekMillis -= seekIncrementMillis
                interactionSource.tryEmit(PressInteraction.Press(offset))
            }

            DoubleTapAction.SEEK_FORWARD -> {
                player.seekByRequestedOffset(seekIncrementMillis)
                if (seekMillis < 0L) {
                    seekMillis = 0L
                }
                seekMillis += seekIncrementMillis
                interactionSource.tryEmit(PressInteraction.Press(offset))
            }

            DoubleTapAction.PLAY_PAUSE -> {
                when (player.isPlaying) {
                    true -> player.pause()
                    false -> player.play()
                }
            }
        }
        resetDoubleTapSeekState()
    }

    fun handleLongPress(offset: Offset) {
        if (!shouldUseLongPressGesture) return
        if (!player.isPlaying) return
        if (isLongPressGestureInAction) return

        isLongPressGestureInAction = true
        longPressStartedAtMs = SystemClock.elapsedRealtime()
        longPressStartPositionMs = player.currentPosition.coerceAtLeast(0L)
        longPressPreviewPositionMs = longPressStartPositionMs
        currentSpeed = player.playbackParameters.speed
        player.setPlaybackSpeedWithoutPersistence(longPressSpeed)
        startLongPressPreviewUpdates()
    }

    fun handleOnLongPressRelease() {
        if (!isLongPressGestureInAction) return

        isLongPressGestureInAction = false
        longPressPreviewJob?.cancel()
        longPressPreviewJob = null
        longPressPreviewPositionMs = player.currentPosition.coerceAtLeast(0L)
        player.setPlaybackSpeedWithoutPersistence(currentSpeed)
    }

    private fun Player.setPlaybackSpeedWithoutPersistence(speed: Float) {
        when (this) {
            is MediaController -> setTransientPlaybackSpeed(speed)
            else -> setPlaybackSpeed(speed)
        }
    }

    private fun resetDoubleTapSeekState() {
        resetJob?.cancel()
        resetJob = coroutineScope.launch {
            delay(750.milliseconds)
            seekMillis = 0L
        }
    }

    private fun startLongPressPreviewUpdates() {
        longPressPreviewJob?.cancel()
        longPressPreviewJob = coroutineScope.launch {
            while (isLongPressGestureInAction) {
                longPressPreviewPositionMs = resolveLongPressPreviewPosition(
                    startPositionMs = longPressStartPositionMs,
                    durationMs = player.availableDurationMs(),
                    elapsedSinceLongPressMs = SystemClock.elapsedRealtime() - longPressStartedAtMs,
                    longPressSpeed = longPressSpeed,
                )
                delay(100.milliseconds)
            }
        }
    }
}

enum class DoubleTapAction {
    SEEK_BACKWARD,
    SEEK_FORWARD,
    PLAY_PAUSE,
}
