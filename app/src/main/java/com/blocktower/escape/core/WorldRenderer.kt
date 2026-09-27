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

    private object K { const val BLOCK = 0; const val COIN = 1; const val PLAYER = 2; const val BEACON = 3
        const val GUARD = 5; const val ROCK = 8; const val RIDE = 9; const val BUBBLE = 10; const val DECO = 11; const val LOG = 12
        const val PILLAR = 13; const val LOOPSEG = 14; const val RELIC = 15 }

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
        if (g.spec.volcano && g.seaY > -99f) drawLava(gr, g.seaY, true)
        if (g.ev.lavaY > -50f) drawLava(gr, g.ev.lavaY, false)
        playerBox()
        drawGate(gr)
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
                K.GUARD -> drawGuard(gr)
                K.ROCK -> drawRock(gr, r as Rock)
                K.RIDE -> drawRidePlatform(gr)
                K.BUBBLE -> drawBubble(gr, r as Bubble)
                K.DECO -> drawDeco(gr, r as Deco)
                K.LOG -> drawMace(gr, r as SwingLog)
                K.PILLAR -> drawPillar(gr, r as Pillar)
                K.LOOPSEG -> drawLoopSeg(gr, r as LoopSeg)
                K.RELIC -> drawRelic(gr)
            }
        }
        for (i in 0 until n) refs[i] = null
        drawParticles(gr)
        drawWorldPopups(gr)
        drawScreenEffects(gr)
    }

    // ------------------------------------------------------------------ background
    private fun drawBackground(gr: Gfx) {
        if (g.spec.volcano) { drawPlateBackground(gr); return }
        val h = g.hud
        val s = h.bgS
        gr.fillRect(0f, 0f, gr.width.toFloat(), gr.height.toFloat(), 0xFF0A3CA8.toInt())
        val px = clamp(-g.camX * 7f, -60f, 60f) * s
        val climb = clamp((cam.ey - 3.17f) * 7f, 0f, 380f) * s
        val w = art.bg.w * s
        val hh = art.bg.h * s
        val left = gr.width * 0.5f - (art.bgArtX + 512f) * s + px
        val top = h.bgArtTop - art.bgArtY * s + climb
        val roll = cam.roll
        val rolled = abs(roll) > 0.0005f
        if (rolled) {
            // the sky rolls with the camera; zoom slightly so no edge shows
            gr.save(); gr.translate(cam.cx, cam.cy); gr.rotate(roll * 57.29578f)
            val z = 1f + abs(roll) * 2.6f
            gr.scale(z, z); gr.translate(-cam.cx, -cam.cy)
        }
        gr.image(art.bg, left, top, w, hh)
        if (top > 0f) gr.fillRectGradient(-40f, -40f, gr.width + 40f, top + 2f, 0xFF03287E.toInt(), 0xFF04349F.toInt())
        if (rolled) gr.restore()
        // depth: a light sky haze over the far scenery (never a blur), so the blocks, the boy and the coins in
        // front stand out crisp and bright against it
        gr.fillRectGradient(0f, 0f, gr.width.toFloat(), gr.height.toFloat(), 0x1CC8DEFF, 0x2EC8DEFF)
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
        for (bb in g.world.bubbles) {
            if (bb.taken && bb.pop <= 0f) continue
            val d = cam.depthOf(bb.x, bb.y, bb.z)
            if (d < 0.5f || d > maxDepth) continue
            push(K.BUBBLE, bb, cam.dist2(bb.x, bb.y, bb.z))
        }
        for (cp in g.world.checkpoints) {
            if (cp.id == 0) continue
            val d = cam.depthOf(cp.x, cp.y + 2.6f, cp.z)
            if (d < 0.5f || d > maxDepth) continue
            push(K.BEACON, cp, cam.dist2(cp.x, cp.y + 2.2f, cp.z - 0.2f))
        }
        val plp = p.loop
        if (plp != null && p.state == PS.LOOP) {
            // on the loop: the body's middle, in from the track (drawn over the block he runs on)
            val u = p.loopU
            push(K.PLAYER, p, cam.dist2(p.x, p.y + 0.7f * plp.upY(u), p.z + 0.7f * plp.upZ(u)) - 0.6f)
        } else if (p.state != PS.DEAD || p.y > p.lastGroundY - 20f) push(K.PLAYER, p, cam.dist2(p.x, p.y + 0.5f, p.z - 0.55f))
        if (p.state == PS.RESCUE_RIDE) push(K.RIDE, p, cam.dist2(p.x, p.y - 0.1f, p.z) + 0.5f)
        val gd = g.ev.guard
        if (gd.on) push(K.GUARD, gd, cam.dist2(gd.x, gd.y + 2f, gd.z - 0.3f))
        for (pl in g.world.pillars) {
            val dd = cam.depthOf(pl.x, pl.top - 2f, pl.z)
            if (dd < -2f || dd > maxDepth + 4f) continue
            push(K.PILLAR, pl, cam.dist2(pl.x, pl.top - 2.5f, pl.z))
        }
        for (lp in g.world.loops) {
            if (cam.depthOf(lp.x0, lp.y0 + lp.r, lp.z0) > maxDepth + lp.r) continue
            for (sg in loopSegs(lp)) {
                val dd = cam.depthOf(sg.cx, sg.cy, sg.cz)
                if (dd < 0.3f || dd > maxDepth + 2f) continue
                push(K.LOOPSEG, sg, cam.dist2(sg.cx, sg.cy, sg.cz))
            }
        }
        val rl = g.relic
        if (rl.state != RS.NONE && rl.show > 0.01f) push(K.RELIC, rl, cam.dist2(rl.x, rl.y + 0.4f, rl.z))
        for (d in g.world.decos) {
            val dd = cam.depthOf(d.x, d.y + d.w * 0.2f, d.z)
            if (dd < 0.6f || dd > maxDepth) continue
            push(K.DECO, d, cam.dist2(d.x, d.y + d.w * 0.2f, d.z))
        }
        for (l in g.world.logs) {
            val dd = cam.depthOf(l.cx, l.cy, l.pz)
            if (dd < 0.6f || dd > maxDepth) continue
            push(K.LOG, l, cam.dist2(l.cx, l.cy, l.pz))
        }
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
        // anything right in front of the lens fades out instead of filling the screen
        val near = cam.depthOf((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, (z0 + z1) * 0.5f)
        if (near < 1.8f) a *= smooth((near - 0.7f) / 1.1f)
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
        const val CRACK3 = 11; const val SHIFT = 12; const val ENERGY = 13; const val SPRING = 14; const val LAVA = 15; const val TOOL = 16
        const val STAR = 17; const val STAR_Y = 18; const val PAD = 20 }

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
            OV.SPRING -> {
                // blue plate with gold corner brackets on top of the dark block (the design's spring pad)
                if (face != 5) { if (face == 4 || face == 2 || face == 3) goldBand(gr, a) ; return }
                insetQuad(0.04f)
                for (i in 0..7) poly[i] = qq[i]
                gr.fillPolyGradient(poly, 4, qq[0], qq[1], qq[4], qq[5], Col.withA(0xFF3A7CF0.toInt(), a), Col.withA(0xFF1A3FA8.toInt(), a))
                for (c in 0..3) {
                    val cxp = qq[c * 2]; val cyp = qq[c * 2 + 1]
                    val nx = qq[((c + 1) % 4) * 2]; val ny = qq[((c + 1) % 4) * 2 + 1]
                    val px = qq[((c + 3) % 4) * 2]; val py = qq[((c + 3) % 4) * 2 + 1]
                    poly[0] = cxp; poly[1] = cyp
                    poly[2] = cxp + (nx - cxp) * 0.22f; poly[3] = cyp + (ny - cyp) * 0.22f
                    poly[4] = cxp + (px - cxp) * 0.22f; poly[5] = cyp + (py - cyp) * 0.22f
                    gr.fillPoly(poly, 3, Col.withA(0xFFF2C040.toInt(), a))
                }
                return
            }
            OV.LAVA -> {
                if (face != 5 && face != 4 && face != 2 && face != 3) return
                val glow = 0.75f + 0.25f * pulse(g.t, 3.5f)
                val ci = art.cracks[if (face == 5) 2 else 1]
                gr.setAdditive(true)
                gr.imageQuad(ci, 0f, 0f, ci.w.toFloat(), ci.h.toFloat(), q, a * glow, 1f, 0xFFFF7A1A.toInt(), 1f)
                gr.setAdditive(false)
                gr.imageQuad(ci, 0f, 0f, ci.w.toFloat(), ci.h.toFloat(), q, a * 0.9f, 1f, 0xFFFFC040.toInt(), 1f)
                return
            }
            OV.TOOL -> { if (toolIcon == null) return; img = toolIcon!!; inset = 0.16f }
            OV.PAD -> {
                // the design's speed pad: glowing white chevrons pointing the way, pulsing forward
                if (face != 5) { if (face == 4) { img = art.frameGlow; inset = 0f; oa = a * 0.7f } else return }
                else {
                    gr.setAdditive(true)
                    gr.imageQuad(art.frameGlow, 0f, 0f, art.frameGlow.w.toFloat(), art.frameGlow.h.toFloat(), q, a * (0.7f + 0.3f * pulse(g.t, 5f)))
                    gr.setAdditive(false)
                    img = art.chevrons; inset = 0.12f; add = 0xFFFFFFFF.toInt(); addA = 0.55f + 0.3f * pulse(g.t * 1.4f, 3f)
                }
            }
            OV.STAR, OV.STAR_Y -> {
                // a glowing star on the front face (the design's star blocks)
                if (face != 4) return
                img = art.star; inset = 0.14f
                if (ov == OV.STAR_Y) { add = 0xFFFFD230.toInt(); addA = 0.75f }
                gr.setAdditive(true)
                insetQuad(0.04f)
                gr.imageQuad(art.star, 0f, 0f, art.star.w.toFloat(), art.star.h.toFloat(), qq, a * (0.45f + 0.2f * pulse(g.t, 3f)), 1f, add, addA)
                gr.setAdditive(false)
            }
            else -> return
        }
        insetQuad(inset)
        gr.imageQuad(img, 0f, 0f, img.w.toFloat(), img.h.toFloat(), qq, oa, 1f, add, addA)
    }

    private var toolIcon: Img? = null

    /** Thin gold band along the top edge of a side face (spring pad trim). */
    private fun goldBand(gr: Gfx, a: Float) {
        // q holds the face as top-left, top-right, bottom-right, bottom-left
        poly[0] = q[0]; poly[1] = q[1]; poly[2] = q[2]; poly[3] = q[3]
        poly[4] = q[2] + (q[4] - q[2]) * 0.12f; poly[5] = q[3] + (q[5] - q[3]) * 0.12f
        poly[6] = q[0] + (q[6] - q[0]) * 0.12f; poly[7] = q[1] + (q[7] - q[1]) * 0.12f
        gr.fillPoly(poly, 4, Col.withA(0xFFD9A030.toInt(), a))
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
        var flash = b.flash
        // scale about the block centre: a collected shell swells as it cracks, blocks pop when they settle
        var grow = 1f
        val objective = g.target < g.spec.targetNeed
        when (b.type) {
            BT.MYSTERY -> {
                val tool = b.reward - Reward.TOOL_MAGNET
                if (b.color == BC.BLUE && tool in 0..3) { overlay = OV.TOOL; toolIcon = toolImg(tool); glowC = 0xFFFFF0A0.toInt(); glowA = 0.05f + 0.06f * pulse(t, 4f) }
                else overlay = if (b.color == BC.RED || b.color == BC.YELLOW) OV.MYSTERY_R else OV.MYSTERY_G
            }
            BT.BOOST -> { overlay = OV.BOOST; color = BC.BLUE; glowC = 0xFF7FE6FF.toInt(); glowA = 0.08f + 0.08f * pulse(t, 5f) }
            BT.PAD -> { overlay = OV.PAD; color = BC.BLUE; glowC = 0xFF9AF0FF.toInt(); glowA = 0.1f + 0.1f * pulse(t * 1.3f + b.z * 0.4f, 4f) }
            BT.SAVE -> { overlay = OV.SAVE; glowC = 0xFFFFFFD0.toInt(); glowA = 0.1f + 0.15f * pulse(t, 4f) }
            BT.BOUNCE -> overlay = OV.SPRING
            BT.TRAP -> if (b.speed == 0f) overlay = OV.TRAP else { overlay = OV.TRAP
                // armed: the trap glows red just before the spikes pop
                val c = (t + b.phase) % 2.6f
                if (c in 1.25f..1.85f) { glowC = 0xFFFF3A2A.toInt(); glowA = 0.28f * sin(((c - 1.25f) / 0.6f) * Math.PI.toFloat()) } }
            BT.CHECKPOINT -> overlay = if (g.world.checkpoints.getOrNull(b.checkpointId)?.active == true) OV.RUNE_ON else OV.RUNE_OFF
            BT.CRACKED -> overlay = when { b.damage > 0.75f -> OV.CRACK3; b.damage > 0.35f -> OV.CRACK2; else -> OV.CRACK1 }
            BT.FALLING -> { overlay = OV.LAVA; if (b.state >= 1) { glowC = 0xFFFF5A10.toInt(); glowA = 0.25f } }
            BT.COLORSHIFT -> { color = g.shiftColor(b); overlay = OV.SHIFT }
            BT.TOOLBLOCK, BT.RESCUE -> { overlay = OV.ENERGY; glowC = Col.WHITE; glowA = 0.15f + 0.15f * pulse(t, 7f) }
            BT.TARGET -> { glowC = 0xFF9FD8FF.toInt(); glowA = if (objective) 0.07f + 0.09f * pulse(t + b.z * 0.7f, 3f) else 0.03f }
            else -> if (b.star) { overlay = if (b.color == BC.GREEN) OV.STAR_Y else OV.STAR; glowC = if (b.color == BC.GREEN) 0xFFFFF0A0.toInt() else 0xFF7FE6FF.toInt(); glowA = 0.05f + 0.05f * pulse(t, 3f) }
        }
        if (b.crackT > 0f) {
            val u = b.crackT
            overlay = if (u < 0.34f) OV.CRACK1 else if (u < 0.67f) OV.CRACK2 else OV.CRACK3
            flash = max(flash, u * 0.55f); grow = 1f + 0.07f * u
        }
        if (b.pop > 0f) grow *= 1f + 0.09f * sin(b.pop * Math.PI.toFloat())
        val hx = b.sx * 0.5f * grow; val hz = b.sz * 0.5f * grow; val hy = sy * grow
        val cz = b.z + b.sz * 0.5f
        val y0 = y - (hy - sy) * 0.5f
        val static = b.type != BT.MOVING && b.rise <= 0f && !b.destroyed && b.shake <= 0f && b.bump <= 0f && b.squash <= 0f &&
            b.alpha >= 0.99f && b.sx == 1f && b.sy == 1f && b.sz == 1f && b.type != BT.FALLING && grow == 1f
        val ok = drawBox(gr, x - hx, y0, cz - hz, x + hx, y0 + hy, cz + hz, color, b.variant, alpha, flash,
            if (static) b else null, overlay, glowC, glowA)
        if (!ok) return
        if (b.type == BT.TRAP && b.spike > 0.02f) drawSpikes(gr, b)
        if (b.type == BT.BOUNCE) drawSpring(gr, b, y0 + hy)
        if (b.type == BT.TARGET && objective) {
            // the current objective: a soft light breathing on the top face, and a twinkle now and then
            if (cam.project(b.x, b.y1 + 0.05f, b.z + 0.5f)) {
                val sc = cam.scaleAt(cam.depth)
                val a = (0.16f + 0.12f * pulse(t + b.z * 0.7f, 3f)) * (1f - smooth((cam.depth - 30f) / 20f))
                gr.setAdditive(true)
                gr.glow(cam.sx, cam.sy, 0.85f * sc, Col.withA(0xFF4AA8FF.toInt(), a))
                gr.setAdditive(false)
            }
            val ph = (t * 0.7f + b.z * 0.37f + b.x * 0.21f) % 3f
            if (ph < 0.35f && cam.project(b.x - 0.3f + 0.6f * fract(b.z * 0.31f), b.y + 1.02f, b.z + 0.3f)) {
                val sz = cam.scaleAt(cam.depth) * 0.22f * sin(ph / 0.35f * Math.PI.toFloat())
                star4(gr, cam.sx, cam.sy, sz, 0xFFFFFFFF.toInt())
            }
        }
    }

    private fun toolImg(k: Int) = when (k) { TK.MAGNET -> art.magnet; TK.SHIELD -> art.shield; TK.SPEED -> art.lightning; else -> art.blockTool }

    /** Gold coil spring standing on a spring pad (thick hollow rings, as in the design); squashes when used. */
    private fun drawSpring(gr: Gfx, b: Block, top: Float) {
        val sq = if (b.squash > 0f) sin(b.squash * Math.PI.toFloat()) else 0f
        val coilH = 0.42f * (1f - 0.55f * sq)
        val cx = b.x; val cz = b.z + b.sz * 0.5f
        if (!cam.project(cx, top + coilH * 0.5f, cz)) return
        val sc = cam.scaleAt(cam.depth)
        if (sc * 0.3f < 2f) return
        gr.setAdditive(true)
        gr.glow(cam.sx, cam.sy, 0.55f * sc, Col.withA(0xFFFFB030.toInt(), 0.22f + 0.1f * pulse(g.t, 4f)))
        gr.setAdditive(false)
        val rings = 4
        for (k in 0 until rings) {
            val yy = top + 0.06f + coilH * (k + 0.5f) / rings
            val rr = if (k == 0 || k == rings - 1) 0.26f else 0.3f
            var m = 0
            for (i in 0 until 18) {
                val a = i / 18f * TAU
                if (!cam.project(cx + cos(a) * rr, yy, cz + sin(a) * rr)) return
                poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
            }
            // thick tube: dark rim, gold body, bright highlight on the near side
            gr.strokePoly(poly, m, 0.13f * sc, 0xFF7A4608.toInt())
            gr.strokePoly(poly, m, 0.095f * sc, 0xFFE8A824.toInt())
            var h = 0
            for (i in 2..7) {
                val a = i / 18f * TAU
                if (!cam.project(cx + cos(a) * rr, yy + 0.015f, cz + sin(a) * rr)) return
                poly[h * 2] = cam.sx; poly[h * 2 + 1] = cam.sy; h++
            }
            gr.strokePoly(poly, h, 0.035f * sc, 0xFFFFF0A8.toInt(), false)
        }
    }

    /** Floating power-up bubble with a tool icon inside (as in the Screen 4 design). */
    private fun drawBubble(gr: Gfx, bb: Bubble) {
        val t = g.t
        val bob = sin(t * 2.2f + bb.z) * 0.12f
        if (!cam.project(bb.x, bb.y + bob, bb.z)) return
        val sc = cam.scaleAt(cam.depth)
        val popK = if (bb.taken) 1f + (1f - bb.pop) * 0.8f else 1f
        val a = if (bb.taken) bb.pop else 1f
        val r = 0.46f * sc * popK
        val sx = cam.sx; val sy = cam.sy
        gr.setAdditive(true)
        gr.glow(sx, sy, r * 1.6f, Col.withA(0xFF7FC8FF.toInt(), 0.35f * a))
        gr.setAdditive(false)
        gr.fillCircle(sx, sy, r, Col.withA(0xFF3A78D8.toInt(), 0.35f * a))
        if (!bb.taken) {
            val img = toolImg(bb.kind)
            val ih = r * 1.25f; val iw = ih * img.w / img.h
            gr.image(img, sx - iw * 0.5f, sy - ih * 0.5f, iw, ih, a)
        }
        gr.strokeCircle(sx, sy, r, max(1.5f, r * 0.08f), Col.withA(0xFFD8F0FF.toInt(), 0.85f * a))
        gr.arc(sx, sy, r * 0.8f, 200f, 60f, max(1.5f, r * 0.1f), Col.withA(Col.WHITE, 0.8f * a))
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
                drawShadow(gr, p.x, p.z, gy, clamp01(1f - hgt / 6f) * p.portalScale, 0.36f * (1f + hgt * 0.05f), 0.26f * (1f + hgt * 0.05f))
            }
        }
        // invulnerability after a hit: flicker with a pale flash (never see-through, so the overlapping
        // rig parts at the hips and shoulders can't show through each other)
        val blink = p.invuln > 0f && p.state == PS.NORMAL && ((t * 14f).toInt() and 1) == 1
        // sprite metrics: the boy is ~1.95 world units tall at the camera's framing distance
        val worldH = g.spec.boyH
        val lp = p.loop
        if (lp != null && p.state == PS.LOOP) loopFooting(gr, p, lp)
        if (!cam.project(p.x, p.y, p.z)) return
        val fx0 = cam.sx; val fy0 = cam.sy
        val scale = cam.scaleAt(cam.depth)
        val hPx = worldH * scale * p.portalScale
        if (hPx < 2f) return
        val pose = g.rig.computePose(p)
        val speed = len2(p.vx, p.vz)
        // speed afterimages
        if (g.speedOn && speed > 1f && p.state == PS.NORMAL) {
            for (k in 2 downTo 1) {
                if (!cam.project(p.x - p.vx * 0.04f * k, p.y, p.z - p.vz * 0.04f * k)) continue
                g.rig.draw(gr, art.rig, pose, cam.sx, cam.sy, worldH * cam.scaleAt(cam.depth), 0.22f / k, 0xFF60D8FF.toInt(), 0.65f)
            }
        }
        val hurt = p.hurtFlash
        if (hurt > 0f) g.rig.draw(gr, art.rig, pose, fx0, fy0, hPx, 1f, 0xFFFF3030.toInt(), hurt * 0.55f)
        else g.rig.draw(gr, art.rig, pose, fx0, fy0, hPx, 1f, if (blink) 0xFFFFFFFF.toInt() else 0, if (blink) 0.42f else 0f)
        var midX = fx0; var midY = fy0 - hPx * 0.45f
        if (p.state == PS.LOOP) { val r = p.loopRot * Math.PI.toFloat() / 180f; midX = fx0 + sin(r) * hPx * 0.45f; midY = fy0 - cos(r) * hPx * 0.45f }
        if (g.shieldOn) {
            val tl = g.tools[TK.SHIELD]
            val grow = easeOutBack(clamp01((tl.duration - tl.active) / 0.3f))
            val r = hPx * 0.62f * (1f + 0.03f * sin(t * 6f)) * grow
            val a = if (tl.active < 2f && ((t * 8f).toInt() and 1) == 0) 0.4f else 1f
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
        // dazed stars after taking a hit
        if (p.hurtT < 0.9f && p.state == PS.NORMAL) {
            val a = 1f - p.hurtT / 0.9f
            for (k in 0..2) {
                val an = t * 7f + k * TAU / 3f
                star4(gr, midX + cos(an) * hPx * 0.22f, fy0 - hPx * 0.98f + sin(an) * hPx * 0.05f, hPx * 0.05f * a, Col.withA(0xFFFFF0A0.toInt(), a))
            }
        }
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
        val close = smooth((cam.depth - 3f) / 5f)
        // a beacon passing right by the lens fades away instead of filling the screen
        val near = smooth((cam.depth - 2.2f) / 2.5f)
        if (near <= 0.01f) return
        if (on) {
            // light beam
            if (cam.project(cp.x, cp.y, cp.z)) {
                val by = cam.sy
                val bw = 0.55f * s
                gr.setAdditive(true)
                gr.fillRectGradient(sx - bw, sy - 3.5f * s, sx + bw, by, 0x00FFE070, Col.withA(0xFFFFE070.toInt(), 0.4f * close))
                gr.setAdditive(false)
            }
        }
        val r = 0.34f * s
        val col = Col.mulA(if (on) 0xFFFFD84A.toInt() else 0xFF8FB4F0.toInt(), near)
        val hi = Col.mulA(if (on) 0xFFFFF6C0.toInt() else 0xFFE0ECFF.toInt(), near)
        gr.setAdditive(true)
        gr.glow(sx, sy, r * (if (on) 3.2f else 2f), Col.withA(col, (if (on) 0.7f else 0.35f) * (0.3f + 0.7f * close)))
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

    // ------------------------------------------------------------------ the Ancient Gate
    /** Gate billboard size in world units and where its threshold sits in the picture (0 top, 1 bottom). */
    private val gateW get() = if (g.spec.volcano) 16f else 13.4f
    private val gateThreshold get() = if (g.spec.volcano) 0.667f else 0.647f
    private val archU get() = if (g.spec.volcano) 0.523f else 0.516f
    private val archV get() = if (g.spec.volcano) 0.545f else 0.4f
    private val archR get() = if (g.spec.volcano) 0.088f else 0.085f
    private val gq = FloatArray(8)

    /**
     * The gate from the Screen 4 design is the destination. Far away it is a landmark in the sky,
     * exactly where the design shows it, growing as the player climbs; over the last stretch it
     * slides onto its real place at the top of the grand staircase at the end of the path.
     */
    private fun drawGate(gr: Gfx) {
        val po = g.world.portals.firstOrNull() ?: return
        if (!g.spec.bigGate) { drawBlockPortal(gr, po); return }
        val fade = clamp01(1f - (abs(cam.yaw) - 0.5f) / 0.5f)
        if (fade <= 0.01f) return
        val img = art.gate
        val aspect = img.h / img.w.toFloat()
        val h = g.hud
        // landmark: under the top bar, moving with the sky's parallax, growing with progress
        val bs = h.bgS
        val px = clamp(-g.camX * 7f, -60f, 60f) * bs
        val climb = clamp((cam.ey - 3.17f) * 7f, 0f, 380f) * bs
        val k = 1f + 0.32f * smooth(g.gateProgress())
        var lw = gr.width * 0.5575f * k
        var lcx = gr.width * 0.5f + px
        var ltop = h.ayT(90f) + climb
        if (g.spec.volcano) {
            // exactly where the fortress stands in the Level 5 design, riding on the plate
            lw = 430f * jbS * k; lcx = jbLeft + (art.bgArtX + 470f + 215f) * jbS; ltop = jbTop + (art.bgArtY + 80f) * jbS + (1f - k) * 0f
        }
        // the real gate at the end of the path
        val gh = gateW * aspect
        val top = po.y + gh * gateThreshold
        val dz = po.z - cam.ez
        var w = if (dz < 54f) smooth((54f - dz) / 24f) else 0f
        var ww = 0f; var wcx = 0f; var wtop = 0f
        if (w > 0f) {
            if (cam.project(po.x - gateW * 0.5f, top, po.z)) {
                val x0 = cam.sx; val y0 = cam.sy
                if (cam.project(po.x + gateW * 0.5f, top, po.z)) { ww = cam.sx - x0; wcx = (cam.sx + x0) * 0.5f; wtop = (cam.sy + y0) * 0.5f } else w = 0f
            } else w = 0f
        }
        val gw = lerp(lw, ww, w)
        val cx = lerp(lcx, wcx, w)
        val ty = lerp(ltop, wtop, w)
        val gH = gw * aspect
        val t = g.t
        val open = g.target >= g.spec.targetNeed
        val near = clamp01(1f - dz / 60f)
        val ax = cx + (archU - 0.5f) * gw; val ay = ty + archV * gH
        // warm light behind the gate, stronger as you get close and when the gate is open
        gr.setAdditive(true)
        gr.glow(ax, ay, gw * (0.55f + 0.1f * near), Col.withA(0xFFFF9A40.toInt(), fade * (0.18f + 0.18f * near + 0.1f * pulse(t, 2f))))
        gr.setAdditive(false)
        gr.image(img, cx - gw * 0.5f, ty, gw, gH, fade)
        // the portal inside the arch: swirls, brightens as you approach, blazes when activated
        val charge = po.charge
        val r = archR * gw * (1f + 0.05f * sin(t * 3f))
        val spin = t * (1.2f + 0.8f * near + 5f * charge)
        val intensity = fade * (0.35f + 0.25f * near + (if (open) 0.15f else 0f) + 0.45f * charge)
        gr.setAdditive(true)
        for (layer in 0..1) {
            val rr = r * (if (layer == 0) 1.25f else 0.85f)
            val a0 = if (layer == 0) spin else -spin * 1.4f
            for (i in 0..3) {
                val a = a0 + i * TAU / 4f + TAU / 8f
                gq[i * 2] = ax + cos(a) * rr * 1.414f; gq[i * 2 + 1] = ay + sin(a) * rr * 1.414f * 1.55f
            }
            gr.imageQuad(art.swirl, 0f, 0f, art.swirl.w.toFloat(), art.swirl.h.toFloat(), gq, intensity * (if (layer == 0) 0.9f else 0.6f))
        }
        gr.glow(ax, ay + r * 0.6f, r * (2.2f + 1.5f * charge), Col.withA(0xFFFFD27A.toInt(), intensity * 0.6f))
        gr.setAdditive(false)
        // sparkles drifting out of the portal, more of them the closer you get
        val sparks = 6 + (12 * near).toInt()
        for (i in 0 until sparks) {
            val ph = fract(t * 0.35f + i / sparks.toFloat())
            val sx = ax + sin(i * 2.3f + t) * r * (0.6f + ph)
            val sy = ay + r * 1.2f - ph * r * 3.2f
            star4(gr, sx, sy, gw * 0.012f * (1f - ph) * (1f + near), Col.withA(0xFFFFF0C0.toInt(), fade * (1f - ph)))
        }
        // sealed: a magic barrier while the blue blocks are still missing and the player is close
        if (!open && dz < 22f && g.state == GS.PLAY) {
            val a = fade * clamp01((22f - dz) / 6f) * (0.35f + 0.15f * pulse(t, 4f))
            gr.setAdditive(true)
            gr.glow(ax, ay, r * 2.4f, Col.withA(0xFF60A8FF.toInt(), a))
            gr.setAdditive(false)
            gr.strokeCircle(ax, ay, r * 1.5f, gw * 0.008f, Col.withA(0xFFB8E0FF.toInt(), a * 1.6f))
        }
    }

    /**
     * The early levels' portal: a swirl standing inside the gold block arch at the end of the path. It glows
     * brighter as you approach, is held shut by a blue barrier until the objective is done, and blazes on entry.
     */
    private fun drawBlockPortal(gr: Gfx, po: Portal) {
        val t = g.t
        val cx = po.x; val cy = po.y + 1.75f; val z = po.z - 0.05f
        if (!cam.project(cx, cy, z)) return
        val depth = cam.depth
        // too far to draw yet: a pillar of light marks where the portal is, from the first second of the level
        val far = smooth((depth - (maxDepth - 16f)) / 12f)
        if (far > 0.01f && g.state == GS.PLAY) drawPortalMarker(gr, cam.sx, cam.sy, far)
        if (depth > maxDepth) return
        val sc = cam.scaleAt(depth)
        val sx = cam.sx; val sy = cam.sy
        val open = g.target >= g.spec.targetNeed
        val near = clamp01(1f - (po.z - g.player.z) / 30f)
        val charge = po.charge
        val a = 1f - smooth((depth - (maxDepth - 10f)) / 10f)
        gr.setAdditive(true)
        gr.glow(sx, sy, 2.6f * sc, Col.withA(0xFFB060FF.toInt(), a * (0.25f + 0.2f * near + 0.4f * charge)))
        val spin = t * (1.4f + 5f * charge)
        for (layer in 0..1) {
            val rr = (if (layer == 0) 1.55f else 1.1f) * (1f + 0.04f * sin(t * 3f))
            val a0 = if (layer == 0) spin else -spin * 1.4f
            for (i in 0..3) {
                val an = a0 + i * TAU / 4f + TAU / 8f
                if (!cam.project(cx + cos(an) * rr * 1.414f * 0.62f, cy + sin(an) * rr * 1.414f, z)) return
                gq[i * 2] = cam.sx; gq[i * 2 + 1] = cam.sy
            }
            gr.imageQuad(art.swirl, 0f, 0f, art.swirl.w.toFloat(), art.swirl.h.toFloat(), gq, a * (if (layer == 0) 0.9f else 0.65f) * (0.7f + 0.3f * near))
        }
        gr.glow(sx, sy + 0.3f * sc, (0.9f + 0.8f * charge) * sc, Col.withA(0xFFFFE0FF.toInt(), a * (0.35f + 0.5f * charge)))
        gr.setAdditive(false)
        // more magic spills out the closer you get
        val sparks = 6 + (10 * near).toInt()
        for (i in 0 until sparks) {
            val ph = fract(t * 0.4f + i / sparks.toFloat())
            star4(gr, sx + sin(i * 2.3f + t) * sc * (0.4f + ph), sy + 0.9f * sc - ph * 2.2f * sc, 0.1f * sc * (1f - ph), Col.withA(0xFFFFF0FF.toInt(), a * (1f - ph)))
        }
        if (!open && g.state == GS.PLAY) {
            val k = a * clamp01((24f - (po.z - g.player.z)) / 6f) * (0.35f + 0.15f * pulse(t, 4f))
            if (k > 0.01f) {
                gr.setAdditive(true); gr.glow(sx, sy, 1.8f * sc, Col.withA(0xFF60A8FF.toInt(), k)); gr.setAdditive(false)
                gr.strokeCircle(sx, sy, 1.2f * sc, max(2f, 0.06f * sc), Col.withA(0xFFB8E0FF.toInt(), k * 1.6f))
            }
        }
    }

    /** A pillar of light rising from a portal still too far away to draw, so the player always knows where to go. */
    private fun drawPortalMarker(gr: Gfx, sx: Float, sy: Float, a: Float) {
        val s = g.hud.s; val t = g.t
        val bw = 30f * s * (1f + 0.08f * sin(t * 2.5f))
        gr.setAdditive(true)
        gr.fillRectGradient(sx - bw, 0f, sx + bw, sy, 0x00B070FF, Col.withA(0xFFB070FF.toInt(), 0.30f * a))
        gr.fillRectGradient(sx - bw * 0.3f, 0f, sx + bw * 0.3f, sy, 0x00FFF0FF, Col.withA(0xFFFFF0FF.toInt(), 0.35f * a))
        gr.glow(sx, sy, 70f * s * (1f + 0.12f * sin(t * 3f)), Col.withA(0xFFD8A0FF.toInt(), 0.6f * a))
        gr.setAdditive(false)
        for (i in 0 until 5) {
            val ph = fract(t * 0.5f + i / 5f)
            star4(gr, sx + sin(i * 2.1f + t * 1.3f) * bw * 0.8f, sy - ph * 160f * s, 9f * s * (1f - ph), Col.withA(0xFFFFF0FF.toInt(), a * (1f - ph)))
        }
    }

    // ------------------------------------------------------------------ guard (block golem)
    private fun drawGuard(gr: Gfx) {
        if (g.spec.volcano) { drawGuardSprite(gr); return }
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
        // between the camera and the boy it turns see-through: it looms, but never hides the path
        val p = g.player
        val between = abs(cam.yaw) < 1f && gz < p.z && gz + 1.2f > cam.ez
        val ga = if (between) lerp(1f, 0.35f, clamp01((gz + 1.2f - cam.ez) / 1.5f)) else 1f
        fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, col: Int, v: Int) {
            drawBox(gr, gx + x0, baseY + y0 + bob, gz + z0, gx + x1, baseY + y1 + bob, gz + z1, col, v, ga, 0f, null)
        }
        val ls = sw * 0.45f
        val reach = gd.reach
        val grab = gd.grab
        // legs
        box(-1.05f, 0f, -0.45f + ls, -0.2f, 1.7f, 0.45f + ls, b, 0)
        box(0.2f, 0f, -0.45f - ls, 1.05f, 1.7f, 0.45f - ls, b, 1)
        // torso
        box(-1.35f, 1.6f, -0.7f, 1.35f, 3.6f, 0.7f, c, 0)
        box(-1.1f, 1.9f, if (lookBack) 0.62f else -0.78f, 1.1f, 3.3f, if (lookBack) 0.78f else -0.62f, b, 2)
        // arms: reach forward when close, close in around the boy when grabbing
        val az = 0.2f + reach * 1.2f + grab * 0.4f
        val ax = lerp(1.75f, 0.95f, grab)
        val ay = grab * 0.9f
        box(-ax - 0.4f, 1.8f - reach * 0.2f + ay, -0.45f + az * 0.3f - ls * 0.6f, -ax + 0.4f, 3.5f + ay, 0.45f + az + ls * 0.6f, c, 1)
        box(ax - 0.4f, 1.8f - reach * 0.2f + ay, -0.45f + az * 0.3f + ls * 0.6f, ax + 0.4f, 3.5f + ay, 0.45f + az - ls * 0.6f, c, 2)
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
            val angry = if (g.ev.chase == Chase.REVEAL || g.ev.chase == Chase.CAUGHT) 0.3f else 0f
            gr.glow(cam.sx, cam.sy, 1.4f * s, Col.withA(0xFFFF6A10.toInt(), 0.35f + angry + 0.15f * pulse(t, 8f)))
            gr.setAdditive(false)
        }
        if (fall > 0f) return
    }

    private fun drawRock(gr: Gfx, r: Rock) {
        // warning marker where it will land
        if (!r.landed) {
            var m = 0
            val u = clamp01(1f - (r.y - r.ground) / 11f)
            for (i in 0 until 14) {
                val a = i / 14f * TAU
                if (!cam.project(r.tx + cos(a) * 0.5f, r.ground + 0.02f, r.tz + sin(a) * 0.5f)) { m = 0; break }
                poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
            }
            if (m > 0) {
                gr.fillPoly(poly, m, Col.withA(0xFFFF3020.toInt(), 0.25f + 0.35f * u))
                gr.strokePoly(poly, m, 3f * g.hud.s, Col.withA(0xFFFFD0C0.toInt(), 0.5f + 0.5f * u))
            }
        }
        val volc = g.spec.volcano
        // a boulder flying past the lens fades instead of filling the screen
        val a = smooth((cam.depthOf(r.x, r.y + 0.35f, r.z) - 1.5f) / 2.5f)
        if (a <= 0.01f) return
        drawBox(gr, r.x - 0.35f, r.y, r.z - 0.35f, r.x + 0.35f, r.y + 0.7f, r.z + 0.35f, if (volc) BC.BASALT else BC.BRICK, 1, a, 0f, null,
            if (volc) OV.LAVA else 0)
    }

    // ------------------------------------------------------------------ Level 5: the volcanic sky fortress
    /** Level 5 plate placement (scale, left, top), updated each frame by [drawPlateBackground]. */
    private var jbS = 1f; private var jbLeft = 0f; private var jbTop = 0f

    /**
     * The Level 5 plate (the design's sunset sky, floating islands and cliffs): it fills the screen with the design's
     * framing at the start (the fortress where the design shows it), then drifts with a gentle parallax and climb.
     */
    private fun drawPlateBackground(gr: Gfx) {
        val w = gr.width.toFloat(); val h = gr.height.toFloat()
        val s = max(w / (art.artW - 70f), h / (art.artH - 40f))
        val px = clamp(-g.camX * 4f, -20f, 20f) * s
        val climb = clamp((cam.ey - 3.3f) * 1.6f, 0f, 60f) * s
        jbS = s
        // framed right of the design's centre so the fortress and its portal stand clear of the HUD's target panel
        jbLeft = clamp(w * 0.5f - (art.bgArtX + 580f) * s, w - art.artW * s + 22f * s, -22f * s) + px
        jbTop = -art.bgArtY * s - 10f * s + climb
        gr.fillRect(0f, 0f, w, h, 0xFFE0805A.toInt())
        val roll = cam.roll
        val rolled = abs(roll) > 0.0005f
        if (rolled) {
            gr.save(); gr.translate(cam.cx, cam.cy); gr.rotate(roll * 57.29578f)
            val z = 1f + abs(roll) * 2.6f
            gr.scale(z, z); gr.translate(-cam.cx, -cam.cy)
        }
        gr.image(art.bg, jbLeft, jbTop, art.bg.w * s, art.bg.h * s)
        if (jbTop > 0f) gr.fillRectGradient(-40f, -40f, w + 40f, jbTop + 2f, 0xFF5A4E9A.toInt(), 0xFF6A5AA8.toInt())
        if (rolled) gr.restore()
        // warm haze of the lava below, rising from the bottom of the screen
        gr.fillRectGradient(0f, h * 0.45f, w, h, 0x00FF7A30, 0x40FF7A30)
    }

    /** Fades pictures standing in the world that come right up to the lens. */
    private fun nearFade(depth: Float) = smooth((depth - 1.2f) / 2.2f) * (1f - smooth((depth - (maxDepth - 10f)) / 10f))

    /** Scenery: torches, banners on the pillars, chain railings along the bridges. */
    private fun drawDeco(gr: Gfx, d: Deco) {
        when (d.kind) {
            DK.TORCH -> drawTorch(gr, d)
            DK.BANNER -> drawBanner(gr, d)
            DK.RAIL -> drawRail(gr, d)
        }
    }

    /** The design's red banner with the gold crown, hanging on a pillar's front face and stirring in the hot wind. */
    private fun drawBanner(gr: Gfx, d: Deco) {
        val img = art.banner ?: return
        val hgt = d.w * img.h / img.w
        val t = g.t + d.z * 0.7f
        val sway = sin(t * 2.1f) * 0.06f * d.w
        if (!cam.project(d.x - d.w * 0.5f, d.y, d.z)) return; q[0] = cam.sx; q[1] = cam.sy
        val a = nearFade(cam.depth)
        if (a <= 0.01f) return
        if (!cam.project(d.x + d.w * 0.5f, d.y, d.z)) return; q[2] = cam.sx; q[3] = cam.sy
        if (!cam.project(d.x + d.w * 0.5f + sway, d.y - hgt, d.z - 0.02f)) return; q[4] = cam.sx; q[5] = cam.sy
        if (!cam.project(d.x - d.w * 0.5f + sway, d.y - hgt, d.z - 0.02f)) return; q[6] = cam.sx; q[7] = cam.sy
        gr.imageQuad(img, 0f, 0f, img.w.toFloat(), img.h.toFloat(), q, a, 1f, 0xFFFFB070.toInt(), hazeFor(cam.depth) * 0.5f)
    }

    /** A chain railing along a bridge: a stone post at each end and a sagging iron chain between them. */
    private fun drawRail(gr: Gfx, d: Deco) {
        val x = d.x; val y = d.y; val z0 = d.z; val z1 = d.z + d.w
        for (zz in floatArrayOf(z0, z1)) drawBox(gr, x - 0.13f, y, zz - 0.13f, x + 0.13f, y + 0.95f, zz + 0.13f, BC.FORT, 2, 1f, 0f, null)
        var px = 0f; var py = 0f; var ok = false
        val n = 10
        for (i in 0..n) {
            val u = i / n.toFloat()
            val yy = y + 0.8f - 0.32f * 4f * u * (1f - u)
            if (!cam.project(x, yy, lerp(z0, z1, u))) { ok = false; continue }
            val sc = cam.scaleAt(cam.depth)
            val a = nearFade(cam.depth)
            if (ok && a > 0.01f) {
                gr.line(px, py, cam.sx, cam.sy, max(1.5f, 0.07f * sc), Col.withA(0xFF1E1A22.toInt(), a))
                gr.line(px, py - 0.02f * sc, cam.sx, cam.sy - 0.02f * sc, max(1f, 0.025f * sc), Col.withA(0xFF8A8494.toInt(), a))
            }
            px = cam.sx; py = cam.sy; ok = true
        }
    }

    /** A fire bowl on a fortress-stone post (as along the design's path), with a living flame. */
    private fun drawTorch(gr: Gfx, d: Deco) {
        val x = d.x; val top = d.y; val z = d.z
        if (!drawBox(gr, x - 0.3f, top - 2.2f, z - 0.3f, x + 0.3f, top, z + 0.3f, BC.FORT, 1, 1f, 0f, null)) return
        drawBox(gr, x - 0.42f, top, z - 0.42f, x + 0.42f, top + 0.26f, z + 0.42f, BC.GOLD, 0, 1f, 0f, null)
        if (!cam.project(x, top + 0.45f, z)) return
        val sc = cam.scaleAt(cam.depth)
        if (sc < 3f) return
        val a = nearFade(cam.depth)
        val t = g.t + d.z * 1.7f
        val fl = 0.85f + 0.15f * sin(t * 13f) * sin(t * 7.3f + 1f)
        val fx0 = cam.sx; val fy0 = cam.sy
        gr.setAdditive(true)
        gr.glow(fx0, fy0 - 0.1f * sc, 1.3f * sc * fl, Col.withA(0xFFFF7A1A.toInt(), 0.35f * a))
        gr.glow(fx0, fy0 - 0.12f * sc, 0.5f * sc * fl, Col.withA(0xFFFFB030.toInt(), 0.8f * a))
        gr.glow(fx0 + sin(t * 9f) * 0.04f * sc, fy0 - 0.3f * sc * fl, 0.28f * sc, Col.withA(0xFFFFE890.toInt(), 0.85f * a))
        gr.setAdditive(false)
        // tongue of flame
        val hgt = 0.62f * sc * fl
        poly[0] = fx0 - 0.17f * sc; poly[1] = fy0
        poly[2] = fx0 + sin(t * 11f) * 0.06f * sc; poly[3] = fy0 - hgt
        poly[4] = fx0 + 0.17f * sc; poly[5] = fy0
        gr.fillPolyGradient(poly, 3, fx0, fy0 - hgt, fx0, fy0, Col.withA(0xFFFFF4B0.toInt(), a), Col.withA(0xFFFF8A1A.toInt(), a))
        if (g.fx.rng.f() < 0.08f && a > 0.5f) {
            val q2 = g.fx.spawn()
            q2.x = x + g.fx.rng.f(-0.1f, 0.1f); q2.y = top + 0.5f; q2.z = z
            q2.vx = g.fx.rng.f(-0.2f, 0.2f); q2.vy = g.fx.rng.f(1f, 2f); q2.vz = 0f
            q2.life = g.fx.rng.f(0.5f, 0.9f); q2.maxLife = q2.life; q2.size = 0.05f; q2.color = 0xFFFFB040.toInt(); q2.kind = PK.EMBER
        }
    }

    /**
     * A fortress-stone pillar rising out of the lava sea: brick courses all the way down, darker and glowing
     * with the lava's heat toward the bottom.
     */
    private fun drawPillar(gr: Gfx, pl: Pillar) {
        val tex = art.pillarSide ?: return
        val x0 = pl.x - pl.w * 0.5f; val x1 = pl.x + pl.w * 0.5f; val z0 = pl.z - pl.d * 0.5f; val z1 = pl.z + pl.d * 0.5f
        val top = pl.top
        val bottom = max(g.seaY - 0.5f, top - 16f)
        if (top <= bottom) return
        if (!projCorner(0, x0, bottom, z0) || !projCorner(1, x1, bottom, z0) || !projCorner(2, x1, bottom, z1) || !projCorner(3, x0, bottom, z1) ||
            !projCorner(4, x0, top, z0) || !projCorner(5, x1, top, z0) || !projCorner(6, x1, top, z1) || !projCorner(7, x0, top, z1)) return
        val depth = (cd[4] + cd[6]) * 0.5f
        val a = nearFade(depth)
        if (a <= 0.01f) return
        val ts = art.texSize.toFloat()
        val v1 = min(tex.h.toFloat(), (top - bottom) * ts)
        val ex = cam.ex; val ey = cam.ey; val ez = cam.ez
        val haze = hazeFor(depth) * 0.8f
        if (ez > z1) { quad(6, 7, 3, 2); gr.imageQuad(tex, 0f, 0f, ts, v1, q, a, 0.6f, 0xFFFFA070.toInt(), haze) }
        if (ex < x0) { quad(7, 4, 0, 3); gr.imageQuad(tex, 0f, 0f, ts * pl.d, v1, q, a, 0.62f, 0xFFFFA070.toInt(), haze) }
        if (ex > x1) { quad(5, 6, 2, 1); gr.imageQuad(tex, 0f, 0f, ts * pl.d, v1, q, a, 0.62f, 0xFFFFA070.toInt(), haze) }
        if (ez < z0) { quad(4, 5, 1, 0); gr.imageQuad(tex, 0f, 0f, ts * pl.w, v1, q, a, 0.8f, 0xFFFFA070.toInt(), haze) }
        if (ey > top) { quad(7, 6, 5, 4); gr.imageQuad(art.top[BC.FORT][1], 0f, 0f, ts, ts, q, a, 0.95f, 0xFFFFA070.toInt(), haze) }
        // the lava's glow on the foot of the pillar
        if (cam.project(pl.x, bottom + 1.2f, z0) ) {
            val sc = cam.scaleAt(cam.depth)
            gr.setAdditive(true)
            gr.glow(cam.sx, cam.sy, 1.6f * sc * pl.w, Col.withA(0xFFFF6A10.toInt(), 0.35f * a))
            gr.setAdditive(false)
        }
    }

    // ---- the great loop: a ring of coloured blocks, 3 lanes wide
    /** One block of the loop's ring: segment [i] of [LOOP_N] around, lane [j] across. Its centre is the sort point. */
    class LoopSeg(val lp: Loop, val i: Int, val j: Int) { var cx = 0f; var cy = 0f; var cz = 0f }
    private val loopSegCache = HashMap<Loop, Array<LoopSeg>>()
    private val LOOP_N = 36
    private val loopDepth = 0.85f
    private fun loopSegs(lp: Loop): Array<LoopSeg> = loopSegCache.getOrPut(lp) {
        Array(LOOP_N * 3) { k ->
            val sg = LoopSeg(lp, k / 3, k % 3)
            val u = (sg.i + 0.5f) / LOOP_N
            val a = lp.angle(u); val rr = lp.r + loopDepth * 0.5f
            sg.cx = lp.cx(u) - 1f + sg.j; sg.cy = lp.y0 + lp.r - rr * cos(a); sg.cz = lp.z0 + rr * sin(a)
            sg
        }
    }
    private val loopColors = intArrayOf(BC.RED, BC.YELLOW, BC.GREEN, BC.BLUE, BC.PURPLE, BC.ORANGE)
    private val lx = FloatArray(8); private val ly = FloatArray(8); private val lz = FloatArray(8)
    /** Screen box of the boy this frame (the loop's blocks between him and the camera turn see-through). */
    private val pbox = FloatArray(4)
    private var pdepth = 0f

    private fun playerBox() {
        val p = g.player
        pbox[0] = 1e9f; pbox[1] = 1e9f; pbox[2] = -1e9f; pbox[3] = -1e9f; pdepth = 1e9f
        val lp = p.loop
        val my = if (lp != null && p.state == PS.LOOP) p.y + 0.8f * lp.upY(p.loopU) else p.y + 0.8f
        val mz = if (lp != null && p.state == PS.LOOP) p.z + 0.8f * lp.upZ(p.loopU) else p.z
        if (!cam.project(p.x, my, mz)) return
        val sc = cam.scaleAt(cam.depth); pdepth = cam.depth
        pbox[0] = cam.sx - 1.1f * sc; pbox[2] = cam.sx + 1.1f * sc; pbox[1] = cam.sy - 1.3f * sc; pbox[3] = cam.sy + 1.3f * sc
    }

    private fun drawLoopSeg(gr: Gfx, sg: LoopSeg) {
        val lp = sg.lp
        val u0 = sg.i / LOOP_N.toFloat(); val u1 = (sg.i + 1) / LOOP_N.toFloat()
        val l0 = -1.5f + sg.j; val l1 = l0 + 1f
        // corners: 0..3 on the running surface (u0l0, u0l1, u1l1, u1l0), 4..7 the same on the outside
        for (k in 0..7) {
            val u = if (k % 4 == 0 || k % 4 == 1) u0 else u1
            val l = if (k % 4 == 0 || k % 4 == 3) l0 else l1
            val rr = if (k < 4) lp.r else lp.r + loopDepth
            val a = lp.angle(u)
            lx[k] = lp.cx(u) + l; ly[k] = lp.y0 + lp.r - rr * cos(a); lz[k] = lp.z0 + rr * sin(a)
            if (!projCorner(k, lx[k], ly[k], lz[k])) return
        }
        var minX = cx[0]; var maxX = cx[0]; var minY = cy[0]; var maxY = cy[0]
        for (k in 1..7) { minX = min(minX, cx[k]); maxX = max(maxX, cx[k]); minY = min(minY, cy[k]); maxY = max(maxY, cy[k]) }
        if (maxX < -20f || minX > gfx!!.width + 20f || maxY < -20f || minY > gfx!!.height + 20f) return
        val depth = (cd[0] + cd[2]) * 0.5f
        // the ring's blocks close to the lens (as the camera swings back after the loop) fade away
        var a = nearFade(depth) * smooth((depth - 2.2f) / 3.5f)
        // just out of the loop the ring towers right beside the path: its nearby blocks turn see-through so the
        // way ahead stays clear
        val pl = g.player
        if (pl.state != PS.LOOP && pl.z > lp.z0 - 0.5f && pl.z < lp.z0 + lp.r + 4f) a *= lerp(0.3f, 1f, smooth((depth - 4f) / 7f))
        // blocks between the camera and the boy turn see-through, so he is never lost behind the ring
        if (depth < pdepth - 0.3f && maxX > pbox[0] && minX < pbox[2] && maxY > pbox[1] && minY < pbox[3]) a *= 0.35f
        if (a <= 0.01f) return
        val pad = lp.pads.any { it >= u0 && it < u1 }
        val col = if (pad) BC.BLUE else loopColors[(sg.i * 2 + sg.j) % loopColors.size]
        val ts = art.texSize.toFloat()
        val haze = hazeFor(depth)
        val um = (u0 + u1) * 0.5f; val am = lp.angle(um)
        // inward normal (toward the ring's centre) at this segment
        val ny = cos(am); val nz = -sin(am)
        val mx = (lx[0] + lx[2]) * 0.5f; val my = (ly[0] + ly[2]) * 0.5f; val mz = (lz[0] + lz[2]) * 0.5f
        val tox = cam.ex - mx; val toy = cam.ey - my; val toz = cam.ez - mz
        val inner = toy * ny + toz * nz > 0f
        if (a > 0.95f) {
            val m = hull()
            for (k in 0 until m) { poly[k * 2] = cx[hullIdx[k]]; poly[k * 2 + 1] = cy[hullIdx[k]] }
            gr.fillPoly(poly, m, Col.mix(BC.dark(col), skyHaze, haze))
        }
        // outer face
        if (!inner) { quad(4, 5, 6, 7); gr.imageQuad(art.side[col][sg.j], 0f, 0f, ts, ts, q, a, 0.72f, skyHaze, haze) }
        // the sides of the track (outer lanes only)
        if (sg.j == 0 && tox < 0f) { quad(4, 0, 3, 7); gr.imageQuad(art.side[col][1], 0f, 0f, ts, ts, q, a, 0.66f, skyHaze, haze) }
        if (sg.j == 2 && tox > 0f) { quad(1, 5, 6, 2); gr.imageQuad(art.side[col][2], 0f, 0f, ts, ts, q, a, 0.66f, skyHaze, haze) }
        // the running surface
        if (inner) {
            quad(0, 1, 2, 3); gr.imageQuad(art.top[col][sg.j], 0f, 0f, ts, ts, q, a, 1.04f, skyHaze, haze)
            if (pad) {
                // the speed pad's glowing chevrons, pointing on round the loop
                gr.setAdditive(true)
                gr.imageQuad(art.frameGlow, 0f, 0f, art.frameGlow.w.toFloat(), art.frameGlow.h.toFloat(), q, a * (0.7f + 0.3f * pulse(g.t, 5f)))
                gr.setAdditive(false)
                quad(3, 2, 1, 0)
                gr.imageQuad(art.chevrons, 0f, 0f, art.chevrons.w.toFloat(), art.chevrons.h.toFloat(), q, a, 1f, 0xFFFFFFFF.toInt(), 0.6f + 0.3f * pulse(g.t * 1.4f, 3f))
            }
            // spike plates on this block
            for (sp in lp.spikes) if (sp[0] >= u0 && sp[0] < u1 && sp[1] > l0 - 0.01f && sp[1] < l1 + 0.01f) loopSpikes(gr, lp, sp[0], sp[1], a)
        }
    }

    /** The boy's contact shadow on the loop's track (under his feet), fading while he hops. */
    private fun loopFooting(gr: Gfx, p: Player, lp: Loop) {
        val u = p.loopU
        val uy = lp.upY(u); val uz = lp.upZ(u)
        val fy = lp.sy(u, 0.03f); val fz = lp.sz(u, 0.03f)
        val tan0 = kotlin.math.sin(lp.angle(u)); val tan1 = kotlin.math.cos(lp.angle(u))
        var m = 0
        val k = clamp01(1f - p.loopHop / 1.2f)
        for (i in 0 until 12) {
            val an = i / 12f * TAU
            val ax = cos(an) * 0.34f; val at = sin(an) * 0.26f
            if (!cam.project(p.x + ax, fy + tan0 * at + 0.01f * uy, fz + tan1 * at + 0.01f * uz)) return
            poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
        }
        if (k > 0.02f) gr.fillPoly(poly, m, Col.withA(0xFF10081A.toInt(), 0.4f * k))
    }

    /** Iron spikes standing in from the loop's track (toward the centre). */
    private fun loopSpikes(gr: Gfx, lp: Loop, u: Float, lat: Float, a: Float) {
        val an = lp.angle(u)
        val du = 0.3f / lp.length
        for (k in 0..2) {
            val l = lat - 0.3f + k * 0.3f
            val bx = lp.cx(u) + l
            if (!cam.project(bx, lp.sy(u, 0.5f), lp.sz(u, 0.5f))) return
            val tipX = cam.sx; val tipY = cam.sy
            if (!cam.project(bx - 0.13f, lp.sy(u - du), lp.sz(u - du))) return
            poly[0] = cam.sx; poly[1] = cam.sy
            if (!cam.project(bx + 0.13f, lp.sy(u + du), lp.sz(u + du))) return
            poly[2] = cam.sx; poly[3] = cam.sy
            poly[4] = tipX; poly[5] = tipY
            gr.fillPolyGradient(poly, 3, tipX, tipY, poly[0], poly[1], Col.withA(0xFFF4F6FF.toInt(), a), Col.withA(0xFF5A6078.toInt(), a))
        }
        if (cam.project(lp.cx(u) + lat, lp.sy(u, 0.25f), lp.sz(u, 0.25f)) && an >= 0f) {
            gr.setAdditive(true); gr.glow(cam.sx, cam.sy, 0.5f * cam.scaleAt(cam.depth), Col.withA(0xFFFF3A2A.toInt(), 0.25f * a)); gr.setAdditive(false)
        }
    }

    // ---- the Runaway Relic
    private fun drawRelic(gr: Gfx) {
        val img = art.relic ?: return
        val rl = g.relic
        if (!cam.project(rl.x, rl.y, rl.z)) return
        val sc = cam.scaleAt(cam.depth)
        var a = rl.show * nearFade(cam.depth + 1f)
        if (a <= 0.01f) return
        val t = g.t
        val pop = if (rl.state == RS.APPEAR) easeOutBack(clamp01(rl.t / 0.35f)) else 1f
        val hgt = 1.45f * sc * pop * (if (rl.state == RS.ESCAPED) rl.show else 1f)
        val wid = hgt * img.w / img.h
        val bx = cam.sx; val by = cam.sy
        gr.setAdditive(true)
        gr.glow(bx, by - hgt * 0.5f, hgt * (0.95f + 0.1f * sin(t * 6f)), Col.withA(0xFFFFD050.toInt(), 0.55f * a))
        gr.glow(bx, by - hgt * 0.5f, hgt * 0.45f, Col.withA(0xFFFFFFFF.toInt(), 0.35f * a))
        gr.setAdditive(false)
        val tilt = if (rl.state == RS.RUN) -6f + 5f * sin(t * 9f) else 0f
        gr.save(); gr.translate(bx, by); gr.rotate(tilt)
        gr.image(img, -wid * 0.5f, -hgt, wid, hgt, a)
        gr.restore()
        // a sparkle now and then
        val ph = fract(t * 1.3f)
        star4(gr, bx + wid * 0.45f, by - hgt * 0.9f, hgt * 0.12f * sin(ph * Math.PI.toFloat()), Col.withA(Col.WHITE, a))
    }

    /** A spiked iron ball on its chain, hanging from a stone beam, swinging across the path. */
    private fun drawMace(gr: Gfx, l: SwingLog) {
        val img = art.mace ?: return
        val depth0 = cam.depthOf(l.px, l.py, l.pz)
        val a = nearFade(depth0)
        if (a <= 0.01f) return
        // the beam across the path at the pivot
        drawBox(gr, l.px - 3.1f, l.py, l.pz - 0.32f, l.px + 3.1f, l.py + 0.55f, l.pz + 0.32f, BC.FORT, 0, a, 0f, null)
        val bx = l.cx; val by = l.cy
        // the chain: links from the pivot to the ball
        val n = 9
        for (i in 0 until n) {
            val u = (i + 0.5f) / n
            if (!cam.project(lerp(l.px, bx, u), lerp(l.py, by, u), l.pz)) continue
            val sc = cam.scaleAt(cam.depth)
            val rr = 0.13f * sc
            if (i % 2 == 0) gr.strokeCircle(cam.sx, cam.sy, rr, max(1.5f, 0.06f * sc), Col.withA(0xFF2A2630.toInt(), a))
            else gr.line(cam.sx - rr * sin(l.angle), cam.sy - rr, cam.sx + rr * sin(l.angle), cam.sy + rr, max(1.5f, 0.07f * sc), Col.withA(0xFF3A3642.toInt(), a))
        }
        if (!cam.project(bx, by, l.pz)) return
        val sc = cam.scaleAt(cam.depth)
        val d = l.radius * 2.3f * sc
        gr.setAdditive(true); gr.glow(cam.sx, cam.sy, d * 0.7f, Col.withA(0xFFFF4A20.toInt(), 0.18f * a)); gr.setAdditive(false)
        gr.save(); gr.translate(cam.sx, cam.sy); gr.rotate(l.angle * 57.29578f)
        gr.image(img, -d * 0.5f, -d * 0.5f, d, d * img.h / img.w, a)
        gr.restore()
    }

    /**
     * The lava Guardian from the design: it climbs out of the lava beside the path and runs after the boy on the
     * left, reaching for him, glowing through its cracks. A camera-facing picture (the design shows its face).
     */
    private fun drawGuardSprite(gr: Gfx) {
        val img = art.guardian ?: return
        val gd = g.ev.guard
        val t = g.t
        val sw = sin(gd.step * Math.PI.toFloat())
        val bob = abs(sw) * 0.22f
        val x = gd.x; val z = gd.z
        var baseY = gd.y - 1.4f - (1f - gd.rise) * 5f + bob + gd.reach * 0.3f
        val worldW = 6.2f
        var a = 1f
        if (gd.falling) { a = clamp01(1f - gd.fallT / 2.5f); baseY -= gd.fallT * gd.fallT * 1.5f }
        val p = g.player
        // between the camera and the boy it turns see-through: it looms, but never hides the path
        val between = abs(cam.yaw) < 1f && z < p.z && z + 1.5f > cam.ez && abs(x - p.x) < 2.5f
        if (between) a *= lerp(1f, 0.4f, clamp01((z + 1.5f - cam.ez) / 1.8f))
        if (!cam.project(x, baseY, z)) return
        val sc = cam.scaleAt(cam.depth)
        a *= smooth((cam.depth - 0.8f) / 1.6f)
        if (a <= 0.01f) return
        val w = worldW * sc
        val h = w * img.h / img.w
        val bx = cam.sx; val by = cam.sy
        val angry = if (g.ev.chase == Chase.REVEAL || g.ev.chase == Chase.CAUGHT) 1f else 0.5f
        gr.setAdditive(true)
        gr.glow(bx, by - h * 0.5f, w * 0.55f, Col.withA(0xFFFF6A10.toInt(), a * (0.25f + 0.15f * angry + 0.08f * pulse(t, 7f))))
        gr.setAdditive(false)
        // it leans toward the boy as it reaches for him
        val lean = -4f + 6f * gd.reach + sin(gd.step * Math.PI.toFloat()) * 2f
        gr.save(); gr.translate(bx, by); gr.rotate(lean)
        gr.image(img, -w * 0.5f, -h, w, h, a)
        gr.restore()
        // eyes blaze when it is angry
        gr.setAdditive(true)
        val ey = by - h * 0.4f
        gr.glow(bx + w * 0.07f, ey, w * 0.05f, Col.withA(0xFFFFD040.toInt(), a * (0.35f + 0.4f * angry)))
        gr.glow(bx + w * 0.19f, ey + h * 0.02f, w * 0.045f, Col.withA(0xFFFFD040.toInt(), a * (0.35f + 0.4f * angry)))
        gr.setAdditive(false)
        // embers and smoke rising off it
        if (g.fx.rng.f() < 0.25f && a > 0.4f) {
            val q2 = g.fx.spawn()
            q2.x = x + g.fx.rng.f(-2.2f, 2.2f); q2.y = baseY + g.fx.rng.f(0.5f, 3.5f); q2.z = z
            q2.vx = g.fx.rng.f(-0.3f, 0.3f); q2.vy = g.fx.rng.f(1f, 2.5f); q2.vz = 0f
            q2.life = g.fx.rng.f(0.5f, 1f); q2.maxLife = q2.life; q2.size = g.fx.rng.f(0.05f, 0.1f); q2.color = 0xFFFFA040.toInt(); q2.kind = PK.EMBER
        }
    }

    // ------------------------------------------------------------------ lava
    /**
     * A lava surface at height [ly]: the rising lava (Level 4) or, with [sea], Level 5's lava sea far below the
     * course, which fades out into the heat haze in the distance so it meets the sky plate softly.
     */
    private fun drawLava(gr: Gfx, ly: Float, sea: Boolean) {
        val t = g.t
        val tile = 4f
        // tiles around the point the camera looks at (the camera may look sideways, e.g. round the loop)
        val fx0 = cam.ex + sin(cam.yaw) * 26f; val fz0 = cam.ez + cos(cam.yaw) * 26f
        val ox = floor(fx0 / tile) * tile
        val oz = floor(fz0 / tile) * tile
        val flow = fract(t * (if (sea) 0.05f else 0.08f)) * tile
        for (j in 8 downTo -8) {
            for (i in -8..8) {
                val x0 = ox + i * tile; val zz = oz + j * tile + flow
                if (!cam.project(x0, ly, zz)) continue; q[6] = cam.sx; q[7] = cam.sy
                if (!cam.project(x0 + tile, ly, zz)) continue; q[4] = cam.sx; q[5] = cam.sy
                if (!cam.project(x0 + tile, ly, zz + tile)) continue; q[2] = cam.sx; q[3] = cam.sy
                if (!cam.project(x0, ly, zz + tile)) continue; q[0] = cam.sx; q[1] = cam.sy
                val d = cam.depthOf(x0 + tile * 0.5f, ly, zz + tile * 0.5f)
                val a = if (sea) 1f - smooth((d - 34f) / 20f) else 1f
                if (a <= 0.01f) continue
                gr.imageQuad(art.lava, 0f, 0f, art.lava.w.toFloat(), art.lava.h.toFloat(), q, a, 1f + 0.1f * sin(t * 3f + i + j), 0xFFFF9A40.toInt(), hazeFor(d) * 0.6f)
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
                PK.CONFETTI -> {
                    val cs = cos(p.rot) * r * 1.3f; val sn = sin(p.rot) * r * 0.55f * abs(cos(p.rot * 1.7f))
                    poly[0] = cam.sx + cs; poly[1] = cam.sy + sn; poly[2] = cam.sx - sn; poly[3] = cam.sy + cs * 0.4f
                    poly[4] = cam.sx - cs; poly[5] = cam.sy - sn; poly[6] = cam.sx + sn; poly[7] = cam.sy - cs * 0.4f
                    gr.fillPoly(poly, 4, Col.withA(p.color, min(1f, lf * 2.5f)))
                }
                PK.STREAK -> {
                    val sx0 = cam.sx; val sy0 = cam.sy
                    if (cam.project(p.x - p.vx * 0.05f, p.y - p.vy * 0.05f, p.z - p.vz * 0.05f)) {
                        gr.setAdditive(true)
                        gr.line(sx0, sy0, cam.sx, cam.sy, max(1.5f, r * 0.8f), Col.withA(p.color, 0.8f * lf))
                        gr.setAdditive(false)
                    }
                }
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
        val ev = g.ev
        if (ev.windShow > 0.01f) {
            // magical wind: pale streaks sweeping across the way the gust blows (faint while it gathers)
            val dir = ev.windDir
            val speed = 0.35f + 0.9f * ev.gust
            for (i in 0 until 18) {
                val row = fract(i * 0.618f)
                val y = h * (0.14f + 0.72f * row) + sin(t * 1.7f + i) * 18f * s
                val ph = fract(t * speed + i * 0.37f)
                val len = (120f + 140f * fract(i * 0.29f)) * s * (0.5f + ev.gust)
                val x = if (dir > 0f) -len + ph * (w + len * 2f) else w + len - ph * (w + len * 2f)
                val a = ev.windShow * (0.3f + 0.5f * ev.gust) * sin(ph * 3.14159f)
                val th = (5f + 5f * fract(i * 0.47f)) * s
                val bend = sin(t * 3f + i) * 10f * s
                // a soft blue under-stroke (so the streak reads against white clouds), then the bright streak
                gr.line(x, y + th * 0.5f, x - dir * len, y + bend + th * 0.5f, th * 1.6f, Col.withA(0xFF6AA8E8.toInt(), a * 0.35f))
                gr.line(x, y, x - dir * len, y + bend, th, Col.withA(0xFFF6FCFF.toInt(), a))
                gr.line(x - dir * len * 0.15f, y - th * 1.8f, x - dir * len * 0.7f, y - th * 1.8f + bend * 0.6f, th * 0.5f, Col.withA(0xFFF6FCFF.toInt(), a * 0.6f))
            }
        }
        if (g.speedOn && g.player.state == PS.NORMAL) {
            // speed lines at the screen edges only (never over the path)
            for (i in 0 until 16) {
                val a = i / 16f * TAU + t * 0.3f
                val ph = fract(t * 2.4f + i * 0.37f)
                val r0 = (0.62f + ph * 0.4f) * max(w, h) * 0.55f
                val x0 = w * 0.5f + cos(a) * r0; val y0 = h * 0.55f + sin(a) * r0
                val x1 = w * 0.5f + cos(a) * (r0 + 80f * s); val y1 = h * 0.55f + sin(a) * (r0 + 80f * s)
                gr.line(x0, y0, x1, y1, 3f * s, Col.withA(Col.WHITE, 0.28f * (1f - ph)))
            }
        }
        // lava heat glow
        val ly = g.ev.lavaY
        if (ly > -50f) {
            val d = g.player.y - ly
            val k = clamp01(1f - d / 6f)
            gr.fillRectGradient(0f, h * 0.6f, w, h, 0x00FF5010, Col.withA(0xFFFF5010.toInt(), 0.18f + 0.3f * k))
        }
        // danger glow at the bottom edge while the guard closes in
        if (g.ev.chaseActive || g.ev.chase == Chase.WARNING) {
            val k = if (g.ev.chase == Chase.WARNING) 0.7f else g.ev.meter
            if (k > 0.4f) {
                val a = (k - 0.4f) * 0.85f * (0.7f + 0.3f * pulse(t, 10f))
                gr.fillRectGradient(0f, h * 0.72f, w, h, 0x00FF2010, Col.withA(0xFFFF2010.toInt(), a))
                gr.fillRectGradient(0f, 0f, w * 0.12f, h, Col.withA(0xFFFF2010.toInt(), a * 0.6f), 0x00FF2010, false)
                gr.fillRectGradient(w * 0.88f, 0f, w, h, 0x00FF2010, Col.withA(0xFFFF2010.toInt(), a * 0.6f), false)
            }
        }
        if (g.ev.finalOn && g.state == GS.PLAY) {
            val k = 0.12f + 0.05f * pulse(t, 3f)
            gr.fillRectGradient(0f, h * 0.8f, w, h, 0x00FF8A30, Col.withA(0xFFFF8A30.toInt(), k))
        }
        if (g.player.state == PS.CAUGHT || (g.state == GS.FAILED && g.failReason.startsWith("CAUGHT"))) {
            gr.fillRectGradient(0f, 0f, w, h * 0.3f, 0x99000008.toInt(), 0x00000008)
            gr.fillRectGradient(0f, h * 0.7f, w, h, 0x00000008, 0x99000008.toInt())
        }
        if (g.flashRed > 0f) {
            gr.fillRectGradient(0f, 0f, w, h * 0.22f, Col.withA(0xFFFF1020.toInt(), 0.4f * g.flashRed), 0x00FF1020)
            gr.fillRectGradient(0f, h * 0.78f, w, h, 0x00FF1020, Col.withA(0xFFFF1020.toInt(), 0.4f * g.flashRed))
        }
        if (g.flashWhite > 0f) gr.fillRect(0f, 0f, w, h, Col.withA(Col.WHITE, g.flashWhite * 0.6f))
    }

}
