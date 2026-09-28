package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object RS { const val NONE = 0; const val APPEAR = 1; const val RUN = 2; const val CAUGHT = 3; const val ESCAPED = 4 }

/**
 * Level 5's Runaway Relic: the glowing golden creature from the design. Halfway through the level it pops up a
 * few blocks ahead (CHASE & COLLECT! 0/1) and runs off along the course, rows [World.relicZ0] .. [World.relicZ1],
 * at a steady pace a little slower than the boy's run: it never teleports and never waits, it just follows the
 * path (hopping gaps and steps in readable arcs), so good running, clean jumps and the speed pads catch it and a
 * stop or a fall lets it get away. Catching it pays a bonus (coins, gems, score and the relic itself in the
 * Prize Vault); missing it costs nothing: it escapes at the end of its route and the level carries on.
 */
class RelicChase(val g: Game) {
    var state = RS.NONE
    var x = 0f; var y = 0f; var z = 0f
    /** Distance along the route (a z position). */
    private var s = 0f
    /** Level 6: a rainbow slide on its route, which it rides too (slower than the boy can), and how far down it is. */
    private var onSlide: Slide? = null
    private var ss = 0f
    var t = 0f
    /** Bounce of its gait and which way it faces (for the sprite). */
    var hop = 0f
    var sortKey = 0f
    /** 0..1 fade used when it appears and when it leaves. */
    var show = 0f
    val caught get() = state == RS.CAUGHT || caughtOnce
    private var caughtOnce = false
    /** Coins, gems and score the bonus pays. */
    val bonusCoins = 250; val bonusGems = 10; val bonusScore = 2500

    companion object {
        /**
         * Its pace (units per second): a little quicker than the boy's run (5.2), so plain running never catches
         * it; each speed pad on its route closes about three blocks and the lightning tool a lot more (a stop, a
         * fall or a wait loses them again). Using every pad on its route catches it near the end.
         */
        const val SPEED = 5.4f
        /** How far ahead of the boy it appears. */
        const val LEAD = 13f
        /** Its pace down a slide (the boy slides faster: a slide is where he catches up). */
        const val SLIDE_SPEED = 8.5f
        const val CATCH = 1.05f
    }

    fun reset() { state = RS.NONE; show = 0f; t = 0f; caughtOnce = false; onSlide = null }

    /** Continuing from a checkpoint before the relic's route puts the chase back (unless it was already caught). */
    fun resetAfter(cpZ: Float) {
        if (caughtOnce) return
        if (g.world.relicZ0 > 0 && cpZ < g.world.relicZ0) { state = RS.NONE; show = 0f; t = 0f; onSlide = null }
    }

    fun start() {
        if (state != RS.NONE || caughtOnce || g.world.relicZ1 <= g.world.relicZ0) return
        val p = g.player
        state = RS.APPEAR; t = 0f; onSlide = null
        s = max(p.z + LEAD, g.world.relicZ0.toFloat() + 0.5f)
        place()
        // Level 7: the golden sprite breaks loose and runs for it, leaving a trail of golden light to follow
        if (g.spec.enchanted) g.fx.banner("THE GOLDEN SPRITE ESCAPED!", "CATCH IT FOR A BONUS — FOLLOW ITS GOLDEN TRAIL", 0xFFFFE14A.toInt(), 2.3f)
        else g.fx.banner("CHASE & COLLECT!", "CATCH THE RUNAWAY RELIC FOR A BONUS", 0xFFFFE14A.toInt(), 2.1f)
        g.platform.sound(Sfx.STAR, 1f, 1.2f); g.platform.sound(Sfx.WHOOSH, 0.7f, 1.4f)
        g.fx.burst(x, y + 0.6f, z, 30, PK.STAR, 0xFFFFE680.toInt(), 5f, 0.16f, 0.8f)
        g.hud.relicShow()
    }

    /** Ground height of row [r] on the main path, or NaN where the path has a gap. */
    private fun groundAt(r: Int): Float {
        val row = g.world.row(r) ?: return Float.NaN
        val px = g.world.pathXAt(r)
        val lvl = g.world.levelAt(r)
        var best = Float.NaN
        for (b in row) {
            if (!b.collides() || b.type == BT.TRAP) continue
            if (abs(b.x - px) > 1.6f || abs(b.y1 - lvl) > 1.2f) continue
            if (best.isNaN() || abs(b.y1 - lvl) < abs(best - lvl)) best = b.y1
        }
        return best
    }

