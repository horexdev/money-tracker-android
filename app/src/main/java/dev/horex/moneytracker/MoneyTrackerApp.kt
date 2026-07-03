package dev.horex.moneytracker

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.horex.moneytracker.backup.MoneyTrackerBackupDocumentRepository
import dev.horex.moneytracker.core.accounts.AccountsRepository
import dev.horex.moneytracker.core.balance.BalancesRepository
import dev.horex.moneytracker.core.budgets.BudgetsRepository
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.currency.CurrencyRatesRepository
import dev.horex.moneytracker.core.designsystem.component.MoneyTrackerPlaceholderScreen
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerThemeMode
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.database.profile.LocalProfileRepository
import dev.horex.moneytracker.core.navigation.MoneyTrackerRoutes
import dev.horex.moneytracker.core.navigation.MoneyTrackerTopLevelDestination
import dev.horex.moneytracker.core.notifications.NotificationPermissionStatus
import dev.horex.moneytracker.core.preferences.AppPreferences
import dev.horex.moneytracker.core.preferences.AppPreferencesRepository
import dev.horex.moneytracker.core.preferences.AppThemePreference
import dev.horex.moneytracker.core.preferences.SettingsRepository
import dev.horex.moneytracker.core.recurring.RecurringTransactionsRepository
import dev.horex.moneytracker.core.stats.StatsRepository
import dev.horex.moneytracker.core.stats.StatsTransactionType
import dev.horex.moneytracker.core.templates.TransactionTemplatesRepository
import dev.horex.moneytracker.core.transactions.TransactionType
import dev.horex.moneytracker.core.transactions.TransactionsRepository
import dev.horex.moneytracker.csv.MoneyTrackerCsvDocumentRepository
import dev.horex.moneytracker.feature.accounts.AccountsRoute
import dev.horex.moneytracker.feature.addtransaction.AddTransactionRoute
import dev.horex.moneytracker.feature.budgets.BudgetsRoute
import dev.horex.moneytracker.feature.categories.CategoriesRoute
import dev.horex.moneytracker.feature.home.HomeRoute
import dev.horex.moneytracker.feature.history.HistoryFilters
import dev.horex.moneytracker.feature.history.HistoryRoute
import dev.horex.moneytracker.feature.recurring.RecurringRoute
import dev.horex.moneytracker.feature.stats.StatsRoute
import dev.horex.moneytracker.feature.templates.TemplatesRoute

