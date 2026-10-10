package dev.montra.data

import dev.montra.util.ptText
import dev.montra.data.model.AccessRequirements
import dev.montra.data.model.IndexApp
import dev.montra.data.model.IndexJson
import dev.montra.data.model.PlayStore
import dev.montra.data.model.RequirementFilter
import dev.montra.ui.AppFilter
import dev.montra.ui.matchesCatalogueFilters
import org.junit.Assert.*
import org.junit.Test

class AccessRequirementsTest {
    private val app = IndexApp(
        id = "example", name = "Example", summary = "Example application",
        packageName = "org.example.app", license = "MIT", sourceCode = "https://example.org",
        categories = listOf("tools"), playStore = PlayStore(false),
    )

    @Test fun `old catalogues without access metadata still decode`() {
        val decoded = IndexJson.decodeFromString<IndexApp>(
            """{"id":"old","name":"Old","summary":"An old app","packageName":"org.old.app",
                "license":"MIT","sourceCode":"https://example.org","futureField":true}""",
        )
        assertNull(decoded.accessRequirements)
        assertTrue(RequirementFilter.NONE.matches(decoded))
        assertFalse(RequirementFilter.ROOT.matches(decoded))
        assertFalse(RequirementFilter.SHIZUKU.matches(decoded))
    }

    @Test fun `root only and shizuku only do not imply each other`() {
        for (method in listOf("root", "shizuku")) {
            val entry = app.copy(accessRequirements = AccessRequirements("required", listOf(method)))
            assertFalse(RequirementFilter.NONE.matches(entry))
            assertEquals(method == "root", RequirementFilter.ROOT.matches(entry))
            assertEquals(method == "shizuku", RequirementFilter.SHIZUKU.matches(entry))
        }
    }

    @Test fun `optional access remains available without special requirements`() {
        val entry = app.copy(accessRequirements = AccessRequirements("optional", listOf("root")))
        assertTrue(RequirementFilter.NONE.matches(entry))
        assertTrue(RequirementFilter.ROOT.matches(entry))
        assertEquals("Root opcional", entry.accessRequirements!!.label.ptText())
    }

    @Test fun `alternatives match both filters but label says or`() {
        val entry = app.copy(accessRequirements = AccessRequirements("required", listOf("root", "shizuku")))
        assertEquals("Requer Shizuku ou Root", entry.accessRequirements!!.label.ptText())
        assertTrue(RequirementFilter.ROOT.matches(entry))
        assertTrue(RequirementFilter.SHIZUKU.matches(entry))
        assertFalse(RequirementFilter.NONE.matches(entry))
    }

    @Test fun `additional privileged modes are not mistaken for no requirements`() {
        val entry = app.copy(accessRequirements = AccessRequirements("required", listOf("deviceOwner")))
        assertFalse(RequirementFilter.NONE.matches(entry))
        assertFalse(RequirementFilter.ROOT.matches(entry))
    }

    @Test fun `category license and access filters intersect and all clears access only`() {
        val entry = app.copy(accessRequirements = AccessRequirements("required", listOf("shizuku")))
        assertTrue(entry.matchesCatalogueFilters("tools", AppFilter.OFF_PLAY, RequirementFilter.SHIZUKU))
        assertFalse(entry.matchesCatalogueFilters("games", AppFilter.OFF_PLAY, RequirementFilter.SHIZUKU))
        assertFalse(entry.matchesCatalogueFilters("tools", AppFilter.RESTRICTED, RequirementFilter.SHIZUKU))
        assertFalse(entry.matchesCatalogueFilters("tools", AppFilter.OFF_PLAY, RequirementFilter.NONE))
        assertTrue(entry.matchesCatalogueFilters("tools", AppFilter.OFF_PLAY, null))
        assertTrue(entry.matchesCatalogueFilters(null, null, null))
    }

    @Test fun `new metadata decodes with localized explanations and guide`() {
        val access = IndexJson.decodeFromString<AccessRequirements>(
            """{"mode":"required","methods":["shizuku"],
                "note":{"en":"English","pt":"Português"},
                "guideUrl":"https://shizuku.rikka.app/guide/setup/","futureField":true}""",
        )
        assertEquals("Requer Shizuku", access.label.ptText())
        assertEquals("Português", access.noteFor("pt-PT"))
        assertEquals("English", access.noteFor("fr"))
        assertEquals("https://shizuku.rikka.app/guide/setup/", access.guideUrl)
    }
}
