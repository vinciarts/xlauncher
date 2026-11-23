package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.HomeGestureAction
import com.example.model.LauncherUiState
import com.example.model.SwipeDirection
import com.example.ui.components.CreateDrawerTabDialog
import com.example.ui.components.EditDrawerTabDialog
import com.example.ui.components.HomeGesturePickerSheet
import com.example.ui.components.homeGestureActionLabel
import com.example.ui.components.swipeDirectionLabel

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
    onSetDrawerColumns: (Int) -> Unit,
    onCreateDrawerTab: (String, List<String>) -> Unit,
    onRenameDrawerTab: (String, String) -> Unit,
    onSetDrawerTabApps: (String, Set<String>) -> Unit,
    onMoveDrawerTab: (String, Int) -> Unit,
    onSetAllTabHidden: (Boolean) -> Unit,
    onSetDrawerTabHidden: (String, Boolean) -> Unit,
    onDeleteDrawerTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val settings = uiState.settings
    var editingHomeGestureDir by remember { mutableStateOf<SwipeDirection?>(null) }
    var showCreateTabDialog by remember { mutableStateOf(false) }
    var editingTabId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings_title))
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
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .navigationBarsPadding(),
            ) {
                // Default Launcher
                item(key = "default_launcher") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_default_launcher),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(
                                        if (uiState.isDefaultLauncher) R.string.settings_default_yes
                                        else R.string.settings_default_no
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (uiState.isDefaultLauncher) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = onOpenDefaultHomeSettings,
                                modifier = Modifier.testTag("set_default_launcher_button"),
                            ) {
                                Text(stringResource(R.string.settings_switch_default))
                            }
                        }
                    }
                }

                // Home Screen Section
                item(key = "home_section") {
                    SettingsSectionCard(title = stringResource(R.string.settings_home_section)) {
                        Text(
                            text = stringResource(R.string.settings_home_pages),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val pageOptions = (1..com.example.data.LauncherPreferencesRepository.MAX_HOME_PAGES).toList()
                        ConnectedToggleGroup(
                            options = pageOptions,
                            selectedOption = settings.homePageCount,
                            labelFor = { "$it" },
                            testTagFor = { "home_pages_$it" },
                            onSelect = onSetHomePageCount,
                            compactPadding = true,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = stringResource(R.string.settings_home_columns),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val colOptions = listOf(4, 5)
                        ConnectedToggleGroup(
                            options = colOptions,
                            selectedOption = settings.homeGridColumns,
                            labelFor = { stringResource(R.string.grid_columns_option, it) },
                            testTagFor = { "home_cols_$it" },
                            onSelect = onSetHomeGridColumns,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_home_loop),
                            checked = settings.homeLoopPaging,
                            testTag = "switch_home_loop",
                            onCheckedChange = onSetHomeLoopPaging,
                        )
                    }
                }

                // 4-direction Home Desktop Gestures
                item(key = "home_gestures_section") {
                    SettingsSectionCard(title = stringResource(R.string.settings_home_gestures_section)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SwipeDirection.entries.forEach { dir ->
                                val action = settings.homeGestures[dir] ?: HomeGestureAction.None
                                val actionLabel = homeGestureActionLabel(
                                    direction = dir,
                                    action = action,
                                    appsByKey = uiState.appsByKey,
                                    shortcutsByTargetKey = uiState.shortcutsByTargetKey,
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { editingHomeGestureDir = dir }
                                        .testTag("home_gesture_${dir.name.lowercase()}"),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    ) {
                                        Text(
                                            text = swipeDirectionLabel(dir),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = actionLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Drawer Section
                item(key = "drawer_section") {
                    SettingsSectionCard(title = stringResource(R.string.settings_drawer_section)) {
                        Text(
                            text = stringResource(R.string.settings_drawer_columns),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ConnectedToggleGroup(
                            options = listOf(4, 5),
                            selectedOption = settings.drawerColumns,
                            labelFor = { stringResource(R.string.grid_columns_option, it) },
                            testTagFor = { "drawer_cols_$it" },
                            onSelect = onSetDrawerColumns,
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_drawer_tabs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                        }

                        val visibleTabCount = (if (!settings.isAllTabHidden) 1 else 0) +
                            settings.drawerTabs.count { !it.isHidden }

                        Text(
                            text = stringResource(R.string.drawer_tab_visibility_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Built-in "All" tab visibility row
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_drawer_tab_all"),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.drawer_tab_all),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (settings.isAllTabHidden) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val countText = stringResource(R.string.drawer_tab_app_count, uiState.allApps.size)
                                        Text(
                                            text = if (settings.isAllTabHidden) {
                                                "$countText · ${stringResource(R.string.drawer_tab_hidden_badge)}"
                                            } else {
                                                countText
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = !settings.isAllTabHidden,
                                        enabled = settings.isAllTabHidden || visibleTabCount > 1,
                                        onCheckedChange = { visible -> onSetAllTabHidden(!visible) },
                                        modifier = Modifier.testTag("switch_drawer_tab_all"),
                                    )
                                }
                            }

                            settings.drawerTabs.forEach { tab ->
                                val validCount = tab.appKeys.count { it in uiState.appsByKey }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { editingTabId = tab.id }
                                        .testTag("settings_drawer_tab_${tab.id}"),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tab.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (tab.isHidden) MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val countText = stringResource(R.string.drawer_tab_app_count, validCount)
                                            Text(
                                                text = if (tab.isHidden) {
                                                    "$countText · ${stringResource(R.string.drawer_tab_hidden_badge)}"
                                                } else {
                                                    countText
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Switch(
                                            checked = !tab.isHidden,
                                            enabled = tab.isHidden || visibleTabCount > 1,
                                            onCheckedChange = { visible -> onSetDrawerTabHidden(tab.id, !visible) },
                                            modifier = Modifier.testTag("switch_drawer_tab_${tab.id}"),
                                        )
                                    }
                                }
                            }
                        }

                        if (settings.drawerTabs.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.settings_drawer_tabs_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    val activeDir = editingHomeGestureDir
    if (activeDir != null) {
        HomeGesturePickerSheet(
            direction = activeDir,
            currentAction = settings.homeGestures[activeDir] ?: HomeGestureAction.None,
            allApps = uiState.allApps,
            allShortcuts = uiState.allShortcuts,
            hasShortcutHostPermission = uiState.hasShortcutHostPermission,
            onOpenDefaultHomeSettings = onOpenDefaultHomeSettings,
            onSelectAction = { action -> onSetHomeGesture(activeDir, action) },
            onDismiss = { editingHomeGestureDir = null },
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
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
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

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}
