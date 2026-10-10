package dev.montra

import android.content.res.Configuration
import android.os.LocaleList
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.montra.data.model.IndexFile
import dev.montra.data.model.IndexJson
import dev.montra.data.AppLanguage
import dev.montra.util.UiText
import dev.montra.util.asString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun resourcesFollowLocalesAndFallback() {
        for ((tag, expected) in mapOf("en" to "Settings", "pt-PT" to "Definições", "pt-BR" to "Definições",
            "es" to "Ajustes", "fr" to "Paramètres", "ja" to "Settings")) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            }
            val localized = context.createConfigurationContext(configuration)
            assertEquals(tag, expected, localized.getString(R.string.text_settings))
            assertTrue(localized.getString(R.string.notification_downloading, "Example").contains("Example"))
        }
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("ja,es"))
        }
        assertEquals("Ajustes", context.createConfigurationContext(configuration).getString(R.string.text_settings))
        val french = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags("fr"))
        })
        assertEquals("1 mise à jour", french.resources.getQuantityString(R.plurals.update_count, 1, 1))
        assertEquals("2 mises à jour", french.resources.getQuantityString(R.plurals.update_count, 2, 2))
    }

    private fun awaitSettingsLanguage(scenario: ActivityScenario<MainActivity>, expected: String) {
        // LocaleManager delivers configuration changes asynchronously, after its IPC returns.
        val deadline = SystemClock.uptimeMillis() + 5_000
        var actual = ""
        do {
            instrumentation.waitForIdleSync()
            scenario.onActivity { actual = it.getString(R.string.text_settings) }
            if (actual == expected) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals(expected, actual)
    }

    @Test fun languageChangesSurviveRecreationAndApplyOutsideActivity() {
        val previous = AppCompatDelegate.getApplicationLocales()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                for ((language, expected) in listOf(AppLanguage.SPANISH to "Ajustes", AppLanguage.FRENCH to "Paramètres",
                    AppLanguage.PORTUGUESE to "Definições", AppLanguage.ENGLISH to "Settings")) {
                    scenario.onActivity { language.apply() }
                    awaitSettingsLanguage(scenario, expected)
                    scenario.onActivity { activity ->
                        val catalogue = IndexJson.decodeFromString(IndexFile.serializer(), activity.assets.open("index.json").bufferedReader().use { it.readText() })
                        val app = catalogue.apps.first { it.id == "newpipe" }
                        val tag = activity.resources.configuration.locales[0].toLanguageTag()
                        assertEquals(if (language == AppLanguage.PORTUGUESE) app.summaryTranslations["pt"] else app.summary, app.summaryFor(tag))
                        assertEquals(app.description[if (language == AppLanguage.PORTUGUESE) "pt" else "en"], app.descriptionFor(tag))
                        assertEquals(expected, activity.getString(R.string.text_settings))
                        assertEquals(language, AppLanguage.current())
                    }
                    scenario.recreate()
                    scenario.onActivity { activity -> assertEquals(expected, activity.getString(R.string.text_settings)) }
                    assertEquals(expected, UiText.Resource(R.string.text_settings).asString(ContextCompat.getContextForLanguage(context)))
                }
                scenario.onActivity { AppLanguage.SYSTEM.apply() }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty) }
            } finally {
                instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(previous) }
            }
        }
    }
}
