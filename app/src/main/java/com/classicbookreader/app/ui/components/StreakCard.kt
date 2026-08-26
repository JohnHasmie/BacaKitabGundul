package com.classicbookreader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Radius
import com.classicbookreader.app.ui.theme.Spacing

enum class DayStatus { Completed, Today, Upcoming }

data class StreakDay(val initial: String, val status: DayStatus)

/**
 * The "istiqomah" streak card: title + day-count chip, a one-line nudge,
 * and a 7-segment week strip with the current day ringed in green.
 */
@Composable
fun StreakCard(
    title: String,
    daysLabel: String,
    subtitle: String,
    week: List<StreakDay>,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    GlassCard(modifier = modifier, cornerRadius = Radius.lg) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                leadingIcon?.invoke()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(text = title, style = MaterialTheme.typography.titleMedium)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AppTheme.glass.amber.copy(alpha = 0.22f))
                                .padding(horizontal = Spacing.md, vertical = 2.dp),
                        ) {
                            Text(
                                text = daysLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = AppTheme.glass.amberDeep,
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.glass.inkSecondary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
                week.forEach { day ->
                    DaySegment(day = day, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DaySegment(day: StreakDay, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.sm - 2.dp)
    val base = modifier.height(36.dp).clip(shape)
    when (day.status) {
        DayStatus.Completed -> Box(
            modifier = base.background(AppTheme.glass.amber),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.initial,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        DayStatus.Today -> Box(
            modifier = base
                .background(AppTheme.glass.surfaceStrong)
                .border(2.dp, MaterialTheme.colorScheme.primary, shape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = day.initial,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        DayStatus.Upcoming -> Box(
            modifier = base.background(AppTheme.glass.inkWash),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.initial,
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.glass.inkFaint,
            )
        }
    }
}
