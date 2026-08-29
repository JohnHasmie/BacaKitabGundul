package com.classicbookreader.app.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.classicbookreader.app.R
import com.classicbookreader.app.data.analysis.AnalysisEvent
import com.classicbookreader.app.data.analysis.AnalysisResult
import com.classicbookreader.app.data.analysis.WordAnalysis
import com.classicbookreader.app.feature.reader.ReaderViewModel.AiUiState
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.components.PillButton
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.ArabicTextStyles
import com.classicbookreader.app.ui.theme.Spacing

/**
 * The analysis bottom sheet (mockup screens 7-8): streams early harakat,
 * then shows the full result behind Cara Baca / I'rob / Shorof / Arti tabs.
 * Religious-content policy lives in the backend; the UI only carries the
 * "Penjelasan AI" label and a report action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisSheet(
    ai: AiUiState,
    isDemoMode: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSaveWord: (WordAnalysis) -> Unit,
    onReport: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.xl)
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            when (ai) {
                is AiUiState.Preparing -> AnalysisPending()
                is AiUiState.Streaming -> AnalysisStreaming(ai.vocalizedText)
                is AiUiState.Failed -> AnalysisFailed(ai.reason, onRetry)
                is AiUiState.Ready -> AnalysisReady(
                    result = ai.result,
                    isDemoMode = isDemoMode,
                    onSaveWord = onSaveWord,
                    onReport = onReport,
                )
                else -> Unit
            }
            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun AnalysisPending() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.ai_analyzing),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.glass.inkTertiary,
        )
    }
}

@Composable
private fun AnalysisStreaming(vocalizedText: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ArabicBlock(vocalizedText)
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = AppTheme.glass.inkWash,
        )
        Text(
            text = stringResource(R.string.ai_analyzing),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.glass.inkTertiary,
        )
    }
}

@Composable
private fun AnalysisFailed(reason: AnalysisEvent.FailureReason, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(
                when (reason) {
                    AnalysisEvent.FailureReason.NETWORK -> R.string.ai_failed_network
                    AnalysisEvent.FailureReason.SERVER -> R.string.ai_failed_server
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        PillButton(text = stringResource(R.string.action_retry), onClick = onRetry)
    }
}

@Composable
private fun AnalysisReady(
    result: AnalysisResult,
    isDemoMode: Boolean,
    onSaveWord: (WordAnalysis) -> Unit,
    onReport: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.ai_tab_reading),
        stringResource(R.string.ai_tab_irab),
        stringResource(R.string.ai_tab_sarf),
        stringResource(R.string.ai_tab_meaning),
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LabelChip(text = stringResource(R.string.ai_label))
            Text(
                text = stringResource(R.string.ai_report),
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.glass.inkTertiary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onReport)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }

        ArabicBlock(result.vocalizedText)
        if (result.transliteration.isNotBlank()) {
            Text(
                text = result.transliteration,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.glass.inkTertiary,
            )
        }
        if (result.phraseGloss.isNotBlank()) {
            Text(
                text = result.phraseGloss,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        TabPills(tabs = tabs, selected = selectedTab, onSelected = { selectedTab = it })

        when (selectedTab) {
            0 -> ReadingTab(result)
            1 -> WordCards(result.words) { word ->
                DetailLine(stringResource(R.string.ai_irab_role), word.irab.role)
                DetailLine(stringResource(R.string.ai_irab_case_marker), word.irab.caseMarker)
                if (word.irab.reasoning.isNotBlank()) {
                    Text(
                        text = word.irab.reasoning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.glass.inkSecondary,
                    )
                }
            }
            2 -> WordCards(result.words) { word ->
                DetailLine(stringResource(R.string.ai_sarf_root), word.sarf.root)
                DetailLine(stringResource(R.string.ai_sarf_pattern), word.sarf.pattern)
                DetailLine(stringResource(R.string.ai_sarf_form), word.sarf.form)
            }
            3 -> WordCards(result.words, onSaveWord) { word ->
                Text(text = word.gloss, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (isDemoMode) {
            Text(
                text = stringResource(R.string.ai_demo_note),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.glass.inkTertiary,
            )
        }
    }
}

@Composable
private fun ReadingTab(result: AnalysisResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (result.contextBefore.isNotBlank() || result.contextAfter.isNotBlank()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(
                        text = stringResource(R.string.ai_context_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTheme.glass.inkTertiary,
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(
                            text = listOf(result.contextBefore, result.selectedText, result.contextAfter)
                                .filter { it.isNotBlank() }
                                .joinToString(" "),
                            style = ArabicTextStyles.body,
                            color = AppTheme.glass.inkSecondary,
                        )
                    }
                }
            }
        }
        WordCards(result.words) { word ->
            if (word.transliteration.isNotBlank()) {
                Text(
                    text = word.transliteration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.glass.inkTertiary,
                )
            }
        }
    }
}

/** One glass card per analyzed word; [detail] fills the tab-specific body. */
@Composable
private fun WordCards(
    words: List<WordAnalysis>,
    onSaveWord: ((WordAnalysis) -> Unit)? = null,
    detail: @Composable (WordAnalysis) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        words.forEach { word ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = word.vocalized, style = ArabicTextStyles.label)
                        if (onSaveWord != null) {
                            IconButton(onClick = { onSaveWord(word) }) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkAdd,
                                    contentDescription = stringResource(R.string.ai_save_word),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    detail(word)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.glass.inkTertiary,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ArabicBlock(text: String) {
    if (text.isBlank()) return
    Box(modifier = Modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                text = text,
                style = ArabicTextStyles.body,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LabelChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

@Composable
private fun TabPills(tabs: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == selected
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else AppTheme.glass.inkWash,
                    )
                    .clickable { onSelected(index) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
    }
}
