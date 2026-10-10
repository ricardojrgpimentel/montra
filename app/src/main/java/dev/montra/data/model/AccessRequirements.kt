package dev.montra.data.model

import dev.montra.R
import dev.montra.util.UiText
import kotlinx.serialization.Serializable

/** Methods are alternatives: one is enough. Missing metadata preserves older catalogues. */
@Serializable
data class AccessRequirements(
    val mode: String,
    val methods: List<String>,
    val note: Map<String, String> = emptyMap(),
    val guideUrl: String? = null,
) {
    val isRequired: Boolean get() = mode != "optional"
    val label: UiText get() {
        if (methods.size > 2) return UiText.Resource(
            if (isRequired) R.string.text_requires_special_access else R.string.text_optional_special_access,
        )
        val names = UiText.Joined(
            methods.sortedBy { if (it == "shizuku") 0 else 1 }.map { method ->
                when (method) {
                    "shizuku" -> UiText.Resource(R.string.text_shizuku)
                    "root" -> UiText.Resource(R.string.text_root)
                    "adb" -> UiText.Resource(R.string.text_adb)
                    "deviceOwner" -> UiText.Resource(R.string.text_device_management)
                    "systemApp" -> UiText.Resource(R.string.text_system_app)
                    "workProfile" -> UiText.Resource(R.string.text_work_profile)
                    "dhizuku" -> UiText.Literal("Dhizuku")
                    else -> UiText.Literal(method)
                }
            },
            UiText.Resource(R.string.or_separator),
        )
        return UiText.Resource(
            if (isRequired) R.string.access_required else R.string.access_optional,
            listOf(names),
        )
    }

    fun noteFor(languageTag: String): String? = note[languageTag]
        ?: note[languageTag.substringBefore('-')] ?: note["en"] ?: note.values.firstOrNull()
}

enum class RequirementFilter(val labelRes: Int) {
    NONE(R.string.text_no_special_requirements),
    SHIZUKU(R.string.text_shizuku),
    ROOT(R.string.text_root);

    fun matches(app: IndexApp): Boolean = when (this) {
        NONE -> app.accessRequirements?.isRequired != true
        SHIZUKU -> app.accessRequirements?.methods?.contains("shizuku") == true
        ROOT -> app.accessRequirements?.methods?.contains("root") == true
    }
}
