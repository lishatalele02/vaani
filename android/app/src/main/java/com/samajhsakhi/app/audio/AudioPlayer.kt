package com.samajhsakhi.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

enum class PlayState { Idle, Playing, Paused, Error }

private class WavInfo(val channels: Int, val rate: Int, val dataOffset: Int, val dataLen: Int)

/** Minimal, forgiving WAV reader (16-bit PCM). Tolerates wrong length fields in the header. */
private fun parseWav(b: ByteArray): WavInfo? {
    fun u16(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    fun u32(o: Int) = u16(o).toLong() or (u16(o + 2).toLong() shl 16)
    if (b.size < 44 || String(b, 0, 4, Charsets.ISO_8859_1) != "RIFF" || String(b, 8, 4, Charsets.ISO_8859_1) != "WAVE") return null
    var pos = 12
    var channels = 0
    var rate = 0
    var bits = 0
    var tag = 0
    while (pos + 8 <= b.size) {
        val id = String(b, pos, 4, Charsets.ISO_8859_1)
        val size = u32(pos + 4)
        val body = pos + 8
        if (id == "fmt " && body + 16 <= b.size) {
            tag = u16(body)
            channels = u16(body + 2)
            rate = u32(body + 4).toInt()
            bits = u16(body + 14)
        } else if (id == "data") {
            val remaining = (b.size - body).toLong()
            val len = if (size <= 0L || size > remaining) remaining else size
            if (tag != 1 || bits != 16 || channels !in 1..2 || rate < 8000) return null
            return WavInfo(channels, rate, body, (len - len % (2L * channels)).toInt())
        }
        val next = body.toLong() + size + (size and 1L)
        if (next >= b.size) break
        pos = next.toInt()
    }
    return null
}

/**
 * Plays a local audio file and exposes state/progress (play, pause, resume, stop, replay).
 * Uses MediaPlayer first; if the device cannot open the file, falls back to decoding the
 * WAV ourselves and playing it with AudioTrack.
 */
class AudioPlayer {
    private val _state = MutableStateFlow(PlayState.Idle)
    private val _position = MutableStateFlow(0)
    private val _duration = MutableStateFlow(0)
    val state: StateFlow<PlayState> = _state
    val positionMs: StateFlow<Int> = _position
    val durationMs: StateFlow<Int> = _duration

    private var player: MediaPlayer? = null
    private var track: AudioTrack? = null
    private var trackRate = 1
    private var trackFrames = 0
    private var currentFile: File? = null
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            val t = track
            if (t != null) {
                try {
                    val head = t.playbackHeadPosition
                    _position.value = (head * 1000L / trackRate).toInt()
                    if (trackFrames > 0 && head >= trackFrames) {
                        finishTrack()
                        return
                    }
                } catch (e: IllegalStateException) {
                    return
                }
                handler.postDelayed(this, 150)
                return
            }
            val p = player ?: return
            try {
                if (p.isPlaying) _position.value = p.currentPosition
            } catch (e: IllegalStateException) {
                return
            }
            handler.postDelayed(this, 150)
        }
    }

    private fun startTicker() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    fun play(file: File) {
        stop()
        currentFile = file
        if (playWithMediaPlayer(file)) return
        if (playWithTrack(file)) return
        _state.value = PlayState.Error
    }

    private fun playWithMediaPlayer(file: File): Boolean {
        return try {
            val p = MediaPlayer()
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener {
                handler.removeCallbacks(ticker)
                _position.value = 0
                _state.value = PlayState.Idle
            }
            p.setOnErrorListener { _, _, _ ->
                handler.removeCallbacks(ticker)
                _state.value = PlayState.Error
                true
            }
            try {
                p.prepare()
            } catch (e: Exception) {
                p.release()
                throw e
            }
            _duration.value = p.duration
            _position.value = 0
            player = p
            p.start()
            _state.value = PlayState.Playing
            startTicker()
            true
        } catch (e: Exception) {
            releasePlayer()
            false
        }
    }

    private fun playWithTrack(file: File): Boolean {
        try {
            val bytes = file.readBytes()
            val w = parseWav(bytes)
            if (w == null || w.dataLen <= 0) return false
            val mask = if (w.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(w.rate)
                        .setChannelMask(mask)
                        .build(),
                )
                .setBufferSizeInBytes(w.dataLen)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(bytes, w.dataOffset, w.dataLen)
            trackRate = w.rate
            trackFrames = w.dataLen / (2 * w.channels)
            track = t
            _duration.value = (trackFrames * 1000L / w.rate).toInt()
            _position.value = 0
            t.play()
            _state.value = PlayState.Playing
            startTicker()
            return true
        } catch (e: Exception) {
            releaseTrack()
            return false
        }
    }

    fun pause() {
        val t = track
        if (t != null) {
            try {
                t.pause()
                _state.value = PlayState.Paused
            } catch (e: IllegalStateException) {
                _state.value = PlayState.Error
            }
            return
        }
        val p = player ?: return
        try {
            if (p.isPlaying) {
                p.pause()
                _state.value = PlayState.Paused
            }
        } catch (e: IllegalStateException) {
            _state.value = PlayState.Error
        }
    }

    fun resume() {
        val t = track
        if (t != null) {
            try {
                t.play()
                _state.value = PlayState.Playing
                startTicker()
            } catch (e: IllegalStateException) {
                _state.value = PlayState.Error
            }
            return
        }
        val p = player ?: return
        try {
            p.start()
            _state.value = PlayState.Playing
            startTicker()
        } catch (e: IllegalStateException) {
            _state.value = PlayState.Error
        }
    }

    fun replay() {
        val f = currentFile ?: return
        play(f)
    }

    fun stop() {
        handler.removeCallbacks(ticker)
        releasePlayer()
        releaseTrack()
        _position.value = 0
        _state.value = PlayState.Idle
    }

    fun release() {
        stop()
        currentFile = null
    }

    private fun finishTrack() {
        handler.removeCallbacks(ticker)
        releaseTrack()
        _position.value = 0
        _state.value = PlayState.Idle
    }

    private fun releasePlayer() {
        try {
            player?.stop()
        } catch (e: IllegalStateException) {
        }
        player?.release()
        player = null
    }

    private fun releaseTrack() {
        try {
            track?.stop()
        } catch (e: IllegalStateException) {
        }
        track?.release()
        track = null
    }
}
