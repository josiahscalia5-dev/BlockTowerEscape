package com.blocktower.escape.core

import kotlin.math.max
import kotlin.math.min

/**
 * The popups opened from the Home screen and the level map: DAILY REWARDS, MISSIONS, PRIZE VAULT, SETTINGS,
 * the level card (objective, star goals, PLAY), the reset confirmation and "more levels coming soon".
 * They use the HUD's panel and button style, and their header icons are the Home screen's own buttons.
 */
class Menus(private val app: App) {
    private val ui get() = app.ui
    private val s get() = app.s
    private val pr get() = app.progress
    /** The pictures of the Home screen's menu buttons, cut out (design/home/cut_icons.py). */
    private val headerIcons by lazy {
        arrayOf(app.pf.loadImage("home/icon_daily.png"), app.pf.loadImage("home/icon_missions.png"),
            app.pf.loadImage("home/icon_vault.png"), app.pf.loadImage("home/icon_settings.png"))
    }
    private val guardian by lazy { app.pf.loadImage("l5/guardian.png") }
    private var claimFlash = 0f
    private var lastClaimed = -1

    fun opened(p: Int) { claimFlash = 0f; lastClaimed = -1 }
    fun move(x: Float, y: Float) = ui.move(x, y)

    // ------------------------------------------------------------------ drawing
    fun render(gr: Gfx) {
        val w = app.w; val h = app.h
        val u = easeOutBack(clamp01(app.popupT / 0.3f))
        gr.fillRect(0f, 0f, w, h, Col.withA(0xFF050A20.toInt(), 0.72f * clamp01(app.popupT / 0.15f)))
        ui.hit(BLOCK, 0f, 0f, w, h)
        claimFlash = max(0f, claimFlash - 1f / 60f)
        when (app.popup) {
            Pop.DAILY -> daily(gr, u)
            Pop.MISSIONS -> missions(gr, u)
            Pop.VAULT -> vault(gr, u)
            Pop.SETTINGS -> settings(gr, u)
            Pop.LEVEL -> levelCard(gr, u)
            Pop.RESET -> confirmReset(gr, u)
            Pop.SOON -> soon(gr, u)
            Pop.MORE -> more(gr, u)
        }
    }

    /** Panel with a header icon (from the Home screen buttons) and a title; returns [l, t, r, b]. */
    private fun frame(gr: Gfx, u: Float, halfH: Float, title: String, icon: Int): FloatArray {
        val cx = app.w * 0.5f; val cy = app.h * 0.5f + app.topInset * 0.3f
        val hw = min(470f * s, app.w * 0.47f) * u; val hh = halfH * s * u
        val l = cx - hw; val t = cy - hh; val r = cx + hw; val b = cy + hh
        ui.panel(gr, l, t, r, b)
        if (u < 0.9f) return floatArrayOf(l, t, r, b)
        if (icon >= 0) {
            // the menu's picture sits on the top edge of the panel
            val img = headerIcons[icon]
            val ih = 120f * s; val iw = ih * img.w / img.h
            gr.setAdditive(true); gr.glow(cx, t, iw * 0.85f, Col.withA(0xFF60A8FF.toInt(), 0.35f)); gr.setAdditive(false)
            gr.image(img, cx - iw / 2, t - ih * 0.62f, iw, ih)
        }
        ui.title(gr, title, cx, t + (if (icon >= 0) 110f else 70f) * s, 58f)
        ui.closeButton(gr, CLOSE, r - 20f * s, t + 20f * s)
        return floatArrayOf(l, t, r, b)
    }

