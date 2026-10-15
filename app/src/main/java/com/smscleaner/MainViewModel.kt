package com.smscleaner

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    var hasSendPermission by mutableStateOf(false)
        private set
    var hasPhoneStatePermission by mutableStateOf(false)
        private set
    var sims by mutableStateOf<List<SimInfo>>(emptyList())
        private set
    var selectedSubId by mutableStateOf(-1)
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
    var contactEntries by mutableStateOf<List<ContactEntry>>(emptyList())
        private set

    /** Set when another app asks us to compose a message (number, text). */
    var pendingCompose by mutableStateOf<Pair<String, String>?>(null)
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

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = scheduleReload()
    }
    private var observerJob: Job? = null

    /** Refreshes shortly after the SMS store changes (new message, send status, edits). */
    private fun scheduleReload() {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            delay(600)
            if (ready) reload()
        }
    }

    override fun onCleared() {
        try {
            appCtx.contentResolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
            // already gone
        }
    }

    init {
        try {
            appCtx.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (e: Exception) {
            // live refresh is optional
        }
        // Earlier versions kept copies of deleted messages. Remove any that are left over.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appCtx.filesDir.listFiles()
                    ?.filter { it.name.startsWith("deleted_backup") }
                    ?.forEach { it.delete() }
            } catch (e: Exception) {
                // best effort
            }
        }
    }

    fun refreshStatus() {
        isDefault = SmsRepository.isDefault(appCtx)
        hasSmsPermission = SmsRepository.hasReadPermission(appCtx)
        hasContactsPermission = Contacts.hasPermission(appCtx)
        hasSendPermission = SmsSender.hasPermission(appCtx)
        hasPhoneStatePermission = Sims.hasPermission(appCtx)
        sims = Sims.list(appCtx)
        selectedSubId = Sims.resolve(sims, store.simSubId)
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
            var entries: List<ContactEntry> = emptyList()
            try {
                all = SmsRepository.loadAll(appCtx)
                entries = Contacts.loadEntries(appCtx)
            } catch (e: Exception) {
                // leave empty; the UI shows an empty state
            }
            val contacts = Contacts.toKeyMap(entries)
            val convs = Conversations.build(all, contacts)
            val prot = Protection.load(appCtx, contacts)
            withContext(Dispatchers.Main) {
                if (token == reloadToken) {
                    messages = all
                    conversations = convs
                    protection = prot
                    contactEntries = entries
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

    /** Deletes the messages. [onDone] gets the number deleted, or -1 on failure. */
    fun deleteMessages(msgs: List<Sms>, onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val n = try {
                SmsRepository.delete(appCtx, msgs)
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

    // ---- sending ----

    /** [onDone] gets null on success or an error message. The list reloads before it is called. */
    fun sendMessage(to: String, body: String, onDone: (String?) -> Unit) {
        val sub = selectedSubId
        viewModelScope.launch(Dispatchers.IO) {
            val err = try {
                SmsSender.send(appCtx, to, body, sub)
            } catch (e: Exception) {
                "Failed to send: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                reload() // first, so a screen opened by onDone sees the loading state
                onDone(err)
            }
        }
    }

    fun selectSim(subId: Int) {
        store.simSubId = subId
        selectedSubId = subId
    }

    fun requestCompose(to: String, body: String) {
        pendingCompose = to to body
    }

    fun consumeCompose() {
        pendingCompose = null
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

    fun updateSchedule(on: Boolean) {
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

    fun updateProtectContacts(on: Boolean) {
        store.protectContacts = on
        protectContacts = on
        rebuildProtection()
    }
}
