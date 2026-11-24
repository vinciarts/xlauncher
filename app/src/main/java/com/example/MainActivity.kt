package com.example

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LauncherPreferencesRepository
import com.example.model.AppActionOrigin
import com.example.model.LauncherDestination
import com.example.model.PlacedWidget
import com.example.model.WidgetCatalogItem
import com.example.ui.components.AppContextMenuHost
import com.example.ui.components.AppGestureConfigSheet
import com.example.ui.components.AssignAppTabsDialog
import com.example.ui.components.MoveToPageDialog
import com.example.ui.components.WidgetContextMenuHost
import com.example.ui.screens.AppDrawerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WidgetPickerScreen
import com.example.ui.theme.QingfengLauncherTheme
import com.example.viewmodel.LauncherViewModel
import com.example.widget.WidgetHostController

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var widgetHostController: WidgetHostController

    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null
    private var pendingWidgetPage: Int = 0
    private var pendingWidgetIsReconfigure: Boolean = false
    private var isActivityStarted: Boolean = false

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val widgetId = pendingWidgetId
        val info = pendingWidgetInfo
        val page = pendingWidgetPage
        if (result.resultCode == RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && info != null) {
            continueAfterWidgetBound(widgetId, info, page)
        } else {
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                widgetHostController.deleteWidget(widgetId)
            }
            clearPendingWidgetState()
        }
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart
            viewModel.refreshInstalledApps(changedPackage = pkg)
            pruneOrphanedWidgets()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetHostController = WidgetHostController(this)
        widgetHostController.startListening()
        enableEdgeToEdge()

        setContent {
            QingfengLauncherTheme {
                LauncherRoot(
                    viewModel = viewModel,
                    widgetHostController = widgetHostController,
                    launchedAsHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true,
                    onPickWidget = ::beginAddWidget,
                    onReconfigureWidget = ::beginReconfigureWidget,
                    onDeleteWidget = ::deletePlacedWidget,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isActivityStarted = true
        widgetHostController.startListening()
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, pkgFilter)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultLauncherStatus()
        viewModel.refreshSearchSourcesIfEnabled()
        pruneOrphanedWidgets()
        hideSoftKeyboardIfOnHome()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            viewModel.refreshDefaultLauncherStatus()
        }
    }

    override fun onStop() {
        isActivityStarted = false
        super.onStop()
        widgetHostController.stopListening()
        runCatching { unregisterReceiver(packageChangeReceiver) }
    }

    override fun onDestroy() {
        widgetHostController.clearViewCache()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            val broughtToFront = (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0
            viewModel.handleHomeButton(alreadyInForeground = isActivityStarted && !broughtToFront)
            hideSoftKeyboardIfOnHome()
        }
    }

    private fun hideSoftKeyboardIfOnHome() {
        if (viewModel.uiState.value.destination == LauncherDestination.HOME) {
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
    }

    @Deprecated("Required for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            val widgetId = pendingWidgetId
            val info = pendingWidgetInfo
            val page = pendingWidgetPage
            val isReconfigure = pendingWidgetIsReconfigure
            clearPendingWidgetState()

            if (isReconfigure) {
                return
            }

            if (resultCode == RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && info != null) {
                finalizeWidgetPlacement(widgetId, info, page)
            } else if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                widgetHostController.deleteWidget(widgetId)
            }
        }
    }

    private fun beginAddWidget(item: WidgetCatalogItem) {
        viewModel.returnHome()
        val targetPage = viewModel.uiState.value.currentHomePage
        val appWidgetId = runCatching { widgetHostController.allocateAppWidgetId() }
            .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            viewModel.showMessage(getString(R.string.widget_add_error))
            return
        }

        val info = item.providerInfo
        pendingWidgetId = appWidgetId
        pendingWidgetInfo = info
        pendingWidgetPage = targetPage
        pendingWidgetIsReconfigure = false

        if (widgetHostController.tryBindWidget(appWidgetId, info)) {
            continueAfterWidgetBound(appWidgetId, info, targetPage)
        } else {
            val bindIntent = widgetHostController.createBindIntent(appWidgetId, info)
            val launched = runCatching {
                bindWidgetLauncher.launch(bindIntent)
                true
            }.getOrDefault(false)
            if (!launched) {
                widgetHostController.deleteWidget(appWidgetId)
                clearPendingWidgetState()
                viewModel.showMessage(getString(R.string.widget_add_error))
            }
        }
    }

    private fun continueAfterWidgetBound(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        targetPage: Int,
    ) {
        if (widgetHostController.needsInitialConfiguration(info)) {
            val launched = runCatching {
                widgetHostController.appWidgetHost.startAppWidgetConfigureActivityForResult(
                    this,
                    appWidgetId,
                    0,
                    REQUEST_CONFIGURE_WIDGET,
                    null,
                )
                true
            }.getOrDefault(false)
            if (!launched) {
                clearPendingWidgetState()
                finalizeWidgetPlacement(appWidgetId, info, targetPage)
            }
        } else {
            clearPendingWidgetState()
            finalizeWidgetPlacement(appWidgetId, info, targetPage)
        }
    }

    private fun beginReconfigureWidget(widget: PlacedWidget) {
        viewModel.dismissWidgetMenu()
        val info = widgetHostController.getProviderInfo(widget.appWidgetId) ?: return
        pendingWidgetId = widget.appWidgetId
        pendingWidgetInfo = info
        pendingWidgetPage = widget.pageIndex
        pendingWidgetIsReconfigure = true
        val launched = runCatching {
            widgetHostController.appWidgetHost.startAppWidgetConfigureActivityForResult(
                this,
                widget.appWidgetId,
                0,
                REQUEST_CONFIGURE_WIDGET,
                null,
            )
            true
        }.getOrDefault(false)
        if (!launched) {
            clearPendingWidgetState()
            viewModel.showMessage(getString(R.string.system_action_unavailable))
        }
    }

    private fun finalizeWidgetPlacement(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        targetPage: Int,
    ) {
        val cols = viewModel.uiState.value.settings.homeGridColumns
        val rows = viewModel.uiState.value.settings.homeGridRows
        val (spanX, spanY) = widgetHostController.computeDefaultSpan(info, cols, rows)
        viewModel.addPlacedWidget(
            PlacedWidget(
                appWidgetId = appWidgetId,
                pageIndex = targetPage,
                spanX = spanX,
                spanY = spanY,
                providerComponent = info.provider.flattenToShortString(),
            )
        )
    }

    private fun deletePlacedWidget(widget: PlacedWidget) {
        widgetHostController.deleteWidget(widget.appWidgetId)
        viewModel.removeWidget(widget.appWidgetId)
    }

    private fun pruneOrphanedWidgets() {
        val placed = viewModel.uiState.value.settings.placedWidgets
        if (placed.isEmpty()) return
        val validIds = widgetHostController.validWidgetIds(placed.map { it.appWidgetId })
        if (validIds.size < placed.size) {
            placed.filterNot { it.appWidgetId in validIds }.forEach { orphan ->
                widgetHostController.deleteWidget(orphan.appWidgetId)
            }
            viewModel.pruneMissingWidgets(validIds)
        }
    }

    private fun clearPendingWidgetState() {
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingWidgetInfo = null
        pendingWidgetPage = 0
        pendingWidgetIsReconfigure = false
    }

    companion object {
        private const val REQUEST_CONFIGURE_WIDGET = 1002
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LauncherRoot(
    viewModel: LauncherViewModel,
    widgetHostController: WidgetHostController,
    launchedAsHome: Boolean,
    onPickWidget: (WidgetCatalogItem) -> Unit,
    onReconfigureWidget: (PlacedWidget) -> Unit,
    onDeleteWidget: (PlacedWidget) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Consume Back on the bare Home screen when acting as a Home launcher so Android never
    // finishes and immediately recreates MainActivity (overlays/drawers register their own
    // BackHandlers after this one and therefore still take priority).
    BackHandler(enabled = uiState.isDefaultLauncher || launchedAsHome) {}

    LaunchedEffect(uiState.destination) {
        if (uiState.destination == LauncherDestination.HOME) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    val isWidgetPickerOpen = uiState.destination == LauncherDestination.WIDGET_PICKER
    var widgetCatalog by remember { mutableStateOf<List<WidgetCatalogItem>>(emptyList()) }
    var isLoadingWidgets by remember { mutableStateOf(false) }

    LaunchedEffect(isWidgetPickerOpen, uiState.settings.homeGridColumns, uiState.settings.homeGridRows) {
        if (isWidgetPickerOpen) {
            isLoadingWidgets = true
            widgetCatalog = widgetHostController.loadWidgetCatalog(
                uiState.settings.homeGridColumns,
                uiState.settings.homeGridRows,
            )
            isLoadingWidgets = false
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    val isDrawerOpen = uiState.destination == LauncherDestination.DRAWER
    val motionScheme = MaterialTheme.motionScheme
    val drawerProgressState = animateFloatAsState(
        targetValue = if (isDrawerOpen) 1f else 0f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "drawer_progress",
    )
    // Only flips twice (on open start and close end) so layout never re-runs during GPU animation
    val isDrawerPlaced by remember {
        derivedStateOf { drawerProgressState.value > 0.001f }
    }

    val appMenuHost = remember(
        uiState.appMenuTarget,
        uiState.settings.dockKeys,
        uiState.settings.appGestures,
        uiState.appsByKey,
        uiState.shortcutsByTargetKey,
        uiState.hasShortcutHostPermission,
    ) {
        AppContextMenuHost(
            activeTarget = uiState.appMenuTarget,
            dockKeys = uiState.settings.dockKeys,
            appGestures = uiState.settings.appGestures,
            appsByKey = uiState.appsByKey,
            shortcutsByTargetKey = uiState.shortcutsByTargetKey,
            hasShortcutHostPermission = uiState.hasShortcutHostPermission,
            hasWidgetsForPackage = widgetHostController::hasWidgetsForPackage,
            onOpen = viewModel::openAppMenu,
            onDismiss = viewModel::dismissAppMenu,
            onLaunchShortcut = viewModel::launchShortcut,
            onOpenDefaultHomeSettings = {
                viewModel.dismissAppMenu()
                viewModel.openDefaultHomeSettings()
            },
            onOpenLauncherSettings = viewModel::openSettings,
            onOpenAppWidgets = { pkg -> viewModel.openWidgetPicker(pkg) },
            onOpenGestureConfig = viewModel::openAppGestureConfig,
            onPinToCurrentHomePage = viewModel::pinToCurrentHomePage,
            onRemoveFromHomePage = viewModel::removeFromHomePage,
            onOpenMovePage = viewModel::openMovePageDialog,
            onOpenDrawerTabs = viewModel::openAppTabsDialog,
            onTogglePinDock = viewModel::togglePinDock,
            onAppInfo = viewModel::openAppInfo,
            onUninstall = viewModel::uninstallApp,
        )
    }

    val widgetMenuHost = remember(uiState.widgetMenuId) {
        WidgetContextMenuHost(
            activeWidgetId = uiState.widgetMenuId,
            onOpen = viewModel::openWidgetMenu,
            onDismiss = viewModel::dismissWidgetMenu,
            onReconfigure = onReconfigureWidget,
            onResize = viewModel::openResizeWidget,
            onMovePage = viewModel::openMoveWidgetDialog,
            onRemove = onDeleteWidget,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 0: HomeScreen stays persistently mounted underneath (zero tear-down/recomposition on Drawer open/close)
        HomeScreen(
            isLoading = uiState.isLoading,
            homePageApps = uiState.homePageApps,
            homePageWidgets = uiState.homePageWidgets,
            dockApps = uiState.dockApps,
            appGestures = uiState.settings.appGestures,
            homeGestures = uiState.settings.homeGestures,
            homeGridColumns = uiState.settings.homeGridColumns,
            homeGridRows = uiState.settings.homeGridRows,
            homeLoopPaging = uiState.settings.homeLoopPaging,
            maxHomePages = LauncherPreferencesRepository.MAX_HOME_PAGES,
            maxDockItems = LauncherPreferencesRepository.MAX_DOCK_ITEMS,
            currentHomePage = uiState.currentHomePage,
            homePageCommand = uiState.homePageCommand,
            isHomeEditMode = uiState.isHomeEditMode,
            resizingWidgetId = uiState.resizingWidgetId,
            widgetHostController = widgetHostController,
            appMenuHost = appMenuHost,
            widgetMenuHost = widgetMenuHost,
            homeEmptyMenuAnchor = uiState.homeEmptyMenuAnchor,
            onHomePageChanged = viewModel::onHomePageChanged,
            onAppClick = viewModel::launchApp,
            onAppSwipe = viewModel::onAppSwiped,
            onResizeWidget = viewModel::resizeWidget,
            onSelectEditWidget = viewModel::selectEditWidget,
            onDismissResizeWidget = viewModel::dismissResizeWidget,
            onExitHomeEditMode = viewModel::exitHomeEditMode,
            onMoveHomeAppToCell = viewModel::moveHomeAppToCell,
            onMoveWidgetToCell = viewModel::moveWidgetToCell,
            onMoveHomeAppToNewPage = viewModel::moveHomeAppToNewPage,
            onMoveWidgetToNewPage = viewModel::moveWidgetToNewPage,
            onReorderDockApp = viewModel::reorderDockApp,
            onMoveDockAppToHomeCell = viewModel::moveDockAppToHomeCell,
            onMoveDockAppToNewPage = viewModel::moveDockAppToNewPage,
            onMoveHomeAppToDock = viewModel::moveHomeAppToDock,
            onRemoveHomeApp = viewModel::removeHomeAppByKey,
            onRemoveDockApp = viewModel::removeDockAppByKey,
            onRemoveWidget = onDeleteWidget,
            onHomeSwipe = viewModel::onHomeSwiped,
            onEmptyLongPress = viewModel::openHomeEmptyMenu,
            onEmptyMenuAddWidget = { viewModel.openWidgetPicker() },
            onEmptyMenuOpenWallpaper = viewModel::openWallpaperPicker,
            onEmptyMenuEditLayout = viewModel::enterHomeEditMode,
            onEmptyMenuOpenSettings = viewModel::openSettings,
            onEmptyMenuAddPageAtStart = viewModel::insertHomePageAtStart,
            onEmptyMenuAddPageAtEnd = viewModel::appendHomePageAtEnd,
            onEmptyMenuMovePageLeft = { viewModel.moveCurrentHomePage(-1) },
            onEmptyMenuMovePageRight = { viewModel.moveCurrentHomePage(1) },
            onEmptyMenuDeleteCurrentPage = {
                viewModel.deleteCurrentHomePage { removedIds ->
                    removedIds.forEach { widgetId -> widgetHostController.deleteWidget(widgetId) }
                }
            },
            onDismissEmptyMenu = viewModel::dismissHomeEmptyMenu,
        )

        // Layer 1: AppDrawer stays warm in memory once apps load; unplaced (0 draw / 0 touch cost) when closed,
        // and animated purely on the GPU via graphicsLayer when opening/closing.
        if (!uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        if (isDrawerPlaced) {
                            layout(placeable.width, placeable.height) {
                                placeable.place(0, 0)
                            }
                        } else {
                            layout(0, 0) {}
                        }
                    }
                    .graphicsLayer {
                        val p = drawerProgressState.value
                        alpha = p
                        translationY = (1f - p) * size.height * 0.22f
                    },
            ) {
                AppDrawerScreen(
                    isActive = isDrawerOpen,
                    allApps = uiState.allApps,
                    drawerTabs = uiState.settings.drawerTabs,
                    drawerTabPages = uiState.drawerTabPages,
                    filteredDrawerApps = uiState.filteredDrawerApps,
                    hasAnyExtraSearchEnabled = uiState.settings.hasAnyExtraSearchEnabled,
                    matchedShortcuts = uiState.matchedShortcuts,
                    matchedContacts = uiState.matchedContacts,
                    matchedFiles = uiState.matchedFiles,
                    activeSearchProviders = uiState.activeSearchProviders,
                    drawerSearchQuery = uiState.drawerSearchQuery,
                    autoFocusDrawerSearch = uiState.autoFocusDrawerSearch,
                    drawerColumns = uiState.settings.drawerColumns,
                    onClose = { viewModel.returnHome() },
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onAutoFocusConsumed = viewModel::clearAutoFocusSearch,
                    onAppClick = viewModel::launchApp,
                    onLaunchShortcut = viewModel::launchShortcut,
                    onOpenContact = viewModel::openContact,
                    onDialContact = viewModel::dialContact,
                    onOpenFile = viewModel::openFile,
                    onLaunchThirdPartySearch = viewModel::launchThirdPartySearch,
                    appMenuHost = appMenuHost,
                    onCreateTab = viewModel::createDrawerTab,
                    onRenameTab = viewModel::renameDrawerTab,
                    onSetTabApps = viewModel::setDrawerTabApps,
                    onMoveTab = viewModel::moveDrawerTab,
                    onSetTabHidden = viewModel::setDrawerTabHidden,
                    onDeleteTab = viewModel::deleteDrawerTab,
                )
            }
        }

        // Layer 2: Settings overlay without destroying HomeScreen underneath
        AnimatedVisibility(
            visible = uiState.destination == LauncherDestination.SETTINGS,
            enter = fadeIn(motionScheme.fastEffectsSpec()),
            exit = fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {},
            ) {
                SettingsScreen(
                    uiState = uiState,
                    onBack = { viewModel.returnHome() },
                    onOpenDefaultHomeSettings = viewModel::openDefaultHomeSettings,
                    onSetHomeLoopPaging = viewModel::setHomeLoopPaging,
                    onSetHomeGridColumns = viewModel::setHomeGridColumns,
                    onSetHomeGridRows = viewModel::setHomeGridRows,
                    onSetHomeGesture = viewModel::setHomeGesture,
                    onSetHomeButtonAction = viewModel::setHomeButtonAction,
                    onSetDrawerColumns = viewModel::setDrawerColumns,
                    onCreateDrawerTab = viewModel::createDrawerTab,
                    onRenameDrawerTab = viewModel::renameDrawerTab,
                    onSetDrawerTabApps = viewModel::setDrawerTabApps,
                    onMoveDrawerTab = viewModel::moveDrawerTab,
                    onSetAllTabHidden = viewModel::setAllTabHidden,
                    onSetDrawerTabHidden = viewModel::setDrawerTabHidden,
                    onDeleteDrawerTab = viewModel::deleteDrawerTab,
                    onSetSearchShortcutsEnabled = viewModel::setSearchShortcutsEnabled,
                    onSetSearchContactsEnabled = viewModel::setSearchContactsEnabled,
                    onContactsPermissionDenied = viewModel::onContactsPermissionDenied,
                    onSetSearchFilesEnabled = viewModel::setSearchFilesEnabled,
                    onAddSearchFileFolder = viewModel::addSearchFileFolder,
                    onRemoveSearchFileFolder = viewModel::removeSearchFileFolder,
                    onSetSearchThirdPartyAppEnabled = viewModel::setSearchThirdPartyAppEnabled,
                    onExportSettings = viewModel::exportSettingsToUri,
                    onImportSettings = viewModel::importSettingsFromUri,
                )
            }
        }

        // Layer 3: Full-screen Material 3 Widget Picker (unmodified native LazyColumn scrolling)
        AnimatedVisibility(
            visible = isWidgetPickerOpen,
            enter = fadeIn(motionScheme.fastEffectsSpec()),
            exit = fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {},
            ) {
                WidgetPickerScreen(
                    catalog = widgetCatalog,
                    isLoading = isLoadingWidgets,
                    widgetHostController = widgetHostController,
                    onSelectWidget = onPickWidget,
                    onBack = { viewModel.returnHome() },
                    filterPackageName = uiState.widgetPickerPackageFilter,
                    onClearPackageFilter = viewModel::clearWidgetPickerPackageFilter,
                )
            }
        }

        // Move widget to another Home page dialog
        val moveWidget = uiState.moveWidgetTarget
        if (moveWidget != null) {
            MoveToPageDialog(
                currentPageCount = uiState.homePageApps.size,
                maxPages = LauncherPreferencesRepository.MAX_HOME_PAGES,
                onSelectPage = { targetPage ->
                    viewModel.moveWidgetToPage(moveWidget.appWidgetId, targetPage)
                },
                onDismiss = viewModel::dismissMoveWidgetDialog,
            )
        }

        // Assign an app to custom App Drawer tabs
        val appTabsTarget = uiState.appTabsTarget
        if (appTabsTarget != null) {
            AssignAppTabsDialog(
                app = appTabsTarget,
                customTabs = uiState.settings.drawerTabs,
                onConfirm = { selectedTabIds ->
                    viewModel.setAppDrawerTabs(appTabsTarget.componentKey, selectedTabIds)
                },
                onCreateTabWithApp = { tabName ->
                    viewModel.createDrawerTab(tabName, listOf(appTabsTarget.componentKey))
                },
                onDismiss = viewModel::dismissAppTabsDialog,
            )
        }

        // Global per-app swipe-gesture configuration sheet (supports Apps + dynamic App Shortcuts)
        val gestureApp = uiState.gestureConfigApp
        if (gestureApp != null) {
            AppGestureConfigSheet(
                app = gestureApp,
                currentBindings = uiState.settings.appGestures[gestureApp.componentKey].orEmpty(),
                allApps = uiState.allApps,
                appsByKey = uiState.appsByKey,
                allShortcuts = uiState.allShortcuts,
                shortcutsByTargetKey = uiState.shortcutsByTargetKey,
                hasShortcutHostPermission = uiState.hasShortcutHostPermission,
                onOpenDefaultHomeSettings = viewModel::openDefaultHomeSettings,
                onSetGesture = { dir, targetKey ->
                    viewModel.setAppGesture(gestureApp.componentKey, dir, targetKey)
                },
                onDismiss = viewModel::dismissAppGestureConfig,
            )
        }

        // Move app to another Home page dialog
        val moveTarget = uiState.movePageTarget
        if (moveTarget != null && moveTarget.origin == AppActionOrigin.HOME) {
            MoveToPageDialog(
                currentPageCount = uiState.homePageApps.size,
                maxPages = LauncherPreferencesRepository.MAX_HOME_PAGES,
                onSelectPage = { targetPage -> viewModel.confirmMoveToPage(moveTarget, targetPage) },
                onDismiss = viewModel::dismissMovePageDialog,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp),
        )
    }
}
