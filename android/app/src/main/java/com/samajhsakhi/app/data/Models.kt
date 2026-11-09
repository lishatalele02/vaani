package com.samajhsakhi.app.data

import com.samajhsakhi.app.BuildConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AppSettings(
    val onboarded: Boolean = false,
    val name: String = "",
    val inputLang: String = "mr-IN",
    /** "" means "same as the language I speak". */
    val outputLang: String = "",
    val serverUrl: String = BuildConfig.DEFAULT_SERVER_URL,
    val accessCode: String = "",
    val token: String = "",
    val userId: String = "",
    val speed: Float = 1.0f,
    val voice: String = "",
    val highContrast: Boolean = false,
    /** Quick-card usage counts, encoded "id:count,id:count" (see Cards.bump). */
    val cardUse: String = "",
) {
    /** Language of the spoken/communicated message (TTS + suggested wording). */
    val effectiveOutLang: String
        get() = when {
            outputLang.isNotBlank() -> outputLang
            inputLang == "hi-en" -> "hi-IN"
            else -> inputLang
        }
}

data class Lang(val code: String, val english: String, val native: String)

object Langs {
    val input = listOf(
        Lang("mr-IN", "Marathi", "मराठी"),
        Lang("hi-IN", "Hindi", "हिन्दी"),
        Lang("en-IN", "English", "English"),
        Lang("hi-en", "Hinglish", "Hinglish"),
    )
    val output = listOf(
        Lang("mr-IN", "Marathi", "मराठी"),
        Lang("hi-IN", "Hindi", "हिन्दी"),
        Lang("en-IN", "English", "English"),
    )

    fun english(code: String): String =
        (input + output).firstOrNull { it.code == code }?.english ?: code

    /** 'mr-IN' -> 'mr', 'hi-en' -> 'hi' (matches the backend catalog). */
    fun short(code: String): String = when {
        code.startsWith("mr") -> "mr"
        code.startsWith("en") -> "en"
        else -> "hi"
    }
}

data class Alt(
    val intent: String,
    val meaning: String,
    val message: String,
    val confidence: Double,
)

data class Interpretation(
    val transcript: String,
    val language: String,
    val outputLanguage: String,
    val intent: String,
    val meaning: String,
    val suggestedMessage: String,
    val confidence: Double,
    val level: String,
    val source: String,
    val negated: Boolean,
    val alreadyDone: Boolean,
    val ambiguous: Boolean,
    val matchedPhraseId: Int?,
    val alternatives: List<Alt>,
    val warning: String?,
)

data class TranscribeResult(val transcript: String, val interpretation: Interpretation)

/** [method]: same | catalog | machine | closest | failed. [note] is a short warning to show, if any. */
data class TranslateResult(val text: String, val method: String, val note: String?)

/** How a message is spoken. Non-normal styles use Timbre v3.0 audio tags on the server. */
enum class SpeakStyle(val code: String, val label: String) {
    Normal("normal", "Normal"),
    Comfort("comfort", "Soft"),
    Urgent("urgent", "Urgent"),
    SlowClear("slow_clear", "Slow and clear"),
}

data class ConfirmResult(val phraseSaved: Boolean, val historyId: Int?)

data class Phrase(
    val id: Int,
    val alias: String,
    val alternatives: List<String>,
    val meaning: String,
    val intent: String,
    val language: String,
    val category: String,
    val usageCount: Int,
)

data class HistoryItem(
    val id: Int,
    val transcript: String,
    val language: String,
    val meaning: String,
    val intent: String,
    val finalText: String,
    val outputLanguage: String,
    val shared: Boolean,
    val createdAt: String,
)

data class IntentInfo(
    val intent: String,
    val category: String,
    val meaning: String,
    val messages: Map<String, String>,
)

data class CatalogData(
    val categories: Map<String, String> = emptyMap(),
    val intents: List<IntentInfo> = emptyList(),
)

data class CareLink(
    val id: Int,
    val caregiverName: String,
    val sharePhrases: Boolean,
    val shareHistory: Boolean,
    val shareEmergency: Boolean,
)

data class CarePerson(
    val linkId: Int,
    val userId: String,
    val name: String,
    val sharePhrases: Boolean,
    val shareHistory: Boolean,
    val shareEmergency: Boolean,
)

data class SharedMessage(val id: Int, val text: String, val meaning: String, val createdAt: String)

data class EmergencyContact(val name: String, val phone: String)

data class Profile(
    val displayName: String,
    val emergencyName: String,
    val emergencyPhone: String,
    val saveHistory: Boolean,
)

data class PairCode(val code: String, val expiresAt: String)

fun prettyTime(iso: String): String = try {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))
} catch (e: Exception) {
    iso
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
