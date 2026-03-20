package one.next.player.feature.player.ui.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlin.time.Duration.Companion.milliseconds
import one.next.player.core.model.PlayerControl
import one.next.player.core.model.VideoContentScale
import one.next.player.core.ui.R
import one.next.player.core.ui.designsystem.NextIcons
import one.next.player.core.ui.extensions.copy
import one.next.player.feature.player.buttons.LoopButton
import one.next.player.feature.player.buttons.PlayerButton
import one.next.player.feature.player.buttons.ShuffleButton
import one.next.player.feature.player.extensions.drawableRes
import one.next.player.feature.player.extensions.formatted
import one.next.player.feature.player.extensions.noRippleClickable
import one.next.player.feature.player.state.MediaPresentationState
import one.next.player.feature.player.state.durationFormatted

@OptIn(UnstableApi::class)
@Composable
fun ControlsBottomView(
    modifier: Modifier = Modifier,
    player: Player,
    mediaPresentationState: MediaPresentationState,
    controlsAlignment: Alignment.Horizontal,
    videoContentScale: VideoContentScale,
    isPipSupported: Boolean,
    pendingSeekPosition: Long?,
    onVideoContentScaleClick: () -> Unit,
    onVideoContentScaleLongClick: () -> Unit,
    onLockControlsClick: () -> Unit,
    onPictureInPictureClick: () -> Unit,
    onRotateClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
    isTakingScreenshot: Boolean,
    onScreenshotClick: () -> Unit,
    onCustomizeControlsClick: () -> Unit,
    onLoopClick: (() -> Unit)? = null,
    onShuffleClick: (() -> Unit)? = null,
    isCustomizingControls: Boolean,
    visiblePlayerControls: Set<PlayerControl>,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
) {
    val systemBarsPadding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val displayedPosition = pendingSeekPosition ?: mediaPresentationState.position
    val displayedPendingPosition = (mediaPresentationState.duration - displayedPosition).coerceAtLeast(0L)

    fun isVisible(control: PlayerControl): Boolean = isCustomizingControls || control in visiblePlayerControls
    fun isSelected(control: PlayerControl): Boolean = isCustomizingControls && control in visiblePlayerControls
    Column(
        modifier = modifier
            .padding(systemBarsPadding.copy(top = 0.dp))
            .padding(horizontal = 8.dp)
            .padding(top = 16.dp)
            .padding(bottom = 16.dp.takeIf { systemBarsPadding.calculateBottomPadding() == 0.dp } ?: 0.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var shouldShowPendingPosition by rememberSaveable { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.noRippleClickable {
                    shouldShowPendingPosition = !shouldShowPendingPosition
                },
            ) {
                Text(
                    text = when (shouldShowPendingPosition) {
                        true -> "-${displayedPendingPosition.milliseconds.formatted()}"
                        false -> displayedPosition.milliseconds.formatted()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Text(
                    text = mediaPresentationState.durationFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            if (isVisible(PlayerControl.ROTATE)) {
                PlayerButton(
                    buttonSize = 30.dp,
                    onClick = onRotateClick,
                    isSelected = isSelected(PlayerControl.ROTATE),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_screen_rotation),
                        contentDescription = "btn_rotate",
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        PlayerSeekbar(
            position = displayedPosition.toFloat(),
            duration = mediaPresentationState.duration.toFloat(),
            onSeek = { onSeek(it.toLong()) },
            onSeekFinished = { onSeekEnd() },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when (isCustomizingControls) {
                        true -> Modifier.heightIn(min = 72.dp)
                        false -> Modifier
                    },
                )
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = when (isCustomizingControls) {
                true -> Alignment.Top
                false -> Alignment.CenterVertically
            },
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = controlsAlignment),
        ) {
            PlayerButton(
                onClick = onCustomizeControlsClick,
                isSelected = false,
                label = stringResource(R.string.customize_player_controls).takeIf { isCustomizingControls },
                shouldShowSelectionBadge = false,
            ) {
                Icon(
                    imageVector = NextIcons.Settings,
                    contentDescription = "btn_customize_controls",
                )
            }
            if (isVisible(PlayerControl.LOCK)) {
                PlayerButton(
                    onClick = onLockControlsClick,
                    isSelected = isSelected(PlayerControl.LOCK),
                    label = stringResource(R.string.controls_lock).takeIf { isCustomizingControls },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock_open),
                        contentDescription = "btn_lock",
                    )
                }
            }
            if (isVisible(PlayerControl.SCALE)) {
                PlayerButton(
                    onClick = onVideoContentScaleClick,
                    onLongClick = onVideoContentScaleLongClick,
                    isSelected = isSelected(PlayerControl.SCALE),
                    label = stringResource(R.string.video_zoom).takeIf { isCustomizingControls },
                ) {
                    Icon(
                        painter = painterResource(videoContentScale.drawableRes()),
                        contentDescription = "btn_scale",
                    )
                }
            }
            if (isPipSupported && isVisible(PlayerControl.PIP)) {
                PlayerButton(
                    onClick = onPictureInPictureClick,
                    isSelected = isSelected(PlayerControl.PIP),
                    label = stringResource(R.string.pip_settings).takeIf { isCustomizingControls },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pip),
                        contentDescription = "btn_pip",
                    )
                }
            }
            if (isVisible(PlayerControl.SCREENSHOT)) {
                PlayerButton(
                    onClick = onScreenshotClick,
                    isSelected = isSelected(PlayerControl.SCREENSHOT),
                    isEnabled = !isTakingScreenshot,
                    modifier = Modifier.alpha(if (isTakingScreenshot) 0.5f else 1f),
                    label = stringResource(R.string.take_screenshot).takeIf { isCustomizingControls },
                ) {
                    if (isTakingScreenshot) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
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
            if (isVisible(PlayerControl.BACKGROUND_PLAY)) {
                PlayerButton(
                    onClick = onPlayInBackgroundClick,
                    isSelected = isSelected(PlayerControl.BACKGROUND_PLAY),
                    label = stringResource(R.string.background_play).takeIf { isCustomizingControls },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_headset),
                        contentDescription = "btn_background",
                    )
                }
            }
            if (isVisible(PlayerControl.LOOP)) {
                LoopButton(
                    player = player,
                    isSelected = isSelected(PlayerControl.LOOP),
                    label = stringResource(R.string.loop_mode).takeIf { isCustomizingControls },
                    onClick = onLoopClick,
                )
            }
            if (isVisible(PlayerControl.SHUFFLE)) {
                ShuffleButton(
                    player = player,
                    isSelected = isSelected(PlayerControl.SHUFFLE),
                    label = stringResource(R.string.shuffle).takeIf { isCustomizingControls },
                    onClick = onShuffleClick,
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeekbar(
    modifier: Modifier = Modifier,
    position: Float,
    duration: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MaterialYouSlider(
            modifier = modifier.fillMaxWidth(),
            value = position,
            valueRange = 0f..duration,
            onValueChange = onSeek,
            onValueChangeFinished = onSeekFinished,
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialYouSlider(
    modifier: Modifier = Modifier,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val trackHeight = 8.dp
    val thumbWidth = 4.dp
    val trackThumbGapWidth = 12.dp

    Slider(
        value = value,
        valueRange = valueRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier.height(24.dp).semantics { contentDescription = "slider_seek" },
        track = { sliderState ->
            val disabledAlpha = 0.4f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight),
            ) {
                val min = sliderState.valueRange.start
                val max = sliderState.valueRange.endInclusive
                val range = (max - min).takeIf { it > 0f } ?: 1f
                val playedFraction = ((sliderState.value - min) / range).coerceIn(0f, 1f)
                val playedPixels = size.width * playedFraction

                val endCornerRadius = size.height / 2f
                val insideCornerRadius = 2.dp.toPx()
                val gapHalf = trackThumbGapWidth.toPx() / 2f
                val leftEnd = (playedPixels - gapHalf).coerceIn(0f, size.width)
                val rightStart = (playedPixels + gapHalf).coerceIn(0f, size.width)

                if (leftEnd > 0f) {
                    drawRoundedRect(
                        offset = Offset(0f, 0f),
                        size = Size(leftEnd, size.height),
                        color = primaryColor.copy(alpha = disabledAlpha),
                        startCornerRadius = endCornerRadius,
                        endCornerRadius = insideCornerRadius,
                    )
                }

                if (rightStart < size.width) {
                    drawRoundedRect(
                        offset = Offset(rightStart, 0f),
                        size = Size(size.width - rightStart, size.height),
                        color = primaryColor.copy(alpha = disabledAlpha),
                        startCornerRadius = insideCornerRadius,
                        endCornerRadius = endCornerRadius,
                    )
                }

                if (leftEnd > 0f) {
                    drawRoundedRect(
                        offset = Offset(0f, 0f),
                        size = Size(leftEnd, size.height),
                        color = primaryColor,
                        startCornerRadius = endCornerRadius,
                        endCornerRadius = insideCornerRadius,
                    )
                }
            }
        },
        thumb = {
            Box(
                modifier = Modifier
                    .width(thumbWidth)
                    .height(20.dp)
                    .background(primaryColor, CircleShape),
            )
        },
    )
}

private fun DrawScope.drawRoundedRect(
    offset: Offset,
    size: Size,
    color: Color,
    startCornerRadius: Float,
    endCornerRadius: Float,
) {
    val startCorner = CornerRadius(startCornerRadius, startCornerRadius)
    val endCorner = CornerRadius(endCornerRadius, endCornerRadius)
    val track = RoundRect(
        rect = Rect(Offset(offset.x, 0f), size = Size(size.width, size.height)),
        topLeft = startCorner,
        topRight = endCorner,
        bottomRight = endCorner,
        bottomLeft = startCorner,
    )
    drawPath(
        path = Path().apply {
            addRoundRect(track)
        },
        color = color,
    )
}
