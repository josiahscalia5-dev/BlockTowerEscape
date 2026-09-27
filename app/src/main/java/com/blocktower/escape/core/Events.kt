package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Guard {
    var on = false
    var x = 0f; var y = 0f; var z = 0f
    var speed = 0f
    var step = 0f
    var rise = 1f          // 0 = hidden below the tower, 1 = standing on the path
    var falling = false
    var vy = 0f
    var fallT = 0f
    var stun = 0f
    var reach = 0f         // arms reaching forward when close
    var grab = 0f          // capture: hands closed around the boy
    var sortKey = 0f
}

class Rock {
    var on = false
    var x = 0f; var y = 0f; var z = 0f
    var vy = 0f
    var vx = 0f; var vz = 0f       // thrown boulders (Level 5) fly on an arc
    var tx = 0f; var tz = 0f       // where it will land
    var ground = 0f
    var landed = false
    var t = 0f
    var sortKey = 0f
}

/** A stretch of tower crumbling from behind: every tagged block the front passes shakes, then falls. */
class Collapse {
    var on = false
    var z = 0f
    var speed = 0f
    var maxSpeed = 0f
    var accel = 0f
    var endZ = 0f
    val crumbling = ArrayList<Block>()
    fun reset() { on = false; z = 0f; speed = 0f; crumbling.clear() }
}

object Chase { const val NONE = 0; const val WARNING = 1; const val REVEAL = 2; const val RUN = 3; const val ESCAPED = 4; const val CAUGHT = 5 }

/** Adventure events: Tower Guard chase (with the tower collapsing behind), rising lava, final escape, debris, magical wind. */
class Events(val g: Game) {
    companion object {
        /** Magical wind: a gust every 3 s: 0.8 s of warning streaks, 1.3 s of push, then calm. */
        const val GUST_PERIOD = 3.0f; const val WARN_T = 0.8f; const val GUST_T = 1.3f
        /** Strongest sideways push (units per second): steering (a swipe or the stick) beats it easily. */
        const val WIND_PUSH = 1.1f
    }
    private val rng = Rng(99)

    // ---- Tower Guard chase
    var chase = Chase.NONE
    var phaseT = 0f
    var chaseT = 0f
    val guard = Guard()
    val chaseCollapse = Collapse()
    val chaseActive get() = chase == Chase.RUN
    /** Level 23 stops the boy while the camera turns around; in Level 5 the Guardian appears ahead and he keeps running. */
    val revealing get() = chase == Chase.REVEAL && !g.spec.jungle
    /** 0 = normal follow camera, 1 = camera turned around (guard reveal / capture). */
    var camBlend = 0f

    // ---- final escape
    var finalOn = false
    var finalT = 0f
    val finalCollapse = Collapse()

    // ---- lava
    var lavaOn = false
    var lavaStop = false
    var lavaY = -100f
    private var lavaSpeed = 0f

    // ---- magical wind: gusts from alternating sides, each announced by streaks of wind before it pushes
    var windOn = false
    private var windT = 0f
    /** Which way the next / current gust blows (-1 left, 1 right). */
    var windDir = 1f
    /** 0..1: how hard the gust is pushing right now. */
    var gust = 0f
    /** 0..1: the warning streaks before a gust, and the gust itself (for the screen effect). */
    var windShow = 0f
    /** Sideways push the wind puts on the steering target (units per second). */
    val windPush get() = windDir * gust * WIND_PUSH

    // ---- debris
    val rocks = Array(6) { Rock() }
    private var rockT = 0f

    // ---- Level 5: the Temple Guardian watching from its ruins before the chase
    /** 1 while it watches; fades out as it leaves. */
    var lairShow = 0f
    /** 0 watching .. 1 gone (leapt back into the jungle). */
    var lairLeave = 0f
    private var lairState = 0      // 0 none, 1 watching, 2 leaving, 3 gone

    // ---- danger meter (guard / collapse / lava)
    var meter = 0f
    var meterShow = 0f
    var meterLabel = ""
    var meterKind = 0

    fun reset() {
        resetChase(); resetFinal(); resetLava(); resetWind()
        meter = 0f; meterShow = 0f; camBlend = 0f
        val watching = g.world.guardianLair != null
        lairState = if (watching) 1 else 0; lairShow = if (watching) 1f else 0f; lairLeave = 0f
    }

