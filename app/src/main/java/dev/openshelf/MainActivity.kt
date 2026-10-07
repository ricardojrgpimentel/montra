package dev.openshelf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.openshelf.ui.AppDetailScreen
import dev.openshelf.ui.AppListScreen
import dev.openshelf.ui.OpenShelfViewModel
import dev.openshelf.ui.SettingsScreen
import dev.openshelf.ui.theme.OpenShelfTheme

class MainActivity : ComponentActivity() {

    private val viewModel: OpenShelfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OpenShelfTheme {
                OpenShelfRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The "install unknown apps" permission and the set of installed apps can
        // both change while we are in the background.
        viewModel.onResume()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenShelfRoot(viewModel: OpenShelfViewModel) {
    val state by viewModel.ui.collectAsState()
    val navController = rememberNavController()
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(route)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    if (route != "list") {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (route == "list") {
                        IconButton(onClick = { viewModel.refresh(true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Atualizar catálogo")
                        }
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Definições")
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "list",
            modifier = Modifier.padding(padding),
        ) {
            composable("list") {
                AppListScreen(
                    state = state,
                    onQuery = viewModel::setQuery,
                    onCategory = viewModel::setCategory,
                    onSort = viewModel::setSort,
                    onOpen = { app -> navController.navigate("detail/${app.id}") },
                    onInstall = viewModel::install,
                )
            }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id")
                val row = state.rows.firstOrNull { it.app.id == id }
                if (row == null) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Text("Esta app já não está no catálogo.")
                    }
                } else {
                    AppDetailScreen(
                        row = row,
                        keyId = state.index.keyId,
                        onInstall = viewModel::install,
                        onClearError = viewModel::clearInstallError,
                        onOpenSource = { url -> openUrl(context, url) },
                    )
                }
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    onRefresh = { viewModel.refresh(true) },
                    onSetIndexUrl = viewModel::setIndexUrl,
                )
            }
        }
    }
}

private fun titleFor(route: String?): String = when {
    route == null -> "OpenShelf"
    route.startsWith("detail") -> "Detalhes"
    route == "settings" -> "Definições"
    else -> "OpenShelf"
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Suppress("unused")
private fun NavHostController.noop() = Unit
