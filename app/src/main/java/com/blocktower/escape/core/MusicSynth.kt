package com.blocktower.escape.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The music: which theme is playing and how its layers follow the action. */
object Music {
    const val NONE = 0; const val HOME = 1; const val SKY = 2; const val JUNGLE = 3
    const val RATE = 22050
    /** Every theme is three layers of the same length, played in sync: the tune, the drums, the rush. */
    const val LAYERS = 3

    /**
     * How loud each layer plays. The Home theme always plays whole; in a level the tune always plays, the drums
     * build with the action and the rush (fast arpeggios, open hats, fills) only joins when things get intense:
     * the final approach to the portal, a chase, rising lava, the final escape.
     */
    fun layerGains(track: Int, intensity: Float, out: FloatArray) {
        if (track == HOME) { out[0] = 1f; out[1] = 0.75f; out[2] = 0.55f; return }
        val i = clamp01(intensity)
        out[0] = 1f
        out[1] = 0.45f + 0.55f * smooth(i / 0.5f)
        out[2] = smooth((i - 0.45f) / 0.4f)
    }
}

/**
 * Synthesises the music themes as seamless loops (pure Kotlin, no audio files): wavetable instruments for the
 * tunes and noise / pitch-sweep drums, written with wrap-around so the last notes' tails run into the start of the
 * loop. Each theme renders to [Music.LAYERS] float arrays of the same length, normalised so all layers together
 * never clip.
 */
