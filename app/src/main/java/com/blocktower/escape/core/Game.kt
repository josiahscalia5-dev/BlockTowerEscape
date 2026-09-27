package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** Game states. INTRO = camera fly-in + 3-2-1-GO countdown. */
object GS { const val INTRO = 0; const val PLAY = 1; const val COMPLETE = 4; const val RESULTS = 5; const val FAILED = 6 }

object Sfx {
    const val COIN = 0; const val TARGET = 1; const val WRONG = 2; const val JUMP = 3; const val LAND = 4; const val BOUNCE = 5
    const val HIT = 6; const val FALL = 7; const val RESCUE = 8; const val CHECKPOINT = 9; const val MAGNET = 10; const val SHIELD = 11
    const val SPEED = 12; const val BLOCK = 13; const val MYSTERY = 14; const val WARNING = 15; const val STOMP = 16; const val ROAR = 17
    const val EXPLODE = 19; const val CRUMBLE = 20; const val PORTAL = 21; const val WIN = 22; const val LOSE = 23
    const val CLICK = 24; const val TICK = 25; const val GEM = 28; const val TOOLGET = 29
    const val COUNT = 30; const val GO = 31; const val CRACK = 32; const val STAR = 33; const val SCORE = 34; const val WHOOSH = 35
    const val GRAB = 36; const val SPIKE = 37; const val SKID = 38
    const val TOTAL = 39
}

object TK { const val MAGNET = 0; const val SHIELD = 1; const val SPEED = 2; const val BLOCK = 3 }

class Tool(val kind: Int, var count: Int, val duration: Float, val cooldownDur: Float) {
    var active = 0f
    var cooldown = 0f
    var anim = 0f          // activation animation 1 -> 0
    var gain = 0f          // "+1" pickup animation
    var readyFlash = 0f    // cooldown finished
    val isActive get() = active > 0f
    val ready get() = count > 0 && active <= 0f && cooldown <= 0f
}

object Key { const val LEFT = 1; const val RIGHT = 2; const val UP = 3; const val DOWN = 4; const val JUMP = 5
    const val T1 = 6; const val T2 = 7; const val T3 = 8; const val T4 = 9; const val PAUSE = 10; const val ENTER = 11 }

class Input {
    var joyX = 0f; var joyY = 0f
    var kL = false; var kR = false; var kU = false; var kD = false
    var jumpHeld = false
    var jumpPressed = false
    val toolTap = BooleanArray(4)
    fun axisX() = clamp(joyX + (if (kR) 1f else 0f) - (if (kL) 1f else 0f), -1f, 1f)
    fun axisY() = clamp(joyY + (if (kU) 1f else 0f) - (if (kD) 1f else 0f), -1f, 1f)
}

/** Auto step-up height: half-block stairs can be walked, full blocks need a jump. */
private const val STEP = 0.56f

class Game(val platform: Platform) {
    val art = Art(platform)
    var world = Level.build()
    val player = Player()
    val cam = Camera()
    val fx = Fx()
    val input = Input()
    val tools = arrayOf(Tool(TK.MAGNET, 3, 8f, 3f), Tool(TK.SHIELD, 2, 10f, 3f), Tool(TK.SPEED, 3, 6f, 3f), Tool(TK.BLOCK, 3, 0f, 1.5f))
    val ev = Events(this)
    val rig = PlayerRig(this)
    val view = WorldRenderer(this)
    val hud = Hud(this)

    var state = GS.INTRO
    var stateT = 0f
    /** Seconds since GO (drives the GO! splash and the mission card). */
    var playT = 99f
    var paused = false
    var t = 0f                 // game clock (animation)
    var time = Tune.START_TIME // countdown
    var hearts = 2
    val maxHearts = 3
    var coins = Tune.START_COINS
    var gems = Tune.START_GEMS
    var target = 0
    var score = 0
    var shownCoins = coins
    var shownGems = gems
    var shownTarget = 0
    var coinsCollected = 0
    var mysteryOpened = 0
    var falls = 0
    var checkpoint = 0
    var failReason = ""
    var shake = 0f            // trauma: decays; strong events set it
    var rumble = 0f           // continuous shake, re-set every frame by whatever is rumbling
    var flashWhite = 0f
    var flashRed = 0f
    var hintT = 0f
    var hint = ""
    var hintKind = 0
    private var pendingHint = ""
    private var pendingHintKind = 0
    var results: Results? = null
    private var tickT = 0f
    var slowMo = 1f
    private var slowTarget = 1f
    private var slowHold = 0f
    private var combo = 0
    private var lastTargetT = -9f
    var introCountFrom = 0f    // stateT where the INTRO countdown starts (0 = full 3-2-1)
    /** The mission card is shown at the level start only (not when continuing from a checkpoint). */
    val showMission get() = introSwoop
    private var introSwoop = true

    // camera tuning: base framing matches the Screen 4 artwork
    var camDist = 4.0f
    var camHeight = 3.17f
    var camPitch = 0.28f
    var baseDist = 4.4f
    var baseHeight = 3.6f
    var basePitch = 0.30f
    var baseFocal = 1.0f
    var camX = 0f; var camY = 0f; var camZ = 0f
    var camYaw = 0f
    private var camFov = 1f
    private var camRoll = 0f
    private var camLead = 0f
    private var kickY = 0f; private var kickV = 0f
    private var camInit = false

    val onArrive: (Flyer) -> Unit = { f -> arrive(f) }
    private val cand = ArrayList<Block>(64)
    private var magnetPullT = 0f

    init { restartLevel(first = true) }

    // ------------------------------------------------------------------ lifecycle
    fun restartLevel(first: Boolean = false) {
        if (!first) world = Level.build()
        player.reset(world.spawnX, world.spawnY, world.spawnZ)
        state = GS.INTRO; stateT = 0f; playT = 99f; paused = false; time = Tune.START_TIME
        introCountFrom = 0f; introSwoop = true
        hearts = 2; coins = Tune.START_COINS; gems = Tune.START_GEMS; target = 0; score = 0
        shownCoins = coins; shownGems = gems; shownTarget = 0
        coinsCollected = 0; mysteryOpened = 0; falls = 0; checkpoint = 0
        tools[0].count = 3; tools[1].count = 2; tools[2].count = 3; tools[3].count = 3
        for (tl in tools) { tl.active = 0f; tl.cooldown = 0f; tl.anim = 0f; tl.gain = 0f; tl.readyFlash = 0f }
        fx.clear(); ev.reset()
        results = null; failReason = ""
        shake = 0f; rumble = 0f; flashWhite = 0f; flashRed = 0f; hint = ""; hintT = 0f; pendingHint = ""
        camInit = false; camYaw = 0f; slowMo = 1f; slowTarget = 1f; slowHold = 0f
        combo = 0; lastTargetT = -9f
        kickY = 0f; kickV = 0f; camLead = 0f; camRoll = 0f; camFov = 1f
        markSpawnContacts()
    }

    /** The block the player stands on is already "touched" so it is not collected instantly. */
    private fun markSpawnContacts() {
        gather(player.z - 1f, player.z + 1f)
        for (b in cand) if (touching(b, 0.06f)) { b.contact = true; b.wasContact = true }
    }

    private fun go() {
        state = GS.PLAY; stateT = 0f; playT = 0f
        platform.sound(Sfx.GO)
        input.jumpPressed = false
    }

    /** GAME OVER -> continue from the last activated checkpoint with full hearts. */
    fun continueFromCheckpoint() {
        val cp = world.checkpoints[checkpoint]
        restoreFrom(cp.z)
        ev.resetAfter(cp.z, cp.y)
        player.reset(cp.x, cp.y, cp.z)
        hearts = maxHearts; hud.bumpHearts()
        time = max(time, 45f)
        for (tl in tools) { tl.active = 0f; tl.cooldown = 0f }
        fx.clear(); results = null; failReason = ""
        shake = 0f; flashRed = 0f; flashWhite = 0f; slowMo = 1f; slowTarget = 1f; slowHold = 0f
        state = GS.INTRO; stateT = 1.39f; introCountFrom = 1.39f; introSwoop = false; paused = false
        camInit = false; camYaw = 0f
        markSpawnContacts()
    }

    // ------------------------------------------------------------------ input from host
    fun onKey(code: Int, down: Boolean) {
        when (code) {
            Key.LEFT -> input.kL = down
            Key.RIGHT -> input.kR = down
            Key.UP -> input.kU = down
            Key.DOWN -> input.kD = down
            Key.JUMP -> { if (down && !input.jumpHeld) input.jumpPressed = true; input.jumpHeld = down }
            Key.T1 -> if (down) input.toolTap[0] = true
            Key.T2 -> if (down) input.toolTap[1] = true
            Key.T3 -> if (down) input.toolTap[2] = true
            Key.T4 -> if (down) input.toolTap[3] = true
            Key.PAUSE -> if (down) togglePause()
            Key.ENTER -> if (down) hud.pressDefault()
        }
    }
    fun touchDown(id: Int, x: Float, y: Float) = hud.touchDown(id, x, y)
    fun touchMove(id: Int, x: Float, y: Float) = hud.touchMove(id, x, y)
    fun touchUp(id: Int, x: Float, y: Float) = hud.touchUp(id, x, y)

    fun togglePause() {
        if (state == GS.RESULTS || state == GS.FAILED || state == GS.COMPLETE) return
        paused = !paused
        platform.sound(Sfx.CLICK)
    }

    // ------------------------------------------------------------------ main update
    fun update(dtIn: Float) {
        val dt = min(dtIn, 1f / 30f)
        hud.update(dt)
        if (paused) return
        t += dt
        stateT += dt
        playT += dt
        if (slowHold > 0f) slowHold -= dt else slowTarget = 1f
        slowMo = approach(slowMo, slowTarget, dt * 2.5f)
        val sdt = dt * slowMo
        rumble = 0f

        if (state == GS.INTRO) updateIntro(dt)

        if (state == GS.PLAY && !ev.revealing && player.state == PS.NORMAL) {
            time -= sdt
            if (time <= 10f) {
                tickT -= sdt
                if (tickT <= 0f) { tickT = 1f; platform.sound(Sfx.TICK, 0.6f) }
            }
            if (time <= 0f) timeUp()
        }

        updateTools(sdt)
        updateBlocks(sdt)
        ev.update(sdt)
        updatePlayer(sdt)
        updateCoins(sdt)
        checkTriggers()
        if (state == GS.COMPLETE) updateComplete(dt, sdt)
        if (state == GS.RESULTS) updateResults(dt)
        updateCamera(dt)
        fx.update(sdt, onArrive)
        updateHudCounters(dt)
        if (pendingHint.isNotEmpty() && playT > 4.2f && fx.banners.isEmpty()) {
            hint = pendingHint; hintKind = pendingHintKind; hintT = 3.8f; pendingHint = ""
        }

        shake = max(0f, shake - dt * 1.8f)
        flashWhite = max(0f, flashWhite - dt * 2.8f)
        flashRed = max(0f, flashRed - dt * 2f)
        if (hintT > 0f) hintT -= dt
        input.jumpPressed = false
        for (i in 0..3) input.toolTap[i] = false
    }

