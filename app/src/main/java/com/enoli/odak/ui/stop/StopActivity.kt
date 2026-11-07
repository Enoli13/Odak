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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/**
 * Durdurma ekranı. Önce birkaç saniyelik nefes, sonra iki seçenek:
 * büyük "Vazgeç → 3 kart" ve küçük "Yine de aç". Uyarı metni yok.
 */
class StopActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_LABEL = "label"
    }

    private var visit by mutableIntStateOf(0)
    private var pkg by mutableStateOf("")
    private var label by mutableStateOf("")

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
                        appLabel = label,
                        onOpenAnyway = ::openAnyway,
                        onGoHome = ::goHome,
                        onOpenOdak = ::openOdak,
                        onTakeBreak = ::takeBreak,
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
    }

    private fun openAnyway() {
        Repo.logOpen()
        if (pkg.isNotEmpty()) GateState.grant(pkg)
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

private enum class Phase { Breath, Choice, Cards, Done }

@Composable
private fun GateFlow(
    appLabel: String,
    onOpenAnyway: () -> Unit,
    onGoHome: () -> Unit,
    onOpenOdak: () -> Unit,
    onTakeBreak: () -> Unit,
) {
    val data by Repo.data.collectAsState()
    val s = data.settings
    var phase by remember { mutableStateOf(Phase.Breath) }
    var solved by remember { mutableIntStateOf(0) }
    val sessionCards = remember { Scheduler.pick(Repo.current.cards, s.gateCards) }

    // Geri tuşu: kart bitmeden çıkarsa da vazgeçmiş sayılır, ana ekrana döner.
    BackHandler {
        if (phase == Phase.Breath || phase == Phase.Choice) Repo.logQuit()
        onGoHome()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(OdakColors.Paper)
            .safeDrawingPadding(),
    ) {
        when (phase) {
            Phase.Breath -> Breath(seconds = s.waitSeconds) { phase = Phase.Choice }
            Phase.Choice -> Choice(
                appLabel = appLabel,
                urgeIf = s.urgeIf,
                urgeThen = s.urgeThen,
                cardCount = if (sessionCards.isEmpty()) s.gateCards else sessionCards.size,
                breakReady = Repo.earnedBreakReady(data),
                onQuit = {
                    Repo.logQuit()
                    phase = if (sessionCards.isEmpty()) Phase.Done else Phase.Cards
                },
                onOpenAnyway = onOpenAnyway,
                onTakeBreak = onTakeBreak,
            )
            Phase.Cards -> CardSession(
                cards = sessionCards,
                deckName = { id -> data.decks.firstOrNull { it.id == id }?.name ?: "" },
                onFinish = { n ->
                    solved = n
                    Repo.logSession(n)
                    phase = Phase.Done
                },
            )
            Phase.Done -> Done(
                solved = solved,
                noCards = sessionCards.isEmpty(),
                quitsToday = data.today().quits,
                cardsToday = data.today().cards,
                onGoHome = onGoHome,
                onOpenOdak = onOpenOdak,
            )
        }
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
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val inhale = ((seconds - left) / 2.5f).toInt() % 2 == 0
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            Box(
                Modifier
                    .size(240.dp)
                    .scale(scale)
                    .background(OdakColors.GreenSoft, CircleShape),
            )
            Box(
                Modifier
                    .size(90.dp)
                    .scale(scale)
                    .background(OdakColors.Green, CircleShape),
            )
        }
        Spacer(Modifier.height(36.dp))
        Text(if (inhale) "Nefes al…" else "Nefes ver…", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Muted("$left")
    }
}

@Composable
private fun Choice(
    appLabel: String,
    urgeIf: String,
    urgeThen: String,
    cardCount: Int,
    breakReady: Boolean,
    onQuit: () -> Unit,
    onOpenAnyway: () -> Unit,
    onTakeBreak: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (urgeIf.isNotBlank() && urgeThen.isNotBlank()) {
            Panel {
                Eyebrow("Kendi planın")
                Spacer(Modifier.height(8.dp))
                Text("Eğer $urgeIf", style = MaterialTheme.typography.bodyLarge)
                Text("→ $urgeThen", style = MaterialTheme.typography.titleMedium, color = OdakColors.Green)
            }
            Spacer(Modifier.height(28.dp))
        }
        Button(
            onClick = onQuit,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OdakColors.Green),
        ) {
            Text("Vazgeç → $cardCount kart", style = MaterialTheme.typography.titleLarge)
        }
        if (breakReady) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onTakeBreak, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("25 dk'yı bitirdin: 10 dk molanı al")
            }
        }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onOpenAnyway, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(
                if (appLabel.isBlank()) "Yine de aç" else "Yine de aç · $appLabel",
                color = OdakColors.Muted,
            )
        }
    }
}

@Composable
private fun Done(
    solved: Int,
    noCards: Boolean,
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
            if (solved == 0) Cheer.quitMsg(quitsToday) else "Tik aldın.",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
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
        TextButton(onClick = onOpenOdak) { Text(if (noCards) "Odak'ı aç, kart ekle" else "Odak'ı aç") }
    }
}
