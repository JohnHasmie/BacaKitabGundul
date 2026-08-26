package com.classicbookreader.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Tegas Glass" design tokens — the single source of truth for color.
 * Reference: design/mockups (canvas "Classic Book Reader", decision v2.4).
 */
object AppColors {
    // Light palette
    val Cream = Color(0xFFF4F3EE) // app background
    val Ink = Color(0xFF1C1B16) // primary text
    val InkSecondary = Color(0xFF6F6B5E)
    val InkTertiary = Color(0xFF9B9789)
    val InkFaint = Color(0xFFB4B0A2)
    val Green = Color(0xFF1E5C44) // primary actions
    val GreenDeep = Color(0xFF14432F)
    val Amber = Color(0xFFF0A63A) // highlights, streak
    val AmberInk = Color(0xFFC4780A)
    val AmberDeep = Color(0xFF7A5A10)
    val Danger = Color(0xFFB0483A)

    // Glass surfaces (light)
    val GlassSurface = Color(0xB8FFFFFF) // ~72% white
    val GlassSurfaceStrong = Color(0xF0FFFFFF) // ~94% white (dialogs, flashcards)
    val Hairline = Color(0x1A1C1B16) // ~10% ink
    val InkWash = Color(0x0F1C1B16) // ~6% ink, subtle fills

    // Dark palette (initial pass; refined in Phase 5 with the dark theme work)
    val DarkBackground = Color(0xFF15140F)
    val DarkInk = Color(0xFFEFEDE6)
    val DarkInkSecondary = Color(0xFFA8A494)
    val DarkGlassSurface = Color(0xB8232219)
    val DarkHairline = Color(0x22EFEDE6)
    val GreenBright = Color(0xFF3E8A6B) // primary on dark
}
