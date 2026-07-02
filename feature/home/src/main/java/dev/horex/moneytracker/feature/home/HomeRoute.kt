package dev.horex.moneytracker.feature.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.horex.moneytracker.core.balance.BalanceAccount
import dev.horex.moneytracker.core.balance.BalanceCurrency
import dev.horex.moneytracker.core.balance.BalanceQuery
import dev.horex.moneytracker.core.balance.BalanceSnapshot
import dev.horex.moneytracker.core.balance.BalancesRepository
import dev.horex.moneytracker.core.common.AppBootstrapContent
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.component.MoneyTrackerPlaceholderScreen
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.money.MoneyParser
import dev.horex.moneytracker.core.transactions.MoneyTransaction
import dev.horex.moneytracker.core.transactions.TransactionQuery
import dev.horex.moneytracker.core.transactions.TransactionType
import dev.horex.moneytracker.core.transactions.TransactionsRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeRoute(
    localProfileBootstrapper: LocalProfileBootstrapper? = null,
    balancesRepository: BalancesRepository? = null,
    transactionsRepository: TransactionsRepository? = null,
    content: AppBootstrapContent? = null,
    onAddTransaction: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (
        localProfileBootstrapper == null ||
        balancesRepository == null ||
        transactionsRepository == null
    ) {
        val resolvedContent = content ?: AppBootstrapContent(
            title = stringResource(R.string.feature_home_title),
            subtitle = stringResource(R.string.feature_home_subtitle),
        )
        MoneyTrackerPlaceholderScreen(
            title = resolvedContent.title,
            subtitle = resolvedContent.subtitle,
        )
        return
    }

    val scope = rememberCoroutineScope()
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var hasAppliedInitialAccount by remember { mutableStateOf(false) }
    var uiState by remember { mutableStateOf(HomeUiState(isLoading = true)) }

    fun loadDashboard(
        requestedAccountId: Long? = selectedAccountId,
        chooseDefaultAccount: Boolean = false,
    ) {
        scope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val profile = localProfileBootstrapper.ensureActiveProfile()
                val accountSnapshot = balancesRepository.getBalance(profile.id)
                val accountId = if (chooseDefaultAccount && !hasAppliedInitialAccount) {
                    accountSnapshot.accountBalances.firstOrNull { it.isDefault }?.id
                        ?: accountSnapshot.accountBalances.firstOrNull()?.id
                } else {
                    requestedAccountId
                }
                hasAppliedInitialAccount = true
                selectedAccountId = accountId

                val balanceSnapshot = if (accountId == null) {
                    accountSnapshot
                } else {
                    balancesRepository.getBalance(
                        profileId = profile.id,
                        query = BalanceQuery(accountId = accountId),
                    )
                }
                val recentTransactions = transactionsRepository.listTransactions(
                    profileId = profile.id,
                    query = TransactionQuery(
                        accountId = accountId,
                        page = 1,
                        pageSize = RECENT_TRANSACTION_LIMIT,
                    ),
                ).transactions

                uiState = HomeUiState(
                    snapshot = balanceSnapshot,
                    accounts = accountSnapshot.accountBalances,
                    recentTransactions = recentTransactions,
                    selectedAccountId = accountId,
                    hideAmounts = profile.hideAmounts,
                )
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = HomeError.Generic,
                )
            }
        }
    }

    LaunchedEffect(localProfileBootstrapper, balancesRepository, transactionsRepository) {
        loadDashboard(chooseDefaultAccount = true)
    }

    HomeScreen(
        state = uiState,
        modifier = modifier,
        onSelectAccount = { accountId ->
            selectedAccountId = accountId
            loadDashboard(requestedAccountId = accountId)
        },
        onRetry = {
            loadDashboard(requestedAccountId = selectedAccountId)
        },
        onAddTransaction = onAddTransaction,
        onOpenHistory = onOpenHistory,
        onOpenStats = onOpenStats,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSelectAccount: (Long?) -> Unit,
    onRetry: () -> Unit,
    onAddTransaction: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> HomeLoading()
            state.snapshot == null -> HomeErrorState(
                error = state.error ?: HomeError.Generic,
                onRetry = onRetry,
            )

            else -> HomeContent(
                state = state,
                onSelectAccount = onSelectAccount,
                onAddTransaction = onAddTransaction,
                onOpenHistory = onOpenHistory,
                onOpenStats = onOpenStats,
            )
        }
    }
}

