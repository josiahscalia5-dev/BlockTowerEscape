package com.blocktower.escape.core

import kotlin.math.max
import kotlin.math.min

/**
 * The splash screen (from the approved Splash reference): the Block Tower Escape logo over the same floating-island
 * world, castle and portal as the Home screen, the same boy running from the Guardian, and a loading bar near the
 * bottom (no buttons). The bar follows the real loading of the menus and block textures; when it is full the app
 * fades to the Home screen.
 */
class SplashScreen(private val app: App) {
    private var t = 0f
    private var shown = 0f
    private var doneT = 0f
    private var laidOut = -1

    // layout (px)
    private var logoW = 0f; private var logoCY = 0f
    private var heroTop = 0f; private var heroBottom = 0f
    private var barL = 0f; private var barR = 0f; private var barCY = 0f; private var barH = 0f
    private var labelY = 0f; private var labelSize = 0f

    private fun layout() {
        val m = app.safe
        if (laidOut == m.version) return
        laidOut = m.version
        val u = m.u
        // the loading bar and its label hug the bottom of the safe area
        labelSize = max(m.dp(14f), 17f * u)
        labelY = m.b - labelSize * 0.6f
        barH = max(m.dp(16f), 20f * u)
        barCY = labelY - labelSize * 0.9f - barH / 2
        val bw = min(m.sw * 0.68f, 330f * u)
        barL = m.cx - bw / 2; barR = m.cx + bw / 2
        // the logo: big and centred in the upper part (a tall phone shows a little world above it)
        val logo = app.menuArt.logo
        val aspect = logo.h.toFloat() / logo.w
        logoW = min(min(m.sw * 0.94f, 470f * u), m.sh * 0.3f / aspect)
        val logoH = logoW * aspect
        val extra = max(0f, m.sh - 900f * u)
        val logoTop = m.t + m.sh * 0.03f + extra * 0.25f
        logoCY = logoTop + logoH / 2
        // the boy and the Guardian between the logo and the bar
        heroTop = logoTop + logoH + 4f * u
        heroBottom = barCY - barH / 2 - 14f * u
    }

    fun update(dt: Float) {
        t += dt
        // the bar shows the real progress, paced so the splash can be seen (~1.8 s at least)
        val target = min(app.loaded, t / 1.8f)
        shown = min(1f, shown + max(0f, target - shown) * min(1f, dt * 6f) + dt * 0.02f)
        shown = min(shown, target + 0.02f)
        if (app.ready && shown >= 0.995f) {
            doneT += dt
            if (doneT > 0.25f) app.splashDone()
        }
    }

    fun render(gr: Gfx) {
        layout()
        val m = app.safe
        val art = app.menuArt
        Scenery.world(gr, art, m, app.t, heroTop + (heroBottom - heroTop) * 0.3f)
        Scenery.hero(gr, art, heroTop, heroBottom, m.w * 0.84f, t, min(1f, t / 0.5f))
        val pop = easeOutBack(clamp01((t - 0.1f) / 0.5f))
        if (pop > 0f) Kit.imageC(gr, art.logo, m.cx, logoCY, logoW * pop, min(1f, pop * 1.5f))
        drawBar(gr)
    }

    private fun drawBar(gr: Gfx) {
        val u = app.safe.u
        val cx = app.safe.cx
        val top = barCY - barH / 2; val bot = barCY + barH / 2
        val rad = barH / 2
        gr.fillRoundRect(barL - 4f * u, top - 4f * u, barR + 4f * u, bot + 4f * u, rad + 4f * u, Kit.NAVY_EDGE)
        Kit.grad(gr, barL - 2.5f * u, top - 2.5f * u, barR + 2.5f * u, bot + 2.5f * u, rad + 2.5f * u, 0xFF4FB2FF.toInt(), 0xFF1650DC.toInt())
        gr.fillRoundRect(barL, top, barR, bot, rad, Kit.NAVY)
        val fw = (barR - barL) * clamp01(shown)
        if (fw > barH * 0.4f) {
            Kit.grad(gr, barL, top, barL + fw, bot, rad, 0xFFFFE070.toInt(), 0xFFF29A10.toInt())
            gr.fillRoundRect(barL + rad * 0.5f, top + barH * 0.14f, barL + fw - rad * 0.5f, top + barH * 0.42f, rad * 0.4f, 0x66FFFFFF)
        }
        val label = if (app.loadError != null) "LOADING FAILED" else "LOADING..."
        Kit.text(gr, label, cx, labelY, labelSize, Col.WHITE, Font.TITLE, Align.CENTER, Kit.NAVY, labelSize * 0.14f)
    }

    /** Tests: the logo, the boy and the loading bar on screen, [left, top, right, bottom] each. */
    fun layoutRects(): List<Pair<String, FloatArray>> {
        layout()
        val a = app.menuArt.logo.h.toFloat() / app.menuArt.logo.w
        val hb = Scenery.heroHeight(heroBottom - heroTop, app.safe.w * 0.84f)
        val bl = -WorldPlate.GUARD_DX * hb
        return listOf(
            "logo" to floatArrayOf(app.safe.cx - logoW / 2, logoCY - logoW * a / 2, app.safe.cx + logoW / 2, logoCY + logoW * a / 2),
            "boy" to floatArrayOf(bl, heroBottom - hb, bl + WorldPlate.BOY_ASPECT * hb, heroBottom),
            "loading bar" to floatArrayOf(barL, barCY - barH / 2, barR, labelY + labelSize * 0.6f),
        )
    }
}
