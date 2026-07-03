package dev.horex.moneytracker.core.transactions

import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCsvExporterTest {
    @Test
    fun exporterWritesUtf8CsvAndEscapesUserText() = runBlocking {
        val repository = FakeTransactionsRepository(
            pages = mapOf(
                1L to listOf(
                    TransactionPage(
                        transactions = listOf(
                            transaction(
                                amountCents = 1_250,
                                note = "Lunch, \"combo\"",
                                categoryName = "Food",
                                accountName = "Cash \"box\"",
                                createdAtEpochMillis = Instant.parse("2026-04-28T10:15:00Z")
                                    .toEpochMilli(),
                            ),
                            transaction(
                                type = TransactionType.Income,
                                amountCents = 100_000,
                                note = "Зарплата",
                                categoryName = "Доход",
                                accountName = "Карта",
                                createdAtEpochMillis = Instant.parse("2026-04-27T08:00:00Z")
                                    .toEpochMilli(),
                            ),
                        ),
                        totalPages = 1,
                        currentPage = 1,
                    ),
                ),
            ),
        )
        val exporter = TransactionCsvExporter(repository)

        val export = exporter.exportTransactions(
            profiles = listOf(TransactionCsvProfileSelection(1L, "Main, profile")),
        )
        val csv = export.bytes.toString(Charsets.UTF_8)

        assertEquals(2, export.rowCount)
        assertTrue(csv.startsWith("Profile,Date,Type,Amount,Currency,Account,Category,Note\n"))
        assertTrue(
            csv.contains(
                "\"Main, profile\",2026-04-28 10:15,expense,12.50,USD," +
                    "\"Cash \"\"box\"\"\",Food,\"Lunch, \"\"combo\"\"\"",
            ),
        )
        assertTrue(csv.contains("Карта,Доход,Зарплата"))
        assertFalse(csv.lines().first().split(',').any { column -> column.equals("id", ignoreCase = true) })
    }

    @Test
    fun exporterAppliesFiltersToEveryPagedQuery() = runBlocking {
        val repository = FakeTransactionsRepository(
            pages = mapOf(
                1L to listOf(
                    TransactionPage(
                        transactions = listOf(transaction(amountCents = 100)),
                        totalPages = 2,
                        currentPage = 1,
                    ),
                    TransactionPage(
                        transactions = listOf(transaction(amountCents = 200)),
                        totalPages = 2,
                        currentPage = 2,
                    ),
                ),
                2L to listOf(
                    TransactionPage(
                        transactions = listOf(transaction(amountCents = 300)),
                        totalPages = 1,
                        currentPage = 1,
                    ),
                ),
            ),
        )
        val exporter = TransactionCsvExporter(repository)
        val filters = TransactionCsvExportFilters(
            accountId = 10,
            categoryId = 20,
            type = TransactionType.Expense,
            currencyCode = "EUR",
            fromEpochMillis = 1000,
            toEpochMillis = 2000,
            searchText = "coffee",
        )

        val export = exporter.exportTransactions(
            profiles = listOf(
                TransactionCsvProfileSelection(1L, "Personal"),
                TransactionCsvProfileSelection(2L, "Work"),
            ),
            filters = filters,
        )

        assertEquals(3, export.rowCount)
        assertEquals(listOf(1L, 1L, 2L), repository.queries.map { it.profileId })
        assertEquals(listOf(1, 2, 1), repository.queries.map { it.query.page })
        repository.queries.forEach { recorded ->
            assertEquals(10L, recorded.query.accountId)
            assertEquals(20L, recorded.query.categoryId)
            assertEquals(TransactionType.Expense, recorded.query.type)
            assertEquals("EUR", recorded.query.currencyCode)
            assertEquals(1000L, recorded.query.fromEpochMillis)
            assertEquals(2000L, recorded.query.toEpochMillis)
            assertEquals("coffee", recorded.query.searchText)
            assertEquals(MAX_TRANSACTION_PAGE_SIZE, recorded.query.pageSize)
        }
    }

    @Test
    fun exporterRejectsEmptyProfileSelection() = runBlocking {
        val exporter = TransactionCsvExporter(FakeTransactionsRepository())

        try {
            exporter.exportTransactions(emptyList())
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("CSV export"))
            return@runBlocking
        }

        error("Expected IllegalArgumentException")
    }
}

private class FakeTransactionsRepository(
    private val pages: Map<Long, List<TransactionPage>> = emptyMap(),
) : TransactionsRepository {
    val queries = mutableListOf<RecordedTransactionQuery>()
    private val nextPageByProfile = mutableMapOf<Long, Int>()

    override suspend fun addTransaction(profileId: Long, input: CreateTransactionInput): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun applyBalanceAdjustment(
        profileId: Long,
        input: BalanceAdjustmentInput,
    ): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun getTransaction(profileId: Long, transactionId: Long): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun listTransactions(profileId: Long, query: TransactionQuery): TransactionPage {
        queries += RecordedTransactionQuery(profileId, query)
        val index = nextPageByProfile.getOrDefault(profileId, 0)
        nextPageByProfile[profileId] = index + 1
        return pages[profileId]?.getOrNull(index) ?: TransactionPage(
            transactions = emptyList(),
            totalPages = 1,
            currentPage = 1,
        )
    }

    override suspend fun updateTransaction(
        profileId: Long,
        transactionId: Long,
        input: UpdateTransactionInput,
    ): MoneyTransaction {
        throw UnsupportedOperationException()
    }

    override suspend fun deleteTransaction(profileId: Long, transactionId: Long) {
        throw UnsupportedOperationException()
    }
}

private data class RecordedTransactionQuery(
    val profileId: Long,
    val query: TransactionQuery,
)

private fun transaction(
    type: TransactionType = TransactionType.Expense,
    amountCents: Long,
    categoryName: String = "Food",
    accountName: String = "Cash",
    note: String = "",
    createdAtEpochMillis: Long = Instant.parse("2026-04-28T10:00:00Z").toEpochMilli(),
): MoneyTransaction {
    return MoneyTransaction(
        id = 1,
        profileId = 1,
        type = type,
        amountCents = amountCents,
        categoryId = 2,
        categoryName = categoryName,
        categoryIcon = "tag",
        categoryColor = "#64748b",
        accountId = 3,
        accountName = accountName,
        note = note,
        currencyCode = "USD",
        snapshotDate = "2026-04-28",
        createdAtEpochMillis = createdAtEpochMillis,
        isAdjustment = false,
    )
}
