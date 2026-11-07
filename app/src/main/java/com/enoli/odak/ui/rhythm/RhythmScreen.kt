package com.enoli.odak.ui.rhythm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enoli.odak.data.Day
import com.enoli.odak.data.Repo
import com.enoli.odak.ui.Eyebrow
import com.enoli.odak.ui.LinkButton
import com.enoli.odak.ui.Muted
import com.enoli.odak.ui.Panel
import com.enoli.odak.ui.theme.OdakColors

private val dayNames = listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz")

/** 10 haftalık tik takvimi. Seri kırılsa da hiçbir şey sıfırlanmaz. */
@Composable
fun RhythmScreen(onSrbai: () -> Unit) {
    val data by Repo.data.collectAsState()
    val today = Day.today()
    val firstWeek = Day.weekStart(data.settings.startDay)
    val weeksElapsed = ((Day.weekStart(today) - firstWeek) / 7).toInt()
    val windowStart = firstWeek + maxOf(0, weeksElapsed - 9) * 7L
    val firstWeekNo = maxOf(0, weeksElapsed - 9) + 1

    val ticks = data.days.values.count { it.ticked }
    val thisWeek = Day.weekStart(today)
    val ticksThisWeek = data.days.values.count { it.ticked && it.day >= thisWeek }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ritim", style = MaterialTheme.typography.headlineMedium)
        Muted("Plan saatinde çalıştığın her gün bir tik. Kart oturumu bitirince tik kendiliğinden gelir; bir güne dokunarak elle de işaretleyebilirsin.")

        Panel {
            Row {
                Spacer(Modifier.width(34.dp))
                dayNames.forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = OdakColors.Muted)
                }
            }
            Spacer(Modifier.height(6.dp))
            for (w in 0 until 10) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "H${firstWeekNo + w}",
                        Modifier.width(34.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = OdakColors.Muted,
                    )
                    for (d in 0 until 7) {
                        val day = windowStart + w * 7L + d
                        DayCell(
                            ticked = data.day(day).ticked,
                            isToday = day == today,
                            enabled = day <= today && day >= data.settings.startDay,
                            onClick = { Repo.toggleTick(day) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Muted("Toplam $ticks tik · bu hafta $ticksThisWeek/7. Hedef: haftada en az 5 gün.")
        }

        Panel {
            Eyebrow("Otomatiklik (haftalık 4 soru)")
            Spacer(Modifier.height(6.dp))
            val history = data.srbai.sortedBy { it.weekStart }.takeLast(10)
            if (history.isEmpty()) {
                Muted("Pazar günleri 4 kısa soru gelir. Puanın yükseldikçe çalışmak daha az çaba ister.")
            } else {
                Row(
                    Modifier.fillMaxWidth().height(110.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    history.forEach { r ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%.1f".format(r.average), style = MaterialTheme.typography.labelMedium)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((r.average / 7.0 * 80).dp)
                                    .background(OdakColors.Green, RoundedCornerShape(4.dp)),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Muted("1 = hiç otomatik değil, 7 = tamamen otomatik.")
            }
            LinkButton(if (data.srbai.any { it.weekStart == thisWeek }) "Bu haftanın cevabını güncelle" else "Bu haftanın 4 sorusu", onSrbai)
        }
    }
}

@Composable
private fun DayCell(ticked: Boolean, isToday: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.padding(3.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
        val base = Modifier
            .fillMaxSize()
            .background(
                when {
                    ticked -> OdakColors.Green
                    enabled -> OdakColors.Paper
                    else -> OdakColors.Line.copy(alpha = 0.35f)
                },
                RoundedCornerShape(7.dp),
            )
        val withBorder = if (isToday) base.border(BorderStroke(2.dp, OdakColors.Amber), RoundedCornerShape(7.dp)) else base
        Box(
            if (enabled) withBorder.clickable(onClick = onClick) else withBorder,
            contentAlignment = Alignment.Center,
        ) {
            if (ticked) Text("✓", color = OdakColors.Paper, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val srbaiItems = listOf(
    "otomatik olarak yaptığım bir şey.",
    "bilinçli olarak hatırlamak zorunda kalmadan yaptığım bir şey.",
    "düşünmeden yaptığım bir şey.",
    "farkına varmadan başlamış olduğum bir şey.",
)

/** SRBAI: 4 madde, 1–7 ölçek. */
@Composable
fun SrbaiScreen(onDone: () -> Unit) {
    val data by Repo.data.collectAsState()
    val week = Day.weekStart(Day.today())
    val existing = data.srbai.firstOrNull { it.weekStart == week }?.scores
    val answers = remember { mutableStateListOf<Int>().apply { addAll(existing ?: List(4) { 0 }) } }
    val behavior = data.settings.studyThen.ifBlank { "Çalışma planımı uygulamak" }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Haftalık 4 soru", style = MaterialTheme.typography.headlineMedium)
        Muted("Doğru cevap yok. Bu hafta nasıl hissettirdiyse onu seç.")
        Panel(tint = OdakColors.GreenSoft) {
            Eyebrow("Davranış")
            Text("“$behavior”…", style = MaterialTheme.typography.titleMedium)
        }
        srbaiItems.forEachIndexed { i, text ->
            Panel {
                Text("…$text", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (v in 1..7) {
                        val sel = answers[i] == v
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(if (sel) OdakColors.Green else OdakColors.Paper, RoundedCornerShape(50))
                                .border(BorderStroke(1.dp, if (sel) OdakColors.Green else OdakColors.Line), RoundedCornerShape(50))
                                .clickable { answers[i] = v },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$v", color = if (sel) OdakColors.Paper else OdakColors.Ink, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Row {
                    Muted("Hiç katılmıyorum", Modifier.weight(1f))
                    Muted("Tamamen katılıyorum")
                }
            }
        }
        Button(
            enabled = answers.all { it in 1..7 },
            onClick = {
                Repo.saveSrbai(week, answers.toList())
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Kaydet") }
        LinkButton("Şimdi değil", onDone)
    }
}
