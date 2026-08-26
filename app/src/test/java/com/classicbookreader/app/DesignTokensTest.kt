package com.classicbookreader.app

import androidx.compose.ui.graphics.toArgb
import com.classicbookreader.app.ui.theme.AppColors
import com.classicbookreader.app.ui.theme.Radius
import com.classicbookreader.app.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the "Tegas Glass" design tokens against accidental drift —
 * the values here mirror design/mockups (decision v2.4).
 */
class DesignTokensTest {

    @Test
    fun corePaletteMatchesMockups() {
        assertEquals(0xFFF4F3EE.toInt(), AppColors.Cream.toArgb())
        assertEquals(0xFF1C1B16.toInt(), AppColors.Ink.toArgb())
        assertEquals(0xFF1E5C44.toInt(), AppColors.Green.toArgb())
        assertEquals(0xFFF0A63A.toInt(), AppColors.Amber.toArgb())
    }

    @Test
    fun spacingScaleIsMonotonic() {
        val scale = listOf(Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg, Spacing.xl, Spacing.xxl)
        assertEquals(scale, scale.sortedBy { it.value })
    }

    @Test
    fun radiusScaleIsMonotonic() {
        val scale = listOf(Radius.sm, Radius.md, Radius.lg, Radius.xl)
        assertEquals(scale, scale.sortedBy { it.value })
    }
}
