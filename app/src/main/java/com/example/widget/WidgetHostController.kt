package com.example.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.LruCache
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.model.WidgetCatalogItem
import com.example.model.WidgetResizeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import kotlin.math.hypot

class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPressListener: ((Rect) -> Unit)? = null
    var onLongPressArmedListener: (() -> Unit)? = null
    var onTouchDownListener: (() -> Unit)? = null
    var onDragStartListener: ((Rect, androidx.compose.ui.geometry.Offset) -> Unit)? = null
    var onDragMoveListener: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null
    var onDragEndListener: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null
    var onDragCancelListener: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var longPressRawX = 0f
    private var longPressRawY = 0f
    private var longPressTriggered = false
    private var dragStarted = false
    private var lastWidthDp = -1
    private var lastHeightDp = -1

    private fun currentBoundsInWindow(): Rect {
        val loc = IntArray(2)
        getLocationInWindow(loc)
        return Rect(
            left = loc[0].toFloat(),
            top = loc[1].toFloat(),
            right = (loc[0] + width).toFloat(),
            bottom = (loc[1] + height).toFloat(),
        )
    }

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        dragStarted = false
        longPressRawX = lastRawX
        longPressRawY = lastRawY
        parent?.requestDisallowInterceptTouchEvent(true)
        onLongPressArmedListener?.invoke()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressTriggered = false
                dragStarted = false
                downX = ev.x
                downY = ev.y
                lastRawX = ev.rawX
                lastRawY = ev.rawY
                onTouchDownListener?.invoke()
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, longPressTimeout)
            }

            MotionEvent.ACTION_MOVE -> {
                lastRawX = ev.rawX
                lastRawY = ev.rawY
                if (longPressTriggered) {
                    handlePostLongPressMove(ev)
                    return true
                }
                if (hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble()) > touchSlop) {
                    handler.removeCallbacks(longPressRunnable)
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (longPressTriggered) {
                    handlePostLongPressUp(ev)
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (longPressTriggered) {
                    handlePostLongPressCancel()
                    return true
                }
            }
        }
        return longPressTriggered
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only reached when no clickable child inside the widget consumed ACTION_DOWN.
                // Return true so we still receive MOVE/UP/CANCEL for long-press menu and drag.
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                if (longPressTriggered) {
                    handlePostLongPressMove(event)
                    return true
                }
                if (hypot((event.x - downX).toDouble(), (event.y - downY).toDouble()) > touchSlop) {
                    handler.removeCallbacks(longPressRunnable)
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (longPressTriggered) {
                    handlePostLongPressUp(event)
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (longPressTriggered) {
                    handlePostLongPressCancel()
                    return true
                }
            }
        }
        return if (longPressTriggered) true else super.onTouchEvent(event)
    }

    private fun eventInWindow(ev: MotionEvent): androidx.compose.ui.geometry.Offset {
        val loc = IntArray(2)
        getLocationInWindow(loc)
        return androidx.compose.ui.geometry.Offset(loc[0] + ev.x, loc[1] + ev.y)
    }

    private fun handlePostLongPressMove(ev: MotionEvent) {
        val windowPos = eventInWindow(ev)
        if (!dragStarted) {
            if (hypot((ev.rawX - longPressRawX).toDouble(), (ev.rawY - longPressRawY).toDouble()) > touchSlop) {
                dragStarted = true
                onDragStartListener?.invoke(currentBoundsInWindow(), windowPos)
            }
        } else {
            onDragMoveListener?.invoke(windowPos)
        }
    }

    private fun handlePostLongPressUp(ev: MotionEvent) {
        val wasDragging = dragStarted
        longPressTriggered = false
        dragStarted = false
        parent?.requestDisallowInterceptTouchEvent(false)
        if (wasDragging) {
            onDragEndListener?.invoke(eventInWindow(ev))
        } else {
            onLongPressListener?.invoke(currentBoundsInWindow())
        }
    }

    private fun handlePostLongPressCancel() {
        val wasArmedOrDragging = longPressTriggered || dragStarted
        longPressTriggered = false
        dragStarted = false
        parent?.requestDisallowInterceptTouchEvent(false)
        if (wasArmedOrDragging) {
            onDragCancelListener?.invoke()
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        if (longPressTriggered || dragStarted) {
            handlePostLongPressCancel()
        }
        super.onDetachedFromWindow()
    }

    fun updateSizeDpIfNeeded(widthDp: Int, heightDp: Int) {
        if (widthDp <= 0 || heightDp <= 0) return
        if (widthDp == lastWidthDp && heightDp == lastHeightDp) return
        lastWidthDp = widthDp
        lastHeightDp = heightDp
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
            } else {
                @Suppress("DEPRECATION")
                updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
            }
        }
    }
}

