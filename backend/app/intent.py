"""Meaning interpretation: personalised phrase match -> ML classifier -> rules.

Never returns a "certain" result: every interpretation must be confirmed by
the user. Negation ("I do NOT need water"), "already done" ("I already drank
water") and low confidence are handled explicitly.
"""
from __future__ import annotations

import logging
from difflib import SequenceMatcher
from typing import Optional

import joblib

from . import catalog
from .dataset import rows as dataset_rows
from .textutil import has_done_marker, has_hedge, has_negation, normalize, script_of, tokens
from .train_model import MODEL_PATH, train

log = logging.getLogger("samajh.intent")

HIGH = 0.70
LOW = 0.40
PHRASE_MATCH_MIN = 0.86

_model = None
_fallback_rows = None


def load_model():
    """Load the saved classifier; (re)train automatically if missing or incompatible."""
    global _model
    if _model is not None:
        return _model
    try:
        _model = joblib.load(MODEL_PATH)
    except Exception as exc:  # missing file or scikit-learn version mismatch
        log.warning("Loading model failed (%s); training a fresh one.", exc)
        try:
            train(save=True)
            _model = joblib.load(MODEL_PATH)
        except Exception as exc2:
            log.error("Training failed (%s); using nearest-phrase fallback.", exc2)
            _model = False
    return _model


def _fallback_predict(norm: str) -> list:
    """Nearest-phrase fallback when scikit-learn is unusable. Returns [(intent, score)]."""
    global _fallback_rows
    if _fallback_rows is None:
        _fallback_rows = [(normalize(t), i) for t, i, _ in dataset_rows()]
    best = {}
    for text, intent in _fallback_rows:
        score = SequenceMatcher(None, norm, text).ratio()
        if score > best.get(intent, 0):
            best[intent] = score
    ranked = sorted(best.items(), key=lambda kv: kv[1], reverse=True)[:3]
    return [(i, round(s * 0.8, 3)) for i, s in ranked]


def classify(norm: str) -> list:
    """Top-3 [(intent, probability)] from the ML model (or the fallback)."""
    model = load_model()
    if not model:
        return _fallback_predict(norm)
    probs = model.predict_proba([norm])[0]
    classes = model.classes_
    ranked = sorted(zip(classes, probs), key=lambda kv: kv[1], reverse=True)[:3]
    return [(str(c), float(p)) for c, p in ranked]


def _similarity(a: str, b: str) -> float:
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    ta, tb = set(tokens(a)), set(tokens(b))
    jacc = len(ta & tb) / len(ta | tb) if ta | tb else 0.0
    seq = SequenceMatcher(None, a, b).ratio()
    return 0.5 * jacc + 0.5 * seq


def match_phrase(norm: str, negated: bool, phrases: list) -> Optional[tuple]:
    """Best personalised phrase (phrase_dict, score). Skips phrases whose negation differs."""
    best = None
    for p in phrases:
        for text in [p["alias"], *p.get("alternatives", [])]:
            cand = normalize(text)
            if not cand or has_negation(cand) != negated:
                continue
            score = _similarity(norm, cand)
            if best is None or score > best[1]:
                best = (p, score)
    if best and best[1] >= PHRASE_MATCH_MIN:
        return best
    return None


def _alt(intent: str, conf: float, out_lang: str, variant: str = "message") -> dict:
    entry = catalog.INTENTS.get(intent)
    return {
        "intent": intent,
        "meaning": entry["meaning"] if entry else catalog.UNCLEAR_MEANING,
        "suggested_message": catalog.message_for(intent, out_lang, variant) if entry else "",
        "confidence": round(conf, 3),
    }


def _level(conf: float) -> str:
    return "high" if conf >= HIGH else ("medium" if conf >= LOW else "low")


