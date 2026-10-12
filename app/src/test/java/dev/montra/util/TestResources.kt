package dev.montra.util

import dev.montra.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

internal fun resourceStrings(language: String): Map<String, String> {
    val suffix = if (language == "en") "" else "-$language"
    val file = File("src/main/res/values$suffix/strings.xml")
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    val nodes = document.getElementsByTagName("string")
    return (0 until nodes.length).associate { index ->
        val node = nodes.item(index)
        node.attributes.getNamedItem("name").nodeValue to node.textContent
            .removeSurrounding("\"").replace("\\'", "'").replace("\\\"", "\"")
    }
}

/**
 * Os plurais por nome, com as quantidades que cada um declara.
 *
 * Ficam de fora de [resourceStrings] porque as quantidades variam por língua — o
 * francês tem `many`, o português não. O que tem de ser igual nas quatro é o *nome*
 * do plural, e é isso que o teste compara.
 */
internal fun resourcePlurals(language: String): Map<String, Set<String>> {
    val suffix = if (language == "en") "" else "-$language"
    val file = File("src/main/res/values$suffix/strings.xml")
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    val nodes = document.getElementsByTagName("plurals")
    return (0 until nodes.length).associate { index ->
        val node = nodes.item(index)
        val items = node.childNodes
        val quantities = (0 until items.length)
            .map { items.item(it) }
            .filter { it.nodeName == "item" }
            .map { it.attributes.getNamedItem("quantity").nodeValue }
            .toSet()
        node.attributes.getNamedItem("name").nodeValue to quantities
    }
}

internal fun UiText.testText(language: String): String {
    val values = resourceStrings(language)
    val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    return resolve { id, args ->
        String.format(Locale.forLanguageTag(language), values.getValue(names.getValue(id)), *args.toTypedArray())
    }
}

internal fun UiText.ptText(): String = testText("pt")
