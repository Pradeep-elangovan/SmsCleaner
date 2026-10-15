package com.smscleaner

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx: Context = app.applicationContext
    private val store = RuleStore(appCtx)
    private var reloadToken = 0

    // ---- status ----
    var isDefault by mutableStateOf(false)
        private set
    var hasSmsPermission by mutableStateOf(false)
        private set
    var hasContactsPermission by mutableStateOf(false)
        private set
    var diagnostics by mutableStateOf("")
        private set

    // ---- data ----
    var loading by mutableStateOf(false)
        private set
    var messages by mutableStateOf<List<Sms>>(emptyList())
        private set
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set
    var protection by mutableStateOf(Protection.EMPTY)
        private set

    // ---- settings ----
    var rules by mutableStateOf<List<Rule>>(emptyList())
        private set
    var protectedSenders by mutableStateOf<List<String>>(emptyList())
        private set
    var protectContacts by mutableStateOf(true)
        private set
    var scheduleEnabled by mutableStateOf(false)
        private set
    var runHour by mutableStateOf(3)
        private set
    var runMinute by mutableStateOf(0)
        private set
    var lastRun by mutableStateOf("")
        private set

    val ready: Boolean get() = isDefault && hasSmsPermission

    fun refreshStatus() {
        isDefault = SmsRepository.isDefault(appCtx)
        hasSmsPermission = SmsRepository.hasReadPermission(appCtx)
        hasContactsPermission = Contacts.hasPermission(appCtx)
        diagnostics = SmsRepository.diagnostics(appCtx)
        rules = store.load()
        protectedSenders = store.protectedSenders
        protectContacts = store.protectContacts
        scheduleEnabled = store.scheduleEnabled
        runHour = store.runHour
        runMinute = store.runMinute
        lastRun = store.lastRun
        if (ready) reload()
    }

    fun reload() {
        val token = ++reloadToken
        loading = true
        viewModelScope.launch(Dispatchers.IO) {
            var all: List<Sms> = emptyList()
            var contacts: Map<String, String> = emptyMap()
            try {
                all = SmsRepository.loadAll(appCtx)
                contacts = Contacts.load(appCtx)
            } catch (e: Exception) {
                // leave empty; the UI shows an empty state
            }
            val convs = Conversations.build(all, contacts)
            val prot = Protection.load(appCtx, contacts)
            withContext(Dispatchers.Main) {
                if (token == reloadToken) {
                    messages = all
                    conversations = convs
                    protection = prot
                    loading = false
                }
            }
        }
    }

    private fun rebuildProtection() {
        viewModelScope.launch(Dispatchers.IO) {
            val prot = Protection.load(appCtx)
            withContext(Dispatchers.Main) { protection = prot }
        }
    }

    // ---- messages ----

    fun markRead(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            SmsRepository.markRead(appCtx, ids)
            withContext(Dispatchers.Main) { reload() }
        }
    }

    /** Backs up then deletes. [onDone] gets the number deleted, or -1 on failure. */
    fun deleteMessages(msgs: List<Sms>, label: String, onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val n = try {
                CleanupRunner.deleteWithBackup(appCtx, label, msgs)
            } catch (e: Exception) {
                -1
            }
            withContext(Dispatchers.Main) {
                onDone(n)
                reload()
            }
        }
    }

    fun runRulesNow(onResult: (CleanupRunner.RunResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = CleanupRunner.runAll(appCtx)
            withContext(Dispatchers.Main) {
                lastRun = store.lastRun
                onResult(r)
                reload()
            }
        }
    }

    // ---- rules ----

    fun saveRule(rule: Rule) {
        store.upsert(rule)
        rules = store.load()
    }

    fun deleteRule(id: String) {
        store.delete(id)
        rules = store.load()
    }

    fun toggleRule(rule: Rule, enabled: Boolean) = saveRule(rule.copy(enabled = enabled))

    fun setScheduleEnabled(on: Boolean) {
        store.scheduleEnabled = on
        scheduleEnabled = on
        Scheduler.apply(appCtx)
    }

    fun setRunTime(hour: Int, minute: Int) {
        store.runHour = hour
        store.runMinute = minute
        runHour = hour
        runMinute = minute
        Scheduler.apply(appCtx)
    }

    // ---- protection ----

    fun addProtectedSender(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty()) return false
        val key = Sender.key(s)
        if (protectedSenders.any { Sender.key(it) == key }) return false
        val updated = protectedSenders + s
        store.protectedSenders = updated
        protectedSenders = updated
        rebuildProtection()
        return true
    }

    fun removeProtectedSender(s: String) {
        val key = Sender.key(s)
        val updated = protectedSenders.filter { Sender.key(it) != key }
        store.protectedSenders = updated
        protectedSenders = updated
        rebuildProtection()
    }

    fun setProtectContacts(on: Boolean) {
        store.protectContacts = on
        protectContacts = on
        rebuildProtection()
    }
}
