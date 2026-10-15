package com.smscleaner

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Sends SMS. As the default SMS app we must also store the message ourselves: it is written to
 * the Outbox first, then [SmsSentReceiver] moves it to Sent (or Failed) when the radio reports back.
 */
object SmsSender {
    const val EXTRA_ID = "msgId"
    private const val ACTION_SENT = "com.smscleaner.SMS_SENT"

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun smsManager(ctx: Context): SmsManager? =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()

    /** Returns null when the message was handed to the network, or a short error for the user. */
    fun send(ctx: Context, to: String, body: String): String? {
        val dest = to.trim()
        val text = body.trim()
        if (dest.isEmpty()) return "Enter a phone number."
        if (text.isEmpty()) return "Type a message first."
        if (dest.any { it.isLetter() } || dest.none { it.isDigit() }) return "Enter a valid phone number."
        if (!SmsRepository.isDefault(ctx)) return "Set this app as the default SMS app to send."
        if (!hasPermission(ctx)) return "SMS permission is missing."
        val sm = smsManager(ctx) ?: return "Sending is not available on this device."

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, dest)
            put(Telephony.Sms.BODY, text)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
        }
        val uri = ctx.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            ?: return "Could not save the message."
        val id = ContentUris.parseId(uri)

        return try {
            val parts = sm.divideMessage(text)
            val intents = ArrayList<PendingIntent>()
            for (i in parts.indices) intents.add(sentIntent(ctx, id, i))
            if (parts.size > 1) sm.sendMultipartTextMessage(dest, null, parts, intents, null)
            else sm.sendTextMessage(dest, null, text, intents[0], null)
            null
        } catch (e: Exception) {
            markFailed(ctx, id)
            "Failed to send: ${e.message ?: "unknown error"}"
        }
    }

    private fun sentIntent(ctx: Context, id: Long, part: Int): PendingIntent {
        val intent = Intent(ctx, SmsSentReceiver::class.java)
            .setAction(ACTION_SENT)
            .setData(Uri.parse("smscleaner://sent/$id/$part")) // makes each PendingIntent distinct
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, part, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Outbox/queued -> Sent. Never overwrites a Failed result from another part. */
    fun markSentIfPending(ctx: Context, id: Long) {
        val v = ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT) }
        try {
            ctx.contentResolver.update(Uri.parse("content://sms/$id"), v, "type IN (4,6)", null)
        } catch (e: Exception) {
            // nothing sensible to do
        }
    }

    fun markFailed(ctx: Context, id: Long) {
        val v = ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) }
        try {
            ctx.contentResolver.update(Uri.parse("content://sms/$id"), v, null, null)
        } catch (e: Exception) {
            // nothing sensible to do
        }
    }
}