    /** The timer never ends the level by itself: running out costs a heart and buys 20 more seconds. */
    private fun timeUp() {
        time = 0f
        hearts--
        hud.bumpHearts()
        flashRed = 0.8f; shake = max(shake, 0.4f)
        platform.sound(Sfx.HIT); platform.haptic(true)
        fx.popupScreen("-1", hud.heartX(max(0, hearts)), hud.heartY() + 50f * hud.s, 0xFFFF6070.toInt(), 44f)
        if (hearts <= 0) { fail("OUT OF TIME AND HEARTS!"); return }
        time = 20f; tickT = 0f
        fx.banner("TIME'S UP!", "-1 HEART  •  +20 SECONDS", 0xFFFF8A5A.toInt(), 1.8f, true)
    }

    private fun updateIntro(dt: Float) {
        val c = stateT
        val prev = c - dt
        for (k in 0..2) {
            val at = 0.6f + k * 0.8f
            if (at >= introCountFrom && prev < at && c >= at) platform.sound(Sfx.COUNT, 0.9f, 1f)
        }
        if (c >= 3.0f) go()
    }

    /** Countdown label for the HUD (empty when none). */
    fun countdownText(): String {
        if (state == GS.INTRO) {
            val c = stateT
            return when {
                c < 0.6f -> ""
                c < 1.4f -> "3"
                c < 2.2f -> "2"
                else -> "1"
            }
        }
        return if (state == GS.PLAY && playT < 0.8f) "GO!" else ""
    }
    fun countdownPhase(): Float = if (state == GS.INTRO) { val c = stateT; if (c < 0.6f) 0f else fract((c - 0.6f) / 0.8f) } else playT / 0.8f

    private fun updateHudCounters(dt: Float) {
        if (shownCoins < coins) { val step = max(1, ((coins - shownCoins) * min(1f, dt * 6f)).toInt()); shownCoins = min(coins, shownCoins + step) }
        if (shownCoins > coins) shownCoins = coins
        if (shownGems < gems) shownGems = min(gems, shownGems + max(1, ((gems - shownGems) * min(1f, dt * 6f)).toInt()))
        if (shownGems > gems) shownGems = gems
        shownTarget = target
    }

    // ------------------------------------------------------------------ score
    fun addScore(n: Int, x: Float, y: Float, z: Float, label: String = "") {
        score += n
        if (label.isEmpty()) fx.popupWorld("+$n", x, y, z, 0xFFFFE680.toInt(), 38f, 0.9f)
        else fx.popupWorld("+$n  $label", x, y, z, 0xFFFFE680.toInt(), 38f, 1.2f)
    }

    // ------------------------------------------------------------------ tools
    private fun updateTools(dt: Float) {
        for (i in 0..3) if (input.toolTap[i]) activateTool(i)
        for ((k, tl) in tools.withIndex()) {
            tl.anim = max(0f, tl.anim - dt * 1.6f)
            tl.gain = max(0f, tl.gain - dt * 1.2f)
            tl.readyFlash = max(0f, tl.readyFlash - dt * 2f)
            if (tl.active > 0f) {
                val before = tl.active
                tl.active -= dt
                if (before > 1.5f && tl.active <= 1.5f) platform.sound(Sfx.TICK, 0.5f, 1.4f)
                if (tl.active <= 0f) {
                    tl.active = 0f; tl.cooldown = tl.cooldownDur
                    if (k != TK.SHIELD || before > 0f) fx.popupWorld(toolName(k) + " OFF", player.x, player.y + 2.2f, player.z, 0xFFCFE0FF.toInt(), 30f, 0.8f)
                }
            } else if (tl.cooldown > 0f) {
                tl.cooldown = max(0f, tl.cooldown - dt)
                if (tl.cooldown <= 0f && tl.count > 0) tl.readyFlash = 1f
            }
        }
        // tool trails
        val p = player
        if (speedOn && p.state == PS.NORMAL && fx.rng.f() < dt * 30f) {
            val q = fx.spawn()
            q.x = p.x + fx.rng.f(-0.35f, 0.35f); q.y = p.y + fx.rng.f(0.2f, 1.5f); q.z = p.z + fx.rng.f(-0.3f, 0.3f)
            q.vx = fx.rng.f(-2f, 2f); q.vy = fx.rng.f(-1f, 2f); q.vz = -p.vz * 0.3f
            q.life = 0.25f; q.maxLife = 0.25f; q.size = 0.07f; q.color = 0xFFFFF08A.toInt(); q.kind = PK.SPARK
        }
        if (magnetOn && p.state == PS.NORMAL && fx.rng.f() < dt * 22f) {
            // field particles streaming inward
            val a = fx.rng.f(0f, TAU); val r = fx.rng.f(2.5f, 4.5f)
            val q = fx.spawn()
            q.x = p.x + cos(a) * r; q.y = p.y + fx.rng.f(0.2f, 2f); q.z = p.z + sin(a) * r
            q.vx = -cos(a) * r * 2.4f; q.vy = 0f; q.vz = -sin(a) * r * 2.4f
            q.life = 0.4f; q.maxLife = 0.4f; q.size = 0.06f; q.color = if (fx.rng.f() < 0.5f) 0xFFFF6A6A.toInt() else 0xFF8FD0FF.toInt(); q.kind = PK.STREAK
        }
    }

    fun toolName(k: Int) = when (k) { TK.MAGNET -> "MAGNET"; TK.SHIELD -> "SHIELD"; TK.SPEED -> "SPEED"; else -> "BLOCK" }

    fun activateTool(k: Int) {
        if (state != GS.PLAY) return
        if (player.state != PS.NORMAL || ev.revealing) return
        val tl = tools[k]
        if (tl.count <= 0) { hud.denied(k); platform.sound(Sfx.WRONG, 0.5f); return }
        if (!tl.ready) { hud.denied(k); platform.sound(Sfx.WRONG, 0.3f, 1.3f); return }
        val p = player
        if (k == TK.BLOCK) {
            if (!placeToolBlocks()) {
                hud.denied(k); platform.sound(Sfx.WRONG, 0.5f)
                fx.popupWorld("NO GAP AHEAD", p.x, p.y + 2.3f, p.z, 0xFFFFD0D0.toInt(), 34f)
                return
            }
            tl.count--; tl.cooldown = tl.cooldownDur; tl.anim = 1f
            p.castT = 0f
            hud.toolFx(k)
            platform.sound(Sfx.BLOCK); platform.haptic(false)
            return
        }
        tl.count--
        tl.active = tl.duration
        tl.anim = 1f
        p.castT = 0f
        hud.toolFx(k)
        when (k) {
            TK.MAGNET -> { platform.sound(Sfx.MAGNET); fx.popupWorld("MAGNET!", p.x, p.y + 2.4f, p.z, 0xFFFF6B6B.toInt(), 44f)
                fx.burst(p.x, p.y + 0.8f, p.z, 26, PK.SPARK, 0xFFFF5050.toInt(), 5f, 0.16f, 0.6f)
                if (cam.project(p.x, p.y + 0.8f, p.z)) fx.ring(cam.sx, cam.sy, 0xFFFF6A6A.toInt(), 20f * hud.s, 260f * hud.s, 0.5f, 10f * hud.s) }
            TK.SHIELD -> { platform.sound(Sfx.SHIELD); fx.popupWorld("SHIELD!", p.x, p.y + 2.4f, p.z, 0xFF7FDBFF.toInt(), 44f)
                fx.burst(p.x, p.y + 0.8f, p.z, 26, PK.SPARK, 0xFF60D0FF.toInt(), 5f, 0.16f, 0.6f) }
            TK.SPEED -> { platform.sound(Sfx.SPEED); fx.popupWorld("SPEED!", p.x, p.y + 2.4f, p.z, 0xFFFFE14A.toInt(), 44f)
                fx.burst(p.x, p.y + 0.8f, p.z, 30, PK.SPARK, 0xFFFFE040.toInt(), 6f, 0.14f, 0.5f)
                flashWhite = max(flashWhite, 0.18f) }
        }
        platform.haptic(false)
    }

    val magnetOn get() = tools[TK.MAGNET].isActive
    val shieldOn get() = tools[TK.SHIELD].isActive
    val speedOn get() = tools[TK.SPEED].isActive

    /**
     * Builds a temporary safe platform: under the player when falling over a gap, otherwise
     * across the first gap ahead (up to three blocks, so it bridges the gap).
     */
    private fun placeToolBlocks(): Boolean {
        val p = player
        val lx = p.x.roundToInt().toFloat()
        val bottom = p.lastGroundY - 1f
        var placed = 0
        if (!p.grounded && p.vy < 2f) {
            val z = floor(p.z).toFloat()
            if (!solidAt(lx, bottom, z)) { makeToolBlock(lx, bottom, z, 0); placed++ }
        }
        val dirZ = if (input.axisY() < -0.5f) -1 else 1
        var started = placed > 0
        for (k in 1..5) {
            val z = floor(p.z).toFloat() + k * dirZ
            val solid = solidAt(lx, bottom, z)
            if (solid) { if (started) break else continue }
            if (!started && solidAt(lx, bottom + 1f, z)) return placed > 0  // a wall ahead, not a gap
            makeToolBlock(lx, bottom, z, placed)
            placed++; started = true
            if (placed >= 3) break
        }
        if (placed > 0) fx.popupWorld("SAFE PLATFORM!", lx, bottom + 2.9f, floor(p.z) + dirZ * 1.5f, 0xFFB8F2FF.toInt(), 36f)
        return placed > 0
    }

