package one.next.player.feature.player.ui

import android.graphics.Typeface
import android.util.TypedValue
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.getSystemService
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.widget.AssSubtitleView as AssMediaSubtitleView
import one.next.player.core.model.Font
import one.next.player.feature.player.extensions.toTypeface
import one.next.player.feature.player.state.rememberCuesState
import one.next.player.feature.player.state.rememberTracksState
import one.next.player.feature.player.subtitle.AssHandlerRegistry

@OptIn(UnstableApi::class)
@Composable
fun SubtitleView(
    modifier: Modifier = Modifier,
    player: Player,
    isInPictureInPictureMode: Boolean,
    configuration: SubtitleConfiguration,
) {
    val cuesState = rememberCuesState(player)
    val assHandler by AssHandlerRegistry.handler.collectAsState()
    val textTracksState = rememberTracksState(player = player, trackType = C.TRACK_TYPE_TEXT)
    val isAssSubtitleSelected = textTracksState.tracks.any { track ->
        track.isSelected &&
            (0 until track.mediaTrackGroup.length).any { index ->
                val format = track.mediaTrackGroup.getFormat(index)
                format.sampleMimeType == MimeTypes.TEXT_SSA || format.codecs == MimeTypes.TEXT_SSA
            }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SubtitleView(context).apply {
                val captioningManager = getSystemService(context, CaptioningManager::class.java) ?: return@apply
                if (configuration.shouldUseSystemCaptionStyle) {
                    val systemCaptionStyle = CaptionStyleCompat.createFromCaptionStyle(captioningManager.userStyle)
                    setStyle(systemCaptionStyle)
                } else {
                    val userStyle = CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.BLACK.takeIf { configuration.shouldShowBackground } ?: android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                        android.graphics.Color.BLACK,
                        Typeface.create(
                            configuration.font.toTypeface(),
                            Typeface.BOLD.takeIf { configuration.shouldUseBoldText } ?: Typeface.NORMAL,
                        ),
                    )
                    setStyle(userStyle)
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, configuration.textSize.toFloat())
                }
                setApplyEmbeddedStyles(configuration.shouldApplyEmbeddedStyles)
            }
        },
        update = { subtitleView ->
            if (isAssSubtitleSelected) {
                assHandler?.let(subtitleView::syncAssSupport)
                    ?: subtitleView.clearAssSupport()
                subtitleView.setCues(emptyList())
            } else {
                subtitleView.clearAssSupport()
                subtitleView.setCues(cuesState.cues)
            }

            if (isInPictureInPictureMode) {
                subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
            } else {
                subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, configuration.textSize.toFloat())
            }
        },
    )
}

@Stable
data class SubtitleConfiguration(
    val shouldUseSystemCaptionStyle: Boolean,
    val shouldShowBackground: Boolean,
    val font: Font,
    val textSize: Int,
    val shouldUseBoldText: Boolean,
    val shouldApplyEmbeddedStyles: Boolean,
)

@OptIn(UnstableApi::class)
private fun SubtitleView.syncAssSupport(handler: AssHandler) {
    val assSupportView = findAssSupportView()
    val currentHandler = assSupportView?.tag as? AssHandler
    if (currentHandler === handler) return

    assSupportView?.let(::removeView)
    this.withAssSupport(handler)
    findAssSupportView()?.tag = handler
}

private fun SubtitleView.clearAssSupport() {
    findAssSupportView()?.let(::removeView)
}

private fun SubtitleView.findAssSupportView(): AssMediaSubtitleView? = (0 until childCount).firstNotNullOfOrNull { index ->
    getChildAt(index) as? AssMediaSubtitleView
}
