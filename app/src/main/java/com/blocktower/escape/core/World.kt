package com.blocktower.escape.core

import kotlin.math.floor

class Coin(@JvmField var x: Float, @JvmField var y: Float, @JvmField var z: Float) {
    @JvmField var collected = false
    @JvmField var phase = 0f
    @JvmField var pulled = false
    @JvmField var sortKey = 0f
    @JvmField val ox = x
    @JvmField val oy = y
    @JvmField val oz = z
    /** Level 7: a hidden coin (on the secret routes): it shows itself, with a sparkle, only when the boy is near. */
    @JvmField var hidden = false
    @JvmField var reveal = 0f
}

/** Floating power-up bubble (as in the Screen 4 design): touching it gives one use of a tool. */
class Bubble(@JvmField val kind: Int, @JvmField var x: Float, @JvmField var y: Float, @JvmField var z: Float) {
    @JvmField var taken = false
    @JvmField var pulled = false
    @JvmField var pop = 0f
    @JvmField var sortKey = 0f
}

class Checkpoint(val id: Int, val x: Float, val y: Float, val z: Float) {
    var active = false
    var activeTime = 0f
    var sortKey = 0f
}

/** The Ancient Gate at the top of the tower (level exit). */
class Portal(val x: Float, val y: Float, val z: Float) {
    var sortKey = 0f
    var charge = 0f
}

/** Kinds of scenery standing in the world (Level 5). */
object DK {
    /** A fire bowl on a fortress-stone post. */
    const val TORCH = 3
    /** The design's red banner with the gold crown, hanging on the front (-z) face of a pillar; w = width. */
    const val BANNER = 4
    /** Chain railing along a bridge: posts at z and z + w, a sagging chain between them. */
    const val RAIL = 5
    /** Level 7: a moonstone turret with a pointed witch-hat roof and a pennant (the design's fantasy towers); w = width. */
    const val SPIRE = 6
    /** Level 7: a cluster of glowing magic crystals; w = size. */
    const val CRYSTAL = 7
}

/** Level 7: a gem floating over the path (the design's purple gems): picked up like a coin, +1 gem. */
class GemPickup(@JvmField var x: Float, @JvmField var y: Float, @JvmField var z: Float) {
    @JvmField var collected = false
    @JvmField var pulled = false
    @JvmField var phase = 0f
    @JvmField val oz = z
}

/** Level 7: an iron chain slung between two points (islands, spiked balls, the minions' platforms), sagging by [sag]. */
class ChainLink(@JvmField val x0: Float, @JvmField val y0: Float, @JvmField val z0: Float,
                @JvmField val x1: Float, @JvmField val y1: Float, @JvmField val z1: Float, @JvmField val sag: Float = 0.6f)

/** Kinds of the Sorcerer's minions (Level 7). */
object MK {
    /** A witch-hat imp that drops onto the path and patrols across it: steer round it, time it, or stomp on it. */
    const val PATROL = 0
    /** A round orb imp that swoops across the path at a row, in a steady rhythm, at knee-to-chest height: jump it or wait. */
    const val SWOOP = 1
    /** The lantern-ship imp: flies beside the path and drops magic bolts onto it ahead of the boy (marked where they land). */
    const val BOMBER = 2
    /** A purple-eyed imp that flies after the boy: it closes in when he slows down and lunges at him when it is close. */
    const val CHASER = 3
}

/**
 * Where a minion of wave [wave] waits (Level 7): a patroller or a swooper at row z, x (its lane), walking-surface
 * height [y]; [amp] how far it moves across the path, [speed] its pace, [phase] where it is in its rhythm.
 */
class MinionSpot(@JvmField val wave: Int, @JvmField val kind: Int, @JvmField val x: Float, @JvmField val y: Float, @JvmField val z: Float,
                 @JvmField val amp: Float, @JvmField val speed: Float, @JvmField val phase: Float)

