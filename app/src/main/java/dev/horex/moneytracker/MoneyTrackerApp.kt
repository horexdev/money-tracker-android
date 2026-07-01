package dev.horex.moneytracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.horex.moneytracker.core.common.AppBootstrapContent
import dev.horex.moneytracker.core.navigation.MoneyTrackerRoutes
import dev.horex.moneytracker.core.navigation.MoneyTrackerTopLevelDestination
import dev.horex.moneytracker.feature.home.HomeRoute

@Composable
fun MoneyTrackerApp() {
    val navController = rememberNavController()

    MaterialTheme {
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
            HomeRoute(
                content = AppBootstrapContent(
                    title = "Dashboard",
                    subtitle = "Root navigation is ready.",
                ),
            )
        }
        composable(MoneyTrackerRoutes.History) {
            PlaceholderRoute(
                title = "History",
                subtitle = "Transaction history route is registered.",
            )
        }
        composable(MoneyTrackerRoutes.AddTransaction) {
            PlaceholderRoute(
                title = "Add transaction",
                subtitle = "Transaction creation route is registered.",
            )
        }
        composable(MoneyTrackerRoutes.Stats) {
            PlaceholderRoute(
                title = "Stats",
                subtitle = "Analytics route is registered.",
            )
        }
        composable(MoneyTrackerRoutes.More) {
            PlaceholderRoute(
                title = "More",
                subtitle = "Secondary routes will attach here.",
            )
        }
        composable(MoneyTrackerRoutes.Settings) {
            PlaceholderRoute("Settings", "Settings route is registered.")
        }
        composable(MoneyTrackerRoutes.Categories) {
            PlaceholderRoute("Categories", "Categories route is registered.")
        }
        composable(MoneyTrackerRoutes.Budgets) {
            PlaceholderRoute("Budgets", "Budgets route is registered.")
        }
        composable(MoneyTrackerRoutes.Recurring) {
            PlaceholderRoute("Recurring", "Recurring transactions route is registered.")
        }
        composable(MoneyTrackerRoutes.Templates) {
            PlaceholderRoute("Templates", "Transaction templates route is registered.")
        }
        composable(MoneyTrackerRoutes.Savings) {
            PlaceholderRoute("Savings", "Savings goals route is registered.")
        }
        composable(MoneyTrackerRoutes.Export) {
            PlaceholderRoute("Export", "Backup and export route is registered.")
        }
        composable(MoneyTrackerRoutes.Accounts) {
            PlaceholderRoute("Accounts", "Accounts route is registered.")
        }
    }
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

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigateToTopLevelDestination(destination)
                },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(text = destination.label)
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

@Composable
private fun PlaceholderRoute(
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class AppTopLevelDestination(
    val destination: MoneyTrackerTopLevelDestination,
    val label: String,
    val icon: ImageVector,
) {
    val route: String = destination.route
}

private val topLevelDestinations = listOf(
    AppTopLevelDestination(
        destination = MoneyTrackerTopLevelDestination.Dashboard,
        label = "Home",
        icon = Icons.Filled.Home,
    ),
    AppTopLevelDestination(
        destination = MoneyTrackerTopLevelDestination.History,
        label = "History",
        icon = Icons.Filled.History,
    ),
    AppTopLevelDestination(
        destination = MoneyTrackerTopLevelDestination.AddTransaction,
        label = "Add",
        icon = Icons.Filled.Add,
    ),
    AppTopLevelDestination(
        destination = MoneyTrackerTopLevelDestination.Stats,
        label = "Stats",
        icon = Icons.Filled.BarChart,
    ),
    AppTopLevelDestination(
        destination = MoneyTrackerTopLevelDestination.More,
        label = "More",
        icon = Icons.Filled.MoreHoriz,
    ),
)

@Preview(showBackground = true)
@Composable
private fun MoneyTrackerAppPreview() {
    MoneyTrackerApp()
}
