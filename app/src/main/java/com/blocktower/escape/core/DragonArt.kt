package com.blocktower.escape.core

/**
 * Cartoon dragon for the DRAGON ATTACK event, drawn with vector paths in a local
 * space roughly 460 x 300 units, facing +x, centred on the body.
 */
object DragonArt {
    private const val OUT = 0xFF2A0638.toInt()

    private val bodyFill = Linear(0f, -60f, 0f, 60f, intArrayOf(0xFFE264F4.toInt(), 0xFFA82BC8.toInt(), 0xFF5E1284.toInt()), floatArrayOf(0f, 0.5f, 1f))
    private val bellyFill = Linear(0f, 0f, 0f, 60f, intArrayOf(0xFFFFD27A.toInt(), 0xFFFF8A2E.toInt()))
    private val wingFarFill = Linear(0f, -200f, 0f, 0f, intArrayOf(0xFF7A2AA8.toInt(), 0xFF4A1070.toInt()))
    private val wingNearFill = Linear(0f, -220f, 0f, 0f, intArrayOf(0xFFB04ADF.toInt(), 0xFF6A1C98.toInt()))
    private val hornFill = Linear(0f, -150f, 0f, -100f, intArrayOf(0xFFFFF6E0.toInt(), 0xFFE8C898.toInt()))
    private val spikeFill = Solid(0xFFFF9A3A.toInt())
    private val outline = Solid(OUT)

    private val tail = VPath().moveTo(-70f, 0f)
        .cubicTo(-130f, -10f, -170f, 30f, -230f, 20f)
        .cubicTo(-250f, 16f, -262f, 8f, -268f, 0f)
        .cubicTo(-240f, 22f, -170f, 50f, -110f, 38f)
        .cubicTo(-90f, 34f, -76f, 26f, -64f, 22f).close()
    private val spade = VPath().moveTo(-262f, 2f).lineTo(-300f, -26f).lineTo(-292f, 8f).lineTo(-306f, 30f).close()
    private val body = VPath().moveTo(-88f, 6f)
        .cubicTo(-92f, -40f, -30f, -62f, 30f, -52f)
        .cubicTo(70f, -44f, 96f, -20f, 92f, 12f)
        .cubicTo(88f, 44f, 40f, 58f, -10f, 56f)
        .cubicTo(-60f, 54f, -86f, 36f, -88f, 6f).close()
    private val belly = VPath().moveTo(-66f, 24f)
        .cubicTo(-40f, 50f, 30f, 54f, 76f, 22f)
        .cubicTo(66f, 44f, 20f, 58f, -14f, 56f)
        .cubicTo(-44f, 54f, -60f, 42f, -66f, 24f).close()
    private val neck = VPath().moveTo(56f, -40f)
        .cubicTo(80f, -70f, 104f, -104f, 136f, -118f)
        .lineTo(158f, -92f)
        .cubicTo(128f, -80f, 104f, -40f, 90f, 4f).close()
    private val head = VPath().moveTo(124f, -118f)
        .cubicTo(140f, -150f, 184f, -150f, 214f, -128f)
        .cubicTo(236f, -118f, 250f, -108f, 252f, -98f)
        .cubicTo(236f, -92f, 206f, -92f, 186f, -94f)
        .cubicTo(166f, -88f, 146f, -84f, 132f, -92f).close()
    private val jaw = VPath().moveTo(150f, -92f)
        .cubicTo(176f, -86f, 206f, -80f, 238f, -70f)
        .cubicTo(214f, -64f, 176f, -66f, 150f, -80f).close()
    private val horn1 = VPath().moveTo(150f, -134f).cubicTo(140f, -160f, 124f, -176f, 104f, -182f).cubicTo(126f, -164f, 136f, -146f, 138f, -126f).close()
    private val horn2 = VPath().moveTo(170f, -140f).cubicTo(166f, -166f, 156f, -186f, 140f, -198f).cubicTo(158f, -176f, 164f, -156f, 160f, -136f).close()
    private val farWing = VPath().moveTo(-10f, -40f)
        .lineTo(-70f, -190f).lineTo(-20f, -150f).lineTo(20f, -206f).lineTo(40f, -150f).lineTo(90f, -180f)
        .cubicTo(80f, -120f, 60f, -70f, 30f, -40f).close()
    private val nearWing = VPath().moveTo(-30f, -30f)
        .lineTo(-120f, -200f).cubicTo(-90f, -176f, -70f, -168f, -52f, -166f)
        .lineTo(-10f, -232f).cubicTo(0f, -200f, 12f, -186f, 26f, -178f)
        .lineTo(84f, -214f).cubicTo(80f, -150f, 56f, -80f, 24f, -30f).close()
    private val leg = VPath().moveTo(-40f, 40f).cubicTo(-50f, 70f, -40f, 86f, -20f, 88f).lineTo(-8f, 82f).cubicTo(-20f, 70f, -22f, 56f, -16f, 44f).close()
    private val leg2 = VPath().moveTo(40f, 40f).cubicTo(34f, 70f, 44f, 84f, 64f, 86f).lineTo(74f, 78f).cubicTo(62f, 68f, 60f, 56f, 62f, 44f).close()
    private val spikes: VPath = VPath().also { p ->
        val pts = floatArrayOf(-200f, 16f, -160f, 14f, -120f, 4f, -70f, -30f, -30f, -52f, 14f, -56f, 56f, -44f, 90f, -84f, 118f, -112f)
        var i = 0
        while (i < pts.size) {
            val x = pts[i]; val y = pts[i + 1]
            p.moveTo(x - 12f, y + 2f).lineTo(x, y - 22f).lineTo(x + 12f, y + 2f).close()
            i += 2
        }
    }
    private val bone = Solid(0xFF3A0C52.toInt())

