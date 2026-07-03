package dev.horex.moneytracker.feature.savings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.horex.moneytracker.core.accounts.Account
import dev.horex.moneytracker.core.accounts.AccountType
import dev.horex.moneytracker.core.accounts.AccountsRepository
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.money.InvalidMoneyAmountException
import dev.horex.moneytracker.core.money.MoneyOverflowException
import dev.horex.moneytracker.core.money.MoneyParser
import dev.horex.moneytracker.core.savings.CreateSavingsGoalInput
import dev.horex.moneytracker.core.savings.InvalidSavingsGoalAmountException
import dev.horex.moneytracker.core.savings.InvalidSavingsGoalCurrencyException
import dev.horex.moneytracker.core.savings.InvalidSavingsGoalDeadlineException
import dev.horex.moneytracker.core.savings.SavingsGoal
import dev.horex.moneytracker.core.savings.SavingsGoalAccountNotFoundException
import dev.horex.moneytracker.core.savings.SavingsGoalCategoryNotFoundException
import dev.horex.moneytracker.core.savings.SavingsGoalHistoryEntry
import dev.horex.moneytracker.core.savings.SavingsGoalInsufficientFundsException
import dev.horex.moneytracker.core.savings.SavingsGoalNameEmptyException
import dev.horex.moneytracker.core.savings.SavingsGoalNotFoundException
import dev.horex.moneytracker.core.savings.SavingsGoalOperationInput
import dev.horex.moneytracker.core.savings.SavingsGoalTransactionType
import dev.horex.moneytracker.core.savings.SavingsGoalsRepository
import dev.horex.moneytracker.core.savings.UpdateSavingsGoalInput
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SavingsRoute(
    localProfileBootstrapper: LocalProfileBootstrapper,
    savingsGoalsRepository: SavingsGoalsRepository,
    accountsRepository: AccountsRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Long?>(null) }
    var state by remember { mutableStateOf(SavingsUiState(isLoading = true)) }
    var goalSheet by remember { mutableStateOf<SavingsGoalSheetMode?>(null) }
    var operationSheet by remember { mutableStateOf<SavingsOperationSheetMode?>(null) }
    var historySheet by remember { mutableStateOf<SavingsHistorySheetState?>(null) }
    var deleteTarget by remember { mutableStateOf<SavingsGoal?>(null) }

    suspend fun load(activeProfileId: Long): SavingsUiState = SavingsUiState(
        goals = savingsGoalsRepository.listGoals(activeProfileId),
        accounts = accountsRepository.listAccounts(activeProfileId),
    )

    fun reload(showLoading: Boolean) {
        scope.launch {
            if (showLoading) state = state.copy(isLoading = true, error = null)
            try {
                val profile = localProfileBootstrapper.ensureActiveProfile()
                profileId = profile.id
                state = load(profile.id)
            } catch (error: Throwable) {
                state = state.copy(isLoading = false, isMutating = false, error = error.toSavingsError())
            }
        }
    }

    fun mutate(
        closeGoalSheet: Boolean = false,
        closeOperationSheet: Boolean = false,
        block: suspend (Long) -> Unit,
    ) {
        scope.launch {
            val activeProfileId = profileId ?: localProfileBootstrapper.ensureActiveProfile().id
            profileId = activeProfileId
            state = state.copy(isMutating = true, error = null)
            try {
                block(activeProfileId)
                state = load(activeProfileId)
                if (closeGoalSheet) goalSheet = null
                if (closeOperationSheet) operationSheet = null
                deleteTarget = null
            } catch (error: Throwable) {
                state = state.copy(isLoading = false, isMutating = false, error = error.toSavingsError())
            }
        }
    }

    fun loadHistory(goal: SavingsGoal) {
        scope.launch {
            val activeProfileId = profileId ?: localProfileBootstrapper.ensureActiveProfile().id
            historySheet = SavingsHistorySheetState(goal = goal, isLoading = true)
            try {
                historySheet = SavingsHistorySheetState(
                    goal = goal,
                    history = savingsGoalsRepository.listHistory(activeProfileId, goal.id),
                )
            } catch (error: Throwable) {
                historySheet = SavingsHistorySheetState(goal = goal, error = error.toSavingsError())
            }
        }
    }

    LaunchedEffect(localProfileBootstrapper, savingsGoalsRepository, accountsRepository) {
        reload(showLoading = true)
    }

    SavingsScreen(
        state = state,
        onRetry = { reload(showLoading = true) },
        onAddGoal = { state = state.copy(error = null); goalSheet = SavingsGoalSheetMode.Create },
        onEditGoal = { state = state.copy(error = null); goalSheet = SavingsGoalSheetMode.Edit(it) },
        onDeleteGoal = { deleteTarget = it },
        onDeposit = { state = state.copy(error = null); operationSheet = SavingsOperationSheetMode.Deposit(it) },
        onWithdraw = { state = state.copy(error = null); operationSheet = SavingsOperationSheetMode.Withdraw(it) },
        onOpenHistory = ::loadHistory,
        modifier = modifier,
    )

    goalSheet?.let { mode ->
        SavingsGoalFormSheet(
            mode = mode,
            accounts = state.accounts,
            error = state.error,
            isMutating = state.isMutating,
            onDismiss = { goalSheet = null },
            onFormChanged = { state = state.copy(error = null) },
            onSubmit = { form ->
                mutate(closeGoalSheet = true) { activeProfileId ->
                    when (mode) {
                        SavingsGoalSheetMode.Create -> savingsGoalsRepository.createGoal(activeProfileId, form.toCreateInput())
                        is SavingsGoalSheetMode.Edit -> savingsGoalsRepository.updateGoal(
                            profileId = activeProfileId,
                            goalId = mode.goal.id,
                            input = form.toUpdateInput(),
                        )
                    }
                }
            },
        )
    }

    operationSheet?.let { mode ->
        SavingsOperationSheet(
            mode = mode,
            error = state.error,
            isMutating = state.isMutating,
            onDismiss = { operationSheet = null },
            onFormChanged = { state = state.copy(error = null) },
            onSubmit = { form ->
                mutate(closeOperationSheet = true) { activeProfileId ->
                    when (mode) {
                        is SavingsOperationSheetMode.Deposit -> savingsGoalsRepository.deposit(activeProfileId, mode.goal.id, form.toInput())
                        is SavingsOperationSheetMode.Withdraw -> savingsGoalsRepository.withdraw(activeProfileId, mode.goal.id, form.toInput())
                    }
                }
            },
        )
    }

    historySheet?.let { SavingsHistorySheet(state = it, onDismiss = { historySheet = null }) }

    deleteTarget?.let { goal ->
        DeleteSavingsGoalDialog(
            goal = goal,
            isMutating = state.isMutating,
            onDismiss = { deleteTarget = null },
            onConfirm = { mutate { savingsGoalsRepository.deleteGoal(it, goal.id) } },
        )
    }
}

