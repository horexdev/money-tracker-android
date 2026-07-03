package dev.horex.moneytracker.feature.categories

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.horex.moneytracker.core.categories.Category
import dev.horex.moneytracker.core.categories.CategoryNameEmptyException
import dev.horex.moneytracker.core.categories.CategoryNotFoundException
import dev.horex.moneytracker.core.categories.CategoryProtectedException
import dev.horex.moneytracker.core.categories.CategorySortOrder
import dev.horex.moneytracker.core.categories.CategoryType
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.categories.CreateCategoryInput
import dev.horex.moneytracker.core.categories.UpdateCategoryInput
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import kotlinx.coroutines.launch

@Composable
fun CategoriesRoute(
    localProfileBootstrapper: LocalProfileBootstrapper,
    categoriesRepository: CategoriesRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Long?>(null) }
    var selectedFilter by remember { mutableStateOf(CategoryTypeFilter.All) }
    var uiState by remember { mutableStateOf(CategoriesUiState(isLoading = true)) }
    var sheetMode by remember { mutableStateOf<CategorySheetMode?>(null) }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }

    fun loadCategories(showLoading: Boolean, filterSnapshot: CategoryTypeFilter = selectedFilter) {
        scope.launch {
            if (showLoading) {
                uiState = uiState.copy(isLoading = true, error = null)
            }
            try {
                val profile = localProfileBootstrapper.ensureActiveProfile()
                profileId = profile.id
                val allCategories = categoriesRepository.listAllCategories(
                    profileId = profile.id,
                    sortOrder = CategorySortOrder.NameAsc,
                )
                uiState = CategoriesUiState(
                    categories = allCategories.filteredBy(filterSnapshot),
                    allCategories = allCategories,
                )
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    isMutating = false,
                    error = error.toCategoriesError(),
                )
            }
        }
    }

    fun mutate(closeSheet: Boolean = false, block: suspend (Long) -> Unit) {
        scope.launch {
            val activeProfileId = profileId ?: localProfileBootstrapper.ensureActiveProfile().id
            profileId = activeProfileId
            uiState = uiState.copy(isMutating = true, error = null)
            try {
                block(activeProfileId)
                val allCategories = categoriesRepository.listAllCategories(
                    profileId = activeProfileId,
                    sortOrder = CategorySortOrder.NameAsc,
                )
                uiState = CategoriesUiState(
                    categories = allCategories.filteredBy(selectedFilter),
                    allCategories = allCategories,
                )
                if (closeSheet) {
                    sheetMode = null
                }
                deleteTarget = null
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    isMutating = false,
                    error = error.toCategoriesError(),
                )
            }
        }
    }

    LaunchedEffect(localProfileBootstrapper, categoriesRepository, selectedFilter) {
        loadCategories(showLoading = true)
    }

    CategoriesScreen(
        state = uiState,
        selectedFilter = selectedFilter,
        modifier = modifier,
        onSelectFilter = { filter ->
            selectedFilter = filter
            uiState = uiState.copy(
                categories = uiState.allCategories.filteredBy(filter),
                error = null,
            )
        },
        onRetry = { loadCategories(showLoading = true) },
        onAddCategory = {
            uiState = uiState.copy(error = null)
            sheetMode = CategorySheetMode.Create
        },
        onEditCategory = { category ->
            uiState = uiState.copy(error = null)
            sheetMode = CategorySheetMode.Edit(category)
        },
        onDeleteCategory = { category ->
            deleteTarget = category
        },
    )

    deleteTarget?.let { category ->
        DeleteCategoryDialog(
            category = category,
            isMutating = uiState.isMutating,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                mutate {
                    categoriesRepository.deleteCategory(it, category.id)
                }
            },
        )
    }

    sheetMode?.let { mode ->
        CategoryFormSheet(
            mode = mode,
            categories = uiState.allCategories,
            error = uiState.error,
            isMutating = uiState.isMutating,
            onDismiss = { sheetMode = null },
            onCreate = { form ->
                mutate(closeSheet = true) { activeProfileId ->
                    categoriesRepository.createCategory(activeProfileId, form.toCreateInput())
                }
            },
            onUpdate = { category, form ->
                mutate(closeSheet = true) { activeProfileId ->
                    categoriesRepository.updateCategory(activeProfileId, category.id, form.toUpdateInput())
                }
            },
        )
    }
}

