package dev.horex.moneytracker.feature.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.horex.moneytracker.core.accounts.Account
import dev.horex.moneytracker.core.accounts.AccountType
import dev.horex.moneytracker.core.accounts.AccountsRepository
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.money.MoneyParser
import dev.horex.moneytracker.core.preferences.MoneyTrackerSettings
import dev.horex.moneytracker.core.preferences.SettingsRepository
import dev.horex.moneytracker.core.preferences.StatsChartStylePreference
import dev.horex.moneytracker.core.preferences.UpdateSettingsInput
import dev.horex.moneytracker.core.preferences.UpdateSettingsUiPreferencesInput
import dev.horex.moneytracker.core.stats.CategoryStat
import dev.horex.moneytracker.core.stats.StatsPeriod
import dev.horex.moneytracker.core.stats.StatsQuery
import dev.horex.moneytracker.core.stats.StatsRange
import dev.horex.moneytracker.core.stats.StatsRepository
import dev.horex.moneytracker.core.stats.StatsSnapshot
import dev.horex.moneytracker.core.stats.StatsTransactionType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun StatsRoute(
    localProfileBootstrapper: LocalProfileBootstrapper,
    accountsRepository: AccountsRepository,
    settingsRepository: SettingsRepository,
    statsRepository: StatsRepository,
    modifier: Modifier = Modifier,
    onOpenHistory: (StatsDrilldownFilter) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Long?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedPeriod by remember { mutableStateOf(StatsPeriod.Month) }
    var selectedCurrencyCode by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf(StatsTransactionType.Expense) }
    var chartStyle by remember { mutableStateOf(StatsChartStylePreference.Donut) }
    var uiState by remember { mutableStateOf(StatsUiState(isLoading = true)) }

    fun loadStats(
        activeProfileId: Long,
        accountId: Long?,
        period: StatsPeriod,
        preferredCurrencyCode: String? = selectedCurrencyCode,
    ) {
        scope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val snapshot = statsRepository.getStats(
                    profileId = activeProfileId,
                    query = StatsQuery(period = period, accountId = accountId),
                )
                selectedCurrencyCode = snapshot.preferredCurrencyCode(
                    preferred = preferredCurrencyCode ?: uiState.settings?.baseCurrencyCode,
                )
                uiState = uiState.copy(
                    isLoading = false,
                    snapshot = snapshot,
                    error = null,
                )
            } catch (_: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = StatsError.Generic,
                )
            }
        }
    }

    LaunchedEffect(localProfileBootstrapper, accountsRepository, settingsRepository, statsRepository) {
        uiState = StatsUiState(isLoading = true)
        try {
            val profile = localProfileBootstrapper.ensureActiveProfile()
            val accounts = accountsRepository.listAccounts(profile.id)
            val settings = settingsRepository.getSettings()
            val initialAccountId = accounts.firstOrNull { it.isDefault }?.id ?: accounts.firstOrNull()?.id
            val snapshot = statsRepository.getStats(
                profileId = profile.id,
                query = StatsQuery(period = selectedPeriod, accountId = initialAccountId),
            )

            profileId = profile.id
            selectedAccountId = initialAccountId
            chartStyle = settings.uiPreferences.statsChartStyle
            selectedCurrencyCode = snapshot.preferredCurrencyCode(settings.baseCurrencyCode)
            uiState = StatsUiState(
                accounts = accounts,
                settings = settings,
                snapshot = snapshot,
            )
        } catch (_: Throwable) {
            uiState = StatsUiState(
                isLoading = false,
                error = StatsError.Generic,
            )
        }
    }

    StatsScreen(
        state = uiState,
        selectedAccountId = selectedAccountId,
        selectedPeriod = selectedPeriod,
        selectedCurrencyCode = selectedCurrencyCode,
        selectedType = selectedType,
        chartStyle = chartStyle,
        modifier = modifier,
        onSelectAccount = { accountId ->
            selectedAccountId = accountId
            profileId?.let { loadStats(it, accountId, selectedPeriod) }
        },
        onSelectPeriod = { period ->
            selectedPeriod = period
            profileId?.let { loadStats(it, selectedAccountId, period) }
        },
        onSelectCurrency = { selectedCurrencyCode = it },
        onSelectType = { selectedType = it },
        onSelectChartStyle = { style ->
            chartStyle = style
            scope.launch {
                try {
                    val settings = settingsRepository.updateSettings(
                        UpdateSettingsInput(
                            uiPreferences = UpdateSettingsUiPreferencesInput(
                                statsChartStyle = style,
                            ),
                        ),
                    )
                    uiState = uiState.copy(settings = settings, error = null)
                } catch (_: Throwable) {
                    uiState = uiState.copy(error = StatsError.Generic)
                }
            }
        },
        onOpenHistory = onOpenHistory,
        onRetry = {
            profileId?.let { loadStats(it, selectedAccountId, selectedPeriod) }
        },
    )
}

