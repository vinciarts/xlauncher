package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppItem
import com.example.model.AppShortcutItem
import com.example.model.HomeGestureAction
import com.example.model.SwipeDirection

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

                SwipeDirection.entries.forEach { dir ->
                    val boundKey = currentBindings[dir]
                    val boundApp = boundKey?.let { appsByKey[it] }
                    val boundShortcut = boundKey?.let { shortcutsByTargetKey[it] }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                filterQuery = ""
                                pickShortcutsTab = boundKey?.startsWith(AppShortcutItem.SHORTCUT_PREFIX) == true
                                pickingDirection = dir
                            }
                            .testTag("gesture_row_${dir.name.lowercase()}"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = swipeDirectionLabel(dir),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(74.dp),
                            )
                            when {
                                boundApp != null -> {
                                    AppIconImage(app = boundApp, size = 28.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = boundApp.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = { onSetGesture(dir, null) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.gesture_clear),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }

                                boundShortcut != null -> {
                                    ShortcutIconImage(shortcut = boundShortcut, size = 28.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = boundShortcut.displayLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = { onSetGesture(dir, null) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.gesture_clear),
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }

                                else -> {
                                    Text(
                                        text = stringResource(R.string.gesture_unbound),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
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
                    SheetSearchBar(
                        query = filterQuery,
                        placeholder = stringResource(
                            if (pickShortcutsTab) R.string.search_shortcuts_placeholder
                            else R.string.search_apps_placeholder
                        ),
                        onQueryChange = { filterQuery = it },
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
                        else allApps.filter {
                            it.label.contains(q, ignoreCase = true) ||
                                it.packageName.contains(q, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(items = filtered, key = { it.componentKey }) { candidate ->
                            val selected = currentBindings[activeDir] == candidate.componentKey
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        onSetGesture(activeDir, candidate.componentKey)
                                        pickingDirection = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                            ) {
                                AppIconImage(app = candidate, size = 34.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = candidate.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
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
fun swipeDirectionLabel(direction: SwipeDirection): String = when (direction) {
    SwipeDirection.UP -> stringResource(R.string.gesture_up)
    SwipeDirection.DOWN -> stringResource(R.string.gesture_down)
    SwipeDirection.LEFT -> stringResource(R.string.gesture_left)
    SwipeDirection.RIGHT -> stringResource(R.string.gesture_right)
}

@Composable
fun homeGestureActionLabel(
    direction: SwipeDirection,
    action: HomeGestureAction,
    appsByKey: Map<String, AppItem>,
    shortcutsByTargetKey: Map<String, AppShortcutItem>,
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

private enum class HomeGesturePickerMode {
    ACTIONS,
    PICK_APP,
    PICK_SHORTCUT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeGesturePickerSheet(
    direction: SwipeDirection,
    currentAction: HomeGestureAction,
    allApps: List<AppItem>,
    allShortcuts: List<AppShortcutItem>,
    hasShortcutHostPermission: Boolean,
    onOpenDefaultHomeSettings: () -> Unit,
    onSelectAction: (HomeGestureAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(HomeGesturePickerMode.ACTIONS) }
    var filterQuery by remember { mutableStateOf("") }

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
                        text = stringResource(R.string.home_gesture_picker_title, swipeDirectionLabel(direction)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    val launcherActions = listOf(
                        HomeGestureAction.None to if (direction == SwipeDirection.LEFT || direction == SwipeDirection.RIGHT) {
                            stringResource(R.string.home_action_none_page)
                        } else {
                            stringResource(R.string.home_action_none)
                        },
                        HomeGestureAction.OpenDrawer to stringResource(R.string.home_action_open_drawer),
                        HomeGestureAction.OpenSearch to stringResource(R.string.home_action_open_search),
                        HomeGestureAction.OpenSettings to stringResource(R.string.home_action_open_settings),
                        HomeGestureAction.GoFirstPage to stringResource(R.string.home_action_go_first_page),
                        HomeGestureAction.PrevHomePage to stringResource(R.string.home_action_prev_page),
                        HomeGestureAction.NextHomePage to stringResource(R.string.home_action_next_page),
                    )

                    val systemActions = listOf(
                        HomeGestureAction.ExpandNotifications to stringResource(R.string.home_action_expand_notifications),
                        HomeGestureAction.ExpandQuickSettings to stringResource(R.string.home_action_expand_quick_settings),
                        HomeGestureAction.ToggleFlashlight to stringResource(R.string.home_action_toggle_flashlight),
                        HomeGestureAction.OpenVolumePanel to stringResource(R.string.home_action_volume_panel),
                        HomeGestureAction.ToggleVibrateMode to stringResource(R.string.home_action_toggle_vibrate),
                        HomeGestureAction.OpenVoiceAssist to stringResource(R.string.home_action_voice_assist),
                        HomeGestureAction.OpenCamera to stringResource(R.string.home_action_open_camera),
                        HomeGestureAction.OpenDialer to stringResource(R.string.home_action_open_dialer),
                        HomeGestureAction.OpenWifiPanel to stringResource(R.string.home_action_wifi_panel),
                        HomeGestureAction.OpenBluetoothSettings to stringResource(R.string.home_action_bluetooth_settings),
                        HomeGestureAction.OpenWallpaperPicker to stringResource(R.string.home_action_wallpaper_picker),
                        HomeGestureAction.OpenSystemSettings to stringResource(R.string.home_action_system_settings),
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                    ) {
                        item(key = "group_launcher") {
                            Text(
                                text = stringResource(R.string.home_gesture_group_launcher),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 4.dp),
                            )
                        }
                        items(items = launcherActions, key = { it.first.encode() }) { (action, label) ->
                            HomeGestureActionRow(
                                label = label,
                                selected = currentAction == action,
                                onClick = {
                                    onSelectAction(action)
                                    onDismiss()
                                },
                            )
                        }

                        item(key = "group_system") {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            Text(
                                text = stringResource(R.string.home_gesture_group_system),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                            )
                        }
                        items(items = systemActions, key = { it.first.encode() }) { (action, label) ->
                            HomeGestureActionRow(
                                label = label,
                                selected = currentAction == action,
                                onClick = {
                                    onSelectAction(action)
                                    onDismiss()
                                },
                            )
                        }

                        item(key = "group_app") {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            Text(
                                text = stringResource(R.string.home_gesture_group_app),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                            )
                            HomeGestureActionRow(
                                label = stringResource(R.string.home_action_pick_app),
                                selected = currentAction is HomeGestureAction.LaunchApp,
                                onClick = {
                                    filterQuery = ""
                                    mode = HomeGesturePickerMode.PICK_APP
                                },
                            )
                            HomeGestureActionRow(
                                label = stringResource(R.string.home_action_pick_shortcut),
                                selected = currentAction is HomeGestureAction.LaunchShortcut,
                                onClick = {
                                    filterQuery = ""
                                    mode = HomeGesturePickerMode.PICK_SHORTCUT
                                },
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
                                swipeDirectionLabel(direction),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isShortcutMode || hasShortcutHostPermission) {
                        SheetSearchBar(
                            query = filterQuery,
                            placeholder = stringResource(
                                if (isShortcutMode) R.string.search_shortcuts_placeholder
                                else R.string.search_apps_placeholder
                            ),
                            onQueryChange = { filterQuery = it },
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
                            else allApps.filter {
                                it.label.contains(q, ignoreCase = true) ||
                                    it.packageName.contains(q, ignoreCase = true)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(items = filtered, key = { it.componentKey }) { candidate ->
                                val selected = (currentAction as? HomeGestureAction.LaunchApp)?.componentKey == candidate.componentKey
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            onSelectAction(HomeGestureAction.LaunchApp(candidate.componentKey))
                                            onDismiss()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                ) {
                                    AppIconImage(app = candidate, size = 34.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = candidate.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeGestureActionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun MoveToPageDialog(
    currentPageCount: Int,
    maxPages: Int,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.move_page_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until currentPageCount) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectPage(i) }
                            .testTag("move_to_page_$i"),
                    ) {
                        Text(
                            text = stringResource(R.string.page_number_label, i + 1),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                if (currentPageCount < maxPages) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectPage(currentPageCount) }
                            .testTag("move_to_new_page"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.page_add_new, currentPageCount + 1),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
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
