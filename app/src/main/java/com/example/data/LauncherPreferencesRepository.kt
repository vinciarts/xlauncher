package com.example.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DrawerTab
import com.example.model.HomeGestureAction
import com.example.model.HomeGridEngine
import com.example.model.IconShape
import com.example.model.LauncherSettings
import com.example.model.PlacedHomeApp
import com.example.model.PlacedWidget
import com.example.model.SwipeDirection
import com.example.model.WebSearchEngine
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private const val CURRENT_SCHEMA_VERSION = 2
private const val PREF_PAGE_SEP = ";"
private const val PREF_ITEM_SEP = ","
private const val PREF_KV_SEP = ":"
private const val PREF_WIDGET_FIELD_SEP = "|"

private object PrefKeys {
    val SCHEMA_VERSION = intPreferencesKey("schema_version")
    val LEGACY_HOME_PAGES_DATA = stringPreferencesKey("home_pages_data")
    val HOME_APPS_GRID = stringPreferencesKey("home_apps_grid")
    val SEARCH_THIRD_PARTY_PACKAGES = stringPreferencesKey("search_third_party_packages")
    val SEARCH_THIRD_PARTY_APPS_ENABLED = booleanPreferencesKey("search_third_party_apps_enabled")
}

private object LauncherPrefsMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[PrefKeys.SCHEMA_VERSION] ?: 1) < CURRENT_SCHEMA_VERSION

    override suspend fun migrate(currentData: Preferences): Preferences {
        val mutable = currentData.toMutablePreferences()
        val version = currentData[PrefKeys.SCHEMA_VERSION] ?: 1
        if (version < 2) {
            // Migrate v1 home_pages_data -> home_apps_grid if grid is not yet populated
            if (mutable[PrefKeys.HOME_APPS_GRID] == null) {
                val legacyPages = decodeLegacyHomePages(mutable[PrefKeys.LEGACY_HOME_PAGES_DATA])
                if (legacyPages.isNotEmpty()) {
                    val migratedApps = mutableListOf<PlacedHomeApp>()
                    legacyPages.entries.sortedBy { it.key }.forEach { (page, keys) ->
                        keys.distinct().forEach { key ->
                            migratedApps.add(
                                PlacedHomeApp(componentKey = key, pageIndex = page, cellX = -1, cellY = -1)
                            )
                        }
                    }
                    mutable[PrefKeys.HOME_APPS_GRID] = encodePlacedHomeAppsHelper(migratedApps)
                }
            }
            mutable.remove(PrefKeys.LEGACY_HOME_PAGES_DATA)

            // Migrate v1 search_third_party_packages -> search_third_party_apps_enabled
            if (mutable[PrefKeys.SEARCH_THIRD_PARTY_APPS_ENABLED] == null &&
                mutable[PrefKeys.SEARCH_THIRD_PARTY_PACKAGES] != null
            ) {
                val hadSelected = decodeEncodedListHelper(mutable[PrefKeys.SEARCH_THIRD_PARTY_PACKAGES]).isNotEmpty()
                mutable[PrefKeys.SEARCH_THIRD_PARTY_APPS_ENABLED] = hadSelected
            }
            mutable.remove(PrefKeys.SEARCH_THIRD_PARTY_PACKAGES)
        }
        mutable[PrefKeys.SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
        return mutable.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

private fun decodeLegacyHomePages(raw: String?): Map<Int, List<String>> {
    if (raw.isNullOrBlank()) return emptyMap()
    val result = linkedMapOf<Int, List<String>>()
    raw.split(PREF_PAGE_SEP).forEach { segment ->
        val sep = segment.indexOf(PREF_KV_SEP)
        if (sep > 0) {
            val page = segment.substring(0, sep).toIntOrNull() ?: return@forEach
            val items = segment.substring(sep + 1).split(PREF_ITEM_SEP).filter { it.isNotBlank() }
            if (items.isNotEmpty()) result[page] = items
        }
    }
    return result
}

private fun encodePlacedHomeAppsHelper(apps: List<PlacedHomeApp>): String =
    apps.joinToString(PREF_PAGE_SEP) { a ->
        listOf(a.pageIndex, a.cellX, a.cellY, Uri.encode(a.componentKey)).joinToString(PREF_WIDGET_FIELD_SEP)
    }

private fun decodeEncodedListHelper(raw: String?): List<String> =
    raw?.split(PREF_PAGE_SEP)
        ?.mapNotNull { segment -> Uri.decode(segment).takeIf { it.isNotBlank() } }
        ?.distinct()
        ?: emptyList()

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "qingfeng_launcher_v2",
    produceMigrations = { listOf(LauncherPrefsMigration) },
)

class LauncherPreferencesRepository(private val context: Context) {

    private object Keys {
        val SCHEMA_VERSION = PrefKeys.SCHEMA_VERSION
        val HOME_PAGE_COUNT = intPreferencesKey("home_page_count")
        val HOME_LOOP_PAGING = booleanPreferencesKey("home_loop_paging")
        val HOME_SHOW_APP_LABELS = booleanPreferencesKey("home_show_app_labels")
        val HOME_GRID_COLUMNS = intPreferencesKey("home_grid_columns")
        val HOME_GRID_ROWS = intPreferencesKey("home_grid_rows")
        val HOME_APPS_GRID = PrefKeys.HOME_APPS_GRID
        val PLACED_WIDGETS = stringPreferencesKey("placed_widgets")
        val DOCK_KEYS = stringPreferencesKey("dock_keys")
        val DOCK_MAX_ITEMS = intPreferencesKey("dock_max_items")
        val DOCK_GESTURES = stringPreferencesKey("dock_gestures")
        val HOME_GESTURE_UP = stringPreferencesKey("home_gesture_up")
        val HOME_GESTURE_DOWN = stringPreferencesKey("home_gesture_down")
        val HOME_GESTURE_LEFT = stringPreferencesKey("home_gesture_left")
        val HOME_GESTURE_RIGHT = stringPreferencesKey("home_gesture_right")
        val HOME_BUTTON_ACTION = stringPreferencesKey("home_button_action")
        val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val DRAWER_TABS = stringPreferencesKey("drawer_tabs")
        val ALL_TAB_HIDDEN = booleanPreferencesKey("drawer_all_tab_hidden")
        val ALL_TAB_NAME = stringPreferencesKey("drawer_all_tab_name")
        val AUTO_HIDE_EMPTY_DRAWER_TABS = booleanPreferencesKey("drawer_auto_hide_empty_tabs")
        val FOLDED_DRAWER_APP_KEYS = stringPreferencesKey("drawer_folded_app_keys")
        val HAS_SEEDED_DEFAULTS = booleanPreferencesKey("has_seeded_defaults")
        val SEARCH_SHORTCUTS_ENABLED = booleanPreferencesKey("search_shortcuts_enabled")
        val SEARCH_CONTACTS_ENABLED = booleanPreferencesKey("search_contacts_enabled")
        val SEARCH_FILES_ENABLED = booleanPreferencesKey("search_files_enabled")
        val SEARCH_FILE_TREE_URIS = stringPreferencesKey("search_file_tree_uris")
        val SEARCH_THIRD_PARTY_APPS_ENABLED = PrefKeys.SEARCH_THIRD_PARTY_APPS_ENABLED
        val SEARCH_WEB_ENABLED = booleanPreferencesKey("search_web_enabled")
        val SEARCH_WEB_ENGINE = stringPreferencesKey("search_web_engine")
    }

    val settingsFlow: Flow<LauncherSettings> = context.launcherDataStore.data.map { prefs ->
        val gridApps = readHomeApps(prefs)
        LauncherSettings(
            homePageCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES),
            homeLoopPaging = prefs[Keys.HOME_LOOP_PAGING] ?: true,
            homeGridColumns = readGridColumns(prefs),
            homeGridRows = readGridRows(prefs),
            dockMaxItems = readDockMaxItems(prefs),
            placedHomeApps = gridApps.toImmutableList(),
            placedWidgets = decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]).toImmutableList(),
            dockKeys = decodeList(prefs[Keys.DOCK_KEYS]).take(MAX_DOCK_ITEMS).toImmutableList(),
            appGestures = decodeAppGestures(prefs[Keys.DOCK_GESTURES])
                .mapValues { it.value.toImmutableMap() }
                .toImmutableMap(),
            homeGestures = persistentMapOf(
                SwipeDirection.UP to (prefs[Keys.HOME_GESTURE_UP]?.let { HomeGestureAction.decode(it) }
                    ?: HomeGestureAction.OpenDrawer),
                SwipeDirection.DOWN to (prefs[Keys.HOME_GESTURE_DOWN]?.let { HomeGestureAction.decode(it) }
                    ?: HomeGestureAction.ExpandNotifications),
                SwipeDirection.LEFT to HomeGestureAction.decode(prefs[Keys.HOME_GESTURE_LEFT]),
                SwipeDirection.RIGHT to HomeGestureAction.decode(prefs[Keys.HOME_GESTURE_RIGHT]),
            ),
            homeButtonAction = prefs[Keys.HOME_BUTTON_ACTION]?.let { HomeGestureAction.decode(it) }
                ?: HomeGestureAction.GoFirstPage,
            drawerColumns = (prefs[Keys.DRAWER_COLUMNS] ?: 4)
                .coerceIn(HomeGridEngine.MIN_HOME_GRID_COLUMNS, HomeGridEngine.MAX_HOME_GRID_COLUMNS),
            iconShape = IconShape.decode(prefs[Keys.ICON_SHAPE]),
            homeShowAppLabels = prefs[Keys.HOME_SHOW_APP_LABELS] ?: true,
            drawerTabs = decodeDrawerTabs(prefs[Keys.DRAWER_TABS]).toImmutableList(),
            isAllTabHidden = prefs[Keys.ALL_TAB_HIDDEN] ?: false,
            allTabName = prefs[Keys.ALL_TAB_NAME]?.trim() ?: "",
            autoHideEmptyDrawerTabs = prefs[Keys.AUTO_HIDE_EMPTY_DRAWER_TABS] ?: false,
            foldedDrawerAppKeys = decodeEncodedList(prefs[Keys.FOLDED_DRAWER_APP_KEYS]).toImmutableSet(),
            hasSeededDefaults = prefs[Keys.HAS_SEEDED_DEFAULTS] ?: false,
            searchShortcutsEnabled = prefs[Keys.SEARCH_SHORTCUTS_ENABLED] ?: false,
            searchContactsEnabled = prefs[Keys.SEARCH_CONTACTS_ENABLED] ?: false,
            searchFilesEnabled = prefs[Keys.SEARCH_FILES_ENABLED] ?: false,
            searchFileTreeUris = decodeEncodedList(prefs[Keys.SEARCH_FILE_TREE_URIS]).toImmutableList(),
            searchThirdPartyAppsEnabled = prefs[Keys.SEARCH_THIRD_PARTY_APPS_ENABLED] ?: false,
            searchWebEnabled = prefs[Keys.SEARCH_WEB_ENABLED] ?: true,
            searchWebEngine = WebSearchEngine.decode(prefs[Keys.SEARCH_WEB_ENGINE]),
        )
    }

    private fun readHomeApps(prefs: Preferences): List<PlacedHomeApp> =
        decodePlacedHomeApps(prefs[Keys.HOME_APPS_GRID])

    private fun readGridColumns(prefs: Preferences): Int =
        (prefs[Keys.HOME_GRID_COLUMNS] ?: HomeGridEngine.DEFAULT_HOME_GRID_COLUMNS)
            .coerceIn(HomeGridEngine.MIN_HOME_GRID_COLUMNS, HomeGridEngine.MAX_HOME_GRID_COLUMNS)

    private fun readGridRows(prefs: Preferences): Int =
        (prefs[Keys.HOME_GRID_ROWS] ?: HomeGridEngine.DEFAULT_HOME_GRID_ROWS)
            .coerceIn(HomeGridEngine.MIN_HOME_GRID_ROWS, HomeGridEngine.MAX_HOME_GRID_ROWS)

    private fun readDockMaxItems(prefs: Preferences): Int =
        (prefs[Keys.DOCK_MAX_ITEMS] ?: DEFAULT_DOCK_ITEMS)
            .coerceIn(MIN_DOCK_ITEMS, MAX_DOCK_ITEMS)

    suspend fun setDockMaxItems(count: Int) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.DOCK_MAX_ITEMS] = count.coerceIn(MIN_DOCK_ITEMS, MAX_DOCK_ITEMS)
        }
    }

    private fun normalizeAllPages(
        widgets: List<PlacedWidget>,
        apps: List<PlacedHomeApp>,
        pageCount: Int,
        cols: Int,
        rows: Int,
    ): Pair<List<PlacedWidget>, List<PlacedHomeApp>> {
        val outWidgets = mutableListOf<PlacedWidget>()
        val outApps = mutableListOf<PlacedHomeApp>()
        val maxPage = (pageCount - 1).coerceAtLeast(0)
        for (p in 0..maxPage) {
            val pageW = widgets.filter { it.pageIndex == p }
            val pageA = apps.filter { it.pageIndex == p }
            val (nw, na) = HomeGridEngine.normalizePage(pageW, pageA, cols, rows)
            outWidgets.addAll(nw)
            outApps.addAll(na)
        }
        return outWidgets to outApps
    }

    suspend fun seedDefaultsIfNeeded(
        dockKeys: List<String>,
        homePages: Map<Int, List<String>>,
    ) {
        context.launcherDataStore.edit { prefs ->
            if (prefs[Keys.HAS_SEEDED_DEFAULTS] == true) return@edit
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            val seededApps = mutableListOf<PlacedHomeApp>()
            homePages.entries.sortedBy { it.key }.forEach { (page, keys) ->
                keys.distinct().forEachIndexed { idx, key ->
                    val cx = idx % cols
                    val cy = idx / cols
                    if (cy < rows) {
                        seededApps.add(PlacedHomeApp(componentKey = key, pageIndex = page, cellX = cx, cellY = cy))
                    }
                }
            }
            prefs[Keys.SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
            prefs[Keys.DOCK_KEYS] = encodeList(dockKeys.take(MAX_DOCK_ITEMS))
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(seededApps)
            prefs[Keys.HOME_PAGE_COUNT] = homePages.size.coerceIn(2, MAX_HOME_PAGES)
            prefs[Keys.HAS_SEEDED_DEFAULTS] = true
        }
    }

    suspend fun setHomePageCount(count: Int) {
        val clamped = count.coerceIn(1, MAX_HOME_PAGES)
        context.launcherDataStore.edit { prefs ->
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            prefs[Keys.HOME_PAGE_COUNT] = clamped
            val lastIdx = clamped - 1

            val currentApps = readHomeApps(prefs).map { app ->
                if (app.pageIndex >= clamped) app.copy(pageIndex = lastIdx, cellX = -1, cellY = -1)
                else app
            }
            val currentWidgets = decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]).map { w ->
                if (w.pageIndex >= clamped) w.copy(pageIndex = lastIdx, cellX = -1, cellY = -1)
                else w
            }
            val (normW, normA) = normalizeAllPages(currentWidgets, currentApps, clamped, cols, rows)
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(normA)
        }
    }

    suspend fun insertHomePageAtStart(): Boolean {
        var added = false
        context.launcherDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            if (currentCount >= MAX_HOME_PAGES) return@edit
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            prefs[Keys.HOME_PAGE_COUNT] = currentCount + 1
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW.map { it.copy(pageIndex = it.pageIndex + 1) })
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(normA.map { it.copy(pageIndex = it.pageIndex + 1) })
            added = true
        }
        return added
    }

    suspend fun appendHomePageAtEnd(): Boolean {
        var added = false
        context.launcherDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            if (currentCount >= MAX_HOME_PAGES) return@edit
            prefs[Keys.HOME_PAGE_COUNT] = currentCount + 1
            added = true
        }
        return added
    }

    suspend fun deleteHomePage(pageIndex: Int): List<Int> {
        var removedWidgetIds: List<Int> = emptyList()
        context.launcherDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            if (currentCount <= 1 || pageIndex !in 0 until currentCount) return@edit
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            removedWidgetIds = normW.filter { it.pageIndex == pageIndex }.map { it.appWidgetId }
            val updatedW = normW.mapNotNull { w ->
                when {
                    w.pageIndex == pageIndex -> null
                    w.pageIndex > pageIndex -> w.copy(pageIndex = w.pageIndex - 1)
                    else -> w
                }
            }
            val updatedA = normA.mapNotNull { a ->
                when {
                    a.pageIndex == pageIndex -> null
                    a.pageIndex > pageIndex -> a.copy(pageIndex = a.pageIndex - 1)
                    else -> a
                }
            }
            prefs[Keys.HOME_PAGE_COUNT] = currentCount - 1
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updatedW)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(updatedA)
        }
        return removedWidgetIds
    }

    suspend fun moveHomePage(pageIndex: Int, delta: Int): Boolean {
        if (delta == 0) return false
        var moved = false
        context.launcherDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val targetIndex = pageIndex + delta
            if (currentCount <= 1 || pageIndex !in 0 until currentCount || targetIndex !in 0 until currentCount) {
                return@edit
            }
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            val swapPage: (Int) -> Int = { p ->
                when (p) {
                    pageIndex -> targetIndex
                    targetIndex -> pageIndex
                    else -> p
                }
            }
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW.map { it.copy(pageIndex = swapPage(it.pageIndex)) })
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(normA.map { it.copy(pageIndex = swapPage(it.pageIndex)) })
            moved = true
        }
        return moved
    }

    suspend fun insertHomePageAndMoveApp(
        componentKey: String,
        fromPage: Int,
        atStart: Boolean,
        targetCellX: Int,
        targetCellY: Int,
    ): Boolean {
        var moved = false
        context.launcherDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            if (currentCount >= MAX_HOME_PAGES) return@edit
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            if (normA.none { it.pageIndex == fromPage && it.componentKey == componentKey }) return@edit
            val cx = targetCellX.coerceIn(0, cols - 1)
            val cy = targetCellY.coerceIn(0, rows - 1)
            val updatedW = if (atStart) normW.map { it.copy(pageIndex = it.pageIndex + 1) } else normW
            val newPageIdx = if (atStart) 0 else currentCount
            val updatedA = normA.map { a ->
                when {
                    a.pageIndex == fromPage && a.componentKey == componentKey -> {
                        a.copy(pageIndex = newPageIdx, cellX = cx, cellY = cy)
                    }
                    atStart -> a.copy(pageIndex = a.pageIndex + 1)
                    else -> a
                }
            }
            prefs[Keys.HOME_PAGE_COUNT] = currentCount + 1
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updatedW)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(updatedA)
            moved = true
        }
        return moved
    }

    suspend fun insertHomePageAndMoveWidget(
        appWidgetId: Int,
        atStart: Boolean,
        targetCellX: Int,
        targetCellY: Int,
    ): Boolean {
        var moved = false
        context.launcherDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            if (currentCount >= MAX_HOME_PAGES) return@edit
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            val target = normW.firstOrNull { it.appWidgetId == appWidgetId } ?: return@edit
            val sx = target.spanX.coerceIn(1, cols)
            val sy = target.spanY.coerceIn(1, rows)
            val cx = targetCellX.coerceIn(0, cols - sx)
            val cy = targetCellY.coerceIn(0, rows - sy)
            val newPageIdx = if (atStart) 0 else currentCount
            val updatedW = normW.map { w ->
                when {
                    w.appWidgetId == appWidgetId -> {
                        w.copy(pageIndex = newPageIdx, cellX = cx, cellY = cy, spanX = sx, spanY = sy)
                    }
                    atStart -> w.copy(pageIndex = w.pageIndex + 1)
                    else -> w
                }
            }
            val updatedA = if (atStart) normA.map { it.copy(pageIndex = it.pageIndex + 1) } else normA
            prefs[Keys.HOME_PAGE_COUNT] = currentCount + 1
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updatedW)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(updatedA)
            moved = true
        }
        return moved
    }

    suspend fun setHomeLoopPaging(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.HOME_LOOP_PAGING] = enabled
        }
    }

    suspend fun setHomeGridColumns(columns: Int) {
        context.launcherDataStore.edit { prefs ->
            val cols = columns.coerceIn(HomeGridEngine.MIN_HOME_GRID_COLUMNS, HomeGridEngine.MAX_HOME_GRID_COLUMNS)
            val rows = readGridRows(prefs)
            prefs[Keys.HOME_GRID_COLUMNS] = cols
            val pageCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                pageCount,
                cols,
                rows,
            )
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(normA)
        }
    }

    suspend fun setHomeGridRows(rows: Int) {
        context.launcherDataStore.edit { prefs ->
            val safeRows = rows.coerceIn(HomeGridEngine.MIN_HOME_GRID_ROWS, HomeGridEngine.MAX_HOME_GRID_ROWS)
            val cols = readGridColumns(prefs)
            prefs[Keys.HOME_GRID_ROWS] = safeRows
            val pageCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                pageCount,
                cols,
                safeRows,
            )
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(normA)
        }
    }

    suspend fun pinAppToHomePage(componentKey: String, pageIndex: Int): Boolean {
        var placed = true
        context.launcherDataStore.edit { prefs ->
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            var currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val targetPage = pageIndex.coerceIn(0, MAX_HOME_PAGES - 1)
            if (targetPage >= currentCount) {
                currentCount = targetPage + 1
                prefs[Keys.HOME_PAGE_COUNT] = currentCount
            }
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            if (normA.any { it.pageIndex == targetPage && it.componentKey == componentKey }) {
                prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(normA)
                return@edit
            }
            val occ = HomeGridEngine.buildOccupancy(
                widgets = normW.filter { it.pageIndex == targetPage },
                apps = normA.filter { it.pageIndex == targetPage },
                cols = cols,
                rows = rows,
            )
            val freeCell = HomeGridEngine.findFirstAvailableCell(occ, 1, 1, cols, rows)
            if (freeCell != null) {
                val updated = normA + PlacedHomeApp(
                    componentKey = componentKey,
                    pageIndex = targetPage,
                    cellX = freeCell.first,
                    cellY = freeCell.second,
                )
                prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(updated)
                prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW)
            } else {
                placed = false
            }
        }
        return placed
    }

    suspend fun removeAppFromHomePage(componentKey: String, pageIndex: Int) {
        context.launcherDataStore.edit { prefs ->
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            val pageCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val (_, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                pageCount,
                cols,
                rows,
            )
            val updated = normA.filterNot { it.pageIndex == pageIndex && it.componentKey == componentKey }
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(updated)
        }
    }

    suspend fun moveAppBetweenHomePages(componentKey: String, fromPage: Int, toPage: Int): Boolean {
        var moved = true
        context.launcherDataStore.edit { prefs ->
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            var currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val target = toPage.coerceIn(0, MAX_HOME_PAGES - 1)
            if (target >= currentCount) {
                currentCount = target + 1
                prefs[Keys.HOME_PAGE_COUNT] = currentCount
            }
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            val withoutSrc = normA.filterNot { it.pageIndex == fromPage && it.componentKey == componentKey }
            if (withoutSrc.any { it.pageIndex == target && it.componentKey == componentKey }) {
                prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(withoutSrc)
                return@edit
            }
            val occ = HomeGridEngine.buildOccupancy(
                widgets = normW.filter { it.pageIndex == target },
                apps = withoutSrc.filter { it.pageIndex == target },
                cols = cols,
                rows = rows,
            )
            val freeCell = HomeGridEngine.findFirstAvailableCell(occ, 1, 1, cols, rows)
            if (freeCell != null) {
                val updated = withoutSrc + PlacedHomeApp(
                    componentKey = componentKey,
                    pageIndex = target,
                    cellX = freeCell.first,
                    cellY = freeCell.second,
                )
                prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(updated)
                prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW)
            } else {
                moved = false
            }
        }
        return moved
    }

    /**
     * Moves a Home app icon to an exact (targetPage, targetCellX, targetCellY).
     * If another 1x1 app occupies that exact cell, swaps their positions cleanly.
     */
    suspend fun moveAppToCell(
        componentKey: String,
        fromPage: Int,
        targetPage: Int,
        targetCellX: Int,
        targetCellY: Int,
    ) {
        context.launcherDataStore.edit { prefs ->
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            var currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val clampedPage = targetPage.coerceIn(0, MAX_HOME_PAGES - 1)
            if (clampedPage >= currentCount) {
                currentCount = clampedPage + 1
                prefs[Keys.HOME_PAGE_COUNT] = currentCount
            }
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            val sourceApp = normA.firstOrNull { it.pageIndex == fromPage && it.componentKey == componentKey }
                ?: return@edit
            val cx = targetCellX.coerceIn(0, cols - 1)
            val cy = targetCellY.coerceIn(0, rows - 1)
            if (sourceApp.pageIndex == clampedPage && sourceApp.cellX == cx && sourceApp.cellY == cy) return@edit

            val targetPageWidgets = normW.filter { it.pageIndex == clampedPage }
            val widgetOcc = HomeGridEngine.buildOccupancy(targetPageWidgets, emptyList(), cols, rows)
            if (widgetOcc[cy][cx]) {
                // Blocked by a widget; do not overlap
                return@edit
            }

            // Check if another app is already at (clampedPage, cx, cy)
            val occupant = normA.firstOrNull {
                it.pageIndex == clampedPage && it.cellX == cx && it.cellY == cy &&
                    !(it.pageIndex == fromPage && it.componentKey == componentKey)
            }

            val updated = normA.mapNotNull { item ->
                when {
                    item.pageIndex == fromPage && item.componentKey == componentKey -> {
                        item.copy(pageIndex = clampedPage, cellX = cx, cellY = cy)
                    }
                    occupant != null && item.pageIndex == occupant.pageIndex && item.componentKey == occupant.componentKey -> {
                        // Swap occupant into sourceApp's previous spot if moving within same page or source spot free
                        if (fromPage == clampedPage) {
                            item.copy(cellX = sourceApp.cellX, cellY = sourceApp.cellY)
                        } else {
                            // Cross-page swap: move occupant to sourceApp's old page & cell
                            val dupOnFrom = normA.any { it.pageIndex == fromPage && it.componentKey == occupant.componentKey }
                            if (dupOnFrom) null else item.copy(pageIndex = fromPage, cellX = sourceApp.cellX, cellY = sourceApp.cellY)
                        }
                    }
                    // If dragging across pages and same app already existed on target page, remove duplicate
                    fromPage != clampedPage && item.pageIndex == clampedPage && item.componentKey == componentKey -> null
                    else -> item
                }
            }
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(updated)
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW)
        }
    }

    suspend fun togglePinDock(componentKey: String): Boolean {
        var success = true
        context.launcherDataStore.edit { prefs ->
            val limit = readDockMaxItems(prefs)
            val current = decodeList(prefs[Keys.DOCK_KEYS]).toMutableList()
            if (current.contains(componentKey)) {
                current.remove(componentKey)
            } else {
                if (limit <= 0 || current.size >= limit) {
                    success = false
                    return@edit
                }
                current.add(componentKey)
            }
            prefs[Keys.DOCK_KEYS] = encodeList(current)
        }
        return success
    }

    suspend fun reorderDockApp(componentKey: String, targetIndex: Int) {
        context.launcherDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.DOCK_KEYS]).toMutableList()
            val fromIndex = current.indexOf(componentKey)
            if (fromIndex < 0) return@edit
            val clampedTarget = targetIndex.coerceIn(0, current.lastIndex)
            if (fromIndex == clampedTarget) return@edit
            current.removeAt(fromIndex)
            current.add(clampedTarget, componentKey)
            prefs[Keys.DOCK_KEYS] = encodeList(current)
        }
    }

    suspend fun moveDockAppToHomeCell(
        componentKey: String,
        targetPage: Int,
        targetCellX: Int,
        targetCellY: Int,
    ) {
        context.launcherDataStore.edit { prefs ->
            val currentDock = decodeList(prefs[Keys.DOCK_KEYS]).toMutableList()
            val dockIndex = currentDock.indexOf(componentKey)
            if (dockIndex < 0) return@edit

            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            var currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val clampedPage = targetPage.coerceIn(0, MAX_HOME_PAGES - 1)
            if (clampedPage >= currentCount) {
                currentCount = clampedPage + 1
                prefs[Keys.HOME_PAGE_COUNT] = currentCount
            }
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            val cx = targetCellX.coerceIn(0, cols - 1)
            val cy = targetCellY.coerceIn(0, rows - 1)

            val targetPageWidgets = normW.filter { it.pageIndex == clampedPage }
            val widgetOcc = HomeGridEngine.buildOccupancy(targetPageWidgets, emptyList(), cols, rows)
            if (widgetOcc[cy][cx]) return@edit

            val occupant = normA.firstOrNull {
                it.pageIndex == clampedPage && it.cellX == cx && it.cellY == cy
            }

            currentDock.removeAt(dockIndex)
            val updatedApps = normA.toMutableList()
            if (occupant != null) {
                if (occupant.componentKey != componentKey) {
                    updatedApps.removeAll {
                        it.pageIndex == clampedPage &&
                            (it.componentKey == occupant.componentKey || it.componentKey == componentKey)
                    }
                    updatedApps.add(
                        PlacedHomeApp(
                            componentKey = componentKey,
                            pageIndex = clampedPage,
                            cellX = cx,
                            cellY = cy,
                        )
                    )
                    if (!currentDock.contains(occupant.componentKey)) {
                        currentDock.add(dockIndex.coerceIn(0, currentDock.size), occupant.componentKey)
                    }
                }
            } else {
                updatedApps.removeAll { it.pageIndex == clampedPage && it.componentKey == componentKey }
                updatedApps.add(
                    PlacedHomeApp(
                        componentKey = componentKey,
                        pageIndex = clampedPage,
                        cellX = cx,
                        cellY = cy,
                    )
                )
            }

            prefs[Keys.DOCK_KEYS] = encodeList(currentDock)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(updatedApps)
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW)
        }
    }

    suspend fun insertHomePageAndMoveDockApp(
        componentKey: String,
        atStart: Boolean,
        targetCellX: Int,
        targetCellY: Int,
    ): Boolean {
        var moved = false
        context.launcherDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            if (currentCount >= MAX_HOME_PAGES) return@edit
            val currentDock = decodeList(prefs[Keys.DOCK_KEYS]).toMutableList()
            if (!currentDock.remove(componentKey)) return@edit

            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            val cx = targetCellX.coerceIn(0, cols - 1)
            val cy = targetCellY.coerceIn(0, rows - 1)
            val newPageIdx = if (atStart) 0 else currentCount
            val updatedW = if (atStart) normW.map { it.copy(pageIndex = it.pageIndex + 1) } else normW
            val shiftedA = if (atStart) normA.map { it.copy(pageIndex = it.pageIndex + 1) } else normA
            val updatedA = shiftedA + PlacedHomeApp(
                componentKey = componentKey,
                pageIndex = newPageIdx,
                cellX = cx,
                cellY = cy,
            )
            prefs[Keys.DOCK_KEYS] = encodeList(currentDock)
            prefs[Keys.HOME_PAGE_COUNT] = currentCount + 1
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updatedW)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(updatedA)
            moved = true
        }
        return moved
    }

    suspend fun moveHomeAppToDock(
        componentKey: String,
        fromPage: Int,
        targetDockIndex: Int,
    ) {
        context.launcherDataStore.edit { prefs ->
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            val pageCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                pageCount,
                cols,
                rows,
            )
            val sourceApp = normA.firstOrNull { it.pageIndex == fromPage && it.componentKey == componentKey }
                ?: return@edit

            val currentDock = decodeList(prefs[Keys.DOCK_KEYS]).toMutableList()
            val updatedApps = normA.filterNot { it.pageIndex == fromPage && it.componentKey == componentKey }.toMutableList()

            val dockLimit = readDockMaxItems(prefs)
            if (dockLimit <= 0) return@edit
            val existingIdx = currentDock.indexOf(componentKey)
            when {
                existingIdx >= 0 -> {
                    currentDock.removeAt(existingIdx)
                    currentDock.add(targetDockIndex.coerceIn(0, currentDock.size), componentKey)
                }

                currentDock.size < dockLimit -> {
                    currentDock.add(targetDockIndex.coerceIn(0, currentDock.size), componentKey)
                }

                else -> {
                    val swapIdx = targetDockIndex.coerceIn(0, (currentDock.lastIndex).coerceAtLeast(0))
                    val displacedKey = currentDock[swapIdx]
                    currentDock[swapIdx] = componentKey
                    val dupOnFrom = updatedApps.any { it.pageIndex == fromPage && it.componentKey == displacedKey }
                    if (!dupOnFrom) {
                        updatedApps.add(
                            PlacedHomeApp(
                                componentKey = displacedKey,
                                pageIndex = fromPage,
                                cellX = sourceApp.cellX,
                                cellY = sourceApp.cellY,
                            )
                        )
                    }
                }
            }

            prefs[Keys.DOCK_KEYS] = encodeList(currentDock)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(updatedApps)
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW)
        }
    }

    suspend fun setAppGesture(
        appKey: String,
        direction: SwipeDirection,
        targetAppKey: String?,
    ) {
        context.launcherDataStore.edit { prefs ->
            val gestures = decodeAppGestures(prefs[Keys.DOCK_GESTURES])
                .mapValues { it.value.toMutableMap() }
                .toMutableMap()
            val appMap = gestures.getOrPut(appKey) { mutableMapOf() }
            if (targetAppKey.isNullOrBlank()) {
                appMap.remove(direction)
                if (appMap.isEmpty()) gestures.remove(appKey)
            } else {
                appMap[direction] = targetAppKey
            }
            prefs[Keys.DOCK_GESTURES] = encodeAppGestures(gestures)
        }
    }

    suspend fun setHomeGesture(direction: SwipeDirection, action: HomeGestureAction) {
        val key = when (direction) {
            SwipeDirection.UP -> Keys.HOME_GESTURE_UP
            SwipeDirection.DOWN -> Keys.HOME_GESTURE_DOWN
            SwipeDirection.LEFT -> Keys.HOME_GESTURE_LEFT
            SwipeDirection.RIGHT -> Keys.HOME_GESTURE_RIGHT
        }
        context.launcherDataStore.edit { prefs ->
            prefs[key] = action.encode()
        }
    }

    suspend fun setHomeButtonAction(action: HomeGestureAction) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.HOME_BUTTON_ACTION] = action.encode()
        }
    }

    suspend fun setDrawerColumns(columns: Int) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.DRAWER_COLUMNS] = columns.coerceIn(
                HomeGridEngine.MIN_HOME_GRID_COLUMNS,
                HomeGridEngine.MAX_HOME_GRID_COLUMNS,
            )
        }
    }

    suspend fun createDrawerTab(name: String, appKeys: List<String> = emptyList()): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        var createdId: String? = null
        context.launcherDataStore.edit { prefs ->
            val current = decodeDrawerTabs(prefs[Keys.DRAWER_TABS])
            if (current.size >= MAX_DRAWER_TABS) return@edit
            val assigned = appKeys.distinct().toSet()
            val cleaned = current.map { tab ->
                if (assigned.isEmpty()) tab
                else tab.copy(appKeys = tab.appKeys.filterNot { it in assigned }.toImmutableList())
            }.toMutableList()
            val id = "tab_${UUID.randomUUID().toString().take(8)}"
            cleaned.add(DrawerTab(id = id, name = trimmed, appKeys = appKeys.distinct().toImmutableList()))
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(cleaned)
            createdId = id
        }
        return createdId
    }

    suspend fun renameDrawerTab(tabId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        context.launcherDataStore.edit { prefs ->
            val updated = decodeDrawerTabs(prefs[Keys.DRAWER_TABS]).map { tab ->
                if (tab.id == tabId) tab.copy(name = trimmed) else tab
            }
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(updated)
        }
    }

    suspend fun setDrawerTabApps(tabId: String, appKeys: Set<String>) {
        context.launcherDataStore.edit { prefs ->
            val updated = decodeDrawerTabs(prefs[Keys.DRAWER_TABS]).map { tab ->
                if (tab.id == tabId) {
                    tab.copy(appKeys = appKeys.toImmutableList())
                } else {
                    tab.copy(appKeys = tab.appKeys.filterNot { it in appKeys }.toImmutableList())
                }
            }
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(updated)
        }
    }

    suspend fun setAppDrawerTabs(componentKey: String, selectedTabIds: Set<String>) {
        val chosenTabId = selectedTabIds.firstOrNull()
        context.launcherDataStore.edit { prefs ->
            val updated = decodeDrawerTabs(prefs[Keys.DRAWER_TABS]).map { tab ->
                val list = tab.appKeys.toMutableList()
                if (tab.id == chosenTabId) {
                    if (!list.contains(componentKey)) list.add(componentKey)
                } else {
                    list.remove(componentKey)
                }
                tab.copy(appKeys = list.toImmutableList())
            }
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(updated)
        }
    }

    suspend fun moveAppToDrawerTab(componentKey: String, targetTabId: String) {
        context.launcherDataStore.edit { prefs ->
            val updated = decodeDrawerTabs(prefs[Keys.DRAWER_TABS]).map { tab ->
                val list = tab.appKeys.toMutableList()
                if (targetTabId != DrawerTab.ALL_TAB_ID && tab.id == targetTabId) {
                    if (!list.contains(componentKey)) list.add(componentKey)
                } else {
                    list.remove(componentKey)
                }
                tab.copy(appKeys = list.toImmutableList())
            }
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(updated)
        }
    }

    suspend fun moveDrawerTab(tabId: String, delta: Int) {
        if (delta == 0) return
        context.launcherDataStore.edit { prefs ->
            val list = decodeDrawerTabs(prefs[Keys.DRAWER_TABS]).toMutableList()
            val idx = list.indexOfFirst { it.id == tabId }
            if (idx < 0) return@edit
            val targetIdx = (idx + delta).coerceIn(0, list.lastIndex)
            if (targetIdx == idx) return@edit
            val item = list.removeAt(idx)
            list.add(targetIdx, item)
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(list)
        }
    }

    suspend fun setAllTabHidden(hidden: Boolean): Boolean {
        var applied = true
        context.launcherDataStore.edit { prefs ->
            if (hidden) {
                val tabs = decodeDrawerTabs(prefs[Keys.DRAWER_TABS])
                if (tabs.none { !it.isHidden }) {
                    applied = false
                    return@edit
                }
            }
            prefs[Keys.ALL_TAB_HIDDEN] = hidden
        }
        return applied
    }

    suspend fun setAllTabName(name: String) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.ALL_TAB_NAME] = name.trim()
        }
    }

    suspend fun setAutoHideEmptyDrawerTabs(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.AUTO_HIDE_EMPTY_DRAWER_TABS] = enabled
        }
    }

    suspend fun toggleDrawerAppFolded(componentKey: String) {
        if (componentKey.isBlank()) return
        context.launcherDataStore.edit { prefs ->
            val current = decodeEncodedList(prefs[Keys.FOLDED_DRAWER_APP_KEYS]).toMutableList()
            if (componentKey in current) {
                current.remove(componentKey)
            } else {
                current.add(componentKey)
            }
            prefs[Keys.FOLDED_DRAWER_APP_KEYS] = encodeEncodedList(current)
        }
    }

    suspend fun setDrawerTabHidden(tabId: String, hidden: Boolean): Boolean {
        var applied = true
        context.launcherDataStore.edit { prefs ->
            val current = decodeDrawerTabs(prefs[Keys.DRAWER_TABS])
            if (hidden) {
                val allVisible = prefs[Keys.ALL_TAB_HIDDEN] != true
                val otherVisibleTabs = current.count { it.id != tabId && !it.isHidden }
                if (!allVisible && otherVisibleTabs == 0) {
                    applied = false
                    return@edit
                }
            }
            val updated = current.map { tab ->
                if (tab.id == tabId) tab.copy(isHidden = hidden) else tab
            }
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(updated)
        }
        return applied
    }

    suspend fun deleteDrawerTab(tabId: String) {
        context.launcherDataStore.edit { prefs ->
            val updated = decodeDrawerTabs(prefs[Keys.DRAWER_TABS]).filterNot { it.id == tabId }
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(updated)
            if (prefs[Keys.ALL_TAB_HIDDEN] == true && updated.none { !it.isHidden }) {
                prefs[Keys.ALL_TAB_HIDDEN] = false
            }
        }
    }

    suspend fun addPlacedWidget(widget: PlacedWidget) {
        context.launcherDataStore.edit { prefs ->
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            var currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            var targetPage = widget.pageIndex.coerceIn(0, MAX_HOME_PAGES - 1)
            if (targetPage >= currentCount) {
                currentCount = targetPage + 1
                prefs[Keys.HOME_PAGE_COUNT] = currentCount
            }
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]).filterNot { it.appWidgetId == widget.appWidgetId },
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            val sx = widget.spanX.coerceIn(1, cols)
            val sy = widget.spanY.coerceIn(1, rows)

            // Try target page first, then subsequent pages, expanding up to MAX_HOME_PAGES if needed
            var placed: PlacedWidget? = null
            val candidatePages = (listOf(targetPage) + (0 until currentCount).filter { it != targetPage })
            for (p in candidatePages) {
                val occ = HomeGridEngine.buildOccupancy(
                    widgets = normW.filter { it.pageIndex == p },
                    apps = normA.filter { it.pageIndex == p },
                    cols = cols,
                )
                val slot = HomeGridEngine.findFirstAvailableCell(occ, sx, sy, cols, rows)
                if (slot != null) {
                    placed = widget.copy(
                        pageIndex = p,
                        spanX = sx,
                        spanY = sy,
                        cellX = slot.first,
                        cellY = slot.second,
                    )
                    break
                }
            }
            if (placed == null && currentCount < MAX_HOME_PAGES) {
                val newPage = currentCount
                currentCount += 1
                prefs[Keys.HOME_PAGE_COUNT] = currentCount
                placed = widget.copy(pageIndex = newPage, spanX = sx, spanY = sy, cellX = 0, cellY = 0)
            }
            if (placed == null) {
                placed = widget.copy(pageIndex = targetPage, spanX = sx, spanY = sy, cellX = 0, cellY = 0)
            }
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(normW + placed)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(normA)
        }
    }

    suspend fun removePlacedWidget(appWidgetId: Int) {
        context.launcherDataStore.edit { prefs ->
            val updated = decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS])
                .filterNot { it.appWidgetId == appWidgetId }
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updated)
        }
    }

    suspend fun updateWidgetRect(
        appWidgetId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    ) {
        context.launcherDataStore.edit { prefs ->
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            val pageCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                pageCount,
                cols,
                rows,
            )
            val target = normW.firstOrNull { it.appWidgetId == appWidgetId } ?: return@edit
            val sx = spanX.coerceIn(1, cols)
            val sy = spanY.coerceIn(1, rows)
            val cx = cellX.coerceIn(0, cols - sx)
            val cy = cellY.coerceIn(0, rows - sy)
            val occ = HomeGridEngine.buildOccupancy(
                widgets = normW.filter { it.pageIndex == target.pageIndex },
                apps = normA.filter { it.pageIndex == target.pageIndex },
                cols = cols,
                rows = rows,
                ignoreWidgetId = appWidgetId,
            )
            if (!HomeGridEngine.canPlace(occ, cx, cy, sx, sy, cols, rows)) return@edit

            val updated = normW.map { w ->
                if (w.appWidgetId == appWidgetId) {
                    w.copy(cellX = cx, cellY = cy, spanX = sx, spanY = sy)
                } else {
                    w
                }
            }
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updated)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(normA)
        }
    }

    suspend fun moveWidgetToCell(
        appWidgetId: Int,
        targetPage: Int,
        targetCellX: Int,
        targetCellY: Int,
    ) {
        context.launcherDataStore.edit { prefs ->
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            var currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val clampedPage = targetPage.coerceIn(0, MAX_HOME_PAGES - 1)
            if (clampedPage >= currentCount) {
                currentCount = clampedPage + 1
                prefs[Keys.HOME_PAGE_COUNT] = currentCount
            }
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            val widget = normW.firstOrNull { it.appWidgetId == appWidgetId } ?: return@edit
            val sx = widget.spanX.coerceIn(1, cols)
            val sy = widget.spanY.coerceIn(1, rows)
            val cx = targetCellX.coerceIn(0, cols - sx)
            val cy = targetCellY.coerceIn(0, rows - sy)
            val occ = HomeGridEngine.buildOccupancy(
                widgets = normW.filter { it.pageIndex == clampedPage },
                apps = normA.filter { it.pageIndex == clampedPage },
                cols = cols,
                rows = rows,
                ignoreWidgetId = appWidgetId,
            )
            val finalSlot = if (HomeGridEngine.canPlace(occ, cx, cy, sx, sy, cols, rows)) {
                cx to cy
            } else {
                return@edit
            }
            val updated = normW.map { w ->
                if (w.appWidgetId == appWidgetId) {
                    w.copy(pageIndex = clampedPage, cellX = finalSlot.first, cellY = finalSlot.second, spanX = sx, spanY = sy)
                } else {
                    w
                }
            }
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updated)
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(normA)
        }
    }

    suspend fun moveWidgetToPage(appWidgetId: Int, targetPage: Int): Boolean {
        var moved = true
        context.launcherDataStore.edit { prefs ->
            val cols = readGridColumns(prefs)
            val rows = readGridRows(prefs)
            var currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val clamped = targetPage.coerceIn(0, MAX_HOME_PAGES - 1)
            if (clamped >= currentCount) {
                currentCount = clamped + 1
                prefs[Keys.HOME_PAGE_COUNT] = currentCount
            }
            val (normW, normA) = normalizeAllPages(
                decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
                readHomeApps(prefs),
                currentCount,
                cols,
                rows,
            )
            val widget = normW.firstOrNull { it.appWidgetId == appWidgetId } ?: return@edit
            val sx = widget.spanX.coerceIn(1, cols)
            val sy = widget.spanY.coerceIn(1, rows)
            val occ = HomeGridEngine.buildOccupancy(
                widgets = normW.filter { it.pageIndex == clamped },
                apps = normA.filter { it.pageIndex == clamped },
                cols = cols,
                rows = rows,
                ignoreWidgetId = appWidgetId,
            )
            val slot = HomeGridEngine.findFirstAvailableCell(occ, sx, sy, cols, rows)
            if (slot != null) {
                val updated = normW.map { w ->
                    if (w.appWidgetId == appWidgetId) {
                        w.copy(pageIndex = clamped, cellX = slot.first, cellY = slot.second, spanX = sx, spanY = sy)
                    } else {
                        w
                    }
                }
                prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updated)
                prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(normA)
            } else {
                moved = false
            }
        }
        return moved
    }

    suspend fun pruneMissingWidgets(validIds: Set<Int>) {
        context.launcherDataStore.edit { prefs ->
            val current = decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS])
            val filtered = current.filter { it.appWidgetId in validIds }
            if (filtered.size != current.size) {
                prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(filtered)
            }
        }
    }

    suspend fun setSearchShortcutsEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.SEARCH_SHORTCUTS_ENABLED] = enabled
        }
    }

    suspend fun setSearchContactsEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.SEARCH_CONTACTS_ENABLED] = enabled
        }
    }

    suspend fun setSearchFilesEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.SEARCH_FILES_ENABLED] = enabled
        }
    }

    suspend fun addSearchFileTreeUri(treeUri: String, autoEnable: Boolean = true): Boolean {
        var added = false
        context.launcherDataStore.edit { prefs ->
            val current = decodeEncodedList(prefs[Keys.SEARCH_FILE_TREE_URIS]).toMutableList()
            if (treeUri !in current) {
                current.add(treeUri)
                prefs[Keys.SEARCH_FILE_TREE_URIS] = encodeEncodedList(current)
                added = true
            }
            if (autoEnable) {
                prefs[Keys.SEARCH_FILES_ENABLED] = true
            }
        }
        return added
    }

    suspend fun removeSearchFileTreeUri(treeUri: String) {
        context.launcherDataStore.edit { prefs ->
            val updated = decodeEncodedList(prefs[Keys.SEARCH_FILE_TREE_URIS]).filterNot { it == treeUri }
            prefs[Keys.SEARCH_FILE_TREE_URIS] = encodeEncodedList(updated)
            if (updated.isEmpty()) {
                prefs[Keys.SEARCH_FILES_ENABLED] = false
            }
        }
    }

    suspend fun setSearchThirdPartyAppsEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.SEARCH_THIRD_PARTY_APPS_ENABLED] = enabled
        }
    }

    suspend fun setSearchWebEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.SEARCH_WEB_ENABLED] = enabled
        }
    }

    suspend fun setSearchWebEngine(engine: WebSearchEngine) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.SEARCH_WEB_ENGINE] = engine.id
        }
    }

    suspend fun setIconShape(shape: IconShape) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.ICON_SHAPE] = shape.id
        }
    }

    suspend fun setHomeShowAppLabels(show: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.HOME_SHOW_APP_LABELS] = show
        }
    }

    suspend fun exportSettingsJson(): String {
        val snapshot = settingsFlow.first()
        return LauncherSettingsBackup.serialize(snapshot)
    }

    suspend fun restoreSettings(restored: RestoredLauncherSettings) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
            prefs[Keys.HOME_PAGE_COUNT] = restored.homePageCount
            prefs[Keys.HOME_LOOP_PAGING] = restored.homeLoopPaging
            prefs[Keys.HOME_SHOW_APP_LABELS] = restored.homeShowAppLabels
            prefs[Keys.HOME_GRID_COLUMNS] = restored.homeGridColumns
            prefs[Keys.HOME_GRID_ROWS] = restored.homeGridRows
            prefs[Keys.HOME_APPS_GRID] = encodePlacedHomeApps(restored.placedHomeApps)

            prefs[Keys.DOCK_KEYS] = encodeList(restored.dockKeys)
            prefs[Keys.DOCK_MAX_ITEMS] = restored.dockMaxItems
            prefs[Keys.DOCK_GESTURES] = encodeAppGestures(restored.appGestures)

            prefs[Keys.HOME_GESTURE_UP] = (restored.homeGestures[SwipeDirection.UP] ?: HomeGestureAction.None).encode()
            prefs[Keys.HOME_GESTURE_DOWN] = (restored.homeGestures[SwipeDirection.DOWN] ?: HomeGestureAction.None).encode()
            prefs[Keys.HOME_GESTURE_LEFT] = (restored.homeGestures[SwipeDirection.LEFT] ?: HomeGestureAction.None).encode()
            prefs[Keys.HOME_GESTURE_RIGHT] = (restored.homeGestures[SwipeDirection.RIGHT] ?: HomeGestureAction.None).encode()
            prefs[Keys.HOME_BUTTON_ACTION] = restored.homeButtonAction.encode()

            prefs[Keys.DRAWER_COLUMNS] = restored.drawerColumns
            prefs[Keys.ICON_SHAPE] = restored.iconShape.id
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(restored.drawerTabs)
            prefs[Keys.ALL_TAB_HIDDEN] = restored.isAllTabHidden
            prefs[Keys.ALL_TAB_NAME] = restored.allTabName
            prefs[Keys.AUTO_HIDE_EMPTY_DRAWER_TABS] = restored.autoHideEmptyDrawerTabs
            prefs[Keys.FOLDED_DRAWER_APP_KEYS] = encodeEncodedList(restored.foldedDrawerAppKeys)
            prefs[Keys.HAS_SEEDED_DEFAULTS] = true

            prefs[Keys.SEARCH_SHORTCUTS_ENABLED] = restored.searchShortcutsEnabled
            prefs[Keys.SEARCH_CONTACTS_ENABLED] = restored.searchContactsEnabled
            prefs[Keys.SEARCH_THIRD_PARTY_APPS_ENABLED] = restored.searchThirdPartyAppsEnabled
            prefs[Keys.SEARCH_WEB_ENABLED] = restored.searchWebEnabled
            prefs[Keys.SEARCH_WEB_ENGINE] = restored.searchWebEngine.id
        }
    }

    private fun encodeEncodedList(items: List<String>): String =
        items.distinct().joinToString(PAGE_SEP) { Uri.encode(it) }

    private fun decodeEncodedList(raw: String?): List<String> =
        decodeEncodedListHelper(raw)

    private fun encodeList(items: List<String>): String =
        items.distinct().joinToString(ITEM_SEP)

    private fun decodeList(raw: String?): List<String> =
        raw?.split(ITEM_SEP)?.filter { it.isNotBlank() } ?: emptyList()

    // Format: "appKey>UP=targetKey,LEFT=targetKey2;appKey2>DOWN=targetKey3"
    private fun encodeAppGestures(gestures: Map<String, Map<SwipeDirection, String>>): String =
        gestures.entries
            .filter { it.value.isNotEmpty() }
            .joinToString(PAGE_SEP) { (appKey, dirMap) ->
                val encodedDirs = dirMap.entries.joinToString(ITEM_SEP) { "${it.key.name}$EQ_SEP${it.value}" }
                "$appKey$GESTURE_SEP$encodedDirs"
            }

    private fun decodeAppGestures(raw: String?): Map<String, Map<SwipeDirection, String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, Map<SwipeDirection, String>>()
        raw.split(PAGE_SEP).forEach { entry ->
            val gIdx = entry.indexOf(GESTURE_SEP)
            if (gIdx <= 0) return@forEach
            val appKey = entry.substring(0, gIdx)
            val dirs = linkedMapOf<SwipeDirection, String>()
            entry.substring(gIdx + 1).split(ITEM_SEP).forEach { pair ->
                val eqIdx = pair.indexOf(EQ_SEP)
                if (eqIdx > 0) {
                    val dir = runCatching { SwipeDirection.valueOf(pair.substring(0, eqIdx)) }.getOrNull()
                    val target = pair.substring(eqIdx + 1)
                    if (dir != null && target.isNotBlank()) {
                        dirs[dir] = target
                    }
                }
            }
            if (dirs.isNotEmpty()) result[appKey] = dirs
        }
        return result
    }

    // Format: "encodedId|encodedName|key1,key2|0_or_1_hidden;..."
    private fun encodeDrawerTabs(tabs: List<DrawerTab>): String =
        tabs.joinToString(PAGE_SEP) { tab ->
            val encId = Uri.encode(tab.id)
            val encName = Uri.encode(tab.name)
            val encApps = tab.appKeys.distinct().joinToString(ITEM_SEP) { Uri.encode(it) }
            val hiddenFlag = if (tab.isHidden) "1" else "0"
            "$encId$WIDGET_FIELD_SEP$encName$WIDGET_FIELD_SEP$encApps$WIDGET_FIELD_SEP$hiddenFlag"
        }

    private fun decodeDrawerTabs(raw: String?): List<DrawerTab> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(PAGE_SEP).mapNotNull { segment ->
            val parts = segment.split(WIDGET_FIELD_SEP)
            if (parts.size < 2) return@mapNotNull null
            val id = Uri.decode(parts[0]).trim()
            val name = Uri.decode(parts[1]).trim()
            if (id.isBlank() || name.isBlank() || id == DrawerTab.ALL_TAB_ID) return@mapNotNull null
            val keys = if (parts.size >= 3 && parts[2].isNotBlank()) {
                parts[2].split(ITEM_SEP).mapNotNull { encoded ->
                    Uri.decode(encoded).takeIf { it.isNotBlank() }
                }.distinct()
            } else {
                emptyList()
            }
            val isHidden = parts.size >= 4 && parts[3] == "1"
            DrawerTab(id = id, name = name, appKeys = keys.toImmutableList(), isHidden = isHidden)
        }
    }

    // Format: "page|cellX|cellY|encodedComponentKey;..."
    private fun encodePlacedHomeApps(apps: List<PlacedHomeApp>): String =
        encodePlacedHomeAppsHelper(apps)

    private fun decodePlacedHomeApps(raw: String?): List<PlacedHomeApp> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(PAGE_SEP).mapNotNull { segment ->
            val parts = segment.split(WIDGET_FIELD_SEP)
            if (parts.size < 4) return@mapNotNull null
            val page = parts[0].toIntOrNull()?.coerceAtLeast(0) ?: return@mapNotNull null
            val cx = parts[1].toIntOrNull() ?: -1
            val cy = parts[2].toIntOrNull() ?: -1
            val key = Uri.decode(parts[3])
            if (key.isBlank()) return@mapNotNull null
            PlacedHomeApp(componentKey = key, pageIndex = page, cellX = cx, cellY = cy)
        }
    }

    // Format: "widgetId|pageIndex|spanX|spanY|providerFlatName|cellX|cellY;..."
    // Backward-compatible with legacy 5-field entries (decoded with cellX = -1, cellY = -1).
    private fun encodePlacedWidgets(widgets: List<PlacedWidget>): String =
        widgets.joinToString(PAGE_SEP) { w ->
            listOf(
                w.appWidgetId,
                w.pageIndex,
                w.spanX,
                w.spanY,
                w.providerComponent,
                w.cellX,
                w.cellY,
            ).joinToString(WIDGET_FIELD_SEP)
        }

    private fun decodePlacedWidgets(raw: String?): List<PlacedWidget> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(PAGE_SEP).mapNotNull { segment ->
            val parts = segment.split(WIDGET_FIELD_SEP)
            if (parts.size < 5) return@mapNotNull null
            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            val page = parts[1].toIntOrNull() ?: return@mapNotNull null
            val spanX = parts[2].toIntOrNull()?.coerceIn(1, HomeGridEngine.MAX_HOME_GRID_COLUMNS) ?: return@mapNotNull null
            val spanY = parts[3].toIntOrNull()?.coerceIn(1, HomeGridEngine.MAX_HOME_GRID_ROWS) ?: return@mapNotNull null
            val provider = parts[4]
            if (provider.isBlank()) return@mapNotNull null
            val cellX = parts.getOrNull(5)?.toIntOrNull() ?: -1
            val cellY = parts.getOrNull(6)?.toIntOrNull() ?: -1
            PlacedWidget(
                appWidgetId = id,
                pageIndex = page.coerceAtLeast(0),
                spanX = spanX,
                spanY = spanY,
                providerComponent = provider,
                cellX = cellX,
                cellY = cellY,
            )
        }
    }

    companion object {
        const val MIN_DOCK_ITEMS = 0
        const val DEFAULT_DOCK_ITEMS = 5
        const val MAX_DOCK_ITEMS = 8
        const val MAX_HOME_PAGES = 7
        const val MAX_DRAWER_TABS = 12
        private const val PAGE_SEP = ";"
        private const val ITEM_SEP = ","
        private const val KV_SEP = ":"
        private const val GESTURE_SEP = ">"
        private const val EQ_SEP = "="
        private const val WIDGET_FIELD_SEP = "|"
    }
}
