package com.example.viewmodel

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppRepository
import com.example.data.LauncherPreferencesRepository
import com.example.model.AppActionOrigin
import com.example.model.AppItem
import com.example.model.AppMenuTarget
import com.example.model.AppShortcutItem
import com.example.model.DrawerTab
import com.example.model.DrawerTabPage
import com.example.model.HomeGestureAction
import com.example.model.HomePageCommand
import com.example.model.HomePageCommandType
import com.example.model.LauncherDestination
import com.example.model.LauncherSettings
import com.example.model.LauncherUiState
import com.example.model.PlacedWidget
import com.example.model.SwipeDirection
import com.example.model.WidgetMenuTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val prefsRepository = LauncherPreferencesRepository(application)

    private val installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    private val isLoadingApps = MutableStateFlow(true)
    private val appShortcuts = MutableStateFlow<List<AppShortcutItem>>(emptyList())
    private val hasShortcutPermission = MutableStateFlow(false)
    private val destination = MutableStateFlow(LauncherDestination.HOME)
    private val currentHomePage = MutableStateFlow(0)
    private val searchQuery = MutableStateFlow("")
    private val autoFocusSearch = MutableStateFlow(false)
    private val isDefaultLauncher = MutableStateFlow(false)
    private val appMenuTarget = MutableStateFlow<AppMenuTarget?>(null)
    private val activeAppMenuShortcuts = MutableStateFlow<List<AppShortcutItem>>(emptyList())
    private val gestureConfigApp = MutableStateFlow<AppItem?>(null)
    private val movePageTarget = MutableStateFlow<AppMenuTarget?>(null)
    private val appTabsTarget = MutableStateFlow<AppItem?>(null)
    private val homeEmptyMenuAnchor = MutableStateFlow<Offset?>(null)
    private val widgetMenuTarget = MutableStateFlow<WidgetMenuTarget?>(null)
    private val resizeWidgetTarget = MutableStateFlow<PlacedWidget?>(null)
    private val moveWidgetTarget = MutableStateFlow<PlacedWidget?>(null)
    private val homePageCommand = MutableStateFlow<HomePageCommand?>(null)
    private var nextPageCommandId = 0
    private val snackbarMessage = MutableStateFlow<String?>(null)
    private var appMenuShortcutsJob: Job? = null
    private var bulkShortcutsJob: Job? = null

    private data class NavState(
        val destination: LauncherDestination,
        val homePage: Int,
        val searchQuery: String,
        val autoFocusSearch: Boolean,
        val isDefault: Boolean,
        val homePageCommand: HomePageCommand?,
    )

    private data class OverlayState(
        val menuTarget: AppMenuTarget?,
        val activeMenuShortcuts: List<AppShortcutItem>,
        val gestureApp: AppItem?,
        val moveTarget: AppMenuTarget?,
        val appTabsTarget: AppItem?,
        val snackbar: String?,
        val homeEmptyAnchor: Offset?,
        val widgetMenu: WidgetMenuTarget?,
        val resizeWidget: PlacedWidget?,
        val moveWidget: PlacedWidget?,
    )

    private data class WidgetOverlayState(
        val homeEmptyAnchor: Offset?,
        val widgetMenu: WidgetMenuTarget?,
        val resizeWidget: PlacedWidget?,
        val moveWidget: PlacedWidget?,
    )

    private data class AppOverlayState(
        val menuTarget: AppMenuTarget?,
        val activeMenuShortcuts: List<AppShortcutItem>,
        val gestureApp: AppItem?,
        val moveTarget: AppMenuTarget?,
        val appTabsTarget: AppItem?,
    )

    private val navFlow = combine(
        combine(destination, currentHomePage, searchQuery) { dest, page, query -> Triple(dest, page, query) },
        combine(autoFocusSearch, isDefaultLauncher, homePageCommand) { focus, isDef, cmd -> Triple(focus, isDef, cmd) },
    ) { a, b ->
        NavState(a.first, a.second, a.third, b.first, b.second, b.third)
    }

    private val appOverlayFlow = combine(
        appMenuTarget,
        activeAppMenuShortcuts,
        gestureConfigApp,
        movePageTarget,
        appTabsTarget,
    ) { menu, menuShortcuts, gesture, move, tabs ->
        AppOverlayState(menu, menuShortcuts, gesture, move, tabs)
    }

    private val widgetOverlayFlow = combine(
        homeEmptyMenuAnchor,
        widgetMenuTarget,
        resizeWidgetTarget,
        moveWidgetTarget,
    ) { emptyAnchor, wMenu, wResize, wMove ->
        WidgetOverlayState(emptyAnchor, wMenu, wResize, wMove)
    }

    private val overlayFlow = combine(
        appOverlayFlow,
        snackbarMessage,
        widgetOverlayFlow,
    ) { appOv, snack, widgetOv ->
        OverlayState(
            menuTarget = appOv.menuTarget,
            activeMenuShortcuts = appOv.activeMenuShortcuts,
            gestureApp = appOv.gestureApp,
            moveTarget = appOv.moveTarget,
            appTabsTarget = appOv.appTabsTarget,
            snackbar = snack,
            homeEmptyAnchor = widgetOv.homeEmptyAnchor,
            widgetMenu = widgetOv.widgetMenu,
            resizeWidget = widgetOv.resizeWidget,
            moveWidget = widgetOv.moveWidget,
        )
    }

    private data class CatalogState(
        val apps: List<AppItem>,
        val loading: Boolean,
        val shortcuts: List<AppShortcutItem>,
        val hasShortcutPermission: Boolean,
    )

    private val catalogFlow = combine(
        installedApps,
        isLoadingApps,
        appShortcuts,
        hasShortcutPermission,
    ) { apps, loading, shortcuts, hasPerm ->
        CatalogState(apps, loading, shortcuts, hasPerm)
    }

    private data class DerivedLayoutState(
        val isLoading: Boolean,
        val allApps: List<AppItem>,
        val appsByKey: Map<String, AppItem>,
        val hasShortcutHostPermission: Boolean,
        val allShortcuts: List<AppShortcutItem>,
        val shortcutsByTargetKey: Map<String, AppShortcutItem>,
        val homePageApps: List<List<AppItem>>,
        val homePageWidgets: List<List<PlacedWidget>>,
        val dockApps: List<AppItem>,
        val drawerTabPages: List<DrawerTabPage>,
        val settings: LauncherSettings,
    )

    private val derivedLayoutFlow = combine(
        catalogFlow,
        prefsRepository.settingsFlow,
    ) { catalog, settings ->
        val apps = catalog.apps
        val appsByKey = apps.associateBy { it.componentKey }
        val shortcutsByKey = catalog.shortcuts.associateBy { it.targetKey }

        val dockApps = settings.dockKeys
            .mapNotNull { appsByKey[it] }
            .take(LauncherPreferencesRepository.MAX_DOCK_ITEMS)

        val homePageApps = List(settings.homePageCount) { pageIdx ->
            settings.homePages[pageIdx]
                .orEmpty()
                .mapNotNull { appsByKey[it] }
        }

        val homePageWidgets = List(settings.homePageCount) { pageIdx ->
            settings.placedWidgets.filter { it.pageIndex == pageIdx }
        }

        val drawerTabPages = buildDrawerTabPages(apps, settings)

        DerivedLayoutState(
            isLoading = catalog.loading,
            allApps = apps,
            appsByKey = appsByKey,
            hasShortcutHostPermission = catalog.hasShortcutPermission,
            allShortcuts = catalog.shortcuts,
            shortcutsByTargetKey = shortcutsByKey,
            homePageApps = homePageApps,
            homePageWidgets = homePageWidgets,
            dockApps = dockApps,
            drawerTabPages = drawerTabPages,
            settings = settings,
        )
    }

    val uiState: StateFlow<LauncherUiState> = combine(
        derivedLayoutFlow,
        navFlow,
        overlayFlow,
    ) { layout, nav, overlay ->
        val trimmed = nav.searchQuery.trim()
        val filteredDrawerApps = if (trimmed.isEmpty()) {
            layout.allApps
        } else {
            layout.allApps.filter { app ->
                app.label.contains(trimmed, ignoreCase = true) ||
                    app.packageName.contains(trimmed, ignoreCase = true)
            }
        }

        LauncherUiState(
            isLoading = layout.isLoading,
            allApps = layout.allApps,
            appsByKey = layout.appsByKey,
            hasShortcutHostPermission = layout.hasShortcutHostPermission,
            allShortcuts = layout.allShortcuts,
            shortcutsByTargetKey = layout.shortcutsByTargetKey,
            homePageApps = layout.homePageApps,
            homePageWidgets = layout.homePageWidgets,
            dockApps = layout.dockApps,
            drawerTabPages = layout.drawerTabPages,
            filteredDrawerApps = filteredDrawerApps,
            settings = layout.settings,
            destination = nav.destination,
            currentHomePage = nav.homePage.coerceIn(0, (layout.settings.homePageCount - 1).coerceAtLeast(0)),
            drawerSearchQuery = nav.searchQuery,
            autoFocusDrawerSearch = nav.autoFocusSearch,
            isDefaultLauncher = nav.isDefault,
            appMenuTarget = overlay.menuTarget,
            activeAppMenuShortcuts = overlay.activeMenuShortcuts,
            gestureConfigApp = overlay.gestureApp,
            movePageTarget = overlay.moveTarget,
            appTabsTarget = overlay.appTabsTarget,
            homeEmptyMenuAnchor = overlay.homeEmptyAnchor,
            widgetMenuTarget = overlay.widgetMenu,
            resizeWidgetTarget = overlay.resizeWidget,
            moveWidgetTarget = overlay.moveWidget,
            homePageCommand = nav.homePageCommand,
            snackbarMessage = overlay.snackbar,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState(),
    )

    init {
        appRepository.registerShortcutsChangedCallback { _ ->
            reloadAllShortcuts()
        }
        refreshInstalledApps()
        refreshDefaultLauncherStatus()
    }

    override fun onCleared() {
        appRepository.release()
        super.onCleared()
    }

    fun refreshInstalledApps(changedPackage: String? = null) {
        viewModelScope.launch {
            if (changedPackage != null) {
                appRepository.clearIconCacheForPackage(changedPackage)
            }
            val apps = appRepository.loadInstalledApps()
            installedApps.value = apps
            isLoadingApps.value = false

            val canHost = appRepository.hasShortcutHostPermission()
            hasShortcutPermission.value = canHost
            if (canHost) {
                reloadAllShortcuts(apps)
            } else {
                appShortcuts.value = emptyList()
            }

            val settings = prefsRepository.settingsFlow.first()
            if (!settings.hasSeededDefaults && apps.isNotEmpty()) {
                seedInitialLayout(apps)
            }
        }
    }

    fun refreshDefaultLauncherStatus() {
        val wasHost = hasShortcutPermission.value
        isDefaultLauncher.value = appRepository.isDefaultLauncher()
        val canHost = appRepository.hasShortcutHostPermission()
        hasShortcutPermission.value = canHost
        if (!canHost) {
            bulkShortcutsJob?.cancel()
            appShortcuts.value = emptyList()
            activeAppMenuShortcuts.value = emptyList()
            return
        }
        val apps = installedApps.value
        if (apps.isNotEmpty() && (!wasHost || appShortcuts.value.isEmpty())) {
            reloadAllShortcuts(apps)
        }
    }

    private fun reloadAllShortcuts(apps: List<AppItem> = installedApps.value) {
        if (apps.isEmpty()) return
        bulkShortcutsJob?.cancel()
        bulkShortcutsJob = viewModelScope.launch {
            val canHost = appRepository.hasShortcutHostPermission()
            hasShortcutPermission.value = canHost
            appShortcuts.value = if (canHost) appRepository.loadAppShortcuts(apps) else emptyList()
        }
    }

    fun onHomePageChanged(realPageIndex: Int) {
        currentHomePage.value = realPageIndex
    }

    fun openDrawer(focusSearch: Boolean = false) {
        searchQuery.value = ""
        autoFocusSearch.value = focusSearch
        destination.value = LauncherDestination.DRAWER
    }

    fun clearAutoFocusSearch() {
        autoFocusSearch.value = false
    }

    fun openSettings() {
        appMenuTarget.value = null
        homeEmptyMenuAnchor.value = null
        widgetMenuTarget.value = null
        refreshDefaultLauncherStatus()
        destination.value = LauncherDestination.SETTINGS
    }

    fun returnHome(resetToFirstPage: Boolean = false) {
        searchQuery.value = ""
        autoFocusSearch.value = false
        clearAppMenuState()
        gestureConfigApp.value = null
        movePageTarget.value = null
        appTabsTarget.value = null
        homeEmptyMenuAnchor.value = null
        widgetMenuTarget.value = null
        resizeWidgetTarget.value = null
        moveWidgetTarget.value = null
        destination.value = LauncherDestination.HOME
        if (resetToFirstPage) {
            dispatchHomePageCommand(HomePageCommandType.FIRST_PAGE)
        }
    }

    private fun dispatchHomePageCommand(type: HomePageCommandType) {
        nextPageCommandId += 1
        homePageCommand.value = HomePageCommand(nextPageCommandId, type)
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun openAppMenu(
        app: AppItem,
        origin: AppActionOrigin,
        sourcePageIndex: Int = currentHomePage.value,
        anchorBounds: Rect? = null,
    ) {
        val canHost = appRepository.hasShortcutHostPermission()
        hasShortcutPermission.value = canHost
        isDefaultLauncher.value = appRepository.isDefaultLauncher()

        appMenuTarget.value = AppMenuTarget(app, origin, sourcePageIndex, anchorBounds)

        // Immediately seed from cached shortcuts so there is zero flicker, then query live per-package shortcuts
        val cached = if (canHost && !app.isSelfLauncher) {
            appShortcuts.value
                .filter { it.packageName == app.packageName && it.isMenuShortcut }
                .sortedBy { it.rank }
        } else {
            emptyList()
        }
        activeAppMenuShortcuts.value = cached

        appMenuShortcutsJob?.cancel()
        if (canHost && !app.isSelfLauncher) {
            appMenuShortcutsJob = viewModelScope.launch {
                val fresh = appRepository.loadShortcutsForApp(app)
                if (appMenuTarget.value?.app?.componentKey == app.componentKey) {
                    activeAppMenuShortcuts.value = fresh
                }
            }
        }
    }

    fun dismissAppMenu() {
        clearAppMenuState()
    }

    private fun clearAppMenuState() {
        appMenuShortcutsJob?.cancel()
        appMenuTarget.value = null
        activeAppMenuShortcuts.value = emptyList()
    }

    fun openAppGestureConfig(app: AppItem) {
        appMenuTarget.value = null
        gestureConfigApp.value = app
    }

    fun dismissAppGestureConfig() {
        gestureConfigApp.value = null
    }

    fun setAppGesture(appKey: String, direction: SwipeDirection, targetAppKey: String?) {
        viewModelScope.launch {
            prefsRepository.setAppGesture(appKey, direction, targetAppKey)
        }
    }

    fun onAppSwiped(app: AppItem, direction: SwipeDirection, origin: AppActionOrigin) {
        val state = uiState.value
        val targetKey = state.settings.appGestures[app.componentKey]?.get(direction)
        if (targetKey != null) {
            val shortcutRef = AppShortcutItem.parseTargetKey(targetKey)
            if (shortcutRef != null) {
                launchShortcut(shortcutRef.first, shortcutRef.second)
                return
            }
            val targetApp = state.appsByKey[targetKey]
            if (targetApp != null) {
                launchApp(targetApp)
                return
            }
        }
        if (origin == AppActionOrigin.DOCK) {
            onHomeSwiped(direction)
        }
    }

    fun setHomeGesture(direction: SwipeDirection, action: HomeGestureAction) {
        viewModelScope.launch {
            prefsRepository.setHomeGesture(direction, action)
        }
    }

    fun onHomeSwiped(direction: SwipeDirection) {
        val state = uiState.value
        val action = state.settings.homeGestures[direction] ?: HomeGestureAction.None
        when (action) {
            HomeGestureAction.None -> Unit
            HomeGestureAction.OpenDrawer -> openDrawer(focusSearch = false)
            HomeGestureAction.OpenSearch -> openDrawer(focusSearch = true)
            HomeGestureAction.OpenSettings -> openSettings()
            HomeGestureAction.GoFirstPage -> returnHome(resetToFirstPage = true)
            HomeGestureAction.PrevHomePage -> dispatchHomePageCommand(HomePageCommandType.PREV_PAGE)
            HomeGestureAction.NextHomePage -> dispatchHomePageCommand(HomePageCommandType.NEXT_PAGE)
            HomeGestureAction.ExpandNotifications -> {
                if (!appRepository.expandNotificationsPanel()) {
                    snackbarMessage.value = getApplication<Application>().getString(R.string.expand_status_bar_error)
                }
            }
            HomeGestureAction.ExpandQuickSettings -> {
                if (!appRepository.expandQuickSettingsPanel()) {
                    snackbarMessage.value = getApplication<Application>().getString(R.string.expand_status_bar_error)
                }
            }
            HomeGestureAction.ToggleFlashlight -> runSystemAction { appRepository.toggleFlashlight() }
            HomeGestureAction.OpenVolumePanel -> runSystemAction { appRepository.openVolumePanel() }
            HomeGestureAction.ToggleVibrateMode -> runSystemAction { appRepository.toggleVibrateMode() }
            HomeGestureAction.OpenVoiceAssist -> runSystemAction { appRepository.openVoiceAssist() }
            HomeGestureAction.OpenCamera -> runSystemAction { appRepository.openCamera() }
            HomeGestureAction.OpenDialer -> runSystemAction { appRepository.openDialer() }
            HomeGestureAction.OpenSystemSettings -> runSystemAction { appRepository.openSystemSettings() }
            HomeGestureAction.OpenWifiPanel -> runSystemAction { appRepository.openWifiPanel() }
            HomeGestureAction.OpenBluetoothSettings -> runSystemAction { appRepository.openBluetoothSettings() }
            HomeGestureAction.OpenWallpaperPicker -> runSystemAction { appRepository.openWallpaperPicker() }
            is HomeGestureAction.LaunchApp -> {
                val targetApp = state.appsByKey[action.componentKey]
                if (targetApp != null) {
                    launchApp(targetApp)
                }
            }
            is HomeGestureAction.LaunchShortcut -> {
                launchShortcut(action.packageName, action.shortcutId)
            }
        }
    }

    fun launchShortcut(shortcut: AppShortcutItem) {
        launchShortcut(shortcut.packageName, shortcut.shortcutId)
    }

    private fun launchShortcut(packageName: String, shortcutId: String) {
        appMenuTarget.value = null
        val ok = appRepository.launchShortcut(packageName, shortcutId)
        if (ok) {
            if (destination.value == LauncherDestination.DRAWER) {
                returnHome()
            }
        } else {
            snackbarMessage.value = getApplication<Application>().getString(R.string.shortcut_launch_error)
        }
    }

    private fun runSystemAction(block: () -> Boolean) {
        if (!block()) {
            snackbarMessage.value = getApplication<Application>().getString(R.string.system_action_unavailable)
        }
    }

    fun openMovePageDialog(target: AppMenuTarget) {
        appMenuTarget.value = null
        movePageTarget.value = target
    }

    fun dismissMovePageDialog() {
        movePageTarget.value = null
    }

    fun openAppTabsDialog(app: AppItem) {
        appMenuTarget.value = null
        appTabsTarget.value = app
    }

    fun dismissAppTabsDialog() {
        appTabsTarget.value = null
    }

    fun confirmMoveToPage(target: AppMenuTarget, targetPageIndex: Int) {
        movePageTarget.value = null
        viewModelScope.launch {
            if (target.origin == AppActionOrigin.HOME) {
                prefsRepository.moveAppBetweenHomePages(
                    componentKey = target.app.componentKey,
                    fromPage = target.sourcePageIndex,
                    toPage = targetPageIndex,
                )
            }
        }
    }

    fun launchApp(app: AppItem) {
        appMenuTarget.value = null
        if (app.isSelfLauncher) {
            openSettings()
            return
        }
        val ok = appRepository.launchApp(app)
        if (ok) {
            if (destination.value == LauncherDestination.DRAWER) {
                returnHome()
            }
        } else {
            snackbarMessage.value = getApplication<Application>().getString(R.string.launch_error_message)
        }
    }

    fun pinToCurrentHomePage(app: AppItem) {
        appMenuTarget.value = null
        val page = currentHomePage.value
        viewModelScope.launch {
            prefsRepository.pinAppToHomePage(app.componentKey, page)
        }
    }

    fun removeFromHomePage(app: AppItem, pageIndex: Int) {
        appMenuTarget.value = null
        viewModelScope.launch {
            prefsRepository.removeAppFromHomePage(app.componentKey, pageIndex)
        }
    }

    fun togglePinDock(app: AppItem) {
        appMenuTarget.value = null
        viewModelScope.launch {
            val ok = prefsRepository.togglePinDock(app.componentKey)
            if (!ok) {
                snackbarMessage.value = getApplication<Application>().getString(R.string.dock_full_message)
            }
        }
    }

    fun openAppInfo(app: AppItem) {
        appMenuTarget.value = null
        appRepository.openAppInfo(app.packageName)
    }

    fun uninstallApp(app: AppItem) {
        appMenuTarget.value = null
        appRepository.requestUninstall(app.packageName)
    }

    fun openDefaultHomeSettings() {
        appRepository.openDefaultHomeSettings()
    }

    fun setHomePageCount(count: Int) {
        viewModelScope.launch { prefsRepository.setHomePageCount(count) }
    }

    fun setHomeLoopPaging(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setHomeLoopPaging(enabled) }
    }

    fun setHomeGridColumns(columns: Int) {
        viewModelScope.launch { prefsRepository.setHomeGridColumns(columns) }
    }

    fun setDrawerColumns(columns: Int) {
        viewModelScope.launch { prefsRepository.setDrawerColumns(columns) }
    }

    fun createDrawerTab(name: String, appKeys: List<String> = emptyList()) {
        viewModelScope.launch {
            val id = prefsRepository.createDrawerTab(name, appKeys)
            if (id == null && name.isNotBlank()) {
                snackbarMessage.value = getApplication<Application>().getString(R.string.drawer_tabs_limit_message)
            }
        }
    }

    fun renameDrawerTab(tabId: String, newName: String) {
        viewModelScope.launch { prefsRepository.renameDrawerTab(tabId, newName) }
    }

    fun setDrawerTabApps(tabId: String, appKeys: Set<String>) {
        viewModelScope.launch { prefsRepository.setDrawerTabApps(tabId, appKeys) }
    }

    fun setAppDrawerTabs(componentKey: String, selectedTabIds: Set<String>) {
        appTabsTarget.value = null
        viewModelScope.launch { prefsRepository.setAppDrawerTabs(componentKey, selectedTabIds) }
    }

    fun moveDrawerTab(tabId: String, delta: Int) {
        viewModelScope.launch { prefsRepository.moveDrawerTab(tabId, delta) }
    }

    fun deleteDrawerTab(tabId: String) {
        viewModelScope.launch { prefsRepository.deleteDrawerTab(tabId) }
    }

    fun openHomeEmptyMenu(anchor: Offset) {
        appMenuTarget.value = null
        widgetMenuTarget.value = null
        homeEmptyMenuAnchor.value = anchor
    }

    fun dismissHomeEmptyMenu() {
        homeEmptyMenuAnchor.value = null
    }

    fun openWidgetPicker() {
        homeEmptyMenuAnchor.value = null
        appMenuTarget.value = null
        widgetMenuTarget.value = null
        destination.value = LauncherDestination.WIDGET_PICKER
    }

    fun openWallpaperPicker() {
        homeEmptyMenuAnchor.value = null
        runSystemAction { appRepository.openWallpaperPicker() }
    }

    fun openWidgetMenu(widget: PlacedWidget, anchorBounds: Rect?) {
        homeEmptyMenuAnchor.value = null
        appMenuTarget.value = null
        widgetMenuTarget.value = WidgetMenuTarget(widget, anchorBounds)
    }

    fun dismissWidgetMenu() {
        widgetMenuTarget.value = null
    }

    fun openResizeWidget(widget: PlacedWidget) {
        widgetMenuTarget.value = null
        resizeWidgetTarget.value = widget
    }

    fun dismissResizeWidget() {
        resizeWidgetTarget.value = null
    }

    fun openMoveWidgetDialog(widget: PlacedWidget) {
        widgetMenuTarget.value = null
        moveWidgetTarget.value = widget
    }

    fun dismissMoveWidgetDialog() {
        moveWidgetTarget.value = null
    }

    fun addPlacedWidget(widget: PlacedWidget) {
        viewModelScope.launch {
            prefsRepository.addPlacedWidget(widget)
        }
    }

    fun resizeWidget(appWidgetId: Int, spanX: Int, spanY: Int) {
        resizeWidgetTarget.value = null
        viewModelScope.launch {
            prefsRepository.updateWidgetSpan(appWidgetId, spanX, spanY)
        }
    }

    fun moveWidgetToPage(appWidgetId: Int, targetPage: Int) {
        moveWidgetTarget.value = null
        viewModelScope.launch {
            prefsRepository.moveWidgetToPage(appWidgetId, targetPage)
        }
    }

    fun removeWidget(appWidgetId: Int) {
        widgetMenuTarget.value = null
        viewModelScope.launch {
            prefsRepository.removePlacedWidget(appWidgetId)
        }
    }

    fun pruneMissingWidgets(validIds: Set<Int>) {
        viewModelScope.launch {
            prefsRepository.pruneMissingWidgets(validIds)
        }
    }

    fun showMessage(message: String) {
        snackbarMessage.value = message
    }

    fun consumeSnackbar() {
        snackbarMessage.value = null
    }

    private fun buildDrawerTabPages(apps: List<AppItem>, settings: LauncherSettings): List<DrawerTabPage> {
        val result = ArrayList<DrawerTabPage>(settings.drawerTabs.size + 1)
        result.add(
            DrawerTabPage(
                id = DrawerTab.ALL_TAB_ID,
                name = "",
                isAllTab = true,
                apps = apps,
            )
        )
        for (tab in settings.drawerTabs) {
            val keySet = tab.appKeys.toSet()
            result.add(
                DrawerTabPage(
                    id = tab.id,
                    name = tab.name,
                    isAllTab = false,
                    apps = if (keySet.isEmpty()) emptyList() else apps.filter { it.componentKey in keySet },
                )
            )
        }
        return result
    }

    private suspend fun seedInitialLayout(apps: List<AppItem>) {
        val candidateApps = apps.filterNot { it.isSelfLauncher }
        val dockKeywords = listOf(
            listOf("dialer", "phone", "telecom", "电话"),
            listOf("messaging", "mms", "sms", "信息", "短信"),
            listOf("chrome", "browser", "firefox", "浏览器"),
            listOf("camera", "相机"),
        )

        val chosenDock = mutableListOf<AppItem>()
        for (keywords in dockKeywords) {
            val match = candidateApps.firstOrNull { app ->
                app !in chosenDock && keywords.any { kw ->
                    app.packageName.contains(kw, ignoreCase = true) ||
                        app.label.contains(kw, ignoreCase = true)
                }
            }
            if (match != null) chosenDock.add(match)
        }
        for (app in candidateApps) {
            if (chosenDock.size >= 4) break
            if (app !in chosenDock) chosenDock.add(app)
        }

        val remaining = candidateApps.filterNot { it in chosenDock }
        val page0 = remaining.take(8).map { it.componentKey }
        val page1 = remaining.drop(8).take(8).map { it.componentKey }

        prefsRepository.seedDefaultsIfNeeded(
            dockKeys = chosenDock.map { it.componentKey },
            homePages = mapOf(
                0 to page0,
                1 to page1,
            ),
        )
    }
}
