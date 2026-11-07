package com.enoli.odak.service

/**
 * Bellekte tutulan anlık durum: "Yine de aç" izinleri ve son açılış zamanı.
 * Bir izin, kullanıcı uygulamadan çıkıp [GRACE_MS] süresinden uzun kalınca biter.
 */
object GateState {
    private const val GRACE_MS = 30_000L
    private const val LAUNCH_DEBOUNCE_MS = 2_500L

    private class Pass(var leftAt: Long = 0L, var reminded: Boolean = false)

    // ---- 1 · Sürekli scroll sayacı ----
    private const val SCROLL_GAP_MS = 30_000L
    private class ScrollRun(var start: Long, var last: Long)
    private val scrolls = HashMap<String, ScrollRun>()

    /** Kaydırma olayını sayar; kesintisiz kaydırma süresini (ms) döner. */
    @Synchronized
    fun onScroll(pkg: String, now: Long = System.currentTimeMillis()): Long {
        val run = scrolls[pkg]
        if (run == null || now - run.last > SCROLL_GAP_MS) {
            scrolls[pkg] = ScrollRun(now, now)
            return 0L
        }
        run.last = now
        return now - run.start
    }

    /** "5 dk daha": sayaç, eşiğe 5 dakika kalacak şekilde geri alınır. */
    @Synchronized
    fun snoozeScroll(pkg: String, thresholdMs: Long, now: Long = System.currentTimeMillis()) {
        scrolls[pkg] = ScrollRun(now - thresholdMs + 5 * 60_000L, now)
    }

    @Synchronized
    fun resetScroll(pkg: String) {
        scrolls.remove(pkg)
    }

    private val passes = HashMap<String, Pass>()

    @Volatile
    var lastLaunchAt: Long = 0L
        private set

    /** [remind] false ise (ör. ölçüm haftası) çıkışta "ne yapacaktın?" bildirimi gönderilmez. */
    @Synchronized
    fun grant(pkg: String, remind: Boolean = true) {
        passes[pkg] = Pass(reminded = !remind)
    }

    @Synchronized
    fun hasPass(pkg: String, now: Long = System.currentTimeMillis()): Boolean {
        val p = passes[pkg] ?: return false
        if (p.leftAt == 0L) return true
        return if (now - p.leftAt < GRACE_MS) {
            p.leftAt = 0L // kısa bir çıkıştan geri döndü
            true
        } else {
            passes.remove(pkg)
            false
        }
    }

    /**
     * Kullanıcı hedef uygulamadan çıktı. "Yine de aç" ile girdiyse ve henüz
     * hatırlatılmadıysa true döner: "Az önce ne yapacaktın?" bildirimi gösterilir.
     */
    @Synchronized
    fun onLeft(pkg: String, now: Long = System.currentTimeMillis()): Boolean {
        val p = passes[pkg] ?: return false
        p.leftAt = now
        if (p.reminded) return false
        p.reminded = true
        return true
    }

    fun canLaunch(now: Long = System.currentTimeMillis()): Boolean = now - lastLaunchAt > LAUNCH_DEBOUNCE_MS

    fun markLaunched(now: Long = System.currentTimeMillis()) {
        lastLaunchAt = now
    }
}
