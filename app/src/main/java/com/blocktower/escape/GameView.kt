package com.blocktower.escape

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import com.blocktower.escape.core.App
import com.blocktower.escape.core.Key
import kotlin.math.max

/**
 * Hardware-accelerated view that runs the game loop on the UI thread via Choreographer:
 * every vsync it updates the current screen (Splash, Home, Select Level, Gameplay) and redraws.
 * The view is edge-to-edge; the safe area it reports keeps UI clear of the status bar,
 * navigation bar / gesture area, display cutout and rounded corners.
 */
class GameView(context: Context) : View(context), Choreographer.FrameCallback {
    private val platform = AndroidPlatform(context, this)
    private val app = App(platform)
    private val gfx = AndroidGfx(context.assets)
    private var lastNanos = 0L
    private var running = false
    private val safe = FloatArray(4)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    fun resume() {
        if (running) return
        running = true
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
        requestFocus()
        requestApplyInsets()
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        app.pause()
    }

    fun release() {
        pause()
        platform.release()
    }

    fun backPressed() { app.back() }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        safeInsets(insets, safe)
        applySize()
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applySize()
    }

    private fun applySize() {
        if (width <= 0 || height <= 0) return
        app.resize(width, height, resources.displayMetrics.density, safe[0], safe[1], safe[2], safe[3])
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now
        applySize()
        if (running) app.update(dt)
        gfx.begin(canvas, width, height)
        app.render(gfx)
        gfx.end()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                app.touchDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) app.touchMove(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                app.touchUp(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> {
                // gesture taken by the system: release everything without firing buttons
                for (i in 0 until e.pointerCount) app.touchUp(e.getPointerId(i), -1e6f, -1e6f)
            }
        }
        return true
    }

    private fun mapKey(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> Key.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> Key.RIGHT
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> Key.UP
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> Key.DOWN
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A -> Key.JUMP
        KeyEvent.KEYCODE_1 -> Key.T1
        KeyEvent.KEYCODE_2 -> Key.T2
        KeyEvent.KEYCODE_3 -> Key.T3
        KeyEvent.KEYCODE_4 -> Key.T4
        KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_START -> Key.PAUSE
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> Key.ENTER
        else -> 0
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val k = mapKey(keyCode)
        if (k == 0) return false
        if (event.repeatCount == 0) app.key(k, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val k = mapKey(keyCode)
        if (k == 0) return false
        app.key(k, false)
        return true
    }

    companion object {
        /**
         * Safe insets (px) = system bars as if shown (they appear transiently over the immersive game),
         * display cutout, the mandatory gesture area and clearance for rounded display corners.
         */
        @Suppress("DEPRECATION")
        fun safeInsets(wi: WindowInsets, out: FloatArray) {
            var l: Int; var t: Int; var r: Int; var b: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = wi.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val gest = wi.getInsets(WindowInsets.Type.mandatorySystemGestures())
                l = bars.left; t = bars.top; r = bars.right; b = max(bars.bottom, gest.bottom)
            } else {
                l = wi.stableInsetLeft; t = wi.stableInsetTop; r = wi.stableInsetRight; b = wi.stableInsetBottom
                if (Build.VERSION.SDK_INT >= 28) wi.displayCutout?.let { c ->
                    l = max(l, c.safeInsetLeft); t = max(t, c.safeInsetTop); r = max(r, c.safeInsetRight); b = max(b, c.safeInsetBottom)
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                // a UI element tucked into a rounded corner needs ~(1 - 1/sqrt2) * radius of clearance
                fun rad(pos: Int) = wi.getRoundedCorner(pos)?.radius ?: 0
                val k = 0.3f
                val tl = rad(RoundedCorner.POSITION_TOP_LEFT); val tr = rad(RoundedCorner.POSITION_TOP_RIGHT)
                val bl = rad(RoundedCorner.POSITION_BOTTOM_LEFT); val br = rad(RoundedCorner.POSITION_BOTTOM_RIGHT)
                t = max(t, (max(tl, tr) * k).toInt()); b = max(b, (max(bl, br) * k).toInt())
                l = max(l, (max(tl, bl) * k).toInt()); r = max(r, (max(tr, br) * k).toInt())
            }
            out[0] = l.toFloat(); out[1] = t.toFloat(); out[2] = r.toFloat(); out[3] = b.toFloat()
        }
    }
}
