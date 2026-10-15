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

    /** Map of normalized phone key (last 10 digits) to contact name. Empty without permission. */
    fun load(ctx: Context): Map<String, String> {
        if (!hasPermission(ctx)) return emptyMap()
        val out = HashMap<String, String>()
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
                    if (key.isNotEmpty() && key != "unknown") out.putIfAbsent(key, name)
                }
            }
        } catch (e: Exception) {
            // unreadable contacts: treated as no contacts
        }
        return out
    }
}
