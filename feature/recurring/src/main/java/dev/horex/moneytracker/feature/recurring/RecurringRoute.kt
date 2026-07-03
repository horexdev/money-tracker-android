package dev.horex.moneytracker.feature.recurring

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.horex.moneytracker.core.accounts.Account
import dev.horex.moneytracker.core.accounts.AccountType
import dev.horex.moneytracker.core.accounts.AccountsRepository
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.categories.Category
import dev.horex.moneytracker.core.categories.CategorySortOrder
import dev.horex.moneytracker.core.categories.CategoryType
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.money.InvalidMoneyAmountException
import dev.horex.moneytracker.core.money.MoneyOverflowException
import dev.horex.moneytracker.core.money.MoneyParser
import dev.horex.moneytracker.core.recurring.CreateRecurringTransactionInput
import dev.horex.moneytracker.core.recurring.InvalidRecurringAmountException
import dev.horex.moneytracker.core.recurring.InvalidRecurringNextRunException
import dev.horex.moneytracker.core.recurring.RecurringAccountNotFoundException
import dev.horex.moneytracker.core.recurring.RecurringCategoryNotFoundException
import dev.horex.moneytracker.core.recurring.RecurringCategoryTypeException
import dev.horex.moneytracker.core.recurring.RecurringFrequency
import dev.horex.moneytracker.core.recurring.RecurringTransaction
import dev.horex.moneytracker.core.recurring.RecurringTransactionNotFoundException
import dev.horex.moneytracker.core.recurring.RecurringTransactionsRepository
import dev.horex.moneytracker.core.recurring.UpdateRecurringTransactionInput
import dev.horex.moneytracker.core.transactions.TransactionType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs

@Composable
fun RecurringRoute(
    localProfileBootstrapper: LocalProfileBootstrapper,
    recurringRepository: RecurringTransactionsRepository,
    accountsRepository: AccountsRepository,
    categoriesRepository: CategoriesRepository,
    modifier: Modifier = Modifier,
    todayProvider: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) },
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Long?>(null) }
    var uiState by remember { mutableStateOf(RecurringUiState(isLoading = true)) }
    var sheetMode by remember { mutableStateOf<RecurringSheetMode?>(null) }
    var deleteTarget by remember { mutableStateOf<RecurringTransaction?>(null) }

    suspend fun loadState(activeProfileId: Long): RecurringUiState {
        return RecurringUiState(
            recurring = recurringRepository.listRecurring(activeProfileId),
            accounts = accountsRepository.listAccounts(activeProfileId),
            categories = categoriesRepository.listCategories(
                profileId = activeProfileId,
                sortOrder = CategorySortOrder.Frequency,
            ),
        )
    }

    fun loadRecurring(showLoading: Boolean) {
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
                    error = error.toRecurringError(),
                )
            }
        }
    }

    fun mutate(
        closeSheet: Boolean = false,
        block: suspend (Long) -> Unit,
    ) {
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
                    error = error.toRecurringError(),
                )
            }
        }
    }

    fun submitForm(
        mode: RecurringSheetMode,
        form: RecurringFormState,
    ) {
        val validationError = form.validationError(
            accounts = uiState.accounts,
            categories = uiState.categories.compatibleWith(form.type),
        )
        if (validationError != null) {
            uiState = uiState.copy(error = validationError)
            return
        }

        mutate(closeSheet = true) { activeProfileId ->
            when (mode) {
                RecurringSheetMode.Create -> {
                    recurringRepository.createRecurring(activeProfileId, form.toCreateInput())
                }

                is RecurringSheetMode.Edit -> {
                    recurringRepository.updateRecurring(
                        profileId = activeProfileId,
                        recurringId = mode.recurring.id,
                        input = form.toUpdateInput(),
                    )
                }
            }
        }
    }

    LaunchedEffect(localProfileBootstrapper, recurringRepository, accountsRepository, categoriesRepository) {
        loadRecurring(showLoading = true)
    }

    RecurringScreen(
        state = uiState,
        modifier = modifier,
        onRetry = { loadRecurring(showLoading = true) },
        onAddRecurring = {
            uiState = uiState.copy(error = null)
            sheetMode = RecurringSheetMode.Create
        },
        onEditRecurring = { recurring ->
            uiState = uiState.copy(error = null)
            sheetMode = RecurringSheetMode.Edit(recurring)
        },
        onToggleRecurring = { recurring ->
            mutate {
                recurringRepository.toggleRecurringActive(it, recurring.id)
            }
        },
        onDeleteRecurring = { recurring ->
            deleteTarget = recurring
        },
    )

    deleteTarget?.let { recurring ->
        DeleteRecurringDialog(
            recurring = recurring,
            isMutating = uiState.isMutating,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                mutate {
                    recurringRepository.deleteRecurring(it, recurring.id)
                }
            },
        )
    }

    sheetMode?.let { mode ->
        RecurringFormSheet(
            mode = mode,
            accounts = uiState.accounts,
            categories = uiState.categories,
            error = uiState.error,
            isMutating = uiState.isMutating,
            todayProvider = todayProvider,
            onDismiss = { sheetMode = null },
            onFormChanged = { uiState = uiState.copy(error = null) },
            onSubmit = { form -> submitForm(mode, form) },
        )
    }
}

