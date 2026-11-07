package com.enoli.odak.ui.stop

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enoli.odak.data.Mood
import com.enoli.odak.data.Repo
import com.enoli.odak.data.Scheduler
import com.enoli.odak.service.GateState
import com.enoli.odak.ui.Cheer
import com.enoli.odak.ui.Eyebrow
import com.enoli.odak.ui.MainActivity
import com.enoli.odak.ui.Muted
import com.enoli.odak.ui.Panel
import com.enoli.odak.ui.Perms
import com.enoli.odak.ui.cards.CardSession
import com.enoli.odak.ui.theme.OdakColors
import com.enoli.odak.ui.theme.OdakTheme
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * İki modlu ekran:
 *  - [MODE_GATE]: durdurma ekranı. Nefes → "Şu an ne hissediyorsun?" → büyük "Vazgeç → 3 kart", küçük "Yine de aç".
 *  - [MODE_SCROLL]: kesintisiz kaydırma eşiği aşılınca çıkan kapatılabilir ara ekran.
 * Uyarı metni ve sert kilit yok.
 */
class StopActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_LABEL = "label"
        const val EXTRA_MODE = "mode"
        const val MODE_GATE = "gate"
        const val MODE_SCROLL = "scroll"
    }

    private var visit by mutableIntStateOf(0)
    private var pkg by mutableStateOf("")
    private var label by mutableStateOf("")
    private var mode by mutableStateOf(MODE_GATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        Repo.init(applicationContext)
        readIntent(intent)
        setContent {
            OdakTheme {
                key(visit) {
                    GateFlow(
                        pkg = pkg,
                        appLabel = label,
                        scrollMode = mode == MODE_SCROLL,
                        actions = GateActions(
                            openAnyway = ::openAnyway,
                            fiveMore = ::fiveMore,
                            goHome = ::goHome,
                            openOdak = ::openOdak,
                            takeBreak = ::takeBreak,
                        ),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        visit++ // yeni bir dürtü: akışı baştan başlat
    }

    private fun readIntent(i: Intent?) {
        pkg = i?.getStringExtra(EXTRA_PKG) ?: ""
        label = i?.getStringExtra(EXTRA_LABEL) ?: ""
        mode = i?.getStringExtra(EXTRA_MODE) ?: MODE_GATE
    }

    private fun openAnyway() {
        Repo.logOpen(pkg)
        if (pkg.isNotEmpty()) GateState.grant(pkg)
        finish()
    }

    /** Kaydırma ara ekranı: 5 dakika daha. */
    private fun fiveMore() {
        Repo.logEvent("scroll_more", pkg)
        if (pkg.isNotEmpty()) GateState.snoozeScroll(pkg, Repo.effectiveScrollMin() * 60_000L)
        finish()
    }

    private fun takeBreak() {
        Repo.takeEarnedBreak()
        finish()
    }

    private fun goHome() {
        Perms.goHome(this)
        finish()
    }

    private fun openOdak() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

private class GateActions(
    val openAnyway: () -> Unit,
    val fiveMore: () -> Unit,
    val goHome: () -> Unit,
    val openOdak: () -> Unit,
    val takeBreak: () -> Unit,
)

private enum class Phase { Breath, Choice, Cards, Rest, FirstStep, Done }

private fun isNight(): Boolean = LocalTime.now().hour.let { it >= 23 || it < 6 }

@Composable
private fun GateFlow(pkg: String, appLabel: String, scrollMode: Boolean, actions: GateActions) {
    val data by Repo.data.collectAsState()
    val s = data.settings
    var phase by remember { mutableStateOf(if (scrollMode) Phase.Choice else Phase.Breath) }
    var mood by remember { mutableStateOf<String?>(null) }
    var solved by remember { mutableIntStateOf(0) }
    var firstStep by remember { mutableStateOf("") }
    val sessionCards = remember { Scheduler.pick(Repo.current.cards, s.gateCards) }
    val cardCount = if (sessionCards.isEmpty()) s.gateCards else sessionCards.size

    fun quit() {
        if (scrollMode) Repo.logEvent("scroll_stop", pkg) else Repo.logQuit(pkg)
    }

    fun startCards() {
        quit()
        phase = if (sessionCards.isEmpty()) Phase.Done else Phase.Cards
    }

    // Geri tuşu: kart bitmeden çıkarsa da vazgeçmiş sayılır, ana ekrana döner.
    BackHandler {
        if (phase == Phase.Breath || phase == Phase.Choice) quit()
        actions.goHome()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(OdakColors.Paper)
            .safeDrawingPadding(),
    ) {
        when (phase) {
            Phase.Breath -> Breath(seconds = Repo.effectiveWait(data)) { phase = Phase.Choice }
            Phase.Choice -> if (scrollMode) {
                ScrollChoice(
                    appLabel = appLabel,
                    minutes = Repo.effectiveScrollMin(data),
                    cardCount = cardCount,
                    onStop = { startCards() },
                    onFiveMore = actions.fiveMore,
                )
            } else {
                Choice(
                    appLabel = appLabel,
                    urgeIf = s.urgeIf,
                    urgeThen = s.urgeThen,
                    cardCount = cardCount,
                    mood = mood,
                    onMood = { m ->
                        if (mood == null) Repo.logEvent("mood", pkg, m)
                        mood = m
                    },
                    breakReady = Repo.earnedBreakReady(data),
                    onCards = { startCards() },
                    onRest = {
                        quit()
                        phase = Phase.Rest
                    },
                    onEscape = { phase = Phase.FirstStep },
                    onOpenAnyway = actions.openAnyway,
                    onTakeBreak = actions.takeBreak,
                )
            }
            Phase.Cards -> CardSession(
                cards = sessionCards,
                deckName = { id -> data.decks.firstOrNull { it.id == id }?.name ?: "" },
                onFinish = { n ->
                    solved = n
                    Repo.logSession(n)
                    phase = Phase.Done
                },
            )
            Phase.Rest -> Rest(night = isNight(), onDone = actions.goHome)
            Phase.FirstStep -> FirstStep(
                plan = s.studyThen,
                lastTask = s.lastTask,
                lastStep = s.lastFirstStep,
                onStart = { task, step ->
                    Repo.updateSettings { it.copy(lastTask = task.trim(), lastFirstStep = step.trim()) }
                    quit()
                    firstStep = step.trim()
                    phase = Phase.Done
                },
                onCards = { startCards() },
            )
            Phase.Done -> Done(
                solved = solved,
                noCards = sessionCards.isEmpty(),
                firstStep = firstStep,
                quitsToday = data.today().quits,
                cardsToday = data.today().cards,
                onGoHome = actions.goHome,
                onOpenOdak = actions.openOdak,
            )
        }
    }
}

@Composable
private fun BreathingRings(size: Int = 240) {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size.dp)) {
        Box(Modifier.size(size.dp).scale(scale).background(OdakColors.GreenSoft, CircleShape))
        Box(Modifier.size((size * 0.38f).dp).scale(scale).background(OdakColors.Green, CircleShape))
    }
}

@Composable
private fun Breath(seconds: Int, onDone: () -> Unit) {
    var left by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
        onDone()
    }
    val inhale = ((seconds - left) / 2.5f).toInt() % 2 == 0
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BreathingRings()
        Spacer(Modifier.height(36.dp))
        Text(if (inhale) "Nefes al…" else "Nefes ver…", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Muted("$left")
    }
}

@Composable
private fun MoodChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) OdakColors.Green else OdakColors.Card,
        border = BorderStroke(1.dp, if (selected) OdakColors.Green else OdakColors.Line),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            color = if (selected) OdakColors.Paper else OdakColors.Ink,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Choice(
    appLabel: String,
    urgeIf: String,
    urgeThen: String,
    cardCount: Int,
    mood: String?,
    onMood: (String) -> Unit,
    breakReady: Boolean,
    onCards: () -> Unit,
    onRest: () -> Unit,
    onEscape: () -> Unit,
    onOpenAnyway: () -> Unit,
    onTakeBreak: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(12.dp))
        // 2 · Can sıkıntısı sorusu: tek dokunuş, isteğe bağlı
        Text("Şu an ne hissediyorsun?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Mood.all.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    MoodChip(Mood.label(m), mood == m, { onMood(m) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(14.dp))

        when (mood) {
            Mood.TIRED -> {
                Panel(tint = OdakColors.GreenSoft) {
                    Text(
                        if (isNight()) "Saat geç. Uyumak bugün çalıştığını da pekiştirir." else "Yorgunken kart da zor gelir. 5 dakika gözlerini dinlendir.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(14.dp))
                BigButton(if (isNight()) "Telefonu bırak → uykuya hazırlan" else "5 dk dinlen", onRest)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onCards, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Yine de $cardCount kart çöz")
                }
            }
            Mood.ESCAPE -> {
                Panel(tint = OdakColors.AmberSoft) {
                    Eyebrow("Az önce ne yapacaktın?", OdakColors.Amber)
                    Spacer(Modifier.height(4.dp))
                    Text("Ertelediğin işin yalnızca ilk küçük adımını seçelim.", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(14.dp))
                BigButton("İlk küçük adımı seç", onEscape)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onCards, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("$cardCount kart çöz")
                }
            }
            else -> {
                if (urgeIf.isNotBlank() && urgeThen.isNotBlank()) {
                    Panel {
                        Eyebrow("Kendi planın")
                        Spacer(Modifier.height(8.dp))
                        Text("Eğer $urgeIf", style = MaterialTheme.typography.bodyLarge)
                        Text("→ $urgeThen", style = MaterialTheme.typography.titleMedium, color = OdakColors.Green)
                    }
                    Spacer(Modifier.height(18.dp))
                }
                BigButton("Vazgeç → $cardCount kart", onCards)
            }
        }

        if (breakReady) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onTakeBreak, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("25 dk'yı bitirdin: 10 dk molanı al")
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onOpenAnyway, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (appLabel.isBlank()) "Yine de aç" else "Yine de aç · $appLabel", color = OdakColors.Muted)
        }
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OdakColors.Green),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
}

