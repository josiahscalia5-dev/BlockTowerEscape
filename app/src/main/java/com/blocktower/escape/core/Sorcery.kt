package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/** The Sorcerer's Wrath (Level 7's guardian encounter): not yet, rising, attacking, reeling from a star rune, over. */
object WS { const val NONE = 0; const val RISE = 1; const val ATTACK = 2; const val STUN = 3; const val DONE = 4 }

/** A rolling stone creature (a ball of moonstone with glowing eyes): 0 waiting, 1 dropping in, 2 rolling, 3 smashed, 4 gone, 5 off the edge. */
class Roller(val spot: SpellSpot) {
    var state = 0
    var x = 0f; var y = 0f; var z = 0f; var vy = 0f
    /** Its lane, relative to the path. */
    var lane = 0f
    var rot = 0f; var t = 0f; var show = 0f
    var sortKey = 0f
}

/** A teleporting spirit: 0 waiting, 1 its mark glows on the path, 2 it forms there, 3 there (it hurts), 4 it fades, 5 gone. */
class Spirit(val spot: SpellSpot) {
    var state = 0
    var x = 0f; var y = 0f; var z = 0f; var t = 0f; var show = 0f
    /** Where it last was (the streak of its blink). */
    var fx = 0f; var fy = 0f; var fz = 0f
    var sortKey = 0f
}

/** A magical hand: how far it has reached out of the island's edge (0..1), its warning glow, and whether it is slamming the lane now. */
class Hand(val spot: SpellSpot) {
    var reach = 0f; var warn = 0f; var danger = false
    var x = 0f; var y = 0f; var z = 0f
    /** Where it comes out of the island (beside the path). */
    var ex = 0f
    var sortKey = 0f
    var wasOut = false
}

/** A shockwave from the Sorcerer's staff: a band of magic rolling down the path toward the boy (jump it). */
class Shock {
    var on = false
    var x = 0f; var y = 0f; var z = 0f; var t = 0f; var show = 0f
    var sortKey = 0f
}

/** A crystal shard falling from the sky onto a marked spot ([harmless]: the warning's strays, beside the path). */
class Shard {
    var on = false
    var x = 0f; var y = 0f; var z = 0f; var ground = 0f; var t = 0f
    var landed = false; var harmless = false
    var sortKey = 0f
}

/**
 * Level 7's magic beyond the minions: the Sorcerer's creatures on the course and the Sorcerer's Wrath.
 *
 *  - Rolling stone creatures drop onto the path ahead and roll down it toward the boy: step into another lane or jump
 *    them (landing on one smashes it).
 *  - A teleporting spirit blinks onto the path ahead of him again and again: its mark glows on the spot a second
 *    before it forms there, so he steers round it.
 *  - Magical hands reach out of the islands' edges and slam down on the outer lanes (they glow first); the middle of
 *    the path is always safe from them.
 *  - The Wrath (the level's guardian encounter): the Sorcerer rises over the course and attacks in three stretches:
 *    shockwaves rolling down the path (jump them), hands and cursed blocks (keep to the middle), falling crystal
 *    shards (their landing spots are marked; the floor crumbles too). Three star runes are set into the path: running
 *    over one strikes him (he reels and stops for a moment); lighting all three drives him back. Rings of starlight
 *    (sanctuaries) are safe from his spells. Nothing here fails the level by itself: a hit costs a heart like any
 *    hazard (the shield blocks it; the hourglass slows all of it down).
 *
 * Everything that moves runs on the hazard clock ([Game.hazT] and the dt it is given), so the hourglass slows it.
 */
class Sorcery(val g: Game) {
    companion object {
        /** A roller drops in when the boy is this far from its row; its radius. */
        const val ROLL_AT = 15f; const val ROLL_R = 0.45f
        /** Shockwaves: one every [SHOCK_EVERY] s (the staff glows [SHOCK_CHARGE] s first), rolling at [SHOCK_SPEED], this far ahead. */
        const val SHOCK_EVERY = 2.2f; const val SHOCK_CHARGE = 0.7f; const val SHOCK_SPEED = 5.5f; const val SHOCK_AHEAD = 11f
        /** Shards: one every [SHARD_EVERY] s, each falling [SHARD_FALL] s onto its marked spot. */
        const val SHARD_EVERY = 1.25f; const val SHARD_FALL = 1.1f
        /** A hand's rhythm: glow, reach out, hold the lane, draw back (the rest of its period it is hidden). */
        const val HAND_WARN = 0.8f; const val HAND_REACH = 0.25f; const val HAND_HOLD = 0.6f; const val HAND_BACK = 0.35f
        /** The spirit's blink: its mark glows, it forms, it stays, it fades. */
        const val SPIRIT_MARK = 0.9f; const val SPIRIT_IN = 0.25f; const val SPIRIT_STAY = 1.3f; const val SPIRIT_OUT = 0.3f
        /** Cursed blocks: every [CURSE_PERIOD] s they flicker for [CURSE_WARN] s and are gone for [CURSE_GONE] s. */
        const val CURSE_PERIOD = 3.2f; const val CURSE_WARN = 0.8f; const val CURSE_GONE = 0.8f
        /** How long the Sorcerer reels when a rune strikes him, and how long he takes to rise. */
        const val STUN_T = 2.0f; const val RISE_T = 2.0f
    }

