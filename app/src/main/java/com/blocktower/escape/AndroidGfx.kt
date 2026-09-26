package com.blocktower.escape

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.blocktower.escape.core.Align
import com.blocktower.escape.core.Fill
import com.blocktower.escape.core.Gfx
import com.blocktower.escape.core.Img
import com.blocktower.escape.core.Linear
import com.blocktower.escape.core.Radial
import com.blocktower.escape.core.Solid
import com.blocktower.escape.core.VPath

/** [Gfx] implemented with android.graphics on a hardware-accelerated Canvas. */
class AndroidGfx(assets: AssetManager) : Gfx {
    private lateinit var c: Canvas
    override var width = 0
    override var height = 0

    private val typefaces = arrayOf(
        Typeface.createFromAsset(assets, "fonts/FiraSansCondensed-Bold.ttf"),
        Typeface.createFromAsset(assets, "fonts/LilitaOne-Regular.ttf")
    )
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val bmpP = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val quadP = Paint(Paint.FILTER_BITMAP_FLAG)
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val glowP = Paint(Paint.FILTER_BITMAP_FLAG)
    private val m = Matrix()
    private val src = FloatArray(8)
    private val tmpPath = Path()
    private val rect = RectF()
    private var additive = false
    private val addMode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val filters = HashMap<Long, ColorFilter>()
    private val tints = HashMap<Int, ColorFilter>()
    private val shaders = HashMap<Bitmap, BitmapShader>()
    private val fm = Paint.FontMetrics()

    /** Soft radial falloff used for all glows (tinted per call). */
    private val glowBmp: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { b ->
        val px = IntArray(64 * 64)
        for (y in 0 until 64) for (x in 0 until 64) {
            val dx = (x + 0.5f - 32f) / 32f; val dy = (y + 0.5f - 32f) / 32f
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            val k = if (d >= 1f) 0f else (1f - d) * (1f - d)
            px[y * 64 + x] = ((k * 255f).toInt() shl 24) or 0xFFFFFF
        }
        b.setPixels(px, 0, 64, 0, 0, 64, 64)
    }

    fun begin(canvas: Canvas, w: Int, h: Int) {
        c = canvas; width = w; height = h; additive = false
    }

    fun end() {}

    private fun bmp(img: Img) = img.native as Bitmap
    private fun mode() = if (additive) addMode else null

    override fun save() { c.save() }
    override fun restore() { c.restore() }
    override fun translate(dx: Float, dy: Float) { c.translate(dx, dy) }
    override fun scale(sx: Float, sy: Float) { c.scale(sx, sy) }
    override fun rotate(deg: Float) { c.rotate(deg) }
    override fun clipRect(l: Float, t: Float, r: Float, b: Float) { c.clipRect(l, t, r, b) }
    override fun setAdditive(on: Boolean) { additive = on }

    override fun image(img: Img, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        if (alpha <= 0.004f) return
        rect.set(x, y, x + w, y + h)
        bmpP.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        bmpP.xfermode = mode()
        c.drawBitmap(bmp(img), null, rect, bmpP)
    }

