package dev.horex.moneytracker.core.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTrackerBackupMigrationRehearsalTest {
    @Test
    fun anonymizedFixtureKeepsExpectedFinancialTotals() {
        val backup = MoneyTrackerBackupV1Json.decodeFromString(fixture(FULL_DOMAIN_FIXTURE))
        val profile = backup.profiles.single()

        assertEquals(
            mapOf(
                "EUR" to AmountTotals(incomeCents = 46_000, expenseCents = 0, netCents = 46_000),
                "RUB" to AmountTotals(incomeCents = 10_000, expenseCents = 0, netCents = 10_000),
                "USD" to AmountTotals(incomeCents = 250_000, expenseCents = 52_750, netCents = 197_250),
            ),
            profile.transactionTotalsByCurrency(),
        )
        assertEquals(
            mapOf(
                "EUR" to 46_000L,
                "RUB" to 10_000L,
                "USD" to 197_250L,
            ),
            profile.includedAccountTotalsByCurrency(),
        )
        assertEquals(
            mapOf(
                "USD->EUR" to TransferTotals(
                    transferCount = 1,
                    fromAmountCents = 50_000,
                    toAmountCents = 46_000,
                ),
            ),
            profile.transferTotalsByCurrencyPair(),
        )
        assertEquals(mapOf("USD" to 100_000L), profile.budgetLimitsByCurrency())
        assertEquals(
            mapOf("USD" to AmountTotals(incomeCents = 0, expenseCents = 121_500, netCents = -121_500)),
            profile.recurringTotalsByCurrency(),
        )
        assertEquals(mapOf("EUR" to 46_000L, "USD" to 15_000L), profile.savingsCurrentByCurrency())
        assertEquals(
            mapOf(
                "EUR" to AmountTotals(incomeCents = 46_000, expenseCents = 0, netCents = 46_000),
                "USD" to AmountTotals(incomeCents = 0, expenseCents = 5_000, netCents = -5_000),
            ),
            profile.goalTransactionTotalsByCurrency(),
        )
    }

    @Test
    fun anonymizedFixtureHasNoSourceIdentityMarkersInKeysValuesOrRefs() {
        val encoded = fixture(FULL_DOMAIN_FIXTURE)
        val root = MoneyTrackerBackupV1Json.json.parseToJsonElement(encoded)

        val forbiddenKeys = collectJsonKeys(root).filter(::isForbiddenIdentityKey)
        assertTrue("Forbidden identity keys: $forbiddenKeys", forbiddenKeys.isEmpty())

        val forbiddenStrings = collectJsonStrings(root).filter(::isForbiddenIdentityValue)
        assertTrue("Forbidden identity values: $forbiddenStrings", forbiddenStrings.isEmpty())

        val forbiddenRefs = collectBackupRefs(root).filter { ref ->
            forbiddenRefParts.any { part -> part in ref.lowercase() }
        }
        assertTrue("Forbidden export refs: $forbiddenRefs", forbiddenRefs.isEmpty())

        forbiddenRawFragments.forEach { fragment ->
            assertFalse(
                "Fixture must not contain raw fragment '$fragment'.",
                encoded.contains(fragment, ignoreCase = true),
            )
        }
    }

    private fun BackupProfile.transactionTotalsByCurrency(): Map<String, AmountTotals> {
        return transactions
            .groupingBy { it.currencyCode }
            .aggregateTo(sortedMapOf()) { _, totals: AmountTotals?, transaction, _ ->
                (totals ?: AmountTotals()).plus(transaction.type, transaction.amountCents)
            }
    }

    private fun BackupProfile.includedAccountTotalsByCurrency(): Map<String, Long> {
        val accountsByRef = accounts.associateBy { it.ref }
        val balances = sortedMapOf<String, Long>()
        transactions.forEach { transaction ->
            val account = accountsByRef.getValue(transaction.accountRef)
            if (account.includeInTotal) {
                val signedAmount = when (transaction.type) {
                    BackupTransactionType.Income -> transaction.amountCents
                    BackupTransactionType.Expense -> -transaction.amountCents
                }
                balances[transaction.currencyCode] = balances.getOrDefault(transaction.currencyCode, 0L) + signedAmount
            }
        }
        return balances
    }

    private fun BackupProfile.transferTotalsByCurrencyPair(): Map<String, TransferTotals> {
        val transactionsByRef = transactions.associateBy { it.ref }
        return transfers
            .groupingBy { "${it.fromCurrencyCode}->${it.toCurrencyCode}" }
            .aggregateTo(sortedMapOf()) { _, totals: TransferTotals?, transfer, _ ->
                val toAmount = transfer.toTransactionRef
                    ?.let(transactionsByRef::get)
                    ?.amountCents
                    ?: 0L
                (totals ?: TransferTotals()).plus(transfer.amountCents, toAmount)
            }
    }

    private fun BackupProfile.budgetLimitsByCurrency(): Map<String, Long> {
        return budgets
            .groupingBy { it.currencyCode }
            .foldTo(sortedMapOf(), 0L) { total, budget -> total + budget.limitCents }
    }

    private fun BackupProfile.recurringTotalsByCurrency(): Map<String, AmountTotals> {
        return recurringTransactions
            .groupingBy { it.currencyCode }
            .aggregateTo(sortedMapOf()) { _, totals: AmountTotals?, recurring, _ ->
                (totals ?: AmountTotals()).plus(recurring.type, recurring.amountCents)
            }
    }

    private fun BackupProfile.savingsCurrentByCurrency(): Map<String, Long> {
        return savingsGoals
            .groupingBy { it.currencyCode }
            .foldTo(sortedMapOf(), 0L) { total, goal -> total + goal.currentCents }
    }

    private fun BackupProfile.goalTransactionTotalsByCurrency(): Map<String, AmountTotals> {
        val goalsByRef = savingsGoals.associateBy { it.ref }
        return goalTransactions
            .groupingBy { goalsByRef.getValue(it.goalRef).currencyCode }
            .aggregateTo(sortedMapOf()) { _, totals: AmountTotals?, transaction, _ ->
                val transactionType = when (transaction.type) {
                    BackupGoalTransactionType.Deposit -> BackupTransactionType.Income
                    BackupGoalTransactionType.Withdraw -> BackupTransactionType.Expense
                }
                (totals ?: AmountTotals()).plus(transactionType, transaction.amountCents)
            }
    }

    private fun AmountTotals.plus(type: BackupTransactionType, amountCents: Long): AmountTotals {
        return when (type) {
            BackupTransactionType.Income -> copy(
                incomeCents = incomeCents + amountCents,
                netCents = netCents + amountCents,
            )
            BackupTransactionType.Expense -> copy(
                expenseCents = expenseCents + amountCents,
                netCents = netCents - amountCents,
            )
        }
    }

    private fun TransferTotals.plus(fromAmountCents: Long, toAmountCents: Long): TransferTotals {
        return copy(
            transferCount = transferCount + 1,
            fromAmountCents = this.fromAmountCents + fromAmountCents,
            toAmountCents = this.toAmountCents + toAmountCents,
        )
    }

    private fun collectJsonKeys(element: JsonElement): Set<String> {
        return when (element) {
            is JsonObject -> element.keys + element.values.flatMap { collectJsonKeys(it) }
            is JsonArray -> element.flatMap { collectJsonKeys(it) }.toSet()
            is JsonPrimitive -> emptySet()
        }
    }

    private fun collectJsonStrings(element: JsonElement): List<String> {
        return when (element) {
            is JsonObject -> element.values.flatMap(::collectJsonStrings)
            is JsonArray -> element.flatMap(::collectJsonStrings)
            is JsonPrimitive -> if (element.isString) listOf(element.contentOrNull.orEmpty()) else emptyList()
        }
    }

    private fun collectBackupRefs(element: JsonElement): List<String> {
        return when (element) {
            is JsonObject -> element.entries.flatMap { (key, value) ->
                val ownRef = if (
                    value is JsonPrimitive &&
                    value.isString &&
                    (key == "ref" || key.endsWith("_ref"))
                ) {
                    listOf(value.contentOrNull.orEmpty())
                } else {
                    emptyList()
                }
                ownRef + collectBackupRefs(value)
            }
            is JsonArray -> element.flatMap(::collectBackupRefs)
            is JsonPrimitive -> emptyList()
        }
    }

    private fun isForbiddenIdentityKey(key: String): Boolean {
        val normalized = key.normalizeIdentityToken()
        if (normalized == "id" || normalized.endsWith("_id")) {
            return true
        }
        if (normalized in exactForbiddenIdentityKeys) {
            return true
        }
        return forbiddenRefParts.any { part ->
            normalized == part ||
                normalized.startsWith("${part}_") ||
                normalized.endsWith("_$part") ||
                normalized.contains("_${part}_")
        }
    }

    private fun isForbiddenIdentityValue(value: String): Boolean {
        val normalized = value.lowercase()
        return forbiddenValueFragments.any { fragment -> fragment in normalized } ||
            botOrChatMetadataPattern.containsMatchIn(normalized)
    }

    private fun fixture(name: String): String {
        return requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) {
            "Missing fixture $name"
        }.readText()
    }

    private fun String.normalizeIdentityToken(): String {
        return replace(camelBoundary, "$1_$2")
            .replace('-', '_')
            .lowercase()
    }

    private data class AmountTotals(
        val incomeCents: Long = 0,
        val expenseCents: Long = 0,
        val netCents: Long = 0,
    )

    private data class TransferTotals(
        val transferCount: Int = 0,
        val fromAmountCents: Long = 0,
        val toAmountCents: Long = 0,
    )

    private companion object {
        const val FULL_DOMAIN_FIXTURE = "money_tracker_backup_v1_anonymized_full.json"
        val camelBoundary = Regex("([a-z0-9])([A-Z])")
        val botOrChatMetadataPattern = Regex("""(^|[^a-z0-9])(bot|chat)([^a-z0-9]|$)""")
        val exactForbiddenIdentityKeys = setOf(
            "telegram",
            "telegram_id",
            "telegram_user_id",
            "telegram_username",
            "username",
            "first_name",
            "last_name",
            "init_data",
            "init_data_unsafe",
            "auth_date",
            "hash",
            "query_id",
            "bot",
            "bot_id",
            "chat",
            "chat_id",
            "legacy",
            "legacy_id",
            "source",
            "source_id",
            "source_db_id",
        )
        val forbiddenRefParts = listOf("telegram", "source", "legacy", "init_data", "bot", "chat")
        val forbiddenValueFragments = listOf(
            "telegram",
            "initdata",
            "init_data",
            "legacy",
            "source",
            "sqlcipher",
            "passphrase",
            "keystore",
            "device-bound",
            "device_bound",
            "device bound",
            "secret",
            "password",
        )
        val forbiddenRawFragments = listOf(
            "4242424242",
            "987654321",
            "81001",
            "82001",
            "83001",
            "84001",
            "85001",
            "86001",
            "87001",
            "88001",
            "89001",
            "account:501",
            "transaction:701",
            "profile:901",
            "from_tx_id",
            "to_tx_id",
        )
    }
}
