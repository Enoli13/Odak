package com.enoli.odak.ui.onboarding

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enoli.odak.data.Catalog
import com.enoli.odak.data.TargetApp
import com.enoli.odak.ui.Muted
import com.enoli.odak.ui.Panel

private fun installed(context: Context, pkg: String): Boolean = try {
    context.packageManager.getPackageInfo(pkg, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/** Hedef uygulama seçimi: işaretle, varsa "yalnızca kısa video" modunu seç. */
@Composable
fun TargetPicker(selected: List<TargetApp>, onChange: (List<TargetApp>) -> Unit) {
    val context = LocalContext.current
    val installedMap = remember { Catalog.apps.associate { it.pkg to installed(context, it.pkg) } }
    // Yüklü olanlar üstte
    val apps = remember { Catalog.apps.sortedByDescending { installedMap[it.pkg] == true } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        apps.forEach { app ->
            val current = selected.firstOrNull { it.pkg == app.pkg }
            val isOn = current != null
            Panel {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onChange(
                                if (isOn) selected.filterNot { it.pkg == app.pkg }
                                else selected + TargetApp(app.pkg, app.label, shortOnly = app.shortLabel != null),
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isOn, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        if (installedMap[app.pkg] != true) Muted("Bu telefonda yüklü görünmüyor")
                    }
                }
                if (isOn && app.shortLabel != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.shortLabel, style = MaterialTheme.typography.bodyMedium)
                            Muted(
                                if (current!!.shortOnly) "Diğer sekmeler serbest" else "Uygulamanın tamamında durur",
                            )
                        }
                        Switch(
                            checked = current!!.shortOnly,
                            onCheckedChange = { on ->
                                onChange(selected.map { if (it.pkg == app.pkg) it.copy(shortOnly = on) else it })
                            },
                        )
                    }
                }
            }
        }
        Muted("Tarayıcıdan girmek her zaman mümkün; Odak kilitlemez, yalnızca bir an durdurur.")
    }
}
