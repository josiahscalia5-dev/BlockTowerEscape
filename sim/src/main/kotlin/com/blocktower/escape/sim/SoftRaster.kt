package com.blocktower.escape.sim

import com.blocktower.escape.core.Img
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Minimal software rasterizer used by the desktop harness for the two hot paths: perspective
 * textured quads (block faces, sprites) and radial glows. Much faster than Java2D's clipped
 * triangles and gradient paints, which keeps video recording practical.
 */
class SoftRaster(target: BufferedImage) {
    private val w = target.width
    private val h = target.height
    private val dst = (target.raster.dataBuffer as DataBufferInt).data

    private class Tex(val w: Int, val h: Int, val px: IntArray)
    /** mip chains per source image */
    private val mips = HashMap<Any, Array<Tex>>()

    private fun chain(img: Img): Array<Tex> = mips.getOrPut(img.native) {
        val b = img.native as BufferedImage
        val levels = ArrayList<Tex>()
        var cur = Tex(b.width, b.height, b.getRGB(0, 0, b.width, b.height, null, 0, b.width))
        levels.add(cur)
        while (cur.w > 8 && cur.h > 8 && levels.size < 6) {
            val nw = cur.w / 2; val nh = cur.h / 2
            val out = IntArray(nw * nh)
            for (y in 0 until nh) for (x in 0 until nw) {
                var a = 0; var r = 0; var g = 0; var bl = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val c = cur.px[(y * 2 + dy) * cur.w + x * 2 + dx]
                    val ca = c ushr 24
                    a += ca; r += ((c shr 16) and 255) * ca; g += ((c shr 8) and 255) * ca; bl += (c and 255) * ca
                }
                out[y * nw + x] = if (a == 0) 0 else ((a / 4) shl 24) or ((r / a) shl 16) or ((g / a) shl 8) or (bl / a)
            }
            cur = Tex(nw, nh, out); levels.add(cur)
        }
        levels.toTypedArray()
    }

    private val pts = FloatArray(8)

    fun quad(gfx: J2DGfx, img: Img, u0: Float, v0: Float, u1: Float, v1: Float, q: FloatArray,
             alpha: Float, mul: Float, addColor: Int, addAmt: Float, additive: Boolean): Boolean {
        val tr = gfx.currentTransform()
        if (tr.type and AffineTransform.TYPE_GENERAL_TRANSFORM != 0) return false
        tr.transform(q, 0, pts, 0, 4)
        val clip = gfx.currentClip()?.bounds
        var cx0 = 0; var cy0 = 0; var cx1 = w; var cy1 = h
        if (clip != null) { cx0 = max(0, clip.x); cy0 = max(0, clip.y); cx1 = min(w, clip.x + clip.width); cy1 = min(h, clip.y + clip.height) }
        // homography: unit square -> quad, then invert (screen -> unit square)
        val x0 = pts[0]; val y0 = pts[1]; val x1 = pts[2]; val y1 = pts[3]; val x2 = pts[4]; val y2 = pts[5]; val x3 = pts[6]; val y3 = pts[7]
        val dx3 = x0 - x1 + x2 - x3; val dy3 = y0 - y1 + y2 - y3
        val a: Double; val b: Double; val c: Double; val d: Double; val e: Double; val f: Double; val gg: Double; val hh: Double
        if (abs(dx3) < 1e-4f && abs(dy3) < 1e-4f) {
            a = (x1 - x0).toDouble(); b = (x3 - x0).toDouble(); c = x0.toDouble(); d = (y1 - y0).toDouble(); e = (y3 - y0).toDouble(); f = y0.toDouble(); gg = 0.0; hh = 0.0
        } else {
            val dx1 = (x1 - x2).toDouble(); val dx2 = (x3 - x2).toDouble(); val dy1 = (y1 - y2).toDouble(); val dy2 = (y3 - y2).toDouble()
            val den = dx1 * dy2 - dx2 * dy1
            if (abs(den) < 1e-12) return true
            gg = (dx3 * dy2 - dx2 * dy3) / den; hh = (dx1 * dy3 - dx3 * dy1) / den
            a = x1 - x0 + gg * x1; b = x3 - x0 + hh * x3; c = x0.toDouble()
            d = y1 - y0 + gg * y1; e = y3 - y0 + hh * y3; f = y0.toDouble()
        }
        // inverse of [[a b c][d e f][gg hh 1]]
        val A = e - f * hh; val B = c * hh - b; val C = b * f - c * e
        val D = f * gg - d; val E = a - c * gg; val F = c * d - a * f
        val G = d * hh - e * gg; val H = b * gg - a * hh; val I = a * e - b * d
        if (abs(I) < 1e-12 && abs(G) < 1e-12 && abs(H) < 1e-12) return true
        var minY = min(min(y0, y1), min(y2, y3)); var maxY = max(max(y0, y1), max(y2, y3))
        var minX = min(min(x0, x1), min(x2, x3)); var maxX = max(max(x0, x1), max(x2, x3))
        val sy0 = max(cy0, ceil(minY - 0.5f).toInt()); val sy1 = min(cy1 - 1, floor(maxY - 0.5f).toInt())
        if (sy0 > sy1 || maxX < cx0 || minX > cx1) return true
        // choose a mip level from the texel/pixel ratio
        val ch = chain(img)
        val area = abs((x2 - x0) * (y3 - y1) - (x3 - x1) * (y2 - y0)) * 0.5f
        val texArea = abs((u1 - u0) * (v1 - v0))
        var lvl = 0
        if (area > 1f) { var ratio = texArea / area; while (ratio > 2.5f && lvl < ch.size - 1) { ratio /= 4f; lvl++ } }
        val tex = ch[lvl]
        val scale = 1f / (1 shl lvl)
        val tu0 = u0 * scale; val tv0 = v0 * scale; val tdu = (u1 - u0) * scale; val tdv = (v1 - v0) * scale
        val k = (mul * (1f - addAmt)).coerceIn(0f, 2f)
        val ar = ((addColor shr 16) and 255) * addAmt; val ag = ((addColor shr 8) and 255) * addAmt; val ab = (addColor and 255) * addAmt
        val filt = abs(k - 1f) > 0.004f || addAmt > 0.004f
        val al = alpha.coerceIn(0f, 1f)
        val xs = floatArrayOf(x0, x1, x2, x3); val ys = floatArrayOf(y0, y1, y2, y3)
        val tw = tex.w; val th = tex.h; val tpx = tex.px
        for (py in sy0..sy1) {
            val yc = py + 0.5f
            var lx = Float.MAX_VALUE; var rx = -Float.MAX_VALUE
            for (i in 0..3) {
                val ax = xs[i]; val ay = ys[i]; val bx = xs[(i + 1) % 4]; val by = ys[(i + 1) % 4]
                if ((ay <= yc && by > yc) || (by <= yc && ay > yc)) {
                    val xi = ax + (yc - ay) / (by - ay) * (bx - ax)
                    if (xi < lx) lx = xi; if (xi > rx) rx = xi
                }
            }
            if (lx > rx) continue
            val px0 = max(cx0, ceil(lx - 0.5f).toInt()); val px1 = min(cx1 - 1, floor(rx - 0.5f).toInt())
            var o = py * w + px0
            val affine = abs(G) < 1e-12 && abs(H) < 1e-12
            // texel coordinates at the span start and their per-pixel steps (exact for affine quads)
            val xs0 = px0 + 0.5
            val wz0 = G * xs0 + H * yc + I
            var fu = (tu0 + (A * xs0 + B * yc + C) / wz0 * tdu).toFloat()
            var fv = (tv0 + (D * xs0 + E * yc + F) / wz0 * tdv).toFloat()
            val stepU = if (affine) (A / I * tdu).toFloat() else 0f
            val stepV = if (affine) (D / I * tdv).toFloat() else 0f
            for (px in px0..px1) {
                if (!affine) {
                    val xc = px + 0.5
                    val wz = G * xc + H * yc + I
                    fu = (tu0 + (A * xc + B * yc + C) / wz * tdu).toFloat()
                    fv = (tv0 + (D * xc + E * yc + F) / wz * tdv).toFloat()
                }
                var tx = fu.toInt(); var ty = fv.toInt()
                fu += stepU; fv += stepV
                if (tx < 0) tx = 0 else if (tx >= tw) tx = tw - 1
                if (ty < 0) ty = 0 else if (ty >= th) ty = th - 1
                val sc = tpx[ty * tw + tx]
                val sa = ((sc ushr 24) / 255f) * al
                if (sa > 0.003f) {
                    var r = ((sc shr 16) and 255).toFloat(); var gc = ((sc shr 8) and 255).toFloat(); var bc = (sc and 255).toFloat()
                    if (filt) { r = r * k + ar; gc = gc * k + ag; bc = bc * k + ab }
                    if (sa >= 0.999f && !additive && !filt) dst[o] = sc and 0xFFFFFF
                    else {
                        val dc = dst[o]
                        val dr = (dc shr 16) and 255; val dg = (dc shr 8) and 255; val db = dc and 255
                        val nr: Int; val ng: Int; val nb: Int
                        if (additive) { nr = min(255, dr + (r * sa).toInt()); ng = min(255, dg + (gc * sa).toInt()); nb = min(255, db + (bc * sa).toInt()) }
                        else { nr = min(255, (dr + (r - dr) * sa).toInt()); ng = min(255, (dg + (gc - dg) * sa).toInt()); nb = min(255, (db + (bc - db) * sa).toInt()) }
                        dst[o] = (nr shl 16) or (ng shl 8) or nb
                    }
                }
                o++
            }
        }
        return true
    }

    fun glow(gfx: J2DGfx, cxIn: Float, cyIn: Float, rIn: Float, color: Int, additive: Boolean): Boolean {
        val tr = gfx.currentTransform()
        if (tr.type and AffineTransform.TYPE_GENERAL_TRANSFORM != 0) return false
        pts[0] = cxIn; pts[1] = cyIn; pts[2] = cxIn + rIn; pts[3] = cyIn
        tr.transform(pts, 0, pts, 4, 2)
        val cx = pts[4]; val cy = pts[5]
        val r = sqrt((pts[6] - cx) * (pts[6] - cx) + (pts[7] - cy) * (pts[7] - cy))
        if (r < 1f) return true
        val a0 = (color ushr 24) / 255f
        val cr = (color shr 16) and 255; val cg = (color shr 8) and 255; val cb = color and 255
        val y0 = max(0, (cy - r).toInt()); val y1 = min(h - 1, (cy + r).toInt())
        val x0 = max(0, (cx - r).toInt()); val x1 = min(w - 1, (cx + r).toInt())
        val inv = 1f / r
        for (y in y0..y1) {
            val dy = (y + 0.5f - cy) * inv
            var o = y * w + x0
            for (x in x0..x1) {
                val dx = (x + 0.5f - cx) * inv
                val dd = dx * dx + dy * dy
                if (dd < 1f) {
                    val t = 1f - sqrt(dd)
                    val k = t * t * a0
                    val dc = dst[o]
                    val dr = (dc shr 16) and 255; val dg = (dc shr 8) and 255; val db = dc and 255
                    dst[o] = if (additive) (min(255, dr + (cr * k).toInt()) shl 16) or (min(255, dg + (cg * k).toInt()) shl 8) or min(255, db + (cb * k).toInt())
                    else ((dr + ((cr - dr) * k).toInt()) shl 16) or ((dg + ((cg - dg) * k).toInt()) shl 8) or (db + ((cb - db) * k).toInt())
                }
                o++
            }
        }
        return true
    }

    private val px = FloatArray(64)
    private val xsBuf = FloatArray(64)

    /** Solid polygon fill (even-odd, any simple polygon), no anti-aliasing. */
    fun poly(gfx: J2DGfx, xy: FloatArray, n: Int, color: Int, additive: Boolean): Boolean {
        if (n > 32) return false
        val tr = gfx.currentTransform()
        if (tr.type and AffineTransform.TYPE_GENERAL_TRANSFORM != 0) return false
        tr.transform(xy, 0, px, 0, n)
        val clip = gfx.currentClip()?.bounds
        var cx0 = 0; var cy0 = 0; var cx1 = w; var cy1 = h
        if (clip != null) { cx0 = max(0, clip.x); cy0 = max(0, clip.y); cx1 = min(w, clip.x + clip.width); cy1 = min(h, clip.y + clip.height) }
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until n) { minY = min(minY, px[i * 2 + 1]); maxY = max(maxY, px[i * 2 + 1]) }
        val sy0 = max(cy0, ceil(minY - 0.5f).toInt()); val sy1 = min(cy1 - 1, floor(maxY - 0.5f).toInt())
        val a = (color ushr 24) / 255f
        if (a <= 0.003f) return true
        val cr = (color shr 16) and 255; val cg = (color shr 8) and 255; val cb = color and 255
        for (py in sy0..sy1) {
            val yc = py + 0.5f
            var m = 0
            for (i in 0 until n) {
                val ax = px[i * 2]; val ay = px[i * 2 + 1]; val j = (i + 1) % n; val bx = px[j * 2]; val by = px[j * 2 + 1]
                if ((ay <= yc && by > yc) || (by <= yc && ay > yc)) xsBuf[m++] = ax + (yc - ay) / (by - ay) * (bx - ax)
            }
            if (m < 2) continue
            java.util.Arrays.sort(xsBuf, 0, m)
            var k = 0
            while (k + 1 < m) {
                val x0 = max(cx0, ceil(xsBuf[k] - 0.5f).toInt()); val x1 = min(cx1 - 1, floor(xsBuf[k + 1] - 0.5f).toInt())
                var o = py * w + x0
                for (x in x0..x1) {
                    val dc = dst[o]
                    val dr = (dc shr 16) and 255; val dg = (dc shr 8) and 255; val db = dc and 255
                    dst[o] = if (additive) (min(255, dr + (cr * a).toInt()) shl 16) or (min(255, dg + (cg * a).toInt()) shl 8) or min(255, db + (cb * a).toInt())
                    else ((dr + ((cr - dr) * a).toInt()) shl 16) or ((dg + ((cg - dg) * a).toInt()) shl 8) or (db + ((cb - db) * a).toInt())
                    o++
                }
                k += 2
            }
        }
        return true
    }
}
