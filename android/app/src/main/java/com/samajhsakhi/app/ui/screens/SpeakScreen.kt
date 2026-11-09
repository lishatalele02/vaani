@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui.screens

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samajhsakhi.app.audio.PlayState
import com.samajhsakhi.app.audio.WavRecorder
import com.samajhsakhi.app.data.Langs
import com.samajhsakhi.app.data.formatDuration
import com.samajhsakhi.app.ui.*

@Composable
fun SpeakScreen(vm: AppViewModel, onBack: () -> Unit, onQuick: () -> Unit) {
    val ctx = LocalContext.current
    val ui by vm.speak.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    var denied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) vm.startRecording()
    }
    fun tryStart() {
        if (hasMicPermission(ctx)) vm.startRecording() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(Unit) {
        onDispose {
            // keep a recording alive across rotation; otherwise clean up when leaving the screen
            if ((ctx as? Activity)?.isChangingConfigurations != true) vm.resetSpeak()
        }
    }

    ScreenScaffold(title = "Speak", onBack = onBack) { mod ->
        Column(mod) {
            when (ui.phase) {
                Phase.Idle -> IdleSection(
                    ui = ui, inputLang = settings.inputLang, denied = denied,
                    onLang = { l -> vm.updateSettings { it.copy(inputLang = l) } },
                    onStart = { tryStart() },
                    onSettings = { openAppSettings(ctx) },
                    onQuick = onQuick,
                )
                Phase.Recording -> RecordingSection(ui, onStop = { vm.stopRecording() })
                Phase.Recorded -> RecordedSection(vm, ui)
                Phase.Transcribing -> {
                    Spacer(Modifier.height(80.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(48.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Understanding what you said...", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Phase.Review -> ReviewSection(vm, ui, settings.effectiveOutLang, onQuick)
                Phase.Done -> DoneSection(vm, ui)
            }
        }
    }
}

@Composable
private fun IdleSection(
    ui: SpeakUi, inputLang: String, denied: Boolean,
    onLang: (String) -> Unit, onStart: () -> Unit, onSettings: () -> Unit, onQuick: () -> Unit,
) {
    LangChips(Langs.input, inputLang, onLang)
    Spacer(Modifier.height(12.dp))
    ErrorBanner(ui.error)
    if (denied) {
        Spacer(Modifier.height(8.dp))
        InfoCard {
            Text("Microphone access is off", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Allow it to record your voice, or use quick messages instead.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = onStart) { Text("Try again") }
                TextButton(onClick = onSettings) { Text("Open settings") }
            }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        RecordButton(recording = false, level = 0f, onClick = onStart, size = 176.dp)
        Text("Tap and speak", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Short sentences work best.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onQuick) { Text("Use quick messages instead") }
    }
}

@Composable
private fun RecordingSection(ui: SpeakUi, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        RecordButton(recording = true, level = ui.level, onClick = onStop, size = 176.dp)
        Text(formatDuration(ui.elapsedMs), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Recording. Tap the button when you finish.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(
            "Stops automatically after ${WavRecorder.MAX_SECONDS} seconds.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordedSection(vm: AppViewModel, ui: SpeakUi) {
    val playState by vm.previewPlayer.state.collectAsStateWithLifecycle()
    Spacer(Modifier.height(8.dp))
    InfoCard {
        Text("Recording ready", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Text("Length ${formatDuration(ui.elapsedMs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        if (playState == PlayState.Playing) {
            OutlinedButton(onClick = { vm.previewPlayer.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Stop playing") }
        } else {
            OutlinedButton(onClick = { vm.playPreview() }, modifier = Modifier.fillMaxWidth()) { Text("Listen") }
        }
    }
    Spacer(Modifier.height(12.dp))
    ErrorBanner(ui.error, onRetry = { vm.transcribe() })
    Spacer(Modifier.height(16.dp))
    BigButton("Continue", onClick = { vm.transcribe() })
    TextButton(onClick = { vm.discardRecording() }, modifier = Modifier.fillMaxWidth()) { Text("Record again") }
}

@Composable
private fun ReviewSection(vm: AppViewModel, ui: SpeakUi, outLang: String, onQuick: () -> Unit) {
    val interp = ui.interp
    val unclear = ui.intent == "other_unclear"
    var showEdit by remember { mutableStateOf(false) }
    val note = interp?.warning ?: if (interp?.level == "medium") "Not completely sure. Please check." else null
    val alts = interp?.alternatives.orEmpty().filter { it.intent != ui.intent }
    val showAlts = alts.isNotEmpty() && (interp == null || interp.level != "high" || interp.ambiguous)

    Text("We understood", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    Text(
        if (unclear) "We could not understand this clearly." else ui.meaning,
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
    )
    if (ui.transcript.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text("You said: ${ui.transcript}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (note != null) {
        Spacer(Modifier.height(10.dp))
        InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) { Text(note) }
    }
    ErrorBanner(ui.error, Modifier.padding(top = 10.dp))

    if (showAlts) {
        SectionTitle("Other meanings")
        alts.forEach { a ->
            OutlinedButton(
                onClick = { vm.chooseAlternative(a) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) { Text(a.meaning, modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }

    SectionTitle("Say it in")
    // tapping a language converts the message into that language (Marathi, Hindi or English)
    LangChips(Langs.output.filter { it.code != "hi-en" }, ui.outLang.ifBlank { outLang }, vm::setOutLang)
    Spacer(Modifier.height(8.dp))
    LabeledField(ui.message, vm::setMessage, "Message", minLines = 2)
    if (ui.note != null) {
        Spacer(Modifier.height(6.dp))
        InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) { Text(ui.note) }
    }

    TextButton(onClick = { showEdit = !showEdit }) { Text(if (showEdit) "Fewer options" else "More options") }
    if (showEdit) {
        LabeledField(ui.transcript, vm::setTranscript, "What you said")
        TextButton(onClick = { vm.reinterpret() }, enabled = !ui.busy && ui.transcript.isNotBlank()) { Text("Check meaning again") }
        Spacer(Modifier.height(4.dp))
        LabeledField(ui.meaning, vm::setMeaning, "Meaning")
        Spacer(Modifier.height(4.dp))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = ui.savePhrase, onCheckedChange = vm::setSavePhrase)
            Spacer(Modifier.width(12.dp))
            Text("Save as my phrase (off unless you turn it on)", style = MaterialTheme.typography.bodyMedium)
        }
    }

    Spacer(Modifier.height(20.dp))
    if (ui.busy) Loading()
    BigButton("Confirm and speak", onClick = { vm.confirmAndSpeak() }, enabled = !ui.busy && ui.message.isNotBlank())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { vm.resetSpeak() }) { Text("Record again") }
        if (unclear || showAlts) TextButton(onClick = onQuick) { Text("Use quick messages") }
    }
    Spacer(Modifier.height(8.dp))
    PlaybackControls(vm)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun DoneSection(vm: AppViewModel, ui: SpeakUi) {
    Spacer(Modifier.height(8.dp))
    Text("Spoken", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    Text(ui.message, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    if (ui.phraseSaved) {
        Spacer(Modifier.height(6.dp))
        Text("Saved to your phrases.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(20.dp))
    PlaybackControls(vm)
    Spacer(Modifier.height(24.dp))
    BigButton("New message", onClick = { vm.resetSpeak() })
    TextButton(onClick = { vm.backToReview() }, modifier = Modifier.fillMaxWidth()) { Text("Edit message") }
}
