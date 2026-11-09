@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import java.util.Calendar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samajhsakhi.app.data.Cards
import com.samajhsakhi.app.data.Langs
import com.samajhsakhi.app.data.QuickCard
import com.samajhsakhi.app.ui.*

@Composable
fun HomeScreen(vm: AppViewModel, go: (String) -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val serverOk by vm.serverOk.collectAsStateWithLifecycle()
    var dialogCard by remember { mutableStateOf<QuickCard?>(null) }
    val settings = s ?: return

    LaunchedEffect(Unit) {
        vm.checkServer()
        vm.refreshAll()
    }

    ScreenScaffold(
        title = "Vaani",
        actions = {
            IconButton(onClick = { go("settings") }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        },
    ) { mod ->
        Column(mod) {
            Text("Powered by Gnani AI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(
                if (settings.name.isBlank()) "Hello" else "Hello, ${settings.name}",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            )
            Text("What would you like to say?", color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (serverOk == false) {
                Spacer(Modifier.height(12.dp))
                ErrorBanner("Cannot reach the server at ${settings.serverUrl}", onRetry = { vm.checkServer() })
            }

            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RecordButton(recording = false, level = 0f, onClick = { go("speak") }, size = 96.dp)
                Text("Tap to speak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                LangChips(Langs.input, settings.inputLang, { l -> vm.updateSettings { it.copy(inputLang = l) } })
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { go("emergency") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    .semantics { contentDescription = "Emergency. Speaks an urgent message and shows a call button." },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
            ) { Text("Emergency", fontSize = 20.sp, fontWeight = FontWeight.Bold) }

            // ordered by what this person uses most, with a small time-of-day nudge
            val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
            val homeCards = remember(settings.cardUse) { Cards.homeFor(settings.cardUse, hour) }
            SectionTitle("Quick messages")
            homeCards.chunked(2).forEach { rowCards ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowCards.forEach { c ->
                        QuickCardTile(c, settings.effectiveOutLang, Modifier.weight(1f)) { dialogCard = c }
                    }
                }
            }
            TextButton(onClick = { go("quick") }) { Text("All quick messages") }

            SectionTitle("More")
            ListRow("My phrases") { go("phrases") }
            ListRow("Type and speak") { go("type") }
            ListRow("History") { go("history") }
            Spacer(Modifier.height(24.dp))
        }
    }

    dialogCard?.let { c ->
        QuickMessageDialog(vm, c, settings.effectiveOutLang, onDismiss = { dialogCard = null })
    }
}

@Composable
fun QuickCardTile(card: QuickCard, lang: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.heightIn(min = 92.dp)
            .semantics { contentDescription = card.text(lang) }
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            Text(
                card.text(lang), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Shows a quick message, lets the user edit it, choose the language and speak it. */
@Composable
fun QuickMessageDialog(vm: AppViewModel, card: QuickCard, initialLang: String, onDismiss: () -> Unit) {
    var lang by remember { mutableStateOf(initialLang) }
    var text by remember(card, lang) { mutableStateOf(card.text(lang)) }

    AlertDialog(
        onDismissRequest = { vm.speechPlayer.stop(); onDismiss() },
        title = { Text("Speak message") },
        text = {
            Column {
                LabeledField(text, { text = it }, "Message", minLines = 2)
                Spacer(Modifier.height(10.dp))
                LangChips(Langs.output, lang, { lang = it })
                Spacer(Modifier.height(16.dp))
                BigButton("Speak", onClick = { vm.playQuickCard(card, text, lang) })
                Spacer(Modifier.height(10.dp))
                PlaybackControls(vm)
            }
        },
        confirmButton = { TextButton(onClick = { vm.speechPlayer.stop(); onDismiss() }) { Text("Close") } },
    )
}
