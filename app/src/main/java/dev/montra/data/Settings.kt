package dev.montra.data

import dev.montra.R
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.montra.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "montra")

/**
 * Com que frequência a app vai verificar se o catálogo mudou, por sua iniciativa.
 *
 * Existe porque o catálogo é um ficheiro num repositório: sem isto, a única forma
 * de saber que saiu uma versão nova era abrir a app e puxar a lista. A verificação
 * usa o ETag, portanto quando nada mudou custa um 304 e uns bytes.
 */
enum class AutoRefresh(val minutes: Int, val labelRes: Int) {
    OFF(0, R.string.off),
    QUARTER(15, R.string.text_every_15_minutes),
    HOUR(60, R.string.text_every_hour),
    THREE_HOURS(180, R.string.text_every_3_hours),
    DAILY(1440, R.string.text_once_a_day),
    ;

    val enabled: Boolean get() = minutes > 0

    companion object {
        val DEFAULT = HOUR
        fun of(minutes: Int): AutoRefresh = entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}

/**
 * Claro, escuro, ou o que o sistema estiver a usar.
 *
 * Isto não é cor dinâmica. A Montra continua a recusar o Material You — a paleta é
 * dela e não do papel de parede. Claro e escuro são outra coisa: é a luz da sala
 * onde o telemóvel está, e sobre isso quem manda é quem está a segurar nele.
 */
enum class ThemeMode(val labelRes: Int) {
    SYSTEM(R.string.text_system),
    LIGHT(R.string.text_light),
    DARK(R.string.text_dark),
    ;

    /** Traduz a escolha num "usar o tema escuro?", dado o que o sistema diz. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        val DEFAULT = SYSTEM
        fun of(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Everything the user can change. Note what is *not* here: the trusted public key
 * and the expected key id. Those are baked into the APK and never read from
 * storage, so no app-level setting can be used to bypass index verification.
 */
class Settings(private val context: Context) {

    private val indexUrlKey = stringPreferencesKey("index_url")
    private val etagKey = stringPreferencesKey("index_etag")
    private val hideRestrictedKey = booleanPreferencesKey("hide_restricted")
    private val autoRefreshKey = intPreferencesKey("auto_refresh_minutes")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val lastCheckedKey = longPreferencesKey("last_checked_at")
    private val notifiedUpdatesKey = stringPreferencesKey("notified_updates")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only")
    private val savedKey = stringSetPreferencesKey("saved_apps")
    private val preDownloadKey = booleanPreferencesKey("pre_download_updates")

    val indexUrl: Flow<String> = context.dataStore.data.map { it[indexUrlKey] ?: BuildConfig.DEFAULT_INDEX_URL }

    val etag: Flow<String?> = context.dataStore.data.map { it[etagKey] }

    suspend fun currentIndexUrl(): String = indexUrl.first()

    suspend fun currentEtag(): String? = etag.first()

    /**
     * Esconder as apps com licença restritiva.
     *
     * Por omissão mostram-se: o catálogo decidiu incluí-las e marcá-las, e esconder
     * por omissão seria tomar pelo utilizador uma decisão que é dele. Quem só quer
     * software livre desliga isto nas definições, e a escolha fica guardada.
     */
    val hideRestricted: Flow<Boolean> = context.dataStore.data.map { it[hideRestrictedKey] ?: false }

    suspend fun currentHideRestricted(): Boolean = hideRestricted.first()

    suspend fun setHideRestricted(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[hideRestrictedKey] = value }
    }

    /**
     * Só transferir por Wi-Fi.
     *
     * Ligado por omissão: o catálogo tem APKs de centenas de megabytes, e gastar os
     * dados de alguém sem lhe perguntar é um dano que não se desfaz. Quem quiser
     * transferir em dados móveis desliga isto, ou autoriza transferência a
     * transferência na própria ficha.
     */
    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { it[wifiOnlyKey] ?: true }

    suspend fun currentWifiOnly(): Boolean = wifiOnly.first()

    suspend fun setWifiOnly(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[wifiOnlyKey] = value }
    }

