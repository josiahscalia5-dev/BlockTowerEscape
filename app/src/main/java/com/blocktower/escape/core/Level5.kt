package com.blocktower.escape.core

/**
 * Level 5: the jungle temple. One climb from the temple entrance up to the golden portal, the portal
 * at the top of the Level 5 design. Rows run along +z; "lvl" is the walking-surface height (block top).
 *
 * The opening rows reproduce the Level 5 design as seen from the start: the blue star block under the boy,
 * the spike platform and the floating ? block on the left, the ? block and the red spring button on the
 * right, the magnet, the spiked log swinging from its rope island, the stone steps up to the CHECKPOINT
 * arch, the Temple Guardian watching from the ruins on the left, the shield, the lightning block and the
 * coin trail winding up to the portal. After that the temple is built in nine sections:
 *
 *  1 temple entrance      2 colourful block climb    3 moving blocks and hazards
 *  4 tool trials          5 Temple Guardian chase    6 checkpoint
 *  7 difficult temple climb   8 final approach to the portal   9 escape through the portal
 *
 * Row string legend: see [CourseWriter]. In this level 'K' is carved temple stone, '^' a temple slab
 * with spikes and 'b' the red spring button.
 */
object Level5 {
    fun build(): World {
        val w = World()
        w.rowMin = -8; w.rowMax = 260
        w.initRows()
        Level5Writer(w).write()
        return w
    }
}

private class Level5Writer(w: World) : CourseWriter(w, 5) {
    /** Carved temple stone (walkable). */
    fun stone(x: Float, lvl: Float, z: Int) = blk(x, lvl, z, BC.TEMPLE, BT.BRICK)
    fun stoneRow(z: Int, lvl: Float, x0: Int, x1: Int) { for (x in x0..x1) stone(x.toFloat(), lvl, z) }
    /** A column of temple stone under a block (so raised blocks stand on something). */
    fun under(x: Float, topLvl: Float, z: Int, n: Int) { for (k in 1..n) blk(x, topLvl - k, z, BC.TEMPLE, BT.BRICK) }
    fun spikeSlab(x: Float, lvl: Float, z: Int) = blk(x, lvl, z, BC.TEMPLE_DARK, BT.TRAP).also { it.speed = 0f }
    fun spring(x: Float, lvl: Float, z: Int) = blk(x, lvl, z, BC.TEMPLE, BT.BOUNCE)
    /** A carved stone ledge (one block deep) under rows z0..z1, from x0 to x1, top at [top]. */
    fun ledge(z0: Int, z1: Int, x0: Float, x1: Float, top: Float) {
        for (z in z0..z1) { var x = x0; while (x <= x1 + 0.01f) { stone(x, top, z); x += 1f } }
    }
    /** A coin at an exact height (the design's big coins resting on blocks). */
    fun coinAt(x: Float, y: Float, z: Float) { w.coins.add(Coin(ox + x, y + lo, z + zo)); w.coinTotal++ }
    fun deco(kind: Int, x: Float, y: Float, z: Float, width: Float) { w.decos.add(Deco(kind, ox + x, y + lo, z + zo, width)) }
    /** [atGo]: where the log is when the countdown ends, -1 (left end of its swing) .. 1 (right end). */
    fun swingLog(x: Float, pivotLvl: Float, z: Float, len: Float, amp: Float, period: Float, atGo: Float) {
        val w0 = TAU / period
        val phase = kotlin.math.asin(clamp(atGo, -1f, 1f)) - w0 * 3.0f
        w.logs.add(SwingLog(ox + x, pivotLvl + lo, z + zo, len, amp, period, phase))
    }
    /** Temple-stone checkpoint platform under the CHECKPOINT arch from the design. */
    fun archCheckpoint(z: Int, lvl: Int, x: Float = 0f) {
        stone(x - 1f, lvl.toFloat(), z); stone(x + 1f, lvl.toFloat(), z)
        checkpoint(z, lvl, x)
    }

