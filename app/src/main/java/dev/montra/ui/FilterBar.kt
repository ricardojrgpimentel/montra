package dev.montra.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.montra.data.model.RequirementFilter
import dev.montra.data.model.categoryLabel
import dev.montra.ui.components.SectionTitle
import dev.montra.ui.theme.Motion
import dev.montra.ui.theme.Space

/**
 * A barra de filtros.
 *
 * O problema que isto resolve: a fila de chips antiga punha quatro coisas
 * diferentes no mesmo fato — a ordenação (um menu), dois filtros do catálogo e
 * dezoito categorias — todas com o mesmo `FilterChip`. Não havia hierarquia, e a
 * única forma de saber o que estava escolhido era olhar para a cor de um chip que,
 * entretanto, já tinha saído do ecrã por a fila fazer scroll na horizontal.
 *
 * Agora há duas formas distintas e uma memória:
 *
 *   - **Ordenar** é um menu: texto simples, com uma seta. Não guarda estado, muda a
 *     ordem.
 *   - **Filtrar** é um estado: ganha moldura e preenchimento quando há algo ativo, e
 *     diz quantos filtros estão ligados.
 *   - Por baixo, os filtros ativos aparecem como peças removíveis. Essa linha fica
 *     fora da lista, portanto continua à vista com a lista a rolar — que era
 *     exactamente o que faltava.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    state: UiState,
    onSort: (SortOrder) -> Unit,
    onFilter: (AppFilter?) -> Unit,
    onRequirement: (RequirementFilter?) -> Unit,
    onCategory: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }

    val activeFilters = (if (state.category != null) 1 else 0) + (if (state.filter != null) 1 else 0) +
        (if (state.requirement != null) 1 else 0)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { sortOpen = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Space.sm))
                Text(state.sort.label, maxLines = 1)
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Mudar a ordenação",
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            if (activeFilters > 0) {
                FilledTonalButton(onClick = { filterOpen = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text("Filtrar · $activeFilters")
                }
            } else {
                OutlinedButton(onClick = { filterOpen = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text("Filtrar")
                }
            }
        }

        ActiveFilters(
            state = state,
            onFilter = onFilter,
            onRequirement = onRequirement,
            onCategory = onCategory,
        )
    }

    if (sortOpen) {
        SortSheet(
            current = state.sort,
            onPick = onSort,
            onDismiss = { sortOpen = false },
        )
    }
    if (filterOpen) {
        FilterSheet(
            state = state,
            onCategory = onCategory,
            onFilter = onFilter,
            onRequirement = onRequirement,
            onClear = {
                onCategory(null)
                onFilter(null)
                onRequirement(null)
            },
            onDismiss = { filterOpen = false },
        )
    }
}

/**
 * O que está escolhido, em peças que se podem tirar. Não desaparece com o scroll da
 * lista porque vive fora dela.
 */
@Composable
private fun ActiveFilters(
    state: UiState,
    onFilter: (AppFilter?) -> Unit,
    onRequirement: (RequirementFilter?) -> Unit,
    onCategory: (String?) -> Unit,
) {
    val visible = state.category != null || state.filter != null || state.requirement != null
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = Motion.standard()) + fadeIn(Motion.standard()),
        exit = shrinkVertically(animationSpec = Motion.quick()) + fadeOut(Motion.quick()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = Space.lg, end = Space.lg, bottom = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.category?.let { category ->
                ActiveChip(label = categoryLabel(category)) { onCategory(null) }
            }
            state.filter?.let { filter ->
                ActiveChip(label = filter.label) { onFilter(null) }
            }
            state.requirement?.let { requirement ->
                ActiveChip(label = requirement.label) { onRequirement(null) }
            }
            TextButton(onClick = { onCategory(null); onFilter(null); onRequirement(null) }) { Text("Limpar") }
        }
    }
}

@Composable
private fun ActiveChip(label: String, onRemove: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onRemove,
        label = {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Tirar o filtro $label",
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(current: SortOrder, onPick: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeading("Ordenar por", "A ordem da lista. Não esconde nada.")
        SortOrder.entries.forEach { order ->
            ChoiceRow(
                label = order.label,
                selected = current == order,
                onClick = {
                    onPick(order)
                    onDismiss()
                },
            )
        }
        Spacer(Modifier.height(Space.xl))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: UiState,
    onCategory: (String?) -> Unit,
    onFilter: (AppFilter?) -> Unit,
    onRequirement: (RequirementFilter?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Quantas apps sobram com o que está escolhido neste momento: o botão de fechar
    // é a resposta, em vez de obrigar a fechar para contar.
    val remaining = remember(state.rows, state.category, state.filter, state.requirement) {
        state.filteredRows()
    }.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.heightIn(max = 540.dp)) {
            // Keep the result count and clear action reachable while filters scroll.
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                SheetHeading("Filtrar", "Combina requisitos, categoria e origem das apps.")

                SectionTitle("Requisitos", divider = false, modifier = Modifier.padding(horizontal = Space.lg))
                Text(
                    "Segundo o catálogo. Shizuku e Root incluem funcionalidades opcionais.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.lg),
                )
                Column(modifier = Modifier.padding(horizontal = Space.lg)) {
                    ChoiceRow(
                        label = "Todas",
                        selected = state.requirement == null,
                        onClick = { onRequirement(null) },
                        padding = false,
                    )
                    RequirementFilter.entries.forEach { requirement ->
                        ChoiceRow(
                            label = requirement.label,
                            detail = "${state.rows.count { it.app.matchesCatalogueFilters(state.category, state.filter, requirement) }}",
                            selected = state.requirement == requirement,
                            onClick = { onRequirement(if (state.requirement == requirement) null else requirement) },
                            padding = false,
                        )
                    }
                }

                SectionTitle("Categorias", modifier = Modifier.padding(horizontal = Space.lg))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    FilterChip(
                        selected = state.category == null,
                        onClick = { onCategory(null) },
                        label = { Text("Todas") },
                    )
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = {
                                // Carregar no que já está escolhido desliga-o: é a mesma
                                // regra do interruptor, e evita ter de procurar o "Todas".
                                onCategory(if (state.category == category) null else category)
                            },
                            label = {
                                Text("${categoryLabel(category)} ${state.categoryCounts[category] ?: 0}")
                            },
                        )
                    }
                }

                SectionTitle("Mostrar", modifier = Modifier.padding(horizontal = Space.lg))
                Column(modifier = Modifier.padding(horizontal = Space.lg)) {
                    ChoiceRow(
                        label = "Tudo",
                        detail = "${state.rows.size}",
                        selected = state.filter == null,
                        onClick = { onFilter(null) },
                        padding = false,
                    )
                    AppFilter.entries
                        .filter { (state.filterCounts[it] ?: 0) > 0 }
                        .forEach { entry ->
                            ChoiceRow(
                                label = entry.label,
                                detail = "${state.filterCounts[entry] ?: 0}",
                                selected = state.filter == entry,
                                onClick = { onFilter(if (state.filter == entry) null else entry) },
                                padding = false,
                            )
                        }
                }

            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onClear,
                    enabled = state.category != null || state.filter != null || state.requirement != null,
                ) { Text("Limpar") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) {
                    Text(if (remaining == 1) "Ver 1 app" else "Ver $remaining apps")
                }
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun SheetHeading(title: String, detail: String) {
    Column(modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    detail: String? = null,
    padding: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = if (padding) Space.lg else 0.dp, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Space.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
