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
    var reach = 0f         // arm swing when close
    var sortKey = 0f
}

class Dragon {
    var on = false
    var done = false
    var t = 0f
    var x = 0f; var y = 0f; var z = 0f
    var vx = 0f
    var shots = 0
    var sortKey = 0f
    var ox = 0f; var lvl = 15f
}

class Fireball {
    var on = false
    var x = 0f; var y = 0f; var z = 0f
    var x0 = 0f; var y0 = 0f; var z0 = 0f
    var x1 = 0f; var y1 = 0f; var z1 = 0f
    var t = 0f
    var group = 0
    var sortKey = 0f
}

class Rock {
    var on = false
    var x = 0f; var y = 0f; var z = 0f
    var vy = 0f
    var ground = 0f
    var landed = false
    var t = 0f
    var sortKey = 0f
}

/** Adventure events: storm, guard chase, dragon attack, tower collapse, lava rush, falling debris. */
class Events(val g: Game) {
    private val rng = Rng(99)

    // storm
    var stormOn = false
    var stormAmt = 0f
    var windVX = 0f
    var windDir = 1f
    var windPhase = 0          // 0 calm, 1 warning, 2 gust
    var windT = 0f
    var lightning = 0f
    private var lightningT = 3f

    // chase
    var chaseActive = false
    var chaseDone = false
    var revealing = false
    var revealT = 0f
    private var revealedOnce = false
    var chaseT = 0f
    val guard = Guard()

    // dragon
    val dragon = Dragon()
    val fireballs = Array(4) { Fireball() }
    private val dragonGroups = arrayOf(intArrayOf(146, 147), intArrayOf(151), intArrayOf(155, 156))
    private val groupHit = BooleanArray(3)

    // collapse
    var collapseOn = false
    var collapseZ = 0f
    private var collapseSpeed = 0f
    private val crumbling = ArrayList<Block>()
    private var dustT = 0f

    // lava
    var lavaOn = false
    var lavaStop = false
    var lavaY = -100f
    private var lavaSpeed = 0f

    // debris
    val rocks = Array(6) { Rock() }
    private var rockT = 0f

    // danger meter (chase / collapse)
    var meter = 0f
    var meterShow = 0f
    var meterLabel = "TOWER GUARD"
    var meterKind = 0

    fun reset() {
        stormOn = false; stormAmt = 0f; windVX = 0f; windPhase = 0; windT = 0f; lightning = 0f
        resetChase(); resetDragon(); resetCollapse(); resetLava()
        meter = 0f; meterShow = 0f
    }

    private fun resetChase() {
        chaseActive = false; chaseDone = false; revealing = false; revealT = 0f; chaseT = 0f
        guard.on = false; guard.falling = false; guard.stun = 0f
        for (r in rocks) r.on = false
    }
    private fun resetDragon() { dragon.on = false; dragon.done = false; dragon.t = 0f; dragon.shots = 0; for (f in fireballs) f.on = false; groupHit.fill(false) }
    private fun resetCollapse() { collapseOn = false; collapseZ = 0f; crumbling.clear() }
    private fun resetLava() { lavaOn = false; lavaStop = false; lavaY = -100f; lavaSpeed = 0f }

    private fun triggerZ(e: Int): Float = g.world.triggers.firstOrNull { it.event == e }?.z ?: 9999f

    /** Called after the player is carried back to a checkpoint. */
    fun resetAfter(cpZ: Float) {
        if (triggerZ(Ev.STORM) > cpZ) { stormOn = false; windVX = 0f; windPhase = 0 }
        if (triggerZ(Ev.STORM_END) > cpZ && triggerZ(Ev.STORM) <= cpZ) { stormOn = true }
        if (triggerZ(Ev.CHASE) > cpZ) resetChase()
        if (triggerZ(Ev.DRAGON) > cpZ) resetDragon()
        if (triggerZ(Ev.COLLAPSE) > cpZ) resetCollapse()
        if (triggerZ(Ev.LAVA) > cpZ) resetLava()
    }

