package dev.horex.moneytracker.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.horex.moneytracker.core.common.AppBootstrapContent
import dev.horex.moneytracker.core.designsystem.component.MoneyTrackerPlaceholderScreen
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme

@Composable
fun HomeRoute(
    content: AppBootstrapContent? = null,
) {
    val resolvedContent = content ?: AppBootstrapContent(
        title = stringResource(R.string.feature_home_title),
        subtitle = stringResource(R.string.feature_home_subtitle),
    )

    MoneyTrackerPlaceholderScreen(
        title = resolvedContent.title,
        subtitle = resolvedContent.subtitle,
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeRoutePreview() {
    MoneyTrackerTheme {
        HomeRoute()
    }
}
