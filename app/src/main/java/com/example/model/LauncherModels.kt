package com.example.model

import android.appwidget.AppWidgetProviderInfo
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap

enum class SwipeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

enum class AppActionOrigin {
    HOME,
    DOCK,
    DRAWER,
}

enum class LauncherDestination {
    HOME,
    DRAWER,
    SETTINGS,
    WIDGET_PICKER,
}

enum class HomePageCommandType {
    FIRST_PAGE,
    PREV_PAGE,
    NEXT_PAGE,
}

@Immutable
data class HomePageCommand(
    val id: Int,
    val type: HomePageCommandType,
)

@Immutable
sealed class HomeGestureAction {
    data object None : HomeGestureAction()
    data object OpenDrawer : HomeGestureAction()
    data object OpenSearch : HomeGestureAction()
    data object OpenSettings : HomeGestureAction()
    data object GoFirstPage : HomeGestureAction()
    data object PrevHomePage : HomeGestureAction()
    data object NextHomePage : HomeGestureAction()
    data object ExpandNotifications : HomeGestureAction()
    data object ExpandQuickSettings : HomeGestureAction()
    data object ToggleFlashlight : HomeGestureAction()
    data object OpenVolumePanel : HomeGestureAction()
    data object ToggleVibrateMode : HomeGestureAction()
    data object OpenVoiceAssist : HomeGestureAction()
    data object OpenCamera : HomeGestureAction()
    data object OpenDialer : HomeGestureAction()
    data object OpenSystemSettings : HomeGestureAction()
    data object OpenWifiPanel : HomeGestureAction()
    data object OpenBluetoothSettings : HomeGestureAction()
    data object OpenWallpaperPicker : HomeGestureAction()
    data class LaunchApp(val componentKey: String) : HomeGestureAction()
    data class LaunchShortcut(val packageName: String, val shortcutId: String) : HomeGestureAction()

    fun encode(): String = when (this) {
        None -> "NONE"
        OpenDrawer -> "OPEN_DRAWER"
        OpenSearch -> "OPEN_SEARCH"
        OpenSettings -> "OPEN_SETTINGS"
        GoFirstPage -> "GO_FIRST_PAGE"
        PrevHomePage -> "PREV_HOME_PAGE"
        NextHomePage -> "NEXT_HOME_PAGE"
        ExpandNotifications -> "EXPAND_NOTIFICATIONS"
        ExpandQuickSettings -> "EXPAND_QUICK_SETTINGS"
        ToggleFlashlight -> "TOGGLE_FLASHLIGHT"
        OpenVolumePanel -> "OPEN_VOLUME_PANEL"
        ToggleVibrateMode -> "TOGGLE_VIBRATE_MODE"
        OpenVoiceAssist -> "OPEN_VOICE_ASSIST"
        OpenCamera -> "OPEN_CAMERA"
        OpenDialer -> "OPEN_DIALER"
        OpenSystemSettings -> "OPEN_SYSTEM_SETTINGS"
        OpenWifiPanel -> "OPEN_WIFI_PANEL"
        OpenBluetoothSettings -> "OPEN_BLUETOOTH_SETTINGS"
        OpenWallpaperPicker -> "OPEN_WALLPAPER_PICKER"
        is LaunchApp -> "APP:$componentKey"
        is LaunchShortcut -> AppShortcutItem.encodeTargetKey(packageName, shortcutId)
    }

    companion object {
        fun decode(raw: String?): HomeGestureAction = when {
            raw.isNullOrBlank() || raw == "NONE" -> None
            raw == "OPEN_DRAWER" -> OpenDrawer
            raw == "OPEN_SEARCH" -> OpenSearch
            raw == "OPEN_SETTINGS" -> OpenSettings
            raw == "GO_FIRST_PAGE" -> GoFirstPage
            raw == "PREV_HOME_PAGE" -> PrevHomePage
            raw == "NEXT_HOME_PAGE" -> NextHomePage
            raw == "EXPAND_NOTIFICATIONS" -> ExpandNotifications
            raw == "EXPAND_QUICK_SETTINGS" -> ExpandQuickSettings
            raw == "TOGGLE_FLASHLIGHT" -> ToggleFlashlight
            raw == "OPEN_VOLUME_PANEL" -> OpenVolumePanel
            raw == "TOGGLE_VIBRATE_MODE" -> ToggleVibrateMode
            raw == "OPEN_VOICE_ASSIST" -> OpenVoiceAssist
            raw == "OPEN_CAMERA" -> OpenCamera
            raw == "OPEN_DIALER" -> OpenDialer
            raw == "OPEN_SYSTEM_SETTINGS" -> OpenSystemSettings
            raw == "OPEN_WIFI_PANEL" -> OpenWifiPanel
            raw == "OPEN_BLUETOOTH_SETTINGS" -> OpenBluetoothSettings
            raw == "OPEN_WALLPAPER_PICKER" -> OpenWallpaperPicker
            raw.startsWith("APP:") -> {
                val key = raw.removePrefix("APP:")
                if (key.isNotBlank()) LaunchApp(key) else None
            }
            raw.startsWith(AppShortcutItem.SHORTCUT_PREFIX) -> {
                AppShortcutItem.parseTargetKey(raw)?.let { (pkg, id) -> LaunchShortcut(pkg, id) } ?: None
            }
            else -> None
        }
    }
}

@Immutable
data class AppItem(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isSystemApp: Boolean,
    val isSelfLauncher: Boolean = false,
) {
    val componentKey: String
        get() = "$packageName/$activityName"
}