    fun fire(e: Int) {
        val p = g.player
        val ox = g.world.sectionAt(p.z)?.originX ?: 0f
        when (e) {
            Ev.STORM -> {
                stormOn = true; windPhase = 0; windT = 1.2f
                g.fx.banner("STORM!", "WIND GUSTS WILL PUSH YOU", 0xFFBFE4FF.toInt(), 2.2f, true)
                g.platform.sound(Sfx.THUNDER); lightning = 1f
            }
            Ev.STORM_END -> { stormOn = false; windPhase = 0 }
            Ev.CHASE -> {
                g.fx.banner("WARNING!", "THE TOWER GUARD IS COMING!", 0xFFFF5A4A.toInt(), 2.8f, true)
                g.platform.sound(Sfx.WARNING)
                guard.on = true; guard.falling = false; guard.stun = 0f
                guard.z = p.z - 11f; guard.x = ox; guard.y = g.world.levelAt(floor(guard.z).toInt())
                guard.step = 0f; chaseT = 0f
                if (!revealedOnce) {
                    revealedOnce = true; revealing = true; revealT = 0f; guard.rise = 0f
                } else { guard.rise = 1f; chaseActive = true; guard.z = p.z - 12f }
                for (r in rocks) r.on = false
                rockT = 2.5f
            }
            Ev.CHASE_END -> {
                if (guard.on && !chaseDone) {
                    chaseActive = false; chaseDone = true
                    guard.falling = true; guard.vy = 2f; guard.fallT = 0f
                    g.fx.banner("SAFE!", "THE GUARD FELL BEHIND", 0xFF7FFFA0.toInt(), 2.2f)
                    g.platform.sound(Sfx.ROAR, 0.9f, 0.8f); g.platform.sound(Sfx.CRUMBLE)
                    g.shake = max(g.shake, 0.5f)
                    g.fx.dust(guard.x, guard.y, guard.z, 16, 0xFFC8A080.toInt())
                }
            }
            Ev.DRAGON -> {
                dragon.on = true; dragon.done = false; dragon.t = 0f; dragon.shots = 0
                dragon.ox = ox; dragon.lvl = g.world.levelAt(floor(p.z).toInt())
                groupHit.fill(false)
                g.fx.banner("DRAGON ATTACK!", "IT'S BURNING THE BRIDGE!", 0xFFFF8A3A.toInt(), 2.4f, true)
                g.platform.sound(Sfx.DRAGON)
            }
            Ev.COLLAPSE -> {
                collapseOn = true
                collapseZ = (g.world.triggers.firstOrNull { it.event == Ev.COLLAPSE }?.z ?: p.z) - 2f
                collapseSpeed = 2.6f
                g.fx.banner("TOWER COLLAPSE!", "KEEP MOVING!", 0xFFFFB04A.toInt(), 2.4f, true)
                g.platform.sound(Sfx.CRUMBLE); g.platform.sound(Sfx.WARNING, 0.7f)
                g.shake = max(g.shake, 0.6f)
            }
            Ev.LAVA -> {
                lavaOn = true; lavaStop = false
                lavaY = g.world.levelAt(floor(p.z).toInt()) - 4.8f
                lavaSpeed = 0.5f
                g.fx.banner("LAVA RUSH!", "CLIMB TO THE PORTAL!", 0xFFFF6A2A.toInt(), 2.4f, true)
                g.platform.sound(Sfx.WARNING)
            }
            Ev.LAVA_STOP -> { lavaStop = true }
        }
    }

    fun update(dt: Float) {
        updateStorm(dt)
        updateChase(dt)
        updateDragon(dt)
        updateCollapse(dt)
        updateLava(dt)
        val want = when {
            chaseActive || (revealing && revealT > 2.2f) -> { meterKind = 0; meterLabel = "TOWER GUARD"; 1f }
            collapseOn && g.state == GS.PLAY -> { meterKind = 1; meterLabel = "COLLAPSE"; 1f }
            else -> 0f
        }
        meterShow = approach(meterShow, want, dt * 3f)
        if (collapseOn) meter = 1f - clamp01((g.player.z - collapseZ - 1f) / 9f)
        lightning = max(0f, lightning - dt * 3f)
    }

