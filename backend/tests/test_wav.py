import io
import struct
import wave

from app.wavfix import normalize_wav, parse_wav


def _wav_bytes(frames=4800, rate=48000, ch=1, tag=1, bits=16, data_size=None, extra_chunk=False, riff_size=None):
    if tag == 3:
        pcm = struct.pack("<%df" % frames, *([0.5] * frames))
    else:
        pcm = struct.pack("<%dh" % frames, *([1000] * frames))
    fmt = struct.pack("<HHIIHH", tag, ch, rate, rate * ch * bits // 8, ch * bits // 8, bits)
    out = b"RIFF" + struct.pack("<I", riff_size if riff_size is not None else 36 + len(pcm)) + b"WAVE"
    out += b"fmt " + struct.pack("<I", 16) + fmt
    if extra_chunk:
        out += b"LIST" + struct.pack("<I", 5) + b"abcde" + b"\x00"  # odd size + pad byte
    out += b"data" + struct.pack("<I", len(pcm) if data_size is None else data_size) + pcm
    return out


def _check(raw, frames=4800):
    fixed = normalize_wav(raw)
    with wave.open(io.BytesIO(fixed)) as w:  # strict reader: fails on bad headers
        assert w.getnframes() == frames and w.getframerate() == 48000 and w.getsampwidth() == 2
    return fixed


def test_clean_wav_unchanged_audio():
    _check(_wav_bytes())


def test_streamed_header_unknown_length():
    _check(_wav_bytes(data_size=0xFFFFFFFF, riff_size=0xFFFFFFFF))
    _check(_wav_bytes(data_size=0, riff_size=0))


def test_extra_chunk_before_data():
    _check(_wav_bytes(extra_chunk=True))


def test_float32_converted_to_int16():
    fixed = _check(_wav_bytes(tag=3, bits=32))
    assert struct.unpack("<h", fixed[44:46])[0] > 10000


def test_non_wav_untouched():
    assert normalize_wav(b"ID3\x03not wav at all" * 10) == b"ID3\x03not wav at all" * 10
    assert parse_wav(b"") is None