    /**
     * As apps guardadas para depois.
     *
     * É a única lista pessoal que uma loja sem backend pode oferecer, e é coerente com
     * não haver contas: vive no telemóvel, não sai dele, e não há nada para sincronizar
     * porque não há nada do outro lado.
     */
    val saved: Flow<Set<String>> = context.dataStore.data.map { it[savedKey] ?: emptySet() }

    /** Guarda ou tira, conforme o que estava, e devolve o que ficou. */
    suspend fun toggleSaved(appId: String): Set<String> {
        var result = emptySet<String>()
        context.dataStore.edit { prefs ->
            val current = prefs[savedKey] ?: emptySet()
            result = if (appId in current) current - appId else current + appId
            prefs[savedKey] = result
        }
        return result
    }

    /**
     * Pré-descarregar as atualizações por Wi-Fi, para o toque do utilizador só ter de
     * abrir o diálogo do sistema.
     *
     * **Desligado por omissão, e não ligado.** Isto gasta dados e espaço sem ninguém o ter
     * pedido, e uma loja não decide isso por quem a instalou. Quem liga está a trocar
     * espaço por tempo, e sabe que está.
     */
    val preDownloadUpdates: Flow<Boolean> = context.dataStore.data.map { it[preDownloadKey] ?: false }

    suspend fun currentPreDownloadUpdates(): Boolean = preDownloadUpdates.first()

    suspend fun setPreDownloadUpdates(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[preDownloadKey] = value }
    }

    val autoRefresh: Flow<AutoRefresh> = context.dataStore.data.map { prefs ->
        AutoRefresh.of(prefs[autoRefreshKey] ?: AutoRefresh.DEFAULT.minutes)
    }

    suspend fun currentAutoRefresh(): AutoRefresh = autoRefresh.first()

    suspend fun setAutoRefresh(value: AutoRefresh) {
        context.dataStore.edit { prefs -> prefs[autoRefreshKey] = value.minutes }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.of(prefs[themeModeKey])
    }

    suspend fun currentThemeMode(): ThemeMode = themeMode.first()

    suspend fun setThemeMode(value: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[themeModeKey] = value.name }
    }

    /**
     * Quando foi a última vez que falámos com o servidor e ele confirmou o
     * catálogo — incluindo um 304, que é uma confirmação. Guardado em disco para a
     * faixa poder dizer "verificado há 3 horas" logo no arranque, em vez de fingir
     * que acabámos de verificar.
     */
    val lastCheckedAt: Flow<Long?> = context.dataStore.data.map { it[lastCheckedKey] }

    suspend fun currentLastCheckedAt(): Long? = lastCheckedAt.first()

    suspend fun setLastCheckedAt(value: Long) {
        context.dataStore.edit { prefs -> prefs[lastCheckedKey] = value }
    }

    /**
     * A impressão digital do último conjunto de atualizações que gerou notificação.
     *
     * Existe para a mesma notificação não ser repetida a cada verificação: uma loja
     * que diz "3 atualizações" de hora a hora é uma loja que se desliga. Muda quando
     * aparece uma atualização nova ou quando uma é aplicada — que é exactamente
     * quando vale a pena voltar a falar.
     */
    val notifiedUpdates: Flow<String?> = context.dataStore.data.map { it[notifiedUpdatesKey] }

    suspend fun currentNotifiedUpdates(): String? = notifiedUpdates.first()

    suspend fun setNotifiedUpdates(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(notifiedUpdatesKey) else prefs[notifiedUpdatesKey] = value
        }
    }

    suspend fun setIndexUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[indexUrlKey] = url.trim()
            // A different index has a different ETag; never reuse the old one.
            prefs.remove(etagKey)
            // E "verificado há 3 minutos" era sobre outro catálogo: deixa de ser verdade.
            prefs.remove(lastCheckedKey)
            // O conjunto de atualizações era de outro catálogo.
            prefs.remove(notifiedUpdatesKey)
        }
    }

    suspend fun setEtag(etag: String?) {
        context.dataStore.edit { prefs ->
            if (etag == null) prefs.remove(etagKey) else prefs[etagKey] = etag
        }
    }

    suspend fun resetIndexUrl() {
        context.dataStore.edit { prefs ->
            prefs.remove(indexUrlKey)
            prefs.remove(etagKey)
            prefs.remove(lastCheckedKey)
            prefs.remove(notifiedUpdatesKey)
        }
    }
}
