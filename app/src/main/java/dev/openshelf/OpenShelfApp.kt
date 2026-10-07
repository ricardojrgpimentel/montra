package dev.openshelf

import android.app.Application
import android.content.Context
import dev.openshelf.data.IndexRepository
import dev.openshelf.data.IndexSource
import dev.openshelf.data.Settings
import dev.openshelf.install.ApkDownloader
import dev.openshelf.install.InstallManager
import dev.openshelf.security.TrustStore
import dev.openshelf.ui.ImageStore
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
    val indexSource = IndexSource(context, okHttp)
    val indexRepository = IndexRepository(context, indexSource, settings, trustStore)
    val downloader = ApkDownloader(context, okHttp)
    val imageStore = ImageStore(context, okHttp)
    val installManager = InstallManager(context, downloader)
}

class OpenShelfApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
