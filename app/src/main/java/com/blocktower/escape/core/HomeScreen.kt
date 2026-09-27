package com.blocktower.escape.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen 2 - Home. Layout is solved top-down inside the safe area:
 * HUD row, logo band (logo between the two tile columns), hero (Guardian + boy), PLAY, tagline.
 * On short phones the logo / tiles / hero shrink first; buttons never go below 48dp.
 * On tall phones the extra height is given to the world between the elements.
 */
class HomeScreen(app: App) : Screen(app) {
    private object B { const val PROFILE = 0; const val COINS = 1; const val GEMS = 2; const val DAILY = 3; const val MISSIONS = 4
        const val VAULT = 5; const val SETTINGS = 6; const val PLAY = 7; const val SOUND = 10; const val VIB = 11; const val CLOSE = 12 }

    private val btns = Buttons()
    private val bProfile = btns.add(B.PROFILE)
    private val bCoins = btns.add(B.COINS)
    private val bGems = btns.add(B.GEMS)
    private val bDaily = btns.add(B.DAILY)
    private val bMissions = btns.add(B.MISSIONS)
    private val bVault = btns.add(B.VAULT)
    private val bSettings = btns.add(B.SETTINGS)
    private val bPlay = btns.add(B.PLAY)
    private val tiles = arrayOf(bDaily, bMissions, bVault, bSettings)

    private val dlg = Buttons()
    private val dSound = dlg.add(B.SOUND)
    private val dVib = dlg.add(B.VIB)
    private val dClose = dlg.add(B.CLOSE)
    private var settings = false
    private var settingsT = 0f

    private var t = 0f

    // layout (px)
    private var hudCY = 0f; private var hudH = 0f; private var pillH = 0f; private var avD = 0f
    private var profR = 0f; private var coinL = 0f; private var coinR = 0f; private var gemL = 0f; private var gemR = 0f
    private var tileW = 0f; private var tileH = 0f
    private var logoW = 0f; private var logoCY = 0f
    private var heroTop = 0f; private var heroBottom = 0f
    private var tagL = 0f; private var tagT = 0f; private var tagR = 0f; private var tagB = 0f
    private var panelL = 0f; private var panelT = 0f; private var panelR = 0f; private var panelB = 0f

    /** Exposed for the layout tests. */
    val debugRects = ArrayList<FloatArray>()

    override fun enter() { t = 0f; settings = false; btns.cancel() }

