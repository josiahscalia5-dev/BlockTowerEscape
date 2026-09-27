package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The level map (PLAY on the Home screen opens it): a winding trail of chunky blocks climbing through the
 * sky world, one big block per level, Level 1 at the bottom. Completed levels show their stars, locked ones a
 * padlock, and the boy stands on the level you are up to. Tap a level to see its card and play it.
 */
class LevelMap(private val app: App) {
    private val slots = 10
    private var scroll = 0f
    private var vel = 0f
    private var dragging = false
    private var downY = 0f; private var lastY = 0f; private var lastT = 0f
    private var pressedNode = -1
    private var shakeNode = 0; private var shakeT = 0f
    private var newT = 0f
    /** The unlock animation of a newly opened level: seconds since the map finished fading in. */
    private var unlockAge = -1f
    private var unlockBurst = false
    /** "Finish Level N to unlock" after tapping a locked level. */
    private var lockedMsg = ""
    private var lockedMsgT = 0f
    private var lockedNode = 0
    private val poly = FloatArray(16)
    private val q = FloatArray(8)
    // where each level's block was drawn this frame (the labels are drawn after the boy)
    private val lx = FloatArray(slots + 1); private val ly = FloatArray(slots + 1); private val lsize = FloatArray(slots + 1)
    private val lopen = BooleanArray(slots + 1); private val lpop = FloatArray(slots + 1)
    /** The sky world behind the trail: the sky tower plate, softened (design/map/make_map_bg.py). */
    private val bg by lazy { app.pf.loadImage("img/map_bg.jpg") }

    private val s get() = app.s
    private val spacing get() = 260f * s
    private val bottomPad get() = 300f * s + app.bottomInset
    private fun nodeY(i: Int) = app.h - bottomPad - (i - 1) * spacing + scroll
    private fun nodeX(i: Int) = app.w * 0.5f + app.w * 0.24f * sin((i - 1) * 1.15f)
    private val maxScroll get() = max(0f, (slots - 1) * spacing + bottomPad + 420f * s + app.topInset - app.h)

    /** Scrolls so that level [n] sits a little below the middle of the screen. */
    fun focusOn(n: Int) {
        scroll = 0f
        val y = nodeY(n)
        scroll = clamp(app.h * 0.58f - y, 0f, maxScroll)
        vel = 0f
        if (app.justUnlocked > 0) { newT = 4.5f; unlockAge = 0f; unlockBurst = false }
        lockedMsgT = 0f
    }

    fun update(dt: Float) {
        if (!dragging) {
            scroll += vel * dt
            vel *= kotlin.math.exp(-4f * dt)
            if (scroll < 0f) { scroll = lerp(scroll, 0f, damp(12f, dt)); vel = 0f }
            if (scroll > maxScroll) { scroll = lerp(scroll, maxScroll, damp(12f, dt)); vel = 0f }
        }
        if (shakeT > 0f) shakeT -= dt
        if (lockedMsgT > 0f) lockedMsgT -= dt
        // the unlock plays once the map is fully on screen
        if (unlockAge >= 0f && !app.fading) {
            unlockAge += dt
            val n = app.justUnlocked
            if (!unlockBurst && unlockAge >= UNLOCK_POP && n > 0) {
                unlockBurst = true
                app.burst(nodeX(n), nodeY(n), 36, 0xFFFFE070.toInt(), confetti = true)
                app.pf.sound(Sfx.CRUMBLE, 0.5f, 1.4f); app.pf.sound(Sfx.STAR, 0.9f); app.pf.haptic(false)
            }
            if (unlockAge > 6f) unlockAge = -1f
        }
        if (newT > 0f && (unlockAge < 0f || unlockAge > UNLOCK_POP)) { newT -= dt; if (newT <= 0f) app.justUnlocked = 0 }
    }