class MusicSynth(private val rate: Int = Music.RATE) {
    private var seed = 0x2545F491
    private fun rnd(): Float { seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5); return (seed and 0xFFFFFF) / 8388608f - 1f }

    // ------------------------------------------------------------------ wavetables
    private val size = 1024
    private fun table(vararg harmonics: Float): FloatArray {
        val t = FloatArray(size)
        for (i in 0 until size) {
            var v = 0.0
            for ((k, a) in harmonics.withIndex()) v += a * sin(2 * PI * (k + 1) * i / size)
            t[i] = v.toFloat()
        }
        val peak = t.maxOf { kotlin.math.abs(it) }
        for (i in t.indices) t[i] /= peak
        return t
    }
    private val square = table(1f, 0f, 0.33f * 0.85f, 0f, 0.2f * 0.7f, 0f, 0.14f * 0.6f, 0f, 0.11f * 0.5f, 0f, 0.09f * 0.4f)
    private val saw = table(1f, 0.5f * 0.9f, 0.33f * 0.8f, 0.25f * 0.7f, 0.2f * 0.6f, 0.17f * 0.5f, 0.14f * 0.4f, 0.12f * 0.3f)
    private val tri = table(1f, 0f, -0.11f, 0f, 0.04f, 0f, -0.02f)
    private val organ = table(1f, 0.5f, 0.25f, 0.12f)
    private val marimba = table(1f, 0f, 0f, 0.35f, 0f, 0f, 0f, 0f, 0f, 0.08f)
    private val bell = table(1f, 0.45f, 0.1f, 0.25f, 0f, 0.06f)

    private fun hz(midi: Int) = 440f * 2f.pow((midi - 69) / 12f)

    // ------------------------------------------------------------------ the loop being written
    private lateinit var layers: Array<FloatArray>
    private var n = 0
    private var spb = 0f          // samples per beat

    private fun add(layer: Int, pos: Int, v: Float) { val b = layers[layer]; val i = pos % n; b[i] += v }

    /** A tuned note: [beat] and [len] in beats, envelope attack / decay-to-sustain / release in seconds. */
    private fun note(layer: Int, tab: FloatArray, midi: Int, beat: Float, len: Float, vel: Float,
                     attack: Float = 0.005f, decay: Float = 0.25f, sustain: Float = 0.5f, release: Float = 0.08f, vibrato: Float = 0f) {
        val f = hz(midi)
        val s0 = (beat * spb).toInt()
        val on = (len * spb).toInt()
        val total = on + (release * 5f * rate).toInt()
        var ph = 0f
        var level = 0f
        for (i in 0 until total) {
            val t = i.toFloat() / rate
            val env = when {
                i < on -> if (t < attack) t / attack else sustain + (1f - sustain) * exp(-(t - attack) / decay)
                else -> level * exp(-(i - on).toFloat() / rate / release)
            }
            if (i < on) level = env
            val vib = if (vibrato > 0f && t > 0.12f) 1f + vibrato * sin(2f * PI.toFloat() * 5.5f * t) else 1f
            ph += f * vib * size / rate
            if (ph >= size) ph -= size
            val k = ph.toInt(); val fr = ph - k
            val v = tab[k] + (tab[(k + 1) % size] - tab[k]) * fr
            add(layer, s0 + i, v * env * vel)
        }
    }

    /** A plucked note (marimba / bell / stab): no sustain, just a decay. */
    private fun pluck(layer: Int, tab: FloatArray, midi: Int, beat: Float, vel: Float, decay: Float) =
        note(layer, tab, midi, beat, decay * 4f / (spb / rate), vel, 0.002f, decay, 0f, decay)

    private fun kick(layer: Int, beat: Float, vel: Float) {
        val s0 = (beat * spb).toInt()
        var ph = 0f
        for (i in 0 until (0.35f * rate).toInt()) {
            val t = i.toFloat() / rate
            val f = 48f + 110f * exp(-t / 0.04f)
            ph += 2f * PI.toFloat() * f / rate
            // a little click and a body in the range a phone speaker can play
            val v = sin(ph) * exp(-t / 0.16f) + 0.35f * sin(ph * 2.5f) * exp(-t / 0.05f) + (if (i < 60) rnd() * 0.3f * (1f - i / 60f) else 0f)
            add(layer, s0 + i, v * vel)
        }
    }
    private fun noiseHit(layer: Int, beat: Float, vel: Float, decay: Float, bright: Float, len: Float = decay * 6f, tone: Float = 0f, toneAmt: Float = 0f) {
        val s0 = (beat * spb).toInt()
        var lp = 0f; var ph = 0f
        for (i in 0 until (len * rate).toInt()) {
            val t = i.toFloat() / rate
            val x = rnd()
            lp += (x - lp) * (1f - bright)
            var v = (x - lp) * exp(-t / decay)          // high-passed noise
            if (toneAmt > 0f) { ph += 2f * PI.toFloat() * tone / rate; v += sin(ph) * toneAmt * exp(-t / (decay * 0.6f)) }
            add(layer, s0 + i, v * vel)
        }
    }
    private fun snare(layer: Int, beat: Float, vel: Float) = noiseHit(layer, beat, vel, 0.07f, 0.55f, 0.3f, 190f, 0.6f)
    private fun hat(layer: Int, beat: Float, vel: Float, open: Boolean = false) = noiseHit(layer, beat, vel, if (open) 0.12f else 0.025f, 0.15f)
    private fun shaker(layer: Int, beat: Float, vel: Float) = noiseHit(layer, beat, vel, 0.035f, 0.25f)
    private fun crash(layer: Int, beat: Float, vel: Float) = noiseHit(layer, beat, vel, 0.5f, 0.2f, 2.2f)
    private fun tom(layer: Int, beat: Float, vel: Float, f0: Float) {
        val s0 = (beat * spb).toInt()
        var ph = 0f
        for (i in 0 until (0.4f * rate).toInt()) {
            val t = i.toFloat() / rate
            ph += 2f * PI.toFloat() * (f0 * (0.65f + 0.35f * exp(-t / 0.06f))) / rate
            add(layer, s0 + i, (sin(ph) + 0.25f * sin(ph * 2f)) * exp(-t / 0.13f) * vel)
        }
    }
    private fun block(layer: Int, beat: Float, vel: Float) {            // woodblock
        val s0 = (beat * spb).toInt()
        var ph = 0f
        for (i in 0 until (0.12f * rate).toInt()) {
            val t = i.toFloat() / rate
            ph += 2f * PI.toFloat() * 1150f / rate
            add(layer, s0 + i, sin(ph) * exp(-t / 0.018f) * vel)
        }
    }

    private fun begin(bpm: Float, bars: Int) {
        spb = rate * 60f / bpm
        n = (bars * 4 * spb).toInt()
        layers = Array(Music.LAYERS) { FloatArray(n) }
    }

    /** Melody written bar by bar as (MIDI note, length in eighths) pairs; 0 is a rest. */
    private fun melody(layer: Int, bars: List<IntArray>, play: (midi: Int, beat: Float, len: Float) -> Unit) {
        for ((b, bar) in bars.withIndex()) {
            var e = 0
            var i = 0
            while (i < bar.size) {
                val m = bar[i]; val l = bar[i + 1]
                if (m > 0) play(m, b * 4f + e * 0.5f, l * 0.5f)
                e += l; i += 2
            }
            check(e == 8) { "bar $b of the melody is $e eighths long" }
        }
    }

    // ------------------------------------------------------------------ the themes
    fun render(track: Int): Array<FloatArray> {
        when (track) {
            Music.HOME -> home()
            Music.JUNGLE -> jungle()
            else -> sky()
        }
        normalise()
        return layers
    }

    /** Home: a bright, hopeful loop in C major (C Am F G), bell tune over a soft pad, a gentle groove. */
    private fun home() {
        begin(96f, 8)
        val chords = listOf(intArrayOf(60, 64, 67), intArrayOf(57, 60, 64), intArrayOf(57, 60, 65), intArrayOf(55, 59, 62))
        val roots = intArrayOf(48, 45, 41, 43)
        for (bar in 0 until 8) {
            val c = bar % 4
            for (m in chords[c]) note(0, organ, m, bar * 4f, 3.9f, 0.07f, 0.35f, 1.2f, 0.7f, 0.5f)
            note(0, saw, roots[c], bar * 4f, 1.8f, 0.16f, 0.01f, 0.3f, 0.5f, 0.12f)
            note(0, saw, roots[c] + 7, bar * 4f + 2f, 1.8f, 0.12f, 0.01f, 0.3f, 0.5f, 0.12f)
            kick(1, bar * 4f, 0.35f); kick(1, bar * 4f + 2f, 0.28f)
            for (e in 0 until 8) shaker(1, bar * 4f + e * 0.5f, if (e % 2 == 1) 0.16f else 0.09f)
            // sparkles: a bell arpeggio of the chord, high up
            val arp = chords[c]
            for (e in 0 until 8) pluck(2, bell, arp[e % 3] + 24, bar * 4f + e * 0.5f + 0.25f, 0.05f, 0.18f)
        }
        val tune = listOf(
            intArrayOf(76, 2, 79, 2, 84, 2, 83, 1, 79, 1),
            intArrayOf(81, 4, 76, 2, 79, 2),
            intArrayOf(77, 2, 81, 2, 84, 2, 81, 2),
            intArrayOf(79, 4, 74, 2, 77, 2),
            intArrayOf(76, 2, 79, 2, 84, 2, 86, 2),
            intArrayOf(88, 3, 86, 1, 84, 2, 81, 2),
            intArrayOf(77, 2, 81, 2, 79, 2, 77, 2),
            intArrayOf(74, 2, 76, 1, 77, 1, 79, 4))
        melody(0, tune) { m, beat, len -> pluck(0, bell, m, beat, 0.30f, 0.35f + 0.15f * len) }
    }

    /** The sky tower: an upbeat adventure in A minor (Am F C G Am F G E), square-wave lead, pulsing bass. */
    private fun sky() {
        begin(126f, 8)
        val prog = listOf(
            intArrayOf(57, 60, 64), intArrayOf(57, 60, 65), intArrayOf(55, 60, 64), intArrayOf(55, 59, 62),
            intArrayOf(57, 60, 64), intArrayOf(57, 60, 65), intArrayOf(55, 59, 62), intArrayOf(56, 59, 64))
        val roots = intArrayOf(45, 41, 48, 43, 45, 41, 43, 40)
        for (bar in 0 until 8) {
            val c = prog[bar]; val r = roots[bar]
            // bass: eighths on the root, up an octave on the off-beats
            for (e in 0 until 8) note(0, saw, if (e % 2 == 1) r + 12 else r, bar * 4f + e * 0.5f, 0.42f, 0.2f, 0.004f, 0.08f, 0.4f, 0.05f)
            // chord stabs on the off-beats
            for (b in 0 until 4) for (m in c) note(0, saw, m + 12, bar * 4f + b + 0.5f, 0.22f, 0.055f, 0.003f, 0.06f, 0.2f, 0.05f)
            // drums
            kick(1, bar * 4f, 0.55f); kick(1, bar * 4f + 2f, 0.5f)
            if (bar % 2 == 1) kick(1, bar * 4f + 2.5f, 0.35f)
            snare(1, bar * 4f + 1f, 0.32f); snare(1, bar * 4f + 3f, 0.32f)
            for (e in 0 until 8) hat(1, bar * 4f + e * 0.5f, if (e % 2 == 1) 0.2f else 0.12f)
            // the rush: sixteenth arpeggios up high, open hats on the off-beats, a crash every four bars
            for (s in 0 until 16) note(2, square, c[s % 3] + 24 + (if (s % 6 >= 3) 12 else 0), bar * 4f + s * 0.25f, 0.2f, 0.045f, 0.002f, 0.05f, 0.1f, 0.03f)
            for (b in 0 until 4) hat(2, bar * 4f + b + 0.5f, 0.14f, open = true)
            if (bar % 4 == 0) crash(2, bar * 4f, 0.22f)
            kick(2, bar * 4f + 1f, 0.35f); kick(2, bar * 4f + 3f, 0.35f)
        }
        // a tom fill into the loop
        for ((k, f) in floatArrayOf(220f, 200f, 175f, 150f, 130f, 115f).withIndex()) tom(2, 30f + k * 0.333f, 0.4f, f)
        val tune = listOf(
            intArrayOf(76, 2, 81, 2, 79, 1, 76, 1, 74, 1, 76, 1),
            intArrayOf(72, 2, 77, 2, 76, 1, 74, 1, 72, 2),
            intArrayOf(76, 2, 79, 2, 81, 1, 79, 1, 76, 1, 79, 1),
            intArrayOf(74, 3, 71, 1, 74, 2, 79, 2),
            intArrayOf(76, 2, 81, 2, 83, 1, 81, 1, 79, 1, 81, 1),
            intArrayOf(77, 2, 81, 2, 79, 1, 77, 1, 76, 1, 77, 1),
            intArrayOf(79, 2, 74, 2, 79, 1, 81, 1, 83, 2),
            intArrayOf(80, 2, 76, 2, 71, 2, 76, 2))
        melody(0, tune) { m, beat, len -> note(0, square, m, beat, len * 0.92f, 0.2f, 0.006f, 0.18f, 0.55f, 0.07f, vibrato = 0.006f) }
    }

    /** The jungle temple: D minor (Dm Bb F C Dm Bb C A), marimba tune, toms and shakers. */
    private fun jungle() {
        begin(118f, 8)
        val prog = listOf(
            intArrayOf(57, 62, 65), intArrayOf(58, 62, 65), intArrayOf(57, 60, 65), intArrayOf(55, 60, 64),
            intArrayOf(57, 62, 65), intArrayOf(58, 62, 65), intArrayOf(55, 60, 64), intArrayOf(57, 61, 64))
        val roots = intArrayOf(50, 46, 41, 48, 50, 46, 48, 45)
        for (bar in 0 until 8) {
            val c = prog[bar]; val r = roots[bar]
            // bass: a bouncing root-fifth figure
            val fig = intArrayOf(0, -1, 7, 0, -1, 0, 7, 12)
            for (e in 0 until 8) if (fig[e] >= 0) note(0, saw, r - 12 + fig[e], bar * 4f + e * 0.5f, 0.4f, 0.2f, 0.004f, 0.1f, 0.35f, 0.05f)
            // marimba chords on the off-beats
            for (b in 0 until 4) for (m in c) pluck(0, marimba, m + 12, bar * 4f + b + 0.5f, 0.05f, 0.14f)
            // drums: kick, toms and shakers
            kick(1, bar * 4f, 0.5f); kick(1, bar * 4f + 2f, 0.42f)
            tom(1, bar * 4f + 1.5f, 0.3f, 190f); tom(1, bar * 4f + 3f, 0.34f, 150f); tom(1, bar * 4f + 3.5f, 0.24f, 130f)
            for (e in 0 until 8) shaker(1, bar * 4f + e * 0.5f, if (e % 2 == 1) 0.18f else 0.1f)
            // the rush: fast marimba arpeggios and woodblocks
            for (s in 0 until 16) pluck(2, marimba, c[s % 3] + 24 - (if (s % 8 >= 4) 12 else 0), bar * 4f + s * 0.25f, 0.06f, 0.09f)
            for (b in 0 until 4) block(2, bar * 4f + b + 0.75f, 0.12f)
            kick(2, bar * 4f + 1f, 0.32f); kick(2, bar * 4f + 3f, 0.32f)
            if (bar % 4 == 0) crash(2, bar * 4f, 0.16f)
        }
        for ((k, f) in floatArrayOf(240f, 210f, 190f, 165f, 145f, 125f).withIndex()) tom(2, 30f + k * 0.333f, 0.4f, f)
        val tune = listOf(
            intArrayOf(74, 1, 77, 1, 81, 2, 79, 1, 77, 1, 76, 1, 77, 1),
            intArrayOf(74, 2, 77, 2, 79, 1, 77, 1, 74, 2),
            intArrayOf(72, 1, 77, 1, 81, 2, 84, 2, 81, 2),
            intArrayOf(79, 3, 76, 1, 72, 2, 76, 2),
            intArrayOf(74, 1, 77, 1, 81, 2, 86, 2, 84, 1, 81, 1),
            intArrayOf(82, 2, 81, 1, 79, 1, 77, 2, 74, 2),
            intArrayOf(76, 1, 79, 1, 84, 2, 82, 2, 79, 2),
            intArrayOf(81, 2, 76, 2, 73, 2, 76, 2))
        melody(0, tune) { m, beat, len -> pluck(0, marimba, m, beat, 0.3f, 0.2f + 0.08f * len) }
    }

    /** Scales the layers so that all of them together at full volume stay just under full scale. */
    private fun normalise() {
        var peak = 1e-4f
        for (i in 0 until n) { var s = 0f; for (l in layers) s += l[i]; peak = max(peak, kotlin.math.abs(s)) }
        val g = min(4f, 0.92f / peak)
        for (l in layers) for (i in 0 until n) l[i] *= g
    }
}
