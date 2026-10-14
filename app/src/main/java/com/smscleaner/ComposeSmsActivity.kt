package com.smscleaner

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** Required for default-SMS-app eligibility. Sending is not implemented in this app. */
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(
            this,
            "SMS Cleaner cannot send messages. Switch your default SMS app back to send.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
}
