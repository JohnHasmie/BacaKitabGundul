package com.classicbookreader.app.core.selection

import kotlin.math.max
import kotlin.math.min

/** A point in view (gesture) coordinates, in pixels. */
data class SelectionPoint(val x: Float, val y: Float)

/** A rectangle in page-normalized coordinates — every value is a 0..1 fraction of the page. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A crop rectangle in bitmap pixels. */
data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** Where a ContentScale.Fit image lands inside its container. */
data class FitPlacement(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val drawnWidth: Float,
    val drawnHeight: Float,
)

/**
 * Geometry for the circle-to-analyze gesture: gesture points arrive in view
 * coordinates, the page bitmap is letterboxed with ContentScale.Fit, and the
 * selection must come out as page fractions so it survives any render width.
 */
object SelectionGeometry {

    /** Selections smaller than this fraction of the page (per axis) are grown to it. */
    const val MIN_SELECTION_FRACTION = 0.04f

    /** Context margin sent to the AI, as a fraction of the page width (plan §Fase 2). */
    const val CONTEXT_MARGIN_OF_WIDTH = 0.15f

    fun fitPlacement(
        viewWidth: Float,
        viewHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
    ): FitPlacement {
        val scale = min(viewWidth / imageWidth, viewHeight / imageHeight)
        val drawnWidth = imageWidth * scale
        val drawnHeight = imageHeight * scale
        return FitPlacement(
            scale = scale,
            offsetX = (viewWidth - drawnWidth) / 2f,
            offsetY = (viewHeight - drawnHeight) / 2f,
            drawnWidth = drawnWidth,
            drawnHeight = drawnHeight,
        )
    }

    /**
     * Bounding box of the drawn stroke, mapped to page fractions and clamped
     * to the page. Returns null when the stroke never touched the page or is
     * degenerate (fewer than 3 points). Tiny circles around a single short
     * word are grown to [MIN_SELECTION_FRACTION] per axis, centered.
     */
    fun normalizeSelection(
        points: List<SelectionPoint>,
        viewWidth: Float,
        viewHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
    ): NormalizedRect? {
        if (points.size < 3) return null
        val fit = fitPlacement(viewWidth, viewHeight, imageWidth, imageHeight)
        if (fit.drawnWidth <= 0f || fit.drawnHeight <= 0f) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (point in points) {
            minX = min(minX, point.x)
            minY = min(minY, point.y)
            maxX = max(maxX, point.x)
            maxY = max(maxY, point.y)
        }

        var left = ((minX - fit.offsetX) / fit.drawnWidth).coerceIn(0f, 1f)
        var right = ((maxX - fit.offsetX) / fit.drawnWidth).coerceIn(0f, 1f)
        var top = ((minY - fit.offsetY) / fit.drawnHeight).coerceIn(0f, 1f)
        var bottom = ((maxY - fit.offsetY) / fit.drawnHeight).coerceIn(0f, 1f)
        if (right - left <= 0f && bottom - top <= 0f) return null

        if (right - left < MIN_SELECTION_FRACTION) {
            val center = (left + right) / 2f
            left = (center - MIN_SELECTION_FRACTION / 2f).coerceAtLeast(0f)
            right = (left + MIN_SELECTION_FRACTION).coerceAtMost(1f)
        }
        if (bottom - top < MIN_SELECTION_FRACTION) {
            val center = (top + bottom) / 2f
            top = (center - MIN_SELECTION_FRACTION / 2f).coerceAtLeast(0f)
            bottom = (top + MIN_SELECTION_FRACTION).coerceAtMost(1f)
        }
        return NormalizedRect(left, top, right, bottom)
    }

    /**
     * Expands the selection with the AI context margin: [marginOfWidth] of the
     * page width on each side, converted to the equivalent height fraction via
     * [pageAspectRatio] (= pageHeight / pageWidth) so the margin is visually
     * uniform. Clamped to the page.
     */
    fun expandWithContext(
        rect: NormalizedRect,
        marginOfWidth: Float = CONTEXT_MARGIN_OF_WIDTH,
        pageAspectRatio: Float,
    ): NormalizedRect {
        val marginY = if (pageAspectRatio > 0f) marginOfWidth / pageAspectRatio else marginOfWidth
        return NormalizedRect(
            left = (rect.left - marginOfWidth).coerceAtLeast(0f),
            top = (rect.top - marginY).coerceAtLeast(0f),
            right = (rect.right + marginOfWidth).coerceAtMost(1f),
            bottom = (rect.bottom + marginY).coerceAtMost(1f),
        )
    }

    /** Converts page fractions to a pixel crop rect within a rendered bitmap, always ≥1px. */
    fun toPixelRect(rect: NormalizedRect, bitmapWidth: Int, bitmapHeight: Int): PixelRect {
        val left = (rect.left * bitmapWidth).toInt().coerceIn(0, bitmapWidth - 1)
        val top = (rect.top * bitmapHeight).toInt().coerceIn(0, bitmapHeight - 1)
        val width = (rect.width * bitmapWidth).toInt().coerceIn(1, bitmapWidth - left)
        val height = (rect.height * bitmapHeight).toInt().coerceIn(1, bitmapHeight - top)
        return PixelRect(left, top, width, height)
    }

    /**
     * The selection's bbox in pixels relative to the *cropped context image*,
     * as the /v1/analyze contract expects.
     */
    fun selectionWithinCrop(
        selection: NormalizedRect,
        crop: NormalizedRect,
        cropWidthPx: Int,
        cropHeightPx: Int,
    ): PixelRect {
        val relLeft = if (crop.width > 0f) (selection.left - crop.left) / crop.width else 0f
        val relTop = if (crop.height > 0f) (selection.top - crop.top) / crop.height else 0f
        val relRight = if (crop.width > 0f) (selection.right - crop.left) / crop.width else 1f
        val relBottom = if (crop.height > 0f) (selection.bottom - crop.top) / crop.height else 1f
        return toPixelRect(
            NormalizedRect(
                left = relLeft.coerceIn(0f, 1f),
                top = relTop.coerceIn(0f, 1f),
                right = relRight.coerceIn(0f, 1f),
                bottom = relBottom.coerceIn(0f, 1f),
            ),
            cropWidthPx,
            cropHeightPx,
        )
    }
}
