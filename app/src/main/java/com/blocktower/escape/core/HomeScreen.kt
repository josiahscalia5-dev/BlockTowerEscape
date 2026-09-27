package com.blocktower.escape.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Home / Welcome screen, composed from the approved Home artwork (design/home_reference.png): the top bar
 * (profile, level and XP, coins and gems with their + buttons, the settings gear), DAILY REWARDS and MISSIONS on
 * the left, PRIZE VAULT and SETTINGS on the right with the Block Tower Escape logo between them, the boy running
 * from the Guardian over the floating-island world with the castle and portal, the PLAY button and
 * RUN • JUMP • COLLECT • ESCAPE. The pieces are the artwork's own (cut out by design/home/build_home.py and
 * design/menu), placed by a responsive layout instead of a fixed picture:
 *
 * - the world fills the whole screen edge to edge (no bars, no blurred filler, never stretched);
 * - every button and text sits inside the Android safe area (bars, camera cut-out, gesture strip, rounded corners);
 * - the layout is solved from the top (top bar, logo band) and the bottom (tagline, PLAY) toward the hero in the
 *   middle; a short phone shrinks the logo, tiles and hero first, never PLAY or the tiles below 48dp; a tall phone
 *   gives its spare height to the world around the hero, so nothing overlaps and there is no dead zone.
 *
 * Every button works: PLAY opens the level map, DAILY REWARDS, MISSIONS, PRIZE VAULT and SETTINGS (and the gear)
 * open their screens, and the + buttons open GET MORE. The red "!" badges show only when something is waiting.
 * PLAY breathes, the portal pulses and the Guardian's eyes glow.
 */
class HomeScreen(private val app: App) {
    private val playImg = app.pf.loadImage("home/play.png")
    private val btnImg = arrayOf(
        app.pf.loadImage("home/btn_daily.png"), app.pf.loadImage("home/btn_missions.png"),
        app.pf.loadImage("home/btn_vault.png"), app.pf.loadImage("home/btn_settings.png"))
    private val hudProfile = app.pf.loadImage("home/hud_profile.png")
    private val hudWallet = app.pf.loadImage("home/hud_wallet.png")
    private val hudGear = app.pf.loadImage("home/hud_gear.png")
    private val badgeImg = app.pf.loadImage("home/badge.png")
    private val tagImg = app.pf.loadImage("home/tagline.png")

    // ---- the artwork's pieces, in its pixels (design/home_reference.png, 1024 x 1536)
    /** Tile sprites [l, t, r, b]: daily, missions, vault, settings; left column 14..168, right column 860..1010. */
    private val tileArt = arrayOf(
        floatArrayOf(18f, 158f, 168f, 353f), floatArrayOf(14f, 364f, 163f, 528f),
        floatArrayOf(860f, 160f, 1010f, 355f), floatArrayOf(864f, 372f, 1010f, 528f))
    /** The red badge on the daily, missions and vault tiles (artwork pixels). */
    private val badgeArt = arrayOf(floatArrayOf(148f, 195f), floatArrayOf(143f, 395f), floatArrayOf(990f, 196f))
    /** The top bar: profile, wallet, gear, and the buttons inside the wallet / gear. */
    private val profileBox = floatArrayOf(8f, 2f, 346f, 118f)
    private val walletBox = floatArrayOf(452f, 12f, 918f, 90f)
    private val gearBox = floatArrayOf(920f, 8f, 1014f, 102f)
    private val coinPlusArt = floatArrayOf(616f, 16f, 676f, 84f)
    private val gemPlusArt = floatArrayOf(850f, 16f, 912f, 84f)
    private val gearHitArt = floatArrayOf(924f, 12f, 1010f, 98f)

    // ---- layout (screen pixels), recomputed when the size or the safe area changes
    private var laidOut = -1
    /** Top bar scale (screen px per artwork px) and where its three parts start. */
    private var kh = 1f; private var hudT = 0f; private var profL = 0f; private var walletL = 0f; private var gearL = 0f
    /** Tile scale and the two columns' positions. */
    private var kt = 1f; private var colT = 0f; private var leftColL = 0f; private var rightColL = 0f
    private var logoW = 0f; private var logoCY = 0f
    private var heroTop = 0f; private var heroBottom = 0f
    private val playR = FloatArray(4); private val tagR = FloatArray(4)
    /** Button rectangles as drawn [l, t, r, b], by id. */
    private val rects = Array(8) { FloatArray(4) }

