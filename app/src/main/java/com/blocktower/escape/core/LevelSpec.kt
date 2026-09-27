package com.blocktower.escape.core

/** Which art set a level uses (world plate, landmark gate, chaser, checkpoints, block trims). */
object Theme { const val SKY_TOWER = 0; const val JUNGLE_TEMPLE = 1 }

/**
 * Everything that differs from one level to the next: the numbers on the HUD, the objective, the tools
 * you start with, the chaser's name, the art set and the camera framing. The course itself comes from [build].
 */
class LevelSpec(
    val number: Int,
    val theme: Int,
    val targetNeed: Int,
    val startTime: Float,
    val startCoins: Int,
    val startGems: Int,
    val startHearts: Int,
    val maxHearts: Int,
    val toolCounts: IntArray,
    /** Shown on the DANGER banner, the danger meter and the game-over reason. */
    val guardName: String,
    /** The destination, as the mission card and results call it. */
    val gateName: String,
    /** Base camera: distance behind the boy, height, pitch, focal scale. */
    val camDist: Float, val camHeight: Float, val camPitch: Float, val camFocal: Float,
    /** Where the lens centre sits on screen (fraction of the height), or 0 for the default framing. */
    val camCy: Float = 0f,
    /** How tall the boy is drawn, in blocks. */
    val boyH: Float = 1.95f,
    val build: () -> World,
) {
    val jungle get() = theme == Theme.JUNGLE_TEMPLE
}

object Levels {
    /** Level 23 (Screen 4): the sky tower and the Ancient Gate. */
    val level23 = LevelSpec(
        number = 23, theme = Theme.SKY_TOWER, targetNeed = 12, startTime = 102f,
        startCoins = 4250, startGems = 320, startHearts = 2, maxHearts = 3, toolCounts = intArrayOf(3, 2, 3, 3),
        guardName = "TOWER GUARD", gateName = "Ancient Gate",
        camDist = 4.4f, camHeight = 3.6f, camPitch = 0.30f, camFocal = 1.0f,
    ) { Level.build() }

    /** Level 5: the jungle temple, the Temple Guardian and the golden portal. */
    val level5 = LevelSpec(
        number = 5, theme = Theme.JUNGLE_TEMPLE, targetNeed = 10, startTime = 75f,
        startCoins = 1850, startGems = 220, startHearts = 3, maxHearts = 3, toolCounts = intArrayOf(3, 2, 3, 2),
        guardName = "TEMPLE GUARDIAN", gateName = "Temple Portal",
        // fitted to the Level 5 design: a closer, lower camera looking down on chunky blocks
        camDist = 3.35f, camHeight = 2.4f, camPitch = 0.70f, camFocal = 0.892f, camCy = 0.823f, boyH = 1.75f,
    ) { Level5.build() }

    fun get(n: Int) = if (n == 23) level23 else level5
}
