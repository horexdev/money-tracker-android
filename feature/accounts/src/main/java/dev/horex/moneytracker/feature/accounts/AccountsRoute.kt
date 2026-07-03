package dev.horex.moneytracker.feature.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.horex.moneytracker.core.accounts.Account
import dev.horex.moneytracker.core.accounts.AccountHasRecurringTransactionsException
import dev.horex.moneytracker.core.accounts.AccountHasTemplatesException
import dev.horex.moneytracker.core.accounts.AccountHasTransactionsException
import dev.horex.moneytracker.core.accounts.AccountHasTransfersException
import dev.horex.moneytracker.core.accounts.AccountNameEmptyException
import dev.horex.moneytracker.core.accounts.AccountType
import dev.horex.moneytracker.core.accounts.AccountsRepository
import dev.horex.moneytracker.core.accounts.CannotDeleteLastAccountException
import dev.horex.moneytracker.core.accounts.CreateAccountInput
import dev.horex.moneytracker.core.accounts.CurrencyImmutableException
import dev.horex.moneytracker.core.accounts.InvalidAccountCurrencyException
import dev.horex.moneytracker.core.accounts.MustSetNewDefaultAccountException
import dev.horex.moneytracker.core.accounts.UpdateAccountInput
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.money.MoneyParser
import dev.horex.moneytracker.core.transactions.BalanceAdjustmentInput
import dev.horex.moneytracker.core.transactions.InvalidAdjustmentDeltaException
import dev.horex.moneytracker.core.transactions.TransactionsRepository
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AccountsRoute(
    localProfileBootstrapper: LocalProfileBootstrapper,
    accountsRepository: AccountsRepository,
    transactionsRepository: TransactionsRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Long?>(null) }
    var uiState by remember { mutableStateOf(AccountsUiState(isLoading = true)) }
    var sheetMode by remember { mutableStateOf<AccountSheetMode?>(null) }
    var deleteTarget by remember { mutableStateOf<Account?>(null) }

    fun loadAccounts(showLoading: Boolean) {
        scope.launch {
            if (showLoading) {
                uiState = uiState.copy(isLoading = true, error = null)
            }
            try {
                val profile = localProfileBootstrapper.ensureActiveProfile()
                profileId = profile.id
                uiState = AccountsUiState(
                    accounts = accountsRepository.listAccounts(profile.id),
                )
            } catch (error: Throwable) {
                uiState = AccountsUiState(error = error.toAccountsError())
            }
        }
    }

    fun mutate(closeSheet: Boolean = false, block: suspend (Long) -> Unit) {
        scope.launch {
            val activeProfileId = profileId ?: localProfileBootstrapper.ensureActiveProfile().id
            profileId = activeProfileId
            uiState = uiState.copy(isMutating = true, error = null)
            try {
                block(activeProfileId)
                uiState = AccountsUiState(
                    accounts = accountsRepository.listAccounts(activeProfileId),
                )
                if (closeSheet) {
                    sheetMode = null
                }
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    isMutating = false,
                    error = error.toAccountsError(),
                )
            }
        }
    }

    LaunchedEffect(localProfileBootstrapper, accountsRepository) {
        loadAccounts(showLoading = true)
    }

    AccountsScreen(
        state = uiState,
        modifier = modifier,
        onRetry = { loadAccounts(showLoading = true) },
        onAddAccount = {
            uiState = uiState.copy(error = null)
            sheetMode = AccountSheetMode.Create
        },
        onEditAccount = {
            uiState = uiState.copy(error = null)
            sheetMode = AccountSheetMode.Edit(it)
        },
        onDeleteAccount = { deleteTarget = it },
        onSetDefault = { account ->
            mutate {
                accountsRepository.setDefaultAccount(it, account.id)
            }
        },
    )

    deleteTarget?.let { account ->
        DeleteAccountDialog(
            account = account,
            isMutating = uiState.isMutating,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                mutate {
                    accountsRepository.deleteAccount(it, account.id)
                }
                deleteTarget = null
            },
        )
    }

    sheetMode?.let { mode ->
        AccountFormSheet(
            mode = mode,
            error = uiState.error,
            isMutating = uiState.isMutating,
            onDismiss = { sheetMode = null },
            onCreate = { form ->
                mutate(closeSheet = true) { activeProfileId ->
                    accountsRepository.createAccount(activeProfileId, form.toCreateInput())
                }
            },
            onUpdate = { account, form ->
                mutate(closeSheet = true) { activeProfileId ->
                    accountsRepository.updateAccount(activeProfileId, account.id, form.toUpdateInput())
                }
            },
            onAdjustBalance = { account, deltaCents, note ->
                mutate(closeSheet = true) { activeProfileId ->
                    transactionsRepository.applyBalanceAdjustment(
                        activeProfileId,
                        BalanceAdjustmentInput(
                            accountId = account.id,
                            deltaCents = deltaCents,
                            note = note.trim(),
                        ),
                    )
                }
            },
        )
    }
}