    // ------------------------------------------------------------------ storm
    private fun updateStorm(dt: Float) {
        stormAmt = approach(stormAmt, if (stormOn) 1f else 0f, dt * 0.8f)
        if (!stormOn || g.state != GS.PLAY) { windVX = approach(windVX, 0f, dt * 6f); return }
        windT -= dt
        if (windT <= 0f) {
            when (windPhase) {
                0 -> { windPhase = 1; windT = 0.75f; windDir = if (rng.f() < 0.5f) -1f else 1f; g.platform.sound(Sfx.WIND, 0.8f) }
                1 -> { windPhase = 2; windT = 1.6f }
                else -> { windPhase = 0; windT = rng.f(1.1f, 1.8f) }
            }
        }
        val target = if (windPhase == 2) windDir * 2.3f else 0f
        windVX = approach(windVX, target, dt * 7f)
        lightningT -= dt
        if (lightningT <= 0f) { lightningT = rng.f(2.6f, 5f); lightning = 1f; g.platform.sound(Sfx.THUNDER, 0.7f) }
    }

    // ------------------------------------------------------------------ chase
    fun revealCamera(c: Game): Boolean {
        if (!revealing) return false
        val p = c.player
        val ox = c.world.sectionAt(p.z)?.originX ?: 0f
        c.cam.yaw = Math.PI.toFloat()
        c.cam.pitch = 0.16f
        c.cam.ex = ox
        c.cam.ey = guard.y + 3.6f
        c.cam.ez = p.z - 3.6f
        return true
    }

    private fun updateChase(dt: Float) {
        val p = g.player
        if (revealing) {
            val before = revealT
            revealT += dt
            if (before < 0.9f && revealT >= 0.9f) { g.flashWhite = 0.8f }
            if (revealT > 0.9f) guard.rise = smooth((revealT - 1.0f) / 0.9f)
            if (before < 1.3f && revealT >= 1.3f) { g.platform.sound(Sfx.ROAR); g.shake = max(g.shake, 0.7f); g.platform.haptic(true) }
            if (revealT > 1.0f) g.fx.dust(guard.x + rng.f(-2f, 2f), guard.y, guard.z + rng.f(-1f, 1f), 1, 0xFFC8A080.toInt())
            if (before < 3.1f && revealT >= 3.1f) { g.flashWhite = 0.6f }
            if (revealT >= 3.2f) { revealing = false; chaseActive = true; chaseT = 0f; guard.rise = 1f }
            return
        }
        if (guard.on && guard.falling) {
            guard.fallT += dt
            guard.vy -= 18f * dt
            guard.y += guard.vy * dt
            if (guard.fallT > 3f) guard.on = false
            return
        }
        if (!chaseActive) return
        if (g.state != GS.PLAY || p.state != PS.NORMAL) return
        chaseT += dt
        var sp = min(5.0f, 3.5f + chaseT * 0.08f)
        val dist = p.z - guard.z
        if (dist > 13f) sp = 6.4f
        if (guard.stun > 0f) { guard.stun -= dt; sp = 0f }
        guard.speed = sp
        guard.z += sp * dt
        val ox = g.world.sectionAt(guard.z)?.originX ?: 0f
        guard.x = lerp(guard.x, clamp(p.x, ox - 1f, ox + 1f), damp(1.5f, dt))
        guard.y = lerp(guard.y, g.world.levelAt(floor(guard.z).toInt()), damp(3f, dt))
        val stepBefore = guard.step
        guard.step += dt * max(sp, 1f) * 0.75f
        if (floor(stepBefore) != floor(guard.step)) {
            val close = 1f - clamp01((dist - 2f) / 12f)
            g.platform.sound(Sfx.STOMP, 0.3f + 0.7f * close)
            g.shake = max(g.shake, 0.18f + 0.35f * close)
        }
        guard.reach = approach(guard.reach, if (dist < 4f) 1f else 0f, dt * 3f)
        meter = 1f - clamp01((dist - 1.4f) / 11f)
        if (dist < 1.4f) {
            if (g.shieldOn) {
                g.breakShield()
                guard.z = p.z - 7f; guard.stun = 1.3f
                g.fx.popupWorld("BLOCKED!", p.x, p.y + 2.3f, p.z, 0xFF9FE8FF.toInt(), 46f)
                g.platform.sound(Sfx.ROAR, 0.8f, 1.2f)
            } else {
                p.state = PS.CAUGHT; p.stateT = 0f
                g.shake = 1.2f; g.flashRed = 1f
                g.platform.sound(Sfx.ROAR); g.platform.haptic(true)
                g.fail("CAUGHT BY THE TOWER GUARD!")
            }
        }
        updateRocks(dt)
    }

