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

    /** Runs every enabled rule. Backs messages up to CSV before deleting them. */
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
        var total = 0
        val per = mutableListOf<Pair<String, Int>>()
        for (rule in rules) {
            val matches = SmsRepository.findMatches(ctx, rule)
            if (matches.isEmpty()) {
                per.add(rule.name to 0)
                continue
            }
            SmsRepository.backup(ctx, rule.name, matches) // backup first; if it throws, nothing is deleted
            val n = SmsRepository.delete(ctx, matches)
            total += n
            per.add(rule.name to n)
        }
        return RunResult(total, per)
    }
}
