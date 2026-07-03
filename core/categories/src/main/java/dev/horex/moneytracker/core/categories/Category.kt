package dev.horex.moneytracker.core.categories

const val DEFAULT_CATEGORY_ICON = "tag"
const val DEFAULT_CATEGORY_COLOR = "#6366f1"

data class Category(
    val id: Long,
    val profileId: Long,
    val name: String,
    val icon: String,
    val type: CategoryType,
    val color: String,
    val isProtected: Boolean,
    val updatedAtEpochMillis: Long,
    val deletedAtEpochMillis: Long?,
    val localizationKey: String? = null,
) {
    val isDeleted: Boolean
        get() = deletedAtEpochMillis != null
}

enum class CategoryType(val storageValue: String) {
    Expense("expense"),
    Income("income"),
    Both("both"),
    Savings("savings"),
    Transfer("transfer"),
    Adjustment("adjustment"),
    ;

    companion object {
        fun fromStorageValue(value: String): CategoryType {
            return entries.firstOrNull { it.storageValue == value } ?: Both
        }
    }
}

enum class CategorySortOrder {
    NameAsc,
    NameDesc,
    Frequency,
}

data class CreateCategoryInput(
    val name: String,
    val icon: String = DEFAULT_CATEGORY_ICON,
    val type: CategoryType = CategoryType.Both,
    val color: String = DEFAULT_CATEGORY_COLOR,
)

data class UpdateCategoryInput(
    val name: String? = null,
    val icon: String? = null,
    val type: CategoryType? = null,
    val color: String? = null,
)
