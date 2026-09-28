package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * The optional thumb stick (SETTINGS → Controls → JOYSTICK; swipes are the default). It sits where the
 * movement pad is, bottom left:
 *
 *  - push UP to run: a little is a jog, all the way is a sprint
 *  - push DOWN to slow down, and further to step back carefully
 *  - push LEFT / RIGHT to steer: the further, the quicker he moves across (and he still settles onto the
 *    middle of a block when the stick is let go)
 *
 * It drives the same movement as the swipes (speed eases in and out, steering glides), so nothing snaps.
 * Jumping stays on the JUMP button. Positions are in screen pixels; the stick's reach scales with the HUD.
 */
class JoystickControl(private val g: Game) {
    /** Settings: the same sensitivity as the swipes (0.75 low, 1 normal, 1.3 high) makes steering quicker. */
    var sensitivity = 1f

    var id = -1
        private set
    val holding get() = id >= 0
    /** Knob position, -1..1 on each axis (y down, like the screen). */
    var x = 0f
        private set
    var y = 0f
        private set
    /** Seconds since the stick last steered. */
    var steerIdle = 9f
        private set

    private val reach get() = 100f * g.hud.s

    fun down(pointer: Int, px: Float, py: Float) { id = pointer; move(pointer, px, py) }

    fun move(pointer: Int, px: Float, py: Float) {
        if (pointer != id) return
        var dx = (px - g.hud.joyX()) / reach; var dy = (py - g.hud.joyY()) / reach
        val m = sqrt(dx * dx + dy * dy)
        if (m > 1f) { dx /= m; dy /= m }
        x = dx; y = dy
    }

    fun up(pointer: Int) { if (pointer == id) release() }
    fun release() { id = -1; x = 0f; y = 0f }

    private fun past(v: Float, dead: Float) = if (abs(v) <= dead) 0f else sign(v) * (abs(v) - dead) / (1f - dead)

    /** Forward drive -0.45 (stepping back) .. 1 (sprint). */
    val drive: Float get() {
        val f = past(-y, DEAD)
        return if (f > 0f) 0.3f + 0.7f * f else if (f < 0f) 0.45f * f else 0f
    }

    /** Steering speed (units per second): gentle near the centre, quick at the rim. */
    fun steerRate(): Float {
        val u = past(x, DEAD_X)
        return sign(u) * abs(u).let { it * (0.35f + 0.65f * it) } * STEER_MAX * sensitivity
    }

    fun update(dt: Float) { steerIdle = if (abs(steerRate()) > 0.01f) 0f else steerIdle + dt }

    companion object {
        const val DEAD = 0.18f
        const val DEAD_X = 0.15f
        /** Fastest sideways steering, units per second. */
        const val STEER_MAX = 6f
    }
}
