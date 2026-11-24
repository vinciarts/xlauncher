package com.example.data

import com.example.model.DrawerTab
import com.example.model.HomeGestureAction
import com.example.model.HomeGridEngine
import com.example.model.LauncherSettings
import com.example.model.PlacedHomeApp
import com.example.model.SwipeDirection
import com.example.model.WebSearchEngine
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Validated, sanitized snapshot decoded from a user-selected settings backup JSON file.
 */
data class RestoredLauncherSettings(
    val homePageCount: Int,
    val homeLoopPaging: Boolean,
    val homeGridColumns: Int,
    val homeGridRows: Int,
    val placedHomeApps: List<PlacedHomeApp>,
    val dockKeys: List<String>,
    val appGestures: Map<String, Map<SwipeDirection, String>>,
    val homeGestures: Map<SwipeDirection, HomeGestureAction>,
    val homeButtonAction: HomeGestureAction,
    val drawerColumns: Int,
    val drawerTabs: List<DrawerTab>,
    val isAllTabHidden: Boolean,
    val searchShortcutsEnabled: Boolean,
    val searchContactsEnabled: Boolean,
    val searchThirdPartyAppsEnabled: Boolean,
    val searchWebEnabled: Boolean,
    val searchWebEngine: WebSearchEngine,
)

object LauncherSettingsBackup {

    private const val APP_SIGNATURE = "xlauncher"
    private const val CURRENT_FORMAT_VERSION = 1
    const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

