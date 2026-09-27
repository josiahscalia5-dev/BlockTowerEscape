package com.blocktower.escape.core

import kotlin.math.max
import kotlin.math.min

/** A full-screen state: Splash, Home, Select Level or Gameplay. */
abstract class Screen(val app: App) {
    val m get() = app.m
    private var laidOut = -1

    open fun enter() {}
    /** Recompute positions for the current size / safe area. */
    open fun layout() {}
    open fun update(dt: Float) {}
    abstract fun draw(gr: Gfx)
    open fun touchDown(id: Int, x: Float, y: Float) {}
    open fun touchMove(id: Int, x: Float, y: Float) {}
    open fun touchUp(id: Int, x: Float, y: Float) {}
    open fun key(code: Int, down: Boolean) {}
    /** System Back. Return false to let the app handle it (leave the app). */
    open fun back(): Boolean = false
    open fun pause() {}

    fun render(gr: Gfx) {
        if (laidOut != m.version) { laidOut = m.version; layout() }
        draw(gr)
    }
    fun relayout() { laidOut = -1 }
}

/**
 * Screen flow: SPLASH -> HOME -> SELECT LEVEL -> GAMEPLAY.
 * Menu art needed by the splash loads immediately; the level map art and the whole gameplay
 * (textures, level) load on a background thread while the splash progress bar runs.
 */
class App(val platform: Platform) {
    val m = Metrics()
    val progress = Progress(platform)
    val art = MenuArt(platform)
    @Volatile var mapArt: MapArt? = null; private set
    @Volatile var game: Game? = null; private set
    @Volatile var loaded = 0f; private set
    @Volatile var loadError: String? = null; private set
    var t = 0f; private set

    val splash = SplashScreen(this)
    val home by lazy { HomeScreen(this) }
    val levels by lazy { LevelSelectScreen(this) }
    val play by lazy { GameScreen(this) }

    var screen: Screen = splash; private set
    private var next: Screen? = null
    private var fade = 0f
    private var fading = 0

    private var toastMsg = ""
    private var toastT = 0f

    init {
        Thread({
            try {
                mapArt = MapArt(platform)
                loaded = 0.35f
                val g = Game(platform)
                g.onExit = { exitGame() }
                g.onComplete = { r -> levelComplete(g, r) }
                game = g
                loaded = 1f
            } catch (e: Throwable) {
                loadError = e.toString()
            }
        }, "loader").start()
    }

    val isLoaded get() = game != null

    fun resize(w: Int, h: Int, density: Float, il: Float, it: Float, ir: Float, ib: Float) =
        m.set(w.toFloat(), h.toFloat(), density, il, it, ir, ib)

    fun go(s: Screen) {
        if (fading != 0 || s === screen) return
        next = s; fading = 1; fade = 0f
    }

    fun toast(msg: String) { toastMsg = msg; toastT = 2.2f }

    fun click() { platform.sound(Sfx.CLICK); platform.haptic(false) }

    fun startLevel(info: LevelInfo) {
        val g = game ?: return
        g.levelNumber = info.number
        g.startCoins = progress.coins
        g.startGems = progress.gems
        g.restartLevel()
        go(play)
    }

    private fun levelComplete(g: Game, r: Results) {
        progress.levelDone(g.levelNumber, r.stars)
        progress.setWallet(g.coins, g.gems)
    }

    private fun exitGame() { go(levels) }

    fun update(dtIn: Float) {
        val dt = min(dtIn, 1f / 20f)
        t += dt
        if (toastT > 0f) toastT -= dt
        if (fading == 1) {
            fade += dt / 0.16f
            if (fade >= 1f) {
                fade = 1f; fading = -1
                screen.pause()
                screen = next!!; next = null
                screen.relayout()
                screen.enter()
            }
        } else if (fading == -1) {
            fade -= dt / 0.24f
            if (fade <= 0f) { fade = 0f; fading = 0 }
        }
        screen.update(dt)
    }

    fun render(gr: Gfx) {
        screen.render(gr)
        if (toastT > 0f && toastMsg.isNotEmpty()) drawToast(gr)
        if (fade > 0f) gr.fillRect(0f, 0f, m.w, m.h, Col.withA(0xFF06122E.toInt(), smooth(fade)))
    }

    private fun drawToast(gr: Gfx) {
        val a = clamp01(toastT / 0.3f) * clamp01((2.2f - toastT) / 0.15f)
        val size = max(m.dp(15f), 16f * m.u)
        val maxW = m.sw - m.dp(24f)
        val fs = Ui.fit(gr, toastMsg, size, Font.UI, maxW - size * 1.6f)
        val tw = gr.textWidth(toastMsg, fs, Font.UI)
        val bw = tw + size * 1.6f; val bh = size * 2.3f
        val cy = m.t + m.sh * 0.6f
        gr.fillRoundRect(m.cx - bw / 2 - 2f * m.u, cy - bh / 2 - 2f * m.u, m.cx + bw / 2 + 2f * m.u, cy + bh / 2 + 2f * m.u, bh * 0.5f, Col.withA(Ui.NAVY_EDGE, 0.9f * a))
        gr.fillRoundRect(m.cx - bw / 2, cy - bh / 2, m.cx + bw / 2, cy + bh / 2, bh * 0.5f, Col.withA(0xFF16307A.toInt(), 0.95f * a))
        gr.text(toastMsg, m.cx, cy, fs, Font.UI, Col.WHITE, Align.CENTER, 0f, 0, a)
    }

