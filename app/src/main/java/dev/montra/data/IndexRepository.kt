package dev.montra.data

import android.content.Context
import dev.montra.data.model.IndexApp
import dev.montra.data.model.IndexFile
import dev.montra.data.model.IndexJson
import dev.montra.security.SignatureCheck
import dev.montra.util.Log
import dev.montra.security.TrustStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class IndexOrigin { BUNDLED, CACHED, NETWORK }

/** Como terminou a última verificação, para a faixa poder dizê-lo sem inventar. */
enum class RefreshOutcome {
    /** Ainda não houve verificação nesta sessão. */
    NONE,

    /** O servidor serviu um catálogo diferente do que já tínhamos. */
    UPDATED,

    /** O servidor confirmou que o que temos é o mais recente (304, ou o mesmo `generatedAt`). */
    CURRENT,
}

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
    /** Epoch millis of the last round trip that confirmed the catalogue. */
    val lastCheckedAt: Long? = null,
    val outcome: RefreshOutcome = RefreshOutcome.NONE,
    /** Quando terminou a verificação, para a faixa poder mostrar o resultado só uns segundos. */
    val outcomeAt: Long = 0L,
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
        Log.i("a carregar o catálogo de $url (chave de confiança ${trust.keyId})")
        _state.update {
            it.copy(loading = true, indexUrl = url, lastCheckedAt = settings.currentLastCheckedAt())
        }

        // 1. last verified copy: verified again on every launch, cheap and paranoid
        val cached = source.cached()
        if (cached != null) {
            when (val check = accept(cached)) {
                is Check.Accepted -> {
                    Log.i("cache em disco verificada: ${check.index.apps.size} apps")
                    publish(check, IndexOrigin.CACHED, url)
                    _state.update { it.copy(loading = false) }
                }
                is Check.Rejected -> {
                    // The cache does not verify any more: drop it rather than use it.
                    Log.e("cache em disco recusada: ${check.message}")
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
                is Check.Rejected -> {
                    Log.e("snapshot incluído na app recusado: ${check.message}")
                    _state.update { it.copy(rejectedMessage = check.message) }
                }
            }
            Log.i("a mostrar o snapshot incluído na app: ${_state.value.apps.size} apps")
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
                Log.i("o servidor respondeu 304: o catálogo em cache já é o mais recente")
                confirm(RefreshOutcome.CURRENT)
                return@withLock
            }
            Log.d("recebidos ${payload.bytes.size} bytes de índice; a verificar a assinatura")
            when (val check = accept(payload)) {
                is Check.Accepted -> {
                    // O `generatedAt` é a versão do índice: se não mexeu, o que
                    // recebemos é o mesmo catálogo e vale a pena dizê-lo em vez de
                    // sugerir que houve novidades.
                    val previous = _state.value.generatedAt
                    Log.i(
                        "assinatura válida (chave ${check.keyId}); ${check.index.apps.size} apps, " +
                            "gerado em ${check.index.generatedAt}",
                    )
                    source.storeVerified(payload)
                    settings.setEtag(payload.etag)
                    publish(check, IndexOrigin.NETWORK, url)
                    _state.update { it.copy(refreshing = false, loading = false, error = null) }
                    confirm(
                        if (previous != null && previous == check.index.generatedAt) {
                            RefreshOutcome.CURRENT
                        } else {
                            RefreshOutcome.UPDATED
                        },
                    )
                }
                is Check.Rejected -> {
                    Log.e("índice recebido RECUSADO: ${check.message}")
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
            // A causa vai na mensagem, não só no throwable: muitas ROMs limpam o
            // stack trace do buffer, e quem depura fica sem saber o que falhou.
            val cause = error.message?.takeIf { it.isNotBlank() } ?: "(sem mensagem)"
            Log.w("falha a atualizar o catálogo de $url — ${error::class.java.simpleName}: $cause", error)
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

    /**
     * Regista que o servidor confirmou o catálogo — um 304 incluído. A pergunta era
     * "isto mudou?" e uma resposta negativa também é uma resposta: é isso que
     * permite à faixa dizer "verificado há 4 minutos" sem estar a inventar.
     */
    private suspend fun confirm(outcome: RefreshOutcome) {
        val now = System.currentTimeMillis()
        settings.setLastCheckedAt(now)
        _state.update {
            it.copy(
                refreshing = false,
                loading = false,
                error = null,
                lastCheckedAt = now,
                outcome = outcome,
                outcomeAt = now,
            )
        }
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
        _state.update {
            it.copy(lastCheckedAt = null, outcome = RefreshOutcome.NONE, apps = emptyList())
        }
    }
}
