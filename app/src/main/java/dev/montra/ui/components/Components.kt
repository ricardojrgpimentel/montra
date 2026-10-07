package dev.montra.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.montra.MontraApp
import dev.montra.ui.theme.Shapes
import dev.montra.ui.theme.Space
import dev.montra.util.monogramColor

/**
 * Loads a remote icon through the app's image store. Falls back to a deterministic
 * monogram while loading and when the index has no icon for an app — several good
 * apps ship only vector art, and a catalogue must not look broken because of it.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    seed: String = contentDescription.orEmpty(),
    monogramText: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val store = (context.applicationContext as MontraApp).container.imageStore
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = if (url.isNullOrBlank()) null else store.load(url)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Monogram(seed = seed, text = monogramText ?: seed.take(1).uppercase())
        }
    }
}

/**
 * A flat tinted square with one letter. No gradient: the colour is derived from the
 * package name, so it is stable, and that is the whole idea.
 */
@Composable
fun Monogram(seed: String, text: String, modifier: Modifier = Modifier) {
    val base = Color(monogramColor(seed))
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = Shapes.MONOGRAM_PERCENT))
            .background(base),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
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

/** A section heading: more space above than below, so it belongs to what follows. */
@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xl, bottom = Space.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleSmall)
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    color: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
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
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(Shapes.block),
    ) {
        Column(modifier = Modifier.padding(Space.lg)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Box(Modifier.height(Space.xs))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
