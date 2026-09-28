package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Level 7's hidden things and the world answering the boy:
 *
 *  - Secret routes: a glowing rune tile beside the path (a switch) or a hidden ledge below it. Finding one ("SECRET
 *    ROUTE FOUND!") raises its blue blocks out of the clouds (the blocks with its shortcutId) and shows its hidden
 *    coins; each route has a treasure chest and rejoins the path further on (a shortcut). Both are optional.
 *  - Treasure chests: touching one throws its lid open (coins and gems fly to the wallet).
 *  - Hidden coins show themselves, with a sparkle, as the boy comes near.
 *  - The world reacts (subtly): blocks light up under his feet, fireflies and petals scatter from the towers, crystals
 *    and trees as he passes, bridges dip under his steps, the distant Celestial Gate flares when a checkpoint lights.
 */
class Wonders(val g: Game) {
    private val rng = Rng(5151)
    /** Scenery that already scattered its fireflies (by index into the world's decos). */
    private var stirred = BooleanArray(0)
    private var petalT = 0f
    var secretsFound = 0
    var chestsOpened = 0

    fun reset() {
        stirred = BooleanArray(g.world.decos.size)
        for (s in g.world.secrets) s.found = false
        for (c in g.world.chests) { c.open = false; c.openT = 0f }
        for (c in g.world.coins) c.reveal = 0f
        secretsFound = 0; chestsOpened = 0; petalT = 0f
    }

    /** Continuing from a checkpoint: scenery after it can scatter its fireflies again (secrets and chests stay found). */
    fun resetAfter(cpZ: Float) {
        for ((i, d) in g.world.decos.withIndex()) if (d.z > cpZ && i < stirred.size) stirred[i] = false
    }

    fun update(dt: Float) {
        val p = g.player
        val playing = g.state == GS.PLAY && p.state == PS.NORMAL
        for (s in g.world.secrets) if (!s.found && playing && abs(p.x - s.x) < s.r && abs(p.z - s.z) < s.r && p.y > s.y - 0.6f && p.y < s.y + 1.4f &&
            (p.grounded || !s.switch)) find(s)
        for (c in g.world.chests) {
            if (c.open) { c.openT += dt; continue }
            if (playing && abs(p.x - c.x) < 0.75f && abs(p.z - c.z) < 0.75f && abs(p.y - c.y) < 1.3f) open(c)
        }
        // hidden coins show themselves as he comes near
        for (c in g.world.coins) if (c.hidden && !c.collected) {
            val near = abs(c.z - p.z) < 5.5f && abs(c.x - p.x) < 4f && abs(c.y - p.y) < 4f
            val before = c.reveal
            c.reveal = approach(c.reveal, if (near) 1f else 0f, dt * 2.5f)
            if (before < 0.05f && c.reveal >= 0.05f) {
                g.fx.burst(c.x, c.y, c.z, 6, PK.STAR, 0xFFFFE680.toInt(), 1.6f, 0.08f, 0.5f)
                g.platform.sound(Sfx.STAR, 0.2f, 1.9f)
            }
        }
        // fireflies scatter from the towers and crystals he runs past
        val decos = g.world.decos
        if (stirred.size != decos.size) stirred = BooleanArray(decos.size)
        for ((i, d) in decos.withIndex()) {
            if (stirred[i] || (d.kind != DK.SPIRE && d.kind != DK.CRYSTAL)) continue
            val dz = d.z - p.z
            if (dz > 7f || dz < -2f) continue
            stirred[i] = true
            val n = if (d.kind == DK.SPIRE) 5 else 4
            for (k in 0 until n) {
                val q = g.fx.spawn()
                val a = rng.f(0f, TAU)
                q.x = d.x + rng.f(-0.5f, 0.5f); q.y = d.y + rng.f(0.6f, 2.2f); q.z = d.z + rng.f(-0.4f, 0.4f)
                q.vx = cos(a) * rng.f(0.8f, 2f) + (if (d.x > p.x) 1f else -1f) * 1.2f; q.vy = rng.f(1f, 2.6f); q.vz = sin(a) * rng.f(0.5f, 1.5f)
                q.life = rng.f(1.2f, 2f); q.maxLife = q.life; q.size = rng.f(0.06f, 0.1f)
                q.color = if (k % 2 == 0) 0xFFFFE680.toInt() else 0xFFFF9CEE.toInt(); q.kind = PK.STAR; q.drag = 0.6f
            }
        }
        // blossom petals drift across the path near the islands' trees
        petalT -= dt
        if (petalT <= 0f && playing && p.grounded && len3(p.vx, 0f, p.vz) > 2f) {
            petalT = rng.f(0.18f, 0.35f)
            val isl = g.world.islands.firstOrNull { it.palms > 0 && abs(it.z - p.z - 3f) < it.d * 0.5f + 3f }
            if (isl != null) {
                val q = g.fx.spawn()
                q.x = p.x + rng.f(-3.5f, 3.5f); q.y = p.y + rng.f(1.8f, 3.2f); q.z = p.z + rng.f(1f, 6f)
                q.vx = rng.f(-0.6f, 0.6f); q.vy = rng.f(-0.9f, -0.3f); q.vz = rng.f(-1.2f, -0.4f)
                q.life = rng.f(1.6f, 2.4f); q.maxLife = q.life; q.size = rng.f(0.06f, 0.09f)
                q.color = if (rng.f() < 0.6f) 0xFFFF9CD8.toInt() else 0xFFFFD0EE.toInt(); q.kind = PK.CONFETTI
                q.rot = rng.f(0f, TAU); q.vrot = rng.f(-4f, 4f); q.drag = 0.4f
            }
        }
    }

    /** The boy lands on (or stands on) a block: in the enchanted realm it lights up under him; a bridge's planks dip. */
    fun onLand(b: Block) {
        b.flash = max(b.flash, if (b.type == BT.TARGET) 0f else 0.32f)
        if (b.color == BC.WOOD) b.squash = max(b.squash, 0.35f)
    }

    private fun find(s: Secret) {
        s.found = true; secretsFound++
        val p = g.player
        var k = 0
        // (they rise quickly, nearest first, always ahead of a boy running up them)
        for (o in g.world.blocks.filter { it.shortcutId == s.id && it.state == 0 }.sortedBy { it.z }) { o.state = 1; o.visible = true; o.rise = 0.35f; o.timer = 0.06f * k; k++ }
        for (c in g.world.coins) if (c.hidden && abs(c.z - s.z) < 14f) c.reveal = max(c.reveal, 0.01f)
        g.fx.banner("SECRET ROUTE FOUND!", s.title, 0xFF7FE0FF.toInt(), 2.2f)
        g.addScore(500, p.x, p.y + 2.4f, p.z, "SECRET!")
        g.platform.sound(Sfx.STAR, 1f, 1.3f); g.platform.sound(Sfx.MYSTERY, 0.8f, 1.1f); g.platform.haptic(false)
        g.fx.burst(s.x, s.y + 0.4f, s.z, 30, PK.STAR, 0xFF9FE8FF.toInt(), 5f, 0.14f, 0.9f)
        if (g.cam.project(s.x, s.y + 0.4f, s.z)) g.fx.ring(g.cam.sx, g.cam.sy, 0xFF9FE8FF.toInt(), 24f * g.hud.s, 240f * g.hud.s, 0.6f, 10f * g.hud.s)
    }

    private fun open(c: Chest) {
        c.open = true; c.openT = 0f; chestsOpened++
        g.platform.sound(Sfx.MYSTERY, 1f, 0.9f); g.platform.sound(Sfx.WIN, 0.45f, 1.6f); g.platform.haptic(true)
        g.fx.burst(c.x, c.y + 0.7f, c.z, 36, PK.STAR, 0xFFFFE680.toInt(), 5.5f, 0.15f, 0.9f, 0f, 3f)
        g.fx.confetti(c.x, c.y + 1f, c.z, 24)
        g.addScore(1000, c.x, c.y + 2.2f, c.z, "TREASURE!")
        g.fx.popupWorld("+${c.coins * Tune.COIN_VALUE} COINS  +${c.gems} GEMS", c.x, c.y + 1.6f, c.z, 0xFFFFE14A.toInt(), 38f)
        val sx: Float; val sy: Float
        if (g.cam.project(c.x, c.y + 0.7f, c.z)) { sx = g.cam.sx; sy = g.cam.sy } else { sx = g.hud.cx(); sy = g.hud.cy() }
        g.fx.ring(sx, sy, 0xFFFFE680.toInt(), 20f * g.hud.s, 220f * g.hud.s, 0.6f, 10f * g.hud.s)
        for (k in 0 until c.coins) g.fx.fly(FK.COIN, sx + g.fx.rng.f(-30f, 30f) * g.hud.s, sy, g.hud.coinIconX(), g.hud.coinIconY(), Tune.COIN_VALUE, k * 0.04f, 0.75f)
        for (k in 0 until c.gems) g.fx.fly(FK.GEM, sx, sy, g.hud.gemIconX(), g.hud.gemIconY(), 1, 0.2f + k * 0.06f, 0.8f)
    }

    private fun len3(x: Float, y: Float, z: Float) = kotlin.math.sqrt(x * x + y * y + z * z)
}