/**
 * One of the Sorcerer's minion waves (Level 7): starts at an [Ev.MINIONS] trigger, ends at its [Ev.MINIONS_END]
 * (a checkpoint), where every minion still about vanishes. [chasers] and [bombers] fly in when it starts; its patrollers
 * and swoopers wait on the course ([MinionSpot]).
 */
class MinionWave(val id: Int, val chasers: Int, val bombers: Int, val title: String, val sub: String)

/**
 * A piece of scenery (purely visual): kind [DK], base at (x, y, z), [w] world units wide (or long, for rails).
 */
class Deco(@JvmField val kind: Int, @JvmField val x: Float, @JvmField val y: Float, @JvmField val z: Float, @JvmField val w: Float)

/**
 * A spiked iron ball on a chain that swings across the path (Level 5), hanging from a stone beam between two
 * pillars. It swings in the x-y plane around the pivot (px, py, pz): angle = amp * sin(2π t / period + phase).
 */
class SwingLog(@JvmField val px: Float, @JvmField val py: Float, @JvmField val pz: Float, @JvmField val len: Float,
               @JvmField val amp: Float, @JvmField val period: Float, @JvmField val phase: Float) {
    @JvmField var angle = 0f
    @JvmField var omega = 0f
    /** The ball's radius, spikes included. */
    @JvmField val radius = 0.62f
    val cx get() = px + kotlin.math.sin(angle) * len
    val cy get() = py - kotlin.math.cos(angle) * len
}

/**
 * A fortress-stone pillar rising out of the lava sea (Level 5): centre (x, z), top height, width (x) and depth
 * (z). Purely visual: the course's blocks stand on top of it.
 */
class Pillar(@JvmField val x: Float, @JvmField val z: Float, @JvmField val top: Float, @JvmField val w: Float, @JvmField val d: Float)

/**
 * The great block loop (Level 5): a ring of coloured blocks standing up along the course (in the y-z plane).
 * The boy runs in at the bottom (going +z), up the far side, over the top upside down, down the near side and
 * out at the bottom again, [shift] further to the right: the ring is a slight corkscrew, so the way out passes
 * beside the way in. [r] is the radius of the running surface; u = 0..1 is the way round.
 */
class Loop(@JvmField val x0: Float, @JvmField val y0: Float, @JvmField val z0: Float, @JvmField val r: Float, @JvmField val shift: Float) {
    /** Width of the track across (three lanes). */
    @JvmField val width = 3f
    /** Spike plates on the track: u, lane offset from the track's centre (steer round them, or hop over). */
    val spikes = ArrayList<FloatArray>()
    /** Speed pads across the whole track (u of each): running over one gives a burst of pace. */
    val pads = ArrayList<Float>()
    /** The track's up direction at [u] (toward the ring's centre): the boy's feet point the other way. */
    fun upY(u: Float) = kotlin.math.cos(angle(u))
    fun upZ(u: Float) = -kotlin.math.sin(angle(u))
    fun cx(u: Float) = x0 + shift * smooth(u)
    fun angle(u: Float) = u * TAU
    /** Height and depth of the running surface at [u], [hop] units in from the track (toward the centre). */
    fun sy(u: Float, hop: Float = 0f) = y0 + r - (r - hop) * kotlin.math.cos(angle(u))
    fun sz(u: Float, hop: Float = 0f) = z0 + (r - hop) * kotlin.math.sin(angle(u))
    val length get() = TAU * r
}

/**
 * A rainbow water slide (Level 6): an open half-pipe that follows a smooth curve through [pts] (x, y, z triples: the
 * running line along the bottom of the channel). The boy rides it from its mouth (s = 0) to its exit (s = [length]):
 * down the bottom and up the walls. th is the angle round the channel from its bottom (0) toward the right wall (+)
 * or the left wall (-); the channel is [r] wide either side of its middle. A tube with [ride] false is scenery
 * (a closed pipe). The frame along the curve keeps the channel upright: up is as close to straight up as the
 * curve allows, right is up x forward.
 */
