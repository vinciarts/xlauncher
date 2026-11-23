package com.example.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DrawerTab
import com.example.model.HomeGestureAction
import com.example.model.LauncherSettings
import com.example.model.PlacedWidget
import com.example.model.SwipeDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "qingfeng_launcher_v2"
)

class LauncherPreferencesRepository(private val context: Context) {

    private object Keys {
        val HOME_PAGE_COUNT = intPreferencesKey("home_page_count")
        val HOME_LOOP_PAGING = booleanPreferencesKey("home_loop_paging")
        val HOME_GRID_COLUMNS = intPreferencesKey("home_grid_columns")
        val HOME_PAGES_DATA = stringPreferencesKey("home_pages_data")
        val PLACED_WIDGETS = stringPreferencesKey("placed_widgets")
        val DOCK_KEYS = stringPreferencesKey("dock_keys")
        val DOCK_GESTURES = stringPreferencesKey("dock_gestures")
        val HOME_GESTURE_UP = stringPreferencesKey("home_gesture_up")
        val HOME_GESTURE_DOWN = stringPreferencesKey("home_gesture_down")
        val HOME_GESTURE_LEFT = stringPreferencesKey("home_gesture_left")
        val HOME_GESTURE_RIGHT = stringPreferencesKey("home_gesture_right")
        val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val DRAWER_TABS = stringPreferencesKey("drawer_tabs")
        val ALL_TAB_HIDDEN = booleanPreferencesKey("drawer_all_tab_hidden")
        val HAS_SEEDED_DEFAULTS = booleanPreferencesKey("has_seeded_defaults")
    }

