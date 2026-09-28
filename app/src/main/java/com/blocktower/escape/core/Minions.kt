package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A minion's state. */
object MS { const val HIDDEN = 0; const val APPEAR = 1; const val ACTIVE = 2; const val STOMPED = 3; const val VANISH = 4; const val LUNGE = 5 }

/** One of the Sorcerer's minions (Level 7): a patroller or swooper waiting on the course ([spot]), or a chaser or bomber. */
class Minion(val kind: Int, val spot: MinionSpot?, val wave: Int) {
    var state = MS.HIDDEN
    var t = 0f
    var x = 0f; var y = 0f; var z = 0f
    /** 0..1: fades in as it appears, out as it vanishes. */
    var show = 0f
    /** Which way it faces on screen (-1 left, 1 right): the sprite is mirrored to match. */
    var face = 1f
    /** Chasers and bombers: the side of the path they fly on (-1 left, 1 right). */
    var side = 1f
    /** Chasers: how far behind the boy (units along the course); bombers: seconds to the next bolt. */
    var gap = 8f
    var cool = 0f
    /** A lunge: where it started and where it strikes. */
    var lx = 0f; var ly = 0f; var lz = 0f; var tx = 0f; var ty = 0f; var tz = 0f
    /** Swoopers: 0..1 warning glow before a pass, and whether it is crossing the path now. */
    var warn = 0f
    var crossing = false
    /** Seconds until a swooper's next pass (0 while crossing). */
    var untilCross = 0f
    /** Which picture (0 the witch-hat imp, 1 the purple-eyed imp). */
    var look = 0
    var sortKey = 0f
}

/** A magic bolt a lantern-ship imp drops onto the path: it flies in an arc to a marked spot. */
class Bolt {
    var on = false
    var x = 0f; var y = 0f; var z = 0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var tx = 0f; var tz = 0f; var ground = 0f
    var t = 0f
    var landed = false
    var sortKey = 0f
}

/**
 * The Sorcerer's minions (Level 7), a different threat from the single Guardian of Levels 4-6: groups of small magical
 * imps that put the boy under pressure for a stretch of the course and then vanish at a checkpoint.
 *
 * A wave starts at an [Ev.MINIONS] trigger: the Sorcerer in the sky raises his claw, his minions fly down from the sky
 * and the banner names the wave. Patrollers drop onto the path ahead as the boy comes near and walk back and forth
 * across it; swoopers cross a row in a steady rhythm (they glow before each pass); lantern-ship bombers fly beside the
 * path and drop magic bolts onto it ahead (a glowing ring marks where each lands, a second before it does); chasers
 * fly after him from behind, close in when he slows down and lunge when they are close (the eyes flare first).
 *
 * Every threat is announced and can be dodged, jumped or out-run; touching a minion costs a heart like any hazard (the
 * shield blocks one and zaps it), jumping onto a patroller or a swooper stomps it. Nothing traps the boy and nothing
 * fails the level by itself: only running out of hearts does, as everywhere. At the wave's checkpoint
 * ([Ev.MINIONS_END]) the minions vanish in a puff of purple smoke.
 */
class MinionSystem(val g: Game) {
    companion object {
        /** Chasers: the pace they fly after the boy at (a full run of 5.2 slowly leaves them behind). */
        const val CHASE_SPEED = 4.3f
        /** Chasers lunge from this close, after a warning of [WINDUP] seconds, and are thrown back to [RECOIL] after. */
        const val LUNGE_AT = 1.5f; const val WINDUP = 0.5f; const val DASH = 0.28f; const val RECOIL = 6f
        /** Bombers: seconds between bolts, and a bolt's flight (its landing spot is marked all that time). */
        const val BOLT_EVERY = 2.5f; const val BOLT_FLIGHT = 1.05f
        /** Swoopers: one pass across the path takes this long; it glows this long before each pass. */
        const val PASS = 1.1f; const val WARN = 0.65f
    }