    override fun layout() {
        val u = m.u
        val sw = m.sw
        // ---- HUD row
        // profile : coins : gems = 3.3 : 3.6 : 2.9 pill heights (+ gaps); narrow phones shrink the row
        hudH = min(max(m.dp(40f), 44f * u), sw / 10.2f)
        avD = hudH * 1.14f
        hudCY = m.t + avD / 2
        pillH = hudH * 0.8f
        val gap = 8f * u
        val profW = (sw - 2f * gap) * 3.3f / 9.8f; val coinW = (sw - 2f * gap) * 3.6f / 9.8f
        profR = m.l + profW
        coinL = profR + gap + hudH * 0.5f; coinR = profR + gap + coinW
        gemL = coinR + gap + hudH * 0.5f; gemR = m.r
        val plusS = pillH * 0.9f
        bProfile.place(m.l, hudCY - avD / 2, profR, hudCY + avD / 2, m.touch)
        bCoins.place(coinR - plusS - 2f * u, hudCY - plusS / 2, coinR - 2f * u, hudCY + plusS / 2, m.touch)
        bGems.place(gemR - plusS - 2f * u, hudCY - plusS / 2, gemR - 2f * u, hudCY + plusS / 2, m.touch)
        val hudBottom = hudCY + avD / 2

        // ---- bottom stack: tagline on the safe bottom, PLAY above it
        val logo = app.art.logo
        val aspect = logo.h.toFloat() / logo.w
        val needHero = max(m.dp(150f), sw * 0.36f) * WorldPlate.GROUP_H
        var s = 1f
        var playW = 0f; var playH = 0f; var tagH = 0f; var bandH = 0f; var heroH = 0f; var gapS = 1f
        while (true) {
            gapS = max(0.55f, s)
            tileW = max(m.dp(58f), min(sw * 0.205f, 94f * u) * s)
            tileH = tileW * 1.08f
            val colH = tileH * 2f + 10f * u * gapS
            logoW = min(sw - 2f * tileW - 20f * u, 340f * u) * max(0.72f, s)
            bandH = max(colH, logoW * aspect)
            playW = min(sw * 0.66f, 300f * u)
            playH = max(m.dp(58f), playW * 0.3f * max(0.85f, s))
            tagH = max(m.dp(28f), 36f * u * max(0.8f, s))
            heroH = (m.b - tagH - 8f * u * gapS - playH - 12f * u * gapS) - (hudBottom + 12f * u * gapS + bandH + 6f * u * gapS)
            if (heroH >= needHero || s <= 0.62f) break
            s -= 0.04f
        }

        tagB = m.b; tagT = tagB - tagH
        tagL = m.cx - min(sw * 0.9f, 370f * u) / 2; tagR = m.w - tagL
        val playB = tagT - 8f * u * gapS
        bPlay.place(m.cx - playW / 2, playB - playH, m.cx + playW / 2, playB, m.touch)

        // tall phones: spare height goes between the HUD and the logo band, the rest shows world above the hero
        val heroMax = Scenery.heroHeight(Float.MAX_VALUE, m.w * 0.84f) * WorldPlate.GROUP_H
        val spare = max(0f, heroH - heroMax)
        val bandTop = hudBottom + 12f * u * gapS + spare * 0.3f
        val colTop = bandTop + (bandH - (tileH * 2f + 10f * u * gapS)) / 2
        logoCY = bandTop + bandH / 2
        bDaily.place(m.l, colTop, m.l + tileW, colTop + tileH, m.touch)
        bMissions.place(m.l, colTop + tileH + 10f * u * gapS, m.l + tileW, colTop + 2f * tileH + 10f * u * gapS, m.touch)
        bVault.place(m.r - tileW, colTop, m.r, colTop + tileH, m.touch)
        bSettings.place(m.r - tileW, colTop + tileH + 10f * u * gapS, m.r, colTop + 2f * tileH + 10f * u * gapS, m.touch)
        heroTop = bandTop + bandH + 6f * u * gapS
        heroBottom = bPlay.t - 12f * u * gapS

        // ---- settings dialog
        val pw = min(sw, 360f * u); val ph = 300f * u
        panelL = m.cx - pw / 2; panelR = m.cx + pw / 2
        panelT = m.t + (m.sh - ph) * 0.45f; panelB = panelT + ph
        val rowH = max(m.dp(48f), 52f * u)
        dSound.place(panelR - 28f * u - 88f * u, panelT + 92f * u, panelR - 28f * u, panelT + 92f * u + rowH, m.touch)
        dVib.place(panelR - 28f * u - 88f * u, panelT + 100f * u + rowH, panelR - 28f * u, panelT + 100f * u + rowH * 2f, m.touch)
        dClose.place(m.cx - 110f * u, panelB - 28f * u - max(m.dp(52f), 56f * u), m.cx + 110f * u, panelB - 28f * u, m.touch)

        debugRects.clear()
        debugRects.add(floatArrayOf(m.l, hudCY - avD / 2, profR, hudCY + avD / 2))
        debugRects.add(floatArrayOf(coinL - hudH * 0.5f, hudCY - hudH / 2, coinR, hudCY + hudH / 2))
        debugRects.add(floatArrayOf(gemL - hudH * 0.5f, hudCY - hudH / 2, gemR, hudCY + hudH / 2))
        for (b in tiles) debugRects.add(floatArrayOf(b.l, b.t, b.r, b.b))
        val lh = logoW * aspect
        debugRects.add(floatArrayOf(m.cx - logoW / 2, logoCY - lh / 2, m.cx + logoW / 2, logoCY + lh / 2))
        debugRects.add(floatArrayOf(bPlay.l, bPlay.t, bPlay.r, bPlay.b))
        debugRects.add(floatArrayOf(tagL, tagT, tagR, tagB))
        val hb = Scenery.heroHeight(heroBottom - heroTop, m.w * 0.84f)
        debugRects.add(floatArrayOf(-WorldPlate.GUARD_DX * hb, heroBottom - hb, -WorldPlate.GUARD_DX * hb + WorldPlate.BOY_ASPECT * hb, heroBottom))
    }

