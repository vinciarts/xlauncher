package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.model.AppItem
import com.example.model.AppShortcutItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class AppRepository(private val context: Context) {

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val shortcutIconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val pm: PackageManager get() = context.packageManager
    private val torchEnabled = AtomicBoolean(false)
    private var torchCallback: CameraManager.TorchCallback? = null
    private var launcherAppsCallback: LauncherApps.Callback? = null

    init {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cm != null) {
            val cb = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    torchEnabled.set(enabled)
                }
            }
            if (runCatching { cm.registerTorchCallback(cb, null) }.isSuccess) {
                torchCallback = cb
            }
        }
    }

    fun registerLauncherCallbacks(
        onShortcutsChanged: (String) -> Unit,
        onPackagesChanged: (String?) -> Unit,
    ) {
        if (launcherAppsCallback != null) return
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return
        val cb = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                if (user == Process.myUserHandle()) onPackagesChanged(packageName)
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                if (user == Process.myUserHandle()) onPackagesChanged(packageName)
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                if (user == Process.myUserHandle()) onPackagesChanged(packageName)
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                if (user == Process.myUserHandle()) onPackagesChanged(null)
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                if (user == Process.myUserHandle()) onPackagesChanged(null)
            }

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: UserHandle,
            ) {
                if (user == Process.myUserHandle()) {
                    clearShortcutIconCacheForPackage(packageName)
                    onShortcutsChanged(packageName)
                }
            }
        }
        if (runCatching { launcherApps.registerCallback(cb, Handler(Looper.getMainLooper())) }.isSuccess) {
            launcherAppsCallback = cb
        }
    }

    fun release() {
        val cb = torchCallback
        if (cb != null) {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            runCatching { cm?.unregisterTorchCallback(cb) }
            torchCallback = null
        }
        val laCb = launcherAppsCallback
        if (laCb != null) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            runCatching { launcherApps?.unregisterCallback(laCb) }
            launcherAppsCallback = null
        }
    }

    suspend fun loadInstalledApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val myPackageName = context.packageName
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }

        val seenKeys = HashSet<String>()
        val items = ArrayList<AppItem>(resolvedActivities.size)

        for (resolveInfo in resolvedActivities) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            val cls = activityInfo.name ?: continue

            val key = "$pkg/$cls"
            if (!seenKeys.add(key)) continue

            val label = runCatching { resolveInfo.loadLabel(pm)?.toString()?.trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: pkg.substringAfterLast('.')

            val icon = iconCache[key] ?: runCatching {
                resolveInfo.loadIcon(pm)
                    ?.toBitmap(width = 144, height = 144, config = Bitmap.Config.ARGB_8888)
                    ?.asImageBitmap()
            }.getOrNull()?.also { bmp ->
                iconCache[key] = bmp
            }

            val isSystemApp = (activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            items.add(
                AppItem(
                    packageName = pkg,
                    activityName = cls,
                    label = label,
                    icon = icon,
                    isSystemApp = isSystemApp,
                    isSelfLauncher = (pkg == myPackageName),
                )
            )
        }

        items.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun clearIconCacheForPackage(packageName: String) {
        iconCache.keys.removeAll { it.startsWith("$packageName/") }
        clearShortcutIconCacheForPackage(packageName)
    }

    private fun clearShortcutIconCacheForPackage(packageName: String) {
        val encodedPkgPrefix = "${AppShortcutItem.SHORTCUT_PREFIX}${Uri.encode(packageName)}|"
        shortcutIconCache.keys.removeAll { it.startsWith(encodedPkgPrefix) }
    }

    fun hasShortcutHostPermission(): Boolean {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return false
        return runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)
    }

    /**
     * Live single-package shortcut lookup used when long-pressing an app icon (matches AOSP Launcher3).
     * Never hits cross-package Binder limits and always returns up-to-date manifest + dynamic shortcuts.
     */
    suspend fun loadShortcutsForApp(app: AppItem): List<AppShortcutItem> = withContext(Dispatchers.IO) {
        if (app.isSelfLauncher) return@withContext emptyList()
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return@withContext emptyList()
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) {
            return@withContext emptyList()
        }

        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(app.packageName)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
        }

        val allPkgInfos = runCatching {
            launcherApps.getShortcuts(query, Process.myUserHandle())
        }.getOrNull().orEmpty().filter { it.isEnabled }

        if (allPkgInfos.isEmpty()) return@withContext emptyList()

        val targetComponent = ComponentName(app.packageName, app.activityName)
        val activityMatched = allPkgInfos.filter { it.activity == null || it.activity == targetComponent }
        val effectiveInfos = activityMatched.ifEmpty { allPkgInfos }

        val densityDpi = context.resources.displayMetrics.densityDpi
        val seenIds = HashSet<String>()
        val manifestIds = HashSet<String>()
        val items = ArrayList<AppShortcutItem>(effectiveInfos.size)

        for (info in effectiveInfos) {
            val pkg = info.`package` ?: continue
            val id = info.id ?: continue
            if (!seenIds.add(id)) continue

            val label = info.shortLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: info.longLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: continue

            if (info.isDeclaredInManifest) {
                manifestIds.add(id)
            }

            val targetKey = AppShortcutItem.encodeTargetKey(pkg, id)
            val icon = shortcutIconCache[targetKey] ?: runCatching {
                launcherApps.getShortcutIconDrawable(info, densityDpi)
                    ?.toBitmap(width = 112, height = 112, config = Bitmap.Config.ARGB_8888)
                    ?.asImageBitmap()
            }.getOrNull()?.also { bmp ->
                shortcutIconCache[targetKey] = bmp
            } ?: app.icon

            items.add(
                AppShortcutItem(
                    packageName = pkg,
                    shortcutId = id,
                    label = label,
                    appLabel = app.label,
                    icon = icon,
                    rank = info.rank,
                    isMenuShortcut = true,
                )
            )
        }

        items.sortedWith(
            compareBy<AppShortcutItem> { if (it.shortcutId in manifestIds) 0 else 1 }
                .thenBy { it.rank }
        )
    }

    /**
     * Queries shortcuts per package so a single giant Binder call cannot fail with TransactionTooLargeException
     * or an OEM package restriction, keeping both gesture picker and menu caches reliable.
     */
    suspend fun loadAppShortcuts(apps: List<AppItem>): List<AppShortcutItem> = withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return@withContext emptyList()
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) {
            return@withContext emptyList()
        }

        val appByPkg = HashMap<String, AppItem>(apps.size)
        for (app in apps) {
            if (!app.isSelfLauncher) {
                appByPkg.putIfAbsent(app.packageName, app)
            }
        }

        val user = Process.myUserHandle()
        val densityDpi = context.resources.displayMetrics.densityDpi
        val flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED

        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }

        val seenKeys = HashSet<String>()
        val results = ArrayList<AppShortcutItem>()

        for ((pkg, hostApp) in appByPkg) {
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(pkg)
                setQueryFlags(flags)
            }
            val shortcutInfos = runCatching {
                launcherApps.getShortcuts(query, user)
            }.getOrNull().orEmpty()

            for (info in shortcutInfos) {
                if (!info.isEnabled) continue
                val id = info.id ?: continue
                val label = info.shortLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: info.longLabel?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: continue

                val targetKey = AppShortcutItem.encodeTargetKey(pkg, id)
                if (!seenKeys.add(targetKey)) continue

                val icon = shortcutIconCache[targetKey] ?: runCatching {
                    launcherApps.getShortcutIconDrawable(info, densityDpi)
                        ?.toBitmap(width = 112, height = 112, config = Bitmap.Config.ARGB_8888)
                        ?.asImageBitmap()
                }.getOrNull()?.also { bmp ->
                    shortcutIconCache[targetKey] = bmp
                } ?: hostApp.icon

                results.add(
                    AppShortcutItem(
                        packageName = pkg,
                        shortcutId = id,
                        label = label,
                        appLabel = hostApp.label,
                        icon = icon,
                        rank = info.rank,
                        isMenuShortcut = info.isDeclaredInManifest || info.isDynamic,
                    )
                )
            }
        }

        results.sortedWith { a, b ->
            val appCmp = collator.compare(a.appLabel, b.appLabel)
            if (appCmp != 0) appCmp else collator.compare(a.label, b.label)
        }
    }

    fun launchShortcut(packageName: String, shortcutId: String): Boolean {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return false
        return runCatching {
            launcherApps.startShortcut(
                packageName,
                shortcutId,
                null,
                null,
                Process.myUserHandle(),
            )
            true
        }.getOrDefault(false)
    }

    fun launchApp(app: AppItem): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            runCatching {
                val fallback = pm.getLaunchIntentForPackage(app.packageName) ?: return false
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                true
            }.getOrDefault(false)
        }
    }

    fun openAppInfo(packageName: String) {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun requestUninstall(packageName: String) {
        val launched = runCatching {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.fromParts("package", packageName, null)
                putExtra(Intent.EXTRA_USER, Process.myUserHandle())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!launched) {
            openAppInfo(packageName)
        }
    }

    fun openDefaultHomeSettings() {
        val homeIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            context.startActivity(homeIntent)
            true
        }.getOrDefault(false)

        if (!launched) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }

    fun expandNotificationsPanel(): Boolean = invokeStatusBarMethod("expandNotificationsPanel", "expand")

    fun expandQuickSettingsPanel(): Boolean = invokeStatusBarMethod("expandSettingsPanel", "expandNotificationsPanel")

    @Suppress("PrivateApi", "WrongConstant")
    private fun invokeStatusBarMethod(primary: String, fallback: String): Boolean {
        return runCatching {
            val statusBarService = context.getSystemService("statusbar") ?: return false
            val statusBarClass = Class.forName("android.app.StatusBarManager")
            val method = runCatching { statusBarClass.getMethod(primary) }
                .getOrElse { statusBarClass.getMethod(fallback) }
            method.invoke(statusBarService)
            true
        }.getOrDefault(false)
    }

    fun toggleFlashlight(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            val cameraIds = cm.cameraIdList
            val backWithFlash = cameraIds.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            }
            val anyWithFlash = backWithFlash ?: cameraIds.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false

            val nextState = !torchEnabled.get()
            cm.setTorchMode(anyWithFlash, nextState)
            torchEnabled.set(nextState)
            true
        }.getOrDefault(false)
    }

    fun openVolumePanel(): Boolean {
        return runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            audio.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            true
        }.getOrDefault(false)
    }

    fun toggleVibrateMode(): Boolean {
        return runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val nextMode = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                AudioManager.RINGER_MODE_VIBRATE
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
            audio.ringerMode = nextMode
            audio.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            true
        }.getOrDefault(false)
    }

    fun openVoiceAssist(): Boolean =
        startSystemIntent(Intent(Intent.ACTION_VOICE_COMMAND)) ||
            startSystemIntent(Intent(Intent.ACTION_ASSIST))

    fun openCamera(): Boolean =
        startSystemIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    fun openDialer(): Boolean =
        startSystemIntent(Intent(Intent.ACTION_DIAL))

    fun openSystemSettings(): Boolean =
        startSystemIntent(Intent(Settings.ACTION_SETTINGS))

    fun openWifiPanel(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (startSystemIntent(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))) {
                return true
            }
        }
        return startSystemIntent(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    fun openBluetoothSettings(): Boolean =
        startSystemIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun openWallpaperPicker(): Boolean =
        startSystemIntent(Intent(Intent.ACTION_SET_WALLPAPER))

    private fun startSystemIntent(intent: Intent): Boolean {
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }
}
