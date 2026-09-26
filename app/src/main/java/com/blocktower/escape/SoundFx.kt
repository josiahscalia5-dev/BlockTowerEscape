package com.blocktower.escape

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.blocktower.escape.core.Sfx
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Sound effects synthesised at first launch (no audio assets needed), written as small
 * WAV files to the cache and played through a SoundPool.
 */
class SoundFx(context: Context) {
    private val rate = 22050
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .build()
    private val ids = IntArray(Sfx.COUNT) { 0 }
    @Volatile private var ready = false

    init {
        val dir = File(context.cacheDir, "sfx_v1").apply { mkdirs() }
        Thread {
            try {
                for (id in 0 until Sfx.COUNT) {
                    val f = File(dir, "s$id.wav")
                    if (!f.exists()) writeWav(f, synth(id))
                    ids[id] = pool.load(f.absolutePath, 1)
                }
                ready = true
            } catch (_: Throwable) { }
        }.start()
    }

    fun play(id: Int, volume: Float, pitch: Float) {
        if (!ready || id < 0 || id >= ids.size || ids[id] == 0) return
        val v = volume.coerceIn(0f, 1f) * 0.9f
        pool.play(ids[id], v, v, 1, 0, pitch.coerceIn(0.5f, 2f))
    }

    fun release() { pool.release() }

    // ------------------------------------------------------------------ synthesis
    private val rnd = java.util.Random(7)

    private fun buf(sec: Float) = FloatArray((sec * rate).toInt())
    private fun env(i: Int, n: Int, attack: Float = 0.005f, decay: Float = 6f): Float {
        val t = i.toFloat() / rate
        val a = min(1f, t / attack)
        return a * exp(-decay * t / (n.toFloat() / rate))
    }
    private fun tone(out: FloatArray, f0: Float, f1: Float, amp: Float, start: Float = 0f, len: Float = -1f,
                     decay: Float = 5f, wave: Int = 0) {
        val s0 = (start * rate).toInt()
        val n = if (len < 0) out.size - s0 else min(out.size - s0, (len * rate).toInt())
        var ph = 0.0
        for (i in 0 until n) {
            val f = f0 + (f1 - f0) * i / n
            ph += 2 * PI * f / rate
            val w = when (wave) {
                1 -> if (sin(ph) >= 0) 0.6 else -0.6          // square
                2 -> ((ph / (2 * PI)) % 1.0) * 1.2 - 0.6     // saw
                else -> sin(ph)
            }
            out[s0 + i] += (w * amp * env(i, n, 0.004f, decay)).toFloat()
        }
    }
    private fun noise(out: FloatArray, amp: Float, start: Float = 0f, len: Float = -1f, decay: Float = 5f, lp: Float = 0.3f) {
        val s0 = (start * rate).toInt()
        val n = if (len < 0) out.size - s0 else min(out.size - s0, (len * rate).toInt())
        var y = 0f
        for (i in 0 until n) {
            val x = rnd.nextFloat() * 2f - 1f
            y += (x - y) * lp
            out[s0 + i] += y * amp * env(i, n, 0.003f, decay)
        }
    }
    private fun notes(out: FloatArray, freqs: FloatArray, step: Float, len: Float, amp: Float, wave: Int = 0) {
        for ((k, f) in freqs.withIndex()) tone(out, f, f, amp, k * step, len, 4f, wave)
    }