    // ------------------------------------------------------------------ drawing
    fun render(gr: Gfx, interactive: Boolean) {
        val w = app.w; val h = app.h
        val art = app.baseArt
        val t = app.t
        // the sky world behind, a little larger than the screen, drifting slowly with the scroll
        val k = max(w / bg.w, h / bg.h) * 1.12f
        val par = clamp(scroll / max(1f, maxScroll), 0f, 1f)
        val left = (w - bg.w * k) * 0.5f
        val topY = (h - bg.h * k) * (1f - par)
        gr.fillRect(0f, 0f, w, h, 0xFF1C5FC8.toInt())
        gr.image(bg, left, topY, bg.w * k, bg.h * k)
        gr.fillRectGradient(0f, 0f, w, h, 0x110A1440, 0x440A1440)
        // the trail of stepping stones between levels
        val pr = app.progress
        val current = app.currentLevel()
        for (i in 1 until slots) {
            val x0 = nodeX(i); val y0 = nodeY(i); val x1 = nodeX(i + 1); val y1 = nodeY(i + 1)
            if (max(y0, y1) < -100f * s || min(y0, y1) > h + 100f * s) continue
            val open = i + 1 <= pr.unlocked && i + 1 <= Levels.count
            for (j in 1..3) {
                val u = j / 4f
                val cx = lerp(x0, x1, u) + sin(u * 3.14159f) * 40f * s * (if (i % 2 == 0) 1f else -1f)
                val cy = lerp(y0, y1, u)
                val col = if (open) intArrayOf(BC.GREEN, BC.YELLOW, BC.RED, BC.PURPLE)[(i + j) % 4] else BC.STONE
                cube(gr, cx, cy, 30f * s, col, (i + j) % 3, if (open) 1f else 0.7f)
            }
        }
        // level blocks, top to bottom so nearer (lower) ones overlap
        for (i in slots downTo 1) {
            val cx = nodeX(i) + (if (i == shakeNode && shakeT > 0f) sin(shakeT * 50f) * 10f * s * shakeT / 0.4f else 0f)
            val cy = nodeY(i)
            if (cy < -200f * s || cy > h + 200f * s) continue
            val exists = i <= Levels.count
            val unlocked = exists && i <= pr.unlocked
            val isCurrent = i == current && unlocked
            // a level that has just opened: still locked, the padlock shakes, then pops off and the block colours in
            val unlocking = i == app.justUnlocked && unlockAge >= 0f
            val popU = if (unlocking) clamp01((unlockAge - UNLOCK_POP) / 0.6f) else 1f
            val shownUnlocked = unlocked && (!unlocking || unlockAge >= UNLOCK_POP)
            val size = 78f * s * (if (pressedNode == i) 0.94f else 1f) * (1f + (if (unlocking && popU < 1f) 0.18f * sin(popU * 3.14159f) else 0f))
            if (isCurrent && shownUnlocked) {
                gr.setAdditive(true)
                gr.glow(cx, cy, size * 2.4f, Col.withA(0xFFFFE070.toInt(), 0.25f + 0.2f * pulse(t, 3f)))
                gr.setAdditive(false)
            }
            val col = when (i) { 1 -> BC.GREEN; 2 -> BC.YELLOW; 3 -> BC.PURPLE; 4 -> BC.RED; 5 -> BC.GOLD; else -> BC.BLUE }
            if (shownUnlocked) {
                cube(gr, cx, cy, size, col, i % 3, 1f)
                if (popU < 1f) cube(gr, cx, cy, size, BC.STONE, i % 3, 0.75f, 1f - popU)
            } else cube(gr, cx, cy, size, BC.STONE, i % 3, 0.75f)
            if (unlocking && popU > 0f && popU < 1f) {
                // a ring of light spreads from the block
                gr.strokeCircle(cx, cy, size * (1.1f + 1.6f * popU), 8f * s * (1f - popU), Col.withA(0xFFFFE070.toInt(), 1f - popU))
            }
            if (shownUnlocked) {
                gr.text(i.toString(), cx, cy + size * 0.28f, size * 1.0f, Font.TITLE, Col.WHITE, Align.CENTER, size * 0.1f, 0xFF1A0A20.toInt(), popU)
                // stars earned
                val st = pr.stars[i]
                for (j in 0..2) app.ui.star(gr, cx + (j - 1) * size * 0.62f, cy + size * 1.32f - (if (j == 1) size * 0.1f else 0f), size * 0.27f, j < st)
            } else {
                val shake = if (unlocking) sin(unlockAge * 40f) * 7f * s * clamp01(unlockAge / UNLOCK_POP) else 0f
                app.ui.padlock(gr, cx + shake, cy + size * 0.25f, size * 0.42f)
            }
            lx[i] = cx; ly[i] = cy; lsize[i] = size; lopen[i] = shownUnlocked; lpop[i] = popU
            if (unlocking && popU > 0f && popU < 1f) {
                // the padlock flies off
                val py = cy + size * 0.25f - popU * 150f * s
                app.ui.padlock(gr, cx + popU * 40f * s, py, size * 0.42f * (1f - popU))
            }
            if (interactive) app.ui.hit(100 + i, cx - size * 1.3f, cy - size * 1.6f, cx + size * 1.3f, cy + size * 1.6f)
        }
        // the boy stands on the level you are up to; after an unlock he waits on the level he finished,
        // then hops across once the new level's padlock has popped
        run {
            val n = app.justUnlocked
            val hopU = if (n > 1 && unlockAge >= 0f) clamp01((unlockAge - UNLOCK_POP - 0.25f) / 0.55f) else 1f
            val from = if (hopU < 1f) n - 1 else current
            val to = if (hopU < 1f) n else current
            val e = smooth(hopU)
            val cx = lerp(nodeX(from), nodeX(to), e); val cy = lerp(nodeY(from), nodeY(to), e)
            val size = 78f * s
            val img = art.boy
            val bh = size * 2.3f; val bw = bh * img.w / img.h
            val hop = if (hopU > 0f && hopU < 1f) sin(hopU * 3.14159f) * 90f * s else abs(sin(t * 3f)) * 12f * s
            gr.fillCircle(cx, cy - size * 0.55f, size * 0.45f, 0x33000010)
            gr.image(img, cx - bw * 0.5f, cy - size * 0.62f - bh - hop, bw, bh)
        }
        // labels last, so the boy never hides one: level names, what opens the next level, NEW!, the locked message
        for (i in slots downTo 1) {
            if (nodeY(i) < -200f * s || nodeY(i) > h + 200f * s) continue      // (as the blocks above: not drawn)
            val cx = lx[i]; val cy = ly[i]; val size = lsize[i]
            val exists = i <= Levels.count
            val shownUnlocked = lopen[i]; val popU = lpop[i]
            if (shownUnlocked) {
                gr.text(Levels.get(i).name, cx, cy + size * 1.78f, 26f * s, Font.TITLE, Col.WHITE, Align.CENTER, 4f * s, 0xFF10205A.toInt())
            } else {
                gr.text(if (exists) "LEVEL $i" else "SOON", cx, cy + size * 1.35f, 26f * s, Font.TITLE, 0xFFD0D8EC.toInt(), Align.CENTER, 4f * s, 0xFF10205A.toInt())
                if (exists && i == pr.unlocked + 1)
                    gr.text("Finish Level ${i - 1}", cx, cy + size * 1.72f, 22f * s, Font.UI, 0xFFFFE9A8.toInt(), Align.CENTER, 3f * s, 0xFF10205A.toInt())
            }
            if (i == app.justUnlocked && newT > 0f && shownUnlocked) {
                val a = clamp01(newT / 0.5f) * clamp01(popU * 2f)
                val bob = sin(t * 6f) * 6f * s
                gr.text("NEW!", cx + size * 1.05f, cy - size * 0.9f + bob, 40f * s, Font.TITLE, Col.withA(0xFFFFE14A.toInt(), a), Align.CENTER, 6f * s, Col.withA(0xFF1A0A20.toInt(), a))
            }
            if (i == lockedNode && lockedMsgT > 0f) {
                val a = clamp01(lockedMsgT / 0.4f)
                val tw = gr.textWidth(lockedMsg, 28f * s, Font.UI) + 36f * s
                val my = cy - size * 1.35f
                gr.fillRoundRect(cx - tw / 2, my - 26f * s, cx + tw / 2, my + 26f * s, 22f * s, Col.withA(0xFF0A1438.toInt(), 0.92f * a))
                gr.strokeRoundRect(cx - tw / 2, my - 26f * s, cx + tw / 2, my + 26f * s, 22f * s, 2.5f * s, Col.withA(0xFFFFE14A.toInt(), a))
                gr.text(lockedMsg, cx, my, 28f * s, Font.UI, Col.withA(Col.WHITE, a), Align.CENTER)
            }
        }
        // header: a dark band the trail scrolls under, fading out below the wallet
        val top = app.topInset
        val band = top + 175f * s
        gr.fillRectGradient(0f, 0f, w, band, 0xF20A1438.toInt(), 0xD80A1438.toInt())
        gr.fillRectGradient(0f, band, w, band + 110f * s, 0xD80A1438.toInt(), 0x000A1438)
        app.ui.backButton(gr, 1, 70f * s, top + 70f * s)
        app.ui.title(gr, "SELECT LEVEL", w * 0.5f, top + 70f * s, 64f)
        app.ui.star(gr, w * 0.5f - 60f * s, top + 140f * s, 24f * s, true)
        gr.text("${pr.totalStars} / ${Levels.count * 3}", w * 0.5f - 25f * s, top + 140f * s, 34f * s, Font.TITLE, Col.WHITE, Align.LEFT, 4f * s, 0xFF10205A.toInt())
        app.ui.wallet(gr, w - 20f * s, top + 205f * s, pr.coins, pr.gems)
    }

