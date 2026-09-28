package com.blocktower.escape.sim

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.io.OutputStream

/** Pipes rendered frames into ffmpeg, which encodes them as an H.264 MP4 at 30 fps. */
class VideoWriter(file: File, private val w: Int, private val h: Int, ffmpegPath: String, crf: Int = 30) {
    private val proc: Process
    private val out: OutputStream
    private val buf = ByteArray(w * h * 3)
    var frames = 0
        private set

    init {
        val pb = ProcessBuilder(ffmpegPath, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "${w}x$h", "-r", "30", "-i", "-",
            "-c:v", "libx264", "-preset", "medium", "-crf", "$crf", "-pix_fmt", "yuv420p", "-movflags", "+faststart", file.absolutePath)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        proc = pb.start()
        out = proc.outputStream.buffered(1 shl 20)
    }

    fun write(img: BufferedImage) {
        val px = (img.raster.dataBuffer as DataBufferInt).data
        var j = 0
        for (i in 0 until w * h) { val c = px[i]; buf[j++] = (c shr 16).toByte(); buf[j++] = (c shr 8).toByte(); buf[j++] = c.toByte() }
        out.write(buf)
        frames++
    }

    fun close() {
        out.close()
        proc.waitFor()
    }
}
