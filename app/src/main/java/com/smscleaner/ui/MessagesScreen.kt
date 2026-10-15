@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.smscleaner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smscleaner.Conversation
import com.smscleaner.MainViewModel
import com.smscleaner.Sender
import com.smscleaner.Sms

@Composable
fun MessagesTab(
    vm: MainViewModel,
    actions: AppActions,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val list = remember(vm.conversations, q) {
        if (q.isEmpty()) vm.conversations
        else vm.conversations.filter { c ->
            c.title.contains(q, ignoreCase = true) ||
                c.address.contains(q, ignoreCase = true) ||
                c.messages.any { it.body.contains(q, ignoreCase = true) }
        }
    }
    val selected = remember { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected.clear() }

    fun toggle(key: String) {
        if (!selected.remove(key)) selected.add(key)
    }

    Column(modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                onClose = { selected.clear() },
                onSelectAll = {
                    selected.clear()
                    selected.addAll(list.map { it.key })
                },
                onDelete = { confirmDelete = true },
            )
        } else {
            ScreenHeader("Messages", "${vm.conversations.size} conversations - long-press to select")
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search messages") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        when {
            vm.loading && vm.conversations.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() ->
                EmptyState(if (q.isEmpty()) "No messages found" else "No results for \"$q\"")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.key }) { c ->
                    ConversationRow(
                        c = c,
                        q = q,
                        selected = selected.contains(c.key),
                        onClick = { if (selecting) toggle(c.key) else onOpen(c.key) },
                        onLongClick = { toggle(c.key) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        val chosen = vm.conversations.filter { selected.contains(it.key) }
        val msgs = chosen.flatMap { it.messages }
        val protectedCount = chosen.count { vm.protection.isProtected(it.address) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${msgs.size} messages?") },
            text = {
                Text(
                    "All messages in ${chosen.size} conversation(s) will be removed from your phone. " +
                        (if (protectedCount > 0)
                            "$protectedCount of them is protected from automatic cleanup, but manual delete overrides that. "
                        else "") +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteMessages(msgs) { n ->
                        actions.toast(if (n < 0) "Delete failed" else "Deleted $n message(s)")
                        selected.clear()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel selection") }
            Text(
                "$count selected",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.Check, contentDescription = "Select all shown") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete selected") }
        }
    }
}

@Composable
private fun ConversationRow(
    c: Conversation,
    q: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val matched = if (q.isEmpty()) null else c.messages.firstOrNull { it.body.contains(q, ignoreCase = true) }
    val preview = (matched ?: c.last).body.replace('\n', ' ')
    val unread = c.unread > 0
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            Avatar(c.title, c.key)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                )
                Text(
                    formatDate((matched ?: c.last).date),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (unread) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            c.unread.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            if (!c.isContact) CategoryChip(c.category, Modifier.padding(top = 4.dp))
        }
    }
    HorizontalDivider(Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
fun ThreadScreen(vm: MainViewModel, actions: AppActions, key: String, onBack: () -> Unit) {
    val conv = vm.conversations.firstOrNull { it.key == key }
    if (conv == null) {
        // Conversation is gone (all messages deleted) or not loaded yet.
        LaunchedEffect(vm.loading, vm.conversations) { if (!vm.loading) onBack() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val selected = remember { mutableStateListOf<Long>() }
    var confirmDelete by remember { mutableStateOf(false) }

    // Mark received messages as read once when the thread opens.
    LaunchedEffect(key) {
        vm.markRead(conv.messages.filter { it.type == 1 && !it.read }.map { it.id })
    }
    BackHandler(enabled = selected.isNotEmpty()) { selected.clear() }

    val selecting = selected.isNotEmpty()
    val listProtected = vm.protectedSenders.any { Sender.key(it) == key }
    val anyProtected = vm.protection.isProtected(conv.address)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (selecting) "${selected.size} selected" else conv.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!selecting) {
                            Text(
                                if (anyProtected) "${conv.address} - protected" else "${conv.address} - long-press to select",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (selecting) selected.clear() else onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(onClick = {
                            selected.clear()
                            selected.addAll(conv.messages.map { it.id })
                        }) { Icon(Icons.Filled.Check, contentDescription = "Select all") }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    } else {
                        IconButton(onClick = {
                            if (listProtected) {
                                vm.removeProtectedSender(conv.address)
                                actions.toast("${conv.title} is no longer protected")
                            } else {
                                vm.addProtectedSender(conv.address)
                                actions.toast("${conv.title} is now protected from cleanup")
                            }
                        }) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = if (listProtected) "Remove protection" else "Protect sender",
                                tint = if (anyProtected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            reverseLayout = true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(conv.messages, key = { it.id }) { m ->
                Bubble(
                    m = m,
                    selected = selected.contains(m.id),
                    selecting = selecting,
                    onToggle = { if (!selected.remove(m.id)) selected.add(m.id) },
                )
            }
        }
    }

    if (confirmDelete) {
        val chosen = conv.messages.filter { selected.contains(it.id) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${chosen.size} message(s)?") },
            text = {
                Text("They will be removed from your phone. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteMessages(chosen) { n ->
                        actions.toast(if (n < 0) "Delete failed" else "Deleted $n message(s)")
                        selected.clear()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Bubble(m: Sms, selected: Boolean, selecting: Boolean, onToggle: () -> Unit) {
    val sent = m.type == 2
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (sent) cs.primaryContainer else cs.surfaceContainerHigh,
            contentColor = if (sent) cs.onPrimaryContainer else cs.onSurface,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (sent) 18.dp else 4.dp,
                bottomEnd = if (sent) 4.dp else 18.dp,
            ),
            border = if (selected) BorderStroke(2.dp, cs.primary) else null,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(onClick = { if (selecting) onToggle() }, onLongClick = onToggle),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(m.body, style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatDateTime(m.date),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColorAlpha(),
                )
            }
        }
    }
}

@Composable
private fun LocalContentColorAlpha() =
    androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.65f)
