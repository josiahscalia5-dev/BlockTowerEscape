package com.blocktower.escape.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

const val TAU = (2.0 * PI).toFloat()

fun clamp(v: Float, lo: Float, hi: Float) = if (v < lo) lo else if (v > hi) hi else v
fun clamp01(v: Float) = clamp(v, 0f, 1f)
fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun invLerp(a: Float, b: Float, v: Float) = clamp01((v - a) / (b - a))
fun smooth(t: Float): Float { val x = clamp01(t); return x * x * (3f - 2f * x) }
fun easeOutBack(t: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val x = t - 1f; return 1f + c3 * x * x * x + c1 * x * x }
fun easeOutCubic(t: Float): Float { val x = 1f - clamp01(t); return 1f - x * x * x }
fun easeInCubic(t: Float): Float { val x = clamp01(t); return x * x * x }
fun approach(v: Float, target: Float, step: Float): Float =
    if (v < target) minOf(v + step, target) else maxOf(v - step, target)
/** Frame-rate independent exponential smoothing factor. */
fun damp(rate: Float, dt: Float): Float = 1f - kotlin.math.exp(-rate * dt)
fun fract(v: Float) = v - floor(v)
fun sq(v: Float) = v * v
fun len2(x: Float, y: Float) = sqrt(x * x + y * y)
fun len3(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)
fun pulse(t: Float, speed: Float) = 0.5f + 0.5f * sin(t * speed)
fun absf(v: Float) = abs(v)

object Col {
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun rgb(r: Int, g: Int, b: Int) = argb(255, r, g, b)
    fun a(c: Int) = (c ushr 24) and 255
    fun r(c: Int) = (c shr 16) and 255
    fun g(c: Int) = (c shr 8) and 255
    fun b(c: Int) = c and 255
    fun withA(c: Int, a: Float) = (clamp(a * 255f, 0f, 255f).toInt() shl 24) or (c and 0xFFFFFF)
    fun mulA(c: Int, a: Float) = withA(c, a(c) / 255f * a)
    fun mix(c0: Int, c1: Int, t: Float): Int {
        val u = clamp01(t)
        return argb(
            (a(c0) + (a(c1) - a(c0)) * u).toInt(), (r(c0) + (r(c1) - r(c0)) * u).toInt(),
            (g(c0) + (g(c1) - g(c0)) * u).toInt(), (b(c0) + (b(c1) - b(c0)) * u).toInt()
        )
    }
    fun scale(c: Int, k: Float): Int = argb(a(c), clamp(r(c) * k, 0f, 255f).toInt(), clamp(g(c) * k, 0f, 255f).toInt(), clamp(b(c) * k, 0f, 255f).toInt())
    const val WHITE = -1
    const val BLACK = -0x1000000
    const val CLEAR = 0
}

/** Small deterministic RNG (xorshift) so levels and effects are reproducible. */
class Rng(seed: Long) {
    private var s = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed
    fun nextLong(): Long { var x = s; x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17); s = x; return x }
    fun f(): Float = ((nextLong() ushr 40).toInt() and 0xFFFFFF) / 16777216f
    fun f(lo: Float, hi: Float) = lo + (hi - lo) * f()
    fun i(n: Int) = ((nextLong() ushr 33) % n).toInt().let { if (it < 0) it + n else it }
    fun sign() = if (f() < 0.5f) -1f else 1f
}

/** Hash for per-cell variation. */
fun hash3(x: Int, y: Int, z: Int): Int {
    var h = x * 374761393 + y * 668265263 + z * 1274126177
    h = (h xor (h ushr 13)) * 1103515245
    return h xor (h ushr 16)
}
