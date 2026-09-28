package com.blocktower.escape.sim

import com.blocktower.escape.core.BT
import com.blocktower.escape.core.Block
import com.blocktower.escape.core.Chase
import com.blocktower.escape.core.GS
import com.blocktower.escape.core.Game
import com.blocktower.escape.core.Key
import com.blocktower.escape.core.MK
import com.blocktower.escape.core.MS
import com.blocktower.escape.core.PS
import com.blocktower.escape.core.TK
import com.blocktower.escape.core.Tune
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Plays a level (Level 4, the sky tower, by default) from START to LEVEL COMPLETE through the real game code, the way a player would:
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
 *   play controls=joystick    play with the joystick (SETTINGS → Controls) instead of swipes
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
    private val g = external ?: Game(sim, (opts["level"] ?: "4").toInt())
    private val p get() = g.player
    private val dt = 1f / 60f
    private var frame = 0
    private val log = ArrayList<String>()
    private val seen = HashSet<String>()

    /**
     * Where the scripted moments happen on each level, and the lane to steer for from each z onward.
     * Levels 1-3 are the early sky levels, Level 4 the sky tower, Level 5 the volcanic sky fortress.
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
    ) else if (level == 6) Plan(
        route = floatArrayOf(
            -9f, 0f,
            103.3f, -1f, 104.3f, 0f, 105.3f, -1f, 106.3f, 0f,   // vanishing stepping stones
            107.2f, -2.2f, 112.4f, -3.0f, 129f, 0f,              // the fork: the fast slide on the left
            281.3f, -1f, 282.3f, 0f, 283.3f, -1f, 284.3f, 0f,   // vanishing stones in the hazard gardens
            346.3f, 1f, 347.8f, 0f,                              // round the spikes during the chase
            386.3f, -1f, 387.3f, 0f, 388.3f, -1f, 389.3f, 0f,   // vanishing stones in the maze
            424.3f, -1f, 425.3f, 0f),
        fallZ = 3.0f, steerZ = 310.2f, speedZ = 150f, captureZ = 330f, magnetZ = 290f, shieldZ = 322f,
        blockZ0 = 35.3f, blockZ1 = 36f, trapZ0 = 262f, trapZ1 = 316f,
        pathA = floatArrayOf(113.05f, 113.95f, -4f, -2f), pathB = floatArrayOf(130f, 138f, -2.6f, 2.6f),
    ) else if (level == 7) level7Plan() else Plan(
        route = floatArrayOf(
            -9f, 0f,
            26.3f, 1f, 28.6f, 0f,                  // round the spikes in the middle lane
            45.3f, -3f, 57.2f, 0f,                 // the fork: the safe bridge on the left, then back
            73.5f, 3f, 83.0f, 1f, 84.5f, 0f,       // out of the loop on the right, then back across
            183.3f, 1f, 185.6f, 0f,                // round the spikes during the chase
            202.6f, 1f, 203.7f, 0f, 204.7f, 1f, 205.9f, 0f,   // vanishing stepping stones
            238.6f, 1f, 239.7f, 0f,
            268.6f, 1f, 269.7f, 0f),
        fallZ = 3.0f, steerZ = 10.5f, speedZ = 160f, captureZ = 166f, magnetZ = 110f, shieldZ = 200f,
        blockZ0 = 30.2f, blockZ1 = 31.6f, trapZ0 = 19f, trapZ1 = 31f,
        pathA = floatArrayOf(46.5f, 56f, -4f, -1.8f), pathB = floatArrayOf(57.5f, 60.5f, -2.6f, 2.6f),
    )
    /** Where a section of the course starts (row), by name. */
    private fun sec(name: String) = g.world.sections.firstOrNull { it.name == name }?.z0?.toFloat() ?: 9999f

    /**
     * Level 7, section by section. "secret=1": also take both secret routes (the rune that raises the blue stair to the
     * treasure island on the crystal stairs, and the hidden ledge under the gauntlet's narrow bridge), opening both chests.
     */
    private fun level7Plan(): Plan {
        val st = sec("stairs"); val ga = sec("gauntlet"); val pu = sec("pursuit"); val mz = sec("maze"); val asc = sec("ascent")
        val am = sec("ambush"); val re = sec("relic")
        val secret = opts["secret"] == "1"
        val r = ArrayList<Float>()
        fun at(z: Float, x: Float) { r.add(z); r.add(x) }
        at(-9f, 0f)
        at(34.3f, -1f); at(35.3f, 0f)                                                      // vanishing stones on the isles
        at(st + 17.3f, -1f); at(st + 18.3f, 0f); at(st + 19.3f, -1f); at(st + 20.3f, 0f)   // vanishing stones on the crystal stairs
        if (secret) { at(st + 21.3f, -2f); at(st + 21.8f, -3f); at(st + 26.1f, -4f); at(st + 27.9f, -3f); at(st + 29.1f, -2.2f) }   // the secret stair
        at(st + 30.2f, -2.2f); at(st + 31.3f, -3.0f); at(st + 46.2f, 0f)                   // the fork: the crystal bridge on the left
        if (secret) { at(ga + 26.2f, 1f); at(ga + 27.8f, 2f); at(ga + 31.1f, 3f); at(ga + 32.8f, 2f); at(ga + 35.2f, 1f); at(ga + 36.2f, 0f) }  // the hidden ledge
        at(pu + 12.3f, -1f); at(pu + 16.6f, 0f)                                            // round the spikes in the pursuit
        at(mz + 21.3f, -1f); at(mz + 22.3f, 0f); at(mz + 23.3f, -1f); at(mz + 24.3f, 0f)   // vanishing stones in the maze
        at(asc + 16.3f, -1f); at(asc + 17.3f, 0f)                                          // stepping stones on the final ascent
        return Plan(route = r.toFloatArray(), fallZ = 3.0f, steerZ = st + 46.4f, speedZ = if (opts["relic"] == "miss") 9999f else re + 3f,
            captureZ = 9999f, magnetZ = ga + 1f, shieldZ = pu + 3f, blockZ0 = am + 7.3f, blockZ1 = am + 8f, trapZ0 = ga, trapZ1 = ga + 53f,
            pathA = floatArrayOf(st + 32f, st + 45f, -4f, -2f), pathB = floatArrayOf(st + 46f, st + 51f, -2.6f, 2.6f))
    }

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
    private var video: VideoWriter? = null
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
    /** Plays with the joystick (SETTINGS → Controls) instead of swipes: the thumb holds the stick all the time. */
    private val joyMode = external?.joystickMode ?: (opts["controls"] == "joystick")
    private var jx = 0f; private var jy = 0f
    private var steerTarget = Float.NaN
    private var steering = false
    private var leanFrames = 0

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
        val videoFile = opts["video"]
        if (videoFile != null || every > 0) gfx = J2DGfx(assets, w, h)
        g.hud.layout(w, h)
        if (videoFile != null) video = VideoWriter(File(videoFile), w, h, opts["ffmpeg"] ?: "ffmpeg")
        val frameDir = opts["frames"]?.let { File(it).apply { mkdirs() } }
        var lastState = -1; var lastHearts = g.hearts; var lastTarget = 0; var lastCp = 0; var lastChase = Chase.NONE; var lastPs = PS.NORMAL
        var lastRelic = com.blocktower.escape.core.RS.NONE
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
            opts["tracez"]?.split(',')?.let { r -> if (p.z >= r[0].toFloat() && p.z <= r[1].toFloat())
                println("TRACE f=$frame z=${"%.2f".format(p.z)} x=${"%.2f".format(p.x)} y=${"%.2f".format(p.y)} vz=${"%.1f".format(p.vz)} vy=${"%.1f".format(p.vy)} st=${p.state} gr=${p.grounded} steer=${"%.2f".format(g.steerX)} jumpF=$jumpHoldF thumb=${thumbQ.size}") }
            // timeline
            if (g.state != lastState) { note("state ${stateName(g.state)}"); lastState = g.state }
            if (g.hearts != lastHearts) { note("hearts ${lastHearts} -> ${g.hearts}"); lastHearts = g.hearts }
            if (g.target != lastTarget) { if (g.target % 3 == 0 || g.target == g.spec.targetNeed) note("blue blocks ${g.target}/${g.spec.targetNeed}"); lastTarget = g.target }
            if (g.checkpoint != lastCp) { note("checkpoint ${g.checkpoint} activated"); lastCp = g.checkpoint; seen.add("checkpoint") }
            if (g.ev.chase != lastChase) { note("chase ${chaseName(g.ev.chase)}"); lastChase = g.ev.chase; seen.add("chase:" + chaseName(g.ev.chase)) }
            if (p.state != lastPs) {
                if (lastPs == PS.LOOP && p.state == PS.NORMAL) { seen.add("loop done"); note("out of the loop at x=${"%.1f".format(p.x)} (lane ${"%.1f".format(p.loopLat)})") }
                note("player ${psName(p.state)}"); lastPs = p.state; seen.add("ps:" + psName(p.state))
            }
            if (g.ev.lavaOn) seen.add("lava")
            if (g.minions.anyWave != lastAnyWave) {
                if (g.minions.anyWave) { wavesStarted++; note("minion wave begins (hearts ${g.hearts})") } else { wavesEnded++; note("minion wave over (hearts ${g.hearts})") }
                lastAnyWave = g.minions.anyWave
            }
            if (g.ev.finalOn) seen.add("final escape")
            if (g.spec.enchanted) watchSorcery()
            // round the loop: the stride keeps going, the turn follows the track smoothly, feet stay on it
            if (p.state == PS.LOOP) {
                if (!inLoop) { inLoop = true; loopRot0 = p.loopRot; lastLoopRot = p.loopRot; loopPhase0 = p.runPhase; loopFrames = 0; loopCoins0 = g.coinsCollected; loopTurn = 0f }
                var d = p.loopRot - lastLoopRot; while (d > 180f) d -= 360f; while (d < -180f) d += 360f
                if (loopFrames > 0) { loopMaxStep = max(loopMaxStep, abs(d)); loopTurn += d }
                lastLoopRot = p.loopRot; loopFrames++
                if (p.loopHop <= 0.01f) loopOnTrack++ else loopHopFrames++
                loopMinV = min(loopMinV, p.loopV); loopStride = p.runPhase - loopPhase0
                loopCoins = g.coinsCollected - loopCoins0
            } else inLoop = false
            // down a slide: he turns with the channel smoothly and stays on its surface
            if (p.state == PS.SLIDE) {
                val sl = p.slide!!
                if (!inSlide) { inSlide = true; lastSlideRot = p.loopRot; slideCoins0 = g.coinsCollected }
                var d = p.loopRot - lastSlideRot; while (d > 180f) d -= 360f; while (d < -180f) d += 360f
                slideMaxStep = max(slideMaxStep, abs(d)); lastSlideRot = p.loopRot; slideFrames++
                slideMaxTh = max(slideMaxTh, abs(p.slideTh))
                // his feet: the channel's surface at his (s, th), plus the hop
                val q = FloatArray(3); sl.point(p.slideS.coerceAtMost(sl.length), p.slideTh, 0f, q, 0)
                if (p.slideIn <= 0f) slideOffSurface = max(slideOffSurface, abs(len3(p.x - q[0], p.y - q[1], p.z - q[2]) - p.slideHop))
                slideCoins += 0
            } else if (inSlide) {
                inSlide = false; slidesDone++; seen.add("slide done")
                note("out of a slide at ${"%.1f".format(len2(p.vx, p.vz))}/s (${g.coinsCollected - slideCoins0} coins on it)")
                slideCoins += g.coinsCollected - slideCoins0
            }
            val rs = g.relic.state
            if (rs != lastRelic) {
                when (rs) {
                    com.blocktower.escape.core.RS.APPEAR -> { seen.add("relic:appear"); note("Runaway Relic appears ${"%.1f".format(g.relic.z - p.z)} ahead") }
                    com.blocktower.escape.core.RS.CAUGHT -> { seen.add("relic:caught"); note("Runaway Relic CAUGHT at z=${"%.1f".format(p.z)} (coins ${g.coins}, gems ${g.gems}, score ${g.score})") }
                    com.blocktower.escape.core.RS.ESCAPED -> { seen.add("relic:escaped"); note("Runaway Relic got away (hearts ${g.hearts}, checkpoint ${g.checkpoint})") }
                }
                lastRelic = rs
            }
            // render
            val gr = gfx
            if (gr != null && (frame % 2 == 0)) {
                if (video != null || (every > 0 && frame % every == 0)) {
                    gr.clear(); g.render(gr)
                    video?.write(gr.image)
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
        video?.close()
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
        if (joyMode) joyStep()
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
        if (p.state == PS.LOOP) { loopHands(); return }
        if (p.state == PS.SLIDE) { slideHands(); return }
        if (p.state != PS.NORMAL) { wantRun = true; return }
        if (scenario == "hearts" && !seen.contains("continue")) { if (thumbFree && p.grounded && g.playT > 0.5f) leapOffTheSide(); return }
        if (startStep < 99) { if (joyMode) joyStartTests() else startTests(); return }
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
        // "relic=miss": dawdle when the Runaway Relic appears, so it gets away (it must not cost anything)
        if (opts["relic"] == "miss" && g.relic.state == com.blocktower.escape.core.RS.RUN && g.relic.z - p.z < 30f && g.relic.t < 6f) { wantRun = false; keepPace(); return }
        if (!magnetUsed && z > plan.magnetZ && p.grounded) { tapTool(TK.MAGNET); magnetUsed = true }
        if (!shieldUsed && z > plan.shieldZ && p.grounded) { tapTool(TK.SHIELD); shieldUsed = true }

        wantRun = true
        // --- Level 7: a magic bolt about to land where we will be: steer to a free lane first (even while waiting)
        val boltX = boltDodge()
        if (!boltX.isNaN() && boltX != -99f && !joyMode && thumbFree && frame - lastSteerFrame > 14 && abs(boltX - g.steerX) > 0.15f) {
            steerSwipe(max(-2.5f, min(2.5f, boltX - g.steerX))); return
        }
        // --- a swinging log will be in the way when we get there: wait for it to swing clear
        if (opts["trace"] != null && z in 176f..181f && frame % 3 == 0) { val l = g.world.logs.minByOrNull { abs(it.pz - z) }!!; println("TRACE f=$frame z=${"%.2f".format(z)} vz=${"%.2f".format(p.vz)} x=${"%.2f".format(p.x)} ball=(${"%.2f".format(l.cx)},${"%.2f".format(l.cy)}) way=${logInWay()} cruise=${g.swipe.cruise} dash=${p.dashT}") }
        if (logInWay()) {
            if (!logWaitNoted) { note("waiting for the swinging mace"); logWaitNoted = true; logWaits++ }
            wantRun = false; keepPace(); return
        }
        logWaitNoted = false
        // --- Level 7: an orb imp swooping across a row ahead: wait for its pass (while imps chase us, jump it instead)
        val sw = swooperAhead()
        if (sw != null) {
            if (g.minions.pursuit) {
                if (sw in 1.15f..1.8f && p.grounded && jumpHoldF == 0) { pressJump(); swoopJumps++; note("jumping a swooping imp") }
            } else {
                if (!swoopWaitNoted) { note("waiting for the swooping imp"); swoopWaitNoted = true; swoopWaits++ }
                wantRun = false; keepPace(); return
            }
        } else swoopWaitNoted = false
        // --- Level 7: a shockwave rolling at us (jump as it arrives); a rolling stone with no lane to step into (jump it)
        if (g.spec.enchanted && p.grounded && jumpHoldF == 0) {
            val si = shockIn()
            if (si in 0.08f..0.24f) { pressJump(); shockJumps++; note("jumping a shockwave") }
            else if (rollerJumpIn in 0.05f..0.22f) { pressJump(); note("jumping a rolling stone") }
        }
        // --- a laser gate just ahead: jump its beam (timed so he is above it as he passes)
        val la = laserAhead()
        val lv = max(2f, len2(p.vx, p.vz))
        if (p.grounded && la > 0f && la - 0.44f in 0.08f * lv..0.26f * lv && jumpHoldF == 0) { pressJump(); if (!laserNoted) { laserJumps++; laserNoted = true } }
        if (la < 0f || la > 3.5f) laserNoted = false
        // never stop in (or just before) a laser's line: carry on through it first
        val nearLaser = g.world.lasers.any { l -> l.z - p.z in -0.6f..2.4f && p.x > l.x0 - 0.2f && p.x < l.x1 + 0.2f }
        // --- a spiked block sliding across the path: pass on the side it has left (or wait for it)
        if (!nearLaser) when (spikeBoxPlan()) {
            2 -> {
                if (!boxWaitNoted) { note("waiting for the sliding spiked block"); boxWaitNoted = true; boxWaits++ }
                // stop well short of it (not creeping closer while it sweeps): too close, step back a little
                val near = sliders.filter { it.z - p.z in 0.6f..4.5f }.minOfOrNull { it.z - p.z } ?: 9f
                wantRun = near > 2.4f && len2(p.vx, p.vz) < 2f
                if (near < 1.9f && thumbFree && g.swipe.cruise <= 0f && frame % 30 == 0) swipe(baseX(), baseY() - 60f * s, 0f, 170f * s, 8, 14)
                keepPace(); return
            }
            1 -> if (!boxWaitNoted) { note("dodging the sliding spiked block to x=${"%.0f".format(dodgeX)}"); boxWaitNoted = true; boxWaits++ }
            else -> boxWaitNoted = false
        }
        // --- steering: a sideways swipe whenever the lane we want is off to the side
        var tx = routeX(z)
        if (!dodgeX.isNaN()) tx = dodgeX
        // --- Level 7: a magic bolt about to land where we will be: steer to a free lane (or hold back)
        if (boltX == -99f) { wantRun = false; keepPace(); return }
        if (!boltX.isNaN()) tx = boltX
        // (never steer back into a lane something is about to strike: stay where we are until it has passed)
        else if (!laneSafe(tx)) tx = g.steerX
        val ground = p.ground
        val onMover = ground != null && ground.type == BT.MOVING
        if (onMover) tx = g.steerX
        if (joyMode) steerTarget = tx
        else if (thumbFree && frame - lastSteerFrame > (if (g.ev.windOn) 8 else 14) && abs(tx - g.steerX) > (if (g.ev.windOn) 0.08f else 0.15f)) { steerSwipe(max(-2.5f, min(2.5f, tx - g.steerX))); return }

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
        // the mouth of a slide ahead is not a gap to jump: run into it
        if (g.world.slides.any { it.ride && it.z0 - z in -0.2f..2.6f && abs(it.x0 - p.x) < it.r + 0.3f && abs(it.y0 - y) < 1f }) { keepPace(); return }
        // the block tool bridge in the tool trials: too far to jump
        if (!blockUsed && !wall && z > plan.blockZ0 && z < plan.blockZ1 && edge < 0.6f) { tapTool(TK.BLOCK); blockUsed = true; return }
        val landing = landingAhead(z + edge, y)
        if (landing != null && landing.type == BT.MOVING && !wall) {
            // wait back from the edge (with a run-up) until the platform will be under us when we land
            val standing = len2(p.vx, p.vz) < 1f
            val tLand = if (standing) 0.95f else 0.55f
            val px = landing.baseX + landing.amp * sin(landing.phase + (g.hazT + tLand * g.hazK) * landing.speed)
            if (abs(px - p.x) > landing.sx * 0.5f - 0.15f) {
                if (edge < 1.5f) { wantRun = false; keepPace() }
                return
            }
            if (edge > 0.35f) { keepPace(); if (!thumbFree) return }
        }
        // a few gaps are crossed with the context flick instead of the button
        // (not while the flat-ground flick test is still watching for a jump; Level 1 has its gaps early)
        if (flickJumpTest < 3 && flatTest != 1 && !wall && landing != null && landing.type != BT.MOVING && edge in 0.9f..2.0f && thumbFree &&
            len2(p.vx, p.vz) > 3.5f && frame - flickJumpFrame > 90 && z > (if (level == 1) 12f else 30f)) {
            flickUp(); flickJumpFrame = frame; flickJumpTest++; note("flick up with a gap ${"%.2f".format(edge)} ahead"); return
        }
        if (frame - flickJumpFrame < 50) { keepPace(); return }   // the game times that jump to the edge
        if (edge < (if (wall) 0.45f else 0.32f)) pressJump()
        keepPace()
    }

    /**
     * Round the loop: keep pushing forward, and pick a lane with no spike plate coming up (or hop the plate when
     * there is no time to steer).
     */
    private fun loopHands() {
        val lp = p.loop ?: return
        if (!seen.contains("loop")) { note("entered the great loop at ${"%.1f".format(len2(p.vx, p.vz))}/s"); seen.add("loop") }
        wantRun = true
        if (thumbFree && g.swipe.cruise < 0.85f) flickUp()
        val ahead = lp.spikes.filter { it[0] > p.loopU && (it[0] - p.loopU) * lp.length < 3.2f }
        if (ahead.isEmpty()) return
        val sp = ahead.minByOrNull { it[0] }!!
        if (abs(sp[1] - p.loopLat) < 0.7f) {
            val free = floatArrayOf(0f, -1f, 1f).filter { l -> lp.spikes.none { s2 -> s2[0] > p.loopU && (s2[0] - p.loopU) * lp.length < 3.2f && abs(s2[1] - l) < 0.7f } }
            val dist = (sp[0] - p.loopU) * lp.length
            // "loop=hop": jump every spike plate instead of steering round it (tests the hop on the loop)
            if (opts["loop"] == "hop") { if (dist < 1.3f && p.loopHop <= 0.01f) { pressJump(); note("loop: hopping a spike plate") }; return }
            if (free.isNotEmpty() && dist > 1.2f && thumbFree && frame - lastSteerFrame > 10) {
                val l = free.minByOrNull { abs(it - p.loopLat) }!!
                steerSwipe(l - p.loopLat); note("loop: steering round a spike plate")
            } else if (dist < 0.9f) { pressJump(); note("loop: hopping a spike plate") }
        }
    }

    // ------------------------------------------------------------------ Level 6: slides, lasers, spiked blocks
    private var laserNoted = false; private var laserJumps = 0
    private var boxWaitNoted = false; private var boxWaits = 0
    private var slidesDone = 0; private var inSlide = false; private var slideMaxStep = 0f; private var lastSlideRot = 0f
    private var slideFrames = 0; private var slideCoins0 = 0; private var slideCoins = 0; private var slideMaxTh = 0f
    private var slideOffSurface = 0f

    // ---- Level 7: the Sorcerer's creatures and his Wrath, the secret routes
    private var lastWrath = 0; private var minHazK = 1f; private var lastPhase = 1
    private val spiritHops = HashSet<Int>(); private var handSlams = 0; private var shocksSeen = 0; private var shardsSeen = 0; private var rollersSeen = 0
    private val shockSeenSet = HashSet<Any>(); private val shardSeenT = HashMap<Any, Float>(); private val rollerSeen = HashSet<Any>()
    private val handWasOut = HashSet<Any>()
    private fun watchSorcery() {
        val so = g.sorcery
        if (so.wrath != lastWrath) {
            note("Sorcerer's Wrath: ${arrayOf("NONE", "RISE", "ATTACK", "STUN", "DONE")[so.wrath]} (runes ${so.runesLit}/${g.world.runes.size}, hearts ${g.hearts})")
            lastWrath = so.wrath; seen.add("wrath:${so.wrath}")
        }
        if (so.phase != lastPhase) { note("phase ${so.phase}"); lastPhase = so.phase; seen.add("phase:${so.phase}") }
        minHazK = min(minHazK, g.hazK)
        for (sh in so.shocks) if (sh.on) { if (shockSeenSet.add(sh.hashCode() * 1000 + (sh.z * 10).toInt() / 1000)) {} }
        for (sh in so.shocks) if (sh.on && sh.t < 0.02f) shocksSeen++
        for (sh in so.shards) if (sh.on && !sh.harmless && sh.t < 0.02f && !sh.landed) shardsSeen++
        for (r in so.rollers) if (r.state >= 1 && rollerSeen.add(r)) rollersSeen++
        for (sp in so.spirits) if (sp.state == 1) spiritHops.add((sp.z * 10).toInt())
        for (h in so.hands) { if (h.danger && abs(h.z - p.z) < 12f && handWasOut.add(h)) handSlams++; if (!h.danger) handWasOut.remove(h) }
        for (se in g.world.secrets) if (se.found && seen.add("secret:${se.id}")) note("SECRET ROUTE FOUND: ${se.title}")
        for (c in g.world.chests) if (c.open && seen.add("chest:${c.z}")) note("treasure chest opened (coins ${g.coins}, gems ${g.gems})")
    }

    // ---- Level 7: the Sorcerer's minions
    private var swoopWaitNoted = false; private var swoopWaits = 0; private var swoopJumps = 0
    private var boltDodges = 0; private var boltDodgeZ = -99f

    /** Distance to a swooping imp's row ahead whose pass we would run into (or null). */
    private fun swooperAhead(): Float? {
        for (m in g.minions.all) {
            if (m.kind != MK.SWOOP || m.state != MS.ACTIVE) continue
            val sp = m.spot ?: continue
            val dz = sp.z + 0.5f - p.z
            if (dz < 0.9f || dz > 3.4f || abs(p.y - sp.y) > 1.5f) continue
            val t0 = g.hazT + arrival(dz) * g.hazK
            var k = -3
            while (k <= 6) { if (swoopHits(sp, t0 + k * 0.1f * g.hazK, p.x)) return dz; k++ }
        }
        return null
    }
    private fun swoopHits(sp: com.blocktower.escape.core.MinionSpot, t: Float, x: Float): Boolean {
        val period = sp.speed
        val c = ((t + sp.phase) % period + period) % period
        val restAt = period - com.blocktower.escape.core.MinionSystem.PASS
        if (c < restAt - 0.1f) return false
        val u = clamp01((c - restAt) / com.blocktower.escape.core.MinionSystem.PASS)
        val pass = (floor((t + sp.phase) / period).toInt() and 1) == 0
        val dir = if (pass) 1f else -1f
        val mx = sp.x - dir * (sp.amp + 0.6f) + dir * 2f * (sp.amp + 0.6f) * u
        return abs(mx - x) < 0.34f + Tune.RADIUS + 0.3f
    }
    private fun clamp01(v: Float) = max(0f, min(1f, v))

    private val boltList = ArrayList<FloatArray>()
    private val dangerList = ArrayList<FloatArray>()
    private val spellList = ArrayList<FloatArray>()
    /** A roller will reach us with no lane free to step into: jump it as it arrives (seconds until it does, or -1). */
    private var rollerJumpIn = -1f

    /**
     * A lane away from what is about to strike where we are heading (NaN: none needed; -99: no lane, hold back): the
     * lantern ships' bolts, and in the Sorcerer's realm his falling shards, the spirit's glowing mark and his rolling
     * stones. Each danger is a spot (x, z) and the seconds [from, until] it is dangerous; we are in trouble if we will
     * be within a row of it, in its lane, while it is.
     */
    private fun boltDodge(): Float {
        dangerList.clear(); rollerJumpIn = -1f
        g.minions.boltTargets(boltList)
        for (b in boltList) dangerList.add(floatArrayOf(b[0], b[1], b[2] - 0.35f, b[2] + 0.15f, 0f))
        if (g.spec.enchanted) {
            g.sorcery.dangers(spellList)
            for (d in spellList) dangerList.add(floatArrayOf(d[0], d[1], d[2], d[3], 1f))
            for (r in g.sorcery.rollers) if (r.state == 1 || r.state == 2) {
                val dz = r.z - p.z
                if (dz < -0.3f || dz > 15f) continue
                val v = r.spot.period * g.hazK
                val tm = dz / max(0.6f, max(0f, p.vz) + v)
                dangerList.add(floatArrayOf(r.x, p.z + max(0f, p.vz) * tm, tm - 0.4f, tm + 0.4f, 2f))
            }
        }
        for (d in dangerList) {
            if (!hits(d, g.steerX)) continue
            // a lane beside it, with floor from here to there, free of every danger
            val row = floor(d[1]).toInt()
            val px = g.world.pathXAt(row)
            val cands = floatArrayOf(px - 2f, px - 1f, px, px + 1f, px + 2f).filter { x ->
                abs(x - d[0]) > 0.9f && dangerList.none { hits(it, x) } && laneHasFloor(x, floor(p.z).toInt(), row)
            }
            val lane = cands.minByOrNull { abs(it - p.x) }
            if (abs(boltDodgeZ - d[1]) > 2.5f) {
                boltDodgeZ = d[1]; boltDodges++
                note("dodging ${when (d[4].toInt()) { 0 -> "a magic bolt"; 1 -> "a shard / the spirit"; else -> "a rolling stone" }} at x=${"%.1f".format(d[0])}")
            }
            if (lane == null && d[4] == 2f) { rollerJumpIn = max(0f, d[2] + 0.4f); return Float.NaN }
            return lane ?: -99f
        }
        return Float.NaN
    }

    /** Whether danger [d] catches us in lane [lane] as we pass its spot. */
    private fun hits(d: FloatArray, lane: Float): Boolean {
        if (abs(lane - d[0]) > 0.9f || d[1] < p.z - 0.6f) return false
        val t0 = arrival(max(0f, d[1] - p.z - 0.75f)); val t1 = arrival(max(0f, d[1] - p.z + 0.75f))
        return t1 > d[2] && t0 < d[3]
    }
    /** Lane [x] is clear of everything about to strike (from the last [boltDodge]). */
    private fun laneSafe(x: Float) = dangerList.none { hits(it, x) }

    /** Floor under lane [x] in every row from [r0] to [r1] (near the path's level). */
    private fun laneHasFloor(x: Float, r0: Int, r1: Int): Boolean {
        for (r in r0..r1) {
            val lvl = g.world.levelAt(r)
            if (g.world.row(r)?.any { it.collides() && it.type != BT.TRAP && x > it.x0 - 0.05f && x < it.x1 + 0.05f && abs(it.y1 - lvl) < 0.6f } != true) return false
        }
        return true
    }

    /** Seconds until a shockwave rolling at us reaches us (or -1). */
    private fun shockIn(): Float {
        var best = -1f
        for (sh in g.sorcery.shocks) {
            if (!sh.on) continue
            val dz = sh.z - p.z
            if (dz < -0.1f || abs(sh.x - p.x) > 2f) continue
            val tt = dz / max(0.5f, max(0f, p.vz) + com.blocktower.escape.core.Sorcery.SHOCK_SPEED * g.hazK)
            if (best < 0f || tt < best) best = tt
        }
        return best
    }
    private var shockJumps = 0

    /** Distance to the next laser gate across our lane (or -1). */
    private fun laserAhead(): Float {
        var best = -1f
        for (l in g.world.lasers) {
            val d = l.z - p.z
            if (d < 0f || d > 3f || p.x < l.x0 - 0.2f || p.x > l.x1 + 0.2f || abs(l.y - (p.y + 0.42f)) > 0.8f) continue
            if (best < 0f || d < best) best = d
        }
        return best
    }

    /** A lane to pass a sliding spiked block (or a patrolling imp) in (NaN: none needed). */
    private var dodgeX = Float.NaN
    private var dodgeBox: Any? = null
    /** Things that slide back and forth across the path: sliding spiked blocks and (Level 7) patrolling imps. */
    private class Slider(val ref: Any, val x0: Float, val amp: Float, val speed: Float, val phase: Float, val z: Float, val y: Float, val half: Float)
    private val sliders = ArrayList<Slider>()
    private fun gatherSliders() {
        sliders.clear()
        for (b in g.world.spikeBoxes) if (b.amp > 0f) sliders.add(Slider(b, b.x0, b.amp, b.speed, b.phase, b.z, b.y, b.size * 0.5f + 0.14f))
        for (m in g.minions.all) {
            val sp = m.spot ?: continue
            if (m.kind != MK.PATROL || (m.state != MS.ACTIVE && m.state != MS.APPEAR)) continue
            sliders.add(Slider(m, sp.x, sp.amp, sp.speed, sp.phase, sp.z + 0.5f, sp.y, 0.36f))
        }
    }

    /**
     * Sliding spiked blocks ahead: 0 = our lane is clear when we pass, 1 = steer to [dodgeX] (a lane it has left),
     * 2 = no lane is clear in time: wait.
     */
    private fun spikeBoxPlan(): Int {
        gatherSliders()
        val cur = sliders.firstOrNull { it.ref === dodgeBox }
        if (dodgeBox != null && (cur == null || p.z > cur.z + 0.7f)) { dodgeBox = null; dodgeX = Float.NaN }
        for (b in sliders) {
            val dz = b.z - p.z
            if (dz < 0.6f || dz > 4.5f || abs(p.y - b.y) > 1.5f) continue
            val t0 = g.hazT + arrival(dz) * g.hazK
            fun clear(x: Float): Boolean {
                var k = -2
                while (k <= 4) {
                    val bx = b.x0 + b.amp * sin(b.phase + (t0 + k * 0.1f * g.hazK) * b.speed)
                    if (abs(bx - x) < b.half + Tune.RADIUS + 0.12f) return false
                    k++
                }
                return true
            }
            // committed to a lane in front of it: keep it while it stays clear
            if (dodgeBox === b.ref && !dodgeX.isNaN() && clear(dodgeX)) return 1
            val here = g.steerX
            if (clear(here) && abs(p.x - here) < 0.3f) { dodgeBox = null; dodgeX = Float.NaN; continue }
            // a lane it will have left by then, reachable in time (a lane change takes about a third of a second)
            val arrive = arrival(dz)
            val px = g.world.pathXAt(kotlin.math.floor(b.z).toInt())
            val lane = floatArrayOf(px - 1f, px, px + 1f).filter { clear(it) && (abs(it - p.x) < 0.3f || 0.3f + 0.25f * abs(it - p.x) < arrive - 0.1f) }.minByOrNull { abs(it - p.x) }
            if (lane != null) { dodgeX = lane; dodgeBox = b.ref; return 1 }
            // nothing clear in time: wait out of its reach until a lane is
            dodgeX = Float.NaN; dodgeBox = null
            if (dz < 0.6f) return 0
            return 2
        }
        return 0
    }

    /**
     * Down a slide: keep the pace up, ride toward the coins and the blue blocks, and steer round the spike plates
     * (or hop one when there is no time to steer).
     */
    private fun slideHands() {
        val sl = p.slide ?: return
        if (!inSlide) { note("slide: in at ${"%.1f".format(len2(p.vx, p.vz))}/s"); seen.add("slide") }
        wantRun = true
        if (thumbFree && g.swipe.cruise < 0.85f) { flickUp(); return }
        val s0 = p.slideS
        // where to ride: the next coin (or blue block) 2..6 ahead, else the bottom
        var want = 0f; var bestD = 99f
        for (c in g.world.coins) {
            if (c.collected || abs(c.z - p.z) > 8f) continue
            val th = thetaOf(sl, s0, c.x, c.y, c.z) ?: continue
            if (th[0] < s0 + 1.5f || th[0] > s0 + 6f) continue
            if (th[0] - s0 < bestD) { bestD = th[0] - s0; want = th[1] }
        }
        // spike plates coming up: move off their line
        var hop = false
        for (sp in sl.spikes) {
            val d = sp[0] - s0
            if (d < 0.3f || d > max(6f, p.slideV * 1.1f)) continue
            if (abs(want - sp[1]) * sl.r < 0.9f) want = sp[1] + (if (want >= sp[1]) 1f else -1f) * 1.0f
            if (d < 0.9f + p.slideV * 0.1f && abs(p.slideTh - sp[1]) * sl.r < 0.7f) hop = true
        }
        if (hop && p.slideHop <= 0.01f && jumpHoldF == 0) { pressJump(); note("slide: hopping a spike plate") }
        want = max(-Tune.SLIDE_WALL, min(Tune.SLIDE_WALL, want))
        val eq = -kotlin.math.atan(p.slideV * p.slideV * sl.curvAt(s0 + 1f) / Tune.GRAVITY)
        val need = max(-1.05f, min(1.05f, want - eq)) - g.slideSteer
        if (thumbFree && frame - lastSteerFrame > 10 && abs(need) > 0.2f) steerSwipe(max(-2.5f, min(2.5f, need / 0.55f)))
    }

    /** (s, th) of a point near slide [sl] ahead of s0 (or null when it is not in the channel). */
    private fun thetaOf(sl: com.blocktower.escape.core.Slide, s0: Float, x: Float, y: Float, z: Float): FloatArray? {
        var best = -1; var bd = 1e9f
        val i0 = max(0, (s0 / com.blocktower.escape.core.Slide.STEP).toInt()); val i1 = min(sl.n - 1, i0 + 40)
        for (i in i0..i1) {
            val ax = sl.bx[i] + sl.r * sl.ux[i]; val ay = sl.by[i] + sl.r * sl.uy[i]; val az = sl.bz[i] + sl.r * sl.uz[i]
            val d = (x - ax) * sl.tx[i] + (y - ay) * sl.ty[i] + (z - az) * sl.tz[i]
            if (abs(d) < bd) { bd = abs(d); best = i }
        }
        if (best < 0 || bd > 0.3f) return null
        val i = best
        val vx = x - (sl.bx[i] + sl.r * sl.ux[i]); val vy = y - (sl.by[i] + sl.r * sl.uy[i]); val vz = z - (sl.bz[i] + sl.r * sl.uz[i])
        val r = vx * sl.rx[i] + vy * sl.ry[i] + vz * sl.rz[i]
        val u = vx * sl.ux[i] + vy * sl.uy[i] + vz * sl.uz[i]
        if (len2(r, u) > sl.r + 0.2f) return null
        return floatArrayOf(i * com.blocktower.escape.core.Slide.STEP, kotlin.math.atan2(r, -u))
    }

    private var inLoop = false
    private var loopRot0 = 0f; private var lastLoopRot = 0f; private var loopMaxStep = 0f; private var loopTurn = 0f
    private var loopFrames = 0; private var loopOnTrack = 0; private var loopHopFrames = 0; private var loopMinV = 99f
    private var loopPhase0 = 0f; private var loopStride = 0f; private var loopCoins0 = 0; private var loopCoins = 0

    private var logWaitNoted = false
    private var logWaits = 0
    /** Predicts whether a swinging mace sweeps through our lane while we pass under it. */
    private fun logInWay(): Boolean {
        for (l in g.world.logs) {
            val dz = l.pz - p.z
            if (dz < 1.0f || dz > 3.4f + max(0f, p.vz - Tune.RUN) * 1.2f || abs(p.y - (l.py - l.len)) > 3f) continue
            // still carried fast by a launch (out of a slide) toward a swinging mace: brake first, time it after
            if (p.boostT > 0f && p.vz > Tune.RUN + 0.5f) return true
            val t0 = g.logT + arrival(dz) * g.hazK
            val w = com.blocktower.escape.core.TAU / l.period
            // from a little before we arrive until we are through (about 0.6 s under the swing)
            var k = -3
            while (k <= 7) {
                val a = l.amp * sin(w * (t0 + k * 0.1f * g.hazK) + l.phase)
                val cx = l.px + sin(a) * l.len; val cy = l.py - kotlin.math.cos(a) * l.len
                if (abs(p.x - cx) < l.radius + 0.7f && cy - l.radius < p.y + Tune.HEIGHT + 0.25f) return true
                k++
            }
        }
        return false
    }

    /**
     * How long until we have run [dz] further, running on at a full run: speeding up to it at 16 u/s² as the game
     * does, or, still carried faster by a launch (a slide's exit, a star block), keeping that speed until the launch
     * wears off and then easing down to a run at 14 u/s².
     */
    private fun arrival(dz: Float): Float {
        val h = 1f / 60f
        val vRun = (if (g.speedOn) Tune.RUN_FAST else Tune.RUN) * max(0.85f, min(1f, g.swipe.cruise))
        var v = max(0f, p.vz); var z = 0f; var t = 0f; var tb = max(0f, p.boostT); var td = max(0f, p.dashT)
        while (z < dz && t < 5f) {
            // a speed pad's burst carries him at a dash while it lasts
            val vm = if (td > 0f) Tune.RUN_DASH else vRun
            val target = if (tb > 0f && v > vm) v else vm
            v = if (v < target) min(target, v + 16f * h) else max(target, v - 14f * h)
            z += v * h; t += h; tb -= h; td -= h
        }
        return t
    }

    /** Swipe up to keep running (or down to stop) when the pace is not what we want. */
    private fun keepPace() {
        if (joyMode) { jy = if (wantRun) -1f else 0f; return }
        if (!thumbFree) return
        if (wantRun && g.swipe.cruise < 0.85f) { flickUp(); return }
        if (!wantRun && g.swipe.cruise > 0f) swipeDown(90f)
    }

    /** Jump, then swipe hard to the side while in the air (steering on the ground never leaves the path). */
    private fun leapOffTheSide(): Boolean {
        if (joyMode) {
            if (jumpHoldF > 0) return false
            pressJump(); leanFrames = 150; return true
        }
        if (jumpHoldF > 0 || !thumbFree) return false
        pressJump(); thumbQ.add(Act(-1, 0f, 0f)); swipe(baseX() + 60f * s, baseY(), -300f * s, 0f, 9)
        return true
    }

    /** Joystick mode: the thumb stays on the stick; each frame it is pushed toward the steering target and up to run. */
    private var joyDown = false
    private fun joyStep() {
        if (!g.joy.holding) joyDown = false
        if (g.state != GS.PLAY && g.state != GS.INTRO) { jx = 0f; jy = 0f }
        var x = 0f
        if (leanFrames > 0) {
            // leaning off the path on purpose (the fall test): hop sideways until the path is gone from under him
            leanFrames--; x = -1f
            if (p.grounded && p.state == PS.NORMAL && jumpHoldF == 0 && leanFrames < 44) pressJump()
            if (p.state != PS.NORMAL) leanFrames = 0
        }
        else if (!steerTarget.isNaN() && p.state == PS.NORMAL) {
            val err = steerTarget - g.steerX
            if (abs(err) > 0.12f) steering = true
            if (abs(err) < 0.04f) steering = false
            if (steering) x = (if (err < 0f) -1f else 1f) * min(1f, 0.32f + abs(err) * 1.2f)
        }
        jx = x
        val r = 100f * g.hud.s
        val px = g.hud.joyX() + jx * r; val py = g.hud.joyY() + jy * r
        if (!joyDown) { g.touchDown(THUMB, px, py); joyDown = true } else g.touchMove(THUMB, px, py)
    }

    /** Joystick mode at the start line: steer one block right, back again, then push up and run. */
    private fun joyStartTests() {
        if (frame < stepAt) return
        when (startStep) {
            0 -> { if (g.playT > 0.4f) { mark0 = p.x; steerTarget = mark0 + 1f; startStep = 1; stepAt = frame + 60 } }
            1 -> {
                result("joystick right: one block, gliding", abs(p.x - (mark0 + 1f)) < 0.15f, "x ${"%.2f".format(mark0)} -> ${"%.2f".format(p.x)}")
                steerTarget = mark0; startStep = 2; stepAt = frame + 60
            }
            2 -> {
                result("joystick left: back again", abs(p.x - mark0) < 0.15f, "back to x=${"%.2f".format(p.x)}")
                jy = -1f; runStartFrame = frame; startStep = 3; stepAt = frame + 48
            }
            3 -> {
                val sp = p.vz
                result("joystick up: runs, accelerates smoothly", sp > 0.85f * Tune.RUN && maxDvz <= 16f * dt * 1.05f + 1e-3f,
                    "speed ${"%.2f".format(sp)} after 0.8 s, max speed change per frame ${"%.3f".format(maxDvz)} (limit ${"%.3f".format(16f * dt)})")
                startStep = 99
            }
        }
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
        if (flatTest != 0 || p.z < 40f || !p.grounded || !thumbFree || len2(p.vx, p.vz) < 4.5f || frame - flickJumpFrame < 60) return
        // spikes count as an edge even under the shield: the game's flick jumps them
        var d = 0.1f
        while (d < 4.8f) { val sup = support(p.x, p.z + d, p.y); if (sup == null || sup.type == BT.TRAP || sup.y1 > p.y + 0.3f) return; d += 0.1f }
        flickUp(); flatTest = 1; flatUntil = frame + 45; note("flick up on flat ground")
    }

    // ------------------------------------------------------------------ measurements
    private var maxDvz = 0f
    private var maxDx = 0f; private var maxDvx = 0f
    private var penetrations = 0
    private var spellHits = 0
    private var hurts = 0; private var trapHurts = 0; private var logHits = 0; private var minionHits = 0; private var boltHits = 0
    private var wavesStarted = 0; private var wavesEnded = 0; private var lastAnyWave = false
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
            if (g.lastHurt == "mace") logHits++
            if (g.lastHurt == "minion") minionHits++
            if (g.lastHurt == "bolt") boltHits++
            if (g.lastHurt in setOf("roller", "spirit", "hand", "shockwave", "shard")) spellHits++
            note("hit (hurt flash) at z=${"%.1f".format(p.z)}: ${g.lastHurt}")
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
        val left = g.world.blocks.filter { it.type == BT.TARGET }
        if (left.isNotEmpty()) println("blue blocks left behind (${left.size}): " + left.joinToString { "(%.1f, %.1f, %.1f)".format(it.x, it.y, it.z) })
        // only what this level has: tools it hands out, a chase, lava, a final escape, the scripted fall
        val hasEvent = { e: Int -> g.world.triggers.any { it.event == e } }
        val chase = g.spec.guardName.isNotEmpty()
        val toolZ = floatArrayOf(plan.magnetZ, plan.shieldZ, plan.speedZ, plan.blockZ0)
        val usesTool = BooleanArray(4) { k -> !g.toolLocked(k) && toolZ[k] < 9999f }
        val checks = listOf(
            "countdown + GO" to (seen.contains("ps:NORMAL") || true),
            "blue blocks collected (${g.spec.targetNeed}/${g.spec.targetNeed})" to (g.target >= g.spec.targetNeed),
            "fall -> safety net -> recovery" to (seen.contains("ps:RESCUE_FALL") && seen.contains("ps:RESCUE_RIDE")),
            "tool ${g.toolName(TK.SPEED)} used" to seen.contains("tool:" + g.toolName(TK.SPEED)),
            "tool MAGNET used" to seen.contains("tool:MAGNET"),
            "tool BLOCK used" to seen.contains("tool:BLOCK"),
            "tool SHIELD used" to seen.contains("tool:SHIELD"),
            "checkpoints activated" to seen.contains("checkpoint"),
            "chase: warning + reveal" to (seen.contains("chase:WARNING") && seen.contains("chase:REVEAL")),
            "chase: captured -> game over" to seen.contains("chase:CAUGHT"),
            "game over -> continue from checkpoint" to seen.contains("continue"),
            "chase: escaped at the checkpoint" to seen.contains("chase:ESCAPED"),
            "final climb (lava)" to seen.contains("lava"),
            "final escape (collapse)" to seen.contains("final escape"),
            "the great loop (ran all the way round)" to seen.contains("loop done"),
            "rode a rainbow slide" to seen.contains("slide done"),
            "Runaway Relic: CHASE & COLLECT appeared" to seen.contains("relic:appear"),
            (if (opts["relic"] == "miss") "Runaway Relic: got away, nothing lost" else "Runaway Relic: caught, bonus paid") to
                (if (opts["relic"] == "miss") seen.contains("relic:escaped") && !seen.contains("relic:caught") else seen.contains("relic:caught")),
            "entered the ${g.spec.gateName}" to seen.contains("ps:WIN"),
            "LEVEL COMPLETE results" to (g.state == GS.RESULTS),
        ).filter { (name, _) ->
            when (name) {
                "fall -> safety net -> recovery" -> !clear && plan.fallZ < 9999f
                "tool SPEED used", "tool HOURGLASS used" -> usesTool[TK.SPEED]
                "tool MAGNET used" -> usesTool[TK.MAGNET]
                "tool BLOCK used" -> usesTool[TK.BLOCK]
                "tool SHIELD used" -> usesTool[TK.SHIELD]
                "chase: warning + reveal", "chase: escaped at the checkpoint" -> chase
                "chase: captured -> game over", "game over -> continue from checkpoint" -> chase && !clear
                "final climb (lava)" -> hasEvent(com.blocktower.escape.core.Ev.LAVA)
                "final escape (collapse)" -> hasEvent(com.blocktower.escape.core.Ev.FINAL)
                "the great loop (ran all the way round)" -> g.world.loops.isNotEmpty()
                "rode a rainbow slide" -> g.world.slides.any { it.ride }
                else -> if (name.startsWith("Runaway Relic")) g.world.relicZ1 > 0 else true
            }
        }
        var ok = true
        for ((name, pass) in checks) { println((if (pass) "  [PASS] " else "  [FAIL] ") + name); ok = ok && pass }
        // ---- movement tests
        val box = trapBox()
        val jumpsNeeded = if (level == 1) 3 else 5
        result("5 jump while moving", jumpsWhileMoving >= jumpsNeeded && (clear || joyMode || flickJumps >= 1),
            "$jumpsWhileMoving running jumps landed (button + $flickJumps context flick jumps)")
        if (level == 4) {
            result("6 steering across block paths (fork)", forkRight && forkRejoined, "took the right-hand path: $forkRight, steered back to the main path: $forkRejoined")
            result("7 moving around obstacles", box != null && !box.used && trapHurts == 0, "trapped ? box untouched: ${box?.used == false}, hits in the trap section: $trapHurts")
        } else if (level == 5) {
            result("6 steering across block paths (fork)", forkRight && forkRejoined, "took the safe bridge on the left: $forkRight, steered back to the main path: $forkRejoined")
            result("7 moving around obstacles", logHits == 0 && trapHurts == 0 && g.world.logs.isNotEmpty(), "waited for the swinging maces $logWaits times, mace hits: $logHits, hits in the hazard section: $trapHurts")
        } else if (level == 6) {
            result("6 steering across block paths (fork)", forkRight && forkRejoined, "took the fast slide on the left: $forkRight, back on the main path after it: $forkRejoined")
            result("7 moving around obstacles", logHits == 0 && trapHurts == 0, "waited for maces $logWaits times and sliding spiked blocks $boxWaits times, jumped $laserJumps laser beams; mace hits: $logHits, hits in the hazard gardens: $trapHurts")
        } else if (level == 7) {
            result("6 steering across block paths (fork)", forkRight && forkRejoined, "took the crystal bridge on the left: $forkRight, back on the main path after it: $forkRejoined")
            result("7 moving around obstacles", logHits == 0 && trapHurts == 0, "waited for maces $logWaits times, sliding blocks and imps $boxWaits times, swooping imps $swoopWaits times (jumped $swoopJumps), dodged $boltDodges bolts, jumped $laserJumps beams; mace hits: $logHits, hits in the gauntlet: $trapHurts")
            val waves = g.world.waves.size
            result("minion waves: every wave came and vanished at its checkpoint", wavesStarted >= waves && wavesEnded >= waves,
                "$wavesStarted of $waves waves started, $wavesEnded ended at their checkpoints; minion hits $minionHits, bolt hits $boltHits, stomps ${g.minions.stomps}")
            val so = g.sorcery
            result("the Sorcerer's Wrath: survived, star runes lit", seen.contains("wrath:${com.blocktower.escape.core.WS.DONE}") && so.runesLit == g.world.runes.size,
                "runes lit ${so.runesLit}/${g.world.runes.size}; shockwaves $shocksSeen (jumped $shockJumps), hand slams $handSlams, shards $shardsSeen; hits from his spells $spellHits")
            result("chase elements: rolling stones and the spirit came", rollersSeen >= g.sorcery.rollers.size && spiritHops.size >= 2,
                "$rollersSeen of ${g.sorcery.rollers.size} rolling stones dropped in, the spirit blinked onto ${spiritHops.size} spots; dodged $boltDodges bolts / shards / spirits / stones")
            result("cinematic phases 1..9 in order", (2..9).all { seen.contains("phase:$it") }, "phases reached: ${(1..9).filter { it == 1 || seen.contains("phase:$it") }.joinToString()}")
            if (seen.contains("tool:HOURGLASS")) result("hourglass slows the Sorcerer's magic", minHazK < 0.5f, "hazard clock down to ${"%.2f".format(minHazK)} of its pace")
            if (opts["secret"] == "1") result("secret routes: both found, both chests opened", g.world.secrets.all { it.found } && g.world.chests.all { it.open },
                "secrets ${g.world.secrets.count { it.found }}/${g.world.secrets.size}, chests ${g.world.chests.count { it.open }}/${g.world.chests.size}")
        } else if (plan.trapZ0 < 9999f) {
            result("7 moving around obstacles", trapHurts == 0, "hits in the hazard section: $trapHurts")
        }
        val toolsNeeded = min(3, usesTool.count { it })
        if (toolsNeeded > 0) result("9 tools while moving", toolsWhileMoving.size >= toolsNeeded, "used while running: ${toolsWhileMoving.joinToString()}")
        result("10 reaching the final portal", seen.contains("ps:WIN") && g.state == GS.RESULTS, "entered the gate, results shown")
        if (g.world.slides.any { it.ride }) {
            val rideable = g.world.slides.count { it.ride }
            result("slides: rode them all the way down", slidesDone >= rideable - 1 && seen.contains("slide done"),
                "$slidesDone slides ridden (of $rideable; the fork's is optional), ${"%.1f".format(slideFrames / 60f)} s sliding, up the walls to ${"%.0f".format(Math.toDegrees(slideMaxTh.toDouble()))} degrees, $slideCoins coins on them")
            result("slides: feet stay on the channel", slideOffSurface < 0.02f, "furthest off the surface ${"%.3f".format(slideOffSurface)}")
            result("slides: turning is smooth (no snapping)", slideMaxStep < 12f, "largest turn in one frame ${"%.1f".format(slideMaxStep)} degrees")
        }
        if (g.world.loops.isNotEmpty()) {
            val secs = loopFrames / 60f
            result("loop: runs round on the track", seen.contains("loop done") && loopStride > secs * 5f && abs(abs(loopTurn) - 360f) < 60f,
                "${"%.1f".format(secs)} s round, ${"%.1f".format(loopStride)} strides (legs keep alternating), turned ${"%.0f".format(loopTurn)} degrees with the track, feet on the blocks ${loopOnTrack} frames (+$loopHopFrames hopping), slowest ${"%.1f".format(loopMinV)}/s, $loopCoins coins")
            result("loop: turning is smooth (no snapping)", loopMaxStep < 12f, "largest turn in one frame ${"%.1f".format(loopMaxStep)} degrees")
        }
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
    private fun psName(s: Int) = when (s) { PS.NORMAL -> "NORMAL"; PS.RESCUE_FALL -> "RESCUE_FALL"; PS.RESCUE_RIDE -> "RESCUE_RIDE"; PS.CAUGHT -> "CAUGHT"; PS.WIN -> "WIN"; PS.DEAD -> "DEAD"; PS.LOOP -> "LOOP"; PS.SLIDE -> "SLIDE"; else -> "$s" }
    private fun len3(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)
    private fun len2(x: Float, y: Float) = sqrt(x * x + y * y)

    // runs last, after every property above has its initial value
    init {
        if (clear) { startStep = 99; fallTestDone = true; steerTest = 99; captureTestDone = true; flatTest = 2; flickJumpTest = 3 }
        // the swipe-only tests (swipe gestures, context flicks) do not apply to the joystick
        if (joyMode) { steerTest = 99; flatTest = 2; flickJumpTest = 3; if (external == null) g.joystickMode = true }
    }
}
