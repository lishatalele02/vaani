# Submission draft (edit before sending: fill every blank honestly)

**Narrative (one award):** Marathi-first assistive voice for people who struggle to be understood.

**What it does:** The person speaks, Samajh Sakhi works out the likely meaning, the person **confirms or corrects it**, and only then is it spoken aloud in a clear voice. Confirmation first, instead of auto-speaking, is deliberate: a wrong guess spoken aloud can be harmful, a wrong guess shown on screen is only a correction.

## Gnani APIs used (exact versions and endpoints)
| API | Version / endpoint | Used for |
|---|---|---|
| Prisma STT | `prisma-v3.0`, `POST https://api.vachana.ai/stt/v3` | Speech to text; `mr-IN` only for Marathi speakers; personal `keyterms` |
| Timbre TTS | `timbre-v3.0`, `POST https://api.vachana.ai/api/v1/tts/inference` | Spoken messages in Marathi/Hindi/English; 13 voices; audio tags `<shouting>` (emergency), `<whispers>` (soft), `<slow>`/`<pauses>` (slow and clear) |
| Timbre SSE | `POST https://api.vachana.ai/api/v1/tts/sse` | Streaming speech endpoint on the backend (not yet used by the app) |

Not used: Evon, voice cloning, realtime STT.

## Team contributions (state each person's real work)
- ______: 
- ______: 

## Measured results (from `docs/RESULTS.md`, only what you really measured)
- Real-user sessions: ___ people, meaning correct first time ___ %, median time ___ s
- Keyterms with/without: ___
- Held-out synthetic intent accuracy: see `docs/INTENT_ACCURACY.md` (synthetic data, optimistic)

## Demo post checklist (the post is what gets counted)
- [ ] Shows a real use end to end: speak → meaning → confirm → voice
- [ ] Names Prisma v3.0 and Timbre v3.0 on screen, with the Prisma-to-Timbre flow
- [ ] Shows Marathi, the Emergency screen, and offline messages
- [ ] Shared genuinely. No bought engagement, no exchange groups (can disqualify).

## Interview preparation
Be ready to explain: every module; why confirmation instead of auto-speaking; the limits (synthetic training data, small intent set, 3 meaning languages, no clinical validation, not a medical device); and what you would do next (voice cloning with consent, live captions, more languages with native-speaker review, real-user data).
