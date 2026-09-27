package com.blocktower.escape.core

/** All bitmaps used by the game. Block skins are colourised at load time from grey tone maps. */
class Art(p: Platform, val theme: Int = Theme.SKY_TOWER) {
    private val volcano = theme == Theme.VOLCANO
    private val temple = theme == Theme.SKY_TEMPLE
    private val plate = volcano || temple
    val bg = p.loadImage(when { volcano -> "l5v/bg_plate.jpg"; temple -> "l6/bg_plate.jpg"; else -> "img/bg_plate.jpg" })
    /** Where the design sits inside the background plate, and the design's size (in the design's own pixels). */
    val bgArtX = if (plate) 0f else 64f
    val bgArtY = if (plate) 0f else 480f
    val artW = if (plate) 941f else 1024f
    val artH = if (plate) 1672f else 1536f

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
    /** The destination cut from the level's design: the Ancient Gate (Level 4), the volcano fortress (Level 5) or the sky temple (Level 6). */
    val gate = p.loadImage(when { volcano -> "l5v/gate.png"; temple -> "l6/gate.png"; else -> "img/gate.png" })
    val guardFace = p.loadImage("emb/guard_face.png")

    // ---- Level 5 (volcanic sky fortress) art, cut from its design by `sim l5art`; Level 6 (the sky temple) art, cut
    // from its design by design/level6/build_l6.py (the Runaway Relic and the spiked maces are the same in both)
    val guardian = when { volcano -> p.loadImage("l5v/guardian.png"); temple -> p.loadImage("l6/guardian.png"); else -> null }
    val relic = if (plate) p.loadImage("l5v/relic.png") else null
    val mace = if (plate) p.loadImage("l5v/mace.png") else null
    val banner = if (volcano) p.loadImage("l5v/banner.png") else null
    /** Level 6: the helicopter flying round the sky temple, and a strip of its waterfalls (tiles vertically). */
    val heli = if (temple) p.loadImage("l6/heli.png") else null
    val falls = if (temple) p.loadImage("l6/falls.png") else null
    /** Level 6: soft clouds for the cloud sea far below the course (tiles both ways). */
    val clouds = if (temple) p.createImage(cloudTexture(128)) else null
    /** Tall stone texture (the brick courses repeated) for the pillars and islands under the course. */
    val pillarSide: Img?
    /** The stone the pillars and islands are made of. */
    val pillarColor = if (temple) BC.SAND else BC.FORT

    /** [colour][variant] */
    val top: Array<Array<Img>>
    val side: Array<Array<Img>>
    val texSize: Int

    init {
        val topT = Array(3) { p.loadPixels("tex/top$it.png") }
        val sideT = Array(3) { p.loadPixels("tex/side$it.png") }
        val brick = p.loadPixels("tex/brick.png")
        texSize = topT[0].w
        top = Array(BC.COUNT) { c -> Array(3) { v -> p.createImage(colorize(if (BC.bricky(c)) brick else topT[v], BC.ramps[c], true)) } }
        side = Array(BC.COUNT) { c -> Array(3) { v -> p.createImage(colorize(if (BC.bricky(c)) brick else sideT[v], BC.ramps[c], false)) } }
        pillarSide = if (!plate) null else {
            val reps = 8
            val tall = IntArray(brick.w * brick.h * reps)
            for (k in 0 until reps) System.arraycopy(brick.argb, 0, tall, k * brick.w * brick.h, brick.w * brick.h)
            p.createImage(colorize(Pixels(brick.w, brick.h * reps, tall), BC.ramps[pillarColor], false))
        }
    }

    /**
     * A tileable patch of soft cloud tops (fractal value noise that wraps round): pale lavender-white billows with
     * blue sky showing between them, the colours of the Level 6 design's clouds.
     */
    private fun cloudTexture(size: Int): Pixels {
        val out = IntArray(size * size)
        val rng = Rng(66)
        val grids = Array(5) { o -> val g = 4 shl o; FloatArray(g * g) { rng.f() } }
        fun noise(x: Float, y: Float, o: Int): Float {
            val g = 4 shl o
            val fx = x * g; val fy = y * g
            val x0 = kotlin.math.floor(fx).toInt(); val y0 = kotlin.math.floor(fy).toInt()
            val ux = smooth(fx - x0); val uy = smooth(fy - y0)
            fun v(i: Int, j: Int) = grids[o][((j % g + g) % g) * g + ((i % g + g) % g)]
            val a = lerp(v(x0, y0), v(x0 + 1, y0), ux); val b = lerp(v(x0, y0 + 1), v(x0 + 1, y0 + 1), ux)
            return lerp(a, b, uy)
        }
        val sky = 0xFFA8C8F4.toInt(); val lav = 0xFFD6CCF0.toInt(); val white = 0xFFF8F6FF.toInt()
        for (y in 0 until size) for (x in 0 until size) {
            val u = x / size.toFloat(); val v = y / size.toFloat()
            var n = 0f; var amp = 0.5f
            for (o in 0 until 5) { n += noise(u, v, o) * amp; amp *= 0.5f }
            val lit = noise(u, v - 0.02f, 1) - noise(u, v + 0.02f, 1)
            val c = smooth((n - 0.36f) / 0.3f)
            var col = Col.mix(sky, lav, c)
            col = Col.mix(col, white, clamp01(c * 0.8f + lit * 2.5f))
            out[y * size + x] = col or (0xFF shl 24)
        }
        return Pixels(size, size, out)
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
