package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Geometry of menu/map.jpg, the Select Level artwork (the approved Select Level reference): a path of big
 * coloured blocks climbing from the grassy start toward the castle and the glowing portal. The approved artwork is
 * the core (x in [CORE_L, CORE_L + CORE_W]); the strips at the sides are extra scenery so wider screens (16:9,
 * foldables, tablets) can show the whole path without bars. Node coordinates are core-relative.
 */
object MapPlate {
    const val W = 707f
    const val H = 1253f
    const val CORE_L = 112f
    const val CORE_W = 483f
    const val PORTAL_X = 405f
    const val PORTAL_Y = 445f

    /**
     * A painted level block: box (l, t, r, b), scale (block width / level-1 width), number centre, stars row,
     * padlock (on the top face), where the boy stands, and the label anchor with its alignment
     * (-1 to the left of the anchor, 0 centred under it, 1 to the right of it).
     */
    class Node(val l: Float, val t: Float, val r: Float, val b: Float, val s: Float,
               val numX: Float, val numY: Float, val starY: Float, val lockX: Float, val lockY: Float,
               val topX: Float, val topY: Float, val labX: Float, val labY: Float, val labAlign: Int)

    val nodes = arrayOf(
        Node(115f, 937f, 315f, 1080f, 1.00f, 215f, 1005f, 1049f, 215f, 953f, 215f, 952f, 219f, 1083f, 0),
        // levels 2-5: the boy stands on the side of the top face away from the next block up
        Node(245f, 713f, 390f, 817f, 0.725f, 316f, 772f, 800f, 313f, 731f, 352f, 725f, 318f, 832f, 0),
        Node(213f, 638f, 330f, 723f, 0.585f, 272f, 681f, 705f, 272f, 650f, 300f, 646f, 207f, 688f, -1),
        Node(145f, 575f, 247f, 653f, 0.51f, 196f, 611f, 636f, 196f, 584f, 172f, 580f, 139f, 614f, -1),
        Node(181f, 510f, 270f, 577f, 0.445f, 225f, 541f, 563f, 225f, 517f, 205f, 514f, 277f, 546f, 1),
        // level 6: the dark block up the path from level 5
        Node(258f, 432f, 330f, 492f, 0.36f, 294f, 460f, 479f, 294f, 437f, 306f, 436f, 336f, 462f, 1),
        // level 7: the dark block with the flag, up and to the left of level 6
        Node(222f, 387f, 284f, 433f, 0.31f, 253f, 409f, 424f, 253f, 391f, 262f, 390f, 216f, 411f, -1),
    )
    /** The next block after the last level on the path (more levels soon), and the far blocks with padlocks. */
    val soon = floatArrayOf(308f, 377f, 368f, 428f)
    val farLocks = floatArrayOf(380f, 372f)
    /** Height of the boy (back view) standing on the level-1 block. */
    const val BOY_H = 170f
}

/**
 * The level map (Select Level; PLAY on the Home screen opens it), from the approved Select Level artwork: the block
 * path climbs toward the castle and the portal, one painted block per level, Level 1 at the bottom. Each block shows
 * its number, the stars earned and a padlock while locked; the short level names sit beside the blocks, and the
 * block after Level 5 says SOON. The same boy as on the Home screen (seen from behind) stands on the level you are
 * up to. Tap a level for its card (objective, star goals) and PLAY; a locked level shakes and says which level
 * opens it. A level that has just opened plays its unlock: the padlock shakes and pops off in a burst of confetti,
 * a ring of light, NEW!, and the boy hops across.
 *
 * The artwork always covers the screen (no bars, nothing stretched) and is placed so the whole path fits between
 * the title sign and the safe bottom: on phones the approved artwork fills the width; shorter or wider screens zoom
 * out into extra side scenery, then shrink the sign.
 */
class LevelMap(private val app: App) {
    private val mapImg = app.pf.loadImage("menu/map.jpg")
    private val signImg = app.pf.loadImage("menu/sign.png")
    private val boyBack = app.pf.loadImage("menu/boy_back.png")
    private val walletImg = app.pf.loadImage("home/hud_wallet.png")