    private fun solidAt(x: Float, y: Float, z: Float): Boolean {
        val r = world.row(floor(z + 0.001f).toInt()) ?: return false
        for (b in r) {
            if (!b.collides() && !(b.type == BT.APPEAR && b.rise > 0f && b.visible) && b.type != BT.TOOLBLOCK) continue
            if (x > b.x0 - 0.01f && x < b.x1 + 0.01f && y + 0.5f > b.y && y + 0.5f < b.y1) return true
        }
        return false
    }

    private fun makeToolBlock(x: Float, y: Float, z: Float, order: Int) {
        val b = Block(x, y, z, BC.ENERGY, BT.TOOLBLOCK)
        b.life = 8f; b.rise = 0.7f; b.timer = order * 0.09f; b.flash = 1f; b.remember()
        world.add(b)
        fx.burst(x, y + 0.9f, z + 0.5f, 16, PK.STAR, 0xFFB8F2FF.toInt(), 3.5f, 0.13f, 0.6f)
    }

    // ------------------------------------------------------------------ blocks
    private val shiftCycle = intArrayOf(BC.RED, BC.YELLOW, BC.GREEN, BC.BLUE, BC.PURPLE)
    fun shiftColor(b: Block): Int = shiftCycle[(((t + b.phase) / 1.15f).toInt()) % shiftCycle.size]

    private fun updateBlocks(dt: Float) {
        val r0 = floor(min(player.z, cam.ez) - 12f).toInt()
        val r1 = floor(max(player.z, cam.ez) + 70f).toInt()
        var removeList: ArrayList<Block>? = null
        for (r in r0..r1) {
            val row = world.row(r) ?: continue
            for (i in row.indices) {
                val b = row[i]
                b.flash = max(0f, b.flash - dt * 2.5f)
                b.bump = max(0f, b.bump - dt * 4f)
                b.squash = max(0f, b.squash - dt * 3f)
                b.pop = max(0f, b.pop - dt * 4f)
                if (b.shake > 0f) b.shake = max(0f, b.shake - dt)
                if (b.crackT > 0f) { b.crackT += dt / 0.2f; if (b.crackT >= 1f) { b.crackT = 0f; shatterShell(b) } }
                if (b.destroyed) {
                    if (b.visible) { b.vy -= Tune.GRAVITY * 0.6f * dt; b.y += b.vy * dt; if (b.y < b.origY - 18f) b.visible = false }
                    continue
                }
                when (b.type) {
                    BT.MOVING -> {
                        b.lastX = b.x
                        b.x = b.baseX + b.amp * sin(b.phase + t * b.speed)
                        b.dxFrame = b.x - b.lastX
                    }
                    BT.DISAPPEAR -> when (b.state) {
                        1 -> { b.timer += dt; b.alpha = if (((b.timer * 12f).toInt() and 1) == 0) 1f else 0.45f; b.shake = 0.05f
                            if (b.timer > 0.75f) { b.state = 2; b.timer = 0f; b.visible = false; fx.burst(b.x, b.y + 0.5f, b.z + 0.5f, 12, PK.STAR, 0xFFE0B0FF.toInt(), 2.5f, 0.12f, 0.5f)
                                platform.sound(Sfx.WHOOSH, 0.35f, 1.6f) } }
                        2 -> { b.timer += dt; if (b.timer > 3.2f && !playerInside(b)) { b.state = 3; b.timer = 0f; b.visible = true; b.alpha = 0f } }
                        3 -> { b.alpha = min(1f, b.alpha + dt * 2.5f); if (b.alpha >= 1f) b.state = 0 }
                    }
                    BT.FALLING -> when (b.state) {
                        1 -> { b.timer += dt; b.shake = 0.1f
                            if (fx.rng.f() < dt * 14f) crumbleBit(b)
                            if (b.timer > 0.45f) { b.state = 2; b.timer = 0f; b.vy = 0f; fx.dust(b.x, b.y, b.z + 0.5f, 5); for (k in 0 until 4) crumbleBit(b)
                            shake = max(shake, 0.12f) } }
                        2 -> { b.vy -= Tune.GRAVITY * 0.7f * dt; b.y += b.vy * dt; b.alpha = clamp01(1f - (b.origY - b.y) / 10f)
                            if (b.y < b.origY - 10f) { b.state = 3; b.visible = false; b.timer = 0f } }
                        3 -> { b.timer += dt; if (b.timer > 4.5f) { b.y = b.origY; b.vy = 0f; b.visible = true; b.alpha = 0f; b.state = 4 } }
                        4 -> { if (playerInside(b)) b.alpha = 0f else b.alpha = min(1f, b.alpha + dt * 2.5f); if (b.alpha >= 1f) b.state = 0 }
                    }
                    BT.CRACKED -> when (b.state) {
                        2 -> { b.timer += dt; if (b.timer > 4.5f && !playerInside(b)) { b.state = 3; b.visible = true; b.alpha = 0f; b.damage = 0f } }
                        3 -> { b.alpha = min(1f, b.alpha + dt * 2.5f); if (b.alpha >= 1f) b.state = 0 }
                    }
                    BT.TRAP -> if (b.speed == 0f) b.spike = 1f else {
                        val c = (t + b.phase) % 2.6f
                        val prev = b.spike
                        b.spike = when {
                            c < 1.4f -> 0f
                            c < 1.75f -> 0.25f * ((c - 1.4f) / 0.35f)
                            c < 1.85f -> 0.25f + 0.75f * ((c - 1.75f) / 0.1f)
                            c < 2.4f -> 1f
                            else -> 1f - (c - 2.4f) / 0.2f
                        }
                        // rattle before the spikes pop (the telegraph)
                        b.shake = if (c in 1.4f..1.75f) 0.04f else 0f
                        if (prev < 0.3f && b.spike >= 0.3f) {
                            val d = len3(player.x - b.x, player.y - b.y1, player.z - b.z - 0.5f)
                            if (d < 6f) platform.sound(Sfx.SPIKE, clamp01(1.1f - d / 6f) * 0.6f, 1f + fx.rng.f(-0.1f, 0.1f))
                        }
                    }
                    BT.APPEAR -> {
                        if (b.state == 0 && b.shortcutId == 0) {
                            val reveal = if (b.amp > 0f) b.amp else 4.6f
                            if (abs(b.z + 0.5f - player.z) < 5.5f && abs(b.x - player.x) < reveal && b.z + 0.5f > player.z - 1f) {
                                b.state = 1; b.visible = true; b.rise = 1f
                                platform.sound(Sfx.BLOCK, 0.35f, 1.4f)
                            }
                        }
                        if (b.state == 1) {
                            if (b.timer > 0f) b.timer -= dt
                            else {
                                b.rise = max(0f, b.rise - dt * 3f)
                                if (b.rise <= 0f) { b.state = 2; b.flash = 0.8f; b.pop = 1f; fx.burst(b.x, b.y + 1f, b.z + 0.5f, 6, PK.STAR, Col.WHITE, 2f, 0.1f, 0.4f) }
                            }
                        }
                    }
                    BT.TOOLBLOCK -> {
                        if (b.timer > 0f) b.timer -= dt
                        else if (b.rise > 0f) { b.rise = max(0f, b.rise - dt * 3.5f); if (b.rise <= 0f) { b.pop = 1f; platform.sound(Sfx.BLOCK, 0.4f, 1.3f) } }
                        b.life -= dt
                        b.alpha = if (b.life < 2f) (if (((b.life * 8f).toInt() and 1) == 0) 1f else 0.5f) else 0.92f
                        if (b.life <= 0f) {
                            fx.burst(b.x, b.y + 0.5f, b.z + 0.5f, 14, PK.STAR, 0xFFB8F2FF.toInt(), 3f, 0.12f, 0.5f)
                            if (removeList == null) removeList = ArrayList()
                            removeList.add(b)
                        }
                    }
                    else -> {}
                }
            }
        }
        removeList?.forEach { world.remove(it) }
    }

    /** A glowing chunk breaking off a lava-cracked block. */
    private fun crumbleBit(b: Block) {
        val q = fx.spawn()
        q.x = b.x + fx.rng.f(-0.45f, 0.45f); q.y = b.y + fx.rng.f(0f, 0.6f); q.z = b.z + fx.rng.f(0.1f, 0.9f)
        q.vx = fx.rng.f(-1f, 1f); q.vy = fx.rng.f(-0.5f, 1.5f); q.vz = fx.rng.f(-1f, 1f)
        q.life = fx.rng.f(0.6f, 1f); q.maxLife = q.life; q.size = fx.rng.f(0.1f, 0.18f)
        q.color = if (fx.rng.f() < 0.6f) 0xFFE0301A.toInt() else 0xFFFF8A20.toInt(); q.kind = PK.SHARD; q.gravity = 18f
        q.rot = fx.rng.f(0f, TAU); q.vrot = fx.rng.f(-9f, 9f)
    }

    private fun playerInside(b: Block): Boolean {
        val p = player
        return p.x + Tune.RADIUS > b.x0 && p.x - Tune.RADIUS < b.x1 && p.y + Tune.HEIGHT > b.y && p.y < b.y1 &&
            p.z + Tune.RADIUS > b.z && p.z - Tune.RADIUS < b.z1
    }

    // ------------------------------------------------------------------ player physics
    private fun gather(z0: Float, z1: Float) {
        cand.clear()
        for (r in floor(z0).toInt() - 2..floor(z1).toInt() + 1) {
            val row = world.row(r) ?: continue
            for (b in row) if (b.collides() || b.type == BT.RESCUE) cand.add(b)
        }
    }

    private fun overlaps(b: Block, x: Float, y: Float, z: Float): Boolean {
        val R = Tune.RADIUS
        return x + R > b.x0 + 1e-4f && x - R < b.x1 - 1e-4f && y + Tune.HEIGHT > b.y + 1e-4f && y < b.y1 - 1e-4f &&
            z + R > b.z + 1e-4f && z - R < b.z1 - 1e-4f
    }

    private fun touching(b: Block, e: Float): Boolean {
        val p = player; val R = Tune.RADIUS
        return p.x + R + e > b.x0 && p.x - R - e < b.x1 && p.y + Tune.HEIGHT + e > b.y && p.y - e < b.y1 &&
            p.z + R + e > b.z && p.z - R - e < b.z1
    }

