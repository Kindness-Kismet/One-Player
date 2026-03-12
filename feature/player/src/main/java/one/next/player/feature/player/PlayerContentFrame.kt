package one.next.player.feature.player

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import one.next.player.feature.player.extensions.toContentScale
import one.next.player.feature.player.state.ControlsVisibilityState
import one.next.player.feature.player.state.PictureInPictureState
import one.next.player.feature.player.state.SeekGestureState
import one.next.player.feature.player.state.TapGestureState
import one.next.player.feature.player.state.VideoZoomAndContentScaleState
import one.next.player.feature.player.state.VolumeAndBrightnessGestureState
import one.next.player.feature.player.ui.PlayerGestures
import one.next.player.feature.player.ui.ShutterView
import one.next.player.feature.player.ui.SubtitleConfiguration
import one.next.player.feature.player.ui.SubtitleView

@OptIn(UnstableApi::class)
@Composable
fun PlayerContentFrame(
    modifier: Modifier = Modifier,
    player: Player,
    pictureInPictureState: PictureInPictureState,
    controlsVisibilityState: ControlsVisibilityState,
    tapGestureState: TapGestureState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    volumeAndBrightnessGestureState: VolumeAndBrightnessGestureState,
    subtitleConfiguration: SubtitleConfiguration,
) {
    val presentationState = rememberPresentationState(player)
    val density = LocalDensity.current

    // presentationState.videoSizeDp 依赖 onVideoSizeChanged，ASS wrapper 不触发该回调
    // 从 metadata extras 中的视频尺寸作为后备
    val sourceSizeDp = presentationState.videoSizeDp?.let { size ->
        size.copy(
            width = with(density) { size.width.toDp().value },
            height = with(density) { size.height.toDp().value },
        )
    } ?: run {
        val w = videoZoomAndContentScaleState.metadataVideoWidth.toFloat()
        val h = videoZoomAndContentScaleState.metadataVideoHeight.toFloat()
        val rotation = videoZoomAndContentScaleState.metadataVideoRotation
        if (w <= 0f || h <= 0f) return@run null
        val (dw, dh) = if (rotation == 90 || rotation == 270) h to w else w to h
        Size(
            width = with(density) { dw.toDp().value },
            height = with(density) { dh.toDp().value },
        )
    }

    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
        modifier = modifier
            .resizeWithContentScale(
                contentScale = videoZoomAndContentScaleState.videoContentScale.toContentScale(),
                sourceSizeDp = sourceSizeDp,
            )
            .onGloballyPositioned {
                val bounds = it.boundsInWindow()
                val rect = Rect(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt(),
                )
                pictureInPictureState.setVideoViewRect(rect)
            }
            .graphicsLayer {
                scaleX = videoZoomAndContentScaleState.zoom
                scaleY = videoZoomAndContentScaleState.zoom
                translationX = videoZoomAndContentScaleState.offset.x
                translationY = videoZoomAndContentScaleState.offset.y
            },
    )

    PlayerGestures(
        controlsVisibilityState = controlsVisibilityState,
        tapGestureState = tapGestureState,
        pictureInPictureState = pictureInPictureState,
        seekGestureState = seekGestureState,
        videoZoomAndContentScaleState = videoZoomAndContentScaleState,
        volumeAndBrightnessGestureState = volumeAndBrightnessGestureState,
    )

    if (!presentationState.coverSurface) {
        SubtitleView(
            modifier = Modifier.resizeWithContentScale(
                contentScale = videoZoomAndContentScaleState.videoContentScale.toContentScale(),
                sourceSizeDp = sourceSizeDp,
            ),
            player = player,
            isInPictureInPictureMode = pictureInPictureState.isInPictureInPictureMode,
            configuration = subtitleConfiguration,
        )
    }

    if (presentationState.coverSurface) {
        ShutterView()
    }
}
