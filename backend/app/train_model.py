"""Train and evaluate the intent classifier on the SYNTHETIC dataset.

Run from the backend folder:   python -m app.train_model

Method
  * Split the *base* phrases first (stratified, 75/25) so nothing in the test
    set - not even an augmented copy - is seen during training (no leakage).
  * Augment only the training split with filler words (hello / please / ji).
  * Features: char n-gram TF-IDF (robust to spelling variation in romanised
    Hindi/Marathi) + word n-gram TF-IDF. Classifier: logistic regression.
  * Report accuracy, macro precision/recall/F1 and per-class scores.
  * Refit on all data for the deployed model and save it.
"""
from __future__ import annotations

import csv
import json
import random
from collections import Counter
from pathlib import Path

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, precision_recall_fscore_support
from sklearn.model_selection import train_test_split
from sklearn.pipeline import FeatureUnion, Pipeline

from .dataset import keyword_rows, rows
from .textutil import normalize

BASE = Path(__file__).resolve().parent.parent
MODEL_PATH = BASE / "models" / "intent_model.joblib"
METRICS_PATH = BASE / "models" / "metrics.json"
CSV_PATH = BASE / "data" / "synthetic_intents.csv"

PREFIXES = ["hello ", "please ", "ji ", "uh ", "hello please ", "suno ", "arre "]
SUFFIXES = [" please", " plz", " ji", " na", " now", " thanks"]


def build_pipeline() -> Pipeline:
    features = FeatureUnion([
        ("char", TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 5), sublinear_tf=True, preprocessor=normalize)),
        ("word", TfidfVectorizer(analyzer="word", ngram_range=(1, 2), sublinear_tf=True, preprocessor=normalize, token_pattern=r"(?u)\b\w+\b")),
    ])
    clf = LogisticRegression(C=20, max_iter=3000, class_weight="balanced")
    return Pipeline([("features", features), ("clf", clf)])


def augment(samples: list, seed: int = 7) -> list:
    rnd = random.Random(seed)
    out = list(samples)
    for text, intent, lang in samples:
        if intent == "other_unclear":
            continue
        out.append((rnd.choice(PREFIXES) + text, intent, lang))
        out.append((text + rnd.choice(SUFFIXES), intent, lang))
    return out


def write_csv(data: list) -> None:
    CSV_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(CSV_PATH, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["text", "intent", "language_group", "synthetic"])
        for text, intent, lang in data:
            w.writerow([text, intent, lang, "yes"])


def train(save: bool = True) -> dict:
    data = rows()
    write_csv(data)
    # de-duplicate on normalised text so identical strings cannot straddle the split
    seen, unique = set(), []
    for text, intent, lang in data:
        key = normalize(text)
        if key in seen:
            continue
        seen.add(key)
        unique.append((text, intent, lang))

    labels = [u[1] for u in unique]
    train_s, test_s = train_test_split(unique, test_size=0.25, random_state=42, stratify=labels)
    train_aug = augment(train_s) + keyword_rows()

    model = build_pipeline()
    model.fit([t[0] for t in train_aug], [t[1] for t in train_aug])

    y_true = [t[1] for t in test_s]
    y_pred = model.predict([t[0] for t in test_s])
    p, r, f1, _ = precision_recall_fscore_support(y_true, y_pred, average="macro", zero_division=0)
    by_lang = {}
    for lang in ("en", "hi", "mr"):
        idx = [i for i, t in enumerate(test_s) if t[2] == lang]
        if idx:
            by_lang[lang] = round(accuracy_score([y_true[i] for i in idx], [y_pred[i] for i in idx]), 4)

    metrics = {
        "dataset": "SYNTHETIC hand-written phrases - not real or clinical data",
        "total_unique_examples": len(unique),
        "train_examples_before_augmentation": len(train_s),
        "train_examples_after_augmentation": len(train_aug),
        "keyword_lexicon_examples": len(keyword_rows()),
        "test_examples": len(test_s),
        "classes": len(set(labels)),
        "class_counts": dict(Counter(labels)),
        "accuracy": round(accuracy_score(y_true, y_pred), 4),
        "macro_precision": round(float(p), 4),
        "macro_recall": round(float(r), 4),
        "macro_f1": round(float(f1), 4),
        "accuracy_by_language_group": by_lang,
        "per_class": classification_report(y_true, y_pred, zero_division=0, output_dict=True),
        "note": "A curated keyword lexicon (training-only) is added to every fit, so the test split mainly measures generalisation across phrase wording. Scores come from a small synthetic test split and are optimistic; expect lower accuracy on real speech.",
    }

    # deployed model: refit on everything
    final = build_pipeline()
    all_aug = augment(unique) + keyword_rows()
    final.fit([t[0] for t in all_aug], [t[1] for t in all_aug])
    if save:
        MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump(final, MODEL_PATH)
        METRICS_PATH.write_text(json.dumps(metrics, indent=2, ensure_ascii=False), encoding="utf-8")
    return metrics


if __name__ == "__main__":
    m = train()
    print(json.dumps({k: v for k, v in m.items() if k not in ("per_class", "class_counts")}, indent=2, ensure_ascii=False))
