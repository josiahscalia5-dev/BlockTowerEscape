package com.blocktower.escape.core

object PS { const val NORMAL = 0; const val RESCUE_FALL = 1; const val RESCUE_RIDE = 2; const val CAUGHT = 4; const val WIN = 5; const val DEAD = 6 }

class Player {
    var x = 0f; var y = 0f; var z = 0.45f
    var vx = 0f; var vy = 0f; var vz = 0f
    var grounded = true
    var ground: Block? = null
    var lastGroundY = 0f
    var coyote = 0f
    var jumpBuffer = 0f
    var jumping = false
    var state = PS.NORMAL
    var stateT = 0f

    // animation
    var runPhase = 0f      // stride cycle, one unit per step (legs swap every step)
    var runW = 0f          // 0 idle .. 1 full run (smoothed)
    var airW = 0f          // 0 grounded .. 1 airborne (smoothed)
    var lean = 0f
    var landT = 9f; var landAmt = 0f
    var jumpT = 9f
    var skidT = 9f; var skidDir = 0f
    var collectT = 9f
    var castT = 9f
    var hurtT = 9f; var hurtDir = 1f
    var celebrateT = 9f
    var invuln = 0f
    var hurtFlash = 0f
    var airTime = 0f
    var boostT = 0f
    var portalScale = 1f
    var spin = 0f
    var sortKey = 0f

    // last position where the player stood safely (fall recovery returns here)
    var safeX = 0f; var safeY = 0f; var safeZ = 0f
    var safeBlock: Block? = null

    // recovery ride
    var fallFromY = 0f
    var rideX0 = 0f; var rideY0 = 0f; var rideZ0 = 0f
    var rideX1 = 0f; var rideY1 = 0f; var rideZ1 = 0f
    var rideT = 0f; var rideDur = 1.6f

    fun reset(x0: Float, y0: Float, z0: Float) {
        x = x0; y = y0; z = z0; vx = 0f; vy = 0f; vz = 0f
        grounded = true; ground = null; lastGroundY = y0; coyote = 0f; jumpBuffer = 0f; jumping = false
        state = PS.NORMAL; stateT = 0f; lean = 0f; invuln = 0f; hurtFlash = 0f
        portalScale = 1f; spin = 0f; airTime = 0f; boostT = 0f; runW = 0f; airW = 0f
        landT = 9f; jumpT = 9f; skidT = 9f; collectT = 9f; castT = 9f; hurtT = 9f; celebrateT = 9f
        safeX = x0; safeY = y0; safeZ = z0; safeBlock = null
    }

    /** Advances the animation timers. */
    fun tickAnim(dt: Float) {
        landT += dt; jumpT += dt; skidT += dt; collectT += dt; castT += dt; hurtT += dt; celebrateT += dt
    }
}
