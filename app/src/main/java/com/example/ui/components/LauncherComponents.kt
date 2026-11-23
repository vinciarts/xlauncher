package com.example.ui.components

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.example.R
import com.example.model.AppActionOrigin
import com.example.model.AppItem
import com.example.model.AppMenuTarget
import com.example.model.AppShortcutItem
import com.example.model.SwipeDirection
import kotlin.math.abs

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
 * - When drag callbacks are omitted (Dock / App Drawer), uses official Compose combinedClickable
 *   plus optional directional swipe recognizer when boundDirections is non-empty.
 * - When drag callbacks are provided (Home screen), uses a unified Android ViewConfiguration
 *   gesture detector supporting quick tap, bound 4-direction swipes, long-press menu (hold + release),
 *   and AOSP long-press-and-drag repositioning (hold + slide > touchSlop).
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
    onLongClick: () -> Unit,
    onLongPressHoldChanged: ((Boolean) -> Unit)? = null,
    onSwipe: (SwipeDirection) -> Unit,
    onDragStart: ((Rect, Offset) -> Unit)? = null,
    onDragMove: ((Offset) -> Unit)? = null,
    onDragEnd: ((Offset) -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hasGestures = boundDirections.isNotEmpty()
    val dragEnabled = onDragStart != null
    val coordsHolder = remember { CoordinatesHolder() }
    var longPressTriggeredInGesture by remember { mutableStateOf(false) }

    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val pagingTouchSlopPx = viewConfig.scaledPagingTouchSlop.toFloat()
    val minFlingVelocityPx = viewConfig.scaledMinimumFlingVelocity.toFloat()

    val currentBoundDirections by rememberUpdatedState(boundDirections)
    val currentOnDownPointer by rememberUpdatedState(onDownPointer)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnLongPressHoldChanged by rememberUpdatedState(onLongPressHoldChanged)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragMove by rememberUpdatedState(onDragMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    val interactionModifier = if (dragEnabled) {
        val interactionSource = remember { MutableInteractionSource() }
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .indication(interactionSource, ripple())
            .semantics {
                role = Role.Button
                onClick(label = app.label) {
                    currentOnClick()
                    true
                }
            }
            .pointerInput(app.componentKey, pagingTouchSlopPx, minFlingVelocityPx) {
                val touchSlop = viewConfiguration.touchSlop
                val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnDownPointer?.invoke(down.id.value, currentBoundDirections)
                    val pressInteraction = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(pressInteraction)

                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)
                    val deadline = SystemClock.uptimeMillis() + longPressTimeoutMs
                    var claimedSwipeDir: SwipeDirection? = null
                    var longPressFired = false
                    var lastPos = down.position
                    var pressReleased = false

                    while (true) {
                        if (claimedSwipeDir != null) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                interactionSource.tryEmit(PressInteraction.Cancel(pressInteraction))
                                pressReleased = true
                                break
                            }
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val totalDrag = change.position - down.position
                            val absX = abs(totalDrag.x)
                            val absY = abs(totalDrag.y)
                            if (!change.pressed) {
                                change.consume()
                                interactionSource.tryEmit(PressInteraction.Cancel(pressInteraction))
                                pressReleased = true
                                val dir = claimedSwipeDir!!
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
                                break
                            }
                            change.consume()
                        } else {
                            val remaining = deadline - SystemClock.uptimeMillis()
                            if (remaining <= 0L) {
                                longPressFired = true
                                break
                            }
                            val event = withTimeoutOrNull(remaining) {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                longPressFired = true
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed) {
                                interactionSource.tryEmit(PressInteraction.Cancel(pressInteraction))
                                pressReleased = true
                                break
                            }
                            lastPos = change.position
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val totalDrag = change.position - down.position
                            val absX = abs(totalDrag.x)
                            val absY = abs(totalDrag.y)

                            if (!change.pressed) {
                                if (totalDrag.getDistance() <= touchSlop) {
                                    change.consume()
                                    interactionSource.tryEmit(PressInteraction.Release(pressInteraction))
                                    pressReleased = true
                                    currentOnClick()
                                } else {
                                    interactionSource.tryEmit(PressInteraction.Cancel(pressInteraction))
                                    pressReleased = true
                                }
                                break
                            }

                            if (totalDrag.getDistance() > touchSlop) {
                                val dir = if (absY >= absX) {
                                    if (totalDrag.y < 0f) SwipeDirection.UP else SwipeDirection.DOWN
                                } else {
                                    if (totalDrag.x < 0f) SwipeDirection.LEFT else SwipeDirection.RIGHT
                                }
                                if (dir in currentBoundDirections) {
                                    claimedSwipeDir = dir
                                    change.consume()
                                } else {
                                    interactionSource.tryEmit(PressInteraction.Cancel(pressInteraction))
                                    pressReleased = true
                                    break
                                }
                            }
                        }
                    }

                    if (longPressFired) {
                        // Claim all 4 directions for this pointer so HomeScreen gestures never steal post-long-press drag
                        currentOnDownPointer?.invoke(down.id.value, SwipeDirection.entries.toSet())
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val startBounds = coordsHolder.coords?.takeIf { it.isAttached }?.boundsInWindow()
                        val anchorWindow = coordsHolder.coords?.takeIf { it.isAttached }?.localToWindow(lastPos) ?: lastPos
                        var dragStarted = false
                        var latestWindowPos = anchorWindow

                        // Open context menu immediately on long-press (standard Android launcher behavior);
                        // keep Popup non-focusable until finger lifts so sliding past touchSlop transitions cleanly into drag.
                        currentOnLongPressHoldChanged?.invoke(true)
                        currentOnLongClick()

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    if (dragStarted) currentOnDragCancel?.invoke()
                                    break
                                }
                                change.consume()
                                val nowWindow = coordsHolder.coords?.takeIf { it.isAttached }?.localToWindow(change.position)
                                    ?: latestWindowPos
                                latestWindowPos = nowWindow

                                if (!change.pressed) {
                                    if (dragStarted) {
                                        currentOnDragEnd?.invoke(nowWindow)
                                    }
                                    break
                                }

                                if (!dragStarted) {
                                    if ((nowWindow - anchorWindow).getDistance() > touchSlop) {
                                        dragStarted = true
                                        currentOnLongPressHoldChanged?.invoke(false)
                                        interactionSource.tryEmit(PressInteraction.Cancel(pressInteraction))
                                        pressReleased = true
                                        currentOnDragStart?.invoke(startBounds ?: Rect.Zero, nowWindow)
                                    }
                                } else {
                                    currentOnDragMove?.invoke(nowWindow)
                                }
                            }
                        } finally {
                            currentOnLongPressHoldChanged?.invoke(false)
                        }
                        if (!pressReleased) {
                            interactionSource.tryEmit(PressInteraction.Release(pressInteraction))
                        }
                    }
                }
            }
    } else {
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

        swipeModifier.combinedClickable(
            onClick = { currentOnClick() },
            onLongClick = {
                longPressTriggeredInGesture = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnLongClick()
            },
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .onGloballyPositioned { coordsHolder.coords = it }
            .minimumInteractiveComponentSize()
            .then(interactionModifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        AppIconImage(app = app, size = iconSize)
        if (showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
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

internal class ExpressiveMenuItemSpec(
    val text: @Composable () -> Unit,
    val leadingIcon: (@Composable () -> Unit)? = null,
    val trailingIcon: (@Composable () -> Unit)? = null,
    val colors: MenuItemColors? = null,
    val testTag: String? = null,
    val submenuGroups: List<List<ExpressiveMenuItemSpec>>? = null,
    val onClick: () -> Unit = {},
)

/**
 * Renders segmented Material 3 Expressive [DropdownMenuGroup] sections inside a [DropdownMenuPopup],
 * including optional official cascading submenus via nested [DropdownMenuPopup].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveMenuGroupsContent(
    groups: List<List<ExpressiveMenuItemSpec>>,
    parentExpanded: Boolean,
    onActionClicked: () -> Unit = {},
) {
    val nonEmptyGroups = groups.filter { it.isNotEmpty() }
    nonEmptyGroups.forEachIndexed { groupIndex, items ->
        if (groupIndex > 0) {
            Spacer(modifier = Modifier.height(MenuDefaults.GroupSpacing))
        }
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(groupIndex, nonEmptyGroups.size),
        ) {
            items.forEachIndexed { itemIndex, item ->
                val itemShape = MenuDefaults.itemShape(itemIndex, items.size).shape
                val submenu = item.submenuGroups?.filter { it.isNotEmpty() }
                if (!submenu.isNullOrEmpty()) {
                    var submenuExpanded by remember(parentExpanded) { mutableStateOf(false) }
                    Box {
                        DropdownMenuItem(
                            text = item.text,
                            onClick = { submenuExpanded = !submenuExpanded },
                            shape = itemShape,
                            leadingIcon = item.leadingIcon,
                            trailingIcon = item.trailingIcon ?: {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowRight,
                                    contentDescription = null,
                                )
                            },
                            colors = item.colors ?: MenuDefaults.itemColors(),
                            modifier = if (item.testTag != null) Modifier.testTag(item.testTag) else Modifier,
                        )
                        DropdownMenuPopup(
                            expanded = parentExpanded && submenuExpanded,
                            onDismissRequest = { submenuExpanded = false },
                            properties = PopupProperties(focusable = true),
                        ) {
                            ExpressiveMenuGroupsContent(
                                groups = submenu,
                                parentExpanded = parentExpanded && submenuExpanded,
                                onActionClicked = {
                                    submenuExpanded = false
                                    onActionClicked()
                                },
                            )
                        }
                    }
                } else {
                    DropdownMenuItem(
                        text = item.text,
                        onClick = {
                            onActionClicked()
                            item.onClick()
                        },
                        shape = itemShape,
                        leadingIcon = item.leadingIcon,
                        trailingIcon = item.trailingIcon,
                        colors = item.colors ?: MenuDefaults.itemColors(),
                        modifier = if (item.testTag != null) Modifier.testTag(item.testTag) else Modifier,
                    )
                }
            }
        }
    }
}

@Immutable
data class AppContextMenuHost(
    val activeTarget: AppMenuTarget?,
    val dockKeys: List<String>,
    val appGestures: Map<String, Map<SwipeDirection, String>>,
    val appsByKey: Map<String, AppItem>,
    val shortcutsByTargetKey: Map<String, AppShortcutItem>,
    val hasShortcutHostPermission: Boolean,
    val hasWidgetsForPackage: (String) -> Boolean,
    val onOpen: (AppItem, AppActionOrigin, Int) -> Unit,
    val onDismiss: () -> Unit,
    val onLaunchShortcut: (AppShortcutItem) -> Unit,
    val onOpenDefaultHomeSettings: () -> Unit,
    val onOpenLauncherSettings: () -> Unit,
    val onOpenAppWidgets: (String) -> Unit,
    val onOpenGestureConfig: (AppItem) -> Unit,
    val onPinToCurrentHomePage: (AppItem) -> Unit,
    val onRemoveFromHomePage: (AppItem, Int) -> Unit,
    val onOpenMovePage: (AppMenuTarget) -> Unit,
    val onOpenDrawerTabs: (AppItem) -> Unit,
    val onTogglePinDock: (AppItem) -> Unit,
    val onAppInfo: (AppItem) -> Unit,
    val onUninstall: (AppItem) -> Unit,
) {
    fun isExpandedFor(app: AppItem, origin: AppActionOrigin, sourcePageIndex: Int): Boolean {
        val target = activeTarget ?: return false
        return target.origin == origin &&
            target.sourcePageIndex == sourcePageIndex &&
            target.app.componentKey == app.componentKey
    }
}

/**
 * Official Material 3 Expressive grouped context menu mounted natively inside the trigger app icon's [Box].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppContextMenuDropdown(
    app: AppItem,
    origin: AppActionOrigin,
    sourcePageIndex: Int,
    expanded: Boolean,
    host: AppContextMenuHost,
    isPointerHeld: Boolean = false,
) {
    // Keep this app's loaded shortcuts stable while its exit animation finishes
    var cachedShortcuts by remember(app.componentKey) { mutableStateOf<List<AppShortcutItem>>(emptyList()) }
    if (expanded) {
        host.activeTarget?.shortcuts?.let { cachedShortcuts = it }
    }

    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = host.onDismiss,
        properties = PopupProperties(
            focusable = !isPointerHeld,
            dismissOnClickOutside = !isPointerHeld,
        ),
        modifier = Modifier.testTag("app_context_menu_popup"),
    ) {
        val appOwnShortcuts = cachedShortcuts
        val isPinnedDock = app.componentKey in host.dockKeys
        val hasWidgets = remember(app.packageName) { host.hasWidgetsForPackage(app.packageName) }
        val appGestures = host.appGestures[app.componentKey].orEmpty()
        val hasTopLevelItems = appOwnShortcuts.isNotEmpty() ||
            (!host.hasShortcutHostPermission && !app.isSelfLauncher) ||
            app.isSelfLauncher

        val gestureSummaryParts = if (origin == AppActionOrigin.HOME || origin == AppActionOrigin.DOCK) {
            buildList {
                fun resolveLabel(key: String): String? =
                    host.appsByKey[key]?.label ?: host.shortcutsByTargetKey[key]?.displayLabel

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
        } else {
            emptyList()
        }

        val primaryItemColors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.primary,
            leadingIconColor = MaterialTheme.colorScheme.primary,
        )
        val errorItemColors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.error,
            leadingIconColor = MaterialTheme.colorScheme.error,
        )

        val topLevelGroup = buildList {
            if (appOwnShortcuts.isNotEmpty()) {
                appOwnShortcuts.forEach { shortcut ->
                    add(
                        ExpressiveMenuItemSpec(
                            text = {
                                Text(
                                    text = shortcut.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = { ShortcutIconImage(shortcut = shortcut, size = 24.dp) },
                            onClick = { host.onLaunchShortcut(shortcut) },
                        )
                    )
                }
            } else if (!host.hasShortcutHostPermission && !app.isSelfLauncher) {
                add(
                    ExpressiveMenuItemSpec(
                        text = {
                            Text(
                                text = stringResource(R.string.action_enable_app_shortcuts),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Home, contentDescription = null) },
                        colors = primaryItemColors,
                        testTag = "menu_enable_app_shortcuts",
                        onClick = host.onOpenDefaultHomeSettings,
                    )
                )
            }

            if (app.isSelfLauncher) {
                add(
                    ExpressiveMenuItemSpec(
                        text = { Text(text = stringResource(R.string.settings_title)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Settings, contentDescription = null) },
                        testTag = "menu_launcher_settings",
                        onClick = host.onOpenLauncherSettings,
                    )
                )
            }
        }

        val actionsGroup = buildList {
            if (hasWidgets) {
                add(
                    ExpressiveMenuItemSpec(
                        text = { Text(text = stringResource(R.string.action_app_widgets)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Widgets, contentDescription = null) },
                        testTag = "menu_app_widgets",
                        onClick = { host.onOpenAppWidgets(app.packageName) },
                    )
                )
            }

            if (origin == AppActionOrigin.HOME || origin == AppActionOrigin.DOCK) {
                add(
                    ExpressiveMenuItemSpec(
                        text = {
                            if (gestureSummaryParts.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.action_dock_gestures),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Column {
                                    Text(
                                        text = stringResource(R.string.action_dock_gestures),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = gestureSummaryParts.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.Swipe, contentDescription = null) },
                        testTag = "menu_app_gestures",
                        onClick = { host.onOpenGestureConfig(app) },
                    )
                )
            }

            when (origin) {
                AppActionOrigin.HOME -> {
                    add(
                        ExpressiveMenuItemSpec(
                            text = { Text(text = stringResource(R.string.action_move_home_page)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null)
                            },
                            testTag = "menu_move_home_page",
                            onClick = {
                                host.onOpenMovePage(
                                    AppMenuTarget(
                                        app = app,
                                        origin = origin,
                                        sourcePageIndex = sourcePageIndex,
                                        shortcuts = appOwnShortcuts,
                                    )
                                )
                            },
                        )
                    )
                    add(
                        ExpressiveMenuItemSpec(
                            text = { Text(text = stringResource(R.string.action_unpin_home)) },
                            leadingIcon = { Icon(imageVector = Icons.Rounded.Home, contentDescription = null) },
                            testTag = "menu_unpin_home",
                            onClick = { host.onRemoveFromHomePage(app, sourcePageIndex) },
                        )
                    )
                }

                AppActionOrigin.DRAWER -> {
                    add(
                        ExpressiveMenuItemSpec(
                            text = { Text(text = stringResource(R.string.action_pin_home)) },
                            leadingIcon = { Icon(imageVector = Icons.Rounded.Home, contentDescription = null) },
                            testTag = "menu_pin_home",
                            onClick = { host.onPinToCurrentHomePage(app) },
                        )
                    )
                    add(
                        ExpressiveMenuItemSpec(
                            text = { Text(text = stringResource(R.string.action_manage_drawer_tabs)) },
                            leadingIcon = { Icon(imageVector = Icons.Rounded.Tab, contentDescription = null) },
                            testTag = "menu_drawer_tabs",
                            onClick = { host.onOpenDrawerTabs(app) },
                        )
                    )
                }

                AppActionOrigin.DOCK -> Unit
            }

            add(
                ExpressiveMenuItemSpec(
                    text = {
                        Text(
                            text = stringResource(
                                if (isPinnedDock) R.string.action_unpin_dock else R.string.action_pin_dock
                            )
                        )
                    },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Dock, contentDescription = null) },
                    testTag = "menu_toggle_dock",
                    onClick = { host.onTogglePinDock(app) },
                )
            )
        }

        val systemGroup = buildList {
            add(
                ExpressiveMenuItemSpec(
                    text = { Text(text = stringResource(R.string.action_app_info)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Info, contentDescription = null) },
                    testTag = "menu_app_info",
                    onClick = { host.onAppInfo(app) },
                )
            )
            if (!app.isSystemApp && !app.isSelfLauncher) {
                add(
                    ExpressiveMenuItemSpec(
                        text = { Text(text = stringResource(R.string.action_uninstall)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = null) },
                        colors = errorItemColors,
                        testTag = "menu_uninstall",
                        onClick = { host.onUninstall(app) },
                    )
                )
            }
        }

        val rootGroups = if (hasTopLevelItems) {
            listOf(
                topLevelGroup,
                listOf(
                    ExpressiveMenuItemSpec(
                        text = { Text(text = stringResource(R.string.action_more_options)) },
                        leadingIcon = { Icon(imageVector = Icons.Rounded.MoreHoriz, contentDescription = null) },
                        testTag = "menu_more_options",
                        submenuGroups = listOf(actionsGroup, systemGroup),
                    )
                ),
            )
        } else {
            listOf(actionsGroup, systemGroup)
        }

        ExpressiveMenuGroupsContent(
            groups = rootGroups,
            parentExpanded = expanded,
        )
    }
}
