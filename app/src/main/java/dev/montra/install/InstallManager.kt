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
import dev.montra.security.ApkVerifier
import dev.montra.util.Log
import dev.montra.util.fingerprintsMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val bytes: Long, val total: Long, val fraction: Float) : InstallState

    /** Download complete, now checking hash and signature before asking the system. */
    data object Verifying : InstallState

    /**
     * Android has not been told that this app may install packages. This is not a
     * failure — it is a permission the user grants in two taps, so the UI is
     * expected to offer that, not merely report it.
     */
    data object NeedsPermission : InstallState

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
 *
 * A singleton owned by the application container, because the work runs in
 * [InstallService] while the UI observes it: one state machine, two views of it
 * (the screen and the notification).
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
    fun installedSignatureMatches(asset: Asset, packageName: String): Boolean? {
        val expected = asset.signingCertSha256 ?: return null
        val installed = ApkVerifier.installedSigningCertificateSha256(context, packageName) ?: return null
        return fingerprintsMatch(expected, installed)
    }

    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))

    fun cancelDownload() {
        downloader.cancel()
    }

    /**
     * Runs the whole pipeline for one app. Never throws on failure: every problem
     * ends up as a state the UI can explain.
     */
    suspend fun install(request: InstallRequest) {
        // Checked before a single byte is downloaded. Asking for 15 MB and only then
        // saying "you never granted me permission" wastes the user's data and their
        // patience — and it is the wrong order for a permission we can prompt for.
        if (!canRequestInstall()) {
            Log.i("${request.appId}: falta autorização para instalar apps desconhecidas")
            setState(request.appId, InstallState.NeedsPermission)
            return
        }

        val asset = request.asset
        try {
            Log.i("instalar ${request.appId} (${asset.abi}, ${asset.size} bytes, sha256 ${asset.sha256.take(16)}…)")
            setState(request.appId, InstallState.Downloading(0, asset.size, 0f))
            val downloaded = downloader.download(asset) { progress ->
                setState(
                    request.appId,
                    InstallState.Downloading(progress.bytes, progress.total, progress.fraction),
                )
            }

            setState(request.appId, InstallState.Verifying)
            when (
                val result = withContext(Dispatchers.IO) {
                    ApkVerifier.verify(
                        context = context,
                        file = downloaded.file,
                        expectedSha256 = asset.sha256,
                        expectedCertFingerprint = asset.signingCertSha256 ?: request.pinnedCertSha256,
                    )
                }
            ) {
                is ApkVerifier.Result.Rejected -> {
                    Log.e("${request.appId}: verificação falhou — ${result.reason}")
                    downloaded.file.delete()
                    setState(request.appId, InstallState.Failed(result.reason))
                    return
                }
                is ApkVerifier.Result.Verified -> Log.i(
                    "${request.appId}: verificado (sha256 e certificado ${result.certSha256?.take(17)}…)",
                )
            }

            setState(request.appId, InstallState.AwaitingUser)
            commit(request, downloaded.file)
        } catch (cancelled: CancellationException) {
            Log.i("${request.appId}: download cancelado")
            setState(request.appId, InstallState.Idle)
            throw cancelled
        } catch (error: Exception) {
            if (!currentCoroutineContext().isActive) {
                // Cancelled mid-flight: an error state here would contradict the
                // cancel the user just asked for.
                setState(request.appId, InstallState.Idle)
                return
            }
            Log.e("${request.appId}: instalação falhou", error)
            setState(
                request.appId,
                InstallState.Failed(error.message ?: error::class.simpleName ?: "falha desconhecida"),
            )
        }
    }

    private suspend fun commit(request: InstallRequest, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(request.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .putExtra(InstallResultReceiver.EXTRA_APP_ID, request.appId)
                    .putExtra(InstallResultReceiver.EXTRA_SESSION_ID, sessionId)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            Log.e("${request.appId}: o sistema recusou a sessão de instalação", error)
            setState(
                request.appId,
                InstallState.Failed(error.message ?: "o sistema recusou a sessão de instalação"),
            )
        } finally {
            apk.delete()
        }
    }

    fun onResult(appId: String, status: Int, message: String?) {
        Log.i("$appId: resultado do instalador status=$status mensagem=${message ?: "-"}")
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

/**
 * Receives the outcome of a PackageInstaller session. Manifest-declared, not exported.
 *
 * The subtlety that cost an afternoon: when a session commits with
 * STATUS_PENDING_USER_ACTION, the platform hands the app an Intent (EXTRA_INTENT)
 * that shows the confirmation dialog, and expects the app to launch it. Get this
 * wrong and the install does not fail — it hangs, with a session waiting forever
 * for a dialog nobody is showing. Observed exactly that on Android 16, so the
 * intent is launched whenever it is delivered, on every API level.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        val confirmation = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            }
        } else {
            null
        }

        Log.d(
            "$appId: resultado status=$status, extras=${intent.extras?.keySet()?.joinToString()}, " +
                "diálogo do sistema=${confirmation != null}",
        )

        if (confirmation != null) {
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(confirmation) }
                .onFailure { Log.w("$appId: não consegui abrir o diálogo do instalador", it) }
        }

        InstallManager.of(context).onResult(appId, status, message)
    }

    companion object {
        const val EXTRA_APP_ID = "dev.montra.extra.APP_ID"
        const val EXTRA_SESSION_ID = "dev.montra.extra.SESSION_ID"
    }
}
