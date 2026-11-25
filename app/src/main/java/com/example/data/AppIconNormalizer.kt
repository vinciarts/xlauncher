package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.model.IconShape
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Normalizes both AdaptiveIconDrawable and legacy Drawables into a uniform [IconShape]
 * so all launcher icons share consistent silhouette and visual weight.
 */
object AppIconNormalizer {

    private const val LEGACY_SAFE_SCALE = 0.72f
    private const val FULL_BLEED_COVERAGE_THRESHOLD = 0.85f
    private const val FULL_BLEED_BOUNDS_RATIO = 0.88f

    fun render(drawable: Drawable, shape: IconShape, sizePx: Int = 144): ImageBitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            return renderAdaptive(drawable, shape, sizePx).asImageBitmap()
        }
        return renderLegacy(drawable, shape, sizePx).asImageBitmap()
    }

    private fun renderAdaptive(
        drawable: AdaptiveIconDrawable,
        shape: IconShape,
        sizePx: Int,
    ): Bitmap {
        if (shape == IconShape.SYSTEM) {
            // Use the system ROM's native AdaptiveIconDrawable mask directly
            val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val oldBounds = drawable.copyBounds()
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            drawable.bounds = oldBounds
            return output
        }

        val content = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val contentCanvas = Canvas(content)

        // AdaptiveIconDrawable layers are designed on a 108dp canvas with 72dp visible viewport (1.5x).
        val extra = (sizePx * 0.25f).roundToInt()
        val layerBounds = Rect(-extra, -extra, sizePx + extra, sizePx + extra)

        drawable.background?.let { bg ->
            val oldBounds = bg.copyBounds()
            bg.bounds = layerBounds
            bg.draw(contentCanvas)
            bg.bounds = oldBounds
        }
        drawable.foreground?.let { fg ->
            val oldBounds = fg.copyBounds()
            fg.bounds = layerBounds
            fg.draw(contentCanvas)
            fg.bounds = oldBounds
        }

        val masked = maskBitmapWithPath(content, buildMaskPath(shape, sizePx), sizePx)
        content.recycle()
        return masked
    }

    private fun renderLegacy(
        drawable: Drawable,
        shape: IconShape,
        sizePx: Int,
    ): Bitmap {
        val raw = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val rawCanvas = Canvas(raw)
        val oldBounds = drawable.copyBounds()
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(rawCanvas)
        drawable.bounds = oldBounds

        val metrics = analyzeOpacity(raw)
        val content = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val contentCanvas = Canvas(content)
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        if (metrics.isFullBleedSquare) {
            // Legacy icon already fills the square -> draw full bleed and clip to target shape
            contentCanvas.drawBitmap(raw, 0f, 0f, bitmapPaint)
        } else {
            // Irregular or pre-padded legacy icon -> place inside a uniform white plate
            contentCanvas.drawColor(Color.WHITE)

            val contentW = (metrics.bounds.width()).coerceAtLeast(1)
            val contentH = (metrics.bounds.height()).coerceAtLeast(1)
            val maxContentDim = max(contentW, contentH).toFloat()
            val targetDim = sizePx * LEGACY_SAFE_SCALE
            val scale = targetDim / maxContentDim

            val destW = contentW * scale
            val destH = contentH * scale
            val left = (sizePx - destW) / 2f
            val top = (sizePx - destH) / 2f
            val destRect = RectF(left, top, left + destW, top + destH)
            contentCanvas.drawBitmap(raw, metrics.bounds, destRect, bitmapPaint)
        }

        val masked = maskBitmapWithPath(content, buildMaskPath(shape, sizePx), sizePx)
        content.recycle()
        raw.recycle()
        return masked
    }

    private fun maskBitmapWithPath(source: Bitmap, maskPath: Path, sizePx: Int): Bitmap {
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(maskPath, paint)
        return output
    }

    private fun buildMaskPath(shape: IconShape, sizePx: Int): Path {
        val size = sizePx.toFloat()
        return when (shape) {
            IconShape.SYSTEM -> buildSystemMaskPath(sizePx)
            IconShape.CIRCLE -> Path().apply {
                addOval(RectF(0f, 0f, size, size), Path.Direction.CW)
            }
            IconShape.ROUNDED_SQUARE -> Path().apply {
                val r = size * 0.24f
                addRoundRect(RectF(0f, 0f, size, size), r, r, Path.Direction.CW)
            }
            IconShape.SQUIRCLE -> Path().apply {
                // Smooth n=4 superellipse via 4 cubic Beziers
                val half = size / 2f
                val c = half * 0.90f
                moveTo(half, 0f)
                cubicTo(half + c, 0f, size, half - c, size, half)
                cubicTo(size, half + c, half + c, size, half, size)
                cubicTo(half - c, size, 0f, half + c, 0f, half)
                cubicTo(0f, half - c, half - c, 0f, half, 0f)
                close()
            }
        }
    }

    private fun buildSystemMaskPath(sizePx: Int): Path {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val dummy = AdaptiveIconDrawable(null, null)
            dummy.setBounds(0, 0, sizePx, sizePx)
            val systemPath = Path(dummy.iconMask)
            val bounds = RectF()
            systemPath.computeBounds(bounds, true)
            if (bounds.width() > 1f && bounds.height() > 1f) {
                val matrix = Matrix()
                matrix.setRectToRect(bounds, RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), Matrix.ScaleToFit.FILL)
                systemPath.transform(matrix)
                return systemPath
            }
        }
        return buildMaskPath(IconShape.SQUIRCLE, sizePx)
    }

    private data class OpacityMetrics(
        val bounds: Rect,
        val isFullBleedSquare: Boolean,
    )

    private fun analyzeOpacity(bitmap: Bitmap): OpacityMetrics {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        var opaqueCount = 0
        var sampledCount = 0

        val step = 2
        var y = 0
        while (y < h) {
            val rowOffset = y * w
            var x = 0
            while (x < w) {
                sampledCount++
                val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                if (alpha >= 24) {
                    opaqueCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                x += step
            }
            y += step
        }

        if (maxX < minX || maxY < minY) {
            return OpacityMetrics(Rect(0, 0, w, h), isFullBleedSquare = true)
        }

        val bounds = Rect(minX, minY, (maxX + step).coerceAtMost(w), (maxY + step).coerceAtMost(h))
        val coverage = opaqueCount.toFloat() / sampledCount.coerceAtLeast(1)
        val widthRatio = bounds.width().toFloat() / w
        val heightRatio = bounds.height().toFloat() / h
        val isFullBleed = coverage >= FULL_BLEED_COVERAGE_THRESHOLD &&
            widthRatio >= FULL_BLEED_BOUNDS_RATIO &&
            heightRatio >= FULL_BLEED_BOUNDS_RATIO

        return OpacityMetrics(bounds = bounds, isFullBleedSquare = isFullBleed)
    }
}
