package dev.montra.util

import android.content.Context
import androidx.annotation.StringRes

/** Keep resource references in long-lived state; resolve in the current UI language. */
sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Literal(val value: String) : UiText
    data class Joined(val parts: List<UiText>, val separator: UiText) : UiText
}

/** Also usable by JVM tests with a resource resolver, without an Android context. */
fun UiText.resolve(resource: (Int, List<Any>) -> String): String = when (this) {
    is UiText.Resource -> resource(id, args.map { if (it is UiText) it.resolve(resource) else it })
    is UiText.Literal -> value
    is UiText.Joined -> parts.joinToString(separator.resolve(resource)) { it.resolve(resource) }
}

fun UiText.asString(context: Context): String = resolve { id, args ->
    context.getString(id, *args.toTypedArray())
}
