package dev.horex.moneytracker

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.horex.moneytracker.core.designsystem.component.MoneyTrackerPlaceholderScreen
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerThemeMode
import dev.horex.moneytracker.core.navigation.MoneyTrackerRoutes
import dev.horex.moneytracker.core.navigation.MoneyTrackerTopLevelDestination
import dev.horex.moneytracker.feature.home.HomeRoute

@Composable
fun MoneyTrackerApp() {
    val navController = rememberNavController()

    MoneyTrackerTheme(themeMode = MoneyTrackerThemeMode.System) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                MoneyTrackerBottomBar(navController = navController)
            },
        ) { innerPadding ->
            MoneyTrackerNavHost(
                navController = navController,
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
            LocalizedPlaceholderScreen(
                titleResId = R.string.history_title,
                subtitleResId = R.string.history_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.AddTransaction) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.add_transaction_title,
                subtitleResId = R.string.add_transaction_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.Stats) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.stats_title,
                subtitleResId = R.string.stats_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.More) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.more_title,
                subtitleResId = R.string.more_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.Settings) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.settings_title,
                subtitleResId = R.string.settings_placeholder_subtitle,
            )
        }
        composable(MoneyTrackerRoutes.Categories) {
            LocalizedPlaceholderScreen(
                titleResId = R.string.categories_title,
                subtitleResId = R.string.categories_placeholder_subtitle,
            )
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
            LocalizedPlaceholderScreen(
                titleResId = R.string.accounts_title,
                subtitleResId = R.string.accounts_placeholder_subtitle,
            )
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

@Preview(showBackground = true)
@Composable
private fun MoneyTrackerAppPreview() {
    MoneyTrackerApp()
}
