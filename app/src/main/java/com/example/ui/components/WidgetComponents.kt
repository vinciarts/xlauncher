package com.example.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.PlacedWidget
import com.example.model.WidgetResizeSpec
import com.example.widget.WidgetHostController
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Material 3 Expressive grouped menu anchored directly at the local long-press point on empty desktop space.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeEmptyMenuPopup(
    anchorOffset: Offset?,
    canAddPage: Boolean,
    canMovePageLeft: Boolean,
    canMovePageRight: Boolean,
    canDeleteCurrentPage: Boolean,
    onAddWidget: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onEditLayout: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddPageAtStart: () -> Unit,
    onAddPageAtEnd: () -> Unit,
    onMovePageLeft: () -> Unit,
    onMovePageRight: () -> Unit,
    onDeleteCurrentPage: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastAnchor by remember { mutableStateOf<Offset?>(null) }
    if (anchorOffset != null) {
        lastAnchor = anchorOffset
    }
    val activeOffset = anchorOffset ?: lastAnchor ?: return

    Box(
        modifier = modifier
            .offset { IntOffset(activeOffset.x.roundToInt(), activeOffset.y.roundToInt()) }
            .size(1.dp),
    ) {
        key(activeOffset) {
            val expanded = anchorOffset != null
            DropdownMenuPopup(
                expanded = expanded,
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("home_empty_menu_popup"),
            ) {
                val errorItemColors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                )

                val homeActionsGroup = listOf(
                    ExpressiveMenuItemSpec(
                        text = { Text(text = stringResource(R.string.home_menu_add_widget)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Widgets, contentDescription = null) },
                        testTag = "home_menu_add_widget",
                        onClick = onAddWidget,
                    ),
                    ExpressiveMenuItemSpec(
                        text = { Text(text = stringResource(R.string.home_menu_wallpaper)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Wallpaper, contentDescription = null) },
                        testTag = "home_menu_wallpaper",
                        onClick = onOpenWallpaper,
                    ),
                    ExpressiveMenuItemSpec(
                        text = { Text(text = stringResource(R.string.home_menu_edit_layout)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
                        testTag = "home_menu_edit_layout",
                        onClick = onEditLayout,
                    ),
                    ExpressiveMenuItemSpec(
                        text = { Text(text = stringResource(R.string.home_menu_settings)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Settings, contentDescription = null) },
                        testTag = "home_menu_settings",
                        onClick = onOpenSettings,
                    ),
                )

                val pageActionsGroup = buildList {
                    if (canAddPage) {
                        add(
                            ExpressiveMenuItemSpec(
                                text = { Text(text = stringResource(R.string.home_menu_add_page_start)) },
                                leadingIcon = { Icon(imageVector = Icons.Rounded.FirstPage, contentDescription = null) },
                                testTag = "home_menu_add_page_start",
                                onClick = onAddPageAtStart,
                            )
                        )
                        add(
                            ExpressiveMenuItemSpec(
                                text = { Text(text = stringResource(R.string.home_menu_add_page_end)) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.AutoMirrored.Rounded.LastPage, contentDescription = null)
                                },
                                testTag = "home_menu_add_page_end",
                                onClick = onAddPageAtEnd,
                            )
                        )
                    }
                    if (canMovePageLeft) {
                        add(
                            ExpressiveMenuItemSpec(
                                text = { Text(text = stringResource(R.string.home_menu_move_page_left)) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                                },
                                testTag = "home_menu_move_page_left",
                                onClick = onMovePageLeft,
                            )
                        )
                    }
                    if (canMovePageRight) {
                        add(
                            ExpressiveMenuItemSpec(
                                text = { Text(text = stringResource(R.string.home_menu_move_page_right)) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                                },
                                testTag = "home_menu_move_page_right",
                                onClick = onMovePageRight,
                            )
                        )
                    }
                    if (canDeleteCurrentPage) {
                        add(
                            ExpressiveMenuItemSpec(
                                text = { Text(text = stringResource(R.string.home_menu_delete_page)) },
                                leadingIcon = { Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = null) },
                                colors = errorItemColors,
                                testTag = "home_menu_delete_page",
                                onClick = onDeleteCurrentPage,
                            )
                        )
                    }
                }

                ExpressiveMenuGroupsContent(
                    groups = listOf(homeActionsGroup, pageActionsGroup),
                    parentExpanded = expanded,
                )
            }
        }
    }
}

