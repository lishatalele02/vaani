package com.samajhsakhi.app.data

/** Offline quick-communication cards. Wording is hand-written in three languages. */
data class QuickCard(
    val id: String,
    val category: String,
    val intent: String,
    val en: String,
    val hi: String,
    val mr: String,
) {
    fun text(langCode: String): String = when (Langs.short(langCode)) {
        "mr" -> mr
        "en" -> en
        else -> hi
    }
}

object Cards {
    val categories = listOf(
        "basic" to "Basic Needs",
        "health" to "Health",
        "help" to "Help",
        "emotions" to "Emotions",
        "daily" to "Daily",
    )

    val all = listOf(
        QuickCard("water", "basic", "request_water", "I need water.", "मुझे पानी चाहिए।", "मला पाणी हवे आहे."),
        QuickCard("hungry", "basic", "request_food", "I am hungry.", "मुझे भूख लगी है।", "मला भूक लागली आहे."),
        QuickCard("food", "basic", "request_food", "I need food.", "मुझे खाना चाहिए।", "मला जेवण हवे आहे."),
        QuickCard("rest", "basic", "request_rest", "I need to rest.", "मुझे आराम करना है।", "मला विश्रांती घ्यायची आहे."),
        QuickCard("sleep", "basic", "request_sleep", "I need to sleep.", "मुझे सोना है।", "मला झोपायचे आहे."),

        QuickCard("unwell", "health", "express_discomfort", "I am not feeling well.", "मेरी तबीयत ठीक नहीं है।", "माझी तब्येत बरी नाही."),
        QuickCard("pain", "health", "express_pain", "I am in pain.", "मुझे दर्द हो रहा है।", "मला वेदना होत आहेत."),
        QuickCard("medicine", "health", "request_medicine", "I need my medicine.", "मुझे मेरी दवा चाहिए।", "मला माझे औषध हवे आहे."),
        QuickCard("doctor", "health", "request_doctor", "I need a doctor.", "मुझे डॉक्टर चाहिए।", "मला डॉक्टरांची गरज आहे."),

        QuickCard("help", "help", "request_help", "Please help me.", "कृपया मेरी मदद कीजिए।", "कृपया माझी मदत करा."),
        QuickCard("family", "help", "request_family", "Please call my family.", "कृपया मेरे परिवार को बुलाइए।", "कृपया माझ्या कुटुंबाला बोलवा."),
        QuickCard("assist", "help", "request_help", "I need assistance.", "मुझे सहायता चाहिए।", "मला सहाय्य हवे आहे."),

        QuickCard("happy", "emotions", "express_happiness", "I am happy.", "मैं खुश हूँ।", "मी आनंदी आहे."),
        QuickCard("sad", "emotions", "express_sadness", "I am sad.", "मैं उदास हूँ।", "मी दुःखी आहे."),
        QuickCard("uncomfortable", "emotions", "express_discomfort", "I am uncomfortable.", "मुझे असहज महसूस हो रहा है।", "मला अस्वस्थ वाटत आहे."),
        QuickCard("scared", "emotions", "express_fear", "I am scared.", "मुझे डर लग रहा है।", "मला भीती वाटत आहे."),

        QuickCard("outside", "daily", "request_go_outside", "I need to go outside.", "मुझे बाहर जाना है।", "मला बाहेर जायचे आहे."),
        QuickCard("bathroom", "daily", "request_bathroom", "I need to use the bathroom.", "मुझे शौचालय जाना है।", "मला स्वच्छतागृहात जायचे आहे."),
        QuickCard("wait", "daily", "request_wait", "Please wait.", "कृपया रुकिए।", "कृपया थांबा."),
        QuickCard("home", "daily", "request_go_home", "I want to go home.", "मुझे घर जाना है।", "मला घरी जायचे आहे."),
    )

    /** Spoken urgently, with a dial button, from the emergency screen. Not part of [all]. */
    val emergency = QuickCard(
        "emergency", "help", "request_emergency_help",
        "This is an emergency. I need urgent help.",
        "यह आपातकाल है, मुझे तुरंत मदद चाहिए।",
        "ही आणीबाणी आहे, मला तातडीने मदत हवी आहे.",
    )

    /** Everything that can be stored on the phone for offline use. */
    val offlineSet: List<QuickCard> get() = all + emergency

    /** Default four for a brand-new user. */
    private val defaultHome = listOf("water", "pain", "help", "medicine")

    /** Cards that make sense at certain times of day (small boost, never beats real usage). */
    private fun timeBoost(id: String, hour: Int): Int = when (id) {
        "food", "hungry", "medicine", "water" -> if (hour in 6..9 || hour in 12..14 || hour in 18..21) 2 else 0
        "sleep", "rest" -> if (hour >= 21 || hour <= 4) 3 else 0
        "bathroom" -> if (hour >= 22 || hour <= 6) 1 else 0
        else -> 0
    }

    fun parseUse(encoded: String): Map<String, Int> =
        encoded.split(",").mapNotNull { part ->
            val bits = part.split(":")
            val n = bits.getOrNull(1)?.toIntOrNull()
            if (bits.size == 2 && n != null && bits[0].isNotBlank()) bits[0] to n else null
        }.toMap()

    /** Adds one use of [id]; keeps the string short by halving all counts when they grow large. */
    fun bump(encoded: String, id: String): String {
        val m = parseUse(encoded).toMutableMap()
        m[id] = (m[id] ?: 0) + 1
        if (m.values.sum() > 500) m.replaceAll { _, v -> v / 2 }
        return m.filterValues { it > 0 }.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    /** Four home-screen cards ordered by how often each is used, plus a time-of-day nudge. */
    fun homeFor(encodedUse: String, hour: Int): List<QuickCard> {
        val use = parseUse(encodedUse)
        val ranked = all.sortedWith(
            compareByDescending<QuickCard> { (use[it.id] ?: 0) * 10 + timeBoost(it.id, hour) + if (it.id in defaultHome) 1 else 0 }
                .thenBy { defaultHome.indexOf(it.id).let { i -> if (i < 0) 99 else i } }
        )
        return ranked.take(4)
    }

    /** Kept for callers that want the fixed default set. */
    val home = defaultHome.mapNotNull { id -> all.firstOrNull { it.id == id } }
}
