package com.indiavpn.fast.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.indiavpn.fast.R

/** Four ascending bars: all green when the server is online, none lit when offline. */
class SignalBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var level: Int = 0
        set(value) {
            val v = value.coerceIn(0, BARS)
            if (field == v) return
            field = v
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density = resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((22 * density).toInt(), widthMeasureSpec),
            resolveSize((16 * density).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val gap = 2f * density
        val barW = (width - gap * (BARS - 1)) / BARS
        val onColor = ContextCompat.getColor(
            context,
            if (level > 0) R.color.statusOnline else R.color.statusOffline
        )
        val offColor = ContextCompat.getColor(context, R.color.signalOff)
        for (i in 0 until BARS) {
            val h = height * (0.35f + 0.65f * (i / (BARS - 1f)))
            val left = i * (barW + gap)
            rect.set(left, height - h, left + barW, height.toFloat())
            paint.color = if (i < level) onColor else offColor
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint)
        }
    }

    companion object {
        private const val BARS = 4

        /** Online servers always show full green bars; the real ping is never displayed.
         *  Offline (-2) and not-checked-yet (-1) light nothing. */
        fun levelForPing(pingMs: Int): Int = if (pingMs >= 0) BARS else 0

        /** Green when online, red when offline, grey while the first check is still running. */
        fun colorForPing(pingMs: Int): Int = when {
            pingMs >= 0 -> R.color.statusOnline
            pingMs == -2 -> R.color.statusOffline
            else -> R.color.gray
        }
    }
}