@Composable
fun RecurringScreen(
    state: RecurringUiState,
    onRetry: () -> Unit,
    onAddRecurring: () -> Unit,
    onEditRecurring: (RecurringTransaction) -> Unit,
    onToggleRecurring: (RecurringTransaction) -> Unit,
    onDeleteRecurring: (RecurringTransaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (!state.isLoading && state.canCreateRecurring) {
                FloatingActionButton(
                    onClick = onAddRecurring,
                    modifier = Modifier.testTag("recurring-add"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.recurring_add),
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
                state.isLoading -> RecurringLoading()
                else -> RecurringList(
                    state = state,
                    onAddRecurring = onAddRecurring,
                    onEditRecurring = onEditRecurring,
                    onToggleRecurring = onToggleRecurring,
                    onDeleteRecurring = onDeleteRecurring,
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
private fun RecurringLoading() {
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
            text = stringResource(R.string.recurring_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RecurringList(
    state: RecurringUiState,
    onAddRecurring: () -> Unit,
    onEditRecurring: (RecurringTransaction) -> Unit,
    onToggleRecurring: (RecurringTransaction) -> Unit,
    onDeleteRecurring: (RecurringTransaction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recurring-list"),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RecurringHeader()
        }

        when {
            !state.canCreateRecurring && state.recurring.isEmpty() -> item {
                RecurringNoSetupState()
            }

            state.recurring.isEmpty() -> item {
                RecurringEmptyState(onAddRecurring = onAddRecurring)
            }

            else -> items(
                items = state.recurring,
                key = { it.id },
            ) { recurring ->
                RecurringCard(
                    recurring = recurring,
                    enabled = !state.isMutating,
                    onEditRecurring = onEditRecurring,
                    onToggleRecurring = onToggleRecurring,
                    onDeleteRecurring = onDeleteRecurring,
                )
            }
        }
    }
}

@Composable
private fun RecurringHeader() {
    Text(
        text = stringResource(R.string.recurring_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.recurring_subtitle),
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RecurringEmptyState(onAddRecurring: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CalendarToday,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.recurring_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.recurring_empty_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onAddRecurring,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.recurring_add))
        }
    }
}

@Composable
private fun RecurringNoSetupState() {
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
            text = stringResource(R.string.recurring_no_setup_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.recurring_no_setup_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecurringCard(
    recurring: RecurringTransaction,
    enabled: Boolean,
    onEditRecurring: (RecurringTransaction) -> Unit,
    onToggleRecurring: (RecurringTransaction) -> Unit,
    onDeleteRecurring: (RecurringTransaction) -> Unit,
) {
    val nextRunDate = recurring.nextRunAtEpochMillis.toDisplayDate()
    val amountColor = if (recurring.type == TransactionType.Income) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recurring-card-${recurring.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (recurring.isActive) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (recurring.isActive) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryMark(
                    categoryName = recurring.categoryName,
                    categoryIcon = recurring.categoryIcon,
                    categoryColor = recurring.categoryColor,
                    fallbackColor = amountColor,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = recurring.note.ifBlank { recurring.categoryName },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!recurring.isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.recurring_inactive)) },
                            )
                        }
                    }
                    Text(
                        text = "${recurring.categoryName} - ${recurring.type.toLabel()}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = recurring.amountLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(recurring.frequency.labelResId)) },
                )
                AssistChip(
                    onClick = {},
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = {
                        Text(stringResource(R.string.recurring_next_run_value, nextRunDate))
                    },
                )
            }

            Text(
                text = if (recurring.isActive) {
                    stringResource(R.string.recurring_active_state, nextRunDate)
                } else {
                    stringResource(R.string.recurring_inactive_state)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onToggleRecurring(recurring) },
                    enabled = enabled,
                    modifier = Modifier.testTag("recurring-toggle-${recurring.id}"),
                ) {
                    Icon(
                        imageVector = if (recurring.isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (recurring.isActive) {
                                R.string.recurring_pause
                            } else {
                                R.string.recurring_resume
                            },
                        ),
                    )
                }
                IconButton(
                    onClick = { onEditRecurring(recurring) },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.recurring_edit),
                    )
                }
                IconButton(
                    onClick = { onDeleteRecurring(recurring) },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.recurring_delete),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryMark(
    categoryName: String,
    categoryIcon: String,
    categoryColor: String,
    fallbackColor: Color,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(categoryColor.toColorOrFallback(fallbackColor)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryIcon.toCategoryIcon(),
            contentDescription = categoryName,
            modifier = Modifier.size(20.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun ErrorBanner(
    error: RecurringError,
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
            if (error == RecurringError.Generic) {
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.recurring_retry))
                }
            }
        }
    }
}

