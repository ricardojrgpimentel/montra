package dev.montra.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A escolha claro/escuro é do utilizador, mas os ícones da barra de estado são do
 * sistema — e é esta função que decide quem ganha. Se ela se enganar, o resultado é
 * texto branco sobre fundo branco.
 */
class ThemeModeTest {

    @Test
    fun `sistema segue o que o telemovel diz`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun `claro e escuro ignoram o sistema`() {
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun `por omissao segue o sistema`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DEFAULT)
        assertEquals(ThemeMode.SYSTEM, ThemeMode.of(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.of("qualquer coisa"))
    }

    @Test
    fun `o que foi guardado é o que volta a ser lido`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.of(mode.name))
        }
    }
}
