package com.enoli.odak.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.enoli.odak.data.Day
import com.enoli.odak.data.Repo
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Her gün plan saatinde bir kez çalışır:
 *  - bugün tik yoksa planı hatırlatır,
 *  - ara 2+ gün uzadıysa nazik bir "kaldığın yerden" mesajı verir,
 *  - pazar günü haftalık 4 soruyu hatırlatır.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Repo.init(applicationContext)
        val d = Repo.current
        val s = d.settings
        if (!s.onboarded || !s.remindersOn) return Result.success()

        val today = d.today()
        if (!today.ticked) {
            val gap = Repo.daysSinceLastTick(d)
            if (gap != null && gap >= 2) {
                Notifs.post(
                    applicationContext, Notifs.ID_PLAN,
                    "Ara biraz uzadı",
                    "Sorun değil, kaldığın yerden devam. Bugün 3 kart bile yeter.",
                )
            } else {
                val plan = if (s.studyIf.isNotBlank() && s.studyThen.isNotBlank()) {
                    "${s.studyIf} → ${s.studyThen}"
                } else {
                    "Plan saati geldi. 3 kartla başlayabilirsin."
                }
                Notifs.post(applicationContext, Notifs.ID_PLAN, "Plan saati", plan)
            }
        }

        val week = Day.weekStart(Day.today())
        if (Day.isSunday() && d.srbai.none { it.weekStart == week }) {
            Notifs.post(applicationContext, Notifs.ID_SRBAI, "Haftalık 4 soru", "Çalışma alışkanlığın ne kadar otomatikleşti? 30 saniye sürer.")
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "odak_daily_plan"

        /** Plan saatine hizalanmış günlük iş. [replace] true ise saat değişikliğini uygular. */
        fun schedule(context: Context, replace: Boolean = false) {
            val s = Repo.current.settings
            val now = LocalDateTime.now()
            var next = now.withHour(s.planHour).withMinute(s.planMinute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delayMs = Duration.between(now, next).toMillis()

            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