    private fun updatePlayer(dt: Float) {
        val p = player
        p.invuln = max(0f, p.invuln - dt)
        p.hurtFlash = max(0f, p.hurtFlash - dt * 3f)
        p.stateT += dt
        p.tickAnim(dt)
        if (p.boostT > 0f) p.boostT -= dt

        when (p.state) {
            PS.RESCUE_RIDE -> { updateRide(dt); return }
            PS.WIN, PS.CAUGHT -> return
            PS.DEAD -> { p.vy -= Tune.GRAVITY * dt; p.y += p.vy * dt; p.airW = 1f; return }
        }
        val controllable = state == GS.PLAY && p.state == PS.NORMAL && !ev.revealing
        var ax = 0f; var az = 0f
        if (controllable) {
            ax = input.axisX(); az = input.axisY()
            val m = len2(ax, az)
            if (m < 0.18f) { ax = 0f; az = 0f } else if (m > 1f) { ax /= m; az /= m }
        }
        val maxV = if (speedOn) Tune.RUN_FAST else Tune.RUN
        val accel = if (p.grounded) Tune.ACCEL else Tune.AIR_ACCEL
        val tvx = ax * maxV
        var tvz = az * maxV
        if (p.boostT > 0f && p.vz > tvz) tvz = p.vz   // a star-block launch keeps its speed in the air
        // skid: sharp change of sideways direction while running
        if (p.grounded && abs(p.vx) > 2.6f && tvx * p.vx < 0f && abs(tvx) > 2.6f && p.skidT > 0.3f) {
            p.skidT = 0f; p.skidDir = sign(p.vx)
            fx.dust(p.x + p.skidDir * 0.2f, p.y, p.z, 5, 0xAAE8DCC8.toInt())
            platform.sound(Sfx.SKID, 0.35f)
        }
        p.vx = approach(p.vx, tvx, accel * dt)
        p.vz = approach(p.vz, tvz, accel * dt)
        if (!controllable && p.grounded) { p.vx *= 0.8f; p.vz *= 0.8f }

        // jumping (coyote time + buffer, variable height)
        if (controllable) {
            if (input.jumpPressed) p.jumpBuffer = 0.14f
            if (p.jumpBuffer > 0f) p.jumpBuffer -= dt
            if (p.grounded) p.coyote = 0.11f else p.coyote -= dt
            if (p.jumpBuffer > 0f && p.coyote > 0f) {
                p.vy = if (speedOn) Tune.JUMP_V_FAST else Tune.JUMP_V
                p.grounded = false; p.coyote = 0f; p.jumpBuffer = 0f; p.jumping = true
                p.jumpT = 0f
                kickV += 1.2f
                platform.sound(Sfx.JUMP, 0.7f, if (speedOn) 1.2f else 1f)
                fx.dust(p.x, p.y, p.z, 4)
            }
            if (p.jumping && !input.jumpHeld && p.vy > 3.5f) p.vy = 3.5f
        }
        val gr = if (p.vy < 0f) Tune.GRAVITY * 1.08f else Tune.GRAVITY
        p.vy = max(p.vy - gr * dt, -24f)

        // carry on moving platforms
        var carry = 0f
        val gb = p.ground
        if (p.grounded && gb != null && gb.type == BT.MOVING) carry = gb.dxFrame

        // substeps to avoid tunnelling
        val steps = if (abs(p.vy) * dt > 0.3f || len2(p.vx, p.vz) * dt > 0.3f) 3 else 1
        val sdt = dt / steps
        val wasGrounded = p.grounded
        val preVy = p.vy
        var landedOn: Block? = null
        p.grounded = false
        gather(p.z - 1.5f, p.z + 1.5f)
        for (s in 0 until steps) {
            p.y += p.vy * sdt
            var best: Block? = null
            for (b in cand) if (overlaps(b, p.x, p.y, p.z)) {
                if (p.vy <= 0f) { if (best == null || b.y1 > best.y1) best = b }
                else { p.y = b.y - Tune.HEIGHT - 1e-3f; p.vy = min(0f, p.vy); headHit(b) }
            }
            if (best != null) { p.y = best.y1; p.vy = 0f; p.grounded = true; landedOn = best }
            p.x += p.vx * sdt + carry / steps
            for (b in cand) if (overlaps(b, p.x, p.y, p.z)) {
                if (b.y1 - p.y in 0f..STEP && p.vy <= 0.5f && !blockedAbove(b, p.x, p.z)) { p.y = b.y1; p.grounded = true; landedOn = b; continue }
                p.x = if (p.x < b.x) b.x0 - Tune.RADIUS - 1e-3f else b.x1 + Tune.RADIUS + 1e-3f
                sideHit(b)
            }
            p.z += p.vz * sdt
            for (b in cand) if (overlaps(b, p.x, p.y, p.z)) {
                if (b.y1 - p.y in 0f..STEP && p.vy <= 0.5f && !blockedAbove(b, p.x, p.z)) { p.y = b.y1; p.grounded = true; landedOn = b; continue }
                p.z = if (p.z < b.z + b.sz * 0.5f) b.z - Tune.RADIUS - 1e-3f else b.z1 + Tune.RADIUS + 1e-3f
                p.vz = 0f
                sideHit(b)
            }
        }
        // ground probe (stay grounded when resting exactly on a surface)
        if (!p.grounded && p.vy <= 0f) {
            for (b in cand) {
                if (abs(p.y - b.y1) < 0.02f && p.x + Tune.RADIUS > b.x0 && p.x - Tune.RADIUS < b.x1 && p.z + Tune.RADIUS > b.z && p.z - Tune.RADIUS < b.z1) {
                    p.grounded = true; landedOn = b; p.vy = 0f; break
                }
            }
        }
        p.ground = if (p.grounded) landedOn else null
        if (p.grounded) {
            p.jumping = false
            if (!wasGrounded) onLand(landedOn!!, preVy)
            p.lastGroundY = p.y
            p.airTime = 0f
            if (p.grounded) landedOn?.let { onStand(it, dt) }
            landedOn?.let { rememberSafeSpot(it) }
        } else p.airTime += dt

        // contacts (collect / open / trigger)
        for (b in cand) {
            b.wasContact = b.contact
            b.contact = touching(b, 0.05f)
            if (b.contact && !b.wasContact) onContact(b)
        }

        // animation state
        val sp = len2(p.vx, p.vz)
        if (p.grounded && sp > 0.6f) {
            val before = p.runPhase
            p.runPhase += dt * sp * 0.72f
            if (floor(before) != floor(p.runPhase) && sp > 2f) fx.dust(p.x, p.y, p.z - 0.2f, 1, 0x88FFFFFF.toInt())
        }
        p.runW = approach(p.runW, if (p.grounded) clamp01(sp / 4f) else p.runW, dt * 8f)
        p.airW = if (p.grounded) approach(p.airW, 0f, dt * 18f) else if (p.airTime > 0.05f) approach(p.airW, 1f, dt * 10f) else p.airW
        p.lean = lerp(p.lean, clamp(p.vx / Tune.RUN, -1f, 1f), min(1f, dt * 10f))

        // fell off the course
        if (p.state == PS.NORMAL && !p.grounded && p.y < p.lastGroundY - Tune.FALL_DEPTH) startRescue("fall")
        if (p.state == PS.RESCUE_FALL) {
            if (p.grounded && p.ground?.type == BT.RESCUE) {
                if (p.stateT > 0.1f && p.rideT == 0f) { p.rideT = 0.0001f; p.stateT = 0f; p.landT = 0f; p.landAmt = 0.8f }
                if (p.rideT > 0f && p.stateT > 0.4f) beginRide()
            }
            if (p.y < p.fallFromY - 30f) beginRide()
        }
    }

    /** Remembers where the player last stood on solid, stable ground (fall recovery returns there). */
    private fun rememberSafeSpot(b: Block) {
        val p = player
        if (p.state != PS.NORMAL) return
        val stable = when (b.type) {
            BT.NORMAL, BT.BRICK, BT.TARGET, BT.CHECKPOINT, BT.MYSTERY, BT.COLORSHIFT -> true
            BT.APPEAR -> b.state == 2
            else -> false
        }
        if (!stable || b.destroyed || b.sx != 1f) return
        if (abs(p.x - b.x) > 0.42f || p.z < b.z + 0.08f || p.z > b.z1 - 0.08f) return
        p.safeX = b.x; p.safeY = b.y1; p.safeZ = b.z + 0.5f; p.safeBlock = b
    }

    private fun blockedAbove(b: Block, x: Float, z: Float): Boolean {
        for (o in cand) if (o !== b && o.y < b.y1 + Tune.HEIGHT && o.y1 > b.y1 + 0.05f &&
            x + Tune.RADIUS > o.x0 && x - Tune.RADIUS < o.x1 && z + Tune.RADIUS > o.z && z - Tune.RADIUS < o.z1) return true
        return false
    }

    private fun headHit(b: Block) {
        if (b.type == BT.MYSTERY && !b.used) openMystery(b)
        else if (b.type == BT.TARGET) collectTarget(b, true)
        else if (b.type == BT.COLORSHIFT && b.state == 0) collectShift(b)
        else platform.sound(Sfx.LAND, 0.3f, 1.5f)
        b.bump = 1f
        kickV -= 0.6f
    }

    private fun sideHit(b: Block) {
        if (b.type == BT.MYSTERY && !b.used) openMystery(b)
    }

    private fun onLand(b: Block, vy: Float) {
        val p = player
        val hard = -vy
        p.landT = 0f; p.landAmt = clamp01((hard - 2f) / 13f)
        if (hard > 4f) {
            platform.sound(Sfx.LAND, clamp01(hard / 16f) * 0.8f + 0.2f)
            fx.dust(p.x, p.y, p.z, if (hard > 12f) 9 else 4)
            kickV -= hard * 0.09f
            if (hard > 14f) shake = max(shake, 0.18f)
        }
        when (b.type) {
            BT.SAVE -> {
                p.vy = 17.5f; p.grounded = false; p.jumping = false; b.squash = 1f; b.flash = 1f
                p.lastGroundY = b.y1 + 3f
                platform.sound(Sfx.BOUNCE, 1f, 0.8f)
                fx.popupWorld("SAVED!", p.x, p.y + 2.3f, p.z, 0xFFFFE680.toInt(), 50f)
                fx.burst(b.x, b.y1, b.z + 0.5f, 22, PK.STAR, 0xFFFFF0A0.toInt(), 4f, 0.16f, 0.7f)
                time = max(1f, time - 2f)
                fx.popupScreen("-2s", hud.timerX(), hud.timerY() + 60f * hud.s, 0xFFFFB0B0.toInt(), 36f)
                kickV += 2f
            }
            BT.CRACKED -> if (hard > 13f) breakCracked(b)
            else -> {}
        }
    }

