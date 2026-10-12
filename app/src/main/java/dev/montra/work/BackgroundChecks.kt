package dev.montra.work

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import dev.montra.data.AutoRefresh
import dev.montra.util.Log

/**
 * A verificação do catálogo em segundo plano.
 *
 * **Porque não o WorkManager.** O roadmap dizia WorkManager, e o preço é que decidiu:
 * `androidx.work:work-runtime` arrasta o `androidx.room:room-ktx`, o
 * `lifecycle-livedata`, o `tracing` e o `startup` para dentro de uma app que se
 * orgulha de não ter base de dados nenhuma. O `JobScheduler` é a API da plataforma
 * que o WorkManager usa por baixo, dá as mesmas garantias que aqui interessam —
 * acorda com o ecrã desligado, respeita o Doze, sobrevive ao reboot com
 * `setPersisted`, e sabe exigir uma rede não medida — e não acrescenta uma única
 * dependência.
 *
 * A cadência é a que o utilizador já escolheu em [AutoRefresh]. O mínimo do
 * `JobScheduler` para trabalho periódico são 15 minutos, que é exactamente a
 * cadência mais curta que a app oferece.
 */
object BackgroundChecks {

    /** Um id fixo: reagendar o mesmo trabalho substitui-o em vez de acumular. */
    private const val JOB_ID = 0x4D07A

    /** O trabalho que está agendado agora, se houver. Para o log e para os testes. */
    fun pending(context: Context): JobInfo? =
        context.getSystemService(JobScheduler::class.java)?.getPendingJob(JOB_ID)

    fun schedule(context: Context, refresh: AutoRefresh) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return

        if (!refresh.enabled) {
            scheduler.cancel(JOB_ID)
            Log.i("verificação em segundo plano desligada")
            return
        }

        val interval = refresh.minutes * 60_000L
        // Reagendar o mesmo trabalho reinicia o contador. Sem isto, abrir a app de
        // cinco em cinco minutos adiava a verificação de fundo para sempre — e é
        // precisamente quando a app não se abre que ela faz falta.
        scheduler.getPendingJob(JOB_ID)?.let { existing ->
            if (existing.intervalMillis == interval) return
        }

        val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJobService::class.java))
            .setPeriodic(interval)
            // Sem rede não há catálogo para verificar: pedi-la ao sistema evita
            // acordar o telefone para falhar.
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            // Sobrevive ao reboot. Exige RECEIVE_BOOT_COMPLETED no manifest.
            .setPersisted(true)
            .build()

        when (val result = scheduler.schedule(job)) {
            JobScheduler.RESULT_SUCCESS ->
                Log.i("verificação em segundo plano a cada ${refresh.minutes} minutos")
            else ->
                Log.w("o sistema recusou a verificação em segundo plano (resultado $result)")
        }
    }
}
