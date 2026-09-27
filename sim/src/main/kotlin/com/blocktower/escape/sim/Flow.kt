package com.blocktower.escape.sim

import com.blocktower.escape.core.App
import com.blocktower.escape.core.GS
import com.blocktower.escape.core.Levels
import com.blocktower.escape.core.Menus
import com.blocktower.escape.core.Pop
import com.blocktower.escape.core.Progress
import com.blocktower.escape.core.Scr
import com.blocktower.escape.core.Sfx
import java.io.File
import javax.imageio.ImageIO

/**
 * The whole app from a fresh install, driven the way a player would: every step is a tap on the screen sent
 * through the app's own touch handlers, and the levels are played by the [Autopilot] inside the app.
 *
 *   Home -> DAILY REWARDS (claim, the next day, a missed day) -> PLAY -> level map (a locked level stays shut)
 *   -> Level 1 card -> PLAY -> pause -> LEVEL MAP -> Level 1 again, played to the results -> NEXT LEVEL
 *   -> Levels 2..5 (Level 3 leaves through LEVEL MAP to see the new level on the map) -> "more levels soon"
 *   -> MISSIONS (claim) -> PRIZE VAULT (relics, chests) -> SETTINGS (sound, swipe sensitivity, reset -> cancel)
 *   -> the app is closed and opened again: everything is still there -> RESET -> a fresh start
 *
 *   flow            run and print a pass/fail checklist (also written to flow-report.txt)
 *   flow shots=1    also save a screenshot of each screen on the way (flow-*.png)
 *   flow video=tour.mp4 ffmpeg=/path/to/ffmpeg   also record it all as a video (30 fps), lingering on each screen
 */
class Flow(val assets: File, val out: File, val opts: Map<String, String>) {
    private val w = (opts["w"] ?: "540").toInt()
    private val h = (opts["h"] ?: "1170").toInt()
    private val saveFile = File(out, "flow-save.txt").also { it.delete() }
    private val sim = SimPlatform(assets).also { it.saveFile = saveFile }
    private val app = App(sim)
    private val gfx = J2DGfx(assets, w, h)
    private val shots = opts["shots"] != null
    private val video = opts["video"]?.let { VideoWriter(File(it), w, h, opts["ffmpeg"] ?: "ffmpeg", (opts["crf"] ?: "28").toInt()) }
    private val dt = 1f / 60f
    private var frame = 0
    private val log = ArrayList<String>()
    private val checks = ArrayList<Pair<String, Boolean>>()

    private fun note(s: String) { val line = "%7.1fs  %s".format(frame / 60f, s); log.add(line); println(line) }
    private fun check(name: String, pass: Boolean, detail: String = "") {
        checks.add(name to pass)
        note((if (pass) "PASS: " else "FAIL: ") + name + (if (detail.isNotEmpty()) " — $detail" else ""))
    }

    // ------------------------------------------------------------------ fingers
    private fun render() { gfx.clear(); app.render(gfx) }
    /** Every other frame goes into the video (60 fps game, 30 fps video). */
    private fun record(drawn: Boolean) { val v = video ?: return; if (frame % 2 == 0) { if (!drawn) render(); v.write(gfx.image) } }
    /** Runs the app for [n] frames, drawing each one (menu buttons register where they are drawn). */
    private fun step(n: Int) = repeat(n) { app.update(dt); frame++; render(); record(true) }
    /** When recording, stays on the screen for [seconds] so it can be seen. */
    private fun linger(seconds: Float) { if (video != null) step((seconds * 60f).toInt()) }
    /** Waits for a screen change to fade through and the new screen to settle. */
    private fun settle() { var i = 0; while (app.fading && i < 300) { step(1); i++ }; step(24) }
    private fun shot(name: String, hold: Float = 1.8f) {
        linger(hold)
        if (!shots) return
        render(); ImageIO.write(gfx.image, "png", File(out, "flow-$name.png"))
    }
    private fun tap(x: Float, y: Float) { app.touchDown(0, x, y); step(2); app.touchUp(0, x, y); step(2) }
    /** Taps a menu button where it was drawn in the last frame. */
    private fun tapId(id: Int): Boolean {
        val c = app.ui.centreOf(id) ?: run { note("button $id is not on screen"); return false }
        tap(c[0], c[1]); step(12); return true
    }
    private fun tapHome(b: Int) { val c = app.home.buttonCentre(b, w.toFloat(), h.toFloat()); tap(c[0], c[1]); step(30); settle() }
    private fun tapLevel(n: Int) { val c = app.map.nodeCentre(n); tap(c[0], c[1]); step(30) }
    private fun sounds() = sim.soundCounts.sum()

