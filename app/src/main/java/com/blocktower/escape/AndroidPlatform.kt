package com.blocktower.escape

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
    private val musicPlayer = MusicPlayer()

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
        // mipmaps: drawn smaller than its size (a far block, the sky on a small screen, the distant temple) it is
        // filtered from a matching smaller copy, so it stays smooth and clean instead of shimmering or aliasing
        b.setHasMipMap(true)
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
        b.setHasMipMap(true)
        b.prepareToDraw()
        return Img(p.w, p.h, b)
    }

    override fun sound(id: Int, volume: Float, rate: Float) = sfx.play(id, volume, rate)
    override fun music(track: Int, intensity: Float, volume: Float) = musicPlayer.set(track, intensity, volume)
    /** The app went to the background / came back: the music stops and starts with it. */
    fun pauseAudio() = musicPlayer.pause()
    fun resumeAudio() = musicPlayer.resume()

    override fun haptic(strong: Boolean) {
        view.performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private val prefs = context.getSharedPreferences("block_tower_escape", Context.MODE_PRIVATE)
    override fun loadText(key: String): String? = prefs.getString(key, null)
    override fun saveText(key: String, value: String) { prefs.edit().putString(key, value).apply() }

    fun release() { sfx.release(); musicPlayer.release() }
}
