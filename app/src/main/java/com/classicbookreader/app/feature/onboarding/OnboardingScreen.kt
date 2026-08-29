package com.classicbookreader.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.classicbookreader.app.R
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.components.PillButton
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.ArabicTextStyles
import com.classicbookreader.app.ui.theme.Spacing

/** One-time welcome screen (mockup 1). */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val finish = { viewModel.completeOnboarding(onFinished) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.xxl),
    ) {
        Text(
            text = stringResource(R.string.onboarding_skip),
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.glass.inkTertiary,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = Spacing.lg)
                .clip(CircleShape)
                .clickable(onClick = finish)
                .padding(Spacing.md),
        )

        Column(modifier = Modifier.padding(top = Spacing.lg)) {
            Text(text = stringResource(R.string.onboarding_headline_1), style = MaterialTheme.typography.displaySmall)
            Text(text = stringResource(R.string.onboarding_headline_2), style = MaterialTheme.typography.displaySmall)
            Text(
                text = stringResource(R.string.onboarding_headline_3),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.onboarding_body),
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.glass.inkSecondary,
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            GlassCard {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = stringResource(R.string.onboarding_sample_arabic),
                        style = ArabicTextStyles.label,
                        modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AppTheme.glass.inkWash),
                )
            }
        }

        PillButton(
            text = stringResource(R.string.onboarding_cta),
            onClick = finish,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xxl),
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = AppTheme.glass.amber,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}
