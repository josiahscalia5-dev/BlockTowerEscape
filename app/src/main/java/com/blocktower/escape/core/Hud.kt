package com.blocktower.escape.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Screen 4 HUD. All positions are the artwork's coordinates (1024 x 1536) scaled by [s];
 * the top row is anchored to the top edge and the movement pad / jump button to the bottom edge.
 */
class Hud(val g: Game) {
    var w = 1024f; var h = 1536f
    var s = 1f
    var sceneS = 1f
    var sceneArtTop = 0f
    var sceneCY = 768f
    /** Background plate scale/placement: covers the full screen height on tall phones. */
    var bgS = 1f
    var bgArtTop = 0f
    var focal = 1024f
    var topInset = 0f
    var bottomInset = 0f
    private var offX = 0f

    // controls state
    private var jumpId = -1
    private var toolId = -1; private var toolK = 0          // a finger resting on a tool button
    private var toolDownX = 0f; private var toolDownY = 0f
    /** Movement pad knob (-1..1): mirrors the swipe so the player sees what the thumb is doing. */
    var knobX = 0f; var knobY = 0f
    private var jumpDown = 0f
    private val toolPress = FloatArray(4)
    private val toolDenied = FloatArray(4)
    private var targetBump = 0f
    private var coinBump = 0f
    private var gemBump = 0f
    private var heartBump = 0f
    private var pausePress = 0f
    private var overlayT = 0f
    private val toolBurst = FloatArray(4)
    private var objectiveFlash = 0f
    private var nextToastT = 0f
    private var lastScoreTick = 0

    fun setInsets(top: Float, bottom: Float) { topInset = top; bottomInset = bottom }
    /** Tools only: hide the countdown, mission card and hints (for comparing the view with the design). */
    var plain = false

    fun layout(width: Int, height: Int) {
        w = width.toFloat(); h = height.toFloat()
        s = min(w / 1024f, (h - topInset - bottomInset) / 1450f)
        offX = (w - 1024f * s) * 0.5f
        // the 3D scene fills a phone's width; a wider screen (tablet, foldable) keeps at least the view ahead a
        // 16:9 phone has (so the portal stays in sight) and shows more scenery at the sides instead
        sceneS = min(max(w / 1024f, 0.8f * h / 1536f), h / (1.72f * 1024f))
        val feetY = h - (1536f - 1190f) * sceneS
        sceneArtTop = feetY - 1190f * sceneS
        sceneCY = sceneArtTop + 768f * sceneS
        focal = 1024f * sceneS
        // the sky plate always covers the screen, parallax drift included
        bgS = max(sceneS, max(h / 1536f, w / 1032f))
        bgArtTop = if (bgS > sceneS + 0.001f) (h - 1536f * bgS) * 0.5f else sceneArtTop
    }

    // art -> screen
    fun ax(x: Float) = offX + x * s
    fun ayT(y: Float) = topInset + y * s
    fun ayB(y: Float) = h - bottomInset - (1536f - y) * s

    fun cx() = w * 0.5f
    fun cy() = h * 0.45f
    fun targetIconX() = ax(906f); fun targetIconY() = ayT(215f)
    fun coinIconX() = ax(682f); fun coinIconY() = ayT(53f)
    fun gemIconX() = ax(846f); fun gemIconY() = ayT(52f)
    fun heartX(i: Int) = ax(148f + 63.5f * i); fun heartY() = ayT(56f)
    fun timerX() = ax(540f); fun timerY() = ayT(57f)
    private val toolYs = floatArrayOf(510f, 650f, 795f, 940f)
    fun toolX(k: Int) = ax(934f); fun toolY(k: Int) = ayT(toolYs[k])
    private fun joyCX() = ax(183f); private fun joyCY() = ayB(1302f)
    /** The movement pad's centre (the joystick's centre in joystick mode). */
    fun joyX() = joyCX(); fun joyY() = joyCY()
    /** Joystick mode: a touch here takes the stick (around the pad, and the lower-left corner). */
    private fun inJoyZone(x: Float, y: Float) = !inJump(x, y) && !inToolColumn(x, y) &&
        (sq(x - joyCX()) + sq(y - joyCY()) <= sq(220f * s) || (x < w * 0.5f && y > joyCY() - 200f * s))
    private fun jumpCX() = ax(872f); private fun jumpCY() = ayB(1308f)

    fun bumpTarget() { targetBump = 1f }
    fun bumpCoins() { coinBump = 1f }
    fun bumpGems() { gemBump = 1f }
    fun bumpHearts() { heartBump = 1f }
    fun denied(k: Int) { toolDenied[k] = 1f }
    fun toolFx(k: Int) { toolBurst[k] = 1f; g.fx.ring(toolX(k), toolY(k), toolColor(k), 50f * s, 150f * s, 0.45f, 9f * s) }
    fun objectiveDone() { objectiveFlash = 1f; g.fx.ring(targetIconX(), targetIconY(), 0xFF7CFFA8.toInt(), 30f * s, 200f * s, 0.7f, 10f * s) }
    fun resultsStarted() { overlayT = 0f; lastScoreTick = 0; skipResults = false; starPlayed.fill(false) }

    fun update(dt: Float) {
        targetBump = max(0f, targetBump - dt * 3f); coinBump = max(0f, coinBump - dt * 5f)
        gemBump = max(0f, gemBump - dt * 5f); heartBump = max(0f, heartBump - dt * 2.5f)
        jumpDown = max(0f, jumpDown - dt * 6f); pausePress = max(0f, pausePress - dt * 5f)
        for (i in 0..3) { toolPress[i] = max(0f, toolPress[i] - dt * 5f); toolDenied[i] = max(0f, toolDenied[i] - dt * 3f); toolBurst[i] = max(0f, toolBurst[i] - dt * 2.2f) }
        objectiveFlash = max(0f, objectiveFlash - dt * 0.6f)
        overlayT += dt
        if (toolId >= 0) toolPress[toolK] = 1f
        // the pad's knob follows the thumb while swiping and leans forward while the boy keeps running
        val sw = g.swipe
        var tx = 0f; var ty = 0f
        if (g.joystickMode) { tx = g.joy.x; ty = g.joy.y }
        else if (sw.holding) {
            tx = (sw.fingerX - sw.anchorX) / (120f * s); ty = (sw.fingerY - sw.anchorY) / (120f * s)
        } else ty = -0.4f * sw.cruise
        val m = len2(tx, ty)
        if (m > 1f) { tx /= m; ty /= m }
        val k = damp(if (g.joystickMode) 40f else 16f, dt)
        knobX = lerp(knobX, tx, k); knobY = lerp(knobY, ty, k)
    }

    // ------------------------------------------------------------------ touch
    private fun inCircle(x: Float, y: Float, cx: Float, cy: Float, r: Float) = sq(x - cx) + sq(y - cy) <= r * r

    private fun inJump(x: Float, y: Float) = inCircle(x, y, jumpCX(), jumpCY(), 150f * s)
    private fun inToolColumn(x: Float, y: Float) = x > ax(858f) && y > ayT(420f) && y < toolY(3) + 85f * s
    /** Swipe / movement area: the lower part of the screen that is not a button. */
    fun inSwipeArea(x: Float, y: Float) = y > h * 0.3f && !inJump(x, y) && !inToolColumn(x, y)

    fun touchDown(id: Int, x: Float, y: Float) {
        val st = g.state
        if (g.paused) { overlayButton(x, y); return }
        if (st == GS.RESULTS && !resultsRevealed()) { skipResults = true; return }
        if (st == GS.RESULTS || st == GS.FAILED) { overlayButton(x, y); return }
        if (st == GS.COMPLETE) return
        if (x > ax(930f) && y < ayT(110f)) { pausePress = 1f; g.togglePause(); overlayT = 0f; return }
        for (k in 0..3) if (inCircle(x, y, toolX(k), toolY(k), 72f * s)) {
            // a tool fires when the finger lifts on its button, so a swipe can never trigger one
            if (toolId < 0) { toolId = id; toolK = k; toolDownX = x; toolDownY = y; toolPress[k] = 1f }
            return
        }
        if (inJump(x, y)) {
            jumpId = id; jumpDown = 1f
            g.input.jumpHeld = true; g.input.jumpPressed = true
            return
        }
        if (g.joystickMode) { if (inJoyZone(x, y) && !g.joy.holding) g.joy.down(id, x, y); return }
        if (inSwipeArea(x, y) && !g.swipe.holding) g.swipe.down(id, x, y)
    }

