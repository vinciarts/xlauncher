package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.PlacedWidget
import com.example.model.WidgetMenuTarget
import com.example.model.WidgetResizeSpec
import kotlin.math.roundToInt

/**
 * Standard Material 3 DropdownMenu anchored to the long-pressed point on empty desktop space.
 */
@Composable
fun HomeEmptyMenuPopup(
    anchorOffset: Offset,
    canAddPage: Boolean,
    canDeleteCurrentPage: Boolean,
    onAddWidget: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddPageAtStart: () -> Unit,
    onAddPageAtEnd: () -> Unit,
    onDeleteCurrentPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(anchorOffset.x.roundToInt(), anchorOffset.y.roundToInt()) }
                .size(1.dp),
        ) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("home_empty_menu_popup"),
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.home_menu_add_widget)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Widgets, contentDescription = null) },
                    onClick = onAddWidget,
                    modifier = Modifier.testTag("home_menu_add_widget"),
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.home_menu_wallpaper)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Wallpaper, contentDescription = null) },
                    onClick = onOpenWallpaper,
                    modifier = Modifier.testTag("home_menu_wallpaper"),
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.home_menu_settings)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Settings, contentDescription = null) },
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("home_menu_settings"),
                )

                if (canAddPage || canDeleteCurrentPage) {
                    HorizontalDivider()
                }
                if (canAddPage) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.home_menu_add_page_start)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.FirstPage, contentDescription = null) },
                        onClick = onAddPageAtStart,
                        modifier = Modifier.testTag("home_menu_add_page_start"),
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.home_menu_add_page_end)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.LastPage, contentDescription = null) },
                        onClick = onAddPageAtEnd,
                        modifier = Modifier.testTag("home_menu_add_page_end"),
                    )
                }
                if (canDeleteCurrentPage) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.home_menu_delete_page)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = null) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error,
                        ),
                        onClick = onDeleteCurrentPage,
                        modifier = Modifier.testTag("home_menu_delete_page"),
                    )
                }
            }
        }
    }
}

/**
 * Standard Material 3 DropdownMenu anchored to a long-pressed Home widget's bounds.
 */
