package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppItem
import com.example.model.AppShortcutItem
import com.example.model.HomeGestureAction
import com.example.model.SwipeDirection
import com.example.search.PinyinSearchEngine

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppGestureConfigSheet(
    app: AppItem,
    currentBindings: Map<SwipeDirection, String>,
    allApps: List<AppItem>,
    appsByKey: Map<String, AppItem>,
    allShortcuts: List<AppShortcutItem>,
    shortcutsByTargetKey: Map<String, AppShortcutItem>,
    hasShortcutHostPermission: Boolean,
    onOpenDefaultHomeSettings: () -> Unit,
    onSetGesture: (SwipeDirection, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingDirection by remember { mutableStateOf<SwipeDirection?>(null) }
    var pickShortcutsTab by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    val sheetSegmentedColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            val activeDir = pickingDirection
            if (activeDir == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(app = app, size = 36.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.gesture_dialog_title, app.label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.gesture_dialog_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val directions = SwipeDirection.entries
                    directions.forEachIndexed { index, dir ->
                        val boundKey = currentBindings[dir]
                        val boundApp = boundKey?.let { appsByKey[it] }
                        val boundShortcut = boundKey?.let { shortcutsByTargetKey[it] }
                        SegmentedListItem(
                            onClick = {
                                filterQuery = ""
                                pickShortcutsTab = boundKey?.startsWith(AppShortcutItem.SHORTCUT_PREFIX) == true
                                pickingDirection = dir
                            },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = directions.size),
                            colors = sheetSegmentedColors,
                            leadingContent = {
                                Icon(
                                    imageVector = swipeDirectionIcon(dir),
                                    contentDescription = null,
                                )
                            },
                            supportingContent = {
                                when {
                                    boundApp != null -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AppIconImage(app = boundApp, size = 18.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = boundApp.label,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    boundShortcut != null -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            ShortcutIconImage(shortcut = boundShortcut, size = 18.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = boundShortcut.displayLabel,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    else -> {
                                        Text(text = stringResource(R.string.gesture_unbound))
                                    }
                                }
                            },
                            trailingContent = {
                                if (boundApp != null || boundShortcut != null) {
                                    IconButton(onClick = { onSetGesture(dir, null) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.gesture_clear),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier.testTag("gesture_row_${dir.name.lowercase()}"),
                            content = {
                                Text(text = swipeDirectionLabel(dir))
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dialog_done))
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = { pickingDirection = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                    Text(
                        text = stringResource(
                            if (pickShortcutsTab) R.string.gesture_pick_shortcut_title
                            else R.string.gesture_pick_app_title,
                            swipeDirectionLabel(activeDir),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab switch: Apps vs App Shortcuts
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ToggleButton(
                        checked = !pickShortcutsTab,
                        onCheckedChange = { checked ->
                            if (checked) {
                                pickShortcutsTab = false
                                filterQuery = ""
                            }
                        },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.gesture_tab_apps),
                            maxLines = 1,
                        )
                    }
                    ToggleButton(
                        checked = pickShortcutsTab,
                        onCheckedChange = { checked ->
                            if (checked) {
                                pickShortcutsTab = true
                                filterQuery = ""
                            }
                        },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.gesture_tab_shortcuts),
                            maxLines = 1,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!pickShortcutsTab || hasShortcutHostPermission) {
                    ExpressiveSearchField(
                        query = filterQuery,
                        onQueryChange = { filterQuery = it },
                        placeholder = stringResource(
                            if (pickShortcutsTab) R.string.search_shortcuts_placeholder
                            else R.string.search_apps_placeholder
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (pickShortcutsTab) {
                    ShortcutPickerContent(
                        allShortcuts = allShortcuts,
                        hasShortcutHostPermission = hasShortcutHostPermission,
                        selectedTargetKey = currentBindings[activeDir],
                        filterQuery = filterQuery,
                        preferredPackageName = app.packageName,
                        onSelectShortcut = { shortcut ->
                            onSetGesture(activeDir, shortcut.targetKey)
                            pickingDirection = null
                        },
                        onOpenDefaultHomeSettings = onOpenDefaultHomeSettings,
                    )
                } else {
                    val filtered = remember(allApps, filterQuery) {
                        val q = filterQuery.trim()
                        if (q.isEmpty()) allApps
                        else allApps.filter { PinyinSearchEngine.matches(q, it.label, it.packageName) }
                    }

                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalFadingEdge(listState),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        itemsIndexed(
                            items = filtered,
                            key = { _, candidate -> candidate.componentKey },
                        ) { index, candidate ->
                            val selected = currentBindings[activeDir] == candidate.componentKey
                            SegmentedListItem(
                                selected = selected,
                                onClick = {
                                    onSetGesture(activeDir, candidate.componentKey)
                                    pickingDirection = null
                                },
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = filtered.size),
                                colors = sheetSegmentedColors,
                                leadingContent = {
                                    AppIconImage(app = candidate, size = 32.dp)
                                },
                                trailingContent = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else {
                                    null
                                },
                                content = {
                                    Text(
                                        text = candidate.label,
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

@Composable
fun swipeDirectionLabel(direction: SwipeDirection): String = when (direction) {
    SwipeDirection.UP -> stringResource(R.string.gesture_up)
    SwipeDirection.DOWN -> stringResource(R.string.gesture_down)
    SwipeDirection.LEFT -> stringResource(R.string.gesture_left)
    SwipeDirection.RIGHT -> stringResource(R.string.gesture_right)
}

@Composable
fun homeGestureActionLabel(
    action: HomeGestureAction,
    appsByKey: Map<String, AppItem>,
    shortcutsByTargetKey: Map<String, AppShortcutItem>,
    direction: SwipeDirection? = null,
): String = when (action) {
    HomeGestureAction.None -> {
        if (direction == SwipeDirection.LEFT || direction == SwipeDirection.RIGHT) {
            stringResource(R.string.home_action_none_page)
        } else {
            stringResource(R.string.home_action_none)
        }
    }
    HomeGestureAction.OpenDrawer -> stringResource(R.string.home_action_open_drawer)
    HomeGestureAction.OpenSearch -> stringResource(R.string.home_action_open_search)
    HomeGestureAction.OpenSettings -> stringResource(R.string.home_action_open_settings)
    HomeGestureAction.GoFirstPage -> stringResource(R.string.home_action_go_first_page)
    HomeGestureAction.PrevHomePage -> stringResource(R.string.home_action_prev_page)
    HomeGestureAction.NextHomePage -> stringResource(R.string.home_action_next_page)
    HomeGestureAction.ExpandNotifications -> stringResource(R.string.home_action_expand_notifications)
    HomeGestureAction.ExpandQuickSettings -> stringResource(R.string.home_action_expand_quick_settings)
    HomeGestureAction.ToggleFlashlight -> stringResource(R.string.home_action_toggle_flashlight)
    HomeGestureAction.OpenVolumePanel -> stringResource(R.string.home_action_volume_panel)
    HomeGestureAction.ToggleVibrateMode -> stringResource(R.string.home_action_toggle_vibrate)
    HomeGestureAction.OpenVoiceAssist -> stringResource(R.string.home_action_voice_assist)
    HomeGestureAction.OpenCamera -> stringResource(R.string.home_action_open_camera)
    HomeGestureAction.OpenDialer -> stringResource(R.string.home_action_open_dialer)
    HomeGestureAction.OpenSystemSettings -> stringResource(R.string.home_action_system_settings)
    HomeGestureAction.OpenWifiPanel -> stringResource(R.string.home_action_wifi_panel)
    HomeGestureAction.OpenBluetoothSettings -> stringResource(R.string.home_action_bluetooth_settings)
    HomeGestureAction.OpenWallpaperPicker -> stringResource(R.string.home_action_wallpaper_picker)
    is HomeGestureAction.LaunchApp -> {
        val label = appsByKey[action.componentKey]?.label
        if (label != null) stringResource(R.string.home_action_launch_app, label)
        else stringResource(R.string.home_action_none)
    }
    is HomeGestureAction.LaunchShortcut -> {
        shortcutsByTargetKey[action.encode()]?.displayLabel
            ?: stringResource(R.string.home_action_shortcut_fallback)
    }
}

@Composable
fun HomeGestureActionIcon(
    action: HomeGestureAction,
    appsByKey: Map<String, AppItem>,
    shortcutsByTargetKey: Map<String, AppShortcutItem>,
    size: Dp = 22.dp,
) {
    when (action) {
        is HomeGestureAction.LaunchApp -> {
            val app = appsByKey[action.componentKey]
            if (app != null) {
                AppIconImage(app = app, size = size)
            } else {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(size),
                )
            }
        }
        is HomeGestureAction.LaunchShortcut -> {
            val shortcut = shortcutsByTargetKey[action.encode()]
            val iconBmp = shortcut?.icon ?: shortcut?.let { sc ->
                appsByKey.values.firstOrNull { it.packageName == sc.packageName }?.icon
            }
            if (iconBmp != null) {
                Image(
                    bitmap = iconBmp,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(size),
                )
            }
        }
        else -> {
            val vector = when (action) {
                HomeGestureAction.OpenDrawer -> Icons.Rounded.Apps
                HomeGestureAction.OpenSearch -> Icons.Rounded.Search
                HomeGestureAction.OpenSettings -> Icons.Rounded.Settings
                HomeGestureAction.GoFirstPage,
                HomeGestureAction.PrevHomePage,
                HomeGestureAction.NextHomePage,
                HomeGestureAction.None,
                -> Icons.Rounded.Home
                HomeGestureAction.ExpandNotifications -> Icons.Rounded.Notifications
                HomeGestureAction.ExpandQuickSettings -> Icons.Rounded.Tune
                HomeGestureAction.ToggleFlashlight -> Icons.Rounded.FlashlightOn
                HomeGestureAction.OpenVolumePanel -> Icons.AutoMirrored.Rounded.VolumeUp
                HomeGestureAction.ToggleVibrateMode -> Icons.Rounded.Vibration
                HomeGestureAction.OpenVoiceAssist -> Icons.Rounded.Mic
                HomeGestureAction.OpenCamera -> Icons.Rounded.PhotoCamera
                HomeGestureAction.OpenDialer -> Icons.Rounded.Call
                HomeGestureAction.OpenSystemSettings -> Icons.Rounded.Settings
                HomeGestureAction.OpenWifiPanel -> Icons.Rounded.Wifi
                HomeGestureAction.OpenBluetoothSettings -> Icons.Rounded.Bluetooth
                HomeGestureAction.OpenWallpaperPicker -> Icons.Rounded.Wallpaper
                is HomeGestureAction.LaunchApp,
                is HomeGestureAction.LaunchShortcut,
                -> Icons.Rounded.Apps
            }
            Icon(
                imageVector = vector,
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
    }
}

private enum class HomeGesturePickerMode {
    ACTIONS,
    PICK_APP,
    PICK_SHORTCUT,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeGesturePickerSheet(
    triggerLabel: String,
    currentAction: HomeGestureAction,
    allApps: List<AppItem>,
    allShortcuts: List<AppShortcutItem>,
    hasShortcutHostPermission: Boolean,
    onOpenDefaultHomeSettings: () -> Unit,
    onSelectAction: (HomeGestureAction) -> Unit,
    onDismiss: () -> Unit,
    direction: SwipeDirection? = null,
    includeLauncherActions: Boolean = true,
    titleOverride: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(HomeGesturePickerMode.ACTIONS) }
    var filterQuery by remember { mutableStateOf("") }
    val sheetSegmentedColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            when (mode) {
                HomeGesturePickerMode.ACTIONS -> {
                    Text(
                        text = titleOverride ?: stringResource(R.string.home_gesture_picker_title, triggerLabel),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    val launcherActions = HomeGestureAction.launcherActions
                    val systemActions = HomeGestureAction.systemActions

                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalFadingEdge(listState),
                    ) {
                        if (includeLauncherActions) {
                            item(key = "group_launcher") {
                                ExpressiveSectionHeader(title = stringResource(R.string.home_gesture_group_launcher))
                            }
                            itemsIndexed(
                                items = launcherActions,
                                key = { _, action -> action.encode() },
                            ) { index, action ->
                                val selected = currentAction == action
                                val label = homeGestureActionLabel(
                                    action = action,
                                    appsByKey = emptyMap(),
                                    shortcutsByTargetKey = emptyMap(),
                                    direction = direction,
                                )
                                SegmentedListItem(
                                    selected = selected,
                                    onClick = {
                                        onSelectAction(action)
                                        onDismiss()
                                    },
                                    shapes = ListItemDefaults.segmentedShapes(index = index, count = launcherActions.size),
                                    colors = sheetSegmentedColors,
                                    trailingContent = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    content = { Text(text = label) },
                                )
                            }
                        }

                        item(key = "group_system") {
                            ExpressiveSectionHeader(
                                title = stringResource(R.string.home_gesture_group_system),
                                modifier = Modifier.padding(top = if (includeLauncherActions) 8.dp else 0.dp),
                            )
                        }
                        itemsIndexed(
                            items = systemActions,
                            key = { _, action -> action.encode() },
                        ) { index, action ->
                            val selected = currentAction == action
                            val label = homeGestureActionLabel(
                                action = action,
                                appsByKey = emptyMap(),
                                shortcutsByTargetKey = emptyMap(),
                                direction = direction,
                            )
                            SegmentedListItem(
                                selected = selected,
                                onClick = {
                                    onSelectAction(action)
                                    onDismiss()
                                },
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = systemActions.size),
                                colors = sheetSegmentedColors,
                                trailingContent = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else {
                                    null
                                },
                                content = { Text(text = label) },
                            )
                        }

                        item(key = "group_app") {
                            ExpressiveSectionHeader(
                                title = stringResource(R.string.home_gesture_group_app),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        item(key = "pick_app_action") {
                            val selected = currentAction is HomeGestureAction.LaunchApp
                            SegmentedListItem(
                                selected = selected,
                                onClick = {
                                    filterQuery = ""
                                    mode = HomeGesturePickerMode.PICK_APP
                                },
                                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                                colors = sheetSegmentedColors,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                    )
                                },
                                content = { Text(text = stringResource(R.string.home_action_pick_app)) },
                            )
                        }
                        item(key = "pick_shortcut_action") {
                            val selected = currentAction is HomeGestureAction.LaunchShortcut
                            SegmentedListItem(
                                selected = selected,
                                onClick = {
                                    filterQuery = ""
                                    mode = HomeGesturePickerMode.PICK_SHORTCUT
                                },
                                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                                colors = sheetSegmentedColors,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                    )
                                },
                                content = { Text(text = stringResource(R.string.home_action_pick_shortcut)) },
                            )
                        }
                    }
                }

                HomeGesturePickerMode.PICK_APP,
                HomeGesturePickerMode.PICK_SHORTCUT,
                -> {
                    val isShortcutMode = mode == HomeGesturePickerMode.PICK_SHORTCUT
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = { mode = HomeGesturePickerMode.ACTIONS }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                        Text(
                            text = stringResource(
                                if (isShortcutMode) R.string.gesture_pick_shortcut_title
                                else R.string.gesture_pick_app_title,
                                triggerLabel,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isShortcutMode || hasShortcutHostPermission) {
                        ExpressiveSearchField(
                            query = filterQuery,
                            onQueryChange = { filterQuery = it },
                            placeholder = stringResource(
                                if (isShortcutMode) R.string.search_shortcuts_placeholder
                                else R.string.search_apps_placeholder
                            ),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (isShortcutMode) {
                        ShortcutPickerContent(
                            allShortcuts = allShortcuts,
                            hasShortcutHostPermission = hasShortcutHostPermission,
                            selectedTargetKey = (currentAction as? HomeGestureAction.LaunchShortcut)?.encode(),
                            filterQuery = filterQuery,
                            onSelectShortcut = { shortcut ->
                                onSelectAction(
                                    HomeGestureAction.LaunchShortcut(shortcut.packageName, shortcut.shortcutId)
                                )
                                onDismiss()
                            },
                            onOpenDefaultHomeSettings = onOpenDefaultHomeSettings,
                        )
                    } else {
                        val filtered = remember(allApps, filterQuery) {
                            val q = filterQuery.trim()
                            if (q.isEmpty()) allApps
                            else allApps.filter { PinyinSearchEngine.matches(q, it.label, it.packageName) }
                        }

                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalFadingEdge(listState),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            itemsIndexed(
                                items = filtered,
                                key = { _, candidate -> candidate.componentKey },
                            ) { index, candidate ->
                                val selected =
                                    (currentAction as? HomeGestureAction.LaunchApp)?.componentKey == candidate.componentKey
                                SegmentedListItem(
                                    selected = selected,
                                    onClick = {
                                        onSelectAction(HomeGestureAction.LaunchApp(candidate.componentKey))
                                        onDismiss()
                                    },
                                    shapes = ListItemDefaults.segmentedShapes(index = index, count = filtered.size),
                                    colors = sheetSegmentedColors,
                                    leadingContent = {
                                        AppIconImage(app = candidate, size = 32.dp)
                                    },
                                    trailingContent = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    content = {
                                        Text(
                                            text = candidate.label,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MoveToPageDialog(
    currentPageCount: Int,
    maxPages: Int,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val canAddNew = currentPageCount < maxPages
    val totalItems = currentPageCount + if (canAddNew) 1 else 0
    val dialogSegmentedColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.move_page_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                for (i in 0 until currentPageCount) {
                    SegmentedListItem(
                        onClick = { onSelectPage(i) },
                        shapes = ListItemDefaults.segmentedShapes(index = i, count = totalItems),
                        colors = dialogSegmentedColors,
                        modifier = Modifier.testTag("move_to_page_$i"),
                        content = {
                            Text(text = stringResource(R.string.page_number_label, i + 1))
                        },
                    )
                }
                if (canAddNew) {
                    SegmentedListItem(
                        onClick = { onSelectPage(currentPageCount) },
                        shapes = ListItemDefaults.segmentedShapes(index = currentPageCount, count = totalItems),
                        colors = dialogSegmentedColors,
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        modifier = Modifier.testTag("move_to_new_page"),
                        content = {
                            Text(text = stringResource(R.string.page_add_new, currentPageCount + 1))
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
