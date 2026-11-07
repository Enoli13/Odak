package com.enoli.odak.ui.report

import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enoli.odak.data.AppData
import com.enoli.odak.data.Day
import com.enoli.odak.data.Mood
import com.enoli.odak.data.Report
import com.enoli.odak.data.Repo
import com.enoli.odak.ui.Eyebrow
import com.enoli.odak.ui.LinkButton
import com.enoli.odak.ui.Muted
import com.enoli.odak.ui.Panel
import com.enoli.odak.ui.Perms
import com.enoli.odak.ui.UiEvents
import com.enoli.odak.ui.theme.OdakColors
import java.time.ZoneId

/** 6 · Kendi verisini toplama: 10 haftalık deney raporu + CSV. */
@Composable
fun ReportScreen() {
    val context = LocalContext.current
    val data by Repo.data.collectAsState()
    val weeks = remember(data) { Report.weeks(data) }
    val baseline = remember(data) { Report.baselinePerDay(data) }
    val allMoods = remember(data) { Report.moods(data) }
    val weekMoods = remember(data) { Report.moods(data, sinceDay = Day.weekStart(Day.today())) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) exportCsv(context, uri, data)
    }

    val active = weeks.filter { it.triggers > 0 }
    val totalTriggers = active.sumOf { it.triggers }
    val totalQuits = active.sumOf { it.quits }
    val overallQuit = if (totalTriggers == 0) null else totalQuits * 100 / totalTriggers
    val srbaiList = weeks.mapNotNull { it.srbai }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Rapor", style = MaterialTheme.typography.headlineMedium)
        Muted("10 haftalık kendi deneyin. Veriler yalnızca bu telefonda.")

        // Hedefler
        Panel {
            Eyebrow("Hedef ölçütler")
            Spacer(Modifier.height(8.dp))
            Goal(
                "Vazgeçme oranı en az %33",
                overallQuit?.let { "Şu an %$it" } ?: "Henüz veri yok",
                overallQuit?.let { it >= 33 },
            )
            Spacer(Modifier.height(8.dp))
            Goal(
                "Otomatiklik (SRBAI) yükseliyor",
                if (srbaiList.size >= 2) "%.1f → %.1f".format(srbaiList.first(), srbaiList.last()) else "En az 2 haftalık ölçüm gerekli",
                if (srbaiList.size >= 2) srbaiList.last() > srbaiList.first() else null,
            )
            Spacer(Modifier.height(8.dp))
            Muted("Karşılaştırma için: one sec çalışmasında denemelerin %36'sı vazgeçmeyle bitti.")
        }

        // Başlangıç çizgisi
        Panel {
            Eyebrow("Başlangıç çizgisi (ilk 7 gün)")
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !data.settings.baselineWeek -> "Ölçüm haftası atlandı."
                    baseline == null -> "Henüz ölçüm yok."
                    else -> "Müdahale yokken günde ortalama %.1f dürtü.".format(baseline)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            val later = weeks.filter { it.triggers > 0 }
            if (baseline != null && later.isNotEmpty()) {
                val days = later.size * 7
                Muted("Sonrasında günde ortalama %.1f durdurma ekranı.".format(later.sumOf { it.triggers }.toDouble() / days))
            }
        }

        // Haftalık tablo
        Panel {
            Eyebrow("Haftalar")
            Spacer(Modifier.height(8.dp))
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                TableRow(listOf("Hf", "Dürtü", "Vazgeçme", "Kart", "Doğru", "Tik", "Kaydırma", "SRBAI"), header = true)
                weeks.takeLast(12).forEach { w ->
                    TableRow(
                        listOf(
                            "${w.no}",
                            "${w.triggers + w.baseline}",
                            w.quitRate?.let { "%$it" } ?: "–",
                            "${w.cards}",
                            w.correctRate?.let { "%$it" } ?: "–",
                            "${w.ticks}/7",
                            if (w.scrollGates == 0) "–" else "${w.scrollStops}/${w.scrollGates}",
                            w.srbai?.let { "%.1f".format(it) } ?: "–",
                        ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Muted("Kaydırma: ara ekranda bırakma / gösterilen ara ekran.")
        }

        // Tetikleyiciler
        Panel {
            Eyebrow("Tetikleyiciler")
            Spacer(Modifier.height(6.dp))
            Text(
                weekMoods.topContext?.let { "Bu hafta en sık: $it" } ?: "Bu hafta henüz cevap yok.",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            val max = (allMoods.counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
            Mood.all.forEach { m ->
                val c = allMoods.counts[m] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(Mood.label(m), Modifier.width(150.dp), style = MaterialTheme.typography.bodyMedium)
                    Box(
                        Modifier
                            .height(14.dp)
                            .width((140f * c / max).coerceAtLeast(2f).dp)
                            .background(OdakColors.Green, RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("$c", style = MaterialTheme.typography.labelMedium)
                }
            }
            allMoods.topContext?.let {
                Spacer(Modifier.height(6.dp))
                Muted("Tüm zamanlar: $it")
            }
        }

        ScreenTimePanel(data)

        Panel {
            Eyebrow("Dışa aktar")
            Spacer(Modifier.height(6.dp))
            Muted("Günlük satırlarla CSV: dürtü, vazgeçme, kart, doğru oranı, his cevapları, tik, SRBAI. Hiçbir sunucuya gönderilmez; dosyayı nereye kaydedeceğini sen seçersin.")
            Spacer(Modifier.height(10.dp))
            Button(onClick = { exporter.launch("odak-rapor-${Day.date(Day.today())}.csv") }) { Text("CSV olarak kaydet") }
        }
    }
}

@Composable
private fun Goal(title: String, value: String, ok: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (ok) {
                true -> "✓"
                false -> "…"
                null -> "·"
            },
            color = if (ok == true) OdakColors.Green else OdakColors.Muted,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.width(28.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Muted(value)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    Row(Modifier.padding(vertical = 4.dp)) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                modifier = Modifier.width(if (i == 0) 34.dp else 72.dp),
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else null,
                color = if (header) OdakColors.Muted else OdakColors.Ink,
            )
        }
    }
}

/** İsteğe bağlı ekran süresi: yalnızca istenirse, ayrı bir bölümde. */
@Composable
private fun ScreenTimePanel(data: AppData) {
    val context = LocalContext.current
    val tick by UiEvents.resumed.collectAsState()
    var show by remember { mutableStateOf(false) }
    val granted = remember(tick) { Perms.usageOn(context) }
    Panel {
        Eyebrow("Ekran süresi (isteğe bağlı)")
        Spacer(Modifier.height(6.dp))
        if (!show) {
            Muted("Ana ekranda gösterilmez. Yalnızca merak edersen bak.")
            LinkButton("Göster") { show = true }
        } else if (!granted) {
            Muted("Hedef uygulamalardaki günlük süreyi görmek için “Kullanım erişimi” izni gerekiyor.")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { Perms.openUsage(context) }) { Text("Kullanım erişimini aç") }
            LinkButton("Gizle") { show = false }
        } else {
            ScreenTimeBars(data) { show = false }
        }
    }
}

@Composable
private fun ScreenTimeBars(data: AppData, onHide: () -> Unit) {
    val context = LocalContext.current
    val tick by UiEvents.resumed.collectAsState()
    Column {
        val pkgs = data.settings.targets.map { it.pkg }.toSet()
        val minutes = remember(tick, pkgs) { lastDaysMinutes(context, pkgs, 7) }
        val max = (minutes.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            minutes.forEach { (day, min) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$min", style = MaterialTheme.typography.labelMedium)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((80f * min / max).coerceAtLeast(2f).dp)
                            .background(OdakColors.Line, RoundedCornerShape(4.dp)),
                    )
                    Text(Day.date(day).dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, color = OdakColors.Muted)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Muted("Hedef uygulamalarda dakika, son 7 gün. Asıl ölçüt bu değil: vazgeçişler ve çözülen kartlar.")
        LinkButton("Gizle", onHide)
    }
}

private fun lastDaysMinutes(context: Context, pkgs: Set<String>, days: Int): List<Pair<Long, Long>> {
    val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
    val zone = ZoneId.systemDefault()
    val today = Day.today()
    return ((days - 1) downTo 0).map { back ->
        val day = today - back
        val from = Day.date(day).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = minOf(Day.date(day + 1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        val stats = try {
            usm.queryAndAggregateUsageStats(from, to)
        } catch (_: Exception) {
            emptyMap()
        }
        val ms = stats.filterKeys { it in pkgs }.values.sumOf { it.totalTimeInForeground }
        day to ms / 60_000
    }
}

private fun exportCsv(context: Context, uri: Uri, data: AppData) {
    val ok = try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            // Excel'in Türkçe karakterleri doğru açması için BOM
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            out.write(Report.csv(data).toByteArray(Charsets.UTF_8))
        } != null
    } catch (_: Exception) {
        false
    }
    Toast.makeText(context, if (ok) "Rapor kaydedildi" else "Kaydedilemedi", Toast.LENGTH_SHORT).show()
}
