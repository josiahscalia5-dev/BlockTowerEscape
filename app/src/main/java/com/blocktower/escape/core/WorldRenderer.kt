package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the 3D block course with a painter's algorithm: every visible object gets a
 * distance key, the list is sorted far-to-near, and textured cube faces are drawn with
 * perspective-mapped bitmaps.
 */
class WorldRenderer(val g: Game) {
    private val art get() = g.art
    private val cam get() = g.cam

    private object K { const val BLOCK = 0; const val COIN = 1; const val PLAYER = 2; const val BEACON = 3; const val PORTAL = 4
        const val GUARD = 5; const val DRAGON = 6; const val FIREBALL = 7; const val ROCK = 8; const val RIDE = 9 }

    private var n = 0
    private var kinds = IntArray(2048)
    private var refs = arrayOfNulls<Any>(2048)
    private var keys = LongArray(2048)

    private val q = FloatArray(8)
    private val cx = FloatArray(8)
    private val cy = FloatArray(8)
    private val cd = FloatArray(8)
    private val poly = FloatArray(64)
    private val hullIdx = IntArray(9)
    private var gfx: Gfx? = null
    private val skyHaze = 0xFFA8D0FA.toInt()
    private val maxDepth = 56f

    private fun push(kind: Int, ref: Any, d2: Float) {
        if (n >= kinds.size) {
            kinds = kinds.copyOf(n * 2); refs = refs.copyOf(n * 2); keys = keys.copyOf(n * 2)
        }
        kinds[n] = kind; refs[n] = ref
        keys[n] = (java.lang.Float.floatToRawIntBits(max(d2, 0f)).toLong() shl 20) or n.toLong()
        n++
    }

    fun render(gr: Gfx) {
        gfx = gr
        drawBackground(gr)
        if (g.ev.lavaY > -50f) drawLava(gr)
        collect()
        java.util.Arrays.sort(keys, 0, n)
        for (i in n - 1 downTo 0) {
            val idx = (keys[i] and 0xFFFFF).toInt()
            val r = refs[idx]!!
            when (kinds[idx]) {
                K.BLOCK -> drawBlock(gr, r as Block)
                K.COIN -> drawCoin(gr, r as Coin)
                K.PLAYER -> drawPlayer(gr)
                K.BEACON -> drawBeacon(gr, r as Checkpoint)
                K.PORTAL -> drawPortal(gr, r as Portal)
                K.GUARD -> drawGuard(gr)
                K.DRAGON -> drawDragon(gr)
                K.FIREBALL -> drawFireball(gr, r as Fireball)
                K.ROCK -> drawRock(gr, r as Rock)
                K.RIDE -> drawRidePlatform(gr)
            }
        }
        for (i in 0 until n) refs[i] = null
        drawParticles(gr)
        drawWorldPopups(gr)
        drawScreenEffects(gr)
    }

    // ------------------------------------------------------------------ background
    private fun drawBackground(gr: Gfx) {
        val h = g.hud
        val s = h.bgS
        gr.fillRect(0f, 0f, gr.width.toFloat(), gr.height.toFloat(), 0xFF0A3CA8.toInt())
        val sec = g.world.sectionAt(g.player.z)
        val ox = sec?.originX ?: 0f
        val px = clamp(-(g.camX - ox) * 7f, -60f, 60f) * s
        val climb = clamp((cam.ey - 3.17f) * 7f, 0f, 380f) * s
        val w = art.bg.w * s
        val hh = art.bg.h * s
        val left = gr.width * 0.5f - (art.bgArtX + 512f) * s + px
        val top = h.bgArtTop - art.bgArtY * s + climb
        gr.image(art.bg, left, top, w, hh)
        if (top > 0f) gr.fillRectGradient(0f, 0f, gr.width.toFloat(), top + 2f, 0xFF03287E.toInt(), 0xFF04349F.toInt())
        val st = g.ev.stormAmt
        if (st > 0.01f) gr.fillRect(0f, 0f, gr.width.toFloat(), gr.height.toFloat(), Col.withA(0xFF1A2440.toInt(), 0.42f * st))
    }

    // ------------------------------------------------------------------ collection
    private fun collect() {
        n = 0
        val p = g.player
        val r0 = floor(cam.ez - maxDepth).toInt()
        val r1 = floor(cam.ez + maxDepth).toInt()
        for (r in r0..r1) {
            val row = g.world.row(r) ?: continue
            for (b in row) {
                if (!b.visible || b.alpha <= 0.01f) continue
                val by = b.y - b.rise * 2.8f
                val d = cam.depthOf(b.x, by + b.sy * 0.5f, b.z + b.sz * 0.5f)
                if (d < -1.5f || d > maxDepth + 2f) continue
                if (b.type == BT.RESCUE && p.state == PS.RESCUE_RIDE) continue
                push(K.BLOCK, b, cam.dist2(b.x, by + b.sy * 0.5f, b.z + b.sz * 0.5f))
            }
        }
        for (c in g.world.coins) {
            if (c.collected) continue
            val d = cam.depthOf(c.x, c.y, c.z)
            if (d < 0.5f || d > maxDepth) continue
            push(K.COIN, c, cam.dist2(c.x, c.y, c.z))
        }
        for (cp in g.world.checkpoints) {
            if (cp.id == 0) continue
            val d = cam.depthOf(cp.x, cp.y + 2.6f, cp.z)
            if (d < 0.5f || d > maxDepth) continue
            push(K.BEACON, cp, cam.dist2(cp.x, cp.y + 2.2f, cp.z - 0.2f))
        }
        for (po in g.world.portals) {
            val d = cam.depthOf(po.x, po.y + 1.5f, po.z)
            if (d < 0.5f || d > maxDepth + 6f) continue
            push(K.PORTAL, po, cam.dist2(po.x, po.y + 1.3f, po.z))
        }
        if (p.state != PS.DEAD || p.y > p.lastGroundY - 20f) push(K.PLAYER, p, cam.dist2(p.x, p.y + 0.5f, p.z - 0.55f))
        if (p.state == PS.RESCUE_RIDE) push(K.RIDE, p, cam.dist2(p.x, p.y - 0.1f, p.z) + 0.5f)
        val gd = g.ev.guard
        if (gd.on) push(K.GUARD, gd, cam.dist2(gd.x, gd.y + 2f, gd.z - 0.3f))
        val dr = g.ev.dragon
        if (dr.on) push(K.DRAGON, dr, cam.dist2(dr.x, dr.y, dr.z))
        for (f in g.ev.fireballs) if (f.on) push(K.FIREBALL, f, cam.dist2(f.x, f.y, f.z))
        for (rk in g.ev.rocks) if (rk.on) push(K.ROCK, rk, cam.dist2(rk.x, rk.y + 0.35f, rk.z) - 0.3f)
    }

