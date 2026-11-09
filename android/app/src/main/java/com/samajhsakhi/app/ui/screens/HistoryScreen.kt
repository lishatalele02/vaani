@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samajhsakhi.app.data.HistoryItem
import com.samajhsakhi.app.data.Langs
import com.samajhsakhi.app.data.prettyTime
import com.samajhsakhi.app.ui.*

@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var detailId by remember { mutableStateOf<Int?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadHistory() }
    DisposableEffect(Unit) { onDispose { vm.speechPlayer.stop() } }

    val q = query.trim().lowercase()
    val shown = history.filter {
        q.isEmpty() || it.finalText.lowercase().contains(q) || it.transcript.lowercase().contains(q) || it.meaning.lowercase().contains(q)
    }
    val detail = history.firstOrNull { it.id == detailId }

    ScreenScaffold(
        title = "History", onBack = onBack, scroll = false,
        actions = {
            if (history.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.Delete, contentDescription = "Clear all history") }
            }
        },
    ) { mod ->
        Column(mod.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search history") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) {
                EmptyState("Nothing here yet", "Messages you speak are listed here.")
            } else if (shown.isEmpty()) {
                EmptyState("No matches", "Try a different search.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(shown, key = { it.id }) { h ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { detailId = h.id },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(h.finalText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (h.meaning.isNotBlank()) Text(h.meaning, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    prettyTime(h.createdAt) + if (h.shared) "  ·  shared" else "",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { h ->
        HistoryDetail(vm, h, s?.effectiveOutLang ?: "hi-IN", onDismiss = { vm.speechPlayer.stop(); detailId = null })
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("All saved conversations will be deleted. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryDetail(vm: AppViewModel, h: HistoryItem, fallbackLang: String, onDismiss: () -> Unit) {
    val lang = h.outputLanguage.ifBlank { fallbackLang }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conversation") },
        text = {
            Column(Modifier.verticalScrollCompat()) {
                Text("Message spoken", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(h.finalText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (h.meaning.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Meaning", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(h.meaning)
                }
                if (h.transcript.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("What was said", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(h.transcript)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${prettyTime(h.createdAt)}  ·  spoken in ${Langs.english(lang)}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                BigButton("Replay", onClick = { vm.speakText(h.finalText, lang) })
                Spacer(Modifier.height(8.dp))
                PlaybackControls(vm)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Switch(checked = h.shared, onCheckedChange = { vm.shareHistory(h.id, it) })
                    Spacer(Modifier.width(10.dp))
                    Text("Share this with my caregivers")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = { vm.deleteHistory(h.id); onDismiss() }) { Text("Delete") } },
    )
}
