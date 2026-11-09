"""Intent catalog: curated meanings and messages in English, Hindi and Marathi.

The Hindi/Marathi messages are hand-written (not machine translated) so the
intended meaning is preserved when the user speaks one language and the
output is another. Have a native speaker review them before real-world use.
"""
from __future__ import annotations

UNCLEAR = "other_unclear"

INTENTS = {
    "request_water": {
        "category": "basic_needs",
        "meaning": "The user needs drinking water.",
        "message": {"en": "Please give me some water.", "hi": "कृपया मुझे थोड़ा पानी दीजिए।", "mr": "कृपया मला थोडे पाणी द्या."},
        "neg_meaning": "The user does not want water.",
        "neg_message": {"en": "I do not want water.", "hi": "मुझे पानी नहीं चाहिए।", "mr": "मला पाणी नको आहे."},
        "done_meaning": "The user says they have already had water.",
        "done_message": {"en": "I have already had water.", "hi": "मैंने पानी पी लिया है।", "mr": "मी पाणी घेतले आहे."},
    },
    "request_food": {
        "category": "basic_needs",
        "meaning": "The user is hungry and needs food.",
        "message": {"en": "I am hungry. Please give me food.", "hi": "मुझे भूख लगी है, कृपया खाना दीजिए।", "mr": "मला भूक लागली आहे, कृपया जेवण द्या."},
        "neg_meaning": "The user does not want food.",
        "neg_message": {"en": "I do not want food.", "hi": "मुझे खाना नहीं चाहिए।", "mr": "मला जेवण नको आहे."},
        "done_meaning": "The user says they have already eaten.",
        "done_message": {"en": "I have already eaten.", "hi": "मैंने खाना खा लिया है।", "mr": "मी जेवण केले आहे."},
    },
    "request_rest": {
        "category": "basic_needs",
        "meaning": "The user needs to rest.",
        "message": {"en": "I need to rest.", "hi": "मुझे आराम करना है।", "mr": "मला विश्रांती घ्यायची आहे."},
        "neg_meaning": "The user does not want to rest.",
        "neg_message": {"en": "I do not need to rest.", "hi": "मुझे आराम नहीं करना है।", "mr": "मला विश्रांती नको आहे."},
    },
    "request_sleep": {
        "category": "basic_needs",
        "meaning": "The user wants to sleep.",
        "message": {"en": "I want to sleep.", "hi": "मुझे सोना है।", "mr": "मला झोपायचे आहे."},
        "neg_meaning": "The user does not want to sleep.",
        "neg_message": {"en": "I do not want to sleep.", "hi": "मुझे सोना नहीं है।", "mr": "मला झोपायचे नाही."},
    },
    "express_pain": {
        "category": "health",
        "meaning": "The user is in pain.",
        "message": {"en": "I am in pain.", "hi": "मुझे दर्द हो रहा है।", "mr": "मला वेदना होत आहेत."},
    },
    "request_medicine": {
        "category": "health",
        "meaning": "The user needs their medicine.",
        "message": {"en": "I need my medicine.", "hi": "मुझे मेरी दवा चाहिए।", "mr": "मला माझे औषध हवे आहे."},
        "neg_meaning": "The user does not need medicine.",
        "neg_message": {"en": "I do not need medicine.", "hi": "मुझे दवा नहीं चाहिए।", "mr": "मला औषध नको आहे."},
        "done_meaning": "The user says they have already taken their medicine.",
        "done_message": {"en": "I have already taken my medicine.", "hi": "मैंने दवा ले ली है।", "mr": "मी औषध घेतले आहे."},
    },
    "request_doctor": {
        "category": "health",
        "meaning": "The user needs a doctor.",
        "message": {"en": "I need a doctor.", "hi": "मुझे डॉक्टर चाहिए।", "mr": "मला डॉक्टरांची गरज आहे."},
        "neg_meaning": "The user does not need a doctor.",
        "neg_message": {"en": "I do not need a doctor.", "hi": "मुझे डॉक्टर नहीं चाहिए।", "mr": "मला डॉक्टर नको आहेत."},
    },
    "express_discomfort": {
        "category": "emotions",
        "meaning": "The user is uncomfortable or not feeling well.",
        "message": {"en": "I am feeling uncomfortable.", "hi": "मुझे असहज महसूस हो रहा है।", "mr": "मला अस्वस्थ वाटत आहे."},
    },
    "request_help": {
        "category": "help",
        "meaning": "The user needs help.",
        "message": {"en": "Please help me.", "hi": "कृपया मेरी मदद कीजिए।", "mr": "कृपया माझी मदत करा."},
        "neg_meaning": "The user says they do not need help.",
        "neg_message": {"en": "I do not need help.", "hi": "मुझे मदद नहीं चाहिए।", "mr": "मला मदत नको आहे."},
    },
    "request_family": {
        "category": "help",
        "meaning": "The user wants their family to be called.",
        "message": {"en": "Please call my family.", "hi": "कृपया मेरे परिवार को बुलाइए।", "mr": "कृपया माझ्या कुटुंबाला बोलवा."},
    },
    "request_emergency_help": {
        "category": "help",
        "meaning": "The user needs urgent emergency help.",
        "message": {"en": "This is an emergency. I need urgent help.", "hi": "यह आपातकाल है, मुझे तुरंत मदद चाहिए।", "mr": "ही आणीबाणी आहे, मला तातडीने मदत हवी आहे."},
    },
    "express_happiness": {
        "category": "emotions",
        "meaning": "The user is happy.",
        "message": {"en": "I am happy.", "hi": "मैं खुश हूँ।", "mr": "मी आनंदी आहे."},
    },
    "express_sadness": {
        "category": "emotions",
        "meaning": "The user is sad.",
        "message": {"en": "I am sad.", "hi": "मैं उदास हूँ।", "mr": "मी दुःखी आहे."},
    },
    "express_fear": {
        "category": "emotions",
        "meaning": "The user is scared.",
        "message": {"en": "I am scared.", "hi": "मुझे डर लग रहा है।", "mr": "मला भीती वाटत आहे."},
    },
    "request_bathroom": {
        "category": "daily",
        "meaning": "The user needs to use the bathroom.",
        "message": {"en": "I need to use the bathroom.", "hi": "मुझे शौचालय जाना है।", "mr": "मला स्वच्छतागृहात जायचे आहे."},
        "neg_meaning": "The user does not need the bathroom.",
        "neg_message": {"en": "I do not need the bathroom.", "hi": "मुझे शौचालय नहीं जाना है।", "mr": "मला स्वच्छतागृहात जायचे नाही."},
    },
    "request_go_outside": {
        "category": "daily",
        "meaning": "The user wants to go outside.",
        "message": {"en": "I need to go outside.", "hi": "मुझे बाहर जाना है।", "mr": "मला बाहेर जायचे आहे."},
    },
    "request_wait": {
        "category": "daily",
        "meaning": "The user is asking you to wait.",
        "message": {"en": "Please wait.", "hi": "कृपया रुकिए।", "mr": "कृपया थांबा."},
    },
    "request_go_home": {
        "category": "daily",
        "meaning": "The user wants to go home.",
        "message": {"en": "I want to go home.", "hi": "मुझे घर जाना है।", "mr": "मला घरी जायचे आहे."},
    },
}

