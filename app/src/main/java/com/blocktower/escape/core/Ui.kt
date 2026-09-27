package com.blocktower.escape.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen size, Android safe area and density shared by the menu screens.
 * [l]/[t]/[r]/[b] is the safe rectangle: system bars, display cutout and rounded corners
 * (reported by the host as insets) plus a small design margin. Critical UI stays inside it;
 * artwork may bleed to the physical edges.
 */
class Metrics {
    var w = 1080f; var h = 2400f
    /** px per dp */
    var density = 2.625f
    var insL = 0f; var insT = 0f; var insR = 0f; var insB = 0f
    var l = 0f; var t = 0f; var r = 0f; var b = 0f
    /** Design unit: 1dp on a ~390 x 800dp phone; grows on big screens (tablets), never below 0.84dp. */
    var u = 2.625f
    var version = 0

    fun set(width: Float, height: Float, dens: Float, il: Float, it: Float, ir: Float, ib: Float) {
        if (width == w && height == h && dens == density && il == insL && it == insT && ir == insR && ib == insB) return
        w = width; h = height; density = max(dens, 0.5f)
        insL = il; insT = it; insR = ir; insB = ib
        l = insL + dp(12f); r = w - insR - dp(12f)
        t = insT + dp(8f); b = h - insB - dp(10f)
        u = density * clamp(min(sw / density / 390f, sh / density / 800f), 0.84f, 1.5f)
        version++
    }

    fun dp(v: Float) = v * density
    val sw get() = r - l
    val sh get() = b - t
    val cx get() = w * 0.5f
    /** Minimum comfortable Android touch target (48dp). */
    val touch get() = 48f * density
}

/** A tappable area. The visual rect can be smaller than the hit rect, which is always >= 48dp. */
class Btn(val id: Int) {
    var l = 0f; var t = 0f; var r = 0f; var b = 0f
    private var hl = 0f; private var ht = 0f; private var hr = 0f; private var hb = 0f
    var visible = true
    var press = 0f
    var held = false

    fun place(l: Float, t: Float, r: Float, b: Float, minHit: Float) {
        this.l = l; this.t = t; this.r = r; this.b = b
        val ex = max(0f, (minHit - (r - l)) * 0.5f); val ey = max(0f, (minHit - (b - t)) * 0.5f)
        hl = l - ex; hr = r + ex; ht = t - ey; hb = b + ey
    }
    fun hitRect(l: Float, t: Float, r: Float, b: Float) { hl = l; ht = t; hr = r; hb = b }
    fun contains(x: Float, y: Float) = visible && x >= hl && x <= hr && y >= ht && y <= hb
    val cx get() = (l + r) * 0.5f
    val cy get() = (t + b) * 0.5f
    val bw get() = r - l
    val bh get() = b - t
    /** Press feedback scale. */
    val k get() = 1f - 0.07f * press
}

/** Standard mobile button behaviour: highlight on press, fire on release inside. */
class Buttons {
    val list = ArrayList<Btn>()
    private var down: Btn? = null
    private var pointer = -1

    fun add(id: Int) = Btn(id).also { list.add(it) }

    fun touchDown(id: Int, x: Float, y: Float): Boolean {
        if (down != null) return false
        // last added is drawn on top, so test in reverse
        for (i in list.indices.reversed()) {
            val bt = list[i]
            if (bt.contains(x, y)) { down = bt; pointer = id; bt.held = true; return true }
        }
        return false
    }

    fun touchMove(id: Int, x: Float, y: Float) {
        val d = down ?: return
        if (id == pointer) d.held = d.contains(x, y)
    }

    /** Returns the clicked button, if the finger was released on it. */
    fun touchUp(id: Int, x: Float, y: Float): Btn? {
        val d = down ?: return null
        if (id != pointer) return null
        down = null; pointer = -1
        val hit = d.held && d.contains(x, y)
        d.held = false
        return if (hit) d else null
    }

    fun cancel() { down?.held = false; down = null; pointer = -1 }

    fun update(dt: Float) {
        for (bt in list) bt.press = if (bt.held) min(1f, bt.press + dt * 14f) else max(0f, bt.press - dt * 8f)
    }
}

/** Drawing helpers matching the gameplay HUD style (blue / green glossy buttons, navy pills, Lilita One). */
object Ui {
    const val NAVY = 0xFF0A1A44.toInt()
    const val NAVY_EDGE = 0xFF06122E.toInt()
    const val PILL = 0xD90A1438.toInt()
    const val GOLD = 0xFFFFD23A.toInt()

