package dev.montra

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
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

    @After
    fun cleanUp() {
        manager.cancelPendingInstall(request.appId)
        createdSessions.forEach { runCatching { installer.abandonSession(it) } }
    }

    @Test
    fun rejectedConfirmationClearsNotificationAndAllowsRetryWithoutOldCallbacksWinning() {
        val first = session()
        awaitConfirmation(first)
        assertNotNull(notification())
        deliver(first, PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(InstallState.Idle, manager.stateOf(request.appId))
        assertNull(notification())

        val retry = session()
        val action = awaitConfirmation(retry)
        // Android delivered the abort twice on the Samsung used to report this bug.
        deliver(first, PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(action, (manager.stateOf(request.appId) as InstallState.AwaitingUser).confirmation)
        assertNotNull(notification())
    }

    @Test
    fun notificationRetainsTheSameConfirmationActionAsTheScreen() {
        InstallNotifications.showResult(context, request, InstallState.AwaitingUser())
        assertNull(notification())
        val action = awaitConfirmation(session())
        assertEquals(action, notification()!!.notification.contentIntent)
        assertTrue(notification()!!.notification.actions.isNotEmpty())
    }

    @Test
    fun cancellingPendingInstallAbandonsSessionAndRemovesNotification() {
        val id = session()
        awaitConfirmation(id)
        manager.cancelPendingInstall(request.appId)
        assertNull(installer.getSessionInfo(id))
        assertNull(notification())
        assertEquals(InstallState.Idle, manager.stateOf(request.appId))
        deliver(id, PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(InstallState.Idle, manager.stateOf(request.appId))
    }

    @Test
    fun missingConfirmationIsRecoverableInsteadOfWaitingForever() {
        val id = session()
        assertTrue(manager.onResult(request.appId, id, PackageInstaller.STATUS_PENDING_USER_ACTION, null))
        assertTrue(manager.stateOf(request.appId) is InstallState.Failed)
        assertNull(installer.getSessionInfo(id))
    }

    @Test
    fun sessionIdentitySurvivesManagerRecreationAndSuccessReplacesPendingNotification() {
        val id = session()
        awaitConfirmation(id)
        val recreated = InstallManager(context, ApkDownloader(context, OkHttpClient()))
        assertFalse(recreated.onResult(request.appId, id + 1, PackageInstaller.STATUS_SUCCESS, null))
        assertTrue(recreated.onResult(
            request.appId, id, PackageInstaller.STATUS_PENDING_USER_ACTION, null,
            Intent(context, MainActivity::class.java).setAction("dev.montra.TEST_CONFIRMATION"),
        ))
        // The real receiver also works after the service has gone away.
        deliver(id, PackageInstaller.STATUS_SUCCESS)
        assertTrue(manager.stateOf(request.appId) is InstallState.Installed)
        val result = notification()!!.notification
        assertEquals(0, result.flags and android.app.Notification.FLAG_ONGOING_EVENT)
        assertEquals(context.getString(R.string.notification_installed, request.appName),
            result.extras.getString(android.app.Notification.EXTRA_TITLE))
    }
}
