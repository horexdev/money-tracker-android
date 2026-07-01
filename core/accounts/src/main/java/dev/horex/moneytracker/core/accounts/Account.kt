package dev.horex.moneytracker.core.accounts

data class Account(
    val id: Long,
    val profileId: Long,
    val name: String,
    val icon: String,
    val color: String,
    val type: AccountType,
    val currencyCode: String,
    val isDefault: Boolean,
    val includeInTotal: Boolean,
    val balanceCents: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

enum class AccountType(val storageValue: String) {
    Checking("checking"),
    Savings("savings"),
    Cash("cash"),
    Credit("credit"),
    Crypto("crypto"),
    ;

    companion object {
        fun fromStorageValue(value: String): AccountType {
            return entries.firstOrNull { it.storageValue == value } ?: Checking
        }
    }
}

data class CreateAccountInput(
    val name: String,
    val icon: String = DEFAULT_ACCOUNT_ICON,
    val color: String = DEFAULT_ACCOUNT_COLOR,
    val type: AccountType = AccountType.Checking,
    val currencyCode: String,
    val includeInTotal: Boolean = true,
)

data class UpdateAccountInput(
    val name: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val type: AccountType? = null,
    val currencyCode: String? = null,
    val includeInTotal: Boolean? = null,
)

const val DEFAULT_ACCOUNT_ICON = "wallet"
const val DEFAULT_ACCOUNT_COLOR = "#6366f1"
