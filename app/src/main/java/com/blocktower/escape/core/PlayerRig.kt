package com.blocktower.escape.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One cut-out piece of the boy sprite: its bitmap, where it sits in the sprite and its joint. */
class RigPart(val img: Img, val x0: Float, val y0: Float, val pivotX: Float, val pivotY: Float) {
    val x1 get() = x0 + img.w
    val y1 get() = y0 + img.h
}

/**
 * The approved boy sprite (242 x 420, back view, mid-stride) cut into legs, body and two arms.
 * Nothing is repainted: every part keeps the original pixels.
 *
 * The legs swap sides every step (they are mirrored about the centre of the pants seat), so the
 * join between body and legs is cut where the sprite is left/right symmetric: the seat of the
 * pants (rows 264-290 run from x 56 to 156, centred on [HIP_X]). Across that band both the body
 * and the top of the legs are trimmed to the symmetric part of the outline, so whichever way the
 * legs face, the hips have the same outline and no strip of the other pose shows beside them.
 * The body fades out above the raised shoe's heel (which starts at row 281 and belongs to the
 * legs); the legs run up under the opaque body so the seat is always covered.
 */
class BoyRig(val legs: RigPart, val body: RigPart, val armL: RigPart, val armR: RigPart, val w: Int, val h: Int) {
    companion object {
        /** Mirror axis of the legs (centre of the pants seat), in sprite pixels. */
        const val HIP_X = 106f
        /** Hip joint: legs swing, squash and mirror about (HIP_X, HIP_Y); the body leans about it too. */
        const val HIP_Y = 272f
        /** The body fades out over these rows (top of the seat, above the raised heel). */
        private const val BODY_FADE0 = 266
        private const val BODY_FADE1 = 280
        /** The legs start under the body here, trimmed to the symmetric outline down to the crotch. */
        private const val LEGS_TOP = 250
        private const val SYM0 = 288
        private const val SYM1 = 298

        fun build(p: Platform, src: Pixels): BoyRig {
            val w = src.w; val h = src.h
            // hand-traced outlines of the two arms in sprite pixels (sleeve, forearm and fist); the torso
            // edge and backpack straps stay with the body. The arms overlap the body only at the shoulder,
            // where the arm fades out so a swinging arm blends into the static shoulder.
            val polyL = floatArrayOf(62f, 148f, 67f, 165f, 65f, 186f, 58f, 198f, 52f, 205f, 49f, 222f, 20f, 233f, 0f, 229f, 0f, 168f, 20f, 158f, 40f, 149f)
            val polyR = floatArrayOf(158f, 153f, 190f, 158f, 216f, 183f, 241f, 198f, 242f, 240f, 241f, 270f, 204f, 270f, 194f, 246f, 185f, 221f, 178f, 214f, 170f, 205f, 159f, 190f)
            fun inside(poly: FloatArray, x: Float, y: Float): Boolean {
                var c = false
                var j = poly.size / 2 - 1
                for (i in 0 until poly.size / 2) {
                    val xi = poly[i * 2]; val yi = poly[i * 2 + 1]; val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
                    if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
                    j = i
                }
                return c
            }
            fun inL(x: Int, y: Int) = inside(polyL, x + 0.5f, y + 0.5f)
            fun inR(x: Int, y: Int) = inside(polyR, x + 0.5f, y + 0.5f)
            /** Is (x, y) part of the lower body (not an arm)? */
            fun lower(x: Int, y: Int) = x in 0..176 && !inR(x, y) && !inL(x, y)
            /** Opacity of the pixel mirrored across HIP_X (0 outside the lower body): the symmetric outline. */
            fun mirrorA(x: Int, y: Int): Float {
                val mx = (2f * HIP_X - x - 1f).toInt()
                if (mx < 0 || mx >= w || !lower(mx, y)) return 0f
                return (src.argb[y * w + mx] ushr 24) / 255f
            }
            fun armLAlpha(x: Int, y: Int): Float = if (!inL(x, y)) 0f else if (x < 54) 1f else clamp01((65 - x) / 11f)
            fun armRAlpha(x: Int, y: Int): Float = if (!inR(x, y)) 0f else if (x >= 172) 1f else clamp01((x - 160) / 12f)
            fun legsAlpha(x: Int, y: Int): Float {
                if (y < LEGS_TOP || !lower(x, y)) return 0f
                val full = clamp01((y - SYM0).toFloat() / (SYM1 - SYM0))
                return lerp(mirrorA(x, y), 1f, full)
            }
            fun bodyAlpha(x: Int, y: Int): Float {
                if (y >= BODY_FADE1) return 0f
                if (x < 54 && inL(x, y)) return 0f
                if (x >= 172 && inR(x, y)) return 0f
                if (y < BODY_FADE0) return 1f
                return (BODY_FADE1 - y).toFloat() / (BODY_FADE1 - BODY_FADE0) * mirrorA(x, y)
            }
            fun part(l: Int, t: Int, r: Int, b: Int, px: Float, py: Float, alpha: (Int, Int) -> Float): RigPart {
                val pw = r - l; val ph = b - t
                val out = IntArray(pw * ph)
                for (y in 0 until ph) for (x in 0 until pw) {
                    val sx = l + x; val sy = t + y
                    val c = src.argb[sy * w + sx]
                    val a = ((c ushr 24) * clamp01(alpha(sx, sy))).toInt()
                    out[y * pw + x] = (a shl 24) or (c and 0xFFFFFF)
                }
                return RigPart(p.createImage(Pixels(pw, ph, out)), l.toFloat(), t.toFloat(), px, py)
            }
            return BoyRig(
                legs = part(0, LEGS_TOP, 178, h, HIP_X, HIP_Y, ::legsAlpha),
                body = part(0, 0, w, BODY_FADE1, HIP_X, HIP_Y, ::bodyAlpha),
                armL = part(0, 145, 68, 236, 60f, 176f, ::armLAlpha),
                armR = part(156, 150, w, 272, 170f, 180f, ::armRAlpha),
                w = w, h = h
            )
        }
    }
}