    private var pressedNode = -1
    private var shakeNode = 0; private var shakeT = 0f
    private var newT = 0f
    /** The unlock animation of a newly opened level: seconds since the map finished fading in. */
    private var unlockAge = -1f
    private var unlockBurst = false
    /** "Finish Level N to unlock" after tapping a locked level. */
    private var lockedMsg = ""
    private var lockedMsgT = 0f
    private var lockedNode = 0

    // ---- layout (screen pixels)
    private var laidOut = -1
    private var k = 1f; private var x0 = 0f; private var y0 = 0f
    /** Tight fit: the sign may cover the top face of block 5, so its padlock moves beside the number. */
    private var compact = false
    private var backCX = 0f; private var backCY = 0f; private var backR = 0f
    private var walletK = 1f; private var walletL = 0f; private var walletT = 0f
    private var signW = 0f; private var signTop = 0f; private var signCX = 0f

    private fun mx(x: Float) = x0 + (MapPlate.CORE_L + x) * k
    private fun my(y: Float) = y0 + y * k

    private fun layout() {
        val m = app.safe
        if (laidOut == m.version) return
        laidOut = m.version
        val u = m.u; val sw = m.sw
        // ---- header: Back on the left, the coin / gem wallet (the Home screen's) on the right
        val backD = max(m.dp(50f), 54f * u)
        backR = backD / 2; backCX = m.l + backR; backCY = m.t + backR
        val hMax = max(m.dp(40f), 46f * u)
        walletK = min((sw - backD - 12f * u) / 466f, hMax / 78f)
        walletL = m.r - 466f * walletK
        walletT = backCY - 39f * walletK
        val headerBottom = max(m.t + backD, walletT + 78f * walletK)

        // ---- the title sign under the header
        val signAspect = signImg.h.toFloat() / signImg.w
        signTop = headerBottom + 2f * u; signCX = m.cx
        val wMax = min(sw * 0.8f, 420f * u)
        val wMin = min(wMax, m.dp(170f))
        // only when nothing else fits (a near-square screen, now that the path climbs to Level 7): a smaller sign
        val wTiny = min(wMin, m.dp(104f))

        // ---- where the artwork goes: the path from level 5 to level 1's label between the sign and the safe bottom.
        // Preferred scale: the approved artwork fills the width (phones). Otherwise zoom out (showing the side
        // scenery) down to the smallest scale that still covers the screen, then shrink the sign; only as a last
        // resort does the sign cover the top of block 5 (its number stays visible).
        val n1 = MapPlate.nodes[0]; val n5 = MapPlate.nodes[Levels.count - 1]
        val bottomNeed = n1.labY + 13f
        val kCover = max(m.w / MapPlate.W, m.h / MapPlate.H)
        val kWant = max(m.w / MapPlate.CORE_W, m.h / MapPlate.H)
        var chosen = -1f
        var yLo = 0f; var yHi = 0f
        compact = false
        search@ for (pass in 0..2) {
            // 0: the whole path under the sign; 1: the same with a smaller sign; 2: the sign may cover the top block's top face
            val topNeed = if (pass < 2) n5.t else n5.numY - 16f
            val wLow = if (pass == 1) wTiny else wMin
            k = kWant
            while (true) {
                val lo0 = m.h - MapPlate.H * k
                var w = wMax
                while (w >= wLow - 0.5f) {
                    val signBottom = signTop + w * signAspect * 0.92f
                    yLo = max(lo0, signBottom + 4f * u - topNeed * k)
                    yHi = min(0f, m.b - bottomNeed * k)
                    if (yLo <= yHi) { chosen = w; compact = pass == 2; break@search }
                    w -= sw * 0.02f
                }
                if (k <= kCover) break
                k = max(kCover, k * 0.97f)
            }
        }
        signW = if (chosen > 0f) chosen else wMin
        if (chosen > 0f) {
            // centre the path between the sign and the safe bottom, within what fits
            val signBottom = signTop + signW * signAspect * 0.92f
            val ideal = (signBottom + m.b) * 0.5f - (n5.t + bottomNeed) * 0.5f * k
            y0 = clamp(ideal, yLo, yHi)
        } else {
            // a near-square screen (the map must be scaled to its width, and the path to Level 7 is too tall for a sign
            // under the header): the sign goes up into the header row, between Back and the wallet, and the whole
            // path fits under the header
            k = kCover
            val lo0 = m.h - MapPlate.H * k
            val gapL = backCX + backR + 6f * u; val gapR = walletL - 6f * u
            val hRow = headerBottom - m.t
            val wRow = min(gapR - gapL, hRow / (signAspect * 0.92f))
            val lo = max(lo0, headerBottom + 4f * u - n5.t * k); val hi = min(0f, m.b - bottomNeed * k)
            if (wRow >= m.dp(90f) && lo <= hi) {
                signW = wRow; signCX = (gapL + gapR) * 0.5f; signTop = m.t + (hRow - wRow * signAspect * 0.92f) * 0.5f
                y0 = clamp((headerBottom + m.b) * 0.5f - (n5.t + bottomNeed) * 0.5f * k, lo, hi)
            } else {
                compact = true
                y0 = clamp(m.b - bottomNeed * k, lo0, 0f)
            }
        }
        x0 = m.cx - (MapPlate.CORE_L + MapPlate.CORE_W * 0.5f) * k
    }

