package dev.montra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.montra.data.model.IndexApp
import dev.montra.data.model.categoryLabel
import dev.montra.ui.components.AlertBlock
import dev.montra.ui.components.SearchBar
import dev.montra.ui.theme.Space

/**
 * The Apps tab: everything in the catalogue, grouped by what the person needs to do
 * about it — updates first, then what is already installed, then the rest.
 *
 * The categories chip row filters here only. Searching is its own tab, reached
 * through the search bar, which is how the Play Store does it and how the bottom bar
 * makes sense.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    state: UiState,
    onCategory: (String?) -> Unit,
    onRestrictedOnly: (Boolean) -> Unit,
    onSort: (SortOrder) -> Unit,
    onOpenSearch: () -> Unit,
    onOpen: (IndexApp) -> Unit,
    onInstall: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(state.rows, state.category, state.restrictedOnly) {
        val base = if (state.restrictedOnly) {
            state.rows.filter { it.app.hasRestrictedLicense() }
        } else {
            state.rows
        }
        state.category?.let { category -> base.filter { it.app.categories.contains(category) } } ?: base
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            onClick = onOpenSearch,
            placeholder = "Procurar por nome, etiqueta ou pacote",
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
        )

        if (!state.canInstallPackages) {
            AlertBlock(
                title = "Falta uma autorização",
                text = "O Android ainda não autorizou a Montra a instalar aplicações. " +
                    "É uma autorização por app, dada nas definições do sistema.",
                action = {
                    androidx.compose.material3.Button(onClick = onAuthorize) {
                        Text("Autorizar instalação")
                    }
                },
                modifier = Modifier.padding(horizontal = Space.lg),
            )
        }

        if (state.categories.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Space.lg),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    item { SortChip(state.sort, onSort) }
                    // Só aparece se existirem: um filtro que não filtra nada é ruído.
                    if (state.restrictedCount > 0 && !state.hideRestricted) {
                        item {
                            FilterChip(
                                selected = state.restrictedOnly,
                                onClick = { onRestrictedOnly(!state.restrictedOnly) },
                                label = { Text("Licença restritiva") },
                            )
                        }
                    }
                    item {
                        FilterChip(
                            selected = state.category == null,
                            onClick = { onCategory(null) },
                            label = { Text("Todas") },
                        )
                    }
                    items(state.categories.size) { index ->
                        val category = state.categories[index]
                        FilterChip(
                            selected = state.category == category,
                            onClick = { onCategory(if (state.category == category) null else category) },
                            label = { Text(categoryLabel(category)) },
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.index.loading && filtered.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                filtered.isEmpty() -> EmptyState(state)

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Space.lg,
                        end = Space.lg,
                        top = Space.sm,
                        bottom = Space.xxl,
                    ),
                ) {
                    val updates = filtered.filter { it.updateAvailable }
                    val installed = filtered.filter { it.isInstalled && !it.updateAvailable }
                    val rest = filtered.filter { !it.isInstalled && !it.updateAvailable }

                    if (updates.isNotEmpty()) {
                        item { AppSectionBlock("Atualizações disponíveis", updates, state, onOpen, onInstall, onAuthorize) }
                    }
                    if (installed.isNotEmpty()) {
                        item { AppSectionBlock("Instaladas", installed, state, onOpen, onInstall, onAuthorize) }
                    }
                    if (rest.isNotEmpty()) {
                        item {
                            AppSectionBlock(
                                if (updates.isEmpty() && installed.isEmpty()) "Tudo" else "Descobrir",
                                rest,
                                state,
                                onOpen,
                                onInstall,
                                onAuthorize,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSectionBlock(
    title: String,
    rows: List<AppRow>,
    state: UiState,
    onOpen: (IndexApp) -> Unit,
    onInstall: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
) {
    AppSection(
        title = title,
        rows = rows,
        needsPermission = !state.canInstallPackages,
        onOpen = onOpen,
        onInstall = onInstall,
        onAuthorize = onAuthorize,
    )
}

@Composable
private fun EmptyState(state: UiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when {
                state.index.error != null -> state.index.error
                state.restrictedOnly -> "Nenhuma app com licença restritiva."
                state.category != null -> "Nada nesta categoria."
                else -> "O catálogo está vazio."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.index.error != null) {
            Text(
                text = "O último catálogo verificado continua a ser usado.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortChip(current: SortOrder, onSort: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = false, onClick = { expanded = true }, label = { Text(current.label) })
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
