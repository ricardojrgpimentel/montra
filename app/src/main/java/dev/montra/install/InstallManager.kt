package dev.montra.install

import dev.montra.util.asString
import dev.montra.R
import dev.montra.util.UiText
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

    /** Keep the system action so leaving its dialog does not strand the install. */
    data class AwaitingUser(val confirmation: PendingIntent? = null) : InstallState
    data class Installed(val versionName: String?) : InstallState
    data class Failed(val reason: UiText) : InstallState
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
    private val sessions = context.getSharedPreferences("install_sessions", Context.MODE_PRIVATE)

    fun setState(appId: String, state: InstallState) {
        _states.update { it + (appId to state) }
    }

    fun stateOf(appId: String): InstallState = _states.value[appId] ?: InstallState.Idle

    internal fun trackSession(appId: String, sessionId: Int) {
        sessions.edit().putInt(appId, sessionId).commit()
    }

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

    fun confirmInstall(appId: String) {
        val state = stateOf(appId) as? InstallState.AwaitingUser ?: return
        val confirmation = state.confirmation ?: return
        runCatching { confirmation.send() }.onFailure {
            cancelPendingInstall(appId)
            setState(appId, InstallState.Failed(UiText.Resource(R.string.installer_unavailable)))
        }
    }

    fun cancelPendingInstall(appId: String) {
        val sessionId = sessions.getInt(appId, -1)
        sessions.edit().remove(appId).commit()
        (stateOf(appId) as? InstallState.AwaitingUser)?.confirmation?.cancel()
        if (sessionId != -1) {
            runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
        }
        setState(appId, InstallState.Idle)
        InstallNotifications.dismiss(context, appId)
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
                    Log.e("${request.appId}: verificação falhou — ${result.reason.asString(context)}")
                    downloaded.file.delete()
                    setState(request.appId, InstallState.Failed(result.reason))
                    return
                }
                is ApkVerifier.Result.Verified -> Log.i(
                    "${request.appId}: verificado (sha256 e certificado ${result.certSha256?.take(17)}…)",
                )
            }

            setState(request.appId, InstallState.AwaitingUser())
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
                InstallState.Failed(UiText.Resource(R.string.install_unknown)),
            )
        }
    }

    private suspend fun commit(request: InstallRequest, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        sessions.edit().remove(request.appId).commit()
        // Also recover sessions left by an older process/version which lost its Intent.
        installer.mySessions.filter { it.appPackageName == request.packageName }.forEach {
            installer.abandonSession(it.sessionId)
        }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(request.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        trackSession(request.appId, sessionId)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val intent = request.putInto(Intent(context, InstallResultReceiver::class.java))
                    .putExtra(InstallResultReceiver.EXTRA_SESSION_ID, sessionId)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (error: Exception) {
            sessions.edit().remove(request.appId).commit()
            runCatching { installer.abandonSession(sessionId) }
            Log.e("${request.appId}: o sistema recusou a sessão de instalação", error)
            setState(
                request.appId,
                InstallState.Failed(UiText.Resource(R.string.install_unknown)),
            )
        } finally {
            apk.delete()
        }
    }

    fun onResult(
        appId: String,
        sessionId: Int,
        status: Int,
        message: String?,
        confirmation: Intent? = null,
    ): Boolean {
        // A late/duplicate callback from a cancelled attempt must not reset a retry.
        if (sessions.getInt(appId, -1) != sessionId || sessionId == -1) return false
        Log.i("$appId: resultado do instalador status=$status mensagem=${message ?: "-"}")
        val action = confirmation?.let {
            PendingIntent.getActivity(
                context, sessionId, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION || action == null) {
            sessions.edit().remove(appId).commit()
            (stateOf(appId) as? InstallState.AwaitingUser)?.confirmation?.cancel()
        }
        setState(
            appId,
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> InstallState.Installed(null)
                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallState.Idle
                PackageInstaller.STATUS_PENDING_USER_ACTION -> if (action != null) {
                    InstallState.AwaitingUser(action)
                } else {
                    runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
                    InstallState.Failed(UiText.Resource(R.string.installer_unavailable))
                }
                else -> InstallState.Failed(UiText.Resource(R.string.install_system_rejected, listOf(status)))
            },
        )
        return true
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

        val manager = InstallManager.of(context)
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        if (!manager.onResult(appId, sessionId, status, message, confirmation)) return
        InstallRequest.from(intent)?.let { request ->
            InstallNotifications.showResult(context, request, manager.stateOf(appId))
        }

        if (confirmation != null) {
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(confirmation) }
                .onFailure { Log.w("$appId: não consegui abrir o diálogo do instalador", it) }
        }

    }

    companion object {
        const val EXTRA_APP_ID = "dev.montra.extra.APP_ID"
        const val EXTRA_SESSION_ID = "dev.montra.extra.SESSION_ID"
    }
}