    /** Places it at route position [s]: on the path, hopping over gaps and steps in arcs (or down the slide it rides). */
    private fun place() {
        val sl = onSlide
        if (sl != null) {
            sl.point(ss, 0f, 0.05f, tmp, 0)
            x = tmp[0]; y = tmp[1]; z = tmp[2]; hop = 0f
            return
        }
        val r = floor(s).toInt()
        x = lerp(g.world.pathXAt(r), g.world.pathXAt(r + 1), s - r)
        var r0 = r; while (r0 > r - 8 && groundAt(r0).isNaN()) r0--
        var r1 = r + 1; while (r1 < r + 9 && groundAt(r1).isNaN()) r1++
        val y0 = groundAt(r0).let { if (it.isNaN()) g.world.levelAt(r0) else it }
        val y1 = groundAt(r1).let { if (it.isNaN()) g.world.levelAt(r1) else it }
        val a = r0 + 0.5f; val b = r1 + 0.5f
        val u = clamp01((s - a) / (b - a))
        val span = b - a
        // a gap or a step: one arc from ground to ground; otherwise a light bunny bounce
        val arc = if (span > 1.2f || abs(y1 - y0) > 0.3f) (0.6f + 0.35f * span + max(0f, y1 - y0) * 0.6f) * 4f * u * (1f - u) else 0f
        hop = if (arc > 0f) 0f else abs(sin(s * 3.1416f / 1.1f)) * 0.28f
        y = lerp(y0, y1, u) + arc + hop
        z = s
    }

    fun update(dt: Float) {
        if (state == RS.NONE) return
        t += dt
        val p = g.player
        when (state) {
            RS.APPEAR -> {
                show = min(1f, show + dt * 3f)
                // it looks back at the boy for a moment, then bolts
                if (t > 0.6f) { state = RS.RUN; t = 0f; g.platform.sound(Sfx.WHOOSH, 0.6f, 1.6f) }
                checkCatch()
            }
            RS.RUN -> {
                if (g.state == GS.PLAY && p.state != PS.CAUGHT) {
                    val sl = onSlide
                    if (sl != null) {
                        ss += SLIDE_SPEED * dt
                        // out of the slide's exit it carries on along the path from there
                        if (ss >= sl.length) { onSlide = null; s = sl.z1 + 0.3f }
                    } else {
                        val before = s
                        s += SPEED * dt
                        // a slide's mouth on its route: it rides the slide down
                        for (sl2 in g.world.slides) if (sl2.ride && sl2.z0 > before - 0.01f && sl2.z0 <= s + 0.5f &&
                            abs(sl2.x0 - g.world.pathXAt(floor(sl2.z0 - 0.5f).toInt())) < 1.6f) { onSlide = sl2; ss = 0f; break }
                    }
                }
                place()
                // a trail of sparkles behind it
                if (g.fx.rng.f() < dt * 40f) {
                    val q = g.fx.spawn()
                    q.x = x + g.fx.rng.f(-0.2f, 0.2f); q.y = y + 0.35f + g.fx.rng.f(-0.1f, 0.2f); q.z = z - 0.3f
                    q.vx = g.fx.rng.f(-0.3f, 0.3f); q.vy = g.fx.rng.f(0.2f, 1.2f); q.vz = -0.5f
                    q.life = g.fx.rng.f(0.4f, 0.8f); q.maxLife = q.life; q.size = g.fx.rng.f(0.05f, 0.11f)
                    q.color = if (g.fx.rng.f() < 0.5f) 0xFFFFE680.toInt() else 0xFFFFFFFF.toInt(); q.kind = PK.STAR
                }
                // Level 7: its golden footprints stay glowing on the path for a moment (the trail to follow)
                if (g.spec.enchanted) {
                    trailT -= dt
                    if (trailT <= 0f && hop < 0.05f && onSlide == null) {
                        trailT = 0.1f
                        val q = g.fx.spawn()
                        q.x = x + g.fx.rng.f(-0.08f, 0.08f); q.y = y - hop + 0.06f; q.z = z
                        q.vx = 0f; q.vy = 0.15f; q.vz = 0f
                        q.life = 1.6f; q.maxLife = 1.6f; q.size = 0.13f; q.color = 0xFFFFE070.toInt(); q.kind = PK.STAR
                    }
                }
                checkCatch()
                if (state == RS.RUN && s >= g.world.relicZ1) escape()
            }
            RS.CAUGHT -> show = max(0f, show - dt * 2.5f)
            RS.ESCAPED -> {
                // it leaps away toward the fortress, shrinking into a spark
                z += dt * 9f; y += dt * (7f - t * 6f); show = max(0f, show - dt * 0.8f)
            }
        }
    }