    // ---- DAILY REWARDS
    private fun daily(gr: Gfx, u: Float) {
        val f = frame(gr, u, 470f, "DAILY REWARDS", 0)
        if (u < 0.9f) return
        val cx = app.w * 0.5f
        val today = app.today()
        val idx = pr.dailyShownIndex(today)
        val avail = pr.dailyAvailable(today)
        ui.label(gr, "Come back every day — the rewards grow!", cx, f[1] + 175f * s, 30f, 0xFFCFE0FF.toInt())
        val tw = 190f * s; val th = 200f * s; val gap = 16f * s
        for (d in 0..6) {
            val row = if (d < 4) 0 else 1
            val col = if (d < 4) d else d - 4
            val n = if (row == 0) 4 else 3
            val x0 = cx - (n * tw + (n - 1) * gap) / 2 + col * (tw + gap)
            val y0 = f[1] + 220f * s + row * (th + gap)
            // claimed in this streak (after the 7th day the calendar starts again)
            val done = if (avail) d < idx else (pr.dailyIndex == 0 || d < pr.dailyIndex)
            val isToday = avail && d == idx
            ui.card(gr, x0, y0, x0 + tw, y0 + th, isToday)
            if (isToday) { gr.setAdditive(true); gr.glow(x0 + tw / 2, y0 + th / 2, tw * 0.8f, Col.withA(0xFFFFE070.toInt(), 0.18f + 0.12f * pulse(app.t, 4f))); gr.setAdditive(false) }
            gr.text("DAY ${d + 1}", x0 + tw / 2, y0 + 30f * s, 28f * s, Font.TITLE, if (isToday) 0xFFFFE14A.toInt() else 0xFFCFE0FF.toInt(), Align.CENTER, 4f * s, 0xFF10205A.toInt())
            val r = Progress.DAILY[d]
            val gem = r[0] == 0
            val img = if (gem) app.baseArt.gem else app.baseArt.coinIcon
            val isz = (if (d == 6) 70f else 60f) * s
            gr.image(img, x0 + tw / 2 - isz / 2, y0 + 62f * s, isz, isz * img.h / img.w, if (done) 0.45f else 1f)
            if (d == 6) { gr.image(app.baseArt.gem, x0 + tw / 2 + 20f * s, y0 + 90f * s, 44f * s, 44f * s * app.baseArt.gem.h / app.baseArt.gem.w, if (done) 0.45f else 1f) }
            val amt = if (d == 6) "${r[0]} + ${r[1]}" else if (gem) "${r[1]}" else Ui.fmt(r[0])
            gr.text(amt, x0 + tw / 2, y0 + 160f * s, 34f * s, Font.TITLE, if (gem) 0xFFE59CFF.toInt() else 0xFFFFE14A.toInt(), Align.CENTER, 4f * s, 0xFF10205A.toInt(), if (done) 0.5f else 1f)
            if (done) { gr.fillCircle(x0 + tw - 28f * s, y0 + 28f * s, 20f * s, 0xFF2EC05A.toInt()); ui.check(gr, x0 + tw - 28f * s, y0 + 28f * s, 10f * s, Col.WHITE) }
        }
        val by = f[3] - 110f * s
        if (avail) ui.green(gr, CLAIM, cx, by, 460f * s, 110f * s, "CLAIM")
        else {
            val sec = app.secondsToTomorrow()
            ui.label(gr, "Claimed today!  Next reward in ${sec / 3600}h ${(sec / 60) % 60}m", cx, by - 20f * s, 32f, 0xFF7CFFA8.toInt())
            ui.label(gr, "Missing a day starts the streak over.", cx, by + 30f * s, 26f, 0xFF9FB4E0.toInt())
        }
    }

