package com.blocktower.escape.core

/** All bitmaps used by the game. Block skins are colourised at load time from grey tone maps. */
class Art(p: Platform, val theme: Int = Theme.SKY_TOWER) {
    private val jungle = theme == Theme.JUNGLE_TEMPLE
    val bg = p.loadImage(if (jungle) "l5/bg_plate.jpg" else "img/bg_plate.jpg")
    /** Where the design sits inside the background plate, and the design's size. */
    val bgArtX = if (jungle) 74f else 64f
    val bgArtY = if (jungle) 500f else 480f
    val artW = if (jungle) 852f else 1024f
    val artH = if (jungle) 1846f else 1536f

    val boy = p.loadImage("img/boy.png")
    /** Boy sprite placement in the original artwork (for sizing). */
    val boyArtX = 410f; val boyArtY = 774f
    /** The same boy artwork cut into animatable parts. */
    val rig = BoyRig.build(p, p.loadPixels("img/boy.png"))

    val coin = p.loadImage("img/coin.png")
    val heartFull = p.loadImage("img/heart_full.png")
    val heartEmpty = p.loadImage("img/heart_empty.png")
    val stopwatch = p.loadImage("img/stopwatch.png")
    val coinIcon = p.loadImage("img/coin_icon.png")
    val gem = p.loadImage("img/gem.png")
    val magnet = p.loadImage("img/magnet.png")
    val shield = p.loadImage("img/shield.png")
    val lightning = p.loadImage("img/lightning.png")
    val targetCube = p.loadImage("img/target_cube.png")
    val blockTool = p.loadImage("img/block_tool.png")

    val qPlate = p.loadImage("emb/q_plate_green.png")
    val qOrange = p.loadImage("emb/q_orange.png")
    val star = p.loadImage("emb/star.png")
    val frameGlow = p.loadImage("emb/frame_glow.png")
    val saveEmblem = p.loadImage("emb/save_emblem.png")
    val runeOff = p.loadImage("emb/rune_off.png")
    val runeOn = p.loadImage("emb/rune_on.png")
    val chevrons = p.loadImage("emb/chevrons.png")
    val trapHoles = p.loadImage("emb/trap_holes.png")
    val cracks = arrayOf(p.loadImage("emb/cracks1.png"), p.loadImage("emb/cracks2.png"), p.loadImage("emb/cracks3.png"))
    val lava = p.loadImage("emb/lava.png")
    val swirl = p.loadImage("emb/portal_swirl.png")
    /** The destination cut from the level's design: the Ancient Gate (Level 23) or the temple portal (Level 5). */
    val gate = p.loadImage(if (jungle) "l5/gate.png" else "img/gate.png")

    // ---- Level 5 (jungle temple) art, cut from its design
    val guardian = if (jungle) p.loadImage("l5/guardian.png") else null
    val checkpointArch = if (jungle) p.loadImage("l5/checkpoint.png") else null
    val spikedLog = if (jungle) p.loadImage("l5/log.png") else null
    val bridgeLeft = if (jungle) p.loadImage("l5/bridge_left.png") else null
    val bridgeRight = if (jungle) p.loadImage("l5/bridge_right.png") else null
    val ropeIsland = if (jungle) p.loadImage("l5/rope_island.png") else null
    /** Gold medallion carved into temple stones (Level 5). */
    val emblem = if (jungle) p.loadImage("l5/emblem.png") else null
    val guardFace = p.loadImage("emb/guard_face.png")

    /** [colour][variant] */
    val top: Array<Array<Img>>
    val side: Array<Array<Img>>
    val texSize: Int

    init {
        val topT = Array(3) { p.loadPixels("tex/top$it.png") }
        val sideT = Array(3) { p.loadPixels("tex/side$it.png") }
        val brick = p.loadPixels("tex/brick.png")
        texSize = topT[0].w
        top = Array(BC.COUNT) { c -> Array(3) { v ->
            if (c == BC.TEMPLE && jungle) p.loadImage("l5/temple_top$v.png")
            else p.createImage(colorize(if (c == BC.BRICK || c == BC.TEMPLE) brick else topT[v], BC.ramps[c], true)) } }
        side = Array(BC.COUNT) { c -> Array(3) { v ->
            if (c == BC.TEMPLE && jungle) p.loadImage("l5/temple_side$v.png")
            else p.createImage(colorize(if (c == BC.BRICK || c == BC.TEMPLE) brick else sideT[v], BC.ramps[c], false)) } }
    }

    private fun colorize(src: Pixels, ramp: IntArray, isTop: Boolean): Pixels {
        val out = IntArray(src.w * src.h)
        val lut = IntArray(256)
        for (i in 0 until 256) {
            var t = i / 255f
            t = if (isTop) 0.08f + 0.92f * t else t * 0.97f
            lut[i] = rampAt(ramp, t)
        }
        for (i in out.indices) {
            val g = (src.argb[i] shr 8) and 255
            out[i] = lut[g]
        }
        return Pixels(src.w, src.h, out)
    }

    private fun rampAt(r: IntArray, t: Float): Int {
        val stops = floatArrayOf(0f, 0.35f, 0.6f, 0.85f, 1f)
        val x = clamp01(t)
        for (k in 0 until 4) {
            if (x <= stops[k + 1]) {
                val u = (x - stops[k]) / (stops[k + 1] - stops[k])
                return Col.mix(r[k], r[k + 1], u) or (0xFF shl 24)
            }
        }
        return r[4]
    }
}
