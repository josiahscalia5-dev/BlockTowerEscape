package com.blocktower.escape.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Home / Welcome screen, drawn from the approved Home artwork (design/home_reference.png; its assets are built
 * by design/home/build_home.py).
 *
 * The artwork is shown whole and undistorted on every display: it spans the full width (never cropped at the
 * sides, so the menu buttons and the monster always show) and fits between the safe-area insets. The top bar
 * (profile, level and XP, coins and gems with their + buttons, the settings gear) is pinned to the top of the
 * safe area and shows live values. A taller phone gets its spare height as sky between the top bar and the
 * title (and a little below the tagline, where the scene carries on); a wider screen (tablet, foldable) gets
 * soft mirrored scenery at the sides.
 *
 * Every button works: PLAY opens the level map, DAILY REWARDS, MISSIONS, PRIZE VAULT and SETTINGS (and the gear)
 * open their screens, and the + buttons open GET MORE (where coins and gems come from). The red "!" badges show
 * only when something is waiting. PLAY breathes, the portal pulses, coins twinkle and the monster's eyes glow.
 */
class HomeScreen(private val app: App) {
    private val plate = app.pf.loadImage("home/home_plate.jpg")
    private val playImg = app.pf.loadImage("home/play.png")
    private val btnImg = arrayOf(
        app.pf.loadImage("home/btn_daily.png"), app.pf.loadImage("home/btn_missions.png"),
        app.pf.loadImage("home/btn_vault.png"), app.pf.loadImage("home/btn_settings.png"))
    private val hudProfile = app.pf.loadImage("home/hud_profile.png")
    private val hudWallet = app.pf.loadImage("home/hud_wallet.png")
    private val hudGear = app.pf.loadImage("home/hud_gear.png")
    private val badgeImg = app.pf.loadImage("home/badge.png")

    /** Where the artwork sits inside the plate (plate pixels = artwork pixels). */
    private val plateArtX = 200f; private val plateArtY = 640f
    /** Beyond the plate: the sky's colour above it, the haze's colour below it. */
    private val skyTop = 0xFF0258C4.toInt()
    private val hazeBottom = 0xFFA6BBDE.toInt()
    /** Of the spare height on a tall phone, this share goes above the title (the rest under the tagline). */
    private val topShare = 0.85f

    // ---- the buttons, in artwork pixels
    private class Btn(val id: Int, val hit: FloatArray, val sprite: FloatArray?, val img: Img?, val onHud: Boolean)
    private val buttons = listOf(
        Btn(PLAY, floatArrayOf(275f, 1180f, 750f, 1345f), floatArrayOf(266f, 1172f, 760f, 1356f), playImg, false),
        Btn(DAILY, floatArrayOf(26f, 170f, 160f, 345f), floatArrayOf(18f, 158f, 168f, 353f), btnImg[0], false),
        Btn(MISSIONS, floatArrayOf(22f, 372f, 155f, 520f), floatArrayOf(14f, 364f, 163f, 528f), btnImg[1], false),
        Btn(VAULT, floatArrayOf(868f, 168f, 1002f, 347f), floatArrayOf(860f, 160f, 1010f, 355f), btnImg[2], false),
        Btn(SETTINGS, floatArrayOf(872f, 380f, 1002f, 520f), floatArrayOf(864f, 372f, 1010f, 528f), btnImg[3], false),
        Btn(GEAR, floatArrayOf(924f, 12f, 1010f, 98f), null, null, true),
        Btn(COIN_PLUS, floatArrayOf(616f, 16f, 676f, 84f), null, null, true),
        Btn(GEM_PLUS, floatArrayOf(850f, 16f, 912f, 84f), null, null, true),
    )
    /** The top bar's sprites: [left, top, right, bottom] in artwork pixels. */
    private val profileBox = floatArrayOf(8f, 2f, 346f, 118f)
    private val walletBox = floatArrayOf(452f, 12f, 918f, 90f)
    private val gearBox = floatArrayOf(920f, 8f, 1014f, 102f)
    /** Twinkles on the coins, the portal, the monster's eyes (artwork pixels). */
    private val coins = floatArrayOf(588f, 820f, 625f, 820f, 688f, 838f, 738f, 865f, 540f, 968f, 590f, 942f, 710f, 742f, 735f, 750f, 690f, 788f)
    private val eyes = floatArrayOf(215f, 690f, 280f, 705f)
    private val portalX = 700f; private val portalY = 600f

