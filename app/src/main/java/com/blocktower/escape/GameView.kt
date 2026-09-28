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
 * every vsync it updates the simulation and redraws the frame.
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
        app.density = resources.displayMetrics.density
    }

    fun resume() {
        if (running) return
        running = true
        lastNanos = 0L
        platform.resumeAudio()
        Choreographer.getInstance().postFrameCallback(this)
        requestFocus()
        requestApplyInsets()
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        app.onPause()
        platform.pauseAudio()
    }

    fun release() {
        pause()
        platform.release()
    }

    /** Returns false when back should leave the app (on the Home screen). */
    fun backPressed(): Boolean = app.back()

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    /**
     * The safe area. The game draws edge to edge (behind the hidden system bars and into the camera cut-out); only
     * buttons and text keep inside the safe area: the status and navigation bars as if shown (they slide in over the
     * game on a swipe, in gesture and 3-button navigation alike), the camera cut-out, the gesture-navigation strip
     * (a swipe that starts there goes to the system) and room for rounded display corners.
     */
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        safeInsets(insets, safe)
        app.setInsets(safe[1], safe[3], safe[0], safe[2])
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        app.layout(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now
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
                for (i in 0 until e.pointerCount) app.touchCancel(e.getPointerId(i))
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
        if (event.repeatCount == 0) app.onKey(k, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val k = mapKey(keyCode)
        if (k == 0) return false
        app.onKey(k, false)
        return true
    }

    companion object {
        /** Safe insets in pixels: [left, top, right, bottom]. */
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
                if (Build.VERSION.SDK_INT >= 29) b = max(b, wi.mandatorySystemGestureInsets.bottom)
            }
            if (Build.VERSION.SDK_INT >= 31) {
                // something tucked into a rounded corner needs about (1 - 1/sqrt2) of its radius of clearance
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
