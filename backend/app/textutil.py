"""Text normalisation and rule helpers shared by training and inference."""
from __future__ import annotations

import re
import unicodedata

_DEVANAGARI = re.compile(r"[\u0900-\u097F]")
_KEEP = re.compile(r"[^0-9a-z\u0900-\u097F\s]")

# Words that negate a need ("I do NOT need water"). Roman + Devanagari.
NEGATION = {
    "dont", "not", "no", "never", "wont", "nothing",
    "नहीं", "नही", "मत", "nahi", "nahin", "nhi", "mat",
    "नको", "नाही", "नका", "nako", "nakos", "nakka",
}
# "already" / completed-action markers (only meaningful for water, food, medicine)
DONE = {
    "already", "finished", "drank", "ate", "taken",
    "लिया", "चुका", "चुकी", "खा", "पी", "liya", "chuka", "chuki",
    "प्यायलो", "प्यायले", "घेतले", "घेतलं", "खाल्ले", "जेवलो", "जेवले", "झाले", "झालं",
    "pilo", "pile", "pila", "ghetle", "ghetla", "khalle", "jevlo", "jevle", "zale", "zala",
}
# Words that make a negative sentence ambiguous ("I have not had water YET")
HEDGE = {"yet", "still", "अभी", "abhi", "अजून", "ajun", "ajun", "अजुन"}


def normalize(text: str) -> str:
    t = unicodedata.normalize("NFC", text or "").lower()
    t = t.replace("'", "").replace("\u2019", "")
    t = _KEEP.sub(" ", t)
    return re.sub(r"\s+", " ", t).strip()


def tokens(norm: str) -> list:
    return norm.split()


def has_negation(norm: str) -> bool:
    return any(tok in NEGATION for tok in tokens(norm))


def has_done_marker(norm: str) -> bool:
    toks = tokens(norm)
    # "already" alone is a strong marker; verb markers need to be present as tokens
    return any(t in DONE for t in toks)


def has_hedge(norm: str) -> bool:
    return any(t in HEDGE for t in tokens(norm))


def script_of(text: str) -> str:
    return "devanagari" if _DEVANAGARI.search(text or "") else "latin"