    // ------------------------------------------------------------------ cube drawing
    private fun projCorner(i: Int, x: Float, y: Float, z: Float): Boolean {
        val ok = cam.project(x, y, z)
        cx[i] = cam.sx; cy[i] = cam.sy; cd[i] = cam.depth
        return ok
    }

    private fun hazeFor(depth: Float) = smooth((depth - 15f) / 42f) * 0.62f

    /** Draws an axis-aligned textured box. Returns false when off-screen / clipped. */
    fun drawBox(gr: Gfx, x0: Float, y0In: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int, variant: Int,
                alpha: Float, flash: Float, cullSelf: Block?, overlay: Int = 0, glowAdd: Int = 0, glowAmt: Float = 0f) : Boolean {
        var y0 = y0In
        val lava = g.ev.lavaY
        if (lava > -50f) {
            if (y1 <= lava + 0.02f) return false
            if (y0 < lava) y0 = lava
        }
        // corners: 0..3 bottom (x0z0,x1z0,x1z1,x0z1), 4..7 top
        if (!projCorner(0, x0, y0, z0) || !projCorner(1, x1, y0, z0) || !projCorner(2, x1, y0, z1) || !projCorner(3, x0, y0, z1) ||
            !projCorner(4, x0, y1, z0) || !projCorner(5, x1, y1, z0) || !projCorner(6, x1, y1, z1) || !projCorner(7, x0, y1, z1)) return false
        var minX = cx[0]; var maxX = cx[0]; var minY = cy[0]; var maxY = cy[0]
        for (i in 1..7) { minX = min(minX, cx[i]); maxX = max(maxX, cx[i]); minY = min(minY, cy[i]); maxY = max(maxY, cy[i]) }
        if (maxX < -20f || minX > gr.width + 20f || maxY < -20f || minY > gr.height + 20f) return false
        val depth = (cd[4] + cd[6]) * 0.5f
        val haze = hazeFor(depth)
        var a = alpha
        if (depth > maxDepth - 10f) a *= 1f - smooth((depth - (maxDepth - 10f)) / 10f)
        if (a <= 0.01f) return false

        val ex = cam.ex; val ey = cam.ey; val ez = cam.ez
        val cb = cullSelf
        val showTop = ey > y1 && (cb == null || g.world.cell(cb.x, cb.y + 1f, cb.z) == null)
        val showBottom = ey < y0
        val showFront = ez < z0 && (cb == null || g.world.cell(cb.x, cb.y, cb.z - 1f) == null)
        val showBack = ez > z1 && (cb == null || g.world.cell(cb.x, cb.y, cb.z + 1f) == null)
        val showLeft = ex < x0 && (cb == null || g.world.cell(cb.x - 1f, cb.y, cb.z) == null)
        val showRight = ex > x1 && (cb == null || g.world.cell(cb.x + 1f, cb.y, cb.z) == null)
        if (!(showTop || showBottom || showFront || showBack || showLeft || showRight)) return false

        val tiny = (maxX - minX) < 5f && (maxY - minY) < 5f
        val c = color
        val topImg = art.top[c][variant]
        val sideImg = art.side[c][variant]
        val ts = art.texSize.toFloat()
        var addC = skyHaze; var addA = haze
        if (flash > haze) { addC = Col.WHITE; addA = flash * 0.75f }
        if (glowAmt > addA) { addC = glowAdd; addA = glowAmt }
        // lava heat
        if (lava > -50f && y0In < lava + 2f) { val h = (1f - clamp01((y0In - lava) / 2f)) * 0.4f; if (h > addA) { addC = 0xFFFF6A10.toInt(); addA = h } }

        // silhouette under the faces hides hairline seams
        if (a > 0.95f && !tiny) {
            val m = hull()
            for (k in 0 until m) { poly[k * 2] = cx[hullIdx[k]]; poly[k * 2 + 1] = cy[hullIdx[k]] }
            gr.fillPoly(poly, m, Col.mix(BC.dark(c), skyHaze, haze))
        }
        if (tiny) {
            val m = hull()
            for (k in 0 until m) { poly[k * 2] = cx[hullIdx[k]]; poly[k * 2 + 1] = cy[hullIdx[k]] }
            gr.fillPoly(poly, m, Col.withA(Col.mix(BC.base(c), skyHaze, haze), a))
            return true
        }
        val vFull = ts
        val vClip = if (y0 > y0In) ts * (y1 - y0) / (y1 - y0In) else ts
        if (showBottom) { quad(0, 1, 2, 3); gr.imageQuad(sideImg, 0f, 0f, ts, ts, q, a, 0.5f, addC, addA) }
        if (showBack) { quad(6, 7, 3, 2); gr.imageQuad(sideImg, 0f, 0f, ts, vClip, q, a, 0.7f, addC, addA); faceOverlay(gr, overlay, a, 1, depth) }
        if (showLeft) { quad(7, 4, 0, 3); gr.imageQuad(sideImg, 0f, 0f, ts, vClip, q, a, 0.7f, addC, addA); faceOverlay(gr, overlay, a, 2, depth) }
        if (showRight) { quad(5, 6, 2, 1); gr.imageQuad(sideImg, 0f, 0f, ts, vClip, q, a, 0.7f, addC, addA); faceOverlay(gr, overlay, a, 3, depth) }
        if (showFront) { quad(4, 5, 1, 0); gr.imageQuad(sideImg, 0f, 0f, ts, vClip, q, a, 0.86f, addC, addA); faceOverlay(gr, overlay, a, 4, depth) }
        if (showTop) { quad(7, 6, 5, 4); gr.imageQuad(topImg, 0f, 0f, ts, vFull, q, a, 1.06f, addC, addA); faceOverlay(gr, overlay, a, 5, depth) }
        return true
    }

    private fun quad(a: Int, b: Int, c: Int, d: Int) {
        q[0] = cx[a]; q[1] = cy[a]; q[2] = cx[b]; q[3] = cy[b]; q[4] = cx[c]; q[5] = cy[c]; q[6] = cx[d]; q[7] = cy[d]
    }

