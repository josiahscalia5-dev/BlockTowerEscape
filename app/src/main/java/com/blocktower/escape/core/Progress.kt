package com.blocktower.escape.core

import kotlin.math.max

/** A mission on the MISSIONS screen: progress comes from the player's saved stats. */
class Mission(val title: String, val goal: Int, val rewardCoins: Int, val rewardGems: Int, val value: (Progress) -> Int)

/** A collectible awarded by finishing a level for the first time (shown in the PRIZE VAULT). */
class Relic(val name: String, val level: Int)

/**
 * Everything saved between sessions: unlocked levels, best stars and scores, the coin/gem wallet,
 * relics, mission and daily-reward state, vault chests and settings.
 */
class Progress(private val p: Platform) {
    var unlocked = 1
    val stars = IntArray(LEVEL_SLOTS)
    val best = IntArray(LEVEL_SLOTS)
    val relics = BooleanArray(LEVEL_SLOTS)
    var coins = START_COINS
    var gems = START_GEMS
    /** Experience from finishing levels: the player's level on the Home screen goes up every [XP_PER_LEVEL]. */
    var xp = 0
    val playerLevel get() = 1 + xp / XP_PER_LEVEL
    val levelXp get() = xp % XP_PER_LEVEL
    // stats (missions)
    var levelsDone = 0; var totalBlue = 0; var totalCoins = 0; var toolsUsed = 0; var chases = 0
    var mysteries = 0; var flawless = 0
    val missionClaimed = BooleanArray(MISSIONS.size)
    // daily rewards: next reward index (0..6) and the local day it was last claimed
    var dailyIndex = 0
    var lastClaimDay = -1
    val chestOpened = BooleanArray(CHESTS.size)
    // settings
    var sound = true
    var music = true
    var vibration = true
    /** Swipe / joystick sensitivity: 0 low, 1 normal, 2 high. */
    var sensitivity = 1
    /** How the boy is moved: 0 swipes (the default), 1 the joystick. */
    var controls = 0

    val totalStars get() = stars.sum()

    fun missionValue(i: Int) = MISSIONS[i].value(this)
    fun missionDone(i: Int) = missionValue(i) >= MISSIONS[i].goal
    fun missionClaimable(i: Int) = missionDone(i) && !missionClaimed[i]
    val anyMissionClaimable get() = MISSIONS.indices.any { missionClaimable(it) }

    // ---- daily rewards
    /** Which reward of the 7-day calendar is offered today (a missed day starts the streak over). */
    fun dailyShownIndex(today: Int): Int = if (lastClaimDay >= 0 && today - lastClaimDay > 1) 0 else dailyIndex
    fun dailyAvailable(today: Int) = lastClaimDay != today
    fun claimDaily(today: Int): Int {
        if (!dailyAvailable(today)) return -1
        val i = dailyShownIndex(today)
        val r = DAILY[i]
        coins += r[0]; gems += r[1]
        dailyIndex = (i + 1) % DAILY.size
        lastClaimDay = today
        save()
        return i
    }

    fun claimMission(i: Int): Boolean {
        if (!missionClaimable(i)) return false
        missionClaimed[i] = true
        coins += MISSIONS[i].rewardCoins; gems += MISSIONS[i].rewardGems
        save(); return true
    }

    fun chestReady(i: Int) = !chestOpened[i] && totalStars >= CHESTS[i][0]
    fun openChest(i: Int): Boolean {
        if (!chestReady(i)) return false
        chestOpened[i] = true; coins += CHESTS[i][1]; gems += CHESTS[i][2]
        save(); return true
    }

    /** Records a finished level. Returns true when it unlocked the next level. */
    fun recordLevel(level: Int, starsGot: Int, score: Int, coinsEarned: Int, gemsEarned: Int, blue: Int, coinPickups: Int,
                    tools: Int, escaped: Boolean, opened: Int, noHeartLost: Boolean): Boolean {
        levelsDone++
        xp += xpFor(starsGot)
        stars[level] = max(stars[level], starsGot)
        best[level] = max(best[level], score)
        relics[level] = true
        coins += coinsEarned; gems += gemsEarned
        totalBlue += blue; totalCoins += coinPickups; toolsUsed += tools; mysteries += opened
        if (escaped) chases++
        if (noHeartLost) flawless++
        val opens = level + 1 > unlocked && level + 1 < LEVEL_SLOTS
        if (opens) unlocked = level + 1
        save()
        return opens
    }