    /** A chunky block seen from the front and a little above, textured like the game's blocks. */
    private fun cube(gr: Gfx, cx: Float, cy: Float, a: Float, color: Int, variant: Int, light: Float, alpha: Float = 1f) {
        val art = app.baseArt
        val topD = a * 0.55f
        val l = cx - a; val r = cx + a; val tp = cy - a * 0.55f; val b = cy + a * 0.95f
        // shadow
        if (alpha >= 1f) gr.fillRoundRect(l + a * 0.1f, b - a * 0.05f, r + a * 0.2f, b + a * 0.22f, a * 0.2f, 0x40000010)
        // front face
        q[0] = l; q[1] = tp; q[2] = r; q[3] = tp; q[4] = r; q[5] = b; q[6] = l; q[7] = b
        val side = art.side[color][variant]; val topI = art.top[color][variant]
        gr.imageQuad(side, 0f, 0f, side.w.toFloat(), side.h.toFloat(), q, alpha, 0.86f * light)
        // top face (a little narrower at the back)
        q[0] = l + a * 0.18f; q[1] = tp - topD; q[2] = r - a * 0.18f; q[3] = tp - topD; q[4] = r; q[5] = tp; q[6] = l; q[7] = tp
        gr.imageQuad(topI, 0f, 0f, topI.w.toFloat(), topI.h.toFloat(), q, alpha, 1.06f * light)
        gr.line(l, tp, r, tp, max(1.5f, a * 0.03f), Col.withA(0x55FFFFFF, alpha * 0.33f))
    }

