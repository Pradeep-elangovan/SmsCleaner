package com.smscleaner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimInfo(val subId: Int, val slot: Int, val name: String)

object Sims {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /** Active SIMs ordered by slot. Empty without the phone-state permission. */
    @SuppressLint("MissingPermission")
    fun list(ctx: Context): List<SimInfo> {
        if (!hasPermission(ctx)) return emptyList()
        return try {
            val sm = ctx.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            (sm.activeSubscriptionInfoList ?: emptyList())
                .sortedBy { it.simSlotIndex }
                .map {
                    val label = (it.displayName ?: it.carrierName ?: "").toString()
                    SimInfo(it.subscriptionId, it.simSlotIndex, label.ifBlank { "SIM ${it.simSlotIndex + 1}" })
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** The SIM the system uses for SMS by default, or -1. */
    fun defaultSmsSubId(): Int = try {
        SubscriptionManager.getDefaultSmsSubscriptionId()
    } catch (e: Exception) {
        -1
    }

    /** The saved choice if that SIM is still active, else the system default, else the first SIM. */
    fun resolve(sims: List<SimInfo>, saved: Int): Int =
        sims.firstOrNull { it.subId == saved }?.subId
            ?: sims.firstOrNull { it.subId == defaultSmsSubId() }?.subId
            ?: sims.firstOrNull()?.subId
            ?: -1
}
