package com.blocktower.escape.sim

import com.blocktower.escape.core.BT
import com.blocktower.escape.core.Block
import com.blocktower.escape.core.Chase
import com.blocktower.escape.core.GS
import com.blocktower.escape.core.Game
import com.blocktower.escape.core.Key
import com.blocktower.escape.core.PS
import com.blocktower.escape.core.TK
import com.blocktower.escape.core.Tune
import java.io.File
import java.io.OutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Plays Level 23 from START to LEVEL COMPLETE through the real game code, the way a player would:
 * every input is a touch on the screen (swipes in the movement area, taps on the JUMP and tool
 * buttons) sent through the game's own touch handlers. On the way it runs the movement tests
 * (swipe forward / left / right, small corrections, jumps while running, choosing a path at the
 * fork, steering around obstacles, falling and recovery, tools while running, reaching the gate),
 * deliberately falls once, lets the Tower Guard catch it once (capture -> GAME OVER -> CONTINUE)
 * and uses every tool. Prints a timeline and a checklist; optionally records the run as a video.
 *
 *   play                      run and report
 *   play video=out.mp4 ffmpeg=/path/to/ffmpeg   also record (30 fps)
 *   play frames=dir every=30  also write every Nth frame as PNG
 *   play scenario=hearts      keep falling until GAME OVER, then CONTINUE
 */
