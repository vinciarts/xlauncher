package com.example.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.LauncherUiState
import com.example.model.WebSearchEngine
import com.example.ui.components.ExpressiveSectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
internal fun LazyListScope.searchSettingsItems(
    context: Context,
    uiState: LauncherUiState,
    segmentedColors: ListItemColors,
    onSetSearchShortcutsEnabled: (Boolean) -> Unit,
    onSetSearchContactsEnabled: (Boolean) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onSetSearchFilesEnabled: (Boolean) -> Unit,
    onOpenFolderPicker: () -> Unit,
    onOpenSearchFoldersDialog: () -> Unit,
    onSetSearchThirdPartyAppsEnabled: (Boolean) -> Unit,
    onSetSearchWebEnabled: (Boolean) -> Unit,
    onSetSearchWebEngine: (WebSearchEngine) -> Unit,
) {
    val settings = uiState.settings

    // Opt-in Universal Search Sources Section
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
                SegmentedListItem(
                    onClick = { onSetSearchShortcutsEnabled(!settings.searchShortcutsEnabled) },
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

                SegmentedListItem(
                    onClick = {
                        val enable = !settings.searchContactsEnabled
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
                                onRequestContactsPermission()
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
                    onClick = onOpenSearchFoldersDialog,
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
                                    onOpenFolderPicker()
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

    // Auto Third-Party App Search & Web Search Section
    item(key = "web_and_third_party_search_section") {
        val providerCount = uiState.availableSearchProviders.size
        val itemCount = if (settings.searchWebEnabled) 3 else 2
        Column(modifier = Modifier.fillMaxWidth()) {
            ExpressiveSectionHeader(title = stringResource(R.string.settings_search_external_section))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedListItem(
                    onClick = { onSetSearchWebEnabled(!settings.searchWebEnabled) },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = itemCount),
                    colors = segmentedColors,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(
                                R.string.settings_search_web_desc,
                                settings.searchWebEngine.label,
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.searchWebEnabled,
                            onCheckedChange = null,
                            modifier = Modifier.testTag("switch_search_web"),
                        )
                    },
                    modifier = Modifier.testTag("settings_search_web"),
                    content = {
                        Text(text = stringResource(R.string.settings_search_web))
                    },
                )

                if (settings.searchWebEnabled) {
                    SegmentedControlContainer(
                        index = 1,
                        count = itemCount,
                        containerColor = segmentedColors.containerColor,
                        title = stringResource(R.string.settings_search_web_engine_label),
                    ) {
                        ConnectedToggleGroup(
                            options = WebSearchEngine.entries,
                            selectedOption = settings.searchWebEngine,
                            labelFor = { it.label },
                            testTagFor = { "chip_web_engine_${it.id}" },
                            onSelect = onSetSearchWebEngine,
                            compactPadding = true,
                        )
                    }
                }

                SegmentedListItem(
                    onClick = { onSetSearchThirdPartyAppsEnabled(!settings.searchThirdPartyAppsEnabled) },
                    shapes = ListItemDefaults.segmentedShapes(
                        index = if (settings.searchWebEnabled) 2 else 1,
                        count = itemCount,
                    ),
                    colors = segmentedColors,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (providerCount == 0) {
                                stringResource(R.string.settings_search_third_party_none_installed)
                            } else {
                                stringResource(R.string.settings_search_third_party_auto_desc, providerCount)
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.searchThirdPartyAppsEnabled,
                            onCheckedChange = null,
                            modifier = Modifier.testTag("switch_search_third_party_apps"),
                        )
                    },
                    modifier = Modifier.testTag("settings_search_third_party_apps"),
                    content = {
                        Text(text = stringResource(R.string.settings_search_third_party_section))
                    },
                )
            }
        }
    }
}