    private val rng = Rng(707)
    val all = ArrayList<Minion>()
    val bolts = Array(6) { Bolt() }
    private val waveOn = HashSet<Int>()
    private val waveDone = HashSet<Int>()
    /** Stomped minions (a player stat for the playtest report). */
    var stomps = 0
    /** 1 -> 0 flash of the Sorcerer casting (a wave begins); how far the sky flock has flown down to the course (0..1). */
    var cast = 0f
    var skyAway = 0f
    /** 0..1: the Sorcerer withdrawing into the mist once the last wave is over (the final ascent is the boy's). */
    var retreat = 0f
    /** The chasers' danger (0..1) for the meter; true while chasers are after the boy. */
    var meter = 0f
    val pursuit get() = all.any { it.kind == MK.CHASER && (it.state == MS.ACTIVE || it.state == MS.LUNGE || it.state == MS.APPEAR) }
    val anyWave get() = waveOn.isNotEmpty()
    val lastWaveDone get() = g.world.waves.isNotEmpty() && waveDone.size >= g.world.waves.size

    fun reset() {
        all.clear(); waveOn.clear(); waveDone.clear()
        for ((i, sp) in g.world.minionSpots.withIndex()) all.add(Minion(sp.kind, sp, sp.wave).also { it.look = i % 2; place(it) })
        for (b in bolts) b.on = false
        cast = 0f; skyAway = 0f; retreat = 0f; meter = 0f; stomps = 0
    }

    private fun waveStartZ(id: Int) = g.world.triggers.firstOrNull { it.event == Ev.MINIONS && it.text2 == id.toString() }?.z ?: 9999f
    private fun waveEndZ(id: Int) = g.world.triggers.firstOrNull { it.event == Ev.MINIONS_END && it.text2 == id.toString() }?.z ?: 9999f

    /** Continuing from a checkpoint: waves that start after it are put back; one it is inside of starts over. */
    fun resetAfter(cpZ: Float) {
        for (b in bolts) b.on = false
        for (wv in g.world.waves) {
            val s = waveStartZ(wv.id); val e = waveEndZ(wv.id)
            if (s > cpZ || e > cpZ) {
                waveOn.remove(wv.id); waveDone.remove(wv.id)
                all.removeAll { it.wave == wv.id && it.spot == null }
                for (m in all) if (m.wave == wv.id) { m.state = MS.HIDDEN; m.show = 0f; place(m) }
            }
        }
        if (!lastWaveDone) retreat = 0f
        meter = 0f
    }

    /** A fall-recovery ride put the boy back on the course: the chasers drop back and bolts in the air fizzle out. */
    fun onRecovered() {
        for (m in all) if (m.kind == MK.CHASER && (m.state == MS.ACTIVE || m.state == MS.LUNGE)) { m.state = MS.ACTIVE; m.gap = max(m.gap, RECOIL + 1f); m.cool = 1.5f }
        for (b in bolts) b.on = false
    }

    fun startWave(id: Int) {
        if (waveOn.contains(id) || waveDone.contains(id)) return
        val wv = g.world.waves.firstOrNull { it.id == id } ?: return
        waveOn.add(id)
        cast = 1f
        val p = g.player
        g.fx.banner(wv.title, wv.sub, 0xFFD890FF.toInt(), 2.2f, true)
        g.platform.sound(Sfx.WARNING, 0.8f, 1.15f); g.platform.sound(Sfx.WHOOSH, 0.8f, 0.8f); g.platform.haptic(true)
        g.shake = max(g.shake, 0.3f)
        // the lantern ships fly down beside the path; the chasers come in from behind, one after another
        for (k in 0 until wv.bombers) {
            val m = Minion(MK.BOMBER, null, id)
            m.side = if (k % 2 == 0) 1f else -1f; m.gap = 1.2f + k * 1.1f; m.state = MS.APPEAR; m.t = 0f
            place(m); m.y += 6f
            all.add(m)
        }
        for (k in 0 until wv.chasers) {
            val m = Minion(MK.CHASER, null, id)
            m.side = if (k % 2 == 0) -1f else 1f; m.gap = 7.5f + k * 1.6f; m.cool = 0.6f + k * 0.9f; m.look = 1
            m.state = MS.APPEAR; m.t = 0f
            place(m)
            all.add(m)
        }
        g.fx.burst(p.x, p.y + 2.5f, p.z + 2f, 24, PK.SPARK, 0xFFC070FF.toInt(), 5f, 0.12f, 0.7f)
    }

