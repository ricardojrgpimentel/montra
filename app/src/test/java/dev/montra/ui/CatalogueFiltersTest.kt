package dev.montra.ui

import dev.montra.data.model.IndexApp
import dev.montra.data.model.PlayStore
import dev.montra.install.InstallState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Os filtros que não são propriedades da app.
 *
 * "Novidade" depende do relógio, "instalada" do telemóvel e "guardada" da pessoa. Se
 * algum destes testes falhar, alguém voltou a pôr a decisão na app — e a app não sabe
 * nenhuma das três coisas.
 */
class CatalogueFiltersTest {

    private fun row(
        category: String = "tools",
        offPlay: Boolean = false,
        restricted: Boolean = false,
        installed: Boolean = false,
        isNew: Boolean = false,
        saved: Boolean = false,
    ) = AppRow(
        app = IndexApp(
            id = "app",
            name = "App",
            summary = "s",
            packageName = "dev.app",
            license = "MIT",
            sourceCode = "https://example.com",
            categories = listOf(category),
            antiFeatures = if (restricted) listOf("restrictedLicense") else emptyList(),
            playStore = PlayStore(present = !offPlay),
        ),
        asset = null,
        installed = if (installed) InstalledInfo(versionCode = 1, versionName = "1.0", certSha256 = null) else null,
        updateAvailable = false,
        signatureConflict = false,
        installState = InstallState.Idle,
        iconUrl = null,
        screenshotUrls = emptyList(),
        size = 0L,
        isNew = isNew,
        saved = saved,
    )

    @Test fun `a app sozinha nao exclui o que depende do relogio do telemovel ou da pessoa`() {
        // Condição necessária e não suficiente: a app não tem como saber estas três, e
        // por isso não as exclui. Quem decide é a linha, e é isso que o teste seguinte
        // prova. Se este teste passar a `false`, alguém voltou a pôr a decisão na app.
        assertTrue(row().app.matchesCatalogueFilters(null, AppFilter.NEW, null))
        assertTrue(row().app.matchesCatalogueFilters(null, AppFilter.INSTALLED, null))
        assertTrue(row().app.matchesCatalogueFilters(null, AppFilter.SAVED, null))
    }

    @Test fun `cada filtro apanha a sua linha`() {
        assertTrue(row(isNew = true).matchesCatalogueFilters(null, AppFilter.NEW, null))
        assertFalse(row(isNew = false).matchesCatalogueFilters(null, AppFilter.NEW, null))

        assertTrue(row(installed = true).matchesCatalogueFilters(null, AppFilter.INSTALLED, null))
        assertFalse(row(installed = false).matchesCatalogueFilters(null, AppFilter.INSTALLED, null))

        assertTrue(row(saved = true).matchesCatalogueFilters(null, AppFilter.SAVED, null))
        assertFalse(row(saved = false).matchesCatalogueFilters(null, AppFilter.SAVED, null))
    }

    @Test fun `os filtros da app continuam a funcionar ao lado dos novos`() {
        assertTrue(row(offPlay = true).matchesCatalogueFilters(null, AppFilter.OFF_PLAY, null))
        assertFalse(row(offPlay = false).matchesCatalogueFilters(null, AppFilter.OFF_PLAY, null))
        assertTrue(row(restricted = true).matchesCatalogueFilters(null, AppFilter.RESTRICTED, null))
        assertFalse(row(restricted = false).matchesCatalogueFilters(null, AppFilter.RESTRICTED, null))
    }

    @Test fun `sem filtro passa tudo`() {
        assertTrue(row().matchesCatalogueFilters(null, null, null))
    }

    @Test fun `a categoria e o filtro tem de concordar`() {
        assertTrue(row(category = "tools", isNew = true).matchesCatalogueFilters("tools", AppFilter.NEW, null))
        assertFalse(row(category = "games", isNew = true).matchesCatalogueFilters("tools", AppFilter.NEW, null))
    }
}
