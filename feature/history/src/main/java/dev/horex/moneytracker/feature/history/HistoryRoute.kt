package dev.horex.moneytracker.feature.history

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import dev.horex.moneytracker.core.transactions.AdjustmentTransactionImmutableException
import dev.horex.moneytracker.core.transactions.InvalidTransactionAmountException
import dev.horex.moneytracker.core.transactions.MoneyTransaction
import dev.horex.moneytracker.core.transactions.TransactionCategoryNotFoundException
import dev.horex.moneytracker.core.transactions.TransactionCategoryTypeException
import dev.horex.moneytracker.core.transactions.TransactionLinkedToTransferException
import dev.horex.moneytracker.core.transactions.TransactionNotFoundException
import dev.horex.moneytracker.core.transactions.TransactionQuery
import dev.horex.moneytracker.core.transactions.TransactionType
import dev.horex.moneytracker.core.transactions.TransactionsRepository
import dev.horex.moneytracker.core.transactions.UpdateTransactionInput
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

@Composable
fun HistoryRoute(
    localProfileBootstrapper: LocalProfileBootstrapper,
    transactionsRepository: TransactionsRepository,
    accountsRepository: AccountsRepository,
    categoriesRepository: CategoriesRepository,
    initialFilters: HistoryFilters = HistoryFilters(),
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Long?>(null) }
    var filters by remember(initialFilters) { mutableStateOf(initialFilters) }
    var searchDraft by remember(initialFilters) { mutableStateOf(initialFilters.searchText) }
    var uiState by remember { mutableStateOf(HistoryUiState(isLoading = true)) }
    var editTarget by remember { mutableStateOf<MoneyTransaction?>(null) }
    var deleteTarget by remember { mutableStateOf<MoneyTransaction?>(null) }

    fun loadHistory(
        page: Int,
        append: Boolean,
        showLoading: Boolean,
        filterSnapshot: HistoryFilters = filters,
    ) {
        scope.launch {
            if (showLoading) {
                uiState = uiState.copy(
                    isLoading = !append,
                    isLoadingMore = append,
                    error = null,
                )
            }
            try {
                val profile = localProfileBootstrapper.ensureActiveProfile()
                profileId = profile.id
                val accounts = accountsRepository.listAccounts(profile.id)
                val categories = categoriesRepository.listCategories(
                    profileId = profile.id,
                    sortOrder = CategorySortOrder.NameAsc,
                )
                val transactionPage = transactionsRepository.listTransactions(
                    profileId = profile.id,
                    query = filterSnapshot.toQuery(page),
                )
                uiState = HistoryUiState(
                    transactions = if (append) {
                        uiState.transactions + transactionPage.transactions
                    } else {
                        transactionPage.transactions
                    },
                    accounts = accounts,
                    categories = categories,
                    currentPage = transactionPage.currentPage,
                    totalPages = transactionPage.totalPages,
                )
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isMutating = false,
                    error = error.toHistoryError(),
                )
            }
        }
    }

    fun applyFilters(newFilters: HistoryFilters) {
        filters = newFilters
        searchDraft = newFilters.searchText
        loadHistory(page = 1, append = false, showLoading = true, filterSnapshot = newFilters)
    }

    fun mutate(block: suspend (Long) -> Unit) {
        scope.launch {
            val activeProfileId = profileId ?: localProfileBootstrapper.ensureActiveProfile().id
            profileId = activeProfileId
            uiState = uiState.copy(isMutating = true, error = null)
            try {
                block(activeProfileId)
                editTarget = null
                deleteTarget = null
                val nextFilters = filters
                val transactionPage = transactionsRepository.listTransactions(
                    profileId = activeProfileId,
                    query = nextFilters.toQuery(1),
                )
                uiState = uiState.copy(
                    isMutating = false,
                    transactions = transactionPage.transactions,
                    currentPage = transactionPage.currentPage,
                    totalPages = transactionPage.totalPages,
                )
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isMutating = false,
                    error = error.toHistoryError(),
                )
            }
        }
    }

    LaunchedEffect(
        localProfileBootstrapper,
        transactionsRepository,
        accountsRepository,
        categoriesRepository,
        initialFilters,
    ) {
        filters = initialFilters
        searchDraft = initialFilters.searchText
        loadHistory(
            page = 1,
            append = false,
            showLoading = true,
            filterSnapshot = initialFilters,
        )
    }

    HistoryScreen(
        state = uiState,
        filters = filters,
        searchDraft = searchDraft,
        modifier = modifier,
        onSearchDraftChange = { searchDraft = it.take(MAX_SEARCH_LENGTH) },
        onApplySearch = {
            applyFilters(filters.copy(searchText = searchDraft.trim()))
        },
        onClearFilters = {
            applyFilters(HistoryFilters())
        },
        onSelectAccount = { accountId ->
            applyFilters(filters.copy(accountId = accountId))
        },
        onSelectCategory = { categoryId ->
            applyFilters(filters.copy(categoryId = categoryId))
        },
        onLoadMore = {
            loadHistory(
                page = uiState.currentPage + 1,
                append = true,
                showLoading = true,
            )
        },
        onRetry = {
            loadHistory(page = 1, append = false, showLoading = true)
        },
        onEditTransaction = { editTarget = it },
        onDeleteTransaction = { deleteTarget = it },
    )

    editTarget?.let { transaction ->
        EditTransactionSheet(
            transaction = transaction,
            categories = uiState.categories.compatibleWith(transaction.type),
            error = uiState.error,
            isMutating = uiState.isMutating,
            onDismiss = { editTarget = null },
            onSave = { form ->
                mutate { activeProfileId ->
                    transactionsRepository.updateTransaction(
                        profileId = activeProfileId,
                        transactionId = transaction.id,
                        input = form.toUpdateInput(),
                    )
                }
            },
        )
    }

    deleteTarget?.let { transaction ->
        DeleteTransactionDialog(
            transaction = transaction,
            isMutating = uiState.isMutating,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                mutate { activeProfileId ->
                    transactionsRepository.deleteTransaction(activeProfileId, transaction.id)
                }
            },
        )
    }
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    filters: HistoryFilters,
    searchDraft: String,
    onSearchDraftChange: (String) -> Unit,
    onApplySearch: () -> Unit,
    onClearFilters: () -> Unit,
    onSelectAccount: (Long?) -> Unit,
    onSelectCategory: (Long?) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onEditTransaction: (MoneyTransaction) -> Unit,
    onDeleteTransaction: (MoneyTransaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> HistoryLoading()
                else -> HistoryList(
                    state = state,
                    filters = filters,
                    searchDraft = searchDraft,
                    onSearchDraftChange = onSearchDraftChange,
                    onApplySearch = onApplySearch,
                    onClearFilters = onClearFilters,
                    onSelectAccount = onSelectAccount,
                    onSelectCategory = onSelectCategory,
                    onLoadMore = onLoadMore,
                    onEditTransaction = onEditTransaction,
                    onDeleteTransaction = onDeleteTransaction,
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
private fun HistoryLoading() {
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
            text = stringResource(R.string.history_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    filters: HistoryFilters,
    searchDraft: String,
    onSearchDraftChange: (String) -> Unit,
    onApplySearch: () -> Unit,
    onClearFilters: () -> Unit,
    onSelectAccount: (Long?) -> Unit,
    onSelectCategory: (Long?) -> Unit,
    onLoadMore: () -> Unit,
    onEditTransaction: (MoneyTransaction) -> Unit,
    onDeleteTransaction: (MoneyTransaction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.history_subtitle),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            HistoryFiltersPanel(
                filters = filters,
                searchDraft = searchDraft,
                accounts = state.accounts,
                categories = state.categories,
                onSearchDraftChange = onSearchDraftChange,
                onApplySearch = onApplySearch,
                onClearFilters = onClearFilters,
                onSelectAccount = onSelectAccount,
                onSelectCategory = onSelectCategory,
            )
        }

        if (state.transactions.isEmpty()) {
            item {
                HistoryEmptyState()
            }
        } else {
            items(
                items = state.transactions,
                key = { it.id },
            ) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    enabled = !state.isMutating,
                    onEditTransaction = onEditTransaction,
                    onDeleteTransaction = onDeleteTransaction,
                )
            }
        }

        item {
            HistoryPageFooter(
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                isLoadingMore = state.isLoadingMore,
                onLoadMore = onLoadMore,
            )
        }
    }
}

