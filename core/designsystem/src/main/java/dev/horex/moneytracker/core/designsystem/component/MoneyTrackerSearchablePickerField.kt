package dev.horex.moneytracker.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun <T> MoneyTrackerSearchablePickerField(
    value: String,
    label: String,
    options: List<T>,
    optionKey: (T) -> String,
    optionHeadline: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedKey: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    searchLabel: String = label,
    dismissText: String? = null,
    optionSupporting: (T) -> String? = { null },
    optionTrailing: (T) -> String? = { null },
    optionMatchesQuery: (T, String) -> Boolean = { option, query ->
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) {
            true
        } else {
            listOfNotNull(
                optionKey(option),
                optionHeadline(option),
                optionSupporting(option),
                optionTrailing(option),
            ).any { text ->
                text.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
        }
    },
    fieldTestTag: String? = null,
    searchTestTag: String? = null,
    optionTestTagPrefix: String? = null,
) {
    var isDialogOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val visibleOptions = remember(options, query) {
        options.filter { option -> optionMatchesQuery(option, query) }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = supportingText?.let { { Text(it) } },
            readOnly = true,
            singleLine = true,
            enabled = enabled,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(fieldTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                .clickable(enabled = enabled) {
                    query = ""
                    isDialogOpen = true
                }
                .semantics {
                    contentDescription = label
                    role = Role.Button
                },
        )
    }

    if (isDialogOpen) {
        AlertDialog(
            onDismissRequest = { isDialogOpen = false },
            title = { Text(text = label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(searchTestTag?.let { Modifier.testTag(it) } ?: Modifier),
                        label = { Text(searchLabel) },
                        singleLine = true,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                    ) {
                        items(
                            items = visibleOptions,
                            key = optionKey,
                        ) { option ->
                            val key = optionKey(option)
                            val selected = selectedKey == key
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        optionTestTagPrefix?.let { prefix ->
                                            Modifier.testTag("$prefix$key")
                                        } ?: Modifier,
                                    )
                                    .clickable {
                                        onOptionSelected(option)
                                        isDialogOpen = false
                                        query = ""
                                    },
                                headlineContent = {
                                    Text(
                                        text = optionHeadline(option),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                supportingContent = optionSupporting(option)?.let { supporting ->
                                    {
                                        Text(
                                            text = supporting,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                trailingContent = optionTrailing(option)?.let { trailing ->
                                    {
                                        Text(
                                            text = trailing,
                                            modifier = Modifier.padding(start = 8.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = dismissText?.let { text ->
                {
                    TextButton(onClick = { isDialogOpen = false }) {
                        Text(text = text)
                    }
                }
            },
        )
    }
}