class Slide(pts: FloatArray, @JvmField val r: Float = 1.25f, @JvmField val ride: Boolean = true) {
    companion object {
        /** Spacing of the samples along the curve. */
        const val STEP = 0.25f
    }
    @JvmField val n: Int
    @JvmField val bx: FloatArray; @JvmField val by: FloatArray; @JvmField val bz: FloatArray
    @JvmField val tx: FloatArray; @JvmField val ty: FloatArray; @JvmField val tz: FloatArray
    @JvmField val ux: FloatArray; @JvmField val uy: FloatArray; @JvmField val uz: FloatArray
    @JvmField val rx: FloatArray; @JvmField val ry: FloatArray; @JvmField val rz: FloatArray
    /** Signed sideways curvature (1/radius; + when the slide bends to the right). */
    @JvmField val curv: FloatArray
    @JvmField val length: Float
    /** Spike plates in the channel: s, th (steer round them, or hop over). */
    val spikes = ArrayList<FloatArray>()
    /** Speed rings across the channel (s of each): sliding through one gives a burst of pace. */
    val pads = ArrayList<Float>()
    /** How the mouth and the exit sit: the first and last samples. */
    val x0 get() = bx[0]; val y0 get() = by[0]; val z0 get() = bz[0]
    val x1 get() = bx[n - 1]; val y1 get() = by[n - 1]; val z1 get() = bz[n - 1]

    init {
        // Catmull-Rom through the points (the ends repeated), sampled finely, then resampled by arc length
        val m = pts.size / 3
        val fine = ArrayList<FloatArray>()
        fun p(i: Int, k: Int) = pts[kotlin.math.min(m - 1, kotlin.math.max(0, i)) * 3 + k]
        for (i in 0 until m - 1) {
            val sub = 24
            for (j in 0 until sub) {
                val t = j / sub.toFloat()
                val t2 = t * t; val t3 = t2 * t
                val q = FloatArray(3)
                for (k in 0..2) {
                    val a = p(i - 1, k); val b = p(i, k); val c = p(i + 1, k); val d = p(i + 2, k)
                    q[k] = 0.5f * (2f * b + (-a + c) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (-a + 3f * b - 3f * c + d) * t3)
                }
                fine.add(q)
            }
        }
        fine.add(floatArrayOf(p(m - 1, 0), p(m - 1, 1), p(m - 1, 2)))
        val acc = FloatArray(fine.size)
        for (i in 1 until fine.size) acc[i] = acc[i - 1] + len3(fine[i][0] - fine[i - 1][0], fine[i][1] - fine[i - 1][1], fine[i][2] - fine[i - 1][2])
        length = acc[fine.size - 1]
        n = kotlin.math.max(2, (length / STEP).toInt() + 1)
        bx = FloatArray(n); by = FloatArray(n); bz = FloatArray(n)
        var j = 0
        for (i in 0 until n) {
            val s = kotlin.math.min(length, i * STEP)
            while (j < fine.size - 2 && acc[j + 1] < s) j++
            val u = if (acc[j + 1] > acc[j]) (s - acc[j]) / (acc[j + 1] - acc[j]) else 0f
            bx[i] = lerp(fine[j][0], fine[j + 1][0], u); by[i] = lerp(fine[j][1], fine[j + 1][1], u); bz[i] = lerp(fine[j][2], fine[j + 1][2], u)
        }
        tx = FloatArray(n); ty = FloatArray(n); tz = FloatArray(n)
        ux = FloatArray(n); uy = FloatArray(n); uz = FloatArray(n)
        rx = FloatArray(n); ry = FloatArray(n); rz = FloatArray(n)
        curv = FloatArray(n)
        for (i in 0 until n) {
            val a = kotlin.math.max(0, i - 1); val b = kotlin.math.min(n - 1, i + 1)
            var dx = bx[b] - bx[a]; var dy = by[b] - by[a]; var dz = bz[b] - bz[a]
            val l = len3(dx, dy, dz).coerceAtLeast(1e-5f); dx /= l; dy /= l; dz /= l
            tx[i] = dx; ty[i] = dy; tz[i] = dz
            // up: straight up with the forward part taken out
            var vx = -dy * dx; var vy = 1f - dy * dy; var vz = -dy * dz
            val lv = len3(vx, vy, vz).coerceAtLeast(1e-5f); vx /= lv; vy /= lv; vz /= lv
            ux[i] = vx; uy[i] = vy; uz[i] = vz
            // right = up x forward
            rx[i] = vy * dz - vz * dy; ry[i] = vz * dx - vx * dz; rz[i] = vx * dy - vy * dx
        }
        for (i in 0 until n) {
            val a = kotlin.math.max(0, i - 2); val b = kotlin.math.min(n - 1, i + 2)
            val ds = (b - a) * STEP
            curv[i] = if (ds > 0f) ((tx[b] - tx[a]) * rx[i] + (ty[b] - ty[a]) * ry[i] + (tz[b] - tz[a]) * rz[i]) / ds else 0f
        }
    }

