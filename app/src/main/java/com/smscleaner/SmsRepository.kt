package com.smscleaner

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

data class Sms(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val type: Int,
)

object SmsRepository {
    private val COLS = arrayOf("_id", "address", "body", "date", "read", "type")
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun isDefault(ctx: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    private fun fromCursor(c: Cursor): Sms = Sms(
        id = c.getLong(0),
        address = c.getString(1) ?: "",
        body = c.getString(2) ?: "",
        date = c.getLong(3),
        read = c.getInt(4) == 1,
        type = c.getInt(5),
    )

    /** Latest inbox + sent messages, newest first. */
    fun recent(ctx: Context, limit: Int): List<Sms> {
        val out = ArrayList<Sms>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, COLS, "type IN (1,2)", null, "date DESC"
        )?.use { c ->
            while (out.size < limit && c.moveToNext()) out.add(fromCursor(c))
        }
        return out
    }

    /** Messages that [rule] would delete. Never deletes anything itself. */
    fun findMatches(ctx: Context, rule: Rule, now: Long = System.currentTimeMillis()): List<Sms> {
        if (!rule.isSafe()) return emptyList()

        val senderRegex: Regex? = if (rule.senderMode == SenderMode.REGEX) {
            try {
                Regex(rule.sender, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                return emptyList() // invalid regex must match nothing, never everything
            }
        } else null

        val where = StringBuilder()
        val args = ArrayList<String>()
        when (rule.box) {
            Box.INBOX -> where.append("type = 1")
            Box.SENT -> where.append("type = 2")
            Box.ALL -> where.append("type IN (1,2)")
        }
        when (rule.readState) {
            ReadState.READ -> where.append(" AND read = 1")
            ReadState.UNREAD -> where.append(" AND read = 0")
            ReadState.ANY -> {}
        }
        if (rule.olderThanDays > 0) {
            where.append(" AND date < ?")
            args.add((now - rule.olderThanDays * DAY_MS).toString())
        }

        val keywords = rule.keywordList()
        val out = ArrayList<Sms>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, COLS, where.toString(),
            if (args.isEmpty()) null else args.toTypedArray(), "date DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val m = fromCursor(c)
                if (!senderMatches(rule, senderRegex, m.address)) continue
                if (keywords.isNotEmpty() && keywords.none { m.body.contains(it, ignoreCase = true) }) continue
                out.add(m)
            }
        }
        return out
    }

    private fun senderMatches(rule: Rule, regex: Regex?, address: String): Boolean {
        if (rule.senderMode == SenderMode.ANY || rule.sender.isBlank()) return true
        val s = rule.sender.trim()
        return when (rule.senderMode) {
            SenderMode.EXACT -> address.equals(s, ignoreCase = true)
            SenderMode.PREFIX -> address.startsWith(s, ignoreCase = true)
            SenderMode.CONTAINS -> address.contains(s, ignoreCase = true)
            SenderMode.REGEX -> regex?.containsMatchIn(address) == true
            SenderMode.ANY -> true
        }
    }

    /** Appends the messages to a CSV in app storage so deletions can be recovered. */
    fun backup(ctx: Context, ruleName: String, msgs: List<Sms>) {
        if (msgs.isEmpty()) return
        val f = File(ctx.filesDir, "deleted_backup.csv")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        if (!f.exists()) sb.append("date,sender,rule,message\n")
        for (m in msgs) {
            sb.append(csv(fmt.format(Date(m.date)))).append(',')
                .append(csv(m.address)).append(',')
                .append(csv(ruleName)).append(',')
                .append(csv(m.body)).append('\n')
        }
        f.appendText(sb.toString())
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    /** Deletes the given messages. Works only while this app is the default SMS app. */
    fun delete(ctx: Context, msgs: List<Sms>): Int {
        var n = 0
        for (m in msgs) {
            n += ctx.contentResolver.delete(Uri.parse("content://sms/${m.id}"), null, null)
        }
        return n
    }
}
