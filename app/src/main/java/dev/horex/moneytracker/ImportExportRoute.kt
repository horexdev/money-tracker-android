package dev.horex.moneytracker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.horex.moneytracker.backup.MoneyTrackerBackupDocumentContracts
import dev.horex.moneytracker.backup.MoneyTrackerBackupDocumentExportRequest
import dev.horex.moneytracker.backup.MoneyTrackerBackupDocumentRepository
import dev.horex.moneytracker.backup.MoneyTrackerBackupDocumentRestorePreview
import dev.horex.moneytracker.backup.MoneyTrackerBackupDocumentRestoreProfile
import dev.horex.moneytracker.core.backup.BackupProfile
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupDocumentException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupDocumentExportResult
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupEncryptedPayloadValidationException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupEncryptionException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupExportSelectionException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupImportResult
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupImportSelectionException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupImportValidationException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupInvalidContainerException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupPasswordRequiredException
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupValidationCode
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupValidationIssue
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupValidationSeverity
import dev.horex.moneytracker.core.backup.MoneyTrackerBackupWrongPasswordException
import dev.horex.moneytracker.core.database.profile.LocalProfile
import dev.horex.moneytracker.core.database.profile.LocalProfileRepository
import dev.horex.moneytracker.core.designsystem.theme.MoneyTrackerTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun ImportExportRoute(
    localProfileRepository: LocalProfileRepository,
    backupDocumentRepository: MoneyTrackerBackupDocumentRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ImportExportUiState(isLoading = true)) }
    var pendingExportRequest by remember { mutableStateOf<PendingExportRequest?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePreview by remember { mutableStateOf<MoneyTrackerBackupDocumentRestorePreview?>(null) }

    fun loadProfiles(showLoading: Boolean = true) {
        scope.launch {
            if (showLoading) {
                state = state.copy(isLoading = true, error = null)
            }
            try {
                localProfileRepository.ensureActiveProfile()
                val profiles = localProfileRepository.listProfiles().map(LocalProfile::toExportProfileUi)
                val availableIds = profiles.mapTo(linkedSetOf()) { it.id }
                val selectedIds = state.selectedExportProfileIds
                    .takeIf { it.isNotEmpty() }
                    ?.filterTo(linkedSetOf()) { it in availableIds }
                    ?.takeIf { it.isNotEmpty() }
                    ?: availableIds
                state = state.copy(
                    isLoading = false,
                    exportProfiles = profiles,
                    selectedExportProfileIds = selectedIds,
                    error = null,
                )
            } catch (error: Throwable) {
                state = state.copy(
                    isLoading = false,
                    isBusy = false,
                    error = error.toImportExportError(),
                )
            }
        }
    }

    fun exportToDocument(uri: Uri, request: PendingExportRequest) {
        scope.launch {
            state = state.copy(isBusy = true, error = null, exportResult = null)
            val password = request.password?.toCharArray()
            try {
                val result = backupDocumentRepository.exportToDocument(
                    uri = uri,
                    request = MoneyTrackerBackupDocumentExportRequest(
                        selectedLocalProfileIds = request.selectedLocalProfileIds,
                        encrypted = request.encrypted,
                        password = password,
                    ),
                )
                state = state.copy(
                    isBusy = false,
                    exportResult = result.toExportResultUi(),
                    error = null,
                    exportPassword = "",
                )
            } catch (error: Throwable) {
                state = state.copy(
                    isBusy = false,
                    error = error.toImportExportError(),
                )
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun loadRestorePreview(uri: Uri, passwordText: String?) {
        scope.launch {
            state = state.copy(
                isBusy = true,
                error = null,
                restoreResult = null,
                restoreDocumentSelected = true,
            )
            val password = passwordText
                ?.takeIf(String::isNotBlank)
                ?.toCharArray()
            try {
                val preview = backupDocumentRepository.loadRestorePreview(uri, password)
                restorePreview = preview
                val previewUi = preview.toRestorePreviewUi()
                state = state.copy(
                    isBusy = false,
                    restorePasswordRequired = false,
                    restorePassword = "",
                    restorePreview = previewUi,
                    selectedRestoreProfileRefs = previewUi.profiles.mapTo(linkedSetOf()) { it.ref },
                    restoreProfileLabels = previewUi.profiles.associate { it.ref to it.defaultLocalLabel },
                    error = null,
                )
            } catch (error: MoneyTrackerBackupPasswordRequiredException) {
                restorePreview = null
                state = state.copy(
                    isBusy = false,
                    restorePasswordRequired = true,
                    restorePreview = null,
                    selectedRestoreProfileRefs = emptySet(),
                    restoreProfileLabels = emptyMap(),
                    error = ImportExportError.RestorePasswordRequired,
                )
            } catch (error: Throwable) {
                restorePreview = null
                state = state.copy(
                    isBusy = false,
                    restorePreview = null,
                    selectedRestoreProfileRefs = emptySet(),
                    restoreProfileLabels = emptyMap(),
                    error = error.toImportExportError(),
                )
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    fun restoreSelectedProfiles() {
        val preview = restorePreview ?: return
        val selections = state.selectedRestoreProfileRefs.map { ref ->
            MoneyTrackerBackupDocumentRestoreProfile(
                ref = ref,
                localLabel = state.restoreProfileLabels[ref].orEmpty(),
            )
        }
        scope.launch {
            state = state.copy(isBusy = true, error = null, restoreResult = null)
            try {
                val result = backupDocumentRepository.restorePreview(
                    preview = preview,
                    selectedProfiles = selections,
                )
                state = state.copy(
                    isBusy = false,
                    restoreResult = result.toRestoreResultUi(),
                    restorePassword = "",
                    error = null,
                )
                loadProfiles(showLoading = false)
            } catch (error: Throwable) {
                state = state.copy(
                    isBusy = false,
                    error = error.toImportExportError(),
                )
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        MoneyTrackerBackupDocumentContracts.CreateBackupDocument(),
    ) { uri ->
        val request = pendingExportRequest
        pendingExportRequest = null
        if (uri != null && request != null) {
            exportToDocument(uri, request)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        MoneyTrackerBackupDocumentContracts.OpenBackupDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            loadRestorePreview(uri, state.restorePassword)
        }
    }

    LaunchedEffect(localProfileRepository) {
        loadProfiles(showLoading = true)
    }

    ImportExportScreen(
        state = state,
        modifier = modifier,
        onRetry = { loadProfiles(showLoading = true) },
        onToggleExportProfile = { profileId, selected ->
            state = state.copy(
                selectedExportProfileIds = state.selectedExportProfileIds.toggle(profileId, selected),
                exportResult = null,
                error = null,
            )
        },
        onSelectAllExportProfiles = {
            state = state.copy(
                selectedExportProfileIds = state.exportProfiles.mapTo(linkedSetOf()) { it.id },
                exportResult = null,
                error = null,
            )
        },
        onClearExportProfiles = {
            state = state.copy(
                selectedExportProfileIds = emptySet(),
                exportResult = null,
                error = null,
            )
        },
        onExportEncryptedChange = { encrypted ->
            state = state.copy(
                exportEncrypted = encrypted,
                exportResult = null,
                error = null,
            )
        },
        onExportPasswordChange = { password ->
            state = state.copy(exportPassword = password.take(MAX_BACKUP_PASSWORD_LENGTH), error = null)
        },
        onExport = {
            val encrypted = state.exportEncrypted
            val password = state.exportPassword.takeIf { encrypted }
            pendingExportRequest = PendingExportRequest(
                selectedLocalProfileIds = state.selectedExportProfileIds,
                encrypted = encrypted,
                password = password,
            )
            createDocumentLauncher.launch(defaultBackupFileName(encrypted))
        },
        onChooseRestoreDocument = {
            openDocumentLauncher.launch(Unit)
        },
        onRestorePasswordChange = { password ->
            state = state.copy(restorePassword = password.take(MAX_BACKUP_PASSWORD_LENGTH), error = null)
        },
        onPreviewRestoreDocument = {
            pendingRestoreUri?.let { uri ->
                loadRestorePreview(uri, state.restorePassword)
            }
        },
        onToggleRestoreProfile = { ref, selected ->
            state = state.copy(
                selectedRestoreProfileRefs = state.selectedRestoreProfileRefs.toggle(ref, selected),
                restoreResult = null,
                error = null,
            )
        },
        onRestoreProfileLabelChange = { ref, label ->
            state = state.copy(
                restoreProfileLabels = state.restoreProfileLabels + (ref to label.take(MAX_PROFILE_LABEL_LENGTH)),
                restoreResult = null,
                error = null,
            )
        },
        onRestore = ::restoreSelectedProfiles,
    )
}

@Composable
fun ImportExportScreen(
    state: ImportExportUiState,
    onRetry: () -> Unit,
    onToggleExportProfile: (Long, Boolean) -> Unit,
    onSelectAllExportProfiles: () -> Unit,
    onClearExportProfiles: () -> Unit,
    onExportEncryptedChange: (Boolean) -> Unit,
    onExportPasswordChange: (String) -> Unit,
    onExport: () -> Unit,
    onChooseRestoreDocument: () -> Unit,
    onRestorePasswordChange: (String) -> Unit,
    onPreviewRestoreDocument: () -> Unit,
    onToggleRestoreProfile: (String, Boolean) -> Unit,
    onRestoreProfileLabelChange: (String, String) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                ImportExportHeader()
            }
            state.error?.let { error ->
                item {
                    ImportExportErrorBanner(error = error, onRetry = onRetry)
                }
            }
            state.exportResult?.let { result ->
                item {
                    ImportExportResultBanner(
                        icon = Icons.Filled.CheckCircle,
                        title = stringResource(R.string.import_export_export_done),
                        body = stringResource(
                            R.string.import_export_export_done_body,
                            result.exportedProfiles,
                            result.bytesWritten,
                        ),
                    )
                }
            }
            state.restoreResult?.let { result ->
                item {
                    ImportExportResultBanner(
                        icon = Icons.Filled.CheckCircle,
                        title = stringResource(R.string.import_export_restore_done),
                        body = stringResource(
                            R.string.import_export_restore_done_body,
                            result.importedProfiles,
                            result.importedRows,
                        ),
                    )
                }
            }
            item {
                HorizontalDivider()
            }
            item {
                ExportSection(
                    state = state,
                    onToggleProfile = onToggleExportProfile,
                    onSelectAll = onSelectAllExportProfiles,
                    onClear = onClearExportProfiles,
                    onEncryptedChange = onExportEncryptedChange,
                    onPasswordChange = onExportPasswordChange,
                    onExport = onExport,
                )
            }
            item {
                HorizontalDivider()
            }
            item {
                RestoreSection(
                    state = state,
                    onChooseDocument = onChooseRestoreDocument,
                    onPasswordChange = onRestorePasswordChange,
                    onPreviewDocument = onPreviewRestoreDocument,
                    onToggleProfile = onToggleRestoreProfile,
                    onProfileLabelChange = onRestoreProfileLabelChange,
                    onRestore = onRestore,
                )
            }
        }

        if (state.isBusy) {
            BusyOverlay()
        }
    }
}

@Composable
private fun ImportExportHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.export_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.import_export_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExportSection(
    state: ImportExportUiState,
    onToggleProfile: (Long, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onEncryptedChange: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit,
    onExport: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(
            icon = Icons.Filled.FileDownload,
            title = stringResource(R.string.import_export_export_section),
        )

        if (state.isLoading) {
            InlineLoading(text = stringResource(R.string.import_export_profiles_loading))
        } else if (state.exportProfiles.isEmpty()) {
            EmptySection(text = stringResource(R.string.import_export_no_local_profiles))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSelectAll, enabled = !state.isBusy) {
                    Text(text = stringResource(R.string.import_export_select_all))
                }
                TextButton(onClick = onClear, enabled = !state.isBusy) {
                    Text(text = stringResource(R.string.import_export_clear_selection))
                }
            }
            state.exportProfiles.forEach { profile ->
                ExportProfileCard(
                    profile = profile,
                    selected = profile.id in state.selectedExportProfileIds,
                    enabled = !state.isBusy,
                    onToggle = onToggleProfile,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.isBusy) {
                    onEncryptedChange(!state.exportEncrypted)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.import_export_encrypt_export),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.import_export_encrypt_export_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.exportEncrypted,
                enabled = !state.isBusy,
                onCheckedChange = onEncryptedChange,
            )
        }

        if (state.exportEncrypted) {
            OutlinedTextField(
                value = state.exportPassword,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_export_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        }

        Button(
            onClick = onExport,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("import-export-export-button"),
            enabled = state.canStartExport,
        ) {
            Icon(Icons.Filled.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.import_export_save_backup))
        }
    }
}

@Composable
private fun RestoreSection(
    state: ImportExportUiState,
    onChooseDocument: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onPreviewDocument: () -> Unit,
    onToggleProfile: (String, Boolean) -> Unit,
    onProfileLabelChange: (String, String) -> Unit,
    onRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(
            icon = Icons.Filled.FileUpload,
            title = stringResource(R.string.import_export_restore_section),
        )

        OutlinedButton(
            onClick = onChooseDocument,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isBusy,
        ) {
            Icon(Icons.Filled.Description, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.import_export_choose_backup))
        }

        if (state.restoreDocumentSelected) {
            OutlinedTextField(
                value = state.restorePassword,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_export_restore_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedButton(
                onClick = onPreviewDocument,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isBusy && (!state.restorePasswordRequired || state.restorePassword.isNotBlank()),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.import_export_preview_backup))
            }
        }

        state.restorePreview?.let { preview ->
            RestorePreviewContent(
                preview = preview,
                selectedRefs = state.selectedRestoreProfileRefs,
                labelsByRef = state.restoreProfileLabels,
                isBusy = state.isBusy,
                onToggleProfile = onToggleProfile,
                onProfileLabelChange = onProfileLabelChange,
            )
        }

        Button(
            onClick = onRestore,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("import-export-restore-button"),
            enabled = state.canStartRestore,
        ) {
            Icon(Icons.Filled.Restore, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.import_export_restore_selected))
        }
    }
}