    /** Resets the map for level [n] (the one the player is up to or has just opened). */
    fun focusOn(n: Int) {
        if (app.justUnlocked > 0) { newT = 4.5f; unlockAge = 0f; unlockBurst = false }
        lockedMsgT = 0f
    }

    fun update(dt: Float) {
        if (shakeT > 0f) shakeT -= dt
        if (lockedMsgT > 0f) lockedMsgT -= dt
        // the unlock plays once the map is fully on screen
        if (unlockAge >= 0f && !app.fading) {
            unlockAge += dt
            val n = app.justUnlocked
            if (!unlockBurst && unlockAge >= UNLOCK_POP && n in 1..Levels.count) {
                unlockBurst = true
                val c = nodeCentre(n)
                app.burst(c[0], c[1], 36, 0xFFFFE070.toInt(), confetti = true)
                app.pf.sound(Sfx.CRUMBLE, 0.5f, 1.4f); app.pf.sound(Sfx.STAR, 0.9f); app.pf.haptic(false)
            }
            if (unlockAge > 6f) unlockAge = -1f
        }
        if (newT > 0f && (unlockAge < 0f || unlockAge > UNLOCK_POP)) { newT -= dt; if (newT <= 0f) app.justUnlocked = 0 }
    }

    // ------------------------------------------------------------------ drawing
    fun render(gr: Gfx, interactive: Boolean) {
        layout()
        val m = app.safe
        val t = app.t
        gr.fillRect(0f, 0f, m.w, m.h, 0xFF2A78D8.toInt())
        gr.image(mapImg, x0, y0, MapPlate.W * k, MapPlate.H * k)
        Scenery.portal(gr, app.menuArt, mx(MapPlate.PORTAL_X), my(MapPlate.PORTAL_Y), 24f * k, t)
        // a soft foreground shade so the bottom edge reads as depth, not an edge
        gr.fillRectGradient(0f, m.h * 0.92f, m.w, m.h, 0x00000000, 0x55061410)

        val pr = app.progress
        val current = app.currentLevel()
        // the far blocks (locked) and the next block after the last level (more levels soon)
        val f = MapPlate.farLocks
        var i = 0
        while (i < f.size) { app.ui.padlock(gr, mx(f[i]), my(f[i + 1]), 7f * k); i += 2 }
        soonTag(gr)
        if (interactive) {
            val s = MapPlate.soon
            hit(SOON_ID, mx(s[0]), my(s[1]), mx(s[2]), my(s[3]))
            // far to near, so the nearer block wins where the painted blocks overlap
            for (n in Levels.count downTo 1) {
                val nd = MapPlate.nodes[n - 1]
                hit(100 + n, mx(nd.l), my(nd.t), mx(nd.r), my(nd.b))
            }
        }
        // levels above the boy, the boy, then the levels at and below him
        val boyNode = boyNodeIndex(current)
        for (n in Levels.count downTo 1) {
            if (n == boyNode) drawBoy(gr, current, t)
            drawNode(gr, n, current, t)
        }
        // labels last, so the boy never hides one
        for (n in Levels.count downTo 1) drawLabel(gr, n, current, t)
        drawHeader(gr, interactive)
    }

