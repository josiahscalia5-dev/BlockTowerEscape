package com.blocktower.escape.core

/**
 * Level 23 course. Rows run along +z; "lvl" is the walking-surface height (block top).
 * The first rows reproduce the composition of the Screen 4 artwork; after that the tower is
 * built as eight hand-placed sections that get steadily more intense:
 *
 *  1 intro  2 moving blocks  3 falling/cracked  4 trap gauntlet  5 Tower Guard chase
 *  6 checkpoint haven  7 final tower (rising lava)  8 final escape to the Ancient Gate
 *
 * Block colour tells the player how a block behaves: yellow cracked, orange falling, purple
 * vanishing, stone spikes, cyan star boost, gold save, magenta bounce, blue = objective.
 *
 * Row string legend (lane x starts at x0, default centred):
 *  R G Y P O  normal coloured blocks      K brick        B blue TARGET block
 *  c cracked    d disappearing   f falling   b bouncing   s colour-shifting
 *  t timed spike trap   ^ spike block (always armed)   * boost (star)   S save block
 *  a appears on approach   . empty
 */
object Level {
    fun build(): World {
        val w = World()
        w.rowMin = -8; w.rowMax = 250
        w.initRows()
        LevelWriter(w).write()
        return w
    }
}

private class LevelWriter(val w: World) {
    var ox = 0f
    /** Height offset applied to everything after the start staircase. */
    var lo = 0f
    private val rng = Rng(23)

    fun blk(x: Float, lvl: Float, z: Int, c: Int, t: BT = BT.NORMAL): Block {
        val b = Block(ox + x, lvl + lo - 1f, z.toFloat(), c, t)
        b.variant = (hash3((ox + x).toInt(), lvl.toInt(), z) ushr 3) % 3
        if (b.variant < 0) b.variant += 3
        when (t) {
            BT.TARGET -> w.targetTotal++
            BT.DISAPPEAR -> b.alpha = 1f
            BT.COLORSHIFT -> { b.phase = rng.f(0f, 5f) }
            // spikes fire in a wave along the course so a row's rhythm can be read and timed
            BT.TRAP -> { b.phase = ((z * 0.9f + x * 0.35f) % 2.6f + 2.6f) % 2.6f; b.speed = 1f }
            BT.APPEAR -> { b.rise = 1f; b.visible = false }
            else -> {}
        }
        b.remember()
        w.add(b)
        return b
    }

    fun row(z: Int, lvl: Int, s: String, x0: Int = -(s.length - 1) / 2, path: Boolean = true) = row(z, lvl.toFloat(), s, x0, path)

    fun row(z: Int, lvl: Float, s: String, x0: Int = -(s.length - 1) / 2, path: Boolean = true) {
        for (i in s.indices) {
            val ch = s[i]
            val x = (x0 + i).toFloat()
            val l = lvl
            when (ch) {
                'R' -> blk(x, l, z, BC.RED)
                'G' -> blk(x, l, z, BC.GREEN)
                'Y' -> blk(x, l, z, BC.YELLOW)
                'P' -> blk(x, l, z, BC.PURPLE)
                'O' -> blk(x, l, z, BC.ORANGE)
                'K' -> blk(x, l, z, BC.BRICK, BT.BRICK)
                'B' -> blk(x, l, z, BC.BLUE, BT.TARGET)
                'c' -> blk(x, l, z, BC.YELLOW, BT.CRACKED)
                'd' -> blk(x, l, z, BC.PURPLE, BT.DISAPPEAR)
                'f' -> blk(x, l, z, BC.IRON, BT.FALLING)
                'b' -> blk(x, l, z, BC.IRON, BT.BOUNCE)
                '^' -> blk(x, l, z, BC.IRON, BT.TRAP).also { it.speed = 0f }
                's' -> blk(x, l, z, BC.RED, BT.COLORSHIFT)
                't' -> blk(x, l, z, BC.STONE, BT.TRAP)
                '*' -> blk(x, l, z, BC.CYAN, BT.BOOST)
                'S' -> blk(x, l, z, BC.GOLD, BT.SAVE)
                'a' -> blk(x, l, z, pick(), BT.APPEAR)
                '.' -> {}
            }
        }
        if (path) { w.pathLevel[z] = lvl + lo; w.pathX[z] = ox }
    }

