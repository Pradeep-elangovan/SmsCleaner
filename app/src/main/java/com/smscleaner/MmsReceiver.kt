package com.smscleaner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Required for default-SMS-app eligibility. MMS download is not implemented in this app. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // intentionally empty
    }
}
