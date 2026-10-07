package dev.montra.data

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
enum class AutoRefresh(val minutes: Int, val label: String) {
    OFF(0, "Desligado"),
    QUARTER(15, "A cada 15 minutos"),
    HOUR(60, "A cada hora"),
    THREE_HOURS(180, "A cada 3 horas"),
    DAILY(1440, "Uma vez por dia"),
    ;

    val enabled: Boolean get() = minutes > 0

    companion object {
        val DEFAULT = HOUR
        fun of(minutes: Int): AutoRefresh = entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
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
