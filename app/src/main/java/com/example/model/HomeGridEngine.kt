package com.example.model

import kotlin.math.abs

/**
 * Pure 2D cell-grid layout engine (AOSP CellLayout style) for Home screen apps and widgets.
 * Guarantees valid in-bounds, non-overlapping (cellX, cellY, spanX, spanY) coordinates per page.
 */
object HomeGridEngine {

    const val MIN_HOME_GRID_COLUMNS = 1
    const val MAX_HOME_GRID_COLUMNS = 8
    const val DEFAULT_HOME_GRID_COLUMNS = 4

    const val MIN_HOME_GRID_ROWS = 1
    const val MAX_HOME_GRID_ROWS = 12
    const val DEFAULT_HOME_GRID_ROWS = 6
    const val HOME_GRID_ROWS = DEFAULT_HOME_GRID_ROWS

    fun buildOccupancy(
        widgets: List<PlacedWidget>,
        apps: List<PlacedHomeApp>,
        cols: Int,
        rows: Int = HOME_GRID_ROWS,
        ignoreWidgetId: Int? = null,
        ignoreAppKey: String? = null,
    ): Array<BooleanArray> {
        val grid = Array(rows) { BooleanArray(cols) }
        for (w in widgets) {
            if (w.appWidgetId == ignoreWidgetId) continue
            if (w.cellX < 0 || w.cellY < 0) continue
            val sx = w.spanX.coerceIn(1, cols)
            val sy = w.spanY.coerceIn(1, rows)
            for (y in w.cellY until (w.cellY + sy).coerceAtMost(rows)) {
                for (x in w.cellX until (w.cellX + sx).coerceAtMost(cols)) {
                    if (y >= 0 && x >= 0) grid[y][x] = true
                }
            }
        }
        for (a in apps) {
            if (a.componentKey == ignoreAppKey) continue
            if (a.cellX in 0 until cols && a.cellY in 0 until rows) {
                grid[a.cellY][a.cellX] = true
            }
        }
        return grid
    }

    fun canPlace(
        occupancy: Array<BooleanArray>,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
        cols: Int,
        rows: Int = HOME_GRID_ROWS,
    ): Boolean {
        if (spanX <= 0 || spanY <= 0) return false
        if (cellX < 0 || cellY < 0) return false
        if (cellX + spanX > cols || cellY + spanY > rows) return false
        for (y in cellY until cellY + spanY) {
            for (x in cellX until cellX + spanX) {
                if (occupancy[y][x]) return false
            }
        }
        return true
    }

    fun markOccupied(
        occupancy: Array<BooleanArray>,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
        cols: Int,
        rows: Int = HOME_GRID_ROWS,
    ) {
        for (y in cellY until (cellY + spanY).coerceAtMost(rows)) {
            for (x in cellX until (cellX + spanX).coerceAtMost(cols)) {
                if (y in 0 until rows && x in 0 until cols) {
                    occupancy[y][x] = true
                }
            }
        }
    }

    fun findFirstAvailableCell(
        occupancy: Array<BooleanArray>,
        spanX: Int,
        spanY: Int,
        cols: Int,
        rows: Int = HOME_GRID_ROWS,
    ): Pair<Int, Int>? {
        val sx = spanX.coerceIn(1, cols)
        val sy = spanY.coerceIn(1, rows)
        for (y in 0..(rows - sy)) {
            for (x in 0..(cols - sx)) {
                if (canPlace(occupancy, x, y, sx, sy, cols, rows)) {
                    return x to y
                }
            }
        }
        return null
    }

    fun findNearestAvailableCell(
        occupancy: Array<BooleanArray>,
        desiredX: Int,
        desiredY: Int,
        spanX: Int,
        spanY: Int,
        cols: Int,
        rows: Int = HOME_GRID_ROWS,
    ): Pair<Int, Int>? {
        val sx = spanX.coerceIn(1, cols)
        val sy = spanY.coerceIn(1, rows)
        val clampedX = desiredX.coerceIn(0, cols - sx)
        val clampedY = desiredY.coerceIn(0, rows - sy)
        if (canPlace(occupancy, clampedX, clampedY, sx, sy, cols, rows)) {
            return clampedX to clampedY
        }
        var best: Pair<Int, Int>? = null
        var bestDist = Int.MAX_VALUE
        for (y in 0..(rows - sy)) {
            for (x in 0..(cols - sx)) {
                if (canPlace(occupancy, x, y, sx, sy, cols, rows)) {
                    val dist = abs(x - clampedX) * 10 + abs(y - clampedY) * 10 + y
                    if (dist < bestDist) {
                        bestDist = dist
                        best = x to y
                    }
                }
            }
        }
        return best
    }

