package com.smscleaner

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * While this app is the default SMS app, Android delivers incoming SMS only here, and it is
 * this app's job to store them. Without the insert below, new messages would be lost.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts == null || parts.isEmpty()) return

        val address = parts[0].displayOriginatingAddress ?: ""
        val body = parts.joinToString("") { it.displayMessageBody ?: "" }

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, parts[0].timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        try {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (e: Exception) {
            // nothing sensible to do in a receiver
        }
        Notifier.post(context, address, body, id = 1000 + (address.hashCode() and 0xFFF))
    }
}
