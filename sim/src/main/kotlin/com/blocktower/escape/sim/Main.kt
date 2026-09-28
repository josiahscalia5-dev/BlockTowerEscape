package com.blocktower.escape.sim

import com.blocktower.escape.core.Game
import com.blocktower.escape.core.PS
import java.io.File
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    val opts = args.drop(1).associate { a -> a.substringBefore('=') to a.substringAfter('=', "") }
    val assets = File(opts["assets"] ?: "app/src/main/assets")
    val w = (opts["w"] ?: "540").toInt(); val h = (opts["h"] ?: "1170").toInt()
    val out = File(opts["out"] ?: "sim-out").apply { mkdirs() }
    when (args.firstOrNull() ?: "shot") {
        "shot" -> {
            val game = Game(SimPlatform(assets), (opts["level"] ?: "5").toInt())
            val gfx = J2DGfx(assets, w, h)
            game.hud.layout(w, h)
            game.hud.plain = opts["plain"] != null
            val frames = (opts["frames"] ?: "30").toInt()
            for (i in 0 until frames) game.update(1f / 60f)
            gfx.clear(); game.render(gfx)
            ImageIO.write(gfx.image, "png", File(out, opts["name"] ?: "shot.png"))
            println("wrote ${File(out, opts["name"] ?: "shot.png")}")
            val c = game.cam
            println("cam e=(%.2f, %.2f, %.2f) yaw=%.3f pitch=%.3f f=%.1f cy=%.1f  player=(%.2f, %.2f, %.2f)".format(c.ex, c.ey, c.ez, c.yaw, c.pitch, c.f, c.cy, game.player.x, game.player.y, game.player.z))
        }
        "run" -> runShots(assets, out, w, h, opts)
        "poses" -> posesSheet(assets, out, opts["pose"] ?: "")
        "play" -> Autopilot(assets, out, opts).run()
        "profile" -> profile(assets)
        "finish" -> finishFrames(assets, out, (opts["level"] ?: "5").toInt())
        "app" -> appShots(assets, out, w, h)
        "flow" -> Flow(assets, out, opts).run()
        "devices" -> Devices(assets, out, opts).run()
        "music" -> musicFiles(out)
        "l5art" -> L5Art(File("."), File(out, "l5art")).run()
        else -> error("unknown mode ${args[0]}")
    }
}

/**
 * Renders the animation rig for review: every key pose at close range (poses.png), the hips of each
 * pose magnified (poses_hips.png) and a full run cycle, a jump arc and a landing at gameplay size
 * (poses_cycle.png). Used to check that legs, hips, torso and shoes stay one connected body.
 */