    private val path = VPath()
    private val tp = FloatArray(8)

    /** Rounded rect with a vertical 2-colour gradient (same as the gameplay HUD). */
    fun grad(gr: Gfx, l: Float, t: Float, r: Float, b: Float, rad: Float, c0: Int, c1: Int, alpha: Float = 1f) {
        if (r <= l || b <= t) return
        path.ops.clear(); path.native = null
        path.roundRect(l, t, r, b, min(rad, min(r - l, b - t) * 0.5f))
        gr.fillPath(path, Linear(0f, t, 0f, b, intArrayOf(c0, c1)), alpha)
    }

    fun gradCircle(gr: Gfx, cx: Float, cy: Float, rad: Float, c0: Int, c1: Int) {
        path.ops.clear(); path.native = null
        path.circle(cx, cy, rad)
        gr.fillPath(path, Linear(0f, cy - rad, 0f, cy + rad, intArrayOf(c0, c1)))
    }

    /** Glossy button body: dark rim, drop shadow, gradient face and top gloss. */
    fun glossy(gr: Gfx, l: Float, t: Float, r: Float, b: Float, rad: Float, top: Int, bottom: Int, rim: Int, u: Float) {
        val depth = max(2f, (b - t) * 0.07f)
        gr.fillRoundRect(l, t + depth, r, b + depth, rad, Col.withA(Col.scale(rim, 0.7f), 0.9f))
        gr.fillRoundRect(l - 1.5f * u, t - 1.5f * u, r + 1.5f * u, b + 1.5f * u, rad + 1.5f * u, rim)
        grad(gr, l, t, r, b, rad, top, bottom)
        val gi = max(2f, (b - t) * 0.08f)
        grad(gr, l + gi, t + gi * 0.7f, r - gi, (t + b) * 0.5f, max(1f, rad - gi), 0x55FFFFFF, 0x10FFFFFF)
    }

    /** Blue square/tile button (gameplay pause-button style). */
    fun blueTile(gr: Gfx, l: Float, t: Float, r: Float, b: Float, rad: Float, u: Float) =
        glossy(gr, l, t, r, b, rad, 0xFF4FB2FF.toInt(), 0xFF1650DC.toInt(), 0xFF0B2F8C.toInt(), u)

    fun greenTile(gr: Gfx, l: Float, t: Float, r: Float, b: Float, rad: Float, u: Float) =
        glossy(gr, l, t, r, b, rad, 0xFF8AF25C.toInt(), 0xFF1EA83A.toInt(), 0xFF0C5A1C.toInt(), u)

    fun pill(gr: Gfx, l: Float, t: Float, r: Float, b: Float, u: Float) {
        val rad = (b - t) * 0.5f
        gr.fillRoundRect(l - 1.5f * u, t - 1.5f * u, r + 1.5f * u, b + 1.5f * u, rad + 1.5f * u, 0xE606122E.toInt())
        gr.fillRoundRect(l, t, r, b, rad, PILL)
        gr.fillRoundRect(l + rad * 0.5f, t + 1.5f * u, r - rad * 0.5f, t + (b - t) * 0.42f, rad * 0.4f, 0x14FFFFFF)
    }

    /** Text with a dark outline (and optional drop shadow), vertically centred on [y]. */
    fun text(gr: Gfx, s: String, x: Float, y: Float, size: Float, color: Int = Col.WHITE, font: Int = Font.TITLE,
             align: Int = Align.CENTER, outline: Int = NAVY, outlineW: Float = size * 0.1f, shadow: Boolean = true, alpha: Float = 1f) {
        if (shadow) gr.text(s, x, y + size * 0.08f, size, font, Col.mulA(outline, 0.8f), align, outlineW, Col.mulA(outline, 0.8f), alpha)
        gr.text(s, x, y, size, font, color, align, outlineW, outline, alpha)
    }

    /** Largest size <= [size] at which [s] fits in [maxW]. */
    fun fit(gr: Gfx, s: String, size: Float, font: Int, maxW: Float): Float {
        val w = gr.textWidth(s, size, font)
        return if (w <= maxW || w <= 0f) size else size * maxW / w
    }