    private var k = 1f; private var ox = 0f; private var oy = 0f; private var hudY = 0f
    private var pressed = -1
    private val pop = FloatArray(8)
    private var action = -1; private var actionT = 0f
    private val poly = FloatArray(16)
    private val q = FloatArray(8)

    private fun layout(w: Float, h: Float) {
        val top = app.topInset; val bottom = app.bottomInset
        // the whole artwork, as large as fits between the sides and between the insets
        k = min(w / 1024f, (h - top - bottom) / 1536f)
        ox = (w - 1024f * k) * 0.5f
        val spare = h - top - bottom - 1536f * k
        oy = top + spare * topShare
        // the top bar stays at the top of the safe area
        hudY = top
    }
    private fun sx(x: Float) = ox + x * k
    private fun sy(y: Float) = oy + y * k
    private fun hy(y: Float) = hudY + y * k
    private fun bx(b: Btn, x: Float) = sx(x)
    private fun by(b: Btn, y: Float) = if (b.onHud) hy(y) else sy(y)

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
        val w = gr.width.toFloat(); val h = gr.height.toFloat()
        layout(w, h)
        val t = app.t
        // the plate: the artwork with sky above, the scene carrying on below and mirrored sides
        val plateTop = oy - plateArtY * k
        val plateBottom = plateTop + plate.h * k
        if (plateTop > 0f) gr.fillRect(0f, 0f, w, plateTop + 2f, skyTop)
        if (plateBottom < h) gr.fillRect(0f, plateBottom - 2f, w, h, hazeBottom)
        gr.image(plate, ox - plateArtX * k, plateTop, plate.w * k, plate.h * k)

        gr.setAdditive(true)
        // the portal pulses, the monster's eyes smoulder
        gr.glow(sx(portalX), sy(portalY), 120f * k, Col.withA(0xFFB070FF.toInt(), 0.16f + 0.12f * pulse(t, 1.8f)))
        gr.glow(sx(portalX), sy(portalY), 55f * k, Col.withA(0xFFE0C8FF.toInt(), 0.12f + 0.12f * pulse(t + 0.4f, 1.8f)))
        for (i in 0..1) gr.glow(sx(eyes[i * 2]), sy(eyes[i * 2 + 1]), 40f * k, Col.withA(0xFFD070FF.toInt(), 0.22f + 0.2f * pulse(t + i * 0.7f, 2.2f)))
        gr.setAdditive(false)
        // sparks drift out of the portal; coins twinkle now and then
        for (i in 0 until 6) {
            val ph = fract(t * 0.45f + i / 6f)
            val a = (i * 1.7f) + ph * 1.2f
            val r = (20f + 70f * ph) * k
            gr.star4(sx(portalX) + kotlin.math.cos(a) * r, sy(portalY) + sin(a) * r * 0.8f - ph * 20f * k, 9f * k * (1f - ph), 0xFFF0D8FF.toInt(), poly)
        }
        var i = 0
        while (i < coins.size) {
            val ph = fract(t * 0.3f + i * 0.173f + 0.5f)
            if (ph < 0.15f) gr.star4(sx(coins[i]) - 6f * k, sy(coins[i + 1]) - 10f * k, 15f * k * sin(ph / 0.15f * 3.14159f), 0xFFFFF4C0.toInt(), poly)
            i += 2
        }