    /** Hit area of at least 48dp around a rectangle. */
    private fun hit(id: Int, l: Float, t: Float, r: Float, b: Float) {
        val min = app.safe.touch
        val ex = max(0f, (min - (r - l)) / 2); val ey = max(0f, (min - (b - t)) / 2)
        app.ui.hit(id, l - ex, t - ey, r + ex, b + ey)
    }

    /** The node the boy is drawn just before (he stands on it, in front of the blocks behind it). */
    private fun boyNodeIndex(current: Int): Int {
        val n = app.justUnlocked
        val hopping = n > 1 && unlockAge >= 0f && hopU() < 1f
        return if (hopping) n else current
    }
    private fun hopU() = clamp01((unlockAge - UNLOCK_POP - 0.25f) / 0.55f)

    private fun drawNode(gr: Gfx, n: Int, current: Int, t: Float) {
        val nd = MapPlate.nodes[n - 1]
        val pr = app.progress
        val unlocked = n <= pr.unlocked
        val unlocking = n == app.justUnlocked && unlockAge >= 0f
        val popU = if (unlocking) clamp01((unlockAge - UNLOCK_POP) / 0.6f) else 1f
        val shownUnlocked = unlocked && (!unlocking || unlockAge >= UNLOCK_POP)
        val sc = nd.s * k
        val press = if (pressedNode == n) 0.94f else 1f
        val sx = if (n == shakeNode && shakeT > 0f) sin(shakeT * 50f) * 8f * app.safe.u * shakeT / 0.4f else 0f
        val cx = mx((nd.l + nd.r) / 2); val cy = my((nd.t + nd.b) / 2)
        if (n == current && shownUnlocked) {
            gr.setAdditive(true)
            gr.glow(cx, cy, (nd.r - nd.l) * k * 0.7f, Col.withA(0xFFFFF0A0.toInt(), 0.14f + 0.1f * pulse(t, 3.5f)))
            gr.setAdditive(false)
        }
        if (unlocking && popU > 0f && popU < 1f) {
            // a ring of light spreads from the block
            gr.strokeCircle(cx, cy, (nd.r - nd.l) * k * (0.5f + 0.9f * popU), 8f * app.safe.u * (1f - popU), Col.withA(0xFFFFE070.toInt(), 1f - popU))
        }
        // number and stars
        val numSize = 50f * sc * press * (if (n == current) 1f + 0.05f * pulse(t, 3.5f) else 1f)
        Kit.text(gr, n.toString(), mx(nd.numX) + sx, my(nd.numY), numSize, if (shownUnlocked) Col.WHITE else 0xFFE4E8F4.toInt(),
            Font.TITLE, Align.CENTER, 0xFF0A1030.toInt(), numSize * 0.12f)
        val got = pr.stars[n]
        for (j in 0..2) app.ui.star(gr, mx(nd.numX + (j - 1) * 36f * nd.s) + sx, my(nd.starY), 13f * sc, j < got)
        // the padlock while locked (it shakes before an unlock, then flies off)
        val lockR = 14f * sc
        if (!shownUnlocked) {
            val shake = if (unlocking) sin(unlockAge * 40f) * 6f * app.safe.u * clamp01(unlockAge / UNLOCK_POP) else 0f
            if (compact && n == Levels.count) app.ui.padlock(gr, mx(nd.numX + 44f * nd.s) + sx + shake, my(nd.numY), lockR * 0.9f)
            else app.ui.padlock(gr, mx(nd.lockX) + sx * 1.5f + shake, my(nd.lockY) + lockR * 0.2f, lockR)
        } else if (unlocking && popU < 1f) {
            val py = my(nd.lockY) - popU * 120f * app.safe.u
            app.ui.padlock(gr, mx(nd.lockX) + popU * 30f * app.safe.u, py, lockR * (1f - popU))
        }
    }

