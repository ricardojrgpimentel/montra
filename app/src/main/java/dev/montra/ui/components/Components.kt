package dev.montra.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.montra.MontraApp
import dev.montra.ui.theme.Shapes
import dev.montra.ui.theme.Space
import dev.montra.util.monogramColor

/** O que fica no lugar de uma imagem que ainda não chegou, ou que não existe. */
enum class Fallback {
    /**
     * Um monograma derivado do nome do pacote. Para ícones de app: uma app boa que
     * só publica vetores não pode fazer a loja parecer avariada.
     */
    Monogram,

    /**
     * Nada, só a superfície. Para screenshots: uma letra gigante no lugar de uma
     * captura de ecrã era ruído, não um plano B.
     */
    Blank,
}

/**
 * Loads a remote icon through the app's image store, with a deliberate placeholder
 * while it loads and when the index has no image for that app.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    seed: String = contentDescription.orEmpty(),
    monogramText: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    fallback: Fallback = Fallback.Monogram,
) {
    val context = LocalContext.current
    val store = (context.applicationContext as MontraApp).container.imageStore
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = if (url.isNullOrBlank()) null else store.load(url)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        when {
            image != null -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )

            fallback == Fallback.Monogram -> Monogram(
                seed = seed,
                text = monogramText ?: seed.take(1).uppercase(),
                modifier = Modifier.fillMaxSize(),
            )

            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            )
        }
    }
}

/**
 * A flat tinted square with one letter, filling the whole box.
 *
 * The box is the icon: the letter is sized as a fraction of it, the way artwork sits
 * inside an adaptive icon's safe zone. The first version let the box wrap the text
 * instead, so a monogram came out as a ~20dp stamp beside 52dp real icons — which is
 * exactly what made the apps without a logo look broken.
 *
 * The letter is measured and centred by hand because the box is not square in every
 * size it is used at, and font padding otherwise pushes a single capital off-centre.
 */
@Composable
fun Monogram(seed: String, text: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(percent = Shapes.MONOGRAM_PERCENT))
            .background(Color(monogramColor(seed))),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val glyph = with(density) { (minOf(maxWidth, maxHeight) * 0.42f).toSp() }
        val measurer = rememberTextMeasurer()
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(
                fontSize = glyph,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            ),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = (size.width - layout.size.width) / 2f,
                    y = (size.height - layout.size.height) / 2f,
                ),
            )
        }
    }
}

@Composable
fun AppIcon(url: String?, seed: String, text: String, size: Dp = 52.dp) {
    RemoteImage(
        url = url,
        contentDescription = null,
        seed = seed,
        monogramText = text,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(percent = Shapes.MONOGRAM_PERCENT)),
    )
}

/**
 * A section heading: more space above than below, so it belongs to what follows.
 *
 * `divider` desenha a linha de prateleira acima do título. Não é decoração: é o que
 * separa duas secções sem um cartão, e é a única linha horizontal do ecrã.
 */
@Composable
fun SectionTitle(
    text: String,
    trailing: String? = null,
    divider: Boolean = true,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = Space.xl),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (divider) Space.md else Space.xl, bottom = Space.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                // O âmbar só aparece onde há uma decisão a tomar: "Atualizações
                // disponíveis". Todo o resto é contexto, e o contexto é cinzento.
                color = if (accent) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (accent) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(Shapes.badge))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = Space.sm, vertical = 3.dp),
    )
}

/**
 * One line of label/value. The value is monospaced when it is something a user
 * should be able to compare character by character (a hash, a fingerprint).
 */
@Composable
fun KeyValue(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Space.md),
        )
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            textAlign = TextAlign.End,
        )
    }
}

/** A block of content on its own surface. One level: never nested. */
@Composable
fun Block(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color,
        shape = RoundedCornerShape(Shapes.block),
    ) {
        Column(modifier = Modifier.padding(Space.lg)) { content() }
    }
}

/** An alert: the only coloured surface used for something other than a list row. */
@Composable
fun AlertBlock(
    text: String,
    title: String? = null,
    modifier: Modifier = Modifier,
    // Vermelho é para o que corre mal. Uma licença restritiva é uma condição a
    // conhecer, não um erro, e por isso tem o seu próprio tom.
    containerColor: Color = MaterialTheme.colorScheme.errorContainer,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(Shapes.block),
    ) {
        Column(modifier = Modifier.padding(Space.lg)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                )
                Box(Modifier.height(Space.xs))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            if (action != null) {
                Box(Modifier.height(Space.md))
                action()
            }
        }
    }
}

/** Screenshots scroll horizontally with matching space at both ends. */
@Composable
fun ScreenshotRow(urls: List<String>, modifier: Modifier = Modifier) {
    if (urls.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        contentPadding = PaddingValues(horizontal = Space.xs),
    ) {
        items(urls.size) { index ->
            Box(
                modifier = Modifier
                    .size(width = 168.dp, height = 300.dp)
                    .clip(RoundedCornerShape(Space.md)),
            ) {
                RemoteImage(
                    url = urls[index],
                    contentDescription = null,
                    seed = urls[index],
                    contentScale = ContentScale.Crop,
                    fallback = Fallback.Blank,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** The search affordance on the Apps and Games tabs: it navigates, it does not filter. */
@Composable
fun SearchBar(onClick: () -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Shapes.row))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.size(Space.md))
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