        // PLAY: a soft glow and a gentle breath (never smaller than the button in the artwork)
        val pb = buttons[0].hit
        gr.setAdditive(true)
        gr.glow(sx((pb[0] + pb[2]) * 0.5f), sy((pb[1] + pb[3]) * 0.5f), 330f * k, Col.withA(0xFF60FF60.toInt(), 0.10f + 0.08f * pulse(t, 2.4f)))
        gr.setAdditive(false)
        for (b in buttons) if (b.img != null) drawSprite(gr, b, if (b.id == PLAY) 1f + 0.025f * (0.5f + 0.5f * sin(t * 2.4f)) else 1f)

        // badges: something is waiting behind that button
        val pr = app.progress
        if (pr.dailyAvailable(app.today())) badge(gr, sx(148f), sy(195f), t)
        if (pr.anyMissionClaimable) badge(gr, sx(143f), sy(395f), t + 0.4f)
        if (Progress.CHESTS.indices.any { pr.chestReady(it) }) badge(gr, sx(990f), sy(196f), t + 0.8f)

        // the top bar, pinned to the top of the safe area, with live values
        hudSprite(gr, hudProfile, profileBox, -1)
        hudSprite(gr, hudWallet, walletBox, -1)
        hudSprite(gr, hudGear, gearBox, GEAR)
        profileValues(gr, pr)
        walletValues(gr, pr)
    }

    /** "Level N" and the XP bar in the profile panel. */
    private fun profileValues(gr: Gfx, pr: Progress) {
        gr.text("Level ${pr.playerLevel}", sx(138f), hy(45f), 38f * k, Font.TITLE, Col.WHITE, Align.LEFT, 5f * k, 0xFF0A1E4A.toInt())
        val l = sx(143f); val r = sx(313f); val t = hy(71f); val b = hy(85f)
        val u = pr.levelXp.toFloat() / Progress.XP_PER_LEVEL
        if (u > 0f) {
            val right = l + (r - l) * u
            gr.fillRoundRect(l, t, max(right, l + (b - t)), b, (b - t) * 0.5f, 0xFFFFB524.toInt())
            gr.fillRoundRect(l + 2f * k, t + 1.5f * k, max(right, l + (b - t)) - 2f * k, (t + b) * 0.5f, (b - t) * 0.3f, 0x55FFF4C0)
        }
        gr.text("${pr.levelXp}/${Progress.XP_PER_LEVEL}", sx(228f), hy(78f), 22f * k, Font.TITLE, Col.WHITE, Align.CENTER, 4f * k, 0xFF2A1406.toInt())
    }

    /** Coins and gems in the wallet bar (shrunk to fit when the numbers grow long). */
    private fun walletValues(gr: Gfx, pr: Progress) {
        fitText(gr, Ui.fmt(pr.coins), 572f, 50f, 522f, 616f)
        fitText(gr, Ui.fmt(pr.gems), 802f, 50f, 752f, 850f)
    }
    private fun fitText(gr: Gfx, s: String, cx: Float, cy: Float, l: Float, r: Float) {
        var size = 36f * k
        val room = (r - l) * k
        val tw = gr.textWidth(s, size, Font.TITLE)
        if (tw > room) size *= room / tw
        // centred like the artwork's numbers, but kept inside the bar
        val half = min(gr.textWidth(s, size, Font.TITLE), room) * 0.5f
        val x = clamp(sx(cx), sx(l) + half, sx(r) - half)
        gr.text(s, x, hy(cy), size, Font.TITLE, Col.WHITE, Align.CENTER, 4.5f * k, 0xFF0A1E4A.toInt())
    }

    private fun hudSprite(gr: Gfx, img: Img, box: FloatArray, id: Int) {
        val held = id >= 0 && pressed == id
        val sw = if (id >= 0) sin(pop[id] * 3.14159f) else 0f
        val sc = 1f + 0.08f * sw - (if (held) 0.04f else 0f)
        val cx = sx((box[0] + box[2]) * 0.5f); val cy = hy((box[1] + box[3]) * 0.5f)
        val w = (box[2] - box[0]) * k * sc; val h = (box[3] - box[1]) * k * sc
        quad(cx, cy, w, h)
        gr.imageQuad(img, 0f, 0f, img.w.toFloat(), img.h.toFloat(), q, 1f, if (held) 0.8f else 1f)
        // the + buttons live inside the wallet sprite: darken them in place while held
        if (pressed == COIN_PLUS || pressed == GEM_PLUS) {
            val b = buttons.first { it.id == pressed }.hit
            if (img === hudWallet) gr.fillRoundRect(sx(b[0] + 6f), hy(b[1] + 7f), sx(b[2] - 4f), hy(b[3] - 6f), 10f * k, 0x44000000)
        }
    }

    private fun quad(cx: Float, cy: Float, w: Float, h: Float) {
        q[0] = cx - w / 2; q[1] = cy - h / 2; q[2] = cx + w / 2; q[3] = cy - h / 2
        q[4] = cx + w / 2; q[5] = cy + h / 2; q[6] = cx - w / 2; q[7] = cy + h / 2
    }

    /**
     * A button sprite drawn exactly over its place in the artwork: darker while held, and a quick
     * swell when released (always at least its own size, so the artwork under it never shows).
     */
    private fun drawSprite(gr: Gfx, b: Btn, scale0: Float) {
        val img = b.img ?: return
        val box = b.sprite ?: return
        val held = pressed == b.id
        val sw = sin(pop[b.id] * 3.14159f)
        val sc = scale0 * (1f + 0.07f * sw)
        if (!held && sc <= 1.0005f) return
        val cx = sx((box[0] + box[2]) * 0.5f); val cy = sy((box[1] + box[3]) * 0.5f)
        val bw = (box[2] - box[0]) * k * sc; val bh = (box[3] - box[1]) * k * sc
        quad(cx, cy, bw, bh)
        gr.imageQuad(img, 0f, 0f, img.w.toFloat(), img.h.toFloat(), q, 1f, if (held) 0.8f else 1f)
        if (sw > 0.01f) { gr.setAdditive(true); gr.glow(cx, cy, bw * 0.6f, Col.withA(0xFFFFFFFF.toInt(), 0.18f * sw)); gr.setAdditive(false) }
    }

    /** The artwork's red "!" badge, gently pulsing. */
    private fun badge(gr: Gfx, x: Float, y: Float, t: Float) {
        val r = 30f * k * (1f + 0.08f * sin(t * 6f))
        gr.image(badgeImg, x - r, y - r, r * 2f, r * 2f)
    }

    private fun buttonAt(x: Float, y: Float): Int {
        // the top bar first (it is drawn on top), then the artwork's buttons
        for (b in buttons.sortedByDescending { it.onHud }) {
            val h = b.hit
            val pad = if (b.id == PLAY) 6f else 0f
            if (x >= bx(b, h[0] + pad) && x <= bx(b, h[2] - pad) && y >= by(b, h[1] + pad) && y <= by(b, h[3] - pad)) return b.id
        }
        return -1
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
        layout(w, h)
        val b = buttons.first { it.id == id }
        val hb = b.hit
        return floatArrayOf(bx(b, (hb[0] + hb[2]) * 0.5f), by(b, (hb[1] + hb[3]) * 0.5f))
    }
    /** Tests: the artwork's rectangle on screen [left, top, right, bottom] and the top bar's bottom edge. */
    fun artRect(w: Float, h: Float): FloatArray { layout(w, h); return floatArrayOf(sx(0f), sy(0f), sx(1024f), sy(1536f), hy(118f)) }

    companion object {
        const val PLAY = 0; const val DAILY = 1; const val MISSIONS = 2; const val VAULT = 3; const val SETTINGS = 4
        const val GEAR = 5; const val COIN_PLUS = 6; const val GEM_PLUS = 7
    }
}
