@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.samajhsakhi.app.ui.*

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val items = listOf(
        "Speak a message" to "Tap the microphone, speak, then tap it again to stop. Tap Continue. Check the meaning and tap Confirm and speak.",
        "Say it in another language" to "On the Speak and Type screens, tap Marathi, Hindi or English under Say it in. The message is converted into that language and spoken in it. Ready-made messages are translated exactly. Other sentences use a free translation service (only the message text is sent) and show a reminder to check them.",
        "Emergency" to "The red Emergency button speaks an urgent message and shows a button that opens your phone's dialer with your emergency contact. It never calls by itself. Add the contact in Settings.",
        "Offline messages" to "In Settings, tap Download for offline use. The quick messages are then stored on the phone and play with no internet.",
        "Quick messages" to "Large ready-made messages. Tap one, change it if you like, pick a language and tap Speak. They work without a microphone.",
        "My phrases" to "Save words you use often. They are matched first the next time you speak. Phrases are saved only when you ask.",
        "Type and speak" to "Type a sentence in Marathi, Hindi or English, pick the language to speak it in, and tap Speak.",
        "Caregiver" to "Connect a caregiver with a one-time code. They see nothing until you switch sharing on. You can remove them at any time.",
        "Cannot connect?" to "Check that the backend is running on your laptop, that the phone and laptop use the same Wi-Fi, and that the server address in Settings is your laptop's Wi-Fi IP address.",
        "In a medical emergency" to "Call your local emergency number. Vaani is not a medical device.",
    )
    ScreenScaffold(title = "Help", onBack = onBack) { mod ->
        Column(mod) {
            items.forEach { (title, body) ->
                Column(Modifier.padding(vertical = 10.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