    private fun onStand(b: Block, dt: Float) {
        val p = player
        when (b.type) {
            BT.BOUNCE -> if (p.grounded) {
                p.vy = 16.5f; p.grounded = false; p.jumping = false; b.squash = 1f; p.jumpT = 0f
                platform.sound(Sfx.BOUNCE); fx.burst(b.x, b.y1, b.z + 0.5f, 12, PK.STAR, 0xFFFF94EE.toInt(), 3f, 0.14f, 0.5f)
                kickV += 1.6f
            }
            BT.BOOST -> if (p.grounded) {
                p.vy = 11.5f; p.vz = 8.6f; p.boostT = 0.75f; p.grounded = false; p.jumping = false; b.flash = 1f; p.jumpT = 0f
                platform.sound(Sfx.SPEED, 0.9f, 1.3f)
                fx.burst(b.x, b.y1, b.z + 0.5f, 20, PK.STAR, 0xFF9AF0FF.toInt(), 4f, 0.16f, 0.7f)
                fx.popupWorld("BOOST!", p.x, p.y + 2.2f, p.z, 0xFF9AF0FF.toInt(), 44f)
                kickV += 1.4f
            }
            BT.DISAPPEAR -> if (b.state == 0) { b.state = 1; b.timer = 0f; platform.sound(Sfx.CRUMBLE, 0.3f, 1.6f) }
            BT.FALLING -> if (b.state == 0) { b.state = 1; b.timer = 0f; platform.sound(Sfx.CRUMBLE, 0.5f) }
            BT.CRACKED -> if (b.state == 0) {
                val before = b.damage
                b.damage += dt
                if (before < 0.35f && b.damage >= 0.35f) platform.sound(Sfx.CRACK, 0.35f, 0.8f)
                if (before < 0.75f && b.damage >= 0.75f) { platform.sound(Sfx.CRACK, 0.5f, 0.7f); b.shake = 0.2f }
                if (b.damage > 1.15f) breakCracked(b)
            }
            BT.TRAP -> if (b.spike > 0.55f) hurt("spikes", b)
            BT.CHECKPOINT -> activateCheckpoint(b.checkpointId)
            else -> {}
        }
    }

    private fun breakCracked(b: Block) {
        b.state = 2; b.timer = 0f; b.visible = false
        fx.shards(b, BC.base(b.color), 12); fx.dust(b.x, b.y + 0.5f, b.z + 0.5f, 6)
        platform.sound(Sfx.CRUMBLE)
        shake = max(shake, 0.22f)
    }

    private fun onContact(b: Block) {
        when (b.type) {
            BT.TARGET -> collectTarget(b, false)
            BT.COLORSHIFT -> if (b.state == 0) collectShift(b)
            BT.MYSTERY -> if (!b.used) openMystery(b)
            else -> {}
        }
    }

    // ------------------------------------------------------------------ collection
    /** Blue block: counter updates at once, the shell cracks (0.2 s) and then shatters. */
    fun collectTarget(b: Block, remote: Boolean) {
        if (b.type != BT.TARGET) return
        target++
        shownTarget = target
        hud.bumpTarget()
        b.type = BT.BRICK; b.crackT = 0.001f; b.flash = 0.6f
        combo = if (t - lastTargetT < 2.6f) min(combo + 1, 5) else 1
        lastTargetT = t
        val pts = 100 * combo
        score += pts
        fx.popupWorld("+$pts", b.x, b.y + 1.7f, b.z + 0.5f, 0xFF9FDBFF.toInt(), 54f)
        if (combo > 1) fx.popupWorld("COMBO x$combo", b.x, b.y + 2.35f, b.z + 0.5f, 0xFFFFE680.toInt(), 34f, 1.0f)
        if (cam.project(b.x, b.y + 1f, b.z + 0.5f)) fx.fly(FK.TARGET, cam.sx, cam.sy, hud.targetIconX(), hud.targetIconY(), 1, 0f, 0.6f)
        platform.sound(Sfx.CRACK, 0.9f, 1.1f)
        platform.haptic(false)
        player.collectT = 0f
        if (target == Tune.TARGET_NEED) {
            fx.banner("TARGET COMPLETE!", "THE ANCIENT GATE IS OPEN", 0xFF7FFFA0.toInt(), 2.2f)
            platform.sound(Sfx.WIN, 0.7f, 1.2f)
            hud.objectiveDone()
        }
    }

    private fun shatterShell(b: Block) {
        b.color = BC.BRICK; b.pop = 1f; b.flash = 1f
        fx.shards(b, BC.base(BC.BLUE), 18)
        fx.burst(b.x, b.y + 1f, b.z + 0.5f, 16, PK.STAR, 0xFFBFE4FF.toInt(), 4.5f, 0.14f, 0.6f, 0f, 1.5f)
        if (cam.project(b.x, b.y + 1f, b.z + 0.5f)) fx.ring(cam.sx, cam.sy, 0xFF8FD0FF.toInt(), 20f * hud.s, 150f * hud.s, 0.45f, 10f * hud.s)
        platform.sound(Sfx.TARGET, 1f, 1f + min(0.5f, target * 0.03f))
        shake = max(shake, 0.16f)
    }

    private fun collectShift(b: Block) {
        val c = shiftColor(b)
        b.state = 1
        b.type = BT.BRICK; b.color = BC.BRICK; b.flash = 1f
        if (c == BC.BLUE) {
            b.type = BT.TARGET; b.color = BC.BLUE
            collectTarget(b, false)
        } else {
            fx.shards(b, BC.base(c), 14)
            fx.popupWorld("WRONG COLOR", b.x, b.y + 1.7f, b.z + 0.5f, 0xFFFF7070.toInt(), 40f)
            fx.burst(b.x, b.y + 1f, b.z + 0.5f, 10, PK.SPARK, BC.base(c), 3f, 0.12f, 0.5f)
            platform.sound(Sfx.WRONG)
        }
    }

    private fun updateCoins(dt: Float) {
        val p = player
        val px = p.x; val py = p.y + 0.8f; val pz = p.z
        val magR = if (magnetOn) 5.5f else 0f
        for (c in world.coins) {
            if (c.collected) continue
            if (abs(c.z - pz) > 12f) continue
            c.phase += dt
            val dx = px - c.x; val dy = py - c.y; val dz = pz - c.z
            val d = len3(dx, dy, dz)
            if (magR > 0f && d < magR && p.state == PS.NORMAL) c.pulled = true
            if (c.pulled) {
                val k = min(1f, 15f * dt / max(d, 0.001f))
                c.x += dx * k; c.y += dy * k; c.z += dz * k
            }
            if (d < 0.8f && (p.state == PS.NORMAL || p.state == PS.RESCUE_FALL)) collectCoin(c)
        }
        for (bb in world.bubbles) {
            if (bb.taken) { bb.pop = max(0f, bb.pop - dt * 3f); continue }
            if (abs(bb.z - pz) > 12f) continue
            val dx = px - bb.x; val dy = py - bb.y; val dz = pz - bb.z
            val d = len3(dx, dy, dz)
            if (magR > 0f && d < magR && p.state == PS.NORMAL) bb.pulled = true
            if (bb.pulled) { val k = min(1f, 12f * dt / max(d, 0.001f)); bb.x += dx * k; bb.y += dy * k; bb.z += dz * k }
            if (d < 0.95f && p.state == PS.NORMAL) takeBubble(bb)
        }
        // the magnet also pulls in blue target blocks (their shells fly to the player)
        if (magnetOn && p.state == PS.NORMAL) {
            magnetPullT -= dt
            if (magnetPullT <= 0f) {
                gather(p.z - 3.5f, p.z + 3.5f)
                for (b in cand) {
                    if (b.type != BT.TARGET) continue
                    if (len3(b.x - px, b.y + 0.5f - p.y, b.z + 0.5f - pz) < 3.4f) {
                        collectTarget(b, true)
                        for (i in 0 until 10) {
                            val q = fx.spawn()
                            q.x = b.x + fx.rng.f(-0.3f, 0.3f); q.y = b.y + 0.8f; q.z = b.z + 0.5f
                            q.vx = (px - b.x) * 3.5f; q.vy = (py - b.y) * 3.5f + 1f; q.vz = (pz - b.z) * 3.5f
                            q.life = 0.35f; q.maxLife = 0.35f; q.size = 0.16f; q.color = BC.light(BC.BLUE); q.kind = PK.SHARD
                        }
                        magnetPullT = 0.25f
                        break
                    }
                }
            }
        }
    }

    fun collectCoin(c: Coin) {
        c.collected = true
        coinsCollected++
        coins += Tune.COIN_VALUE
        score += 10
        platform.sound(Sfx.COIN, 0.8f, 1f + (coinsCollected % 5) * 0.04f)
        if (cam.project(c.x, c.y, c.z)) {
            fx.fly(FK.COIN, cam.sx, cam.sy, hud.coinIconX(), hud.coinIconY(), 0, 0f, 0.55f)
            fx.burst(c.x, c.y, c.z, 6, PK.SPARK, 0xFFFFE070.toInt(), 2.5f, 0.1f, 0.35f)
        }
    }

    private fun takeBubble(bb: Bubble) {
        bb.taken = true; bb.pop = 1f
        val k = bb.kind
        tools[k].count++
        score += 50
        platform.sound(Sfx.TOOLGET); platform.sound(Sfx.SHIELD, 0.4f, 1.6f)
        fx.burst(bb.x, bb.y, bb.z, 18, PK.STAR, 0xFFD8F0FF.toInt(), 4f, 0.13f, 0.6f)
        fx.popupWorld("+1 ${toolName(k)}", bb.x, bb.y + 1.1f, bb.z, 0xFFB8F2FF.toInt(), 40f)
        if (cam.project(bb.x, bb.y, bb.z)) {
            fx.fly(FK.TOOL, cam.sx, cam.sy, hud.toolX(k), hud.toolY(k), k, 0f, 0.7f)
            fx.ring(cam.sx, cam.sy, 0xFFB8F2FF.toInt(), 20f * hud.s, 120f * hud.s, 0.4f, 8f * hud.s)
        }
        player.collectT = 0f
    }

