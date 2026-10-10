package dev.montra.install

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.montra.R
import dev.montra.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Runs one install in the foreground.
 *
 * Why a service instead of the ViewModel: the catalogue contains APKs over 300 MB,
 * and a download that dies when the user switches to another app (or when Android
 * reclaims a cached process) is a broken store. A foreground service keeps the
 * process alive, gives the user real progress in the shade, and makes the download
 * a thing they can leave running — and cancel from the notification.
 *
 * The service owns no state: it drives [InstallManager], whose StateFlow the UI
 * reads, so the screen and the notification can never disagree.
 */
class InstallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var current: InstallRequest? = null
    private var lastProgressNotification = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val appId = intent.getStringExtra(InstallRequest.EXTRA_APP_ID)
            if (appId != null && current?.appId == appId) {
                cancelCurrent()
            } else if (appId != null) {
                InstallManager.of(this).cancelPendingInstall(appId)
                if (current == null) stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        val request = intent?.let { InstallRequest.from(it) }
        if (request == null) {
            // Nothing to do: never leave a foreground service running with no work.
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // The downloader is shared: do not let two pipelines cancel or overwrite each other.
        if (current != null) return START_NOT_STICKY

        InstallNotifications.ensureChannel(this)
        InstallNotifications.dismiss(this, request.appId)
        current = request
        lastProgressNotification = SystemClock.elapsedRealtime()

        // Must happen fast after startForegroundService, so this notification is
        // built synchronously and shows "0 bytes" until the first progress event.
        ServiceCompat.startForeground(
            this,
            InstallNotifications.NOTIFICATION_ID,
            InstallNotifications.progress(this, request, 0, request.asset.size),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        job?.cancel()
        job = scope.launch {
            // Mirror the pipeline state into the notification for as long as this
            // service is the one doing the work.
            val mirror = launch {
                val manager = InstallManager.of(this@InstallService)
                manager.states.collectLatest { states ->
                    val state = states[request.appId] ?: return@collectLatest
                    if (current?.appId != request.appId) return@collectLatest
                    notify(request, state)
                }
            }
            try {
                InstallManager.of(this@InstallService).install(request)
            } finally {
                mirror.cancel()
                finish(request)
            }
        }
        return START_NOT_STICKY
    }

    private fun notify(request: InstallRequest, state: InstallState) {
        // A fast download can emit hundreds of chunks per second. Android drops
        // notification updates above its rate limit, including the final action.
        if (state is InstallState.Downloading) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastProgressNotification < 500) return
            lastProgressNotification = now
        }
        val notification = when (state) {
            is InstallState.Downloading ->
                InstallNotifications.progress(this, request, state.bytes, state.total)
            is InstallState.Verifying -> InstallNotifications.verifying(this, request)
            is InstallState.AwaitingUser -> return // The result receiver posts the actionable notification.
            is InstallState.NeedsPermission -> InstallNotifications.failed(
                this,
                request,
                getString(R.string.notification_needs_permission),
            )
            is InstallState.Installed -> InstallNotifications.installed(this, request)
            is InstallState.Failed -> InstallNotifications.failed(this, request, state.reason)
            InstallState.Idle -> return
        }
        post(notification)
    }

    /**
     * A user who denied notifications must not lose the download: the app's own
     * screen shows the same progress. So this is a checked, best-effort post — and
     * it says so in the log rather than failing silently.
     */
    private fun post(notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(this)
        if (!allowed || !manager.areNotificationsEnabled()) {
            Log.d("notificações não autorizadas: o progresso continua visível no ecrã da app")
            return
        }
        runCatching { manager.notify(InstallNotifications.NOTIFICATION_ID, notification) }
    }

    private fun finish(request: InstallRequest) {
        val state = InstallManager.of(this).stateOf(request.appId)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Only the receiver has the actual confirmation action. Posting a placeholder
        // here can race with its notification and leave a non-actionable one behind.
        if (state !is InstallState.AwaitingUser) {
            InstallNotifications.showResult(this, request, state)
        }
        current = null
        stopSelf()
    }

    private fun cancelCurrent() {
        Log.i("instalação cancelada pelo utilizador na notificação")
        job?.cancel()
        val request = current
        if (request != null) {
            InstallManager.of(this).setState(request.appId, InstallState.Idle)
            InstallManager.of(this).cancelDownload()
            runCatching { NotificationManagerCompat.from(this).cancel(InstallNotifications.NOTIFICATION_ID) }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "dev.montra.action.CANCEL_INSTALL"

        /** Starts (or restarts) the pipeline for [request] in the foreground. */
        fun start(context: Context, request: InstallRequest) {
            val intent = request.putInto(Intent(context, InstallService::class.java))
            context.startForegroundService(intent)
        }

        fun cancel(context: Context, appId: String) {
            val intent = Intent(context, InstallService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(InstallRequest.EXTRA_APP_ID, appId)
            context.startService(intent)
        }
    }
}
