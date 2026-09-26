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

class Checkpoint(val id: Int, val x: Float, val y: Float, val z: Float) {
    var active = false
    var activeTime = 0f
    var sortKey = 0f
}

class Portal(val x: Float, val y: Float, val z: Float, val isEnd: Boolean,
             val destX: Float = 0f, val destY: Float = 0f, val destZ: Float = 0f) {
    var sortKey = 0f
    var charge = 0f
}

/** Z-range with a section name and x origin (used for background parallax and camera framing). */
class Section(val name: String, val z0: Int, val z1: Int, val originX: Float)

object Ev {
    const val STORM = 1; const val CHASE = 2; const val DRAGON = 3; const val COLLAPSE = 4; const val LAVA = 5
    const val HINT_TARGET = 6; const val HINT_JUMP = 7; const val HINT_TOOLS = 8; const val HINT_FORK = 9; const val CHASE_END = 10
    const val STORM_END = 11; const val LAVA_STOP = 12; const val HINT_BLOCK = 13
}

class Trigger(val z: Float, val xMin: Float, val xMax: Float, val event: Int) {
    var fired = false
}

class World {
    val blocks = ArrayList<Block>(1600)
    var rowMin = -8
    var rowMax = 240
    lateinit var rows: Array<ArrayList<Block>>
    val coins = ArrayList<Coin>()
    val checkpoints = ArrayList<Checkpoint>()
    val portals = ArrayList<Portal>()
    val triggers = ArrayList<Trigger>()
    val sections = ArrayList<Section>()
    /** Walking-surface height of the main path per row (for the guard / collapse / camera). */
    val pathLevel = HashMap<Int, Float>()
    val pathX = HashMap<Int, Float>()
    var spawnX = 0f; var spawnY = 0f; var spawnZ = 0.5f
    var targetTotal = 0
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