    /** Convex hull (gift wrapping) of the 8 projected corners; fills hullIdx, returns count. */
    private fun hull(): Int {
        var start = 0
        for (i in 1..7) if (cx[i] < cx[start] || (cx[i] == cx[start] && cy[i] < cy[start])) start = i
        var m = 0; var p = start
        do {
            hullIdx[m++] = p
            var qn = (p + 1) % 8
            for (i in 0..7) {
                val cr = (cx[qn] - cx[p]) * (cy[i] - cy[p]) - (cy[qn] - cy[p]) * (cx[i] - cx[p])
                if (cr < 0f || (cr == 0f && sq(cx[i] - cx[p]) + sq(cy[i] - cy[p]) > sq(cx[qn] - cx[p]) + sq(cy[qn] - cy[p]))) qn = i
            }
            p = qn
        } while (p != start && m < 8)
        return m
    }

    private object OV { const val NONE = 0; const val MYSTERY_G = 1; const val MYSTERY_R = 2; const val BOOST = 3; const val SAVE = 4
        const val BOUNCE = 5; const val TRAP = 6; const val RUNE_OFF = 7; const val RUNE_ON = 8; const val CRACK1 = 9; const val CRACK2 = 10
        const val CRACK3 = 11; const val SHIFT = 12; const val ENERGY = 13 }

    private val qq = FloatArray(8)
    /** Emblem drawn onto the face currently held in q. face: 1 back, 2 left, 3 right, 4 front, 5 top */
    private fun faceOverlay(gr: Gfx, ov: Int, a: Float, face: Int, depth: Float) {
        if (ov == OV.NONE) return
        if (depth > 40f && ov != OV.BOOST && ov != OV.SAVE) return
        val img: Img
        var inset = 0.12f
        var oa = a
        var add = 0; var addA = 0f
        when (ov) {
            OV.MYSTERY_G -> { if (face == 5) return; img = art.qPlate; inset = 0.14f }
            OV.MYSTERY_R -> { if (face == 5) return; img = art.qOrange; inset = 0.1f }
            OV.BOOST -> { img = if (face == 5) art.star else art.frameGlow; inset = if (face == 5) 0.14f else 0f
                gr.setAdditive(true)
                gr.imageQuad(art.frameGlow, 0f, 0f, art.frameGlow.w.toFloat(), art.frameGlow.h.toFloat(), q, a * (0.8f + 0.2f * pulse(g.t, 5f)))
                gr.setAdditive(false) }
            OV.SAVE -> { if (face != 5 && face != 4) return; img = art.saveEmblem; inset = 0.12f }
            OV.BOUNCE -> { if (face != 5) return; img = art.chevrons; inset = 0.1f }
            OV.TRAP -> { if (face != 5) return; img = art.trapHoles; inset = 0.02f }
            OV.RUNE_OFF -> { if (face != 5) return; img = art.runeOff; inset = 0.04f }
            OV.RUNE_ON -> { if (face != 5) return; img = art.runeOn; inset = 0.04f; oa = a * (0.8f + 0.2f * pulse(g.t, 4f)) }
            OV.CRACK1 -> { if (face != 5 && face != 4) return; img = art.cracks[0]; inset = 0f }
            OV.CRACK2 -> { if (face != 5 && face != 4) return; img = art.cracks[1]; inset = 0f }
            OV.CRACK3 -> { if (face != 5 && face != 4) return; img = art.cracks[2]; inset = 0f }
            OV.SHIFT -> { img = art.frameGlow; inset = 0f; oa = a * (0.55f + 0.45f * pulse(g.t, 6f)); add = 0xFFFFFFFF.toInt(); addA = 0.2f }
            OV.ENERGY -> { img = art.frameGlow; inset = 0f; oa = a * 0.9f }
            else -> return
        }
        insetQuad(inset)
        gr.imageQuad(img, 0f, 0f, img.w.toFloat(), img.h.toFloat(), qq, oa, 1f, add, addA)
    }

    private fun insetQuad(t: Float) {
        if (t <= 0f) { for (i in 0..7) qq[i] = q[i]; return }
        // bilinear inset of the (projected) quad; fine for small insets
        val ax = q[0]; val ay = q[1]; val bx = q[2]; val by = q[3]; val cx2 = q[4]; val cy2 = q[5]; val dx = q[6]; val dy = q[7]
        fun lx(u: Float, v: Float) = (ax * (1 - u) + bx * u) * (1 - v) + (dx * (1 - u) + cx2 * u) * v
        fun ly(u: Float, v: Float) = (ay * (1 - u) + by * u) * (1 - v) + (dy * (1 - u) + cy2 * u) * v
        val a = t; val b = 1f - t
        qq[0] = lx(a, a); qq[1] = ly(a, a); qq[2] = lx(b, a); qq[3] = ly(b, a)
        qq[4] = lx(b, b); qq[5] = ly(b, b); qq[6] = lx(a, b); qq[7] = ly(a, b)
    }

    private fun drawBlock(gr: Gfx, b: Block) {
        val t = g.t
        var x = b.x; var y = b.y
        if (b.shake > 0f) { x += sin(t * 70f) * 0.05f; y += cos(t * 83f) * 0.03f }
        y -= b.rise * 2.8f
        if (b.bump > 0f) y += sin(b.bump * Math.PI.toFloat()) * 0.28f
        var sy = b.sy
        if (b.squash > 0f) sy *= 1f - 0.22f * sin(b.squash * Math.PI.toFloat())
        var alpha = b.alpha
        if (b.rise > 0f) alpha *= 1f - b.rise * 0.8f
        var color = b.color
        var overlay = OV.NONE
        var glowC = 0; var glowA = 0f
        when (b.type) {
            BT.MYSTERY -> overlay = if (b.color == BC.RED) OV.MYSTERY_R else OV.MYSTERY_G
            BT.BOOST -> { overlay = OV.BOOST; color = BC.BLUE; glowC = 0xFF7FE6FF.toInt(); glowA = 0.08f + 0.08f * pulse(t, 5f) }
            BT.SAVE -> { overlay = OV.SAVE; glowC = 0xFFFFFFD0.toInt(); glowA = 0.1f + 0.15f * pulse(t, 4f) }
            BT.BOUNCE -> overlay = OV.BOUNCE
            BT.TRAP -> overlay = OV.TRAP
            BT.CHECKPOINT -> overlay = if (g.world.checkpoints.getOrNull(b.checkpointId)?.active == true) OV.RUNE_ON else OV.RUNE_OFF
            BT.CRACKED -> overlay = when { b.damage > 0.75f -> OV.CRACK3; b.damage > 0.35f -> OV.CRACK2; else -> OV.CRACK1 }
            BT.FALLING -> overlay = if (b.state >= 1) OV.CRACK2 else OV.CRACK1
            BT.COLORSHIFT -> { color = g.shiftColor(b); overlay = OV.SHIFT }
            BT.TOOLBLOCK, BT.RESCUE -> { overlay = OV.ENERGY; glowC = Col.WHITE; glowA = 0.15f + 0.15f * pulse(t, 7f) }
            BT.TARGET -> { glowC = 0xFF9FD8FF.toInt(); glowA = 0.06f * pulse(t + b.z * 0.7f, 3f) }
            else -> {}
        }
        val static = b.type != BT.MOVING && b.rise <= 0f && !b.destroyed && b.shake <= 0f && b.bump <= 0f && b.squash <= 0f &&
            b.alpha >= 0.99f && b.sx == 1f && b.sy == 1f && b.sz == 1f && b.type != BT.FALLING
        val ok = drawBox(gr, x - b.sx * 0.5f, y, b.z, x + b.sx * 0.5f, y + sy, b.z + b.sz, color, b.variant, alpha, b.flash,
            if (static) b else null, overlay, glowC, glowA)
        if (!ok) return
        if (b.type == BT.TRAP && b.spike > 0.02f) drawSpikes(gr, b)
        if (b.type == BT.TARGET && ((t * 0.7f + b.z * 0.37f + b.x * 0.21f) % 3f) < 0.35f) {
            // twinkle so blue target blocks read as collectibles
            val u = ((t * 0.7f + b.z * 0.37f + b.x * 0.21f) % 3f) / 0.35f
            if (cam.project(b.x - 0.3f + 0.6f * fract(b.z * 0.31f), b.y + 1.02f, b.z + 0.3f)) {
                val sz = cam.scaleAt(cam.depth) * 0.22f * sin(u * Math.PI.toFloat())
                star4(gr, cam.sx, cam.sy, sz, 0xFFFFFFFF.toInt())
            }
        }
    }

