package com.smscleaner

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val NAME = "daily_sms_cleanup"

    /** Applies the current schedule settings: enqueue a 24h periodic job, or cancel it. */
    fun apply(ctx: Context) {
        val store = RuleStore(ctx)
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (!store.scheduleEnabled) {
            wm.cancelUniqueWork(NAME)
            return
        }
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, store.runHour)
            set(Calendar.MINUTE, store.runMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = next.timeInMillis - now.timeInMillis
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
