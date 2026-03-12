package one.next.player.feature.player.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.next.player.core.model.VideoContentScale
import one.next.player.core.ui.R
import one.next.player.feature.player.extensions.nameRes

@Composable
fun BoxScope.VideoContentScaleSelectorView(
    modifier: Modifier = Modifier,
    shouldShow: Boolean,
    videoContentScale: VideoContentScale,
    onVideoContentScaleChanged: (VideoContentScale) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayView(
        modifier = modifier,
        shouldShow = shouldShow,
        title = stringResource(R.string.video_zoom),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .padding(horizontal = 24.dp)
                .selectableGroup(),
        ) {
            VideoContentScale.entries.forEach { contentScale ->
                RadioButtonRow(
                    isSelected = contentScale == videoContentScale,
                    text = stringResource(contentScale.nameRes()),
                    onClick = {
                        onVideoContentScaleChanged(contentScale)
                        onDismiss()
                    },
                )
            }
        }
    }
}