    private var pressed = -1
    private val pop = FloatArray(8)
    private var action = -1; private var actionT = 0f
    private val q = FloatArray(8)

    private fun layout() {
        val m = app.safe
        if (laidOut == m.version) return
        laidOut = m.version
        val u = m.u; val sw = m.sw

        // ---- top bar: the artwork's profile / wallet / gear, as wide as the safe area allows (at most ~64dp tall)
        kh = min(sw / 1006f, 64f * u / 116f)
        hudT = m.t
        profL = m.l
        gearL = m.r - 94f * kh
        walletL = gearL - 2f * kh - 466f * kh
        setRect(GEAR, gx(gearHitArt[0]), hy(gearHitArt[1]), gx(gearHitArt[2]), hy(gearHitArt[3]))
        setRect(COIN_PLUS, wx(coinPlusArt[0]), hy(coinPlusArt[1]), wx(coinPlusArt[2]), hy(coinPlusArt[3]))
        setRect(GEM_PLUS, wx(gemPlusArt[0]), hy(gemPlusArt[1]), wx(gemPlusArt[2]), hy(gemPlusArt[3]))
        val hudBottom = hy(118f)

        // ---- solve the vertical stack; shrink the decorative parts first on short phones
        val logo = app.menuArt.logo
        val aspect = logo.h.toFloat() / logo.w
        val needHero = max(m.dp(150f), sw * 0.36f) * WorldPlate.GROUP_H
        var sc = 1f
        var gap = 1f; var bandH = 0f; var playW = 0f; var playH = 0f; var tagW = 0f; var tagH = 0f; var heroH = 0f
        while (true) {
            gap = max(0.55f, sc)
            val colW = max(m.dp(66f), min(sw * 0.215f, 100f * u) * sc)
            kt = colW / 154f
            val colH = 370f * kt
            logoW = min(sw - 2f * colW - 14f * u, 345f * u) * max(0.72f, sc)
            bandH = max(colH, logoW * aspect)
            playW = min(sw * 0.68f, 310f * u) * max(0.9f, sc)
            playH = max(m.dp(58f), playW * playImg.h / playImg.w)
            playW = playH * playImg.w / playImg.h
            tagW = min(sw * 0.78f, 330f * u) * max(0.85f, sc)
            tagH = tagW * tagImg.h / tagImg.w
            heroH = (m.b - tagH - 4f * u * gap - playH - 12f * u * gap) - (hudBottom + 10f * u * gap + bandH + 6f * u * gap)
            if (heroH >= needHero || sc <= 0.62f) break
            sc -= 0.04f
        }
        // bottom: the tagline on the safe bottom, PLAY above it
        tagR[0] = m.cx - tagW / 2; tagR[2] = m.cx + tagW / 2; tagR[3] = m.b; tagR[1] = m.b - tagH
        playR[0] = m.cx - playW / 2; playR[2] = m.cx + playW / 2; playR[3] = tagR[1] - 4f * u * gap; playR[1] = playR[3] - playH
        setRect(PLAY, playR[0], playR[1], playR[2], playR[3])
        // a tall phone: some of the spare height goes above the logo band, the rest shows world around the hero
        val heroMax = Scenery.heroHeight(Float.MAX_VALUE, m.w * 0.84f) * WorldPlate.GROUP_H
        val spare = max(0f, heroH - heroMax)
        val bandT = hudBottom + 10f * u * gap + spare * 0.3f
        logoCY = bandT + bandH / 2
        colT = bandT + (bandH - 370f * kt) / 2
        leftColL = m.l - 14f * kt
        rightColL = m.r - 1010f * kt
        for (i in 0..3) {
            val a = tileArt[i]
            setRect(DAILY + i, tx(i, a[0]), ty(a[1]), tx(i, a[2]), ty(a[3]))
        }
        heroTop = bandT + bandH + 6f * u * gap
        heroBottom = playR[1] - 12f * u * gap
    }

