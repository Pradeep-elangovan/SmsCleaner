package com.smscleaner

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class CleanupWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val r = CleanupRunner.runAll(applicationContext)
        if (r.error != null || r.deleted > 0) {
            Notifier.post(applicationContext, "SMS Cleaner", r.summary(), id = 1)
        }
        return Result.success()
    }
}
