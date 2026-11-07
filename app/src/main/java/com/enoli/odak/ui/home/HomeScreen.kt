package com.enoli.odak.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enoli.odak.data.Day
import com.enoli.odak.data.Repo
import com.enoli.odak.data.Scheduler
import com.enoli.odak.ui.Eyebrow
import com.enoli.odak.ui.LinkButton
import com.enoli.odak.ui.Muted
import com.enoli.odak.ui.Panel
import com.enoli.odak.ui.Perms
import com.enoli.odak.ui.UiEvents
import com.enoli.odak.ui.theme.OdakColors
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onStudy: (Int) -> Unit,
    onSrbai: () -> Unit,
    onDecks: () -> Unit,
) {
    val context = LocalContext.current
    val data by Repo.data.collectAsState()
    val tick by UiEvents.resumed.collectAsState()
    val accOn = remember(tick) { Perms.accessibilityOn(context) }
    val s = data.settings
    val today = data.today()

    // Odak bloğu ve mola için dakikada bir yenile
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }

    // Bu haftanın vazgeçme oranı (MVP başarı ölçütü)
    val week = Day.weekStart(Day.today())
    val weekLogs = data.days.values.filter { it.day >= week }
    val weekTriggers = weekLogs.sumOf { it.triggers }
    val weekQuits = weekLogs.sumOf { it.quits }
    val due = Scheduler.dueCount(data.cards)
    val daysIn = Day.today() - s.startDay
    val gap = Repo.daysSinceLastTick(data)
    val srbaiDone = data.srbai.any { it.weekStart == week }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Bugün", style = MaterialTheme.typography.headlineMedium)

        // İzin kapalıysa: kilitlemeden, şeffafça sor.
        if (!accOn && !s.accessibilityOptOut) {
            Panel(tint = OdakColors.AmberSoft) {
                Eyebrow("Durdurma ekranı kapalı", OdakColors.Amber)
                Spacer(Modifier.height(6.dp))
                Text("Erişilebilirlik izni kapalı görünüyor. Devam etmek ister misin?")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { Perms.openAccessibility(context) }) { Text("Evet, aç") }
                    OutlinedButton(onClick = { Repo.updateSettings { it.copy(accessibilityOptOut = true) } }) {
                        Text("Şimdilik kapalı kalsın")
                    }
                }
            }
        }

        // Suçlama yok: süre değil, vazgeçiş ve kart sayılır.
        Panel {
            Eyebrow("Bugünün özeti")
            Spacer(Modifier.height(8.dp))
            Text(
                if (today.quits == 0 && today.cards == 0) "Bugün henüz bir şey yok. Sorun değil, gün bitmedi."
                else "Bugün ${today.quits} kez vazgeçtin, ${today.cards} kart çözdün.",
                style = MaterialTheme.typography.titleLarge,
            )
            if (weekTriggers > 0) {
                Spacer(Modifier.height(6.dp))
                Muted("Bu haftaki vazgeçme oranın: %${weekQuits * 100 / weekTriggers} (${weekQuits}/${weekTriggers})")
            }
        }

        // Ana eylem
        Panel(tint = OdakColors.GreenSoft) {
            Eyebrow("Kartlar")
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    data.cards.isEmpty() -> "Henüz kartın yok."
                    due > 0 -> "Zamanı gelen $due kart var."
                    else -> "Zamanı gelen kart yok. İstersen önden tekrar edebilirsin."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))
            if (data.cards.isEmpty()) {
                Button(onClick = onDecks) { Text("Deste ekle") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onStudy(3) }, modifier = Modifier.weight(1f)) { Text("3 kart") }
                    OutlinedButton(onClick = { onStudy(10) }, modifier = Modifier.weight(1f)) { Text("10 kart") }
                }
            }
        }

        // Planlar
        if (s.studyIf.isNotBlank() || s.urgeIf.isNotBlank() || s.goal.isNotBlank()) {
            Panel {
                if (s.goal.isNotBlank()) {
                    Eyebrow("Hedefin")
                    Text(s.goal, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                }
                if (s.studyIf.isNotBlank()) {
                    Eyebrow("Çalışma planın · %02d:%02d".format(s.planHour, s.planMinute))
                    Text("Eğer ${s.studyIf} → ${s.studyThen}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                }
                if (s.urgeIf.isNotBlank()) {
                    Eyebrow("Dürtü planın")
                    Text("Eğer ${s.urgeIf} → ${s.urgeThen}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // Hak edilmiş mola
        if (s.earnedBreak) FocusPanel(data.focusStart, data.breakUntil, now)

        // Pazar: 4 soru
        if (Day.isSunday() && !srbaiDone) {
            Panel(tint = OdakColors.AmberSoft) {
                Eyebrow("Haftalık 4 soru", OdakColors.Amber)
                Spacer(Modifier.height(6.dp))
                Text("Çalışma alışkanlığın ne kadar otomatikleşti? 30 saniye.")
                LinkButton("Cevapla", onSrbai)
            }
        }

        // Nazik notlar
        if (gap != null && gap >= 2 && !today.ticked) {
            Muted("Ara biraz uzadı. Kaldığın yerden devam: plan saatinde 3 kart bile yeter.")
        } else if (daysIn < 21) {
            Muted("İlk haftalar zor; alışkanlık ortalama 2–3 ayda otomatikleşiyor. Doğru yoldasın.")
        }
    }
}

@Composable
private fun FocusPanel(focusStart: Long, breakUntil: Long, tickNow: Long) {
    val now = System.currentTimeMillis().coerceAtLeast(tickNow)
    Panel {
        Eyebrow("25 dakikalık blok")
        Spacer(Modifier.height(6.dp))
        when {
            now < breakUntil -> {
                val left = ((breakUntil - now) / 60_000) + 1
                Text("Moladasın. $left dk kaldı; durdurma ekranı bu sürede çıkmaz.")
            }
            focusStart == 0L -> {
                Muted("25 dakika çalışırsan, istersen 10 dakikalık molanı durdurma ekranından kendin açabilirsin.")
                Spacer(Modifier.height(10.dp))
                Button(onClick = { Repo.startFocus() }) { Text("Bloğu başlat") }
            }
            now - focusStart < Repo.FOCUS_MS -> {
                val left = ((Repo.FOCUS_MS - (now - focusStart)) / 60_000) + 1
                Text("Çalışıyorsun. $left dk kaldı.", style = MaterialTheme.typography.titleMedium)
                LinkButton("Bloğu iptal et") { Repo.stopFocus() }
            }
            else -> {
                Text("Bloğu bitirdin. 10 dakikalık molan hazır.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { Repo.takeEarnedBreak() }) { Text("Molayı başlat") }
                    OutlinedButton(onClick = { Repo.startFocus() }) { Text("Bir blok daha") }
                }
            }
        }
    }
}