@Composable
fun StatsScreen(
    state: StatsUiState,
    selectedAccountId: Long?,
    selectedPeriod: StatsPeriod,
    selectedCurrencyCode: String?,
    selectedType: StatsTransactionType,
    chartStyle: StatsChartStylePreference,
    onSelectAccount: (Long?) -> Unit,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onSelectType: (StatsTransactionType) -> Unit,
    onSelectChartStyle: (StatsChartStylePreference) -> Unit,
    onOpenHistory: (StatsDrilldownFilter) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("stats-list"),
            contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                StatsHeader()
            }

            when {
                state.isLoading -> item {
                    StatsLoading()
                }

                state.error != null -> item {
                    StatsErrorCard(error = state.error, onRetry = onRetry)
                }

                state.snapshot == null -> item {
                    StatsEmptyState()
                }

                else -> {
                    val snapshot = state.snapshot
                    val currencyCode = selectedCurrencyCode
                        ?: snapshot.preferredCurrencyCode(state.settings?.baseCurrencyCode)
                    val hideAmounts = state.settings?.uiPreferences?.hideAmounts == true
                    val breakdownItems = snapshot.items.filtered(selectedType, currencyCode)

                    item {
                        StatsFiltersPanel(
                            accounts = state.accounts,
                            snapshot = snapshot,
                            selectedAccountId = selectedAccountId,
                            selectedPeriod = selectedPeriod,
                            selectedCurrencyCode = currencyCode,
                            selectedType = selectedType,
                            chartStyle = chartStyle,
                            onSelectAccount = onSelectAccount,
                            onSelectPeriod = onSelectPeriod,
                            onSelectCurrency = onSelectCurrency,
                            onSelectType = onSelectType,
                            onSelectChartStyle = onSelectChartStyle,
                        )
                    }

                    item {
                        StatsTotalsPanel(
                            snapshot = snapshot,
                            currencyCode = currencyCode,
                            hideAmounts = hideAmounts,
                        )
                    }

                    item {
                        StatsChart(
                            snapshot = snapshot,
                            currencyCode = currencyCode,
                            selectedType = selectedType,
                            chartStyle = chartStyle,
                            hideAmounts = hideAmounts,
                        )
                    }

                    if (breakdownItems.isEmpty()) {
                        item {
                            StatsEmptyState()
                        }
                    } else {
                        items(
                            items = breakdownItems,
                            key = { "${it.type.storageValue}-${it.currencyCode}-${it.categoryId}" },
                        ) { item ->
                            StatsCategoryRow(
                                item = item,
                                hideAmounts = hideAmounts,
                                onOpenHistory = {
                                    onOpenHistory(
                                        StatsDrilldownFilter(
                                            accountId = selectedAccountId,
                                            categoryId = item.categoryId,
                                            type = item.type,
                                            currencyCode = item.currencyCode,
                                            range = snapshot.range,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

data class StatsUiState(
    val isLoading: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val settings: MoneyTrackerSettings? = null,
    val snapshot: StatsSnapshot? = null,
    val error: StatsError? = null,
)

data class StatsDrilldownFilter(
    val accountId: Long?,
    val categoryId: Long,
    val type: StatsTransactionType,
    val currencyCode: String,
    val range: StatsRange,
)

enum class StatsError(@StringRes val messageResId: Int) {
    Generic(R.string.stats_error_generic),
}

@Composable
private fun StatsHeader() {
    Text(
        text = stringResource(R.string.stats_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.stats_subtitle),
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatsLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.stats_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StatsErrorCard(
    error: StatsError,
    onRetry: () -> Unit,
) {
    Card(
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
                Text(text = stringResource(R.string.stats_retry))
            }
        }
    }
}

@Composable
private fun StatsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.BarChart,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.stats_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.stats_empty_body),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsFiltersPanel(
    accounts: List<Account>,
    snapshot: StatsSnapshot,
    selectedAccountId: Long?,
    selectedPeriod: StatsPeriod,
    selectedCurrencyCode: String,
    selectedType: StatsTransactionType,
    chartStyle: StatsChartStylePreference,
    onSelectAccount: (Long?) -> Unit,
    onSelectPeriod: (StatsPeriod) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onSelectType: (StatsTransactionType) -> Unit,
    onSelectChartStyle: (StatsChartStylePreference) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatsFilterLabel(text = stringResource(R.string.stats_filter_accounts))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedAccountId == null,
                onClick = { onSelectAccount(null) },
                label = { Text(stringResource(R.string.stats_all_accounts)) },
                modifier = Modifier.testTag("stats-account-all"),
            )
            accounts.forEach { account ->
                FilterChip(
                    selected = selectedAccountId == account.id,
                    onClick = { onSelectAccount(account.id) },
                    label = {
                        Text(
                            text = account.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.testTag("stats-account-${account.id}"),
                )
            }
        }

        StatsFilterLabel(text = stringResource(R.string.stats_filter_period))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsPeriod.entries.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { onSelectPeriod(period) },
                    label = { Text(stringResource(period.labelResId)) },
                    modifier = Modifier.testTag("stats-period-${period.storageValue}"),
                )
            }
        }

        val currencies = snapshot.currencyCodes()
        if (currencies.size > 1) {
            StatsFilterLabel(text = stringResource(R.string.stats_filter_currency))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                currencies.forEach { currencyCode ->
                    FilterChip(
                        selected = selectedCurrencyCode == currencyCode,
                        onClick = { onSelectCurrency(currencyCode) },
                        label = { Text(currencyCode) },
                        modifier = Modifier.testTag("stats-currency-$currencyCode"),
                    )
                }
            }
        }

        StatsFilterLabel(text = stringResource(R.string.stats_filter_type))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsTransactionType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onSelectType(type) },
                    label = { Text(stringResource(type.labelResId)) },
                    modifier = Modifier.testTag("stats-type-${type.storageValue}"),
                )
            }
        }

        StatsFilterLabel(text = stringResource(R.string.stats_filter_chart_style))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsChartStylePreference.entries.forEach { style ->
                FilterChip(
                    selected = chartStyle == style,
                    onClick = { onSelectChartStyle(style) },
                    label = { Text(stringResource(style.labelResId)) },
                    modifier = Modifier.testTag("stats-chart-option-${style.persistedValue}"),
                )
            }
        }
    }
}

