@file:OptIn(ExperimentalMaterial3Api::class)

package com.smscleaner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smscleaner.Box
import com.smscleaner.Category
import com.smscleaner.MainViewModel
import com.smscleaner.Rule
import com.smscleaner.SmsRepository

private val CLEAN_CATEGORIES = listOf(
    Category.OTP, Category.OFFERS, Category.DELIVERY, Category.BANK, Category.OTHER,
)

private val AGE_OPTIONS = listOf(0 to "All", 1 to "1 day+", 7 to "7 days+", 30 to "30 days+", 90 to "90 days+")

private fun defaultAge(c: Category) = when (c) {
    Category.OTP -> 1
    Category.OFFERS -> 7
    Category.DELIVERY -> 30
    Category.BANK -> 90
    else -> 30
}

@Composable
fun CleanupTab(vm: MainViewModel, actions: AppActions, modifier: Modifier = Modifier) {
    val received = remember(vm.messages) { vm.messages.count { it.type == 1 } }
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            ScreenHeader("Cleanup", "$received received messages, sorted automatically")
        }
        items(CLEAN_CATEGORIES) { cat ->
            CleanupCard(vm, actions, cat)
        }
        item {
            Text(
                "Categories are automatic guesses based on the sender and wording, so check the count before cleaning. " +
                    "Messages from contacts and protected senders are never deleted. " +
                    "Deleting cannot be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CleanupCard(vm: MainViewModel, actions: AppActions, cat: Category) {
    var age by rememberSaveable(cat.name) { mutableIntStateOf(defaultAge(cat)) }
    var confirm by remember { mutableStateOf(false) }

    val total = remember(vm.messages) { vm.messages.count { it.type == 1 && it.category == cat } }
    val matches = remember(vm.messages, vm.protection, age) {
        SmsRepository.applyRule(
            Rule(name = "Quick clean", category = cat, olderThanDays = age, box = Box.INBOX),
            vm.messages,
            vm.protection,
        )
    }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CategoryChip(cat)
                Text(
                    "$total in inbox",
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(cat.blurb, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))

            Row(
                Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AGE_OPTIONS.forEach { (days, label) ->
                    FilterChip(
                        selected = age == days,
                        onClick = { age = days },
                        label = { Text(label) },
                    )
                }
            }

            Button(
                onClick = { confirm = true },
                enabled = matches.isNotEmpty(),
                modifier = Modifier.padding(top = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(if (matches.isEmpty()) "Nothing to clean" else "Clean ${matches.size} messages")
            }
        }
    }

    if (confirm) {
        val ageText = if (age == 0) "of any age" else "older than $age day(s)"
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${matches.size} messages?") },
            text = {
                Text(
                    "${cat.label} $ageText will be removed from your phone. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.deleteMessages(matches) { n ->
                        actions.toast(if (n < 0) "Delete failed" else "Deleted $n message(s)")
                    }
                }) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
