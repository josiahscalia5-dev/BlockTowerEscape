package com.blocktower.escape.core

/**
 * Level 5: the volcanic sky fortress, from the approved Level 5 design (design/level5_volcano_reference.png).
 *
 * The fortress and its glowing portal stand far away at the top of the screen from the first second; the course
 * is one long, dangerous route over the lava sea to reach it. Rows run along +z; "lvl" is the walking-surface
 * height (block top). The coloured blocks stand on fortress-stone pillars rising out of the lava; wooden bridges
 * with chain railings cross the wider gaps; torches and red banners line the way.
 *
 *  1 the volcanic approach            (speed pads, first jumps and blue blocks)       CHECKPOINT 1
 *  2 choose your path                 (left: a safe bridge, right: treasure on moving blocks)
 *  3 the great loop                   (run-up with speed pads, the loop itself: coins and spike plates)
 *                                                                                      CHECKPOINT 2
 *  4 the Runaway Relic                (CHASE & COLLECT: a bonus you can miss)          CHECKPOINT 3
 *  5 the Guardian chase               (GUARDIAN APPROACHING! spikes, gaps, moving blocks, a mace)
 *                                                                                      CHECKPOINT 4
 *  6 the harder obstacles             (cracked, vanishing and moving blocks, maces, lava gaps, a narrow bridge)
 *                                                                                      CHECKPOINT 5
 *  7 the final ascent                 (the bridge collapses behind you: climb, speed pads, moving platforms)
 *  8 the fortress stairs and the portal
 *
 * Row string legend: see [CourseWriter] ('p' is a speed pad, 'K' fortress stone in this level).
 */
object Level5 {
    fun build(): World {
        val w = World()
        w.rowMin = -8; w.rowMax = 360
        w.initRows()
        Level5Writer(w).write()
        return w
    }
}

private class Level5Writer(w: World) : CourseWriter(w, 5) {
    /**
     * A fortress-stone pillar out of the lava under rows z0..z1, [width] wide around x. Its top is just under the
     * lowest walking level of those rows (never above a step it holds up); [lvl] when the rows have none yet.
     */
    fun pillar(z0: Int, z1: Int, lvl: Float, x: Float = 0f, width: Float = 3.3f) {
        var low = lvl + lo
        for (z in z0..z1) w.pathLevel[z + zo]?.let { low = kotlin.math.min(low, it) }
        w.pillars.add(Pillar(ox + x, (z0 + z1 + 1) * 0.5f + zo, low - 1f, width, (z1 - z0 + 1) - 0.1f))
    }
    /** Wooden bridge planks over the lava (rows z0..z1, lanes x0..x1), chain railings at both sides. */
    fun bridge(z0: Int, z1: Int, lvl: Float, x0: Int = -1, x1: Int = 1, rails: Boolean = true) {
        for (z in z0..z1) {
            for (x in x0..x1) {
                val b = blk(x.toFloat(), lvl, z, BC.WOOD, BT.BRICK)
                b.y = lvl + lo - 0.35f; b.sy = 0.35f; b.remember()
            }
            setPath(z, lvl)
        }
        if (rails) {
            w.decos.add(Deco(DK.RAIL, ox + x0 - 0.62f, lvl + lo, (z0 + zo).toFloat(), (z1 - z0 + 1).toFloat()))
            w.decos.add(Deco(DK.RAIL, ox + x1 + 0.62f, lvl + lo, (z0 + zo).toFloat(), (z1 - z0 + 1).toFloat()))
        }
    }
    /** A fortress tower beside the path: a pillar with a fire bowl on top and the red banner on its face. */
    fun tower(x: Float, lvl: Float, z: Int, banner: Boolean = true) {
        val top = lvl + lo + 1.2f
        w.pillars.add(Pillar(ox + x, z + zo + 0.5f, top, 1.3f, 1.3f))
        w.decos.add(Deco(DK.TORCH, ox + x, top + 2.2f - 2.2f + 0.02f, z + zo + 0.5f, 1f))
        if (banner) w.decos.add(Deco(DK.BANNER, ox + x, top - 0.25f, z + zo + 0.5f - 0.67f, 0.85f))
    }
    /** A spiked mace on its chain, hung from a stone beam across the path. [atGo]: where it is when the countdown ends (-1..1). */
    fun mace(x: Float, lvl: Float, z: Int, amp: Float, period: Float, atGo: Float) {
        val len = 3.3f
        val py = lvl + lo + 4.1f
        val w0 = TAU / period
        val phase = kotlin.math.asin(clamp(atGo, -1f, 1f)) - w0 * 3.0f
        w.logs.add(SwingLog(ox + x, py, z + zo + 0.5f, len, amp, period, phase))
        // the posts holding the beam
        w.pillars.add(Pillar(ox + x - 3.1f, z + zo + 0.5f, py + 0.02f, 0.6f, 0.6f))
        w.pillars.add(Pillar(ox + x + 3.1f, z + zo + 0.5f, py + 0.02f, 0.6f, 0.6f))
    }
    fun stoneRow(z: Int, lvl: Float, x0: Int, x1: Int) { for (x in x0..x1) blk(x.toFloat(), lvl, z, BC.FORT, BT.BRICK); setPath(z, lvl) }
    /** A coin placed exactly (world units relative to the section). */
    fun coinAt(x: Float, y: Float, z: Float) { w.coins.add(Coin(ox + x, y + lo, z + zo)); w.coinTotal++ }
    /** A checkpoint platform of fortress stone. */
    fun fortCheckpoint(z: Int, lvl: Int) {
        row(z, lvl, "K.K"); checkpoint(z, lvl)
    }