@Composable
fun MoneyTrackerApp(
    localProfileBootstrapper: LocalProfileBootstrapper? = null,
    accountsRepository: AccountsRepository? = null,
    balancesRepository: BalancesRepository? = null,
    budgetsRepository: BudgetsRepository? = null,
    categoriesRepository: CategoriesRepository? = null,
    currencyRatesRepository: CurrencyRatesRepository? = null,
    settingsRepository: SettingsRepository? = null,
    recurringRepository: RecurringTransactionsRepository? = null,
    statsRepository: StatsRepository? = null,
    transactionTemplatesRepository: TransactionTemplatesRepository? = null,
    transactionsRepository: TransactionsRepository? = null,
    localProfileRepository: LocalProfileRepository? = null,
    appPreferencesRepository: AppPreferencesRepository? = null,
    backupDocumentRepository: MoneyTrackerBackupDocumentRepository? = null,
    csvDocumentRepository: MoneyTrackerCsvDocumentRepository? = null,
    notificationPermissionStatusProvider: (() -> NotificationPermissionStatus)? = null,
    areNotificationsEnabledProvider: (() -> Boolean)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
) {
    val navController = rememberNavController()
    var historyInitialFilters by remember { mutableStateOf(HistoryFilters()) }
    val fallbackPreferencesState = remember { mutableStateOf(AppPreferences()) }
    val appPreferencesState = appPreferencesRepository?.preferences?.collectAsState(
        initial = AppPreferences(),
    ) ?: fallbackPreferencesState

    LaunchedEffect(localProfileBootstrapper) {
        localProfileBootstrapper?.ensureActiveProfile()
    }

    MoneyTrackerTheme(themeMode = appPreferencesState.value.theme.toThemeMode()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                MoneyTrackerBottomBar(
                    navController = navController,
                    onDestinationSelected = { destination ->
                        if (destination.destination == MoneyTrackerTopLevelDestination.History) {
                            historyInitialFilters = HistoryFilters()
                        }
                    },
                )
            },
        ) { innerPadding ->
            MoneyTrackerNavHost(
                navController = navController,
                localProfileBootstrapper = localProfileBootstrapper,
                accountsRepository = accountsRepository,
                balancesRepository = balancesRepository,
                budgetsRepository = budgetsRepository,
                categoriesRepository = categoriesRepository,
                currencyRatesRepository = currencyRatesRepository,
                settingsRepository = settingsRepository,
                recurringRepository = recurringRepository,
                statsRepository = statsRepository,
                transactionTemplatesRepository = transactionTemplatesRepository,
                transactionsRepository = transactionsRepository,
                localProfileRepository = localProfileRepository,
                appPreferencesRepository = appPreferencesRepository,
                backupDocumentRepository = backupDocumentRepository,
                csvDocumentRepository = csvDocumentRepository,
                notificationPermissionStatusProvider = notificationPermissionStatusProvider,
                areNotificationsEnabledProvider = areNotificationsEnabledProvider,
                onOpenNotificationSettings = onOpenNotificationSettings,
                historyInitialFilters = historyInitialFilters,
                onHistoryInitialFiltersChange = { historyInitialFilters = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun MoneyTrackerNavHost(
    navController: NavHostController,
    localProfileBootstrapper: LocalProfileBootstrapper?,
    accountsRepository: AccountsRepository?,
    balancesRepository: BalancesRepository?,
    budgetsRepository: BudgetsRepository?,
    categoriesRepository: CategoriesRepository?,
    currencyRatesRepository: CurrencyRatesRepository?,
    settingsRepository: SettingsRepository?,
    recurringRepository: RecurringTransactionsRepository?,
    statsRepository: StatsRepository?,
    transactionTemplatesRepository: TransactionTemplatesRepository?,
    transactionsRepository: TransactionsRepository?,
    localProfileRepository: LocalProfileRepository?,
    appPreferencesRepository: AppPreferencesRepository?,
    backupDocumentRepository: MoneyTrackerBackupDocumentRepository?,
    csvDocumentRepository: MoneyTrackerCsvDocumentRepository?,
    notificationPermissionStatusProvider: (() -> NotificationPermissionStatus)?,
    areNotificationsEnabledProvider: (() -> Boolean)?,
    onOpenNotificationSettings: (() -> Unit)?,
    historyInitialFilters: HistoryFilters,
    onHistoryInitialFiltersChange: (HistoryFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = MoneyTrackerRoutes.Dashboard,
        modifier = modifier,
    ) {
        composable(MoneyTrackerRoutes.Dashboard) {
            if (
                localProfileBootstrapper != null &&
                balancesRepository != null &&
                transactionsRepository != null
            ) {
                HomeRoute(
                    localProfileBootstrapper = localProfileBootstrapper,
                    balancesRepository = balancesRepository,
                    transactionsRepository = transactionsRepository,
                    transactionTemplatesRepository = transactionTemplatesRepository,
                    onAddTransaction = {
                        navController.navigate(MoneyTrackerRoutes.AddTransaction) {
                            launchSingleTop = true
                        }
                    },
                    onOpenHistory = {
                        onHistoryInitialFiltersChange(HistoryFilters())
                        navController.navigate(MoneyTrackerRoutes.History) {
                            launchSingleTop = true
                        }
                    },
                    onOpenStats = {
                        navController.navigate(MoneyTrackerRoutes.Stats) {
                            launchSingleTop = true
                        }
                    },
                )
            } else {
                HomeRoute()
            }
        }
        composable(MoneyTrackerRoutes.History) {
            if (
                localProfileBootstrapper != null &&
                accountsRepository != null &&
                categoriesRepository != null &&
                transactionsRepository != null
            ) {
                HistoryRoute(
                    localProfileBootstrapper = localProfileBootstrapper,
                    transactionsRepository = transactionsRepository,
                    accountsRepository = accountsRepository,
                    categoriesRepository = categoriesRepository,
                    initialFilters = historyInitialFilters,
                )
            } else {
                LocalizedPlaceholderScreen(
                    titleResId = R.string.history_title,
                    subtitleResId = R.string.history_placeholder_subtitle,
                )
            }
        }
        composable(MoneyTrackerRoutes.AddTransaction) {
            if (
                localProfileBootstrapper != null &&
                accountsRepository != null &&
                categoriesRepository != null &&
                transactionsRepository != null
            ) {
                AddTransactionRoute(
                    localProfileBootstrapper = localProfileBootstrapper,
                    transactionsRepository = transactionsRepository,
                    accountsRepository = accountsRepository,
                    categoriesRepository = categoriesRepository,
                    transactionTemplatesRepository = transactionTemplatesRepository,
                    onTransactionSaved = {
                        onHistoryInitialFiltersChange(HistoryFilters())
                        navController.navigate(MoneyTrackerRoutes.History) {
                            launchSingleTop = true
                        }
                    },
                )
            } else {
                LocalizedPlaceholderScreen(
                    titleResId = R.string.add_transaction_title,
                    subtitleResId = R.string.add_transaction_placeholder_subtitle,
                )
            }
        }
        composable(MoneyTrackerRoutes.Stats) {
            if (
                localProfileBootstrapper != null &&
                accountsRepository != null &&
                settingsRepository != null &&
                statsRepository != null
            ) {
                StatsRoute(
                    localProfileBootstrapper = localProfileBootstrapper,
                    accountsRepository = accountsRepository,
                    settingsRepository = settingsRepository,
                    statsRepository = statsRepository,
                    onOpenHistory = { drilldown ->
                        onHistoryInitialFiltersChange(
                            HistoryFilters(
                                accountId = drilldown.accountId,
                                categoryId = drilldown.categoryId,
                                transactionType = drilldown.type.toTransactionType(),
                                currencyCode = drilldown.currencyCode,
                                fromEpochMillis = drilldown.range.fromEpochMillisInclusive,
                                toEpochMillis = (drilldown.range.toEpochMillisExclusive - 1)
                                    .coerceAtLeast(drilldown.range.fromEpochMillisInclusive),
                            ),
                        )
                        navController.navigate(MoneyTrackerRoutes.History) {
                            launchSingleTop = true
                        }
                    },
                )
            } else {
                LocalizedPlaceholderScreen(
                    titleResId = R.string.stats_title,
                    subtitleResId = R.string.stats_placeholder_subtitle,
                )
            }
        }
        composable(MoneyTrackerRoutes.More) {
            MoreRoute(
                onOpenAccounts = {
                    navController.navigate(MoneyTrackerRoutes.Accounts)
                },
                onOpenBudgets = {
                    navController.navigate(MoneyTrackerRoutes.Budgets)
                },
                onOpenRecurring = {
                    navController.navigate(MoneyTrackerRoutes.Recurring)
                },
                onOpenTemplates = {
                    navController.navigate(MoneyTrackerRoutes.Templates)
                },
                onOpenCategories = {
                    navController.navigate(MoneyTrackerRoutes.Categories)
                },
                onOpenSettings = {
                    navController.navigate(MoneyTrackerRoutes.Settings)
                },
                onOpenExport = {
                    navController.navigate(MoneyTrackerRoutes.Export)
                },
            )
        }
        composable(MoneyTrackerRoutes.Settings) {
            if (
                settingsRepository != null &&
                localProfileRepository != null &&
                currencyRatesRepository != null
            ) {
                SettingsRoute(
                    settingsRepository = settingsRepository,
                    localProfileRepository = localProfileRepository,
                    currencyRatesRepository = currencyRatesRepository,
                    appPreferencesRepository = appPreferencesRepository,
                    notificationPermissionStatusProvider = notificationPermissionStatusProvider ?: {
                        NotificationPermissionStatus.NotRequired
                    },
                    areNotificationsEnabledProvider = areNotificationsEnabledProvider ?: { true },
                    onOpenNotificationSettings = onOpenNotificationSettings ?: {},
                    onOpenImportExport = {
                        navController.navigate(MoneyTrackerRoutes.Export) {
                            launchSingleTop = true
                        }
                    },
                )
            } else {
                LocalizedPlaceholderScreen(
                    titleResId = R.string.settings_title,
                    subtitleResId = R.string.settings_placeholder_subtitle,
                )
            }
        }
        composable(MoneyTrackerRoutes.Categories) {
            if (
                localProfileBootstrapper != null &&
                categoriesRepository != null
            ) {
                CategoriesRoute(
                    localProfileBootstrapper = localProfileBootstrapper,
                    categoriesRepository = categoriesRepository,
                )
            } else {
                LocalizedPlaceholderScreen(
                    titleResId = R.string.categories_title,
                    subtitleResId = R.string.categories_placeholder_subtitle,
                )
            }
        }
        composable(MoneyTrackerRoutes.Budgets) {
            if (
                localProfileBootstrapper != null &&
                budgetsRepository != null &&
                categoriesRepository != null
            ) {
                BudgetsRoute(
                    localProfileBootstrapper = localProfileBootstrapper,
                    budgetsRepository = budgetsRepository,
                    categoriesRepository = categoriesRepository,
                )
            } else {
                LocalizedPlaceholderScreen(
                    titleResId = R.string.budgets_title,
                    subtitleResId = R.string.budgets_placeholder_subtitle,
                )
            }
        }
        composable(MoneyTrackerRoutes.Recurring) {
            if (
                localProfileBootstrapper != null &&
                recurringRepository != null &&
                accountsRepository != null &&
                categoriesRepository != null
            ) {
                RecurringRoute(
                    localProfileBootstrapper = localProfileBootstrapper,
                    recurringRepository = recurringRepository,
                    accountsRepository = accountsRepository,
                    categoriesRepository = categoriesRepository,
                )
            } else {
                LocalizedPlaceholderScreen(
                    titleResId = R.string.recurring_title,
                    subtitleResId = R.string.recurring_placeholder_subtitle,
                )
            }
        }
        composable(MoneyTrackerRoutes.Templates) {
            if (
                localProfileBootstrapper != null &&
                transactionTemplatesRepository != null &&
                accountsRepository != null &&
                categoriesRepository != null
            ) {
                TemplatesRoute(
                    localProfileBootstrapper = localProfileBootstrapper,
                    templatesRepository = transactionTemplatesRepository,
                    accountsRepository = accountsRepository,
                    categoriesRepository = categoriesRepository,
                )
            } else {
                LocalizedPlaceholderScreen(
                    titleResId = R.string.templates_title,
                    subtitleResId = R.string.templates_placeholder_subtitle,
                )
            }
        }
        composable(MoneyTrackerRoutes.Savings) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.savings_title,
                subtitleResId = R.string.savings_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.Export) {
            if (
                localProfileRepository != null &&
                backupDocumentRepository != null &&
                csvDocumentRepository != null
            ) {
                ImportExportRoute(
                    localProfileRepository = localProfileRepository,
                    backupDocumentRepository = backupDocumentRepository,
                    csvDocumentRepository = csvDocumentRepository,
                )
            } else {
                LocalizedPlaceholderScreen(
                    titleResId = R.string.export_title,
                    subtitleResId = R.string.export_placeholder_subtitle,
                )
            }
        }
        composable(MoneyTrackerRoutes.Accounts) {
            if (
                localProfileBootstrapper != null &&
                accountsRepository != null &&
                transactionsRepository != null
            ) {
                AccountsRoute(
                    localProfileBootstrapper = localProfileBootstrapper,
                    accountsRepository = accountsRepository,
                    transactionsRepository = transactionsRepository,
                )
            } else {
                LocalizedPlaceholderScreen(
                    titleResId = R.string.accounts_title,
                    subtitleResId = R.string.accounts_placeholder_subtitle,
                )
            }
        }
    }
}

@Composable
private fun MoreRoute(
    onOpenAccounts: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        item {
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAccounts),
                headlineContent = {
                    Text(text = stringResource(R.string.accounts_title))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.accounts_more_subtitle))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                    )
                },
            )
            HorizontalDivider()
        }
        item {
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenRecurring),
                headlineContent = {
                    Text(text = stringResource(R.string.recurring_title))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.recurring_more_subtitle))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                    )
                },
            )
            HorizontalDivider()
        }
        item {
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTemplates),
                headlineContent = {
                    Text(text = stringResource(R.string.templates_title))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.templates_more_subtitle))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                    )
                },
            )
            HorizontalDivider()
        }
        item {
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings),
                headlineContent = {
                    Text(text = stringResource(R.string.settings_title))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.settings_more_subtitle))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                    )
                },
            )
            HorizontalDivider()
        }
        item {
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCategories),
                headlineContent = {
                    Text(text = stringResource(R.string.categories_title))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.categories_more_subtitle))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                    )
                },
            )
            HorizontalDivider()
        }
        item {
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenBudgets),
                headlineContent = {
                    Text(text = stringResource(R.string.budgets_title))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.budgets_more_subtitle))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Savings,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                    )
                },
            )
            HorizontalDivider()
        }
        item {
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenExport),
                headlineContent = {
                    Text(text = stringResource(R.string.export_title))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.export_more_subtitle))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.ImportExport,
                        contentDescription = null,
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                    )
                },
            )
            HorizontalDivider()
        }
        items(morePlaceholderRoutes) { route ->
            ListItem(
                headlineContent = {
                    Text(text = stringResource(route.titleResId))
                },
                supportingContent = {
                    Text(text = stringResource(route.subtitleResId))
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun LocalizedPlaceholderScreen(
    @StringRes titleResId: Int,
    @StringRes subtitleResId: Int,
) {
    MoneyTrackerPlaceholderScreen(
        title = stringResource(titleResId),
        subtitle = stringResource(subtitleResId),
    )
}

@Composable
private fun MoneyTrackerBottomBar(
    navController: NavHostController,
    onDestinationSelected: (AppTopLevelDestination) -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        topLevelDestinations.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == destination.route
            } == true
            val label = stringResource(destination.labelResId)

            NavigationBarItem(
                selected = selected,
                onClick = {
                    onDestinationSelected(destination)
                    navController.navigateToTopLevelDestination(destination)
                },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = label,
                    )
                },
                label = {
                    Text(text = label)
                },
            )
        }
    }
}

