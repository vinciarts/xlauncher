package com.example.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppItem
import com.example.model.DrawerTab

/**
 * Native Material 3 dialog to create a new App Drawer tab and optionally pick initial apps.
 */
@Composable
fun CreateDrawerTabDialog(
    allApps: List<AppItem>,
    onConfirm: (name: String, appKeys: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var tabName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val selectedKeys = remember { mutableStateListOf<String>() }

    val filteredApps = remember(allApps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) allApps
        else allApps.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
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

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_apps_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                ) {
                    items(items = filteredApps, key = { it.componentKey }) { app ->
                        val checked = app.componentKey in selectedKeys
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (checked) selectedKeys.remove(app.componentKey)
                                    else selectedKeys.add(app.componentKey)
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (app.componentKey !in selectedKeys) selectedKeys.add(app.componentKey)
                                    } else {
                                        selectedKeys.remove(app.componentKey)
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AppIconImage(app = app, size = 30.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
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
 * Native Material 3 dialog to rename, pick apps for, reorder, or delete a custom App Drawer tab.
 */
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

    val filteredApps = remember(allApps, searchQuery, selectedKeys.size) {
        val q = searchQuery.trim()
        val matched = if (q.isEmpty()) allApps
        else allApps.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
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

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.drawer_tab_visible_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = !isHidden,
                        enabled = isHidden || canHide,
                        onCheckedChange = { visible -> isHidden = !visible },
                        modifier = Modifier.testTag("edit_tab_visibility_switch"),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.drawer_tab_pick_apps_label, selectedKeys.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_apps_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                ) {
                    items(items = filteredApps, key = { it.componentKey }) { app ->
                        val checked = app.componentKey in selectedKeys
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (checked) selectedKeys.remove(app.componentKey)
                                    else selectedKeys.add(app.componentKey)
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        if (app.componentKey !in selectedKeys) selectedKeys.add(app.componentKey)
                                    } else {
                                        selectedKeys.remove(app.componentKey)
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AppIconImage(app = app, size = 30.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
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
 * Native Material 3 dialog opened from an app's long-press menu in the App Drawer
 * to assign/remove the app across custom tabs or create a new tab directly.
 */
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (customTabs.isEmpty() && !creatingNew) {
                    Text(
                        text = stringResource(R.string.drawer_tabs_empty_assign_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (customTabs.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                    ) {
                        items(items = customTabs, key = { it.id }) { tab ->
                            val checked = tab.id in selectedIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (checked) selectedIds.remove(tab.id)
                                        else selectedIds.add(tab.id)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            if (tab.id !in selectedIds) selectedIds.add(tab.id)
                                        } else {
                                            selectedIds.remove(tab.id)
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (tab.isHidden) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.drawer_tab_hidden_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