    private fun drawSpikes(gr: Gfx, b: Block) {
        val h = 0.5f * b.spike
        for (j in 2 downTo 0) for (i in 0..2) {
            val bx = b.x0 + (i + 0.5f) / 3f; val bz = b.z + (j + 0.5f) / 3f; val by = b.y1
            val r = 0.13f
            if (!cam.project(bx, by + h, bz)) continue
            val apx = cam.sx; val apy = cam.sy
            if (!cam.project(bx - r, by, bz - r)) continue
            val ax = cam.sx; val ay = cam.sy
            if (!cam.project(bx + r, by, bz - r)) continue
            val bxs = cam.sx; val bys = cam.sy
            if (!cam.project(bx + r, by, bz + r)) continue
            val cxs = cam.sx; val cys = cam.sy
            if (!cam.project(bx - r, by, bz + r)) continue
            val dxs = cam.sx; val dys = cam.sy
            poly[0] = dxs; poly[1] = dys; poly[2] = ax; poly[3] = ay; poly[4] = apx; poly[5] = apy
            gr.fillPoly(poly, 3, 0xFF8A90A8.toInt())
            poly[0] = bxs; poly[1] = bys; poly[2] = cxs; poly[3] = cys; poly[4] = apx; poly[5] = apy
            gr.fillPoly(poly, 3, 0xFF6A7088.toInt())
            poly[0] = ax; poly[1] = ay; poly[2] = bxs; poly[3] = bys; poly[4] = apx; poly[5] = apy
            gr.fillPolyGradient(poly, 3, apx, apy, ax, ay, 0xFFFFFFFF.toInt(), 0xFFB8C0D8.toInt())
        }
    }

    // ------------------------------------------------------------------ coins
    private fun drawCoin(gr: Gfx, c: Coin) {
        val bob = sin(c.phase * 2.6f + c.oz) * 0.07f
        if (!cam.project(c.x, c.y + bob, c.z)) return
        val px = cam.scaleAt(cam.depth)
        val hgt = 0.82f * px
        if (hgt < 2f) return
        val wid = hgt * art.coin.w / art.coin.h
        val spin = cos(g.t * 3.2f + c.oz * 0.9f)
        val sxs = max(0.18f, abs(spin))
        val a = (1f - smooth((cam.depth - (maxDepth - 10f)) / 10f)) * smooth((cam.depth - 2.2f) / 1.6f)
        if (a <= 0.01f) return
        gr.setAdditive(true)
        gr.glow(cam.sx, cam.sy, hgt * 1.25f, Col.withA(0xFFFFC830.toInt(), 0.75f * a))
        gr.setAdditive(false)
        gr.save()
        gr.translate(cam.sx, cam.sy)
        gr.scale(if (spin < 0f) -sxs else sxs, 1f)
        gr.image(art.coin, -wid * 0.5f, -hgt * 0.5f, wid, hgt, a)
        gr.restore()
    }

    // ------------------------------------------------------------------ player
    private fun groundBelow(x: Float, y: Float, z: Float): Float {
        var best = -1000f
        for (r in floor(z - 0.3f).toInt()..floor(z + 0.3f).toInt()) {
            val row = g.world.row(r) ?: continue
            for (b in row) {
                if (!b.collides() && b.type != BT.RESCUE) continue
                if (x + 0.2f > b.x0 && x - 0.2f < b.x1 && z + 0.2f > b.z && z - 0.2f < b.z1 && b.y1 <= y + 0.05f && b.y1 > best) best = b.y1
            }
        }
        return best
    }

    private fun drawShadow(gr: Gfx, x: Float, z: Float, gy: Float, strength: Float, rx: Float, rz: Float) {
        var m = 0
        for (i in 0 until 14) {
            val a = i / 14f * TAU
            if (!cam.project(x + cos(a) * rx, gy + 0.01f, z + sin(a) * rz)) return
            poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
        }
        gr.fillPoly(poly, m, Col.withA(0xFF10081A.toInt(), 0.38f * strength))
    }