    private val rng = Rng(4242)
    val rollers = ArrayList<Roller>()
    val spirits = ArrayList<Spirit>()
    val hands = ArrayList<Hand>()
    val shocks = Array(4) { Shock() }
    val shards = Array(8) { Shard() }
    private val cursed = ArrayList<Block>()

    var wrath = WS.NONE
    var wrathT = 0f
    /** Which attack the Wrath is on (1 shockwaves, 2 hands and curses, 3 shards). */
    var mode = 0
    val runesLit get() = g.world.runes.count { it.lit }
    /** The Sorcerer in the sky: 0..1 how enraged he looks, the glow of a spell he is charging, a flinch when a rune strikes him. */
    var rage = 0f; var charge = 0f; var flinch = 0f
    /** The sky: 0..1 how dark his magic makes it, and the Celestial Gate's warm light at the end. */
    var gloom = 0f; var gold = 0f
    /** The magical warning has sounded (the Sorcerer is awake). */
    var warned = false
    /** Level 7's cinematic phase (1..9, from the course's triggers). */
    var phase = 1
    /** The Celestial Gate's awakening (the finale): seconds since it began, or < 0. */
    var gateT = -1f
    /** 1 -> 0: the gate flaring when a checkpoint lights (the distant castle answers). */
    var gateFlare = 0f
    /** Player stats for the playtest report. */
    var hits = 0; var smashed = 0
    private var attackT = 0f
    private var strayT = 0f
    private var lastPz = 0f

    val active get() = wrath == WS.RISE || wrath == WS.ATTACK || wrath == WS.STUN

    fun reset() {
        rollers.clear(); spirits.clear(); hands.clear(); cursed.clear()
        for (sp in g.world.spells) when (sp.kind) {
            SK.ROLLER -> rollers.add(Roller(sp))
            SK.SPIRIT -> spirits.add(Spirit(sp))
            SK.HAND -> hands.add(Hand(sp))
        }
        for (b in g.world.blocks) if (b.eventTag == Ev.CURSE) cursed.add(b)
        for (s in shocks) s.on = false
        for (s in shards) s.on = false
        for (r in g.world.runes) { r.lit = false; r.litT = 0f }
        wrath = WS.NONE; wrathT = 0f; mode = 0
        rage = 0f; charge = 0f; flinch = 0f; gloom = 0f; gold = 0f; warned = false; phase = 1; gateT = -1f; gateFlare = 0f
        attackT = 0f; strayT = 0f; hits = 0; smashed = 0
        lastPz = g.player.z
    }

    private fun triggerZ(e: Int) = g.world.triggers.firstOrNull { it.event == e }?.z ?: 9999f

    /** Continuing from a checkpoint: what lies after it starts over (the Wrath, if the checkpoint is inside it, carries on). */
    fun resetAfter(cpZ: Float) {
        for (s in shocks) s.on = false
        for (s in shards) s.on = false
        for (r in rollers) if (r.spot.z > cpZ) { r.state = 0; r.show = 0f }
        for (s in spirits) if (s.spot.z + s.spot.len > cpZ) { s.state = 0; s.show = 0f; s.t = 0f }
        for (r in g.world.runes) if (r.z > cpZ) { r.lit = false; r.litT = 0f }
        for (b in cursed) { b.visible = true; b.alpha = 1f; b.color = b.origColor; b.shake = 0f }
        if (triggerZ(Ev.WARNING) > cpZ) warned = false
        if (triggerZ(Ev.WRATH) > cpZ) { wrath = WS.NONE; mode = 0; rage = 0f }
        else if (triggerZ(Ev.WRATH_END) > cpZ && wrath != WS.NONE) {
            // continuing inside the Wrath: he attacks again with the attack of the stretch the checkpoint is on
            wrath = WS.ATTACK; wrathT = 0f; attackT = 1.5f
            mode = g.world.triggers.filter { it.event == Ev.WRATH_MODE && it.z <= cpZ }.maxByOrNull { it.z }?.text2?.toIntOrNull() ?: 1
        }
        phase = g.world.triggers.filter { it.event == Ev.PHASE && it.z <= cpZ }.maxByOrNull { it.z }?.text2?.toIntOrNull() ?: 1
        if (triggerZ(Ev.GATE) > cpZ) gateT = -1f
        lastPz = g.player.z
    }

    /** A fall-recovery ride put the boy back on the course: spells in the air fizzle out. */
    fun onRecovered() {
        for (s in shocks) s.on = false
        for (s in shards) if (!s.harmless) s.on = false
        if (wrath == WS.ATTACK) attackT = max(attackT, 1.2f)
        lastPz = g.player.z
    }