@Composable
private fun DeleteRecurringDialog(
    recurring: RecurringTransaction,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recurring_delete_title)) },
        text = {
            Text(
                text = "${recurring.note.ifBlank { recurring.categoryName }}\n" +
                    stringResource(R.string.recurring_delete_body),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isMutating,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.recurring_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recurring_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringFormSheet(
    mode: RecurringSheetMode,
    accounts: List<Account>,
    categories: List<Category>,
    error: RecurringError?,
    isMutating: Boolean,
    todayProvider: () -> LocalDate,
    onDismiss: () -> Unit,
    onFormChanged: () -> Unit,
    onSubmit: (RecurringFormState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recurring = (mode as? RecurringSheetMode.Edit)?.recurring
    var form by remember(mode, accounts, categories) {
        mutableStateOf(
            RecurringFormState.fromRecurring(
                recurring = recurring,
                accounts = accounts,
                categories = categories,
                today = todayProvider(),
            ),
        )
    }
    val compatibleCategories = categories.compatibleWith(form.type)
    val selectedAccount = accounts.firstOrNull { it.id == form.accountId }
    val canSave = !isMutating &&
        form.amount.isNotBlank() &&
        form.accountId != null &&
        form.categoryId != null &&
        form.nextRunDate.length == DATE_LENGTH

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
                        if (recurring == null) R.string.recurring_add else R.string.recurring_edit,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recurring_cancel))
                }
            }

            TransactionTypeSelector(
                selectedType = form.type,
                onTypeChange = { type ->
                    val selectedCategoryId = form.categoryId.takeIf { selected ->
                        selected != null && categories.compatibleWith(type).any { it.id == selected }
                    }
                    form = form.copy(type = type, categoryId = selectedCategoryId)
                    onFormChanged()
                },
            )

            AmountCard(
                amount = form.amount,
                currencyCode = selectedAccount?.currencyCode ?: DEFAULT_CURRENCY,
                isIncome = form.type == TransactionType.Income,
                onAmountChange = {
                    form = form.copy(amount = MoneyParser.sanitizeAmountInput(it))
                    onFormChanged()
                },
            )

            AccountSelector(
                accounts = accounts,
                selectedAccountId = form.accountId,
                onAccountChange = {
                    form = form.copy(accountId = it)
                    onFormChanged()
                },
            )

            CategorySelector(
                categories = compatibleCategories,
                selectedCategoryId = form.categoryId,
                transactionType = form.type,
                onCategoryChange = {
                    form = form.copy(categoryId = it)
                    onFormChanged()
                },
            )

            Text(
                text = stringResource(R.string.recurring_frequency),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecurringFrequency.entries.forEach { frequency ->
                    FilterChip(
                        selected = form.frequency == frequency,
                        onClick = {
                            form = form.copy(frequency = frequency)
                            onFormChanged()
                        },
                        label = { Text(stringResource(frequency.labelResId)) },
                        modifier = Modifier.testTag("recurring-frequency-${frequency.storageValue}"),
                    )
                }
            }

            OutlinedTextField(
                value = form.nextRunDate,
                onValueChange = {
                    form = form.copy(nextRunDate = it.take(DATE_LENGTH))
                    onFormChanged()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recurring-next-run"),
                label = { Text(stringResource(R.string.recurring_next_run)) },
                placeholder = { Text(stringResource(R.string.recurring_next_run_hint)) },
                leadingIcon = {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null)
                },
                singleLine = true,
            )

            OutlinedTextField(
                value = form.note,
                onValueChange = {
                    form = form.copy(note = it.take(MAX_NOTE_LENGTH))
                    onFormChanged()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recurring-note"),
                label = { Text(stringResource(R.string.recurring_note)) },
                placeholder = { Text(stringResource(R.string.recurring_note_placeholder)) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                },
                singleLine = true,
            )

            error?.let {
                FormErrorMessage(error = it)
            }

            Button(
                onClick = { onSubmit(form) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
            ) {
                Icon(
                    imageVector = if (recurring == null) Icons.Filled.Add else Icons.Filled.Save,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (recurring == null) R.string.recurring_create else R.string.recurring_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TransactionTypeSelector(
    selectedType: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransactionType.entries.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeChange(type) },
                modifier = Modifier.testTag("recurring-type-${type.name}"),
                label = { Text(type.toLabel()) },
                leadingIcon = {
                    if (selectedType == type) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun AmountCard(
    amount: String,
    currencyCode: String,
    isIncome: Boolean,
    onAmountChange: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isIncome) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isIncome) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        ),
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("recurring-amount"),
            label = { Text(stringResource(R.string.recurring_amount)) },
            leadingIcon = {
                Icon(Icons.Filled.AttachMoney, contentDescription = null)
            },
            suffix = { Text(currencyCode) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}

@Composable
private fun AccountSelector(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onAccountChange: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.recurring_account),
            style = MaterialTheme.typography.labelLarge,
        )
        if (accounts.isEmpty()) {
            EmptySelectionCard(
                title = stringResource(R.string.recurring_no_accounts_title),
                body = stringResource(R.string.recurring_no_accounts_body),
                icon = Icons.Filled.AccountBalanceWallet,
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                accounts.forEach { account ->
                    FilterChip(
                        selected = selectedAccountId == account.id,
                        onClick = { onAccountChange(account.id) },
                        modifier = Modifier.testTag("recurring-account-${account.id}"),
                        label = {
                            Text(
                                text = account.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySelector(
    categories: List<Category>,
    selectedCategoryId: Long?,
    transactionType: TransactionType,
    onCategoryChange: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.recurring_category),
            style = MaterialTheme.typography.labelLarge,
        )
        if (categories.isEmpty()) {
            EmptySelectionCard(
                title = stringResource(R.string.recurring_no_categories_title),
                body = stringResource(R.string.recurring_no_categories_body),
                icon = Icons.AutoMirrored.Filled.Label,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { onCategoryChange(category.id) },
                        modifier = Modifier.testTag("recurring-category-${category.id}"),
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            CategoryMark(
                                categoryName = category.name,
                                categoryIcon = category.icon,
                                categoryColor = category.color,
                                fallbackColor = if (transactionType == TransactionType.Income) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySelectionCard(
    title: String,
    body: String,
    icon: ImageVector,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FormErrorMessage(error: RecurringError) {
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

data class RecurringUiState(
    val isLoading: Boolean = false,
    val recurring: List<RecurringTransaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isMutating: Boolean = false,
    val error: RecurringError? = null,
) {
    val canCreateRecurring: Boolean =
        accounts.isNotEmpty() &&
            categories.any { it.type == CategoryType.Expense || it.type == CategoryType.Income || it.type == CategoryType.Both }
}

enum class RecurringError(@StringRes val messageResId: Int) {
    Generic(R.string.recurring_error_generic),
    Amount(R.string.recurring_error_amount),
    NextRun(R.string.recurring_error_next_run),
    AccountRequired(R.string.recurring_error_account_required),
    CategoryRequired(R.string.recurring_error_category_required),
    AccountNotFound(R.string.recurring_error_account_not_found),
    CategoryNotFound(R.string.recurring_error_category_not_found),
    CategoryType(R.string.recurring_error_category_type),
    NotFound(R.string.recurring_error_not_found),
}

private sealed interface RecurringSheetMode {
    data object Create : RecurringSheetMode
    data class Edit(val recurring: RecurringTransaction) : RecurringSheetMode
}

data class RecurringFormState(
    val type: TransactionType = TransactionType.Expense,
    val amount: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val frequency: RecurringFrequency = RecurringFrequency.Monthly,
    val nextRunDate: String,
    val note: String = "",
) {
    fun validationError(
        accounts: List<Account>,
        categories: List<Category>,
    ): RecurringError? {
        val parsedAmount = runCatching { MoneyParser.parsePositiveCents(amount) }.getOrNull()
        if (parsedAmount == null) {
            return RecurringError.Amount
        }
        if (runCatching { nextRunDate.toStartOfDayEpochMillis() }.isFailure) {
            return RecurringError.NextRun
        }
        val selectedAccountId = accountId ?: return RecurringError.AccountRequired
        if (accounts.none { it.id == selectedAccountId }) {
            return RecurringError.AccountNotFound
        }
        val selectedCategoryId = categoryId ?: return RecurringError.CategoryRequired
        if (categories.none { it.id == selectedCategoryId }) {
            return RecurringError.CategoryRequired
        }
        return null
    }

    fun toCreateInput(): CreateRecurringTransactionInput {
        return CreateRecurringTransactionInput(
            type = type,
            amountCents = MoneyParser.parsePositiveCents(amount),
            categoryId = requireNotNull(categoryId),
            accountId = requireNotNull(accountId),
            note = note.trim(),
            frequency = frequency,
            nextRunAtEpochMillis = nextRunDate.toStartOfDayEpochMillis(),
        )
    }

    fun toUpdateInput(): UpdateRecurringTransactionInput {
        return UpdateRecurringTransactionInput(
            type = type,
            amountCents = MoneyParser.parsePositiveCents(amount),
            categoryId = requireNotNull(categoryId),
            accountId = requireNotNull(accountId),
            note = note.trim(),
            frequency = frequency,
            nextRunAtEpochMillis = nextRunDate.toStartOfDayEpochMillis(),
        )
    }

    companion object {
        fun fromRecurring(
            recurring: RecurringTransaction?,
            accounts: List<Account>,
            categories: List<Category>,
            today: LocalDate,
        ): RecurringFormState {
            val type = recurring?.type ?: TransactionType.Expense
            val compatibleCategories = categories.compatibleWith(type)
            return RecurringFormState(
                type = type,
                amount = recurring?.amountCents?.let(MoneyParser::formatPlainCents).orEmpty(),
                accountId = recurring?.accountId
                    ?.takeIf { id -> accounts.any { it.id == id } }
                    ?: accounts.firstOrNull { it.isDefault }?.id
                    ?: accounts.firstOrNull()?.id,
                categoryId = recurring?.categoryId
                    ?.takeIf { id -> compatibleCategories.any { it.id == id } }
                    ?: compatibleCategories.firstOrNull()?.id,
                frequency = recurring?.frequency ?: RecurringFrequency.Monthly,
                nextRunDate = recurring?.nextRunAtEpochMillis?.toDisplayDate() ?: today.plusDays(1).toString(),
                note = recurring?.note.orEmpty(),
            )
        }
    }
}

private fun Throwable.toRecurringError(): RecurringError {
    return when (this) {
        is InvalidMoneyAmountException,
        is MoneyOverflowException,
        is InvalidRecurringAmountException -> RecurringError.Amount

        is InvalidRecurringNextRunException,
        is DateTimeParseException -> RecurringError.NextRun

        is RecurringAccountNotFoundException -> RecurringError.AccountNotFound
        is RecurringCategoryNotFoundException -> RecurringError.CategoryNotFound
        is RecurringCategoryTypeException -> RecurringError.CategoryType
        is RecurringTransactionNotFoundException -> RecurringError.NotFound
        else -> RecurringError.Generic
    }
}

@Composable
private fun TransactionType.toLabel(): String {
    return stringResource(
        when (this) {
            TransactionType.Expense -> R.string.recurring_expense
            TransactionType.Income -> R.string.recurring_income
        },
    )
}

private val RecurringFrequency.labelResId: Int
    @StringRes
    get() = when (this) {
        RecurringFrequency.Daily -> R.string.recurring_frequency_daily
        RecurringFrequency.Weekly -> R.string.recurring_frequency_weekly
        RecurringFrequency.Monthly -> R.string.recurring_frequency_monthly
        RecurringFrequency.Yearly -> R.string.recurring_frequency_yearly
    }

private fun List<Category>.compatibleWith(type: TransactionType): List<Category> {
    return filter { category ->
        when (type) {
            TransactionType.Expense -> category.type == CategoryType.Expense || category.type == CategoryType.Both
            TransactionType.Income -> category.type == CategoryType.Income || category.type == CategoryType.Both
        }
    }
}

private fun RecurringTransaction.amountLabel(): String {
    val prefix = if (type == TransactionType.Income) "+" else "-"
    return "$prefix${MoneyParser.formatPlainCents(abs(amountCents))} $currencyCode"
}

private fun String.toStartOfDayEpochMillis(): Long {
    return LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}

private fun Long.toDisplayDate(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}

private fun String.toCategoryIcon(): ImageVector {
    return when (this) {
        "house", "home", "bed" -> Icons.Filled.Home
        "shopping-bag", "basket" -> Icons.Filled.ShoppingCart
        "briefcase", "salary", "work" -> Icons.Filled.Work
        "money", "currency-dollar", "bank", "wallet" -> Icons.Filled.AccountBalanceWallet
        "arrows-left-right", "transfer" -> Icons.Filled.SwapHoriz
        else -> Icons.AutoMirrored.Filled.Label
    }
}

private fun String.toColorOrFallback(fallback: Color): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }
        .getOrDefault(fallback)
}

private const val DEFAULT_CURRENCY = "USD"
private const val DATE_LENGTH = 10
private const val MAX_NOTE_LENGTH = 120

@Preview(showBackground = true, widthDp = 320, heightDp = 760)
@Composable
private fun RecurringScreenPreview() {
    MoneyTrackerTheme {
        RecurringScreen(
            state = RecurringUiState(
                recurring = listOf(
                    recurringFixture(
                        id = 1,
                        note = "Rent",
                        categoryName = "Housing",
                        amountCents = 120_000,
                        nextRunAtEpochMillis = "2026-08-01".toStartOfDayEpochMillis(),
                        isActive = true,
                    ),
                    recurringFixture(
                        id = 2,
                        note = "Salary",
                        categoryName = "Salary",
                        type = TransactionType.Income,
                        amountCents = 350_000,
                        nextRunAtEpochMillis = "2026-07-15".toStartOfDayEpochMillis(),
                        isActive = false,
                    ),
                ),
                accounts = accountsFixture,
                categories = categoriesFixture,
            ),
            onRetry = {},
            onAddRecurring = {},
            onEditRecurring = {},
            onToggleRecurring = {},
            onDeleteRecurring = {},
        )
    }
}

private fun recurringFixture(
    id: Long,
    note: String,
    categoryName: String,
    type: TransactionType = TransactionType.Expense,
    amountCents: Long,
    nextRunAtEpochMillis: Long,
    isActive: Boolean,
): RecurringTransaction {
    return RecurringTransaction(
        id = id,
        profileId = 1,
        accountId = 1,
        categoryId = id,
        categoryName = categoryName,
        categoryIcon = "shopping-bag",
        categoryColor = if (type == TransactionType.Income) "#10B981" else "#EF4444",
        type = type,
        amountCents = amountCents,
        currencyCode = "USD",
        note = note,
        frequency = RecurringFrequency.Monthly,
        nextRunAtEpochMillis = nextRunAtEpochMillis,
        isActive = isActive,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}

private val accountsFixture = listOf(
    Account(
        id = 1,
        profileId = 1,
        name = "Main card",
        icon = "wallet",
        color = "#6366F1",
        type = AccountType.Checking,
        currencyCode = "USD",
        isDefault = true,
        includeInTotal = true,
        balanceCents = 125_50,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    ),
)

private val categoriesFixture = listOf(
    Category(
        id = 1,
        profileId = 1,
        name = "Housing",
        icon = "home",
        type = CategoryType.Expense,
        color = "#EF4444",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
    Category(
        id = 2,
        profileId = 1,
        name = "Salary",
        icon = "salary",
        type = CategoryType.Income,
        color = "#10B981",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
)