    private fun drawBoy(gr: Gfx, current: Int, t: Float) {
        val n = app.justUnlocked
        val hopU = if (n > 1 && unlockAge >= 0f) hopU() else 1f
        val from = MapPlate.nodes[(if (hopU < 1f) n - 1 else current) - 1]
        val to = MapPlate.nodes[(if (hopU < 1f) n else current) - 1]
        val e = smooth(hopU)
        val fx = mx(lerp(from.topX, to.topX, e)); val fy = my(lerp(from.topY, to.topY, e))
        val h = MapPlate.BOY_H * lerp(from.s, to.s, e) * k
        val w = h * boyBack.w / boyBack.h
        val hop = if (hopU > 0f && hopU < 1f) sin(hopU * 3.14159f) * h * 0.5f else abs(sin(t * 3.2f)) * h * 0.025f
        gr.fillCircle(fx, fy, w * 0.26f, 0x33000000)
        gr.image(boyBack, fx - w * 0.5f, fy - h + h * 0.04f - hop, w, h)
    }

    private fun drawLabel(gr: Gfx, n: Int, current: Int, t: Float) {
        val m = app.safe
        val nd = MapPlate.nodes[n - 1]
        val pr = app.progress
        val unlocked = n <= pr.unlocked
        val unlocking = n == app.justUnlocked && unlockAge >= 0f
        val shownUnlocked = unlocked && (!unlocking || unlockAge >= UNLOCK_POP)
        val isCurrent = n == current && shownUnlocked
        val size = max(m.dp(11f), (if (isCurrent) 17f else 14f) * k * max(nd.s, 0.62f))
        val sub = if (!shownUnlocked && n == pr.unlocked + 1) "Finish Level ${n - 1}" else null
        val color = when { isCurrent -> 0xFFFFF4B0.toInt(); shownUnlocked -> Col.WHITE; else -> 0xFFB8C4E0.toInt() }
        val bg = if (isCurrent) 0xE61A3A10.toInt() else 0xD90A1438.toInt()
        val align = when (nd.labAlign) { 0 -> Align.CENTER; -1 -> Align.RIGHT; else -> Align.LEFT }
        val x = mx(nd.labX)
        val lcy = my(nd.labY) + (if (sub != null && nd.labAlign == 0) size * 0.5f else 0f)
        Kit.pill(gr, Levels.get(n).name, x, lcy, size, align, color, bg, m.u, m.l, m.r, sub)
        val cx = mx((nd.l + nd.r) / 2); val cy = my((nd.t + nd.b) / 2)
        if (n == app.justUnlocked && newT > 0f && shownUnlocked) {
            val popU = if (unlocking) clamp01((unlockAge - UNLOCK_POP) / 0.6f) else 1f
            val a = clamp01(newT / 0.5f) * clamp01(popU * 2f)
            val bob = sin(t * 6f) * 4f * m.u
            val ns = max(m.dp(16f), 30f * nd.s * k)
            Kit.text(gr, "NEW!", clamp(mx(nd.r) - ns * 0.4f, m.l + ns * 1.2f, m.r - ns * 1.2f), my(nd.t) - ns * 0.1f + bob, ns,
                0xFFFFE14A.toInt(), Font.TITLE, Align.CENTER, 0xFF1A0A20.toInt(), ns * 0.14f, true, a)
        }
        if (n == lockedNode && lockedMsgT > 0f) {
            val a = clamp01(lockedMsgT / 0.4f)
            val ms = max(m.dp(13f), 15f * m.u)
            val tw = gr.textWidth(lockedMsg, ms, Font.UI) + ms * 1.4f
            val bx = clamp(cx, m.l + tw / 2, m.r - tw / 2)
            val by = my(nd.t) - ms * 1.6f
            gr.fillRoundRect(bx - tw / 2, by - ms * 1.0f, bx + tw / 2, by + ms * 1.0f, ms, Col.withA(0xFF0A1438.toInt(), 0.94f * a))
            gr.strokeRoundRect(bx - tw / 2, by - ms * 1.0f, bx + tw / 2, by + ms * 1.0f, ms, 2f * m.u, Col.withA(0xFFFFE14A.toInt(), a))
            gr.text(lockedMsg, bx, by, ms, Font.UI, Col.withA(Col.WHITE, a), Align.CENTER)
        }
    }

