package com.blocktower.escape.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Home / Welcome screen: the approved Home artwork, shown whole on every phone shape (the plate adds sky
 * above and shadowed ground below it), brought to life with small touches that leave the artwork untouched:
 * the PLAY button breathes, gems and coins twinkle, the monster's eyes glow. The five buttons in the artwork
 * work: PLAY opens the level map, the four on the left open DAILY REWARDS, MISSIONS, PRIZE VAULT and SETTINGS.
 */
class HomeScreen(private val app: App) {
    private val plate = app.pf.loadImage("home/home_plate.jpg")
    private val playImg = app.pf.loadImage("home/play.png")
    private val btnImg = arrayOf(
        app.pf.loadImage("home/btn_daily.png"), app.pf.loadImage("home/btn_missions.png"),
        app.pf.loadImage("home/btn_vault.png"), app.pf.loadImage("home/btn_settings.png"))
    /** Where the artwork sits inside the plate, and the plate's size (plate pixels = artwork pixels). */
    private val plateArtX = 200f; private val plateArtY = 560f
    /** Buttons in artwork pixels: [sprite box l, t, r, b] (the sprite is cut exactly from this box). */
    private val playBox = floatArrayOf(250f, 1252f, 768f, 1448f)
    private val boxes = arrayOf(
        floatArrayOf(26f, 508f, 182f, 698f), floatArrayOf(12f, 696f, 186f, 878f),
        floatArrayOf(12f, 878f, 186f, 1058f), floatArrayOf(12f, 1057f, 183f, 1236f))
    /** Twinkles on the artwork's gems and coins, and the monster's eyes (artwork pixels). */
    private val gems = floatArrayOf(355f, 540f, 420f, 668f, 795f, 1195f, 982f, 1015f, 885f, 1375f, 105f, 1395f, 1003f, 700f, 645f, 700f)
    private val coins = floatArrayOf(352f, 462f, 690f, 715f, 935f, 1128f, 242f, 858f, 415f, 545f)
    private val eyes = floatArrayOf(800f, 575f, 920f, 540f)

    private var k = 1f; private var ox = 0f; private var oy = 0f
    private var pressed = -1
    private val pop = FloatArray(5)
    private var action = -1; private var actionT = 0f
    private val poly = FloatArray(16)

    private fun layout(w: Float, h: Float) {
        k = min(w / 1024f, h / 1536f)
        ox = (w - 1024f * k) * 0.5f
        val extra = h - 1536f * k
        // more sky above than ground below; the plate covers up to 560 px above and 360 px below
        oy = clamp(extra * 0.7f, extra - 360f * k, 560f * k)
    }
    private fun sx(x: Float) = ox + x * k
    private fun sy(y: Float) = oy + y * k

    fun update(dt: Float) {
        for (i in pop.indices) pop[i] = max(0f, pop[i] - dt * 4f)
        if (action >= 0) {
            actionT -= dt
            if (actionT <= 0f) {
                val a = action; action = -1
                when (a) {
                    0 -> app.openMap()
                    1 -> app.openPopup(Pop.DAILY)
                    2 -> app.openPopup(Pop.MISSIONS)
                    3 -> app.openPopup(Pop.VAULT)
                    4 -> app.openPopup(Pop.SETTINGS)
                }
            }
        }
    }

