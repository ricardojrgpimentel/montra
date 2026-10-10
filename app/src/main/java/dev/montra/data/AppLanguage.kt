package dev.montra.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Empty tags delegate to the system, including its ordered language preferences. */
enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("", ""),
    PORTUGUESE("pt", "Português"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français");

    fun apply() = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

    companion object {
        fun current(): AppLanguage {
            val language = AppCompatDelegate.getApplicationLocales()[0]?.language
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}
