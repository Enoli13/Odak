package com.enoli.odak.ui.settings

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enoli.odak.data.Repo
import com.enoli.odak.ui.Eyebrow
import com.enoli.odak.ui.LinkButton
import com.enoli.odak.ui.Muted
import com.enoli.odak.ui.Panel
import com.enoli.odak.ui.Perms
import com.enoli.odak.ui.Stepper
import com.enoli.odak.ui.UiEvents
import com.enoli.odak.ui.onboarding.TargetPicker
import com.enoli.odak.work.Notifs
import com.enoli.odak.work.ReminderWorker

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val data by Repo.data.collectAsState()
    val s = data.settings
    val tick by UiEvents.resumed.collectAsState()
    var notifAsked by remember { mutableStateOf(false) }
    val accOn = remember(tick) { Perms.accessibilityOn(context) }
    val notifOn = remember(tick, notifAsked) { Notifs.canPost(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifAsked = !notifAsked }
    var editPlans by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium)
        Muted("Limitleri ve hedefleri sen koyarsın. Odak yalnızca önerir.")

        Panel {
            Eyebrow("Durdurma ekranı")
            Spacer(Modifier.height(10.dp))
            Text("Bekleme süresi (nefes)", style = MaterialTheme.typography.titleMedium)
            Stepper(s.waitSeconds, { v -> Repo.updateSettings { it.copy(waitSeconds = v) } }, 3, 15) { "$it sn" }
            Spacer(Modifier.height(12.dp))
            Text("Vazgeçince çözülecek kart", style = MaterialTheme.typography.titleMedium)
            Stepper(s.gateCards, { v -> Repo.updateSettings { it.copy(gateCards = v) } }, 1, 10)
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = "Hak edilmiş mola",
                sub = "25 dk çalışınca 10 dk'lık molayı kendin açabilirsin.",
                checked = s.earnedBreak,
            ) { on -> Repo.updateSettings { it.copy(earnedBreak = on) } }
        }

        Panel {
            Eyebrow("Hedef uygulamalar")
            Spacer(Modifier.height(10.dp))
            TargetPicker(s.targets) { list -> Repo.updateSettings { it.copy(targets = list) } }
        }

        Panel {
            Eyebrow("Planların")
            Spacer(Modifier.height(6.dp))
            Text("Hedef: ${s.goal}")
            Text("Çalışma: Eğer ${s.studyIf} → ${s.studyThen}")
            Text("Dürtü: Eğer ${s.urgeIf} → ${s.urgeThen}")
            LinkButton("Düzenle") { editPlans = true }
            Spacer(Modifier.height(6.dp))
            Text("Plan saati", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(s.planHour, { v ->
                    Repo.updateSettings { it.copy(planHour = v) }
                    ReminderWorker.schedule(context, replace = true)
                }, 0, 23) { "%02d".format(it) }
                Spacer(Modifier.width(8.dp))
                Text(":")
                Spacer(Modifier.width(8.dp))
                Stepper(s.planMinute / 5, { v ->
                    Repo.updateSettings { it.copy(planMinute = v * 5) }
                    ReminderWorker.schedule(context, replace = true)
                }, 0, 11) { "%02d".format(it * 5) }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Hatırlatmalar",
                sub = "Plan saati, ara uzayınca nazik not, pazar günü 4 soru.",
                checked = s.remindersOn,
            ) { on -> Repo.updateSettings { it.copy(remindersOn = on) } }
        }

        Panel {
            Eyebrow("İzinler")
            Spacer(Modifier.height(8.dp))
            Text("Erişilebilirlik: ${if (accOn) "açık" else "kapalı"}", style = MaterialTheme.typography.titleMedium)
            Muted("Kapatırsan Odak seni kilitlemez; yalnızca bir kez sorar.")
            OutlinedButton(onClick = { Perms.openAccessibility(context) }) { Text("Erişilebilirlik ayarları") }
            if (s.accessibilityOptOut) {
                LinkButton("“İzin kapalı” hatırlatmasını tekrar göster") {
                    Repo.updateSettings { it.copy(accessibilityOptOut = false) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Bildirimler: ${if (notifOn) "açık" else "kapalı"}", style = MaterialTheme.typography.titleMedium)
            if (!notifOn && Build.VERSION.SDK_INT >= 33) {
                OutlinedButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Bildirimlere izin ver") }
            }
        }

        Panel {
            Eyebrow("Gizlilik")
            Spacer(Modifier.height(6.dp))
            Muted("Odak tamamen telefonunda çalışır. Hesap yok, sunucu yok; kullanım verin telefondan çıkmaz.")
        }
    }

    if (editPlans) PlansDialog(onDismiss = { editPlans = false })
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Muted(sub)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PlansDialog(onDismiss: () -> Unit) {
    val s = Repo.current.settings
    var goal by remember { mutableStateOf(s.goal) }
    var studyIf by remember { mutableStateOf(s.studyIf) }
    var studyThen by remember { mutableStateOf(s.studyThen) }
    var urgeIf by remember { mutableStateOf(s.urgeIf) }
    var urgeThen by remember { mutableStateOf(s.urgeThen) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Planları düzenle") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(goal, { goal = it }, label = { Text("Hedef") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(studyIf, { studyIf = it }, label = { Text("Çalışma · Eğer…") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(studyThen, { studyThen = it }, label = { Text("Çalışma · …o zaman") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(urgeIf, { urgeIf = it }, label = { Text("Dürtü · Eğer…") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(urgeThen, { urgeThen = it }, label = { Text("Dürtü · …o zaman") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Repo.updateSettings {
                    it.copy(
                        goal = goal.trim(), studyIf = studyIf.trim(), studyThen = studyThen.trim(),
                        urgeIf = urgeIf.trim(), urgeThen = urgeThen.trim(),
                    )
                }
                onDismiss()
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}
