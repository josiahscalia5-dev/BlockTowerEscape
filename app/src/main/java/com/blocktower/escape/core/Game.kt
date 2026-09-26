package com.blocktower.escape.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GS { const val INTRO = 0; const val PLAY = 1; const val TELEPORT = 2; const val REVEAL = 3; const val COMPLETE = 4; const val RESULTS = 5; const val FAILED = 6 }

object Sfx {
    const val COIN = 0; const val TARGET = 1; const val WRONG = 2; const val JUMP = 3; const val LAND = 4; const val BOUNCE = 5
    const val HIT = 6; const val FALL = 7; const val RESCUE = 8; const val CHECKPOINT = 9; const val MAGNET = 10; const val SHIELD = 11
    const val SPEED = 12; const val BLOCK = 13; const val MYSTERY = 14; const val WARNING = 15; const val STOMP = 16; const val ROAR = 17
    const val DRAGON = 18; const val EXPLODE = 19; const val CRUMBLE = 20; const val PORTAL = 21; const val WIN = 22; const val LOSE = 23
    const val CLICK = 24; const val TICK = 25; const val WIND = 26; const val THUNDER = 27; const val GEM = 28; const val TOOLGET = 29
    const val COUNT = 30
}

object TK { const val MAGNET = 0; const val SHIELD = 1; const val SPEED = 2; const val BLOCK = 3 }

class Tool(val kind: Int, var count: Int, val duration: Float, val cooldownDur: Float) {
    var active = 0f
    var cooldown = 0f
    var anim = 0f          // activation animation 1 -> 0
    var gain = 0f          // "+1" pickup animation
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
    var any = false
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
    val tools = arrayOf(Tool(TK.MAGNET, 3, 8f, 2f), Tool(TK.SHIELD, 2, 10f, 2f), Tool(TK.SPEED, 3, 6f, 2f), Tool(TK.BLOCK, 3, 0f, 1.2f))
    val ev = Events(this)
    val view = WorldRenderer(this)
    val hud = Hud(this)

    var state = GS.INTRO
    var stateT = 0f
    var paused = false
    var t = 0f                 // game clock (animation)
    var time = Tune.START_TIME // countdown
    var hearts = 2
    val maxHearts = 3
    var coins = Tune.START_COINS
    var gems = Tune.START_GEMS
    var target = 0
    var shownCoins = coins
    var shownGems = gems
    var shownTarget = 0
    var coinsCollected = 0
    var mysteryOpened = 0
    var targetsWrong = 0
    var falls = 0
    var checkpoint = 0
    var failReason = ""
    var shake = 0f
    var flashWhite = 0f
    var flashRed = 0f
    var hintT = 0f
    var hint = ""
    var hintKind = 0
    var results: Results? = null
    var completeDone = false
    var tickT = 0f
    var slowMo = 1f

    // camera tuning
    var camDist = 4.0f
    var camHeight = 3.17f
    var camPitch = 0.28f
    // base framing (tuned to match the Screen 4 artwork)
    var baseDist = 4.4f
    var baseHeight = 3.6f
    var basePitch = 0.30f
    var baseFocal = 1.0f
    var camX = 0f; var camY = 0f; var camZ = 0f
    var camYaw = 0f
    private var camInit = false

    val onArrive: (Flyer) -> Unit = { f -> arrive(f) }
    private val cand = ArrayList<Block>(64)
    private var magnetPullT = 0f

    init { restartLevel(first = true) }

    // ------------------------------------------------------------------ lifecycle
    fun restartLevel(first: Boolean = false) {
        if (!first) world = Level.build()
        player.reset(world.spawnX, world.spawnY, world.spawnZ)
        state = GS.INTRO; stateT = 0f; paused = false; time = Tune.START_TIME
        hearts = 2; coins = Tune.START_COINS; gems = Tune.START_GEMS; target = 0
        shownCoins = coins; shownGems = gems; shownTarget = 0
        coinsCollected = 0; mysteryOpened = 0; targetsWrong = 0; falls = 0; checkpoint = 0
        tools[0].count = 3; tools[1].count = 2; tools[2].count = 3; tools[3].count = 3
        for (tl in tools) { tl.active = 0f; tl.cooldown = 0f; tl.anim = 0f; tl.gain = 0f }
        fx.clear(); ev.reset()
        results = null; completeDone = false; failReason = ""
        shake = 0f; flashWhite = 0f; flashRed = 0f; hint = ""; hintT = 0f
        camInit = false; camYaw = 0f; slowMo = 1f
        // the block the player spawns on is already "touched" so it is not collected instantly
        gather(player.z - 1f, player.z + 1f)
        for (b in cand) if (touching(b, 0.06f)) { b.contact = true; b.wasContact = true }
    }

    fun start() {
        if (state != GS.INTRO) return
        state = GS.PLAY; stateT = 0f
        fx.banner("GO!", "", 0xFFFFE14A.toInt(), 1.1f)
        platform.sound(Sfx.CLICK)
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
        if (down) input.any = true
    }
    fun touchDown(id: Int, x: Float, y: Float) = hud.touchDown(id, x, y)
    fun touchMove(id: Int, x: Float, y: Float) = hud.touchMove(id, x, y)
    fun touchUp(id: Int, x: Float, y: Float) = hud.touchUp(id, x, y)

