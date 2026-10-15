package com.smscleaner

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the radio's result for each sent message part and updates the stored message. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(SmsSender.EXTRA_ID, -1L)
        if (id < 0) return
        if (resultCode == Activity.RESULT_OK) SmsSender.markSentIfPending(context, id)
        else SmsSender.markFailed(context, id)
    }
}
