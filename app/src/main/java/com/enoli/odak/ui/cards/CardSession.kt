package com.enoli.odak.ui.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enoli.odak.data.Card
import com.enoli.odak.data.Repo
import com.enoli.odak.ui.Cheer
import com.enoli.odak.ui.Eyebrow
import com.enoli.odak.ui.Muted
import com.enoli.odak.ui.Panel
import com.enoli.odak.ui.theme.OdakColors

/**
 * Kart motoru. Ön yüz soru; cevap önce yazılır (ya da içinden söylenir), sonra kart çevrilir.
 * Bilinen kart oturumdan çıkar, bilinmeyen sona gider ve aynı oturumda tekrar gelir.
 * [onFinish] çözülen farklı kart sayısıyla çağrılır.
 */
@Composable
fun CardSession(
    cards: List<Card>,
    deckName: (Long) -> String,
    onFinish: (cardsDone: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue = remember { mutableStateListOf<Card>().apply { addAll(cards) } }
    val attempted = remember { mutableStateListOf<Long>() }
    var knownCount by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var cheer by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    val total = cards.size
    val current = queue.firstOrNull()

    LaunchedEffect(current == null) {
        if (current == null && !finished) {
            finished = true
            onFinish(attempted.size)
        }
    }
    if (current == null) return

    fun next(known: Boolean) {
        Repo.review(current.id, known)
        if (current.id !in attempted) attempted.add(current.id)
        queue.removeAt(0)
        if (known) knownCount++ else queue.add(current)
        step++
        cheer = Cheer.tryMsg(step)
        flipped = false
        answer = ""
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Kart ${(knownCount + 1).coerceAtMost(total)} / $total",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                if (!finished) {
                    finished = true
                    onFinish(attempted.size)
                }
            }) { Text("Bitir") }
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else knownCount.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
            color = OdakColors.Green,
            trackColor = OdakColors.GreenSoft,
        )
        Spacer(Modifier.height(10.dp))
        Muted(cheer ?: "Önce cevabı hatırlamaya çalış, sonra çevir.")
        Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = current to flipped,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "card",
        ) { (card, isFlipped) ->
            Panel(Modifier.heightIn(min = 220.dp)) {
                Eyebrow(deckName(card.deckId), OdakColors.Muted)
                Spacer(Modifier.height(10.dp))
                Text(card.front, style = MaterialTheme.typography.headlineSmall)
                if (isFlipped) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = OdakColors.Line)
                    Spacer(Modifier.height(14.dp))
                    if (answer.isNotBlank()) {
                        Muted("Senin cevabın: $answer")
                        Spacer(Modifier.height(8.dp))
                    }
                    Eyebrow("Cevap")
                    Spacer(Modifier.height(4.dp))
                    Text(card.back, style = MaterialTheme.typography.titleLarge)
                    if (card.why.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Panel(tint = OdakColors.AmberSoft) {
                            Eyebrow("Neden?", OdakColors.Amber)
                            Spacer(Modifier.height(4.dp))
                            Text(card.why, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            Muted("Cevabı kendi cümlelerinle açıkla, sonra devam et.")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (!flipped) {
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                label = { Text("Cevabın (yaz ya da içinden söyle)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { flipped = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Kartı çevir")
            }
        } else {
            Text(
                "Hatırladın mı?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { next(false) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Bilemedim")
                }
                Button(onClick = { next(true) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Bildim")
                }
            }
            Spacer(Modifier.height(8.dp))
            Muted("Bilemediğin kart bu turun sonunda tekrar gelir.", Modifier.fillMaxWidth())
        }
    }
}
