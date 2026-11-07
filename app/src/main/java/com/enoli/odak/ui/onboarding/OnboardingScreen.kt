package com.enoli.odak.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enoli.odak.data.Day
import com.enoli.odak.data.Repo
import com.enoli.odak.data.TargetApp
import com.enoli.odak.ui.Eyebrow
import com.enoli.odak.ui.Muted
import com.enoli.odak.ui.Panel
import com.enoli.odak.ui.Perms
import com.enoli.odak.ui.Stepper
import com.enoli.odak.ui.UiEvents
import com.enoli.odak.ui.theme.OdakColors
import com.enoli.odak.work.Notifs
import com.enoli.odak.work.ReminderWorker

/**
 * Başlangıç sözleşmesi: hedef uygulamalar, tek küçük hedef ve iki eğer–o zaman planı.
 * Her plan iki kez yazılır; ikincisinde "durum → eylem" bağı vurgulanır.
 */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    var targets by remember { mutableStateOf<List<TargetApp>>(emptyList()) }
    var goal by remember { mutableStateOf("") }
    var studyIf by remember { mutableStateOf("") }
    var studyThen by remember { mutableStateOf("") }
    var studyIf2 by remember { mutableStateOf("") }
    var studyThen2 by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(20) }
    var minuteSlot by remember { mutableIntStateOf(0) } // 5 dakikalık dilimler
    var urgeIf by remember { mutableStateOf("Reels açma isteği gelirse") }
    var urgeThen by remember { mutableStateOf("önce 3 kart çözerim") }
    var urgeIf2 by remember { mutableStateOf("") }
    var urgeThen2 by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf(true) }
    var baseline by remember { mutableStateOf(true) }

    val lastStep = 7
    BackHandler(enabled = step > 0) { step-- }

    fun finish() {
        Repo.updateSettings {
            it.copy(
                onboarded = true,
                startDay = Day.today(),
                targets = targets,
                goal = goal.trim(),
                studyIf = studyIf2.trim(),
                studyThen = studyThen2.trim(),
                urgeIf = urgeIf2.trim(),
                urgeThen = urgeThen2.trim(),
                planHour = hour,
                planMinute = minuteSlot * 5,
                baselineWeek = baseline,
            )
        }
        if (seed) Repo.seedExampleDeck()
        ReminderWorker.schedule(context, replace = true)
    }

    val canNext = when (step) {
        1 -> targets.isNotEmpty()
        2 -> goal.isNotBlank()
        3 -> studyIf.isNotBlank() && studyThen.isNotBlank()
        4 -> studyIf2.isNotBlank() && studyThen2.isNotBlank()
        5 -> urgeIf.isNotBlank() && urgeThen.isNotBlank()
        6 -> urgeIf2.isNotBlank() && urgeThen2.isNotBlank()
        else -> true
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        if (step > 0) {
            LinearProgressIndicator(
                progress = { step / lastStep.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = OdakColors.Green,
                trackColor = OdakColors.GreenSoft,
            )
            Spacer(Modifier.height(18.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (step) {
                0 -> Welcome()
                1 -> {
                    Title("Hangi uygulamalarda bir an durmak istersin?", "Sen seçersin. İstediğin zaman değiştirebilirsin.")
                    TargetPicker(targets) { targets = it }
                }
                2 -> {
                    Title("Tek bir küçük hedef", "Küçük olsun ki her gün yapılabilsin.")
                    OutlinedTextField(
                        value = goal, onValueChange = { goal = it },
                        label = { Text("Hedefim") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Her gün 10 kart", "Her akşam 20 dk çalışma").forEach { s ->
                            AssistChip(onClick = { goal = s }, label = { Text(s) })
                        }
                    }
                }
                3 -> {
                    Title("Çalışma planın", "Ne zaman ve nerede çalışacağını bağla. Sabit saat ve yer, alışkanlığı hızlandırır.")
                    PlanFields(
                        ifValue = studyIf, onIf = { studyIf = it }, ifHint = "Akşam yemeğinden kalkınca",
                        thenValue = studyThen, onThen = { studyThen = it }, thenHint = "masaya oturup 10 kart çözerim",
                    )
                    Spacer(Modifier.height(20.dp))
                    Eyebrow("Plan saati")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Stepper(hour, { hour = it }, 0, 23) { "%02d".format(it) }
                        Spacer(Modifier.width(8.dp))
                        Text(":", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(8.dp))
                        Stepper(minuteSlot, { minuteSlot = it }, 0, 11) { "%02d".format(it * 5) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Muted("Bu saatte nazik bir hatırlatma gelir.")
                }
                4 -> {
                    Title("Şimdi bir kez daha yaz", "Bu sefer bağa odaklan: durum gelince, eylem otomatik gelsin.")
                    BondFields(
                        ifValue = studyIf2, onIf = { studyIf2 = it }, ifHint = studyIf,
                        thenValue = studyThen2, onThen = { studyThen2 = it }, thenHint = studyThen,
                    )
                }
                5 -> {
                    Title("Dürtü planın", "Scroll isteği geldiğinde ne yapacaksın? Bu plan durdurma ekranında sana gösterilir.")
                    PlanFields(
                        ifValue = urgeIf, onIf = { urgeIf = it }, ifHint = "Reels açma isteği gelirse",
                        thenValue = urgeThen, onThen = { urgeThen = it }, thenHint = "önce 3 kart çözerim",
                    )
                }
                6 -> {
                    Title("Bir kez daha, bağa odaklanarak", "İki kez yazmak planın uygulanma ihtimalini belirgin biçimde artırıyor.")
                    BondFields(
                        ifValue = urgeIf2, onIf = { urgeIf2 = it }, ifHint = urgeIf,
                        thenValue = urgeThen2, onThen = { urgeThen2 = it }, thenHint = urgeThen,
                    )
                }
                else -> PermissionsStep(seed = seed, onSeed = { seed = it }, baseline = baseline, onBaseline = { baseline = it })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Geri") }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = canNext,
                onClick = { if (step < lastStep) step++ else finish() },
                modifier = Modifier.height(52.dp),
            ) {
                Text(
                    when (step) {
                        0 -> "Başlayalım"
                        lastStep -> "Sözleşmeyi tamamla"
                        else -> "Devam"
                    },
                )
            }
        }
    }
}

@Composable
private fun Title(title: String, sub: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(6.dp))
    Muted(sub)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Welcome() {
    Spacer(Modifier.height(40.dp))
    Eyebrow("Odak")
    Spacer(Modifier.height(10.dp))
    Text("Scroll'dan çalışmaya.", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(16.dp))
    Text(
        "Reels, Shorts ya da TikTok açma isteği geldiğinde Odak seni bir an durdurur ve o anı 3 kartlık kısa bir teste çevirir. Aynı tetikleyici, yeni davranış.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "Karar hep sende: “Yine de aç” düğmesi her zaman orada.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(28.dp))
    Panel(tint = OdakColors.AmberSoft) {
        Eyebrow("Gerçekçi beklenti", OdakColors.Amber)
        Spacer(Modifier.height(6.dp))
        Text(
            "İlk haftalar zor. Bir alışkanlık ortalama 2–3 ayda otomatikleşiyor. Bir gün kaçırmak süreci bozmuyor.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PlanFields(
    ifValue: String, onIf: (String) -> Unit, ifHint: String,
    thenValue: String, onThen: (String) -> Unit, thenHint: String,
) {
    OutlinedTextField(
        value = ifValue, onValueChange = onIf,
        label = { Text("Eğer…") }, placeholder = { Text(ifHint) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = thenValue, onValueChange = onThen,
        label = { Text("…o zaman") }, placeholder = { Text(thenHint) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** İkinci yazım: durum ve eylem ayrı kutularda, arada ok. */
@Composable
private fun BondFields(
    ifValue: String, onIf: (String) -> Unit, ifHint: String,
    thenValue: String, onThen: (String) -> Unit, thenHint: String,
) {
    Panel(tint = OdakColors.GreenSoft) {
        Eyebrow("Durum")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = ifValue, onValueChange = onIf,
            placeholder = { Text(ifHint) }, modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        "↓",
        style = MaterialTheme.typography.displaySmall,
        color = OdakColors.Green,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    Panel(tint = OdakColors.AmberSoft) {
        Eyebrow("Eylem", OdakColors.Amber)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = thenValue, onValueChange = onThen,
            placeholder = { Text(thenHint) }, modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(10.dp))
    Muted("Kopyalamak yerine kendi kelimelerinle yeniden yaz. Silik yazı ilk hâli.")
}

@Composable
private fun PermissionsStep(seed: Boolean, onSeed: (Boolean) -> Unit, baseline: Boolean, onBaseline: (Boolean) -> Unit) {
    val context = LocalContext.current
    val tick by UiEvents.resumed.collectAsState()
    var notifAsked by remember { mutableStateOf(false) }
    val accOn = remember(tick) { Perms.accessibilityOn(context) }
    val notifOn = remember(tick, notifAsked) { Notifs.canPost(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifAsked = !notifAsked
    }

    Title("Son adım: izinler", "Neden istediğimizi açıkça yazıyoruz.")

    Panel {
        Eyebrow(if (accOn) "Açık ✓" else "Gerekli")
        Spacer(Modifier.height(6.dp))
        Text("Erişilebilirlik izni", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Muted(
            "Yalnızca seçtiğin uygulamanın ya da Reels/Shorts sekmesinin açıldığını fark etmek için. " +
                "Ekrandaki yazılar okunmaz, kaydedilmez, telefondan çıkmaz.",
        )
        if (!accOn) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { Perms.openAccessibility(context) }) { Text("Ayarları aç → Odak'ı etkinleştir") }
            Spacer(Modifier.height(4.dp))
            Muted("Android 13+ telefonda izin gri görünürse: Ayarlar → Uygulamalar → Odak → ⋮ → “Kısıtlanmış ayarlara izin ver”.")
        }
    }
    Spacer(Modifier.height(12.dp))
    Panel {
        Eyebrow(if (notifOn) "Açık ✓" else "İsteğe bağlı")
        Spacer(Modifier.height(6.dp))
        Text("Bildirimler", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Muted("Plan saatinde bir hatırlatma ve pazar günleri 4 kısa soru.")
        if (!notifOn && Build.VERSION.SDK_INT >= 33) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Bildirimlere izin ver") }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = seed, onCheckedChange = onSeed)
        Text("Denemek için küçük bir örnek deste ekle")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = baseline, onCheckedChange = onBaseline)
        Column {
            Text("İlk 7 gün yalnızca ölçüm yap (önerilen)")
            Muted("Bu hafta durdurma ekranı çıkmaz, Odak yalnızca sayar. Böylece kendi başlangıç çizgini görürsün.")
        }
    }
    if (!accOn) {
        Spacer(Modifier.height(8.dp))
        Muted("İzni şimdi açmasan da devam edebilirsin; durdurma ekranı izin açılınca çalışır.")
    }
}
