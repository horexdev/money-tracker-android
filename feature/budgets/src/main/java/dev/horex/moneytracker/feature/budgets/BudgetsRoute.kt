package dev.horex.moneytracker.feature.budgets

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.horex.moneytracker.core.budgets.Budget
import dev.horex.moneytracker.core.budgets.BudgetAlreadyExistsException
import dev.horex.moneytracker.core.budgets.BudgetCategoryNotFoundException
import dev.horex.moneytracker.core.budgets.BudgetCategoryTypeException
import dev.horex.moneytracker.core.budgets.BudgetNotFoundException
import dev.horex.moneytracker.core.budgets.BudgetPeriod
import dev.horex.moneytracker.core.budgets.BudgetTransaction
import dev.horex.moneytracker.core.budgets.BudgetsRepository
import dev.horex.moneytracker.core.budgets.CreateBudgetInput
import dev.horex.moneytracker.core.budgets.DEFAULT_BUDGET_NOTIFY_AT_PERCENT
import dev.horex.moneytracker.core.budgets.InvalidBudgetAmountException
import dev.horex.moneytracker.core.budgets.InvalidBudgetCurrencyException
import dev.horex.moneytracker.core.budgets.InvalidBudgetNotifyPercentException
import dev.horex.moneytracker.core.budgets.InvalidBudgetPeriodException
import dev.horex.moneytracker.core.budgets.UpdateBudgetInput
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.categories.Category
import dev.horex.moneytracker.core.categories.CategorySortOrder
import dev.horex.moneytracker.core.categories.CategoryType
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.money.InvalidMoneyAmountException
import dev.horex.moneytracker.core.money.MoneyOverflowException
import dev.horex.moneytracker.core.money.MoneyParser
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BudgetsRoute(
    localProfileBootstrapper: LocalProfileBootstrapper,
    budgetsRepository: BudgetsRepository,
    categoriesRepository: CategoriesRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Long?>(null) }
    var uiState by remember { mutableStateOf(BudgetsUiState(isLoading = true)) }
    var sheetMode by remember { mutableStateOf<BudgetSheetMode?>(null) }
    var deleteTarget by remember { mutableStateOf<Budget?>(null) }
    var transactionsSheet by remember { mutableStateOf<BudgetTransactionsSheetState?>(null) }

    suspend fun loadState(activeProfileId: Long): BudgetsUiState {
        val categories = categoriesRepository.listAllCategories(
            profileId = activeProfileId,
            sortOrder = CategorySortOrder.NameAsc,
        ).filter { it.supportsBudgetExpenses() }
        return BudgetsUiState(
            budgets = budgetsRepository.listBudgets(activeProfileId),
            categories = categories,
        )
    }

    fun loadBudgets(showLoading: Boolean) {
        scope.launch {
            if (showLoading) {
                uiState = uiState.copy(isLoading = true, error = null)
            }
            try {
                val profile = localProfileBootstrapper.ensureActiveProfile()
                profileId = profile.id
                uiState = loadState(profile.id)
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    isMutating = false,
                    error = error.toBudgetsError(),
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
                uiState = loadState(activeProfileId)
                if (closeSheet) {
                    sheetMode = null
                }
                deleteTarget = null
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    isMutating = false,
                    error = error.toBudgetsError(),
                )
            }
        }
    }

    fun loadTransactions(budget: Budget) {
        scope.launch {
            val activeProfileId = profileId ?: localProfileBootstrapper.ensureActiveProfile().id
            profileId = activeProfileId
            transactionsSheet = BudgetTransactionsSheetState(
                budget = budget,
                isLoading = true,
            )
            try {
                transactionsSheet = BudgetTransactionsSheetState(
                    budget = budget,
                    transactions = budgetsRepository.listBudgetTransactions(activeProfileId, budget.id),
                )
            } catch (error: Throwable) {
                transactionsSheet = BudgetTransactionsSheetState(
                    budget = budget,
                    error = error.toBudgetsError(),
                )
            }
        }
    }

    LaunchedEffect(localProfileBootstrapper, budgetsRepository, categoriesRepository) {
        loadBudgets(showLoading = true)
    }

    BudgetsScreen(
        state = uiState,
        modifier = modifier,
        onRetry = { loadBudgets(showLoading = true) },
        onAddBudget = {
            uiState = uiState.copy(error = null)
            sheetMode = BudgetSheetMode.Create
        },
        onEditBudget = { budget ->
            uiState = uiState.copy(error = null)
            sheetMode = BudgetSheetMode.Edit(budget)
        },
        onDeleteBudget = { budget ->
            deleteTarget = budget
        },
        onToggleNotifications = { budget, enabled ->
            mutate {
                budgetsRepository.updateBudget(
                    profileId = it,
                    budgetId = budget.id,
                    input = UpdateBudgetInput(notificationsEnabled = enabled),
                )
            }
        },
        onOpenTransactions = ::loadTransactions,
    )

    deleteTarget?.let { budget ->
        DeleteBudgetDialog(
            budget = budget,
            isMutating = uiState.isMutating,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                mutate {
                    budgetsRepository.deleteBudget(it, budget.id)
                }
            },
        )
    }

    sheetMode?.let { mode ->
        BudgetFormSheet(
            mode = mode,
            categories = uiState.categories,
            error = uiState.error,
            isMutating = uiState.isMutating,
            onDismiss = { sheetMode = null },
            onCreate = { form ->
                mutate(closeSheet = true) { activeProfileId ->
                    budgetsRepository.createBudget(activeProfileId, form.toCreateInput())
                }
            },
            onUpdate = { budget, form ->
                mutate(closeSheet = true) { activeProfileId ->
                    budgetsRepository.updateBudget(activeProfileId, budget.id, form.toUpdateInput())
                }
            },
        )
    }

    transactionsSheet?.let { sheetState ->
        BudgetTransactionsSheet(
            state = sheetState,
            onDismiss = { transactionsSheet = null },
        )
    }
}