    fun togglePause() {
        if (state == GS.RESULTS || state == GS.FAILED) return
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
        val sdt = dt * slowMo

        if (state == GS.INTRO && (input.any || abs(input.axisX()) > 0.2f || abs(input.axisY()) > 0.2f || input.jumpPressed)) start()
        input.any = false

        if (state == GS.PLAY && !ev.revealing) {
            time -= sdt
            if (time <= 10f) {
                tickT -= sdt
                if (tickT <= 0f) { tickT = 1f; platform.sound(Sfx.TICK, 0.6f) }
            }
            if (time <= 0f) { time = 0f; fail("TIME'S UP!") }
        }

        updateTools(sdt)
        updateBlocks(sdt)
        ev.update(sdt)
        updatePlayer(sdt)
        updateCoins(sdt)
        checkTriggers()
        if (state == GS.TELEPORT) updateTeleport(sdt)
        if (state == GS.COMPLETE) updateComplete(sdt)
        updateCamera(dt)
        fx.update(sdt, onArrive)
        updateHudCounters(dt)

        shake = max(0f, shake - dt * 2.2f)
        flashWhite = max(0f, flashWhite - dt * 2.5f)
        flashRed = max(0f, flashRed - dt * 1.8f)
        if (hintT > 0f) hintT -= dt
        input.jumpPressed = false
        for (i in 0..3) input.toolTap[i] = false
    }

    private fun updateHudCounters(dt: Float) {
        // counters roll toward their true value (flyers bump them on arrival)
        if (shownCoins < coins) { val step = max(1, ((coins - shownCoins) * min(1f, dt * 6f)).toInt()); shownCoins = min(coins, shownCoins + step) }
        if (shownCoins > coins) shownCoins = coins
        if (shownGems < gems) shownGems = min(gems, shownGems + max(1, ((gems - shownGems) * min(1f, dt * 6f)).toInt()))
        if (shownGems > gems) shownGems = gems
        if (shownTarget > target) shownTarget = target
    }

    // ------------------------------------------------------------------ tools
    private fun updateTools(dt: Float) {
        for (i in 0..3) if (input.toolTap[i]) activateTool(i)
        for (tl in tools) {
            tl.anim = max(0f, tl.anim - dt * 1.6f)
            tl.gain = max(0f, tl.gain - dt * 1.2f)
            if (tl.active > 0f) {
                tl.active -= dt
                if (tl.active <= 0f) { tl.active = 0f; tl.cooldown = tl.cooldownDur }
            } else if (tl.cooldown > 0f) tl.cooldown = max(0f, tl.cooldown - dt)
        }
    }

    fun activateTool(k: Int) {
        if (state != GS.PLAY && state != GS.INTRO) return
        if (state == GS.INTRO) start()
        if (player.state != PS.NORMAL) return
        val tl = tools[k]
        if (tl.count <= 0) { hud.denied(k); platform.sound(Sfx.WRONG, 0.5f); return }
        if (!tl.ready) { hud.denied(k); return }
        if (k == TK.BLOCK) {
            if (!placeToolBlock()) { hud.denied(k); fx.popupWorld("NO GAP AHEAD", player.x, player.y + 2.3f, player.z, 0xFFFFD0D0.toInt(), 34f); return }
            tl.count--; tl.cooldown = tl.cooldownDur; tl.anim = 1f
            platform.sound(Sfx.BLOCK); platform.haptic(false)
            return
        }
        tl.count--
        tl.active = tl.duration
        tl.anim = 1f
        val p = player
        when (k) {
            TK.MAGNET -> { platform.sound(Sfx.MAGNET); fx.popupWorld("MAGNET!", p.x, p.y + 2.4f, p.z, 0xFFFF6B6B.toInt(), 44f)
                fx.burst(p.x, p.y + 0.8f, p.z, 26, PK.SPARK, 0xFFFF5050.toInt(), 5f, 0.16f, 0.6f) }
            TK.SHIELD -> { platform.sound(Sfx.SHIELD); fx.popupWorld("SHIELD!", p.x, p.y + 2.4f, p.z, 0xFF7FDBFF.toInt(), 44f)
                fx.burst(p.x, p.y + 0.8f, p.z, 26, PK.SPARK, 0xFF60D0FF.toInt(), 5f, 0.16f, 0.6f) }
            TK.SPEED -> { platform.sound(Sfx.SPEED); fx.popupWorld("SPEED!", p.x, p.y + 2.4f, p.z, 0xFFFFE14A.toInt(), 44f)
                fx.burst(p.x, p.y + 0.8f, p.z, 26, PK.SPARK, 0xFFFFE040.toInt(), 5f, 0.16f, 0.6f) }
        }
        platform.haptic(false)
    }

    val magnetOn get() = tools[TK.MAGNET].isActive
    val shieldOn get() = tools[TK.SHIELD].isActive
    val speedOn get() = tools[TK.SPEED].isActive

    /** Creates a temporary safe block in the first gap ahead (or under the player when airborne). */
    private fun placeToolBlock(): Boolean {
        val p = player
        val lx = p.x.roundToInt().toFloat().let { if (abs(p.x - it) > 0.5f) it else it }
        val lvl = p.lastGroundY
        val bottom = lvl - 1f
        if (!p.grounded && p.vy < 2f) {
            val z = floor(p.z).toFloat()
            if (!solidAt(lx, bottom, z)) { makeToolBlock(lx, bottom, z); return true }
        }
        val dirZ = if (input.axisY() < -0.5f) -1 else 1
        for (k in 1..3) {
            val z = floor(p.z).toFloat() + k * dirZ
            if (solidAt(lx, bottom, z)) continue
            if (solidAt(lx, bottom + 1f, z)) return false  // wall ahead, not a gap
            makeToolBlock(lx, bottom, z)
            return true
        }
        return false
    }

    private fun solidAt(x: Float, y: Float, z: Float): Boolean {
        val r = world.row(floor(z + 0.001f).toInt()) ?: return false
        for (b in r) {
            if (!b.collides() && !(b.type == BT.APPEAR && b.rise > 0f && b.visible)) continue
            if (x > b.x0 - 0.01f && x < b.x1 + 0.01f && y + 0.5f > b.y && y + 0.5f < b.y1) return true
        }
        return false
    }

