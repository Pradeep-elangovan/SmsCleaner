package com.smscleaner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object Contacts {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** All contacts with a phone number, sorted by name, one entry per number. */
    fun loadEntries(ctx: Context): List<ContactEntry> {
        if (!hasPermission(ctx)) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<ContactEntry>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val key = Sender.key(number)
                    if (key.isNotEmpty() && key != "unknown" && seen.add(key)) {
                        out.add(ContactEntry(name, number.trim()))
                    }
                }
            }
        } catch (e: Exception) {
            // unreadable contacts: treated as no contacts
        }
        return out.sortedBy { it.name.lowercase() }
    }

    fun toKeyMap(entries: List<ContactEntry>): Map<String, String> =
        entries.associate { Sender.key(it.number) to it.name }

    /** Map of normalized phone key (last 10 digits) to contact name. Empty without permission. */
    fun load(ctx: Context): Map<String, String> = toKeyMap(loadEntries(ctx))
}