@Composable
private fun HomeLoading() {
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
            text = stringResource(R.string.home_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HomeErrorState(
    error: HomeError,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("home-error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(error.messageResId),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.home_retry))
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onSelectAccount: (Long?) -> Unit,
    onAddTransaction: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val snapshot = requireNotNull(state.snapshot)
    val selectedAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val heroCurrency = selectedAccount?.currencyCode ?: snapshot.baseCurrencyCode
    val heroCents = selectedAccount?.balanceCents ?: snapshot.totalInBaseCents
    val summary = snapshot.byCurrency.firstOrNull { it.currencyCode == heroCurrency }
        ?: snapshot.byCurrency.firstOrNull()
        ?: BalanceCurrency(
            currencyCode = heroCurrency,
            incomeCents = 0,
            expenseCents = 0,
            netCents = heroCents,
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.feature_home_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            BalanceHeroCard(
                account = selectedAccount,
                amountCents = heroCents,
                currencyCode = heroCurrency,
                hideAmounts = state.hideAmounts,
                onAddTransaction = onAddTransaction,
            )
        }

        item {
            AccountSelector(
                accounts = state.accounts,
                selectedAccountId = state.selectedAccountId,
                onSelectAccount = onSelectAccount,
            )
        }

        item {
            SummaryCards(
                summary = summary,
                hideAmounts = state.hideAmounts,
                onOpenStats = onOpenStats,
            )
        }

        item {
            RecentTransactionsCard(
                transactions = state.recentTransactions,
                hideAmounts = state.hideAmounts,
                onOpenHistory = onOpenHistory,
            )
        }
    }
}

@Composable
private fun BalanceHeroCard(
    account: BalanceAccount?,
    amountCents: Long,
    currencyCode: String,
    hideAmounts: Boolean,
    onAddTransaction: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-balance-hero"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_net_balance),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    )
                    Text(
                        text = account?.name ?: stringResource(R.string.home_all_accounts),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    )
                }
                IconButton(onClick = onAddTransaction) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.home_add_transaction),
                    )
                }
            }
            Text(
                text = formatMoney(amountCents, currencyCode, hideAmounts),
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccountSelector(
    accounts: List<BalanceAccount>,
    selectedAccountId: Long?,
    onSelectAccount: (Long?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedAccountId == null,
            onClick = { onSelectAccount(null) },
            label = { Text(stringResource(R.string.home_all_accounts)) },
            modifier = Modifier.testTag("home-account-all"),
        )
        accounts.forEach { account ->
            FilterChip(
                selected = selectedAccountId == account.id,
                onClick = { onSelectAccount(account.id) },
                label = { Text(account.name) },
                modifier = Modifier.testTag("home-account-${account.id}"),
            )
        }
    }
}

