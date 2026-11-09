@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samajhsakhi.app.data.Langs
import com.samajhsakhi.app.data.Phrase
import com.samajhsakhi.app.ui.*

private const val CUSTOM_INTENT = "custom_phrase"

@Composable
fun PhrasesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val phrases by vm.phrases.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("all") }
    var editing by remember { mutableStateOf<Phrase?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Phrase?>(null) }

    LaunchedEffect(Unit) {
        vm.loadPhrases()
        vm.loadCatalog()
    }

    val q = query.trim().lowercase()
    val shown = phrases.filter { p ->
        (category == "all" || p.category == category) &&
            (q.isEmpty() || p.alias.lowercase().contains(q) || p.meaning.lowercase().contains(q) ||
                p.alternatives.any { it.lowercase().contains(q) })
    }
    val cats = phrases.map { it.category }.distinct()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My phrases", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnack.current) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add phrase")
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search phrases") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )
            if (cats.size > 1) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = category == "all", onClick = { category = "all" }, label = { Text("All") }) }
                    items(cats) { c ->
                        FilterChip(
                            selected = category == c, onClick = { category = c },
                            label = { Text(catalog.categories[c] ?: c.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                loading && phrases.isEmpty() -> Loading()
                phrases.isEmpty() -> EmptyState(
                    "No phrases yet",
                    "Save the words you use often, for example “Mala pani pahije”. They are matched first the next time you speak.",
                )
                shown.isEmpty() -> EmptyState("No matches", "Try a different search.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(shown, key = { it.id }) { p ->
                        InfoCard {
                            Text(p.alias, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (p.alternatives.isNotEmpty()) {
                                Text("Also: " + p.alternatives.joinToString("  •  "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(p.meaning)
                            Text(
                                "${Langs.english(p.language)}  ·  used ${p.usageCount} time${if (p.usageCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row {
                                TextButton(onClick = { vm.speakText(p.meaning.ifBlank { p.alias }, s?.effectiveOutLang ?: "hi-IN") }) { Text("Speak") }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, contentDescription = "Edit phrase") }
                                IconButton(onClick = { deleting = p }) { Icon(Icons.Default.Delete, contentDescription = "Delete phrase") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        PhraseDialog(
            vm = vm, phrase = editing,
            onDismiss = { creating = false; editing = null },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this phrase?") },
            text = { Text("“${p.alias}” will be removed from your library.") },
            confirmButton = { TextButton(onClick = { vm.deletePhrase(p.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PhraseDialog(vm: AppViewModel, phrase: Phrase?, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    var alias by remember { mutableStateOf(phrase?.alias ?: "") }
    var alts by remember { mutableStateOf(phrase?.alternatives?.joinToString("\n") ?: "") }
    var meaning by remember { mutableStateOf(phrase?.meaning ?: "") }
    var intent by remember { mutableStateOf(phrase?.intent ?: CUSTOM_INTENT) }
    var lang by remember { mutableStateOf(phrase?.language ?: s?.inputLang ?: "mr-IN") }
    var saving by remember { mutableStateOf(false) }

    val intentOptions = listOf(CUSTOM_INTENT) + catalog.intents.map { it.intent }
    fun intentLabel(i: String) = if (i == CUSTOM_INTENT) "Custom (my own meaning)" else
        catalog.intents.firstOrNull { it.intent == i }?.meaning ?: i

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (phrase == null) "Add phrase" else "Edit phrase") },
        text = {
            Column(Modifier.verticalScrollCompat()) {
                LabeledField(alias, { alias = it }, "Phrase (what you say)", singleLine = true)
                Spacer(Modifier.height(8.dp))
                LabeledField(alts, { alts = it }, "Other ways you say it (one per line)", minLines = 2)
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = "Intent", current = intentLabel(intent), options = intentOptions, optionLabel = { intentLabel(it) },
                    onSelect = { i ->
                        val previous = catalog.intents.firstOrNull { it.intent == intent }?.meaning
                        intent = i
                        val picked = catalog.intents.firstOrNull { it.intent == i }?.meaning
                        if (picked != null && (meaning.isBlank() || meaning == previous)) meaning = picked
                    },
                )
                Spacer(Modifier.height(8.dp))
                LabeledField(meaning, { meaning = it }, "Meaning (what it should communicate)", minLines = 2)
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = "Language", current = Langs.english(lang), options = Langs.input,
                    optionLabel = { it.english }, onSelect = { lang = it.code },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && alias.isNotBlank() && meaning.isNotBlank(),
                onClick = {
                    saving = true
                    val list = alts.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }
                    vm.savePhrase(phrase?.id, alias.trim(), list, meaning.trim(), intent, lang) { onDismiss() }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
