package dev.horex.moneytracker.core.templates

import dev.horex.moneytracker.core.transactions.MoneyTransaction

interface TransactionTemplatesRepository {
    suspend fun listTemplates(profileId: Long): List<TransactionTemplate>

    suspend fun getTemplate(profileId: Long, templateId: Long): TransactionTemplate

    suspend fun createTemplate(
        profileId: Long,
        input: CreateTransactionTemplateInput,
    ): TransactionTemplate

    suspend fun updateTemplate(
        profileId: Long,
        templateId: Long,
        input: UpdateTransactionTemplateInput,
    ): TransactionTemplate

    suspend fun deleteTemplate(profileId: Long, templateId: Long)

    suspend fun reorderTemplates(
        profileId: Long,
        orderedTemplateIds: List<Long>,
    ): List<TransactionTemplate>

    suspend fun applyTemplate(
        profileId: Long,
        templateId: Long,
        input: ApplyTransactionTemplateInput = ApplyTransactionTemplateInput(),
    ): MoneyTransaction
}