    fun defaultFileName(nowMs: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(nowMs))
        return "xlauncher-backup-$stamp.json"
    }

    fun serialize(settings: LauncherSettings, nowMs: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
        root.put("app", APP_SIGNATURE)
        root.put("formatVersion", CURRENT_FORMAT_VERSION)
        root.put("exportedAt", nowMs)

        // Home screen layout & grid
        root.put("homePageCount", settings.homePageCount)
        root.put("homeLoopPaging", settings.homeLoopPaging)
        root.put("homeGridColumns", settings.homeGridColumns)
        root.put("homeGridRows", settings.homeGridRows)

        val homeAppsArr = JSONArray()
        for (app in settings.placedHomeApps) {
            homeAppsArr.put(
                JSONObject().apply {
                    put("componentKey", app.componentKey)
                    put("pageIndex", app.pageIndex)
                    put("cellX", app.cellX)
                    put("cellY", app.cellY)
                }
            )
        }
        root.put("placedHomeApps", homeAppsArr)

        val dockArr = JSONArray()
        settings.dockKeys.forEach { dockArr.put(it) }
        root.put("dockKeys", dockArr)

        // Gestures & Home button
        val appGesturesObj = JSONObject()
        for ((appKey, dirMap) in settings.appGestures) {
            if (dirMap.isEmpty()) continue
            val inner = JSONObject()
            for ((dir, target) in dirMap) {
                inner.put(dir.name, target)
            }
            appGesturesObj.put(appKey, inner)
        }
        root.put("appGestures", appGesturesObj)

        val homeGesturesObj = JSONObject()
        for (dir in SwipeDirection.entries) {
            val action = settings.homeGestures[dir] ?: HomeGestureAction.None
            homeGesturesObj.put(dir.name, action.encode())
        }
        root.put("homeGestures", homeGesturesObj)
        root.put("homeButtonAction", settings.homeButtonAction.encode())

        // App Drawer
        root.put("drawerColumns", settings.drawerColumns)
        root.put("isAllTabHidden", settings.isAllTabHidden)

        val tabsArr = JSONArray()
        for (tab in settings.drawerTabs) {
            tabsArr.put(
                JSONObject().apply {
                    put("id", tab.id)
                    put("name", tab.name)
                    put("isHidden", tab.isHidden)
                    val keysArr = JSONArray()
                    tab.appKeys.forEach { keysArr.put(it) }
                    put("appKeys", keysArr)
                }
            )
        }
        root.put("drawerTabs", tabsArr)

        // Search preferences (device-bound SAF folder grants are intentionally excluded)
        root.put("searchShortcutsEnabled", settings.searchShortcutsEnabled)
        root.put("searchContactsEnabled", settings.searchContactsEnabled)
        root.put("searchThirdPartyAppsEnabled", settings.searchThirdPartyAppsEnabled)
        root.put("searchWebEnabled", settings.searchWebEnabled)
        root.put("searchWebEngine", settings.searchWebEngine.id)

        return root.toString(2)
    }

    fun deserialize(jsonText: String): Result<RestoredLauncherSettings> = runCatching {
        val trimmed = jsonText.trim()
        require(trimmed.isNotEmpty()) { "Empty backup file" }

        val root = JSONObject(trimmed)
        val appTag = root.optString("app", "")
        require(appTag == APP_SIGNATURE) { "Unrecognized backup signature" }
        val version = root.optInt("formatVersion", -1)
        require(version in 1..CURRENT_FORMAT_VERSION) { "Unsupported backup version: $version" }

        val homePageCount = root.optInt("homePageCount", 2)
            .coerceIn(1, LauncherPreferencesRepository.MAX_HOME_PAGES)
        val homeLoopPaging = root.optBoolean("homeLoopPaging", true)
        val homeGridColumns = root.optInt("homeGridColumns", HomeGridEngine.DEFAULT_HOME_GRID_COLUMNS)
            .coerceIn(HomeGridEngine.MIN_HOME_GRID_COLUMNS, HomeGridEngine.MAX_HOME_GRID_COLUMNS)
        val homeGridRows = root.optInt("homeGridRows", HomeGridEngine.DEFAULT_HOME_GRID_ROWS)
            .coerceIn(HomeGridEngine.MIN_HOME_GRID_ROWS, HomeGridEngine.MAX_HOME_GRID_ROWS)

        val placedHomeApps = mutableListOf<PlacedHomeApp>()
        val seenPageAppKeys = mutableSetOf<String>()
        val homeAppsArr = root.optJSONArray("placedHomeApps")
        if (homeAppsArr != null) {
            for (i in 0 until homeAppsArr.length()) {
                val obj = homeAppsArr.optJSONObject(i) ?: continue
                val key = obj.optString("componentKey", "").trim()
                if (key.isEmpty()) continue
                val page = obj.optInt("pageIndex", 0).coerceIn(0, homePageCount - 1)
                val dedupKey = "$page|$key"
                if (!seenPageAppKeys.add(dedupKey)) continue
                val rawX = obj.optInt("cellX", -1)
                val rawY = obj.optInt("cellY", -1)
                val cx = if (rawX in 0 until homeGridColumns) rawX else -1
                val cy = if (rawY in 0 until homeGridRows) rawY else -1
                placedHomeApps.add(
                    PlacedHomeApp(
                        componentKey = key,
                        pageIndex = page,
                        cellX = cx,
                        cellY = cy,
                    )
                )
            }
        }

        val dockKeys = mutableListOf<String>()
        val dockArr = root.optJSONArray("dockKeys")
        if (dockArr != null) {
            for (i in 0 until dockArr.length()) {
                val key = dockArr.optString(i, "").trim()
                if (key.isNotEmpty() && key !in dockKeys) {
                    dockKeys.add(key)
                    if (dockKeys.size >= LauncherPreferencesRepository.MAX_DOCK_ITEMS) break
                }
            }
        }

        val appGestures = linkedMapOf<String, Map<SwipeDirection, String>>()
        val appGesturesObj = root.optJSONObject("appGestures")
        if (appGesturesObj != null) {
            val keysIter = appGesturesObj.keys()
            while (keysIter.hasNext()) {
                val appKey = keysIter.next().trim()
                if (appKey.isEmpty()) continue
                val inner = appGesturesObj.optJSONObject(appKey) ?: continue
                val dirMap = linkedMapOf<SwipeDirection, String>()
                for (dir in SwipeDirection.entries) {
                    val target = inner.optString(dir.name, "").trim()
                    if (target.isNotEmpty()) {
                        dirMap[dir] = target
                    }
                }
                if (dirMap.isNotEmpty()) {
                    appGestures[appKey] = dirMap
                }
            }
        }

        val defaultGestures = LauncherSettings.defaultHomeGestures()
        val homeGestures = linkedMapOf<SwipeDirection, HomeGestureAction>()
        val homeGesturesObj = root.optJSONObject("homeGestures")
        for (dir in SwipeDirection.entries) {
            homeGestures[dir] = if (homeGesturesObj != null && homeGesturesObj.has(dir.name)) {
                HomeGestureAction.decode(homeGesturesObj.optString(dir.name, ""))
            } else {
                defaultGestures[dir] ?: HomeGestureAction.None
            }
        }

        val homeButtonAction = if (root.has("homeButtonAction")) {
            HomeGestureAction.decode(root.optString("homeButtonAction", ""))
        } else {
            HomeGestureAction.GoFirstPage
        }

        val drawerColumns = root.optInt("drawerColumns", 4).coerceIn(4, 5)
        val rawAllHidden = root.optBoolean("isAllTabHidden", false)

        val drawerTabs = mutableListOf<DrawerTab>()
        val seenTabIds = mutableSetOf<String>()
        val tabsArr = root.optJSONArray("drawerTabs")
        if (tabsArr != null) {
            for (i in 0 until tabsArr.length()) {
                if (drawerTabs.size >= LauncherPreferencesRepository.MAX_DRAWER_TABS) break
                val obj = tabsArr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim().take(24)
                if (name.isEmpty()) continue
                var id = obj.optString("id", "").trim()
                if (id.isEmpty() || id == DrawerTab.ALL_TAB_ID || !seenTabIds.add(id)) {
                    id = UUID.randomUUID().toString()
                    seenTabIds.add(id)
                }
                val isHidden = obj.optBoolean("isHidden", false)
                val appKeys = mutableListOf<String>()
                val keysArr = obj.optJSONArray("appKeys")
                if (keysArr != null) {
                    for (j in 0 until keysArr.length()) {
                        val k = keysArr.optString(j, "").trim()
                        if (k.isNotEmpty() && k !in appKeys) appKeys.add(k)
                    }
                }
                drawerTabs.add(
                    DrawerTab(
                        id = id,
                        name = name,
                        appKeys = appKeys.toImmutableList(),
                        isHidden = isHidden,
                    )
                )
            }
        }

        // Guarantee at least one visible tab in the App Drawer
        val hasVisibleCustomTab = drawerTabs.any { !it.isHidden }
        val isAllTabHidden = rawAllHidden && hasVisibleCustomTab

        val searchShortcutsEnabled = root.optBoolean("searchShortcutsEnabled", false)
        val searchContactsEnabled = root.optBoolean("searchContactsEnabled", false)
        val legacyThirdPartyArr = root.optJSONArray("searchThirdPartyPackages")
        val hasLegacyThirdParty = legacyThirdPartyArr != null && legacyThirdPartyArr.length() > 0
        val searchThirdPartyAppsEnabled = if (root.has("searchThirdPartyAppsEnabled")) {
            root.optBoolean("searchThirdPartyAppsEnabled", false)
        } else {
            hasLegacyThirdParty
        }
        val searchWebEnabled = root.optBoolean("searchWebEnabled", true)
        val searchWebEngine = WebSearchEngine.decode(root.optString("searchWebEngine", ""))

        RestoredLauncherSettings(
            homePageCount = homePageCount,
            homeLoopPaging = homeLoopPaging,
            homeGridColumns = homeGridColumns,
            homeGridRows = homeGridRows,
            placedHomeApps = placedHomeApps,
            dockKeys = dockKeys,
            appGestures = appGestures,
            homeGestures = homeGestures,
            homeButtonAction = homeButtonAction,
            drawerColumns = drawerColumns,
            drawerTabs = drawerTabs,
            isAllTabHidden = isAllTabHidden,
            searchShortcutsEnabled = searchShortcutsEnabled,
            searchContactsEnabled = searchContactsEnabled,
            searchThirdPartyAppsEnabled = searchThirdPartyAppsEnabled,
            searchWebEnabled = searchWebEnabled,
            searchWebEngine = searchWebEngine,
        )
    }
}
