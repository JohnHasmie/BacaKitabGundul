package com.classicbookreader.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.classicbookreader.app.R
import com.classicbookreader.app.ui.components.DayStatus
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.components.StreakCard
import com.classicbookreader.app.ui.components.StreakDay
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Spacing

/**
 * Phase 0: static home screen exercising the shared components against
 * the mockup (screen 2). Real data arrives with the reader in Phase 1.
 */
@Composable
fun HomeScreen() {
    val sampleWeek = listOf(
        StreakDay("A", DayStatus.Completed),
        StreakDay("S", DayStatus.Completed),
        StreakDay("S", DayStatus.Completed),
        StreakDay("R", DayStatus.Today),
        StreakDay("K", DayStatus.Upcoming),
        StreakDay("J", DayStatus.Upcoming),
        StreakDay("S", DayStatus.Upcoming),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column {
            Text(
                text = stringResource(R.string.home_greeting_prefix),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.glass.inkTertiary,
            )
            Text(
                text = "Yahya",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        StreakCard(
            title = stringResource(R.string.streak_title),
            daysLabel = "3 " + stringResource(R.string.streak_days_suffix),
            subtitle = stringResource(R.string.streak_subtitle),
            week = sampleWeek,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(Spacing.lg)) {
                    Text(
                        text = "Kata tersimpan",
                        style = MaterialTheme.typography.titleSmall,
                        color = AppTheme.glass.inkSecondary,
                    )
                    Text(
                        text = "24",
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }
            GlassCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(Spacing.lg)) {
                    Text(
                        text = "Muroja'ah",
                        style = MaterialTheme.typography.titleSmall,
                        color = AppTheme.glass.inkSecondary,
                    )
                    Text(
                        text = stringResource(R.string.placeholder_coming_soon),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.glass.inkTertiary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp)) // room for the floating dock
    }
}
