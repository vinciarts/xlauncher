package com.example.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LauncherSettingsBackup
import com.example.model.HomeGestureAction
import com.example.model.LauncherUiState
import com.example.model.SwipeDirection
import com.example.ui.components.CreateDrawerTabDialog
import com.example.ui.components.EditDrawerTabDialog
import com.example.ui.components.HomeGesturePickerSheet
import com.example.ui.components.SearchFoldersDialog
import com.example.ui.components.swipeDirectionLabel
import com.example.ui.components.verticalFadingEdge
import com.example.ui.screens.settings.HomeActionTrigger
import com.example.ui.screens.settings.generalSettingsItems
import com.example.ui.screens.settings.gestureSettingsItems
import com.example.ui.screens.settings.layoutSettingsItems
import com.example.ui.screens.settings.searchSettingsItems
import kotlinx.coroutines.launch

private enum class SettingsTab {
    GENERAL,
    LAYOUT,
    GESTURE,
    SEARCH,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    uiState: LauncherUiState,
    onBack: () -> Unit,
    onOpenDefaultHomeSettings: () -> Unit,
    onSetHomeLoopPaging: (Boolean) -> Unit,
    onSetHomeShowAppLabels: (Boolean) -> Unit,
    onSetHomeGridColumns: (Int) -> Unit,
    onSetHomeGridRows: (Int) -> Unit,
    onSetDockMaxItems: (Int) -> Unit,
    onSetIconShape: (com.example.model.IconShape) -> Unit,
    onSetHomeGesture: (SwipeDirection, HomeGestureAction) -> Unit,
    onSetHomeButtonAction: (HomeGestureAction) -> Unit,
    onSetDrawerColumns: (Int) -> Unit,
    onSetDrawerBottomAligned: (Boolean) -> Unit,
    onSetAutoHideEmptyDrawerTabs: (Boolean) -> Unit,
    onCreateDrawerTab: (String, List<String>) -> Unit,
    onRenameDrawerTab: (String, String) -> Unit,
    onSetDrawerTabApps: (String, Set<String>) -> Unit,
    onMoveDrawerTab: (String, Int) -> Unit,
    onSetAllTabHidden: (Boolean) -> Unit,
    onSetDrawerTabHidden: (String, Boolean) -> Unit,
    onDeleteDrawerTab: (String) -> Unit,
    onSetSearchShortcutsEnabled: (Boolean) -> Unit,
    onSetSearchContactsEnabled: (Boolean) -> Unit,
    onContactsPermissionDenied: () -> Unit,
    onSetSearchFilesEnabled: (Boolean) -> Unit,
    onAddSearchFileFolder: (Uri) -> Unit,
    onRemoveSearchFileFolder: (String) -> Unit,
    onSetSearchThirdPartyAppsEnabled: (Boolean) -> Unit,
    onSetSearchWebEnabled: (Boolean) -> Unit,
    onSetSearchWebEngine: (com.example.model.WebSearchEngine) -> Unit,
    onExportSettings: (Uri) -> Unit,
    onImportSettings: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val settings = uiState.settings
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editingActionTrigger by remember { mutableStateOf<HomeActionTrigger?>(null) }
    var showCreateTabDialog by remember { mutableStateOf(false) }
    var editingTabId by remember { mutableStateOf<String?>(null) }
    var showSearchFoldersDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val pagerState = rememberPagerState(pageCount = { SettingsTab.entries.size })
    val coroutineScope = rememberCoroutineScope()
    val segmentedColors = ListItemDefaults.segmentedColors()

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onSetSearchContactsEnabled(true)
        } else {
            onContactsPermissionDenied()
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            onAddSearchFileFolder(treeUri)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            onExportSettings(uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar {
                SettingsTab.entries.forEachIndexed { index, tab ->
                    val titleRes = when (tab) {
                        SettingsTab.GENERAL -> R.string.settings_tab_general
                        SettingsTab.LAYOUT -> R.string.settings_tab_layout
                        SettingsTab.GESTURE -> R.string.settings_tab_gesture
                        SettingsTab.SEARCH -> R.string.settings_tab_search
                    }
                    val icon = when (tab) {
                        SettingsTab.GENERAL -> Icons.Rounded.Tune
                        SettingsTab.LAYOUT -> Icons.Rounded.GridView
                        SettingsTab.GESTURE -> Icons.Rounded.Swipe
                        SettingsTab.SEARCH -> Icons.Rounded.Search
                    }
                    val label = stringResource(titleRes)
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.testTag("settings_tab_${tab.name.lowercase()}"),
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { pageIndex ->
            val selectedTab = SettingsTab.entries[pageIndex]
            val listState = rememberLazyListState()
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .verticalFadingEdge(listState),
                ) {
                    when (selectedTab) {
                        SettingsTab.GENERAL -> generalSettingsItems(
                            isDefaultLauncher = uiState.isDefaultLauncher,
                            segmentedColors = segmentedColors,
                            onOpenDefaultHomeSettings = onOpenDefaultHomeSettings,
                            onLaunchExportBackup = {
                                exportBackupLauncher.launch(LauncherSettingsBackup.defaultFileName())
                            },
                            onLaunchImportBackup = {
                                importBackupLauncher.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                                )
                            },
                        )

                        SettingsTab.LAYOUT -> layoutSettingsItems(
                            uiState = uiState,
                            segmentedColors = segmentedColors,
                            onSetHomeGridColumns = onSetHomeGridColumns,
                            onSetHomeGridRows = onSetHomeGridRows,
                            onSetDockMaxItems = onSetDockMaxItems,
                            onSetHomeLoopPaging = onSetHomeLoopPaging,
                            onSetHomeShowAppLabels = onSetHomeShowAppLabels,
                            onSetIconShape = onSetIconShape,
                            onSetDrawerColumns = onSetDrawerColumns,
                            onSetDrawerBottomAligned = onSetDrawerBottomAligned,
                            onSetAutoHideEmptyDrawerTabs = onSetAutoHideEmptyDrawerTabs,
                            onSetAllTabHidden = onSetAllTabHidden,
                            onSetDrawerTabHidden = onSetDrawerTabHidden,
                            onOpenCreateTabDialog = { showCreateTabDialog = true },
                            onEditTab = { editingTabId = it },
                        )

                        SettingsTab.GESTURE -> gestureSettingsItems(
                            uiState = uiState,
                            segmentedColors = segmentedColors,
                            onEditTrigger = { editingActionTrigger = it },
                        )

                        SettingsTab.SEARCH -> searchSettingsItems(
                            context = context,
                            uiState = uiState,
                            segmentedColors = segmentedColors,
                            onSetSearchShortcutsEnabled = onSetSearchShortcutsEnabled,
                            onSetSearchContactsEnabled = onSetSearchContactsEnabled,
                            onRequestContactsPermission = {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            },
                            onSetSearchFilesEnabled = onSetSearchFilesEnabled,
                            onOpenFolderPicker = { folderPickerLauncher.launch(null) },
                            onOpenSearchFoldersDialog = { showSearchFoldersDialog = true },
                            onSetSearchThirdPartyAppsEnabled = onSetSearchThirdPartyAppsEnabled,
                            onSetSearchWebEnabled = onSetSearchWebEnabled,
                            onSetSearchWebEngine = onSetSearchWebEngine,
                        )
                    }
                }
            }
        }
    }

    val activeTrigger = editingActionTrigger
    if (activeTrigger != null) {
        val triggerLabel = when (activeTrigger) {
            is HomeActionTrigger.Swipe -> swipeDirectionLabel(activeTrigger.direction)
            HomeActionTrigger.HomeButton -> stringResource(R.string.settings_home_button_action)
        }
        val currentAction = when (activeTrigger) {
            is HomeActionTrigger.Swipe -> settings.homeGestures[activeTrigger.direction] ?: HomeGestureAction.None
            HomeActionTrigger.HomeButton -> settings.homeButtonAction
        }
        val direction = (activeTrigger as? HomeActionTrigger.Swipe)?.direction
        HomeGesturePickerSheet(
            triggerLabel = triggerLabel,
            currentAction = currentAction,
            allApps = uiState.allApps,
            allShortcuts = uiState.allShortcuts,
            hasShortcutHostPermission = uiState.hasShortcutHostPermission,
            onOpenDefaultHomeSettings = onOpenDefaultHomeSettings,
            onSelectAction = { action ->
                when (activeTrigger) {
                    is HomeActionTrigger.Swipe -> onSetHomeGesture(activeTrigger.direction, action)
                    HomeActionTrigger.HomeButton -> onSetHomeButtonAction(action)
                }
            },
            onDismiss = { editingActionTrigger = null },
            direction = direction,
        )
    }

    if (showCreateTabDialog) {
        CreateDrawerTabDialog(
            onConfirm = { name ->
                showCreateTabDialog = false
                onCreateDrawerTab(name, emptyList())
            },
            onDismiss = { showCreateTabDialog = false },
        )
    }

    val editingTab = editingTabId?.let { id -> settings.drawerTabs.firstOrNull { it.id == id } }
    if (editingTab != null) {
        val visibleTabCount = (if (!settings.isAllTabHidden) 1 else 0) +
            settings.drawerTabs.count { !it.isHidden }
        EditDrawerTabDialog(
            tabKey = editingTab.id,
            initialName = editingTab.name,
            initialHidden = editingTab.isHidden,
            canHide = visibleTabCount > 1,
            canDelete = true,
            onSave = { newName, isHidden ->
                if (newName != editingTab.name) {
                    onRenameDrawerTab(editingTab.id, newName)
                }
                if (isHidden != editingTab.isHidden) {
                    onSetDrawerTabHidden(editingTab.id, isHidden)
                }
                editingTabId = null
            },
            onDelete = {
                editingTabId = null
                onDeleteDrawerTab(editingTab.id)
            },
            onDismiss = { editingTabId = null },
        )
    }

    if (showSearchFoldersDialog) {
        SearchFoldersDialog(
            folders = uiState.indexedFolders,
            onAddFolder = { folderPickerLauncher.launch(null) },
            onRemoveFolder = onRemoveSearchFileFolder,
            onDismiss = { showSearchFoldersDialog = false },
        )
    }

    val restoreUri = pendingRestoreUri
    if (restoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = {
                Text(text = stringResource(R.string.backup_restore_confirm_title))
            },
            text = {
                Text(text = stringResource(R.string.backup_restore_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri = null
                        onImportSettings(restoreUri)
                    },
                    modifier = Modifier.testTag("confirm_restore_backup"),
                ) {
                    Text(text = stringResource(R.string.backup_restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRestoreUri = null },
                    modifier = Modifier.testTag("cancel_restore_backup"),
                ) {
                    Text(text = stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}
