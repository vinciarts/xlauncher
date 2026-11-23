package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.LauncherPreferencesRepository
import com.example.model.HomeGestureAction
import com.example.model.LauncherUiState
import com.example.model.SwipeDirection
import com.example.search.PinyinSearchEngine
import com.example.ui.components.CreateDrawerTabDialog
import com.example.ui.components.EditDrawerTabDialog
import com.example.ui.components.ExpressiveSearchField
import com.example.ui.components.ExpressiveSectionHeader
import com.example.ui.components.HomeGesturePickerSheet
import com.example.ui.components.SearchFoldersDialog
import com.example.ui.components.homeGestureActionLabel
import com.example.ui.components.swipeDirectionIcon
import com.example.ui.components.swipeDirectionLabel

private sealed interface HomeActionTrigger {
    data class Swipe(val direction: SwipeDirection) : HomeActionTrigger
    data object HomeButton : HomeActionTrigger
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    uiState: LauncherUiState,
    onBack: () -> Unit,
    onOpenDefaultHomeSettings: () -> Unit,
    onSetHomePageCount: (Int) -> Unit,
    onSetHomeLoopPaging: (Boolean) -> Unit,
    onSetHomeGridColumns: (Int) -> Unit,
    onSetHomeGesture: (SwipeDirection, HomeGestureAction) -> Unit,
    onSetHomeButtonAction: (HomeGestureAction) -> Unit,
    onSetDrawerColumns: (Int) -> Unit,
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
    onSetSearchThirdPartyAppEnabled: (String, Boolean) -> Unit,
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
    var providerFilterQuery by remember { mutableStateOf("") }
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
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .navigationBarsPadding(),
            ) {
                // Default Launcher
                item(key = "default_launcher") {
                    SegmentedListItem(
                        onClick = onOpenDefaultHomeSettings,
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                        colors = segmentedColors,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Home,
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(
                                    if (uiState.isDefaultLauncher) R.string.settings_default_yes
                                    else R.string.settings_default_no
                                ),
                                color = if (uiState.isDefaultLauncher) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            if (uiState.isDefaultLauncher) {
                                FilledTonalButton(
                                    onClick = onOpenDefaultHomeSettings,
                                    modifier = Modifier.testTag("set_default_launcher_button"),
                                ) {
                                    Text(stringResource(R.string.settings_switch_default))
                                }
                            } else {
                                Button(
                                    onClick = onOpenDefaultHomeSettings,
                                    modifier = Modifier.testTag("set_default_launcher_button"),
                                ) {
                                    Text(stringResource(R.string.settings_switch_default))
                                }
                            }
                        },
                        content = {
                            Text(text = stringResource(R.string.settings_default_launcher))
                        },
                    )
                }

                // Home Screen Section
                item(key = "home_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ExpressiveSectionHeader(title = stringResource(R.string.settings_home_section))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Row 0: Home page count
                            SegmentedControlContainer(
                                index = 0,
                                count = 3,
                                containerColor = segmentedColors.containerColor,
                                title = stringResource(R.string.settings_home_pages),
                            ) {
                                val pageOptions = (1..LauncherPreferencesRepository.MAX_HOME_PAGES).toList()
                                ConnectedToggleGroup(
                                    options = pageOptions,
                                    selectedOption = settings.homePageCount,
                                    labelFor = { "$it" },
                                    testTagFor = { "home_pages_$it" },
                                    onSelect = onSetHomePageCount,
                                    compactPadding = true,
                                )
                            }

                            // Row 1: Home grid columns
                            SegmentedControlContainer(
                                index = 1,
                                count = 3,
                                containerColor = segmentedColors.containerColor,
                                title = stringResource(R.string.settings_home_columns),
                            ) {
                                ConnectedToggleGroup(
                                    options = listOf(4, 5),
                                    selectedOption = settings.homeGridColumns,
                                    labelFor = { stringResource(R.string.grid_columns_option, it) },
                                    testTagFor = { "home_cols_$it" },
                                    onSelect = onSetHomeGridColumns,
                                )
                            }

                            // Row 2: Loop paging toggle
                            SegmentedListItem(
                                checked = settings.homeLoopPaging,
                                onCheckedChange = onSetHomeLoopPaging,
                                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                                colors = segmentedColors,
                                trailingContent = {
                                    Switch(
                                        checked = settings.homeLoopPaging,
                                        onCheckedChange = null,
                                        modifier = Modifier.testTag("switch_home_loop"),
                                    )
                                },
                                content = {
                                    Text(text = stringResource(R.string.settings_home_loop))
                                },
                            )
                        }
                    }
                }

                // 4-direction Home Desktop Gestures + Home Button Action
                item(key = "home_gestures_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ExpressiveSectionHeader(title = stringResource(R.string.settings_home_gestures_section))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val directions = SwipeDirection.entries
                            val totalRows = directions.size + 1
                            directions.forEachIndexed { index, dir ->
                                val action = settings.homeGestures[dir] ?: HomeGestureAction.None
                                val actionLabel = homeGestureActionLabel(
                                    action = action,
                                    appsByKey = uiState.appsByKey,
                                    shortcutsByTargetKey = uiState.shortcutsByTargetKey,
                                    direction = dir,
                                )
                                SegmentedListItem(
                                    onClick = { editingActionTrigger = HomeActionTrigger.Swipe(dir) },
                                    shapes = ListItemDefaults.segmentedShapes(index = index, count = totalRows),
                                    colors = segmentedColors,
                                    leadingContent = {
                                        Icon(
                                            imageVector = swipeDirectionIcon(dir),
                                            contentDescription = null,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = actionLabel,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                        )
                                    },
                                    modifier = Modifier.testTag("home_gesture_${dir.name.lowercase()}"),
                                    content = {
                                        Text(text = swipeDirectionLabel(dir))
                                    },
                                )
                            }

                            val homeBtnActionLabel = homeGestureActionLabel(
                                action = settings.homeButtonAction,
                                appsByKey = uiState.appsByKey,
                                shortcutsByTargetKey = uiState.shortcutsByTargetKey,
                            )
                            SegmentedListItem(
                                onClick = { editingActionTrigger = HomeActionTrigger.HomeButton },
                                shapes = ListItemDefaults.segmentedShapes(index = directions.size, count = totalRows),
                                colors = segmentedColors,
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Home,
                                        contentDescription = null,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = homeBtnActionLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.testTag("home_button_action"),
                                content = {
                                    Text(text = stringResource(R.string.settings_home_button_action))
                                },
                            )
                        }
                    }
                }

                // Drawer Columns Section
                item(key = "drawer_columns_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ExpressiveSectionHeader(title = stringResource(R.string.settings_drawer_section))
                        SegmentedControlContainer(
                            index = 0,
                            count = 1,
                            containerColor = segmentedColors.containerColor,
                            title = stringResource(R.string.settings_drawer_columns),
                        ) {
                            ConnectedToggleGroup(
                                options = listOf(4, 5),
                                selectedOption = settings.drawerColumns,
                                labelFor = { stringResource(R.string.grid_columns_option, it) },
                                testTagFor = { "drawer_cols_$it" },
                                onSelect = onSetDrawerColumns,
                            )
                        }
                    }
                }

                // Drawer Tabs Section
                item(key = "drawer_tabs_section") {
                    val visibleTabCount = (if (!settings.isAllTabHidden) 1 else 0) +
                        settings.drawerTabs.count { !it.isHidden }
                    val totalTabs = 1 + settings.drawerTabs.size

                    Column(modifier = Modifier.fillMaxWidth()) {
                        ExpressiveSectionHeader(
                            title = stringResource(R.string.settings_drawer_tabs),
                            action = {
                                TextButton(
                                    onClick = { showCreateTabDialog = true },
                                    modifier = Modifier.testTag("settings_add_drawer_tab"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = stringResource(R.string.drawer_tab_create_title))
                                }
                            },
                        )

                        Text(
                            text = stringResource(R.string.drawer_tab_visibility_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val allCountText = stringResource(R.string.drawer_tab_app_count, uiState.allApps.size)
                            val allCanToggle = settings.isAllTabHidden || visibleTabCount > 1
                            SegmentedListItem(
                                checked = !settings.isAllTabHidden,
                                onCheckedChange = { visible -> onSetAllTabHidden(!visible) },
                                enabled = allCanToggle,
                                shapes = ListItemDefaults.segmentedShapes(index = 0, count = totalTabs),
                                colors = segmentedColors,
                                supportingContent = {
                                    Text(
                                        text = if (settings.isAllTabHidden) {
                                            "$allCountText · ${stringResource(R.string.drawer_tab_hidden_badge)}"
                                        } else {
                                            allCountText
                                        },
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = !settings.isAllTabHidden,
                                        enabled = allCanToggle,
                                        onCheckedChange = null,
                                        modifier = Modifier.testTag("switch_drawer_tab_all"),
                                    )
                                },
                                modifier = Modifier.testTag("settings_drawer_tab_all"),
                                content = {
                                    Text(
                                        text = stringResource(R.string.drawer_tab_all),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )

                            settings.drawerTabs.forEachIndexed { idx, tab ->
                                val validCount = tab.appKeys.count { it in uiState.appsByKey }
                                val countText = stringResource(R.string.drawer_tab_app_count, validCount)
                                SegmentedListItem(
                                    onClick = { editingTabId = tab.id },
                                    shapes = ListItemDefaults.segmentedShapes(index = idx + 1, count = totalTabs),
                                    colors = segmentedColors,
                                    supportingContent = {
                                        Text(
                                            text = if (tab.isHidden) {
                                                "$countText · ${stringResource(R.string.drawer_tab_hidden_badge)}"
                                            } else {
                                                countText
                                            },
                                        )
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = !tab.isHidden,
                                            enabled = tab.isHidden || visibleTabCount > 1,
                                            onCheckedChange = { visible -> onSetDrawerTabHidden(tab.id, !visible) },
                                            modifier = Modifier.testTag("switch_drawer_tab_${tab.id}"),
                                        )
                                    },
                                    modifier = Modifier.testTag("settings_drawer_tab_${tab.id}"),
                                    content = {
                                        Text(
                                            text = tab.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }

                        if (settings.drawerTabs.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_drawer_tabs_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            )
                        }
                    }
                }

                // Opt-in Universal Search Sources Section (all OFF by default)
                item(key = "search_sources_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ExpressiveSectionHeader(title = stringResource(R.string.settings_search_section))
                        Text(
                            text = stringResource(R.string.settings_search_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // 0: App shortcuts
                            SegmentedListItem(
                                checked = settings.searchShortcutsEnabled,
                                onCheckedChange = onSetSearchShortcutsEnabled,
                                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
                                colors = segmentedColors,
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = stringResource(
                                            if (uiState.hasShortcutHostPermission) {
                                                R.string.settings_search_shortcuts_desc
                                            } else {
                                                R.string.settings_search_shortcuts_need_default
                                            }
                                        ),
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = settings.searchShortcutsEnabled,
                                        onCheckedChange = null,
                                        modifier = Modifier.testTag("switch_search_shortcuts"),
                                    )
                                },
                                modifier = Modifier.testTag("settings_search_shortcuts"),
                                content = {
                                    Text(text = stringResource(R.string.settings_search_shortcuts))
                                },
                            )

                            // 1: Contacts (runtime READ_CONTACTS requested only when enabled)
                            SegmentedListItem(
                                checked = settings.searchContactsEnabled,
                                onCheckedChange = { enable ->
                                    if (!enable) {
                                        onSetSearchContactsEnabled(false)
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.READ_CONTACTS,
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            onSetSearchContactsEnabled(true)
                                        } else {
                                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                        }
                                    }
                                },
                                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 3),
                                colors = segmentedColors,
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Contacts,
                                        contentDescription = null,
                                    )
                                },
                                supportingContent = {
                                    Text(text = stringResource(R.string.settings_search_contacts_desc))
                                },
                                trailingContent = {
                                    Switch(
                                        checked = settings.searchContactsEnabled,
                                        onCheckedChange = null,
                                        modifier = Modifier.testTag("switch_search_contacts"),
                                    )
                                },
                                modifier = Modifier.testTag("settings_search_contacts"),
                                content = {
                                    Text(text = stringResource(R.string.settings_search_contacts))
                                },
                            )

                            // 2: Local files via SAF OpenDocumentTree (zero broad storage permissions)
                            val filesSubtitle = if (uiState.indexedFolders.isEmpty()) {
                                stringResource(R.string.settings_search_files_empty)
                            } else {
                                stringResource(
                                    R.string.settings_search_files_summary,
                                    uiState.indexedFolders.size,
                                    uiState.indexedFileCount,
                                )
                            }
                            SegmentedListItem(
                                onClick = { showSearchFoldersDialog = true },
                                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                                colors = segmentedColors,
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = null,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = filesSubtitle,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = settings.searchFilesEnabled && uiState.indexedFolders.isNotEmpty(),
                                        onCheckedChange = { enable ->
                                            if (enable && uiState.indexedFolders.isEmpty()) {
                                                folderPickerLauncher.launch(null)
                                            } else {
                                                onSetSearchFilesEnabled(enable)
                                            }
                                        },
                                        modifier = Modifier.testTag("switch_search_files"),
                                    )
                                },
                                modifier = Modifier.testTag("settings_search_files"),
                                content = {
                                    Text(text = stringResource(R.string.settings_search_files))
                                },
                            )
                        }
                    }
                }

                // Per-app Third-Party Search Providers Section (each app toggled individually, all OFF by default)
                item(key = "third_party_search_section") {
                    val providers = uiState.availableSearchProviders
                    val enabledSet = remember(settings.searchThirdPartyPackages) {
                        settings.searchThirdPartyPackages.toSet()
                    }
                    val filteredProviders = remember(providers, providerFilterQuery) {
                        val q = providerFilterQuery.trim()
                        if (q.isEmpty()) providers
                        else providers.filter { PinyinSearchEngine.matches(q, it.label, it.packageName) }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        ExpressiveSectionHeader(title = stringResource(R.string.settings_search_third_party_section))
                        Text(
                            text = stringResource(
                                if (providers.isEmpty()) R.string.settings_search_third_party_none_installed
                                else R.string.settings_search_third_party_section_hint
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )

                        if (providers.size > 6) {
                            ExpressiveSearchField(
                                query = providerFilterQuery,
                                onQueryChange = { providerFilterQuery = it },
                                placeholder = stringResource(R.string.search_providers_placeholder),
                                inputTestTag = "settings_search_providers_filter",
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }

                        if (filteredProviders.isNotEmpty()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                filteredProviders.forEachIndexed { idx, provider ->
                                    val enabled = provider.packageName in enabledSet
                                    SegmentedListItem(
                                        checked = enabled,
                                        onCheckedChange = { checked ->
                                            onSetSearchThirdPartyAppEnabled(provider.packageName, checked)
                                        },
                                        shapes = ListItemDefaults.segmentedShapes(
                                            index = idx,
                                            count = filteredProviders.size,
                                        ),
                                        colors = segmentedColors,
                                        leadingContent = {
                                            if (provider.icon != null) {
                                                Image(
                                                    bitmap = provider.icon,
                                                    contentDescription = provider.label,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.size(32.dp),
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.Search,
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                        supportingContent = {
                                            Text(
                                                text = stringResource(
                                                    if (provider.action == Intent.ACTION_WEB_SEARCH) {
                                                        R.string.settings_search_app_web_desc
                                                    } else {
                                                        R.string.settings_search_app_inapp_desc
                                                    }
                                                ),
                                            )
                                        },
                                        trailingContent = {
                                            Switch(
                                                checked = enabled,
                                                onCheckedChange = null,
                                                modifier = Modifier.testTag("switch_search_provider_${provider.packageName}"),
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_search_provider_${provider.packageName}"),
                                        content = {
                                            Text(
                                                text = provider.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                    )
                                }
                            }
                        }
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
            allApps = uiState.allApps,
            onConfirm = { name, appKeys ->
                showCreateTabDialog = false
                onCreateDrawerTab(name, appKeys)
            },
            onDismiss = { showCreateTabDialog = false },
        )
    }

    val editingTab = editingTabId?.let { id -> settings.drawerTabs.firstOrNull { it.id == id } }
    if (editingTab != null) {
        val idx = settings.drawerTabs.indexOfFirst { it.id == editingTab.id }
        val visibleTabCount = (if (!settings.isAllTabHidden) 1 else 0) +
            settings.drawerTabs.count { !it.isHidden }
        EditDrawerTabDialog(
            tab = editingTab,
            canMoveLeft = idx > 0,
            canMoveRight = idx in 0 until settings.drawerTabs.lastIndex,
            canHide = visibleTabCount > 1,
            allApps = uiState.allApps,
            onSave = { newName, appKeys, isHidden ->
                if (newName != editingTab.name) {
                    onRenameDrawerTab(editingTab.id, newName)
                }
                onSetDrawerTabApps(editingTab.id, appKeys)
                if (isHidden != editingTab.isHidden) {
                    onSetDrawerTabHidden(editingTab.id, isHidden)
                }
                editingTabId = null
            },
            onMoveLeft = { onMoveDrawerTab(editingTab.id, -1) },
            onMoveRight = { onMoveDrawerTab(editingTab.id, +1) },
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
}

/**
 * Non-clickable segmented item container using official [ListItemDefaults.segmentedShapes]
 * for hosting interactive controls like [ConnectedToggleGroup] inside a segmented list group.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SegmentedControlContainer(
    index: Int,
    count: Int,
    containerColor: androidx.compose.ui.graphics.Color,
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedToggleGroup(
    options: List<T>,
    selectedOption: T,
    labelFor: @Composable (T) -> String,
    testTagFor: (T) -> String,
    onSelect: (T) -> Unit,
    compactPadding: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, option ->
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = selectedOption == option,
                onCheckedChange = { checked ->
                    if (checked) onSelect(option)
                },
                shapes = shapes,
                contentPadding = if (compactPadding) {
                    PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                } else {
                    ToggleButtonDefaults.ContentPadding
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(testTagFor(option)),
            ) {
                Text(
                    text = labelFor(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
