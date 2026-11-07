package com.enoli.odak.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enoli.odak.ui.theme.OdakColors
import kotlinx.coroutines.delay

/** Uygulama her açıldığında gösterilen yükleme ekranı. */
@Composable
fun SplashOverlay(durationMs: Long = 1600L) {
    var visible by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val steps = 40
        for (i in 1..steps) {
            delay(durationMs / steps)
            progress = i / steps.toFloat()
        }
        visible = false
    }
    AnimatedVisibility(visible = visible, exit = fadeOut(tween(350))) {
        SplashContent(progress)
    }
}

@Composable
private fun SplashContent(progress: Float) {
    val pulse = rememberInfiniteTransition(label = "splash")
    val scale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(OdakColors.Green)
            // Yükleme sırasında alttaki ekrana dokunulmasın
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(120.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2 * scale
                drawCircle(OdakColors.Paper, radius = r - 6.dp.toPx(), center = c, style = Stroke(6.dp.toPx()))
                drawCircle(OdakColors.Paper.copy(alpha = 0.6f), radius = r * 0.5f, center = c, style = Stroke(4.dp.toPx()))
                drawCircle(OdakColors.Paper, radius = 7.dp.toPx(), center = c)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Enoli13",
                color = OdakColors.Paper,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text("Odak · Scroll'dan çalışmaya", color = OdakColors.Paper.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(160.dp),
                color = OdakColors.Paper,
                trackColor = OdakColors.Paper.copy(alpha = 0.25f),
            )
        }
    }
}
