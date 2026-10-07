package dev.montra.util

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
 * Colour for apps that ship no icon.
 *
 * Deterministic from the package name, but picked from a curated set rather than
 * the whole colour wheel: a hash-derived hue produces the occasional purple avatar
 * in an otherwise green interface, which reads as accidental. These eight are dark
 * enough for white text in both themes.
 */
private val MONOGRAM_COLORS = intArrayOf(
    0xFF2E6B4F.toInt(), // verde Montra
    0xFF3B6470.toInt(), // azul-petróleo
    0xFF4E6355.toInt(), // verde-acinzentado
    0xFF5A6B3B.toInt(), // oliva
    0xFF6B4F3B.toInt(), // castanho
    0xFF3F5566.toInt(), // azul-ardósia
    0xFF6B3B4F.toInt(), // vinho
    0xFF4A4A6B.toInt(), // índigo acinzentado
)

fun monogramColor(seed: String): Int = MONOGRAM_COLORS[abs(seed.hashCode()) % MONOGRAM_COLORS.size]
