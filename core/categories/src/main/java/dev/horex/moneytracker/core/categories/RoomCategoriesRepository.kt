package dev.horex.moneytracker.core.categories

import androidx.room.withTransaction
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.model.CategoryEntity

class RoomCategoriesRepository(
    private val database: MoneyTrackerDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : CategoriesRepository {
    private val categoryDao = database.categoryDao()

    override suspend fun listCategories(
        profileId: Long,
        type: CategoryType?,
        sortOrder: CategorySortOrder,
    ): List<Category> {
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
        return entities.map(CategoryEntity::toCategory)
    }

    override suspend fun getCategory(profileId: Long, categoryId: Long): Category {
        return requireCategory(profileId, categoryId).toCategory()
    }

    override suspend fun getProtectedCategoryByType(profileId: Long, type: CategoryType): Category {
        return categoryDao.getProtectedByType(profileId, type.storageValue)?.toCategory()
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
        database.withTransaction {
            val existing = requireActiveCategory(profileId, categoryId)
            if (existing.isProtected) {
                throw CategoryProtectedException()
            }

            val updated = existing.copy(
                name = input.name.normalizedName() ?: existing.name,
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
}

private fun CategoryEntity.toCategory(): Category {
    return Category(
        id = id,
        profileId = profileId,
        name = name,
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
