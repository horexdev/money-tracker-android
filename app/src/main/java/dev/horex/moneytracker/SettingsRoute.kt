package dev.horex.moneytracker

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.horex.moneytracker.core.currency.CurrencyException
import dev.horex.moneytracker.core.currency.CurrencyInfo
import dev.horex.moneytracker.core.currency.CurrencyRatesRepository
import dev.horex.moneytracker.core.currency.ExchangeRateUpdateService
import dev.horex.moneytracker.core.currency.ExchangeRateNotFoundException
import dev.horex.moneytracker.core.currency.ExchangeRateOverride
import dev.horex.moneytracker.core.currency.ExchangeRateOverrideAlreadyExistsException
import dev.horex.moneytracker.core.currency.ExchangeRateSource
import dev.horex.moneytracker.core.currency.IsoCurrencyCatalog
import dev.horex.moneytracker.core.currency.RATE_SCALE_E8
import dev.horex.moneytracker.core.currency.ResolvedExchangeRate
import dev.horex.moneytracker.core.currency.SaveExchangeRateOverrideInput
import dev.horex.moneytracker.core.currency.currencyDisplayText
import dev.horex.moneytracker.core.currency.currencySymbolOrNull
import dev.horex.moneytracker.core.currency.matchesCurrencyQuery
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.LocalProfileRepository
import dev.horex.moneytracker.core.designsystem.component.MoneyTrackerSearchablePickerField
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.notifications.NotificationPermissionStatus
import dev.horex.moneytracker.core.notifications.canPostNotifications
import dev.horex.moneytracker.core.preferences.AppPreferencesRepository
import dev.horex.moneytracker.core.preferences.AppThemePreference
import dev.horex.moneytracker.core.preferences.MAX_SETTINGS_DISPLAY_CURRENCIES
import dev.horex.moneytracker.core.preferences.MoneyTrackerSettings
import dev.horex.moneytracker.core.preferences.SettingsException
import dev.horex.moneytracker.core.preferences.SettingsNotificationPreferences
import dev.horex.moneytracker.core.preferences.SettingsRepository
import dev.horex.moneytracker.core.preferences.SettingsUiPreferences
import dev.horex.moneytracker.core.preferences.StatsChartStylePreference
import dev.horex.moneytracker.core.preferences.UpdateSettingsInput
import dev.horex.moneytracker.core.preferences.UpdateSettingsNotificationPreferencesInput
import dev.horex.moneytracker.core.preferences.UpdateSettingsUiPreferencesInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

