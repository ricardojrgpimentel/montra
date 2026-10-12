package dev.montra

import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.montra.util.Notifications
import dev.montra.work.UpdateNotifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A notificação das atualizações, no aparelho: aparece, diz o número certo e sai. */
@RunWith(AndroidJUnit4::class)
class UpdatesNotificationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun active() = manager.activeNotifications
        .firstOrNull { it.id == UpdateNotifications.NOTIFICATION_ID }

    @After
    fun cleanUp() {
        UpdateNotifications.dismiss(context)
    }

    /**
     * O singular e o plural, no idioma do aparelho. Um plural em falta não rebenta:
     * o Android cai no recurso por omissão e a notificação passa a dizer "1
     * atualizações", que é o género de erro que ninguém vê num teste de compilação.
     */
    @Test
    fun oneUpdateIsSaidInTheSingularAndThreeInThePlural() {
        val one = context.resources.getQuantityString(R.plurals.notification_updates, 1, 1)
        val three = context.resources.getQuantityString(R.plurals.notification_updates, 3, 3)

        assertTrue("o singular tem de dizer 1: $one", one.contains("1"))
        assertTrue("o plural tem de dizer 3: $three", three.contains("3"))
        assertTrue("singular e plural não podem ser a mesma frase", one != three)
    }

    @Test
    fun theUpdateNotificationIsPostedAndCanBeDismissed() {
        // Sem autorização para notificar, nada aparece — e é isso que a app faz, sem
        // falhar: o mesmo conteúdo está no ecrã. O teste di-lo em vez de dar um erro
        // que não é da app.
        assumeTrue(
            "POST_NOTIFICATIONS não está autorizada neste aparelho",
            Notifications.canPost(context),
        )

        UpdateNotifications.show(context, 3)
        // Publicar é um pedido assíncrono ao sistema: espera-se, não se assume.
        eventually("a notificação das atualizações não apareceu") { active() != null }
        val posted = requireNotNull(active()) { "a notificação das atualizações não apareceu" }
        val title = posted.notification.extras.getString(Notification.EXTRA_TITLE)
        assertEquals(
            context.resources.getQuantityString(R.plurals.notification_updates, 3, 3),
            title,
        )
        // Tocar tem de levar a algum lado: uma notificação sem destino é um beco.
        assertNotNull(posted.notification.contentIntent)

        UpdateNotifications.dismiss(context)
        eventually("a notificação das atualizações não saiu depois de dispensada") { active() == null }
    }
}