    private var li = 0; private var lf = 0f
    private fun locate(s: Float) {
        val f = clamp(s, 0f, length) / STEP
        li = kotlin.math.min(n - 2, f.toInt()); lf = clamp01(f - li)
    }
    private fun at(a: FloatArray, s: Float): Float { locate(s); return lerp(a[li], a[li + 1], lf) }

    /**
     * The point at [s] along the slide, [th] round the channel, [inset] in from its surface (toward the channel's
     * middle): writes x, y, z into out[o..o+2].
     */
    fun point(s: Float, th: Float, inset: Float, out: FloatArray, o: Int = 0) {
        locate(s)
        val i = li; val f = lf
        val bxs = lerp(bx[i], bx[i + 1], f); val bys = lerp(by[i], by[i + 1], f); val bzs = lerp(bz[i], bz[i + 1], f)
        val uxs = lerp(ux[i], ux[i + 1], f); val uys = lerp(uy[i], uy[i + 1], f); val uzs = lerp(uz[i], uz[i + 1], f)
        val rxs = lerp(rx[i], rx[i + 1], f); val rys = lerp(ry[i], ry[i + 1], f); val rzs = lerp(rz[i], rz[i + 1], f)
        val c = kotlin.math.cos(th); val sn = kotlin.math.sin(th)
        val k = r - inset
        // the channel's axis is r above the bottom; the surface is r from it
        out[o] = bxs + r * uxs + k * (-c * uxs + sn * rxs)
        out[o + 1] = bys + r * uys + k * (-c * uys + sn * rys)
        out[o + 2] = bzs + r * uzs + k * (-c * uzs + sn * rzs)
    }

    /** The inward normal (toward the channel's middle) at [s], [th]: the boy's "up" while he rides it. */
    fun normal(s: Float, th: Float, out: FloatArray, o: Int = 0) {
        locate(s)
        val i = li; val f = lf
        val c = kotlin.math.cos(th); val sn = kotlin.math.sin(th)
        out[o] = c * lerp(ux[i], ux[i + 1], f) - sn * lerp(rx[i], rx[i + 1], f)
        out[o + 1] = c * lerp(uy[i], uy[i + 1], f) - sn * lerp(ry[i], ry[i + 1], f)
        out[o + 2] = c * lerp(uz[i], uz[i + 1], f) - sn * lerp(rz[i], rz[i + 1], f)
    }

    fun tanX(s: Float) = at(tx, s); fun tanY(s: Float) = at(ty, s); fun tanZ(s: Float) = at(tz, s)
    fun curvAt(s: Float) = at(curv, s)
    /** The heading of the slide at [s] (radians about +y, 0 = +z). */
    fun heading(s: Float) = kotlin.math.atan2(tanX(s), tanZ(s))
}

/** A laser gate across the path (Level 6): emitters at x0 and x1, a beam at height [y] across row [z]. */
class Laser(@JvmField val x0: Float, @JvmField val x1: Float, @JvmField val y: Float, @JvmField val z: Float,
            @JvmField val period: Float, @JvmField val onFor: Float, @JvmField val phase: Float) {
    /** 0..1: the beam (fades in and out), the warning flicker before it fires. */
    @JvmField var beam = 0f
    @JvmField var warn = 0f
    /** Seconds until the beam fires next (0 while it is on). */
    @JvmField var untilOn = 0f
}

