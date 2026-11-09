package com.samajhsakhi.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt

/** Result of a finished recording. [peak] is the loudest level seen (0..1). */
data class RecordingResult(val file: File, val durationMs: Long, val peak: Float)

/**
 * Records 16 kHz mono 16-bit PCM and writes a standard WAV file, a format Gnani Prisma accepts.
 * Recording stops automatically at [MAX_SECONDS] (the Prisma REST limit).
 */
class WavRecorder(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_SECONDS = 58
        private const val BYTES_PER_MS = SAMPLE_RATE * 2 / 1000 // 32
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var file: File? = null
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var recording = false
    @Volatile private var totalBytes = 0L
    @Volatile private var peak = 0f

    val isRecording: Boolean get() = recording

    /** Throws IllegalStateException with a friendly message if the microphone cannot be used. */
    @SuppressLint("MissingPermission") // the UI asks for RECORD_AUDIO before calling start()
    fun start(onLevel: (Float) -> Unit, onLimit: () -> Unit) {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) throw IllegalStateException("The microphone is not available on this device.")
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8192) * 2,
            )
        } catch (e: SecurityException) {
            throw IllegalStateException("Microphone permission is needed.")
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("The microphone is busy or unavailable. Close other apps using it and try again.")
        }
        val out = File(context.cacheDir, "recording_${System.currentTimeMillis()}.wav")
        val raf = RandomAccessFile(out, "rw")
        raf.setLength(0)
        raf.write(ByteArray(44)) // header placeholder, filled in when we stop

        record = rec
        file = out
        totalBytes = 0
        peak = 0f
        recording = true
        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            recording = false
            rec.release()
            raf.close()
            out.delete()
            throw IllegalStateException("The microphone could not start. Please try again.")
        }

        thread = Thread {
            val buf = ByteArray(4096)
            try {
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        raf.write(buf, 0, n)
                        totalBytes += n
                        val level = rms(buf, n)
                        if (level > peak) peak = level
                        onLevel(level)
                        if (totalBytes >= MAX_SECONDS * 1000L * BYTES_PER_MS) {
                            recording = false
                            main.post(onLimit)
                        }
                    } else if (n < 0) {
                        break
                    }
                }
            } catch (e: Exception) {
                // ignore: stop() will validate what was written
            } finally {
                try { raf.close() } catch (e: Exception) { }
            }
        }.also { it.start() }
    }

    /** Stops recording. Returns null if nothing usable was recorded. */
    fun stop(): RecordingResult? {
        val rec = record ?: return null
        recording = false
        try { thread?.join(1500) } catch (e: InterruptedException) { }
        try { rec.stop() } catch (e: IllegalStateException) { }
        rec.release()
        record = null
        thread = null
        val f = file ?: return null
        file = null
        val bytes = totalBytes
        if (bytes <= 0) {
            f.delete()
            return null
        }
        return try {
            writeHeader(f, bytes)
            RecordingResult(f, bytes / BYTES_PER_MS, peak)
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    /** Stops and deletes everything (used when leaving the screen). */
    fun cancel() {
        val res = stop()
        res?.file?.delete()
    }

    private fun rms(buf: ByteArray, n: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < n) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
            sum += s.toDouble() * s
            count++
            i += 2
        }
        if (count == 0) return 0f
        return (sqrt(sum / count) / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    private fun writeHeader(f: File, dataBytes: Long) {
        RandomAccessFile(f, "rw").use { raf ->
            val h = ByteArray(44)
            fun put32(off: Int, v: Long) {
                h[off] = (v and 0xFF).toByte()
                h[off + 1] = ((v shr 8) and 0xFF).toByte()
                h[off + 2] = ((v shr 16) and 0xFF).toByte()
                h[off + 3] = ((v shr 24) and 0xFF).toByte()
            }
            fun put16(off: Int, v: Int) {
                h[off] = (v and 0xFF).toByte()
                h[off + 1] = ((v shr 8) and 0xFF).toByte()
            }
            "RIFF".toByteArray().copyInto(h, 0)
            put32(4, 36 + dataBytes)
            "WAVE".toByteArray().copyInto(h, 8)
            "fmt ".toByteArray().copyInto(h, 12)
            put32(16, 16)
            put16(20, 1)            // PCM
            put16(22, 1)            // mono
            put32(24, SAMPLE_RATE.toLong())
            put32(28, SAMPLE_RATE * 2L)
            put16(32, 2)            // block align
            put16(34, 16)           // bits per sample
            "data".toByteArray().copyInto(h, 36)
            put32(40, dataBytes)
            raf.seek(0)
            raf.write(h)
        }
    }
}
