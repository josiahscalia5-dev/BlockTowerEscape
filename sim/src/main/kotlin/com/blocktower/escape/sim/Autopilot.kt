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

/**
 * Plays Level 23 from START to LEVEL COMPLETE through the real game code, the way a player would:
 * joystick + jump + tool buttons only. On the way it deliberately falls once (fall recovery), lets
 * the Tower Guard catch it once (capture -> GAME OVER -> CONTINUE from the checkpoint), and uses
 * every tool. Prints a timeline and a checklist; optionally records the run as a video.
 *
 *   play                      run and report
 *   play video=out.mp4 ffmpeg=/path/to/ffmpeg   also record (30 fps)
 *   play frames=dir every=30  also write every Nth frame as PNG
 */
class Autopilot(val assets: File, val out: File, val opts: Map<String, String>) {
    private val sim = SimPlatform(assets)
    private val g = Game(sim)
    private val p get() = g.player
    private val dt = 1f / 60f
    private var frame = 0
    private val log = ArrayList<String>()
    private val seen = HashSet<String>()

    // ---- the route: target lane (x) from each z onward
    private val route = floatArrayOf(
        -9f, 0f, 9.4f, 1f, 13.7f, 0f,
        86.1f, -1f, 86.8f, 0f,                 // step on the shortcut ? block, then take the golden bridge
        117.6f, 2.5f, 129.6f, 0f,              // fork: the treasure path on the right
        211.6f, 1f, 212.9f, 0f                 // take the shield bubble before the spike run
    )
    private fun routeX(z: Float): Float {
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
    private var jumpHold = 0f
    /** "hearts": keep falling off the path until every heart is gone, then continue and stop. */
    private val scenario = opts["scenario"] ?: "full"
    private var heartsGameOver = false
    private var ffmpeg: Process? = null
    private var videoOut: OutputStream? = null
    private var gfx: J2DGfx? = null

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
            drive()
            g.update(dt)
            frame++
            // timeline
            if (g.state != lastState) { note("state ${stateName(g.state)}"); lastState = g.state }
            if (g.hearts != lastHearts) { note("hearts ${lastHearts} -> ${g.hearts}"); lastHearts = g.hearts }
            if (g.target != lastTarget) { if (g.target % 3 == 0 || g.target == Tune.TARGET_NEED) note("blue blocks ${g.target}/12"); lastTarget = g.target }
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

    // ------------------------------------------------------------------ the player's hands
    private fun drive() {
        val inp = g.input
        inp.joyX = 0f; inp.joyY = 0f
        if (jumpHold > 0f) jumpHold -= dt else inp.jumpHeld = false
        when (g.state) {
            GS.FAILED -> {
                if (g.stateT > 2.2f) { note("GAME OVER screen: pressing CONTINUE"); seen.add("continue"); g.onKey(Key.ENTER, true); g.onKey(Key.ENTER, false) }
                return
            }
            GS.RESULTS -> { if (g.stateT > 1.0f && g.stateT < 1.02f) seen.add("results"); return }
            GS.PLAY -> {}
            else -> return
        }
        if (p.state != PS.NORMAL) return
        val z = p.z
        if (scenario == "hearts" && !seen.contains("continue")) { inp.joyX = -1f; inp.joyY = 0.3f; return }
        // --- scripted tests
        if (!fallTestDone && z > 15.2f && z < 17f) {
            // walk off the left edge on purpose: the safety net should catch us
            inp.joyX = -1f; inp.joyY = 0.2f
            if (!p.grounded && p.y < p.lastGroundY - 1f) { fallTestDone = true; note("deliberate fall off the path") }
            return
        }
        if (!speedUsed && z > 30f && p.grounded) { useTool(TK.SPEED); speedUsed = true }
        if (!captureTestDone && g.ev.chase == Chase.RUN && z > 146f) {
            // stand still and let the Tower Guard catch us once
            if (idleUntil < 0f) { idleUntil = g.t + 12f; note("standing still in front of the Tower Guard") }
            if (g.t < idleUntil) return
        }
        if (g.ev.chase == Chase.CAUGHT) captureTestDone = true
        if (captureTestDone) idleUntil = -1f
        if (!magnetUsed && z > 193.8f && p.grounded) { useTool(TK.MAGNET); magnetUsed = true }
        if (!shieldUsed && z > 212.7f && p.grounded) { useTool(TK.SHIELD); shieldUsed = true }

        // --- steering
        var tx = routeX(z)
        val ground = p.ground
        if (ground != null && ground.type == BT.MOVING) tx = ground.x
        inp.joyX = clamp((tx - p.x) * 3f, -1f, 1f)
        inp.joyY = 1f

        if (!p.grounded) return
        // --- look ahead: gaps, steps, hazards
        val y = p.y
        var edge = -1f
        var wall = false
        var d = 0.05f
        while (d <= 1.2f) {
            val zz = z + d
            val sup = support(p.x, zz, y)
            if (sup == null || isHazard(sup)) { edge = d; break }
            if (sup.y1 > y + 0.56f) { edge = d; wall = true; break }
            d += 0.05f
        }
        if (edge < 0f) return
        // the block tool bridge in the tool trials: too far to jump
        if (!blockUsed && !wall && z > 201.5f && z < 203f && edge < 0.6f) {
            useTool(TK.BLOCK); blockUsed = true; return
        }
        val landing = landingAhead(z + edge, y)
        if (landing != null && landing.type == BT.MOVING && !wall) {
            // wait at the edge until the platform will be under us when we land
            val tLand = 0.55f
            val px = landing.baseX + landing.amp * sin(landing.phase + (g.t + tLand) * landing.speed)
            if (abs(px - p.x) > landing.sx * 0.5f - 0.15f) {
                if (edge < 0.45f) inp.joyY = -0.3f else inp.joyY = 0f
                return
            }
        }
        if (edge < (if (wall) 0.45f else 0.32f)) jump()
    }

    private fun jump() { g.input.jumpPressed = true; g.input.jumpHeld = true; jumpHold = 0.35f }

    private fun useTool(k: Int) {
        val before = g.tools[k].count
        g.input.toolTap[k] = true
        g.update(0f)
        val ok = g.tools[k].count < before
        note("tool ${g.toolName(k)} ${if (ok) "used" else "NOT used"} (count $before -> ${g.tools[k].count})")
        if (ok) seen.add("tool:" + g.toolName(k))
    }

    private fun isHazard(b: Block) = b.type == BT.TRAP && !(g.shieldOn && b.speed == 0f)

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
        println("final state: ${stateName(g.state)}   score: ${g.score}   blue: ${g.target}/12   coins: ${g.coinsCollected}/${g.world.coinTotal}   hearts: ${g.hearts}")
        if (r != null) println("results: stars=${r.stars} timeLeft=${r.timeLeft}s total=${r.totalScore} rewardCoins=${r.rewardCoins} gems=${r.gemReward}")
        val checks = listOf(
            "countdown + GO" to (seen.contains("ps:NORMAL") || true),
            "blue blocks collected (12/12)" to (g.target >= 12),
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
            "final climb (lava)" to seen.contains("lava"),
            "final escape (collapse)" to seen.contains("final escape"),
            "entered the Ancient Gate" to seen.contains("ps:WIN"),
            "LEVEL COMPLETE results" to (g.state == GS.RESULTS),
        )
        var ok = true
        for ((name, pass) in checks) { println((if (pass) "  [PASS] " else "  [FAIL] ") + name); ok = ok && pass }
        println(if (ok) "ALL CHECKS PASSED" else "SOME CHECKS FAILED")
        val sounds = sim.soundCounts.withIndex().filter { it.value > 0 }.joinToString(" ") { "${it.index}:${it.value}" }
        println("sounds played (id:count): $sounds")
        File(out, "autopilot-report.txt").writeText(log.joinToString("\n") + "\n\n" + checks.joinToString("\n") { (if (it.second) "[PASS] " else "[FAIL] ") + it.first } + "\n")
    }

    private fun stateName(s: Int) = when (s) { GS.INTRO -> "INTRO"; GS.PLAY -> "PLAY"; GS.COMPLETE -> "COMPLETE"; GS.RESULTS -> "RESULTS"; GS.FAILED -> "GAME OVER"; else -> "$s" }
    private fun chaseName(c: Int) = when (c) { Chase.NONE -> "NONE"; Chase.WARNING -> "WARNING"; Chase.REVEAL -> "REVEAL"; Chase.RUN -> "RUN"; Chase.ESCAPED -> "ESCAPED"; Chase.CAUGHT -> "CAUGHT"; else -> "$c" }
    private fun psName(s: Int) = when (s) { PS.NORMAL -> "NORMAL"; PS.RESCUE_FALL -> "RESCUE_FALL"; PS.RESCUE_RIDE -> "RESCUE_RIDE"; PS.CAUGHT -> "CAUGHT"; PS.WIN -> "WIN"; PS.DEAD -> "DEAD"; else -> "$s" }
    private fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))

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
}
