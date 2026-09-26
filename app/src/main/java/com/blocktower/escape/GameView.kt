package com.blocktower.escape

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.blocktower.escape.core.GS
import com.blocktower.escape.core.Game
import com.blocktower.escape.core.Key

/**
 * Hardware-accelerated view that runs the game loop on the UI thread via Choreographer:
 * every vsync it updates the simulation and redraws the frame.
 */
class GameView(context: Context) : View(context), Choreographer.FrameCallback {
    private val platform = AndroidPlatform(context, this)
    private val game = Game(platform)
    private val gfx = AndroidGfx(context.assets)
    private var lastNanos = 0L
    private var running = false

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
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        if (game.state == GS.PLAY && !game.paused) game.togglePause()
    }

    fun release() {
        pause()
        platform.release()
    }

    fun backPressed() { game.togglePause() }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var top = 0f
        var bottom = 0f
        if (Build.VERSION.SDK_INT >= 28) {
            insets.displayCutout?.let { top = it.safeInsetTop.toFloat(); bottom = it.safeInsetBottom.toFloat() }
        }
        game.hud.setInsets(top, bottom)
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        game.hud.layout(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now
        if (running) game.update(dt)
        gfx.begin(canvas, width, height)
        game.render(gfx)
        gfx.end()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                game.touchDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) game.touchMove(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                game.touchUp(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until e.pointerCount) game.touchUp(e.getPointerId(i), e.getX(i), e.getY(i))
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
        if (event.repeatCount == 0) game.onKey(k, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val k = mapKey(keyCode)
        if (k == 0) return false
        game.onKey(k, false)
        return true
    }
}
