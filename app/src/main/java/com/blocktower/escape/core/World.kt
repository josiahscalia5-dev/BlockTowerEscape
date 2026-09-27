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
}

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
    fun cx(u: Float) = x0 + shift * smooth(u)
    fun angle(u: Float) = u * TAU
    /** Height and depth of the running surface at [u], [hop] units in from the track (toward the centre). */
    fun sy(u: Float, hop: Float = 0f) = y0 + r - (r - hop) * kotlin.math.cos(angle(u))
    fun sz(u: Float, hop: Float = 0f) = z0 + (r - hop) * kotlin.math.sin(angle(u))
    val length get() = TAU * r
}

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
    /** Level 5: the Runaway Relic's route, as rows (z) it runs from and to; 0 = none. */
    var relicZ0 = 0; var relicZ1 = 0
    /** Level 5: height of the lava sea below the course, relative to the path (it follows the climb). */
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
