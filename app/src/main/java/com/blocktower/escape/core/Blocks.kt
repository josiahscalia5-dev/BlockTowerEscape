package com.blocktower.escape.core

/** Block colours sampled from the Screen 4 artwork. */
object BC {
    const val RED = 0; const val GREEN = 1; const val YELLOW = 2; const val BLUE = 3; const val PURPLE = 4
    const val ORANGE = 5; const val BRICK = 6; const val GOLD = 7; const val CYAN = 8; const val MAGENTA = 9
    const val STONE = 10; const val IRON = 11; const val ENERGY = 12
    /** Level 5: the fortress's stone bricks, dark volcanic rock (spike bases, the Guardian's boulders) and bridge planks. */
    const val FORT = 13; const val BASALT = 14; const val WOOD = 15
    /** Level 6: the sky temple's sun-bleached stone (the floating islands, the temple steps). */
    const val SAND = 16
    /** Level 7: the enchanted realm's moonstone (lavender-grey stone of the islands, towers and the gate's stairs). */
    const val MOON = 17
    const val COUNT = 18

    /** Colours drawn with the brick tone map (courses of stones or planks) instead of the cracked-stone ones. */
    fun bricky(c: Int) = c == BRICK || c == FORT || c == WOOD || c == SAND || c == MOON

    /** 5-stop colour ramps: shadow, base, bright, highlight, white-hot. */
    val ramps: Array<IntArray> = arrayOf(
        intArrayOf(0xFF7A0006.toInt(), 0xFFC8080C.toInt(), 0xFFF53A2A.toInt(), 0xFFFF7E5C.toInt(), 0xFFFFD9C8.toInt()),  // red
        intArrayOf(0xFF065A14.toInt(), 0xFF12A62A.toInt(), 0xFF3CDB45.toInt(), 0xFF9CF77E.toInt(), 0xFFE9FFD8.toInt()),  // green
        intArrayOf(0xFFB05A00.toInt(), 0xFFE89A00.toInt(), 0xFFFFCC14.toInt(), 0xFFFFEE78.toInt(), 0xFFFFFFE0.toInt()),  // yellow
        intArrayOf(0xFF00146E.toInt(), 0xFF0036C8.toInt(), 0xFF1467FA.toInt(), 0xFF5EA8FF.toInt(), 0xFFDDEEFF.toInt()),  // blue
        intArrayOf(0xFF3A0070.toInt(), 0xFF7404B8.toInt(), 0xFFA52BE6.toInt(), 0xFFD884FA.toInt(), 0xFFF6E0FF.toInt()),  // purple
        intArrayOf(0xFF8A3000.toInt(), 0xFFD06000.toInt(), 0xFFFF9412.toInt(), 0xFFFFC266.toInt(), 0xFFFFF0D8.toInt()),  // orange
        intArrayOf(0xFF4A220E.toInt(), 0xFF84461F.toInt(), 0xFFB26A36.toInt(), 0xFFDC9C62.toInt(), 0xFFF6D8B0.toInt()),  // brick
        intArrayOf(0xFF9A6200.toInt(), 0xFFE0A000.toInt(), 0xFFFFD230.toInt(), 0xFFFFF09A.toInt(), 0xFFFFFFFF.toInt()),  // gold
        intArrayOf(0xFF004A8A.toInt(), 0xFF0A8CE0.toInt(), 0xFF22C8FF.toInt(), 0xFF8AF0FF.toInt(), 0xFFF0FFFF.toInt()),  // cyan (boost)
        intArrayOf(0xFF6A0060.toInt(), 0xFFB0149E.toInt(), 0xFFE83ED0.toInt(), 0xFFFF94EE.toInt(), 0xFFFFE6FA.toInt()),  // magenta (bounce)
        intArrayOf(0xFF2A2436.toInt(), 0xFF4E4660.toInt(), 0xFF7A7090.toInt(), 0xFFA89CBC.toInt(), 0xFFE0DAEA.toInt()),  // stone (trap)
        intArrayOf(0xFF14161E.toInt(), 0xFF2A2E3C.toInt(), 0xFF484E62.toInt(), 0xFF7C8498.toInt(), 0xFFC8D0E0.toInt()),  // iron (guard)
        intArrayOf(0xFF1060B0.toInt(), 0xFF40A8F0.toInt(), 0xFF90E0FF.toInt(), 0xFFD0F8FF.toInt(), 0xFFFFFFFF.toInt()),  // energy (tool block)
        intArrayOf(0xFF2A2024.toInt(), 0xFF564650.toInt(), 0xFF7E6A70.toInt(), 0xFFAA9294.toInt(), 0xFFE2CEC6.toInt()),  // fortress stone (Level 5)
        intArrayOf(0xFF161012.toInt(), 0xFF2E2426.toInt(), 0xFF4A3A38.toInt(), 0xFF766058.toInt(), 0xFFB8988A.toInt()),  // basalt (Level 5)
        intArrayOf(0xFF3A1E0C.toInt(), 0xFF6A3C1A.toInt(), 0xFF96602C.toInt(), 0xFFC4904E.toInt(), 0xFFEED0A0.toInt()),  // bridge planks (Level 5)
        intArrayOf(0xFF4A3A48.toInt(), 0xFF866E76.toInt(), 0xFFB09A98.toInt(), 0xFFD6C2B2.toInt(), 0xFFF4E8DA.toInt()),  // temple stone (Level 6)
        intArrayOf(0xFF3A3050.toInt(), 0xFF6E6488.toInt(), 0xFF9C90B4.toInt(), 0xFFC8BCDA.toInt(), 0xFFF0E8FA.toInt()),  // moonstone (Level 7)
    )

