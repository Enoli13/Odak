package com.enoli.odak.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.enoli.odak.data.Repo
import com.enoli.odak.data.TargetApp
import com.enoli.odak.ui.stop.StopActivity
import com.enoli.odak.work.Notifs

/**
 * Algılama katmanı. Hedef uygulama (ya da onun Reels/Shorts/Keşfet ekranı) öne
 * geldiğinde durdurma ekranını açar. Ekrandaki içerik okunmaz ya da kaydedilmez.
 */
class GateService : AccessibilityService() {

    private var foregroundPkg: String? = null
    private var lastScanAt = 0L
    private var ignored: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Repo.init(applicationContext)
        val imes = try {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.map { it.packageName }
        } catch (_: Exception) {
            emptyList()
        }
        ignored = setOf(packageName, "com.android.systemui", "android") + imes
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return

        val data = Repo.current
        if (!data.settings.onboarded) return
        val targets = data.settings.targets.associateBy { it.pkg }
        val now = System.currentTimeMillis()

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != foregroundPkg) {
            val previous = foregroundPkg
            foregroundPkg = pkg
            if (previous != null && previous in targets && GateState.onLeft(previous, now)) {
                Notifs.whatWereYouDoing(this)
            }
        }
        if (pkg != foregroundPkg) return

        val target = targets[pkg] ?: return
        evaluate(target, event, now)
    }

    private fun evaluate(target: TargetApp, event: AccessibilityEvent, now: Long) {
        if (Repo.onBreak()) return
        if (GateState.hasPass(target.pkg, now)) return
        if (!GateState.canLaunch(now)) return

        val isStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!isStateChange && now - lastScanAt < 400) return
        lastScanAt = now

        // Öndeki pencere gerçekten hedef uygulama mı? (Durdurma ekranı açıkken değildir.)
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != target.pkg) return

        if (target.shortOnly && !Detectors.isShortVideo(target.pkg, root)) return

        launchGate(target, now)
    }

    private fun launchGate(target: TargetApp, now: Long) {
        GateState.markLaunched(now)
        Repo.logTrigger()
        val intent = Intent(this, StopActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(StopActivity.EXTRA_PKG, target.pkg)
            .putExtra(StopActivity.EXTRA_LABEL, target.label)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Sistem engellediyse sessizce geç; kullanıcıyı uygulamada kilitlemeyiz.
        }
    }

    override fun onInterrupt() = Unit
}