@Composable
private fun StatsFilterLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatsTotalsPanel(
    snapshot: StatsSnapshot,
    currencyCode: String,
    hideAmounts: Boolean,
) {
    val income = snapshot.items.total(StatsTransactionType.Income, currencyCode)
    val expense = snapshot.items.total(StatsTransactionType.Expense, currencyCode)
    val net = income - expense

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsMetricCard(
            label = stringResource(R.string.stats_income_total),
            value = income.formatMoney(currencyCode, hideAmounts),
            accent = MaterialTheme.colorScheme.primary,
        )
        StatsMetricCard(
            label = stringResource(R.string.stats_expense_total),
            value = expense.formatMoney(currencyCode, hideAmounts),
            accent = MaterialTheme.colorScheme.error,
        )
        StatsMetricCard(
            label = stringResource(R.string.stats_net_total),
            value = net.formatSignedMoney(currencyCode, hideAmounts),
            accent = if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun StatsMetricCard(
    label: String,
    value: String,
    accent: Color,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatsChart(
    snapshot: StatsSnapshot,
    currencyCode: String,
    selectedType: StatsTransactionType,
    chartStyle: StatsChartStylePreference,
    hideAmounts: Boolean,
) {
    val income = snapshot.items.total(StatsTransactionType.Income, currencyCode)
    val expense = snapshot.items.total(StatsTransactionType.Expense, currencyCode)
    val selectedItems = snapshot.items.filtered(selectedType, currencyCode)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val chartLabel = stringResource(chartStyle.labelResId)
    val selectedTypeLabel = stringResource(selectedType.labelResId)
    val incomeLabel = stringResource(R.string.stats_income_total)
    val expenseLabel = stringResource(R.string.stats_expense_total)
    val netLabel = stringResource(R.string.stats_net_total)
    val emptyLabel = stringResource(R.string.stats_empty_title)
    val transactionCountLabels = selectedItems.associate { item ->
        item.categoryId to stringResource(R.string.stats_transactions_count, item.transactionCount)
    }
    val chartDescription = when (chartStyle) {
        StatsChartStylePreference.Donut,
        StatsChartStylePreference.StackedBar,
        -> selectedItems.toCategoryChartDescription(
            chartLabel = chartLabel,
            selectedTypeLabel = selectedTypeLabel,
            transactionCountLabels = transactionCountLabels,
            hideAmounts = hideAmounts,
            emptyLabel = emptyLabel,
        )

        StatsChartStylePreference.DualBar,
        StatsChartStylePreference.ProfitBars,
        -> listOf(
            chartLabel,
            "$incomeLabel ${income.formatMoney(currencyCode, hideAmounts)}",
            "$expenseLabel ${expense.formatMoney(currencyCode, hideAmounts)}",
            "$netLabel ${(income - expense).formatSignedMoney(currencyCode, hideAmounts)}",
        ).joinToString(". ")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats-chart-${chartStyle.persistedValue}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (chartStyle) {
                        StatsChartStylePreference.Donut -> Icons.Filled.PieChart
                        StatsChartStylePreference.StackedBar -> Icons.Filled.BarChart
                        StatsChartStylePreference.DualBar -> Icons.Filled.BarChart
                        StatsChartStylePreference.ProfitBars -> Icons.Filled.TrendingUp
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(chartStyle.labelResId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            when (chartStyle) {
                StatsChartStylePreference.Donut -> DonutChart(
                    items = selectedItems,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .semantics { contentDescription = chartDescription },
                )

                StatsChartStylePreference.StackedBar -> StackedBarChart(
                    items = selectedItems,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .semantics { contentDescription = chartDescription },
                )

                StatsChartStylePreference.DualBar -> DualBarChart(
                    income = income,
                    expense = expense,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp)
                        .semantics { contentDescription = chartDescription },
                )

                StatsChartStylePreference.ProfitBars -> ProfitBarsChart(
                    income = income,
                    expense = expense,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .semantics { contentDescription = chartDescription },
                )
            }

            Text(
                text = when (chartStyle) {
                    StatsChartStylePreference.Donut,
                    StatsChartStylePreference.StackedBar -> {
                        val total = selectedItems.sumOf { it.totalCents }
                        total.formatMoney(currencyCode, hideAmounts)
                    }

                    StatsChartStylePreference.DualBar,
                    StatsChartStylePreference.ProfitBars -> {
                        (income - expense).formatSignedMoney(currencyCode, hideAmounts)
                    }
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (selectedItems.isEmpty()) {
                    stringResource(R.string.stats_empty_title)
                } else {
                    stringResource(selectedType.labelResId)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DonutChart(
    items: List<CategoryStat>,
    modifier: Modifier = Modifier,
) {
    val fallback = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val diameter = min(size.width, size.height)
        val strokeWidth = 28.dp.toPx()
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val chartSize = Size(diameter, diameter)
        val total = items.sumOf { it.totalCents }.coerceAtLeast(0L)

        if (total == 0L) {
            drawArc(
                color = emptyColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = chartSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            return@Canvas
        }

        var startAngle = -90f
        items.forEach { item ->
            val sweep = item.totalCents.toFloat() / total.toFloat() * 360f
            drawArc(
                color = item.categoryColor.toColorOrFallback(fallback),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = chartSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun StackedBarChart(
    items: List<CategoryStat>,
    modifier: Modifier = Modifier,
) {
    val fallback = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val total = items.sumOf { it.totalCents }.coerceAtLeast(0L)
        val barHeight = 28.dp.toPx()
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(14.dp.toPx(), 14.dp.toPx())

        if (total == 0L) {
            drawRoundRect(
                color = emptyColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, barHeight),
                cornerRadius = radius,
            )
            return@Canvas
        }

        var left = 0f
        items.forEach { item ->
            val width = size.width * item.totalCents.toFloat() / total.toFloat()
            drawRoundRect(
                color = item.categoryColor.toColorOrFallback(fallback),
                topLeft = Offset(left, top),
                size = Size(width.coerceAtLeast(2f), barHeight),
                cornerRadius = radius,
            )
            left += width
        }
    }
}

@Composable
private fun DualBarChart(
    income: Long,
    expense: Long,
    modifier: Modifier = Modifier,
) {
    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = MaterialTheme.colorScheme.error
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val maxValue = max(income, expense).coerceAtLeast(1L).toFloat()
        val barHeight = 24.dp.toPx()
        val radius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        val incomeWidth = size.width * income.toFloat() / maxValue
        val expenseWidth = size.width * expense.toFloat() / maxValue
        drawRoundRect(
            color = if (income == 0L) emptyColor else incomeColor,
            topLeft = Offset(0f, 12.dp.toPx()),
            size = Size(incomeWidth.coerceAtLeast(2f), barHeight),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = if (expense == 0L) emptyColor else expenseColor,
            topLeft = Offset(0f, 58.dp.toPx()),
            size = Size(expenseWidth.coerceAtLeast(2f), barHeight),
            cornerRadius = radius,
        )
    }
}

@Composable
private fun ProfitBarsChart(
    income: Long,
    expense: Long,
    modifier: Modifier = Modifier,
) {
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val neutralColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val net = income - expense
        val maxValue = max(max(income, expense), abs(net)).coerceAtLeast(1L).toFloat()
        val barHeight = 18.dp.toPx()
        val radius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
        val values = listOf(income to positiveColor, expense to negativeColor, abs(net) to if (net >= 0) positiveColor else negativeColor)
        values.forEachIndexed { index, (value, color) ->
            val width = size.width * value.toFloat() / maxValue
            drawRoundRect(
                color = if (value == 0L) neutralColor else color,
                topLeft = Offset(0f, 14.dp.toPx() + index * 38.dp.toPx()),
                size = Size(width.coerceAtLeast(2f), barHeight),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun StatsCategoryRow(
    item: CategoryStat,
    hideAmounts: Boolean,
    onOpenHistory: () -> Unit,
) {
    val fallback = MaterialTheme.colorScheme.primary
    val openHistoryDescription = stringResource(R.string.stats_open_history, item.categoryName)
    val rowDescription = listOf(
        item.categoryName,
        item.totalCents.formatMoney(item.currencyCode, hideAmounts),
        stringResource(R.string.stats_transactions_count, item.transactionCount),
        openHistoryDescription,
    ).joinToString(". ")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats-breakdown-row-${item.categoryId}")
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                role = Role.Button
            }
            .clickable(onClick = onOpenHistory),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(item.categoryColor.toColorOrFallback(fallback)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.categoryName.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 10.dp),
            ) {
                Text(
                    text = item.categoryName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(stringResource(R.string.stats_transactions_count, item.transactionCount))
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.totalCents.formatMoney(item.currencyCode, hideAmounts),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun StatsSnapshot.currencyCodes(): List<String> {
    return items.map { it.currencyCode }.distinct().sorted()
}

private fun StatsSnapshot.preferredCurrencyCode(preferred: String?): String {
    val currencies = currencyCodes()
    return preferred?.takeIf { it in currencies }
        ?: currencies.firstOrNull()
        ?: preferred
        ?: "USD"
}

private fun List<CategoryStat>.filtered(
    type: StatsTransactionType,
    currencyCode: String,
): List<CategoryStat> {
    return filter { it.type == type && it.currencyCode == currencyCode }
        .sortedWith(compareByDescending<CategoryStat> { it.totalCents }.thenBy { it.categoryName })
}

private fun List<CategoryStat>.total(
    type: StatsTransactionType,
    currencyCode: String,
): Long {
    return filter { it.type == type && it.currencyCode == currencyCode }
        .sumOf { it.totalCents }
}

private fun List<CategoryStat>.toCategoryChartDescription(
    chartLabel: String,
    selectedTypeLabel: String,
    transactionCountLabels: Map<Long, String>,
    hideAmounts: Boolean,
    emptyLabel: String,
): String {
    if (isEmpty()) {
        return "$chartLabel. $emptyLabel"
    }
    val categorySummary = joinToString(". ") { item ->
        val countLabel = transactionCountLabels.getValue(item.categoryId)
        "${item.categoryName}: ${item.totalCents.formatMoney(item.currencyCode, hideAmounts)}, $countLabel"
    }
    return "$chartLabel. $selectedTypeLabel. $categorySummary"
}

private fun Long.formatMoney(currencyCode: String, hideAmounts: Boolean): String {
    return if (hideAmounts) {
        "**** $currencyCode"
    } else {
        "${MoneyParser.formatPlainCents(abs(this))} $currencyCode"
    }
}

private fun Long.formatSignedMoney(currencyCode: String, hideAmounts: Boolean): String {
    if (hideAmounts) {
        return "**** $currencyCode"
    }
    val sign = when {
        this > 0 -> "+"
        this < 0 -> "-"
        else -> ""
    }
    return "$sign${MoneyParser.formatPlainCents(abs(this))} $currencyCode"
}

private fun String.toColorOrFallback(fallback: Color): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }
        .getOrDefault(fallback)
}

private val StatsPeriod.labelResId: Int
    @StringRes
    get() = when (this) {
        StatsPeriod.Today -> R.string.stats_today
        StatsPeriod.Week -> R.string.stats_week
        StatsPeriod.Month -> R.string.stats_month
        StatsPeriod.LastMonth -> R.string.stats_last_month
    }

private val StatsTransactionType.labelResId: Int
    @StringRes
    get() = when (this) {
        StatsTransactionType.Expense -> R.string.stats_expense
        StatsTransactionType.Income -> R.string.stats_income
    }

private val StatsChartStylePreference.labelResId: Int
    @StringRes
    get() = when (this) {
        StatsChartStylePreference.Donut -> R.string.stats_style_donut
        StatsChartStylePreference.StackedBar -> R.string.stats_style_stacked_bar
        StatsChartStylePreference.DualBar -> R.string.stats_style_dual_bar
        StatsChartStylePreference.ProfitBars -> R.string.stats_style_profit_bars
    }

@Preview(showBackground = true, widthDp = 320, heightDp = 840)
@Composable
private fun StatsScreenPreview() {
    MoneyTrackerTheme {
        StatsScreen(
            state = StatsUiState(
                accounts = previewAccounts,
                settings = MoneyTrackerSettings(profileId = 1),
                snapshot = previewSnapshot,
            ),
            selectedAccountId = 1,
            selectedPeriod = StatsPeriod.Month,
            selectedCurrencyCode = "USD",
            selectedType = StatsTransactionType.Expense,
            chartStyle = StatsChartStylePreference.Donut,
            onSelectAccount = {},
            onSelectPeriod = {},
            onSelectCurrency = {},
            onSelectType = {},
            onSelectChartStyle = {},
            onOpenHistory = {},
            onRetry = {},
        )
    }
}

private val previewAccounts = listOf(
    Account(
        id = 1,
        profileId = 1,
        name = "Main card",
        icon = "card",
        color = "#4F46E5",
        type = AccountType.Checking,
        currencyCode = "USD",
        isDefault = true,
        includeInTotal = true,
        balanceCents = 120_000,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    ),
)

private val previewSnapshot = StatsSnapshot(
    profileId = 1,
    period = "month",
    range = StatsRange(1_782_864_000_000L, 1_785_542_400_000L),
    items = listOf(
        CategoryStat(
            categoryId = 1,
            categoryName = "Groceries and household supplies",
            categoryIcon = "cart",
            categoryColor = "#EF4444",
            type = StatsTransactionType.Expense,
            totalCents = 42_500,
            transactionCount = 12,
            currencyCode = "USD",
        ),
        CategoryStat(
            categoryId = 2,
            categoryName = "Salary",
            categoryIcon = "salary",
            categoryColor = "#10B981",
            type = StatsTransactionType.Income,
            totalCents = 280_000,
            transactionCount = 1,
            currencyCode = "USD",
        ),
    ),
)
