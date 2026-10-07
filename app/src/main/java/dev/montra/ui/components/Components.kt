package dev.montra.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.montra.MontraApp
import dev.montra.util.monogramColor

/**
 * Loads a remote icon/screenshot through the app's [dev.montra.ui.ImageStore].
 * Shows a deterministic monogram while loading and when the index has no icon for
 * an app at all — several good apps ship only vector art, and a store must not
 * look broken because of that.
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

@Composable
fun Monogram(seed: String, text: String, modifier: Modifier = Modifier) {
    val base = Color(monogramColor(seed))
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 28))
            .background(
                Brush.linearGradient(
                    listOf(base, base.copy(alpha = 0.72f), base.copy(alpha = 0.92f)),
                ),
            ),
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
fun ScreenshotImage(url: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 180.dp, height = 320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        RemoteImage(
            url = url,
            contentDescription = null,
            seed = url,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun KeyValue(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        Text(text = text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** A small square thumbnail used in the list. */
@Composable
fun AppIcon(url: String?, seed: String, text: String, size: Int = 48) {
    RemoteImage(
        url = url,
        contentDescription = null,
        seed = seed,
        monogramText = text,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(percent = 26)),
    )
}

/** Horizontally scrollable screenshots; fixed height so the page does not jump. */
@Composable
fun ScreenshotRow(urls: List<String>, modifier: Modifier = Modifier) {
    if (urls.isEmpty()) return
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(urls.size) { index -> ScreenshotImage(urls[index]) }
    }
}
