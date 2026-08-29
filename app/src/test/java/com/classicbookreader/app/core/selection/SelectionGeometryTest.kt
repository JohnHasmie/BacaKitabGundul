package com.classicbookreader.app.core.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionGeometryTest {

    // A 1000×2000 view showing a 500×800 page: fit scale = min(2.0, 2.5) = 2.0,
    // drawn 1000×1600, letterboxed vertically with 200px top/bottom bands.
    private val viewW = 1000f
    private val viewH = 2000f
    private val imageW = 500f
    private val imageH = 800f

    private fun stroke(vararg points: Pair<Float, Float>) =
        points.map { (x, y) -> SelectionPoint(x, y) }

    @Test
    fun fitPlacementCentersLetterboxedImage() {
        val fit = SelectionGeometry.fitPlacement(viewW, viewH, imageW, imageH)
        assertEquals(2f, fit.scale, 1e-4f)
        assertEquals(0f, fit.offsetX, 1e-4f)
        assertEquals(200f, fit.offsetY, 1e-4f)
        assertEquals(1000f, fit.drawnWidth, 1e-4f)
        assertEquals(1600f, fit.drawnHeight, 1e-4f)
    }

    @Test
    fun normalizeSelectionMapsViewPointsToPageFractions() {
        // Circle covering view x 250..750, y 600..1000 → page x 0.25..0.75, y 0.25..0.5.
        val rect = SelectionGeometry.normalizeSelection(
            stroke(250f to 600f, 750f to 600f, 750f to 1000f, 250f to 1000f),
            viewW, viewH, imageW, imageH,
        )!!
        assertEquals(0.25f, rect.left, 1e-3f)
        assertEquals(0.75f, rect.right, 1e-3f)
        assertEquals(0.25f, rect.top, 1e-3f)
        assertEquals(0.5f, rect.bottom, 1e-3f)
    }

    @Test
    fun normalizeSelectionClampsStrokesInTheLetterbox() {
        // Stroke reaching into the top letterbox band clamps to the page edge.
        val rect = SelectionGeometry.normalizeSelection(
            stroke(100f to 50f, 500f to 50f, 500f to 600f, 100f to 600f),
            viewW, viewH, imageW, imageH,
        )!!
        assertEquals(0f, rect.top, 1e-3f)
        assertEquals(0.25f, rect.bottom, 1e-3f)
    }

    @Test
    fun normalizeSelectionRejectsDegenerateStrokes() {
        assertNull(
            SelectionGeometry.normalizeSelection(
                stroke(10f to 10f, 12f to 10f),
                viewW, viewH, imageW, imageH,
            ),
        )
    }

    @Test
    fun tinySelectionGrowsToMinimumSize() {
        val rect = SelectionGeometry.normalizeSelection(
            stroke(500f to 1000f, 504f to 1000f, 504f to 1004f, 500f to 1004f),
            viewW, viewH, imageW, imageH,
        )!!
        assertEquals(SelectionGeometry.MIN_SELECTION_FRACTION, rect.width, 1e-3f)
        assertEquals(SelectionGeometry.MIN_SELECTION_FRACTION, rect.height, 1e-3f)
    }

    @Test
    fun expandWithContextAddsUniformMarginAndClamps() {
        val rect = NormalizedRect(left = 0.4f, top = 0.05f, right = 0.6f, bottom = 0.1f)
        // aspect 1.6 (page 500×800) → y margin = 0.15 / 1.6 = 0.09375.
        val expanded = SelectionGeometry.expandWithContext(rect, pageAspectRatio = 1.6f)
        assertEquals(0.25f, expanded.left, 1e-4f)
        assertEquals(0.75f, expanded.right, 1e-4f)
        assertEquals(0f, expanded.top, 1e-4f) // clamped at the page edge
        assertEquals(0.1f + 0.09375f, expanded.bottom, 1e-4f)
    }

    @Test
    fun toPixelRectStaysInsideBitmapAndNeverCollapses() {
        val rect = NormalizedRect(0.99f, 0.99f, 1f, 1f)
        val pixels = SelectionGeometry.toPixelRect(rect, bitmapWidth = 100, bitmapHeight = 100)
        assertEquals(99, pixels.left)
        assertEquals(99, pixels.top)
        assertEquals(1, pixels.width)
        assertEquals(1, pixels.height)
    }

    @Test
    fun selectionWithinCropIsRelativeToTheCrop() {
        val selection = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f)
        val crop = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)
        val bbox = SelectionGeometry.selectionWithinCrop(selection, crop, 600, 600)
        // Selection occupies the middle third of the crop.
        assertEquals(200, bbox.left)
        assertEquals(200, bbox.top)
        assertEquals(200, bbox.width)
        assertEquals(200, bbox.height)
    }

    @Test
    fun cacheKeyIsStableUnderTinyWobbleButChangesAcrossPages() {
        val a = NormalizedRect(0.4000f, 0.4f, 0.6f, 0.6f)
        val b = NormalizedRect(0.4004f, 0.4f, 0.6f, 0.6f) // same 1/200 grid cell
        val c = NormalizedRect(0.45f, 0.4f, 0.6f, 0.6f)
        assertEquals(
            AnalysisCacheKey.forSelection(1L, 3, a),
            AnalysisCacheKey.forSelection(1L, 3, b),
        )
        assertNotEquals(
            AnalysisCacheKey.forSelection(1L, 3, a),
            AnalysisCacheKey.forSelection(1L, 4, a),
        )
        assertNotEquals(
            AnalysisCacheKey.forSelection(1L, 3, a),
            AnalysisCacheKey.forSelection(1L, 3, c),
        )
        assertTrue(AnalysisCacheKey.forSelection(1L, 3, a).matches(Regex("[0-9a-f]{64}")))
    }
}