private fun NavHostController.navigateToTopLevelDestination(
    destination: AppTopLevelDestination,
) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun StatsTransactionType.toTransactionType(): TransactionType {
    return when (this) {
        StatsTransactionType.Expense -> TransactionType.Expense
        StatsTransactionType.Income -> TransactionType.Income
    }
}

private fun AppThemePreference.toThemeMode(): MoneyTrackerThemeMode {
    return when (this) {
        AppThemePreference.System -> MoneyTrackerThemeMode.System
        AppThemePreference.Light -> MoneyTrackerThemeMode.Light
        AppThemePreference.Dark -> MoneyTrackerThemeMode.Dark
    }
}

private data class AppTopLevelDestination(
    val destination: MoneyTrackerTopLevelDestination,
    @StringRes val labelResId: Int,
    val icon: ImageVector,
) {
    val route: String = destination.route
}

private val topLevelDestinations = listOf(
    AppTopLevelDestination(
        destination = MoneyTrackerTopLevelDestination.Dashboard,
        labelResId = R.string.tab_home,
        icon = Icons.Filled.Home,
    ),
    AppTopLevelDestination(
        destination = MoneyTrackerTopLevelDestination.History,
        labelResId = R.string.tab_history,
        icon = Icons.Filled.History,
    ),
    AppTopLevelDestination(
        destination = MoneyTrackerTopLevelDestination.AddTransaction,
        labelResId = R.string.tab_add,
        icon = Icons.Filled.Add,
    ),
    AppTopLevelDestination(
        destination = MoneyTrackerTopLevelDestination.Stats,
        labelResId = R.string.tab_stats,
        icon = Icons.Filled.BarChart,
    ),
    AppTopLevelDestination(
        destination = MoneyTrackerTopLevelDestination.More,
        labelResId = R.string.tab_more,
        icon = Icons.Filled.MoreHoriz,
    ),
)

private data class MorePlaceholderRoute(
    @StringRes val titleResId: Int,
    @StringRes val subtitleResId: Int,
)

private val morePlaceholderRoutes = listOf(
    MorePlaceholderRoute(R.string.savings_title, R.string.savings_placeholder_subtitle),
)

@Preview(showBackground = true)
@Composable
private fun MoneyTrackerAppPreview() {
    MoneyTrackerApp()
}