private fun posesSheet(assets: File, out: File, one: String) {
    val game = Game(SimPlatform(assets))
    val cellW = 300; val cellH = 470
    val names = listOf("rest", "run A", "run B", "run pass", "run pass B", "sprint/boost", "jump up",
        "jump top", "fall", "land", "land hard", "turn L", "turn R", "skid", "collect", "cast",
        "hurt", "celebrate", "rescue", "ride", "caught", "win")
    val cols = 8
    val rows = (names.size + cols - 1) / cols
    val gfx = J2DGfx(assets, cellW * cols, cellH * rows)
    val hips = J2DGfx(assets, 260 * cols, 260 * rows)
    gfx.clear(); hips.clear()
    gfx.fillRect(0f, 0f, cellW * cols.toFloat(), cellH * rows.toFloat(), 0xFF5F86C8.toInt())
    hips.fillRect(0f, 0f, 260f * cols, 260f * rows, 0xFF7FA6D8.toInt())
    val p = game.player
    fun setup(n: String) {
        p.reset(0f, 0f, 0f)
        game.t = 1.3f
        when (n) {
            "run A" -> { p.vz = 5f; p.runW = 1f; p.runPhase = 0.5f }
            "run B" -> { p.vz = 5f; p.runW = 1f; p.runPhase = 1.5f }
            "run pass" -> { p.vz = 5f; p.runW = 1f; p.runPhase = 1.02f }
            "run pass B" -> { p.vz = 5f; p.runW = 1f; p.runPhase = 1.98f }
            "sprint/boost" -> { p.vz = 9f; p.runW = 1f; p.runPhase = 1.35f; p.lean = 0.2f }
            "jump up" -> { p.grounded = false; p.airW = 1f; p.vy = 8f; p.jumpT = 0.05f }
            "jump top" -> { p.grounded = false; p.airW = 1f; p.vy = 0.5f; p.runPhase = 1.3f }
            "fall" -> { p.grounded = false; p.airW = 1f; p.vy = -12f; game.t = 1.45f }
            "land" -> { p.landT = 0.05f; p.landAmt = 1f }
            "land hard" -> { p.landT = 0.02f; p.landAmt = 1.4f; p.runPhase = 1.3f }
            "turn L" -> { p.vx = -4f; p.lean = -0.9f; p.runW = 1f; p.runPhase = 1.5f }
            "turn R" -> { p.vx = 4f; p.lean = 0.9f; p.runW = 1f; p.runPhase = 0.5f }
            "skid" -> { p.vx = 4f; p.lean = 0.9f; p.runW = 1f; p.runPhase = 0.5f; p.skidT = 0.1f; p.skidDir = 1f }
            "collect" -> { p.collectT = 0.17f }
            "cast" -> { p.castT = 0.2f }
            "hurt" -> { p.hurtT = 0.1f }
            "celebrate" -> { p.celebrateT = 0.3f }
            "rescue" -> { p.state = PS.RESCUE_FALL; p.grounded = false; p.airW = 1f; game.t = 1.36f }
            "ride" -> { p.state = PS.RESCUE_RIDE }
            "caught" -> p.state = PS.CAUGHT
            "win" -> { p.state = PS.WIN; p.stateT = 1f }
        }
    }
    for ((i, n) in names.withIndex()) {
        setup(n)
        val pose = game.rig.computePose(p)
        val cx = (i % cols) * cellW + cellW / 2f; val cy = (i / cols) * cellH + cellH - 40f
        game.rig.draw(gfx, game.art.rig, pose, cx, cy, 400f, 1f, 0, 0f)
        gfx.text(n, cx, (i / cols) * cellH + 22f, 26f, 0, -1, 1, 3f, 0xFF000000.toInt(), 1f)
        // hips close-up: the boy drawn 2x the sprite's size, centred on the hip joint (sprite 106, 272;
        // the feet anchor is at 121, 401), so the tile shows the waist, the pants and the tops of the shoes
        val hx = (i % cols) * 260f; val hy = (i / cols) * 260f
        hips.save(); hips.clipRect(hx + 2f, hy + 2f, hx + 258f, hy + 258f)
        game.rig.draw(hips, game.art.rig, pose, hx + 130f + 15f * 2f, hy + 110f + 129f * 2f, 840f, 1f, 0, 0f)
        hips.restore()
        if (n.replace(" ", "_") == one) {
            val one = J2DGfx(assets, 700, 700); one.clear(); one.fillRect(0f, 0f, 700f, 700f, 0xFF7FA6D8.toInt())
            game.rig.draw(one, game.art.rig, pose, 350f + 15f * 3f, 300f + 129f * 3f, 1260f, 1f, 0, 0f)
            ImageIO.write(one.image, "png", File(out, "pose_one.png"))
        }
        hips.text(n, hx + 130f, hy + 22f, 22f, 0, -1, 1, 3f, 0xFF000000.toInt(), 1f)
    }
    ImageIO.write(gfx.image, "png", File(out, "poses.png"))
    ImageIO.write(hips.image, "png", File(out, "poses_hips.png"))
    // gameplay size (the boy is ~150 px tall on a 1080-wide phone at the camera's framing distance):
    // one full run cycle (two steps), a jump arc and a landing, frame by frame
    val cw = 90; val ch = 190; val n = 16
    val cyc = J2DGfx(assets, cw * n, ch * 3)
    cyc.clear(); cyc.fillRect(0f, 0f, cw * n.toFloat(), ch * 3f, 0xFF5F86C8.toInt())
    for (k in 0 until n) {
        p.reset(0f, 0f, 0f); game.t = 1.3f + k / 60f
        p.vz = 6f; p.runW = 1f; p.runPhase = k * 2f / n
        game.rig.draw(cyc, game.art.rig, game.rig.computePose(p), k * cw + cw / 2f, ch - 12f, 150f, 1f, 0, 0f)
        p.reset(0f, 0f, 0f); p.grounded = false; p.airW = 1f; p.vy = 9f - k * 18f / (n - 1); p.jumpT = k / 60f
        game.rig.draw(cyc, game.art.rig, game.rig.computePose(p), k * cw + cw / 2f, 2 * ch - 12f, 150f, 1f, 0, 0f)
        p.reset(0f, 0f, 0f); p.landT = k * 0.3f / n; p.landAmt = 1.2f; p.vz = 5f; p.runW = 1f; p.runPhase = 1f + k * 0.5f / n
        game.rig.draw(cyc, game.art.rig, game.rig.computePose(p), k * cw + cw / 2f, 3 * ch - 12f, 150f, 1f, 0, 0f)
    }
    ImageIO.write(cyc.image, "png", File(out, "poses_cycle.png"))
    println("wrote poses.png, poses_hips.png, poses_cycle.png")
}

