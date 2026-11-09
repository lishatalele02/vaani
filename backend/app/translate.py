"""Translate a message between Marathi, Hindi and English.

Order of trust:
 1. 'same'     the text is already in the target language -> unchanged.
 2. 'catalog'  the text IS (or is almost exactly) one of the curated ready-made messages
               -> the hand-written message in the target language. Meaning-safe.
 3. 'machine'  anything else -> a free machine-translation service (MyMemory, no key).
               Only the message text is sent. Switch off with TRANSLATE_FALLBACK=false.
 4. 'closest'  machine translation unavailable but a ready-made message is a fair match
               -> that message, flagged so the person checks it.
If nothing works the caller gets a clear error; we never guess silently.
"""
from __future__ import annotations

import logging
import re
from typing import Optional

import requests

from . import catalog
from .config import settings
from .intent import interpret
from .textutil import normalize, script_of

log = logging.getLogger("samajh.translate")

_MR_MARKERS = {"आहे", "आहेत", "मला", "हवे", "हवा", "हवी", "नको", "द्या", "माझे", "माझा", "माझी", "तुम्ही", "काय", "होत", "करा", "झोपायचे",
               "आहात", "नाही", "कृपया", "मी", "आम्ही", "येथे", "इथे", "पाहिजे", "लागली", "लागले", "वेदना"}
_EN_WORDS = {"i", "am", "is", "are", "the", "a", "an", "to", "my", "me", "you", "your", "please", "need", "want", "have", "has", "can",
             "could", "do", "does", "not", "dont", "it", "this", "that", "what", "where", "how", "when", "help", "give", "some",
             "and", "with", "for", "in", "on", "of", "we", "he", "she", "they", "will", "would", "feel", "feeling", "go", "get",
             "water", "food", "pain", "doctor", "medicine", "hungry", "tired", "sleep", "toilet", "bathroom", "thank", "thanks"}
_MT_LANG = {"mr": "mr", "hi": "hi", "en": "en"}


def detect_source(text: str) -> str:
    """'mr' | 'hi' | 'en' | 'latin_indic' (romanised Marathi/Hindi, language unknown)."""
    if script_of(text) == "devanagari":
        toks = set(re.findall(r"[ऀ-ॿ]+", text))
        return "mr" if toks & _MR_MARKERS else "hi"
    toks = normalize(text).split()
    if not toks:
        return "en"
    hits = sum(1 for t in toks if t in _EN_WORDS)
    return "en" if hits / len(toks) >= 0.4 else "latin_indic"


def _all_messages(intent: str) -> set:
    entry = catalog.INTENTS.get(intent) or {}
    out = set()
    for key in ("message", "neg_message", "done_message"):
        for v in (entry.get(key) or {}).values():
            out.add(normalize(v))
    return out


def _machine(text: str, src: str, tgt: str) -> Optional[str]:
    if not settings.translate_fallback or src not in _MT_LANG or tgt not in _MT_LANG or len(text.encode("utf-8")) > 480:
        return None
    try:
        r = requests.get(settings.translate_url, params={"q": text, "langpair": f"{_MT_LANG[src]}|{_MT_LANG[tgt]}"}, timeout=8)
        if r.status_code != 200:
            return None
        data = r.json()
        out = (data.get("responseData") or {}).get("translatedText") or ""
        if data.get("responseStatus") not in (200, "200") or not out.strip():
            return None
        if "MYMEMORY WARNING" in out.upper() or "INVALID" in out.upper()[:20]:
            return None
        return out.strip()
    except (requests.RequestException, ValueError):
        log.warning("Machine translation unavailable")
        return None


def translate(text: str, target_language: str, phrases: list, intent_hint: str = "") -> dict:
    text = (text or "").strip()
    tgt = catalog.short_lang(target_language)
    src = detect_source(text)
    base = {"text": text, "method": "same", "source_language": src, "target_language": target_language,
            "intent": "", "confidence": 1.0, "note": None}
    if not text or src == tgt:
        return base

    # a) text is exactly one of the curated messages (e.g. the app's own suggestion)
    if intent_hint in catalog.INTENTS and normalize(text) in _all_messages(intent_hint):
        entry = catalog.INTENTS[intent_hint]
        for key in ("message", "neg_message", "done_message"):
            if normalize(text) in {normalize(v) for v in (entry.get(key) or {}).values()}:
                return {**base, "text": entry[key][tgt], "method": "catalog", "intent": intent_hint}

    r = interpret(text, "en-IN" if src == "en" else "mr-IN", target_language, phrases)
    known = r["intent"] in catalog.INTENTS and bool(r["suggested_message"])
    short = len(normalize(text).split()) <= 6
    conf = r["confidence"]

    # b) a short, confidently recognised ready-made message
    if known and conf >= 0.8 and short and not r["ambiguous"]:
        return {**base, "text": r["suggested_message"], "method": "catalog", "intent": r["intent"], "confidence": conf}

    # c) machine translation (not possible from romanised Marathi/Hindi: the service needs real scripts)
    mt = _machine(text, src, tgt) if src != "latin_indic" else None
    if mt:
        return {**base, "text": mt, "method": "machine", "confidence": 0.0,
                "note": "Machine translation. Please check it before speaking."}

    # d) closest ready-made message, clearly flagged
    if known and conf >= 0.65 and not r["ambiguous"]:
        return {**base, "text": r["suggested_message"], "method": "closest", "intent": r["intent"], "confidence": conf,
                "note": "Closest ready-made message. The meaning may differ a little. Please check it."}

    return {**base, "method": "failed",
            "note": ("This sentence is written in English letters but is not English, so it cannot be translated. "
                     "Please type it in Devanagari or English, or use a quick message.") if src == "latin_indic"
            else "This message could not be translated right now. Please check the internet connection or use a quick message."}