/**
 * A block studded with iron spikes (Level 6): touching it from any side hurts. With [amp] > 0 it slides back and
 * forth across the path along an iron rail.
 */
class SpikeBox(@JvmField val x0: Float, @JvmField val y: Float, @JvmField val z: Float, @JvmField val amp: Float,
               @JvmField val speed: Float, @JvmField val phase: Float, @JvmField val size: Float = 0.9f) {
    @JvmField var x = x0
}

/**
 * A floating island of temple stone under a stretch of the course (Level 6): a stone slab with moss on top, a rocky
 * underside tapering away below it, and waterfalls pouring off its edges ([falls]: 1 left, 2 right, 4 front).
 */
class Island(@JvmField val x: Float, @JvmField val z: Float, @JvmField val top: Float, @JvmField val w: Float, @JvmField val d: Float,
             @JvmField val depth: Float, @JvmField val falls: Int, @JvmField val palms: Int, @JvmField val seed: Int)

/** Level 7: kinds of the Sorcerer's creatures and spells placed on the course (see [Sorcery]). */
object SK {
    /** A rolling stone creature: drops onto lane x (relative to the path; [SpellSpot.AIM]: the boy's lane) of row z and rolls down the path toward him. */
    const val ROLLER = 0
    /** A teleporting spirit haunting rows z .. z + len: it blinks onto a marked spot on the path ahead of the boy, again and again. */
    const val SPIRIT = 1
    /** A magical hand reaching out of the island's edge on side x (-1 left, 1 right) and slamming down on that outer lane of row z. */
    const val HAND = 2
}

/**
 * Level 7: one of the Sorcerer's creatures or spells waiting on the course ([kind] from [SK]). [len]: how many rows a
 * spirit haunts; [period]: a hand's rhythm (seconds) or a roller's speed (units a second); [phase]: a hand's timing.
 */
class SpellSpot(@JvmField val kind: Int, @JvmField val x: Float, @JvmField val y: Float, @JvmField val z: Float,
                @JvmField val len: Float, @JvmField val period: Float, @JvmField val phase: Float) {
    companion object { const val AIM = 9f }
}

/** Level 7: kinds of grab attacks (the level's signature threat, see [GrabSystem]). */
object GK {
    /** A giant shadow hand rising out of a magic rift beside the path: it rears up, then lunges at the boy and snaps shut. */
    const val HAND = 0
    /** Magical vines erupting from the path across a row and lashing up round the legs (jump them, or wait). */
    const val VINES = 1
    /** A stretch where small flying grabbers dart at the boy from different directions, one after another. */
    const val GRABBERS = 2
    /** A magical chain swinging low across the path from a floating rune ring, an open shackle on its end (jump it). */
    const val CHAIN = 3
    /** The Guardian's grab: the Sorcerer's own claw reaching down out of the sky at the boy. */
    const val GUARDIAN = 4
    /** Little imps that follow the boy from behind for a stretch without attacking (something is following...). */
    const val FOLLOWERS = 5
}

/**
 * Level 7: a grab attack waiting on the course ([kind] from [GK]) at row [z], walking level [y]. By kind:
 *  HAND: [x] the side it rises on (-1 left, 1 right). VINES: [len] how many lanes either side of the path's middle.
 *  GRABBERS and FOLLOWERS: rows z .. z + [len]; grabbers come every [period] s (the first after [phase] s); [flag] followers.
 *  CHAIN: [x] where it hangs (from the path's middle), [len] the chain's length, [period] its swing, [phase] its timing.
 *  GUARDIAN: [flag] 1 for the final grab (EPIC ESCAPE, then the Celestial Gate).
 */
class GrabSpot(@JvmField val kind: Int, @JvmField val x: Float, @JvmField val y: Float, @JvmField val z: Float,
               @JvmField val len: Float = 0f, @JvmField val period: Float = 0f, @JvmField val phase: Float = 0f, @JvmField val flag: Int = 0)