/**
 * Puts the boy on the path at row [z] of a level and runs him forward (the keyboard's up key) for [frames] frames,
 * writing a picture every [every] frames (run_0000.png, ...) and a line of state for each: for looking at a stretch of
 * a level (a slide, a hazard) without playing up to it. `steer=x` holds him at lane x; `jump=f1,f2` jumps then.
 */
private fun runShots(assets: File, out: File, w: Int, h: Int, opts: Map<String, String>) {
    val game = Game(SimPlatform(assets), (opts["level"] ?: "6").toInt())
    val gfx = J2DGfx(assets, w, h)
    game.hud.layout(w, h)
    game.hud.plain = opts["plain"] != null
    for (i in 0 until 200) game.update(1f / 60f)
    val z = (opts["z"] ?: "0").toFloat()
    val r = kotlin.math.floor(z).toInt()
    val x = opts["x"]?.toFloat() ?: game.world.pathXAt(r)
    game.player.reset(x, game.world.levelAt(r), z)
    game.steerX = x
    val frames = (opts["frames"] ?: "240").toInt(); val every = (opts["every"] ?: "20").toInt()
    val jumps = opts["jump"]?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    game.input.kU = opts["stand"] == null
    var k = 0
    for (f in 0 until frames) {
        if (f in jumps) game.onKey(com.blocktower.escape.core.Key.JUMP, true)
        if (f - 12 in jumps) game.onKey(com.blocktower.escape.core.Key.JUMP, false)
        opts["steer"]?.toFloat()?.let { if (game.player.state == com.blocktower.escape.core.PS.NORMAL) game.steerX = it }
        game.update(1f / 60f)
        if (f % every == 0) {
            gfx.clear(); game.render(gfx)
            ImageIO.write(gfx.image, "png", File(out, "run_%04d.png".format(k)))
            val p = game.player
            println("run_%04d f=%d state=%d z=%.2f x=%.2f y=%.2f v=%.1f slide=%.1f th=%.2f rot=%.0f sy=%.2f turn=%.0f pose=%.2f cam yaw=%.2f".format(k, f, p.state, p.z, p.x, p.y,
                kotlin.math.sqrt(p.vx * p.vx + p.vz * p.vz + p.vy * p.vy), p.slideS, p.slideTh, p.loopRot, p.slideSy, p.slideTurn, p.slidePose, game.cam.yaw))
            k++
        }
    }
}

