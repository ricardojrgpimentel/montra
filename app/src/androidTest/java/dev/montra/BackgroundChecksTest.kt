package dev.montra

import android.app.job.JobInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.montra.data.AutoRefresh
import dev.montra.work.BackgroundChecks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * O agendamento segue a cadência escolhida, e desligá-la desagenda mesmo.
 *
 * O que este teste não prova — e não pode provar — é que o sistema acorda a app na
 * hora certa: isso é o Doze, e verifica-se no aparelho com
 * `adb shell cmd jobscheduler run -f dev.montra.debug 315514`, com o processo morto.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundChecksTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as MontraApp).container

    @After
    fun restoreTheStoredCadence() = runBlocking {
        BackgroundChecks.schedule(context, container.settings.currentAutoRefresh())
    }

    @Test
    fun theScheduledJobFollowsTheChosenCadence() {
        BackgroundChecks.schedule(context, AutoRefresh.HOUR)
        assertEquals(60 * 60_000L, BackgroundChecks.pending(context)?.intervalMillis)

        // A cadência mais curta que a app oferece é exactamente o mínimo que o
        // JobScheduler aceita para trabalho periódico.
        BackgroundChecks.schedule(context, AutoRefresh.QUARTER)
        assertEquals(15 * 60_000L, BackgroundChecks.pending(context)?.intervalMillis)

        // Desligar a cadência tem de desagendar mesmo: um trabalho que continua a
        // acordar a app depois de a pessoa o ter desligado é uma promessa quebrada.
        BackgroundChecks.schedule(context, AutoRefresh.OFF)
        assertNull(BackgroundChecks.pending(context))
    }

    @Test
    fun theJobAsksForConnectivityAndIsScheduledForADay() {
        BackgroundChecks.schedule(context, AutoRefresh.DAILY)
        val job = requireNotNull(BackgroundChecks.pending(context)) { "o trabalho não ficou agendado" }

        assertEquals(24 * 60 * 60_000L, job.intervalMillis)
        // Sem rede não há catálogo para verificar: pedi-la ao sistema evita acordar o
        // telefone para falhar.
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.networkType)
        // `setPersisted(true)` sem a permissão RECEIVE_BOOT_COMPLETED faz o
        // `schedule` devolver falha e não agendar nada — é por isso que ela existe.
        assertTrue("o trabalho tem de sobreviver ao reboot", job.isPersisted)
    }
}
