package dev.horex.moneytracker.core.transactions

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class TransactionCsvExporter(
    private val transactionsRepository: TransactionsRepository,
) {
    suspend fun exportTransactions(
        profiles: List<TransactionCsvProfileSelection>,
        filters: TransactionCsvExportFilters = TransactionCsvExportFilters(),
    ): TransactionCsvExport {
        require(profiles.isNotEmpty()) { "At least one local profile must be selected for CSV export." }

        val rows = mutableListOf<TransactionCsvRow>()
        profiles.forEach { profile ->
            var page = 1
            do {
                val transactionPage = transactionsRepository.listTransactions(
                    profileId = profile.profileId,
                    query = filters.toQuery(page),
                )
                rows += transactionPage.transactions.map { transaction ->
                    TransactionCsvRow(
                        profileLabel = profile.profileLabel,
                        transaction = transaction,
                    )
                }
                page += 1
            } while (page <= transactionPage.totalPages)
        }

        val text = TransactionCsvWriter.encode(rows)
        val bytes = text.toByteArray(Charsets.UTF_8)
        return TransactionCsvExport(
            rowCount = rows.size,
            bytes = bytes,
        )
    }
}

data class TransactionCsvProfileSelection(
    val profileId: Long,
    val profileLabel: String,
)

data class TransactionCsvExportFilters(
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val type: TransactionType? = null,
    val currencyCode: String? = null,
    val fromEpochMillis: Long? = null,
    val toEpochMillis: Long? = null,
    val searchText: String? = null,
) {
    fun toQuery(page: Int): TransactionQuery {
        return TransactionQuery(
            accountId = accountId,
            categoryId = categoryId,
            type = type,
            currencyCode = currencyCode,
            fromEpochMillis = fromEpochMillis,
            toEpochMillis = toEpochMillis,
            searchText = searchText,
            page = page,
            pageSize = MAX_TRANSACTION_PAGE_SIZE,
        )
    }
}

data class TransactionCsvExport(
    val rowCount: Int,
    val bytes: ByteArray,
) {
    val bytesWritten: Long
        get() = bytes.size.toLong()
}

data class TransactionCsvRow(
    val profileLabel: String,
    val transaction: MoneyTransaction,
)

object TransactionCsvWriter {
    private val headers = listOf(
        "Profile",
        "Date",
        "Type",
        "Amount",
        "Currency",
        "Account",
        "Category",
        "Note",
    )

    fun encode(rows: List<TransactionCsvRow>): String {
        return buildString {
            appendRecord(headers)
            rows.forEach { row ->
                appendRecord(row.toFields())
            }
        }
    }

    private fun TransactionCsvRow.toFields(): List<String> {
        return listOf(
            profileLabel,
            transaction.createdAtEpochMillis.formatCsvDateTime(),
            transaction.type.storageValue,
            transaction.amountCents.formatCents(),
            transaction.currencyCode,
            transaction.accountName,
            transaction.categoryName,
            transaction.note,
        )
    }

    private fun StringBuilder.appendRecord(fields: List<String>) {
        append(fields.joinToString(separator = ",") { it.escapeCsvField() })
        append('\n')
    }

    private fun String.escapeCsvField(): String {
        val mustQuote = any { char -> char == ',' || char == '"' || char == '\n' || char == '\r' }
        if (!mustQuote) {
            return this
        }
        return buildString {
            append('"')
            this@escapeCsvField.forEach { char ->
                if (char == '"') {
                    append("\"\"")
                } else {
                    append(char)
                }
            }
            append('"')
        }
    }

    private fun Long.formatCsvDateTime(): String {
        return Instant.ofEpochMilli(this)
            .atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
    }

    private fun Long.formatCents(): String {
        val whole = this / 100
        val cents = this % 100
        return "%d.%02d".format(Locale.US, whole, cents)
    }
}
