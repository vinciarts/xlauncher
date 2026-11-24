package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.HomeGridEngine
import com.example.model.LauncherUiState
import com.example.ui.components.ExpressiveSectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.layoutSettingsItems(
    uiState: LauncherUiState,
    segmentedColors: ListItemColors,
    onSetHomeGridColumns: (Int) -> Unit,
    onSetHomeGridRows: (Int) -> Unit,
    onSetHomeLoopPaging: (Boolean) -> Unit,
    onSetDrawerColumns: (Int) -> Unit,
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
                    count = 3,
                    containerColor = segmentedColors.containerColor,
                    title = stringResource(R.string.settings_home_columns),
                ) {
                    GridCountStepper(
                        value = settings.homeGridColumns,
                        minValue = HomeGridEngine.MIN_HOME_GRID_COLUMNS,
                        maxValue = HomeGridEngine.MAX_HOME_GRID_COLUMNS,
                        label = stringResource(R.string.grid_columns_option, settings.homeGridColumns),
                        testTagPrefix = "home_cols",
                        onValueChange = onSetHomeGridColumns,
                    )
                }

                SegmentedControlContainer(
                    index = 1,
                    count = 3,
                    containerColor = segmentedColors.containerColor,
                    title = stringResource(R.string.settings_home_rows),
                ) {
                    GridCountStepper(
                        value = settings.homeGridRows,
                        minValue = HomeGridEngine.MIN_HOME_GRID_ROWS,
                        maxValue = HomeGridEngine.MAX_HOME_GRID_ROWS,
                        label = stringResource(R.string.grid_rows_option, settings.homeGridRows),
                        testTagPrefix = "home_rows",
                        onValueChange = onSetHomeGridRows,
                    )
                }

                SegmentedListItem(
                    onClick = { onSetHomeLoopPaging(!settings.homeLoopPaging) },
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
