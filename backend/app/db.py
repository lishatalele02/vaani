"""SQLite storage. All queries are parameterised; every row is scoped to a user."""
from __future__ import annotations

import hashlib
import json
import secrets
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from typing import Optional

from .config import settings

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    token_hash TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL DEFAULT '',
    preferred_language TEXT NOT NULL DEFAULT 'mr-IN',
    emergency_name TEXT NOT NULL DEFAULT '',
    emergency_phone TEXT NOT NULL DEFAULT '',
    save_history INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS phrases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    alias TEXT NOT NULL,
    alternatives TEXT NOT NULL DEFAULT '[]',
    canonical_meaning TEXT NOT NULL,
    intent TEXT NOT NULL,
    language TEXT NOT NULL DEFAULT 'mr-IN',
    category TEXT NOT NULL DEFAULT 'custom',
    usage_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_phrases_user ON phrases(user_id);
CREATE TABLE IF NOT EXISTS history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    transcript TEXT NOT NULL DEFAULT '',
    source_language TEXT NOT NULL DEFAULT '',
    meaning TEXT NOT NULL DEFAULT '',
    intent TEXT NOT NULL DEFAULT '',
    confirmed INTEGER NOT NULL DEFAULT 0,
    final_text TEXT NOT NULL DEFAULT '',
    output_language TEXT NOT NULL DEFAULT '',
    shared INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_history_user ON history(user_id, id DESC);
