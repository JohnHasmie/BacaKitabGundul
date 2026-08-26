package com.classicbookreader.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Radius

/**
 * Frosted glass surface — the base container of the "Tegas Glass" system:
 * translucent white, hairline border, soft diffuse shadow.
 *
 * True backdrop blur (haze) will be layered in via RenderEffect on
 * API 31+ during Phase 1; the translucent surface is the graceful
 * fallback everywhere else.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Radius.lg,
    strong: Boolean = false,
    elevation: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val surface = if (strong) AppTheme.glass.surfaceStrong else AppTheme.glass.surface
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .clip(shape)
            .background(surface)
            .border(BorderStroke(1.dp, AppTheme.glass.hairline), shape),
    ) {
        content()
    }
}
