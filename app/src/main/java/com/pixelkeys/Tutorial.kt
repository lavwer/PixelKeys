package com.pixelkeys

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Затемнение экрана с «окном» вокруг нужной кнопки и пульсирующим кольцом. Касания не пропускает. */
class SpotlightView(ctx: Context, private val pal: Palette) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val cur = RectF()
    private var hole = false
    private var pulse = 0f
    private var anim: ValueAnimator? = null
    private var pulseAnim: ValueAnimator? = null

    init {
        scrim.color = 0xD90B0A10.toInt()
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 3f * d
        ring.color = pal.action
    }

    /** null: окна нет, весь экран затемнён. */
    fun setTarget(r: RectF?) {
        anim?.cancel()
        if (r == null) {
            hole = false
            invalidate()
            return
        }
        val start = if (hole) RectF(cur) else RectF(r.centerX() - 2f, r.centerY() - 2f, r.centerX() + 2f, r.centerY() + 2f)
        hole = true
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 420L
        a.interpolator = OvershootInterpolator(1.1f)
        a.addUpdateListener {
            val t = it.animatedValue as Float
            cur.set(
                start.left + (r.left - start.left) * t,
                start.top + (r.top - start.top) * t,
                start.right + (r.right - start.right) * t,
                start.bottom + (r.bottom - start.bottom) * t
            )
            invalidate()
        }
        anim = a
        a.start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val p = ValueAnimator.ofFloat(0f, 1f)
        p.duration = 1300L
        p.repeatCount = ValueAnimator.INFINITE
        p.addUpdateListener {
            pulse = it.animatedValue as Float
            if (hole) invalidate()
        }
        pulseAnim = p
        p.start()
    }

    override fun onDetachedFromWindow() {
        pulseAnim?.cancel()
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = true

    override fun onDraw(c: Canvas) {
        c.save()
        val rad = if (hole) minOf(cur.height() / 2f, 32f * d) else 0f
        if (hole) {
            path.reset()
            path.addRoundRect(cur, rad, rad, Path.Direction.CW)
            c.clipOutPath(path)
        }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
        c.restore()
        if (hole) {
            ring.alpha = 255
            c.drawRoundRect(cur, rad, rad, ring)
            val grow = pulse * 12f * d
            ring.alpha = ((1f - pulse) * 200).toInt()
            val rr = RectF(cur.left - grow, cur.top - grow, cur.right + grow, cur.bottom + grow)
            c.drawRoundRect(rr, rad + grow, rad + grow, ring)
        }
    }
}

/** Схема клавиатуры: ключевые кнопки по очереди подсвечиваются, снизу подпись. */
class KbDiagram(ctx: Context, private val pal: Palette) : View(ctx) {
    companion object {
        val NAMES = arrayOf(
            "Буфер обмена", "Эмодзи", "Подсказки T9", "Кнопка приложения", "Буквы", "Shift", "Стирание",
            "Цифры и символы", "Запятая и точка", "Язык", "Пробел", "Голос", "Enter"
        )
        val DESCS = arrayOf(
            "Скопированное хранится на телефоне сутки. Нажми на запись, чтобы вставить; нужное можно закрепить.",
            "Смайлы по категориям.",
            "Подсказывает слова и исправляет опечатки. Первая подсказка выделена, её можно нажать.",
            "Открывает настройки Pixel Keys.",
            "Нажми и подержи букву: появится вариант из угла, например ё или ъ.",
            "Заглавные буквы.",
            "Держи, чтобы стирать быстро. Сразу после автоисправления стирание возвращает твоё слово.",
            "Цифры и символы.",
            "Нажми и подержи: получится ! или ?.",
            "Смена языка: русский и английский.",
            "Двойной пробел ставит точку и пробел.",
            "Голосовой ввод прямо в клавиатуре. Режим, место и вид кнопки меняются в настройках.",
            "Отправить сообщение или перейти на новую строку."
        )
    }

    private class K(val rect: RectF, val kind: Int, val label: String, val region: Int)

    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keys = ArrayList<K>()
    private val regionRect = Array(NAMES.size) { RectF() }
    private val regionSet = BooleanArray(NAMES.size)
    private var pulse = 0f
    private var pulseAnim: ValueAnimator? = null
    var region = 0
        set(v) {
            field = v
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 0.64f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        layoutKeys(w.toFloat(), h.toFloat())
    }

