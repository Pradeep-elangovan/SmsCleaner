package com.smscleaner

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Keeps rules (as JSON) and schedule settings in SharedPreferences. */
class RuleStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("smscleaner", Context.MODE_PRIVATE)

    fun load(): MutableList<Rule> {
        val out = mutableListOf<Rule>()
        try {
            val arr = JSONArray(prefs.getString("rules", "[]") ?: "[]")
            for (i in 0 until arr.length()) out.add(Rule.fromJson(arr.getJSONObject(i)))
        } catch (e: Exception) {
            // corrupted data: start empty rather than crash
        }
        return out
    }

    private fun save(rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("rules", arr.toString()).apply()
    }

    fun upsert(rule: Rule) {
        val rules = load()
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) rules[idx] = rule else rules.add(rule)
        save(rules)
    }

    fun delete(id: String) {
        save(load().filter { it.id != id })
    }

    var protectedSenders: List<String>
        get() = try {
            val a = JSONArray(prefs.getString("protectedSenders", "[]") ?: "[]")
            (0 until a.length()).map { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
        set(v) = prefs.edit().putString("protectedSenders", JSONArray(v).toString()).apply()

    var protectContacts: Boolean
        get() = prefs.getBoolean("protectContacts", true)
        set(v) = prefs.edit().putBoolean("protectContacts", v).apply()

    /** Days to keep deleted messages for recovery. 0 = backup off. */
    var backupDays: Int
        get() = prefs.getInt("backupDays", 30)
        set(v) = prefs.edit().putInt("backupDays", v).apply()

    var scheduleEnabled: Boolean
        get() = prefs.getBoolean("scheduleEnabled", false)
        set(v) = prefs.edit().putBoolean("scheduleEnabled", v).apply()

    var runHour: Int
        get() = prefs.getInt("runHour", 3)
        set(v) = prefs.edit().putInt("runHour", v).apply()

    var runMinute: Int
        get() = prefs.getInt("runMinute", 0)
        set(v) = prefs.edit().putInt("runMinute", v).apply()

    var lastRun: String
        get() = prefs.getString("lastRun", "") ?: ""
        set(v) = prefs.edit().putString("lastRun", v).apply()
}
