package com.example.ui.screens.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.AppActionOrigin
import com.example.model.AppItem
import com.example.model.HomeAppSlot
import com.example.model.HomeGridEngine
import com.example.model.PlacedWidget
import com.example.model.SwipeDirection
import com.example.ui.components.AppContextMenuDropdown
import com.example.ui.components.AppContextMenuHost
import com.example.ui.components.AppIconTile
import com.example.ui.components.PageIndicatorDots
import com.example.ui.components.WidgetContextMenuDropdown
import com.example.ui.components.WidgetContextMenuHost
import com.example.widget.WidgetHostController
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Composable
internal fun HomePageIndicatorBar(
    pagerState: PagerState,
    pageCount: Int,
    loopEnabled: Boolean,
    overridePageCount: Int? = null,
    overrideCurrentPage: Int? = null,
) {
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        val realCurrent = ((pagerState.currentPage % pageCount) + pageCount) % pageCount
        val shownCount = overridePageCount ?: pageCount
        val shownCurrent = overrideCurrentPage ?: realCurrent
        PageIndicatorDots(
            pageCount = shownCount,
            currentPage = shownCurrent,
            activeColor = Color.White,
            inactiveColor = Color.White.copy(alpha = 0.38f),
            onSelectPage = { targetReal ->
                if (overridePageCount != null) return@PageIndicatorDots
                scope.launch {
                    val targetVirtual = if (loopEnabled) {
                        pagerState.currentPage + (targetReal - realCurrent)
                    } else {
                        targetReal
                    }
                    pagerState.animateScrollToPage(targetVirtual)
                }
            },
        )
    }
}

