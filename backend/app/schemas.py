"""Request/response models."""
from __future__ import annotations

from typing import List, Optional

from pydantic import BaseModel, Field, field_validator

INTENT_PATTERN = r"^[a-z][a-z0-9_]{1,39}$"


class RegisterIn(BaseModel):
    display_name: str = Field("", max_length=60)
    preferred_language: str = "mr-IN"


class RegisterOut(BaseModel):
    user_id: str
    token: str


class ProfileUpdate(BaseModel):
    display_name: Optional[str] = Field(None, max_length=60)
    preferred_language: Optional[str] = None
    emergency_name: Optional[str] = Field(None, max_length=60)
    emergency_phone: Optional[str] = Field(None, max_length=30)
    save_history: Optional[bool] = None


class InterpretIn(BaseModel):
    text: str = Field(..., min_length=1, max_length=500)
    language: str = "mr-IN"
    output_language: Optional[str] = None


class ConfirmIn(BaseModel):
    transcript: str = Field("", max_length=500)
    intent: str = Field(..., pattern=INTENT_PATTERN)
    canonical_meaning: str = Field(..., min_length=1, max_length=300)
    language: str = "mr-IN"
    save_as_phrase: bool = False
    final_text: Optional[str] = Field(None, max_length=500)
    output_language: Optional[str] = None
    matched_phrase_id: Optional[int] = None
    user_id: Optional[str] = None  # ignored: identity comes from the token


class ConfirmOut(BaseModel):
    confirmed: bool = True
    phrase_saved: bool = False
    history_id: Optional[int] = None


class SpeakIn(BaseModel):
    text: str = Field(..., min_length=1, max_length=500)
    language: str = "hi-IN"
    speed: Optional[float] = None
    voice: Optional[str] = None
    # normal | urgent (<shouting>) | comfort (<whispers>) | slow_clear (<slow>/<pauses>); Timbre v3.0 audio tags
    style: str = "normal"


class TranslateIn(BaseModel):
    text: str = Field(min_length=1, max_length=500)
    target_language: str
    intent: str = ""  # optional hint: the intent the text came from (exact, meaning-safe translation)


class PhraseIn(BaseModel):
    alias: str = Field(..., min_length=1, max_length=200)
    alternatives: List[str] = Field(default_factory=list)
    canonical_meaning: str = Field(..., min_length=1, max_length=300)
    intent: str = Field(..., pattern=INTENT_PATTERN)
    language: str = "mr-IN"
    category: Optional[str] = None
    user_id: Optional[str] = None  # ignored

    @field_validator("alternatives")
    @classmethod
    def _clean_alts(cls, v: list) -> list:
        out = []
        for a in v[:20]:
            a = (a or "").strip()
            if a and len(a) <= 200 and a.lower() not in [x.lower() for x in out]:
                out.append(a)
        return out


class HistoryIn(BaseModel):
    transcript: str = Field("", max_length=500)
    language: str = ""
    meaning: str = Field("", max_length=300)
    intent: str = Field("", max_length=40)
    final_text: str = Field(..., min_length=1, max_length=500)
    output_language: str = ""
    confirmed: bool = True


class ShareIn(BaseModel):
    shared: bool = True


class PairIn(BaseModel):
    code: str = Field(..., min_length=6, max_length=6, pattern=r"^\d{6}$")


class LinkUpdate(BaseModel):
    share_phrases: bool = False
    share_history: bool = False
    share_emergency: bool = False