    fun touchMove(id: Int, x: Float, y: Float) {
        if (id == g.joy.id) g.joy.move(id, x, y)
        else if (id == g.swipe.id) g.swipe.move(id, x, y)
        else if (id == toolId && len2(x - toolDownX, y - toolDownY) > 30f * s) toolId = -1   // slid away: not a tap
    }

    fun touchUp(id: Int, x: Float, y: Float) {
        if (id == g.joy.id) g.joy.up(id)
        if (id == g.swipe.id) g.swipe.up(id, x, y)
        if (id == jumpId) { jumpId = -1; g.input.jumpHeld = false }
        if (id == toolId) {
            toolId = -1
            if (inCircle(x, y, toolX(toolK), toolY(toolK), 80f * s)) g.input.toolTap[toolK] = true
        }
    }

    /** The system took the touch away (e.g. a gesture or dialog): release everything, trigger nothing. */
    fun touchCancel(id: Int) {
        g.swipe.cancel(id)
        g.joy.up(id)
        if (id == jumpId) { jumpId = -1; g.input.jumpHeld = false }
        if (id == toolId) toolId = -1
    }

    private var btnA = FloatArray(4); private var btnB = FloatArray(4); private var btnC = FloatArray(4)
    private fun overlayButton(x: Float, y: Float) {
        if (x >= btnA[0] && x <= btnA[2] && y >= btnA[1] && y <= btnA[3]) { pressA(); return }
        if (x >= btnB[0] && x <= btnB[2] && y >= btnB[1] && y <= btnB[3]) { pressB(); return }
        if (x >= btnC[0] && x <= btnC[2] && y >= btnC[1] && y <= btnC[3]) { pressC(); return }
    }
    /** Tests: centre of an overlay button (0 = CONTINUE / NEXT LEVEL / RESUME, 1 = RESTART / REPLAY, 2 = LEVEL MAP). */
    fun overlayButtonCentre(i: Int): FloatArray { val b = when (i) { 0 -> btnA; 1 -> btnB; else -> btnC }; return floatArrayOf((b[0] + b[2]) * 0.5f, (b[1] + b[3]) * 0.5f) }
    /** LEVEL MAP: back to the level map (only when the game runs inside the app). */
    private fun pressC() {
        val host = g.host ?: return
        g.platform.sound(Sfx.CLICK)
        host.exitToMap(g)
    }
    private fun pressA() {
        g.platform.sound(Sfx.CLICK)
        when {
            g.paused -> g.togglePause()
            g.state == GS.FAILED -> { g.continueFromCheckpoint(); overlayT = 0f }
            g.state == GS.RESULTS -> {
                val host = g.host
                if (host != null) host.nextLevel(g)
                else g.fx.popupScreen("LEVEL ${g.spec.number + 1} — COMING SOON", w * 0.5f, btnA[1] - 50f * s, 0xFFBFD8FF.toInt(), 36f)
            }
        }
    }
    private fun pressB() {
        g.platform.sound(Sfx.CLICK)
        if (g.paused || g.state == GS.FAILED || g.state == GS.RESULTS) { g.restartLevel(); overlayT = 0f }
    }
    fun pressDefault() {
        if (g.state == GS.RESULTS && !resultsRevealed()) { skipResults = true; return }
        if (g.paused || g.state == GS.RESULTS || g.state == GS.FAILED) pressA()
    }

    // ------------------------------------------------------------------ render
    fun render(gr: Gfx) {
        drawTopBar(gr)
        drawTargetPanel(gr)
        for (k in 0..3) drawTool(gr, k)
        drawJoystick(gr)
        drawJump(gr)
        drawSwipeTrail(gr)
        drawMeter(gr)
        drawHint(gr)
        drawRings(gr)
        drawFlyers(gr)
        drawScreenPopups(gr)
        drawBanner(gr)
        if (!plain) {
            drawToast(gr)
            drawMission(gr)
            drawCountdown(gr)
        }
        if (g.paused) drawPause(gr)
        else if (g.state == GS.RESULTS) drawResults(gr)
        else if (g.state == GS.FAILED && g.stateT > (if (g.failReason.startsWith("CAUGHT")) 0.5f else 1.1f)) drawFail(gr)
    }

    private fun panel(gr: Gfx, l: Float, t: Float, r: Float, b: Float, rad: Float, fill: Int) {
        gr.fillRoundRect(ax(l), ayT(t), ax(r), ayT(b), rad * s, fill)
    }

    private fun drawTopBar(gr: Gfx) {
        // level badge
        val l = ax(14f); val t = ayT(18f); val r = ax(104f); val b = ayT(110f)
        gr.fillRoundRect(l - 2f * s, t - 2f * s, r + 2f * s, b + 2f * s, 16f * s, 0xFF06122E.toInt())
        drawRR(gr, l, t, r, b, 14f * s, 0xFF3FA9FF.toInt(), 0xFF1D5FE0.toInt())
        drawRR(gr, l + 5f * s, t + 5f * s, r - 5f * s, b - 5f * s, 10f * s, 0xFF15306C.toInt(), 0xFF0A1A44.toInt())
        gr.text("Lv", ax(59f), ayT(44f), 33f * s, Font.UI, Col.WHITE)
        gr.text(g.spec.number.toString(), ax(59f), ayT(84f), 48f * s, Font.UI, Col.WHITE)

        // hearts
        panel(gr, 112f, 20f, 314f, 92f, 16f, 0x8C0A1434.toInt())
        for (i in 0 until g.maxHearts) {
            val full = i < g.hearts
            val img = if (full) g.art.heartFull else g.art.heartEmpty
            var sc = 1f
            if (heartBump > 0f && i == (if (full) g.hearts - 1 else g.hearts)) sc = 1f + 0.35f * sin(heartBump * PI.toFloat())
            val hw = 57f * s * sc; val hh = hw * img.h / img.w
            gr.image(img, heartX(i) - hw * 0.5f, heartY() - hh * 0.5f, hw, hh)
        }

        // timer
        val low = g.time <= 15f && g.state == GS.PLAY
        gr.fillRoundRect(ax(394f), ayT(22f), ax(614f), ayT(94f), 20f * s, if (low) Col.mix(0xFF8A2A6A.toInt(), 0xFFFF3030.toInt(), pulse(g.t, 8f)) else 0xFF8A2A6A.toInt())
        gr.fillRoundRect(ax(397f), ayT(25f), ax(611f), ayT(91f), 17f * s, 0xF20A0F2C.toInt())
        val sw = g.art.stopwatch
        val iw = 64f * s * (if (low) 1f + 0.08f * pulse(g.t, 8f) else 1f); val ih = iw * sw.h / sw.w
        gr.image(sw, ax(432f) - iw * 0.5f, ayT(58f) - ih * 0.5f, iw, ih)
        gr.text(g.timeText(), ax(541f), ayT(58f), 52f * s, Font.UI, if (low) 0xFFFF8A8A.toInt() else Col.WHITE)

        // coins + gems
        panel(gr, 650f, 22f, 805f, 88f, 14f, 0xD90A1438.toInt())
        panel(gr, 810f, 22f, 936f, 88f, 14f, 0xD90A1438.toInt())
        val ci = g.art.coinIcon
        val cs = 58f * s * (1f + 0.25f * coinBump)
        gr.image(ci, coinIconX() - cs * 0.5f, coinIconY() - cs * 0.5f * ci.h / ci.w, cs, cs * ci.h / ci.w)
        gr.text(fmt(g.shownCoins), ax(758f), ayT(54f), 40f * s * (1f + 0.1f * coinBump), Font.UI, Col.WHITE)
        val gi = g.art.gem
        val gs = 56f * s * (1f + 0.25f * gemBump)
        gr.image(gi, gemIconX() - gs * 0.5f, gemIconY() - gs * 0.5f * gi.h / gi.w, gs, gs * gi.h / gi.w)
        gr.text(g.shownGems.toString(), ax(903f), ayT(54f), 40f * s * (1f + 0.1f * gemBump), Font.UI, Col.WHITE)

        // pause
        val pp = 1f - 0.08f * pausePress
        val pcx = ax(978f); val pcy = ayT(56f)
        val half = 38f * s * pp
        gr.fillRoundRect(pcx - half - 2f * s, pcy - half - 2f * s, pcx + half + 2f * s, pcy + half + 4f * s, 18f * s, 0xFF0B2F8C.toInt())
        drawRR(gr, pcx - half, pcy - half, pcx + half, pcy + half, 16f * s, 0xFF55B6FF.toInt(), 0xFF1650DC.toInt())
        gr.fillRoundRect(pcx - half + 6f * s, pcy - half + 4f * s, pcx + half - 6f * s, pcy - 6f * s, 12f * s, 0x2EFFFFFF)
        gr.fillRoundRect(pcx - 17f * s, pcy - 22f * s, pcx - 5f * s, pcy + 22f * s, 6f * s, Col.WHITE)
        gr.fillRoundRect(pcx + 5f * s, pcy - 22f * s, pcx + 17f * s, pcy + 22f * s, 6f * s, Col.WHITE)
    }

