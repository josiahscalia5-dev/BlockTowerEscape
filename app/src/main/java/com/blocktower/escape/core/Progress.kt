package com.blocktower.escape.core

/** One stop on the Select Level path. [playable] = its course is built in this version. */
class LevelInfo(val number: Int, val name: String, val playable: Boolean)

object Levels {
    val all = listOf(
        LevelInfo(1, "Tutorial Adventure", true),
        LevelInfo(2, "Sky Bridge", false),
        LevelInfo(3, "Waterfall Climb", false),
        LevelInfo(4, "Guardian Gate", false),
        LevelInfo(5, "Tower Escape", false),
    )
    const val COUNT = 5
    const val MAX_STARS = COUNT * 3
}

/** Player progress and settings, persisted through [Platform.saveInt]. */
class Progress(private val p: Platform) {
    var coins = p.loadInt("coins", 1250); private set
    var gems = p.loadInt("gems", 50); private set
    var xp = p.loadInt("xp", 0); private set
    var sound = p.loadInt("sound", 1) == 1; private set
    var haptics = p.loadInt("haptics", 1) == 1; private set
    private val stars = IntArray(Levels.COUNT + 1) { if (it == 0) 0 else p.loadInt("stars$it", 0) }

    init { p.setFeedback(sound, haptics) }

    val playerLevel get() = 1 + xp / XP_PER_LEVEL
    val levelXp get() = xp % XP_PER_LEVEL
    val totalStars get() = stars.sum()

    fun stars(level: Int) = if (level in 1..Levels.COUNT) stars[level] else 0
    fun unlocked(level: Int) = level == 1 || (level in 2..Levels.COUNT && stars[level - 1] > 0)
    /** The level the player should play next: the highest unlocked one. */
    fun currentLevel(): Int {
        var c = 1
        for (l in 2..Levels.COUNT) if (unlocked(l)) c = l
        return c
    }

    fun setWallet(c: Int, g: Int) {
        coins = maxOf(0, c); gems = maxOf(0, g)
        p.saveInt("coins", coins); p.saveInt("gems", gems)
    }

    fun levelDone(level: Int, earned: Int) {
        if (level !in 1..Levels.COUNT) return
        if (earned > stars[level]) { stars[level] = earned; p.saveInt("stars$level", earned) }
        xp += 20 + 10 * earned
        p.saveInt("xp", xp)
    }

    fun setSound(on: Boolean) { sound = on; p.saveInt("sound", if (on) 1 else 0); p.setFeedback(sound, haptics) }
    fun setHaptics(on: Boolean) { haptics = on; p.saveInt("haptics", if (on) 1 else 0); p.setFeedback(sound, haptics) }

    companion object { const val XP_PER_LEVEL = 100 }
}
