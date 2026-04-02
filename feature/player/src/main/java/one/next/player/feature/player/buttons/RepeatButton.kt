package one.next.player.feature.player.buttons

import androidx.annotation.OptIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import one.next.player.core.ui.R as coreUiR
import one.next.player.feature.player.LocalControlsVisibilityState

@OptIn(UnstableApi::class)
@Composable
fun LoopButton(
    player: Player,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    label: String? = null,
    isOutlineOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val state = rememberRepeatButtonState(player)
    val controlsVisibilityState = LocalControlsVisibilityState.current

    PlayerButton(
        modifier = modifier,
        isEnabled = onClick != null || state.isEnabled,
        isSelected = isSelected,
        label = label,
        isOutlineOnly = isOutlineOnly,
        onClick = {
            if (onClick != null) {
                onClick()
            } else {
                state.onClick()
                controlsVisibilityState?.showControls()
            }
        },
    ) {
        Icon(
            painter = repeatModeIconPainter(state.repeatModeState),
            contentDescription = repeatModeContentDescription(state.repeatModeState),
        )
    }
}

@Composable
private fun repeatModeIconPainter(repeatMode: @Player.RepeatMode Int): Painter = when (repeatMode) {
    Player.REPEAT_MODE_OFF -> painterResource(coreUiR.drawable.ic_loop_off)
    Player.REPEAT_MODE_ONE -> painterResource(coreUiR.drawable.ic_loop_one)
    else -> painterResource(coreUiR.drawable.ic_loop_all)
}

@Composable
private fun repeatModeContentDescription(repeatMode: @Player.RepeatMode Int): String = when (repeatMode) {
    Player.REPEAT_MODE_OFF -> stringResource(coreUiR.string.loop_mode_off)
    Player.REPEAT_MODE_ONE -> stringResource(coreUiR.string.loop_mode_one)
    else -> stringResource(coreUiR.string.loop_mode_all)
}