    fun write() {
        w.seaDepth = 10f
        // ================= SECTION 1 : THE VOLCANIC APPROACH =================
        ox = 0f; lo = 0f; zo = 0
        section("approach", -8, 43)
        w.spawnX = 0f; w.spawnY = 0f; w.spawnZ = 0.45f
        w.checkpoints.add(Checkpoint(0, 0f, 0f, 0.45f).also { it.active = true })
        row(-4, 0, "GRG"); row(-3, 0, "YGY"); row(-2, 0, "RYR"); row(-1, 0, "GRG")
        row(0, 0, "YGY"); row(1, 0, "RYR"); row(2, 0, "GRG"); row(3, 0, "YRY"); row(4, 0, "GYG")
        pillar(-4, 4, 0f)
        trig(0, Ev.ZONE, "THE VOLCANO FORTRESS", "THE PORTAL IS FAR AWAY — RUN!")
        // the first lava gap (a coin arc over it)
        coin(0f, 0.9f, 5); coin(0f, 1.2f, 6)
        row(7, 0.5f, "PYP"); row(8, 1, "YBY"); row(9, 1, "GRG")
        pillar(7, 9, 1f)
        trig(6, Ev.HINT_TARGET)
        // the fortress courtyard: a wide stretch to find your feet
        row(10, 1, "GRYRG", -2); row(11, 1, "YGRGY", -2); row(12, 1.5f, "RYGYR", -2); row(13, 2, "GYRYG", -2)
        pillar(10, 13, 2f, 0f, 5.3f)
        // a wide wooden bridge over the lava
        bridge(14, 19, 2f, -2, 2)
        coinLine(0f, 2f, 14, 19)
        row(20, 2, "YRGRY", -2); row(21, 2, "GtYtG", -2); row(22, 2, "YRGRY", -2); row(23, 2, "GYRYG", -2)
        pillar(20, 23, 2f, 0f, 5.3f)
        mystery(-3.2f, 3.2f, 21, Reward.COINS, BC.GREEN)
        row(24, 2.5f, "GBG"); row(25, 3, "YRY"); row(26, 3, "PGP")
        pillar(24, 26, 3f)
        trig(25, Ev.TUT, "Blue chevron pads give a burst of SPEED!", "7")
        // spikes in the middle lane: steer round them
        row(27, 3, "R^R"); row(28, 3, "GYG"); row(29, 3, "YpY"); row(30, 3, "RGR")
        pillar(27, 30, 3f)
        // a long jump (take the pad's speed into it)
        coin(0f, 4.1f, 31); coin(0f, 4.4f, 32)
        row(33, 3, "GYG"); row(34, 3.5f, "YBY"); row(35, 4, "GRG")
        pillar(33, 35, 4f)
        row(36, 4, "cYc"); row(37, 4, "cRc"); row(38, 4, "cYc"); row(39, 4, "GYG")
        pillar(36, 39, 4f)
        row(40, 4, "RGR"); row(41, 4, "GYG"); fortCheckpoint(42, 4); row(43, 4, "YGY")
        pillar(40, 43, 4f)
        coinLine(0f, 1f, 10, 13); coinLine(0f, 3f, 24, 26); coinLine(0f, 4f, 39, 41)
        for ((i, z) in intArrayOf(3, 11, 20, 28, 37).withIndex()) tower(if (i % 2 == 0) -3.3f else 3.3f, w.levelAt(z) , z)

        // ================= SECTION 2 : CHOOSE YOUR PATH =================
        zo = 44; lo = 4f
        section("fork", 0, 17)
        row(0, 0, "YRGRY", -2); row(1, 0, "GYRYG", -2)
        pillar(0, 1, 0f, 0f, 5.3f)
        trig(1, Ev.FORK)
        // left: the safe bridge with a line of coins
        ox = -2.5f
        bridge(2, 12, 0f, -1, 0)
        coinLine(-0.5f, 0f, 2, 12)
        pillar(6, 7, 0f, -0.5f, 2.3f)
        // right: treasure on moving blocks (a blue block and a gem box), gaps between
        ox = 2.5f
        row(2, 0, "GY", 0, false); row(3, 0, "YG", 0, false)
        moving(0.5f, 0, 5, 0.9f, 1.6f, 0f, 2f, BC.PURPLE)
        row(7, 0, "RB", 0, false); row(8, 0.5f, "YG", 0, false)
        mystery(1.5f, 1.5f, 8, Reward.GEMS, BC.GREEN)
        moving(0.5f, 1, 10, 0.9f, 1.9f, 1.5f, 2f, BC.YELLOW)
        row(12, 0.5f, "GR", 0, false)
        pillar(2, 3, 0f, 0.5f, 2.3f); pillar(7, 8, 0.5f, 0.5f, 2.3f); pillar(12, 12, 0.5f, 0.5f, 2.3f)
        coin(0.5f, 0.6f, 5); coin(1f, 1.1f, 10)
        // both ways meet again
        ox = 0f
        row(13, 0.5f, "YRGRY", -2); row(14, 0.5f, "GYBYG", -2); row(15, 1, "RGYGR", -2); row(16, 1, "GYRYG", -2); row(17, 1, "YGpGY", -2)
        pillar(13, 17, 1f, 0f, 5.3f)
        for (z in 2..12) setPath(z, 0f)
        w.pathX.keys.filter { it in (2 + zo)..(12 + zo) }.forEach { w.pathX[it] = -2.5f }
        tower(-5.4f, 0f, 4); tower(5.4f, 1f, 14)

        // ================= SECTION 3 : THE GREAT LOOP =================
        zo = 62; lo = 5f
        section("loop", 0, 27)
        trig(0, Ev.ZONE, "THE GREAT LOOP", "KEEP YOUR SPEED UP!")
        row(0, 0, "RGR"); row(1, 0, "YpY"); row(2, 0, "GRG"); row(3, 0, "YBY"); row(4, 0, "RpR")
        pillar(0, 4, 0f)
        row(5, 0, "GYG"); row(6, 0, "YRY"); row(7, 0, "GpG"); row(8, 0, "RYR"); row(9, 0, "GYG"); row(10, 0, "YpY"); row(11, 0, "GRG")
        pillar(5, 11, 0f)
        coinLine(0f, 0f, 5, 11)
        // the loop: its foot at row 12, radius 4.2, leaving three lanes to the right
        val loop = Loop(ox, lo, (12 + zo).toFloat(), 4.2f, 3f)
        w.loops.add(loop)
        for (i in 0 until 11) {
            val u = 0.12f + i * 0.075f
            val lat = if (i % 4 == 1) -1f else if (i % 4 == 3) 1f else 0f
            w.coins.add(Coin(loop.cx(u) + lat, loop.sy(u, 0.85f), loop.sz(u, 0.85f))); w.coinTotal++
        }
        loop.spikes.add(floatArrayOf(0.34f, -1f)); loop.spikes.add(floatArrayOf(0.5f, 1f)); loop.spikes.add(floatArrayOf(0.7f, 0f))
        w.pillars.add(Pillar(ox, 12f + zo, lo - 1f - 0.9f, 3.4f, 2.2f))
        w.pillars.add(Pillar(ox + 3f, 12f + zo, lo - 1f - 0.9f, 3.4f, 2.2f))
        // out of the loop three lanes to the right, then back across
        ox = 3f
        row(12, 0, "YGY"); row(13, 0, "RYR"); row(14, 0, "GRG"); row(15, 0, "YBY"); row(16, 0, "RGR")
        pillar(13, 16, 0f)
        fortCheckpoint(17, 0)
        row(18, 0, "GYG"); row(19, 0, "YRY")
        pillar(17, 19, 0f)
        ox = 0f
        row(20, 0, "RGRYG", -1); row(21, 0, "GYGRY", -1); row(22, 0.5f, "YRYGR", -1); row(23, 1, "GRG"); row(24, 1, "YGY")
        row(25, 1, "RYR"); row(26, 1, "GRG"); row(27, 1, "YGY")
        pillar(20, 22, 0f, 1f, 5.3f); pillar(23, 27, 1f)
        coinLine(0f, 1f, 23, 27)
        tower(-3.3f, 0f, 6); tower(-4.6f, 0f, 16); tower(6.3f, 0f, 18); tower(-3.3f, 1f, 25)

        // ================= SECTION 4 : THE RUNAWAY RELIC =================
        zo = 90; lo = 6f
        section("relic", 0, 62)
        trig(0, Ev.RELIC)
        w.relicZ0 = 2 + zo; w.relicZ1 = 60 + zo
        row(0, 0, "GYG"); row(1, 0, "RGR"); row(2, 0, "YRY"); row(3, 0, "GYG"); row(4, 0, "YpY"); row(5, 0, "RGR"); row(6, 0, "GYG")
        pillar(0, 6, 0f)
        // a gap, then up a step
        row(9, 0.5f, "YGY"); row(10, 1, "RBR"); row(11, 1, "GYG"); row(12, 1, "YRY"); row(13, 1, "GYG"); row(14, 1, "RYR")
        pillar(9, 14, 1f)
        bridge(15, 19, 1f)
        row(20, 1, "GpG"); row(21, 1, "YRY")
        pillar(20, 21, 1f)
        // a single stepping stone over the lava (the relic hops it; you jump on and off it)
        row(23, 1, "O"); pillar(23, 23, 1f, 0f, 1.2f)
        row(25, 1, "GRG"); row(26, 1.5f, "YGY"); row(27, 2, "RGR"); row(28, 2, "cYc"); row(29, 2, "cRc"); row(30, 2, "GYG")
        pillar(25, 30, 2f)
        row(32, 2, "YBY"); row(33, 2, "GRG"); row(34, 2, "tYt"); row(35, 2, "GRG"); row(36, 2, "YGY"); row(37, 2, "RGR")
        pillar(32, 37, 2f)
        // spring up to the high road
        row(38, 2, "GbG"); row(39, 4, "YRY"); row(40, 4, "GYG"); row(41, 4, "RpR"); row(42, 4, "GYG")
        pillar(38, 38, 2f); pillar(39, 42, 4f)
        coin(0f, 3.6f, 38)
        bridge(43, 48, 4f)
        row(49, 4, "YGY"); row(50, 4, "RBR"); row(51, 4.5f, "GYG"); row(52, 5, "YGY"); row(53, 5, "GRG")
        pillar(49, 53, 5f)
        row(55, 5, "RGR"); row(56, 5, "YGY"); row(57, 5, "GRG"); row(58, 5, "YRY"); row(59, 5, "GYG"); row(60, 5, "RGR")
        pillar(55, 60, 5f)
        fortCheckpoint(62, 5); row(61, 5, "GYG")
        pillar(61, 62, 5f)
        coinLine(0f, 0f, 0, 6); coinLine(0f, 1f, 11, 14); coinLine(0f, 2f, 27, 30); coinLine(0f, 4f, 39, 42); coinLine(0f, 5f, 55, 60)
        for ((i, z) in intArrayOf(3, 12, 21, 30, 41, 50, 58).withIndex()) tower(if (i % 2 == 0) 3.3f else -3.3f, w.levelAt(z + zo) - lo, z)

        // ================= SECTION 5 : THE GUARDIAN CHASE =================
        zo = 153; lo = 11f
        section("chase", 0, 46)
        stoneRow(0, 0f, -1, 1); stoneRow(1, 0f, -1, 1)
        pillar(0, 1, 0f)
        trig(2, Ev.CHASE)
        row(3, 0, "GYG"); row(4, 0, "RYR"); row(5, 0, "GRG"); row(6, 0, "YRY")
        pillar(2, 6, 0f)
        row(2, 0, "YGY")
        row(8, 0, "tYt"); row(9, 0, "GRG"); row(10, 0, "YBY"); row(11, 0, "RGR")
        pillar(8, 11, 0f)
        moving(0f, 0, 13, 1.0f, 2.0f, 0f, 2.4f, BC.YELLOW)
        save(0f, -3, 13)
        row(15, 0, "GYG"); row(16, 0, "ccc"); row(17, 0, "cYc"); row(18, 0, "ccc"); row(19, 0, "GYG")
        pillar(15, 19, 0f)
        // a narrow bridge: one lane
        bridge(20, 25, 0f, 0, 0)
        mace(0f, 0f, 27, 0.95f, 3.0f, 0.2f)
        row(26, 0, "GYG"); row(27, 0, "YRY"); row(28, 0, "GYG")
        pillar(26, 28, 0f)
        row(30, 0.5f, "RGR"); row(31, 1, "Y^Y"); row(32, 1, "GRG"); row(33, 1, "YpY"); row(34, 1, "RGR")
        pillar(30, 34, 1f)
        row(36, 1, "GYG"); row(37, 1, "fff"); row(38, 1, "fff"); row(39, 1, "GYG"); row(40, 1, "YBY")
        pillar(36, 40, 1f)
        coinLine(0f, 0f, 3, 6); coinLine(0f, 0f, 9, 11); coinLine(0f, 1f, 32, 34); coin(0f, 1.2f, 13)
        collapsible(-2, 41)
        // CHECKPOINT 4: the Guardian's bridge gives way behind it
        for (z in 41..46) if (z != 43) row(z, 1, if (z % 2 == 0) "GYGYG" else "YGYGY", -2)
        row(43, 1, "GY.YG", -2); checkpoint(43, 1)
        trig(42, Ev.CHASE_END)
        mystery(-2f, 1f, 45, Reward.HEART, BC.GREEN)
        mystery(2f, 1f, 45, Reward.TOOL_SHIELD, BC.GREEN)
        pillar(41, 46, 1f, 0f, 5.3f)
        coin(-1f, 1f, 45); coin(1f, 1f, 45); coin(0f, 1f, 46)
        for ((i, z) in intArrayOf(4, 16, 28, 38).withIndex()) tower(if (i % 2 == 0) -3.3f else 3.3f, w.levelAt(z + zo) - lo, z)

        // ================= SECTION 6 : THE HARDER OBSTACLES =================
        zo = 200; lo = 12f
        section("hard", 0, 50)
        trig(0, Ev.ZONE, "THE FIRE BRIDGES", "TIME EVERY STEP")
        row(0, 0, "GYG"); row(1, 0, "RGR")
        pillar(0, 1, 0f)
        // vanishing stepping stones over the lava
        row(3, 0, "d.d", -1); row(4, 0.5f, ".d.", -1); row(5, 1, "d.d", -1); row(6, 1, "GYG")
        save(0f, -3, 4)
        pillar(6, 6, 1f)
        coin(0f, 1.1f, 3); coin(0f, 1.6f, 4); coin(0f, 2.1f, 5)
        row(7, 1, "YBY"); row(8, 1, "GRG")
        pillar(7, 8, 1f)
        mace(0f, 1f, 10, 1.0f, 3.0f, -0.6f)
        row(9, 1, "YGY"); row(10, 1, "RYR"); row(11, 1, "GYG")
        pillar(9, 11, 1f)
        moving(0f, 1, 13, 1.2f, 2.3f, 1f, 2f, BC.PURPLE)
        moving(0f, 1, 15, 1.2f, 2.3f, 2.6f, 2f, BC.GREEN)
        save(0f, -2, 14)
        row(17, 1, "GYG"); row(18, 1, "^Y^"); row(19, 1, "GRG"); row(20, 1, "tYt"); row(21, 1, "GYG")
        pillar(17, 21, 1f)
        bridge(22, 27, 1f, 0, 0)
        mace(0f, 1f, 24, 0.9f, 3.2f, 0.7f)
        row(28, 1, "YGY"); row(29, 1.5f, "fYf"); row(30, 2, "fRf"); row(31, 2, "GpG"); row(32, 2, "YBY")
        pillar(28, 32, 2f)
        row(34, 2.5f, "ccc"); row(35, 3, "cYc"); row(36, 3, "ccc"); row(37, 3, "GYG")
        pillar(34, 37, 3f)
        row(39, 3, "d.d", -1); row(40, 3.5f, ".d.", -1); row(41, 4, "YGY")
        save(0f, 0, 40)
        row(42, 4, "RBR"); row(43, 4, "GYG"); row(44, 4, "YRY")
        pillar(41, 44, 4f)
        row(45, 4, "GYG"); fortCheckpoint(46, 4); row(47, 4, "YGY"); row(48, 4, "GRG"); row(49, 4, "YGY"); row(50, 4, "GYG")
        pillar(45, 50, 4f)
        coinLine(0f, 1f, 7, 11); coinLine(0f, 1f, 17, 21); coinLine(0f, 2f, 30, 32); coinLine(0f, 4f, 42, 45)
        for ((i, z) in intArrayOf(2, 9, 19, 30, 43).withIndex()) tower(if (i % 2 == 0) 3.3f else -3.3f, w.levelAt(z + zo) - lo, z)

        // ================= SECTION 7 : THE FINAL ASCENT =================
        zo = 251; lo = 16f
        section("ascent", 0, 70)
        trig(1, Ev.FINAL)
        row(0, 0, "GYG"); row(1, 0, "YpY")
        pillar(0, 1, 0f)
        for (i in 0 until 10) {
            val z = 2 + i
            row(z, 0.5f * (i + 1), if (i % 3 == 2) "RBR" else if (i % 2 == 0) "YGY" else "GRG")
        }
        pillar(2, 11, 5f)
        row(12, 5, "GpG"); row(13, 5, "YRY")
        pillar(12, 13, 5f)
        moving(0f, 5, 15, 1.1f, 2.0f, 0.8f, 2.2f, BC.YELLOW)
        save(0f, 2, 15)
        row(17, 5.5f, "GYG"); row(18, 6, "Y.Y", -1); row(19, 6.5f, ".G.", -1); row(20, 7, "YRY")
        pillar(17, 20, 7f)
        row(21, 7.5f, "GpG"); row(22, 8, "YGY"); row(23, 8.5f, "RBR"); row(24, 9, "GYG")
        pillar(21, 24, 9f)
        bridge(25, 30, 9f)
        row(31, 9.5f, "YGY"); row(32, 10, "GRG"); row(33, 10.5f, "YpY"); row(34, 11, "GYG")
        pillar(31, 34, 11f)
        moving(0f, 11, 36, 1.0f, 2.4f, 0f, 2f, BC.PURPLE)
        moving(0f, 11, 38, 1.0f, 2.4f, 2.2f, 2f, BC.GREEN)
        save(0f, 8, 37)
        row(40, 11.5f, "GYG"); row(41, 12, "RBR"); row(42, 12.5f, "YGY"); row(43, 13, "GpG")
        pillar(40, 43, 13f)
        coinLine(0f, 5f, 12, 13); coinLine(0f, 9f, 25, 30); coinLine(0f, 11f, 31, 34)
        for (i in 0 until 10) coin(0f, 0.5f * (i + 1), 2 + i)
        collapsible(-6, 44)
        for ((i, z) in intArrayOf(4, 13, 22, 32, 42).withIndex()) tower(if (i % 2 == 0) -3.3f else 3.3f, w.levelAt(z + zo) - lo, z)

        // ================= SECTION 8 : THE FORTRESS STAIRS AND THE PORTAL =================
        zo = 295; lo = 29f
        section("portal", 0, 20)
        w.finalSafeZ = (0 + zo).toFloat()
        row(0, 0, "YGY"); row(1, 0, "GRG")
        pillar(0, 1, 0f)
        val stairs = arrayOf("KYPYK", "KGRGK", "KYPYK", "KGBGK", "KYPYK", "KGRGK", "KYPYK", "KGRGK", "KYPYK", "KGRGK")
        for ((i, st) in stairs.withIndex()) row(2 + i, 0.5f + i * 0.5f, st, -2)
        for (i in stairs.indices) coin(0f, 0.5f + i * 0.5f, 2 + i)
        pillar(2, 11, 5f, 0f, 5.3f)
        stoneRow(12, 5f, -2, 2); stoneRow(13, 5f, -2, 2); stoneRow(14, 5f, -2, 2)
        pillar(12, 14, 5f, 0f, 5.3f)
        tower(-3.8f, 5f, 12); tower(3.8f, 5f, 12)
        // the fortress's portal (the design's destination) at the far edge of the plaza
        w.portals.add(Portal(ox, 5f + lo, (15 + zo).toFloat() + 0.2f))

        // in the fortress every stone and checkpoint post is fortress stone, every spike base dark basalt
        for (b in w.blocks) {
            if (b.color == BC.BRICK && b.type == BT.BRICK) b.color = BC.FORT
            if (b.type == BT.TRAP || b.type == BT.CHECKPOINT) b.color = BC.BASALT
            b.remember()
        }
    }
}