    fun plus(gr: Gfx, cx: Float, cy: Float, size: Float, u: Float) {
        val h = size * 0.5f
        greenTile(gr, cx - h, cy - h, cx + h, cy + h, size * 0.22f, u)
        val a = size * 0.26f; val th = size * 0.085f
        gr.fillRoundRect(cx - a, cy - th, cx + a, cy + th, th, Col.WHITE)
        gr.fillRoundRect(cx - th, cy - a, cx + th, cy + a, th, Col.WHITE)
    }

    fun star(gr: Gfx, x: Float, y: Float, r: Float, filled: Boolean, dim: Float = 1f) {
        path.ops.clear(); path.native = null
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            val rr = if (i % 2 == 0) r else r * 0.48f
            val px = x + cos(a) * rr; val py = y + sin(a) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        if (filled) {
            gr.fillPath(path, Linear(0f, y - r, 0f, y + r, intArrayOf(0xFFFFF4A0.toInt(), 0xFFFFC21A.toInt(), 0xFFE08A00.toInt())), dim)
            gr.strokePath(path, max(1f, r * 0.14f), Solid(0xFF7A3E00.toInt()), dim)
        } else {
            gr.fillPath(path, Solid(0xFF16245A.toInt()), 0.92f * dim)
            gr.strokePath(path, max(1f, r * 0.12f), Solid(0xFF0A1030.toInt()), 0.9f * dim)
        }
    }

    /** Gold padlock centred on (cx, cy); [s] = overall height. */
    fun lock(gr: Gfx, cx: Float, cy: Float, s: Float) {
        val bw = s * 0.78f; val bh = s * 0.56f
        val bt = cy - s * 0.5f + s * 0.44f
        // shackle
        gr.arc(cx, bt, bw * 0.3f, 180f, 180f, s * 0.13f, 0xFF3A2A10.toInt())
        gr.arc(cx, bt, bw * 0.3f, 180f, 180f, s * 0.08f, 0xFFD8DCE8.toInt())
        gr.line(cx - bw * 0.3f, bt, cx - bw * 0.3f, bt + s * 0.05f, s * 0.08f, 0xFFD8DCE8.toInt())
        gr.line(cx + bw * 0.3f, bt, cx + bw * 0.3f, bt + s * 0.05f, s * 0.08f, 0xFFD8DCE8.toInt())
        // body
        gr.fillRoundRect(cx - bw * 0.5f - s * 0.04f, bt - s * 0.04f, cx + bw * 0.5f + s * 0.04f, bt + bh + s * 0.04f, s * 0.14f, 0xFF3A2A10.toInt())
        grad(gr, cx - bw * 0.5f, bt, cx + bw * 0.5f, bt + bh, s * 0.11f, 0xFFFFE070.toInt(), 0xFFE09A10.toInt())
        gr.fillRoundRect(cx - bw * 0.38f, bt + bh * 0.1f, cx + bw * 0.38f, bt + bh * 0.34f, s * 0.05f, 0x55FFFFFF)
        gr.fillCircle(cx, bt + bh * 0.45f, s * 0.07f, 0xFF4A300A.toInt())
        tp[0] = cx - s * 0.035f; tp[1] = bt + bh * 0.45f; tp[2] = cx + s * 0.035f; tp[3] = bt + bh * 0.45f
        tp[4] = cx + s * 0.05f; tp[5] = bt + bh * 0.78f; tp[6] = cx - s * 0.05f; tp[7] = bt + bh * 0.78f
        gr.fillPoly(tp, 4, 0xFF4A300A.toInt())
    }

    /** Red notification badge with "!". */
    fun badge(gr: Gfx, cx: Float, cy: Float, r: Float, t: Float) {
        val k = 1f + 0.06f * sin(t * 5f)
        val rr = r * k
        gr.fillCircle(cx, cy + r * 0.08f, rr + r * 0.12f, 0x66000000)
        gr.fillCircle(cx, cy, rr + r * 0.14f, Col.WHITE)
        gradCircle(gr, cx, cy, rr, 0xFFFF5A4A.toInt(), 0xFFD0101A.toInt())
        gr.fillRoundRect(cx - rr * 0.13f, cy - rr * 0.6f, cx + rr * 0.13f, cy + rr * 0.18f, rr * 0.12f, Col.WHITE)
        gr.fillCircle(cx, cy + rr * 0.45f, rr * 0.14f, Col.WHITE)
    }