    // ---- MISSIONS
    private fun missions(gr: Gfx, u: Float) {
        val n = Progress.MISSIONS.size
        val rowH = 104f * s
        val f = frame(gr, u, 170f + n * 52f + 20f, "MISSIONS", 1)
        if (u < 0.9f) return
        val l = f[0] + 34f * s; val r = f[2] - 34f * s
        for (i in 0 until n) {
            val m = Progress.MISSIONS[i]
            val y0 = f[1] + 170f * s + i * rowH
            val done = pr.missionDone(i); val claimed = pr.missionClaimed[i]
            ui.card(gr, l, y0, r, y0 + rowH - 12f * s, done && !claimed)
            gr.text(m.title, l + 24f * s, y0 + 30f * s, 30f * s, Font.UI, if (claimed) 0xFF9FB4E0.toInt() else Col.WHITE, Align.LEFT)
            // progress bar
            val v = min(pr.missionValue(i), m.goal)
            val bl = l + 24f * s; val br = r - 330f * s; val bt = y0 + 56f * s; val bb = bt + 22f * s
            gr.fillRoundRect(bl, bt, br, bb, 11f * s, 0xFF223058.toInt())
            if (v > 0) gr.fillRoundRect(bl, bt, bl + (br - bl) * v / m.goal, bb, 11f * s, if (done) 0xFF4ADB6A.toInt() else 0xFF3A9CFF.toInt())
            gr.text("$v/${m.goal}", br + 12f * s, (bt + bb) / 2, 26f * s, Font.UI, 0xFFCFE0FF.toInt(), Align.LEFT)
            // reward and claim
            val rx = r - 110f * s; val ry = y0 + (rowH - 12f * s) / 2
            when {
                claimed -> { gr.fillCircle(rx, ry, 26f * s, 0xFF2EC05A.toInt()); ui.check(gr, rx, ry, 13f * s, Col.WHITE) }
                done -> ui.green(gr, MISSION0 + i, rx, ry, 190f * s, 70f * s, "CLAIM")
                else -> {
                    val gem = m.rewardGems > 0
                    val img = if (gem) app.baseArt.gem else app.baseArt.coinIcon
                    gr.image(img, rx - 70f * s, ry - 20f * s, 40f * s, 40f * s * img.h / img.w)
                    gr.text(if (gem) "${m.rewardGems}" else Ui.fmt(m.rewardCoins), rx - 22f * s, ry, 32f * s, Font.TITLE, if (gem) 0xFFE59CFF.toInt() else 0xFFFFE14A.toInt(), Align.LEFT, 4f * s, 0xFF10205A.toInt())
                }
            }
            if (lastClaimed == i && claimFlash > 0f) { gr.setAdditive(true); gr.glow(rx, ry, 120f * s, Col.withA(0xFFFFE070.toInt(), claimFlash)); gr.setAdditive(false) }
        }
    }

    // ---- PRIZE VAULT
    private fun vault(gr: Gfx, u: Float) {
        val f = frame(gr, u, 530f, "PRIZE VAULT", 2)
        if (u < 0.9f) return
        val cx = app.w * 0.5f
        ui.label(gr, "RELICS  •  one for every level you finish", cx, f[1] + 172f * s, 28f, 0xFFCFE0FF.toInt())
        val cw = 150f * s; val gap = 14f * s
        val n = Progress.RELICS.size
        for (i in 0 until n) {
            val rel = Progress.RELICS[i]
            val x0 = cx - (n * cw + (n - 1) * gap) / 2 + i * (cw + gap); val y0 = f[1] + 205f * s
            val got = pr.relics[rel.level]
            ui.card(gr, x0, y0, x0 + cw, y0 + 200f * s, got)
            val icx = x0 + cw / 2; val icy = y0 + 80f * s
            if (got) {
                gr.setAdditive(true); gr.glow(icx, icy, 70f * s, Col.withA(0xFFFFE070.toInt(), 0.35f + 0.15f * pulse(app.t + i, 2f))); gr.setAdditive(false)
                relicIcon(gr, rel.level, icx, icy, 92f * s)
                gr.text(rel.name, icx, y0 + 165f * s, 22f * s, Font.TITLE, 0xFFFFE14A.toInt(), Align.CENTER, 3f * s, 0xFF10205A.toInt())
            } else {
                gr.text("?", icx, icy, 80f * s, Font.TITLE, 0xFF3A4A80.toInt())
                gr.text("Level ${rel.level}", icx, y0 + 165f * s, 24f * s, Font.UI, 0xFF9FB4E0.toInt())
            }
        }
        // treasure chests, opened with stars
        ui.label(gr, "TREASURE CHESTS  •  earn stars to open them", cx, f[1] + 460f * s, 28f, 0xFFCFE0FF.toInt())
        val chest = headerIcons[2]
        for (i in Progress.CHESTS.indices) {
            val c = Progress.CHESTS[i]
            val x0 = cx - 430f * s + i * 290f * s; val y0 = f[1] + 495f * s
            val opened = pr.chestOpened[i]; val ready = pr.chestReady(i)
            ui.card(gr, x0, y0, x0 + 280f * s, y0 + 360f * s, ready)
            val ih = 118f * s; val iw = ih * chest.w / chest.h
            val icx = x0 + 140f * s
            if (ready) { gr.setAdditive(true); gr.glow(icx, y0 + 26f * s + ih * 0.5f, iw * 0.9f, Col.withA(0xFFFFE070.toInt(), 0.25f + 0.15f * pulse(app.t, 3f))); gr.setAdditive(false) }
            gr.image(chest, icx - iw / 2, y0 + 26f * s, iw, ih, if (opened) 0.45f else 1f)
            ui.star(gr, icx - 40f * s, y0 + 180f * s, 20f * s, true)
            gr.text("${min(pr.totalStars, c[0])}/${c[0]}", icx - 12f * s, y0 + 180f * s, 30f * s, Font.TITLE, Col.WHITE, Align.LEFT, 4f * s, 0xFF10205A.toInt())
            gr.text(Ui.fmt(c[1]) + " coins + ${c[2]} gems", icx, y0 + 230f * s, 23f * s, Font.UI, 0xFFFFE14A.toInt())
            when {
                opened -> { gr.text("OPENED", icx, y0 + 300f * s, 32f * s, Font.TITLE, 0xFF7CFFA8.toInt(), Align.CENTER, 4f * s, 0xFF10205A.toInt()) }
                ready -> ui.green(gr, CHEST0 + i, icx, y0 + 300f * s, 200f * s, 70f * s, "OPEN")
                else -> ui.button(gr, CHEST0 + i, icx, y0 + 300f * s, 200f * s, 70f * s, "LOCKED", 0, 0, false)
            }
        }
        ui.label(gr, "Blue blocks ${pr.totalBlue}  •  Coins picked up ${pr.totalCoins}  •  Levels finished ${pr.levelsDone}", cx, f[3] - 50f * s, 25f, 0xFF9FB4E0.toInt())
    }

