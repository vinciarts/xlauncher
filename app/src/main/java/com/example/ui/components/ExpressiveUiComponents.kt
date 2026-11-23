package com.example.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
                null
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