    private fun arrive(f: Flyer) {
        when (f.kind) {
            FK.TARGET -> hud.bumpTarget()
            FK.COIN -> hud.bumpCoins()
            FK.GEM -> hud.bumpGems()
            FK.HEART -> hud.bumpHearts()
            FK.TOOL -> { tools[f.value].gain = 1f }
        }
        if (f.kind == FK.COIN && f.value > 0) coins += f.value
        if (f.kind == FK.GEM && f.value > 0) gems += f.value
    }

    // ------------------------------------------------------------------ mystery blocks
    private fun openMystery(b: Block) {
        if (b.used) return
        b.used = true; b.bump = 1f; b.flash = 1f
        mysteryOpened++
        platform.sound(Sfx.MYSTERY)
        val sx: Float; val sy: Float
        if (cam.project(b.x, b.y + 1f, b.z + 0.5f)) { sx = cam.sx; sy = cam.sy } else { sx = hud.cx(); sy = hud.cy() }
        fx.burst(b.x, b.y + 1f, b.z + 0.5f, 18, PK.STAR, 0xFFFFE680.toInt(), 4f, 0.15f, 0.7f, 0f, 2f)
        val p = player
        if (b.reward != Reward.TRAP) { score += 50; p.collectT = 0f }
        when (b.reward) {
            Reward.COINS -> {
                for (i in 0 until 6) fx.fly(FK.COIN, sx, sy, hud.coinIconX(), hud.coinIconY(), Tune.COIN_VALUE, i * 0.08f, 0.8f)
                fx.popupWorld("+60 COINS", b.x, b.y + 1.8f, b.z + 0.5f, 0xFFFFE14A.toInt(), 42f)
            }
            Reward.GEMS -> {
                for (i in 0 until 5) fx.fly(FK.GEM, sx, sy, hud.gemIconX(), hud.gemIconY(), 1, i * 0.09f, 0.8f)
                fx.popupWorld("+5 GEMS", b.x, b.y + 1.8f, b.z + 0.5f, 0xFFE59CFF.toInt(), 42f)
                platform.sound(Sfx.GEM)
            }
            Reward.TOOL_MAGNET, Reward.TOOL_SHIELD, Reward.TOOL_SPEED, Reward.TOOL_BLOCK -> {
                val k = b.reward - Reward.TOOL_MAGNET
                tools[k].count++
                fx.fly(FK.TOOL, sx, sy, hud.toolX(k), hud.toolY(k), k, 0.1f, 0.8f)
                fx.popupWorld("+1 ${toolName(k)}", b.x, b.y + 1.8f, b.z + 0.5f, 0xFFB8F2FF.toInt(), 42f)
                platform.sound(Sfx.TOOLGET)
            }
            Reward.HEART -> {
                if (hearts < maxHearts) {
                    hearts++
                    fx.fly(FK.HEART, sx, sy, hud.heartX(hearts - 1), hud.heartY(), 0, 0.05f, 0.8f)
                    fx.popupWorld("+1 HEART", b.x, b.y + 1.8f, b.z + 0.5f, 0xFFFF7A8A.toInt(), 42f)
                } else {
                    for (i in 0 until 10) fx.fly(FK.COIN, sx, sy, hud.coinIconX(), hud.coinIconY(), Tune.COIN_VALUE, i * 0.06f, 0.8f)
                    fx.popupWorld("+100 COINS", b.x, b.y + 1.8f, b.z + 0.5f, 0xFFFFE14A.toInt(), 42f)
                }
                platform.sound(Sfx.TOOLGET, 1f, 1.2f)
            }
            Reward.TRAP -> {
                fx.burst(b.x, b.y + 0.8f, b.z + 0.5f, 30, PK.FIRE, 0xFFFF8A20.toInt(), 6f, 0.25f, 0.6f)
                platform.sound(Sfx.EXPLODE, 0.8f)
                shake = max(shake, 0.5f)
                if (shieldOn) {
                    breakShield(); fx.popupWorld("BLOCKED!", p.x, p.y + 2.3f, p.z, 0xFF9FE8FF.toInt(), 44f)
                } else {
                    time = max(1f, time - 5f)
                    fx.popupWorld("TRAP!  -5s", b.x, b.y + 1.9f, b.z + 0.5f, 0xFFFF6060.toInt(), 46f)
                    fx.popupScreen("-5s", hud.timerX(), hud.timerY() + 60f * hud.s, 0xFFFF8080.toInt(), 40f)
                    p.vz = -6f; p.vy = 7f; p.grounded = false; p.hurtFlash = 1f; p.hurtT = 0f; p.hurtDir = 1f; flashRed = 0.5f
                }
            }
            Reward.SHORTCUT -> {
                var k = 0
                for (o in world.blocks) if (o.shortcutId == 1 && o.state == 0) {
                    o.state = 1; o.visible = true; o.rise = 1f; o.timer = 0.15f * k; k++
                }
                fx.popupWorld("SHORTCUT!", b.x, b.y + 1.9f, b.z + 0.5f, 0xFFFFE680.toInt(), 50f)
                fx.banner("SHORTCUT!", "A SECRET BRIDGE APPEARS", 0xFFFFE680.toInt(), 1.8f)
            }
        }
        b.type = BT.BRICK; b.color = BC.BRICK
    }

    // ------------------------------------------------------------------ damage, falling, recovery
    fun breakShield() {
        tools[TK.SHIELD].active = 0f; tools[TK.SHIELD].cooldown = tools[TK.SHIELD].cooldownDur
        val p = player
        fx.burst(p.x, p.y + 0.8f, p.z, 30, PK.SPARK, 0xFF9FE8FF.toInt(), 6f, 0.16f, 0.6f)
        for (i in 0 until 16) {
            val q = fx.spawn(); val a = fx.rng.f(0f, TAU)
            q.x = p.x + cos(a) * 0.9f; q.y = p.y + 0.9f + fx.rng.f(-0.6f, 0.6f); q.z = p.z + sin(a) * 0.9f
            q.vx = cos(a) * 4f; q.vy = fx.rng.f(1f, 4f); q.vz = sin(a) * 4f
            q.life = 0.6f; q.maxLife = 0.6f; q.size = 0.14f; q.color = 0xFFB8ECFF.toInt(); q.kind = PK.SHARD; q.gravity = 10f
            q.rot = fx.rng.f(0f, TAU); q.vrot = fx.rng.f(-10f, 10f)
        }
        if (cam.project(p.x, p.y + 0.9f, p.z)) fx.ring(cam.sx, cam.sy, 0xFFB8ECFF.toInt(), 40f * hud.s, 220f * hud.s, 0.45f, 12f * hud.s)
        platform.sound(Sfx.SHIELD, 1f, 0.7f)
        p.invuln = 1f
    }

    fun hurt(reason: String, src: Block?) {
        val p = player
        if (p.invuln > 0f || p.state != PS.NORMAL || state != GS.PLAY) return
        if (shieldOn) {
            breakShield()
            fx.popupWorld("BLOCKED!", p.x, p.y + 2.3f, p.z, 0xFF9FE8FF.toInt(), 44f)
            if (src != null) { p.vy = 7f; p.grounded = false }
            return
        }
        hearts--
        p.invuln = 1.6f; p.hurtFlash = 1f; p.hurtT = 0f; p.hurtDir = if (p.vx >= 0f) 1f else -1f
        flashRed = 0.8f; shake = max(shake, 0.5f)
        platform.sound(Sfx.HIT); platform.haptic(true)
        fx.popupScreen("-1", hud.heartX(max(0, hearts)), hud.heartY() + 50f * hud.s, 0xFFFF6070.toInt(), 44f)
        fx.popupWorld(if (reason == "spikes") "OUCH!" else "HIT!", p.x, p.y + 2.3f, p.z, 0xFFFF8A8A.toInt(), 42f, 0.8f)
        fx.burst(p.x, p.y + 1.8f, p.z, 8, PK.STAR, 0xFFFFF0A0.toInt(), 2f, 0.1f, 0.7f)
        hud.bumpHearts()
        if (src != null) { p.vy = 8f; p.vz = -3f; p.grounded = false }
        if (hearts <= 0) fail("OUT OF HEARTS!")
    }

    fun startRescue(cause: String) {
        val p = player
        if (p.state != PS.NORMAL || state != GS.PLAY) return
        falls++
        hearts--
        hud.bumpHearts()
        platform.sound(Sfx.FALL)
        flashRed = 0.6f
        fx.popupScreen("-1", hud.heartX(max(0, hearts)), hud.heartY() + 50f * hud.s, 0xFFFF6070.toInt(), 44f)
        if (hearts <= 0) {
            p.state = PS.DEAD; p.stateT = 0f
            fail(if (cause == "lava") "BURNED BY THE LAVA!" else "OUT OF HEARTS!")
            return
        }
        p.state = PS.RESCUE_FALL; p.stateT = 0f; p.rideT = 0f; p.fallFromY = p.y
        p.vy = max(p.vy, -9f)
        if (cause == "lava") { p.vy = 9f; p.hurtT = 0f; p.hurtFlash = 1f }
        // the emergency platform materialises below the falling player
        val rb = Block(p.x, p.y - (if (cause == "lava") 0.6f else 2.2f), p.z - 1.1f, BC.ENERGY, BT.RESCUE)
        rb.sx = 2.2f; rb.sy = 0.35f; rb.sz = 2.2f; rb.rise = 0f; rb.flash = 1f; rb.remember()
        world.add(rb)
        rescueBlock = rb
        fx.burst(p.x, rb.y1, p.z, 26, PK.STAR, 0xFFB8F2FF.toInt(), 5f, 0.16f, 0.8f)
        fx.popupWorld(if (cause == "lava") "TOO HOT!" else "SAFETY NET!", p.x, p.y + 1.6f, p.z, 0xFFB8F2FF.toInt(), 46f)
        platform.sound(Sfx.RESCUE)
    }
    var rescueBlock: Block? = null

