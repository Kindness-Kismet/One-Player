package one.next.player.feature.player.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.next.player.core.ui.R
import one.next.player.core.ui.components.NextDialogWithDoneAndCancelButtons
import one.next.player.feature.player.state.SleepTimerState

@Composable
fun SleepTimerDialog(
    sleepTimerState: SleepTimerState,
    onDismiss: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val initialMinutes = if (sleepTimerState.isActive) {
        (sleepTimerState.remainingMillis / 60_000f).coerceAtLeast(1f)
    } else {
        30f
    }
    var sliderValue by remember { mutableFloatStateOf(initialMinutes) }
    val displayMinutes = sliderValue.toInt()
    val displayHours = displayMinutes / 60
    val displayRemainderMinutes = displayMinutes % 60

    NextDialogWithDoneAndCancelButtons(
        title = stringResource(R.string.sleep_timer),
        onDoneClick = {
            if (displayMinutes > 0) {
                sleepTimerState.start(displayMinutes)
            } else {
                sleepTimerState.cancel()
            }
            onDismiss()
        },
        onDismissClick = onDismiss,
        content = {
            if (sleepTimerState.isActive) {
                val remainMin = (sleepTimerState.remainingMillis / 60_000L).toInt()
                val remainSec = ((sleepTimerState.remainingMillis % 60_000L) / 1000L).toInt()
                Text(
                    text = "${stringResource(R.string.sleep_timer_remaining)}: ${String.format("%d:%02d", remainMin, remainSec)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = when {
                    displayMinutes == 0 -> stringResource(R.string.sleep_timer_off)
                    displayHours > 0 -> String.format("%dh %02dmin", displayHours, displayRemainderMinutes)
                    else -> stringResource(R.string.sleep_timer_minutes, displayMinutes)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )

            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                valueRange = 0f..300f,
            )

            if (sleepTimerState.isActive) {
                TextButton(
                    onClick = {
                        sleepTimerState.cancel()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sleep_timer_off))
                }
            }
        },
    )
}