    override fun update(dt: Float) {
        t += dt
        btns.update(dt); dlg.update(dt)
        settingsT = if (settings) min(1f, settingsT + dt * 6f) else max(0f, settingsT - dt * 6f)
    }

    override fun draw(gr: Gfx) {
        Scenery.world(gr, app, heroTop + (heroBottom - heroTop) * 0.22f)
        Scenery.hero(gr, app, heroTop, heroBottom, m.w * 0.84f, t)
        Ui.imageC(gr, app.art.logo, m.cx, logoCY + sin(t * 1.4f) * m.u * 2f, logoW)
        drawHud(gr)
        drawTile(gr, bDaily, app.mapArt?.icDaily, "Daily", "Rewards", true)
        drawTile(gr, bMissions, app.mapArt?.icMissions, "Missions", null, true)
        drawTile(gr, bVault, app.mapArt?.icVault, "Prize", "Vault", false)
        drawTile(gr, bSettings, app.mapArt?.icGear, "Settings", null, false)
        drawPlay(gr)
        Ui.plank(gr, tagL, tagT, tagR, tagB, m.u)
        val tg = "RUN • JUMP • COLLECT • ESCAPE"
        val ts = Ui.fit(gr, tg, (tagB - tagT) * 0.5f, Font.TITLE, (tagR - tagL) * 0.84f)
        Ui.text(gr, tg, m.cx, (tagT + tagB) / 2, ts, 0xFFFFF0C8.toInt(), Font.TITLE, Align.CENTER, 0xFF3A1A06.toInt(), ts * 0.12f, false)
        if (settingsT > 0f) drawSettings(gr)
    }

    private fun drawHud(gr: Gfx) {
        val u = m.u
        val ma = app.mapArt ?: return
        val p = app.progress
        val pt = hudCY - pillH / 2; val pb = hudCY + pillH / 2
        // profile: avatar + level / XP pill
        val ax = m.l + avD / 2
        Ui.pill(gr, ax, pt, profR, pb, u)
        val tx = ax + avD / 2 + 6f * u
        val maxTw = profR - tx - pillH * 0.35f
        val ls = Ui.fit(gr, "Level ${p.playerLevel}", pillH * 0.42f, Font.TITLE, maxTw)
        Ui.text(gr, "Level ${p.playerLevel}", tx, pt + pillH * 0.3f, ls, Col.WHITE, Font.TITLE, Align.LEFT, Ui.NAVY, ls * 0.1f, false)
        val bt = pt + pillH * 0.56f; val bb = pt + pillH * 0.86f
        gr.fillRoundRect(tx, bt, profR - pillH * 0.3f, bb, (bb - bt) / 2, 0xFF3A2A12.toInt())
        val frac = p.levelXp / Progress.XP_PER_LEVEL.toFloat()
        if (frac > 0.02f) Ui.grad(gr, tx, bt, tx + (profR - pillH * 0.3f - tx) * frac, bb, (bb - bt) / 2, 0xFFFFE070.toInt(), 0xFFE89A10.toInt())
        gr.text("${p.levelXp}/${Progress.XP_PER_LEVEL}", (tx + profR - pillH * 0.3f) / 2, (bt + bb) / 2, (bb - bt) * 0.82f, Font.UI, Col.WHITE)
        val k = bProfile.k
        gr.fillCircle(ax, hudCY, avD / 2 * k, 0xFF06122E.toInt())
        Ui.gradCircle(gr, ax, hudCY, (avD / 2 - 1.5f * u) * k, 0xFF4FB2FF.toInt(), 0xFF1650DC.toInt())
        gr.fillCircle(ax, hudCY, (avD / 2 - 4f * u) * k, Col.WHITE)
        val ar = (avD / 2 - 6f * u) * k
        gr.image(ma.avatar, ax - ar, hudCY - ar, ar * 2, ar * 2)

        Ui.currency(gr, ma.coin, Ui.fmt(p.coins), coinL, coinR, hudCY, hudH * 0.98f, pillH, bCoins, u)
        Ui.currency(gr, ma.gem, p.gems.toString(), gemL, gemR, hudCY, hudH * 0.98f, pillH, bGems, u)
    }

