package one.next.player.feature.player.state

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi
import one.next.player.core.model.ScreenOrientation
import one.next.player.feature.player.extensions.videoHeight
import one.next.player.feature.player.extensions.videoRotation
import one.next.player.feature.player.extensions.videoWidth

@UnstableApi
@Composable
fun rememberRotationState(
    player: Player,
    screenOrientation: ScreenOrientation,
): RotationState {
    val activity = LocalActivity.current as ComponentActivity
    val rotationState = remember(screenOrientation) {
        RotationState(
            activity = activity,
            screenOrientation = screenOrientation,
        )
    }
    DisposableEffect(activity) {
        rotationState.handleListeners(this)
    }
    LaunchedEffect(player) { rotationState.observe(player) }
    return rotationState
}

@Stable
class RotationState(
    private val activity: ComponentActivity,
    private val screenOrientation: ScreenOrientation,
) {
    var currentRequestedOrientation: Int by mutableIntStateOf(activity.requestedOrientation)
        private set

    fun rotate() {
        activity.requestedOrientation = when (activity.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun handleListeners(disposableEffectScope: DisposableEffectScope): DisposableEffectResult = with(disposableEffectScope) {
        val configurationChangedListener: Consumer<Configuration> = Consumer {
            currentRequestedOrientation = activity.requestedOrientation
        }

        activity.addOnConfigurationChangedListener(configurationChangedListener)

        onDispose {
            activity.removeOnConfigurationChangedListener(configurationChangedListener)
        }
    }

    suspend fun observe(player: Player) {
        Log.d(TAG, "observe: player=${player.javaClass.simpleName}@${System.identityHashCode(player)}")
        setOrientation(player)
        maybeApplyVideoOrientation(player)

        // 视频尺寸通过 metadata extras 从 Service 端传递
        player.listen { events ->
            if (events.containsAny(
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                )
            ) {
                val metadata = player.mediaMetadata
                Log.d(TAG, "listen: w=${metadata.videoWidth}, h=${metadata.videoHeight}, rot=${metadata.videoRotation}")
                maybeApplyVideoOrientation(player)
            }
        }
    }

    private fun maybeApplyVideoOrientation(player: Player) {
        if (screenOrientation != ScreenOrientation.VIDEO_ORIENTATION) return
        val orientation = getVideoBasedOrientation(player)
        if (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            Log.d(TAG, "applyOrientation: $orientation")
            activity.requestedOrientation = orientation
        }
    }

    private fun setOrientation(player: Player) {
        Log.d(TAG, "setOrientation: requestedOrientation=${activity.requestedOrientation}")
        if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activity.requestedOrientation = when (screenOrientation) {
                ScreenOrientation.AUTOMATIC -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ScreenOrientation.LANDSCAPE_REVERSE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                ScreenOrientation.LANDSCAPE_AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                ScreenOrientation.VIDEO_ORIENTATION -> getVideoBasedOrientation(player)
            }
        }
    }

    private fun getVideoBasedOrientation(player: Player): Int {
        val metadata = player.mediaMetadata
        val width = metadata.videoWidth ?: 0
        val height = metadata.videoHeight ?: 0
        if (width == 0 || height == 0) {
            Log.d(TAG, "getVideoBasedOrientation: metadata=${width}x$height -> UNSPECIFIED")
            return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        val rotation = metadata.videoRotation ?: 0

        val visuallyPortrait = if (rotation == 90 || rotation == 270) {
            width >= height
        } else {
            height >= width
        }

        Log.d(TAG, "getVideoBasedOrientation: ${width}x$height, rotation=$rotation, portrait=$visuallyPortrait")
        return if (visuallyPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
}

private const val TAG = "RotationState"
