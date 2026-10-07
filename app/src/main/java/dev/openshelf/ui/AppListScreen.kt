package dev.openshelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openshelf.data.model.IndexApp
import dev.openshelf.data.model.categoryLabel
import dev.openshelf.install.InstallState
import dev.openshelf.ui.components.AppIcon
import dev.openshelf.util.formatBytes

@Composable
fun AppListScreen(
    state: UiState,
    onQuery: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onSort: (SortOrder) -> Unit,
    onOpen: (IndexApp) -> Unit,
    onInstall: (IndexApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Procurar por nome, etiqueta ou pacote") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Limpar",
                        modifier = Modifier.clickable { onQuery("") },
                    )
                }
            },
            singleLine = true,
        )

        if (state.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SortChip(state.sort, onSort)
                }
                item {
                    FilterChip(
                        selected = state.category == null,
                        onClick = { onCategory(null) },
                        label = { Text("Todas") },
                    )
                }
                items(state.categories) { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { onCategory(if (state.category == category) null else category) },
                        label = { Text(categoryLabel(category)) },
                    )
                }
            }
        }

        if (state.index.refreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.index.loading && state.rows.isEmpty() -> Loading()
                state.rows.isEmpty() -> Empty(state)
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.rows, key = { it.app.id }) { row ->
                        AppCard(
                            row = row,
                            onOpen = { onOpen(row.app) },
                            onInstall = { onInstall(row.app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Empty(state: UiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.index.error?.let { "Não foi possível atualizar o catálogo.\n$it" }
                ?: "Nada encontrado para esta pesquisa.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortChip(current: SortOrder, onSort: (SortOrder) -> Unit) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text(current.label) },
        )
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(order.label) },
                    onClick = {
                        onSort(order)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AppCard(row: AppRow, onOpen: () -> Unit, onInstall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            AppIcon(
                url = row.iconUrl,
                seed = row.app.packageName,
                text = row.app.name.take(1).uppercase(),
                size = 52,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.app.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.app.status != "active") {
                        Spacer(Modifier.width(6.dp))
                        Badge(row.app.status, MaterialTheme.colorScheme.tertiary)
                    }
                }
                Text(
                    text = row.app.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "v${row.app.release?.versionName ?: "?"} · ${formatBytes(row.size)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!row.app.availableOnPlayStore && row.app.playStore != null) {
                        Spacer(Modifier.width(6.dp))
                        Badge("fora da Play Store", MaterialTheme.colorScheme.primary)
                    }
                    if (row.updateAvailable) {
                        Spacer(Modifier.width(6.dp))
                        Badge("atualizar", MaterialTheme.colorScheme.error)
                    }
                }
                if (row.installState is InstallState.Downloading) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { row.installState.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (row.installState is InstallState.Failed) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = row.installState.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            InstallAction(row = row, onInstall = onInstall)
        }
    }
}

@Composable
fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun InstallAction(row: AppRow, onInstall: () -> Unit) {
    when (row.installState) {
        is InstallState.Downloading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
        is InstallState.Verifying, InstallState.AwaitingUser -> CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
        )
        is InstallState.Installed -> Badge("instalado", MaterialTheme.colorScheme.primary)
        else -> {
            if (!row.isInstalled || row.updateAvailable) {
                TextButton(onClick = onInstall, enabled = row.asset != null) {
                    Text(if (row.isInstalled) "Atualizar" else "Instalar")
                }
            } else {
                Badge("instalado", MaterialTheme.colorScheme.primary)
            }
        }
    }
}