    /** Rounded rect with a vertical 2-colour gradient. */
    private fun drawRR(gr: Gfx, l: Float, t: Float, r: Float, b: Float, rad: Float, c0: Int, c1: Int) {
        rr.ops.clear(); rr.native = null
        rr.roundRect(l, t, r, b, rad)
        val f = Linear(0f, t, 0f, b, intArrayOf(c0, c1))
        gr.fillPath(rr, f)
    }
    private val rr = VPath()

    private fun fmt(v: Int): String {
        val s = v.toString()
        val sb = StringBuilder()
        for (i in s.indices) { if (i > 0 && (s.length - i) % 3 == 0) sb.append(','); sb.append(s[i]) }
        return sb.toString()
    }

    /** A drawn check mark (fonts on some phones lack the glyph). */
    private fun check(gr: Gfx, x: Float, y: Float, r: Float, color: Int, a: Float) {
        val c = Col.mulA(color, a)
        gr.line(x - r, y, x - r * 0.3f, y + r * 0.7f, r * 0.45f, c)
        gr.line(x - r * 0.3f, y + r * 0.7f, x + r, y - r * 0.8f, r * 0.45f, c)
    }

    private fun drawTargetPanel(gr: Gfx) {
        val l = ax(810f); val t = ayT(118f); val r = ax(1004f); val b = ayT(392f)
        val done = g.shownTarget >= g.spec.targetNeed
        if (objectiveFlash > 0f) { gr.setAdditive(true); gr.glow((l + r) * 0.5f, (t + b) * 0.5f, (r - l) * 0.9f, Col.withA(0xFF4AF08A.toInt(), objectiveFlash * 0.7f)); gr.setAdditive(false) }
        gr.fillRoundRect(l - 2f * s, t - 2f * s, r + 2f * s, b + 2f * s, 20f * s, 0xFF06122E.toInt())
        drawRR(gr, l, t, r, b, 18f * s, if (done) 0xFF4AF08A.toInt() else 0xFF3A9CFF.toInt(), if (done) 0xFF18B050.toInt() else 0xFF1E62E6.toInt())
        drawRR(gr, l + 5f * s, t + 5f * s, r - 5f * s, b - 5f * s, 14f * s, 0xFF0F245C.toInt(), 0xFF081640.toInt())
        gr.text("Target", ax(907f), ayT(150f), 34f * s, Font.UI, Col.WHITE)
        val cube = g.art.targetCube
        val bump = 1f + 0.3f * sin(targetBump * PI.toFloat())
        val cw = 88f * s * bump
        if (targetBump > 0f) { gr.setAdditive(true); gr.glow(targetIconX(), targetIconY(), cw * 0.9f, Col.withA(0xFF60B8FF.toInt(), targetBump)); gr.setAdditive(false) }
        gr.image(cube, targetIconX() - cw * 0.5f, targetIconY() - cw * 0.5f * cube.h / cube.w, cw, cw * cube.h / cube.w)
        gr.text("Collect", ax(907f), ayT(282f), 33f * s, Font.UI, Col.WHITE)
        gr.text("${g.spec.targetNeed} Blue Blocks", ax(907f), ayT(314f), 30f * s, Font.UI, Col.WHITE)
        val cnt = "${min(g.shownTarget, g.spec.targetNeed)}/${g.spec.targetNeed}"
        val cs = 46f * s * (1f + 0.35f * sin(targetBump * PI.toFloat()))
        gr.text(cnt, ax(907f), ayT(360f), cs, Font.UI, if (done) 0xFF7CFFA8.toInt() else Col.WHITE)
        if (done) check(gr, ax(978f), ayT(360f), 13f * s, 0xFF7CFFA8.toInt(), 1f)
    }

    private fun drawTool(gr: Gfx, k: Int) {
        if (g.toolLocked(k)) { drawLockedTool(gr, k); return }
        val tl = g.tools[k]
        var x = toolX(k); val y = toolY(k)
        if (toolDenied[k] > 0f) x += sin(toolDenied[k] * 40f) * 8f * s * toolDenied[k]
        val press = 1f - 0.08f * toolPress[k]
        val R = 62f * s * press * (1f + 0.12f * sin(tl.anim * PI.toFloat()))
        if (tl.isActive || toolBurst[k] > 0f || tl.readyFlash > 0f) {
            val expiring = tl.isActive && tl.active < 1.5f && ((g.t * 8f).toInt() and 1) == 0
            val a = if (tl.isActive) (if (expiring) 0.25f else 0.55f + 0.25f * pulse(g.t, 6f)) else 0f
            gr.setAdditive(true)
            gr.glow(x, y, R * (1.55f + 0.5f * toolBurst[k]), Col.withA(toolColor(k), max(a, max(toolBurst[k], tl.readyFlash * 0.6f))))
            gr.setAdditive(false)
        }
        gr.fillCircle(x, y, R + 2f * s, 0xFF081236.toInt())
        ring.ops.clear(); ring.native = null; ring.circle(x, y, R)
        gr.fillPath(ring, Linear(0f, y - R, 0f, y + R, intArrayOf(0xFF4AA6FF.toInt(), 0xFF1848C8.toInt())))
        inner.ops.clear(); inner.native = null; inner.circle(x, y, R * 0.86f)
        gr.fillPath(inner, Radial(x - R * 0.2f, y - R * 0.3f, R, intArrayOf(0xFF1A3A80.toInt(), 0xFF0A1740.toInt())))
        val img = when (k) { TK.MAGNET -> g.art.magnet; TK.SHIELD -> g.art.shield; TK.SPEED -> g.art.lightning; else -> g.art.blockTool }
        val base = if (k == TK.BLOCK) 92f else 84f
        val ih = base * s * press * (1f + 0.3f * sin(tl.anim * PI.toFloat())) * (1f + 0.25f * sin(tl.gain * PI.toFloat()))
        val iw = ih * img.w / img.h
        val a = if (tl.count <= 0 && !tl.isActive) 0.4f else 1f
        val iy = y - 10f * s * sin(toolBurst[k] * PI.toFloat())
        gr.image(img, x - iw * 0.5f - 4f * s, iy - ih * 0.5f - 4f * s, iw, ih, a)
        if (tl.isActive) {
            val frac = tl.active / tl.duration
            gr.arc(x, y, R + 7f * s, -90f, 360f * frac, 7f * s, 0xFFFFE14A.toInt())
            gr.text(kotlin.math.ceil(tl.active).toInt().toString() + "s", x, y + R * 0.62f, 24f * s, Font.UI, 0xFFFFE14A.toInt(), Align.CENTER, 4f * s, 0xFF081236.toInt())
        } else if (tl.cooldown > 0f) {
            gr.pie(x, y, R * 0.86f, -90f, 360f * (tl.cooldown / tl.cooldownDur), 0x99000010.toInt())
        }
        // count badge
        val bx = x + 42f * s; val by = y + 40f * s
        val br = 23f * s * (1f + 0.3f * sin(tl.gain * PI.toFloat()))
        gr.fillCircle(bx, by, br + 3f * s, 0xFF2E6BE8.toInt())
        gr.fillCircle(bx, by, br, 0xFF0B1636.toInt())
        gr.text(tl.count.toString(), bx, by, 31f * s, Font.UI, if (tl.count > 0) Col.WHITE else 0xFF8090B0.toInt())
    }
    /** A tool that unlocks in a later level: the same button, greyed, with a padlock. */
    private fun drawLockedTool(gr: Gfx, k: Int) {
        var x = toolX(k); val y = toolY(k)
        if (toolDenied[k] > 0f) x += sin(toolDenied[k] * 40f) * 8f * s * toolDenied[k]
        val R = 62f * s
        gr.fillCircle(x, y, R + 2f * s, 0xFF081236.toInt())
        ring.ops.clear(); ring.native = null; ring.circle(x, y, R)
        gr.fillPath(ring, Linear(0f, y - R, 0f, y + R, intArrayOf(0xFF5A6480.toInt(), 0xFF343C54.toInt())))
        inner.ops.clear(); inner.native = null; inner.circle(x, y, R * 0.86f)
        gr.fillPath(inner, Solid(0xFF161C30.toInt()))
        val img = when (k) { TK.MAGNET -> g.art.magnet; TK.SHIELD -> g.art.shield; TK.SPEED -> g.art.lightning; else -> g.art.blockTool }
        val ih = 70f * s; val iw = ih * img.w / img.h
        gr.image(img, x - iw * 0.5f, y - ih * 0.5f - 4f * s, iw, ih, 0.22f)
        padlock(gr, x, y + 6f * s, 26f * s)
    }

