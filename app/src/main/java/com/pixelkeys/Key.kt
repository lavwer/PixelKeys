package com.pixelkeys

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/** Одна клавиша: сама рисует себя, ловит нажатие, долгое нажатие и автоповтор. */
class Key(ctx: Context, private val pal: Palette, private val kind: Int = 0) : View(ctx) {

    companion object {
        const val NORMAL = 0
        const val SPECIAL = 1
        const val ACTION = 2
        const val FLAT = 3
    }

    var label: String = ""
        set(v) { field = v; invalidate() }
    var hint: String? = null
        set(v) { field = v; invalidate() }
    var icon: Drawable? = null
        set(v) { field = v; invalidate() }
    var active: Boolean = false
        set(v) { field = v; invalidate() }
    var weight: Float = 1f
    var textSp: Float = 22f
    var repeatable: Boolean = false
    var onTap: () -> Unit = {}
    var onLong: (() -> Unit)? = null

    private var down = false
    private var longDone = false
    private val d = resources.displayMetrics.density
    private val rect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val ui = Handler(Looper.getMainLooper())

    private val longRun = Runnable {
        longDone = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLong?.invoke()
    }
    private val repRun = object : Runnable {
        override fun run() {
            onTap()
            ui.postDelayed(this, 45)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val m = 3f * d
        rect.set(m, m * 1.2f, width - m, height - m * 1.2f)
        val r = if (down) 24f * d else 12f * d
        val highlighted = active || kind == ACTION

        if (!(kind == FLAT && !down && !active)) {
            bgPaint.color = when {
                down && kind != ACTION -> pal.pressed
                highlighted -> pal.action
                kind == SPECIAL -> pal.special
                else -> pal.key
            }
            canvas.drawRoundRect(rect, r, r, bgPaint)
        }

        val color = if (highlighted) pal.onAction else pal.text
        fg.color = color
        fg.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSp, resources.displayMetrics)

        val ic = icon
        if (ic != null) {
            val s = (24 * d).toInt()
            val l = (width - s) / 2
            val t = (height - s) / 2
            ic.setBounds(l, t, l + s, t + s)
            ic.setTint(color)
            ic.draw(canvas)
        } else if (label.isNotEmpty()) {
            val y = height / 2f - (fg.descent() + fg.ascent()) / 2f
            canvas.drawText(label, width / 2f, y, fg)
        }

        val h = hint
        if (h != null) {
            hp.color = pal.hint
            hp.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
            canvas.drawText(h, width - m - 6 * d, m + 14 * d, hp)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = true
                longDone = false
                invalidate()
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (repeatable) {
                    onTap()
                    ui.postDelayed(repRun, 380)
                } else if (onLong != null) {
                    ui.postDelayed(longRun, 350)
                }
            }
            MotionEvent.ACTION_UP -> {
                val inside = e.x >= 0 && e.x <= width && e.y >= 0 && e.y <= height
                release()
                if (!repeatable && !longDone && inside) onTap()
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    private fun release() {
        down = false
        ui.removeCallbacks(longRun)
        ui.removeCallbacks(repRun)
        invalidate()
    }
}