    // artwork -> screen, for the top bar parts and the tile columns
    private fun px(x: Float) = profL + (x - 8f) * kh
    private fun wx(x: Float) = walletL + (x - 452f) * kh
    private fun gx(x: Float) = gearL + (x - 920f) * kh
    private fun hy(y: Float) = hudT + (y - 2f) * kh
    private fun tx(i: Int, x: Float) = if (i < 2) leftColL + x * kt else rightColL + x * kt
    private fun ty(y: Float) = colT + (y - 158f) * kt
    private fun setRect(id: Int, l: Float, t: Float, r: Float, b: Float) { val a = rects[id]; a[0] = l; a[1] = t; a[2] = r; a[3] = b }

    fun update(dt: Float) {
        for (i in pop.indices) pop[i] = max(0f, pop[i] - dt * 4f)
        if (action >= 0) {
            actionT -= dt
            if (actionT <= 0f) {
                val a = action; action = -1
                when (a) {
                    PLAY -> app.openMap()
                    DAILY -> app.openPopup(Pop.DAILY)
                    MISSIONS -> app.openPopup(Pop.MISSIONS)
                    VAULT -> app.openPopup(Pop.VAULT)
                    SETTINGS, GEAR -> app.openPopup(Pop.SETTINGS)
                    COIN_PLUS, GEM_PLUS -> app.openPopup(Pop.MORE)
                }
            }
        }
    }

    fun render(gr: Gfx, interactive: Boolean) {
        layout()
        val m = app.safe
        val t = app.t
        val art = app.menuArt
        Scenery.world(gr, art, m, t, heroTop + (heroBottom - heroTop) * 0.22f)
        Scenery.hero(gr, art, heroTop, heroBottom, m.w * 0.84f, t)
        Kit.imageC(gr, art.logo, m.cx, logoCY + sin(t * 1.4f) * m.u * 1.5f, logoW)

        // the tiles, with their badges when something is waiting
        for (i in 0..3) sprite(gr, DAILY + i, btnImg[i], 1f)
        val pr = app.progress
        if (pr.dailyAvailable(app.today())) badge(gr, 0, t)
        if (pr.anyMissionClaimable) badge(gr, 1, t + 0.4f)
        if (Progress.CHESTS.indices.any { pr.chestReady(it) }) badge(gr, 2, t + 0.8f)

        // PLAY: a soft glow and a gentle breath; the tagline plank under it
        val pcx = (playR[0] + playR[2]) / 2; val pcy = (playR[1] + playR[3]) / 2
        gr.setAdditive(true)
        gr.glow(pcx, pcy, (playR[2] - playR[0]) * 0.62f, Col.withA(0xFF60FF60.toInt(), 0.10f + 0.07f * pulse(t, 2.4f)))
        gr.setAdditive(false)
        sprite(gr, PLAY, playImg, 1f + 0.025f * (0.5f + 0.5f * sin(t * 2.4f)))
        gr.image(tagImg, tagR[0], tagR[1], tagR[2] - tagR[0], tagR[3] - tagR[1])

        // the top bar with live values
        hudSprite(gr, hudProfile, px(profileBox[0]), hy(profileBox[1]), px(profileBox[2]), hy(profileBox[3]), -1)
        hudSprite(gr, hudWallet, wx(walletBox[0]), hy(walletBox[1]), wx(walletBox[2]), hy(walletBox[3]), -1)
        hudSprite(gr, hudGear, gx(gearBox[0]), hy(gearBox[1]), gx(gearBox[2]), hy(gearBox[3]), GEAR)
        if (pressed == COIN_PLUS || pressed == GEM_PLUS) {
            val b = rects[pressed]
            gr.fillRoundRect(b[0] + 6f * kh, b[1] + 7f * kh, b[2] - 4f * kh, b[3] - 6f * kh, 10f * kh, 0x44000000)
        }
        profileValues(gr, pr)
        walletValues(gr, pr)
    }

