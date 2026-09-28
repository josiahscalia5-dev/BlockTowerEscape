package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/** A giant hand's stage: waiting, rearing up (the warning), lunging, snapping shut, holding the boy, drawing back, done. */
object GST { const val IDLE = 0; const val WARN = 1; const val REACH = 2; const val SNAP = 3; const val HOLD = 4; const val BACK = 5; const val DONE = 6 }

/** A giant hand: the shadow hand out of a rift beside the path ([GK.HAND]) or the Sorcerer's own claw out of the sky ([GK.GUARDIAN]). */
class GrabHand(val spot: GrabSpot) {
    val guardian get() = spot.kind == GK.GUARDIAN
    val final get() = spot.kind == GK.GUARDIAN && spot.flag == 1
    var state = GST.IDLE
    var t = 0f
    /** Real seconds of its warning (the hourglass slows how it moves, never how long it warns). */
    var rt = 0f
    /** How long it has waited, reared up, for the boy (he stopped short of it). */
    var wait = 0f
    /** Where it comes from (the rift, or high in the sky), where it lunged from, and the floor it snaps at. */
    var ax = 0f; var ay = 0f; var az = 0f
    var fx = 0f; var fy = 0f; var fz = 0f
    var tx = 0f; var ty = 0f; var tz = 0f
    /** The hand now, how far it is out (0..1), how shut its fingers are, how strong its warning is, and its turn (degrees). */
    var hx = 0f; var hy = 0f; var hz = 0f
    var show = 0f; var close = 0f; var warn = 0f; var tilt = 0f
    /** The lane (and row) it aims at while it rears up: it follows the boy until it lunges. */
    var aimX = 0f; var aimZ = 0f; var floorY = 0f
    /** 1 -> 0: burnt by the shield bubble (it recoils); 1 -> 0: slammed down on empty ground. */
    var burn = 0f; var slam = 0f
    var caught = false
    var cool = 0f
    var sortKey = 0f
}

/** A patch of magical vines across a row: asleep, stirring (the warning), lashing up, withdrawing, resting. */
class Vines(val spot: GrabSpot) {
    var state = 0
    var t = 0f
    /** 0..1: how far they are up, and the warning glow as they stir. */
    var grow = 0f; var warn = 0f
    /** The lanes they cover, the floor they grow from and the row's middle. */
    var x0 = 0f; var x1 = 0f; var y = 0f; var z = 0f
    /** The boy is in the air over them this lash / already rewarded / caught this lash. */
    var over = false; var rewarded = false; var hit = false
    /** 1 -> 0: burnt by the shield, or torn when the boy broke free. */
    var burn = 0f
    var sortKey = 0f
}

/** A magical chain swinging low across the path from a floating rune ring; the open shackle on its end grabs at the ankles. */
class GrabChain(val spot: GrabSpot) {
    var px = 0f; var py = 0f; var pz = 0f
    /** The shackle (x, y) and the swing's angle. */
    var sx = 0f; var sy = 0f; var angle = 0f
    var lastSide = 0f; var lastAngle = 0f
    var cool = 0f; var rewarded = false
    /** 1 -> 0: the shackle glinting as it sweeps past the bottom of its swing near the boy. */
    var glint = 0f
    var sortKey = 0f
}

/** A small flying grabber: appears, hovers beside the boy (the warning), dives at him, flies past (or pops). */
class Grabber {
    var on = false
    var state = 0
    var t = 0f
    /** Where it hovers, relative to the boy, and which way it came from (0 left, 1 right, 2 ahead, 3 behind left, 4 behind right). */
    var ox = 0f; var oy = 0f; var oz = 0f; var dir = 0
    var x = 0f; var y = 0f; var z = 0f
    /** The dive: from, to, and its velocity (it carries on past). */
    var fx = 0f; var fy = 0f; var fz = 0f
    var tx = 0f; var ty = 0f; var tz = 0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var minD = 99f; var hit = false
    var show = 0f; var look = 0
    var sortKey = 0f
}

/** One of the little imps following the boy (they never attack: something is following...). */
class Follower {
    var on = false
    /** 1 following, 2 flying off to join the Sorcerer's wave. */
    var state = 0
    var t = 0f
    var x = 0f; var y = 0f; var z = 0f
    var side = 1f; var gap = 3f; var peek = 0f; var show = 0f; var look = 0
    var sortKey = 0f
}

/**
 * Level 7's signature threat, the GRAB ATTACKS: the Sorcerer's magic tries to grab the boy.
 *
 *  - Giant shadow hands rise out of a violet rift beside the path, rear up (the warning: the rift glows, a marker on the
 *    path shows the lane it aims at and it follows him), then lunge at where he is heading and snap shut.
 *  - Magical vines stir in a row of the path (they glow and sprout), then lash up round the legs for a moment.
 *  - Flying grabbers appear beside, ahead of or behind him, hover (eyes flaring, claws open, a dotted line to him), then
 *    dive at him, one after another.
 *  - A magical chain swings low across the path from a floating rune ring: jump its shackle as it sweeps past.
 *  - The Guardian's grab: the Sorcerer reaches down out of the sky with his own claw (a WARNING first, its shadow on the
 *    path); the camera draws back to show the boy, the way ahead and the Sorcerer. The last one, just before the
 *    Celestial Gate, is his final grab: escape it and the gate is revealed (EPIC ESCAPE).
 *
 * Every grab is announced, aims only when it strikes (never where the boy is going sideways) and can be escaped by
 * jumping, stepping aside or changing pace. A grab that catches him costs a heart through [Game.hurt] like any hazard
 * (the shield takes it instead and burns the hand), holds him for a moment, then lets him go on the path. Escaping one
 * pays: NARROW ESCAPE, PERFECT DODGE (jumped clean over it), GUARDIAN ESCAPED, EPIC ESCAPE. Everything runs on the
 * hazard clock (the hourglass slows it), and the grabbers are a small pool that is never allowed to grow.
 */