@Immutable
data class WidgetContextMenuHost(
    val activeWidgetId: Int?,
    val onOpen: (PlacedWidget) -> Unit,
    val onDismiss: () -> Unit,
    val onReconfigure: (PlacedWidget) -> Unit,
    val onResize: (PlacedWidget) -> Unit,
    val onMovePage: (PlacedWidget) -> Unit,
    val onRemove: (PlacedWidget) -> Unit,
)

/**
 * Official Material 3 Expressive grouped context menu mounted natively inside the placed widget's [Box].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WidgetContextMenuDropdown(
    widget: PlacedWidget,
    expanded: Boolean,
    cols: Int,
    widgetHostController: WidgetHostController,
    host: WidgetContextMenuHost,
) {
    val canReconfigure = remember(widget.appWidgetId) {
        widgetHostController.isReconfigurable(widget.appWidgetId)
    }
    val widgetActionsGroup = buildList {
        if (canReconfigure) {
            add(
                ExpressiveMenuItemSpec(
                    text = { Text(text = stringResource(R.string.widget_menu_configure)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Tune, contentDescription = null) },
                    testTag = "widget_menu_configure",
                    onClick = { host.onReconfigure(widget) },
                )
            )
        }
        add(
            ExpressiveMenuItemSpec(
                text = { Text(text = stringResource(R.string.home_menu_edit_layout)) },
                leadingIcon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
                testTag = "widget_menu_edit_layout",
                onClick = { host.onResize(widget) },
            )
        )
    }

    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = host.onDismiss,
        modifier = Modifier.testTag("widget_context_menu_popup"),
    ) {
        ExpressiveMenuGroupsContent(
            groups = listOf(widgetActionsGroup),
            parentExpanded = expanded,
        )
    }
}

private enum class ResizeEdge {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

private class HandleCoordsRef(var coords: LayoutCoordinates? = null)

private data class WidgetGridRect(
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
)

private fun stepTowardValue(
    current: Int,
    desired: Int,
    isAllowed: (Int) -> Boolean,
): Int {
    var best = current
    if (desired > current) {
        for (candidate in (current + 1)..desired) {
            if (isAllowed(candidate)) best = candidate else break
        }
    } else if (desired < current) {
        for (candidate in (current - 1) downTo desired) {
            if (isAllowed(candidate)) best = candidate else break
        }
    }
    return best
}

/**
 * AOSP Launcher3-style inline widget edit frame overlay rendered in the Home root layer
 * (sized to widgetBounds + 24.dp on all 4 sides so edge borders & handles never clip).
 * Edit-mode gestures:
 * - Tap anywhere (widget body, border, or outside) -> exits edit mode.
 * - Drag any resizable border or handle (no long-press needed) -> resizes that border live.
 * - Long-press the widget body -> starts free-floating drag across cells & screen edges.
 */
