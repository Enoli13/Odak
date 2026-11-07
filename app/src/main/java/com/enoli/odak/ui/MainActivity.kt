package com.enoli.odak.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.enoli.odak.data.Repo
import com.enoli.odak.data.Scheduler
import com.enoli.odak.ui.cards.CardSession
import com.enoli.odak.ui.decks.DeckDetailScreen
import com.enoli.odak.ui.decks.DecksScreen
import com.enoli.odak.ui.home.HomeScreen
import com.enoli.odak.ui.onboarding.OnboardingScreen
import com.enoli.odak.ui.rhythm.RhythmScreen
import com.enoli.odak.ui.rhythm.SrbaiScreen
import com.enoli.odak.ui.settings.SettingsScreen
import com.enoli.odak.ui.theme.OdakColors
import com.enoli.odak.ui.theme.OdakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        Repo.init(applicationContext)
        handleOpen(intent)
        setContent {
            OdakTheme {
                val data by Repo.data.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    if (!data.settings.onboarded) OnboardingScreen() else AppShell()
                    // Her açılışta yükleme ekranı (uygulama yeniden öne geldiğinde de)
                    key(splashRun) { SplashOverlay() }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    private fun handleOpen(i: android.content.Intent?) {
        if (i == null) return
        val target = i.getStringExtra(com.enoli.odak.work.Notifs.EXTRA_OPEN) ?: return
        UiEvents.openRequest.value = target
        i.removeExtra(com.enoli.odak.work.Notifs.EXTRA_OPEN)
    }

    private var splashRun by mutableIntStateOf(0)
    private var leftApp = false

    override fun onStop() {
        super.onStop()
        // Yalnızca uygulamadan gerçekten çıkıldıysa (ayar değişikliği değil)
        leftApp = !isChangingConfigurations
    }

    override fun onRestart() {
        super.onRestart()
        if (leftApp) splashRun++
        leftApp = false
    }

    override fun onResume() {
        super.onResume()
        UiEvents.resumed.value = UiEvents.resumed.value + 1
    }
}

/** Basit gezinme: alt sekmeler + üstte açılan ekranlar. */
sealed interface Route {
    data object Home : Route
    data object Decks : Route
    data object Rhythm : Route
    data object Settings : Route
    data object Report : Route
    data object Inbox : Route
    data class Deck(val id: Long) : Route
    /** deckId null: tüm destelerden karışık. */
    data class Study(val deckId: Long?, val count: Int) : Route
    data object Srbai : Route
}

private data class Tab(val route: Route, val label: String, val icon: ImageVector)

@Composable
private fun AppShell() {
    var tab by remember { mutableStateOf<Route>(Route.Home) }
    var overlay by remember { mutableStateOf<Route?>(null) }
    val data by Repo.data.collectAsState()

    val tabs = listOf(
        Tab(Route.Home, "Bugün", Icons.Filled.Home),
        Tab(Route.Decks, "Desteler", Icons.AutoMirrored.Filled.List),
        Tab(Route.Rhythm, "Ritim", Icons.Filled.DateRange),
        Tab(Route.Report, "Rapor", Icons.Filled.Info),
        Tab(Route.Settings, "Ayarlar", Icons.Filled.Settings),
    )

    val openRequest by UiEvents.openRequest.collectAsState()
    LaunchedEffect(openRequest) {
        if (openRequest == com.enoli.odak.work.Notifs.OPEN_INBOX) {
            overlay = Route.Inbox
            UiEvents.openRequest.value = null
        }
    }

    BackHandler(enabled = overlay != null || tab != Route.Home) {
        if (overlay != null) overlay = null else tab = Route.Home
    }

    val open: (Route) -> Unit = { overlay = it }
    val close: () -> Unit = { overlay = null }

    Scaffold(
        containerColor = OdakColors.Paper,
        bottomBar = {
            if (overlay == null) {
                NavigationBar(containerColor = OdakColors.Card) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t.route,
                            onClick = { tab = t.route },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = OdakColors.GreenSoft),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val o = overlay) {
                is Route.Deck -> DeckDetailScreen(o.id, onBack = close, onStudy = { open(Route.Study(o.id, 10)) })
                is Route.Study -> {
                    val pool = if (o.deckId == null) data.cards else data.cardsIn(o.deckId)
                    val picked = remember(o) { Scheduler.pick(pool, o.count) }
                    if (picked.isEmpty()) {
                        EmptyStudy(onBack = close)
                    } else {
                        CardSession(
                            cards = picked,
                            deckName = { id -> data.decks.firstOrNull { it.id == id }?.name ?: "" },
                            onFinish = { n ->
                                Repo.logSession(n)
                                close()
                            },
                        )
                    }
                }
                is Route.Srbai -> SrbaiScreen(onDone = close)
                is Route.Inbox -> com.enoli.odak.ui.report.InboxScreen(onBack = close)
                else -> when (tab) {
                    Route.Decks -> DecksScreen(onOpen = { open(Route.Deck(it)) })
                    Route.Rhythm -> RhythmScreen(onSrbai = { open(Route.Srbai) })
                    Route.Settings -> SettingsScreen()
                    Route.Report -> com.enoli.odak.ui.report.ReportScreen()
                    else -> HomeScreen(
                        onStudy = { n -> open(Route.Study(null, n)) },
                        onSrbai = { open(Route.Srbai) },
                        onDecks = { tab = Route.Decks },
                        onInbox = { open(Route.Inbox) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStudy(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Panel {
            Text("Burada henüz kart yok.")
            Muted("Desteler sekmesinden kart ekleyebilir ya da toplu yapıştırabilirsin.")
            LinkButton("Geri dön", onBack)
        }
    }
}
