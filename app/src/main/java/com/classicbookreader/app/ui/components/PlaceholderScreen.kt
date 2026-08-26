package com.classicbookreader.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.classicbookreader.app.R
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Spacing

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard {
            Column(
                modifier = Modifier.padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.placeholder_coming_soon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.glass.inkTertiary,
                )
            }
        }
    }
}