    // ------------------------------------------------------------------ the run
    fun run() {
        app.layout(w, h)
        app.dayOverride = 20_000
        step(40)
        val pr = app.progress
        shot("01-home")
        check("fresh install: Level 1 open, the rest locked, starting wallet",
            pr.unlocked == 1 && pr.totalStars == 0 && pr.coins == Progress.START_COINS && pr.gems == Progress.START_GEMS,
            "unlocked ${pr.unlocked}, ${pr.coins} coins, ${pr.gems} gems")

        dailyRewards()
        firstVisitToTheMap()
        for (n in 1..Levels.count) playFromStartToResults(n)
        afterTheLastLevel()
        missions()
        vault()
        settings()
        reopenTheApp()
        resetProgress()
        video?.let { it.close(); note("video: ${it.frames} frames (${"%.0f".format(it.frames / 30f)} s) -> ${opts["video"]}") }
        report()
    }

    private fun dailyRewards() {
        val pr = app.progress
        tapHome(1)
        check("DAILY REWARDS opens from its Home button", app.popup == Pop.DAILY)
        shot("02-daily")
        val c0 = pr.coins
        tapId(Menus.CLAIM); step(20)
        check("day 1 reward claimed (+${Progress.DAILY[0][0]} coins)", pr.coins == c0 + Progress.DAILY[0][0] && !pr.dailyAvailable(app.today()), "${pr.coins} coins")
        check("no second claim on the same day", app.ui.centreOf(Menus.CLAIM) == null)
        shot("03-daily-claimed")
        tapId(Menus.CLOSE)
        check("close button closes the popup", app.popup == Pop.NONE)
        // the next day: day 2 of the streak
        app.dayOverride = 20_001
        tapHome(1)
        val c1 = pr.coins
        tapId(Menus.CLAIM); step(20)
        check("next day: day 2 reward (+${Progress.DAILY[1][0]} coins)", pr.coins == c1 + Progress.DAILY[1][0] && pr.dailyIndex == 2, "${pr.coins} coins")
        check("Android back closes a popup", app.back() && app.popup == Pop.NONE)
        step(10)
        // a day missed: the streak starts over (not claimed, so the next check sees it still waiting)
        app.dayOverride = 20_003
        tapHome(1)
        check("a missed day starts the streak over", pr.dailyShownIndex(app.today()) == 0 && app.ui.centreOf(Menus.CLAIM) != null)
        tapId(Menus.CLOSE)
    }

