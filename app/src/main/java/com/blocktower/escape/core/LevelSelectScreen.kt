package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen 3 - Select Level. The approved painted block path climbs toward the castle and portal;
 * numbers, stars, locks and labels are drawn live on top of the painted blocks, so the map
 * scales with the artwork. The map is placed so it always covers the screen, level 5 stays
 * clear of the title sign and level 1 (with its label) stays above the safe bottom.
 */
class LevelSelectScreen(app: App) : Screen(app) {
    private object B { const val BACK = 100; const val COINS = 101; const val GEMS = 102; const val SOON = 103 }

    private val btns = Buttons()
    // added far-to-near so the nearest block wins where hit areas overlap
    private val bSoon = btns.add(B.SOON)
    private val bNodes = Array(Levels.COUNT) { i -> btns.add(Levels.COUNT - 1 - i) }.also { it.reverse() }
    private val bBack = btns.add(B.BACK)
    private val bCoins = btns.add(B.COINS)
    private val bGems = btns.add(B.GEMS)

    private var t = 0f
    private val shake = FloatArray(Levels.COUNT)

    // layout (px)
    var k = 1f; var x0 = 0f; var y0 = 0f
    /** Tight fit: the sign may cover the top face of block 5, so its lock moves beside the number. */
    private var compact = false
    private var hudH = 0f; private var pillH = 0f; private var hudCY = 0f
    private var coinL = 0f; private var coinR = 0f; private var gemL = 0f; private var gemR = 0f
    private var signW = 0f; private var signTop = 0f
    val debugRects = ArrayList<FloatArray>()

    fun mx(x: Float) = x0 + (MapPlate.CORE_L + x) * k
    fun my(y: Float) = y0 + y * k

    override fun enter() { t = 0f; btns.cancel() }

    override fun layout() {
        val u = m.u
        val sw = m.sw
        // ---- top row: Back (left), coins + gems (right)
        val backD = max(m.dp(50f), 54f * u)
        // coins + gems need ~6.5 pill heights next to the Back button; narrow phones shrink the row
        hudH = min(max(m.dp(40f), 44f * u), (sw - backD - 24f * u) / 6.6f)
        pillH = hudH * 0.8f
        bBack.place(m.l, m.t, m.l + backD, m.t + backD, m.touch)
        hudCY = m.t + backD * 0.45f
        val gap = 8f * u
        val gemW = max(min(sw * 0.26f, 120f * u), 2.9f * hudH); val coinW = max(min(sw * 0.31f, 150f * u), 3.6f * hudH)
        gemR = m.r; gemL = gemR - gemW + hudH * 0.5f
        coinR = gemR - gemW - gap; coinL = coinR - coinW + hudH * 0.5f
        val plusS = pillH * 0.9f
        bCoins.place(coinR - plusS - 2f * u, hudCY - plusS / 2, coinR - 2f * u, hudCY + plusS / 2, m.touch)
        bGems.place(gemR - plusS - 2f * u, hudCY - plusS / 2, gemR - 2f * u, hudCY + plusS / 2, m.touch)

        // ---- title sign: below the currency row, clear of the Back button
        val signAspect = app.mapArt?.sign?.let { it.h.toFloat() / it.w } ?: 0.391f
        signTop = hudCY + hudH * 0.5f + 2f * u
        val maxByBack = 2f * (m.cx - bBack.r - 6f * u)
        val wMax = min(min(sw * 0.76f, 410f * u), maxByBack)
        val wMin = min(wMax, m.dp(170f))

        // ---- map placement solver
        // Preferred scale: the approved artwork fills the width (phones). If the path does not fit
        // between the sign and the safe bottom, zoom out (revealing the side scenery) down to the
        // smallest scale that still covers the screen, then shrink the sign, and only as a last
        // resort let the sign overlap the top of block 5 (its number stays visible).
        val n1 = MapPlate.nodes[0]; val n5 = MapPlate.nodes[Levels.COUNT - 1]
        val bottomNeed = n1.labY + 13f
        val kCover = max(m.w / MapPlate.W, m.h / MapPlate.H)
        val kWant = max(m.w / MapPlate.CORE_W, m.h / MapPlate.H)
        var chosen = -1f
        var yLo = 0f; var yHi = 0f
        compact = false
        search@ for (pass in 0..1) {
            val topNeed = if (pass == 0) n5.t else n5.numY - 16f
            k = kWant
            while (true) {
                val lo0 = m.h - MapPlate.H * k
                var w = wMax
                while (w >= wMin - 0.5f) {
                    val signBottom = signTop + w * signAspect * 0.92f
                    yLo = max(lo0, signBottom + 4f * u - topNeed * k)
                    yHi = min(0f, m.b - bottomNeed * k)
                    if (yLo <= yHi) { chosen = w; compact = pass == 1; break@search }
                    w -= sw * 0.02f
                }
                if (k <= kCover) break
                k = max(kCover, k * 0.97f)
            }
        }
        signW = if (chosen > 0f) chosen else wMin
        if (chosen > 0f) {
            // centre the path between the sign and the safe bottom, within the feasible range
            val signBottom = signTop + signW * signAspect * 0.92f
            val ideal = (signBottom + m.b) * 0.5f - (n5.t + bottomNeed) * 0.5f * k
            y0 = clamp(ideal, yLo, yHi)
        } else {
            // extremely short window: keep level 1 fully visible, still covering the screen
            k = kCover
            compact = true
            y0 = clamp(m.b - bottomNeed * k, m.h - MapPlate.H * k, 0f)
        }
        x0 = m.cx - (MapPlate.CORE_L + MapPlate.CORE_W * 0.5f) * k

        for (i in 0 until Levels.COUNT) {
            val n = MapPlate.nodes[i]
            bNodes[i].place(mx(n.l), my(n.t), mx(n.r), my(n.b), m.touch)
        }
        val s = MapPlate.soon
        bSoon.place(mx(s[0]), my(s[1]), mx(s[2]), my(s[3]), m.touch)

        debugRects.clear()
        debugRects.add(floatArrayOf(bBack.l, bBack.t, bBack.r, bBack.b))
        debugRects.add(floatArrayOf(coinL - hudH * 0.5f, hudCY - hudH / 2, coinR, hudCY + hudH / 2))
        debugRects.add(floatArrayOf(gemL - hudH * 0.5f, hudCY - hudH / 2, gemR, hudCY + hudH / 2))
        debugRects.add(floatArrayOf(m.cx - signW / 2, signTop, m.cx + signW / 2, signTop + signW * signAspect * 0.92f))
        for (i in 0 until Levels.COUNT) { val n = MapPlate.nodes[i]; debugRects.add(floatArrayOf(mx(n.l), my(n.t), mx(n.r), my(if (i == 0) bottomNeed else n.b))) }
    }