@Composable
fun SavingsScreen(
    state: SavingsUiState,
    onRetry: () -> Unit,
    onAddGoal: () -> Unit,
    onEditGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (SavingsGoal) -> Unit,
    onDeposit: (SavingsGoal) -> Unit,
    onWithdraw: (SavingsGoal) -> Unit,
    onOpenHistory: (SavingsGoal) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (!state.isLoading) {
                FloatingActionButton(onClick = onAddGoal, modifier = Modifier.testTag("savings-add")) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.savings_add))
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> SavingsLoading()
                else -> SavingsList(
                    state = state,
                    onAddGoal = onAddGoal,
                    onEditGoal = onEditGoal,
                    onDeleteGoal = onDeleteGoal,
                    onDeposit = onDeposit,
                    onWithdraw = onWithdraw,
                    onOpenHistory = onOpenHistory,
                )
            }
            state.error?.let {
                ErrorBanner(
                    error = it,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SavingsLoading() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.savings_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SavingsList(
    state: SavingsUiState,
    onAddGoal: () -> Unit,
    onEditGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (SavingsGoal) -> Unit,
    onDeposit: (SavingsGoal) -> Unit,
    onWithdraw: (SavingsGoal) -> Unit,
    onOpenHistory: (SavingsGoal) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("savings-list"),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SavingsHeader() }
        if (state.goals.isEmpty()) {
            item { SavingsEmptyState(onAddGoal) }
        } else {
            items(state.goals, key = { it.id }) { goal ->
                SavingsGoalCard(
                    goal = goal,
                    linkedAccountName = state.accountName(goal.accountId),
                    enabled = !state.isMutating,
                    onEditGoal = onEditGoal,
                    onDeleteGoal = onDeleteGoal,
                    onDeposit = onDeposit,
                    onWithdraw = onWithdraw,
                    onOpenHistory = onOpenHistory,
                )
            }
        }
    }
}

