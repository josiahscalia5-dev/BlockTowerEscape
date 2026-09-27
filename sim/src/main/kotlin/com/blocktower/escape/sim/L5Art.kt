package com.blocktower.escape.sim

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Builds the Level 5 (volcanic sky fortress) artwork from the approved reference
 * `design/level5_volcano_reference.png` (941 x 1672). Nothing is painted by hand: every piece is cut from the
 * reference along a traced outline, refined per pixel at the edge by colour (foreground and background colours
 * sampled just inside and just outside the outline, tile by tile), and feathered.
 *
 *  - gate.png      the fortress and its portal (the level's destination). The reference's Time panel hides the
 *                  right wing and the top pills hide the tops of the towers: the hidden wing is rebuilt by
 *                  mirroring the left wing across the portal (the fortress is symmetric), the tops fade out.
 *  - guardian.png  the lava-rock golem (its left fist runs off the reference's edge: that side fades out)
 *  - relic.png     the glowing golden runaway relic
 *  - mace.png      the spiked iron ball (it swings on a chain drawn in the game)
 *  - banner.png    a red banner with the gold crown
 *  - bg_plate.jpg  the sunset sky, the floating islands and castles and the cliffs with lava falls: the HUD
 *                  panels, the fortress, the loop, the track and the characters are taken out and the holes
 *                  filled from their surroundings (push-pull), with the reference's own cloud texture on top.
 *
 *   ./gradlew :sim:run --args="l5art"            writes app/src/main/assets/l5v/ and previews to sim-out/l5art/
 */
class L5Art(private val root: File, private val preview: File) {
    private val ref = ImageIO.read(File(root, "design/level5_volcano_reference.png"))
    private val W = ref.width
    private val H = ref.height
    private val px = IntArray(W * H).also { ref.getRGB(0, 0, W, H, it, 0, W) }
    private val outDir = File(root, "app/src/main/assets/l5v").apply { mkdirs() }

    private fun r(c: Int) = (c shr 16) and 255
    private fun g(c: Int) = (c shr 8) and 255
    private fun b(c: Int) = c and 255

    // ------------------------------------------------------------------ outlines
    /** Inside test (even-odd) for a polygon given as x0,y0,x1,y1,... */
    private fun inside(poly: FloatArray, x: Float, y: Float): Boolean {
        var c = false
        var j = poly.size / 2 - 1
        for (i in 0 until poly.size / 2) {
            val xi = poly[i * 2]; val yi = poly[i * 2 + 1]; val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    private fun segDist(px0: Float, py0: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        var t = if (l2 > 0f) ((px0 - ax) * dx + (py0 - ay) * dy) / l2 else 0f
        t = t.coerceIn(0f, 1f)
        val qx = ax + t * dx - px0; val qy = ay + t * dy - py0
        return sqrt(qx * qx + qy * qy)
    }

    /** Signed distance to the outline (positive inside) over the whole image, limited to [lim]. */
    private fun sdf(poly: FloatArray, lim: Float): FloatArray {
        val out = FloatArray(W * H) { -lim }
        var minX = 1e9f; var maxX = -1e9f; var minY = 1e9f; var maxY = -1e9f
        for (i in 0 until poly.size / 2) { minX = min(minX, poly[i * 2]); maxX = max(maxX, poly[i * 2]); minY = min(minY, poly[i * 2 + 1]); maxY = max(maxY, poly[i * 2 + 1]) }
        val x0 = max(0, (minX - lim - 1).toInt()); val x1 = min(W - 1, (maxX + lim + 1).toInt())
        val y0 = max(0, (minY - lim - 1).toInt()); val y1 = min(H - 1, (maxY + lim + 1).toInt())
        val n = poly.size / 2
        for (y in y0..y1) for (x in x0..x1) {
            val fx = x + 0.5f; val fy = y + 0.5f
            var d = lim
            var j = n - 1
            for (i in 0 until n) { d = min(d, segDist(fx, fy, poly[j * 2], poly[j * 2 + 1], poly[i * 2], poly[i * 2 + 1])); j = i }
            out[y * W + x] = if (inside(poly, fx, fy)) d else -d
        }
        return out
    }

    /**
     * Matte for the outline: 1 deeper than [band] inside, 0 further than [band] outside; in the band each pixel
     * goes to whichever side its colour is closer to (k-means colours of the pixels just inside / just outside,
     * sampled per 48 px tile so the background's changing colours are followed).
     */
    private fun matte(poly: FloatArray, band: Float, img: IntArray = px): FloatArray {
        val sd = sdf(poly, band + 14f)
        val a = FloatArray(W * H)
        for (i in a.indices) a[i] = if (sd[i] >= band) 1f else if (sd[i] <= -band) 0f else -1f
        val tile = 48
        for (ty in 0 until (H + tile - 1) / tile) for (tx in 0 until (W + tile - 1) / tile) {
            val x0 = tx * tile; val y0 = ty * tile; val x1 = min(W, x0 + tile); val y1 = min(H, y0 + tile)
            var any = false
            for (y in y0 until y1) { for (x in x0 until x1) if (a[y * W + x] < 0f) { any = true; break }; if (any) break }
            if (!any) continue
            val fg = ArrayList<Int>(); val bg = ArrayList<Int>()
            val m = 24
            for (y in max(0, y0 - m) until min(H, y1 + m)) for (x in max(0, x0 - m) until min(W, x1 + m)) {
                val d = sd[y * W + x]
                if (d >= band && d < band + 12f) fg.add(img[y * W + x])
                else if (d <= -band && d > -band - 12f) bg.add(img[y * W + x])
            }
            val cf = kmeans(fg, 5); val cb = kmeans(bg, 5)
            for (y in y0 until y1) for (x in x0 until x1) {
                val i = y * W + x
                if (a[i] >= 0f) continue
                if (cf.isEmpty() || cb.isEmpty()) { a[i] = if (sd[i] > 0f) 1f else 0f; continue }
                val c = img[i]
                val df = nearest(cf, c); val db = nearest(cb, c)
                // prefer the traced side a little so flat regions stay where the outline puts them
                val bias = sd[i] / band * 0.25f
                val u = db / (df + db + 1e-3f) + bias
                a[i] = smoothstep(0.38f, 0.62f, u)
            }
        }
        return a
    }

    private fun kmeans(samples: List<Int>, k: Int): Array<FloatArray> {
        if (samples.isEmpty()) return emptyArray()
        val step = max(1, samples.size / 600)
        val s = samples.filterIndexed { i, _ -> i % step == 0 }
        val kk = min(k, s.size)
        val c = Array(kk) { i -> val v = s[i * s.size / kk]; floatArrayOf(r(v).toFloat(), g(v).toFloat(), b(v).toFloat()) }
        repeat(8) {
            val sum = Array(kk) { FloatArray(4) }
            for (v in s) {
                var best = 0; var bd = Float.MAX_VALUE
                for (j in 0 until kk) { val d = sq(r(v) - c[j][0]) + sq(g(v) - c[j][1]) + sq(b(v) - c[j][2]); if (d < bd) { bd = d; best = j } }
                sum[best][0] += r(v).toFloat(); sum[best][1] += g(v).toFloat(); sum[best][2] += b(v).toFloat(); sum[best][3] += 1f
            }
            for (j in 0 until kk) if (sum[j][3] > 0f) { c[j][0] = sum[j][0] / sum[j][3]; c[j][1] = sum[j][1] / sum[j][3]; c[j][2] = sum[j][2] / sum[j][3] }
        }
        return c
    }
    private fun sq(v: Float) = v * v
    private fun sq(v: Int) = (v * v).toFloat()
    private fun nearest(cs: Array<FloatArray>, v: Int): Float {
        var bd = Float.MAX_VALUE
        for (c in cs) bd = min(bd, sq(r(v) - c[0]) + sq(g(v) - c[1]) + sq(b(v) - c[2]))
        return sqrt(bd)
    }
    private fun smoothstep(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    /** Majority clean-up (removes single stray pixels) and a small blur for a soft, clean edge. */
    private fun clean(a: FloatArray, blur: Float): FloatArray {
        val o = a.copyOf()
        for (y in 1 until H - 1) for (x in 1 until W - 1) {
            val i = y * W + x
            if (a[i] <= 0f && a[i - 1] <= 0f && a[i + 1] <= 0f) continue
            var s = 0f
            for (dy in -1..1) for (dx in -1..1) s += a[i + dy * W + dx]
            val m = s / 9f
            if (a[i] > 0.5f && m < 0.3f) o[i] = m else if (a[i] < 0.5f && m > 0.7f) o[i] = m
        }
        return gauss(o, blur)
    }

    private fun gauss(a: FloatArray, sigma: Float): FloatArray {
        if (sigma <= 0f) return a
        val rad = (sigma * 2.5f).toInt().coerceAtLeast(1)
        val k = FloatArray(rad * 2 + 1) { i -> exp(-sq((i - rad).toFloat()) / (2f * sigma * sigma)) }
        val ks = k.sum(); for (i in k.indices) k[i] /= ks
        val t = FloatArray(W * H); val o = FloatArray(W * H)
        for (y in 0 until H) for (x in 0 until W) { var s = 0f; for (i in k.indices) s += a[y * W + (x + i - rad).coerceIn(0, W - 1)] * k[i]; t[y * W + x] = s }
        for (y in 0 until H) for (x in 0 until W) { var s = 0f; for (i in k.indices) s += t[(y + i - rad).coerceIn(0, H - 1) * W + x] * k[i]; o[y * W + x] = s }
        return o
    }

    // ------------------------------------------------------------------ output
    private fun writeCut(name: String, img: IntArray, a: FloatArray, x0: Int, y0: Int, x1: Int, y1: Int, scale: Float = 1f) {
        val w = x1 - x0; val h = y1 - y0
        val o = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val pv = BufferedImage(w * 2, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y0 + y) * W + x0 + x
            val al = (a[i].coerceIn(0f, 1f) * 255f).toInt()
            o.setRGB(x, y, (al shl 24) or (img[i] and 0xFFFFFF))
            // preview: on magenta, and the original beside it
            val c = img[i]; val k = al / 255f
            val mr = (r(c) * k + 255 * (1 - k)).toInt(); val mg = (g(c) * k).toInt(); val mb = (b(c) * k + 255 * (1 - k)).toInt()
            pv.setRGB(x, y, (mr shl 16) or (mg shl 8) or mb)
            pv.setRGB(w + x, y, c and 0xFFFFFF)
        }
        val out = if (scale != 1f) scaled(o, (w * scale).toInt(), (h * scale).toInt()) else o
        ImageIO.write(out, "png", File(outDir, name))
        ImageIO.write(pv, "png", File(preview, name.replace(".png", "_prev.png")))
        println("wrote l5v/$name ${out.width}x${out.height}")
    }

    private fun scaled(src: BufferedImage, w: Int, h: Int): BufferedImage {
        val o = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g2 = o.createGraphics()
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2.drawImage(src, 0, 0, w, h, null); g2.dispose()
        return o
    }

    private fun writeJpg(name: String, img: IntArray, w: Int, h: Int, q: Float) {
        val o = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        o.setRGB(0, 0, w, h, img, 0, w)
        val wr = ImageIO.getImageWritersByFormatName("jpg").next()
        val p = wr.defaultWriteParam
        p.compressionMode = ImageWriteParam.MODE_EXPLICIT; p.compressionQuality = q
        File(outDir, name).outputStream().use { os ->
            ImageIO.createImageOutputStream(os).use { ios -> wr.output = ios; wr.write(null, IIOImage(o, null, null), p) }
        }
        ImageIO.write(o, "png", File(preview, name.replace(".jpg", "_prev.png")))
        println("wrote l5v/$name ${w}x$h")
    }

    // ------------------------------------------------------------------ hole filling
    /**
     * Push-pull fill: every hole pixel (hole > 0.5) gets the colour its surroundings suggest, smooth across the
     * hole. Away from the hole's edge the sunset sky takes over (see below).
     */
    private fun fill(img: IntArray, hole: FloatArray): IntArray {
        var lw = W; var lh = H
        val levelsC = ArrayList<FloatArray>(); val levelsW = ArrayList<FloatArray>(); val dims = ArrayList<IntArray>()
        var c = FloatArray(W * H * 3); var wt = FloatArray(W * H)
        for (i in 0 until W * H) {
            val v = img[i]
            // only sky-coloured pixels feed the fill (dark towers and islands at a hole's edge would smudge it)
            val lum = r(v) * 0.3f + g(v) * 0.55f + b(v) * 0.15f
            val k = (1f - hole[i].coerceIn(0f, 1f)) * (if (lum >= 120f) 1f else 0.02f)
            c[i * 3] = r(v) * k; c[i * 3 + 1] = g(v) * k; c[i * 3 + 2] = b(v) * k; wt[i] = k
        }
        levelsC.add(c); levelsW.add(wt); dims.add(intArrayOf(lw, lh))
        while (lw > 2 && lh > 2) {
            val nw = (lw + 1) / 2; val nh = (lh + 1) / 2
            val nc = FloatArray(nw * nh * 3); val nwt = FloatArray(nw * nh)
            for (y in 0 until nh) for (x in 0 until nw) {
                var sc0 = 0f; var sc1 = 0f; var sc2 = 0f; var sw = 0f
                for (dy in 0..1) for (dx in 0..1) {
                    val xx = min(lw - 1, x * 2 + dx); val yy = min(lh - 1, y * 2 + dy)
                    val j = yy * lw + xx
                    sc0 += c[j * 3]; sc1 += c[j * 3 + 1]; sc2 += c[j * 3 + 2]; sw += wt[j]
                }
                val o = y * nw + x
                // premultiplied: keep sums normalised to weight <= 1
                val norm = if (sw > 1f) 1f / sw else 1f
                nc[o * 3] = sc0 * norm; nc[o * 3 + 1] = sc1 * norm; nc[o * 3 + 2] = sc2 * norm; nwt[o] = min(1f, sw)
            }
            c = nc; wt = nwt; lw = nw; lh = nh
            levelsC.add(c); levelsW.add(wt); dims.add(intArrayOf(lw, lh))
        }
        // push: fill each level's missing weight from the (already filled) coarser level, bilinear
        for (l in levelsC.size - 2 downTo 0) {
            val cc = levelsC[l]; val ww = levelsW[l]; val (cw, ch) = dims[l].let { it[0] to it[1] }
            val pc = levelsC[l + 1]; val (pw, ph) = dims[l + 1].let { it[0] to it[1] }
            for (y in 0 until ch) for (x in 0 until cw) {
                val i = y * cw + x
                val miss = 1f - ww[i]
                if (miss <= 0f) continue
                val fx = (x + 0.5f) / 2f - 0.5f; val fy = (y + 0.5f) / 2f - 0.5f
                val x0 = fx.toInt().coerceIn(0, pw - 1); val y0 = fy.toInt().coerceIn(0, ph - 1)
                val x1 = min(pw - 1, x0 + 1); val y1 = min(ph - 1, y0 + 1)
                val ax = (fx - x0).coerceIn(0f, 1f); val ay = (fy - y0).coerceIn(0f, 1f)
                for (ch3 in 0..2) {
                    val v = (pc[(y0 * pw + x0) * 3 + ch3] * (1 - ax) + pc[(y0 * pw + x1) * 3 + ch3] * ax) * (1 - ay) +
                        (pc[(y1 * pw + x0) * 3 + ch3] * (1 - ax) + pc[(y1 * pw + x1) * 3 + ch3] * ax) * ay
                    cc[i * 3 + ch3] += v * miss
                }
                ww[i] = 1f
            }
        }
        val base = levelsC[0]
        // far from the hole's edge the sky follows the sunset: each row's colour measured from the reference's
        // own sky pixels in that row band, with soft horizontal cloud streaks (fractal noise); near the edge the
        // push-pull colour takes over so the fill meets what is around it
        val rowC = Array(H) { FloatArray(3) }
        run {
            val acc = Array(H) { FloatArray(4) }
            for (y in 0 until H) for (x in 0 until W) {
                val i = y * W + x
                if (hole[i] > 0.01f) continue
                val v = img[i]
                val lum = r(v) * 0.3f + g(v) * 0.55f + b(v) * 0.15f
                if (lum < 150f) continue
                for (yy in max(0, y - 6)..min(H - 1, y + 6)) { acc[yy][0] += r(v).toFloat(); acc[yy][1] += g(v).toFloat(); acc[yy][2] += b(v).toFloat(); acc[yy][3] += 1f }
            }
            var last = -1
            for (y in 0 until H) if (acc[y][3] > 30f) {
                for (k in 0..2) rowC[y][k] = acc[y][k] / acc[y][3]
                if (last < 0) { for (yy in 0 until y) for (k in 0..2) rowC[yy][k] = rowC[y][k] }
                else if (y - last > 1) for (yy in last + 1 until y) { val u = (yy - last).toFloat() / (y - last); for (k in 0..2) rowC[yy][k] = rowC[last][k] + (rowC[y][k] - rowC[last][k]) * u }
                last = y
            }
            if (last in 0 until H - 1) for (yy in last + 1 until H) for (k in 0..2) rowC[yy][k] = rowC[last][k]
        }
        val dist = holeDistance(hole)
        val out = IntArray(W * H)
        for (i in 0 until W * H) {
            val k = hole[i].coerceIn(0f, 1f)
            val x = i % W; val y = i / W
            val far = smoothstep(4f, 90f, dist[i])
            // banks of sunset cloud: lit from above (pale pink-gold tops), shaded underneath
            val n = fbm(x / 180f, y / 26f)
            val bank = smoothstep(0.46f, 0.7f, n)
            val under = smoothstep(0.46f, 0.7f, fbm(x / 180f, (y - 7f) / 26f))
            val lit = (bank - under * 0.6f).coerceIn(0f, 1f)
            val cloud = 0.9f + 0.12f * n - 0.1f * (under - bank).coerceAtLeast(0f)
            val hi = lit * 0.55f + bank * 0.15f
            var cr = base[i * 3]; var cg = base[i * 3 + 1]; var cb = base[i * 3 + 2]
            val sr = rowC[y][0] * cloud + (255f - rowC[y][0]) * hi * 0.5f
            val sg = rowC[y][1] * cloud + (226f - rowC[y][1]) * hi * 0.5f
            val sb = rowC[y][2] * cloud + (214f - rowC[y][2]) * hi * 0.5f
            cr += (sr - cr) * far; cg += (sg - cg) * far; cb += (sb - cb) * far
            val v = img[i]
            val rr = (r(v) + (cr - r(v)) * k).toInt().coerceIn(0, 255)
            val gg = (g(v) + (cg - g(v)) * k).toInt().coerceIn(0, 255)
            val bb = (b(v) + (cb - b(v)) * k).toInt().coerceIn(0, 255)
            out[i] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        return out
    }

    /** Distance (in pixels, chamfer) from each hole pixel to the nearest pixel outside the hole. */
    private fun holeDistance(hole: FloatArray): FloatArray {
        val d = FloatArray(W * H) { if (hole[it] > 0.5f) 1e6f else 0f }
        for (y in 0 until H) for (x in 0 until W) {
            val i = y * W + x; if (d[i] == 0f) continue
            if (x > 0) d[i] = min(d[i], d[i - 1] + 1f)
            if (y > 0) d[i] = min(d[i], d[i - W] + 1f)
            if (x > 0 && y > 0) d[i] = min(d[i], d[i - W - 1] + 1.414f)
            if (x < W - 1 && y > 0) d[i] = min(d[i], d[i - W + 1] + 1.414f)
        }
        for (y in H - 1 downTo 0) for (x in W - 1 downTo 0) {
            val i = y * W + x; if (d[i] == 0f) continue
            if (x < W - 1) d[i] = min(d[i], d[i + 1] + 1f)
            if (y < H - 1) d[i] = min(d[i], d[i + W] + 1f)
            if (x < W - 1 && y < H - 1) d[i] = min(d[i], d[i + W + 1] + 1.414f)
            if (x > 0 && y < H - 1) d[i] = min(d[i], d[i + W - 1] + 1.414f)
        }
        return d
    }

    private fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777215f
    }
    private fun vnoise(x: Float, y: Float): Float {
        val xi = kotlin.math.floor(x).toInt(); val yi = kotlin.math.floor(y).toInt()
        val fx = x - xi; val fy = y - yi
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        val a = hash(xi, yi); val b0 = hash(xi + 1, yi); val c = hash(xi, yi + 1); val d = hash(xi + 1, yi + 1)
        val top = a + (b0 - a) * ux; val bot = c + (d - c) * ux
        return top + (bot - top) * uy
    }
    private fun fbm(x: Float, y: Float): Float {
        var s = 0f; var amp = 0.5f; var f = 1f
        repeat(5) { s += vnoise(x * f, y * f) * amp; f *= 2.03f; amp *= 0.5f }
        return s
    }

    private fun rectMask(m: FloatArray, x0: Int, y0: Int, x1: Int, y1: Int, v: Float = 1f) {
        for (y in max(0, y0) until min(H, y1)) for (x in max(0, x0) until min(W, x1)) m[y * W + x] = max(m[y * W + x], v)
    }
    private fun polyMask(m: FloatArray, poly: FloatArray, grow: Float) {
        val sd = sdf(poly, grow + 2f)
        for (i in m.indices) if (sd[i] > -grow) m[i] = max(m[i], ((sd[i] + grow) / 2f).coerceIn(0f, 1f))
    }

    // ------------------------------------------------------------------ the pieces
    private val fortress = floatArrayOf(
        560f, 372f, 490f, 372f, 484f, 330f, 488f, 306f, 478f, 292f, 495f, 282f, 515f, 290f, 520f, 262f, 530f, 250f, 545f, 262f,
        546f, 218f, 560f, 211f, 568f, 198f, 572f, 165f, 590f, 172f, 598f, 200f, 604f, 204f, 602f, 150f, 598f, 124f,
        604f, 104f, 624f, 97f, 640f, 99f, 646f, 84f, 653f, 96f,
        // the tops of the central towers are hidden by the reference's top bar: cut level, faded in [gate]
        660f, 96f, 732f, 96f,
        // right side: the hidden upper wing is the mirror of the left one
        739f, 96f, 746f, 84f, 752f, 99f, 768f, 97f, 788f, 104f, 794f, 124f, 790f, 150f, 788f, 204f, 794f, 200f,
        802f, 172f, 820f, 165f, 824f, 198f, 832f, 211f, 840f, 218f, 840f, 283f, 852f, 284f, 870f, 289f, 883f, 300f, 886f, 380f, 780f, 380f, 745f, 398f, 640f, 398f, 600f, 385f)

    private val golem = floatArrayOf(
        0f, 1008f, 0f, 834f, 15f, 815f, 45f, 803f, 85f, 800f, 120f, 806f, 150f, 795f, 165f, 780f, 200f, 772f, 250f, 770f,
        290f, 775f, 325f, 790f, 345f, 815f, 360f, 845f, 390f, 862f, 425f, 872f, 445f, 900f, 447f, 960f, 440f, 993f,
        410f, 997f, 375f, 998f, 352f, 1010f, 336f, 1040f, 290f, 1052f, 230f, 1052f, 180f, 1045f, 150f, 1030f, 120f, 1016f,
        60f, 1013f, 20f, 1011f)

    private val relic = floatArrayOf(
        588f, 838f, 606f, 838f, 618f, 852f, 626f, 875f, 628f, 893f, 640f, 905f, 650f, 925f, 652f, 950f, 640f, 960f,
        622f, 972f, 615f, 1002f, 565f, 1003f, 545f, 990f, 542f, 955f, 550f, 930f, 552f, 900f, 556f, 880f, 566f, 853f,
        578f, 862f, 587f, 888f)

    private fun circle(cx: Float, cy: Float, rr: Float, n: Int = 40) = FloatArray(n * 2) { i ->
        val a = (i / 2) * 2.0 * Math.PI / n
        if (i % 2 == 0) (cx + rr * kotlin.math.cos(a)).toFloat() else (cy + rr * kotlin.math.sin(a)).toFloat()
    }
    private val banner = floatArrayOf(877f, 801f, 926f, 801f, 926f, 899f, 921f, 900f, 900f, 881f, 880f, 900f, 876f, 899f)

    fun run() {
        preview.mkdirs()
        // ---- the fortress: rebuild the wing under the Time panel as the mirror of the left wing (axis x = 696)
        val axis = 696f
        val fort = px.copyOf()
        val tx0 = 721; val ty0 = 121; val tx1 = 934; val ty1 = 245
        for (y in ty0 - 6 until ty1 + 6) for (x in tx0 - 6 until min(W, tx1 + 6)) {
            val mx = (2f * axis - x).toInt().coerceIn(0, W - 1)
            // soft blend across the panel's border so the rebuilt wing meets the visible one
            val dx = min(x - (tx0 - 6), (tx1 + 6) - x).toFloat(); val dy = min(y - (ty0 - 6), (ty1 + 6) - y).toFloat()
            val k = (min(dx, dy) / 6f).coerceIn(0f, 1f)
            fort[y * W + x] = mix(px[y * W + x], px[y * W + mx], k)
        }
        var ga = matte(fortress, 7f, fort)
        // the tower tops are cut by the reference's top bar: fade them out over a few pixels
        for (y in 0 until 112) for (x in 0 until W) ga[y * W + x] *= ((y - 97f) / 12f).coerceIn(0f, 1f)
        // inside the rebuilt wing the traced outline decides (the mirrored background is not the real one)
        run {
            val sd = sdf(fortress, 3f)
            for (y in ty0 until ty1) for (x in tx0 until tx1) { val i = y * W + x; ga[i] = if (sd[i] > 0f) max(ga[i], if (y > 100) 1f else ga[i]) else 0f }
        }
        ga = clean(ga, 0.7f)
        writeCut("gate.png", fort, ga, 470, 80, 900, 410)

        // ---- the golem: its left fist runs off the reference's left edge; fade that side
        var gm = matte(golem, 7f)
        for (y in 0 until H) for (x in 0 until 30) gm[y * W + x] *= (x / 30f)
        gm = clean(gm, 0.7f)
        writeCut("guardian.png", px, gm, 0, 762, 452, 1058)

        // ---- the relic
        writeCut("relic.png", px, clean(matte(relic, 5f), 0.6f), 536, 832, 658, 1008)
        // ---- the spiked ball
        writeCut("mace.png", px, clean(matte(circle(781f, 985f, 58f), 11f), 0.6f), 716, 918, 848, 1052)
        // ---- the banner (a clean outline on a stone wall)
        val bm = FloatArray(W * H); polyMask(bm, banner, 0.5f)
        writeCut("banner.png", px, gauss(bm, 0.5f), 874, 798, 929, 903)

        // ---- the plate: sky, floating islands, the fortress's clouds, the cliffs with lava falls
        val hole = FloatArray(W * H)
        // the reference's HUD
        rectMask(hole, 0, 0, 368, 142); rectMask(hole, 370, 10, 840, 101); rectMask(hole, 838, 12, W, 121)
        rectMask(hole, 6, 138, 254, 264); rectMask(hole, 6, 242, 277, 397); rectMask(hole, tx0 - 2, ty0 - 2, tx1 + 2, ty1 + 2)
        // the fortress (drawn by the game as the gate), the loop and the track and bridges in front of it
        polyMask(hole, fortress, 6f)
        polyMask(hole, circle(537f, 497f, 128f), 2f)
        polyMask(hole, floatArrayOf(330f, 372f, 941f, 330f, 941f, 700f, 330f, 700f), 2f)
        // everything below the horizon is the game's own lava sea and course
        rectMask(hole, 0, 610, W, H)
        // the right side (the design's track and bridges, taken out) gets the left side's cliffs, lava falls and
        // castle island, mirrored: kept from the fill
        val src = px.copyOf()
        run {
            val y0 = 372; val y1 = 612; val x0 = 611
            for (y in y0 until y1) for (x in x0 until W) {
                val sxp = (330 - (x - x0) * 330 / (W - x0)).coerceIn(0, 329)
                val k = smoothstep(0f, 40f, (x - x0).toFloat()) * smoothstep(18f, 60f, (y - y0).toFloat())
                val i = y * W + x
                src[i] = mix(src[i], px[y * W + sxp], k)
                hole[i] = min(hole[i], 1f - k)
            }
        }
        // the middle (the design's loop and bridges, taken out): the same cliffs further away (smaller, hazier),
        // standing on the cloud bank at the horizon
        run {
            val sc = 0.72f
            val dx0 = 326; val dx1 = 615; val dy1 = 612
            val srcX0 = 0f; val srcY0 = 372f
            for (y in 372 until dy1) for (x in dx0 until dx1) {
                val sxf = srcX0 + (x - dx0) / sc; val syf = srcY0 + (y - (dy1 - (240 * sc).toInt())) / sc
                if (syf < srcY0 || syf >= 612f || sxf >= 330f) continue
                val v = px[syf.toInt() * W + sxf.toInt()]
                val edge = min(min((x - dx0).toFloat(), (dx1 - x).toFloat()), (y - (dy1 - 240 * sc)).toFloat())
                val k = smoothstep(0f, 26f, edge)
                val i = y * W + x
                src[i] = mix(src[i], mix(v, 0xFFF0B090.toInt(), 0.22f), k)
                hole[i] = min(hole[i], 1f - k)
            }
        }
        // soft edges: the fill fades into the reference over ~10 px
        val soft = gauss(hole, 5f)
        for (i in soft.indices) soft[i] = max(soft[i], if (hole[i] >= 1f) 0.999f else 0f)
        val plate = fill(src, soft)
        // below the islands: a warm lava haze that deepens toward the bottom (the 3D lava sea covers it in play)
        for (y in 560 until H) for (x in 0 until W) {
            val u = ((y - 560f) / 260f).coerceIn(0f, 1f)
            plate[y * W + x] = mix(plate[y * W + x], 0xFFE8702A.toInt(), u * 0.85f)
        }
        writeJpg("bg_plate.jpg", plate, W, H, 0.9f)
    }

    private fun mix(a: Int, b: Int, k: Float): Int {
        val rr = (r(a) + (r(b) - r(a)) * k).toInt(); val gg = (g(a) + (g(b) - g(a)) * k).toInt(); val bb = (b(a) + (b(b) - b(a)) * k).toInt()
        return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
    }
}