@Composable
private fun SummaryCards(
    summary: BalanceCurrency,
    hideAmounts: Boolean,
    onOpenStats: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(
            title = stringResource(R.string.home_income),
            amount = formatMoney(summary.incomeCents, summary.currencyCode, hideAmounts),
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            modifier = Modifier
                .weight(1f)
                .testTag("home-income-summary"),
            onClick = onOpenStats,
        )
        SummaryCard(
            title = stringResource(R.string.home_expense),
            amount = formatMoney(summary.expenseCents, summary.currencyCode, hideAmounts),
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            modifier = Modifier
                .weight(1f)
                .testTag("home-expense-summary"),
            onClick = onOpenStats,
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = amount,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentTransactionsCard(
    transactions: List<MoneyTransaction>,
    hideAmounts: Boolean,
    onOpenHistory: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-recent-transactions"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_recent_transactions),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onOpenHistory) {
                    Text(text = stringResource(R.string.home_view_all))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (transactions.isEmpty()) {
                HomeEmptyState()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    transactions.forEach { transaction ->
                        RecentTransactionRow(
                            transaction = transaction,
                            hideAmounts = hideAmounts,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp)
            .testTag("home-empty-state"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentTransactionRow(
    transaction: MoneyTransaction,
    hideAmounts: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(transaction.categoryColor.toColorOrFallback()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = Color.White,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = transaction.categoryName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = transaction.note.takeIf { it.isNotBlank() } ?: transaction.accountName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = transaction.createdAtEpochMillis.formatDisplayDate(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = transaction.formatSignedAmount(hideAmounts),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (transaction.type == TransactionType.Income) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val snapshot: BalanceSnapshot? = null,
    val accounts: List<BalanceAccount> = emptyList(),
    val recentTransactions: List<MoneyTransaction> = emptyList(),
    val selectedAccountId: Long? = null,
    val hideAmounts: Boolean = false,
    val error: HomeError? = null,
)

enum class HomeError(@StringRes val messageResId: Int) {
    Generic(R.string.home_error_generic),
}

private fun MoneyTransaction.formatSignedAmount(hideAmounts: Boolean): String {
    if (hideAmounts) {
        return "$MASKED_AMOUNT $currencyCode"
    }
    val sign = if (type == TransactionType.Income) "+" else "-"
    return "$sign${MoneyParser.formatPlainCents(abs(amountCents))} $currencyCode"
}

private fun formatMoney(cents: Long, currencyCode: String, hideAmounts: Boolean): String {
    if (hideAmounts) {
        return "$MASKED_AMOUNT $currencyCode"
    }
    val sign = if (cents < 0) "-" else ""
    return "$sign${MoneyParser.formatPlainCents(abs(cents))} $currencyCode"
}

private fun Long.formatDisplayDate(): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    return Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .format(formatter)
}

private fun String.toColorOrFallback(): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }
        .getOrDefault(Color(0xFF6366F1))
}

private const val RECENT_TRANSACTION_LIMIT = 5
private const val MASKED_AMOUNT = "****"

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HomeScreenPreview() {
    MoneyTrackerTheme {
        HomeScreen(
            state = HomeUiState(
                snapshot = BalanceSnapshot(
                    profileId = 1,
                    accountId = 1,
                    baseCurrencyCode = "USD",
                    accountBalances = balanceAccountsFixture,
                    byCurrency = listOf(
                        BalanceCurrency(
                            currencyCode = "USD",
                            incomeCents = 280_000,
                            expenseCents = 64_250,
                            netCents = 215_750,
                        ),
                    ),
                    displayConversions = emptyList(),
                    totalInBaseCents = 215_750,
                ),
                accounts = balanceAccountsFixture,
                selectedAccountId = 1,
                recentTransactions = transactionsFixture,
            ),
            onSelectAccount = {},
            onRetry = {},
            onAddTransaction = {},
            onOpenHistory = {},
            onOpenStats = {},
        )
    }
}

private val balanceAccountsFixture = listOf(
    BalanceAccount(
        id = 1,
        profileId = 1,
        name = "Main card",
        icon = "checking",
        color = "#6366F1",
        type = "checking",
        currencyCode = "USD",
        isDefault = true,
        includeInTotal = true,
        balanceCents = 215_750,
    ),
)

private val transactionsFixture = listOf(
    MoneyTransaction(
        id = 1,
        profileId = 1,
        type = TransactionType.Expense,
        amountCents = 4_250,
        categoryId = 1,
        categoryName = "Groceries",
        categoryIcon = "cart",
        categoryColor = "#EF4444",
        accountId = 1,
        accountName = "Main card",
        note = "Weekly market",
        currencyCode = "USD",
        snapshotDate = "2026-07-02",
        createdAtEpochMillis = 1_782_950_400_000L,
        isAdjustment = false,
    ),
)