    // ------------------------------------------------------------------ events from the course
    fun fire(tr: Trigger) {
        val p = g.player
        when (tr.event) {
            Ev.PHASE -> phase = tr.text2.toIntOrNull() ?: phase
            Ev.WARNING -> if (!warned) {
                warned = true
                charge = 1f
                g.fx.banner("THE SORCERER AWAKENS!", "HIS WRATH IS COMING — BE READY", 0xFFE070FF.toInt(), 2.4f, true)
                g.platform.sound(Sfx.WARNING, 0.8f, 0.9f); g.platform.sound(Sfx.EXPLODE, 0.6f, 0.6f); g.platform.haptic(true)
                g.flashWhite = max(g.flashWhite, 0.25f); g.shake = max(g.shake, 0.35f)
                strayT = 1.2f
            }
            Ev.WRATH -> if (wrath == WS.NONE) {
                wrath = WS.RISE; wrathT = 0f; mode = 0; warned = true
                g.fx.banner("THE SORCERER'S WRATH!", "SURVIVE HIS MAGIC — LIGHT THE ${g.world.runes.size} STAR RUNES!", 0xFFFF70D0.toInt(), 2.6f, true)
                g.platform.sound(Sfx.ROAR, 0.9f, 1.25f); g.platform.sound(Sfx.WARNING, 0.8f, 1.0f); g.platform.haptic(true)
                g.shake = max(g.shake, 0.5f); g.flashWhite = max(g.flashWhite, 0.3f)
                g.fx.burst(p.x, p.y + 3f, p.z + 4f, 30, PK.SPARK, 0xFFE070FF.toInt(), 6f, 0.14f, 0.8f)
            }
            Ev.WRATH_MODE -> { mode = tr.text2.toIntOrNull() ?: mode; if (wrath == WS.ATTACK) attackT = min(attackT, 0.6f) }
            Ev.WRATH_END -> if (active) {
                val n = runesLit
                end("YOU SURVIVED HIS WRATH!", "$n OF ${g.world.runes.size} STAR RUNES LIT  •  +${500 + 300 * n}", 500 + 300 * n)
            }
            Ev.GATE -> if (gateT < 0f) awakenGate()
        }
    }

    private fun end(title: String, sub: String, bonus: Int) {
        wrath = WS.DONE; wrathT = 0f
        for (s in shocks) if (s.on) fizzle(s)
        for (s in shards) if (s.on && !s.landed) s.on = false
        val p = g.player
        g.fx.banner(title, sub, 0xFF7FFFA0.toInt(), 2.4f)
        g.addScore(bonus, p.x, p.y + 2.6f, p.z, "WRATH BROKEN")
        g.platform.sound(Sfx.CHECKPOINT, 1f, 1.1f); g.platform.sound(Sfx.WIN, 0.5f, 1.3f)
        g.fx.confetti(p.x, p.y + 2.2f, p.z + 0.5f, 36)
        if (g.cam.project(p.x, p.y + 1f, p.z)) for (k in 0 until 8) g.fx.fly(FK.COIN, g.cam.sx, g.cam.sy, g.hud.coinIconX(), g.hud.coinIconY(), Tune.COIN_VALUE, k * 0.06f, 0.75f)
    }

    // ------------------------------------------------------------------ update
    /** [dt]: game time (visuals); [k]: the hazard clock's rate (the hourglass slows it). */
    fun update(dt: Float, k: Float) {
        val hdt = dt * k
        val p = g.player
        charge = max(0f, charge - dt * 1.2f)
        flinch = max(0f, flinch - dt * 1.3f)
        gateFlare = max(0f, gateFlare - dt * 0.8f)
        if (gateT >= 0f) gateT += dt
        // the sky follows the story: his magic darkens it, the gate's light warms it at the end
        val gloomTo = when {
            active -> 0.55f
            wrath == WS.DONE && phase <= 6 -> 0.2f
            phase == 5 || (warned && wrath == WS.NONE) -> 0.28f
            phase == 7 -> 0.15f
            else -> 0f
        }
        gloom = approach(gloom, gloomTo, dt * 0.35f)
        gold = approach(gold, when { gateT >= 0f -> 1f; phase >= 9 -> 0.6f; phase == 8 -> 0.3f; else -> 0f }, dt * 0.4f)
        rage = approach(rage, if (active) 1f else if (warned && wrath == WS.NONE) 0.35f else 0f, dt * (if (active) 0.9f else 0.5f))
        updateWrath(dt, hdt)
        updateRollers(hdt)
        updateSpirits(hdt)
        updateHands()
        updateCurses()
        updateShocks(hdt)
        updateShards(hdt)
        updateRunes(dt)
        lastPz = p.z
    }

    private fun playing() = g.state == GS.PLAY && g.player.state == PS.NORMAL
    private fun inSanctuary(z: Float) = g.world.sanctuaries.any { z >= it.z0 - 0.5f && z <= it.z1 + 0.5f }

