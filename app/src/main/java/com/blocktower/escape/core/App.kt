package com.blocktower.escape.core

import kotlin.math.min

object Scr { const val HOME = 0; const val MAP = 1; const val GAME = 2; const val SPLASH = 3 }
object Pop { const val NONE = 0; const val DAILY = 1; const val MISSIONS = 2; const val VAULT = 3; const val SETTINGS = 4; const val LEVEL = 5; const val RESET = 6; const val SOON = 7; const val MORE = 8 }

/** Sound and vibration follow the player's settings. */
private class GatedPlatform(private val p: Platform, private val prog: () -> Progress?) : Platform by p {
    override fun sound(id: Int, volume: Float, rate: Float) { if (prog()?.sound != false) p.sound(id, volume, rate) }
    override fun haptic(strong: Boolean) { if (prog()?.vibration != false) p.haptic(strong) }
    override fun music(track: Int, intensity: Float, volume: Float) {
        if (prog()?.music != false) p.music(track, intensity, volume) else p.music(Music.NONE, 0f, 0f)
    }
}

/**
 * The whole game: the splash screen, the Home screen, the level map, the menu popups and the level being played.
 * It keeps the saved progress (unlocked levels, stars, wallet, missions, daily rewards, settings) and
 * moves between screens with a short fade.
 *
 * With [showSplash] the splash is shown while the menus and the block textures load on a background thread
 * (the splash's bar follows that loading); without it (tests) everything loads right away and the app opens on Home.
 */
class App(platform: Platform, showSplash: Boolean = true) : GameHost {
    private var prog: Progress? = null
    val pf: Platform = GatedPlatform(platform) { prog }
    val progress: Progress = Progress(platform).also { it.load(); prog = it }

    private val arts = java.util.concurrent.ConcurrentHashMap<Int, Art>()
    fun art(theme: Int): Art = arts.getOrPut(theme) { Art(pf, theme) }
    /** Icons, coins and block textures used by the menus. */
    val baseArt get() = art(Theme.SKY_TOWER)

    /** The safe area and density the menu screens lay out in. */
    val safe = Safe()
    /** The splash / Home artwork: the world, the boy, the Guardian and the logo (needed by the first frame). */
    val menuArt = MenuArt(pf)
    val ui = Ui(this)
    val splash = SplashScreen(this)
    lateinit var home: HomeScreen
        private set
    lateinit var map: LevelMap
        private set
    val menus = Menus(this)

    /** Loading progress 0..1 (the splash's bar), and whether the menus are ready. */
    @Volatile var loaded = 0f
        private set
    @Volatile var loadError: String? = null
        private set
    @Volatile var ready = false
        private set

    var screen = if (showSplash) Scr.SPLASH else Scr.HOME
        private set
    var popup = Pop.NONE
        private set
    /** The level shown in the LEVEL popup. */
    var popupLevel = 1
        private set
    var popupT = 0f
        private set
    var game: Game? = null
        private set

    var w = 1080f; var h = 2340f
    /** UI scale: artwork pixels (1024 wide) to screen pixels, same as the HUD. */
    var s = 1f
    var topInset = 0f; var bottomInset = 0f
    var leftInset = 0f; var rightInset = 0f
    /** Screen pixels per dp (set by the host; tests use a typical phone's). */
    var density = 0f
    var t = 0f
        private set
    /** Tests: pretend today is this local day. */
    var dayOverride: Int? = null
    /** A level unlocked by the last result (the map celebrates it), 0 when none. */
    var justUnlocked = 0
    val sparks = ArrayList<UiSpark>()

    // ---- screen changes fade through dark
    private var fadeA = 0f
    private var fadeTarget = 0f
    private var pending: (() -> Unit)? = null
    val fading get() = fadeTarget > 0f || fadeA > 0.35f

    private fun transition(action: () -> Unit) {
        if (pending != null) return
        pending = action; fadeTarget = 1f
    }

    /** Loads what the menus need: the block textures and icons, the Home screen and the level map. */
    private fun load() {
        try {
            art(Theme.SKY_TOWER); loaded = 0.55f
            val hs = HomeScreen(this); loaded = 0.8f
            val lm = LevelMap(this); loaded = 0.95f
            home = hs; map = lm
            loaded = 1f; ready = true
        } catch (e: Throwable) {
            loadError = e.toString()
        }
    }

    /** The splash is done (loading finished): on to the Home screen. */
    fun splashDone() = transition { screen = Scr.HOME; popup = Pop.NONE }

    fun setInsets(top: Float, bottom: Float, left: Float = 0f, right: Float = 0f) {
        topInset = top; bottomInset = bottom; leftInset = left; rightInset = right
        game?.hud?.setInsets(top, bottom)
    }

    fun layout(width: Int, height: Int) {
        w = width.toFloat(); h = height.toFloat()
        s = min(w / 1024f, (h - topInset - bottomInset) / 1450f)
        val dens = if (density > 0f) density else w / 411f
        safe.set(w, h, dens, leftInset, topInset, rightInset, bottomInset)
    }

