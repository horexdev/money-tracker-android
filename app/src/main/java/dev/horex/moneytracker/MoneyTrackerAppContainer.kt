package dev.horex.moneytracker

import android.content.Context
import dev.horex.moneytracker.core.accounts.RoomAccountsRepository
import dev.horex.moneytracker.core.balance.RoomBalancesRepository
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupExporter
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupImporter
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupSafRepository
import dev.horex.moneytracker.core.background.BackgroundTaskId
import dev.horex.moneytracker.core.background.BackgroundTaskResult
import dev.horex.moneytracker.core.background.BackgroundTaskExecutor
import dev.horex.moneytracker.core.background.MoneyTrackerBackgroundWorkScheduler
import dev.horex.moneytracker.core.background.MoneyTrackerWorkerFactory
import dev.horex.moneytracker.core.background.MutableBackgroundTaskRegistry
import dev.horex.moneytracker.core.background.PeriodicBackgroundWorkSpec
import dev.horex.moneytracker.core.budgets.Budget
import dev.horex.moneytracker.core.budgets.BudgetNotificationProfile
import dev.horex.moneytracker.core.budgets.BudgetNotificationProfileProvider
import dev.horex.moneytracker.core.budgets.BudgetThresholdNotificationProcessor
import dev.horex.moneytracker.core.budgets.RoomBudgetsRepository
import dev.horex.moneytracker.core.categories.RoomCategoriesRepository
import dev.horex.moneytracker.core.currency.KtorExchangeRateUpdateService
import dev.horex.moneytracker.core.currency.RoomCurrencyRatesRepository
import dev.horex.moneytracker.core.database.MoneyTrackerDatabase
import dev.horex.moneytracker.core.database.MoneyTrackerDatabaseFactory
import dev.horex.moneytracker.core.database.profile.LocalProfileDefaults
import dev.horex.moneytracker.core.database.profile.LocalProfileRepository
import dev.horex.moneytracker.core.database.profile.normalizeLocalProfileLanguageCode
import dev.horex.moneytracker.core.database.seed.DefaultProfileSeedRepository
import dev.horex.moneytracker.core.database.security.AndroidDatabasePassphraseStore
import dev.horex.moneytracker.core.notifications.AndroidMoneyTrackerNotificationBuilder
import dev.horex.moneytracker.core.notifications.AndroidMoneyTrackerNotifier
import dev.horex.moneytracker.core.notifications.AndroidNotificationChannelRegistrar
import dev.horex.moneytracker.core.notifications.AndroidNotificationIntentFactory
import dev.horex.moneytracker.core.notifications.AndroidNotificationPermissionController
import dev.horex.moneytracker.core.preferences.AppPreferencesRepository
import dev.horex.moneytracker.core.preferences.DataStoreActiveProfileIdStore
import dev.horex.moneytracker.core.preferences.RoomSettingsRepository
import dev.horex.moneytracker.core.preferences.createAppPreferencesDataStore
import dev.horex.moneytracker.core.recurring.RoomRecurringTransactionsRepository
import dev.horex.moneytracker.core.savings.GoalMilestoneNotificationPreferenceProvider
import dev.horex.moneytracker.core.savings.GoalMilestoneNotificationProcessor
import dev.horex.moneytracker.core.savings.RoomSavingsGoalsRepository
import dev.horex.moneytracker.core.savings.SavingsGoal
import dev.horex.moneytracker.core.stats.RoomStatsRepository
import dev.horex.moneytracker.core.templates.RoomTransactionTemplatesRepository
import dev.horex.moneytracker.core.transactions.RoomTransactionsRepository
import dev.horex.moneytracker.core.transactions.TransactionCsvExporter
import dev.horex.moneytracker.backup.MoneyTrackerBackupDocumentRepository
import dev.horex.moneytracker.csv.MoneyTrackerCsvDocumentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.absoluteValue