    fun endWave(id: Int) {
        if (!waveOn.remove(id)) return
        waveDone.add(id)
        var n = 0
        for (m in all) if (m.wave == id && m.state != MS.HIDDEN && m.state != MS.VANISH && m.state != MS.STOMPED) { vanish(m); n++ }
        for (m in all) if (m.wave == id && m.state == MS.HIDDEN) m.state = MS.VANISH
        for (b in bolts) b.on = false
        val p = g.player
        g.fx.banner("THE MINIONS VANISH!", if (lastWaveDone) "THE SORCERER RETREATS — RUN FOR THE GATE!" else "YOU ESCAPED THE SORCERER'S MINIONS", 0xFF7FFFA0.toInt(), 2.2f)
        g.addScore(500, p.x, p.y + 2.6f, p.z, "ESCAPE BONUS")
        g.platform.sound(Sfx.CHECKPOINT, 1f, 1.2f)
        if (n > 0) g.platform.sound(Sfx.WHOOSH, 0.8f, 1.3f)
    }

    private fun vanish(m: Minion) {
        m.state = MS.VANISH; m.t = 0f
        g.fx.burst(m.x, m.y + 0.5f, m.z, 16, PK.DUST, 0xFFB890E8.toInt(), 2.5f, 0.2f, 0.8f)
        g.fx.burst(m.x, m.y + 0.5f, m.z, 10, PK.SPARK, 0xFFE0A0FF.toInt(), 3f, 0.1f, 0.5f)
    }

    // ------------------------------------------------------------------ where each one is
    private val ptmp = FloatArray(3)

    /** Puts a minion where its kind and rhythm say it is now. */
    private fun place(m: Minion) {
        // (patrols and swoops keep the hazard clock: the hourglass slows them)
        val t = g.hazT
        val sp = m.spot
        when (m.kind) {
            MK.PATROL -> if (sp != null) {
                val before = m.x
                m.x = sp.x + sp.amp * sin(sp.phase + t * sp.speed)
                // a hopping gait
                m.y = sp.y + abs(sin(t * 7f + sp.phase)) * 0.16f
                m.z = sp.z + 0.5f
                if (abs(m.x - before) > 0.002f) m.face = if (m.x > before) 1f else -1f
            }
            MK.SWOOP -> if (sp != null) swoopAt(m, sp, t)
            MK.CHASER -> {
                val p = g.player
                val r = floor(p.z).toInt()
                val px = g.world.pathXAt(r)
                val lvl = g.world.levelAt(r)
                // it flies at the boy's side, level with him when it is close and back toward the camera when it is
                // not, so it slides in from the screen's edge as it closes in (its [gap] is how far behind it really is)
                m.x = px + m.side * (1.35f + 0.1f * m.gap)
                m.z = p.z + 1.0f - m.gap * 0.3f
                m.y = max(lvl, p.y) + 1.5f + 0.3f * sin(g.t * 3.1f + m.side * 2f)
                m.face = -m.side
            }
            MK.BOMBER -> {
                val p = g.player
                val r = floor(p.z).toInt()
                val px = g.world.pathXAt(r)
                val lvl = g.world.levelAt(r)
                m.x = px + m.side * (3.3f + 0.4f * sin(g.t * 0.8f + m.gap))
                m.z = p.z + 4.2f + m.gap + 0.7f * sin(g.t * 0.6f + m.side)
                m.y = max(lvl, p.y) + 3.1f + 0.25f * sin(g.t * 2.3f + m.gap)
                m.face = -m.side
            }
        }
    }

    /**
     * A swooper's rhythm: it hovers beside the path, glows, then swoops across the row low (knee to chest height at the
     * middle of the path, higher at its edges), then rests on the other side and comes back the other way.
     */
    private fun swoopAt(m: Minion, sp: MinionSpot, t: Float) {
        val period = sp.speed
        val c = ((t + sp.phase) % period + period) % period
        val pass = ((floor((t + sp.phase) / period)).toInt() and 1) == 0
        val dir = if (pass) 1f else -1f
        val restAt = period - PASS
        val from = sp.x - dir * (sp.amp + 0.6f); val to = sp.x + dir * (sp.amp + 0.6f)
        m.z = sp.z + 0.5f
        if (c < restAt) {
            // resting on the side it came from, bobbing, glowing before the next pass
            m.crossing = false
            m.untilCross = restAt - c
            m.warn = if (restAt - c < WARN) 1f - (restAt - c) / WARN else 0f
            m.x = from + dir * -0.2f * sin(c * 2f)
            m.y = sp.y + 1.7f + 0.15f * sin(t * 3f + sp.phase)
            m.face = dir
        } else {
            val u = (c - restAt) / PASS
            m.crossing = true; m.untilCross = 0f; m.warn = 0f
            m.x = lerp(from, to, u)
            val k = 2f * u - 1f
            m.y = sp.y + 0.5f + 1.25f * k * k
            m.face = dir
        }
    }

