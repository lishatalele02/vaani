package com.samajhsakhi.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samajhsakhi.app.audio.AudioPlayer
import com.samajhsakhi.app.audio.OfflineStore
import com.samajhsakhi.app.audio.WavRecorder
import com.samajhsakhi.app.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class Phase { Idle, Recording, Recorded, Transcribing, Review, Done }

/** State of the main "speak -> understand -> confirm -> talk" flow. */
data class SpeakUi(
    val phase: Phase = Phase.Idle,
    val elapsedMs: Long = 0,
    val level: Float = 0f,
    val recFile: File? = null,
    val transcript: String = "",
    val interp: Interpretation? = null,
    val intent: String = "",
    val meaning: String = "",
    val message: String = "",
    val savePhrase: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val phraseSaved: Boolean = false,
    /** Language the message will be spoken in (the chips on the review screen). */
    val outLang: String = "",
    /** Short warning about the last translation, e.g. "Machine translation. Please check it." */
    val note: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    val settings: StateFlow<AppSettings?> =
        prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val cur: AppSettings get() = settings.value ?: AppSettings()

    private val api = ApiClient { cur }
    private val cacheDir: File get() = getApplication<Application>().cacheDir

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

    val serverOk = MutableStateFlow<Boolean?>(null)

    // audio
    private val recorder = WavRecorder(app)
    val previewPlayer = AudioPlayer()
    val speechPlayer = AudioPlayer()
    val speechLoading = MutableStateFlow(false)
    private val offline = OfflineStore(app)

    /** (done, total) while quick messages are being stored for offline use; null otherwise. */
    val offlineProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val offlineCount = MutableStateFlow(0)

    // data
    private val _phrases = MutableStateFlow<List<Phrase>>(emptyList())
    val phrases: StateFlow<List<Phrase>> = _phrases.asStateFlow()
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()
    private val _catalog = MutableStateFlow(CatalogData())
    val catalog: StateFlow<CatalogData> = _catalog.asStateFlow()
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()
    private val _voices = MutableStateFlow<List<String>>(emptyList())
    val voices: StateFlow<List<String>> = _voices.asStateFlow()
    val loading = MutableStateFlow(false)

    private val _speak = MutableStateFlow(SpeakUi())
    val speak: StateFlow<SpeakUi> = _speak.asStateFlow()

    private var timerJob: Job? = null
    private var lastSpeech: File? = null
    private var lastSpeechKey = ""

    init {
        // privacy: remove leftover audio from earlier sessions
        cacheDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("recording_") || f.name.startsWith("speech_")) f.delete()
        }
    }

    // ------------------------------------------------------------ helpers
    fun errorText(e: Throwable): String =
        (e as? ApiException)?.message ?: "Something went wrong. Please try again."

    private suspend fun registerNow() {
        val s = cur
        val (uid, tok) = api.register(s.name, s.inputLang, s.accessCode)
        prefs.save(s.copy(userId = uid, token = tok))
        settings.first { it?.token == tok }
    }

    /** Runs [block]; signs the user up first if needed and once more if the session expired. */
    private suspend fun <T> authed(block: suspend () -> T): T {
        if (cur.token.isBlank()) registerNow()
        return try {
            block()
        } catch (e: ApiException) {
            if (e.code == "unauthorized") {
                registerNow()
                block()
            } else throw e
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { prefs.save(transform(cur)) }
    }

    fun checkServer() {
        viewModelScope.launch { serverOk.value = api.health() }
    }

    /** Waits for freshly saved settings to be applied, then pings the server. */
    fun checkServerSoon() {
        viewModelScope.launch {
            serverOk.value = null
            delay(400)
            serverOk.value = api.health()
        }
    }

    fun refreshAll() {
        loadCatalog()
    }

    // ------------------------------------------------------------ onboarding
    fun finishOnboarding(name: String, inputLang: String, outputLang: String, serverUrl: String, accessCode: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val s = cur.copy(
                onboarded = true, name = name.trim(), inputLang = inputLang, outputLang = outputLang,
                serverUrl = serverUrl.trim().ifBlank { cur.serverUrl }, accessCode = accessCode.trim(),
            )
            prefs.save(s)
            settings.first { it?.onboarded == true }
            onDone()
            try {
                authed { Unit }
                refreshAll()
            } catch (e: Exception) {
                toast("Saved. Could not reach the server yet: ${errorText(e)}")
            }
        }
    }

    // ------------------------------------------------------------ main speak flow
    fun startRecording() {
        if (_speak.value.phase == Phase.Recording) return
        speechPlayer.stop()
        previewPlayer.stop()
        _speak.value.recFile?.delete()
        try {
            recorder.start(
                onLevel = { lvl -> _speak.update { it.copy(level = lvl) } },
                onLimit = { stopRecording() },
            )
        } catch (e: IllegalStateException) {
            _speak.value = SpeakUi(error = e.message ?: "The microphone is not available.")
            return
        }
        _speak.value = SpeakUi(phase = Phase.Recording)
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val t0 = SystemClock.elapsedRealtime()
            while (isActive) {
                _speak.update { it.copy(elapsedMs = SystemClock.elapsedRealtime() - t0) }
                delay(100)
            }
        }
    }

    fun stopRecording() {
        if (_speak.value.phase != Phase.Recording) return
        timerJob?.cancel()
        val res = recorder.stop()
        when {
            res == null || res.durationMs < 600 -> {
                res?.file?.delete()
                _speak.value = SpeakUi(error = "That was too short. Tap the microphone, speak, then tap stop.")
            }
            res.peak < 0.004f -> {
                res.file.delete()
                _speak.value = SpeakUi(error = "We could not hear anything. Please speak closer to the microphone and try again.")
            }
            else -> _speak.update {
                it.copy(phase = Phase.Recorded, recFile = res.file, elapsedMs = res.durationMs, level = 0f, error = null)
            }
        }
    }

    fun cancelRecording() {
        timerJob?.cancel()
        recorder.cancel()
    }

    fun playPreview() {
        _speak.value.recFile?.let { previewPlayer.play(it) }
    }

    fun discardRecording() {
        previewPlayer.stop()
        _speak.value.recFile?.delete()
        _speak.value = SpeakUi()
    }

    fun resetSpeak() {
        timerJob?.cancel()
        recorder.cancel()
        previewPlayer.stop()
        speechPlayer.stop()
        _speak.value.recFile?.delete()
        _speak.value = SpeakUi()
    }

    fun transcribe() {
        val file = _speak.value.recFile ?: return
        previewPlayer.stop()
        viewModelScope.launch {
            _speak.update { it.copy(phase = Phase.Transcribing, error = null) }
            try {
                val s = cur
                val r = authed { api.transcribe(file, s.inputLang, s.effectiveOutLang) }
                file.delete() // privacy: raw audio is not kept after upload
                applyInterpretation(r.transcript, r.interpretation)
            } catch (e: Exception) {
                _speak.update { it.copy(phase = Phase.Recorded, error = errorText(e)) }
            }
        }
    }

    private fun applyInterpretation(transcript: String, i: Interpretation) {
        _speak.update {
            it.copy(
                phase = Phase.Review, recFile = null, transcript = transcript, interp = i,
                outLang = i.outputLanguage.ifBlank { cur.effectiveOutLang }, note = null,
                intent = i.intent, meaning = i.meaning, message = i.suggestedMessage,
                error = null, busy = false, savePhrase = false, phraseSaved = false,
            )
        }
    }

    fun setTranscript(t: String) = _speak.update { it.copy(transcript = t) }
    fun setMessage(t: String) = _speak.update { it.copy(message = t) }
    fun setMeaning(t: String) = _speak.update { it.copy(meaning = t) }
    fun setSavePhrase(b: Boolean) = _speak.update { it.copy(savePhrase = b) }

    /** Tapping a language chip converts the message into that language (Marathi, Hindi or English). */
    fun setOutLang(code: String) {
        _speak.update { it.copy(outLang = code) }
        translateMessage(code)
    }

    private fun translateMessage(code: String) {
        val ui = _speak.value
        val text = ui.message.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _speak.update { it.copy(busy = true, error = null, note = null) }
            try {
                val r = authed { api.translate(text, code, ui.intent) }
                _speak.update {
                    when {
                        it.outLang != code -> it.copy(busy = false)            // the person tapped another language meanwhile
                        r.method == "failed" -> it.copy(busy = false, error = r.note)
                        else -> it.copy(busy = false, message = r.text, note = r.note)
                    }
                }
            } catch (e: Exception) {
                _speak.update { it.copy(busy = false, error = errorText(e)) }
            }
        }
    }

    /** Type and speak: converts [text] into [lang] first, then speaks it. [onShown] receives what is actually spoken. */
    fun translateAndSpeak(text: String, lang: String, onShown: (String, String?) -> Unit) {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            try {
                val r = authed { api.translate(t, lang, "") }
                if (r.method == "failed") {
                    onShown(t, r.note)
                    return@launch
                }
                onShown(r.text, r.note)
                speakText(r.text, lang, record = true)
            } catch (e: Exception) {
                onShown(t, errorText(e))
            }
        }
    }

    fun chooseAlternative(a: Alt) {
        _speak.update { it.copy(intent = a.intent, meaning = a.meaning, message = a.message) }
        translateMessage(_speak.value.outLang.ifBlank { cur.effectiveOutLang })
    }

    /** Re-run interpretation after the user edits the recognised text. */
    fun reinterpret() {
        val text = _speak.value.transcript.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _speak.update { it.copy(busy = true, error = null) }
            try {
                val s = cur
                val i = authed { api.interpret(text, s.inputLang, _speak.value.outLang.ifBlank { s.effectiveOutLang }) }
                applyInterpretation(text, i)
            } catch (e: Exception) {
                _speak.update { it.copy(busy = false, error = errorText(e)) }
            }
        }
    }

    fun confirmAndSpeak() {
        val ui = _speak.value
        val interp = ui.interp ?: return
        val text = ui.message.trim()
        if (text.isEmpty()) {
            toast("Please check or type the message first.")
            return
        }
        val s = cur
        val outLang = ui.outLang.ifBlank { interp.outputLanguage.ifBlank { s.effectiveOutLang } }
        val inLang = s.inputLang
        viewModelScope.launch {
            _speak.update { it.copy(busy = true, error = null) }
            try {
                speakSuspend(text, outLang)
                val res = authed {
                    api.confirm(
                        transcript = ui.transcript, intent = ui.intent.ifBlank { "custom_phrase" },
                        meaning = ui.meaning.ifBlank { text }, lang = inLang, savePhrase = ui.savePhrase,
                        finalText = text, outLang = outLang, matchedPhraseId = interp.matchedPhraseId,
                    )
                }
                _speak.update { it.copy(phase = Phase.Done, busy = false, phraseSaved = res.phraseSaved) }
                loadHistory()
                if (res.phraseSaved) loadPhrases()
            } catch (e: Exception) {
                _speak.update { it.copy(busy = false, error = errorText(e)) }
            }
        }
    }

    fun backToReview() = _speak.update { it.copy(phase = Phase.Review) }

    // ------------------------------------------------------------ text to speech
    private suspend fun speakSuspend(text: String, lang: String, style: String = "normal") {
        speechLoading.value = true
        try {
            val s = cur
            val key = "$text|$lang|${s.voice}|${s.speed}|$style"
            val existing = lastSpeech
            val file = if (key == lastSpeechKey && existing != null && existing.exists()) {
                existing
            } else {
                val bytes = authed { api.speak(text, lang, s.speed, s.voice.ifBlank { null }, style) }
                speechPlayer.stop()
                lastSpeech?.delete()
                val f = File(cacheDir, "speech_${System.currentTimeMillis()}.wav")
                f.writeBytes(bytes)
                lastSpeech = f
                lastSpeechKey = key
                f
            }
            speechPlayer.play(file)
        } finally {
            speechLoading.value = false
        }
    }

    /** Speak any text (quick cards, typed text, history replay). Optionally log it in history. */
    fun speakText(
        text: String, lang: String, record: Boolean = false, meaning: String = "", intent: String = "",
        style: String = "normal",
    ) {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            try {
                speakSuspend(t, lang, style)
                if (record) {
                    authed { api.addHistory(t, meaning, intent, cur.inputLang, lang) }
                    loadHistory()
                }
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    // ------------------------------------------------------------ quick cards, offline voice, emergency
    /**
     * Speaks a quick card. If the text is unchanged and the card is stored on the phone, it plays
     * from storage instantly with no internet and no credits; otherwise Timbre is used.
     */
    fun playQuickCard(card: QuickCard, text: String, lang: String, style: String = "normal") {
        val s = cur
        updateSettings { it.copy(cardUse = Cards.bump(it.cardUse, card.id)) }
        val stored = offline.file(lang, card.id, s.voice)
        if (text.trim() == card.text(lang) && offline.has(lang, card.id, s.voice)) {
            previewPlayer.stop()
            speechPlayer.play(stored)
            viewModelScope.launch { // history is best effort: it must not fail when there is no internet
                try {
                    authed { api.addHistory(text.trim(), card.en, card.intent, s.inputLang, lang) }
                    loadHistory()
                } catch (e: Exception) { /* offline */ }
            }
        } else {
            speakText(text, lang, record = true, meaning = card.en, intent = card.intent, style = style)
        }
    }

    fun refreshOfflineCount() {
        val s = cur
        offlineCount.value = offline.count(s.effectiveOutLang, s.voice)
    }

    /** Pre-generates every quick message (and the emergency message) in [lang] and stores it. */
    fun downloadOffline(lang: String) {
        if (offlineProgress.value != null) return
        val voice = cur.voice
        val cards = Cards.offlineSet
        viewModelScope.launch {
            var done = 0
            offlineProgress.value = 0 to cards.size
            try {
                for (c in cards) {
                    if (!offline.has(lang, c.id, voice)) {
                        val style = if (c.id == Cards.emergency.id) SpeakStyle.Urgent.code else "normal"
                        val bytes = authed { api.speak(c.text(lang), lang, 1.0f, voice.ifBlank { null }, style) }
                        offline.save(lang, c.id, voice, bytes)
                    }
                    done++
                    offlineProgress.value = done to cards.size
                }
                toast("Stored ${cards.size} messages for offline use.")
            } catch (e: Exception) {
                toast("Stopped after $done of ${cards.size}: ${errorText(e)}")
            } finally {
                offlineProgress.value = null
                refreshOfflineCount()
            }
        }
    }

    fun clearOffline() {
        offline.clear()
        offlineCount.value = 0
        toast("Offline messages removed.")
    }

    /** One-tap emergency message, spoken urgently. The dialer is opened by the screen, never automatically. */
    fun speakEmergency(lang: String) {
        val c = Cards.emergency
        playQuickCard(c, c.text(lang), lang, SpeakStyle.Urgent.code)
    }

    // ------------------------------------------------------------ catalog / phrases / history
    fun loadCatalog() {
        viewModelScope.launch {
            try {
                _catalog.value = authed { api.catalog() }
                if (_voices.value.isEmpty()) _voices.value = authed { api.voices() }
            } catch (e: Exception) { /* optional data */ }
        }
    }

    fun loadPhrases() {
        viewModelScope.launch {
            loading.value = true
            try {
                _phrases.value = authed { api.phrases() }
            } catch (e: Exception) {
                toast(errorText(e))
            } finally {
                loading.value = false
            }
        }
    }

    fun savePhrase(id: Int?, alias: String, alts: List<String>, meaning: String, intent: String, lang: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                authed {
                    if (id == null) api.addPhrase(alias, alts, meaning, intent, lang)
                    else api.updatePhrase(id, alias, alts, meaning, intent, lang)
                }
                loadPhrases()
                onDone()
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    fun deletePhrase(id: Int) {
        viewModelScope.launch {
            try {
                authed { api.deletePhrase(id) }
                loadPhrases()
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    fun loadHistory(query: String = "") {
        viewModelScope.launch {
            try {
                _history.value = authed { api.history(query) }
            } catch (e: Exception) {
                if (query.isNotBlank()) toast(errorText(e))
            }
        }
    }

    fun deleteHistory(id: Int) {
        viewModelScope.launch {
            try {
                authed { api.deleteHistory(id) }
                loadHistory()
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                authed { api.clearHistory() }
                _history.value = emptyList()
                toast("History cleared.")
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    fun shareHistory(id: Int, shared: Boolean) {
        viewModelScope.launch {
            try {
                authed { api.shareHistory(id, shared) }
                loadHistory()
                toast(if (shared) "Shared with your caregivers." else "No longer shared.")
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    // ------------------------------------------------------------ profile / privacy
    fun loadProfile() {
        viewModelScope.launch {
            try {
                _profile.value = authed { api.profile() }
            } catch (e: Exception) { /* shown as empty */ }
        }
    }

    fun saveProfile(name: String? = null, emergencyName: String? = null, emergencyPhone: String? = null, saveHistory: Boolean? = null) {
        viewModelScope.launch {
            try {
                authed { api.updateProfile(name, emergencyName, emergencyPhone, saveHistory) }
                if (name != null) updateSettings { it.copy(name = name) }
                loadProfile()
                toast("Saved.")
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    fun deleteEverything(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                authed { api.deleteAccount() }
                resetSpeak()
                prefs.clear()
                _phrases.value = emptyList()
                _history.value = emptyList()
                _profile.value = null
                onDone()
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    // ------------------------------------------------------------ caregiver
    val pairCode = MutableStateFlow<PairCode?>(null)
    val links = MutableStateFlow<List<CareLink>>(emptyList())
    val people = MutableStateFlow<List<CarePerson>>(emptyList())

    fun loadCaregiver() {
        viewModelScope.launch {
            try {
                links.value = authed { api.links() }
                people.value = authed { api.people() }
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    fun createCode() {
        viewModelScope.launch {
            try {
                pairCode.value = authed { api.createPairCode() }
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    fun pairWith(code: String) {
        viewModelScope.launch {
            try {
                val name = authed { api.pair(code) }
                toast("Connected with $name. They must allow sharing before you can see anything.")
                loadCaregiver()
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    fun updateLink(l: CareLink, phrases: Boolean, history: Boolean, emergency: Boolean) {
        viewModelScope.launch {
            try {
                authed { api.updateLink(l.id, phrases, history, emergency) }
                loadCaregiver()
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    fun removeLink(id: Int) {
        viewModelScope.launch {
            try {
                authed { api.removeLink(id) }
                loadCaregiver()
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    fun leavePerson(userId: String) {
        viewModelScope.launch {
            try {
                authed { api.leavePerson(userId) }
                loadCaregiver()
            } catch (e: Exception) {
                toast(errorText(e))
            }
        }
    }

    /** Loads one kind of shared data for a person; returns the user-facing text via callback. */
    fun loadPersonData(p: CarePerson, kind: String, onResult: (List<String>, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val lines = when (kind) {
                    "messages" -> authed { api.personMessages(p.userId) }.map { "${prettyTime(it.createdAt)}  ·  ${it.text}" }
                    "phrases" -> authed { api.personPhrases(p.userId) }.map { "${it.alias}  -  ${it.meaning}" }
                    else -> authed { api.personEmergency(p.userId) }.let { listOf("${it.name.ifBlank { "(no name)" }}  ·  ${it.phone.ifBlank { "(no number)" }}") }
                }
                onResult(lines, null)
            } catch (e: Exception) {
                onResult(emptyList(), errorText(e))
            }
        }
    }

    override fun onCleared() {
        recorder.cancel()
        previewPlayer.release()
        speechPlayer.release()
        super.onCleared()
    }
}
