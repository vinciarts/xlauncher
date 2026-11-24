package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppItem
import com.example.model.DrawerTab
import com.example.search.PinyinSearchEngine

/**
 * Native Material 3 Expressive dialog to create a new App Drawer tab and optionally pick initial apps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CreateDrawerTabDialog(
    allApps: List<AppItem>,
    onConfirm: (name: String, appKeys: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var tabName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val selectedKeys = remember { mutableStateListOf<String>() }
    val dialogSegmentedColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    val filteredApps = remember(allApps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) allApps
        else allApps.filter { PinyinSearchEngine.matches(q, it.label, it.packageName) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.drawer_tab_create_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tabName,
                    onValueChange = { tabName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.drawer_tab_name_label)) },
                    placeholder = { Text(stringResource(R.string.drawer_tab_name_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_tab_name_input"),
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.drawer_tab_pick_apps_label, selectedKeys.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExpressiveSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.search_apps_placeholder),
                )

                Spacer(modifier = Modifier.height(8.dp))

                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalFadingEdge(listState),
                ) {
                    itemsIndexed(
                        items = filteredApps,
                        key = { _, app -> app.componentKey },
                    ) { index, app ->
                        val checked = app.componentKey in selectedKeys
                        SegmentedListItem(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (app.componentKey !in selectedKeys) selectedKeys.add(app.componentKey)
                                } else {
                                    selectedKeys.remove(app.componentKey)
                                }
                            },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = filteredApps.size),
                            colors = dialogSegmentedColors,
                            leadingContent = {
                                AppIconImage(app = app, size = 28.dp)
                            },
                            trailingContent = {
                                Checkbox(checked = checked, onCheckedChange = null)
                            },
                            content = {
                                Text(
                                    text = app.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = tabName.isNotBlank(),
                onClick = { onConfirm(tabName.trim(), selectedKeys.toList()) },
                modifier = Modifier.testTag("create_tab_confirm"),
            ) {
                Text(stringResource(R.string.dialog_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

/**
 * Native Material 3 Expressive dialog to rename, pick apps for, reorder, or delete a custom App Drawer tab.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditDrawerTabDialog(
    tab: DrawerTab,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canHide: Boolean,
    allApps: List<AppItem>,
    onSave: (newName: String, appKeys: Set<String>, isHidden: Boolean) -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tabName by remember(tab.id) { mutableStateOf(tab.name) }
    var isHidden by remember(tab.id) { mutableStateOf(tab.isHidden) }
    var searchQuery by remember(tab.id) { mutableStateOf("") }
    val selectedKeys = remember(tab.id) {
        mutableStateListOf<String>().apply { addAll(tab.appKeys) }
    }
    val dialogSegmentedColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    val filteredApps = remember(allApps, searchQuery, selectedKeys.size) {
        val q = searchQuery.trim()
        val matched = if (q.isEmpty()) allApps
        else allApps.filter { PinyinSearchEngine.matches(q, it.label, it.packageName) }
        if (q.isEmpty()) {
            matched.sortedBy { if (it.componentKey in selectedKeys) 0 else 1 }
        } else {
            matched
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.drawer_tab_edit_title),
                    modifier = Modifier.weight(1f),
                )
                if (canMoveLeft) {
                    IconButton(onClick = onMoveLeft) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_move_tab_left),
                        )
                    }
                }
                if (canMoveRight) {
                    IconButton(onClick = onMoveRight) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = stringResource(R.string.cd_move_tab_right),
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("edit_tab_delete"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.drawer_tab_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tabName,
                    onValueChange = { tabName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.drawer_tab_name_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_tab_name_input"),
                )

                Spacer(modifier = Modifier.height(10.dp))

                SegmentedListItem(
                    checked = !isHidden,
                    onCheckedChange = { visible -> isHidden = !visible },
                    enabled = isHidden || canHide,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    colors = dialogSegmentedColors,
                    trailingContent = {
                        Switch(
                            checked = !isHidden,
                            enabled = isHidden || canHide,
                            onCheckedChange = null,
                            modifier = Modifier.testTag("edit_tab_visibility_switch"),
                        )
                    },
                    content = {
                        Text(text = stringResource(R.string.drawer_tab_visible_toggle))
                    },
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.drawer_tab_pick_apps_label, selectedKeys.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExpressiveSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.search_apps_placeholder),
                )

                Spacer(modifier = Modifier.height(8.dp))

                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalFadingEdge(listState),
                ) {
                    itemsIndexed(
                        items = filteredApps,
                        key = { _, app -> app.componentKey },
                    ) { index, app ->
                        val checked = app.componentKey in selectedKeys
                        SegmentedListItem(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (app.componentKey !in selectedKeys) selectedKeys.add(app.componentKey)
                                } else {
                                    selectedKeys.remove(app.componentKey)
                                }
                            },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = filteredApps.size),
                            colors = dialogSegmentedColors,
                            leadingContent = {
                                AppIconImage(app = app, size = 28.dp)
                            },
                            trailingContent = {
                                Checkbox(checked = checked, onCheckedChange = null)
                            },
                            content = {
                                Text(
                                    text = app.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = tabName.isNotBlank(),
                onClick = { onSave(tabName.trim(), selectedKeys.toSet(), isHidden) },
                modifier = Modifier.testTag("edit_tab_confirm"),
            ) {
                Text(stringResource(R.string.dialog_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

/**
 * Native Material 3 Expressive dialog opened from an app's long-press menu in the App Drawer
 * to assign/remove the app across custom tabs or create a new tab directly.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AssignAppTabsDialog(
    app: AppItem,
    customTabs: List<DrawerTab>,
    onConfirm: (selectedTabIds: Set<String>) -> Unit,
    onCreateTabWithApp: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedIds = remember(app.componentKey, customTabs) {
        mutableStateListOf<String>().apply {
            customTabs.filter { app.componentKey in it.appKeys }.forEach { add(it.id) }
        }
    }
    var creatingNew by remember { mutableStateOf(false) }
    var newTabName by remember { mutableStateOf("") }
    val dialogSegmentedColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconImage(app = app, size = 28.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.drawer_app_tabs_title, app.label),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (customTabs.isEmpty() && !creatingNew) {
                    Text(
                        text = stringResource(R.string.drawer_tabs_empty_assign_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (customTabs.isNotEmpty()) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalFadingEdge(listState),
                    ) {
                        itemsIndexed(
                            items = customTabs,
                            key = { _, tab -> tab.id },
                        ) { index, tab ->
                            val checked = tab.id in selectedIds
                            SegmentedListItem(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (tab.id !in selectedIds) selectedIds.add(tab.id)
                                    } else {
                                        selectedIds.remove(tab.id)
                                    }
                                },
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = customTabs.size),
                                colors = dialogSegmentedColors,
                                supportingContent = if (tab.isHidden) {
                                    {
                                        Text(text = stringResource(R.string.drawer_tab_hidden_badge))
                                    }
                                } else {
                                    null
                                },
                                trailingContent = {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                },
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
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                if (creatingNew) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = newTabName,
                            onValueChange = { newTabName = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.drawer_tab_name_placeholder)) },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(
                            enabled = newTabName.isNotBlank(),
                            onClick = {
                                val trimmed = newTabName.trim()
                                if (trimmed.isNotEmpty()) {
                                    onCreateTabWithApp(trimmed)
                                    newTabName = ""
                                    creatingNew = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.drawer_tab_add_short))
                        }
                    }
                } else {
                    TextButton(
                        onClick = { creatingNew = true },
                        modifier = Modifier.testTag("assign_tabs_new_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.drawer_tab_create_title))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds.toSet()) },
                modifier = Modifier.testTag("assign_tabs_confirm"),
            ) {
                Text(stringResource(R.string.dialog_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