    private fun updateWrath(dt: Float, hdt: Float) {
        wrathT += dt
        val p = g.player
        when (wrath) {
            WS.NONE -> {
                // the magical warning: stray shards crash onto the islands beside the path (they never hit the boy)
                if (warned && playing()) {
                    strayT -= hdt
                    if (strayT <= 0f) { strayT = rng.f(2.6f, 4.2f); castShard(true) }
                }
            }
            WS.RISE -> {
                g.rumble = max(g.rumble, 0.25f * (1f - wrathT / RISE_T))
                // (his opening move is the Guardian's grab: his spells start at the course's first WRATH_MODE trigger)
                if (wrathT >= RISE_T) { wrath = WS.ATTACK; wrathT = 0f; attackT = 0.4f }
            }
            WS.STUN -> if (wrathT >= STUN_T) { wrath = WS.ATTACK; wrathT = 0f; attackT = 0.8f }
            WS.ATTACK -> {
                if (!playing() || inSanctuary(p.z)) return
                attackT -= hdt
                // the staff glows before a shockwave (the warning)
                if (mode == 1 && attackT < SHOCK_CHARGE) charge = max(charge, 1f - attackT / SHOCK_CHARGE)
                if (attackT <= 0f) when (mode) {
                    1 -> { castShock(); if (attackT <= 0f) attackT = SHOCK_EVERY }
                    3 -> { castShard(false); attackT = SHARD_EVERY + rng.f(-0.15f, 0.2f) }
                    else -> attackT = 1f
                }
                // no shockwave or shard while his claw is grabbing at the boy (one threat at a time)
                if (g.grabs.cinematic) attackT = max(attackT, 0.9f)
            }
        }
    }

    // ------------------------------------------------------------------ the floor
    /** Top of the floor under (x, z) near the path's level (NaN where there is none). */
    fun floorAt(x: Float, z: Float): Float {
        val r = floor(z).toInt()
        val row = g.world.row(r) ?: return Float.NaN
        val lvl = g.world.levelAt(r)
        var best = Float.NaN
        for (b in row) {
            if (!b.collides() || b.type == BT.TRAP) continue
            if (x < b.x0 - 0.05f || x > b.x1 + 0.05f || b.y1 < lvl - 2.5f || b.y1 > lvl + 2.5f) continue
            if (best.isNaN() || b.y1 > best) best = b.y1
        }
        return best
    }

    /** Solid floor right across the path in row [r] (a shockwave rolls over it). */
    private fun floorAcross(r: Int): Boolean {
        val px = g.world.pathXAt(r)
        val lvl = g.world.levelAt(r)
        val row = g.world.row(r) ?: return false
        return (-1..1).all { l -> row.any { it.collides() && it.type != BT.TRAP && abs(it.x - (px + l)) < 0.5f && abs(it.y1 - lvl) < 0.3f } }
    }

    // ------------------------------------------------------------------ rolling stone creatures
    private fun updateRollers(dt: Float) {
        val p = g.player
        for (r in rollers) {
            val sp = r.spot
            r.t += dt
            when (r.state) {
                0 -> if (playing() && sp.z - p.z < ROLL_AT && sp.z - p.z > 3f) {
                    val row = floor(sp.z).toInt()
                    val px = g.world.pathXAt(row)
                    r.lane = if (sp.x == SpellSpot.AIM) clamp(round(p.x - g.world.pathXAt(floor(p.z).toInt())), -1f, 1f) else sp.x
                    r.x = px + r.lane; r.z = sp.z + 0.5f
                    val gy = floorAt(r.x, r.z)
                    if (gy.isNaN()) { r.state = 4; continue }
                    r.y = gy + 6f; r.vy = -2f; r.state = 1; r.t = 0f; r.show = 0f
                    g.platform.sound(Sfx.WHOOSH, 0.6f, 0.7f)
                    g.fx.burst(r.x, r.y, r.z, 12, PK.SPARK, 0xFFC890FF.toInt(), 3f, 0.1f, 0.5f)
                }
                1 -> {
                    r.show = min(1f, r.show + dt * 4f)
                    val gy = floorAt(r.x, r.z)
                    r.vy -= 30f * dt; r.y += r.vy * dt
                    if (!gy.isNaN() && r.y <= gy) {
                        r.y = gy; r.state = 2; r.t = 0f
                        val d = len3(p.x - r.x, p.y - r.y, p.z - r.z)
                        g.platform.sound(Sfx.STOMP, clamp01(1.2f - d / 16f), 0.8f)
                        g.shake = max(g.shake, 0.3f * clamp01(1.2f - d / 12f))
                        g.fx.dust(r.x, r.y, r.z, 10, 0xFFC8BCDA.toInt())
                    }
                    if (r.y < sp.y - 10f) r.state = 4
                }
                2 -> {
                    val v = sp.period
                    r.z -= v * dt
                    r.rot += v * dt / ROLL_R
                    val row = floor(r.z).toInt()
                    r.x = approach(r.x, g.world.pathXAt(row) + r.lane, dt * 2f)
                    val gy = floorAt(r.x, r.z)
                    when {
                        gy.isNaN() || gy < r.y - 0.7f -> { r.state = 5; r.vy = 0f; r.t = 0f }
                        gy > r.y + 0.6f -> smash(r, false)
                        else -> r.y = approach(r.y, gy, dt * 6f)
                    }
                    // it grinds along: pebbles and a rumble when it is near
                    val d = abs(p.z - r.z)
                    if (d < 10f) g.rumble = max(g.rumble, 0.1f * (1f - d / 10f))
                    if (g.fx.rng.f() < dt * 16f) {
                        val q = g.fx.spawn()
                        q.x = r.x + g.fx.rng.f(-0.3f, 0.3f); q.y = r.y + 0.05f; q.z = r.z + 0.3f
                        q.vx = g.fx.rng.f(-1f, 1f); q.vy = g.fx.rng.f(0.5f, 2f); q.vz = g.fx.rng.f(0f, 1.5f)
                        q.life = 0.5f; q.maxLife = 0.5f; q.size = g.fx.rng.f(0.05f, 0.1f); q.color = 0xFF9C90B4.toInt(); q.kind = PK.SHARD; q.gravity = 18f
                    }
                    if (r.state == 2 && playing()) rollerContact(r)
                    if (r.state == 2 && r.z < p.z - 6f) { r.state = 4; r.t = 0f }
                }
                3, 4 -> r.show = max(0f, r.show - dt * (if (r.state == 3) 4f else 2f))
                5 -> {
                    r.vy -= 30f * dt; r.y += r.vy * dt; r.z -= sp.period * dt; r.rot += sp.period * dt / ROLL_R
                    r.show = max(0f, r.show - dt * 0.7f)
                }
            }
        }
    }

