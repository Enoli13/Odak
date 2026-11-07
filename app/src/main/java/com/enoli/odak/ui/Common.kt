package com.enoli.odak.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.enoli.odak.service.GateService
import com.enoli.odak.ui.theme.OdakColors
import kotlinx.coroutines.flow.MutableStateFlow

/** MainActivity her öne geldiğinde artar; izin durumunu yeniden okumak için. */
object UiEvents {
    val resumed = MutableStateFlow(0)
}

object Perms {
    fun accessibilityOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context, GateService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun openAccessibility(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun goHome(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Denemeyi kutlayan kısa geri bildirimler — doğruluğa değil çabaya. */
object Cheer {
    private val afterTry = listOf(
        "Güzel deneme.",
        "Hatırlamaya çalışmak, tam da işe yarayan kısım.",
        "Her deneme bir tekrar sayılır.",
        "Beynin şu an çalışıyor, bu iyi.",
        "Bir kart daha geride.",
    )
    private val afterQuit = listOf(
        "Vazgeçtin. Bu küçük bir zafer.",
        "Aynı dürtü, yeni davranış.",
        "Güzel seçim. Hadi ısınalım.",
    )

    fun tryMsg(seed: Int) = afterTry[Math.floorMod(seed, afterTry.size)]
    fun quitMsg(seed: Int) = afterQuit[Math.floorMod(seed, afterQuit.size)]
}

@Composable
fun Eyebrow(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    tint: Color = OdakColors.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = tint,
        border = BorderStroke(1.dp, OdakColors.Line),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = OdakColors.Muted, modifier = modifier)
}

/** − değer + biçiminde küçük sayaç. */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int, max: Int, format: (Int) -> String = { it.toString() }) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) { Text("−") }
        Spacer(Modifier.width(12.dp))
        Text(format(value), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max) { Text("+") }
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text) }
}
