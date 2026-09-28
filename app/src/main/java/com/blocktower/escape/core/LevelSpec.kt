package com.blocktower.escape.core

/** Which art set a level uses (world plate, landmark gate, chaser, checkpoints, block trims). */
object Theme { const val SKY_TOWER = 0; const val VOLCANO = 1; const val SKY_TEMPLE = 2; const val ENCHANTED = 3 }

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
    /** Level 5: the volcanic sky fortress (its own plate, fortress gate, lava sea, golem and runaway relic). */
    val volcano get() = theme == Theme.VOLCANO
    /** Level 6: the sky temple (its own plate, temple gate, cloud sea, stone Guardian, rainbow slides). */
    val temple get() = theme == Theme.SKY_TEMPLE
    /** Level 7: the enchanted sky realm (its own plate, the Celestial Gate, pink cloud sea, the Sorcerer and his minions). */
    val enchanted get() = theme == Theme.ENCHANTED
    /** Levels 6 and 7: floating islands over a sea of clouds (not lava). */
    val skyIsles get() = temple || enchanted
    /**
     * Levels built on their design's picture (5, 6 and 7): the design's sky plate with the destination standing far
     * away in it, the route-progress / score / CHASE & COLLECT panels, and the Guardian running beside the path.
     */
    val plate get() = theme != Theme.SKY_TOWER
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
        newThings = listOf("The Shield tool", "Vanishing and cracked blocks", "Spikes", "Magical wind", "Treasure route"),
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

    /**
     * Level 5: the volcanic sky fortress from the approved Level 5 design (design/level5_volcano_reference.png):
     * a long route over the lava to the portal in the distant fortress, the great block loop, the Runaway Relic
     * and the Guardian chase.
     */
    val level5 = LevelSpec(
        number = 5, name = "Volcano Fortress", theme = Theme.VOLCANO, targetNeed = 12, startTime = 150f,
        startHearts = 3, maxHearts = 3, toolCounts = intArrayOf(3, 2, 3, 2),
        guardName = "GUARDIAN", gateName = "Fortress Portal",
        camDist = 4.6f, camHeight = 3.3f, camPitch = 0.34f, camFocal = 1.0f,
        extraObjective = "Survive the Guardian",
        newThings = listOf("Runaway Relic (bonus)", "The great loop", "Speed pads", "Swinging maces", "Lava Guardian"),
        coinStar = 0.5f, timeStar = 40, rewardCoins = 320, rewardGems = 8,
    ) { Level5.build() }

    /**
     * Level 6: the sky temple from the approved Level 6 design (design/level6_reference.png): a long, hard route of
     * floating islands and waterfalls up to the portal in the distant sky temple, with rainbow water slides to ride,
     * laser gates, spiked blocks, the Runaway Relic and the stone Guardian.
     */
    val level6 = LevelSpec(
        number = 6, name = "Sky Temple", theme = Theme.SKY_TEMPLE, targetNeed = 15, startTime = 175f,
        startHearts = 3, maxHearts = 3, toolCounts = intArrayOf(3, 3, 2, 3),
        guardName = "GUARDIAN", gateName = "Temple Portal",
        camDist = 4.6f, camHeight = 3.3f, camPitch = 0.34f, camFocal = 1.0f,
        extraObjective = "Survive the Guardian",
        newThings = listOf("Rainbow slides", "Laser gates", "Spiked blocks", "Runaway Relic (bonus)", "Stone Guardian"),
        coinStar = 0.5f, timeStar = 40, rewardCoins = 400, rewardGems = 10,
    ) { Level6.build() }

    /**
     * Level 7: the enchanted sky realm from the approved Level 7 design (design/level7_reference.png): the longest,
     * hardest route yet, in nine cinematic phases, over floating islands with waterfalls and witch-hat towers to the
     * Celestial Gate (the golden crystal castle with the star portal) far away in the violet sky, while the hooded
     * Sorcerer sends his minions and creatures after the boy and, halfway, rises in his Wrath; the Golden Sprite to chase,
     * two secret routes to find, and the hourglass among the tools.
     */
    val level7 = LevelSpec(
        number = 7, name = "Enchanted Sky", theme = Theme.ENCHANTED, targetNeed = 20, startTime = 250f,
        startHearts = 3, maxHearts = 3, toolCounts = intArrayOf(3, 3, 2, 3),
        guardName = "", gateName = "Celestial Gate",
        camDist = 4.6f, camHeight = 3.45f, camPitch = 0.36f, camFocal = 1.0f,
        extraObjective = "Survive the Sorcerer's Wrath",
        newThings = listOf("The Sorcerer's Wrath", "Minions, rolling stones and a spirit", "Secret routes and treasure", "The Hourglass slows his magic", "Golden Sprite (bonus)"),
        coinStar = 0.5f, timeStar = 40, rewardCoins = 520, rewardGems = 14,
    ) { Level7.build() }

    /** Levels that exist so far (1..7). */
    val all = listOf(level1, level2, level3, level4, level5, level6, level7)
    val count get() = all.size

    /** Level by number (23, the old number of the Screen 4 level, still means Level 4). */
    fun get(n: Int) = when (n) { 1 -> level1; 2 -> level2; 3 -> level3; 4, 23 -> level4; 5 -> level5; 6 -> level6; else -> level7 }
}
