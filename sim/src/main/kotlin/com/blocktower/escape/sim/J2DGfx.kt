package com.blocktower.escape.sim

import com.blocktower.escape.core.Align
import com.blocktower.escape.core.Fill
import com.blocktower.escape.core.Gfx
import com.blocktower.escape.core.Img
import com.blocktower.escape.core.Linear
import com.blocktower.escape.core.Radial
import com.blocktower.escape.core.Solid
import com.blocktower.escape.core.VPath
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Composite
import java.awt.CompositeContext
import java.awt.Font
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.Raster
import java.awt.image.WritableRaster
import java.io.File
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Additive blending (dst += src * alpha) for the renderer's glow passes. */
private class AddComposite(val extra: Float) : Composite {
    override fun createContext(srcCM: ColorModel, dstCM: ColorModel, hints: RenderingHints?): CompositeContext {
        val pre = srcCM.isAlphaPremultiplied
        val srcHasA = srcCM.hasAlpha()
        return object : CompositeContext {
            override fun dispose() {}
            override fun compose(src: Raster, dstIn: Raster, dstOut: WritableRaster) {
                val w = min(src.width, dstIn.width); val h = min(src.height, dstIn.height)
                val sb = src.numBands; val db = dstIn.numBands
                val s = IntArray(w * sb); val d = IntArray(w * db)
                for (y in 0 until h) {
                    src.getPixels(src.minX, src.minY + y, w, 1, s)
                    dstIn.getPixels(dstIn.minX, dstIn.minY + y, w, 1, d)
                    for (x in 0 until w) {
                        val a = if (srcHasA && sb >= 4) s[x * sb + 3] / 255f else 1f
                        val k = (if (pre) 1f else a) * extra
                        for (c in 0 until 3) {
                            val v = d[x * db + c] + (s[x * sb + c] * k).toInt()
                            d[x * db + c] = if (v > 255) 255 else v
                        }
                    }
                    dstOut.setPixels(dstOut.minX, dstOut.minY + y, w, 1, d)
                }
            }
        }
    }
}