class GrabSystem(val g: Game) {
    companion object {
        /** Shadow hand: rears up when the boy is this many rows away, for at least [HAND_WARN] s; lunge, snap, hold, draw back. */
        const val HAND_LEAD = 7.5f; const val HAND_WARN = 1.05f; const val HAND_REACH = 0.55f; const val HAND_SNAP = 0.14f
        const val HAND_HOLD = 0.5f; const val HAND_BACK = 0.7f
        /** How long a hand waits reared up for a boy who stopped short of it before it sinks back (and rises again). */
        const val HAND_WAIT = 3.2f
        /** The Guardian's grab: longer warning, a slower, bigger reach. */
        const val GUARD_LEAD = 10.5f; const val GUARD_WARN = 1.35f; const val GUARD_REACH = 0.6f; const val GUARD_SNAP = 0.16f
        const val GUARD_HOLD = 0.65f; const val GUARD_BACK = 0.9f
        /** Where a hand snaps: this far to the side and along the path; feet this high above the floor are clear of it. */
        const val HALF_X = 0.78f; const val HALF_Z = 0.85f; const val CLEAR_Y = 1.0f
        const val G_HALF_X = 0.85f; const val G_HALF_Z = 1.0f; const val G_CLEAR_Y = 1.05f
        /** An escape this close to where it snapped is a narrow one. */
        const val NEAR = 2.3f
        /** Vines: stir, lash, withdraw, rest; the height they reach (jump higher than this). */
        const val VINE_LEAD = 8f; const val VINE_WARN = 0.9f; const val VINE_LASH = 0.85f; const val VINE_BACK = 0.35f; const val VINE_REST = 1.1f
        const val VINE_H = 0.62f; const val VINE_HOLD = 0.45f
        /** Grabbers: appear, hover (the warning), dive, fly on past; how close to his middle a dive catches him. */
        const val GRABBER_IN = 0.35f; const val GRABBER_WARN = 0.8f; const val GRABBER_DASH = 0.6f; const val GRABBER_PAST = 0.45f
        const val GRABBER_R = 0.55f; const val GRABBERS_MAX = 2
        /** The chain's swing (radians either side) and the shackle's radius. */
        const val CHAIN_AMP = 0.95f; const val SHACKLE_R = 0.36f
        /** Rewards. */
        const val NARROW = 100; const val PERFECT = 150; const val GUARDIAN = 250; const val EPIC = 500
    }

    private val rng = Rng(9191)
    val hands = ArrayList<GrabHand>()
    val vines = ArrayList<Vines>()
    val chains = ArrayList<GrabChain>()
    val grabbers = Array(4) { Grabber() }
    val followers = Array(3) { Follower() }
    /** The grabber stretches and follower stretches on the course, with their timers (and whether they have begun). */
    private val swarms = ArrayList<GrabSpot>()
    private var swarmT = FloatArray(0)
    private var swarmDir = IntArray(0)
    private val trails = ArrayList<GrabSpot>()
    private var trailOn = BooleanArray(0)

    /** What holds the boy right now (a hand or vines), and for how long. */
    var holding: Any? = null
        private set
    private var holdT = 0f; private var holdDur = 0f; private var holdX = 0f; private var holdY = 0f; private var holdZ = 0f; private var lift = 0f

    /** The Sorcerer comes back out of the mist for his grabs (0..1); the gate's reveal after the final grab (1 -> 0). */
    var skyReturn = 0f
    var reveal = 0f
    /** A Guardian grab is under way (the chase camera draws back while it is, and a moment after). */
    private var camHold = 0f
    val cinematic get() = camHold > 0f || hands.any { it.guardian && it.state in GST.WARN..GST.HOLD }

    /** First of each kind: taught once per level. */
    private var taughtHand = false; private var taughtVines = false; private var taughtChain = false; private var taughtGrabbers = false

    // player stats (the playtest report)
    var grabsAttempted = 0; var grabsDodged = 0; var grabsHit = 0; var grabsBlocked = 0
    var narrow = 0; var perfect = 0; var guardianEscapes = 0; var epic = 0
    var vineLashes = 0; var chainPasses = 0; var grabberDives = 0; var followersSeen = 0; var guardianGrabs = 0
    /** Seconds the hazard clock ran slow while a grab was lunging (the hourglass at work on them). */
    var slowedGrabT = 0f

    fun reset() {
        hands.clear(); vines.clear(); chains.clear(); swarms.clear(); trails.clear()
        for (sp in g.world.grabs) when (sp.kind) {
            GK.HAND, GK.GUARDIAN -> hands.add(GrabHand(sp))
            GK.VINES -> vines.add(Vines(sp))
            GK.CHAIN -> chains.add(GrabChain(sp))
            GK.GRABBERS -> swarms.add(sp)
            GK.FOLLOWERS -> trails.add(sp)
        }
        swarmT = FloatArray(swarms.size) { swarms[it].phase }; swarmDir = IntArray(swarms.size)
        trailOn = BooleanArray(trails.size)
        for (gb in grabbers) gb.on = false
        for (f in followers) f.on = false
        holding = null; g.player.held = 0
        skyReturn = 0f; reveal = 0f; camHold = 0f
        taughtHand = false; taughtVines = false; taughtChain = false; taughtGrabbers = false
        grabsAttempted = 0; grabsDodged = 0; grabsHit = 0; grabsBlocked = 0
        narrow = 0; perfect = 0; guardianEscapes = 0; epic = 0
        vineLashes = 0; chainPasses = 0; grabberDives = 0; followersSeen = 0; guardianGrabs = 0; slowedGrabT = 0f
    }

    /** Continuing from a checkpoint: the grabs after it wait again (what the boy already escaped before it stays escaped). */
    fun resetAfter(cpZ: Float) {
        for (h in hands) if (h.spot.z > cpZ) { h.state = GST.IDLE; h.t = 0f; h.show = 0f; h.close = 0f; h.warn = 0f; h.caught = false; h.cool = 0f; h.wait = 0f }
            else if (h.state != GST.DONE && h.state != GST.IDLE) h.state = GST.DONE
        for (v in vines) { v.state = 0; v.t = 0f; v.grow = 0f; v.warn = 0f }
        for (c in chains) if (c.spot.z > cpZ) c.rewarded = false
        for ((i, sp) in swarms.withIndex()) if (sp.z + sp.len > cpZ) { swarmT[i] = sp.phase; swarmDir[i] = 0 }
        for ((i, sp) in trails.withIndex()) if (sp.z + sp.len > cpZ) trailOn[i] = false
        for (gb in grabbers) gb.on = false
        for (f in followers) f.on = false
        holding = null; g.player.held = 0
        skyReturn = 0f; camHold = 0f; reveal = 0f
    }

    /** A fall-recovery ride put the boy back on the course: grabs under way draw back (they will try again later). */
    fun onRecovered() {
        for (h in hands) if (h.state == GST.WARN || h.state == GST.REACH || h.state == GST.SNAP) {
            h.state = GST.BACK; h.t = 0f; h.fx = h.hx; h.fy = h.hy; h.fz = h.hz; h.cool = 1.2f
        }
        for (gb in grabbers) if (gb.on && gb.state <= 3) pop(gb, false)
        release(false)
    }

    // ------------------------------------------------------------------ update
    private fun playing() = g.state == GS.PLAY && g.player.state == PS.NORMAL

    /** [dt]: game time (visuals); [k]: the hazard clock's rate (the hourglass slows every grab). */
    fun update(dt: Float, k: Float) {
        val hdt = dt * k
        camHold = max(0f, camHold - dt)
        reveal = max(0f, reveal - dt / 3.6f)
        val guardianOut = hands.any { it.guardian && it.state in GST.WARN..GST.HOLD }
        skyReturn = approach(skyReturn, if (guardianOut) 1f else 0f, dt * (if (guardianOut) 1.4f else 0.5f))
        if (guardianOut) camHold = 1.1f
        if (g.state != GS.PLAY && holding != null) release(false)
        for (h in hands) updateHand(h, dt, hdt)
        for (v in vines) updateVines(v, dt, hdt)
        for (c in chains) updateChain(c, dt)
        updateSwarms(hdt)
        for (gb in grabbers) if (gb.on) updateGrabber(gb, dt, hdt)
        updateFollowers(dt)
        updateThreat(dt)
        if (k < 0.9f && hands.any { it.state == GST.REACH } || k < 0.9f && grabbers.any { it.on && it.state == 3 }) slowedGrabT += dt
    }

