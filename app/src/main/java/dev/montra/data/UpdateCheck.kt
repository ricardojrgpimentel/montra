package dev.montra.data

import android.content.Context
import dev.montra.data.model.Asset
import dev.montra.data.model.IndexApp
import dev.montra.install.InstallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Há uma versão mais recente?" — uma só resposta, para o ecrã e para o trabalho de
 * fundo.
 *
 * A regra estava escrita dentro do `rebuild()` do ViewModel. O trabalho de fundo
 * precisa da mesma resposta, e uma segunda cópia era a maneira garantida de as duas
 * discordarem: a notificação a dizer "3 atualizações" e a lista a mostrar duas.
 */
object UpdateCheck {

    /**
     * Um projeto que publica splits atribui `versionCode` diferentes por ABI (o
     * Obtainium: 2356 no universal, 23563 no arm64). Quem tem o universal instalado
     * não deve ver "Atualizar" para a mesma versão, portanto o nome da versão manda
     * quando existe.
     */
    fun isUpdateAvailable(
        app: IndexApp,
        asset: Asset?,
        installedVersionCode: Long?,
        installedVersionName: String?,
    ): Boolean {
        if (installedVersionCode == null) return false
        val targetVersion = asset?.versionCode ?: app.release?.versionCode ?: return false
        val targetName = asset?.versionName ?: app.release?.versionName
        val sameVersionName = installedVersionName != null && targetName != null &&
            installedVersionName == targetName
        return targetVersion > installedVersionCode && !sameVersionName
    }

    /**
     * As apps do catálogo instaladas com uma versão mais antiga do que a que o
     * catálogo publica. Pergunta ao sistema por cada app do catálogo — o catálogo é
     * dinâmico, portanto não há uma lista `<queries>` que sirva.
     */
    suspend fun appsWithUpdates(
        context: Context,
        apps: List<IndexApp>,
        deviceAbis: List<String>,
    ): List<IndexApp> = withContext(Dispatchers.IO) {
        apps.filter { app ->
            val installed = InstallManager.installedVersionCode(context, app.packageName)
            isUpdateAvailable(
                app = app,
                asset = app.bestAssetFor(deviceAbis),
                installedVersionCode = installed,
                installedVersionName = InstallManager.installedVersionName(context, app.packageName),
            )
        }
    }

    /**
     * Uma impressão digital do conjunto de atualizações, para não repetir a mesma
     * notificação a cada verificação. Muda quando aparece uma atualização nova ou
     * quando uma é aplicada — que é exactamente quando vale a pena voltar a falar.
     */
    fun signature(apps: List<IndexApp>, deviceAbis: List<String>): String = apps
        .map { app ->
            val asset = app.bestAssetFor(deviceAbis)
            "${app.id}@${asset?.versionCode ?: app.release?.versionCode ?: 0}"
        }
        .sorted()
        .joinToString(",")
}