/** Times rendering of a few frames (for keeping the video recorder fast). */
fun profile(assets: File) {
    val game = Game(SimPlatform(assets))
    val gfx = J2DGfx(assets, 540, 1170)
    game.hud.layout(540, 1170)
    for (i in 0 until 240) game.update(1f / 60f)
    game.input.kU = true
    for (i in 0 until 60) game.update(1f / 60f)
    for (i in 0 until 40) { game.update(1f / 60f); gfx.clear(); game.render(gfx) }
    gfx.profileReset()
    val t0 = System.nanoTime()
    for (i in 0 until 10) { game.update(1f / 60f); gfx.clear(); game.render(gfx) }
    println("ms/frame: " + (System.nanoTime() - t0) / 1e7)
    gfx.profileDump()
}

/** Starts on the gate plaza with the objective complete and renders the finish sequence. */
fun finishFrames(assets: File, out: File, level: Int) {
    val game = Game(SimPlatform(assets), level)
    val gfx = J2DGfx(assets, 540, 1170)
    game.hud.layout(540, 1170)
    for (i in 0 until 200) game.update(1f / 60f)
    val po = game.world.portals.first()
    game.player.reset(0f, po.y, po.z - 3.2f)
    game.target = game.spec.targetNeed
    game.input.kU = true
    var f = 0
    while (game.state != com.blocktower.escape.core.GS.COMPLETE && f < 600) { game.update(1f / 60f); f++ }
    game.input.kU = false
    println("complete after $f frames, cam z=${game.cam.ez} y=${game.cam.ey} gate z=${po.z} y=${po.y}")
    for (k in 0 until 8) {
        for (i in 0 until 30) game.update(1f / 60f)
        gfx.clear(); game.render(gfx)
        ImageIO.write(gfx.image, "png", File(out, "finish$k.png"))
        println("t=${"%.1f".format(game.stateT)} state=${game.state} cam z=${"%.2f".format(game.cam.ez)} y=${"%.2f".format(game.cam.ey)} yaw=${"%.2f".format(game.cam.yaw)} pitch=${"%.2f".format(game.cam.pitch)} f=${"%.0f".format(game.cam.f)}")
    }
}

/** Screenshots of the Home screen, the level map and every popup, reached by tapping like a player. */
fun appShots(assets: File, out: File, w: Int, h: Int) {
    val app = com.blocktower.escape.core.App(SimPlatform(assets), showSplash = false)
    val gfx = J2DGfx(assets, w, h)
    app.layout(w, h)
    fun step(n: Int) { for (i in 0 until n) { app.update(1f / 60f); if (i % 4 == 0) { gfx.clear(); app.render(gfx) } } }
    fun shot(name: String) { gfx.clear(); app.render(gfx); ImageIO.write(gfx.image, "png", File(out, name)); println("wrote $name") }
    fun tap(x: Float, y: Float) { app.touchDown(0, x, y); step(2); app.touchUp(0, x, y); step(30) }
    step(40); shot("home.png")
    val c = app.home.buttonCentre(1, w.toFloat(), h.toFloat()); tap(c[0], c[1]); step(20); shot("daily.png"); app.back(); step(10)
    val m = app.home.buttonCentre(2, w.toFloat(), h.toFloat()); tap(m[0], m[1]); step(20); shot("missions.png"); app.back(); step(10)
    val v = app.home.buttonCentre(3, w.toFloat(), h.toFloat()); tap(v[0], v[1]); step(20); shot("vault.png"); app.back(); step(10)
    val st = app.home.buttonCentre(4, w.toFloat(), h.toFloat()); tap(st[0], st[1]); step(20); shot("settings.png"); app.back(); step(10)
    val mo = app.home.buttonCentre(com.blocktower.escape.core.HomeScreen.COIN_PLUS, w.toFloat(), h.toFloat()); tap(mo[0], mo[1]); step(20); shot("more.png"); app.back(); step(10)
    val p = app.home.buttonCentre(0, w.toFloat(), h.toFloat()); tap(p[0], p[1]); step(40); shot("map.png")
    val n1 = app.map.nodeCentre(1); tap(n1[0], n1[1]); step(20); shot("levelcard.png")
    // the other level cards (opened directly: those levels are still locked)
    for (n in 2..com.blocktower.escape.core.Levels.count) { app.openPopup(com.blocktower.escape.core.Pop.LEVEL, n); step(30); shot("levelcard$n.png") }
    // Level 1 finished: back on the map, Level 2's padlock shakes, pops off and the block colours in
    app.closePopup()
    app.progress.recordLevel(1, 3, 5000, 0, 0, 5, 20, 0, false, 0, true); app.justUnlocked = 2
    app.openHome(); step(30); app.openMap(); step(26)
    for (k in 0 until 6) { step(10); shot("unlock$k.png") }
    // a locked level says what opens it
    val n3 = app.map.nodeCentre(3); tap(n3[0], n3[1]); step(10); shot("locked.png")
}