/**
 * A pose for the rig. Angles in degrees (screen space, clockwise), offsets in sprite pixels.
 * The legs always hang from the hip joint: [bodyDy] and [bodyRot] move the hips (and so the legs)
 * with the body, and the legs only add their own swing, squash and mirror about that joint.
 */
class Pose {
    var rot = 0f; var sx = 1f; var sy = 1f; var dy = 0f; var spin = 1f
    var bodyDy = 0f; var bodyRot = 0f
    var legsMirror = false; var legsSx = 1f; var legsSy = 1f; var legsRot = 0f
    var armL = 0f; var armR = 0f; var armLS = 1f; var armRS = 1f
    fun reset() {
        rot = 0f; sx = 1f; sy = 1f; dy = 0f; spin = 1f; bodyDy = 0f; bodyRot = 0f
        legsMirror = false; legsSx = 1f; legsSy = 1f; legsRot = 0f
        armL = 0f; armR = 0f; armLS = 1f; armRS = 1f
    }
}

/** Turns the player's movement state into a pose and draws the rig. */
class PlayerRig(val g: Game) {
    val pose = Pose()
    private val q = FloatArray(8)
    private val anchorX = 121f   // feet anchor in the sprite (centre, 95.5% down)
    private val anchorY = 401f

    private fun env(t: Float, dur: Float): Float { if (t <= 0f || t >= dur) return 0f; val u = t / dur; return sin(u * PI.toFloat()) }
    private fun decay(t: Float, dur: Float): Float = if (t <= 0f || t >= dur) 0f else 1f - t / dur

