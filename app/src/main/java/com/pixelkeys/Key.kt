package com.pixelkeys

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
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
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        // подписи служебных клавиш — средней жирности, как в Material 3
        typeface = pal.typeface(kind == SPECIAL || kind == ACTION)
    }
    private val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val ui = Handler(Looper.getMainLooper())
    private var popup: PopupWindow? = null
    private var bubble: TextView? = null

    private val longRun = Runnable {
        longDone = true
        if (pal.vibrate) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
        val m = pal.gapDp * d
        rect.set(m, m * 1.2f, width - m, height - m * 1.2f)
        val base = pal.radiusPct / 100f * 24f * d      // скругление задаётся в настройках
        val pill = minOf(rect.width(), rect.height()) / 2f
        val r = base + (pill - base) * morph
        val highlighted = active || kind == ACTION

        if (!(kind == FLAT && !down && !active)) {
            bgPaint.color = when {
                down && kind != ACTION -> pal.keyDown
                highlighted -> pal.action
                pal.rainbow && kind == NORMAL -> rainbowColor()
                kind == SPECIAL -> pal.special
                else -> pal.key
            }
            if (kind != ACTION) bgPaint.alpha = pal.keyAlpha
            if (scallop && pal.keyShape == 0) {
                drawScallop(canvas, rect, bgPaint)
            } else {
                // служебные клавиши: «скруглённая» = как буквы, «капсула» = полностью круглые края
                val rr = if (scallop && pal.keyShape == 2) minOf(rect.width(), rect.height()) / 2f else r
                canvas.drawRoundRect(rect, rr, rr, bgPaint)
            }
        }

        drawFx(canvas, r)
        val color = if (highlighted) pal.onAction else pal.text
        fg.color = color
        fg.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSp * pal.textScale, resources.displayMetrics)

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
        if (h != null && pal.showHints) {
            hp.color = pal.hint
            hp.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
            canvas.drawText(h, width - m - 6 * d, m + 14 * d, hp)
        }
    }

    private val scallopPath = Path()

    // эффект после нажатия (рисуется внутри клавиши): 1 = волна, 2 = кольцо, 3 = свечение, 4 = пиксели, 5 = искры
    private var fxT = 1f
    private var fxX = 0f
    private var fxY = 0f
    private var fxAnim: ValueAnimator? = null
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fxPath = Path()
    private val fxAng = FloatArray(10)
    private val fxSpd = FloatArray(10)
    private val fxRnd = java.util.Random()
    private val hsv = FloatArray(3)

    /** Режим разработчика: цвет зависит от положения клавиши, получается радуга по всей клавиатуре. */
    private fun rainbowColor(): Int {
        val p = parent as? View
        val rw = maxOf(1, rootView.width)
        hsv[0] = (((left + width / 2f) * 360f / rw) + (p?.top ?: 0) * 0.35f) % 360f
        hsv[1] = if (pal.night) 0.55f else 0.30f
        hsv[2] = if (pal.night) 0.55f else 0.98f
        return android.graphics.Color.HSVToColor(hsv)
    }

    private fun startFx(x: Float, y: Float) {
        if (pal.effect == 0 || kind == FLAT) return
        fxX = x
        fxY = y
        for (i in 0 until 10) {
            fxAng[i] = fxRnd.nextFloat() * 6.2831855f
            fxSpd[i] = 0.55f + fxRnd.nextFloat() * 0.45f
        }
        fxAnim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = pal.fxDur.toLong()
        a.addUpdateListener {
            fxT = it.animatedValue as Float
            invalidate()
        }
        fxT = 0f
        fxAnim = a
        a.start()
    }

    private fun drawFx(c: Canvas, r: Float) {
        val t = fxT
        if (t >= 1f || pal.effect == 0) return
        val fade = 1f - t
        val k = pal.fxPow / 100f
        c.save()
        fxPath.reset()
        fxPath.addRoundRect(rect, r, r, Path.Direction.CW)
        c.clipPath(fxPath)
        val far = Math.hypot(width.toDouble(), height.toDouble()).toFloat()
        when (pal.effect) {
            1 -> {   // волна
                fxPaint.style = Paint.Style.FILL
                fxPaint.color = pal.action
                fxPaint.alpha = (fade * 140 * k).toInt()
                c.drawCircle(fxX, fxY, t * far, fxPaint)
            }
            2 -> {   // кольцо
                fxPaint.style = Paint.Style.STROKE
                fxPaint.strokeWidth = (1f + 3f * fade) * d
                fxPaint.color = pal.action
                fxPaint.alpha = (fade * 255 * k).toInt()
                c.drawCircle(fxX, fxY, t * far, fxPaint)
            }
            3 -> {   // свечение
                fxPaint.style = Paint.Style.FILL
                fxPaint.color = pal.action
                fxPaint.alpha = (fade * fade * 190 * k).toInt()
                c.drawRect(rect, fxPaint)
            }
            4 -> {   // пиксели
                fxPaint.style = Paint.Style.FILL
                fxPaint.color = pal.action
                fxPaint.alpha = (fade * 255 * k).toInt()
                val s = (2f + 5f * fade) * d
                for (i in 0 until 10) {
                    val dist = t * fxSpd[i] * 36f * d
                    val px = fxX + Math.cos(fxAng[i].toDouble()).toFloat() * dist
                    val py = fxY + Math.sin(fxAng[i].toDouble()).toFloat() * dist
                    c.drawRect(px - s / 2f, py - s / 2f, px + s / 2f, py + s / 2f, fxPaint)
                }
            }
            5 -> {   // искры
                fxPaint.style = Paint.Style.FILL
                val rad = (0.8f + 2.6f * fade) * d
                for (i in 0 until 10) {
                    fxPaint.color = if (i % 2 == 0) pal.action else pal.accent
                    fxPaint.alpha = (fade * 255 * k).toInt()
                    val px = fxX + (fxAng[i] / 6.2831855f - 0.5f) * 44f * d * t
                    val py = fxY - t * fxSpd[i] * 30f * d
                    c.drawCircle(px, py, rad, fxPaint)
                }
            }
            6 -> {   // радуга: волна, которая меняет цвет
                hsv[0] = (t * 360f + fxX * 0.5f) % 360f
                hsv[1] = 0.65f
                hsv[2] = 1f
                fxPaint.style = Paint.Style.FILL
                fxPaint.color = android.graphics.Color.HSVToColor(hsv)
                fxPaint.alpha = (fade * 160 * k).toInt()
                c.drawCircle(fxX, fxY, t * far, fxPaint)
            }
            7 -> {   // конфетти: разноцветные квадраты разлетаются и падают
                fxPaint.style = Paint.Style.FILL
                val s = (3f + 3f * fade) * d
                for (i in 0 until 10) {
                    hsv[0] = i * 36f
                    hsv[1] = 0.7f
                    hsv[2] = 1f
                    fxPaint.color = android.graphics.Color.HSVToColor(hsv)
                    fxPaint.alpha = (fade * 255 * k).toInt()
                    val dist = t * fxSpd[i] * 30f * d
                    val px = fxX + Math.cos(fxAng[i].toDouble()).toFloat() * dist
                    val py = fxY + Math.sin(fxAng[i].toDouble()).toFloat() * dist + t * t * 26f * d
                    c.drawRect(px - s / 2f, py - s / 2f, px + s / 2f, py + s / 2f, fxPaint)
                }
            }
            8 -> {   // неон: контур клавиши вспыхивает
                fxPaint.style = Paint.Style.STROKE
                fxPaint.strokeWidth = (2f + 5f * fade) * d
                fxPaint.color = pal.accent
                fxPaint.alpha = (fade * 255 * k).toInt()
                c.drawRoundRect(rect, r, r, fxPaint)
            }
        }
        c.restore()
    }

    // 0 = обычная форма, 1 = полностью скруглённая «капсула» (клавиша в нажатом состоянии)
    private var morph = 0f
    private var morphAnim: ValueAnimator? = null

    private fun animateMorph(to: Float) {
        morphAnim?.cancel()
        val a = ValueAnimator.ofFloat(morph, to)
        a.duration = if (to > morph) 70L else 130L
        a.addUpdateListener {
            morph = it.animatedValue as Float
            invalidate()
        }
        morphAnim = a
        a.start()
    }

    /** Волнистый квадрат: круги по периметру + сплошная середина. Рисуется одним контуром, поэтому прозрачность ровная. */
    private fun drawScallop(c: Canvas, rc: RectF, p: Paint) {
        val w = rc.width()
        val h = rc.height()
        val nh = 4                                   // бугорков по высоте
        val r = h / (2f + 1.76f * (nh - 1))          // радиус бугорка
        val nw = maxOf(3, Math.round((w / r - 2f) / 1.76f) + 1)   // бугорков по ширине
        val sx = (w - 2 * r) / (nw - 1)
        val sy = (h - 2 * r) / (nh - 1)
        val path = scallopPath
        path.reset()
        path.addRect(rc.left + r, rc.top + r, rc.right - r, rc.bottom - r, Path.Direction.CW)
        for (i in 0 until nw) {
            val x = rc.left + r + i * sx
            path.addCircle(x, rc.top + r, r, Path.Direction.CW)
            path.addCircle(x, rc.bottom - r, r, Path.Direction.CW)
        }
        for (j in 1 until nh - 1) {
            val y = rc.top + r + j * sy
            path.addCircle(rc.left + r, y, r, Path.Direction.CW)
            path.addCircle(rc.right - r, y, r, Path.Direction.CW)
        }
        c.drawPath(path, p)
    }

    private fun playKeySound() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.playSoundEffect(if (repeatable) AudioManager.FX_KEYPRESS_DELETE else AudioManager.FX_KEYPRESS_STANDARD, 0.6f)
        } catch (e: Exception) {
        }
    }

    private fun showBubble() {
        val text = label
        if (!preview || text.isEmpty() || !pal.previewBubble) return
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
        morphAnim?.cancel()
        fxAnim?.cancel()
        fxT = 1f
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
                if (pal.morphOn) animateMorph(1f)
                startFx(e.x, e.y)
                invalidate()
                if (haptic && pal.vibrate) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (haptic && pal.sound) playKeySound()
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
        animateMorph(0f)
        hideBubble()
        ui.removeCallbacks(longRun)
        ui.removeCallbacks(repRun)
        invalidate()
    }
}
