package com.smscleaner

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.util.ArrayList

object SmsRepository {
    private val COLS = arrayOf("_id", "address", "body", "date", "read", "type")
    private const val DAY_MS = 24L * 60 * 60 * 1000

    // ---- default-app detection (three independent sources) ----

    private fun telephonyDefault(ctx: Context): String? = try {
        Telephony.Sms.getDefaultSmsPackage(ctx)
    } catch (e: Exception) {
        null
    }

    private fun roleHeld(ctx: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else false
    } catch (e: Exception) {
        false
    }

    private fun settingDefault(ctx: Context): String? = try {
        Settings.Secure.getString(ctx.contentResolver, "sms_default_application")
    } catch (e: Exception) {
        null
    }

    fun isDefault(ctx: Context): Boolean {
        val me = ctx.packageName
        return telephonyDefault(ctx) == me || roleHeld(ctx) || settingDefault(ctx) == me
    }

    fun diagnostics(ctx: Context): String =
        "telephony=" + (telephonyDefault(ctx) ?: "null") +
            ", role=" + roleHeld(ctx) +
            ", setting=" + (settingDefault(ctx) ?: "null")

    fun hasReadPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    // ---- reading ----

    private fun fromCursor(c: Cursor): Sms {
        val address = c.getString(1) ?: ""
        val body = c.getString(2) ?: ""
        return Sms(
            id = c.getLong(0),
            address = address,
            body = body,
            date = c.getLong(3),
            read = c.getInt(4) == 1,
            type = c.getInt(5),
            category = Classifier.classify(address, body),
        )
    }

    /** Every inbox, sent, sending and failed message, newest first. */
    fun loadAll(ctx: Context): List<Sms> {
        val out = ArrayList<Sms>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI, COLS, "type IN (1,2,4,5,6)", null, "date DESC"
        )?.use { c ->
            while (c.moveToNext()) out.add(fromCursor(c))
        }
        return out
    }

    // ---- rule matching (pure, in memory) ----

    /** Messages in [all] that [rule] would delete. Protected senders are always skipped. */
    fun applyRule(
        rule: Rule,
        all: List<Sms>,
        protection: Protection,
        now: Long = System.currentTimeMillis(),
    ): List<Sms> {
        if (!rule.isSafe()) return emptyList()

        val regex: Regex? = if (rule.senderMode == SenderMode.REGEX && rule.sender.isNotBlank()) {
            try {
                Regex(rule.sender, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                return emptyList() // an invalid regex must match nothing, never everything
            }
        } else null

        val cutoff = if (rule.olderThanDays > 0) now - rule.olderThanDays * DAY_MS else Long.MAX_VALUE
        val keywords = rule.keywordList()
        val sender = rule.sender.trim()

        return all.filter { m ->
            val boxOk = when (rule.box) {
                Box.INBOX -> m.type == 1
                Box.SENT -> m.type == 2
                Box.ALL -> m.type == 1 || m.type == 2
            }
            boxOk &&
                (rule.readState == ReadState.ANY ||
                    (rule.readState == ReadState.READ && m.read) ||
                    (rule.readState == ReadState.UNREAD && !m.read)) &&
                m.date < cutoff &&
                (rule.category == null || m.category == rule.category) &&
                senderMatches(rule.senderMode, sender, regex, m.address) &&
                (keywords.isEmpty() || keywords.any { m.body.contains(it, ignoreCase = true) }) &&
                !protection.isProtected(m.address)
        }
    }

    private fun senderMatches(mode: SenderMode, sender: String, regex: Regex?, address: String): Boolean {
        if (mode == SenderMode.ANY || sender.isBlank()) return true
        return when (mode) {
            SenderMode.EXACT -> address.equals(sender, ignoreCase = true)
            SenderMode.PREFIX -> address.startsWith(sender, ignoreCase = true)
            SenderMode.CONTAINS -> address.contains(sender, ignoreCase = true)
            SenderMode.REGEX -> regex?.containsMatchIn(address) == true
            SenderMode.ANY -> true
        }
    }

    // ---- writing ----

    /** Deletes the given messages. Works only while this app is the default SMS app. */
    fun delete(ctx: Context, msgs: List<Sms>): Int {
        var n = 0
        for (m in msgs) {
            n += ctx.contentResolver.delete(Uri.parse("content://sms/${m.id}"), null, null)
        }
        return n
    }

    fun markRead(ctx: Context, ids: List<Long>) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        for (id in ids) {
            try {
                ctx.contentResolver.update(Uri.parse("content://sms/$id"), values, null, null)
            } catch (e: Exception) {
                // ignore single failures
            }
        }
    }
}