/** 1 · Kesintisiz kaydırma ara ekranı. Kapatılabilir; sert kilit yok. */
@Composable
private fun ScrollChoice(appLabel: String, minutes: Int, cardCount: Int, onStop: () -> Unit, onFiveMore: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BreathingRings(size = 140)
        Spacer(Modifier.height(28.dp))
        Text(
            "$minutes dakikadır kesintisiz kaydırıyorsun.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Muted("Burada bir durak koyalım mı?")
        Spacer(Modifier.height(28.dp))
        BigButton("Bırak → $cardCount kart", onStop)
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onFiveMore) { Text("5 dk daha", color = OdakColors.Muted) }
    }
}

@Composable
private fun Rest(night: Boolean, onDone: () -> Unit) {
    val total = 5 * 60
    var left by remember { mutableIntStateOf(total) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BreathingRings(size = 200)
        Spacer(Modifier.height(28.dp))
        Text(
            if (night) "İyi uykular." else "%d:%02d".format(left / 60, left % 60),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (night) {
                "Telefonu şarja koy, rahatsız etme modunu aç. Bugün öğrendiklerin uykuda yerleşir."
            } else {
                "Telefonu bırak, uzağa bak, omuzlarını gevşet. Süre bitince istersen kaldığın yerden devam."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (left == 0 || night) "Ana ekrana dön" else "Bitir")
        }
    }
}

