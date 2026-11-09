package com.samajhsakhi.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "samajh_settings")

/** Local preferences (language, profile, server address, session token). */
class Prefs(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val inputLang = stringPreferencesKey("input_lang")
        val outputLang = stringPreferencesKey("output_lang")
        val serverUrl = stringPreferencesKey("server_url")
        val accessCode = stringPreferencesKey("access_code")
        val token = stringPreferencesKey("token")
        val userId = stringPreferencesKey("user_id")
        val speed = floatPreferencesKey("speed")
        val voice = stringPreferencesKey("voice")
        val highContrast = booleanPreferencesKey("high_contrast")
        val cardUse = stringPreferencesKey("card_use")
    }

    val flow: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p: Preferences ->
            val d = AppSettings()
            AppSettings(
                onboarded = p[K.onboarded] ?: d.onboarded,
                name = p[K.name] ?: d.name,
                inputLang = p[K.inputLang] ?: d.inputLang,
                outputLang = p[K.outputLang] ?: d.outputLang,
                serverUrl = p[K.serverUrl] ?: d.serverUrl,
                accessCode = p[K.accessCode] ?: d.accessCode,
                token = p[K.token] ?: d.token,
                userId = p[K.userId] ?: d.userId,
                speed = p[K.speed] ?: d.speed,
                voice = p[K.voice] ?: d.voice,
                highContrast = p[K.highContrast] ?: d.highContrast,
                cardUse = p[K.cardUse] ?: d.cardUse,
            )
        }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[K.onboarded] = s.onboarded
            p[K.name] = s.name
            p[K.inputLang] = s.inputLang
            p[K.outputLang] = s.outputLang
            p[K.serverUrl] = s.serverUrl
            p[K.accessCode] = s.accessCode
            p[K.token] = s.token
            p[K.userId] = s.userId
            p[K.speed] = s.speed
            p[K.voice] = s.voice
            p[K.highContrast] = s.highContrast
            p[K.cardUse] = s.cardUse
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