@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onRetry: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onSetDefault: (Account) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (!state.isLoading) {
                FloatingActionButton(
                    onClick = onAddAccount,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.accounts_add),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> AccountsLoading()
                state.accounts.isEmpty() -> AccountsEmptyState(onAddAccount = onAddAccount)
                else -> AccountsList(
                    state = state,
                    onEditAccount = onEditAccount,
                    onDeleteAccount = onDeleteAccount,
                    onSetDefault = onSetDefault,
                )
            }

            state.error?.let { error ->
                ErrorBanner(
                    error = error,
                    onRetry = onRetry,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountsLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.accounts_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AccountsEmptyState(onAddAccount: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.accounts_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.accounts_empty_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onAddAccount,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.accounts_add))
        }
    }
}

@Composable
private fun AccountsList(
    state: AccountsUiState,
    onEditAccount: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onSetDefault: (Account) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.accounts_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.accounts_subtitle),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(
            items = state.accounts,
            key = { it.id },
        ) { account ->
            AccountCard(
                account = account,
                canDelete = state.accounts.size > 1,
                enabled = !state.isMutating,
                onEditAccount = onEditAccount,
                onDeleteAccount = onDeleteAccount,
                onSetDefault = onSetDefault,
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    canDelete: Boolean,
    enabled: Boolean,
    onEditAccount: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onSetDefault: (Account) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(account.color.toColorOrFallback()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = account.type.toAccountIcon(),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (account.isDefault) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(text = stringResource(R.string.accounts_default))
                                },
                                modifier = Modifier.padding(start = 8.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = account.type.toLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = account.balanceCents.formatMoney(account.currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (account.includeInTotal) {
                        stringResource(R.string.accounts_include_total)
                    } else {
                        stringResource(R.string.accounts_excluded_total)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    if (!account.isDefault) {
                        IconButton(
                            onClick = { onSetDefault(account) },
                            enabled = enabled,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.StarBorder,
                                contentDescription = stringResource(R.string.accounts_set_default),
                            )
                        }
                    }
                    IconButton(
                        onClick = { onEditAccount(account) },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.accounts_edit),
                        )
                    }
                    IconButton(
                        onClick = { onDeleteAccount(account) },
                        enabled = enabled && canDelete,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.accounts_delete),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    error: AccountsError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(error.messageResId),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.accounts_retry))
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    account: Account,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Delete, contentDescription = null)
        },
        title = {
            Text(text = stringResource(R.string.accounts_delete_title))
        },
        text = {
            Text(text = stringResource(R.string.accounts_delete_body))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isMutating,
            ) {
                Text(text = stringResource(R.string.accounts_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.accounts_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFormSheet(
    mode: AccountSheetMode,
    error: AccountsError?,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (AccountFormState) -> Unit,
    onUpdate: (Account, AccountFormState) -> Unit,
    onAdjustBalance: (Account, Long, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val account = (mode as? AccountSheetMode.Edit)?.account
    var form by remember(mode) {
        mutableStateOf(AccountFormState.fromAccount(account))
    }
    var adjustTarget by remember(mode) {
        mutableStateOf(account?.balanceCents?.let(MoneyParser::formatPlainCents) ?: "")
    }
    var adjustNote by remember(mode) { mutableStateOf("") }
    val parsedTarget = MoneyParser.parseSignedCentsOrZero(adjustTarget)
    val deltaCents = account?.let { parsedTarget - it.balanceCents } ?: 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (account == null) R.string.accounts_add else R.string.accounts_edit,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.accounts_cancel))
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it.take(MAX_ACCOUNT_NAME_LENGTH)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.accounts_name)) },
                placeholder = { Text(stringResource(R.string.accounts_name_placeholder)) },
                singleLine = true,
            )

            Text(
                text = stringResource(R.string.accounts_type),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccountType.entries.forEach { type ->
                    FilterChip(
                        selected = form.type == type,
                        onClick = { form = form.copy(type = type) },
                        label = { Text(text = type.toLabel()) },
                        leadingIcon = {
                            Icon(
                                imageVector = type.toAccountIcon(),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            OutlinedTextField(
                value = form.currencyCode,
                onValueChange = {
                    form = form.copy(currencyCode = it.uppercase(Locale.US).filter(Char::isLetter).take(3))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.accounts_currency)) },
                singleLine = true,
                enabled = account == null,
            )

            error?.let {
                FormErrorMessage(error = it)
            }

            Text(
                text = stringResource(R.string.accounts_color),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccountColorSwatches.forEach { color ->
                    ColorSwatch(
                        color = color,
                        selected = form.color == color,
                        onSelect = { form = form.copy(color = color) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = form.includeInTotal,
                        role = Role.Switch,
                        onValueChange = { form = form.copy(includeInTotal = it) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.accounts_include_total),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = form.includeInTotal,
                    onCheckedChange = null,
                )
            }

            account?.let { editAccount ->
                BalanceAdjustmentSection(
                    account = editAccount,
                    targetBalance = adjustTarget,
                    note = adjustNote,
                    isMutating = isMutating,
                    deltaCents = deltaCents,
                    onTargetChange = {
                        adjustTarget = MoneyParser.sanitizeSignedAmountInput(it)
                    },
                    onNoteChange = { adjustNote = it.take(MAX_ADJUSTMENT_NOTE_LENGTH) },
                    onApply = {
                        onAdjustBalance(editAccount, deltaCents, adjustNote)
                    },
                )
            }

            Button(
                onClick = {
                    if (account == null) {
                        onCreate(form)
                    } else {
                        onUpdate(account, form)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isMutating && form.name.trim().isNotEmpty(),
            ) {
                Icon(
                    imageVector = if (account == null) Icons.Filled.Add else Icons.Filled.Save,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (account == null) R.string.accounts_create else R.string.accounts_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FormErrorMessage(error: AccountsError) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = stringResource(error.messageResId),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ColorSwatch(
    color: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colorLabel = stringResource(R.string.accounts_color)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color.toColorOrFallback())
            .clickable(onClick = onSelect)
            .semantics {
                contentDescription = "$colorLabel $color"
                this.selected = selected
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun BalanceAdjustmentSection(
    account: Account,
    targetBalance: String,
    note: String,
    isMutating: Boolean,
    deltaCents: Long,
    onTargetChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.accounts_balance_adjustment),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.accounts_current_balance),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = account.balanceCents.formatMoney(account.currencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedTextField(
                value = targetBalance,
                onValueChange = onTargetChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.accounts_target_balance)) },
                suffix = { Text(account.currencyCode) },
                singleLine = true,
            )
            if (deltaCents != 0L) {
                Text(
                    text = stringResource(
                        if (deltaCents > 0) {
                            R.string.accounts_balance_increase
                        } else {
                            R.string.accounts_balance_decrease
                        },
                        kotlin.math.abs(deltaCents).formatMoney(account.currencyCode),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deltaCents > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.accounts_adjust_note)) },
                singleLine = true,
            )
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isMutating && deltaCents != 0L,
            ) {
                Text(text = stringResource(R.string.accounts_apply_adjustment))
            }
        }
    }
}

data class AccountsUiState(
    val isLoading: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val isMutating: Boolean = false,
    val error: AccountsError? = null,
)

private sealed interface AccountSheetMode {
    data object Create : AccountSheetMode
    data class Edit(val account: Account) : AccountSheetMode
}

data class AccountFormState(
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    val color: String,
    val includeInTotal: Boolean,
) {
    fun toCreateInput(): CreateAccountInput {
        return CreateAccountInput(
            name = name.trim(),
            icon = type.storageValue,
            color = color,
            type = type,
            currencyCode = currencyCode,
            includeInTotal = includeInTotal,
        )
    }

    fun toUpdateInput(): UpdateAccountInput {
        return UpdateAccountInput(
            name = name.trim(),
            icon = type.storageValue,
            color = color,
            type = type,
            includeInTotal = includeInTotal,
        )
    }

    companion object {
        fun fromAccount(account: Account?): AccountFormState {
            return AccountFormState(
                name = account?.name.orEmpty(),
                type = account?.type ?: AccountType.Checking,
                currencyCode = account?.currencyCode ?: DEFAULT_CURRENCY,
                color = account?.color ?: AccountColorSwatches.first(),
                includeInTotal = account?.includeInTotal ?: true,
            )
        }
    }
}

enum class AccountsError(@StringRes val messageResId: Int) {
    Generic(R.string.accounts_error_generic),
    EmptyName(R.string.accounts_error_empty_name),
    InvalidCurrency(R.string.accounts_error_invalid_currency),
    CurrencyImmutable(R.string.accounts_error_currency_immutable),
    LastAccount(R.string.accounts_error_last_account),
    SetDefaultFirst(R.string.accounts_error_set_default_first),
    HasTransactions(R.string.accounts_error_has_transactions),
    HasTransfers(R.string.accounts_error_has_transfers),
    HasRecurring(R.string.accounts_error_has_recurring),
    HasTemplates(R.string.accounts_error_has_templates),
    AdjustmentDelta(R.string.accounts_error_adjustment_delta),
}

private fun Throwable.toAccountsError(): AccountsError {
    return when (this) {
        is AccountNameEmptyException -> AccountsError.EmptyName
        is InvalidAccountCurrencyException -> AccountsError.InvalidCurrency
        is CurrencyImmutableException -> AccountsError.CurrencyImmutable
        is CannotDeleteLastAccountException -> AccountsError.LastAccount
        is MustSetNewDefaultAccountException -> AccountsError.SetDefaultFirst
        is AccountHasTransactionsException -> AccountsError.HasTransactions
        is AccountHasTransfersException -> AccountsError.HasTransfers
        is AccountHasRecurringTransactionsException -> AccountsError.HasRecurring
        is AccountHasTemplatesException -> AccountsError.HasTemplates
        is InvalidAdjustmentDeltaException -> AccountsError.AdjustmentDelta
        else -> AccountsError.Generic
    }
}

@Composable
private fun AccountType.toLabel(): String {
    val resId = when (this) {
        AccountType.Checking -> R.string.account_type_checking
        AccountType.Savings -> R.string.account_type_savings
        AccountType.Cash -> R.string.account_type_cash
        AccountType.Credit -> R.string.account_type_credit
        AccountType.Crypto -> R.string.account_type_crypto
    }
    return stringResource(resId)
}

private fun AccountType.toAccountIcon(): ImageVector {
    return Icons.Filled.AccountBalanceWallet
}

private fun String.toColorOrFallback(): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }
        .getOrDefault(Color(0xFF6366F1))
}