/** "Kaçıyorum" cevabı: ertelenen işin ilk küçük adımı. */
@Composable
private fun FirstStep(
    plan: String,
    lastTask: String,
    lastStep: String,
    onStart: (task: String, step: String) -> Unit,
    onCards: () -> Unit,
) {
    var task by remember { mutableStateOf(lastTask.ifBlank { plan }) }
    var step by remember { mutableStateOf(lastStep) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Az önce ne yapacaktın?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Muted("Kaçmak istemek normal. İşin tamamını değil, yalnızca ilk 2 dakikasını düşün.")
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = task, onValueChange = { task = it },
            label = { Text("Ertelediğin iş") }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = step, onValueChange = { step = it },
            label = { Text("İlk küçük adım") },
            placeholder = { Text("ör. PDF'i açıp ilk başlığı okumak") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        BigButton("Bu adımla başla") { onStart(task, step.ifBlank { task }) }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCards, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Onun yerine kart çöz") }
    }
}

@Composable
private fun Done(
    solved: Int,
    noCards: Boolean,
    firstStep: String,
    quitsToday: Int,
    cardsToday: Int,
    onGoHome: () -> Unit,
    onOpenOdak: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                firstStep.isNotBlank() -> "Sadece 2 dakika."
                solved == 0 -> Cheer.quitMsg(quitsToday)
                else -> "Tik aldın."
            },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                firstStep.isNotBlank() -> "İlk adım: $firstStep\nBaşlamak en zor kısım; gerisi genelde kendiliğinden gelir."
                noCards -> "Henüz kartın yok. Odak'ta bir deste ekleyince burada 3 kart çözeceksin."
                solved == 0 -> "Bugün $quitsToday kez vazgeçtin. Kartlar seni bekliyor, acele yok."
                else -> "$solved kart çözdün. Bugün $quitsToday kez vazgeçtin, toplam $cardsToday kart."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Ana ekrana dön") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenOdak) { Text(if (noCards && firstStep.isBlank()) "Odak'ı aç, kart ekle" else "Odak'ı aç") }
    }
}
