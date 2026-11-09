@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samajhsakhi.app.data.Cards
import com.samajhsakhi.app.data.Langs
import com.samajhsakhi.app.data.QuickCard
import com.samajhsakhi.app.ui.*

/** Large, easy-to-tap messages that work without the microphone. */
@Composable
fun QuickScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    var category by remember { mutableStateOf("all") }
    var lang by remember { mutableStateOf(settings.effectiveOutLang) }
    var selected by remember { mutableStateOf<QuickCard?>(null) }
    val shown = if (category == "all") Cards.all else Cards.all.filter { it.category == category }

    ScreenScaffold(title = "Quick messages", onBack = onBack, scroll = false) { mod ->
        Column(mod.padding(horizontal = 20.dp, vertical = 8.dp)) {
            LangChips(Langs.output, lang, { lang = it })
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = category == "all", onClick = { category = "all" }, label = { Text("All") }) }
                items(Cards.categories) { (id, title) ->
                    FilterChip(selected = category == id, onClick = { category = id }, label = { Text(title) })
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown) { c -> QuickCardTile(c, lang, Modifier.fillMaxWidth()) { selected = c } }
            }
        }
    }

    selected?.let { QuickMessageDialog(vm, it, lang, onDismiss = { selected = null }) }
}

/** Type any text and have it spoken. */
@Composable
fun TypeSpeakScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: return
    var text by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf(settings.effectiveOutLang.let { if (it == "hi-en") "hi-IN" else it }) }
    var spoken by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { vm.speechPlayer.stop() } }

    ScreenScaffold(title = "Type and speak", onBack = onBack) { mod ->
        Column(mod) {
            Spacer(Modifier.height(8.dp))
            LabeledField(text, { text = it; spoken = null; note = null }, "Type your message", minLines = 4)
            SectionTitle("Say it in")
            // the text is converted into the language you pick, then spoken
            LangChips(Langs.output.filter { it.code != "hi-en" }, lang, { lang = it; spoken = null; note = null })
            Spacer(Modifier.height(20.dp))
            BigButton("Speak", onClick = { vm.translateAndSpeak(text, lang) { shown, n -> spoken = shown; note = n } }, enabled = text.isNotBlank())
            if (spoken != null && spoken!!.trim() != text.trim()) {
                Spacer(Modifier.height(12.dp))
                Text("Spoken as", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(spoken!!, style = MaterialTheme.typography.titleMedium)
            }
            if (note != null) {
                Spacer(Modifier.height(8.dp))
                InfoCard(container = MaterialTheme.colorScheme.secondaryContainer) { Text(note!!) }
            }
            Spacer(Modifier.height(12.dp))
            PlaybackControls(vm)
        }
    }
}