    private fun drawTile(gr: Gfx, b: Btn, icon: Img?, l1: String, l2: String?, badge: Boolean) {
        val u = m.u
        val k = b.k
        val cx = b.cx; val cy = b.cy
        val w = b.bw * k; val h = b.bh * k
        val l = cx - w / 2; val tp = cy - h / 2
        Ui.blueTile(gr, l, tp, l + w, tp + h, w * 0.2f, u)
        val labelSize = max(m.dp(11f), w * 0.155f)
        val lines = if (l2 == null) 1 else 2
        val labelTop = tp + h - lines * labelSize * 1.02f - h * 0.07f
        if (icon != null) {
            val boxW = w * 0.74f; val boxH = labelTop - tp - h * 0.08f
            val iw = min(boxW, boxH * icon.w / icon.h)
            Ui.imageC(gr, icon, cx, tp + h * 0.06f + boxH / 2, iw)
        }
        val s1 = Ui.fit(gr, l1, labelSize, Font.UI, w * 0.9f)
        Ui.text(gr, l1, cx, labelTop + labelSize * 0.5f, s1, Col.WHITE, Font.UI, Align.CENTER, Ui.NAVY, labelSize * 0.16f, false)
        if (l2 != null) {
            val s2 = Ui.fit(gr, l2, labelSize, Font.UI, w * 0.9f)
            Ui.text(gr, l2, cx, labelTop + labelSize * 1.5f, s2, Col.WHITE, Font.UI, Align.CENTER, Ui.NAVY, labelSize * 0.16f, false)
        }
        if (badge) Ui.badge(gr, l + w - w * 0.06f, tp + w * 0.06f, w * 0.15f, t)
    }

    private val tri = FloatArray(6)
    private fun drawPlay(gr: Gfx) {
        val u = m.u
        val b = bPlay
        val k = b.k * (1f + 0.025f * sin(t * 4f))
        val w = b.bw * k; val h = b.bh * k
        val l = b.cx - w / 2; val tp = b.cy - h / 2
        gr.setAdditive(true)
        gr.glow(b.cx, b.cy, w * 0.62f, Col.withA(0xFF7CFF6A.toInt(), 0.18f + 0.08f * pulse(t, 4f)))
        gr.setAdditive(false)
        Ui.greenTile(gr, l, tp, l + w, tp + h, h * 0.3f, u * 1.6f)
        val ts = h * 0.56f
        val label = "PLAY"
        val tw = gr.textWidth(label, ts, Font.TITLE)
        val triW = h * 0.36f
        val total = triW + h * 0.16f + tw
        val x0 = b.cx - total / 2
        val cy = b.cy
        tri[0] = x0; tri[1] = cy - triW * 0.62f; tri[2] = x0 + triW; tri[3] = cy; tri[4] = x0; tri[5] = cy + triW * 0.62f
        val sh = h * 0.05f
        for (i in 0..2) { tri[i * 2 + 1] += sh }
        gr.fillPoly(tri, 3, 0x990A4A14.toInt())
        for (i in 0..2) { tri[i * 2 + 1] -= sh }
        gr.fillPoly(tri, 3, Col.WHITE)
        gr.strokePoly(tri, 3, h * 0.04f, 0xFF0C5A1C.toInt())
        Ui.text(gr, label, x0 + triW + h * 0.16f, cy, ts, Col.WHITE, Font.TITLE, Align.LEFT, 0xFF0C5A1C.toInt(), ts * 0.1f)
    }

