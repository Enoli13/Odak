package com.enoli.odak.service

/**
 * Bellekte tutulan anlık durum: "Yine de aç" izinleri ve son açılış zamanı.
 * Bir izin, kullanıcı uygulamadan çıkıp [GRACE_MS] süresinden uzun kalınca biter.
 */
object GateState {
    private const val GRACE_MS = 30_000L
    private const val LAUNCH_DEBOUNCE_MS = 2_500L

    private class Pass(var leftAt: Long = 0L, var reminded: Boolean = false)

    private val passes = HashMap<String, Pass>()

    @Volatile
    var lastLaunchAt: Long = 0L
        private set

    @Synchronized
    fun grant(pkg: String) {
        passes[pkg] = Pass()
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
