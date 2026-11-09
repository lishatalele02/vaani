"""Intent accuracy report on HELD-OUT data -> docs/INTENT_ACCURACY.md

The base phrases are split 75/25 *before* augmentation, so no test phrase (or augmented copy) is
seen in training. Data is synthetic and hand-written: scores are optimistic. Add real, consented
phrases from native speakers to data/ and re-run to get a more honest number.

Run from backend/:  python scripts/intent_report.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.train_model import train  # noqa: E402

OUT = Path(__file__).resolve().parent.parent.parent / "docs" / "INTENT_ACCURACY.md"


def main() -> int:
    m = train(save=False)
    lines = [
        "# Intent accuracy report (held-out test split)", "",
        f"Dataset: {m['dataset']}.", "",
        f"- Unique phrases: {m['total_unique_examples']}  |  held-out test phrases: {m['test_examples']}  |  classes: {m['classes']}",
        f"- Accuracy: **{m['accuracy']:.3f}**  |  macro precision {m['macro_precision']:.3f}  |  macro recall {m['macro_recall']:.3f}  |  macro F1 {m['macro_f1']:.3f}", "",
        "## By language group", "", "| Group | Accuracy |", "|---|---|",
    ]
    lines += [f"| {k} | {v:.3f} |" for k, v in m["accuracy_by_language_group"].items()]
    lines += ["", "## Per intent", "", "| Intent | Precision | Recall | F1 | Support |", "|---|---|---|---|---|"]
    for k, v in m["per_class"].items():
        if isinstance(v, dict) and k not in ("macro avg", "weighted avg"):
            lines.append(f"| {k} | {v['precision']:.2f} | {v['recall']:.2f} | {v['f1-score']:.2f} | {int(v['support'])} |")
    lines += ["", f"> {m['note']}", "",
              "Every interpretation is still confirmed by the user before it is spoken, so a wrong guess is a "
              "correction, not an action."]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT}")
    print("\n".join(lines[:8]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