    private fun colorFilter(mul: Float, addColor: Int, addAmt: Float): ColorFilter? {
        if (addAmt <= 0.004f && mul in 0.995f..1.005f) return null
        val km = ((mul * (1f - addAmt)).coerceIn(0f, 2f) * 64f).toInt()
        val ka = (addAmt.coerceIn(0f, 1f) * 48f).toInt()
        val key = (km.toLong() shl 40) or (ka.toLong() shl 32) or (addColor.toLong() and 0xFFFFFF)
        return filters.getOrPut(key) {
            val k = km / 64f
            val a = ka / 48f
            val r = Color.red(addColor) * a; val g = Color.green(addColor) * a; val b = Color.blue(addColor) * a
            ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                k, 0f, 0f, 0f, r,
                0f, k, 0f, 0f, g,
                0f, 0f, k, 0f, b,
                0f, 0f, 0f, 1f, 0f)))
        }
    }

    override fun imageQuad(img: Img, u0: Float, v0: Float, u1: Float, v1: Float, q: FloatArray,
                           alpha: Float, mul: Float, addColor: Int, addAmt: Float) {
        if (alpha <= 0.004f) return
        src[0] = u0; src[1] = v0; src[2] = u1; src[3] = v0; src[4] = u1; src[5] = v1; src[6] = u0; src[7] = v1
        if (!m.setPolyToPoly(src, 0, q, 0, 4)) return
        val b = bmp(img)
        quadP.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        quadP.colorFilter = colorFilter(mul, addColor, addAmt)
        quadP.xfermode = mode()
        val full = u0 <= 0f && v0 <= 0f && u1 >= img.w - 0.01f && v1 >= img.h - 0.01f
        if (full) {
            quadP.shader = null
            c.drawBitmap(b, m, quadP)
        } else {
            // partial texture (clipped faces): paint the quad with a transformed bitmap shader
            val sh = shaders.getOrPut(b) { BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            sh.setLocalMatrix(m)
            quadP.shader = sh
            tmpPath.rewind()
            tmpPath.moveTo(q[0], q[1]); tmpPath.lineTo(q[2], q[3]); tmpPath.lineTo(q[4], q[5]); tmpPath.lineTo(q[6], q[7]); tmpPath.close()
            c.drawPath(tmpPath, quadP)
            quadP.shader = null
        }
    }

    private fun pathOf(p: VPath): Path {
        (p.native as? Path)?.let { return it }
        val path = Path()
        val o = p.ops
        var i = 0
        while (i < o.size) {
            when (o[i].toInt()) {
                0 -> { path.moveTo(o[i + 1], o[i + 2]); i += 3 }
                1 -> { path.lineTo(o[i + 1], o[i + 2]); i += 3 }
                2 -> { path.quadTo(o[i + 1], o[i + 2], o[i + 3], o[i + 4]); i += 5 }
                3 -> { path.cubicTo(o[i + 1], o[i + 2], o[i + 3], o[i + 4], o[i + 5], o[i + 6]); i += 7 }
                4 -> { path.close(); i += 1 }
                5 -> { path.addCircle(o[i + 1], o[i + 2], o[i + 3], Path.Direction.CW); i += 4 }
                6 -> { path.addOval(o[i + 1], o[i + 2], o[i + 3], o[i + 4], Path.Direction.CW); i += 5 }
                7 -> { path.addRoundRect(o[i + 1], o[i + 2], o[i + 3], o[i + 4], o[i + 5], o[i + 5], Path.Direction.CW); i += 6 }
                8 -> { path.addRect(o[i + 1], o[i + 2], o[i + 3], o[i + 4], Path.Direction.CW); i += 5 }
                else -> i++
            }
        }
        p.native = path
        return path
    }

    private fun shaderOf(f: Fill): Shader? = when (f) {
        is Solid -> null
        is Linear -> (f.native as? Shader) ?: LinearGradient(f.x0, f.y0, f.x1, f.y1, f.colors, f.stops, Shader.TileMode.CLAMP).also { f.native = it }
        is Radial -> (f.native as? Shader) ?: RadialGradient(f.cx, f.cy, maxOf(f.r, 0.01f), f.colors, f.stops, Shader.TileMode.CLAMP).also { f.native = it }
    }

    private fun setup(p: Paint, f: Fill, alpha: Float) {
        p.xfermode = mode()
        val sh = shaderOf(f)
        if (sh == null) {
            p.shader = null
            p.color = (f as Solid).color
            p.alpha = (Color.alpha(f.color) * alpha.coerceIn(0f, 1f)).toInt()
        } else {
            p.shader = sh
            p.color = Color.BLACK
            p.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        }
    }

    override fun fillPath(p: VPath, f: Fill, alpha: Float) {
        setup(fillP, f, alpha); c.drawPath(pathOf(p), fillP); fillP.shader = null
    }

    override fun strokePath(p: VPath, width: Float, f: Fill, alpha: Float) {
        setup(strokeP, f, alpha); strokeP.strokeWidth = width; c.drawPath(pathOf(p), strokeP); strokeP.shader = null
    }

    private fun polyPath(xy: FloatArray, n: Int, closed: Boolean): Path {
        tmpPath.rewind()
        tmpPath.moveTo(xy[0], xy[1])
        for (i in 1 until n) tmpPath.lineTo(xy[i * 2], xy[i * 2 + 1])
        if (closed) tmpPath.close()
        return tmpPath
    }

    private fun solid(p: Paint, color: Int) { p.shader = null; p.color = color; p.xfermode = mode() }

    override fun fillPoly(xy: FloatArray, n: Int, color: Int) {
        if (n < 3) return
        solid(fillP, color); c.drawPath(polyPath(xy, n, true), fillP)
    }

    override fun fillPolyGradient(xy: FloatArray, n: Int, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int) {
        if (n < 3) return
        fillP.xfermode = mode()
        fillP.color = Color.BLACK
        fillP.shader = if (x0 == x1 && y0 == y1) null else LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP)
        if (fillP.shader == null) fillP.color = c0
        c.drawPath(polyPath(xy, n, true), fillP)
        fillP.shader = null
    }

    override fun strokePoly(xy: FloatArray, n: Int, width: Float, color: Int, closed: Boolean) {
        if (n < 2) return
        solid(strokeP, color); strokeP.strokeWidth = width; c.drawPath(polyPath(xy, n, closed), strokeP)
    }

    override fun fillCircle(cx: Float, cy: Float, r: Float, color: Int) {
        if (r <= 0f) return
        solid(fillP, color); c.drawCircle(cx, cy, r, fillP)
    }

    override fun strokeCircle(cx: Float, cy: Float, r: Float, width: Float, color: Int) {
        if (r <= 0f || width <= 0f) return
        solid(strokeP, color); strokeP.strokeWidth = width; c.drawCircle(cx, cy, r, strokeP)
    }

    override fun glow(cx: Float, cy: Float, r: Float, color: Int) {
        if (r < 1f || Color.alpha(color) == 0) return
        val rgb = color or (0xFF shl 24)
        glowP.colorFilter = tints.getOrPut(rgb) { PorterDuffColorFilter(rgb, PorterDuff.Mode.SRC_IN) }
        glowP.alpha = Color.alpha(color)
        glowP.xfermode = mode()
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawBitmap(glowBmp, null, rect, glowP)
    }

    override fun fillRect(l: Float, t: Float, r: Float, b: Float, color: Int) {
        solid(fillP, color); fillP.isAntiAlias = false; c.drawRect(l, t, r, b, fillP); fillP.isAntiAlias = true
    }

    override fun fillRectGradient(l: Float, t: Float, r: Float, b: Float, c0: Int, c1: Int, vertical: Boolean) {
        if (r <= l || b <= t) return
        fillP.xfermode = mode()
        fillP.color = Color.BLACK
        fillP.shader = if (vertical) LinearGradient(0f, t, 0f, b, c0, c1, Shader.TileMode.CLAMP)
                       else LinearGradient(l, 0f, r, 0f, c0, c1, Shader.TileMode.CLAMP)
        c.drawRect(l, t, r, b, fillP)
        fillP.shader = null
    }

    override fun fillRoundRect(l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int) {
        solid(fillP, color); c.drawRoundRect(l, t, r, b, rad, rad, fillP)
    }

    override fun strokeRoundRect(l: Float, t: Float, r: Float, b: Float, rad: Float, width: Float, color: Int) {
        solid(strokeP, color); strokeP.strokeWidth = width; c.drawRoundRect(l, t, r, b, rad, rad, strokeP)
    }

    override fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        solid(strokeP, color); strokeP.strokeWidth = width; c.drawLine(x0, y0, x1, y1, strokeP)
    }

    override fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, width: Float, color: Int) {
        solid(strokeP, color); strokeP.strokeWidth = width
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, startDeg, sweepDeg, false, strokeP)
    }

    override fun pie(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, color: Int) {
        solid(fillP, color)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(rect, startDeg, sweepDeg, true, fillP)
    }

    override fun text(s: String, x: Float, y: Float, size: Float, font: Int, color: Int, align: Int,
                      strokeW: Float, strokeColor: Int, alpha: Float) {
        if (s.isEmpty() || size < 1f || alpha <= 0.004f) return
        textP.typeface = typefaces[font]
        textP.textSize = size
        textP.textAlign = when (align) { Align.CENTER -> Paint.Align.CENTER; Align.RIGHT -> Paint.Align.RIGHT; else -> Paint.Align.LEFT }
        textP.getFontMetrics(fm)
        val baseline = y - (fm.ascent + fm.descent) / 2f
        textP.xfermode = null
        if (strokeW > 0f) {
            textP.style = Paint.Style.STROKE
            textP.strokeJoin = Paint.Join.ROUND
            textP.strokeWidth = strokeW * 2f
            textP.color = strokeColor
            textP.alpha = (Color.alpha(strokeColor) * alpha.coerceIn(0f, 1f)).toInt()
            c.drawText(s, x, baseline, textP)
            textP.style = Paint.Style.FILL
        }
        textP.color = color
        textP.alpha = (Color.alpha(color) * alpha.coerceIn(0f, 1f)).toInt()
        c.drawText(s, x, baseline, textP)
    }

    override fun textWidth(s: String, size: Float, font: Int): Float {
        textP.typeface = typefaces[font]
        textP.textSize = size
        return textP.measureText(s)
    }
}
