package one.next.player.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import one.next.player.core.model.PlayerControl
import one.next.player.core.model.PlayerControlZone
import one.next.player.core.model.PlayerControlsLayout

internal fun PlayerControlsLayout.dropDraggedControl(
    control: PlayerControl,
    dragOffset: Offset,
    itemBounds: Map<PlayerControl, Rect>,
    zoneBounds: Map<PlayerControlZone, Rect>,
): PlayerControlsLayout {
    val sourceBounds = itemBounds[control] ?: return this
    return dropControl(
        control = control,
        dropPosition = sourceBounds.center + dragOffset,
        itemBounds = itemBounds,
        zoneBounds = zoneBounds,
    )
}

// 拖拽预览：仅同区域内重排，不跨区移动（避免节点 detach 导致手势取消）
internal fun PlayerControlsLayout.previewReorder(
    control: PlayerControl,
    dropPosition: Offset,
    itemBounds: Map<PlayerControl, Rect>,
): PlayerControlsLayout {
    val sourceZone = entries.firstOrNull { it.control == control }?.zone ?: return this
    val controlsInZone = controlsIn(sourceZone).filterNot { it == control }
    val insertionIndex = controlsInZone.indexOfFirst { candidate ->
        val bounds = itemBounds[candidate] ?: return@indexOfFirst false
        dropPosition.x < bounds.center.x
    }.takeIf { it >= 0 } ?: controlsInZone.size

    return move(
        control = control,
        toZone = sourceZone,
        toIndex = insertionIndex,
    )
}

internal fun PlayerControlsLayout.dropControl(
    control: PlayerControl,
    dropPosition: Offset,
    itemBounds: Map<PlayerControl, Rect>,
    zoneBounds: Map<PlayerControlZone, Rect>,
): PlayerControlsLayout {
    val targetZone = resolveDropZone(
        dropPosition = dropPosition,
        zoneBounds = zoneBounds,
    ) ?: return this

    val controlsInZone = controlsIn(targetZone).filterNot { it == control }
    val insertionIndex = controlsInZone.indexOfFirst { candidate ->
        val bounds = itemBounds[candidate] ?: return@indexOfFirst false
        dropPosition.x < bounds.center.x
    }.takeIf { it >= 0 } ?: controlsInZone.size

    return move(
        control = control,
        toZone = targetZone,
        toIndex = insertionIndex,
    )
}

@Composable
internal fun AnimatedPlayerControlPlacement(
    control: PlayerControl,
    itemBounds: MutableMap<PlayerControl, Rect>,
    isTracking: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!isTracking) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val scope = rememberCoroutineScope()
    val animatedOffset = remember(control) { Animatable(Offset.Zero, Offset.VectorConverter) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            val previousBounds = itemBounds.put(control, bounds)
            val delta = previousBounds?.topLeft?.minus(bounds.topLeft) ?: Offset.Zero
            val distanceSquared = (delta.x * delta.x) + (delta.y * delta.y)
            if (distanceSquared < 1f) return@onGloballyPositioned

            scope.launch {
                animatedOffset.stop()
                animatedOffset.snapTo(delta)
                animatedOffset.animateTo(
                    targetValue = Offset.Zero,
                    animationSpec = tween(durationMillis = 180),
                )
            }
        },
    ) {
        Box(
            modifier = Modifier.offset {
                IntOffset(
                    x = animatedOffset.value.x.roundToInt(),
                    y = animatedOffset.value.y.roundToInt(),
                )
            },
        ) {
            content()
        }
    }
}

internal fun Modifier.playerControlZoneTarget(
    zone: PlayerControlZone,
    zoneBounds: MutableMap<PlayerControlZone, Rect>,
): Modifier = onGloballyPositioned {
    zoneBounds[zone] = it.boundsInWindow()
}

internal fun Modifier.playerControlDragSource(
    control: PlayerControl,
    enabled: Boolean,
    onDropDragged: (PlayerControl, Offset) -> Unit,
    onDragStarted: (PlayerControl) -> Unit = {},
    onDragMoved: (PlayerControl, Offset) -> Unit = { _, _ -> },
    onDragCancelled: (PlayerControl) -> Unit = {},
): Modifier = if (!enabled) {
    this
} else {
    composed {
        val currentOnDropDragged by rememberUpdatedState(onDropDragged)
        val currentOnDragStarted by rememberUpdatedState(onDragStarted)
        val currentOnDragMoved by rememberUpdatedState(onDragMoved)
        val currentOnDragCancelled by rememberUpdatedState(onDragCancelled)

        pointerInput(control, enabled) {
            var totalDrag = Offset.Zero
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    totalDrag = Offset.Zero
                    currentOnDragStarted(control)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    totalDrag += dragAmount
                    currentOnDragMoved(control, totalDrag)
                },
                onDragEnd = {
                    currentOnDropDragged(control, totalDrag)
                },
                onDragCancel = {
                    currentOnDragCancelled(control)
                },
            )
        }
    }
}

internal fun resolveDropZone(
    dropPosition: Offset,
    zoneBounds: Map<PlayerControlZone, Rect>,
): PlayerControlZone? {
    zoneBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(dropPosition) }?.let { entry ->
        return entry.key
    }

    // 区域之间用垂直距离判断，避免全宽底栏抢占上方区域
    return zoneBounds.minByOrNull { (_, bounds) ->
        abs(dropPosition.y - bounds.center.y)
    }?.key
}
