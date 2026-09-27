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
        "poses" -> posesSheet(assets, out)
        "play" -> Autopilot(assets, out, opts).run()
        "profile" -> profile(assets)
        "finish" -> finishFrames(assets, out, (opts["level"] ?: "5").toInt())
        "app" -> appShots(assets, out, w, h)
        "flow" -> Flow(assets, out, opts).run()
        else -> error("unknown mode ${args[0]}")
    }
}

/** Renders the animation rig in its key poses side by side (for reviewing the character animation). */
private fun posesSheet(assets: File, out: File) {
    val game = Game(SimPlatform(assets))
    val cellW = 300; val cellH = 470
    val names = listOf("rest", "run A", "run B", "run pass", "jump up", "fall", "land", "turn", "collect", "hurt", "celebrate", "rescue", "caught", "win")
    val gfx = J2DGfx(assets, cellW * 7, cellH * 2)
    gfx.clear()
    gfx.fillRect(0f, 0f, cellW * 7f, cellH * 2f, 0xFF5F86C8.toInt())
    val p = game.player
    for ((i, n) in names.withIndex()) {
        p.reset(0f, 0f, 0f)
        game.t = 1.3f
        when (n) {
            "run A" -> { p.vz = 5f; p.runW = 1f; p.runPhase = 0.5f }
            "run B" -> { p.vz = 5f; p.runW = 1f; p.runPhase = 1.5f }
            "run pass" -> { p.vz = 5f; p.runW = 1f; p.runPhase = 1.02f }
            "jump up" -> { p.grounded = false; p.airW = 1f; p.vy = 8f; p.jumpT = 0.05f }
            "fall" -> { p.grounded = false; p.airW = 1f; p.vy = -12f }
            "land" -> { p.landT = 0.05f; p.landAmt = 1f }
            "turn" -> { p.vx = 4f; p.lean = 0.9f; p.runW = 1f; p.runPhase = 0.5f; p.skidT = 0.1f; p.skidDir = 1f }
            "collect" -> { p.collectT = 0.17f }
            "hurt" -> { p.hurtT = 0.1f }
            "celebrate" -> { p.celebrateT = 0.3f }
            "rescue" -> { p.state = PS.RESCUE_FALL; p.grounded = false; p.airW = 1f }
            "caught" -> p.state = PS.CAUGHT
            "win" -> { p.state = PS.WIN; p.stateT = 1f }
        }
        val pose = game.rig.computePose(p)
        val cx = (i % 7) * cellW + cellW / 2f; val cy = (i / 7) * cellH + cellH - 40f
        game.rig.draw(gfx, game.art.rig, pose, cx, cy, 400f, 1f, 0, 0f)
        gfx.text(n, cx, (i / 7) * cellH + 22f, 26f, 0, -1, 1, 3f, 0xFF000000.toInt(), 1f)
    }
    ImageIO.write(gfx.image, "png", File(out, "poses.png"))
    println("wrote poses.png")
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
    val app = com.blocktower.escape.core.App(SimPlatform(assets))
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
    val p = app.home.buttonCentre(0, w.toFloat(), h.toFloat()); tap(p[0], p[1]); step(40); shot("map.png")
    val n1 = app.map.nodeCentre(1); tap(n1[0], n1[1]); step(20); shot("levelcard.png")
    // the other level cards (opened directly: those levels are still locked)
    for (n in 2..com.blocktower.escape.core.Levels.count) { app.openPopup(com.blocktower.escape.core.Pop.LEVEL, n); step(30); shot("levelcard$n.png") }
}
