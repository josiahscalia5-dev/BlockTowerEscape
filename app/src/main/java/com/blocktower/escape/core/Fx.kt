package com.blocktower.escape.core

/** World-space particle (pooled). */
class Particle {
    @JvmField var active = false
    @JvmField var x = 0f; @JvmField var y = 0f; @JvmField var z = 0f
    @JvmField var vx = 0f; @JvmField var vy = 0f; @JvmField var vz = 0f
    @JvmField var life = 0f; @JvmField var maxLife = 1f
    @JvmField var size = 0.1f
    @JvmField var color = Col.WHITE
    @JvmField var kind = 0
    @JvmField var rot = 0f; @JvmField var vrot = 0f
    @JvmField var gravity = 0f
    @JvmField var drag = 0f
    @JvmField var grow = 0f
}

object PK { const val SPARK = 0; const val SHARD = 1; const val DUST = 2; const val EMBER = 3; const val STAR = 4; const val FIRE = 5
    const val CONFETTI = 7; const val STREAK = 8 }

/** Screen-space item flying to a HUD counter. */
class Flyer {
    @JvmField var active = false
    @JvmField var x0 = 0f; @JvmField var y0 = 0f; @JvmField var x1 = 0f; @JvmField var y1 = 0f
    @JvmField var t = 0f; @JvmField var dur = 0.8f; @JvmField var delay = 0f
    @JvmField var kind = 0
    @JvmField var value = 0
    @JvmField var arc = 0f
}
object FK { const val COIN = 0; const val TARGET = 1; const val GEM = 2; const val TOOL = 3; const val HEART = 4 }

class Popup {
    @JvmField var active = false
    @JvmField var text = ""
    @JvmField var x = 0f; @JvmField var y = 0f; @JvmField var z = 0f
    @JvmField var world = true
    @JvmField var t = 0f; @JvmField var dur = 1.2f
    @JvmField var color = Col.WHITE
    @JvmField var size = 44f
}

class Banner(val line1: String, val line2: String, val color: Int, val dur: Float, val warn: Boolean) {
    var t = 0f
}

/** Small message under the top bar (zone names, objective updates). */
class Toast(val text: String, val sub: String, val color: Int, val dur: Float) {
    var t = 0f
}

class ScreenRing {
    @JvmField var active = false
    @JvmField var x = 0f; @JvmField var y = 0f
    @JvmField var t = 0f; @JvmField var dur = 0.6f; @JvmField var r0 = 20f; @JvmField var r1 = 160f
    @JvmField var color = Col.WHITE
    @JvmField var width = 8f
}

class Fx {
    val parts = Array(900) { Particle() }
    val flyers = Array(96) { Flyer() }
    val popups = Array(24) { Popup() }
    val rings = Array(16) { ScreenRing() }
    val banners = ArrayList<Banner>()
    val toasts = ArrayList<Toast>()
    private var pi = 0
    val rng = Rng(777)

    fun spawn(): Particle {
        for (k in parts.indices) {
            pi = (pi + 1) % parts.size
            val p = parts[pi]
            if (!p.active) { p.active = true; p.rot = 0f; p.vrot = 0f; p.gravity = 0f; p.drag = 0f; p.grow = 0f; return p }
        }
        pi = (pi + 1) % parts.size
        return parts[pi].also { it.active = true }
    }

    fun burst(x: Float, y: Float, z: Float, n: Int, kind: Int, color: Int, speed: Float, size: Float, life: Float, gravity: Float = 0f, up: Float = 0f) {
        for (i in 0 until n) {
            val p = spawn()
            p.x = x; p.y = y; p.z = z
            val a = rng.f(0f, TAU); val e = rng.f(-0.3f, 1f)
            val s = speed * rng.f(0.45f, 1f)
            p.vx = kotlin.math.cos(a) * s * (1f - absf(e) * 0.4f)
            p.vz = kotlin.math.sin(a) * s * (1f - absf(e) * 0.4f)
            p.vy = e * s + up
            p.life = life * rng.f(0.6f, 1f); p.maxLife = p.life
            p.size = size * rng.f(0.6f, 1.2f)
            p.color = color; p.kind = kind; p.gravity = gravity
            p.rot = rng.f(0f, TAU); p.vrot = rng.f(-8f, 8f); p.drag = 1.2f
        }
    }

    /** Pieces of a broken block; [cvx], [cvy], [cvz] carry them along (a block smashed at speed), [size] scales them. */
    fun shards(b: Block, color: Int, n: Int = 14, cvx: Float = 0f, cvy: Float = 0f, cvz: Float = 0f, size: Float = 1f) {
        for (i in 0 until n) {
            val p = spawn()
            p.x = b.x + rng.f(-0.45f, 0.45f); p.y = b.y + rng.f(0.2f, 1f); p.z = b.z + rng.f(0.05f, 0.95f)
            p.vx = (p.x - b.x) * rng.f(4f, 9f) + cvx; p.vz = (p.z - b.z - 0.5f) * rng.f(4f, 9f) + cvz; p.vy = rng.f(3f, 8f) + cvy
            p.life = rng.f(0.6f, 1.1f); p.maxLife = p.life
            p.size = rng.f(0.12f, 0.26f) * size; p.color = color; p.kind = PK.SHARD; p.gravity = 22f
            p.rot = rng.f(0f, TAU); p.vrot = rng.f(-12f, 12f)
        }
    }