    /**
     * Normalizes a single page's widgets & apps so every item has valid non-overlapping
     * (cellX, cellY) coordinates, preserving already-valid positions and cleanly placing
     * legacy (-1, -1) or out-of-bounds items (e.g. after switching 5 -> 4 columns).
     */
    fun normalizePage(
        widgets: List<PlacedWidget>,
        apps: List<PlacedHomeApp>,
        cols: Int,
        rows: Int = HOME_GRID_ROWS,
    ): Pair<List<PlacedWidget>, List<PlacedHomeApp>> {
        val occupancy = Array(rows) { BooleanArray(cols) }
        val resolvedWidgets = ArrayList<PlacedWidget>(widgets.size)
        val pendingWidgets = mutableListOf<PlacedWidget>()

        // Pass 1: lock in widgets that already have valid non-overlapping positions
        for (w in widgets) {
            val sx = w.spanX.coerceIn(1, cols)
            val sy = w.spanY.coerceIn(1, rows)
            if (w.cellX >= 0 && w.cellY >= 0 && canPlace(occupancy, w.cellX, w.cellY, sx, sy, cols, rows)) {
                val fixed = w.copy(spanX = sx, spanY = sy)
                markOccupied(occupancy, fixed.cellX, fixed.cellY, sx, sy, cols, rows)
                resolvedWidgets.add(fixed)
            } else {
                pendingWidgets.add(w.copy(spanX = sx, spanY = sy))
            }
        }

        // Pass 2: lock in apps that already have valid non-overlapping positions
        val resolvedApps = ArrayList<PlacedHomeApp>(apps.size)
        val pendingApps = mutableListOf<PlacedHomeApp>()
        for (a in apps) {
            if (a.cellX >= 0 && a.cellY >= 0 && canPlace(occupancy, a.cellX, a.cellY, 1, 1, cols, rows)) {
                markOccupied(occupancy, a.cellX, a.cellY, 1, 1, cols, rows)
                resolvedApps.add(a)
            } else {
                pendingApps.add(a)
            }
        }

        // Pass 3: place pending widgets (legacy or shifted after column change)
        for (w in pendingWidgets) {
            val desiredX = if (w.cellX >= 0) w.cellX else 0
            val desiredY = if (w.cellY >= 0) w.cellY else 0
            val slot = findNearestAvailableCell(occupancy, desiredX, desiredY, w.spanX, w.spanY, cols, rows)
            if (slot != null) {
                markOccupied(occupancy, slot.first, slot.second, w.spanX, w.spanY, cols, rows)
                resolvedWidgets.add(w.copy(cellX = slot.first, cellY = slot.second))
            } else {
                // Shrink span to fit if page is tight
                val fallback = findFirstAvailableCell(occupancy, 1, 1, cols, rows)
                if (fallback != null) {
                    markOccupied(occupancy, fallback.first, fallback.second, 1, 1, cols, rows)
                    resolvedWidgets.add(w.copy(cellX = fallback.first, cellY = fallback.second, spanX = 1, spanY = 1))
                }
            }
        }

        // Pass 4: place pending apps (legacy or shifted after column change)
        for (a in pendingApps) {
            val slot = if (a.cellX >= 0 && a.cellY >= 0) {
                findNearestAvailableCell(occupancy, a.cellX, a.cellY, 1, 1, cols, rows)
            } else {
                findFirstAvailableCell(occupancy, 1, 1, cols, rows)
            }
            if (slot != null) {
                markOccupied(occupancy, slot.first, slot.second, 1, 1, cols, rows)
                resolvedApps.add(a.copy(cellX = slot.first, cellY = slot.second))
            }
        }

        return resolvedWidgets to resolvedApps
    }
}