    /** "SOON" on the block after the last level. */
    private fun soonTag(gr: Gfx) {
        val m = app.safe
        val s = MapPlate.soon
        val cx = mx((s[0] + s[2]) / 2); val cy = my((s[1] + s[3]) / 2 + 2f)
        val ts = max(m.dp(10f), 15f * k * 0.62f) * (if (pressedNode == SOON_ID - 100) 0.94f else 1f)
        val tw = gr.textWidth("SOON", ts, Font.TITLE)
        val ph = ts * 1.5f
        val hw = tw / 2 + ph * 0.45f
        gr.fillRoundRect(cx - hw - 2f * m.u, cy - ph / 2 - 2f * m.u, cx + hw + 2f * m.u, cy + ph / 2 + 2f * m.u, ph * 0.5f, 0xFF3A1004.toInt())
        Kit.grad(gr, cx - hw, cy - ph / 2, cx + hw, cy + ph / 2, ph * 0.5f, 0xFFFFB03A.toInt(), 0xFFE0521A.toInt())
        Kit.text(gr, "SOON", cx, cy, ts, Col.WHITE, Font.TITLE, Align.CENTER, 0xFF5A1A04.toInt(), ts * 0.1f, false)
    }

    private val q = FloatArray(8)
    private fun drawHeader(gr: Gfx, interactive: Boolean) {
        val m = app.safe
        val u = m.u
        val pr = app.progress
        // back button
        val r = backR * (if (app.ui.pressed == 1) 0.92f else 1f)
        gr.fillCircle(backCX, backCY + r * 0.1f, r + 2f * u, 0x66000000)
        gr.fillCircle(backCX, backCY, r + 2f * u, 0xFF0B2F8C.toInt())
        Kit.grad(gr, backCX - r, backCY - r, backCX + r, backCY + r, r, 0xFF55B6FF.toInt(), 0xFF1650DC.toInt())
        gr.fillCircle(backCX, backCY - r * 0.35f, r * 0.55f, 0x22FFFFFF)
        val a = r * 0.46f; val th = r * 0.17f
        gr.line(backCX - a, backCY, backCX + a * 0.95f, backCY, th * 2f, Col.WHITE)
        gr.line(backCX - a, backCY, backCX - a * 0.1f, backCY - a * 0.8f, th * 2f, Col.WHITE)
        gr.line(backCX - a, backCY, backCX - a * 0.1f, backCY + a * 0.8f, th * 2f, Col.WHITE)
        if (interactive) hit(1, backCX - backR, backCY - backR, backCX + backR, backCY + backR)
        // the wallet, as on the Home screen (its + buttons open GET MORE)
        val wk = walletK
        q[0] = walletL; q[1] = walletT; q[2] = walletL + 466f * wk; q[3] = walletT
        q[4] = walletL + 466f * wk; q[5] = walletT + 78f * wk; q[6] = walletL; q[7] = walletT + 78f * wk
        gr.imageQuad(walletImg, 0f, 0f, walletImg.w.toFloat(), walletImg.h.toFloat(), q)
        fun wx(x: Float) = walletL + (x - 452f) * wk
        fun wy(y: Float) = walletT + (y - 12f) * wk
        walletText(gr, Ui.fmt(pr.coins), wx(572f), wx(522f), wx(616f), wy(50f), wk)
        walletText(gr, Ui.fmt(pr.gems), wx(802f), wx(752f), wx(850f), wy(50f), wk)
        for ((id, x0) in listOf(2 to 616f, 3 to 850f)) {
            if (app.ui.pressed == id) gr.fillRoundRect(wx(x0 + 6f), wy(23f), wx(x0 + 58f), wy(78f), 10f * wk, 0x44000000)
            if (interactive) hit(id, wx(x0), wy(16f), wx(x0 + 62f), wy(84f))
        }
        // the title sign: SELECT LEVEL and the stars collected
        val sh = signW * signImg.h / signImg.w
        gr.image(signImg, signCX - signW / 2, signTop, signW, sh)
        val title = "SELECT LEVEL"
        val ts = Kit.fit(gr, title, sh * 0.27f, Font.TITLE, signW * 0.74f)
        Kit.text(gr, title, signCX, signTop + sh * 0.33f, ts, 0xFFFFD84A.toInt(), Font.TITLE, Align.CENTER, 0xFF5A2408.toInt(), ts * 0.12f)
        val cnt = "${pr.totalStars} / ${Levels.count * 3}"
        val cs = sh * 0.19f
        val cw = gr.textWidth(cnt, cs, Font.TITLE)
        val sr = cs * 0.62f
        val total = sr * 2f + cs * 0.4f + cw
        val sx = signCX - total / 2
        val cy = signTop + sh * 0.765f
        app.ui.star(gr, sx + sr, cy, sr, true)
        Kit.text(gr, cnt, sx + sr * 2f + cs * 0.4f, cy, cs, Col.WHITE, Font.TITLE, Align.LEFT, Kit.NAVY, cs * 0.12f, false)
    }