@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    selectedFilter: CategoryTypeFilter,
    onSelectFilter: (CategoryTypeFilter) -> Unit,
    onRetry: () -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (!state.isLoading) {
                FloatingActionButton(onClick = onAddCategory) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.categories_add),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> CategoriesLoading()
                else -> CategoriesList(
                    state = state,
                    selectedFilter = selectedFilter,
                    onSelectFilter = onSelectFilter,
                    onAddCategory = onAddCategory,
                    onEditCategory = onEditCategory,
                    onDeleteCategory = onDeleteCategory,
                )
            }

            state.error?.let { error ->
                ErrorBanner(
                    error = error,
                    onRetry = onRetry,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoriesLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.categories_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CategoriesList(
    state: CategoriesUiState,
    selectedFilter: CategoryTypeFilter,
    onSelectFilter: (CategoryTypeFilter) -> Unit,
    onAddCategory: () -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.categories_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.categories_subtitle),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            CategoryTypeFilters(
                selectedFilter = selectedFilter,
                onSelectFilter = onSelectFilter,
            )
        }

        if (state.categories.isEmpty()) {
            item {
                CategoriesEmptyState(onAddCategory = onAddCategory)
            }
        } else {
            items(
                items = state.categories,
                key = { it.id },
            ) { category ->
                CategoryCard(
                    category = category,
                    enabled = !state.isMutating,
                    onEditCategory = onEditCategory,
                    onDeleteCategory = onDeleteCategory,
                )
            }
        }
    }
}

@Composable
private fun CategoryTypeFilters(
    selectedFilter: CategoryTypeFilter,
    onSelectFilter: (CategoryTypeFilter) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryTypeFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelectFilter(filter) },
                modifier = Modifier.testTag("categories-filter-${filter.name}"),
                label = { Text(text = stringResource(filter.labelResId)) },
            )
        }
    }
}

@Composable
private fun CategoriesEmptyState(onAddCategory: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Label,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.categories_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.categories_empty_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onAddCategory,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.categories_add))
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    enabled: Boolean,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
) {
    val locked = category.isLockedForManagement
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.78f else 1f)
            .testTag("category-card-${category.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryMark(category = category)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = category.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(text = category.type.toLabel()) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (locked) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = stringResource(
                                        if (category.isProtected) {
                                            R.string.categories_protected
                                        } else {
                                            R.string.categories_system
                                        },
                                    ),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { onEditCategory(category) },
                    enabled = enabled && !locked,
                    modifier = Modifier.testTag("category-edit-${category.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.categories_edit),
                    )
                }
                IconButton(
                    onClick = { onDeleteCategory(category) },
                    enabled = enabled && !locked,
                    modifier = Modifier.testTag("category-delete-${category.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.categories_delete),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryMark(category: Category) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(category.color.toColorOrFallback()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category.icon.toCategoryIcon(),
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
private fun ErrorBanner(
    error: CategoriesError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(error.messageResId),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.categories_retry))
            }
        }
    }
}

