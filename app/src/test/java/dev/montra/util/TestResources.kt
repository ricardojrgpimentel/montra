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

internal fun UiText.testText(language: String): String {
    val values = resourceStrings(language)
    val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
    return resolve { id, args ->
        String.format(Locale.forLanguageTag(language), values.getValue(names.getValue(id)), *args.toTypedArray())
    }
}

internal fun UiText.ptText(): String = testText("pt")
