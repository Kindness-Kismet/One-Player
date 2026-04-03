package one.next.player.feature.player.ui.controls

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import one.next.player.core.model.PlayerControl
import one.next.player.core.model.VideoContentScale
import one.next.player.core.ui.R
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.feature.player.buttons.LoopButton
import one.next.player.feature.player.buttons.PlayerButton
import one.next.player.feature.player.buttons.ShuffleButton
import one.next.player.feature.player.extensions.drawableRes
import one.next.player.feature.player.state.SleepTimerState

@Composable
internal fun PlayerCustomizableControlButton(
    modifier: Modifier = Modifier,
    control: PlayerControl,
    player: Player,
    videoContentScale: VideoContentScale,
    isPipSupported: Boolean,
    isCustomizingControls: Boolean,
    visiblePlayerControls: Set<PlayerControl>,
    isBeingDragged: Boolean = false,
    isOutlineOnly: Boolean = false,
    onPlaylistClick: () -> Unit,
    onPlaybackSpeedClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onLockControlsClick: () -> Unit,
    onVideoContentScaleClick: () -> Unit,
    onVideoContentScaleLongClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onRotateClick: () -> Unit,
    isTakingScreenshot: Boolean,
    onScreenshotClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
    onLoopClick: (() -> Unit)? = null,
    onShuffleClick: (() -> Unit)? = null,
    onSleepTimerClick: (() -> Unit)? = null,
    sleepTimerState: SleepTimerState? = null,
) {
    if (!isCustomizingControls && control !in visiblePlayerControls) return
    if (!isCustomizingControls && control == PlayerControl.PIP && !isPipSupported) return

    val isSelected = isCustomizingControls && control in visiblePlayerControls
    val isPlaceholder = isBeingDragged || isOutlineOnly
    val label = control.label().takeIf { isCustomizingControls }
    val buttonModifier = modifier

    when (control) {
        PlayerControl.PLAYLIST -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onPlaylistClick,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_playlist),
                    contentDescription = "btn_playlist",
                )
            }
        }

        PlayerControl.PLAYBACK_SPEED -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onPlaybackSpeedClick,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_speed),
                    contentDescription = "btn_speed",
                )
            }
        }

        PlayerControl.AUDIO -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onAudioClick,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio_track),
                    contentDescription = "btn_audio",
                )
            }
        }

        PlayerControl.SUBTITLE -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onSubtitleClick,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_subtitle_track),
                    contentDescription = "btn_subtitle",
                )
            }
        }

        PlayerControl.LOCK -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onLockControlsClick,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock_open),
                    contentDescription = "btn_lock",
                )
            }
        }

        PlayerControl.SCALE -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onVideoContentScaleClick,
                onLongClick = onVideoContentScaleLongClick,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(videoContentScale.drawableRes()),
                    contentDescription = "btn_scale",
                )
            }
        }

        PlayerControl.PIP -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onPictureInPictureClick,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pip),
                    contentDescription = "btn_pip",
                )
            }
        }

        PlayerControl.SCREENSHOT -> {
            PlayerButton(
                modifier = buttonModifier.alpha(if (isTakingScreenshot) 0.5f else 1f),
                onClick = onScreenshotClick,
                isSelected = isSelected,
                isEnabled = !isTakingScreenshot,
                label = label,
                isOutlineOnly = isPlaceholder,
            ) {
                if (isTakingScreenshot) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = NextIcons.Image,
                        contentDescription = "btn_screenshot",
                    )
                }
            }
        }

        PlayerControl.BACKGROUND_PLAY -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onPlayInBackgroundClick,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_headset),
                    contentDescription = "btn_background",
                )
            }
        }

        PlayerControl.LOOP -> {
            LoopButton(
                player = player,
                modifier = buttonModifier,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
                onClick = onLoopClick.takeIf { isCustomizingControls },
            )
        }

        PlayerControl.SHUFFLE -> {
            ShuffleButton(
                player = player,
                modifier = buttonModifier,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
                onClick = onShuffleClick.takeIf { isCustomizingControls },
            )
        }

        PlayerControl.SLEEP_TIMER -> {
            val isSleepTimerActive = sleepTimerState?.isActive == true
            PlayerButton(
                modifier = buttonModifier,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
                onClick = {
                    onSleepTimerClick?.invoke()
                },
            ) {
                Icon(
                    painter = painterResource(
                        if (isSleepTimerActive) R.drawable.ic_sleep_timer_on else R.drawable.ic_sleep_timer,
                    ),
                    contentDescription = "btn_sleep_timer",
                )
            }
        }

        PlayerControl.ROTATE -> {
            PlayerButton(
                modifier = buttonModifier,
                onClick = onRotateClick,
                isSelected = isSelected,
                label = label,
                isOutlineOnly = isPlaceholder,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_screen_rotation),
                    contentDescription = "btn_rotate",
                )
            }
        }

        PlayerControl.BACK,
        PlayerControl.PREVIOUS,
        PlayerControl.PLAY_PAUSE,
        PlayerControl.NEXT,
        -> Unit
    }
}

@Composable
private fun PlayerControl.label(): String = when (this) {
    PlayerControl.PLAYLIST -> stringResource(R.string.now_playing)
    PlayerControl.PLAYBACK_SPEED -> stringResource(R.string.speed)
    PlayerControl.AUDIO -> stringResource(R.string.audio)
    PlayerControl.SUBTITLE -> stringResource(R.string.subtitle)
    PlayerControl.LOCK -> stringResource(R.string.controls_lock)
    PlayerControl.SCALE -> stringResource(R.string.video_zoom)
    PlayerControl.PIP -> stringResource(R.string.pip_settings)
    PlayerControl.SCREENSHOT -> stringResource(R.string.take_screenshot)
    PlayerControl.BACKGROUND_PLAY -> stringResource(R.string.background_play)
    PlayerControl.LOOP -> stringResource(R.string.loop_mode)
    PlayerControl.SHUFFLE -> stringResource(R.string.shuffle)
    PlayerControl.SLEEP_TIMER -> stringResource(R.string.sleep_timer)
    PlayerControl.ROTATE -> stringResource(R.string.screen_rotation)
    PlayerControl.BACK,
    PlayerControl.PREVIOUS,
    PlayerControl.PLAY_PAUSE,
    PlayerControl.NEXT,
    -> ""
}
