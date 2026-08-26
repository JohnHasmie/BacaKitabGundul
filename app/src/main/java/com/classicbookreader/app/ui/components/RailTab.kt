package com.classicbookreader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.classicbookreader.app.ui.theme.AppTheme
import com.classicbookreader.app.ui.theme.Radius
import com.classicbookreader.app.ui.theme.Spacing

data class RailTabItem(
    val id: String,
    val label: String,
    val icon: @Composable (selected: Boolean) -> Unit,
)

/**
 * Vertical glass tab rail, anchored to the right edge — it follows the
 * RTL reading direction of the kitab text and the reach of the right
 * thumb. Used by the analysis screens (Baca / I'rob / Shorof / Arti /
 * Wawasan).
 */
@Composable
fun RailTab(
    items: List<RailTabItem>,
    selectedId: String,
    onSelected: (RailTabItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, cornerRadius = Radius.xl, elevation = 10.dp) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items.forEach { item ->
                val selected = item.id == selectedId
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.md))
                        .then(
                            if (selected) {
                                Modifier
                                    .shadow(6.dp, RoundedCornerShape(Radius.md))
                                    .background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelected(item) }
                        .size(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    item.icon(selected)
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            AppTheme.glass.inkSecondary
                        },
                    )
                }
            }
        }
    }
}