    // ---- storage: one line of key=value pairs
    fun save() {
        val sb = StringBuilder()
        fun put(k: String, v: Any) { sb.append(k).append('=').append(v).append(';') }
        put("v", 1); put("unlocked", unlocked); put("coins", coins); put("gems", gems); put("xp", xp)
        put("stars", stars.joinToString(",")); put("best", best.joinToString(","))
        put("relics", relics.joinToString(",") { if (it) "1" else "0" })
        put("levelsDone", levelsDone); put("totalBlue", totalBlue); put("totalCoins", totalCoins); put("toolsUsed", toolsUsed)
        put("chases", chases); put("mysteries", mysteries); put("flawless", flawless)
        put("missions", missionClaimed.joinToString(",") { if (it) "1" else "0" })
        put("dailyIndex", dailyIndex); put("lastClaimDay", lastClaimDay)
        put("chests", chestOpened.joinToString(",") { if (it) "1" else "0" })
        put("sound", if (sound) 1 else 0); put("music", if (music) 1 else 0); put("vibration", if (vibration) 1 else 0); put("sensitivity", sensitivity); put("controls", controls)
        p.saveText(KEY, sb.toString())
    }

    fun load() {
        val s = p.loadText(KEY) ?: return
        val m = HashMap<String, String>()
        for (kv in s.split(';')) { val i = kv.indexOf('='); if (i > 0) m[kv.substring(0, i)] = kv.substring(i + 1) }
        fun int(k: String, d: Int) = m[k]?.toIntOrNull() ?: d
        fun ints(k: String, a: IntArray) { m[k]?.split(',')?.forEachIndexed { i, v -> if (i < a.size) a[i] = v.toIntOrNull() ?: 0 } }
        fun bools(k: String, a: BooleanArray) { m[k]?.split(',')?.forEachIndexed { i, v -> if (i < a.size) a[i] = v == "1" } }
        unlocked = int("unlocked", 1).coerceIn(1, LEVEL_SLOTS - 1); coins = int("coins", START_COINS); gems = int("gems", START_GEMS)
        xp = int("xp", 0).coerceAtLeast(0)
        ints("stars", stars); ints("best", best); bools("relics", relics)
        levelsDone = int("levelsDone", 0); totalBlue = int("totalBlue", 0); totalCoins = int("totalCoins", 0); toolsUsed = int("toolsUsed", 0)
        chases = int("chases", 0); mysteries = int("mysteries", 0); flawless = int("flawless", 0)
        bools("missions", missionClaimed); dailyIndex = int("dailyIndex", 0); lastClaimDay = int("lastClaimDay", -1)
        bools("chests", chestOpened)
        sound = int("sound", 1) == 1; music = int("music", 1) == 1; vibration = int("vibration", 1) == 1; sensitivity = int("sensitivity", 1).coerceIn(0, 2)
        controls = int("controls", 0).coerceIn(0, 1)
    }

    /** Starts over (settings are kept). */
    fun reset() {
        unlocked = 1; stars.fill(0); best.fill(0); relics.fill(false); coins = START_COINS; gems = START_GEMS; xp = 0
        levelsDone = 0; totalBlue = 0; totalCoins = 0; toolsUsed = 0; chases = 0; mysteries = 0; flawless = 0
        missionClaimed.fill(false); dailyIndex = 0; lastClaimDay = -1; chestOpened.fill(false)
        save()
    }

    companion object {
        const val KEY = "progress"
        const val LEVEL_SLOTS = 11          // levels 1..10 (index 0 unused)
        const val START_COINS = 500
        const val START_GEMS = 50
        const val XP_PER_LEVEL = 100
        /** Experience for finishing a level with [stars] stars (replays count too). */
        fun xpFor(stars: Int) = 30 + stars * 20
        /** Daily calendar: [coins, gems] for days 1..7. */
        val DAILY = arrayOf(intArrayOf(100, 0), intArrayOf(150, 0), intArrayOf(0, 10), intArrayOf(250, 0), intArrayOf(0, 20), intArrayOf(400, 0), intArrayOf(500, 50))
        /** Vault chests: [stars needed, coins, gems]. */
        val CHESTS = arrayOf(intArrayOf(5, 300, 10), intArrayOf(10, 600, 25), intArrayOf(15, 1000, 60))
        val MISSIONS = arrayOf(
            Mission("Complete Level 1", 1, 100, 0) { if (it.stars[1] > 0) 1 else 0 },
            Mission("Collect 20 blue blocks", 20, 150, 0) { it.totalBlue },
            Mission("Pick up 100 coins", 100, 0, 10) { it.totalCoins },
            Mission("Earn 10 stars", 10, 0, 20) { it.totalStars },
            Mission("Use tools 5 times", 5, 150, 0) { it.toolsUsed },
            Mission("Open 5 mystery blocks", 5, 150, 0) { it.mysteries },
            Mission("Finish a level without losing a heart", 1, 200, 0) { it.flawless },
            Mission("Escape a chase", 1, 0, 25) { it.chases },
            Mission("Complete Level 5", 1, 0, 50) { if (it.stars[5] > 0) 1 else 0 },
        )
        val RELICS = arrayOf(Relic("Sky Star", 1), Relic("Magnet Charm", 2), Relic("Crystal Shield", 3), Relic("Guard's Mask", 4), Relic("Temple Idol", 5))
    }
}