    /** A small gold padlock. */
    fun padlock(gr: Gfx, x: Float, y: Float, r: Float) {
        gr.arc(x, y - r * 0.55f, r * 0.55f, 180f, 180f, r * 0.26f, 0xFFB8C0D8.toInt())
        gr.fillRoundRect(x - r * 0.85f, y - r * 0.5f, x + r * 0.85f, y + r * 0.75f, r * 0.2f, 0xFFE8A824.toInt())
        gr.fillRoundRect(x - r * 0.85f, y - r * 0.5f, x + r * 0.85f, y - r * 0.2f, r * 0.2f, 0xFFFFD86A.toInt())
        gr.fillCircle(x, y + r * 0.08f, r * 0.17f, 0xFF5A3208.toInt())
        gr.fillRect(x - r * 0.07f, y + r * 0.08f, x + r * 0.07f, y + r * 0.45f, 0xFF5A3208.toInt())
    }

    private val ring = VPath()
    private val inner = VPath()
    private fun toolColor(k: Int) = when (k) { TK.MAGNET -> 0xFFFF4A4A.toInt(); TK.SHIELD -> 0xFF3FB8FF.toInt(); TK.SPEED -> 0xFFFFD83A.toInt(); else -> 0xFFFFB03A.toInt() }

    private fun drawJoystick(gr: Gfx) {
        val x = joyCX(); val y = joyCY()
        val R = 140f * s
        gr.fillCircle(x, y, R, 0x5E14244A)
        gr.strokeCircle(x, y, R - 2f * s, 4f * s, 0xB8D2E2FF.toInt())
        gr.strokeCircle(x, y, R - 14f * s, 2f * s, 0x40FFFFFF)
        // the arrow of the last recognised swipe lights up briefly; before the first move the forward arrow breathes
        val sw = g.swipe
        val idle = if (!g.movedYet && g.state == GS.PLAY && g.playT > 1.5f) 0.5f * pulse(g.t, 2.2f) else 0f
        fun lit(dir: Int): Float {
            if (g.joystickMode) {
                // the stick lights the way it is pushed
                val j = g.joy
                val v = when (dir) { 2 -> -j.y; -2 -> j.y; -1 -> -j.x; else -> j.x }
                return max(clamp01(v * 1.6f - 0.25f), if (dir == 2) idle else 0f)
            }
            return max(if (sw.lastSwipeDir == dir) sw.swipeFlash else 0f, if (dir == 2) idle else 0f)
        }
        arrow(gr, x, y - 104f * s, 0f, lit(2)); arrow(gr, x, y + 104f * s, 180f, lit(-2))
        arrow(gr, x - 104f * s, y, -90f, lit(-1)); arrow(gr, x + 104f * s, y, 90f, lit(1))
        val kx = x + knobX * 78f * s; val ky = y + knobY * 78f * s
        val kr = 64f * s
        gr.fillCircle(kx, ky + 4f * s, kr + 3f * s, 0x55000010)
        gr.fillCircle(kx, ky, kr + 3f * s, 0xFF5A74B4.toInt())
        knob.ops.clear(); knob.native = null; knob.circle(kx, ky, kr)
        gr.fillPath(knob, Radial(kx - kr * 0.3f, ky - kr * 0.4f, kr * 1.3f, intArrayOf(0xFFF4F8FF.toInt(), 0xFFC6D4F0.toInt(), 0xFF8EA4D4.toInt()), floatArrayOf(0f, 0.55f, 1f)))
        gr.arc(kx, ky, kr * 0.78f, 200f, 90f, 5f * s, 0x99FFFFFF.toInt())
    }

    private fun arrow(gr: Gfx, x: Float, y: Float, rot: Float, lit: Float) {
        if (lit > 0.01f) { gr.setAdditive(true); gr.glow(x, y, 34f * s, Col.withA(0xFF7CC8FF.toInt(), 0.55f * lit)); gr.setAdditive(false) }
        tri(gr, x, y, rot, s, Col.mix(0xD8C4D4F2.toInt(), 0xFFFFFFFF.toInt(), lit))
    }

    /** A faint trace of the swipe under the thumb (where it started, where it is now). */
    private fun drawSwipeTrail(gr: Gfx) {
        val sw = g.swipe
        val a = sw.trail
        if (a < 0.02f || g.state != GS.PLAY && g.state != GS.INTRO) return
        val ax0 = sw.anchorX; val ay0 = sw.anchorY; val fx = sw.fingerX; val fy = sw.fingerY
        if (len2(fx - ax0, fy - ay0) > 16f * s) gr.line(ax0, ay0, fx, fy, 5f * s, Col.withA(0xFFCFE6FF.toInt(), 0.18f * a))
        gr.strokeCircle(ax0, ay0, 30f * s, 3f * s, Col.withA(0xFFCFE6FF.toInt(), 0.22f * a))
        gr.fillCircle(fx, fy, 16f * s, Col.withA(0xFFFFFFFF.toInt(), 0.2f * a))
    }
    private val knob = VPath()

    private fun tri(gr: Gfx, x: Float, y: Float, rot: Float, s: Float, c: Int) {
        val a = rot * PI.toFloat() / 180f
        val cs = cos(a); val sn = sin(a)
        fun px(u: Float, v: Float) = x + u * cs - v * sn
        fun py(u: Float, v: Float) = y + u * sn + v * cs
        tp[0] = px(0f, -16f * s); tp[1] = py(0f, -16f * s)
        tp[2] = px(18f * s, 12f * s); tp[3] = py(18f * s, 12f * s)
        tp[4] = px(-18f * s, 12f * s); tp[5] = py(-18f * s, 12f * s)
        gr.fillPoly(tp, 3, c)
    }
    private val tp = FloatArray(16)

