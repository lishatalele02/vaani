@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samajhsakhi.app.data.CareLink
import com.samajhsakhi.app.data.CarePerson
import com.samajhsakhi.app.ui.*

/** Consent-based caregiver pairing. Nothing is shared until the person switches it on. */
@Composable
fun CaregiverScreen(vm: AppViewModel, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.loadCaregiver() }

    ScreenScaffold(title = "Caregiver", onBack = onBack, scroll = false) { mod ->
        Column(mod) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("My caregivers") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("I am a caregiver") })
            }
            Column(Modifier.weight(1f).verticalScrollCompat().padding(16.dp)) {
                if (tab == 0) MyCaregivers(vm) else IAmCaregiver(vm)
            }
        }
    }
}

@Composable
private fun MyCaregivers(vm: AppViewModel) {
    val code by vm.pairCode.collectAsStateWithLifecycle()
    val links by vm.links.collectAsStateWithLifecycle()

    Text(
        "A caregiver can only see what you allow. Create a code, give it to your caregiver, then choose what to share.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    BigButton("Create pairing code", onClick = { vm.createCode() })
    code?.let { c ->
        Spacer(Modifier.height(12.dp))
        InfoCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Text("Give this code to your caregiver", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                c.code.chunked(3).joinToString(" "), fontSize = 42.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text("It works once and expires in 15 minutes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }

    SectionTitle("Connected caregivers")
    if (links.isEmpty()) {
        EmptyState("No caregivers connected", "When someone enters your code they will appear here.")
    }
    links.forEach { l -> LinkCard(vm, l) }
}

@Composable
private fun LinkCard(vm: AppViewModel, l: CareLink) {
    var confirm by remember { mutableStateOf(false) }
    InfoCard(Modifier.padding(bottom = 10.dp)) {
        Text(l.caregiverName.ifBlank { "Caregiver" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Choose what this caregiver can see:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ShareRow("My saved phrases", l.sharePhrases) { vm.updateLink(l, it, l.shareHistory, l.shareEmergency) }
        ShareRow("All my conversation history", l.shareHistory) { vm.updateLink(l, l.sharePhrases, it, l.shareEmergency) }
        ShareRow("My emergency contact", l.shareEmergency) { vm.updateLink(l, l.sharePhrases, l.shareHistory, it) }
        Text(
            "Even with history off, you can share single messages from the History screen.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { confirm = true }) { Text("Remove this caregiver") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Remove caregiver?") },
            text = { Text("They will immediately lose access to everything.") },
            confirmButton = { TextButton(onClick = { vm.removeLink(l.id); confirm = false }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ShareRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun IAmCaregiver(vm: AppViewModel) {
    val people by vm.people.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    Text("Enter the 6-digit code the person gave you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    LabeledField(code, { v -> code = v.filter { it.isDigit() }.take(6) }, "Pairing code", singleLine = true)
    Spacer(Modifier.height(8.dp))
    BigButton("Connect", enabled = code.length == 6, onClick = { vm.pairWith(code); code = "" })

    SectionTitle("People you support")
    if (people.isEmpty()) EmptyState("Nobody yet", "Ask the person to create a pairing code in their app.")
    people.forEach { p -> PersonCard(vm, p) }
}

@Composable
private fun PersonCard(vm: AppViewModel, p: CarePerson) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var shownKind by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(kind: String) {
        shownKind = kind
        error = null
        lines = emptyList()
        vm.loadPersonData(p, kind) { result, err -> lines = result; error = err }
    }

    InfoCard(Modifier.padding(bottom = 10.dp)) {
        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val shared = listOfNotNull(
            if (p.shareHistory) "history" else null,
            if (p.sharePhrases) "phrases" else null,
            if (p.shareEmergency) "emergency contact" else null,
        )
        Text(
            if (shared.isEmpty()) "Nothing shared yet (only messages they pick)" else "Shared: " + shared.joinToString(", "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { load("messages") }) { Text("Messages") }
            OutlinedButton(onClick = { load("phrases") }, enabled = p.sharePhrases) { Text("Phrases") }
            OutlinedButton(onClick = { load("emergency") }, enabled = p.shareEmergency) { Text("Emergency") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        if (shownKind.isNotEmpty() && error == null) {
            Spacer(Modifier.height(8.dp))
            if (lines.isEmpty()) Text("Nothing to show.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            lines.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
        }
        TextButton(onClick = { vm.leavePerson(p.userId) }) { Text("Stop following") }
    }
}
