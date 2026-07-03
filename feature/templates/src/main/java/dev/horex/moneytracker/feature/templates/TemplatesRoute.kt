package dev.horex.moneytracker.feature.templates

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.horex.moneytracker.core.categories.CategoriesRepository
import dev.horex.moneytracker.core.categories.Category
import dev.horex.moneytracker.core.categories.CategorySortOrder
import dev.horex.moneytracker.core.categories.CategoryType
import dev.horex.moneytracker.core.database.profile.LocalProfileBootstrapper
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import dev.horex.moneytracker.core.money.InvalidMoneyAmountException
import dev.horex.moneytracker.core.money.MoneyOverflowException
import dev.horex.moneytracker.core.money.MoneyParser
import dev.horex.moneytracker.core.templates.ApplyTransactionTemplateInput
import dev.horex.moneytracker.core.templates.CreateTransactionTemplateInput
import dev.horex.moneytracker.core.templates.InvalidTransactionTemplateAmountException
import dev.horex.moneytracker.core.templates.InvalidTransactionTemplateReorderException
import dev.horex.moneytracker.core.templates.TransactionTemplate
import dev.horex.moneytracker.core.templates.TransactionTemplateAccountNotFoundException
import dev.horex.moneytracker.core.templates.TransactionTemplateAmountMode
import dev.horex.moneytracker.core.templates.TransactionTemplateCategoryNotFoundException
import dev.horex.moneytracker.core.templates.TransactionTemplateCategoryTypeException
import dev.horex.moneytracker.core.templates.TransactionTemplateNotFoundException
import dev.horex.moneytracker.core.templates.TransactionTemplatesRepository
import dev.horex.moneytracker.core.templates.UpdateTransactionTemplateInput
import dev.horex.moneytracker.core.transactions.TransactionType
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun TemplatesRoute(
    localProfileBootstrapper: LocalProfileBootstrapper,
    templatesRepository: TransactionTemplatesRepository,
    accountsRepository: AccountsRepository,
    categoriesRepository: CategoriesRepository,
    modifier: Modifier = Modifier,
    onTemplateApplied: () -> Unit = {},
    nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf<Long?>(null) }
    var uiState by remember { mutableStateOf(TemplatesUiState(isLoading = true)) }
    var sheetMode by remember { mutableStateOf<TemplateSheetMode?>(null) }
    var deleteTarget by remember { mutableStateOf<TransactionTemplate?>(null) }
    var variableApplyTarget by remember { mutableStateOf<TransactionTemplate?>(null) }

    suspend fun loadState(activeProfileId: Long): TemplatesUiState {
        return TemplatesUiState(
            templates = templatesRepository.listTemplates(activeProfileId),
            accounts = accountsRepository.listAccounts(activeProfileId),
            categories = categoriesRepository.listCategories(
                profileId = activeProfileId,
                sortOrder = CategorySortOrder.Frequency,
            ),
        )
    }

    fun loadTemplates(showLoading: Boolean) {
        scope.launch {
            if (showLoading) {
                uiState = uiState.copy(isLoading = true, error = null)
            }
            try {
                val profile = localProfileBootstrapper.ensureActiveProfile()
                profileId = profile.id
                uiState = loadState(profile.id)
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    isMutating = false,
                    error = error.toTemplatesError(),
                )
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
                uiState = loadState(activeProfileId)
                if (closeSheet) {
                    sheetMode = null
                }
                deleteTarget = null
                variableApplyTarget = null
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    isMutating = false,
                    error = error.toTemplatesError(),
                )
            }
        }
    }

    fun submitForm(mode: TemplateSheetMode, form: TemplateFormState) {
        val validationError = form.validationError(
            accounts = uiState.accounts,
            categories = uiState.categories.compatibleWith(form.type),
        )
        if (validationError != null) {
            uiState = uiState.copy(error = validationError)
            return
        }

        mutate(closeSheet = true) { activeProfileId ->
            when (mode) {
                TemplateSheetMode.Create -> {
                    templatesRepository.createTemplate(activeProfileId, form.toCreateInput())
                }

                is TemplateSheetMode.Edit -> {
                    templatesRepository.updateTemplate(
                        profileId = activeProfileId,
                        templateId = mode.template.id,
                        input = form.toUpdateInput(),
                    )
                }
            }
        }
    }

    fun applyTemplate(template: TransactionTemplate, variableAmountCents: Long? = null) {
        mutate {
            templatesRepository.applyTemplate(
                profileId = it,
                templateId = template.id,
                input = ApplyTransactionTemplateInput(
                    variableAmountCents = variableAmountCents,
                    createdAtEpochMillis = nowProvider(),
                ),
            )
            onTemplateApplied()
        }
    }

    fun reorderTemplate(template: TransactionTemplate, offset: Int) {
        val currentIndex = uiState.templates.indexOfFirst { it.id == template.id }
        val targetIndex = currentIndex + offset
        if (currentIndex !in uiState.templates.indices || targetIndex !in uiState.templates.indices) {
            return
        }
        val orderedIds = uiState.templates.map { it.id }.toMutableList()
        val moved = orderedIds.removeAt(currentIndex)
        orderedIds.add(targetIndex, moved)
        mutate {
            templatesRepository.reorderTemplates(it, orderedIds)
        }
    }

    LaunchedEffect(localProfileBootstrapper, templatesRepository, accountsRepository, categoriesRepository) {
        loadTemplates(showLoading = true)
    }

    TemplatesScreen(
        state = uiState,
        modifier = modifier,
        onRetry = { loadTemplates(showLoading = true) },
        onAddTemplate = {
            uiState = uiState.copy(error = null)
            sheetMode = TemplateSheetMode.Create
        },
        onEditTemplate = { template ->
            uiState = uiState.copy(error = null)
            sheetMode = TemplateSheetMode.Edit(template)
        },
        onDeleteTemplate = { template ->
            deleteTarget = template
        },
        onApplyTemplate = { template ->
            if (template.amountMode == TransactionTemplateAmountMode.Variable) {
                variableApplyTarget = template
            } else {
                applyTemplate(template)
            }
        },
        onMoveTemplateUp = { reorderTemplate(it, -1) },
        onMoveTemplateDown = { reorderTemplate(it, 1) },
    )

    deleteTarget?.let { template ->
        DeleteTemplateDialog(
            isMutating = uiState.isMutating,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                mutate {
                    templatesRepository.deleteTemplate(it, template.id)
                }
            },
        )
    }

    variableApplyTarget?.let { template ->
        VariableAmountDialog(
            template = template,
            isMutating = uiState.isMutating,
            onDismiss = { variableApplyTarget = null },
            onApply = { amountCents -> applyTemplate(template, amountCents) },
        )
    }

    sheetMode?.let { mode ->
        TemplateFormSheet(
            mode = mode,
            accounts = uiState.accounts,
            categories = uiState.categories,
            error = uiState.error,
            isMutating = uiState.isMutating,
            onDismiss = { sheetMode = null },
            onFormChanged = { uiState = uiState.copy(error = null) },
            onSubmit = { form -> submitForm(mode, form) },
        )
    }
}