    private fun addRow(y: Float, rowH: Float, w: Float, weights: FloatArray, kinds: IntArray, labels: Array<String>, regions: IntArray) {
        val pad = 0.02f * w
        val gap = 0.012f * w
        val total = weights.sum()
        val unit = (w - 2f * pad - gap * (weights.size - 1)) / total
        var x = pad
        for (i in weights.indices) {
            val kw = unit * weights[i]
            val rc = RectF(x, y, x + kw, y + rowH)
            keys.add(K(rc, kinds[i], labels[i], regions[i]))
            val rg = regions[i]
            if (rg >= 0) {
                if (!regionSet[rg]) {
                    regionRect[rg].set(rc)
                    regionSet[rg] = true
                } else {
                    regionRect[rg].union(rc)
                }
            }
            x += kw + gap
        }
    }

    private fun layoutKeys(w: Float, h: Float) {
        keys.clear()
        for (i in regionSet.indices) regionSet[i] = false
        val pad = 0.02f * w
        val gap = 0.012f * w
        val rowH = (h - 2f * pad - 4f * gap) / 5f
        fun y(i: Int) = pad + i * (rowH + gap)
        addRow(y(0), rowH, w, floatArrayOf(1.1f, 1.1f, 6.2f, 1.1f), intArrayOf(3, 3, 4, 3), arrayOf("📋", "😊", "T9", "⚙"), intArrayOf(0, 1, 2, 3))
        addRow(y(1), rowH, w, FloatArray(11) { 1f }, IntArray(11) { 0 }, "йцукенгшщзх".map { it.toString() }.toTypedArray(), IntArray(11) { 4 })
        addRow(y(2), rowH, w, FloatArray(11) { 1f }, IntArray(11) { 0 }, "фывапролджэ".map { it.toString() }.toTypedArray(), IntArray(11) { 4 })
        val l3 = FloatArray(11) { 1f }
        l3[0] = 1.5f
        l3[10] = 1.5f
        val k3 = IntArray(11) { 0 }
        k3[0] = 1
        k3[10] = 1
        val lab3 = Array(11) { "" }
        val cyr = "ячсмитьбю"
        for (i in cyr.indices) lab3[i + 1] = cyr[i].toString()
        lab3[0] = "⇧"
        lab3[10] = "⌫"
        val r3 = IntArray(11) { 4 }
        r3[0] = 5
        r3[10] = 6
        addRow(y(3), rowH, w, l3, k3, lab3, r3)
        addRow(
            y(4), rowH, w, floatArrayOf(1.4f, 1f, 1f, 4.2f, 0.8f, 1f, 1.5f),
            intArrayOf(1, 1, 1, 0, 1, 1, 2), arrayOf("?123", ",", "🌐", "Русский", "🎤", ".", "↵"),
            intArrayOf(7, 8, 9, 10, 11, -1, 12)
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1100L
        a.repeatCount = ValueAnimator.INFINITE
        a.addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
        pulseAnim = a
        a.start()
    }

    override fun onDetachedFromWindow() {
        pulseAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = pal.bg
        val bgR = RectF(0f, 0f, w, h)
        c.drawRoundRect(bgR, 22f * d, 22f * d, p)
        val rowH = (h - 2f * 0.02f * w - 4f * 0.012f * w) / 5f
        p.textAlign = Paint.Align.CENTER
        for (k in keys) {
            val on = k.region == region
            val alpha = if (on) 255 else 110
            if (k.kind != 3) {
                p.style = Paint.Style.FILL
                p.color = when (k.kind) {
                    1 -> pal.special
                    2 -> pal.action
                    4 -> pal.tonal
                    else -> pal.key
                }
                p.alpha = alpha
                val rad = if (k.kind == 4) k.rect.height() / 2f else k.rect.height() * 0.28f
                c.drawRoundRect(k.rect, rad, rad, p)
            }
            p.color = if (k.kind == 2) pal.onAction else pal.text
            p.alpha = alpha
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.textSize = rowH * (if (k.label.length > 2) 0.32f else 0.46f)
            val ty = k.rect.centerY() - (p.descent() + p.ascent()) / 2f
            c.drawText(k.label, k.rect.centerX(), ty, p)
        }
        if (regionSet[region]) {
            val r = regionRect[region]
            val grow = (3f + 6f * pulse) * d
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.5f * d
            p.color = pal.action
            p.alpha = (255 * (1f - pulse * 0.6f)).toInt()
            val rr = RectF(r.left - grow, r.top - grow, r.right + grow, r.bottom + grow)
            c.drawRoundRect(rr, rowH * 0.34f + grow, rowH * 0.34f + grow, p)
        }
    }
}

/** Обучение при первом запуске: подсвечивает кнопки приложения и показывает схему клавиатуры. */
class Tutorial(
    private val act: Activity,
    private val pal: Palette,
    private val host: FrameLayout,
    private val steps: List<Step>,
    private val onFinish: () -> Unit
) {
    class Step(
        val title: String,
        val text: String,
        val targets: (() -> List<View>)? = null,
        val diagram: Boolean = false,
        val enter: (() -> Unit)? = null,
        val delayMs: Long = 140L,
        val primary: String? = null
    )

    var isShowing = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var idx = 0
    private lateinit var root: FrameLayout
    private lateinit var spot: SpotlightView
    private lateinit var card: LinearLayout
    private lateinit var dots: LinearLayout
    private lateinit var titleTv: TextView
    private lateinit var textTv: TextView
    private lateinit var diagram: KbDiagram
    private lateinit var diagBox: LinearLayout
    private lateinit var capTitle: TextView
    private lateinit var capDesc: TextView
    private lateinit var skipBtn: TextView
    private lateinit var backBtn: TextView
    private lateinit var nextBtn: TextView

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    private fun tv(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(act)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        return t
    }

    private fun pill(text: String, filled: Boolean, onClick: () -> Unit): TextView {
        val t = tv(text, 16f, if (filled) pal.onAction else pal.text, true)
        t.gravity = Gravity.CENTER
        t.background = Ui.key3d(act, if (filled) pal.action else pal.tonal, pal.night, 26)
        t.setPadding(dp(22), 0, dp(22), 0)
        t.setOnClickListener { onClick() }
        Ui.pressable(t)
        return t
    }

    fun start() {
        root = FrameLayout(act)
        spot = SpotlightView(act, pal)
        root.addView(spot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        buildCard()
        host.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        isShowing = true
        go(0)
    }

    private fun buildCard() {
        card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.background = Ui.blob(act, pal.container, 1)
        card.setPadding(dp(24), dp(22), dp(24), dp(20))
        card.elevation = dp(8).toFloat()

        dots = LinearLayout(act)
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER_VERTICAL
        for (i in steps.indices) {
            val v = View(act)
            dots.addView(v, LinearLayout.LayoutParams(dp(8), dp(8)).also { it.rightMargin = dp(5) })
        }
        card.addView(dots)

        titleTv = tv("", 26f, pal.text)
        Ui.display(titleTv, 26f)
        titleTv.setPadding(0, dp(14), 0, dp(6))
        card.addView(titleTv)

        textTv = tv("", 15f, pal.text)
        textTv.setLineSpacing(0f, 1.1f)
        card.addView(textTv)

        diagBox = LinearLayout(act)
        diagBox.orientation = LinearLayout.VERTICAL
        diagram = KbDiagram(act, pal)
        diagBox.addView(diagram, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        capTitle = tv("", 17f, pal.accent, true)
        capTitle.setPadding(0, dp(12), 0, dp(2))
        diagBox.addView(capTitle)
        capDesc = tv("", 14f, pal.text)
        capDesc.minLines = 3
        diagBox.addView(capDesc)
        card.addView(diagBox)

        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        skipBtn = tv("Пропустить", 14f, pal.hint, true)
        skipBtn.setPadding(0, dp(10), dp(12), dp(10))
        skipBtn.setOnClickListener { finish(true) }
        row.addView(skipBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        backBtn = pill("Назад", false) { if (idx > 0) go(idx - 1) }
        row.addView(backBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)).also { it.rightMargin = dp(8) })
        nextBtn = pill("Далее", true) { if (idx >= steps.size - 1) finish(true) else go(idx + 1) }
        row.addView(nextBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)))
        val rlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rlp.topMargin = dp(18)
        card.addView(row, rlp)

        val clp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        clp.leftMargin = dp(14)
        clp.rightMargin = dp(14)
        clp.bottomMargin = dp(24)
        root.addView(card, clp)
    }

