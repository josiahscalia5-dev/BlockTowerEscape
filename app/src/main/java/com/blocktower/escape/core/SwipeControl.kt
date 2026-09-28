package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Swipe movement for Screen 4 (one thumb, anywhere in the movement area).
 *
 *  - Swipe UP: run forward. The swipe's length sets the pace (short = jog, medium = run,
 *    long = sprint). A quick flick keeps the boy running after the thumb lifts; a held swipe runs
 *    while the thumb stays down and eases to a stop when it lifts.
 *  - Swipe LEFT / RIGHT: steer toward that side of the path. The distance sets how far: a short
 *    swipe is a small correction, a medium one about one block, a long one up to two and a half.
 *    Sliding the held thumb back steers back.
 *  - Swipe DOWN: slow down / stop. Keep holding down to step back carefully.
 *  - A quick upward flick with a gap, step or hazard just ahead jumps (the game times it to the edge).
 *
 * Tiny accidental finger movements are ignored. Distances are in artwork pixels (scaled by the HUD).
 */
class SwipeControl(private val g: Game) {
    /** Settings: swipe sensitivity (0.75 low, 1 normal, 1.3 high). Higher = the same swipe does more. */
    var sensitivity = 1f
    private val s get() = g.hud.s / sensitivity

    // ---- the active movement finger
    var id = -1
        private set
    var anchorX = 0f; var anchorY = 0f       // touch-down point (pulled along by a leash on long drags)
        private set
    var fingerX = 0f; var fingerY = 0f
        private set
    private var downX = 0f; private var downY = 0f
    private var downT = 0f
    private var engagedH = false; private var engagedV = false
    private var applied = 0f                  // steering already handed to the game by this gesture (units)
    private var lastFlickT = -9f
    private var flickArmed = true
    private var maxUp = 0f                    // largest upward travel of this gesture (px)

    // recent finger samples for flick velocity
    private val sx = FloatArray(12); private val sy = FloatArray(12); private val st = FloatArray(12)
    private var sn = 0

    // ---- what the game reads
    /** Persistent forward drive 0..1 left by an upward flick. */
    var cruise = 0f
    /** Drive from the held thumb (-0.45 back .. 1 sprint); only while [holding]. */
    var hold = 0f
        private set
    val holding get() = id >= 0
    private var steerDelta = 0f
    /** Seconds since the thumb last steered. */
    var steerIdle = 9f
        private set
    private var flickUp = false
    /**
     * The last steering move was a lane change (about half a block or more), so the boy may settle onto
     * the middle of a block afterwards. Small corrections are kept exactly where the thumb put him.
     */
    var laneChange = true

    // ---- feedback for the HUD / world
    var trail = 0f                             // visibility of the touch trail 0..1
        private set
    var lastSwipeDir = 0                       // -1 left, 1 right, 2 up, -2 down (last recognised swipe)
        private set
    var swipeFlash = 0f                        // 1 -> 0 after a recognised swipe
        private set

    /** Effective forward drive the player should run with. */
    val drive: Float get() = if (holding) { if (hold < -0.01f) hold else max(cruise, hold) } else cruise

    fun reset() {
        id = -1; cruise = 0f; hold = 0f; steerDelta = 0f; flickUp = false; trail = 0f; swipeFlash = 0f
    }

    /** Stops any running (used after falls, captures and when continuing). */
    fun stop() { cruise = 0f; hold = 0f; if (holding) { anchorY = fingerY; engagedV = false } }

    fun down(pointer: Int, x: Float, y: Float) {
        id = pointer
        anchorX = x; anchorY = y; downX = x; downY = y; fingerX = x; fingerY = y
        downT = g.t; engagedH = false; engagedV = false; applied = 0f
        hold = 0f; maxUp = 0f; flickArmed = true
        sn = 0; sample(x, y)
    }

    fun move(pointer: Int, x: Float, y: Float) {
        if (pointer != id) return
        fingerX = x; fingerY = y
        sample(x, y)
        // ignore tiny accidental movements until the thumb clearly travels, and let the main direction
        // of the swipe win: a sideways swipe that arcs a little does not also run or brake, and an
        // upward swipe that drifts a little does not steer
        val ax = abs(x - downX); val ay = abs(y - downY)
        if (!engagedV && ay > 14f * s && ay > ax * 0.6f) engagedV = true
        if (!engagedH && ax > 12f * s && ax > ay * 0.35f) engagedH = true
        updateVertical()
        updateHorizontal()
        detectFlick()
    }

