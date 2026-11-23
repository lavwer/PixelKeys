package com.pixelkeys

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/**
 * Третья страница приложения: «Посыпки». Выбираешь сезон (снег, листья, вода, весна),
 * и это бесконечно падает сверху клавиатуры. Сверху живое превью.
 */
class FallPage(
    private val act: Activity,
    private val pal: Palette,
    private val prefs: SharedPreferences,
    private val onChanged: () -> Unit
) {
    private val icons = listOf("❄️", "🍂", "💧", "🌸")
    private val names = listOf("Снег", "Листья", "Вода", "Весна")
    private val notes = listOf(
        "Зима: кристаллы, пушистые хлопья и блёстки",
        "Осень: клёны, берёзовые и дубовые листья",
        "Лето: дождь, капли, пузыри и круги на воде",
        "Весна: сакура, цветы, бабочки и пыльца"
    )

    private var cur = prefs.getInt("fall", 0).coerceIn(0, 4)
    private lateinit var overlay: FallOverlay
    private lateinit var empty: TextView
    private lateinit var offBtn: TextView
    private val cards = ArrayList<LinearLayout>()
    private val nmViews = ArrayList<TextView>()
    private val ntViews = ArrayList<TextView>()

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    private fun round(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    private fun tv(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(act)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        return t
    }

    private fun lp(top: Int = 0, height: Int = LinearLayout.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        p.topMargin = dp(top)
        return p
    }

    private fun ripple(v: View, radiusDp: Int) {
        val mask = GradientDrawable()
        mask.setColor(Color.WHITE)
        mask.cornerRadius = dp(radiusDp).toFloat()
        val tint = (pal.text and 0x00FFFFFF) or (0x1F shl 24)
        v.foreground = RippleDrawable(ColorStateList.valueOf(tint), null, mask)
    }

    /** Рисует макет клавиш внизу превью, чтобы было видно, как падает поверх клавиатуры. */
    private class MockKeys(ctx: android.content.Context, private val pal: Palette) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        override fun onDraw(c: Canvas) {
            val d = resources.displayMetrics.density
            val cols = 10
            val rows = 3
            val gap = 4f * d
            val kw = (width - gap * (cols + 1)) / cols
            val kh = 34f * d
            val top = height - rows * (kh + gap) - gap
            p.style = Paint.Style.FILL
            for (r in 0 until rows) {
                val shift = if (r == 1) kw * 0.5f else 0f
                val n = if (r == 1) cols - 1 else cols
                for (i in 0 until n) {
                    val x = gap + shift + i * (kw + gap)
                    val y = top + r * (kh + gap)
                    p.color = pal.key
                    rect.set(x, y, x + kw, y + kh)
                    c.drawRoundRect(rect, 7f * d, 7f * d, p)
                }
            }
        }
    }

    fun view(): View {
        val sv = ScrollView(act)
        sv.setBackgroundColor(pal.surface)
        sv.isVerticalScrollBarEnabled = false
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(28), dp(16), dp(110))

        val ttl = tv("Посыпки", 46f, pal.text)
        Ui.display(ttl, 46f)
        root.addView(ttl)
        val sub = tv("Что падает сверху клавиатуры. Бесконечно, и каждый раз по-разному.", 14f, pal.hint)
        sub.setPadding(0, dp(4), 0, 0)
        root.addView(sub)

        // живое превью
        val box = FrameLayout(act)
        box.background = Ui.raised(act, pal.bg, pal.night, 48)
        box.elevation = 0f
        box.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, dp(28).toFloat())
            }
        }
        box.clipToOutline = true
        box.addView(MockKeys(act, pal), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        overlay = FallOverlay(act, pal.night)
        box.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        empty = tv("Выбери, что будет падать", 15f, pal.hint, true)
        empty.gravity = Gravity.CENTER
        box.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(110)))
        root.addView(box, lp(18, dp(220)))

        // выбор сезона: четыре карточки 2 x 2
        val grid = LinearLayout(act)
        grid.orientation = LinearLayout.VERTICAL
        for (row in 0..1) {
            val rl = LinearLayout(act)
            rl.orientation = LinearLayout.HORIZONTAL
            for (col in 0..1) {
                val i = row * 2 + col
                val cv = LinearLayout(act)
                cv.orientation = LinearLayout.VERTICAL
                cv.gravity = Gravity.CENTER_HORIZONTAL
                cv.setPadding(dp(12), dp(16), dp(12), dp(14))
                cv.minimumHeight = dp(150)
                val ic = tv(icons[i], 34f, pal.text)
                ic.gravity = Gravity.CENTER
                ic.background = Ui.PolarShape(pal.action, intArrayOf(8, 4, 10, 6)[i], floatArrayOf(0.1f, 0.16f, 0.08f, 0.12f)[i])
                cv.addView(ic, LinearLayout.LayoutParams(dp(68), dp(68)))
                val nm = tv(names[i], 17f, pal.text, true)
                nm.gravity = Gravity.CENTER
                nm.setPadding(0, dp(6), 0, dp(4))
                cv.addView(nm)
                nmViews.add(nm)
                val nt = tv(notes[i], 12f, pal.hint)
                nt.gravity = Gravity.CENTER
                cv.addView(nt)
                ntViews.add(nt)
                cv.setOnClickListener { select(i + 1) }
                ripple(cv, 24)
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (col == 1) p.leftMargin = dp(10)
                rl.addView(cv, p)
                cards.add(cv)
            }
            grid.addView(rl, lp(if (row == 0) 0 else 10))
        }
        root.addView(grid, lp(16))

        offBtn = TextView(act)
        offBtn.textSize = 16f
        offBtn.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        offBtn.gravity = Gravity.CENTER
        offBtn.setPadding(dp(16), 0, dp(16), 0)
        offBtn.setOnClickListener { select(0) }
        ripple(offBtn, 26)
        root.addView(offBtn, lp(14, dp(52)))

        // настройки
        val st = tv("Настройки", 14f, pal.accent, true)
        st.setPadding(dp(8), dp(24), 0, dp(8))
        root.addView(st)
        val c = LinearLayout(act)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.raised(act, pal.container, pal.night, 28)
        c.elevation = 0f
        c.setPadding(dp(18), dp(8), dp(18), dp(8))
        c.addView(slider("Количество", "fallamt", 10, 100, 50, "%"))
        c.addView(divider(), lp(0, dp(1)))
        c.addView(slider("Скорость", "fallspd", 20, 200, 100, "%"))
        c.addView(divider(), lp(0, dp(1)))
        c.addView(slider("Ветер", "fallwind", -100, 100, 0, ""))
        root.addView(c, lp())

        val tip = tv("Падает сверху клавиатуры и поверх мини-клавиатуры в «Управлении». Ветер меняется порывами, поэтому каждый раз всё выглядит иначе.", 12f, pal.hint)
        tip.setPadding(dp(8), dp(14), dp(8), 0)
        root.addView(tip)

        sv.addView(root)
        updateSelection()
        refresh()
        return sv
    }

    private fun divider(): View {
        val v = View(act)
        v.setBackgroundColor(pal.line)
        return v
    }

    private fun slider(title: String, key: String, min: Int, max: Int, def: Int, unit: String): View {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(12), 0, dp(6))
        val head = LinearLayout(act)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(tv(title, 16f, pal.text), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val value = tv("", 15f, pal.accent, true)
        head.addView(value)
        box.addView(head)
        val sb = SeekBar(act)
        sb.max = max - min
        sb.progress = prefs.getInt(key, def).coerceIn(min, max) - min
        sb.progressTintList = ColorStateList.valueOf(pal.action)
        sb.progressBackgroundTintList = ColorStateList.valueOf(pal.tonal)
        sb.thumbTintList = ColorStateList.valueOf(pal.action)
        value.text = (sb.progress + min).toString() + unit
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p + min
                value.text = v.toString() + unit
                if (fromUser) {
                    prefs.edit().putInt(key, v).apply()
                    refresh()
                    onChanged()
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(sb)
        return box
    }

    private fun select(i: Int) {
        cur = i
        prefs.edit().putInt("fall", i).apply()
        updateSelection()
        refresh()
        onChanged()
    }

    private fun updateSelection() {
        for (i in cards.indices) {
            val sel = (i + 1) == cur
            cards[i].background = Ui.blob(act, if (sel) Ui.inkBg(pal.night) else pal.container, intArrayOf(1, 2, 3, 0)[i])
            cards[i].elevation = 0f
            nmViews[i].setTextColor(if (sel) Ui.inkFg(pal.night) else pal.text)
            ntViews[i].setTextColor(if (sel) Ui.mix(Ui.inkFg(pal.night), Ui.inkBg(pal.night), 0.3f) else pal.hint)
        }
        offBtn.text = if (cur == 0) "Посыпки выключены" else "Выключить посыпки"
        offBtn.setTextColor(pal.text)
        offBtn.background = Ui.raised(act, if (cur == 0) pal.container else pal.tonal, pal.night, 26)
        offBtn.elevation = 0f
        empty.visibility = if (cur == 0) View.VISIBLE else View.GONE
    }

    private fun refresh() {
        overlay.configure(cur, prefs.getInt("fallamt", 50), prefs.getInt("fallspd", 100), prefs.getInt("fallwind", 0))
    }
}