    private fun resetChase() {
        chase = Chase.NONE; phaseT = 0f; chaseT = 0f
        guard.on = false; guard.falling = false; guard.stun = 0f; guard.grab = 0f; guard.reach = 0f
        chaseCollapse.reset()
        for (r in rocks) r.on = false
    }
    private fun resetFinal() { finalOn = false; finalT = 0f; finalCollapse.reset() }
    private fun resetLava() { lavaOn = false; lavaStop = false; lavaY = -100f; lavaSpeed = 0f }
    private fun resetWind() { windOn = false; windT = 0f; gust = 0f; windShow = 0f; windDir = 1f }

    private fun triggerZ(e: Int): Float = g.world.triggers.firstOrNull { it.event == e }?.z ?: 9999f

    /** Called when the player continues from a checkpoint: events after it start over. */
    fun resetAfter(cpZ: Float, cpY: Float) {
        if (triggerZ(Ev.CHASE) > cpZ) resetChase()
        else if (chase != Chase.ESCAPED) { resetChase(); chase = Chase.ESCAPED }
        if (triggerZ(Ev.FINAL) > cpZ) resetFinal()
        if (triggerZ(Ev.LAVA) > cpZ) resetLava() else if (lavaOn) lavaY = min(lavaY, cpY - 5f)
        if (triggerZ(Ev.WIND) > cpZ || triggerZ(Ev.WIND_STOP) <= cpZ) resetWind() else { windT = 0f; gust = 0f }
        camBlend = 0f
        val lair = g.world.guardianLair
        if (lair != null && cpZ > lair[2] - 7f) { lairState = 3; lairShow = 0f; lairLeave = 1f }
    }

    /** Called when a fall-recovery ride puts the player back on the course at (x, y, z). */
    fun onRecovered(x: Float, y: Float, z: Float) {
        if (chase == Chase.RUN && g.spec.jungle) { meter = max(0f, meter - 0.3f); guard.x = min(guard.x, -5f) }
        else if (chase == Chase.RUN && guard.z > z - 7.5f) {
            guard.z = z - 7.5f; guard.stun = 0.8f
            guard.y = g.world.levelAt(floor(guard.z).toInt())
        }
        if (lavaOn) lavaY = min(lavaY, y - 4.5f)
        if (finalOn && finalCollapse.z > z - 5f) finalCollapse.z = z - 5f
        for (r in rocks) r.on = false
    }

