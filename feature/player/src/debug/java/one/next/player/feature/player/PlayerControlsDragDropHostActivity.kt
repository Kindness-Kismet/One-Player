package one.next.player.feature.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import one.next.player.core.model.PlayerControl

class PlayerControlsDragDropHostActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isEnabled = intent.getBooleanExtra(EXTRA_ENABLED, false)

        setContent {
            var dropCount by mutableIntStateOf(0)
            var droppedControl by mutableStateOf("null")
            var droppedOffset by mutableStateOf("null")

            Column {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .testTag("drag_source")
                        .playerControlDragSource(
                            control = PlayerControl.ROTATE,
                            enabled = isEnabled,
                            onDropDragged = { control, offset ->
                                dropCount += 1
                                droppedControl = control.name
                                droppedOffset = "${offset.x},${offset.y}"
                            },
                        ),
                )
                Text(
                    text = isEnabled.toString(),
                    modifier = Modifier.testTag("enabled_state"),
                )
                Text(
                    text = dropCount.toString(),
                    modifier = Modifier.testTag("drop_count"),
                )
                Text(
                    text = droppedControl,
                    modifier = Modifier.testTag("dropped_control"),
                )
                Text(
                    text = droppedOffset,
                    modifier = Modifier.testTag("dropped_offset"),
                )
            }
        }
    }

    companion object {
        const val EXTRA_ENABLED = "enabled"
    }
}