    companion object {
        /** The newly opened level's padlock shakes this long before it pops off. */
        const val UNLOCK_POP = 0.7f
    }

    // ------------------------------------------------------------------ input
    fun down(x: Float, y: Float) {
        dragging = false; downY = y; lastY = y; lastT = app.t; vel = 0f
        val id = app.ui.down(x, y)
        pressedNode = if (id >= 100) id - 100 else -1
    }

    fun move(x: Float, y: Float) {
        if (!dragging && abs(y - downY) > 24f * s) { dragging = true; app.ui.cancel(); pressedNode = -1 }
        if (dragging) {
            val dy = y - lastY
            scroll += dy
            val dtt = max(0.001f, app.t - lastT)
            vel = lerp(vel, dy / dtt, 0.5f)
            lastY = y; lastT = app.t
        }
    }

    fun up(x: Float, y: Float) {
        pressedNode = -1
        if (dragging) { dragging = false; return }
        val id = app.ui.up(x, y)
        when {
            id == 1 -> { app.pf.sound(Sfx.CLICK); app.openHome() }
            id >= 101 -> tapLevel(id - 100)
        }
    }

    fun cancel() { dragging = false; pressedNode = -1 }

    private fun tapLevel(n: Int) {
        val pr = app.progress
        when {
            n > Levels.count -> app.openPopup(Pop.SOON)
            n > pr.unlocked -> {
                shakeNode = n; shakeT = 0.4f
                lockedNode = n; lockedMsgT = 2.2f
                lockedMsg = if (n == pr.unlocked + 1) "Finish Level ${n - 1} to unlock" else "Finish Levels ${pr.unlocked}-${n - 1} to unlock"
                app.pf.sound(Sfx.WRONG, 0.6f)
            }
            else -> app.openPopup(Pop.LEVEL, n)
        }
    }

    /** Tests: the message shown after tapping a locked level ("" when none). */
    fun lockedMessage() = if (lockedMsgT > 0f) lockedMsg else ""

    /** Tests: where level [n]'s block is on screen. */
    fun nodeCentre(n: Int) = floatArrayOf(nodeX(n), nodeY(n))
}