    fun fire(e: Int) {
        val p = g.player
        when (e) {
            Ev.CHASE -> if (chase == Chase.NONE) {
                chase = Chase.WARNING; phaseT = 0f
                // first the warning, then who is coming
                g.fx.banner("DANGER!", "", 0xFFFF5A4A.toInt(), 1.15f, true)
                g.fx.bannerThen(g.spec.guardName, "APPROACHING!", 0xFFFF5A4A.toInt(), 1.7f, true)
                g.platform.sound(Sfx.WARNING); g.platform.haptic(true)
                guard.on = true; guard.falling = false; guard.stun = 0f; guard.grab = 0f
                guard.z = p.z - 9f; guard.x = 0f; guard.y = g.world.levelAt(floor(guard.z).toInt())
                guard.step = 0f; guard.rise = 0f
                if (g.spec.jungle) {
                    // the Temple Guardian comes out of the ruins ahead on the left, as in the design
                    guard.x = -4.8f; guard.z = p.z + 10f; guard.y = g.world.levelAt(floor(guard.z).toInt()); meter = 0f
                }
                for (r in rocks) r.on = false
                rockT = 3f
            }
            Ev.CHASE_END -> if (chase == Chase.RUN || chase == Chase.REVEAL || chase == Chase.WARNING) {
                chase = Chase.ESCAPED; phaseT = 0f; camBlend = 0f
                // the tower gives way under the guard
                chaseCollapse.on = true; chaseCollapse.speed = 16f; chaseCollapse.maxSpeed = 16f
                g.fx.banner("CHASE COMPLETE!", if (g.spec.jungle) "THE GUARDIAN FELL BEHIND" else "THE GUARD FELL BEHIND", 0xFF7FFFA0.toInt(), 2.2f)
                g.addScore(500, p.x, p.y + 2.6f, p.z, "ESCAPE BONUS")
                g.platform.sound(Sfx.CHECKPOINT, 1f, 1.1f)
                g.fx.confetti(p.x, p.y + 2.2f, p.z + 0.5f, 40)
            }
            Ev.LAVA -> if (!lavaOn) {
                lavaOn = true; lavaStop = false
                lavaY = g.world.levelAt(floor(p.z).toInt()) - 5.5f
                lavaSpeed = 0.4f
                g.fx.banner("LAVA RISING!", "CLIMB THE FINAL TOWER!", 0xFFFF6A2A.toInt(), 2.3f, true)
                g.platform.sound(Sfx.WARNING, 0.8f)
                g.shake = max(g.shake, 0.35f)
            }
            Ev.LAVA_STOP -> lavaStop = true
            Ev.WIND -> if (!windOn) {
                windOn = true; windT = 0f; gust = 0f; windDir = 1f
                g.fx.banner("MAGICAL WIND!", "GUSTS PUSH YOU SIDEWAYS — STEER AGAINST THEM", 0xFF9FE8FF.toInt(), 2.4f, true)
                g.platform.sound(Sfx.WHOOSH, 0.9f, 0.7f); g.platform.sound(Sfx.WARNING, 0.5f, 1.3f)
            }
            Ev.WIND_STOP -> if (windOn) { windOn = false; g.fx.toast("THE WIND DIES DOWN", "", 0xFF9FE8FF.toInt(), 1.6f) }
            Ev.FINAL -> if (!finalOn) {
                finalOn = true; finalT = 0f
                val c = finalCollapse
                c.on = true; c.z = p.z - 6f; c.speed = 3.0f; c.maxSpeed = 5.0f; c.accel = 0.4f
                c.endZ = g.world.finalSafeZ - 0.5f
                g.fx.banner("FINAL ESCAPE!", "THE TOWER IS FALLING — RUN TO THE GATE!", 0xFFFFB04A.toInt(), 2.4f, true)
                g.platform.sound(Sfx.CRUMBLE); g.platform.sound(Sfx.WARNING, 0.7f); g.platform.haptic(true)
                g.shake = max(g.shake, 0.6f)
            }
        }
    }

    private fun p0() = g.player

    /** A gust every [GUST_PERIOD] seconds: streaks of wind first (the warning), then the push, then calm. */
    private fun updateWind(dt: Float) {
        if (!windOn) { gust = approach(gust, 0f, dt * 3f); windShow = approach(windShow, 0f, dt * 2f); return }
        if (g.state != GS.PLAY) return
        val before = windT % GUST_PERIOD
        windT += dt
        val c = windT % GUST_PERIOD
        if (c < before) windDir = -windDir                                   // a new gust, from the other side
        if (before < WARN_T && c >= WARN_T) { g.platform.sound(Sfx.WHOOSH, 0.8f, if (windDir > 0f) 0.9f else 0.8f); g.platform.haptic(false) }
        val target = when {
            c < WARN_T -> 0f
            c < WARN_T + GUST_T -> 1f
            else -> 0f
        }
        gust = approach(gust, target, dt * (if (target > gust) 2.5f else 1.8f))
        windShow = approach(windShow, if (c < WARN_T + GUST_T) 1f else 0.25f, dt * 3f)
    }

    fun update(dt: Float) {
        updateWind(dt)
        updateChase(dt)
        updateLair(dt)
        updateCollapse(chaseCollapse, dt, if (chase == Chase.RUN) (if (g.spec.jungle) p0().z - (8.5f - 4.5f * meter) else guard.z - 2.6f) else null)
        if (finalOn) {
            finalT += dt
            if (g.state == GS.PLAY && g.player.state == PS.NORMAL) updateCollapse(finalCollapse, dt, null)
        }
        updateLava(dt)
        val p = g.player
        val want = when {
            chase == Chase.RUN || chase == Chase.CAUGHT || (chase == Chase.REVEAL && phaseT > 1.8f) -> { meterKind = 0; meterLabel = g.spec.guardName; 1f }
            finalOn && finalCollapse.z < finalCollapse.endZ - 0.5f && g.state == GS.PLAY -> {
                meterKind = 1; meterLabel = "COLLAPSE"; meter = 1f - clamp01((p.z - finalCollapse.z - 1f) / 9f); 1f
            }
            lavaOn && !lavaStop && g.state == GS.PLAY -> { meterKind = 2; meterLabel = "LAVA"; meter = 1f - clamp01((p.y - lavaY - 0.8f) / 6f); 1f }
            else -> 0f
        }
        meterShow = approach(meterShow, want, dt * 3f)
    }

