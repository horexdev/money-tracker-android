package dev.horex.moneytracker.core.balance

data class BalanceAccount(
    val id: Long,
    val profileId: Long,
    val name: String,
    val icon: String,
    val color: String,
    val type: String,
    val currencyCode: String,
    val isDefault: Boolean,
    val includeInTotal: Boolean,
    val balanceCents: Long,
)

data class BalanceCurrency(
    val currencyCode: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val netCents: Long,
)

data class DisplayConversion(
    val currencyCode: String,
    val netCents: Long,
)

data class BalanceSnapshot(
    val profileId: Long,
    val accountId: Long?,
    val baseCurrencyCode: String,
    val accountBalances: List<BalanceAccount>,
    val byCurrency: List<BalanceCurrency>,
    val displayConversions: List<DisplayConversion>,
    val totalInBaseCents: Long,
)

data class BalanceQuery(
    val accountId: Long? = null,
    val baseCurrencyCode: String? = null,
    val displayCurrencyCodes: List<String> = emptyList(),
    val displayConversionDate: String? = null,
    val includeExcludedAccounts: Boolean = false,
)
