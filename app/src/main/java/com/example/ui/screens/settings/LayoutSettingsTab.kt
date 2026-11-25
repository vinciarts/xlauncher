package com.example.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LauncherPreferencesRepository
import com.example.model.HomeGridEngine
import com.example.model.IconShape
import com.example.model.LauncherUiState
import com.example.ui.components.ExpressiveSectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.layoutSettingsItems(
    uiState: LauncherUiState,
    segmentedColors: ListItemColors,
    onSetHomeGridColumns: (Int) -> Unit,
    onSetHomeGridRows: (Int) -> Unit,
    onSetDockMaxItems: (Int) -> Unit,
    onSetHomeLoopPaging: (Boolean) -> Unit,
    onSetHomeShowAppLabels: (Boolean) -> Unit,
    onSetIconShape: (IconShape) -> Unit,
    onSetDrawerColumns: (Int) -> Unit,
    onSetAutoHideEmptyDrawerTabs: (Boolean) -> Unit,
    onSetAllTabHidden: (Boolean) -> Unit,
    onSetDrawerTabHidden: (String, Boolean) -> Unit,
    onOpenCreateTabDialog: () -> Unit,
    onEditTab: (String) -> Unit,
) {
    val settings = uiState.settings

    // Home Screen Section
    item(key = "home_section") {
        Column(modifier = Modifier.fillMaxWidth()) {
            ExpressiveSectionHeader(title = stringResource(R.string.settings_home_section))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedControlContainer(
                    index = 0,
                    count = 5,
                    containerColor = segmentedColors.containerColor,
                    title = stringResource(R.string.settings_home_columns),
                    valueLabel = stringResource(R.string.grid_columns_option, settings.homeGridColumns),
                ) {
                    DiscreteIntSlider(
                        value = settings.homeGridColumns,
                        minValue = HomeGridEngine.MIN_HOME_GRID_COLUMNS,
                        maxValue = HomeGridEngine.MAX_HOME_GRID_COLUMNS,
                        testTag = "slider_home_cols",
                        onValueChange = onSetHomeGridColumns,
                    )
                }

                SegmentedControlContainer(
                    index = 1,
                    count = 5,
                    containerColor = segmentedColors.containerColor,
                    title = stringResource(R.string.settings_home_rows),
                    valueLabel = stringResource(R.string.grid_rows_option, settings.homeGridRows),
                ) {
                    DiscreteIntSlider(
                        value = settings.homeGridRows,
                        minValue = HomeGridEngine.MIN_HOME_GRID_ROWS,
                        maxValue = HomeGridEngine.MAX_HOME_GRID_ROWS,
                        testTag = "slider_home_rows",
                        onValueChange = onSetHomeGridRows,
                    )
                }

                SegmentedControlContainer(
                    index = 2,
                    count = 5,
                    containerColor = segmentedColors.containerColor,
                    title = stringResource(R.string.settings_dock_count),
                    valueLabel = if (settings.dockMaxItems == 0) {
                        stringResource(R.string.dock_count_off)
                    } else {
                        stringResource(R.string.dock_count_option, settings.dockMaxItems)
                    },
                ) {
                    DiscreteIntSlider(
                        value = settings.dockMaxItems,
                        minValue = LauncherPreferencesRepository.MIN_DOCK_ITEMS,
                        maxValue = LauncherPreferencesRepository.MAX_DOCK_ITEMS,
                        testTag = "slider_dock_count",
                        onValueChange = onSetDockMaxItems,
                    )
                }

                SegmentedListItem(
                    onClick = { onSetHomeLoopPaging(!settings.homeLoopPaging) },
                    shapes = ListItemDefaults.segmentedShapes(index = 3, count = 5),
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

                SegmentedListItem(
                    onClick = { onSetHomeShowAppLabels(!settings.homeShowAppLabels) },
                    shapes = ListItemDefaults.segmentedShapes(index = 4, count = 5),
                    colors = segmentedColors,
                    trailingContent = {
                        Switch(
                            checked = settings.homeShowAppLabels,
                            onCheckedChange = null,
                            modifier = Modifier.testTag("switch_home_show_labels"),
                        )
                    },
                    content = {
                        Text(text = stringResource(R.string.settings_home_show_labels))
                    },
                )
            }
        }
    }

    // Icon Shape Section
    item(key = "icon_shape_section") {
        val previewApps = remember(uiState.allApps) {
            uiState.allApps.filter { it.icon != null }.take(5)
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            ExpressiveSectionHeader(title = stringResource(R.string.settings_icon_shape_section))
            SegmentedControlContainer(
                index = 0,
                count = 1,
                containerColor = segmentedColors.containerColor,
                title = stringResource(R.string.settings_icon_shape),
            ) {
                if (previewApps.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("icon_shape_preview_row"),
                    ) {
                        previewApps.forEach { app ->
                            val bmp = app.icon
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = app.label,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(44.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                ConnectedToggleGroup(
                    options = IconShape.entries,
                    selectedOption = settings.iconShape,
                    labelFor = { shape ->
                        val labelRes = when (shape) {
                            IconShape.SYSTEM -> R.string.icon_shape_system
                            IconShape.SQUIRCLE -> R.string.icon_shape_squircle
                            IconShape.ROUNDED_SQUARE -> R.string.icon_shape_rounded_square
                            IconShape.CIRCLE -> R.string.icon_shape_circle
                        }
                        stringResource(labelRes)
                    },
                    testTagFor = { "chip_icon_shape_${it.id}" },
                    onSelect = onSetIconShape,
                    compactPadding = true,
                )
            }
        }
    }

    // Drawer Section
    item(key = "drawer_columns_section") {
        Column(modifier = Modifier.fillMaxWidth()) {
            ExpressiveSectionHeader(title = stringResource(R.string.settings_drawer_section))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedControlContainer(
                    index = 0,
                    count = 2,
                    containerColor = segmentedColors.containerColor,
                    title = stringResource(R.string.settings_drawer_columns),
                    valueLabel = stringResource(R.string.grid_columns_option, settings.drawerColumns),
                ) {
                    DiscreteIntSlider(
                        value = settings.drawerColumns,
                        minValue = HomeGridEngine.MIN_HOME_GRID_COLUMNS,
                        maxValue = HomeGridEngine.MAX_HOME_GRID_COLUMNS,
                        testTag = "slider_drawer_cols",
                        onValueChange = onSetDrawerColumns,
                    )
                }

                SegmentedListItem(
                    onClick = { onSetAutoHideEmptyDrawerTabs(!settings.autoHideEmptyDrawerTabs) },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    colors = segmentedColors,
                    trailingContent = {
                        Switch(
                            checked = settings.autoHideEmptyDrawerTabs,
                            onCheckedChange = null,
                            modifier = Modifier.testTag("switch_auto_hide_empty_tabs"),
                        )
                    },
                    content = {
                        Text(text = stringResource(R.string.settings_drawer_auto_hide_empty_tabs))
                    },
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
                        onClick = onOpenCreateTabDialog,
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
                    onClick = { onSetAllTabHidden(!settings.isAllTabHidden) },
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
                        onClick = { onEditTab(tab.id) },
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
}