@Composable
private fun SavingsHeader() {
    Text(
        text = stringResource(R.string.savings_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.savings_subtitle),
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SavingsEmptyState(onAddGoal: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Savings, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.savings_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(R.string.savings_empty_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddGoal, modifier = Modifier.padding(top = 20.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.savings_add))
        }
    }
}

@Composable
private fun SavingsGoalCard(
    goal: SavingsGoal,
    linkedAccountName: String?,
    enabled: Boolean,
    onEditGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (SavingsGoal) -> Unit,
    onDeposit: (SavingsGoal) -> Unit,
    onWithdraw: (SavingsGoal) -> Unit,
    onOpenHistory: (SavingsGoal) -> Unit,
) {
    val progressPercent = goal.progressPercent.roundToInt()
    Card(
        modifier = Modifier.fillMaxWidth().testTag("savings-goal-${goal.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (goal.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Savings, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = goal.name,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (goal.isCompleted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.savings_completed)) },
                                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.savings_amount_progress,
                            goal.currentCents.formatMoney(goal.currencyCode),
                            goal.targetCents.formatMoney(goal.currencyCode),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    linkedAccountName?.let {
                        Text(
                            text = it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onEditGoal(goal) }, enabled = enabled) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.savings_edit))
                }
            }
            LinearProgressIndicator(
                progress = { (goal.progressPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.savings_progress_percent, progressPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.savings_remaining, goal.remainingCents.formatMoney(goal.currencyCode)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDeposit(goal) }, enabled = enabled, modifier = Modifier.testTag("savings-deposit-${goal.id}")) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.savings_deposit))
                }
                OutlinedButton(onClick = { onWithdraw(goal) }, enabled = enabled && goal.currentCents > 0L, modifier = Modifier.testTag("savings-withdraw-${goal.id}")) {
                    Icon(Icons.Filled.Remove, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.savings_withdraw))
                }
                OutlinedButton(onClick = { onOpenHistory(goal) }, enabled = enabled, modifier = Modifier.testTag("savings-history-${goal.id}")) {
                    Icon(Icons.Filled.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.savings_history))
                }
                IconButton(onClick = { onDeleteGoal(goal) }, enabled = enabled) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.savings_delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsGoalFormSheet(
    mode: SavingsGoalSheetMode,
    accounts: List<Account>,
    error: SavingsError?,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onFormChanged: () -> Unit,
    onSubmit: (SavingsGoalFormState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val goal = (mode as? SavingsGoalSheetMode.Edit)?.goal
    var form by remember(mode, accounts) { mutableStateOf(SavingsGoalFormState.fromGoal(goal, accounts)) }
    val canSave = !isMutating && form.name.isNotBlank() && form.targetAmount.isNotBlank() && form.currencyCode.length == CURRENCY_CODE_LENGTH
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(stringResource(if (goal == null) R.string.savings_add else R.string.savings_edit), onDismiss)
            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it.take(MAX_NAME_LENGTH)); onFormChanged() },
                modifier = Modifier.fillMaxWidth().testTag("savings-name"),
                label = { Text(stringResource(R.string.savings_name)) },
                maxLines = 2,
            )
            OutlinedTextField(
                value = form.targetAmount,
                onValueChange = { form = form.copy(targetAmount = MoneyParser.sanitizeAmountInput(it)); onFormChanged() },
                modifier = Modifier.fillMaxWidth().testTag("savings-target"),
                label = { Text(stringResource(R.string.savings_target)) },
                leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                suffix = { Text(form.currencyCode.ifBlank { DEFAULT_CURRENCY }) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (goal == null) {
                OutlinedTextField(
                    value = form.currencyCode,
                    onValueChange = { form = form.copy(currencyCode = it.normalizedCurrencyCode()); onFormChanged() },
                    modifier = Modifier.fillMaxWidth().testTag("savings-currency"),
                    label = { Text(stringResource(R.string.savings_currency)) },
                    singleLine = true,
                )
            } else {
                Text("${stringResource(R.string.savings_currency)}: ${goal.currencyCode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = form.deadlineDate,
                onValueChange = { form = form.copy(deadlineDate = it.filter { c -> c.isDigit() || c == '-' }.take(DATE_LENGTH)); onFormChanged() },
                modifier = Modifier.fillMaxWidth().testTag("savings-deadline"),
                label = { Text(stringResource(R.string.savings_deadline_optional)) },
                singleLine = true,
            )
            AccountSelector(accounts, form.accountId) { form = form.copy(accountId = it); onFormChanged() }
            error?.let { FormErrorMessage(it) }
            Button(onClick = { onSubmit(form) }, modifier = Modifier.fillMaxWidth(), enabled = canSave) {
                Icon(if (goal == null) Icons.Filled.Add else Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(if (goal == null) R.string.savings_create else R.string.savings_save))
            }
        }
    }
}