    private fun drawPlayer(gr: Gfx) {
        val p = g.player
        val t = g.t
        if (p.state == PS.RESCUE_FALL || p.state == PS.NORMAL || p.state == PS.WIN) {
            val gy = groundBelow(p.x, p.y, p.z)
            if (gy > -999f) {
                val hgt = p.y - gy
                drawShadow(gr, p.x, p.z, gy, clamp01(1f - hgt / 6f), 0.36f * (1f + hgt * 0.05f), 0.26f * (1f + hgt * 0.05f))
            }
        }
        if (p.invuln > 0f && p.state == PS.NORMAL && ((t * 16f).toInt() and 1) == 1) return
        // sprite metrics: the boy is ~1.95 world units tall at the camera's framing distance
        val worldH = 1.95f
        var bob = 0f
        val speed = len2(p.vx, p.vz)
        if (p.grounded && speed > 0.6f) bob = abs(sin(p.runPhase * Math.PI.toFloat())) * 0.07f
        val breathe = if (p.grounded && speed <= 0.6f) sin(t * 2.4f) * 0.012f else 0f
        if (!cam.project(p.x, p.y + bob, p.z)) return
        val fx0 = cam.sx; val fy0 = cam.sy
        val scale = cam.scaleAt(cam.depth)
        var hPx = worldH * scale
        val wPx = hPx * art.boy.w / art.boy.h
        var sxk = 1f; var syk = 1f + breathe
        if (p.squash > 0f) { syk *= 1f - p.squash; sxk *= 1f + p.squash * 0.7f }
        else if (p.squash < 0f) { syk *= 1f - p.squash; sxk *= 1f + p.squash * 0.5f }
        if (!p.grounded && p.state == PS.NORMAL) { syk *= 1f + clamp(p.vy * 0.004f, -0.03f, 0.05f) }
        val ps = p.portalScale
        sxk *= ps; syk *= ps
        var rot = -p.lean * 7f
        if (p.state == PS.RESCUE_FALL && !p.grounded) rot += sin(t * 14f) * 16f
        if (p.state == PS.CAUGHT || p.state == PS.PORTAL) rot += sin(p.spin) * 25f
        val flip = p.flip && p.grounded && speed > 0.6f
        // speed afterimages
        if (g.speedOn && speed > 1f) {
            for (k in 2 downTo 1) {
                if (!cam.project(p.x - p.vx * 0.035f * k, p.y + bob, p.z - p.vz * 0.035f * k)) continue
                spriteQuad(cam.sx, cam.sy, wPx * sxk, hPx * syk, rot, flip, cam.scaleAt(cam.depth) / scale)
                gr.imageQuad(art.boy, 0f, 0f, art.boy.w.toFloat(), art.boy.h.toFloat(), q, 0.28f / k, 1f, 0xFF60D8FF.toInt(), 0.6f)
            }
        }
        spriteQuad(fx0, fy0, wPx * sxk, hPx * syk, rot, flip, 1f)
        val hurt = p.hurtFlash
        gr.imageQuad(art.boy, 0f, 0f, art.boy.w.toFloat(), art.boy.h.toFloat(), q, 1f, 1f,
            if (hurt > 0f) 0xFFFF3030.toInt() else 0, if (hurt > 0f) hurt * 0.55f else 0f)
        hPx *= syk
        val midX = fx0; val midY = fy0 - hPx * 0.45f
        if (g.shieldOn) {
            val r = hPx * 0.62f * (1f + 0.03f * sin(t * 6f))
            val left = g.tools[TK.SHIELD].active
            val a = if (left < 2f && ((t * 8f).toInt() and 1) == 0) 0.4f else 1f
            gr.setAdditive(true)
            gr.glow(midX, midY, r * 1.05f, Col.withA(0xFF3FA8FF.toInt(), 0.35f * a))
            gr.setAdditive(false)
            gr.strokeCircle(midX, midY, r, 5f * g.hud.s, Col.withA(0xFFB8ECFF.toInt(), 0.85f * a))
            gr.arc(midX, midY, r * 0.86f, 200f + t * 40f, 70f, 4f * g.hud.s, Col.withA(Col.WHITE, 0.7f * a))
        }
        if (g.magnetOn) {
            for (k in 0..2) {
                val ph = fract(t * 0.9f + k / 3f)
                val rr = hPx * (0.3f + ph * 0.7f)
                gr.strokeCircle(midX, midY + hPx * 0.2f, rr, 4f * g.hud.s, Col.withA(0xFFFF5A5A.toInt(), 0.6f * (1f - ph)))
            }
        }
    }

    /** Builds q for a sprite anchored at its feet (ax, ay). */
    private fun spriteQuad(ax: Float, ay: Float, w: Float, h: Float, rotDeg: Float, flip: Boolean, k: Float) {
        val ww = w * k; val hh = h * k
        val anchorU = 0.5f; val anchorV = 0.955f
        val l = -ww * anchorU; val r = ww * (1f - anchorU); val tp = -hh * anchorV; val bt = hh * (1f - anchorV)
        val rad = rotDeg * Math.PI.toFloat() / 180f
        val cs = cos(rad); val sn = sin(rad)
        fun px(x: Float, y: Float) = ax + x * cs - y * sn
        fun py(x: Float, y: Float) = ay + x * sn + y * cs
        val xl = if (flip) r else l; val xr = if (flip) l else r
        q[0] = px(xl, tp); q[1] = py(xl, tp)
        q[2] = px(xr, tp); q[3] = py(xr, tp)
        q[4] = px(xr, bt); q[5] = py(xr, bt)
        q[6] = px(xl, bt); q[7] = py(xl, bt)
    }

    private fun drawRidePlatform(gr: Gfx) {
        val p = g.player
        var m = 0
        for (i in 0 until 16) {
            val a = i / 16f * TAU
            if (!cam.project(p.x + cos(a) * 1.1f, p.y - 0.02f, p.z + sin(a) * 1.1f)) return
            poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
        }
        gr.setAdditive(true)
        gr.fillPoly(poly, m, 0x8840B8FF.toInt())
        gr.setAdditive(false)
        gr.strokePoly(poly, m, 5f * g.hud.s, 0xFFD8F6FF.toInt())
    }

    // ------------------------------------------------------------------ checkpoint beacon
    private fun drawBeacon(gr: Gfx, cp: Checkpoint) {
        val t = g.t
        val hover = 2.7f + sin(t * 2f + cp.id) * 0.12f
        if (!cam.project(cp.x, cp.y + hover, cp.z)) return
        val s = cam.scaleAt(cam.depth)
        val sx = cam.sx; val sy = cam.sy
        val on = cp.active
        if (on) {
            // light beam
            if (cam.project(cp.x, cp.y, cp.z)) {
                val by = cam.sy
                val bw = 0.55f * s
                gr.setAdditive(true)
                gr.fillRectGradient(sx - bw, sy - 3.5f * s, sx + bw, by, 0x00FFE070, 0x66FFE070)
                gr.setAdditive(false)
            }
        }
        val r = 0.34f * s
        val col = if (on) 0xFFFFD84A.toInt() else 0xFF8FB4F0.toInt()
        val hi = if (on) 0xFFFFF6C0.toInt() else 0xFFE0ECFF.toInt()
        gr.setAdditive(true)
        gr.glow(sx, sy, r * (if (on) 3.2f else 2f), Col.withA(col, if (on) 0.7f else 0.35f))
        gr.setAdditive(false)
        val w = r * (0.65f + 0.35f * abs(cos(t * 1.8f)))
        poly[0] = sx; poly[1] = sy - r * 1.4f; poly[2] = sx + w; poly[3] = sy; poly[4] = sx; poly[5] = sy + r * 1.4f; poly[6] = sx - w; poly[7] = sy
        gr.fillPolyGradient(poly, 4, sx - w, sy - r, sx + w, sy + r, hi, col)
        poly[0] = sx; poly[1] = sy - r * 1.4f; poly[2] = sx + w * 0.35f; poly[3] = sy; poly[4] = sx; poly[5] = sy + r * 1.4f
        gr.fillPoly(poly, 3, Col.withA(Col.WHITE, 0.45f))
        if (on) for (k in 0..3) {
            val a = t * 1.5f + k * TAU / 4f
            star4(gr, sx + cos(a) * r * 2.2f, sy + sin(a) * r * 0.8f, r * 0.35f, 0xFFFFF0A0.toInt())
        }
    }

