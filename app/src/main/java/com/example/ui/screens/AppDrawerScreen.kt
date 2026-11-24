package com.example.ui.screens

import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
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
import com.example.ui.components.ExpressiveSearchField
import com.example.ui.components.verticalFadingEdge
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerScreen(
    isActive: Boolean,
    allApps: ImmutableList<AppItem>,
    drawerTabs: ImmutableList<DrawerTab>,
    drawerTabPages: ImmutableList<DrawerTabPage>,
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
    onDeleteTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = isActive, onBack = onBack)

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
    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val pagingTouchSlopPx = viewConfig.scaledPagingTouchSlop.toFloat()
    val minFlingVelocityPx = viewConfig.scaledMinimumFlingVelocity.toFloat()
    val instantTriggerPx = with(density) { 48.dp.toPx() }.coerceAtLeast(pagingTouchSlopPx)

    val pages = drawerTabPages.ifEmpty {
        persistentListOf(
            DrawerTabPage(
                id = DrawerTab.ALL_TAB_ID,
                name = "",
                isAllTab = true,
                apps = allApps,
            )
        )
    }

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
                if (!currentIsActive || !startedAtTop) return Offset.Zero
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
                if (!currentIsActive || !startedAtTop) return Velocity.Zero
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
                triggered = !currentIsActive
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

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
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
                // Top search header; dragging or flinging down tracks finger 1:1 to close drawer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(directPullDownModifier),
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
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    )
                }

                if (drawerSearchQuery.isNotBlank()) {
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
                                cols = drawerColumns,
                                tabIndex = realPage,
                                isActive = isActive,
                                isCurrentPage = virtualPage == pagerState.currentPage,
                                onAppClick = onAppClick,
                                appMenuHost = appMenuHost,
                                onAtTopChanged = { atTop -> pageAtTopMap[realPage] = atTop },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    // Bottom native Material 3 Scrollable TabRow + quick Edit / Add tab buttons
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(directPullDownModifier),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 4.dp),
                        ) {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = currentPageIndex,
                                edgePadding = 8.dp,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                divider = {},
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("drawer_tab_row"),
                            ) {
                                pages.forEachIndexed { index, tabPage ->
                                    val title = if (tabPage.isAllTab) {
                                        stringResource(R.string.drawer_tab_all)
                                    } else {
                                        tabPage.name
                                    }
                                    Tab(
                                        selected = currentPageIndex == index,
                                        onClick = {
                                            if (loopEnabled && index != currentPageIndex) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(
                                                        pagerState.currentPage + (index - currentPageIndex)
                                                    )
                                                }
                                            }
                                        },
                                        text = {
                                            Text(
                                                text = title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        modifier = Modifier.testTag("drawer_tab_${tabPage.id}"),
                                    )
                                }
                            }

                            if (!currentTabPage.isAllTab) {
                                IconButton(
                                    onClick = { editingTabId = currentTabPage.id },
                                    modifier = Modifier.testTag("drawer_edit_tab_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.cd_edit_drawer_tab),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

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
                        }
                    }
                }
            }
        }
    }

    if (showCreateTabDialog) {
        CreateDrawerTabDialog(
            allApps = allApps,
            onConfirm = { name, appKeys ->
                showCreateTabDialog = false
                pendingSelectNewTabFromCount = pageCount
                onCreateTab(name, appKeys)
            },
            onDismiss = { showCreateTabDialog = false },
        )
    }

    if (editingTab != null) {
        val customIdx = drawerTabs.indexOfFirst { it.id == editingTab.id }
        EditDrawerTabDialog(
            tab = editingTab,
            canMoveLeft = customIdx > 0,
            canMoveRight = customIdx in 0 until drawerTabs.lastIndex,
            canHide = pages.size > 1,
            allApps = allApps,
            onSave = { newName, appKeys, isHidden ->
                if (newName != editingTab.name) {
                    onRenameTab(editingTab.id, newName)
                }
                onSetTabApps(editingTab.id, appKeys)
                if (isHidden != editingTab.isHidden) {
                    onSetTabHidden(editingTab.id, isHidden)
                }
                editingTabId = null
            },
            onMoveLeft = { onMoveTab(editingTab.id, -1) },
            onMoveRight = { onMoveTab(editingTab.id, +1) },
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
    cols: Int,
    tabIndex: Int,
    isActive: Boolean,
    isCurrentPage: Boolean,
    onAppClick: (AppItem) -> Unit,
    appMenuHost: AppContextMenuHost,
    onAtTopChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val iconSize = if (cols == 5) 48.dp else 52.dp
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow { !gridState.canScrollBackward }
            .collect { onAtTopChanged(it) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.verticalFadingEdge(gridState),
    ) {
        items(
            items = apps,
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
                    onLongClick = {
                        appMenuHost.onOpen(app, AppActionOrigin.DRAWER, tabIndex)
                    },
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
    }
}