/** Level 7: a star rune set into the path (the Sorcerer's Wrath): running over it lights it and strikes the Sorcerer. */
class Rune(@JvmField val id: Int, @JvmField val x: Float, @JvmField val y: Float, @JvmField val z: Float) {
    @JvmField var lit = false
    @JvmField var litT = 0f
}

/** Level 7: a treasure chest (the secret routes' prize): touching it opens it. */
class Chest(@JvmField val x: Float, @JvmField val y: Float, @JvmField val z: Float, @JvmField val coins: Int, @JvmField val gems: Int) {
    @JvmField var open = false
    @JvmField var openT = 0f
    @JvmField var sortKey = 0f
}

/**
 * Level 7: a secret route. Its switch (a glowing rune tile beside the path, [switch]) or its hidden ledge (landing
 * there finds it) at (x, y, z) within [r] reveals the route's blocks (those with shortcutId == [id]).
 */
class Secret(@JvmField val id: Int, @JvmField val x: Float, @JvmField val y: Float, @JvmField val z: Float, @JvmField val r: Float,
             @JvmField val switch: Boolean, @JvmField val title: String) {
    @JvmField var found = false
}

/** Level 7: a sanctuary: a ring of starlight on the path where the Sorcerer cannot strike (rows z0 .. z1). */
class Sanctuary(@JvmField val x: Float, @JvmField val y: Float, @JvmField val z0: Float, @JvmField val z1: Float)

/** Z-range with a section name and x origin (used for background parallax and camera framing). */
class Section(val name: String, val z0: Int, val z1: Int, val originX: Float)

object Ev {
    const val CHASE = 2; const val COLLAPSE = 4; const val LAVA = 5
    const val HINT_TARGET = 6; const val HINT_JUMP = 7; const val HINT_TOOLS = 8; const val FORK = 9; const val CHASE_END = 10
    const val LAVA_STOP = 12; const val HINT_BLOCK = 13; const val FINAL = 14; const val TRAPS = 15; const val ZONE = 16; const val HINT_MAGNET = 17
    /** Tutorial tip: text in the trigger, tip position kind in text2. */
    const val TUT = 18
    /** Magical wind: gusts push the boy sideways until WIND_STOP. */
    const val WIND = 19; const val WIND_STOP = 20
    /** Level 5: the Runaway Relic appears and runs off along the course (CHASE & COLLECT). */
    const val RELIC = 21
    /** Level 7: one of the Sorcerer's minion waves begins (its id in text2) / ends, at a checkpoint (its id in text2). */
    const val MINIONS = 22; const val MINIONS_END = 23
    /**
     * Level 7: the Sorcerer's Wrath (the level's guardian encounter) begins / changes its attack (1 shockwaves,
     * 2 hands and cursed blocks, 3 falling crystal shards; in text2) / ends at a checkpoint. WARNING: the Sorcerer
     * awakens (the magical warning before it).
     */
    const val WRATH = 24; const val WRATH_MODE = 25; const val WRATH_END = 26; const val WARNING = 27
    /** Level 7: the level's cinematic phase changes (its number in text2: the sky's light and the music follow it). */
    const val PHASE = 28
    /** Level 7: the Celestial Gate awakens as the boy reaches its plaza (the finale). */
    const val GATE = 29
    /** Blocks the Sorcerer curses (Level 7): they flicker purple and vanish for a moment, again and again. */
    const val CURSE = 30
}

class Trigger(val z: Float, val xMin: Float, val xMax: Float, val event: Int, val text: String = "", val text2: String = "") {
    var fired = false
}

