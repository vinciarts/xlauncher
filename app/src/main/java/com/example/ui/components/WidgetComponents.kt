package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.PlacedWidget
import com.example.model.WidgetMenuTarget
import kotlin.math.roundToInt

/**
 * Standard Material 3 DropdownMenu anchored to the long-pressed point on empty desktop space.
 */
@Composable
fun HomeEmptyMenuPopup(
    anchorOffset: Offset,
    onAddWidget: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(anchorOffset.x.roundToInt(), anchorOffset.y.roundToInt()) }
                .size(1.dp),
        ) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("home_empty_menu_popup"),
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.home_menu_add_widget)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Widgets, contentDescription = null) },
                    onClick = onAddWidget,
                    modifier = Modifier.testTag("home_menu_add_widget"),
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.home_menu_wallpaper)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Wallpaper, contentDescription = null) },
                    onClick = onOpenWallpaper,
                    modifier = Modifier.testTag("home_menu_wallpaper"),
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.home_menu_settings)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Settings, contentDescription = null) },
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("home_menu_settings"),
                )
            }
        }
    }
}

/**
 * Standard Material 3 DropdownMenu anchored to a long-pressed Home widget's bounds.
 */
@Composable
fun WidgetContextMenuPopup(
    target: WidgetMenuTarget,
    canReconfigure: Boolean,
    onDismiss: () -> Unit,
    onReconfigure: () -> Unit,
    onResize: () -> Unit,
    onMovePage: () -> Unit,
    onRemove: () -> Unit,
) {
    val density = LocalDensity.current
    val widgetBounds = target.anchorBounds

    Box(modifier = Modifier.fillMaxSize()) {
        val anchorModifier = if (widgetBounds != null) {
            val widthDp = with(density) { widgetBounds.width.toDp().coerceAtLeast(1.dp) }
            val heightDp = with(density) { widgetBounds.height.toDp().coerceAtLeast(1.dp) }
            Modifier
                .offset { IntOffset(widgetBounds.left.roundToInt(), widgetBounds.top.roundToInt()) }
                .size(width = widthDp, height = heightDp)
        } else {
            Modifier.align(Alignment.Center)
        }

        Box(modifier = anchorModifier) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("widget_context_menu_popup"),
            ) {
                if (canReconfigure) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.widget_menu_configure)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Tune, contentDescription = null) },
                        onClick = onReconfigure,
                        modifier = Modifier.testTag("widget_menu_configure"),
                    )
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.widget_menu_resize)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.AspectRatio, contentDescription = null) },
                    onClick = onResize,
                    modifier = Modifier.testTag("widget_menu_resize"),
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.widget_menu_move_page)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null)
                    },
                    onClick = onMovePage,
                    modifier = Modifier.testTag("widget_menu_move_page"),
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.widget_menu_remove)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = onRemove,
                    modifier = Modifier.testTag("widget_menu_remove"),
                )
            }
        }
    }
}

/**
 * Native Material 3 dialog to resize a placed Home widget's column span and row span.
 */
@Composable
fun WidgetResizeDialog(
    widget: PlacedWidget,
    maxColumns: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val clampedMaxCols = maxColumns.coerceIn(1, 5)
    var selectedWidth by remember(widget.appWidgetId) {
        mutableIntStateOf(widget.spanX.coerceIn(1, clampedMaxCols))
    }
    var selectedHeight by remember(widget.appWidgetId) {
        mutableIntStateOf(widget.spanY.coerceIn(1, 6))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.widget_resize_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.widget_resize_width),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        for (w in 1..clampedMaxCols) {
                            val selected = w == selectedWidth
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedWidth = w }
                                    .testTag("widget_width_$w"),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "$w",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.widget_resize_height),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        for (h in 1..6) {
                            val selected = h == selectedHeight
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedHeight = h }
                                    .testTag("widget_height_$h"),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "$h",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedWidth, selectedHeight) },
                modifier = Modifier.testTag("widget_resize_confirm"),
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
