package dev.horex.moneytracker.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.horex.moneytracker.core.common.AppBootstrapContent
import dev.horex.moneytracker.core.common.AppBootstrapDefaults
import dev.horex.moneytracker.core.designsystem.component.MoneyTrackerPlaceholderScreen
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme

@Composable
fun HomeRoute(
    content: AppBootstrapContent = AppBootstrapDefaults.content,
) {
    MoneyTrackerPlaceholderScreen(
        title = content.title,
        subtitle = content.subtitle,
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeRoutePreview() {
    MoneyTrackerTheme {
        HomeRoute()
    }
}