    override fun update(dt: Float) {
        t += dt
        btns.update(dt)
        for (i in shake.indices) shake[i] = max(0f, shake[i] - dt * 2.5f)
    }

    override fun draw(gr: Gfx) {
        val ma = app.mapArt ?: return
        gr.fillRect(0f, 0f, m.w, m.h, 0xFF2A78D8.toInt())
        gr.image(ma.map, x0, y0, MapPlate.W * k, MapPlate.H * k)
        Scenery.portal(gr, app, mx(MapPlate.PORTAL_X), my(MapPlate.PORTAL_Y), 24f * k)
        // soft foreground vignette so the bottom edge reads as depth, not an edge
        gr.fillRectGradient(0f, m.h - m.h * 0.08f, m.w, m.h, 0x00000000, 0x55061410)

        val cur = app.progress.currentLevel()
        drawFar(gr)
        for (i in Levels.COUNT - 1 downTo cur) drawNode(gr, i, cur)
        drawBoy(gr, ma, cur - 1)
        for (i in cur - 1 downTo 0) drawNode(gr, i, cur)
        drawHud(gr, ma)
    }

    private fun drawFar(gr: Gfx) {
        val f = MapPlate.farLocks
        var i = 0
        while (i < f.size) { Ui.lock(gr, mx(f[i]), my(f[i + 1]), 16f * k); i += 2 }
        // next stop after level 5: "SOON"
        val s = MapPlate.soon
        val cx = mx((s[0] + s[2]) / 2); val cy = my((s[1] + s[3]) / 2 + 2f)
        val ts = max(m.dp(11f), 15f * k * 0.62f)
        val tw = gr.textWidth("SOON", ts, Font.TITLE)
        val ph = ts * 1.5f
        val kk = bSoon.k
        gr.fillRoundRect(cx - (tw / 2 + ph * 0.45f) * kk - 2f * m.u, cy - ph / 2 * kk - 2f * m.u, cx + (tw / 2 + ph * 0.45f) * kk + 2f * m.u, cy + ph / 2 * kk + 2f * m.u, ph * 0.5f, 0xFF3A1004.toInt())
        Ui.grad(gr, cx - (tw / 2 + ph * 0.45f) * kk, cy - ph / 2 * kk, cx + (tw / 2 + ph * 0.45f) * kk, cy + ph / 2 * kk, ph * 0.5f, 0xFFFFB03A.toInt(), 0xFFE0521A.toInt())
        Ui.text(gr, "SOON", cx, cy, ts * kk, Col.WHITE, Font.TITLE, Align.CENTER, 0xFF5A1A04.toInt(), ts * 0.1f, false)
    }