@Composable
private fun HistoryFiltersPanel(
    filters: HistoryFilters,
    searchDraft: String,
    accounts: List<Account>,
    categories: List<Category>,
    onSearchDraftChange: (String) -> Unit,
    onApplySearch: () -> Unit,
    onClearFilters: () -> Unit,
    onSelectAccount: (Long?) -> Unit,
    onSelectCategory: (Long?) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = searchDraft,
            onValueChange = onSearchDraftChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.history_search)) },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onApplySearch,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.history_apply_filters))
            }
            TextButton(
                onClick = onClearFilters,
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.history_clear_filters))
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filters.accountId == null,
                onClick = { onSelectAccount(null) },
                label = { Text(stringResource(R.string.history_all_accounts)) },
            )
            accounts.forEach { account ->
                FilterChip(
                    selected = filters.accountId == account.id,
                    onClick = { onSelectAccount(account.id) },
                    label = { Text(account.name) },
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filters.categoryId == null,
                onClick = { onSelectCategory(null) },
                label = { Text(stringResource(R.string.history_all_categories)) },
            )
            categories.forEach { category ->
                FilterChip(
                    selected = filters.categoryId == category.id,
                    onClick = { onSelectCategory(category.id) },
                    label = { Text(category.name) },
                )
            }
        }
    }
}

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.history_empty_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransactionCard(
    transaction: MoneyTransaction,
    enabled: Boolean,
    onEditTransaction: (MoneyTransaction) -> Unit,
    onDeleteTransaction: (MoneyTransaction) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryMark(color = transaction.categoryColor)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = transaction.categoryName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(transaction.type.toLabel()) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        text = transaction.note.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.history_note_empty),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${transaction.accountName} · ${transaction.createdAtEpochMillis.formatDisplayDate()}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = transaction.formatSignedAmount(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == TransactionType.Income) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = { onEditTransaction(transaction) },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.history_edit),
                    )
                }
                IconButton(
                    onClick = { onDeleteTransaction(transaction) },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.history_delete),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryMark(color: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.toColorOrFallback()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
private fun HistoryPageFooter(
    currentPage: Int,
    totalPages: Int,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.history_page_label, currentPage, totalPages),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (currentPage < totalPages) {
            Button(
                onClick = onLoadMore,
                modifier = Modifier.padding(top = 8.dp),
                enabled = !isLoadingMore,
            ) {
                Text(text = stringResource(R.string.history_load_more))
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    error: HistoryError,
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
                Text(text = stringResource(R.string.history_retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTransactionSheet(
    transaction: MoneyTransaction,
    categories: List<Category>,
    error: HistoryError?,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onSave: (HistoryEditFormState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var form by remember(transaction) {
        mutableStateOf(HistoryEditFormState.fromTransaction(transaction))
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
                    text = stringResource(R.string.history_edit),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.history_cancel))
                }
            }

            OutlinedTextField(
                value = form.amount,
                onValueChange = { form = form.copy(amount = MoneyParser.sanitizeAmountInput(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.history_amount)) },
                suffix = { Text(transaction.currencyCode) },
                singleLine = true,
            )
            OutlinedTextField(
                value = form.date,
                onValueChange = { form = form.copy(date = it.take(MAX_DATE_LENGTH)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.history_date)) },
                placeholder = { Text(stringResource(R.string.history_date_hint)) },
                singleLine = true,
            )

            Text(
                text = stringResource(R.string.history_category),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = form.categoryId == category.id,
                        onClick = { form = form.copy(categoryId = category.id) },
                        label = { Text(category.name) },
                    )
                }
            }

            OutlinedTextField(
                value = form.note,
                onValueChange = { form = form.copy(note = it.take(MAX_NOTE_LENGTH)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.history_note)) },
                singleLine = true,
            )

            error?.let {
                FormErrorMessage(error = it)
            }

            Button(
                onClick = { onSave(form) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isMutating && form.canSave,
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.history_save))
            }
        }
    }
}

