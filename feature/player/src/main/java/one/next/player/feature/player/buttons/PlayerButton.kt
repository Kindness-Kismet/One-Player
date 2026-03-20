package one.next.player.feature.player.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import one.next.player.core.ui.designsystem.NextIcons

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerButton(
    modifier: Modifier = Modifier,
    buttonSize: Dp = 40.dp,
    isEnabled: Boolean = true,
    isSelected: Boolean = false,
    label: String? = null,
    shouldShowSelectionBadge: Boolean = isSelected || label != null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val viewConfiguration = LocalViewConfiguration.current
    val hapticFeedback = LocalHapticFeedback.current
    val isCustomizingButton = label != null

    LaunchedEffect(interactionSource) {
        var isLongPressClicked = false
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    isLongPressClicked = false
                    delay(viewConfiguration.longPressTimeoutMillis)
                    onLongClick?.let {
                        isLongPressClicked = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        it.invoke()
                    }
                }

                is PressInteraction.Release -> {
                    if (!isLongPressClicked) {
                        onClick()
                    }
                }
            }
        }
    }

    val selectionBadgeBackgroundColor = Color.White
    val selectionBadgeInactiveColor = Color(0xFF2E3444)
    val customizeBorderColor = Color(0xFFD7B46A)
    val selectionBadgeSize = if (buttonSize >= 56.dp) 20.dp else 18.dp
    val selectionBadgeIconSize = if (buttonSize >= 56.dp) 13.dp else 12.dp

    val buttonWithBadge: @Composable () -> Unit = {
        Box(
            modifier = Modifier.size(buttonSize),
            contentAlignment = Alignment.Center,
        ) {
            FilledTonalIconButton(
                onClick = {},
                enabled = isEnabled,
                modifier = Modifier.size(buttonSize),
                interactionSource = interactionSource,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }

            if (shouldShowSelectionBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(selectionBadgeSize)
                        .background(
                            color = if (isSelected) selectionBadgeBackgroundColor else selectionBadgeInactiveColor,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = NextIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(selectionBadgeIconSize),
                        )
                    }
                }
            }
        }
    }

    if (!isCustomizingButton) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            buttonWithBadge()
        }
        return
    }

    Column(
        modifier = modifier
            .widthIn(min = buttonSize + 16.dp, max = 88.dp)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawRoundRect(
                    color = customizeBorderColor,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(12f, 12f),
                        ),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                )
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        buttonWithBadge()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
