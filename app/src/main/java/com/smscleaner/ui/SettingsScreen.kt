@file:OptIn(ExperimentalMaterial3Api::class)

package com.smscleaner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smscleaner.MainViewModel

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✓" else "✗",
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )
        Text(label, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingsTab(vm: MainViewModel, actions: AppActions, modifier: Modifier = Modifier) {
    var newSender by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("Settings")

        SettingsCard("Status") {
            StatusLine("Default SMS app", vm.isDefault)
            StatusLine("SMS permission", vm.hasSmsPermission)
            StatusLine("Contacts permission", vm.hasContactsPermission)
            Text(
                "Detected: ${vm.diagnostics}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedButton(onClick = actions.openDefaultSettings, modifier = Modifier.padding(top = 8.dp)) {
                Text("Change default SMS app")
            }
        }

        SettingsCard("SIM for sending") {
            when {
                !vm.hasPhoneStatePermission -> {
                    Text(
                        "Allow SIM access to choose which SIM sends your messages on a dual-SIM phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(onClick = actions.requestSim, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Allow SIM access")
                    }
                }
                vm.sims.size < 2 -> Text(
                    "Only one SIM detected. Messages are sent with it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> {
                    Text(
                        "Your choice is remembered. You can also switch it while writing a message.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    SimPicker(vm)
                }
            }
        }

        SettingsCard("Protection") {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Never delete messages from my contacts", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Applies to rules, quick clean and the daily run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.protectContacts, onCheckedChange = { vm.updateProtectContacts(it) })
            }
            if (vm.protectContacts && !vm.hasContactsPermission) {
                Text(
                    "Contacts access is off, so for safety every message from a phone number is protected. " +
                        "Allow access to protect only your contacts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = actions.requestContacts, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Allow contacts access")
                }
            }

            Text(
                "Protected senders",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Use a brand name like HDFCBK or a phone number. You can also tap the lock icon inside any conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            vm.protectedSenders.forEach { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { vm.removeProtectedSender(s) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove $s")
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSender,
                    onValueChange = { newSender = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Add sender") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        if (vm.addProtectedSender(newSender)) newSender = ""
                        else actions.toast("Enter a sender that is not already listed")
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Add") }
            }
        }

        SettingsCard("About") {
            Text(
                "SMS Cleaner 2.0. Deleting works only while this is your default SMS app. " +
                    "Picture messages (MMS) and RCS chats are not handled.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