@Composable
private fun FormErrorMessage(error: HistoryError) {
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

@Composable
private fun DeleteTransactionDialog(
    transaction: MoneyTransaction,
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
            Text(text = stringResource(R.string.history_delete_title))
        },
        text = {
            Text(text = stringResource(R.string.history_delete_body))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isMutating,
            ) {
                Text(text = stringResource(R.string.history_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.history_cancel))
            }
        },
    )
}

data class HistoryUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val transactions: List<MoneyTransaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val isMutating: Boolean = false,
    val error: HistoryError? = null,
)

data class HistoryFilters(
    val searchText: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val transactionType: TransactionType? = null,
    val currencyCode: String? = null,
    val fromEpochMillis: Long? = null,
    val toEpochMillis: Long? = null,
) {
    fun toQuery(page: Int): TransactionQuery {
        return TransactionQuery(
            accountId = accountId,
            categoryId = categoryId,
            type = transactionType,
            currencyCode = currencyCode,
            fromEpochMillis = fromEpochMillis,
            toEpochMillis = toEpochMillis,
            searchText = searchText.trim().takeIf { it.isNotEmpty() },
            page = page,
            pageSize = HISTORY_PAGE_SIZE,
        )
    }
}

data class HistoryEditFormState(
    val amount: String,
    val categoryId: Long,
    val date: String,
    val note: String,
) {
    val canSave: Boolean
        get() = amount.isNotBlank() && categoryId > 0 && date.length == MAX_DATE_LENGTH

    fun toUpdateInput(): UpdateTransactionInput {
        return UpdateTransactionInput(
            amountCents = MoneyParser.parsePositiveCents(amount),
            categoryId = categoryId,
            note = note.trim(),
            createdAtEpochMillis = date.toStartOfDayEpochMillis(),
        )
    }

    companion object {
        fun fromTransaction(transaction: MoneyTransaction): HistoryEditFormState {
            return HistoryEditFormState(
                amount = MoneyParser.formatPlainCents(transaction.amountCents),
                categoryId = transaction.categoryId,
                date = transaction.createdAtEpochMillis.formatDateInput(),
                note = transaction.note,
            )
        }
    }
}

