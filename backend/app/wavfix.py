"""Make WAV audio from Gnani safe for Android's MediaPlayer.

Streamed / generated WAV files often carry a wrong or placeholder length in the
header (0, 0xFFFFFFFF, or larger than the file). Desktop players ignore this;
Android's MediaPlayer refuses to open the file. We read the real PCM bytes and
write a clean 44-byte header (16-bit PCM). Non-WAV data is returned unchanged.
"""
from __future__ import annotations

import struct
import sys
from array import array
from typing import Optional


def parse_wav(data: bytes) -> Optional[dict]:
    """Return {'tag','channels','rate','bits','pcm'} or None if this is not a readable WAV."""
    if len(data) < 12 or data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        return None
    n = len(data)
    pos = 12
    fmt = None
    while pos + 8 <= n:
        cid = data[pos:pos + 4]
        size = struct.unpack("<I", data[pos + 4:pos + 8])[0]
        body = pos + 8
        if cid == b"fmt " and body + 16 <= n:
            tag, ch, rate, _byterate, _align, bits = struct.unpack("<HHIIHH", data[body:body + 16])
            if tag == 0xFFFE and size >= 40 and body + 26 <= n:  # WAVE_FORMAT_EXTENSIBLE
                tag = struct.unpack("<H", data[body + 24:body + 26])[0]
            fmt = {"tag": tag, "channels": ch, "rate": rate, "bits": bits}
        elif cid == b"data":
            remaining = n - body
            if size == 0 or size > remaining:  # unknown/streamed length
                size = remaining
            if fmt is None:
                return None
            fmt["pcm"] = data[body:body + size]
            return fmt
        pos = body + size + (size & 1)
    return None


def describe(data: bytes) -> str:
    """Short text for logs, e.g. 'RIFF/WAVE tag=1 ch=1 rate=48000 bits=16 pcm=199680B'."""
    w = parse_wav(data)
    if not w:
        return f"not a readable WAV; first bytes={data[:16].hex()}"
    return f"RIFF/WAVE tag={w['tag']} ch={w['channels']} rate={w['rate']} bits={w['bits']} pcm={len(w['pcm'])}B"


def _to_int16(w: dict) -> Optional[bytes]:
    tag, bits, pcm = w["tag"], w["bits"], w["pcm"]
    if tag == 1 and bits == 16:
        return pcm[: len(pcm) - (len(pcm) % 2)]
    out = array("h")
    if tag == 3 and bits == 32:  # IEEE float
        f = array("f")
        f.frombytes(pcm[: len(pcm) - (len(pcm) % 4)])
        if sys.byteorder == "big":
            f.byteswap()
        out.extend(max(-32768, min(32767, int(v * 32767))) for v in f)
    elif tag == 1 and bits == 32:
        a = array("i")
        a.frombytes(pcm[: len(pcm) - (len(pcm) % 4)])
        if sys.byteorder == "big":
            a.byteswap()
        out.extend(v >> 16 for v in a)
    elif tag == 1 and bits == 24:
        for i in range(0, len(pcm) - 2, 3):
            out.append(struct.unpack("<h", pcm[i + 1:i + 3])[0])
    elif tag == 1 and bits == 8:
        out.extend((b - 128) << 8 for b in pcm)
    else:
        return None
    if sys.byteorder == "big":
        out.byteswap()
    return out.tobytes()


def normalize_wav(data: bytes) -> bytes:
    """Return a clean 16-bit PCM WAV, or the original bytes if they cannot be parsed."""
    w = parse_wav(data)
    if not w or w["channels"] < 1 or w["rate"] < 1000:
        return data
    pcm = _to_int16(w)
    if pcm is None:
        return data
    ch, rate = w["channels"], w["rate"]
    frame = 2 * ch
    pcm = pcm[: len(pcm) - (len(pcm) % frame)]
    if not pcm:
        return data
    header = (
        b"RIFF" + struct.pack("<I", 36 + len(pcm)) + b"WAVE"
        + b"fmt " + struct.pack("<IHHIIHH", 16, 1, ch, rate, rate * frame, frame, 16)
        + b"data" + struct.pack("<I", len(pcm))
    )
    return header + pcm


def streaming_wav_header(sample_rate: int = 24000, channels: int = 1, sample_width: int = 2) -> bytes:
    """44-byte WAV header for a stream of unknown length (sizes set to 0xFFFFFFFF).

    Used when raw PCM chunks from Timbre SSE are forwarded to the phone while they
    are still being generated.
    """
    import struct

    byte_rate = sample_rate * channels * sample_width
    return (
        b"RIFF" + struct.pack("<I", 0xFFFFFFFF) + b"WAVE"
        + b"fmt " + struct.pack("<IHHIIHH", 16, 1, channels, sample_rate, byte_rate, channels * sample_width, sample_width * 8)
        + b"data" + struct.pack("<I", 0xFFFFFFFF)
    )