    /** Relic pictures, made from the art each level is known for. */
    private fun relicIcon(gr: Gfx, level: Int, x: Float, y: Float, size: Float) {
        val art = app.baseArt
        val img = when (level) { 1 -> art.star; 2 -> art.magnet; 3 -> art.shield; 4 -> art.guardFace; else -> guardian }
        if (level == 5) {
            // the Temple Guardian's head
            val u0 = 95f; val v0 = 0f; val u1 = 175f; val v1 = 85f
            val hh = size; val ww = hh * (u1 - u0) / (v1 - v0)
            val q = floatArrayOf(x - ww / 2, y - hh / 2, x + ww / 2, y - hh / 2, x + ww / 2, y + hh / 2, x - ww / 2, y + hh / 2)
            gr.imageQuad(img, u0, v0, u1, v1, q, 1f)
            return
        }
        val hh = size; val ww = hh * img.w / img.h
        gr.image(img, x - ww / 2, y - hh / 2, ww, hh)
    }

    // ---- SETTINGS
    private fun settings(gr: Gfx, u: Float) {
        val f = frame(gr, u, 545f, "SETTINGS", 3)
        if (u < 0.9f) return
        val l = f[0] + 50f * s; val r = f[2] - 50f * s
        fun row(i: Int, label: String): Float {
            val y = f[1] + 220f * s + i * 130f * s
            ui.card(gr, l, y - 50f * s, r, y + 50f * s)
            gr.text(label, l + 30f * s, y, 38f * s, Font.TITLE, Col.WHITE, Align.LEFT, 4f * s, 0xFF10205A.toInt())
            return y
        }
        var y = row(0, "Sound")
        toggle(gr, SOUND, r - 110f * s, y, pr.sound)
        y = row(1, "Music")
        toggle(gr, MUSIC, r - 110f * s, y, pr.music)
        y = row(2, "Vibration")
        toggle(gr, VIBRATION, r - 110f * s, y, pr.vibration)
        y = row(3, "Controls")
        val modes = arrayOf("SWIPE", "JOYSTICK")
        for (i in 0..1) {
            val bx = r - 368f * s + i * 218f * s
            if (pr.controls == i) ui.green(gr, CTRL0 + i, bx, y, 210f * s, 70f * s, modes[i]) else ui.blue(gr, CTRL0 + i, bx, y, 210f * s, 70f * s, modes[i])
        }
        y = row(4, "Sensitivity")
        val labels = arrayOf("LOW", "NORMAL", "HIGH")
        for (i in 0..2) {
            val bx = r - 440f * s + i * 145f * s
            if (pr.sensitivity == i) ui.green(gr, SENS0 + i, bx, y, 138f * s, 70f * s, labels[i]) else ui.blue(gr, SENS0 + i, bx, y, 138f * s, 70f * s, labels[i])
        }
        ui.red(gr, RESET, app.w * 0.5f, f[1] + 880f * s, 440f * s, 90f * s, "RESET PROGRESS")
        ui.label(gr, "Block Tower Escape  •  progress is saved on this phone", app.w * 0.5f, f[3] - 50f * s, 25f, 0xFF9FB4E0.toInt())
    }

