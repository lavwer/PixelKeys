package com.pixelkeys

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Готовые сборки клавиатуры и пресеты эффектов. Меняют только оформление и эффекты, режимы работы не трогают. */
object Presets {
    class Preset(
        val id: Int,
        val icon: String,
        val name: String,
        val note: String,
        val colors: IntArray,        // для миниатюры: фон, клавиша, служебная, акцент
        val radiusPct: Int,          // скругление клавиш для миниатюры
        val values: Map<String, Int>,
        val effectOnly: Boolean
    )

    /** Оформление: перед применением сборки эти ключи сбрасываются к значениям по умолчанию. */
    val LOOK = listOf(
        "theme", "radius", "keyalpha", "textsize", "gap", "font", "shape", "morph", "hints", "bubble",
        "bpad", "hpad", "hue", "sat", "fxon", "fx", "sugstyle",
        "ny", "nygar", "nyedge", "nysnow", "nyfx", "nytheme",
        "fall", "fallamt", "fallspd", "fallwind"
    )

    /** Эффекты: пресет эффектов сбрасывает только эти ключи. */
    val EFFECT = listOf("fxon", "fx", "morph", "bubble", "fall", "fallamt", "fallspd", "fallwind")

    private fun c(h: Long) = h.toInt()

    val builds = listOf(
        Preset(
            1, "🟣", "Pixel", "Цвета обоев и чипы подсказок, как на Pixel",
            intArrayOf(c(0xFF1A1B24), c(0xFF3A3D4C), c(0xFF2C2E3C), c(0xFFB8C4FF)), 50,
            mapOf("theme" to 0, "sugstyle" to 1), false
        ),
        Preset(
            2, "⚪", "Минимал", "Светлая и спокойная, без украшений",
            intArrayOf(c(0xFFE8ECF3), c(0xFFFFFFFF), c(0xFFD5DBE8), c(0xFF3D5AFE)), 30,
            mapOf("theme" to 1, "radius" to 30, "shape" to 1, "gap" to 2, "morph" to 1, "hints" to 1), false
        ),
        Preset(
            3, "🌙", "Ночь", "Тёмная, мягкие клавиши и чипы",
            intArrayOf(c(0xFF14161C), c(0xFF2A2E3A), c(0xFF20232C), c(0xFF9FB4FF)), 40,
            mapOf("theme" to 2, "radius" to 40, "shape" to 1, "font" to 1, "sugstyle" to 1), false
        ),
        Preset(
            4, "💜", "Неон", "Яркий фиолетовый со свечением при нажатии",
            intArrayOf(c(0xFF120A24), c(0xFF2B1A52), c(0xFF1C1038), c(0xFFD56BFF)), 55,
            mapOf("theme" to 10, "hue" to 285, "sat" to 100, "radius" to 55, "shape" to 2, "font" to 1, "fxon" to 0, "fx" to 2, "sugstyle" to 1), false
        ),
        Preset(
            5, "💊", "Капсулы", "Круглые клавиши и широкие промежутки",
            intArrayOf(c(0xFF0E2A2B), c(0xFF1F4F50), c(0xFF173B3C), c(0xFF5EEAD4)), 100,
            mapOf("theme" to 5, "radius" to 100, "shape" to 2, "gap" to 5, "font" to 1, "sugstyle" to 1), false
        ),
        Preset(
            6, "🟩", "Терминал", "Квадратные клавиши, жирные буквы, пиксели",
            intArrayOf(c(0xFF0B1F0E), c(0xFF16381B), c(0xFF10280F), c(0xFF7CFC00)), 0,
            mapOf("theme" to 6, "radius" to 0, "shape" to 1, "gap" to 2, "font" to 2, "fxon" to 0, "fx" to 3, "morph" to 1), false
        ),
        Preset(
            7, "🌸", "Сакура", "Розовая, падают лепестки и бабочки",
            intArrayOf(c(0xFFFFF1F5), c(0xFFFFFFFF), c(0xFFF8D3DF), c(0xFFE91E63)), 70,
            mapOf("theme" to 8, "radius" to 70, "sugstyle" to 1, "fall" to 4, "fallamt" to 30, "fallspd" to 80, "fallwind" to 10), false
        ),
        Preset(
            8, "🍂", "Осень", "Тёплые цвета и кружащиеся листья",
            intArrayOf(c(0xFF2B1A10), c(0xFF54321C), c(0xFF3A2214), c(0xFFE8873A)), 45,
            mapOf("theme" to 10, "hue" to 28, "sat" to 85, "radius" to 45, "shape" to 1, "sugstyle" to 1, "fall" to 2, "fallamt" to 35, "fallspd" to 90, "fallwind" to 20), false
        ),
        Preset(
            9, "🌊", "Океан", "Синяя, дождь и круги на воде",
            intArrayOf(c(0xFF0A1E33), c(0xFF17406A), c(0xFF10304F), c(0xFF5BC0FF)), 60,
            mapOf("theme" to 4, "radius" to 60, "shape" to 2, "sugstyle" to 1, "fxon" to 0, "fx" to 1, "fall" to 3, "fallamt" to 35, "fallwind" to 15), false
        ),
        Preset(
            10, "🎄", "Новый год", "Гирлянды, мишура, конфетти и снег",
            intArrayOf(c(0xFF0B2B20), c(0xFF1E4D3A), c(0xFF6E1B2B), c(0xFFFFC93C)), 50,
            mapOf("ny" to 0, "nygar" to 0, "nyedge" to 0, "nysnow" to 0, "nytheme" to 0, "nyfx" to 1, "sugstyle" to 1), false
        )
    )

