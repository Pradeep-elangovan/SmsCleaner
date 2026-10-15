package com.smscleaner

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CleanupRunner {

    data class RunResult(
        val deleted: Int,
        val perRule: List<Pair<String, Int>>,
        val error: String? = null,
    ) {
        fun summary(): String = when {
            error != null -> error
            deleted == 0 -> "No messages matched."
            else -> "Deleted $deleted: " +
                perRule.filter { it.second > 0 }.joinToString { "${it.first} (${it.second})" }
        }
    }

    /** Backs the messages up to CSV, then deletes them. If the backup throws, nothing is deleted. */
    fun deleteWithBackup(ctx: Context, label: String, msgs: List<Sms>): Int {
        SmsRepository.backup(ctx, label, msgs)
        return SmsRepository.delete(ctx, msgs)
    }

    /** Runs every enabled rule. Protected senders are never touched. */
    fun runAll(ctx: Context): RunResult {
        val app = ctx.applicationContext
        val result = try {
            doRun(app)
        } catch (e: Exception) {
            RunResult(0, emptyList(), "Error: ${e.message}")
        }
        RuleStore(app).lastRun =
            SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date()) + " - " + result.summary()
        return result
    }

    private fun doRun(ctx: Context): RunResult {
        if (!SmsRepository.isDefault(ctx)) {
            return RunResult(0, emptyList(), "Not the default SMS app, so nothing was deleted.")
        }
        val rules = RuleStore(ctx).load().filter { it.enabled && it.isSafe() }
        val protection = Protection.load(ctx)
        var all = SmsRepository.loadAll(ctx)
        var total = 0
        val per = mutableListOf<Pair<String, Int>>()
        for (rule in rules) {
            val matches = SmsRepository.applyRule(rule, all, protection)
            if (matches.isEmpty()) {
                per.add(rule.name to 0)
                continue
            }
            val n = deleteWithBackup(ctx, rule.name, matches)
            total += n
            per.add(rule.name to n)
            val gone = matches.map { it.id }.toHashSet()
            all = all.filter { it.id !in gone }
        }
        return RunResult(total, per)
    }
}
