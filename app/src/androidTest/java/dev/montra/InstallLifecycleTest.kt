package dev.montra

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.montra.data.model.Asset
import dev.montra.install.ApkDownloader
import dev.montra.install.InstallManager
import dev.montra.install.InstallNotifications
import dev.montra.install.InstallRequest
import dev.montra.install.InstallResultReceiver
import dev.montra.install.InstallState
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real sessions and notifications, without downloading or installing a third-party app. */
@RunWith(AndroidJUnit4::class)
class InstallLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager get() = InstallManager.of(context)
    private val installer get() = context.packageManager.packageInstaller
    private val request = InstallRequest(
        "install-lifecycle-test", "Install lifecycle test", "dev.montra.fixture", null,
        Asset(abi = "universal", url = "https://example.com/test.apk", sha256 = "00", size = 1),
    )
    private val createdSessions = mutableListOf<Int>()

    private fun session(): Int = installer.createSession(
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(request.packageName)
        },
    ).also {
        createdSessions += it
        manager.trackSession(request.appId, it)
    }

    private fun awaitConfirmation(id: Int): PendingIntent {
        assertTrue(manager.onResult(
            request.appId, id, PackageInstaller.STATUS_PENDING_USER_ACTION, null,
            Intent(context, MainActivity::class.java).setAction("dev.montra.TEST_CONFIRMATION"),
        ))
        val state = manager.stateOf(request.appId) as InstallState.AwaitingUser
        InstallNotifications.showResult(context, request, state)
        return requireNotNull(state.confirmation)
    }

    private fun deliver(id: Int, status: Int) {
        val intent = request.putInto(Intent(context, InstallResultReceiver::class.java))
            .putExtra(InstallResultReceiver.EXTRA_SESSION_ID, id)
            .putExtra(PackageInstaller.EXTRA_STATUS, status)
        instrumentation.runOnMainSync { InstallResultReceiver().onReceive(context, intent) }
    }

    private fun notification() = context.getSystemService(NotificationManager::class.java)
        .activeNotifications.firstOrNull { it.tag == request.appId }

    /**
     * A publicação é um pedido assíncrono ao sistema: espera-se por ela em vez de se
     * assumir que já lá está. Isto falhava só quando a suite estava carregada, que é a
     * pior altura para um teste decidir.
     */
    private fun awaitNotification(): StatusBarNotification {
        eventually("a notificação não apareceu") { notification() != null }
        return requireNotNull(notification())
    }

    @After
    fun cleanUp() {
        manager.cancelPendingInstall(request.appId)
        createdSessions.forEach { runCatching { installer.abandonSession(it) } }
    }

    @Test
    fun rejectedConfirmationClearsNotificationAndAllowsRetryWithoutOldCallbacksWinning() {
        val first = session()
        awaitConfirmation(first)
        awaitNotification()
        deliver(first, PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(InstallState.Idle, manager.stateOf(request.appId))
        eventually("a notificação não saiu depois de a instalação ser abandonada") { notification() == null }

        val retry = session()
        val action = awaitConfirmation(retry)
        // Android delivered the abort twice on the Samsung used to report this bug.
        deliver(first, PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(action, (manager.stateOf(request.appId) as InstallState.AwaitingUser).confirmation)
        awaitNotification()
    }

    @Test
    fun notificationRetainsTheSameConfirmationActionAsTheScreen() {
        InstallNotifications.showResult(context, request, InstallState.AwaitingUser())
        eventually("não devia haver notificação sem confirmação para mostrar") { notification() == null }
        val action = awaitConfirmation(session())
        val posted = awaitNotification()
        assertEquals(action, posted.notification.contentIntent)
        assertTrue(posted.notification.actions.isNotEmpty())
    }

    @Test
    fun cancellingPendingInstallAbandonsSessionAndRemovesNotification() {
        val id = session()
        awaitConfirmation(id)
        manager.cancelPendingInstall(request.appId)
        eventually("a sessão não foi abandonada") { installer.getSessionInfo(id) == null }
        eventually("a notificação não saiu depois de cancelar") { notification() == null }
        assertEquals(InstallState.Idle, manager.stateOf(request.appId))
        deliver(id, PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(InstallState.Idle, manager.stateOf(request.appId))
    }

    @Test
    fun missingConfirmationIsRecoverableInsteadOfWaitingForever() {
        val id = session()
        assertTrue(manager.onResult(request.appId, id, PackageInstaller.STATUS_PENDING_USER_ACTION, null))
        assertTrue(manager.stateOf(request.appId) is InstallState.Failed)
        eventually("a sessão não foi abandonada depois de a confirmação faltar") {
            installer.getSessionInfo(id) == null
        }
    }

    @Test
    fun sessionIdentitySurvivesManagerRecreationAndSuccessReplacesPendingNotification() {
        val id = session()
        awaitConfirmation(id)
        val container = (context.applicationContext as MontraApp).container
        val recreated = InstallManager(
            context,
            ApkDownloader(context, OkHttpClient()),
            container.networkStatus,
            container.settings,
        )
        assertFalse(recreated.onResult(request.appId, id + 1, PackageInstaller.STATUS_SUCCESS, null))
        assertTrue(recreated.onResult(
            request.appId, id, PackageInstaller.STATUS_PENDING_USER_ACTION, null,
            Intent(context, MainActivity::class.java).setAction("dev.montra.TEST_CONFIRMATION"),
        ))
        // The real receiver also works after the service has gone away.
        deliver(id, PackageInstaller.STATUS_SUCCESS)
        assertTrue(manager.stateOf(request.appId) is InstallState.Installed)
        // A notificação já existia (a pendente), portanto não basta esperar que exista:
        // espera-se que seja a nova, senão o teste passava a verificar a antiga.
        val installedTitle = context.getString(R.string.notification_installed, request.appName)
        eventually("a notificação de sucesso não substituiu a pendente") {
            notification()?.notification?.extras?.getString(Notification.EXTRA_TITLE) == installedTitle
        }
        val result = requireNotNull(notification()).notification
        assertEquals(0, result.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(installedTitle, result.extras.getString(Notification.EXTRA_TITLE))
    }
}
