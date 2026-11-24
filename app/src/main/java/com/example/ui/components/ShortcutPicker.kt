package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppShortcutItem
import com.example.search.PinyinSearchEngine

@Composable
fun ShortcutIconImage(
    shortcut: AppShortcutItem,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        if (shortcut.icon != null) {
            Image(
                bitmap = shortcut.icon,
                contentDescription = shortcut.displayLabel,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Text(
                    text = shortcut.label.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShortcutPickerContent(
    allShortcuts: List<AppShortcutItem>,
    hasShortcutHostPermission: Boolean,
    selectedTargetKey: String?,
    filterQuery: String,
    preferredPackageName: String? = null,
    onSelectShortcut: (AppShortcutItem) -> Unit,
    onOpenDefaultHomeSettings: () -> Unit,
) {
    if (!hasShortcutHostPermission) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            ) {
                Text(
                    text = stringResource(R.string.shortcuts_need_default_launcher),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = onOpenDefaultHomeSettings) {
                    Text(stringResource(R.string.settings_switch_default))
                }
            }
        }
        return
    }

    val filtered = remember(allShortcuts, filterQuery, preferredPackageName) {
        val q = filterQuery.trim()
        val matched = if (q.isEmpty()) {
            allShortcuts
        } else {
            allShortcuts.filter {
                PinyinSearchEngine.matches(q, it.label, it.appLabel) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
        if (preferredPackageName != null) {
            matched.sortedBy { if (it.packageName == preferredPackageName) 0 else 1 }
        } else {
            matched
        }
    }

    if (filtered.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            Text(
                text = stringResource(R.string.shortcuts_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val segmentedColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

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
            key = { _, shortcut -> shortcut.targetKey },
        ) { index, shortcut ->
            val selected = selectedTargetKey == shortcut.targetKey
            SegmentedListItem(
                selected = selected,
                onClick = { onSelectShortcut(shortcut) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = filtered.size),
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
                        text = shortcut.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