CREATE TABLE IF NOT EXISTS usage (
    user_id TEXT NOT NULL,
    day TEXT NOT NULL,
    kind TEXT NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day, kind)
);
CREATE TABLE IF NOT EXISTS pair_codes (
    code TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    expires_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS pairings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    caregiver_id TEXT NOT NULL,
    share_phrases INTEGER NOT NULL DEFAULT 0,
    share_history INTEGER NOT NULL DEFAULT 0,
    share_emergency INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    UNIQUE (user_id, caregiver_id)
);
"""


def now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def today() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%d")


@contextmanager
def get_conn():
    conn = sqlite3.connect(settings.db_path, timeout=15)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def init_db() -> None:
    with get_conn() as c:
        c.execute("PRAGMA journal_mode = WAL")
        c.executescript(SCHEMA)


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


# ---------------------------------------------------------------- users
def create_user(display_name: str, preferred_language: str) -> tuple:
    user_id = "u_" + secrets.token_hex(8)
    token = secrets.token_urlsafe(32)
    with get_conn() as c:
        c.execute(
            "INSERT INTO users (id, token_hash, display_name, preferred_language, created_at) VALUES (?,?,?,?,?)",
            (user_id, hash_token(token), display_name, preferred_language, now_iso()),
        )
    return user_id, token


def user_by_token(token: str) -> Optional[dict]:
    with get_conn() as c:
        row = c.execute("SELECT * FROM users WHERE token_hash = ?", (hash_token(token),)).fetchone()
    return dict(row) if row else None


def get_user(user_id: str) -> Optional[dict]:
    with get_conn() as c:
        row = c.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
    return dict(row) if row else None


def update_user(user_id: str, fields: dict) -> None:
    allowed = {"display_name", "preferred_language", "emergency_name", "emergency_phone", "save_history"}
    sets = {k: v for k, v in fields.items() if k in allowed and v is not None}
    if not sets:
        return
    cols = ", ".join(f"{k} = ?" for k in sets)
    with get_conn() as c:
        c.execute(f"UPDATE users SET {cols} WHERE id = ?", (*sets.values(), user_id))


def delete_user(user_id: str) -> None:
    with get_conn() as c:
        for table in ("phrases", "history", "usage", "pair_codes"):
            c.execute(f"DELETE FROM {table} WHERE user_id = ?", (user_id,))
        c.execute("DELETE FROM pairings WHERE user_id = ? OR caregiver_id = ?", (user_id, user_id))
        c.execute("DELETE FROM users WHERE id = ?", (user_id,))


# ---------------------------------------------------------------- usage limits
def usage_count(user_id: str, kind: str) -> int:
    with get_conn() as c:
        row = c.execute(
            "SELECT count FROM usage WHERE user_id = ? AND day = ? AND kind = ?", (user_id, today(), kind)
        ).fetchone()
    return int(row["count"]) if row else 0


def usage_total(kind: str) -> int:
    """All users together, today. Protects the shared Gnani credit balance."""
    with get_conn() as c:
        row = c.execute("SELECT COALESCE(SUM(count), 0) AS n FROM usage WHERE day = ? AND kind = ?", (today(), kind)).fetchone()
    return int(row["n"])


def bump_usage(user_id: str, kind: str) -> None:
    with get_conn() as c:
        c.execute(
            "INSERT INTO usage (user_id, day, kind, count) VALUES (?,?,?,1) "
            "ON CONFLICT(user_id, day, kind) DO UPDATE SET count = count + 1",
            (user_id, today(), kind),
        )


# ---------------------------------------------------------------- phrases
def _phrase_row(row: sqlite3.Row) -> dict:
    d = dict(row)
    try:
        d["alternatives"] = json.loads(d.get("alternatives") or "[]")
    except json.JSONDecodeError:
        d["alternatives"] = []
    return d


def list_phrases(user_id: str, q: str = "", category: str = "", language: str = "") -> list:
    sql = "SELECT * FROM phrases WHERE user_id = ?"
    args: list = [user_id]
    if category:
        sql += " AND category = ?"
        args.append(category)
    if language:
        sql += " AND language = ?"
        args.append(language)
    sql += " ORDER BY usage_count DESC, id DESC"
    with get_conn() as c:
        rows = [_phrase_row(r) for r in c.execute(sql, args).fetchall()]
    if q:
        ql = q.strip().lower()
        rows = [
            r for r in rows
            if ql in r["alias"].lower()
            or ql in r["canonical_meaning"].lower()
            or any(ql in a.lower() for a in r["alternatives"])
        ]
    return rows


def get_phrase(user_id: str, phrase_id: int) -> Optional[dict]:
    with get_conn() as c:
        row = c.execute("SELECT * FROM phrases WHERE id = ? AND user_id = ?", (phrase_id, user_id)).fetchone()
    return _phrase_row(row) if row else None


def find_phrase_by_alias(user_id: str, alias: str) -> Optional[dict]:
    with get_conn() as c:
        row = c.execute(
            "SELECT * FROM phrases WHERE user_id = ? AND lower(alias) = lower(?)", (user_id, alias.strip())
        ).fetchone()
    return _phrase_row(row) if row else None


def add_phrase(user_id: str, alias: str, alternatives: list, meaning: str, intent: str, language: str, category: str) -> dict:
    with get_conn() as c:
        cur = c.execute(
            "INSERT INTO phrases (user_id, alias, alternatives, canonical_meaning, intent, language, category, created_at) "
            "VALUES (?,?,?,?,?,?,?,?)",
            (user_id, alias.strip(), json.dumps(alternatives, ensure_ascii=False), meaning.strip(), intent, language, category, now_iso()),
        )
        pid = cur.lastrowid
    return get_phrase(user_id, pid)


def update_phrase(user_id: str, phrase_id: int, alias: str, alternatives: list, meaning: str, intent: str, language: str, category: str) -> Optional[dict]:
    with get_conn() as c:
        cur = c.execute(
            "UPDATE phrases SET alias=?, alternatives=?, canonical_meaning=?, intent=?, language=?, category=? "
            "WHERE id=? AND user_id=?",
            (alias.strip(), json.dumps(alternatives, ensure_ascii=False), meaning.strip(), intent, language, category, phrase_id, user_id),
        )
        if cur.rowcount == 0:
            return None
    return get_phrase(user_id, phrase_id)


def delete_phrase(user_id: str, phrase_id: int) -> bool:
    with get_conn() as c:
        cur = c.execute("DELETE FROM phrases WHERE id = ? AND user_id = ?", (phrase_id, user_id))
        return cur.rowcount > 0


def bump_phrase_usage(user_id: str, phrase_id: int) -> None:
    with get_conn() as c:
        c.execute("UPDATE phrases SET usage_count = usage_count + 1 WHERE id = ? AND user_id = ?", (phrase_id, user_id))


# ---------------------------------------------------------------- history
def add_history(user_id: str, transcript: str, source_language: str, meaning: str, intent: str,
                confirmed: bool, final_text: str, output_language: str) -> int:
    with get_conn() as c:
        cur = c.execute(
            "INSERT INTO history (user_id, transcript, source_language, meaning, intent, confirmed, final_text, output_language, created_at) "
            "VALUES (?,?,?,?,?,?,?,?,?)",
            (user_id, transcript, source_language, meaning, intent, 1 if confirmed else 0, final_text, output_language, now_iso()),
        )
        return cur.lastrowid


def list_history(user_id: str, q: str = "", limit: int = 100) -> list:
    sql = "SELECT * FROM history WHERE user_id = ?"
    args: list = [user_id]
    if q:
        sql += " AND (transcript LIKE ? OR final_text LIKE ? OR meaning LIKE ?)"
        like = f"%{q.strip()}%"
        args += [like, like, like]
    sql += " ORDER BY id DESC LIMIT ?"
    args.append(max(1, min(limit, 500)))
    with get_conn() as c:
        return [dict(r) for r in c.execute(sql, args).fetchall()]


def get_history(user_id: str, history_id: int) -> Optional[dict]:
    with get_conn() as c:
        row = c.execute("SELECT * FROM history WHERE id = ? AND user_id = ?", (history_id, user_id)).fetchone()
    return dict(row) if row else None


def delete_history(user_id: str, history_id: int) -> bool:
    with get_conn() as c:
        return c.execute("DELETE FROM history WHERE id = ? AND user_id = ?", (history_id, user_id)).rowcount > 0


def clear_history(user_id: str) -> int:
    with get_conn() as c:
        return c.execute("DELETE FROM history WHERE user_id = ?", (user_id,)).rowcount


def set_history_shared(user_id: str, history_id: int, shared: bool) -> bool:
    with get_conn() as c:
        return c.execute(
            "UPDATE history SET shared = ? WHERE id = ? AND user_id = ?", (1 if shared else 0, history_id, user_id)
        ).rowcount > 0


# ---------------------------------------------------------------- caregiver pairing
def create_pair_code(user_id: str, minutes: int = 15) -> tuple:
    code = f"{secrets.randbelow(1_000_000):06d}"
    expires = (datetime.now(timezone.utc) + timedelta(minutes=minutes)).strftime("%Y-%m-%dT%H:%M:%SZ")
    with get_conn() as c:
        c.execute("DELETE FROM pair_codes WHERE user_id = ?", (user_id,))
        c.execute("DELETE FROM pair_codes WHERE expires_at < ?", (now_iso(),))
        while c.execute("SELECT 1 FROM pair_codes WHERE code = ?", (code,)).fetchone():
            code = f"{secrets.randbelow(1_000_000):06d}"
        c.execute("INSERT INTO pair_codes (code, user_id, expires_at) VALUES (?,?,?)", (code, user_id, expires))
    return code, expires


def redeem_pair_code(code: str, caregiver_id: str) -> Optional[dict]:
    """Consume a code. Pairing starts with NOTHING shared; the user must grant access."""
    with get_conn() as c:
        row = c.execute("SELECT * FROM pair_codes WHERE code = ?", (code,)).fetchone()
        if not row or row["expires_at"] < now_iso() or row["user_id"] == caregiver_id:
            return None
        c.execute("DELETE FROM pair_codes WHERE code = ?", (code,))
        c.execute(
            "INSERT OR IGNORE INTO pairings (user_id, caregiver_id, created_at) VALUES (?,?,?)",
            (row["user_id"], caregiver_id, now_iso()),
        )
        link = c.execute(
            "SELECT * FROM pairings WHERE user_id = ? AND caregiver_id = ?", (row["user_id"], caregiver_id)
        ).fetchone()
    return dict(link)


def links_for_user(user_id: str) -> list:
    with get_conn() as c:
        rows = c.execute(
            "SELECT p.*, u.display_name AS caregiver_name FROM pairings p JOIN users u ON u.id = p.caregiver_id "
            "WHERE p.user_id = ? ORDER BY p.id",
            (user_id,),
        ).fetchall()
    return [dict(r) for r in rows]


def update_link(user_id: str, link_id: int, share_phrases: bool, share_history: bool, share_emergency: bool) -> bool:
    with get_conn() as c:
        return c.execute(
            "UPDATE pairings SET share_phrases=?, share_history=?, share_emergency=? WHERE id=? AND user_id=?",
            (int(share_phrases), int(share_history), int(share_emergency), link_id, user_id),
        ).rowcount > 0


def delete_link(user_id: str, link_id: int) -> bool:
    with get_conn() as c:
        return c.execute("DELETE FROM pairings WHERE id = ? AND user_id = ?", (link_id, user_id)).rowcount > 0


def people_for_caregiver(caregiver_id: str) -> list:
    with get_conn() as c:
        rows = c.execute(
            "SELECT p.*, u.display_name AS person_name FROM pairings p JOIN users u ON u.id = p.user_id "
            "WHERE p.caregiver_id = ? ORDER BY p.id",
            (caregiver_id,),
        ).fetchall()
    return [dict(r) for r in rows]


def link_for_caregiver(caregiver_id: str, user_id: str) -> Optional[dict]:
    with get_conn() as c:
        row = c.execute(
            "SELECT * FROM pairings WHERE caregiver_id = ? AND user_id = ?", (caregiver_id, user_id)
        ).fetchone()
    return dict(row) if row else None


def shared_history(user_id: str, all_history: bool, limit: int = 50) -> list:
    sql = "SELECT * FROM history WHERE user_id = ?"
    if not all_history:
        sql += " AND shared = 1"
    sql += " ORDER BY id DESC LIMIT ?"
    with get_conn() as c:
        return [dict(r) for r in c.execute(sql, (user_id, limit)).fetchall()]


def leave_link(caregiver_id: str, user_id: str) -> bool:
    with get_conn() as c:
        return c.execute(
            "DELETE FROM pairings WHERE caregiver_id = ? AND user_id = ?", (caregiver_id, user_id)
        ).rowcount > 0
