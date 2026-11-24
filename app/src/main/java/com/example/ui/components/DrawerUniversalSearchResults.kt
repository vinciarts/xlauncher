package com.example.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppActionOrigin
import com.example.model.AppItem
import com.example.model.AppShortcutItem
import com.example.model.ContactSearchResult
import com.example.model.FileSearchResult
import com.example.model.SearchProviderApp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf

/**
 * Unified Material 3 Expressive search result view for the App Drawer.
 * Renders matching apps as an icon grid followed by opt-in segmented sections
 * for shortcuts, contacts, local files, and third-party in-app search providers.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DrawerUniversalSearchResults(
    query: String,
    cols: Int,
    hasAnyExtraSearchEnabled: Boolean,
    apps: ImmutableList<AppItem>,
    shortcuts: ImmutableList<AppShortcutItem>,
    contacts: ImmutableList<ContactSearchResult>,
    files: ImmutableList<FileSearchResult>,
    searchProviders: ImmutableList<SearchProviderApp>,
    appMenuHost: AppContextMenuHost,
    onAppClick: (AppItem) -> Unit,
    onLaunchShortcut: (AppShortcutItem) -> Unit,
    onOpenContact: (ContactSearchResult) -> Unit,
    onDialContact: (String) -> Unit,
    onOpenFile: (FileSearchResult) -> Unit,
    onLaunchThirdPartySearch: (SearchProviderApp, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trimmedQuery = query.trim()
    val hasExtraResults = shortcuts.isNotEmpty() ||
        contacts.isNotEmpty() ||
        files.isNotEmpty() ||
        searchProviders.isNotEmpty()

    if (apps.isEmpty() && !hasExtraResults) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(
                    if (hasAnyExtraSearchEnabled) R.string.empty_universal_search_hint
                    else R.string.empty_search_hint
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val context = LocalContext.current
    val iconSize = if (cols == 5) 48.dp else 52.dp
    val segmentedColors = ListItemDefaults.segmentedColors()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .verticalFadingEdge(gridState)
            .testTag("drawer_search_results"),
    ) {
        if (apps.isNotEmpty()) {
            if (hasExtraResults) {
                item(
                    key = "search_header_apps",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    ExpressiveSectionHeader(title = stringResource(R.string.search_section_apps))
                }
            }
            items(
                items = apps,
                key = { "search_app_${it.componentKey}" },
                contentType = { "drawer_app" },
            ) { app ->
                Box {
                    AppIconTile(
                        app = app,
                        boundDirections = persistentSetOf(),
                        showLabel = true,
                        onWallpaper = false,
                        iconSize = iconSize,
                        onClick = { onAppClick(app) },
                        onLongClick = {
                            appMenuHost.onOpen(app, AppActionOrigin.DRAWER, -1)
                        },
                        onSwipe = {},
                    )
                    AppContextMenuDropdown(
                        app = app,
                        origin = AppActionOrigin.DRAWER,
                        sourcePageIndex = -1,
                        expanded = appMenuHost.isExpandedFor(app, AppActionOrigin.DRAWER, -1),
                        host = appMenuHost,
                    )
                }
            }
        }

        if (shortcuts.isNotEmpty()) {
            item(
                key = "search_section_shortcuts",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSectionHeader(title = stringResource(R.string.search_section_shortcuts))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        shortcuts.forEachIndexed { index, shortcut ->
                            SegmentedListItem(
                                onClick = { onLaunchShortcut(shortcut) },
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = shortcuts.size),
                                colors = segmentedColors,
                                leadingContent = {
                                    ShortcutIconImage(shortcut = shortcut, size = 32.dp)
                                },
                                supportingContent = {
                                    Text(
                                        text = shortcut.appLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag("search_shortcut_${shortcut.shortcutId}"),
                                content = {
                                    Text(
                                        text = shortcut.label,
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

        if (contacts.isNotEmpty()) {
            item(
                key = "search_section_contacts",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSectionHeader(title = stringResource(R.string.search_section_contacts))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        contacts.forEachIndexed { index, contact ->
                            SegmentedListItem(
                                onClick = { onOpenContact(contact) },
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = contacts.size),
                                colors = segmentedColors,
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = null,
                                    )
                                },
                                supportingContent = contact.phoneNumber?.let { phone ->
                                    {
                                        Text(
                                            text = phone,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                trailingContent = contact.phoneNumber?.let { phone ->
                                    {
                                        IconButton(
                                            onClick = { onDialContact(phone) },
                                            modifier = Modifier.testTag("search_contact_dial_${contact.contactId}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Call,
                                                contentDescription = stringResource(R.string.cd_call_contact),
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("search_contact_${contact.contactId}"),
                                content = {
                                    Text(
                                        text = contact.displayName,
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

        if (files.isNotEmpty()) {
            item(
                key = "search_section_files",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSectionHeader(title = stringResource(R.string.search_section_files))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        files.forEachIndexed { index, file ->
                            val subtitle = if (file.sizeBytes > 0L) {
                                "${file.parentFolderName} · ${Formatter.formatShortFileSize(context, file.sizeBytes)}"
                            } else {
                                file.parentFolderName
                            }
                            SegmentedListItem(
                                onClick = { onOpenFile(file) },
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = files.size),
                                colors = segmentedColors,
                                leadingContent = {
                                    Icon(
                                        imageVector = mimeTypeIcon(file.mimeType),
                                        contentDescription = null,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = subtitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag("search_file_$index"),
                                content = {
                                    Text(
                                        text = file.name,
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

        if (searchProviders.isNotEmpty() && trimmedQuery.isNotEmpty()) {
            item(
                key = "search_section_third_party",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExpressiveSectionHeader(
                        title = stringResource(R.string.search_section_third_party, trimmedQuery),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        searchProviders.forEachIndexed { index, provider ->
                            SegmentedListItem(
                                onClick = { onLaunchThirdPartySearch(provider, trimmedQuery) },
                                shapes = ListItemDefaults.segmentedShapes(
                                    index = index,
                                    count = searchProviders.size,
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
                                        text = stringResource(R.string.search_in_app_subtitle, provider.label),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.testTag("search_provider_${provider.packageName}"),
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

private fun mimeTypeIcon(mimeType: String): ImageVector = when {
    mimeType.startsWith("image/") -> Icons.Rounded.Image
    mimeType.startsWith("video/") -> Icons.Rounded.VideoFile
    mimeType.startsWith("audio/") -> Icons.Rounded.AudioFile
    mimeType == "application/pdf" -> Icons.Rounded.PictureAsPdf
    mimeType.startsWith("text/") || mimeType.contains("document") || mimeType.contains("word") -> {
        Icons.Rounded.Description
    }
    else -> Icons.Rounded.InsertDriveFile
}
