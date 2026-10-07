package dev.openshelf.data

import android.content.Context
import dev.openshelf.data.model.IndexApp
import dev.openshelf.data.model.IndexFile
import dev.openshelf.data.model.IndexJson
import dev.openshelf.security.SignatureCheck
import dev.openshelf.security.TrustStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class IndexOrigin { BUNDLED, CACHED, NETWORK }

data class IndexState(
    val loading: Boolean = true,
    val apps: List<IndexApp> = emptyList(),
    val generatedAt: String? = null,
    val origin: IndexOrigin? = null,
    val signatureValid: Boolean = false,
    val keyId: String? = null,
    val indexUrl: String = "",
    val refreshing: Boolean = false,
    /** Set when a refresh failed; the previously verified catalogue stays in use. */
    val error: String? = null,
    /** Set when something *was* served but rejected: always worth showing loudly. */
    val rejectedMessage: String? = null,
)

/**
 * The heart of the app: obtain a catalogue, verify it, and only then use it.
 *
 * Failure policy is deliberately asymmetric. A *network* problem is routine: keep
 * serving the last verified catalogue and show a soft error. A *verification*
 * failure is an attack signal or a broken build: never replace what we have, and
 * say so on screen. That distinction is the whole security model in one class.
 */
class IndexRepository(
    private val context: Context,
    private val source: IndexSource,
    private val settings: Settings,
    private val trust: TrustStore,
) {
    private val _state = MutableStateFlow(IndexState())
    val state: StateFlow<IndexState> = _state.asStateFlow()

    private val refreshMutex = Mutex()

    suspend fun load() {
        val url = settings.currentIndexUrl()
        _state.update { it.copy(loading = true, indexUrl = url) }

        // 1. last verified copy: verified again on every launch, cheap and paranoid
        val cached = source.cached()
        if (cached != null) {
            when (val check = accept(cached)) {
                is Check.Accepted -> {
                    publish(check, IndexOrigin.CACHED, url)
                    _state.update { it.copy(loading = false) }
                }
                is Check.Rejected -> {
                    // The cache does not verify any more: drop it rather than use it.
                    source.cachedIndexFile.delete()
                    source.cachedSignatureFile.delete()
                    _state.update { it.copy(rejectedMessage = check.message) }
                }
            }
        }

        // 2. bundled snapshot, so there is always something to show
        if (_state.value.apps.isEmpty()) {
            when (val check = accept(source.bundled())) {
                is Check.Accepted -> publish(check, IndexOrigin.BUNDLED, url)
                is Check.Rejected -> _state.update { it.copy(rejectedMessage = check.message) }
            }
            _state.update { it.copy(loading = false) }
        }

        // 3. and then try to get something newer
        refresh(force = false)
    }

    suspend fun refresh(force: Boolean) = refreshMutex.withLock {
        val url = settings.currentIndexUrl()
        _state.update { it.copy(refreshing = true, error = null, indexUrl = url) }
        try {
            val etag = if (force) null else settings.currentEtag()
            val payload = source.fetchRemote(url, etag)
            if (payload == null) {
                _state.update { it.copy(refreshing = false, error = null) }
                return@withLock
            }
            when (val check = accept(payload)) {
                is Check.Accepted -> {
                    source.storeVerified(payload)
                    settings.setEtag(payload.etag)
                    publish(check, IndexOrigin.NETWORK, url)
                    _state.update { it.copy(refreshing = false, loading = false, error = null) }
                }
                is Check.Rejected -> {
                    // Keep serving what we have. Tell the user exactly why.
                    _state.update {
                        it.copy(
                            refreshing = false,
                            loading = false,
                            rejectedMessage = check.message,
                            error = "O índice recebido foi recusado. A mostrar a última versão verificada.",
                        )
                    }
                }
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    refreshing = false,
                    loading = false,
                    error = error.message ?: error::class.simpleName ?: "falha ao atualizar",
                )
            }
        }
    }

    private sealed interface Check {
        data class Accepted(val index: IndexFile, val keyId: String) : Check
        data class Rejected(val message: String) : Check
    }

    private fun accept(payload: IndexSource.Payload): Check {
        // Parse first (cheap) only to read the declared key id, then verify bytes.
        val declaredKeyId = try {
            IndexJson.decodeFromString(IndexFile.serializer(), payload.bytes.decodeToString()).signingKeyId
        } catch (_: Exception) {
            null
        }
        return when (val check = trust.verify(payload.bytes, payload.signature, declaredKeyId)) {
            is SignatureCheck.Invalid -> Check.Rejected("assinatura do índice inválida — ${check.reason}")
            is SignatureCheck.KeyMismatch -> Check.Rejected(
                "o índice foi assinado por uma chave diferente da que esta app conhece " +
                    "(índice: ${check.declared}, app: ${check.bundled}). Atualiza a app antes de confiar neste índice.",
            )
            is SignatureCheck.Valid -> {
                val index = try {
                    IndexJson.decodeFromString(IndexFile.serializer(), payload.bytes.decodeToString())
                } catch (error: Exception) {
                    return Check.Rejected("índice ilegível depois de verificado: ${error.message}")
                }
                Check.Accepted(index, check.keyId)
            }
        }
    }

    private fun publish(accepted: Check.Accepted, origin: IndexOrigin, url: String) {
        _state.update {
            it.copy(
                loading = false,
                apps = accepted.index.apps,
                generatedAt = accepted.index.generatedAt,
                origin = origin,
                signatureValid = true,
                keyId = accepted.keyId,
                indexUrl = url,
                rejectedMessage = null,
            )
        }
    }

    /** For the UI: turn a relative media path from the index into a fetchable URL. */
    fun mediaUrl(relative: String): String = source.resolveAgainst(_state.value.indexUrl, relative)

    suspend fun setIndexUrl(url: String): Unit = withContext(Dispatchers.IO) {
        settings.setIndexUrl(url)
        source.cachedIndexFile.delete()
        source.cachedSignatureFile.delete()
    }
}
