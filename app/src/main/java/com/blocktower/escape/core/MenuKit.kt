package com.blocktower.escape.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Android safe area and density for the menu screens (Splash, Home, level map).
 * [l]/[t]/[r]/[b] is the safe rectangle: the insets the host reports (status and navigation bars as if shown,
 * camera cut-out, gesture strip, rounded display corners) plus a small margin. Buttons and text stay inside it;
 * the artwork behind them runs to the physical edges.
 */
class Safe {
    var w = 1080f; var h = 2340f
    /** Screen pixels per dp. */
    var density = 2.625f
    var l = 0f; var t = 0f; var r = 0f; var b = 0f
    /** Design unit: 1dp on a ~390 x 800dp phone, a little more on big screens (tablets), never below 0.84dp. */
    var u = 2.625f
    var version = 0
    private val key = FloatArray(7)

    fun set(width: Float, height: Float, dens: Float, il: Float, it: Float, ir: Float, ib: Float) {
        if (key[0] == width && key[1] == height && key[2] == dens && key[3] == il && key[4] == it && key[5] == ir && key[6] == ib) return
        key[0] = width; key[1] = height; key[2] = dens; key[3] = il; key[4] = it; key[5] = ir; key[6] = ib
        w = width; h = height; density = max(dens, 0.5f)
        l = il + dp(12f); r = w - ir - dp(12f)
        t = it + dp(8f); b = h - ib - dp(10f)
        u = density * clamp(min(sw / density / 390f, sh / density / 800f), 0.84f, 1.5f)
        version++
    }

    fun dp(v: Float) = v * density
    val sw get() = r - l
    val sh get() = b - t
    val cx get() = w * 0.5f
    /** Android's minimum comfortable touch target (48dp). */
    val touch get() = 48f * density
}

/** Drawing helpers for the menu screens, in the HUD's style (Lilita One with dark outlines, navy pills). */
object Kit {
    const val NAVY = 0xFF0A1A44.toInt()
    const val NAVY_EDGE = 0xFF06122E.toInt()
    private val path = VPath()

    /** Rounded rect with a vertical 2-colour gradient. */
    fun grad(gr: Gfx, l: Float, t: Float, r: Float, b: Float, rad: Float, c0: Int, c1: Int, alpha: Float = 1f) {
        if (r <= l || b <= t) return
        path.ops.clear(); path.native = null
        path.roundRect(l, t, r, b, min(rad, min(r - l, b - t) * 0.5f))
        gr.fillPath(path, Linear(0f, t, 0f, b, intArrayOf(c0, c1)), alpha)
    }

    /** Text with a dark outline and a soft drop shadow, vertically centred on [y]. */
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

    /** Draws [img] with its centre at (cx, cy) and width [w]. */
    fun imageC(gr: Gfx, img: Img, cx: Float, cy: Float, w: Float, alpha: Float = 1f) {
        val h = w * img.h / img.w
        gr.image(img, cx - w * 0.5f, cy - h * 0.5f, w, h, alpha)
    }

    /** A navy pill label; returns its width. [sub] is an optional smaller second line (yellow). */
    fun pill(gr: Gfx, text: String, x: Float, cy: Float, size: Float, align: Int, color: Int, bg: Int, u: Float,
             minL: Float, maxR: Float, sub: String? = null, alpha: Float = 1f): Float {
        val subSize = size * 0.8f
        val tw = max(gr.textWidth(text, size, Font.UI), if (sub != null) gr.textWidth(sub, subSize, Font.UI) else 0f)
        val padX = size * 0.55f
        val ph = size * 1.45f + (if (sub != null) subSize * 1.1f else 0f)
        val bw = tw + padX * 2
        var l = when (align) { Align.CENTER -> x - bw / 2; Align.RIGHT -> x - bw; else -> x }
        l = clamp(l, minL, maxR - bw)
        val r = l + bw
        val t = cy - ph / 2; val b = cy + ph / 2
        gr.fillRoundRect(l - 1.5f * u, t - 1.5f * u, r + 1.5f * u, b + 1.5f * u, size * 0.8f, Col.mulA(0xCC06122E.toInt(), alpha))
        gr.fillRoundRect(l, t, r, b, size * 0.72f, Col.mulA(bg, alpha))
        if (sub == null) gr.text(text, (l + r) / 2, cy, size, Font.UI, color, Align.CENTER, 0f, 0, alpha)
        else {
            gr.text(text, (l + r) / 2, t + size * 0.78f, size, Font.UI, color, Align.CENTER, 0f, 0, alpha)
            gr.text(sub, (l + r) / 2, b - subSize * 0.8f, subSize, Font.UI, 0xFFFFE9A8.toInt(), Align.CENTER, 0f, 0, alpha)
        }
        return bw
    }
}

/** The Splash / Home artwork (the approved reference screens): the world, the hero boy and the Guardian. */
class MenuArt(p: Platform) {
    /** The shared floating-island world (castle, portal, block stairs) with nothing in front of it. */
    val world = p.loadImage("menu/world.jpg")
    /** The same cartoon boy as in the game, running (front view). */
    val boyRun = p.loadImage("menu/boy_run.png")
    val guardian = p.loadImage("menu/guardian.png")
    val logo = p.loadImage("home/logo.png")
    val swirl = p.loadImage("emb/portal_swirl.png")
}

