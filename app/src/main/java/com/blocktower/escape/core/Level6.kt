package com.blocktower.escape.core

/**
 * Level 6: the sky temple, from the approved Level 6 design (design/level6_reference.png).
 *
 * The temple and its glowing portal stand far away at the top of the screen from the first second; the course is one
 * long, hard route across floating islands with waterfalls to reach it, and the rainbow water slides are part of the
 * way: you really ride them (down their bottoms, up their walls in the bends, steering for coins and round the spike
 * plates). Rows run along +z; "lvl" is the walking-surface height (block top). The coloured blocks stand on floating
 * islands of temple stone (moss on top, waterfalls pouring off their edges) or on stone towers rising out of the
 * clouds far below.
 *
 *  1 the sky garden                   (speed pads, spiked blocks, the first laser gate, a mace)   CHECKPOINT 1
 *  2 the rainbow slide                (the first slide: bends, coins up the walls, a speed ring)
 *  3 the waterfall islands            (moving and vanishing blocks, lasers; a fork: the fast slide on the left or
 *                                      the block path with treasure on the right)                 CHECKPOINT 2
 *  4 the Runaway Relic                (CHASE & COLLECT: speed pads and a slide to catch it; a bonus you can miss)
 *                                                                                                  CHECKPOINT 3
 *  5 the great rainbow run            (a long winding slide with spike plates to steer round)
 *  6 the hazard gardens               (maces, fast lasers, sliding spiked blocks, vanishing stones) CHECKPOINT 4
 *  7 the Guardian chase               (GUARDIAN APPROACHING! boulders, spikes, a laser, a narrow bridge)
 *                                                                                                  CHECKPOINT 5
 *  8 the floating maze                (moving platforms in a row, crumbling stones, springs)      CHECKPOINT 6
 *  9 the temple ascent                (FINAL ESCAPE: the islands fall behind you; the long climb, the temple stairs
 *                                      and the portal)
 *
 * Row string legend: see [CourseWriter] ('p' is a speed pad, 'K' temple stone in this level).
 */
object Level6 {
    fun build(): World {
        val w = World()
        w.rowMin = -8; w.rowMax = 520
        w.initRows()
        Level6Writer(w).write()
        return w
    }
}

private class Level6Writer(w: World) : CourseWriter(w, 6) {
    private var seed = 1

    /**
     * A floating island under rows z0..z1 ([width] wide around x): its slab's top is just under the lowest walking
     * level of those rows. [falls]: waterfalls off its left (1), right (2) and front (4) edges.
     */
    fun island(z0: Int, z1: Int, lvl: Float, x: Float = 0f, width: Float = 5.6f, falls: Int = 3, palms: Int = 2, depth: Float = 5f) {
        var low = lvl + lo
        for (z in z0..z1) w.pathLevel[z + zo]?.let { if (w.pathX[z + zo] == ox) low = kotlin.math.min(low, it) }
        w.islands.add(Island(ox + x, (z0 + z1 + 1) * 0.5f + zo, low - 1f, width, (z1 - z0 + 1) + 0.3f, depth, falls, palms, seed++))
    }
    /** A stone tower out of the clouds under rows z0..z1 (for steps and single stones). */
    fun tower(z0: Int, z1: Int, lvl: Float, x: Float = 0f, width: Float = 3.3f) {
        w.pillars.add(Pillar(ox + x, (z0 + z1 + 1) * 0.5f + zo, lvl + lo - 1f, width, (z1 - z0 + 1) - 0.1f))
    }
    /** A slide through points given as (x, lvl, z) in this section's coordinates (lvl is the channel's bottom). */
    fun slide(vararg p: Float, ride: Boolean = true, r: Float = 1.25f): Slide {
        val pts = FloatArray(p.size)
        for (i in 0 until p.size / 3) { pts[i * 3] = p[i * 3] + ox; pts[i * 3 + 1] = p[i * 3 + 1] + lo; pts[i * 3 + 2] = p[i * 3 + 2] + zo }
        val sl = Slide(pts, r, ride)
        w.slides.add(sl)
        return sl
    }
    private val tmp = FloatArray(3)
    /** Coins down a slide from s0 to s1, every [step], [th] round the channel (a function of the stretch, for curves). */
    fun slideCoins(sl: Slide, s0: Float, s1: Float, step: Float, th: (Float) -> Float) {
        var s = s0
        while (s <= s1) { sl.point(s, th(s), 0.8f, tmp, 0); w.coins.add(Coin(tmp[0], tmp[1], tmp[2])); w.coinTotal++; s += step }
    }
    /** A blue block floating in a slide's channel (collected as you slide through it). */
    fun slideBlue(sl: Slide, s: Float, th: Float) {
        sl.point(s, th, 0.35f, tmp, 0)
        val b = Block(tmp[0], tmp[1], tmp[2] - 0.5f, BC.BLUE, BT.TARGET)
        b.sx = 0.8f; b.sy = 0.8f; b.sz = 0.8f; b.x = tmp[0]; b.z = tmp[2] - 0.4f; b.decor = true; b.remember()
        w.add(b); w.targetTotal++
        slideTargets.add(floatArrayOf(s, th))
    }
    val slideTargets = ArrayList<FloatArray>()
    /** A laser gate across the path at row z (emitters just outside lanes x0..x1), beam at knee height. */
    fun laser(z: Int, lvl: Float, x0: Float = -1.5f, x1: Float = 1.5f, period: Float = 3f, onFor: Float = 1.1f, phase: Float = 0f) {
        w.lasers.add(Laser(ox + x0, ox + x1, lvl + lo + 0.42f, z + zo + 0.5f, period, onFor, phase))
    }
    /** A spiked block standing at (x, z) on the walking level [lvl]; with [amp] it slides across the path on a rail. */
    fun spikeBox(x: Float, lvl: Float, z: Int, amp: Float = 0f, speed: Float = 1.6f, phase: Float = 0f) {
        w.spikeBoxes.add(SpikeBox(ox + x, lvl + lo + 0.02f, z + zo + 0.5f, amp, speed, phase))
    }
    /** A spiked mace on its chain, hung from a stone beam across the path. [atGo]: where it is when the countdown ends (-1..1). */
    fun mace(x: Float, lvl: Float, z: Int, amp: Float, period: Float, atGo: Float) {
        val len = 3.3f
        val py = lvl + lo + 4.1f
        val w0 = TAU / period
        val phase = kotlin.math.asin(clamp(atGo, -1f, 1f)) - w0 * 3.0f
        w.logs.add(SwingLog(ox + x, py, z + zo + 0.5f, len, amp, period, phase))
        w.pillars.add(Pillar(ox + x - 3.1f, z + zo + 0.5f, py + 0.02f, 0.6f, 0.6f))
        w.pillars.add(Pillar(ox + x + 3.1f, z + zo + 0.5f, py + 0.02f, 0.6f, 0.6f))
    }
    /** A brazier on a stone post beside the path (the design's fire bowls). */
    fun brazier(x: Float, lvl: Float, z: Int) {
        val top = lvl + lo + 1.2f
        w.pillars.add(Pillar(ox + x, z + zo + 0.5f, top, 0.9f, 0.9f))
        w.decos.add(Deco(DK.TORCH, ox + x, top + 0.02f, z + zo + 0.5f, 1f))
    }
    fun stoneRow(z: Int, lvl: Float, x0: Int, x1: Int) { for (x in x0..x1) blk(x.toFloat(), lvl, z, BC.SAND, BT.BRICK); setPath(z, lvl) }
    fun templeCheckpoint(z: Int, lvl: Int) { row(z, lvl, "K.K"); checkpoint(z, lvl) }