    private fun drawNode(gr: Gfx, i: Int, cur: Int) {
        val n = MapPlate.nodes[i]
        val info = Levels.all[i]
        val level = info.number
        val p = app.progress
        val unlocked = p.unlocked(level)
        val current = level == cur
        val sc = n.s * k
        val press = bNodes[i].k
        val sx = if (shake[i] > 0f) sin(shake[i] * 40f) * 6f * m.u * shake[i] else 0f

        if (current) {
            gr.setAdditive(true)
            gr.glow(mx((n.l + n.r) / 2), my((n.t + n.b) / 2), (n.r - n.l) * k * 0.7f, Col.withA(0xFFFFF0A0.toInt(), 0.16f + 0.1f * pulse(t, 3.5f)))
            gr.setAdditive(false)
        }
        // number
        val numSize = 50f * sc * press * (if (current) 1f + 0.05f * pulse(t, 3.5f) else 1f)
        Ui.text(gr, level.toString(), mx(n.numX) + sx, my(n.numY), numSize, if (unlocked) Col.WHITE else 0xFFE4E8F4.toInt(),
            Font.TITLE, Align.CENTER, 0xFF0A1030.toInt(), numSize * 0.12f)
        // stars
        val got = p.stars(level)
        val sr = 13f * sc
        for (j in 0..2) Ui.star(gr, mx(n.numX + (j - 1) * 36f * n.s) + sx, my(n.starY), sr, j < got, if (unlocked) 1f else 0.85f)
        // lock
        if (!unlocked) {
            if (compact && i == Levels.COUNT - 1) Ui.lock(gr, mx(n.numX + 44f * n.s) + sx, my(n.numY), 30f * sc)
            else Ui.lock(gr, mx(n.lockX) + sx * 1.5f, my(n.lockY), 34f * sc)
        }
        // label
        label(gr, info.name, n, unlocked, current, info.playable)
    }

    private fun label(gr: Gfx, text: String, n: MapPlate.Node, unlocked: Boolean, current: Boolean, playable: Boolean) {
        val u = m.u
        val size = max(m.dp(11f), (if (current) 17f else 14f) * k * max(n.s, 0.62f))
        val s = if (!unlocked || playable) text else "$text (soon)"
        val tw = gr.textWidth(s, size, Font.UI)
        val padX = size * 0.55f; val ph = size * 1.45f
        val cy = my(n.labY)
        var l = when (n.labAlign) { 0 -> mx(n.labX) - tw / 2 - padX; -1 -> mx(n.labX) - tw - padX * 2; else -> mx(n.labX) }
        l = clamp(l, m.l, m.r - tw - padX * 2)
        val r = l + tw + padX * 2
        val bg = if (current) 0xE61A3A10.toInt() else 0xD90A1438.toInt()
        gr.fillRoundRect(l - 1.5f * u, cy - ph / 2 - 1.5f * u, r + 1.5f * u, cy + ph / 2 + 1.5f * u, ph / 2 + 1.5f * u, 0xCC06122E.toInt())
        gr.fillRoundRect(l, cy - ph / 2, r, cy + ph / 2, ph / 2, bg)
        val col = when { current -> 0xFFFFF4B0.toInt(); unlocked -> Col.WHITE; else -> 0xFFB8C4E0.toInt() }
        gr.text(s, (l + r) / 2, cy, size, Font.UI, col)
    }

