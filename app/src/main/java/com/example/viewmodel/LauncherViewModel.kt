package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppRepository
import com.example.data.LauncherPreferencesRepository
import com.example.data.LauncherSettingsBackup
import com.example.data.UniversalSearchRepository
import com.example.model.AppActionOrigin
import com.example.model.AppItem
import com.example.model.AppMenuTarget
import com.example.model.AppShortcutItem
import com.example.model.ContactSearchResult
import com.example.model.DrawerTab
import com.example.model.DrawerTabPage
import com.example.model.FileSearchResult
import com.example.model.HomeAppSlot
import com.example.model.HomeGestureAction
import com.example.model.HomeGridEngine
import com.example.model.HomePageCommand
import com.example.model.HomePageCommandType
import com.example.model.IndexedFolderInfo
import com.example.model.LauncherDestination
import com.example.model.LauncherSettings
import com.example.model.LauncherUiState
import com.example.model.PlacedWidget
import com.example.model.SearchProviderApp
import com.example.model.SwipeDirection
import com.example.search.PinyinSearchEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val prefsRepository = LauncherPreferencesRepository(application)
    private val searchRepository = UniversalSearchRepository(application)

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
    private val gestureConfigApp = MutableStateFlow<AppItem?>(null)
    private val movePageTarget = MutableStateFlow<AppMenuTarget?>(null)
    private val appTabsTarget = MutableStateFlow<AppItem?>(null)
    private val homeEmptyMenuAnchor = MutableStateFlow<Offset?>(null)
    private val widgetMenuId = MutableStateFlow<Int?>(null)
    private val resizingWidgetId = MutableStateFlow<Int?>(null)
    private val moveWidgetTarget = MutableStateFlow<PlacedWidget?>(null)
    private val widgetPickerPackageFilter = MutableStateFlow<String?>(null)
    private val homePageCommand = MutableStateFlow<HomePageCommand?>(null)
    private var nextPageCommandId = 0
    private val snackbarMessage = MutableStateFlow<String?>(null)
    private val cachedContacts = MutableStateFlow<List<ContactSearchResult>>(emptyList())
    private val cachedFiles = MutableStateFlow<List<FileSearchResult>>(emptyList())
    private val indexedFolders = MutableStateFlow<List<IndexedFolderInfo>>(emptyList())
    private val availableSearchProviders = MutableStateFlow<List<SearchProviderApp>>(emptyList())
    private var appMenuShortcutsJob: Job? = null
    private var bulkShortcutsJob: Job? = null
    private var contactsLoadJob: Job? = null
    private var filesIndexJob: Job? = null

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
        val gestureApp: AppItem?,
        val moveTarget: AppMenuTarget?,
        val appTabsTarget: AppItem?,
        val snackbar: String?,
        val homeEmptyAnchor: Offset?,
        val widgetMenuId: Int?,
        val resizingWidgetId: Int?,
        val moveWidget: PlacedWidget?,
        val widgetPickerPackageFilter: String?,
    )

    private data class WidgetOverlayState(
        val homeEmptyAnchor: Offset?,
        val widgetMenuId: Int?,
        val resizingWidgetId: Int?,
        val moveWidget: PlacedWidget?,
        val widgetPickerPackageFilter: String?,
    )

    private data class AppOverlayState(
        val menuTarget: AppMenuTarget?,
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
        gestureConfigApp,
        movePageTarget,
        appTabsTarget,
    ) { menu, gesture, move, tabs ->
        AppOverlayState(menu, gesture, move, tabs)
    }

    private val widgetOverlayFlow = combine(
        homeEmptyMenuAnchor,
        widgetMenuId,
        resizingWidgetId,
        moveWidgetTarget,
        widgetPickerPackageFilter,
    ) { emptyAnchor, wMenuId, wResizeId, wMove, pkgFilter ->
        WidgetOverlayState(emptyAnchor, wMenuId, wResizeId, wMove, pkgFilter)
    }

    private val overlayFlow = combine(
        appOverlayFlow,
        snackbarMessage,
        widgetOverlayFlow,
    ) { appOv, snack, widgetOv ->
        OverlayState(
            menuTarget = appOv.menuTarget,
            gestureApp = appOv.gestureApp,
            moveTarget = appOv.moveTarget,
            appTabsTarget = appOv.appTabsTarget,
            snackbar = snack,
            homeEmptyAnchor = widgetOv.homeEmptyAnchor,
            widgetMenuId = widgetOv.widgetMenuId,
            resizingWidgetId = widgetOv.resizingWidgetId,
            moveWidget = widgetOv.moveWidget,
            widgetPickerPackageFilter = widgetOv.widgetPickerPackageFilter,
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
        val allApps: ImmutableList<AppItem>,
        val appsByKey: ImmutableMap<String, AppItem>,
        val hasShortcutHostPermission: Boolean,
        val allShortcuts: ImmutableList<AppShortcutItem>,
        val shortcutsByTargetKey: ImmutableMap<String, AppShortcutItem>,
        val homePageApps: ImmutableList<ImmutableList<HomeAppSlot>>,
        val homePageWidgets: ImmutableList<ImmutableList<PlacedWidget>>,
        val dockApps: ImmutableList<AppItem>,
        val drawerTabPages: ImmutableList<DrawerTabPage>,
        val settings: LauncherSettings,
    )

    private val derivedLayoutFlow = combine(
        catalogFlow,
        prefsRepository.settingsFlow,
    ) { catalog, settings ->
        val apps = catalog.apps.toImmutableList()
        val appsByKey = apps.associateBy { it.componentKey }.toImmutableMap()
        val shortcuts = catalog.shortcuts.toImmutableList()
        val shortcutsByKey = shortcuts.associateBy { it.targetKey }.toImmutableMap()

        val dockApps = settings.dockKeys
            .mapNotNull { appsByKey[it] }
            .take(LauncherPreferencesRepository.MAX_DOCK_ITEMS)
            .toImmutableList()

        val homePageApps = ArrayList<ImmutableList<HomeAppSlot>>(settings.homePageCount)
        val homePageWidgets = ArrayList<ImmutableList<PlacedWidget>>(settings.homePageCount)
        for (pageIdx in 0 until settings.homePageCount) {
            val rawPageWidgets = settings.placedWidgets.filter { it.pageIndex == pageIdx }
            val rawPageApps = settings.placedHomeApps.filter {
                it.pageIndex == pageIdx && it.componentKey in appsByKey
            }
            val (normW, normA) = HomeGridEngine.normalizePage(
                widgets = rawPageWidgets,
                apps = rawPageApps,
                cols = settings.homeGridColumns,
            )
            homePageWidgets.add(normW.toImmutableList())
            homePageApps.add(
                normA.mapNotNull { placed ->
                    appsByKey[placed.componentKey]?.let { app ->
                        HomeAppSlot(
                            app = app,
                            pageIndex = pageIdx,
                            cellX = placed.cellX,
                            cellY = placed.cellY,
                        )
                    }
                }.toImmutableList()
            )
        }

        val drawerTabPages = buildDrawerTabPages(apps, settings)

        DerivedLayoutState(
            isLoading = catalog.loading,
            allApps = apps,
            appsByKey = appsByKey,
            hasShortcutHostPermission = catalog.hasShortcutPermission,
            allShortcuts = shortcuts,
            shortcutsByTargetKey = shortcutsByKey,
            homePageApps = homePageApps.toImmutableList(),
            homePageWidgets = homePageWidgets.toImmutableList(),
            dockApps = dockApps,
            drawerTabPages = drawerTabPages,
            settings = settings,
        )
    }

    private data class SearchCacheState(
        val contacts: List<ContactSearchResult>,
        val files: List<FileSearchResult>,
        val folders: List<IndexedFolderInfo>,
        val providers: List<SearchProviderApp>,
    )

    private val searchCacheFlow = combine(
        cachedContacts,
        cachedFiles,
        indexedFolders,
        availableSearchProviders,
    ) { contacts, files, folders, providers ->
        SearchCacheState(contacts, files, folders, providers)
    }

    val uiState: StateFlow<LauncherUiState> = combine(
        derivedLayoutFlow,
        navFlow,
        overlayFlow,
        searchCacheFlow,
    ) { layout, nav, overlay, searchCache ->
        val trimmed = nav.searchQuery.trim()
        val settings = layout.settings

        val filteredDrawerApps = if (trimmed.isEmpty()) {
            layout.allApps
        } else {
            layout.allApps.mapNotNull { app ->
                val score = PinyinSearchEngine.matchScore(trimmed, app.label, app.packageName)
                if (score != PinyinSearchEngine.NO_MATCH) score to app else null
            }
                .sortedBy { it.first }
                .map { it.second }
                .toImmutableList()
        }

        val matchedShortcuts = if (trimmed.isNotEmpty() && settings.searchShortcutsEnabled) {
            layout.allShortcuts.mapNotNull { shortcut ->
                val score = PinyinSearchEngine.matchScore(trimmed, shortcut.label, shortcut.appLabel)
                if (score != PinyinSearchEngine.NO_MATCH) score to shortcut else null
            }
                .sortedWith(compareBy<Pair<Int, AppShortcutItem>> { it.first }.thenBy { it.second.rank })
                .take(6)
                .map { it.second }
                .toImmutableList()
        } else {
            persistentListOf()
        }

        val matchedContacts = if (trimmed.isNotEmpty() && settings.searchContactsEnabled) {
            searchRepository.searchContacts(trimmed, searchCache.contacts, limit = 6).toImmutableList()
        } else {
            persistentListOf()
        }

        val matchedFiles = if (trimmed.isNotEmpty() && settings.searchFilesEnabled) {
            searchRepository.searchFiles(trimmed, searchCache.files, limit = 8).toImmutableList()
        } else {
            persistentListOf()
        }

        val activeSearchProviders = if (trimmed.isNotEmpty() && settings.searchThirdPartyPackages.isNotEmpty()) {
            val allowed = settings.searchThirdPartyPackages.toSet()
            searchCache.providers
                .filter { it.packageName in allowed }
                .toImmutableList()
        } else {
            persistentListOf()
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
            matchedShortcuts = matchedShortcuts,
            matchedContacts = matchedContacts,
            matchedFiles = matchedFiles,
            availableSearchProviders = searchCache.providers.toImmutableList(),
            activeSearchProviders = activeSearchProviders,
            indexedFolders = searchCache.folders.toImmutableList(),
            indexedFileCount = searchCache.files.size,
            settings = settings,
            destination = nav.destination,
            currentHomePage = nav.homePage.coerceIn(0, (settings.homePageCount - 1).coerceAtLeast(0)),
            drawerSearchQuery = nav.searchQuery,
            autoFocusDrawerSearch = nav.autoFocusSearch,
            isDefaultLauncher = nav.isDefault,
            appMenuTarget = overlay.menuTarget,
            gestureConfigApp = overlay.gestureApp,
            movePageTarget = overlay.moveTarget,
            appTabsTarget = overlay.appTabsTarget,
            homeEmptyMenuAnchor = overlay.homeEmptyAnchor,
            widgetMenuId = overlay.widgetMenuId,
            resizingWidgetId = overlay.resizingWidgetId,
            moveWidgetTarget = overlay.moveWidget,
            widgetPickerPackageFilter = overlay.widgetPickerPackageFilter,
            homePageCommand = nav.homePageCommand,
            snackbarMessage = overlay.snackbar,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState(),
    )

    init {
        appRepository.registerLauncherCallbacks(
            onShortcutsChanged = { _ -> reloadAllShortcuts() },
            onPackagesChanged = { pkg -> refreshInstalledApps(changedPackage = pkg) },
        )
        observeSearchSourceSettings()
        refreshInstalledApps()
        refreshDefaultLauncherStatus()
    }

    private fun observeSearchSourceSettings() {
        viewModelScope.launch {
            prefsRepository.settingsFlow
                .map { it.searchContactsEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        if (searchRepository.hasContactsPermission()) {
                            reloadContactsCache()
                        } else {
                            cachedContacts.value = emptyList()
                            prefsRepository.setSearchContactsEnabled(false)
                        }
                    } else {
                        contactsLoadJob?.cancel()
                        cachedContacts.value = emptyList()
                    }
                }
        }

        viewModelScope.launch {
            prefsRepository.settingsFlow
                .map { it.searchFileTreeUris }
                .distinctUntilChanged()
                .collect { uris ->
                    indexedFolders.value = searchRepository.resolveIndexedFolders(uris)
                }
        }

        viewModelScope.launch {
            prefsRepository.settingsFlow
                .map { it.searchFilesEnabled to it.searchFileTreeUris }
                .distinctUntilChanged()
                .collect { (enabled, uris) ->
                    filesIndexJob?.cancel()
                    if (enabled && uris.isNotEmpty()) {
                        filesIndexJob = viewModelScope.launch {
                            cachedFiles.value = searchRepository.indexFileTrees(uris)
                        }
                    } else {
                        cachedFiles.value = emptyList()
                    }
                }
        }
    }

    private fun reloadContactsCache() {
        contactsLoadJob?.cancel()
        contactsLoadJob = viewModelScope.launch {
            cachedContacts.value = searchRepository.loadContactsIfPermitted()
        }
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

            launch(Dispatchers.Default) {
                PinyinSearchEngine.prewarm(apps.map { it.label })
            }
            launch {
                availableSearchProviders.value = searchRepository.loadThirdPartySearchProviders()
            }

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
            val loaded = if (canHost) appRepository.loadAppShortcuts(apps) else emptyList()
            appShortcuts.value = loaded
            if (loaded.isNotEmpty()) {
                withContext(Dispatchers.Default) {
                    PinyinSearchEngine.prewarm(loaded.map { it.label })
                }
            }
        }
    }

    fun onHomePageChanged(realPageIndex: Int) {
        currentHomePage.value = realPageIndex
    }

    fun openDrawer(focusSearch: Boolean = false) {
        resizingWidgetId.value = null
        searchQuery.value = ""
        autoFocusSearch.value = focusSearch
        destination.value = LauncherDestination.DRAWER
    }

    fun clearAutoFocusSearch() {
        autoFocusSearch.value = false
    }

    fun openSettings() {
        clearAppMenuState()
        homeEmptyMenuAnchor.value = null
        widgetMenuId.value = null
        resizingWidgetId.value = null
        refreshDefaultLauncherStatus()
        destination.value = LauncherDestination.SETTINGS
    }

    fun handleHomeButton(alreadyInForeground: Boolean) {
        val isCleanHome = alreadyInForeground &&
            destination.value == LauncherDestination.HOME &&
            appMenuTarget.value == null &&
            gestureConfigApp.value == null &&
            movePageTarget.value == null &&
            appTabsTarget.value == null &&
            homeEmptyMenuAnchor.value == null &&
            widgetMenuId.value == null &&
            resizingWidgetId.value == null &&
            moveWidgetTarget.value == null

        if (isCleanHome) {
            executeHomeAction(uiState.value.settings.homeButtonAction)
        } else {
            returnHome(resetToFirstPage = false)
        }
    }

    fun returnHome(resetToFirstPage: Boolean = false) {
        searchQuery.value = ""
        autoFocusSearch.value = false
        clearAppMenuState()
        gestureConfigApp.value = null
        movePageTarget.value = null
        appTabsTarget.value = null
        homeEmptyMenuAnchor.value = null
        widgetMenuId.value = null
        resizingWidgetId.value = null
        moveWidgetTarget.value = null
        widgetPickerPackageFilter.value = null
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
    ) {
        val canHost = appRepository.hasShortcutHostPermission()
        hasShortcutPermission.value = canHost
        isDefaultLauncher.value = appRepository.isDefaultLauncher()

        appMenuShortcutsJob?.cancel()
        homeEmptyMenuAnchor.value = null
        widgetMenuId.value = null

        if (!canHost || app.isSelfLauncher) {
            appMenuTarget.value = AppMenuTarget(
                app = app,
                origin = origin,
                sourcePageIndex = sourcePageIndex,
                shortcuts = persistentListOf(),
            )
            return
        }

        val cached = appShortcuts.value
            .filter { it.packageName == app.packageName && it.isMenuShortcut }
            .sortedBy { it.rank }
            .toImmutableList()

        if (cached.isNotEmpty()) {
            appMenuTarget.value = AppMenuTarget(
                app = app,
                origin = origin,
                sourcePageIndex = sourcePageIndex,
                shortcuts = cached,
            )
            appMenuShortcutsJob = viewModelScope.launch {
                val fresh = appRepository.loadShortcutsForApp(app).toImmutableList()
                val current = appMenuTarget.value
                if (current != null &&
                    current.app.componentKey == app.componentKey &&
                    current.origin == origin &&
                    current.sourcePageIndex == sourcePageIndex &&
                    fresh.map { it.shortcutId to it.label } != current.shortcuts.map { it.shortcutId to it.label }
                ) {
                    appMenuTarget.value = current.copy(shortcuts = fresh)
                }
            }
        } else {
            appMenuShortcutsJob = viewModelScope.launch {
                val fresh = withTimeoutOrNull(120L) {
                    appRepository.loadShortcutsForApp(app)
                }.orEmpty().toImmutableList()
                appMenuTarget.value = AppMenuTarget(
                    app = app,
                    origin = origin,
                    sourcePageIndex = sourcePageIndex,
                    shortcuts = fresh,
                )
            }
        }
    }

    fun dismissAppMenu() {
        clearAppMenuState()
    }

    private fun clearAppMenuState() {
        appMenuShortcutsJob?.cancel()
        appMenuShortcutsJob = null
        appMenuTarget.value = null
    }

    fun openAppGestureConfig(app: AppItem) {
        clearAppMenuState()
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

    fun setHomeButtonAction(action: HomeGestureAction) {
        viewModelScope.launch {
            prefsRepository.setHomeButtonAction(action)
        }
    }

    fun onHomeSwiped(direction: SwipeDirection) {
        val action = uiState.value.settings.homeGestures[direction] ?: HomeGestureAction.None
        executeHomeAction(action)
    }

    private fun executeHomeAction(action: HomeGestureAction) {
        val state = uiState.value
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
        clearAppMenuState()
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
        clearAppMenuState()
        movePageTarget.value = target
    }

    fun dismissMovePageDialog() {
        movePageTarget.value = null
    }

    fun openAppTabsDialog(app: AppItem) {
        clearAppMenuState()
        appTabsTarget.value = app
    }

    fun dismissAppTabsDialog() {
        appTabsTarget.value = null
    }

    fun confirmMoveToPage(target: AppMenuTarget, targetPageIndex: Int) {
        movePageTarget.value = null
        viewModelScope.launch {
            if (target.origin == AppActionOrigin.HOME) {
                val moved = prefsRepository.moveAppBetweenHomePages(
                    componentKey = target.app.componentKey,
                    fromPage = target.sourcePageIndex,
                    toPage = targetPageIndex,
                )
                if (!moved) {
                    snackbarMessage.value = getApplication<Application>().getString(R.string.home_page_full_message)
                }
            }
        }
    }

    fun moveHomeAppToCell(
        componentKey: String,
        fromPage: Int,
        targetPage: Int,
        targetCellX: Int,
        targetCellY: Int,
    ) {
        viewModelScope.launch {
            prefsRepository.moveAppToCell(
                componentKey = componentKey,
                fromPage = fromPage,
                targetPage = targetPage,
                targetCellX = targetCellX,
                targetCellY = targetCellY,
            )
        }
    }

    fun moveHomeAppToNewPage(
        componentKey: String,
        fromPage: Int,
        atStart: Boolean,
        targetCellX: Int,
        targetCellY: Int,
    ) {
        val currentCount = uiState.value.settings.homePageCount
        if (currentCount >= LauncherPreferencesRepository.MAX_HOME_PAGES) {
            snackbarMessage.value = getApplication<Application>().getString(
                R.string.home_pages_limit_message,
                LauncherPreferencesRepository.MAX_HOME_PAGES,
            )
            return
        }
        currentHomePage.value = if (atStart) 0 else currentCount
        viewModelScope.launch {
            val moved = prefsRepository.insertHomePageAndMoveApp(
                componentKey = componentKey,
                fromPage = fromPage,
                atStart = atStart,
                targetCellX = targetCellX,
                targetCellY = targetCellY,
            )
            if (!moved) {
                snackbarMessage.value = getApplication<Application>().getString(
                    R.string.home_pages_limit_message,
                    LauncherPreferencesRepository.MAX_HOME_PAGES,
                )
            }
        }
    }

    fun reorderDockApp(componentKey: String, targetIndex: Int) {
        viewModelScope.launch {
            prefsRepository.reorderDockApp(componentKey, targetIndex)
        }
    }

    fun moveDockAppToHomeCell(
        componentKey: String,
        targetPage: Int,
        targetCellX: Int,
        targetCellY: Int,
    ) {
        viewModelScope.launch {
            prefsRepository.moveDockAppToHomeCell(
                componentKey = componentKey,
                targetPage = targetPage,
                targetCellX = targetCellX,
                targetCellY = targetCellY,
            )
        }
    }

    fun moveDockAppToNewPage(
        componentKey: String,
        atStart: Boolean,
        targetCellX: Int,
        targetCellY: Int,
    ) {
        val currentCount = uiState.value.settings.homePageCount
        if (currentCount >= LauncherPreferencesRepository.MAX_HOME_PAGES) {
            snackbarMessage.value = getApplication<Application>().getString(
                R.string.home_pages_limit_message,
                LauncherPreferencesRepository.MAX_HOME_PAGES,
            )
            return
        }
        currentHomePage.value = if (atStart) 0 else currentCount
        viewModelScope.launch {
            val moved = prefsRepository.insertHomePageAndMoveDockApp(
                componentKey = componentKey,
                atStart = atStart,
                targetCellX = targetCellX,
                targetCellY = targetCellY,
            )
            if (!moved) {
                snackbarMessage.value = getApplication<Application>().getString(
                    R.string.home_pages_limit_message,
                    LauncherPreferencesRepository.MAX_HOME_PAGES,
                )
            }
        }
    }

    fun moveHomeAppToDock(
        componentKey: String,
        fromPage: Int,
        targetDockIndex: Int,
    ) {
        viewModelScope.launch {
            prefsRepository.moveHomeAppToDock(
                componentKey = componentKey,
                fromPage = fromPage,
                targetDockIndex = targetDockIndex,
            )
        }
    }

    fun launchApp(app: AppItem) {
        clearAppMenuState()
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
        clearAppMenuState()
        val page = currentHomePage.value
        viewModelScope.launch {
            val pinned = prefsRepository.pinAppToHomePage(app.componentKey, page)
            if (!pinned) {
                snackbarMessage.value = getApplication<Application>().getString(R.string.home_page_full_message)
            }
        }
    }

    fun removeFromHomePage(app: AppItem, pageIndex: Int) {
        clearAppMenuState()
        viewModelScope.launch {
            prefsRepository.removeAppFromHomePage(app.componentKey, pageIndex)
        }
    }

    fun togglePinDock(app: AppItem) {
        clearAppMenuState()
        viewModelScope.launch {
            val ok = prefsRepository.togglePinDock(app.componentKey)
            if (!ok) {
                snackbarMessage.value = getApplication<Application>().getString(R.string.dock_full_message)
            }
        }
    }

    fun openAppInfo(app: AppItem) {
        clearAppMenuState()
        appRepository.openAppInfo(app.packageName)
    }

    fun uninstallApp(app: AppItem) {
        clearAppMenuState()
        appRepository.requestUninstall(app.packageName)
    }

    fun openDefaultHomeSettings() {
        appRepository.openDefaultHomeSettings()
    }

    fun setHomePageCount(count: Int) {
        viewModelScope.launch { prefsRepository.setHomePageCount(count) }
    }

    fun insertHomePageAtStart() {
        homeEmptyMenuAnchor.value = null
        val currentCount = uiState.value.settings.homePageCount
        if (currentCount >= LauncherPreferencesRepository.MAX_HOME_PAGES) {
            snackbarMessage.value = getApplication<Application>().getString(
                R.string.home_pages_limit_message,
                LauncherPreferencesRepository.MAX_HOME_PAGES,
            )
            return
        }
        currentHomePage.value = 0
        viewModelScope.launch {
            prefsRepository.insertHomePageAtStart()
        }
    }

    fun appendHomePageAtEnd() {
        homeEmptyMenuAnchor.value = null
        val currentCount = uiState.value.settings.homePageCount
        if (currentCount >= LauncherPreferencesRepository.MAX_HOME_PAGES) {
            snackbarMessage.value = getApplication<Application>().getString(
                R.string.home_pages_limit_message,
                LauncherPreferencesRepository.MAX_HOME_PAGES,
            )
            return
        }
        currentHomePage.value = currentCount
        viewModelScope.launch {
            prefsRepository.appendHomePageAtEnd()
        }
    }

    fun deleteCurrentHomePage(onWidgetsRemoved: (List<Int>) -> Unit) {
        homeEmptyMenuAnchor.value = null
        resizingWidgetId.value = null
        val state = uiState.value
        val currentCount = state.settings.homePageCount
        if (currentCount <= 1) return
        val targetPage = state.currentHomePage.coerceIn(0, currentCount - 1)
        currentHomePage.value = targetPage.coerceIn(0, (currentCount - 2).coerceAtLeast(0))
        viewModelScope.launch {
            val removedWidgetIds = prefsRepository.deleteHomePage(targetPage)
            if (removedWidgetIds.isNotEmpty()) {
                onWidgetsRemoved(removedWidgetIds)
            }
        }
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

    fun setAllTabHidden(hidden: Boolean) {
        viewModelScope.launch {
            val ok = prefsRepository.setAllTabHidden(hidden)
            if (!ok) {
                snackbarMessage.value = getApplication<Application>().getString(R.string.drawer_tab_keep_one_visible)
            }
        }
    }

    fun setDrawerTabHidden(tabId: String, hidden: Boolean) {
        viewModelScope.launch {
            val ok = prefsRepository.setDrawerTabHidden(tabId, hidden)
            if (!ok) {
                snackbarMessage.value = getApplication<Application>().getString(R.string.drawer_tab_keep_one_visible)
            }
        }
    }

    fun deleteDrawerTab(tabId: String) {
        viewModelScope.launch { prefsRepository.deleteDrawerTab(tabId) }
    }

    fun openHomeEmptyMenu(anchor: Offset) {
        clearAppMenuState()
        widgetMenuId.value = null
        resizingWidgetId.value = null
        homeEmptyMenuAnchor.value = anchor
    }

    fun dismissHomeEmptyMenu() {
        homeEmptyMenuAnchor.value = null
    }

    fun openWidgetPicker(packageName: String? = null) {
        homeEmptyMenuAnchor.value = null
        clearAppMenuState()
        widgetMenuId.value = null
        resizingWidgetId.value = null
        widgetPickerPackageFilter.value = packageName
        destination.value = LauncherDestination.WIDGET_PICKER
    }

    fun clearWidgetPickerPackageFilter() {
        widgetPickerPackageFilter.value = null
    }

    fun openWallpaperPicker() {
        homeEmptyMenuAnchor.value = null
        runSystemAction { appRepository.openWallpaperPicker() }
    }

    fun openWidgetMenu(widget: PlacedWidget) {
        homeEmptyMenuAnchor.value = null
        clearAppMenuState()
        resizingWidgetId.value = null
        widgetMenuId.value = widget.appWidgetId
    }

    fun dismissWidgetMenu() {
        widgetMenuId.value = null
    }

    fun openResizeWidget(widget: PlacedWidget) {
        widgetMenuId.value = null
        homeEmptyMenuAnchor.value = null
        clearAppMenuState()
        resizingWidgetId.value = widget.appWidgetId
    }

    fun dismissResizeWidget() {
        resizingWidgetId.value = null
    }

    fun openMoveWidgetDialog(widget: PlacedWidget) {
        widgetMenuId.value = null
        resizingWidgetId.value = null
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

    fun resizeWidget(
        appWidgetId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    ) {
        viewModelScope.launch {
            prefsRepository.updateWidgetRect(appWidgetId, cellX, cellY, spanX, spanY)
        }
    }

    fun moveWidgetToCell(
        appWidgetId: Int,
        targetPage: Int,
        targetCellX: Int,
        targetCellY: Int,
    ) {
        viewModelScope.launch {
            prefsRepository.moveWidgetToCell(appWidgetId, targetPage, targetCellX, targetCellY)
        }
    }

    fun moveWidgetToNewPage(
        appWidgetId: Int,
        atStart: Boolean,
        targetCellX: Int,
        targetCellY: Int,
    ) {
        val currentCount = uiState.value.settings.homePageCount
        if (currentCount >= LauncherPreferencesRepository.MAX_HOME_PAGES) {
            snackbarMessage.value = getApplication<Application>().getString(
                R.string.home_pages_limit_message,
                LauncherPreferencesRepository.MAX_HOME_PAGES,
            )
            return
        }
        currentHomePage.value = if (atStart) 0 else currentCount
        viewModelScope.launch {
            val moved = prefsRepository.insertHomePageAndMoveWidget(
                appWidgetId = appWidgetId,
                atStart = atStart,
                targetCellX = targetCellX,
                targetCellY = targetCellY,
            )
            if (!moved) {
                snackbarMessage.value = getApplication<Application>().getString(
                    R.string.home_pages_limit_message,
                    LauncherPreferencesRepository.MAX_HOME_PAGES,
                )
            }
        }
    }

    fun moveWidgetToPage(appWidgetId: Int, targetPage: Int) {
        moveWidgetTarget.value = null
        viewModelScope.launch {
            val moved = prefsRepository.moveWidgetToPage(appWidgetId, targetPage)
            if (!moved) {
                snackbarMessage.value = getApplication<Application>().getString(R.string.home_page_full_message)
            }
        }
    }

    fun removeWidget(appWidgetId: Int) {
        if (widgetMenuId.value == appWidgetId) {
            widgetMenuId.value = null
        }
        if (resizingWidgetId.value == appWidgetId) {
            resizingWidgetId.value = null
        }
        viewModelScope.launch {
            prefsRepository.removePlacedWidget(appWidgetId)
        }
    }

    fun pruneMissingWidgets(validIds: Set<Int>) {
        viewModelScope.launch {
            prefsRepository.pruneMissingWidgets(validIds)
        }
    }

    fun hasContactsPermission(): Boolean = searchRepository.hasContactsPermission()

    fun refreshSearchSourcesIfEnabled() {
        val settings = uiState.value.settings
        if (settings.searchContactsEnabled) {
            if (searchRepository.hasContactsPermission()) {
                reloadContactsCache()
            } else {
                cachedContacts.value = emptyList()
                viewModelScope.launch { prefsRepository.setSearchContactsEnabled(false) }
            }
        }
    }

    fun setSearchShortcutsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setSearchShortcutsEnabled(enabled)
        }
    }

    fun setSearchContactsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !searchRepository.hasContactsPermission()) {
                snackbarMessage.value = getApplication<Application>().getString(R.string.settings_search_contacts_denied)
                prefsRepository.setSearchContactsEnabled(false)
                return@launch
            }
            prefsRepository.setSearchContactsEnabled(enabled)
        }
    }

    fun onContactsPermissionDenied() {
        snackbarMessage.value = getApplication<Application>().getString(R.string.settings_search_contacts_denied)
        viewModelScope.launch {
            prefsRepository.setSearchContactsEnabled(false)
        }
    }

    fun setSearchFilesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setSearchFilesEnabled(enabled)
        }
    }

    fun addSearchFileFolder(treeUri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModelScope.launch {
            val added = prefsRepository.addSearchFileTreeUri(treeUri.toString(), autoEnable = true)
            if (added) {
                snackbarMessage.value = getApplication<Application>().getString(R.string.search_folders_added_message)
            }
        }
    }

    fun removeSearchFileFolder(uriString: String) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModelScope.launch {
            prefsRepository.removeSearchFileTreeUri(uriString)
        }
    }

    fun setSearchThirdPartyAppEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setSearchThirdPartyAppEnabled(packageName, enabled)
        }
    }

    fun openContact(contact: ContactSearchResult) {
        val ok = searchRepository.openContact(contact)
        if (ok) {
            if (destination.value == LauncherDestination.DRAWER) {
                returnHome()
            }
        } else {
            snackbarMessage.value = getApplication<Application>().getString(R.string.search_open_contact_error)
        }
    }

    fun dialContact(phoneNumber: String) {
        val ok = searchRepository.dialContact(phoneNumber)
        if (ok) {
            if (destination.value == LauncherDestination.DRAWER) {
                returnHome()
            }
        } else {
            snackbarMessage.value = getApplication<Application>().getString(R.string.search_open_contact_error)
        }
    }

    fun openFile(file: FileSearchResult) {
        val ok = searchRepository.openFile(file)
        if (ok) {
            if (destination.value == LauncherDestination.DRAWER) {
                returnHome()
            }
        } else {
            snackbarMessage.value = getApplication<Application>().getString(R.string.search_open_file_error)
        }
    }

    fun launchThirdPartySearch(provider: SearchProviderApp, query: String) {
        val ok = searchRepository.launchThirdPartySearch(provider, query)
        if (ok) {
            if (destination.value == LauncherDestination.DRAWER) {
                returnHome()
            }
        } else {
            snackbarMessage.value = getApplication<Application>().getString(R.string.search_third_party_error)
        }
    }

    fun showMessage(message: String) {
        snackbarMessage.value = message
    }

    fun consumeSnackbar() {
        snackbarMessage.value = null
    }

    private fun buildDrawerTabPages(
        apps: ImmutableList<AppItem>,
        settings: LauncherSettings,
    ): ImmutableList<DrawerTabPage> {
        val result = ArrayList<DrawerTabPage>(settings.drawerTabs.size + 1)
        if (!settings.isAllTabHidden) {
            result.add(
                DrawerTabPage(
                    id = DrawerTab.ALL_TAB_ID,
                    name = "",
                    isAllTab = true,
                    apps = apps,
                )
            )
        }
        for (tab in settings.drawerTabs) {
            if (tab.isHidden) continue
            val keySet = tab.appKeys.toSet()
            result.add(
                DrawerTabPage(
                    id = tab.id,
                    name = tab.name,
                    isAllTab = false,
                    apps = if (keySet.isEmpty()) {
                        persistentListOf()
                    } else {
                        apps.filter { it.componentKey in keySet }.toImmutableList()
                    },
                )
            )
        }
        if (result.isEmpty()) {
            result.add(
                DrawerTabPage(
                    id = DrawerTab.ALL_TAB_ID,
                    name = "",
                    isAllTab = true,
                    apps = apps,
                )
            )
        }
        return result.toImmutableList()
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

    fun exportSettingsToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val result = runCatching {
                val json = prefsRepository.exportSettingsJson()
                val out = app.contentResolver.openOutputStream(uri, "wt")
                    ?: app.contentResolver.openOutputStream(uri)
                    ?: error("Cannot open output stream")
                out.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    stream.flush()
                }
            }
            snackbarMessage.value = app.getString(
                if (result.isSuccess) R.string.backup_export_success
                else R.string.backup_export_error
            )
        }
    }

    fun importSettingsFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val parsed = runCatching {
                val input = app.contentResolver.openInputStream(uri)
                    ?: error("Cannot open input stream")
                val bytes = input.use { stream ->
                    val buffer = ByteArray(8 * 1024)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > LauncherSettingsBackup.MAX_BACKUP_BYTES) {
                            error("Backup file too large")
                        }
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
                LauncherSettingsBackup.deserialize(bytes.toString(Charsets.UTF_8)).getOrThrow()
            }

            val restored = parsed.getOrNull()
            if (restored == null) {
                snackbarMessage.value = app.getString(R.string.backup_import_invalid)
                return@launch
            }

            prefsRepository.restoreSettings(restored)
            currentHomePage.value = currentHomePage.value.coerceIn(0, (restored.homePageCount - 1).coerceAtLeast(0))
            snackbarMessage.value = app.getString(R.string.backup_import_success)
        }
    }
}
