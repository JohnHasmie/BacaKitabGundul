package com.classicbookreader.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.classicbookreader.app.ui.theme.AppTheme

/** Shared confirm step for destructive actions — never window.confirm-style ad-hoc dialogs. */
@Composable
fun ConfirmationDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    text: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = text?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.glass.inkSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel, color = MaterialTheme.colorScheme.onBackground)
            }
        },
    )
}