    fun computePose(p: Player): Pose {
        val o = pose
        o.reset()
        val t = g.t
        val speed = len2(p.vx, p.vz)
        val runW = p.runW
        val airW = p.airW
        // ---- running: stride cycle (legs swap sides each step), bob, arm pump
        val ph = p.runPhase
        val stepFrac = fract(ph)
        val stride = sin(stepFrac * PI.toFloat())
        val swing = sin(ph * PI.toFloat())
        o.legsMirror = (floor(ph).toInt() and 1) == 1
        val ground = 1f - airW
        // the whole boy rises at mid-stride (flight phase); at the step change the legs gather under the hips
        o.bodyDy += ground * (-runW * (2f + stride * 7f) + (1f - runW) * sin(t * 2.4f) * 1.4f)
        o.legsSy *= 1f - ground * runW * 0.12f * (1f - stride)
        o.legsRot += ground * runW * (if (o.legsMirror) -3.5f else 3.5f) * stride
        o.armL += ground * (runW * (20f * swing - 4f) + (1f - runW) * sin(t * 2.4f) * 2f)
        o.armR += ground * (runW * (20f * swing + 4f) - (1f - runW) * sin(t * 2.4f) * 2f)
        o.armLS = 1f - ground * runW * 0.07f * swing
        o.armRS = 1f + ground * runW * 0.07f * swing
        o.bodyRot += ground * runW * swing * 1.5f
        // ---- airborne: tuck while rising, arms up; reach and flail while falling
        if (airW > 0f) {
            val rising = clamp01(p.vy / 6f)
            val falling = clamp01(-p.vy / 10f)
            o.legsSy *= 1f - airW * (0.2f * rising + 0.02f)
            o.armL += airW * (26f + 16f * falling + falling * 7f * sin(t * 17f))
            o.armR -= airW * (26f + 16f * falling + falling * 7f * sin(t * 17f + 1.3f))
            o.sy *= 1f + airW * 0.04f * rising
            o.sx *= 1f - airW * 0.03f * rising
            o.bodyDy -= airW * 3f * rising
            if (falling > 0.6f) o.legsMirror = ((t * 7f).toInt() and 1) == 1
        }
        // ---- landing: squash, body dips, arms drop
        val land = decay(p.landT, 0.28f) * p.landAmt
        if (land > 0f) {
            val k = land * land
            o.sy *= 1f - 0.16f * k; o.sx *= 1f + 0.1f * k
            o.bodyDy += 10f * k; o.legsSy *= 1f - 0.1f * k
            o.armL -= 14f * k; o.armR += 14f * k
        }
        // jump take-off stretch
        val take = decay(p.jumpT, 0.16f)
        if (take > 0f) { o.sy *= 1f + 0.09f * take; o.sx *= 1f - 0.06f * take }
        // ---- turning / side movement: lean into the turn, skid squeeze on direction change
        // (body banks a little more than the legs, the outer arm swings out for balance)
        o.rot += -p.lean * 10f
        o.bodyRot += -p.lean * 3f * (1f - airW)
        o.sx *= 1f - 0.05f * abs(p.lean)
        if (p.lean > 0f) o.armL += 10f * p.lean * (1f - airW) else o.armR += 10f * p.lean * (1f - airW)
        val skid = env(p.skidT, 0.22f)
        if (skid > 0f) { o.rot += p.skidDir * 7f * skid; o.sx *= 1f - 0.08f * skid; o.bodyDy += 3f * skid }
        // ---- collect: quick fist pump
        val col = env(p.collectT, 0.34f)
        if (col > 0f) { o.armL += 34f * col; o.armR -= 34f * col; o.sy *= 1f + 0.05f * col; o.bodyDy -= 4f * col }
        // ---- tool cast: right arm thrust up
        val cast = env(p.castT, 0.4f)
        if (cast > 0f) { o.armR -= 50f * cast; o.armRS *= 1f - 0.08f * cast; o.sy *= 1f + 0.03f * cast }
        // ---- damage: knocked back, arms thrown up, legs tucked
        val hurt = decay(p.hurtT, 0.55f)
        if (hurt > 0f) {
            val k = hurt * hurt
            o.bodyRot += -10f * k * sin(p.hurtT * 26f)
            o.rot += p.hurtDir * 12f * k
            o.armL += 42f * k; o.armR -= 42f * k
            o.legsSy *= 1f - 0.1f * k; o.bodyDy += 4f * k
        }
        // ---- checkpoint celebration: little hops with both arms up
        val cel = if (p.celebrateT > 0f && p.celebrateT < 1.2f) min(1f, p.celebrateT / 0.12f) * min(1f, (1.2f - p.celebrateT) / 0.25f) else 0f
        if (cel > 0f) {
            val hop = abs(sin(p.celebrateT * PI.toFloat() * 2.6f))
            o.dy -= 26f * cel * hop
            o.armL += cel * (58f + 8f * sin(t * 16f)); o.armR -= cel * (58f + 8f * sin(t * 16f + 2f))
            o.sy *= 1f + 0.05f * cel * hop
            o.legsSy *= 1f - 0.12f * cel * hop
        }
        // ---- special states
        when (p.state) {
            PS.RESCUE_FALL -> if (!p.grounded) {
                o.armL = 40f + 26f * sin(t * 21f); o.armR = -40f - 26f * sin(t * 21f + 1.7f)
                o.legsMirror = ((t * 9f).toInt() and 1) == 1; o.rot += sin(t * 13f) * 14f
            }
            PS.RESCUE_RIDE -> {
                o.sy *= 0.95f; o.armL = 22f + 5f * sin(t * 3f); o.armR = -22f - 5f * sin(t * 3f + 1f)
                o.rot += sin(t * 2.2f) * 4f; o.bodyDy = 3f
            }
            PS.CAUGHT -> {
                o.armL = 48f + 22f * sin(t * 18f); o.armR = -48f - 22f * sin(t * 18f + 1.1f)
                o.legsMirror = ((t * 11f).toInt() and 1) == 1; o.rot += sin(t * 9f) * 10f; o.legsSy = 0.92f
            }
            PS.WIN -> {
                val up = min(1f, p.stateT / 0.35f)
                o.armL = lerp(o.armL, 62f + 6f * sin(t * 12f), up); o.armR = lerp(o.armR, -62f - 6f * sin(t * 12f + 1f), up)
                o.spin = cos(p.spin)
            }
            PS.DEAD -> { o.armL = 50f + 20f * sin(t * 19f); o.armR = -50f - 20f * sin(t * 19f + 1f); o.legsMirror = ((t * 8f).toInt() and 1) == 1 }
        }
        if (speed < 0.01f && airW < 0.01f && p.state == PS.NORMAL) o.legsRot = 0f
        // round the loop the boy is turned to stand on the track (see WorldRenderer.drawPlayer)
        if (p.state == PS.LOOP) o.rot += p.loopRot
        // on the ground the feet stay planted: when the hips dip (landing, skid) the legs bend to
        // absorb it instead of pushing the feet through the floor; a rising body half-lifts them
        val reach = anchorY - BoyRig.HIP_Y
        val lock = (1f - airW) * (if (o.bodyDy > 0f) 1f else 0.5f)
        o.legsSy *= (reach - o.bodyDy * lock) / reach
        return o
    }

