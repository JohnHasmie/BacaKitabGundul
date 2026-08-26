package com.classicbookreader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Spacing

data class DockItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Floating glass navigation dock. The selected item expands into a filled
 * green pill with its label; the rest collapse to icons.
 */
@Composable
fun GlassDock(
    items: List<DockItem>,
    selectedRoute: String,
    onItemSelected: (DockItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, cornerRadius = 999.dp, elevation = 14.dp) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                if (selected) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onItemSelected(item) }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                } else {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = AppTheme.glass.inkTertiary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onItemSelected(item) }
                            .padding(Spacing.md)
                            .size(20.dp),
                    )
                }
            }
        }
    }
}
