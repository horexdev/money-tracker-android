package dev.horex.moneytracker

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {
    @Test
    fun localeConfigMatchesMigrationLanguageSet() {
        assertEquals(SUPPORTED_LOCALES, readLocaleConfig())
    }

    @Test
    fun allLocalizedResourceFilesMirrorDefaultStringKeys() {
        val modules = stringResourceModules()

        assertTrue("Expected at least the app resource module.", modules.isNotEmpty())

        modules.forEach { module ->
            val defaultStrings = readStrings(module.defaultStringsFile)

            SUPPORTED_LOCALES
                .filterNot { it == DEFAULT_LOCALE }
                .forEach { locale ->
                    val localizedFile = module.localizedStringsFile(locale)

                    assertTrue(
                        "Missing ${module.relativePath}/values-$locale/strings.xml",
                        localizedFile.isFile,
                    )

                    val localizedStrings = readStrings(localizedFile)

                    assertEquals(
                        "${module.relativePath}/values-$locale keys must match default values keys",
                        defaultStrings.keys.toList(),
                        localizedStrings.keys.toList(),
                    )

                    defaultStrings.forEach { (name, defaultValue) ->
                        assertEquals(
                            "${module.relativePath}/values-$locale/$name placeholders must match default",
                            placeholderCounts(defaultValue),
                            placeholderCounts(localizedStrings.getValue(name)),
                        )
                    }
                }
        }
    }

    @Test
    fun arabicResourcesProvideRtlSmokeCoverage() {
        val modules = stringResourceModules()
        val arabicStrings = modules.flatMap { readStrings(it.localizedStringsFile("ar")).values }

        assertTrue("Arabic resources should contain RTL text.", arabicStrings.any { RTL_TEXT.containsMatchIn(it) })
        assertTrue("Arabic app name should stay readable.", arabicStrings.contains("Money Tracker"))
    }

    @Test
    fun longLocalizedUiTextStaysWithinSmokeLimit() {
        val modules = stringResourceModules()
        val violations = modules.flatMap { module ->
            SUPPORTED_LOCALES
                .filterNot { it == DEFAULT_LOCALE }
                .flatMap { locale ->
                    readStrings(module.localizedStringsFile(locale))
                        .filterKeys { it.endsWith("_title") || it.endsWith("_subtitle") || it.endsWith("_body") || it.endsWith("_desc") }
                        .filterValues { it.length > LONG_TEXT_SMOKE_LIMIT }
                        .map { (name, value) -> "${module.relativePath}/values-$locale/$name=${value.length}" }
                }
        }

        assertTrue(
            "Localized title/body/description strings are unexpectedly long: ${violations.joinToString()}",
            violations.isEmpty(),
        )
    }

    private fun readLocaleConfig(): List<String> {
        val document = parseXml(File(appResourceRoot(), "xml/locales_config.xml"))
        val locales = document.getElementsByTagName("locale")

        return (0 until locales.length).map { index ->
            val element = locales.item(index) as Element
            element.getAttributeNS(ANDROID_NAMESPACE, "name").ifBlank {
                element.getAttribute("android:name")
            }
        }
    }

    private fun stringResourceModules(): List<ResourceModule> {
        val root = repoRoot()

        return root
            .walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" && it.parentFile?.name == "values" }
            .map { stringsFile ->
                val resRoot = requireNotNull(stringsFile.parentFile?.parentFile) {
                    "Could not resolve res root for ${stringsFile.path}"
                }
                ResourceModule(
                    resRoot = resRoot,
                    relativePath = resRoot.toRelativeString(root).replace(File.separatorChar, '/'),
                )
            }
            .sortedBy { it.relativePath }
            .toList()
    }

    private fun readStrings(file: File): LinkedHashMap<String, String> {
        val document = parseXml(file)
        val strings = document.getElementsByTagName("string")
        val result = linkedMapOf<String, String>()

        (0 until strings.length).forEach { index ->
            val element = strings.item(index) as Element
            result[element.getAttribute("name")] = element.textContent
        }

        return result
    }

    private fun parseXml(file: File) =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)

    private fun appResourceRoot(): File = File(repoRoot(), "app/src/main/res")

    private fun repoRoot(): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
        var current = File(userDir).absoluteFile

        while (true) {
            if (File(current, "settings.gradle.kts").isFile) {
                return current
            }
            current = current.parentFile ?: break
        }

        error("Could not find repository root from $userDir")
    }

    private fun placeholderCounts(value: String): Map<String, Int> {
        return PLACEHOLDER.findAll(value)
            .groupingBy { it.value }
            .eachCount()
    }

    private data class ResourceModule(
        val resRoot: File,
        val relativePath: String,
    ) {
        val defaultStringsFile: File = File(resRoot, "values/strings.xml")

        fun localizedStringsFile(locale: String): File = File(resRoot, "values-$locale/strings.xml")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val DEFAULT_LOCALE = "en"
        const val LONG_TEXT_SMOKE_LIMIT = 280
        val SUPPORTED_LOCALES = listOf(
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
        )
        val PLACEHOLDER = Regex("%(\\d+\\$)?[sd]|%%")
        val RTL_TEXT = Regex("[\\u0600-\\u06FF]")
    }
}
