package dev.montra.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.montra.data.model.Asset
import dev.montra.data.model.IndexApp
import dev.montra.security.ApkVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val bytes: Long, val total: Long, val fraction: Float) : InstallState

    /** Download complete, now checking hash and signature before asking the system. */
    data object Verifying : InstallState

    /** The system installer is showing its own confirmation dialog. */
    data object AwaitingUser : InstallState
    data class Installed(val versionName: String?) : InstallState
    data class Failed(val reason: String) : InstallState
}

/**
 * Download → verify → hand to the system installer.
 *
 * The order is the point. Android's PackageInstaller will happily install a
 * modified APK if the signature is consistent with what is already on the device;
 * it has no idea what our index promised. So verification happens here, before a
 * session is even created, and the session is only opened for a file that already
 * matched both the SHA-256 and the pinned certificate.
 */
class InstallManager(private val context: Context, private val downloader: ApkDownloader) {

    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val states: StateFlow<Map<String, InstallState>> = _states.asStateFlow()

    fun setState(appId: String, state: InstallState) {
        _states.update { it + (appId to state) }
    }

    fun stateOf(appId: String): InstallState = _states.value[appId] ?: InstallState.Idle

    fun canRequestInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    /** Android refuses in-place updates when the installed app has another signer. */
    fun installedSignatureMatches(app: IndexApp): Boolean? {
        val expected = app.signingCertSha256 ?: return null
        val installed = ApkVerifier.installedSigningCertificateSha256(context, app.packageName) ?: return null
        return dev.montra.util.fingerprintsMatch(expected, installed)
    }

    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))

    /**
     * Runs the whole pipeline for one app. Never throws: every failure ends up as
     * [InstallState.Failed] so the UI always has something true to show.
     */
    suspend fun install(app: IndexApp, asset: Asset) {
        try {
            setState(app.id, InstallState.Downloading(0, asset.size, 0f))
            val downloaded = downloader.download(asset) { progress ->
                setState(app.id, InstallState.Downloading(progress.bytes, progress.total, progress.fraction))
            }

            setState(app.id, InstallState.Verifying)
            when (
                val result = withContext(Dispatchers.IO) {
                    ApkVerifier.verify(
                        context = context,
                        file = downloaded.file,
                        expectedSha256 = asset.sha256,
                        expectedCertFingerprint = asset.signingCertSha256 ?: app.signingCertSha256,
                    )
                }
            ) {
                is ApkVerifier.Result.Rejected -> {
                    downloaded.file.delete()
                    setState(app.id, InstallState.Failed(result.reason))
                    return
                }
                is ApkVerifier.Result.Verified -> Unit
            }

            if (!canRequestInstall()) {
                setState(app.id, InstallState.Failed("precisas de autorizar esta app a instalar aplicações desconhecidas"))
                return
            }

            setState(app.id, InstallState.AwaitingUser)
            commit(app, downloaded.file)
        } catch (error: Exception) {
            setState(
                app.id,
                InstallState.Failed(error.message ?: error::class.simpleName ?: "falha desconhecida"),
            )
        }
    }

    private suspend fun commit(app: IndexApp, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(
                PackageInstaller.SessionParams.USER_ACTION_REQUIRED,
            )
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .putExtra(InstallResultReceiver.EXTRA_APP_ID, app.id)
                    .putExtra(InstallResultReceiver.EXTRA_SESSION_ID, sessionId)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            setState(app.id, InstallState.Failed(error.message ?: "o sistema recusou a sessão de instalação"))
            throw error
        } finally {
            apk.delete()
        }
    }

    fun onResult(appId: String, status: Int, message: String?) {
        setState(
            appId,
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> InstallState.Installed(null)
                PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallState.AwaitingUser
                else -> InstallState.Failed(message ?: "instalação recusada pelo sistema (código $status)")
            },
        )
    }

    companion object {
        fun of(context: Context): InstallManager =
            (context.applicationContext as dev.montra.MontraApp).container.installManager

        fun isInstalled(context: Context, packageName: String): Boolean = try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        fun installedVersionCode(context: Context, packageName: String): Long? = try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else @Suppress("DEPRECATION") info.versionCode.toLong()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        fun installedVersionName(context: Context, packageName: String): String? = try {
            context.packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        fun launchIntent(context: Context, packageName: String): Intent? =
            context.packageManager.getLaunchIntentForPackage(packageName)
    }
}

/** Receives the outcome of a PackageInstaller session. Manifest-declared, not exported. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        InstallManager.of(context).onResult(appId, status, message)
    }

    companion object {
        const val EXTRA_APP_ID = "dev.montra.extra.APP_ID"
        const val EXTRA_SESSION_ID = "dev.montra.extra.SESSION_ID"
    }
}

/** Small helper kept next to the installer: is this failure worth retrying? */
fun InstallState.Failed.causeIsNetwork(): Boolean =
    reason.contains("HTTP", ignoreCase = true) || reason.contains("SHA-256", ignoreCase = true) ||
        reason.contains("timeout", ignoreCase = true) || reason.contains("Unable to resolve host", ignoreCase = true)