UNCLEAR_MEANING = "We could not understand what you want to communicate."

CATEGORIES = {
    "basic_needs": "Basic Needs",
    "health": "Health",
    "help": "Help",
    "emotions": "Emotions",
    "daily": "Daily Activities",
    "custom": "My Phrases",
}

# Input language codes accepted by the API -> STT language_code sent to Gnani Prisma
INPUT_LANGUAGES = {
    "mr-IN": "mr-IN",
    "hi-IN": "hi-IN",
    "en-IN": "en-IN",
    "hi-en": "hi-IN,en-IN",  # Hinglish: Prisma auto-detects between the listed codes
    # Any language: Prisma v3.0 detects it and returns detected_language_code.
    "indic-auto": "indic-auto",
}
# Languages accepted for TTS (all documented by Gnani Timbre)
OUTPUT_LANGUAGES = {"mr-IN", "hi-IN", "en-IN", "hi-en"}


AUTO = "indic-auto"
# Languages the meaning catalog can express (messages exist for these three).
CATALOG_LANGS = {"mr-IN", "hi-IN", "en-IN"}


def resolve_languages(language: str, output_language: str, detected: str = "") -> tuple:
    """-> (language_used_for_meaning, output_language, note).

    Marathi speakers should send only mr-IN (very short Marathi can be tagged Hindi because both
    use Devanagari). For 'indic-auto' the detected language decides; the spoken output defaults
    to the same language when we have wording for it, else Hindi.
    """
    note = None
    lang = language
    if language == AUTO:
        lang = detected if detected in CATALOG_LANGS else "hi-IN"
        if detected and detected not in CATALOG_LANGS:
            note = (f"Detected language {detected} - meanings are only checked for Marathi, Hindi and English. "
                    "Please check the message carefully.")
    out = output_language if output_language in OUTPUT_LANGUAGES else (lang if lang in OUTPUT_LANGUAGES else "hi-IN")
    return lang, out, note


def short_lang(code: str) -> str:
    """'mr-IN' -> 'mr', 'hi-en' -> 'hi', 'en-IN' -> 'en'."""
    c = (code or "").lower()
    if c.startswith("mr"):
        return "mr"
    if c.startswith("en"):
        return "en"
    return "hi"


def message_for(intent: str, lang_code: str, variant: str = "message") -> str:
    entry = INTENTS.get(intent)
    if not entry or variant not in entry:
        return ""
    return entry[variant][short_lang(lang_code)]
