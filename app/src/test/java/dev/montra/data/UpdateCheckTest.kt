package dev.montra.data

import dev.montra.data.model.Asset
import dev.montra.data.model.IndexApp
import dev.montra.data.model.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    private fun app(
        id: String = "app",
        versionCode: Int = 10,
        versionName: String = "1.0",
        assets: List<Asset> = emptyList(),
    ) = IndexApp(
        id = id,
        name = id,
        summary = "s",
        packageName = "dev.$id",
        license = "GPL-3.0-only",
        sourceCode = "https://example.com/$id",
        release = Release(
            versionName = versionName,
            versionCode = versionCode,
            tag = "v$versionName",
            assets = assets,
        ),
    )

    private fun asset(abi: String, versionCode: Int, versionName: String) = Asset(
        abi = abi,
        url = "https://example.com/a.apk",
        sha256 = "0".repeat(64),
        size = 1,
        versionCode = versionCode,
        versionName = versionName,
    )

    @Test fun `sem a app instalada nunca ha atualizacao`() {
        assertFalse(UpdateCheck.isUpdateAvailable(app(), null, null, null))
    }

    @Test fun `a mesma versao nao e atualizacao`() {
        assertFalse(UpdateCheck.isUpdateAvailable(app(versionCode = 10), null, 10L, "1.0"))
    }

    @Test fun `um versionCode maior e atualizacao`() {
        assertTrue(UpdateCheck.isUpdateAvailable(app(versionCode = 11, versionName = "1.1"), null, 10L, "1.0"))
    }

    @Test fun `um versionCode menor nao e atualizacao`() {
        assertFalse(UpdateCheck.isUpdateAvailable(app(versionCode = 9, versionName = "0.9"), null, 10L, "1.0"))
    }

    @Test fun `sem nome de versao instalado vale o versionCode`() {
        assertTrue(UpdateCheck.isUpdateAvailable(app(versionCode = 11, versionName = "1.1"), null, 10L, null))
    }

    /**
     * O caso do Obtainium: 2356 no universal, 23563 no arm64, e o mesmo nome de
     * versão. Quem tem o universal instalado não deve ver "Atualizar" — é a regra que
     * estava escrita dentro do ViewModel e que agora serve também a notificação.
     */
    @Test fun `o mesmo nome de versao manda sobre um versionCode maior`() {
        val split = app(
            versionCode = 23563,
            versionName = "1.9.0",
            assets = listOf(asset("arm64-v8a", 23563, "1.9.0")),
        )
        assertFalse(
            UpdateCheck.isUpdateAvailable(split, split.bestAssetFor(listOf("arm64-v8a")), 2356L, "1.9.0"),
        )
    }

    @Test fun `um nome de versao diferente com versionCode maior e atualizacao`() {
        val split = app(
            versionCode = 23563,
            versionName = "2.0.0",
            assets = listOf(asset("arm64-v8a", 23563, "2.0.0")),
        )
        assertTrue(
            UpdateCheck.isUpdateAvailable(split, split.bestAssetFor(listOf("arm64-v8a")), 2356L, "1.9.0"),
        )
    }

    @Test fun `sem release nao ha atualizacao`() {
        val noRelease = IndexApp(
            id = "x",
            name = "x",
            summary = "s",
            packageName = "dev.x",
            license = "MIT",
            sourceCode = "https://example.com/x",
        )
        assertFalse(UpdateCheck.isUpdateAvailable(noRelease, null, 1L, "1.0"))
    }

    @Test fun `a assinatura muda quando aparece uma atualizacao nova`() {
        val abis = listOf("arm64-v8a")
        val one = listOf(app("a", versionCode = 10))
        val two = listOf(app("a", versionCode = 10), app("b", versionCode = 20))

        assertEquals(UpdateCheck.signature(one, abis), UpdateCheck.signature(one, abis))
        assertTrue(UpdateCheck.signature(one, abis) != UpdateCheck.signature(two, abis))
    }

    @Test fun `a assinatura nao depende da ordem do catalogo`() {
        val abis = listOf("arm64-v8a")
        val first = listOf(app("a", versionCode = 10), app("b", versionCode = 20))
        val reversed = first.reversed()
        assertEquals(UpdateCheck.signature(first, abis), UpdateCheck.signature(reversed, abis))
    }
}