    private val confettiColors = intArrayOf(0xFFFF4A5A.toInt(), 0xFFFFD23A.toInt(), 0xFF4ADB6A.toInt(), 0xFF3FA8FF.toInt(), 0xFFD884FA.toInt(), 0xFFFFFFFF.toInt())
    fun confetti(x: Float, y: Float, z: Float, n: Int, speed: Float = 5f) {
        for (i in 0 until n) {
            val p = spawn()
            p.x = x + rng.f(-0.3f, 0.3f); p.y = y; p.z = z + rng.f(-0.3f, 0.3f)
            val a = rng.f(0f, TAU); val s = speed * rng.f(0.3f, 1f)
            p.vx = kotlin.math.cos(a) * s; p.vz = kotlin.math.sin(a) * s; p.vy = rng.f(3f, 8f)
            p.life = rng.f(1.1f, 1.8f); p.maxLife = p.life; p.size = rng.f(0.07f, 0.12f)
            p.color = confettiColors[rng.i(confettiColors.size)]; p.kind = PK.CONFETTI
            p.gravity = 9f; p.drag = 2.2f; p.rot = rng.f(0f, TAU); p.vrot = rng.f(-14f, 14f)
        }
    }

    fun dust(x: Float, y: Float, z: Float, n: Int, color: Int = 0xFFD8C8B0.toInt()) {
        for (i in 0 until n) {
            val p = spawn()
            p.x = x + rng.f(-0.4f, 0.4f); p.y = y + rng.f(0f, 0.2f); p.z = z + rng.f(-0.4f, 0.4f)
            p.vx = rng.f(-1.2f, 1.2f); p.vy = rng.f(0.3f, 1.4f); p.vz = rng.f(-1.2f, 1.2f)
            p.life = rng.f(0.5f, 0.9f); p.maxLife = p.life; p.size = rng.f(0.18f, 0.35f); p.grow = 0.6f
            p.color = color; p.kind = PK.DUST; p.drag = 2f
        }
    }

    fun fly(kind: Int, x0: Float, y0: Float, x1: Float, y1: Float, value: Int, delay: Float = 0f, dur: Float = 0.75f) {
        for (f in flyers) if (!f.active) {
            f.active = true; f.kind = kind; f.x0 = x0; f.y0 = y0; f.x1 = x1; f.y1 = y1; f.value = value
            f.t = 0f; f.delay = delay; f.dur = dur; f.arc = rng.f(-160f, 160f)
            return
        }
    }

    fun popupWorld(text: String, x: Float, y: Float, z: Float, color: Int, size: Float = 46f, dur: Float = 1.1f) {
        for (p in popups) if (!p.active) {
            p.active = true; p.text = text; p.x = x; p.y = y; p.z = z; p.world = true; p.t = 0f; p.dur = dur; p.color = color; p.size = size
            return
        }
    }

    fun popupScreen(text: String, x: Float, y: Float, color: Int, size: Float = 46f, dur: Float = 1.2f) {
        for (p in popups) if (!p.active) {
            p.active = true; p.text = text; p.x = x; p.y = y; p.world = false; p.t = 0f; p.dur = dur; p.color = color; p.size = size
            return
        }
    }

    fun ring(x: Float, y: Float, color: Int, r0: Float, r1: Float, dur: Float = 0.6f, width: Float = 8f) {
        for (r in rings) if (!r.active) {
            r.active = true; r.x = x; r.y = y; r.color = color; r.r0 = r0; r.r1 = r1; r.dur = dur; r.t = 0f; r.width = width
            return
        }
    }

    fun banner(l1: String, l2: String, color: Int, dur: Float = 2.4f, warn: Boolean = false) {
        // a warning on screen is never cut short by good news: that waits its turn
        val cur = banners.firstOrNull()
        if (!warn && cur != null && cur.warn && cur.dur - cur.t > 0.4f) {
            banners.removeAll { it !== cur && !it.warn }
            banners.add(Banner(l1, l2, color, dur, warn)); return
        }
        banners.clear()
        banners.add(Banner(l1, l2, color, dur, warn))
    }
    /** A banner that follows the one on screen (two-part announcements). */
    fun bannerThen(l1: String, l2: String, color: Int, dur: Float = 2.4f, warn: Boolean = false) {
        banners.add(Banner(l1, l2, color, dur, warn))
    }

    fun toast(text: String, sub: String, color: Int, dur: Float = 2.2f) {
        if (toasts.size >= 3) toasts.removeAt(toasts.size - 1)
        toasts.add(Toast(text, sub, color, dur))
    }

    /** Returns list of flyers that arrived this frame through the callback. */
    fun update(dt: Float, onArrive: (Flyer) -> Unit) {
        for (p in parts) if (p.active) {
            p.life -= dt
            if (p.life <= 0f) { p.active = false; continue }
            val dr = 1f / (1f + p.drag * dt)
            p.vx *= dr; p.vz *= dr
            if (p.kind != PK.EMBER && p.kind != PK.FIRE) p.vy *= dr
            p.vy -= p.gravity * dt
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt
            p.rot += p.vrot * dt
            p.size += p.grow * dt
        }
        for (f in flyers) if (f.active) {
            if (f.delay > 0f) { f.delay -= dt; continue }
            f.t += dt / f.dur
            if (f.t >= 1f) { f.active = false; onArrive(f) }
        }
        for (p in popups) if (p.active) { p.t += dt; if (p.t >= p.dur) p.active = false }
        for (r in rings) if (r.active) { r.t += dt; if (r.t >= r.dur) r.active = false }
        if (banners.isNotEmpty()) { val b = banners[0]; b.t += dt; if (b.t >= b.dur) banners.removeAt(0) }
        // toasts wait while a banner is on screen, then play one after another
        if (toasts.isNotEmpty() && banners.isEmpty()) { val o = toasts[0]; o.t += dt; if (o.t >= o.dur) toasts.removeAt(0) }
    }

    fun clear() {
        for (p in parts) p.active = false
        for (f in flyers) f.active = false
        for (p in popups) p.active = false
        for (r in rings) r.active = false
        banners.clear()
        toasts.clear()
    }
}