    // ------------------------------------------------------------------ update
    fun update(dt: Float) {
        cast = max(0f, cast - dt * 1.4f)
        skyAway = approach(skyAway, if (anyWave) 1f else 0f, dt * 1.2f)
        retreat = if (lastWaveDone) min(1f, retreat + dt * 0.35f) else retreat
        val p = g.player
        val playing = g.state == GS.PLAY
        var danger = 0f
        for (m in all) {
            m.t += dt
            when (m.state) {
                MS.HIDDEN -> {
                    if (!waveOn.contains(m.wave)) continue
                    val sp = m.spot ?: continue
                    // it appears (drops out of a purple flash) as the boy comes near
                    if (sp.z - p.z < 17f && sp.z - p.z > -3f) {
                        m.state = MS.APPEAR; m.t = 0f; place(m)
                        g.fx.burst(m.x, m.y + 1.4f, m.z, 18, PK.SPARK, 0xFFC878FF.toInt(), 3.5f, 0.12f, 0.6f)
                        val d = len3(p.x - m.x, p.y - m.y, p.z - m.z)
                        g.platform.sound(Sfx.WHOOSH, clamp01(1.1f - d / 18f) * 0.7f, 1.5f)
                    }
                }
                MS.APPEAR -> {
                    m.show = min(1f, m.show + dt * 2.2f)
                    place(m)
                    if (m.kind == MK.PATROL) m.y += (1f - smooth(m.t / 0.6f)) * 3f
                    if (m.kind == MK.BOMBER) m.y += (1f - smooth(m.t / 1.2f)) * 6f
                    if (m.kind == MK.CHASER) m.gap = max(m.gap, 5f)
                    if (m.t > (if (m.kind == MK.BOMBER) 1.2f else 0.6f)) { m.state = MS.ACTIVE; m.t = 0f; m.cool = max(m.cool, if (m.kind == MK.BOMBER) 1.2f + m.gap * 0.6f else 0.8f) }
                }
                MS.ACTIVE -> {
                    m.show = min(1f, m.show + dt * 3f)
                    when (m.kind) {
                        MK.CHASER -> if (playing) chase(m, dt)
                        MK.BOMBER -> if (playing) bomb(m, dt)
                    }
                    place(m)
                    if (playing) contact(m)
                    if (m.kind == MK.CHASER) danger = max(danger, 1f - clamp01((m.gap - LUNGE_AT) / 6f))
                }
                MS.LUNGE -> { lunge(m, dt); danger = 1f }
                MS.STOMPED -> { m.show = max(0f, m.show - dt * 3f); m.y -= dt * 1.5f }
                MS.VANISH -> { m.show = max(0f, m.show - dt * 2.5f); m.y += dt * 1.2f }
            }
            // a patroller or swooper the boy has long passed is gone (the wave's end would take it anyway)
            val sp = m.spot
            if (sp != null && (m.state == MS.ACTIVE) && p.z - sp.z > 14f) { m.state = MS.VANISH; m.t = 0f }
        }
        all.removeAll { m -> m.spot == null && (m.state == MS.VANISH || m.state == MS.STOMPED) && m.show <= 0f }
        meter = approach(meter, danger, dt * 3f)
        updateBolts(dt)
    }

    /** A chaser gains on the boy when he slows (and drops back when he runs flat out); close enough, it lunges. */
    private fun chase(m: Minion, dt: Float) {
        val p = g.player
        if (p.state != PS.NORMAL) return
        val sp = max(0f, p.vz)
        // (standing still it closes about 2.3 units a second: a moment's wait at a hazard is fine, a long stop is not)
        m.gap += ((sp - CHASE_SPEED) * 0.5f - 0.15f) * dt
        // it never falls hopelessly behind (it flies faster to catch up) and never passes him
        if (m.gap > 10f) m.gap -= (m.gap - 10f) * dt * 2f
        m.gap = clamp(m.gap, 0.6f, 13f)
        if (m.cool > 0f) m.cool -= dt
        if (m.gap < LUNGE_AT && m.cool <= 0f && p.invuln <= 0f && all.none { it !== m && it.state == MS.LUNGE }) {
            m.state = MS.LUNGE; m.t = 0f
            m.lx = m.x; m.ly = m.y; m.lz = m.z
            g.platform.sound(Sfx.WARNING, 0.45f, 1.6f)
        }
    }