/** Geometry of menu/world.jpg in its native units (image width = [W]). */
object WorldPlate {
    const val W = 484f
    const val H = 1184f
    const val PORTAL_X = 390f
    const val PORTAL_Y = 638f

    /** Hero group, measured on the reference splash with the boy's height as the unit. */
    const val BOY_ASPECT = 1000f / 1132f
    const val GUARD_W = 0.7247f
    const val GUARD_H = GUARD_W * 668f / 820f
    /** Guardian top / left relative to the boy's top-left. */
    const val GUARD_DY = -0.341f
    const val GUARD_DX = -0.105f
    const val GROUP_H = 1f - GUARD_DY
    const val GROUP_W = 0.989f
}

/**
 * The shared world backdrop and hero group (Splash and Home). The world covers the whole screen edge to edge,
 * undistorted: a taller phone shows more sky, a shorter one crops sky first, a wider one crops the sides.
 */
object Scenery {
    /** [portalY] = the preferred screen y of the portal. */
    fun world(gr: Gfx, art: MenuArt, s: Safe, t: Float, portalY: Float) {
        val img = art.world
        val k = max(s.w / WorldPlate.W, s.h / WorldPlate.H)
        val pw = WorldPlate.W * k; val ph = WorldPlate.H * k
        val x0 = (s.w - pw) * 0.65f
        val y0 = clamp(portalY - WorldPlate.PORTAL_Y * k, s.h - ph, 0f)
        gr.image(img, x0, y0, pw, ph)
        portal(gr, art, x0 + WorldPlate.PORTAL_X * k, y0 + WorldPlate.PORTAL_Y * k, 26f * k, t)
    }

    /** Animated glow and swirl over a painted portal. */
    fun portal(gr: Gfx, art: MenuArt, x: Float, y: Float, r: Float, t: Float) {
        gr.setAdditive(true)
        gr.glow(x, y, r * (2.2f + 0.25f * pulse(t, 2.2f)), Col.withA(0xFFB060FF.toInt(), 0.3f))
        gr.save(); gr.translate(x, y); gr.rotate(-t * 70f)
        gr.image(art.swirl, -r, -r, r * 2f, r * 2f, 0.22f + 0.1f * pulse(t, 3f))
        gr.restore()
        gr.glow(x, y, r * 0.8f, Col.withA(0xFFE8F4FF.toInt(), 0.25f + 0.15f * pulse(t, 4f)))
        gr.setAdditive(false)
    }

    /** Boy height that fits the hero group into a box of height [h] and width [w]. */
    fun heroHeight(h: Float, w: Float) = max(0f, min(h / WorldPlate.GROUP_H, w / WorldPlate.GROUP_W))

    /**
     * The Guardian chasing the running boy, the boy's feet on [bottom]; the Guardian bleeds off the left screen edge
     * as in the reference art. [intro] 0..1 slides them in. Returns the boy's rectangle [l, t, r, b].
     */
    fun hero(gr: Gfx, art: MenuArt, top: Float, bottom: Float, maxW: Float, t: Float, intro: Float = 1f): FloatArray {
        val hb = heroHeight(bottom - top, maxW)
        if (hb <= 0f) return floatArrayOf(0f, 0f, 0f, 0f)
        val ia = smooth(intro)
        val bob = sin(t * 9f) * hb * 0.012f
        val boyL = -WorldPlate.GUARD_DX * hb
        val boyT = bottom - hb
        val gy = boyT + WorldPlate.GUARD_DY * hb + sin(t * 1.6f) * hb * 0.012f
        val gw = WorldPlate.GUARD_W * hb; val gh = WorldPlate.GUARD_H * hb
        // the Guardian: purple aura, body, smouldering eyes
        gr.setAdditive(true)
        gr.glow(gw * 0.5f, gy + gh * 0.5f, gw * 0.75f, Col.withA(0xFF9A3CFF.toInt(), 0.25f * ia))
        gr.setAdditive(false)
        gr.image(art.guardian, -(1f - ia) * gw * 0.3f, gy, gw, gh, ia)
        gr.setAdditive(true)
        val eye = 0.55f + 0.45f * pulse(t, 3.2f)
        gr.glow(gw * 0.47f, gy + gh * 0.47f, gw * 0.09f, Col.withA(0xFFE070FF.toInt(), 0.45f * eye * ia))
        gr.glow(gw * 0.68f, gy + gh * 0.58f, gw * 0.08f, Col.withA(0xFFE070FF.toInt(), 0.45f * eye * ia))
        gr.setAdditive(false)
        // the boy
        val bw = WorldPlate.BOY_ASPECT * hb
        val bx = boyL + (1f - ia) * bw * 0.25f
        gr.image(art.boyRun, bx, boyT + bob, bw, hb, ia)
        return floatArrayOf(boyL, boyT, boyL + bw, bottom)
    }
}