    private fun updateRocks(dt: Float) {
        val p = g.player
        rockT -= dt
        if (rockT <= 0f) {
            rockT = rng.f(1.2f, 2.0f)
            for (r in rocks) if (!r.on) {
                val ox = g.world.sectionAt(p.z)?.originX ?: 0f
                val row = floor(p.z + rng.f(2.5f, 6.5f)).toInt()
                r.on = true; r.landed = false; r.t = 0f
                r.x = ox + (rng.i(3) - 1).toFloat()
                r.z = row + 0.5f
                r.ground = g.world.levelAt(row)
                r.y = r.ground + 11f; r.vy = -2f
                break
            }
        }
        for (r in rocks) if (r.on) {
            if (!r.landed) {
                r.vy -= 24f * dt; r.y += r.vy * dt
                if (r.y <= r.ground) {
                    r.y = r.ground; r.landed = true; r.t = 0f
                    g.fx.dust(r.x, r.ground, r.z, 8, 0xFFC8A080.toInt())
                    g.fx.shards(Block(r.x, r.ground, r.z - 0.5f, BC.BRICK), BC.base(BC.BRICK), 6)
                    g.platform.sound(Sfx.CRUMBLE, 0.7f)
                    g.shake = max(g.shake, 0.25f)
                    if (abs(p.x - r.x) < 0.8f && abs(p.z - r.z) < 0.8f && p.y < r.ground + 1.4f && p.y > r.ground - 1f) g.hurt("debris", null)
                }
            } else { r.t += dt; if (r.t > 0.15f) r.on = false }
        }
    }

    // ------------------------------------------------------------------ dragon
    private fun dragonPos(t: Float) {
        val u = clamp01(t / 5.2f)
        val ox = dragon.ox; val l = dragon.lvl
        // cubic bezier across the course, ahead of the player
        val p0x = ox + 26f; val p0y = l + 12f; val p0z = 166f
        val p1x = ox + 9f; val p1y = l + 6.5f; val p1z = 157f
        val p2x = ox - 7f; val p2y = l + 6.5f; val p2z = 151f
        val p3x = ox - 30f; val p3y = l + 14f; val p3z = 161f
        val a = (1 - u) * (1 - u) * (1 - u); val b = 3 * (1 - u) * (1 - u) * u; val c = 3 * (1 - u) * u * u; val d = u * u * u
        val nx = a * p0x + b * p1x + c * p2x + d * p3x
        dragon.vx = nx - dragon.x
        dragon.x = nx
        dragon.y = a * p0y + b * p1y + c * p2y + d * p3y + sin(t * 3f) * 0.4f
        dragon.z = a * p0z + b * p1z + c * p2z + d * p3z
    }

    private fun updateDragon(dt: Float) {
        if (dragon.on) {
            dragon.t += dt
            dragonPos(dragon.t)
            val shotTimes = floatArrayOf(1.0f, 1.7f, 2.4f)
            if (dragon.shots < 3 && dragon.t >= shotTimes[dragon.shots]) {
                dragon.shots++
                shoot()
            }
            if (dragon.t > 5.2f) { dragon.on = false; dragon.done = true }
        }
        for (f in fireballs) if (f.on) {
            f.t += dt / 0.6f
            val u = min(1f, f.t)
            f.x = lerp(f.x0, f.x1, u); f.z = lerp(f.z0, f.z1, u)
            f.y = lerp(f.y0, f.y1, u) + sin(u * Math.PI.toFloat()) * 1.2f
            val q = g.fx.spawn()
            q.x = f.x; q.y = f.y; q.z = f.z; q.vx = rng.f(-0.5f, 0.5f); q.vy = rng.f(0.2f, 1f); q.vz = rng.f(-0.5f, 0.5f)
            q.life = 0.35f; q.maxLife = 0.35f; q.size = 0.35f; q.color = 0xFFFF9A30.toInt(); q.kind = PK.FIRE
            if (f.t >= 1f) { f.on = false; impact(f) }
        }
    }

