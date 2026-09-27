package com.blocktower.escape.sim

import com.blocktower.escape.core.Safe

/** Layout audit for the menu screens: everything inside the safe area, nothing overlapping, touch sizes. */
object Layout {
    /**
     * Problems with a screen's element rectangles ([left, top, right, bottom], screen pixels): an element outside the
     * safe area (except those in [artwork], which may run to the screen edge), or two elements overlapping (unless
     * [overlapping] allows that pair, like the painted level blocks seen in perspective).
     */
    fun problems(screen: String, rects: List<Pair<String, FloatArray>>, safe: Safe, artwork: Set<String>,
                 overlapping: (String, String) -> Boolean = { _, _ -> false }): List<String> {
        val out = ArrayList<String>()
        fun fmt(r: FloatArray) = r.joinToString(",") { "%.0f".format(it) }
        for ((i, p) in rects.withIndex()) {
            val (name, r) = p
            val inside = if (name in artwork) r[0] >= -0.5f - r[2] && r[2] <= safe.w + 0.5f && r[1] >= safe.t - 0.5f && r[3] <= safe.b + 0.5f
                         else r[0] >= safe.l - 0.5f && r[2] <= safe.r + 0.5f && r[1] >= safe.t - 0.5f && r[3] <= safe.b + 0.5f
            if (!inside) out.add("$screen: $name outside the safe area [${fmt(r)}] safe [${"%.0f,%.0f,%.0f,%.0f".format(safe.l, safe.t, safe.r, safe.b)}]")
            for (j in i + 1 until rects.size) {
                val (n2, q) = rects[j]
                if (overlapping(name, n2)) continue
                if (r[0] < q[2] - 0.5f && q[0] < r[2] - 0.5f && r[1] < q[3] - 0.5f && q[1] < r[3] - 0.5f) out.add("$screen: $name overlaps $n2")
            }
        }
        return out
    }
}
