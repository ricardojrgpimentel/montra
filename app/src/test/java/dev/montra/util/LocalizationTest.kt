package dev.montra.util

import dev.montra.R
import dev.montra.data.model.AccessRequirements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LocalizationTest {
    @Test fun `all languages cover the same resources and preserve format arguments`() {
        val english = resourceStrings("en")
        val placeholders = Regex("%[0-9]+\\$[sd]")
        for (language in listOf("pt", "es", "fr")) {
            val translated = resourceStrings(language)
            assertEquals(language, english.keys, translated.keys)
            for ((name, value) in translated) {
                assertTrue("$language/$name is empty", value.isNotBlank())
                assertEquals("$language/$name changes format arguments",
                    placeholders.findAll(english.getValue(name)).map { it.value }.sorted().toList(),
                    placeholders.findAll(value).map { it.value }.sorted().toList())
            }
        }
    }

    @Test fun `state text is resolved again when the language changes`() {
        val text = verifiedLabel(1_000_000L, 1_120_000L)
        assertEquals("verificado há 2 minutos", text.testText("pt"))
        assertEquals("verified 2 minutes ago", text.testText("en"))
        assertEquals("verificado hace 2 minutos", text.testText("es"))
        assertEquals("vérifié il y a 2 minutes", text.testText("fr"))
    }

    @Test fun `access alternatives remain alternatives in every language`() {
        val text = AccessRequirements("required", listOf("root", "shizuku")).label
        assertEquals("Requires Shizuku or Root", text.testText("en"))
        assertEquals("Requiere Shizuku o Root", text.testText("es"))
        assertEquals("Nécessite Shizuku ou Root", text.testText("fr"))
    }

    @Test fun `release date uses the requested locale`() {
        assertEquals("February 23, 2026", releaseDateLabel("2026-02-23T14:05:11Z", Locale.US))
        assertEquals("23 février 2026", releaseDateLabel("2026-02-23T14:05:11Z", Locale.FRANCE))
    }
}