    private fun drawJump(gr: Gfx) {
        val x = jumpCX(); val y = jumpCY()
        val press = 1f - 0.07f * jumpDown
        val R = 104f * s * press
        gr.setAdditive(true)
        gr.glow(x, y, R * 1.35f, 0x552E8CFF)
        gr.setAdditive(false)
        gr.fillCircle(x, y, R + 3f * s, 0xFF0A2A80.toInt())
        ring.ops.clear(); ring.native = null; ring.circle(x, y, R)
        gr.fillPath(ring, Linear(0f, y - R, 0f, y + R, intArrayOf(0xFF4AB0FF.toInt(), 0xFF1C5AE0.toInt())))
        inner.ops.clear(); inner.native = null; inner.circle(x, y, R * 0.9f)
        gr.fillPath(inner, Radial(x, y - R * 0.2f, R, intArrayOf(if (jumpDown > 0f) 0xF2204EA8.toInt() else 0xF2143070.toInt(), 0xF20A1844.toInt())))
        val k = R / 104f
        arrowPath.ops.clear(); arrowPath.native = null
        arrowPath.poly(x, y - 62f * k, x + 56f * k, y - 4f * k, x + 26f * k, y - 4f * k, x + 26f * k, y + 56f * k,
            x - 26f * k, y + 56f * k, x - 26f * k, y - 4f * k, x - 56f * k, y - 4f * k)
        gr.setAdditive(true)
        gr.glow(x, y, R * 0.7f, 0x3360C0FF)
        gr.setAdditive(false)
        gr.fillPath(arrowPath, Linear(0f, y - 62f * k, 0f, y + 56f * k, intArrayOf(0xFFC8ECFF.toInt(), 0xFF62B8FF.toInt())))
        gr.strokePath(arrowPath, 3f * s, Solid(0xCCE8F6FF.toInt()))
    }
    private val arrowPath = VPath()

    private fun drawMeter(gr: Gfx) {
        val a = g.ev.meterShow
        if (a <= 0.01f) return
        // bottom centre, between the joystick and the jump button (the danger comes from behind)
        val l = ax(335f); val r = ax(745f); val t = ayB(1452f) + (1f - a) * 30f * s; val b = t + 54f * s
        gr.fillRoundRect(l, t, r, b, 16f * s, Col.withA(0xFF0A1438.toInt(), 0.85f * a))
        gr.strokeRoundRect(l, t, r, b, 16f * s, 3f * s, Col.withA(0xFFFF5A4A.toInt(), a))
        val label = g.ev.meterLabel
        gr.text(label, (l + r) * 0.5f, t - 18f * s, 26f * s, Font.UI, Col.withA(0xFFFFD0C8.toInt(), a), Align.CENTER, 4f * s, Col.withA(0xFF0A1438.toInt(), a))
        val bl = l + 20f * s; val br = r - 64f * s; val bt = t + 17f * s; val bb = b - 17f * s
        gr.fillRoundRect(bl, bt, br, bb, 11f * s, Col.withA(0xFF223058.toInt(), a))
        val m = clamp01(g.ev.meter)
        val col = if (m < 0.5f) Col.mix(0xFF4ADB6A.toInt(), 0xFFFFD23A.toInt(), m * 2f) else Col.mix(0xFFFFD23A.toInt(), 0xFFFF3A2A.toInt(), (m - 0.5f) * 2f)
        if (m > 0.02f) gr.fillRoundRect(bl, bt, bl + (br - bl) * m, bb, 11f * s, Col.withA(col, a))
        // enemy marker sliding toward the player marker
        val ex = bl + (br - bl) * m
        val ey = (bt + bb) * 0.5f
        gr.fillRoundRect(ex - 18f * s, ey - 18f * s, ex + 18f * s, ey + 18f * s, 7f * s, Col.withA(0xFF2A2E3C.toInt(), a))
        if (g.ev.meterKind == 0) {
            gr.fillRect(ex - 12f * s, ey - 5f * s, ex - 3f * s, ey + 1f * s, Col.withA(0xFFFF8A20.toInt(), a))
            gr.fillRect(ex + 3f * s, ey - 5f * s, ex + 12f * s, ey + 1f * s, Col.withA(0xFFFF8A20.toInt(), a))
        } else if (g.ev.meterKind == 2) {
            gr.fillRoundRect(ex - 14f * s, ey - 2f * s, ex + 14f * s, ey + 14f * s, 5f * s, Col.withA(0xFFFF6A10.toInt(), a))
            gr.fillCircle(ex - 5f * s, ey - 4f * s, 6f * s, Col.withA(0xFFFFB040.toInt(), a)); gr.fillCircle(ex + 6f * s, ey - 7f * s, 4f * s, Col.withA(0xFFFFD070.toInt(), a))
        } else {
            gr.line(ex - 10f * s, ey - 10f * s, ex + 8f * s, ey + 10f * s, 3f * s, Col.withA(0xFFFFB04A.toInt(), a))
            gr.line(ex - 2f * s, ey - 12f * s, ex - 6f * s, ey + 4f * s, 3f * s, Col.withA(0xFFFFB04A.toInt(), a))
        }
        // player marker (brown hair head)
        val px = r - 34f * s
        gr.fillCircle(px, ey, 21f * s, Col.withA(0xFF1C5AE0.toInt(), a))
        gr.fillCircle(px, ey + 2f * s, 16f * s, Col.withA(0xFFF0B080.toInt(), a))
        gr.fillCircle(px, ey - 6f * s, 15f * s, Col.withA(0xFF6A3418.toInt(), a))
        if (m > 0.75f && ((g.t * 6f).toInt() and 1) == 0) gr.strokeRoundRect(l, t, r, b, 16f * s, 6f * s, Col.withA(0xFFFF2A1A.toInt(), a))
    }

    private fun drawHint(gr: Gfx) {
        if (g.hintT <= 0f || g.hint.isEmpty()) return
        val a = clamp01(g.hintT / 0.4f) * clamp01((3.8f - g.hintT) / 0.25f)
        val size = 30f * s
        val tw = gr.textWidth(g.hint, size, Font.UI)
        val pad = 18f * s
        val bw = tw + pad * 2; val bh = 58f * s
        var bx: Float; var by: Float
        var tipX: Float; var tipY: Float
        when (g.hintKind) {
            1 -> { bx = ax(800f) - bw; by = ayT(360f); tipX = bx + bw; tipY = by + bh * 0.5f }
            2 -> { bx = jumpCX() - bw + 60f * s; by = jumpCY() - 190f * s; tipX = jumpCX(); tipY = by + bh }
            3 -> { bx = ax(862f) - bw; by = toolY(1) - bh * 0.5f; tipX = bx + bw; tipY = toolY(1) }
            5 -> { bx = ax(862f) - bw; by = toolY(0) - bh * 0.5f; tipX = bx + bw; tipY = toolY(0) }
            6 -> { bx = joyCX() - 60f * s; by = joyCY() - 250f * s; tipX = joyCX(); tipY = by + bh }
            7 -> { bx = w * 0.5f - bw * 0.5f; by = h * 0.22f; tipX = -1f; tipY = -1f }
            else -> { bx = ax(862f) - bw; by = toolY(3) - bh * 0.5f; tipX = bx + bw; tipY = toolY(3) }
        }
        bx = max(bx, 10f * s)
        val bob = sin(g.t * 5f) * 4f * s
        gr.fillRoundRect(bx, by + bob, bx + bw, by + bh + bob, 16f * s, Col.withA(0xFFFFFFFF.toInt(), 0.95f * a))
        if (g.hintKind == 7) {} else if (g.hintKind == 2 || g.hintKind == 6) { tp[0] = tipX - 14f * s; tp[1] = tipY + bob; tp[2] = tipX + 14f * s; tp[3] = tipY + bob; tp[4] = tipX; tp[5] = tipY + 18f * s + bob }
        else { tp[0] = tipX; tp[1] = tipY - 14f * s + bob; tp[2] = tipX; tp[3] = tipY + 14f * s + bob; tp[4] = tipX + 18f * s; tp[5] = tipY + bob }
        gr.fillPoly(tp, 3, Col.withA(Col.WHITE, 0.95f * a))
        gr.text(g.hint, bx + bw * 0.5f, by + bh * 0.5f + bob, size, Font.UI, Col.withA(0xFF10205A.toInt(), a))
    }

    private fun drawRings(gr: Gfx) {
        for (r in g.fx.rings) if (r.active) {
            val u = r.t / r.dur
            gr.strokeCircle(r.x, r.y, lerp(r.r0, r.r1, easeOutCubic(u)), r.width * (1f - u), Col.withA(r.color, 1f - u))
        }
    }

