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

    private fun bumpToday(block: (DayLog) -> DayLog) = update { d ->
        val today = Day.today()
        d.copy(days = d.days + (today to block(d.day(today))))
    }

    fun logTrigger() = bumpToday { it.copy(triggers = it.triggers + 1) }
    fun logQuit() = bumpToday { it.copy(quits = it.quits + 1) }
    fun logOpen() = bumpToday { it.copy(opens = it.opens + 1) }

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
        d.copy(cards = d.cards.map { if (it.id == cardId) Scheduler.review(it, known) else it })
    }

    // ---------- Ritim ----------

    fun saveSrbai(weekStart: Long, scores: List<Int>) = update { d ->
        d.copy(srbai = d.srbai.filterNot { it.weekStart == weekStart } + Srbai(weekStart, scores))
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
                            .put("cards", it.cards).put("ticked", it.ticked),
                    )
                }
            })
            .put("srbai", JSONArray().apply {
                d.srbai.forEach {
                    put(JSONObject().put("weekStart", it.weekStart).put("scores", JSONArray(it.scores)))
                }
            })
            .put("focusStart", d.focusStart)
            .put("breakUntil", d.breakUntil)
            .put("nextId", d.nextId)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

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
        )
    }
}
