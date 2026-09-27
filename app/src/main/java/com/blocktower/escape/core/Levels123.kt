package com.blocktower.escape.core

/**
 * Levels 1-3: the first climbs through the floating-block sky world (the Screen 4 world), each ending at a
 * glowing block portal. They teach the game step by step:
 *
 *  Level 1  Tutorial Adventure  move, steer, jump, collect, reach the portal. Falls are free.
 *  Level 2  First Challenge     longer path, wider gaps, slow moving blocks, mystery blocks, the first
 *                               spikes, and the MAGNET for blue blocks out of reach.
 *  Level 3  Mechanics Begin     faster moving blocks, vanishing and cracked blocks, spike rows, harder
 *                               jumps, an optional treasure route, and the SHIELD.
 *
 * Row string legend: see [CourseWriter].
 */
object Levels123 {
    fun level1(): World = build { L1(it).write() }
    fun level2(): World = build { L2(it).write() }
    fun level3(): World = build { L3(it).write() }

    private fun build(write: (World) -> Unit): World {
        val w = World()
        w.rowMin = -8; w.rowMax = 160
        w.initRows()
        write(w)
        return w
    }
}

/** Shared pieces of the early levels. */
private open class SkyWriter(w: World, seed: Int) : CourseWriter(w, seed) {
    /** Start pad: three rows behind the boy, the start row, checkpoint 0. */
    fun start() {
        w.spawnX = 0f; w.spawnY = 0f; w.spawnZ = 0.45f
        w.checkpoints.add(Checkpoint(0, 0f, 0f, 0.45f).also { it.active = true })
        row(-3, 0, "GYG"); row(-2, 0, "YGY"); row(-1, 0, "GYG"); row(0, 0, "RGR")
        for (x in -1..1) pillar(x, 0, -3, 2)
    }

    /** A tutorial tip (kind 6 points at the movement pad, 2 at the jump button, 7 floats in the middle). */
    fun tip(z: Int, text: String, kind: Int) { w.triggers.add(Trigger((z + zo).toFloat(), ox - 9f, ox + 9f, Ev.TUT, text, kind.toString())) }

    /**
     * The portal at the end: a plaza, two gold pillars and a lintel (high enough to jump under) around a
     * swirling portal. The portal opens once the blue-block objective is complete.
     */
    fun portal(z: Int, lvl: Int) {
        row(z - 3, lvl, "OGYGO", -2); row(z - 2, lvl, "GYGYG", -2); row(z - 1, lvl, "OGYGO", -2)
        row(z, lvl, "YGY")
        for (k in 0..3) { blk(-2f, lvl.toFloat() + k, z, BC.GOLD, BT.BRICK); blk(2f, lvl.toFloat() + k, z, BC.GOLD, BT.BRICK) }
        for (x in -2..2) blk(x.toFloat(), lvl + 4.5f, z, if (x == 0) BC.PURPLE else BC.GOLD, BT.BRICK)
        for (zz in z - 3..z) { pillar(-2, lvl, zz, 2); pillar(2, lvl, zz, 2) }
        w.portals.add(Portal(ox, lvl + lo, (z + zo) + 0.55f))
    }
}

