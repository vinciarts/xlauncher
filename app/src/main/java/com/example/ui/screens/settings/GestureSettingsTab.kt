package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.model.HomeGestureAction
import com.example.model.LauncherUiState
import com.example.model.SwipeDirection
import com.example.ui.components.ExpressiveSectionHeader
import com.example.ui.components.homeGestureActionLabel
import com.example.ui.components.swipeDirectionIcon
import com.example.ui.components.swipeDirectionLabel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.gestureSettingsItems(
    uiState: LauncherUiState,
    segmentedColors: ListItemColors,
    onEditTrigger: (HomeActionTrigger) -> Unit,
) {
    val settings = uiState.settings

    item(key = "home_gestures_section") {
        Column(modifier = Modifier.fillMaxWidth()) {
            ExpressiveSectionHeader(title = stringResource(R.string.settings_home_gestures_section))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val directions = SwipeDirection.entries
                val totalRows = directions.size + 1
                val loopBlocksEdgeGestures = settings.homeLoopPaging && settings.homePageCount > 1
                directions.forEachIndexed { index, dir ->
                    val action = settings.homeGestures[dir] ?: HomeGestureAction.None
                    val baseLabel = homeGestureActionLabel(
                        action = action,
                        appsByKey = uiState.appsByKey,
                        shortcutsByTargetKey = uiState.shortcutsByTargetKey,
                        direction = dir,
                    )
                    val isHorizontal = dir == SwipeDirection.LEFT || dir == SwipeDirection.RIGHT
                    val actionLabel = if (isHorizontal && action != HomeGestureAction.None && loopBlocksEdgeGestures) {
                        stringResource(R.string.home_gesture_paused_by_loop, baseLabel)
                    } else {
                        baseLabel
                    }
                    SegmentedListItem(
                        onClick = { onEditTrigger(HomeActionTrigger.Swipe(dir)) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = totalRows),
                        colors = segmentedColors,
                        leadingContent = {
                            Icon(
                                imageVector = swipeDirectionIcon(dir),
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = actionLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.testTag("home_gesture_${dir.name.lowercase()}"),
                        content = {
                            Text(text = swipeDirectionLabel(dir))
                        },
                    )
                }

                val homeBtnActionLabel = homeGestureActionLabel(
                    action = settings.homeButtonAction,
                    appsByKey = uiState.appsByKey,
                    shortcutsByTargetKey = uiState.shortcutsByTargetKey,
                )
                SegmentedListItem(
                    onClick = { onEditTrigger(HomeActionTrigger.HomeButton) },
                    shapes = ListItemDefaults.segmentedShapes(index = directions.size, count = totalRows),
                    colors = segmentedColors,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = null,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = homeBtnActionLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.testTag("home_button_action"),
                    content = {
                        Text(text = stringResource(R.string.settings_home_button_action))
                    },
                )
            }
        }
    }
}