@Composable
fun TemplatesScreen(
    state: TemplatesUiState,
    onRetry: () -> Unit,
    onAddTemplate: () -> Unit,
    onEditTemplate: (TransactionTemplate) -> Unit,
    onDeleteTemplate: (TransactionTemplate) -> Unit,
    onApplyTemplate: (TransactionTemplate) -> Unit,
    onMoveTemplateUp: (TransactionTemplate) -> Unit,
    onMoveTemplateDown: (TransactionTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (!state.isLoading && state.canCreateTemplates) {
                FloatingActionButton(
                    onClick = onAddTemplate,
                    modifier = Modifier.testTag("templates-add"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.templates_add),
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
                state.isLoading -> TemplatesLoading()
                else -> TemplatesList(
                    state = state,
                    onAddTemplate = onAddTemplate,
                    onEditTemplate = onEditTemplate,
                    onDeleteTemplate = onDeleteTemplate,
                    onApplyTemplate = onApplyTemplate,
                    onMoveTemplateUp = onMoveTemplateUp,
                    onMoveTemplateDown = onMoveTemplateDown,
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
private fun TemplatesLoading() {
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
            text = stringResource(R.string.templates_loading),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TemplatesList(
    state: TemplatesUiState,
    onAddTemplate: () -> Unit,
    onEditTemplate: (TransactionTemplate) -> Unit,
    onDeleteTemplate: (TransactionTemplate) -> Unit,
    onApplyTemplate: (TransactionTemplate) -> Unit,
    onMoveTemplateUp: (TransactionTemplate) -> Unit,
    onMoveTemplateDown: (TransactionTemplate) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("templates-list"),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TemplatesHeader()
        }

        when {
            !state.canCreateTemplates && state.templates.isEmpty() -> item {
                TemplatesNoSetupState()
            }

            state.templates.isEmpty() -> item {
                TemplatesEmptyState(onAddTemplate = onAddTemplate)
            }

            else -> items(
                items = state.templates,
                key = { it.id },
            ) { template ->
                val index = state.templates.indexOf(template)
                TemplateCard(
                    template = template,
                    enabled = !state.isMutating,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.templates.lastIndex,
                    onEditTemplate = onEditTemplate,
                    onDeleteTemplate = onDeleteTemplate,
                    onApplyTemplate = onApplyTemplate,
                    onMoveTemplateUp = onMoveTemplateUp,
                    onMoveTemplateDown = onMoveTemplateDown,
                )
            }
        }
    }
}

@Composable
private fun TemplatesHeader() {
    Text(
        text = stringResource(R.string.templates_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.templates_subtitle),
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TemplatesEmptyState(onAddTemplate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.AttachMoney,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.templates_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.templates_empty_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onAddTemplate,
            modifier = Modifier.padding(top = 20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.templates_add))
        }
    }
}

@Composable
private fun TemplatesNoSetupState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.templates_no_setup_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.templates_no_setup_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TemplateCard(
    template: TransactionTemplate,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEditTemplate: (TransactionTemplate) -> Unit,
    onDeleteTemplate: (TransactionTemplate) -> Unit,
    onApplyTemplate: (TransactionTemplate) -> Unit,
    onMoveTemplateUp: (TransactionTemplate) -> Unit,
    onMoveTemplateDown: (TransactionTemplate) -> Unit,
) {
    val amountColor = if (template.type == TransactionType.Income) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("template-card-${template.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryMark(
                    categoryIcon = template.categoryIcon,
                    categoryColor = template.categoryColor,
                    fallbackColor = amountColor,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = template.displayName(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = template.accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { onMoveTemplateUp(template) },
                    enabled = enabled && canMoveUp,
                    modifier = Modifier.testTag("template-move-up-${template.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(R.string.templates_move_up),
                    )
                }
                IconButton(
                    onClick = { onMoveTemplateDown(template) },
                    enabled = enabled && canMoveDown,
                    modifier = Modifier.testTag("template-move-down-${template.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(R.string.templates_move_down),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.amountLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                    )
                    Text(
                        text = template.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (template.amountMode == TransactionTemplateAmountMode.Variable) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.templates_variable_chip)) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onApplyTemplate(template) },
                    enabled = enabled,
                    modifier = Modifier.testTag("template-apply-${template.id}"),
                ) {
                    Text(text = stringResource(R.string.templates_apply))
                }
                IconButton(
                    onClick = { onEditTemplate(template) },
                    enabled = enabled,
                    modifier = Modifier.testTag("template-edit-${template.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.templates_edit),
                    )
                }
                IconButton(
                    onClick = { onDeleteTemplate(template) },
                    enabled = enabled,
                    modifier = Modifier.testTag("template-delete-${template.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.templates_delete),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    error: TemplatesError,
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
                Text(text = stringResource(R.string.templates_retry))
            }
        }
    }
}

@Composable
private fun DeleteTemplateDialog(
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
            Text(text = stringResource(R.string.templates_delete_title))
        },
        text = {
            Text(text = stringResource(R.string.templates_delete_body))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isMutating,
            ) {
                Text(text = stringResource(R.string.templates_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.templates_cancel))
            }
        },
    )
}

