package dev.montra.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.montra.MontraApp
import dev.montra.data.AutoRefresh
import dev.montra.data.IndexState
import dev.montra.data.ThemeMode
import dev.montra.data.model.RequirementFilter
import dev.montra.data.model.Asset
import dev.montra.data.model.IndexApp
import dev.montra.data.model.isGamesOrEmulators
import dev.montra.data.model.isIncompatibleWith
import dev.montra.install.InstallManager
import dev.montra.install.InstallRequest
import dev.montra.install.InstallService
import dev.montra.install.InstallState
import dev.montra.security.ApkVerifier
import dev.montra.util.fingerprintsMatch
import dev.montra.util.isStaleRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Filtros do separador Apps.
 *
 * O critério para um filtro existir: responde a uma pergunta que alguém faz. "Fora
 * da Play Store" responde (é a razão de ser desta loja). "Só forks" não responde a
 * nada — ninguém escolhe uma app por ser fork; escolhe-a por substituir outra, e
 * isso é o que a linha "Baseado em" mostra na ficha.
 */
enum class AppFilter(val label: String) {
    OFF_PLAY("Fora da Play Store"),
    RESTRICTED("Licença restritiva"),
}

enum class SortOrder(val label: String) {
    NAME("Nome"),
    RECENT("Atualizadas"),
    POPULAR("Populares"),
    SIZE("Tamanho"),
}

data class InstalledInfo(
    val versionCode: Long,
    val versionName: String?,
    val certSha256: String?,
)

data class AppRow(
    val app: IndexApp,
    val asset: Asset?,
    val installed: InstalledInfo?,
    val updateAvailable: Boolean,
    /** Installed copy was signed with a different key: Android will refuse the update. */
    val signatureConflict: Boolean,
    val installState: InstallState,
    val iconUrl: String?,
    val screenshotUrls: List<String>,
    val size: Long,
    /** Set when this device cannot run the app at all (minSdk above this device). */
    val incompatible: String? = null,
    /**
     * O catálogo não vê um lançamento deste projeto há mais de seis meses. Sai da
     * data que o índice publica, e não de uma bandeira gravada: uma data não
     * envelhece numa cache.
     */
    val staleRelease: Boolean = false,
) {
    val isInstalled: Boolean get() = installed != null
    val canInstall: Boolean get() = asset != null && incompatible == null && !signatureConflict
}

data class UiState(
    val index: IndexState = IndexState(),
    val rows: List<AppRow> = emptyList(),
    /** The same rows, narrowed to games and emulators: the Games tab's whole purpose. */
    val games: List<AppRow> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val sort: SortOrder = SortOrder.NAME,
    val filter: AppFilter? = null,
    val requirement: RequirementFilter? = null,
    /** Quantas apps cada filtro apanharia: um filtro sem resultados não se mostra. */
    val filterCounts: Map<AppFilter, Int> = emptyMap(),
    /** Quantas apps cada categoria tem, para a folha de filtros as poder numerar. */
    val categoryCounts: Map<String, Int> = emptyMap(),
    val hideRestricted: Boolean = false,
    val restrictedCount: Int = 0,
    val categories: List<String> = emptyList(),
    val canInstallPackages: Boolean = true,
    val installedCount: Int = 0,
    val updateCount: Int = 0,
    val autoRefresh: AutoRefresh = AutoRefresh.DEFAULT,
)

class MontraViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MontraApp).container
    private val deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /**
     * O tema vive fora do [UiState] porque quem o lê é a raiz da composição, que
     * envolve tudo o resto — o [UiState] só existe lá dentro.
     */
    private val _themeMode = MutableStateFlow(ThemeMode.DEFAULT)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val installed = MutableStateFlow<Map<String, InstalledInfo>>(emptyMap())
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(SortOrder.NAME)
    private val filter = MutableStateFlow<AppFilter?>(null)
    private val requirement = MutableStateFlow<RequirementFilter?>(null)
    private val hideRestricted = MutableStateFlow(false)
    private val autoRefresh = MutableStateFlow(AutoRefresh.DEFAULT)

    /** Corre enquanto a app está à frente; pára quando ela sai. Ver [onPause]. */
    private var ticker: Job? = null

    init {
        viewModelScope.launch { container.indexRepository.load() }
        viewModelScope.launch { container.indexRepository.state.collect { rebuild() } }
        viewModelScope.launch {
            container.installManager.states.collect { states ->
                rebuild()
                if (states.values.any { it is InstallState.Installed }) refreshInstalled()
            }
        }
        viewModelScope.launch { installed.collect { rebuild() } }
        viewModelScope.launch { query.collect { rebuild() } }
        viewModelScope.launch { category.collect { rebuild() } }
        viewModelScope.launch { sort.collect { rebuild() } }
        viewModelScope.launch { filter.collect { rebuild() } }
        viewModelScope.launch { requirement.collect { rebuild() } }
        viewModelScope.launch { autoRefresh.collect { rebuild() } }
        viewModelScope.launch {
            hideRestricted.value = container.settings.currentHideRestricted()
            rebuild()
        }
        viewModelScope.launch {
            // A preferência vive em disco: o fluxo é a fonte de verdade, não um valor
            // lido uma vez, senão mudar a cadência nas definições só pegava no
            // arranque seguinte.
            container.settings.autoRefresh.collect { autoRefresh.value = it }
        }
        viewModelScope.launch {
            container.settings.themeMode.collect { _themeMode.value = it }
        }

        // Depois de o índice carregar, e não antes.
        //
        // refreshInstalled() precisa da lista de apps para poder perguntar ao
        // sistema por cada uma. Chamá-lo no init (ou no onResume, que corre antes de
        // a rede responder) consultava uma lista vazia e o resultado era um mapa
        // vazio: nenhuma app aparecia como instalada, sem erro nenhum. Era uma
        // corrida, e por isso é que às vezes funcionava.
        viewModelScope.launch {
            container.indexRepository.state
                .map { it.apps.size }
                .distinctUntilChanged()
                .collect { count -> if (count > 0) refreshInstalled() }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    /** Case-insensitive match across the fields a person would actually search by. */
    fun searchResults(): List<AppRow> {
        val question = query.value.trim().lowercase()
        if (question.isEmpty()) return emptyList()
        return _ui.value.rows.filter { row ->
            val app = row.app
            app.name.lowercase().contains(question) ||
                app.summary.lowercase().contains(question) ||
                app.packageName.lowercase().contains(question) ||
                app.author?.lowercase()?.contains(question) == true ||
                app.accessRequirements?.methods?.any { it.contains(question) } == true ||
                app.tags.any { it.contains(question) } ||
                app.categories.any { it.contains(question) }
        }
    }

    fun setCategory(value: String?) {
        category.value = value
    }

    fun setSort(value: SortOrder) {
        sort.value = value
    }

    fun setFilter(value: AppFilter?) {
        filter.value = if (filter.value == value) null else value
    }

    fun setRequirement(value: RequirementFilter?) {
        requirement.value = value
    }

    fun setHideRestricted(value: Boolean) {
        hideRestricted.value = value
        viewModelScope.launch { container.settings.setHideRestricted(value) }
    }

    fun setAutoRefresh(value: AutoRefresh) {
        autoRefresh.value = value
        viewModelScope.launch { container.settings.setAutoRefresh(value) }
        // Escolher uma cadência e não ver nada acontecer é o mesmo que não a ter:
        // se já passou tempo suficiente para a nova cadência, verifica já.
        maybeAutoRefresh()
    }

    /** Aplicado no instante: a escolha muda a app inteira, não uma pré-visualização. */
    fun setThemeMode(value: ThemeMode) {
        _themeMode.value = value
        viewModelScope.launch { container.settings.setThemeMode(value) }
    }

    /** Puxar para atualizar: ignora o ETag, porque quem puxa quer mesmo perguntar. */
    fun refresh(force: Boolean = true) {
        viewModelScope.launch { container.indexRepository.refresh(force) }
    }

    fun setIndexUrl(url: String) {
        viewModelScope.launch {
            container.indexRepository.setIndexUrl(url)
            container.indexRepository.load()
        }
    }

    fun onResume() {
        _ui.update { it.copy(canInstallPackages = container.installManager.canRequestInstall()) }
        refreshInstalled()
        // Abrir a app já é motivo para perguntar se saiu alguma coisa: a cadência
        // mede o intervalo *mínimo* entre verificações, não uma espera obrigatória.
        maybeAutoRefresh()
        if (ticker?.isActive != true) {
            ticker = viewModelScope.launch {
                while (true) {
                    delay(TICK)
                    maybeAutoRefresh()
                }
            }
        }
    }

    fun onPause() {
        ticker?.cancel()
        ticker = null
    }

    /**
     * Verifica sozinha quando já passou a cadência escolhida. Sem `force`: com o
     * ETag, uma verificação sem novidades custa um 304.
     */
    private fun maybeAutoRefresh() {
        val interval = autoRefresh.value
        if (!interval.enabled) return
        val index = container.indexRepository.state.value
        if (index.refreshing) return
        val dueAt = (index.lastCheckedAt ?: 0L) + interval.minutes * 60_000L
        if (System.currentTimeMillis() >= dueAt) refresh(force = false)
    }

    /**
     * Hands the work to a foreground service instead of downloading in the
     * ViewModel: a 300 MB download must survive the user leaving the app, and the
     * service is also what makes the progress notification possible.
     */
    fun install(app: IndexApp) {
        val asset = app.bestAssetFor(deviceAbis) ?: run {
            container.installManager.setState(
                app.id,
                InstallState.Failed("esta app não publica um APK para a arquitetura deste dispositivo"),
            )
            return
        }
        InstallService.start(
            getApplication(),
            InstallRequest(
                appId = app.id,
                appName = app.name,
                packageName = app.packageName,
                pinnedCertSha256 = app.signingCertSha256,
                asset = asset,
            ),
        )
    }

    fun cancelInstall(appId: String) {
        InstallService.cancel(getApplication(), appId)
    }

    fun clearInstallError(appId: String) {
        container.installManager.setState(appId, InstallState.Idle)
    }

    fun refreshInstalled() {
        viewModelScope.launch {
            val apps = container.indexRepository.state.value.apps
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                apps.mapNotNull { app ->
                    val versionCode = InstallManager.installedVersionCode(context, app.packageName)
                        ?: return@mapNotNull null
                    app.packageName to InstalledInfo(
                        versionCode = versionCode,
                        versionName = InstallManager.installedVersionName(context, app.packageName),
                        certSha256 = ApkVerifier.installedSigningCertificateSha256(context, app.packageName),
                    )
                }.toMap()
            }
            installed.value = result
        }
    }

    private fun rebuild() {
        val index = container.indexRepository.state.value
        val installStates = container.installManager.states.value
        val installedNow = installed.value
        val selectedCategory = category.value
        // Uma vez por reconstrução, não uma vez por linha: o aviso de app parada é a
        // única coisa aqui que depende do relógio.
        val now = System.currentTimeMillis()

        // `rows` é a lista completa: cada separador aplica o seu próprio filtro
        // (categorias, jogos, ou a pesquisa), em vez de o ViewModel adivinhar qual
        // o ecrã que está à frente.
        val visible = if (hideRestricted.value) {
            index.apps.filterNot { it.hasRestrictedLicense() }
        } else {
            index.apps
        }
        val rows = visible
            .asSequence()
            .map { app ->
                val asset = app.bestAssetFor(deviceAbis)
                val info = installedNow[app.packageName]
                val pinnedCert = asset?.signingCertSha256 ?: app.signingCertSha256
                val conflict = info?.certSha256 != null && pinnedCert != null &&
                    !fingerprintsMatch(pinnedCert, info.certSha256)
                val targetVersion = asset?.versionCode ?: app.release?.versionCode
                // Um projeto que publica splits atribui versionCodes diferentes por
                // ABI (o Obtainium: 2356 no universal, 23563 no arm64). Quem tem o
                // universal instalado não deve ver "Atualizar" para a mesma versão,
                // portanto o nome da versão manda quando existe.
                val targetName = asset?.versionName ?: app.release?.versionName
                val sameVersionName = info?.versionName != null && targetName != null &&
                    info.versionName == targetName
                AppRow(
                    app = app,
                    asset = asset,
                    installed = info,
                    updateAvailable = info != null && targetVersion != null &&
                        targetVersion > info.versionCode && !sameVersionName,
                    signatureConflict = conflict,
                    installState = installStates[app.id] ?: InstallState.Idle,
                    iconUrl = app.icon?.let { container.indexRepository.mediaUrl(it) },
                    screenshotUrls = app.screenshots.map { container.indexRepository.mediaUrl(it) },
                    incompatible = asset?.takeIf { it.isIncompatibleWith(Build.VERSION.SDK_INT) }?.let {
                        "Precisa de Android API ${it.minSdk}; este dispositivo tem ${Build.VERSION.SDK_INT}"
                    },
                    size = asset?.size ?: app.artifact?.size ?: 0L,
                    staleRelease = isStaleRelease(app.release?.publishedAt, now),
                )
            }
            .sortedWith(
                when (sort.value) {
                    SortOrder.NAME -> compareBy { it.app.name.lowercase() }
                    SortOrder.RECENT -> compareByDescending { it.app.release?.publishedAt ?: "" }
                    SortOrder.POPULAR -> compareByDescending { it.app.downloadCount ?: 0L }
                    SortOrder.SIZE -> compareBy { it.size }
                },
            )
            .toList()

        // Uma categoria que não tem nada para mostrar não merece um chip — e a
        // contagem é o que diz isso sem ter de se carregar no filtro para descobrir.
        val categoryCounts = visible
            .flatMap { app -> app.categories }
            .groupingBy { it }
            .eachCount()

        _ui.update {
            it.copy(
                index = index,
                rows = rows,
                games = rows.filter { it.app.isGamesOrEmulators() },
                query = query.value,
                category = selectedCategory,
                sort = sort.value,
                filter = filter.value,
                requirement = requirement.value,
                filterCounts = mapOf(
                    AppFilter.OFF_PLAY to visible.count { it.playStore?.present == false },
                    AppFilter.RESTRICTED to visible.count { it.hasRestrictedLicense() },
                ),
                hideRestricted = hideRestricted.value,
                restrictedCount = index.apps.count { it.hasRestrictedLicense() },
                categories = categoryCounts.entries.sortedByDescending { e -> e.value }.map { e -> e.key },
                categoryCounts = categoryCounts,
                canInstallPackages = container.installManager.canRequestInstall(),
                installedCount = installedNow.size,
                updateCount = rows.count { row -> row.updateAvailable },
                autoRefresh = autoRefresh.value,
            )
        }
    }

    private companion object {
        /** De quanto em quanto tempo vale a pena perguntar se já é altura de verificar. */
        const val TICK = 60_000L
    }
}
