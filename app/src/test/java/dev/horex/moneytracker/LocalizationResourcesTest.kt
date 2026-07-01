package dev.horex.moneytracker

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {
    @Test
    fun localeConfigMatchesMigrationLanguageSet() {
        assertEquals(
            listOf(
                "en",
                "ru",
                "uk",
                "be",
                "kk",
                "uz",
                "es",
                "de",
                "it",
                "fr",
                "pt",
                "nl",
                "ar",
                "tr",
                "ko",
                "ms",
                "id",
            ),
            readLocaleConfig(),
        )
    }

    @Test
    fun russianAppStringsMirrorDefaultAppStringKeys() {
        assertEquals(
            readStringNames("values/strings.xml"),
            readStringNames("values-ru/strings.xml"),
        )
    }

    private fun readLocaleConfig(): List<String> {
        val document = parseXml("xml/locales_config.xml")
        val locales = document.getElementsByTagName("locale")

        return (0 until locales.length).map { index ->
            val element = locales.item(index) as Element
            element.getAttributeNS(ANDROID_NAMESPACE, "name").ifBlank {
                element.getAttribute("android:name")
            }
        }
    }

    private fun readStringNames(relativePath: String): List<String> {
        val document = parseXml(relativePath)
        val strings = document.getElementsByTagName("string")

        return (0 until strings.length).map { index ->
            val element = strings.item(index) as Element
            element.getAttribute("name")
        }
    }

    private fun parseXml(relativePath: String) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(resourceRoot(), relativePath))

    private fun resourceRoot(): File {
        return listOf(
            File("src/main/res"),
            File("app/src/main/res"),
        ).first { it.isDirectory }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
