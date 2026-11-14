package com.sultan.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

class SultanView(context: Context) : View(context) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    var state: State = State.IDLE
        set(value) {
            field = value
            invalidate()
        }

    private val dp = resources.displayMetrics.density
    private val sizePx = (88 * dp).toInt()

    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.62f
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4 * dp
        color = 0xFFFFC107.toInt()
    }

    private var t = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val wave = sin(t * 2 * PI).toFloat()

        val emoji = when (state) {
            State.IDLE -> "😎"
            State.LISTENING -> "🙂"
            State.THINKING -> "🤔"
            State.SPEAKING -> if (t < 0.5f) "😃" else "😮"
        }
        val bob = when (state) {
            State.IDLE -> wave * 3 * dp
            State.THINKING -> wave * 6 * dp
            else -> 0f
        }
        val scale = when (state) {
            State.LISTENING -> 1f + 0.10f * wave
            State.SPEAKING -> 1f + 0.06f * wave
            else -> 1f
        }

        if (state == State.LISTENING) {
            ringPaint.alpha = (150 + 100 * wave).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, sizePx / 2f - 4 * dp, ringPaint)
        }

        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        canvas.drawText(emoji, cx, cy + emojiPaint.textSize * 0.35f + bob, emojiPaint)
        canvas.restore()
    }
}
