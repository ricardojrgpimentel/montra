package dev.montra.install

import dev.montra.util.asString
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.montra.MainActivity
import dev.montra.R
import dev.montra.util.formatBytes

/**
 * The download notification.
 *
 * A store that downloads 300 MB APKs must not be a store you have to keep staring
 * at: this is why the download runs in a foreground service, and the notification
 * is the visible half of that contract. It shows real bytes, not a spinner, and
 * tapping it goes straight to the app being installed.
 */
object InstallNotifications {

    const val CHANNEL_DOWNLOADS = "downloads"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                ContextCompat.getContextForLanguage(context).getString(R.string.channel_downloads),
                // LOW: a download is expected work the user started. It must be
                // visible and updatable, but it must never make a sound.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = ContextCompat.getContextForLanguage(context).getString(R.string.channel_downloads_description)
                setShowBadge(false)
            },
        )
    }

    fun progress(
        context: Context,
        request: InstallRequest,
        bytes: Long,
        total: Long,
    ): Notification {
        val percent = if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 100) else 0
        val indeterminate = total <= 0
        return base(context, request)
            .setContentTitle(ContextCompat.getContextForLanguage(context).getString(R.string.notification_downloading, request.appName))
            .setContentText(
                if (indeterminate) formatBytes(bytes) else "${formatBytes(bytes)} / ${formatBytes(total)}",
            )
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .addAction(0, ContextCompat.getContextForLanguage(context).getString(R.string.action_cancel), cancelIntent(context, request))
            .build()
    }

    fun verifying(context: Context, request: InstallRequest): Notification =
        base(context, request)
            .setContentTitle(ContextCompat.getContextForLanguage(context).getString(R.string.notification_verifying, request.appName))
            .setContentText(ContextCompat.getContextForLanguage(context).getString(R.string.notification_verifying_detail))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()

    fun awaitingUser(
        context: Context,
        request: InstallRequest,
        confirmation: PendingIntent,
    ): Notification =
        base(context, request)
            .setContentIntent(confirmation)
            .setContentTitle(ContextCompat.getContextForLanguage(context).getString(R.string.notification_confirm, request.appName))
            .setContentText(ContextCompat.getContextForLanguage(context).getString(R.string.notification_confirm_detail))
            .setProgress(0, 0, false)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, ContextCompat.getContextForLanguage(context).getString(R.string.action_cancel), cancelIntent(context, request))
            .build()

    fun dismiss(context: Context, appId: String) {
        NotificationManagerCompat.from(context).cancel(appId, NOTIFICATION_ID)
    }

    /** The receiver owns updates after the download service has stopped. */
    fun showResult(context: Context, request: InstallRequest, state: InstallState) {
        val notification = when (state) {
            is InstallState.AwaitingUser -> awaitingUser(context, request, state.confirmation ?: return)
            is InstallState.Installed -> installed(context, request)
            is InstallState.Failed -> failed(context, request, state.reason.asString(ContextCompat.getContextForLanguage(context)))
            InstallState.NeedsPermission -> failed(context, request, ContextCompat.getContextForLanguage(context).getString(R.string.notification_needs_permission))
            else -> {
                dismiss(context, request.appId)
                return
            }
        }
        ensureChannel(context)
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) {
            runCatching {
                NotificationManagerCompat.from(context).notify(request.appId, NOTIFICATION_ID, notification)
            }
        }
    }

    fun installed(context: Context, request: InstallRequest): Notification =
        base(context, request)
            .setContentTitle(ContextCompat.getContextForLanguage(context).getString(R.string.notification_installed, request.appName))
            .setContentText(ContextCompat.getContextForLanguage(context).getString(R.string.notification_installed_detail))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    fun failed(context: Context, request: InstallRequest, reason: String): Notification =
        base(context, request)
            .setContentTitle(ContextCompat.getContextForLanguage(context).getString(R.string.notification_failed, request.appName))
            .setContentText(reason.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    private fun base(context: Context, request: InstallRequest): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openAppIntent(context, request))
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

    /** Tapping the notification opens the app straight on the app being installed. */
    private fun openAppIntent(context: Context, request: InstallRequest): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(InstallRequest.EXTRA_APP_ID, request.appId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            request.appId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag(),
        )
    }

    private fun cancelIntent(context: Context, request: InstallRequest): PendingIntent {
        val intent = Intent(context, InstallService::class.java)
            .setAction(InstallService.ACTION_CANCEL)
            .putExtra(InstallRequest.EXTRA_APP_ID, request.appId)
        return PendingIntent.getService(
            context,
            request.appId.hashCode() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag(),
        )
    }

    /**
     * The system fills in extras on some of these PendingIntents, which requires
     * FLAG_MUTABLE from API 31. Mutable PendingIntents are only safe here because
     * every one of them targets our own components with an explicit intent.
     */
    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
}