    fun write() {
        // ================= SECTION 1 : THE TEMPLE ENTRANCE — the Level 5 design =================
        // Positions were solved from the design: each object stands where the start camera sees it there.
        ox = 0f; lo = 0f; zo = 0
        section("entrance", -6, 33)
        w.spawnX = 0f; w.spawnY = 0f; w.spawnZ = 0.45f
        w.checkpoints.add(Checkpoint(0, 0f, 0f, 0.45f).also { it.active = true })
        // behind the start: the green and red blocks at the bottom of the design
        blk(-1.2f, -1.5f, -2, BC.RED); blk(0f, -1.5f, -2, BC.YELLOW); blk(1.05f, -1.5f, -2, BC.GREEN)
        blk(0f, -1f, -1, BC.GREEN); blk(1.05f, -1f, -1, BC.RED); stone(2.1f, -1f, -1)
        setPath(-2, -1.5f); setPath(-1, -1f)
        // the start row: red on the left, the blue star block under the boy, yellow, carved stone
        blk(-1.15f, 0.5f, 0, BC.RED); under(-1.15f, 0.5f, 0, 1)
        blk(0f, 0f, 0, BC.BLUE).star = true
        blk(1.05f, 0f, 0, BC.YELLOW)
        stone(2.1f, -0.25f, 0)
        setPath(0, 0f)
        // row 1: the raised green block (with a coin) on the left, the red spring button on the right
        blk(-1.0f, 1.0f, 1, BC.GREEN); under(-1.0f, 1.0f, 1, 2)
        blk(0f, 0f, 1, BC.YELLOW)
        spring(1.15f, 0f, 1)
        stone(2.2f, -0.25f, 1)
        setPath(1, 0f)
        blk(-0.8f, 1.0f, 2, BC.YELLOW); under(-0.8f, 1.0f, 2, 2)
        blk(0.2f, 0.5f, 2, BC.GREEN); stone(1.2f, 0f, 2)
        setPath(2, 0.5f)
        blk(-0.3f, 0.5f, 3, BC.PURPLE); blk(0.7f, 1.0f, 3, BC.RED); under(0.7f, 1.0f, 3, 1)
        setPath(3, 0.5f)
        // the tall blue block (a target) and the yellow block, the magnet floating on the right
        blk(-0.3f, 1.5f, 4, BC.BLUE, BT.TARGET); under(-0.3f, 1.5f, 4, 1)
        blk(0.7f, 1.0f, 4, BC.YELLOW)
        blk(0f, 1.0f, 5, BC.YELLOW); under(0f, 1.0f, 5, 1)
        bubble(1.0f, 0.5f, 5, TK.MAGNET)
        setPath(4, 1f); setPath(5, 1f)
        // the spiked log swings across here
        blk(0f, 1.5f, 6, BC.GREEN); under(0f, 1.5f, 6, 1)
        // the green ? block floating low on the right, beside the mossy log bridge
        mystery(1.45f, 1.7f, 6, Reward.COINS, BC.GREEN)
        setPath(6, 1.5f)
        stone(-0.5f, 1.5f, 7); stone(0.5f, 1.5f, 7); under(-0.5f, 1.5f, 7, 1); under(0.5f, 1.5f, 7, 1)
        setPath(7, 1.5f)
        // the spike platform on the left
        spikeSlab(-2.2f, 1.0f, 8); spikeSlab(-1.2f, 1.0f, 8); under(-2.2f, 1.0f, 8, 1); under(-1.2f, 1.0f, 8, 1)
        blk(0f, 2.0f, 8, BC.YELLOW); under(0f, 2.0f, 8, 1)
        setPath(8, 2f)
        // the green star block, the red block and the stone steps up to the CHECKPOINT arch
        blk(0.6f, 2.0f, 9, BC.GREEN).star = true; under(0.6f, 2.0f, 9, 1)
        blk(-0.4f, 2.0f, 9, BC.RED)
        blk(1.0f, 2.5f, 10, BC.RED); blk(0f, 2.5f, 10, BC.YELLOW)
        mystery(-2.45f, 2.3f, 10, Reward.GEMS, BC.GREEN)
        stoneRow(11, 2.5f, 0, 2); stoneRow(12, 3.0f, 0, 2); stoneRow(13, 3.0f, 0, 2)
        setPath(9, 2f); setPath(10, 2.5f); setPath(11, 2.5f); setPath(12, 3f); setPath(13, 3f)
        ox = 0.4f
        archCheckpoint(14, 3, 1f)
        ox = 0f
        setPath(14, 3f)
        // after the checkpoint the path swings left past the guardian's ruins, then right toward the portal,
        // in clusters of blocks on carved ledges (as in the design) with short hops between them
        stone(0.4f, 3.5f, 15); stone(1.4f, 3.5f, 15); blk(-0.6f, 4.0f, 15, BC.PURPLE); under(-0.6f, 4.0f, 15, 1)
        blk(-0.6f, 4.0f, 16, BC.GREEN); blk(0.4f, 4.0f, 16, BC.YELLOW)
        ledge(16, 16, -1.1f, 0.9f, 3.0f)
        // hop up to the guardian's ruins (row 17 is a gap)
        blk(-0.8f, 5.0f, 18, BC.RED); blk(0.2f, 5.0f, 18, BC.BLUE, BT.TARGET)
        stone(-2.8f, 5.5f, 19); stone(-1.8f, 5.5f, 19); blk(-0.8f, 5.0f, 19, BC.GREEN); blk(0.2f, 5.0f, 19, BC.YELLOW)
        ledge(18, 19, -1.3f, 0.7f, 4.0f); under(-2.8f, 5.5f, 19, 2); under(-1.8f, 5.5f, 19, 2)
        // the shield floats over the next gap
        bubble(0.9f, 5.2f, 20, TK.SHIELD)
        blk(0.4f, 6.0f, 21, BC.RED); blk(1.4f, 6.0f, 21, BC.PURPLE)
        blk(0.8f, 6.0f, 22, BC.YELLOW); blk(1.8f, 6.0f, 22, BC.GREEN)
        ledge(21, 22, -0.1f, 2.3f, 5.0f)
        blk(1.4f, 7.0f, 24, BC.YELLOW); blk(2.4f, 7.0f, 24, BC.GREEN)
        ledge(24, 24, 0.9f, 2.9f, 6.0f)
        // the lightning block floats over the right lane: jump into it from below
        mystery(2.9f, 9.5f, 24, Reward.TOOL_SPEED, BC.BLUE)
        setPath(15, 3.5f); setPath(16, 4f); setPath(17, 4f); setPath(18, 5f); setPath(19, 5f); setPath(20, 5f)
        setPath(21, 6f); setPath(22, 6f); setPath(23, 6f); setPath(24, 7f)
        // the coin trail winding up toward the portal
        val trail = floatArrayOf(1.8f, 1.5f, 1.2f, 0.9f, 1.0f, 1.3f, 1.6f, 1.7f, 1.5f)
        val tl = floatArrayOf(7.5f, 7.5f, 8.5f, 8.5f, 9.5f, 9.5f, 10.5f, 10.5f, 11.5f)
        for ((i, x) in trail.withIndex()) {
            val z = 25 + i
            blk(x, tl[i], z, palette[i % palette.size]); setPath(z, tl[i])
            if (i % 2 == 1) ledge(z - 1, z, x - 1.5f, x + 1.5f, tl[i] - 1f) else under(x, tl[i], z, 1)
        }
        // coins, as in the design (the big ones sit on the blocks next to the boy)
        coinAt(-0.85f, 1.05f, 0.7f); coinAt(-0.7f, 1.25f, 1.9f); coin(0f, 1.5f, 6); coin(0.6f, 2.0f, 9); coin(-0.2f, 2.5f, 10)
        coin(-0.4f, 4.0f, 16); coin(-0.8f, 5.0f, 18); coin(0f, 6.0f, 21); coin(1.6f, 7.0f, 23)
        for ((i, x) in trail.withIndex()) coin(x, tl[i], 25 + i)
        // the spiked log swings across the path, hanging on ropes from high above
        swingLog(1.5f, 7.3f, 5.0f, 5.6f, 0.55f, 3.4f, 0.46f)
        // scenery from the design: the rope island, the mossy log bridges beside the path
        deco(DK.ISLAND, 3.1f, 5.05f, 18.5f, 2.0f)
        deco(DK.BRIDGE_L, -2.7f, 2.3f, 16.8f, 4.0f)
        deco(DK.BRIDGE_R, 2.85f, -0.8f, 5.9f, 2.1f)
        // the Temple Guardian watches from the ruins on the left
        w.guardianLair = floatArrayOf(-2.8f, 5.4f, 20.5f)
        trig(1, Ev.HINT_TARGET)

        // ================= SECTION 2 : COLOURFUL BLOCK CLIMB =================
        zo = 34; lo = 11f
        section("climb", 0, 22)
        trig(0, Ev.ZONE, "SECTION 2", "COLOURFUL CLIMB")
        row(0, 0, "RGY"); row(1, 0.5f, "YBR"); row(2, 1, "GYP")
        trig(1, Ev.HINT_JUMP)
        // first gap with a coin arc
        coin(0f, 1.9f, 3); coin(0f, 2.2f, 4)
        row(5, 1, "PRG"); row(6, 1.5f, "RYG"); row(7, 2, "YGR")
        // side step up to a blue block on a raised pillar (optional)
        blk(2f, 3f, 7, BC.BLUE, BT.TARGET); under(2f, 3f, 7, 2)
        coin(2f, 3f, 6)
        row(8, 2.5f, "GRY"); row(9, 3, "PYG")
        mystery(0f, 5.6f, 9, Reward.COINS, BC.GREEN)
        row(11, 3, "RGY"); row(12, 3, "YRG"); row(13, 3.5f, "GPR")
        row(15, 3.5f, "YRG"); row(16, 4, "GYR"); row(17, 4, "RGY")
        mystery(-2f, 4f, 16, Reward.TOOL_MAGNET, BC.BLUE)
        row(19, 4, "PYP"); row(20, 4.5f, "YGY"); row(21, 5, "GRG"); row(22, 5, "RYR")
        coinLine(0f, 3f, 11, 13); coinLine(0f, 4f, 15, 17); coinLine(0f, 4.5f, 19, 22)
        for (z in intArrayOf(0, 5, 11, 15, 19)) { val l = w.levelAt(z + zo) - lo; under(-1f, l, z, 3); under(1f, l, z, 3) }

        // ================= SECTION 3 : MOVING BLOCKS AND HAZARDS =================
        zo = 57; lo = 16f
        section("hazards", 0, 32)
        trig(0, Ev.ZONE, "SECTION 3", "MOVING STONES")
        row(0, 0, "GYG"); row(1, 0, "GBG")
        // sliding platforms over a gap (golden save block below as a safety net)
        moving(0f, 0, 3, 1.1f, 1.4f, 0f, 2.2f, BC.GREEN)
        moving(0f, 0, 5, 1.1f, 1.4f, 3.14f, 2.2f, BC.YELLOW)
        save(0f, -3, 4)
        coin(0f, 0.6f, 3); coin(0f, 0.6f, 5)
        row(7, 0, "RYR"); row(8, 0, "cBc"); row(9, 0, "ccc")
        // a spiked log across the path: wait for it, then go
        stoneRow(10, 0f, -1, 1); stoneRow(11, 0f, -1, 1); stoneRow(12, 0f, -1, 1)
        swingLog(0f, 5.8f, 11.5f, 4.6f, 0.7f, 3.2f, 0.3f)
        coin(0f, 0.9f, 11)
        // crumbling temple stones: keep moving
        row(13, 0, "fff"); row(14, 0, "fff"); row(15, 0, "fff")
        row(16, 0, "GYG"); row(17, 0, "tYt"); row(18, 0, "GYG"); row(19, 0, "tBt"); row(20, 0, "GYG")
        // vanishing stepping stones
        row(22, 0, "d.d", -1); row(23, 0.5f, ".d.", -1); row(24, 1, "d.d", -1)
        save(0f, -3, 23)
        coin(0f, 1.1f, 22); coin(0f, 1.6f, 23); coin(0f, 2.1f, 24)
        row(25, 1, "RGR"); row(26, 1, "RYR")
        archCheckpoint(27, 1)
        row(28, 1, "GYG"); row(29, 1.5f, "YRY"); row(30, 2, "GRG"); row(31, 2, "YGY"); row(32, 2, "GYG")
        coinLine(0f, 1f, 25, 26); coinLine(0f, 2f, 30, 32)

        // ================= SECTION 4 : TOOL TRIALS =================
        zo = 91; lo = 18f
        section("tools", 0, 26)
        trig(0, Ev.ZONE, "SECTION 4", "TOOL TRIALS")
        row(0, 0, "YGY"); row(1, 0, "GYG")
        mystery(0f, 2.6f, 1, Reward.TOOL_BLOCK, BC.GREEN)
        trig(1, Ev.HINT_TOOLS)
        // blue blocks on islands out of reach: the magnet pulls them in
        row(2, 0, "RYR"); row(3, 0, "RYR"); row(4, 0, "RYR")
        blk(-3.2f, 0.5f, 3, BC.BLUE, BT.TARGET); blk(3.2f, 0.5f, 4, BC.BLUE, BT.TARGET)
        under(-3.2f, 0.5f, 3, 2); under(3.2f, 0.5f, 4, 2)
        trig(2, Ev.HINT_MAGNET)
        row(5, 0, "GYG"); row(6, 0, "GYG")
        // a gap too wide to jump: build a platform (BLOCK), or leap it with LIGHTNING
        trig(6, Ev.HINT_BLOCK)
        save(0f, -3, 9)
        coin(0f, 1.1f, 8); coin(0f, 1.3f, 9); coin(0f, 1.1f, 10)
        // ...or hop across the vanishing stones on the left
        for (z in intArrayOf(7, 9, 11)) blk(-3f, 0f, z, BC.PURPLE, BT.DISAPPEAR)
        row(12, 0, "YGY"); row(13, 0, "YBY"); row(14, 0, "YGY")
        bubble(1f, 0f, 14, TK.SHIELD)
        // spike run: jump the rows or let the shield take a hit
        row(15, 0, "^^^"); row(16, 0, "^^^"); row(17, 0, "^^^")
        coin(0f, 1.4f, 15); coin(0f, 1.8f, 16); coin(0f, 1.4f, 17)
        row(18, 0, "GYG"); row(19, 0, "GYG")
        // a high ledge: the spring button (or lightning's strong jump)
        row(20, 0, "PbP"); row(21, 2, "PYP"); row(22, 2, "PRP")
        for (z in 21..22) { under(-1f, 2f, z, 2); under(1f, 2f, z, 2) }
        coin(0f, 3f, 20)
        row(23, 2, "GYG"); row(24, 2, "GYG"); row(25, 2, "GYG"); row(26, 2, "GYG")
        coinLine(0f, 2f, 23, 26)

        // ================= SECTION 5 : TEMPLE GUARDIAN CHASE =================
        zo = 118; lo = 20f
        section("chase", 0, 38)
        // a checkpoint just before the chase: getting caught never sends you back through the tool trials
        archCheckpoint(0, 0); stoneRow(1, 0f, -1, 1); stoneRow(2, 0f, -1, 1)
        trig(2, Ev.CHASE)
        row(3, 0, "KYK"); row(4, 0, "KBK"); row(5, 0, "KYK")
        row(7, 0, "RYR"); row(8, 0, "RYR"); row(9, 0, "tYt"); row(10, 0, "RYR")
        row(12, 0, "ccc"); row(13, 0, "cYc"); row(14, 0, "ccc")
        stoneRow(15, 0.5f, -1, 1); stoneRow(16, 1f, -1, 1); row(17, 1, "K*K"); stoneRow(18, 1f, -1, 1)
        save(0f, -2, 20)
        row(22, 1, "RYR"); row(23, 1, "RBR"); row(24, 1, "RYR")
        moving(0f, 1, 26, 1.0f, 2.0f, 0f, 2.4f, BC.YELLOW)
        save(0f, -2, 26)
        row(28, 1, "KYK"); row(29, 1, "tYt"); row(30, 1, "KYK"); row(31, 1, "KYK")
        row(32, 1, "fff"); row(33, 1, "fff"); row(34, 1, "fff")
        row(35, 1, "KYK"); row(36, 1, "KYK"); row(37, 1, "KYK"); row(38, 1, "KYK")
        coinLine(0f, 0f, 3, 5); coinLine(0f, 0f, 7, 10); coinLine(0f, 0f, 12, 14)
        coinLine(0f, 1f, 22, 24); coinLine(0f, 1f, 28, 38)
        coin(0f, 2.4f, 20)
        collapsible(-4, 38)

        // ================= SECTION 6 : CHECKPOINT =================
        section("haven", 39, 48)
        for (z in 39..48) if (z != 42) row(z, 1, if (z % 2 == 0) "GYGYG" else "YGYGY", -2)
        row(42, 1, "GY.YG", -2); checkpoint(42, 1)
        trig(41, Ev.CHASE_END)
        mystery(-2f, 1f, 46, Reward.HEART, BC.GREEN)
        mystery(2f, 1f, 46, Reward.TOOL_SHIELD, BC.GREEN)
        for (z in intArrayOf(39, 44, 48)) { under(-2f, 1f, z, 3); under(2f, 1f, z, 3) }
        coin(-1f, 1f, 44); coin(1f, 1f, 44); coin(0f, 1f, 47)

        // ================= SECTION 7 : DIFFICULT TEMPLE CLIMB =================
        zo = 167; lo = 21f
        section("temple", 0, 36)
        trig(0, Ev.ZONE, "SECTION 7", "THE TEMPLE CLIMB")
        stoneRow(0, 0f, -1, 1); row(1, 0, "KbK")
        stoneRow(2, 2f, -1, 1); stoneRow(3, 2f, -1, 1); under(-1f, 2f, 2, 2); under(0f, 2f, 2, 2); under(1f, 2f, 2, 2)
        coin(0f, 3.4f, 1)
        row(4, 2, "tYt"); row(5, 2.5f, "YRY"); row(6, 3, "RYR")
        swingLog(0f, 8.8f, 7.5f, 4.6f, 0.72f, 3.0f, -0.5f)
        stoneRow(7, 3f, -1, 1); stoneRow(8, 3f, -1, 1)
        row(10, 3, "ddd"); row(11, 3.5f, "ddd"); row(12, 4, "ddd")
        save(0f, 0, 11)
        row(13, 4, "KYK"); row(14, 4, "KbK")
        stoneRow(15, 6f, -1, 1); under(-1f, 6f, 15, 2); under(0f, 6f, 15, 2); under(1f, 6f, 15, 2)
        row(16, 6, "GBG"); row(17, 6, "GYG")
        archCheckpoint(18, 6)
        row(19, 6, "RYR")
        moving(0f, 6, 21, 1.3f, 2.1f, 1f, 2.2f, BC.PURPLE)
        save(0f, 3, 21)
        row(23, 6, "fff"); row(24, 6.5f, "fff"); row(25, 7, "fff")
        row(26, 7, "YGY"); row(27, 7, "^Y^"); row(28, 7, "YGY")
        swingLog(0f, 12.6f, 29.5f, 4.4f, 0.7f, 2.8f, 0.8f)
        stoneRow(29, 7f, -1, 1); stoneRow(30, 7f, -1, 1)
        row(31, 7.5f, "PYP"); row(32, 8, "PRP"); row(33, 8.5f, "PYP"); row(34, 9, "PGP")
        row(35, 9, "KYK"); row(36, 9, "KYK")
        coinLine(0f, 2f, 5, 6); coinLine(0f, 3f, 7, 8); coinLine(0f, 6f, 16, 17); coinLine(0f, 7f, 26, 28)
        coin(0f, 4.2f, 10); coin(0f, 4.7f, 11); coin(0f, 5.2f, 12)
        for (z in intArrayOf(5, 13, 19, 26, 35)) { val l = w.levelAt(z + zo) - lo; under(-1f, l, z, 3); under(1f, l, z, 3) }

        // ================= SECTION 8 + 9 : FINAL APPROACH AND ESCAPE THROUGH THE PORTAL =================
        zo = 205; lo = 30f
        section("approach", 0, 28)
        trig(0, Ev.FINAL)
        row(0, 0, "KYK"); row(1, 0, "KYK"); row(2, 0, "KYK"); row(3, 0, "KYK")
        row(5, 0, "KOK"); row(6, 0, "KYK"); row(7, 0, "KOK")
        row(9, 0, "KYK"); row(10, 0, "KYK")
        coinLine(0f, 0f, 0, 3); coinLine(0f, 0f, 5, 7); coinLine(0f, 0f, 9, 10)
        coin(0f, 1.4f, 4); coin(0f, 1.4f, 8)
        collapsible(-38, 10)
        // the grand stairs up to the portal (the last blue block on the steps)
        w.finalSafeZ = (11 + zo).toFloat()
        val stairs = arrayOf("KYPYK", "KGRGK", "KYPYK", "KGBGK", "KYPYK", "KGRGK", "KYPYK", "KGRGK", "KYPYK", "KGRGK")
        for ((i, s) in stairs.withIndex()) row(11 + i, 0.5f + i * 0.5f, s, -2)
        for (i in stairs.indices) coin(0f, 0.5f + i * 0.5f, 11 + i)
        for (z in intArrayOf(11, 15, 19)) { val top = 0.5f + (z - 11) * 0.5f; under(-2f, top, z, 3); under(2f, top, z, 3) }
        // the portal plaza
        stoneRow(21, 5f, -2, 2); stoneRow(22, 5f, -2, 2); stoneRow(23, 5f, -2, 2)
        for (z in 21..23) { under(-2f, 5f, z, 3); under(2f, 5f, z, 3) }
        // the golden portal from the design stands at the far edge of the plaza
        w.portals.add(Portal(ox, 5f + lo, (24 + zo).toFloat() + 0.2f))

        // torches on carved pillars line the path through the temple (as in the design)
        for (z in intArrayOf(40, 46, 52, 60, 70, 84, 94, 104, 112, 124, 134, 146, 160, 170, 178, 188, 196, 206, 214, 222)) {
            val row = w.row(z) ?: continue
            val xs = row.filter { it.collides() && it.type != BT.MOVING }.map { it.x }
            if (xs.isEmpty()) continue
            val top = w.levelAt(z) + 0.2f
            w.decos.add(Deco(DK.TORCH, xs.min() - 1.35f, top, z + 0.5f, 1f))
            w.decos.add(Deco(DK.TORCH, xs.max() + 1.35f, top, z + 0.5f, 1f))
        }

        // in the temple every stone, spike base, spring housing and crumbling block is carved temple stone
        for (b in w.blocks) if (b.color == BC.BRICK || b.color == BC.STONE || b.color == BC.IRON) { b.color = if (b.type == BT.TRAP) BC.TEMPLE_DARK else BC.TEMPLE; b.remember() }
    }
}
