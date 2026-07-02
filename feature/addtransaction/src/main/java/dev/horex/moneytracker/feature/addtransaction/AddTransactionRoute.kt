package dev.horex.moneytracker.feature.addtransaction

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.horex.moneytracker.core.categories.Category
import dev.horex.moneytracker.core.categories.CategorySortOrder
import dev.horex.moneytracker.core.categories.CategoryType
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.money.InvalidMoneyAmountException
import dev.horex.moneytracker.core.money.MoneyOverflowException
import dev.horex.moneytracker.core.money.MoneyParser
import dev.horex.moneytracker.core.transactions.CreateTransactionInput
import dev.horex.moneytracker.core.transactions.InvalidTransactionAmountException
import dev.horex.moneytracker.core.transactions.TransactionAccountNotFoundException
import dev.horex.moneytracker.core.transactions.TransactionCategoryNotFoundException
import dev.horex.moneytracker.core.transactions.TransactionCategoryTypeException
import dev.horex.moneytracker.core.transactions.TransactionType
import dev.horex.moneytracker.core.transactions.TransactionsRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun AddTransactionRoute(
    localProfileBootstrapper: LocalProfileBootstrapper,
    transactionsRepository: TransactionsRepository,
    accountsRepository: AccountsRepository,
    categoriesRepository: CategoriesRepository,
    onTransactionSaved: () -> Unit = {},
    modifier: Modifier = Modifier,
    todayProvider: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) },
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Long?>(null) }
    var form by remember {
        mutableStateOf(AddTransactionFormState(date = todayProvider().toString()))
    }
    var uiState by remember { mutableStateOf(AddTransactionUiState(isLoading = true)) }

    fun loadFormData(showLoading: Boolean) {
        scope.launch {
            if (showLoading) {
                uiState = uiState.copy(isLoading = true, error = null)
            }
            try {
                val profile = localProfileBootstrapper.ensureActiveProfile()
                profileId = profile.id
                val accounts = accountsRepository.listAccounts(profile.id)
                val categories = categoriesRepository.listCategories(
                    profileId = profile.id,
                    sortOrder = CategorySortOrder.Frequency,
                )
                val selectedAccountId = form.accountId.takeIf { selected ->
                    selected != null && accounts.any { it.id == selected }
                } ?: accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id
                val selectedCategoryId = form.categoryId.takeIf { selected ->
                    selected != null && categories.compatibleWith(form.type).any { it.id == selected }
                }

                form = form.copy(
                    accountId = selectedAccountId,
                    categoryId = selectedCategoryId,
                )
                uiState = AddTransactionUiState(
                    accounts = accounts,
                    categories = categories,
                )
            } catch (error: Throwable) {
                uiState = AddTransactionUiState(error = error.toAddTransactionError())
            }
        }
    }

    fun submit() {
        val compatibleCategories = uiState.categories.compatibleWith(form.type)
        val validationError = form.validationError(
            accounts = uiState.accounts,
            categories = compatibleCategories,
        )
        if (validationError != null) {
            uiState = uiState.copy(error = validationError)
            return
        }

        scope.launch {
            val activeProfileId = profileId ?: localProfileBootstrapper.ensureActiveProfile().id
            profileId = activeProfileId
            uiState = uiState.copy(isSaving = true, error = null)
            try {
                transactionsRepository.addTransaction(
                    profileId = activeProfileId,
                    input = form.toCreateInput(),
                )
                uiState = uiState.copy(isSaving = false, error = null)
                onTransactionSaved()
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isSaving = false,
                    error = error.toAddTransactionError(),
                )
            }
        }
    }

    LaunchedEffect(localProfileBootstrapper, accountsRepository, categoriesRepository) {
        loadFormData(showLoading = true)
    }

    AddTransactionScreen(
        state = uiState,
        form = form,
        modifier = modifier,
        onRetry = { loadFormData(showLoading = true) },
        onTypeChange = { type ->
            val selectedCategoryId = form.categoryId.takeIf { selected ->
                selected != null && uiState.categories.compatibleWith(type).any { it.id == selected }
            }
            form = form.copy(type = type, categoryId = selectedCategoryId)
            uiState = uiState.copy(error = null)
        },
        onAmountChange = {
            form = form.copy(amount = MoneyParser.sanitizeAmountInput(it))
            uiState = uiState.copy(error = null)
        },
        onAccountChange = {
            form = form.copy(accountId = it)
            uiState = uiState.copy(error = null)
        },
        onCategoryChange = {
            form = form.copy(categoryId = it)
            uiState = uiState.copy(error = null)
        },
        onDateChange = {
            form = form.copy(date = it.take(MAX_DATE_LENGTH))
            uiState = uiState.copy(error = null)
        },
        onNoteChange = {
            form = form.copy(note = it.take(MAX_NOTE_LENGTH))
            uiState = uiState.copy(error = null)
        },
        onSubmit = ::submit,
    )
}

