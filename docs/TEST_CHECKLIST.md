# Manual test checklist

## Backend (laptop)
- [ ] `python scripts\check_gnani.py` prints `TTS OK` and `STT OK` (live Gnani check)
- [ ] http://127.0.0.1:8000/health → ok; `/docs` opens
- [ ] `python -m pytest -q` → all pass

## Phone connectivity
- [ ] Phone browser opens `http://<laptop-ip>:8000/health`
- [ ] App shows no red “Cannot reach the server” banner on Home

## End-to-end (first flow to verify)
1. [ ] Home → tap the microphone → allow microphone
2. [ ] Say **“Hello I need water please”** → tap the button again to stop → *Listen* (optional) → *Continue*
3. [ ] “You said” ≈ “Hello I need water please”; meaning **“The user needs drinking water.”**
4. [ ] Tap *Confirm and speak* → audio plays; Pause / Resume / Stop / Replay work
5. [ ] History shows the message

## Other
- [ ] Deny microphone → explanation + “Open settings”; quick messages still work
- [ ] Record < 1 s or silence → friendly “too short / could not hear” message
- [ ] “I don’t need water” → meaning says the user does NOT want water
- [ ] “I already drank water” → “already had water”
- [ ] Mumble / nonsense → “could not understand”, alternatives or quick-card link
- [ ] Edit the message, or *Edit what you said or the meaning*, before confirming; choose from *Other meanings*
- [ ] *Save as my phrase* is OFF by default; turn on → phrase appears in My phrases
- [ ] Phrase library: add (with aliases), edit, search, delete
- [ ] Quick messages: all 5 categories, edit text, switch language, speak
- [ ] Marathi speech → message spoken in Hindi/English (Settings → Say my messages in)
- [ ] Stop the backend → friendly “Cannot reach the server” message, no crash
- [ ] History: search, detail, replay, delete, clear; turn history off in Settings
- [ ] Settings → Caregiver sharing: create code on phone A; enter on phone B; B sees nothing; A switches a share on; B sees it; A removes B
- [ ] Settings → Delete all my data → returns to onboarding

## New in this version
- [ ] `python scripts\check_gnani.py` prints `STT OK ... detected_language_code = ...` (Prisma v3.0) and TTS plays with the Shivani voice (Timbre v3.0)
- [ ] Settings → Voice lists 13 voices; picking each one still speaks Marathi
- [ ] Type and speak: type `mla pani have ahe`, tap **English** → shows "Spoken as: Please give me some water." and speaks English; tap **Hindi** and **Marathi** too
- [ ] Speak screen review: tap English / Hindi / Marathi under **Say it in** → the message text changes language and is spoken in it
- [ ] Type a free sentence (e.g. "I want tea and biscuits please") → Hindi: a "Machine translation. Please check it." note appears
- [ ] Marathi selected: short Marathi phrase is still treated as Marathi (only `mr-IN` is sent)
- [ ] Review screen "How should it sound?": Normal / Soft / Urgent / Slow and clear all play (audio tags are ignored, not read aloud)
- [ ] Save a phrase, then speak it: `keyterms_sent` > 0 in the API response (`/docs`); compare accuracy with `GNANI_KEYTERMS=false`
- [ ] Settings → Offline messages → Download; switch the phone to airplane mode; a quick card still speaks
- [ ] Home → Emergency: speaks urgently at once, big text; with a saved contact the button opens the dialer and does NOT call by itself
- [ ] Settings → High contrast; system font size at maximum; TalkBack reads the Emergency button, quick cards, record button
- [ ] Use "Pain" five times: it stays on the Home screen; use "Bathroom" many times: it moves up
- [ ] Wrong access code on a server with `REGISTRATION_CODE` set → "The access code is not correct."