    /** "Level N" and the XP bar in the profile panel. */
    private fun profileValues(gr: Gfx, pr: Progress) {
        gr.text("Level ${pr.playerLevel}", px(138f), hy(45f), 38f * kh, Font.TITLE, Col.WHITE, Align.LEFT, 5f * kh, 0xFF0A1E4A.toInt())
        val l = px(143f); val r = px(313f); val t = hy(71f); val b = hy(85f)
        gr.fillRoundRect(l - 1f * kh, t - 1f * kh, r + 1f * kh, b + 1f * kh, (b - t) * 0.5f + kh, 0xFF3A2410.toInt())
        val u = pr.levelXp.toFloat() / Progress.XP_PER_LEVEL
        if (u > 0f) {
            val right = l + (r - l) * u
            gr.fillRoundRect(l, t, max(right, l + (b - t)), b, (b - t) * 0.5f, 0xFFFFB524.toInt())
            gr.fillRoundRect(l + 2f * kh, t + 1.5f * kh, max(right, l + (b - t)) - 2f * kh, (t + b) * 0.5f, (b - t) * 0.3f, 0x55FFF4C0)
        }
        gr.text("${pr.levelXp}/${Progress.XP_PER_LEVEL}", px(228f), hy(78f), 22f * kh, Font.TITLE, Col.WHITE, Align.CENTER, 4f * kh, 0xFF2A1406.toInt())
    }

    /** Coins and gems in the wallet bar (shrunk to fit when the numbers grow long). */
    private fun walletValues(gr: Gfx, pr: Progress) {
        fitText(gr, Ui.fmt(pr.coins), 572f, 50f, 522f, 616f)
        fitText(gr, Ui.fmt(pr.gems), 802f, 50f, 752f, 850f)
    }
    private fun fitText(gr: Gfx, s: String, cx: Float, cy: Float, l: Float, r: Float) {
        var size = 36f * kh
        val room = (r - l) * kh
        val tw = gr.textWidth(s, size, Font.TITLE)
        if (tw > room) size *= room / tw
        val half = min(gr.textWidth(s, size, Font.TITLE), room) * 0.5f
        val x = clamp(wx(cx), wx(l) + half, wx(r) - half)
        gr.text(s, x, hy(cy), size, Font.TITLE, Col.WHITE, Align.CENTER, 4.5f * kh, 0xFF0A1E4A.toInt())
    }

    private fun hudSprite(gr: Gfx, img: Img, l: Float, t: Float, r: Float, b: Float, id: Int) {
        val held = id >= 0 && pressed == id
        val sw = if (id >= 0) sin(pop[id] * 3.14159f) else 0f
        val sc = 1f + 0.08f * sw - (if (held) 0.04f else 0f)
        quad((l + r) / 2, (t + b) / 2, (r - l) * sc, (b - t) * sc)
        gr.imageQuad(img, 0f, 0f, img.w.toFloat(), img.h.toFloat(), q, 1f, if (held) 0.8f else 1f)
    }

    private fun quad(cx: Float, cy: Float, w: Float, h: Float) {
        q[0] = cx - w / 2; q[1] = cy - h / 2; q[2] = cx + w / 2; q[3] = cy - h / 2
        q[4] = cx + w / 2; q[5] = cy + h / 2; q[6] = cx - w / 2; q[7] = cy + h / 2
    }

    /** A button sprite: darker while held, and a quick swell when released. */
    private fun sprite(gr: Gfx, id: Int, img: Img, scale0: Float) {
        val box = rects[id]
        val held = pressed == id
        val sw = sin(pop[id] * 3.14159f)
        val sc = scale0 * (1f + 0.07f * sw) * (if (held) 0.96f else 1f)
        val cx = (box[0] + box[2]) / 2; val cy = (box[1] + box[3]) / 2
        val bw = (box[2] - box[0]) * sc; val bh = (box[3] - box[1]) * sc
        quad(cx, cy, bw, bh)
        gr.imageQuad(img, 0f, 0f, img.w.toFloat(), img.h.toFloat(), q, 1f, if (held) 0.8f else 1f)
        if (sw > 0.01f) { gr.setAdditive(true); gr.glow(cx, cy, bw * 0.6f, Col.withA(0xFFFFFFFF.toInt(), 0.18f * sw)); gr.setAdditive(false) }
    }

