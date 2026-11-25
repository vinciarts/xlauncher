package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.SwipeDirection

/**
 * Official Material 3 inline search field built directly on [SearchBarDefaults.InputField]
 * and [SearchBarDefaults.inputFieldShape] / [SearchBarDefaults.colors].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    inputTestTag: String? = null,
    onSearch: (String) -> Unit = {},
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val searchBarColors = SearchBarDefaults.colors()
    val inputModifier = Modifier
        .fillMaxWidth()
        .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
        .let { m -> if (inputTestTag != null) m.testTag(inputTestTag) else m }

    Surface(
        shape = SearchBarDefaults.inputFieldShape,
        color = searchBarColors.containerColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            expanded = false,
            onExpandedChange = {},
            enabled = enabled,
            placeholder = {
                Text(
                    text = placeholder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_clear_search),
                        )
                    }
                }
            } else {
                trailingContent
            },
            colors = searchBarColors.inputFieldColors,
            modifier = inputModifier,
        )
    }
}

/**
 * Standard Material 3 Expressive section header used above grouped [androidx.compose.material3.SegmentedListItem]s.
 */
@Composable
fun ExpressiveSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

fun swipeDirectionIcon(direction: SwipeDirection): ImageVector = when (direction) {
    SwipeDirection.UP -> Icons.Rounded.ArrowUpward
    SwipeDirection.DOWN -> Icons.Rounded.ArrowDownward
    SwipeDirection.LEFT -> Icons.AutoMirrored.Rounded.ArrowBack
    SwipeDirection.RIGHT -> Icons.AutoMirrored.Rounded.ArrowForward
}

/**
 * Applies a subtle One UI-style alpha fading edge at the top/bottom of a scrollable container
 * whenever more content is available in that direction.
 */
fun Modifier.verticalFadingEdge(
    state: ScrollableState,
    fadeHeight: Dp = 24.dp,
): Modifier = composed {
    val topAlpha by animateFloatAsState(
        targetValue = if (state.canScrollBackward) 1f else 0f,
        label = "top_fading_edge",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (state.canScrollForward) 1f else 0f,
        label = "bottom_fading_edge",
    )
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fadePx = fadeHeight.toPx().coerceAtMost(size.height * 0.35f)
            if (fadePx <= 0f) return@drawWithContent
            if (topAlpha > 0.001f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 1f - topAlpha), Color.Black),
                        startY = 0f,
                        endY = fadePx,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, fadePx),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottomAlpha > 0.001f) {
                val startY = size.height - fadePx
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - bottomAlpha)),
                        startY = startY,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, startY),
                    size = Size(size.width, fadePx),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}
