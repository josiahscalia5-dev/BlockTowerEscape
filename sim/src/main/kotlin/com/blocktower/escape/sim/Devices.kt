package com.blocktower.escape.sim

import com.blocktower.escape.core.App
import com.blocktower.escape.core.GS
import com.blocktower.escape.core.Pop
import com.blocktower.escape.core.Scr
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The responsive-layout audit: every screen of the app drawn on a range of Android display shapes (short and
 * tall phones, tablets, a foldable), each with the insets that phone has (camera cut-out at the top, gesture
 * bar at the bottom, drawn as translucent red bands). Writes one comparison sheet per screen to devices/.
 *
 *   devices out=sim-out            all screens
 *   devices out=sim-out only=home  one screen (home, daily, missions, vault, settings, map, card, level1..level5,
 *                                  pause, gameover, results, results1)
 */
class Devices(val assets: File, val out: File, val opts: Map<String, String>) {
    /** A display: size in pixels and its safe-area insets (cut-out top, gesture area bottom). */
    class Dev(val name: String, val w: Int, val h: Int, val top: Int, val bottom: Int)

    private val devs = listOf(
        Dev("16:9 phone", 540, 960, 0, 0),
        Dev("18:9 phone", 540, 1080, 30, 0),
        Dev("19.5:9 notch", 540, 1170, 45, 24),
        Dev("20:9 punch-hole", 540, 1200, 50, 24),
        Dev("21:9 tall", 540, 1260, 40, 24),
        Dev("16:10 tablet", 600, 960, 0, 24),
        Dev("4:3 tablet", 720, 960, 0, 24),
        Dev("foldable", 760, 950, 0, 20),
    )
    private val only = opts["only"]
    private val shots = LinkedHashMap<String, MutableList<Pair<Dev, BufferedImage>>>()

    fun run() {
        val dir = File(out, "devices").apply { mkdirs() }
        for (d in devs) {
            println("device ${d.name} ${d.w}x${d.h} insets ${d.top}/${d.bottom}")
            screens(d)
        }
        for ((name, list) in shots) {
            ImageIO.write(sheet(list), "png", File(dir, "$name.png"))
            println("wrote devices/$name.png")
        }
    }

    private fun screens(d: Dev) {
        val app = App(SimPlatform(assets))
        app.dayOverride = 20_000
        val gfx = J2DGfx(assets, d.w, d.h)
        app.setInsets(d.top.toFloat(), d.bottom.toFloat())
        app.layout(d.w, d.h)
        fun step(n: Int) = repeat(n) { app.update(1f / 60f); gfx.clear(); app.render(gfx) }
        fun settle() { var i = 0; while (app.fading && i < 300) { step(1); i++ }; step(20) }
        fun want(name: String) = only == null || only == name
        fun grab(name: String) {
            gfx.clear(); app.render(gfx)
            val img = BufferedImage(d.w, d.h, BufferedImage.TYPE_INT_RGB)
            img.createGraphics().apply { drawImage(gfx.image, 0, 0, null); dispose() }
            shots.getOrPut(name) { ArrayList() }.add(d to img)
        }
        step(40)
        if (want("home")) grab("home")
        for ((p, name) in listOf(Pop.DAILY to "daily", Pop.MISSIONS to "missions", Pop.VAULT to "vault", Pop.SETTINGS to "settings")) {
            if (!want(name)) continue
            app.openPopup(p); step(30); grab(name); app.closePopup(); step(5)
        }
        if (want("map") || want("card")) {
            app.openMap(); settle()
            if (want("map")) grab("map")
            if (want("card")) { app.openPopup(Pop.LEVEL, 4); step(30); grab("card"); app.closePopup() }
        }
        for (n in 1..5) {
            val name = "level$n"
            val extra = n == 4 && (want("pause") || want("gameover") || want("results"))
            val extra1 = n == 1 && want("results1")
            if (!want(name) && !extra && !extra1) continue
            app.startLevel(n); settle()
            val g = app.game ?: continue
            var i = 0
            while (g.state != GS.PLAY && i < 600) { step(1); i++ }
            step(90)
            if (want(name)) grab(name)
            if (n == 4 && want("pause")) { g.togglePause(); step(30); grab("pause"); g.togglePause(); step(5) }
            if (n == 4 && want("gameover")) { g.fail("CAUGHT BY THE TOWER GUARD!"); step(200); grab("gameover"); g.continueFromCheckpoint(); step(5) }
            if ((n == 4 && want("results")) || extra1) {
                val po = g.world.portals.first()
                g.player.reset(po.x, po.y, po.z - 3.2f)
                g.target = g.spec.targetNeed
                g.input.kU = true
                i = 0
                while (!(g.state == GS.RESULTS && g.hud.resultsRevealed()) && i < 1800) { step(1); i++ }
                g.input.kU = false
                step(40)
                grab(if (n == 1) "results1" else "results")
            }
            app.exitToMap(g); settle()
            check(app.screen == Scr.MAP)
        }
    }

    /** All devices side by side at the same height, labelled, with their insets shaded. */
    private fun sheet(list: List<Pair<Dev, BufferedImage>>): BufferedImage {
        val th = 720; val gap = 14; val label = 34
        val widths = list.map { (d, _) -> th * d.w / d.h }
        val img = BufferedImage(widths.sum() + gap * (list.size + 1), th + label + gap * 2, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(40, 40, 48); g.fillRect(0, 0, img.width, img.height)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 17)
        var x = gap
        for ((i, p) in list.withIndex()) {
            val (d, shot) = p
            val w = widths[i]
            g.drawImage(shot, x, label + gap, w, th, null)
            val k = th.toFloat() / d.h
            g.color = Color(255, 40, 40, 70)
            if (d.top > 0) g.fillRect(x, label + gap, w, (d.top * k).toInt())
            if (d.bottom > 0) g.fillRect(x, label + gap + th - (d.bottom * k).toInt(), w, (d.bottom * k).toInt())
            g.color = Color.WHITE
            g.drawString(d.name, x, label)
            x += w + gap
        }
        g.dispose()
        return img
    }
}
