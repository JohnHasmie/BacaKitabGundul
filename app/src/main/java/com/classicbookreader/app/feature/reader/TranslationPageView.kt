package com.classicbookreader.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.classicbookreader.app.R
import com.classicbookreader.app.data.analysis.AnalysisEvent
import com.classicbookreader.app.data.translation.TranslatedWord
import com.classicbookreader.app.data.translation.TranslationLine
import com.classicbookreader.app.feature.reader.ReaderViewModel.TranslationUiState
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.components.PillButton
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.ArabicTextStyles
import com.classicbookreader.app.ui.theme.Radius
import com.classicbookreader.app.ui.theme.Spacing

/**
 * One page in interlinear translate mode (mockup screen 9): flowing RTL
 * rows of word chips — Arabic on top, a small Indonesian gloss beneath.
 * Translation is an explicit per-page action, never automatic.
 */
@Composable
fun TranslationPageView(
    state: TranslationUiState,
    textScale: Float,
    onTranslate: () -> Unit,
    onRetranslate: () -> Unit,
    onWordTap: (TranslatedWord) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is TranslationUiState.Idle -> TranslationPrompt(onTranslate, modifier)
        is TranslationUiState.Loading -> TranslationLoading(state.wordCount, modifier)
        is TranslationUiState.Failed -> TranslationFailed(state.reason, onTranslate, modifier)
        is TranslationUiState.Ready -> TranslationLines(
            lines = state.translation.lines,
            fromCache = state.fromCache,
            textScale = textScale,
            onRetranslate = onRetranslate,
            onWordTap = onWordTap,
            modifier = modifier,
        )
    }
}

@Composable
private fun TranslationPrompt(onTranslate: () -> Unit, modifier: Modifier = Modifier) {
    CenteredCard(modifier) {
        Text(
            text = stringResource(R.string.translate_prompt_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.translate_prompt_body),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.glass.inkSecondary,
            textAlign = TextAlign.Center,
        )
        PillButton(text = stringResource(R.string.translate_action), onClick = onTranslate)
    }
}

@Composable
private fun TranslationLoading(wordCount: Int, modifier: Modifier = Modifier) {
    CenteredCard(modifier) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.translate_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.glass.inkTertiary,
        )
        if (wordCount > 0) {
            Text(
                text = stringResource(R.string.translate_word_count, wordCount),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun TranslationFailed(
    reason: AnalysisEvent.FailureReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredCard(modifier) {
        Text(
            text = stringResource(
                when (reason) {
                    AnalysisEvent.FailureReason.NETWORK -> R.string.ai_failed_network
                    AnalysisEvent.FailureReason.SERVER -> R.string.ai_failed_server
                    AnalysisEvent.FailureReason.RATE_LIMITED -> R.string.translate_rate_limited
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        PillButton(text = stringResource(R.string.action_retry), onClick = onRetry)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslationLines(
    lines: List<TranslationLine>,
    fromCache: Boolean,
    textScale: Float,
    onRetranslate: () -> Unit,
    onWordTap: (TranslatedWord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val arabicStyle = ArabicTextStyles.body.copy(
        fontSize = ArabicTextStyles.body.fontSize * textScale,
        lineHeight = ArabicTextStyles.body.lineHeight * textScale,
    )
    val glossStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.xl,
            end = Spacing.xl,
            top = 96.dp, // clears the reader chrome
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (fromCache) {
                    Text(
                        text = stringResource(R.string.translate_cached),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }
                Text(
                    text = stringResource(R.string.translate_retranslate),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.glass.inkTertiary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onRetranslate)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }

        items(lines) { line ->
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    line.words.forEach { word ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(AppTheme.glass.surface)
                                .clickable { onWordTap(word) }
                                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        ) {
                            Text(text = word.arabic, style = arabicStyle)
                            CompositionLocalProvider(
                                LocalLayoutDirection provides LayoutDirection.Ltr,
                            ) {
                                Text(
                                    text = word.gloss,
                                    style = glossStyle,
                                    color = AppTheme.glass.inkSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(140.dp)) } // clears the slider footer
    }
}

@Composable
private fun CenteredCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard {
            Column(
                modifier = Modifier.padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                content = content,
            )
        }
    }
}
