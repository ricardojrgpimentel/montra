package dev.montra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.montra.data.model.AccessRequirements
import dev.montra.ui.theme.Space

@Composable
fun AccessBadge(access: AccessRequirements) {
    val color = if (access.isRequired) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(Space.xs)) {
        Row(
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Key, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(access.label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
