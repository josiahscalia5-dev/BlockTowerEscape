package com.blocktower.escape.core

/**
 * Artwork for the Splash, Home and Select Level screens, taken from the approved reference
 * screens. [MenuArt] (what the splash needs) loads synchronously; [MapArt] loads in the
 * background while the splash is showing.
 */
class MenuArt(p: Platform) {
    /** Shared floating-island world (castle, portal, block stairs). */
    val world = p.loadImage("menu/world.jpg")
    val logo = p.loadImage("menu/logo.png")
    /** The hero boy, running (front view) - same boy as the back view on the level path. */
    val boyRun = p.loadImage("menu/boy_run.png")
    val guardian = p.loadImage("menu/guardian.png")
}

class MapArt(p: Platform) {
    val map = p.loadImage("menu/map.jpg")
    val sign = p.loadImage("menu/sign.png")
    val boyBack = p.loadImage("menu/boy_back.png")
    val avatar = p.loadImage("menu/avatar.png")
    val icDaily = p.loadImage("menu/ic_daily.png")
    val icMissions = p.loadImage("menu/ic_missions.png")
    val icVault = p.loadImage("menu/ic_vault.png")
    val icGear = p.loadImage("menu/ic_gear.png")
    /** Same coin / gem icons as the gameplay HUD. */
    val coin = p.loadImage("img/coin_icon.png")
    val gem = p.loadImage("img/gem.png")
    val swirl = p.loadImage("emb/portal_swirl.png")
}

/** Geometry of menu/world.jpg in its native units (image width = [W]). */
object WorldPlate {
    const val W = 484f
    const val H = 1184f
    const val PORTAL_X = 390f
    const val PORTAL_Y = 638f

    /** Hero group, measured on the reference splash with the boy's height as the unit. */
    const val BOY_ASPECT = 1000f / 1132f
    const val GUARD_W = 0.7247f
    const val GUARD_H = GUARD_W * 668f / 820f
    /** Guardian top / left relative to the boy's top-left. */
    const val GUARD_DY = -0.341f
    const val GUARD_DX = -0.105f
    const val GROUP_H = 1f - GUARD_DY
    const val GROUP_W = 0.989f
}

/**
 * Geometry of menu/map.jpg (the Select Level path) in its native units. The approved artwork is
 * the core (x in [CORE_L, CORE_L + CORE_W]); the side strips are extra scenery so wide screens
 * (foldables, tablets, 16:9) can show the whole path without bars. Node coordinates are core-relative.
 */
object MapPlate {
    const val W = 707f
    const val H = 1253f
    const val CORE_L = 112f
    const val CORE_W = 483f
    const val PORTAL_X = 405f
    const val PORTAL_Y = 445f

    /**
     * Painted level blocks: box (l, t, r, b), scale (block width / level-1 width),
     * number centre, stars centre, lock (top face) centre, top-face centre (where the boy stands),
     * label anchor and label alignment (-1 right-aligned to the left, 0 centred below, 1 left-aligned to the right).
     */
    class Node(val l: Float, val t: Float, val r: Float, val b: Float, val s: Float,
               val numX: Float, val numY: Float, val starY: Float, val lockX: Float, val lockY: Float,
               val topX: Float, val topY: Float, val labX: Float, val labY: Float, val labAlign: Int)

    val nodes = arrayOf(
        Node(115f, 937f, 315f, 1080f, 1.00f, 215f, 1005f, 1049f, 215f, 953f, 215f, 952f, 219f, 1083f, 0),
        // levels 2-5: the boy stands on the side of the top face away from the next block up
        Node(245f, 713f, 390f, 817f, 0.725f, 316f, 772f, 800f, 313f, 731f, 352f, 725f, 318f, 832f, 0),
        Node(213f, 638f, 330f, 723f, 0.585f, 272f, 681f, 705f, 272f, 650f, 300f, 646f, 207f, 688f, -1),
        Node(145f, 575f, 247f, 653f, 0.51f, 196f, 611f, 636f, 196f, 584f, 172f, 580f, 139f, 614f, -1),
        Node(181f, 510f, 270f, 577f, 0.445f, 225f, 541f, 563f, 225f, 517f, 205f, 514f, 277f, 546f, 1),
    )
    /** The next block after level 5 on the path ("SOON"), and the far decorative blocks. */
    val soon = floatArrayOf(258f, 430f, 330f, 490f)
    val farLocks = floatArrayOf(254f, 404f, 336f, 394f, 380f, 372f)
    /** Height of the back-view boy on the level-1 block (native units). */
    const val BOY_H = 170f
}