    val effects = listOf(
        Preset(
            11, "✨", "Искры и снег", "Искры при каждом нажатии и лёгкий снегопад",
            intArrayOf(), 0,
            mapOf("fxon" to 0, "fx" to 4, "fall" to 1, "fallamt" to 40, "fallspd" to 90, "fallwind" to 10), true
        ),
        Preset(
            12, "🍃", "Тишина", "Без эффектов и анимаций, экономит батарею",
            intArrayOf(), 0,
            mapOf("morph" to 1, "bubble" to 1), true
        )
    )

    fun apply(prefs: SharedPreferences, p: Preset) {
        val ed = prefs.edit()
        for (k in if (p.effectOnly) EFFECT else LOOK) ed.remove(k)
        for ((k, v) in p.values) ed.putInt(k, v)
        ed.apply()
    }

    /** Пресет активен, если все его ключи совпадают с текущими настройками. */
    fun isActive(prefs: SharedPreferences, p: Preset): Boolean {
        for (k in if (p.effectOnly) EFFECT else LOOK) {
            val want = p.values[k]
            if (want == null) {
                if (prefs.contains(k)) return false
            } else if (!prefs.contains(k) || prefs.getInt(k, -1) != want) {
                return false
            }
        }
        return true
    }
}

/** Четвёртая страница приложения: «Пресеты». */
class PresetsPage(
    private val act: Activity,
    private val pal: Palette,
    private val prefs: SharedPreferences,
    private val onApplied: () -> Unit
) {
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

    /** Миниатюра клавиатуры в цветах и скруглении пресета. */
    private class MiniKb(ctx: Context, private val col: IntArray, private val radiusPct: Int) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val d = resources.displayMetrics.density
            val w = width.toFloat()
            val h = height.toFloat()
            p.style = Paint.Style.FILL
            p.color = col[0]
            r.set(0f, 0f, w, h)
            c.drawRoundRect(r, 14f * d, 14f * d, p)
            val pad = 8f * d
            val gap = 3f * d
            val barH = 9f * d
            p.color = col[2]
            r.set(pad, pad, w - pad, pad + barH)
            c.drawRoundRect(r, barH / 2f, barH / 2f, p)
            val top = pad + barH + gap
            val kh = (h - top - pad - 2f * gap) / 3f
            val kw = (w - 2f * pad - 7f * gap) / 8f
            val rad = maxOf(1.5f * d, radiusPct / 100f * kh / 2f)
            for (row in 0 until 3) {
                val n = if (row == 1) 7 else 8
                val shift = if (row == 1) kw / 2f + gap / 2f else 0f
                for (i in 0 until n) {
                    val x = pad + shift + i * (kw + gap)
                    val y = top + row * (kh + gap)
                    val kc = when {
                        row == 2 && i == n - 1 -> col[3]
                        row == 2 && i == 0 -> col[2]
                        else -> col[1]
                    }
                    p.color = kc
                    r.set(x, y, x + kw, y + kh)
                    c.drawRoundRect(r, rad, rad, p)
                }
            }
        }
    }

    private fun apply(p: Presets.Preset) {
        Presets.apply(prefs, p)
        Toast.makeText(act, "Применено: " + p.name, Toast.LENGTH_SHORT).show()
        onApplied()
    }

    private fun buildCard(p: Presets.Preset): View {
        val active = Presets.isActive(prefs, p)
        val card = LinearLayout(act)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(10), dp(10), dp(10), dp(12))
        card.background = Ui.blob(act, if (active) pal.tonal else pal.container, p.id)
        card.elevation = 0f
        card.addView(MiniKb(act, p.colors, p.radiusPct), lp(0, dp(78)))
        val title = tv(p.icon + "  " + p.name, 16f, pal.text, true)
        title.setPadding(dp(4), dp(10), dp(4), 0)
        card.addView(title)
        val note = tv(p.note, 12f, pal.hint)
        note.setPadding(dp(4), dp(2), dp(4), 0)
        note.minLines = 2
        note.maxLines = 2
        card.addView(note)
        val st = tv(if (active) "✓ Применено" else " ", 12f, pal.accent, true)
        st.setPadding(dp(4), dp(6), dp(4), 0)
        card.addView(st)
        card.setOnClickListener { apply(p) }
        ripple(card, 24)
        return card
    }

    private fun effectCard(p: Presets.Preset): View {
        val active = Presets.isActive(prefs, p)
        val row = LinearLayout(act)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14), dp(12), dp(20), dp(12))
        row.background = Ui.raised(act, if (active) pal.action else pal.tonal, pal.night, 50)
        row.elevation = 0f
        val fg = if (active) pal.onAction else pal.text
        val sub = if (active) pal.onAction else pal.hint
        val ic = tv(p.icon, 26f, pal.text)
        ic.gravity = Gravity.CENTER
        ic.background = Ui.PolarShape(if (active) pal.onAction else pal.action, 8, 0.1f)
        row.addView(ic, LinearLayout.LayoutParams(dp(54), dp(54)))
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), 0, dp(8), 0)
        col.addView(tv(p.name, 17f, fg, true))
        val note = tv(p.note, 12f, sub)
        note.setPadding(0, dp(2), 0, 0)
        col.addView(note)
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(tv(if (active) "✓" else "", 20f, fg, true))
        row.setOnClickListener { apply(p) }
        ripple(row, 24)
        return row
    }

    fun view(): View {
        val sv = ScrollView(act)
        sv.setBackgroundColor(pal.surface)
        sv.isVerticalScrollBarEnabled = false
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(28), dp(16), dp(110))

        val ttl = tv("Пресеты", 46f, pal.text)
        Ui.display(ttl, 46f)
        root.addView(ttl)
        val sub = tv("Готовые сборки клавиатуры и эффектов. Одно нажатие применяет всё.", 14f, pal.hint)
        sub.setPadding(0, dp(4), 0, 0)
        root.addView(sub)

        val t1 = tv("Сборки", 14f, pal.accent, true)
        t1.setPadding(dp(8), dp(22), 0, dp(8))
        root.addView(t1)
        val builds = Presets.builds
        var i = 0
        while (i < builds.size) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in 0..1) {
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                if (j == 1) p.leftMargin = dp(10)
                if (i + j < builds.size) row.addView(buildCard(builds[i + j]), p) else row.addView(View(act), p)
            }
            root.addView(row, lp(if (i == 0) 0 else 10))
            i += 2
        }

        val t2 = tv("Эффекты", 14f, pal.accent, true)
        t2.setPadding(dp(8), dp(24), 0, dp(8))
        root.addView(t2)
        for ((k, p) in Presets.effects.withIndex()) {
            root.addView(effectCard(p), lp(if (k == 0) 0 else 10))
        }

        val tip = tv("Пресет меняет только оформление и эффекты. Режимы работы (T9, автоисправление, вибрация, высота клавиш) остаются как у тебя.", 12f, pal.hint)
        tip.setPadding(dp(8), dp(16), dp(8), 0)
        root.addView(tip)

        sv.addView(root)
        return sv
    }
}
