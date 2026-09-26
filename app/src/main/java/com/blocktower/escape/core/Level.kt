package com.blocktower.escape.core

/**
 * Level 23 course. Rows run along +z; "lvl" is the walking-surface height (block top).
 * The first rows reproduce the composition of the Screen 4 artwork.
 *
 * Row string legend (lane x starts at x0, default centred):
 *  R G Y P O  normal coloured blocks      K brick        B blue TARGET block
 *  c cracked    d disappearing   f falling   b bouncing   s colour-shifting
 *  t spike trap * boost (star)   S save block   a appears on approach   . empty
 */
object Level {
    fun build(): World {
        val w = World()
        w.rowMin = -8; w.rowMax = 240
        w.initRows()
        LevelWriter(w).write()
        return w
    }
}

private class LevelWriter(val w: World) {
    var ox = 0f
    private val rng = Rng(23)

    fun blk(x: Float, lvl: Float, z: Int, c: Int, t: BT = BT.NORMAL): Block {
        val b = Block(ox + x, lvl - 1f, z.toFloat(), c, t)
        b.variant = (hash3((ox + x).toInt(), lvl.toInt(), z) ushr 3) % 3
        if (b.variant < 0) b.variant += 3
        when (t) {
            BT.TARGET -> w.targetTotal++
            BT.DISAPPEAR -> b.alpha = 1f
            BT.COLORSHIFT -> { b.phase = rng.f(0f, 5f) }
            BT.TRAP -> { b.phase = rng.f(0f, 2.4f) }
            BT.APPEAR -> { b.rise = 1f; b.visible = false }
            else -> {}
        }
        b.remember()
        w.add(b)
        return b
    }

