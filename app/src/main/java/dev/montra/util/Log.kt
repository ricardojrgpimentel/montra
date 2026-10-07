package dev.montra.util

import android.util.Log
import dev.montra.BuildConfig

/**
 * One tag for the whole app, so `adb logcat -s Montra` tells the full story of a
 * catalogue refresh: which URL, which source won (network, cache or bundled
 * snapshot), whether the signature verified, and why it did not.
 *
 * A store whose catalogue silently fails to update is a store nobody can debug,
 * so failures are logged even in release builds; verbose detail is debug-only.
 */
object Log {
    private const val TAG = "Montra"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun i(message: String) = Log.i(TAG, message).let { }

    fun w(message: String, error: Throwable? = null) {
        if (error != null) Log.w(TAG, message, error) else Log.w(TAG, message)
    }

    fun e(message: String, error: Throwable? = null) {
        if (error != null) Log.e(TAG, message, error) else Log.e(TAG, message)
    }
}
