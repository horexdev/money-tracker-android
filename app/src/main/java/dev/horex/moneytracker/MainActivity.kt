package dev.horex.moneytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private val appContainer by lazy {
        MoneyTrackerAppContainer(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoneyTrackerApp(
                localProfileBootstrapper = appContainer.localProfileRepository,
                accountsRepository = appContainer.accountsRepository,
                balancesRepository = appContainer.balancesRepository,
                categoriesRepository = appContainer.categoriesRepository,
                transactionsRepository = appContainer.transactionsRepository,
            )
        }
    }

    override fun onDestroy() {
        appContainer.close()
        super.onDestroy()
    }
}