    /** The lunge: a moment's warning (the eyes flare), a dash at where the boy was, then it is thrown back. */
    private fun lunge(m: Minion, dt: Float) {
        val p = g.player
        m.show = 1f
        if (m.t < WINDUP) {
            // it hangs back, trembling, eyes flaring: the warning
            place(m)
            m.x += sin(m.t * 60f) * 0.04f
            m.tx = p.x; m.ty = p.y + 0.7f; m.tz = p.z + max(0f, p.vz) * 0.1f
            m.lx = m.x; m.ly = m.y; m.lz = m.z
            return
        }
        val u = clamp01((m.t - WINDUP) / DASH)
        m.x = lerp(m.lx, m.tx, u); m.y = lerp(m.ly, m.ty, u) - 0.35f; m.z = lerp(m.lz, m.tz, u)
        if (g.state == GS.PLAY && (p.state == PS.NORMAL) && p.invuln <= 0f) {
            if (len3(p.x - m.x, p.y + 0.7f - (m.y + 0.35f), p.z - m.z) < 0.72f) {
                strike(m, "minion")
                recoil(m); return
            }
        }
        if (m.t >= WINDUP + DASH) recoil(m)
    }

    private fun recoil(m: Minion) {
        m.state = MS.ACTIVE; m.t = 0f; m.gap = RECOIL; m.cool = 2.4f
        g.fx.burst(m.x, m.y + 0.4f, m.z, 8, PK.SPARK, 0xFFC878FF.toInt(), 2.5f, 0.08f, 0.4f)
    }

    /** It hits the boy: a heart (the shield takes it instead and zaps the minion). */
    private fun strike(m: Minion, why: String) {
        if (g.shieldOn) {
            g.hurt(why, null)
            if (m.kind == MK.PATROL || m.kind == MK.SWOOP) vanish(m)
            return
        }
        g.hurt(why, null)
        val p = g.player
        g.fx.burst(p.x, p.y + 0.9f, p.z, 14, PK.SPARK, 0xFFD070FF.toInt(), 3.5f, 0.1f, 0.5f)
    }

    /** Touching a patroller or a swooper hurts; landing on one from above stomps it. */
    private fun contact(m: Minion) {
        if (m.kind != MK.PATROL && m.kind != MK.SWOOP) return
        val p = g.player
        if (p.state != PS.NORMAL) return
        if (m.kind == MK.SWOOP && !m.crossing) return
        val hw = if (m.kind == MK.PATROL) 0.36f else 0.34f
        val top = m.y + (if (m.kind == MK.PATROL) 0.95f else 0.75f)
        val bottom = m.y + (if (m.kind == MK.PATROL) 0f else 0.05f)
        if (abs(p.x - m.x) > hw + Tune.RADIUS || abs(p.z - m.z) > hw + Tune.RADIUS) return
        if (p.y > top + 0.05f || p.y + Tune.HEIGHT < bottom) return
        // coming down onto its head: a stomp
        if (p.vy < -0.5f && p.y > top - 0.42f) {
            stomp(m); return
        }
        if (p.invuln > 0f) return
        val shielded = g.shieldOn
        strike(m, "minion")
        // knocked back down the path (never sideways off it)
        if (!shielded) { p.vz = -3.2f; p.vy = 6f; p.vx = 0f; p.grounded = false; p.jumping = false; g.swipe.stop() }
    }

    private fun stomp(m: Minion) {
        val p = g.player
        m.state = MS.STOMPED; m.t = 0f; stomps++
        p.vy = 9.5f; p.grounded = false; p.jumping = true; p.jumpT = 0f
        g.kick(1.2f)
        g.platform.sound(Sfx.BOUNCE, 1f, 1.3f); g.platform.sound(Sfx.STAR, 0.6f, 1.4f)
        g.fx.burst(m.x, m.y + 0.6f, m.z, 20, PK.STAR, 0xFFE8B0FF.toInt(), 4f, 0.13f, 0.6f)
        g.fx.burst(m.x, m.y + 0.4f, m.z, 12, PK.DUST, 0xFFB890E8.toInt(), 2.5f, 0.2f, 0.7f)
        g.addScore(150, m.x, m.y + 1.6f, m.z, "STOMP!")
        // a minion drops a few coins when it pops
        if (g.cam.project(m.x, m.y + 0.6f, m.z)) for (i in 0 until 3) g.fx.fly(FK.COIN, g.cam.sx, g.cam.sy, g.hud.coinIconX(), g.hud.coinIconY(), Tune.COIN_VALUE, i * 0.07f, 0.7f)
    }