    private fun drawFlyers(gr: Gfx) {
        for (f in g.fx.flyers) {
            if (!f.active || f.delay > 0f) continue
            val u = easeInCubic(f.t) * 0.6f + f.t * 0.4f
            val mx = (f.x0 + f.x1) * 0.5f + f.arc * s; val my = min(f.y0, f.y1) - 120f * s
            val x = (1 - u) * (1 - u) * f.x0 + 2 * (1 - u) * u * mx + u * u * f.x1
            val y = (1 - u) * (1 - u) * f.y0 + 2 * (1 - u) * u * my + u * u * f.y1
            val img = when (f.kind) {
                FK.COIN -> g.art.coinIcon; FK.TARGET -> g.art.targetCube; FK.GEM -> g.art.gem; FK.HEART -> g.art.heartFull
                else -> when (f.value) { TK.MAGNET -> g.art.magnet; TK.SHIELD -> g.art.shield; TK.SPEED -> g.art.lightning; else -> g.art.blockTool }
            }
            val size = (if (f.kind == FK.TARGET) 70f else 50f) * s * (1.2f - 0.4f * f.t)
            gr.setAdditive(true)
            gr.glow(x, y, size * 0.9f, if (f.kind == FK.TARGET) 0x6650B0FF else 0x55FFD040)
            gr.setAdditive(false)
            gr.image(img, x - size * 0.5f, y - size * 0.5f * img.h / img.w, size, size * img.h / img.w)
        }
    }

    private fun drawScreenPopups(gr: Gfx) {
        for (p in g.fx.popups) {
            if (!p.active || p.world) continue
            val u = p.t / p.dur
            val a = if (u < 0.7f) 1f else 1f - (u - 0.7f) / 0.3f
            gr.text(p.text, p.x, p.y - p.t * 50f * s, p.size * s, Font.TITLE, p.color, Align.CENTER, p.size * s * 0.15f, 0xFF1A1030.toInt(), a)
        }
    }

    private fun drawBanner(gr: Gfx) {
        val b = g.fx.banners.firstOrNull() ?: return
        val t = b.t
        val inU = clamp01(t / 0.3f)
        val out = if (t > b.dur - 0.35f) clamp01((b.dur - t) / 0.35f) else 1f
        val a = out
        val y = h * 0.3f
        val bh = (if (b.line2.isEmpty()) 104f else 150f) * s
        val sc = easeOutBack(inU)
        gr.fillRectGradient(0f, y - bh * 0.5f * sc, w, y + bh * 0.5f * sc, Col.withA(0xFF0A1030.toInt(), 0.7f * a), Col.withA(0xFF1A0A30.toInt(), 0.7f * a))
        if (b.warn) {
            val st = 18f * s
            for (edge in 0..1) {
                val ey = if (edge == 0) y - bh * 0.5f * sc else y + bh * 0.5f * sc - st
                gr.save(); gr.clipRect(0f, ey, w, ey + st)
                gr.fillRect(0f, ey, w, ey + st, Col.withA(0xFFFFC21A.toInt(), a))
                var x = -st * 2 + ((g.t * 120f * s) % (st * 2))
                while (x < w + st) {
                    tp[0] = x; tp[1] = ey + st; tp[2] = x + st; tp[3] = ey; tp[4] = x + st * 2; tp[5] = ey; tp[6] = x + st; tp[7] = ey + st
                    gr.fillPoly(tp, 4, Col.withA(0xFF1A1020.toInt(), a))
                    x += st * 2f
                }
                gr.restore()
            }
        }
        val s1 = 68f * s * sc
        if (b.line2.isEmpty()) {
            gr.text(b.line1, w * 0.5f, y, s1 * 1.15f, Font.TITLE, b.color, Align.CENTER, s1 * 0.12f, 0xFF1A0A20.toInt(), a)
        } else {
            gr.text(b.line1, w * 0.5f, y - 26f * s * sc, s1, Font.TITLE, b.color, Align.CENTER, s1 * 0.12f, 0xFF1A0A20.toInt(), a)
            gr.text(b.line2, w * 0.5f, y + 36f * s * sc, 34f * s * sc, Font.TITLE, Col.WHITE, Align.CENTER, 5f * s, 0xFF1A0A20.toInt(), a)
        }
    }

    // ------------------------------------------------------------------ overlays
    private fun button(gr: Gfx, cxp: Float, cyp: Float, bw: Float, bh: Float, label: String, c0: Int, c1: Int, out: FloatArray, enabled: Boolean = true) {
        out[0] = cxp - bw / 2; out[1] = cyp - bh / 2; out[2] = cxp + bw / 2; out[3] = cyp + bh / 2
        gr.fillRoundRect(out[0], out[1] + 6f * s, out[2], out[3] + 6f * s, bh * 0.35f, Col.scale(c1, 0.55f))
        drawRR(gr, out[0], out[1], out[2], out[3], bh * 0.35f, if (enabled) c0 else 0xFF6A7488.toInt(), if (enabled) c1 else 0xFF4A5264.toInt())
        gr.fillRoundRect(out[0] + 8f * s, out[1] + 5f * s, out[2] - 8f * s, cyp - 4f * s, bh * 0.3f, 0x30FFFFFF)
        gr.text(label, cxp, cyp, bh * 0.46f, Font.TITLE, Col.WHITE, Align.CENTER, 5f * s, 0x66000000)
    }

    private fun overlayPanel(gr: Gfx, topIn: Float, bottomIn: Float, scale: Float = 1f) {
        gr.fillRect(0f, 0f, w, h, Col.withA(0xFF050A20.toInt(), 0.69f * clamp01(scale * 2f)))
        val cyp = (topIn + bottomIn) * 0.5f
        val top = cyp + (topIn - cyp) * scale; val bottom = cyp + (bottomIn - cyp) * scale
        val l = w * 0.5f - 440f * s * scale; val r = w * 0.5f + 440f * s * scale
        gr.fillRoundRect(l - 3f * s, top - 3f * s, r + 3f * s, bottom + 3f * s, 36f * s, 0xFF06122E.toInt())
        drawRR(gr, l, top, r, bottom, 34f * s, 0xFF3A9CFF.toInt(), 0xFF1E62E6.toInt())
        drawRR(gr, l + 7f * s, top + 7f * s, r - 7f * s, bottom - 7f * s, 28f * s, 0xFF13306E.toInt(), 0xFF081640.toInt())
    }

    private fun drawPause(gr: Gfx) {
        val map = g.host != null
        val top = h * 0.5f - 260f * s - (if (map) 60f * s else 0f); val bot = h * 0.5f + 260f * s + (if (map) 60f * s else 0f)
        overlayPanel(gr, top, bot)
        gr.text("PAUSED", w * 0.5f, top + 90f * s, 80f * s, Font.TITLE, 0xFFFFE14A.toInt(), Align.CENTER, 8f * s, 0xFF1A0A20.toInt())
        gr.text("Level ${g.spec.number}  •  ${g.timeText()} left  •  ${g.target}/${g.spec.targetNeed} blue", w * 0.5f, top + 170f * s, 32f * s, Font.UI, 0xFFCFE0FF.toInt())
        button(gr, w * 0.5f, top + 290f * s, 520f * s, 100f * s, "RESUME", 0xFF5AE07A.toInt(), 0xFF1E9E48.toInt(), btnA)
        button(gr, w * 0.5f, top + 420f * s, 520f * s, 100f * s, "RESTART", 0xFFFFB84A.toInt(), 0xFFE0701A.toInt(), btnB)
        if (map) button(gr, w * 0.5f, top + 545f * s, 520f * s, 90f * s, "LEVEL MAP", 0xFF5AB6FF.toInt(), 0xFF1E62E6.toInt(), btnC) else btnC.fill(-1f)
    }

