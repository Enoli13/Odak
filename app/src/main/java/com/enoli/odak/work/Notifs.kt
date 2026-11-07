package com.enoli.odak.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.enoli.odak.R
import com.enoli.odak.data.Repo
import com.enoli.odak.ui.MainActivity

object Notifs {
    private const val CHANNEL = "odak_nudges"
    const val ID_PLAN = 1
    const val ID_WHAT = 2
    const val ID_SRBAI = 3

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Hatırlatmalar", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Plan saati, kısa ara hatırlatması ve haftalık 4 soru"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_odak)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    /** "Yine de aç" sonrası uygulamadan çıkınca: suçlamadan, planı hatırlat. */
    fun whatWereYouDoing(context: Context) {
        val s = Repo.current.settings
        val plan = s.studyThen.ifBlank { s.goal }
        val text = if (plan.isBlank()) "Döndüysen, 3 kartla ısınabilirsin." else "Planın: $plan"
        post(context, ID_WHAT, "Az önce ne yapacaktın?", text)
    }
}
