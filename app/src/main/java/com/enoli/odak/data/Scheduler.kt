package com.enoli.odak.data

/**
 * Basit aralıklı tekrar (Leitner kutuları).
 * Bilinen kart bir üst kutuya çıkar ve aralığı uzar; bilinmeyen kart 0. kutuya döner
 * ve aynı oturumda tekrar gelir. (Sınav tarihine göre aralık ayarı v2'de.)
 */
object Scheduler {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Kutu → gün cinsinden aralık. */
    private val intervals = intArrayOf(0, 1, 2, 4, 7, 14, 30)

    fun review(card: Card, known: Boolean, now: Long = System.currentTimeMillis()): Card =
        if (known) {
            val box = (card.box + 1).coerceAtMost(intervals.size - 1)
            card.copy(box = box, dueAt = now + intervals[box] * DAY_MS, seen = card.seen + 1, known = card.known + 1)
        } else {
            card.copy(box = 0, dueAt = now, seen = card.seen + 1)
        }

    fun dueCount(cards: List<Card>, now: Long = System.currentTimeMillis()): Int =
        cards.count { it.dueAt <= now }

    /**
     * Oturum için [n] kart seçer. Önce zamanı gelmiş kartlar, sonra en yakın zamanda
     * gelecek olanlar. Farklı destelerden sırayla alınır (karışık çalışma).
     */
    fun pick(cards: List<Card>, n: Int, now: Long = System.currentTimeMillis()): List<Card> {
        if (cards.isEmpty() || n <= 0) return emptyList()
        val sorted = cards.sortedWith(compareBy<Card>({ it.dueAt > now }, { it.dueAt }, { it.id }))
        val byDeck = sorted.groupBy { it.deckId }.values.map { ArrayDeque(it) }
        val result = mutableListOf<Card>()
        // Önce zamanı gelmişleri desteler arasında sırayla, sonra kalanları.
        for (dueOnly in listOf(true, false)) {
            var progressed = true
            while (result.size < n && progressed) {
                progressed = false
                for (queue in byDeck) {
                    if (result.size >= n) break
                    val head = queue.firstOrNull() ?: continue
                    if (dueOnly && head.dueAt > now) continue
                    result += queue.removeFirst()
                    progressed = true
                }
            }
        }
        return result
    }
}
