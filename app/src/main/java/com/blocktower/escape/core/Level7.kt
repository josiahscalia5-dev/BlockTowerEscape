package com.blocktower.escape.core

/**
 * Level 7: the enchanted sky realm, from the approved Level 7 design (design/level7_reference.png).
 *
 * The Celestial Gate (the golden crystal castle with the star portal) stands far away in the violet sky at the top of
 * the screen from the first second, and the hooded Sorcerer looms in the sky on the left with his minions about him.
 * The course is the longest and hardest yet: a winding rainbow road of blocks across floating islands of moonstone
 * (moss and blossom trees on top, waterfalls pouring off their edges, witch-hat towers and glowing crystals), wooden
 * bridges and stepping stones over a sea of pink clouds, with spiked balls swinging on chains, arcane beams, spiked
 * blocks and the Sorcerer's minions. Rows run along +z; "lvl" is the walking-surface height (block top).
 *
 *  1 the enchanted isles              (speed pads, the first gaps, a spiked ball, moving blocks)    CHECKPOINT 1
 *  2 the minion ambush                (MINIONS AHEAD! patrolling imps, a swooping orb imp, a lantern ship's bolts)
 *                                                                                                   CHECKPOINT 2
 *  3 the crystal stairs               (narrow half-step climbs, cracked bridges, an arcane beam, vanishing stones,
 *                                      a spring; a fork: the safe crystal bridge or the treasure on moving blocks)
 *                                                                                                   CHECKPOINT 3
 *  4 the Runaway Relic                (CHASE & COLLECT: speed pads, a long jump; a bonus you can miss)   CHECKPOINT 4
 *  5 the Sorcerer's gauntlet          (spiked balls, beams, spiked blocks, falling blocks, platforms)    CHECKPOINT 5
 *  6 the starwind bridges             (MAGICAL WIND over spike-lined paths and jumps)               CHECKPOINT 6
 *  7 the minion pursuit               (THE SORCERER'S PURSUIT! imps fly after the boy and lunge when he slows, ships
 *                                      bomb the path, imps patrol and swoop: keep running)          CHECKPOINT 7
 *  8 the floating maze                (platforms in turn, a patrolled bridge, the last imps)        CHECKPOINT 8
 *  9 the final ascent                 (FINAL ESCAPE: the star bridge fades behind you; the long climb)
 * 10 the celestial stairs and the gate
 *
 * Row string legend: see [CourseWriter] ('p' is a speed pad, 'K' moonstone in this level, 'M' magenta).
 */
object Level7 {
    fun build(): World {
        val w = World()
        w.rowMin = -8; w.rowMax = 600
        w.initRows()
        Level7Writer(w).write()
        return w
    }
}

private class Level7Writer(w: World) : CourseWriter(w, 7) {
    private var seed = 1

    /**
     * A floating island under rows z0..z1 ([width] wide around x): its slab's top is just under the lowest walking
     * level of those rows. [falls]: waterfalls off its left (1), right (2) and front (4) edges; [trees] blossom trees.
     */
    fun island(z0: Int, z1: Int, lvl: Float, x: Float = 0f, width: Float = 5.6f, falls: Int = 3, trees: Int = 2, depth: Float = 5f) {
        var low = lvl + lo
        for (z in z0..z1) w.pathLevel[z + zo]?.let { if (w.pathX[z + zo] == ox) low = kotlin.math.min(low, it) }
        w.islands.add(Island(ox + x, (z0 + z1 + 1) * 0.5f + zo, low - 1f, width, (z1 - z0 + 1) + 0.3f, depth, falls, trees, seed++))
    }
    /** A moonstone tower out of the clouds under rows z0..z1 (for steps and single stones). */
    fun tower(z0: Int, z1: Int, lvl: Float, x: Float = 0f, width: Float = 3.3f) {
        w.pillars.add(Pillar(ox + x, (z0 + z1 + 1) * 0.5f + zo, lvl + lo - 1f, width, (z1 - z0 + 1) - 0.1f))
    }
    /** An arcane beam across the path at row z (emitters just outside lanes x0..x1), at knee height. */
    fun laser(z: Int, lvl: Float, x0: Float = -1.5f, x1: Float = 1.5f, period: Float = 3f, onFor: Float = 1.1f, phase: Float = 0f) {
        w.lasers.add(Laser(ox + x0, ox + x1, lvl + lo + 0.42f, z + zo + 0.5f, period, onFor, phase))
    }
    /** A spiked block at (x, z) on the walking level [lvl]; with [amp] it slides across the path on a rail. */
    fun spikeBox(x: Float, lvl: Float, z: Int, amp: Float = 0f, speed: Float = 1.6f, phase: Float = 0f) {
        w.spikeBoxes.add(SpikeBox(ox + x, lvl + lo + 0.02f, z + zo + 0.5f, amp, speed, phase))
    }
    /** A spiked ball on its chain, hung from a moonstone beam across the path. [atGo]: where it is when the countdown ends (-1..1). */
    fun mace(x: Float, lvl: Float, z: Int, amp: Float, period: Float, atGo: Float) {
        val len = 3.3f
        val py = lvl + lo + 4.1f
        val w0 = TAU / period
        val phase = kotlin.math.asin(clamp(atGo, -1f, 1f)) - w0 * 3.0f
        w.logs.add(SwingLog(ox + x, py, z + zo + 0.5f, len, amp, period, phase))
        w.pillars.add(Pillar(ox + x - 3.1f, z + zo + 0.5f, py + 0.02f, 0.6f, 0.6f))
        w.pillars.add(Pillar(ox + x + 3.1f, z + zo + 0.5f, py + 0.02f, 0.6f, 0.6f))
    }
    /** A witch-hat tower of moonstone beside the path (the design's fantasy towers). */
    fun spire(x: Float, lvl: Float, z: Int, width: Float = 1.2f) { w.decos.add(Deco(DK.SPIRE, ox + x, lvl + lo, z + zo + 0.5f, width)) }
    /** A cluster of glowing crystals on an island. */
    fun crystal(x: Float, lvl: Float, z: Int, size: Float = 1f) { w.decos.add(Deco(DK.CRYSTAL, ox + x, lvl + lo, z + zo + 0.5f, size)) }
    /** A gem floating over the path. */
    fun gem(x: Float, lvl: Float, z: Int) { w.gemPicks.add(GemPickup(ox + x, lvl + lo + 0.95f, z + zo + 0.5f)) }
    /** An iron chain slung between two points (section coordinates). */
    fun chain(x0: Float, l0: Float, z0: Float, x1: Float, l1: Float, z1: Float, sag: Float = 0.6f) {
        w.chains.add(ChainLink(ox + x0, l0 + lo, z0 + zo, ox + x1, l1 + lo, z1 + zo, sag))
    }
    /** A spiked ball hanging on a chain from a floating island beside the path (scenery, well clear of the way). */
    fun hangingBall(x: Float, lvl: Float, z: Int) {
        w.logs.add(SwingLog(ox + x, lvl + lo + 5.2f, z + zo + 0.5f, 2.6f, 0.12f, 3.6f, z * 0.7f))
    }
    /** A witch-hat imp of wave [wave] that patrols back and forth across the path at row z. */
    fun patrol(wave: Int, x: Float, lvl: Float, z: Int, amp: Float, speed: Float, phase: Float = 0f) {
        w.minionSpots.add(MinionSpot(wave, MK.PATROL, ox + x, lvl + lo, (z + zo).toFloat(), amp, speed, phase))
    }
    /** An orb imp of wave [wave] that swoops across row z every [period] seconds. */
    fun swoop(wave: Int, x: Float, lvl: Float, z: Int, amp: Float = 1.2f, period: Float = 2.8f, phase: Float = 0f) {
        w.minionSpots.add(MinionSpot(wave, MK.SWOOP, ox + x, lvl + lo, (z + zo).toFloat(), amp, period, phase))
    }
    fun wave(id: Int, chasers: Int, bombers: Int, title: String, sub: String) { w.waves.add(MinionWave(id, chasers, bombers, title, sub)) }
    fun moonCheckpoint(z: Int, lvl: Int) { row(z, lvl, "K.K"); checkpoint(z, lvl) }
    fun stoneRow(z: Int, lvl: Float, x0: Int, x1: Int) { for (x in x0..x1) blk(x.toFloat(), lvl, z, BC.MOON, BT.BRICK); setPath(z, lvl) }
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

