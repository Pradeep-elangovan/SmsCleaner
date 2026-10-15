package com.smscleaner

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Entry point for "send an SMS" requests from other apps (Contacts, dialer, share sheets).
 * It only extracts the number and text and opens the in-app New message screen.
 */
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ssp = intent?.data?.schemeSpecificPart ?: ""
        val to = Uri.decode(ssp.substringBefore('?')).trim()
        val queryBody = ssp.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("body=") }
            ?.removePrefix("body=")
            ?.let { Uri.decode(it) }
        val body = intent?.getStringExtra("sms_body")
            ?: intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: queryBody
            ?: ""

        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TO, to)
                .putExtra(MainActivity.EXTRA_BODY, body)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
