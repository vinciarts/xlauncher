package com.example.ui.screens

import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.AppActionOrigin
import com.example.model.AppItem
import com.example.model.HomeAppSlot
import com.example.model.HomeGestureAction
import com.example.model.HomeGridEngine
import com.example.model.HomePageCommand
import com.example.model.HomePageCommandType
import com.example.model.PlacedHomeApp
import com.example.model.PlacedWidget
import com.example.model.SwipeDirection
import com.example.ui.components.AppContextMenuDropdown
import com.example.ui.components.AppContextMenuHost
import com.example.ui.components.AppIconImage
import com.example.ui.components.AppIconTile
import com.example.ui.components.HomeEmptyMenuPopup
import com.example.ui.components.PageIndicatorDots
import com.example.ui.components.RESIZE_HANDLE_MARGIN_DP
import com.example.ui.components.WidgetContextMenuDropdown
import com.example.ui.components.WidgetContextMenuHost
import com.example.ui.components.WidgetResizeFrameOverlay
import com.example.widget.WidgetHostController
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private class LayoutCoordsRef(var coords: LayoutCoordinates? = null)

private data class PageGridMetrics(
    val gridBounds: Rect = Rect.Zero,
    val cellWidthPx: Float = 160f,
    val cellHeightPx: Float = 160f,
    val colStepPx: Float = 166f,
    val rowStepPx: Float = 168f,
    val colGapPx: Float = 6f,
    val rowGapPx: Float = 8f,
)

private sealed class DragItem {
    data class App(val slot: HomeAppSlot) : DragItem()
    data class Widget(val widget: PlacedWidget) : DragItem()

    val spanX: Int
        get() = when (this) {
            is App -> 1
            is Widget -> widget.spanX
        }

    val spanY: Int
        get() = when (this) {
            is App -> 1
            is Widget -> widget.spanY
        }

    val sourcePage: Int
        get() = when (this) {
            is App -> slot.pageIndex
            is Widget -> widget.pageIndex
        }
}

private data class HomeDragSession(
    val item: DragItem,
    val pointerWindowPos: Offset,
    val grabOffsetInItem: Offset,
    val itemSizePx: Size,
    val targetPage: Int,
    val targetCellX: Int,
    val targetCellY: Int,
    val canDrop: Boolean,
    val isSwapTarget: Boolean,
)

private const val CELL_COL_GAP_DP = 6
private const val CELL_ROW_GAP_DP = 8
private const val GRID_HORIZONTAL_PAD_DP = 16
private const val GRID_TOP_PAD_DP = 12
private const val GRID_BOTTOM_PAD_DP = 8
private const val DRAG_EDGE_INNER_DP = 32
private const val DRAG_EDGE_FLIP_DELAY_MS = 450L
private const val DRAG_NEW_FRONT_PAGE = -1