    /** Where a recovery ride should drop the player: the last safe spot, if it still stands. */
    private fun recoverySpot(): FloatArray {
        val p = player
        val sb = p.safeBlock
        if (sb != null && sb.collides() && !isCrumbling(sb)) return floatArrayOf(p.safeX, p.safeY, p.safeZ)
        // the safe block is gone (collapse): first standing path block ahead of the collapse front
        val front = max(ev.finalCollapse.z, ev.chaseCollapse.z)
        var r = floor(max(p.safeZ, front + 1f)).toInt()
        while (r < world.rowMax) {
            val row = world.row(r)
            val lvl = world.levelAt(r)
            val b = row?.firstOrNull { it.collides() && abs(it.x - world.pathXAt(r)) < 0.1f && abs(it.y1 - lvl) < 0.3f && it.sx == 1f && !isCrumbling(it) &&
                (it.type == BT.NORMAL || it.type == BT.BRICK || it.type == BT.TARGET || it.type == BT.CHECKPOINT) }
            if (b != null) return floatArrayOf(b.x, b.y1, b.z + 0.5f)
            r++
        }
        val cp = world.checkpoints[checkpoint]
        return floatArrayOf(cp.x, cp.y, cp.z)
    }

    private fun isCrumbling(b: Block) = ev.finalCollapse.crumbling.contains(b) || ev.chaseCollapse.crumbling.contains(b)

    private fun beginRide() {
        val p = player
        val spot = recoverySpot()
        p.state = PS.RESCUE_RIDE; p.stateT = 0f
        p.rideX0 = p.x; p.rideY0 = p.y; p.rideZ0 = p.z
        p.rideX1 = spot[0]; p.rideY1 = spot[1]; p.rideZ1 = spot[2]
        val d = len3(p.rideX1 - p.rideX0, p.rideY1 - p.rideY0, p.rideZ1 - p.rideZ0)
        p.rideDur = clamp(d / 12f, 1.0f, 2.2f)
        p.rideT = 0f
        rescueBlock?.let { world.remove(it) }
        platform.sound(Sfx.RESCUE, 0.8f, 1.3f)
    }

    private fun updateRide(dt: Float) {
        val p = player
        p.rideT += dt / p.rideDur
        val u = smooth(p.rideT)
        p.x = lerp(p.rideX0, p.rideX1, u)
        p.z = lerp(p.rideZ0, p.rideZ1, u)
        p.y = lerp(p.rideY0, p.rideY1, u) + sin(u * Math.PI.toFloat()) * 3f
        if (((t * 30f).toInt() % 3) == 0) {
            val q = fx.spawn()
            q.x = p.x + fx.rng.f(-0.8f, 0.8f); q.y = p.y - 0.2f; q.z = p.z + fx.rng.f(-0.8f, 0.8f)
            q.vx = 0f; q.vy = -1f; q.vz = 0f; q.life = 0.6f; q.maxLife = 0.6f; q.size = 0.12f; q.color = 0xFFB8F2FF.toInt(); q.kind = PK.STAR
        }
        if (p.rideT >= 1f) {
            val x = p.rideX1; val y = p.rideY1; val z = p.rideZ1
            p.reset(x, y, z)
            p.invuln = 1.5f; p.landT = 0f; p.landAmt = 0.6f
            ev.onRecovered(x, y, z)
            fx.burst(p.x, p.y + 0.3f, p.z, 20, PK.STAR, 0xFFB8F2FF.toInt(), 4f, 0.14f, 0.6f)
            fx.popupWorld("BACK ON TRACK!", p.x, p.y + 2.3f, p.z, Col.WHITE, 42f)
            gather(p.z - 1f, p.z + 1f)
            for (b in cand) { b.contact = touching(b, 0.05f); b.wasContact = b.contact }
        }
    }

    /** Puts the course back the way it was after the checkpoint so the player can retry that part. */
    private fun restoreFrom(cpZ: Float) {
        // rows from a little behind the checkpoint on (the checkpoint's own platform may have crumbled)
        val from = floor(cpZ).toInt() - 4
        for (b in world.blocks) {
            if (b.row < from) continue
            if (b.eventTag != 0 && b.destroyed) { b.destroyed = false; b.visible = true; b.y = b.origY; b.vy = 0f; b.alpha = 1f; b.shake = 0f }
            when (b.type) {
                BT.DISAPPEAR, BT.FALLING, BT.CRACKED -> if (b.state != 0) { b.state = 0; b.visible = true; b.alpha = 1f; b.y = b.origY; b.vy = 0f; b.damage = 0f; b.timer = 0f }
                else -> {}
            }
        }
        ev.chaseCollapse.crumbling.clear(); ev.finalCollapse.crumbling.clear()
        world.blocks.filter { it.type == BT.TOOLBLOCK || it.type == BT.RESCUE }.forEach { world.remove(it) }
        for (tr in world.triggers) if (tr.z > cpZ) tr.fired = false
    }

    fun activateCheckpoint(id: Int) {
        if (id < 0) return
        val cp = world.checkpoints[id]
        if (cp.active) { if (checkpoint < id) checkpoint = id; return }
        cp.active = true; cp.activeTime = t
        checkpoint = id
        platform.sound(Sfx.CHECKPOINT); platform.haptic(false)
        fx.burst(cp.x, cp.y + 2.4f, cp.z, 40, PK.STAR, 0xFFFFE680.toInt(), 6f, 0.18f, 1.0f)
        fx.confetti(cp.x, cp.y + 2.6f, cp.z, 36)
        fx.popupWorld("CHECKPOINT!", cp.x, cp.y + 3.2f, cp.z, 0xFFFFE680.toInt(), 52f)
        if (cam.project(cp.x, cp.y + 1f, cp.z)) fx.ring(cam.sx, cam.sy, 0xFFFFE680.toInt(), 30f * hud.s, 260f * hud.s, 0.8f, 12f * hud.s)
        addScore(250, cp.x, cp.y + 2.5f, cp.z)
        player.celebrateT = 0f
        shake = max(shake, 0.15f)
    }

    // ------------------------------------------------------------------ triggers & the gate
    private fun checkTriggers() {
        if (state != GS.PLAY || player.state != PS.NORMAL) return
        val p = player
        for (tr in world.triggers) {
            if (tr.fired) continue
            if (p.z >= tr.z && p.x >= tr.xMin && p.x <= tr.xMax) { tr.fired = true; fire(tr) }
        }
        if (p.grounded) for (cp in world.checkpoints) {
            if (!cp.active && abs(p.z - cp.z) < 0.9f && abs(p.x - cp.x) < 1.9f && abs(p.y - cp.y) < 1.2f) activateCheckpoint(cp.id)
        }
        for (po in world.portals) {
            if (abs(p.x - po.x) < 1.75f && abs(p.z - po.z) < 0.6f && p.y > po.y - 0.5f && p.y < po.y + 2.8f) {
                if (target >= Tune.TARGET_NEED) { beginComplete(po); break }
                // sealed until the objective is complete: the barrier pushes the player back
                p.z = po.z - 0.62f; if (p.vz > 0f) p.vz = -4f
                if (t - sealedMsgT > 2.2f) {
                    sealedMsgT = t
                    fx.toast("THE GATE IS SEALED", "COLLECT ${Tune.TARGET_NEED - target} MORE BLUE BLOCKS", 0xFF9FDBFF.toInt(), 2.4f)
                    platform.sound(Sfx.WRONG); platform.sound(Sfx.SHIELD, 0.5f, 0.6f)
                    shake = max(shake, 0.2f)
                    if (cam.project(po.x, po.y + 1.2f, po.z)) fx.ring(cam.sx, cam.sy, 0xFF9FDBFF.toInt(), 30f * hud.s, 220f * hud.s, 0.5f, 10f * hud.s)
                }
            }
        }
    }

    private fun fire(tr: Trigger) {
        when (tr.event) {
            Ev.HINT_TARGET -> queueHint("Step on BLUE blocks to collect them!", 1)
            Ev.HINT_JUMP -> queueHint("Tap the JUMP button to cross gaps!", 2)
            Ev.HINT_TOOLS -> queueHint("Tap a TOOL to use it!", 3)
            Ev.HINT_BLOCK -> queueHint("BLOCK tool bridges gaps!", 4)
            Ev.HINT_MAGNET -> queueHint("MAGNET pulls in blocks out of reach!", 5)
            Ev.FORK -> fx.banner("CHOOSE YOUR PATH!", "LEFT: SAFE  •  RIGHT: TREASURE", 0xFFFFE14A.toInt(), 2.0f)
            Ev.TRAPS -> { fx.banner("DANGER ZONE!", "TIME YOUR STEPS — OR JUMP THE SPIKES", 0xFFFF8A5A.toInt(), 2.2f, true); platform.sound(Sfx.WARNING, 0.5f, 1.2f) }
            Ev.ZONE -> fx.toast(tr.text, tr.text2, 0xFFFFE14A.toInt())
            else -> ev.fire(tr.event)
        }
    }

    private var sealedMsgT = -9f
    private fun queueHint(s: String, kind: Int) { pendingHint = s; pendingHintKind = kind }

    private var endPortal: Portal? = null
    private var rainLeft = 0
    private var rainT = 0f
    private var gemRainLeft = 0
    /** The level-exit sequence: slow motion, camera pull-back, gate activates, boy steps in, rewards fly. */
    private fun beginComplete(po: Portal) {
        state = GS.COMPLETE; stateT = 0f; endPortal = po
        val p = player
        p.state = PS.WIN; p.stateT = 0f; p.spin = 0f
        slowTarget = 0.55f; slowHold = 1.6f
        platform.sound(Sfx.PORTAL)
        results = Results.compute(this)
        rainLeft = 20; rainT = 1.8f; gemRainLeft = results!!.gemReward
        for (tl in tools) tl.active = 0f
        hintT = 0f; pendingHint = ""
    }

