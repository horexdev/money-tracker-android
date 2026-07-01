package dev.horex.moneytracker.core.navigation

object MoneyTrackerRoutes {
    const val Dashboard = "dashboard"
    const val AddTransaction = "add-transaction"
    const val History = "history"
    const val Stats = "stats"
    const val More = "more"
    const val Settings = "settings"
    const val Categories = "categories"
    const val Budgets = "budgets"
    const val Recurring = "recurring"
    const val Templates = "templates"
    const val Savings = "savings"
    const val Export = "export"
    const val Accounts = "accounts"

    val all: List<String> = listOf(
        Dashboard,
        AddTransaction,
        History,
        Stats,
        More,
        Settings,
        Categories,
        Budgets,
        Recurring,
        Templates,
        Savings,
        Export,
        Accounts,
    )
}

enum class MoneyTrackerTopLevelDestination(
    val route: String,
) {
    Dashboard(MoneyTrackerRoutes.Dashboard),
    History(MoneyTrackerRoutes.History),
    AddTransaction(MoneyTrackerRoutes.AddTransaction),
    Stats(MoneyTrackerRoutes.Stats),
    More(MoneyTrackerRoutes.More),
}