    private val palette = intArrayOf(BC.RED, BC.GREEN, BC.YELLOW, BC.PURPLE, BC.RED, BC.GREEN, BC.YELLOW)
    fun pick() = palette[rng.i(palette.size)]

    fun pillar(x: Int, topLvl: Int, z: Int, n: Int) {
        for (k in 1..n) blk(x.toFloat(), (topLvl - k).toFloat(), z, BC.BRICK, BT.BRICK)
    }
    fun pillarF(x: Int, topLvl: Float, z: Int, n: Int) {
        for (k in 1..n) blk(x.toFloat(), topLvl - k, z, BC.BRICK, BT.BRICK)
    }

    fun coin(x: Float, lvl: Float, z: Int, dz: Float = 0.5f) {
        w.coins.add(Coin(ox + x, lvl + lo + 0.85f, z + dz)); w.coinTotal++
    }
    fun coinLine(x: Float, lvl: Float, z0: Int, z1: Int) { for (z in z0..z1) coin(x, lvl, z) }

    fun mystery(x: Float, topLvl: Float, z: Int, reward: Int, color: Int = BC.GREEN): Block {
        val b = blk(x, topLvl, z, color, BT.MYSTERY)
        b.reward = reward
        w.mysteryTotal++
        return b
    }

    fun moving(x: Float, lvl: Int, z: Int, amp: Float, speed: Float, phase: Float, width: Float = 2f, c: Int = BC.GREEN, depth: Float = 1f): Block {
        val b = blk(x, lvl.toFloat(), z, c, BT.MOVING)
        b.sx = width; b.sz = depth; b.baseX = ox + x; b.amp = amp; b.speed = speed; b.phase = phase; b.lastX = b.x
        return b
    }

    fun save(x: Float, lvl: Int, z: Int) = blk(x, lvl.toFloat(), z, BC.GOLD, BT.SAVE)

    /** A floating power-up bubble one block above the walking surface [lvl]. */
    fun bubble(x: Float, lvl: Float, z: Int, kind: Int) { w.bubbles.add(Bubble(kind, ox + x, lvl + lo + 1.05f, z + 0.5f)) }

    /** Hidden bridge block: invisible until the player comes within [reveal] lanes of it. */
    fun hidden(x: Float, lvl: Int, z: Int, c: Int, reveal: Float): Block {
        val b = blk(x, lvl.toFloat(), z, c, BT.APPEAR)
        b.amp = reveal
        return b
    }

    fun checkpoint(z: Int, lvl: Int, x: Float = 0f) {
        val id = w.checkpoints.size
        val b = blk(x, lvl.toFloat(), z, BC.STONE, BT.CHECKPOINT)
        b.checkpointId = id
        w.checkpoints.add(Checkpoint(id, ox + x, lvl + lo, z + 0.5f))
    }

    /** Checkpoint platform: a rune block between two brick posts, standing on brick pillars. */
    fun checkpointRow(z: Int, lvl: Int) {
        row(z, lvl, "K.K"); checkpoint(z, lvl)
        pillar(-1, lvl, z, 2); pillar(1, lvl, z, 2)
    }

    /** Raise one block of a row (lane variety, as in the artwork's uneven stacks). */
    fun raise(x: Int, z: Int, dy: Float) {
        val r = w.row(z) ?: return
        for (b in r) if (absf(b.x - (ox + x)) < 0.01f && b.type != BT.MOVING) { b.y += dy; b.remember() }
    }

    fun trig(z: Int, ev: Int, text: String = "", text2: String = "") { w.triggers.add(Trigger(z.toFloat(), ox - 8f, ox + 8f, ev, text, text2)) }

    fun section(name: String, z0: Int, z1: Int) { w.sections.add(Section(name, z0, z1, ox)) }

    /** Marks every block in rows z0..z1 as part of a collapsing stretch of the tower. */
    fun collapsible(z0: Int, z1: Int) { for (b in w.blocks) if (b.row in z0..z1) b.eventTag = Ev.COLLAPSE }