@Composable
fun WidgetContextMenuPopup(
    target: WidgetMenuTarget,
    canReconfigure: Boolean,
    canResize: Boolean,
    onDismiss: () -> Unit,
    onReconfigure: () -> Unit,
    onResize: () -> Unit,
    onMovePage: () -> Unit,
    onRemove: () -> Unit,
) {
    val density = LocalDensity.current
    val widgetBounds = target.anchorBounds

    Box(modifier = Modifier.fillMaxSize()) {
        val anchorModifier = if (widgetBounds != null) {
            val widthDp = with(density) { widgetBounds.width.toDp().coerceAtLeast(1.dp) }
            val heightDp = with(density) { widgetBounds.height.toDp().coerceAtLeast(1.dp) }
            Modifier
                .offset { IntOffset(widgetBounds.left.roundToInt(), widgetBounds.top.roundToInt()) }
                .size(width = widthDp, height = heightDp)
        } else {
            Modifier.align(Alignment.Center)
        }

        Box(modifier = anchorModifier) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("widget_context_menu_popup"),
            ) {
                if (canReconfigure) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.widget_menu_configure)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Tune, contentDescription = null) },
                        onClick = onReconfigure,
                        modifier = Modifier.testTag("widget_menu_configure"),
                    )
                }
                if (canResize) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.widget_menu_resize)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.AspectRatio, contentDescription = null) },
                        onClick = onResize,
                        modifier = Modifier.testTag("widget_menu_resize"),
                    )
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.widget_menu_move_page)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null)
                    },
                    onClick = onMovePage,
                    modifier = Modifier.testTag("widget_menu_move_page"),
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.widget_menu_remove)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = onRemove,
                    modifier = Modifier.testTag("widget_menu_remove"),
                )
            }
        }
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
 * AOSP Launcher3-style inline AppWidgetResizeFrame overlay rendered in the Home root layer
 * (sized to widgetBounds + 24.dp on all 4 sides so every 48.dp edge handle sits centered
 * on the border with full unclipped touch area).
 * - LEFT / TOP handles shift top-left (cellX, cellY) while keeping opposite edge anchored.
 * - RIGHT / BOTTOM handles keep top-left (cellX, cellY) anchored.
 * - Dragging the widget interior directly repositions top-left (cellX, cellY) on the current page.
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
    val currentOnInteriorDragStart by rememberUpdatedState(onInteriorDragStart)
    val currentOnInteriorDragMove by rememberUpdatedState(onInteriorDragMove)
    val currentOnInteriorDragEnd by rememberUpdatedState(onInteriorDragEnd)
    val currentOnInteriorDragCancel by rememberUpdatedState(onInteriorDragCancel)

    val frameCoordsRef = remember { HandleCoordsRef() }
    var activeEdge by remember(widget.appWidgetId) { mutableStateOf<ResizeEdge?>(null) }
    var residualPx by remember(widget.appWidgetId) { mutableFloatStateOf(0f) }

    val safeColStep = colStepPx.coerceAtLeast(36f)
    val safeRowStep = rowStepPx.coerceAtLeast(36f)
    val resizeDesc = stringResource(R.string.widget_menu_resize)

    Box(
        modifier = modifier
            .onGloballyPositioned { frameCoordsRef.coords = it }
            .graphicsLayer { alpha = if (isDraggingInterior) 0f else 1f }
            .testTag("widget_resize_frame_${widget.appWidgetId}"),
    ) {
        // Interior drag hands off to free-floating HomeDragSession (can cross over apps/widgets & trigger edge page-flip)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widget.appWidgetId) {
                    val marginPx = RESIZE_HANDLE_MARGIN_DP.dp.toPx()
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val insideWidget = down.position.x in marginPx..(size.width - marginPx) &&
                            down.position.y in marginPx..(size.height - marginPx)
                        if (insideWidget) {
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
                            var isMoving = false
                            var latestWindow = startWindow

                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id }
                                if (ch == null) {
                                    if (isMoving) currentOnInteriorDragCancel()
                                    break
                                }
                                ch.consume()
                                val nowWindow = frameCoordsRef.coords?.takeIf { it.isAttached }?.localToWindow(ch.position)
                                    ?: latestWindow
                                latestWindow = nowWindow

                                if (!ch.pressed) {
                                    if (isMoving) currentOnInteriorDragEnd(nowWindow)
                                    break
                                }

                                if (!isMoving) {
                                    if ((nowWindow - startWindow).getDistance() > touchSlop) {
                                        isMoving = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentOnInteriorDragStart(widgetBounds, nowWindow)
                                    }
                                } else {
                                    currentOnInteriorDragMove(nowWindow)
                                }
                            }
                        }
                    }
                },
        )

        // Dynamic rounded frame outline matching AOSP Launcher3 AppWidgetResizeFrame
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = RESIZE_HANDLE_MARGIN_DP.dp.toPx()
            val leftDelta = if (activeEdge == ResizeEdge.LEFT) -residualPx else 0f
            val rightDelta = if (activeEdge == ResizeEdge.RIGHT) residualPx else 0f
            val topDelta = if (activeEdge == ResizeEdge.TOP) -residualPx else 0f
            val bottomDelta = if (activeEdge == ResizeEdge.BOTTOM) residualPx else 0f

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
                widgetId = widget.appWidgetId,
                alignment = Alignment.CenterStart,
                activeResidualPx = if (activeEdge == ResizeEdge.LEFT) -residualPx else 0f,
                cols = cols,
                rows = rows,
                primaryColor = primaryColor,
                ringColor = onPrimaryColor,
                contentDesc = resizeDesc,
                onDragStart = { activeEdge = ResizeEdge.LEFT; residualPx = 0f },
                onDragEnd = { activeEdge = null; residualPx = 0f },
                getCurrentRect = { currentRect },
                canPlaceRect = { cx, cy, sx, sy -> currentCanPlaceRect(cx, cy, sx, sy) },
                spec = spec,
                stepPx = safeColStep,
                onResidualChanged = { residualPx = it },
                onRectChanged = { rect ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnUpdateRect(rect.cellX, rect.cellY, rect.spanX, rect.spanY)
                },
            )
            ResizeHandleDot(
                edge = ResizeEdge.RIGHT,
                widgetId = widget.appWidgetId,
                alignment = Alignment.CenterEnd,
                activeResidualPx = if (activeEdge == ResizeEdge.RIGHT) residualPx else 0f,
                cols = cols,
                rows = rows,
                primaryColor = primaryColor,
                ringColor = onPrimaryColor,
                contentDesc = resizeDesc,
                onDragStart = { activeEdge = ResizeEdge.RIGHT; residualPx = 0f },
                onDragEnd = { activeEdge = null; residualPx = 0f },
                getCurrentRect = { currentRect },
                canPlaceRect = { cx, cy, sx, sy -> currentCanPlaceRect(cx, cy, sx, sy) },
                spec = spec,
                stepPx = safeColStep,
                onResidualChanged = { residualPx = it },
                onRectChanged = { rect ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnUpdateRect(rect.cellX, rect.cellY, rect.spanX, rect.spanY)
                },
            )
        }

        if (spec.canResizeVertical) {
            ResizeHandleDot(
                edge = ResizeEdge.TOP,
                widgetId = widget.appWidgetId,
                alignment = Alignment.TopCenter,
                activeResidualPx = if (activeEdge == ResizeEdge.TOP) -residualPx else 0f,
                cols = cols,
                rows = rows,
                primaryColor = primaryColor,
                ringColor = onPrimaryColor,
                contentDesc = resizeDesc,
                onDragStart = { activeEdge = ResizeEdge.TOP; residualPx = 0f },
                onDragEnd = { activeEdge = null; residualPx = 0f },
                getCurrentRect = { currentRect },
                canPlaceRect = { cx, cy, sx, sy -> currentCanPlaceRect(cx, cy, sx, sy) },
                spec = spec,
                stepPx = safeRowStep,
                onResidualChanged = { residualPx = it },
                onRectChanged = { rect ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnUpdateRect(rect.cellX, rect.cellY, rect.spanX, rect.spanY)
                },
            )
            ResizeHandleDot(
                edge = ResizeEdge.BOTTOM,
                widgetId = widget.appWidgetId,
                alignment = Alignment.BottomCenter,
                activeResidualPx = if (activeEdge == ResizeEdge.BOTTOM) residualPx else 0f,
                cols = cols,
                rows = rows,
                primaryColor = primaryColor,
                ringColor = onPrimaryColor,
                contentDesc = resizeDesc,
                onDragStart = { activeEdge = ResizeEdge.BOTTOM; residualPx = 0f },
                onDragEnd = { activeEdge = null; residualPx = 0f },
                getCurrentRect = { currentRect },
                canPlaceRect = { cx, cy, sx, sy -> currentCanPlaceRect(cx, cy, sx, sy) },
                spec = spec,
                stepPx = safeRowStep,
                onResidualChanged = { residualPx = it },
                onRectChanged = { rect ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnUpdateRect(rect.cellX, rect.cellY, rect.spanX, rect.spanY)
                },
            )
        }
    }
}