class LauncherAppWidgetHost(
    context: Context,
    hostId: Int,
) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = LauncherAppWidgetHostView(context)
}

class WidgetHostController(private val context: Context) {

    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetHost: LauncherAppWidgetHost = LauncherAppWidgetHost(context, APPWIDGET_HOST_ID)

    private val viewCache = HashMap<Int, LauncherAppWidgetHostView>()
    private val previewCache = LruCache<String, ImageBitmap>(48)

    private var isListening = false

    fun startListening() {
        if (isListening) return
        if (runCatching { appWidgetHost.startListening() }.isSuccess) {
            isListening = true
        }
    }

    fun stopListening() {
        if (!isListening) return
        runCatching { appWidgetHost.stopListening() }
        isListening = false
    }

    fun allocateAppWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    fun getProviderInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { appWidgetManager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    fun tryBindWidget(appWidgetId: Int, info: AppWidgetProviderInfo): Boolean {
        return runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null)
        }.getOrDefault(false)
    }

    fun createBindIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        }
    }

    fun needsInitialConfiguration(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val optional = (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
            if (optional) return false
        }
        return true
    }

    fun isReconfigurable(appWidgetId: Int): Boolean {
        val info = getProviderInfo(appWidgetId) ?: return false
        if (info.configure == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
        } else {
            true
        }
    }

    fun getOrCreateView(appWidgetId: Int): LauncherAppWidgetHostView? {
        val info = getProviderInfo(appWidgetId) ?: return null
        val cached = viewCache[appWidgetId]
        if (cached != null) return cached

        val created = runCatching {
            appWidgetHost.createView(context, appWidgetId, info) as LauncherAppWidgetHostView
        }.getOrNull() ?: return null
        created.setAppWidget(appWidgetId, info)
        created.setPadding(0, 0, 0, 0)
        created.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        viewCache[appWidgetId] = created
        return created
    }

    fun attachViewToContainer(appWidgetId: Int, container: ViewGroup): LauncherAppWidgetHostView? {
        val view = getOrCreateView(appWidgetId) ?: return null
        if (view.parent !== container) {
            (view.parent as? ViewGroup)?.removeView(view)
            container.removeAllViews()
            container.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        return view
    }

    fun deleteWidget(appWidgetId: Int) {
        viewCache.remove(appWidgetId)?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        runCatching { appWidgetHost.deleteAppWidgetId(appWidgetId) }
    }

    fun clearViewCache() {
        viewCache.values.forEach { view ->
            view.onLongPressListener = null
            view.onLongPressArmedListener = null
            view.onTouchDownListener = null
            view.onDragStartListener = null
            view.onDragMoveListener = null
            view.onDragEndListener = null
            view.onDragCancelListener = null
            (view.parent as? ViewGroup)?.removeView(view)
        }
        viewCache.clear()
    }

    fun validWidgetIds(candidateIds: List<Int>): Set<Int> {
        return candidateIds.filter { id -> getProviderInfo(id) != null }.toSet()
    }

    fun computeDefaultSpan(info: AppWidgetProviderInfo, gridColumns: Int): Pair<Int, Int> {
        val cols = gridColumns.coerceIn(1, 5)
        val spanX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0) {
            info.targetCellWidth.coerceIn(1, cols)
        } else {
            ((info.minWidth + 30) / 70).coerceIn(1, cols)
        }
        val spanY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
            info.targetCellHeight.coerceIn(1, MAX_WIDGET_ROWS)
        } else {
            ((info.minHeight + 30) / 70).coerceIn(1, MAX_WIDGET_ROWS)
        }
        return spanX to spanY
    }

    fun getResizeSpec(appWidgetId: Int, gridColumns: Int): WidgetResizeSpec {
        val cols = gridColumns.coerceIn(1, 5)
        val info = getProviderInfo(appWidgetId) ?: return WidgetResizeSpec(
            canResizeHorizontal = false,
            canResizeVertical = false,
            minSpanX = 1,
            maxSpanX = cols,
            minSpanY = 1,
            maxSpanY = MAX_WIDGET_ROWS,
        )
        val mode = info.resizeMode
        val canH = (mode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
        val canV = (mode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0

        val minSpanX = if (info.minResizeWidth > 0) {
            ((info.minResizeWidth + 30) / 70).coerceIn(1, cols)
        } else {
            1
        }
        val maxSpanX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeWidth > 0) {
            ((info.maxResizeWidth + 30) / 70).coerceIn(minSpanX, cols)
        } else {
            cols
        }

        val minSpanY = if (info.minResizeHeight > 0) {
            ((info.minResizeHeight + 30) / 70).coerceIn(1, MAX_WIDGET_ROWS)
        } else {
            1
        }
        val maxSpanY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeHeight > 0) {
            ((info.maxResizeHeight + 30) / 70).coerceIn(minSpanY, MAX_WIDGET_ROWS)
        } else {
            MAX_WIDGET_ROWS
        }

        return WidgetResizeSpec(
            canResizeHorizontal = canH,
            canResizeVertical = canV,
            minSpanX = minSpanX,
            maxSpanX = maxSpanX,
            minSpanY = minSpanY,
            maxSpanY = maxSpanY,
        )
    }

    fun getCachedPreview(providerComponent: String): ImageBitmap? =
        previewCache.get(providerComponent)

    suspend fun loadWidgetPreview(
        providerComponent: String,
        info: AppWidgetProviderInfo,
    ): ImageBitmap? {
        previewCache.get(providerComponent)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bitmap = runCatching {
                val drawable = info.loadPreviewImage(context, DisplayMetrics.DENSITY_HIGH)
                    ?: info.loadIcon(context, DisplayMetrics.DENSITY_XXHIGH)
                    ?: return@runCatching null
                val rawW = drawable.intrinsicWidth.takeIf { it > 0 } ?: 240
                val rawH = drawable.intrinsicHeight.takeIf { it > 0 } ?: 160
                val maxSide = 320f
                val scale = minOf(1f, maxSide / maxOf(rawW, rawH).toFloat())
                val targetW = (rawW * scale).toInt().coerceAtLeast(48)
                val targetH = (rawH * scale).toInt().coerceAtLeast(48)
                drawable.toBitmap(width = targetW, height = targetH, config = Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            }.getOrNull()
            if (bitmap != null) {
                previewCache.put(providerComponent, bitmap)
            }
            bitmap
        }
    }

    fun hasWidgetsForPackage(packageName: String): Boolean {
        if (packageName == context.packageName) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appWidgetManager.getInstalledProvidersForPackage(packageName, null).isNotEmpty()
            } else {
                appWidgetManager.installedProviders.any { it.provider?.packageName == packageName }
            }
        }.getOrDefault(false)
    }

    suspend fun loadWidgetCatalog(gridColumns: Int): List<WidgetCatalogItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val providers = runCatching { appWidgetManager.installedProviders }.getOrNull().orEmpty()
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }

        val appLabelCache = HashMap<String, String>()
        val appIconCache = HashMap<String, ImageBitmap?>()
        val items = ArrayList<WidgetCatalogItem>(providers.size)

        for (info in providers) {
            val provider = info.provider ?: continue
            val pkg = provider.packageName ?: continue
            if (pkg == context.packageName) continue

            val appLabel = appLabelCache.getOrPut(pkg) {
                runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                }.getOrNull() ?: pkg.substringAfterLast('.')
            }

            val appIcon = appIconCache.getOrPut(pkg) {
                runCatching {
                    pm.getApplicationIcon(pkg)
                        .toBitmap(width = 96, height = 96, config = Bitmap.Config.ARGB_8888)
                        .asImageBitmap()
                }.getOrNull()
            }

            val widgetLabel = runCatching { info.loadLabel(pm)?.trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: appLabel

            val (spanX, spanY) = computeDefaultSpan(info, gridColumns)

            items.add(
                WidgetCatalogItem(
                    providerInfo = info,
                    providerComponent = provider.flattenToShortString(),
                    packageName = pkg,
                    appLabel = appLabel,
                    widgetLabel = widgetLabel,
                    spanX = spanX,
                    spanY = spanY,
                    appIcon = appIcon,
                )
            )
        }

        items.sortedWith { a, b ->
            val appCmp = collator.compare(a.appLabel, b.appLabel)
            if (appCmp != 0) appCmp else collator.compare(a.widgetLabel, b.widgetLabel)
        }
    }

    companion object {
        const val APPWIDGET_HOST_ID = 0x5146
        const val MAX_WIDGET_ROWS = 6
    }
}