    fun write() {
        w.seaDepth = 11f

        // ================= SECTION 1 : THE SKY GARDEN =================
        ox = 0f; lo = 0f; zo = 0
        section("garden", -8, 46)
        w.spawnX = 0f; w.spawnY = 0f; w.spawnZ = 0.45f
        w.checkpoints.add(Checkpoint(0, 0f, 0f, 0.45f).also { it.active = true })
        // the opening view of the design: a wide rainbow path of blocks with speed pads down the middle
        row(-4, 0, "GRG"); row(-3, 0, "YGY"); row(-2, 0, "RPR"); row(-1, 0, "GYG")
        row(0, 0, "YGY"); row(1, 0, "RpR"); row(2, 0, "GYG"); row(3, 0, "PpP"); row(4, 0, "YRY"); row(5, 0, "GYG")
        island(-4, 5, 0f, 0f, 5.6f, 3, 2)
        trig(0, Ev.ZONE, "THE SKY TEMPLE", "THE PORTAL IS FAR AWAY — CLIMB!")
        coinLine(0f, 0f, 1, 5)
        // the rainbow tubes of the design arcing round the islands on the left (scenery)
        slide(-7f, -3f, -4f, -10f, -1.5f, 6f, -9.5f, 0.5f, 16f, -7f, 2.5f, 26f, -8.5f, 4.5f, 38f, ride = false, r = 1.1f)
        slide(-14f, 2f, 10f, -11f, 1f, 20f, -12f, 0f, 30f, -16f, 2f, 42f, ride = false, r = 1.0f)
        // spiked blocks on the island's edges, a brazier beside the way
        spikeBox(-2.3f, 0f, 3); spikeBox(2.3f, 0f, 5)
        brazier(3.4f, 0f, 1)
        // the first gap and a blue block
        row(7, 0.5f, "YGY"); row(8, 1, "GRG"); row(9, 1, "YBY"); row(10, 1, "RpR"); row(11, 1, "GYG"); row(12, 1, "PRP")
        island(7, 12, 1f, 0f, 5.4f, 1, 1)
        trig(6, Ev.HINT_TARGET)
        coin(0f, 0.9f, 6)
        // the first laser gate: jump its beam or pass while it is off
        row(13, 1, "GYG"); row(14, 1, "YRY"); row(15, 1, "GYG"); row(16, 1, "RYR"); row(17, 1, "GYG")
        island(13, 17, 1f, 0f, 5.2f, 2, 1)
        laser(14, 1f, period = 3.2f, onFor = 1.0f, phase = 0.4f)
        trig(12, Ev.TUT, "LASER GATE! Jump the beam — or pass while it is off", "7")
        coinLine(0f, 1f, 15, 17)
        // a moving block over the gap
        moving(0f, 1, 19, 1.0f, 1.8f, 0f, 2.2f, BC.PURPLE)
        save(0f, -2, 19)
        row(21, 1, "GYG"); row(22, 1.5f, "YGY"); row(23, 2, "RYR"); row(24, 2, "GBG"); row(25, 2, "YRY"); row(26, 2, "GYG"); row(27, 2, "RGR")
        island(21, 21, 1f, 0f, 3.6f, 0, 0); tower(22, 22, 1.5f); island(23, 27, 2f, 0f, 5.4f, 1, 2)
        mace(0f, 2f, 26, 0.95f, 3.1f, 0.3f)
        mystery(-2.2f, 2f, 24, Reward.COINS, BC.YELLOW)
        // a spiked block sliding across the path on its rail
        row(28, 2, "GYG"); row(29, 2, "YPY"); row(30, 2, "GYG"); row(31, 2, "RYR"); row(32, 2, "GYG")
        island(28, 32, 2f, 0f, 5.2f, 2, 1)
        spikeBox(0f, 2f, 30, amp = 1.3f, speed = 1.5f, phase = 0f)
        coin(-1f, 2f, 28); coin(1f, 2f, 32)
        // up a step, a vanishing pair, the first checkpoint
        row(34, 2.5f, "YGY"); row(35, 3, "GBG")
        tower(34, 34, 2.5f); island(35, 35, 3f, 0f, 3.6f, 0, 0)
        row(37, 3, "d.d", -1); row(38, 3, ".d.", -1)
        save(0f, 0, 37)
        row(39, 3, "GYG"); row(40, 3, "RGR"); templeCheckpoint(41, 3); row(42, 3, "GYG"); row(43, 3, "YpY"); row(44, 3, "GYG")
        island(39, 44, 3f, 0f, 5.6f, 3, 2)
        coinLine(0f, 3f, 42, 44)
        brazier(-3.4f, 3f, 40); brazier(3.4f, 3f, 42)

        // ================= SECTION 2 : THE RAINBOW SLIDE =================
        // its mouth at the end of the checkpoint island; it swings left, then right, and drops to an island below
        zo = 45; lo = 3f
        section("slide1", 0, 36)
        trig(-1, Ev.ZONE, "RAINBOW SLIDE!", "STEER UP THE WALLS FOR COINS")
        val s1 = slide(0f, 0f, 0f, 0f, -0.4f, 3f, -1.5f, -1f, 7f, -4f, -1.7f, 11f, -5f, -2.3f, 15f, -3f, -2.8f, 19f,
            0.5f, -3.3f, 22.5f, 3f, -3.8f, 26f, 2f, -4.3f, 29.5f, 0f, -4.6f, 32.5f, 0f, -4.8f, 35f)
        slideCoins(s1, 3f, s1.length - 3f, 1.1f) { s -> if (s < 12f) 0.75f else if (s < 22f) -0.6f else if (s < 28f) 0.5f else 0f }
        s1.pads.add(s1.length * 0.45f)
        slideBlue(s1, s1.length * 0.68f, -0.2f)
        // islands and waterfalls under its bends
        w.islands.add(Island(ox - 4.5f, zo + 12f, lo - 5.5f, 4f, 4f, 4f, 3, 1, seed++))
        w.islands.add(Island(ox + 3.5f, zo + 25f, lo - 6.5f, 3.6f, 3.6f, 4f, 2, 1, seed++))

        // ================= SECTION 3 : THE WATERFALL ISLANDS =================
        zo = 82; lo = -2f
        section("islands", 0, 58)
        // the landing island (the slide's exit is just above its first rows)
        row(0, 0, "GYG"); row(1, 0, "YRY"); row(2, 0, "GYG"); row(3, 0, "RYR"); row(4, 0, "GYG"); row(5, 0, "YBY"); row(6, 0, "PRP")
        island(0, 6, 0f, 0f, 6f, 7, 2)
        coinLine(0f, 0f, 3, 6)
        // moving blocks island to island
        moving(0f, 0, 8, 1.2f, 1.7f, 0.5f, 2.2f, BC.YELLOW)
        save(0f, -3, 8)
        row(10, 0, "GYG"); row(11, 0, "RYR"); row(12, 0, "GYG")
        island(10, 12, 0f, 0f, 4.6f, 2, 1)
        moving(0f, 0, 14, 1.3f, 2.0f, 2.2f, 2f, BC.GREEN)
        save(0f, -3, 14)
        // a laser gate on a wide island, spiked blocks at its edges
        row(16, 0, "YGY"); row(17, 0, "GRG"); row(18, 0, "YGY"); row(19, 0, "GpG"); row(20, 0, "YRY")
        island(16, 20, 0f, 0f, 5.4f, 3, 1)
        laser(18, 0f, period = 2.8f, onFor = 1.1f, phase = 1.1f)
        spikeBox(-2.2f, 0f, 17); spikeBox(2.2f, 0f, 19)
        coin(0f, 0f, 16); coin(0f, 1.3f, 18); coin(0f, 0f, 20)
        // vanishing stepping stones
        row(22, 0, "d.d", -1); row(23, 0.5f, ".d.", -1); row(24, 1, "d.d", -1)
        save(0f, -3, 23)
        coin(0f, 0.6f, 22); coin(0f, 1.1f, 23); coin(0f, 1.6f, 24)
        row(25, 1, "GYG"); row(26, 1, "YBY"); row(27, 1, "GYG")
        island(25, 27, 1f, 0f, 5f, 1, 1)
        // the fork: the fast slide on the left, the block path with treasure on the right
        row(28, 1, "YRGRY", -2); row(29, 1, "GYRYG", -2); row(30, 1, "YRGRY", -2)
        island(28, 30, 1f, 0f, 6.4f, 0, 2)
        trig(27, Ev.FORK)
        // left: a short fast slide (coins, a blue block at its end)
        ox = -2.5f
        row(31, 1.5f, "GY", -1, false)
        tower(31, 31, 1.5f, -0.5f, 2.3f)
        val fork = slide(-0.5f, 1.5f, 32f, -1f, 1.25f, 35f, -2.5f, 1f, 38.5f, -2.5f, 0.8f, 42f, 0.5f, 0.6f, 45f, 1.5f, 0.45f, 47.5f)
        slideCoins(fork, 1.5f, fork.length - 1.5f, 0.9f) { s -> if (s < 7f) -0.5f else 0.35f }
        slideBlue(fork, fork.length * 0.8f, 0.3f)
        // right: moving blocks and a gem box, a mystery shield
        ox = 2.5f
        row(31, 1, "BY", 0, false)
        moving(0.5f, 1, 33, 0.8f, 1.7f, 0f, 2f, BC.PURPLE)
        row(35, 1, "YG", 0, false); row(36, 1, "RB", 0, false); row(37, 1.5f, "GY", 0, false)
        mystery(1.5f, 1.5f, 37, Reward.GEMS, BC.GREEN)
        moving(0.5f, 1, 39, 0.9f, 1.9f, 1.4f, 2f, BC.YELLOW)
        row(41, 1, "GR", 0, false); row(42, 1, "YG", 0, false); row(43, 1, "RY", 0, false)
        mystery(0f, 1f, 43, Reward.TOOL_SHIELD, BC.GREEN)
        row(44, 1, "YG", -1, false); row(45, 1, "GR", -1, false); row(46, 0.5f, "YG", -1, false); row(47, 0.5f, "GY", -1, false)
        island(31, 31, 1f, 0.5f, 2.6f, 2, 0); island(35, 37, 1f, 0.5f, 2.6f, 2, 1); island(41, 43, 1f, 0.5f, 2.6f, 2, 0)
        tower(44, 45, 1f, -0.5f, 2.3f); tower(46, 47, 0.5f, -0.5f, 2.3f)
        coin(0.5f, 1f, 33); coin(0.5f, 1f, 39)
        for (z in 31..47) setPath(z, if (z < 46) 1f else 0.5f)
        w.pathX.keys.filter { it in (31 + zo)..(47 + zo) }.forEach { w.pathX[it] = 2.5f }
        // both ways meet on the checkpoint island
        ox = 0f
        row(48, 0, "YRGRY", -2); row(49, 0, "GYRYG", -2); row(50, 0, "RGYGR", -2); templeCheckpoint(51, 0); row(52, 0, "GYG"); row(53, 0, "YpY")
        island(48, 53, 0f, 0f, 6.6f, 7, 3)
        coin(-1f, 0f, 49); coin(1f, 0f, 49); coinLine(0f, 0f, 52, 53)
        brazier(-3.8f, 0f, 50); brazier(3.8f, 0f, 50)
        row(54, 0, "GYG"); row(55, 0.5f, "YGY"); row(56, 1, "RYR"); row(57, 1.5f, "GYG"); row(58, 2, "YRY")
        island(54, 54, 0f, 0f, 4.4f, 0, 0); tower(55, 55, 0.5f); tower(56, 56, 1f); tower(57, 57, 1.5f); tower(58, 58, 2f)

        // ================= SECTION 4 : THE RUNAWAY RELIC =================
        zo = 141; lo = 0f
        section("relic", 0, 66)
        trig(0, Ev.RELIC)
        w.relicZ0 = 2 + zo; w.relicZ1 = 64 + zo
        row(0, 0, "GYG"); row(1, 0, "RGR"); row(2, 0, "YpY"); row(3, 0, "GYG"); row(4, 0, "RGR"); row(5, 0, "GYG")
        island(0, 5, 0f, 0f, 5.2f, 3, 1)
        row(7, 0, "YGY"); row(8, 0, "GYG"); row(9, 0, "YRY"); row(10, 0, "GYG")
        island(7, 10, 0f, 0f, 5f, 1, 1)
        laser(9, 0f, period = 3.4f, onFor = 0.9f, phase = 2.0f)
        row(12, 0, "GYG"); row(13, 0, "YGY"); row(14, 0, "GYG"); row(15, 0, "RYR")
        island(12, 15, 0f, 0f, 5f, 2, 1)
        bridge(16, 20, 0f)
        row(21, 0, "GBG"); row(22, 0, "YpY"); row(23, 0, "GYG")
        island(21, 23, 0f, 0f, 5f, 3, 1)
        // the relic takes this slide too, but you ride it faster than it does
        val rs = slide(0f, 0f, 24f, 0f, -0.3f, 27f, 1.5f, -0.9f, 30.5f, 1.5f, -1.5f, 34f, 0f, -1.9f, 37.5f, 0f, -2.1f, 40f)
        slideCoins(rs, 1.5f, rs.length - 1.5f, 1.0f) { s -> if (s < 8f) -0.4f else 0.2f }
        rs.pads.add(rs.length * 0.35f)
        row(41, -2.5f, "GYG"); row(42, -2.5f, "YpY"); row(43, -2.5f, "GYG"); row(44, -2.5f, "RYR"); row(45, -2.5f, "GBG")
        island(41, 45, -2.5f, 0f, 5.4f, 3, 2)
        // a gap, then up (a spring for the quick way up)
        row(47, -2.5f, "YbY"); tower(47, 47, -2.5f)
        coin(0f, -0.9f, 47)
        row(48, -0.5f, "GRG"); row(49, -0.5f, "YpY"); row(50, -0.5f, "GYG")
        island(48, 50, -0.5f, 0f, 5f, 2, 1)
        row(51, 0, "RGR"); row(52, 0.5f, "YGY"); tower(51, 51, 0f); tower(52, 52, 0.5f)
        row(53, 1, "GRG"); row(54, 1, "YGY"); row(55, 1, "GpG"); row(56, 1, "RYR")
        island(53, 56, 1f, 0f, 5.2f, 2, 2)
        spikeBox(-2.2f, 1f, 54); spikeBox(2.2f, 1f, 56)
        row(58, 1.5f, "GYG"); row(59, 2, "YGY"); tower(58, 58, 1.5f); tower(59, 59, 2f)
        row(60, 2, "GYG"); row(61, 2, "YRY"); row(62, 2, "GpG"); row(63, 2, "GYG"); templeCheckpoint(64, 2); row(65, 2, "YGY"); row(66, 2, "GYG")
        island(60, 66, 2f, 0f, 5.8f, 3, 2)
        coinLine(0f, 0f, 0, 5); coinLine(0f, 0f, 7, 10); coinLine(0f, 0f, 12, 15); coinLine(0f, -2.5f, 41, 45); coinLine(0f, 1f, 53, 56); coinLine(0f, 2f, 60, 63)
        brazier(-3.3f, 2f, 61); brazier(3.3f, 2f, 65)

        // ================= SECTION 5 : THE GREAT RAINBOW RUN =================
        zo = 208; lo = 2f
        section("slide2", 0, 52)
        trig(-1, Ev.ZONE, "THE GREAT RAINBOW RUN", "STEER ROUND THE SPIKES!")
        val s2 = slide(0f, 0f, 0f, 0f, -0.4f, 3.5f, 2f, -1f, 7.5f, 5f, -1.6f, 11.5f, 5.5f, -2.2f, 15.5f, 3f, -2.8f, 19.5f,
            -1f, -3.4f, 23f, -4.5f, -4f, 27f, -5f, -4.6f, 31f, -2.5f, -5.1f, 35f, 1f, -5.6f, 38.5f, 3f, -6f, 42f, 2f, -6.4f, 45.5f,
            0f, -6.7f, 48.5f, 0f, -6.9f, 51f)
        // spike plates to steer round (the coins show the safe line), speed rings on the straights
        val L2 = s2.length
        s2.spikes.add(floatArrayOf(L2 * 0.2f, 0f)); s2.spikes.add(floatArrayOf(L2 * 0.38f, -0.55f)); s2.spikes.add(floatArrayOf(L2 * 0.47f, 0.35f))
        s2.spikes.add(floatArrayOf(L2 * 0.62f, 0.45f)); s2.spikes.add(floatArrayOf(L2 * 0.78f, -0.4f))
        s2.pads.add(L2 * 0.1f); s2.pads.add(L2 * 0.88f)
        slideCoins(s2, 2f, L2 - 2f, 1.0f) { s ->
            val u = s / L2
            when { u < 0.3f -> 0.7f; u < 0.43f -> 0.55f; u < 0.55f -> -0.45f; u < 0.7f -> -0.5f; u < 0.84f -> 0.5f; else -> 0f }
        }
        slideBlue(s2, L2 * 0.3f, 0.7f); slideBlue(s2, L2 * 0.7f, -0.5f)
        w.islands.add(Island(ox + 6.5f, zo + 13f, lo - 7f, 4f, 4.4f, 4f, 3, 1, seed++))
        w.islands.add(Island(ox - 6.5f, zo + 29f, lo - 8.5f, 4.4f, 4.4f, 4f, 3, 2, seed++))
        w.islands.add(Island(ox + 4.5f, zo + 43f, lo - 10f, 3.6f, 3.6f, 3.5f, 1, 1, seed++))

        // ================= SECTION 6 : THE HAZARD GARDENS =================
        zo = 261; lo = -5.5f
        section("hazards", 0, 54)
        trig(4, Ev.ZONE, "THE HAZARD GARDENS", "TIME EVERY STEP")
        row(0, 0, "GYG"); row(1, 0, "YRY"); row(2, 0, "GYG"); row(3, 0, "RYR"); row(4, 0, "GYG"); row(5, 0, "YGY")
        island(0, 5, 0f, 0f, 6f, 7, 2)
        coinLine(0f, 0f, 3, 5)
        // a mace, then a fast laser
        row(6, 0, "GRG"); row(7, 0, "YGY"); row(8, 0, "GRG")
        island(6, 8, 0f, 0f, 5f, 2, 1)
        mace(0f, 0f, 7, 1.0f, 2.8f, -0.5f)
        row(10, 0, "YGY"); row(11, 0, "GYG"); row(12, 0, "RYR"); row(13, 0, "GYG"); row(14, 0, "YGY")
        island(10, 13, 0f, 0f, 5f, 1, 1)
        laser(10, 0f, period = 2.3f, onFor = 1.0f, phase = 0.7f)
        // two spiked blocks sliding on their rails, out of step
        row(15, 0, "GRG"); row(16, 0, "YGY"); row(17, 0, "RGR"); row(18, 0, "YGY"); row(19, 0, "GBG")
        island(14, 19, 0f, 0f, 5.2f, 3, 1)
        spikeBox(0f, 0f, 15, amp = 1.3f, speed = 1.7f, phase = 0f)
        spikeBox(0f, 0f, 18, amp = 1.3f, speed = 1.7f, phase = 2.6f)
        coin(0f, 1.2f, 16); coin(0f, 1.2f, 17)
        // vanishing stones over the clouds, cracked blocks
        row(21, 0, "d.d", -1); row(22, 0.5f, ".d.", -1); row(23, 1, "d.d", -1)
        save(0f, -3, 22)
        row(24, 1, "GYG"); row(25, 1, "ccc"); row(26, 1, "ccc"); row(27, 1, "ccc"); row(28, 1, "GYG")
        island(24, 28, 1f, 0f, 5f, 3, 1)
        // a narrow bridge under a mace
        bridge(29, 34, 1f, 0, 0)
        mace(0f, 1f, 32, 0.9f, 3.0f, 0.6f)
        row(35, 1, "GYG"); row(36, 1, "YGY"); row(37, 1, "GRG")
        island(35, 37, 1f, 0f, 4.6f, 2, 1)
        // lava-cracked falling blocks and a spiked row: jump it
        row(38, 1, "fff"); row(39, 1, "fff"); row(40, 1, "GYG"); row(41, 1, "^^^"); row(42, 1, "GBG")
        tower(38, 39, 1f); island(40, 42, 1f, 0f, 4.6f, 1, 0)
        coin(0f, 2.3f, 41)
        row(44, 1.5f, "YGY"); row(45, 2, "GRG"); row(46, 2, "YGY")
        tower(44, 44, 1.5f); island(45, 46, 2f, 0f, 4.4f, 0, 1)
        // (two rows of run-up after the step: a jump up the step never comes down in the beam)
        laser(48, 2f, period = 2.5f, onFor = 1.1f, phase = 1.9f)
        row(47, 2, "GYG"); row(48, 2, "RGR"); row(49, 2, "GYG"); templeCheckpoint(50, 2); row(51, 2, "YGY"); row(52, 2, "GRG"); row(53, 2, "YGY"); row(54, 2, "GYG")
        island(47, 54, 2f, 0f, 6f, 3, 3)
        mystery(-2f, 2f, 51, Reward.HEART, BC.GREEN); mystery(2f, 2f, 51, Reward.TOOL_SHIELD, BC.GREEN)
        coinLine(0f, 2f, 50, 54)
        brazier(-3.6f, 2f, 48); brazier(3.6f, 2f, 48)

        // ================= SECTION 7 : THE GUARDIAN CHASE =================
        zo = 316; lo = -3.5f
        section("chase", 0, 48)
        stoneRow(0, 0f, -1, 1); stoneRow(1, 0f, -1, 1)
        island(0, 1, 0f, 0f, 4.6f, 0, 0)
        trig(4, Ev.CHASE)
        row(2, 0, "YGY"); row(3, 0, "GYG"); row(4, 0, "RYR"); row(5, 0, "GRG"); row(6, 0, "YRY")
        island(2, 6, 0f, 0f, 5f, 2, 1)
        row(8, 0, "tYt"); row(9, 0, "GRG"); row(10, 0, "YBY"); row(11, 0, "RGR")
        island(8, 11, 0f, 0f, 4.8f, 2, 1)
        moving(0f, 0, 13, 1.0f, 2.0f, 0f, 2.4f, BC.YELLOW)
        save(0f, -3, 13)
        row(15, 0, "GYG"); row(16, 0, "ccc"); row(17, 0, "cYc"); row(18, 0, "ccc"); row(19, 0, "GYG")
        island(15, 19, 0f, 0f, 5f, 3, 1)
        laser(19, 0f, period = 2.6f, onFor = 0.9f, phase = 0.3f)
        // a narrow bridge: one lane
        bridge(20, 25, 0f, 0, 0)
        row(26, 0, "GYG"); row(27, 0, "YpY"); row(28, 0, "GYG")
        island(26, 28, 0f, 0f, 4.6f, 2, 1)
        row(30, 0.5f, "RGR"); row(31, 1, "Y^Y"); row(32, 1, "GRG"); row(33, 1, "YpY"); row(34, 1, "RGR")
        tower(30, 30, 0.5f); island(31, 34, 1f, 0f, 5f, 1, 1)
        row(36, 1, "GYG"); row(37, 1, "fff"); row(38, 1, "fff"); row(39, 1, "GYG"); row(40, 1, "YGY")
        tower(37, 38, 1f); island(36, 36, 1f, 0f, 4f, 0, 0); island(39, 40, 1f, 0f, 4.6f, 3, 1)
        coinLine(0f, 0f, 3, 6); coinLine(0f, 0f, 9, 11); coinLine(0f, 1f, 32, 34); coin(0f, 1.2f, 13)
        collapsible(-2, 41)
        // CHECKPOINT 5: the island behind gives way under the Guardian
        for (z in 41..48) if (z != 44) row(z, 1, if (z % 2 == 0) "GYGYG" else "YGYGY", -2)
        row(44, 1, "GY.YG", -2); checkpoint(44, 1)
        trig(43, Ev.CHASE_END)
        mystery(-2f, 1f, 46, Reward.TOOL_BLOCK, BC.GREEN)
        mystery(2f, 1f, 46, Reward.TOOL_MAGNET, BC.GREEN)
        island(41, 48, 1f, 0f, 6.6f, 7, 3)
        coin(-1f, 1f, 46); coin(1f, 1f, 46); coin(0f, 1f, 47)

        // ================= SECTION 8 : THE FLOATING MAZE =================
        zo = 365; lo = -2.5f
        section("maze", 0, 44)
        trig(0, Ev.ZONE, "THE FLOATING MAZE", "RIDE THE PLATFORMS")
        row(0, 0, "GYG"); row(1, 0, "YRY")
        island(0, 1, 0f, 0f, 4.6f, 0, 1)
        // platforms sliding in turn over a long drop
        moving(0f, 0, 3, 1.1f, 1.5f, 0f, 2.4f, BC.PURPLE)
        moving(0f, 0, 5, 1.1f, 1.5f, 1.6f, 2.4f, BC.GREEN)
        moving(0f, 0, 7, 1.1f, 1.5f, 3.2f, 2.4f, BC.YELLOW)
        save(0f, -3, 4); save(0f, -3, 6)
        coin(0f, 0.6f, 3); coin(0f, 0.6f, 5); coin(0f, 0.6f, 7)
        row(9, 0, "GYG"); row(10, 0, "RBR"); row(11, 0, "GYG")
        island(9, 11, 0f, 0f, 4.8f, 3, 1)
        // a spring up to a high island
        row(12, 0, "YbY"); row(13, 2, "GRG"); row(14, 2, "YGY"); row(15, 2, "GYG"); row(16, 2, "YGY")
        tower(12, 12, 0f); island(13, 16, 2f, 0f, 5f, 2, 1)
        laser(14, 2f, period = 2.4f, onFor = 1.0f, phase = 1.2f)
        coin(0f, 1.6f, 12)
        // crumbling stones, then vanishing stones zig-zagging up
        row(17, 2, "cGc"); row(18, 2, "ccc"); row(19, 2, "ccc"); row(20, 2, "GYG"); row(21, 2, "YGY")
        island(17, 21, 2f, 0f, 5f, 1, 1)
        row(22, 2, "d.d", -1); row(23, 2.5f, ".d.", -1); row(24, 3, "d.d", -1); row(25, 3, "GYG")
        save(0f, -1, 23)
        coin(0f, 2.6f, 22); coin(0f, 3.1f, 23); coin(0f, 3.6f, 24)
        row(26, 3, "YGY"); row(27, 3, "GRG"); row(28, 3, "YBY")
        island(25, 28, 3f, 0f, 5f, 3, 2)
        spikeBox(0f, 3f, 27, amp = 1.3f, speed = 1.6f, phase = 1f)
        moving(0f, 3, 30, 1.2f, 2.1f, 0.8f, 2f, BC.PURPLE)
        save(0f, 0, 30)
        row(32, 3, "GYG"); row(33, 3, "YRY"); templeCheckpoint(34, 3); row(35, 3, "GYG"); row(36, 3, "YpY")
        island(32, 36, 3f, 0f, 5.6f, 3, 2)
        mystery(-2f, 3f, 35, Reward.TOOL_SPEED, BC.BLUE)
        row(37, 3, "GYG"); row(38, 3.5f, "YGY"); row(39, 4, "RGR"); row(40, 4.5f, "GYG"); row(41, 5, "YRY")
        tower(37, 37, 3f); tower(38, 38, 3.5f); tower(39, 39, 4f); tower(40, 40, 4.5f); island(41, 41, 5f, 0f, 4f, 0, 0)
        coinLine(0f, 0f, 9, 11); coinLine(0f, 2f, 13, 15); coinLine(0f, 3f, 32, 33)

        // ================= SECTION 9 : THE TEMPLE ASCENT =================
        zo = 407; lo = 2.5f
        section("ascent", 0, 80)
        trig(1, Ev.FINAL)
        row(0, 0, "GYG"); row(1, 0, "YGY")
        island(0, 1, 0f, 0f, 4.6f, 0, 1)
        for (i in 0 until 10) {
            val z = 2 + i
            row(z, 0.5f * (i + 1), if (i == 5) "RBR" else if (i % 2 == 0) "YGY" else "GRG")
            tower(z, z, 0.5f * (i + 1))
        }
        for (i in 0 until 10) coin(0f, 0.5f * (i + 1), 2 + i)
        row(12, 5, "GpG"); row(13, 5, "YRY")
        island(12, 13, 5f, 0f, 4.8f, 3, 1)
        laser(13, 5f, period = 2.6f, onFor = 1.0f, phase = 0.2f)
        moving(0f, 5, 15, 1.1f, 2.0f, 0.8f, 2.2f, BC.YELLOW)
        save(0f, 2, 15)
        row(17, 5.5f, "GYG"); row(18, 6, "Y.Y", -1); row(19, 6.5f, ".G.", -1); row(20, 7, "YRY")
        tower(17, 17, 5.5f); tower(18, 18, 6f, 0f, 1.2f); tower(19, 19, 6.5f, 0f, 1.2f); island(20, 20, 7f, 0f, 4f, 0, 0)
        row(21, 7.5f, "GYG"); row(22, 8, "YGY"); row(23, 8.5f, "RGR"); row(24, 9, "GYG")
        tower(21, 21, 7.5f); tower(22, 22, 8f); tower(23, 23, 8.5f); island(24, 24, 9f, 0f, 4.4f, 2, 0)
        mace(0f, 9f, 27, 0.9f, 3.0f, 0.2f)
        bridge(25, 30, 9f)
        row(31, 9.5f, "YGY"); row(32, 10, "GRG"); row(33, 10.5f, "YGY"); row(34, 11, "GYG")
        tower(31, 31, 9.5f); tower(32, 32, 10f); tower(33, 33, 10.5f); island(34, 34, 11f, 0f, 4.4f, 1, 1)
        spikeBox(0f, 11f, 36, amp = 1.3f, speed = 2.0f, phase = 0.5f)
        row(35, 11, "GYG"); row(36, 11, "YGY"); row(37, 11, "GRG")
        island(35, 37, 11f, 0f, 5f, 3, 1)
        moving(0f, 11, 39, 1.0f, 2.4f, 0f, 2f, BC.PURPLE)
        moving(0f, 11, 41, 1.0f, 2.4f, 2.2f, 2f, BC.GREEN)
        save(0f, 8, 40)
        row(43, 11.5f, "GYG"); row(44, 12, "RBR"); row(45, 12.5f, "YGY"); row(46, 13, "GpG")
        tower(43, 43, 11.5f); tower(44, 44, 12f); tower(45, 45, 12.5f); island(46, 46, 13f, 0f, 4.4f, 3, 1)
        row(47, 13, "GYG"); row(48, 13, "YGY")
        island(47, 48, 13f, 0f, 4.6f, 0, 0)
        laser(48, 13f, period = 2.3f, onFor = 1.0f, phase = 1.5f)
        coinLine(0f, 5f, 12, 13); coinLine(0f, 9f, 25, 30); coinLine(0f, 11f, 35, 37); coinLine(0f, 13f, 47, 48)
        collapsible(-6, 48)
        brazier(-3.3f, 11f, 36); brazier(3.3f, 11f, 36)

        // ================= THE TEMPLE STAIRS AND THE PORTAL =================
        zo = 456; lo = 15.5f
        section("portal", 0, 22)
        w.finalSafeZ = (0 + zo).toFloat()
        row(0, 0, "YGY"); row(1, 0, "GRG")
        island(0, 1, 0f, 0f, 5f, 3, 1)
        val stairs = arrayOf("KYPYK", "KGRGK", "KYBYK", "KGRGK", "KYPYK", "KGRGK", "KYPYK", "KGRGK", "KYPYK", "KGRGK", "KYPYK", "KGRGK")
        for ((i, st) in stairs.withIndex()) row(2 + i, 0.5f + i * 0.5f, st, -2)
        for (i in stairs.indices) coin(0f, 0.5f + i * 0.5f, 2 + i)
        for (i in stairs.indices) tower(2 + i, 2 + i, 0.5f + i * 0.5f, 0f, 5.3f)
        stoneRow(14, 6f, -2, 2); stoneRow(15, 6f, -2, 2); stoneRow(16, 6f, -2, 2)
        island(14, 16, 6f, 0f, 7f, 7, 2, 6f)
        brazier(-3.8f, 6f, 14); brazier(3.8f, 6f, 14)
        // the temple's portal (the design's destination) at the far edge of the plaza
        w.portals.add(Portal(ox, 6f + lo, (17 + zo).toFloat() + 0.2f))

        // in the temple every stone is temple stone, every spike base dark iron
        for (b in w.blocks) {
            if (b.color == BC.BRICK && b.type == BT.BRICK) b.color = BC.SAND
            if (b.type == BT.CHECKPOINT) b.color = BC.SAND
            b.remember()
        }
    }

    /** Wooden bridge planks between islands (rows z0..z1, lanes x0..x1), chain railings at both sides. */
    fun bridge(z0: Int, z1: Int, lvl: Float, x0: Int = -1, x1: Int = 1) {
        for (z in z0..z1) {
            for (x in x0..x1) {
                val b = blk(x.toFloat(), lvl, z, BC.WOOD, BT.BRICK)
                b.y = lvl + lo - 0.35f; b.sy = 0.35f; b.remember()
            }
            setPath(z, lvl)
        }
        w.decos.add(Deco(DK.RAIL, ox + x0 - 0.62f, lvl + lo, (z0 + zo).toFloat(), (z1 - z0 + 1).toFloat()))
        w.decos.add(Deco(DK.RAIL, ox + x1 + 0.62f, lvl + lo, (z0 + zo).toFloat(), (z1 - z0 + 1).toFloat()))
    }
}