@Composable
fun BudgetsScreen(
    state: BudgetsUiState,
    onRetry: () -> Unit,
    onAddBudget: () -> Unit,
    onEditBudget: (Budget) -> Unit,
    onDeleteBudget: (Budget) -> Unit,
    onToggleNotifications: (Budget, Boolean) -> Unit,
    onOpenTransactions: (Budget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (!state.isLoading && state.categories.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onAddBudget,
                    modifier = Modifier.testTag("budgets-add"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.budgets_add),
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
                state.isLoading -> BudgetsLoading()
                else -> BudgetsList(
                    state = state,
                    onAddBudget = onAddBudget,
                    onEditBudget = onEditBudget,
                    onDeleteBudget = onDeleteBudget,
                    onToggleNotifications = onToggleNotifications,
                    onOpenTransactions = onOpenTransactions,
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
private fun BudgetsLoading() {
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
            text = stringResource(R.string.budgets_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BudgetsList(
    state: BudgetsUiState,
    onAddBudget: () -> Unit,
    onEditBudget: (Budget) -> Unit,
    onDeleteBudget: (Budget) -> Unit,
    onToggleNotifications: (Budget, Boolean) -> Unit,
    onOpenTransactions: (Budget) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("budgets-list"),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BudgetsHeader()
        }

        when {
            state.categories.isEmpty() && state.budgets.isEmpty() -> item {
                BudgetsNoCategoriesState()
            }

            state.budgets.isEmpty() -> item {
                BudgetsEmptyState(onAddBudget = onAddBudget)
            }

            else -> items(
                items = state.budgets,
                key = { it.id },
            ) { budget ->
                BudgetCard(
                    budget = budget,
                    enabled = !state.isMutating,
                    onEditBudget = onEditBudget,
                    onDeleteBudget = onDeleteBudget,
                    onToggleNotifications = onToggleNotifications,
                    onOpenTransactions = onOpenTransactions,
                )
            }
        }
    }
}

@Composable
private fun BudgetsHeader() {
    Text(
        text = stringResource(R.string.budgets_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.budgets_subtitle),
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BudgetsEmptyState(onAddBudget: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Savings,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.budgets_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.budgets_empty_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onAddBudget,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.budgets_add))
        }
    }
}

@Composable
private fun BudgetsNoCategoriesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.budgets_no_categories_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.budgets_no_categories_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetCard(
    budget: Budget,
    enabled: Boolean,
    onEditBudget: (Budget) -> Unit,
    onDeleteBudget: (Budget) -> Unit,
    onToggleNotifications: (Budget, Boolean) -> Unit,
    onOpenTransactions: (Budget) -> Unit,
) {
    val progress = budget.progressFraction()
    val progressPercent = budget.usagePercent.roundToInt().coerceAtLeast(0)
    val overageCents = (budget.spentCents - budget.limitCents).coerceAtLeast(0)
    val statusColor = if (budget.isOverLimit) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("budget-card-${budget.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (budget.isOverLimit) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryMark(
                    categoryName = budget.categoryName,
                    categoryColor = budget.categoryColor,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = budget.categoryName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (budget.isOverLimit) {
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.budgets_over_limit_chip)) },
                                modifier = Modifier.padding(start = 8.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = stringResource(budget.period.labelResId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(
                        onClick = { onEditBudget(budget) },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.budgets_edit),
                        )
                    }
                    IconButton(
                        onClick = { onDeleteBudget(budget) },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.budgets_delete),
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(MaterialTheme.shapes.small),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.budgets_spent_of_limit,
                        budget.spentCents.formatMoney(budget.currencyCode),
                        budget.limitCents.formatMoney(budget.currencyCode),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.budgets_progress_percent, progressPercent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                )
            }

            Text(
                text = if (budget.isOverLimit) {
                    stringResource(
                        R.string.budgets_over_limit,
                        overageCents.formatMoney(budget.currencyCode),
                    )
                } else {
                    stringResource(
                        R.string.budgets_remaining,
                        (budget.limitCents - budget.spentCents).coerceAtLeast(0).formatMoney(budget.currencyCode),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (budget.isOverLimit) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) {
                        onToggleNotifications(budget, !budget.notificationsEnabled)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.budgets_notifications),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (budget.notificationsEnabled) {
                            stringResource(R.string.budgets_notifications_enabled)
                        } else {
                            stringResource(R.string.budgets_notifications_disabled)
                        } + " - ${budget.notifyAtPercent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = budget.notificationsEnabled,
                    onCheckedChange = { onToggleNotifications(budget, it) },
                    enabled = enabled,
                    modifier = Modifier.testTag("budget-notifications-${budget.id}"),
                )
            }

            Button(
                onClick = { onOpenTransactions(budget) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            ) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.budgets_transactions))
            }
        }
    }
}