@Composable
fun HomeScreen(
    isLoading: Boolean,
    homePageApps: ImmutableList<ImmutableList<HomeAppSlot>>,
    homePageWidgets: ImmutableList<ImmutableList<PlacedWidget>>,
    dockApps: ImmutableList<AppItem>,
    appGestures: ImmutableMap<String, ImmutableMap<SwipeDirection, String>>,
    homeGestures: ImmutableMap<SwipeDirection, HomeGestureAction>,
    homeGridColumns: Int,
    homeLoopPaging: Boolean,
    maxHomePages: Int,
    currentHomePage: Int,
    homePageCommand: HomePageCommand?,
    resizingWidgetId: Int?,
    widgetHostController: WidgetHostController,
    appMenuHost: AppContextMenuHost,
    widgetMenuHost: WidgetContextMenuHost,
    homeEmptyMenuAnchor: Offset?,
    onHomePageChanged: (Int) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppSwipe: (AppItem, SwipeDirection, AppActionOrigin) -> Unit,
    onResizeWidget: (Int, Int, Int, Int, Int) -> Unit,
    onDismissResizeWidget: () -> Unit,
    onMoveHomeAppToCell: (String, Int, Int, Int, Int) -> Unit,
    onMoveWidgetToCell: (Int, Int, Int, Int) -> Unit,
    onMoveHomeAppToNewPage: (String, Int, Boolean, Int, Int) -> Unit,
    onMoveWidgetToNewPage: (Int, Boolean, Int, Int) -> Unit,
    onHomeSwipe: (SwipeDirection) -> Unit,
    onEmptyLongPress: (Offset) -> Unit,
    onEmptyMenuAddWidget: () -> Unit,
    onEmptyMenuOpenWallpaper: () -> Unit,
    onEmptyMenuOpenSettings: () -> Unit,
    onEmptyMenuAddPageAtStart: () -> Unit,
    onEmptyMenuAddPageAtEnd: () -> Unit,
    onEmptyMenuDeleteCurrentPage: () -> Unit,
    onDismissEmptyMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val currentOnEmptyLongPress by rememberUpdatedState(onEmptyLongPress)
    val currentOnHomeSwipe by rememberUpdatedState(onHomeSwipe)
    val currentHomeGestures by rememberUpdatedState(homeGestures)
    val currentOnDismissResizeWidget by rememberUpdatedState(onDismissResizeWidget)
    val currentHomePageApps by rememberUpdatedState(homePageApps)
    val currentHomePageWidgets by rememberUpdatedState(homePageWidgets)
    val currentCols by rememberUpdatedState(homeGridColumns)
    val currentOnMoveHomeAppToCell by rememberUpdatedState(onMoveHomeAppToCell)
    val currentOnMoveWidgetToCell by rememberUpdatedState(onMoveWidgetToCell)
    val currentOnMoveHomeAppToNewPage by rememberUpdatedState(onMoveHomeAppToNewPage)
    val currentOnMoveWidgetToNewPage by rememberUpdatedState(onMoveWidgetToNewPage)

    var dragSession by remember { mutableStateOf<HomeDragSession?>(null) }
    val isResizingWidget by rememberUpdatedState(resizingWidgetId != null)
    val isDraggingItem by rememberUpdatedState(dragSession != null)

    BackHandler(enabled = resizingWidgetId != null || dragSession != null) {
        if (dragSession != null) {
            dragSession = null
        } else {
            currentOnDismissResizeWidget()
        }
    }

    val pageCount = homePageApps.size.coerceAtLeast(1)
    val loopEnabled = homeLoopPaging && pageCount > 1

    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val pagingTouchSlopPx = viewConfig.scaledPagingTouchSlop.toFloat()
    val minFlingVelocityPx = viewConfig.scaledMinimumFlingVelocity.toFloat()

    // Plain refs for gesture coordination without triggering Compose recompositions on touch
    var lastIconPointerId by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var lastIconBoundDirs by remember { mutableStateOf<Set<SwipeDirection>>(emptySet()) }
    var lastInteractiveTouchDownMs by remember { mutableLongStateOf(0L) }
    val rootCoordsRef = remember { LayoutCoordsRef() }
    var resizingWidgetBounds by remember(resizingWidgetId) { mutableStateOf<Rect?>(null) }
    var pageGridMetrics by remember { mutableStateOf(PageGridMetrics()) }
    var displayedRealPage by remember { mutableIntStateOf(currentHomePage.coerceIn(0, pageCount - 1)) }

    val currentPageGridMetrics by rememberUpdatedState(pageGridMetrics)
    val currentDisplayedRealPage by rememberUpdatedState(displayedRealPage)
    val currentPageCount by rememberUpdatedState(pageCount)

    fun isTemporaryNewPage(targetPage: Int): Boolean =
        targetPage == DRAG_NEW_FRONT_PAGE || targetPage >= currentPageCount

    fun evaluateDropTarget(
        item: DragItem,
        pointerWindowPos: Offset,
        grabOffsetInItem: Offset,
        pageIndex: Int,
        metrics: PageGridMetrics,
    ): HomeDragSession {
        val cols = currentCols.coerceIn(4, 5)
        val rows = HomeGridEngine.HOME_GRID_ROWS
        val sx = item.spanX.coerceIn(1, cols)
        val sy = item.spanY.coerceIn(1, rows)
        val itemTopLeft = pointerWindowPos - grabOffsetInItem
        val relX = itemTopLeft.x - metrics.gridBounds.left
        val relY = itemTopLeft.y - metrics.gridBounds.top
        val stepX = metrics.colStepPx.coerceAtLeast(36f)
        val stepY = metrics.rowStepPx.coerceAtLeast(36f)
        val targetX = (relX / stepX).roundToInt().coerceIn(0, (cols - sx).coerceAtLeast(0))
        val targetY = (relY / stepY).roundToInt().coerceIn(0, (rows - sy).coerceAtLeast(0))

        val isNewEmptyPage = isTemporaryNewPage(pageIndex)
        val pageWidgets = if (isNewEmptyPage) emptyList() else currentHomePageWidgets.getOrElse(pageIndex) { emptyList() }
        val pageApps = if (isNewEmptyPage) emptyList() else currentHomePageApps.getOrElse(pageIndex) { emptyList() }

        val canDrop: Boolean
        val isSwap: Boolean
        when (item) {
            is DragItem.App -> {
                val widgetOcc = HomeGridEngine.buildOccupancy(pageWidgets, emptyList(), cols)
                val blockedByWidget = widgetOcc[targetY][targetX]
                canDrop = !blockedByWidget
                isSwap = !isNewEmptyPage && canDrop && pageApps.any {
                    it.cellX == targetX && it.cellY == targetY &&
                        !(it.pageIndex == item.slot.pageIndex && it.app.componentKey == item.slot.app.componentKey)
                }
            }

            is DragItem.Widget -> {
                val occ = HomeGridEngine.buildOccupancy(
                    widgets = pageWidgets,
                    apps = pageApps.map { PlacedHomeApp(it.app.componentKey, it.pageIndex, it.cellX, it.cellY) },
                    cols = cols,
                    ignoreWidgetId = item.widget.appWidgetId,
                )
                canDrop = HomeGridEngine.canPlace(occ, targetX, targetY, sx, sy, cols)
                isSwap = false
            }
        }

        val itemWidthPx = sx * metrics.cellWidthPx + (sx - 1) * metrics.colGapPx
        val itemHeightPx = sy * metrics.cellHeightPx + (sy - 1) * metrics.rowGapPx

        return HomeDragSession(
            item = item,
            pointerWindowPos = pointerWindowPos,
            grabOffsetInItem = grabOffsetInItem,
            itemSizePx = Size(itemWidthPx, itemHeightPx),
            targetPage = pageIndex,
            targetCellX = targetX,
            targetCellY = targetY,
            canDrop = canDrop,
            isSwapTarget = isSwap,
        )
    }

    fun startDrag(
        item: DragItem,
        startBounds: Rect,
        pointerWindowPos: Offset,
        keepResizeMode: Boolean = false,
    ) {
        if (!keepResizeMode && isResizingWidget) currentOnDismissResizeWidget()
        val grabOffset = if (startBounds != Rect.Zero) {
            Offset(
                x = (pointerWindowPos.x - startBounds.left).coerceIn(0f, startBounds.width.coerceAtLeast(1f)),
                y = (pointerWindowPos.y - startBounds.top).coerceIn(0f, startBounds.height.coerceAtLeast(1f)),
            )
        } else {
            Offset.Zero
        }
        dragSession = evaluateDropTarget(
            item = item,
            pointerWindowPos = pointerWindowPos,
            grabOffsetInItem = grabOffset,
            pageIndex = currentDisplayedRealPage,
            metrics = currentPageGridMetrics,
        )
    }

    fun updateDrag(pointerWindowPos: Offset) {
        val prev = dragSession ?: return
        val activePage = if (isTemporaryNewPage(prev.targetPage)) prev.targetPage else currentDisplayedRealPage
        val next = evaluateDropTarget(
            item = prev.item,
            pointerWindowPos = pointerWindowPos,
            grabOffsetInItem = prev.grabOffsetInItem,
            pageIndex = activePage,
            metrics = currentPageGridMetrics,
        )
        if (next.targetPage != prev.targetPage ||
            next.targetCellX != prev.targetCellX ||
            next.targetCellY != prev.targetCellY
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        dragSession = next
    }

    fun finishDrag(pointerWindowPos: Offset) {
        val prev = dragSession ?: return
        val activePage = if (isTemporaryNewPage(prev.targetPage)) prev.targetPage else currentDisplayedRealPage
        val finalSession = evaluateDropTarget(
            item = prev.item,
            pointerWindowPos = pointerWindowPos,
            grabOffsetInItem = prev.grabOffsetInItem,
            pageIndex = activePage,
            metrics = currentPageGridMetrics,
        )
        dragSession = null
        if (finalSession.canDrop) {
            val isNewFront = finalSession.targetPage == DRAG_NEW_FRONT_PAGE
            val isNewEnd = finalSession.targetPage >= currentPageCount
            when (val dragged = finalSession.item) {
                is DragItem.App -> {
                    if (isNewFront || isNewEnd) {
                        currentOnMoveHomeAppToNewPage(
                            dragged.slot.app.componentKey,
                            dragged.slot.pageIndex,
                            isNewFront,
                            finalSession.targetCellX,
                            finalSession.targetCellY,
                        )
                    } else {
                        currentOnMoveHomeAppToCell(
                            dragged.slot.app.componentKey,
                            dragged.slot.pageIndex,
                            finalSession.targetPage,
                            finalSession.targetCellX,
                            finalSession.targetCellY,
                        )
                    }
                }

                is DragItem.Widget -> {
                    if (isNewFront || isNewEnd) {
                        if (isResizingWidget) currentOnDismissResizeWidget()
                        currentOnMoveWidgetToNewPage(
                            dragged.widget.appWidgetId,
                            isNewFront,
                            finalSession.targetCellX,
                            finalSession.targetCellY,
                        )
                    } else {
                        if (isResizingWidget && finalSession.targetPage != dragged.widget.pageIndex) {
                            currentOnDismissResizeWidget()
                        }
                        currentOnMoveWidgetToCell(
                            dragged.widget.appWidgetId,
                            finalSession.targetPage,
                            finalSession.targetCellX,
                            finalSession.targetCellY,
                        )
                    }
                }
            }
        }
    }

    // Re-evaluate drop cell immediately when edge-hover flips to another existing Home page
    LaunchedEffect(displayedRealPage) {
        val prev = dragSession ?: return@LaunchedEffect
        if (!isTemporaryNewPage(prev.targetPage) && prev.targetPage != displayedRealPage) {
            dragSession = evaluateDropTarget(
                item = prev.item,
                pointerWindowPos = prev.pointerWindowPos,
                grabOffsetInItem = prev.grabOffsetInItem,
                pageIndex = displayedRealPage,
                metrics = currentPageGridMetrics,
            )
        }
    }

    val resizingWidget = remember(resizingWidgetId, homePageWidgets) {
        if (resizingWidgetId == null) null
        else homePageWidgets.asSequence().flatten().firstOrNull { it.appWidgetId == resizingWidgetId }
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords -> rootCoordsRef.coords = coords }
            // 4-direction Home desktop gesture detector using Android ViewConfiguration + VelocityTracker
            .pointerInput(pagingTouchSlopPx, minFlingVelocityPx) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (isResizingWidget || isDraggingItem) return@awaitEachGesture
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var claimedDir: SwipeDirection? = null

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (isResizingWidget || isDraggingItem) break
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val totalDrag = change.position - down.position
                        val absX = abs(totalDrag.x)
                        val absY = abs(totalDrag.y)

                        if (!change.pressed) {
                            val dir = claimedDir
                            if (dir != null) {
                                change.consume()
                                val velocity = velocityTracker.calculateVelocity()
                                val primaryDist = if (dir == SwipeDirection.UP || dir == SwipeDirection.DOWN) absY else absX
                                val directionalVelocity = when (dir) {
                                    SwipeDirection.UP -> -velocity.y
                                    SwipeDirection.DOWN -> velocity.y
                                    SwipeDirection.LEFT -> -velocity.x
                                    SwipeDirection.RIGHT -> velocity.x
                                }
                                if (primaryDist >= pagingTouchSlopPx || directionalVelocity >= minFlingVelocityPx) {
                                    currentOnHomeSwipe(dir)
                                }
                            }
                            break
                        }

                        if (claimedDir == null && totalDrag.getDistance() > touchSlop) {
                            val dir = if (absY >= absX) {
                                if (totalDrag.y < 0f) SwipeDirection.UP else SwipeDirection.DOWN
                            } else {
                                if (totalDrag.x < 0f) SwipeDirection.LEFT else SwipeDirection.RIGHT
                            }
                            val iconOwnsDir = (down.id.value == lastIconPointerId) && (dir in lastIconBoundDirs)
                            val homeConfigured = currentHomeGestures[dir].let {
                                it != null && it != HomeGestureAction.None
                            }
                            if (homeConfigured && !iconOwnsDir) {
                                claimedDir = dir
                            } else {
                                break
                            }
                        }

                        if (claimedDir != null) {
                            change.consume()
                        }
                    }
                }
            }
            // Empty-space long-press using official Compose detectTapGestures
            .pointerInput(Unit) {
                var pressStartedOnChild = false
                detectTapGestures(
                    onPress = {
                        pressStartedOnChild = (System.currentTimeMillis() - lastInteractiveTouchDownMs) < 100L
                        tryAwaitRelease()
                    },
                    onLongPress = { localOffset ->
                        if (!isResizingWidget && !isDraggingItem && !pressStartedOnChild) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnEmptyLongPress(localOffset)
                        }
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Pure Home Pager workspace directly over the untouched system wallpaper
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                key(pageCount, loopEnabled) {
                    val basePage = remember(pageCount, loopEnabled) {
                        if (loopEnabled) {
                            val mid = Int.MAX_VALUE / 2
                            mid - (mid % pageCount) + currentHomePage.coerceIn(0, pageCount - 1)
                        } else {
                            currentHomePage.coerceIn(0, pageCount - 1)
                        }
                    }

                    val pagerState = rememberPagerState(
                        initialPage = basePage,
                        pageCount = { if (loopEnabled) Int.MAX_VALUE else pageCount },
                    )

                    // Track live displayed page for accurate drag-and-drop & grid measurement
                    LaunchedEffect(pagerState, pageCount) {
                        snapshotFlow {
                            ((pagerState.currentPage % pageCount) + pageCount) % pageCount
                        }
                            .distinctUntilChanged()
                            .collect { realPage -> displayedRealPage = realPage }
                    }

                    // Only notify ViewModel once the fling settles (zero state updates mid-swipe)
                    LaunchedEffect(pagerState, pageCount) {
                        snapshotFlow {
                            ((pagerState.settledPage % pageCount) + pageCount) % pageCount
                        }
                            .distinctUntilChanged()
                            .collect { realPage -> onHomePageChanged(realPage) }
                    }

                    LaunchedEffect(homePageCommand?.id) {
                        val cmd = homePageCommand ?: return@LaunchedEffect
                        val target = when (cmd.type) {
                            HomePageCommandType.FIRST_PAGE -> {
                                if (loopEnabled) {
                                    val current = pagerState.currentPage
                                    val real = ((current % pageCount) + pageCount) % pageCount
                                    current - real
                                } else {
                                    0
                                }
                            }
                            HomePageCommandType.PREV_PAGE -> {
                                if (loopEnabled) {
                                    pagerState.currentPage - 1
                                } else {
                                    (pagerState.currentPage - 1).coerceAtLeast(0)
                                }
                            }
                            HomePageCommandType.NEXT_PAGE -> {
                                if (loopEnabled) {
                                    pagerState.currentPage + 1
                                } else {
                                    (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                                }
                            }
                        }
                        pagerState.animateScrollToPage(target)
                    }

                    // AOSP edge-hover paging while dragging:
                    // uses gridBounds (works on both phones and wide screens) and never loops at boundaries;
                    // opens a temporary new front/end page at page 0 / last page instead.
                    val edgeInnerPx = with(density) { DRAG_EDGE_INNER_DP.dp.toPx() }
                    val dragEdgeDir = run {
                        val session = dragSession
                        val gridBounds = pageGridMetrics.gridBounds
                        if (session == null || gridBounds == Rect.Zero) {
                            0
                        } else when {
                            session.pointerWindowPos.x <= gridBounds.left + edgeInnerPx -> -1
                            session.pointerWindowPos.x >= gridBounds.right - edgeInnerPx -> 1
                            else -> 0
                        }
                    }

                    LaunchedEffect(dragEdgeDir, pageCount, maxHomePages) {
                        if (dragEdgeDir == 0) return@LaunchedEffect
                        while (true) {
                            delay(DRAG_EDGE_FLIP_DELAY_MS)
                            val session = dragSession ?: break
                            val realCur = ((pagerState.currentPage % pageCount) + pageCount) % pageCount

                            if (dragEdgeDir < 0) {
                                when {
                                    // Currently on temporary new end page -> return to last existing page
                                    session.targetPage >= pageCount -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        dragSession = evaluateDropTarget(
                                            item = session.item,
                                            pointerWindowPos = session.pointerWindowPos,
                                            grabOffsetInItem = session.grabOffsetInItem,
                                            pageIndex = realCur,
                                            metrics = currentPageGridMetrics,
                                        )
                                    }
                                    // Existing page > 0 -> flip to previous existing page (no loop)
                                    session.targetPage != DRAG_NEW_FRONT_PAGE && realCur > 0 -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                    // At first page (0) -> open temporary new front page if below maxHomePages
                                    session.targetPage != DRAG_NEW_FRONT_PAGE && realCur == 0 && pageCount < maxHomePages -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        dragSession = evaluateDropTarget(
                                            item = session.item,
                                            pointerWindowPos = session.pointerWindowPos,
                                            grabOffsetInItem = session.grabOffsetInItem,
                                            pageIndex = DRAG_NEW_FRONT_PAGE,
                                            metrics = currentPageGridMetrics,
                                        )
                                        break
                                    }
                                    else -> break
                                }
                            } else {
                                when {
                                    // Currently on temporary new front page -> return to first existing page (0)
                                    session.targetPage == DRAG_NEW_FRONT_PAGE -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        dragSession = evaluateDropTarget(
                                            item = session.item,
                                            pointerWindowPos = session.pointerWindowPos,
                                            grabOffsetInItem = session.grabOffsetInItem,
                                            pageIndex = realCur,
                                            metrics = currentPageGridMetrics,
                                        )
                                    }
                                    // Existing page < last -> flip to next existing page (no loop)
                                    session.targetPage < pageCount && realCur < pageCount - 1 -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                    // At last page -> open temporary new end page if below maxHomePages
                                    session.targetPage < pageCount && realCur == pageCount - 1 && pageCount < maxHomePages -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        dragSession = evaluateDropTarget(
                                            item = session.item,
                                            pointerWindowPos = session.pointerWindowPos,
                                            grabOffsetInItem = session.grabOffsetInItem,
                                            pageIndex = pageCount,
                                            metrics = currentPageGridMetrics,
                                        )
                                        break
                                    }
                                    else -> break
                                }
                            }
                        }
                    }

                    val isDraggingOnNewPage = dragSession?.let { isTemporaryNewPage(it.targetPage) } == true

                    val pagerViewportCoordsRef = remember { LayoutCoordsRef() }
                    fun updateStationaryGridMetrics(coords: LayoutCoordinates) {
                        if (!coords.isAttached) return
                        val viewport = coords.boundsInWindow()
                        val padH = with(density) { GRID_HORIZONTAL_PAD_DP.dp.toPx() }
                        val padTop = with(density) { GRID_TOP_PAD_DP.dp.toPx() }
                        val padBottom = with(density) { GRID_BOTTOM_PAD_DP.dp.toPx() }
                        val gridBounds = Rect(
                            left = viewport.left + padH,
                            top = viewport.top + padTop,
                            right = (viewport.right - padH).coerceAtLeast(viewport.left + padH + 48f),
                            bottom = (viewport.bottom - padBottom).coerceAtLeast(viewport.top + padTop + 48f),
                        )
                        val colGapPx = with(density) { CELL_COL_GAP_DP.dp.toPx() }
                        val rowGapPx = with(density) { CELL_ROW_GAP_DP.dp.toPx() }
                        val safeCols = currentCols.coerceIn(4, 5)
                        val rows = HomeGridEngine.HOME_GRID_ROWS
                        val cellWidthPx = ((gridBounds.width - colGapPx * (safeCols - 1)) / safeCols).coerceAtLeast(48f)
                        val cellHeightPx = ((gridBounds.height - rowGapPx * (rows - 1)) / rows).coerceAtLeast(48f)
                        val next = PageGridMetrics(
                            gridBounds = gridBounds,
                            cellWidthPx = cellWidthPx,
                            cellHeightPx = cellHeightPx,
                            colStepPx = cellWidthPx + colGapPx,
                            rowStepPx = cellHeightPx + rowGapPx,
                            colGapPx = colGapPx,
                            rowGapPx = rowGapPx,
                        )
                        if (next != pageGridMetrics) {
                            pageGridMetrics = next
                        }
                    }

                    LaunchedEffect(homeGridColumns) {
                        pagerViewportCoordsRef.coords?.let { updateStationaryGridMetrics(it) }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                pagerViewportCoordsRef.coords = coords
                                updateStationaryGridMetrics(coords)
                            },
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = resizingWidgetId == null && dragSession == null,
                            beyondViewportPageCount = if (dragSession != null) (pageCount - 1).coerceAtLeast(1) else 1,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = if (isDraggingOnNewPage) 0f else 1f
                                }
                                .testTag("home_pager"),
                        ) { virtualPage ->
                            val realPage = ((virtualPage % pageCount) + pageCount) % pageCount
                            val pageApps = homePageApps.getOrElse(realPage) { persistentListOf() }
                            val pageWidgets = homePageWidgets.getOrElse(realPage) { persistentListOf() }

                            // In an infinite-loop pager (for ANY pageCount >= 2), multiple virtual pages can share the same
                            // realPage simultaneously (during swipes, beyondViewport preloading, or drags).
                            // Half-open interval [-pageCount/2, +pageCount/2) guarantees EXACTLY ONE virtual copy of each
                            // realPage owns the single AppWidgetHostView at all times — zero stealing when wrapping last->first.
                            val isPreferredWidgetSlot by remember(virtualPage, loopEnabled, pageCount, pagerState) {
                                derivedStateOf {
                                    if (!loopEnabled) {
                                        true
                                    } else {
                                        // Subtract Int indices FIRST before converting to Float:
                                        // virtualPage is near Int.MAX_VALUE / 2 (~1e9), where 32-bit Float ULP is 128.0!
                                        val delta = (virtualPage - pagerState.currentPage).toFloat() -
                                            pagerState.currentPageOffsetFraction
                                        val half = pageCount / 2f
                                        delta >= -half && delta < half
                                    }
                                }
                            }
                            val isCurrentVirtualPage by remember(virtualPage, pagerState) {
                                derivedStateOf { virtualPage == pagerState.currentPage }
                            }

                            val draggedItem = dragSession?.item
                            val draggedAppKey = (draggedItem as? DragItem.App)?.takeIf { it.slot.pageIndex == realPage }?.slot?.app?.componentKey
                            val draggedWidgetId = (draggedItem as? DragItem.Widget)?.widget?.appWidgetId

                            HomePageContent(
                                isLoading = isLoading,
                                realPage = realPage,
                                pageApps = pageApps,
                                pageWidgets = pageWidgets,
                                isPreferredWidgetSlot = isPreferredWidgetSlot,
                                isCurrentVirtualPage = isCurrentVirtualPage,
                                resizingWidgetId = resizingWidgetId,
                                draggedAppKey = draggedAppKey,
                                draggedWidgetId = draggedWidgetId,
                                cols = homeGridColumns,
                                appGestures = appGestures,
                                widgetHostController = widgetHostController,
                                appMenuHost = appMenuHost,
                                widgetMenuHost = widgetMenuHost,
                                onResizingWidgetBoundsChanged = { bounds ->
                                    resizingWidgetBounds = bounds
                                },
                                onIconDownPointer = { id, dirs ->
                                    lastIconPointerId = id
                                    lastIconBoundDirs = dirs
                                    lastInteractiveTouchDownMs = System.currentTimeMillis()
                                },
                                onWidgetTouchDown = { pointerId ->
                                    if (pointerId != null) {
                                        // Yield vertical UP/DOWN Home gestures to scrollable widgets while preserving horizontal paging
                                        lastIconPointerId = pointerId
                                        lastIconBoundDirs = setOf(SwipeDirection.UP, SwipeDirection.DOWN)
                                    }
                                    lastInteractiveTouchDownMs = System.currentTimeMillis()
                                },
                                onAppClick = onAppClick,
                                onAppSwipe = onAppSwipe,
                                onAppDragStart = { slot, bounds, pos ->
                                    appMenuHost.onDismiss()
                                    startDrag(DragItem.App(slot), bounds, pos)
                                },
                                onAppDragMove = { pos -> updateDrag(pos) },
                                onAppDragEnd = { pos -> finishDrag(pos) },
                                onAppDragCancel = { dragSession = null },
                            )
                        }

                        if (isDraggingOnNewPage) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    text = stringResource(R.string.drag_new_page_hint),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.82f),
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.72f),
                                            offset = Offset(0f, 1f),
                                            blurRadius = 4f,
                                        ),
                                    ),
                                )
                            }
                        }
                    }

                    val indicatorOverrideCount = if (isDraggingOnNewPage) pageCount + 1 else null
                    val indicatorOverrideCurrent = when {
                        dragSession?.targetPage == DRAG_NEW_FRONT_PAGE -> 0
                        dragSession != null && dragSession!!.targetPage >= pageCount -> pageCount
                        else -> null
                    }

                    HomePageIndicatorBar(
                        pagerState = pagerState,
                        pageCount = pageCount,
                        loopEnabled = loopEnabled,
                        overridePageCount = indicatorOverrideCount,
                        overrideCurrentPage = indicatorOverrideCurrent,
                    )
                }
            }

            // Native Dock directly over wallpaper
            if (dockApps.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 12.dp)
                        .testTag("home_dock"),
                ) {
                    dockApps.forEach { app ->
                        key(app.componentKey) {
                            val boundDirs = appGestures[app.componentKey]?.keys ?: persistentSetOf()
                            Box(modifier = Modifier.weight(1f)) {
                                AppIconTile(
                                    app = app,
                                    boundDirections = boundDirs,
                                    showLabel = false,
                                    onWallpaper = true,
                                    iconSize = 50.dp,
                                    onDownPointer = { id, dirs ->
                                        lastIconPointerId = id
                                        lastIconBoundDirs = dirs
                                        lastInteractiveTouchDownMs = System.currentTimeMillis()
                                    },
                                    onClick = { onAppClick(app) },
                                    onLongClick = {
                                        appMenuHost.onOpen(app, AppActionOrigin.DOCK, 0)
                                    },
                                    onSwipe = { dir -> onAppSwipe(app, dir, AppActionOrigin.DOCK) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AppContextMenuDropdown(
                                    app = app,
                                    origin = AppActionOrigin.DOCK,
                                    sourcePageIndex = 0,
                                    expanded = appMenuHost.isExpandedFor(app, AppActionOrigin.DOCK, 0),
                                    host = appMenuHost,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top-level AOSP DragLayer overlay for live drag target highlight + floating drag preview
        val activeDrag = dragSession
        if (activeDrag != null && pageGridMetrics.gridBounds != Rect.Zero) {
            val rootPos = rootCoordsRef.coords?.takeIf { it.isAttached }?.positionInWindow() ?: Offset.Zero
            val edgeInnerPx = with(density) { DRAG_EDGE_INNER_DP.dp.toPx() }
            val activeEdgeIndicatorDir = when {
                activeDrag.pointerWindowPos.x <= pageGridMetrics.gridBounds.left + edgeInnerPx &&
                    (activeDrag.targetPage >= pageCount || displayedRealPage > 0 || (activeDrag.targetPage != DRAG_NEW_FRONT_PAGE && pageCount < maxHomePages)) -> -1
                activeDrag.pointerWindowPos.x >= pageGridMetrics.gridBounds.right - edgeInnerPx &&
                    (activeDrag.targetPage == DRAG_NEW_FRONT_PAGE || displayedRealPage < pageCount - 1 || (activeDrag.targetPage < pageCount && pageCount < maxHomePages)) -> 1
                else -> 0
            }
            HomeDragOverlay(
                session = activeDrag,
                gridMetrics = pageGridMetrics,
                rootPos = rootPos,
                cols = homeGridColumns,
                edgeIndicatorDir = activeEdgeIndicatorDir,
            )
        }

        // Top-level AOSP DragLayer-equivalent resize frame overlay (zero handle clipping & clean outside-tap dismiss)
        // Kept mounted (hidden via alpha) during interior drag so the active pointer stream is never cancelled.
        val activeWidget = resizingWidget
        val hasGridMetrics = pageGridMetrics.gridBounds != Rect.Zero
        val activeBounds = resizingWidgetBounds
        if (activeWidget != null && (hasGridMetrics || activeBounds != null)) {
            val rootPos = rootCoordsRef.coords?.takeIf { it.isAttached }?.positionInWindow() ?: Offset.Zero
            val marginPx = with(density) { RESIZE_HANDLE_MARGIN_DP.dp.toPx() }
            val widgetRectInRoot = if (hasGridMetrics) {
                val safeCols = homeGridColumns.coerceIn(4, 5)
                val rows = HomeGridEngine.HOME_GRID_ROWS
                val sx = activeWidget.spanX.coerceIn(1, safeCols)
                val sy = activeWidget.spanY.coerceIn(1, rows)
                val cx = activeWidget.cellX.coerceIn(0, (safeCols - sx).coerceAtLeast(0))
                val cy = activeWidget.cellY.coerceIn(0, (rows - sy).coerceAtLeast(0))
                val left = pageGridMetrics.gridBounds.left - rootPos.x + cx * pageGridMetrics.colStepPx
                val top = pageGridMetrics.gridBounds.top - rootPos.y + cy * pageGridMetrics.rowStepPx
                val w = sx * pageGridMetrics.cellWidthPx + (sx - 1) * pageGridMetrics.colGapPx
                val h = sy * pageGridMetrics.cellHeightPx + (sy - 1) * pageGridMetrics.rowGapPx
                Rect(left = left, top = top, right = left + w, bottom = top + h)
            } else {
                val b = activeBounds!!
                Rect(
                    left = b.left - rootPos.x,
                    top = b.top - rootPos.y,
                    right = b.right - rootPos.x,
                    bottom = b.bottom - rootPos.y,
                )
            }
            val frameLeftPx = (widgetRectInRoot.left - marginPx).roundToInt()
            val frameTopPx = (widgetRectInRoot.top - marginPx).roundToInt()
            val frameWidthPx = (widgetRectInRoot.width + marginPx * 2f).roundToInt().coerceAtLeast(1)
            val frameHeightPx = (widgetRectInRoot.height + marginPx * 2f).roundToInt().coerceAtLeast(1)
            val resizeSpec = remember(activeWidget.appWidgetId, homeGridColumns) {
                widgetHostController.getResizeSpec(activeWidget.appWidgetId, homeGridColumns)
            }
            val resizePageOccupancy = remember(activeWidget, homePageWidgets, homePageApps, homeGridColumns) {
                val pageW = homePageWidgets.getOrElse(activeWidget.pageIndex) { emptyList() }
                val pageA = homePageApps.getOrElse(activeWidget.pageIndex) { emptyList() }.map {
                    PlacedHomeApp(it.app.componentKey, it.pageIndex, it.cellX, it.cellY)
                }
                HomeGridEngine.buildOccupancy(
                    widgets = pageW,
                    apps = pageA,
                    cols = homeGridColumns,
                    ignoreWidgetId = activeWidget.appWidgetId,
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Outside-tap dismiss layer behind the resize frame (only handles unconsumed outside taps)
                if (dragSession == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activeWidget.appWidgetId) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    down.consume()
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        ch.consume()
                                        if (!ch.pressed) break
                                    }
                                    currentOnDismissResizeWidget()
                                }
                            },
                    )
                }
                // Measure with unbounded exact Constraints.fixed so full-width/full-height widgets
                // (whose outer handle margin exceeds screen width) are never clamped or shifted left.
                WidgetResizeFrameOverlay(
                    widget = activeWidget,
                    spec = resizeSpec,
                    cols = homeGridColumns,
                    rows = HomeGridEngine.HOME_GRID_ROWS,
                    colStepPx = pageGridMetrics.colStepPx,
                    rowStepPx = pageGridMetrics.rowStepPx,
                    isDraggingInterior = dragSession != null,
                    canPlaceRect = { cx, cy, sx, sy ->
                        HomeGridEngine.canPlace(resizePageOccupancy, cx, cy, sx, sy, homeGridColumns)
                    },
                    onUpdateRect = { cx, cy, sx, sy ->
                        onResizeWidget(activeWidget.appWidgetId, cx, cy, sx, sy)
                    },
                    onTapToDismiss = currentOnDismissResizeWidget,
                    onInteriorDragStart = { bounds, pos ->
                        startDrag(DragItem.Widget(activeWidget), bounds, pos, keepResizeMode = true)
                    },
                    onInteriorDragMove = { pos -> updateDrag(pos) },
                    onInteriorDragEnd = { pos -> finishDrag(pos) },
                    onInteriorDragCancel = { dragSession = null },
                    modifier = Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            Constraints.fixed(frameWidthPx, frameHeightPx)
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(frameLeftPx, frameTopPx)
                        }
                    },
                )
            }
        }

        // Empty-space long-press menu anchored directly at the local tap coordinate
        HomeEmptyMenuPopup(
            anchorOffset = homeEmptyMenuAnchor,
            canAddPage = pageCount < maxHomePages,
            canDeleteCurrentPage = pageCount > 1,
            onAddWidget = onEmptyMenuAddWidget,
            onOpenWallpaper = onEmptyMenuOpenWallpaper,
            onOpenSettings = onEmptyMenuOpenSettings,
            onAddPageAtStart = onEmptyMenuAddPageAtStart,
            onAddPageAtEnd = onEmptyMenuAddPageAtEnd,
            onDeleteCurrentPage = onEmptyMenuDeleteCurrentPage,
            onDismiss = onDismissEmptyMenu,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun HomeDragOverlay(
    session: HomeDragSession,
    gridMetrics: PageGridMetrics,
    rootPos: Offset,
    cols: Int,
    edgeIndicatorDir: Int,
) {
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val highlightColor = when {
        !session.canDrop -> errorColor
        session.isSwapTarget -> secondaryColor
        else -> primaryColor
    }

    val sx = session.item.spanX.coerceIn(1, cols)
    val sy = session.item.spanY.coerceIn(1, HomeGridEngine.HOME_GRID_ROWS)

    // Target cell highlight snapped directly to the 2D CellLayout grid + edge-flip cue
    Canvas(modifier = Modifier.fillMaxSize()) {
        val targetLeft = gridMetrics.gridBounds.left - rootPos.x + session.targetCellX * gridMetrics.colStepPx
        val targetTop = gridMetrics.gridBounds.top - rootPos.y + session.targetCellY * gridMetrics.rowStepPx
        val targetW = sx * gridMetrics.cellWidthPx + (sx - 1) * gridMetrics.colGapPx
        val targetH = sy * gridMetrics.cellHeightPx + (sy - 1) * gridMetrics.rowGapPx
        val radius = CornerRadius(16.dp.toPx(), 16.dp.toPx())

        drawRoundRect(
            color = highlightColor.copy(alpha = 0.16f),
            topLeft = Offset(targetLeft, targetTop),
            size = Size(targetW, targetH),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = highlightColor,
            topLeft = Offset(targetLeft, targetTop),
            size = Size(targetW, targetH),
            cornerRadius = radius,
            style = Stroke(width = 2.dp.toPx()),
        )

        if (edgeIndicatorDir != 0) {
            val barW = 4.dp.toPx()
            val barH = gridMetrics.gridBounds.height * 0.45f
            val barTop = gridMetrics.gridBounds.top - rootPos.y + (gridMetrics.gridBounds.height - barH) / 2f
            val barLeft = if (edgeIndicatorDir < 0) {
                (gridMetrics.gridBounds.left - rootPos.x - 10.dp.toPx()).coerceAtLeast(4.dp.toPx())
            } else {
                (gridMetrics.gridBounds.right - rootPos.x + 6.dp.toPx()).coerceAtMost(size.width - barW - 4.dp.toPx())
            }
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.85f),
                topLeft = Offset(barLeft, barTop),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }

    // Floating drag preview tracking finger smoothly in root coordinates
    val ghostTopLeft = session.pointerWindowPos - session.grabOffsetInItem - rootPos
    val ghostWidthDp = with(density) { session.itemSizePx.width.coerceAtLeast(48f).toDp() }
    val ghostHeightDp = with(density) { session.itemSizePx.height.coerceAtLeast(48f).toDp() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { IntOffset(ghostTopLeft.x.roundToInt(), ghostTopLeft.y.roundToInt()) }
            .size(width = ghostWidthDp, height = ghostHeightDp)
            .graphicsLayer {
                scaleX = 1.05f
                scaleY = 1.05f
                alpha = 0.92f
            },
    ) {
        when (val dragged = session.item) {
            is DragItem.App -> {
                val iconSize = if (cols == 5) 48.dp else 54.dp
                AppIconImage(app = dragged.slot.app, size = iconSize)
            }

            is DragItem.Widget -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.5.dp, highlightColor, RoundedCornerShape(16.dp)),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.widget_size_label, sx, sy),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePageIndicatorBar(
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
private fun HomePageContent(
    isLoading: Boolean,
    realPage: Int,
    pageApps: ImmutableList<HomeAppSlot>,
    pageWidgets: ImmutableList<PlacedWidget>,
    isPreferredWidgetSlot: Boolean,
    isCurrentVirtualPage: Boolean,
    resizingWidgetId: Int?,
    draggedAppKey: String?,
    draggedWidgetId: Int?,
    cols: Int,
    appGestures: ImmutableMap<String, ImmutableMap<SwipeDirection, String>>,
    widgetHostController: WidgetHostController,
    appMenuHost: AppContextMenuHost,
    widgetMenuHost: WidgetContextMenuHost,
    onResizingWidgetBoundsChanged: (Rect) -> Unit,
    onIconDownPointer: (Long, Set<SwipeDirection>) -> Unit,
    onWidgetTouchDown: (Long?) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppSwipe: (AppItem, SwipeDirection, AppActionOrigin) -> Unit,
    onAppDragStart: (HomeAppSlot, Rect, Offset) -> Unit,
    onAppDragMove: (Offset) -> Unit,
    onAppDragEnd: (Offset) -> Unit,
    onAppDragCancel: () -> Unit,
) {
    val density = LocalDensity.current
    val rows = HomeGridEngine.HOME_GRID_ROWS

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
        val safeCols = cols.coerceIn(4, 5)
        val cellWidthPx = ((innerWidthPx - colGapPx * (safeCols - 1)) / safeCols).coerceAtLeast(48f)
        val cellHeightPx = ((innerHeightPx - rowGapPx * (rows - 1)) / rows).coerceAtLeast(48f)
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

                pageApps.isEmpty() && pageWidgets.isEmpty() -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.empty_home_page_hint),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.72f),
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 4f,
                                ),
                            ),
                        )
                    }
                }

                else -> {
                    val cellHeightDp = with(density) { cellHeightPx.toDp() }
                    val iconSize = when {
                        safeCols == 5 || cellHeightDp < 86.dp -> 46.dp
                        else -> 52.dp
                    }

                    // 2D CellLayout widgets at exact (cellX, cellY, spanX, spanY)
                    pageWidgets.forEach { widget ->
                        key(widget.appWidgetId) {
                            val sx = widget.spanX.coerceIn(1, safeCols)
                            val sy = widget.spanY.coerceIn(1, rows)
                            val cx = widget.cellX.coerceIn(0, (safeCols - sx).coerceAtLeast(0))
                            val cy = widget.cellY.coerceIn(0, (rows - sy).coerceAtLeast(0))
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
                                    isResizing = isResizing,
                                    isBeingDragged = isBeingDragged,
                                    widgetHostController = widgetHostController,
                                    onResizingBoundsChanged = onResizingWidgetBoundsChanged,
                                    onTouchDown = onWidgetTouchDown,
                                    onLongClick = { widgetMenuHost.onOpen(widget) },
                                )
                                WidgetContextMenuDropdown(
                                    widget = widget,
                                    expanded = isCurrentVirtualPage && widgetMenuHost.activeWidgetId == widget.appWidgetId,
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
                            val cy = slot.cellY.coerceIn(0, rows - 1)
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
                                    showLabel = true,
                                    onWallpaper = true,
                                    iconSize = iconSize,
                                    onDownPointer = onIconDownPointer,
                                    onClick = { onAppClick(slot.app) },
                                    onLongClick = {
                                        appMenuHost.onOpen(slot.app, AppActionOrigin.HOME, realPage)
                                    },
                                    onLongPressHoldChanged = { isPointerHeld = it },
                                    onSwipe = { dir -> onAppSwipe(slot.app, dir, AppActionOrigin.HOME) },
                                    onDragStart = { bounds, pos -> onAppDragStart(slot, bounds, pos) },
                                    onDragMove = onAppDragMove,
                                    onDragEnd = onAppDragEnd,
                                    onDragCancel = onAppDragCancel,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                AppContextMenuDropdown(
                                    app = slot.app,
                                    origin = AppActionOrigin.HOME,
                                    sourcePageIndex = realPage,
                                    expanded = isCurrentVirtualPage &&
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
    isResizing: Boolean,
    isBeingDragged: Boolean,
    widgetHostController: WidgetHostController,
    onResizingBoundsChanged: (Rect) -> Unit,
    onTouchDown: (Long?) -> Unit,
    onLongClick: () -> Unit,
) {
    val currentOnTouchDown by rememberUpdatedState(onTouchDown)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
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
    }
}