@Composable
private fun AccountSelector(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onAccountChange: (Long?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.savings_account), style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedAccountId == null,
                onClick = { onAccountChange(null) },
                label = { Text(stringResource(R.string.savings_no_account)) },
            )
            accounts.forEach { account ->
                FilterChip(
                    selected = selectedAccountId == account.id,
                    onClick = { onAccountChange(account.id) },
                    modifier = Modifier.testTag("savings-account-${account.id}"),
                    label = { Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsOperationSheet(
    mode: SavingsOperationSheetMode,
    error: SavingsError?,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onFormChanged: () -> Unit,
    onSubmit: (SavingsOperationFormState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var form by remember(mode) { mutableStateOf(SavingsOperationFormState()) }
    val isDeposit = mode is SavingsOperationSheetMode.Deposit
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SheetHeader(stringResource(if (isDeposit) R.string.savings_deposit else R.string.savings_withdraw), onDismiss)
            Text(mode.goal.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = form.amount,
                onValueChange = { form = form.copy(amount = MoneyParser.sanitizeAmountInput(it)); onFormChanged() },
                modifier = Modifier.fillMaxWidth().testTag("savings-operation-amount"),
                label = { Text(stringResource(R.string.savings_operation_amount)) },
                leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                suffix = { Text(mode.goal.currencyCode) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { form = form.copy(note = it.take(MAX_NOTE_LENGTH)); onFormChanged() },
                modifier = Modifier.fillMaxWidth().testTag("savings-operation-note"),
                label = { Text(stringResource(R.string.savings_operation_note)) },
                placeholder = { Text(stringResource(R.string.savings_operation_note_placeholder)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                singleLine = true,
            )
            error?.let { FormErrorMessage(it) }
            Button(onClick = { onSubmit(form) }, modifier = Modifier.fillMaxWidth(), enabled = !isMutating && form.amount.isNotBlank()) {
                Icon(if (isDeposit) Icons.Filled.Add else Icons.Filled.Remove, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(if (isDeposit) R.string.savings_deposit else R.string.savings_withdraw))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsHistorySheet(state: SavingsHistorySheetState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(stringResource(R.string.savings_history_title), onDismiss)
            Text(state.goal.name, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                state.isLoading -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                state.error != null -> FormErrorMessage(state.error)
                state.history.isEmpty() -> Text(stringResource(R.string.savings_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.history, key = { it.id }) { entry ->
                        SavingsHistoryRow(entry = entry, currencyCode = state.goal.currencyCode)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingsHistoryRow(entry: SavingsGoalHistoryEntry, currencyCode: String) {
    val isDeposit = entry.type == SavingsGoalTransactionType.Deposit
    val color = if (isDeposit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val prefix = if (isDeposit) "+" else "-"
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isDeposit) Icons.Filled.Add else Icons.Filled.Remove, contentDescription = null, tint = color)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(if (isDeposit) R.string.savings_deposit else R.string.savings_withdraw), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(entry.createdAtEpochMillis.toDisplayDate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$prefix${entry.amountCents.formatMoney(currencyCode)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun SheetHeader(title: String, onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.savings_cancel))
        }
    }
}

@Composable
private fun ErrorBanner(error: SavingsError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(error.messageResId), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.savings_retry)) }
        }
    }
}

@Composable
private fun FormErrorMessage(error: SavingsError) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
        Text(
            text = stringResource(error.messageResId),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DeleteSavingsGoalDialog(
    goal: SavingsGoal,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.savings_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(goal.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.savings_delete_body))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isMutating) {
                Text(stringResource(R.string.savings_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.savings_cancel)) }
        },
    )
}

data class SavingsUiState(
    val isLoading: Boolean = false,
    val goals: List<SavingsGoal> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val isMutating: Boolean = false,
    val error: SavingsError? = null,
) {
    fun accountName(accountId: Long?): String? = accountId?.let { id -> accounts.firstOrNull { it.id == id }?.name }
}

enum class SavingsError(@StringRes val messageResId: Int) {
    Generic(R.string.savings_error_generic),
    Name(R.string.savings_error_name),
    Amount(R.string.savings_error_amount),
    Currency(R.string.savings_error_currency),
    Deadline(R.string.savings_error_deadline),
    Account(R.string.savings_error_account),
    Category(R.string.savings_error_category),
    Funds(R.string.savings_error_funds),
    NotFound(R.string.savings_error_not_found),
}

private sealed interface SavingsGoalSheetMode {
    data object Create : SavingsGoalSheetMode
    data class Edit(val goal: SavingsGoal) : SavingsGoalSheetMode
}

private sealed interface SavingsOperationSheetMode {
    val goal: SavingsGoal
    data class Deposit(override val goal: SavingsGoal) : SavingsOperationSheetMode
    data class Withdraw(override val goal: SavingsGoal) : SavingsOperationSheetMode
}

data class SavingsGoalFormState(
    val name: String,
    val targetAmount: String,
    val currencyCode: String,
    val deadlineDate: String,
    val accountId: Long?,
) {
    fun toCreateInput(): CreateSavingsGoalInput = CreateSavingsGoalInput(
        name = name.trim(),
        targetCents = MoneyParser.parsePositiveCents(targetAmount),
        currencyCode = currencyCode,
        deadlineDate = deadlineDate.trim().ifBlank { null },
        accountId = accountId,
    )

    fun toUpdateInput(): UpdateSavingsGoalInput {
        val deadline = deadlineDate.trim()
        return UpdateSavingsGoalInput(
            name = name.trim(),
            targetCents = MoneyParser.parsePositiveCents(targetAmount),
            deadlineDate = deadline.ifBlank { null },
            clearDeadline = deadline.isBlank(),
            accountId = accountId,
            clearLinkedAccount = accountId == null,
        )
    }

    companion object {
        fun fromGoal(goal: SavingsGoal?, accounts: List<Account>): SavingsGoalFormState = SavingsGoalFormState(
            name = goal?.name.orEmpty(),
            targetAmount = goal?.targetCents?.let(MoneyParser::formatPlainCents).orEmpty(),
            currencyCode = goal?.currencyCode ?: accounts.firstOrNull { it.isDefault }?.currencyCode ?: DEFAULT_CURRENCY,
            deadlineDate = goal?.deadlineDate.orEmpty(),
            accountId = goal?.accountId?.takeIf { id -> accounts.any { it.id == id } },
        )
    }
}

data class SavingsOperationFormState(
    val amount: String = "",
    val note: String = "",
) {
    fun toInput(): SavingsGoalOperationInput = SavingsGoalOperationInput(
        amountCents = MoneyParser.parsePositiveCents(amount),
        note = note.trim().ifBlank { null },
    )
}

data class SavingsHistorySheetState(
    val goal: SavingsGoal,
    val isLoading: Boolean = false,
    val history: List<SavingsGoalHistoryEntry> = emptyList(),
    val error: SavingsError? = null,
)

private fun Throwable.toSavingsError(): SavingsError = when (this) {
    is SavingsGoalNameEmptyException -> SavingsError.Name
    is InvalidMoneyAmountException,
    is MoneyOverflowException,
    is InvalidSavingsGoalAmountException -> SavingsError.Amount
    is InvalidSavingsGoalCurrencyException -> SavingsError.Currency
    is InvalidSavingsGoalDeadlineException -> SavingsError.Deadline
    is SavingsGoalAccountNotFoundException -> SavingsError.Account
    is SavingsGoalCategoryNotFoundException -> SavingsError.Category
    is SavingsGoalInsufficientFundsException -> SavingsError.Funds
    is SavingsGoalNotFoundException -> SavingsError.NotFound
    else -> SavingsError.Generic
}

private fun String.normalizedCurrencyCode(): String = uppercase(Locale.US).filter(Char::isLetter).take(CURRENCY_CODE_LENGTH)

private fun Long.formatMoney(currencyCode: String): String = "${MoneyParser.formatPlainCents(abs(this))} $currencyCode"

private fun Long.toDisplayDate(): String = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().toString()

private const val DEFAULT_CURRENCY = "USD"
private const val CURRENCY_CODE_LENGTH = 3
private const val DATE_LENGTH = 10
private const val MAX_NAME_LENGTH = 160
private const val MAX_NOTE_LENGTH = 120

@Preview(showBackground = true, widthDp = 320, heightDp = 760)
@Composable
private fun SavingsScreenPreview() {
    MoneyTrackerTheme {
        SavingsScreen(
            state = SavingsUiState(
                goals = listOf(
                    savingsGoalFixture(1, "Emergency fund with a very long name that should wrap cleanly", 75_000, 100_000),
                    savingsGoalFixture(2, "Trip", 200_000, 200_000),
                ),
                accounts = accountsFixture,
            ),
            onRetry = {},
            onAddGoal = {},
            onEditGoal = {},
            onDeleteGoal = {},
            onDeposit = {},
            onWithdraw = {},
            onOpenHistory = {},
        )
    }
}

private fun savingsGoalFixture(id: Long, name: String, currentCents: Long, targetCents: Long): SavingsGoal = SavingsGoal(
    id = id,
    profileId = 1,
    name = name,
    targetCents = targetCents,
    currentCents = currentCents,
    currencyCode = "USD",
    deadlineDate = "2026-12-31",
    accountId = 1,
    createdAtEpochMillis = 1,
    updatedAtEpochMillis = 1,
)

private val accountsFixture = listOf(
    Account(
        id = 1,
        profileId = 1,
        name = "Main savings account",
        icon = "wallet",
        color = "#0EA5E9",
        type = AccountType.Savings,
        currencyCode = "USD",
        isDefault = true,
        includeInTotal = true,
        balanceCents = 250_000,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    ),
)
