package com.blocktower.escape

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import android.view.View
import com.blocktower.escape.core.Img
import com.blocktower.escape.core.Pixels
import com.blocktower.escape.core.Platform

class AndroidPlatform(private val context: Context, private val view: View) : Platform {
    private val sfx = SoundFx(context)
    private val prefs = context.getSharedPreferences("block_tower_escape", Context.MODE_PRIVATE)
    @Volatile private var soundOn = true
    @Volatile private var hapticsOn = true

    private fun decode(path: String): Bitmap {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
            inPremultiplied = true
        }
        context.assets.open(path).use { stream ->
            return BitmapFactory.decodeStream(stream, null, opts) ?: error("Could not decode asset $path")
        }
    }

    override fun loadImage(path: String): Img {
        val b = decode(path)
        b.prepareToDraw()
        return Img(b.width, b.height, b)
    }

    override fun loadPixels(path: String): Pixels {
        val b = decode(path)
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val p = Pixels(b.width, b.height, px)
        b.recycle()
        return p
    }

    override fun createImage(p: Pixels): Img {
        val b = Bitmap.createBitmap(p.argb, p.w, p.h, Bitmap.Config.ARGB_8888)
        b.prepareToDraw()
        return Img(p.w, p.h, b)
    }

    override fun sound(id: Int, volume: Float, rate: Float) { if (soundOn) sfx.play(id, volume, rate) }

    override fun haptic(strong: Boolean) {
        if (hapticsOn) view.performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun loadInt(key: String, def: Int): Int = prefs.getInt(key, def)
    override fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    override fun setFeedback(sound: Boolean, haptics: Boolean) { soundOn = sound; hapticsOn = haptics }
    override fun exitApp() { (context as? Activity)?.moveTaskToBack(true) }

    fun release() = sfx.release()
}