    fun up(pointer: Int, x: Float, y: Float) {
        if (pointer != id) return
        move(pointer, x, y)
        val dur = g.t - downT
        val upTravel = (downY - y) / s
        val sideTravel = abs(x - downX) / s
        val quick = dur < 0.35f || g.t - lastFlickT < 0.2f
        if (upTravel > 36f && upTravel > sideTravel * 0.8f && quick) {
            // runner-style flick: keep running after the thumb lifts, at a pace set by the swipe length
            val pace = if (maxUp < 70f) 0.72f else if (maxUp < 150f) 0.88f else 1f
            cruise = max(cruise, pace)
        }
        id = -1; hold = 0f
    }

    fun cancel(pointer: Int) { if (pointer == id) { id = -1; hold = 0f } }

    private fun sample(x: Float, y: Float) {
        if (sn == sx.size) { for (i in 1 until sn) { sx[i - 1] = sx[i]; sy[i - 1] = sy[i]; st[i - 1] = st[i] }; sn-- }
        sx[sn] = x; sy[sn] = y; st[sn] = g.t; sn++
    }

    private fun updateVertical() {
        if (!engagedV) return
        var up = (anchorY - fingerY) / s
        // leash: on long drags the anchor follows so turning around responds at once
        if (up > 170f) { anchorY = fingerY + 170f * s; up = 170f }
        if (up < -150f) { anchorY = fingerY - 150f * s; up = -150f }
        maxUp = max(maxUp, (downY - fingerY) / s)
        hold = when {
            up >= 14f -> if (up < 60f) 0.35f + 0.3f * (up - 14f) / 46f else if (up < 130f) 0.65f + 0.35f * (up - 60f) / 70f else 1f
            up > -30f -> 0f
            else -> -0.45f * clamp01((-up - 30f) / 90f)
        }
        if (up <= -30f) {
            // swipe down: a short one slows down, a longer one stops
            if (cruise > 0f) { cruise = if (up > -60f) min(cruise, 0.45f) else 0f; mark(-2) }
        }
        if (up >= 14f && hold > 0.3f && lastSwipeDir != 2) mark(2)
    }

    private fun updateHorizontal() {
        if (!engagedH) return
        var off = (fingerX - anchorX) / s
        if (abs(off) > 285f) { anchorX = fingerX - sign(off) * 285f * s; off = sign(off) * 285f }
        val shift = shiftFor(off)
        val d = shift - applied
        if (abs(d) > 1e-4f) {
            steerDelta += d; applied = shift; steerIdle = 0f
            laneChange = abs(shift) >= 0.4f
            if (abs(off) > 24f) mark(if (off < 0f) -1 else 1)
        }
    }

    /** Swipe distance (px) -> lateral steering (units): gentle for short swipes, about a block per 90 px after that. */
    private fun shiftFor(off: Float): Float {
        val d = max(0f, abs(off) - 10f)
        val u = if (d < 60f) 0.45f * d / 60f else 0.45f + (d - 60f) / 90f
        return sign(off) * min(u, 2.5f)
    }

    private fun detectFlick() {
        // upward flick: fast and far enough within the last ~0.12 s
        var i = sn - 1
        while (i > 0 && g.t - st[i - 1] < 0.12f) i--
        val dt = g.t - st[i]
        if (dt < 0.016f) return
        val up = (sy[i] - fingerY) / s
        val v = up / dt
        if (up > 40f && v > 850f && flickArmed) {
            flickUp = true; flickArmed = false; lastFlickT = g.t; mark(2)
        }
        if (v < 200f) flickArmed = true
    }

    private fun mark(dir: Int) { lastSwipeDir = dir; swipeFlash = 1f }

    // ---- consumed by the game each frame
    fun takeSteer(): Float { val d = steerDelta; steerDelta = 0f; return d }
    /** Inverse of [shiftFor]: the swipe offset (px) that gives [u] units of steering. */
    private fun offsetFor(u: Float): Float {
        val a = abs(u)
        val d = if (a < 0.45f) 60f * a / 0.45f else 60f + (a - 0.45f) * 90f
        return sign(u) * (d + 10f)
    }

    /**
     * The game could not steer that far (edge of the path): slide the anchor along so the thumb's
     * current position means "at the edge", and sliding back steers back at once.
     */
    fun absorb(excess: Float) {
        if (!holding || abs(excess) < 1e-4f) return
        applied -= excess
        val off = if (abs(applied) < 1e-3f) sign(fingerX - anchorX) * 10f else offsetFor(applied)
        anchorX = fingerX - off * s
    }
    fun takeFlickUp(): Boolean { val f = flickUp; flickUp = false; return f }

    fun update(dt: Float) {
        steerIdle += dt
        trail = if (holding) min(1f, trail + dt * 8f) else max(0f, trail - dt * 4f)
        swipeFlash = max(0f, swipeFlash - dt * 2.5f)
    }
}