    fun render(gr: Gfx, interactive: Boolean) {
        val w = gr.width.toFloat(); val h = gr.height.toFloat()
        layout(w, h)
        val t = app.t
        gr.fillRect(0f, 0f, w, h, 0xFF0A1440.toInt())
        gr.image(plate, ox - plateArtX * k, oy - plateArtY * k, plate.w * k, plate.h * k)
        // the monster's eyes smoulder
        gr.setAdditive(true)
        for (i in 0..1) {
            val a = 0.25f + 0.2f * pulse(t + i * 0.7f, 2.2f)
            gr.glow(sx(eyes[i * 2]), sy(eyes[i * 2 + 1]), 46f * k, Col.withA(0xFFD070FF.toInt(), a))
        }
        // warm light around the crown
        gr.glow(sx(512f), sy(70f), 130f * k, Col.withA(0xFFFFD060.toInt(), 0.12f + 0.08f * pulse(t, 1.6f)))
        gr.setAdditive(false)
        // gems and coins twinkle now and then
        var i = 0
        while (i < gems.size) {
            val ph = fract(t * 0.35f + i * 0.137f)
            if (ph < 0.18f) gr.star4(sx(gems[i]) + 10f * k, sy(gems[i + 1]) - 12f * k, 20f * k * sin(ph / 0.18f * 3.14159f), 0xFFFFFFFF.toInt(), poly)
            i += 2
        }
        i = 0
        while (i < coins.size) {
            val ph = fract(t * 0.3f + i * 0.21f + 0.5f)
            if (ph < 0.15f) gr.star4(sx(coins[i]) - 8f * k, sy(coins[i + 1]) - 10f * k, 16f * k * sin(ph / 0.15f * 3.14159f), 0xFFFFF4C0.toInt(), poly)
            i += 2
        }
        // PLAY: a soft glow and a gentle breath (never smaller than the button in the artwork)
        val pb = playBox
        val pcx = sx((pb[0] + pb[2]) * 0.5f); val pcy = sy((pb[1] + pb[3]) * 0.5f)
        gr.setAdditive(true)
        gr.glow(pcx, pcy, 330f * k, Col.withA(0xFF60FF60.toInt(), 0.10f + 0.08f * pulse(t, 2.4f)))
        gr.setAdditive(false)
        val breathe = 1f + 0.025f * (0.5f + 0.5f * sin(t * 2.4f))
        drawSprite(gr, playImg, pb, 0, breathe)
        for (b in 0..3) drawSprite(gr, btnImg[b], boxes[b], b + 1, 1f)
        // badges: something waiting behind a button
        val pr = app.progress
        val today = app.today()
        if (pr.dailyAvailable(today)) app.ui.badge(gr, sx(boxes[0][2] - 14f), sy(boxes[0][1] + 30f), 20f * k, t)
        if (pr.anyMissionClaimable) app.ui.badge(gr, sx(boxes[1][2] - 14f), sy(boxes[1][1] + 20f), 20f * k, t)
        if (Progress.CHESTS.indices.any { pr.chestReady(it) }) app.ui.badge(gr, sx(boxes[2][2] - 14f), sy(boxes[2][1] + 20f), 20f * k, t)
    }

    /**
     * A button sprite drawn exactly over its place in the artwork: darker while held, and a quick
     * swell when released (always at least its own size, so the artwork under it never shows).
     */
    private fun drawSprite(gr: Gfx, img: Img, box: FloatArray, id: Int, scale0: Float) {
        val held = pressed == id
        val sw = sin(pop[id] * 3.14159f)
        val sc = scale0 * (1f + 0.07f * sw)
        if (!held && sc <= 1.0005f) return
        val cx = sx((box[0] + box[2]) * 0.5f); val cy = sy((box[1] + box[3]) * 0.5f)
        val bw = (box[2] - box[0]) * k * sc; val bh = (box[3] - box[1]) * k * sc
        val q = floatArrayOf(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2, cx - bw / 2, cy + bh / 2)
        gr.imageQuad(img, 0f, 0f, img.w.toFloat(), img.h.toFloat(), q, 1f, if (held) 0.8f else 1f)
        if (sw > 0.01f) { gr.setAdditive(true); gr.glow(cx, cy, bw * 0.6f, Col.withA(0xFFFFFFFF.toInt(), 0.18f * sw)); gr.setAdditive(false) }
    }

    private fun buttonAt(x: Float, y: Float): Int {
        val pad = 6f
        val pb = playBox
        if (x >= sx(pb[0] + pad) && x <= sx(pb[2] - pad) && y >= sy(pb[1] + pad) && y <= sy(pb[3] - pad)) return 0
        for (b in 0..3) {
            val bx = boxes[b]
            if (x >= sx(bx[0]) && x <= sx(bx[2]) && y >= sy(bx[1]) && y <= sy(bx[3])) return b + 1
        }
        return -1
    }

    fun down(x: Float, y: Float) { if (action < 0) pressed = buttonAt(x, y) }
    fun move(x: Float, y: Float) { if (pressed >= 0 && buttonAt(x, y) != pressed) pressed = -1 }
    fun up(x: Float, y: Float) {
        val b = pressed; pressed = -1
        if (b < 0 || buttonAt(x, y) != b || action >= 0) return
        pop[b] = 1f
        app.pf.sound(if (b == 0) Sfx.GO else Sfx.CLICK, if (b == 0) 0.6f else 1f)
        app.pf.haptic(false)
        action = b; actionT = 0.14f
    }
    fun cancel() { pressed = -1 }

    /** Tests: the screen position of a button (0 PLAY, 1 daily, 2 missions, 3 vault, 4 settings). */
    fun buttonCentre(b: Int, w: Float, h: Float): FloatArray {
        layout(w, h)
        val box = if (b == 0) playBox else boxes[b - 1]
        return floatArrayOf(sx((box[0] + box[2]) * 0.5f), sy((box[1] + box[3]) * 0.5f))
    }
}