/** Renders every music theme to WAV (calm and flat-out mixes, two loops each) and prints levels and timing. */
fun musicFiles(out: File) {
    val names = mapOf(com.blocktower.escape.core.Music.HOME to "home", com.blocktower.escape.core.Music.SKY to "sky", com.blocktower.escape.core.Music.VOLCANO to "volcano")
    val gains = FloatArray(com.blocktower.escape.core.Music.LAYERS)
    for ((track, name) in names) {
        val t0 = System.nanoTime()
        val layers = com.blocktower.escape.core.MusicSynth().render(track)
        val ms = (System.nanoTime() - t0) / 1e6
        val n = layers[0].size
        for (intensity in floatArrayOf(0.1f, 1f)) {
            com.blocktower.escape.core.Music.layerGains(track, intensity, gains)
            val mix = FloatArray(n * 2)
            var peak = 0f; var sum = 0.0
            for (i in 0 until n * 2) {
                var v = 0f
                for (l in layers.indices) v += layers[l][i % n] * gains[l]
                mix[i] = v; peak = kotlin.math.max(peak, kotlin.math.abs(v)); sum += v * v
            }
            val rms = kotlin.math.sqrt(sum / (n * 2))
            // the loop seam: the step across the end of the loop, next to the typical step inside it
            var steps = 0.0
            for (i in 1 until n) steps += kotlin.math.abs(mix[i] - mix[i - 1])
            val seam = kotlin.math.abs(mix[n] - mix[n - 1])
            val f = File(out, "music-$name-${if (intensity < 0.5f) "calm" else "full"}.wav")
            writeWav(f, mix)
            println("%-7s %-5s %.1f s loop, rendered in %.0f ms, peak %.2f, rms %.3f, seam step %.4f (average step %.4f) -> %s".format(
                name, if (intensity < 0.5f) "calm" else "full", n / com.blocktower.escape.core.Music.RATE.toFloat(), ms, peak, rms, seam, steps / n, f.name))
        }
    }
}

private fun writeWav(f: File, data: FloatArray) {
    val rate = com.blocktower.escape.core.Music.RATE
    val bb = java.nio.ByteBuffer.allocate(44 + data.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    bb.put("RIFF".toByteArray()); bb.putInt(36 + data.size * 2); bb.put("WAVE".toByteArray())
    bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1); bb.putInt(rate); bb.putInt(rate * 2); bb.putShort(2); bb.putShort(16)
    bb.put("data".toByteArray()); bb.putInt(data.size * 2)
    for (v in data) bb.putShort((v * 32767f).toInt().coerceIn(-32768, 32767).toShort())
    f.writeBytes(bb.array())
}
