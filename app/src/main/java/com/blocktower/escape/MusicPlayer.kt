package com.blocktower.escape

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.blocktower.escape.core.Music
import com.blocktower.escape.core.MusicSynth
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Plays the music. A background thread synthesises the themes once at launch (Home first); the music thread
 * streams the wanted theme through an AudioTrack, mixing its layers live with gains that follow the game's
 * intensity (so the drums and the rush fade in and out smoothly), crossfading when the theme changes.
 */
class MusicPlayer {
    @Volatile private var wantTrack = Music.NONE
    @Volatile private var wantIntensity = 0f
    @Volatile private var wantVolume = 1f
    @Volatile private var paused = false
    @Volatile private var running = true
    private val themes = ConcurrentHashMap<Int, Array<ShortArray>>()

    private val renderer = Thread({
        try {
            val synth = MusicSynth()
            for (t in intArrayOf(Music.HOME, Music.SKY, Music.JUNGLE)) {
                if (!running) return@Thread
                val layers = synth.render(t)
                themes[t] = Array(layers.size) { l -> ShortArray(layers[l].size) { i -> (layers[l][i] * 32767f).toInt().coerceIn(-32768, 32767).toShort() } }
            }
        } catch (_: Throwable) { }
    }, "music-render").apply { priority = Thread.MIN_PRIORITY; start() }

    private val player = Thread({ play() }, "music").apply { start() }

    fun set(track: Int, intensity: Float, volume: Float) { wantTrack = track; wantIntensity = intensity; wantVolume = volume }
    fun pause() { paused = true }
    fun resume() { paused = false }
    fun release() { running = false; player.join(500) }

    private fun play() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        val rate = Music.RATE
        val track = try {
            val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(max(minBuf, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Throwable) { return }
        val chunk = ShortArray(1024)
        val gains = FloatArray(Music.LAYERS)
        val live = FloatArray(Music.LAYERS)
        var cur = Music.NONE
        var pos = 0
        var fade = 0f
        var level = 0f
        var started = false
        try {
            while (running) {
                if (paused) {
                    if (started) { track.pause(); started = false }
                    Thread.sleep(50); continue
                }
                // a different theme: fade the current one out, then start the new one from its beginning
                val want = wantTrack
                val step = chunk.size.toFloat() / rate
                if (want != cur) {
                    fade -= step / 0.45f
                    if (fade <= 0f) {
                        fade = 0f; pos = 0
                        if (want == Music.NONE || themes.containsKey(want)) {
                            cur = want
                            Music.layerGains(cur, wantIntensity, gains); for (l in live.indices) live[l] = gains[l]
                        }
                    }
                } else fade = min(1f, fade + step / 0.6f)
                val layers = themes[cur]
                if (cur == Music.NONE || layers == null) {
                    if (started) { track.pause(); track.flush(); started = false }
                    Thread.sleep(40); continue
                }
                if (!started) { track.play(); started = true }
                Music.layerGains(cur, wantIntensity, gains)
                val k = min(1f, step / 0.9f)
                for (l in live.indices) live[l] += (gains[l] - live[l]) * k
                level += (wantVolume * MUSIC_LEVEL - level) * min(1f, step / 0.3f)
                val n = layers[0].size
                for (i in chunk.indices) {
                    var v = 0f
                    for (l in layers.indices) v += layers[l][pos] * live[l]
                    v *= fade * level / 32768f
                    // a gentle limiter instead of hard clipping
                    if (abs(v) > 0.85f) v = (if (v > 0f) 1f else -1f) * (0.85f + 0.15f * (1f - 1f / (1f + (abs(v) - 0.85f) * 6f)))
                    chunk[i] = (v * 32767f).toInt().toShort()
                    pos++; if (pos >= n) pos = 0
                }
                track.write(chunk, 0, chunk.size)
            }
        } catch (_: Throwable) {
        } finally {
            try { track.stop() } catch (_: Throwable) { }
            track.release()
        }
    }

    companion object {
        /** The music sits under the sound effects. */
        const val MUSIC_LEVEL = 0.5f
    }
}