@Composable
fun WidgetResizeFrameOverlay(
    widget: PlacedWidget,
    spec: WidgetResizeSpec,
    cols: Int,
    rows: Int = com.example.model.HomeGridEngine.HOME_GRID_ROWS,
    colStepPx: Float,
    rowStepPx: Float,
    isDraggingInterior: Boolean,
    canPlaceRect: (cellX: Int, cellY: Int, spanX: Int, spanY: Int) -> Boolean,
    onUpdateRect: (cellX: Int, cellY: Int, spanX: Int, spanY: Int) -> Unit,
    onTapToDismiss: () -> Unit,
    onInteriorDragStart: (Rect, Offset) -> Unit,
    onInteriorDragMove: (Offset) -> Unit,
    onInteriorDragEnd: (Offset) -> Unit,
    onInteriorDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val currentRect by rememberUpdatedState(
        WidgetGridRect(
            cellX = widget.cellX.coerceIn(0, (cols - 1).coerceAtLeast(0)),
            cellY = widget.cellY.coerceIn(0, (rows - 1).coerceAtLeast(0)),
            spanX = widget.spanX.coerceIn(1, cols),
            spanY = widget.spanY.coerceIn(1, rows),
        )
    )
    val currentCanPlaceRect by rememberUpdatedState(canPlaceRect)
    val currentOnUpdateRect by rememberUpdatedState(onUpdateRect)
    val currentOnTapToDismiss by rememberUpdatedState(onTapToDismiss)
    val currentOnInteriorDragStart by rememberUpdatedState(onInteriorDragStart)
    val currentOnInteriorDragMove by rememberUpdatedState(onInteriorDragMove)
    val currentOnInteriorDragEnd by rememberUpdatedState(onInteriorDragEnd)
    val currentOnInteriorDragCancel by rememberUpdatedState(onInteriorDragCancel)

    val frameCoordsRef = remember { HandleCoordsRef() }
    var activeEdge by remember(widget.appWidgetId) { mutableStateOf<ResizeEdge?>(null) }
    var dragDeltaPx by remember(widget.appWidgetId) { mutableFloatStateOf(0f) }
    var dragStartRect by remember(widget.appWidgetId) { mutableStateOf<WidgetGridRect?>(null) }

    val safeColStep = colStepPx.coerceAtLeast(36f)
    val safeRowStep = rowStepPx.coerceAtLeast(36f)
    val resizeDesc = stringResource(R.string.widget_menu_resize)

    Box(
        modifier = modifier
            .onGloballyPositioned { frameCoordsRef.coords = it }
            .graphicsLayer { alpha = if (isDraggingInterior) 0f else 1f }
            .testTag("widget_resize_frame_${widget.appWidgetId}"),
    ) {
        // Unified edit-mode gesture layer:
        // 1) Drag any resizable border / handle directly (no long-press) to resize
        // 2) Long-press widget interior to start free-floating drag
        // 3) Tap anywhere on the widget or border to exit edit mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widget.appWidgetId, spec, cols, rows, safeColStep, safeRowStep) {
                    val marginPx = RESIZE_HANDLE_MARGIN_DP.dp.toPx()
                    val touchSlop = viewConfiguration.touchSlop
                    val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val leftLine = marginPx
                        val rightLine = (size.width - marginPx).coerceAtLeast(leftLine)
                        val topLine = marginPx
                        val bottomLine = (size.height - marginPx).coerceAtLeast(topLine)
                        val widgetW = (rightLine - leftLine).coerceAtLeast(1f)
                        val widgetH = (bottomLine - topLine).coerceAtLeast(1f)
                        val innerSlopX = minOf(16.dp.toPx(), widgetW * 0.25f)
                        val innerSlopY = minOf(16.dp.toPx(), widgetH * 0.25f)

                        val px = down.position.x
                        val py = down.position.y

                        // Hit-test resizable borders (entire border strip + handle dot area)
                        val edgeCandidates = mutableListOf<Pair<ResizeEdge, Float>>()
                        if (spec.canResizeHorizontal && py in (topLine - marginPx)..(bottomLine + marginPx)) {
                            if (px in (leftLine - marginPx)..(leftLine + innerSlopX)) {
                                edgeCandidates += ResizeEdge.LEFT to abs(px - leftLine)
                            }
                            if (px in (rightLine - innerSlopX)..(rightLine + marginPx)) {
                                edgeCandidates += ResizeEdge.RIGHT to abs(px - rightLine)
                            }
                        }
                        if (spec.canResizeVertical && px in (leftLine - marginPx)..(rightLine + marginPx)) {
                            if (py in (topLine - marginPx)..(topLine + innerSlopY)) {
                                edgeCandidates += ResizeEdge.TOP to abs(py - topLine)
                            }
                            if (py in (bottomLine - innerSlopY)..(bottomLine + marginPx)) {
                                edgeCandidates += ResizeEdge.BOTTOM to abs(py - bottomLine)
                            }
                        }
                        val hitEdge = edgeCandidates.minByOrNull { it.second }?.first
                        val insideWidget = px in leftLine..rightLine && py in topLine..bottomLine

                        if (hitEdge != null) {
                            // Immediate border drag (no long-press); tap without dragging exits edit mode
                            down.consume()
                            val startWindow = frameCoordsRef.coords?.takeIf { it.isAttached }?.localToWindow(down.position)
                                ?: down.position
                            val start = currentRect
                            var latest = start
                            val anchorRight = start.cellX + start.spanX
                            val anchorBottom = start.cellY + start.spanY
                            val effMinX = minOf(spec.minSpanX, start.spanX)
                            val effMaxX = maxOf(spec.maxSpanX, start.spanX)
                            val effMinY = minOf(spec.minSpanY, start.spanY)
                            val effMaxY = maxOf(spec.maxSpanY, start.spanY)
                            val stepPx = when (hitEdge) {
                                ResizeEdge.LEFT, ResizeEdge.RIGHT -> safeColStep
                                ResizeEdge.TOP, ResizeEdge.BOTTOM -> safeRowStep
                            }
                            var didDragBorder = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                val nowWindow = frameCoordsRef.coords?.takeIf { it.isAttached }?.localToWindow(change.position)
                                    ?: startWindow
                                val totalDelta = nowWindow - startWindow

                                if (!change.pressed) {
                                    if (!didDragBorder) {
                                        currentOnTapToDismiss()
                                    }
                                    break
                                }

                                if (!didDragBorder) {
                                    if (totalDelta.getDistance() <= touchSlop) continue
                                    didDragBorder = true
                                    activeEdge = hitEdge
                                    dragStartRect = start
                                    dragDeltaPx = 0f
                                }

                                when (hitEdge) {
                                    ResizeEdge.RIGHT -> {
                                        val signedDx = totalDelta.x
                                        val maxAllowedSpan = minOf(effMaxX, cols - start.cellX)
                                        val maxReachableSpan = stepTowardValue(start.spanX, maxAllowedSpan) { cand ->
                                            currentCanPlaceRect(start.cellX, start.cellY, cand, start.spanY)
                                        }
                                        val desiredSpan = (start.spanX + (signedDx / stepPx).roundToInt())
                                            .coerceIn(effMinX, maxAllowedSpan)
                                        val acceptedSpan = stepTowardValue(latest.spanX, desiredSpan) { cand ->
                                            currentCanPlaceRect(start.cellX, start.cellY, cand, start.spanY)
                                        }
                                        if (acceptedSpan != latest.spanX) {
                                            latest = latest.copy(spanX = acceptedSpan)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            currentOnUpdateRect(latest.cellX, latest.cellY, latest.spanX, latest.spanY)
                                        }
                                        val minDelta = -(start.spanX - effMinX) * stepPx
                                        val maxDelta = (maxReachableSpan - start.spanX) * stepPx
                                        dragDeltaPx = signedDx.coerceIn(minDelta, maxDelta)
                                    }

                                    ResizeEdge.LEFT -> {
                                        val signedDx = -totalDelta.x
                                        val maxAllowedSpan = minOf(effMaxX, anchorRight)
                                        val maxReachableSpan = stepTowardValue(start.spanX, maxAllowedSpan) { cand ->
                                            currentCanPlaceRect(anchorRight - cand, start.cellY, cand, start.spanY)
                                        }
                                        val desiredSpan = (start.spanX + (signedDx / stepPx).roundToInt())
                                            .coerceIn(effMinX, maxAllowedSpan)
                                        val acceptedSpan = stepTowardValue(latest.spanX, desiredSpan) { cand ->
                                            currentCanPlaceRect(anchorRight - cand, start.cellY, cand, start.spanY)
                                        }
                                        if (acceptedSpan != latest.spanX) {
                                            latest = latest.copy(cellX = anchorRight - acceptedSpan, spanX = acceptedSpan)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            currentOnUpdateRect(latest.cellX, latest.cellY, latest.spanX, latest.spanY)
                                        }
                                        val minDelta = -(maxReachableSpan - start.spanX) * stepPx
                                        val maxDelta = (start.spanX - effMinX) * stepPx
                                        dragDeltaPx = totalDelta.x.coerceIn(minDelta, maxDelta)
                                    }

                                    ResizeEdge.BOTTOM -> {
                                        val signedDy = totalDelta.y
                                        val maxAllowedSpan = minOf(effMaxY, rows - start.cellY)
                                        val maxReachableSpan = stepTowardValue(start.spanY, maxAllowedSpan) { cand ->
                                            currentCanPlaceRect(start.cellX, start.cellY, start.spanX, cand)
                                        }
                                        val desiredSpan = (start.spanY + (signedDy / stepPx).roundToInt())
                                            .coerceIn(effMinY, maxAllowedSpan)
                                        val acceptedSpan = stepTowardValue(latest.spanY, desiredSpan) { cand ->
                                            currentCanPlaceRect(start.cellX, start.cellY, start.spanX, cand)
                                        }
                                        if (acceptedSpan != latest.spanY) {
                                            latest = latest.copy(spanY = acceptedSpan)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            currentOnUpdateRect(latest.cellX, latest.cellY, latest.spanX, latest.spanY)
                                        }
                                        val minDelta = -(start.spanY - effMinY) * stepPx
                                        val maxDelta = (maxReachableSpan - start.spanY) * stepPx
                                        dragDeltaPx = signedDy.coerceIn(minDelta, maxDelta)
                                    }

                                    ResizeEdge.TOP -> {
                                        val signedDy = -totalDelta.y
                                        val maxAllowedSpan = minOf(effMaxY, anchorBottom)
                                        val maxReachableSpan = stepTowardValue(start.spanY, maxAllowedSpan) { cand ->
                                            currentCanPlaceRect(start.cellX, anchorBottom - cand, start.spanX, cand)
                                        }
                                        val desiredSpan = (start.spanY + (signedDy / stepPx).roundToInt())
                                            .coerceIn(effMinY, maxAllowedSpan)
                                        val acceptedSpan = stepTowardValue(latest.spanY, desiredSpan) { cand ->
                                            currentCanPlaceRect(start.cellX, anchorBottom - cand, start.spanX, cand)
                                        }
                                        if (acceptedSpan != latest.spanY) {
                                            latest = latest.copy(cellY = anchorBottom - acceptedSpan, spanY = acceptedSpan)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            currentOnUpdateRect(latest.cellX, latest.cellY, latest.spanX, latest.spanY)
                                        }
                                        val minDelta = -(maxReachableSpan - start.spanY) * stepPx
                                        val maxDelta = (start.spanY - effMinY) * stepPx
                                        dragDeltaPx = totalDelta.y.coerceIn(minDelta, maxDelta)
                                    }
                                }
                            }
                            activeEdge = null
                            dragStartRect = null
                            dragDeltaPx = 0f
                        } else if (insideWidget) {
                            // Widget body: tap exits edit mode; long-press starts free-floating drag
                            down.consume()
                            val startWindow = frameCoordsRef.coords?.takeIf { it.isAttached }?.localToWindow(down.position)
                                ?: down.position
                            val frameWindowBounds = frameCoordsRef.coords?.takeIf { it.isAttached }?.boundsInWindow()
                            val widgetBounds = if (frameWindowBounds != null) {
                                Rect(
                                    left = frameWindowBounds.left + marginPx,
                                    top = frameWindowBounds.top + marginPx,
                                    right = frameWindowBounds.right - marginPx,
                                    bottom = frameWindowBounds.bottom - marginPx,
                                )
                            } else {
                                Rect.Zero
                            }

                            val deadline = SystemClock.uptimeMillis() + longPressTimeoutMs
                            var longPressFired = false
                            var movedBeforeLongPress = false
                            var latestWindow = startWindow

                            while (true) {
                                val remaining = deadline - SystemClock.uptimeMillis()
                                if (remaining <= 0L) {
                                    longPressFired = true
                                    break
                                }
                                val ev = withTimeoutOrNull(remaining) {
                                    awaitPointerEvent()
                                }
                                if (ev == null) {
                                    longPressFired = true
                                    break
                                }
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                ch.consume()
                                val nowWindow = frameCoordsRef.coords?.takeIf { it.isAttached }?.localToWindow(ch.position)
                                    ?: latestWindow
                                latestWindow = nowWindow

                                if (!ch.pressed) {
                                    if (!movedBeforeLongPress && (nowWindow - startWindow).getDistance() <= touchSlop) {
                                        currentOnTapToDismiss()
                                    }
                                    break
                                }

                                if ((nowWindow - startWindow).getDistance() > touchSlop) {
                                    movedBeforeLongPress = true
                                    // Drain remaining events until lift without starting a drag
                                    while (true) {
                                        val drainEv = awaitPointerEvent()
                                        val drainCh = drainEv.changes.firstOrNull { it.id == down.id } ?: break
                                        drainCh.consume()
                                        if (!drainCh.pressed) break
                                    }
                                    break
                                }
                            }

                            if (longPressFired) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnInteriorDragStart(widgetBounds, latestWindow)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id }
                                    if (ch == null) {
                                        currentOnInteriorDragCancel()
                                        break
                                    }
                                    ch.consume()
                                    val nowWindow = frameCoordsRef.coords?.takeIf { it.isAttached }?.localToWindow(ch.position)
                                        ?: latestWindow
                                    latestWindow = nowWindow
                                    if (!ch.pressed) {
                                        currentOnInteriorDragEnd(nowWindow)
                                        break
                                    }
                                    currentOnInteriorDragMove(nowWindow)
                                }
                            }
                        }
                    }
                },
        )

        // Dynamic rounded frame outline matching AOSP Launcher3 AppWidgetResizeFrame
        val startRef = dragStartRect
        val leftDelta = if (activeEdge == ResizeEdge.LEFT && startRef != null) {
            dragDeltaPx - (currentRect.cellX - startRef.cellX) * safeColStep
        } else {
            0f
        }
        val rightDelta = if (activeEdge == ResizeEdge.RIGHT && startRef != null) {
            dragDeltaPx - ((currentRect.cellX + currentRect.spanX) - (startRef.cellX + startRef.spanX)) * safeColStep
        } else {
            0f
        }
        val topDelta = if (activeEdge == ResizeEdge.TOP && startRef != null) {
            dragDeltaPx - (currentRect.cellY - startRef.cellY) * safeRowStep
        } else {
            0f
        }
        val bottomDelta = if (activeEdge == ResizeEdge.BOTTOM && startRef != null) {
            dragDeltaPx - ((currentRect.cellY + currentRect.spanY) - (startRef.cellY + startRef.spanY)) * safeRowStep
        } else {
            0f
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = RESIZE_HANDLE_MARGIN_DP.dp.toPx()
            val rectLeft = inset + leftDelta
            val rectTop = inset + topDelta
            val baseW = (size.width - inset * 2f).coerceAtLeast(24.dp.toPx())
            val baseH = (size.height - inset * 2f).coerceAtLeast(24.dp.toPx())
            val rectWidth = (baseW - leftDelta + rightDelta).coerceAtLeast(24.dp.toPx())
            val rectHeight = (baseH - topDelta + bottomDelta).coerceAtLeast(24.dp.toPx())
            val radius = CornerRadius(16.dp.toPx(), 16.dp.toPx())

            drawRoundRect(
                color = primaryColor.copy(alpha = 0.10f),
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectWidth, rectHeight),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectWidth, rectHeight),
                cornerRadius = radius,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }

        if (spec.canResizeHorizontal) {
            ResizeHandleDot(
                edge = ResizeEdge.LEFT,
                alignment = Alignment.CenterStart,
                activeResidualPx = leftDelta,
                primaryColor = primaryColor,
                ringColor = onPrimaryColor,
                contentDesc = resizeDesc,
            )
            ResizeHandleDot(
                edge = ResizeEdge.RIGHT,
                alignment = Alignment.CenterEnd,
                activeResidualPx = rightDelta,
                primaryColor = primaryColor,
                ringColor = onPrimaryColor,
                contentDesc = resizeDesc,
            )
        }

        if (spec.canResizeVertical) {
            ResizeHandleDot(
                edge = ResizeEdge.TOP,
                alignment = Alignment.TopCenter,
                activeResidualPx = topDelta,
                primaryColor = primaryColor,
                ringColor = onPrimaryColor,
                contentDesc = resizeDesc,
            )
            ResizeHandleDot(
                edge = ResizeEdge.BOTTOM,
                alignment = Alignment.BottomCenter,
                activeResidualPx = bottomDelta,
                primaryColor = primaryColor,
                ringColor = onPrimaryColor,
                contentDesc = resizeDesc,
            )
        }
    }
}

const val RESIZE_HANDLE_MARGIN_DP = 24

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ResizeHandleDot(
    edge: ResizeEdge,
    alignment: Alignment,
    activeResidualPx: Float,
    primaryColor: androidx.compose.ui.graphics.Color,
    ringColor: androidx.compose.ui.graphics.Color,
    contentDesc: String,
) {
    val edgeShift = when (edge) {
        ResizeEdge.LEFT, ResizeEdge.RIGHT -> IntOffset(activeResidualPx.roundToInt(), 0)
        ResizeEdge.TOP, ResizeEdge.BOTTOM -> IntOffset(0, activeResidualPx.roundToInt())
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(alignment)
            .offset { edgeShift }
            .size((RESIZE_HANDLE_MARGIN_DP * 2).dp)
            .semantics { contentDescription = contentDesc }
            .testTag("widget_resize_handle_${edge.name.lowercase()}"),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(primaryColor)
                .border(width = 2.dp, color = ringColor, shape = CircleShape),
        )
    }
}