    /** Wooden plank (tagline). */
    fun plank(gr: Gfx, l: Float, t: Float, r: Float, b: Float, u: Float) {
        val rad = (b - t) * 0.32f
        gr.fillRoundRect(l, t + (b - t) * 0.1f, r, b + (b - t) * 0.1f, rad, 0x66000000)
        gr.fillRoundRect(l - 1.5f * u, t - 1.5f * u, r + 1.5f * u, b + 1.5f * u, rad + 1.5f * u, 0xFF3A1A06.toInt())
        grad(gr, l, t, r, b, rad, 0xFF9A5424.toInt(), 0xFF5E2C0C.toInt())
        val gh = (b - t)
        gr.line(l + rad, t + gh * 0.3f, r - rad * 2f, t + gh * 0.3f, max(1f, gh * 0.03f), 0x33FFD8A0)
        gr.line(l + rad * 2f, t + gh * 0.7f, r - rad, t + gh * 0.7f, max(1f, gh * 0.03f), 0x33000000)
        gr.fillRoundRect(l + 2f * u, t + 2f * u, r - 2f * u, t + gh * 0.18f, rad * 0.5f, 0x22FFE0B0)
        // leaf tufts on the ends
        leaf(gr, l + gh * 0.1f, t + gh * 0.15f, gh * 0.42f, -35f)
        leaf(gr, l + gh * 0.32f, t - gh * 0.05f, gh * 0.34f, 10f)
        leaf(gr, r - gh * 0.1f, b - gh * 0.1f, gh * 0.42f, 150f)
        leaf(gr, r - gh * 0.36f, b + gh * 0.02f, gh * 0.32f, 190f)
    }

    private fun leaf(gr: Gfx, x: Float, y: Float, s: Float, deg: Float) {
        gr.save(); gr.translate(x, y); gr.rotate(deg)
        path.ops.clear(); path.native = null
        path.moveTo(-s * 0.5f, 0f).quadTo(0f, -s * 0.42f, s * 0.5f, 0f).quadTo(0f, s * 0.42f, -s * 0.5f, 0f).close()
        gr.fillPath(path, Linear(0f, -s * 0.3f, 0f, s * 0.3f, intArrayOf(0xFF8CE85A.toInt(), 0xFF2E9A2A.toInt())))
        gr.strokePath(path, max(1f, s * 0.05f), Solid(0xFF14501A.toInt()))
        gr.restore()
    }

    /** Coin / gem counter: navy pill, icon overlapping its left end, value, green "+" button ([plus]). */
    fun currency(gr: Gfx, icon: Img, value: String, l: Float, r: Float, cy: Float, iconSize: Float, pillH: Float, plus: Btn, u: Float) {
        pill(gr, l, cy - pillH / 2, r, cy + pillH / 2, u)
        imageC(gr, icon, l, cy, iconSize)
        val vl = l + iconSize * 0.5f + 2f * u; val vr = plus.l - 3f * u
        val vs = fit(gr, value, pillH * 0.5f, Font.TITLE, vr - vl)
        text(gr, value, (vl + vr) / 2, cy, vs, Col.WHITE, Font.TITLE, Align.CENTER, NAVY, vs * 0.1f, false)
        plus(gr, plus.cx, plus.cy, plus.bw * plus.k, u)
    }

    fun fmt(v: Int): String {
        val s = v.toString()
        val sb = StringBuilder()
        for (i in s.indices) { if (i > 0 && (s.length - i) % 3 == 0) sb.append(','); sb.append(s[i]) }
        return sb.toString()
    }

    /** Draws [img] with its centre at (cx, cy) and width [w]. */
    fun imageC(gr: Gfx, img: Img, cx: Float, cy: Float, w: Float, alpha: Float = 1f) {
        val h = w * img.h / img.w
        gr.image(img, cx - w * 0.5f, cy - h * 0.5f, w, h, alpha)
    }

    /** Panel used by dialogs (same as the gameplay pause panel). */
    fun panel(gr: Gfx, l: Float, t: Float, r: Float, b: Float, u: Float) {
        gr.fillRoundRect(l - 3f * u, t - 3f * u, r + 3f * u, b + 3f * u, 30f * u, NAVY_EDGE)
        grad(gr, l, t, r, b, 28f * u, 0xFF3A9CFF.toInt(), 0xFF1E62E6.toInt())
        grad(gr, l + 6f * u, t + 6f * u, r - 6f * u, b - 6f * u, 23f * u, 0xFF13306E.toInt(), 0xFF081640.toInt())
    }
}
