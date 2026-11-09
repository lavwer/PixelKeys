package com.pixelkeys

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView

/** Одна клавиша: сама рисует себя, ловит нажатие, долгое нажатие, автоповтор и показывает увеличенную букву. */
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
    var preview: Boolean = false      // показывать увеличенную букву над клавишей
    var haptic: Boolean = true
    var scallop: Boolean = false      // форма «волнистый квадрат» (бугорки по краям)
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
    private var popup: PopupWindow? = null
    private var bubble: TextView? = null

    private val longRun = Runnable {
        longDone = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val h = hint
        if (h != null) bubble?.text = h
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
        val r = if (down) 11f * d else 7f * d   // почти квадрат со скруглёнными углами
        val highlighted = active || kind == ACTION

        if (!(kind == FLAT && !down && !active)) {
            bgPaint.color = when {
                down && kind != ACTION -> pal.pressed
                highlighted -> pal.action
                kind == SPECIAL -> pal.special
                else -> pal.key
            }
            if (scallop) drawScallop(canvas, rect, bgPaint) else canvas.drawRoundRect(rect, r, r, bgPaint)
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

    /** Волнистый квадрат: круги по периметру + сплошная середина (как на образце). */
    private fun drawScallop(c: Canvas, rc: RectF, p: Paint) {
        val w = rc.width()
        val h = rc.height()
        val nh = 4                                   // бугорков по высоте
        val r = h / (2f + 1.76f * (nh - 1))          // радиус бугорка
        val nw = maxOf(3, Math.round((w / r - 2f) / 1.76f) + 1)   // бугорков по ширине
        val sx = (w - 2 * r) / (nw - 1)
        val sy = (h - 2 * r) / (nh - 1)
        c.drawRect(rc.left + r, rc.top + r, rc.right - r, rc.bottom - r, p)
        for (i in 0 until nw) {
            val x = rc.left + r + i * sx
            c.drawCircle(x, rc.top + r, r, p)
            c.drawCircle(x, rc.bottom - r, r, p)
        }
        for (j in 1 until nh - 1) {
            val y = rc.top + r + j * sy
            c.drawCircle(rc.left + r, y, r, p)
            c.drawCircle(rc.right - r, y, r, p)
        }
    }

    private fun showBubble() {
        val text = label
        if (!preview || text.isEmpty()) return
        try {
            var p = popup
            if (p == null) {
                val tv = TextView(context)
                tv.gravity = Gravity.CENTER
                tv.textSize = 30f
                tv.setTextColor(pal.text)
                val bg = GradientDrawable()
                bg.setColor(pal.key)
                bg.setStroke(d.toInt().coerceAtLeast(1), pal.pressed)
                bg.cornerRadius = 16f * d
                tv.background = bg
                bubble = tv
                p = PopupWindow(tv, (width + 14 * d).toInt(), (68 * d).toInt())
                p.isTouchable = false
                p.isFocusable = false
                p.isClippingEnabled = false
                p.elevation = 8f * d
                p.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                popup = p
            }
            bubble?.text = text
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            val pw = p.width
            var xo = (width - pw) / 2
            val left = loc[0] + xo
            val sw = resources.displayMetrics.widthPixels
            if (left < 0) xo -= left
            if (left + pw > sw) xo -= (left + pw - sw)
            p.showAsDropDown(this, xo, -(height + p.height + (4 * d).toInt()))
        } catch (e: Exception) {
        }
    }

    private fun hideBubble() {
        try {
            popup?.dismiss()
        } catch (e: Exception) {
        }
    }

    override fun onDetachedFromWindow() {
        hideBubble()
        ui.removeCallbacks(longRun)
        ui.removeCallbacks(repRun)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = true
                longDone = false
                invalidate()
                if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                showBubble()
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
        hideBubble()
        ui.removeCallbacks(longRun)
        ui.removeCallbacks(repRun)
        invalidate()
    }
}
