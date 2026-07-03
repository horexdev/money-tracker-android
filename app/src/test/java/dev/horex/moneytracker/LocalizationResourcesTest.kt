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
    fun nonEnglishResourcesDoNotCopyDefaultUserFacingStrings() {
        val modules = stringResourceModules()
        val violations = modules.flatMap { module ->
            val defaultStrings = readStrings(module.defaultStringsFile)

            SUPPORTED_LOCALES
                .filterNot { it == DEFAULT_LOCALE }
                .flatMap { locale ->
                    val localizedStrings = readStrings(module.localizedStringsFile(locale))

                    defaultStrings
                        .filter { (name, defaultValue) ->
                            localizedStrings.getValue(name) == defaultValue &&
                                !isAllowedExactMatch(defaultValue)
                        }
                        .map { (name, value) -> "${module.relativePath}/values-$locale/$name=$value" }
                }
        }

        assertTrue(
            "Non-English resources copy default user-facing strings: ${violations.joinToString()}",
            violations.isEmpty(),
        )
    }

    @Test
    fun nonEnglishResourcesDoNotContainDefaultEnglishFragments() {
        val modules = stringResourceModules()
        val violations = modules.flatMap { module ->
            val defaultStrings = readStrings(module.defaultStringsFile)

            SUPPORTED_LOCALES
                .filterNot { it == DEFAULT_LOCALE }
                .flatMap { locale ->
                    val localizedStrings = readStrings(module.localizedStringsFile(locale))

                    defaultStrings.flatMap { (name, defaultValue) ->
                        defaultEnglishFragments(defaultValue)
                            .filter { fragment -> localizedStrings.getValue(name).contains(fragment) }
                            .map { fragment -> "${module.relativePath}/values-$locale/$name contains \"$fragment\"" }
                    }
                }
        }

        assertTrue(
            "Non-English resources contain default English fragments: ${violations.joinToString()}",
            violations.isEmpty(),
        )
    }

    @Test
    fun userFacingResourcesDoNotContainForbiddenOfflineWording() {
        val violations = stringResourceFiles().flatMap { stringsFile ->
            readStrings(stringsFile)
                .filterValues { value -> FORBIDDEN_USER_FACING_OFFLINE_WORDING.containsMatchIn(value) }
                .map { (name, value) ->
                    "${stringsFile.toRepoRelativePath()}/$name=$value"
                }
        }

        assertTrue(
            "User-facing resources contain forbidden offline wording: ${violations.joinToString()}",
            violations.isEmpty(),
        )
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

    private fun stringResourceFiles(): List<File> {
        return repoRoot()
            .walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" && it.parentFile?.name?.startsWith("values") == true }
            .sortedBy { it.path }
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

    private fun isAllowedExactMatch(defaultValue: String): Boolean {
        val value = defaultValue.trim()

        return value.isEmpty() ||
            value in EXACT_MATCH_ALLOWLIST ||
            PLACEHOLDER_ONLY.matches(value) ||
            CURRENCY_CODE.matches(value) ||
            value == DATE_FORMAT_TOKEN
    }

    private fun defaultEnglishFragments(defaultValue: String): List<String> {
        val normalized = defaultValue.normalizeWhitespace()
        if (isAllowedExactMatch(normalized)) {
            return emptyList()
        }

        val splitFragments = ENGLISH_FRAGMENT_SEPARATOR
            .split(normalized)
            .map { it.trimEnglishFragment() }

        return (splitFragments + normalized.trimEnglishFragment())
            .filter { it.isSuspiciousEnglishFragment() }
            .distinct()
    }

    private fun String.normalizeWhitespace(): String = trim().replace(WHITESPACE, " ")

    private fun String.trimEnglishFragment(): String =
        trim { char -> char.isWhitespace() || char == ',' || char == ':' || char == '(' || char == ')' || char == '[' || char == ']' }

    private fun String.isSuspiciousEnglishFragment(): Boolean {
        val value = trim()

        return value.isNotEmpty() &&
            !isAllowedExactMatch(value) &&
            ENGLISH_WORD.findAll(value).count() >= MIN_ENGLISH_FRAGMENT_WORDS
    }

    private fun File.toRepoRelativePath(): String =
        toRelativeString(repoRoot()).replace(File.separatorChar, '/')

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
        const val DATE_FORMAT_TOKEN = "YYYY-MM-DD"
        const val LONG_TEXT_SMOKE_LIMIT = 280
        const val MIN_ENGLISH_FRAGMENT_WORDS = 2
        val EXACT_MATCH_ALLOWLIST = setOf("Money Tracker")
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
        val PLACEHOLDER_ONLY = Regex("[0-9%\$sd.,: /()-]+")
        val CURRENCY_CODE = Regex("[A-Z]{3}")
        val WHITESPACE = Regex("\\s+")
        val ENGLISH_FRAGMENT_SEPARATOR = Regex("[.!?;]+")
        val ENGLISH_WORD = Regex("[A-Za-z][A-Za-z']+")
        val FORBIDDEN_USER_FACING_OFFLINE_WORDING = Regex("""\boffline\b|оф+лайн""", RegexOption.IGNORE_CASE)
        val RTL_TEXT = Regex("[\\u0600-\\u06FF]")
    }
}
