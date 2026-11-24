package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SwipeDirection

internal sealed interface HomeActionTrigger {
    data class Swipe(val direction: SwipeDirection) : HomeActionTrigger
    data object HomeButton : HomeActionTrigger
}

/**
 * Non-clickable segmented item container using official [ListItemDefaults.segmentedShapes]
 * for hosting interactive controls like [ConnectedToggleGroup] inside a segmented list group.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SegmentedControlContainer(
    index: Int,
    count: Int,
    containerColor: Color,
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

/**
 * Connected Material 3 Expressive toggle button group using [ButtonGroupDefaults] shapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> ConnectedToggleGroup(
    options: List<T>,
    selectedOption: T,
    labelFor: @Composable (T) -> String,
    testTagFor: (T) -> String,
    onSelect: (T) -> Unit,
    compactPadding: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, option ->
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = selectedOption == option,
                onCheckedChange = { checked ->
                    if (checked) onSelect(option)
                },
                shapes = shapes,
                contentPadding = if (compactPadding) {
                    PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                } else {
                    ToggleButtonDefaults.ContentPadding
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(testTagFor(option)),
            ) {
                Text(
                    text = labelFor(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Compact +/- stepper for numeric grid dimensions (columns 1..8, rows 1..12).
 */
@Composable
internal fun GridCountStepper(
    value: Int,
    minValue: Int,
    maxValue: Int,
    label: String,
    testTagPrefix: String,
    onValueChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilledTonalIconButton(
            onClick = { onValueChange((value - 1).coerceIn(minValue, maxValue)) },
            enabled = value > minValue,
            modifier = Modifier.testTag("${testTagPrefix}_dec"),
        ) {
            Icon(
                imageVector = Icons.Rounded.Remove,
                contentDescription = null,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("${testTagPrefix}_value"),
        )
        FilledTonalIconButton(
            onClick = { onValueChange((value + 1).coerceIn(minValue, maxValue)) },
            enabled = value < maxValue,
            modifier = Modifier.testTag("${testTagPrefix}_inc"),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
            )
        }
    }
}
