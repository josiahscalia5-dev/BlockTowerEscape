package com.blocktower.escape.sim

import com.blocktower.escape.core.Game
import java.io.File
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    val opts = args.drop(1).associate { a -> a.substringBefore('=') to a.substringAfter('=', "") }
    val assets = File(opts["assets"] ?: "app/src/main/assets")
    val w = (opts["w"] ?: "540").toInt(); val h = (opts["h"] ?: "1170").toInt()
    when (args.firstOrNull() ?: "shot") {
        "shot" -> {
            val out = File(opts["out"] ?: "sim-out").apply { mkdirs() }
            val game = Game(SimPlatform(assets))
            val gfx = J2DGfx(assets, w, h)
            game.hud.layout(w, h)
            val frames = (opts["frames"] ?: "30").toInt()
            for (i in 0 until frames) game.update(1f / 60f)
            gfx.clear(); game.render(gfx)
            ImageIO.write(gfx.image, "png", File(out, "shot.png"))
            println("wrote ${File(out, "shot.png")}")
        }
        else -> error("unknown mode ${args[0]}")
    }
}
