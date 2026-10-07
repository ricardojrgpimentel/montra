package dev.montra.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.montra.data.model.Asset
import dev.montra.util.fingerprintsMatch
import dev.montra.util.hexToColon
import dev.montra.util.sha256Hex
import dev.montra.util.toHex
import java.io.File

/**
 * Verification of a downloaded APK, immediately before it is handed to the
 * system installer.
 *
 * Two independent checks, both fail-closed:
 *
 *  1. SHA-256 of the file must equal what the signed index says. This catches a
 *     truncated download, a corrupted CDN copy, or a swapped file on a host we
 *     do not control.
 *  2. The signing certificate of the APK must equal the fingerprint pinned in
 *     the index. This catches the subtle attack that hashing alone misses: a
 *     perfectly valid APK, byte-for-byte what the index promised, is impossible
 *     to fake — but a *different release* signed with a different key is a real
 *     risk when upstream rotates or an attacker gets publish rights on a repo.
 *
 * A caveat worth stating loudly: pinning the certificate means the app can only
 * ever update an install that was made with the same key. If a user already has
 * the app from F-Droid (signed with F-Droid's key) the update will be refused by
 * Android, and the app says so instead of failing obscurely.
 */
object ApkVerifier {

    sealed interface Result {
        data class Verified(val sha256: String, val certSha256: String?) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * Streaming hash: the largest app in the catalogue is over 300 MB and must
     * never be read into a byte array on a phone.
     */
    fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    fun verify(
        context: Context,
        file: File,
        expectedSha256: String,
        expectedCertFingerprint: String?,
    ): Result {
        if (!file.isFile || file.length() == 0L) return Result.Rejected("ficheiro vazio ou inexistente")

        val actualSha = sha256(file)
        if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
            return Result.Rejected(
                "O SHA-256 do ficheiro não corresponde ao índice.\n" +
                    "esperado: $expectedSha256\nobtido:   $actualSha",
            )
        }

        val certSha = signingCertificateSha256(context, file)
            ?: return Result.Rejected("não foi possível ler o certificado de assinatura do APK")

        if (expectedCertFingerprint != null && !fingerprintsMatch(expectedCertFingerprint, certSha)) {
            return Result.Rejected(
                "O APK está assinado por uma chave diferente da fixada no índice.\n" +
                    "índice:  ${expectedCertFingerprint.hexToColon()}\nno APK:  ${certSha.hexToColon()}",
            )
        }

        return Result.Verified(actualSha, certSha)
    }

    /** SHA-256 of the APK's signing certificate, as "aa:bb:...". */
    fun signingCertificateSha256(context: Context, apk: File): String? =
        signerBytes(context, apk, apk.absolutePath)?.let { sha256Hex(it).hexToColon() }

    /** Signing certificate of an already installed package. */
    fun installedSigningCertificateSha256(context: Context, packageName: String): String? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(packageName, flags)
                val signers = info.signingInfo?.apkContentsSigners ?: return null
                signers.firstOrNull()?.toByteArray()?.let { sha256Hex(it).hexToColon() }
            } else {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(packageName, flags)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()?.let { sha256Hex(it).hexToColon() }
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun signerBytes(context: Context, apk: File, path: String): ByteArray? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = context.packageManager.getPackageArchiveInfo(path, flags) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                ?: info.signingInfo?.signingCertificateHistory?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        }
    }

    /** One-line summary of what is pinned for this asset, for the UI. */
    fun describeTrust(asset: Asset): String =
        buildString {
            append("sha256 ")
            append(asset.sha256.take(16))
            append("…")
            if (asset.signingCertSha256 != null) {
                append(" · cert ")
                append(asset.signingCertSha256.take(17))
                append("…")
            } else {
                append(" · cert não fixado")
            }
        }
}
