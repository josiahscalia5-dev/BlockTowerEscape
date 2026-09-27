package com.blocktower.escape.core

/** Which art set a level uses (world plate, landmark gate, chaser, checkpoints, block trims). */
object Theme { const val SKY_TOWER = 0; const val JUNGLE_TEMPLE = 1 }

/**
 * Everything that differs from one level to the next: the numbers on the HUD, the objective, the tools
 * you start with, the chaser's name, the art set and the camera framing. The course itself comes from [build].
 */
class LevelSpec(
    val number: Int,
    /** Shown on the level map and the level card. */
    val name: String,
    val theme: Int,
    val targetNeed: Int,
    val startTime: Float,
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
    /** The big gate from the level's design (visible in the sky from the start); otherwise a block portal arch. */
    val bigGate: Boolean = true,
    /** Falls cost no heart (the tutorial). */
    val gentleFalls: Boolean = false,
    /** An extra objective line ("Survive the chase"). */
    val extraObjective: String = "",
    /** What this level introduces (level card and mission card). */
    val newThings: List<String> = emptyList(),
    /** Star goals: 2 stars = this share of the level's coins; 3 stars = also finish with this many seconds left and no game over. */
    val coinStar: Float = 0.5f,
    val timeStar: Int = 20,
    /** Coins and gems for finishing (plus a bonus per star). */
    val rewardCoins: Int = 200,
    val rewardGems: Int = 5,
    val build: () -> World,
) {
    val jungle get() = theme == Theme.JUNGLE_TEMPLE
    /** Coins needed for the second star. */
    fun coinGoal(total: Int) = kotlin.math.ceil(total * coinStar).toInt()
}

object Levels {
    /** The level where each tool (magnet, shield, lightning, block) becomes available. */
    val toolUnlock = intArrayOf(2, 3, 4, 4)

    val level1 = LevelSpec(
        number = 1, name = "Tutorial Adventure", theme = Theme.SKY_TOWER, targetNeed = 5, startTime = 90f,
        startHearts = 3, maxHearts = 3, toolCounts = intArrayOf(0, 0, 0, 0),
        guardName = "", gateName = "Sky Portal",
        camDist = 4.4f, camHeight = 3.6f, camPitch = 0.30f, camFocal = 1.0f,
        bigGate = false, gentleFalls = true,
        newThings = listOf("Swipe to run and steer", "Tap JUMP to hop gaps"),
        coinStar = 0.5f, timeStar = 45, rewardCoins = 100, rewardGems = 2,
    ) { Levels123.level1() }

    val level2 = LevelSpec(
        number = 2, name = "First Challenge", theme = Theme.SKY_TOWER, targetNeed = 7, startTime = 80f,
        startHearts = 3, maxHearts = 3, toolCounts = intArrayOf(2, 0, 0, 0),
        guardName = "", gateName = "Sky Portal",
        camDist = 4.4f, camHeight = 3.6f, camPitch = 0.30f, camFocal = 1.0f,
        bigGate = false,
        newThings = listOf("The Magnet tool", "Moving blocks", "Mystery blocks"),
        coinStar = 0.5f, timeStar = 40, rewardCoins = 140, rewardGems = 3,
    ) { Levels123.level2() }

    val level3 = LevelSpec(
        number = 3, name = "Mechanics Begin", theme = Theme.SKY_TOWER, targetNeed = 8, startTime = 95f,
        startHearts = 3, maxHearts = 3, toolCounts = intArrayOf(2, 2, 0, 0),
        guardName = "", gateName = "Sky Portal",
        camDist = 4.4f, camHeight = 3.6f, camPitch = 0.30f, camFocal = 1.0f,
        bigGate = false, extraObjective = "Survive the hazards",
        newThings = listOf("The Shield tool", "Vanishing and cracked blocks", "Spikes", "Treasure route"),
        coinStar = 0.55f, timeStar = 45, rewardCoins = 180, rewardGems = 4,
    ) { Levels123.level3() }

    /** Level 4: the approved Screen 4 design (the sky tower and the Ancient Gate). */
    val level4 = LevelSpec(
        number = 4, name = "Tower Escape", theme = Theme.SKY_TOWER, targetNeed = 12, startTime = 102f,
        startHearts = 2, maxHearts = 3, toolCounts = intArrayOf(3, 2, 3, 3),
        guardName = "TOWER GUARD", gateName = "Ancient Gate",
        camDist = 4.4f, camHeight = 3.6f, camPitch = 0.30f, camFocal = 1.0f,
        extraObjective = "Survive the chase",
        newThings = listOf("Lightning and Block tools", "Tower Guard chase", "Rising lava"),
        coinStar = 0.55f, timeStar = 20, rewardCoins = 250, rewardGems = 6,
    ) { Level.build() }

    /** Level 5: the jungle temple, the Temple Guardian and the golden portal. */
    val level5 = LevelSpec(
        number = 5, name = "Jungle Temple", theme = Theme.JUNGLE_TEMPLE, targetNeed = 10, startTime = 75f,
        startHearts = 3, maxHearts = 3, toolCounts = intArrayOf(3, 2, 3, 2),
        guardName = "TEMPLE GUARDIAN", gateName = "Temple Portal",
        // fitted to the Level 5 design: a closer, lower camera looking down on chunky blocks
        camDist = 3.35f, camHeight = 2.4f, camPitch = 0.70f, camFocal = 0.892f, camCy = 0.823f, boyH = 1.75f,
        extraObjective = "Survive the Temple Guardian",
        newThings = listOf("Temple Guardian", "Swinging logs", "Spring buttons"),
        coinStar = 0.55f, timeStar = 15, rewardCoins = 300, rewardGems = 8,
    ) { Level5.build() }

    /** Levels that exist so far (1..5). */
    val all = listOf(level1, level2, level3, level4, level5)
    val count get() = all.size

    /** Level by number (23, the old number of the Screen 4 level, still means Level 4). */
    fun get(n: Int) = when (n) { 1 -> level1; 2 -> level2; 3 -> level3; 4, 23 -> level4; else -> level5 }
}