    private fun drawFail(gr: Gfx) {
        val map = g.host != null
        val top = h * 0.5f - 330f * s - (if (map) 55f * s else 0f); val bot = h * 0.5f + 330f * s + (if (map) 55f * s else 0f)
        val u = easeOutBack(clamp01(overlayTSinceFail() / 0.35f))
        overlayPanel(gr, top, bot, u)
        if (u < 0.6f) return
        gr.text("GAME OVER", w * 0.5f, top + 95f * s, 84f * s, Font.TITLE, 0xFFFF6A5A.toInt(), Align.CENTER, 8f * s, 0xFF1A0A20.toInt())
        gr.text(g.failReason, w * 0.5f, top + 185f * s, 38f * s, Font.TITLE, Col.WHITE, Align.CENTER, 5f * s, 0xFF1A0A20.toInt())
        gr.text("Blue blocks ${g.target}/${g.spec.targetNeed}   •   Score ${fmt(g.score)}", w * 0.5f, top + 262f * s, 32f * s, Font.UI, 0xFFCFE0FF.toInt())
        val cp = g.checkpoint
        gr.text(if (cp > 0) "Continue from checkpoint $cp with full hearts" else "Continue from the start with full hearts", w * 0.5f, top + 318f * s, 28f * s, Font.UI, 0xFF9FB4E0.toInt())
        button(gr, w * 0.5f, top + 430f * s, 560f * s, 110f * s, "CONTINUE", 0xFF5AE07A.toInt(), 0xFF1E9E48.toInt(), btnA)
        button(gr, w * 0.5f, top + 565f * s, 560f * s, 90f * s, "RESTART LEVEL", 0xFFFFB84A.toInt(), 0xFFE0701A.toInt(), btnB)
        if (map) button(gr, w * 0.5f, top + 675f * s, 560f * s, 84f * s, "LEVEL MAP", 0xFF5AB6FF.toInt(), 0xFF1E62E6.toInt(), btnC) else btnC.fill(-1f)
    }
    private fun overlayTSinceFail() = g.stateT - (if (g.failReason.startsWith("CAUGHT")) 0.5f else 1.1f)

    // ------------------------------------------------------------------ level start
    private fun drawCountdown(gr: Gfx) {
        val txt = g.countdownText()
        if (txt.isEmpty()) return
        val ph = clamp01(g.countdownPhase())
        val go = txt == "GO!"
        val pop = easeOutBack(clamp01(ph / 0.25f))
        val a = if (ph < 0.7f) 1f else 1f - (ph - 0.7f) / 0.3f
        val size = (if (go) 150f else 190f) * s * (0.6f + 0.4f * pop) * (if (go) 1f + 0.15f * ph else 1f)
        val cy = h * 0.4f
        if (go) { gr.setAdditive(true); gr.glow(w * 0.5f, cy, 300f * s * (0.6f + ph), Col.withA(0xFFFFD040.toInt(), 0.5f * a)); gr.setAdditive(false) }
        gr.text(txt, w * 0.5f, cy, size, Font.TITLE, if (go) 0xFFFFE14A.toInt() else Col.WHITE, Align.CENTER, size * 0.09f, 0xFF10205A.toInt(), a)
    }

    /** Compact mission card under the top bar right after GO (never covers the path). */
    private fun drawMission(gr: Gfx) {
        if (g.state != GS.PLAY && g.state != GS.INTRO) return
        if (!g.showMission) return
        val t = if (g.state == GS.INTRO) -1f else g.playT - 0.5f
        if (t < 0f || t > 3.6f) return
        val inU = easeOutCubic(clamp01(t / 0.35f)); val outU = clamp01((3.6f - t) / 0.35f)
        val a = inU * outU
        // under the gate, over the far end of the path: never on the gate or near the player
        val extra = g.spec.extraObjective
        val l = w * 0.5f - 300f * s; val r = w * 0.5f + 300f * s; val top = h * 0.34f - 75f * s - (1f - inU) * 30f * s; val b = top + (if (extra.isEmpty()) 150f else 196f) * s
        gr.fillRoundRect(l, top, r, b, 22f * s, Col.withA(0xFF0A1438.toInt(), 0.82f * a))
        gr.strokeRoundRect(l, top, r, b, 22f * s, 3f * s, Col.withA(0xFFFFE14A.toInt(), 0.9f * a))
        gr.text("MISSION", (l + r) * 0.5f, top + 28f * s, 30f * s, Font.TITLE, Col.withA(0xFFFFE14A.toInt(), a), Align.CENTER, 4f * s, Col.withA(0xFF1A0A20.toInt(), a))
        val cube = g.art.targetCube
        val iw = 40f * s
        gr.image(cube, l + 40f * s, top + 50f * s, iw, iw * cube.h / cube.w, a)
        gr.text("Collect ${g.spec.targetNeed} Blue Blocks", l + 96f * s, top + 71f * s, 32f * s, Font.UI, Col.withA(Col.WHITE, a), Align.LEFT)
        // a small gate glyph
        val gx = l + 60f * s; val gy = top + 118f * s
        gr.fillRoundRect(gx - 16f * s, gy - 18f * s, gx + 16f * s, gy + 18f * s, 14f * s, Col.withA(0xFFFFB040.toInt(), a))
        gr.fillRoundRect(gx - 9f * s, gy - 10f * s, gx + 9f * s, gy + 18f * s, 9f * s, Col.withA(0xFFB04AE8.toInt(), a))
        gr.text("Reach the ${g.spec.gateName}", l + 96f * s, gy, 32f * s, Font.UI, Col.withA(Col.WHITE, a), Align.LEFT)
        if (extra.isNotEmpty()) {
            val ey = gy + 46f * s
            gr.fillCircle(gx, ey, 15f * s, Col.withA(0xFFFF6A5A.toInt(), a))
            gr.text("!", gx, ey, 24f * s, Font.TITLE, Col.withA(Col.WHITE, a))
            gr.text(extra, l + 96f * s, ey, 32f * s, Font.UI, Col.withA(Col.WHITE, a), Align.LEFT)
        }
    }

    private fun drawToast(gr: Gfx) {
        val o = g.fx.toasts.firstOrNull() ?: return
        if (g.fx.banners.isNotEmpty()) return
        val u = o.t
        val a = clamp01(u / 0.25f) * clamp01((o.dur - u) / 0.35f)
        // under the gate, never on it
        val y = h * 0.3f + (1f - clamp01(u / 0.25f)) * -20f * s
        gr.text(o.text, w * 0.5f, y, 30f * s, Font.TITLE, Col.withA(o.color, a), Align.CENTER, 5f * s, Col.withA(0xFF10205A.toInt(), a))
        if (o.sub.isNotEmpty()) gr.text(o.sub, w * 0.5f, y + 40f * s, 40f * s, Font.TITLE, Col.withA(Col.WHITE, a), Align.CENTER, 6f * s, Col.withA(0xFF10205A.toInt(), a))
    }

    private val starPath = VPath()
    private fun star(gr: Gfx, x: Float, y: Float, r: Float, filled: Boolean) {
        starPath.ops.clear(); starPath.native = null
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            val rr = if (i % 2 == 0) r else r * 0.47f
            val px = x + cos(a) * rr; val py = y + sin(a) * rr
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        if (filled) {
            gr.setAdditive(true); gr.glow(x, y, r * 1.6f, 0x66FFC020); gr.setAdditive(false)
            gr.fillPath(starPath, Linear(0f, y - r, 0f, y + r, intArrayOf(0xFFFFF4A0.toInt(), 0xFFFFC21A.toInt(), 0xFFE08A00.toInt())))
            gr.strokePath(starPath, 5f * s, Solid(0xFF8A4A00.toInt()))
        } else {
            gr.fillPath(starPath, Solid(0xFF1A2650.toInt()))
            gr.strokePath(starPath, 5f * s, Solid(0xFF3A4A80.toInt()))
        }
    }

    // ------------------------------------------------------------------ results
    private var skipResults = false
    private val starPlayed = BooleanArray(3)
    private val rStars = 1.3f; private val rScore = 2.3f; private val rScoreDur = 1.4f; private val rRewards = 3.9f; private val rButtons = 4.3f
    private fun resultsT() = if (skipResults) 99f else g.stateT
    fun resultsRevealed() = resultsT() >= rButtons