@Composable
private fun VariableAmountDialog(
    template: TransactionTemplate,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit,
) {
    var amount by remember(template.id) {
        mutableStateOf(MoneyParser.formatPlainCents(template.amountCents))
    }
    var showAmountError by remember(template.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.templates_amount_prompt_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.templates_amount_prompt_body))
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = MoneyParser.sanitizeAmountInput(it)
                        showAmountError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("template-variable-amount"),
                    label = { Text(stringResource(R.string.templates_amount)) },
                    suffix = { Text(template.currencyCode) },
                    isError = showAmountError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (showAmountError) {
                    Text(
                        text = stringResource(R.string.templates_error_amount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountCents = runCatching { MoneyParser.parsePositiveCents(amount) }.getOrNull()
                    if (amountCents == null) {
                        showAmountError = true
                    } else {
                        onApply(amountCents)
                    }
                },
                enabled = !isMutating,
                modifier = Modifier.testTag("template-variable-apply"),
            ) {
                Text(text = stringResource(R.string.templates_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.templates_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateFormSheet(
    mode: TemplateSheetMode,
    accounts: List<Account>,
    categories: List<Category>,
    error: TemplatesError?,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onFormChanged: () -> Unit,
    onSubmit: (TemplateFormState) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val template = (mode as? TemplateSheetMode.Edit)?.template
    var form by remember(mode, accounts.size, categories.size) {
        mutableStateOf(
            TemplateFormState.fromTemplate(
                template = template,
                accounts = accounts,
                categories = categories,
            ),
        )
    }
    val compatibleCategories = categories.compatibleWith(form.type)
    val canSave = !isMutating &&
        form.name.trim().isNotEmpty() &&
        form.amount.isNotBlank() &&
        form.accountId != null &&
        form.categoryId != null

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
                        if (template == null) R.string.templates_add else R.string.templates_edit,
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.templates_cancel))
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = {
                    form = form.copy(name = it.take(MAX_TEMPLATE_NAME_LENGTH))
                    onFormChanged()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("template-name"),
                label = { Text(stringResource(R.string.templates_name)) },
                placeholder = { Text(stringResource(R.string.templates_name_placeholder)) },
                singleLine = true,
            )

            TransactionTypeSelector(
                selectedType = form.type,
                onTypeChange = { type ->
                    val selectedCategoryId = form.categoryId.takeIf { selected ->
                        selected != null && categories.compatibleWith(type).any { it.id == selected }
                    }
                    form = form.copy(type = type, categoryId = selectedCategoryId)
                    onFormChanged()
                },
            )

            AmountModeSelector(
                amountMode = form.amountMode,
                onAmountModeChange = {
                    form = form.copy(amountMode = it)
                    onFormChanged()
                },
            )

            AmountCard(
                amount = form.amount,
                currencyCode = accounts.firstOrNull { it.id == form.accountId }?.currencyCode ?: DEFAULT_CURRENCY,
                isIncome = form.type == TransactionType.Income,
                onAmountChange = {
                    form = form.copy(amount = MoneyParser.sanitizeAmountInput(it))
                    onFormChanged()
                },
            )

            AccountSelector(
                accounts = accounts,
                selectedAccountId = form.accountId,
                onAccountChange = {
                    form = form.copy(accountId = it)
                    onFormChanged()
                },
            )

            CategorySelector(
                categories = compatibleCategories,
                selectedCategoryId = form.categoryId,
                transactionType = form.type,
                onCategoryChange = {
                    form = form.copy(categoryId = it)
                    onFormChanged()
                },
            )

            OutlinedTextField(
                value = form.note,
                onValueChange = {
                    form = form.copy(note = it.take(MAX_NOTE_LENGTH))
                    onFormChanged()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("template-note"),
                label = { Text(stringResource(R.string.templates_note)) },
                placeholder = { Text(stringResource(R.string.templates_note_placeholder)) },
                singleLine = true,
            )

            error?.let {
                FormErrorMessage(error = it)
            }

            Button(
                onClick = { onSubmit(form) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("template-save"),
                enabled = canSave,
            ) {
                Icon(
                    imageVector = if (template == null) Icons.Filled.Add else Icons.Filled.Save,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (template == null) R.string.templates_create else R.string.templates_save,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TransactionTypeSelector(
    selectedType: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransactionType.entries.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeChange(type) },
                modifier = Modifier.testTag("template-type-${type.name}"),
                label = { Text(type.toLabel()) },
                leadingIcon = {
                    if (selectedType == type) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun AmountModeSelector(
    amountMode: TransactionTemplateAmountMode,
    onAmountModeChange: (TransactionTemplateAmountMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.templates_mode),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransactionTemplateAmountMode.entries.forEach { mode ->
                FilterChip(
                    selected = amountMode == mode,
                    onClick = { onAmountModeChange(mode) },
                    modifier = Modifier.testTag("template-mode-${mode.name}"),
                    label = {
                        Text(
                            text = stringResource(
                                when (mode) {
                                    TransactionTemplateAmountMode.Fixed -> R.string.templates_mode_fixed
                                    TransactionTemplateAmountMode.Variable -> R.string.templates_mode_variable
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AmountCard(
    amount: String,
    currencyCode: String,
    isIncome: Boolean,
    onAmountChange: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isIncome) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isIncome) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        ),
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("template-amount"),
            label = { Text(stringResource(R.string.templates_amount)) },
            leadingIcon = {
                Icon(Icons.Filled.AttachMoney, contentDescription = null)
            },
            suffix = { Text(currencyCode) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}

@Composable
private fun AccountSelector(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onAccountChange: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.templates_account),
            style = MaterialTheme.typography.labelLarge,
        )
        if (accounts.isEmpty()) {
            EmptySelectionCard(
                title = stringResource(R.string.templates_no_accounts_title),
                body = stringResource(R.string.templates_no_accounts_body),
                icon = Icons.Filled.AccountBalanceWallet,
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                accounts.forEach { account ->
                    FilterChip(
                        selected = selectedAccountId == account.id,
                        onClick = { onAccountChange(account.id) },
                        modifier = Modifier.testTag("template-account-${account.id}"),
                        label = {
                            Text(
                                text = account.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySelector(
    categories: List<Category>,
    selectedCategoryId: Long?,
    transactionType: TransactionType,
    onCategoryChange: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.templates_category),
            style = MaterialTheme.typography.labelLarge,
        )
        if (categories.isEmpty()) {
            EmptySelectionCard(
                title = stringResource(R.string.templates_no_categories_title),
                body = stringResource(R.string.templates_no_categories_body),
                icon = Icons.AutoMirrored.Filled.Label,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { onCategoryChange(category.id) },
                        modifier = Modifier.testTag("template-category-${category.id}"),
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            CategoryMark(
                                categoryIcon = category.icon,
                                categoryColor = category.color,
                                fallbackColor = if (transactionType == TransactionType.Income) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySelectionCard(
    title: String,
    body: String,
    icon: ImageVector,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryMark(
    categoryIcon: String,
    categoryColor: String,
    fallbackColor: Color,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(categoryColor.toColorOrFallback(fallbackColor)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryIcon.toCategoryIcon(),
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
private fun FormErrorMessage(error: TemplatesError) {
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

data class TemplatesUiState(
    val isLoading: Boolean = false,
    val templates: List<TransactionTemplate> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isMutating: Boolean = false,
    val error: TemplatesError? = null,
) {
    val canCreateTemplates: Boolean =
        accounts.isNotEmpty() &&
            categories.any {
                it.type == CategoryType.Expense ||
                    it.type == CategoryType.Income ||
                    it.type == CategoryType.Both
            }
}

enum class TemplatesError(@StringRes val messageResId: Int) {
    Generic(R.string.templates_error_generic),
    Amount(R.string.templates_error_amount),
    AccountRequired(R.string.templates_error_account_required),
    CategoryRequired(R.string.templates_error_category_required),
    AccountNotFound(R.string.templates_error_account_not_found),
    CategoryNotFound(R.string.templates_error_category_not_found),
    CategoryType(R.string.templates_error_category_type),
    NotFound(R.string.templates_error_not_found),
    Reorder(R.string.templates_error_reorder),
}

private sealed interface TemplateSheetMode {
    data object Create : TemplateSheetMode
    data class Edit(val template: TransactionTemplate) : TemplateSheetMode
}

data class TemplateFormState(
    val name: String,
    val type: TransactionType = TransactionType.Expense,
    val amount: String = "",
    val amountMode: TransactionTemplateAmountMode = TransactionTemplateAmountMode.Fixed,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
) {
    fun validationError(
        accounts: List<Account>,
        categories: List<Category>,
    ): TemplatesError? {
        val parsedAmount = runCatching { MoneyParser.parsePositiveCents(amount) }.getOrNull()
        if (parsedAmount == null) {
            return TemplatesError.Amount
        }
        val selectedAccountId = accountId ?: return TemplatesError.AccountRequired
        if (accounts.none { it.id == selectedAccountId }) {
            return TemplatesError.AccountNotFound
        }
        val selectedCategoryId = categoryId ?: return TemplatesError.CategoryRequired
        if (categories.none { it.id == selectedCategoryId }) {
            return TemplatesError.CategoryRequired
        }
        return null
    }

    fun toCreateInput(): CreateTransactionTemplateInput {
        return CreateTransactionTemplateInput(
            name = name.trim(),
            type = type,
            amountCents = MoneyParser.parsePositiveCents(amount),
            amountMode = amountMode,
            categoryId = requireNotNull(categoryId),
            accountId = requireNotNull(accountId),
            note = note.trim(),
        )
    }

    fun toUpdateInput(): UpdateTransactionTemplateInput {
        return UpdateTransactionTemplateInput(
            name = name.trim(),
            type = type,
            amountCents = MoneyParser.parsePositiveCents(amount),
            amountMode = amountMode,
            categoryId = requireNotNull(categoryId),
            accountId = requireNotNull(accountId),
            note = note.trim(),
        )
    }

    companion object {
        fun fromTemplate(
            template: TransactionTemplate?,
            accounts: List<Account>,
            categories: List<Category>,
        ): TemplateFormState {
            val type = template?.type ?: TransactionType.Expense
            val compatibleCategories = categories.compatibleWith(type)
            return TemplateFormState(
                name = template?.name.orEmpty(),
                type = type,
                amount = template?.amountCents?.let(MoneyParser::formatPlainCents).orEmpty(),
                amountMode = template?.amountMode ?: TransactionTemplateAmountMode.Fixed,
                accountId = template?.accountId
                    ?.takeIf { id -> accounts.any { it.id == id } }
                    ?: accounts.firstOrNull { it.isDefault }?.id
                    ?: accounts.firstOrNull()?.id,
                categoryId = template?.categoryId
                    ?.takeIf { id -> compatibleCategories.any { it.id == id } }
                    ?: compatibleCategories.firstOrNull()?.id,
                note = template?.note.orEmpty(),
            )
        }
    }
}

private fun Throwable.toTemplatesError(): TemplatesError {
    return when (this) {
        is InvalidMoneyAmountException,
        is MoneyOverflowException,
        is InvalidTransactionTemplateAmountException -> TemplatesError.Amount

        is TransactionTemplateAccountNotFoundException -> TemplatesError.AccountNotFound
        is TransactionTemplateCategoryNotFoundException -> TemplatesError.CategoryNotFound
        is TransactionTemplateCategoryTypeException -> TemplatesError.CategoryType
        is TransactionTemplateNotFoundException -> TemplatesError.NotFound
        is InvalidTransactionTemplateReorderException -> TemplatesError.Reorder
        else -> TemplatesError.Generic
    }
}

@Composable
private fun TransactionType.toLabel(): String {
    return stringResource(
        when (this) {
            TransactionType.Expense -> R.string.templates_expense
            TransactionType.Income -> R.string.templates_income
        },
    )
}

private fun List<Category>.compatibleWith(type: TransactionType): List<Category> {
    return filter { category ->
        when (type) {
            TransactionType.Expense -> category.type == CategoryType.Expense || category.type == CategoryType.Both
            TransactionType.Income -> category.type == CategoryType.Income || category.type == CategoryType.Both
        }
    }
}

private fun TransactionTemplate.displayName(): String {
    return name.ifBlank { note.ifBlank { categoryName } }
}

private fun TransactionTemplate.amountLabel(): String {
    val prefix = if (type == TransactionType.Income) "+" else "-"
    return "$prefix${MoneyParser.formatPlainCents(abs(amountCents))} $currencyCode"
}

private fun String.toCategoryIcon(): ImageVector {
    return when (this) {
        "house", "home", "bed" -> Icons.Filled.Home
        "shopping-bag", "basket", "fork-knife" -> Icons.Filled.ShoppingCart
        "briefcase", "salary", "work" -> Icons.Filled.Work
        "money", "currency-dollar", "bank", "wallet" -> Icons.Filled.AccountBalanceWallet
        "arrows-left-right", "transfer" -> Icons.Filled.SwapHoriz
        else -> Icons.AutoMirrored.Filled.Label
    }
}

private fun String.toColorOrFallback(fallback: Color): Color {
    return runCatching { Color(android.graphics.Color.parseColor(this)) }
        .getOrDefault(fallback)
}

private const val DEFAULT_CURRENCY = "USD"
private const val MAX_TEMPLATE_NAME_LENGTH = 40
private const val MAX_NOTE_LENGTH = 120

@Preview(showBackground = true, widthDp = 320, heightDp = 760)
@Composable
private fun TemplatesScreenPreview() {
    MoneyTrackerTheme {
        TemplatesScreen(
            state = TemplatesUiState(
                templates = listOf(
                    templateFixture(
                        id = 1,
                        name = "Lunch",
                        categoryName = "Food",
                        amountCents = 1_250,
                        amountMode = TransactionTemplateAmountMode.Fixed,
                    ),
                    templateFixture(
                        id = 2,
                        name = "Taxi",
                        categoryName = "Transport",
                        amountCents = 2_000,
                        amountMode = TransactionTemplateAmountMode.Variable,
                    ),
                ),
                accounts = accountsFixture,
                categories = categoriesFixture,
            ),
            onRetry = {},
            onAddTemplate = {},
            onEditTemplate = {},
            onDeleteTemplate = {},
            onApplyTemplate = {},
            onMoveTemplateUp = {},
            onMoveTemplateDown = {},
        )
    }
}

private fun templateFixture(
    id: Long,
    name: String,
    categoryName: String,
    amountCents: Long,
    amountMode: TransactionTemplateAmountMode,
): TransactionTemplate {
    return TransactionTemplate(
        id = id,
        profileId = 1,
        name = name,
        type = TransactionType.Expense,
        amountCents = amountCents,
        amountMode = amountMode,
        categoryId = id,
        categoryName = categoryName,
        categoryIcon = "shopping-bag",
        categoryColor = "#EF4444",
        accountId = 1,
        accountName = "Main card",
        currencyCode = "USD",
        note = name,
        sortOrder = id.toInt() - 1,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )
}

private val accountsFixture = listOf(
    Account(
        id = 1,
        profileId = 1,
        name = "Main card",
        icon = "wallet",
        color = "#6366F1",
        type = AccountType.Checking,
        currencyCode = "USD",
        isDefault = true,
        includeInTotal = true,
        balanceCents = 125_50,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    ),
)

private val categoriesFixture = listOf(
    Category(
        id = 1,
        profileId = 1,
        name = "Food",
        icon = "shopping-bag",
        type = CategoryType.Expense,
        color = "#EF4444",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
    Category(
        id = 2,
        profileId = 1,
        name = "Salary",
        icon = "salary",
        type = CategoryType.Income,
        color = "#10B981",
        isProtected = false,
        updatedAtEpochMillis = 1,
        deletedAtEpochMillis = null,
    ),
)