    fun draw(gr: Gfx, flap: Float, t: Float, breath: Boolean) {
        // far wing (behind the body)
        gr.save(); gr.translate(10f, -40f); gr.scale(1f, 0.35f + 0.65f * (0.5f + 0.5f * flap)); gr.translate(-10f, 40f)
        gr.fillPath(farWing, wingFarFill); gr.strokePath(farWing, 6f, outline)
        gr.restore()
        gr.fillPath(leg, bodyFill); gr.strokePath(leg, 5f, outline)
        gr.fillPath(tail, bodyFill); gr.strokePath(tail, 6f, outline)
        gr.fillPath(spade, spikeFill); gr.strokePath(spade, 5f, outline)
        gr.fillPath(spikes, spikeFill); gr.strokePath(spikes, 4f, outline)
        gr.fillPath(body, bodyFill); gr.strokePath(body, 7f, outline)
        gr.fillPath(belly, bellyFill)
        for (k in 0 until 5) { val x = -48f + k * 26f; gr.line(x, 36f + (if (k == 2) 14f else 8f), x + 6f, 52f, 3f, 0x66A04010) }
        gr.fillPath(leg2, bodyFill); gr.strokePath(leg2, 5f, outline)
        gr.fillPath(neck, bodyFill); gr.strokePath(neck, 6f, outline)
        gr.fillPath(horn2, hornFill); gr.strokePath(horn2, 4f, outline)
        gr.fillPath(jaw, bodyFill); gr.strokePath(jaw, 5f, outline)
        if (breath) {
            gr.setAdditive(true)
            gr.glow(252f, -80f, 70f, 0xAAFF8A20.toInt())
            gr.glow(248f, -80f, 34f, 0xFFFFE070.toInt())
            gr.setAdditive(false)
        }
        gr.fillPath(head, bodyFill); gr.strokePath(head, 6f, outline)
        gr.fillPath(horn1, hornFill); gr.strokePath(horn1, 4f, outline)
        // eye
        gr.fillCircle(186f, -120f, 13f, OUT)
        gr.fillCircle(186f, -120f, 10f, 0xFFFFE24A.toInt())
        gr.fillCircle(189f, -120f, 5f, 0xFF1A0600.toInt())
        gr.fillCircle(183f, -124f, 3f, 0xFFFFFFFF.toInt())
        gr.line(170f, -136f, 200f, -130f, 5f, OUT)
        gr.fillCircle(240f, -104f, 3.5f, OUT)
        // near wing with bones
        gr.save(); gr.translate(-20f, -30f); gr.scale(1f, 0.3f + 0.7f * (0.5f + 0.5f * flap)); gr.translate(20f, 30f)
        gr.fillPath(nearWing, wingNearFill); gr.strokePath(nearWing, 6f, outline)
        gr.line(-24f, -34f, -118f, -196f, 6f, 0xFF3A0C52.toInt())
        gr.line(-24f, -34f, -12f, -226f, 6f, 0xFF3A0C52.toInt())
        gr.line(-24f, -34f, 80f, -208f, 6f, 0xFF3A0C52.toInt())
        gr.restore()
    }
}
