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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.montra.data.model.IndexApp
import dev.montra.install.InstallManager
import dev.montra.install.InstallRequest
import dev.montra.ui.AppDetailScreen
import dev.montra.ui.AppsScreen
import dev.montra.ui.GamesScreen
import dev.montra.ui.MontraViewModel
import dev.montra.ui.SearchScreen
import dev.montra.ui.SettingsScreen
import dev.montra.ui.theme.MontraTheme
import dev.montra.ui.theme.Space
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
        // Tapping the progress notification while the app is already open should take
        // the user to that app, not restart the activity (singleTop).
        pendingAppId.value = intent.getStringExtra(InstallRequest.EXTRA_APP_ID)
    }

    override fun onResume() {
        super.onResume()
        // The "install unknown apps" permission and the set of installed apps can both
        // change while we are away — we send the user to Settings for exactly that.
        viewModel.onResume()
    }
}

/**
 * The four destinations of the footer, in the order they appear.
 *
 * Apps is first and is where the app lands: this is a catalogue of tools where games
 * are a growing minority, so landing on games would be a statement the data does not
 * make yet. If that ever flips, swapping two lines here is the whole change.
 */
private enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    APPS("apps", "Apps", Icons.Outlined.Apps, Icons.Filled.Apps),
    GAMES("games", "Jogos", Icons.Outlined.SportsEsports, Icons.Filled.SportsEsports),
    SEARCH("search", "Procurar", Icons.Outlined.Search, Icons.Filled.Search),
    SETTINGS("settings", "Definições", Icons.Outlined.Settings, Icons.Filled.Settings),
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
    val route = backStackEntry?.destination?.route ?: Tab.APPS.route
    val isDetail = route.startsWith("detail")
    // O route é o *padrão* ("detail/{id}"), não o caminho resolvido: o id tem de vir
    // dos argumentos do back stack, senão o título cai sempre no fallback.
    val detailedAppId = backStackEntry?.arguments?.getString("id")
    val detailedApp = detailedAppId?.let { id -> state.rows.firstOrNull { it.app.id == id } }

    // Deep link from a download notification; waits for the catalogue if it is loading.
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

    // Asked when it becomes useful: the notification is how progress stays visible
    // after leaving the app. Denying it does not block the install.
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
            // The search tab owns its own field; a title above it would be a line of
            // chrome saying what the field already says.
            if (route != Tab.SEARCH.route) {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                isDetail -> detailedApp?.app?.name ?: "Detalhes"
                                route == Tab.SETTINGS.route -> "Definições"
                                route == Tab.GAMES.route -> "Jogos e emuladores"
                                else -> "Montra"
                            },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        if (isDetail) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                )
                            }
                        }
                    },
                    actions = {
                        if (route == Tab.APPS.route) {
                            IconButton(onClick = { viewModel.refresh(true) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar catálogo")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            // Play hides the footer on an app page, and so do we: the page has its own
            // primary action and its own way back.
            if (!isDetail) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.APPS.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.APPS.route) {
                AppsScreen(
                    state = state,
                    onCategory = viewModel::setCategory,
                    onSort = viewModel::setSort,
                    onOpenSearch = { navController.navigate(Tab.SEARCH.route) },
                    onOpen = { app -> navController.navigate("detail/${app.id}") },
                    onInstall = startInstall,
                    onAuthorize = authorize,
                )
            }
            composable(Tab.GAMES.route) {
                GamesScreen(
                    state = state,
                    onOpenSearch = { navController.navigate(Tab.SEARCH.route) },
                    onOpen = { app -> navController.navigate("detail/${app.id}") },
                    onInstall = startInstall,
                    onAuthorize = authorize,
                )
            }
            composable(Tab.SEARCH.route) {
                SearchScreen(
                    state = state,
                    query = state.query,
                    results = viewModel.searchResults(),
                    onQuery = viewModel::setQuery,
                    onClear = viewModel::clearQuery,
                    onOpen = { app -> navController.navigate("detail/${app.id}") },
                    onInstall = startInstall,
                    onAuthorize = authorize,
                )
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    state = state,
                    onRefresh = { viewModel.refresh(true) },
                    onSetIndexUrl = viewModel::setIndexUrl,
                    onAuthorize = authorize,
                )
            }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id")
                val row = state.rows.firstOrNull { it.app.id == id }
                if (row == null) {
                    Column(modifier = Modifier.fillMaxSize().padding(Space.xl)) {
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
        }
    }
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