    // ------------------------------------------------------------------ giant hands (the shadow hand, the Guardian's claw)
    private fun rowFloor(x: Float, z: Float): Float {
        val y = g.sorcery.floorAt(x, z)
        return if (y.isNaN()) g.world.levelAt(floor(z).toInt()) else y
    }

    private fun updateHand(h: GrabHand, dt: Float, hdt: Float) {
        val p = g.player
        val sp = h.spot
        val G = h.guardian
        h.burn = max(0f, h.burn - dt * 1.6f)
        h.slam = max(0f, h.slam - dt * 2.5f)
        val row = floor(sp.z).toInt()
        val px = g.world.pathXAt(row)
        h.floorY = g.world.levelAt(row)
        val zc = sp.z + 0.5f
        val reach = if (G) GUARD_REACH else HAND_REACH
        val snap = if (G) GUARD_SNAP else HAND_SNAP
        when (h.state) {
            GST.IDLE -> {
                if (h.cool > 0f) { h.cool -= hdt; return }
                if (!playing()) return
                if (p.z >= sp.z - (if (G) GUARD_LEAD else HAND_LEAD) && p.z < sp.z + 1.5f) begin(h, px)
            }
            GST.WARN -> {
                h.t += hdt; h.rt += dt
                h.aimX = approach(h.aimX, clamp(p.x, px - 1.3f, px + 1.3f), dt * 5f)
                h.aimZ = zc
                h.warn = min(1f, h.t / 0.5f)
                if (G) {
                    // high over the path ahead of the boy, looming (it comes down out of the sky from the Sorcerer's side)
                    val u = smooth(h.t / 0.7f)
                    val rx = lerp(px, h.aimX, 0.35f) - 1.2f + 0.25f * sin(g.t * 1.3f)
                    val ry = h.floorY + 4.5f + 0.2f * sin(g.t * 2.1f)
                    val rz = max(p.z + 7.2f, zc + 1.5f)
                    h.hx = lerp(h.ax, rx, u); h.hy = lerp(h.ay, ry, u); h.hz = lerp(h.az, rz, u)
                    h.tilt = lerp(-20f, 6f, u) + 3f * sin(g.t * 5f)
                    h.show = min(1f, h.t / 0.45f)
                    g.sorcery.charge = max(g.sorcery.charge, 0.8f)
                } else {
                    // it rises out of the rift and rears up beside the path, fingers twitching, leaning toward him
                    val u = smooth(h.t / 0.45f)
                    val side = sp.x
                    val rx = px + side * 2.15f + (h.aimX - px) * 0.1f
                    val ry = h.floorY + 1.95f + 0.08f * sin(g.t * 3f)
                    val rz = zc + 0.9f
                    h.hx = lerp(h.ax, rx, u); h.hy = lerp(h.ay, ry, u); h.hz = lerp(h.az, rz, u)
                    // (the claw's picture has its wrist at the upper left: turned so the wrist points down into the rift
                    // and the fingers up at the path, mirrored on the right)
                    h.tilt = side * (lerp(20f, 66f, u) + 4f * sin(g.t * 9f))
                    h.show = min(1f, h.t / 0.3f)
                }
                h.close = 0.15f + 0.1f * sin(g.t * 7f)
                if (!playing()) { back(h, 1.2f); return }
                // lunge when he will be at its row by the time it gets there (or he is on it, or has waited too long in
                // front). Its strike takes longer while the hourglass slows it, so it strikes that much earlier.
                val dz = zc - p.z
                val vz = max(0f, p.vz)
                val eta = if (vz > 0.3f) dz / vz else 99f
                val minWarn = if (G) GUARD_WARN else HAND_WARN
                // (he has run on past it: it strikes at him now, so the moment never lingers behind him)
                val outran = dz < -3.5f && h.rt >= minWarn * 0.4f
                if (h.rt >= minWarn || outran) {
                    val go = outran || eta <= reach / max(0.2f, g.hazK) + 0.04f || dz < 0.9f || (h.wait > HAND_WAIT && dz < 3.5f)
                    if (go) lunge(h, px)
                    else {
                        h.wait += hdt
                        if (h.wait > HAND_WAIT + 2.5f) back(h, 1.2f)
                    }
                }
            }
            GST.REACH -> {
                h.t += hdt
                val u = clamp01(h.t / reach)
                // a strike: most of the way at once, then it closes in over its mark (readable: the boy sees where it will shut)
                val e = easeOutCubic(u)
                // (it closes over him from above and its own side, so its fingers are seen coming down round him)
                val gx = h.tx + (if (G) -0.3f else -sp.x * 0.5f); val gy = h.ty + (if (G) 1.5f else 1.2f); val gz = h.tz + 0.05f
                h.hx = lerp(h.fx, gx, e); h.hy = lerp(h.fy, gy, e) + (if (G) 0.4f else 0.5f) * sin(e * Math.PI.toFloat()); h.hz = lerp(h.fz, gz, e)
                h.tilt = lerp(h.tilt, if (G) 12f else sp.x * 12f, min(1f, dt * 8f))
                h.close = 0.05f
                h.warn = 1f
                if (u >= 1f) { h.state = GST.SNAP; h.t = 0f; g.platform.sound(Sfx.GRAB, if (G) 1f else 0.8f, if (G) 0.8f else 1.2f) }
            }
            GST.SNAP -> {
                h.t += hdt
                h.close = clamp01(h.t / snap)
                if (!h.caught && playing() && p.invuln <= 0f && holding == null) {
                    val hx = if (G) G_HALF_X else HALF_X; val hz = if (G) G_HALF_Z else HALF_Z; val cy = if (G) G_CLEAR_Y else CLEAR_Y
                    if (abs(p.x - h.tx) < hx && abs(p.z - h.tz) < hz && p.y < h.ty + cy && p.y > h.ty - 1.2f) catchBy(h)
                }
                if (h.state == GST.SNAP && h.t >= snap) missed(h)
            }
            GST.HOLD -> {
                h.t += dt
                h.close = 1f
                h.hx = p.x + (if (G) -0.25f else -sp.x * 0.4f); h.hy = p.y + (if (G) 1.0f else 0.85f); h.hz = p.z - 0.05f
            }
            GST.BACK -> {
                h.t += hdt
                val dur = if (G) GUARD_BACK else HAND_BACK
                val u = smooth(h.t / dur)
                h.hx = lerp(h.fx, h.ax, u); h.hy = lerp(h.fy, h.ay, u); h.hz = lerp(h.fz, h.az, u)
                h.close = max(0f, h.close - dt * 2f)
                // (the Guardian's claw is huge: it fades out quickly as it pulls away, never lingering over the view)
                h.show = if (G) 1f - smooth(h.t / (dur * 0.5f)) else 1f - u
                h.warn = max(0f, h.warn - dt * 3f)
                if (h.t >= dur) { h.show = 0f; h.state = if (h.cool > 0f) GST.IDLE else GST.DONE; h.t = 0f; h.wait = 0f }
            }
        }
    }

