package com.blocktower.escape.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * Pinhole camera. World: x right, y up, z forward (course direction).
 * yaw rotates around +y (0 = looking down +z), pitch > 0 looks downward.
 */
class Camera {
    var ex = 0f; var ey = 0f; var ez = 0f
    var yaw = 0f; var pitch = 0.28f
    /** Screen roll in radians (dramatic moments, shake). */
    var roll = 0f
    var f = 1000f
    var cx = 0f; var cy = 0f
    private var cyaw = 1f; private var syaw = 0f; private var cp = 1f; private var sp = 0f
    private var cr = 1f; private var sr = 0f

    // results of the last project() call
    @JvmField var sx = 0f
    @JvmField var sy = 0f
    @JvmField var depth = 0f

    fun update() {
        cyaw = cos(yaw); syaw = sin(yaw); cp = cos(pitch); sp = sin(pitch); cr = cos(roll); sr = sin(roll)
    }

    /** Camera-space depth of a world point (forward distance). */
    fun depthOf(x: Float, y: Float, z: Float): Float {
        val dx = x - ex; val dy = y - ey; val dz = z - ez
        val fz = dz * cyaw + dx * syaw
        return fz * cp - dy * sp
    }

    /** Projects a world point; returns false when it is behind the near plane. */
    fun project(x: Float, y: Float, z: Float): Boolean {
        val dx = x - ex; val dy = y - ey; val dz = z - ez
        val rx = dx * cyaw - dz * syaw
        val fz = dz * cyaw + dx * syaw
        val zc = fz * cp - dy * sp
        val yc = fz * sp + dy * cp
        depth = zc
        if (zc < NEAR) return false
        val px = f * rx / zc
        val py = -f * yc / zc
        sx = cx + px * cr - py * sr
        sy = cy + px * sr + py * cr
        return true
    }

    fun dist2(x: Float, y: Float, z: Float): Float {
        val dx = x - ex; val dy = y - ey; val dz = z - ez
        return dx * dx + dy * dy + dz * dz
    }

    /** Pixels per world unit at a given depth. */
    fun scaleAt(depth: Float) = f / maxOf(depth, NEAR)

    companion object { const val NEAR = 0.35f }
}