    private fun checkCatch() {
        val p = g.player
        if (g.state != GS.PLAY || (p.state != PS.NORMAL && p.state != PS.LOOP && p.state != PS.SLIDE)) return
        val d = len3(p.x - x, p.y + 0.7f - (y + 0.35f), p.z - z)
        if (d < CATCH) capture()
    }

    /** The reward: a short burst of light, coins and gems flying to the HUD, the bonus score, then back to play. */
    private fun capture() {
        state = RS.CAUGHT; t = 0f; caughtOnce = true
        val p = g.player
        g.slowFor(0.5f, 0.55f)
        g.flashWhite = max(g.flashWhite, 0.4f); g.shake = max(g.shake, 0.3f); g.kick(1.6f)
        g.platform.sound(Sfx.STAR, 1f, 1f); g.platform.sound(Sfx.TARGET, 0.9f, 1.3f); g.platform.sound(Sfx.WIN, 0.5f, 1.4f)
        g.platform.haptic(true)
        g.fx.burst(x, y + 0.5f, z, 60, PK.STAR, 0xFFFFE680.toInt(), 7f, 0.2f, 1.1f)
        g.fx.burst(x, y + 0.5f, z, 26, PK.SPARK, 0xFFFFFFFF.toInt(), 5f, 0.12f, 0.6f)
        g.fx.confetti(x, y + 1.2f, z, 30)
        g.addScore(bonusScore, p.x, p.y + 2.6f, p.z, "RELIC BONUS")
        g.fx.banner(if (g.spec.enchanted) "CHASE COMPLETE!" else "RELIC CAPTURED!", "+$bonusCoins COINS  •  +$bonusGems GEMS  •  +${bonusScore}", 0xFFFFE14A.toInt(), 1.9f)
        g.hud.relicCaught()
        val sx: Float; val sy: Float
        if (g.cam.project(x, y + 0.5f, z)) { sx = g.cam.sx; sy = g.cam.sy } else { sx = g.hud.cx(); sy = g.hud.cy() }
        g.fx.ring(sx, sy, 0xFFFFE680.toInt(), 30f * g.hud.s, 320f * g.hud.s, 0.7f, 14f * g.hud.s)
        // the coins and gems fly to the wallet on the HUD (they are added as they arrive)
        for (k in 0 until 10) g.fx.fly(FK.COIN, sx + g.fx.rng.f(-40f, 40f) * g.hud.s, sy + g.fx.rng.f(-30f, 30f) * g.hud.s,
            g.hud.coinIconX(), g.hud.coinIconY(), bonusCoins / 10, k * 0.05f, 0.7f)
        for (k in 0 until bonusGems) g.fx.fly(FK.GEM, sx, sy, g.hud.gemIconX(), g.hud.gemIconY(), 1, 0.2f + k * 0.05f, 0.75f)
    }

    private fun escape() {
        state = RS.ESCAPED; t = 0f
        g.platform.sound(Sfx.WHOOSH, 0.8f, 1.5f)
        g.fx.burst(x, y + 0.5f, z, 24, PK.STAR, 0xFFFFE680.toInt(), 5f, 0.14f, 0.7f)
        g.fx.toast(if (g.spec.enchanted) "THE GOLDEN SPRITE GOT AWAY!" else "THE RELIC GOT AWAY!", "NO PROBLEM — KEEP GOING", 0xFFFFD27A.toInt(), 2.2f)
        g.hud.relicEscaped()
    }

    private val tmp = FloatArray(3)
    private var trailT = 0f

    /** For the HUD: 1 when caught. */
    val count get() = if (caught) 1 else 0
    /** Shown on the HUD from the moment it appears. */
    val active get() = state != RS.NONE || caughtOnce
}
