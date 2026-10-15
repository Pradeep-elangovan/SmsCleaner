@file:OptIn(ExperimentalMaterial3Api::class)

package com.smscleaner.ui

import android.telephony.SmsMessage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smscleaner.MainViewModel
import com.smscleaner.Sender

@Composable
fun NewMessageScreen(
    vm: MainViewModel,
    actions: AppActions,
    initialTo: String,
    initialBody: String,
    onClose: () -> Unit,
    onSent: (String) -> Unit,
) {
    var to by remember { mutableStateOf(initialTo) }
    var body by remember { mutableStateOf(initialBody) }
    var sending by remember { mutableStateOf(false) }

    val q = to.trim()
    val digits = q.filter { it.isDigit() }
    val suggestions = remember(q, vm.contactEntries) {
        if (q.isEmpty()) emptyList()
        else vm.contactEntries.filter { c ->
            c.name.contains(q, ignoreCase = true) ||
                (digits.length >= 3 && c.number.filter { it.isDigit() }.contains(digits))
        }.take(6)
    }
    // Hide suggestions once the field holds exactly one suggested number.
    val showSuggestions = suggestions.isNotEmpty() && !(suggestions.size == 1 && suggestions[0].number == q)
    val parts = remember(body) { if (body.isEmpty()) 0 else SmsMessage.calculateLength(body, false)[0] }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("To") },
                placeholder = { Text("Name or phone number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showSuggestions) {
                suggestions.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { to = c.number }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(c.name, Sender.key(c.number), size = 36.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                c.number,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
            if (!vm.hasContactsPermission) {
                Text(
                    "Allow contacts access in Settings to pick people by name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            if (parts > 1) {
                Text(
                    "$parts SMS will be sent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SimPicker(vm, Modifier.padding(top = 12.dp))

            Button(
                onClick = {
                    if (!vm.hasSendPermission) {
                        actions.toast("Allow SMS permission, then tap Send again")
                        actions.requestPermissions()
                    } else {
                        sending = true
                        vm.sendMessage(to, body) { err ->
                            sending = false
                            if (err == null) onSent(Sender.key(to.trim())) else actions.toast(err)
                        }
                    }
                },
                enabled = !sending && to.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text(if (sending) "Sending..." else "Send", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                "Standard SMS charges from your carrier apply.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** "Send with" chips. Only shown on phones with two or more active SIMs. */
@Composable
fun SimPicker(vm: MainViewModel, modifier: Modifier = Modifier) {
    if (vm.sims.size < 2) return
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Send with", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.sims.forEach { sim ->
            FilterChip(
                selected = sim.subId == vm.selectedSubId,
                onClick = { vm.selectSim(sim.subId) },
                label = { Text("SIM ${sim.slot + 1} \u00B7 ${sim.name}", maxLines = 1) },
            )
        }
    }
}
