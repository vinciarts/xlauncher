package com.example.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.LauncherUiState
import com.example.search.PinyinSearchEngine
import com.example.ui.components.ExpressiveSearchField
import com.example.ui.components.ExpressiveSectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.searchSettingsItems(
    context: Context,
    uiState: LauncherUiState,
    segmentedColors: ListItemColors,
    providerFilterQuery: String,
    onProviderFilterQueryChange: (String) -> Unit,
    onSetSearchShortcutsEnabled: (Boolean) -> Unit,
    onSetSearchContactsEnabled: (Boolean) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onSetSearchFilesEnabled: (Boolean) -> Unit,
    onOpenFolderPicker: () -> Unit,
    onOpenSearchFoldersDialog: () -> Unit,
    onSetSearchThirdPartyAppEnabled: (String, Boolean) -> Unit,
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

    // Per-app Third-Party Search Providers Section
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
                    onQueryChange = onProviderFilterQueryChange,
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
                            onClick = {
                                onSetSearchThirdPartyAppEnabled(provider.packageName, !enabled)
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
