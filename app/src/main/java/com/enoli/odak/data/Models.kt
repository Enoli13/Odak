package com.enoli.odak.data

import java.time.DayOfWeek
import java.time.LocalDate

/** Kullanıcının durdurma ekranı koymak istediği uygulama. */
data class TargetApp(
    val pkg: String,
    val label: String,
    /** true: yalnızca Reels/Shorts/Keşfet açılınca dur. false: uygulama her açıldığında dur. */
    val shortOnly: Boolean,
)

data class Deck(
    val id: Long,
    val name: String,
)

data class Card(
    val id: Long,
    val deckId: Long,
    val front: String,
    val back: String,
    /** İsteğe bağlı "Neden?" sorusu: cevaptan sonra kendine açıklama. */
    val why: String = "",
    /** Leitner kutusu: 0 = yeni/bilinmeyen, yükseldikçe aralık uzar. */
    val box: Int = 0,
    val dueAt: Long = 0L,
    val seen: Int = 0,
    val known: Int = 0,
)

/** Bir günün kaydı. Süre değil, davranış sayılır. */
data class DayLog(
    val day: Long,
    val triggers: Int = 0,
    val quits: Int = 0,
    val opens: Int = 0,
    val cards: Int = 0,
    val ticked: Boolean = false,
)

/** Haftalık otomatiklik ölçümü (SRBAI, 4 madde, 1–7). */
data class Srbai(
    val weekStart: Long,
    val scores: List<Int>,
) {
    val average: Double get() = if (scores.isEmpty()) 0.0 else scores.average()
}

data class Settings(
    val onboarded: Boolean = false,
    val startDay: Long = 0L,
    val targets: List<TargetApp> = emptyList(),
    val goal: String = "",
    val studyIf: String = "",
    val studyThen: String = "",
    val urgeIf: String = "",
    val urgeThen: String = "",
    val planHour: Int = 20,
    val planMinute: Int = 0,
    val waitSeconds: Int = 5,
    val gateCards: Int = 3,
    val earnedBreak: Boolean = true,
    val remindersOn: Boolean = true,
    /** Kullanıcı "izin kapalı kalsın" dediyse bir daha ısrar etmeyiz. */
    val accessibilityOptOut: Boolean = false,
)

data class AppData(
    val settings: Settings = Settings(),
    val decks: List<Deck> = emptyList(),
    val cards: List<Card> = emptyList(),
    val days: Map<Long, DayLog> = emptyMap(),
    val srbai: List<Srbai> = emptyList(),
    /** 25 dakikalık çalışma bloğunun başlangıcı (0 = blok yok). */
    val focusStart: Long = 0L,
    /** Hak edilmiş molanın bitiş zamanı. */
    val breakUntil: Long = 0L,
    val nextId: Long = 1L,
) {
    fun today(): DayLog = days[Day.today()] ?: DayLog(Day.today())
    fun day(d: Long): DayLog = days[d] ?: DayLog(d)
    fun cardsIn(deckId: Long): List<Card> = cards.filter { it.deckId == deckId }
}

object Day {
    fun today(): Long = LocalDate.now().toEpochDay()
    fun date(d: Long): LocalDate = LocalDate.ofEpochDay(d)
    /** Haftanın pazartesi günü. */
    fun weekStart(d: Long): Long {
        val date = LocalDate.ofEpochDay(d)
        return date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()).toEpochDay()
    }
    fun isSunday(d: Long = today()): Boolean = LocalDate.ofEpochDay(d).dayOfWeek == DayOfWeek.SUNDAY
}

/** Odak'ın tanıdığı uygulamalar. */
object Catalog {
    data class Known(
        val pkg: String,
        val label: String,
        val shortLabel: String?, // null: yalnızca "tüm uygulama" modu var
    )

    val apps = listOf(
        Known("com.instagram.android", "Instagram", "Yalnızca Reels ve Keşfet"),
        Known("com.google.android.youtube", "YouTube", "Yalnızca Shorts"),
        Known("com.zhiliaoapp.musically", "TikTok", null),
        Known("com.ss.android.ugc.trill", "TikTok (bazı bölgeler)", null),
        Known("com.facebook.katana", "Facebook", null),
        Known("com.twitter.android", "X (Twitter)", null),
        Known("com.snapchat.android", "Snapchat", null),
        Known("com.reddit.frontpage", "Reddit", null),
    )

    fun find(pkg: String): Known? = apps.firstOrNull { it.pkg == pkg }
}
