package com.classicbookreader.app.feature.reader

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.classicbookreader.app.R
import com.classicbookreader.app.core.selection.SelectionPoint
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Radius
import com.classicbookreader.app.ui.theme.Spacing

/**
 * The circle-to-analyze layer (mockup screens 5-6): a pulsing edge glow says
 * AI mode is live, the finger draws an amber lasso, and the finished stroke
 * is handed back in view coordinates together with the overlay size.
 */
@Composable
fun CircleSelectionOverlay(
    onStrokeFinished: (points: List<SelectionPoint>, viewWidth: Float, viewHeight: Float) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val glow by rememberInfiniteTransition(label = "aiGlow").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "aiGlowAlpha",
    )

    val primary = MaterialTheme.colorScheme.primary
    val amber = AppTheme.glass.amber
    val scrim = MaterialTheme.colorScheme.scrim

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start -> stroke = listOf(start) },
                    onDrag = { change, _ -> stroke = stroke + change.position },
                    onDragCancel = { stroke = emptyList() },
                    onDragEnd = {
                        val points = stroke.map { SelectionPoint(it.x, it.y) }
                        stroke = emptyList()
                        onStrokeFinished(
                            points,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Dim the page slightly so the glow and lasso read clearly.
            drawRect(scrim.copy(alpha = 0.18f))

            // Galaxy-AI style breathing edge glow.
            val edge = 26.dp.toPx()
            val glowBrush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = glow), amber.copy(alpha = glow)),
            )
            drawRoundRect(
                brush = glowBrush,
                cornerRadius = CornerRadius(edge, edge),
                style = Stroke(width = 6.dp.toPx()),
            )

            if (stroke.size > 1) {
                val path = Path().apply {
                    moveTo(stroke.first().x, stroke.first().y)
                    stroke.drop(1).forEach { point -> lineTo(point.x, point.y) }
                }
                drawPath(
                    path = path,
                    color = amber,
                    style = Stroke(
                        width = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        GlassCard(
            cornerRadius = Radius.pill,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = Spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.reader_ai_hint),
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(onClick = onExit) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.reader_ai_exit),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