class World {
    val blocks = ArrayList<Block>(1600)
    var rowMin = -8
    var rowMax = 240
    lateinit var rows: Array<ArrayList<Block>>
    val coins = ArrayList<Coin>()
    val bubbles = ArrayList<Bubble>()
    val checkpoints = ArrayList<Checkpoint>()
    val portals = ArrayList<Portal>()
    val triggers = ArrayList<Trigger>()
    val sections = ArrayList<Section>()
    val decos = ArrayList<Deco>()
    val logs = ArrayList<SwingLog>()
    val pillars = ArrayList<Pillar>()
    val loops = ArrayList<Loop>()
    /** Level 6: rainbow slides (rideable and scenery), laser gates, spiked blocks and the floating islands. */
    val slides = ArrayList<Slide>()
    val lasers = ArrayList<Laser>()
    val spikeBoxes = ArrayList<SpikeBox>()
    val islands = ArrayList<Island>()
    /** Level 7: gems over the path, chains between islands and hazards, and the Sorcerer's minion waves. */
    val gemPicks = ArrayList<GemPickup>()
    val chains = ArrayList<ChainLink>()
    val minionSpots = ArrayList<MinionSpot>()
    val waves = ArrayList<MinionWave>()
    /** Level 7: the Sorcerer's creatures and spells, the Wrath's star runes and sanctuaries, and the secret routes' chests. */
    val spells = ArrayList<SpellSpot>()
    val runes = ArrayList<Rune>()
    val sanctuaries = ArrayList<Sanctuary>()
    val chests = ArrayList<Chest>()
    val secrets = ArrayList<Secret>()
    /** Level 7: the grab attacks (shadow hands, vines, grabbers, chains, the Guardian's grabs) and the followers. */
    val grabs = ArrayList<GrabSpot>()
    /** Level 5: the Runaway Relic's route, as rows (z) it runs from and to; 0 = none. */
    var relicZ0 = 0; var relicZ1 = 0
    /** Levels 5 and 6: depth of the lava sea (the cloud sea) below the course, relative to the path (it follows the climb). */
    var seaDepth = 0f
    /** Walking-surface height of the main path per row (for the guard / collapse / camera). */
    val pathLevel = HashMap<Int, Float>()
    val pathX = HashMap<Int, Float>()
    var spawnX = 0f; var spawnY = 0f; var spawnZ = 0.5f
    var targetTotal = 0
    /** Where the final collapse stops (the grand staircase to the gate is safe ground). */
    var finalSafeZ = 1e9f
    var coinTotal = 0
    var mysteryTotal = 0

    fun initRows() {
        rows = Array(rowMax - rowMin + 1) { ArrayList<Block>(8) }
    }

    fun rowIndex(z: Float) = floor(z).toInt()

    fun row(r: Int): ArrayList<Block>? {
        val i = r - rowMin
        return if (i < 0 || i >= rows.size) null else rows[i]
    }

    fun add(b: Block): Block {
        blocks.add(b)
        b.row = floor(b.z + 0.001f).toInt()
        row(b.row)?.add(b)
        return b
    }

    fun remove(b: Block) {
        blocks.remove(b)
        row(b.row)?.remove(b)
    }

    /** Find a static unit block occupying the given cell (centre x, bottom y, front z). */
    fun cell(x: Float, y: Float, z: Float): Block? {
        val r = row(floor(z + 0.001f).toInt()) ?: return null
        for (i in r.indices) {
            val b = r[i]
            if (b.type == BT.MOVING || b.decor || !b.visible || b.destroyed || b.alpha < 0.99f || b.rise > 0.01f) continue
            if (b.sx != 1f || b.sy != 1f || b.sz != 1f) continue
            if (absf(b.x - x) < 0.01f && absf(b.y - y) < 0.01f && absf(b.z - z) < 0.01f) return b
        }
        return null
    }

    fun sectionAt(z: Float): Section? {
        for (s in sections) if (z >= s.z0 - 0.5f && z < s.z1 + 1f) return s
        return sections.lastOrNull()
    }

    fun levelAt(row: Int): Float {
        pathLevel[row]?.let { return it }
        var r = row
        var n = 0
        while (n < 30) { r--; n++; pathLevel[r]?.let { return it } }
        return 0f
    }

    fun pathXAt(row: Int): Float {
        pathX[row]?.let { return it }
        var r = row; var n = 0
        while (n < 30) { r--; n++; pathX[r]?.let { return it } }
        return 0f
    }
}
