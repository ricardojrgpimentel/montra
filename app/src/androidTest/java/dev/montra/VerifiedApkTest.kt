package dev.montra

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.montra.install.InstallRequest
import dev.montra.install.InstallService
import dev.montra.install.InstallState
import dev.montra.util.toHex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * Um APK pode ser descarregado e verificado sem ser instalado.
 *
 * É o que permite arquivar e levar para outro telemóvel, e é a única coisa que uma loja
 * sem servidor oferece e uma com servidor não: um ficheiro cuja origem foi confirmada
 * aqui, nas mãos de quem o vai instalar.
 *
 * Usa o APK mais pequeno do catálogo: a regra é a mesma em qualquer tamanho, e não vale
 * a pena gastar dezenas de megabytes dos dados de alguém para a provar.
 */
@RunWith(AndroidJUnit4::class)
class VerifiedApkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as MontraApp).container
    private val originalWifiOnly = runBlocking { container.settings.currentWifiOnly() }

    @After
    fun restore() = runBlocking {
        container.settings.setWifiOnly(originalWifiOnly)
        container.installManager.setState(APP_ID, InstallState.Idle)
        container.installManager.clearDownloads()
    }

    @Test
    fun anApkIsVerifiedAndKeptWithoutBeingInstalled() = runBlocking {
        assumeTrue(
            "precisa de: adb shell appops set dev.montra.debug REQUEST_INSTALL_PACKAGES allow",
            container.installManager.canRequestInstall(),
        )
        // Tira a barreira da rede do caminho: aqui testa-se o caminho do APK guardado.
        container.settings.setWifiOnly(false)

        container.indexRepository.load()
        val candidate = container.indexRepository.state.value.apps
            .mapNotNull { app -> app.bestAssetFor(listOf("arm64-v8a", "armeabi-v7a"))?.let { app to it } }
            .filter { (_, asset) -> asset.size > 100_000 }
            .minByOrNull { (_, asset) -> asset.size }
            ?: error("o catálogo devia ter um APK pequeno para este teste")
        val (app, asset) = candidate

        val sessionsBefore = context.packageManager.packageInstaller.mySessions.size

        InstallService.start(
            context,
            InstallRequest(
                appId = app.id,
                appName = app.name,
                packageName = app.packageName,
                pinnedCertSha256 = app.signingCertSha256,
                asset = asset,
                allowMetered = true,
                shareOnly = true,
            ),
        )

        withTimeout(120_000) {
            container.installManager.states.first { it[app.id] is InstallState.ReadyToShare }
        }

        val file = requireNotNull(container.installManager.verifiedFile(app.id)) {
            "o APK verificado devia estar em cache para se poder partilhar"
        }
        assertEquals("o ficheiro é o do índice", asset.size, file.length())
        assertEquals(
            "o ficheiro guardado tem de ser o que o índice descreve",
            asset.sha256.lowercase(),
            sha256Of(file),
        )
        // E nada foi instalado: o caminho do "guardar" não abre sessão nenhuma.
        assertEquals(
            "guardar não pode abrir uma sessão de instalação",
            sessionsBefore,
            context.packageManager.packageInstaller.mySessions.size,
        )
        assertTrue(
            "a app não pode ficar marcada como instalada",
            container.installManager.stateOf(app.id) is InstallState.ReadyToShare,
        )
    }

    /**
     * O pré-download de fundo faz o mesmo sem serviço em primeiro plano e **sem a
     * autorização de instalar**: não instala nada, portanto não tem de a pedir. É o que
     * permite ao trabalho periódico adiantar trabalho em vez de esperar pelo toque.
     */
    @Test
    fun theBackgroundPreDownloadVerifiesAndKeepsWithoutInstalling() = runBlocking {
        container.settings.setWifiOnly(false)
        container.indexRepository.load()
        val candidate = container.indexRepository.state.value.apps
            .mapNotNull { app -> app.bestAssetFor(listOf("arm64-v8a", "armeabi-v7a"))?.let { app to it } }
            .filter { (_, asset) -> asset.size > 100_000 }
            .minByOrNull { (_, asset) -> asset.size }
            ?: error("o catálogo devia ter um APK pequeno para este teste")
        val (app, asset) = candidate

        val sessionsBefore = context.packageManager.packageInstaller.mySessions.size
        val ok = container.installManager.preDownload(
            InstallRequest(
                appId = app.id,
                appName = app.name,
                packageName = app.packageName,
                pinnedCertSha256 = app.signingCertSha256,
                asset = asset,
                allowMetered = true,
            ),
        )

        assertTrue("o pré-download devia ter verificado o APK", ok)
        val file = requireNotNull(container.installManager.verifiedFile(app.id))
        assertEquals("o ficheiro é o do índice", asset.size, file.length())
        assertEquals(asset.sha256.lowercase(), sha256Of(file))
        assertEquals(
            "pré-descarregar não pode abrir uma sessão de instalação",
            sessionsBefore,
            context.packageManager.packageInstaller.mySessions.size,
        )
        assertTrue(
            "o estado devia ser o de pronto a instalar, não o de instalado",
            container.installManager.stateOf(app.id) is InstallState.ReadyToShare,
        )
    }

    private fun sha256Of(file: java.io.File): String {        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private companion object {
        const val APP_ID = "verified-apk-test"
    }
}
