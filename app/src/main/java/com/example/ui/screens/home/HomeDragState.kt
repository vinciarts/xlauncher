package com.example.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppItem
import com.example.model.HomeAppSlot
import com.example.model.HomeGridEngine
import com.example.model.PlacedWidget
import com.example.ui.components.AppIconImage
import kotlin.math.roundToInt

internal class LayoutCoordsRef(var coords: LayoutCoordinates? = null)

internal data class PageGridMetrics(
    val gridBounds: Rect = Rect.Zero,
    val cellWidthPx: Float = 160f,
    val cellHeightPx: Float = 160f,
    val colStepPx: Float = 166f,
    val rowStepPx: Float = 168f,
    val colGapPx: Float = 6f,
    val rowGapPx: Float = 8f,
)

internal sealed class DragItem {
    data class App(val slot: HomeAppSlot) : DragItem()
    data class DockApp(val app: AppItem, val index: Int) : DragItem()
    data class Widget(val widget: PlacedWidget) : DragItem()

    val spanX: Int
        get() = when (this) {
            is App, is DockApp -> 1
            is Widget -> widget.spanX
        }

    val spanY: Int
        get() = when (this) {
            is App, is DockApp -> 1
            is Widget -> widget.spanY
        }

    val sourcePage: Int
        get() = when (this) {
            is App -> slot.pageIndex
            is DockApp -> 0
            is Widget -> widget.pageIndex
        }
}

internal data class HomeDragSession(
    val item: DragItem,
    val pointerWindowPos: Offset,
    val grabOffsetInItem: Offset,
    val itemSizePx: Size,
    val targetPage: Int,
    val targetCellX: Int,
    val targetCellY: Int,
    val targetDockIndex: Int? = null,
    val targetDockSlotCount: Int = 0,
    val canDrop: Boolean,
    val isSwapTarget: Boolean,
    val isOverRemoveZone: Boolean = false,
)

internal const val CELL_COL_GAP_DP = 6
internal const val CELL_ROW_GAP_DP = 8
internal const val GRID_HORIZONTAL_PAD_DP = 16
internal const val GRID_TOP_PAD_DP = 12
internal const val GRID_BOTTOM_PAD_DP = 8
internal const val DRAG_EDGE_INNER_DP = 32
internal const val DRAG_EDGE_FLIP_DELAY_MS = 450L
internal const val DRAG_NEW_FRONT_PAGE = -1

@Composable
internal fun HomeDragOverlay(
    session: HomeDragSession,
    gridMetrics: PageGridMetrics,
    dockBounds: Rect,
    rootPos: Offset,
    cols: Int,
    rows: Int,
    edgeIndicatorDir: Int,
) {
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val highlightColor = when {
        !session.canDrop -> errorColor
        session.isSwapTarget -> secondaryColor
        else -> primaryColor
    }

    val sx = session.item.spanX.coerceIn(1, cols.coerceIn(HomeGridEngine.MIN_HOME_GRID_COLUMNS, HomeGridEngine.MAX_HOME_GRID_COLUMNS))
    val sy = session.item.spanY.coerceIn(1, rows.coerceIn(HomeGridEngine.MIN_HOME_GRID_ROWS, HomeGridEngine.MAX_HOME_GRID_ROWS))

    Box(
        contentAlignment = Alignment.TopStart,
        modifier = Modifier.fillMaxSize(),
    ) {
        // Target cell highlight snapped directly to the 2D CellLayout grid or Dock slot + edge-flip cue
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!session.isOverRemoveZone) {
                val targetLeft: Float
                val targetTop: Float
                val targetW: Float
                val targetH: Float

                val dockIdx = session.targetDockIndex
                if (dockIdx != null && dockBounds != Rect.Zero) {
                    val slotCount = session.targetDockSlotCount.coerceAtLeast(1)
                    val slotW = dockBounds.width / slotCount
                    targetLeft = dockBounds.left - rootPos.x + dockIdx * slotW
                    targetTop = dockBounds.top - rootPos.y
                    targetW = slotW
                    targetH = dockBounds.height
                } else {
                    targetLeft = gridMetrics.gridBounds.left - rootPos.x + session.targetCellX * gridMetrics.colStepPx
                    targetTop = gridMetrics.gridBounds.top - rootPos.y + session.targetCellY * gridMetrics.rowStepPx
                    targetW = sx * gridMetrics.cellWidthPx + (sx - 1) * gridMetrics.colGapPx
                    targetH = sy * gridMetrics.cellHeightPx + (sy - 1) * gridMetrics.rowGapPx
                }
                val radius = CornerRadius(16.dp.toPx(), 16.dp.toPx())

                drawRoundRect(
                    color = highlightColor.copy(alpha = 0.16f),
                    topLeft = Offset(targetLeft, targetTop),
                    size = Size(targetW, targetH),
                    cornerRadius = radius,
                )
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(targetLeft, targetTop),
                    size = Size(targetW, targetH),
                    cornerRadius = radius,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            if (edgeIndicatorDir != 0) {
                val barW = 4.dp.toPx()
                val barH = gridMetrics.gridBounds.height * 0.45f
                val barTop = gridMetrics.gridBounds.top - rootPos.y + (gridMetrics.gridBounds.height - barH) / 2f
                val barLeft = if (edgeIndicatorDir < 0) {
                    (gridMetrics.gridBounds.left - rootPos.x - 10.dp.toPx()).coerceAtLeast(4.dp.toPx())
                } else {
                    (gridMetrics.gridBounds.right - rootPos.x + 6.dp.toPx()).coerceAtMost(size.width - barW - 4.dp.toPx())
                }
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.85f),
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                )
            }
        }

        // Floating drag preview tracking finger smoothly in root coordinates
        val ghostTopLeft = session.pointerWindowPos - session.grabOffsetInItem - rootPos
        val ghostWidthDp = with(density) { session.itemSizePx.width.coerceAtLeast(48f).toDp() }
        val ghostHeightDp = with(density) { session.itemSizePx.height.coerceAtLeast(48f).toDp() }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset { IntOffset(ghostTopLeft.x.roundToInt(), ghostTopLeft.y.roundToInt()) }
                .size(width = ghostWidthDp, height = ghostHeightDp)
                .graphicsLayer {
                    scaleX = if (session.isOverRemoveZone) 0.9f else 1.05f
                    scaleY = if (session.isOverRemoveZone) 0.9f else 1.05f
                    alpha = if (session.isOverRemoveZone) 0.72f else 0.92f
                },
        ) {
            when (val dragged = session.item) {
                is DragItem.App -> {
                    val iconSize = when {
                        cols >= 7 -> 38.dp
                        cols >= 5 -> 48.dp
                        else -> 54.dp
                    }
                    AppIconImage(app = dragged.slot.app, size = iconSize)
                }

                is DragItem.DockApp -> {
                    AppIconImage(app = dragged.app, size = 50.dp)
                }

                is DragItem.Widget -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
                        tonalElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.5.dp, highlightColor, RoundedCornerShape(16.dp)),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(R.string.widget_size_label, sx, sy),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