    // ------------------------------------------------------------------ Level 5: the watching guardian
    private fun updateLair(dt: Float) {
        val lair = g.world.guardianLair ?: return
        val p = g.player
        if (lairState == 1 && g.state == GS.PLAY && p.z > lair[2] - 7f) {
            // it has seen you: a roar, then it leaps back into the jungle (it will be back...)
            lairState = 2
            g.platform.sound(Sfx.ROAR, 0.7f, 1.05f); g.shake = max(g.shake, 0.3f)
            g.fx.toast("THE GUARDIAN IS WATCHING...", "KEEP CLIMBING!", 0xFFFFB04A.toInt(), 2.2f)
        }
        if (lairState == 2) {
            lairLeave = min(1f, lairLeave + dt / 1.3f)
            lairShow = 1f - smooth((lairLeave - 0.45f) / 0.55f)
            if (lairLeave >= 1f) lairState = 3
        }
    }

    // ------------------------------------------------------------------ chase
    private fun updateChase(dt: Float) {
        val p = g.player
        if (chase == Chase.NONE) return
        phaseT += dt
        when (chase) {
            Chase.WARNING -> {
                g.rumble = max(g.rumble, 0.18f + 0.1f * phaseT)
                if (g.spec.jungle) keepAhead(dt, 10f)
                if (phaseT > 1.1f) {
                    chase = Chase.REVEAL; phaseT = 0f
                    if (!g.spec.jungle) { guard.z = p.z - 8f; guard.y = g.world.levelAt(floor(guard.z).toInt()) }
                }
            }
            Chase.REVEAL -> if (g.spec.jungle) revealAhead(dt) else {
                val before = phaseT - dt
                camBlend = if (phaseT < 2.0f) smooth(phaseT / 0.45f) else 1f - smooth((phaseT - 2.0f) / 0.45f)
                guard.x = lerp(guard.x, 0f, damp(4f, dt))
                if (phaseT > 0.35f) guard.rise = easeOutCubic((phaseT - 0.35f) / 0.8f)
                if (phaseT in 0.35f..1.2f) {
                    g.fx.dust(guard.x + rng.f(-2.2f, 2.2f), guard.y, guard.z + rng.f(-1f, 1f), 1, 0xFFC8A080.toInt())
                    g.rumble = max(g.rumble, 0.4f)
                }
                if (before < 1.05f && phaseT >= 1.05f) {
                    g.platform.sound(Sfx.ROAR); g.shake = max(g.shake, 0.75f); g.platform.haptic(true)
                    g.fx.burst(guard.x, guard.y + 0.2f, guard.z, 26, PK.DUST, 0xFFC8A080.toInt(), 5f, 0.4f, 0.9f)
                }
                if (phaseT >= 2.45f) {
                    chase = Chase.RUN; phaseT = 0f; chaseT = 0f; camBlend = 0f; guard.rise = 1f
                    chaseCollapse.on = true; chaseCollapse.z = guard.z - 2.6f; chaseCollapse.speed = 0f
                    chaseCollapse.endZ = (g.world.triggers.firstOrNull { it.event == Ev.CHASE_END }?.z ?: (p.z + 40f)) - 2.5f
                    g.fx.banner("RUN!", "", 0xFFFFE14A.toInt(), 1.1f)
                    g.platform.sound(Sfx.GO, 0.9f, 0.9f)
                }
            }
            Chase.RUN -> if (g.spec.jungle) runChaseAhead(dt) else runChase(dt)
            Chase.ESCAPED -> {
                if (g.spec.jungle && guard.on && !guard.falling) {
                    // its ledge gives way: the Guardian tumbles into the jungle below
                    guard.falling = true; guard.vy = 2f; guard.fallT = 0f
                    g.platform.sound(Sfx.ROAR, 0.9f, 0.8f); g.platform.sound(Sfx.CRUMBLE)
                    g.shake = max(g.shake, 0.45f)
                    g.fx.dust(guard.x, guard.y, guard.z, 16, 0xFFC8A080.toInt())
                }
                if (guard.on && !guard.falling && chaseCollapse.z > guard.z - 0.5f) {
                    guard.falling = true; guard.vy = 3f; guard.fallT = 0f
                    g.platform.sound(Sfx.ROAR, 0.9f, 0.8f); g.platform.sound(Sfx.CRUMBLE)
                    g.shake = max(g.shake, 0.45f)
                    g.fx.dust(guard.x, guard.y, guard.z, 16, 0xFFC8A080.toInt())
                }
                if (guard.falling) {
                    guard.fallT += dt; guard.vy -= 18f * dt; guard.y += guard.vy * dt
                    if (guard.fallT > 3f) guard.on = false
                }
            }
            Chase.CAUGHT -> if (g.spec.jungle) caughtAhead(dt) else {
                guard.grab = min(1f, guard.grab + dt * 2.5f)
                guard.reach = 1f
                camBlend = smooth(phaseT / 0.7f)
                // the guard lifts the boy up in front of its face
                val hx = guard.x; val hy = guard.y + 3.05f; val hz = guard.z + 1.35f
                val k = damp(6f, dt)
                p.x = lerp(p.x, hx, k); p.y = lerp(p.y, hy, k); p.z = lerp(p.z, hz, k)
                p.vx = 0f; p.vy = 0f; p.vz = 0f
                if (phaseT in 0.2f..0.3f) g.rumble = max(g.rumble, 0.6f)
                if (phaseT > 2.4f) g.fail("CAUGHT BY THE ${g.spec.guardName}!")
            }
        }
        if (guard.on && (chase == Chase.RUN || chase == Chase.CAUGHT || chase == Chase.REVEAL)) updateRocks(dt)
    }

