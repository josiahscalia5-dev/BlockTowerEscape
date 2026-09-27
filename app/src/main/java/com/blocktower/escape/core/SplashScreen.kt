package com.blocktower.escape.core

import kotlin.math.max
import kotlin.math.min

/**
 * Screen 1 - Splash. Same world, logo, boy and Guardian as Home; only a loading bar as UI.
 * The bar follows the real background loading (gameplay textures, level, map art).
 */
class SplashScreen(app: App) : Screen(app) {
    private var t = 0f
    private var shown = 0f
    private var doneT = 0f

    // layout (px)
    private var logoW = 0f; private var logoCY = 0f
    private var heroTop = 0f; private var heroBottom = 0f
    private var barL = 0f; private var barR = 0f; private var barCY = 0f; private var barH = 0f
    private var labelY = 0f; private var labelSize = 0f

    override fun layout() {
        val u = m.u
        // loading bar + label hug the bottom of the safe area
        labelSize = max(m.dp(14f), 17f * u)
        labelY = m.b - labelSize * 0.6f
        barH = max(m.dp(16f), 20f * u)
        barCY = labelY - labelSize * 0.9f - barH * 0.5f
        val bw = min(m.sw * 0.68f, 330f * u)
        barL = m.cx - bw / 2; barR = m.cx + bw / 2

        // logo: large, centred, below the top safe edge
        val logo = app.art.logo
        val aspect = logo.h.toFloat() / logo.w
        logoW = min(m.sw * 0.92f, 470f * u)
        logoW = min(logoW, m.sh * 0.3f / aspect)
        val logoH = logoW * aspect
        val extra = max(0f, m.sh - 900f * u) // tall phones: let the world breathe above the logo
        val logoTop = m.t + m.sh * 0.03f + extra * 0.25f
        logoCY = logoTop + logoH / 2

        heroTop = logoTop + logoH + 4f * u
        heroBottom = barCY - barH / 2 - 14f * u
    }

    override fun update(dt: Float) {
        t += dt
        // bar = real progress, paced so the splash is readable (>= ~1.8 s)
        val target = min(app.loaded, t / 1.8f)
        shown = min(1f, shown + max(0f, target - shown) * min(1f, dt * 6f) + dt * 0.02f)
        shown = min(shown, target + 0.02f)
        if (app.isLoaded && shown >= 0.995f) {
            doneT += dt
            if (doneT > 0.25f) app.go(app.home)
        }
    }

    override fun draw(gr: Gfx) {
        Scenery.world(gr, app, heroTop + (heroBottom - heroTop) * 0.3f)
        Scenery.hero(gr, app, heroTop, heroBottom, m.w * 0.84f, t, min(1f, t / 0.5f))

        // logo pops in
        val pop = easeOutBack(clamp01((t - 0.1f) / 0.5f))
        if (pop > 0f) Ui.imageC(gr, app.art.logo, m.cx, logoCY, logoW * pop, min(1f, pop * 1.5f))

        drawBar(gr)
    }

    private fun drawBar(gr: Gfx) {
        val u = m.u
        val top = barCY - barH / 2; val bot = barCY + barH / 2
        val rad = barH / 2
        gr.fillRoundRect(barL - 4f * u, top - 4f * u, barR + 4f * u, bot + 4f * u, rad + 4f * u, 0xFF06122E.toInt())
        Ui.grad(gr, barL - 2.5f * u, top - 2.5f * u, barR + 2.5f * u, bot + 2.5f * u, rad + 2.5f * u, 0xFF4FB2FF.toInt(), 0xFF1650DC.toInt())
        gr.fillRoundRect(barL, top, barR, bot, rad, 0xFF0A1A44.toInt())
        val fw = (barR - barL) * clamp01(shown)
        if (fw > barH * 0.4f) {
            Ui.grad(gr, barL, top, barL + fw, bot, rad, 0xFFFFE070.toInt(), 0xFFF29A10.toInt())
            gr.fillRoundRect(barL + rad * 0.5f, top + barH * 0.14f, barL + fw - rad * 0.5f, top + barH * 0.42f, rad * 0.4f, 0x66FFFFFF)
        }
        val err = app.loadError
        val label = if (err != null) "LOADING FAILED" else "LOADING..."
        Ui.text(gr, label, m.cx, labelY, labelSize, Col.WHITE, Font.TITLE, Align.CENTER, Ui.NAVY, labelSize * 0.14f)
    }

    override fun back(): Boolean = false
}
