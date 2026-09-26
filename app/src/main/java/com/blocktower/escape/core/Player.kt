package com.blocktower.escape.core

object PS { const val NORMAL = 0; const val RESCUE_FALL = 1; const val RESCUE_RIDE = 2; const val PORTAL = 3; const val CAUGHT = 4; const val WIN = 5; const val DEAD = 6 }

class Player {
    var x = 0f; var y = 0f; var z = 0.45f
    var vx = 0f; var vy = 0f; var vz = 0f
    var grounded = true
    var ground: Block? = null
    var lastGroundY = 0f
    var coyote = 0f
    var jumpBuffer = 0f
    var jumpHeld = false
    var jumping = false
    var state = PS.NORMAL
    var stateT = 0f

    // animation
    var runPhase = 0f
    var flip = false
    var squash = 0f        // >0 squash (landing), <0 stretch (jump)
    var lean = 0f
    var invuln = 0f
    var hurtFlash = 0f
    var airTime = 0f
    var portalScale = 1f
    var spin = 0f
    var sortKey = 0f
    var idleT = 0f

    // recovery
    var fallFromY = 0f
    var rideX0 = 0f; var rideY0 = 0f; var rideZ0 = 0f
    var rideX1 = 0f; var rideY1 = 0f; var rideZ1 = 0f
    var rideT = 0f; var rideDur = 1.6f

    val cx get() = x
    val cyMid get() = y + 0.75f

    fun reset(x0: Float, y0: Float, z0: Float) {
        x = x0; y = y0; z = z0; vx = 0f; vy = 0f; vz = 0f
        grounded = true; ground = null; lastGroundY = y0; coyote = 0f; jumpBuffer = 0f; jumping = false
        state = PS.NORMAL; stateT = 0f; squash = 0f; lean = 0f; invuln = 0f; hurtFlash = 0f
        portalScale = 1f; spin = 0f; airTime = 0f
    }
}