    // ---- Level 5: the Temple Guardian bounds along the ruins ahead on the left and closes in on the path
    private val guardFar = -4.3f
    private val guardNear = -1.5f

    /** Keeps the Guardian [ahead] units in front of the boy, on the ruins beside the path. */
    private fun keepAhead(dt: Float, ahead: Float) {
        val p = g.player
        val want = p.z + ahead
        val before = guard.z
        guard.z = lerp(guard.z, want, damp(2.2f, dt))
        guard.y = lerp(guard.y, g.world.levelAt(floor(guard.z).toInt()), damp(3f, dt))
        guard.step += abs(guard.z - before) * 0.45f
    }

    private fun revealAhead(dt: Float) {
        val before = phaseT - dt
        keepAhead(dt, 10f)
        guard.rise = easeOutCubic(phaseT / 0.9f)
        if (phaseT < 0.9f) { g.rumble = max(g.rumble, 0.35f); if (rng.f() < dt * 20f) g.fx.dust(guard.x + rng.f(-2f, 2f), guard.y, guard.z, 1, 0xFFC8A080.toInt()) }
        if (before < 0.8f && phaseT >= 0.8f) {
            g.platform.sound(Sfx.ROAR); g.shake = max(g.shake, 0.6f); g.platform.haptic(true)
            g.fx.burst(guard.x, guard.y + 0.2f, guard.z, 22, PK.DUST, 0xFFC8A080.toInt(), 5f, 0.4f, 0.9f)
        }
        if (phaseT >= 1.6f) {
            chase = Chase.RUN; phaseT = 0f; chaseT = 0f; guard.rise = 1f
            chaseCollapse.on = true; chaseCollapse.z = g.player.z - 9f; chaseCollapse.speed = 0f
            chaseCollapse.endZ = (g.world.triggers.firstOrNull { it.event == Ev.CHASE_END }?.z ?: (g.player.z + 40f)) - 2.5f
            g.fx.banner("RUN!", "", 0xFFFFE14A.toInt(), 1.1f)
            g.platform.sound(Sfx.GO, 0.9f, 0.9f)
            rockT = 1.6f
        }
    }

