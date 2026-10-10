package dev.montra.ui

import androidx.compose.ui.platform.LocalContext
import dev.montra.R
import dev.montra.util.asString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: UiState,
    query: String,
    results: List<AppRow>,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (IndexApp) -> Unit,
    onInstall: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val suggestions = remember(state.rows) {
        state.rows.sortedByDescending { it.app.downloadCount ?: 0L }.take(6)
    }

    // O teclado tapa a lista: sem isto, os resultados e o fim da lista ficavam por
    // baixo dele e a última sugestão era inalcançável.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.md)
                .focusRequester(focusRequester),
            placeholder = { Text(strings.getString(R.string.text_name_tag_or_package)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        onClear()
                        keyboard?.hide()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = strings.getString(R.string.text_clear))
                    }
                }
            },
            singleLine = true,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                query.isBlank() -> Results(
                    title = strings.getString(R.string.text_most_downloaded),
                    rows = suggestions,
                    state = state,
                    onRefresh = onRefresh,
                    onOpen = onOpen,
                    onInstall = onInstall,
                    onAuthorize = onAuthorize,
                )

                results.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Space.xl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = strings.getString(R.string.text_nothing_found_for_1_s, query),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = strings.getString(R.string.text_search_by_name_summary_author_tag_or),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Space.sm),
                    )
                }

                else -> Results(
                    title = strings.resources.getQuantityString(R.plurals.result_count, results.size, results.size),
                    rows = results,
                    state = state,
                    onRefresh = onRefresh,
                    onOpen = onOpen,
                    onInstall = onInstall,
                    onAuthorize = onAuthorize,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Results(
    title: String,
    rows: List<AppRow>,
    state: UiState,
    onRefresh: () -> Unit,
    onOpen: (IndexApp) -> Unit,
    onInstall: (IndexApp) -> Unit,
    onAuthorize: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.index.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            contentPadding = PaddingValues(top = Space.sm, bottom = Space.xxl),
        ) {
            appSections(
                sections = listOf(AppSectionSpec(title, rows)),
                needsPermission = !state.canInstallPackages,
                onOpen = onOpen,
                onInstall = onInstall,
                onAuthorize = onAuthorize,
            )
        }
    }
}