    private fun firstVisitToTheMap() {
        val pr = app.progress
        tapHome(0)
        check("PLAY opens the level map", app.screen == Scr.MAP)
        shot("04-map")
        val wrong = sim.soundCounts[Sfx.WRONG]
        tapLevel(2)
        check("a locked level stays shut", app.popup == Pop.NONE && sim.soundCounts[Sfx.WRONG] > wrong)
        tapLevel(1)
        check("Level 1 opens its level card", app.popup == Pop.LEVEL && app.popupLevel == 1)
        shot("05-level1-card")
        tapId(Menus.PLAY); settle()
        val g = app.game
        check("PLAY on the card starts Level 1", app.screen == Scr.GAME && g != null && g.spec.number == 1)
        if (g == null) return
        check("Level 1: every tool is still locked", (0..3).all { g.toolLocked(it) })
        // pause -> LEVEL MAP leaves the level without finishing it
        var i = 0
        while (g.state != GS.PLAY && i < 600) { step(1); i++ }
        step(30)
        tap(g.hud.ax(975f), g.hud.ayT(55f))
        check("the pause button pauses", g.paused)
        step(20)
        shot("06-paused")
        val m = g.hud.overlayButtonCentre(2); tap(m[0], m[1]); settle()
        check("pause -> LEVEL MAP returns to the map", app.screen == Scr.MAP && app.game == null && pr.stars[1] == 0)
        tapLevel(1)
        tapId(Menus.PLAY); settle()
    }

    /** Plays level [n] inside the app until its results are on screen, then checks what was saved. */
    private fun playFromStartToResults(n: Int) {
        val pr = app.progress
        val g = app.game
        if (g == null || g.spec.number != n) { check("Level $n is being played", false, "game: ${g?.spec?.number}"); return }
        val coins0 = pr.coins; val gems0 = pr.gems
        check("Level $n starts with the saved wallet", g.coins == coins0 && g.gems == gems0, "HUD ${g.coins} / ${g.gems}, saved $coins0 / $gems0")
        val open = (0..3).filter { !g.toolLocked(it) }.map { g.toolName(it) }
        val expected = (0..3).filter { n >= Levels.toolUnlock[it] }.map { g.toolName(it) }
        check("Level $n: tools available: ${open.joinToString().ifEmpty { "none" }}", open == expected)
        g.hud.layout(w, h)
        val ap = Autopilot(assets, out, mapOf("scenario" to "clear"), external = g, stepper = { app.update(it); frame++ }, frameHook = { record(false) })
        val done = ap.playLevel(420)
        val r = g.results
        step(30)
        shot("%02d-level$n-results".format(6 + n), 2.5f)
        if (!done || r == null) { check("Level $n played to LEVEL COMPLETE", false, "state ${g.state}"); return }
        check("Level $n played to LEVEL COMPLETE", true, "${r.stars} stars, ${r.targetGot}/${g.spec.targetNeed} blue, ${r.coinsCollected}/${r.coinTotal} coins, ${r.timeLeft}s left")
        check("Level $n: stars, best score and relic saved; Level ${n + 1} unlocked",
            pr.stars[n] == r.stars && pr.best[n] == r.totalScore && pr.relics[n] && pr.unlocked == n + 1,
            "stars ${pr.stars[n]}, best ${pr.best[n]}, unlocked ${pr.unlocked}")
        check("Level $n: wallet += coins and gems picked up + level reward",
            pr.coins == coins0 + r.earnedCoins + r.rewardCoins && pr.gems == gems0 + r.earnedGems + r.gemReward,
            "coins $coins0 + ${r.earnedCoins} + ${r.rewardCoins} = ${pr.coins}, gems $gems0 + ${r.earnedGems} + ${r.gemReward} = ${pr.gems}")
        step(90)   // every reward flyer has reached the HUD
        check("Level $n: the HUD wallet ends where the saved wallet is", g.coins == pr.coins && g.gems == pr.gems,
            "HUD ${g.coins} / ${g.gems}, saved ${pr.coins} / ${pr.gems}")
        if (n == 3) {
            // leave through LEVEL MAP: the new level is waiting on the map
            val m = g.hud.overlayButtonCentre(2); tap(m[0], m[1]); settle()
            check("results -> LEVEL MAP shows the map with Level 4 new", app.screen == Scr.MAP && app.game == null && app.justUnlocked == 4)
            shot("10-map-level4-new")
            tapLevel(4)
            check("Level 4 card opens", app.popup == Pop.LEVEL && app.popupLevel == 4)
            shot("11-level4-card")
            tapId(Menus.PLAY); settle()
        } else {
            val c = g.hud.overlayButtonCentre(0); tap(c[0], c[1]); settle()
            if (n < Levels.count) check("NEXT LEVEL starts Level ${n + 1}", app.screen == Scr.GAME && app.game?.spec?.number == n + 1)
        }
    }

