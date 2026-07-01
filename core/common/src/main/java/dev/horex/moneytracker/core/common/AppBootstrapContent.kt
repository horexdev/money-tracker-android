package dev.horex.moneytracker.core.common

data class AppBootstrapContent(
    val title: String,
    val subtitle: String,
)

object AppBootstrapDefaults {
    val content = AppBootstrapContent(
        title = "Money Tracker",
        subtitle = "Android bootstrap is ready.",
    )
}