    fun write() {
        // ================= SECTION 0 : START — composition of the artwork =================
        // a half-step staircase climbing toward the far portal, as in the artwork
        ox = 0f; lo = 0f
        section("start", -8, 13)
        w.spawnX = 0f; w.spawnY = 0f; w.spawnZ = 0.45f
        // the start pad counts as checkpoint 0
        w.checkpoints.add(Checkpoint(0, 0f, 0f, 0.45f).also { it.active = true })
        // lanes are separate columns of different heights, like the artwork's uneven stacks
        fun lane(x: Int, z: Int, lvl: Float, c: Char) { row(z, lvl, c.toString(), x, path = false) }
        val L = arrayOf(
            // z, left, centre, right
            Triple(-6, "Y-2.5", "B-3.0"), Triple(-5, "G-2.0", "Y-2.5"), Triple(-4, "R-2.0", "G-2.0"),
            Triple(-3, "Y-1.5", "B-1.5"), Triple(-2, "G-1.5", "B-1.0"), Triple(-1, "Y-1.0", "B-0.5"))
        val R = arrayOf("R-3.0", "R-2.5", "Y-2.0", "R-1.5", "Y-1.0", "R-0.5")
        for ((i, t) in L.withIndex()) {
            val z = t.first
            lane(-1, z, t.second.substring(1).toFloat(), t.second[0])
            lane(0, z, t.third.substring(1).toFloat(), t.third[0])
            lane(1, z, R[i].substring(1).toFloat(), R[i][0])
            w.pathLevel[z] = t.third.substring(1).toFloat(); w.pathX[z] = 0f
        }
        blk(2f, -1.0f, -1, BC.RED); blk(2f, -1.5f, -2, BC.RED)
        pillarF(-2, -1.5f, -1, 1); blk(-2f, -1.5f, 0, BC.BRICK, BT.BRICK); blk(-2f, -2.5f, 0, BC.BRICK, BT.BRICK)
        // player row
        lane(-1, 0, -0.5f, 'G'); lane(0, 0, 0f, 'B'); lane(1, 0, 0f, 'Y')
        w.pathLevel[0] = 0f; w.pathX[0] = 0f
        pillarF(-1, -0.5f, 0, 1)
        lane(-1, 1, 0.5f, 'R'); lane(0, 1, 0.5f, 'R'); lane(1, 1, 0f, 'G')
        lane(-1, 2, 1.0f, 'R'); lane(0, 2, 0.5f, 'R'); lane(1, 2, 0.5f, 'G')
        w.pathLevel[1] = 0.5f; w.pathLevel[2] = 0.5f
        // elevated yellow on the right, floating star block on the left
        blk(2f, 1.5f, 2, BC.YELLOW); blk(2f, 1.5f, 3, BC.YELLOW)
        blk(-2f, 1.0f, 1, BC.CYAN, BT.BOOST)
        lane(-1, 3, 1.5f, 'R'); lane(0, 3, 1.0f, 'G'); lane(1, 3, 1.0f, 'Y')
        lane(-1, 4, 1.5f, 'Y'); lane(0, 4, 1.5f, 'G'); lane(1, 4, 1.0f, 'R')
        lane(-1, 5, 2.0f, 'B'); lane(0, 5, 2.0f, 'R'); lane(1, 5, 2.0f, 'R')
        w.pathLevel[3] = 1f; w.pathLevel[4] = 1.5f; w.pathLevel[5] = 2f
        for (z in 4..6) blk(2f, 1.0f, z, BC.BRICK, BT.BRICK)
        mystery(3f, 3.0f, 5, Reward.COINS, BC.GREEN)
        lane(-1, 6, 2.5f, 'Y'); lane(0, 6, 2.5f, 'B'); lane(1, 6, 3.0f, 'G')
        blk(-2f, 3.5f, 6, BC.BLUE, BT.TARGET); blk(-2f, 2.5f, 6, BC.PURPLE)
        lane(-1, 7, 3.0f, 'G'); lane(0, 7, 3.0f, 'Y'); lane(1, 7, 3.0f, 'R')
        // ledge where the star boost lands
        blk(-2f, 2.5f, 7, BC.BRICK, BT.BRICK); blk(-2f, 2.5f, 8, BC.BRICK, BT.BRICK); blk(-3f, 2.5f, 8, BC.BRICK, BT.BRICK); blk(-2f, 3.5f, 9, BC.YELLOW)
        lane(-1, 8, 3.5f, 'Y'); lane(0, 8, 3.5f, 'G'); lane(1, 8, 4.0f, 'B')
        lane(-1, 9, 4.0f, 'P'); lane(0, 9, 4.0f, 'G'); lane(1, 9, 4.5f, 'R')
        lane(-1, 10, 4.5f, 'R'); lane(0, 10, 4.5f, 'B'); lane(1, 10, 4.5f, 'Y')
        for (z in 6..10) w.pathLevel[z] = 2.5f + (z - 6) * 0.5f
        mystery(1f, 7.0f, 10, Reward.GEMS, BC.RED)
        blk(-3f, 6.5f, 11, BC.RED); blk(2f, 5.5f, 11, BC.GREEN)
        row(11, 5.5f, "GYP")
        row(12, 6.0f, "YRG")
        row(13, 6.5f, "RGY")
        raise(-1, 11, 0.5f); raise(1, 12, 0.5f)
        coin(-1f, 1.0f, 1); coin(-1f, 1.0f, 2)
        coin(0f, 1.5f, 4)
        coin(-2.6f, 2.6f, 6)
        coin(0f, 4.0f, 9); coin(0f, 5.5f, 11); coin(0f, 6f, 12)
        coin(1f, 6.5f, 13)
        // coins along the boost arc
        coin(-2f, 2.6f, 3); coin(-2f, 3.2f, 4); coin(-2f, 2.9f, 8)
        trig(3, Ev.HINT_TARGET)

        lo = 3f
        // ================= SECTION 1 : EASY INTRODUCTION =================
        // flat, wide and forgiving: first jump, first blue blocks, a hidden side ledge
        section("intro", 14, 33)
        row(14, 4, "RYG"); row(15, 4, "RBG"); row(16, 4, "RYG")
        trig(15, Ev.HINT_JUMP)
        // first gap: a coin arc shows the jump, a golden save block waits below as a safety net
        save(0f, 1, 17)
        coin(0f, 5f, 17); coin(0f, 5.3f, 18)
        row(19, 4, "RYG"); row(20, 4, "RYG"); row(21, 4, "RBG"); row(22, 4, "RYG"); row(23, 4, "RYG")
        coinLine(0f, 4f, 19, 20); coinLine(0f, 4f, 22, 23)
        // hidden treasure ledge one block up on the left: gems for the curious
        row(20, 5, "PP", -3, path = false); row(21, 5, "PP", -3, path = false); row(22, 5, ".P", -3, path = false)
        mystery(-3f, 5f, 22, Reward.GEMS, BC.GREEN)
        pillar(-3, 5, 20, 2); pillar(-3, 5, 21, 2)
        coin(-2f, 5f, 20); coin(-2f, 5f, 21)
        // half-step climb to a platform with a ? block to bump from below
        row(24, 4.5f, "YRY"); row(25, 5, "GRG"); row(26, 5, "GBG"); row(27, 5, "GRG")
        mystery(0f, 7.6f, 26, Reward.COINS, BC.GREEN)
        pillar(-1, 5, 27, 2); pillar(1, 5, 27, 2)
        // one-row hop
        row(29, 5, "YGY"); row(30, 5, "YGY"); row(31, 5, "BGY"); row(32, 5, "YGY"); row(33, 5, "YGY")
        coinLine(0f, 5f, 29, 33)

        // ================= SECTION 2 : MOVING BLOCKS =================
        // safe route: slow, wide sliding platforms in the middle
        // risky route: single blue pillars on the right (three targets, precise jumps)
        // hidden route: a bridge on the far left that only appears when you walk the left lane
        section("moving", 34, 60)
        trig(34, Ev.ZONE, "ZONE 2", "SLIDING STEPS")
        row(34, 5, "GYG"); row(35, 5, "GYG"); row(36, 5, "GYG")
        mystery(1f, 7.6f, 35, Reward.TOOL_BLOCK, BC.GREEN)
        trig(35, Ev.HINT_TOOLS)
        moving(0f, 5, 38, 0.65f, 1.2f, 0f, 1.6f, BC.GREEN, 2f)
        moving(0f, 5, 42, 0.65f, 1.3f, 2.2f, 1.6f, BC.YELLOW, 2f)
        for (z in intArrayOf(38, 41, 44)) blk(3f, 5f, z, BC.BLUE, BT.TARGET)
        pillar(3, 5, 38, 3); pillar(3, 5, 41, 3); pillar(3, 5, 44, 3)
        for (z in 36..45) hidden(-2f, 5, z, if (z % 2 == 0) BC.GREEN else BC.YELLOW, 1.6f)
        coinLine(-2f, 5f, 38, 44)
        coin(0f, 5.6f, 37); coin(0f, 5.8f, 41); coin(0f, 5.6f, 45)
        save(0f, 2, 40); save(0f, 2, 45)
        row(46, 5, "GYG"); row(47, 5, "GBG"); row(48, 5, "GYG")
        // ferry crossing: two long platforms sliding in opposite directions
        moving(0f, 5, 50, 1.8f, 1.5f, 0f, 3f, BC.RED)
        moving(0f, 5, 52, 1.8f, 1.5f, 3.14f, 3f, BC.YELLOW)
        save(0f, 2, 51)
        coin(0f, 5f, 50); coin(0f, 5f, 52)
        row(54, 5, "RYR"); row(55, 5, "RYR"); row(56, 5, "RBR")
        checkpointRow(57, 5)
        row(58, 5, "RYR"); row(59, 5, "RYR"); row(60, 5, "RYR")

        // ================= SECTION 3 : FALLING & CRACKED BLOCKS =================
        section("crumble", 61, 93)
        trig(61, Ev.ZONE, "ZONE 3", "CRUMBLING BRIDGE")
        // cracked bridge: fine to run over, breaks if you stand still
        row(61, 5, "ccc"); row(62, 5, "ccc"); row(63, 5, "cBc"); row(64, 5, "ccc"); row(65, 5, "ccc")
        // falling blocks drop a moment after you step on them
        row(67, 5, "fff"); row(68, 5, "fff"); row(69, 5, "fff"); row(70, 5, "fff")
        coinLine(0f, 5f, 67, 70)
        row(71, 5, "GRG"); row(72, 5, ".RG")
        // the ? block on the left opens a secret golden bridge over the next gap
        mystery(-1f, 5f, 72, Reward.SHORTCUT, BC.GREEN)
        for (z in 73..79) { val b = blk(0f, 5f, z, BC.GOLD, BT.APPEAR); b.shortcutId = 1 }
        // otherwise: vanishing stepping stones
        blk(-1f, 5f, 74, BC.PURPLE, BT.DISAPPEAR); blk(1f, 5f, 76, BC.PURPLE, BT.DISAPPEAR); blk(-1f, 5f, 78, BC.PURPLE, BT.DISAPPEAR)
        coin(-1f, 5f, 74); coin(1f, 5f, 76); coin(-1f, 5f, 78)
        save(0f, 2, 76)
        row(80, 5, "GRG"); row(81, 5, "BRG"); row(82, 5, "G*G")
        trig(80, Ev.HINT_BLOCK)
        // the difficult jump: three rows wide. The star block launches you over it.
        save(0f, 2, 84)
        coin(0f, 6f, 83); coin(0f, 6.6f, 84); coin(0f, 6f, 85)
        row(86, 5, "RGR"); row(87, 5, "RGR"); row(88, 5, "RGR"); row(89, 5, "RGR")
        pillar(-1, 5, 86, 2); pillar(1, 5, 86, 2)
        // broken staircase: half steps with cracked treads in the middle
        row(90, 5.5f, "YcY"); row(91, 6f, "YcB"); row(92, 6.5f, "YcY"); row(93, 7f, "GYG")

        // ================= SECTION 4 : TRAP GAUNTLET =================
        section("traps", 94, 127)
        row(94, 7, "GYG"); row(95, 7, "GYG"); row(96, 7, "GYG")
        trig(95, Ev.TRAPS)
        // spike rows: wait for the spikes to drop, or jump over them
        row(97, 7, "ttt"); row(98, 7, "PYP"); row(99, 7, "PYP"); row(100, 7, "ttt"); row(101, 7, "PYP"); row(102, 7, "tYt"); row(103, 7, "PYP")
        coin(0f, 7.9f, 97); coin(0f, 7.9f, 100)
        // the fork
        row(104, 7, "GYGYG", -2)
        trig(104, Ev.FORK)
        // left: safe path with coins, and a bounce block up to a hidden heart
        for (z in 105..115) row(z, 7, if (z % 2 == 0) "GY" else "YG", -3, path = false)
        for (b in w.row(109)!!) if (absf(b.x + 3f) < 0.01f) { b.type = BT.BOUNCE; b.color = BC.IRON; b.remember() }
        row(111, 10, "PP", -3, path = false); row(112, 10, ".P", -3, path = false); row(113, 10, "PP", -3, path = false)
        mystery(-3f, 10f, 112, Reward.HEART, BC.GREEN)
        coinLine(-2.5f, 7f, 105, 108); coinLine(-2.5f, 7f, 114, 115); coin(-2.5f, 10f, 111)
        // right: spikes and treasure (three blue blocks and a gem box)
        val right = arrayOf("GG", "tt", "BG", "GG", "tt", "GB", "GG", "tt", "BG", "G.", "GG")
        for ((i, s) in right.withIndex()) row(105 + i, 7, s, 2, path = false)
        mystery(3f, 7f, 114, Reward.GEMS, BC.RED)
        for (z in 105..115) w.pathLevel[z] = 7f + lo
        pillar(-3, 7, 105, 3); pillar(3, 7, 105, 3); pillar(-3, 7, 115, 3); pillar(3, 7, 115, 3)
        row(116, 7, "GYGYG", -2)
        // a trapped ? box in the middle of the path
        row(117, 7, "Y.Y"); mystery(0f, 7f, 117, Reward.TRAP, BC.RED)
        row(118, 7, "YYY")
        moving(0f, 7, 120, 1.3f, 1.9f, 0.5f, 2.2f, BC.PURPLE)
        save(0f, 4, 120)
        row(122, 7, "tYt"); row(123, 7, "GYG"); row(124, 7, "GBG"); row(125, 7, "GYG")
        checkpointRow(126, 7)
        row(127, 7, "GYG")

        // ================= SECTION 5 : TOWER GUARD CHASE =================
        // the guard appears behind you and the tower crumbles behind it; reach the next checkpoint
        section("chase", 128, 169)
        row(128, 7, "KYK"); row(129, 7, "KYK"); row(130, 7, "KYK")
        trig(129, Ev.CHASE)
        row(131, 7, "KYK"); row(132, 7, "KBK"); row(133, 7, "KYK")
        row(135, 7, "RYR"); row(136, 7, "RYR"); row(137, 7, "tYt"); row(138, 7, "RYR")
        row(141, 7, "ccc"); row(142, 7, "cBc"); row(143, 7, "ccc"); row(144, 7, "ccc")
        row(145, 8, "KRK"); row(146, 8, "KRK"); row(147, 8, "KRK"); row(148, 8, "K*K")
        save(0f, 5, 150)
        row(152, 8, "RYR"); row(153, 8, "RYR"); row(154, 8, "RBR"); row(155, 8, "RYR")
        moving(0f, 8, 157, 1.0f, 2.0f, 0f, 2.4f, BC.YELLOW)
        save(0f, 5, 157)
        row(159, 8, "KYK"); row(160, 8, "tYt"); row(161, 8, "KYK"); row(162, 8, "KYK"); row(163, 8, "KYK")
        row(164, 8, "fff"); row(165, 8, "fff"); row(166, 8, "fff")
        row(167, 8, "KYK"); row(168, 8, "KYK"); row(169, 8, "KYK")
        coinLine(0f, 7f, 128, 133); coinLine(0f, 7f, 135, 138); coinLine(0f, 7f, 141, 144)
        coinLine(0f, 8f, 152, 155); coinLine(0f, 8f, 159, 169)
        coin(0f, 9.4f, 150)
        collapsible(112, 169)

        // ================= SECTION 6 : CHECKPOINT HAVEN =================
        section("haven", 170, 177)
        for (z in 170..177) if (z != 173 && z != 175) row(z, 8, if (z % 2 == 0) "GYGYG" else "YGYGY", -2)
        row(173, 8, "GY.YG", -2); checkpoint(173, 8)
        row(175, 8, ".GYG.", -2)
        mystery(-2f, 8f, 175, Reward.HEART, BC.GREEN)
        mystery(2f, 8f, 175, Reward.TOOL_SHIELD, BC.GREEN)
        trig(172, Ev.CHASE_END)
        for (z in intArrayOf(170, 177)) { pillar(-2, 8, z, 3); pillar(2, 8, z, 3) }

        // ================= SECTION 7 : THE FINAL TOWER =================
        // lava floods the tower from below: climb
        section("tower", 178, 212)
        row(178, 8, "PRP"); row(179, 8, "PRP")
        trig(179, Ev.LAVA)
        row(180, 9, "RPR"); row(181, 9, "RPR")
        row(183, 10, "PRP"); row(184, 10, "PBP"); row(185, 10, "PRP"); row(186, 10, "PbP"); row(187, 10, "PRP")
        // the next floor is two blocks up: bounce, or climb the half-step stairs on the left
        row(186, 10, "P", -2, path = false)
        row(187, 10.5f, "P", -2, path = false); row(188, 11f, "R", -2, path = false)
        row(189, 11.5f, "P", -2, path = false); row(190, 12f, "R", -2, path = false)
        row(188, 12, "PRP"); row(189, 12, "PRP"); row(190, 12, "PRP"); row(191, 12, "PRB"); row(192, 12, "PRP")
        checkpointRow(193, 12)
        row(194, 12, "PRP")
        moving(0f, 12, 196, 1.4f, 2.2f, 1f, 2.2f, BC.PURPLE)
        save(0f, 9, 196)
        // vanishing staircase
        row(198, 12.5f, "ddd"); row(199, 13f, "ddd"); row(200, 13.5f, "ddd")
        row(201, 14, "RBR"); row(202, 14, "RPR")
        row(204, 15, "PRP"); row(205, 15, "PRP"); row(206, 15, "PRP")
        row(209, 15, "OYO"); row(210, 15, "OYO"); row(211, 15, "OBO"); row(212, 15, "OYO")
        trig(209, Ev.LAVA_STOP)
        coinLine(0f, 10f, 183, 185); coinLine(0f, 12f, 188, 192); coinLine(0f, 15f, 204, 206); coin(0f, 16.2f, 207); coin(0f, 16.2f, 208)
        coin(-2f, 11f, 188); coin(-2f, 12f, 190)
        for (z in intArrayOf(183, 188, 204, 209)) { pillar(-1, if (z < 188) 10 else if (z < 204) 12 else 15, z, 3); pillar(1, if (z < 188) 10 else if (z < 204) 12 else 15, z, 3) }

        // ================= SECTION 8 : FINAL ESCAPE — THE ANCIENT GATE =================
        section("escape", 213, 232)
        trig(213, Ev.FINAL)
        row(213, 15, "KYK"); row(214, 15, "KYK"); row(215, 15, "KYK"); row(216, 15, "KYK")
        row(218, 15, "KOK"); row(219, 15, "KOK"); row(220, 15, "KBK"); row(221, 15, "KOK")
        row(224, 15, "KYK"); row(225, 15, "KYK")
        coinLine(0f, 15f, 213, 216); coinLine(0f, 15f, 218, 221); coinLine(0f, 15f, 224, 225)
        coin(0f, 16.4f, 217); coin(0f, 16.4f, 222); coin(0f, 16.4f, 223)
        collapsible(200, 225)
        // gate plaza
        row(226, 15, "OKYKO", -2)
        row(227, 15, "YKKKY", -2)
        row(228, 15, "PKKKP", -2)
        row(229, 15, "KKKKK", -2)
        row(230, 15, "PKKKP", -2)
        row(231, 15, "KKKKK", -2)
        for (z in 228..231) { pillar(-2, 15, z, 3); pillar(2, 15, z, 3) }
        // the Ancient Gate (the gate from the Screen 4 design) stands at the far edge of the plaza
        w.portals.add(Portal(ox, 15f + lo, 231.2f))
    }
}