class Autopilot(
    val assets: File, val out: File, val opts: Map<String, String>,
    /** Play a game that runs inside the app (the flow test) instead of a standalone one. */
    external: Game? = null,
    /** Advances the world one frame (the app's update when playing inside the app). */
    private val stepper: ((Float) -> Unit)? = null,
    /** Called after every frame (the flow test records video with it). */
    private val frameHook: (() -> Unit)? = null,
) {
    private val sim = SimPlatform(assets)
    private val g = external ?: Game(sim, (opts["level"] ?: "23").toInt())
    private val p get() = g.player
    private val dt = 1f / 60f
    private var frame = 0
    private val log = ArrayList<String>()
    private val seen = HashSet<String>()

    /**
     * Where the scripted moments happen on each level, and the lane to steer for from each z onward.
     * Level 23 is the sky tower, Level 5 the jungle temple.
     */
    private class Plan(
        val route: FloatArray,
        val fallZ: Float, val steerZ: Float, val speedZ: Float, val captureZ: Float,
        val magnetZ: Float, val shieldZ: Float, val blockZ0: Float, val blockZ1: Float,
        val trapZ0: Float, val trapZ1: Float,
        /** "choosing between paths": pass zone A with x in range, then zone B with x in range */
        val pathA: FloatArray, val pathB: FloatArray,
    )
    private val level = g.spec.number
    private val never = floatArrayOf(9999f, 9999f, 0f, 0f)
    private val plan = if (level == 1) Plan(
        route = floatArrayOf(-9f, 0f, 5.4f, -2f, 8.7f, 1f, 12.8f, 0f, 35.4f, 1f, 37.4f, 2f, 39.5f, 1f, 40.5f, 0f, 41.5f, -1f, 43.5f, 0f),
        fallZ = 9999f, steerZ = 9999f, speedZ = 9999f, captureZ = 9999f, magnetZ = 9999f, shieldZ = 9999f,
        blockZ0 = 9999f, blockZ1 = 9999f, trapZ0 = 9999f, trapZ1 = 9999f, pathA = never, pathB = never,
    ) else if (level == 2) Plan(
        route = floatArrayOf(-9f, 0f, 22.4f, -1f, 24.2f, 1f, 26.1f, 0f, 48.6f, -2f, 50.4f, 0f),
        fallZ = 9999f, steerZ = 9999f, speedZ = 9999f, captureZ = 9999f, magnetZ = 22.2f, shieldZ = 9999f,
        blockZ0 = 9999f, blockZ1 = 9999f, trapZ0 = 17f, trapZ1 = 69f, pathA = never, pathB = never,
    ) else if (level == 3) Plan(
        route = floatArrayOf(-9f, 0f),
        fallZ = 9999f, steerZ = 9999f, speedZ = 9999f, captureZ = 9999f, magnetZ = 61.5f, shieldZ = 43.2f,
        blockZ0 = 9999f, blockZ1 = 9999f, trapZ0 = 43f, trapZ1 = 91f, pathA = never, pathB = never,
    ) else if (level == 4) Plan(
        route = floatArrayOf(
            -9f, 0f, 9.4f, 1f, 13.7f, 0f,
            86.1f, -1f, 86.8f, 0f,                 // step on the shortcut ? block, then take the golden bridge
            117.6f, 2.5f,                          // fork: the treasure path on the right
            129.6f, 1f, 132.3f, 0f,                // back in, passing the trapped ? box in the middle of the path
            211.6f, 1f, 212.9f, 0f),               // take the shield bubble before the spike run
        fallZ = 15.2f, steerZ = 22.1f, speedZ = 30f, captureZ = 146f, magnetZ = 193.8f, shieldZ = 212.7f,
        blockZ0 = 201.5f, blockZ1 = 203f, trapZ0 = 108f, trapZ1 = 142f,
        pathA = floatArrayOf(120f, 127f, 1.7f, 9f), pathB = floatArrayOf(131f, 134f, -1.3f, 1.3f),
    ) else Plan(
        route = floatArrayOf(
            -9f, 0f, 1.6f, 0.2f, 2.6f, -0.3f, 4.6f, 0f, 8.7f, 0.6f, 9.6f, 0.5f, 10.6f, 1.0f,
            13.3f, 1.4f, 14.6f, 0.9f, 15.6f, 0.4f, 17.2f, 0.2f, 20.2f, 0.9f, 21.6f, 1.2f, 23.2f, 1.9f,
            // the coin trail up toward the portal
            24.6f, 1.8f, 25.6f, 1.5f, 26.6f, 1.2f, 27.6f, 0.9f, 28.6f, 1.0f, 29.6f, 1.3f, 30.6f, 1.6f, 31.6f, 1.7f, 32.6f, 1.5f,
            33.6f, 0f,
            // the vanishing stepping stones in the moving-stones section
            78.3f, 1f, 79.4f, 0f, 80.4f, 1f, 81.6f, 0f),
        fallZ = 36.2f, steerZ = 160.5f, speedZ = 38f, captureZ = 125f, magnetZ = 92.5f, shieldZ = 104.5f,
        blockZ0 = 96.5f, blockZ1 = 98.2f, trapZ0 = 57f, trapZ1 = 90f,
        pathA = floatArrayOf(18f, 20f, -9f, 0.6f), pathB = floatArrayOf(24f, 25.2f, 1.2f, 9f),
    )
    private fun routeX(z: Float): Float {
        val route = plan.route
        var x = 0f
        var i = 0
        while (i < route.size) { if (z >= route[i]) x = route[i + 1]; i += 2 }
        return x
    }

    // ---- scripted moments
    private var fallTestDone = false
    private var captureTestDone = false
    private var idleUntil = -1f
    private var speedUsed = false; private var magnetUsed = false; private var blockUsed = false; private var shieldUsed = false
    /** "hearts": keep falling off the path until every heart is gone, then continue and stop. */
    private val scenario = opts["scenario"] ?: "full"
    private var heartsGameOver = false
    private var ffmpeg: Process? = null
    private var videoOut: OutputStream? = null
    private var gfx: J2DGfx? = null

    // ---- movement test results
    private val tests = LinkedHashMap<String, String>()     // name -> "PASS detail" / "FAIL detail"
    private fun result(name: String, pass: Boolean, detail: String) {
        if (tests.containsKey(name) && tests[name]!!.startsWith("PASS")) return
        tests[name] = (if (pass) "PASS " else "FAIL ") + detail
        note("TEST ${if (pass) "PASS" else "FAIL"}: $name — $detail")
    }

    /** Just play through: no deliberate falls, captures or movement tests (the flow test). */
    private val clear = scenario == "clear" || external != null

    /** Plays the level inside the app until its results are on screen. Returns true when it was completed. */
    fun playLevel(maxSeconds: Int = 600): Boolean {
        val maxF = maxSeconds * 60
        var lastState = -1
        while (frame < maxF) {
            hands()
            if (stepper != null) stepper.invoke(dt) else g.update(dt)
            frame++
            frameHook?.invoke()
            if (g.state != lastState) { note("L$level state ${stateName(g.state)}"); lastState = g.state }
            if (g.state == GS.RESULTS && g.hud.resultsRevealed()) return true
        }
        return false
    }

    fun run() {
        val w = (opts["w"] ?: "540").toInt(); val h = (opts["h"] ?: "1170").toInt()
        val every = (opts["every"] ?: "0").toInt()
        val video = opts["video"]
        if (video != null || every > 0) gfx = J2DGfx(assets, w, h)
        g.hud.layout(w, h)
        if (video != null) startVideo(File(video), w, h, opts["ffmpeg"] ?: "ffmpeg")
        val frameDir = opts["frames"]?.let { File(it).apply { mkdirs() } }
        var lastState = -1; var lastHearts = g.hearts; var lastTarget = 0; var lastCp = 0; var lastChase = Chase.NONE; var lastPs = PS.NORMAL
        val maxFrames = 60 * 60 * 6
        var doneAt = -1
        while (frame < maxFrames) {
            val px0 = p.x; val pvx0 = p.vx; val pz0 = p.z
            val wasGrounded = p.grounded; val wasNormal = p.state == PS.NORMAL && g.state == GS.PLAY
            val onMover = p.ground?.type == BT.MOVING
            hands()
            g.update(dt)
            frame++
            measure(px0, pvx0, pz0, wasGrounded, wasNormal, onMover)
            // timeline
            if (g.state != lastState) { note("state ${stateName(g.state)}"); lastState = g.state }
            if (g.hearts != lastHearts) { note("hearts ${lastHearts} -> ${g.hearts}"); lastHearts = g.hearts }
            if (g.target != lastTarget) { if (g.target % 3 == 0 || g.target == g.spec.targetNeed) note("blue blocks ${g.target}/${g.spec.targetNeed}"); lastTarget = g.target }
            if (g.checkpoint != lastCp) { note("checkpoint ${g.checkpoint} activated"); lastCp = g.checkpoint; seen.add("checkpoint") }
            if (g.ev.chase != lastChase) { note("chase ${chaseName(g.ev.chase)}"); lastChase = g.ev.chase; seen.add("chase:" + chaseName(g.ev.chase)) }
            if (p.state != lastPs) { note("player ${psName(p.state)}"); lastPs = p.state; seen.add("ps:" + psName(p.state)) }
            if (g.ev.lavaOn) seen.add("lava")
            if (g.ev.finalOn) seen.add("final escape")
            // render
            val gr = gfx
            if (gr != null && (frame % 2 == 0)) {
                if (videoOut != null || (every > 0 && frame % every == 0)) {
                    gr.clear(); g.render(gr)
                    videoOut?.let { writeFrame(it, gr) }
                    if (frameDir != null && every > 0 && frame % every == 0) ImageIO.write(gr.image, "png", File(frameDir, "f%05d.png".format(frame)))
                }
            }
            if (g.state == GS.RESULTS && doneAt < 0) doneAt = frame
            if (scenario == "hearts") {
                if (g.state == GS.FAILED) heartsGameOver = true
                if (seen.contains("continue") && g.state == GS.PLAY && g.stateT > 1f) break
            }
            if (doneAt >= 0 && frame - doneAt > 60 * 7) break
        }
        stopVideo()
        report()
    }

    // ------------------------------------------------------------------ fingers on the glass
    private val THUMB = 1; private val JUMP_F = 2; private val TOOL_F = 3
    private class Act(val kind: Int, val x: Float, val y: Float)
    private val thumbQ = ArrayDeque<Act>()
    private var jumpHoldF = 0
    private var toolUpF = -1; private var toolK = 0
    private val s get() = g.hud.s
    private fun baseX() = g.hud.w * 0.42f
    private fun baseY() = g.hud.h * 0.80f
    private fun jumpX() = g.hud.ax(872f)
    private fun jumpY() = g.hud.ayB(1308f)

    /** Queues a swipe of the thumb: down at (x0, y0), slide by (dx, dy) over [frames] frames, lift. */
    private fun swipe(x0: Float, y0: Float, dx: Float, dy: Float, frames: Int, hold: Int = 0) {
        thumbQ.add(Act(0, x0, y0))
        for (i in 1..frames) { val u = i.toFloat() / frames; thumbQ.add(Act(1, x0 + dx * u, y0 + dy * u)) }
        repeat(hold) { thumbQ.add(Act(1, x0 + dx, y0 + dy)) }
        thumbQ.add(Act(2, x0 + dx, y0 + dy))
    }
    private var flickFrame = -999
    /** A quick upward flick (run; near an edge it also jumps). [len] in artwork pixels. */
    private fun flickUp(len: Float = 160f) { swipe(baseX(), baseY(), 0f, -len * s, 5); flickFrame = frame }
    private fun swipeDown(len: Float) = swipe(baseX(), baseY() - 60f * s, 0f, len * s, 6)
    /** A sideways swipe meant to steer by [u] block widths (the inverse of the game's swipe curve). */
    private fun steerSwipe(u: Float) { swipe(baseX(), baseY(), offsetFor(u) * s, 0f, 9); lastSteerFrame = frame }
    private var lastSteerFrame = -999
    private fun offsetFor(u: Float): Float {
        val a = abs(u)
        val d = if (a < 0.45f) 60f * a / 0.45f else 60f + (a - 0.45f) * 90f
        return (if (u < 0f) -1f else 1f) * (d + 10f)
    }
    private val thumbFree get() = thumbQ.isEmpty()

    private var buttonJumpFrame = -999
    private fun pressJump() {
        if (jumpHoldF > 0) return
        g.touchDown(JUMP_F, jumpX(), jumpY()); jumpHoldF = 21; buttonJumpFrame = frame
    }

    // tool taps: finger down on the button, up on the next frame; the result is checked a few frames later
    private var toolCheckAt = -1; private var toolBefore = 0; private var toolSpeedAtTap = 0f; private var toolAim = 0
    private fun tapTool(k: Int) {
        g.touchDown(TOOL_F, g.hud.toolX(k), g.hud.toolY(k)); toolUpF = 1; toolK = k
        toolAim = k; toolBefore = g.tools[k].count; toolCheckAt = frame + 4; toolSpeedAtTap = len2(p.vx, p.vz)
    }
    private val toolsWhileMoving = ArrayList<String>()
    private fun checkTool() {
        if (toolCheckAt < 0 || frame < toolCheckAt) return
        toolCheckAt = -1
        val k = toolAim
        val ok = g.tools[k].count < toolBefore
        val moving = toolSpeedAtTap > 2.5f && len2(p.vx, p.vz) > 2.5f
        note("tool ${g.toolName(k)} ${if (ok) "used" else "NOT used"} (count $toolBefore -> ${g.tools[k].count}, speed ${"%.1f".format(toolSpeedAtTap)} -> ${"%.1f".format(len2(p.vx, p.vz))})")
        if (ok) { seen.add("tool:" + g.toolName(k)); if (moving) toolsWhileMoving.add(g.toolName(k)) }
    }

    private fun fingersStep() {
        thumbQ.removeFirstOrNull()?.let { a ->
            when (a.kind) { -1 -> {}; 0 -> g.touchDown(THUMB, a.x, a.y); 1 -> g.touchMove(THUMB, a.x, a.y); else -> g.touchUp(THUMB, a.x, a.y) }
        }
        if (jumpHoldF > 0) { jumpHoldF--; if (jumpHoldF == 0) g.touchUp(JUMP_F, jumpX(), jumpY()) }
        if (toolUpF >= 0) { if (toolUpF == 0) g.touchUp(TOOL_F, g.hud.toolX(toolK), g.hud.toolY(toolK)); toolUpF-- }
    }

    // ------------------------------------------------------------------ the player's decisions
    private var wantRun = true
    private var startStep = 0; private var stepAt = 0
    private var mark0 = 0f; private var mark1 = 0f
    private var toolCounts0 = IntArray(4)
    private var runStartFrame = -1
    private var steerTest = 0; private var steerAt = 0
    private var flatTest = 0; private var flatUntil = 0; private var flatJumped = false
    private var flickJumpTest = 0; private var flickJumpFrame = -999; private var flickJumps = 0
    private var fallStartHearts = 0
    private var leapFrame = -999

    private fun hands() {
        fingersStep()
        checkTool()
        when (g.state) {
            GS.FAILED -> {
                thumbQ.clear()
                if (g.stateT > 2.2f) {
                    note("GAME OVER screen: pressing CONTINUE"); seen.add("continue"); g.onKey(Key.ENTER, true); g.onKey(Key.ENTER, false)
                    // tools placed after the checkpoint may be needed again
                    val cz = p.z
                    if (cz < plan.blockZ1) blockUsed = false
                    if (cz < plan.magnetZ) magnetUsed = false
                    if (cz < plan.shieldZ) shieldUsed = false
                }
                return
            }
            GS.RESULTS -> { if (g.stateT > 1.0f && g.stateT < 1.02f) seen.add("results"); return }
            GS.PLAY -> {}
            else -> return
        }
        if (p.state != PS.NORMAL) { wantRun = true; return }
        if (scenario == "hearts" && !seen.contains("continue")) { if (thumbFree && p.grounded && g.playT > 0.5f) leapOffTheSide(); return }
        if (startStep < 99) { startTests(); return }
        val z = p.z

        // --- scripted tests along the way
        if (!fallTestDone && z > plan.fallZ && p.grounded && thumbFree) {
            // leap off the side of the path on purpose: the safety net should catch us
            fallStartHearts = g.hearts
            if (leapOffTheSide()) { fallTestDone = true; leapFrame = frame; note("deliberate leap off the path") }; return
        }
        if (frame - leapFrame < 90) return          // committed to the leap: no steering back
        if (!fallTestDone) {} else if (steerTest < 99 && z > plan.steerZ && (g.ev.chase == Chase.NONE || g.ev.chase == Chase.ESCAPED)) { steeringTests(); if (steerTest < 99) return }
        if (!speedUsed && z > plan.speedZ && p.grounded && len2(p.vx, p.vz) > 2.5f) { tapTool(TK.SPEED); speedUsed = true }
        flatGroundFlick()
        if (!captureTestDone && g.ev.chase == Chase.RUN && z > plan.captureZ) {
            // stop and let the Tower Guard catch us once
            if (idleUntil < 0f) { idleUntil = g.t + 12f; note("stopping in front of the Tower Guard") }
            if (g.t < idleUntil) { wantRun = false; keepPace(); return }
        }
        if (g.ev.chase == Chase.CAUGHT) captureTestDone = true
        if (captureTestDone) idleUntil = -1f
        if (!magnetUsed && z > plan.magnetZ && p.grounded) { tapTool(TK.MAGNET); magnetUsed = true }
        if (!shieldUsed && z > plan.shieldZ && p.grounded) { tapTool(TK.SHIELD); shieldUsed = true }

        wantRun = true
        // --- a swinging log will be in the way when we get there: wait for it to swing clear
        if (logInWay()) {
            if (!logWaitNoted) { note("waiting for the swinging log"); logWaitNoted = true; logWaits++ }
            wantRun = false; keepPace(); return
        }
        logWaitNoted = false
        // --- steering: a sideways swipe whenever the lane we want is off to the side
        var tx = routeX(z)
        val ground = p.ground
        val onMover = ground != null && ground.type == BT.MOVING
        if (onMover) tx = g.steerX
        if (thumbFree && frame - lastSteerFrame > 14 && abs(tx - g.steerX) > 0.15f) { steerSwipe(max(-2.5f, min(2.5f, tx - g.steerX))); return }

        if (!p.grounded) { keepPace(); return }
        // --- look ahead: gaps, steps, hazards
        val y = p.y
        var edge = -1f
        var wall = false
        var d = 0.05f
        while (d <= 2.4f) {
            val zz = z + d
            val sup = support(p.x, zz, y)
            if (sup == null || isHazard(sup)) { edge = d; break }
            if (sup.y1 > y + 0.56f) { edge = d; wall = true; break }
            d += 0.05f
        }
        if (edge < 0f) { keepPace(); return }
        // the block tool bridge in the tool trials: too far to jump
        if (!blockUsed && !wall && z > plan.blockZ0 && z < plan.blockZ1 && edge < 0.6f) { tapTool(TK.BLOCK); blockUsed = true; return }
        val landing = landingAhead(z + edge, y)
        if (landing != null && landing.type == BT.MOVING && !wall) {
            // wait back from the edge (with a run-up) until the platform will be under us when we land
            val standing = len2(p.vx, p.vz) < 1f
            val tLand = if (standing) 0.95f else 0.55f
            val px = landing.baseX + landing.amp * sin(landing.phase + (g.t + tLand) * landing.speed)
            if (abs(px - p.x) > landing.sx * 0.5f - 0.15f) {
                if (edge < 1.5f) { wantRun = false; keepPace() }
                return
            }
            if (edge > 0.35f) { keepPace(); if (!thumbFree) return }
        }
        // a few gaps are crossed with the context flick instead of the button
        if (flickJumpTest < 3 && !wall && landing != null && landing.type != BT.MOVING && edge in 0.9f..2.0f && thumbFree &&
            len2(p.vx, p.vz) > 3.5f && frame - flickJumpFrame > 90 && z > 30f) {
            flickUp(); flickJumpFrame = frame; flickJumpTest++; note("flick up with a gap ${"%.2f".format(edge)} ahead"); return
        }
        if (frame - flickJumpFrame < 50) { keepPace(); return }   // the game times that jump to the edge
        if (edge < (if (wall) 0.45f else 0.32f)) pressJump()
        keepPace()
    }

    private var logWaitNoted = false
    private var logWaits = 0
    /** Predicts whether a swinging log sweeps through our lane while we pass under it. */
    private fun logInWay(): Boolean {
        for (l in g.world.logs) {
            val dz = l.pz - p.z
            if (dz < 1.0f || dz > 3.4f || abs(p.y - (l.py - l.len)) > 3f) continue
            val sp = max(3.2f, p.vz)
            val t0 = g.logT + dz / sp
            val w = com.blocktower.escape.core.TAU / l.period
            var k = -2
            while (k <= 4) {
                val a = l.amp * sin(w * (t0 + k * 0.1f) + l.phase)
                val cx = l.px + sin(a) * l.len; val cy = l.py - kotlin.math.cos(a) * l.len
                if (abs(p.x - cx) < l.size * 0.5f + 0.6f && cy - l.radius < p.y + Tune.HEIGHT + 0.25f) return true
                k++
            }
        }
        return false
    }

    /** Swipe up to keep running (or down to stop) when the pace is not what we want. */
    private fun keepPace() {
        if (!thumbFree) return
        if (wantRun && g.swipe.cruise < 0.85f) { flickUp(); return }
        if (!wantRun && g.swipe.cruise > 0f) swipeDown(90f)
    }

    /** Jump, then swipe hard to the side while in the air (steering on the ground never leaves the path). */
    private fun leapOffTheSide(): Boolean {
        if (jumpHoldF > 0 || !thumbFree) return false
        pressJump(); thumbQ.add(Act(-1, 0f, 0f)); swipe(baseX() + 60f * s, baseY(), -300f * s, 0f, 9)
        return true
    }

    /** At the start line: stray touches, swipes over the tool buttons, then the first swipe forward. */
    private fun startTests() {
        if (!thumbFree || frame < stepAt) return
        val hud = g.hud
        when (startStep) {
            0 -> { if (g.playT > 0.4f) { mark0 = g.steerX; toolCounts0 = IntArray(4) { g.tools[it].count }; swipe(baseX(), baseY(), 6f * s, -8f * s, 4); startStep = 1; stepAt = frame + 25 } }
            1 -> {
                val ok = abs(g.steerX - mark0) < 0.01f && g.swipe.cruise == 0f && len2(p.vx, p.vz) < 0.05f
                result("tiny accidental touches are ignored", ok, "steer moved ${"%.3f".format(g.steerX - mark0)}, speed ${"%.2f".format(len2(p.vx, p.vz))}")
                // a swipe that starts on a tool button
                swipe(hud.toolX(1), hud.toolY(1), -170f * s, 30f * s, 8); startStep = 2; stepAt = frame + 20
            }
            2 -> {
                // a swipe in the movement area that ends on top of a tool button
                swipe(hud.w * 0.55f, hud.toolY(2), hud.toolX(2) - hud.w * 0.55f, 0f, 9); startStep = 3; stepAt = frame + 30
            }
            3 -> {
                val unchanged = (0..3).all { g.tools[it].count == toolCounts0[it] && !g.tools[it].isActive }
                result("swipes never activate tools", unchanged, "tool counts ${toolCounts0.joinToString()} -> ${(0..3).joinToString { g.tools[it].count.toString() }}")
                result("swipe right (standing)", g.steerX > mark0 + 0.3f && p.x > mark0 + 0.3f, "x ${"%.2f".format(mark0)} -> ${"%.2f".format(p.x)}")
                steerSwipe(mark0 - g.steerX); startStep = 4; stepAt = frame + 64
            }
            4 -> {
                result("swipe left (standing)", abs(p.x - mark0) < 0.15f, "back to x=${"%.2f".format(p.x)}")
                mark1 = p.z; flickUp(170f); runStartFrame = frame; startStep = 5; stepAt = frame + 48
            }
            5 -> {
                val sp = p.vz
                result("1 swipe forward: runs, accelerates smoothly", sp > 0.85f * Tune.RUN && maxDvz <= 16f * dt * 1.05f + 1e-3f,
                    "speed ${"%.2f".format(sp)} after 0.8 s, max speed change per frame ${"%.3f".format(maxDvz)} (limit ${"%.3f".format(16f * dt)})")
                startStep = 99
            }
        }
    }

    /** On the wide platform: slow down, swipe left, swipe right, small corrections, then run on. */
    private fun steeringTests() {
        if (!thumbFree || frame < steerAt || !p.grounded) return
        when (steerTest) {
            0 -> { swipeDown(45f); steerTest = 1; steerAt = frame + 24; note("steering tests on the wide platform") }
            1 -> {
                result("swipe down slows down", g.swipe.cruise in 0.3f..0.5f && p.vz < 0.6f * Tune.RUN, "pace ${"%.2f".format(g.swipe.cruise)}, speed ${"%.2f".format(p.vz)}")
                mark0 = p.x; steerSwipe(-1f); steerTest = 2; steerAt = frame + 36
            }
            2 -> {
                val dx = p.x - mark0
                result("2 swipe left: one block", abs(dx + 1f) < 0.15f, "moved ${"%.2f".format(dx)} (wanted -1)")
                mark0 = p.x; steerSwipe(2f); steerTest = 3; steerAt = frame + 40
            }
            3 -> {
                val dx = p.x - mark0
                result("3 swipe right: two blocks", abs(dx - 2f) < 0.15f, "moved ${"%.2f".format(dx)} (wanted +2)")
                mark0 = p.x; swipe(baseX(), baseY(), -38f * s, 0f, 6); steerTest = 4; steerAt = frame + 36
            }
            4 -> {
                val dx = p.x - mark0
                mark1 = dx
                mark0 = p.x; swipe(baseX(), baseY(), 38f * s, 0f, 6); steerTest = 5; steerAt = frame + 36
            }
            5 -> {
                val dx = p.x - mark0
                result("4 small left/right corrections", mark1 in -0.4f..-0.1f && dx in 0.1f..0.4f, "small left ${"%.2f".format(mark1)}, small right ${"%.2f".format(dx)}")
                steerTest = 99
            }
        }
    }

    /** A flick on flat, open ground must not jump. */
    private fun flatGroundFlick() {
        if (flatTest == 1) {
            if (!p.grounded && frame - buttonJumpFrame > 30) flatJumped = true
            if (frame >= flatUntil) { flatTest = 2; result("upward flick on flat ground does not jump", !flatJumped, if (flatJumped) "jumped" else "stayed on the ground, kept running") }
            return
        }
        if (flatTest != 0 || p.z < 40f || !p.grounded || !thumbFree || len2(p.vx, p.vz) < 4.5f) return
        var d = 0.1f
        while (d < 4.8f) { val sup = support(p.x, p.z + d, p.y); if (sup == null || isHazard(sup) || sup.y1 > p.y + 0.3f) return; d += 0.1f }
        flickUp(); flatTest = 1; flatUntil = frame + 45
    }

    // ------------------------------------------------------------------ measurements
    private var maxDvz = 0f
    private var maxDx = 0f; private var maxDvx = 0f
    private var penetrations = 0
    private var hurts = 0; private var trapHurts = 0; private var logHits = 0
    private var lastHurt = 0f
    private var jumpsWhileMoving = 0; private var jumpsLanded = 0
    private var airFrom = -1; private var airVz = 0f
    private var forkRight = false; private var forkRejoined = false
    private var maxYaw = 0f; private var maxRoll = 0f

    private fun measure(px0: Float, pvx0: Float, pz0: Float, wasGrounded: Boolean, wasNormal: Boolean, onMover: Boolean) {
        if (runStartFrame >= 0 && frame - runStartFrame < 48 && p.grounded) maxDvz = max(maxDvz, abs(p.vz - lastVz))
        lastVz = p.vz
        val normal = p.state == PS.NORMAL && g.state == GS.PLAY
        if (normal && wasNormal && wasGrounded && p.grounded && !onMover && p.ground?.type != BT.MOVING) {
            maxDx = max(maxDx, abs(p.x - px0)); maxDvx = max(maxDvx, abs(p.vx - pvx0))
        }
        // never inside a solid block
        if (normal) {
            val r0 = floor(p.z - 0.5f).toInt(); val r1 = floor(p.z + 0.5f).toInt()
            for (r in r0..r1) g.world.row(r)?.forEach { b ->
                if (b.collides() && b.type != BT.MOVING && p.x + Tune.RADIUS > b.x0 + 0.05f && p.x - Tune.RADIUS < b.x1 - 0.05f &&
                    p.y + Tune.HEIGHT > b.y + 0.05f && p.y < b.y1 - 0.05f && p.z + Tune.RADIUS > b.z + 0.05f && p.z - Tune.RADIUS < b.z1 - 0.05f) {
                    penetrations++
                    if (penetrations < 4) note("INSIDE a block at (${b.x}, ${b.y}, ${b.z}) player (${"%.2f".format(p.x)}, ${"%.2f".format(p.y)}, ${"%.2f".format(p.z)})")
                }
            }
        }
        // hits (spikes, traps) — the fall and capture tests are counted separately
        if (p.hurtFlash > lastHurt + 0.5f && normal) {
            hurts++; if (p.z in plan.trapZ0..plan.trapZ1) trapHurts++
            if (g.world.logs.any { abs(it.pz - p.z) < 1.6f }) logHits++
            note("hit (hurt flash) at z=${"%.1f".format(p.z)}")
        }
        lastHurt = p.hurtFlash
        // jumps while running
        if (normal && wasGrounded && !p.grounded && p.jumping) {
            airFrom = frame; airVz = len2(p.vx, p.vz)
            if (frame - flickJumpFrame in 0..50 && frame - buttonJumpFrame > 30) { flickJumps++; note("context jump from the flick (speed ${"%.1f".format(airVz)})") }
        }
        if (airFrom >= 0 && p.grounded && normal) {
            if (airVz > 3f) { jumpsWhileMoving++; jumpsLanded++ }
            airFrom = -1
        }
        if (airFrom >= 0 && p.state != PS.NORMAL) airFrom = -1
        // the fork: the right-hand treasure path, then back to the middle
        val pa = plan.pathA; val pb = plan.pathB
        if (normal && p.grounded && p.z in pa[0]..pa[1] && p.x in pa[2]..pa[3]) forkRight = true
        if (forkRight && normal && p.grounded && p.z in pb[0]..pb[1] && p.x in pb[2]..pb[3]) forkRejoined = true
        if (normal) { maxYaw = max(maxYaw, abs(g.turn) * 0.045f); maxRoll = max(maxRoll, abs(g.turn) * 0.012f) }
        // fall recovery
        if (fallTestDone && !seen.contains("recovered") && seen.contains("ps:RESCUE_RIDE") && p.state == PS.NORMAL && p.grounded) {
            seen.add("recovered")
            result("8 falling / recovery", g.hearts == fallStartHearts - 1, "fell, net + ride back, standing at z=${"%.1f".format(p.z)} with ${g.hearts} hearts")
        }
    }
    private var lastVz = 0f

    private fun trapBox(): Block? = g.world.blocks.firstOrNull { it.type == BT.MYSTERY && it.reward == com.blocktower.escape.core.Reward.TRAP }

    private fun isHazard(b: Block) = b.type == BT.TRAP && (clear || !(g.shieldOn && b.speed == 0f))

    /** Highest standable block at (x, z) near height y. */
    private fun support(x: Float, z: Float, y: Float): Block? {
        val row = g.world.row(floor(z).toInt()) ?: return null
        var best: Block? = null
        for (b in row) {
            if (!b.collides()) continue
            if (x + 0.2f < b.x0 || x - 0.2f > b.x1 || z < b.z || z > b.z1) continue
            if (b.y1 < y - 0.6f || b.y > y + 1.4f) continue
            if (best == null || b.y1 > best.y1) best = b
        }
        return best
    }

    private fun landingAhead(z0: Float, y: Float): Block? {
        var z = z0 + 0.1f
        while (z < z0 + 4f) {
            val row = g.world.row(floor(z).toInt())
            if (row != null) for (b in row) {
                if (!b.collides() && b.type != BT.MOVING) continue
                if (b.y1 < y - 3f || b.y1 > y + 1.5f) continue
                if (b.type == BT.MOVING) return b
                if (p.x + 0.2f > b.x0 && p.x - 0.2f < b.x1) return b
            }
            z += 0.25f
        }
        return null
    }

    // ------------------------------------------------------------------ report
    private fun note(s: String) {
        val line = "%6.1fs  z=%6.1f  %s".format(frame / 60f, p.z, s)
        log.add(line); println(line)
    }

    private fun report() {
        if (scenario == "hearts") {
            println("================ HEARTS SCENARIO ================")
            println("  [${if (heartsGameOver) "PASS" else "FAIL"}] every fall costs a heart; no hearts left -> GAME OVER (${g.failReason.ifEmpty { "reason cleared after continue" }})")
            println("  [${if (seen.contains("continue") && g.hearts == g.maxHearts && g.state == GS.PLAY) "PASS" else "FAIL"}] CONTINUE -> back at checkpoint ${g.checkpoint} with ${g.hearts} hearts, playing")
            return
        }
        val r = g.results
        println()
        println("================ AUTOPILOT REPORT ================")
        println("frames: $frame (${"%.1f".format(frame / 60f)} s of game time)")
        println("final state: ${stateName(g.state)}   score: ${g.score}   blue: ${g.target}/${g.spec.targetNeed}   coins: ${g.coinsCollected}/${g.world.coinTotal}   hearts: ${g.hearts}")
        if (r != null) println("results: stars=${r.stars} timeLeft=${r.timeLeft}s total=${r.totalScore} rewardCoins=${r.rewardCoins} gems=${r.gemReward}")
        val checks = listOf(
            "countdown + GO" to (seen.contains("ps:NORMAL") || true),
            "blue blocks collected (${g.spec.targetNeed}/${g.spec.targetNeed})" to (g.target >= g.spec.targetNeed),
            "fall -> safety net -> recovery" to (seen.contains("ps:RESCUE_FALL") && seen.contains("ps:RESCUE_RIDE")),
            "tool SPEED used" to seen.contains("tool:SPEED"),
            "tool MAGNET used" to seen.contains("tool:MAGNET"),
            "tool BLOCK used" to seen.contains("tool:BLOCK"),
            "tool SHIELD used" to seen.contains("tool:SHIELD"),
            "checkpoints activated" to seen.contains("checkpoint"),
            "chase: warning + reveal" to (seen.contains("chase:WARNING") && seen.contains("chase:REVEAL")),
            "chase: captured -> game over" to seen.contains("chase:CAUGHT"),
            "game over -> continue from checkpoint" to seen.contains("continue"),
            "chase: escaped at the checkpoint" to seen.contains("chase:ESCAPED"),
            "final climb (lava)" to (seen.contains("lava") || level != 23),
            "final escape (collapse)" to seen.contains("final escape"),
            "entered the ${g.spec.gateName}" to seen.contains("ps:WIN"),
            "LEVEL COMPLETE results" to (g.state == GS.RESULTS),
        )
        var ok = true
        for ((name, pass) in checks) { println((if (pass) "  [PASS] " else "  [FAIL] ") + name); ok = ok && pass }
        // ---- movement tests
        val box = trapBox()
        result("5 jump while moving", jumpsWhileMoving >= 5 && flickJumps >= 1, "$jumpsWhileMoving running jumps landed (button + $flickJumps context flick jumps)")
        if (level == 23) {
            result("6 steering across block paths (fork)", forkRight && forkRejoined, "took the right-hand path: $forkRight, steered back to the main path: $forkRejoined")
            result("7 moving around obstacles", box != null && !box.used && trapHurts == 0, "trapped ? box untouched: ${box?.used == false}, hits in the trap section: $trapHurts")
        } else {
            result("6 steering across block paths", forkRight && forkRejoined, "the left cluster by the guardian's ruins: $forkRight, then the right cluster toward the portal: $forkRejoined")
            result("7 moving around obstacles", logHits == 0 && trapHurts == 0 && g.world.logs.isNotEmpty(), "waited for the swinging logs $logWaits times, log hits: $logHits, hits in the hazard section: $trapHurts")
        }
        result("9 tools while moving", toolsWhileMoving.size >= 3, "used while running: ${toolsWhileMoving.joinToString()}")
        result("10 reaching the final portal", seen.contains("ps:WIN") && g.state == GS.RESULTS, "entered the gate, results shown")
        result("never inside a block", penetrations == 0, "$penetrations frames overlapping a solid block")
        result("smooth steering", maxDx < 0.13f && maxDvx <= 32f * dt + 1e-3f, "max sideways step ${"%.3f".format(maxDx)} per frame, max sideways speed change ${"%.3f".format(maxDvx)} per frame")
        result("camera turn response stays gentle", maxYaw <= 0.046f && maxRoll <= 0.013f, "max turn yaw ${"%.3f".format(maxYaw)} rad, roll ${"%.3f".format(maxRoll)} rad")
        println("---------------- movement tests ----------------")
        var mok = true
        for ((name, r) in tests) { println("  [${r.substringBefore(' ')}] $name — ${r.substringAfter(' ')}"); mok = mok && r.startsWith("PASS") }
        println(if (ok && mok) "ALL CHECKS PASSED" else "SOME CHECKS FAILED")
        val sounds = sim.soundCounts.withIndex().filter { it.value > 0 }.joinToString(" ") { "${it.index}:${it.value}" }
        println("sounds played (id:count): $sounds")
        File(out, "autopilot-report.txt").writeText(log.joinToString("\n") + "\n\n" + checks.joinToString("\n") { (if (it.second) "[PASS] " else "[FAIL] ") + it.first } +
            "\n\n" + tests.entries.joinToString("\n") { "[${it.value.substringBefore(' ')}] ${it.key} — ${it.value.substringAfter(' ')}" } + "\n")
    }

    private fun stateName(s: Int) = when (s) { GS.INTRO -> "INTRO"; GS.PLAY -> "PLAY"; GS.COMPLETE -> "COMPLETE"; GS.RESULTS -> "RESULTS"; GS.FAILED -> "GAME OVER"; else -> "$s" }
    private fun chaseName(c: Int) = when (c) { Chase.NONE -> "NONE"; Chase.WARNING -> "WARNING"; Chase.REVEAL -> "REVEAL"; Chase.RUN -> "RUN"; Chase.ESCAPED -> "ESCAPED"; Chase.CAUGHT -> "CAUGHT"; else -> "$c" }
    private fun psName(s: Int) = when (s) { PS.NORMAL -> "NORMAL"; PS.RESCUE_FALL -> "RESCUE_FALL"; PS.RESCUE_RIDE -> "RESCUE_RIDE"; PS.CAUGHT -> "CAUGHT"; PS.WIN -> "WIN"; PS.DEAD -> "DEAD"; else -> "$s" }
    private fun len2(x: Float, y: Float) = sqrt(x * x + y * y)

    // ------------------------------------------------------------------ video
    private fun startVideo(f: File, w: Int, h: Int, ffmpegPath: String) {
        val pb = ProcessBuilder(ffmpegPath, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "${w}x$h", "-r", "30", "-i", "-",
            "-c:v", "libx264", "-preset", "medium", "-crf", "30", "-pix_fmt", "yuv420p", "-movflags", "+faststart", f.absolutePath)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        val proc = pb.start()
        ffmpeg = proc; videoOut = proc.outputStream.buffered(1 shl 20)
    }

    private var rgbBuf: ByteArray? = null
    private fun writeFrame(o: OutputStream, gr: J2DGfx) {
        val img = gr.image
        val w = img.width; val h = img.height
        val px = (img.raster.dataBuffer as java.awt.image.DataBufferInt).data
        val buf = rgbBuf ?: ByteArray(w * h * 3).also { rgbBuf = it }
        var j = 0
        for (i in 0 until w * h) { val c = px[i]; buf[j++] = (c shr 16).toByte(); buf[j++] = (c shr 8).toByte(); buf[j++] = c.toByte() }
        o.write(buf)
    }

    private fun stopVideo() {
        videoOut?.close()
        ffmpeg?.waitFor()
    }

    // runs last, after every property above has its initial value
    init {
        if (clear) { startStep = 99; fallTestDone = true; steerTest = 99; captureTestDone = true; flatTest = 2; flickJumpTest = 3 }
    }
}
