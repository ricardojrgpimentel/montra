package dev.montra.data.model

import dev.montra.R
import dev.montra.util.UiText
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
    val summaryTranslations: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
    val packageName: String,
    val license: String,
    /** Presente quando a licença não é livre: o que ela permite e o que não permite. */
    val licenseNote: Map<String, String> = emptyMap(),
    val sourceCode: String,
    val author: String? = null,
    /** Parentesco declarado pelo projeto de origem. Nunca um palpite. */
    val forkOf: ForkOf? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val status: String = "active",
    val antiFeatures: List<String> = emptyList(),
    val accessRequirements: AccessRequirements? = null,
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

    /** Exact locale, less specific tags, English, then legacy text. */
    fun summaryFor(languageTag: String?): String = summaryTranslations.localized(languageTag) { summary }
        ?: summary

    fun descriptionFor(languageTag: String?): String? = description.localized(languageTag)

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

private fun Map<String, String>.localized(languageTag: String?, englishFallback: () -> String? = { null }): String? {
    fun text(tag: String): String? = entries.firstOrNull { it.key.equals(tag, ignoreCase = true) }
        ?.value?.takeIf { it.isNotBlank() }
    var tag = languageTag?.replace('_', '-')
    while (!tag.isNullOrEmpty()) {
        text(tag)?.let { return it }
        tag = if ('-' in tag) tag.substringBeforeLast('-') else null
    }
    return text("en") ?: englishFallback()?.takeIf { it.isNotBlank() }
        ?: values.firstOrNull { it.isNotBlank() }
}

@Serializable
data class ForkOf(
    val name: String,
    val repo: String? = null,
    /** id no catálogo, quando o original também está indexado: permite abrir a ficha dele. */
    val appId: String? = null,
    val note: String? = null,
) {
    val url: String? get() = repo?.let { "https://github.com/$it" }
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
fun antiFeatureLabel(code: String): UiText = when (code) {
    "nonFreeNet" -> UiText.Resource(R.string.text_depends_on_a_non_free_network_service)
    "nonFreeAssets" -> UiText.Resource(R.string.text_includes_non_free_assets)
    "nonFreeAdd" -> UiText.Resource(R.string.text_recommends_non_free_extras)
    "tracking" -> UiText.Resource(R.string.text_tracks_users)
    "ads" -> UiText.Resource(R.string.text_contains_advertising)
    "knownVuln" -> UiText.Resource(R.string.text_reduces_device_security)
    "noSourceSince" -> UiText.Resource(R.string.text_no_longer_publishes_source_code)
    "disabledAlgorithm" -> UiText.Resource(R.string.text_signed_with_a_deprecated_algorithm)
    "upstreamNonFree" -> UiText.Resource(R.string.text_based_on_non_free_software)
    "restrictedLicense" -> UiText.Resource(R.string.text_restricted_licence_public_source_code_with_usage)
    else -> UiText.Literal(code)
}

/** Localized labels for the fixed category taxonomy the index uses. */
fun categoryLabel(code: String): UiText = when (code) {
    "ai" -> UiText.Resource(R.string.ai)
    "browser" -> UiText.Resource(R.string.browser)
    "communication" -> UiText.Resource(R.string.text_communication)
    "development" -> UiText.Resource(R.string.development)
    "education" -> UiText.Resource(R.string.text_education)
    "emulators" -> UiText.Resource(R.string.emulators)
    "finance" -> UiText.Resource(R.string.text_finance)
    "games" -> UiText.Resource(R.string.games)
    "graphics" -> UiText.Resource(R.string.graphics)
    "health" -> UiText.Resource(R.string.text_health)
    "maps" -> UiText.Resource(R.string.maps)
    "media" -> UiText.Resource(R.string.text_media)
    "multimedia" -> UiText.Resource(R.string.text_multimedia)
    "navigation" -> UiText.Resource(R.string.text_navigation)
    "notes" -> UiText.Resource(R.string.notes)
    "privacy" -> UiText.Resource(R.string.privacy)
    "productivity" -> UiText.Resource(R.string.productivity)
    "reading" -> UiText.Resource(R.string.reading)
    "security" -> UiText.Resource(R.string.text_security)
    "store" -> UiText.Resource(R.string.store)
    "system" -> UiText.Resource(R.string.text_system)
    "tools" -> UiText.Resource(R.string.tools)
    "utilities" -> UiText.Resource(R.string.utilities)
    "weather" -> UiText.Resource(R.string.weather)
    else -> UiText.Literal(code)
}

/** Human label for the app's lifecycle status. */
fun statusLabel(status: String): UiText = when (status) {
    "active" -> UiText.Resource(R.string.maintained)
    "unmaintained" -> UiText.Resource(R.string.text_unmaintained)
    "archived" -> UiText.Resource(R.string.archived)
    "deprecated" -> UiText.Resource(R.string.deprecated)
    else -> UiText.Literal(status)
}

/**
 * Games and emulators share one tab, because to the person browsing they answer the
 * same question: "what can I play on this?". Keeping them as categories in the data
 * means the tab can change without touching the index.
 */
fun IndexApp.isGamesOrEmulators(): Boolean =
    categories.any { it == "games" || it == "emulators" }
