package com.example.ui.components

import android.view.ViewConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppActionOrigin
import com.example.model.AppItem
import com.example.model.AppMenuTarget
import com.example.model.AppShortcutItem
import com.example.model.SwipeDirection
import kotlin.math.abs
import kotlin.math.roundToInt

private class CoordinatesHolder(var coords: LayoutCoordinates? = null)

@Composable
fun AppIconImage(
    app: AppItem,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Text(
                    text = app.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Unified App Icon tile used on Home, Dock, and App Drawer.
 * - Uses official Compose combinedClickable for tap and long-press (zero custom timers or animations).
 * - Only attaches a directional swipe recognizer when boundDirections is non-empty, using Android
 *   ViewConfiguration standards (touchSlop, scaledPagingTouchSlop, scaledMinimumFlingVelocity + VelocityTracker).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIconTile(
    app: AppItem,
    boundDirections: Set<SwipeDirection>,
    showLabel: Boolean = true,
    onWallpaper: Boolean = true,
    iconSize: Dp = 52.dp,
    onDownPointer: ((Long, Set<SwipeDirection>) -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (Rect?) -> Unit,
    onSwipe: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hasGestures = boundDirections.isNotEmpty()
    val coordsHolder = remember { CoordinatesHolder() }
    var longPressTriggeredInGesture by remember { mutableStateOf(false) }

    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val pagingTouchSlopPx = viewConfig.scaledPagingTouchSlop.toFloat()
    val minFlingVelocityPx = viewConfig.scaledMinimumFlingVelocity.toFloat()

    val currentBoundDirections by rememberUpdatedState(boundDirections)
    val currentOnDownPointer by rememberUpdatedState(onDownPointer)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnSwipe by rememberUpdatedState(onSwipe)

    val swipeModifier = if (hasGestures) {
        Modifier.pointerInput(app.componentKey, pagingTouchSlopPx, minFlingVelocityPx) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                longPressTriggeredInGesture = false
                currentOnDownPointer?.invoke(down.id.value, currentBoundDirections)

                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)
                var claimedDir: SwipeDirection? = null

                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (longPressTriggeredInGesture) break

                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val totalDrag = change.position - down.position
                    val absX = abs(totalDrag.x)
                    val absY = abs(totalDrag.y)

                    if (!change.pressed) {
                        val dir = claimedDir
                        if (dir != null) {
                            change.consume()
                            val velocity = velocityTracker.calculateVelocity()
                            val primaryDist = if (dir == SwipeDirection.UP || dir == SwipeDirection.DOWN) absY else absX
                            val directionalVelocity = when (dir) {
                                SwipeDirection.UP -> -velocity.y
                                SwipeDirection.DOWN -> velocity.y
                                SwipeDirection.LEFT -> -velocity.x
                                SwipeDirection.RIGHT -> velocity.x
                            }
                            if (primaryDist >= pagingTouchSlopPx || directionalVelocity >= minFlingVelocityPx) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSwipe(dir)
                            }
                        }
                        break
                    }

                    if (claimedDir == null && totalDrag.getDistance() > touchSlop) {
                        val dir = if (absY >= absX) {
                            if (totalDrag.y < 0f) SwipeDirection.UP else SwipeDirection.DOWN
                        } else {
                            if (totalDrag.x < 0f) SwipeDirection.LEFT else SwipeDirection.RIGHT
                        }
                        if (dir in currentBoundDirections) {
                            claimedDir = dir
                        } else {
                            break
                        }
                    }

                    if (claimedDir != null) {
                        change.consume()
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .onGloballyPositioned { coordsHolder.coords = it }
            .minimumInteractiveComponentSize()
            .then(swipeModifier)
            .combinedClickable(
                onClick = { currentOnClick() },
                onLongClick = {
                    longPressTriggeredInGesture = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val bounds = coordsHolder.coords?.takeIf { it.isAttached }?.boundsInWindow()
                    currentOnLongClick(bounds)
                },
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        AppIconImage(app = app, size = iconSize)
        if (showLabel) {
            Spacer(modifier = Modifier.height(5.dp))
            val labelStyle = if (onWallpaper) {
                MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.78f),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 4f,
                    ),
                )
            } else {
                MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = app.label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PageIndicatorDots(
    pageCount: Int,
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
) {
    if (pageCount <= 1) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        for (i in 0 until pageCount) {
            val active = i == currentPage
            Box(
                modifier = Modifier
                    .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (active) activeColor else inactiveColor)
                    .clickable { onSelectPage(i) }
            )
        }
    }
}

/**
 * Standard Material 3 DropdownMenu anchored to the long-pressed app icon's bounds.
 */
@Composable
fun AppContextMenuPopup(
    target: AppMenuTarget,
    isPinnedDock: Boolean,
    appGestures: Map<SwipeDirection, String>,
    appsByKey: Map<String, AppItem>,
    shortcutsByTargetKey: Map<String, AppShortcutItem>,
    appOwnShortcuts: List<AppShortcutItem>,
    hasShortcutHostPermission: Boolean,
    onDismiss: () -> Unit,
    onLaunchShortcut: (AppShortcutItem) -> Unit,
    onOpenDefaultHomeSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onOpenGestureConfig: () -> Unit,
    onPinToCurrentHomePage: () -> Unit,
    onRemoveFromHomePage: () -> Unit,
    onOpenMovePage: () -> Unit,
    onOpenDrawerTabs: () -> Unit,
    onTogglePinDock: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val density = LocalDensity.current
    val iconBounds = target.anchorBounds
    val app = target.app

    Box(modifier = Modifier.fillMaxSize()) {
        val anchorModifier = if (iconBounds != null) {
            val widthDp = with(density) { iconBounds.width.toDp().coerceAtLeast(1.dp) }
            val heightDp = with(density) { iconBounds.height.toDp().coerceAtLeast(1.dp) }
            Modifier
                .offset { IntOffset(iconBounds.left.roundToInt(), iconBounds.top.roundToInt()) }
                .size(width = widthDp, height = heightDp)
        } else {
            Modifier.align(Alignment.Center)
        }

        Box(modifier = anchorModifier) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("app_context_menu_popup"),
            ) {
                if (appOwnShortcuts.isNotEmpty()) {
                    appOwnShortcuts.forEach { shortcut ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = shortcut.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = {
                                ShortcutIconImage(shortcut = shortcut, size = 24.dp)
                            },
                            onClick = { onLaunchShortcut(shortcut) },
                        )
                    }
                    HorizontalDivider()
                } else if (!hasShortcutHostPermission && !app.isSelfLauncher) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_enable_app_shortcuts),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Home, contentDescription = null)
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.primary,
                            leadingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                        onClick = onOpenDefaultHomeSettings,
                        modifier = Modifier.testTag("menu_enable_app_shortcuts"),
                    )
                    HorizontalDivider()
                }

                if (app.isSelfLauncher) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.settings_title)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Settings, contentDescription = null) },
                        onClick = onOpenLauncherSettings,
                        modifier = Modifier.testTag("menu_launcher_settings"),
                    )
                }

                if (target.origin == AppActionOrigin.HOME || target.origin == AppActionOrigin.DOCK) {
                    val summaryParts = buildList {
                        fun resolveLabel(key: String): String? =
                            appsByKey[key]?.label ?: shortcutsByTargetKey[key]?.displayLabel

                        appGestures[SwipeDirection.UP]?.let { key ->
                            resolveLabel(key)?.let { add(stringResource(R.string.gesture_short_up, it)) }
                        }
                        appGestures[SwipeDirection.DOWN]?.let { key ->
                            resolveLabel(key)?.let { add(stringResource(R.string.gesture_short_down, it)) }
                        }
                        appGestures[SwipeDirection.LEFT]?.let { key ->
                            resolveLabel(key)?.let { add(stringResource(R.string.gesture_short_left, it)) }
                        }
                        appGestures[SwipeDirection.RIGHT]?.let { key ->
                            resolveLabel(key)?.let { add(stringResource(R.string.gesture_short_right, it)) }
                        }
                    }
                    val gestureSubtitle = if (summaryParts.isEmpty()) {
                        stringResource(R.string.action_dock_gestures_none)
                    } else {
                        summaryParts.joinToString(" · ")
                    }

                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = stringResource(R.string.action_dock_gestures),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = gestureSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Swipe, contentDescription = null) },
                        onClick = onOpenGestureConfig,
                        modifier = Modifier.testTag("menu_app_gestures"),
                    )
                }

                when (target.origin) {
                    AppActionOrigin.HOME -> {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_move_home_page)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null)
                            },
                            onClick = onOpenMovePage,
                            modifier = Modifier.testTag("menu_move_home_page"),
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_unpin_home)) },
                            leadingIcon = { Icon(imageVector = Icons.Rounded.Home, contentDescription = null) },
                            onClick = onRemoveFromHomePage,
                            modifier = Modifier.testTag("menu_unpin_home"),
                        )
                    }

                    AppActionOrigin.DRAWER -> {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_pin_home)) },
                            leadingIcon = { Icon(imageVector = Icons.Rounded.Home, contentDescription = null) },
                            onClick = onPinToCurrentHomePage,
                            modifier = Modifier.testTag("menu_pin_home"),
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_manage_drawer_tabs)) },
                            leadingIcon = { Icon(imageVector = Icons.Rounded.Tab, contentDescription = null) },
                            onClick = onOpenDrawerTabs,
                            modifier = Modifier.testTag("menu_drawer_tabs"),
                        )
                    }

                    AppActionOrigin.DOCK -> Unit
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(
                                if (isPinnedDock) R.string.action_unpin_dock else R.string.action_pin_dock
                            )
                        )
                    },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Dock, contentDescription = null) },
                    onClick = onTogglePinDock,
                    modifier = Modifier.testTag("menu_toggle_dock"),
                )

                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.action_app_info)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Info, contentDescription = null) },
                    onClick = onAppInfo,
                    modifier = Modifier.testTag("menu_app_info"),
                )

                if (!app.isSystemApp && !app.isSelfLauncher) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_uninstall)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = null) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error,
                        ),
                        onClick = onUninstall,
                        modifier = Modifier.testTag("menu_uninstall"),
                    )
                }
            }
        }
    }
}