def interpret(text: str, language: str, output_language: str, phrases: list) -> dict:
    out_lang = output_language or language
    norm = normalize(text)
    result = {
        "transcript": text,
        "language": language,
        "output_language": out_lang,
        "script": script_of(text),
        "intent": catalog.UNCLEAR,
        "meaning": catalog.UNCLEAR_MEANING,
        "suggested_message": "",
        "confidence": 0.0,
        "confidence_level": "low",
        "source": "none",
        "negated": False,
        "already_done": False,
        "ambiguous": False,
        "needs_confirmation": True,
        "matched_phrase_id": None,
        "alternatives": [],
        "warning": None,
    }
    if not norm:
        result["warning"] = "No speech was recognised. Please try again."
        return result

    negated = has_negation(norm)
    done = has_done_marker(norm)

    # 1) personalised phrase library
    hit = match_phrase(norm, negated, phrases)
    ranked = classify(norm)
    if hit:
        phrase, score = hit
        intent = phrase["intent"]
        conf = min(0.98, score)
        known = intent in catalog.INTENTS
        result.update(
            intent=intent,
            meaning=phrase["canonical_meaning"],
            suggested_message=(catalog.message_for(intent, out_lang) if known and not negated else phrase["canonical_meaning"]),
            confidence=round(conf, 3),
            source="phrase",
            matched_phrase_id=phrase["id"],
            negated=negated,
        )
        if negated and known and "neg_message" in catalog.INTENTS[intent]:
            result["meaning"] = catalog.INTENTS[intent]["neg_meaning"]
            result["suggested_message"] = catalog.message_for(intent, out_lang, "neg_message")
    else:
        intent, conf = ranked[0]
        result.update(intent=intent, confidence=round(conf, 3), source="model")
        entry = catalog.INTENTS.get(intent)
        if entry is None:  # other_unclear
            result["meaning"] = catalog.UNCLEAR_MEANING
        else:
            result["meaning"] = entry["meaning"]
            result["suggested_message"] = catalog.message_for(intent, out_lang)
            # 2) rule layer: negation / already-done
            if negated and "neg_message" in entry:
                result["negated"] = True
                result["source"] = "rule"
                result["meaning"] = entry["neg_meaning"]
                result["suggested_message"] = catalog.message_for(intent, out_lang, "neg_message")
                if has_hedge(norm):  # "I have not had water yet" - could mean either
                    result["ambiguous"] = True
                    result["confidence"] = min(result["confidence"], 0.5)
                    result["warning"] = "This could mean you do NOT want it, or that you have not had it yet."
            elif negated and intent != "express_discomfort":  # "I am not feeling well" is itself discomfort
                result["negated"] = True
                result["ambiguous"] = True
                result["confidence"] = min(result["confidence"], 0.45)
                result["warning"] = "Your sentence contains a 'no/not'. Please check the meaning carefully."
            elif done and "done_message" in entry:
                result["already_done"] = True
                result["source"] = "rule"
                result["meaning"] = entry["done_meaning"]
                result["suggested_message"] = catalog.message_for(intent, out_lang, "done_message")
                result["confidence"] = min(result["confidence"], 0.8)

    conf = result["confidence"]
    result["confidence_level"] = _level(conf)

    # alternatives from the model's runner-up intents
    for alt_intent, alt_conf in ranked:
        if alt_intent != result["intent"] and alt_intent != catalog.UNCLEAR and alt_conf >= 0.08:
            result["alternatives"].append(_alt(alt_intent, alt_conf, out_lang))
    if result["negated"] and result["ambiguous"] and result["intent"] in catalog.INTENTS:
        base = _alt(result["intent"], result["confidence"], out_lang)
        if base not in result["alternatives"]:
            result["alternatives"].insert(0, base)

    if result["confidence_level"] == "low" and result["intent"] != catalog.UNCLEAR:
        result["ambiguous"] = True
        result["warning"] = result["warning"] or "We are not sure. Please choose the closest meaning or record again."
    if result["intent"] == catalog.UNCLEAR:
        result["warning"] = result["warning"] or "We could not understand this. Please record again or pick a card."
    return result
