package com.enoli.odak

import android.app.Application
import com.enoli.odak.data.Repo
import com.enoli.odak.work.BatchWorker
import com.enoli.odak.work.Notifs
import com.enoli.odak.work.ReminderWorker

class OdakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifs.createChannel(this)
        if (Repo.current.settings.onboarded) ReminderWorker.schedule(this)
        BatchWorker.schedule(this)
    }
}