    /** It rears up: the warning. */
    private fun begin(h: GrabHand, px: Float) {
        val p = g.player
        val sp = h.spot
        h.state = GST.WARN; h.t = 0f; h.rt = 0f; h.wait = 0f; h.caught = false; h.close = 0f; h.show = 0f
        h.aimX = clamp(p.x, px - 1.3f, px + 1.3f); h.aimZ = sp.z + 0.5f
        grabsAttempted++
        if (h.guardian) {
            guardianGrabs++
            // out of the sky, from the Sorcerer's side
            h.ax = px - 3.4f; h.ay = h.floorY + 11f; h.az = p.z + 16f
            h.hx = h.ax; h.hy = h.ay; h.hz = h.az
            if (h.final) g.fx.banner("WARNING!", "HIS LAST GRAB — DODGE IT AND REACH THE GATE!", 0xFFFF5AA0.toInt(), 1.7f, true)
            else g.fx.banner("WARNING!", "THE SORCERER REACHES FOR YOU — DODGE HIS CLAW!", 0xFFFF5AA0.toInt(), 1.7f, true)
            g.platform.sound(Sfx.WARNING, 0.85f, 0.95f); g.platform.sound(Sfx.ROAR, 0.75f, 1.2f); g.platform.haptic(true)
            g.shake = max(g.shake, 0.35f); g.flashWhite = max(g.flashWhite, 0.12f)
            g.sorcery.charge = 1f
        } else {
            // out of a violet rift at the edge of the island beside the path
            h.ax = px + sp.x * 2.75f; h.ay = h.floorY - 0.35f; h.az = sp.z + 1.1f
            h.hx = h.ax; h.hy = h.ay; h.hz = h.az
            if (!taughtHand) {
                taughtHand = true
                g.fx.banner("GRAB ATTACK!", "A GIANT HAND! JUMP OR STEP ASIDE WHEN IT STRIKES", 0xFFE070FF.toInt(), 1.9f, true)
            }
            val d = len3(p.x - h.ax, p.y - h.ay, p.z - h.az)
            g.platform.sound(Sfx.WARNING, 0.55f, 1.35f); g.platform.sound(Sfx.WHOOSH, clamp01(1.2f - d / 16f) * 0.8f, 0.55f)
            g.fx.burst(h.ax, h.ay + 0.4f, h.az, 18, PK.SPARK, 0xFFB060FF.toInt(), 3.5f, 0.12f, 0.6f)
        }
    }

    /** It strikes: the target locks where the boy will be at the moment it closes (his lane now; his pace carries him along). */
    private fun lunge(h: GrabHand, px: Float) {
        val p = g.player
        val sp = h.spot
        val reach = if (h.guardian) GUARD_REACH else HAND_REACH
        val snap = if (h.guardian) GUARD_SNAP else HAND_SNAP
        h.state = GST.REACH; h.t = 0f
        h.fx = h.hx; h.fy = h.hy; h.fz = h.hz
        h.tx = clamp(p.x, px - 2.2f, px + 2.2f)
        // (where he will be when it shuts, in real time: the hourglass makes its strike slower)
        h.tz = clamp(p.z + ahead((reach + snap * 0.5f) / max(0.2f, g.hazK)), sp.z - 2f, sp.z + (if (h.guardian) 6f else 3.5f))
        h.ty = rowFloor(h.tx, h.tz)
        h.warn = 1f
        g.platform.sound(Sfx.WHOOSH, 1f, if (h.guardian) 0.6f else 0.9f)
        if (h.guardian) { g.platform.sound(Sfx.ROAR, 0.5f, 1.5f); g.shake = max(g.shake, 0.3f) }
    }

    /** How far the boy's pace carries him in [dt] seconds (a speed pad's burst wears off to a run). */
    private fun ahead(dt: Float): Float {
        val p = g.player
        val vz = max(0f, p.vz)
        if (p.dashT <= 0f || vz <= Tune.RUN) return vz * dt
        val burst = min(p.dashT, dt)
        return vz * burst + Tune.RUN * (dt - burst)
    }

    /** It shut on the boy: a heart (the shield takes it and burns the hand), then it holds him for a moment. */
    private fun catchBy(h: GrabHand) {
        val p = g.player
        h.caught = true
        val shielded = g.shieldOn
        g.hurt(if (h.guardian) "guardian grab" else "grab", null)
        g.shake = max(g.shake, 0.45f)
        g.fx.burst(p.x, p.y + 0.9f, p.z, 20, PK.SPARK, 0xFFD070FF.toInt(), 4f, 0.12f, 0.6f)
        if (shielded) {
            grabsBlocked++
            h.burn = 1f; back(h, 0f)
            g.fx.burst(h.hx, h.hy, h.hz, 26, PK.STAR, 0xFFB8ECFF.toInt(), 5f, 0.14f, 0.6f)
            g.platform.sound(Sfx.EXPLODE, 0.5f, 1.6f)
            return
        }
        grabsHit++
        if (g.state != GS.PLAY || g.hearts <= 0) { back(h, 0f); return }
        h.state = GST.HOLD; h.t = 0f
        hold(h, if (h.guardian) GUARD_HOLD else HAND_HOLD, if (h.guardian) 0.95f else 0.7f, 1)
        g.platform.sound(Sfx.GRAB, 1f, 0.9f); g.platform.haptic(true)
    }

    /** It shut on empty ground: the boy escaped (a reward when it was close). */
    private fun missed(h: GrabHand) {
        val p = g.player
        h.slam = 1f
        back(h, 0f)
        g.fx.dust(h.tx, h.ty, h.tz, if (h.guardian) 10 else 6, 0xFFC8B0E8.toInt())
        g.fx.burst(h.tx, h.ty + 0.3f, h.tz, if (h.guardian) 26 else 14, PK.SPARK, 0xFFB070FF.toInt(), 4f, 0.12f, 0.5f)
        val d = len3(p.x - h.tx, 0f, p.z - h.tz)
        g.platform.sound(Sfx.STOMP, clamp01(1.2f - d / 10f) * (if (h.guardian) 1f else 0.6f), if (h.guardian) 0.8f else 1.3f)
        g.shake = max(g.shake, (if (h.guardian) 0.4f else 0.2f) * clamp01(1.3f - d / 8f))
        if (!playing() || h.caught) return
        grabsDodged++
        val hx = if (h.guardian) G_HALF_X else HALF_X; val hz = if (h.guardian) G_HALF_Z else HALF_Z
        val over = abs(p.x - h.tx) < hx && abs(p.z - h.tz) < hz
        p.dodgeT = 0f; p.dodgeDir = if (p.x >= h.tx) 1f else -1f
        when {
            h.final -> escapeEpic()
            h.guardian -> reward(GUARDIAN, "GUARDIAN ESCAPED!", 0xFF7FFFA0.toInt())
            over -> reward(PERFECT, "PERFECT DODGE!", 0xFFFFE680.toInt())
            d < NEAR -> reward(NARROW, "NARROW ESCAPE!", 0xFF9FE8FF.toInt())
        }
    }

