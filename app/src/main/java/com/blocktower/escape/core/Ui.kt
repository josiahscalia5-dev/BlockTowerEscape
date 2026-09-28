package com.blocktower.escape.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Drawing helpers for the menus (level map, popups), in the same style as the in-game HUD:
 * navy panels in bright blue frames, chunky gradient buttons, the Lilita title font with dark outlines.
 * Buttons register their rectangle every frame; a tap is a press and release on the same button.
 */
class Ui(private val app: App) {
    val s get() = app.s

    // ---- hit testing
    private class Hit(val id: Int, val l: Float, val t: Float, val r: Float, val b: Float)
    private val hits = ArrayList<Hit>()
    private val nextHits = ArrayList<Hit>()
    var pressed = -1
        private set
    private var downX = 0f; private var downY = 0f

    fun beginFrame() { nextHits.clear() }
    fun endFrame() { hits.clear(); hits.addAll(nextHits) }
    fun hit(id: Int, l: Float, t: Float, r: Float, b: Float) { nextHits.add(Hit(id, l, t, r, b)) }
    fun find(x: Float, y: Float): Int {
        for (i in hits.indices.reversed()) { val h = hits[i]; if (x >= h.l && x <= h.r && y >= h.t && y <= h.b) return h.id }
        return -1
    }
    fun down(x: Float, y: Float): Int { pressed = find(x, y); downX = x; downY = y; return pressed }
    /** Cancels the press if the finger slid away (a scroll, not a tap). */
    fun move(x: Float, y: Float) { if (pressed >= 0 && len2(x - downX, y - downY) > 34f * s) pressed = -1 }
    /** Returns the tapped button id, or -1. */
    fun up(x: Float, y: Float): Int { val p = pressed; pressed = -1; return if (p >= 0 && find(x, y) == p) p else -1 }
    fun cancel() { pressed = -1 }
    /** Tests: the centre of button [id] as it was drawn in the last frame, or null when it is not on screen. */
    fun centreOf(id: Int): FloatArray? = hits.lastOrNull { it.id == id }?.let { floatArrayOf((it.l + it.r) * 0.5f, (it.t + it.b) * 0.5f) }

    // ---- shapes
    private val path = VPath()
    fun rr(gr: Gfx, l: Float, t: Float, r: Float, b: Float, rad: Float, c0: Int, c1: Int) {
        path.ops.clear(); path.native = null
        path.roundRect(l, t, r, b, rad)
        gr.fillPath(path, Linear(0f, t, 0f, b, intArrayOf(c0, c1)))
    }

    /** The HUD's panel: dark outline, bright blue frame, deep navy inside. */
    fun panel(gr: Gfx, l: Float, t: Float, r: Float, b: Float, frame0: Int = 0xFF3A9CFF.toInt(), frame1: Int = 0xFF1E62E6.toInt()) {
        gr.fillRoundRect(l - 3f * s, t - 3f * s, r + 3f * s, b + 3f * s, 36f * s, 0xFF06122E.toInt())
        rr(gr, l, t, r, b, 34f * s, frame0, frame1)
        rr(gr, l + 7f * s, t + 7f * s, r - 7f * s, b - 7f * s, 28f * s, 0xFF13306E.toInt(), 0xFF081640.toInt())
    }

    /** An inset card inside a panel (rows in lists). */
    fun card(gr: Gfx, l: Float, t: Float, r: Float, b: Float, highlight: Boolean = false) {
        gr.fillRoundRect(l, t, r, b, 18f * s, if (highlight) 0xFF1E4A9A.toInt() else 0xFF0C1E4E.toInt())
        gr.strokeRoundRect(l, t, r, b, 18f * s, 2.5f * s, if (highlight) 0xFFFFE14A.toInt() else 0x5580B8FF)
    }

    fun button(gr: Gfx, id: Int, cx: Float, cy: Float, bw: Float, bh: Float, label: String, c0: Int, c1: Int, enabled: Boolean = true, textSize: Float = 0.46f) {
        val press = if (pressed == id && enabled) 1f else 0f
        val dy = press * 4f * s
        val l = cx - bw / 2; val t = cy - bh / 2; val r = cx + bw / 2; val b = cy + bh / 2
        gr.fillRoundRect(l, t + 6f * s, r, b + 6f * s, bh * 0.35f, Col.scale(if (enabled) c1 else 0xFF4A5264.toInt(), 0.55f))
        rr(gr, l, t + dy, r, b + dy, bh * 0.35f, if (enabled) c0 else 0xFF6A7488.toInt(), if (enabled) c1 else 0xFF4A5264.toInt())
        gr.fillRoundRect(l + 8f * s, t + 5f * s + dy, r - 8f * s, cy - 4f * s + dy, bh * 0.3f, 0x30FFFFFF)
        gr.text(label, cx, cy + dy, bh * textSize, Font.TITLE, if (enabled) Col.WHITE else 0xFFC8D0E0.toInt(), Align.CENTER, 5f * s, 0x66000000)
        if (enabled) hit(id, l, t, r, b + 6f * s)
    }