enum class HistoryError(@StringRes val messageResId: Int) {
    Generic(R.string.history_error_generic),
    InvalidAmount(R.string.history_error_invalid_amount),
    InvalidDate(R.string.history_error_invalid_date),
    Category(R.string.history_error_category),
    LinkedTransfer(R.string.history_error_linked_transfer),
    NotFound(R.string.history_error_not_found),
}

private fun Throwable.toHistoryError(): HistoryError {
    return when (this) {
        is InvalidMoneyAmountException,
        is MoneyOverflowException,
        is InvalidTransactionAmountException -> HistoryError.InvalidAmount

        is DateTimeParseException -> HistoryError.InvalidDate
        is TransactionCategoryNotFoundException,
        is TransactionCategoryTypeException -> HistoryError.Category

        is TransactionLinkedToTransferException,
        is AdjustmentTransactionImmutableException -> HistoryError.LinkedTransfer

        is TransactionNotFoundException -> HistoryError.NotFound
        else -> HistoryError.Generic
    }
}

@Composable
private fun TransactionType.toLabel(): String {
    return stringResource(
        when (this) {
            TransactionType.Income -> R.string.history_income
            TransactionType.Expense -> R.string.history_expense
        },
    )
}

private fun MoneyTransaction.formatSignedAmount(): String {
    val sign = if (type == TransactionType.Income) "+" else "-"
    return "$sign${MoneyParser.formatPlainCents(abs(amountCents))} $currencyCode"
}

private fun Long.formatDateInput(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()
}

private fun Long.formatDisplayDate(): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(formatter)
}

private fun String.toStartOfDayEpochMillis(): Long {
    return LocalDate.parse(this, DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}

private fun List<Category>.compatibleWith(type: TransactionType): List<Category> {
    return filter { category ->
        when (type) {
            TransactionType.Expense -> category.type == CategoryType.Expense || category.type == CategoryType.Both
            TransactionType.Income -> category.type == CategoryType.Income || category.type == CategoryType.Both
        }
    }
}

private fun String.toColorOrFallback(): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }
        .getOrDefault(Color(0xFF6366F1))
}

private const val HISTORY_PAGE_SIZE = 25
private const val MAX_SEARCH_LENGTH = 80
private const val MAX_NOTE_LENGTH = 120
private const val MAX_DATE_LENGTH = 10

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun HistoryScreenPreview() {
    MoneyTrackerTheme {
        HistoryScreen(
            state = HistoryUiState(
                transactions = listOf(
                    transactionFixture(
                        id = 1,
                        categoryName = "Groceries",
                        note = "Weekly market",
                        type = TransactionType.Expense,
                        amountCents = 4250,
                    ),
                    transactionFixture(
                        id = 2,
                        categoryName = "Salary",
                        note = "July",
                        type = TransactionType.Income,
                        amountCents = 220000,
                    ),
                ),
                accounts = accountsFixture,
                categories = categoriesFixture,
            ),
            filters = HistoryFilters(),
            searchDraft = "",
            onSearchDraftChange = {},
            onApplySearch = {},
            onClearFilters = {},
            onSelectAccount = {},
            onSelectCategory = {},
            onLoadMore = {},
            onRetry = {},
            onEditTransaction = {},
            onDeleteTransaction = {},
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
)

private val categoriesFixture = listOf(
    Category(
        id = 1,
        profileId = 1,
        name = "Groceries",
        icon = "cart",
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

private fun transactionFixture(
    id: Long,
    categoryName: String,
    note: String,
    type: TransactionType,
    amountCents: Long,
): MoneyTransaction {
    return MoneyTransaction(
        id = id,
        profileId = 1,
        type = type,
        amountCents = amountCents,
        categoryId = id,
        categoryName = categoryName,
        categoryIcon = "tag",
        categoryColor = if (type == TransactionType.Income) "#10B981" else "#EF4444",
        accountId = 1,
        accountName = "Main card",
        note = note,
        currencyCode = "USD",
        snapshotDate = "2026-07-02",
        createdAtEpochMillis = 1_782_950_400_000L,
        isAdjustment = false,
    )
}