    /** It draws back (to its rift, or up into the sky); with [cool] > 0 it will rise again after that long. */
    private fun back(h: GrabHand, cool: Float) {
        h.state = GST.BACK; h.t = 0f; h.cool = cool
        h.fx = h.hx; h.fy = h.hy; h.fz = h.hz
        if (h.guardian) { h.ax = h.hx - 2.5f; h.ay = h.hy + 8f; h.az = h.hz + 10f }
    }

    // ------------------------------------------------------------------ holding the boy
    private fun hold(by: Any, dur: Float, lift: Float, kind: Int) {
        val p = g.player
        holding = by; holdT = 0f; holdDur = dur; this.lift = lift
        holdX = p.x; holdY = p.y; holdZ = p.z
        p.held = kind
        g.swipe.stop()
    }

    /** While a grab holds him (instead of his physics): lifted and shaken in a hand, or stuck by the legs in the vines. */
    fun holdPlayer(dt: Float) {
        val p = g.player
        holdT += dt
        val u = smooth(holdT / 0.2f)
        p.x = holdX + sin(holdT * 38f) * 0.035f * (1f - 0.5f * u)
        p.y = holdY + lift * u
        p.z = holdZ
        p.vx = 0f; p.vy = 0f; p.vz = 0f
        p.grounded = lift <= 0f; p.jumping = false
        p.airW = approach(p.airW, if (lift > 0f) 1f else 0f, dt * 8f)
        p.runW = approach(p.runW, 0f, dt * 6f)
        g.steerX = p.x
        if (holdT >= holdDur) release(true)
    }

    /** Lets him go: thrown down behind the hand onto the path, or breaking free of the vines with a hop. */
    private fun release(effects: Boolean) {
        val by = holding ?: return
        holding = null
        val p = g.player
        p.held = 0
        if (by is GrabHand) {
            back(by, 0f)
            if (effects) {
                p.vy = 3.5f; p.vz = -2.2f; p.vx = 0f; p.grounded = false; p.jumping = false
                g.platform.sound(Sfx.WHOOSH, 0.7f, 1.1f)
                g.fx.burst(p.x, p.y + 0.8f, p.z, 12, PK.SPARK, 0xFFD070FF.toInt(), 3f, 0.1f, 0.4f)
            }
        } else if (by is Vines) {
            by.state = 3; by.t = 0f; by.burn = 1f
            if (effects) {
                p.vy = 5f; p.grounded = false; p.jumping = true; p.jumpT = 0f
                g.platform.sound(Sfx.CRACK, 0.8f, 1.4f)
                leaves(by.x0 + (by.x1 - by.x0) * 0.5f, by.y + 0.4f, by.z, 16)
            }
        }
        g.steerX = p.x
    }

    // ------------------------------------------------------------------ vines
    private fun updateVines(v: Vines, dt: Float, hdt: Float) {
        val p = g.player
        val sp = v.spot
        val row = floor(sp.z).toInt()
        val px = g.world.pathXAt(row)
        v.x0 = px - sp.len; v.x1 = px + sp.len; v.y = g.world.levelAt(row); v.z = sp.z + 0.5f
        v.burn = max(0f, v.burn - dt * 1.5f)
        v.t += hdt
        when (v.state) {
            0 -> {
                v.grow = 0f; v.warn = max(0f, v.warn - dt * 2f)
                if (playing() && p.z >= v.z - VINE_LEAD && p.z < v.z - 0.4f) stir(v)
            }
            1 -> {
                v.warn = min(1f, v.t / VINE_WARN)
                v.grow = 0.12f * v.warn
                if (v.t >= VINE_WARN) {
                    v.state = 2; v.t = 0f; v.over = false; v.rewarded = false; v.hit = false
                    vineLashes++
                    val d = len3(p.x - px, p.y - v.y, p.z - v.z)
                    g.platform.sound(Sfx.WHOOSH, clamp01(1.2f - d / 12f) * 0.7f, 1.5f)
                    g.platform.sound(Sfx.CRACK, clamp01(1.2f - d / 12f) * 0.45f, 1.8f)
                    leaves(px, v.y + 0.2f, v.z, 10)
                }
            }
            2 -> {
                v.grow = if (v.t < 0.14f) easeOutBack(v.t / 0.14f) else 1f
                v.warn = 1f
                vineContact(v)
                if (v.t >= VINE_LASH && holding !== v) { v.state = 3; v.t = 0f }
            }
            3 -> {
                v.grow = max(0f, 1f - v.t / VINE_BACK)
                v.warn = max(0f, v.warn - dt * 3f)
                if (v.t >= VINE_BACK) { v.state = 4; v.t = 0f }
            }
            4 -> {
                v.grow = 0f; v.warn = 0f
                // they stir again while he is still in front of them
                if (v.t >= VINE_REST) { if (playing() && p.z >= v.z - VINE_LEAD - 2f && p.z < v.z - 0.4f) stir(v) else { v.state = 0; v.t = 0f } }
            }
        }
    }

    private fun stir(v: Vines) {
        v.state = 1; v.t = 0f
        val p = g.player
        if (!taughtVines) { taughtVines = true; g.fx.toast("MAGIC VINES!", "JUMP THEM WHEN THEY LASH UP", 0xFF9CFFB8.toInt(), 2.2f) }
        val d = len3(p.x - v.x0, p.y - v.y, p.z - v.z)
        g.platform.sound(Sfx.CRUMBLE, clamp01(1.2f - d / 12f) * 0.45f, 1.7f)
    }

    private fun vineContact(v: Vines) {
        val p = g.player
        if (!playing() || holding != null) return
        val inRow = abs(p.z - v.z) < 0.5f + Tune.RADIUS - 0.06f
        val inLanes = p.x > v.x0 - 0.6f && p.x < v.x1 + 0.6f
        val danger = v.t > 0.06f
        if (danger && inRow && inLanes && !v.hit && p.invuln <= 0f && p.y < v.y + VINE_H && p.y > v.y - 0.8f) {
            v.hit = true
            val shielded = g.shieldOn
            g.hurt("vines", null)
            if (shielded) { grabsBlocked++; v.burn = 1f; v.state = 3; v.t = 0f; leaves(p.x, v.y + 0.3f, v.z, 14); return }
            grabsHit++
            if (g.state != GS.PLAY || g.hearts <= 0) return
            hold(v, VINE_HOLD, 0f, 2)
            g.platform.sound(Sfx.GRAB, 0.7f, 1.5f)
            return
        }
        if (inRow && inLanes && p.y >= v.y + VINE_H) v.over = true
        // cleared them in the air while they lashed: a perfect dodge (paid once he is past)
        if (v.over && !v.rewarded && !v.hit && p.z > v.z + 0.5f + Tune.RADIUS) {
            v.rewarded = true; grabsDodged++
            p.dodgeT = 0f; p.dodgeDir = 0f
            reward(PERFECT, "PERFECT DODGE!", 0xFFFFE680.toInt())
        }
    }

