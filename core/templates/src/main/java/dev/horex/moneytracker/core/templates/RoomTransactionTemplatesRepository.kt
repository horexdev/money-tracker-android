package dev.horex.moneytracker.core.templates

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.dao.TransactionTemplateWithRelations
import dev.horex.moneytracker.core.database.model.AccountEntity
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.TransactionTemplateEntity
import dev.horex.moneytracker.core.transactions.CreateTransactionInput
import dev.horex.moneytracker.core.transactions.MoneyTransaction
import dev.horex.moneytracker.core.transactions.RoomTransactionsRepository
import dev.horex.moneytracker.core.transactions.TransactionType
import dev.horex.moneytracker.core.transactions.TransactionsRepository

class RoomTransactionTemplatesRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val transactionsRepository: TransactionsRepository = RoomTransactionsRepository(database, clock),
) : TransactionTemplatesRepository {
    private val accountDao = database.accountDao()
    private val categoryDao = database.categoryDao()
    private val templateDao = database.transactionTemplateDao()

    override suspend fun listTemplates(profileId: Long): List<TransactionTemplate> {
        return templateDao.listWithRelationsByProfile(profileId).map(TransactionTemplateWithRelations::toTemplate)
    }

    override suspend fun getTemplate(profileId: Long, templateId: Long): TransactionTemplate {
        return templateDao.getWithRelationsById(profileId, templateId)?.toTemplate()
            ?: throw TransactionTemplateNotFoundException()
    }

    override suspend fun createTemplate(
        profileId: Long,
        input: CreateTransactionTemplateInput,
    ): TransactionTemplate {
        requirePositiveTemplateAmount(input.amountCents)
        input.sortOrder?.let(::requireValidSortOrder)
        val now = clock()
        val templateId = database.withTransaction {
            val account = requireAccount(profileId, input.accountId)
            val category = requireActiveCategory(profileId, input.categoryId)
            requireCategorySupportsType(category, input.type)
            val sortOrder = input.sortOrder ?: templateDao.getMaxSortOrder(profileId) + 1

            templateDao.insert(
                TransactionTemplateEntity(
                    profileId = profileId,
                    name = input.name,
                    type = input.type.storageValue,
                    amountCents = input.amountCents,
                    amountFixed = input.amountMode.amountFixed,
                    categoryId = category.id,
                    accountId = account.id,
                    currencyCode = account.currencyCode,
                    note = input.note,
                    sortOrder = sortOrder,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }

        return getTemplate(profileId, templateId)
    }

    override suspend fun updateTemplate(
        profileId: Long,
        templateId: Long,
        input: UpdateTransactionTemplateInput,
    ): TransactionTemplate {
        input.amountCents?.let(::requirePositiveTemplateAmount)
        val now = clock()

        database.withTransaction {
            val existing = templateDao.getById(profileId, templateId)
                ?: throw TransactionTemplateNotFoundException()
            val type = input.type ?: TransactionType.fromStorageValue(existing.type)
            val account = requireAccount(profileId, input.accountId ?: existing.accountId)
            val category = requireActiveCategory(profileId, input.categoryId ?: existing.categoryId)
            requireCategorySupportsType(category, type)

            val updated = existing.copy(
                name = input.name ?: existing.name,
                type = type.storageValue,
                amountCents = input.amountCents ?: existing.amountCents,
                amountFixed = input.amountMode?.amountFixed ?: existing.amountFixed,
                categoryId = category.id,
                accountId = account.id,
                currencyCode = account.currencyCode,
                note = input.note ?: existing.note,
                updatedAtEpochMillis = now,
            )
            if (templateDao.updateAndReturnCount(updated) != 1) {
                throw TransactionTemplateNotFoundException()
            }
        }

        return getTemplate(profileId, templateId)
    }

    override suspend fun deleteTemplate(profileId: Long, templateId: Long) {
        if (templateDao.deleteById(profileId, templateId) != 1) {
            throw TransactionTemplateNotFoundException()
        }
    }

    override suspend fun reorderTemplates(
        profileId: Long,
        orderedTemplateIds: List<Long>,
    ): List<TransactionTemplate> {
        val now = clock()
        database.withTransaction {
            val currentIds = templateDao.listByProfile(profileId).map { it.id }
            val stableIds = stableTemplateReorder(
                currentIdsInStableOrder = currentIds,
                requestedIdsInNewOrder = orderedTemplateIds,
            )
            stableIds.forEachIndexed { index, templateId ->
                if (templateDao.updateSortOrder(profileId, templateId, index, now) != 1) {
                    throw TransactionTemplateNotFoundException()
                }
            }
        }
        return listTemplates(profileId)
    }

    override suspend fun applyTemplate(
        profileId: Long,
        templateId: Long,
        input: ApplyTransactionTemplateInput,
    ): MoneyTransaction {
        val template = templateDao.getById(profileId, templateId)
            ?: throw TransactionTemplateNotFoundException()
        val amount = resolveTemplateApplyAmount(
            amountMode = TransactionTemplateAmountMode.fromAmountFixed(template.amountFixed),
            storedAmountCents = template.amountCents,
            variableAmountCents = input.variableAmountCents,
        )

        return transactionsRepository.addTransaction(
            profileId = profileId,
            input = CreateTransactionInput(
                type = TransactionType.fromStorageValue(template.type),
                amountCents = amount,
                categoryId = template.categoryId,
                accountId = template.accountId,
                note = template.note,
                createdAtEpochMillis = input.createdAtEpochMillis,
            ),
        )
    }

    private suspend fun requireAccount(profileId: Long, accountId: Long): AccountEntity {
        return accountDao.getById(profileId, accountId) ?: throw TransactionTemplateAccountNotFoundException()
    }

    private suspend fun requireActiveCategory(profileId: Long, categoryId: Long): CategoryEntity {
        return categoryDao.getActiveById(profileId, categoryId) ?: throw TransactionTemplateCategoryNotFoundException()
    }
}

private const val BOTH_CATEGORY_TYPE = "both"
private const val TRANSFER_CATEGORY_TYPE = "transfer"
private const val ADJUSTMENT_CATEGORY_TYPE = "adjustment"
private const val SAVINGS_CATEGORY_TYPE = "savings"

private fun requireCategorySupportsType(category: CategoryEntity, type: TransactionType) {
    val canUse = category.type == BOTH_CATEGORY_TYPE || category.type == type.storageValue
    if (
        !canUse ||
        category.type == TRANSFER_CATEGORY_TYPE ||
        category.type == ADJUSTMENT_CATEGORY_TYPE ||
        category.type == SAVINGS_CATEGORY_TYPE
    ) {
        throw TransactionTemplateCategoryTypeException()
    }
}

private fun TransactionTemplateWithRelations.toTemplate(): TransactionTemplate {
    return TransactionTemplate(
        id = template.id,
        profileId = template.profileId,
        name = template.name,
        type = TransactionType.fromStorageValue(template.type),
        amountCents = template.amountCents,
        amountMode = TransactionTemplateAmountMode.fromAmountFixed(template.amountFixed),
        categoryId = template.categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        accountId = template.accountId,
        accountName = accountName,
        currencyCode = template.currencyCode,
        note = template.note,
        sortOrder = template.sortOrder,
        createdAtEpochMillis = template.createdAtEpochMillis,
        updatedAtEpochMillis = template.updatedAtEpochMillis,
    )
}
