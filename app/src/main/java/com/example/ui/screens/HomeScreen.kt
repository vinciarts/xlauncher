package com.example.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalView
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
import com.example.ui.screens.home.CELL_COL_GAP_DP
import com.example.ui.screens.home.CELL_ROW_GAP_DP
import com.example.ui.screens.home.DRAG_EDGE_FLIP_DELAY_MS
import com.example.ui.screens.home.DRAG_EDGE_INNER_DP
import com.example.ui.screens.home.DRAG_NEW_FRONT_PAGE
import com.example.ui.screens.home.DragItem
import com.example.ui.screens.home.GRID_BOTTOM_PAD_DP
import com.example.ui.screens.home.GRID_HORIZONTAL_PAD_DP
import com.example.ui.screens.home.GRID_TOP_PAD_DP
import com.example.ui.screens.home.HomeDragOverlay
import com.example.ui.screens.home.HomeDragSession
import com.example.ui.screens.home.HomePageContent
import com.example.ui.screens.home.HomePageIndicatorBar
import com.example.ui.screens.home.LayoutCoordsRef
import com.example.ui.screens.home.PageGridMetrics
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

internal fun View.performGestureTriggerHaptic() {
    val feedbackConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(feedbackConstant)
}

@Composable
fun HomeScreen(
    isLoading: Boolean,
    homePageApps: ImmutableList<ImmutableList<HomeAppSlot>>,
    homePageWidgets: ImmutableList<ImmutableList<PlacedWidget>>,
    dockApps: ImmutableList<AppItem>,
    appGestures: ImmutableMap<String, ImmutableMap<SwipeDirection, String>>,
    homeGestures: ImmutableMap<SwipeDirection, HomeGestureAction>,
    homeGridColumns: Int,
    homeGridRows: Int,
    homeLoopPaging: Boolean,
    maxHomePages: Int,
    maxDockItems: Int,
    currentHomePage: Int,
    homePageCommand: HomePageCommand?,
    isHomeEditMode: Boolean,
    resizingWidgetId: Int?,
    widgetHostController: WidgetHostController,
    appMenuHost: AppContextMenuHost,
    widgetMenuHost: WidgetContextMenuHost,
    homeEmptyMenuAnchor: Offset?,
    onHomePageChanged: (Int) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppSwipe: (AppItem, SwipeDirection, AppActionOrigin) -> Unit,
    onResizeWidget: (Int, Int, Int, Int, Int) -> Unit,
    onSelectEditWidget: (PlacedWidget) -> Unit,
    onDismissResizeWidget: () -> Unit,
    onExitHomeEditMode: () -> Unit,
    onMoveHomeAppToCell: (String, Int, Int, Int, Int) -> Unit,
    onMoveWidgetToCell: (Int, Int, Int, Int) -> Unit,
    onMoveHomeAppToNewPage: (String, Int, Boolean, Int, Int) -> Unit,
    onMoveWidgetToNewPage: (Int, Boolean, Int, Int) -> Unit,
    onReorderDockApp: (String, Int) -> Unit,
    onMoveDockAppToHomeCell: (String, Int, Int, Int) -> Unit,
    onMoveDockAppToNewPage: (String, Boolean, Int, Int) -> Unit,
    onMoveHomeAppToDock: (String, Int, Int) -> Unit,
    onRemoveHomeApp: (String, Int) -> Unit,
    onRemoveDockApp: (String) -> Unit,
    onRemoveWidget: (PlacedWidget) -> Unit,
    onHomeSwipe: (SwipeDirection) -> Unit,
    onEmptyLongPress: (Offset) -> Unit,
    onEmptyMenuAddWidget: () -> Unit,
    onEmptyMenuOpenWallpaper: () -> Unit,
    onEmptyMenuEditLayout: () -> Unit,
    onEmptyMenuOpenSettings: () -> Unit,
    onEmptyMenuAddPageAtStart: () -> Unit,
    onEmptyMenuAddPageAtEnd: () -> Unit,
    onEmptyMenuMovePageLeft: () -> Unit,
    onEmptyMenuMovePageRight: () -> Unit,
    onEmptyMenuDeleteCurrentPage: () -> Unit,
    onDismissEmptyMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val currentOnEmptyLongPress by rememberUpdatedState(onEmptyLongPress)
    val currentOnHomeSwipe by rememberUpdatedState(onHomeSwipe)
    val currentHomeGestures by rememberUpdatedState(homeGestures)
    val currentOnDismissResizeWidget by rememberUpdatedState(onDismissResizeWidget)
    val currentHomePageApps by rememberUpdatedState(homePageApps)
    val currentHomePageWidgets by rememberUpdatedState(homePageWidgets)
    val currentDockApps by rememberUpdatedState(dockApps)
    val currentCols by rememberUpdatedState(homeGridColumns)
    val currentRows by rememberUpdatedState(homeGridRows)
    val currentMaxDockItems by rememberUpdatedState(maxDockItems)
    val currentOnMoveHomeAppToCell by rememberUpdatedState(onMoveHomeAppToCell)
    val currentOnMoveWidgetToCell by rememberUpdatedState(onMoveWidgetToCell)
    val currentOnMoveHomeAppToNewPage by rememberUpdatedState(onMoveHomeAppToNewPage)
    val currentOnMoveWidgetToNewPage by rememberUpdatedState(onMoveWidgetToNewPage)
    val currentOnReorderDockApp by rememberUpdatedState(onReorderDockApp)
    val currentOnMoveDockAppToHomeCell by rememberUpdatedState(onMoveDockAppToHomeCell)
    val currentOnMoveDockAppToNewPage by rememberUpdatedState(onMoveDockAppToNewPage)
    val currentOnMoveHomeAppToDock by rememberUpdatedState(onMoveHomeAppToDock)
    val currentOnRemoveHomeApp by rememberUpdatedState(onRemoveHomeApp)
    val currentOnRemoveDockApp by rememberUpdatedState(onRemoveDockApp)
    val currentOnRemoveWidget by rememberUpdatedState(onRemoveWidget)

    var dragSession by remember { mutableStateOf<HomeDragSession?>(null) }
    val currentIsHomeEditMode by rememberUpdatedState(isHomeEditMode)
    val currentOnExitHomeEditMode by rememberUpdatedState(onExitHomeEditMode)
    val currentOnSelectEditWidget by rememberUpdatedState(onSelectEditWidget)
    val isResizingWidget by rememberUpdatedState(resizingWidgetId != null)
    val isDraggingItem by rememberUpdatedState(dragSession != null)
    val isMenuOpen by rememberUpdatedState(
        appMenuHost.activeTarget != null ||
            widgetMenuHost.activeWidgetId != null ||
            homeEmptyMenuAnchor != null
    )

    BackHandler(enabled = isHomeEditMode || resizingWidgetId != null || dragSession != null) {
        if (dragSession != null) {
            dragSession = null
        } else {
            currentOnExitHomeEditMode()
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
    var dockBounds by remember { mutableStateOf(Rect.Zero) }
    var removeZoneBounds by remember { mutableStateOf(Rect.Zero) }
    var displayedRealPage by remember { mutableIntStateOf(currentHomePage.coerceIn(0, pageCount - 1)) }

    LaunchedEffect(dockApps.isEmpty()) {
        if (dockApps.isEmpty()) dockBounds = Rect.Zero
    }

    val currentPageGridMetrics by rememberUpdatedState(pageGridMetrics)
    val currentDockBounds by rememberUpdatedState(dockBounds)
    val currentRemoveZoneBounds by rememberUpdatedState(removeZoneBounds)
    val currentDisplayedRealPage by rememberUpdatedState(displayedRealPage)
    val currentPageCount by rememberUpdatedState(pageCount)
    val currentLoopEnabled by rememberUpdatedState(loopEnabled)

    fun isTemporaryNewPage(targetPage: Int): Boolean =
        targetPage == DRAG_NEW_FRONT_PAGE || targetPage >= currentPageCount

    fun evaluateDropTarget(
        item: DragItem,
        pointerWindowPos: Offset,
        grabOffsetInItem: Offset,
        pageIndex: Int,
        metrics: PageGridMetrics,
        fixedItemSizePx: Size? = null,
    ): HomeDragSession {
        val cols = currentCols.coerceIn(HomeGridEngine.MIN_HOME_GRID_COLUMNS, HomeGridEngine.MAX_HOME_GRID_COLUMNS)
        val rows = currentRows.coerceIn(HomeGridEngine.MIN_HOME_GRID_ROWS, HomeGridEngine.MAX_HOME_GRID_ROWS)
        val sx = item.spanX.coerceIn(1, cols)
        val sy = item.spanY.coerceIn(1, rows)

        val defaultItemSize = Size(
            width = sx * metrics.cellWidthPx + (sx - 1) * metrics.colGapPx,
            height = sy * metrics.cellHeightPx + (sy - 1) * metrics.rowGapPx,
        )
        val itemSizePx = fixedItemSizePx?.takeIf { it.width > 0f && it.height > 0f } ?: defaultItemSize

        val activeRemoveBounds = currentRemoveZoneBounds
        val removeHitPadPx = with(density) { 16.dp.toPx() }
        val isOverRemove = activeRemoveBounds != Rect.Zero &&
            pointerWindowPos.y <= activeRemoveBounds.bottom + removeHitPadPx &&
            pointerWindowPos.x in (activeRemoveBounds.left - removeHitPadPx)..(activeRemoveBounds.right + removeHitPadPx)
        if (isOverRemove) {
            return HomeDragSession(
                item = item,
                pointerWindowPos = pointerWindowPos,
                grabOffsetInItem = grabOffsetInItem,
                itemSizePx = itemSizePx,
                targetPage = pageIndex,
                targetCellX = 0,
                targetCellY = 0,
                canDrop = true,
                isSwapTarget = false,
                isOverRemoveZone = true,
            )
        }

        val activeDockBounds = currentDockBounds
        val dockHitPaddingPx = with(density) { 12.dp.toPx() }
        val dockAppsNow = currentDockApps
        val isOverDock = item !is DragItem.Widget &&
            activeDockBounds != Rect.Zero &&
            dockAppsNow.isNotEmpty() &&
            pointerWindowPos.y >= activeDockBounds.top - dockHitPaddingPx

        if (isOverDock) {
            val dockCount = dockAppsNow.size
            val slotCount: Int
            val targetIdx: Int
            val isSwap: Boolean
            when (item) {
                is DragItem.DockApp -> {
                    slotCount = dockCount
                    val slotW = (activeDockBounds.width / slotCount).coerceAtLeast(1f)
                    targetIdx = ((pointerWindowPos.x - activeDockBounds.left) / slotW)
                        .toInt()
                        .coerceIn(0, slotCount - 1)
                    isSwap = targetIdx != item.index
                }

                is DragItem.App -> {
                    val existingDockIdx = dockAppsNow.indexOfFirst { it.componentKey == item.slot.app.componentKey }
                    val canInsertNew = existingDockIdx < 0 && dockCount < currentMaxDockItems
                    slotCount = if (canInsertNew) dockCount + 1 else dockCount
                    val slotW = (activeDockBounds.width / slotCount).coerceAtLeast(1f)
                    targetIdx = ((pointerWindowPos.x - activeDockBounds.left) / slotW)
                        .toInt()
                        .coerceIn(0, slotCount - 1)
                    isSwap = when {
                        existingDockIdx >= 0 -> targetIdx != existingDockIdx
                        canInsertNew -> false
                        else -> true
                    }
                }

                is DragItem.Widget -> {
                    slotCount = 0
                    targetIdx = 0
                    isSwap = false
                }
            }

            return HomeDragSession(
                item = item,
                pointerWindowPos = pointerWindowPos,
                grabOffsetInItem = grabOffsetInItem,
                itemSizePx = itemSizePx,
                targetPage = currentDisplayedRealPage,
                targetCellX = 0,
                targetCellY = 0,
                targetDockIndex = targetIdx,
                targetDockSlotCount = slotCount,
                canDrop = true,
                isSwapTarget = isSwap,
            )
        }

        val itemTopLeft = pointerWindowPos - grabOffsetInItem
        val stepX = metrics.colStepPx.coerceAtLeast(36f)
        val stepY = metrics.rowStepPx.coerceAtLeast(36f)
        val targetX: Int
        val targetY: Int
        if (item is DragItem.DockApp) {
            val centerX = itemTopLeft.x + itemSizePx.width / 2f
            val centerY = itemTopLeft.y + itemSizePx.height / 2f
            targetX = ((centerX - metrics.gridBounds.left) / stepX).toInt().coerceIn(0, cols - 1)
            targetY = ((centerY - metrics.gridBounds.top) / stepY).toInt().coerceIn(0, rows - 1)
        } else {
            val relX = itemTopLeft.x - metrics.gridBounds.left
            val relY = itemTopLeft.y - metrics.gridBounds.top
            targetX = (relX / stepX).roundToInt().coerceIn(0, (cols - sx).coerceAtLeast(0))
            targetY = (relY / stepY).roundToInt().coerceIn(0, (rows - sy).coerceAtLeast(0))
        }

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

            is DragItem.DockApp -> {
                val widgetOcc = HomeGridEngine.buildOccupancy(pageWidgets, emptyList(), cols)
                val blockedByWidget = widgetOcc[targetY][targetX]
                canDrop = !blockedByWidget
                isSwap = !isNewEmptyPage && canDrop && pageApps.any {
                    it.cellX == targetX && it.cellY == targetY &&
                        it.app.componentKey != item.app.componentKey
                }
            }

            is DragItem.Widget -> {
                val occ = HomeGridEngine.buildOccupancy(
                    widgets = pageWidgets,
                    apps = pageApps.map { PlacedHomeApp(it.app.componentKey, it.pageIndex, it.cellX, it.cellY) },
                    cols = cols,
                    rows = rows,
                    ignoreWidgetId = item.widget.appWidgetId,
                )
                canDrop = HomeGridEngine.canPlace(occ, targetX, targetY, sx, sy, cols, rows)
                isSwap = false
            }
        }

        return HomeDragSession(
            item = item,
            pointerWindowPos = pointerWindowPos,
            grabOffsetInItem = grabOffsetInItem,
            itemSizePx = itemSizePx,
            targetPage = pageIndex,
            targetCellX = targetX,
            targetCellY = targetY,
            targetDockIndex = null,
            targetDockSlotCount = 0,
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
        val fixedSize = if (item is DragItem.DockApp && startBounds != Rect.Zero) startBounds.size else null
        dragSession = evaluateDropTarget(
            item = item,
            pointerWindowPos = pointerWindowPos,
            grabOffsetInItem = grabOffset,
            pageIndex = currentDisplayedRealPage,
            metrics = currentPageGridMetrics,
            fixedItemSizePx = fixedSize,
        )
    }

    fun updateDrag(pointerWindowPos: Offset) {
        val prev = dragSession ?: return
        val activePage = if (prev.targetDockIndex == null && isTemporaryNewPage(prev.targetPage)) {
            prev.targetPage
        } else {
            currentDisplayedRealPage
        }
        val fixedSize = if (prev.item is DragItem.DockApp) prev.itemSizePx else null
        val next = evaluateDropTarget(
            item = prev.item,
            pointerWindowPos = pointerWindowPos,
            grabOffsetInItem = prev.grabOffsetInItem,
            pageIndex = activePage,
            metrics = currentPageGridMetrics,
            fixedItemSizePx = fixedSize,
        )
        val slotChanged = if (next.isOverRemoveZone != prev.isOverRemoveZone) {
            true
        } else if (next.isOverRemoveZone) {
            false
        } else if (next.targetDockIndex != null || prev.targetDockIndex != null) {
            next.targetDockIndex != prev.targetDockIndex
        } else {
            next.targetPage != prev.targetPage ||
                next.targetCellX != prev.targetCellX ||
                next.targetCellY != prev.targetCellY
        }
        if (slotChanged) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        dragSession = next
    }

    fun finishDrag(pointerWindowPos: Offset) {
        val prev = dragSession ?: return
        val activePage = if (prev.targetDockIndex == null && isTemporaryNewPage(prev.targetPage)) {
            prev.targetPage
        } else {
            currentDisplayedRealPage
        }
        val fixedSize = if (prev.item is DragItem.DockApp) prev.itemSizePx else null
        val finalSession = evaluateDropTarget(
            item = prev.item,
            pointerWindowPos = pointerWindowPos,
            grabOffsetInItem = prev.grabOffsetInItem,
            pageIndex = activePage,
            metrics = currentPageGridMetrics,
            fixedItemSizePx = fixedSize,
        )
        dragSession = null
        if (finalSession.isOverRemoveZone) {
            when (val dragged = finalSession.item) {
                is DragItem.App -> currentOnRemoveHomeApp(dragged.slot.app.componentKey, dragged.slot.pageIndex)
                is DragItem.DockApp -> currentOnRemoveDockApp(dragged.app.componentKey)
                is DragItem.Widget -> currentOnRemoveWidget(dragged.widget)
            }
            return
        }
        if (finalSession.canDrop) {
            val dockTarget = finalSession.targetDockIndex
            val isNewFront = dockTarget == null && finalSession.targetPage == DRAG_NEW_FRONT_PAGE
            val isNewEnd = dockTarget == null && finalSession.targetPage >= currentPageCount
            when (val dragged = finalSession.item) {
                is DragItem.App -> {
                    when {
                        dockTarget != null -> {
                            currentOnMoveHomeAppToDock(
                                dragged.slot.app.componentKey,
                                dragged.slot.pageIndex,
                                dockTarget,
                            )
                        }
                        isNewFront || isNewEnd -> {
                            currentOnMoveHomeAppToNewPage(
                                dragged.slot.app.componentKey,
                                dragged.slot.pageIndex,
                                isNewFront,
                                finalSession.targetCellX,
                                finalSession.targetCellY,
                            )
                        }
                        else -> {
                            currentOnMoveHomeAppToCell(
                                dragged.slot.app.componentKey,
                                dragged.slot.pageIndex,
                                finalSession.targetPage,
                                finalSession.targetCellX,
                                finalSession.targetCellY,
                            )
                        }
                    }
                }

                is DragItem.DockApp -> {
                    when {
                        dockTarget != null -> {
                            currentOnReorderDockApp(dragged.app.componentKey, dockTarget)
                        }
                        isNewFront || isNewEnd -> {
                            currentOnMoveDockAppToNewPage(
                                dragged.app.componentKey,
                                isNewFront,
                                finalSession.targetCellX,
                                finalSession.targetCellY,
                            )
                        }
                        else -> {
                            currentOnMoveDockAppToHomeCell(
                                dragged.app.componentKey,
                                finalSession.targetPage,
                                finalSession.targetCellX,
                                finalSession.targetCellY,
                            )
                        }
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
        if (prev.targetDockIndex == null && !isTemporaryNewPage(prev.targetPage) && prev.targetPage != displayedRealPage) {
            val fixedSize = if (prev.item is DragItem.DockApp) prev.itemSizePx else null
            dragSession = evaluateDropTarget(
                item = prev.item,
                pointerWindowPos = prev.pointerWindowPos,
                grabOffsetInItem = prev.grabOffsetInItem,
                pageIndex = displayedRealPage,
                metrics = currentPageGridMetrics,
                fixedItemSizePx = fixedSize,
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
            // Uniform 4-direction Home desktop gesture detector: triggers cleanly once threshold is passed
            .pointerInput(view, pagingTouchSlopPx, minFlingVelocityPx) {
                val touchSlop = viewConfiguration.touchSlop
                val instantTriggerPx = with(density) { 48.dp.toPx() }.coerceAtLeast(pagingTouchSlopPx)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (isResizingWidget || isDraggingItem || isMenuOpen) return@awaitEachGesture
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var claimedDir: SwipeDirection? = null
                    var triggered = false

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (isResizingWidget || isDraggingItem || isMenuOpen) break
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val totalDrag = change.position - down.position
                        val absX = abs(totalDrag.x)
                        val absY = abs(totalDrag.y)

                        if (!change.pressed) {
                            val dir = claimedDir
                            if (dir != null) {
                                change.consume()
                                if (!triggered) {
                                    val velocity = velocityTracker.calculateVelocity()
                                    val directionalDist = when (dir) {
                                        SwipeDirection.UP -> -totalDrag.y
                                        SwipeDirection.DOWN -> totalDrag.y
                                        SwipeDirection.LEFT -> -totalDrag.x
                                        SwipeDirection.RIGHT -> totalDrag.x
                                    }
                                    val directionalVelocity = when (dir) {
                                        SwipeDirection.UP -> -velocity.y
                                        SwipeDirection.DOWN -> velocity.y
                                        SwipeDirection.LEFT -> -velocity.x
                                        SwipeDirection.RIGHT -> velocity.x
                                    }
                                    if (directionalDist >= pagingTouchSlopPx || directionalVelocity >= minFlingVelocityPx) {
                                        view.performGestureTriggerHaptic()
                                        currentOnHomeSwipe(dir)
                                    }
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
                            val configuredAction = currentHomeGestures[dir]
                            val homeConfigured = configuredAction != null && configuredAction != HomeGestureAction.None
                            val safePage = currentDisplayedRealPage.coerceIn(0, currentPageCount - 1)
                            val allowedByPagingEdge = when (dir) {
                                SwipeDirection.LEFT -> !currentLoopEnabled && safePage == currentPageCount - 1
                                SwipeDirection.RIGHT -> !currentLoopEnabled && safePage == 0
                                SwipeDirection.UP, SwipeDirection.DOWN -> true
                            }
                            if (homeConfigured && allowedByPagingEdge && !iconOwnsDir) {
                                claimedDir = dir
                            } else {
                                break
                            }
                        }

                        val dir = claimedDir
                        if (dir != null) {
                            change.consume()
                            if (!triggered) {
                                val directionalDist = when (dir) {
                                    SwipeDirection.UP -> -totalDrag.y
                                    SwipeDirection.DOWN -> totalDrag.y
                                    SwipeDirection.LEFT -> -totalDrag.x
                                    SwipeDirection.RIGHT -> totalDrag.x
                                }
                                if (directionalDist >= instantTriggerPx) {
                                    triggered = true
                                    view.performGestureTriggerHaptic()
                                    currentOnHomeSwipe(dir)
                                }
                            }
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
                    onTap = {
                        if (currentIsHomeEditMode && !isResizingWidget && !isDraggingItem && !isMenuOpen && !pressStartedOnChild) {
                            currentOnExitHomeEditMode()
                        }
                    },
                    onLongPress = { localOffset ->
                        if (!isResizingWidget && !isDraggingItem && !isMenuOpen && !pressStartedOnChild) {
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
                        if (session == null || session.targetDockIndex != null || gridBounds == Rect.Zero) {
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
                            if (session.targetDockIndex != null) break
                            val realCur = ((pagerState.currentPage % pageCount) + pageCount) % pageCount
                            val fixedSize = if (session.item is DragItem.DockApp) session.itemSizePx else null

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
                                            fixedItemSizePx = fixedSize,
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
                                            fixedItemSizePx = fixedSize,
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
                                            fixedItemSizePx = fixedSize,
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
                                            fixedItemSizePx = fixedSize,
                                        )
                                        break
                                    }
                                    else -> break
                                }
                            }
                        }
                    }

                    val isDraggingOnNewPage = dragSession?.let {
                        it.targetDockIndex == null && isTemporaryNewPage(it.targetPage)
                    } == true

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
                        val safeCols = currentCols.coerceIn(
                            HomeGridEngine.MIN_HOME_GRID_COLUMNS,
                            HomeGridEngine.MAX_HOME_GRID_COLUMNS,
                        )
                        val rows = currentRows.coerceIn(
                            HomeGridEngine.MIN_HOME_GRID_ROWS,
                            HomeGridEngine.MAX_HOME_GRID_ROWS,
                        )
                        val cellWidthPx = ((gridBounds.width - colGapPx * (safeCols - 1)) / safeCols).coerceAtLeast(24f)
                        val cellHeightPx = ((gridBounds.height - rowGapPx * (rows - 1)) / rows).coerceAtLeast(24f)
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

                    LaunchedEffect(homeGridColumns, homeGridRows) {
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
                                isHomeEditMode = isHomeEditMode,
                                resizingWidgetId = resizingWidgetId,
                                draggedAppKey = draggedAppKey,
                                draggedWidgetId = draggedWidgetId,
                                cols = homeGridColumns,
                                rows = homeGridRows,
                                appGestures = appGestures,
                                widgetHostController = widgetHostController,
                                appMenuHost = appMenuHost,
                                widgetMenuHost = widgetMenuHost,
                                onResizingWidgetBoundsChanged = { bounds ->
                                    resizingWidgetBounds = bounds
                                },
                                onSelectEditWidget = onSelectEditWidget,
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
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) dockBounds = coords.boundsInWindow()
                        }
                        .testTag("home_dock"),
                ) {
                    dockApps.forEachIndexed { dockIndex, app ->
                        key(app.componentKey) {
                            val boundDirs = appGestures[app.componentKey]?.keys ?: persistentSetOf()
                            val isBeingDragged = (dragSession?.item as? DragItem.DockApp)?.app?.componentKey == app.componentKey
                            var isPointerHeld by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(if (isBeingDragged) 0.25f else 1f),
                            ) {
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
                                    onLongPressHoldChanged = { isPointerHeld = it },
                                    onSwipe = { dir -> onAppSwipe(app, dir, AppActionOrigin.DOCK) },
                                    onDragStart = if (isHomeEditMode) {
                                        { bounds, pos ->
                                            appMenuHost.onDismiss()
                                            startDrag(DragItem.DockApp(app, dockIndex), bounds, pos)
                                        }
                                    } else {
                                        null
                                    },
                                    onDragMove = if (isHomeEditMode) ({ pos -> updateDrag(pos) }) else null,
                                    onDragEnd = if (isHomeEditMode) ({ pos -> finishDrag(pos) }) else null,
                                    onDragCancel = if (isHomeEditMode) ({ dragSession = null }) else null,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                AppContextMenuDropdown(
                                    app = app,
                                    origin = AppActionOrigin.DOCK,
                                    sourcePageIndex = 0,
                                    expanded = appMenuHost.isExpandedFor(app, AppActionOrigin.DOCK, 0),
                                    host = appMenuHost,
                                    isPointerHeld = isPointerHeld,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top-level AOSP DragLayer overlay for live drag target highlight + floating drag preview
        val activeDrag = dragSession
        if (activeDrag != null && (pageGridMetrics.gridBounds != Rect.Zero || dockBounds != Rect.Zero)) {
            val rootPos = rootCoordsRef.coords?.takeIf { it.isAttached }?.positionInWindow() ?: Offset.Zero
            val edgeInnerPx = with(density) { DRAG_EDGE_INNER_DP.dp.toPx() }
            val activeEdgeIndicatorDir = when {
                activeDrag.targetDockIndex != null -> 0
                activeDrag.pointerWindowPos.x <= pageGridMetrics.gridBounds.left + edgeInnerPx &&
                    (activeDrag.targetPage >= pageCount || displayedRealPage > 0 || (activeDrag.targetPage != DRAG_NEW_FRONT_PAGE && pageCount < maxHomePages)) -> -1
                activeDrag.pointerWindowPos.x >= pageGridMetrics.gridBounds.right - edgeInnerPx &&
                    (activeDrag.targetPage == DRAG_NEW_FRONT_PAGE || displayedRealPage < pageCount - 1 || (activeDrag.targetPage < pageCount && pageCount < maxHomePages)) -> 1
                else -> 0
            }
            HomeDragOverlay(
                session = activeDrag,
                gridMetrics = pageGridMetrics,
                dockBounds = dockBounds,
                rootPos = rootPos,
                cols = homeGridColumns,
                rows = homeGridRows,
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
            val safeCols = homeGridColumns.coerceIn(
                HomeGridEngine.MIN_HOME_GRID_COLUMNS,
                HomeGridEngine.MAX_HOME_GRID_COLUMNS,
            )
            val safeRows = homeGridRows.coerceIn(
                HomeGridEngine.MIN_HOME_GRID_ROWS,
                HomeGridEngine.MAX_HOME_GRID_ROWS,
            )
            val widgetRectInRoot = if (hasGridMetrics) {
                val sx = activeWidget.spanX.coerceIn(1, safeCols)
                val sy = activeWidget.spanY.coerceIn(1, safeRows)
                val cx = activeWidget.cellX.coerceIn(0, (safeCols - sx).coerceAtLeast(0))
                val cy = activeWidget.cellY.coerceIn(0, (safeRows - sy).coerceAtLeast(0))
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
            val resizeSpec = remember(activeWidget.appWidgetId, safeCols, safeRows) {
                widgetHostController.getResizeSpec(activeWidget.appWidgetId, safeCols, safeRows)
            }
            val resizePageOccupancy = remember(activeWidget, homePageWidgets, homePageApps, safeCols, safeRows) {
                val pageW = homePageWidgets.getOrElse(activeWidget.pageIndex) { emptyList() }
                val pageA = homePageApps.getOrElse(activeWidget.pageIndex) { emptyList() }.map {
                    PlacedHomeApp(it.app.componentKey, it.pageIndex, it.cellX, it.cellY)
                }
                HomeGridEngine.buildOccupancy(
                    widgets = pageW,
                    apps = pageA,
                    cols = safeCols,
                    rows = safeRows,
                    ignoreWidgetId = activeWidget.appWidgetId,
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Outside-tap layer: tapping another widget switches selection in edit mode;
                // tapping empty space exits edit mode directly.
                if (dragSession == null) {
                    val currentPageWidgets = homePageWidgets.getOrElse(activeWidget.pageIndex) { emptyList() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activeWidget.appWidgetId, currentPageWidgets, pageGridMetrics, safeCols, safeRows) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    down.consume()
                                    var upPos = down.position
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        upPos = ch.position
                                        ch.consume()
                                        if (!ch.pressed) break
                                    }
                                    val winPos = upPos + rootPos
                                    val hitWidget = if (hasGridMetrics) {
                                        currentPageWidgets.firstOrNull { w ->
                                            w.appWidgetId != activeWidget.appWidgetId && run {
                                                val sx = w.spanX.coerceIn(1, safeCols)
                                                val sy = w.spanY.coerceIn(1, safeRows)
                                                val cx = w.cellX.coerceIn(0, (safeCols - sx).coerceAtLeast(0))
                                                val cy = w.cellY.coerceIn(0, (safeRows - sy).coerceAtLeast(0))
                                                val l = pageGridMetrics.gridBounds.left + cx * pageGridMetrics.colStepPx
                                                val t = pageGridMetrics.gridBounds.top + cy * pageGridMetrics.rowStepPx
                                                val r = l + sx * pageGridMetrics.cellWidthPx + (sx - 1) * pageGridMetrics.colGapPx
                                                val b = t + sy * pageGridMetrics.cellHeightPx + (sy - 1) * pageGridMetrics.rowGapPx
                                                winPos.x in l..r && winPos.y in t..b
                                            }
                                        }
                                    } else {
                                        null
                                    }
                                    if (hitWidget != null) {
                                        currentOnSelectEditWidget(hitWidget)
                                    } else {
                                        currentOnExitHomeEditMode()
                                    }
                                }
                            },
                    )
                }
                // Measure with unbounded exact Constraints.fixed so full-width/full-height widgets
                // (whose outer handle margin exceeds screen width) are never clamped or shifted left.
                WidgetResizeFrameOverlay(
                    widget = activeWidget,
                    spec = resizeSpec,
                    cols = safeCols,
                    rows = safeRows,
                    colStepPx = pageGridMetrics.colStepPx,
                    rowStepPx = pageGridMetrics.rowStepPx,
                    isDraggingInterior = dragSession != null,
                    canPlaceRect = { cx, cy, sx, sy ->
                        HomeGridEngine.canPlace(resizePageOccupancy, cx, cy, sx, sy, safeCols, safeRows)
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
        val safeCurrentPage = currentHomePage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        HomeEmptyMenuPopup(
            anchorOffset = homeEmptyMenuAnchor,
            canAddPage = pageCount < maxHomePages,
            canMovePageLeft = pageCount > 1 && safeCurrentPage > 0,
            canMovePageRight = pageCount > 1 && safeCurrentPage < pageCount - 1,
            canDeleteCurrentPage = pageCount > 1,
            onAddWidget = onEmptyMenuAddWidget,
            onOpenWallpaper = onEmptyMenuOpenWallpaper,
            onEditLayout = onEmptyMenuEditLayout,
            onOpenSettings = onEmptyMenuOpenSettings,
            onAddPageAtStart = onEmptyMenuAddPageAtStart,
            onAddPageAtEnd = onEmptyMenuAddPageAtEnd,
            onMovePageLeft = onEmptyMenuMovePageLeft,
            onMovePageRight = onEmptyMenuMovePageRight,
            onDeleteCurrentPage = onEmptyMenuDeleteCurrentPage,
            onDismiss = onDismissEmptyMenu,
            modifier = Modifier.align(Alignment.TopStart),
        )

        if (dragSession != null) {
            val overRemove = dragSession?.isOverRemoveZone == true
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (overRemove) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f)
                },
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 6.dp)
                    .onGloballyPositioned { coords ->
                        if (coords.isAttached) removeZoneBounds = coords.boundsInWindow()
                    }
                    .testTag("home_drag_remove_zone"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    val contentColor = if (overRemove) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.home_drag_remove_zone),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                    )
                }
            }
        } else if (isHomeEditMode) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 6.dp)
                    .testTag("home_edit_mode_banner"),
            ) {
                Text(
                    text = stringResource(R.string.home_edit_mode_banner),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}