/** [Gfx] on a Java2D BufferedImage. Perspective quads are drawn as subdivided affine triangles. */
class J2DGfx(assets: File, override val width: Int, override val height: Int) : Gfx {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    private val g: Graphics2D = image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }
    private val fonts = arrayOf(
        Font.createFont(Font.TRUETYPE_FONT, File(assets, "fonts/FiraSansCondensed-Bold.ttf")),
        Font.createFont(Font.TRUETYPE_FONT, File(assets, "fonts/LilitaOne-Regular.ttf"))
    )
    private var additive = false
    private val stack = ArrayDeque<Pair<AffineTransform, Shape?>>()
    private val tinted = HashMap<Long, BufferedImage>()

    private val soft = SoftRaster(image)
    /** Current transform and clip, for the software rasterizer. */
    fun currentTransform(): AffineTransform = g.transform
    fun currentClip(): Shape? = g.clip

    private val prof = HashMap<String, LongArray>()
    private inline fun <T> timed(k: String, f: () -> T): T { val t0 = System.nanoTime(); val r = f(); val a = prof.getOrPut(k) { LongArray(2) }; a[0] += System.nanoTime() - t0; a[1]++; return r }
    fun profileReset() = prof.clear()
    fun profileDump() { for ((k, v) in prof.entries.sortedByDescending { it.value[0] }) println("%-12s %8.1f ms  %6d calls".format(k, v[0] / 1e6 / 10, v[1] / 10)) }

    fun clear() { g.transform = AffineTransform(); g.clip = null; g.composite = AlphaComposite.SrcOver; g.color = Color.BLACK; g.fillRect(0, 0, width, height); additive = false; stack.clear() }

    private fun comp(alpha: Float): Composite =
        if (additive) AddComposite(alpha.coerceIn(0f, 1f)) else AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha.coerceIn(0f, 1f))

    private fun color(c: Int, alpha: Float = 1f): Color {
        val a = (((c ushr 24) and 255) * alpha.coerceIn(0f, 1f)).toInt()
        return Color((c shr 16) and 255, (c shr 8) and 255, c and 255, a)
    }

    override fun save() { stack.push(Pair(g.transform, g.clip)) }
    override fun restore() { val (t, c) = stack.pop(); g.transform = t; g.clip = c }
    override fun translate(dx: Float, dy: Float) { g.translate(dx.toDouble(), dy.toDouble()) }
    override fun scale(sx: Float, sy: Float) { g.scale(sx.toDouble(), sy.toDouble()) }
    override fun rotate(deg: Float) { g.rotate(Math.toRadians(deg.toDouble())) }
    override fun clipRect(l: Float, t: Float, r: Float, b: Float) { g.clip(Rectangle2D.Float(l, t, r - l, b - t)) }
    override fun setAdditive(on: Boolean) { additive = on }

    private fun bi(img: Img) = img.native as BufferedImage

    override fun image(img: Img, x: Float, y: Float, w: Float, h: Float, alpha: Float) = timed("image") { image0(img, x, y, w, h, alpha) }
        private val iq = FloatArray(8)
    private fun image0(img: Img, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        if (alpha <= 0.004f || w == 0f || h == 0f) return
        iq[0] = x; iq[1] = y; iq[2] = x + w; iq[3] = y; iq[4] = x + w; iq[5] = y + h; iq[6] = x; iq[7] = y + h
        if (soft.quad(this, img, 0f, 0f, img.w.toFloat(), img.h.toFloat(), iq, alpha, 1f, 0, 0f, additive)) return
        g.composite = comp(alpha)
        val b = bi(img)
        val at = AffineTransform(w.toDouble() / b.width, 0.0, 0.0, h.toDouble() / b.height, x.toDouble(), y.toDouble())
        g.drawImage(b, at, null)
        g.composite = AlphaComposite.SrcOver
    }

    // ------------------------------------------------------------------ perspective quads
    private val hx = FloatArray(9)
    private fun homography(q: FloatArray): Boolean {
        val x0 = q[0]; val y0 = q[1]; val x1 = q[2]; val y1 = q[3]; val x2 = q[4]; val y2 = q[5]; val x3 = q[6]; val y3 = q[7]
        val dx3 = x0 - x1 + x2 - x3; val dy3 = y0 - y1 + y2 - y3
        if (abs(dx3) < 1e-3f && abs(dy3) < 1e-3f) {
            hx[0] = x1 - x0; hx[1] = x3 - x0; hx[2] = x0; hx[3] = y1 - y0; hx[4] = y3 - y0; hx[5] = y0; hx[6] = 0f; hx[7] = 0f
            return false
        }
        val dx1 = x1 - x2; val dx2 = x3 - x2; val dy1 = y1 - y2; val dy2 = y3 - y2
        val den = dx1 * dy2 - dx2 * dy1
        if (abs(den) < 1e-9f) return false
        val gg = (dx3 * dy2 - dx2 * dy3) / den
        val hh = (dx1 * dy3 - dx3 * dy1) / den
        hx[0] = x1 - x0 + gg * x1; hx[1] = x3 - x0 + hh * x3; hx[2] = x0
        hx[3] = y1 - y0 + gg * y1; hx[4] = y3 - y0 + hh * y3; hx[5] = y0
        hx[6] = gg; hx[7] = hh
        return true
    }
    private fun mapX(s: Float, t: Float) = (hx[0] * s + hx[1] * t + hx[2]) / (hx[6] * s + hx[7] * t + 1f)
    private fun mapY(s: Float, t: Float) = (hx[3] * s + hx[4] * t + hx[5]) / (hx[6] * s + hx[7] * t + 1f)

    private fun tint(b: BufferedImage, k: Float, add: Int, amt: Float): BufferedImage {
        val kq = (k * 32f).toInt().coerceIn(0, 96); val aq = (amt * 32f).toInt().coerceIn(0, 32)
        if (kq == 32 && aq == 0) return b
        val key = (System.identityHashCode(b).toLong() shl 32) xor (kq.toLong() shl 26) xor (aq.toLong() shl 20) xor (add.toLong() and 0xFFFFFF)
        return tinted.getOrPut(key) {
            val w = b.width; val h = b.height
            val px = b.getRGB(0, 0, w, h, null, 0, w)
            val kk = kq / 32f; val aa = aq / 32f
            val ar = ((add shr 16) and 255) * aa; val ag = ((add shr 8) and 255) * aa; val ab = (add and 255) * aa
            for (i in px.indices) {
                val c = px[i]
                val r = (((c shr 16) and 255) * kk + ar).toInt().coerceIn(0, 255)
                val gg = (((c shr 8) and 255) * kk + ag).toInt().coerceIn(0, 255)
                val bb = ((c and 255) * kk + ab).toInt().coerceIn(0, 255)
                px[i] = (c and -0x1000000) or (r shl 16) or (gg shl 8) or bb
            }
            BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(0, 0, w, h, px, 0, w) }
        }
    }

    override fun imageQuad(img: Img, u0: Float, v0: Float, u1: Float, v1: Float, q: FloatArray,
                           alpha: Float, mul: Float, addColor: Int, addAmt: Float) = timed(if (additive) "quadAdd" else "quad") { imageQuad0(img, u0, v0, u1, v1, q, alpha, mul, addColor, addAmt) }
        private fun imageQuad0(img: Img, u0: Float, v0: Float, u1: Float, v1: Float, q: FloatArray,
                           alpha: Float, mul: Float, addColor: Int, addAmt: Float) {
        if (alpha <= 0.004f) return
        if (soft.quad(this, img, u0, v0, u1, v1, q, alpha, mul, addColor, addAmt, additive)) return
        var b = bi(img)
        val k = mul * (1f - addAmt)
        val opaque = b.transparency == java.awt.Transparency.OPAQUE
        if (!opaque) b = tint(b, k, addColor, addAmt)
        val persp = homography(q)
        var minX = q[0]; var maxX = q[0]; var minY = q[1]; var maxY = q[1]
        for (i in 1..3) { minX = min(minX, q[i * 2]); maxX = max(maxX, q[i * 2]); minY = min(minY, q[i * 2 + 1]); maxY = max(maxY, q[i * 2 + 1]) }
        val big = max(maxX - minX, maxY - minY)
        val n = if (!persp) 1 else if (big > 160f) 4 else if (big > 40f) 2 else 1
        val oldClip = g.clip
        g.composite = comp(alpha)
        for (j in 0 until n) for (i in 0 until n) {
            val s0 = i / n.toFloat(); val s1 = (i + 1) / n.toFloat(); val t0 = j / n.toFloat(); val t1 = (j + 1) / n.toFloat()
            val ax = mapX(s0, t0); val ay = mapY(s0, t0); val bx = mapX(s1, t0); val by = mapY(s1, t0)
            val cx = mapX(s1, t1); val cy = mapY(s1, t1); val dx = mapX(s0, t1); val dy = mapY(s0, t1)
            val su0 = u0 + (u1 - u0) * s0; val su1 = u0 + (u1 - u0) * s1; val sv0 = v0 + (v1 - v0) * t0; val sv1 = v0 + (v1 - v0) * t1
            tri(b, su0, sv0, su1, sv0, su1, sv1, ax, ay, bx, by, cx, cy, oldClip, opaque, k, addColor, addAmt, alpha)
            tri(b, su0, sv0, su1, sv1, su0, sv1, ax, ay, cx, cy, dx, dy, oldClip, opaque, k, addColor, addAmt, alpha)
        }
        g.clip = oldClip
        g.composite = AlphaComposite.SrcOver
    }

    private val triPath = Path2D.Float()
    private fun tri(b: BufferedImage, sx0: Float, sy0: Float, sx1: Float, sy1: Float, sx2: Float, sy2: Float,
                    dx0: Float, dy0: Float, dx1: Float, dy1: Float, dx2: Float, dy2: Float, oldClip: Shape?,
                    opaque: Boolean, k: Float, add: Int, amt: Float, alpha: Float) {
        val a11 = sx1 - sx0; val a12 = sx2 - sx0; val a21 = sy1 - sy0; val a22 = sy2 - sy0
        val det = a11 * a22 - a12 * a21
        if (abs(det) < 1e-6f) return
        val i11 = a22 / det; val i12 = -a12 / det; val i21 = -a21 / det; val i22 = a11 / det
        val b11 = dx1 - dx0; val b12 = dx2 - dx0; val b21 = dy1 - dy0; val b22 = dy2 - dy0
        val m00 = b11 * i11 + b12 * i21; val m01 = b11 * i12 + b12 * i22
        val m10 = b21 * i11 + b22 * i21; val m11 = b21 * i12 + b22 * i22
        val tx = dx0 - (m00 * sx0 + m01 * sy0); val ty = dy0 - (m10 * sx0 + m11 * sy0)
        // slightly enlarged clip triangle hides seams between neighbouring triangles
        val cxm = (dx0 + dx1 + dx2) / 3f; val cym = (dy0 + dy1 + dy2) / 3f
        fun ex(x: Float, c: Float, d: Float) = x + (x - c) / max(1f, d) * 0.6f
        val d0 = Point2D.distance(dx0.toDouble(), dy0.toDouble(), cxm.toDouble(), cym.toDouble()).toFloat()
        val d1 = Point2D.distance(dx1.toDouble(), dy1.toDouble(), cxm.toDouble(), cym.toDouble()).toFloat()
        val d2 = Point2D.distance(dx2.toDouble(), dy2.toDouble(), cxm.toDouble(), cym.toDouble()).toFloat()
        triPath.reset()
        triPath.moveTo(ex(dx0, cxm, d0), ex(dy0, cym, d0)); triPath.lineTo(ex(dx1, cxm, d1), ex(dy1, cym, d1)); triPath.lineTo(ex(dx2, cxm, d2), ex(dy2, cym, d2)); triPath.closePath()
        g.clip = oldClip
        g.clip(triPath)
        g.drawImage(b, AffineTransform(m00.toDouble(), m10.toDouble(), m01.toDouble(), m11.toDouble(), tx.toDouble(), ty.toDouble()), null)
        if (opaque && !additive) {
            if (k < 0.995f) { g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ((1f - k) * alpha).coerceIn(0f, 1f)); g.color = Color.BLACK; g.fill(triPath) }
            if (amt > 0.004f) { g.composite = AddComposite(amt * alpha); g.color = color(add or -0x1000000); g.fill(triPath) }
            g.composite = comp(alpha)
        }
    }

    // ------------------------------------------------------------------ paths & fills
    private fun pathOf(p: VPath): Path2D.Float {
        (p.native as? Path2D.Float)?.let { return it }
        val path = Path2D.Float()
        val o = p.ops
        var i = 0
        while (i < o.size) {
            when (o[i].toInt()) {
                0 -> { path.moveTo(o[i + 1], o[i + 2]); i += 3 }
                1 -> { path.lineTo(o[i + 1], o[i + 2]); i += 3 }
                2 -> { path.quadTo(o[i + 1], o[i + 2], o[i + 3], o[i + 4]); i += 5 }
                3 -> { path.curveTo(o[i + 1], o[i + 2], o[i + 3], o[i + 4], o[i + 5], o[i + 6]); i += 7 }
                4 -> { path.closePath(); i += 1 }
                5 -> { val r = o[i + 3]; path.append(Ellipse2D.Float(o[i + 1] - r, o[i + 2] - r, 2 * r, 2 * r), false); i += 4 }
                6 -> { path.append(Ellipse2D.Float(o[i + 1], o[i + 2], o[i + 3] - o[i + 1], o[i + 4] - o[i + 2]), false); i += 5 }
                7 -> { val rr = o[i + 5] * 2; path.append(RoundRectangle2D.Float(o[i + 1], o[i + 2], o[i + 3] - o[i + 1], o[i + 4] - o[i + 2], rr, rr), false); i += 6 }
                8 -> { path.append(Rectangle2D.Float(o[i + 1], o[i + 2], o[i + 3] - o[i + 1], o[i + 4] - o[i + 2]), false); i += 5 }
                else -> i++
            }
        }
        p.native = path
        return path
    }

    private fun stops(n: Int, s: FloatArray?): FloatArray {
        if (s != null) { val out = s.copyOf(); for (i in 1 until out.size) if (out[i] <= out[i - 1]) out[i] = out[i - 1] + 1e-4f; return out }
        return FloatArray(n) { it / (n - 1).toFloat() }
    }

    private fun paintOf(f: Fill): Paint = when (f) {
        is Solid -> color(f.color)
        is Linear -> (f.native as? Paint) ?: (if (f.x0 == f.x1 && f.y0 == f.y1) color(f.colors[0]) else
            LinearGradientPaint(f.x0, f.y0, f.x1, f.y1, stops(f.colors.size, f.stops), Array(f.colors.size) { color(f.colors[it]) },
                MultipleGradientPaint.CycleMethod.NO_CYCLE)).also { f.native = it }
        is Radial -> (f.native as? Paint) ?: RadialGradientPaint(f.cx, f.cy, max(f.r, 0.01f), stops(f.colors.size, f.stops),
            Array(f.colors.size) { color(f.colors[it]) }).also { f.native = it }
    }

    override fun fillPath(p: VPath, f: Fill, alpha: Float) = timed("fillPath") { fillPath0(p, f, alpha) }
        private fun fillPath0(p: VPath, f: Fill, alpha: Float) {
        g.composite = comp(alpha); g.paint = paintOf(f); g.fill(pathOf(p)); g.composite = AlphaComposite.SrcOver
    }

    override fun strokePath(p: VPath, width: Float, f: Fill, alpha: Float) {
        g.composite = comp(alpha); g.paint = paintOf(f); g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(pathOf(p)); g.composite = AlphaComposite.SrcOver
    }

    private val poly = Path2D.Float()
    private fun polyOf(xy: FloatArray, n: Int, closed: Boolean): Path2D.Float {
        poly.reset(); poly.moveTo(xy[0], xy[1])
        for (i in 1 until n) poly.lineTo(xy[i * 2], xy[i * 2 + 1])
        if (closed) poly.closePath()
        return poly
    }

    private fun solid(c: Int) { g.composite = comp(1f); g.paint = color(c) }

    override fun fillPoly(xy: FloatArray, n: Int, color: Int) = timed("fillPoly") { fillPoly0(xy, n, color) }
    private fun fillPoly0(xy: FloatArray, n: Int, color: Int) {
        if (n < 3) return
        if (soft.poly(this, xy, n, color, additive)) return
        solid(color); g.fill(polyOf(xy, n, true))
    }

    override fun fillPolyGradient(xy: FloatArray, n: Int, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int) {
        if (n < 3) return
        g.composite = comp(1f)
        g.paint = if (x0 == x1 && y0 == y1) color(c0) else LinearGradientPaint(x0, y0, x1, y1, floatArrayOf(0f, 1f), arrayOf(color(c0), color(c1)))
        g.fill(polyOf(xy, n, true))
    }

    override fun strokePoly(xy: FloatArray, n: Int, width: Float, color: Int, closed: Boolean) {
        if (n < 2) return
        solid(color); g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(polyOf(xy, n, closed))
    }

    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int) { if (r <= 0f) return; solid(color); g.fill(Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r)) }

    override fun strokeCircle(cx: Float, cy: Float, r: Float, width: Float, color: Int) {
        if (r <= 0f || width <= 0f) return
        solid(color); g.stroke = BasicStroke(width); g.draw(Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r))
    }

    private val glowStops = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    override fun glow(cx: Float, cy: Float, r: Float, color: Int) = timed("glow") { glow0(cx, cy, r, color) }
        private fun glow0(cx: Float, cy: Float, r: Float, color: Int) {
        if (soft.glow(this, cx, cy, r, color, additive)) return
        if (r < 1f || (color ushr 24) == 0) return
        val a = (color ushr 24) / 255f
        val cols = Array(5) { color(color or -0x1000000, a * (1f - glowStops[it]) * (1f - glowStops[it])) }
        g.composite = comp(1f)
        g.paint = RadialGradientPaint(cx, cy, r, glowStops, cols)
        g.fill(Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r))
        g.composite = AlphaComposite.SrcOver
    }

    override fun fillRect(l: Float, t: Float, r: Float, b: Float, color: Int) { solid(color); g.fill(Rectangle2D.Float(l, t, r - l, b - t)) }

    override fun fillRectGradient(l: Float, t: Float, r: Float, b: Float, c0: Int, c1: Int, vertical: Boolean) {
        if (r <= l || b <= t) return
        g.composite = comp(1f)
        g.paint = if (vertical) LinearGradientPaint(0f, t, 0f, b, floatArrayOf(0f, 1f), arrayOf(color(c0), color(c1)))
                  else LinearGradientPaint(l, 0f, r, 0f, floatArrayOf(0f, 1f), arrayOf(color(c0), color(c1)))
        g.fill(Rectangle2D.Float(l, t, r - l, b - t))
    }

    override fun fillRoundRect(l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        solid(color); g.fill(RoundRectangle2D.Float(l, t, r - l, b - t, rad * 2, rad * 2))
    }

    override fun strokeRoundRect(l: Float, t: Float, r: Float, b: Float, rad: Float, width: Float, color: Int) {
        solid(color); g.stroke = BasicStroke(width); g.draw(RoundRectangle2D.Float(l, t, r - l, b - t, rad * 2, rad * 2))
    }

    override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        solid(color); g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(Line2D.Float(x0, y0, x1, y1))
    }

    override fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, width: Float, color: Int) {
        solid(color); g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, -startDeg, -sweepDeg, Arc2D.OPEN))
    }

    override fun pie(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, color: Int) {
        solid(color); g.fill(Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, -startDeg, -sweepDeg, Arc2D.PIE))
    }

    override fun text(s: String, x: Float, y: Float, size: Float, font: Int, color: Int, align: Int, strokeW: Float, strokeColor: Int, alpha: Float) = timed("text") { text0(s, x, y, size, font, color, align, strokeW, strokeColor, alpha) }
        private fun text0(s: String, x: Float, y: Float, size: Float, font: Int, color: Int, align: Int, strokeW: Float, strokeColor: Int, alpha: Float) {
        if (s.isEmpty() || size < 1f || alpha <= 0.004f) return
        val f = fonts[font].deriveFont(size)
        val tl = TextLayout(s, f, g.fontRenderContext)
        val w = tl.advance
        val x0 = when (align) { Align.CENTER -> x - w / 2f; Align.RIGHT -> x - w; else -> x }
        val baseline = y + (tl.ascent - tl.descent) / 2f
        val shape = tl.getOutline(AffineTransform.getTranslateInstance(x0.toDouble(), baseline.toDouble()))
        g.composite = AlphaComposite.SrcOver
        if (strokeW > 0f) {
            g.paint = color(strokeColor, alpha); g.stroke = BasicStroke(strokeW * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(shape)
        }
        g.paint = color(color, alpha)
        g.fill(shape)
    }

    override fun textWidth(s: String, size: Float, font: Int): Float {
        if (s.isEmpty()) return 0f
        return TextLayout(s, fonts[font].deriveFont(size), g.fontRenderContext).advance
    }
}
