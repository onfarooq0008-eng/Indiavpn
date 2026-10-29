package com.indiavpn.fast.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.indiavpn.fast.R
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Speed-test dial: a 240 degree arc (blue to green) with a needle. [setValue] takes Mbps and moves the
 * needle smoothly. The number itself is an ordinary TextView layered on top in the layout.
 */
class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.divider)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val hubDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.cardBackground)
    }

    private val oval = RectF()
    private val needle = Path()
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    /** 0..1 position of the needle as currently drawn. */
    private var shown = 0f
    private var animator: ValueAnimator? = null

    /** Moves the needle to [mbps]. The dial is logarithmic so both 5 and 500 Mbps read well. */
    fun setValue(mbps: Float, animate: Boolean = true) {
        val target = toProgress(mbps)
        animator?.cancel()
        if (!animate) {
            shown = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun toProgress(mbps: Float): Float =
        (ln(1f + mbps.coerceAtLeast(0f)) / ln(1f + MAX_MBPS)).coerceIn(0f, 1f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = minOf(w, h).toFloat()
        val stroke = size * 0.075f
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        cx = w / 2f
        cy = h / 2f
        radius = size / 2f - stroke
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val colors = intArrayOf(
            ContextCompat.getColor(context, R.color.ringBlue),
            0xFF22B8F0.toInt(),
            ContextCompat.getColor(context, R.color.ringGreen),
            ContextCompat.getColor(context, R.color.ringGreen)
        )
        val sweepEnd = SWEEP / 360f
        val gradient = SweepGradient(cx, cy, colors, floatArrayOf(0f, sweepEnd * 0.45f, sweepEnd, 1f))
        // The gradient starts at 3 o'clock; turn it so it starts where the arc starts.
        gradient.setLocalMatrix(Matrix().apply { setRotate(START, cx, cy) })
        arcPaint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawArc(oval, START, SWEEP, false, trackPaint)
        if (shown > 0.001f) canvas.drawArc(oval, START, SWEEP * shown, false, arcPaint)

        val angle = Math.toRadians((START + SWEEP * shown).toDouble())
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        val len = radius * 0.78f
        val half = 6f * density
        needle.reset()
        needle.moveTo(cx - dy * half, cy + dx * half)
        needle.lineTo(cx + dx * len, cy + dy * len)
        needle.lineTo(cx + dy * half, cy - dx * half)
        needle.close()
        canvas.drawPath(needle, needlePaint)
        canvas.drawCircle(cx, cy, 11f * density, hubPaint)
        canvas.drawCircle(cx, cy, 4f * density, hubDotPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val START = 150f   // degrees, measured clockwise from 3 o'clock
        const val SWEEP = 240f
        const val MAX_MBPS = 500f
    }
}