    /** Torn leaves and petals flying (the vines lashing, snapping, burning). */
    private fun leaves(x: Float, y: Float, z: Float, n: Int) {
        for (i in 0 until n) {
            val q = g.fx.spawn()
            q.x = x + rng.f(-1.2f, 1.2f); q.y = y + rng.f(0f, 0.5f); q.z = z + rng.f(-0.4f, 0.4f)
            q.vx = rng.f(-2f, 2f); q.vy = rng.f(1.5f, 4f); q.vz = rng.f(-1.5f, 1.5f)
            q.life = rng.f(0.7f, 1.2f); q.maxLife = q.life; q.size = rng.f(0.06f, 0.1f)
            q.color = when (i % 3) { 0 -> 0xFF5AD884.toInt(); 1 -> 0xFFB86CFF.toInt(); else -> 0xFFFF9CD8.toInt() }
            q.kind = PK.CONFETTI; q.gravity = 7f; q.drag = 1.6f; q.rot = rng.f(0f, TAU); q.vrot = rng.f(-9f, 9f)
        }
    }

    // ------------------------------------------------------------------ the magical chain
    private fun updateChain(c: GrabChain, dt: Float) {
        val p = g.player
        val sp = c.spot
        val row = floor(sp.z).toInt()
        val floorY = g.world.levelAt(row)
        c.px = g.world.pathXAt(row) + sp.x; c.py = floorY + sp.len + 0.4f; c.pz = sp.z + 0.5f
        c.lastAngle = c.angle
        c.angle = CHAIN_AMP * sin(TAU * g.hazT / sp.period + sp.phase)
        c.sx = c.px + sp.len * sin(c.angle); c.sy = c.py - sp.len * kotlin.math.cos(c.angle)
        c.glint = max(0f, c.glint - dt * 2.5f)
        c.cool = max(0f, c.cool - dt)
        val dz = abs(p.z - c.pz)
        // a clink and a glint each time it sweeps through the bottom of its swing, while he is near
        if (sign(c.angle) != sign(c.lastAngle) && p.z > c.pz - 9f && p.z < c.pz + 2f) {
            c.glint = 1f; chainPasses++
            val d = len3(p.x - c.sx, p.y - c.sy, p.z - c.pz)
            g.platform.sound(Sfx.SPIKE, clamp01(1.2f - d / 10f) * 0.4f, 0.55f)
            g.platform.sound(Sfx.WHOOSH, clamp01(1.2f - d / 10f) * 0.35f, 1.3f)
            if (!taughtChain && p.z > c.pz - 8f) { taughtChain = true; g.fx.toast("A MAGIC CHAIN!", "JUMP ITS SHACKLE AS IT SWEEPS PAST", 0xFFE0B0FF.toInt(), 2.2f) }
        }
        if (!playing()) { c.lastSide = sign(p.x - c.sx); return }
        val side = sign(p.x - c.sx)
        if (dz < SHACKLE_R + Tune.RADIUS && abs(p.x - c.sx) < SHACKLE_R + Tune.RADIUS && p.y < c.sy + SHACKLE_R && p.y + Tune.HEIGHT > c.sy - SHACKLE_R &&
            p.invuln <= 0f && c.cool <= 0f && holding == null) {
            c.cool = 1f
            val shielded = g.shieldOn
            g.hurt("chain", null)
            g.fx.burst(c.sx, c.sy, c.pz, 14, PK.SPARK, 0xFFE0A0FF.toInt(), 3.5f, 0.1f, 0.45f)
            g.platform.sound(Sfx.GRAB, 0.6f, 1.6f)
            if (shielded) grabsBlocked++ else {
                grabsHit++
                // the shackle snaps at his ankles and trips him back
                if (g.state == GS.PLAY) { p.vy = 5f; p.vz = -2f; p.vx = 0f; p.grounded = false; p.jumping = false; g.swipe.stop() }
            }
        } else if (side != c.lastSide && c.lastSide != 0f && dz < 0.65f && p.y > c.sy + SHACKLE_R - 0.05f && !c.rewarded && p.invuln <= 0f) {
            // the shackle swept right under him while he was in the air
            c.rewarded = true; grabsDodged++
            p.dodgeT = 0f; p.dodgeDir = -side
            reward(PERFECT, "PERFECT DODGE!", 0xFFFFE680.toInt())
        }
        c.lastSide = side
    }

    // ------------------------------------------------------------------ flying grabbers
    private val offX = floatArrayOf(-3.3f, 3.3f, 0f, -2.4f, 2.4f)
    private val offY = floatArrayOf(1.9f, 1.9f, 2.9f, 2.4f, 2.4f)
    private val offZ = floatArrayOf(2.8f, 2.8f, 7.5f, -1.4f, -1.4f)

    private fun updateSwarms(hdt: Float) {
        val p = g.player
        for ((i, sp) in swarms.withIndex()) {
            if (!playing() || p.z < sp.z || p.z > sp.z + sp.len) continue
            swarmT[i] -= hdt
            if (swarmT[i] > 0f || grabbers.count { it.on && it.state <= 3 } >= GRABBERS_MAX) continue
            swarmT[i] = sp.period
            val gb = grabbers.firstOrNull { !it.on } ?: continue
            spawn(gb, swarmDir[i] % 5); swarmDir[i]++
        }
    }

    private fun spawn(gb: Grabber, dir: Int) {
        val p = g.player
        gb.on = true; gb.state = 1; gb.t = 0f; gb.dir = dir; gb.show = 0f; gb.minD = 99f; gb.hit = false; gb.look = dir % 2
        gb.ox = offX[dir]; gb.oy = offY[dir]; gb.oz = offZ[dir]
        hover(gb, 0f)
        if (!taughtGrabbers) { taughtGrabbers = true; g.fx.banner("GRABBERS!", "THEY DIVE AT YOU — STEP ASIDE OR JUMP", 0xFFE070FF.toInt(), 1.9f, true) }
        g.fx.burst(gb.x, gb.y, gb.z, 16, PK.SPARK, 0xFFC878FF.toInt(), 3.5f, 0.12f, 0.5f)
        val d = len3(p.x - gb.x, p.y - gb.y, p.z - gb.z)
        g.platform.sound(Sfx.WHOOSH, clamp01(1.2f - d / 14f) * 0.55f, 1.9f)
    }

    /** Hovering beside (ahead of, behind) the boy, bobbing: where it waits before it dives. */
    private fun hover(gb: Grabber, bob: Float) {
        val p = g.player
        val r = floor(p.z).toInt()
        val px = g.world.pathXAt(r)
        val lvl = max(g.world.levelAt(r), p.y)
        gb.x = px * 0.7f + p.x * 0.3f + gb.ox + 0.15f * sin(g.t * 2.3f + gb.dir)
        gb.y = lvl + gb.oy + bob
        gb.z = p.z + gb.oz
    }