    /**
     * The chase in the temple: the Guardian keeps pace a few blocks ahead on the ruins to the left and
     * edges toward the path whenever the boy slows down (it backs off while he keeps running). When it
     * reaches the path it lunges and grabs him. It throws boulders onto the path ahead as it goes.
     */
    private fun runChaseAhead(dt: Float) {
        val p = g.player
        if (g.state != GS.PLAY || p.state != PS.NORMAL) return
        chaseT += dt
        val sp = len2(p.vx, p.vz)
        val rate = when {
            guard.stun > 0f -> -0.2f
            sp < 1.5f -> 0.30f                  // standing still: it comes for you
            sp < 3.8f -> 0.12f
            else -> 0.035f - 0.02f * clamp01((sp - 4.5f) / 3f)   // running: it barely keeps up
        }
        if (guard.stun > 0f) guard.stun -= dt
        meter = clamp01(meter + rate * dt)
        keepAhead(dt, lerp(9.5f, 4.5f, meter))
        guard.x = lerp(guard.x, lerp(guardFar, guardNear, meter), damp(2.5f, dt))
        val stepBefore = guard.step
        if (floor(stepBefore) != floor(guard.step)) {
            g.platform.sound(Sfx.STOMP, 0.3f + 0.6f * meter)
            g.shake = max(g.shake, 0.1f + 0.25f * meter)
            if (meter > 0.6f) g.platform.haptic(false)
        }
        g.rumble = max(g.rumble, 0.08f + 0.12f * meter)
        guard.reach = approach(guard.reach, if (meter > 0.75f) 1f else 0f, dt * 3f)
        if (meter >= 1f) {
            if (g.shieldOn) {
                g.breakShield()
                meter = 0.35f; guard.x = guardFar; guard.stun = 1.2f
                g.fx.popupWorld("BLOCKED!", p.x, p.y + 2.3f, p.z, 0xFF9FE8FF.toInt(), 46f)
                g.platform.sound(Sfx.ROAR, 0.8f, 1.2f)
                g.shake = max(g.shake, 0.5f)
            } else {
                chase = Chase.CAUGHT; phaseT = 0f
                g.beginCapture()
            }
        }
    }

    /** It lunges onto the path in front of the boy and lifts him up. */
    private fun caughtAhead(dt: Float) {
        val p = g.player
        guard.grab = min(1f, guard.grab + dt * 2.5f)
        guard.reach = 1f
        val k = damp(7f, dt)
        guard.x = lerp(guard.x, p.x, k); guard.z = lerp(guard.z, p.z + 1.6f, k)
        guard.y = lerp(guard.y, g.world.levelAt(floor(guard.z).toInt()), k)
        if (phaseT > 0.3f) {
            val kk = damp(5f, dt)
            p.x = lerp(p.x, guard.x, kk); p.y = lerp(p.y, guard.y + 1.9f, kk); p.z = lerp(p.z, guard.z - 0.8f, kk)
        }
        p.vx = 0f; p.vy = 0f; p.vz = 0f
        if (phaseT in 0.2f..0.3f) g.rumble = max(g.rumble, 0.6f)
        if (phaseT > 2.4f) g.fail("CAUGHT BY THE ${g.spec.guardName}!")
    }

