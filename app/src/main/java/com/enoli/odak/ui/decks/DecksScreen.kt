package com.enoli.odak.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enoli.odak.data.Card
import com.enoli.odak.data.Repo
import com.enoli.odak.data.Scheduler
import com.enoli.odak.ui.Eyebrow
import com.enoli.odak.ui.Muted
import com.enoli.odak.ui.Panel
import com.enoli.odak.ui.theme.OdakColors

@Composable
fun DecksScreen(onOpen: (Long) -> Unit) {
    val data by Repo.data.collectAsState()
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Desteler", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Button(onClick = { adding = true }) { Text("Yeni deste") }
            }
            Spacer(Modifier.height(4.dp))
            Muted("Çalışırken farklı destelerden kartlar karışık gelir.")
        }
        if (data.decks.isEmpty()) {
            item {
                Panel {
                    Text("Henüz deste yok.", style = MaterialTheme.typography.titleMedium)
                    Muted("Bir ders için deste aç, kartları tek tek ya da toplu yapıştırarak ekle.")
                }
            }
        }
        items(data.decks, key = { it.id }) { deck ->
            val cards = data.cardsIn(deck.id)
            val due = Scheduler.dueCount(cards)
            Panel(Modifier.clickable { onOpen(deck.id) }) {
                Text(deck.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Muted("${cards.size} kart · zamanı gelen $due")
            }
        }
    }

    if (adding) {
        NameDialog(
            title = "Yeni deste",
            initial = "",
            onDismiss = { adding = false },
            onSave = { name ->
                val id = Repo.addDeck(name)
                adding = false
                onOpen(id)
            },
        )
    }
}

@Composable
fun DeckDetailScreen(deckId: Long, onBack: () -> Unit, onStudy: () -> Unit) {
    val data by Repo.data.collectAsState()
    val deck = data.decks.firstOrNull { it.id == deckId }
    if (deck == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val cards = data.cardsIn(deckId)
    var editing by remember { mutableStateOf<Card?>(null) }
    var newCard by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri") }
                Text(
                    deck.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "Yeniden adlandır") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Desteyi sil") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onStudy, enabled = cards.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Çalış") }
                OutlinedButton(onClick = { newCard = true }, modifier = Modifier.weight(1f)) { Text("Kart ekle") }
                OutlinedButton(onClick = { bulk = true }, modifier = Modifier.weight(1f)) { Text("Toplu") }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (cards.isEmpty()) {
            item { Muted("Bu destede kart yok. “Toplu” ile birçok kartı tek seferde yapıştırabilirsin.") }
        }
        items(cards, key = { it.id }) { card ->
            Panel(Modifier.clickable { editing = card }) {
                Text(card.front, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Muted(card.back, Modifier.fillMaxWidth())
                if (card.why.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Eyebrow("Neden? sorusu var", OdakColors.Amber)
                }
                Spacer(Modifier.height(4.dp))
                Muted(boxLabel(card))
            }
        }
    }

    if (newCard) {
        CardDialog(null, onDismiss = { newCard = false }) { f, b, w ->
            Repo.addCard(deckId, f, b, w)
        }
    }
    editing?.let { c ->
        CardDialog(c, onDismiss = { editing = null }, onDelete = {
            Repo.deleteCard(c.id)
            editing = null
        }) { f, b, w ->
            Repo.editCard(c.id, f, b, w)
            editing = null
        }
    }
    if (bulk) BulkDialog(deckId) { bulk = false }
    if (renaming) {
        NameDialog("Desteyi yeniden adlandır", deck.name, onDismiss = { renaming = false }) {
            Repo.renameDeck(deckId, it)
            renaming = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Deste silinsin mi?") },
            text = { Text("“${deck.name}” ve içindeki ${cards.size} kart silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteDeck(deckId)
                    onBack()
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}

private fun boxLabel(card: Card): String = when {
    card.seen == 0 -> "Yeni"
    card.box == 0 -> "Tekrar gelecek"
    else -> "Kutu ${card.box} · ${card.known}/${card.seen} hatırlandı"
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Ad") })
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
    )
}

/** Yeni kart için dialog açık kalır (art arda ekleme); düzenlemede kapanır. */
@Composable
private fun CardDialog(
    card: Card?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (String, String, String) -> Unit,
) {
    var front by remember { mutableStateOf(card?.front ?: "") }
    var back by remember { mutableStateOf(card?.back ?: "") }
    var why by remember { mutableStateOf(card?.why ?: "") }
    var added by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (card == null) "Kart ekle" else "Kartı düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(front, { front = it }, label = { Text("Ön yüz: soru") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(back, { back = it }, label = { Text("Arka yüz: cevap") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(why, { why = it }, label = { Text("Neden? sorusu (isteğe bağlı)") }, modifier = Modifier.fillMaxWidth())
                if (added > 0) Muted("$added kart eklendi. Devam edebilirsin.")
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Kartı sil", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = front.isNotBlank() && back.isNotBlank(),
                onClick = {
                    onSave(front, back, why)
                    if (card == null) {
                        added++
                        front = ""
                        back = ""
                        why = ""
                    }
                },
            ) { Text(if (card == null) "Ekle" else "Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (card == null) "Bitti" else "İptal") } },
    )
}

@Composable
private fun BulkDialog(deckId: Long, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Toplu ekle") },
        text = {
            Column {
                Muted("Her satıra bir kart: soru | cevap\nİstersen üçüncü kısım: soru | cevap | neden?")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    placeholder = { Text("Mitokondrinin görevi? | ATP üretimi") },
                )
                result?.let {
                    Spacer(Modifier.height(6.dp))
                    Muted(if (it == 0) "Hiç satır okunamadı. Ayırıcı olarak | kullan." else "$it kart eklendi.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = Repo.importText(deckId, text)
                result = n
                if (n > 0) text = ""
            }) { Text("Ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}
