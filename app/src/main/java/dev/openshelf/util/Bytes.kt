package dev.openshelf.util

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

private val HEX = "0123456789abcdef".toCharArray()

fun ByteArray.toHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        out[i * 2] = HEX[v ushr 4]
        out[i * 2 + 1] = HEX[v and 0x0F]
    }
    return String(out)
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

/** "aabbcc" -> "aa:bb:cc". Certificate fingerprints are compared in this shape. */
fun String.hexToColon(): String =
    lowercase(Locale.ROOT).chunked(2).joinToString(":")

/** Accepts either "aa:bb" or "aabb" and returns a comparable canonical form. */
fun normalizeFingerprint(value: String?): String? =
    value?.replace(":", "")?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }

fun fingerprintsMatch(a: String?, b: String?): Boolean {
    val na = normalizeFingerprint(a) ?: return false
    val nb = normalizeFingerprint(b) ?: return false
    return na == nb
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value >= 10 || unit == 0) "${value.toInt()} ${units[unit]}"
    else String.format(Locale.ROOT, "%.1f %s", value, units[unit])
}

/**
 * Deterministic colour for apps that ship no icon, so a monogram still looks
 * like a real avatar and stays stable between launches.
 */
fun monogramColor(seed: String): Int {
    val hue = abs(seed.hashCode()) % 360
    return android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.72f))
}
