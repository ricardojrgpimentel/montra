package dev.montra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import dev.montra.data.model.IndexApp
import dev.montra.ui.theme.Space

/**
 * The Search tab.
 *
 * With an empty field it suggests something instead of showing nothing: a search
 * screen that is blank until you type is a dead end. The field takes focus on
 * arrival, because that is the only reason to be on this tab.
 */
@Composable
fun SearchScreen(
    state: UiState,
    query: String,
    results: List<AppRow>,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    onOpen: (IndexApp) -> Unit,
    onInstall: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val suggestions = remember(state.rows) {
        state.rows.sortedByDescending { it.app.downloadCount ?: 0L }.take(6)
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.md)
                .focusRequester(focusRequester),
            placeholder = { Text("Nome, etiqueta ou pacote") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        onClear()
                        keyboard?.hide()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpar")
                    }
                }
            },
            singleLine = true,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                query.isBlank() -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Space.lg,
                        end = Space.lg,
                        top = Space.sm,
                        bottom = Space.xxl,
                    ),
                ) {
                    item {
                        AppSection(
                            title = "Mais descarregadas",
                            rows = suggestions,
                            needsPermission = !state.canInstallPackages,
                            onOpen = onOpen,
                            onInstall = onInstall,
                            onAuthorize = onAuthorize,
                        )
                    }
                }

                results.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(Space.xl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Nada encontrado para \"$query\".",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Space.sm))
                    Text(
                        text = "Procura por nome, resumo, autor, etiqueta ou nome do pacote — " +
                            "por exemplo \"emulador\" ou \"org.fdroid\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Space.lg,
                        end = Space.lg,
                        top = Space.sm,
                        bottom = Space.xxl,
                    ),
                ) {
                    item {
                        AppSection(
                            title = if (results.size == 1) "1 resultado" else "${results.size} resultados",
                            rows = results,
                            needsPermission = !state.canInstallPackages,
                            onOpen = onOpen,
                            onInstall = onInstall,
                            onAuthorize = onAuthorize,
                        )
                    }
                }
            }
        }
    }
}