    private fun toggle(gr: Gfx, id: Int, cx: Float, cy: Float, on: Boolean) {
        val tw = 150f * s; val th = 70f * s
        ui.rr(gr, cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2, th / 2, if (on) 0xFF5AE07A.toInt() else 0xFF6A7488.toInt(), if (on) 0xFF1E9E48.toInt() else 0xFF4A5264.toInt())
        val kx = if (on) cx + tw / 2 - th / 2 else cx - tw / 2 + th / 2
        gr.fillCircle(kx, cy + 3f * s, th * 0.42f, 0x44000010)
        gr.fillCircle(kx, cy, th * 0.42f, Col.WHITE)
        gr.text(if (on) "ON" else "OFF", if (on) cx - 22f * s else cx + 22f * s, cy, 26f * s, Font.TITLE, Col.WHITE)
        ui.hit(id, cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2)
    }

    private fun confirmReset(gr: Gfx, u: Float) {
        val f = frame(gr, u, 260f, "RESET?", -1)
        if (u < 0.9f) return
        val cx = app.w * 0.5f
        ui.label(gr, "Start over from Level 1?", cx, f[1] + 160f * s, 36f)
        ui.label(gr, "Stars, coins, gems, relics and missions will be lost.", cx, f[1] + 215f * s, 28f, 0xFFFFB0A0.toInt())
        ui.red(gr, RESET_YES, cx - 170f * s, f[3] - 110f * s, 300f * s, 100f * s, "RESET")
        ui.blue(gr, RESET_NO, cx + 170f * s, f[3] - 110f * s, 300f * s, 100f * s, "CANCEL")
    }

    private fun soon(gr: Gfx, u: Float) {
        val f = frame(gr, u, 260f, "MORE LEVELS", -1)
        if (u < 0.9f) return
        val cx = app.w * 0.5f
        ui.label(gr, "You have played every level so far!", cx, f[1] + 160f * s, 34f)
        ui.label(gr, "New worlds are on the way. Try for 3 stars meanwhile.", cx, f[1] + 212f * s, 28f, 0xFFCFE0FF.toInt())
        ui.green(gr, CLOSE, cx, f[3] - 110f * s, 360f * s, 100f * s, "OK")
    }

    // ---- GET MORE (the + buttons on the Home screen): where coins and gems come from
    private fun more(gr: Gfx, u: Float) {
        val f = frame(gr, u, 470f, "GET MORE", -1)
        if (u < 0.9f) return
        val cx = app.w * 0.5f
        val art = app.baseArt
        val isz = 58f * s
        gr.image(art.coinIcon, cx - 80f * s - isz / 2, f[1] - isz * 0.55f, isz, isz * art.coinIcon.h / art.coinIcon.w)
        gr.image(art.gem, cx + 80f * s - isz / 2, f[1] - isz * 0.55f, isz, isz * art.gem.h / art.gem.w)
        ui.label(gr, "Coins and gems come from playing and from these:", cx, f[1] + 140f * s, 28f, 0xFFCFE0FF.toInt())
        val today = app.today()
        val missionsReady = Progress.MISSIONS.indices.count { pr.missionClaimable(it) }
        val chestsReady = Progress.CHESTS.indices.count { pr.chestReady(it) }
        val rows = arrayOf(
            Triple(0, "Daily Rewards", if (pr.dailyAvailable(today)) "Today's reward is waiting!" else "Claimed today, more tomorrow"),
            Triple(1, "Missions", if (missionsReady > 0) "$missionsReady reward${if (missionsReady > 1) "s" else ""} ready to claim" else "Finish missions for coins and gems"),
            Triple(2, "Prize Vault", if (chestsReady > 0) "$chestsReady chest${if (chestsReady > 1) "s" else ""} ready to open" else "Earn stars to open treasure chests"),
            Triple(-1, "Play levels", "Coins, gems and more for 3 stars"),
        )
        val l = f[0] + 40f * s; val r = f[2] - 40f * s
        for ((i, row) in rows.withIndex()) {
            val y0 = f[1] + 185f * s + i * 150f * s
            val ready = (i == 0 && pr.dailyAvailable(today)) || (i == 1 && missionsReady > 0) || (i == 2 && chestsReady > 0)
            ui.card(gr, l, y0, r, y0 + 130f * s, ready)
            val icy = y0 + 65f * s
            if (row.first >= 0) {
                val img = headerIcons[row.first]
                val ih = 92f * s; val iw = ih * img.w / img.h
                gr.image(img, l + 70f * s - iw / 2, icy - ih / 2, iw, ih)
            } else {
                val cube = art.targetCube
                gr.image(cube, l + 70f * s - 42f * s, icy - 42f * s, 84f * s, 84f * s * cube.h / cube.w)
            }
            gr.text(row.second, l + 140f * s, icy - 20f * s, 36f * s, Font.TITLE, Col.WHITE, Align.LEFT, 4f * s, 0xFF10205A.toInt())
            gr.text(row.third, l + 140f * s, icy + 24f * s, 26f * s, Font.UI, if (ready) 0xFF7CFFA8.toInt() else 0xFFB8C8E8.toInt(), Align.LEFT)
            if (row.first >= 0) ui.green(gr, MORE0 + i, r - 90f * s, icy, 140f * s, 74f * s, "GO")
            else ui.orange(gr, MORE0 + i, r - 90f * s, icy, 140f * s, 74f * s, "PLAY")
        }
    }

