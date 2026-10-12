package dev.montra.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * "Posso mostrar isto?" — perguntado uma vez, num sítio só.
 *
 * Um utilizador que recusou notificações não pode perder nada por isso: o ecrã da
 * app mostra o mesmo progresso e as mesmas atualizações. Portanto publicar é sempre
 * uma tentativa verificada e best-effort, e di-lo no log em vez de falhar em
 * silêncio. A regra estava escrita duas vezes — na transferência e no resultado —
 * e uma terceira estava a caminho com as atualizações.
 */
object Notifications {

    fun canPost(context: Context): Boolean {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return allowed && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * @param tag distingue notificações do mesmo [id]. É o que permite que o
     * resultado de uma app e o progresso de outra coexistam, em vez de uma substituir
     * a outra.
     */
    // O lint não segue a verificação através do `canPost()`, e a chamada está de
    // qualquer maneira dentro de um `runCatching`: publicar é sempre best-effort, e o
    // ecrã da app mostra o mesmo conteúdo quando isto não passa.
    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, notification: Notification, tag: String? = null) {
        if (!canPost(context)) {
            Log.d("notificações não autorizadas: o conteúdo continua visível no ecrã da app")
            return
        }
        val manager = NotificationManagerCompat.from(context)
        runCatching {
            if (tag == null) manager.notify(id, notification) else manager.notify(tag, id, notification)
        }
    }
}
