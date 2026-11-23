package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.WidgetCatalogItem
import com.example.widget.WidgetHostController

/**
 * Full-screen Material 3 widget picker using unmodified native LazyColumn scrolling & overscroll stretch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerScreen(
    catalog: List<WidgetCatalogItem>,
    isLoading: Boolean,
    widgetHostController: WidgetHostController,
    onSelectWidget: (WidgetCatalogItem) -> Unit,
    onBack: () -> Unit,
    filterPackageName: String? = null,
    onClearPackageFilter: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    var searchQuery by remember(filterPackageName) { mutableStateOf("") }
    val expandedPackages = remember { mutableStateMapOf<String, Boolean>() }

    val baseCatalog = remember(catalog, filterPackageName) {
        if (filterPackageName.isNullOrBlank()) catalog
        else catalog.filter { it.packageName == filterPackageName }
    }

    val grouped = remember(baseCatalog, searchQuery) {
        val q = searchQuery.trim()
        val filtered = if (q.isEmpty()) {
            baseCatalog
        } else {
            baseCatalog.filter {
                it.appLabel.contains(q, ignoreCase = true) ||
                    it.widgetLabel.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
        filtered.groupBy { it.packageName }.entries.toList()
    }

    val filteredAppLabel = if (!filterPackageName.isNullOrBlank()) {
        baseCatalog.firstOrNull()?.appLabel
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = filteredAppLabel ?: stringResource(R.string.widget_picker_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("widget_picker_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    if (!filterPackageName.isNullOrBlank()) {
                        TextButton(
                            onClick = onClearPackageFilter,
                            modifier = Modifier.testTag("widget_picker_show_all_button"),
                        ) {
                            Text(text = stringResource(R.string.widget_picker_show_all))
                        }
                    }
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .navigationBarsPadding(),
            ) {
                if (filterPackageName.isNullOrBlank()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        placeholder = {
                            Text(text = stringResource(R.string.search_widgets_placeholder))
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search),
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("widget_search_input"),
                    )
                }

                when {
                    isLoading -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.5.dp)
                        }
                    }

                    grouped.isEmpty() -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.widget_picker_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> {
                        val autoExpand = searchQuery.isNotBlank() || grouped.size <= 3
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            items(items = grouped, key = { it.key }) { (pkg, widgets) ->
                                val first = widgets.first()
                                val expanded = autoExpand || (expandedPackages[pkg] == true)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .minimumInteractiveComponentSize()
                                                .clickable {
                                                    expandedPackages[pkg] = !expanded
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                        ) {
                                            if (first.appIcon != null) {
                                                Image(
                                                    bitmap = first.appIcon,
                                                    contentDescription = first.appLabel,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.size(36.dp),
                                                )
                                                Spacer(modifier = Modifier.width(14.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = first.appLabel,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = stringResource(R.string.widget_count_suffix, widgets.size),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Icon(
                                                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        if (expanded) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                            ) {
                                                widgets.forEach { item ->
                                                    WidgetCatalogRow(
                                                        item = item,
                                                        widgetHostController = widgetHostController,
                                                        onClick = { onSelectWidget(item) },
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
            }
        }
    }
}

@Composable
private fun WidgetCatalogRow(
    item: WidgetCatalogItem,
    widgetHostController: WidgetHostController,
    onClick: () -> Unit,
) {
    val preview by produceState<ImageBitmap?>(
        initialValue = widgetHostController.getCachedPreview(item.providerComponent),
        key1 = item.providerComponent,
    ) {
        if (value == null) {
            value = widgetHostController.loadWidgetPreview(item.providerComponent, item.providerInfo)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("widget_item_${item.providerComponent}"),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.widgetLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        R.string.widget_size_label,
                        item.spanX,
                        item.spanY,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (preview != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Image(
                    bitmap = preview!!,
                    contentDescription = item.widgetLabel,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 120.dp),
                )
            }
        }
    }
}
