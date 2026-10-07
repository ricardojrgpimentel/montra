package dev.montra.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.montra.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "montra")

/**
 * Everything the user can change. Note what is *not* here: the trusted public key
 * and the expected key id. Those are baked into the APK and never read from
 * storage, so no app-level setting can be used to bypass index verification.
 */
class Settings(private val context: Context) {

    private val indexUrlKey = stringPreferencesKey("index_url")
    private val etagKey = stringPreferencesKey("index_etag")
    private val hideRestrictedKey = booleanPreferencesKey("hide_restricted")

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

    suspend fun setIndexUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[indexUrlKey] = url.trim()
            // A different index has a different ETag; never reuse the old one.
            prefs.remove(etagKey)
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
        }
    }
}
