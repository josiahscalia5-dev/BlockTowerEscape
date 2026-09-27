package com.blocktower.escape.sim

import com.blocktower.escape.core.Img
import com.blocktower.escape.core.Pixels
import com.blocktower.escape.core.Platform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Desktop stand-in for the Android host: loads the same assets, records sounds instead of playing them. */
class SimPlatform(private val assets: File) : Platform {
    val soundCounts = IntArray(128)
    var haptics = 0

    private fun read(path: String): BufferedImage = ImageIO.read(File(assets, path)) ?: error("cannot read $path")

    override fun loadImage(path: String): Img {
        val src = read(path)
        val type = if (src.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val b = BufferedImage(src.width, src.height, type)
        b.createGraphics().apply { drawImage(src, 0, 0, null); dispose() }
        return Img(b.width, b.height, b)
    }

    override fun loadPixels(path: String): Pixels {
        val b = read(path)
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        if (b.type == BufferedImage.TYPE_BYTE_GRAY) {
            // raw grey samples (getRGB would apply a linear->sRGB curve that Android does not)
            val r = b.raster
            for (y in 0 until h) for (x in 0 until w) { val v = r.getSample(x, y, 0); px[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        } else b.getRGB(0, 0, w, h, px, 0, w)
        return Pixels(w, h, px)
    }

    override fun createImage(p: Pixels): Img {
        var opaque = true
        for (c in p.argb) if ((c ushr 24) != 255) { opaque = false; break }
        val b = BufferedImage(p.w, p.h, if (opaque) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB)
        b.setRGB(0, 0, p.w, p.h, p.argb, 0, p.w)
        return Img(p.w, p.h, b)
    }

    override fun sound(id: Int, volume: Float, rate: Float) { if (id in soundCounts.indices) soundCounts[id]++ }

    override fun haptic(strong: Boolean) { haptics++ }

    /** The music the game asked for last (tests). */
    var musicTrack = 0; var musicIntensity = 0f; var musicVolume = 0f
    override fun music(track: Int, intensity: Float, volume: Float) { musicTrack = track; musicIntensity = intensity; musicVolume = volume }

    /** Saved progress lives in memory (or in [saveFile] when set). */
    val store = HashMap<String, String>()
    var saveFile: File? = null
    override fun loadText(key: String): String? = store[key] ?: saveFile?.takeIf { it.exists() }?.let { f -> f.readLines().firstOrNull { it.startsWith("$key\t") }?.substringAfter('\t') }
    override fun saveText(key: String, value: String) { store[key] = value; saveFile?.writeText(store.entries.joinToString("\n") { "${it.key}\t${it.value}" }) }
}
