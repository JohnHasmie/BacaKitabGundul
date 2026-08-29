package com.classicbookreader.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Spacing

/** Primary action: filled green pill with soft shadow. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .shadow(10.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Spacing.xxl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        // Centered so a fillMaxWidth pill (e.g. the onboarding CTA) keeps its label centered.
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        trailing?.invoke()
    }
}

/** Secondary action: glass pill with hairline border. */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(AppTheme.glass.surface)
            .border(BorderStroke(1.dp, AppTheme.glass.hairline), CircleShape)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
