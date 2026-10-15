package com.smscleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smscleaner.MainViewModel
import com.smscleaner.Rule

class AppActions(
    val requestDefault: () -> Unit,
    val requestPermissions: () -> Unit,
    val requestContacts: () -> Unit,
    val openDefaultSettings: () -> Unit,
    val pickTime: (Int, Int, (Int, Int) -> Unit) -> Unit,
    val toast: (String) -> Unit,
)

sealed interface Screen {
    data object Home : Screen
    data class Thread(val key: String) : Screen
    data class RuleEditor(val ruleId: String?, val template: Rule?) : Screen
}

@Composable
fun AppRoot(vm: MainViewModel, actions: AppActions) {
    if (!vm.ready) {
        SetupScreen(vm, actions)
        return
    }

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = screen !is Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavItem("Messages", Icons.Filled.Email, tab == 0) { tab = 0 }
                    NavItem("Cleanup", Icons.Filled.Delete, tab == 1) { tab = 1 }
                    NavItem("Rules", Icons.Filled.List, tab == 2) { tab = 2 }
                    NavItem("Settings", Icons.Filled.Settings, tab == 3) { tab = 3 }
                }
            }
        ) { padding ->
            val mod = Modifier.padding(padding)
            when (tab) {
                0 -> MessagesTab(vm, actions, onOpen = { screen = Screen.Thread(it) }, modifier = mod)
                1 -> CleanupTab(vm, actions, modifier = mod)
                2 -> RulesTab(
                    vm, actions,
                    onEdit = { screen = Screen.RuleEditor(it, null) },
                    onTemplate = { screen = Screen.RuleEditor(null, it) },
                    modifier = mod,
                )
                else -> SettingsTab(vm, actions, modifier = mod)
            }
        }
        is Screen.Thread -> ThreadScreen(vm, actions, s.key, onBack = { screen = Screen.Home })
        is Screen.RuleEditor -> RuleEditorScreen(vm, s.ruleId, s.template, onClose = { screen = Screen.Home })
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

/** First-run screen: guides the user through the two things Android requires. */
@Composable
fun SetupScreen(vm: MainViewModel, actions: AppActions) {
    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text("SMS Cleaner", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "Tidy your messages with rules that run on their own. Two quick steps first.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            StepCard(
                number = "1",
                title = "Make this your default SMS app",
                body = "Android only lets the default SMS app delete messages. You can switch back to your usual app any time.",
                done = vm.isDefault,
                buttonText = "Set as default",
                onClick = actions.requestDefault,
            )
            Spacer(Modifier.height(12.dp))
            StepCard(
                number = "2",
                title = "Allow SMS and contacts access",
                body = "Needed to read your messages and to keep messages from your contacts safe.",
                done = vm.hasSmsPermission,
                buttonText = "Allow access",
                onClick = actions.requestPermissions,
            )

            Card(
                Modifier.padding(top = 20.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Buttons not working?", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Open Settings, Apps, SMS Cleaner, tap the three-dot menu and choose " +
                            "\"Allow restricted settings\". Then try the steps again.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedButton(onClick = actions.openDefaultSettings, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Open default apps settings")
                    }
                }
            }

            Text(
                "Detected: ${vm.diagnostics}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun StepCard(
    number: String,
    title: String,
    body: String,
    done: Boolean,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                    Icon(Icons.Filled.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Text(number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            if (!done) {
                Button(onClick = onClick, modifier = Modifier.padding(top = 12.dp)) { Text(buttonText) }
            }
        }
    }
}
