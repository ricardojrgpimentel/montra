package dev.montra.install

import android.content.Intent
import dev.montra.data.model.Asset

/**
 * Everything the install pipeline needs, in a form that survives being handed to a
 * foreground service.
 *
 * Deliberately self-contained: the service must not depend on the in-memory index
 * still being loaded, because the whole point of the service is that the download
 * keeps going after the app has been backgrounded or its process restarted.
 */
data class InstallRequest(
    val appId: String,
    val appName: String,
    val packageName: String,
    val pinnedCertSha256: String?,
    val asset: Asset,
) {
    fun putInto(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_APP_ID, appId)
        putExtra(EXTRA_APP_NAME, appName)
        putExtra(EXTRA_PACKAGE, packageName)
        putExtra(EXTRA_PIN, pinnedCertSha256)
        putExtra(EXTRA_ABI, asset.abi)
        putExtra(EXTRA_URL, asset.url)
        putExtra(EXTRA_SHA256, asset.sha256)
        putExtra(EXTRA_SIZE, asset.size)
        putExtra(EXTRA_CERT, asset.signingCertSha256)
        putExtra(EXTRA_VERSION_CODE, asset.versionCode ?: -1)
        putExtra(EXTRA_VERSION_NAME, asset.versionName)
    }

    companion object {
        const val EXTRA_APP_ID = "dev.montra.extra.APP_ID"
        const val EXTRA_APP_NAME = "dev.montra.extra.APP_NAME"
        const val EXTRA_PACKAGE = "dev.montra.extra.PACKAGE"
        const val EXTRA_PIN = "dev.montra.extra.PIN"
        const val EXTRA_ABI = "dev.montra.extra.ABI"
        const val EXTRA_URL = "dev.montra.extra.URL"
        const val EXTRA_SHA256 = "dev.montra.extra.SHA256"
        const val EXTRA_SIZE = "dev.montra.extra.SIZE"
        const val EXTRA_CERT = "dev.montra.extra.CERT"
        const val EXTRA_VERSION_CODE = "dev.montra.extra.VERSION_CODE"
        const val EXTRA_VERSION_NAME = "dev.montra.extra.VERSION_NAME"

        fun from(intent: Intent): InstallRequest? {
            val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return null
            val url = intent.getStringExtra(EXTRA_URL) ?: return null
            val sha256 = intent.getStringExtra(EXTRA_SHA256) ?: return null
            val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return null
            return InstallRequest(
                appId = appId,
                appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName,
                packageName = packageName,
                pinnedCertSha256 = intent.getStringExtra(EXTRA_PIN),
                asset = Asset(
                    abi = intent.getStringExtra(EXTRA_ABI) ?: "universal",
                    url = url,
                    sha256 = sha256,
                    size = intent.getLongExtra(EXTRA_SIZE, 0L),
                    signingCertSha256 = intent.getStringExtra(EXTRA_CERT),
                    versionCode = intent.getIntExtra(EXTRA_VERSION_CODE, -1).takeIf { it >= 0 },
                    versionName = intent.getStringExtra(EXTRA_VERSION_NAME),
                ),
            )
        }
    }
}