    val settingsFlow: Flow<LauncherSettings> = context.launcherDataStore.data.map { prefs ->
        LauncherSettings(
            homePageCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES),
            homeLoopPaging = prefs[Keys.HOME_LOOP_PAGING] ?: true,
            homeGridColumns = (prefs[Keys.HOME_GRID_COLUMNS] ?: 4).coerceIn(4, 5),
            homePages = decodeHomePages(prefs[Keys.HOME_PAGES_DATA]),
            placedWidgets = decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]),
            dockKeys = decodeList(prefs[Keys.DOCK_KEYS]).take(MAX_DOCK_ITEMS),
            appGestures = decodeAppGestures(prefs[Keys.DOCK_GESTURES]),
            homeGestures = mapOf(
                SwipeDirection.UP to (prefs[Keys.HOME_GESTURE_UP]?.let { HomeGestureAction.decode(it) }
                    ?: HomeGestureAction.OpenDrawer),
                SwipeDirection.DOWN to (prefs[Keys.HOME_GESTURE_DOWN]?.let { HomeGestureAction.decode(it) }
                    ?: HomeGestureAction.ExpandNotifications),
                SwipeDirection.LEFT to HomeGestureAction.decode(prefs[Keys.HOME_GESTURE_LEFT]),
                SwipeDirection.RIGHT to HomeGestureAction.decode(prefs[Keys.HOME_GESTURE_RIGHT]),
            ),
            drawerColumns = (prefs[Keys.DRAWER_COLUMNS] ?: 4).coerceIn(4, 5),
            drawerTabs = decodeDrawerTabs(prefs[Keys.DRAWER_TABS]),
            isAllTabHidden = prefs[Keys.ALL_TAB_HIDDEN] ?: false,
            hasSeededDefaults = prefs[Keys.HAS_SEEDED_DEFAULTS] ?: false,
        )
    }

    suspend fun seedDefaultsIfNeeded(
        dockKeys: List<String>,
        homePages: Map<Int, List<String>>,
    ) {
        context.launcherDataStore.edit { prefs ->
            if (prefs[Keys.HAS_SEEDED_DEFAULTS] == true) return@edit
            prefs[Keys.DOCK_KEYS] = encodeList(dockKeys.take(MAX_DOCK_ITEMS))
            prefs[Keys.HOME_PAGES_DATA] = encodeHomePages(homePages)
            prefs[Keys.HOME_PAGE_COUNT] = homePages.size.coerceIn(2, MAX_HOME_PAGES)
            prefs[Keys.HAS_SEEDED_DEFAULTS] = true
        }
    }

    suspend fun setHomePageCount(count: Int) {
        val clamped = count.coerceIn(1, MAX_HOME_PAGES)
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.HOME_PAGE_COUNT] = clamped
            val currentPages = decodeHomePages(prefs[Keys.HOME_PAGES_DATA]).toMutableMap()
            val overflowKeys = mutableListOf<String>()
            val removedKeys = currentPages.keys.filter { it >= clamped }
            removedKeys.sorted().forEach { idx ->
                overflowKeys.addAll(currentPages.remove(idx).orEmpty())
            }
            if (overflowKeys.isNotEmpty()) {
                val lastIdx = clamped - 1
                val existing = currentPages[lastIdx].orEmpty()
                currentPages[lastIdx] = (existing + overflowKeys).distinct()
            }
            prefs[Keys.HOME_PAGES_DATA] = encodeHomePages(currentPages)
            val widgets = decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS])
            if (widgets.any { it.pageIndex >= clamped }) {
                val updated = widgets.map { w ->
                    if (w.pageIndex >= clamped) w.copy(pageIndex = clamped - 1) else w
                }
                prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updated)
            }
        }
    }

    suspend fun setHomeLoopPaging(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.HOME_LOOP_PAGING] = enabled
        }
    }

    suspend fun setHomeGridColumns(columns: Int) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.HOME_GRID_COLUMNS] = columns.coerceIn(4, 5)
        }
    }

    suspend fun pinAppToHomePage(componentKey: String, pageIndex: Int) {
        context.launcherDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val targetPage = pageIndex.coerceIn(0, MAX_HOME_PAGES - 1)
            if (targetPage >= currentCount) {
                prefs[Keys.HOME_PAGE_COUNT] = targetPage + 1
            }
            val pages = decodeHomePages(prefs[Keys.HOME_PAGES_DATA]).toMutableMap()
            val list = pages[targetPage].orEmpty().toMutableList()
            if (!list.contains(componentKey)) {
                list.add(componentKey)
                pages[targetPage] = list
            }
            prefs[Keys.HOME_PAGES_DATA] = encodeHomePages(pages)
        }
    }

    suspend fun removeAppFromHomePage(componentKey: String, pageIndex: Int) {
        context.launcherDataStore.edit { prefs ->
            val pages = decodeHomePages(prefs[Keys.HOME_PAGES_DATA]).toMutableMap()
            val list = pages[pageIndex].orEmpty().filterNot { it == componentKey }
            pages[pageIndex] = list
            prefs[Keys.HOME_PAGES_DATA] = encodeHomePages(pages)
        }
    }

    suspend fun moveAppBetweenHomePages(componentKey: String, fromPage: Int, toPage: Int) {
        context.launcherDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val target = toPage.coerceIn(0, MAX_HOME_PAGES - 1)
            if (target >= currentCount) {
                prefs[Keys.HOME_PAGE_COUNT] = target + 1
            }
            val pages = decodeHomePages(prefs[Keys.HOME_PAGES_DATA]).toMutableMap()
            pages[fromPage] = pages[fromPage].orEmpty().filterNot { it == componentKey }
            val dest = pages[target].orEmpty().toMutableList()
            if (!dest.contains(componentKey)) {
                dest.add(componentKey)
            }
            pages[target] = dest
            prefs[Keys.HOME_PAGES_DATA] = encodeHomePages(pages)
        }
    }

    suspend fun togglePinDock(componentKey: String): Boolean {
        var success = true
        context.launcherDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.DOCK_KEYS]).toMutableList()
            if (current.contains(componentKey)) {
                current.remove(componentKey)
            } else {
                if (current.size >= MAX_DOCK_ITEMS) {
                    success = false
                    return@edit
                }
                current.add(componentKey)
            }
            prefs[Keys.DOCK_KEYS] = encodeList(current)
        }
        return success
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

    suspend fun setDrawerColumns(columns: Int) {
        context.launcherDataStore.edit { prefs ->
            prefs[Keys.DRAWER_COLUMNS] = columns.coerceIn(4, 5)
        }
    }

    suspend fun createDrawerTab(name: String, appKeys: List<String> = emptyList()): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        var createdId: String? = null
        context.launcherDataStore.edit { prefs ->
            val current = decodeDrawerTabs(prefs[Keys.DRAWER_TABS]).toMutableList()
            if (current.size >= MAX_DRAWER_TABS) return@edit
            val id = "tab_${UUID.randomUUID().toString().take(8)}"
            current.add(DrawerTab(id = id, name = trimmed, appKeys = appKeys.distinct()))
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(current)
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
                if (tab.id == tabId) tab.copy(appKeys = appKeys.toList()) else tab
            }
            prefs[Keys.DRAWER_TABS] = encodeDrawerTabs(updated)
        }
    }

    suspend fun setAppDrawerTabs(componentKey: String, selectedTabIds: Set<String>) {
        context.launcherDataStore.edit { prefs ->
            val updated = decodeDrawerTabs(prefs[Keys.DRAWER_TABS]).map { tab ->
                val list = tab.appKeys.toMutableList()
                if (tab.id in selectedTabIds) {
                    if (!list.contains(componentKey)) list.add(componentKey)
                } else {
                    list.remove(componentKey)
                }
                tab.copy(appKeys = list)
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
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val targetPage = widget.pageIndex.coerceIn(0, MAX_HOME_PAGES - 1)
            if (targetPage >= currentCount) {
                prefs[Keys.HOME_PAGE_COUNT] = targetPage + 1
            }
            val list = decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS])
                .filterNot { it.appWidgetId == widget.appWidgetId }
                .toMutableList()
            list.add(widget.copy(pageIndex = targetPage))
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(list)
        }
    }

    suspend fun removePlacedWidget(appWidgetId: Int) {
        context.launcherDataStore.edit { prefs ->
            val updated = decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS])
                .filterNot { it.appWidgetId == appWidgetId }
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updated)
        }
    }

    suspend fun updateWidgetSpan(appWidgetId: Int, spanX: Int, spanY: Int) {
        context.launcherDataStore.edit { prefs ->
            val updated = decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]).map { w ->
                if (w.appWidgetId == appWidgetId) {
                    w.copy(spanX = spanX.coerceIn(1, 5), spanY = spanY.coerceIn(1, 6))
                } else {
                    w
                }
            }
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updated)
        }
    }

    suspend fun moveWidgetToPage(appWidgetId: Int, targetPage: Int) {
        context.launcherDataStore.edit { prefs ->
            val currentCount = (prefs[Keys.HOME_PAGE_COUNT] ?: 2).coerceIn(1, MAX_HOME_PAGES)
            val clamped = targetPage.coerceIn(0, MAX_HOME_PAGES - 1)
            if (clamped >= currentCount) {
                prefs[Keys.HOME_PAGE_COUNT] = clamped + 1
            }
            val updated = decodePlacedWidgets(prefs[Keys.PLACED_WIDGETS]).map { w ->
                if (w.appWidgetId == appWidgetId) w.copy(pageIndex = clamped) else w
            }
            prefs[Keys.PLACED_WIDGETS] = encodePlacedWidgets(updated)
        }
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

    private fun encodeList(items: List<String>): String =
        items.distinct().joinToString(ITEM_SEP)

    private fun decodeList(raw: String?): List<String> =
        raw?.split(ITEM_SEP)?.filter { it.isNotBlank() } ?: emptyList()

    // Format: "0:pkg/cls,pkg2/cls2;1:pkg3/cls3"
    private fun encodeHomePages(pages: Map<Int, List<String>>): String =
        pages.entries
            .sortedBy { it.key }
            .filter { it.value.isNotEmpty() }
            .joinToString(PAGE_SEP) { (page, keys) ->
                "$page$KV_SEP${keys.distinct().joinToString(ITEM_SEP)}"
            }

    private fun decodeHomePages(raw: String?): Map<Int, List<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<Int, List<String>>()
        raw.split(PAGE_SEP).forEach { segment ->
            val sep = segment.indexOf(KV_SEP)
            if (sep > 0) {
                val page = segment.substring(0, sep).toIntOrNull() ?: return@forEach
                val items = segment.substring(sep + 1).split(ITEM_SEP).filter { it.isNotBlank() }
                if (items.isNotEmpty()) result[page] = items
            }
        }
        return result
    }

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
            DrawerTab(id = id, name = name, appKeys = keys, isHidden = isHidden)
        }
    }

    // Format: "widgetId|pageIndex|spanX|spanY|providerFlatName;..."
    private fun encodePlacedWidgets(widgets: List<PlacedWidget>): String =
        widgets.joinToString(PAGE_SEP) { w ->
            listOf(w.appWidgetId, w.pageIndex, w.spanX, w.spanY, w.providerComponent).joinToString(WIDGET_FIELD_SEP)
        }

    private fun decodePlacedWidgets(raw: String?): List<PlacedWidget> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(PAGE_SEP).mapNotNull { segment ->
            val parts = segment.split(WIDGET_FIELD_SEP)
            if (parts.size < 5) return@mapNotNull null
            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            val page = parts[1].toIntOrNull() ?: return@mapNotNull null
            val spanX = parts[2].toIntOrNull()?.coerceIn(1, 5) ?: return@mapNotNull null
            val spanY = parts[3].toIntOrNull()?.coerceIn(1, 6) ?: return@mapNotNull null
            val provider = parts.drop(4).joinToString(WIDGET_FIELD_SEP)
            if (provider.isBlank()) return@mapNotNull null
            PlacedWidget(
                appWidgetId = id,
                pageIndex = page.coerceAtLeast(0),
                spanX = spanX,
                spanY = spanY,
                providerComponent = provider,
            )
        }
    }

    companion object {
        const val MAX_DOCK_ITEMS = 5
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
