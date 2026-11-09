package com.samajhsakhi.app.audio

import android.content.Context
import com.samajhsakhi.app.data.Cards
import java.io.File

/**
 * Quick messages pre-generated once with Timbre and stored on the phone, so they play instantly
 * with no internet and no API credits. Only the app's own preset sentences are stored here
 * (never recordings or personal text).
 */
class OfflineStore(private val context: Context) {
    private fun dir(lang: String) = File(context.filesDir, "offline/$lang").apply { mkdirs() }

    private fun name(cardId: String, voice: String) = "${cardId}_${voice.ifBlank { "default" }}.wav"

    fun file(lang: String, cardId: String, voice: String): File = File(dir(lang), name(cardId, voice))

    fun has(lang: String, cardId: String, voice: String): Boolean =
        file(lang, cardId, voice).let { it.exists() && it.length() > 100 }

    /** How many of the preset messages are stored for [lang] with this voice. */
    fun count(lang: String, voice: String): Int = Cards.offlineSet.count { has(lang, it.id, voice) }

    fun save(lang: String, cardId: String, voice: String, bytes: ByteArray) {
        file(lang, cardId, voice).writeBytes(bytes)
    }

    fun clear() {
        File(context.filesDir, "offline").deleteRecursively()
    }
}
