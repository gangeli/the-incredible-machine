package tim.android

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import tim.core.ui.Game
import tim.core.ui.TouchAction
import tim.core.ui.TouchEvent

/** Renders the game every frame and forwards touches. */
class GameView(context: Context, val game: Game) : View(context) {
    private var lastNanos = 0L
    private var painter: AndroidPainter? = null

    init { isFocusable = true; isClickable = true }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        game.resize(w, h)
        painter = null
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 1.0 / 60 else (now - lastNanos) / 1e9
        lastNanos = now
        game.update(dt)
        val p = painter?.also { it.canvas = canvas } ?: AndroidPainter(canvas, width.toDouble(), height.toDouble()).also { painter = it }
        game.render(p)
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val idx = event.actionIndex
        val action = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchAction.DOWN
            MotionEvent.ACTION_MOVE -> TouchAction.MOVE
            MotionEvent.ACTION_UP -> TouchAction.UP
            MotionEvent.ACTION_CANCEL -> TouchAction.CANCEL
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> return true // one finger at a time
            else -> return true
        }
        // always track the first pointer only
        val pi = event.findPointerIndex(event.getPointerId(0)).let { if (it < 0) idx else it }
        game.touch(TouchEvent(action, event.getX(pi).toDouble(), event.getY(pi).toDouble(), 0))
        return true
    }
}
