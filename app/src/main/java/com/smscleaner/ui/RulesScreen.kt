@file:OptIn(ExperimentalMaterial3Api::class)

package com.smscleaner.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smscleaner.Category
import com.smscleaner.MainViewModel
import com.smscleaner.ReadState
import com.smscleaner.Rule
import com.smscleaner.SenderMode
import com.smscleaner.SmsRepository
import com.smscleaner.Templates
import java.util.UUID

@Composable
fun RulesTab(
    vm: MainViewModel,
    actions: AppActions,
    onEdit: (String) -> Unit,
    onTemplate: (Rule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRun by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val enabledCount = vm.rules.count { it.enabled && it.isSafe() }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { ScreenHeader("Rules", "Saved cleanups you can run any time or every day") }

            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Run automatically every day", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (vm.scheduleEnabled) "Daily at %02d:%02d".format(vm.runHour, vm.runMinute)
                                    else "Off",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Switch(checked = vm.scheduleEnabled, onCheckedChange = { vm.updateSchedule(it) })
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                actions.pickTime(vm.runHour, vm.runMinute) { h, m -> vm.setRunTime(h, m) }
                            }) { Text("Change time") }
                            Button(onClick = { confirmRun = true }, enabled = enabledCount > 0) {
                                Text("Run now")
                            }
                        }
                        if (vm.lastRun.isNotEmpty()) {
                            Text(
                                "Last run: ${vm.lastRun}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            item { SectionTitle("Your rules") }
            if (vm.rules.isEmpty()) {
                item {
                    Text(
                        "No rules yet. Pick a quick template below or tap New rule.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            items(vm.rules, key = { it.id }) { rule ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onEdit(rule.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                rule.summary(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(checked = rule.enabled, onCheckedChange = { vm.toggleRule(rule, it) })
                    }
                }
            }

            item { SectionTitle("Quick templates") }
            items(Templates.all) { t ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onTemplate(t.rule) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                t.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Filled.Add, contentDescription = "Use template", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onEdit("") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New rule") },
        )
    }

    if (confirmRun) {
        AlertDialog(
            onDismissRequest = { confirmRun = false },
            title = { Text("Run $enabledCount rule(s) now?") },
            text = { Text("Matching messages will be deleted. A backup copy is saved first, and protected senders are skipped.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRun = false
                    vm.runRulesNow { resultText = it.summary() }
                }) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { confirmRun = false }) { Text("Cancel") } },
        )
    }
    resultText?.let { text ->
        AlertDialog(
            onDismissRequest = { resultText = null },
            title = { Text("Done") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { resultText = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
fun RuleEditorScreen(vm: MainViewModel, ruleId: String?, template: Rule?, onClose: () -> Unit) {
    val existing = remember { vm.rules.firstOrNull { it.id == ruleId } }
    val base = existing ?: template ?: Rule(name = "")
    val id = remember { existing?.id ?: UUID.randomUUID().toString() }

    var name by remember { mutableStateOf(base.name) }
    var enabled by remember { mutableStateOf(base.enabled) }
    var category by remember { mutableStateOf(base.category) }
    var senderMode by remember { mutableStateOf(base.senderMode) }
    var sender by remember { mutableStateOf(base.sender) }
    var keywords by remember { mutableStateOf(base.keywords) }
    var days by remember { mutableStateOf(if (base.olderThanDays > 0) base.olderThanDays.toString() else "") }
    var readState by remember { mutableStateOf(base.readState) }
    var box by remember { mutableStateOf(base.box) }
    var confirmDelete by remember { mutableStateOf(false) }

    val draft = Rule(
        id = id,
        name = name.trim().ifEmpty { "Untitled rule" },
        enabled = enabled,
        senderMode = senderMode,
        sender = sender.trim(),
        keywords = keywords.trim(),
        olderThanDays = days.toIntOrNull() ?: 0,
        readState = readState,
        box = box,
        category = category,
    )
    val regexError = senderMode == SenderMode.REGEX && sender.isNotBlank() &&
        runCatching { Regex(sender) }.isFailure
    val matches = remember(draft, vm.messages, vm.protection) {
        SmsRepository.applyRule(draft, vm.messages, vm.protection)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New rule" else "Edit rule") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete rule")
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Rule name") }, placeholder = { Text("e.g. Old OTPs") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Text("Category", style = MaterialTheme.typography.titleSmall)
            ChipRow(
                options = listOf<Category?>(null) + Category.values().toList(),
                selected = category,
                label = { it?.label ?: "Any" },
                onSelect = { category = it },
            )

            Text("Sender", style = MaterialTheme.typography.titleSmall)
            ChipRow(
                options = SenderMode.values().toList(),
                selected = senderMode,
                label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { senderMode = it },
            )
            if (senderMode != SenderMode.ANY) {
                OutlinedTextField(
                    value = sender, onValueChange = { sender = it },
                    label = { Text("Sender text") },
                    placeholder = { Text("e.g. HDFCBK") },
                    isError = regexError,
                    supportingText = { if (regexError) Text("Not a valid regex") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = keywords, onValueChange = { keywords = it },
                label = { Text("Message contains any of") },
                placeholder = { Text("comma, separated, words") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = days, onValueChange = { days = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Older than (days)") },
                placeholder = { Text("0 = any age") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Text("Read state", style = MaterialTheme.typography.titleSmall)
            ChipRow(
                options = ReadState.values().toList(), selected = readState,
                label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { readState = it },
            )
            Text("Folder", style = MaterialTheme.typography.titleSmall)
            ChipRow(
                options = com.smscleaner.Box.values().toList(), selected = box,
                label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { box = it },
            )

            Card(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(16.dp)) {
                    when {
                        !draft.isSafe() -> Text(
                            "Add a category, sender, keyword or age so this rule doesn't match everything.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> {
                            Text(
                                "${matches.size} message(s) would be deleted",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Live preview. Nothing is deleted until the rule runs. Protected senders are skipped.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            matches.take(4).forEach { m ->
                                Text(
                                    "${m.address}: ${m.body.replace('\n', ' ')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { vm.saveRule(draft); onClose() },
                enabled = draft.isSafe() && !regexError,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text("Save rule") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this rule?") },
            text = { Text("Only the rule is removed. Your messages are not affected.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteRule(existing.id); onClose() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