    private fun updateDots() {
        for (i in 0 until dots.childCount) {
            val v = dots.getChildAt(i)
            val cur = i == idx
            v.background = Ui.raised(act, if (cur) pal.action else pal.tonal, pal.night, 8)
            val lp = v.layoutParams
            lp.width = if (cur) dp(24) else dp(8)
            v.layoutParams = lp
        }
    }

    private fun go(i: Int) {
        idx = i
        val st = steps[i]
        handler.removeCallbacksAndMessages(null)
        titleTv.text = st.title
        textTv.text = st.text
        textTv.visibility = if (st.text.isEmpty()) View.GONE else View.VISIBLE
        diagBox.visibility = if (st.diagram) View.VISIBLE else View.GONE
        nextBtn.text = st.primary ?: (if (i == steps.size - 1) "Готово" else "Далее")
        backBtn.visibility = if (i == 0) View.INVISIBLE else View.VISIBLE
        skipBtn.visibility = if (i == steps.size - 1) View.INVISIBLE else View.VISIBLE
        updateDots()
        st.enter?.invoke()
        card.animate().alpha(0f).setDuration(90L).start()
        handler.postDelayed({ place(st) }, st.delayMs)
        if (st.diagram) startDiagram()
    }

    private fun startDiagram() {
        diagram.region = 0
        capTitle.text = KbDiagram.NAMES[0]
        capDesc.text = KbDiagram.DESCS[0]
        val tick = object : Runnable {
            override fun run() {
                val n = (diagram.region + 1) % KbDiagram.NAMES.size
                diagram.region = n
                capTitle.text = KbDiagram.NAMES[n]
                capDesc.text = KbDiagram.DESCS[n]
                handler.postDelayed(this, 2600L)
            }
        }
        handler.postDelayed(tick, 2600L)
    }

