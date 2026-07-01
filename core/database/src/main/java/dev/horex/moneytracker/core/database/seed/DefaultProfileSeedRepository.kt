package dev.horex.moneytracker.core.database.seed

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.normalizeLocalProfileLanguageCode
import java.util.Locale

fun interface LocalProfileSeeder {
    suspend fun ensureSeed(profile: LocalProfile)
}

class DefaultProfileSeedRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : LocalProfileSeeder {
    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()

    override suspend fun ensureSeed(profile: LocalProfile) {
        ensureSeed(profile = profile, explicitCurrencyCode = null)
    }

    suspend fun ensureSeed(
        profile: LocalProfile,
        explicitCurrencyCode: String?,
    ) {
        val languageCode = normalizeLocalProfileLanguageCode(profile.languageCode)
        val currencyCode = explicitCurrencyCode.normalizedCurrencyCode()
            ?: DefaultProfileSeed.accountCurrencyForLanguage(languageCode)

        database.withTransaction {
            val now = clock()
            if (accountDao.countByProfile(profile.id) == 0) {
                accountDao.insert(
                    AccountEntity(
                        profileId = profile.id,
                        name = DefaultProfileSeed.accountNameForLanguage(languageCode),
                        icon = "wallet",
                        color = "#6366f1",
                        type = "checking",
                        currencyCode = currencyCode,
                        isDefault = true,
                        includeInTotal = true,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                )
            }

            if (categoryDao.countEditableByProfile(profile.id) == 0) {
                DefaultProfileSeed.categorySeedsForLanguage(languageCode).forEach { seed ->
                    categoryDao.insertIgnore(seed.toEntity(profile.id, now))
                }
            }

            DefaultProfileSeed.infrastructureCategorySeeds.forEach { seed ->
                categoryDao.insertIgnore(seed.toEntity(profile.id, now))
            }
        }
    }
}

internal object DefaultProfileSeed {
    private val accountNames = mapOf(
        "en" to "Main account",
        "ru" to "\u041e\u0441\u043d\u043e\u0432\u043d\u043e\u0439 \u0441\u0447\u0451\u0442",
        "uk" to "\u041e\u0441\u043d\u043e\u0432\u043d\u0438\u0439 \u0440\u0430\u0445\u0443\u043d\u043e\u043a",
        "be" to "\u0410\u0441\u043d\u043e\u045e\u043d\u044b \u0440\u0430\u0445\u0443\u043d\u0430\u043a",
        "kk" to "\u041d\u0435\u0433\u0456\u0437\u0433\u0456 \u0448\u043e\u0442",
        "uz" to "Asosiy hisob",
        "es" to "Cuenta principal",
        "de" to "Hauptkonto",
        "it" to "Conto principale",
        "fr" to "Compte principal",
        "pt" to "Conta principal",
        "nl" to "Hoofdrekening",
        "ar" to "\u0627\u0644\u062d\u0633\u0627\u0628 \u0627\u0644\u0631\u0626\u064a\u0633\u064a",
        "tr" to "Ana hesap",
        "ko" to "\uc8fc \uacc4\uc88c",
        "ms" to "Akaun utama",
        "id" to "Rekening utama",
    )

    private val accountCurrencies = mapOf(
        "en" to "USD",
        "ru" to "RUB",
        "uk" to "UAH",
        "be" to "BYN",
        "kk" to "KZT",
        "uz" to "UZS",
        "es" to "EUR",
        "de" to "EUR",
        "it" to "EUR",
        "fr" to "EUR",
        "pt" to "BRL",
        "nl" to "EUR",
        "ar" to "SAR",
        "tr" to "TRY",
        "ko" to "KRW",
        "ms" to "MYR",
        "id" to "IDR",
    )

    private val categoryTemplates = listOf(
        DefaultCategoryTemplate(
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
        ),
        DefaultCategoryTemplate(
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
        ),
        DefaultCategoryTemplate(
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
        ),
        DefaultCategoryTemplate(
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
        ),
        DefaultCategoryTemplate(
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
        ),
        DefaultCategoryTemplate(
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
        ),
        DefaultCategoryTemplate(
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
        ),
        DefaultCategoryTemplate(
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
        ),
        DefaultCategoryTemplate(
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
        ),
    )

    val infrastructureCategorySeeds = listOf(
        DefaultCategorySeed(
            name = "Transfer",
            icon = "arrows-left-right",
            type = "transfer",
            color = "#6366f1",
            isProtected = true,
        ),
        DefaultCategorySeed(
            name = "Adjustment",
            icon = "scales",
            type = "adjustment",
            color = "#94a3b8",
            isProtected = true,
        ),
    )

    fun accountNameForLanguage(languageCode: String): String {
        return accountNames[languageCode] ?: accountNames.getValue("en")
    }

    fun accountCurrencyForLanguage(languageCode: String): String {
        return accountCurrencies[languageCode] ?: "USD"
    }

    fun categorySeedsForLanguage(languageCode: String): List<DefaultCategorySeed> {
        return categoryTemplates.map { template ->
            DefaultCategorySeed(
                name = template.names[languageCode] ?: template.names.getValue("en"),
                icon = template.icon,
                type = template.type,
                color = template.color,
                isProtected = false,
            )
        }
    }
}

internal data class DefaultCategorySeed(
    val name: String,
    val icon: String,
    val type: String,
    val color: String,
    val isProtected: Boolean,
) {
    fun toEntity(profileId: Long, updatedAtEpochMillis: Long): CategoryEntity {
        return CategoryEntity(
            profileId = profileId,
            name = name,
            icon = icon,
            type = type,
            color = color,
            isProtected = isProtected,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }
}

private data class DefaultCategoryTemplate(
    val names: Map<String, String>,
    val icon: String,
    val type: String,
    val color: String,
)

private val CurrencyCodePattern = Regex("[A-Z]{3}")

private fun String?.normalizedCurrencyCode(): String? {
    return this
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { CurrencyCodePattern.matches(it) }
}