// ============================================================================ LEVEL 1
private class L1(w: World) : SkyWriter(w, 1) {
    fun write() {
        section("tutorial", -3, 60)
        start()
        // ---- run: straight and wide, the first blue block right on the way
        tip(0, "Swipe UP to run!", 6)
        row(1, 0, "GYG"); row(2, 0, "YGY"); row(3, 0, "GYG"); row(4, 0, "YBY"); row(5, 0, "GYG")
        coinLine(0f, 0f, 1, 3)
        tip(3, "Step on BLUE blocks to collect them!", 1)
        // ---- steer: a wide plaza with blue blocks on either side
        row(6, 0, "YGYGY", -2); row(7, 0, "GYGYG", -2); row(8, 0, "BGYGY", -2); row(9, 0, "GYGYG", -2)
        row(10, 0, "YGYGY", -2); row(11, 0, "GYGYG", -2); row(12, 0, "YGYBY", -2)
        tip(6, "Swipe LEFT or RIGHT to steer", 6)
        coin(-1f, 0f, 7); coin(-2f, 0f, 9); coin(1f, 0f, 10); coin(2f, 0f, 12)
        for (z in intArrayOf(6, 12)) { pillar(-2, 0, z, 2); pillar(2, 0, z, 2) }
        row(13, 0, "GYG"); row(14, 0, "YGY")
        // ---- jump: a one-row gap with a coin arc (a golden save block waits below)
        tip(13, "Tap JUMP to hop over the gap!", 2)
        save(0f, -3, 15)
        coin(0f, 0.9f, 15)
        row(16, 0, "RGR"); row(17, 0, "GBG"); row(18, 0, "RGR")
        coinLine(0f, 0f, 16, 18)
        // ---- climb: half steps walk up by themselves
        row(19, 0.5f, "YPY"); row(20, 1, "PYP"); row(21, 1.5f, "YPY"); row(22, 2, "PBP")
        coin(0f, 0.5f, 19); coin(0f, 1.5f, 21)
        for (z in 19..22) { pillar(-1, 0, z, 1); pillar(1, 0, z, 1) }
        // ---- a checkpoint on the plateau
        row(23, 2, "GYG"); checkpointRow(24, 2); row(25, 2, "GYG")
        // ---- two more small hops, a blue block on each landing
        save(0f, -1, 26)
        row(27, 2, "YBY"); row(28, 2, "GYG")
        coin(0f, 2.9f, 26)
        save(0f, -1, 29)
        row(30, 2, "RGR"); row(31, 2, "GYG"); row(32, 2.5f, "YGY"); row(33, 3, "GBG"); row(34, 3, "YGY")
        coin(0f, 2.9f, 29); coinLine(0f, 3f, 33, 34)
        // an extra blue block on a little side step for the curious
        blk(2f, 3.5f, 32, BC.BLUE, BT.TARGET); pillar(2, 3, 32, 2)
        coin(2f, 3.5f, 31)
        // ---- follow the bends: the path swings right, then left
        tip(35, "Steer around the bends!", 6)
        val bend = intArrayOf(0, 1, 1, 2, 2, 1, 0, -1, -1, 0, 0)
        val rows = arrayOf("YGY", "GYG", "YGY", "GBG", "YGY", "GYG", "YGY", "GYG", "YBY", "GYG", "YGY")
        for (i in bend.indices) {
            row(35 + i, 3, rows[i], bend[i] - 1)
            coin(bend[i].toFloat(), 3f, 35 + i)
            if (i % 3 == 0) { pillar(bend[i] - 1, 3, 35 + i, 2); pillar(bend[i] + 1, 3, 35 + i, 2) }
        }
        // ---- the portal
        tip(46, "Reach the PORTAL!", 7)
        portal(50, 3)
        coinLine(0f, 3f, 47, 49)
    }
}