    // ------------------------------------------------------------------ portals
    private fun drawPortal(gr: Gfx, po: Portal) {
        val t = g.t
        val rad = if (po.isEnd) 1.45f else 1.1f
        val cyW = po.y + if (po.isEnd) 1.9f else 1.3f
        val spin = t * (1.6f + po.charge * 5f)
        // swirl disc (rotating quad in the portal plane)
        val rr = rad * 1.42f
        for (k in 0..3) {
            val a = spin + k * TAU / 4f + TAU / 8f
            if (!cam.project(po.x + cos(a) * rr, cyW + sin(a) * rr, po.z)) return
            q[k * 2] = cam.sx; q[k * 2 + 1] = cam.sy
        }
        if (!cam.project(po.x, cyW, po.z)) return
        val csx = cam.sx; val csy = cam.sy
        val s = cam.scaleAt(cam.depth)
        gr.setAdditive(true)
        gr.glow(csx, csy, rad * s * 1.9f, Col.withA(if (po.isEnd) 0xFFFF7AE0.toInt() else 0xFFB070FF.toInt(), 0.55f + 0.3f * po.charge))
        gr.setAdditive(false)
        gr.imageQuad(art.swirl, 0f, 0f, art.swirl.w.toFloat(), art.swirl.h.toFloat(), q, 0.95f, 1f, Col.WHITE, po.charge * 0.5f)
        // second counter-rotating layer
        for (k in 0..3) {
            val a = -spin * 1.3f + k * TAU / 4f
            cam.project(po.x + cos(a) * rr * 0.75f, cyW + sin(a) * rr * 0.75f, po.z)
            q[k * 2] = cam.sx; q[k * 2 + 1] = cam.sy
        }
        gr.setAdditive(true)
        gr.imageQuad(art.swirl, 0f, 0f, art.swirl.w.toFloat(), art.swirl.h.toFloat(), q, 0.55f)
        gr.setAdditive(false)
        // glowing ring
        var m = 0
        for (i in 0 until 28) {
            val a = i / 28f * TAU
            cam.project(po.x + cos(a) * rad, cyW + sin(a) * rad, po.z)
            poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
        }
        gr.strokePoly(poly, m, 0.16f * s, if (po.isEnd) 0xFFFFB0F0.toInt() else 0xFFD8B0FF.toInt())
        gr.strokePoly(poly, m, 0.06f * s, Col.WHITE)
        if (po.isEnd) drawFlames(gr, po, rad, cyW)
        // sparkles
        for (k in 0..5) {
            val a = t * 2f + k * TAU / 6f
            if (cam.project(po.x + cos(a) * rad * 1.1f, cyW + sin(a) * rad * 1.1f, po.z)) star4(gr, cam.sx, cam.sy, 0.12f * s, 0xFFFFE0FF.toInt())
        }
    }

    private fun drawFlames(gr: Gfx, po: Portal, rad: Float, cyW: Float) {
        val t = g.t
        for (k in 0 until 9) {
            val a = Math.PI.toFloat() * (k / 8f)
            val fx0 = po.x + cos(a) * (rad + 0.55f); val fy0 = cyW + sin(a) * (rad + 0.45f)
            if (!cam.project(fx0, fy0, po.z - 0.3f)) continue
            val s = cam.scaleAt(cam.depth)
            val h = (0.55f + 0.2f * sin(t * 9f + k * 1.7f)) * s
            val w = 0.22f * s
            flame(gr, cam.sx, cam.sy, w, h)
        }
    }

    private fun flame(gr: Gfx, x: Float, y: Float, w: Float, h: Float) {
        gr.setAdditive(true)
        gr.glow(x, y - h * 0.3f, h * 0.8f, 0x66FF7A20)
        gr.setAdditive(false)
        poly[0] = x - w; poly[1] = y; poly[2] = x - w * 0.6f; poly[3] = y - h * 0.55f; poly[4] = x; poly[5] = y - h
        poly[6] = x + w * 0.6f; poly[7] = y - h * 0.5f; poly[8] = x + w; poly[9] = y
        gr.fillPolyGradient(poly, 5, x, y - h, x, y, 0xFFFFE070.toInt(), 0xFFFF5A10.toInt())
        poly[0] = x - w * 0.45f; poly[1] = y; poly[2] = x; poly[3] = y - h * 0.55f; poly[4] = x + w * 0.45f; poly[5] = y
        gr.fillPoly(poly, 3, 0xFFFFF6C0.toInt())
    }