    private fun updateGrabber(gb: Grabber, dt: Float, hdt: Float) {
        val p = g.player
        gb.t += hdt
        when (gb.state) {
            1 -> {
                gb.show = min(1f, gb.show + dt * 3f)
                hover(gb, 0.15f * sin(g.t * 4f + gb.dir) + (1f - smooth(gb.t / GRABBER_IN)) * 1.5f)
                if (gb.t >= GRABBER_IN) {
                    gb.state = 2; gb.t = 0f
                    val d = len3(p.x - gb.x, p.y - gb.y, p.z - gb.z)
                    g.platform.sound(Sfx.WARNING, clamp01(1.2f - d / 14f) * 0.35f, 1.8f)
                }
            }
            2 -> {
                gb.show = 1f
                hover(gb, 0.15f * sin(g.t * 4f + gb.dir))
                // it dives once he is on solid ground where it will reach him (never while he is in the air over a gap)
                if (gb.t >= GRABBER_WARN && playing()) {
                    val tz = p.z + ahead(GRABBER_DASH)
                    val floorThere = !g.sorcery.floorAt(p.x, tz).isNaN()
                    if (p.grounded && floorThere) dive(gb)
                    else if (gb.t > GRABBER_WARN + 2.5f) { gb.state = 4; gb.t = 0f; gb.vx = 0f; gb.vy = 3f; gb.vz = 4f }
                }
                if (!playing()) pop(gb, false)
            }
            3 -> {
                val u = clamp01(gb.t / GRABBER_DASH)
                gb.x = lerp(gb.fx, gb.tx, u); gb.y = lerp(gb.fy, gb.ty, u); gb.z = lerp(gb.fz, gb.tz, u)
                val d = len3(p.x - gb.x, p.y + 0.75f - gb.y, p.z - gb.z)
                gb.minD = min(gb.minD, d)
                if (d < GRABBER_R && playing() && p.invuln <= 0f && holding == null) {
                    gb.hit = true
                    val shielded = g.shieldOn
                    g.hurt("grabber", null)
                    if (shielded) grabsBlocked++ else {
                        grabsHit++
                        if (g.state == GS.PLAY) { p.vy = 5f; p.vz = -1.5f; p.vx = 0f; p.grounded = false; p.jumping = false; g.swipe.stop() }
                    }
                    pop(gb, true); return
                }
                if (u >= 1f) {
                    gb.state = 4; gb.t = 0f
                    if (playing() && gb.minD < 1.15f && p.invuln <= 0f) {
                        grabsDodged++
                        p.dodgeT = 0f; p.dodgeDir = if (p.x >= gb.x) 1f else -1f
                        reward(NARROW, "NARROW ESCAPE!", 0xFF9FE8FF.toInt())
                    }
                }
            }
            4 -> {
                gb.x += gb.vx * hdt; gb.y += gb.vy * hdt; gb.z += gb.vz * hdt
                gb.show = max(0f, 1f - gb.t / GRABBER_PAST)
                if (gb.t >= GRABBER_PAST) gb.on = false
            }
            5 -> { gb.show = max(0f, gb.show - dt * 5f); if (gb.show <= 0f) gb.on = false }
        }
        // one that has fallen far behind or away is simply gone
        if (gb.on && (abs(gb.z - p.z) > 30f || abs(gb.y - p.y) > 25f)) gb.on = false
    }

    /** It dives: at the boy's lane, where his pace will carry him by the time it gets there. */
    private fun dive(gb: Grabber) {
        val p = g.player
        gb.state = 3; gb.t = 0f
        gb.fx = gb.x; gb.fy = gb.y; gb.fz = gb.z
        gb.tx = p.x; gb.ty = p.y + 0.75f; gb.tz = p.z + ahead(GRABBER_DASH)
        gb.vx = (gb.tx - gb.fx) / GRABBER_DASH; gb.vy = (gb.ty - gb.fy) / GRABBER_DASH; gb.vz = (gb.tz - gb.fz) / GRABBER_DASH
        grabberDives++; grabsAttempted++
        val d = len3(p.x - gb.x, p.y - gb.y, p.z - gb.z)
        g.platform.sound(Sfx.WHOOSH, clamp01(1.2f - d / 12f) * 0.8f, 1.4f)
    }

    private fun pop(gb: Grabber, hit: Boolean) {
        gb.state = 5; gb.t = 0f
        g.fx.burst(gb.x, gb.y, gb.z, if (hit) 20 else 12, PK.SPARK, 0xFFD890FF.toInt(), 3.5f, 0.1f, 0.5f)
        g.fx.burst(gb.x, gb.y, gb.z, 8, PK.DUST, 0xFFB890E8.toInt(), 2f, 0.18f, 0.6f)
    }

    // ------------------------------------------------------------------ followers (something is following...)
    private fun updateFollowers(dt: Float) {
        val p = g.player
        for ((i, sp) in trails.withIndex()) {
            if (trailOn[i] || !playing() || p.z < sp.z || p.z > sp.z + sp.len) continue
            trailOn[i] = true
            val n = max(1, min(followers.size, sp.flag))
            for (k in 0 until n) {
                val f = followers[k]
                f.on = true; f.state = 1; f.t = -k * 1.1f; f.show = 0f; f.side = if (k % 2 == 0) -1f else 1f
                f.gap = 3.6f; f.peek = 0f; f.look = k % 2
                followersSeen++
            }
            g.fx.toast("SOMETHING IS FOLLOWING YOU...", "LITTLE MAGIC CREATURES CREEP UP BEHIND", 0xFFD8A8FF.toInt(), 2.6f)
            g.platform.sound(Sfx.WHOOSH, 0.35f, 2f)
        }
        val stretch = trails.firstOrNull { p.z >= it.z - 2f && p.z <= it.z + it.len + 1f }
        for (f in followers) {
            if (!f.on) continue
            f.t += dt
            val r = floor(p.z).toInt()
            val px = g.world.pathXAt(r)
            val lvl = max(g.world.levelAt(r), p.y)
            if (f.state == 1) {
                if (f.t < 0f) continue
                f.show = min(1f, f.show + dt * 1.2f)
                val prog = if (stretch != null) clamp01((p.z - stretch.z) / max(1f, stretch.len)) else 1f
                // they creep closer as the stretch goes on, and now and then one darts in for a peek (and giggles)
                val peekNow = fract(f.t / 3.1f + f.side * 0.27f) < 0.16f
                f.peek = approach(f.peek, if (peekNow) 1f else 0f, dt * 3f)
                if (peekNow && f.peek < 0.05f) {
                    val d = len3(p.x - f.x, p.y - f.y, p.z - f.z)
                    g.platform.sound(Sfx.WHOOSH, clamp01(1.1f - d / 10f) * 0.25f, 2.1f)
                }
                // (at the screen's edges, level with him and a little behind: they peek in from the sides of the view and
                // creep inward, closer to him, as it goes on)
                f.gap = lerp(3f, 1.4f, prog) - 0.6f * f.peek
                f.x = px + f.side * (lerp(2.1f, 1.75f, prog) + 0.12f * sin(f.t * 1.7f)) - f.side * 0.4f * f.peek
                f.y = lvl + 1.55f + 0.2f * sin(f.t * 3.3f + f.side)
                f.z = p.z + 1.2f - f.gap * 0.5f
                // off they go to join the Sorcerer's minions (the ambush), or when the stretch is over
                if (g.minions.anyWave || stretch == null || p.state != PS.NORMAL) { f.state = 2; f.t = 0f; g.platform.sound(Sfx.WHOOSH, 0.3f, 1.8f) }
            } else {
                f.x += f.side * 1.2f * dt; f.y += 3.5f * dt; f.z += (p.vz + 7f) * dt
                f.show = max(0f, f.show - dt * 1.1f)
                if (f.show <= 0f) f.on = false
            }
        }
    }