    private fun afterTheLastLevel() {
        val pr = app.progress
        check("after the last level, NEXT LEVEL shows the map and 'more levels soon'", app.screen == Scr.MAP && app.popup == Pop.SOON)
        shot("13-soon")
        tapId(Menus.CLOSE)
        check("OK closes it", app.popup == Pop.NONE)
        check("every level finished with at least one star", (1..Levels.count).all { pr.stars[it] >= 1 }, (1..Levels.count).joinToString(" ") { "L$it:${pr.stars[it]}" })
        shot("14-map-all-done")
        check("Android back on the map goes Home", app.back()); settle()
        check("... to the Home screen", app.screen == Scr.HOME)
    }

    private fun missions() {
        val pr = app.progress
        tapHome(2)
        check("MISSIONS opens from its Home button", app.popup == Pop.MISSIONS)
        shot("15-missions")
        val must = listOf(0, 1, 6, 7, 8)   // complete Level 1, 20 blue blocks, a flawless level, a chase, complete Level 5
        check("missions completed by playing: ${must.joinToString { Progress.MISSIONS[it].title }}", must.all { pr.missionDone(it) },
            Progress.MISSIONS.indices.joinToString(" ") { "${it}:${pr.missionValue(it)}/${Progress.MISSIONS[it].goal}" })
        var expectC = pr.coins; var expectG = pr.gems; var claimed = 0
        for (i in Progress.MISSIONS.indices) {
            if (!pr.missionClaimable(i)) continue
            expectC += Progress.MISSIONS[i].rewardCoins; expectG += Progress.MISSIONS[i].rewardGems
            if (tapId(Menus.MISSION0 + i)) claimed++
        }
        check("CLAIM pays each finished mission once", pr.coins == expectC && pr.gems == expectG && !pr.anyMissionClaimable,
            "$claimed claimed, ${pr.coins} coins, ${pr.gems} gems")
        shot("16-missions-claimed")
        tapId(Menus.CLOSE)
    }

    private fun vault() {
        val pr = app.progress
        tapHome(3)
        check("PRIZE VAULT opens from its Home button", app.popup == Pop.VAULT)
        check("a relic for every finished level", (1..Levels.count).all { pr.relics[it] })
        val ready = Progress.CHESTS.indices.filter { pr.chestReady(it) }
        check("chests are ready for the stars earned (${pr.totalStars} stars)", ready == Progress.CHESTS.indices.filter { pr.totalStars >= Progress.CHESTS[it][0] })
        shot("17-vault")
        var expectC = pr.coins; var expectG = pr.gems
        for (i in ready) { expectC += Progress.CHESTS[i][1]; expectG += Progress.CHESTS[i][2]; tapId(Menus.CHEST0 + i); step(20) }
        check("OPEN pays each ready chest once", pr.coins == expectC && pr.gems == expectG && ready.all { pr.chestOpened[it] } && Progress.CHESTS.indices.none { pr.chestReady(it) },
            "${ready.size} opened, ${pr.coins} coins, ${pr.gems} gems")
        shot("18-vault-opened")
        tapId(Menus.CLOSE)
    }

