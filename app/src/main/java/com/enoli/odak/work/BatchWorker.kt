package com.enoli.odak.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.enoli.odak.data.Catalog
import com.enoli.odak.data.Repo
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 15 dakikada bir bakar: son teslim saatinden bu yana bir toplu saat geçtiyse
 * kuyruktaki bildirimleri tek bir özet olarak gösterir.
 */
class BatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Repo.init(applicationContext)
        val d = Repo.current
        if (!d.settings.batchOn) return Result.success()

        val slot = lastSlotMillis(d.settings.batchHours) ?: return Result.success()
        if (d.lastBatchDelivery >= slot) return Result.success()

        val queued = d.batchQueue
        Repo.deliverBatch()
        if (queued.isNotEmpty()) {
            val byApp = queued.groupBy { it.pkg }
                .map { (pkg, list) -> "${Catalog.find(pkg)?.label ?: pkg}: ${list.size}" }
                .joinToString(" · ")
            Notifs.postBatch(applicationContext, queued.size, byApp)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "odak_batch"

        /** Şu andan önceki en son toplu saat (bugün ya da dün). */
        fun lastSlotMillis(hours: List<Int>, now: LocalDateTime = LocalDateTime.now()): Long? {
            if (hours.isEmpty()) return null
            val candidates = hours.flatMap { h ->
                val today = now.withHour(h.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
                listOf(today, today.minusDays(1))
            }.filter { !it.isAfter(now) }
            val last = candidates.maxOrNull() ?: return null
            return last.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!Repo.current.settings.batchOn) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val req = PeriodicWorkRequestBuilder<BatchWorker>(15, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
