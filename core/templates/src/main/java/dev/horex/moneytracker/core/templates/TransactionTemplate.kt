package dev.horex.moneytracker.core.templates

import dev.horex.moneytracker.core.transactions.TransactionType

data class TransactionTemplate(
    val id: Long,
    val profileId: Long,
    val name: String,
    val type: TransactionType,
    val amountCents: Long,
    val amountMode: TransactionTemplateAmountMode,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val accountId: Long,
    val accountName: String,
    val currencyCode: String,
    val note: String,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val isFixedAmount: Boolean
        get() = amountMode == TransactionTemplateAmountMode.Fixed
}

enum class TransactionTemplateAmountMode {
    Fixed,
    Variable,
    ;

    val amountFixed: Boolean
        get() = this == Fixed

    companion object {
        fun fromAmountFixed(amountFixed: Boolean): TransactionTemplateAmountMode {
            return if (amountFixed) Fixed else Variable
        }
    }
}

data class CreateTransactionTemplateInput(
    val name: String = "",
    val type: TransactionType,
    val amountCents: Long,
    val amountMode: TransactionTemplateAmountMode = TransactionTemplateAmountMode.Fixed,
    val categoryId: Long,
    val accountId: Long,
    val note: String = "",
    val sortOrder: Int? = null,
)

data class UpdateTransactionTemplateInput(
    val name: String? = null,
    val type: TransactionType? = null,
    val amountCents: Long? = null,
    val amountMode: TransactionTemplateAmountMode? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val note: String? = null,
)

data class ApplyTransactionTemplateInput(
    val variableAmountCents: Long? = null,
    val createdAtEpochMillis: Long? = null,
)

internal fun requirePositiveTemplateAmount(amountCents: Long) {
    if (amountCents <= 0) {
        throw InvalidTransactionTemplateAmountException()
    }
}

internal fun requireValidSortOrder(sortOrder: Int) {
    if (sortOrder < 0) {
        throw InvalidTransactionTemplateSortOrderException()
    }
}

internal fun resolveTemplateApplyAmount(
    amountMode: TransactionTemplateAmountMode,
    storedAmountCents: Long,
    variableAmountCents: Long?,
): Long {
    requirePositiveTemplateAmount(storedAmountCents)
    return when (amountMode) {
        TransactionTemplateAmountMode.Fixed -> storedAmountCents
        TransactionTemplateAmountMode.Variable -> {
            val amount = variableAmountCents ?: throw InvalidTransactionTemplateAmountException()
            requirePositiveTemplateAmount(amount)
            amount
        }
    }
}

internal fun stableTemplateReorder(
    currentIdsInStableOrder: List<Long>,
    requestedIdsInNewOrder: List<Long>,
): List<Long> {
    if (requestedIdsInNewOrder.toSet().size != requestedIdsInNewOrder.size) {
        throw InvalidTransactionTemplateReorderException()
    }
    if (currentIdsInStableOrder.toSet() != requestedIdsInNewOrder.toSet()) {
        throw InvalidTransactionTemplateReorderException()
    }
    return requestedIdsInNewOrder
}