    private fun updateComplete(dt: Float, sdt: Float) {
        val p = player
        val po = endPortal ?: return
        val st = stateT
        // walk into the gate
        if (st < 1.05f) {
            p.x = lerp(p.x, po.x, min(1f, sdt * 3f)); p.z = lerp(p.z, po.z - 0.2f, min(1f, sdt * 2.2f))
            p.runPhase += sdt * 3.2f; p.runW = approach(p.runW, 1f - smooth(st / 1.05f), sdt * 6f); p.airW = 0f
        }
        po.charge = min(1f, po.charge + dt * 0.9f)
        val prev = st - dt
        if (prev < 0.95f && st >= 0.95f) {
            flashWhite = 0.45f; shake = max(shake, 0.3f)
            fx.burst(po.x, po.y + 1.9f, po.z, 60, PK.STAR, 0xFFF0C0FF.toInt(), 8f, 0.2f, 1.2f)
            fx.confetti(po.x, po.y + 2.5f, po.z - 0.5f, 40, 6f)
            if (cam.project(po.x, po.y + 1.9f, po.z)) fx.ring(cam.sx, cam.sy, 0xFFFFB0F0.toInt(), 40f * hud.s, 420f * hud.s, 0.8f, 14f * hud.s)
            platform.sound(Sfx.WIN, 0.9f)
        }
        // spin and shrink into the swirl
        if (st > 1.1f) {
            if (prev <= 1.1f) platform.sound(Sfx.WHOOSH, 1f, 0.8f)
            val u = clamp01((st - 1.1f) / 0.6f)
            p.spin += dt * (6f + 18f * u)
            p.portalScale = 1f - easeInCubic(u)
            p.y = po.y + 1.2f * smooth(u)
        }
        // coins and gems fly from the gate to the HUD
        if (st > 1.8f) {
            rainT -= dt
            if (rainT <= 0f && (rainLeft > 0 || gemRainLeft > 0) && cam.project(po.x, po.y + 1.9f, po.z)) {
                rainT = 0.06f
                if (rainLeft > 0) {
                    rainLeft--
                    fx.fly(FK.COIN, cam.sx + fx.rng.f(-40f, 40f) * hud.s, cam.sy, hud.coinIconX(), hud.coinIconY(), results!!.rewardCoins / 20, 0f, 0.65f)
                    platform.sound(Sfx.COIN, 0.45f, 1.1f + (rainLeft % 5) * 0.05f)
                }
                if (gemRainLeft > 0) { gemRainLeft--; fx.fly(FK.GEM, cam.sx, cam.sy, hud.gemIconX(), hud.gemIconY(), 1, 0.03f, 0.7f) }
            }
        }
        if (st > 3.5f) { state = GS.RESULTS; stateT = 0f; hud.resultsStarted() }
    }

    private fun updateResults(dt: Float) {
        // the results panel drives its own animation from stateT; nothing to simulate
    }

    // ------------------------------------------------------------------ fail / capture
    fun fail(reason: String) {
        if (state == GS.FAILED || state == GS.RESULTS || state == GS.COMPLETE) return
        state = GS.FAILED; stateT = 0f; failReason = reason
        platform.sound(Sfx.LOSE)
        shake = max(shake, 0.5f)
        hintT = 0f
    }

    /** The guard got the boy: slow motion, grab, camera turns to show it, then GAME OVER. */
    fun beginCapture() {
        val p = player
        p.state = PS.CAUGHT; p.stateT = 0f; p.hurtFlash = 1f
        slowTarget = 0.4f; slowHold = 0.9f
        shake = max(shake, 0.9f); flashRed = 0.7f
        platform.sound(Sfx.GRAB); platform.sound(Sfx.ROAR); platform.haptic(true)
        fx.banner("CAUGHT!", "", 0xFFFF5A4A.toInt(), 1.8f)
    }

    // ------------------------------------------------------------------ camera
    private fun noise(x: Float) = sin(x) * 0.6f + sin(x * 2.17f + 1.3f) * 0.3f + sin(x * 4.31f + 2.1f) * 0.1f

    private fun updateCamera(dt: Float) {
        val p = player
        var dist = baseDist; var height = baseHeight; var pitch = basePitch; var fov = 1f; var roll = 0f; var yaw = 0f
        if (state == GS.INTRO && introSwoop) {
            val u = smooth(stateT / 2.8f)
            dist = lerp(9.2f, baseDist, u); height = lerp(7.8f, baseHeight, u); pitch = lerp(0.46f, basePitch, u); yaw = lerp(-0.22f, 0f, u)
        }
        if (ev.chase == Chase.RUN || ev.chase == Chase.WARNING) {
            val k = if (ev.chase == Chase.WARNING) smooth(ev.phaseT / 1.1f) else 1f
            dist += 1.3f * k; height += 0.9f * k; pitch += 0.05f * k; fov = lerp(1f, 0.95f, k); roll = sin(t * 1.25f) * 0.012f * k
        }
        if (ev.lavaOn && !ev.lavaStop) { dist += 0.5f; height += 0.5f; pitch += 0.03f }
        if (ev.finalOn && state == GS.PLAY) {
            // the final escape: a lower, closer, wider camera that sways with the collapse
            dist = baseDist - 0.3f; height = baseHeight - 0.85f; pitch = basePitch - 0.07f; fov = 0.88f; roll = sin(t * 1.6f) * 0.022f
        }
        if (speedOn) fov *= 0.93f
        if (p.state == PS.RESCUE_FALL || p.state == PS.DEAD) {
            // follow the fall from above so the tower does not get between the camera and the boy
            dist += 1.4f; height += 2.6f; pitch += 0.3f
        }
        if (state == GS.COMPLETE || state == GS.RESULTS) {
            // pull back until the whole gate is framed, then ease in toward the portal as the boy enters
            val u = if (state == GS.RESULTS) 1f else smooth(stateT / 1.7f)
            val push = if (state == GS.COMPLETE) smooth((stateT - 1.1f) / 1.2f) * 0.22f else 0.22f
            dist = lerp(baseDist, 15f, u) * (1f - push); height = lerp(baseHeight, 3.4f, u); pitch = lerp(basePitch, 0.13f, u)
            yaw = 0.1f * u + (if (state == GS.RESULTS) sin(stateT * 0.35f) * 0.04f else 0f)
        }
        if (!camInit) { camDist = dist; camHeight = height; camPitch = pitch; camFov = fov; camYaw = yaw }
        val k3 = damp(3f, dt)
        camDist = lerp(camDist, dist, k3); camHeight = lerp(camHeight, height, k3); camPitch = lerp(camPitch, pitch, k3)
        camFov = lerp(camFov, fov, damp(4f, dt)); camRoll = lerp(camRoll, roll, damp(3f, dt)); camYaw = lerp(camYaw, yaw, damp(4f, dt))

        // follow: lead a little in the running direction; rise only partly with jumps
        val lead = if (state == GS.PLAY && p.state == PS.NORMAL) clamp(p.vz * 0.07f, -0.15f, 0.45f) else 0f
        camLead = lerp(camLead, lead, damp(3f, dt))
        val tx = p.x * 0.78f
        val groundish = when {
            p.state == PS.DEAD -> max(p.y, p.lastGroundY - 3f)
            p.grounded || p.state != PS.NORMAL -> p.y
            else -> min(p.y, p.lastGroundY + (p.y - p.lastGroundY) * 0.4f)
        }
        if (!camInit) { camX = tx; camY = groundish; camZ = p.z; camInit = true; kickY = 0f; kickV = 0f }
        camX = lerp(camX, tx, damp(6f, dt))
        camY = lerp(camY, groundish, damp(if (groundish < camY) 7f else 4.5f, dt))
        camZ = lerp(camZ, p.z + camLead, damp(14f, dt))
        // jump / landing kick: a small spring on the camera height
        kickV += (-90f * kickY - 13f * kickV) * dt
        kickY += kickV * dt
        kickY = clamp(kickY, -0.35f, 0.35f)

        val sy = sin(camYaw); val cy = cos(camYaw)
        cam.yaw = camYaw
        cam.pitch = camPitch
        cam.ex = camX - sy * camDist
        cam.ey = camY + camHeight + kickY
        cam.ez = camZ - cy * camDist
        cam.f = hud.focal * baseFocal * camFov
        // smooth shake (trauma^2): position + a touch of roll
        val tr = max(shake, rumble)
        val amp = tr * tr * 24f * hud.s
        cam.cx = hud.w * 0.5f + amp * noise(t * 29f)
        cam.cy = hud.sceneCY + amp * noise(t * 31f + 7f)
        cam.roll = camRoll + tr * tr * 0.035f * noise(t * 21f + 3f)
        ev.applyCamera(this)
        cam.update()
    }

    // ------------------------------------------------------------------ render
    fun render(g: Gfx) {
        hud.layout(g.width, g.height)
        if (!camInit) updateCamera(0.016f)
        view.render(g)
        hud.render(g)
    }

    /** 0 at the start of the course .. 1 at the Ancient Gate. */
    fun gateProgress(): Float {
        val po = world.portals.firstOrNull() ?: return 0f
        return clamp01((player.z - world.spawnZ) / (po.z - world.spawnZ))
    }

    fun timeText(): String {
        val s = kotlin.math.ceil(max(0f, time)).toInt()
        return "%02d:%02d".format(s / 60, s % 60)
    }

    fun debugState() = "state=$state t=${"%.1f".format(time)} z=${"%.2f".format(player.z)} y=${"%.2f".format(player.y)} hearts=$hearts target=$target coins=$coinsCollected cp=$checkpoint score=$score"
}

class Results(
    val stars: Int, val targetGot: Int, val coinsCollected: Int, val coinTotal: Int,
    val timeLeft: Int, val hearts: Int, val mystery: Int, val mysteryTotal: Int,
    val levelScore: Int, val timeBonus: Int, val heartBonus: Int, val targetBonus: Int,
    val rewardCoins: Int, val gemReward: Int
) {
    val totalScore get() = levelScore + timeBonus + heartBonus + targetBonus
    companion object {
        fun compute(g: Game): Results {
            val tl = kotlin.math.ceil(g.time).toInt()
            var stars = 1
            if (g.target >= Tune.TARGET_NEED) stars++
            if (tl >= 20) stars++
            return Results(stars, g.target, g.coinsCollected, g.world.coinTotal, tl, g.hearts, g.mysteryOpened, g.world.mysteryTotal,
                g.score, tl * 20, g.hearts * 250, if (g.target >= Tune.TARGET_NEED) 1000 else 0,
                200 + stars * 100, stars * 5)
        }
    }
}
