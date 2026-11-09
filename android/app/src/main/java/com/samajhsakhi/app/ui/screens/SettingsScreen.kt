@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samajhsakhi.app.data.Lang
import com.samajhsakhi.app.data.Langs
import com.samajhsakhi.app.ui.*

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onCaregiver: () -> Unit, onHelp: () -> Unit, onReset: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val voices by vm.voices.collectAsStateWithLifecycle()
    val serverOk by vm.serverOk.collectAsStateWithLifecycle()
    val progress by vm.offlineProgress.collectAsStateWithLifecycle()
    val offlineCount by vm.offlineCount.collectAsStateWithLifecycle()
    val settings = s ?: return

    LaunchedEffect(settings.effectiveOutLang, settings.voice) { vm.refreshOfflineCount() }
    LaunchedEffect(Unit) {
        vm.loadProfile()
        vm.loadCatalog()
        vm.checkServer()
    }

    var name by remember(profile?.displayName, settings.name) { mutableStateOf(profile?.displayName ?: settings.name) }
    var emName by remember(profile?.emergencyName) { mutableStateOf(profile?.emergencyName ?: "") }
    var emPhone by remember(profile?.emergencyPhone) { mutableStateOf(profile?.emergencyPhone ?: "") }
    var server by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var access by remember(settings.accessCode) { mutableStateOf(settings.accessCode) }
    var speed by remember(settings.speed) { mutableFloatStateOf(settings.speed) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Settings", onBack = onBack) { mod ->
        Column(mod) {
            SectionTitle("Language")
            Text("I speak", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LangChips(Langs.input, settings.inputLang, { l -> vm.updateSettings { it.copy(inputLang = l) } })
            Spacer(Modifier.height(12.dp))
            Text("Say my messages in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LangChips(
                listOf(Lang("", "Same", "Same as I speak")) + Langs.output, settings.outputLang,
                { l -> vm.updateSettings { it.copy(outputLang = l) } },
            )

            SectionTitle("Voice")
            DropdownField(
                label = "Voice", current = settings.voice.ifBlank { "Default" },
                options = listOf("") + voices, optionLabel = { it.ifBlank { "Default" } },
                onSelect = { v -> vm.updateSettings { it.copy(voice = v) } },
            )
            Spacer(Modifier.height(12.dp))
            Text("Speed  ${"%.2f".format(speed)}x", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = speed, onValueChange = { speed = it },
                onValueChangeFinished = { vm.updateSettings { it.copy(speed = speed) } },
                valueRange = 0.85f..1.15f,
            )

            SectionTitle("Accessibility")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = settings.highContrast, onCheckedChange = { c -> vm.updateSettings { it.copy(highContrast = c) } })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("High contrast", fontWeight = FontWeight.Medium)
                    Text(
                        "Black on white. Text also follows your phone's font size setting.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("Offline messages")
            val total = com.samajhsakhi.app.data.Cards.offlineSet.size
            Text(
                "Store the quick messages and the emergency message in ${Langs.english(settings.effectiveOutLang)} on this phone. " +
                    "They then play instantly with no internet and no credits. Stored: $offlineCount of $total.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val p = progress
            if (p != null) {
                LinearProgressIndicator(progress = { if (p.second > 0) p.first / p.second.toFloat() else 0f }, modifier = Modifier.fillMaxWidth())
                Text("Storing ${p.first} of ${p.second}...", style = MaterialTheme.typography.bodySmall)
            } else {
                BigButton("Download for offline use", outlined = true, onClick = { vm.downloadOffline(settings.effectiveOutLang) })
                TextButton(onClick = { vm.clearOffline() }, enabled = offlineCount > 0) { Text("Remove stored messages") }
            }

            SectionTitle("Profile")
            LabeledField(name, { name = it }, "Name", singleLine = true)
            Spacer(Modifier.height(10.dp))
            LabeledField(emName, { emName = it }, "Emergency contact name", singleLine = true)
            Spacer(Modifier.height(10.dp))
            LabeledField(emPhone, { emPhone = it }, "Emergency contact phone", singleLine = true)
            Spacer(Modifier.height(12.dp))
            BigButton("Save profile", outlined = true, onClick = {
                vm.saveProfile(name = name.trim(), emergencyName = emName.trim(), emergencyPhone = emPhone.trim())
            })

            SectionTitle("Privacy")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = profile?.saveHistory ?: true, onCheckedChange = { vm.saveProfile(saveHistory = it) })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Keep history", fontWeight = FontWeight.Medium)
                    Text("Recordings are never kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row {
                TextButton(onClick = { confirmClear = true }) { Text("Clear history") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete all my data") }
            }

            SectionTitle("Caregiver")
            ListRow("Caregiver sharing", "Choose exactly what a caregiver can see", onCaregiver)

            SectionTitle("Server")
            LabeledField(server, { server = it }, "Server address", singleLine = true)
            Spacer(Modifier.height(10.dp))
            LabeledField(access, { access = it }, "Access code (optional)", singleLine = true)
            Spacer(Modifier.height(8.dp))
            Text(
                when (serverOk) {
                    true -> "Connected"
                    false -> "Cannot reach the server"
                    null -> "Checking..."
                },
                fontWeight = FontWeight.Medium,
                color = if (serverOk == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            BigButton("Save and test connection", outlined = true, onClick = {
                val changed = server.trim() != settings.serverUrl
                vm.updateSettings {
                    it.copy(
                        serverUrl = server.trim(), accessCode = access.trim(),
                        token = if (changed) "" else it.token, userId = if (changed) "" else it.userId,
                    )
                }
                vm.checkServerSoon()
            })
            Spacer(Modifier.height(6.dp))
            Text(
                "On a phone use your laptop's Wi-Fi address, for example http://192.168.1.5:8000/. Both must be on the same Wi-Fi.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("About")
            ListRow("Help", null, onHelp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Vaani is an assistive communication tool, not a medical device. Powered by Gnani AI (Prisma speech recognition, Timbre voice).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All saved conversations will be deleted.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all your data?") },
            text = { Text("Your phrases, history and caregiver links will be removed from the server and this phone. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteEverything { onReset() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
