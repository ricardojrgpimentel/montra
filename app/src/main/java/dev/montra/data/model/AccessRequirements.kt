package dev.montra.data.model

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
    val label: String get() {
        if (methods.size > 2) return if (isRequired) "Requer acesso especial" else "Acesso especial opcional"
        val names = methods.sortedBy { if (it == "shizuku") 0 else 1 }
            .joinToString(" ou ") { when (it) {
                "shizuku" -> "Shizuku"
                "adb" -> "ADB"
                "deviceOwner" -> "gestão do dispositivo"
                "dhizuku" -> "Dhizuku"
                "systemApp" -> "app de sistema"
                "workProfile" -> "perfil de trabalho"
                else -> it
            } }
        return if (isRequired) "Requer $names" else
            names.replaceFirstChar { it.uppercase() } + " opcional"
    }

    fun noteFor(languageTag: String): String? = note[languageTag]
        ?: note[languageTag.substringBefore('-')] ?: note["en"] ?: note.values.firstOrNull()
}

enum class RequirementFilter(val label: String) {
    NONE("Sem requisitos especiais"),
    SHIZUKU("Shizuku"),
    ROOT("Root");

    fun matches(app: IndexApp): Boolean = when (this) {
        NONE -> app.accessRequirements?.isRequired != true
        SHIZUKU -> app.accessRequirements?.methods?.contains("shizuku") == true
        ROOT -> app.accessRequirements?.methods?.contains("root") == true
    }
}
