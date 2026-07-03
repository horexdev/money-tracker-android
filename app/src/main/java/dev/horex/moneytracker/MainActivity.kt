package dev.horex.moneytracker

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private val appContainer: MoneyTrackerAppContainer
        get() = (application as MoneyTrackerApplication).appContainer

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
                budgetsRepository = appContainer.budgetsRepository,
                categoriesRepository = appContainer.categoriesRepository,
                currencyRatesRepository = appContainer.currencyRatesRepository,
                exchangeRateUpdateService = appContainer.exchangeRateUpdateService,
                settingsRepository = appContainer.settingsRepository,
                recurringRepository = appContainer.recurringRepository,
                savingsGoalsRepository = appContainer.savingsGoalsRepository,
                statsRepository = appContainer.statsRepository,
                transactionTemplatesRepository = appContainer.transactionTemplatesRepository,
                transactionsRepository = appContainer.transactionsRepository,
                localProfileRepository = appContainer.localProfileRepository,
                appPreferencesRepository = appContainer.appPreferencesRepository,
                backupDocumentRepository = appContainer.backupDocumentRepository,
                csvDocumentRepository = appContainer.csvDocumentRepository,
                notificationPermissionStatusProvider = {
                    appContainer.notificationPermissionController.permissionStatus()
                },
                areNotificationsEnabledProvider = ::areNotificationsEnabled,
                onOpenNotificationSettings = ::openNotificationSettings,
            )
        }
        requestNotificationPermissionIfNeeded()
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

    private fun areNotificationsEnabled(): Boolean {
        return getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)
    }
}