    private fun walletText(gr: Gfx, s: String, cx: Float, l: Float, r: Float, cy: Float, wk: Float) {
        var size = 36f * wk
        val room = r - l
        val tw = gr.textWidth(s, size, Font.TITLE)
        if (tw > room) size *= room / tw
        val half = min(gr.textWidth(s, size, Font.TITLE), room) * 0.5f
        gr.text(s, clamp(cx, l + half, r - half), cy, size, Font.TITLE, Col.WHITE, Align.CENTER, 4.5f * wk, 0xFF0A1E4A.toInt())
    }

    companion object {
        /** The newly opened level's padlock shakes this long before it pops off. */
        const val UNLOCK_POP = 0.7f
        /** Touch id of the SOON block (the level blocks are 100 + their number). */
        const val SOON_ID = 199
    }

    // ------------------------------------------------------------------ input
    fun down(x: Float, y: Float) {
        val id = app.ui.down(x, y)
        pressedNode = when { id == SOON_ID -> SOON_ID - 100; id >= 101 -> id - 100; else -> -1 }
    }

    fun move(x: Float, y: Float) {
        app.ui.move(x, y)
        if (app.ui.pressed < 0) pressedNode = -1
    }

    fun up(x: Float, y: Float) {
        pressedNode = -1
        val id = app.ui.up(x, y)
        when {
            id == 1 -> { app.pf.sound(Sfx.CLICK); app.openHome() }
            id == 2 || id == 3 -> app.openPopup(Pop.MORE)
            id == SOON_ID -> app.openPopup(Pop.SOON)
            id >= 101 -> tapLevel(id - 100)
        }
    }

    fun cancel() { pressedNode = -1 }

    private fun tapLevel(n: Int) {
        val pr = app.progress
        when {
            n > Levels.count -> app.openPopup(Pop.SOON)
            n > pr.unlocked -> {
                shakeNode = n; shakeT = 0.4f
                lockedNode = n; lockedMsgT = 2.2f
                lockedMsg = if (n == pr.unlocked + 1) "Finish Level ${n - 1} to unlock" else "Finish Levels ${pr.unlocked}-${n - 1} to unlock"
                app.pf.sound(Sfx.WRONG, 0.6f)
            }
            else -> app.openPopup(Pop.LEVEL, n)
        }
    }

    /** Tests: the message shown after tapping a locked level ("" when none). */
    fun lockedMessage() = if (lockedMsgT > 0f) lockedMsg else ""

    /** Tests: where level [n]'s block is on screen (Levels.count + 1 = the SOON block). */
    fun nodeCentre(n: Int): FloatArray {
        layout()
        if (n > Levels.count) { val s = MapPlate.soon; return floatArrayOf(mx((s[0] + s[2]) / 2), my((s[1] + s[3]) / 2)) }
        val nd = MapPlate.nodes[n - 1]
        return floatArrayOf(mx((nd.l + nd.r) / 2), my((nd.t + nd.b) / 2))
    }

    /** Tests: the header parts and every level block with its label area, [left, top, right, bottom] each. */
    fun layoutRects(): List<Pair<String, FloatArray>> {
        layout()
        val signH = signW * signImg.h / signImg.w * 0.92f
        val out = arrayListOf(
            "back" to floatArrayOf(backCX - backR, backCY - backR, backCX + backR, backCY + backR),
            "wallet" to floatArrayOf(walletL, walletT, walletL + 466f * walletK, walletT + 78f * walletK),
            "sign" to floatArrayOf(signCX - signW / 2, signTop, signCX + signW / 2, signTop + signH),
        )
        for (n in 1..Levels.count) {
            val nd = MapPlate.nodes[n - 1]
            out.add("level $n" to floatArrayOf(mx(nd.l), my(if (compact && n == Levels.count) nd.numY - 16f else nd.t), mx(nd.r),
                my(if (n == 1) nd.labY + 13f else nd.b)))
        }
        return out
    }
}
