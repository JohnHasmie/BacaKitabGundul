package com.classicbookreader.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.classicbookreader.app.R
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Radius
import com.classicbookreader.app.ui.theme.Spacing

/** Add-book source picker (mockup 3): PDF works now, the rest are teasers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookSheet(
    onPickPdf: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.library_add_book),
                style = MaterialTheme.typography.titleMedium,
            )
            ImportOptionRow(
                icon = Icons.Outlined.Description,
                iconTint = AppTheme.glass.amberInk,
                iconBackground = AppTheme.glass.amber.copy(alpha = 0.2f),
                title = stringResource(R.string.import_option_pdf),
                subtitle = stringResource(R.string.import_option_pdf_desc),
                enabled = true,
                onClick = onPickPdf,
            )
            ImportOptionRow(
                icon = Icons.Outlined.AutoStories,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                title = stringResource(R.string.import_option_epub),
                subtitle = stringResource(R.string.import_option_epub_desc),
                enabled = false,
                onClick = {},
            )
            ImportOptionRow(
                icon = Icons.Outlined.CameraAlt,
                iconTint = MaterialTheme.colorScheme.onBackground,
                iconBackground = AppTheme.glass.inkWash,
                title = stringResource(R.string.import_option_scan),
                subtitle = stringResource(R.string.import_option_scan_desc),
                enabled = false,
                onClick = {},
            )
            Text(
                text = stringResource(R.string.import_privacy_note),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.glass.inkTertiary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun ImportOptionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(AppTheme.glass.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(Spacing.lg)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(iconBackground),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.glass.inkSecondary,
            )
        }
        if (!enabled) {
            Text(
                text = stringResource(R.string.import_coming_soon),
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.glass.inkTertiary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppTheme.glass.inkWash)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
}
