package com.example.ui.screens

import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.example.R
import com.example.model.AppActionOrigin
import com.example.model.AppItem
import com.example.model.AppShortcutItem
import com.example.model.ContactSearchResult
import com.example.model.DrawerTab
import com.example.model.DrawerTabPage
import com.example.model.FileSearchResult
import com.example.model.SearchProviderApp
import com.example.ui.components.AppContextMenuDropdown
import com.example.ui.components.AppContextMenuHost
import com.example.ui.components.AppIconTile
import com.example.ui.components.CreateDrawerTabDialog
import com.example.ui.components.DrawerUniversalSearchResults
import com.example.ui.components.EditDrawerTabDialog
import com.example.ui.components.ExpressiveMenuGroupsContent
import com.example.ui.components.ExpressiveMenuItemSpec
import com.example.ui.components.ExpressiveSearchField
import com.example.ui.components.HomeGestureActionIcon
import com.example.ui.components.homeGestureActionLabel
import com.example.ui.components.verticalFadingEdge
import com.example.model.HomeGestureAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun AppDrawerScreen(
    isActive: Boolean,
    allApps: ImmutableList<AppItem>,
    drawerTabs: ImmutableList<DrawerTab>,
    drawerTabPages: ImmutableList<DrawerTabPage>,
    isAllTabHidden: Boolean = false,
    allTabName: String = "",
    foldedAppKeys: ImmutableSet<String> = persistentSetOf(),
    appsByKey: ImmutableMap<String, AppItem> = persistentMapOf(),
    shortcutsByTargetKey: ImmutableMap<String, AppShortcutItem> = persistentMapOf(),
    drawerMenuActions: ImmutableList<HomeGestureAction> = persistentListOf(),
    onExecuteDrawerMenuAction: (HomeGestureAction) -> Unit = {},
    filteredDrawerApps: ImmutableList<AppItem>,
    hasAnyExtraSearchEnabled: Boolean,
    matchedShortcuts: ImmutableList<AppShortcutItem>,
    matchedContacts: ImmutableList<ContactSearchResult>,
    matchedFiles: ImmutableList<FileSearchResult>,
    activeSearchProviders: ImmutableList<SearchProviderApp>,
    searchWebEnabled: Boolean,
    searchWebEngine: com.example.model.WebSearchEngine,
    drawerSearchQuery: String,
    autoFocusDrawerSearch: Boolean,
    drawerColumns: Int,
    drawerBottomAligned: Boolean = false,
    selectedTabId: String = DrawerTab.ALL_TAB_ID,
    onSelectedTabChange: (String) -> Unit = {},
    onClose: () -> Unit,
    onBack: () -> Unit = onClose,
    onSearchQueryChange: (String) -> Unit,
    onAutoFocusConsumed: () -> Unit,
    onAppClick: (AppItem) -> Unit,
    onLaunchShortcut: (AppShortcutItem) -> Unit,
    onOpenContact: (ContactSearchResult) -> Unit,
    onDialContact: (String) -> Unit,
    onOpenFile: (FileSearchResult) -> Unit,
    onLaunchThirdPartySearch: (SearchProviderApp, String) -> Unit,
    onLaunchWebSearch: (String) -> Unit,
    appMenuHost: AppContextMenuHost,
    onCreateTab: (String, List<String>) -> Unit,
    onRenameTab: (String, String) -> Unit,
    onSetTabApps: (String, Set<String>) -> Unit,
    onMoveTab: (String, Int) -> Unit,
    onSetTabHidden: (String, Boolean) -> Unit,
    onSetAllTabHidden: (Boolean) -> Unit = {},
    onSetAllTabName: (String) -> Unit = {},
    onDeleteTab: (String) -> Unit,
    onMoveAppToTab: (String, String) -> Unit = { _, _ -> },
    onToggleAppFolded: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isEditMode by remember { mutableStateOf(false) }
    var showDrawerMenu by remember { mutableStateOf(false) }
    var draggedApp by remember { mutableStateOf<AppItem?>(null) }
    var dragFingerRoot by remember { mutableStateOf(Offset.Zero) }
    var draggingTabId by remember { mutableStateOf<String?>(null) }
    var draggingTabStartIndex by remember { mutableStateOf(-1) }
    var draggingTabTargetIndex by remember { mutableStateOf(-1) }
    var draggingTabOffsetX by remember { mutableStateOf(0f) }
    val tabBounds = remember { mutableStateMapOf<String, Rect>() }
    var rootDropZoneBounds by remember { mutableStateOf<Rect?>(null) }
    var rootOffsetInWindow by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = isActive && !isEditMode, onBack = onBack)
    BackHandler(enabled = isActive && isEditMode) {
        draggedApp = null
        draggingTabId = null
        draggingTabStartIndex = -1
        draggingTabTargetIndex = -1
        draggingTabOffsetX = 0f
        isEditMode = false
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            showDrawerMenu = false
            draggedApp = null
            draggingTabId = null
            draggingTabStartIndex = -1
            draggingTabTargetIndex = -1
            draggingTabOffsetX = 0f
            isEditMode = false
        }
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(isActive, autoFocusDrawerSearch) {
        if (!isActive) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        } else if (autoFocusDrawerSearch) {
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
            onAutoFocusConsumed()
        }
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val currentOnClose by rememberUpdatedState(onClose)
    val currentIsActive by rememberUpdatedState(isActive)
    val currentIsEditMode by rememberUpdatedState(isEditMode)
    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val pagingTouchSlopPx = viewConfig.scaledPagingTouchSlop.toFloat()
    val minFlingVelocityPx = viewConfig.scaledMinimumFlingVelocity.toFloat()
    val instantTriggerPx = with(density) { 48.dp.toPx() }.coerceAtLeast(pagingTouchSlopPx)

    val normalPages = drawerTabPages.ifEmpty {
        persistentListOf(
            DrawerTabPage(
                id = DrawerTab.ALL_TAB_ID,
                name = "",
                isAllTab = true,
                apps = allApps,
            )
        )
    }

    // In Edit Mode, reveal ALL tabs (including hidden All root & hidden folders)
    // so unassigned apps can always be dragged into folders.
    val editModePages = remember(allApps, drawerTabs, allTabName) {
        val assigned = HashSet<String>()
        val folderSets = LinkedHashMap<String, Set<String>>(drawerTabs.size)
        for (tab in drawerTabs) {
            val exclusive = HashSet<String>()
            for (k in tab.appKeys) {
                if (assigned.add(k)) exclusive.add(k)
            }
            folderSets[tab.id] = exclusive
        }
        val rootApps = allApps.filterNot { it.componentKey in assigned }.toImmutableList()
        val list = ArrayList<DrawerTabPage>(drawerTabs.size + 1)
        list.add(
            DrawerTabPage(
                id = DrawerTab.ALL_TAB_ID,
                name = allTabName,
                isAllTab = true,
                apps = rootApps,
            )
        )
        for (tab in drawerTabs) {
            val keys = folderSets[tab.id].orEmpty()
            list.add(
                DrawerTabPage(
                    id = tab.id,
                    name = tab.name,
                    isAllTab = false,
                    apps = if (keys.isEmpty()) persistentListOf()
                    else allApps.filter { it.componentKey in keys }.toImmutableList(),
                )
            )
        }
        list.toImmutableList()
    }

    val visibleNormalTabIds = remember(normalPages) { normalPages.map { it.id }.toSet() }
    val pages = if (isEditMode) editModePages else normalPages

    val pageCount = pages.size
    val loopEnabled = pageCount > 1

    var showCreateTabDialog by remember { mutableStateOf(false) }
    var editingTabId by remember { mutableStateOf<String?>(null) }
    var pendingSelectNewTabFromCount by remember { mutableStateOf<Int?>(null) }
    var lastSelectedTabId by remember { mutableStateOf(selectedTabId) }
    val pageAtTopMap = remember { mutableStateMapOf<Int, Boolean>() }
    var searchAtTop by remember { mutableStateOf(true) }
    var currentIsListAtTop by remember { mutableStateOf(true) }

    // Mutually exclusive pull-down-to-close:
    // Only if the list was ALREADY at the top when the finger touched down (startedAtTop == true)
    // can pulling down close the drawer; otherwise the entire gesture is strictly list scrolling.
    val pullFromTopDismiss = remember(minFlingVelocityPx, instantTriggerPx) {
        object : NestedScrollConnection {
            private var startedAtTop = false
            private var overScrollDown = 0f

            fun onTouchDown(isAtTop: Boolean) {
                startedAtTop = isAtTop
                overScrollDown = 0f
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) {
                    overScrollDown = 0f
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!currentIsActive || currentIsEditMode || !startedAtTop) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (consumed.y != 0f) {
                    // User scrolled the list in this gesture -> lock out dismiss until next touch
                    startedAtTop = false
                    overScrollDown = 0f
                    return Offset.Zero
                }
                if (available.y > 0f) {
                    overScrollDown += available.y
                    if (overScrollDown >= instantTriggerPx) {
                        overScrollDown = 0f
                        currentOnClose()
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!currentIsActive || currentIsEditMode || !startedAtTop) return Velocity.Zero
                if (overScrollDown > 0f && available.y >= minFlingVelocityPx) {
                    overScrollDown = 0f
                    currentOnClose()
                    return available
                }
                overScrollDown = 0f
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                overScrollDown = 0f
                return Velocity.Zero
            }
        }
    }

    val directPullDownModifier = Modifier.pointerInput(pagingTouchSlopPx, minFlingVelocityPx, instantTriggerPx) {
        var dragY = 0f
        var triggered = false
        val velocityTracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = {
                dragY = 0f
                triggered = !currentIsActive || currentIsEditMode
                velocityTracker.resetTracking()
            },
            onDragEnd = {
                if (!triggered && currentIsActive) {
                    val velocityY = velocityTracker.calculateVelocity().y
                    if (dragY >= pagingTouchSlopPx || velocityY >= minFlingVelocityPx) {
                        triggered = true
                        currentOnClose()
                    }
                }
                dragY = 0f
                triggered = false
                velocityTracker.resetTracking()
            },
            onDragCancel = {
                dragY = 0f
                triggered = false
                velocityTracker.resetTracking()
            },
            onVerticalDrag = { change, dragAmount ->
                dragY += dragAmount
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                if (!triggered && currentIsActive && dragY >= instantTriggerPx) {
                    triggered = true
                    currentOnClose()
                }
            },
        )
    }

    val editingTab = editingTabId?.let { id -> drawerTabs.firstOrNull { it.id == id } }

    key(pageCount) {
        val basePage = remember(pageCount) {
            val selectNewest = pendingSelectNewTabFromCount?.let { pageCount > it } == true
            val initialReal = if (selectNewest && pageCount > 1) {
                pages.lastIndex
            } else {
                pages.indexOfFirst { it.id == lastSelectedTabId }.takeIf { it >= 0 } ?: 0
            }
            if (loopEnabled) {
                val mid = Int.MAX_VALUE / 2
                mid - (mid % pageCount) + initialReal
            } else {
                0
            }
        }

        val pagerState = rememberPagerState(
            initialPage = basePage,
            pageCount = { if (loopEnabled) Int.MAX_VALUE else 1 },
        )

        LaunchedEffect(pageCount) {
            if (pendingSelectNewTabFromCount?.let { pageCount > it } == true) {
                pendingSelectNewTabFromCount = null
            }
        }

        val currentPageIndex = if (loopEnabled) {
            ((pagerState.currentPage % pageCount) + pageCount) % pageCount
        } else {
            0
        }
        val currentTabPage = pages[currentPageIndex]

        LaunchedEffect(currentTabPage.id) {
            lastSelectedTabId = currentTabPage.id
            onSelectedTabChange(currentTabPage.id)
        }

        currentIsListAtTop = if (drawerSearchQuery.isNotBlank()) {
            searchAtTop
        } else {
            currentTabPage.apps.isEmpty() || (pageAtTopMap[currentPageIndex] != false)
        }

        val hoveredDropTabId: String? = if (isEditMode && draggedApp != null) {
            val finger = dragFingerRoot
            val overRootZone = !currentTabPage.isAllTab &&
                rootDropZoneBounds?.contains(finger) == true
            if (overRootZone) {
                DrawerTab.ALL_TAB_ID
            } else {
                pages.firstOrNull { page ->
                    page.id != currentTabPage.id && tabBounds[page.id]?.contains(finger) == true
                }?.id
            }
        } else {
            null
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .onGloballyPositioned { coords ->
                    rootOffsetInWindow = coords.positionInRoot()
                }
                .pointerInput(pullFromTopDismiss) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToDownIgnoreConsumed() }) {
                                pullFromTopDismiss.onTouchDown(currentIsListAtTop)
                            }
                        }
                    }
                }
                .nestedScroll(pullFromTopDismiss),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 660.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // Top bar: Search field in Normal Mode, Done / Move-to-All bar in Edit Mode
                if (isEditMode) {
                    val isDraggingFromFolder = draggedApp != null && !currentTabPage.isAllTab
                    val isHoveringRootZone = hoveredDropTabId == DrawerTab.ALL_TAB_ID &&
                        rootDropZoneBounds?.contains(dragFingerRoot) == true
                    if (isDraggingFromFolder) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isHoveringRootZone) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .height(48.dp)
                                .onGloballyPositioned { coords ->
                                    rootDropZoneBounds = coords.boundsInRoot()
                                }
                                .testTag("drawer_move_to_all_zone"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                                    contentDescription = null,
                                    tint = if (isHoveringRootZone) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.drawer_move_to_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isHoveringRootZone) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)
                                .testTag("drawer_edit_mode_bar"),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.drawer_edit_mode_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.drawer_edit_mode_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { showCreateTabDialog = true },
                                modifier = Modifier.testTag("drawer_add_tab_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.cd_add_drawer_tab),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    draggedApp = null
                                    draggingTabId = null
                                    draggingTabOffsetX = 0f
                                    isEditMode = false
                                },
                                modifier = Modifier.testTag("drawer_edit_done_button"),
                            ) {
                                Text(text = stringResource(R.string.dialog_done))
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(directPullDownModifier)
                            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 8.dp),
                    ) {
                        ExpressiveSearchField(
                            query = drawerSearchQuery,
                            onQueryChange = onSearchQueryChange,
                            placeholder = stringResource(
                                if (hasAnyExtraSearchEnabled) R.string.search_universal_placeholder
                                else R.string.search_apps_placeholder
                            ),
                            enabled = isActive,
                            focusRequester = focusRequester,
                            inputTestTag = "drawer_search_input",
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            IconButton(
                                onClick = { showDrawerMenu = true },
                                modifier = Modifier.testTag("drawer_menu_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.cd_drawer_menu),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenuPopup(
                                expanded = showDrawerMenu,
                                onDismissRequest = { showDrawerMenu = false },
                            ) {
                                val customMenuItems = drawerMenuActions.mapIndexedNotNull { idx, action ->
                                    if (action == HomeGestureAction.None) return@mapIndexedNotNull null
                                    if (action is HomeGestureAction.LaunchApp && action.componentKey !in appsByKey) {
                                        return@mapIndexedNotNull null
                                    }
                                    val label = homeGestureActionLabel(
                                        action = action,
                                        appsByKey = appsByKey,
                                        shortcutsByTargetKey = shortcutsByTargetKey,
                                    )
                                    ExpressiveMenuItemSpec(
                                        text = {
                                            Text(
                                                text = label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        leadingIcon = {
                                            HomeGestureActionIcon(
                                                action = action,
                                                appsByKey = appsByKey,
                                                shortcutsByTargetKey = shortcutsByTargetKey,
                                                size = 22.dp,
                                            )
                                        },
                                        testTag = "drawer_menu_custom_item_$idx",
                                        onClick = {
                                            showDrawerMenu = false
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            onExecuteDrawerMenuAction(action)
                                        },
                                    )
                                }
                                val builtInMenuItems = listOf(
                                    ExpressiveMenuItemSpec(
                                        text = { Text(stringResource(R.string.drawer_menu_edit_drawer)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = null,
                                            )
                                        },
                                        testTag = "drawer_menu_edit_item",
                                        onClick = {
                                            showDrawerMenu = false
                                            onSearchQueryChange("")
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            isEditMode = true
                                        },
                                    ),
                                    ExpressiveMenuItemSpec(
                                        text = { Text(stringResource(R.string.settings_title)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Settings,
                                                contentDescription = null,
                                            )
                                        },
                                        testTag = "drawer_menu_settings_item",
                                        onClick = {
                                            showDrawerMenu = false
                                            onOpenSettings()
                                        },
                                    ),
                                )
                                val menuGroups = buildList {
                                    if (customMenuItems.isNotEmpty()) add(customMenuItems)
                                    add(builtInMenuItems)
                                }
                                ExpressiveMenuGroupsContent(
                                    groups = menuGroups,
                                    parentExpanded = showDrawerMenu,
                                )
                            }
                        }
                    }
                }

                if (!isEditMode && drawerSearchQuery.isNotBlank()) {
                    DrawerUniversalSearchResults(
                        query = drawerSearchQuery,
                        cols = drawerColumns,
                        hasAnyExtraSearchEnabled = hasAnyExtraSearchEnabled,
                        apps = filteredDrawerApps,
                        shortcuts = matchedShortcuts,
                        contacts = matchedContacts,
                        files = matchedFiles,
                        searchProviders = activeSearchProviders,
                        searchWebEnabled = searchWebEnabled,
                        searchWebEngine = searchWebEngine,
                        appMenuHost = appMenuHost,
                        onAppClick = onAppClick,
                        onLaunchShortcut = onLaunchShortcut,
                        onOpenContact = onOpenContact,
                        onDialContact = onDialContact,
                        onOpenFile = onOpenFile,
                        onLaunchThirdPartySearch = onLaunchThirdPartySearch,
                        onLaunchWebSearch = onLaunchWebSearch,
                        onAtTopChanged = { searchAtTop = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("drawer_pager"),
                    ) { virtualPage ->
                        val realPage = if (loopEnabled) {
                            ((virtualPage % pageCount) + pageCount) % pageCount
                        } else {
                            0
                        }
                        val tabPage = pages.getOrElse(realPage) { pages.first() }
                        if (tabPage.apps.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(directPullDownModifier)
                                    .padding(horizontal = 32.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.empty_drawer_tab_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (!tabPage.isAllTab) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        FilledTonalButton(
                                            onClick = { editingTabId = tabPage.id },
                                            modifier = Modifier.testTag("empty_tab_add_apps_button"),
                                        ) {
                                            Text(stringResource(R.string.drawer_tab_add_apps_action))
                                        }
                                    }
                                }
                            }
                        } else {
                            DrawerAppsVerticalGrid(
                                apps = tabPage.apps,
                                foldedAppKeys = foldedAppKeys,
                                cols = drawerColumns,
                                bottomAligned = drawerBottomAligned,
                                tabIndex = realPage,
                                isActive = isActive,
                                isCurrentPage = virtualPage == pagerState.currentPage,
                                isEditMode = isEditMode,
                                draggedAppKey = draggedApp?.componentKey,
                                onAppClick = onAppClick,
                                onToggleAppFolded = onToggleAppFolded,
                                appMenuHost = appMenuHost,
                                onAtTopChanged = { atTop -> pageAtTopMap[realPage] = atTop },
                                onAppDragStart = { app, fingerRoot ->
                                    draggedApp = app
                                    dragFingerRoot = fingerRoot
                                },
                                onAppDragMove = { fingerRoot ->
                                    dragFingerRoot = fingerRoot
                                },
                                onAppDragEnd = { fingerRoot ->
                                    dragFingerRoot = fingerRoot
                                    val moving = draggedApp
                                    val targetId = if (moving != null) {
                                        val overRoot = !currentTabPage.isAllTab &&
                                            rootDropZoneBounds?.contains(fingerRoot) == true
                                        if (overRoot) {
                                            DrawerTab.ALL_TAB_ID
                                        } else {
                                            pages.firstOrNull { page ->
                                                page.id != currentTabPage.id &&
                                                    tabBounds[page.id]?.contains(fingerRoot) == true
                                            }?.id
                                        }
                                    } else {
                                        null
                                    }
                                    draggedApp = null
                                    if (moving != null && targetId != null && targetId != currentTabPage.id) {
                                        onMoveAppToTab(moving.componentKey, targetId)
                                    }
                                },
                                onAppDragCancel = {
                                    draggedApp = null
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    // Bottom Tab bar: full width in both modes; in Edit Mode, custom tabs can be
                    // dragged left/right to reorder, and any tab serves as a drop target for apps.
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(directPullDownModifier),
                    ) {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = currentPageIndex,
                            minTabWidth = 56.dp,
                            edgePadding = 8.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            divider = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drawer_tab_row"),
                        ) {
                            val firstCustomIdx = pages.indexOfFirst { !it.isAllTab }.coerceAtLeast(0)
                            val draggedTabWidthPx = draggingTabId?.let { tabBounds[it]?.width } ?: 0f

                            pages.forEachIndexed { index, tabPage ->
                                val title = if (tabPage.isAllTab) {
                                    tabPage.name.ifBlank { stringResource(R.string.drawer_tab_all) }
                                } else {
                                    tabPage.name
                                }
                                val isSelected = currentPageIndex == index
                                val isDropTarget = hoveredDropTabId == tabPage.id
                                val isBeingDraggedTab = isEditMode && draggingTabId == tabPage.id

                                val neighborTargetShift = if (isEditMode && draggingTabId != null && !isBeingDraggedTab && !tabPage.isAllTab) {
                                    when {
                                        draggingTabStartIndex < draggingTabTargetIndex &&
                                            index in (draggingTabStartIndex + 1)..draggingTabTargetIndex -> -draggedTabWidthPx
                                        draggingTabStartIndex > draggingTabTargetIndex &&
                                            index in draggingTabTargetIndex until draggingTabStartIndex -> draggedTabWidthPx
                                        else -> 0f
                                    }
                                } else {
                                    0f
                                }
                                val animatedNeighborShiftX by animateFloatAsState(
                                    targetValue = neighborTargetShift,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                    label = "tab_reorder_shift_${tabPage.id}",
                                )

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .padding(horizontal = 4.dp, vertical = 6.dp)
                                        .zIndex(if (isBeingDraggedTab) 2f else 0f)
                                        .graphicsLayer {
                                            if (isBeingDraggedTab) {
                                                translationX = draggingTabOffsetX
                                                scaleX = 1.08f
                                                scaleY = 1.08f
                                            } else {
                                                translationX = animatedNeighborShiftX
                                            }
                                        }
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            when {
                                                isDropTarget -> MaterialTheme.colorScheme.primaryContainer
                                                isBeingDraggedTab -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> Color.Transparent
                                            }
                                        )
                                        .onGloballyPositioned { coords ->
                                            // Record rest bounds when not currently dragging any tab
                                            if (draggingTabId == null) {
                                                tabBounds[tabPage.id] = coords.boundsInRoot()
                                            }
                                        }
                                        .combinedClickable(
                                            onClick = {
                                                if (index != currentPageIndex) {
                                                    if (loopEnabled) {
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(
                                                                pagerState.currentPage + (index - currentPageIndex)
                                                            )
                                                        }
                                                    }
                                                } else if (isEditMode) {
                                                    editingTabId = tabPage.id
                                                }
                                            },
                                        )
                                        .then(
                                            if (isEditMode && !tabPage.isAllTab) {
                                                Modifier.pointerInput(tabPage.id, isEditMode, pages.size) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            draggingTabId = tabPage.id
                                                            draggingTabStartIndex = index
                                                            draggingTabTargetIndex = index
                                                            draggingTabOffsetX = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            draggingTabOffsetX += dragAmount.x
                                                            val startBounds = tabBounds[tabPage.id]
                                                            if (startBounds != null) {
                                                                val draggedCenterX = startBounds.center.x + draggingTabOffsetX
                                                                var computed = index
                                                                if (draggedCenterX > startBounds.center.x) {
                                                                    for (i in (index + 1)..pages.lastIndex) {
                                                                        val b = tabBounds[pages[i].id] ?: continue
                                                                        if (draggedCenterX > b.center.x) {
                                                                            computed = i
                                                                        }
                                                                    }
                                                                } else if (draggedCenterX < startBounds.center.x) {
                                                                    for (i in (index - 1) downTo firstCustomIdx) {
                                                                        val b = tabBounds[pages[i].id] ?: continue
                                                                        if (draggedCenterX < b.center.x) {
                                                                            computed = i
                                                                        }
                                                                    }
                                                                }
                                                                if (computed != draggingTabTargetIndex) {
                                                                    draggingTabTargetIndex = computed
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                }
                                                            }
                                                        },
                                                        onDragEnd = {
                                                            val delta = draggingTabTargetIndex - draggingTabStartIndex
                                                            val movedId = draggingTabId
                                                            draggingTabId = null
                                                            draggingTabStartIndex = -1
                                                            draggingTabTargetIndex = -1
                                                            draggingTabOffsetX = 0f
                                                            if (movedId != null && delta != 0) {
                                                                onMoveTab(movedId, delta)
                                                            }
                                                        },
                                                        onDragCancel = {
                                                            draggingTabId = null
                                                            draggingTabStartIndex = -1
                                                            draggingTabTargetIndex = -1
                                                            draggingTabOffsetX = 0f
                                                        },
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(horizontal = 12.dp)
                                        .testTag("drawer_tab_${tabPage.id}"),
                                ) {
                                    val isHiddenInNormal = isEditMode && tabPage.id !in visibleNormalTabIds
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = when {
                                            isDropTarget -> MaterialTheme.colorScheme.onPrimaryContainer
                                            isBeingDraggedTab -> MaterialTheme.colorScheme.onSecondaryContainer
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.alpha(if (isHiddenInNormal) 0.55f else 1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating lifted ghost icon when dragging an app to a Tab in Edit Mode
            val liftingApp = draggedApp
            if (isEditMode && liftingApp != null) {
                val ghostSizeDp = 56.dp
                val halfPx = with(density) { (ghostSizeDp / 2).toPx() }
                val localPos = dragFingerRoot - rootOffsetInWindow
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            IntOffset(
                                x = (localPos.x - halfPx).roundToInt(),
                                y = (localPos.y - halfPx).roundToInt(),
                            )
                        }
                        .size(ghostSizeDp)
                        .graphicsLayer {
                            scaleX = 1.15f
                            scaleY = 1.15f
                            alpha = 0.92f
                        },
                ) {
                    val bmp = liftingApp.icon
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = liftingApp.label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    if (showCreateTabDialog) {
        CreateDrawerTabDialog(
            onConfirm = { name ->
                showCreateTabDialog = false
                pendingSelectNewTabFromCount = pageCount
                onCreateTab(name, emptyList())
            },
            onDismiss = { showCreateTabDialog = false },
        )
    }

    if (editingTabId == DrawerTab.ALL_TAB_ID) {
        EditDrawerTabDialog(
            tabKey = DrawerTab.ALL_TAB_ID,
            initialName = allTabName,
            initialHidden = isAllTabHidden,
            canHide = drawerTabs.any { !it.isHidden },
            canDelete = false,
            allowBlankName = true,
            onSave = { newName, isHidden ->
                if (newName != allTabName) {
                    onSetAllTabName(newName)
                }
                if (isHidden != isAllTabHidden) {
                    onSetAllTabHidden(isHidden)
                }
                editingTabId = null
            },
            onDismiss = { editingTabId = null },
        )
    } else if (editingTab != null) {
        EditDrawerTabDialog(
            tabKey = editingTab.id,
            initialName = editingTab.name,
            initialHidden = editingTab.isHidden,
            canHide = normalPages.size > 1,
            canDelete = true,
            onSave = { newName, isHidden ->
                if (newName != editingTab.name) {
                    onRenameTab(editingTab.id, newName)
                }
                if (isHidden != editingTab.isHidden) {
                    onSetTabHidden(editingTab.id, isHidden)
                }
                editingTabId = null
            },
            onDelete = {
                editingTabId = null
                onDeleteTab(editingTab.id)
            },
            onDismiss = { editingTabId = null },
        )
    }
}

@Composable
private fun DrawerAppsVerticalGrid(
    apps: ImmutableList<AppItem>,
    foldedAppKeys: ImmutableSet<String>,
    cols: Int,
    bottomAligned: Boolean,
    tabIndex: Int,
    isActive: Boolean,
    isCurrentPage: Boolean,
    isEditMode: Boolean,
    draggedAppKey: String?,
    onAppClick: (AppItem) -> Unit,
    onToggleAppFolded: (String) -> Unit,
    appMenuHost: AppContextMenuHost,
    onAtTopChanged: (Boolean) -> Unit = {},
    onAppDragStart: (AppItem, Offset) -> Unit = { _, _ -> },
    onAppDragMove: (Offset) -> Unit = {},
    onAppDragEnd: (Offset) -> Unit = {},
    onAppDragCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val iconSize = when {
        cols >= 7 -> 36.dp
        cols == 6 -> 42.dp
        cols == 5 -> 48.dp
        else -> 52.dp
    }
    val primaryApps = remember(apps, foldedAppKeys) {
        apps.filterNot { it.componentKey in foldedAppKeys }
    }
    val foldedApps = remember(apps, foldedAppKeys) {
        apps.filter { it.componentKey in foldedAppKeys }
    }
    var isMoreExpanded by remember(tabIndex) { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow { !gridState.canScrollBackward }
            .collect { onAtTopChanged(it) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(
            space = 10.dp,
            alignment = if (bottomAligned) Alignment.Bottom else Alignment.Top,
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.verticalFadingEdge(gridState),
    ) {
        if (isEditMode) {
            // In Edit Mode, keep all apps stably in place (zero jumping) and mark secondary ones semi-transparent.
            items(
                items = apps,
                key = { it.componentKey },
                contentType = { "drawer_app_edit" },
            ) { app ->
                val isDraggingThis = draggedAppKey == app.componentKey
                val isFolded = app.componentKey in foldedAppKeys
                Box(
                    modifier = Modifier.alpha(
                        when {
                            isDraggingThis -> 0.25f
                            isFolded -> 0.38f
                            else -> 1f
                        }
                    ),
                ) {
                    AppIconTile(
                        app = app,
                        boundDirections = persistentSetOf(),
                        showLabel = true,
                        onWallpaper = false,
                        iconSize = iconSize,
                        onClick = { onToggleAppFolded(app.componentKey) },
                        onLongClick = {},
                        onSwipe = {},
                        onDragStart = { _, fingerRoot -> onAppDragStart(app, fingerRoot) },
                        onDragMove = onAppDragMove,
                        onDragEnd = onAppDragEnd,
                        onDragCancel = onAppDragCancel,
                    )
                }
            }
        } else if (bottomAligned) {
            // Bottom-aligned Normal Mode: Secondary ("More") zone ABOVE, Primary zone anchored at bottom.
            if (foldedApps.isNotEmpty()) {
                if (isMoreExpanded) {
                    items(
                        items = foldedApps,
                        key = { "folded_${it.componentKey}" },
                        contentType = { "drawer_folded_app" },
                    ) { app ->
                        Box(modifier = Modifier.alpha(0.72f)) {
                            AppIconTile(
                                app = app,
                                boundDirections = persistentSetOf(),
                                showLabel = true,
                                onWallpaper = false,
                                iconSize = iconSize,
                                onClick = { onAppClick(app) },
                                onLongClick = { appMenuHost.onOpen(app, AppActionOrigin.DRAWER, tabIndex) },
                                onSwipe = {},
                            )
                            AppContextMenuDropdown(
                                app = app,
                                origin = AppActionOrigin.DRAWER,
                                sourcePageIndex = tabIndex,
                                expanded = isCurrentPage && appMenuHost.isExpandedFor(app, AppActionOrigin.DRAWER, tabIndex),
                                host = appMenuHost,
                            )
                        }
                    }
                    if (primaryApps.isNotEmpty()) {
                        item(
                            key = "drawer_more_gap_$tabIndex",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "drawer_more_gap",
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                } else {
                    item(
                        key = "drawer_show_more_$tabIndex",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "drawer_show_more",
                    ) {
                        DrawerShowMoreButton(
                            count = foldedApps.size,
                            onClick = { isMoreExpanded = true },
                            modifier = Modifier.padding(
                                top = 2.dp,
                                bottom = if (primaryApps.isNotEmpty()) 8.dp else 0.dp,
                            ),
                        )
                    }
                }
            }

            items(
                items = primaryApps,
                key = { it.componentKey },
                contentType = { "drawer_app" },
            ) { app ->
                Box {
                    AppIconTile(
                        app = app,
                        boundDirections = persistentSetOf(),
                        showLabel = true,
                        onWallpaper = false,
                        iconSize = iconSize,
                        onClick = { onAppClick(app) },
                        onLongClick = { appMenuHost.onOpen(app, AppActionOrigin.DRAWER, tabIndex) },
                        onSwipe = {},
                    )
                    AppContextMenuDropdown(
                        app = app,
                        origin = AppActionOrigin.DRAWER,
                        sourcePageIndex = tabIndex,
                        expanded = isCurrentPage && appMenuHost.isExpandedFor(app, AppActionOrigin.DRAWER, tabIndex),
                        host = appMenuHost,
                    )
                }
            }
        } else {
            // Top-aligned Normal Mode: Primary zone ABOVE, Secondary ("More") zone BELOW.
            items(
                items = primaryApps,
                key = { it.componentKey },
                contentType = { "drawer_app" },
            ) { app ->
                Box {
                    AppIconTile(
                        app = app,
                        boundDirections = persistentSetOf(),
                        showLabel = true,
                        onWallpaper = false,
                        iconSize = iconSize,
                        onClick = { onAppClick(app) },
                        onLongClick = { appMenuHost.onOpen(app, AppActionOrigin.DRAWER, tabIndex) },
                        onSwipe = {},
                    )
                    AppContextMenuDropdown(
                        app = app,
                        origin = AppActionOrigin.DRAWER,
                        sourcePageIndex = tabIndex,
                        expanded = isCurrentPage && appMenuHost.isExpandedFor(app, AppActionOrigin.DRAWER, tabIndex),
                        host = appMenuHost,
                    )
                }
            }

            if (foldedApps.isNotEmpty()) {
                if (isMoreExpanded) {
                    if (primaryApps.isNotEmpty()) {
                        item(
                            key = "drawer_more_gap_$tabIndex",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "drawer_more_gap",
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                    items(
                        items = foldedApps,
                        key = { "folded_${it.componentKey}" },
                        contentType = { "drawer_folded_app" },
                    ) { app ->
                        Box(modifier = Modifier.alpha(0.72f)) {
                            AppIconTile(
                                app = app,
                                boundDirections = persistentSetOf(),
                                showLabel = true,
                                onWallpaper = false,
                                iconSize = iconSize,
                                onClick = { onAppClick(app) },
                                onLongClick = { appMenuHost.onOpen(app, AppActionOrigin.DRAWER, tabIndex) },
                                onSwipe = {},
                            )
                            AppContextMenuDropdown(
                                app = app,
                                origin = AppActionOrigin.DRAWER,
                                sourcePageIndex = tabIndex,
                                expanded = isCurrentPage && appMenuHost.isExpandedFor(app, AppActionOrigin.DRAWER, tabIndex),
                                host = appMenuHost,
                            )
                        }
                    }
                } else {
                    item(
                        key = "drawer_show_more_$tabIndex",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "drawer_show_more",
                    ) {
                        DrawerShowMoreButton(
                            count = foldedApps.size,
                            onClick = { isMoreExpanded = true },
                            modifier = Modifier.padding(
                                top = if (primaryApps.isNotEmpty()) 8.dp else 0.dp,
                                bottom = 2.dp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerShowMoreButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.testTag("drawer_show_more_button"),
        ) {
            Text(
                text = stringResource(R.string.drawer_show_more, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}
