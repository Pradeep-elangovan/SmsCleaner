package com.smscleaner

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Required for default-SMS-app eligibility ("quick reply" from the dialer). Not implemented. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