    private fun rollerContact(r: Roller) {
        val p = g.player
        if (abs(p.x - r.x) > ROLL_R + Tune.RADIUS - 0.05f || abs(p.z - r.z) > ROLL_R + Tune.RADIUS) return
        val top = r.y + 2f * ROLL_R
        if (p.y > top + 0.02f || p.y + Tune.HEIGHT < r.y) return
        // coming down onto it: it crumbles under his feet and bounces him up
        if (p.vy < -0.5f && p.y > top - 0.4f) {
            smash(r, true)
            p.vy = 9f; p.grounded = false; p.jumping = true; p.jumpT = 0f
            g.addScore(150, r.x, r.y + 1.6f, r.z, "SMASH!")
            g.platform.sound(Sfx.BOUNCE, 1f, 1.1f)
            return
        }
        if (p.invuln > 0f) return
        val shielded = g.shieldOn
        smash(r, false)
        hits++
        g.hurt("roller", null)
        if (!shielded) { p.vz = -3.2f; p.vy = 6f; p.vx = 0f; p.grounded = false; p.jumping = false; g.swipe.stop() }
    }

    private fun smash(r: Roller, stomped: Boolean) {
        r.state = 3; r.t = 0f
        if (stomped) smashed++
        g.fx.burst(r.x, r.y + ROLL_R, r.z, 22, PK.SHARD, 0xFF9C90B4.toInt(), 5f, 0.16f, 0.8f, 18f)
        g.fx.burst(r.x, r.y + ROLL_R, r.z, 12, PK.SPARK, 0xFFD890FF.toInt(), 3.5f, 0.1f, 0.5f)
        g.fx.dust(r.x, r.y, r.z, 8, 0xFFC8BCDA.toInt())
        g.platform.sound(Sfx.CRUMBLE, 0.9f, 1.1f)
        g.shake = max(g.shake, 0.25f)
    }

    // ------------------------------------------------------------------ the teleporting spirit
    private fun updateSpirits(dt: Float) {
        val p = g.player
        for (s in spirits) {
            val sp = s.spot
            s.t += dt
            when (s.state) {
                0 -> if (playing() && p.z > sp.z - 6f && p.z < sp.z + sp.len) hop(s)
                1 -> if (s.t >= SPIRIT_MARK) {
                    s.state = 2; s.t = 0f
                    val d = len3(p.x - s.x, p.y - s.y, p.z - s.z)
                    g.platform.sound(Sfx.WHOOSH, clamp01(1.1f - d / 14f) * 0.8f, 1.8f)
                    g.fx.burst(s.x, s.y + 1f, s.z, 14, PK.SPARK, 0xFF9FE8FF.toInt(), 3f, 0.1f, 0.5f)
                }
                2 -> { s.show = clamp01(s.t / SPIRIT_IN); if (s.t >= SPIRIT_IN) { s.state = 3; s.t = 0f } }
                3 -> {
                    s.show = 1f
                    if (playing()) spiritContact(s)
                    if (s.state == 3 && s.t >= SPIRIT_STAY) { s.state = 4; s.t = 0f }
                }
                4 -> {
                    s.show = max(0f, 1f - s.t / SPIRIT_OUT)
                    if (s.t >= SPIRIT_OUT) { if (playing() && p.z < sp.z + sp.len - 3f && p.z > sp.z - 6f) hop(s) else if (p.z >= sp.z + sp.len - 3f) s.state = 5 }
                }
            }
        }
    }

    /** The spirit blinks ahead of the boy: a spot on the path (his lane most of the time) where he will be in a moment. */
    private fun hop(s: Spirit) {
        val p = g.player
        val sp = s.spot
        val ahead = max(5.5f, max(0f, p.vz) * 1.25f + 0.5f)
        val row = floor(p.z + ahead).toInt()
        if (row > sp.z + sp.len) { s.state = 5; return }
        val px = g.world.pathXAt(row)
        val mine = clamp(round(p.x - px), -1f, 1f)
        val order = if (rng.f() < 0.6f) floatArrayOf(mine, mine - 1f, mine + 1f) else floatArrayOf(mine + (if (rng.f() < 0.5f) -1f else 1f), mine, -mine)
        for (l in order) {
            if (l < -1f || l > 1f) continue
            val gy = floorAt(px + l, row + 0.5f)
            if (gy.isNaN() || abs(gy - g.world.levelAt(row)) > 0.3f) continue
            s.fx = s.x; s.fy = s.y; s.fz = s.z
            s.x = px + l; s.y = gy; s.z = row + 0.5f
            s.state = 1; s.t = 0f; s.show = 0f
            return
        }
        s.state = 4; s.t = SPIRIT_OUT - 0.15f
    }

