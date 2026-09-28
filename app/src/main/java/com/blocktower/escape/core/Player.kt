package com.blocktower.escape.core

object PS {
    const val NORMAL = 0; const val RESCUE_FALL = 1; const val RESCUE_RIDE = 2; const val CAUGHT = 4; const val WIN = 5; const val DEAD = 6
    /** Running round the great loop (Level 5). */
    const val LOOP = 7
    /** Riding a rainbow slide (Level 6). */
    const val SLIDE = 8
}

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
    /** Speed pad burst left (seconds). */
    var dashT = 0f
    /** On the loop: which loop, how far round (0..1), speed along the track, offset across it, hop in from it. */
    var loop: Loop? = null
    var loopU = 0f; var loopV = 0f; var loopLat = 0f; var loopHop = 0f; var loopHopV = 0f
    /** How the boy is turned on screen (degrees) to stand on the loop's track (or a slide's channel): followed smoothly, never snapped. */
    var loopRot = 0f
    /**
     * On a rainbow slide: which slide, how far down it (s), pace along it, the angle round the channel (th, 0 = its
     * bottom) and how fast that changes, a hop in from its surface, and how hard he leans into the turn (-1..1).
     */
    var slide: Slide? = null
    var slideS = 0f; var slideV = 0f; var slideTh = 0f; var slideThV = 0f; var slideHop = 0f; var slideHopV = 0f; var slideLean = 0f
    /** Dropping into a slide's mouth: where he was relative to the channel, blended away over a moment (no snap). */
    var slideInX = 0f; var slideInY = 0f; var slideInZ = 0f; var slideIn = 0f
    /**
     * Lying on the slide (head first, on his front): [slidePose] 0..1 blends the belly-slide pose in as he dives into
     * the mouth and back out to running after the exit; [slideSy] is how much shorter his lying body looks from the
     * camera than standing; [slideTurn] how far (degrees) the channel ahead swings his head toward one side; [slideBob]
     * a spring that presses him into the water where the slide bottoms out and lifts him over a crest.
     */
    var slidePose = 0f; var slideSy = 1f; var slideTurn = 0f; var slideBob = 0f; var slideBobV = 0f
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
        portalScale = 1f; spin = 0f; airTime = 0f; boostT = 0f; dashT = 0f; runW = 0f; airW = 0f
        loop = null; loopU = 0f; loopV = 0f; loopLat = 0f; loopHop = 0f; loopHopV = 0f; loopRot = 0f
        slide = null; slideS = 0f; slideV = 0f; slideTh = 0f; slideThV = 0f; slideHop = 0f; slideHopV = 0f; slideLean = 0f; slideIn = 0f
        slidePose = 0f; slideSy = 1f; slideTurn = 0f; slideBob = 0f; slideBobV = 0f
        landT = 9f; jumpT = 9f; skidT = 9f; collectT = 9f; castT = 9f; hurtT = 9f; celebrateT = 9f
        safeX = x0; safeY = y0; safeZ = z0; safeBlock = null
    }

    /** Advances the animation timers. */
    fun tickAnim(dt: Float) {
        landT += dt; jumpT += dt; skidT += dt; collectT += dt; castT += dt; hurtT += dt; celebrateT += dt
    }
}
