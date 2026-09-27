package com.blocktower.escape.core

/**
 * Platform-neutral drawing API used by the game core.
 * The Android implementation (AndroidGfx) maps these calls onto android.graphics.Canvas.
 * Colours are ARGB ints (0xAARRGGBB).
 */
class Img(val w: Int, val h: Int, val native: Any)

/** A vector path recorded in local coordinates; the platform caches its native form. */
class VPath {
    val ops = ArrayList<Float>(32)
    @JvmField var native: Any? = null

    fun moveTo(x: Float, y: Float): VPath { ops.add(0f); ops.add(x); ops.add(y); return this }
    fun lineTo(x: Float, y: Float): VPath { ops.add(1f); ops.add(x); ops.add(y); return this }
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): VPath { ops.add(2f); ops.add(cx); ops.add(cy); ops.add(x); ops.add(y); return this }
    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float): VPath {
        ops.add(3f); ops.add(c1x); ops.add(c1y); ops.add(c2x); ops.add(c2y); ops.add(x); ops.add(y); return this
    }
    fun close(): VPath { ops.add(4f); return this }
    fun circle(cx: Float, cy: Float, r: Float): VPath { ops.add(5f); ops.add(cx); ops.add(cy); ops.add(r); return this }
    fun oval(l: Float, t: Float, r: Float, b: Float): VPath { ops.add(6f); ops.add(l); ops.add(t); ops.add(r); ops.add(b); return this }
    fun roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float): VPath {
        ops.add(7f); ops.add(l); ops.add(t); ops.add(r); ops.add(b); ops.add(rad); return this
    }
    fun rect(l: Float, t: Float, r: Float, b: Float): VPath { ops.add(8f); ops.add(l); ops.add(t); ops.add(r); ops.add(b); return this }

    fun poly(vararg xy: Float): VPath {
        moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) { lineTo(xy[i], xy[i + 1]); i += 2 }
        return close()
    }
}

sealed class Fill { @JvmField var native: Any? = null }
class Solid(val color: Int) : Fill()
class Linear(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val colors: IntArray, val stops: FloatArray? = null) : Fill()
class Radial(val cx: Float, val cy: Float, val r: Float, val colors: IntArray, val stops: FloatArray? = null) : Fill()

object Align { const val LEFT = 0; const val CENTER = 1; const val RIGHT = 2 }
object Font { const val UI = 0; const val TITLE = 1 }

interface Gfx {
    val width: Int
    val height: Int

    fun save()
    fun restore()
    fun translate(dx: Float, dy: Float)
    fun scale(sx: Float, sy: Float)
    fun rotate(deg: Float)
    fun clipRect(l: Float, t: Float, r: Float, b: Float)
    fun setAdditive(on: Boolean)

    fun image(img: Img, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f)

    /**
     * Draw the sub-rectangle (u0,v0)-(u1,v1) (in image pixels) of [img] mapped with a perspective
     * transform onto the quad q = [x0,y0, x1,y1, x2,y2, x3,y3] where
     * (u0,v0)->p0, (u1,v0)->p1, (u1,v1)->p2, (u0,v1)->p3.
     * Colour: out = src * mul * (1 - addAmt) + addColor * addAmt.
     */
    fun imageQuad(img: Img, u0: Float, v0: Float, u1: Float, v1: Float, q: FloatArray,
                  alpha: Float = 1f, mul: Float = 1f, addColor: Int = 0, addAmt: Float = 0f)

    fun fillPath(p: VPath, f: Fill, alpha: Float = 1f)
    fun strokePath(p: VPath, width: Float, f: Fill, alpha: Float = 1f)

    /** Fill a dynamic polygon of n points (xy interleaved). */
    fun fillPoly(xy: FloatArray, n: Int, color: Int)
    fun fillPolyGradient(xy: FloatArray, n: Int, x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int)
    fun strokePoly(xy: FloatArray, n: Int, width: Float, color: Int, closed: Boolean = true)

    fun fillCircle(cx: Float, cy: Float, r: Float, color: Int)
    fun strokeCircle(cx: Float, cy: Float, r: Float, width: Float, color: Int)
    /** Radial glow: [color] at the centre fading to fully transparent at radius r. */
    fun glow(cx: Float, cy: Float, r: Float, color: Int)
    fun fillRect(l: Float, t: Float, r: Float, b: Float, color: Int)
    fun fillRectGradient(l: Float, t: Float, r: Float, b: Float, c0: Int, c1: Int, vertical: Boolean = true)
    fun fillRoundRect(l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int)
    fun strokeRoundRect(l: Float, t: Float, r: Float, b: Float, rad: Float, width: Float, color: Int)
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int)
    fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, width: Float, color: Int)
    fun pie(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, color: Int)

    fun text(s: String, x: Float, y: Float, size: Float, font: Int, color: Int, align: Int = Align.CENTER,
             strokeW: Float = 0f, strokeColor: Int = 0, alpha: Float = 1f)
    fun textWidth(s: String, size: Float, font: Int): Float
}

/** Services the game needs from the host platform. */
interface Platform {
    fun loadImage(path: String): Img
    /** Load an image and return its pixels as ARGB (row-major) plus width/height. */
    fun loadPixels(path: String): Pixels
    fun createImage(p: Pixels): Img
    fun sound(id: Int, volume: Float = 1f, rate: Float = 1f)
    fun haptic(strong: Boolean)
    /** Small persistent key-value storage (saved progress). */
    fun loadText(key: String): String? = null
    fun saveText(key: String, value: String) {}
}

class Pixels(val w: Int, val h: Int, val argb: IntArray)
