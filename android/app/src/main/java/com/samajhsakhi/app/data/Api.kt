package com.samajhsakhi.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error with a message that is safe to show to the user. */
class ApiException(val code: String, message: String) : Exception(message)

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * All network calls to the Vaani backend. The app never talks to Gnani directly:
 * API keys stay on the server.
 */
class ApiClient(private val settings: () -> AppSettings) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(): String {
        var u = settings().serverUrl.trim()
        if (u.isEmpty()) throw ApiException("bad_url", "Please enter the server address in Settings.")
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
        return if (u.endsWith("/")) u else "$u/"
    }

    private suspend fun <T> call(
        method: String,
        path: String,
        json: JSONObject? = null,
        multipart: RequestBody? = null,
        auth: Boolean = true,
        extraHeaders: Map<String, String> = emptyMap(),
        parse: (ByteArray, String) -> T,
    ): T = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder().url(baseUrl() + path.trimStart('/'))
            if (auth) {
                val token = settings().token
                if (token.isBlank()) throw ApiException("unauthorized", "Please sign in again.")
                builder.header("Authorization", "Bearer $token")
            }
            extraHeaders.forEach { (k, v) -> builder.header(k, v) }
            val body: RequestBody? = when {
                multipart != null -> multipart
                json != null -> json.toString().toRequestBody(JSON_TYPE)
                method == "POST" || method == "PUT" -> "".toRequestBody(null)
                else -> null
            }
            builder.method(method, body)
            http.newCall(builder.build()).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                if (!resp.isSuccessful) throw toApiException(resp.code, bytes)
                parse(bytes, resp.header("Content-Type") ?: "")
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw ApiException("bad_url", "The server address looks wrong. Check Settings.")
        } catch (e: IOException) {
            throw ApiException("network", "Cannot reach the server. Check Wi-Fi and the server address in Settings.")
        }
    }

    private fun toApiException(status: Int, body: ByteArray): ApiException {
        try {
            val text = String(body, Charsets.UTF_8)
            val detail = JSONObject(text).opt("detail")
            if (detail is JSONObject) {
                return ApiException(detail.optString("code", "error"), detail.optString("message", "Something went wrong."))
            }
        } catch (e: Exception) {
        }
        val msg = when (status) {
            401 -> "Please sign in again."
            413 -> "The recording is too large."
            422 -> "The request was not valid."
            in 500..599 -> "The server had a problem. Please try again."
            else -> "Something went wrong (code $status)."
        }
        return ApiException(if (status == 401) "unauthorized" else "http_$status", msg)
    }

    private fun obj(b: ByteArray) = JSONObject(String(b, Charsets.UTF_8))
    private fun arr(b: ByteArray) = JSONArray(String(b, Charsets.UTF_8))

    // ------------------------------------------------------------ session
    suspend fun health(): Boolean = try {
        call("GET", "health", auth = false) { b, _ -> obj(b).optString("status") == "ok" }
    } catch (e: Exception) {
        false
    }

    suspend fun register(name: String, lang: String, accessCode: String): Pair<String, String> {
        val headers = if (accessCode.isNotBlank()) mapOf("X-Registration-Code" to accessCode) else emptyMap()
        val body = JSONObject().put("display_name", name).put("preferred_language", lang)
        return call("POST", "api/users/register", json = body, auth = false, extraHeaders = headers) { b, _ ->
            val o = obj(b)
            o.getString("user_id") to o.getString("token")
        }
    }

    suspend fun profile(): Profile = call("GET", "api/users/me") { b, _ ->
        val o = obj(b)
        Profile(o.optString("display_name"), o.optString("emergency_name"), o.optString("emergency_phone"), o.optBoolean("save_history", true))
    }

    suspend fun updateProfile(name: String? = null, emergencyName: String? = null, emergencyPhone: String? = null, saveHistory: Boolean? = null) {
        val o = JSONObject()
        name?.let { o.put("display_name", it) }
        emergencyName?.let { o.put("emergency_name", it) }
        emergencyPhone?.let { o.put("emergency_phone", it) }
        saveHistory?.let { o.put("save_history", it) }
        call("PUT", "api/users/me", json = o) { _, _ -> Unit }
    }

    suspend fun deleteAccount() = call("DELETE", "api/users/me") { _, _ -> Unit }

    suspend fun catalog(): CatalogData = call("GET", "api/intents") { b, _ ->
        val o = obj(b)
        val cats = mutableMapOf<String, String>()
        o.optJSONObject("categories")?.let { c -> c.keys().forEach { k -> cats[k] = c.getString(k) } }
        val list = mutableListOf<IntentInfo>()
        o.optJSONArray("intents")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.getJSONObject(i)
                val m = x.optJSONObject("message")
                val msgs = mutableMapOf<String, String>()
                m?.keys()?.forEach { k -> msgs[k] = m.getString(k) }
                list += IntentInfo(x.getString("intent"), x.optString("category"), x.optString("meaning"), msgs)
            }
        }
        CatalogData(cats, list)
    }

    // ------------------------------------------------------------ speech
    suspend fun transcribe(file: File, lang: String, outLang: String): TranscribeResult {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("language_code", lang)
            .addFormDataPart("output_language", outLang)
            .addFormDataPart("audio", "recording.wav", file.asRequestBody("audio/wav".toMediaType()))
            .build()
        return call("POST", "api/speech/transcribe", multipart = body) { b, _ ->
            val o = obj(b)
            TranscribeResult(o.optString("transcript"), parseInterpretation(o.getJSONObject("interpretation")))
        }
    }

    suspend fun interpret(text: String, lang: String, outLang: String): Interpretation {
        val body = JSONObject().put("text", text).put("language", lang).put("output_language", outLang)
        return call("POST", "api/speech/interpret", json = body) { b, _ -> parseInterpretation(obj(b)) }
    }

    /** Converts [text] into Marathi, Hindi or English ([target] is mr-IN, hi-IN or en-IN). */
    suspend fun translate(text: String, target: String, intent: String): TranslateResult {
        val body = JSONObject().put("text", text).put("target_language", target).put("intent", intent)
        return call("POST", "api/translate", json = body) { b, _ ->
            val o = obj(b)
            TranslateResult(o.optString("text"), o.optString("method"), if (o.isNull("note")) null else o.optString("note").ifBlank { null })
        }
    }

    suspend fun speak(text: String, lang: String, speed: Float, voice: String?, style: String = "normal"): ByteArray {
        val body = JSONObject().put("text", text).put("language", lang).put("speed", speed.toDouble())
            .put("style", style)
        if (!voice.isNullOrBlank()) body.put("voice", voice)
        return call("POST", "api/speech/speak", json = body) { b, type ->
            if (!type.startsWith("audio")) throw ApiException("bad_audio", "The server did not return audio.")
            b
        }
    }

    suspend fun voices(): List<String> = call("GET", "api/speech/voices") { b, _ ->
        val a = obj(b).optJSONArray("voices")
        List(a?.length() ?: 0) { i -> a!!.getString(i) }
    }

    suspend fun confirm(
        transcript: String, intent: String, meaning: String, lang: String,
        savePhrase: Boolean, finalText: String, outLang: String, matchedPhraseId: Int?,
    ): ConfirmResult {
        val body = JSONObject()
            .put("transcript", transcript)
            .put("intent", intent)
            .put("canonical_meaning", meaning)
            .put("language", lang)
            .put("save_as_phrase", savePhrase)
            .put("final_text", finalText)
            .put("output_language", outLang)
        if (matchedPhraseId != null) body.put("matched_phrase_id", matchedPhraseId)
        return call("POST", "api/interpret/confirm", json = body) { b, _ ->
            val o = obj(b)
            ConfirmResult(o.optBoolean("phrase_saved"), if (o.isNull("history_id")) null else o.optInt("history_id"))
        }
    }

    // ------------------------------------------------------------ phrases
    private fun parsePhrase(x: JSONObject): Phrase {
        val alts = x.optJSONArray("alternatives")
        return Phrase(
            id = x.getInt("id"),
            alias = x.getString("alias"),
            alternatives = List(alts?.length() ?: 0) { i -> alts!!.getString(i) },
            meaning = x.optString("canonical_meaning"),
            intent = x.optString("intent"),
            language = x.optString("language"),
            category = x.optString("category", "custom"),
            usageCount = x.optInt("usage_count"),
        )
    }

    suspend fun phrases(): List<Phrase> = call("GET", "api/phrases") { b, _ ->
        val a = arr(b)
        List(a.length()) { i -> parsePhrase(a.getJSONObject(i)) }
    }

    private fun phraseBody(alias: String, alts: List<String>, meaning: String, intent: String, lang: String) =
        JSONObject().put("alias", alias).put("alternatives", JSONArray(alts))
            .put("canonical_meaning", meaning).put("intent", intent).put("language", lang)

    suspend fun addPhrase(alias: String, alts: List<String>, meaning: String, intent: String, lang: String): Phrase =
        call("POST", "api/phrases", json = phraseBody(alias, alts, meaning, intent, lang)) { b, _ -> parsePhrase(obj(b)) }

    suspend fun updatePhrase(id: Int, alias: String, alts: List<String>, meaning: String, intent: String, lang: String): Phrase =
        call("PUT", "api/phrases/$id", json = phraseBody(alias, alts, meaning, intent, lang)) { b, _ -> parsePhrase(obj(b)) }

    suspend fun deletePhrase(id: Int) = call("DELETE", "api/phrases/$id") { _, _ -> Unit }

    // ------------------------------------------------------------ history
    private fun parseHistory(x: JSONObject) = HistoryItem(
        id = x.getInt("id"),
        transcript = x.optString("transcript"),
        language = x.optString("source_language"),
        meaning = x.optString("meaning"),
        intent = x.optString("intent"),
        finalText = x.optString("final_text"),
        outputLanguage = x.optString("output_language"),
        shared = x.optBoolean("shared"),
        createdAt = x.optString("created_at"),
    )

    suspend fun history(query: String = ""): List<HistoryItem> {
        val path = if (query.isBlank()) "api/history" else "api/history?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        return call("GET", path) { b, _ ->
            val a = arr(b)
            List(a.length()) { i -> parseHistory(a.getJSONObject(i)) }
        }
    }

    suspend fun addHistory(text: String, meaning: String, intent: String, lang: String, outLang: String) {
        val body = JSONObject().put("final_text", text).put("meaning", meaning).put("intent", intent)
            .put("language", lang).put("output_language", outLang).put("confirmed", true)
        call("POST", "api/history", json = body) { _, _ -> Unit }
    }

    suspend fun deleteHistory(id: Int) = call("DELETE", "api/history/$id") { _, _ -> Unit }
    suspend fun clearHistory() = call("DELETE", "api/history") { _, _ -> Unit }
    suspend fun shareHistory(id: Int, shared: Boolean) =
        call("POST", "api/history/$id/share", json = JSONObject().put("shared", shared)) { _, _ -> Unit }

    // ------------------------------------------------------------ caregiver
    suspend fun createPairCode(): PairCode = call("POST", "api/caregiver/code") { b, _ ->
        val o = obj(b)
        PairCode(o.getString("code"), o.optString("expires_at"))
    }

    suspend fun pair(code: String): String = call("POST", "api/caregiver/pair", json = JSONObject().put("code", code)) { b, _ ->
        obj(b).optString("person_name", "Person")
    }

    suspend fun links(): List<CareLink> = call("GET", "api/caregiver/links") { b, _ ->
        val a = arr(b)
        List(a.length()) { i ->
            val x = a.getJSONObject(i)
            CareLink(x.getInt("id"), x.optString("caregiver_name"), x.optBoolean("share_phrases"), x.optBoolean("share_history"), x.optBoolean("share_emergency"))
        }
    }

    suspend fun updateLink(id: Int, phrases: Boolean, history: Boolean, emergency: Boolean) {
        val body = JSONObject().put("share_phrases", phrases).put("share_history", history).put("share_emergency", emergency)
        call("PUT", "api/caregiver/links/$id", json = body) { _, _ -> Unit }
    }

    suspend fun removeLink(id: Int) = call("DELETE", "api/caregiver/links/$id") { _, _ -> Unit }

    suspend fun people(): List<CarePerson> = call("GET", "api/caregiver/people") { b, _ ->
        val a = arr(b)
        List(a.length()) { i ->
            val x = a.getJSONObject(i)
            CarePerson(x.getInt("link_id"), x.getString("user_id"), x.optString("name"), x.optBoolean("share_phrases"), x.optBoolean("share_history"), x.optBoolean("share_emergency"))
        }
    }

    suspend fun personMessages(userId: String): List<SharedMessage> = call("GET", "api/caregiver/people/$userId/history") { b, _ ->
        val a = arr(b)
        List(a.length()) { i ->
            val x = a.getJSONObject(i)
            SharedMessage(x.getInt("id"), x.optString("final_text"), x.optString("meaning"), x.optString("created_at"))
        }
    }

    suspend fun personPhrases(userId: String): List<Phrase> = call("GET", "api/caregiver/people/$userId/phrases") { b, _ ->
        val a = arr(b)
        List(a.length()) { i -> parsePhrase(a.getJSONObject(i)) }
    }

    suspend fun personEmergency(userId: String): EmergencyContact = call("GET", "api/caregiver/people/$userId/emergency") { b, _ ->
        val o = obj(b)
        EmergencyContact(o.optString("name"), o.optString("phone"))
    }

    suspend fun leavePerson(userId: String) = call("DELETE", "api/caregiver/people/$userId") { _, _ -> Unit }

    // ------------------------------------------------------------ parsing
    private fun parseInterpretation(o: JSONObject): Interpretation {
        val alts = mutableListOf<Alt>()
        o.optJSONArray("alternatives")?.let { a ->
            for (i in 0 until a.length()) {
                val x = a.getJSONObject(i)
                alts += Alt(x.optString("intent"), x.optString("meaning"), x.optString("suggested_message"), x.optDouble("confidence", 0.0))
            }
        }
        return Interpretation(
            transcript = o.optString("transcript"),
            language = o.optString("language"),
            outputLanguage = o.optString("output_language"),
            intent = o.optString("intent"),
            meaning = o.optString("meaning"),
            suggestedMessage = o.optString("suggested_message"),
            confidence = o.optDouble("confidence", 0.0),
            level = o.optString("confidence_level", "low"),
            source = o.optString("source"),
            negated = o.optBoolean("negated"),
            alreadyDone = o.optBoolean("already_done"),
            ambiguous = o.optBoolean("ambiguous"),
            matchedPhraseId = if (o.isNull("matched_phrase_id")) null else o.optInt("matched_phrase_id"),
            alternatives = alts,
            warning = if (o.isNull("warning")) null else o.optString("warning"),
        )
    }
}
