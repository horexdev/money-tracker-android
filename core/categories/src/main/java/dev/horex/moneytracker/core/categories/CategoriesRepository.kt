package dev.horex.moneytracker.core.categories

interface CategoriesRepository {
    suspend fun listCategories(
        profileId: Long,
        type: CategoryType? = null,
        sortOrder: CategorySortOrder = CategorySortOrder.NameAsc,
    ): List<Category>

    suspend fun getCategory(profileId: Long, categoryId: Long): Category

    suspend fun getProtectedCategoryByType(profileId: Long, type: CategoryType): Category

    suspend fun hasEditableCategories(profileId: Long): Boolean

    suspend fun createCategory(profileId: Long, input: CreateCategoryInput): Category

    suspend fun updateCategory(profileId: Long, categoryId: Long, input: UpdateCategoryInput): Category

    suspend fun deleteCategory(profileId: Long, categoryId: Long)
}