internal class MoneyTrackerAppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val appPreferencesScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val databaseLazy = lazy {
        MoneyTrackerDatabaseFactory.createEncrypted(
            context = appContext,
            passphraseStore = AndroidDatabasePassphraseStore(appContext),
            databaseName = MoneyTrackerDatabase.DATABASE_NAME,
        )
    }

    val backgroundTaskRegistry: MutableBackgroundTaskRegistry by lazy {
        MutableBackgroundTaskRegistry()
    }

    val backgroundTaskExecutor: BackgroundTaskExecutor by lazy {
        BackgroundTaskExecutor(backgroundTaskRegistry)
    }

    val backgroundWorkerFactory: MoneyTrackerWorkerFactory by lazy {
        MoneyTrackerWorkerFactory(backgroundTaskExecutor)
    }

    val backgroundWorkScheduler: MoneyTrackerBackgroundWorkScheduler by lazy {
        MoneyTrackerBackgroundWorkScheduler.create(appContext)
    }

    init {
        registerBackgroundTasks()
    }

    private val database: MoneyTrackerDatabase by databaseLazy
    private val appPreferencesDataStore by lazy {
        createAppPreferencesDataStore(
            context = appContext,
            scope = appPreferencesScope,
        )
    }

    val appPreferencesRepository: AppPreferencesRepository by lazy {
        AppPreferencesRepository(appPreferencesDataStore)
    }

    val localProfileRepository: LocalProfileRepository by lazy {
        LocalProfileRepository(
            database = database,
            activeProfileIdStore = DataStoreActiveProfileIdStore(appPreferencesRepository),
            defaults = LocalProfileDefaults(
                languageCode = resolveDeviceLanguageCode(appContext),
            ),
            profileSeeder = DefaultProfileSeedRepository(database),
        )
    }

    val accountsRepository: RoomAccountsRepository by lazy {
        RoomAccountsRepository(database)
    }

    val balancesRepository: RoomBalancesRepository by lazy {
        RoomBalancesRepository(database)
    }

    val budgetsRepository: RoomBudgetsRepository by lazy {
        RoomBudgetsRepository(database)
    }

    val categoriesRepository: RoomCategoriesRepository by lazy {
        RoomCategoriesRepository(database)
    }

    val currencyRatesRepository: RoomCurrencyRatesRepository by lazy {
        RoomCurrencyRatesRepository(database)
    }

    private val exchangeRateUpdateServiceLazy = lazy {
        KtorExchangeRateUpdateService(currencyRatesRepository)
    }

    val exchangeRateUpdateService: KtorExchangeRateUpdateService by exchangeRateUpdateServiceLazy

    val settingsRepository: RoomSettingsRepository by lazy {
        RoomSettingsRepository(
            database = database,
            localProfileBootstrapper = localProfileRepository,
            appPreferencesRepository = appPreferencesRepository,
        )
    }

    val recurringRepository: RoomRecurringTransactionsRepository by lazy {
        RoomRecurringTransactionsRepository(database)
    }

    val savingsGoalsRepository: RoomSavingsGoalsRepository by lazy {
        RoomSavingsGoalsRepository(
            database = database,
            goalMilestoneNotificationProcessor = goalMilestoneNotificationProcessor,
            goalMilestoneNotificationPreferenceProvider = goalMilestoneNotificationPreferenceProvider,
        )
    }

    val statsRepository: RoomStatsRepository by lazy {
        RoomStatsRepository(database)
    }

    val transactionTemplatesRepository: RoomTransactionTemplatesRepository by lazy {
        RoomTransactionTemplatesRepository(database)
    }

    val transactionsRepository: RoomTransactionsRepository by lazy {
        RoomTransactionsRepository(database)
    }

    private val transactionCsvExporter: TransactionCsvExporter by lazy {
        TransactionCsvExporter(transactionsRepository)
    }

    private val backupExporter: MoneyTrackerBackupExporter by lazy {
        MoneyTrackerBackupExporter(database)
    }

    private val backupImporter: MoneyTrackerBackupImporter by lazy {
        MoneyTrackerBackupImporter(database)
    }

    val backupSafRepository: MoneyTrackerBackupSafRepository by lazy {
        MoneyTrackerBackupSafRepository(
            contentResolver = appContext.contentResolver,
            exporter = backupExporter,
            importer = backupImporter,
        )
    }

    val backupDocumentRepository: MoneyTrackerBackupDocumentRepository by lazy {
        MoneyTrackerBackupDocumentRepository(
            contentResolver = appContext.contentResolver,
            safRepository = backupSafRepository,
            importer = backupImporter,
        )
    }

    val csvDocumentRepository: MoneyTrackerCsvDocumentRepository by lazy {
        MoneyTrackerCsvDocumentRepository(
            contentResolver = appContext.contentResolver,
            localProfileRepository = localProfileRepository,
            exporter = transactionCsvExporter,
        )
    }

    val notificationPermissionController: AndroidNotificationPermissionController by lazy {
        AndroidNotificationPermissionController(appContext)
    }

    val notificationChannelRegistrar: AndroidNotificationChannelRegistrar by lazy {
        AndroidNotificationChannelRegistrar(appContext)
    }

    val notificationIntentFactory: AndroidNotificationIntentFactory by lazy {
        AndroidNotificationIntentFactory(
            context = appContext,
            launcherActivityClass = MainActivity::class.java,
        )
    }

    val notificationBuilder: AndroidMoneyTrackerNotificationBuilder by lazy {
        AndroidMoneyTrackerNotificationBuilder(
            context = appContext,
            smallIconResId = R.drawable.ic_notification_money_tracker,
        )
    }

    val notificationNotifier: AndroidMoneyTrackerNotifier by lazy {
        AndroidMoneyTrackerNotifier(
            context = appContext,
            channelRegistrar = notificationChannelRegistrar,
            permissionController = notificationPermissionController,
            notificationBuilder = notificationBuilder,
        )
    }

    private val budgetNotificationProfileProvider: BudgetNotificationProfileProvider by lazy {
        BudgetNotificationProfileProvider {
            localProfileRepository.ensureActiveProfile()
            localProfileRepository.listProfiles().map { profile ->
                BudgetNotificationProfile(
                    profileId = profile.id,
                    budgetNotificationsEnabled = profile.notifyBudgetAlerts,
                )
            }
        }
    }

    private val budgetThresholdNotificationProcessor: BudgetThresholdNotificationProcessor by lazy {
        BudgetThresholdNotificationProcessor(
            profileProvider = budgetNotificationProfileProvider,
            budgetsRepository = budgetsRepository,
            notifier = { request -> notificationNotifier.notify(request) },
            contentIntentFactory = { budget, _ ->
                notificationIntentFactory.openAppPendingIntent(
                    requestCode = budget.notificationRequestCode(),
                )
            },
        )
    }

    private val goalMilestoneNotificationPreferenceProvider: GoalMilestoneNotificationPreferenceProvider by lazy {
        GoalMilestoneNotificationPreferenceProvider { profileId ->
            localProfileRepository.listProfiles()
                .firstOrNull { profile -> profile.id == profileId }
                ?.notifyGoalMilestones == true
        }
    }

    private val goalMilestoneNotificationProcessor: GoalMilestoneNotificationProcessor by lazy {
        GoalMilestoneNotificationProcessor(
            notifier = { request -> notificationNotifier.notify(request) },
            contentIntentFactory = { goal, _ ->
                notificationIntentFactory.openAppPendingIntent(
                    requestCode = goal.notificationRequestCode(),
                )
            },
        )
    }

    fun startBackgroundWork() {
        runRecurringCatchUp()
        backgroundWorkScheduler.enqueuePeriodic(
            PeriodicBackgroundWorkSpec(
                taskId = RecurringDueTaskId,
                repeatIntervalMinutes = RECURRING_DUE_REPEAT_INTERVAL_MINUTES,
                flexIntervalMinutes = RECURRING_DUE_FLEX_INTERVAL_MINUTES,
            ),
        )
        backgroundWorkScheduler.enqueuePeriodic(
            PeriodicBackgroundWorkSpec(
                taskId = BudgetThresholdNotificationTaskId,
                repeatIntervalMinutes = BUDGET_NOTIFICATION_REPEAT_INTERVAL_MINUTES,
                flexIntervalMinutes = BUDGET_NOTIFICATION_FLEX_INTERVAL_MINUTES,
            ),
        )
    }

    fun close() {
        if (exchangeRateUpdateServiceLazy.isInitialized()) {
            exchangeRateUpdateService.close()
        }
        appPreferencesScope.cancel()
        if (databaseLazy.isInitialized()) {
            database.close()
        }
    }

    private fun registerBackgroundTasks() {
        backgroundTaskRegistry.register(RecurringDueTaskId) { context ->
            runCatching {
                recurringRepository.processDue(context.startedAtEpochMillis)
                BackgroundTaskResult.Success
            }.getOrElse {
                BackgroundTaskResult.Retry
            }
        }
        backgroundTaskRegistry.register(BudgetThresholdNotificationTaskId) {
            runCatching {
                budgetThresholdNotificationProcessor.run()
                BackgroundTaskResult.Success
            }.getOrElse {
                BackgroundTaskResult.Retry
            }
        }
    }

    private fun runRecurringCatchUp() {
        appPreferencesScope.launch {
            runCatching {
                recurringRepository.processDue()
            }
        }
    }
}

