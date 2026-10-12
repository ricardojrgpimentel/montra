package dev.montra.install

import dev.montra.util.asString
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.montra.R
import dev.montra.util.Log
import dev.montra.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Runs installs in the foreground, one at a time.
 *
 * Why a service instead of the ViewModel: the catalogue contains APKs over 300 MB,
 * and a download that dies when the user switches to another app (or when Android
 * reclaims a cached process) is a broken store. A foreground service keeps the
 * process alive, gives the user real progress in the shade, and makes the download
 * a thing they can leave running — and cancel from the notification.
 *
 * One at a time, and the rest in a queue: the downloader is shared, so two
 * pipelines would cancel or overwrite each other. A second request used to be
 * dropped on the floor — no state, no error, no warning — and the button looked
 * dead. It now waits its turn and says so.
 *
 * The service owns no state: it drives [InstallManager], whose StateFlow the UI
 * reads, so the screen and the notification can never disagree.
 */
class InstallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var current: InstallRequest? = null

    /** O que ainda não começou, por ordem de chegada. Vive só enquanto o serviço vive. */
    private val queue = ArrayDeque<InstallRequest>()
    private var lastProgressNotification = 0L
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId

        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(InstallRequest.EXTRA_APP_ID)?.let { cancel(it) }
            return START_NOT_STICKY
        }

        val request = intent?.let { InstallRequest.from(it) }
        if (request == null) {
            // Nothing to do: never leave a foreground service running with no work.
            if (current == null) stopSelf(startId)
            return START_NOT_STICKY
        }

        // O mesmo pedido duas vezes é a mesma intenção, não duas instalações.
        if (current?.appId == request.appId) return START_NOT_STICKY
        queue.removeAll { it.appId == request.appId }
        queue.addLast(request)

        if (current == null) {
            pump()
        } else {
            // Já há uma app a instalar: esta fica à espera da sua vez, e di-lo. Antes
            // o pedido era descartado em silêncio e o botão parecia avariado.
            InstallManager.of(this).setState(request.appId, InstallState.Queued)
        }
        return START_NOT_STICKY
    }

    /** Arranca o próximo da fila, ou fecha o serviço quando já não há nada. */
    private fun pump() {
        if (current != null) return
        val next = queue.removeFirstOrNull()
        if (next == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(lastStartId)
            return
        }
        startPipeline(next)
    }

    /**
     * Corre um pedido do princípio ao fim.
     *
     * Só é chamado com `current` a null: o pipeline anterior acabou de terminar, ou
     * nunca existiu. Não há aqui nada para cancelar — e cancelar o `job` de dentro do
     * `finish()` dele seria cancelarmo-nos a nós próprios.
     */
    private fun startPipeline(request: InstallRequest) {
        current = request
        lastProgressNotification = SystemClock.elapsedRealtime()

        InstallNotifications.ensureChannel(this)
        InstallNotifications.dismiss(this, request.appId)

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
    }

    /**
     * Cancela o que estiver a correr, ou tira da fila o que ainda não começou, ou
     * abandona a sessão que está à espera do diálogo do sistema.
     */
    private fun cancel(appId: String) {
        if (current?.appId == appId) {
            cancelCurrent()
            return
        }
        if (queue.removeAll { it.appId == appId }) {
            InstallManager.of(this).setState(appId, InstallState.Idle)
        } else {
            // Nem a correr nem em fila: só pode ser uma sessão à espera do
            // utilizador, que é do InstallManager e não do serviço.
            InstallManager.of(this).cancelPendingInstall(appId)
        }
        if (current == null) stopSelf(lastStartId)
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
            is InstallState.Failed -> InstallNotifications.failed(this, request, state.reason.asString(ContextCompat.getContextForLanguage(this)))
            // Ainda não começou: não há progresso nenhum para mostrar, e a
            // notificação pertence a quem está a ser instalado agora.
            InstallState.Queued -> return
            InstallState.Idle -> return
        }
        post(notification)
    }

    /**
     * A notificação de progresso é a mesma que este serviço põe em primeiro plano, e
     * é por isso que não leva tag: o `startForeground` não aceita uma. As de
     * resultado levam `tag = appId` (ver [InstallNotifications.showResult]), para o
     * resultado de uma app e o progresso de outra poderem coexistir em vez de uma
     * substituir a outra. São dois espaços de nomes de propósito.
     */
    private fun post(notification: Notification) {
        Notifications.post(this, InstallNotifications.NOTIFICATION_ID, notification)
    }

    private fun finish(request: InstallRequest) {
        // Um pipeline cancelado termina depois de a fila já ter avançado: o resultado
        // dele já não interessa a ninguém, e tratá-lo começaria um segundo pipeline.
        if (current?.appId != request.appId) return
        val state = InstallManager.of(this).stateOf(request.appId)
        current = null

        if (queue.isNotEmpty()) {
            // Há mais à espera: o serviço continua em primeiro plano e segue para o
            // próximo. O resultado deste fica no ecrã da app — a notificação é
            // substituída já a seguir pelo progresso do próximo, e uma notificação
            // que vive duzentos milissegundos não informa ninguém.
            pump()
            return
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Only the receiver has the actual confirmation action. Posting a placeholder
        // here can race with its notification and leave a non-actionable one behind.
        if (state !is InstallState.AwaitingUser) {
            InstallNotifications.showResult(this, request, state)
        }
        stopSelf(lastStartId)
    }

    private fun cancelCurrent() {
        val request = current ?: return
        // Antes de cancelar: o `finish()` que corre a seguir é de um pipeline que já
        // não é o atual, e não pode voltar a mexer na fila.
        current = null
        Log.i("instalação cancelada pelo utilizador na notificação")
        job?.cancel()
        InstallManager.of(this).setState(request.appId, InstallState.Idle)
        InstallManager.of(this).cancelDownload()
        runCatching { NotificationManagerCompat.from(this).cancel(InstallNotifications.NOTIFICATION_ID) }
        // Quem cancelou uma app não pediu para cancelar as outras.
        pump()
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
