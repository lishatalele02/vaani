"""Measure Prisma v3.0 accuracy WITH and WITHOUT keyterms (spends API credits: 2 calls per clip).

Gnani warns that hints can occasionally hurt accuracy, so measure before relying on them.

Folder layout (your own consented recordings, <= 30 s each):
    eval_audio/clip01.wav + clip01.txt   (txt = what was really said)
    eval_audio/keyterms.txt              (one personal phrase/name per line; shared by all clips)

Run from backend/:
    python scripts/eval_keyterms.py eval_audio --lang mr-IN
Writes results_keyterms.md (a table you can paste into the README) and prints it.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app import gnani  # noqa: E402
from app.config import settings  # noqa: E402
from app.textutil import normalize  # noqa: E402


def wer(ref: str, hyp: str) -> float:
    r, h = normalize(ref).split(), normalize(hyp).split()
    if not r:
        return 0.0 if not h else 1.0
    d = list(range(len(h) + 1))
    for i, rw in enumerate(r, 1):
        prev, d[0] = d[0], i
        for j, hw in enumerate(h, 1):
            cur = min(d[j] + 1, d[j - 1] + 1, prev + (rw != hw))
            prev, d[j] = d[j], cur
    return d[len(h)] / len(r)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("folder")
    ap.add_argument("--lang", default="mr-IN")
    a = ap.parse_args()
    folder = Path(a.folder)
    terms_file = folder / "keyterms.txt"
    terms = [t.strip() for t in terms_file.read_text(encoding="utf-8").splitlines() if t.strip()] if terms_file.exists() else []
    clips = sorted(folder.glob("*.wav"))
    if not clips or not settings.api_keys:
        print("Need clips in the folder and GNANI_API_KEY in .env")
        return 1
    rows, tot_a, tot_b = [], 0.0, 0.0
    for wav in clips:
        ref_file = wav.with_suffix(".txt")
        if not ref_file.exists():
            continue
        ref = ref_file.read_text(encoding="utf-8").strip()
        audio = wav.read_bytes()
        plain = gnani.transcribe(audio, wav.name, "audio/wav", a.lang, keyterms=None)
        with_k = gnani.transcribe(audio, wav.name, "audio/wav", a.lang, keyterms=terms)
        wa, wb = wer(ref, plain.text), wer(ref, with_k.text)
        tot_a, tot_b = tot_a + wa, tot_b + wb
        rows.append((wav.name, wa, wb))
    n = len(rows) or 1
    out = ["| Clip | WER without keyterms | WER with keyterms |", "|---|---|---|"]
    out += [f"| {n_} | {x:.2f} | {y:.2f} |" for n_, x, y in rows]
    out.append(f"| **Average ({len(rows)} clips)** | **{tot_a / n:.2f}** | **{tot_b / n:.2f}** |")
    text = "\n".join(out)
    Path("results_keyterms.md").write_text(text + "\n", encoding="utf-8")
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
