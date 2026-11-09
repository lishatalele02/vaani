@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samajhsakhi.app.data.Lang
import com.samajhsakhi.app.data.Langs
import com.samajhsakhi.app.ui.*

private const val LAST_STEP = 2

@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var inLang by remember { mutableStateOf("mr-IN") }
    var outLang by remember { mutableStateOf("") }
    var server by remember(s?.serverUrl) { mutableStateOf(s?.serverUrl ?: "") }
    var micGranted by remember { mutableStateOf(hasMicPermission(ctx)) }
    var finishing by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }

    ScreenScaffold(title = "Step ${step + 1} of ${LAST_STEP + 1}") { mod ->
        Column(mod) {
            Spacer(Modifier.height(12.dp))
            when (step) {
                0 -> {
                    Text("Vaani", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Powered by Gnani AI", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Speak in Marathi, Hindi, English or Hinglish. The app works out what you mean, " +
                            "you confirm it, and it says it out loud. Speech recognition by Gnani Prisma, voice by Gnani Timbre.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(24.dp))
                    listOf("Speak", "Check the meaning", "Hear it spoken").forEachIndexed { i, t ->
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
                            Text(t, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Nothing is spoken until you confirm. Recordings are deleted right after they are sent.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                1 -> {
                    Text("Languages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    SectionTitle("I speak")
                    LangChips(Langs.input, inLang, { inLang = it })
                    SectionTitle("Say my messages in")
                    LangChips(listOf(Lang("", "Same", "Same as I speak")) + Langs.output, outLang, { outLang = it })
                    SectionTitle("Microphone")
                    if (micGranted) {
                        Text("Microphone allowed", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    } else {
                        Text(
                            "Needed only to record what you say, and only when you tap the microphone. " +
                                "You can skip this and use quick messages.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        BigButton("Allow microphone", onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }, outlined = true)
                    }
                }
                else -> {
                    Text("Almost done", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    LabeledField(name, { name = it }, "Your name (optional)", singleLine = true)
                    Spacer(Modifier.height(16.dp))
                    LabeledField(server, { server = it }, "Server address", singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Phone: http://<laptop Wi-Fi IP>:8000/\nEmulator: http://10.0.2.2:8000/",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            if (step < LAST_STEP) {
                BigButton("Next", onClick = { step += 1 })
            } else {
                BigButton(
                    if (finishing) "Please wait..." else "Get started", enabled = !finishing,
                    onClick = {
                        finishing = true
                        vm.finishOnboarding(name, inLang, outLang, server, "", onDone)
                    },
                )
            }
            if (step > 0) {
                TextButton(onClick = { step -= 1 }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
            }
        }
    }
}
