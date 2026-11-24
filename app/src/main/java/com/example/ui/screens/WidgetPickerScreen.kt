package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.verticalFadingEdge
import com.example.model.WidgetCatalogItem
import com.example.search.PinyinSearchEngine
import com.example.ui.components.ExpressiveSearchField
import com.example.widget.WidgetHostController

/**
 * Full-screen Material 3 Expressive widget picker with MediumFlexibleTopAppBar,
 * official SearchBar input field, and SegmentedListItem app groups.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motionScheme = MaterialTheme.motionScheme
    val segmentedColors = ListItemDefaults.segmentedColors()
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
                PinyinSearchEngine.matches(q, it.appLabel, it.packageName) ||
                    PinyinSearchEngine.matches(q, it.widgetLabel)
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
            MediumFlexibleTopAppBar(
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
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    ExpressiveSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = stringResource(R.string.search_widgets_placeholder),
                        inputTestTag = "widget_search_input",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                            LoadingIndicator()
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
                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalFadingEdge(listState),
                        ) {
                            itemsIndexed(
                                items = grouped,
                                key = { _, entry -> entry.key },
                            ) { index, (pkg, widgets) ->
                                val first = widgets.first()
                                val expanded = autoExpand || (expandedPackages[pkg] == true)
                                val itemShapes = ListItemDefaults.segmentedShapes(
                                    index = index,
                                    count = grouped.size,
                                )
                                Surface(
                                    shape = itemShapes.shape,
                                    color = segmentedColors.containerColor,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedListItem(
                                            onClick = { expandedPackages[pkg] = !expanded },
                                            shapes = itemShapes,
                                            colors = segmentedColors,
                                            leadingContent = if (first.appIcon != null) {
                                                {
                                                    Image(
                                                        bitmap = first.appIcon,
                                                        contentDescription = first.appLabel,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.size(36.dp),
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                            supportingContent = {
                                                Text(text = stringResource(R.string.widget_count_suffix, widgets.size))
                                            },
                                            trailingContent = {
                                                Icon(
                                                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                    contentDescription = null,
                                                )
                                            },
                                            content = {
                                                Text(
                                                    text = first.appLabel,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                        )

                                        AnimatedVisibility(
                                            visible = expanded,
                                            enter = expandVertically(motionScheme.defaultSpatialSpec()) +
                                                fadeIn(motionScheme.fastEffectsSpec()),
                                            exit = shrinkVertically(motionScheme.defaultSpatialSpec()) +
                                                fadeOut(motionScheme.fastEffectsSpec()),
                                        ) {
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

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("widget_item_${item.providerComponent}"),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = item.widgetLabel,
                    style = MaterialTheme.typography.titleSmall,
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
                    style = MaterialTheme.typography.labelMedium,
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