// ============================================================================ LEVEL 2
private class L2(w: World) : SkyWriter(w, 2) {
    fun write() {
        section("first challenge", -3, 90)
        start()
        trig(1, Ev.ZONE, "NEW TOOL", "MAGNET")
        row(1, 0, "GYG"); row(2, 0, "YGY"); row(3, 0, "GBG"); row(4, 0, "YGY")
        coinLine(0f, 0f, 1, 4)
        // ---- wider gaps (two rows) with coin arcs; a ? block to bump from below
        save(0f, -3, 5); save(0f, -3, 6)
        coin(0f, 1.2f, 5); coin(0f, 1.3f, 6)
        row(7, 0, "RGR"); row(8, 0, "GYG"); row(9, 0, "RGR")
        mystery(0f, 2.6f, 8, Reward.COINS, BC.GREEN)
        trig(7, Ev.HINT_JUMP)
        row(10, 0.5f, "YGY"); row(11, 1, "GBG"); row(12, 1, "YGY")
        save(0f, -2, 13); save(0f, -2, 14)
        coin(0f, 2.1f, 13); coin(0f, 2.3f, 14)
        row(15, 1, "PYP"); row(16, 1, "YPY"); row(17, 1, "PBP")
        // ---- the first hazard: spike blocks on the sides (stay in the middle)
        row(18, 1, "^Y^"); row(19, 1, "GYG"); row(20, 1, "^Y^"); row(21, 1, "GYG")
        coinLine(0f, 1f, 18, 21)
        // ---- the magnet: blue blocks on little islands just out of reach
        trig(22, Ev.HINT_MAGNET)
        row(22, 1, "RYR"); row(23, 1, "RYR"); row(24, 1, "RYR"); row(25, 1, "RYR")
        blk(-3.2f, 1.5f, 23, BC.BLUE, BT.TARGET); pillarF(-3, 1.5f, 23, 2)
        blk(3.2f, 1.5f, 25, BC.BLUE, BT.TARGET); pillarF(3, 1.5f, 25, 2)
        coin(-2.2f, 1f, 24); coin(2.2f, 1f, 24)
        checkpointRow(26, 1)
        row(27, 1, "GYG")
        mystery(1f, 3.6f, 27, Reward.TOOL_MAGNET, BC.BLUE)
        // ---- slow moving blocks over a gap (save blocks below)
        moving(0f, 1, 29, 0.8f, 1.0f, 0f, 2.4f, BC.GREEN)
        save(0f, -2, 29)
        row(31, 1, "YGY"); row(32, 1, "GBG")
        moving(0f, 1, 34, 1.0f, 1.1f, 1.6f, 2.4f, BC.YELLOW)
        save(0f, -2, 34)
        coin(0f, 1.6f, 29); coin(0f, 1.6f, 34)
        row(36, 1, "RGR"); row(37, 1.5f, "GYG"); row(38, 2, "YGY")
        // ---- a timed spike row with a safe middle lane, then a longer hop
        row(39, 2, "tYt"); row(40, 2, "GYG"); row(41, 2, "tBt"); row(42, 2, "GYG")
        coinLine(0f, 2f, 39, 42)
        save(0f, -1, 43); save(0f, -1, 44)
        coin(0f, 3.2f, 43); coin(0f, 3.3f, 44)
        row(45, 2, "PYP"); row(46, 2.5f, "YPY"); row(47, 3, "PBP"); row(48, 3, "YPY")
        mystery(-1f, 5.6f, 47, Reward.GEMS, BC.GREEN)
        // a treasure step on the left: coins and a blue block for a short detour
        row(49, 3, "GYG"); row(50, 3, "GYG")
        blk(-2f, 3f, 49, BC.BLUE, BT.TARGET); blk(-2f, 3f, 50, BC.YELLOW); pillar(-2, 3, 49, 2)
        coin(-2f, 3f, 50)
        row(51, 3, "GYG"); row(52, 3, "YGY")
        // ---- a ferry of two moving blocks
        moving(0f, 3, 54, 1.2f, 1.2f, 0f, 2.4f, BC.RED)
        moving(0f, 3, 56, 1.2f, 1.2f, 3.14f, 2.4f, BC.YELLOW)
        save(0f, 0, 55)
        coin(0f, 3.6f, 54); coin(0f, 3.6f, 56)
        row(58, 3, "GYG"); row(59, 3, "GBG"); row(60, 3, "GYG")
        // ---- the widest gap yet, then a climb past spikes to the portal
        save(0f, 0, 61); save(0f, 0, 62)
        coin(0f, 4.2f, 61); coin(0f, 4.4f, 62)
        row(63, 3.5f, "RYR"); row(64, 4, "RGR"); row(65, 4, "^B^"); row(66, 4, "RGR")
        mystery(0f, 6.6f, 64, Reward.TOOL_MAGNET, BC.BLUE)
        row(67, 4, "tYt"); row(68, 4, "GYG")
        coinLine(0f, 4f, 63, 68)
        for (z in intArrayOf(63, 66)) { pillar(-1, 4, z, 2); pillar(1, 4, z, 2) }
        portal(72, 4)
        coinLine(0f, 4f, 69, 71)
    }
}

