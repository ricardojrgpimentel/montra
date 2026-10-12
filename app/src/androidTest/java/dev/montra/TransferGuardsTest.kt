package dev.montra

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.montra.data.model.Asset
import dev.montra.install.InstallRequest
import dev.montra.install.InstallService
import dev.montra.install.InstallState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * As duas barreiras que perguntam antes de transferir, provadas de ponta a ponta.
 *
 * O pipeline é a única coisa que as pode aplicar — a ficha só mostra o resultado —
 * por isso é ele que se testa. Os pedidos usam assets que não existem: as barreiras
 * correm antes de se tocar na rede, e é isso que se quer provar.
 */
@RunWith(AndroidJUnit4::class)
class TransferGuardsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as MontraApp).container
    private val manager get() = container.installManager

    /** O que estava escolhido antes do teste, para o devolver como estava. */
    private val originalWifiOnly = runBlocking { container.settings.currentWifiOnly() }

    private fun asset(size: Long) = Asset(
        abi = "universal",
        url = "https://example.com/nao-existe.apk",
        sha256 = "0".repeat(64),
        size = size,
    )

    @After
    fun restoreTheSettings() = runBlocking {
        container.settings.setWifiOnly(originalWifiOnly)
        manager.setState(METERED_ID, InstallState.Idle)
        manager.setState(SPACE_ID, InstallState.Idle)
    }

    @Test
    fun aMeteredNetworkHoldsTheDownloadUntilItIsAuthorised() = runBlocking {
        assumeTrue(
            "precisa de: adb shell appops set dev.montra.debug REQUEST_INSTALL_PACKAGES allow",
            manager.canRequestInstall(),
        )
        // Num Wi-Fi não há nada para segurar. O teste di-lo em vez de fingir que
        // provou uma barreira que não chegou a correr.
        assumeTrue(
            "este aparelho não está numa rede medida (dados móveis)",
            container.networkStatus.isMetered(),
        )
        container.settings.setWifiOnly(true)

        InstallService.start(
            context,
            InstallRequest(METERED_ID, "Metered", "dev.montra.metered", null, asset(1_000_000)),
        )
        val held = withTimeout(10_000) {
            manager.states.first { it[METERED_ID] is InstallState.OnMeteredNetwork }
        }
        assertTrue(held[METERED_ID] is InstallState.OnMeteredNetwork)

        // Autorizada, a barreira deixa passar — e o que falha a seguir é a rede, não a
        // política. É o que distingue "não começa" de "não pode".
        InstallService.start(
            context,
            InstallRequest(METERED_ID, "Metered", "dev.montra.metered", null, asset(1_000_000), allowMetered = true),
        )
        val afterAuthorising = withTimeout(15_000) {
            manager.states.first { state ->
                val current = state[METERED_ID]
                current is InstallState.Downloading || current is InstallState.Failed
            }
        }
        assertTrue(
            "autorizar tinha de deixar passar da barreira, vi ${afterAuthorising[METERED_ID]}",
            afterAuthorising[METERED_ID] !is InstallState.OnMeteredNetwork,
        )
        InstallService.cancel(context, METERED_ID)
    }

    @Test
    fun aFileThatCannotFitIsRefusedBeforeAnyDownload() = runBlocking {
        assumeTrue(
            "precisa de: adb shell appops set dev.montra.debug REQUEST_INSTALL_PACKAGES allow",
            manager.canRequestInstall(),
        )
        // Tira a barreira da rede do caminho: aqui testa-se o espaço.
        container.settings.setWifiOnly(false)

        // Dez terabytes não cabem em telemóvel nenhum.
        val impossible = 10L * 1024 * 1024 * 1024 * 1024
        InstallService.start(
            context,
            InstallRequest(SPACE_ID, "Space", "dev.montra.space", null, asset(impossible), allowMetered = true),
        )
        val held = withTimeout(10_000) {
            manager.states.first { it[SPACE_ID] is InstallState.NoSpace }
        }
        val missing = (held[SPACE_ID] as InstallState.NoSpace).missingBytes
        assertTrue("o aviso tem de dizer quanto falta, e faltam $missing bytes", missing > 0)
        InstallService.cancel(context, SPACE_ID)
    }

    private companion object {
        const val METERED_ID = "transfer-guard-metered"
        const val SPACE_ID = "transfer-guard-space"
    }
}