    private fun spiritContact(s: Spirit) {
        val p = g.player
        if (p.invuln > 0f) return
        if (abs(p.x - s.x) > 0.34f + Tune.RADIUS || abs(p.z - s.z) > 0.34f + Tune.RADIUS) return
        if (p.y > s.y + 1.7f || p.y + Tune.HEIGHT < s.y + 0.25f) return
        hits++
        g.hurt("spirit", null)
        g.fx.burst(s.x, s.y + 1f, s.z, 20, PK.SPARK, 0xFF9FE8FF.toInt(), 4f, 0.12f, 0.6f)
        s.state = 4; s.t = 0f
    }

    // ------------------------------------------------------------------ magical hands
    private fun updateHands() {
        val p = g.player
        val T = g.hazT
        val busy = HAND_WARN + HAND_REACH + HAND_HOLD + HAND_BACK
        for (h in hands) {
            val sp = h.spot
            val P = sp.period
            val c = ((T + sp.phase) % P + P) % P
            val c0 = P - busy
            val row = floor(sp.z).toInt()
            val px = g.world.pathXAt(row)
            h.x = px + sp.x; h.z = sp.z + 0.5f; h.y = sp.y
            h.ex = px + sp.x * 2.35f
            h.warn = if (c >= c0 && c < c0 + HAND_WARN) (c - c0) / HAND_WARN else 0f
            val cr = c - c0 - HAND_WARN
            h.reach = when {
                cr < 0f -> 0f
                cr < HAND_REACH -> easeOutCubic(cr / HAND_REACH)
                cr < HAND_REACH + HAND_HOLD -> 1f
                else -> 1f - smooth((cr - HAND_REACH - HAND_HOLD) / HAND_BACK)
            }
            h.danger = cr >= HAND_REACH * 0.6f && cr < HAND_REACH + HAND_HOLD
            val near = abs(p.z - h.z) < 16f
            val out = cr >= HAND_REACH * 0.8f && cr < HAND_REACH + HAND_HOLD
            if (out && !h.wasOut && near) {
                // the slam
                val d = len3(p.x - h.x, p.y - h.y, p.z - h.z)
                g.platform.sound(Sfx.STOMP, clamp01(1.1f - d / 14f) * 0.8f, 1.2f)
                g.fx.burst(h.x, h.y + 0.1f, h.z, 10, PK.SPARK, 0xFFB070FF.toInt(), 3f, 0.1f, 0.45f)
                g.fx.dust(h.x, h.y, h.z, 4, 0xFFB8A0D8.toInt())
            }
            if (cr >= 0f && cr < 0.05f && near) g.platform.sound(Sfx.WHOOSH, 0.35f, 0.75f)
            h.wasOut = out
            if (h.danger && playing()) handContact(h)
        }
    }

    private fun handContact(h: Hand) {
        val p = g.player
        if (p.invuln > 0f) return
        if (abs(p.x - h.x) > 0.5f + Tune.RADIUS - 0.08f || abs(p.z - h.z) > 0.6f + Tune.RADIUS) return
        if (p.y > h.y + 1.1f) return
        hits++
        val shielded = g.shieldOn
        g.hurt("hand", null)
        g.fx.burst(p.x, p.y + 0.8f, p.z, 14, PK.SPARK, 0xFFB070FF.toInt(), 3.5f, 0.1f, 0.5f)
        if (!shielded) { p.vz = -3.4f; p.vy = 6f; p.vx = 0f; p.grounded = false; p.jumping = false; g.swipe.stop() }
    }

    // ------------------------------------------------------------------ cursed blocks
    private fun updateCurses() {
        if (cursed.isEmpty()) return
        val T = g.hazT
        val p = g.player
        for (b in cursed) {
            if (abs(b.z - p.z) > 40f) continue
            val c = ((T + b.phase) % CURSE_PERIOD + CURSE_PERIOD) % CURSE_PERIOD
            val warnAt = CURSE_PERIOD - CURSE_GONE - CURSE_WARN
            val goneAt = CURSE_PERIOD - CURSE_GONE
            when {
                c >= goneAt -> if (b.visible) {
                    b.visible = false
                    g.fx.burst(b.x, b.y + 0.8f, b.z + 0.5f, 10, PK.SPARK, 0xFFE070FF.toInt(), 2.5f, 0.1f, 0.45f)
                    g.fx.dust(b.x, b.y + 0.6f, b.z + 0.5f, 3, 0xFFB890E8.toInt())
                }
                c >= warnAt -> {
                    // the curse takes hold: it glows purple and trembles
                    if (!b.visible && !insideBlock(b)) { b.visible = true; b.alpha = 1f }
                    b.color = BC.MAGENTA; b.shake = max(b.shake, 0.04f)
                    b.flash = max(b.flash, 0.35f * (c - warnAt) / CURSE_WARN)
                }
                else -> {
                    if (!b.visible && !insideBlock(b)) { b.visible = true; b.alpha = 0.4f; b.flash = 0.5f }
                    b.alpha = min(1f, b.alpha + 0.05f)
                    b.color = b.origColor
                }
            }
        }
    }