// ============================================================================ LEVEL 3
private class L3(w: World) : SkyWriter(w, 3) {
    fun write() {
        section("mechanics", -3, 130)
        start()
        trig(1, Ev.ZONE, "NEW TOOL", "SHIELD")
        row(1, 0, "GYG"); row(2, 0, "YBY"); row(3, 0, "GYG")
        coinLine(0f, 0f, 1, 3)
        // ---- faster moving blocks: a two-platform ferry
        moving(0f, 0, 5, 1.3f, 1.5f, 0f, 2.2f, BC.RED)
        moving(0f, 0, 7, 1.3f, 1.5f, 3.14f, 2.2f, BC.YELLOW)
        save(0f, -3, 6)
        coin(0f, 0.6f, 5); coin(0f, 0.6f, 7)
        row(9, 0, "GYG"); row(10, 0, "GBG"); row(11, 0, "GYG")
        // ---- cracked bridge: keep moving or it breaks
        row(12, 0, "ccc"); row(13, 0, "ccc"); row(14, 0, "cBc"); row(15, 0, "ccc"); row(16, 0, "ccc")
        save(0f, -3, 14)
        coinLine(0f, 0f, 12, 16)
        row(17, 0, "YGY")
        // ---- harder jumps: two-row gaps with a step up
        save(0f, -3, 18); save(0f, -3, 19)
        coin(0f, 1.4f, 18); coin(0f, 1.6f, 19)
        row(20, 0.5f, "RGR"); row(21, 0.5f, "GYG")
        save(0f, -2, 22); save(0f, -2, 23)
        coin(0f, 1.9f, 22); coin(0f, 2.1f, 23)
        row(24, 1, "PYP"); row(25, 1, "PBP")
        checkpointRow(26, 1)
        row(27, 1, "GYG")
        // ---- vanishing stepping stones
        row(28, 1, "d"); row(29, 1.5f, "d"); row(30, 2, "d"); row(31, 2.5f, "d")
        save(0f, -2, 29); save(0f, -2, 31)
        coin(0f, 1.6f, 28); coin(0f, 2.1f, 29); coin(0f, 2.6f, 30); coin(0f, 3.1f, 31)
        row(32, 2.5f, "YGY"); row(33, 2.5f, "GBG")
        // ---- the fork: safe path in the middle, a treasure route on the right (vanishing blocks, gems, a blue block)
        trig(33, Ev.FORK)
        row(34, 2.5f, "YGYGY", -2)
        for (z in 35..41) row(z, 2.5f, if (z % 2 == 0) "YGY" else "GYG")
        val tr = arrayOf("d", ".", "d", "B", "d", ".", "d")
        for ((i, c) in tr.withIndex()) if (c != ".") row(35 + i, 2.5f, c.replace("B", "B"), 3, path = false)
        mystery(3f, 2.5f, 41, Reward.GEMS, BC.RED)
        for (z in 35..41) setPath(z, 2.5f)
        coinLine(3f, 2.5f, 35, 40)
        coinLine(0f, 2.5f, 36, 40)
        row(42, 2.5f, "YGYGY", -2)
        pillarF(-2, 2.5f, 34, 2); pillarF(2, 2.5f, 34, 2); pillarF(-2, 2.5f, 42, 2); pillarF(2, 2.5f, 42, 2)
        // ---- the shield: spike rows you can jump, or shrug off with the shield
        row(43, 2.5f, "YGY")
        bubble(1f, 2.5f, 43, TK.SHIELD)
        trig(43, Ev.HINT_TOOLS)
        row(44, 2.5f, "^^^"); row(45, 2.5f, "YGY"); row(46, 2.5f, "^^^"); row(47, 2.5f, "^^^"); row(48, 2.5f, "YBY")
        coin(0f, 3.7f, 44); coin(0f, 3.8f, 46); coin(0f, 3.9f, 47)
        checkpointRow(49, 2)
        row(50, 2, "GYG")
        // ---- timed spike rows (read the rhythm) and a moving block between vanishing stones
        row(51, 2, "tYt"); row(52, 2, "GYG"); row(53, 2, "ttt"); row(54, 2, "GYG"); row(55, 2, "tBt"); row(56, 2, "GYG")
        coinLine(0f, 2f, 51, 56)
        moving(0f, 2, 58, 1.4f, 1.7f, 0.8f, 2.2f, BC.PURPLE)
        save(0f, -1, 58)
        coin(0f, 2.6f, 58)
        row(60, 2, "d"); row(61, 2.5f, "d")
        save(0f, -1, 61)
        row(62, 3, "GBG"); row(63, 3, "YGY")
        mystery(0f, 5.6f, 63, Reward.COINS, BC.GREEN)
        row(64, 3, "cYc"); row(65, 3, "ccc"); row(66, 3.5f, "YGY"); row(67, 4, "GBG")
        coinLine(0f, 3f, 64, 65)
        row(68, 4, "YGY")
        // ---- a cracked bridge with timed spikes beside it: keep moving, stay in the middle
        row(69, 4, "ccc"); row(70, 4, "tct"); row(71, 4, "ccc"); row(72, 4, "cBc")
        save(0f, 1, 71)
        coinLine(0f, 4f, 69, 72)
        row(73, 4, "GYG")
        // ---- vanishing steps climbing up
        row(74, 4.5f, "d"); row(75, 5, "d"); row(76, 5.5f, "d"); row(77, 6, "d")
        save(0f, 2, 75); save(0f, 3, 77)
        coin(0f, 5f, 74); coin(0f, 5.5f, 75); coin(0f, 6f, 76); coin(0f, 6.5f, 77)
        row(78, 6, "GYG"); checkpointRow(79, 6); row(80, 6, "GYG")
        // ---- fast moving blocks, then spikes and a timed row before the portal
        moving(0f, 6, 82, 1.5f, 2.0f, 0f, 2.0f, BC.PURPLE)
        moving(0f, 6, 84, 1.5f, 2.0f, 2.0f, 2.0f, BC.GREEN)
        save(0f, 3, 83)
        coin(0f, 6.6f, 82); coin(0f, 6.6f, 84)
        row(86, 6, "RBR"); row(87, 6, "^Y^"); row(88, 6, "RYR"); row(89, 6, "tYt"); row(90, 6, "RGR")
        coinLine(0f, 6f, 86, 90)
        for (z in intArrayOf(78, 86, 90)) { pillar(-1, 6, z, 3); pillar(1, 6, z, 3) }
        portal(94, 6)
        coinLine(0f, 6f, 91, 93)
    }
}
