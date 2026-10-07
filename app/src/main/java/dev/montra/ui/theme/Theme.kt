package dev.montra.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One palette, deliberate, documented in DESIGN.md.
 *
 * Deliberately *not* Material You dynamic colour: a store has an identity, and
 * "the app is whatever colour your wallpaper is" is the opposite of one. The
 * greens also keep us away from the purple-and-cyan defaults that make generated
 * interfaces look alike.
 *
 * Every role Material reads is written out here. Leaving one out does not leave it
 * neutral: it falls back to the Material baseline, which is pink-purple. The
 * restricted-licence warning was rendered on `tertiaryContainer` and came out
 * maroon for exactly that reason.
 *
 * Two colours carry meaning and nothing else does:
 *   - verde (primary)  — a ação: instalar, abrir, o que está selecionado.
 *   - âmbar (tertiary) — atenção: uma licença que limita, uma atualização à espera.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E6B4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F1CE),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4E6355),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D8),
    onSecondaryContainer = Color(0xFF0B1F14),
    // Âmbar: a segunda cor da casa. É atenção, não decoração — novidades, licenças
    // restritivas e tudo o que exige uma decisão antes de continuar.
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF2B1700),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF8),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFFBFDF8),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F8F2),
    surfaceContainer = Color(0xFFEEF3EC),
    surfaceContainerHigh = Color(0xFFE8EEE6),
    surfaceContainerHighest = Color(0xFFE2E8E0),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC0C9C1),
    inverseSurface = Color(0xFF2E312E),
    inverseOnSurface = Color(0xFFEFF1ED),
    inversePrimary = Color(0xFF99D5B2),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF99D5B2),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF12512F),
    onPrimaryContainer = Color(0xFFB4F1CE),
    secondary = Color(0xFFB5CCBA),
    onSecondary = Color(0xFF203527),
    secondaryContainer = Color(0xFF364B3D),
    onSecondaryContainer = Color(0xFFD1E8D8),
    tertiary = Color(0xFFE8B06A),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF4A3208),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101410),
    onBackground = Color(0xFFE0E4DE),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE0E4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    inverseSurface = Color(0xFFE0E4DE),
    inverseOnSurface = Color(0xFF2E312E),
    inversePrimary = Color(0xFF2E6B4F),
)

/**
 * A type scale with a floor: nothing in the interface goes below 11sp, and
 * controls stay at 12sp or above. Metadata at 11sp was the old default and read as
 * noise on a phone.
 */
private val MontraTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontSize = 12.sp),
        labelSmall = labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.4.sp),
    )
}

/** The spacing scale from DESIGN.md: 4 · 8 · 12 · 16 · 24 · 32. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Shapes, so no composable invents its own radius. */
object Shapes {
    val row = 16.dp
    val badge = 8.dp
    val block = 16.dp
    val sheet = 24.dp

    /** Ícones de app: o mesmo raio relativo em qualquer tamanho. */
    const val MONOGRAM_PERCENT = 28
}

/**
 * Durações e curvas, num sítio só. Sem `bounce` e sem `spring` visível: o
 * movimento existe para explicar uma mudança, não para chamar atenção.
 */
object Motion {
    const val QUICK = 120
    const val STANDARD = 240
    const val SLOW = 400
    val easing = FastOutSlowInEasing

    fun <T> standard() = tween<T>(durationMillis = STANDARD, easing = easing)
    fun <T> quick() = tween<T>(durationMillis = QUICK, easing = easing)
}

/** Monospace for the things a user should be able to compare character by character. */
val MonospaceStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

@Composable
fun MontraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = MontraTypography,
        content = content,
    )
}