    private fun busy() = fading != 0
    fun touchDown(id: Int, x: Float, y: Float) { if (!busy()) screen.touchDown(id, x, y) }
    fun touchMove(id: Int, x: Float, y: Float) { if (!busy()) screen.touchMove(id, x, y) }
    fun touchUp(id: Int, x: Float, y: Float) { screen.touchUp(id, x, y) }
    fun key(code: Int, down: Boolean) { if (!busy()) screen.key(code, down) }
    fun back() { if (!busy() && !screen.back()) platform.exitApp() }
    fun pause() = screen.pause()
}

/** Hosts the existing gameplay (Screen 4) unchanged. */
class GameScreen(app: App) : Screen(app) {
    private val g get() = app.game!!

    override fun update(dt: Float) {
        // the gameplay HUD / camera need the current size before simulating (not only when drawing)
        g.hud.setInsets(m.insT, m.insB)
        g.hud.layout(m.w.toInt(), m.h.toInt())
        g.update(dt)
    }

    override fun draw(gr: Gfx) {
        g.hud.setInsets(m.insT, m.insB)
        g.render(gr)
    }

    override fun touchDown(id: Int, x: Float, y: Float) = g.touchDown(id, x, y)
    override fun touchMove(id: Int, x: Float, y: Float) = g.touchMove(id, x, y)
    override fun touchUp(id: Int, x: Float, y: Float) = g.touchUp(id, x, y)
    override fun key(code: Int, down: Boolean) = g.onKey(code, down)

    override fun back(): Boolean {
        if (g.state == GS.RESULTS || g.state == GS.FAILED) g.onExit?.invoke() else g.togglePause()
        return true
    }

    override fun pause() {
        if (g.state == GS.PLAY && !g.paused) g.togglePause()
    }
}

/**
 * The shared world backdrop (Splash + Home): covers the whole screen edge to edge (no bars,
 * no stretching). Taller phones reveal more sky; shorter phones crop sky first.
 */
object Scenery {
    var k = 1f; var x0 = 0f; var y0 = 0f

    /** [portalY] = preferred screen y of the portal. */
    fun world(gr: Gfx, app: App, portalY: Float) {
        val m = app.m
        val img = app.art.world
        k = max(m.w / WorldPlate.W, m.h / WorldPlate.H)
        val pw = WorldPlate.W * k; val ph = WorldPlate.H * k
        x0 = (m.w - pw) * 0.65f
        y0 = clamp(portalY - WorldPlate.PORTAL_Y * k, m.h - ph, 0f)
        gr.image(img, x0, y0, pw, ph)
        portal(gr, app, x0 + WorldPlate.PORTAL_X * k, y0 + WorldPlate.PORTAL_Y * k, 26f * k)
    }

    /** Animated glow + swirl over the painted portal. */
    fun portal(gr: Gfx, app: App, x: Float, y: Float, r: Float) {
        val t = app.t
        gr.setAdditive(true)
        gr.glow(x, y, r * (2.2f + 0.25f * pulse(t, 2.2f)), Col.withA(0xFFB060FF.toInt(), 0.35f))
        app.mapArt?.let { ma ->
            gr.save(); gr.translate(x, y); gr.rotate(-t * 70f)
            gr.image(ma.swirl, -r, -r, r * 2f, r * 2f, 0.28f + 0.1f * pulse(t, 3f))
            gr.restore()
        }
        gr.glow(x, y, r * 0.8f, Col.withA(0xFFE8F4FF.toInt(), 0.35f + 0.2f * pulse(t, 4f)))
        gr.setAdditive(false)
    }

    /**
     * Guardian + running boy, fitted into (l, top, r, bottom) with the boy's feet on [bottom].
     * The Guardian bleeds off the left screen edge like in the reference art.
     * Returns the boy's height.
     */
    fun hero(gr: Gfx, app: App, top: Float, bottom: Float, maxW: Float, t: Float, intro: Float = 1f): Float {
        val a = app.art
        val hb = heroHeight(bottom - top, maxW)
        if (hb <= 0f) return 0f
        val bob = kotlin.math.sin(t * 9f) * hb * 0.012f
        val gx = 0f
        val boyL = gx - WorldPlate.GUARD_DX * hb
        val boyT = bottom - hb
        val gy = boyT + WorldPlate.GUARD_DY * hb + kotlin.math.sin(t * 1.6f) * hb * 0.012f
        val gw = WorldPlate.GUARD_W * hb; val gh = WorldPlate.GUARD_H * hb
        val ia = smooth(intro)
        // Guardian: purple aura, body, glowing eyes
        gr.setAdditive(true)
        gr.glow(gx + gw * 0.5f, gy + gh * 0.5f, gw * 0.75f, Col.withA(0xFF9A3CFF.toInt(), 0.32f * ia))
        gr.setAdditive(false)
        gr.image(a.guardian, gx - (1f - ia) * gw * 0.3f, gy, gw, gh, ia)
        gr.setAdditive(true)
        val eye = 0.55f + 0.45f * pulse(t, 3.2f)
        gr.glow(gx + gw * 0.47f, gy + gh * 0.47f, gw * 0.09f, Col.withA(0xFFE070FF.toInt(), 0.55f * eye * ia))
        gr.glow(gx + gw * 0.68f, gy + gh * 0.58f, gw * 0.08f, Col.withA(0xFFE070FF.toInt(), 0.55f * eye * ia))
        gr.setAdditive(false)
        // Boy
        val bw = WorldPlate.BOY_ASPECT * hb
        gr.image(a.boyRun, boyL + (1f - ia) * bw * 0.25f, boyT + bob, bw, hb, ia)
        return hb
    }

    /** Boy height that fits the hero group into a box of height [h] and width [w]. */
    fun heroHeight(h: Float, w: Float) = max(0f, min(h / WorldPlate.GROUP_H, w / WorldPlate.GROUP_W))
}
