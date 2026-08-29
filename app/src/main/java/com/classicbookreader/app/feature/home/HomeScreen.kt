package com.classicbookreader.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classicbookreader.app.R
import com.classicbookreader.app.feature.home.HomeViewModel.ContinueReadingUi
import com.classicbookreader.app.ui.components.CoverImage
import com.classicbookreader.app.ui.components.DayStatus
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.components.ProgressRing
import com.classicbookreader.app.ui.components.StreakCard
import com.classicbookreader.app.ui.components.StreakDay
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Radius
import com.classicbookreader.app.ui.theme.Sizes
import com.classicbookreader.app.ui.theme.Spacing

@Composable
fun HomeScreen(
    onContinueReading: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val continueReading by viewModel.continueReading.collectAsStateWithLifecycle()

    // Streak stays static until the murajaah loop lands in Phase 5.
    val dayInitials = stringArrayResource(R.array.streak_day_initials)
    val sampleStatuses = listOf(
        DayStatus.Completed, DayStatus.Completed, DayStatus.Completed,
        DayStatus.Today,
        DayStatus.Upcoming, DayStatus.Upcoming, DayStatus.Upcoming,
    )
    val sampleWeek = dayInitials.zip(sampleStatuses, ::StreakDay)

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
                text = stringResource(R.string.home_greeting_name_placeholder),
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        StreakCard(
            title = stringResource(R.string.streak_title),
            daysLabel = stringResource(R.string.streak_days_format, 3),
            subtitle = stringResource(R.string.streak_subtitle),
            week = sampleWeek,
        )

        continueReading?.let { book ->
            ContinueReadingCard(book = book, onClick = { onContinueReading(book.bookId) })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(Spacing.lg)) {
                    Text(
                        text = stringResource(R.string.home_saved_words),
                        style = MaterialTheme.typography.titleSmall,
                        color = AppTheme.glass.inkSecondary,
                    )
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }
            GlassCard(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(Spacing.lg)) {
                    Text(
                        text = stringResource(R.string.home_murajaah),
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

        Spacer(modifier = Modifier.height(Sizes.dockClearance))
    }
}

@Composable
private fun ContinueReadingCard(book: ContinueReadingUi, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            CoverImage(
                path = book.coverPath,
                contentDescription = book.title,
                modifier = Modifier
                    .size(width = 52.dp, height = 68.dp)
                    .clip(RoundedCornerShape(Radius.sm)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.home_continue_reading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.library_page_of, book.lastReadPage + 1, book.pageCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.glass.inkTertiary,
                )
            }
            ProgressRing(progress = book.progress, size = 34.dp)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
