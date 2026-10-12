package dev.montra

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.montra.ui.Permissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A classificação das permissões, no aparelho.
 *
 * A regra é perguntar ao sistema qual é o nível de proteção, e não manter uma lista de
 * permissões perigosas nossa. Se isto falhar, é o sistema que mudou de ideias — e é
 * exatamente por isso que não há aqui uma lista à mão: uma lista envelhece em silêncio,
 * e uma permissão que deixasse de ser assinalada daria a uma app um atestado de
 * inocência que ninguém verificou.
 */
@RunWith(AndroidJUnit4::class)
class PermissionsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theSystemSaysWhichPermissionsAskForAttention() {
        val lines = Permissions.lines(
            context,
            listOf(
                "android.permission.CAMERA",
                "android.permission.INTERNET",
                "android.permission.ACCESS_FINE_LOCATION",
            ),
        )

        assertTrue("a câmara pede atenção", lines.first { it.name.endsWith("CAMERA") }.sensitive)
        assertTrue(
            "a localização fina pede atenção",
            lines.first { it.name.endsWith("ACCESS_FINE_LOCATION") }.sensitive,
        )
        assertFalse("a internet não pede", lines.first { it.name.endsWith("INTERNET") }.sensitive)
        // As que pedem atenção ficam à frente: quem lê a lista de cima para baixo vê
        // primeiro o que muda a decisão.
        assertTrue("as que pedem atenção deviam vir primeiro", lines.first().sensitive)
    }

    @Test
    fun aPermissionTheSystemDoesNotKnowIsNotAccusedOfAnything() {
        val line = Permissions.lines(context, listOf("com.example.permission.SOMETHING")).single()

        assertFalse("não se inventa um nível para o que não se conhece", line.sensitive)
        // Sem nome do sistema, fica o nome curto: feio, mas é verdade.
        assertEquals("SOMETHING", line.label)
    }

    @Test
    fun showingNotificationsIsNotTreatedAsReachingPersonalData() {
        // O sistema classifica-a como perigosa, e é — mas não chega a dados nenhuns, e
        // assinalá-la punha a nota âmbar em quase todas as apps. Uma cor que aparece
        // sempre deixa de dizer o que quer que seja.
        val line = Permissions.lines(context, listOf("android.permission.POST_NOTIFICATIONS")).single()

        assertFalse("mostrar notificações não é chegar a dados pessoais", line.sensitive)
        assertTrue("mas continua a ser mostrada", line.label.isNotBlank())
    }

    @Test
    fun theLabelAndDescriptionComeFromTheSystemInTheReadersLanguage() {
        val camera = Permissions.lines(context, listOf("android.permission.CAMERA")).single()

        assertFalse("o rótulo devia vir do sistema, não do nome técnico", camera.label == "CAMERA")
        assertTrue("o rótulo não pode ser vazio", camera.label.isNotBlank())
    }
}