    private fun makeToolBlock(x: Float, y: Float, z: Float) {
        val b = Block(x, y, z, BC.ENERGY, BT.TOOLBLOCK)
        b.life = 8f; b.rise = 0.6f; b.flash = 1f; b.remember()
        world.add(b)
        fx.burst(x, y + 0.9f, z + 0.5f, 22, PK.STAR, 0xFFB8F2FF.toInt(), 4f, 0.14f, 0.7f)
        fx.popupWorld("SAFE BLOCK!", x, y + 1.9f, z + 0.5f, 0xFFB8F2FF.toInt(), 36f)
    }

    // ------------------------------------------------------------------ blocks
    private val shiftCycle = intArrayOf(BC.RED, BC.YELLOW, BC.GREEN, BC.BLUE, BC.PURPLE)
    fun shiftColor(b: Block): Int = shiftCycle[(((t + b.phase) / 1.15f).toInt()) % shiftCycle.size]
    fun shiftFrac(b: Block): Float = fract((t + b.phase) / 1.15f)

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
                if (b.shake > 0f) b.shake = max(0f, b.shake - dt)
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
                        1 -> { b.timer += dt; b.alpha = if (((b.timer * 12f).toInt() and 1) == 0) 1f else 0.45f
                            if (b.timer > 0.75f) { b.state = 2; b.timer = 0f; b.visible = false; fx.burst(b.x, b.y + 0.5f, b.z + 0.5f, 10, PK.STAR, 0xFFE0B0FF.toInt(), 2.5f, 0.12f, 0.5f) } }
                        2 -> { b.timer += dt; if (b.timer > 3.2f && !playerInside(b)) { b.state = 3; b.timer = 0f; b.visible = true; b.alpha = 0f } }
                        3 -> { b.alpha = min(1f, b.alpha + dt * 2.5f); if (b.alpha >= 1f) b.state = 0 }
                    }
                    BT.FALLING -> when (b.state) {
                        1 -> { b.timer += dt; b.shake = 0.1f; if (b.timer > 0.45f) { b.state = 2; b.timer = 0f; b.vy = 0f; fx.dust(b.x, b.y, b.z + 0.5f, 5) } }
                        2 -> { b.vy -= Tune.GRAVITY * 0.7f * dt; b.y += b.vy * dt; b.alpha = clamp01(1f - (b.origY - b.y) / 10f)
                            if (b.y < b.origY - 10f) { b.state = 3; b.visible = false; b.timer = 0f } }
                        3 -> { b.timer += dt; if (b.timer > 4.5f) { b.y = b.origY; b.vy = 0f; b.visible = true; b.alpha = 0f; b.state = 4 } }
                        4 -> { if (playerInside(b)) b.alpha = 0f else b.alpha = min(1f, b.alpha + dt * 2.5f); if (b.alpha >= 1f) b.state = 0 }
                    }
                    BT.CRACKED -> when (b.state) {
                        2 -> { b.timer += dt; if (b.timer > 4.5f && !playerInside(b)) { b.state = 3; b.visible = true; b.alpha = 0f; b.damage = 0f } }
                        3 -> { b.alpha = min(1f, b.alpha + dt * 2.5f); if (b.alpha >= 1f) b.state = 0 }
                    }
                    BT.TRAP -> {
                        val c = (t + b.phase) % 2.6f
                        b.spike = when {
                            c < 1.4f -> 0f
                            c < 1.75f -> 0.25f * ((c - 1.4f) / 0.35f)
                            c < 1.85f -> 0.25f + 0.75f * ((c - 1.75f) / 0.1f)
                            c < 2.4f -> 1f
                            else -> 1f - (c - 2.4f) / 0.2f
                        }
                    }
                    BT.APPEAR -> {
                        if (b.state == 0 && b.shortcutId == 0) {
                            if (abs(b.z + 0.5f - player.z) < 5.5f && abs(b.x - player.x) < 4.6f && b.z + 0.5f > player.z - 1f) {
                                b.state = 1; b.visible = true; b.rise = 1f
                                platform.sound(Sfx.BLOCK, 0.35f, 1.4f)
                            }
                        }
                        if (b.state == 1) {
                            if (b.timer > 0f) b.timer -= dt
                            else {
                                b.rise = max(0f, b.rise - dt * 3f)
                                if (b.rise <= 0f) { b.state = 2; b.flash = 0.8f; fx.burst(b.x, b.y + 1f, b.z + 0.5f, 6, PK.STAR, Col.WHITE, 2f, 0.1f, 0.4f) }
                            }
                        }
                    }
                    BT.TOOLBLOCK -> {
                        b.rise = max(0f, b.rise - dt * 3f)
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

    private fun playerInside(b: Block): Boolean {
        val p = player
        return p.x + Tune.RADIUS > b.x0 && p.x - Tune.RADIUS < b.x1 && p.y + Tune.HEIGHT > b.y && p.y < b.y1 &&
            p.z + Tune.RADIUS > b.z && p.z - Tune.RADIUS < b.z1
    }

    // ------------------------------------------------------------------ player physics
    private fun gather(z0: Float, z1: Float) {
        cand.clear()
        for (r in floor(z0).toInt() - 1..floor(z1).toInt() + 1) {
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
        p.squash *= (1f - min(1f, dt * 10f))
        p.stateT += dt

        when (p.state) {
            PS.RESCUE_RIDE -> { updateRide(dt); return }
            PS.PORTAL, PS.WIN -> return
            PS.CAUGHT -> { p.y += dt * 1.5f; p.spin += dt * 9f; return }
            PS.DEAD -> { p.vy -= Tune.GRAVITY * dt; p.y += p.vy * dt; return }
        }
        val controllable = (state == GS.PLAY || state == GS.INTRO) && p.state == PS.NORMAL && !ev.revealing
        var ax = 0f; var az = 0f
        if (controllable && state == GS.PLAY) {
            ax = input.axisX(); az = input.axisY()
            val m = len2(ax, az)
            if (m < 0.18f) { ax = 0f; az = 0f } else if (m > 1f) { ax /= m; az /= m }
        }
        val maxV = if (speedOn) Tune.RUN_FAST else Tune.RUN
        val accel = if (p.grounded) Tune.ACCEL else Tune.AIR_ACCEL
        val tvx = ax * maxV + ev.windVX
        val tvz = az * maxV
        p.vx = approach(p.vx, tvx, accel * dt)
        p.vz = approach(p.vz, tvz, accel * dt)
        if (!controllable && p.grounded && ev.windVX == 0f) { p.vx *= 0.8f; p.vz *= 0.8f }

        // jumping (coyote time + buffer, variable height)
        if (controllable && state == GS.PLAY) {
            if (input.jumpPressed) p.jumpBuffer = 0.14f
            if (p.jumpBuffer > 0f) p.jumpBuffer -= dt
            if (p.grounded) p.coyote = 0.11f else p.coyote -= dt
            if (p.jumpBuffer > 0f && p.coyote > 0f) {
                p.vy = if (speedOn) Tune.JUMP_V_FAST else Tune.JUMP_V
                p.grounded = false; p.coyote = 0f; p.jumpBuffer = 0f; p.jumping = true
                p.squash = -0.14f
                platform.sound(Sfx.JUMP, 0.7f, if (speedOn) 1.2f else 1f)
                fx.dust(p.x, p.y, p.z, 4)
            }
            if (p.jumping && !input.jumpHeld && p.vy > 3.5f) p.vy = 3.5f
        }
        val g = if (p.vy < 0f) Tune.GRAVITY * 1.08f else Tune.GRAVITY
        p.vy = max(p.vy - g * dt, -24f)

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
            // Y
            p.y += p.vy * sdt
            var best: Block? = null
            for (b in cand) if (overlaps(b, p.x, p.y, p.z)) {
                if (p.vy <= 0f) { if (best == null || b.y1 > best.y1) best = b }
                else { p.y = b.y - Tune.HEIGHT - 1e-3f; p.vy = min(0f, p.vy); headHit(b) }
            }
            if (best != null) { p.y = best.y1; p.vy = 0f; p.grounded = true; landedOn = best }
            // X
            p.x += p.vx * sdt + carry / steps
            for (b in cand) if (overlaps(b, p.x, p.y, p.z)) {
                if (b.y1 - p.y in 0f..STEP && p.vy <= 0.5f && !blockedAbove(b, p.x, p.z)) { p.y = b.y1; p.grounded = true; landedOn = b; continue }
                p.x = if (p.x < b.x) b.x0 - Tune.RADIUS - 1e-3f else b.x1 + Tune.RADIUS + 1e-3f
                sideHit(b)
            }
            // Z
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
            landedOn?.let { onStand(it, dt) }
        } else p.airTime += dt

        // contacts (collect / open / trigger)
        for (b in cand) {
            b.wasContact = b.contact
            b.contact = touching(b, 0.05f)
            if (b.contact && !b.wasContact) onContact(b)
        }

        // animation
        val sp = len2(p.vx, p.vz)
        if (p.grounded && sp > 0.6f) {
            p.runPhase += dt * sp * 0.78f
            val f = (p.runPhase.toInt() and 1) == 1
            if (f != p.flip) { p.flip = f; if (sp > 2f) fx.dust(p.x, p.y, p.z - 0.2f, 1, 0x88FFFFFF.toInt()) }
            p.idleT = 0f
        } else p.idleT += dt
        p.lean = lerp(p.lean, clamp(p.vx / Tune.RUN, -1f, 1f), min(1f, dt * 10f))

        // fell off the course
        if (p.state == PS.NORMAL && !p.grounded && p.y < p.lastGroundY - 4.6f) startRescue("fall")
        if (p.state == PS.RESCUE_FALL) {
            if (p.grounded && p.ground?.type == BT.RESCUE) {
                if (p.stateT > 0.1f && p.rideT == 0f) { p.rideT = 0.0001f; p.stateT = 0f }
                if (p.rideT > 0f && p.stateT > 0.45f) beginRide()
            }
            if (p.y < p.fallFromY - 30f) beginRide()
        }
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
    }

    private fun sideHit(b: Block) {
        if (b.type == BT.MYSTERY && !b.used) openMystery(b)
    }

    private fun onLand(b: Block, vy: Float) {
        val p = player
        val hard = -vy
        if (hard > 4f) {
            p.squash = min(0.22f, hard * 0.012f)
            platform.sound(Sfx.LAND, clamp01(hard / 16f) * 0.8f + 0.2f)
            fx.dust(p.x, p.y, p.z, if (hard > 12f) 8 else 4)
        }
        when (b.type) {
            BT.BOUNCE -> {
                p.vy = 16.5f; p.grounded = false; p.jumping = false; b.squash = 1f
                platform.sound(Sfx.BOUNCE); fx.burst(b.x, b.y1, b.z + 0.5f, 12, PK.STAR, 0xFFFF94EE.toInt(), 3f, 0.14f, 0.5f)
            }
            BT.SAVE -> {
                p.vy = 17.5f; p.grounded = false; p.jumping = false; b.squash = 1f; b.flash = 1f
                p.lastGroundY = b.y1 + 3f
                platform.sound(Sfx.BOUNCE, 1f, 0.8f)
                fx.popupWorld("SAVED!", p.x, p.y + 2.3f, p.z, 0xFFFFE680.toInt(), 50f)
                fx.burst(b.x, b.y1, b.z + 0.5f, 22, PK.STAR, 0xFFFFF0A0.toInt(), 4f, 0.16f, 0.7f)
                time = max(1f, time - 2f)
                fx.popupScreen("-2s", hud.timerX(), hud.timerY() + 60f * hud.s, 0xFFFFB0B0.toInt(), 36f)
            }
            BT.BOOST -> {
                p.vy = 11.5f; p.vz = 10.5f; p.grounded = false; p.jumping = false; b.flash = 1f
                platform.sound(Sfx.SPEED, 0.9f, 1.3f)
                fx.burst(b.x, b.y1, b.z + 0.5f, 20, PK.STAR, 0xFF9AF0FF.toInt(), 4f, 0.16f, 0.7f)
                fx.popupWorld("BOOST!", p.x, p.y + 2.2f, p.z, 0xFF9AF0FF.toInt(), 44f)
            }
            BT.CRACKED -> if (hard > 13f) breakCracked(b)
            else -> {}
        }
    }

    private fun onStand(b: Block, dt: Float) {
        when (b.type) {
            BT.DISAPPEAR -> if (b.state == 0) { b.state = 1; b.timer = 0f; platform.sound(Sfx.CRUMBLE, 0.3f, 1.6f) }
            BT.FALLING -> if (b.state == 0) { b.state = 1; b.timer = 0f; platform.sound(Sfx.CRUMBLE, 0.5f) }
            BT.CRACKED -> if (b.state == 0) {
                b.damage += dt
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
    fun collectTarget(b: Block, remote: Boolean) {
        if (b.type != BT.TARGET) return
        target++
        b.type = BT.BRICK; b.color = BC.BRICK; b.flash = 1f
        fx.shards(b, BC.base(BC.BLUE), 16)
        fx.burst(b.x, b.y + 1f, b.z + 0.5f, 16, PK.STAR, 0xFFBFE4FF.toInt(), 4.5f, 0.14f, 0.6f, 0f, 1.5f)
        if (cam.project(b.x, b.y + 1f, b.z + 0.5f)) {
            fx.ring(cam.sx, cam.sy, 0xFF8FD0FF.toInt(), 20f * hud.s, 140f * hud.s, 0.5f, 10f * hud.s)
            fx.fly(FK.TARGET, cam.sx, cam.sy, hud.targetIconX(), hud.targetIconY(), 1, 0f, 0.7f)
        } else shownTarget = target
        fx.popupWorld("+1", b.x, b.y + 1.6f, b.z + 0.5f, 0xFF9FDBFF.toInt(), 54f)
        platform.sound(Sfx.TARGET, 1f, 1f + min(0.5f, target * 0.03f))
        platform.haptic(false)
        if (target == Tune.TARGET_NEED) {
            fx.banner("TARGET COMPLETE!", "12 BLUE BLOCKS COLLECTED", 0xFF7FFFA0.toInt(), 2.2f)
        }
    }

    private fun collectShift(b: Block) {
        val c = shiftColor(b)
        b.state = 1
        b.type = BT.BRICK; b.color = BC.BRICK; b.flash = 1f
        fx.shards(b, BC.base(c), 14)
        if (c == BC.BLUE) {
            b.type = BT.TARGET // count it through the normal path
            b.color = BC.BLUE
            collectTarget(b, false)
        } else {
            targetsWrong++
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
                val sp = 15f * dt / max(d, 0.001f)
                val k = min(1f, sp)
                c.x += dx * k; c.y += dy * k; c.z += dz * k
            }
            if (d < 0.8f && (p.state == PS.NORMAL || p.state == PS.RESCUE_FALL)) collectCoin(c)
        }
        // magnet also pulls blue target blocks (their shells fly to the player)
        if (magnetOn && p.state == PS.NORMAL) {
            magnetPullT -= dt
            if (magnetPullT <= 0f) {
                gather(p.z - 3.5f, p.z + 3.5f)
                for (b in cand) {
                    if (b.type != BT.TARGET) continue
                    if (len3(b.x - px, b.y + 0.5f - p.y, b.z + 0.5f - pz) < 3.4f) {
                        collectTarget(b, true)
                        for (i in 0 until 8) {
                            val q = fx.spawn()
                            q.x = b.x; q.y = b.y + 0.8f; q.z = b.z + 0.5f
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
        platform.sound(Sfx.COIN, 0.8f, 1f + (coinsCollected % 5) * 0.04f)
        if (cam.project(c.x, c.y, c.z)) {
            fx.fly(FK.COIN, cam.sx, cam.sy, hud.coinIconX(), hud.coinIconY(), 0, 0f, 0.55f)
            fx.burst(c.x, c.y, c.z, 6, PK.SPARK, 0xFFFFE070.toInt(), 2.5f, 0.1f, 0.35f)
        }
    }

    private fun arrive(f: Flyer) {
        when (f.kind) {
            FK.TARGET -> { shownTarget = min(target, shownTarget + 1); hud.bumpTarget() }
            FK.COIN -> hud.bumpCoins()
            FK.GEM -> hud.bumpGems()
            FK.HEART -> hud.bumpHearts()
            FK.TOOL -> { tools[f.value].gain = 1f }
        }
        if (f.kind == FK.COIN && f.value > 0) coins += f.value
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
        when (b.reward) {
            Reward.COINS -> {
                for (i in 0 until 6) fx.fly(FK.COIN, sx, sy, hud.coinIconX(), hud.coinIconY(), Tune.COIN_VALUE, i * 0.08f, 0.8f)
                fx.popupWorld("+60 COINS", b.x, b.y + 1.8f, b.z + 0.5f, 0xFFFFE14A.toInt(), 42f)
            }
            Reward.GEMS -> {
                gems += 5
                for (i in 0 until 5) fx.fly(FK.GEM, sx, sy, hud.gemIconX(), hud.gemIconY(), 0, i * 0.09f, 0.8f)
                fx.popupWorld("+5 GEMS", b.x, b.y + 1.8f, b.z + 0.5f, 0xFFE59CFF.toInt(), 42f)
                platform.sound(Sfx.GEM)
            }
            Reward.TOOL_MAGNET, Reward.TOOL_SHIELD, Reward.TOOL_SPEED, Reward.TOOL_BLOCK -> {
                val k = b.reward - Reward.TOOL_MAGNET
                tools[k].count++
                fx.fly(FK.TOOL, sx, sy, hud.toolX(k), hud.toolY(k), k, 0.1f, 0.8f)
                val name = arrayOf("MAGNET", "SHIELD", "SPEED", "BLOCK")[k]
                fx.popupWorld("+1 $name", b.x, b.y + 1.8f, b.z + 0.5f, 0xFFB8F2FF.toInt(), 42f)
                platform.sound(Sfx.TOOLGET)
            }
            Reward.HEART -> {
                if (hearts < maxHearts) {
                    hearts++
                    fx.fly(FK.HEART, sx, sy, hud.heartX(hearts - 1), hud.heartY(), 0, 0.05f, 0.8f)
                    fx.popupWorld("+1 HEART", b.x, b.y + 1.8f, b.z + 0.5f, 0xFFFF7A8A.toInt(), 42f)
                } else {
                    coins += 100
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
                    p.vz = -6f; p.vy = 7f; p.grounded = false; p.hurtFlash = 1f; flashRed = 0.6f
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
        // opened block becomes a plain brick
        b.type = BT.BRICK; b.color = BC.BRICK
    }

    // ------------------------------------------------------------------ damage, falling, recovery
    fun breakShield() {
        tools[TK.SHIELD].active = 0f; tools[TK.SHIELD].cooldown = tools[TK.SHIELD].cooldownDur
        fx.burst(player.x, player.y + 0.8f, player.z, 30, PK.SPARK, 0xFF9FE8FF.toInt(), 6f, 0.16f, 0.6f)
        platform.sound(Sfx.SHIELD, 1f, 0.7f)
        player.invuln = 1f
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
        p.invuln = 1.6f; p.hurtFlash = 1f
        flashRed = 1f; shake = max(shake, 0.55f)
        platform.sound(Sfx.HIT); platform.haptic(true)
        fx.popupScreen("-1", hud.heartX(max(0, hearts)), hud.heartY() + 50f * hud.s, 0xFFFF6070.toInt(), 44f)
        hud.bumpHearts()
        if (src != null) { p.vy = 8f; p.vz = -3f; p.grounded = false }
        if (hearts <= 0) fail(when (reason) { "guard" -> "CAUGHT BY THE TOWER GUARD!"; else -> "OUT OF HEARTS!" })
    }

    fun startRescue(cause: String) {
        val p = player
        if (p.state != PS.NORMAL || state != GS.PLAY) return
        falls++
        hearts--
        hud.bumpHearts()
        platform.sound(Sfx.FALL)
        flashRed = 0.8f
        fx.popupScreen("-1", hud.heartX(max(0, hearts)), hud.heartY() + 50f * hud.s, 0xFFFF6070.toInt(), 44f)
        if (hearts <= 0) {
            p.state = PS.DEAD; p.stateT = 0f
            fail(if (cause == "lava") "BURNED BY THE LAVA!" else "OUT OF HEARTS!")
            return
        }
        p.state = PS.RESCUE_FALL; p.stateT = 0f; p.rideT = 0f; p.fallFromY = p.y
        p.vy = max(p.vy, -9f)
        // emergency platform appears below the falling player
        val rb = Block(p.x, p.y - 2.2f, p.z - 1.1f, BC.ENERGY, BT.RESCUE)
        rb.sx = 2.2f; rb.sy = 0.35f; rb.sz = 2.2f; rb.rise = 0f; rb.flash = 1f; rb.remember()
        world.add(rb)
        rescueBlock = rb
        fx.burst(p.x, p.y - 2f, p.z, 26, PK.STAR, 0xFFB8F2FF.toInt(), 5f, 0.16f, 0.8f)
        fx.popupWorld(if (cause == "lava") "TOO HOT!" else "CAUGHT!", p.x, p.y + 1.6f, p.z, 0xFFB8F2FF.toInt(), 46f)
        platform.sound(Sfx.RESCUE)
    }
    var rescueBlock: Block? = null

    private fun beginRide() {
        val p = player
        val cp = world.checkpoints[checkpoint]
        p.state = PS.RESCUE_RIDE; p.stateT = 0f
        p.rideX0 = p.x; p.rideY0 = p.y; p.rideZ0 = p.z
        p.rideX1 = cp.x; p.rideY1 = cp.y; p.rideZ1 = cp.z
        val d = len3(p.rideX1 - p.rideX0, p.rideY1 - p.rideY0, p.rideZ1 - p.rideZ0)
        p.rideDur = clamp(d / 14f, 1.2f, 2.8f)
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
        p.y = lerp(p.rideY0, p.rideY1, u) + sin(u * Math.PI.toFloat()) * 4f
        if (((t * 30f).toInt() % 3) == 0) {
            val q = fx.spawn()
            q.x = p.x + fx.rng.f(-0.8f, 0.8f); q.y = p.y - 0.2f; q.z = p.z + fx.rng.f(-0.8f, 0.8f)
            q.vx = 0f; q.vy = -1f; q.vz = 0f; q.life = 0.6f; q.maxLife = 0.6f; q.size = 0.12f; q.color = 0xFFB8F2FF.toInt(); q.kind = PK.STAR
        }
        if (p.rideT >= 1f) {
            val cp = world.checkpoints[checkpoint]
            restoreFrom(cp.z)
            p.reset(cp.x, cp.y, cp.z)
            p.invuln = 1.5f
            fx.burst(p.x, p.y + 0.3f, p.z, 20, PK.STAR, 0xFFB8F2FF.toInt(), 4f, 0.14f, 0.6f)
            fx.popupWorld("CONTINUE!", p.x, p.y + 2.3f, p.z, Col.WHITE, 44f)
            gather(p.z - 1f, p.z + 1f)
            for (b in cand) { b.contact = touching(b, 0.05f); b.wasContact = b.contact }
        }
    }

    /** Puts the course back the way it was after the checkpoint so the player can retry that part. */
    private fun restoreFrom(cpZ: Float) {
        for (b in world.blocks) {
            if (b.row <= cpZ) continue
            if (b.eventTag != 0 && b.destroyed) { b.destroyed = false; b.visible = true; b.y = b.origY; b.vy = 0f; b.alpha = 1f; b.state = 0; b.shake = 0f }
            when (b.type) {
                BT.DISAPPEAR, BT.FALLING, BT.CRACKED -> if (b.state != 0) { b.state = 0; b.visible = true; b.alpha = 1f; b.y = b.origY; b.vy = 0f; b.damage = 0f; b.timer = 0f }
                else -> {}
            }
        }
        world.blocks.filter { it.type == BT.TOOLBLOCK }.forEach { world.remove(it) }
        for (tr in world.triggers) if (tr.z > cpZ && tr.event in setOf(Ev.STORM, Ev.STORM_END, Ev.CHASE, Ev.CHASE_END, Ev.DRAGON, Ev.COLLAPSE, Ev.LAVA, Ev.LAVA_STOP)) tr.fired = false
        ev.resetAfter(cpZ)
    }

    fun activateCheckpoint(id: Int) {
        if (id < 0) return
        val cp = world.checkpoints[id]
        if (cp.active) { if (checkpoint < id) checkpoint = id; return }
        cp.active = true; cp.activeTime = t
        checkpoint = id
        platform.sound(Sfx.CHECKPOINT); platform.haptic(false)
        fx.burst(cp.x, cp.y + 2.4f, cp.z, 40, PK.STAR, 0xFFFFE680.toInt(), 6f, 0.18f, 1.0f)
        fx.popupWorld("CHECKPOINT!", cp.x, cp.y + 3.2f, cp.z, 0xFFFFE680.toInt(), 52f)
        if (cam.project(cp.x, cp.y + 1f, cp.z)) fx.ring(cam.sx, cam.sy, 0xFFFFE680.toInt(), 30f * hud.s, 260f * hud.s, 0.8f, 12f * hud.s)
    }

    // ------------------------------------------------------------------ triggers & portals
    private fun checkTriggers() {
        if (state != GS.PLAY || player.state != PS.NORMAL) return
        val p = player
        for (tr in world.triggers) {
            if (tr.fired) continue
            if (p.z >= tr.z && p.x >= tr.xMin && p.x <= tr.xMax) { tr.fired = true; fire(tr.event) }
        }
        for (po in world.portals) {
            if (abs(p.x - po.x) < 1.0f && abs(p.z - po.z) < 0.55f && p.y > po.y - 0.5f && p.y < po.y + 2.5f) {
                if (po.isEnd) beginComplete(po) else beginTeleport(po)
                break
            }
        }
    }

    private fun fire(e: Int) {
        when (e) {
            Ev.HINT_TARGET -> showHint("Step on BLUE blocks to collect them!", 1)
            Ev.HINT_JUMP -> showHint("Tap the JUMP button to cross gaps!", 2)
            Ev.HINT_TOOLS -> showHint("Tap a TOOL to use it!", 3)
            Ev.HINT_FORK -> fx.banner("CHOOSE YOUR PATH!", "ONE WAY IS A TRAP", 0xFFFFE14A.toInt(), 2.0f)
            Ev.HINT_BLOCK -> showHint("BLOCK tool bridges gaps!", 4)
            else -> ev.fire(e)
        }
    }

    fun showHint(s: String, kind: Int) { hint = s; hintKind = kind; hintT = 3.8f }

    private var tpPortal: Portal? = null
    private var tpDone = false
    private fun beginTeleport(po: Portal) {
        state = GS.TELEPORT; stateT = 0f; tpPortal = po; tpDone = false
        player.state = PS.PORTAL; player.stateT = 0f
        platform.sound(Sfx.PORTAL)
        fx.banner("PORTAL!", "TO THE STORM TOWER", 0xFFE0A0FF.toInt(), 1.6f)
    }

    private fun updateTeleport(dt: Float) {
        val p = player
        val po = tpPortal ?: return
        if (!tpDone) {
            p.portalScale = max(0.05f, 1f - stateT / 0.55f)
            p.spin += dt * 14f
            p.x = lerp(p.x, po.x, min(1f, dt * 6f)); p.z = lerp(p.z, po.z, min(1f, dt * 6f))
            if (stateT > 0.55f) {
                tpDone = true; flashWhite = 1f
                p.x = po.destX; p.y = po.destY; p.z = po.destZ; p.vx = 0f; p.vy = 0f; p.vz = 0f
                p.lastGroundY = p.y
                camInit = false
                fx.burst(p.x, p.y + 0.8f, p.z, 34, PK.STAR, 0xFFE0A0FF.toInt(), 5f, 0.16f, 0.8f)
            }
        } else {
            p.portalScale = min(1f, (stateT - 0.55f) / 0.45f)
            if (stateT > 1.0f) { p.portalScale = 1f; p.spin = 0f; p.state = PS.NORMAL; state = GS.PLAY; p.grounded = true }
        }
    }

    // ------------------------------------------------------------------ fail / complete
    fun fail(reason: String) {
        if (state == GS.FAILED || state == GS.RESULTS || state == GS.COMPLETE) return
        state = GS.FAILED; stateT = 0f; failReason = reason
        platform.sound(Sfx.LOSE)
        shake = max(shake, 0.8f)
    }

    private var endPortal: Portal? = null
    private var coinRainLeft = 0
    private var coinRainT = 0f
    private fun beginComplete(po: Portal) {
        state = GS.COMPLETE; stateT = 0f; endPortal = po
        player.state = PS.WIN; player.stateT = 0f
        platform.sound(Sfx.PORTAL); platform.sound(Sfx.WIN, 0.9f)
        coinRainLeft = 24; coinRainT = 0.9f
        for (tl in tools) tl.active = 0f
    }

    private fun updateComplete(dt: Float) {
        val p = player
        val po = endPortal ?: return
        p.x = lerp(p.x, po.x, min(1f, dt * 3f)); p.z = lerp(p.z, po.z - 0.4f, min(1f, dt * 3f))
        po.charge = min(1f, po.charge + dt * 0.8f)
        if (stateT > 0.8f && stateT - dt <= 0.8f) { flashWhite = 0.7f; shake = 0.3f; fx.burst(po.x, po.y + 1.5f, po.z, 60, PK.STAR, 0xFFF0C0FF.toInt(), 8f, 0.2f, 1.2f) }
        // coins fly in toward the player, then on to the counter
        if (coinRainLeft > 0) {
            coinRainT -= dt
            if (coinRainT <= 0f) {
                coinRainT = 0.06f; coinRainLeft--
                if (cam.project(p.x, p.y + 1f, p.z)) {
                    val a = fx.rng.f(0f, TAU); val r = max(hud.w, hud.h) * 0.6f
                    val sx0 = hud.w * 0.5f + kotlin.math.cos(a) * r; val sy0 = hud.h * 0.5f + kotlin.math.sin(a) * r
                    fx.fly(FK.COIN, sx0, sy0, cam.sx, cam.sy, 0, 0f, 0.55f)
                    fx.fly(FK.COIN, cam.sx, cam.sy, hud.coinIconX(), hud.coinIconY(), 25, 0.55f, 0.6f)
                    platform.sound(Sfx.COIN, 0.5f, 1.2f)
                }
            }
        }
        if (stateT > 2.4f && !completeDone) {
            completeDone = true
            results = Results.compute(this)
            hud.bumpTarget()
        }
        if (stateT > 3.4f) { state = GS.RESULTS; stateT = 0f; results?.let { r -> coins += r.bonusCoins; gems += r.gemReward } }
    }

    // ------------------------------------------------------------------ camera
    private fun updateCamera(dt: Float) {
        val p = player
        val sec = world.sectionAt(p.z)
        val ox = sec?.originX ?: 0f
        var dist = baseDist; var height = baseHeight; var pitch = basePitch
        if (ev.chaseActive) { dist = baseDist + 1.0f; height = baseHeight + 0.8f; pitch = basePitch + 0.04f }
        if (state == GS.COMPLETE || state == GS.RESULTS) {
            val u = smooth(if (state == GS.RESULTS) 1f else stateT / 1.6f)
            dist = lerp(baseDist, 10.5f, u); height = lerp(baseHeight, 7.2f, u); pitch = lerp(basePitch, 0.36f, u)
        }
        if (!camInit) { camDist = dist; camHeight = height; camPitch = pitch }
        camDist = lerp(camDist, dist, damp(3f, dt)); camHeight = lerp(camHeight, height, damp(3f, dt)); camPitch = lerp(camPitch, pitch, damp(3f, dt))
        camYaw = 0f
        val tx = ox + (p.x - ox) * 0.78f
        val groundish = if (p.grounded || p.state != PS.NORMAL) p.y else min(p.y, p.lastGroundY + (p.y - p.lastGroundY) * 0.35f)
        val tyBase = if (p.state == PS.RESCUE_RIDE) p.y else groundish
        if (!camInit) { camX = tx; camY = tyBase; camZ = p.z; camInit = true }
        camX = lerp(camX, tx, damp(6f, dt))
        camY = lerp(camY, tyBase, damp(if (p.y < camY) 7f else 4.5f, dt))
        camZ = lerp(camZ, p.z, damp(14f, dt))
        val sy = sin(camYaw); val cy = kotlin.math.cos(camYaw)
        cam.yaw = camYaw
        cam.pitch = camPitch
        cam.ex = camX - sy * camDist
        cam.ey = camY + camHeight
        cam.ez = camZ - cy * camDist
        cam.f = hud.focal * baseFocal
        val sh = shake * shake * 26f * hud.s
        cam.cx = hud.w * 0.5f + (if (sh > 0f) fx.rng.f(-sh, sh) else 0f)
        cam.cy = hud.sceneCY + (if (sh > 0f) fx.rng.f(-sh, sh) else 0f)
        ev.revealCamera(this)
        cam.update()
    }

    // ------------------------------------------------------------------ render
    fun render(g: Gfx) {
        hud.layout(g.width, g.height)
        if (!camInit) updateCamera(0.016f)
        view.render(g)
        hud.render(g)
    }

    fun timeText(): String {
        val s = kotlin.math.ceil(max(0f, time)).toInt()
        return "%02d:%02d".format(s / 60, s % 60)
    }

    fun debugState() = "state=$state t=${"%.1f".format(time)} z=${"%.2f".format(player.z)} y=${"%.2f".format(player.y)} hearts=$hearts target=$target coins=$coinsCollected cp=$checkpoint"
}

class Results(
    val completed: Boolean, val stars: Int, val targetGot: Int, val coinsCollected: Int, val coinTotal: Int,
    val timeLeft: Int, val hearts: Int, val mystery: Int, val mysteryTotal: Int,
    val timeBonus: Int, val heartBonus: Int, val targetBonus: Int, val gemReward: Int
) {
    val bonusCoins get() = timeBonus + heartBonus + targetBonus
    companion object {
        fun compute(g: Game): Results {
            val tl = kotlin.math.ceil(g.time).toInt()
            var stars = 1
            if (g.target >= Tune.TARGET_NEED) stars++
            if (tl >= 20) stars++
            return Results(true, stars, g.target, g.coinsCollected, g.world.coinTotal, tl, g.hearts, g.mysteryOpened, g.world.mysteryTotal,
                tl * 10, g.hearts * 100, if (g.target >= Tune.TARGET_NEED) 500 else g.target * 25, stars * 5)
        }
    }
}
