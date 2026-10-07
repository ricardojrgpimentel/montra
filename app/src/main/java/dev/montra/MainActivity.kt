package dev.montra

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.montra.data.model.IndexApp
import dev.montra.install.InstallManager
import dev.montra.install.InstallRequest
import dev.montra.ui.AppDetailScreen
import dev.montra.ui.AppListScreen
import dev.montra.ui.MontraViewModel
import dev.montra.ui.SettingsScreen
import dev.montra.ui.theme.MontraTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private val viewModel: MontraViewModel by viewModels()

    /** Set when the app was opened from a download notification. */
    private val pendingAppId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingAppId.value = intent?.getStringExtra(InstallRequest.EXTRA_APP_ID)
        setContent {
            MontraTheme {
                MontraRoot(
                    viewModel = viewModel,
                    pendingAppId = pendingAppId,
                    onAppIdConsumed = { pendingAppId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Tapping the progress notification while the app is already open should
        // still take the user to that app, not restart the activity (singleTop).
        pendingAppId.value = intent.getStringExtra(InstallRequest.EXTRA_APP_ID)
    }

    override fun onResume() {
        super.onResume()
        // The "install unknown apps" permission and the set of installed apps can
        // both change while we are in the background — we send the user to Settings
        // for exactly that reason, so the state has to be re-read on return.
        viewModel.onResume()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MontraRoot(
    viewModel: MontraViewModel,
    pendingAppId: StateFlow<String?> = MutableStateFlow(null),
    onAppIdConsumed: () -> Unit = {},
) {
    val state by viewModel.ui.collectAsState()
    val requestedAppId by pendingAppId.collectAsState()
    val navController = rememberNavController()
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    // Deep link from the notification; waits for the catalogue if it is still loading.
    LaunchedEffect(requestedAppId, state.rows.size) {
        val id = requestedAppId ?: return@LaunchedEffect
        if (state.rows.any { it.app.id == id }) {
            navController.navigate("detail/$id") { launchSingleTop = true }
            onAppIdConsumed()
        }
    }

    val authorize: () -> Unit = {
        runCatching { context.startActivity(InstallManager.of(context).unknownSourcesSettingsIntent()) }
        Unit
    }

    // Asked at the moment it becomes useful: the notification is how the user sees
    // progress after leaving the app. Denying it does not block the install.
    var pendingInstall by remember { mutableStateOf<IndexApp?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingInstall?.let(viewModel::install)
        pendingInstall = null
    }
    val startInstall: (IndexApp) -> Unit = { app ->
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            pendingInstall = app
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.install(app)
        }
    }

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
                    onInstall = startInstall,
                    onAuthorize = authorize,
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
                        canInstallPackages = state.canInstallPackages,
                        onInstall = startInstall,
                        onClearError = viewModel::clearInstallError,
                        onAuthorize = authorize,
                        onOpenSource = { url -> openUrl(context, url) },
                    )
                }
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    onRefresh = { viewModel.refresh(true) },
                    onSetIndexUrl = viewModel::setIndexUrl,
                    onAuthorize = authorize,
                )
            }
        }
    }
}

private fun titleFor(route: String?): String = when {
    route == null -> "Montra"
    route.startsWith("detail") -> "Detalhes"
    route == "settings" -> "Definições"
    else -> "Montra"
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