    private fun place(st: Step) {
        var rect: RectF? = null
        val views = st.targets?.invoke() ?: emptyList()
        val oloc = IntArray(2)
        root.getLocationInWindow(oloc)
        for (v in views) {
            val loc = IntArray(2)
            v.getLocationInWindow(loc)
            val r = RectF(
                (loc[0] - oloc[0]).toFloat(), (loc[1] - oloc[1]).toFloat(),
                (loc[0] - oloc[0] + v.width).toFloat(), (loc[1] - oloc[1] + v.height).toFloat()
            )
            if (rect == null) rect = r else rect.union(r)
        }
        if (rect != null) rect.inset(-dp(10).toFloat(), -dp(10).toFloat())

        // высоту карточки считаем заранее: она должна встать туда, где больше места, и не перекрыть подсвеченное
        val rootH = root.height
        card.measure(
            View.MeasureSpec.makeMeasureSpec(root.width - dp(28), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(rootH, View.MeasureSpec.AT_MOST)
        )
        val ch = card.measuredHeight
        val gap = dp(12)
        val topM = dp(36)
        val botM = dp(24)
        var atTop = false
        if (rect != null) {
            val spaceBelow = rootH - rect.bottom - botM
            val spaceAbove = rect.top - topM
            atTop = if (spaceBelow >= ch + gap) false else if (spaceAbove >= ch + gap) true else spaceAbove > spaceBelow
            if (atTop) {
                val cardBottom = topM + ch
                if (rect.top < cardBottom + gap) rect.top = cardBottom + gap
            } else {
                val cardTop = rootH - botM - ch
                if (rect.bottom > cardTop - gap) rect.bottom = cardTop - gap
            }
            // если высокий блок почти целиком под карточкой, окно не рисуем
            if (rect.height() < dp(48) || rect.top >= rect.bottom) rect = null
        }
        spot.setTarget(rect)
        val lp = card.layoutParams as FrameLayout.LayoutParams
        lp.gravity = if (rect == null) Gravity.CENTER_VERTICAL else if (atTop) Gravity.TOP else Gravity.BOTTOM
        lp.topMargin = topM
        lp.bottomMargin = botM
        card.layoutParams = lp
        card.alpha = 0f
        card.translationY = (if (atTop) -dp(22) else dp(22)).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(340L)
            .setInterpolator(OvershootInterpolator(1.3f)).start()
    }

    fun finish(done: Boolean) {
        if (!isShowing) return
        isShowing = false
        handler.removeCallbacksAndMessages(null)
        host.removeView(root)
        if (done) onFinish()
    }
}