    private fun shoot() {
        val p = g.player
        var gi = -1
        for (i in dragonGroups.indices) if (!groupHit[i] && dragonGroups[i][0] > p.z + 2.2f) { gi = i; break }
        for (f in fireballs) if (!f.on) {
            f.on = true; f.t = 0f; f.group = gi
            f.x0 = dragon.x + (if (dragon.vx < 0) -1.6f else 1.6f); f.y0 = dragon.y + 0.3f; f.z0 = dragon.z
            if (gi >= 0) {
                val rows = dragonGroups[gi]
                f.x1 = dragon.ox + 0.2f; f.y1 = dragon.lvl; f.z1 = (rows.first() + rows.last() + 1) * 0.5f
                groupHit[gi] = true
            } else { f.x1 = dragon.ox + 4f; f.y1 = dragon.lvl - 3f; f.z1 = p.z + 8f }
            g.platform.sound(Sfx.DRAGON, 0.6f, 1.4f)
            break
        }
    }

    private fun impact(f: Fireball) {
        g.fx.burst(f.x1, f.y1, f.z1, 36, PK.FIRE, 0xFFFF8A20.toInt(), 7f, 0.4f, 0.7f, 0f, 2f)
        g.fx.burst(f.x1, f.y1, f.z1, 14, PK.SPARK, 0xFFFFE070.toInt(), 8f, 0.12f, 0.6f)
        g.platform.sound(Sfx.EXPLODE)
        g.shake = max(g.shake, 0.6f)
        if (f.group < 0) return
        val rows = dragonGroups[f.group]
        for (r in rows) {
            val row = g.world.row(r) ?: continue
            for (b in row) if (b.eventTag == Ev.DRAGON && !b.destroyed) {
                if (f.group == 2 && b.x < dragon.ox - 0.1f) continue
                b.destroyed = true; b.vy = rng.f(2f, 6f); b.flash = 1f
                g.fx.shards(b, BC.base(b.color), 6)
            }
        }
    }

    // ------------------------------------------------------------------ collapse
    private fun updateCollapse(dt: Float) {
        if (!collapseOn) return
        if (g.state != GS.PLAY || g.player.state != PS.NORMAL) return
        collapseSpeed = min(4.4f, collapseSpeed + dt * 0.22f)
        collapseZ += collapseSpeed * dt
        val end = 177f
        if (collapseZ > end) { collapseOn = false; return }
        g.shake = max(g.shake, 0.16f)
        val r0 = floor(collapseZ).toInt() - 1
        for (r in r0 - 1..r0 + 1) {
            val row = g.world.row(r) ?: continue
            for (b in row) if (b.eventTag == Ev.COLLAPSE && !b.destroyed && b.z < collapseZ && !crumbling.contains(b)) {
                crumbling.add(b); b.respawn = 0.3f; b.shake = 0.3f
            }
        }
        val it = crumbling.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.respawn -= dt
            b.shake = max(b.shake, 0.05f)
            if (b.respawn <= 0f) { b.destroyed = true; b.vy = 0f; it.remove() }
        }
        dustT -= dt
        if (dustT <= 0f) {
            dustT = 0.08f
            val ox = g.world.sectionAt(collapseZ)?.originX ?: 0f
            g.fx.dust(ox + rng.f(-1.5f, 1.5f), g.world.levelAt(floor(collapseZ).toInt()), collapseZ, 2, 0xFFC8A080.toInt())
        }
        if (rng.f() < dt * 3f) g.platform.sound(Sfx.CRUMBLE, 0.4f, rng.f(0.8f, 1.2f))
    }

    // ------------------------------------------------------------------ lava
    private fun updateLava(dt: Float) {
        if (!lavaOn) return
        if (g.state == GS.PLAY && g.player.state == PS.NORMAL) {
            if (!lavaStop) { lavaSpeed = min(0.95f, lavaSpeed + dt * 0.035f); lavaY += lavaSpeed * dt }
            else lavaY = max(lavaY - dt * 0.4f, g.world.levelAt(195) - 5f)
            if (g.player.y < lavaY + 0.05f) g.startRescue("lava")
        }
        if (rng.f() < dt * 12f) {
            val p = g.player
            val q = g.fx.spawn()
            q.x = p.x + rng.f(-7f, 7f); q.y = lavaY + 0.1f; q.z = p.z + rng.f(0f, 16f)
            q.vx = rng.f(-0.3f, 0.3f); q.vy = rng.f(1.5f, 3.5f); q.vz = 0f
            q.life = rng.f(0.8f, 1.6f); q.maxLife = q.life; q.size = rng.f(0.06f, 0.14f); q.color = 0xFFFFB040.toInt(); q.kind = PK.EMBER
        }
    }
}