    // ------------------------------------------------------------------ the boy's attention
    /** How much a threat has the boy's attention and which side it is on (he leans away from it, arm up on its side). */
    private fun updateThreat(dt: Float) {
        val p = g.player
        var best = 0f; var side = 0f
        fun consider(k: Float, s: Float) { if (k > best) { best = k; side = s } }
        for (h in hands) if (h.state == GST.WARN || h.state == GST.REACH || h.state == GST.SNAP) {
            val d = abs(h.spot.z - p.z)
            consider((1f - clamp01((d - 3f) / 10f)) * (if (h.state == GST.WARN) 0.75f else 1f), if (h.guardian) -0.4f else sign(h.hx - p.x))
        }
        for (v in vines) if (v.state == 1 || v.state == 2) { val d = v.z - p.z; if (d > -0.5f && d < 6f) consider(0.6f * (1f - d / 7f), 0f) }
        for (c in chains) { val d = c.pz - p.z; if (d > -0.5f && d < 3f) consider(0.45f, sign(c.sx - p.x)) }
        for (gb in grabbers) if (gb.on && (gb.state == 2 || gb.state == 3)) {
            val d = len3(p.x - gb.x, p.y - gb.y, p.z - gb.z)
            consider(1f - clamp01((d - 1.5f) / 7f), sign(gb.x - p.x))
        }
        // the Sorcerer's imps closing in from behind count too
        if (g.minions.meter > 0.5f) consider((g.minions.meter - 0.5f) * 1.6f, 0f)
        if (!playing()) best = 0f
        p.threat = approach(p.threat, clamp01(best), dt * 4f)
        p.threatSide = approach(p.threatSide, side, dt * 5f)
    }

    // ------------------------------------------------------------------ rewards
    private fun reward(points: Int, label: String, color: Int) {
        val p = g.player
        g.score += points
        g.fx.popupWorld(label, p.x, p.y + 2.55f, p.z, color, 44f, 1.25f)
        g.fx.popupWorld("+$points", p.x, p.y + 1.9f, p.z, 0xFFFFE680.toInt(), 34f, 1.05f)
        g.fx.burst(p.x, p.y + 1.1f, p.z, 14, PK.STAR, color, 3.5f, 0.1f, 0.55f)
        g.platform.sound(Sfx.STAR, 0.75f, 1.35f); g.platform.sound(Sfx.WHOOSH, 0.35f, 1.7f)
        when (points) {
            GUARDIAN -> {
                guardianEscapes++
                g.slowFor(0.5f, 0.35f); g.kick(1.4f); g.flashWhite = max(g.flashWhite, 0.2f)
                g.platform.sound(Sfx.CHECKPOINT, 0.8f, 1.2f); g.platform.haptic(true)
                g.sorcery.flinch = 1f
                if (g.cam.project(p.x, p.y + 1f, p.z)) g.fx.ring(g.cam.sx, g.cam.sy, color, 30f * g.hud.s, 280f * g.hud.s, 0.6f, 12f * g.hud.s)
            }
            PERFECT -> { perfect++; g.slowFor(0.6f, 0.16f) }
            else -> { narrow++; g.slowFor(0.65f, 0.14f) }
        }
    }

    /** Escaping the Sorcerer's last grab: EPIC ESCAPE, then the Celestial Gate is revealed. */
    private fun escapeEpic() {
        val p = g.player
        epic++
        g.score += EPIC
        g.fx.banner("EPIC ESCAPE!", "+$EPIC  •  THE CELESTIAL GATE IS OPEN — RUN!", 0xFFFFE14A.toInt(), 2.2f)
        g.fx.popupWorld("+$EPIC", p.x, p.y + 2.2f, p.z, 0xFFFFE680.toInt(), 44f, 1.2f)
        g.slowFor(0.45f, 0.8f); g.kick(1.8f); g.flashWhite = max(g.flashWhite, 0.2f); g.shake = max(g.shake, 0.3f)
        g.platform.sound(Sfx.WIN, 0.7f, 1.3f); g.platform.sound(Sfx.STAR, 1f, 1.1f); g.platform.haptic(true)
        g.fx.confetti(p.x, p.y + 2.2f, p.z + 0.5f, 40)
        g.fx.burst(p.x, p.y + 1f, p.z, 30, PK.STAR, 0xFFFFE680.toInt(), 6f, 0.16f, 0.9f)
        if (g.cam.project(p.x, p.y + 1f, p.z)) g.fx.ring(g.cam.sx, g.cam.sy, 0xFFFFE680.toInt(), 30f * g.hud.s, 360f * g.hud.s, 0.8f, 14f * g.hud.s)
        g.sorcery.flinch = 1f
        // the destination answers: the gate flares, its light pours over the sky, the camera lifts toward it
        reveal = 1f
        g.sorcery.gateFlare = 1f
        p.celebrateT = 0f
    }

    // ------------------------------------------------------------------ for the autopilot
    /**
     * Spots about to be grabbed: (x, z, from, until, where it comes from (x)) in seconds from now: hands that have struck,
     * grabbers diving.
     */
    fun dangers(out: ArrayList<FloatArray>) {
        out.clear()
        val k = max(0.2f, g.hazK)
        for (h in hands) {
            val reach = if (h.guardian) GUARD_REACH else HAND_REACH
            val snap = if (h.guardian) GUARD_SNAP else HAND_SNAP
            when (h.state) {
                GST.REACH -> out.add(floatArrayOf(h.tx, h.tz, (reach - h.t) / k - 0.05f, (reach - h.t + snap) / k + 0.05f, h.fx))
                GST.SNAP -> out.add(floatArrayOf(h.tx, h.tz, 0f, (snap - h.t) / k + 0.05f, h.fx))
            }
        }
        for (gb in grabbers) if (gb.on && gb.state == 3) out.add(floatArrayOf(gb.tx, gb.tz, 0f, (GRABBER_DASH - gb.t) / k + 0.05f, gb.fx))
    }
}