const val RESIZE_HANDLE_MARGIN_DP = 24

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ResizeHandleDot(
    edge: ResizeEdge,
    widgetId: Int,
    alignment: Alignment,
    activeResidualPx: Float,
    cols: Int,
    rows: Int,
    primaryColor: androidx.compose.ui.graphics.Color,
    ringColor: androidx.compose.ui.graphics.Color,
    contentDesc: String,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    getCurrentRect: () -> WidgetGridRect,
    canPlaceRect: (cellX: Int, cellY: Int, spanX: Int, spanY: Int) -> Boolean,
    spec: WidgetResizeSpec,
    stepPx: Float,
    onResidualChanged: (Float) -> Unit,
    onRectChanged: (WidgetGridRect) -> Unit,
) {
    val coordsRef = remember { HandleCoordsRef() }

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
            .onGloballyPositioned { coordsRef.coords = it }
            .semantics { contentDescription = contentDesc }
            .testTag("widget_resize_handle_${edge.name.lowercase()}")
            .pointerInput(widgetId, edge, spec, cols, rows, stepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val startWindow = coordsRef.coords?.takeIf { it.isAttached }?.localToWindow(down.position)
                        ?: down.position
                    val start = getCurrentRect()
                    var latest = start
                    val anchorRight = start.cellX + start.spanX
                    val anchorBottom = start.cellY + start.spanY
                    val effMinX = minOf(spec.minSpanX, start.spanX)
                    val effMaxX = maxOf(spec.maxSpanX, start.spanX)
                    val effMinY = minOf(spec.minSpanY, start.spanY)
                    val effMaxY = maxOf(spec.maxSpanY, start.spanY)
                    onDragStart()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break

                        val nowWindow = coordsRef.coords?.takeIf { it.isAttached }?.localToWindow(change.position)
                            ?: change.position
                        val totalDelta = nowWindow - startWindow
                        val limit = stepPx * 0.45f

                        when (edge) {
                            ResizeEdge.RIGHT -> {
                                val signedDx = totalDelta.x
                                val maxAllowedSpan = minOf(effMaxX, cols - start.cellX)
                                val desiredSpan = (start.spanX + (signedDx / stepPx).roundToInt())
                                    .coerceIn(effMinX, maxAllowedSpan)
                                val acceptedSpan = stepTowardValue(latest.spanX, desiredSpan) { cand ->
                                    canPlaceRect(start.cellX, start.cellY, cand, start.spanY)
                                }
                                if (acceptedSpan != latest.spanX) {
                                    latest = latest.copy(spanX = acceptedSpan)
                                    onRectChanged(latest)
                                }
                                val rawResidual = signedDx - (latest.spanX - start.spanX) * stepPx
                                val blocked = acceptedSpan != desiredSpan
                                onResidualChanged(
                                    if (blocked) rawResidual.coerceIn(-limit * 0.35f, limit * 0.35f)
                                    else rawResidual.coerceIn(-limit, limit)
                                )
                            }

                            ResizeEdge.LEFT -> {
                                val signedDx = -totalDelta.x
                                val maxAllowedSpan = minOf(effMaxX, anchorRight)
                                val desiredSpan = (start.spanX + (signedDx / stepPx).roundToInt())
                                    .coerceIn(effMinX, maxAllowedSpan)
                                val acceptedSpan = stepTowardValue(latest.spanX, desiredSpan) { cand ->
                                    canPlaceRect(anchorRight - cand, start.cellY, cand, start.spanY)
                                }
                                if (acceptedSpan != latest.spanX) {
                                    latest = latest.copy(cellX = anchorRight - acceptedSpan, spanX = acceptedSpan)
                                    onRectChanged(latest)
                                }
                                val rawResidual = signedDx - (latest.spanX - start.spanX) * stepPx
                                val blocked = acceptedSpan != desiredSpan
                                onResidualChanged(
                                    if (blocked) rawResidual.coerceIn(-limit * 0.35f, limit * 0.35f)
                                    else rawResidual.coerceIn(-limit, limit)
                                )
                            }

                            ResizeEdge.BOTTOM -> {
                                val signedDy = totalDelta.y
                                val maxAllowedSpan = minOf(effMaxY, rows - start.cellY)
                                val desiredSpan = (start.spanY + (signedDy / stepPx).roundToInt())
                                    .coerceIn(effMinY, maxAllowedSpan)
                                val acceptedSpan = stepTowardValue(latest.spanY, desiredSpan) { cand ->
                                    canPlaceRect(start.cellX, start.cellY, start.spanX, cand)
                                }
                                if (acceptedSpan != latest.spanY) {
                                    latest = latest.copy(spanY = acceptedSpan)
                                    onRectChanged(latest)
                                }
                                val rawResidual = signedDy - (latest.spanY - start.spanY) * stepPx
                                val blocked = acceptedSpan != desiredSpan
                                onResidualChanged(
                                    if (blocked) rawResidual.coerceIn(-limit * 0.35f, limit * 0.35f)
                                    else rawResidual.coerceIn(-limit, limit)
                                )
                            }

                            ResizeEdge.TOP -> {
                                val signedDy = -totalDelta.y
                                val maxAllowedSpan = minOf(effMaxY, anchorBottom)
                                val desiredSpan = (start.spanY + (signedDy / stepPx).roundToInt())
                                    .coerceIn(effMinY, maxAllowedSpan)
                                val acceptedSpan = stepTowardValue(latest.spanY, desiredSpan) { cand ->
                                    canPlaceRect(start.cellX, anchorBottom - cand, start.spanX, cand)
                                }
                                if (acceptedSpan != latest.spanY) {
                                    latest = latest.copy(cellY = anchorBottom - acceptedSpan, spanY = acceptedSpan)
                                    onRectChanged(latest)
                                }
                                val rawResidual = signedDy - (latest.spanY - start.spanY) * stepPx
                                val blocked = acceptedSpan != desiredSpan
                                onResidualChanged(
                                    if (blocked) rawResidual.coerceIn(-limit * 0.35f, limit * 0.35f)
                                    else rawResidual.coerceIn(-limit, limit)
                                )
                            }
                        }
                    }
                    onDragEnd()
                }
            },
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