    // ---- the level card
    private fun levelCard(gr: Gfx, u: Float) {
        val n = app.popupLevel
        val sp = Levels.get(n)
        val f = frame(gr, u, 485f, "LEVEL $n", -1)
        if (u < 0.9f) return
        val cx = app.w * 0.5f
        val l = f[0] + 60f * s
        ui.label(gr, sp.name.uppercase(), cx, f[1] + 135f * s, 34f, 0xFF9FDBFF.toInt())
        // objectives
        var y = f[1] + 210f * s
        val cube = app.baseArt.targetCube
        gr.image(cube, l, y - 22f * s, 44f * s, 44f * s * cube.h / cube.w)
        gr.text("Collect ${sp.targetNeed} Blue Blocks", l + 64f * s, y, 34f * s, Font.UI, Col.WHITE, Align.LEFT)
        y += 58f * s
        gr.fillRoundRect(l + 6f * s, y - 20f * s, l + 38f * s, y + 20f * s, 14f * s, 0xFFFFB040.toInt())
        gr.fillRoundRect(l + 13f * s, y - 12f * s, l + 31f * s, y + 20f * s, 9f * s, 0xFFB04AE8.toInt())
        gr.text("Reach the ${sp.gateName}", l + 64f * s, y, 34f * s, Font.UI, Col.WHITE, Align.LEFT)
        if (sp.extraObjective.isNotEmpty()) {
            y += 58f * s
            gr.fillCircle(l + 22f * s, y, 18f * s, 0xFFFF6A5A.toInt()); gr.text("!", l + 22f * s, y, 28f * s, Font.TITLE, Col.WHITE)
            gr.text(sp.extraObjective, l + 64f * s, y, 34f * s, Font.UI, Col.WHITE, Align.LEFT)
        }
        // what is new
        if (sp.newThings.isNotEmpty()) {
            y += 64f * s
            gr.text("NEW IN THIS LEVEL", cx, y, 26f * s, Font.TITLE, 0xFFFFE14A.toInt(), Align.CENTER, 4f * s, 0xFF10205A.toInt())
            for (line in sp.newThings.chunked(2)) { y += 38f * s; ui.label(gr, line.joinToString("  •  "), cx, y, 28f, 0xFFFFF0B8.toInt()) }
        }
        // star goals (below whatever the level introduces)
        y = max(f[1] + 560f * s, y + 135f * s)
        gr.text("STAR GOALS", cx, y - 62f * s, 28f * s, Font.TITLE, 0xFFCFE0FF.toInt(), Align.CENTER, 4f * s, 0xFF10205A.toInt())
        val coins = app.coinTotal(n)
        val goals = arrayOf("Reach the ${sp.gateName}", "Collect ${sp.coinGoal(coins)} of ${coins} coins", "Finish with ${sp.timeStar}s left, no game over")
        val best = pr.stars[n]
        for (i in 0..2) {
            val yy = y + i * 56f * s
            for (j in 0..i) ui.star(gr, l + 20f * s + j * 34f * s, yy, 16f * s, best > i)
            gr.text(goals[i], l + 130f * s, yy, 30f * s, Font.UI, if (best > i) 0xFF7CFFA8.toInt() else 0xFFE8F0FF.toInt(), Align.LEFT)
        }
        if (pr.best[n] > 0) ui.label(gr, "Best score ${Ui.fmt(pr.best[n])}", cx, f[3] - 205f * s, 28f, 0xFF9FB4E0.toInt())
        ui.green(gr, PLAY, cx, f[3] - 110f * s, 480f * s, 120f * s, "PLAY")
    }