    fun today(): Int {
        dayOverride?.let { return it }
        val now = System.currentTimeMillis()
        val off = java.util.TimeZone.getDefault().getOffset(now)
        return ((now + off) / 86_400_000L).toInt()
    }
    /** Seconds until the next local midnight (the next daily reward). */
    fun secondsToTomorrow(): Int {
        val now = System.currentTimeMillis()
        val off = java.util.TimeZone.getDefault().getOffset(now)
        return (86_400 - ((now + off) / 1000L % 86_400L)).toInt()
    }

    // ------------------------------------------------------------------ loop
    fun update(dtIn: Float) {
        val dt = min(dtIn, 1f / 20f)
        t += dt
        if (fadeTarget > 0f && fadeA >= 0.999f) {
            val a = pending; pending = null
            a?.invoke()
            fadeTarget = 0f
        }
        fadeA = approach(fadeA, fadeTarget, dt * 6f)
        when (screen) {
            Scr.GAME -> game?.update(dtIn)
            Scr.HOME -> home.update(dt)
            Scr.MAP -> map.update(dt)
            Scr.SPLASH -> splash.update(dt)
        }
        if (popup != Pop.NONE) popupT += dt
        updateMusic()
        val it = sparks.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt; if (p.life <= 0f) { it.remove(); continue }
            p.vy += 900f * s * dt * (if (p.kind == 1) 1f else 0.2f)
            p.x += p.vx * dt; p.y += p.vy * dt
        }
    }

    /** The Home theme on the menus; in a level its theme, building with the action, quieter while paused. */
    private fun updateMusic() {
        val g = game
        if (screen == Scr.GAME && g != null) {
            val vol = when {
                g.paused -> 0.35f
                g.state == GS.FAILED -> 0.3f
                g.state == GS.RESULTS -> 0.5f
                else -> 1f
            }
            pf.music(if (g.spec.jungle) Music.JUNGLE else Music.SKY, g.musicIntensity(), vol)
        } else pf.music(Music.HOME, 0f, if (popup != Pop.NONE) 0.8f else 1f)
    }

    private val sparkPoly = FloatArray(16)
    fun render(gr: Gfx) {
        layout(gr.width, gr.height)
        ui.beginFrame()
        when (screen) {
            Scr.GAME -> game?.render(gr)
            Scr.HOME -> home.render(gr, popup == Pop.NONE)
            Scr.MAP -> map.render(gr, popup == Pop.NONE)
            Scr.SPLASH -> splash.render(gr)
        }
        if (popup != Pop.NONE) menus.render(gr)
        for (p in sparks) {
            val a = clamp01(p.life / p.max * 1.5f)
            if (p.kind == 1) {
                gr.fillRect(p.x - p.size, p.y - p.size * 0.5f, p.x + p.size, p.y + p.size * 0.5f, Col.withA(p.color, a))
            } else gr.star4(p.x, p.y, p.size * (0.5f + 0.5f * a), Col.withA(p.color, a), sparkPoly)
        }
        if (fadeA > 0.001f) gr.fillRect(0f, 0f, w, h, Col.withA(0xFF050A20.toInt(), fadeA))
        ui.endFrame()
    }

    fun burst(x: Float, y: Float, n: Int, color: Int, confetti: Boolean = false) {
        val rng = java.util.Random((t * 1000f).toLong() + n)
        for (i in 0 until n) {
            val p = UiSpark()
            val a = rng.nextFloat() * TAU; val sp = (200f + rng.nextFloat() * 500f) * s
            p.x = x; p.y = y; p.vx = kotlin.math.cos(a) * sp; p.vy = kotlin.math.sin(a) * sp - (if (confetti) 400f * s else 0f)
            p.life = 0.6f + rng.nextFloat() * 0.6f; p.max = p.life
            p.size = (10f + rng.nextFloat() * 14f) * s
            p.kind = if (confetti) 1 else 0
            p.color = if (confetti) intArrayOf(0xFFFFD84A.toInt(), 0xFF5AE07A.toInt(), 0xFF5AB6FF.toInt(), 0xFFFF6A8A.toInt(), 0xFFB070FF.toInt())[i % 5] else color
            sparks.add(p)
        }
    }

    // ------------------------------------------------------------------ navigation
    fun openPopup(p: Int, level: Int = popupLevel) {
        popup = p; popupLevel = level; popupT = 0f
        menus.opened(p)
        pf.sound(Sfx.CLICK)
    }
    fun closePopup() { popup = Pop.NONE; ui.cancel() }

    fun openMap() = transition { screen = Scr.MAP; popup = Pop.NONE; map.focusOn(currentLevel()) }
    fun openHome() = transition { screen = Scr.HOME; popup = Pop.NONE; game = null }

    /** The level the player is up to (the highest unlocked one that exists). */
    fun currentLevel() = min(progress.unlocked, Levels.count)

    fun startLevel(n: Int) = transition {
        val spec = Levels.get(n)
        game = Game(pf, n, art(spec.theme), this).also {
            it.hud.setInsets(topInset, bottomInset)
            it.swipe.sensitivity = SENSITIVITY[progress.sensitivity]
            it.joy.sensitivity = SENSITIVITY[progress.sensitivity]
            it.joystickMode = progress.controls == 1
        }
        screen = Scr.GAME; popup = Pop.NONE; justUnlocked = 0
    }

    private val coinTotals = HashMap<Int, Int>()
    /** Coins placed in a level (for the star goals on the level card). */
    fun coinTotal(n: Int) = coinTotals.getOrPut(n) { Levels.get(n).build().coinTotal }

    fun applySettings() {
        val g = game ?: return
        g.swipe.sensitivity = SENSITIVITY[progress.sensitivity]; g.joy.sensitivity = SENSITIVITY[progress.sensitivity]
        g.joystickMode = progress.controls == 1
    }

    // ------------------------------------------------------------------ input
    private var menuPointer = -1

    fun touchDown(id: Int, x: Float, y: Float) {
        if (fading || screen == Scr.SPLASH) return
        if (screen == Scr.GAME && popup == Pop.NONE) { game?.touchDown(id, x, y); return }
        if (menuPointer >= 0) return
        menuPointer = id
        when {
            popup != Pop.NONE -> ui.down(x, y)
            screen == Scr.HOME -> home.down(x, y)
            screen == Scr.MAP -> map.down(x, y)
        }
    }

    fun touchMove(id: Int, x: Float, y: Float) {
        if (screen == Scr.SPLASH) return
        if (screen == Scr.GAME && popup == Pop.NONE) { game?.touchMove(id, x, y); return }
        if (id != menuPointer) return
        when {
            popup != Pop.NONE -> menus.move(x, y)
            screen == Scr.HOME -> home.move(x, y)
            screen == Scr.MAP -> map.move(x, y)
        }
    }

    fun touchUp(id: Int, x: Float, y: Float) {
        if (screen == Scr.SPLASH) return
        if (screen == Scr.GAME && popup == Pop.NONE) { game?.touchUp(id, x, y); return }
        if (id != menuPointer) return
        menuPointer = -1
        if (fading) { ui.cancel(); return }
        when {
            popup != Pop.NONE -> menus.tap(ui.up(x, y))
            screen == Scr.HOME -> home.up(x, y)
            screen == Scr.MAP -> map.up(x, y)
        }
    }

    fun touchCancel(id: Int) {
        if (screen == Scr.GAME) { game?.touchCancel(id); return }
        if (id == menuPointer) { menuPointer = -1; ui.cancel(); if (ready) { map.cancel(); home.cancel() } }
    }

    fun onKey(code: Int, down: Boolean) {
        if (screen == Scr.GAME) game?.onKey(code, down)
        else if (down && code == Key.ENTER && screen == Scr.HOME && popup == Pop.NONE) openMap()
    }

    /** Android back. Returns false when the app should close (back on the Home screen). */
    fun back(): Boolean {
        if (fading) return true
        if (popup != Pop.NONE) { closePopup(); pf.sound(Sfx.CLICK); return true }
        when (screen) {
            Scr.HOME, Scr.SPLASH -> return false
            Scr.MAP -> openHome()
            Scr.GAME -> {
                val g = game ?: return true
                if (g.state == GS.RESULTS || g.state == GS.FAILED) exitToMap(g) else g.togglePause()
            }
        }
        return true
    }

    /** The app went to the background. */
    fun onPause() {
        val g = game
        if (screen == Scr.GAME && g != null && (g.state == GS.PLAY || g.state == GS.INTRO) && !g.paused) g.togglePause()
        progress.save()
    }

    // ------------------------------------------------------------------ GameHost
    override val walletCoins get() = progress.coins
    override val walletGems get() = progress.gems

    override fun onLevelComplete(g: Game, r: Results) {
        val n = g.spec.number
        val opened = progress.recordLevel(n, r.stars, r.totalScore, r.earnedCoins + r.rewardCoins, r.earnedGems + r.gemReward,
            r.targetGot, r.coinsCollected, g.toolUses, g.ev.chase == Chase.ESCAPED, g.mysteryOpened, !g.heartLost)
        if (opened && n + 1 <= Levels.count) justUnlocked = n + 1
    }

    override fun nextLevel(g: Game) {
        val n = g.spec.number + 1
        if (n <= Levels.count && n <= progress.unlocked) startLevel(n)
        else transition { game = null; screen = Scr.MAP; map.focusOn(currentLevel()); popup = Pop.SOON; popupT = 0f }
    }

    override fun exitToMap(g: Game) = transition {
        val unlockedNow = justUnlocked
        game = null; screen = Scr.MAP; popup = Pop.NONE
        map.focusOn(if (unlockedNow > 0) unlockedNow else currentLevel())
    }

    // last, so every property above is initialised before loading starts
    init {
        if (showSplash) Thread({ load() }, "loader").start() else load()
    }

    companion object {
        /** Swipe sensitivity settings: low, normal, high. */
        val SENSITIVITY = floatArrayOf(0.75f, 1f, 1.3f)
    }
}
