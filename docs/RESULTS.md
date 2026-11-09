# Results (fill in from real sessions; do not quote until measured)

## A. Real-person testing (5-10 people, with consent)
Record each person's consent. Use native speakers of Marathi/Hindi. Never store raw audio.

| # | Language | Message tried | Meaning correct on 1st try? (Y/N) | Seconds from tap to spoken | Notes |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |

Summary (fill in): meaning right first time ___ / ___ · median time ___ s · corrections needed ___.

## B. Keyterms with vs without (Prisma v3.0)
Run `python scripts/eval_keyterms.py eval_audio --lang mr-IN` (clips + reference .txt + keyterms.txt). It writes `results_keyterms.md`; paste it here.

| Clip | WER without keyterms | WER with keyterms |
|---|---|---|
| (run the script) | | |

Decision: keep keyterms ON / OFF (`GNANI_KEYTERMS=true|false`) because ___.

## C. Intent classifier on held-out synthetic data
See `INTENT_ACCURACY.md` (generated, optimistic because the data is synthetic).

## D. Native-speaker review of message wording
| Language | Reviewer | Date | Changes made |
|---|---|---|---|
| Marathi | | | |
| Hindi | | | |