    // ------------------------------------------------------------------ guard (block golem)
    private fun drawGuard(gr: Gfx) {
        val gd = g.ev.guard
        val t = g.t
        val baseY = gd.y - (1f - gd.rise) * 7f
        val sw = sin(gd.step * Math.PI.toFloat())
        val bob = abs(sw) * 0.18f
        val gx = gd.x; val gz = gd.z
        val c = BC.IRON; val b = BC.BRICK
        val fall = if (gd.falling) gd.fallT else 0f
        // painter order within the golem: legs, torso, arms, head (from the camera's side)
        val lookBack = abs(cam.yaw) > 1.5f
        fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, col: Int, v: Int) {
            drawBox(gr, gx + x0, baseY + y0 + bob, gz + z0, gx + x1, baseY + y1 + bob, gz + z1, col, v, 1f, 0f, null)
        }
        val ls = sw * 0.45f
        val reach = gd.reach
        // legs
        box(-1.05f, 0f, -0.45f + ls, -0.2f, 1.7f, 0.45f + ls, b, 0)
        box(0.2f, 0f, -0.45f - ls, 1.05f, 1.7f, 0.45f - ls, b, 1)
        // torso
        box(-1.35f, 1.6f, -0.7f, 1.35f, 3.6f, 0.7f, c, 0)
        box(-1.1f, 1.9f, if (lookBack) 0.62f else -0.78f, 1.1f, 3.3f, if (lookBack) 0.78f else -0.62f, b, 2)
        // arms (reach forward when close)
        val az = 0.2f + reach * 1.2f
        box(-2.15f, 1.8f - reach * 0.2f, -0.45f + az * 0.3f - ls * 0.6f, -1.35f, 3.5f, 0.45f + az + ls * 0.6f, c, 1)
        box(1.35f, 1.8f - reach * 0.2f, -0.45f + az * 0.3f + ls * 0.6f, 2.15f, 3.5f, 0.45f + az - ls * 0.6f, c, 2)
        // head with horns
        box(-0.75f, 3.6f, -0.65f, 0.75f, 4.75f, 0.65f, c, 2)
        box(-1.0f, 4.45f, -0.2f, -0.72f, 5.15f, 0.2f, BC.STONE, 0)
        box(0.72f, 4.45f, -0.2f, 1.0f, 5.15f, 0.2f, BC.STONE, 1)
        // face (only visible from the front) — glowing eyes
        val fz = gz + 0.66f
        if (cam.ez > fz) {
            val y0 = baseY + 3.6f + bob; val y1 = baseY + 4.75f + bob
            if (cam.project(gx - 0.75f, y1, fz) ) { q[0] = cam.sx; q[1] = cam.sy
                cam.project(gx + 0.75f, y1, fz); q[2] = cam.sx; q[3] = cam.sy
                cam.project(gx + 0.75f, y0, fz); q[4] = cam.sx; q[5] = cam.sy
                cam.project(gx - 0.75f, y0, fz); q[6] = cam.sx; q[7] = cam.sy
                // mirrored because we look at the +z face from the front
                val qx0 = q[0]; val qy0 = q[1]; q[0] = q[2]; q[1] = q[3]; q[2] = qx0; q[3] = qy0
                val qx3 = q[6]; val qy3 = q[7]; q[6] = q[4]; q[7] = q[5]; q[4] = qx3; q[5] = qy3
                gr.imageQuad(art.guardFace, 0f, 0f, art.guardFace.w.toFloat(), art.guardFace.h.toFloat(), q, 1f)
            }
        }
        // eye glow visible from behind as a halo
        if (cam.project(gx, baseY + 4.2f + bob, gz + 0.3f)) {
            val s = cam.scaleAt(cam.depth)
            gr.setAdditive(true)
            gr.glow(cam.sx, cam.sy, 1.4f * s, Col.withA(0xFFFF6A10.toInt(), 0.35f + 0.15f * pulse(t, 8f)))
            gr.setAdditive(false)
        }
        if (fall > 0f) return
    }

    // ------------------------------------------------------------------ dragon
    private fun drawDragon(gr: Gfx) {
        val d = g.ev.dragon
        if (!cam.project(d.x, d.y, d.z)) return
        val s = cam.scaleAt(cam.depth) * 1.6f
        val dir = if (d.vx < 0f) -1f else 1f
        val flap = sin(g.t * 6.5f)
        gr.save()
        gr.translate(cam.sx, cam.sy)
        gr.scale(dir * s / 100f, s / 100f)
        gr.rotate(-6f + sin(g.t * 1.3f) * 4f)
        DragonArt.draw(gr, flap, g.t, d.shots < 3 && d.t > 1.6f)
        gr.restore()
    }

    private fun drawFireball(gr: Gfx, f: Fireball) {
        if (!cam.project(f.x, f.y, f.z)) return
        val s = cam.scaleAt(cam.depth)
        gr.setAdditive(true)
        gr.glow(cam.sx, cam.sy, 0.9f * s, 0xCCFF7A10.toInt())
        gr.glow(cam.sx, cam.sy, 0.45f * s, 0xFFFFE070.toInt())
        gr.setAdditive(false)
    }

    private fun drawRock(gr: Gfx, r: Rock) {
        // warning marker where it will land
        if (!r.landed) {
            var m = 0
            val u = clamp01(1f - (r.y - r.ground) / 11f)
            for (i in 0 until 14) {
                val a = i / 14f * TAU
                if (!cam.project(r.x + cos(a) * 0.5f, r.ground + 0.02f, r.z + sin(a) * 0.5f)) { m = 0; break }
                poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
            }
            if (m > 0) {
                gr.fillPoly(poly, m, Col.withA(0xFFFF3020.toInt(), 0.25f + 0.35f * u))
                gr.strokePoly(poly, m, 3f * g.hud.s, Col.withA(0xFFFFD0C0.toInt(), 0.5f + 0.5f * u))
            }
        }
        drawBox(gr, r.x - 0.35f, r.y, r.z - 0.35f, r.x + 0.35f, r.y + 0.7f, r.z + 0.35f, BC.BRICK, 1, 1f, 0f, null)
    }

    // ------------------------------------------------------------------ lava
    private fun drawLava(gr: Gfx) {
        val ly = g.ev.lavaY
        val t = g.t
        val tile = 4f
        val ox = floor(cam.ex / tile) * tile
        val z0 = floor(cam.ez / tile) * tile
        val flow = fract(t * 0.08f) * tile
        for (j in 14 downTo 0) {
            for (i in -7..7) {
                val x0 = ox + i * tile; val zz = z0 + j * tile + flow
                if (!cam.project(x0, ly, zz)) continue; q[6] = cam.sx; q[7] = cam.sy
                if (!cam.project(x0 + tile, ly, zz)) continue; q[4] = cam.sx; q[5] = cam.sy
                if (!cam.project(x0 + tile, ly, zz + tile)) continue; q[2] = cam.sx; q[3] = cam.sy
                if (!cam.project(x0, ly, zz + tile)) continue; q[0] = cam.sx; q[1] = cam.sy
                val d = cam.depth
                gr.imageQuad(art.lava, 0f, 0f, art.lava.w.toFloat(), art.lava.h.toFloat(), q, 1f, 1f + 0.1f * sin(t * 3f + i + j), 0xFFFF9A40.toInt(), hazeFor(d) * 0.6f)
            }
        }
    }

    // ------------------------------------------------------------------ particles & popups
    private fun star4(gr: Gfx, x: Float, y: Float, r: Float, color: Int) {
        if (r < 0.8f) return
        val w = r * 0.28f
        poly[0] = x; poly[1] = y - r; poly[2] = x + w; poly[3] = y - w; poly[4] = x + r; poly[5] = y; poly[6] = x + w; poly[7] = y + w
        poly[8] = x; poly[9] = y + r; poly[10] = x - w; poly[11] = y + w; poly[12] = x - r; poly[13] = y; poly[14] = x - w; poly[15] = y - w
        gr.setAdditive(true)
        gr.glow(x, y, r * 0.9f, Col.withA(color, 0.5f))
        gr.setAdditive(false)
        gr.fillPoly(poly, 8, color)
    }

    private fun drawParticles(gr: Gfx) {
        for (p in g.fx.parts) {
            if (!p.active) continue
            if (!cam.project(p.x, p.y, p.z)) continue
            val s = cam.scaleAt(cam.depth)
            val r = p.size * s
            if (r < 0.6f) continue
            val lf = clamp01(p.life / p.maxLife)
            when (p.kind) {
                PK.SPARK, PK.EMBER -> { gr.setAdditive(true); gr.glow(cam.sx, cam.sy, r * 2.2f, Col.withA(p.color, lf)); gr.setAdditive(false) }
                PK.STAR -> star4(gr, cam.sx, cam.sy, r * 1.6f * (0.4f + 0.6f * lf), Col.withA(p.color, min(1f, lf * 1.5f)))
                PK.DUST -> gr.fillCircle(cam.sx, cam.sy, r, Col.withA(p.color, 0.55f * lf))
                PK.FIRE -> { gr.setAdditive(true); gr.glow(cam.sx, cam.sy, r * (1.8f - lf * 0.6f), Col.withA(Col.mix(0xFFFF3A10.toInt(), p.color, lf), lf)); gr.setAdditive(false) }
                PK.SHARD -> {
                    val cs = cos(p.rot) * r; val sn = sin(p.rot) * r
                    poly[0] = cam.sx + cs; poly[1] = cam.sy + sn; poly[2] = cam.sx - sn; poly[3] = cam.sy + cs
                    poly[4] = cam.sx - cs; poly[5] = cam.sy - sn; poly[6] = cam.sx + sn; poly[7] = cam.sy - cs
                    gr.fillPoly(poly, 4, Col.withA(p.color, min(1f, lf * 2f)))
                    poly[4] = cam.sx; poly[5] = cam.sy
                    gr.fillPoly(poly, 3, Col.withA(Col.WHITE, 0.35f * lf))
                }
            }
        }
    }

    private fun drawWorldPopups(gr: Gfx) {
        val s = g.hud.s
        for (p in g.fx.popups) {
            if (!p.active || !p.world) continue
            if (!cam.project(p.x, p.y + p.t * 0.9f, p.z)) continue
            val u = p.t / p.dur
            val a = if (u < 0.75f) 1f else 1f - (u - 0.75f) / 0.25f
            val pop = if (p.t < 0.15f) easeOutBack(p.t / 0.15f) else 1f
            val size = p.size * s * pop
            gr.text(p.text, cam.sx, cam.sy, size, Font.TITLE, p.color, Align.CENTER, size * 0.16f, 0xFF1A1030.toInt(), a)
        }
    }

    // ------------------------------------------------------------------ screen effects
    private fun drawScreenEffects(gr: Gfx) {
        val w = gr.width.toFloat(); val h = gr.height.toFloat()
        val s = g.hud.s
        val t = g.t
        val st = g.ev.stormAmt
        if (st > 0.01f) {
            // rain streaks
            val n = (70 * st).toInt()
            val wind = g.ev.windVX * 18f * s
            for (i in 0 until n) {
                val hx = fract(sin(i * 12.9898f) * 43758.547f)
                val hy = fract(sin(i * 78.233f) * 12345.678f)
                val x = (hx * (w + 200f) + t * (60f + wind * 3f)) % (w + 200f) - 100f
                val y = (hy * h + t * (1400f + hx * 500f) * s) % h
                gr.line(x, y, x - 8f * s - wind, y + 38f * s, 2.2f * s, Col.withA(0xFFD8E8FF.toInt(), 0.35f * st))
            }
            if (g.ev.windPhase >= 1) {
                val a = if (g.ev.windPhase == 2) 0.55f else 0.25f
                for (i in 0 until 14) {
                    val hy = fract(sin(i * 3.7f) * 999.1f)
                    val sp = 900f * s
                    var x = (fract(sin(i * 5.1f) * 777.7f) * w + t * sp * g.ev.windDir) % (w + 300f)
                    if (x < 0) x += w + 300f
                    x -= 150f
                    val y = hy * h
                    gr.line(x, y, x - g.ev.windDir * 160f * s, y, 3f * s, Col.withA(Col.WHITE, a * st))
                }
            }
        }
        if (g.ev.lightning > 0f) gr.fillRect(0f, 0f, w, h, Col.withA(0xFFE8F0FF.toInt(), g.ev.lightning * 0.55f))
        if (g.speedOn) {
            for (i in 0 until 18) {
                val a = i / 18f * TAU + t * 0.3f
                val ph = fract(t * 2.2f + i * 0.37f)
                val r0 = (0.35f + ph * 0.6f) * max(w, h) * 0.6f
                val x0 = w * 0.5f + cos(a) * r0; val y0 = h * 0.55f + sin(a) * r0
                val x1 = w * 0.5f + cos(a) * (r0 + 90f * s); val y1 = h * 0.55f + sin(a) * (r0 + 90f * s)
                gr.line(x0, y0, x1, y1, 3f * s, Col.withA(Col.WHITE, 0.35f * (1f - ph)))
            }
        }
        // lava heat glow
        val ly = g.ev.lavaY
        if (ly > -50f) {
            val d = g.player.y - ly
            val k = clamp01(1f - d / 6f)
            gr.fillRectGradient(0f, h * 0.55f, w, h, 0x00FF5010, Col.withA(0xFFFF5010.toInt(), 0.25f + 0.35f * k))
        }
        // guard danger glow at the bottom edge
        if (g.ev.chaseActive) {
            val k = g.ev.meter
            if (k > 0.45f) gr.fillRectGradient(0f, h * 0.7f, w, h, 0x00FF2010, Col.withA(0xFFFF2010.toInt(), (k - 0.45f) * 0.9f * (0.7f + 0.3f * pulse(t, 10f))))
        }
        if (g.flashRed > 0f) {
            gr.fillRectGradient(0f, 0f, w, h * 0.25f, Col.withA(0xFFFF1020.toInt(), 0.45f * g.flashRed), 0x00FF1020)
            gr.fillRectGradient(0f, h * 0.75f, w, h, 0x00FF1020, Col.withA(0xFFFF1020.toInt(), 0.45f * g.flashRed))
        }
        if (g.flashWhite > 0f) gr.fillRect(0f, 0f, w, h, Col.withA(Col.WHITE, g.flashWhite * 0.9f))
    }
}