@Composable
private fun CategoryMark(
    categoryName: String,
    categoryColor: String,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(categoryColor.toColorOrFallback(MaterialTheme.colorScheme.primary)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = categoryName.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ErrorBanner(
    error: BudgetsError,
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
                Text(text = stringResource(R.string.budgets_retry))
            }
        }
    }
}

@Composable
private fun DeleteBudgetDialog(
    budget: Budget,
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
            Text(text = stringResource(R.string.budgets_delete_title))
        },
        text = {
            Text(text = stringResource(R.string.budgets_delete_body))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isMutating,
            ) {
                Text(text = stringResource(R.string.budgets_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.budgets_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetFormSheet(
    mode: BudgetSheetMode,
    categories: List<Category>,
    error: BudgetsError?,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (BudgetFormState) -> Unit,
    onUpdate: (Budget, BudgetFormState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val budget = (mode as? BudgetSheetMode.Edit)?.budget
    var form by remember(mode, categories) {
        mutableStateOf(BudgetFormState.fromBudget(budget, categories))
    }
    val canSave = !isMutating &&
        form.limit.isNotBlank() &&
        form.categoryId > 0 &&
        form.currencyCode.length == CURRENCY_CODE_LENGTH &&
        (form.notifyAtPercent.toIntOrNull() ?: 0) > 0

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
                        if (budget == null) R.string.budgets_add else R.string.budgets_edit,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.budgets_cancel))
                }
            }

            Text(
                text = stringResource(R.string.budgets_category),
                style = MaterialTheme.typography.labelLarge,
            )
            if (budget == null) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = form.categoryId == category.id,
                            onClick = { form = form.copy(categoryId = category.id) },
                            label = {
                                Text(
                                    text = category.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.testTag("budget-category-${category.id}"),
                        )
                    }
                }
            } else {
                AssistChip(
                    onClick = {},
                    label = { Text(budget.categoryName) },
                )
            }

            OutlinedTextField(
                value = form.limit,
                onValueChange = {
                    form = form.copy(limit = MoneyParser.sanitizeAmountInput(it))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget-limit"),
                label = { Text(stringResource(R.string.budgets_limit)) },
                suffix = { Text(form.currencyCode.ifBlank { DEFAULT_CURRENCY }) },
                singleLine = true,
            )

            if (budget == null) {
                OutlinedTextField(
                    value = form.currencyCode,
                    onValueChange = {
                        form = form.copy(
                            currencyCode = it.uppercase(Locale.US)
                                .filter(Char::isLetter)
                                .take(CURRENCY_CODE_LENGTH),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget-currency"),
                    label = { Text(stringResource(R.string.budgets_currency)) },
                    singleLine = true,
                )
            } else {
                Text(
                    text = "${stringResource(R.string.budgets_currency)}: ${budget.currencyCode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.budgets_period),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BudgetPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = form.period == period,
                        onClick = { form = form.copy(period = period) },
                        label = { Text(stringResource(period.labelResId)) },
                        modifier = Modifier.testTag("budget-period-${period.storageValue}"),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { form = form.copy(notificationsEnabled = !form.notificationsEnabled) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.budgets_notifications),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (form.notificationsEnabled) {
                            stringResource(R.string.budgets_notifications_enabled)
                        } else {
                            stringResource(R.string.budgets_notifications_disabled)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = form.notificationsEnabled,
                    onCheckedChange = { form = form.copy(notificationsEnabled = it) },
                    modifier = Modifier.testTag("budget-form-notifications"),
                )
            }

            OutlinedTextField(
                value = form.notifyAtPercent,
                onValueChange = { value ->
                    form = form.copy(notifyAtPercent = value.filter(Char::isDigit).take(MAX_PERCENT_LENGTH))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget-notify-percent"),
                label = { Text(stringResource(R.string.budgets_notify_at)) },
                suffix = { Text("%") },
                singleLine = true,
            )

            error?.let {
                FormErrorMessage(error = it)
            }

            Button(
                onClick = {
                    if (budget == null) {
                        onCreate(form)
                    } else {
                        onUpdate(budget, form)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
            ) {
                Icon(
                    imageVector = if (budget == null) Icons.Filled.Add else Icons.Filled.Save,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (budget == null) R.string.budgets_create else R.string.budgets_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FormErrorMessage(error: BudgetsError) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetTransactionsSheet(
    state: BudgetTransactionsSheetState,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.budgets_transactions_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.budget.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.budgets_cancel))
                }
            }

            when {
                state.isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null -> FormErrorMessage(error = state.error)
                state.transactions.isEmpty() -> Text(
                    text = stringResource(R.string.budgets_transactions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.transactions,
                        key = { it.id },
                    ) { transaction ->
                        BudgetTransactionRow(transaction = transaction)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetTransactionRow(transaction: BudgetTransaction) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryMark(
                categoryName = transaction.categoryName,
                categoryColor = transaction.categoryColor,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = transaction.note.takeIf { it.isNotBlank() } ?: transaction.categoryName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.budgets_transaction_meta,
                        transaction.accountName,
                        transaction.snapshotDate,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "-${transaction.amountCents.formatMoney(transaction.currencyCode)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

data class BudgetsUiState(
    val isLoading: Boolean = false,
    val budgets: List<Budget> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isMutating: Boolean = false,
    val error: BudgetsError? = null,
)

enum class BudgetsError(@StringRes val messageResId: Int) {
    Generic(R.string.budgets_error_generic),
    Amount(R.string.budgets_error_amount),
    Currency(R.string.budgets_error_currency),
    NotifyPercent(R.string.budgets_error_notify_percent),
    Duplicate(R.string.budgets_error_duplicate),
    Category(R.string.budgets_error_category),
    NotFound(R.string.budgets_error_not_found),
}

private sealed interface BudgetSheetMode {
    data object Create : BudgetSheetMode
    data class Edit(val budget: Budget) : BudgetSheetMode
}

data class BudgetFormState(
    val categoryId: Long,
    val limit: String,
    val period: BudgetPeriod,
    val currencyCode: String,
    val notifyAtPercent: String,
    val notificationsEnabled: Boolean,
) {
    fun toCreateInput(): CreateBudgetInput {
        return CreateBudgetInput(
            categoryId = categoryId,
            limitCents = MoneyParser.parsePositiveCents(limit),
            period = period,
            currencyCode = currencyCode,
            notifyAtPercent = parseNotifyPercent(),
            notificationsEnabled = notificationsEnabled,
        )
    }

    fun toUpdateInput(): UpdateBudgetInput {
        return UpdateBudgetInput(
            limitCents = MoneyParser.parsePositiveCents(limit),
            period = period,
            notifyAtPercent = parseNotifyPercent(),
            notificationsEnabled = notificationsEnabled,
        )
    }

    private fun parseNotifyPercent(): Int {
        return notifyAtPercent.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: throw InvalidBudgetNotifyPercentException()
    }

    companion object {
        fun fromBudget(
            budget: Budget?,
            categories: List<Category>,
        ): BudgetFormState {
            return BudgetFormState(
                categoryId = budget?.categoryId ?: categories.firstOrNull()?.id ?: 0L,
                limit = budget?.limitCents?.let(MoneyParser::formatPlainCents).orEmpty(),
                period = budget?.period ?: BudgetPeriod.Monthly,
                currencyCode = budget?.currencyCode ?: DEFAULT_CURRENCY,
                notifyAtPercent = (budget?.notifyAtPercent ?: DEFAULT_BUDGET_NOTIFY_AT_PERCENT).toString(),
                notificationsEnabled = budget?.notificationsEnabled ?: true,
            )
        }
    }
}

data class BudgetTransactionsSheetState(
    val budget: Budget,
    val isLoading: Boolean = false,
    val transactions: List<BudgetTransaction> = emptyList(),
    val error: BudgetsError? = null,
)

private fun Throwable.toBudgetsError(): BudgetsError {
    return when (this) {
        is InvalidMoneyAmountException,
        is MoneyOverflowException,
        is InvalidBudgetAmountException -> BudgetsError.Amount

        is InvalidBudgetCurrencyException -> BudgetsError.Currency
        is InvalidBudgetNotifyPercentException -> BudgetsError.NotifyPercent
        is InvalidBudgetPeriodException -> BudgetsError.Generic
        is BudgetAlreadyExistsException -> BudgetsError.Duplicate
        is BudgetCategoryNotFoundException,
        is BudgetCategoryTypeException -> BudgetsError.Category

        is BudgetNotFoundException -> BudgetsError.NotFound
        else -> BudgetsError.Generic
    }
}

private val BudgetPeriod.labelResId: Int
    @StringRes
    get() = when (this) {
        BudgetPeriod.Weekly -> R.string.budgets_period_weekly
        BudgetPeriod.Monthly -> R.string.budgets_period_monthly
    }

private fun Category.supportsBudgetExpenses(): Boolean {
    return type == CategoryType.Expense || type == CategoryType.Both
}

private fun Budget.progressFraction(): Float {
    if (limitCents <= 0L) {
        return 0f
    }
    return (spentCents.toFloat() / limitCents.toFloat()).coerceIn(0f, 1f)
}

private fun Long.formatMoney(currencyCode: String): String {
    return "${MoneyParser.formatPlainCents(abs(this))} $currencyCode"
}

private fun String.toColorOrFallback(fallback: Color): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }
        .getOrDefault(fallback)
}

private const val DEFAULT_CURRENCY = "USD"
private const val CURRENCY_CODE_LENGTH = 3
private const val MAX_PERCENT_LENGTH = 3

@Preview(showBackground = true, widthDp = 320, heightDp = 760)
@Composable
private fun BudgetsScreenPreview() {
    MoneyTrackerTheme {
        BudgetsScreen(
            state = BudgetsUiState(
                budgets = listOf(
                    budgetFixture(
                        id = 1,
                        categoryName = "Groceries and household supplies",
                        spentCents = 42_500,
                        limitCents = 60_000,
                        notificationsEnabled = true,
                    ),
                    budgetFixture(
                        id = 2,
                        categoryName = "Restaurants",
                        spentCents = 18_250,
                        limitCents = 15_000,
                        notificationsEnabled = false,
                    ),
                ),
                categories = categoriesFixture,
            ),
            onRetry = {},
            onAddBudget = {},
            onEditBudget = {},
            onDeleteBudget = {},
            onToggleNotifications = { _, _ -> },
            onOpenTransactions = {},
        )
    }
}

private fun budgetFixture(
    id: Long,
    categoryName: String,
    spentCents: Long,
    limitCents: Long,
    notificationsEnabled: Boolean,
): Budget {
    return Budget(
        id = id,
        profileId = 1,
        categoryId = id,
        categoryName = categoryName,
        categoryIcon = "tag",
        categoryColor = if (id == 1L) "#F97316" else "#EF4444",
        limitCents = limitCents,
        spentCents = spentCents,
        period = BudgetPeriod.Monthly,
        currencyCode = "USD",
        notifyAtPercent = 80,
        notificationsEnabled = notificationsEnabled,
        lastNotifiedPercent = 0,
        lastNotifiedAtEpochMillis = null,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}

private val categoriesFixture = listOf(
    Category(
        id = 1,
        profileId = 1,
        name = "Groceries",
        icon = "shopping-bag",
        type = CategoryType.Expense,
        color = "#F97316",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
    Category(
        id = 2,
        profileId = 1,
        name = "Restaurants",
        icon = "fork-knife",
        type = CategoryType.Expense,
        color = "#EF4444",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
)
