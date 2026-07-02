package dev.horex.moneytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private val appContainer by lazy {
        MoneyTrackerAppContainer(applicationContext)
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        appContainer.notificationPermissionController.onRuntimePermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContainer.notificationChannelRegistrar.ensureNotificationChannels()
        setContent {
            MoneyTrackerApp(
                localProfileBootstrapper = appContainer.localProfileRepository,
                accountsRepository = appContainer.accountsRepository,
                balancesRepository = appContainer.balancesRepository,
                categoriesRepository = appContainer.categoriesRepository,
                settingsRepository = appContainer.settingsRepository,
                statsRepository = appContainer.statsRepository,
                transactionsRepository = appContainer.transactionsRepository,
            )
        }
        requestNotificationPermissionIfNeeded()
    }

    override fun onDestroy() {
        appContainer.close()
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        val permissionName = appContainer.notificationPermissionController.runtimePermissionName
            ?: return
        if (!appContainer.notificationPermissionController.shouldRequestRuntimePermission()) {
            return
        }

        appContainer.notificationPermissionController.markRuntimePermissionRequested()
        requestNotificationPermission.launch(permissionName)
    }
}