    private fun settings() {
        val pr = app.progress
        tapHome(4)
        check("SETTINGS opens from its Home button", app.popup == Pop.SETTINGS)
        tapId(Menus.SOUND)
        val before = sounds()
        tapId(Menus.SENS0 + 2)
        check("sound off: taps are silent", !pr.sound && sounds() == before)
        check("swipe sensitivity set to HIGH", pr.sensitivity == 2)
        tapId(Menus.SOUND)
        check("sound back on", pr.sound)
        shot("19-settings")
        tapId(Menus.RESET)
        check("RESET PROGRESS asks first", app.popup == Pop.RESET)
        shot("20-reset-confirm")
        tapId(Menus.RESET_NO)
        check("CANCEL keeps everything", app.popup == Pop.SETTINGS && pr.unlocked == Levels.count + 1 && pr.totalStars > 0)
        tapId(Menus.CLOSE)
        // the new sensitivity is used by the next level
        tapHome(0); tapLevel(2); tapId(Menus.PLAY); settle()
        val g = app.game
        check("a replayed level uses the HIGH swipe sensitivity", g != null && g.spec.number == 2 && g.swipe.sensitivity == App.SENSITIVITY[2])
        // the app goes to the background mid-level: the game pauses and progress is saved
        var i = 0
        while (g != null && g.state != GS.PLAY && i < 600) { step(1); i++ }
        app.onPause()
        check("going to the background pauses the level", g != null && g.paused)
        step(10)   // the pause panel is drawn (its buttons are where it draws them)
        val m = g?.hud?.overlayButtonCentre(2)
        if (m != null) { tap(m[0], m[1]); settle() }
        check("an unfinished replay keeps the best stars", app.screen == Scr.MAP && pr.stars[2] >= 1)
        app.back(); settle()
    }

    /** Closes the app and opens it again from what was saved. */
    private fun reopenTheApp() {
        val a = app.progress
        val sim2 = SimPlatform(assets).also { it.saveFile = saveFile }
        val b = App(sim2).progress
        val same = a.unlocked == b.unlocked && a.stars.contentEquals(b.stars) && a.best.contentEquals(b.best) && a.relics.contentEquals(b.relics) &&
            a.coins == b.coins && a.gems == b.gems && a.missionClaimed.contentEquals(b.missionClaimed) && a.chestOpened.contentEquals(b.chestOpened) &&
            a.dailyIndex == b.dailyIndex && a.lastClaimDay == b.lastClaimDay && a.sound == b.sound && a.vibration == b.vibration &&
            a.sensitivity == b.sensitivity && a.levelsDone == b.levelsDone && a.totalBlue == b.totalBlue && a.chases == b.chases
        check("reopening the app restores everything", same, "unlocked ${b.unlocked}, stars ${b.totalStars}, ${b.coins} coins, ${b.gems} gems, sensitivity ${b.sensitivity}")
    }

    private fun resetProgress() {
        val pr = app.progress
        tapHome(4); tapId(Menus.RESET); tapId(Menus.RESET_YES)
        check("RESET starts over (settings kept)", pr.unlocked == 1 && pr.totalStars == 0 && pr.coins == Progress.START_COINS &&
            pr.gems == Progress.START_GEMS && pr.relics.none { it } && pr.missionClaimed.none { it } && pr.sensitivity == 2)
        tapId(Menus.CLOSE)
        val b = App(SimPlatform(assets).also { it.saveFile = saveFile }).progress
        check("... and stays reset after reopening", b.unlocked == 1 && b.totalStars == 0 && b.coins == Progress.START_COINS)
        check("Android back on the Home screen leaves the app", !app.back())
        shot("21-home-after-reset")
    }

    private fun report() {
        println()
        println("================ APP FLOW ================")
        var ok = true
        for ((name, pass) in checks) { println((if (pass) "  [PASS] " else "  [FAIL] ") + name); ok = ok && pass }
        println(if (ok) "FLOW: ALL ${checks.size} CHECKS PASSED" else "FLOW: ${checks.count { !it.second }} OF ${checks.size} CHECKS FAILED")
        File(out, "flow-report.txt").writeText(log.joinToString("\n") + "\n\n" + checks.joinToString("\n") { (if (it.second) "[PASS] " else "[FAIL] ") + it.first } + "\n")
    }
}