    private fun insideBlock(b: Block): Boolean {
        val p = g.player
        return p.x + Tune.RADIUS > b.x0 && p.x - Tune.RADIUS < b.x1 && p.y + Tune.HEIGHT > b.y && p.y < b.y1 &&
            p.z + Tune.RADIUS > b.z && p.z - Tune.RADIUS < b.z1
    }

    // ------------------------------------------------------------------ shockwaves
    private fun castShock() {
        val p = g.player
        val row = floor(p.z + SHOCK_AHEAD).toInt()
        if (!floorAcross(row)) { attackT = 0.3f; return }
        for (s in shocks) if (!s.on) {
            s.on = true; s.t = 0f; s.show = 0f
            s.z = row + 0.5f; s.x = g.world.pathXAt(row); s.y = g.world.levelAt(row)
            charge = 1f
            g.platform.sound(Sfx.EXPLODE, 0.55f, 0.7f); g.platform.sound(Sfx.WHOOSH, 0.7f, 0.6f)
            g.shake = max(g.shake, 0.22f)
            g.fx.burst(s.x, s.y + 0.3f, s.z, 18, PK.SPARK, 0xFFFF80E0.toInt(), 5f, 0.12f, 0.5f)
            return
        }
    }

    private fun fizzle(s: Shock) {
        s.on = false
        g.fx.burst(s.x, s.y + 0.3f, s.z, 14, PK.SPARK, 0xFFFF80E0.toInt(), 3f, 0.1f, 0.4f)
    }

    private fun updateShocks(dt: Float) {
        val p = g.player
        for (s in shocks) if (s.on) {
            s.t += dt
            s.show = min(1f, s.show + dt * 5f)
            val before = s.z - lastPz
            s.z -= SHOCK_SPEED * dt
            val row = floor(s.z).toInt()
            s.x = approach(s.x, g.world.pathXAt(row), dt * 3f)
            if (!floorAcross(row)) { fizzle(s); continue }
            s.y = approach(s.y, g.world.levelAt(row), dt * 8f)
            val after = s.z - p.z
            // it sweeps the path as it passes under him: only a jump clears it
            if (playing() && before >= -0.05f && after <= 0.05f && p.invuln <= 0f && p.y < s.y + 0.5f && abs(p.x - s.x) < 1.9f) {
                hits++
                g.hurt("shockwave", null)
                if (!g.shieldOn) { p.vy = 7f; p.grounded = false; p.jumping = false }
                g.fx.burst(p.x, p.y + 0.4f, p.z, 14, PK.SPARK, 0xFFFF80E0.toInt(), 4f, 0.1f, 0.5f)
            }
            if (s.z < p.z - 4f) s.on = false
        }
    }

    // ------------------------------------------------------------------ shards
    /** A crystal shard falls onto a marked spot: where the boy will be in a moment (or [harmless]: onto an island beside the path). */
    private fun castShard(harmless: Boolean) {
        val p = g.player
        val ahead = max(0f, p.vz) * SHARD_FALL + rng.f(0.5f, 1.8f)
        val row = floor(p.z + (if (harmless) rng.f(5f, 10f) else ahead)).toInt()
        val px = g.world.pathXAt(row)
        val ground: Float
        val tx: Float
        if (harmless) {
            tx = px + (if (rng.f() < 0.5f) -1f else 1f) * rng.f(3.2f, 4.4f)
            ground = g.world.levelAt(row) - 0.1f
        } else {
            var lane = clamp(round(p.x - px), -1f, 1f)
            if (rng.f() < 0.35f) lane += if (rng.f() < 0.5f) -1f else 1f
            lane = clamp(lane, -1f, 1f)
            tx = px + lane
            val gy = floorAt(tx, row + 0.5f)
            if (gy.isNaN() || abs(gy - g.world.levelAt(row)) > 0.3f) { attackT = 0.35f; return }
            ground = gy
        }
        for (s in shards) if (!s.on) {
            s.on = true; s.landed = false; s.harmless = harmless; s.t = 0f
            s.x = tx; s.z = row + 0.5f; s.ground = ground; s.y = ground + 11f
            charge = max(charge, 0.7f)
            if (!harmless) g.platform.sound(Sfx.WHOOSH, 0.5f, 1.5f)
            return
        }
    }

