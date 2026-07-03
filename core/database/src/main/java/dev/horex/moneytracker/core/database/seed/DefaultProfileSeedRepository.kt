package dev.horex.moneytracker.core.database.seed

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.SystemCategoryLocalization
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

    val infrastructureCategorySeeds = listOf(
        DefaultCategorySeed(
            localizationKey = SystemCategoryLocalization.TRANSFER,
            name = "Transfer",
            icon = "arrows-left-right",
            type = "transfer",
            color = "#6366f1",
            isProtected = true,
        ),
        DefaultCategorySeed(
            localizationKey = SystemCategoryLocalization.ADJUSTMENT,
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
        return SystemCategoryLocalization.definitions.filterNot { it.isProtected }.map { definition ->
            DefaultCategorySeed(
                localizationKey = definition.localizationKey,
                name = definition.nameForLanguage(languageCode),
                icon = definition.icon,
                type = definition.type,
                color = definition.color,
                isProtected = false,
            )
        }
    }
}

internal data class DefaultCategorySeed(
    val localizationKey: String?,
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
            localizationKey = localizationKey,
            icon = icon,
            type = type,
            color = color,
            isProtected = isProtected,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }
}

private val CurrencyCodePattern = Regex("[A-Z]{3}")

private fun String?.normalizedCurrencyCode(): String? {
    return this
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { CurrencyCodePattern.matches(it) }
}
