package dev.montra.work

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import dev.montra.MontraApp
import dev.montra.data.UpdateCheck
import dev.montra.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Verifica o catálogo e conta as atualizações, com a app fechada.
 *
 * Sem isto, a cadência que o utilizador escolheu em [dev.montra.data.AutoRefresh] só
 * valia com o ecrã ligado — e é precisamente quando a app não está aberta que uma
 * loja tem trabalho a fazer.
 *
 * O trabalho é: perguntar ao catálogo (barato, o ETag responde 304 quando nada mudou),
 * comparar com o que está instalado, e só falar se houver algo novo a dizer.
 */
class UpdateCheckJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            val container = (applicationContext as MontraApp).container

            if (!container.settings.currentAutoRefresh().enabled) {
                Log.i("verificação em segundo plano: a cadência está desligada")
                jobFinished(params, false)
                return@launch
            }

            Log.i("a verificar o catálogo em segundo plano")
            // Num processo frio — que é o caso normal aqui, porque o sistema arranca
            // a app só para isto — nada foi carregado para memória ainda. Um
            // `refresh` respondido com 304 diria "está atual" sobre uma lista vazia, e
            // o trabalho desistia sem contar nada. O `load()` lê primeiro a cópia
            // verificada em disco (ou o snapshot incluído) e só depois pergunta.
            val repository = container.indexRepository
            if (repository.state.value.apps.isEmpty()) {
                repository.load()
            } else {
                repository.refresh(force = false)
            }
            val state = repository.state.value

            // Basta haver catálogo verificado para contar atualizações: ele é a mesma
            // lista que a app mostra, e uma falha de rede nesta verificação não torna
            // falso o que já estava confirmado. Sem catálogo verificado, não se diz nada.
            if (state.apps.isEmpty() || !state.signatureValid) {
                Log.i("verificação em segundo plano sem catálogo verificado; fica para a próxima")
                jobFinished(params, false)
                return@launch
            }

            val abis = Build.SUPPORTED_ABIS.toList()
            val updates = UpdateCheck.appsWithUpdates(applicationContext, state.apps, abis)
            val signature = UpdateCheck.signature(updates, abis)

            when {
                updates.isEmpty() -> {
                    container.settings.setNotifiedUpdates(null)
                    UpdateNotifications.dismiss(applicationContext)
                    Log.i("nenhuma atualização à espera")
                }
                container.settings.currentNotifiedUpdates() == signature -> {
                    Log.i("${updates.size} atualizações à espera, já notificadas")
                }
                else -> {
                    Log.i("${updates.size} atualizações à espera; a notificar")
                    UpdateNotifications.show(applicationContext, updates.size)
                    container.settings.setNotifiedUpdates(signature)
                }
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        // O sistema parou o trabalho: a rede mudou, ou entrou em Doze. Não é uma
        // falha, e voltar a agendar é a resposta certa.
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