private fun resolveDeviceLanguageCode(context: Context): String {
    val configuredLanguage = context.resources.configuration.locales[0]?.language
    return normalizeLocalProfileLanguageCode(configuredLanguage ?: Locale.getDefault().language)
}

private fun Budget.notificationRequestCode(): Int {
    var result = 17
    result = 31 * result + profileId.hashCode()
    result = 31 * result + id.hashCode()
    return result.absoluteValue.takeIf { it > 0 } ?: 1
}

private fun SavingsGoal.notificationRequestCode(): Int {
    var result = 43
    result = 31 * result + profileId.hashCode()
    result = 31 * result + id.hashCode()
    return result.absoluteValue.takeIf { it > 0 } ?: 1
}

private val BudgetThresholdNotificationTaskId = BackgroundTaskId("budgets.threshold-notifications")
private val RecurringDueTaskId = BackgroundTaskId("recurring.process-due")
private const val BUDGET_NOTIFICATION_REPEAT_INTERVAL_MINUTES = 6L * 60L
private const val BUDGET_NOTIFICATION_FLEX_INTERVAL_MINUTES = 60L
private const val RECURRING_DUE_REPEAT_INTERVAL_MINUTES = 6L * 60L
private const val RECURRING_DUE_FLEX_INTERVAL_MINUTES = 60L
