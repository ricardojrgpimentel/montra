package dev.montra.data

import dev.montra.R
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    suspend fun setIndexUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[indexUrlKey] = url.trim()
            // A different index has a different ETag; never reuse the old one.
            prefs.remove(etagKey)
            // E "verificado há 3 minutos" era sobre outro catálogo: deixa de ser verdade.
            prefs.remove(lastCheckedKey)
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
        }
    }
}