    private fun drawBoy(gr: Gfx, ma: MapArt, i: Int) {
        val n = MapPlate.nodes[i]
        val h = MapPlate.BOY_H * n.s * k
        val w = h * ma.boyBack.w / ma.boyBack.h
        val hop = abs(sin(t * 3.2f)) * h * 0.025f
        val fx = mx(n.topX); val fy = my(n.topY)
        gr.fillCircle(fx, fy, w * 0.26f, 0x33000000)
        gr.image(ma.boyBack, fx - w * 0.5f, fy - h + h * 0.04f - hop, w, h)
    }

    private fun drawHud(gr: Gfx, ma: MapArt) {
        val u = m.u
        // back button
        val b = bBack
        val r = b.bw / 2 * b.k
        gr.fillCircle(b.cx, b.cy + r * 0.1f, r + 2f * u, 0x66000000)
        gr.fillCircle(b.cx, b.cy, r + 2f * u, 0xFF0B2F8C.toInt())
        Ui.gradCircle(gr, b.cx, b.cy, r, 0xFF55B6FF.toInt(), 0xFF1650DC.toInt())
        gr.fillCircle(b.cx, b.cy - r * 0.35f, r * 0.55f, 0x22FFFFFF)
        val a = r * 0.46f; val th = r * 0.17f
        gr.line(b.cx - a, b.cy, b.cx + a * 0.95f, b.cy, th * 2f, Col.WHITE)
        gr.line(b.cx - a, b.cy, b.cx - a * 0.1f, b.cy - a * 0.8f, th * 2f, Col.WHITE)
        gr.line(b.cx - a, b.cy, b.cx - a * 0.1f, b.cy + a * 0.8f, th * 2f, Col.WHITE)

        val p = app.progress
        Ui.currency(gr, ma.coin, Ui.fmt(p.coins), coinL, coinR, hudCY, hudH * 0.98f, pillH, bCoins, u)
        Ui.currency(gr, ma.gem, p.gems.toString(), gemL, gemR, hudCY, hudH * 0.98f, pillH, bGems, u)

        // title sign
        val sg = ma.sign
        val sh = signW * sg.h / sg.w
        gr.image(sg, m.cx - signW / 2, signTop, signW, sh)
        val title = "SELECT LEVEL"
        val ts = Ui.fit(gr, title, sh * 0.27f, Font.TITLE, signW * 0.74f)
        Ui.text(gr, title, m.cx, signTop + sh * 0.33f, ts, 0xFFFFD84A.toInt(), Font.TITLE, Align.CENTER, 0xFF5A2408.toInt(), ts * 0.12f)
        val cnt = "${p.totalStars} / ${Levels.MAX_STARS}"
        val cs = sh * 0.19f
        val cw = gr.textWidth(cnt, cs, Font.TITLE)
        val sr = cs * 0.62f
        val total = sr * 2f + cs * 0.4f + cw
        val sx = m.cx - total / 2
        val cy = signTop + sh * 0.765f
        Ui.star(gr, sx + sr, cy, sr, true)
        Ui.text(gr, cnt, sx + sr * 2f + cs * 0.4f, cy, cs, Col.WHITE, Font.TITLE, Align.LEFT, Ui.NAVY, cs * 0.12f, false)
    }

    override fun touchDown(id: Int, x: Float, y: Float) { btns.touchDown(id, x, y) }
    override fun touchMove(id: Int, x: Float, y: Float) = btns.touchMove(id, x, y)

    override fun touchUp(id: Int, x: Float, y: Float) {
        val b = btns.touchUp(id, x, y) ?: return
        app.click()
        when (b.id) {
            B.BACK -> app.go(app.home)
            B.COINS, B.GEMS -> app.toast("Shop - coming soon!")
            B.SOON -> app.toast("More levels coming soon!")
            else -> {
                val info = Levels.all[b.id]
                when {
                    !app.progress.unlocked(info.number) -> {
                        shake[b.id] = 1f
                        app.platform.sound(Sfx.WRONG, 0.5f)
                        app.toast("Complete Level ${info.number - 1} to unlock")
                    }
                    !info.playable -> app.toast("Level ${info.number}: ${info.name} - coming soon!")
                    else -> app.startLevel(info)
                }
            }
        }
    }

    override fun key(code: Int, down: Boolean) {
        if (down && code == Key.ENTER) {
            val info = Levels.all[app.progress.currentLevel() - 1]
            if (info.playable) { app.click(); app.startLevel(info) }
        }
    }

    override fun back(): Boolean { app.go(app.home); return true }
}