@Composable
private fun DeleteCategoryDialog(
    category: Category,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Delete, contentDescription = null)
        },
        title = {
            Text(text = stringResource(R.string.categories_delete_title))
        },
        text = {
            Text(text = stringResource(R.string.categories_delete_body))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isMutating,
            ) {
                Text(text = stringResource(R.string.categories_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.categories_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFormSheet(
    mode: CategorySheetMode,
    categories: List<Category>,
    error: CategoriesError?,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (CategoryFormState) -> Unit,
    onUpdate: (Category, CategoryFormState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val category = (mode as? CategorySheetMode.Edit)?.category
    var form by remember(mode, categories.size) {
        mutableStateOf(CategoryFormState.fromCategory(category, categories.nextColor()))
    }
    val duplicateName = categories.any { existing ->
        existing.id != category?.id &&
            existing.name.equals(form.name.trim(), ignoreCase = true)
    }
    val formError = when {
        duplicateName -> CategoriesError.DuplicateName
        form.name.isBlank() -> null
        else -> error
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (category == null) R.string.categories_add else R.string.categories_edit,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.categories_cancel))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryPreview(
                    iconId = form.icon,
                    color = form.color,
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = form.name,
                    onValueChange = {
                        form = form.copy(name = it.take(MAX_CATEGORY_NAME_LENGTH))
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.categories_name)) },
                    placeholder = { Text(stringResource(R.string.categories_name_placeholder)) },
                    singleLine = true,
                )
            }

            Text(
                text = stringResource(R.string.categories_type),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditableCategoryTypes.forEach { type ->
                    FilterChip(
                        selected = form.type == type,
                        onClick = { form = form.copy(type = type) },
                        modifier = Modifier.testTag("categories-form-type-${type.name}"),
                        label = { Text(type.toLabel()) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.categories_icon),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryIconChoices.forEach { choice ->
                    FilterChip(
                        selected = form.icon == choice.id,
                        onClick = { form = form.copy(icon = choice.id) },
                        label = { Text(stringResource(choice.labelResId)) },
                        leadingIcon = {
                            Icon(
                                imageVector = choice.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            Text(
                text = stringResource(R.string.categories_color),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CategoryColorSwatches.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = form.color == color,
                        onSelect = { form = form.copy(color = color) },
                    )
                }
            }

            formError?.let {
                FormErrorMessage(error = it)
            }

            Button(
                onClick = {
                    if (category == null) {
                        onCreate(form)
                    } else {
                        onUpdate(category, form)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isMutating && form.name.trim().isNotEmpty() && !duplicateName,
            ) {
                Icon(
                    imageVector = if (category == null) Icons.Filled.Add else Icons.Filled.Save,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (category == null) R.string.categories_create else R.string.categories_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CategoryPreview(
    iconId: String,
    color: String,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(color.toColorOrFallback()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconId.toCategoryIcon(),
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
private fun ColorSwatch(
    color: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colorLabel = stringResource(R.string.categories_color)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color.toColorOrFallback())
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onSelect)
            .semantics {
                contentDescription = "$colorLabel $color"
                this.selected = selected
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun FormErrorMessage(error: CategoriesError) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = stringResource(error.messageResId),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

data class CategoriesUiState(
    val isLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
    val allCategories: List<Category> = categories,
    val isMutating: Boolean = false,
    val error: CategoriesError? = null,
)

enum class CategoryTypeFilter(
    @StringRes val labelResId: Int,
    val type: CategoryType?,
) {
    All(R.string.categories_all, null),
    Expense(R.string.categories_type_expense, CategoryType.Expense),
    Income(R.string.categories_type_income, CategoryType.Income),
    Both(R.string.categories_type_both, CategoryType.Both),
}

enum class CategoriesError(@StringRes val messageResId: Int) {
    Generic(R.string.categories_error_generic),
    EmptyName(R.string.categories_error_empty_name),
    DuplicateName(R.string.categories_error_duplicate_name),
    Protected(R.string.categories_error_protected),
    NotFound(R.string.categories_error_not_found),
}

private sealed interface CategorySheetMode {
    data object Create : CategorySheetMode
    data class Edit(val category: Category) : CategorySheetMode
}

data class CategoryFormState(
    val name: String,
    val icon: String,
    val type: CategoryType,
    val color: String,
) {
    fun toCreateInput(): CreateCategoryInput {
        return CreateCategoryInput(
            name = name.trim(),
            icon = icon,
            type = type,
            color = color,
        )
    }

    fun toUpdateInput(): UpdateCategoryInput {
        return UpdateCategoryInput(
            name = name.trim(),
            icon = icon,
            type = type,
            color = color,
        )
    }

    companion object {
        fun fromCategory(category: Category?, fallbackColor: String): CategoryFormState {
            return CategoryFormState(
                name = category?.name.orEmpty(),
                icon = category?.icon ?: "tag",
                type = category?.type?.takeIf { it in EditableCategoryTypes } ?: CategoryType.Both,
                color = category?.color ?: fallbackColor,
            )
        }
    }
}

private fun Throwable.toCategoriesError(): CategoriesError {
    return when (this) {
        is CategoryNameEmptyException -> CategoriesError.EmptyName
        is CategoryProtectedException -> CategoriesError.Protected
        is CategoryNotFoundException -> CategoriesError.NotFound
        else -> CategoriesError.Generic
    }
}

@Composable
private fun CategoryType.toLabel(): String {
    val resId = when (this) {
        CategoryType.Expense -> R.string.categories_type_expense
        CategoryType.Income -> R.string.categories_type_income
        CategoryType.Both -> R.string.categories_type_both
        CategoryType.Savings -> R.string.categories_type_savings
        CategoryType.Transfer -> R.string.categories_type_transfer
        CategoryType.Adjustment -> R.string.categories_type_adjustment
    }
    return stringResource(resId)
}

private val Category.isLockedForManagement: Boolean
    get() = isProtected || type !in EditableCategoryTypes

private fun List<Category>.filteredBy(filter: CategoryTypeFilter): List<Category> {
    val type = filter.type ?: return this
    return filter { category ->
        category.type == type ||
            (type in setOf(CategoryType.Expense, CategoryType.Income) && category.type == CategoryType.Both)
    }
}

private fun String.toCategoryIcon(): ImageVector {
    return when (this) {
        "house", "home", "bed" -> Icons.Filled.Home
        "shopping-bag", "basket" -> Icons.Filled.ShoppingCart
        "briefcase", "salary", "work" -> Icons.Filled.Work
        "money", "currency-dollar", "bank", "wallet" -> Icons.Filled.AccountBalanceWallet
        "piggy-bank", "savings" -> Icons.Filled.AccountBalanceWallet
        "arrows-left-right", "transfer" -> Icons.Filled.SwapHoriz
        "star" -> Icons.Filled.Star
        else -> Icons.AutoMirrored.Filled.Label
    }
}

private fun String.toColorOrFallback(): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }
        .getOrDefault(Color(0xFF6366F1))
}

private fun List<Category>.nextColor(): String {
    return CategoryColorSwatches[size % CategoryColorSwatches.size]
}

private data class CategoryIconChoice(
    val id: String,
    @StringRes val labelResId: Int,
    val icon: ImageVector,
)

private const val MAX_CATEGORY_NAME_LENGTH = 30

private val EditableCategoryTypes = setOf(
    CategoryType.Expense,
    CategoryType.Income,
    CategoryType.Both,
)

private val CategoryColorSwatches = listOf(
    "#6366f1",
    "#8b5cf6",
    "#ec4899",
    "#ef4444",
    "#f97316",
    "#eab308",
    "#22c55e",
    "#10b981",
    "#14b8a6",
    "#06b6d4",
    "#3b82f6",
    "#64748b",
)

private val CategoryIconChoices = listOf(
    CategoryIconChoice("fork-knife", R.string.categories_icon_food, Icons.AutoMirrored.Filled.Label),
    CategoryIconChoice("bus", R.string.categories_icon_transport, Icons.Filled.SwapHoriz),
    CategoryIconChoice("house", R.string.categories_icon_home, Icons.Filled.Home),
    CategoryIconChoice("shopping-bag", R.string.categories_icon_shopping, Icons.Filled.ShoppingCart),
    CategoryIconChoice("briefcase", R.string.categories_icon_work, Icons.Filled.Work),
    CategoryIconChoice("money", R.string.categories_icon_money, Icons.Filled.AccountBalanceWallet),
    CategoryIconChoice("piggy-bank", R.string.categories_icon_savings, Icons.Filled.AccountBalanceWallet),
    CategoryIconChoice("arrows-left-right", R.string.categories_icon_transfer, Icons.Filled.SwapHoriz),
    CategoryIconChoice("tag", R.string.categories_icon_other, Icons.AutoMirrored.Filled.Label),
)

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun CategoriesScreenPreview() {
    MoneyTrackerTheme {
        CategoriesScreen(
            state = CategoriesUiState(
                categories = categoriesFixture,
            ),
            selectedFilter = CategoryTypeFilter.All,
            onSelectFilter = {},
            onRetry = {},
            onAddCategory = {},
            onEditCategory = {},
            onDeleteCategory = {},
        )
    }
}

private val categoriesFixture = listOf(
    Category(
        id = 1,
        profileId = 1,
        name = "Food",
        icon = "fork-knife",
        type = CategoryType.Expense,
        color = "#f97316",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
    Category(
        id = 2,
        profileId = 1,
        name = "Salary",
        icon = "briefcase",
        type = CategoryType.Income,
        color = "#10b981",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
    Category(
        id = 3,
        profileId = 1,
        name = "Transfer",
        icon = "arrows-left-right",
        type = CategoryType.Transfer,
        color = "#6366f1",
        isProtected = true,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
)
