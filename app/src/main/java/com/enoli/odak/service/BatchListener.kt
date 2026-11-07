package com.enoli.odak.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.enoli.odak.data.QueuedNotif
import com.enoli.odak.data.Repo

/**
 * 3 · Toplu bildirim modu. Seçilen uygulamaların bildirimleri anında gösterilmez;
 * kuyruğa alınır ve günde birkaç sabit saatte tek bir özet olarak gelir.
 * Bildirim içeriği yalnızca telefonda tutulur.
 */
class BatchListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Repo.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val s = Repo.current.settings
        if (!s.batchOn || sbn.packageName !in s.batchApps) return

        val n = sbn.notification ?: return
        val skipFlags = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE
        if ((n.flags and skipFlags) != 0) return
        if (n.category == Notification.CATEGORY_CALL) return

        // Grup özetleri yalnızca kaldırılır; içerik tekil bildirimlerde.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            cancelSafely(sbn.key)
            return
        }

        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        cancelSafely(sbn.key)
        if (title.isBlank() && text.isBlank()) return
        Repo.queueNotif(QueuedNotif(sbn.postTime, sbn.packageName, title, text))
    }

    private fun cancelSafely(key: String) {
        try {
            cancelNotification(key)
        } catch (_: Exception) {
        }
    }
}
