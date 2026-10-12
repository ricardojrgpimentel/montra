package dev.montra.work

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import dev.montra.AppContainer
import dev.montra.MontraApp
import dev.montra.data.UpdateCheck
import dev.montra.data.model.IndexApp
import dev.montra.install.InstallRequest
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

            if (updates.isNotEmpty() && container.settings.currentPreDownloadUpdates()) {
                preDownload(container, updates, abis)
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

    /**
     * Pré-descarrega o que está à espera, do mais pequeno para o maior, e pára no
     * orçamento.
     *
     * Por ordem de tamanho porque é isso que faz mais gente ganhar tempo com menos
     * dados: cinco apps de 20 MB valem mais do que uma de 100. O orçamento existe porque
     * um trabalho de fundo que enche o telemóvel de APKs que ninguém pediu é a versão má
     * de uma boa ideia.
     *
     * Só por rede não medida, mesmo com a definição de "só Wi-Fi" desligada: isto é uma
     * conveniência que ninguém pediu no momento, e gastar dados móveis de alguém por
     * conveniência não se faz.
     */
    private suspend fun preDownload(container: AppContainer, updates: List<IndexApp>, abis: List<String>) {
        if (container.networkStatus.isMetered()) {
            Log.i("rede medida: não se pré-descarrega nada")
            return
        }
        var spent = 0L
        var done = 0
        for (app in updates.sortedBy { it.bestAssetFor(abis)?.size ?: Long.MAX_VALUE }) {
            val asset = app.bestAssetFor(abis) ?: continue
            if (asset.size > MAX_PRE_DOWNLOAD_BYTES) {
                Log.i("${app.id}: ${asset.size} bytes é grande demais para se pré-descarregar")
                continue
            }
            if (spent + asset.size > PRE_DOWNLOAD_BUDGET_BYTES) {
                Log.i("orçamento do pré-download esgotado; o resto fica para a próxima")
                break
            }
            val request = InstallRequest(
                appId = app.id,
                appName = app.name,
                packageName = app.packageName,
                pinnedCertSha256 = app.signingCertSha256,
                asset = asset,
            )
            if (container.installManager.preDownload(request)) {
                spent += asset.size
                done++
            }
        }
        if (done > 0) Log.i("$done atualizações pré-descarregadas, $spent bytes")
    }

    private companion object {
        /** Uma app de 300 MB não se pré-descarrega: o custo não é do tamanho do ganho. */
        const val MAX_PRE_DOWNLOAD_BYTES = 150L * 1024 * 1024

        /** Por verificação. Um trabalho de fundo não pode encher o telemóvel sozinho. */
        const val PRE_DOWNLOAD_BUDGET_BYTES = 300L * 1024 * 1024
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
