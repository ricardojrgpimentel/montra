package dev.montra.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The root of the catalogue. Everything the app knows about available apps comes
 * from one JSON file plus its detached signature.
 */
@Serializable
data class IndexFile(
    val schemaVersion: Int,
    val generatedAt: String,
    val generator: String? = null,
    val signingKeyId: String? = null,
    val apps: List<IndexApp> = emptyList(),
)

@Serializable
data class IndexApp(
    val id: String,
    val name: String,
    val summary: String,
    val description: Map<String, String> = emptyMap(),
    val packageName: String,
    val license: String,
    /** Presente quando a licença não é livre: o que ela permite e o que não permite. */
    val licenseNote: Map<String, String> = emptyMap(),
    val sourceCode: String,
    val author: String? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val status: String = "active",
    val antiFeatures: List<String> = emptyList(),
    val links: Map<String, String> = emptyMap(),
    val playStore: PlayStore? = null,
    val addedAt: String? = null,
    /** Path relative to the index URL, e.g. "icons/newpipe.png". Null means monogram. */
    val icon: String? = null,
    val screenshots: List<String> = emptyList(),
    val downloadCount: Long? = null,
    val signingCertSha256: String? = null,
    val resolvedAt: String? = null,
    val release: Release? = null,
    /** Convenience pointer chosen by the index builder; [bestAssetFor] is smarter. */
    val artifact: ArtifactRef? = null,
    val warnings: List<String> = emptyList(),
) {
    val isActive: Boolean get() = status == "active"
    val availableOnPlayStore: Boolean get() = playStore?.present == true

    /** Código público, mas com restrições de uso que o utilizador tem o direito de saber. */
    fun hasRestrictedLicense(): Boolean = antiFeatures.contains("restrictedLicense")

    fun licenseNoteFor(languageTag: String?): String? {
        if (licenseNote.isEmpty()) return null
        if (languageTag != null) {
            licenseNote[languageTag]?.let { return it }
            licenseNote[languageTag.substringBefore('-')]?.let { return it }
        }
        return licenseNote["en"] ?: licenseNote.values.firstOrNull()
    }

    fun descriptionFor(languageTag: String?): String? {
        if (languageTag != null) {
            description[languageTag]?.let { return it }
            val base = languageTag.substringBefore('-')
            description[base]?.let { return it }
        }
        return description["en"] ?: description.values.firstOrNull()
    }

    /**
     * Pick the asset that matches this device.
     *
     * Order: the device's own ABIs in the order the platform reports them (64-bit
     * before 32-bit, so an arm64 phone prefers the arm64 split over the 32-bit
     * one), then a universal APK. Nothing else.
     *
     * Deliberately no "closest match" fallback: offering an x86_64 APK to an
     * arm64 device because it happens to contain no native libraries is the kind
     * of cleverness that produces an install the system then rejects. Saying "no
     * build for your device" is a better answer than a broken one.
     */
    fun bestAssetFor(deviceAbis: List<String>): Asset? {
        val assets = release?.assets.orEmpty()
        if (assets.isEmpty()) return null
        for (abi in deviceAbis) {
            assets.firstOrNull { it.abi == abi }?.let { return it }
        }
        return assets.firstOrNull { it.abi == "universal" }
    }
}

@Serializable
data class Release(
    val versionName: String,
    val versionCode: Int,
    val tag: String,
    val publishedAt: String? = null,
    val releaseUrl: String? = null,
    val changelog: String? = null,
    val assets: List<Asset> = emptyList(),
)

@Serializable
data class Asset(
    val abi: String,
    val url: String,
    val sha256: String,
    val size: Long,
    val signingCertSha256: String? = null,
    val versionCode: Int? = null,
    val versionName: String? = null,
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val nativeAbis: List<String> = emptyList(),
    val verifiedAt: String? = null,
)

/**
 * True when this APK declares a minSdk above the device's API level, so the
 * system would refuse to install it (or worse, install it and crash).
 *
 * A null minSdk means the index could not read it: it is treated as compatible,
 * because refusing to offer an app over missing metadata would be worse than the
 * system's own install-time check, which still runs.
 */
fun Asset.isIncompatibleWith(deviceSdk: Int): Boolean = minSdk != null && minSdk > deviceSdk

@Serializable
data class ArtifactRef(
    val url: String,
    val sha256: String,
    val size: Long,
    val abi: String,
)

@Serializable
data class PlayStore(
    val present: Boolean,
    val url: String? = null,
)

/**
 * Lenient on purpose: an index generated by a newer builder must not crash an
 * older client. Unknown fields are ignored, missing optional fields get defaults,
 * and the signature is what provides integrity — not the parser.
 */
val IndexJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
    explicitNulls = false
    encodeDefaults = false
}

/** Human-readable explanation for the anti-feature codes the index can carry. */
fun antiFeatureLabel(code: String): String = when (code) {
    "nonFreeNet" -> "Depende de um serviço de rede não livre"
    "nonFreeAssets" -> "Inclui recursos não livres"
    "nonFreeAdd" -> "Recomenda extras não livres"
    "tracking" -> "Rastreia o utilizador"
    "ads" -> "Contém publicidade"
    "knownVuln" -> "Reduz a segurança do dispositivo"
    "noSourceSince" -> "Deixou de publicar código-fonte"
    "disabledAlgorithm" -> "Assinado com algoritmo descontinuado"
    "upstreamNonFree" -> "Deriva de software não livre"
    "restrictedLicense" -> "Licença restritiva: código público, mas com limitações de uso"
    else -> code
}

/** Portuguese labels for the fixed category taxonomy the index uses. */
fun categoryLabel(code: String): String = when (code) {
    "ai" -> "IA"
    "browser" -> "Navegador"
    "communication" -> "Comunicação"
    "development" -> "Desenvolvimento"
    "education" -> "Educação"
    "finance" -> "Finanças"
    "games" -> "Jogos"
    "graphics" -> "Imagem"
    "health" -> "Saúde"
    "maps" -> "Mapas"
    "media" -> "Média"
    "multimedia" -> "Multimédia"
    "navigation" -> "Navegação"
    "notes" -> "Notas"
    "privacy" -> "Privacidade"
    "productivity" -> "Produtividade"
    "reading" -> "Leitura"
    "security" -> "Segurança"
    "store" -> "Lojas"
    "system" -> "Sistema"
    "tools" -> "Ferramentas"
    "utilities" -> "Utilidades"
    "weather" -> "Meteorologia"
    else -> code
}

/** Human label for the app's lifecycle status. */
fun statusLabel(status: String): String = when (status) {
    "active" -> "mantida"
    "unmaintained" -> "sem manutenção"
    "archived" -> "arquivada"
    "deprecated" -> "descontinuada"
    else -> status
}

/**
 * Games and emulators share one tab, because to the person browsing they answer the
 * same question: "what can I play on this?". Keeping them as categories in the data
 * means the tab can change without touching the index.
 */
fun IndexApp.isGamesOrEmulators(): Boolean =
    categories.any { it == "games" || it == "emulators" }