    // ------------------------------------------------------------------ drawing
    private var gx = 0f; private var gy = 0f; private var gk = 1f
    private var gcos = 1f; private var gsin = 0f
    private var ox = 0f; private var oy = 0f

    /** Draws the boy with his feet at (ax, ay); hPx is the full sprite height in pixels. */
    fun draw(gr: Gfx, rig: BoyRig, o: Pose, ax: Float, ay: Float, hPx: Float, alpha: Float, addColor: Int, addAmt: Float) {
        gx = ax; gy = ay; gk = hPx / rig.h
        val rad = o.rot * PI.toFloat() / 180f
        gcos = cos(rad); gsin = sin(rad)
        drawPart(gr, rig.legs, o, 0, alpha, addColor, addAmt)
        drawPart(gr, rig.body, o, 1, alpha, addColor, addAmt)
        drawPart(gr, rig.armL, o, 2, alpha, addColor, addAmt)
        drawPart(gr, rig.armR, o, 3, alpha, addColor, addAmt)
    }

    /** kind: 0 legs, 1 body, 2 left arm, 3 right arm */
    private fun drawPart(gr: Gfx, part: RigPart, o: Pose, kind: Int, alpha: Float, addColor: Int, addAmt: Float) {
        // q[0..3] are the image's corners (left-top, right-top, right-bottom, left-bottom); for the
        // mirrored stride corner() reflects each one across the hip axis, which flips the image
        corner(part, o, kind, part.x0, part.y0, 0)
        corner(part, o, kind, part.x1, part.y0, 1)
        corner(part, o, kind, part.x1, part.y1, 2)
        corner(part, o, kind, part.x0, part.y1, 3)
        gr.imageQuad(part.img, 0f, 0f, part.img.w.toFloat(), part.img.h.toFloat(), q, alpha, 1f, addColor, addAmt)
    }

    /** Sprite-pixel corner -> part-local transform -> body -> global (lean, squash, spin) -> screen. */
    private fun corner(part: RigPart, o: Pose, kind: Int, sxIn: Float, syIn: Float, i: Int) {
        var x = sxIn; var y = syIn
        when (kind) {
            0 -> {
                // legs: mirrored stride, stretch and swing about the hip joint, which moves and leans
                // with the body so the pants never separate from the torso
                if (o.legsMirror) x = 2f * BoyRig.HIP_X - x
                rotScale(x - BoyRig.HIP_X, y - BoyRig.HIP_Y, o.legsRot + o.bodyRot, o.legsSx, o.legsSy)
                x = BoyRig.HIP_X + ox; y = BoyRig.HIP_Y + oy + o.bodyDy
            }
            else -> {
                if (kind >= 2) {
                    val a = if (kind == 2) o.armL else o.armR
                    val s = if (kind == 2) o.armLS else o.armRS
                    rotScale(x - part.pivotX, y - part.pivotY, a, s, s)
                    x = part.pivotX + ox; y = part.pivotY + oy
                }
                rotScale(x - BoyRig.HIP_X, y - BoyRig.HIP_Y, o.bodyRot, 1f, 1f)
                x = BoyRig.HIP_X + ox; y = BoyRig.HIP_Y + oy + o.bodyDy
            }
        }
        y += o.dy
        val lx = (x - anchorX) * o.sx * o.spin * gk
        val ly = (y - anchorY) * o.sy * gk
        q[i * 2] = gx + lx * gcos - ly * gsin
        q[i * 2 + 1] = gy + lx * gsin + ly * gcos
    }

    private fun rotScale(x: Float, y: Float, deg: Float, sx: Float, sy: Float) {
        val r = deg * PI.toFloat() / 180f
        val c = cos(r); val s = sin(r)
        val xs = x * sx; val ys = y * sy
        ox = xs * c - ys * s
        oy = xs * s + ys * c
    }
}