    fun write() {
        w.seaDepth = 11f

        // ================= SECTION 1 : THE ENCHANTED ISLES =================
        ox = 0f; lo = 0f; zo = 0
        section("isles", -8, 49)
        w.spawnX = 0f; w.spawnY = 0f; w.spawnZ = 0.45f
        w.checkpoints.add(Checkpoint(0, 0f, 0f, 0.45f).also { it.active = true })
        // the opening view of the design: a rainbow road with speed pads and a line of coins down the middle
        row(-4, 0, "GRG"); row(-3, 0, "YPY"); row(-2, 0, "RpR"); row(-1, 0, "GYG")
        row(0, 0, "RGR"); row(1, 0, "YpY"); row(2, 0, "GMG"); row(3, 0, "PYP"); row(4, 0, "YGY"); row(5, 0, "RMR"); row(6, 0, "GYG")
        island(-4, 6, 0f, 0f, 5.8f, 3, 2)
        trig(20, Ev.ZONE, "THE ENCHANTED SKY", "THE CELESTIAL GATE IS FAR AWAY — RUN!")
        coinLine(0f, 0f, 1, 6)
        gem(-1f, 0f, 4)
        spire(-4.6f, 0f, 1); spire(4.7f, 0f, 4, 1.3f); crystal(-3.2f, 0f, 5); crystal(3.3f, 0f, 0, 0.8f)
        hangingBall(-4.8f, 0f, 8); hangingBall(5f, 0f, 10)
        chain(-4.8f, 5.2f, 8.5f, -7.5f, 3f, 12f, 0.8f)
        // the first gap and a blue block
        row(8, 0.5f, "YGY"); row(9, 1, "GBG"); row(10, 1, "RYR"); row(11, 1, "YGY"); row(12, 1, "PRP")
        island(8, 12, 1f, 0f, 5.4f, 1, 1)
        trig(7, Ev.HINT_TARGET)
        coin(0f, 0.9f, 7)
        // the first spiked ball swinging across the path
        row(13, 1, "GYG"); row(14, 1, "YRY"); row(15, 1, "GYG"); row(16, 1, "RYR"); row(17, 1, "GMG")
        island(13, 17, 1f, 0f, 5.2f, 2, 1)
        mace(0f, 1f, 15, 0.95f, 3.1f, 0.3f)
        gem(1f, 1f, 17)
        // a moving block over the gap
        moving(0f, 1, 19, 1.0f, 1.8f, 0f, 2.2f, BC.PURPLE)
        save(0f, -2, 19)
        row(21, 1, "GYG"); row(22, 1.5f, "YBY"); row(23, 2, "RGR"); row(24, 2, "GYG"); row(25, 2, "YRY")
        island(21, 21, 1f, 0f, 3.6f, 0, 0); tower(22, 22, 1.5f); island(23, 25, 2f, 0f, 5.2f, 1, 1)
        mystery(-2.2f, 2f, 24, Reward.COINS, BC.YELLOW)
        spire(4.4f, 2f, 23)
        // a spiked block sliding across the path on its rail
        row(26, 2, "GYG"); row(27, 2, "YPY"); row(28, 2, "GYG"); row(29, 2, "RYR"); row(30, 2, "GMG")
        island(26, 30, 2f, 0f, 5.2f, 2, 1)
        spikeBox(0f, 2f, 28, amp = 1.3f, speed = 1.5f, phase = 0f)
        coin(-1f, 2f, 26); coin(1f, 2f, 30)
        // up the steps, vanishing stones, the first checkpoint
        row(32, 2.5f, "YGY"); row(33, 3, "GBG")
        tower(32, 32, 2.5f); island(33, 33, 3f, 0f, 3.6f, 0, 0)
        row(35, 3, "d.d", -1); row(36, 3, ".d.", -1)
        save(0f, 0, 35)
        row(37, 3, "GYG"); row(38, 3, "RGR"); moonCheckpoint(39, 3); row(40, 3, "GYG"); row(41, 3, "YMY"); row(42, 3, "GYG")
        island(37, 42, 3f, 0f, 5.8f, 3, 2)
        coinLine(0f, 3f, 40, 42)
        spire(-4.4f, 3f, 38); crystal(3.3f, 3f, 40)
        row(43, 3, "YMY"); row(44, 3, "GYG"); row(45, 3, "RYR")
        island(43, 45, 3f, 0f, 4.8f, 1, 1)
        gem(0f, 3f, 44)

        // ================= SECTION 2 : THE MINION AMBUSH =================
        zo = 46; lo = 3f
        section("ambush", 0, 33)
        wave(1, 0, 1, "MINIONS AHEAD!", "THE SORCERER SENDS HIS IMPS — DODGE THEM OR STOMP THEM")
        row(0, 0, "GYG"); row(1, 0, "YRY")
        island(0, 1, 0f, 0f, 4.6f, 0, 1)
        trig(1, Ev.MINIONS, "", "1")
        trig(3, Ev.TUT, "Jump on an imp to STOMP it — or steer round it!", "7")
        // the ambush plaza: a wide stretch and a patrolling imp
        row(2, 0, "YRGRY", -2); row(3, 0, "GYRYG", -2); row(4, 0, "RGYGR", -2); row(5, 0, "YRGRY", -2); row(6, 0, "GYBYG", -2); row(7, 0, "RGYGR", -2)
        island(2, 7, 0f, 0f, 6.6f, 3, 2)
        patrol(1, 0f, 0f, 4, 1.6f, 1.5f)
        gem(-2f, 0f, 6); gem(2f, 0f, 3); coin(0f, 0f, 2); coin(0f, 0f, 7)
        spire(-4.7f, 0f, 3); spire(4.7f, 0f, 6)
        // over a gap to a stretch an orb imp swoops across
        row(9, 0.5f, "GYG"); row(10, 1, "YRY"); row(11, 1, "GYG"); row(12, 1, "RYR"); row(13, 1, "GBG"); row(14, 1, "YGY")
        tower(9, 9, 0.5f); island(10, 14, 1f, 0f, 5f, 2, 1)
        swoop(1, 0f, 1f, 12, 1.2f, 2.8f, 0f)
        coin(0f, 1f, 10); coin(0f, 1f, 14)
        // a moving block over the gap; an imp patrols the landing
        moving(0f, 1, 16, 1.0f, 1.8f, 0.5f, 2.2f, BC.MAGENTA)
        save(0f, -2, 16)
        row(18, 1, "GYG"); row(19, 1, "YRY"); row(20, 1, "GYG"); row(21, 1, "RBR"); row(22, 1, "GYG")
        island(18, 22, 1f, 0f, 5f, 3, 1)
        patrol(1, 0f, 1f, 20, 1.0f, 1.3f, 1.5f)
        coin(-1f, 1f, 18); coin(1f, 1f, 22)
        // up the steps, a second orb imp, the checkpoint
        row(23, 1.5f, "YGY"); row(24, 2, "GRG"); row(25, 2, "YGY"); row(26, 2, "GYG"); row(27, 2, "MGM"); row(28, 2, "YGY")
        tower(23, 23, 1.5f); island(24, 28, 2f, 0f, 5.2f, 1, 2)
        swoop(1, 0f, 2f, 26, 1.2f, 3.0f, 1.4f)
        gem(0f, 2f, 28)
        row(29, 2, "GYG"); moonCheckpoint(30, 2); row(31, 2, "YGY"); row(32, 2, "GpG"); row(33, 2, "YGY")
        island(29, 33, 2f, 0f, 5.6f, 3, 2)
        trig(30, Ev.MINIONS_END, "", "1")
        crystal(-3.2f, 2f, 31); crystal(3.2f, 2f, 32, 1.2f)

        // ================= SECTION 3 : THE CRYSTAL STAIRS =================
        zo = 80; lo = 5f
        section("stairs", 0, 58)
        trig(0, Ev.ZONE, "THE CRYSTAL STAIRS", "NARROW PATHS — WATCH YOUR STEP")
        row(0, 0, "GYG"); row(1, 0, "YRY")
        island(0, 1, 0f, 0f, 4.6f, 0, 1)
        // half steps up a narrow two-lane path
        row(2, 0.5f, "GY", -1); row(3, 1, "YG", -1); row(4, 1.5f, "MY", -1); row(5, 2, "YM", -1)
        for (k in 0..3) tower(2 + k, 2 + k, 0.5f + 0.5f * k, -0.5f, 2.3f)
        for (k in 0..3) coin(-0.5f, 0.5f + 0.5f * k, 2 + k)
        // a cracked bridge: keep moving
        row(6, 2, "ccc"); row(7, 2, "ccc"); row(8, 2, "ccc"); row(9, 2, "GYG")
        tower(6, 8, 2f); island(9, 9, 2f, 0f, 4.4f, 0, 0)
        // an arcane beam: jump it or pass while it is off
        row(10, 2, "YGY"); row(11, 2, "GBG"); row(12, 2, "YGY")
        island(10, 12, 2f, 0f, 5f, 2, 1)
        laser(11, 2f, period = 2.9f, onFor = 1.0f, phase = 0.6f)
        trig(9, Ev.TUT, "ARCANE BEAM! Jump it — or pass while it is off", "7")
        // a jump up a whole block
        row(14, 3, "GYG"); row(15, 3, "YRY"); row(16, 3, "GYG")
        island(14, 16, 3f, 0f, 5f, 1, 1)
        gem(0f, 3f, 15)
        // vanishing stones zig-zagging up
        row(18, 3, "d.d", -1); row(19, 3.5f, ".d.", -1); row(20, 4, "d.d", -1)
        save(0f, 1, 19)
        coin(0f, 3.6f, 18); coin(0f, 4.1f, 19); coin(0f, 4.6f, 20)
        // a spiked block sliding across the path
        row(21, 4, "GYG"); row(22, 4, "YGY"); row(23, 4, "GYG"); row(24, 4, "RYR"); row(25, 4, "GBG")
        island(21, 25, 4f, 0f, 5.2f, 3, 1)
        spikeBox(0f, 4f, 25, amp = 1.3f, speed = 1.7f, phase = 0.5f)
        spire(-4.4f, 4f, 21); crystal(3.2f, 4f, 22)
        // springs up to a high island
        row(26, 4, "bbb"); tower(26, 26, 4f)
        coin(0f, 5.6f, 26)
        row(27, 6, "GRG"); row(28, 6, "YGY"); row(29, 6, "GYG")
        island(27, 29, 6f, 0f, 5f, 2, 1)
        // the fork: the safe crystal bridge on the left, treasure on moving blocks on the right
        row(30, 6, "YRGRY", -2); row(31, 6, "GYRYG", -2)
        island(30, 31, 6f, 0f, 6.4f, 0, 2)
        trig(29, Ev.FORK)
        // right: moving blocks, gems, a blue block and a mystery box
        ox = 2.5f
        row(32, 6, "BY", 0, false); row(33, 6, "YG", 0, false)
        moving(0.5f, 6, 35, 0.8f, 1.7f, 0f, 2f, BC.PURPLE)
        row(37, 6, "YG", 0, false); row(38, 6, "RB", 0, false); row(39, 6, "GY", 0, false)
        mystery(1.5f, 6f, 39, Reward.GEMS, BC.GREEN)
        moving(0.5f, 6, 41, 0.9f, 1.9f, 1.4f, 2f, BC.YELLOW)
        row(43, 6, "GR", 0, false); row(44, 6, "YG", 0, false); row(45, 6, "RY", -1, false)
        island(32, 33, 6f, 0.5f, 2.6f, 2, 0); island(37, 39, 6f, 0.5f, 2.6f, 2, 1); island(43, 45, 6f, 0.5f, 2.6f, 2, 0)
        gem(0.5f, 6f, 35); gem(0.5f, 6f, 41); gem(0f, 6f, 44)
        // left: the crystal bridge with a line of coins (the main way)
        ox = -2.5f
        bridge(32, 45, 6f, -1, 0)
        coinLine(-0.5f, 6f, 32, 45)
        tower(38, 39, 6f, -0.5f, 2.3f)
        crystal(-2.2f, 6f, 36, 0.9f)
        // both ways meet on the checkpoint island
        ox = 0f
        row(46, 6, "YRGRY", -2); row(47, 6, "GYRYG", -2); row(48, 6, "RGYGR", -2); row(49, 6, "KK.KK", -2); checkpoint(49, 6)
        row(50, 6, "YGRGY", -2); row(51, 6, "GYpYG", -2)
        island(46, 51, 6f, 0f, 6.6f, 7, 3)
        coin(-1f, 6f, 47); coin(1f, 6f, 47); coinLine(0f, 6f, 50, 51)
        spire(-4.8f, 6f, 47); spire(4.8f, 6f, 48, 1.3f)
        row(52, 6, "GYG"); row(53, 6.5f, "YGY"); row(54, 7, "RYR"); row(55, 7.5f, "GYG"); row(56, 8, "YRY"); row(57, 8, "GMG"); row(58, 8, "YGY")
        island(52, 52, 6f, 0f, 4.4f, 0, 0); tower(53, 53, 6.5f); tower(54, 54, 7f); tower(55, 55, 7.5f); island(56, 58, 8f, 0f, 4.6f, 2, 1)

        // ================= SECTION 4 : THE RUNAWAY RELIC =================
        zo = 139; lo = 13f
        section("relic", 0, 64)
        trig(0, Ev.RELIC)
        w.relicZ0 = 2 + zo; w.relicZ1 = 61 + zo
        row(0, 0, "GYG"); row(1, 0, "RGR"); row(2, 0, "YpY"); row(3, 0, "GYG"); row(4, 0, "RGR"); row(5, 0, "GBG")
        island(0, 5, 0f, 0f, 5.2f, 3, 1)
        row(7, 0, "YGY"); row(8, 0, "GYG"); row(9, 0, "YRY"); row(10, 0, "GYG")
        island(7, 10, 0f, 0f, 5f, 1, 1)
        laser(8, 0f, period = 3.4f, onFor = 0.9f, phase = 2.0f)
        row(12, 0.5f, "GYG"); row(13, 1, "YGY"); row(14, 1, "GpG"); row(15, 1, "RYR"); row(16, 1, "GYG")
        tower(12, 12, 0.5f); island(13, 16, 1f, 0f, 5f, 2, 1)
        // speed pads into a long jump
        row(17, 1, "YpY"); row(18, 1, "GpG")
        island(17, 18, 1f, 0f, 4.6f, 0, 0)
        coin(0f, 1.9f, 19); coin(0f, 2.2f, 20)
        row(21, 1, "YGY"); row(22, 1, "GBG"); row(23, 1, "YRY"); row(24, 1, "GYG")
        island(21, 24, 1f, 0f, 5f, 3, 1)
        row(25, 0.5f, "GYG"); row(26, 0, "YGY"); row(27, 0, "GpG"); row(28, 0, "RYR"); row(29, 0, "GYG")
        tower(25, 25, 0.5f); island(26, 29, 0f, 0f, 5f, 2, 2)
        bridge(30, 35, 0f)
        row(36, 0, "GYG"); row(37, 0, "YpY"); row(38, 0, "GBG"); row(39, 0, "YGY")
        island(36, 39, 0f, 0f, 5f, 1, 1)
        row(40, 0.5f, "GYG"); row(41, 1, "YGY"); row(42, 1.5f, "GRG"); row(43, 2, "YGY")
        tower(40, 40, 0.5f); tower(41, 41, 1f); tower(42, 42, 1.5f); tower(43, 43, 2f)
        row(44, 2, "GpG"); row(45, 2, "YRY"); row(46, 2, "GYG"); row(47, 2, "MYM")
        island(44, 47, 2f, 0f, 5.2f, 3, 2)
        spikeBox(-2.2f, 2f, 45); spikeBox(2.2f, 2f, 47)
        row(49, 2, "GYG"); row(50, 2, "YpY"); row(51, 2, "GYG"); row(52, 2, "RBR"); row(53, 2, "GYG")
        island(49, 53, 2f, 0f, 5f, 2, 1)
        row(54, 2.5f, "YGY"); row(55, 3, "GYG"); tower(54, 54, 2.5f); tower(55, 55, 3f)
        row(56, 3, "GYG"); row(57, 3, "YMY"); row(58, 3, "GYG"); row(59, 3, "RGR"); moonCheckpoint(60, 3); row(61, 3, "YGY"); row(62, 3, "GYG")
        island(56, 62, 3f, 0f, 5.8f, 3, 2)
        row(63, 3, "YMY"); row(64, 3, "GYG"); island(63, 64, 3f, 0f, 4.6f, 1, 0)
        coinLine(0f, 0f, 0, 5); coinLine(0f, 0f, 7, 10); coinLine(0f, 1f, 21, 24); coinLine(0f, 0f, 36, 39); coinLine(0f, 2f, 49, 53); coinLine(0f, 3f, 56, 59)
        gem(-1f, 0f, 3); gem(1f, 1f, 23); gem(0f, 2f, 46); gem(-1f, 3f, 62)
        spire(-4.4f, 0f, 2); spire(4.4f, 1f, 22); spire(-4.5f, 3f, 58); crystal(3.3f, 0f, 37); crystal(-3.2f, 2f, 50)
        hangingBall(-5f, 1f, 14); hangingBall(5.2f, 0f, 33)

        // ================= SECTION 5 : THE SORCERER'S GAUNTLET =================
        zo = 204; lo = 16f
        section("gauntlet", 0, 53)
        trig(2, Ev.ZONE, "THE SORCERER'S GAUNTLET", "TIME EVERY STEP")
        row(0, 0, "GYG"); row(1, 0, "YRY"); row(2, 0, "GYG"); row(3, 0, "RYR")
        island(0, 3, 0f, 0f, 5.6f, 3, 1)
        // a big spiked ball swinging across the path
        row(4, 0, "GYG"); row(5, 0, "YGY"); row(6, 0, "GRG"); row(7, 0, "YGY"); row(8, 0, "GYG"); row(9, 0, "YGY")
        island(4, 9, 0f, 0f, 5f, 2, 1)
        mace(0f, 0f, 6, 1.0f, 2.9f, -0.4f)
        hangingBall(-4.8f, 0f, 9); hangingBall(5f, 0f, 11)
        // an arcane beam between spiked blocks
        row(11, 0, "GYG"); row(12, 0, "YBY"); row(13, 0, "GYG"); row(14, 0, "RYR")
        island(11, 14, 0f, 0f, 5.2f, 1, 1)
        laser(14, 0f, period = 2.6f, onFor = 1.0f, phase = 0.9f)
        spikeBox(-2.2f, 0f, 12); spikeBox(2.2f, 0f, 13)
        // lava-cracked falling blocks and a spike row: jump it
        row(15, 0, "fff"); row(16, 0, "fff"); row(17, 0, "GYG"); row(18, 0, "^^^"); row(19, 0, "GYG"); row(20, 0, "YBY")
        tower(15, 16, 0f); island(17, 20, 0f, 0f, 4.6f, 3, 0)
        coin(0f, 1.3f, 18)
        // platforms sliding in turn over a long drop
        moving(0f, 0, 22, 1.1f, 1.6f, 0f, 2.2f, BC.PURPLE)
        moving(0f, 0, 24, 1.1f, 1.6f, 1.8f, 2.2f, BC.GREEN)
        save(0f, -3, 23)
        coin(0f, 0.6f, 22); coin(0f, 0.6f, 24)
        row(26, 0, "GYG"); row(27, 0, "YRY"); row(28, 0, "GYG")
        island(26, 28, 0f, 0f, 4.8f, 2, 1)
        // a narrow bridge under a spiked ball
        bridge(29, 34, 0f, 0, 0)
        mace(0f, 0f, 32, 0.9f, 3.0f, 0.5f)
        row(35, 0, "GYG"); row(36, 0, "YGY"); row(37, 0, "GRG")
        island(35, 37, 0f, 0f, 4.6f, 2, 1)
        // a spring up, a cracked stretch and a sliding spiked block
        row(38, 0, "YbY"); tower(38, 38, 0f)
        coin(0f, 1.6f, 38)
        row(39, 2, "GYG"); row(40, 2, "ccc"); row(41, 2, "ccc"); row(42, 2, "GYG"); row(43, 2, "YGY"); row(44, 2, "GBG"); row(45, 2, "YGY")
        island(39, 45, 2f, 0f, 5.2f, 3, 1)
        spikeBox(0f, 2f, 45, amp = 1.3f, speed = 1.7f, phase = 0f)
        row(46, 2, "GYG"); row(47, 2, "YGY"); row(48, 2, "GYG"); moonCheckpoint(49, 2); row(50, 2, "YGY"); row(51, 2, "GMG"); row(52, 2, "YGY"); row(53, 2, "GYG")
        island(46, 53, 2f, 0f, 6f, 3, 3)
        mystery(-2f, 2f, 50, Reward.HEART, BC.GREEN); mystery(2f, 2f, 50, Reward.TOOL_SHIELD, BC.GREEN)
        coinLine(0f, 2f, 50, 53); gem(0f, 0f, 27); gem(0f, 2f, 47)
        spire(-4.4f, 0f, 1); spire(4.5f, 2f, 47, 1.3f); crystal(-3.4f, 2f, 52); crystal(3.3f, 0f, 36)

        // ================= SECTION 6 : THE STARWIND BRIDGES =================
        zo = 258; lo = 18f
        section("starwind", 0, 44)
        row(0, 0, "GYG"); row(1, 0, "YRY")
        island(0, 1, 0f, 0f, 4.6f, 0, 1)
        trig(2, Ev.WIND)
        bridge(3, 8, 0f)
        coinLine(0f, 0f, 3, 8)
        // spikes at the path's edge (one side, then the other): steer against the gusts
        row(9, 0, "tYY"); row(10, 0, "YGt"); row(11, 0, "tYY"); row(12, 0, "GBG")
        island(9, 12, 0f, 0f, 4.6f, 3, 0)
        row(14, 0, "GYG"); row(15, 0, "YGY"); row(16, 0, "GYG")
        island(14, 16, 0f, 0f, 4.8f, 2, 1)
        gem(0f, 0f, 16)
        // a one-lane bridge in the wind
        bridge(17, 22, 0f, 0, 0)
        row(23, 0, "GYG"); row(24, 0, "YRt"); row(25, 0, "tYY"); row(26, 0, "GYG"); row(27, 0, "YBY")
        island(23, 27, 0f, 0f, 4.6f, 3, 1)
        // jumps up in the wind
        row(29, 1, "GYG"); row(30, 1, "YGY"); row(31, 1.5f, "GYG"); row(32, 2, "GYG"); row(33, 2, "YRY"); row(34, 2, "tGY"); row(35, 2, "YYt"); row(36, 2, "GYG")
        island(29, 30, 1f, 0f, 4.4f, 1, 0); tower(31, 31, 1.5f); island(32, 36, 2f, 0f, 4.8f, 2, 1)
        trig(37, Ev.WIND_STOP)
        row(37, 2, "YGY"); row(38, 2, "GYG"); moonCheckpoint(39, 2); row(40, 2, "YGY"); row(41, 2, "GpG"); row(42, 2, "YMY"); row(43, 2, "GYG"); row(44, 2, "YGY")
        island(37, 44, 2f, 0f, 5.8f, 3, 2)
        coinLine(0f, 0f, 14, 16); coinLine(0f, 1f, 29, 30); coinLine(0f, 2f, 40, 44)
        spire(-4.4f, 0f, 15); crystal(3.1f, 2f, 38); spire(4.6f, 2f, 42, 1.3f)
        hangingBall(-5f, 0f, 20); hangingBall(5f, 1f, 28)

        // ================= SECTION 7 : THE MINION PURSUIT =================
        zo = 303; lo = 20f
        section("pursuit", 0, 63)
        wave(2, 3, 2, "THE SORCERER'S PURSUIT!", "HIS MINIONS ARE AFTER YOU — KEEP RUNNING!")
        row(0, 0, "GYG"); row(1, 0, "YRY"); row(2, 0, "GYG")
        island(0, 2, 0f, 0f, 4.8f, 1, 1)
        trig(2, Ev.MINIONS, "", "2")
        row(3, 0, "YGY"); row(4, 0, "GYG"); row(5, 0, "YRY"); row(6, 0, "GYG")
        island(3, 6, 0f, 0f, 5f, 2, 1)
        row(8, 0, "GYG"); row(9, 0, "YBY"); row(10, 0, "GYG"); row(11, 0, "RYR")
        island(8, 11, 0f, 0f, 5f, 3, 1)
        // spikes down the middle: take either side
        row(12, 0, "YRGRY", -2); row(13, 0, "GY^YG", -2); row(14, 0, "YG^GY", -2); row(15, 0, "GY^YG", -2); row(16, 0, "YRGRY", -2); row(17, 0, "GYRYG", -2)
        island(12, 17, 0f, 0f, 6.4f, 3, 2)
        coinLine(-1f, 0f, 13, 15); gem(1f, 0f, 14)
        // an orb imp over the next stretch
        row(18, 0, "GYG"); row(19, 0, "YGY"); row(20, 0, "GYG"); row(21, 0, "RBR"); row(22, 0, "GYG")
        island(18, 22, 0f, 0f, 5f, 2, 1)
        swoop(2, 0f, 0f, 21, 1.2f, 3.0f, 0.7f)
        // half steps up (no stopping: they are after you)
        row(23, 0.5f, "YGY"); row(24, 1, "GYG"); row(25, 1.5f, "YRY"); row(26, 2, "GMG")
        tower(23, 23, 0.5f); tower(24, 24, 1f); tower(25, 25, 1.5f); tower(26, 26, 2f)
        coin(0f, 0.5f, 23); coin(0f, 1f, 24); coin(0f, 1.5f, 25)
        row(27, 2, "GYG"); row(28, 2, "YGY"); row(29, 2, "GYG"); row(30, 2, "YRY"); row(31, 2, "GBG")
        island(27, 31, 2f, 0f, 5.2f, 3, 1)
        patrol(2, 0f, 2f, 30, 1.0f, 1.5f, 0.8f)
        bridge(32, 37, 2f)
        coinLine(0f, 2f, 32, 37)
        row(38, 2, "GYG"); row(39, 2, "YpY"); row(40, 2, "GYG"); row(41, 2, "MBM")
        island(38, 41, 2f, 0f, 5f, 2, 1)
        // down a step, speed pads and a long jump
        row(42, 1.5f, "GYG"); row(43, 1, "YGY"); row(44, 1, "GpG"); row(45, 1, "YpY")
        tower(42, 42, 1.5f); island(43, 45, 1f, 0f, 4.6f, 0, 1)
        coin(0f, 1.9f, 46); coin(0f, 2.2f, 47)
        row(48, 1, "GYG"); row(49, 1, "YRY"); row(50, 1, "GYG"); row(51, 1, "YGY")
        island(48, 51, 1f, 0f, 5f, 3, 1)
        swoop(2, 0f, 1f, 50, 1.2f, 2.8f, 0.2f)
        row(52, 1.5f, "GYG"); row(53, 2, "YGY"); row(54, 2, "GYG"); row(55, 2, "YRY"); row(56, 2, "GBG"); row(57, 2, "YGY")
        tower(52, 52, 1.5f); island(53, 57, 2f, 0f, 5f, 2, 1)
        patrol(2, 0f, 2f, 56, 1.0f, 1.4f, 2.2f)
        row(58, 2, "GYG"); moonCheckpoint(59, 2); row(60, 2, "YGY"); row(61, 2, "GYG"); row(62, 2, "YGY"); row(63, 2, "GMG")
        island(58, 63, 2f, 0f, 5.8f, 3, 2)
        trig(59, Ev.MINIONS_END, "", "2")
        mystery(-2f, 2f, 61, Reward.HEART, BC.GREEN)
        coinLine(0f, 0f, 3, 6); coinLine(0f, 1f, 48, 51); gem(0f, 2f, 28); gem(0f, 2f, 54)
        spire(-4.5f, 0f, 9); spire(4.5f, 2f, 29, 1.3f); crystal(-3.3f, 1f, 49); spire(-4.6f, 2f, 61)

        // ================= SECTION 8 : THE FLOATING MAZE =================
        zo = 367; lo = 22f
        section("maze", 0, 47)
        wave(3, 0, 1, "MINIONS ON THE ISLANDS!", "THE LAST OF THE SORCERER'S IMPS")
        row(0, 0, "GYG"); row(1, 0, "YRY")
        island(0, 1, 0f, 0f, 4.6f, 0, 1)
        trig(1, Ev.MINIONS, "", "3")
        // platforms sliding in turn over a long drop
        moving(0f, 0, 3, 1.1f, 1.5f, 0f, 2.4f, BC.PURPLE)
        moving(0f, 0, 5, 1.1f, 1.5f, 1.6f, 2.4f, BC.GREEN)
        moving(0f, 0, 7, 1.1f, 1.5f, 3.2f, 2.4f, BC.YELLOW)
        save(0f, -3, 4); save(0f, -3, 6)
        coin(0f, 0.6f, 3); coin(0f, 0.6f, 5); coin(0f, 0.6f, 7)
        row(9, 0, "GYG"); row(10, 0, "RBR"); row(11, 0, "GYG")
        island(9, 11, 0f, 0f, 4.8f, 3, 1)
        // a bridge an imp patrols
        bridge(12, 18, 0f)
        patrol(3, 0f, 0f, 15, 1.0f, 1.4f, 0f)
        row(19, 0, "GYG"); row(20, 0, "YGY"); row(21, 0, "GYG")
        island(19, 21, 0f, 0f, 4.8f, 2, 1)
        // vanishing stones zig-zagging up
        row(22, 0, "d.d", -1); row(23, 0.5f, ".d.", -1); row(24, 1, "d.d", -1)
        save(0f, -2, 23)
        coin(0f, 0.6f, 22); coin(0f, 1.1f, 23); coin(0f, 1.6f, 24)
        row(25, 1, "GYG"); row(26, 1, "YGY"); row(27, 1, "GRG"); row(28, 1, "YGY"); row(29, 1, "GYG"); row(30, 1, "YBY")
        island(25, 30, 1f, 0f, 5f, 3, 2)
        swoop(3, 0f, 1f, 29, 1.2f, 2.7f, 0.4f)
        // a moving platform, an arcane beam
        moving(0f, 1, 32, 1.2f, 2.0f, 0.8f, 2f, BC.MAGENTA)
        save(0f, -2, 32)
        row(34, 1, "GYG"); row(35, 1, "YRY"); row(36, 1, "GYG"); row(37, 1, "YGY")
        island(34, 37, 1f, 0f, 5f, 2, 1)
        laser(36, 1f, period = 2.4f, onFor = 1.0f, phase = 1.2f)
        row(38, 2, "GYG"); row(39, 2, "YGY"); row(40, 2, "GBG"); row(41, 2, "YGY")
        island(38, 41, 2f, 0f, 5f, 3, 1)
        patrol(3, 0f, 2f, 40, 1.0f, 1.6f, 2.0f)
        row(42, 2, "GYG"); moonCheckpoint(43, 2); row(44, 2, "YGY"); row(45, 2, "GpG"); row(46, 2, "YGY"); row(47, 2, "GYG")
        island(42, 47, 2f, 0f, 5.6f, 3, 2)
        trig(43, Ev.MINIONS_END, "", "3")
        mystery(-2f, 2f, 45, Reward.TOOL_SPEED, BC.BLUE)
        coinLine(0f, 0f, 9, 11); coinLine(0f, 1f, 25, 29); coinLine(0f, 2f, 44, 47); gem(0f, 0f, 20); gem(0f, 1f, 35)
        spire(-4.4f, 0f, 10); crystal(3.2f, 1f, 26); spire(4.5f, 2f, 46, 1.3f); hangingBall(-5f, 0f, 16)

        // ================= SECTION 9 : THE FINAL ASCENT =================
        // (the star bridge fades behind you: only things you run and jump through, nothing that makes you wait)
        zo = 415; lo = 24f
        section("ascent", 0, 66)
        trig(1, Ev.FINAL)
        row(0, 0, "GYG"); row(1, 0, "YGY")
        island(0, 1, 0f, 0f, 4.6f, 0, 1)
        for (i in 0 until 10) {
            val z = 2 + i
            row(z, 0.5f * (i + 1), if (i == 5) "RBR" else if (i % 2 == 0) "YGY" else "GMG")
            tower(z, z, 0.5f * (i + 1))
        }
        for (i in 0 until 10) coin(0f, 0.5f * (i + 1), 2 + i)
        row(12, 5, "GYG"); row(13, 5, "YRY"); row(14, 5, "GYG"); row(15, 5, "YGY")
        island(12, 15, 5f, 0f, 4.8f, 3, 1)
        laser(13, 5f, period = 2.6f, onFor = 1.0f, phase = 0.2f)
        // stepping stones up
        row(16, 5.5f, "GYG"); row(17, 6, "Y.Y", -1); row(18, 6.5f, ".G.", -1); row(19, 7, "YRY")
        tower(16, 16, 5.5f); tower(17, 17, 6f, 0f, 1.2f); tower(18, 18, 6.5f, 0f, 1.2f); island(19, 19, 7f, 0f, 4f, 0, 0)
        row(20, 7.5f, "GYG"); row(21, 8, "YGY"); row(22, 8.5f, "RGR"); row(23, 9, "GYG")
        tower(20, 20, 7.5f); tower(21, 21, 8f); tower(22, 22, 8.5f); island(23, 23, 9f, 0f, 4.4f, 2, 0)
        // a bridge with an arcane beam at its end
        bridge(24, 29, 9f)
        laser(29, 9f, period = 2.8f, onFor = 1.0f, phase = 1.0f)
        row(30, 9.5f, "YGY"); row(31, 10, "GRG"); row(32, 10.5f, "YGY"); row(33, 11, "GYG")
        tower(30, 30, 9.5f); tower(31, 31, 10f); tower(32, 32, 10.5f); island(33, 33, 11f, 0f, 4.4f, 1, 1)
        // a spike row to jump
        row(34, 11, "GYG"); row(35, 11, "^^^"); row(36, 11, "GYG"); row(37, 11, "YBY")
        island(34, 37, 11f, 0f, 5f, 3, 1)
        coin(0f, 12.3f, 35)
        // a cracked stretch: keep moving
        row(38, 11, "ccc"); row(39, 11, "ccc"); row(40, 11, "ccc"); row(41, 11, "GYG")
        tower(38, 40, 11f); island(41, 41, 11f, 0f, 4.4f, 0, 0)
        row(42, 11.5f, "YGY"); row(43, 12, "RBR"); row(44, 12.5f, "YGY"); row(45, 13, "GMG")
        tower(42, 42, 11.5f); tower(43, 43, 12f); tower(44, 44, 12.5f); island(45, 45, 13f, 0f, 4.4f, 3, 1)
        row(46, 13, "GYG"); row(47, 13, "YGY"); row(48, 13, "GMG")
        island(46, 48, 13f, 0f, 4.6f, 0, 0)
        laser(47, 13f, period = 2.3f, onFor = 1.0f, phase = 1.5f)
        // speed pads into a long jump
        row(49, 13, "YpY"); row(50, 13, "GpG")
        island(49, 50, 13f, 0f, 4.6f, 1, 0)
        coin(0f, 13.9f, 51); coin(0f, 14.2f, 52)
        row(53, 13, "GYG"); row(54, 13, "YRY"); row(55, 13, "GYG")
        island(53, 55, 13f, 0f, 4.8f, 2, 1)
        // crumbling blocks, then the last climb
        row(56, 13, "fff"); row(57, 13, "fff")
        tower(56, 57, 13f)
        row(58, 13.5f, "GYG"); row(59, 14, "YBY"); row(60, 14.5f, "GYG"); row(61, 15, "YGY"); row(62, 15, "GRG")
        tower(58, 58, 13.5f); tower(59, 59, 14f); tower(60, 60, 14.5f); island(61, 62, 15f, 0f, 4.6f, 3, 1)
        row(63, 15.5f, "YGY"); row(64, 16, "GpG"); row(65, 16, "YGY"); row(66, 16, "GYG")
        tower(63, 63, 15.5f); island(64, 66, 16f, 0f, 4.6f, 2, 1)
        coinLine(0f, 5f, 12, 15); coinLine(0f, 9f, 24, 28); coinLine(0f, 11f, 36, 37); coinLine(0f, 13f, 46, 48); coinLine(0f, 15f, 61, 62); coinLine(0f, 16f, 64, 66)
        gem(0f, 11f, 34); gem(0f, 13f, 54); gem(0f, 16f, 66)
        collapsible(-6, 66)
        spire(-4.4f, 11f, 36); spire(4.4f, 13f, 47); crystal(-3.2f, 15f, 62, 1.1f); crystal(3.3f, 16f, 66)

        // ================= THE CELESTIAL STAIRS AND THE GATE =================
        zo = 482; lo = 40f
        section("gate", 0, 22)
        w.finalSafeZ = (0 + zo).toFloat()
        row(0, 0, "YGY"); row(1, 0, "GRG")
        island(0, 1, 0f, 0f, 5f, 3, 1)
        val stairs = arrayOf("KYPYK", "KGMGK", "KYBYK", "KGRGK", "KYPYK", "KGMGK", "KYPYK", "KGRGK", "KYPYK", "KGMGK", "KYPYK", "KGRGK")
        for ((i, st) in stairs.withIndex()) row(2 + i, 0.5f + i * 0.5f, st, -2)
        for (i in stairs.indices) coin(0f, 0.5f + i * 0.5f, 2 + i)
        for (i in stairs.indices) tower(2 + i, 2 + i, 0.5f + i * 0.5f, 0f, 5.3f)
        stoneRow(14, 6f, -2, 2); stoneRow(15, 6f, -2, 2); stoneRow(16, 6f, -2, 2)
        island(14, 16, 6f, 0f, 7f, 7, 2, 6f)
        crystal(-3.8f, 6f, 14, 1.2f); crystal(3.8f, 6f, 14, 1.2f)
        // the Celestial Gate's star portal (the design's destination) at the far edge of the plaza
        w.portals.add(Portal(ox, 6f + lo, (17 + zo).toFloat() + 0.2f))

        // in the enchanted realm every stone is moonstone
        for (b in w.blocks) {
            if (b.color == BC.BRICK && b.type == BT.BRICK) b.color = BC.MOON
            if (b.type == BT.CHECKPOINT) b.color = BC.MOON
            b.remember()
        }
    }
}