@Composable
fun SettingsRoute(
    settingsRepository: SettingsRepository,
    localProfileRepository: LocalProfileRepository,
    currencyRatesRepository: CurrencyRatesRepository,
    exchangeRateUpdateService: ExchangeRateUpdateService? = null,
    appPreferencesRepository: AppPreferencesRepository? = null,
    notificationPermissionStatusProvider: () -> NotificationPermissionStatus = {
        NotificationPermissionStatus.NotRequired
    },
    areNotificationsEnabledProvider: () -> Boolean = { true },
    onOpenNotificationSettings: () -> Unit = {},
    onOpenImportExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultRateDate = remember { LocalDate.now().toString() }
    var state by remember { mutableStateOf(SettingsUiState(isLoading = true)) }

    fun loadSettings(showLoading: Boolean = true, success: SettingsSuccess? = null) {
        scope.launch {
            if (showLoading) {
                state = state.copy(isLoading = true, error = null, success = null)
            }
            try {
                localProfileRepository.ensureActiveProfile()
                val settings = settingsRepository.getSettings()
                val profiles = localProfileRepository.listProfiles()
                val latestSnapshotDate = currencyRatesRepository.getLatestSnapshotDate()
                    .takeUnless { it == EMPTY_RATES_SNAPSHOT_DATE }
                val overrides = currencyRatesRepository.listManualOverrides(settings.profileId)
                val notificationDeliveryState = resolveNotificationDeliveryState(
                    permissionStatus = notificationPermissionStatusProvider(),
                    systemNotificationsEnabled = areNotificationsEnabledProvider(),
                )

                state = state.toLoadedState(
                    settings = settings,
                    profiles = profiles,
                    latestSnapshotDate = latestSnapshotDate,
                    overrides = overrides,
                    notificationDeliveryState = notificationDeliveryState,
                    defaultRateDate = defaultRateDate,
                    success = success,
                )
            } catch (error: Throwable) {
                state = state.copy(
                    isLoading = false,
                    isBusy = false,
                    error = error.toSettingsError(),
                    success = null,
                )
            }
        }
    }

    fun runSettingsUpdate(
        input: UpdateSettingsInput,
        success: SettingsSuccess = SettingsSuccess.Saved,
        applyLanguage: Boolean = false,
    ) {
        scope.launch {
            state = state.copy(isBusy = true, error = null, success = null)
            try {
                val updated = settingsRepository.updateSettings(input)
                appPreferencesRepository.syncUiPreferences(updated)
                if (applyLanguage) {
                    applyAppLanguage(context, updated.languageCode)
                }
                loadSettings(showLoading = false, success = success)
            } catch (error: Throwable) {
                state = state.copy(isBusy = false, error = error.toSettingsError(), success = null)
            }
        }
    }

    fun buildRateOverrideInput(): SaveExchangeRateOverrideInput? {
        val rateE8 = state.rateValueInput.toRateE8() ?: return null
        return SaveExchangeRateOverrideInput(
            effectiveDate = state.rateDateInput,
            baseCurrency = state.rateBaseInput,
            targetCurrency = state.rateTargetInput,
            rateE8 = rateE8,
        )
    }

    fun saveRateOverride(input: SaveExchangeRateOverrideInput, overwriteExisting: Boolean) {
        val settings = state.settings ?: return
        scope.launch {
            state = state.copy(
                isBusy = true,
                pendingRateOverwrite = null,
                error = null,
                success = null,
            )
            try {
                currencyRatesRepository.saveManualOverride(
                    profileId = settings.profileId,
                    input = input,
                    overwriteExisting = overwriteExisting,
                )
                loadSettings(showLoading = false, success = SettingsSuccess.RateSaved)
            } catch (error: ExchangeRateOverrideAlreadyExistsException) {
                state = state.copy(
                    isBusy = false,
                    pendingRateOverwrite = error.toPendingRateOverwrite(input),
                    error = null,
                    success = null,
                )
            } catch (error: Throwable) {
                state = state.copy(isBusy = false, error = error.toSettingsError(), success = null)
            }
        }
    }

    LaunchedEffect(settingsRepository, localProfileRepository, currencyRatesRepository) {
        loadSettings(showLoading = true)
    }

    LaunchedEffect(
        state.settings?.profileId,
        state.rateBaseInput,
        state.rateTargetInput,
        state.rateDateInput,
        state.manualRateOverrides,
        state.latestSnapshotDate,
    ) {
        val settings = state.settings ?: return@LaunchedEffect
        val preview = currencyRatesRepository.resolveRatePreview(
            profileId = settings.profileId,
            baseCurrency = state.rateBaseInput,
            targetCurrency = state.rateTargetInput,
            effectiveDate = state.rateDateInput,
        )
        if (state.ratePreview != preview) {
            state = state.copy(ratePreview = preview)
        }
    }

    SettingsScreen(
        state = state,
        modifier = modifier,
        onRetry = { loadSettings(showLoading = true) },
        onDismissMessage = { state = state.copy(error = null, success = null) },
        onSelectProfile = { profileId ->
            scope.launch {
                state = state.copy(isBusy = true, error = null, success = null)
                try {
                    localProfileRepository.selectActiveProfile(profileId)
                    val selectedSettings = settingsRepository.getSettings()
                    appPreferencesRepository.syncUiPreferences(selectedSettings)
                    applyAppLanguage(context, selectedSettings.languageCode)
                    loadSettings(showLoading = false, success = SettingsSuccess.ProfileSelected)
                } catch (error: Throwable) {
                    state = state.copy(isBusy = false, error = error.toSettingsError(), success = null)
                }
            }
        },
        onProfileLabelInputChange = { label ->
            state = state.copy(activeProfileLabelInput = label, error = null, success = null)
        },
        onSaveProfileLabel = {
            val profileId = state.settings?.profileId ?: return@SettingsScreen
            scope.launch {
                state = state.copy(isBusy = true, error = null, success = null)
                try {
                    localProfileRepository.updateProfileLabel(profileId, state.activeProfileLabelInput)
                    loadSettings(showLoading = false, success = SettingsSuccess.ProfileSaved)
                } catch (error: Throwable) {
                    state = state.copy(isBusy = false, error = error.toSettingsError(), success = null)
                }
            }
        },
        onNewProfileLabelChange = { label ->
            state = state.copy(newProfileLabel = label, error = null, success = null)
        },
        onCreateProfile = {
            val languageCode = state.settings?.languageCode ?: DEFAULT_NEW_PROFILE_LANGUAGE
            scope.launch {
                state = state.copy(isBusy = true, error = null, success = null)
                try {
                    val profile = localProfileRepository.createProfile(
                        label = state.newProfileLabel,
                        languageCode = languageCode,
                    )
                    localProfileRepository.selectActiveProfile(profile.id)
                    val selectedSettings = settingsRepository.getSettings()
                    appPreferencesRepository.syncUiPreferences(selectedSettings)
                    loadSettings(showLoading = false, success = SettingsSuccess.ProfileCreated)
                } catch (error: Throwable) {
                    state = state.copy(isBusy = false, error = error.toSettingsError(), success = null)
                }
            }
        },
        onLanguageSelected = { languageCode ->
            runSettingsUpdate(
                input = UpdateSettingsInput(languageCode = languageCode),
                applyLanguage = true,
            )
        },
        onThemeSelected = { theme ->
            runSettingsUpdate(
                input = UpdateSettingsInput(
                    uiPreferences = UpdateSettingsUiPreferencesInput(theme = theme),
                ),
            )
        },
        onHideAmountsChanged = { hidden ->
            runSettingsUpdate(
                input = UpdateSettingsInput(
                    uiPreferences = UpdateSettingsUiPreferencesInput(hideAmounts = hidden),
                ),
            )
        },
        onAnimateNumbersChanged = { animate ->
            runSettingsUpdate(
                input = UpdateSettingsInput(
                    uiPreferences = UpdateSettingsUiPreferencesInput(animateNumbers = animate),
                ),
            )
        },
        onChartStyleSelected = { chartStyle ->
            runSettingsUpdate(
                input = UpdateSettingsInput(
                    uiPreferences = UpdateSettingsUiPreferencesInput(statsChartStyle = chartStyle),
                ),
            )
        },
        onNotificationChanged = { key, enabled ->
            runSettingsUpdate(
                input = UpdateSettingsInput(
                    notificationPreferences = key.toUpdateInput(enabled),
                ),
            )
        },
        onOpenNotificationSettings = onOpenNotificationSettings,
        onDisplayCurrenciesInputChange = { value ->
            state = state.copy(displayCurrenciesInput = value, error = null, success = null)
        },
        onSaveDisplayCurrencies = {
            runSettingsUpdate(
                input = UpdateSettingsInput(
                    displayCurrencyCodes = state.displayCurrenciesInput.toCurrencyCodesInput(),
                ),
            )
        },
        onRateBaseChange = { value ->
            state = state.copy(rateBaseInput = value.uppercase(Locale.US).take(MAX_CURRENCY_INPUT), error = null)
        },
        onRateTargetChange = { value ->
            state = state.copy(rateTargetInput = value.uppercase(Locale.US).take(MAX_CURRENCY_INPUT), error = null)
        },
        onRateDateChange = { value ->
            state = state.copy(rateDateInput = value.take(MAX_RATE_DATE_INPUT), error = null)
        },
        onRateValueChange = { value ->
            state = state.copy(rateValueInput = value.take(MAX_RATE_VALUE_INPUT), error = null)
        },
        onSaveRateOverride = {
            val input = buildRateOverrideInput()
            if (input == null) {
                state = state.copy(error = SettingsUiError.InvalidInput, success = null)
                return@SettingsScreen
            }
            saveRateOverride(input = input, overwriteExisting = false)
        },
        onUpdateOnlineRates = {
            val service = exchangeRateUpdateService ?: return@SettingsScreen
            val settings = state.settings ?: return@SettingsScreen
            val baseCurrency = state.rateBaseInput.ifBlank { settings.baseCurrencyCode }
            val targetCurrencies = state.displayCurrenciesInput.toCurrencyCodesInput()
                .plus(state.rateTargetInput.takeIf(String::isNotBlank))
                .filterNotNull()
                .distinct()
            if (targetCurrencies.isEmpty()) {
                state = state.copy(error = SettingsUiError.InvalidInput, success = null)
                return@SettingsScreen
            }

            scope.launch {
                state = state.copy(isBusy = true, error = null, success = null)
                try {
                    service.updateLatestRates(
                        baseCurrency = baseCurrency,
                        targetCurrencies = targetCurrencies,
                    )
                    loadSettings(showLoading = false, success = SettingsSuccess.OnlineRatesUpdated)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    state = state.copy(
                        isBusy = false,
                        error = SettingsUiError.RateUpdateFailed,
                        success = null,
                    )
                }
            }
        },
        onDeleteRateOverride = { overrideId ->
            val settings = state.settings ?: return@SettingsScreen
            scope.launch {
                state = state.copy(isBusy = true, error = null, success = null)
                try {
                    currencyRatesRepository.deleteManualOverride(settings.profileId, overrideId)
                    loadSettings(showLoading = false, success = SettingsSuccess.RateDeleted)
                } catch (error: Throwable) {
                    state = state.copy(isBusy = false, error = error.toSettingsError(), success = null)
                }
            }
        },
        onConfirmRateOverwrite = {
            val pending = state.pendingRateOverwrite ?: return@SettingsScreen
            saveRateOverride(input = pending.input, overwriteExisting = true)
        },
        onDismissRateOverwrite = {
            state = state.copy(pendingRateOverwrite = null)
        },
        onOpenImportExport = onOpenImportExport,
        onResetRequested = {
            state = state.copy(showResetConfirm = true, error = null, success = null)
        },
        onResetDismiss = {
            state = state.copy(showResetConfirm = false)
        },
        onResetConfirmed = {
            scope.launch {
                state = state.copy(isBusy = true, showResetConfirm = false, error = null, success = null)
                try {
                    localProfileRepository.resetActiveProfileData()
                    val resetSettings = settingsRepository.getSettings()
                    appPreferencesRepository.syncUiPreferences(resetSettings)
                    applyAppLanguage(context, resetSettings.languageCode)
                    loadSettings(showLoading = false, success = SettingsSuccess.DataReset)
                } catch (error: Throwable) {
                    state = state.copy(isBusy = false, error = error.toSettingsError(), success = null)
                }
            }
        },
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onProfileLabelInputChange: (String) -> Unit,
    onSaveProfileLabel: () -> Unit,
    onNewProfileLabelChange: (String) -> Unit,
    onCreateProfile: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onThemeSelected: (AppThemePreference) -> Unit,
    onHideAmountsChanged: (Boolean) -> Unit,
    onAnimateNumbersChanged: (Boolean) -> Unit,
    onChartStyleSelected: (StatsChartStylePreference) -> Unit,
    onNotificationChanged: (NotificationPreferenceKey, Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDisplayCurrenciesInputChange: (String) -> Unit,
    onSaveDisplayCurrencies: () -> Unit,
    onRateBaseChange: (String) -> Unit,
    onRateTargetChange: (String) -> Unit,
    onRateDateChange: (String) -> Unit,
    onRateValueChange: (String) -> Unit,
    onSaveRateOverride: () -> Unit,
    onUpdateOnlineRates: () -> Unit = {},
    onDeleteRateOverride: (Long) -> Unit,
    onOpenImportExport: () -> Unit,
    onResetRequested: () -> Unit,
    onResetDismiss: () -> Unit,
    onResetConfirmed: () -> Unit,
    onConfirmRateOverwrite: () -> Unit = {},
    onDismissRateOverwrite: () -> Unit = onDismissMessage,
    onDismissSuccess: () -> Unit = onDismissMessage,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading && state.settings == null) {
            InlineLoading(
                text = stringResource(R.string.settings_loading),
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings-screen"),
                contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    SettingsHeader()
                }
                state.error?.let { error ->
                    item {
                        SettingsMessageCard(
                            icon = Icons.Filled.Error,
                            title = stringResource(R.string.settings_error_title),
                            body = stringResource(error.messageResId),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            actionText = if (state.settings == null) {
                                stringResource(R.string.settings_retry)
                            } else {
                                null
                            },
                            onAction = onRetry,
                            onDismiss = onDismissMessage,
                        )
                    }
                }
                state.success?.let { success ->
                    item {
                        SettingsMessageCard(
                            icon = Icons.Filled.CheckCircle,
                            title = stringResource(R.string.settings_saved_title),
                            body = stringResource(success.messageResId),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onDismiss = onDismissSuccess,
                        )
                    }
                }
                state.settings?.let { settings ->
                    item {
                        ProfileSection(
                            state = state,
                            onSelectProfile = onSelectProfile,
                            onProfileLabelInputChange = onProfileLabelInputChange,
                            onSaveProfileLabel = onSaveProfileLabel,
                            onNewProfileLabelChange = onNewProfileLabelChange,
                            onCreateProfile = onCreateProfile,
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        LanguageSection(
                            selectedLanguageCode = settings.languageCode,
                            isBusy = state.isBusy,
                            onLanguageSelected = onLanguageSelected,
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        AppearanceSection(
                            preferences = settings.uiPreferences,
                            isBusy = state.isBusy,
                            onThemeSelected = onThemeSelected,
                            onHideAmountsChanged = onHideAmountsChanged,
                            onAnimateNumbersChanged = onAnimateNumbersChanged,
                            onChartStyleSelected = onChartStyleSelected,
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        NotificationsSection(
                            preferences = settings.notificationPreferences,
                            deliveryState = state.notificationDeliveryState,
                            isBusy = state.isBusy,
                            onNotificationChanged = onNotificationChanged,
                            onOpenNotificationSettings = onOpenNotificationSettings,
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        RatesSection(
                            state = state,
                            settings = settings,
                            onDisplayCurrenciesInputChange = onDisplayCurrenciesInputChange,
                            onSaveDisplayCurrencies = onSaveDisplayCurrencies,
                            onRateBaseChange = onRateBaseChange,
                            onRateTargetChange = onRateTargetChange,
                            onRateDateChange = onRateDateChange,
                            onRateValueChange = onRateValueChange,
                            onSaveRateOverride = onSaveRateOverride,
                            onUpdateOnlineRates = onUpdateOnlineRates,
                            onDeleteRateOverride = onDeleteRateOverride,
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        DataSection(
                            isBusy = state.isBusy,
                            onOpenImportExport = onOpenImportExport,
                            onResetRequested = onResetRequested,
                        )
                    }
                }
            }
        }

        if (state.isBusy) {
            BusyOverlay()
        }
    }

    if (state.showResetConfirm) {
        AlertDialog(
            onDismissRequest = onResetDismiss,
            icon = {
                Icon(Icons.Filled.Delete, contentDescription = null)
            },
            title = {
                Text(text = stringResource(R.string.settings_reset_confirm_title))
            },
            text = {
                Text(text = stringResource(R.string.settings_reset_confirm_body))
            },
            confirmButton = {
                Button(onClick = onResetConfirmed, enabled = !state.isBusy) {
                    Text(text = stringResource(R.string.settings_reset_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = onResetDismiss) {
                    Text(text = stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    state.pendingRateOverwrite?.let { pending ->
        AlertDialog(
            onDismissRequest = onDismissRateOverwrite,
            icon = {
                Icon(Icons.Filled.CurrencyExchange, contentDescription = null)
            },
            title = {
                Text(text = stringResource(R.string.settings_rate_overwrite_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_rate_overwrite_body,
                        pending.baseCurrency,
                        pending.targetCurrency,
                        pending.effectiveDate,
                        pending.existingRate,
                        pending.newRate,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = onConfirmRateOverwrite, enabled = !state.isBusy) {
                    Text(text = stringResource(R.string.settings_replace_rate))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRateOverwrite) {
                    Text(text = stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileSection(
    state: SettingsUiState,
    onSelectProfile: (Long) -> Unit,
    onProfileLabelInputChange: (String) -> Unit,
    onSaveProfileLabel: () -> Unit,
    onNewProfileLabelChange: (String) -> Unit,
    onCreateProfile: () -> Unit,
) {
    SettingsSection(
        icon = Icons.Filled.Person,
        title = stringResource(R.string.settings_profiles_section),
    ) {
        state.profiles.forEach { profile ->
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isBusy && !profile.isActive) {
                        onSelectProfile(profile.id)
                    },
                headlineContent = {
                    Text(
                        text = profile.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(text = stringResource(R.string.settings_profile_language, profile.languageCode.uppercase()))
                },
                leadingContent = {
                    RadioButton(
                        selected = profile.isActive,
                        onClick = { if (!profile.isActive) onSelectProfile(profile.id) },
                        enabled = !state.isBusy,
                    )
                },
            )
        }

        OutlinedTextField(
            value = state.activeProfileLabelInput,
            onValueChange = onProfileLabelInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_active_profile_name)) },
            singleLine = true,
            enabled = !state.isBusy,
        )
        Button(
            onClick = onSaveProfileLabel,
            enabled = !state.isBusy && state.activeProfileLabelInput.isNotBlank(),
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Text(
                text = stringResource(R.string.settings_save_profile),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        OutlinedTextField(
            value = state.newProfileLabel,
            onValueChange = onNewProfileLabelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_new_profile_name)) },
            singleLine = true,
            enabled = !state.isBusy,
        )
        OutlinedButton(
            onClick = onCreateProfile,
            enabled = !state.isBusy && state.newProfileLabel.isNotBlank(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.settings_create_profile),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun LanguageSection(
    selectedLanguageCode: String,
    isBusy: Boolean,
    onLanguageSelected: (String) -> Unit,
) {
    SettingsSection(
        icon = Icons.Filled.Language,
        title = stringResource(R.string.settings_language_section),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SettingsLanguageOptions) { option ->
                FilterChip(
                    selected = option.code == selectedLanguageCode,
                    onClick = { onLanguageSelected(option.code) },
                    enabled = !isBusy,
                    label = { Text(text = option.label) },
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    preferences: SettingsUiPreferences,
    isBusy: Boolean,
    onThemeSelected: (AppThemePreference) -> Unit,
    onHideAmountsChanged: (Boolean) -> Unit,
    onAnimateNumbersChanged: (Boolean) -> Unit,
    onChartStyleSelected: (StatsChartStylePreference) -> Unit,
) {
    SettingsSection(
        icon = Icons.Filled.Palette,
        title = stringResource(R.string.settings_appearance_section),
    ) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemePreference.entries.forEach { theme ->
                FilterChip(
                    selected = preferences.theme == theme,
                    onClick = { onThemeSelected(theme) },
                    enabled = !isBusy,
                    label = { Text(text = stringResource(theme.labelResId)) },
                )
            }
        }

        SettingsSwitchRow(
            icon = Icons.Filled.PrivacyTip,
            title = stringResource(R.string.settings_hide_amounts),
            subtitle = stringResource(R.string.settings_hide_amounts_desc),
            checked = preferences.hideAmounts,
            enabled = !isBusy,
            onCheckedChange = onHideAmountsChanged,
        )
        SettingsSwitchRow(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            title = stringResource(R.string.settings_animate_numbers),
            subtitle = stringResource(R.string.settings_animate_numbers_desc),
            checked = preferences.animateNumbers == true,
            enabled = !isBusy,
            onCheckedChange = onAnimateNumbersChanged,
        )

        Text(
            text = stringResource(R.string.settings_chart_style),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StatsChartStylePreference.entries.toList()) { style ->
                FilterChip(
                    selected = preferences.statsChartStyle == style,
                    onClick = { onChartStyleSelected(style) },
                    enabled = !isBusy,
                    label = { Text(text = stringResource(style.labelResId)) },
                )
            }
        }
    }
}

@Composable
private fun NotificationsSection(
    preferences: SettingsNotificationPreferences,
    deliveryState: SettingsNotificationDeliveryState,
    isBusy: Boolean,
    onNotificationChanged: (NotificationPreferenceKey, Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    SettingsSection(
        icon = Icons.Filled.Notifications,
        title = stringResource(R.string.settings_notifications_section),
    ) {
        NotificationDeliveryStatusCard(
            deliveryState = deliveryState,
            onOpenNotificationSettings = onOpenNotificationSettings,
        )
        NotificationPreferenceKey.entries.forEach { key ->
            SettingsSwitchRow(
                icon = key.icon,
                title = stringResource(key.titleResId),
                subtitle = stringResource(key.subtitleResId),
                checked = key.valueFrom(preferences),
                enabled = !isBusy,
                onCheckedChange = { enabled -> onNotificationChanged(key, enabled) },
            )
        }
    }
}

@Composable
private fun NotificationDeliveryStatusCard(
    deliveryState: SettingsNotificationDeliveryState,
    onOpenNotificationSettings: () -> Unit,
) {
    val isBlocked = deliveryState.blocksDelivery
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (isBlocked) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isBlocked) Icons.Filled.Error else Icons.Filled.CheckCircle,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(deliveryState.titleResId),
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(deliveryState.descriptionResId),
                style = MaterialTheme.typography.bodySmall,
            )
            if (isBlocked) {
                TextButton(onClick = onOpenNotificationSettings) {
                    Text(text = stringResource(R.string.settings_notifications_open_system_settings))
                }
            }
        }
    }
}

@Composable
private fun RatesSection(
    state: SettingsUiState,
    settings: MoneyTrackerSettings,
    onDisplayCurrenciesInputChange: (String) -> Unit,
    onSaveDisplayCurrencies: () -> Unit,
    onRateBaseChange: (String) -> Unit,
    onRateTargetChange: (String) -> Unit,
    onRateDateChange: (String) -> Unit,
    onRateValueChange: (String) -> Unit,
    onSaveRateOverride: () -> Unit,
    onUpdateOnlineRates: () -> Unit,
    onDeleteRateOverride: (Long) -> Unit,
) {
    val currencyOptions = rememberCurrencyOptions()
    val displayCurrencyCodes = state.displayCurrenciesInput.toCurrencyCodesInput()
    SettingsSection(
        icon = Icons.Filled.CurrencyExchange,
        title = stringResource(R.string.settings_rates_section),
    ) {
        Text(
            text = stringResource(R.string.settings_base_currency, settings.baseCurrencyCode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DisplayCurrencySelector(
            selectedCodes = displayCurrencyCodes,
            currencies = currencyOptions,
            isBusy = state.isBusy,
            onSelectedCodesChange = { codes ->
                onDisplayCurrenciesInputChange(codes.joinToString(", "))
            },
        )
        Button(
            onClick = onSaveDisplayCurrencies,
            enabled = !state.isBusy,
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Text(
                text = stringResource(R.string.settings_save_currencies),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Text(
            text = state.latestSnapshotDate?.let { date ->
                stringResource(R.string.settings_latest_snapshot, date)
            } ?: stringResource(R.string.settings_no_snapshots),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onUpdateOnlineRates,
            enabled = !state.isBusy &&
                state.rateBaseInput.isNotBlank() &&
                (
                    state.rateTargetInput.isNotBlank() ||
                        state.displayCurrenciesInput.toCurrencyCodesInput().isNotEmpty()
                    ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(
                text = stringResource(R.string.settings_update_online_rates),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.settings_update_online_rates_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RateSourcePreview(preview = state.ratePreview)

        Text(
            text = stringResource(R.string.settings_manual_rate_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CurrencyPickerField(
                selectedCurrencyCode = state.rateBaseInput,
                currencies = currencyOptions,
                label = stringResource(R.string.settings_rate_base),
                enabled = !state.isBusy,
                dismissText = stringResource(R.string.settings_cancel),
                modifier = Modifier.weight(1f),
                fieldTestTag = "settings-rate-base",
                searchTestTag = "settings-rate-base-search",
                optionTestTagPrefix = "settings-rate-base-option-",
                onCurrencySelected = { currency ->
                    onRateBaseChange(currency.code)
                },
            )
            CurrencyPickerField(
                selectedCurrencyCode = state.rateTargetInput,
                currencies = currencyOptions,
                label = stringResource(R.string.settings_rate_target),
                enabled = !state.isBusy,
                dismissText = stringResource(R.string.settings_cancel),
                modifier = Modifier.weight(1f),
                fieldTestTag = "settings-rate-target",
                searchTestTag = "settings-rate-target-search",
                optionTestTagPrefix = "settings-rate-target-option-",
                onCurrencySelected = { currency ->
                    onRateTargetChange(currency.code)
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.rateDateInput,
                onValueChange = onRateDateChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.settings_rate_date)) },
                singleLine = true,
                enabled = !state.isBusy,
            )
            OutlinedTextField(
                value = state.rateValueInput,
                onValueChange = onRateValueChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.settings_rate_value)) },
                singleLine = true,
                enabled = !state.isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        OutlinedButton(
            onClick = onSaveRateOverride,
            enabled = !state.isBusy &&
                state.rateBaseInput.isNotBlank() &&
                state.rateTargetInput.isNotBlank() &&
                state.rateDateInput.isNotBlank() &&
                state.rateValueInput.isNotBlank(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.settings_save_rate),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Text(
            text = stringResource(R.string.settings_manual_rate_history_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (state.manualRateOverrides.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_no_manual_rates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.manualRateOverrides.forEach { override ->
                ListItem(
                    headlineContent = {
                        Text(text = "${override.baseCurrency} -> ${override.targetCurrency}")
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(
                                R.string.settings_manual_rate_meta,
                                override.effectiveDate,
                                override.rate,
                                stringResource(override.source.labelResId),
                            ),
                        )
                    },
                    trailingContent = {
                        TextButton(
                            onClick = { onDeleteRateOverride(override.id) },
                            enabled = !state.isBusy,
                        ) {
                            Text(text = stringResource(R.string.settings_delete))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DisplayCurrencySelector(
    selectedCodes: List<String>,
    currencies: List<CurrencyInfo>,
    isBusy: Boolean,
    onSelectedCodesChange: (List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_display_currencies),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (selectedCodes.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = selectedCodes,
                    key = { it },
                ) { code ->
                    AssistChip(
                        onClick = {
                            onSelectedCodesChange(selectedCodes.filterNot { it == code })
                        },
                        enabled = !isBusy,
                        label = { Text(code) },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        },
                        modifier = Modifier.testTag("settings-display-currency-$code"),
                    )
                }
            }
        }
        CurrencyPickerField(
            selectedCurrencyCode = null,
            currencies = currencies.filterNot { it.code in selectedCodes },
            label = stringResource(R.string.settings_display_currencies),
            enabled = !isBusy && selectedCodes.size < MAX_SETTINGS_DISPLAY_CURRENCIES,
            dismissText = stringResource(R.string.settings_cancel),
            supportingText = stringResource(R.string.settings_display_currencies_hint),
            modifier = Modifier.fillMaxWidth(),
            fieldTestTag = "settings-display-currency-add",
            searchTestTag = "settings-display-currency-search",
            optionTestTagPrefix = "settings-display-currency-option-",
            onCurrencySelected = { currency ->
                onSelectedCodesChange((selectedCodes + currency.code).distinct())
            },
        )
    }
}

@Composable
private fun CurrencyPickerField(
    selectedCurrencyCode: String?,
    currencies: List<CurrencyInfo>,
    label: String,
    enabled: Boolean,
    dismissText: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    fieldTestTag: String,
    searchTestTag: String,
    optionTestTagPrefix: String,
    onCurrencySelected: (CurrencyInfo) -> Unit,
) {
    val locale = currentLocale()
    MoneyTrackerSearchablePickerField(
        value = selectedCurrencyCode?.currencyDisplayText(currencies).orEmpty(),
        label = label,
        options = currencies,
        optionKey = { it.code },
        optionHeadline = { it.code },
        optionSupporting = { it.displayName },
        optionTrailing = { it.currencySymbolOrNull() },
        optionMatchesQuery = { currency, query ->
            currency.matchesCurrencyQuery(query, locale)
        },
        selectedKey = selectedCurrencyCode,
        enabled = enabled,
        dismissText = dismissText,
        supportingText = supportingText,
        modifier = modifier,
        fieldTestTag = fieldTestTag,
        searchTestTag = searchTestTag,
        optionTestTagPrefix = optionTestTagPrefix,
        onOptionSelected = onCurrencySelected,
    )
}

@Composable
private fun rememberCurrencyOptions(): List<CurrencyInfo> {
    val locale = currentLocale()
    return remember(locale) { IsoCurrencyCatalog.listCurrencies(locale) }
}

@Composable
private fun currentLocale(): Locale {
    return LocalConfiguration.current.locales[0]
}

private fun String.currencyDisplayText(currencies: List<CurrencyInfo>): String {
    return currencies.firstOrNull { it.code == this }?.currencyDisplayText() ?: this
}

@Composable
private fun RateSourcePreview(preview: SettingsRatePreviewUi) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.settings_rate_source_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        when (preview.status) {
            SettingsRatePreviewStatus.Ready -> {
                val rate = preview.resolvedRate
                if (rate != null) {
                    ListItem(
                        headlineContent = {
                            Text(text = "${rate.baseCurrency} -> ${rate.targetCurrency}")
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(
                                    R.string.settings_rate_source_meta,
                                    rate.effectiveDate,
                                    stringResource(rate.source.labelResId),
                                    rate.rate,
                                ),
                            )
                        },
                    )
                }
            }
            SettingsRatePreviewStatus.Missing -> {
                Text(
                    text = stringResource(R.string.settings_rate_source_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsRatePreviewStatus.Waiting -> {
                Text(
                    text = stringResource(R.string.settings_rate_source_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DataSection(
    isBusy: Boolean,
    onOpenImportExport: () -> Unit,
    onResetRequested: () -> Unit,
) {
    SettingsSection(
        icon = Icons.Filled.SettingsBackupRestore,
        title = stringResource(R.string.settings_data_section),
    ) {
        OutlinedButton(
            onClick = onOpenImportExport,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.FileDownload, contentDescription = null)
            Text(
                text = stringResource(R.string.settings_open_import_export),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Button(
            onClick = onResetRequested,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text(
                text = stringResource(R.string.settings_reset_data),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.settings_reset_data_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        headlineContent = {
            Text(text = title)
        },
        supportingContent = {
            Text(text = subtitle)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
    )
}

@Composable
private fun SettingsMessageCard(
    icon: ImageVector,
    title: String,
    body: String,
    containerColor: Color,
    contentColor: Color,
    actionText: String? = null,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = body, style = MaterialTheme.typography.bodySmall)
            }
            actionText?.let {
                TextButton(onClick = onAction) {
                    Text(text = it)
                }
            } ?: TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_dismiss))
            }
        }
    }
}

@Composable
private fun InlineLoading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text(
            text = text,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun BusyOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.settings_processing),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

data class SettingsUiState(
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val settings: MoneyTrackerSettings? = null,
    val profiles: List<SettingsProfileUi> = emptyList(),
    val latestSnapshotDate: String? = null,
    val manualRateOverrides: List<SettingsRateOverrideUi> = emptyList(),
    val ratePreview: SettingsRatePreviewUi = SettingsRatePreviewUi(),
    val notificationDeliveryState: SettingsNotificationDeliveryState = SettingsNotificationDeliveryState.Ready,
    val activeProfileLabelInput: String = "",
    val newProfileLabel: String = "",
    val displayCurrenciesInput: String = "",
    val rateBaseInput: String = "",
    val rateTargetInput: String = "",
    val rateDateInput: String = "",
    val rateValueInput: String = "",
    val showResetConfirm: Boolean = false,
    val pendingRateOverwrite: SettingsPendingRateOverwriteUi? = null,
    val error: SettingsUiError? = null,
    val success: SettingsSuccess? = null,
)

data class SettingsProfileUi(
    val id: Long,
    val label: String,
    val languageCode: String,
    val isActive: Boolean,
)

data class SettingsRateOverrideUi(
    val id: Long,
    val effectiveDate: String,
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: String,
    val source: SettingsRateSourceUi,
)

data class SettingsRatePreviewUi(
    val status: SettingsRatePreviewStatus = SettingsRatePreviewStatus.Waiting,
    val resolvedRate: SettingsResolvedRateUi? = null,
)

data class SettingsResolvedRateUi(
    val effectiveDate: String,
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: String,
    val source: SettingsRateSourceUi,
)

data class SettingsPendingRateOverwriteUi(
    val input: SaveExchangeRateOverrideInput,
    val effectiveDate: String,
    val baseCurrency: String,
    val targetCurrency: String,
    val existingRate: String,
    val newRate: String,
)

enum class SettingsRatePreviewStatus {
    Waiting,
    Missing,
    Ready,
}

enum class SettingsRateSourceUi(@StringRes val labelResId: Int) {
    SameCurrency(R.string.settings_rate_source_same),
    ManualOverride(R.string.settings_rate_source_manual),
    Snapshot(R.string.settings_rate_source_snapshot),
}

enum class SettingsNotificationDeliveryState(
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    val blocksDelivery: Boolean,
) {
    Ready(
        R.string.settings_notifications_delivery_ready_title,
        R.string.settings_notifications_delivery_ready_desc,
        false,
    ),
    RuntimePermissionRequired(
        R.string.settings_notifications_delivery_permission_required_title,
        R.string.settings_notifications_delivery_permission_required_desc,
        true,
    ),
    RuntimePermissionDenied(
        R.string.settings_notifications_delivery_permission_denied_title,
        R.string.settings_notifications_delivery_permission_denied_desc,
        true,
    ),
    SystemNotificationsDisabled(
        R.string.settings_notifications_delivery_disabled_title,
        R.string.settings_notifications_delivery_disabled_desc,
        true,
    ),
}

enum class NotificationPreferenceKey(
    @StringRes val titleResId: Int,
    @StringRes val subtitleResId: Int,
    val icon: ImageVector,
) {
    BudgetAlerts(
        R.string.settings_notify_budget_alerts,
        R.string.settings_notify_budget_alerts_desc,
        Icons.Filled.Notifications,
    ),
    RecurringReminders(
        R.string.settings_notify_recurring_reminders,
        R.string.settings_notify_recurring_reminders_desc,
        Icons.Filled.Refresh,
    ),
    WeeklySummary(
        R.string.settings_notify_weekly_summary,
        R.string.settings_notify_weekly_summary_desc,
        Icons.AutoMirrored.Filled.ShowChart,
    ),
    GoalMilestones(
        R.string.settings_notify_goal_milestones,
        R.string.settings_notify_goal_milestones_desc,
        Icons.Filled.CheckCircle,
    );

    fun valueFrom(preferences: SettingsNotificationPreferences): Boolean {
        return when (this) {
            BudgetAlerts -> preferences.notifyBudgetAlerts
            RecurringReminders -> preferences.notifyRecurringReminders
            WeeklySummary -> preferences.notifyWeeklySummary
            GoalMilestones -> preferences.notifyGoalMilestones
        }
    }

    fun toUpdateInput(enabled: Boolean): UpdateSettingsNotificationPreferencesInput {
        return when (this) {
            BudgetAlerts -> UpdateSettingsNotificationPreferencesInput(notifyBudgetAlerts = enabled)
            RecurringReminders -> UpdateSettingsNotificationPreferencesInput(notifyRecurringReminders = enabled)
            WeeklySummary -> UpdateSettingsNotificationPreferencesInput(notifyWeeklySummary = enabled)
            GoalMilestones -> UpdateSettingsNotificationPreferencesInput(notifyGoalMilestones = enabled)
        }
    }
}

enum class SettingsUiError(@StringRes val messageResId: Int) {
    Generic(R.string.settings_error_generic),
    InvalidInput(R.string.settings_error_invalid_input),
    RateUpdateFailed(R.string.settings_error_rate_update_failed),
}

enum class SettingsSuccess(@StringRes val messageResId: Int) {
    Saved(R.string.settings_saved_body),
    ProfileSelected(R.string.settings_profile_selected),
    ProfileSaved(R.string.settings_profile_saved),
    ProfileCreated(R.string.settings_profile_created),
    RateSaved(R.string.settings_rate_saved),
    RateDeleted(R.string.settings_rate_deleted),
    OnlineRatesUpdated(R.string.settings_online_rates_updated),
    DataReset(R.string.settings_data_reset_done),
}

private fun SettingsUiState.toLoadedState(
    settings: MoneyTrackerSettings,
    profiles: List<LocalProfile>,
    latestSnapshotDate: String?,
    overrides: List<ExchangeRateOverride>,
    notificationDeliveryState: SettingsNotificationDeliveryState,
    defaultRateDate: String,
    success: SettingsSuccess?,
): SettingsUiState {
    val activeProfile = profiles.firstOrNull { it.id == settings.profileId }
    val targetCurrency = settings.displayCurrencyCodes.firstOrNull()
        ?.takeUnless { it == settings.baseCurrencyCode }
        ?: settings.baseCurrencyCode.defaultRateTargetCurrency()

    return copy(
        isLoading = false,
        isBusy = false,
        settings = settings,
        profiles = profiles.map { it.toSettingsProfileUi(settings.profileId) },
        latestSnapshotDate = latestSnapshotDate,
        manualRateOverrides = overrides.map(ExchangeRateOverride::toUi),
        notificationDeliveryState = notificationDeliveryState,
        activeProfileLabelInput = activeProfile?.label.orEmpty(),
        newProfileLabel = "",
        displayCurrenciesInput = settings.displayCurrencyCodes.joinToString(", "),
        rateBaseInput = rateBaseInput.ifBlank { settings.baseCurrencyCode },
        rateTargetInput = rateTargetInput.ifBlank { targetCurrency },
        rateDateInput = rateDateInput.ifBlank { defaultRateDate },
        rateValueInput = "",
        showResetConfirm = false,
        pendingRateOverwrite = null,
        error = null,
        success = success,
    )
}

private fun resolveNotificationDeliveryState(
    permissionStatus: NotificationPermissionStatus,
    systemNotificationsEnabled: Boolean,
): SettingsNotificationDeliveryState {
    if (!permissionStatus.canPostNotifications) {
        return when (permissionStatus) {
            NotificationPermissionStatus.Denied -> {
                SettingsNotificationDeliveryState.RuntimePermissionDenied
            }
            NotificationPermissionStatus.NeedsRuntimePermission -> {
                SettingsNotificationDeliveryState.RuntimePermissionRequired
            }
            NotificationPermissionStatus.NotRequired,
            NotificationPermissionStatus.Granted,
            -> SettingsNotificationDeliveryState.RuntimePermissionRequired
        }
    }

    return if (systemNotificationsEnabled) {
        SettingsNotificationDeliveryState.Ready
    } else {
        SettingsNotificationDeliveryState.SystemNotificationsDisabled
    }
}

private fun LocalProfile.toSettingsProfileUi(activeProfileId: Long): SettingsProfileUi {
    return SettingsProfileUi(
        id = id,
        label = label,
        languageCode = languageCode,
        isActive = id == activeProfileId,
    )
}

private fun ExchangeRateOverride.toUi(): SettingsRateOverrideUi {
    return SettingsRateOverrideUi(
        id = id,
        effectiveDate = effectiveDate,
        baseCurrency = baseCurrency,
        targetCurrency = targetCurrency,
        rate = rateE8.formatRateE8(),
        source = SettingsRateSourceUi.ManualOverride,
    )
}

private fun ResolvedExchangeRate.toUi(): SettingsResolvedRateUi {
    return SettingsResolvedRateUi(
        effectiveDate = effectiveDate,
        baseCurrency = baseCurrency,
        targetCurrency = targetCurrency,
        rate = rateE8.formatRateE8(),
        source = source.toUi(),
    )
}

private fun ExchangeRateSource.toUi(): SettingsRateSourceUi {
    return when (this) {
        ExchangeRateSource.SameCurrency -> SettingsRateSourceUi.SameCurrency
        ExchangeRateSource.ManualOverride -> SettingsRateSourceUi.ManualOverride
        ExchangeRateSource.Snapshot -> SettingsRateSourceUi.Snapshot
    }
}

private fun ExchangeRateOverrideAlreadyExistsException.toPendingRateOverwrite(
    input: SaveExchangeRateOverrideInput,
): SettingsPendingRateOverwriteUi {
    return SettingsPendingRateOverwriteUi(
        input = input,
        effectiveDate = existing.effectiveDate,
        baseCurrency = existing.baseCurrency,
        targetCurrency = existing.targetCurrency,
        existingRate = existing.rateE8.formatRateE8(),
        newRate = input.rateE8.formatRateE8(),
    )
}

private suspend fun CurrencyRatesRepository.resolveRatePreview(
    profileId: Long,
    baseCurrency: String,
    targetCurrency: String,
    effectiveDate: String,
): SettingsRatePreviewUi {
    if (baseCurrency.length != MAX_CURRENCY_INPUT ||
        targetCurrency.length != MAX_CURRENCY_INPUT ||
        effectiveDate.length != MAX_RATE_DATE_INPUT
    ) {
        return SettingsRatePreviewUi(status = SettingsRatePreviewStatus.Waiting)
    }

    return try {
        SettingsRatePreviewUi(
            status = SettingsRatePreviewStatus.Ready,
            resolvedRate = getRate(
                profileId = profileId,
                baseCurrency = baseCurrency,
                targetCurrency = targetCurrency,
                effectiveDate = effectiveDate,
            ).toUi(),
        )
    } catch (error: ExchangeRateNotFoundException) {
        SettingsRatePreviewUi(status = SettingsRatePreviewStatus.Missing)
    } catch (error: CurrencyException) {
        SettingsRatePreviewUi(status = SettingsRatePreviewStatus.Waiting)
    }
}

private suspend fun AppPreferencesRepository?.syncUiPreferences(settings: MoneyTrackerSettings) {
    this ?: return
    setTheme(settings.uiPreferences.theme)
    setHideAmounts(settings.uiPreferences.hideAmounts)
    setAnimateNumbers(settings.uiPreferences.animateNumbers)
}

private fun Throwable.toSettingsError(): SettingsUiError {
    return when (this) {
        is SettingsException,
        is CurrencyException,
        is IllegalArgumentException,
        is ArithmeticException,
        -> SettingsUiError.InvalidInput
        else -> SettingsUiError.Generic
    }
}

private fun String.toCurrencyCodesInput(): List<String> {
    return split(CurrencyInputSeparator)
        .map { it.trim().uppercase(Locale.US) }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun String.toRateE8(): Long? {
    return try {
        val rate = BigDecimal(trim().replace(',', '.'))
        if (rate <= BigDecimal.ZERO) {
            null
        } else {
            rate
                .multiply(BigDecimal.valueOf(RATE_SCALE_E8))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
                .takeIf { it > 0 }
        }
    } catch (error: RuntimeException) {
        null
    }
}

private fun Long.formatRateE8(): String {
    return BigDecimal.valueOf(this)
        .divide(BigDecimal.valueOf(RATE_SCALE_E8), 8, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

private fun String.defaultRateTargetCurrency(): String {
    return if (equals("EUR", ignoreCase = true)) {
        "USD"
    } else {
        "EUR"
    }
}

private fun applyAppLanguage(context: Context, languageCode: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)
            .applicationLocales = LocaleList.forLanguageTags(languageCode)
    }
}

private val AppThemePreference.labelResId: Int
    @StringRes get() = when (this) {
        AppThemePreference.System -> R.string.settings_theme_system
        AppThemePreference.Light -> R.string.settings_theme_light
        AppThemePreference.Dark -> R.string.settings_theme_dark
    }

private val StatsChartStylePreference.labelResId: Int
    @StringRes get() = when (this) {
        StatsChartStylePreference.Donut -> R.string.settings_chart_donut
        StatsChartStylePreference.StackedBar -> R.string.settings_chart_stacked_bar
        StatsChartStylePreference.DualBar -> R.string.settings_chart_dual_bar
        StatsChartStylePreference.ProfitBars -> R.string.settings_chart_profit_bars
    }

private data class SettingsLanguageOption(
    val code: String,
    val label: String,
)

private val SettingsLanguageOptions = listOf(
    SettingsLanguageOption("en", "English"),
    SettingsLanguageOption("ru", "Русский"),
    SettingsLanguageOption("uk", "Українська"),
    SettingsLanguageOption("be", "Беларуская"),
    SettingsLanguageOption("kk", "Қазақша"),
    SettingsLanguageOption("uz", "O'zbekcha"),
    SettingsLanguageOption("es", "Español"),
    SettingsLanguageOption("de", "Deutsch"),
    SettingsLanguageOption("it", "Italiano"),
    SettingsLanguageOption("fr", "Français"),
    SettingsLanguageOption("pt", "Português"),
    SettingsLanguageOption("nl", "Nederlands"),
    SettingsLanguageOption("ar", "العربية"),
    SettingsLanguageOption("tr", "Türkçe"),
    SettingsLanguageOption("ko", "한국어"),
    SettingsLanguageOption("ms", "Bahasa Melayu"),
    SettingsLanguageOption("id", "Bahasa Indonesia"),
)

private val CurrencyInputSeparator = Regex("[,;\\s]+")

private const val EMPTY_RATES_SNAPSHOT_DATE = "1970-01-01"
private const val DEFAULT_NEW_PROFILE_LANGUAGE = "en"
private const val MAX_CURRENCY_INPUT = 3
private const val MAX_RATE_DATE_INPUT = 10
private const val MAX_RATE_VALUE_INPUT = 24

@Preview(showBackground = true, widthDp = 360, heightDp = 980)
@Composable
private fun SettingsScreenPreview() {
    MoneyTrackerTheme {
        SettingsScreen(
            state = SettingsUiState(
                settings = MoneyTrackerSettings(
                    profileId = 1L,
                    baseCurrencyCode = "USD",
                    displayCurrencyCodes = listOf("EUR", "RUB"),
                    languageCode = "en",
                    uiPreferences = SettingsUiPreferences(
                        theme = AppThemePreference.System,
                        hideAmounts = false,
                        animateNumbers = true,
                        statsChartStyle = StatsChartStylePreference.Donut,
                    ),
                ),
                profiles = listOf(
                    SettingsProfileUi(1L, "Personal", "en", true),
                    SettingsProfileUi(2L, "Family", "ru", false),
                ),
                latestSnapshotDate = "2026-07-03",
                ratePreview = SettingsRatePreviewUi(
                    status = SettingsRatePreviewStatus.Ready,
                    resolvedRate = SettingsResolvedRateUi(
                        effectiveDate = "2026-07-03",
                        baseCurrency = "USD",
                        targetCurrency = "EUR",
                        rate = "0.93",
                        source = SettingsRateSourceUi.ManualOverride,
                    ),
                ),
                manualRateOverrides = listOf(
                    SettingsRateOverrideUi(
                        id = 1L,
                        effectiveDate = "2026-07-03",
                        baseCurrency = "USD",
                        targetCurrency = "EUR",
                        rate = "0.93",
                        source = SettingsRateSourceUi.ManualOverride,
                    ),
                ),
                activeProfileLabelInput = "Personal",
                newProfileLabel = "",
                displayCurrenciesInput = "EUR, RUB",
                rateBaseInput = "USD",
                rateTargetInput = "EUR",
                rateDateInput = "2026-07-03",
                rateValueInput = "",
            ),
            onRetry = {},
            onDismissMessage = {},
            onSelectProfile = {},
            onProfileLabelInputChange = {},
            onSaveProfileLabel = {},
            onNewProfileLabelChange = {},
            onCreateProfile = {},
            onLanguageSelected = {},
            onThemeSelected = {},
            onHideAmountsChanged = {},
            onAnimateNumbersChanged = {},
            onChartStyleSelected = {},
            onNotificationChanged = { _, _ -> },
            onOpenNotificationSettings = {},
            onDisplayCurrenciesInputChange = {},
            onSaveDisplayCurrencies = {},
            onRateBaseChange = {},
            onRateTargetChange = {},
            onRateDateChange = {},
            onRateValueChange = {},
            onSaveRateOverride = {},
            onUpdateOnlineRates = {},
            onDeleteRateOverride = {},
            onOpenImportExport = {},
            onResetRequested = {},
            onResetDismiss = {},
            onResetConfirmed = {},
        )
    }
}
