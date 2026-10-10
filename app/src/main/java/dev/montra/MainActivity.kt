package dev.montra

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.montra.data.AppLanguage
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.foundation.isSystemInDarkTheme
import dev.montra.data.ThemeMode
import dev.montra.data.model.IndexApp
import dev.montra.install.InstallManager
import dev.montra.install.InstallRequest
import dev.montra.ui.AppDetailScreen
import dev.montra.ui.AppsScreen
import dev.montra.ui.GamesScreen
import dev.montra.ui.MontraViewModel
import dev.montra.ui.SearchScreen
import dev.montra.ui.SettingsScreen
import dev.montra.ui.components.MontraBrand
import dev.montra.ui.theme.MontraTheme
import dev.montra.ui.theme.Motion
import dev.montra.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : AppCompatActivity() {

    private val viewModel: MontraViewModel by viewModels()

    /** Set when the app was opened from a download notification. */
    private val pendingAppId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingAppId.value = intent?.getStringExtra(InstallRequest.EXTRA_APP_ID)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            MontraTheme(darkTheme = themeMode.isDark(isSystemInDarkTheme())) {
                MontraRoot(
                    viewModel = viewModel,
                    themeMode = themeMode,
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

    override fun onPause() {
        super.onPause()
        // A verificação automática só corre com a app à frente: uma loja não anda a
        // bater no GitHub em segundo plano.
        viewModel.onPause()
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
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    APPS("apps", R.string.text_apps_2, Icons.Outlined.Apps, Icons.Filled.Apps),
    GAMES("games", R.string.games, Icons.Outlined.SportsEsports, Icons.Filled.SportsEsports),
    SEARCH("search", R.string.search, Icons.Outlined.Search, Icons.Filled.Search),
    SETTINGS("settings", R.string.text_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MontraRoot(
    viewModel: MontraViewModel,
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    pendingAppId: StateFlow<String?> = MutableStateFlow(null),
    onAppIdConsumed: () -> Unit = {},
) {
    val strings = LocalContext.current
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

    // Com o teclado aberto, a barra de navegação ficava debaixo dele: uma barra que
    // se vê a fingir que está lá e que não se pode carregar. Sai de cena enquanto o
    // teclado estiver à frente, como faz a loja de onde isto veio.
    val keyboardUp = WindowInsets.isImeVisible

    // Asked when it becomes useful: the notification is how progress stays visible
    // after leaving the app. Denying it does not block the install.
    var pendingInstallId by rememberSaveable { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        state.rows.firstOrNull { it.app.id == pendingInstallId }?.app?.let(viewModel::install)
        pendingInstallId = null
    }
    val startInstall: (IndexApp) -> Unit = { app ->
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            pendingInstallId = app.id
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
                        if (!isDetail && route == Tab.APPS.route) {
                            MontraBrand()
                        } else {
                            Text(
                                when {
                                    isDetail -> detailedApp?.app?.name ?: strings.getString(R.string.details)
                                    route == Tab.SETTINGS.route -> strings.getString(R.string.text_settings)
                                    route == Tab.GAMES.route -> strings.getString(R.string.text_games_and_emulators_2)
                                    else -> "Montra"
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        if (isDetail) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = strings.getString(R.string.back),
                                )
                            }
                        }
                    },
                    actions = {
                        // Nada aqui de propósito. O refresh deixou de ser um ícone
                        // órfão no canto: puxa-se a lista, e o estado dele vive na
                        // faixa do catálogo, onde se vê se está a acontecer alguma
                        // coisa. A opção explícita ficou nas definições.
                    },
                )
            }
        },
        bottomBar = {
            // Play hides the footer on an app page, and so do we: the page has its own
            // primary action and its own way back.
            if (!isDetail && !keyboardUp) {
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
                            label = { Text(strings.getString(tab.labelRes)) },
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
            // Trocar de separador é uma substituição, não uma ida a algum lado: uma
            // dissolvida curta. Abrir uma app é uma ida, e essa desliza.
            enterTransition = { fadeIn(Motion.standard()) },
            exitTransition = { fadeOut(Motion.quick()) },
            popEnterTransition = { fadeIn(Motion.standard()) },
            popExitTransition = { fadeOut(Motion.quick()) },
        ) {
            composable(Tab.APPS.route) {
                AppsScreen(
                    state = state,
                    onCategory = viewModel::setCategory,
                    onFilter = viewModel::setFilter,
                    onRequirement = viewModel::setRequirement,
                    onSort = viewModel::setSort,
                    onRefresh = { viewModel.refresh(true) },
                    onOpenSearch = { navController.navigate(Tab.SEARCH.route) },
                    onOpen = { app -> navController.navigate("detail/${app.id}") },
                    onInstall = startInstall,
                    onAuthorize = authorize,
                )
            }
            composable(Tab.GAMES.route) {
                GamesScreen(
                    state = state,
                    onRefresh = { viewModel.refresh(true) },
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
                    onRefresh = { viewModel.refresh(true) },
                    onOpen = { app -> navController.navigate("detail/${app.id}") },
                    onInstall = startInstall,
                    onAuthorize = authorize,
                )
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    state = state,
                    themeMode = themeMode,
                    onRefresh = { viewModel.refresh(true) },
                    onSetIndexUrl = viewModel::setIndexUrl,
                    onAuthorize = authorize,
                    onHideRestricted = viewModel::setHideRestricted,
                    onAutoRefresh = viewModel::setAutoRefresh,
                    onThemeMode = viewModel::setThemeMode,
                    language = AppLanguage.current(),
                    onLanguage = { it.apply() },
                    onOpenUrl = { url -> openUrl(context, url) },
                )
            }
            composable(
                route = "detail/{id}",
                enterTransition = {
                    slideInHorizontally(animationSpec = Motion.standard()) { it / 4 } +
                        fadeIn(Motion.standard())
                },
                popExitTransition = {
                    slideOutHorizontally(animationSpec = Motion.standard()) { it / 4 } +
                        fadeOut(Motion.quick())
                },
            ) { entry ->
                val id = entry.arguments?.getString("id")
                val row = state.rows.firstOrNull { it.app.id == id }
                if (row == null) {
                    Column(modifier = Modifier.fillMaxSize().padding(Space.xl)) {
                        Text(strings.getString(R.string.text_this_app_is_no_longer_in_the))
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
                        onOpenApp = { otherId -> navController.navigate("detail/$otherId") },
                    )
                }
            }
        }
    }
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
