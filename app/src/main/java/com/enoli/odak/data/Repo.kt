package com.enoli.odak.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tüm veri telefonda, tek bir JSON dosyasında durur. Sunucu ve hesap yok.
 * Arayüz [data] akışını dinler; erişilebilirlik servisi [current] değerini okur.
 */
object Repo {
    private const val TAG = "OdakRepo"
    private lateinit var file: File
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data
    val current: AppData get() = _data.value

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "odak.json")
        _data.value = load()
    }

    @Synchronized
    fun update(block: (AppData) -> AppData) {
        val next = block(_data.value)
        _data.value = next
        save(next)
    }

    fun updateSettings(block: (Settings) -> Settings) = update { it.copy(settings = block(it.settings)) }

    // ---------- Günlük sayaçlar ----------

    private const val MAX_EVENTS = 20_000

    private fun withEvent(d: AppData, type: String, pkg: String, value: String): AppData {
        val ev = Ev(System.currentTimeMillis(), type, pkg, value)
        val list = if (d.events.size >= MAX_EVENTS) d.events.drop(d.events.size - MAX_EVENTS + 1) else d.events
        return d.copy(events = list + ev)
    }

    /** Günün sayacını günceller ve isteğe bağlı olarak olay günlüğüne yazar. */
    private fun bumpToday(type: String? = null, pkg: String = "", value: String = "", block: (DayLog) -> DayLog) =
        update { d ->
            val today = Day.today()
            val next = d.copy(days = d.days + (today to block(d.day(today))))
            if (type == null) next else withEvent(next, type, pkg, value)
        }

    fun logTrigger(pkg: String = "") = bumpToday("gate", pkg) { it.copy(triggers = it.triggers + 1) }
    fun logQuit(pkg: String = "") = bumpToday("quit", pkg) { it.copy(quits = it.quits + 1) }
    fun logOpen(pkg: String = "") = bumpToday("open", pkg) { it.copy(opens = it.opens + 1) }
    fun logBaseline(pkg: String) = bumpToday("baseline", pkg) { it.copy(baseline = it.baseline + 1) }

    /** Günlük sayacı olmayan olaylar: mood, scroll_gate, scroll_stop, scroll_more. */
    fun logEvent(type: String, pkg: String = "", value: String = "") = update { withEvent(it, type, pkg, value) }

    /** Bir kart oturumu bitti: kartları say ve günün tikini ver. */
    fun logSession(cardsDone: Int) {
        if (cardsDone <= 0) return
        bumpToday { it.copy(cards = it.cards + cardsDone, ticked = true) }
    }

    fun toggleTick(day: Long) = update { d ->
        val log = d.day(day)
        d.copy(days = d.days + (day to log.copy(ticked = !log.ticked)))
    }

    // ---------- Desteler ve kartlar ----------

    fun addDeck(name: String): Long {
        var id = 0L
        update { d ->
            id = d.nextId
            d.copy(decks = d.decks + Deck(id, name.trim()), nextId = id + 1)
        }
        return id
    }

    fun renameDeck(id: Long, name: String) = update { d ->
        d.copy(decks = d.decks.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deleteDeck(id: Long) = update { d ->
        d.copy(decks = d.decks.filterNot { it.id == id }, cards = d.cards.filterNot { it.deckId == id })
    }

    fun addCard(deckId: Long, front: String, back: String, why: String = "") = update { d ->
        val id = d.nextId
        d.copy(
            cards = d.cards + Card(id, deckId, front.trim(), back.trim(), why.trim()),
            nextId = id + 1,
        )
    }

    fun editCard(id: Long, front: String, back: String, why: String) = update { d ->
        d.copy(cards = d.cards.map {
            if (it.id == id) it.copy(front = front.trim(), back = back.trim(), why = why.trim()) else it
        })
    }

    fun deleteCard(id: Long) = update { d -> d.copy(cards = d.cards.filterNot { it.id == id }) }

    /**
     * Toplu ekleme. Her satır bir kart: "soru | cevap" ya da "soru | cevap | neden?".
     * Ayırıcı olarak yalnızca | ve sekme kabul edilir.
     */
    fun importText(deckId: Long, text: String): Int {
        val parsed = text.lines().mapNotNull { line ->
            val parts = line.split('|', '\t').map { it.trim() }
            if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts else null
        }
        if (parsed.isEmpty()) return 0
        update { d ->
            var next = d.nextId
            val newCards = parsed.map { p ->
                Card(next++, deckId, p[0], p[1], p.getOrElse(2) { "" })
            }
            d.copy(cards = d.cards + newCards, nextId = next)
        }
        return parsed.size
    }

    fun review(cardId: Long, known: Boolean) = update { d ->
        val today = Day.today()
        val log = d.day(today)
        d.copy(
            cards = d.cards.map { if (it.id == cardId) Scheduler.review(it, known) else it },
            days = d.days + (today to log.copy(reviews = log.reviews + 1, correct = log.correct + if (known) 1 else 0)),
        )
    }

    // ---------- Ritim ----------

    fun saveSrbai(weekStart: Long, scores: List<Int>) = update { d ->
        withdraw(d.copy(srbai = d.srbai.filterNot { it.weekStart == weekStart } + Srbai(weekStart, scores)))
    }

    /**
     * Kademeli geri çekilme. SRBAI ortalaması 2 hafta 5+ ise bekleme ve kaydırma eşiği gevşer,
     * 4 hafta 6+ ise bakım modu önerilir. Puan düşerse bir önceki seviyeye dönülür.
     */
    private fun withdraw(d: AppData): AppData {
        val s = d.settings
        val hist = d.srbai.sortedBy { it.weekStart }.map { it.average }
        val latest = hist.lastOrNull() ?: return d
        var level = s.withdrawLevel
        var maintenance = s.maintenance
        var offered = s.maintenanceOffered
        var note = s.withdrawNote
        val last2 = hist.takeLast(2)
        val last4 = hist.takeLast(4)
        when {
            maintenance && latest < 6 -> {
                maintenance = false
                offered = false
                level = 1
                note = "Bu hafta biraz zorlandın; durdurma ekranı geri geldi. Sorun değil, iskele hâlâ burada."
            }
            level >= 1 && latest < 5 -> {
                level = 0
                offered = false
                note = "Puanın biraz düştü; bekleme süresi ve kaydırma eşiği eski hâline döndü. Bu çok normal."
            }
            else -> {
                if (level == 0 && last2.size == 2 && last2.all { it >= 5 }) {
                    level = 1
                    note = "Alışkanlığın oturuyor: bekleme 3 sn'ye, kaydırma eşiği 20 dk'ya indi."
                }
                if (level >= 1 && !maintenance && last4.size == 4 && last4.all { it >= 6 }) offered = true
            }
        }
        return d.copy(
            settings = s.copy(withdrawLevel = level, maintenance = maintenance, maintenanceOffered = offered, withdrawNote = note),
        )
    }

    fun acceptMaintenance(accept: Boolean) = updateSettings {
        it.copy(maintenance = accept, maintenanceOffered = false, withdrawLevel = if (accept) 2 else it.withdrawLevel)
    }

    fun setMaintenance(on: Boolean) = updateSettings {
        it.copy(maintenance = on, withdrawLevel = if (on) 2 else minOf(it.withdrawLevel, 1))
    }

    fun dismissWithdrawNote() = updateSettings { it.copy(withdrawNote = "") }

    // ---------- Etkin değerler ----------

    fun baselineActive(d: AppData = current): Boolean =
        d.settings.baselineWeek && Day.today() - d.settings.startDay < 7

    fun baselineDay(d: AppData = current): Int = (Day.today() - d.settings.startDay).toInt() + 1

    /** Durdurma ekranı ve kaydırma ara ekranı açılabilir mi? */
    fun interventionsOn(d: AppData = current): Boolean = !d.settings.maintenance && !baselineActive(d)

    fun effectiveWait(d: AppData = current): Int =
        if (d.settings.withdrawLevel >= 1) minOf(d.settings.waitSeconds, 3) else d.settings.waitSeconds

    fun effectiveScrollMin(d: AppData = current): Int =
        if (d.settings.withdrawLevel >= 1) maxOf(d.settings.scrollThresholdMin, 20) else d.settings.scrollThresholdMin

    // ---------- Toplu bildirimler ----------

    fun queueNotif(n: QueuedNotif) = update { it.copy(batchQueue = (it.batchQueue + n).takeLast(500)) }

    /** Kuyruğu kutuya taşır; teslim edilen bildirim sayısını döner. */
    fun deliverBatch(): Int {
        var count = 0
        update { d ->
            count = d.batchQueue.size
            d.copy(
                batchInbox = (d.batchInbox + d.batchQueue).takeLast(500),
                batchQueue = emptyList(),
                lastBatchDelivery = System.currentTimeMillis(),
            )
        }
        return count
    }

    fun clearInbox(pkg: String? = null) = update { d ->
        d.copy(batchInbox = if (pkg == null) emptyList() else d.batchInbox.filterNot { it.pkg == pkg })
    }

    fun startFocus() = update { it.copy(focusStart = System.currentTimeMillis()) }
    fun stopFocus() = update { it.copy(focusStart = 0L) }

    /** 25 dakikalık blok bittiyse 10 dakikalık mola başlatır. */
    fun takeEarnedBreak() = update {
        it.copy(focusStart = 0L, breakUntil = System.currentTimeMillis() + BREAK_MS)
    }

    const val FOCUS_MS = 25 * 60 * 1000L
    const val BREAK_MS = 10 * 60 * 1000L

    fun earnedBreakReady(d: AppData = current): Boolean =
        d.settings.earnedBreak && d.focusStart > 0 &&
            System.currentTimeMillis() - d.focusStart >= FOCUS_MS

    fun onBreak(d: AppData = current): Boolean = System.currentTimeMillis() < d.breakUntil

    /** Ritim takviminde son tikten bu yana geçen gün. Hiç tik yoksa null. */
    fun daysSinceLastTick(d: AppData = current): Long? {
        val last = d.days.values.filter { it.ticked }.maxOfOrNull { it.day } ?: return null
        return Day.today() - last
    }

    // ---------- Örnek deste ----------

    fun seedExampleDeck() {
        if (current.decks.isNotEmpty()) return
        val id = addDeck("Örnek: Hücre biyolojisi")
        importText(
            id,
            """
            Hücrenin enerji santrali olarak bilinen organel hangisidir? | Mitokondri | Neden kendi DNA'sı var?
            Protein sentezinin gerçekleştiği yapı nedir? | Ribozom
            DNA'nın kopyalanmasına ne ad verilir? | Replikasyon
            mRNA'dan protein üretilmesine ne ad verilir? | Translasyon (çeviri)
            Hücre zarının temel yapısını oluşturan molekül nedir? | Fosfolipit çift katman | Neden çift katman oluşur?
            """.trimIndent(),
        )
    }

    // ---------- JSON ----------

    private fun save(d: AppData) {
        try {
            val tmp = File(file.parentFile, "odak.json.tmp")
            tmp.writeText(toJson(d).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kaydedilemedi", e)
        }
    }

    private fun load(): AppData {
        if (!file.exists()) return AppData()
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            Log.e(TAG, "Okunamadı, boş başlıyor", e)
            AppData()
        }
    }

    private fun toJson(d: AppData): JSONObject {
        val s = d.settings
        val settings = JSONObject()
            .put("onboarded", s.onboarded)
            .put("startDay", s.startDay)
            .put("targets", JSONArray().apply {
                s.targets.forEach {
                    put(JSONObject().put("pkg", it.pkg).put("label", it.label).put("shortOnly", it.shortOnly))
                }
            })
            .put("goal", s.goal)
            .put("studyIf", s.studyIf).put("studyThen", s.studyThen)
            .put("urgeIf", s.urgeIf).put("urgeThen", s.urgeThen)
            .put("planHour", s.planHour).put("planMinute", s.planMinute)
            .put("waitSeconds", s.waitSeconds)
            .put("gateCards", s.gateCards)
            .put("earnedBreak", s.earnedBreak)
            .put("remindersOn", s.remindersOn)
            .put("accessibilityOptOut", s.accessibilityOptOut)
            .put("scrollThresholdMin", s.scrollThresholdMin)
            .put("lastTask", s.lastTask)
            .put("lastFirstStep", s.lastFirstStep)
            .put("batchOn", s.batchOn)
            .put("batchApps", JSONArray(s.batchApps))
            .put("batchHours", JSONArray(s.batchHours))
            .put("withdrawLevel", s.withdrawLevel)
            .put("maintenance", s.maintenance)
            .put("maintenanceOffered", s.maintenanceOffered)
            .put("withdrawNote", s.withdrawNote)
            .put("baselineWeek", s.baselineWeek)

        return JSONObject()
            .put("version", 1)
            .put("settings", settings)
            .put("decks", JSONArray().apply {
                d.decks.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
            })
            .put("cards", JSONArray().apply {
                d.cards.forEach {
                    put(
                        JSONObject().put("id", it.id).put("deckId", it.deckId)
                            .put("front", it.front).put("back", it.back).put("why", it.why)
                            .put("box", it.box).put("dueAt", it.dueAt)
                            .put("seen", it.seen).put("known", it.known),
                    )
                }
            })
            .put("days", JSONArray().apply {
                d.days.values.forEach {
                    put(
                        JSONObject().put("day", it.day).put("triggers", it.triggers)
                            .put("quits", it.quits).put("opens", it.opens)
                            .put("cards", it.cards).put("ticked", it.ticked)
                            .put("reviews", it.reviews).put("correct", it.correct).put("baseline", it.baseline),
                    )
                }
            })
            .put("srbai", JSONArray().apply {
                d.srbai.forEach {
                    put(JSONObject().put("weekStart", it.weekStart).put("scores", JSONArray(it.scores)))
                }
            })
            .put("events", JSONArray().apply {
                d.events.forEach {
                    put(JSONObject().put("ts", it.ts).put("t", it.type).put("p", it.pkg).put("v", it.value))
                }
            })
            .put("batchQueue", notifsJson(d.batchQueue))
            .put("batchInbox", notifsJson(d.batchInbox))
            .put("lastBatchDelivery", d.lastBatchDelivery)
            .put("focusStart", d.focusStart)
            .put("breakUntil", d.breakUntil)
            .put("nextId", d.nextId)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

    private fun notifsJson(list: List<QueuedNotif>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("ts", it.ts).put("p", it.pkg).put("ti", it.title).put("tx", it.text)) }
    }

    private fun notifsFrom(a: JSONArray?): List<QueuedNotif> = (a ?: JSONArray()).objects().map {
        QueuedNotif(it.optLong("ts"), it.optString("p"), it.optString("ti"), it.optString("tx"))
    }

    private fun fromJson(o: JSONObject): AppData {
        val s = o.optJSONObject("settings") ?: JSONObject()
        val settings = Settings(
            onboarded = s.optBoolean("onboarded", false),
            startDay = s.optLong("startDay", 0L),
            targets = (s.optJSONArray("targets") ?: JSONArray()).objects().map {
                TargetApp(it.getString("pkg"), it.optString("label"), it.optBoolean("shortOnly", false))
            },
            goal = s.optString("goal"),
            studyIf = s.optString("studyIf"), studyThen = s.optString("studyThen"),
            urgeIf = s.optString("urgeIf"), urgeThen = s.optString("urgeThen"),
            planHour = s.optInt("planHour", 20), planMinute = s.optInt("planMinute", 0),
            waitSeconds = s.optInt("waitSeconds", 5),
            gateCards = s.optInt("gateCards", 3),
            earnedBreak = s.optBoolean("earnedBreak", true),
            remindersOn = s.optBoolean("remindersOn", true),
            accessibilityOptOut = s.optBoolean("accessibilityOptOut", false),
            scrollThresholdMin = s.optInt("scrollThresholdMin", 15),
            lastTask = s.optString("lastTask"),
            lastFirstStep = s.optString("lastFirstStep"),
            batchOn = s.optBoolean("batchOn", false),
            batchApps = s.optJSONArray("batchApps")?.strings() ?: Settings().batchApps,
            batchHours = s.optJSONArray("batchHours")?.ints() ?: Settings().batchHours,
            withdrawLevel = s.optInt("withdrawLevel", 0),
            maintenance = s.optBoolean("maintenance", false),
            maintenanceOffered = s.optBoolean("maintenanceOffered", false),
            withdrawNote = s.optString("withdrawNote"),
            baselineWeek = s.optBoolean("baselineWeek", true),
        )
        val decks = (o.optJSONArray("decks") ?: JSONArray()).objects().map {
            Deck(it.getLong("id"), it.optString("name"))
        }
        val cards = (o.optJSONArray("cards") ?: JSONArray()).objects().map {
            Card(
                id = it.getLong("id"), deckId = it.getLong("deckId"),
                front = it.optString("front"), back = it.optString("back"), why = it.optString("why"),
                box = it.optInt("box", 0), dueAt = it.optLong("dueAt", 0L),
                seen = it.optInt("seen", 0), known = it.optInt("known", 0),
            )
        }
        val days = (o.optJSONArray("days") ?: JSONArray()).objects().map {
            DayLog(
                day = it.getLong("day"), triggers = it.optInt("triggers"), quits = it.optInt("quits"),
                opens = it.optInt("opens"), cards = it.optInt("cards"), ticked = it.optBoolean("ticked"),
                reviews = it.optInt("reviews"), correct = it.optInt("correct"), baseline = it.optInt("baseline"),
            )
        }.associateBy { it.day }
        val srbai = (o.optJSONArray("srbai") ?: JSONArray()).objects().map {
            val arr = it.optJSONArray("scores") ?: JSONArray()
            Srbai(it.getLong("weekStart"), (0 until arr.length()).map { i -> arr.getInt(i) })
        }
        return AppData(
            settings = settings, decks = decks, cards = cards, days = days, srbai = srbai,
            focusStart = o.optLong("focusStart", 0L), breakUntil = o.optLong("breakUntil", 0L),
            nextId = o.optLong("nextId", 1L),
            events = (o.optJSONArray("events") ?: JSONArray()).objects().map {
                Ev(it.optLong("ts"), it.optString("t"), it.optString("p"), it.optString("v"))
            },
            batchQueue = notifsFrom(o.optJSONArray("batchQueue")),
            batchInbox = notifsFrom(o.optJSONArray("batchInbox")),
            lastBatchDelivery = o.optLong("lastBatchDelivery", 0L),
        )
    }
}