private fun Long.formatMoney(currencyCode: String): String {
    return "${MoneyParser.formatPlainCents(this)} $currencyCode"
}

private const val DEFAULT_CURRENCY = "USD"
private const val MAX_ACCOUNT_NAME_LENGTH = 40
private const val MAX_ADJUSTMENT_NOTE_LENGTH = 120

private val AccountColorSwatches = listOf(
    "#6366F1",
    "#10B981",
    "#F59E0B",
    "#EF4444",
    "#8B5CF6",
    "#06B6D4",
    "#64748B",
)

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun AccountsScreenPreview() {
    MoneyTrackerTheme {
        AccountsScreen(
            state = AccountsUiState(
                accounts = listOf(
                    Account(
                        id = 1,
                        profileId = 1,
                        name = "Main card",
                        icon = "checking",
                        color = "#6366F1",
                        type = AccountType.Checking,
                        currencyCode = "USD",
                        isDefault = true,
                        includeInTotal = true,
                        balanceCents = 125_50,
                        createdAtEpochMillis = 1,
                        updatedAtEpochMillis = 1,
                    ),
                    Account(
                        id = 2,
                        profileId = 1,
                        name = "Cash",
                        icon = "cash",
                        color = "#F59E0B",
                        type = AccountType.Cash,
                        currencyCode = "USD",
                        isDefault = false,
                        includeInTotal = false,
                        balanceCents = 42_00,
                        createdAtEpochMillis = 1,
                        updatedAtEpochMillis = 1,
                    ),
                ),
            ),
            onRetry = {},
            onAddAccount = {},
            onEditAccount = {},
            onDeleteAccount = {},
            onSetDefault = {},
        )
    }
}
