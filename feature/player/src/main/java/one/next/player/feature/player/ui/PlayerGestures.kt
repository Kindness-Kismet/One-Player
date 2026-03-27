package one.next.player.feature.player.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.testTag
import one.next.player.feature.player.extensions.detectCustomHorizontalDragGestures
import one.next.player.feature.player.extensions.detectCustomTransformGestures
import one.next.player.feature.player.extensions.detectCustomVerticalDragGestures
import one.next.player.feature.player.state.ControlsVisibilityState
import one.next.player.feature.player.state.PictureInPictureState
import one.next.player.feature.player.state.SeekGestureState
import one.next.player.feature.player.state.TapGestureState
import one.next.player.feature.player.state.VideoZoomAndContentScaleState
import one.next.player.feature.player.state.VolumeAndBrightnessGestureState

@Composable
fun PlayerGestures(
    modifier: Modifier = Modifier,
    controlsVisibilityState: ControlsVisibilityState,
    tapGestureState: TapGestureState,
    pictureInPictureState: PictureInPictureState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    volumeAndBrightnessGestureState: VolumeAndBrightnessGestureState,
    isEnabled: Boolean = true,
) {
    BoxWithConstraints {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("player_gesture_surface")
                .pointerInput(
                    isEnabled,
                    controlsVisibilityState.isControlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                ) {
                    if (!isEnabled) return@pointerInput
                    if (controlsVisibilityState.isControlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

                        tapGestureState.handleLongPress()
                        if (!tapGestureState.isLongPressGestureInAction) return@awaitEachGesture

                        try {
                            longPress.consume()
                            var pointerId = longPress.id
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                    ?: break

                                pointerId = change.id
                                if (!change.pressed) break

                                val dragAmount = change.positionChangeIgnoreConsumed().x
                                if (dragAmount != 0f) {
                                    change.consume()
                                    tapGestureState.handleLongPressHorizontalDrag(dragAmount)
                                }
                            }
                        } finally {
                            tapGestureState.handleOnLongPressRelease()
                        }
                    }
                }
                .pointerInput(isEnabled, pictureInPictureState.isInPictureInPictureMode) {
                    if (!isEnabled) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectTapGestures(
                        onTap = {
                            if (tapGestureState.seekMillis != 0L) return@detectTapGestures
                            controlsVisibilityState.toggleControlsVisibility()
                        },
                        onDoubleTap = {
                            if (controlsVisibilityState.isControlsLocked) return@detectTapGestures
                            tapGestureState.handleDoubleTap(offset = it, size = size)
                        },
                    )
                }
                .pointerInput(
                    isEnabled,
                    controlsVisibilityState.isControlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                ) {
                    if (!isEnabled) return@pointerInput
                    if (controlsVisibilityState.isControlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectCustomHorizontalDragGestures(
                        onDragStart = {
                            if (tapGestureState.isLongPressGestureCaptured) return@detectCustomHorizontalDragGestures
                            seekGestureState.onDragStart(it)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (tapGestureState.isLongPressGestureCaptured) {
                                change.consume()
                                return@detectCustomHorizontalDragGestures
                            }
                            seekGestureState.onDrag(change, dragAmount)
                        },
                        onDragCancel = {
                            if (tapGestureState.isLongPressGestureCaptured) return@detectCustomHorizontalDragGestures
                            seekGestureState.onDragEnd()
                        },
                        onDragEnd = {
                            if (tapGestureState.isLongPressGestureCaptured) return@detectCustomHorizontalDragGestures
                            seekGestureState.onDragEnd()
                        },
                    )
                }
                .pointerInput(
                    isEnabled,
                    controlsVisibilityState.isControlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                    tapGestureState.isLongPressGestureInAction,
                ) {
                    if (!isEnabled) return@pointerInput
                    if (controlsVisibilityState.isControlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput
                    if (tapGestureState.isLongPressGestureInAction) return@pointerInput

                    detectCustomVerticalDragGestures(
                        onDragStart = { volumeAndBrightnessGestureState.onDragStart(it, size) },
                        onVerticalDrag = volumeAndBrightnessGestureState::onDrag,
                        onDragCancel = volumeAndBrightnessGestureState::onDragEnd,
                        onDragEnd = volumeAndBrightnessGestureState::onDragEnd,
                    )
                }
                .pointerInput(
                    isEnabled,
                    controlsVisibilityState.isControlsLocked,
                    pictureInPictureState.isInPictureInPictureMode,
                ) {
                    if (!isEnabled) return@pointerInput
                    if (controlsVisibilityState.isControlsLocked) return@pointerInput
                    if (pictureInPictureState.isInPictureInPictureMode) return@pointerInput

                    detectCustomTransformGestures(
                        onGesture = { _, panChange, zoomChange, _ ->
                            if (tapGestureState.isLongPressGestureInAction) return@detectCustomTransformGestures
                            videoZoomAndContentScaleState.onZoomPanGesture(
                                constraints = this@BoxWithConstraints.constraints,
                                panChange = panChange,
                                zoomChange = zoomChange,
                            )
                        },
                        onGestureEnd = {
                            videoZoomAndContentScaleState.onZoomPanGestureEnd()
                        },
                    )
                },
        )
    }
}