@Immutable
data class AppShortcutItem(
    val packageName: String,
    val shortcutId: String,
    val label: String,
    val appLabel: String,
    val icon: ImageBitmap?,
    val rank: Int = Int.MAX_VALUE,
    val isMenuShortcut: Boolean = true,
) {
    val targetKey: String
        get() = encodeTargetKey(packageName, shortcutId)

    val displayLabel: String
        get() = "$appLabel · $label"

    companion object {
        const val SHORTCUT_PREFIX = "SHORTCUT:"
        private const val SHORTCUT_SEP = "|"

        fun encodeTargetKey(packageName: String, shortcutId: String): String =
            "$SHORTCUT_PREFIX${Uri.encode(packageName)}$SHORTCUT_SEP${Uri.encode(shortcutId)}"

        fun parseTargetKey(raw: String): Pair<String, String>? {
            if (!raw.startsWith(SHORTCUT_PREFIX)) return null
            val body = raw.removePrefix(SHORTCUT_PREFIX)
            val sep = body.indexOf(SHORTCUT_SEP)
            if (sep <= 0 || sep >= body.lastIndex) return null
            val pkg = Uri.decode(body.substring(0, sep))
            val id = Uri.decode(body.substring(sep + 1))
            if (pkg.isBlank() || id.isBlank()) return null
            return pkg to id
        }
    }
}

@Immutable
data class AppMenuTarget(
    val app: AppItem,
    val origin: AppActionOrigin,
    val sourcePageIndex: Int = 0,
    val anchorBounds: Rect? = null,
)

@Immutable
data class PlacedWidget(
    val appWidgetId: Int,
    val pageIndex: Int,
    val spanX: Int,
    val spanY: Int,
    val providerComponent: String,
)

@Immutable
data class WidgetCatalogItem(
    val providerInfo: AppWidgetProviderInfo,
    val providerComponent: String,
    val packageName: String,
    val appLabel: String,
    val widgetLabel: String,
    val spanX: Int,
    val spanY: Int,
    val appIcon: ImageBitmap?,
)

@Immutable
data class WidgetMenuTarget(
    val widget: PlacedWidget,
    val anchorBounds: Rect? = null,
)

@Immutable
data class DrawerTab(
    val id: String,
    val name: String,
    val appKeys: List<String> = emptyList(),
    val isHidden: Boolean = false,
) {
    companion object {
        const val ALL_TAB_ID = "all"
    }
}

@Immutable
data class DrawerTabPage(
    val id: String,
    val name: String,
    val isAllTab: Boolean,
    val apps: List<AppItem>,
)

@Immutable
data class LauncherSettings(
    val homePageCount: Int = 2,
    val homeLoopPaging: Boolean = true,
    val homeGridColumns: Int = 4,
    val homePages: Map<Int, List<String>> = emptyMap(),
    val placedWidgets: List<PlacedWidget> = emptyList(),
    val dockKeys: List<String> = emptyList(),
    // Desktop-wide per-app swipe gestures: Map<appComponentKey, Map<SwipeDirection, targetAppOrShortcutKey>>
    val appGestures: Map<String, Map<SwipeDirection, String>> = emptyMap(),
    // 4-direction Home desktop swipe gestures
    val homeGestures: Map<SwipeDirection, HomeGestureAction> = defaultHomeGestures(),
    val drawerColumns: Int = 4,
    // Custom tabs shown after the built-in "All" tab in the App Drawer
    val drawerTabs: List<DrawerTab> = emptyList(),
    val isAllTabHidden: Boolean = false,
    val hasSeededDefaults: Boolean = false,
) {
    companion object {
        fun defaultHomeGestures(): Map<SwipeDirection, HomeGestureAction> = mapOf(
            SwipeDirection.UP to HomeGestureAction.OpenDrawer,
            SwipeDirection.DOWN to HomeGestureAction.ExpandNotifications,
            SwipeDirection.LEFT to HomeGestureAction.None,
            SwipeDirection.RIGHT to HomeGestureAction.None,
        )
    }
}

@Immutable
data class LauncherUiState(
    val isLoading: Boolean = true,
    val allApps: List<AppItem> = emptyList(),
    val appsByKey: Map<String, AppItem> = emptyMap(),
    val hasShortcutHostPermission: Boolean = false,
    val allShortcuts: List<AppShortcutItem> = emptyList(),
    val shortcutsByTargetKey: Map<String, AppShortcutItem> = emptyMap(),
    val homePageApps: List<List<AppItem>> = listOf(emptyList(), emptyList()),
    val homePageWidgets: List<List<PlacedWidget>> = listOf(emptyList(), emptyList()),
    val dockApps: List<AppItem> = emptyList(),
    val drawerTabPages: List<DrawerTabPage> = listOf(
        DrawerTabPage(
            id = DrawerTab.ALL_TAB_ID,
            name = "",
            isAllTab = true,
            apps = emptyList(),
        )
    ),
    val filteredDrawerApps: List<AppItem> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
    val destination: LauncherDestination = LauncherDestination.HOME,
    val currentHomePage: Int = 0,
    val drawerSearchQuery: String = "",
    val autoFocusDrawerSearch: Boolean = false,
    val isDefaultLauncher: Boolean = false,
    val appMenuTarget: AppMenuTarget? = null,
    val activeAppMenuShortcuts: List<AppShortcutItem> = emptyList(),
    val gestureConfigApp: AppItem? = null,
    val movePageTarget: AppMenuTarget? = null,
    val appTabsTarget: AppItem? = null,
    val homeEmptyMenuAnchor: Offset? = null,
    val widgetMenuTarget: WidgetMenuTarget? = null,
    val resizeWidgetTarget: PlacedWidget? = null,
    val moveWidgetTarget: PlacedWidget? = null,
    val widgetPickerPackageFilter: String? = null,
    val homePageCommand: HomePageCommand? = null,
    val snackbarMessage: String? = null,
)