    /** Representative colours (for particles, far LOD and silhouettes). */
    fun base(c: Int) = ramps[c][2]
    fun dark(c: Int) = ramps[c][0]
    fun light(c: Int) = ramps[c][3]
}

enum class BT {
    NORMAL, TARGET, BRICK, MOVING, DISAPPEAR, BOUNCE, FALLING, CRACKED, COLORSHIFT,
    MYSTERY, BOOST, SAVE, TRAP, APPEAR, CHECKPOINT, TOOLBLOCK, RESCUE, DECOR,
    /** Speed pad (Level 5): a blue block with glowing chevrons; running over it gives a burst of speed. */
    PAD
}

object Reward { const val COINS = 0; const val GEMS = 1; const val TOOL_MAGNET = 2; const val TOOL_SHIELD = 3; const val TOOL_SPEED = 4
    const val TOOL_BLOCK = 5; const val TRAP = 6; const val SHORTCUT = 7; const val HEART = 8 }

/**
 * A box in the world. x = centre x, y = bottom, z = front face. Size sx*sy*sz.
 */
class Block(
    @JvmField var x: Float, @JvmField var y: Float, @JvmField var z: Float,
    @JvmField var color: Int, @JvmField var type: BT = BT.NORMAL
) {
    @JvmField var sx = 1f
    @JvmField var sy = 1f
    @JvmField var sz = 1f
    @JvmField var solid = true
    @JvmField var visible = true
    @JvmField var alpha = 1f
    @JvmField var variant = 0
    @JvmField var row = 0

    // state for special blocks
    @JvmField var timer = 0f
    @JvmField var state = 0
    @JvmField var vy = 0f
    @JvmField var baseX = 0f
    @JvmField var amp = 0f
    @JvmField var speed = 0f
    @JvmField var phase = 0f
    @JvmField var lastX = 0f
    @JvmField var dxFrame = 0f
    @JvmField var reward = -1
    @JvmField var used = false
    @JvmField var shake = 0f
    @JvmField var flash = 0f
    @JvmField var bump = 0f          // mystery bump animation
    @JvmField var squash = 0f        // bounce animation
    @JvmField var rise = 0f          // appear animation offset (0 = in place)
    @JvmField var damage = 0f        // cracked blocks
    @JvmField var life = -1f         // temporary blocks (tool block / rescue)
    @JvmField var spike = 0f         // trap spike height 0..1
    @JvmField var respawn = 0f
    @JvmField var destroyed = false  // removed by an event (collapse/dragon)
    @JvmField var eventTag = 0       // which event may destroy/restore it
    @JvmField var origY = 0f
    @JvmField var origType = BT.NORMAL
    @JvmField var origColor = 0
    @JvmField var shortcutId = 0     // blocks revealed by a shortcut mystery
    @JvmField var checkpointId = -1
    @JvmField var decor = false      // purely visual (no collision)
    @JvmField var sortKey = 0f
    @JvmField var contact = false    // player touching this frame
    @JvmField var wasContact = false
    @JvmField var crackT = 0f        // collected blue block: 0 idle, 0..1 cracking, then it shatters
    @JvmField var pop = 0f           // scale pop after a shell shatters / a block is built
    @JvmField var star = false       // decorative star emblem on the front face (Level 5 start block)

    val x0 get() = x - sx * 0.5f
    val x1 get() = x + sx * 0.5f
    val y1 get() = y + sy
    val z1 get() = z + sz
    val top get() = y + sy

    fun remember() { origY = y; origType = type; origColor = color }

    /** Solid & present for collision purposes. */
    fun collides() = solid && visible && !destroyed && !decor && rise < 0.05f && alpha > 0.35f
}

/** Game-wide gameplay constants. */
object Tune {
    const val GRAVITY = 30f
    const val RUN = 5.2f
    const val RUN_FAST = 8.2f
    const val JUMP_V = 10.4f
    const val JUMP_V_FAST = 12.6f
    const val ACCEL = 34f
    const val AIR_ACCEL = 20f
    const val RADIUS = 0.28f
    const val HEIGHT = 1.45f
    const val FALL_DEPTH = 4.6f      // how far below the last ground a fall triggers the rescue
    const val COIN_VALUE = 10
    /** Speed pad (Level 5): top speed and how long the burst lasts. */
    const val RUN_DASH = 9.2f
    const val DASH_T = 1.1f
    /** The great loop (Level 5): the slowest and fastest pace round it. */
    const val LOOP_MIN = 6.2f
    const val LOOP_MAX = 11.5f
    /** Rainbow slides (Level 6): the slowest and fastest pace down one, and the steepest he rides up a wall (rad). */
    const val SLIDE_MIN = 5.0f
    const val SLIDE_MAX = 13.5f
    const val SLIDE_WALL = 1.2f
}