    fun green(gr: Gfx, id: Int, cx: Float, cy: Float, bw: Float, bh: Float, label: String, enabled: Boolean = true) =
        button(gr, id, cx, cy, bw, bh, label, 0xFF5AE07A.toInt(), 0xFF1E9E48.toInt(), enabled)
    fun orange(gr: Gfx, id: Int, cx: Float, cy: Float, bw: Float, bh: Float, label: String, enabled: Boolean = true) =
        button(gr, id, cx, cy, bw, bh, label, 0xFFFFB84A.toInt(), 0xFFE0701A.toInt(), enabled)
    fun blue(gr: Gfx, id: Int, cx: Float, cy: Float, bw: Float, bh: Float, label: String, enabled: Boolean = true) =
        button(gr, id, cx, cy, bw, bh, label, 0xFF5AB6FF.toInt(), 0xFF1E62E6.toInt(), enabled)
    fun red(gr: Gfx, id: Int, cx: Float, cy: Float, bw: Float, bh: Float, label: String, enabled: Boolean = true) =
        button(gr, id, cx, cy, bw, bh, label, 0xFFFF7A6A.toInt(), 0xFFD02A2A.toInt(), enabled)

    /** Round red close button with an X. */
    fun closeButton(gr: Gfx, id: Int, cx: Float, cy: Float) {
        val r = 38f * s * (if (pressed == id) 0.92f else 1f)
        gr.fillCircle(cx, cy + 4f * s, r + 3f * s, 0xFF06122E.toInt())
        path.ops.clear(); path.native = null; path.circle(cx, cy, r)
        gr.fillPath(path, Linear(0f, cy - r, 0f, cy + r, intArrayOf(0xFFFF7A6A.toInt(), 0xFFC81E28.toInt())))
        val k = r * 0.38f
        gr.line(cx - k, cy - k, cx + k, cy + k, 8f * s, Col.WHITE)
        gr.line(cx - k, cy + k, cx + k, cy - k, 8f * s, Col.WHITE)
        hit(id, cx - r * 1.3f, cy - r * 1.3f, cx + r * 1.3f, cy + r * 1.3f)
    }

    /** Round blue back button with an arrow. */
    fun backButton(gr: Gfx, id: Int, cx: Float, cy: Float) {
        val r = 42f * s * (if (pressed == id) 0.92f else 1f)
        gr.fillCircle(cx, cy + 4f * s, r + 3f * s, 0xFF06122E.toInt())
        path.ops.clear(); path.native = null; path.circle(cx, cy, r)
        gr.fillPath(path, Linear(0f, cy - r, 0f, cy + r, intArrayOf(0xFF55B6FF.toInt(), 0xFF1650DC.toInt())))
        val k = r * 0.42f
        gr.line(cx - k, cy, cx + k, cy, 9f * s, Col.WHITE)
        gr.line(cx - k, cy, cx - k * 0.1f, cy - k * 0.8f, 9f * s, Col.WHITE)
        gr.line(cx - k, cy, cx - k * 0.1f, cy + k * 0.8f, 9f * s, Col.WHITE)
        hit(id, cx - r * 1.3f, cy - r * 1.3f, cx + r * 1.3f, cy + r * 1.3f)
    }

    fun title(gr: Gfx, text: String, x: Float, y: Float, size: Float, color: Int = 0xFFFFE14A.toInt(), a: Float = 1f) =
        gr.text(text, x, y, size * s, Font.TITLE, color, Align.CENTER, size * 0.1f * s, 0xFF1A0A20.toInt(), a)

    fun label(gr: Gfx, text: String, x: Float, y: Float, size: Float, color: Int = Col.WHITE, align: Int = Align.CENTER, a: Float = 1f) =
        gr.text(text, x, y, size * s, Font.UI, color, align, 0f, 0, a)

    private val starPath = VPath()
    fun star(gr: Gfx, x: Float, y: Float, r: Float, filled: Boolean) {
        starPath.ops.clear(); starPath.native = null
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            val rr = if (i % 2 == 0) r else r * 0.47f
            val px = x + cos(a) * rr; val py = y + sin(a) * rr
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        if (filled) {
            gr.fillPath(starPath, Linear(0f, y - r, 0f, y + r, intArrayOf(0xFFFFF4A0.toInt(), 0xFFFFC21A.toInt(), 0xFFE08A00.toInt())))
            gr.strokePath(starPath, max(1.5f, r * 0.12f), Solid(0xFF8A4A00.toInt()))
        } else {
            gr.fillPath(starPath, Solid(0xFF1A2650.toInt()))
            gr.strokePath(starPath, max(1.5f, r * 0.12f), Solid(0xFF3A4A80.toInt()))
        }
    }

