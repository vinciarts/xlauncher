package com.example.ui.screens

import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.AppActionOrigin
import com.example.model.AppItem
import com.example.model.HomeGestureAction
import com.example.model.HomePageCommand
import com.example.model.HomePageCommandType
import com.example.model.PlacedWidget
import com.example.model.SwipeDirection
import com.example.ui.components.AppIconTile
import com.example.ui.components.PageIndicatorDots
import com.example.widget.WidgetHostController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

private class LayoutCoordsRef(var coords: LayoutCoordinates? = null)

@Composable
fun HomeScreen(
    isLoading: Boolean,
    homePageApps: List<List<AppItem>>,
    homePageWidgets: List<List<PlacedWidget>>,
    dockApps: List<AppItem>,
    appGestures: Map<String, Map<SwipeDirection, String>>,
    homeGestures: Map<SwipeDirection, HomeGestureAction>,
    homeGridColumns: Int,
    homeLoopPaging: Boolean,
    currentHomePage: Int,
    homePageCommand: HomePageCommand?,
    widgetHostController: WidgetHostController,
    onHomePageChanged: (Int) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem, AppActionOrigin, Int, Rect?) -> Unit,
    onAppSwipe: (AppItem, SwipeDirection, AppActionOrigin) -> Unit,
    onWidgetLongClick: (PlacedWidget, Rect?) -> Unit,
    onHomeSwipe: (SwipeDirection) -> Unit,
    onEmptyLongPress: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentOnEmptyLongPress by rememberUpdatedState(onEmptyLongPress)
    val currentOnHomeSwipe by rememberUpdatedState(onHomeSwipe)
    val currentHomeGestures by rememberUpdatedState(homeGestures)

    val pageCount = homePageApps.size.coerceAtLeast(1)
    val loopEnabled = homeLoopPaging && pageCount > 1

    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val pagingTouchSlopPx = viewConfig.scaledPagingTouchSlop.toFloat()
    val minFlingVelocityPx = viewConfig.scaledMinimumFlingVelocity.toFloat()

    // Plain refs for gesture coordination without triggering Compose recompositions on touch
    var lastIconPointerId by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var lastIconBoundDirs by remember { mutableStateOf<Set<SwipeDirection>>(emptySet()) }
    var lastWidgetTouchDownMs by remember { mutableLongStateOf(0L) }
    val rootCoordsRef = remember { LayoutCoordsRef() }

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
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    var claimedDir: SwipeDirection? = null

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
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
                var pressStartedOnWidget = false
                detectTapGestures(
                    onPress = {
                        pressStartedOnWidget = (System.currentTimeMillis() - lastWidgetTouchDownMs) < 80L
                        tryAwaitRelease()
                    },
                    onLongPress = { localOffset ->
                        if (!pressStartedOnWidget) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val rootOffset = rootCoordsRef.coords?.takeIf { it.isAttached }?.positionInWindow() ?: Offset.Zero
                            currentOnEmptyLongPress(rootOffset + localOffset)
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

                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("home_pager"),
                    ) { virtualPage ->
                        val realPage = ((virtualPage % pageCount) + pageCount) % pageCount
                        val pageApps = homePageApps.getOrElse(realPage) { emptyList() }
                        val pageWidgets = homePageWidgets.getOrElse(realPage) { emptyList() }

                        // In a 2-page infinite loop, both (currentPage - 1) and (currentPage + 1) map to the same realPage.
                        // derivedStateOf ensures recomposition only happens when the swipe direction flips, never per pixel.
                        val isPreferredWidgetSlot by remember(virtualPage, loopEnabled, pageCount, pagerState) {
                            derivedStateOf {
                                if (!loopEnabled || pageCount != 2) {
                                    true
                                } else {
                                    val current = pagerState.currentPage
                                    val offset = pagerState.currentPageOffsetFraction
                                    when (virtualPage) {
                                        current -> true
                                        current - 1 -> offset < 0f
                                        current + 1 -> offset >= 0f
                                        else -> false
                                    }
                                }
                            }
                        }

                        HomePageContent(
                            isLoading = isLoading,
                            realPage = realPage,
                            pageApps = pageApps,
                            pageWidgets = pageWidgets,
                            isPreferredWidgetSlot = isPreferredWidgetSlot,
                            cols = homeGridColumns,
                            appGestures = appGestures,
                            widgetHostController = widgetHostController,
                            onIconDownPointer = { id, dirs ->
                                lastIconPointerId = id
                                lastIconBoundDirs = dirs
                            },
                            onWidgetTouchDown = { pointerId ->
                                if (pointerId != null) {
                                    // Yield vertical UP/DOWN Home gestures to scrollable widgets while preserving horizontal paging
                                    lastIconPointerId = pointerId
                                    lastIconBoundDirs = setOf(SwipeDirection.UP, SwipeDirection.DOWN)
                                }
                                lastWidgetTouchDownMs = System.currentTimeMillis()
                            },
                            onAppClick = onAppClick,
                            onAppLongClick = onAppLongClick,
                            onAppSwipe = onAppSwipe,
                            onWidgetLongClick = onWidgetLongClick,
                        )
                    }

                    HomePageIndicatorBar(
                        pagerState = pagerState,
                        pageCount = pageCount,
                        loopEnabled = loopEnabled,
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
                            val boundDirs = appGestures[app.componentKey]?.keys ?: emptySet()
                            AppIconTile(
                                app = app,
                                boundDirections = boundDirs,
                                showLabel = false,
                                onWallpaper = true,
                                iconSize = 50.dp,
                                onDownPointer = { id, dirs ->
                                    lastIconPointerId = id
                                    lastIconBoundDirs = dirs
                                },
                                onClick = { onAppClick(app) },
                                onLongClick = { bounds ->
                                    onAppLongClick(app, AppActionOrigin.DOCK, 0, bounds)
                                },
                                onSwipe = { dir -> onAppSwipe(app, dir, AppActionOrigin.DOCK) },
                                modifier = Modifier.weight(1f),
                            )
                        }
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
) {
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        val realCurrent = ((pagerState.currentPage % pageCount) + pageCount) % pageCount
        PageIndicatorDots(
            pageCount = pageCount,
            currentPage = realCurrent,
            activeColor = Color.White,
            inactiveColor = Color.White.copy(alpha = 0.38f),
            onSelectPage = { targetReal ->
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
    pageApps: List<AppItem>,
    pageWidgets: List<PlacedWidget>,
    isPreferredWidgetSlot: Boolean,
    cols: Int,
    appGestures: Map<String, Map<SwipeDirection, String>>,
    widgetHostController: WidgetHostController,
    onIconDownPointer: (Long, Set<SwipeDirection>) -> Unit,
    onWidgetTouchDown: (Long?) -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem, AppActionOrigin, Int, Rect?) -> Unit,
    onAppSwipe: (AppItem, SwipeDirection, AppActionOrigin) -> Unit,
    onWidgetLongClick: (PlacedWidget, Rect?) -> Unit,
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        when {
            isLoading -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp),
                    )
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
                val widgetRows = remember(pageWidgets, cols) {
                    packWidgetsIntoRows(pageWidgets, cols)
                }
                val appRows = remember(pageApps, cols) {
                    pageApps.chunked(cols)
                }
                val iconSize = if (cols == 5) 48.dp else 52.dp

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp, bottom = 8.dp),
                ) {
                    widgetRows.forEach { rowWidgets ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            var usedCols = 0
                            rowWidgets.forEach { widget ->
                                val wSpan = widget.spanX.coerceIn(1, cols)
                                usedCols += wSpan
                                key(widget.appWidgetId) {
                                    Box(modifier = Modifier.weight(wSpan.toFloat())) {
                                        HomeWidgetSlot(
                                            widget = widget,
                                            isPreferredSlot = isPreferredWidgetSlot,
                                            widgetHostController = widgetHostController,
                                            onTouchDown = onWidgetTouchDown,
                                            onLongClick = { bounds -> onWidgetLongClick(widget, bounds) },
                                        )
                                    }
                                }
                            }
                            if (usedCols < cols) {
                                Spacer(modifier = Modifier.weight((cols - usedCols).toFloat()))
                            }
                        }
                    }

                    appRows.forEach { rowApps ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowApps.forEach { app ->
                                key(app.componentKey) {
                                    val boundDirs = appGestures[app.componentKey]?.keys ?: emptySet()
                                    AppIconTile(
                                        app = app,
                                        boundDirections = boundDirs,
                                        showLabel = true,
                                        onWallpaper = true,
                                        iconSize = iconSize,
                                        onDownPointer = onIconDownPointer,
                                        onClick = { onAppClick(app) },
                                        onLongClick = { bounds ->
                                            onAppLongClick(app, AppActionOrigin.HOME, realPage, bounds)
                                        },
                                        onSwipe = { dir -> onAppSwipe(app, dir, AppActionOrigin.HOME) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            val emptySlots = cols - rowApps.size
                            if (emptySlots > 0) {
                                Spacer(modifier = Modifier.weight(emptySlots.toFloat()))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun packWidgetsIntoRows(widgets: List<PlacedWidget>, cols: Int): List<List<PlacedWidget>> {
    if (widgets.isEmpty()) return emptyList()
    val rows = mutableListOf<MutableList<PlacedWidget>>()
    var currentRow = mutableListOf<PlacedWidget>()
    var currentUsed = 0
    for (w in widgets) {
        val span = w.spanX.coerceIn(1, cols)
        if (currentRow.isNotEmpty() && currentUsed + span > cols) {
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentUsed = 0
        }
        currentRow.add(w)
        currentUsed += span
    }
    if (currentRow.isNotEmpty()) {
        rows.add(currentRow)
    }
    return rows
}

@Composable
private fun HomeWidgetSlot(
    widget: PlacedWidget,
    isPreferredSlot: Boolean,
    widgetHostController: WidgetHostController,
    onTouchDown: (Long?) -> Unit,
    onLongClick: (Rect?) -> Unit,
) {
    val currentOnTouchDown by rememberUpdatedState(onTouchDown)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val slotCoordsRef = remember { LayoutCoordsRef() }

    val rowHeightDp = 86
    val rowSpacingDp = 12
    val targetHeightDp = (widget.spanY * rowHeightDp + (widget.spanY - 1) * rowSpacingDp).coerceAtLeast(rowHeightDp)
    val hasProvider = remember(widget.appWidgetId) {
        widgetHostController.getProviderInfo(widget.appWidgetId) != null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(targetHeightDp.dp)
            .onGloballyPositioned { coords -> slotCoordsRef.coords = coords }
            .pointerInput(widget.appWidgetId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentOnTouchDown(down.id.value)
                }
            }
            .testTag("home_widget_${widget.appWidgetId}"),
    ) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(64)
        if (hasProvider) {
            key(widget.appWidgetId) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.FrameLayout(ctx).apply {
                            clipChildren = false
                            clipToPadding = false
                            if (isPreferredSlot) {
                                widgetHostController.attachViewToContainer(widget.appWidgetId, this)?.apply {
                                    onTouchDownListener = { currentOnTouchDown(null) }
                                    onLongPressListener = { bounds -> currentOnLongClick(bounds) }
                                    updateSizeDpIfNeeded(widthDp, targetHeightDp)
                                }
                            }
                        }
                    },
                    update = { container ->
                        if (isPreferredSlot) {
                            widgetHostController.attachViewToContainer(widget.appWidgetId, container)?.apply {
                                onTouchDownListener = { currentOnTouchDown(null) }
                                onLongPressListener = { bounds -> currentOnLongClick(bounds) }
                                updateSizeDpIfNeeded(widthDp, targetHeightDp)
                            }
                        }
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
                            onLongPress = {
                                val bounds = slotCoordsRef.coords?.takeIf { it.isAttached }?.boundsInWindow()
                                currentOnLongClick(bounds)
                            },
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
