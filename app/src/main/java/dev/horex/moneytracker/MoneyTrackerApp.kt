package dev.horex.moneytracker

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import dev.horex.moneytracker.core.accounts.AccountsRepository
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.designsystem.component.MoneyTrackerPlaceholderScreen
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerThemeMode
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.navigation.MoneyTrackerRoutes
import dev.horex.moneytracker.core.navigation.MoneyTrackerTopLevelDestination
import dev.horex.moneytracker.core.transactions.TransactionsRepository
import dev.horex.moneytracker.feature.accounts.AccountsRoute
import dev.horex.moneytracker.feature.addtransaction.AddTransactionRoute
import dev.horex.moneytracker.feature.categories.CategoriesRoute
import dev.horex.moneytracker.feature.home.HomeRoute
import dev.horex.moneytracker.feature.history.HistoryRoute

@Composable
fun MoneyTrackerApp(
    localProfileBootstrapper: LocalProfileBootstrapper? = null,
    accountsRepository: AccountsRepository? = null,
    categoriesRepository: CategoriesRepository? = null,
    transactionsRepository: TransactionsRepository? = null,
) {
    val navController = rememberNavController()

    LaunchedEffect(localProfileBootstrapper) {
        localProfileBootstrapper?.ensureActiveProfile()
    }

    MoneyTrackerTheme(themeMode = MoneyTrackerThemeMode.System) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                MoneyTrackerBottomBar(navController = navController)
            },
        ) { innerPadding ->
            MoneyTrackerNavHost(
                navController = navController,
                localProfileBootstrapper = localProfileBootstrapper,
                accountsRepository = accountsRepository,
                categoriesRepository = categoriesRepository,
                transactionsRepository = transactionsRepository,
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
    categoriesRepository: CategoriesRepository?,
    transactionsRepository: TransactionsRepository?,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = MoneyTrackerRoutes.Dashboard,
        modifier = modifier,
    ) {
        composable(MoneyTrackerRoutes.Dashboard) {
            HomeRoute()
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
                    onTransactionSaved = {
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
            LocalizedPlaceholderScreen(
                titleResId = R.string.stats_title,
                subtitleResId = R.string.stats_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.More) {
            MoreRoute(
                onOpenAccounts = {
                    navController.navigate(MoneyTrackerRoutes.Accounts)
                },
                onOpenCategories = {
                    navController.navigate(MoneyTrackerRoutes.Categories)
                },
            )
        }
        composable(MoneyTrackerRoutes.Settings) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.settings_title,
                subtitleResId = R.string.settings_placeholder_subtitle,
            )
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
            LocalizedPlaceholderScreen(
                titleResId = R.string.budgets_title,
                subtitleResId = R.string.budgets_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.Recurring) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.recurring_title,
                subtitleResId = R.string.recurring_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.Templates) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.templates_title,
                subtitleResId = R.string.templates_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.Savings) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.savings_title,
                subtitleResId = R.string.savings_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.Export) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.export_title,
                subtitleResId = R.string.export_placeholder_subtitle,
            )
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
    onOpenCategories: () -> Unit,
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
    MorePlaceholderRoute(R.string.settings_title, R.string.settings_placeholder_subtitle),
    MorePlaceholderRoute(R.string.budgets_title, R.string.budgets_placeholder_subtitle),
    MorePlaceholderRoute(R.string.recurring_title, R.string.recurring_placeholder_subtitle),
    MorePlaceholderRoute(R.string.templates_title, R.string.templates_placeholder_subtitle),
    MorePlaceholderRoute(R.string.savings_title, R.string.savings_placeholder_subtitle),
    MorePlaceholderRoute(R.string.export_title, R.string.export_placeholder_subtitle),
)

@Preview(showBackground = true)
@Composable
private fun MoneyTrackerAppPreview() {
    MoneyTrackerApp()
}
