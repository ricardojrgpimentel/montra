package dev.montra.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.montra.MontraApp
import dev.montra.data.IndexState
import dev.montra.data.model.Asset
import dev.montra.data.model.IndexApp
import dev.montra.data.model.isIncompatibleWith
import dev.montra.install.InstallManager
import dev.montra.install.InstallRequest
import dev.montra.install.InstallService
import dev.montra.install.InstallState
import dev.montra.security.ApkVerifier
import dev.montra.util.fingerprintsMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
) {
    val isInstalled: Boolean get() = installed != null
    val canInstall: Boolean get() = asset != null && incompatible == null && !signatureConflict
}

data class UiState(
    val index: IndexState = IndexState(),
    val rows: List<AppRow> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val sort: SortOrder = SortOrder.NAME,
    val categories: List<String> = emptyList(),
    val canInstallPackages: Boolean = true,
    val installedCount: Int = 0,
    val updateCount: Int = 0,
)

class MontraViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MontraApp).container
    private val deviceAbis: List<String> = Build.SUPPORTED_ABIS.toList()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val installed = MutableStateFlow<Map<String, InstalledInfo>>(emptyMap())
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(SortOrder.NAME)

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
        refreshInstalled()
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setCategory(value: String?) {
        category.value = value
    }

    fun setSort(value: SortOrder) {
        sort.value = value
    }

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
        val question = query.value.trim().lowercase()
        val selectedCategory = category.value

        val rows = index.apps
            .asSequence()
            .filter { app -> selectedCategory == null || app.categories.contains(selectedCategory) }
            .filter { app ->
                question.isEmpty() ||
                    app.name.lowercase().contains(question) ||
                    app.summary.lowercase().contains(question) ||
                    app.packageName.lowercase().contains(question) ||
                    (app.author?.lowercase()?.contains(question) == true) ||
                    app.tags.any { it.contains(question) }
            }
            .map { app ->
                val asset = app.bestAssetFor(deviceAbis)
                val info = installedNow[app.packageName]
                val pinnedCert = asset?.signingCertSha256 ?: app.signingCertSha256
                val conflict = info?.certSha256 != null && pinnedCert != null &&
                    !fingerprintsMatch(pinnedCert, info.certSha256)
                val targetVersion = asset?.versionCode ?: app.release?.versionCode
                AppRow(
                    app = app,
                    asset = asset,
                    installed = info,
                    updateAvailable = info != null && targetVersion != null && targetVersion > info.versionCode,
                    signatureConflict = conflict,
                    installState = installStates[app.id] ?: InstallState.Idle,
                    iconUrl = app.icon?.let { container.indexRepository.mediaUrl(it) },
                    screenshotUrls = app.screenshots.map { container.indexRepository.mediaUrl(it) },
                    incompatible = asset?.takeIf { it.isIncompatibleWith(Build.VERSION.SDK_INT) }?.let {
                        "Precisa de Android API ${it.minSdk}; este dispositivo tem ${Build.VERSION.SDK_INT}"
                    },
                    size = asset?.size ?: app.artifact?.size ?: 0L,
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

        _ui.update {
            it.copy(
                index = index,
                rows = rows,
                query = query.value,
                category = selectedCategory,
                sort = sort.value,
                categories = index.apps.flatMap { app -> app.categories }.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { e -> e.value }.map { e -> e.key },
                canInstallPackages = container.installManager.canRequestInstall(),
                installedCount = installedNow.size,
                updateCount = rows.count { row -> row.updateAvailable },
            )
        }
    }
}