@Composable
fun AddTransactionScreen(
    state: AddTransactionUiState,
    form: AddTransactionFormState,
    onRetry: () -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountChange: (Long) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onDateChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> AddTransactionLoading()
                else -> AddTransactionForm(
                    state = state,
                    form = form,
                    onTypeChange = onTypeChange,
                    onAmountChange = onAmountChange,
                    onAccountChange = onAccountChange,
                    onCategoryChange = onCategoryChange,
                    onDateChange = onDateChange,
                    onNoteChange = onNoteChange,
                    onSubmit = onSubmit,
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
private fun AddTransactionLoading() {
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
            text = stringResource(R.string.add_transaction_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AddTransactionForm(
    state: AddTransactionUiState,
    form: AddTransactionFormState,
    onTypeChange: (TransactionType) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountChange: (Long) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onDateChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val compatibleCategories = state.categories.compatibleWith(form.type)
    val selectedAccount = state.accounts.firstOrNull { it.id == form.accountId }
    val currencyCode = selectedAccount?.currencyCode ?: DEFAULT_CURRENCY

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.add_transaction_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.add_transaction_subtitle),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            TransactionTypeSelector(
                selectedType = form.type,
                onTypeChange = onTypeChange,
            )
        }

        item {
            AmountCard(
                amount = form.amount,
                currencyCode = currencyCode,
                isIncome = form.type == TransactionType.Income,
                onAmountChange = onAmountChange,
            )
        }

        item {
            AccountSelector(
                accounts = state.accounts,
                selectedAccountId = form.accountId,
                onAccountChange = onAccountChange,
            )
        }

        item {
            CategorySelector(
                categories = compatibleCategories,
                selectedCategoryId = form.categoryId,
                transactionType = form.type,
                onCategoryChange = onCategoryChange,
            )
        }

        item {
            OutlinedTextField(
                value = form.date,
                onValueChange = onDateChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add-transaction-date"),
                label = { Text(stringResource(R.string.add_transaction_date)) },
                placeholder = { Text(stringResource(R.string.add_transaction_date_hint)) },
                leadingIcon = {
                    Icon(Icons.Filled.CalendarToday, contentDescription = null)
                },
                singleLine = true,
            )
        }

        item {
            OutlinedTextField(
                value = form.note,
                onValueChange = onNoteChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add-transaction-note"),
                label = { Text(stringResource(R.string.add_transaction_note)) },
                placeholder = { Text(stringResource(R.string.add_transaction_note_placeholder)) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                },
                singleLine = true,
            )
        }

        item {
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add-transaction-save"),
                enabled = !state.isSaving,
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (state.isSaving) {
                            R.string.add_transaction_saving
                        } else {
                            R.string.add_transaction_save
                        },
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
                modifier = Modifier.testTag("add-transaction-type-${type.name}"),
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
                .testTag("add-transaction-amount"),
            label = { Text(stringResource(R.string.add_transaction_amount)) },
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
            text = stringResource(R.string.add_transaction_account),
            style = MaterialTheme.typography.labelLarge,
        )
        if (accounts.isEmpty()) {
            EmptySelectionCard(
                title = stringResource(R.string.add_transaction_no_accounts_title),
                body = stringResource(R.string.add_transaction_no_accounts_body),
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
                        modifier = Modifier.testTag("add-transaction-account-${account.id}"),
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
            text = stringResource(R.string.add_transaction_category),
            style = MaterialTheme.typography.labelLarge,
        )
        if (categories.isEmpty()) {
            EmptySelectionCard(
                title = stringResource(R.string.add_transaction_no_categories_title),
                body = stringResource(R.string.add_transaction_no_categories_body),
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
                        modifier = Modifier.testTag("add-transaction-category-${category.id}"),
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            CategoryMark(
                                category = category,
                                isSelected = selectedCategoryId == category.id,
                                transactionType = transactionType,
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
private fun CategoryMark(
    category: Category,
    isSelected: Boolean,
    transactionType: TransactionType,
) {
    val fallback = if (transactionType == TransactionType.Income) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (isSelected) fallback else category.color.toColorOrFallback()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = category.icon.toCategoryIcon(),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun ErrorBanner(
    error: AddTransactionError,
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
            if (error == AddTransactionError.Generic) {
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.add_transaction_retry))
                }
            }
        }
    }
}

data class AddTransactionUiState(
    val isLoading: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    val error: AddTransactionError? = null,
)

data class AddTransactionFormState(
    val type: TransactionType = TransactionType.Expense,
    val amount: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val date: String,
    val note: String = "",
) {
    fun validationError(
        accounts: List<Account>,
        categories: List<Category>,
    ): AddTransactionError? {
        val parsedAmount = runCatching { MoneyParser.parsePositiveCents(amount) }.getOrNull()
        if (parsedAmount == null) {
            return AddTransactionError.InvalidAmount
        }
        if (runCatching { LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE) }.isFailure) {
            return AddTransactionError.InvalidDate
        }
        val selectedAccountId = accountId ?: return AddTransactionError.AccountRequired
        if (accounts.none { it.id == selectedAccountId }) {
            return AddTransactionError.AccountNotFound
        }
        val selectedCategoryId = categoryId ?: return AddTransactionError.CategoryRequired
        if (categories.none { it.id == selectedCategoryId }) {
            return AddTransactionError.CategoryRequired
        }
        return null
    }

    fun toCreateInput(): CreateTransactionInput {
        return CreateTransactionInput(
            type = type,
            amountCents = MoneyParser.parsePositiveCents(amount),
            categoryId = requireNotNull(categoryId),
            accountId = requireNotNull(accountId),
            note = note.trim(),
            createdAtEpochMillis = date.toStartOfDayEpochMillis(),
        )
    }
}

enum class AddTransactionError(@StringRes val messageResId: Int) {
    Generic(R.string.add_transaction_error_generic),
    InvalidAmount(R.string.add_transaction_error_invalid_amount),
    InvalidDate(R.string.add_transaction_error_invalid_date),
    AccountRequired(R.string.add_transaction_error_account_required),
    CategoryRequired(R.string.add_transaction_error_category_required),
    AccountNotFound(R.string.add_transaction_error_account_not_found),
    CategoryNotFound(R.string.add_transaction_error_category_not_found),
    CategoryType(R.string.add_transaction_error_category_type),
}

private fun Throwable.toAddTransactionError(): AddTransactionError {
    return when (this) {
        is InvalidMoneyAmountException,
        is MoneyOverflowException,
        is InvalidTransactionAmountException -> AddTransactionError.InvalidAmount

        is DateTimeParseException -> AddTransactionError.InvalidDate
        is TransactionAccountNotFoundException -> AddTransactionError.AccountNotFound
        is TransactionCategoryNotFoundException -> AddTransactionError.CategoryNotFound
        is TransactionCategoryTypeException -> AddTransactionError.CategoryType
        else -> AddTransactionError.Generic
    }
}

@Composable
private fun TransactionType.toLabel(): String {
    return stringResource(
        when (this) {
            TransactionType.Expense -> R.string.add_transaction_expense
            TransactionType.Income -> R.string.add_transaction_income
        },
    )
}

private fun List<Category>.compatibleWith(type: TransactionType): List<Category> {
    return filter { category ->
        when (type) {
            TransactionType.Expense -> category.type == CategoryType.Expense || category.type == CategoryType.Both
            TransactionType.Income -> category.type == CategoryType.Income || category.type == CategoryType.Both
        }
    }
}

private fun String.toStartOfDayEpochMillis(): Long {
    return LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
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

private fun String.toColorOrFallback(): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }
        .getOrDefault(Color(0xFF6366F1))
}

private const val DEFAULT_CURRENCY = "USD"
private const val MAX_NOTE_LENGTH = 120
private const val MAX_DATE_LENGTH = 10

@Preview(showBackground = true, widthDp = 320, heightDp = 720)
@Composable
private fun AddTransactionScreenPreview() {
    MoneyTrackerTheme {
        AddTransactionScreen(
            state = AddTransactionUiState(
                accounts = accountsFixture,
                categories = categoriesFixture,
            ),
            form = AddTransactionFormState(
                amount = "42.50",
                accountId = 1,
                categoryId = 1,
                date = "2026-07-02",
                note = "Groceries",
            ),
            onRetry = {},
            onTypeChange = {},
            onAmountChange = {},
            onAccountChange = {},
            onCategoryChange = {},
            onDateChange = {},
            onNoteChange = {},
            onSubmit = {},
        )
    }
}

private val accountsFixture = listOf(
    Account(
        id = 1,
        profileId = 1,
        name = "Main card",
        icon = "checking",
        color = "#6366F1",
        type = AccountType.Checking,
        currencyCode = "USD",
        isDefault = true,
        includeInTotal = true,
        balanceCents = 125_50,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    ),
    Account(
        id = 2,
        profileId = 1,
        name = "Cash",
        icon = "cash",
        color = "#F59E0B",
        type = AccountType.Cash,
        currencyCode = "USD",
        isDefault = false,
        includeInTotal = true,
        balanceCents = 40_00,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    ),
)

private val categoriesFixture = listOf(
    Category(
        id = 1,
        profileId = 1,
        name = "Food",
        icon = "shopping-bag",
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