@Composable
private fun RestorePreviewContent(
    preview: RestorePreviewUi,
    selectedRefs: Set<String>,
    labelsByRef: Map<String, String>,
    isBusy: Boolean,
    onToggleProfile: (String, Boolean) -> Unit,
    onProfileLabelChange: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = stringResource(
                            if (preview.encrypted) {
                                R.string.import_export_encrypted
                            } else {
                                R.string.import_export_plain_json
                            },
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (preview.encrypted) Icons.Filled.Lock else Icons.Filled.Description,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = stringResource(
                            R.string.import_export_preview_profile_count,
                            preview.profileCount,
                        ),
                    )
                },
            )
        }

        preview.createdAtEpochMillis?.let { createdAt ->
            Text(
                text = stringResource(
                    R.string.import_export_created_at,
                    createdAt.formatDateTime(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (preview.issues.isNotEmpty()) {
            ValidationIssues(issues = preview.issues)
        }

        if (preview.canRestore) {
            preview.profiles.forEach { profile ->
                RestoreProfileCard(
                    profile = profile,
                    selected = profile.ref in selectedRefs,
                    localLabel = labelsByRef[profile.ref].orEmpty(),
                    enabled = !isBusy,
                    onToggleProfile = onToggleProfile,
                    onProfileLabelChange = onProfileLabelChange,
                )
            }
        }
    }
}

@Composable
private fun ExportProfileCard(
    profile: ExportProfileUi,
    selected: Boolean,
    enabled: Boolean,
    onToggle: (Long, Boolean) -> Unit,
) {
    val displayCurrencies = if (profile.displayCurrencies.isBlank()) {
        stringResource(R.string.import_export_no_currencies)
    } else {
        profile.displayCurrencies
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onToggle(profile.id, !selected) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle(profile.id, it) },
                enabled = enabled,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) {
                Text(
                    text = profile.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.import_export_profile_meta,
                        profile.languageCode.uppercase(),
                        displayCurrencies,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RestoreProfileCard(
    profile: RestoreProfileUi,
    selected: Boolean,
    localLabel: String,
    enabled: Boolean,
    onToggleProfile: (String, Boolean) -> Unit,
    onProfileLabelChange: (String, String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("restore-profile-card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleProfile(profile.ref, it) },
                    enabled = enabled,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        text = profile.backupLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.import_export_restore_profile_counts,
                            profile.counts.totalRows,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = localLabel,
                onValueChange = { onProfileLabelChange(profile.ref, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_export_local_profile_name)) },
                singleLine = true,
                enabled = enabled && selected,
                isError = selected && localLabel.isBlank(),
            )
            Text(
                text = profile.counts.summaryText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValidationIssues(issues: List<MoneyTrackerBackupValidationIssue>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.import_export_validation_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        issues.forEach { issue ->
            ValidationIssueRow(issue = issue)
        }
    }
}

@Composable
private fun ValidationIssueRow(issue: MoneyTrackerBackupValidationIssue) {
    val isError = issue.severity == MoneyTrackerBackupValidationSeverity.Error
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.Error else Icons.Filled.Warning,
                contentDescription = null,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(issue.code.titleResId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (issue.code != MoneyTrackerBackupValidationCode.ForbiddenSourceIdentityField) {
                    Text(
                        text = stringResource(R.string.import_export_validation_path, issue.path),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportExportErrorBanner(
    error: ImportExportError,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Error, contentDescription = null)
            Text(
                text = stringResource(error.messageResId),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (error.retryProfiles) {
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.import_export_retry))
                }
            }
        }
    }
}

@Composable
private fun ImportExportResultBanner(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    icon: ImageVector,
    title: String,
) {
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
}

@Composable
private fun InlineLoading(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(
            text = text,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptySection(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.import_export_processing),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

data class ImportExportUiState(
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val exportProfiles: List<ExportProfileUi> = emptyList(),
    val selectedExportProfileIds: Set<Long> = emptySet(),
    val exportEncrypted: Boolean = false,
    val exportPassword: String = "",
    val exportResult: ExportResultUi? = null,
    val restoreDocumentSelected: Boolean = false,
    val restorePasswordRequired: Boolean = false,
    val restorePassword: String = "",
    val restorePreview: RestorePreviewUi? = null,
    val selectedRestoreProfileRefs: Set<String> = emptySet(),
    val restoreProfileLabels: Map<String, String> = emptyMap(),
    val restoreResult: RestoreResultUi? = null,
    val error: ImportExportError? = null,
) {
    val canStartExport: Boolean
        get() = !isLoading &&
            !isBusy &&
            selectedExportProfileIds.isNotEmpty() &&
            (!exportEncrypted || exportPassword.isNotBlank())

    val canStartRestore: Boolean
        get() = !isBusy &&
            restorePreview?.canRestore == true &&
            selectedRestoreProfileRefs.isNotEmpty() &&
            selectedRestoreProfileRefs.all { ref -> restoreProfileLabels[ref]?.isNotBlank() == true }
}

data class ExportProfileUi(
    val id: Long,
    val label: String,
    val languageCode: String,
    val displayCurrencies: String,
)

data class ExportResultUi(
    val exportedProfiles: Int,
    val bytesWritten: Long,
)

data class RestorePreviewUi(
    val encrypted: Boolean,
    val createdAtEpochMillis: Long?,
    val profileCount: Int,
    val exchangeRateSnapshots: Int,
    val issues: List<MoneyTrackerBackupValidationIssue>,
    val profiles: List<RestoreProfileUi>,
) {
    val canRestore: Boolean
        get() = issues.none { it.severity == MoneyTrackerBackupValidationSeverity.Error } && profiles.isNotEmpty()
}

data class RestoreProfileUi(
    val ref: String,
    val backupLabel: String,
    val defaultLocalLabel: String,
    val counts: RestoreProfileCountsUi,
)

data class RestoreProfileCountsUi(
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val transfers: Int,
    val budgets: Int,
    val recurringTransactions: Int,
    val savingsGoals: Int,
    val goalTransactions: Int,
    val exchangeRateOverrides: Int,
    val transactionTemplates: Int,
) {
    val totalRows: Int
        get() = accounts +
            categories +
            transactions +
            transfers +
            budgets +
            recurringTransactions +
            savingsGoals +
            goalTransactions +
            exchangeRateOverrides +
            transactionTemplates

    @Composable
    fun summaryText(): String {
        return stringResource(
            R.string.import_export_restore_profile_summary,
            accounts,
            categories,
            transactions,
            transfers,
        )
    }
}

data class RestoreResultUi(
    val importedProfiles: Int,
    val importedRows: Int,
)

enum class ImportExportError(
    @StringRes val messageResId: Int,
    val retryProfiles: Boolean = false,
) {
    Generic(R.string.import_export_error_generic),
    DocumentAccess(R.string.import_export_error_document),
    RestorePasswordRequired(R.string.import_export_error_password_required),
    WrongPassword(R.string.import_export_error_wrong_password),
    InvalidEncryptedContainer(R.string.import_export_error_invalid_encrypted_container),
    ValidationBlocksRestore(R.string.import_export_error_validation_blocks_restore),
    ProfileSelection(R.string.import_export_error_profile_selection),
    InvalidInput(R.string.import_export_error_invalid_input),
    ProfilesLoad(R.string.import_export_error_profiles_load, retryProfiles = true),
}

private data class PendingExportRequest(
    val selectedLocalProfileIds: Set<Long>,
    val encrypted: Boolean,
    val password: String?,
)

private fun LocalProfile.toExportProfileUi(): ExportProfileUi {
    return ExportProfileUi(
        id = id,
        label = label,
        languageCode = languageCode,
        displayCurrencies = displayCurrencies.joinToString(", "),
    )
}

private fun MoneyTrackerBackupDocumentRestorePreview.toRestorePreviewUi(): RestorePreviewUi {
    val backup = backup
    return RestorePreviewUi(
        encrypted = encrypted,
        createdAtEpochMillis = backup?.createdAtEpochMillis,
        profileCount = backup?.profiles?.size ?: 0,
        exchangeRateSnapshots = backup?.exchangeRateSnapshots?.size ?: 0,
        issues = validation.issues,
        profiles = backup?.profiles.orEmpty().mapIndexed { index, profile ->
            profile.toRestoreProfileUi(index)
        },
    )
}

private fun BackupProfile.toRestoreProfileUi(index: Int): RestoreProfileUi {
    val fallbackLabel = "Profile ${index + 1}"
    val displayLabel = label.ifBlank { fallbackLabel }
    return RestoreProfileUi(
        ref = ref,
        backupLabel = displayLabel,
        defaultLocalLabel = displayLabel,
        counts = RestoreProfileCountsUi(
            accounts = accounts.size,
            categories = categories.size,
            transactions = transactions.size,
            transfers = transfers.size,
            budgets = budgets.size,
            recurringTransactions = recurringTransactions.size,
            savingsGoals = savingsGoals.size,
            goalTransactions = goalTransactions.size,
            exchangeRateOverrides = exchangeRateOverrides.size,
            transactionTemplates = transactionTemplates.size,
        ),
    )
}

private fun MoneyTrackerBackupDocumentExportResult.toExportResultUi(): ExportResultUi {
    return ExportResultUi(
        exportedProfiles = exportedProfiles,
        bytesWritten = bytesWritten,
    )
}

private fun MoneyTrackerBackupImportResult.toRestoreResultUi(): RestoreResultUi {
    return RestoreResultUi(
        importedProfiles = importedProfileCount,
        importedRows = importedProfiles.sumOf { importedProfile ->
            importedProfile.counts.accounts +
                importedProfile.counts.categories +
                importedProfile.counts.transactions +
                importedProfile.counts.transfers +
                importedProfile.counts.budgets +
                importedProfile.counts.recurringTransactions +
                importedProfile.counts.savingsGoals +
                importedProfile.counts.goalTransactions +
                importedProfile.counts.exchangeRateOverrides +
                importedProfile.counts.transactionTemplates
        },
    )
}

private fun Set<Long>.toggle(value: Long, selected: Boolean): Set<Long> {
    return if (selected) {
        this + value
    } else {
        this - value
    }
}

private fun Set<String>.toggle(value: String, selected: Boolean): Set<String> {
    return if (selected) {
        this + value
    } else {
        this - value
    }
}

private fun defaultBackupFileName(encrypted: Boolean): String {
    val defaultName = MoneyTrackerBackupDocumentContracts.defaultBackupFileName()
    return if (encrypted) {
        defaultName.replace(".json", "-encrypted.json")
    } else {
        defaultName
    }
}

private fun Long.formatDateTime(): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(this))
}

private fun Throwable.toImportExportError(): ImportExportError {
    return when (this) {
        is MoneyTrackerBackupDocumentException -> ImportExportError.DocumentAccess
        is MoneyTrackerBackupPasswordRequiredException -> ImportExportError.RestorePasswordRequired
        is MoneyTrackerBackupWrongPasswordException -> ImportExportError.WrongPassword
        is MoneyTrackerBackupInvalidContainerException -> ImportExportError.InvalidEncryptedContainer
        is MoneyTrackerBackupEncryptedPayloadValidationException -> ImportExportError.ValidationBlocksRestore
        is MoneyTrackerBackupImportValidationException -> ImportExportError.ValidationBlocksRestore
        is MoneyTrackerBackupImportSelectionException -> ImportExportError.ProfileSelection
        is MoneyTrackerBackupExportSelectionException -> ImportExportError.ProfileSelection
        is MoneyTrackerBackupEncryptionException -> ImportExportError.InvalidEncryptedContainer
        is IllegalArgumentException -> ImportExportError.InvalidInput
        else -> ImportExportError.Generic
    }
}

private val MoneyTrackerBackupValidationCode.titleResId: Int
    @StringRes get() = when (this) {
        MoneyTrackerBackupValidationCode.InvalidJson -> R.string.import_export_validation_invalid_json
        MoneyTrackerBackupValidationCode.InvalidBackupShape -> R.string.import_export_validation_invalid_shape
        MoneyTrackerBackupValidationCode.UnsupportedFormat -> R.string.import_export_validation_unsupported_format
        MoneyTrackerBackupValidationCode.UnsupportedVersion -> R.string.import_export_validation_unsupported_version
        MoneyTrackerBackupValidationCode.EmptyBackup -> R.string.import_export_validation_empty_backup
        MoneyTrackerBackupValidationCode.InvalidRef -> R.string.import_export_validation_invalid_ref
        MoneyTrackerBackupValidationCode.DuplicateRef -> R.string.import_export_validation_duplicate_ref
        MoneyTrackerBackupValidationCode.ForbiddenRefPart -> R.string.import_export_validation_forbidden_ref
        MoneyTrackerBackupValidationCode.UnresolvedRef -> R.string.import_export_validation_unresolved_ref
        MoneyTrackerBackupValidationCode.ForbiddenSourceIdentityField ->
            R.string.import_export_validation_forbidden_source_identity
    }

private const val MAX_BACKUP_PASSWORD_LENGTH = 128
private const val MAX_PROFILE_LABEL_LENGTH = 48

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun ImportExportScreenPreview() {
    MoneyTrackerTheme {
        ImportExportScreen(
            state = ImportExportUiState(
                exportProfiles = listOf(
                    ExportProfileUi(1, "Personal", "en", "USD, EUR"),
                    ExportProfileUi(2, "Work", "en", "USD"),
                ),
                selectedExportProfileIds = setOf(1),
                restoreDocumentSelected = true,
                restorePreview = RestorePreviewUi(
                    encrypted = true,
                    createdAtEpochMillis = 1_720_000_000_000,
                    profileCount = 1,
                    exchangeRateSnapshots = 4,
                    issues = emptyList(),
                    profiles = listOf(
                        RestoreProfileUi(
                            ref = "profile:1",
                            backupLabel = "Imported profile",
                            defaultLocalLabel = "Imported profile",
                            counts = RestoreProfileCountsUi(
                                accounts = 3,
                                categories = 12,
                                transactions = 48,
                                transfers = 4,
                                budgets = 2,
                                recurringTransactions = 1,
                                savingsGoals = 1,
                                goalTransactions = 2,
                                exchangeRateOverrides = 0,
                                transactionTemplates = 3,
                            ),
                        ),
                    ),
                ),
                selectedRestoreProfileRefs = setOf("profile:1"),
                restoreProfileLabels = mapOf("profile:1" to "Imported profile"),
            ),
            onRetry = {},
            onToggleExportProfile = { _, _ -> },
            onSelectAllExportProfiles = {},
            onClearExportProfiles = {},
            onExportEncryptedChange = {},
            onExportPasswordChange = {},
            onExport = {},
            onChooseRestoreDocument = {},
            onRestorePasswordChange = {},
            onPreviewRestoreDocument = {},
            onToggleRestoreProfile = { _, _ -> },
            onRestoreProfileLabelChange = { _, _ -> },
            onRestore = {},
        )
    }
}