    private fun runChase(dt: Float) {
        val p = g.player
        if (g.state != GS.PLAY || p.state != PS.NORMAL) return
        chaseT += dt
        val dist = p.z - guard.z
        var sp = min(5.0f, 4.3f + chaseT * 0.05f)
        if (dist > 9.5f) sp = 6.4f          // never lose sight of it
        else if (dist < 3.2f) sp = 4.1f     // it looms right behind you, but gives you a chance
        if (guard.stun > 0f) { guard.stun -= dt; sp = 0f }
        guard.speed = sp
        guard.z += sp * dt
        guard.x = lerp(guard.x, clamp(p.x, -1.2f, 1.2f), damp(1.5f, dt))
        guard.y = lerp(guard.y, g.world.levelAt(floor(guard.z).toInt()), damp(3f, dt))
        val stepBefore = guard.step
        guard.step += dt * max(sp, 1f) * 0.75f
        val close = 1f - clamp01((dist - 2f) / 10f)
        if (floor(stepBefore) != floor(guard.step)) {
            g.platform.sound(Sfx.STOMP, 0.3f + 0.7f * close)
            g.shake = max(g.shake, 0.14f + 0.3f * close)
            if (close > 0.6f) g.platform.haptic(false)
        }
        g.rumble = max(g.rumble, 0.1f + 0.12f * close)
        guard.reach = approach(guard.reach, if (dist < 4.5f) 1f else 0f, dt * 3f)
        meter = 1f - clamp01((dist - 1.35f) / 9f)
        if (dist < 1.35f) {
            if (g.shieldOn) {
                g.breakShield()
                guard.z = p.z - 6.5f; guard.stun = 1.2f
                g.fx.popupWorld("BLOCKED!", p.x, p.y + 2.3f, p.z, 0xFF9FE8FF.toInt(), 46f)
                g.platform.sound(Sfx.ROAR, 0.8f, 1.2f)
                g.shake = max(g.shake, 0.5f)
            } else {
                chase = Chase.CAUGHT; phaseT = 0f
                g.beginCapture()
            }
        }
    }

    private fun updateRocks(dt: Float) {
        val p = g.player
        if (chase == Chase.RUN && g.state == GS.PLAY && p.state == PS.NORMAL) {
            rockT -= dt
            if (rockT <= 0f) {
                rockT = rng.f(1.6f, 2.5f)
                val row = floor(p.z + rng.f(3f, 6.5f)).toInt()
                val lane = (rng.i(3) - 1).toFloat()
                val ground = g.world.levelAt(row)
                // only drop debris where there is floor to land on
                val floorHere = g.world.row(row)?.any { it.collides() && abs(it.x - lane) < 0.5f && abs(it.y1 - ground) < 0.3f } == true
                if (floorHere) for (r in rocks) if (!r.on) {
                    r.on = true; r.landed = false; r.t = 0f
                    r.x = lane; r.z = row + 0.5f; r.ground = ground
                    r.y = r.ground + 11f; r.vy = -2f; r.vx = 0f; r.vz = 0f
                    r.tx = r.x; r.tz = r.z
                    if (g.spec.jungle) {
                        // the Guardian hurls it from its raised hand in a high arc onto the path
                        val T = 1.0f
                        val x0 = guard.x + 1.2f; val y0 = guard.y + 3.4f; val z0 = guard.z - 0.3f
                        r.x = x0; r.y = y0; r.z = z0
                        r.vx = (r.tx - x0) / T; r.vz = (r.tz - z0) / T; r.vy = (ground - y0) / T + 12f * T
                        guard.reach = 1f
                        g.platform.sound(Sfx.WHOOSH, 0.6f, 0.7f)
                    }
                    break
                }
            }
        }
        for (r in rocks) if (r.on) {
            if (!r.landed) {
                r.vy -= 24f * dt; r.y += r.vy * dt; r.x += r.vx * dt; r.z += r.vz * dt
                if (r.y <= r.ground && r.vy < 0f) {
                    r.y = r.ground; r.x = r.tx; r.z = r.tz; r.landed = true; r.t = 0f
                    g.fx.dust(r.x, r.ground, r.z, 8, 0xFFC8A080.toInt())
                    g.fx.shards(Block(r.x, r.ground, r.z - 0.5f, BC.BRICK), BC.base(BC.BRICK), 6)
                    val d = len3(p.x - r.x, p.y - r.ground, p.z - r.z)
                    g.platform.sound(Sfx.CRUMBLE, clamp01(1.1f - d / 10f))
                    g.shake = max(g.shake, 0.3f * clamp01(1.2f - d / 8f))
                    if (abs(p.x - r.x) < 0.8f && abs(p.z - r.z) < 0.8f && p.y < r.ground + 1.4f && p.y > r.ground - 1f) g.hurt("debris", null)
                }
            } else { r.t += dt; if (r.t > 0.15f) r.on = false }
        }
    }

