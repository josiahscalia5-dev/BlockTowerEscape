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
        const val PILLAR = 13; const val LOOPSEG = 14; const val RELIC = 15
        const val SLIDESEG = 16; const val ISLAND = 17; const val LASER = 18; const val SPIKEBOX = 19
        const val MINION = 20; const val BOLT = 21; const val GEM = 22; const val CHAIN = 23
        const val ROLLER = 24; const val SPIRIT = 25; const val HAND = 26; const val SHOCK = 27; const val SHARD = 28
        const val CHEST = 29; const val RUNE = 30; const val SANCT = 31; const val SWITCH = 32 }

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
    /** The colour far things fade toward: the sky's blue (Level 7: the violet-pink of its dusk). */
    private val skyHaze = if (g.spec.enchanted) 0xFFD6BEEE.toInt() else 0xFFA8D0FA.toInt()
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
        if (g.spec.skyIsles && g.seaY > -99f) drawLava(gr, g.seaY, true, art.clouds)
        if (g.spec.temple) drawHeli(gr)
        if (g.ev.lavaY > -50f) drawLava(gr, g.ev.lavaY, false)
        playerBox()
        drawGate(gr)
        if (g.spec.enchanted) drawSkyLayer(gr)
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
                K.SLIDESEG -> drawSlideSeg(gr, r as SlideSeg)
                K.ISLAND -> drawIsland(gr, r as Island)
                K.LASER -> drawLaser(gr, r as Laser)
                K.SPIKEBOX -> drawSpikeBox(gr, r as SpikeBox)
                K.MINION -> drawMinion(gr, r as Minion)
                K.BOLT -> drawBolt(gr, r as Bolt)
                K.GEM -> drawGemPickup(gr, r as GemPickup)
                K.CHAIN -> drawChain(gr, r as ChainLink)
                K.ROLLER -> drawRoller(gr, r as Roller)
                K.SPIRIT -> drawSpirit(gr, r as Spirit)
                K.HAND -> drawHand(gr, r as Hand)
                K.SHOCK -> drawShock(gr, r as Shock)
                K.SHARD -> drawShard(gr, r as Shard)
                K.CHEST -> drawChest(gr, r as Chest)
                K.RUNE -> drawRune(gr, r as Rune)
                K.SANCT -> drawSanctuary(gr, r as Sanctuary)
                K.SWITCH -> drawSecretSwitch(gr, r as Secret)
            }
        }
        for (i in 0 until n) refs[i] = null
        drawParticles(gr)
        drawWorldPopups(gr)
        drawScreenEffects(gr)
    }

    // ------------------------------------------------------------------ background
    private fun drawBackground(gr: Gfx) {
        if (g.spec.plate) { drawPlateBackground(gr); return }
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
        val psl = p.slide
        var boyKey = -1f
        if (plp != null && p.state == PS.LOOP) {
            // on the loop: the body's middle, in from the track (drawn over the block he runs on)
            val u = p.loopU
            boyKey = cam.dist2(p.x, p.y + 0.7f * plp.upY(u), p.z + 0.7f * plp.upZ(u)) - 0.6f
            push(K.PLAYER, p, boyKey)
        } else if (psl != null && p.state == PS.SLIDE) {
            // on a slide: the body's middle, in from the channel (drawn over the stretch of channel he rides in)
            psl.normal(p.slideS, p.slideTh, tv, 0)
            boyKey = cam.dist2(p.x + 0.7f * tv[0], p.y + 0.7f * tv[1], p.z + 0.7f * tv[2])
            push(K.PLAYER, p, boyKey)
        } else if (p.state != PS.DEAD || p.y > p.lastGroundY - 20f) push(K.PLAYER, p, cam.dist2(p.x, p.y + 0.5f, p.z - 0.55f))
        if (p.state == PS.RESCUE_RIDE) push(K.RIDE, p, cam.dist2(p.x, p.y - 0.1f, p.z) + 0.5f)
        val gd = g.ev.guard
        if (gd.on) { g.ev.guardView(gvw); push(K.GUARD, gd, cam.dist2(gvw[0], gd.y + 2f, gvw[1] - 0.3f)) }
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
        // ---- Level 6: rainbow slides (a stretch at a time), islands, laser gates, spiked blocks
        for (sl in g.world.slides) {
            for (sg in slideSegs(sl)) {
                val dd = cam.depthOf(sg.cx, sg.cy, sg.cz)
                if (dd < -1.5f || dd > maxDepth + 3f) continue
                var key = cam.dist2(sg.cx, sg.cy, sg.cz)
                // the stretch he is riding in (and just behind him) is drawn under him
                if (psl === sl && p.state == PS.SLIDE && sg.s1 > p.slideS - 3.2f && sg.s0 < p.slideS + 0.9f) key = max(key, boyKey + 0.01f)
                push(K.SLIDESEG, sg, key)
            }
        }
        for (isl in g.world.islands) {
            val dd = cam.depthOf(isl.x, isl.top - 2f, isl.z)
            if (dd < -isl.d || dd > maxDepth + 6f) continue
            push(K.ISLAND, isl, cam.dist2(isl.x, isl.top - 2.6f, isl.z))
        }
        for (l in g.world.lasers) {
            val dd = cam.depthOf((l.x0 + l.x1) * 0.5f, l.y, l.z)
            if (dd < 0.4f || dd > maxDepth) continue
            push(K.LASER, l, cam.dist2((l.x0 + l.x1) * 0.5f, l.y, l.z))
        }
        for (b in g.world.spikeBoxes) {
            val dd = cam.depthOf(b.x, b.y + b.size * 0.5f, b.z)
            if (dd < 0.4f || dd > maxDepth) continue
            push(K.SPIKEBOX, b, cam.dist2(b.x, b.y + b.size * 0.5f, b.z))
        }
        // ---- Level 7: the Sorcerer's minions and their bolts, gems over the path, chains
        for (m in g.minions.all) {
            if (m.state == MS.HIDDEN || m.show <= 0.01f) continue
            val dd = cam.depthOf(m.x, m.y + 0.5f, m.z)
            if (dd < 0.4f || dd > maxDepth) continue
            push(K.MINION, m, cam.dist2(m.x, m.y + 0.5f, m.z))
        }
        for (b in g.minions.bolts) if (b.on) push(K.BOLT, b, cam.dist2(b.x, b.y, b.z) - 0.2f)
        for (gp in g.world.gemPicks) {
            if (gp.collected) continue
            val dd = cam.depthOf(gp.x, gp.y, gp.z)
            if (dd < 0.5f || dd > maxDepth) continue
            push(K.GEM, gp, cam.dist2(gp.x, gp.y, gp.z))
        }
        for (c in g.world.chains) {
            val mx = (c.x0 + c.x1) * 0.5f; val my = (c.y0 + c.y1) * 0.5f; val mz = (c.z0 + c.z1) * 0.5f
            val dd = cam.depthOf(mx, my, mz)
            if (dd < 0.6f || dd > maxDepth) continue
            push(K.CHAIN, c, cam.dist2(mx, my, mz))
        }
        if (g.spec.enchanted) collectSorcery()
    }

    /** Level 7: the Sorcerer's creatures and spells, the star runes and sanctuaries, the chests and the secret switches. */
    private fun collectSorcery() {
        val so = g.sorcery
        for (r in so.rollers) {
            if (r.state == 0 || r.show <= 0.01f) continue
            val dd = cam.depthOf(r.x, r.y + 0.45f, r.z)
            if (dd < 0.4f || dd > maxDepth) continue
            push(K.ROLLER, r, cam.dist2(r.x, r.y + 0.45f, r.z))
        }
        for (sp in so.spirits) {
            if (sp.state == 0 || sp.state == 5) continue
            val dd = cam.depthOf(sp.x, sp.y + 1f, sp.z)
            if (dd < 0.4f || dd > maxDepth) continue
            push(K.SPIRIT, sp, cam.dist2(sp.x, sp.y + 0.9f, sp.z))
        }
        for (h in so.hands) {
            if (h.reach <= 0.01f && h.warn <= 0.01f) continue
            val dd = cam.depthOf(h.x, h.y, h.z)
            if (dd < 0.4f || dd > maxDepth) continue
            push(K.HAND, h, cam.dist2((h.x + h.ex) * 0.5f, h.y + 0.6f, h.z) - 0.3f)
        }
        for (sh in so.shocks) if (sh.on) push(K.SHOCK, sh, cam.dist2(sh.x, sh.y + 0.3f, sh.z) - 0.4f)
        for (sh in so.shards) if (sh.on) push(K.SHARD, sh, cam.dist2(sh.x, sh.y + 0.5f, sh.z) - 0.2f)
        for (c in g.world.chests) {
            val dd = cam.depthOf(c.x, c.y + 0.4f, c.z)
            if (dd < 0.4f || dd > maxDepth) continue
            push(K.CHEST, c, cam.dist2(c.x, c.y + 0.4f, c.z))
        }
        for (r in g.world.runes) {
            val dd = cam.depthOf(r.x, r.y, r.z)
            if (dd < 0.4f || dd > maxDepth) continue
            // a decal on its block's top (drawn over the block), and its beam of light once lit
            push(K.RUNE, r, cam.dist2(r.x, r.y - 0.5f, r.z) - 0.35f)
        }
        for (sa in g.world.sanctuaries) {
            val zc = (sa.z0 + sa.z1) * 0.5f
            val dd = cam.depthOf(sa.x, sa.y, zc)
            if (dd < -2f || dd > maxDepth) continue
            push(K.SANCT, sa, cam.dist2(sa.x, sa.y + 1.2f, zc) - 1f)
        }
        for (se in g.world.secrets) {
            if (!se.switch) continue
            val dd = cam.depthOf(se.x, se.y, se.z)
            if (dd < 0.4f || dd > maxDepth) continue
            push(K.SWITCH, se, cam.dist2(se.x, se.y - 0.5f, se.z) - 0.35f)
        }
    }
    private val tv = FloatArray(12)

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

    private fun toolImg(k: Int) = art.toolIcon(k)

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
        var a = (1f - smooth((cam.depth - (maxDepth - 10f)) / 10f)) * smooth((cam.depth - 2.2f) / 1.6f)
        // a hidden coin (Level 7's secret routes) shows itself only when the boy is near
        if (c.hidden) a *= c.reveal
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
        val blink = p.invuln > 0f && (p.state == PS.NORMAL || p.state == PS.SLIDE) && ((t * 14f).toInt() and 1) == 1
        // sprite metrics: the boy is ~1.95 world units tall at the camera's framing distance
        val worldH = g.spec.boyH
        val lp = p.loop
        if (lp != null && p.state == PS.LOOP) loopFooting(gr, p, lp)
        val psl = p.slide
        if (psl != null && p.state == PS.SLIDE) slideContact(gr, p, psl)
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
        if (p.state == PS.SLIDE) handSplash(gr, p, hPx)
        var midX = fx0; var midY = fy0 - hPx * 0.45f
        if (p.state == PS.LOOP || p.state == PS.SLIDE) {
            val r = p.loopRot * Math.PI.toFloat() / 180f
            val up = hPx * 0.45f * (if (p.state == PS.SLIDE) lerp(1f, p.slideSy, p.slidePose) else 1f)
            midX = fx0 + sin(r) * up; midY = fy0 - cos(r) * up
        }
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
        if (g.shieldHit > 0.01f) {
            // the bubble bursting as it takes the hit: a ring flying out and a white flash
            val u = 1f - g.shieldHit
            val r = hPx * 0.62f * (1f + u * 0.9f)
            gr.setAdditive(true)
            gr.glow(midX, midY, r * 1.2f, Col.withA(0xFFE0F6FF.toInt(), 0.55f * g.shieldHit))
            gr.setAdditive(false)
            gr.strokeCircle(midX, midY, r, 7f * g.hud.s * g.shieldHit, Col.withA(0xFFB8ECFF.toInt(), g.shieldHit))
            for (k in 0 until 6) {
                val an = k * TAU / 6f + 0.4f
                gr.line(midX + cos(an) * r * 0.7f, midY + sin(an) * r * 0.7f, midX + cos(an) * r * 1.15f, midY + sin(an) * r * 1.15f, 4f * g.hud.s, Col.withA(Col.WHITE, g.shieldHit))
            }
        }
        if (g.magnetOn) {
            // tethers of magnetic light from everything it is pulling in
            gr.setAdditive(true)
            for (c in g.world.coins) {
                if (!c.pulled || c.collected || abs(c.z - p.z) > 7f) continue
                if (!cam.project(c.x, c.y, c.z)) continue
                gr.line(midX, midY, cam.sx, cam.sy, 3f * g.hud.s, Col.withA(if (((c.oz * 3f).toInt() and 1) == 0) 0xFFFF6A6A.toInt() else 0xFF8FD0FF.toInt(), 0.55f))
            }
            for (gp in g.world.gemPicks) {
                if (!gp.pulled || gp.collected || abs(gp.z - p.z) > 7f) continue
                if (cam.project(gp.x, gp.y, gp.z)) gr.line(midX, midY, cam.sx, cam.sy, 3f * g.hud.s, Col.withA(0xFFE08AFF.toInt(), 0.55f))
            }
            gr.setAdditive(false)
        }
        for (l in g.magnetLinks) {
            // a blue block pulled in: a beam from where it was
            if (!cam.project(l[0], l[1], l[2])) continue
            val a = 1f - l[3] / 0.45f
            gr.setAdditive(true)
            gr.line(midX, midY, cam.sx, cam.sy, 12f * g.hud.s * a, Col.withA(0xFF5EA8FF.toInt(), 0.5f * a))
            gr.line(midX, midY, cam.sx, cam.sy, 4f * g.hud.s * a, Col.withA(0xFFDDEEFF.toInt(), 0.9f * a))
            gr.setAdditive(false)
        }
        if (g.hourglassOn) {
            // a golden clock face turning slowly round him while time is slowed
            val tl = g.tools[TK.SPEED]
            val a = if (tl.active < 1.5f && ((t * 8f).toInt() and 1) == 0) 0.4f else 1f
            val r = hPx * 0.72f
            gr.setAdditive(true)
            gr.glow(midX, midY, r, Col.withA(0xFFFFE0A0.toInt(), 0.18f * a))
            gr.setAdditive(false)
            for (k in 0 until 12) {
                val an = k * TAU / 12f + t * 0.4f
                val r0 = r * (if (k % 3 == 0) 0.84f else 0.9f)
                gr.line(midX + cos(an) * r0, midY + sin(an) * r0 * 0.9f, midX + cos(an) * r, midY + sin(an) * r * 0.9f, 3f * g.hud.s, Col.withA(0xFFFFE070.toInt(), 0.8f * a))
            }
            val hand = t * 0.8f
            gr.line(midX, midY, midX + cos(hand) * r * 0.6f, midY + sin(hand) * r * 0.55f, 3f * g.hud.s, Col.withA(0xFFFFF4C0.toInt(), 0.5f * a))
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

    private val sc3 = FloatArray(3)
    private val contactPoly = FloatArray(40)

    /** A point on a slide's surface, [s] along it (straight on past its ends), [th] round it, [inset] above it. */
    private fun slideAt(sl: Slide, s: Float, th: Float, inset: Float): Boolean {
        sl.point(clamp(s, 0f, sl.length), th, inset, sc3, 0)
        val o = if (s > sl.length) s - sl.length else if (s < 0f) s else 0f
        if (o != 0f) { val e = if (o > 0f) sl.length else 0f; sc3[0] += sl.tanX(e) * o; sc3[1] += sl.tanY(e) * o; sc3[2] += sl.tanZ(e) * o }
        return cam.project(sc3[0], sc3[1], sc3[2])
    }

    /**
     * Where he lies in the slide: the water under his body darker (his weight in it), a rim of foam round him and two
     * lines of wake spreading out behind his feet, so he is plainly on the surface, not over it.
     */
    private fun slideContact(gr: Gfx, p: Player, sl: Slide) {
        val w = smooth(p.slidePose) * clamp01(1f - p.slideHop / 0.7f)
        if (w < 0.03f) return
        val s = p.slideS; val th = p.slideTh
        var m = 0
        for (i in 0 until 18) {
            val a = i / 18f * TAU
            // his outline on the water: from his trailing feet to his chest, a little wider at the shoulders
            val along = 0.8f + cos(a) * 0.95f
            val across = sin(a) * (0.36f + 0.07f * cos(a))
            if (!slideAt(sl, s + along, th + across / sl.r, 0.025f)) return
            contactPoly[m * 2] = cam.sx; contactPoly[m * 2 + 1] = cam.sy; m++
        }
        gr.fillPoly(contactPoly, m, Col.withA(0xFF0A3470.toInt(), 0.3f * w))
        gr.strokePoly(contactPoly, m, 3.4f * g.hud.s, Col.withA(Col.WHITE, 0.5f * w))
        // the wake: foam spreading back from his feet, fading
        val spread = 0.07f + 0.004f * p.slideV
        for (side in -1..1 step 2) {
            var px0 = 0f; var py0 = 0f
            for (j in 0..8) {
                val back = j * 0.32f
                if (!slideAt(sl, s - back, th + side * (0.26f + j * spread) / sl.r, 0.025f)) break
                if (j > 0) {
                    val a = (1f - j / 9f) * 0.55f * w
                    gr.line(px0, py0, cam.sx, cam.sy, (5.5f - j * 0.45f) * g.hud.s, Col.withA(0xFFF4FCFF.toInt(), a))
                }
                px0 = cam.sx; py0 = cam.sy
            }
        }
    }

    /**
     * His hands in the slide's water: at each fist a patch of foam, a crown of droplets thrown up and a short wake
     * streaming back from it (drawn at the fists' places on screen, so they always meet his hands).
     */
    private fun handSplash(gr: Gfx, p: Player, hPx: Float) {
        val w = smooth((p.slidePose - 0.4f) / 0.6f) * clamp01(1f - p.slideHop / 0.25f)
        if (w < 0.03f) return
        val t = g.t
        val r = hPx * 0.05f * (0.8f + 0.03f * p.slideV)
        val h = g.rig.hands
        val rot = p.loopRot * Math.PI.toFloat() / 180f
        // "back" along the slide on screen: down the picture, turned with him
        val bx = -sin(rot); val by = cos(rot)
        for (k in 0..1) {
            val hx = h[k * 2]; val hy = h[k * 2 + 1] + r * 0.2f
            val out = if (k == 0) -1f else 1f
            val ox = cos(rot) * out; val oy = sin(rot) * out
            gr.fillCircle(hx, hy, r * 1.15f, Col.withA(0xFFEAF8FF.toInt(), 0.42f * w))
            // wake: two streaks spreading back from the hand
            for (side in -1..1 step 2) {
                val sx = hx + (bx * 3f + ox * side * 1.1f) * r; val sy = hy + (by * 3f + oy * side * 1.1f) * r
                gr.line(hx, hy, sx, sy, r * 0.42f, Col.withA(Col.WHITE, 0.3f * w))
            }
            // droplets thrown up and out, flickering as the water breaks round the hand
            for (d in 0 until 6) {
                val ph = fract(t * 3.1f + d * 0.37f + k * 0.5f)
                val spread = (d - 2.5f) * 0.55f
                val dx = (ox * (0.6f + ph * 1.6f) + bx * spread * 0.4f + (-oy) * 0f) * r + ox * spread * 0.3f * r
                val dy = (oy * (0.6f + ph * 1.6f)) * r - (1.2f + 2.2f * ph * (1f - ph) * 4f) * r * 0.5f + by * spread * 0.3f * r
                gr.fillCircle(hx + dx, hy + dy, r * (0.34f - 0.18f * ph), Col.withA(Col.WHITE, 0.8f * (1f - ph) * w))
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
    private val gateW get() = when { g.spec.volcano -> 16f; g.spec.temple -> 14f; g.spec.enchanted -> 15f; else -> 13.4f }
    private val gateThreshold get() = when { g.spec.volcano -> 0.667f; g.spec.temple -> 0.8f; g.spec.enchanted -> 0.879f; else -> 0.647f }
    private val archU get() = when { g.spec.volcano -> 0.523f; g.spec.temple -> 0.4955f; g.spec.enchanted -> 0.5f; else -> 0.516f }
    private val archV get() = when { g.spec.volcano -> 0.545f; g.spec.temple -> 0.58f; g.spec.enchanted -> 0.682f; else -> 0.4f }
    private val archR get() = when { g.spec.volcano -> 0.088f; g.spec.temple -> 0.075f; g.spec.enchanted -> 0.175f; else -> 0.085f }
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
        if (g.spec.temple) {
            // exactly where the sky temple stands in the Level 6 design, riding on the plate
            lw = 220f * jbS * k; lcx = jbLeft + (544f + 110f) * jbS; ltop = jbTop + 96f * jbS
        }
        if (g.spec.enchanted) {
            // exactly where the Celestial Gate stands in the Level 7 design (its cut-out spans x 446..834, from y 84),
            // riding on the plate, growing a little as the boy gets nearer
            lw = 388f * jbS * k; lcx = jbLeft + 640f * jbS; ltop = jbTop + (320f - 236f * k) * jbS
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
        // warm light behind the gate, stronger as you get close and when the gate is open (the temple's portal is a
        // small ring in a big building: its light stays round the portal)
        gr.setAdditive(true)
        if (g.spec.temple) gr.glow(ax, ay, gw * (0.16f + 0.04f * near), Col.withA(0xFFC070FF.toInt(), fade * (0.14f + 0.1f * near + 0.08f * pulse(t, 2f))))
        else if (g.spec.enchanted) {
            // the castle's violet halo and the star portal's golden light behind it (softer once the castle is bigger
            // than the screen, so its light never washes the picture out)
            val big = lerp(0.3f, 1f, clamp01(gr.width * 1.2f / max(1f, gw)))
            gr.glow(ax, ay - gH * 0.1f, gw * (0.5f + 0.05f * near), Col.withA(0xFFB070FF.toInt(), big * fade * (0.2f + 0.1f * near + 0.06f * pulse(t, 2f))))
            gr.glow(ax, ay, gw * (0.24f + 0.04f * near), Col.withA(0xFFFFE0A0.toInt(), big * fade * (0.16f + 0.12f * near + 0.3f * po.charge)))
        }
        else gr.glow(ax, ay, gw * (0.55f + 0.1f * near), Col.withA(0xFFFF9A40.toInt(), fade * (0.18f + 0.18f * near + 0.1f * pulse(t, 2f))))
        gr.setAdditive(false)
        gr.image(img, cx - gw * 0.5f, ty, gw, gH, fade)
        if (g.spec.enchanted) {
            // the Celestial Gate awakens as the boy reaches its plaza (and flares when a checkpoint lights)
            val so = g.sorcery
            val awake = if (so.gateT >= 0f) smooth(so.gateT / 1.2f) else 0f
            celestialPortal(gr, ax, ay, gw, fade, near, open, max(po.charge, 0.55f * awake), w)
            val fl = max(so.gateFlare, awake * (1f - smooth((so.gateT - 1.2f) / 2f)))
            if (fl > 0.01f) {
                val big = lerp(0.35f, 1f, clamp01(gr.width * 1.2f / max(1f, gw)))
                val r = archR * gw
                gr.setAdditive(true)
                gr.glow(ax, ay, r * (2.2f + 2.5f * fl), Col.withA(0xFFFFE8A0.toInt(), 0.55f * fl * big * fade))
                val bw = r * (0.25f + 0.2f * fl)
                gr.fillRectGradient(ax - bw, -20f, ax + bw, ay, Col.withA(0xFFFFF4D0.toInt(), 0.25f * fl * big * fade), Col.withA(0xFFFFE070.toInt(), 0.6f * fl * big * fade))
                gr.setAdditive(false)
            }
            sealedBarrier(gr, ax, ay, archR * gw, gw, fade, open, dz); return
        }
        // the portal inside the arch: swirls, brightens as you approach, blazes when activated
        val charge = po.charge
        val r = archR * gw * (1f + 0.05f * sin(t * 3f))
        val spin = t * (1.2f + 0.8f * near + 5f * charge)
        val intensity = fade * (0.35f + 0.25f * near + (if (open) 0.15f else 0f) + 0.45f * charge)
        gr.setAdditive(true)
        val temple = g.spec.temple
        for (layer in 0..1) {
            val rr = r * (if (layer == 0) (if (temple) 0.85f else 1.25f) else (if (temple) 0.6f else 0.85f))
            val a0 = if (layer == 0) spin else -spin * 1.4f
            for (i in 0..3) {
                val a = a0 + i * TAU / 4f + TAU / 8f
                gq[i * 2] = ax + cos(a) * rr * 1.414f; gq[i * 2 + 1] = ay + sin(a) * rr * 1.414f * (if (temple) 1f else 1.55f)
            }
            gr.imageQuad(art.swirl, 0f, 0f, art.swirl.w.toFloat(), art.swirl.h.toFloat(), gq, intensity * (if (layer == 0) 0.9f else 0.6f) * (if (temple) 0.6f else 1f))
        }
        if (g.spec.temple) gr.glow(ax, ay, r * (1.5f + 0.8f * charge), Col.withA(0xFFE8B8FF.toInt(), intensity * 0.45f))
        else gr.glow(ax, ay + r * 0.6f, r * (2.2f + 1.5f * charge), Col.withA(0xFFFFD27A.toInt(), intensity * 0.6f))
        if (g.spec.temple) {
            // the sky temple's beam of light, rising from the portal into the sky (as in the design)
            val bw = r * (0.32f + 0.05f * sin(t * 5f))
            // (bright in the distance, softer up close so the temple itself stays readable)
            val ba = fade * (0.45f + 0.2f * pulse(t, 3f) + 0.3f * charge) * (1f - 0.65f * w * (1f - charge))
            gr.fillRectGradient(ax - bw, -20f, ax + bw, ay, Col.withA(0xFFB8F4FF.toInt(), ba * 0.5f), Col.withA(0xFFE0FAFF.toInt(), ba))
            gr.fillRectGradient(ax - bw * 0.35f, -20f, ax + bw * 0.35f, ay, Col.withA(Col.WHITE, ba * 0.6f), Col.withA(Col.WHITE, ba))
        }
        gr.setAdditive(false)
        // sparkles drifting out of the portal, more of them the closer you get
        val sparks = 6 + (12 * near).toInt()
        for (i in 0 until sparks) {
            val ph = fract(t * 0.35f + i / sparks.toFloat())
            val sx = ax + sin(i * 2.3f + t) * r * (0.6f + ph)
            val sy = ay + r * 1.2f - ph * r * 3.2f
            star4(gr, sx, sy, gw * 0.012f * (1f - ph) * (1f + near), Col.withA(0xFFFFF0C0.toInt(), fade * (1f - ph)))
        }
        sealedBarrier(gr, ax, ay, r, gw, fade, open, dz)
    }

    /** Sealed: a magic barrier over the portal while the blue blocks are still missing and the player is close. */
    private fun sealedBarrier(gr: Gfx, ax: Float, ay: Float, r: Float, gw: Float, fade: Float, open: Boolean, dz: Float) {
        if (open || dz >= 22f || g.state != GS.PLAY) return
        val a = fade * clamp01((22f - dz) / 6f) * (0.35f + 0.15f * pulse(g.t, 4f))
        gr.setAdditive(true)
        gr.glow(ax, ay, r * 2.4f, Col.withA(0xFF60A8FF.toInt(), a))
        gr.setAdditive(false)
        gr.strokeCircle(ax, ay, r * 1.5f, gw * 0.008f, Col.withA(0xFFB8E0FF.toInt(), a * 1.6f))
    }

    private val ringPts = FloatArray(2 * 49)
    /**
     * The Celestial Gate's star portal (Level 7): the design's golden star turning slowly in a ring of violet and blue
     * light, two golden rings orbiting the castle, a beam of light rising from it into the sky and sparkles drifting out.
     * It brightens as the boy approaches and blazes when he enters.
     */
    private fun celestialPortal(gr: Gfx, ax: Float, ay: Float, gw: Float, fade: Float, near: Float, open: Boolean, charge: Float, inWorld: Float) {
        val t = g.t
        val r = archR * gw
        // (softer once the castle is bigger than the screen: the portal's light never washes the picture out)
        val big = lerp(0.4f, 1f, clamp01(gfx!!.width * 1.2f / max(1f, gw)))
        val intensity = big * fade * (0.45f + 0.25f * near + (if (open) 0.15f else 0f) + 0.45f * charge)
        gr.setAdditive(true)
        // the swirl of violet and blue light inside the ring
        val spin = t * (0.7f + 0.6f * near + 5f * charge)
        for (layer in 0..1) {
            val rr = r * (if (layer == 0) 0.95f else 0.7f)
            val a0 = if (layer == 0) spin else -spin * 1.3f
            for (i in 0..3) {
                val a = a0 + i * TAU / 4f + TAU / 8f
                gq[i * 2] = ax + cos(a) * rr * 1.414f; gq[i * 2 + 1] = ay + sin(a) * rr * 1.414f
            }
            gr.imageQuad(art.swirl, 0f, 0f, art.swirl.w.toFloat(), art.swirl.h.toFloat(), gq, intensity * (if (layer == 0) 0.55f else 0.4f))
        }
        gr.glow(ax, ay, r * (1.1f + 0.6f * charge), Col.withA(0xFF8A7CFF.toInt(), intensity * 0.35f))
        // the golden star, turning slowly
        val sa = t * 0.35f
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r * 0.62f else r * 0.26f
            val a = sa + i * TAU / 10f - TAU / 4f
            poly[i * 2] = ax + cos(a) * rr; poly[i * 2 + 1] = ay + sin(a) * rr
        }
        gr.fillPoly(poly, 10, Col.withA(0xFFFFE6A0.toInt(), intensity * (0.35f + 0.15f * pulse(t, 3f))))
        gr.glow(ax, ay, r * 0.55f, Col.withA(0xFFFFF4D8.toInt(), intensity * (0.4f + 0.4f * charge)))
        // two golden rings orbiting the castle (as the design's rings sweep round it), brighter on their near side
        for (k in 0..1) {
            val rx = gw * (if (k == 0) 0.6f else 0.5f); val ry = gw * (if (k == 0) 0.075f else 0.06f)
            val tilt = if (k == 0) -0.16f else 0.12f
            val ph = t * (if (k == 0) 0.5f else -0.65f)
            val cxr = ax; val cyr = ay - gw * (if (k == 0) 0.02f else 0.12f)
            val ct = cos(tilt); val st = sin(tilt)
            var px0 = 0f; var py0 = 0f
            for (i in 0..48) {
                val a = i / 48f * TAU
                val ex = cos(a) * rx; val ey = sin(a) * ry
                val x = cxr + ex * ct - ey * st; val y = cyr + ex * st + ey * ct
                if (i > 0) {
                    val front = 0.5f + 0.5f * sin(a)
                    val shimmer = 0.6f + 0.4f * sin(a * 3f - ph * 4f)
                    gr.line(px0, py0, x, y, gw * 0.009f * (0.6f + 0.6f * front), Col.withA(0xFFFFD27A.toInt(), fade * (0.18f + 0.4f * front) * shimmer * (0.7f + 0.3f * near)))
                }
                px0 = x; py0 = y
            }
            // sparks racing round the ring
            for (j in 0 until 3) {
                val a = ph + j * TAU / 3f
                val ex = cos(a) * rx; val ey = sin(a) * ry
                val front = 0.5f + 0.5f * sin(a)
                star4(gr, cxr + ex * ct - ey * st, cyr + ex * st + ey * ct, gw * 0.014f * (0.6f + front), Col.withA(0xFFFFF0C0.toInt(), fade * (0.4f + 0.6f * front)))
            }
        }
        // the beam of light rising from the portal into the sky (softer up close, so the castle stays readable)
        val bw = r * (0.22f + 0.04f * sin(t * 5f))
        val ba = fade * (0.4f + 0.2f * pulse(t, 3f) + 0.3f * charge) * (1f - 0.6f * inWorld * (1f - charge))
        gr.fillRectGradient(ax - bw, -20f, ax + bw, ay - r * 0.9f, Col.withA(0xFFE0C0FF.toInt(), ba * 0.5f), Col.withA(0xFFFFF0FF.toInt(), ba))
        gr.fillRectGradient(ax - bw * 0.35f, -20f, ax + bw * 0.35f, ay - r * 0.9f, Col.withA(Col.WHITE, ba * 0.6f), Col.withA(Col.WHITE, ba))
        gr.setAdditive(false)
        // sparkles drifting out of the portal, more of them the closer you get
        val sparks = 8 + (14 * near).toInt()
        for (i in 0 until sparks) {
            val ph = fract(t * 0.3f + i / sparks.toFloat())
            val sx = ax + sin(i * 2.3f + t) * r * (0.7f + 1.2f * ph)
            val sy = ay + r * 0.8f - ph * r * 3f
            star4(gr, sx, sy, gw * 0.011f * (1f - ph) * (1f + near), Col.withA(if (i % 3 == 0) 0xFFD8B8FF.toInt() else 0xFFFFF0C0.toInt(), fade * (1f - ph)))
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
        if (g.spec.plate) { drawGuardSprite(gr); return }
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
        drawBox(gr, r.x - 0.35f, r.y, r.z - 0.35f, r.x + 0.35f, r.y + 0.7f, r.z + 0.35f,
            if (volc) BC.BASALT else if (g.spec.temple) BC.STONE else BC.BRICK, 1, a, 0f, null, if (volc) OV.LAVA else 0)
    }

    // ------------------------------------------------------------------ Level 5: the volcanic sky fortress
    /** Level 5 plate placement (scale, left, top), updated each frame by [drawPlateBackground]. */
    private var jbS = 1f; private var jbLeft = 0f; private var jbTop = 0f

    /**
     * The Level 5 / Level 6 plate (the design's sky, floating islands and cliffs): it fills the screen with the design's
     * framing at the start (the fortress or the temple where the design shows it), then drifts with a gentle parallax
     * and climb (and, in Level 6, a little with the camera's turn down the slides).
     */
    private fun drawPlateBackground(gr: Gfx) {
        val w = gr.width.toFloat(); val h = gr.height.toFloat()
        val s = max(w / (art.artW - 70f), h / (art.artH - 40f))
        val px = clamp(-g.camX * 4f, -20f, 20f) * s
        val climb = clamp((cam.ey - 3.3f) * 1.6f, 0f, 60f) * s
        jbS = s
        // framed right of the design's centre so the fortress and its portal stand clear of the HUD's target panel
        val yawShift = if (g.spec.skyIsles) clamp(-cam.yaw * 90f, -40f, 40f) * s else 0f
        jbLeft = clamp(w * 0.5f - (art.bgArtX + 580f) * s + yawShift, w - art.artW * s + 22f * s, -22f * s) + px
        jbTop = -art.bgArtY * s - 10f * s + climb
        gr.fillRect(0f, 0f, w, h, when { g.spec.temple -> 0xFFB8CCF2.toInt(); g.spec.enchanted -> 0xFFE4C4EC.toInt(); else -> 0xFFE0805A.toInt() })
        val roll = cam.roll
        val rolled = abs(roll) > 0.0005f
        if (rolled) {
            gr.save(); gr.translate(cam.cx, cam.cy); gr.rotate(roll * 57.29578f)
            val z = 1f + abs(roll) * 2.6f
            gr.scale(z, z); gr.translate(-cam.cx, -cam.cy)
        }
        gr.image(art.bg, jbLeft, jbTop, art.artW * s, art.artH * s)
        if (jbTop > 0f) {
            if (g.spec.temple) gr.fillRectGradient(-40f, -40f, w + 40f, jbTop + 2f, 0xFF2A64D0.toInt(), 0xFF3A78DE.toInt())
            else if (g.spec.enchanted) gr.fillRectGradient(-40f, -40f, w + 40f, jbTop + 2f, 0xFF4A34A8.toInt(), 0xFF5C44B8.toInt())
            else gr.fillRectGradient(-40f, -40f, w + 40f, jbTop + 2f, 0xFF5A4E9A.toInt(), 0xFF6A5AA8.toInt())
        }
        if (rolled) gr.restore()
        // warm haze of the lava below, rising from the bottom of the screen (Level 6: a soft cloud glow)
        if (g.spec.temple) gr.fillRectGradient(0f, h * 0.5f, w, h, 0x00F4F0FF, 0x30F4F0FF)
        else if (g.spec.enchanted) gr.fillRectGradient(0f, h * 0.5f, w, h, 0x00FFE8F6, 0x34FFE8F6)
        else gr.fillRectGradient(0f, h * 0.45f, w, h, 0x00FF7A30, 0x40FF7A30)
    }

    /** Fades pictures standing in the world that come right up to the lens. */
    private fun nearFade(depth: Float) = smooth((depth - 1.2f) / 2.2f) * (1f - smooth((depth - (maxDepth - 10f)) / 10f))

    /** Scenery: torches, banners on the pillars, chain railings along the bridges. */
    private fun drawDeco(gr: Gfx, d: Deco) {
        when (d.kind) {
            DK.TORCH -> drawTorch(gr, d)
            DK.BANNER -> drawBanner(gr, d)
            DK.RAIL -> drawRail(gr, d)
            DK.SPIRE -> drawSpire(gr, d)
            DK.CRYSTAL -> drawCrystal(gr, d)
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
        if (!drawBox(gr, x - 0.3f, top - 2.2f, z - 0.3f, x + 0.3f, top, z + 0.3f, art.pillarColor, 1, 1f, 0f, null)) return
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
        val hz = if (g.spec.skyIsles) skyHaze else 0xFFFFA070.toInt()
        if (ez > z1) { quad(6, 7, 3, 2); gr.imageQuad(tex, 0f, 0f, ts, v1, q, a, 0.6f, hz, haze) }
        if (ex < x0) { quad(7, 4, 0, 3); gr.imageQuad(tex, 0f, 0f, ts * pl.d, v1, q, a, 0.62f, hz, haze) }
        if (ex > x1) { quad(5, 6, 2, 1); gr.imageQuad(tex, 0f, 0f, ts * pl.d, v1, q, a, 0.62f, hz, haze) }
        if (ez < z0) { quad(4, 5, 1, 0); gr.imageQuad(tex, 0f, 0f, ts * pl.w, v1, q, a, 0.8f, hz, haze) }
        if (ey > top) { quad(7, 6, 5, 4); gr.imageQuad(art.top[art.pillarColor][1], 0f, 0f, ts, ts, q, a, 0.95f, 0xFFFFA070.toInt(), haze) }
        // the lava's glow on the foot of the pillar
        if (!g.spec.skyIsles && cam.project(pl.x, bottom + 1.2f, z0) ) {
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
        val hanging = l.amp < 0.2f
        // the beam across the path at the pivot (a spiked ball hanging beside the path hangs from far above instead)
        if (!hanging) drawBox(gr, l.px - 3.1f, l.py, l.pz - 0.32f, l.px + 3.1f, l.py + 0.55f, l.pz + 0.32f, if (g.spec.enchanted) BC.MOON else BC.FORT, 0, a, 0f, null)
        val bx = l.cx; val by = l.cy
        if (hanging && cam.project(l.px, l.py + 7f, l.pz)) {
            val tx = cam.sx; val ty = cam.sy
            if (cam.project(l.px, l.py, l.pz)) {
                val sc = cam.scaleAt(cam.depth)
                gr.line(tx, ty, cam.sx, cam.sy, max(1.5f, 0.07f * sc), Col.withA(0xFF2A2630.toInt(), a))
            }
        }
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
     * The Guardian from the design (Level 5's lava golem, Level 6's stone Guardian): it climbs out of the lava (rises
     * out of the clouds) beside the path and runs after the boy on the left, reaching for him. A camera-facing picture
     * (the design shows its face).
     */
    private val gvw = FloatArray(2)
    private fun drawGuardSprite(gr: Gfx) {
        val img = art.guardian ?: return
        val gd = g.ev.guard
        val t = g.t
        val sw = sin(gd.step * Math.PI.toFloat())
        val bob = abs(sw) * 0.22f
        var x = gd.x; var z = gd.z
        var baseY = gd.y - 1.4f - (1f - gd.rise) * 5f + bob + gd.reach * 0.3f
        if (g.spec.temple) {
            // Level 6: it hovers beside the path on the left, level with the boy and looming over him (as in the
            // design), bobbing on the air; it drifts in toward him as it catches up
            g.ev.guardView(gvw); x = gvw[0]; z = gvw[1]
            baseY += 0.6f + sin(t * 1.7f) * 0.25f
        }
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
        val temple = g.spec.temple
        gr.setAdditive(true)
        gr.glow(bx, by - h * 0.5f, w * 0.55f, Col.withA(if (temple) 0xFFA050FF.toInt() else 0xFFFF6A10.toInt(),
            a * ((if (temple) 0.18f else 0.25f) + 0.15f * angry + 0.08f * pulse(t, 7f))))
        gr.setAdditive(false)
        // it leans toward the boy as it reaches for him
        val lean = -4f + 6f * gd.reach + sin(gd.step * Math.PI.toFloat()) * 2f
        gr.save(); gr.translate(bx, by); gr.rotate(lean)
        gr.image(img, -w * 0.5f, -h, w, h, a)
        gr.restore()
        // eyes blaze when it is angry (the lava golem's gold, the stone Guardian's purple)
        gr.setAdditive(true)
        if (temple) {
            val ec = 0xFFD070FF.toInt()
            gr.glow(bx - w * 0.03f, by - h * 0.535f, w * 0.06f, Col.withA(ec, a * (0.45f + 0.45f * angry)))
            gr.glow(bx + w * 0.148f, by - h * 0.506f, w * 0.055f, Col.withA(ec, a * (0.45f + 0.45f * angry)))
        } else {
            val ey = by - h * 0.4f
            gr.glow(bx + w * 0.07f, ey, w * 0.05f, Col.withA(0xFFFFD040.toInt(), a * (0.35f + 0.4f * angry)))
            gr.glow(bx + w * 0.19f, ey + h * 0.02f, w * 0.045f, Col.withA(0xFFFFD040.toInt(), a * (0.35f + 0.4f * angry)))
        }
        gr.setAdditive(false)
        // embers and smoke rising off the lava golem; pebbles and purple sparks off the stone Guardian
        if (g.fx.rng.f() < 0.25f && a > 0.4f) {
            val q2 = g.fx.spawn()
            q2.x = x + g.fx.rng.f(-2.2f, 2.2f); q2.y = baseY + g.fx.rng.f(0.5f, 3.5f); q2.z = z
            q2.vx = g.fx.rng.f(-0.3f, 0.3f); q2.vy = g.fx.rng.f(1f, 2.5f); q2.vz = 0f
            q2.life = g.fx.rng.f(0.5f, 1f); q2.maxLife = q2.life; q2.size = g.fx.rng.f(0.05f, 0.1f)
            if (temple) { q2.color = 0xFFC880FF.toInt(); q2.kind = PK.SPARK; q2.vy = -q2.vy * 0.5f } else { q2.color = 0xFFFFA040.toInt(); q2.kind = PK.EMBER }
        }
    }

    // ------------------------------------------------------------------ lava
    /**
     * A lava surface at height [ly]: the rising lava (Level 4) or, with [sea], Level 5's lava sea far below the
     * course, which fades out into the heat haze in the distance so it meets the sky plate softly.
     */
    private fun drawLava(gr: Gfx, ly: Float, sea: Boolean, texture: Img? = null) {
        val tex = texture ?: art.lava
        val clouds = texture != null
        val t = g.t
        val tile = 4f
        // tiles around the point the camera looks at (the camera may look sideways, e.g. round the loop)
        val fx0 = cam.ex + sin(cam.yaw) * 26f; val fz0 = cam.ez + cos(cam.yaw) * 26f
        val ox = floor(fx0 / tile) * tile
        val oz = floor(fz0 / tile) * tile
        val flow = fract(t * (if (clouds) 0.02f else if (sea) 0.05f else 0.08f)) * tile
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
                if (clouds) gr.imageQuad(tex, 0f, 0f, tex.w.toFloat(), tex.h.toFloat(), q, a, 1f, skyHaze, hazeFor(d) * 0.7f)
                else gr.imageQuad(tex, 0f, 0f, tex.w.toFloat(), tex.h.toFloat(), q, a, 1f + 0.1f * sin(t * 3f + i + j), 0xFFFF9A40.toInt(), hazeFor(d) * 0.6f)
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
            // a particle passing right by the lens fades away instead of filling the screen
            if (cam.depth < 1.1f) continue
            val s = cam.scaleAt(cam.depth)
            val r = p.size * s
            if (r < 0.6f) continue
            val lf = clamp01(p.life / p.maxLife) * smooth((cam.depth - 1.1f) / 1.4f)
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
        // Level 7: the Sorcerer's Wrath pulses magenta at the screen's edges (brighter as he charges a spell)
        val so = g.sorcery
        if (g.spec.enchanted && so.active && g.state == GS.PLAY) {
            val a = (0.1f + 0.1f * pulse(t, 2.2f) + 0.22f * so.charge) * (1f - 0.6f * so.flinch)
            gr.fillRectGradient(0f, 0f, w, h * 0.16f, Col.withA(0xFFD02080.toInt(), a), 0x00D02080)
            gr.fillRectGradient(0f, 0f, w * 0.1f, h, Col.withA(0xFFD02080.toInt(), a * 0.7f), 0x00D02080, false)
            gr.fillRectGradient(w * 0.9f, 0f, w, h, 0x00D02080, Col.withA(0xFFD02080.toInt(), a * 0.7f), false)
        }
        // Level 7's hourglass: time slows: a pale gold-and-blue tint at the edges and grains of golden sand drifting down
        if (g.hourglassOn) {
            val k = clamp01((1f - g.hazK) / (1f - HOURGLASS_K))
            gr.fillRectGradient(0f, 0f, w * 0.14f, h, Col.withA(0xFF9FE8FF.toInt(), 0.22f * k), 0x009FE8FF, false)
            gr.fillRectGradient(w * 0.86f, 0f, w, h, 0x009FE8FF, Col.withA(0xFF9FE8FF.toInt(), 0.22f * k), false)
            gr.fillRectGradient(0f, h * 0.86f, w, h, 0x00FFE070, Col.withA(0xFFFFE070.toInt(), 0.14f * k))
            for (i in 0 until 14) {
                val ph = fract(t * 0.12f + i * 0.071f)
                val x = (if (i % 2 == 0) fract(i * 0.37f) * 0.12f else 0.88f + fract(i * 0.53f) * 0.12f) * w
                star4(gr, x, ph * h, (4f + 3f * fract(i * 0.29f)) * s, Col.withA(0xFFFFE8A0.toInt(), 0.7f * k * sin(ph * Math.PI.toFloat())))
            }
        }
        // the shield blocking a hit: a pale blue flash round the screen
        if (g.shieldHit > 0.01f) {
            val a = 0.35f * g.shieldHit
            gr.fillRectGradient(0f, 0f, w, h * 0.2f, Col.withA(0xFF9FE8FF.toInt(), a), 0x009FE8FF)
            gr.fillRectGradient(0f, h * 0.8f, w, h, 0x009FE8FF, Col.withA(0xFF9FE8FF.toInt(), a))
        }
        // Level 7: a purple glow at the edges while the minions close in
        if (g.minions.pursuit && g.state == GS.PLAY) {
            val k = g.minions.meter
            if (k > 0.35f) {
                val a = (k - 0.35f) * 0.8f * (0.7f + 0.3f * pulse(t, 9f))
                gr.fillRectGradient(0f, h * 0.74f, w, h, 0x00A030F0, Col.withA(0xFFA030F0.toInt(), a))
                gr.fillRectGradient(0f, 0f, w * 0.12f, h, Col.withA(0xFFA030F0.toInt(), a * 0.6f), 0x00A030F0, false)
                gr.fillRectGradient(w * 0.88f, 0f, w, h, 0x00A030F0, Col.withA(0xFFA030F0.toInt(), a * 0.6f), false)
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


    // ------------------------------------------------------------------ Level 6: the rainbow slides
    /** A stretch of a slide (from s0 to s1) drawn as one piece of the painter's list; (cx, cy, cz) is its sort point. */
    class SlideSeg(val sl: Slide, val i: Int, val s0: Float, val s1: Float) { var cx = 0f; var cy = 0f; var cz = 0f }
    private val slideSegCache = HashMap<Slide, Array<SlideSeg>>()
    private fun slideSegs(sl: Slide): Array<SlideSeg> = slideSegCache.getOrPut(sl) {
        val n = max(1, (sl.length / 0.5f).toInt())
        Array(n) { i ->
            val s0 = sl.length * i / n; val s1 = sl.length * (i + 1) / n
            SlideSeg(sl, i, s0, s1).also { sl.point((s0 + s1) * 0.5f, 0f, sl.r * 0.45f, sp3, 0); it.cx = sp3[0]; it.cy = sp3[1]; it.cz = sp3[2] }
        }
    }
    private val sp3 = FloatArray(12)
    /** The slides' glossy rainbow bands, as in the design: blue, pink, yellow, red, purple, sky blue. */
    private val slideBands = intArrayOf(0xFF1E7CFF.toInt(), 0xFFF03CC4.toInt(), 0xFFFFC21E.toInt(), 0xFFF2402E.toInt(), 0xFF8C3CF2.toInt(), 0xFF2CC4FF.toInt())
    private val slWall = 0.16f
    // per-facet scratch for the in-stretch sort: screen corners, depth, kind (0 inner, 1 outer, 2 lip), angle
    private val fq = FloatArray(8 * 20); private val fdep = FloatArray(20); private val fkind = IntArray(20); private val fth = FloatArray(20)
    private val fcol = IntArray(20); private val ford = Array(20) { it }
    private val lightX = -0.35f; private val lightY = 0.86f; private val lightZ = -0.37f

    private fun slideCorner(sl: Slide, s: Float, th: Float, inset: Float, k: Int, o: Int): Boolean {
        sl.point(s, th, inset, sp3, 0)
        if (!cam.project(sp3[0], sp3[1], sp3[2])) return false
        fq[k * 8 + o * 2] = cam.sx; fq[k * 8 + o * 2 + 1] = cam.sy
        return true
    }

    /**
     * One stretch of a rainbow slide: an open channel of glossy striped plastic (a closed tube for the scenery ones)
     * with water running down its bottom. Each of its faces is lit (a soft top light and a shine), the faces are drawn
     * back to front, and the stretch fades when it passes the lens or would hide the boy.
     */
    private fun drawSlideSeg(gr: Gfx, sg: SlideSeg) {
        val sl = sg.sl
        val t = g.t
        val thMax = if (sl.ride) 1.5f else Math.PI.toFloat()
        val m = if (sl.ride) 12 else 14
        val s0 = sg.s0; val s1 = sg.s1; val sm = (s0 + s1) * 0.5f
        val depth = cam.depthOf(sg.cx, sg.cy, sg.cz)
        // the stretch he is riding stays solid right up to the lens (he lies on it); the rest fades as it passes by
        val p = g.player
        val riding = p.state == PS.SLIDE && p.slide === sl && sg.s1 > p.slideS - 3.2f && sg.s0 < p.slideS + 0.9f
        var a = if (riding) smooth((depth - 0.5f) / 1.1f) else nearFade(depth) * smooth((depth - 1.4f) / 2.4f)
        if (a <= 0.01f) return
        val band = slideBands[(sg.i / 2) % slideBands.size]
        val haze = hazeFor(depth)
        var n = 0
        var minX = 1e9f; var maxX = -1e9f; var minY = 1e9f; var maxY = -1e9f
        for (j in 0 until m) {
            val th0 = -thMax + 2f * thMax * j / m; val th1 = -thMax + 2f * thMax * (j + 1) / m; val thm = (th0 + th1) * 0.5f
            sl.point(sm, thm, 0f, sp3, 0); sl.normal(sm, thm, sp3, 3)
            val vx = cam.ex - sp3[0]; val vy = cam.ey - sp3[1]; val vz = cam.ez - sp3[2]
            val inner = vx * sp3[3] + vy * sp3[4] + vz * sp3[5] > 0f
            if (!sl.ride && inner) continue
            val inset = if (inner) 0f else -slWall
            if (!slideCorner(sl, s0, th0, inset, n, 0) || !slideCorner(sl, s0, th1, inset, n, 1) ||
                !slideCorner(sl, s1, th1, inset, n, 2) || !slideCorner(sl, s1, th0, inset, n, 3)) continue
            sl.point(sm, thm, inset, sp3, 6)
            fdep[n] = cam.depthOf(sp3[6], sp3[7], sp3[8]); fkind[n] = if (inner) 0 else 1; fth[n] = thm
            // light: a soft top light on the outside (and the inside, a shade lighter), a shine where it catches the sun
            val nx = if (inner) sp3[3] else -sp3[3]; val ny = if (inner) sp3[4] else -sp3[4]; val nz = if (inner) sp3[5] else -sp3[5]
            val lam = max(0f, nx * lightX + ny * lightY + nz * lightZ)
            // inside: the rainbow bands seen through the water (a touch lighter); outside: the bright rainbow bands
            var c = if (inner) Col.scale(Col.mix(band, 0xFFEAF6FF.toInt(), 0.3f), 0.7f + 0.38f * lam) else Col.scale(band, 0.5f + 0.62f * lam)
            if (!inner) {
                val vl = kotlin.math.sqrt(vx * vx + vy * vy + vz * vz).coerceAtLeast(1e-3f)
                val hx = lightX + vx / vl; val hy = lightY + vy / vl; val hz = lightZ + vz / vl
                val hl = kotlin.math.sqrt(hx * hx + hy * hy + hz * hz).coerceAtLeast(1e-3f)
                val spec = max(0f, (nx * hx + ny * hy + nz * hz) / hl)
                val sh = spec * spec; val sh4 = sh * sh
                c = Col.mix(c, Col.WHITE, sh4 * sh4 * 0.85f)
            }
            fcol[n] = Col.mix(c, skyHaze, haze)
            for (k in 0..3) { minX = min(minX, fq[n * 8 + k * 2]); maxX = max(maxX, fq[n * 8 + k * 2]); minY = min(minY, fq[n * 8 + k * 2 + 1]); maxY = max(maxY, fq[n * 8 + k * 2 + 1]) }
            n++
        }
        // the lips along the top edges of the channel
        if (sl.ride) for (side in 0..1) {
            val th = if (side == 0) -thMax else thMax
            if (!slideCorner(sl, s0, th, 0f, n, 0) || !slideCorner(sl, s0, th, -slWall, n, 1) ||
                !slideCorner(sl, s1, th, -slWall, n, 2) || !slideCorner(sl, s1, th, 0f, n, 3)) continue
            sl.point(sm, th, 0f, sp3, 6)
            fdep[n] = cam.depthOf(sp3[6], sp3[7], sp3[8]) - 0.01f; fkind[n] = 2; fth[n] = th
            fcol[n] = Col.mix(Col.mix(band, Col.WHITE, 0.2f), skyHaze, haze)
            n++
        }
        if (n == 0) return
        if (maxX < -20f || minX > gr.width + 20f || maxY < -20f || minY > gr.height + 20f) return
        // it turns see-through where it would hide the boy (never the stretch he rides in: that is drawn under him)
        if (!riding && depth < pdepth - 0.3f && maxX > pbox[0] && minX < pbox[2] && maxY > pbox[1] && minY < pbox[3]) a *= 0.4f
        for (k in 0 until n) ford[k] = k
        java.util.Arrays.sort(ford, 0, n) { x, y -> fdep[y].compareTo(fdep[x]) }
        val sc = cam.scaleAt(depth)
        for (kk in 0 until n) {
            val k = ford[kk]
            for (i in 0..7) poly[i] = fq[k * 8 + i]
            gr.fillPoly(poly, 4, Col.withA(fcol[k], a))
            if (fkind[k] == 1 && sg.i % 2 == 0) {
                // the ridge between two coloured bands
                gr.line(poly[0], poly[1], poly[2], poly[3], max(1f, 0.05f * sc), Col.withA(Col.scale(band, 0.55f), a))
            }
            if (fkind[k] == 0 && sg.i % 2 == 0 && abs(fth[k]) > 1.0f) {
                // the band's colour shows at the top of the inner wall too, just under the lip
                gr.line(poly[0], poly[1], poly[2], poly[3], max(1f, 0.05f * sc), Col.withA(Col.scale(band, 0.8f), a * 0.6f))
            }
            if (fkind[k] == 0 && sl.ride && abs(fth[k]) < 0.75f) {
                // water running down the channel: a clear blue sheet with streaks of foam flowing toward the exit
                gr.fillPoly(poly, 4, Col.withA(0xFF6CCBFF.toInt(), a * 0.34f))
                val ph = fract(t * 1.7f - sg.i * 0.37f + fth[k] * 1.3f)
                val u0 = ph; val u1 = min(1f, ph + 0.45f)
                // along the stretch: from the s0 edge (corners 0,1) to the s1 edge (corners 3,2)
                val ax = lerp((poly[0] + poly[2]) * 0.5f, (poly[6] + poly[4]) * 0.5f, u0); val ay = lerp((poly[1] + poly[3]) * 0.5f, (poly[7] + poly[5]) * 0.5f, u0)
                val bx = lerp((poly[0] + poly[2]) * 0.5f, (poly[6] + poly[4]) * 0.5f, u1); val by = lerp((poly[1] + poly[3]) * 0.5f, (poly[7] + poly[5]) * 0.5f, u1)
                gr.setAdditive(true)
                gr.line(ax, ay, bx, by, max(1f, 0.05f * sc), Col.withA(0xFFF0FCFF.toInt(), a * 0.55f))
                gr.setAdditive(false)
            }
        }
        // speed rings: a glowing hoop across the channel with chevrons
        if (sl.ride) for (pu in sl.pads) if (pu >= s0 && pu < s1) slideRing(gr, sl, pu, a)
        // spike plates in the channel
        if (sl.ride) for (sp in sl.spikes) if (sp[0] >= s0 && sp[0] < s1) slideSpikes(gr, sl, sp[0], sp[1], a)
    }

    /** A speed ring across a slide: a glowing hoop over the channel. */
    private fun slideRing(gr: Gfx, sl: Slide, s: Float, a: Float) {
        var px = 0f; var py = 0f; var ok = false
        val pulseA = 0.7f + 0.3f * pulse(g.t, 6f)
        gr.setAdditive(true)
        for (i in 0..16) {
            val th = -1.6f + 3.2f * i / 16f
            sl.point(s, th, 0.05f, sp3, 0)
            if (!cam.project(sp3[0], sp3[1], sp3[2])) { ok = false; continue }
            val sc = cam.scaleAt(cam.depth)
            if (ok) { gr.line(px, py, cam.sx, cam.sy, max(2f, 0.2f * sc), Col.withA(0xFF4AD8FF.toInt(), a * 0.5f * pulseA)); gr.line(px, py, cam.sx, cam.sy, max(1f, 0.07f * sc), Col.withA(Col.WHITE, a * pulseA)) }
            px = cam.sx; py = cam.sy; ok = true
        }
        gr.setAdditive(false)
    }

    /** Iron spikes standing up out of a slide's channel at (s, th). */
    private fun slideSpikes(gr: Gfx, sl: Slide, s: Float, th: Float, a: Float) {
        for (k in 0..2) {
            val tk = th + (k - 1) * 0.22f
            sl.point(s, tk, 0.45f, sp3, 0)
            if (!cam.project(sp3[0], sp3[1], sp3[2])) return
            val tipX = cam.sx; val tipY = cam.sy
            sl.point(s - 0.18f, tk - 0.1f, 0f, sp3, 0); if (!cam.project(sp3[0], sp3[1], sp3[2])) return
            poly[0] = cam.sx; poly[1] = cam.sy
            sl.point(s + 0.18f, tk + 0.1f, 0f, sp3, 0); if (!cam.project(sp3[0], sp3[1], sp3[2])) return
            poly[2] = cam.sx; poly[3] = cam.sy
            poly[4] = tipX; poly[5] = tipY
            gr.fillPolyGradient(poly, 3, tipX, tipY, poly[0], poly[1], Col.withA(0xFFF4F6FF.toInt(), a), Col.withA(0xFF4A5068.toInt(), a))
        }
        sl.point(s, th, 0.2f, sp3, 0)
        if (cam.project(sp3[0], sp3[1], sp3[2])) { gr.setAdditive(true); gr.glow(cam.sx, cam.sy, 0.55f * cam.scaleAt(cam.depth), Col.withA(0xFFFF3A2A.toInt(), 0.3f * a)); gr.setAdditive(false) }
    }

    // ------------------------------------------------------------------ Level 6: floating islands
    private val iq = FloatArray(8)
    /** Projects a quad given as seen from outside (top-left, top-right, bottom-right, bottom-left); false when it faces away. */
    private fun faceQuad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx2: Float, cy2: Float, cz2: Float, dx: Float, dy: Float, dz: Float): Boolean {
        if (!cam.project(ax, ay, az)) return false; iq[0] = cam.sx; iq[1] = cam.sy
        if (!cam.project(bx, by, bz)) return false; iq[2] = cam.sx; iq[3] = cam.sy
        if (!cam.project(cx2, cy2, cz2)) return false; iq[4] = cam.sx; iq[5] = cam.sy
        if (!cam.project(dx, dy, dz)) return false; iq[6] = cam.sx; iq[7] = cam.sy
        var area = 0f
        for (i in 0..3) { val j = (i + 1) % 4; area += iq[i * 2] * iq[j * 2 + 1] - iq[j * 2] * iq[i * 2 + 1] }
        return area > 0f
    }

    /**
     * A floating island under the course: the rocky underside tapering away in two tiers, the stone slab with moss
     * on top and vines hanging over its edges, palms at its corners, and waterfalls pouring off it into the clouds.
     */
    private fun drawIsland(gr: Gfx, isl: Island) {
        val tex = art.pillarSide ?: return
        val x0 = isl.x - isl.w * 0.5f; val x1 = isl.x + isl.w * 0.5f; val z0 = isl.z - isl.d * 0.5f; val z1 = isl.z + isl.d * 0.5f
        val slabB = isl.top - 1.1f
        val d0 = cam.depthOf(isl.x, isl.top - 1f, isl.z)
        val a = nearFade(d0 + min(isl.d, isl.w) * 0.35f) * (1f - smooth((d0 - (maxDepth - 4f)) / 8f))
        if (a <= 0.01f) return
        val haze = hazeFor(d0)
        val ts = art.texSize.toFloat()
        val rnd = hash3(isl.seed, 7, 3)
        // the underside: two tiers narrowing to a rocky point, a little off-centre
        val ox = ((rnd and 255) / 255f - 0.5f) * isl.w * 0.25f; val oz = (((rnd shr 8) and 255) / 255f - 0.5f) * isl.d * 0.2f
        val midY = slabB - isl.depth * 0.5f; val botY = slabB - isl.depth
        tier(gr, tex, ts, isl.x, isl.z, isl.w, isl.d, slabB, isl.x + ox * 0.5f, isl.z + oz * 0.5f, isl.w * 0.66f, isl.d * 0.66f, midY, a, haze, 0.62f, 0.5f)
        tier(gr, tex, ts, isl.x + ox * 0.5f, isl.z + oz * 0.5f, isl.w * 0.66f, isl.d * 0.66f, midY, isl.x + ox, isl.z + oz, isl.w * 0.12f, isl.d * 0.12f, botY, a, haze, 0.5f, 0.36f)
        // the slab
        drawBox(gr, x0, slabB, z0, x1, isl.top, z1, art.pillarColor, isl.seed % 3, a, 0f, null)
        // moss on top and hanging over the edges
        if (cam.ey > isl.top && faceQuad(x0, isl.top + 0.005f, z1, x1, isl.top + 0.005f, z1, x1, isl.top + 0.005f, z0, x0, isl.top + 0.005f, z0)) {
            for (i in 0..7) poly[i] = iq[i]
            gr.fillPolyGradient(poly, 4, iq[0], iq[1], iq[6], iq[7], Col.withA(Col.mix(0xFF4E9E36.toInt(), skyHaze, haze), a), Col.withA(Col.mix(0xFF78C83E.toInt(), skyHaze, haze), a))
        }
        mossEdge(gr, x0, z0, x1, z0, isl.top, isl.seed, a, haze)
        mossEdge(gr, x1, z0, x1, z1, isl.top, isl.seed + 1, a, haze)
        mossEdge(gr, x0, z1, x0, z0, isl.top, isl.seed + 2, a, haze)
        // palms at the corners the course leaves free
        for (k in 0 until isl.palms) {
            val left = (k + isl.seed) % 2 == 0
            val px = if (left) x0 + 0.5f else x1 - 0.5f
            val pz = lerp(z0 + 0.6f, z1 - 0.6f, fract(k * 0.618f + isl.seed * 0.31f))
            if (g.spec.enchanted) drawBlossom(gr, px, isl.top, pz, 1.7f + 0.4f * fract(k * 0.37f + isl.seed * 0.11f), if (left) -1f else 1f, a, haze, isl.seed + k)
            else drawPalm(gr, px, isl.top, pz, 2.1f + 0.4f * fract(k * 0.37f + isl.seed * 0.11f), if (left) -1f else 1f, a, haze)
        }
        // waterfalls pouring off its edges into the clouds
        val fallH = isl.depth + 7f
        if (isl.falls and 1 != 0) waterfall(gr, x0 - 0.06f, isl.top - 0.15f, isl.z + isl.d * 0.12f, min(1.6f, isl.d * 0.45f), fallH, a, haze)
        if (isl.falls and 2 != 0) waterfall(gr, x1 + 0.06f, isl.top - 0.15f, isl.z - isl.d * 0.1f, min(1.6f, isl.d * 0.45f), fallH, a, haze)
        if (isl.falls and 4 != 0) waterfall(gr, isl.x + isl.w * 0.3f, isl.top - 0.15f, z0 - 0.06f, min(1.8f, isl.w * 0.3f), fallH, a, haze)
    }

    /** One tier of an island's underside: a box narrowing from the rectangle above to the one below. */
    private fun tier(gr: Gfx, tex: Img, ts: Float, ax: Float, az: Float, aw: Float, ad: Float, ay: Float,
                     bx: Float, bz: Float, bw: Float, bd: Float, by: Float, a: Float, haze: Float, mulTop: Float, mulBot: Float) {
        val tx0 = ax - aw * 0.5f; val tx1 = ax + aw * 0.5f; val tz0 = az - ad * 0.5f; val tz1 = az + ad * 0.5f
        val bx0 = bx - bw * 0.5f; val bx1 = bx + bw * 0.5f; val bz0 = bz - bd * 0.5f; val bz1 = bz + bd * 0.5f
        val v1 = min(tex.h.toFloat(), (ay - by) * ts)
        val mul = (mulTop + mulBot) * 0.5f
        if (faceQuad(tx0, ay, tz0, tx1, ay, tz0, bx1, by, bz0, bx0, by, bz0)) gr.imageQuad(tex, 0f, 0f, ts * aw, v1, iq, a, mul + 0.12f, skyHaze, haze)
        if (faceQuad(tx1, ay, tz0, tx1, ay, tz1, bx1, by, bz1, bx1, by, bz0)) gr.imageQuad(tex, 0f, 0f, ts * ad, v1, iq, a, mul, skyHaze, haze)
        if (faceQuad(tx1, ay, tz1, tx0, ay, tz1, bx0, by, bz1, bx1, by, bz1)) gr.imageQuad(tex, 0f, 0f, ts * aw, v1, iq, a, mul - 0.05f, skyHaze, haze)
        if (faceQuad(tx0, ay, tz1, tx0, ay, tz0, bx0, by, bz0, bx0, by, bz1)) gr.imageQuad(tex, 0f, 0f, ts * ad, v1, iq, a, mul, skyHaze, haze)
    }

    /** Moss along one top edge of an island (from (ax, az) to (bx, bz)), hanging over it in ragged drips. */
    private fun mossEdge(gr: Gfx, ax: Float, az: Float, bx: Float, bz: Float, top: Float, seed: Int, a: Float, haze: Float) {
        val len = len2(bx - ax, bz - az)
        val n = max(2, (len / 0.45f).toInt())
        val dark = Col.withA(Col.mix(0xFF3A7E2A.toInt(), skyHaze, haze), a); val light = Col.withA(Col.mix(0xFF6CBE3A.toInt(), skyHaze, haze), a)
        for (i in 0 until n) {
            val u0 = i / n.toFloat(); val u1 = (i + 1) / n.toFloat(); val um = (u0 + u1) * 0.5f
            val drop = 0.18f + 0.5f * fract(sin((seed * 13 + i) * 12.9898f) * 43758.547f)
            if (!cam.project(lerp(ax, bx, u0), top + 0.02f, lerp(az, bz, u0))) return
            poly[0] = cam.sx; poly[1] = cam.sy
            if (!cam.project(lerp(ax, bx, u1), top + 0.02f, lerp(az, bz, u1))) return
            poly[2] = cam.sx; poly[3] = cam.sy
            if (!cam.project(lerp(ax, bx, um), top - drop, lerp(az, bz, um))) return
            poly[4] = cam.sx; poly[5] = cam.sy
            gr.fillPolyGradient(poly, 3, poly[0], poly[1], poly[4], poly[5], light, dark)
        }
    }

    /**
     * A waterfall pouring off an island: a sheet of the design's falling water (scrolling down) turned to face the
     * camera, fading into mist as it drops into the clouds.
     */
    private fun waterfall(gr: Gfx, x: Float, y: Float, z: Float, w: Float, h: Float, a: Float, haze: Float) {
        val img = art.falls ?: return
        val rx = kotlin.math.cos(cam.yaw) * w * 0.5f; val rz = -kotlin.math.sin(cam.yaw) * w * 0.5f
        val tileH = w * img.h / img.w * 1.6f
        val scroll = fract(g.t * 1.4f) * tileH
        var yy = y
        var first = true
        var k = 0
        while (yy > y - h && k < 24) {
            val seg = if (first) scroll.coerceAtLeast(0.02f) else tileH
            val yb = max(y - h, yy - seg)
            val v0 = if (first) img.h * (1f - seg / tileH) else 0f
            val v1 = img.h * ((yy - yb) / tileH) + v0
            val fadeTop = 1f - smooth(((y - yy) / h - 0.35f) / 0.65f)
            if (!cam.project(x - rx, yy, z - rz)) return; q[0] = cam.sx; q[1] = cam.sy
            if (!cam.project(x + rx, yy, z + rz)) return; q[2] = cam.sx; q[3] = cam.sy
            if (!cam.project(x + rx * 1.15f, yb, z + rz * 1.15f)) return; q[4] = cam.sx; q[5] = cam.sy
            if (!cam.project(x - rx * 1.15f, yb, z - rz * 1.15f)) return; q[6] = cam.sx; q[7] = cam.sy
            gr.imageQuad(img, 0f, v0, img.w.toFloat(), min(img.h.toFloat(), v1), q, a * 0.92f * fadeTop, 1.05f, skyHaze, haze * 0.8f)
            yy = yb; first = false; k++
        }
        // the lip where it spills over, and mist where it falls away
        if (cam.project(x, y, z)) {
            val sc = cam.scaleAt(cam.depth)
            gr.setAdditive(true); gr.glow(cam.sx, cam.sy, w * 0.7f * sc, Col.withA(0xFFE8FAFF.toInt(), 0.35f * a)); gr.setAdditive(false)
        }
    }

    /** A palm tree: a curved ringed trunk and a crown of drooping fronds (as on the design's islands). */
    private fun drawPalm(gr: Gfx, x: Float, y: Float, z: Float, h: Float, lean: Float, a: Float, haze: Float) {
        val n = 6
        var px = 0f; var py = 0f
        val trunk = Col.withA(Col.mix(0xFF8A5A2E.toInt(), skyHaze, haze), a); val ring = Col.withA(Col.mix(0xFF5E3C1E.toInt(), skyHaze, haze), a)
        var topX = 0f; var topY = 0f; var sc = 1f
        for (i in 0..n) {
            val u = i / n.toFloat()
            if (!cam.project(x + lean * 0.55f * u * u, y + h * u, z)) return
            sc = cam.scaleAt(cam.depth)
            if (i > 0) {
                gr.line(px, py, cam.sx, cam.sy, max(1.2f, (0.16f - 0.06f * u) * sc), trunk)
                gr.line(px, py, cam.sx, cam.sy, max(0.6f, (0.05f) * sc), if (i % 2 == 0) ring else trunk)
            }
            px = cam.sx; py = cam.sy
        }
        topX = px; topY = py
        if (sc < 2f) return
        val leaf = Col.withA(Col.mix(0xFF2E8A2A.toInt(), skyHaze, haze), a); val leafHi = Col.withA(Col.mix(0xFF7ED23E.toInt(), skyHaze, haze), a)
        val sw = sin(g.t * 1.3f + x * 0.7f + z) * 0.08f
        for (k in 0 until 7) {
            val an = k * TAU / 7f + x * 1.3f + z * 0.7f
            val cxx = kotlin.math.cos(an); val czz = kotlin.math.sin(an)
            val tx = x + lean * 0.55f
            if (!cam.project(tx + cxx * 0.65f, y + h + 0.22f + sw, z + czz * 0.65f)) continue
            val mx = cam.sx; val my = cam.sy
            if (!cam.project(tx + cxx * 1.3f, y + h - 0.38f + sw, z + czz * 1.3f)) continue
            val ex = cam.sx; val ey = cam.sy
            val nx = -(my - topY); val ny = mx - topX
            val nl = len2(nx, ny).coerceAtLeast(1e-3f)
            val wv = 0.2f * sc
            poly[0] = topX; poly[1] = topY
            poly[2] = mx + nx / nl * wv; poly[3] = my + ny / nl * wv
            poly[4] = ex; poly[5] = ey
            poly[6] = mx - nx / nl * wv; poly[7] = my - ny / nl * wv
            gr.fillPolyGradient(poly, 4, topX, topY, ex, ey, leafHi, leaf)
        }
        gr.fillCircle(topX, topY, 0.1f * sc, Col.withA(Col.mix(0xFF6A4020.toInt(), skyHaze, haze), a))
    }

    // ------------------------------------------------------------------ Level 6: laser gates and spiked blocks
    /**
     * A laser gate: an iron emitter with a red lens on a stone post at each side of the path. Before it fires the lenses
     * flicker and a faint dotted line shows where the beam will run; then a red beam burns across at knee height.
     */
    private fun drawLaser(gr: Gfx, l: Laser) {
        val t = g.t
        val base = l.y - 1.35f
        for (side in 0..1) {
            val px = if (side == 0) l.x0 - 0.32f else l.x1 + 0.32f
            drawBox(gr, px - 0.2f, base, l.z - 0.2f, px + 0.2f, l.y - 0.2f, l.z + 0.2f, art.pillarColor, 1, 1f, 0f, null)
            drawBox(gr, px - 0.3f, l.y - 0.22f, l.z - 0.3f, px + 0.3f, l.y + 0.3f, l.z + 0.3f, BC.IRON, 0, 1f, 0f, null)
            if (cam.project(px + (if (side == 0) 0.31f else -0.31f), l.y + 0.04f, l.z)) {
                val sc = cam.scaleAt(cam.depth)
                val k = 0.3f + 0.5f * l.warn * (0.6f + 0.4f * sin(t * 40f)) + 0.6f * l.beam
                gr.fillCircle(cam.sx, cam.sy, 0.11f * sc, 0xFF5A0A10.toInt())
                gr.setAdditive(true); gr.glow(cam.sx, cam.sy, 0.35f * sc * (0.7f + k), Col.withA(0xFFFF3A2A.toInt(), min(1f, k))); gr.setAdditive(false)
            }
        }
        if (!cam.project(l.x0, l.y + 0.04f, l.z)) return
        val ax = cam.sx; val ay = cam.sy; val sa = cam.scaleAt(cam.depth)
        if (!cam.project(l.x1, l.y + 0.04f, l.z)) return
        val bx = cam.sx; val by = cam.sy; val sb = cam.scaleAt(cam.depth)
        val sc = (sa + sb) * 0.5f
        if (l.beam > 0.01f) {
            val fl = l.beam * (0.85f + 0.15f * sin(t * 57f))
            gr.setAdditive(true)
            gr.line(ax, ay, bx, by, max(3f, 0.34f * sc), Col.withA(0xFFFF2A1A.toInt(), 0.4f * fl))
            gr.line(ax, ay, bx, by, max(2f, 0.13f * sc), Col.withA(0xFFFF6A4A.toInt(), 0.9f * fl))
            gr.line(ax, ay, bx, by, max(1f, 0.045f * sc), Col.withA(Col.WHITE, fl))
            gr.setAdditive(false)
        } else if (l.warn > 0.01f) {
            val on = ((t * 18f).toInt() and 1) == 0
            if (on) for (i in 0 until 10) {
                val u0 = i / 10f + 0.02f; val u1 = u0 + 0.05f
                gr.line(lerp(ax, bx, u0), lerp(ay, by, u0), lerp(ax, bx, u1), lerp(ay, by, u1), max(1f, 0.04f * sc), Col.withA(0xFFFF5A4A.toInt(), 0.35f + 0.5f * l.warn))
            }
        }
    }

    /**
     * A spiked block: dark iron studded with red-tipped spikes (as in the design). A sliding one hangs on a short
     * chain from an iron rail overhead that spans the path between two stone posts.
     */
    private fun drawSpikeBox(gr: Gfx, b: SpikeBox) {
        val h = b.size * 0.5f
        if (b.amp > 0f) {
            val ry = b.y + 3.5f
            val rl = b.x0 - b.amp - 1.1f; val rr = b.x0 + b.amp + 1.1f
            for (px in floatArrayOf(rl, rr)) drawBox(gr, px - 0.16f, b.y - 1.2f, b.z - 0.16f, px + 0.16f, ry + 0.14f, b.z + 0.16f, if (g.spec.enchanted) art.pillarColor else BC.SAND, 2, 1f, 0f, null)
            drawBox(gr, rl, ry, b.z - 0.05f, rr, ry + 0.1f, b.z + 0.05f, BC.STONE, 0, 1f, 0f, null)
            if (cam.project(b.x, ry, b.z)) {
                val x0s = cam.sx; val y0s = cam.sy
                if (cam.project(b.x, b.y + b.size, b.z)) gr.line(x0s, y0s, cam.sx, cam.sy, max(1.5f, 0.06f * cam.scaleAt(cam.depth)), 0xFF2A2630.toInt())
            }
        }
        if (!drawBox(gr, b.x - h, b.y, b.z - h, b.x + h, b.y + b.size, b.z + h, BC.IRON, 1, 1f, 0f, null)) return
        // spikes on the faces the camera sees (2 x 2 each)
        val cy0 = b.y + h
        for (i in 0..1) for (j in 0..1) {
            val u = (i - 0.5f) * h; val v = (j - 0.5f) * h
            if (cam.ey > b.y + b.size) spike(gr, b.x + u, b.y + b.size, b.z + v, 0f, 1f, 0f)
            if (cam.ez < b.z - h) spike(gr, b.x + u, cy0 + v, b.z - h, 0f, 0f, -1f)
            if (cam.ex < b.x - h) spike(gr, b.x - h, cy0 + v, b.z + u, -1f, 0f, 0f)
            if (cam.ex > b.x + h) spike(gr, b.x + h, cy0 + v, b.z + u, 1f, 0f, 0f)
        }
    }

    private fun spike(gr: Gfx, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
        val len = 0.3f; val r = 0.1f
        if (!cam.project(x + nx * len, y + ny * len, z + nz * len)) return
        val tx = cam.sx; val ty = cam.sy
        // a base across the face (perpendicular to the normal)
        val ax = if (ny != 0f) 1f else if (nx != 0f) 0f else 1f; val az = if (nx != 0f) 1f else 0f; val ay = if (ny != 0f) 0f else if (nz != 0f) 0f else 0f
        if (!cam.project(x - ax * r, y - ay * r, z - az * r)) return
        poly[0] = cam.sx; poly[1] = cam.sy
        if (!cam.project(x + ax * r, y + ay * r, z + az * r)) return
        poly[2] = cam.sx; poly[3] = cam.sy
        poly[4] = tx; poly[5] = ty
        gr.fillPolyGradient(poly, 3, (poly[0] + poly[2]) * 0.5f, (poly[1] + poly[3]) * 0.5f, tx, ty, 0xFF3A3A4A.toInt(), 0xFFE8402A.toInt())
    }

    // ------------------------------------------------------------------ Level 6: the helicopter
    /**
     * The yellow helicopter from the design, cruising round the sky temple in the distance (it starts where the design
     * shows it). Its main and tail rotors spin (the blurred discs and a sweeping blade).
     */
    private fun drawHeli(gr: Gfx) {
        val img = art.heli ?: return
        val t = g.t
        val s = jbS
        // the design's placement: the body at (244, 203), 164 x 116, the rotor hub at (347, 207)
        val dx = sin(t * 0.11f) * 70f + sin(t * 0.37f) * 8f
        val dy = sin(t * 0.9f) * 5f + sin(t * 0.07f) * 18f
        val bx = jbLeft + (244f + dx) * s; val by = jbTop + (203f + dy) * s
        val w = 164f * s; val h = 116f * s
        val tilt = sin(t * 0.11f + 1.2f) * 3f
        gr.save(); gr.translate(bx + w * 0.5f, by + h * 0.5f); gr.rotate(tilt); gr.translate(-(bx + w * 0.5f), -(by + h * 0.5f))
        gr.image(img, bx, by, w, h)
        // main rotor: a faint disc and two blades sweeping round
        val hx = bx + (347f - 244f) * s; val hy = by + (207f - 203f) * s
        val rr = 96f * s; val ry = 9f * s
        var m = 0
        for (i in 0 until 20) { val an = i / 20f * TAU; poly[m * 2] = hx + kotlin.math.cos(an) * rr; poly[m * 2 + 1] = hy + kotlin.math.sin(an) * ry; m++ }
        gr.fillPoly(poly, m, 0x2A30302E)
        val sp = t * 38f
        for (k in 0..1) {
            val an = sp + k * Math.PI.toFloat()
            val ex = hx + kotlin.math.cos(an) * rr; val ey = hy + kotlin.math.sin(an) * ry
            gr.line(hx, hy, ex, ey, max(1.5f, 3.5f * s), 0x9A2A2A2E.toInt())
            val an2 = an - 0.35f
            gr.line(hx, hy, hx + kotlin.math.cos(an2) * rr, hy + kotlin.math.sin(an2) * ry, max(1f, 2.5f * s), 0x442A2A2E)
        }
        gr.fillCircle(hx, hy, 3.5f * s, 0xFF2A2A30.toInt())
        // tail rotor
        val tx = bx + (253f - 244f) * s; val ty = by + (228f - 203f) * s
        gr.fillCircle(tx, ty, 14f * s, 0x302A2A2E)
        val ta = t * 44f
        gr.line(tx - kotlin.math.cos(ta) * 14f * s, ty - kotlin.math.sin(ta) * 14f * s, tx + kotlin.math.cos(ta) * 14f * s, ty + kotlin.math.sin(ta) * 14f * s, max(1f, 2.5f * s), 0x9A2A2A2E.toInt())
        gr.restore()
    }

    // ------------------------------------------------------------------ Level 7: the enchanted sky realm
    private val SPIRE_ORDER = intArrayOf(2, 3, 1, 0)
    private val SPIRE_CX = floatArrayOf(-1f, 1f, 1f, -1f); private val SPIRE_CZ = floatArrayOf(-1f, -1f, 1f, 1f)
    private val SPIRE_SHADE = floatArrayOf(0.85f, 1f, 0.7f, 0.6f)
    private val CRYSTAL_COLS = intArrayOf(0xFFB04CF0.toInt(), 0xFFF070D8.toInt(), 0xFF6AA8FF.toInt(), 0xFFD890FF.toInt())
    private val CRYSTAL_SHARDS = floatArrayOf(-0.32f, 0.1f, 0.9f, -8f, 0.02f, 0f, 1.35f, 4f, 0.3f, -0.1f, 0.8f, 16f, -0.1f, 0.25f, 0.6f, -20f)
    private val BLOSSOM_PINKS = intArrayOf(0xFFE04AB8.toInt(), 0xFFF27AD2.toInt(), 0xFFB838C8.toInt(), 0xFFFFA8E4.toInt())

    /**
     * The sky above the course, riding on the plate where the Level 7 design shows it: the purple-and-gold balloon
     * drifting, and the hooded Sorcerer looming on the left with his minions about him (his eyes glow; he raises his
     * claw with a flash of purple lightning when he sends a wave down). His imps fly down to the course while a wave is
     * on and come back after; once the last wave is over he withdraws into the mist.
     */
    private fun drawSkyLayer(gr: Gfx) {
        val s = jbS; val L = jbLeft; val T = jbTop; val t = g.t
        val mn = g.minions
        val so = g.sorcery
        // the story in the sky's light: his magic darkens it (the warning, the Wrath), the gate's light warms it at the end
        if (so.gloom > 0.01f) {
            gr.fillRect(0f, 0f, gr.width.toFloat(), gr.height.toFloat(), Col.withA(0xFF1A0630.toInt(), 0.36f * so.gloom))
            gr.setAdditive(true)
            gr.fillRectGradient(0f, 0f, gr.width.toFloat(), gr.height * 0.6f, Col.withA(0xFF8020A0.toInt(), 0.16f * so.gloom * (0.7f + 0.3f * pulse(t, 1.3f))), 0x00801AA0)
            gr.setAdditive(false)
        }
        if (so.gold > 0.01f) {
            gr.setAdditive(true)
            gr.fillRectGradient(0f, 0f, gr.width.toFloat(), gr.height * 0.55f, Col.withA(0xFFFFD890.toInt(), 0.2f * so.gold), 0x00FFD890)
            gr.setAdditive(false)
        }
        // the balloon, drifting slowly
        art.balloon?.let { img ->
            val dx = sin(t * 0.13f) * 18f + sin(t * 0.41f) * 3f; val dy = sin(t * 0.7f) * 5f
            gr.image(img, L + (157f + dx) * s, T + (554f + dy) * s, 96f * s, 114f * s)
            gr.setAdditive(true); gr.glow(L + (206f + dx) * s, T + (648f + dy) * s, 18f * s, Col.withA(0xFFFFC860.toInt(), 0.5f + 0.2f * pulse(t, 5f))); gr.setAdditive(false)
        }
        val away = mn.retreat
        val show = 1f - smooth(away)
        if (show > 0.01f) {
            art.sorcerer?.let { img ->
                val bob = sin(t * 1.1f) * 5f
                val rise = -away * 60f
                // in his Wrath he swells over the course, nearer and bigger; a star rune's strike makes him reel
                val rg = so.rage
                val sc = 1f + 0.015f * sin(t * 0.9f) - 0.15f * away + 0.24f * rg
                val jolt = sin(t * 55f) * 7f * so.flinch
                val bx = L + (186f + 172f * (1f - sc) + 34f * rg + jolt) * s; val by = T + (262f + bob + rise + 169f * (1f - sc) + 26f * rg - 20f * so.flinch) * s
                val w = 344f * s * sc; val h = 338f * s * sc
                val cast = max(mn.cast, so.charge)
                gr.setAdditive(true)
                gr.glow(L + (330f + 34f * rg) * s, T + (420f + bob + rise + 26f * rg) * s, (230f + 90f * rg) * s, Col.withA(Col.mix(0xFF9040E0.toInt(), 0xFFD02080.toInt(), rg), show * (0.22f + 0.1f * pulse(t, 1.5f) + 0.35f * cast + 0.15f * rg)))
                gr.setAdditive(false)
                gr.image(img, bx, by, w, h, show)
                if (so.flinch > 0.01f) { gr.setAdditive(true); gr.glow(bx + w * 0.45f, by + h * 0.45f, w * 0.6f, Col.withA(0xFFFFF4C0.toInt(), 0.6f * so.flinch)); gr.setAdditive(false) }
                // his eyes, and the purple lightning crackling round his claw (a burst of it when he casts)
                gr.setAdditive(true)
                val ea = show * (0.55f + 0.25f * pulse(t, 2.3f) + 0.6f * cast + 0.3f * rg) * (1f - 0.7f * so.flinch)
                val eyeC = Col.mix(0xFFE8A0FF.toInt(), 0xFFFF5AA0.toInt(), rg)
                val ex0 = bx + (274f - 186f) * s * sc; val ey0 = by + (395f - 262f) * s * sc
                val ex1 = bx + (315f - 186f) * s * sc; val ey1 = by + (400f - 262f) * s * sc
                gr.glow(ex0, ey0, 16f * s * (1f + 0.5f * rg), Col.withA(eyeC, ea)); gr.glow(ex1, ey1, 15f * s * (1f + 0.5f * rg), Col.withA(eyeC, ea))
                val hx = bx + (455f - 186f) * s * sc; val hy = by + (455f - 262f) * s * sc
                gr.glow(hx, hy, 70f * s, Col.withA(0xFFB060FF.toInt(), show * (0.18f + 0.5f * cast)))
                val zap = cast > 0.05f || fract(t * 0.45f) < 0.07f || (rg > 0.5f && fract(t * 1.3f) < 0.18f)
                if (zap) for (k in 0 until (if (cast > 0.05f) 4 else 2)) lightning(gr, hx, hy, hx + (sin(t * 3f + k * 2.1f) * 90f + 40f) * s, hy + (60f + 50f * k) * s, 8f * s, show * (0.5f + 0.5f * cast), k + (t * 20f).toInt())
                gr.setAdditive(false)
            }
        }
        // his minions about him in the sky (they fly down to the course while a wave is on)
        val fa = (1f - mn.skyAway) * show
        if (fa > 0.01f) {
            skyImp(gr, art.minionShip, 58f, 394f, 112f, 152f, 0f, fa, 0xFFFFD040.toInt())
            skyImp(gr, art.minionHat2, 0f, 400f, 77f, 110f, 1.3f, fa, 0xFFE070FF.toInt())
            skyImp(gr, art.minionHat, 310f, 501f, 105f, 112f, 2.2f, fa, 0xFFFFA030.toInt())
            skyImp(gr, art.minionOrb, 286f, 620f, 93f, 110f, 3.1f, fa, 0xFFFFC040.toInt())
        }
    }

    private fun skyImp(gr: Gfx, img: Img?, x: Float, y: Float, w: Float, h: Float, ph: Float, a: Float, eye: Int) {
        img ?: return
        val s = jbS; val t = g.t
        val dx = sin(t * 0.8f + ph) * 6f; val dy = sin(t * 1.7f + ph * 1.3f) * 7f - (1f - a) * 40f
        gr.image(img, jbLeft + (x + dx) * s, jbTop + (y + dy) * s, w * s, h * s, a)
        gr.setAdditive(true); gr.glow(jbLeft + (x + w * 0.5f + dx) * s, jbTop + (y + h * 0.55f + dy) * s, w * 0.45f * s, Col.withA(eye, 0.18f * a * (0.7f + 0.3f * pulse(t + ph, 3f)))); gr.setAdditive(false)
    }

    /** A jagged bolt of purple lightning between two screen points. */
    private fun lightning(gr: Gfx, x0: Float, y0: Float, x1: Float, y1: Float, width: Float, a: Float, seed: Int) {
        var px = x0; var py = y0
        val n = 6
        for (i in 1..n) {
            val u = i / n.toFloat()
            val j = if (i == n) 0f else (fract(sin((seed * 31 + i) * 12.9898f) * 43758.547f) - 0.5f) * 0.35f
            val nx = lerp(x0, x1, u) + (y1 - y0) * j; val ny = lerp(y0, y1, u) - (x1 - x0) * j
            gr.line(px, py, nx, ny, width, Col.withA(0xFFB070FF.toInt(), a * 0.5f))
            gr.line(px, py, nx, ny, width * 0.35f, Col.withA(0xFFF4E0FF.toInt(), a))
            px = nx; py = ny
        }
    }

    /**
     * One of the Sorcerer's minions on the course: the design's imp standing in the world facing the camera. It drops
     * out of a purple flash when it appears, hops as it patrols, glows before a swoop, flares its eyes before a lunge,
     * is squashed flat by a stomp and vanishes in a puff of smoke.
     */
    private fun drawMinion(gr: Gfx, m: Minion) {
        val img = when (m.kind) {
            MK.SWOOP -> art.minionOrb
            MK.BOMBER -> art.minionShip
            MK.CHASER -> art.minionHat2
            else -> if (m.look == 1) art.minionHat2 else art.minionHat
        } ?: return
        val t = g.t
        val worldH = when (m.kind) { MK.SWOOP -> 1.12f; MK.BOMBER -> 2.0f; MK.CHASER -> 1.3f; else -> 1.35f }
        val onGround = m.kind == MK.PATROL
        // patrollers stand on the path; the fliers are drawn round their middle
        val baseY = if (onGround) m.y else m.y - worldH * 0.45f
        if (onGround && m.state != MS.APPEAR) {
            val gy = m.spot?.y ?: m.y
            drawShadow(gr, m.x, m.z, gy, m.show * 0.8f, 0.34f, 0.24f)
        }
        // swooper: the path of its next pass, dotted and glowing, before it swoops
        if (m.kind == MK.SWOOP && m.warn > 0.01f && m.state == MS.ACTIVE) swoopWarning(gr, m)
        if (!cam.project(m.x, baseY, m.z)) return
        val sc = cam.scaleAt(cam.depth)
        var a = m.show * nearFade(cam.depth)
        if (a <= 0.01f) return
        var h = worldH * sc
        var sy = 1f
        if (m.state == MS.STOMPED) sy = max(0.15f, 1f - m.t * 5f)
        if (m.state == MS.APPEAR && onGround) { val u = clamp01(m.t / 0.6f); sy = 0.7f + 0.3f * easeOutBack(u) }
        val w = h * img.w / img.h
        val bx = cam.sx; val by = cam.sy
        val lunging = m.state == MS.LUNGE
        val wind = lunging && m.t < MinionSystem.WINDUP
        // the purple aura, stronger when it is about to strike
        gr.setAdditive(true)
        val eye = when (m.kind) { MK.CHASER -> 0xFFE070FF.toInt(); MK.SWOOP, MK.BOMBER -> 0xFFFFC040.toInt(); else -> if (m.look == 1) 0xFFE070FF.toInt() else 0xFFFFA030.toInt() }
        gr.glow(bx, by - h * 0.5f, w * 0.8f, Col.withA(0xFFA050F0.toInt(), a * (0.2f + (if (wind) 0.4f * pulse(t, 14f) else 0f) + 0.35f * m.warn)))
        if (m.state == MS.APPEAR) gr.glow(bx, by - h * 0.5f, w * (1.4f - clamp01(m.t / 0.6f)), Col.withA(0xFFE0A0FF.toInt(), a * 0.7f * (1f - clamp01(m.t / 0.6f))))
        gr.setAdditive(false)
        gr.save(); gr.translate(bx, by)
        val tilt = when {
            m.kind == MK.SWOOP && m.crossing -> m.face * 14f
            m.kind == MK.CHASER -> m.face * 8f + sin(t * 4f + m.side) * 4f
            m.kind == MK.BOMBER -> sin(t * 1.3f + m.side) * 5f
            else -> sin(t * 7f + (m.spot?.phase ?: 0f)) * 5f
        }
        gr.rotate(tilt)
        gr.scale(if (m.face < 0f) -1f else 1f, sy)
        gr.image(img, -w * 0.5f, -h, w, h, a)
        gr.restore()
        // its eyes blaze before it strikes
        if (wind || m.warn > 0.3f || (m.kind == MK.BOMBER && m.cool < 0.3f)) {
            gr.setAdditive(true)
            gr.glow(bx, by - h * 0.45f * sy, w * 0.32f, Col.withA(eye, a * (0.5f + 0.5f * pulse(t, 12f))))
            gr.setAdditive(false)
            if (wind) star4(gr, bx, by - h * 1.12f, w * 0.14f * (0.7f + 0.3f * pulse(t, 10f)), Col.withA(0xFFFFE070.toInt(), a))
        }
        // a puff of smoke as it vanishes
        if (m.state == MS.VANISH) { gr.setAdditive(true); gr.glow(bx, by - h * 0.5f, w * (0.6f + m.t), Col.withA(0xFFC8A0F0.toInt(), 0.5f * (1f - clamp01(m.t / 0.5f)))); gr.setAdditive(false) }
    }

    /** The arc a swooper will fly across the path, shown dotted before it goes. */
    private fun swoopWarning(gr: Gfx, m: Minion) {
        val sp = m.spot ?: return
        val on = ((g.t * 16f).toInt() and 1) == 0
        if (!on) return
        var px = 0f; var py = 0f; var ok = false
        for (i in 0..16) {
            val u = i / 16f
            val x = lerp(sp.x - sp.amp - 0.6f, sp.x + sp.amp + 0.6f, u)
            val k = 2f * u - 1f
            val y = sp.y + 0.5f + 1.25f * k * k + 0.35f
            if (!cam.project(x, y, sp.z + 0.5f)) { ok = false; continue }
            if (ok && i % 2 == 0) gr.line(px, py, cam.sx, cam.sy, max(1.5f, 0.05f * cam.scaleAt(cam.depth)), Col.withA(0xFFE0A0FF.toInt(), 0.35f + 0.5f * m.warn))
            px = cam.sx; py = cam.sy; ok = true
        }
    }

    /** A magic bolt dropped by a lantern ship: a glowing orb in flight, a ring on the path where it will land. */
    private fun drawBolt(gr: Gfx, b: Bolt) {
        if (!b.landed) {
            var m = 0
            val u = clamp01(b.t / MinionSystem.BOLT_FLIGHT)
            for (i in 0 until 16) {
                val an = i / 16f * TAU
                if (!cam.project(b.tx + cos(an) * 0.55f, b.ground + 0.03f, b.tz + sin(an) * 0.55f)) { m = 0; break }
                poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
            }
            if (m > 0) {
                val pa = 0.3f + 0.5f * u
                gr.fillPoly(poly, m, Col.withA(0xFFB040F0.toInt(), 0.18f + 0.3f * u * (0.7f + 0.3f * pulse(g.t, 10f))))
                gr.strokePoly(poly, m, 3.5f * g.hud.s, Col.withA(0xFFF0C8FF.toInt(), pa))
            }
        }
        if (!cam.project(b.x, b.y, b.z)) return
        val sc = cam.scaleAt(cam.depth)
        val a = smooth((cam.depth - 1.2f) / 1.5f)
        if (a <= 0.01f) return
        gr.setAdditive(true)
        val r = (if (b.landed) 0.9f + b.t * 4f else 0.32f) * sc
        gr.glow(cam.sx, cam.sy, r * 2.2f, Col.withA(0xFFA040FF.toInt(), 0.6f * a * (if (b.landed) 1f - b.t / 0.2f else 1f)))
        gr.glow(cam.sx, cam.sy, r, Col.withA(0xFFF8E8FF.toInt(), a * (if (b.landed) 1f - b.t / 0.2f else 1f)))
        gr.setAdditive(false)
    }

    // ------------------------------------------------------------------ Level 7: the Sorcerer's creatures and spells
    private val vp = VPath()

    /** A ring on the ground round (x, z) at height y, into [poly]; the number of points, or 0 when it is off-screen. */
    private fun groundRing(x: Float, y: Float, z: Float, rx: Float, rz: Float, n: Int = 18, spin: Float = 0f): Int {
        var m = 0
        for (i in 0 until n) {
            val a = spin + i / n.toFloat() * TAU
            if (!cam.project(x + cos(a) * rx, y, z + sin(a) * rz)) return 0
            poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
        }
        return m
    }

    /**
     * A rolling stone creature: a ball of moonstone with glowing rune marks turning over as it rolls at the boy, and two
     * angry violet eyes glowing through the stone at its front.
     */
    private fun drawRoller(gr: Gfx, r: Roller) {
        val R = Sorcery.ROLL_R
        if (r.state == 2 || r.state == 3) drawShadow(gr, r.x, r.z, r.y, r.show, R * 1.0f, R * 0.75f)
        if (!cam.project(r.x, r.y + R, r.z)) return
        val sc = cam.scaleAt(cam.depth)
        val a = r.show * nearFade(cam.depth)
        if (a <= 0.01f) return
        val rad = R * sc * (if (r.state == 3) 1f + (1f - r.show) * 0.5f else 1f)
        val cx = cam.sx; val cy = cam.sy
        val t = g.t
        gr.setAdditive(true)
        gr.glow(cx, cy, rad * 1.8f, Col.withA(0xFFB060FF.toInt(), a * 0.3f))
        gr.setAdditive(false)
        gr.fillCircle(cx, cy, rad * 1.05f, Col.withA(0xFF221A30.toInt(), a))
        vp.ops.clear(); vp.native = null; vp.circle(cx, cy, rad)
        gr.fillPath(vp, Radial(cx - rad * 0.35f, cy - rad * 0.45f, rad * 1.6f,
            intArrayOf(0xFFEAE0F6.toInt(), 0xFFAE9EC4.toInt(), 0xFF655879.toInt(), 0xFF2E2640.toInt()), floatArrayOf(0f, 0.3f, 0.72f, 1f)), a)
        // the rune marks on its stone, turning over toward the camera as it rolls
        gr.setAdditive(true)
        for (k in 0 until 6) {
            val th = r.rot + k * TAU / 6f
            val front = sin(th)
            if (front <= 0.05f) continue
            val mx = cx + (if (k % 2 == 0) -0.42f else 0.4f) * rad * (0.6f + 0.4f * front)
            val my = cy - cos(th) * rad * 0.88f
            star4(gr, mx, my, rad * 0.16f * front, Col.withA(0xFFE0A8FF.toInt(), a * front))
        }
        gr.setAdditive(false)
        // the cracks of its face: two brows and the glowing eyes
        val ey = cy + rad * 0.02f
        gr.line(cx - rad * 0.52f, ey - rad * 0.28f, cx - rad * 0.12f, ey - rad * 0.14f, rad * 0.12f, Col.withA(0xFF1C1428.toInt(), a))
        gr.line(cx + rad * 0.52f, ey - rad * 0.28f, cx + rad * 0.12f, ey - rad * 0.14f, rad * 0.12f, Col.withA(0xFF1C1428.toInt(), a))
        gr.setAdditive(true)
        val blaze = 0.7f + 0.3f * pulse(t, 9f)
        gr.glow(cx - rad * 0.3f, ey, rad * 0.32f, Col.withA(0xFFFF70E0.toInt(), a * blaze))
        gr.glow(cx + rad * 0.3f, ey, rad * 0.32f, Col.withA(0xFFFF70E0.toInt(), a * blaze))
        gr.setAdditive(false)
        gr.fillCircle(cx - rad * 0.3f, ey, rad * 0.1f, Col.withA(0xFFFFE8FF.toInt(), a))
        gr.fillCircle(cx + rad * 0.3f, ey, rad * 0.1f, Col.withA(0xFFFFE8FF.toInt(), a))
        // a rim light from the violet sky
        gr.arc(cx, cy, rad * 0.94f, 200f, 110f, rad * 0.08f, Col.withA(0xFFF4E8FF.toInt(), a * 0.6f))
    }

    /**
     * The teleporting spirit: its mark glows on the path first (a violet ring with turning runes), then it forms there,
     * a pale ghost with a wispy tail and glowing eyes, and a streak of light shows where it blinked from.
     */
    private fun drawSpirit(gr: Gfx, sp: Spirit) {
        val t = g.t
        if (sp.state in 1..4) {
            val mk = when (sp.state) { 1 -> 0.35f + 0.65f * clamp01(sp.t / Sorcery.SPIRIT_MARK); 4 -> 1f - clamp01(sp.t / Sorcery.SPIRIT_OUT); else -> 1f }
            val m = groundRing(sp.x, sp.y + 0.03f, sp.z, 0.52f, 0.52f, 20, t * 1.5f)
            if (m > 0) {
                val flick = 0.75f + 0.25f * pulse(t, 12f)
                gr.fillPoly(poly, m, Col.withA(0xFF7A40D0.toInt(), 0.28f * mk * flick))
                gr.strokePoly(poly, m, 3.5f * g.hud.s, Col.withA(0xFFC8F0FF.toInt(), 0.9f * mk))
                gr.setAdditive(true)
                for (k in 0 until 5) {
                    val an = t * 2f + k * TAU / 5f
                    if (cam.project(sp.x + cos(an) * 0.36f, sp.y + 0.04f, sp.z + sin(an) * 0.36f)) star4(gr, cam.sx, cam.sy, 0.07f * cam.scaleAt(cam.depth), Col.withA(0xFFB8F4FF.toInt(), mk))
                }
                gr.setAdditive(false)
            }
        }
        if (sp.state < 2 || sp.show <= 0.01f) return
        if (!cam.project(sp.x, sp.y + 1.05f, sp.z)) return
        val sc = cam.scaleAt(cam.depth)
        val a = sp.show * nearFade(cam.depth)
        if (a <= 0.01f) return
        val cx = cam.sx + sin(t * 3f) * 0.04f * sc; val cy = cam.sy + sin(t * 2.3f) * 0.05f * sc
        val hr = 0.3f * sc
        // the blink: a streak of light from where it was
        if (sp.state == 2 && cam.project(sp.fx, sp.fy + 1.05f, sp.fz)) {
            gr.setAdditive(true)
            gr.line(cam.sx, cam.sy, cx, cy, hr * 0.5f, Col.withA(0xFF9FE8FF.toInt(), 0.6f * (1f - sp.show)))
            gr.setAdditive(false)
        }
        gr.setAdditive(true)
        gr.glow(cx, cy + hr * 0.6f, hr * 3f, Col.withA(0xFF8FD8FF.toInt(), 0.35f * a))
        gr.setAdditive(false)
        // body: a rounded head flowing into a tail that curls and waves
        vp.ops.clear(); vp.native = null
        val wave = sin(t * 5f) * hr * 0.35f
        vp.moveTo(cx - hr, cy)
        vp.cubicTo(cx - hr, cy - hr * 1.35f, cx + hr, cy - hr * 1.35f, cx + hr, cy)
        vp.cubicTo(cx + hr * 0.95f, cy + hr * 1.2f, cx + hr * 0.4f + wave, cy + hr * 2.2f, cx + wave * 1.6f, cy + hr * 3.1f)
        vp.cubicTo(cx - hr * 0.1f + wave, cy + hr * 2.2f, cx - hr * 0.9f, cy + hr * 1.3f, cx - hr, cy)
        vp.close()
        gr.fillPath(vp, Linear(cx, cy - hr, cx, cy + hr * 3.1f, intArrayOf(0xF0F2FAFF.toInt(), 0xC8B8D8FF.toInt(), 0x008A70FF)), a)
        // little arms trailing wisps
        gr.line(cx - hr * 0.9f, cy + hr * 0.4f, cx - hr * 1.5f, cy + hr * (0.2f + 0.2f * sin(t * 6f)), hr * 0.28f, Col.withA(0xFFDDE8FF.toInt(), a * 0.8f))
        gr.line(cx + hr * 0.9f, cy + hr * 0.4f, cx + hr * 1.5f, cy + hr * (0.2f - 0.2f * sin(t * 6f)), hr * 0.28f, Col.withA(0xFFDDE8FF.toInt(), a * 0.8f))
        // hollow eyes with a cold glow
        gr.fillCircle(cx - hr * 0.36f, cy - hr * 0.15f, hr * 0.2f, Col.withA(0xFF261A48.toInt(), a))
        gr.fillCircle(cx + hr * 0.36f, cy - hr * 0.15f, hr * 0.2f, Col.withA(0xFF261A48.toInt(), a))
        gr.setAdditive(true)
        gr.glow(cx - hr * 0.36f, cy - hr * 0.15f, hr * 0.28f, Col.withA(0xFF60E8FF.toInt(), a * (0.7f + 0.3f * pulse(t, 7f))))
        gr.glow(cx + hr * 0.36f, cy - hr * 0.15f, hr * 0.28f, Col.withA(0xFF60E8FF.toInt(), a * (0.7f + 0.3f * pulse(t, 7f))))
        gr.setAdditive(false)
    }

    /**
     * A magical hand: a dark violet arm reaching out of the island's edge and a clawed hand slamming down on the outer
     * lane; its glow at the edge (and the lane it will slam, outlined) is the warning.
     */
    private fun drawHand(gr: Gfx, h: Hand) {
        val t = g.t
        val side = h.spot.x
        if (h.warn > 0.01f) {
            if (cam.project(h.ex, h.y - 0.1f, h.z)) {
                val sc = cam.scaleAt(cam.depth)
                gr.setAdditive(true)
                gr.glow(cam.sx, cam.sy, 0.9f * sc * (0.6f + 0.4f * h.warn), Col.withA(0xFFB050FF.toInt(), 0.55f * h.warn * (0.7f + 0.3f * pulse(t, 14f))))
                gr.setAdditive(false)
            }
            val m = groundRing(h.x, h.y + 0.03f, h.z, 0.46f, 0.5f, 4, TAU / 8f)
            if (m > 0) gr.strokePoly(poly, m, 3f * g.hud.s, Col.withA(0xFFE070FF.toInt(), 0.8f * h.warn * (if (((t * 10f).toInt() and 1) == 0) 1f else 0.5f)))
        }
        val u = h.reach
        if (u <= 0.01f) return
        // the wrist travels in an arc from under the island's edge up and over onto the lane
        val wx = lerp(h.ex, h.x + side * 0.18f, u)
        val wy = lerp(h.y - 0.5f, h.y + 0.12f, u) + 1.1f * 4f * u * (1f - u)
        if (!cam.project(h.ex, h.y - 0.9f, h.z)) return
        val bx = cam.sx; val by = cam.sy
        if (!cam.project(wx, wy, h.z)) return
        val sc = cam.scaleAt(cam.depth)
        val a = nearFade(cam.depth)
        if (a <= 0.01f) return
        val hx = cam.sx; val hy = cam.sy
        val arm = 0.26f * sc
        gr.setAdditive(true)
        gr.glow(hx, hy, 0.9f * sc, Col.withA(0xFFA040F0.toInt(), 0.4f * a))
        gr.setAdditive(false)
        // the arm
        gr.line(bx, by, hx, hy, arm * 1.25f, Col.withA(0xFF9050E0.toInt(), 0.5f * a))
        gr.line(bx, by, hx, hy, arm, Col.withA(0xFF22123A.toInt(), a))
        // the hand: a palm and four clawed fingers spread toward the path's middle (down onto the lane when slammed)
        val dir = -side
        val down = smooth(u)
        val palm = 0.3f * sc
        gr.fillCircle(hx, hy, palm, Col.withA(0xFF2A1646.toInt(), a))
        for (k in 0 until 4) {
            val spread = (k - 1.5f) * 0.32f
            val ang = lerp(-1.2f, 0.25f, down) + spread
            val fx0 = hx + dir * cos(ang) * palm * 0.6f; val fy0 = hy + sin(ang) * palm * 0.6f
            val fx1 = hx + dir * cos(ang) * palm * 2.1f; val fy1 = hy + sin(ang) * palm * 2.1f + palm * 0.4f * down
            gr.line(fx0, fy0, fx1, fy1, palm * 0.34f, Col.withA(0xFF2A1646.toInt(), a))
            gr.setAdditive(true)
            gr.glow(fx1, fy1, palm * 0.3f, Col.withA(0xFFE080FF.toInt(), a * 0.8f))
            gr.setAdditive(false)
        }
        // the thumb
        gr.line(hx, hy, hx - dir * palm * 0.9f, hy - palm * 1.1f * (1f - down), palm * 0.36f, Col.withA(0xFF2A1646.toInt(), a))
        gr.strokeCircle(hx, hy, palm, max(1f, palm * 0.12f), Col.withA(0xFFB070FF.toInt(), a * 0.7f))
        // the slam: a ring of violet light on the lane
        if (h.danger) {
            val m = groundRing(h.x, h.y + 0.03f, h.z, 0.55f, 0.6f, 16)
            if (m > 0) { gr.setAdditive(true); gr.fillPoly(poly, m, Col.withA(0xFFB040F0.toInt(), 0.35f * a)); gr.setAdditive(false) }
        }
    }

    /** A shockwave: a crest of magenta light across the path, rolling down it toward the boy, with a fading wake. */
    private fun drawShock(gr: Gfx, sh: Shock) {
        val t = g.t
        for (layer in 0..1) {
            val z = sh.z + layer * 0.55f
            val hgt = (if (layer == 0) 0.55f else 0.3f) * (0.9f + 0.1f * sin(t * 30f))
            var m = 0
            val n = 9
            for (i in 0..n) {
                val x = sh.x - 1.75f + 3.5f * i / n
                if (!cam.project(x, sh.y + 0.02f, z)) return
                poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
            }
            for (i in n downTo 0) {
                val x = sh.x - 1.75f + 3.5f * i / n
                val bump = 0.12f * sin(i * 1.7f + t * 18f)
                if (!cam.project(x, sh.y + hgt + bump, z)) return
                poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
            }
            val a = sh.show * (if (layer == 0) 1f else 0.45f) * nearFade(cam.depth + 1f)
            val topY = poly[(n + 1) * 2 + 1]; val botY = poly[1]
            gr.setAdditive(true)
            gr.fillPolyGradient(poly, m, 0f, topY, 0f, botY, Col.withA(0xFFFF60D0.toInt(), 0.15f * a), Col.withA(0xFFFFA0F0.toInt(), 0.75f * a))
            gr.setAdditive(false)
            if (layer == 0) {
                // the bright crest
                for (i in n + 1 until 2 * n + 1) gr.line(poly[i * 2], poly[i * 2 + 1], poly[(i + 1) * 2], poly[(i + 1) * 2 + 1], 3.5f * g.hud.s, Col.withA(0xFFFFF0FF.toInt(), 0.9f * a))
            }
        }
    }

    /** A crystal shard falling onto its marked spot (the ring on the path), then stuck in the ground, fading. */
    private fun drawShard(gr: Gfx, sh: Shard) {
        val t = g.t
        if (!sh.landed) {
            val u = clamp01(sh.t / Sorcery.SHARD_FALL)
            val m = groundRing(sh.x, sh.ground + 0.03f, sh.z, 0.62f, 0.62f, 18)
            if (m > 0) {
                val col = if (sh.harmless) 0xFFB8A0E8.toInt() else 0xFF8FE0FF.toInt()
                gr.fillPoly(poly, m, Col.withA(0xFF6040C0.toInt(), (0.12f + 0.28f * u) * (0.7f + 0.3f * pulse(t, 12f))))
                gr.strokePoly(poly, m, 3.5f * g.hud.s, Col.withA(col, 0.4f + 0.5f * u))
            }
            // a thread of light from the sky marks it
            if (cam.project(sh.x, sh.ground, sh.z)) {
                val gx = cam.sx; val gy = cam.sy
                if (cam.project(sh.x, sh.y, sh.z)) {
                    gr.setAdditive(true)
                    gr.line(gx, gy, cam.sx, cam.sy, 2.5f * g.hud.s, Col.withA(0xFFB8E8FF.toInt(), 0.25f + 0.3f * u))
                    gr.setAdditive(false)
                }
            }
        }
        if (!cam.project(sh.x, sh.y + 0.45f, sh.z)) return
        val sc = cam.scaleAt(cam.depth)
        val fade = if (sh.landed) 1f - clamp01(sh.t / 0.7f) else 1f
        val a = fade * nearFade(cam.depth)
        if (a <= 0.01f) return
        val cx = cam.sx; val cy = cam.sy
        val hw = 0.2f * sc; val hh = 0.55f * sc
        val tilt = if (sh.landed) 0.25f else 0f
        poly[0] = cx + tilt * hh; poly[1] = cy - hh
        poly[2] = cx + hw; poly[3] = cy - hh * 0.1f
        poly[4] = cx - tilt * hh * 0.3f; poly[5] = cy + hh
        poly[6] = cx - hw; poly[7] = cy - hh * 0.1f
        gr.setAdditive(true)
        gr.glow(cx, cy, hh * 1.3f, Col.withA(0xFF9FD8FF.toInt(), 0.45f * a))
        gr.setAdditive(false)
        gr.fillPolyGradient(poly, 4, cx - hw, cy - hh, cx + hw, cy + hh, Col.withA(0xFFF0FCFF.toInt(), a), Col.withA(0xFF8A70E8.toInt(), a))
        gr.line(poly[0], poly[1], poly[4], poly[5], max(1f, hw * 0.18f), Col.withA(Col.WHITE, 0.7f * a))
    }

    /** A treasure chest: wood with golden bands and a lock; its lid thrown open (golden light pouring out) once touched. */
    private fun drawChest(gr: Gfx, c: Chest) {
        val t = g.t
        drawShadow(gr, c.x, c.z, c.y, 0.9f, 0.46f, 0.32f)
        if (!cam.project(c.x, c.y, c.z)) return
        val sc = cam.scaleAt(cam.depth)
        val a = nearFade(cam.depth)
        if (a <= 0.01f) return
        val cx = cam.sx; val by = cam.sy
        val w = 0.82f * sc; val bh = 0.46f * sc; val lh = 0.26f * sc
        val top = by - bh
        val open = if (c.open) easeOutBack(clamp01(c.openT / 0.35f)) else 0f
        gr.setAdditive(true)
        gr.glow(cx, top, w * (0.9f + 0.9f * open), Col.withA(0xFFFFD060.toInt(), a * (0.25f + 0.1f * pulse(t, 3f) + 0.5f * open * (1f - clamp01((c.openT - 1.5f) / 2f)))))
        gr.setAdditive(false)
        // the lid, open: tipped back behind the box
        if (open > 0.01f) {
            val lt = top - lh * (1f - open) - lh * 1.4f * open
            gr.fillRoundRect(cx - w * 0.5f, lt, cx + w * 0.5f, top, w * 0.12f, Col.withA(0xFF6A3A18.toInt(), a))
            gr.fillRect(cx - w * 0.5f, top - lh * 0.25f, cx + w * 0.5f, top, Col.withA(0xFFFFE8A0.toInt(), a))
        }
        // the box
        gr.fillRoundRect(cx - w * 0.5f, top, cx + w * 0.5f, by, w * 0.06f, Col.withA(0xFF8A4E22.toInt(), a))
        gr.fillRectGradient(cx - w * 0.5f, top, cx + w * 0.5f, by, Col.withA(0xFFB06A30.toInt(), a * 0.6f), Col.withA(0xFF4A2410.toInt(), a * 0.6f))
        for (k in 0..1) { val bx = cx + (if (k == 0) -0.3f else 0.3f) * w; gr.fillRect(bx - w * 0.06f, top, bx + w * 0.06f, by, Col.withA(0xFFF0C040.toInt(), a)) }
        gr.strokeRoundRect(cx - w * 0.5f, top, cx + w * 0.5f, by, w * 0.06f, max(1f, w * 0.03f), Col.withA(0xFFFFD860.toInt(), a))
        // the closed lid, rounded, and the lock
        if (open <= 0.01f) {
            gr.fillRoundRect(cx - w * 0.52f, top - lh, cx + w * 0.52f, top + lh * 0.15f, w * 0.2f, Col.withA(0xFF7A4420.toInt(), a))
            for (k in 0..1) { val bx = cx + (if (k == 0) -0.3f else 0.3f) * w; gr.fillRect(bx - w * 0.06f, top - lh, bx + w * 0.06f, top + lh * 0.15f, Col.withA(0xFFF0C040.toInt(), a)) }
            gr.fillRoundRect(cx - w * 0.09f, top - lh * 0.2f, cx + w * 0.09f, top + bh * 0.3f, w * 0.03f, Col.withA(0xFFFFD040.toInt(), a))
            val ph = fract(t * 0.6f + c.z * 0.1f)
            if (ph < 0.2f) star4(gr, cx + w * 0.3f, top - lh * 0.6f, w * 0.18f * sin(ph / 0.2f * Math.PI.toFloat()), Col.withA(Col.WHITE, a))
        } else {
            // coins glinting inside
            for (k in 0 until 4) star4(gr, cx + (k - 1.5f) * w * 0.2f, top - lh * 0.1f + sin(t * 4f + k) * 2f, w * 0.07f, Col.withA(0xFFFFF0A0.toInt(), a))
        }
    }

    /** A five-pointed star on the ground at (x, y, z), into [poly]; the number of points, or 0 when off-screen. */
    private fun groundStar(x: Float, y: Float, z: Float, r0: Float, r1: Float, spin: Float): Int {
        var m = 0
        for (i in 0 until 10) {
            val an = spin + i * TAU / 10f
            val rr = if (i % 2 == 0) r0 else r1
            if (!cam.project(x + cos(an) * rr, y, z + sin(an) * rr)) return 0
            poly[m * 2] = cam.sx; poly[m * 2 + 1] = cam.sy; m++
        }
        return m
    }

    /** A star rune set into the path (the Wrath): violet and pulsing until lit; golden, with a pillar of light striking the sky, once lit. */
    private fun drawRune(gr: Gfx, r: Rune) {
        val t = g.t
        val lit = r.lit
        val col = if (lit) 0xFFFFE070.toInt() else 0xFFC890FF.toInt()
        val pul = if (lit) 1f else 0.6f + 0.4f * pulse(t, 5f)
        val rm = groundRing(r.x, r.y + 0.02f, r.z, 0.47f, 0.47f, 20)
        if (rm > 0) gr.strokePoly(poly, rm, 3f * g.hud.s, Col.withA(col, 0.9f * pul))
        val m = groundStar(r.x, r.y + 0.02f, r.z, 0.4f, 0.17f, -Math.PI.toFloat() / 2f)
        if (m == 0) return
        gr.setAdditive(true)
        gr.fillPoly(poly, m, Col.withA(col, (if (lit) 0.7f else 0.45f) * pul))
        gr.setAdditive(false)
        gr.strokePoly(poly, m, 2f * g.hud.s, Col.withA(Col.WHITE, 0.7f * pul))
        if (!cam.project(r.x, r.y + 0.05f, r.z)) return
        val sc = cam.scaleAt(cam.depth)
        val gx = cam.sx; val gy = cam.sy
        gr.setAdditive(true)
        gr.glow(gx, gy, 0.8f * sc, Col.withA(col, 0.35f * pul))
        if (lit && r.litT < 3f) {
            // the pillar of starlight that strikes the Sorcerer
            val k = 1f - clamp01((r.litT - 1.2f) / 1.8f)
            val bw = 0.28f * sc * (1f + 0.15f * sin(t * 20f))
            gr.fillRectGradient(gx - bw, -20f, gx + bw, gy, Col.withA(0xFFFFF4C0.toInt(), 0.2f * k), Col.withA(0xFFFFE070.toInt(), 0.75f * k))
            gr.fillRectGradient(gx - bw * 0.35f, -20f, gx + bw * 0.35f, gy, Col.withA(Col.WHITE, 0.3f * k), Col.withA(Col.WHITE, 0.95f * k))
        }
        gr.setAdditive(false)
    }

    /** A ring of starlight (a sanctuary from the Sorcerer's spells): a golden ring on the plaza with motes of light rising. */
    private fun drawSanctuary(gr: Gfx, sa: Sanctuary) {
        val t = g.t
        val so = g.sorcery
        val k = if (so.wrath == WS.DONE) 0.35f else if (so.wrath == WS.NONE && !so.warned) 0.5f else 1f
        val zc = (sa.z0 + sa.z1) * 0.5f
        val rz = (sa.z1 - sa.z0) * 0.5f
        val m = groundRing(sa.x, sa.y + 0.04f, zc, 2.35f, rz, 32)
        if (m > 0) {
            gr.setAdditive(true)
            gr.fillPoly(poly, m, Col.withA(0xFFFFD880.toInt(), 0.1f * k))
            gr.setAdditive(false)
            gr.strokePoly(poly, m, 3f * g.hud.s, Col.withA(0xFFFFE8A0.toInt(), (0.45f + 0.2f * pulse(t, 2f)) * k))
        }
        gr.setAdditive(true)
        for (i in 0 until 8) {
            val ph = fract(t * 0.3f + i * 0.125f)
            val an = i * 2.4f
            if (!cam.project(sa.x + cos(an) * 2f, sa.y + 0.2f + ph * 2.6f, zc + sin(an) * rz * 0.8f)) continue
            star4(gr, cam.sx, cam.sy, 0.08f * cam.scaleAt(cam.depth) * sin(ph * Math.PI.toFloat()), Col.withA(0xFFFFF0B0.toInt(), 0.8f * k))
        }
        gr.setAdditive(false)
    }

    /** A secret route's switch: a cyan rune on a slab beside the path, pulsing with little sparks rising off it until found. */
    private fun drawSecretSwitch(gr: Gfx, se: Secret) {
        val t = g.t
        val col = if (se.found) 0xFFFFE070.toInt() else 0xFF7FE8FF.toInt()
        val pul = if (se.found) 0.5f else 0.55f + 0.45f * pulse(t, 4f)
        val m = groundStar(se.x, se.y + 0.02f, se.z, 0.36f, 0.15f, t * 0.4f)
        if (m == 0) return
        gr.setAdditive(true)
        gr.fillPoly(poly, m, Col.withA(col, 0.55f * pul))
        gr.setAdditive(false)
        gr.strokePoly(poly, m, 2f * g.hud.s, Col.withA(Col.WHITE, 0.6f * pul))
        if (se.found || !cam.project(se.x, se.y + 0.05f, se.z)) return
        val sc = cam.scaleAt(cam.depth)
        gr.setAdditive(true)
        gr.glow(cam.sx, cam.sy, 0.7f * sc, Col.withA(col, 0.4f * pul))
        for (i in 0 until 3) {
            val ph = fract(t * 0.7f + i / 3f)
            star4(gr, cam.sx + sin(i * 2.1f + t) * 0.25f * sc, cam.sy - ph * 1.1f * sc, 0.06f * sc * (1f - ph), Col.withA(0xFFDFFBFF.toInt(), 1f - ph))
        }
        gr.setAdditive(false)
    }

    /** A gem floating over the path (the design's purple gems), turning and glinting. */
    private fun drawGemPickup(gr: Gfx, gp: GemPickup) {
        val img = art.gemBig ?: art.gem
        val bob = sin(gp.phase * 2.4f + gp.oz) * 0.08f
        if (!cam.project(gp.x, gp.y + bob, gp.z)) return
        val sc = cam.scaleAt(cam.depth)
        val hgt = 0.72f * sc
        if (hgt < 2f) return
        val a = (1f - smooth((cam.depth - (maxDepth - 10f)) / 10f)) * smooth((cam.depth - 2.2f) / 1.6f)
        if (a <= 0.01f) return
        val spin = cos(g.t * 2.2f + gp.oz * 0.7f)
        val wid = hgt * img.w / img.h * max(0.35f, abs(spin))
        gr.setAdditive(true)
        gr.glow(cam.sx, cam.sy, hgt * 1.2f, Col.withA(0xFFD060FF.toInt(), 0.55f * a))
        gr.setAdditive(false)
        gr.image(img, cam.sx - wid * 0.5f, cam.sy - hgt * 0.5f, wid, hgt, a)
        val ph = fract(g.t * 0.9f + gp.oz * 0.13f)
        if (ph < 0.25f) star4(gr, cam.sx + wid * 0.25f, cam.sy - hgt * 0.3f, hgt * 0.22f * sin(ph / 0.25f * Math.PI.toFloat()), Col.withA(Col.WHITE, a))
    }

    /** An iron chain slung between two points, sagging in the middle. */
    private fun drawChain(gr: Gfx, c: ChainLink) {
        var px = 0f; var py = 0f; var ok = false
        val n = 14
        for (i in 0..n) {
            val u = i / n.toFloat()
            val x = lerp(c.x0, c.x1, u); val z = lerp(c.z0, c.z1, u); val y = lerp(c.y0, c.y1, u) - c.sag * 4f * u * (1f - u)
            if (!cam.project(x, y, z)) { ok = false; continue }
            val sc = cam.scaleAt(cam.depth)
            val a = nearFade(cam.depth)
            if (ok && a > 0.01f) {
                gr.line(px, py, cam.sx, cam.sy, max(1.5f, 0.09f * sc), Col.withA(0xFF1E1A26.toInt(), a))
                if (i % 2 == 0) gr.strokeCircle(cam.sx, cam.sy, max(1f, 0.07f * sc), max(1f, 0.03f * sc), Col.withA(0xFF6A6478.toInt(), a))
            }
            px = cam.sx; py = cam.sy; ok = true
        }
    }

    /**
     * A fantasy tower of the design: a round-ish moonstone turret with a gold band, a pointed purple witch-hat roof with a
     * gold tip, a glowing window and a pennant.
     */
    private fun drawSpire(gr: Gfx, d: Deco) {
        val x = d.x; val base = d.y; val z = d.z; val r = d.w * 0.5f
        val h = d.w * 2.0f
        // it stands on its own little floating rock beside the course, moss on top
        val tex = art.pillarSide
        if (tex != null) {
            val d0 = cam.depthOf(x, base - 1f, z)
            val ra = nearFade(d0 + 0.5f); val rh = hazeFor(d0)
            if (ra > 0.01f) {
                val rw = d.w * 1.9f
                drawBox(gr, x - rw * 0.5f, base - 0.45f, z - rw * 0.5f, x + rw * 0.5f, base, z + rw * 0.5f, art.pillarColor, 1, ra, 0f, null)
                tier(gr, tex, art.texSize.toFloat(), x, z, rw, rw, base - 0.45f, x + 0.1f, z, rw * 0.12f, rw * 0.12f, base - 2.8f, ra, rh, 0.6f, 0.4f)
                mossEdge(gr, x - rw * 0.5f, z - rw * 0.5f, x + rw * 0.5f, z - rw * 0.5f, base, (z * 7f).toInt(), ra, rh)
            }
        }
        if (!drawBox(gr, x - r, base, z - r, x + r, base + h, z + r, art.pillarColor, 2, 1f, 0f, null)) return
        drawBox(gr, x - r - 0.08f, base + h - 0.05f, z - r - 0.08f, x + r + 0.08f, base + h + 0.14f, z + r + 0.08f, BC.GOLD, 0, 1f, 0f, null)
        // the roof: a cone of four faces (purple, shaded), lit from the upper left
        val top = base + h + 0.14f; val tipY = top + d.w * 1.9f
        val rr = r + 0.18f
        if (!cam.project(x, tipY, z)) return
        val tx = cam.sx; val ty = cam.sy
        val a = nearFade(cam.depth)
        if (a <= 0.01f) return
        val haze = hazeFor(cam.depth)
        // faces sorted back to front (the camera looks along +z: the -z face is nearest)
        for (fi in SPIRE_ORDER) {
            val c0x = SPIRE_CX[fi] * rr; val c0z = SPIRE_CZ[fi] * rr; val c1x = SPIRE_CX[(fi + 1) % 4] * rr; val c1z = SPIRE_CZ[(fi + 1) % 4] * rr
            val nx = (c0x + c1x) * 0.5f; val nz = (c0z + c1z) * 0.5f
            if ((cam.ex - (x + nx)) * nx + (cam.ez - (z + nz)) * nz < 0f) continue
            if (!cam.project(x + c0x, top, z + c0z)) return
            poly[0] = cam.sx; poly[1] = cam.sy
            if (!cam.project(x + c1x, top, z + c1z)) return
            poly[2] = cam.sx; poly[3] = cam.sy
            poly[4] = tx; poly[5] = ty
            val col = Col.mix(Col.scale(0xFF6A2CB8.toInt(), SPIRE_SHADE[fi]), skyHaze, haze)
            gr.fillPolyGradient(poly, 3, tx, ty, (poly[0] + poly[2]) * 0.5f, (poly[1] + poly[3]) * 0.5f, Col.withA(Col.mix(col, 0xFFB080F0.toInt(), 0.25f), a), Col.withA(col, a))
        }
        val sc = cam.scaleAt(cam.depth)
        gr.fillCircle(tx, ty, max(1f, 0.07f * sc), Col.withA(0xFFFFD040.toInt(), a))
        // the pennant on the tip, fluttering
        val t = g.t + d.z
        val fl = sin(t * 5f) * 0.08f
        if (cam.project(x + 0.55f, tipY - 0.05f + fl, z)) {
            poly[0] = tx; poly[1] = ty; poly[2] = cam.sx; poly[3] = cam.sy
            if (cam.project(x, tipY - 0.3f, z)) { poly[4] = cam.sx; poly[5] = cam.sy; gr.fillPoly(poly, 3, Col.withA(Col.mix(0xFFE03A6A.toInt(), skyHaze, haze), a)) }
        }
        // a glowing window on the side facing the camera
        if (cam.project(x, base + h * 0.62f, z - r - 0.01f)) {
            val wsc = cam.scaleAt(cam.depth)
            gr.fillRoundRect(cam.sx - 0.1f * wsc, cam.sy - 0.2f * wsc, cam.sx + 0.1f * wsc, cam.sy + 0.12f * wsc, 0.1f * wsc, Col.withA(0xFFFFC860.toInt(), a))
            gr.setAdditive(true); gr.glow(cam.sx, cam.sy, 0.35f * wsc, Col.withA(0xFFFFB040.toInt(), 0.4f * a)); gr.setAdditive(false)
        }
    }

    /** A cluster of glowing magic crystals (violet, pink and blue) standing on an island. */
    private fun drawCrystal(gr: Gfx, d: Deco) {
        val x = d.x; val y = d.y; val z = d.z; val s0 = d.w
        if (!cam.project(x, y + 0.6f * s0, z)) return
        val a = nearFade(cam.depth)
        if (a <= 0.01f) return
        val sc = cam.scaleAt(cam.depth)
        if (sc * s0 < 3f) return
        val haze = hazeFor(cam.depth)
        val t = g.t + z
        gr.setAdditive(true)
        gr.glow(cam.sx, cam.sy, 1.1f * s0 * sc, Col.withA(0xFFC060FF.toInt(), a * (0.3f + 0.12f * pulse(t, 2f))))
        gr.setAdditive(false)
        for (k in 0 until 4) {
            val ox = CRYSTAL_SHARDS[k * 4] * s0; val oz = CRYSTAL_SHARDS[k * 4 + 1] * s0; val hh = CRYSTAL_SHARDS[k * 4 + 2] * s0; val lean = CRYSTAL_SHARDS[k * 4 + 3]
            if (!cam.project(x + ox, y, z + oz)) continue
            val bx = cam.sx; val by = cam.sy
            val w = 0.16f * s0 * cam.scaleAt(cam.depth)
            if (!cam.project(x + ox + lean * 0.01f * hh, y + hh, z + oz)) continue
            val tx = cam.sx; val ty = cam.sy
            val c = Col.mix(CRYSTAL_COLS[k], skyHaze, haze * 0.7f)
            poly[0] = bx - w; poly[1] = by; poly[2] = bx - w * 0.9f; poly[3] = lerp(by, ty, 0.7f); poly[4] = tx; poly[5] = ty
            poly[6] = bx + w * 0.9f; poly[7] = lerp(by, ty, 0.7f); poly[8] = bx + w; poly[9] = by
            gr.fillPolyGradient(poly, 5, tx, ty, bx, by, Col.withA(Col.mix(c, Col.WHITE, 0.55f), a), Col.withA(c, a))
            gr.line(tx, ty, bx - w * 0.2f, by, max(1f, w * 0.25f), Col.withA(Col.WHITE, 0.45f * a))
        }
        val ph = fract(t * 0.7f)
        if (ph < 0.3f) star4(gr, cam.sx + 0.2f * s0 * sc, cam.sy - 0.5f * s0 * sc, 0.25f * s0 * sc * sin(ph / 0.3f * Math.PI.toFloat()), Col.withA(Col.WHITE, a))
    }

    /** A blossom tree on an island (the design's magenta-flowered trees): a curved trunk and a round, shaded pink crown. */
    private fun drawBlossom(gr: Gfx, x: Float, y: Float, z: Float, h: Float, lean: Float, a: Float, haze: Float, seed: Int) {
        val trunk = Col.withA(Col.mix(0xFF6A3E2A.toInt(), skyHaze, haze), a)
        var px = 0f; var py = 0f; var sc = 1f
        for (i in 0..5) {
            val u = i / 5f
            if (!cam.project(x + lean * 0.35f * u * u, y + h * u, z)) return
            sc = cam.scaleAt(cam.depth)
            if (i > 0) gr.line(px, py, cam.sx, cam.sy, max(1.2f, (0.17f - 0.07f * u) * sc), trunk)
            px = cam.sx; py = cam.sy
        }
        if (sc < 2f) return
        val cx = x + lean * 0.35f; val cy = y + h + 0.1f
        // three layers of blossom: deep magenta underneath, pink, then pale pink highlights on the lit upper left
        for (layer in 0..2) {
            val n = if (layer == 0) 7 else if (layer == 1) 6 else 4
            val base = BLOSSOM_PINKS[if (layer == 0) 2 else if (layer == 1) 0 else 3]
            for (k in 0 until n) {
                val an = k * TAU / n + seed * 0.7f + layer * 0.5f
                val rad = if (layer == 0) 0.6f else if (layer == 1) 0.42f else 0.24f
                val ox = cos(an) * rad - (if (layer == 2) 0.15f else 0f)
                val oy = sin(an) * rad * 0.55f + (if (layer == 0) -0.1f else if (layer == 1) 0.12f else 0.3f)
                if (!cam.project(cx + ox, cy + oy, z - layer * 0.12f)) continue
                val rr = (if (layer == 0) 0.36f else if (layer == 1) 0.32f else 0.2f) * (0.85f + 0.3f * fract(k * 0.61f + seed * 0.13f)) * cam.scaleAt(cam.depth)
                gr.fillCircle(cam.sx, cam.sy, rr, Col.withA(Col.mix(base, skyHaze, haze), a))
            }
        }
    }
}