    /** The artwork's red "!" badge on tile [i] (daily, missions, vault), gently pulsing. */
    private fun badge(gr: Gfx, i: Int, t: Float) {
        val a = badgeArt[i]
        val r = 30f * kt * (1f + 0.08f * sin(t * 6f))
        gr.image(badgeImg, tx(i, a[0]) - r, ty(a[1]) - r, r * 2f, r * 2f)
    }

    /**
     * The button under a finger: every button's touch area is at least 48dp; where padded areas overlap,
     * the nearest button wins.
     */
    private fun buttonAt(x: Float, y: Float): Int {
        layout()
        val min = app.safe.touch
        var best = -1; var bestD = Float.MAX_VALUE
        for (id in rects.indices) {
            val b = rects[id]
            val ex = max(0f, (min - (b[2] - b[0])) / 2); val ey = max(0f, (min - (b[3] - b[1])) / 2)
            if (x < b[0] - ex || x > b[2] + ex || y < b[1] - ey || y > b[3] + ey) continue
            val d = sq(x - (b[0] + b[2]) / 2) + sq(y - (b[1] + b[3]) / 2)
            if (d < bestD) { bestD = d; best = id }
        }
        return best
    }

    fun down(x: Float, y: Float) { if (action < 0) pressed = buttonAt(x, y) }
    fun move(x: Float, y: Float) { if (pressed >= 0 && buttonAt(x, y) != pressed) pressed = -1 }
    fun up(x: Float, y: Float) {
        val b = pressed; pressed = -1
        if (b < 0 || buttonAt(x, y) != b || action >= 0) return
        pop[b] = 1f
        app.pf.sound(if (b == PLAY) Sfx.GO else Sfx.CLICK, if (b == PLAY) 0.6f else 1f)
        app.pf.haptic(false)
        action = b; actionT = 0.14f
    }
    fun cancel() { pressed = -1 }

    /** Tests: the screen position of a button (see the ids below). */
    fun buttonCentre(id: Int, w: Float, h: Float): FloatArray {
        app.layout(w.toInt(), h.toInt()); layout()
        val b = rects[id]
        return floatArrayOf((b[0] + b[2]) / 2, (b[1] + b[3]) / 2)
    }

    /** Tests: every element's rectangle on screen [left, top, right, bottom], by name. */
    fun layoutRects(): List<Pair<String, FloatArray>> {
        layout()
        val a = app.menuArt.logo.h.toFloat() / app.menuArt.logo.w
        val hb = Scenery.heroHeight(heroBottom - heroTop, app.safe.w * 0.84f)
        val bl = -WorldPlate.GUARD_DX * hb
        return listOf(
            "profile" to floatArrayOf(px(profileBox[0]), hy(profileBox[1]), px(profileBox[2]), hy(profileBox[3])),
            "wallet" to floatArrayOf(wx(walletBox[0]), hy(walletBox[1]), wx(walletBox[2]), hy(walletBox[3])),
            "gear" to floatArrayOf(gx(gearBox[0]), hy(gearBox[1]), gx(gearBox[2]), hy(gearBox[3])),
            "daily" to rects[DAILY].copyOf(), "missions" to rects[MISSIONS].copyOf(),
            "vault" to rects[VAULT].copyOf(), "settings" to rects[SETTINGS].copyOf(),
            "logo" to floatArrayOf(app.safe.cx - logoW / 2, logoCY - logoW * a / 2, app.safe.cx + logoW / 2, logoCY + logoW * a / 2),
            "boy" to floatArrayOf(bl, heroBottom - hb, bl + WorldPlate.BOY_ASPECT * hb, heroBottom),
            "PLAY" to playR.copyOf(), "tagline" to tagR.copyOf(),
        )
    }

    companion object {
        const val PLAY = 0; const val DAILY = 1; const val MISSIONS = 2; const val VAULT = 3; const val SETTINGS = 4
        const val GEAR = 5; const val COIN_PLUS = 6; const val GEM_PLUS = 7
    }
}