    fun check(gr: Gfx, x: Float, y: Float, r: Float, color: Int) {
        gr.line(x - r, y, x - r * 0.3f, y + r * 0.7f, r * 0.45f, color)
        gr.line(x - r * 0.3f, y + r * 0.7f, x + r, y - r * 0.8f, r * 0.45f, color)
    }

    fun padlock(gr: Gfx, x: Float, y: Float, r: Float) {
        gr.arc(x, y - r * 0.55f, r * 0.55f, 180f, 180f, r * 0.26f, 0xFFB8C0D8.toInt())
        gr.fillRoundRect(x - r * 0.85f, y - r * 0.5f, x + r * 0.85f, y + r * 0.75f, r * 0.2f, 0xFFE8A824.toInt())
        gr.fillRoundRect(x - r * 0.85f, y - r * 0.5f, x + r * 0.85f, y - r * 0.2f, r * 0.2f, 0xFFFFD86A.toInt())
        gr.fillCircle(x, y + r * 0.08f, r * 0.17f, 0xFF5A3208.toInt())
        gr.fillRect(x - r * 0.07f, y + r * 0.08f, x + r * 0.07f, y + r * 0.45f, 0xFF5A3208.toInt())
    }

    /** Coins and gems pill (the HUD's counters). */
    fun wallet(gr: Gfx, rightX: Float, cy: Float, coins: Int, gems: Int) {
        val art = app.baseArt
        val h = 62f * s
        val gw = 150f * s; val cw = 200f * s
        val gl = rightX - gw; val cl = gl - 8f * s - cw
        gr.fillRoundRect(cl, cy - h / 2, cl + cw, cy + h / 2, 16f * s, 0xF50A1438.toInt())
        gr.fillRoundRect(gl, cy - h / 2, rightX, cy + h / 2, 16f * s, 0xF50A1438.toInt())
        val ci = art.coinIcon; val gi = art.gem
        val isz = 52f * s
        gr.image(ci, cl + 10f * s, cy - isz * 0.5f * ci.h / ci.w, isz, isz * ci.h / ci.w)
        gr.text(fmt(coins), cl + cw - 14f * s, cy, 36f * s, Font.UI, Col.WHITE, Align.RIGHT)
        gr.image(gi, gl + 10f * s, cy - isz * 0.5f * gi.h / gi.w, isz, isz * gi.h / gi.w)
        gr.text(fmt(gems), rightX - 14f * s, cy, 36f * s, Font.UI, Col.WHITE, Align.RIGHT)
    }

    /** Small red notification badge. */
    fun badge(gr: Gfx, x: Float, y: Float, r: Float, t: Float) {
        val k = 1f + 0.1f * sin(t * 6f)
        gr.fillCircle(x, y, (r + 3f * s) * k, 0xFF06122E.toInt())
        gr.fillCircle(x, y, r * k, 0xFFFF3A3A.toInt())
        gr.text("!", x, y + 1f * s, r * 1.5f, Font.TITLE, Col.WHITE)
    }

    fun coinsText(coins: Int, gems: Int): String = listOfNotNull(if (coins > 0) "${fmt(coins)} coins" else null, if (gems > 0) "$gems gems" else null).joinToString(" + ")

    companion object {
        fun fmt(v: Int): String {
            val s = kotlin.math.abs(v).toString()
            val sb = StringBuilder(if (v < 0) "-" else "")
            for (i in s.indices) { if (i > 0 && (s.length - i) % 3 == 0) sb.append(','); sb.append(s[i]) }
            return sb.toString()
        }
    }
}

/** Short-lived screen-space sparkle for menu rewards. */
class UiSpark { var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var life = 0f; var max = 1f; var size = 1f; var color = 0; var kind = 0 }

fun Gfx.star4(x: Float, y: Float, r: Float, color: Int, poly: FloatArray) {
    if (r < 0.8f) return
    val w = r * 0.28f
    poly[0] = x; poly[1] = y - r; poly[2] = x + w; poly[3] = y - w; poly[4] = x + r; poly[5] = y; poly[6] = x + w; poly[7] = y + w
    poly[8] = x; poly[9] = y + r; poly[10] = x - w; poly[11] = y + w; poly[12] = x - r; poly[13] = y; poly[14] = x - w; poly[15] = y - w
    setAdditive(true)
    glow(x, y, r * 0.9f, Col.withA(color, 0.5f))
    setAdditive(false)
    fillPoly(poly, 8, color)
}