    /** Turns the camera around toward the guard during the reveal and the capture. */
    fun applyCamera(c: Game) {
        if (camBlend <= 0.001f) return
        val p = c.player
        val cam = c.cam
        val tx: Float; val ty: Float; val tz: Float; val pitch: Float
        if (chase == Chase.CAUGHT) {
            tx = guard.x * 0.6f; ty = p.y + 1.1f; tz = p.z + 3.6f; pitch = 0.1f
        } else {
            tx = guard.x * 0.5f; ty = guard.y + 3.4f; tz = p.z - 2.2f; pitch = 0.16f
        }
        val u = camBlend
        cam.ex = lerp(cam.ex, tx, u); cam.ey = lerp(cam.ey, ty, u); cam.ez = lerp(cam.ez, tz, u)
        cam.yaw = lerp(cam.yaw, Math.PI.toFloat(), u)
        cam.pitch = lerp(cam.pitch, pitch, u)
        // a level framed with the lens centre low on screen looks back through the middle of the screen
        if (c.spec.camCy > 0f) cam.cy = lerp(cam.cy, c.hud.h * 0.5f, u)
        cam.roll *= 1f - u
    }

    // ------------------------------------------------------------------ collapse
    private fun updateCollapse(c: Collapse, dt: Float, followZ: Float?) {
        if (!c.on) return
        if (followZ != null) c.z = max(c.z, followZ)
        else { c.speed = min(c.maxSpeed, c.speed + dt * c.accel); c.z += c.speed * dt }
        if (c.z > c.endZ) c.z = c.endZ
        val r0 = floor(c.z).toInt() - 1
        for (r in r0 - 1..r0 + 1) {
            val row = g.world.row(r) ?: continue
            for (b in row) if (b.eventTag == Ev.COLLAPSE && !b.destroyed && b.z < c.z && !c.crumbling.contains(b)) {
                c.crumbling.add(b); b.respawn = 0.35f; b.shake = 0.35f
            }
        }
        val it = c.crumbling.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.respawn -= dt
            b.shake = max(b.shake, 0.05f)
            if (b.respawn <= 0f) { b.destroyed = true; b.vy = rng.f(-1f, 1f); it.remove() }
        }
        if (c.z < c.endZ - 0.01f) {
            val p = g.player
            val near = 1f - clamp01((p.z - c.z) / 14f)
            g.rumble = max(g.rumble, 0.08f + 0.2f * near)
            if (rng.f() < dt * 12f) g.fx.dust(rng.f(-1.6f, 1.6f), g.world.levelAt(floor(c.z).toInt()), c.z, 2, 0xFFC8A080.toInt())
            if (rng.f() < dt * 3f) g.platform.sound(Sfx.CRUMBLE, 0.25f + 0.4f * near, rng.f(0.8f, 1.2f))
        }
    }

    // ------------------------------------------------------------------ lava
    private fun updateLava(dt: Float) {
        if (!lavaOn) return
        val p = g.player
        if (lavaStop) {
            // you made it above the flood: the lava drains away and the sky opens up again
            lavaSpeed = min(6f, lavaSpeed + dt * 3f)
            lavaY -= lavaSpeed * dt
            if (lavaY < p.y - 45f) { lavaOn = false; lavaY = -100f }
            return
        }
        if (g.state == GS.PLAY && p.state == PS.NORMAL) {
            if (!lavaStop) { lavaSpeed = min(0.9f, lavaSpeed + dt * 0.03f); lavaY += lavaSpeed * dt }
            if (p.y < lavaY + 0.05f) g.startRescue("lava")
            val near = 1f - clamp01((p.y - lavaY) / 5f)
            if (!lavaStop) g.rumble = max(g.rumble, 0.05f + 0.1f * near)
        }
        if (rng.f() < dt * 12f) {
            val q = g.fx.spawn()
            q.x = p.x + rng.f(-7f, 7f); q.y = lavaY + 0.1f; q.z = p.z + rng.f(0f, 16f)
            q.vx = rng.f(-0.3f, 0.3f); q.vy = rng.f(1.5f, 3.5f); q.vz = 0f
            q.life = rng.f(0.8f, 1.6f); q.maxLife = q.life; q.size = rng.f(0.06f, 0.14f); q.color = 0xFFFFB040.toInt(); q.kind = PK.EMBER
        }
    }
}