    private fun updateShards(dt: Float) {
        val p = g.player
        for (s in shards) if (s.on) {
            s.t += dt
            if (!s.landed) {
                val u = clamp01(s.t / SHARD_FALL)
                s.y = s.ground + 11f * (1f - u * u)
                if (u >= 1f) {
                    s.landed = true; s.t = 0f; s.y = s.ground
                    val d = len3(p.x - s.x, p.y - s.ground, p.z - s.z)
                    g.fx.burst(s.x, s.ground + 0.3f, s.z, 18, PK.SHARD, 0xFFB8E8FF.toInt(), 4.5f, 0.12f, 0.6f, 16f)
                    g.fx.burst(s.x, s.ground + 0.2f, s.z, 12, PK.SPARK, 0xFFE0A0FF.toInt(), 3.5f, 0.1f, 0.45f)
                    g.platform.sound(Sfx.CRACK, clamp01(1.1f - d / 14f) * 0.8f, 1.3f)
                    g.shake = max(g.shake, (if (s.harmless) 0.18f else 0.25f) * clamp01(1.2f - d / 10f))
                    if (!s.harmless && playing() && p.invuln <= 0f && abs(p.x - s.x) < 0.72f && abs(p.z - s.z) < 0.72f &&
                        p.y < s.ground + 1.3f && p.y > s.ground - 1f) { hits++; g.hurt("shard", null) }
                }
            } else if (s.t > 0.7f) s.on = false
        }
    }

    // ------------------------------------------------------------------ star runes
    private fun updateRunes(dt: Float) {
        val p = g.player
        for (r in g.world.runes) {
            if (r.lit) { r.litT += dt; continue }
            if (playing() && abs(p.x - r.x) < 0.75f && abs(p.z - r.z) < 0.65f && abs(p.y - r.y) < 1.2f) light(r)
        }
    }

    private fun light(r: Rune) {
        r.lit = true; r.litT = 0f
        val p = g.player
        val n = runesLit; val total = g.world.runes.size
        flinch = 1f
        g.flashWhite = max(g.flashWhite, 0.35f); g.shake = max(g.shake, 0.35f); g.kick(1.2f)
        g.platform.sound(Sfx.STAR, 1f, 0.9f); g.platform.sound(Sfx.TARGET, 0.8f, 1.2f); g.platform.sound(Sfx.EXPLODE, 0.5f, 1.6f)
        g.platform.haptic(true)
        g.fx.burst(r.x, r.y + 0.3f, r.z, 36, PK.STAR, 0xFFFFE680.toInt(), 6f, 0.16f, 0.9f, 0f, 4f)
        g.addScore(300, r.x, r.y + 2.2f, r.z, "STAR RUNE!")
        if (g.cam.project(r.x, r.y + 0.3f, r.z)) g.fx.ring(g.cam.sx, g.cam.sy, 0xFFFFE680.toInt(), 30f * g.hud.s, 300f * g.hud.s, 0.6f, 12f * g.hud.s)
        for (s in shocks) if (s.on) fizzle(s)
        for (s in shards) if (s.on && !s.landed && !s.harmless) s.on = false
        if (!active) return
        if (n >= total) {
            end("THE SORCERER IS DRIVEN BACK!", "ALL $total STAR RUNES LIT  •  +1500", 1500)
            g.slowFor(0.5f, 0.6f)
        } else {
            wrath = WS.STUN; wrathT = 0f
            g.fx.banner("STAR RUNE LIT!  $n/$total", "THE SORCERER REELS — KEEP RUNNING!", 0xFFFFE14A.toInt(), 1.8f)
        }
        if (p.state == PS.NORMAL) p.celebrateT = 0f
    }

    // ------------------------------------------------------------------ the Celestial Gate's awakening
    private fun awakenGate() {
        gateT = 0f
        val p = g.player
        g.fx.banner("THE CELESTIAL GATE AWAKENS!", "RUN INTO THE STAR PORTAL!", 0xFFFFE680.toInt(), 2.6f)
        g.slowFor(0.55f, 1.1f)
        g.flashWhite = max(g.flashWhite, 0.45f); g.shake = max(g.shake, 0.3f)
        g.platform.sound(Sfx.PORTAL, 1f, 0.8f); g.platform.sound(Sfx.STAR, 1f, 0.7f); g.platform.sound(Sfx.WIN, 0.4f, 1.5f)
        g.platform.haptic(true)
        g.fx.burst(p.x, p.y + 2f, p.z + 3f, 40, PK.STAR, 0xFFFFE680.toInt(), 7f, 0.18f, 1.1f)
    }

    // ------------------------------------------------------------------ for the autopilot
    /**
     * Spots about to be dangerous on the path: (x, z, from, until) in seconds from now: shards and bolts about to land,
     * the spirit's marked spot, a roller's path, a hand slamming its lane.
     */
    fun dangers(out: ArrayList<FloatArray>) {
        out.clear()
        for (s in shards) if (s.on && !s.landed && !s.harmless) {
            val left = (SHARD_FALL - s.t) / max(0.2f, g.hazK)
            out.add(floatArrayOf(s.x, s.z, left - 0.25f, left + 0.1f))
        }
        for (s in spirits) when (s.state) {
            1 -> out.add(floatArrayOf(s.x, s.z, (SPIRIT_MARK - s.t) / max(0.2f, g.hazK), (SPIRIT_MARK + SPIRIT_IN + SPIRIT_STAY - s.t) / max(0.2f, g.hazK)))
            2 -> out.add(floatArrayOf(s.x, s.z, 0f, (SPIRIT_IN + SPIRIT_STAY - s.t) / max(0.2f, g.hazK)))
            3 -> out.add(floatArrayOf(s.x, s.z, 0f, (SPIRIT_STAY - s.t) / max(0.2f, g.hazK)))
        }
    }
}