    private fun drawResults(gr: Gfx) {
        val r = g.results ?: return
        val t = resultsT()
        val top = h * 0.5f - 560f * s; val bot = h * 0.5f + 560f * s
        val u = easeOutBack(clamp01(t / 0.35f))
        overlayPanel(gr, top, bot, u)
        if (u < 0.7f) return
        val cxp = w * 0.5f
        val titleU = easeOutBack(clamp01((t - 0.25f) / 0.35f))
        if (titleU > 0f) gr.text("LEVEL COMPLETE!", cxp, top + 88f * s, 78f * s * titleU, Font.TITLE, 0xFFFFE14A.toInt(), Align.CENTER, 8f * s * titleU, 0xFF1A0A20.toInt())
        // objectives
        fun objective(i: Int, done: Boolean, label: String, value: String) {
            val ot = t - 0.55f - i * 0.3f
            if (ot < 0f) return
            val a = clamp01(ot / 0.2f)
            val y = top + 175f * s + i * 58f * s
            val lx = cxp - 360f * s
            gr.fillCircle(lx + 20f * s, y, 20f * s, Col.withA(if (done) 0xFF2EC05A.toInt() else 0xFF6A7488.toInt(), a))
            if (done) check(gr, lx + 20f * s, y, 10f * s, Col.WHITE, a) else gr.fillRoundRect(lx + 11f * s, y - 2.5f * s, lx + 29f * s, y + 2.5f * s, 2f * s, Col.withA(Col.WHITE, a))
            gr.text(label, lx + 56f * s, y, 34f * s, Font.UI, Col.withA(0xFFE8F0FF.toInt(), a), Align.LEFT)
            if (value.isEmpty()) check(gr, cxp + 345f * s, y, 13f * s, Col.withA(0xFF7CFFA8.toInt(), a), 1f)
            else gr.text(value, cxp + 360f * s, y, 34f * s, Font.UI, Col.withA(if (done) 0xFF7CFFA8.toInt() else Col.WHITE, a), Align.RIGHT)
        }
        objective(0, r.targetGot >= g.spec.targetNeed, "Collect ${g.spec.targetNeed} Blue Blocks", "${r.targetGot}/${g.spec.targetNeed}")
        objective(1, true, "Reach the ${g.spec.gateName}", "")
        // stars pop in one by one
        for (i in 0..2) {
            val st = t - rStars - i * 0.3f
            val on = i < r.stars
            val sc = if (st <= 0f) 0f else if (on) easeOutBack(clamp01(st / 0.35f)) else 1f
            val sx = cxp + (i - 1) * 170f * s; val sy = top + 385f * s - (if (i == 1) 26f * s else 0f)
            star(gr, sx, sy, 70f * s, false)
            if (on && sc > 0f) {
                star(gr, sx, sy, 70f * s * sc, true)
                if (st < 0.3f) { gr.setAdditive(true); gr.glow(sx, sy, 160f * s * st / 0.3f, Col.withA(0xFFFFE070.toInt(), 1f - st / 0.3f)); gr.setAdditive(false) }
            }
            if (on && st > 0f && !starPlayed[i]) { starPlayed[i] = true; g.platform.sound(Sfx.STAR, 1f, 1f + i * 0.12f) }
        }
        // why: the star goals, so the rating is never a mystery
        if (t > rStars + 0.9f) {
            val a = clamp01((t - rStars - 0.9f) / 0.3f)
            // three goals side by side, each with a drawn check mark when it was met
            val parts = arrayOf("Coins ${r.coinsCollected}/${r.coinGoal}", "${r.timeLeft}s left (${g.spec.timeStar}s)", if (r.noGameOver) "No game over" else "Game over used")
            val met = booleanArrayOf(r.coinGoalMet, r.timeGoalMet, r.noGameOver)
            val ts = 24f * s; val mark = 24f * s; val gap = 34f * s
            val widths = FloatArray(3) { gr.textWidth(parts[it], ts, Font.UI) + (if (met[it]) mark else 0f) }
            var x = cxp - (widths.sum() + gap * 2f) * 0.5f
            val y = top + 462f * s
            for (i in 0..2) {
                gr.text(parts[i], x, y, ts, Font.UI, Col.withA(if (met[i]) 0xFFCFE0FF.toInt() else 0xFF8090B0.toInt(), a), Align.LEFT)
                if (met[i]) check(gr, x + widths[i] - mark * 0.4f, y, 8f * s, 0xFF7CFFA8.toInt(), a)
                x += widths[i] + gap
            }
        }
        // score counts up
        val su = clamp01((t - rScore) / rScoreDur)
        val shown = (r.totalScore * easeOutCubic(su)).toInt()
        if (t >= rScore) {
            val tick = (shown / 250)
            if (su < 1f && tick != lastScoreTick) { lastScoreTick = tick; g.platform.sound(Sfx.SCORE, 0.5f, 1f + su * 0.5f) }
            gr.text("SCORE", cxp, top + 505f * s, 34f * s, Font.TITLE, 0xFFCFE0FF.toInt())
            val pulseK = if (su >= 1f) 1f + 0.06f * sin(clamp01((t - rScore - rScoreDur) / 0.3f) * PI.toFloat()) else 1f
            gr.text(fmt(shown), cxp, top + 572f * s, 84f * s * pulseK, Font.TITLE, Col.WHITE, Align.CENTER, 7f * s, 0xFF1A0A20.toInt())
            gr.text("Level ${fmt(r.levelScore)}  •  Time +${fmt(r.timeBonus)}  •  Hearts +${fmt(r.heartBonus)}" + (if (r.targetBonus > 0) "  •  Target +${fmt(r.targetBonus)}" else ""),
                cxp, top + 640f * s, 26f * s, Font.UI, 0xFF9FB4E0.toInt())
        }
        // rewards
        val ru = t - rRewards
        if (ru > 0f) {
            val k = easeOutBack(clamp01(ru / 0.35f))
            val y = top + 735f * s
            gr.text("REWARDS", cxp, top + 690f * s, 30f * s, Font.TITLE, 0xFFFFE14A.toInt())
            val ci = g.art.coinIcon; val gi = g.art.gem; val tc = g.art.targetCube
            val isz = 56f * s * k
            gr.image(ci, cxp - 330f * s, y - isz * 0.5f, isz, isz * ci.h / ci.w)
            gr.text("+${fmt(r.rewardCoins + r.earnedCoins)}", cxp - 262f * s, y, 44f * s * k, Font.TITLE, 0xFFFFE14A.toInt(), Align.LEFT, 5f * s, 0xFF1A0A20.toInt())
            gr.image(gi, cxp - 20f * s, y - isz * 0.5f, isz, isz * gi.h / gi.w)
            gr.text("+${r.gemReward + r.earnedGems}", cxp + 48f * s, y, 44f * s * k, Font.TITLE, 0xFFE59CFF.toInt(), Align.LEFT, 5f * s, 0xFF1A0A20.toInt())
            gr.image(tc, cxp + 175f * s, y - isz * 0.5f, isz, isz * tc.h / tc.w)
            gr.text("${r.targetGot}", cxp + 243f * s, y, 44f * s * k, Font.TITLE, 0xFF9FDBFF.toInt(), Align.LEFT, 5f * s, 0xFF1A0A20.toInt())
            gr.text("Coins ${r.coinsCollected}/${r.coinTotal}   •   Mystery ${r.mystery}/${r.mysteryTotal}   •   Hearts ${r.hearts}   •   Time left ${r.timeLeft}s",
                cxp, y + 64f * s, 25f * s, Font.UI, 0xFF9FB4E0.toInt())
        }
        if (t >= rButtons) {
            val k = easeOutBack(clamp01((t - rButtons) / 0.3f))
            button(gr, cxp, bot - 205f * s, 620f * 0.9f * s * k + 1f, 120f * s * k + 1f, "NEXT LEVEL", 0xFF5AE07A.toInt(), 0xFF1E9E48.toInt(), btnA)
            if (g.host != null) {
                button(gr, cxp - 165f * s, bot - 80f * s, 300f * s * k + 1f, 86f * s * k + 1f, "REPLAY", 0xFF5AB6FF.toInt(), 0xFF1E62E6.toInt(), btnB)
                button(gr, cxp + 165f * s, bot - 80f * s, 300f * s * k + 1f, 86f * s * k + 1f, "LEVEL MAP", 0xFFFFB84A.toInt(), 0xFFE0701A.toInt(), btnC)
            } else {
                button(gr, cxp, bot - 80f * s, 420f * s * k + 1f, 86f * s * k + 1f, "REPLAY", 0xFF5AB6FF.toInt(), 0xFF1E62E6.toInt(), btnB)
                btnC.fill(-1f)
            }
        } else { btnA.fill(-1f); btnB.fill(-1f); btnC.fill(-1f) }
    }

}
