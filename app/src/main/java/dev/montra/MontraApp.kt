package dev.montra

import android.app.Application
import android.content.Context
import dev.montra.data.IndexRepository
import dev.montra.data.IndexSource
import dev.montra.data.NetworkStatus
import dev.montra.data.Settings
import dev.montra.install.ApkDownloader
import dev.montra.install.InstallManager
import dev.montra.security.TrustStore
import dev.montra.ui.ImageStore
import dev.montra.work.BackgroundChecks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual dependency wiring. Four objects and no framework: an app this size does
 * not need a DI container, and every dependency here is a thing you can point at
 * in a code review.
 */
class AppContainer(context: Context) {

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val settings = Settings(context)
    val trustStore = TrustStore(context)
    val networkStatus = NetworkStatus(context)
    val indexSource = IndexSource(context, okHttp)
    val indexRepository = IndexRepository(context, indexSource, settings, trustStore, networkStatus)
    val downloader = ApkDownloader(context, okHttp)
    val imageStore = ImageStore(context, okHttp)
    val installManager = InstallManager(context, downloader, networkStatus, settings)
}

class MontraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Reagenda no arranque: o trabalho sobrevive ao reboot com `setPersisted`, mas
        // a cadência vive em disco e o sistema pode tê-lo perdido. Reagendar é
        // idempotente — o mesmo id substitui o trabalho, e o agendador não reinicia o
        // contador quando o intervalo é o mesmo.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            BackgroundChecks.schedule(this@MontraApp, container.settings.currentAutoRefresh())
        }
    }
}