@Composable
internal fun HomePageContent(
    isLoading: Boolean,
    realPage: Int,
    pageApps: ImmutableList<HomeAppSlot>,
    pageWidgets: ImmutableList<PlacedWidget>,
    isPreferredWidgetSlot: Boolean,
    isCurrentVirtualPage: Boolean,
    isHomeEditMode: Boolean,
    resizingWidgetId: Int?,
    draggedAppKey: String?,
    draggedWidgetId: Int?,
    cols: Int,
    rows: Int,
    showAppLabels: Boolean = true,
    appGestures: ImmutableMap<String, ImmutableMap<SwipeDirection, String>>,
    widgetHostController: WidgetHostController,
    appMenuHost: AppContextMenuHost,
    widgetMenuHost: WidgetContextMenuHost,
    onResizingWidgetBoundsChanged: (Rect) -> Unit,
    onSelectEditWidget: (PlacedWidget) -> Unit,
    onIconDownPointer: (Long, Set<SwipeDirection>) -> Unit,
    onWidgetTouchDown: (Long?) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppSwipe: (AppItem, SwipeDirection, AppActionOrigin) -> Unit,
    onAppDragStart: (HomeAppSlot, Rect, Offset) -> Unit,
    onAppDragMove: (Offset) -> Unit,
    onAppDragEnd: (Offset) -> Unit,
    onAppDragCancel: () -> Unit,
    onWidgetDragStart: (PlacedWidget, Rect, Offset) -> Unit,
    onWidgetDragMove: (Offset) -> Unit,
    onWidgetDragEnd: (Offset) -> Unit,
    onWidgetDragCancel: () -> Unit,
) {
    val density = LocalDensity.current
    val safeCols = cols.coerceIn(HomeGridEngine.MIN_HOME_GRID_COLUMNS, HomeGridEngine.MAX_HOME_GRID_COLUMNS)
    val safeRows = rows.coerceIn(HomeGridEngine.MIN_HOME_GRID_ROWS, HomeGridEngine.MAX_HOME_GRID_ROWS)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GRID_HORIZONTAL_PAD_DP.dp)
            .padding(top = GRID_TOP_PAD_DP.dp, bottom = GRID_BOTTOM_PAD_DP.dp),
    ) {
        val innerWidthPx = with(density) { maxWidth.toPx() }
        val innerHeightPx = with(density) { maxHeight.toPx() }
        val colGapPx = with(density) { CELL_COL_GAP_DP.dp.toPx() }
        val rowGapPx = with(density) { CELL_ROW_GAP_DP.dp.toPx() }
        val cellWidthPx = ((innerWidthPx - colGapPx * (safeCols - 1)) / safeCols).coerceAtLeast(24f)
        val cellHeightPx = ((innerHeightPx - rowGapPx * (safeRows - 1)) / safeRows).coerceAtLeast(24f)
        val colStepPx = cellWidthPx + colGapPx
        val rowStepPx = cellHeightPx + rowGapPx

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        LoadingIndicator(color = Color.White)
                    }
                }

                pageApps.isEmpty() && pageWidgets.isEmpty() -> Unit

                else -> {
                    val cellHeightDp = with(density) { cellHeightPx.toDp() }
                    val iconSize = when {
                        safeCols >= 7 || cellHeightDp < 64.dp -> 38.dp
                        safeCols >= 5 || cellHeightDp < 86.dp -> 46.dp
                        else -> 52.dp
                    }

                    // 2D CellLayout widgets at exact (cellX, cellY, spanX, spanY)
                    pageWidgets.forEach { widget ->
                        key(widget.appWidgetId) {
                            val sx = widget.spanX.coerceIn(1, safeCols)
                            val sy = widget.spanY.coerceIn(1, safeRows)
                            val cx = widget.cellX.coerceIn(0, (safeCols - sx).coerceAtLeast(0))
                            val cy = widget.cellY.coerceIn(0, (safeRows - sy).coerceAtLeast(0))
                            val leftPx = (cx * colStepPx).roundToInt()
                            val topPx = (cy * rowStepPx).roundToInt()
                            val widthDp = with(density) { (sx * cellWidthPx + (sx - 1) * colGapPx).toDp() }
                            val heightDp = with(density) { (sy * cellHeightPx + (sy - 1) * rowGapPx).toDp() }
                            val isResizing = isPreferredWidgetSlot && resizingWidgetId == widget.appWidgetId
                            val isBeingDragged = draggedWidgetId == widget.appWidgetId

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(leftPx, topPx) }
                                    .size(width = widthDp, height = heightDp)
                                    .alpha(if (isBeingDragged) 0.25f else 1f),
                            ) {
                                HomeWidgetSlot(
                                    widget = widget,
                                    isPreferredSlot = isPreferredWidgetSlot,
                                    isCurrentVirtualPage = isCurrentVirtualPage,
                                    isHomeEditMode = isHomeEditMode,
                                    isResizing = isResizing,
                                    isBeingDragged = isBeingDragged,
                                    widgetHostController = widgetHostController,
                                    onResizingBoundsChanged = onResizingWidgetBoundsChanged,
                                    onSelectInEditMode = { onSelectEditWidget(widget) },
                                    onWidgetDragStart = { bounds, pos -> onWidgetDragStart(widget, bounds, pos) },
                                    onWidgetDragMove = onWidgetDragMove,
                                    onWidgetDragEnd = onWidgetDragEnd,
                                    onWidgetDragCancel = onWidgetDragCancel,
                                    onTouchDown = onWidgetTouchDown,
                                    onLongClick = { if (!isHomeEditMode) widgetMenuHost.onOpen(widget) },
                                )
                                WidgetContextMenuDropdown(
                                    widget = widget,
                                    expanded = !isHomeEditMode &&
                                        isCurrentVirtualPage &&
                                        widgetMenuHost.activeWidgetId == widget.appWidgetId,
                                    cols = safeCols,
                                    widgetHostController = widgetHostController,
                                    host = widgetMenuHost,
                                )
                            }
                        }
                    }

                    // 2D CellLayout app icons at exact (cellX, cellY, 1x1)
                    pageApps.forEach { slot ->
                        key(slot.app.componentKey) {
                            val cx = slot.cellX.coerceIn(0, safeCols - 1)
                            val cy = slot.cellY.coerceIn(0, safeRows - 1)
                            val leftPx = (cx * colStepPx).roundToInt()
                            val topPx = (cy * rowStepPx).roundToInt()
                            val widthDp = with(density) { cellWidthPx.toDp() }
                            val boundDirs = appGestures[slot.app.componentKey]?.keys ?: persistentSetOf()
                            val isBeingDragged = draggedAppKey == slot.app.componentKey
                            var isPointerHeld by remember { mutableStateOf(false) }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .offset { IntOffset(leftPx, topPx) }
                                    .size(width = widthDp, height = cellHeightDp)
                                    .alpha(if (isBeingDragged) 0.25f else 1f),
                            ) {
                                AppIconTile(
                                    app = slot.app,
                                    boundDirections = boundDirs,
                                    showLabel = showAppLabels,
                                    onWallpaper = true,
                                    iconSize = iconSize,
                                    onDownPointer = onIconDownPointer,
                                    onClick = { onAppClick(slot.app) },
                                    onLongClick = {
                                        if (!isHomeEditMode) {
                                            appMenuHost.onOpen(slot.app, AppActionOrigin.HOME, realPage)
                                        }
                                    },
                                    onLongPressHoldChanged = { isPointerHeld = it },
                                    onSwipe = { dir -> onAppSwipe(slot.app, dir, AppActionOrigin.HOME) },
                                    onDragStart = if (isHomeEditMode) {
                                        { bounds, pos -> onAppDragStart(slot, bounds, pos) }
                                    } else {
                                        null
                                    },
                                    onDragMove = if (isHomeEditMode) onAppDragMove else null,
                                    onDragEnd = if (isHomeEditMode) onAppDragEnd else null,
                                    onDragCancel = if (isHomeEditMode) onAppDragCancel else null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                AppContextMenuDropdown(
                                    app = slot.app,
                                    origin = AppActionOrigin.HOME,
                                    sourcePageIndex = realPage,
                                    expanded = !isHomeEditMode &&
                                        isCurrentVirtualPage &&
                                        appMenuHost.isExpandedFor(slot.app, AppActionOrigin.HOME, realPage),
                                    host = appMenuHost,
                                    isPointerHeld = isPointerHeld,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWidgetSlot(
    widget: PlacedWidget,
    isPreferredSlot: Boolean,
    isCurrentVirtualPage: Boolean,
    isHomeEditMode: Boolean,
    isResizing: Boolean,
    isBeingDragged: Boolean,
    widgetHostController: WidgetHostController,
    onResizingBoundsChanged: (Rect) -> Unit,
    onSelectInEditMode: () -> Unit,
    onWidgetDragStart: (Rect, Offset) -> Unit,
    onWidgetDragMove: (Offset) -> Unit,
    onWidgetDragEnd: (Offset) -> Unit,
    onWidgetDragCancel: () -> Unit,
    onTouchDown: (Long?) -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnTouchDown by rememberUpdatedState(onTouchDown)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnSelectInEditMode by rememberUpdatedState(onSelectInEditMode)
    val currentOnWidgetDragStart by rememberUpdatedState(onWidgetDragStart)
    val currentOnWidgetDragMove by rememberUpdatedState(onWidgetDragMove)
    val currentOnWidgetDragEnd by rememberUpdatedState(onWidgetDragEnd)
    val currentOnWidgetDragCancel by rememberUpdatedState(onWidgetDragCancel)
    val currentOnResizingBoundsChanged by rememberUpdatedState(onResizingBoundsChanged)
    val slotCoordsRef = remember { LayoutCoordsRef() }

    val hasProvider = remember(widget.appWidgetId) {
        widgetHostController.getProviderInfo(widget.appWidgetId) != null
    }

    LaunchedEffect(isResizing, isBeingDragged, widget.cellX, widget.cellY, widget.spanX, widget.spanY) {
        if (isResizing && !isBeingDragged) {
            slotCoordsRef.coords?.takeIf { it.isAttached }?.let {
                currentOnResizingBoundsChanged(it.boundsInWindow())
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                slotCoordsRef.coords = coords
                if (isResizing && !isBeingDragged && coords.isAttached) {
                    currentOnResizingBoundsChanged(coords.boundsInWindow())
                }
            }
            .pointerInput(widget.appWidgetId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentOnTouchDown(down.id.value)
                }
            }
            .testTag("home_widget_${widget.appWidgetId}"),
    ) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(48)
        val heightDp = maxHeight.value.toInt().coerceAtLeast(48)
        if (hasProvider) {
            key(widget.appWidgetId) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.FrameLayout(ctx).apply {
                            clipChildren = false
                            clipToPadding = false
                            if ((isPreferredSlot || isCurrentVirtualPage) && !isBeingDragged) {
                                widgetHostController.attachViewToContainer(widget.appWidgetId, this)?.apply {
                                    onTouchDownListener = { currentOnTouchDown(null) }
                                    onLongPressListener = { _ -> currentOnLongClick() }
                                    updateSizeDpIfNeeded(widthDp, heightDp)
                                }
                            }
                        }
                    },
                    update = { container ->
                        if ((isPreferredSlot || isCurrentVirtualPage) && !isBeingDragged) {
                            widgetHostController.attachViewToContainer(widget.appWidgetId, container)?.apply {
                                onTouchDownListener = { currentOnTouchDown(null) }
                                onLongPressListener = { _ -> currentOnLongClick() }
                                updateSizeDpIfNeeded(widthDp, heightDp)
                            }
                        }
                    },
                    onRelease = { container ->
                        container.removeAllViews()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(widget.appWidgetId) {
                        detectTapGestures(
                            onLongPress = { currentOnLongClick() },
                        )
                    },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.widget_unavailable_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isHomeEditMode) {
            // In edit mode, no menus exist: tapping selects for 4-edge resize, and reaching the
            // long-press threshold vibrates, selects the widget, and enters drag immediately.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(widget.appWidgetId) {
                        val touchSlop = viewConfiguration.touchSlop
                        val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var lastPos = down.position
                            var longPressFired = false
                            val deadline = android.os.SystemClock.uptimeMillis() + longPressTimeoutMs
                            while (true) {
                                val remaining = deadline - android.os.SystemClock.uptimeMillis()
                                if (remaining <= 0L) {
                                    longPressFired = true
                                    break
                                }
                                val event = withTimeoutOrNull(remaining) { awaitPointerEvent() }
                                if (event == null) {
                                    longPressFired = true
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                lastPos = change.position
                                if (!change.pressed) {
                                    if ((lastPos - down.position).getDistance() <= touchSlop) {
                                        change.consume()
                                        currentOnSelectInEditMode()
                                    }
                                    break
                                }
                                if ((lastPos - down.position).getDistance() > touchSlop) {
                                    break
                                }
                            }
                            if (longPressFired) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnSelectInEditMode()
                                val coords = slotCoordsRef.coords?.takeIf { it.isAttached }
                                val startBounds = coords?.boundsInWindow() ?: Rect.Zero
                                val anchorWindow = coords?.localToWindow(lastPos) ?: lastPos
                                currentOnWidgetDragStart(startBounds, anchorWindow)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        currentOnWidgetDragCancel()
                                        break
                                    }
                                    change.consume()
                                    val nowWindow = slotCoordsRef.coords?.takeIf { it.isAttached }
                                        ?.localToWindow(change.position) ?: anchorWindow
                                    if (!change.pressed) {
                                        currentOnWidgetDragEnd(nowWindow)
                                        break
                                    }
                                    currentOnWidgetDragMove(nowWindow)
                                }
                            }
                        }
                    },
            )
        }
    }
}