    private fun synth(id: Int): FloatArray = when (id) {
        Sfx.COIN -> buf(0.22f).also { tone(it, 1320f, 1320f, 0.45f, 0f, 0.07f, 3f); tone(it, 1760f, 1760f, 0.45f, 0.06f, 0.16f, 5f) }
        Sfx.TARGET -> buf(0.5f).also { noise(it, 0.35f, 0f, 0.18f, 7f, 0.9f); notes(it, floatArrayOf(880f, 1320f, 1760f, 2349f), 0.05f, 0.3f, 0.3f) }
        Sfx.WRONG -> buf(0.3f).also { tone(it, 160f, 120f, 0.35f, 0f, 0.3f, 4f, 1) }
        Sfx.JUMP -> buf(0.18f).also { tone(it, 320f, 760f, 0.45f, 0f, 0.18f, 4f) }
        Sfx.LAND -> buf(0.15f).also { tone(it, 120f, 60f, 0.6f, 0f, 0.15f, 6f); noise(it, 0.3f, 0f, 0.08f, 8f, 0.15f) }
        Sfx.BOUNCE -> buf(0.35f).also { tone(it, 220f, 980f, 0.5f, 0f, 0.35f, 3f) }
        Sfx.HIT -> buf(0.35f).also { noise(it, 0.5f, 0f, 0.12f, 6f, 0.5f); tone(it, 420f, 140f, 0.45f, 0f, 0.35f, 4f, 1) }
        Sfx.FALL -> buf(0.7f).also { tone(it, 900f, 180f, 0.4f, 0f, 0.7f, 2f) }
        Sfx.RESCUE -> buf(0.8f).also { notes(it, floatArrayOf(523f, 659f, 784f, 1046f, 1318f), 0.08f, 0.4f, 0.28f) }
        Sfx.CHECKPOINT -> buf(1.0f).also { notes(it, floatArrayOf(523f, 659f, 784f), 0.09f, 0.7f, 0.25f); tone(it, 1046f, 1046f, 0.3f, 0.27f, 0.7f, 3f) }
        Sfx.MAGNET -> buf(0.5f).also { for (k in 0..5) tone(it, 220f + k * 40f, 260f + k * 40f, 0.18f, k * 0.06f, 0.2f, 4f, 2) }
        Sfx.SHIELD -> buf(0.55f).also { tone(it, 600f, 1250f, 0.35f, 0f, 0.5f, 3f); tone(it, 606f, 1262f, 0.25f, 0f, 0.5f, 3f) }
        Sfx.SPEED -> buf(0.45f).also { tone(it, 200f, 1600f, 0.4f, 0f, 0.45f, 3f, 2); noise(it, 0.2f, 0f, 0.4f, 4f, 0.6f) }
        Sfx.BLOCK -> buf(0.3f).also { noise(it, 0.25f, 0f, 0.05f, 8f, 0.4f); tone(it, 660f, 990f, 0.4f, 0.02f, 0.25f, 4f) }
        Sfx.MYSTERY -> buf(0.4f).also { tone(it, 988f, 988f, 0.4f, 0f, 0.12f, 4f); tone(it, 1319f, 1319f, 0.4f, 0.1f, 0.3f, 4f) }
        Sfx.WARNING -> buf(1.3f).also { for (k in 0..3) tone(it, if (k % 2 == 0) 620f else 460f, if (k % 2 == 0) 620f else 460f, 0.32f, k * 0.3f, 0.3f, 1f, 1) }
        Sfx.STOMP -> buf(0.4f).also { tone(it, 70f, 40f, 0.8f, 0f, 0.4f, 5f); noise(it, 0.4f, 0f, 0.2f, 6f, 0.08f) }
        Sfx.ROAR -> buf(1.3f).also { r -> noise(r, 0.7f, 0f, 1.3f, 2f, 0.12f); tone(r, 110f, 70f, 0.4f, 0f, 1.3f, 2f, 2); for (i in r.indices) r[i] *= (0.7f + 0.3f * sin(i * 2f * PI.toFloat() * 9f / rate)) }
        Sfx.DRAGON -> buf(0.9f).also { tone(it, 900f, 380f, 0.35f, 0f, 0.9f, 2f, 2); noise(it, 0.35f, 0f, 0.9f, 2.5f, 0.35f) }
        Sfx.EXPLODE -> buf(0.9f).also { noise(it, 0.9f, 0f, 0.9f, 3.5f, 0.12f); tone(it, 90f, 40f, 0.5f, 0f, 0.5f, 4f) }
        Sfx.CRUMBLE -> buf(0.5f).also { r -> for (k in 0 until 14) noise(r, 0.35f, rnd.nextFloat() * 0.4f, 0.05f, 9f, 0.4f); noise(r, 0.2f, 0f, 0.5f, 4f, 0.08f) }
        Sfx.PORTAL -> buf(1.1f).also { noise(it, 0.3f, 0f, 1.1f, 2f, 0.2f); notes(it, floatArrayOf(392f, 523f, 659f, 784f, 1046f), 0.12f, 0.5f, 0.2f) }
        Sfx.WIN -> buf(1.6f).also { notes(it, floatArrayOf(523f, 659f, 784f, 1046f, 784f, 1046f, 1318f), 0.12f, 0.45f, 0.28f, 1) }
        Sfx.LOSE -> buf(1.3f).also { notes(it, floatArrayOf(392f, 370f, 349f, 330f), 0.25f, 0.4f, 0.3f, 1) }
        Sfx.CLICK -> buf(0.05f).also { tone(it, 1500f, 1200f, 0.4f, 0f, 0.05f, 8f) }
        Sfx.TICK -> buf(0.08f).also { tone(it, 1000f, 1000f, 0.35f, 0f, 0.06f, 8f) }
        Sfx.WIND -> buf(1.1f).also { r -> noise(r, 0.5f, 0f, 1.1f, 1.2f, 0.05f); for (i in r.indices) r[i] *= sin(PI.toFloat() * i / r.size) }
        Sfx.THUNDER -> buf(1.6f).also { noise(it, 0.9f, 0f, 1.6f, 2.2f, 0.06f); noise(it, 0.5f, 0f, 0.15f, 8f, 0.5f) }
        Sfx.GEM -> buf(0.4f).also { tone(it, 1568f, 1568f, 0.3f, 0f, 0.3f, 4f); tone(it, 2093f, 2093f, 0.3f, 0.07f, 0.3f, 4f) }
        Sfx.TOOLGET -> buf(0.45f).also { notes(it, floatArrayOf(659f, 831f, 988f), 0.07f, 0.25f, 0.3f) }
        else -> buf(0.05f)
    }

    private fun writeWav(f: File, data: FloatArray) {
        var peak = 0.001f
        for (v in data) peak = max(peak, kotlin.math.abs(v))
        val g = if (peak > 0.95f) 0.95f / peak else 1f
        val pcm = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (v in data) pcm.putShort((v * g * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()); header.putInt(36 + data.size * 2); header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()); header.putInt(16); header.putShort(1); header.putShort(1)
        header.putInt(rate); header.putInt(rate * 2); header.putShort(2); header.putShort(16)
        header.put("data".toByteArray()); header.putInt(data.size * 2)
        FileOutputStream(f).use { it.write(header.array()); it.write(pcm.array()) }
    }
}