    fun row(z: Int, lvl: Int, s: String, x0: Int = -(s.length - 1) / 2, path: Boolean = true) {
        for (i in s.indices) {
            val ch = s[i]
            val x = (x0 + i).toFloat()
            val l = lvl.toFloat()
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
                'f' -> blk(x, l, z, BC.ORANGE, BT.FALLING)
                'b' -> blk(x, l, z, BC.MAGENTA, BT.BOUNCE)
                's' -> blk(x, l, z, BC.RED, BT.COLORSHIFT)
                't' -> blk(x, l, z, BC.STONE, BT.TRAP)
                '*' -> blk(x, l, z, BC.CYAN, BT.BOOST)
                'S' -> blk(x, l, z, BC.GOLD, BT.SAVE)
                'a' -> blk(x, l, z, pick(), BT.APPEAR)
                '.' -> {}
            }
        }
        if (path) { w.pathLevel[z] = lvl.toFloat(); w.pathX[z] = ox }
    }

    private val palette = intArrayOf(BC.RED, BC.GREEN, BC.YELLOW, BC.PURPLE, BC.RED, BC.GREEN, BC.YELLOW)
    fun pick() = palette[rng.i(palette.size)]

    fun pillar(x: Int, topLvl: Int, z: Int, n: Int) {
        for (k in 1..n) blk(x.toFloat(), (topLvl - k).toFloat(), z, BC.BRICK, BT.BRICK)
    }

    fun coin(x: Float, lvl: Float, z: Int, dz: Float = 0.5f) {
        w.coins.add(Coin(ox + x, lvl + 0.85f, z + dz)); w.coinTotal++
    }
    fun coinLine(x: Float, lvl: Float, z0: Int, z1: Int) { for (z in z0..z1) coin(x, lvl, z) }

    fun mystery(x: Float, topLvl: Float, z: Int, reward: Int, color: Int = BC.GREEN): Block {
        val b = blk(x, topLvl, z, color, BT.MYSTERY)
        b.reward = reward
        w.mysteryTotal++
        return b
    }

    fun moving(x: Float, lvl: Int, z: Int, amp: Float, speed: Float, phase: Float, width: Float = 2f, c: Int = BC.GREEN): Block {
        val b = blk(x, lvl.toFloat(), z, c, BT.MOVING)
        b.sx = width; b.baseX = ox + x; b.amp = amp; b.speed = speed; b.phase = phase; b.lastX = b.x
        return b
    }

    fun checkpoint(z: Int, lvl: Int, x: Float = 0f) {
        val id = w.checkpoints.size
        val b = blk(x, lvl.toFloat(), z, BC.STONE, BT.CHECKPOINT)
        b.checkpointId = id
        w.checkpoints.add(Checkpoint(id, ox + x, lvl.toFloat(), z + 0.5f))
    }

    fun trig(z: Int, ev: Int) { w.triggers.add(Trigger(z.toFloat(), ox - 8f, ox + 8f, ev)) }

    fun section(name: String, z0: Int, z1: Int) { w.sections.add(Section(name, z0, z1, ox)) }

    fun write() {
        // ================= SECTION 0 : START — composition of the artwork =================
        ox = 0f
        section("start", -8, 13)
        w.spawnX = 0f; w.spawnY = 0f; w.spawnZ = 0.45f
        // the start pad counts as checkpoint 0 (recovery point before checkpoint A)
        w.checkpoints.add(Checkpoint(0, 0f, 0f, 0.45f).also { it.active = true })
        row(-6, -1, "YBRR", -1)
        row(-5, -1, "GYRR", -1)
        row(-4, -1, "RGYR", -1)
        row(-3, -1, "YBRR", -1)
        row(-2, -1, "YBRR", -1)
        row(-1, -1, "YBRR", -1)
        pillar(-2, -1, -1, 1); pillar(-2, -1, -2, 1); pillar(-3, -2, -1, 1)
        row(0, 0, "GBY")
        blk(-2f, -1f, 0, BC.BRICK, BT.BRICK); blk(2f, -1f, 0, BC.RED)
        row(1, 0, "RRG")
        blk(-2f, -1f, 1, BC.BRICK, BT.BRICK)
        row(2, 0, "RRG")
        blk(2f, 1f, 2, BC.YELLOW); blk(2f, 1f, 3, BC.YELLOW)
        // floating star (boost) block on the left, as in the artwork
        blk(-3f, 1f, 1, BC.CYAN, BT.BOOST)
        row(3, 1, "RGB")
        row(4, 1, "BGR")
        row(5, 1, "BRR")
        for (z in 4..6) blk(2f, 0f, z, BC.BRICK, BT.BRICK)
        mystery(3f, 2f, 5, Reward.COINS, BC.GREEN)
        row(6, 2, "BYG")
        row(7, 2, "GYB")
        row(8, 2, "GRR")
        // ledge where the boost lands
        row(8, 2, "KK", -4, path = false); row(9, 2, "KY", -4, path = false); row(10, 3, "GR", -4, path = false)
        row(9, 3, "YBG")
        row(10, 3, "PGR")
        row(11, 3, "RGB")
        mystery(1f, 5.6f, 10, Reward.GEMS, BC.RED)
        blk(-3f, 4f, 12, BC.RED)
        row(12, 4, "GRY")
        row(13, 4, "YPR")
        coin(-1f, 0f, 1); coin(-1f, 0f, 2)
        coin(0f, 1f, 4)
        coin(-2.4f, 2.3f, 6)
        coinLine(0f, 3f, 9, 11)
        coin(1f, 4f, 13)
        // coins along the boost arc
        coin(-3f, 3.2f, 3); coin(-3f, 4.0f, 5); coin(-3f, 3.8f, 7)
        trig(1, Ev.HINT_TARGET)

        // ================= SECTION 1 : JUMPS & CRUMBLING BLOCKS =================
        section("jumps", 14, 39)
        row(14, 4, "YRG")
        row(15, 4, "GBR")
        row(16, 4, "RYB")
        trig(15, Ev.HINT_JUMP)
        // gap rows 17-18, with a rare save block far below
        blk(0f, 1f, 17, BC.GOLD, BT.SAVE)
        coin(0f, 5f, 17); coin(0f, 5.2f, 18)
        row(19, 4, "GcR")
        row(20, 4, "RcY")
        row(21, 4, "YGR")
        row(22, 4, "GbY")
        row(23, 5, "..R")
        row(24, 6, "ddd")
        row(25, 6, "ddd")
        coinLine(0f, 6f, 24, 25)
        row(26, 6, "RGY")
        mystery(0f, 8.6f, 26, Reward.TOOL_BLOCK, BC.GREEN)
        row(27, 6, "BRG")
        row(28, 6, "fff")
        row(29, 6, "fff")
        // row 30 gap
        row(31, 6, "RGY")
        row(32, 6, "YBR")
        row(33, 6, "GRB")
        row(34, 6, "RYs")
        row(35, 6, "GRY")
        coinLine(0f, 6f, 31, 35)
        row(36, 7, "YGR")
        row(37, 7, "RPG")
        row(38, 7, "K.K"); checkpoint(38, 7)
        row(39, 7, "GYR")
        pillar(-1, 7, 38, 2); pillar(1, 7, 38, 2)

        // ================= SECTION 2 : MOVING / APPEARING / COLOUR-SHIFT =================
        section("moving", 40, 66)
        row(40, 7, "GYR")
        row(41, 7, "RBG")
        trig(41, Ev.HINT_TOOLS)
        // gap 42..49 : sliding platforms, and a hidden bridge on the left that appears on approach
        moving(0f, 7, 43, 1.4f, 1.5f, 0f)
        moving(0f, 7, 45, 1.4f, 1.8f, 2.2f, c = BC.YELLOW)
        moving(0f, 7, 47, 1.4f, 1.6f, 4.1f, c = BC.RED)
        for (z in 42..49) blk(-3f, 7f, z, pick(), BT.APPEAR)
        coin(0f, 7f, 43); coin(0f, 7f, 45); coin(0f, 7f, 47)
        blk(0f, 4f, 44, BC.GOLD, BT.SAVE); blk(0f, 4f, 48, BC.GOLD, BT.SAVE)
        row(50, 7, "RGY")
        row(51, 7, "sss")
        row(52, 7, "YBR")
        row(53, 7, "GRP")
        row(54, 7, "GY.")
        mystery(1f, 7f, 54, Reward.TRAP, BC.RED)
        row(55, 8, "RGY")
        row(56, 8, ".RG")
        mystery(-1f, 8f, 56, Reward.SHORTCUT, BC.GREEN)
        row(57, 9, "GYR")
        row(58, 9, "RBY")
        // gap 59..62: stepping stones or the shortcut bridge
        blk(1f, 9f, 60, BC.PURPLE, BT.DISAPPEAR)
        blk(-1f, 9f, 61, BC.YELLOW)
        for (z in 59..62) { val b = blk(0f, 9f, z, BC.GOLD, BT.APPEAR); b.shortcutId = 1; b.color = BC.GOLD }
        row(63, 9, "YGR")
        row(64, 9, "GBG")
        row(65, 9, "PPP")
        row(66, 9, "RYG")
        pillar(-1, 9, 65, 2); pillar(1, 9, 65, 2)
        w.portals.add(Portal(0f, 9f, 65.5f, false, 40f, 10f, 70.5f))

        // ================= SECTION 3 : STORM (another tower section) =================
        ox = 40f
        section("storm", 67, 98)
        row(70, 10, "GYR")
        row(71, 10, "RBG")
        row(72, 10, "YGR")
        trig(73, Ev.STORM)
        row(73, 10, "RG", 0)
        row(74, 10, "YB", 0)
        row(75, 10, "GR", 0)
        blk(0f, 8f, 76, BC.GOLD, BT.SAVE)
        row(77, 11, "GR", -1)
        row(78, 11, "BY", -1)
        row(79, 11, "cR", -1)
        row(80, 11, "YG", -1)
        row(81, 11, "RY", 0)
        row(82, 11, ".G", 0)
        mystery(0f, 11f, 82, Reward.HEART, BC.GREEN)
        row(83, 11, "GB", 0)
        blk(1f, 8f, 84, BC.GOLD, BT.SAVE)
        row(85, 12, "Y", 0)
        row(86, 12, "RY", -1)
        row(87, 12, "GR", 0)
        row(88, 12, "BG", -1)
        row(89, 12, "R", 0)
        row(90, 12, "GcY")
        row(91, 12, "RYG")
        row(92, 12, "YcR")
        row(93, 12, "GRY")
        row(94, 12, "RGB")
        coinLine(0.5f, 10f, 73, 75); coinLine(-0.5f, 11f, 77, 80); coinLine(0f, 12f, 85, 89)
        trig(95, Ev.STORM_END)
        row(95, 12, "YGR")
        row(96, 12, "GRY")
        row(97, 12, "K.K"); checkpoint(97, 12)
        row(98, 12, "RYG")
        pillar(-1, 12, 97, 2); pillar(1, 12, 97, 2)

        // ================= SECTION 4 : GUARD CHASE =================
        section("chase", 99, 136)
        row(99, 12, "GYR")
        row(100, 12, "RGY")
        row(101, 12, "YRG")
        trig(101, Ev.CHASE)
        row(102, 12, "GBR")
        row(103, 12, "tGt")
        row(104, 13, "RYG")
        row(105, 13, "YGB")
        row(106, 13, "GtR")
        row(107, 13, "RYG")
        row(108, 13, "GRYGR", -2)
        trig(108, Ev.HINT_FORK)
        // fork: left = bait (targets + traps + dead-end gap), right = safe path with coins
        for (z in 109..117) {
            val s = when (z) { 111 -> "BR"; 113 -> "tt"; 114 -> "YB"; 116 -> "tG"; else -> if (z % 2 == 0) "GY" else "RG" }
            row(z, 13, s, -3, path = false)
        }
        row(121, 13, "RG", -3, path = false)
        for (z in 109..121) {
            val s = if (z % 3 == 0) "YR" else if (z % 3 == 1) "GY" else "RG"
            row(z, 13, s, 2)
        }
        coinLine(2.5f, 13f, 109, 121)
        row(122, 13, "RGYGR", -2)
        row(123, 13, "YBR")
        row(124, 13, "sGs")
        row(125, 13, "GYR")
        row(126, 14, "RGY")
        row(127, 14, "YBG")
        row(128, 14, "fff")
        row(129, 14, "GRY")
        row(130, 15, "RYG")
        row(131, 15, "GBR")
        row(132, 15, "YtG")
        row(133, 15, "RGY")
        row(134, 15, "GYR")
        row(135, 15, "K.K"); checkpoint(135, 15)
        trig(135, Ev.CHASE_END)
        row(136, 15, "YRG")
        pillar(-1, 15, 135, 2); pillar(1, 15, 135, 2)
        coinLine(0f, 13f, 123, 125); coinLine(0f, 14f, 126, 129); coinLine(0f, 15f, 130, 134)

        // ================= SECTION 5 : DRAGON ATTACK =================
        section("dragon", 137, 158)
        row(137, 15, "GYR")
        row(138, 15, "RGY")
        row(139, 15, "YRG")
        mystery(1f, 17.6f, 139, Reward.TOOL_BLOCK, BC.RED)
        row(140, 15, "GYR")
        trig(141, Ev.DRAGON)
        trig(140, Ev.HINT_BLOCK)
        for (z in 141..157) {
            val s = when (z) { 143 -> "RBG"; 149 -> "BRY"; 153 -> "GYB"; else -> if (z % 3 == 0) "YGR" else if (z % 3 == 1) "RYG" else "GRY" }
            row(z, 15, s)
        }
        for (z in 141..157) coin(0f, 15f, z)
        // blocks the dragon will burn away
        for (b in w.blocks) if (b.row in 146..147 || b.row == 151 || (b.row in 155..156 && b.x >= ox - 0.1f)) {
            if (b.row in 141..157) b.eventTag = Ev.DRAGON
        }
        row(158, 15, "K.K"); checkpoint(158, 15)
        pillar(-1, 15, 158, 2); pillar(1, 15, 158, 2)

        // ================= SECTION 6 : TOWER COLLAPSE =================
        section("collapse", 159, 178)
        trig(161, Ev.COLLAPSE)
        row(159, 15, "GYR")
        row(160, 15, "RGY")
        row(161, 15, "YRG")
        row(162, 15, "GRY")
        row(163, 15, "RYB")
        row(164, 16, "GRY")
        row(165, 16, "YGR")
        row(166, 16, "fGf")
        row(167, 16, "BRY")
        row(168, 16, "GYR")
        // row 169 gap
        row(170, 17, "RGY")
        // gap 171..173 with a sliding platform at 172
        moving(0f, 17, 172, 1.2f, 2.0f, 0f, 2f, BC.PURPLE)
        row(174, 17, "YBR")
        row(175, 17, "GRY")
        row(176, 17, "RYG")
        row(177, 17, "GYR")
        row(178, 17, "K.K"); checkpoint(178, 17)
        pillar(-1, 17, 178, 2); pillar(1, 17, 178, 2)
        for (b in w.blocks) if (b.row in 159..176) b.eventTag = Ev.COLLAPSE
        coinLine(0f, 15f, 159, 163); coinLine(0f, 16f, 164, 168); coin(0f, 17f, 172); coinLine(0f, 17f, 174, 176)

        // ================= SECTION 7 : LAVA RUSH =================
        section("lava", 179, 199)
        row(179, 17, "GYR")
        row(180, 17, "RGY")
        row(181, 17, "YRG")
        trig(181, Ev.LAVA)
        row(182, 18, "GRY")
        row(183, 18, "RBG")
        row(184, 19, "YGR")
        row(185, 19, "G.Y")
        row(186, 20, "RbG")
        row(187, 20, "YRG")
        row(188, 21, "GYB")
        row(189, 21, "R.G")
        row(190, 22, "YRG")
        row(191, 22, "GYR")
        row(192, 23, "BRG")
        row(193, 23, "RGY")
        row(194, 24, "GYR")
        row(195, 24, "RYG")
        trig(195, Ev.LAVA_STOP)
        for (z in 182..195) coin(0f, w.pathLevel[z] ?: 18f, z)

        // ================= SECTION 8 : END PORTAL =================
        section("end", 196, 208)
        row(196, 24, "RGYGR", -2)
        row(197, 24, "YRGRY", -2)
        row(198, 24, "GYRYG", -2)
        row(199, 24, "PKKKP", -2)
        row(200, 24, "KKKKK", -2)
        row(201, 24, "PKKKP", -2)
        row(202, 24, "KKKKK", -2)
        for (z in 199..202) { pillar(-2, 24, z, 3); pillar(2, 24, z, 3) }
        w.portals.add(Portal(0f + ox, 24f, 201.5f, true))
        // decorative arch of the end portal
        for (k in 0..3) {
            val l = blk(-2f, 25f + k, 201, BC.BRICK, BT.BRICK); l.decor = false
            val r = blk(2f, 25f + k, 201, BC.BRICK, BT.BRICK); r.decor = false
        }
        for (x in -2..2) blk(x.toFloat(), 29f, 201, if (x == 0) BC.PURPLE else BC.BRICK, if (x == 0) BT.NORMAL else BT.BRICK)
    }
}