    // ------------------------------------------------------------------ bolts
    /** A bomber drops a bolt onto the path ahead of the boy, where he will be in a moment (or the lane beside it). */
    private fun bomb(m: Minion, dt: Float) {
        val p = g.player
        m.cool -= dt
        if (m.cool > 0f || p.state != PS.NORMAL) return
        m.cool = BOLT_EVERY + rng.f(-0.3f, 0.4f)
        val ahead = max(0f, p.vz) * BOLT_FLIGHT + rng.f(0.8f, 2.2f)
        val row = floor(p.z + ahead).toInt()
        val px = g.world.pathXAt(row)
        val ground = g.world.levelAt(row)
        // the boy's lane most of the time, sometimes the one beside it
        var lane = (p.x - px).let { kotlin.math.round(it) }
        if (rng.f() < 0.35f) lane += if (rng.f() < 0.5f) -1f else 1f
        lane = clamp(lane, -1f, 1f)
        val tx = px + lane
        // only where there is floor to land on
        val floorHere = g.world.row(row)?.any { it.collides() && abs(it.x - tx) < 0.5f && abs(it.y1 - ground) < 0.3f } == true
        if (!floorHere) { m.cool = 0.4f; return }
        for (b in bolts) if (!b.on) {
            b.on = true; b.landed = false; b.t = 0f
            b.x = m.x - m.side * 0.5f; b.y = m.y + 0.2f; b.z = m.z - 0.2f
            b.tx = tx; b.tz = row + 0.5f; b.ground = ground
            val T = BOLT_FLIGHT
            b.vx = (b.tx - b.x) / T; b.vz = (b.tz - b.z) / T; b.vy = (ground - b.y) / T + 12f * T
            g.platform.sound(Sfx.WHOOSH, 0.5f, 1.7f)
            g.fx.burst(b.x, b.y, b.z, 8, PK.SPARK, 0xFFE070FF.toInt(), 2f, 0.08f, 0.3f)
            break
        }
    }

    private fun updateBolts(dt: Float) {
        val p = g.player
        for (b in bolts) if (b.on) {
            if (!b.landed) {
                b.t += dt
                b.vy -= 24f * dt; b.x += b.vx * dt; b.y += b.vy * dt; b.z += b.vz * dt
                if (g.fx.rng.f() < dt * 50f) {
                    val q = g.fx.spawn()
                    q.x = b.x; q.y = b.y; q.z = b.z; q.vx = 0f; q.vy = 0f; q.vz = 0f
                    q.life = 0.35f; q.maxLife = 0.35f; q.size = 0.12f; q.color = 0xFFD080FF.toInt(); q.kind = PK.SPARK
                }
                if (b.y <= b.ground && b.vy < 0f) {
                    b.y = b.ground; b.x = b.tx; b.z = b.tz; b.landed = true; b.t = 0f
                    g.fx.burst(b.x, b.ground + 0.2f, b.z, 22, PK.SPARK, 0xFFD070FF.toInt(), 4.5f, 0.12f, 0.5f)
                    g.fx.burst(b.x, b.ground + 0.1f, b.z, 10, PK.STAR, 0xFFF0C8FF.toInt(), 3f, 0.1f, 0.5f)
                    val d = len3(p.x - b.x, p.y - b.ground, p.z - b.z)
                    g.platform.sound(Sfx.EXPLODE, clamp01(1.1f - d / 12f) * 0.55f, 1.5f)
                    g.shake = max(g.shake, 0.22f * clamp01(1.2f - d / 8f))
                    if (g.state == GS.PLAY && p.state == PS.NORMAL && abs(p.x - b.x) < 0.75f && abs(p.z - b.z) < 0.75f &&
                        p.y < b.ground + 1.2f && p.y > b.ground - 1f) g.hurt("bolt", null)
                }
            } else { b.t += dt; if (b.t > 0.2f) b.on = false }
        }
    }

    /** For the autopilot: bolts in the air and where they will land (x, z), or none. */
    fun boltTargets(out: ArrayList<FloatArray>) {
        out.clear()
        for (b in bolts) if (b.on && !b.landed) out.add(floatArrayOf(b.tx, b.tz, BOLT_FLIGHT - b.t))
    }
}
