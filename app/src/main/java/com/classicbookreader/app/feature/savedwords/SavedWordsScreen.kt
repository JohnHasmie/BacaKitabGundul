package com.classicbookreader.app.feature.savedwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classicbookreader.app.R
import com.classicbookreader.app.feature.savedwords.SavedWordsViewModel.SavedWordUi
import com.classicbookreader.app.ui.components.AsyncView
import com.classicbookreader.app.ui.components.ConfirmationDialog
import com.classicbookreader.app.ui.components.GlassCard
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.ArabicTextStyles
import com.classicbookreader.app.ui.theme.Sizes
import com.classicbookreader.app.ui.theme.Spacing

@Composable
fun SavedWordsScreen(viewModel: SavedWordsViewModel = hiltViewModel()) {
    val words by viewModel.words.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<SavedWordUi?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.saved_words_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        AsyncView(state = words, onRetry = {}) { items ->
            if (items.isEmpty()) {
                EmptySavedWords()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    items(items, key = { it.id }) { word ->
                        SavedWordCard(word = word, onDelete = { pendingDelete = word })
                    }
                    item { Spacer(modifier = Modifier.height(Sizes.dockClearance)) }
                }
            }
        }
    }

    pendingDelete?.let { word ->
        ConfirmationDialog(
            title = stringResource(R.string.saved_words_delete_confirm),
            text = word.vocalized,
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                viewModel.delete(word.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SavedWordCard(word: SavedWordUi, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = word.vocalized, style = ArabicTextStyles.label)
                Text(text = word.gloss, style = MaterialTheme.typography.bodyLarge)
                val detail = listOfNotNull(
                    word.transliteration,
                    word.pageNumber?.let { stringResource(R.string.saved_words_page, it) },
                ).joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.glass.inkTertiary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = AppTheme.glass.inkTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptySavedWords() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard {
            Column(
                modifier = Modifier.padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.saved_words_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.saved_words_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.glass.inkTertiary,
                )
            }
        }
    }
}
