package com.smscleaner

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Handles "reply with message" requests (for example declining a call with a text). */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.data?.schemeSpecificPart?.substringBefore('?')?.trim()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!number.isNullOrBlank() && !text.isNullOrBlank()) {
            Thread {
                try {
                    SmsSender.send(applicationContext, number, text)
                } finally {
                    stopSelf(startId)
                }
            }.start()
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}
