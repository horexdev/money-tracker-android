package dev.horex.moneytracker.core.accounts

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.model.AccountEntity
import java.util.Locale

class RoomAccountsRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AccountsRepository {
    private val accountDao = database.accountDao()

    override suspend fun listAccounts(profileId: Long): List<Account> {
        return accountDao.listByProfile(profileId).map { entity ->
            entity.toAccount(balanceCents = accountDao.getBalanceCents(profileId, entity.id))
        }
    }

    override suspend fun getAccount(profileId: Long, accountId: Long): Account {
        return requireAccount(profileId, accountId).toAccount(
            balanceCents = accountDao.getBalanceCents(profileId, accountId),
        )
    }

    override suspend fun getDefaultAccount(profileId: Long): Account {
        val entity = accountDao.getDefault(profileId) ?: throw AccountNotFoundException()
        return entity.toAccount(balanceCents = accountDao.getBalanceCents(profileId, entity.id))
    }

    override suspend fun createAccount(profileId: Long, input: CreateAccountInput): Account {
        val normalized = input.normalized()
        val now = clock()

        val accountId = database.withTransaction {
            val isDefault = accountDao.countByProfile(profileId) == 0
            accountDao.insert(
                AccountEntity(
                    profileId = profileId,
                    name = normalized.name,
                    icon = normalized.icon,
                    color = normalized.color,
                    type = normalized.type.storageValue,
                    currencyCode = normalized.currencyCode,
                    isDefault = isDefault,
                    includeInTotal = normalized.includeInTotal,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }

        return getAccount(profileId, accountId)
    }

    override suspend fun updateAccount(
        profileId: Long,
        accountId: Long,
        input: UpdateAccountInput,
    ): Account {
        val now = clock()

        database.withTransaction {
            val existing = requireAccount(profileId, accountId)
            val requestedCurrency = input.currencyCode?.normalizedCurrencyCode()
                ?: input.currencyCode?.let { throw InvalidAccountCurrencyException() }
            if (requestedCurrency != null && requestedCurrency != existing.currencyCode) {
                throw CurrencyImmutableException()
            }

            val updated = existing.copy(
                name = input.name.normalizedName() ?: existing.name,
                icon = input.icon.normalizedText() ?: existing.icon,
                color = input.color.normalizedText() ?: existing.color,
                type = input.type?.storageValue ?: existing.type,
                includeInTotal = input.includeInTotal ?: existing.includeInTotal,
                updatedAtEpochMillis = now,
            )
            if (accountDao.updateAndReturnCount(updated) != 1) {
                throw AccountNotFoundException()
            }
        }

        return getAccount(profileId, accountId)
    }

    override suspend fun setDefaultAccount(profileId: Long, accountId: Long): Account {
        val now = clock()
        database.withTransaction {
            requireAccount(profileId, accountId)
            accountDao.setDefault(profileId, accountId, now)
        }
        return getAccount(profileId, accountId)
    }

    override suspend fun deleteAccount(profileId: Long, accountId: Long) {
        database.withTransaction {
            val account = requireAccount(profileId, accountId)
            val totalAccounts = accountDao.countByProfile(profileId)
            if (totalAccounts <= 1) {
                throw CannotDeleteLastAccountException()
            }
            if (accountDao.countTransactions(profileId, accountId) > 0) {
                throw AccountHasTransactionsException()
            }
            if (accountDao.countTransfers(profileId, accountId) > 0) {
                throw AccountHasTransfersException()
            }
            if (accountDao.countRecurring(profileId, accountId) > 0) {
                throw AccountHasRecurringTransactionsException()
            }
            if (accountDao.countTemplates(profileId, accountId) > 0) {
                throw AccountHasTemplatesException()
            }

            if (account.isDefault) {
                if (totalAccounts == 2) {
                    val replacement = accountDao.listByProfile(profileId)
                        .first { it.id != accountId }
                    accountDao.setDefault(profileId, replacement.id, clock())
                } else {
                    throw MustSetNewDefaultAccountException()
                }
            }

            if (accountDao.deleteById(profileId, accountId) != 1) {
                throw AccountNotFoundException()
            }
        }
    }

    private suspend fun requireAccount(profileId: Long, accountId: Long): AccountEntity {
        return accountDao.getById(profileId, accountId) ?: throw AccountNotFoundException()
    }
}

private fun AccountEntity.toAccount(balanceCents: Long): Account {
    return Account(
        id = id,
        profileId = profileId,
        name = name,
        icon = icon,
        color = color,
        type = AccountType.fromStorageValue(type),
        currencyCode = currencyCode,
        isDefault = isDefault,
        includeInTotal = includeInTotal,
        balanceCents = balanceCents,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun CreateAccountInput.normalized(): CreateAccountInput {
    return copy(
        name = name.normalizedName() ?: throw AccountNameEmptyException(),
        icon = icon.normalizedText() ?: DEFAULT_ACCOUNT_ICON,
        color = color.normalizedText() ?: DEFAULT_ACCOUNT_COLOR,
        currencyCode = currencyCode.normalizedCurrencyCode() ?: throw InvalidAccountCurrencyException(),
    )
}

private fun String?.normalizedName(): String? {
    return normalizedText()?.also {
        if (it.isBlank()) {
            throw AccountNameEmptyException()
        }
    }
}

private fun String?.normalizedText(): String? {
    return this?.trim()?.takeIf { it.isNotEmpty() }
}

private val CurrencyCodePattern = Regex("[A-Z]{3}")

private fun String?.normalizedCurrencyCode(): String? {
    return this
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { CurrencyCodePattern.matches(it) }
}
