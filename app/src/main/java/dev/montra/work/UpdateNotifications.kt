package dev.montra.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.montra.MainActivity
import dev.montra.R
import dev.montra.util.Notifications

/**
 * "Há atualizações" — a única vez em que a app fala sem lhe pedirem.
 *
 * É por isso que ela só fala quando tem algo a dizer e só o diz uma vez por conjunto
 * de atualizações: uma loja que repete "3 atualizações" de hora a hora é uma loja que
 * se desliga.
 */
object UpdateNotifications {

    const val CHANNEL_UPDATES = "updates"
    const val NOTIFICATION_ID = 1002

    /** Lido pela MainActivity para abrir o separador certo. */
    const val EXTRA_OPEN_UPDATES = "dev.montra.extra.OPEN_UPDATES"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATES,
                ContextCompat.getContextForLanguage(context).getString(R.string.channel_updates),
                // DEFAULT, e não LOW como o canal das transferências: um download é
                // trabalho que a pessoa pediu e não deve fazer barulho; isto é uma
                // notícia que ela não pediu e que só serve se a vir.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = ContextCompat.getContextForLanguage(context)
                    .getString(R.string.channel_updates_description)
            },
        )
    }

    fun show(context: Context, count: Int) {
        ensureChannel(context)
        val strings = ContextCompat.getContextForLanguage(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(strings.resources.getQuantityString(R.plurals.notification_updates, count, count))
            .setContentText(strings.getString(R.string.notification_updates_detail))
            .setContentIntent(openUpdatesIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            // Uma verificação que não mudou nada não volta a incomodar: só o conjunto
            // de atualizações diferente é que fala.
            .setOnlyAlertOnce(true)
            .build()
        Notifications.post(context, NOTIFICATION_ID, notification)
    }

    fun dismiss(context: Context) {
        androidx.core.app.NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Tocar abre a app no separador das apps, onde as atualizações estão à frente. */
    private fun openUpdatesIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_OPEN_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
        )
    }
}