    // ------------------------------------------------------------------ taps
    fun tap(id: Int) {
        if (id < 0 || id == BLOCK) return
        val snd = app.pf
        when (id) {
            CLOSE -> { app.closePopup(); snd.sound(Sfx.CLICK) }
            CLAIM -> {
                val d = pr.claimDaily(app.today())
                if (d >= 0) { snd.sound(Sfx.TOOLGET); snd.sound(Sfx.COIN, 0.8f, 1.2f); app.burst(app.w * 0.5f, app.h * 0.5f, 40, 0xFFFFE070.toInt(), true); snd.haptic(false) }
            }
            PLAY -> { snd.sound(Sfx.GO, 0.7f); app.startLevel(app.popupLevel) }
            SOUND -> { pr.sound = !pr.sound; pr.save(); snd.sound(Sfx.CLICK) }
            VIBRATION -> { pr.vibration = !pr.vibration; pr.save(); snd.sound(Sfx.CLICK); snd.haptic(true) }
            MUSIC -> { pr.music = !pr.music; pr.save(); snd.sound(Sfx.CLICK) }
            RESET -> app.openPopup(Pop.RESET)
            MORE0 -> app.openPopup(Pop.DAILY)
            MORE0 + 1 -> app.openPopup(Pop.MISSIONS)
            MORE0 + 2 -> app.openPopup(Pop.VAULT)
            MORE0 + 3 -> { snd.sound(Sfx.GO, 0.6f); app.openMap() }
            RESET_YES -> { pr.reset(); snd.sound(Sfx.CRUMBLE, 0.6f); app.openPopup(Pop.SETTINGS) }
            RESET_NO -> app.openPopup(Pop.SETTINGS)
            in SENS0..SENS0 + 2 -> { pr.sensitivity = id - SENS0; pr.save(); app.applySettings(); snd.sound(Sfx.CLICK) }
            in CTRL0..CTRL0 + 1 -> { pr.controls = id - CTRL0; pr.save(); app.applySettings(); snd.sound(Sfx.CLICK) }
            in MISSION0 until MISSION0 + Progress.MISSIONS.size -> {
                if (pr.claimMission(id - MISSION0)) {
                    lastClaimed = id - MISSION0; claimFlash = 1f
                    snd.sound(Sfx.TOOLGET); snd.sound(Sfx.COIN, 0.8f, 1.2f); snd.haptic(false)
                    app.burst(app.w * 0.5f, app.h * 0.45f, 24, 0xFFFFE070.toInt())
                }
            }
            in CHEST0 until CHEST0 + Progress.CHESTS.size -> {
                if (pr.openChest(id - CHEST0)) {
                    snd.sound(Sfx.MYSTERY); snd.sound(Sfx.GEM); snd.haptic(true)
                    app.burst(app.w * 0.5f, app.h * 0.62f, 50, 0, true)
                }
            }
        }
    }

    companion object {
        const val BLOCK = 9999; const val CLOSE = 1; const val CLAIM = 2; const val PLAY = 3
        const val SOUND = 4; const val VIBRATION = 5; const val RESET = 6; const val RESET_YES = 7; const val RESET_NO = 8
        const val SENS0 = 10; const val MISSION0 = 20; const val CHEST0 = 40; const val MORE0 = 50; const val CTRL0 = 60; const val MUSIC = 70
    }
}
