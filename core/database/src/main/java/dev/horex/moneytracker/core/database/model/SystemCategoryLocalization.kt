package dev.horex.moneytracker.core.database.model

import java.util.Locale

object SystemCategoryLocalization {
    const val FOOD = "system.food"
    const val TRANSPORT = "system.transport"
    const val HOUSING = "system.housing"
    const val HEALTH = "system.health"
    const val ENTERTAINMENT = "system.entertainment"
    const val SHOPPING = "system.shopping"
    const val SALARY = "system.salary"
    const val OTHER = "system.other"
    const val SAVINGS = "system.savings"
    const val TRANSFER = "system.transfer"
    const val ADJUSTMENT = "system.adjustment"

    val supportedLanguageCodes = setOf(
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

    val definitions = listOf(
        SystemCategoryDefinition(
            localizationKey = FOOD,
            names = mapOf(
                "en" to "Food",
                "ru" to "\u0415\u0434\u0430",
                "uk" to "\u0407\u0436\u0430",
                "be" to "\u0415\u0436\u0430",
                "kk" to "\u0422\u0430\u043c\u0430\u049b",
                "uz" to "Ovqat",
                "es" to "Comida",
                "de" to "Essen",
                "it" to "Cibo",
                "fr" to "Nourriture",
                "pt" to "Comida",
                "nl" to "Eten",
                "ar" to "\u0637\u0639\u0627\u0645",
                "tr" to "Yemek",
                "ko" to "\uc74c\uc2dd",
                "ms" to "Makanan",
                "id" to "Makanan",
            ),
            icon = "fork-knife",
            type = "expense",
            color = "#f97316",
            isProtected = false,
        ),
        SystemCategoryDefinition(
            localizationKey = TRANSPORT,
            names = mapOf(
                "en" to "Transport",
                "ru" to "\u0422\u0440\u0430\u043d\u0441\u043f\u043e\u0440\u0442",
                "uk" to "\u0422\u0440\u0430\u043d\u0441\u043f\u043e\u0440\u0442",
                "be" to "\u0422\u0440\u0430\u043d\u0441\u043f\u0430\u0440\u0442",
                "kk" to "\u041a\u04e9\u043b\u0456\u043a",
                "uz" to "Transport",
                "es" to "Transporte",
                "de" to "Transport",
                "it" to "Trasporto",
                "fr" to "Transport",
                "pt" to "Transporte",
                "nl" to "Vervoer",
                "ar" to "\u0645\u0648\u0627\u0635\u0644\u0627\u062a",
                "tr" to "Ula\u015f\u0131m",
                "ko" to "\uad50\ud1b5",
                "ms" to "Pengangkutan",
                "id" to "Transportasi",
            ),
            icon = "bus",
            type = "expense",
            color = "#8b5cf6",
            isProtected = false,
        ),
        SystemCategoryDefinition(
            localizationKey = HOUSING,
            names = mapOf(
                "en" to "Housing",
                "ru" to "\u0416\u0438\u043b\u044c\u0451",
                "uk" to "\u0416\u0438\u0442\u043b\u043e",
                "be" to "\u0416\u044b\u043b\u043b\u0451",
                "kk" to "\u0422\u04b1\u0440\u0493\u044b\u043d \u04af\u0439",
                "uz" to "Uy-joy",
                "es" to "Vivienda",
                "de" to "Wohnen",
                "it" to "Casa",
                "fr" to "Logement",
                "pt" to "Moradia",
                "nl" to "Wonen",
                "ar" to "\u0633\u0643\u0646",
                "tr" to "Konut",
                "ko" to "\uc8fc\uac70",
                "ms" to "Perumahan",
                "id" to "Perumahan",
            ),
            icon = "house",
            type = "expense",
            color = "#f97316",
            isProtected = false,
        ),
        SystemCategoryDefinition(
            localizationKey = HEALTH,
            names = mapOf(
                "en" to "Health",
                "ru" to "\u0417\u0434\u043e\u0440\u043e\u0432\u044c\u0435",
                "uk" to "\u0417\u0434\u043e\u0440\u043e\u0432'\u044f",
                "be" to "\u0417\u0434\u0430\u0440\u043e\u045e\u0435",
                "kk" to "\u0414\u0435\u043d\u0441\u0430\u0443\u043b\u044b\u049b",
                "uz" to "Salomatlik",
                "es" to "Salud",
                "de" to "Gesundheit",
                "it" to "Salute",
                "fr" to "Sant\u00e9",
                "pt" to "Sa\u00fade",
                "nl" to "Gezondheid",
                "ar" to "\u0635\u062d\u0629",
                "tr" to "Sa\u011fl\u0131k",
                "ko" to "\uac74\uac15",
                "ms" to "Kesihatan",
                "id" to "Kesehatan",
            ),
            icon = "first-aid",
            type = "expense",
            color = "#22c55e",
            isProtected = false,
        ),
        SystemCategoryDefinition(
            localizationKey = ENTERTAINMENT,
            names = mapOf(
                "en" to "Entertainment",
                "ru" to "\u0420\u0430\u0437\u0432\u043b\u0435\u0447\u0435\u043d\u0438\u044f",
                "uk" to "\u0420\u043e\u0437\u0432\u0430\u0433\u0438",
                "be" to "\u0417\u0430\u0431\u0430\u0432\u044b",
                "kk" to "\u041e\u0439\u044b\u043d-\u0441\u0430\u0443\u044b\u049b",
                "uz" to "Ko'ngil ochar",
                "es" to "Entretenimiento",
                "de" to "Unterhaltung",
                "it" to "Intrattenimento",
                "fr" to "Divertissement",
                "pt" to "Entretenimento",
                "nl" to "Entertainment",
                "ar" to "\u062a\u0631\u0641\u064a\u0647",
                "tr" to "E\u011flence",
                "ko" to "\uc5d4\ud130\ud14c\uc778\uba3c\ud2b8",
                "ms" to "Hiburan",
                "id" to "Hiburan",
            ),
            icon = "film-slate",
            type = "both",
            color = "#ec4899",
            isProtected = false,
        ),
        SystemCategoryDefinition(
            localizationKey = SHOPPING,
            names = mapOf(
                "en" to "Shopping",
                "ru" to "\u041f\u043e\u043a\u0443\u043f\u043a\u0438",
                "uk" to "\u041f\u043e\u043a\u0443\u043f\u043a\u0438",
                "be" to "\u041f\u0430\u043a\u0443\u043f\u043a\u0456",
                "kk" to "\u0421\u0430\u0442\u044b\u043f \u0430\u043b\u0443",
                "uz" to "Xarid",
                "es" to "Compras",
                "de" to "Einkaufen",
                "it" to "Shopping",
                "fr" to "Shopping",
                "pt" to "Compras",
                "nl" to "Winkelen",
                "ar" to "\u062a\u0633\u0648\u0642",
                "tr" to "Al\u0131\u015fveri\u015f",
                "ko" to "\uc1fc\ud551",
                "ms" to "Membeli-belah",
                "id" to "Belanja",
            ),
            icon = "shopping-bag",
            type = "expense",
            color = "#ef4444",
            isProtected = false,
        ),
        SystemCategoryDefinition(
            localizationKey = SALARY,
            names = mapOf(
                "en" to "Salary",
                "ru" to "\u0417\u0430\u0440\u043f\u043b\u0430\u0442\u0430",
                "uk" to "\u0417\u0430\u0440\u043f\u043b\u0430\u0442\u0430",
                "be" to "\u0417\u0430\u0440\u043f\u043b\u0430\u0442\u0430",
                "kk" to "\u0416\u0430\u043b\u0430\u049b\u044b",
                "uz" to "Maosh",
                "es" to "Salario",
                "de" to "Gehalt",
                "it" to "Stipendio",
                "fr" to "Salaire",
                "pt" to "Sal\u00e1rio",
                "nl" to "Salaris",
                "ar" to "\u0631\u0627\u062a\u0628",
                "tr" to "Maa\u015f",
                "ko" to "\uae09\uc5ec",
                "ms" to "Gaji",
                "id" to "Gaji",
            ),
            icon = "briefcase",
            type = "income",
            color = "#10b981",
            isProtected = false,
        ),
        SystemCategoryDefinition(
            localizationKey = OTHER,
            names = mapOf(
                "en" to "Other",
                "ru" to "\u0414\u0440\u0443\u0433\u043e\u0435",
                "uk" to "\u0406\u043d\u0448\u0435",
                "be" to "\u0406\u043d\u0448\u0430\u0435",
                "kk" to "\u0411\u0430\u0441\u049b\u0430",
                "uz" to "Boshqa",
                "es" to "Otros",
                "de" to "Sonstiges",
                "it" to "Altro",
                "fr" to "Autre",
                "pt" to "Outros",
                "nl" to "Overig",
                "ar" to "\u0623\u062e\u0631\u0649",
                "tr" to "Di\u011fer",
                "ko" to "\uae30\ud0c0",
                "ms" to "Lain-lain",
                "id" to "Lainnya",
            ),
            icon = "tag",
            type = "both",
            color = "#64748b",
            isProtected = false,
        ),
        SystemCategoryDefinition(
            localizationKey = SAVINGS,
            names = mapOf(
                "en" to "Savings",
                "ru" to "\u041d\u0430\u043a\u043e\u043f\u043b\u0435\u043d\u0438\u044f",
                "uk" to "\u0417\u0430\u043e\u0449\u0430\u0434\u0436\u0435\u043d\u043d\u044f",
                "be" to "\u0417\u0431\u0435\u0440\u0430\u0436\u044d\u043d\u043d\u0456",
                "kk" to "\u0416\u0438\u043d\u0430\u049b\u0442\u0430\u0440",
                "uz" to "Jamg'arma",
                "es" to "Ahorros",
                "de" to "Ersparnisse",
                "it" to "Risparmi",
                "fr" to "\u00c9pargne",
                "pt" to "Poupan\u00e7a",
                "nl" to "Spaargeld",
                "ar" to "\u0645\u062f\u062e\u0631\u0627\u062a",
                "tr" to "Tasarruf",
                "ko" to "\uc800\ucd95",
                "ms" to "Simpanan",
                "id" to "Tabungan",
            ),
            icon = "piggy-bank",
            type = "savings",
            color = "#3b82f6",
            isProtected = false,
        ),
        SystemCategoryDefinition(
            localizationKey = TRANSFER,
            names = mapOf(
                "en" to "Transfer",
                "ru" to "\u041f\u0435\u0440\u0435\u0432\u043e\u0434",
                "uk" to "\u041f\u0435\u0440\u0435\u043a\u0430\u0437",
                "be" to "\u041f\u0435\u0440\u0430\u0432\u043e\u0434",
                "kk" to "\u0410\u0443\u0434\u0430\u0440\u044b\u043c",
                "uz" to "O'tkazma",
                "es" to "Transferencia",
                "de" to "\u00dcberweisung",
                "it" to "Trasferimento",
                "fr" to "Virement",
                "pt" to "Transfer\u00eancia",
                "nl" to "Overboeking",
                "ar" to "\u062a\u062d\u0648\u064a\u0644",
                "tr" to "Transfer",
                "ko" to "\uc774\uccb4",
                "ms" to "Pindahan",
                "id" to "Transfer",
            ),
            icon = "arrows-left-right",
            type = "transfer",
            color = "#6366f1",
            isProtected = true,
        ),
        SystemCategoryDefinition(
            localizationKey = ADJUSTMENT,
            names = mapOf(
                "en" to "Adjustment",
                "ru" to "\u041a\u043e\u0440\u0440\u0435\u043a\u0442\u0438\u0440\u043e\u0432\u043a\u0430",
                "uk" to "\u041a\u043e\u0440\u0438\u0433\u0443\u0432\u0430\u043d\u043d\u044f",
                "be" to "\u041a\u0430\u0440\u044d\u043a\u0446\u0456\u0440\u043e\u045e\u043a\u0430",
                "kk" to "\u0422\u04af\u0437\u0435\u0442\u0443",
                "uz" to "Tuzatish",
                "es" to "Ajuste",
                "de" to "Korrektur",
                "it" to "Rettifica",
                "fr" to "Ajustement",
                "pt" to "Ajuste",
                "nl" to "Correctie",
                "ar" to "\u062a\u0633\u0648\u064a\u0629",
                "tr" to "D\u00fczeltme",
                "ko" to "\uc870\uc815",
                "ms" to "Pelarasan",
                "id" to "Penyesuaian",
            ),
            icon = "scales",
            type = "adjustment",
            color = "#94a3b8",
            isProtected = true,
        ),
    )

    private val definitionsByKey = definitions.associateBy { it.localizationKey }
    private val lookupKeys = definitions
        .flatMap { definition ->
            definition.names.values.map { name ->
                SystemCategoryLookupKey(
                    name = name.normalizedLookupValue(),
                    type = definition.type,
                    icon = definition.icon,
                    color = definition.color.normalizedLookupValue(),
                    isProtected = definition.isProtected,
                ) to definition.localizationKey
            }
        }
        .toMap()

    fun displayName(
        localizationKey: String?,
        languageCode: String,
        fallbackName: String,
    ): String {
        return localizationKey
            ?.let { definitionsByKey[it] }
            ?.nameForLanguage(languageCode)
            ?: fallbackName
    }

    fun inferLocalizationKey(
        name: String,
        type: String,
        icon: String,
        color: String,
        isProtected: Boolean,
    ): String? {
        return lookupKeys[
            SystemCategoryLookupKey(
                name = name.normalizedLookupValue(),
                type = type.normalizedLookupValue(),
                icon = icon.normalizedLookupValue(),
                color = color.normalizedLookupValue(),
                isProtected = isProtected,
            ),
        ]
    }

    fun requireDefinition(localizationKey: String): SystemCategoryDefinition {
        return requireNotNull(definitionsByKey[localizationKey]) {
            "Unknown system category localization key: $localizationKey"
        }
    }

    fun normalizedLanguageCode(languageCode: String): String {
        val normalized = languageCode
            .lowercase(Locale.US)
            .substringBefore('-')
            .substringBefore('_')
        return normalized.takeIf { it in supportedLanguageCodes } ?: "en"
    }
}

data class SystemCategoryDefinition(
    val localizationKey: String,
    val names: Map<String, String>,
    val icon: String,
    val type: String,
    val color: String,
    val isProtected: Boolean,
) {
    fun nameForLanguage(languageCode: String): String {
        val normalizedLanguageCode = SystemCategoryLocalization.normalizedLanguageCode(languageCode)
        return names[normalizedLanguageCode] ?: names.getValue("en")
    }
}

private data class SystemCategoryLookupKey(
    val name: String,
    val type: String,
    val icon: String,
    val color: String,
    val isProtected: Boolean,
)

private fun String.normalizedLookupValue(): String {
    return trim().lowercase(Locale.US)
}
