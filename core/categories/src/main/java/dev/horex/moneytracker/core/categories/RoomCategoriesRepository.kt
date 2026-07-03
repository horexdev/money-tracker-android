package dev.horex.moneytracker.core.categories

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.model.CategoryEntity
import dev.horex.moneytracker.core.database.model.SystemCategoryLocalization

class RoomCategoriesRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : CategoriesRepository {
    private val categoryDao = database.categoryDao()
    private val localProfileDao = database.localProfileDao()

    override suspend fun listCategories(
        profileId: Long,
        type: CategoryType?,
        sortOrder: CategorySortOrder,
    ): List<Category> {
        val languageCode = requireProfileLanguage(profileId)
        val entities = when (sortOrder) {
            CategorySortOrder.NameAsc -> if (type == null) {
                categoryDao.listEditableByProfile(profileId)
            } else {
                categoryDao.listEditableByType(profileId, type.storageValue)
            }

            CategorySortOrder.NameDesc -> if (type == null) {
                categoryDao.listByProfileNameDesc(profileId)
            } else {
                categoryDao.listByTypeNameDesc(profileId, type.storageValue)
            }

            CategorySortOrder.Frequency -> if (type == null) {
                categoryDao.listByFrequency(profileId)
            } else {
                categoryDao.listByTypeFrequency(profileId, type.storageValue)
            }
        }
        val categories = entities.map { it.toCategory(languageCode) }
        return when (sortOrder) {
            CategorySortOrder.NameAsc -> categories.sortedBy { it.name.lowercase() }
            CategorySortOrder.NameDesc -> categories.sortedByDescending { it.name.lowercase() }
            CategorySortOrder.Frequency -> categories
        }
    }

    override suspend fun listAllCategories(
        profileId: Long,
        type: CategoryType?,
        sortOrder: CategorySortOrder,
    ): List<Category> {
        val languageCode = requireProfileLanguage(profileId)
        val categories = if (type == null) {
            categoryDao.listByProfile(profileId)
        } else {
            categoryDao.listByType(profileId, type.storageValue)
        }.map { it.toCategory(languageCode) }

        return when (sortOrder) {
            CategorySortOrder.NameAsc -> categories.sortedBy { it.name.lowercase() }
            CategorySortOrder.Frequency -> categories

            CategorySortOrder.NameDesc -> categories.sortedByDescending { it.name.lowercase() }
        }
    }

    override suspend fun getCategory(profileId: Long, categoryId: Long): Category {
        return requireCategory(profileId, categoryId).toCategory(requireProfileLanguage(profileId))
    }

    override suspend fun getProtectedCategoryByType(profileId: Long, type: CategoryType): Category {
        val languageCode = requireProfileLanguage(profileId)
        return categoryDao.getProtectedByType(profileId, type.storageValue)?.toCategory(languageCode)
            ?: throw CategoryNotFoundException()
    }

    override suspend fun hasEditableCategories(profileId: Long): Boolean {
        return categoryDao.countEditableByProfile(profileId) > 0
    }

    override suspend fun createCategory(profileId: Long, input: CreateCategoryInput): Category {
        val normalized = input.normalized()
        val now = clock()
        val categoryId = categoryDao.insert(
            CategoryEntity(
                profileId = profileId,
                name = normalized.name,
                icon = normalized.icon,
                type = normalized.type.storageValue,
                color = normalized.color,
                isProtected = false,
                updatedAtEpochMillis = now,
            ),
        )
        return getCategory(profileId, categoryId)
    }

    override suspend fun updateCategory(
        profileId: Long,
        categoryId: Long,
        input: UpdateCategoryInput,
    ): Category {
        val now = clock()
        val languageCode = requireProfileLanguage(profileId)
        database.withTransaction {
            val existing = requireActiveCategory(profileId, categoryId)
            if (existing.isProtected) {
                throw CategoryProtectedException()
            }

            val requestedName = input.name.normalizedName()
            val existingDisplayName = SystemCategoryLocalization.displayName(
                existing.localizationKey,
                languageCode,
                existing.name,
            )
            val preservesLocalizedName = requestedName == null || requestedName == existingDisplayName
            val updatedName = if (preservesLocalizedName) {
                existing.name
            } else {
                checkNotNull(requestedName)
            }
            val updated = existing.copy(
                name = updatedName,
                localizationKey = if (preservesLocalizedName) existing.localizationKey else null,
                icon = input.icon.normalizedText() ?: existing.icon,
                type = input.type.normalizedEditableType() ?: existing.type,
                color = input.color.normalizedText() ?: existing.color,
                updatedAtEpochMillis = now,
            )
            if (categoryDao.updateAndReturnCount(updated) != 1) {
                throw CategoryNotFoundException()
            }
        }
        return getCategory(profileId, categoryId)
    }

    override suspend fun deleteCategory(profileId: Long, categoryId: Long) {
        database.withTransaction {
            val existing = requireActiveCategory(profileId, categoryId)
            if (existing.isProtected) {
                throw CategoryProtectedException()
            }
            if (categoryDao.softDelete(profileId, categoryId, clock()) != 1) {
                throw CategoryNotFoundException()
            }
        }
    }

    private suspend fun requireCategory(profileId: Long, categoryId: Long): CategoryEntity {
        return categoryDao.getById(profileId, categoryId) ?: throw CategoryNotFoundException()
    }

    private suspend fun requireActiveCategory(profileId: Long, categoryId: Long): CategoryEntity {
        return categoryDao.getActiveById(profileId, categoryId) ?: throw CategoryNotFoundException()
    }

    private suspend fun requireProfileLanguage(profileId: Long): String {
        return localProfileDao.getById(profileId)?.languageCode ?: throw CategoryNotFoundException()
    }
}

private fun CategoryEntity.toCategory(languageCode: String): Category {
    return Category(
        id = id,
        profileId = profileId,
        name = SystemCategoryLocalization.displayName(localizationKey, languageCode, name),
        localizationKey = localizationKey,
        icon = icon,
        type = CategoryType.fromStorageValue(type),
        color = color,
        isProtected = isProtected,
        updatedAtEpochMillis = updatedAtEpochMillis,
        deletedAtEpochMillis = deletedAtEpochMillis,
    )
}

private fun CreateCategoryInput.normalized(): CreateCategoryInput {
    if (type.isInfrastructure) {
        throw CategoryProtectedException()
    }
    return copy(
        name = name.normalizedName() ?: throw CategoryNameEmptyException(),
        icon = icon.normalizedText() ?: DEFAULT_CATEGORY_ICON,
        color = color.normalizedText() ?: DEFAULT_CATEGORY_COLOR,
    )
}

private fun CategoryType?.normalizedEditableType(): String? {
    if (this?.isInfrastructure == true) {
        throw CategoryProtectedException()
    }
    return this?.storageValue
}

private val CategoryType.isInfrastructure: Boolean
    get() = this == CategoryType.Transfer || this == CategoryType.Adjustment

private fun String?.normalizedName(): String? {
    return normalizedText()?.also {
        if (it.isBlank()) {
            throw CategoryNameEmptyException()
        }
    }
}

private fun String?.normalizedText(): String? {
    return this?.trim()?.takeIf { it.isNotEmpty() }
}
