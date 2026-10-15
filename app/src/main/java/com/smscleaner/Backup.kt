package com.smscleaner

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Short-lived safety net for deleted messages. Entries expire after the chosen number of days
 * and the file is capped at [MAX_ENTRIES], so it can never grow without limit.
 * Stored as one JSON object per line; exported to CSV only when the user shares it.
 */
object Backup {
    const val MAX_ENTRIES = 5000
    private const val FILE = "deleted_backup.jsonl"
    private const val LEGACY = "deleted_backup.csv" // from version 1.x, expires like the rest
    private const val EXPORT = "deleted_backup_export.csv"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    fun add(ctx: Context, label: String, msgs: List<Sms>) {
        if (RuleStore(ctx).backupDays <= 0 || msgs.isEmpty()) return
        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        for (m in msgs) {
            sb.append(
                JSONObject().apply {
                    put("deletedAt", now)
                    put("date", m.date)
                    put("sender", m.address)
                    put("label", label)
                    put("body", m.body)
                }.toString()
            ).append('\n')
        }
        file(ctx).appendText(sb.toString())
        purge(ctx)
    }

    /** Removes expired entries, enforces the size cap, and deletes everything if backup is off. */
    @Synchronized
    fun purge(ctx: Context) {
        val days = RuleStore(ctx).backupDays
        val now = System.currentTimeMillis()

        val legacy = File(ctx.filesDir, LEGACY)
        if (legacy.exists() && (days <= 0 || now - legacy.lastModified() > days * DAY_MS)) legacy.delete()

        val f = file(ctx)
        if (!f.exists()) return
        if (days <= 0) {
            f.delete()
            return
        }
        val cutoff = now - days * DAY_MS
        var total = 0
        val kept = ArrayList<String>()
        f.forEachLine { line ->
            if (line.isNotBlank()) {
                total++
                val t = try {
                    JSONObject(line).optLong("deletedAt", 0L)
                } catch (e: Exception) {
                    0L
                }
                if (t >= cutoff) kept.add(line)
            }
        }
        val trimmed = if (kept.size > MAX_ENTRIES) kept.subList(kept.size - MAX_ENTRIES, kept.size) else kept
        if (trimmed.size == total) return // nothing to remove
        if (trimmed.isEmpty()) {
            f.delete()
            return
        }
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(trimmed.joinToString("\n") + "\n")
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
    }

    /** Number of backed-up messages and the space they use. */
    fun stats(ctx: Context): Pair<Int, Long> {
        val f = file(ctx)
        var n = 0
        var bytes = 0L
        if (f.exists()) {
            f.forEachLine { if (it.isNotBlank()) n++ }
            bytes += f.length()
        }
        val legacy = File(ctx.filesDir, LEGACY)
        if (legacy.exists()) bytes += legacy.length()
        return n to bytes
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
        File(ctx.filesDir, LEGACY).delete()
        File(ctx.filesDir, EXPORT).delete()
    }

    /** Builds a CSV of the current backup for sharing. Null when there is nothing to share. */
    fun exportCsv(ctx: Context): File? {
        val f = file(ctx)
        if (!f.exists()) {
            val legacy = File(ctx.filesDir, LEGACY)
            return if (legacy.exists()) legacy else null
        }
        val out = File(ctx.filesDir, EXPORT)
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        var rows = 0
        out.bufferedWriter().use { w ->
            w.write("deleted_at,message_date,sender,rule,message\n")
            f.forEachLine { line ->
                if (line.isNotBlank()) {
                    try {
                        val o = JSONObject(line)
                        w.write(
                            csv(fmt.format(Date(o.optLong("deletedAt")))) + "," +
                                csv(fmt.format(Date(o.optLong("date")))) + "," +
                                csv(o.optString("sender")) + "," +
                                csv(o.optString("label")) + "," +
                                csv(o.optString("body")) + "\n"
                        )
                        rows++
                    } catch (e: Exception) {
                        // skip a corrupt line
                    }
                }
            }
        }
        return if (rows == 0) null else out
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
}
