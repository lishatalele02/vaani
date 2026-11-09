@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samajhsakhi.app.data.Cards
import com.samajhsakhi.app.data.Langs
import com.samajhsakhi.app.ui.*

/**
 * One-tap emergency screen: big text, an urgent voice (Timbre <shouting>), and a button that
 * OPENS the phone dialer with the emergency contact's number. It never places a call by itself.
 */
@Composable
fun EmergencyScreen(vm: AppViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val settings = s ?: return
    var lang by remember { mutableStateOf(settings.effectiveOutLang) }
    val card = Cards.emergency

    LaunchedEffect(Unit) { vm.loadProfile() }
    // speak as soon as the screen opens (the person tapped Emergency on purpose) and again if the language changes
    LaunchedEffect(lang) { vm.speakEmergency(lang) }
    DisposableEffect(Unit) { onDispose { vm.speechPlayer.stop() } }

    val phone = profile?.emergencyPhone.orEmpty().trim()
    val contact = profile?.emergencyName.orEmpty().trim()

    ScreenScaffold(title = "Emergency", onBack = onBack) { mod ->
        Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            Text(
                card.text(lang),
                fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = card.text(lang) },
            )
            Spacer(Modifier.height(16.dp))
            LangChips(Langs.output, lang, { lang = it })
            Spacer(Modifier.height(20.dp))
            PlaybackControls(vm)
            Spacer(Modifier.height(8.dp))
            BigButton("Say it again", onClick = { vm.speakEmergency(lang) })
            Spacer(Modifier.height(16.dp))

            if (phone.isNotBlank()) {
                Button(
                    onClick = {
                        // ACTION_DIAL only opens the dialer with the number filled in; the person presses call.
                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .semantics { contentDescription = "Open the phone dialer for ${contact.ifBlank { "your emergency contact" }}" },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White),
                ) {
                    Text(
                        "Open dialer: ${contact.ifBlank { phone }}",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "This opens your dialer. You press the call button yourself.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                InfoCard {
                    Text("No emergency contact saved", fontWeight = FontWeight.SemiBold)
                    Text("Add a name and number in Settings so a call button appears here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onSettings) { Text("Open Settings") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "For a medical emergency call your local emergency number. Vaani is not a medical device.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
