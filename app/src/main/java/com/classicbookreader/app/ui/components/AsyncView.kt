package com.classicbookreader.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.classicbookreader.app.R
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Spacing

/** Canonical async state for every list/detail screen. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Empty(val title: String) : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
}

/**
 * Wraps a screen body and renders loading / error / empty states
 * consistently; the [content] slot only ever sees loaded data.
 */
@Composable
fun <T> AsyncView(
    state: UiState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is UiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        is UiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                modifier = Modifier.padding(Spacing.xxl),
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.glass.inkSecondary,
                    textAlign = TextAlign.Center,
                )
                PillButton(text = stringResource(R.string.action_retry), onClick = onRetry)
            }
        }

        is UiState.Empty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.glass.inkTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Spacing.xxl),
            )
        }

        is UiState.Content -> content(state.data)
    }
}
