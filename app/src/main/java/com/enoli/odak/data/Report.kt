package com.enoli.odak.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 10 haftalık "deney raporu" için hesaplamalar. Her şey telefondaki
 * gün kayıtları ve olay günlüğünden üretilir.
 */
object Report {

    data class Week(
        val no: Int,
        val start: Long,
        val baseline: Int,
        val triggers: Int,
        val quits: Int,
        val cards: Int,
        val reviews: Int,
        val correct: Int,
        val ticks: Int,
        val scrollGates: Int,
        val scrollStops: Int,
        val srbai: Double?,
    ) {
        val quitRate: Int? get() = if (triggers == 0) null else quits * 100 / triggers
        val correctRate: Int? get() = if (reviews == 0) null else correct * 100 / reviews
    }

    private fun dayOf(ts: Long): Long =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun hourOf(ts: Long): Int =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).hour

    /** Programın başından bugüne haftalar (en fazla son 10 + gerekirse fazlası). */
    fun weeks(d: AppData): List<Week> {
        val start = d.settings.startDay
        val today = Day.today()
        if (today < start) return emptyList()
        val count = ((today - start) / 7).toInt() + 1
        return (0 until count).map { w ->
            val from = start + w * 7L
            val to = from + 6
            val logs = d.days.values.filter { it.day in from..to }
            val evs = d.events.filter { dayOf(it.ts) in from..to }
            val srbai = d.srbai.filter { it.weekStart in Day.weekStart(from)..to }.maxByOrNull { it.weekStart }?.average
            Week(
                no = w + 1,
                start = from,
                baseline = logs.sumOf { it.baseline },
                triggers = logs.sumOf { it.triggers },
                quits = logs.sumOf { it.quits },
                cards = logs.sumOf { it.cards },
                reviews = logs.sumOf { it.reviews },
                correct = logs.sumOf { it.correct },
                ticks = logs.count { it.ticked },
                scrollGates = evs.count { it.type == "scroll_gate" },
                scrollStops = evs.count { it.type == "scroll_stop" },
                srbai = srbai,
            )
        }
    }

    /** Ölçüm haftasında günlük ortalama dürtü (veri yoksa null). */
    fun baselinePerDay(d: AppData): Double? {
        val s = d.settings
        if (!s.baselineWeek) return null
        val days = d.days.values.filter { it.day >= s.startDay && it.day < s.startDay + 7 && it.day <= Day.today() }
        val elapsed = minOf(7L, Day.today() - s.startDay + 1).coerceAtLeast(1)
        val total = days.sumOf { it.baseline }
        return if (total == 0 && days.isEmpty()) null else total.toDouble() / elapsed
    }

    data class MoodSummary(
        val counts: Map<String, Int>,
        /** En sık bağlam: his · saat dilimi · uygulama */
        val topContext: String?,
    )

    fun moods(d: AppData, sinceDay: Long? = null): MoodSummary {
        val evs = d.events.filter { it.type == "mood" && (sinceDay == null || dayOf(it.ts) >= sinceDay) }
        val counts = Mood.all.associateWith { m -> evs.count { it.value == m } }
        val top = evs
            .groupingBy { Triple(it.value, Mood.timeBucket(hourOf(it.ts)), it.pkg) }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.let { (m, t, p) ->
                val app = Catalog.find(p)?.label ?: p.ifBlank { "?" }
                "${Mood.label(m)} · $t · $app"
            }
        return MoodSummary(counts, top)
    }

    /** Günlük satırlarla CSV. Virgül içeren alan yok; yine de tırnaklanır. */
    fun csv(d: AppData): String {
        val start = d.settings.startDay
        val today = Day.today()
        val srbaiByWeek = d.srbai.associate { it.weekStart to it.average }
        val evByDay = d.events.groupBy { dayOf(it.ts) }
        val sb = StringBuilder()
        sb.append(
            "tarih,hafta,olcum_durtusu,durdurma,vazgecme,yine_de_acma,vazgecme_orani,cozulen_kart,degerlendirme,dogru,dogru_orani," +
                "kaydirma_ara_ekrani,kaydirma_birak,kaydirma_5dk,sikildim,yorgunum,kaciyorum,aliskanlik,tik,srbai_hafta_ort\n",
        )
        var day = start
        while (day <= today) {
            val l = d.day(day)
            val ev = evByDay[day].orEmpty()
            fun c(type: String, value: String? = null) = ev.count { it.type == type && (value == null || it.value == value) }
            val week = ((day - start) / 7) + 1
            val quitRate = if (l.triggers == 0) "" else "%.2f".format(java.util.Locale.US, l.quits.toDouble() / l.triggers)
            val correctRate = if (l.reviews == 0) "" else "%.2f".format(java.util.Locale.US, l.correct.toDouble() / l.reviews)
            val srbai = srbaiByWeek[Day.weekStart(day)]?.let { "%.2f".format(java.util.Locale.US, it) } ?: ""
            sb.append(
                listOf(
                    Day.date(day).toString(), week.toString(), l.baseline.toString(), l.triggers.toString(),
                    l.quits.toString(), l.opens.toString(), quitRate, l.cards.toString(), l.reviews.toString(),
                    l.correct.toString(), correctRate, c("scroll_gate").toString(), c("scroll_stop").toString(),
                    c("scroll_more").toString(), c("mood", Mood.BORED).toString(), c("mood", Mood.TIRED).toString(),
                    c("mood", Mood.ESCAPE).toString(), c("mood", Mood.HABIT).toString(),
                    if (l.ticked) "1" else "0", srbai,
                ).joinToString(",") { "\"$it\"" },
            )
            sb.append('\n')
            day++
        }
        return sb.toString()
    }
}