    private fun drawSettings(gr: Gfx) {
        val u = m.u
        val a = smooth(settingsT)
        gr.fillRect(0f, 0f, m.w, m.h, Col.withA(0xFF050A20.toInt(), 0.62f * a))
        val sc = 0.9f + 0.1f * easeOutBack(settingsT)
        gr.save()
        gr.translate(m.cx, (panelT + panelB) / 2); gr.scale(sc, sc); gr.translate(-m.cx, -(panelT + panelB) / 2)
        Ui.panel(gr, panelL, panelT, panelR, panelB, u)
        Ui.text(gr, "SETTINGS", m.cx, panelT + 48f * u, 40f * u, 0xFFFFE14A.toInt(), Font.TITLE, Align.CENTER, 0xFF1A0A20.toInt(), 4f * u)
        toggleRow(gr, "Sound", dSound, app.progress.sound)
        toggleRow(gr, "Vibration", dVib, app.progress.haptics)
        Ui.greenTile(gr, dClose.l, dClose.t, dClose.r, dClose.b, dClose.bh * 0.32f, u)
        Ui.text(gr, "CLOSE", dClose.cx, dClose.cy, dClose.bh * 0.46f, Col.WHITE, Font.TITLE, Align.CENTER, 0xFF0C5A1C.toInt(), 3f * u)
        gr.restore()
    }

    private fun toggleRow(gr: Gfx, label: String, b: Btn, on: Boolean) {
        val u = m.u
        Ui.text(gr, label, panelL + 30f * u, b.cy, 28f * u, Col.WHITE, Font.TITLE, Align.LEFT, Ui.NAVY, 3f * u, false)
        val th = b.bh * 0.62f
        val tl = b.l; val tr = b.r; val tt = b.cy - th / 2; val tb = b.cy + th / 2
        gr.fillRoundRect(tl - 2f * u, tt - 2f * u, tr + 2f * u, tb + 2f * u, th / 2 + 2f * u, Ui.NAVY_EDGE)
        if (on) Ui.grad(gr, tl, tt, tr, tb, th / 2, 0xFF8AF25C.toInt(), 0xFF1EA83A.toInt())
        else Ui.grad(gr, tl, tt, tr, tb, th / 2, 0xFF6A7488.toInt(), 0xFF4A5264.toInt())
        val kx = if (on) tr - th / 2 else tl + th / 2
        gr.fillCircle(kx, b.cy + 1.5f * u, th * 0.42f, 0x55000000)
        gr.fillCircle(kx, b.cy, th * 0.42f, Col.WHITE)
        Ui.text(gr, if (on) "ON" else "OFF", if (on) tl + (tr - tl - th) / 2 else tl + th + (tr - tl - th) / 2, b.cy, th * 0.42f, Col.WHITE, Font.TITLE, Align.CENTER, Ui.NAVY, th * 0.05f, false)
    }

    override fun touchDown(id: Int, x: Float, y: Float) {
        if (settings) {
            if (!dlg.touchDown(id, x, y) && (x < panelL || x > panelR || y < panelT || y > panelB)) { settings = false; app.click() }
            return
        }
        btns.touchDown(id, x, y)
    }

    override fun touchMove(id: Int, x: Float, y: Float) { if (settings) dlg.touchMove(id, x, y) else btns.touchMove(id, x, y) }

    override fun touchUp(id: Int, x: Float, y: Float) {
        if (settings) {
            when (dlg.touchUp(id, x, y)?.id) {
                B.SOUND -> { app.progress.setSound(!app.progress.sound); app.click() }
                B.VIB -> { app.progress.setHaptics(!app.progress.haptics); app.click() }
                B.CLOSE -> { app.click(); settings = false }
            }
            return
        }
        val b = btns.touchUp(id, x, y) ?: return
        app.click()
        when (b.id) {
            B.PLAY -> app.go(app.levels)
            B.SETTINGS -> { settings = true; dlg.cancel() }
            B.DAILY -> app.toast("Daily Rewards - coming soon!")
            B.MISSIONS -> app.toast("Missions - coming soon!")
            B.VAULT -> app.toast("Prize Vault - coming soon!")
            B.COINS, B.GEMS -> app.toast("Shop - coming soon!")
            B.PROFILE -> app.toast("Level ${app.progress.playerLevel}  •  ${app.progress.totalStars}/${Levels.MAX_STARS} stars")
        }
    }

    override fun key(code: Int, down: Boolean) {
        if (down && code == Key.ENTER && !settings) { app.click(); app.go(app.levels) }
    }

    override fun back(): Boolean {
        if (settings) { settings = false; return true }
        return false
    }
}
