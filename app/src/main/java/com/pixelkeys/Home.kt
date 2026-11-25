package com.pixelkeys

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Контейнер главного экрана: дети размещаются долями ширины и высоты, композиция одинакова на любых экранах. */
class HomeView(ctx: Context) : FrameLayout(ctx) {
    private class Place(val xf: Float, val yf: Float, val wf: Float, val hf: Float, val hOfH: Boolean)

    private val places = HashMap<View, Place>()
    var onSized: ((Int, Int) -> Unit)? = null

    /** x и w в долях ширины, y в долях высоты, h в долях ширины (или высоты, если hOfH). */
    fun put(v: View, xf: Float, yf: Float, wf: Float, hf: Float, hOfH: Boolean = false) {
        places[v] = Place(xf, yf, wf, hf, hOfH)
        addView(v, LayoutParams(1, 1))
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        for ((v, pl) in places) {
            val lp = v.layoutParams as LayoutParams
            lp.width = (pl.wf * w).toInt()
            lp.height = if (pl.hOfH) (pl.hf * h).toInt() else (pl.hf * w).toInt()
            lp.leftMargin = (pl.xf * w).toInt()
            lp.topMargin = (pl.yf * h).toInt()
            v.layoutParams = lp
        }
        onSized?.invoke(w, h)
    }
}

/**
 * Главный экран в стиле референса Material 3 Expressive («Your Mix»): огромное название,
 * большой круг-кнопка, наклонная капсула-«фото» и круглые пузыри-ярлыки по краям.
 */
class HomePage(
    private val act: Activity,
    private val pal: Palette,
    private val prefs: SharedPreferences,
    private val onGo: (Int) -> Unit,
    private val onMenu: () -> Unit
) {
    var circle: View? = null
        private set

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    private fun tv(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(act)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        return t
    }

    /** 0 = клавиатура не включена, 1 = включена, но не выбрана, 2 = выбрана. */
    private fun imeState(): Int {
        return try {
            val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val enabled = imm.enabledInputMethodList.any { it.packageName == act.packageName }
            val cur = Settings.Secure.getString(act.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
            if (cur.startsWith(act.packageName + "/")) 2 else if (enabled) 1 else 0
        } catch (e: Exception) {
            0
        }
    }

    /** Макет клавиш, который торчит из капсулы, как кадр на фото в референсе. */
    private class KeysArt(ctx: Context, private val pal: Palette) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val d = resources.displayMetrics.density
            val cols = 6
            val gap = 5f * d
            val kw = (width - gap * (cols + 1)) / cols
            val kh = kw * 0.95f
            p.style = Paint.Style.FILL
            for (row in 0 until 3) {
                for (i in 0 until cols) {
                    val x = gap + i * (kw + gap)
                    val y = gap + row * (kh + gap)
                    p.color = when {
                        row == 2 && i == cols - 1 -> pal.action
                        row == 2 && i == 0 -> pal.special
                        else -> pal.key
                    }
                    r.set(x, y, x + kw, y + kh)
                    c.drawRoundRect(r, kh * 0.3f, kh * 0.3f, p)
                }
            }
        }
    }

    private fun bubble(home: HomeView, icon: String, label: String, target: Int, xf: Float, yf: Float, wf: Float, bg: Int, fg: Int, bumps: Int, amp: Float) {
        val b = LinearLayout(act)
        b.orientation = LinearLayout.VERTICAL
        b.gravity = Gravity.CENTER
        b.background = Ui.PolarShape(bg, bumps, amp)
        val ic = tv(icon, 28f, fg)
        ic.gravity = Gravity.CENTER
        b.addView(ic)
        val lb = tv(label, 11f, fg, true)
        lb.gravity = Gravity.CENTER
        b.addView(lb)
        b.setOnClickListener { onGo(target) }
        Ui.pressable(b)
        home.put(b, xf, yf, wf, wf)
    }

    private fun roundButton(home: HomeView, text: String, xf: Float, yf: Float, wf: Float, onClick: () -> Unit) {
        val t = tv(text, 20f, pal.text, true)
        t.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(pal.container)
        t.background = g
        t.setOnClickListener { onClick() }
        Ui.pressable(t)
        home.put(t, xf, yf, wf, wf)
    }

    fun view(): View {
        val home = HomeView(act)
        home.setBackgroundColor(pal.surface)
        val ny = prefs.getInt("ny", 1) == 0
        val state = imeState()

        // пузыри-ярлыки по краям; часть из них скрыта за капсулой и краем экрана
        bubble(home, "🎮", "Практика", 1, -0.06f, 0.42f, 0.25f, pal.tonal, pal.text, 8, 0.08f)
        bubble(home, "✨", "Посыпки", 2, 0.80f, 0.50f, 0.22f, pal.action, pal.onAction, 10, 0.07f)
        bubble(home, "🎤", "Голос", 100, 0.02f, 0.70f, 0.22f, pal.action, pal.onAction, 4, 0.15f)
        bubble(home, "🎨", "Пресеты", 3, 0.70f, 0.70f, 0.30f, pal.tonal, pal.text, 8, 0.1f)

        // наклонная капсула с «кадром»: живые посыпки и клавиши
        val cap = FrameLayout(act)
        cap.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Ui.mix(pal.action, android.graphics.Color.WHITE, 0.35f), pal.action)
        ).also { it.cornerRadius = 4000f }
        cap.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, minOf(v.width, v.height) / 2f)
            }
        }
        cap.clipToOutline = true
        cap.rotation = -24f
        val fo = FallOverlay(act, pal.night)
        val fk = prefs.getInt("fall", 0)
        fo.configure(if (fk != 0) fk else 1, 55, 100, 0)
        cap.addView(fo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val hello = tv("Привет!", 36f, pal.onAction)
        Ui.display(hello, 36f)
        hello.gravity = Gravity.CENTER
        hello.setPadding(0, 0, 0, dp(110))
        cap.addView(hello, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val klp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(170), Gravity.BOTTOM)
        klp.bottomMargin = -dp(26)
        cap.addView(KeysArt(act, pal), klp)
        cap.setOnClickListener { onGo(3) }
        Ui.pressable(cap)
        home.put(cap, 0.34f, 0.34f, 0.58f, 0.46f, true)

        // огромное название и состояние клавиатуры
        val title = tv("Pixel\nKeys", 60f, pal.text)
        Ui.display(title, 60f)
        home.put(title, 0.06f, 0.1f, 0.80f, 0.44f)
        val subText = when (state) {
            0 -> "Клавиатура не включена. Нажми ▶"
            1 -> "Включена. Нажми ▶ и выбери Pixel Keys"
            else -> if (ny) "🎄 Всё готово. Нажми ▶ и попробуй" else "Всё готово. Нажми ▶ и попробуй"
        }
        val sub = tv(subText, 14f, pal.hint, true)
        sub.setPadding(dp(4), 0, 0, 0)
        home.put(sub, 0.06f, 0.325f, 0.62f, 0.12f)

        // большой круг-кнопка
        val big = tv("▶", 44f, pal.onAction)
        big.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(pal.action)
        big.background = bg
        big.setOnClickListener {
            when (state) {
                0 -> act.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                1 -> (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                else -> onGo(1)
            }
        }
        Ui.pressable(big)
        home.put(big, 0.62f, 0.16f, 0.32f, 0.32f)
        circle = big

        // верхний ряд: логотип, меню и настройки
        val logo = ImageView(act)
        logo.setImageResource(if (ny) R.drawable.ic_logo_ny else R.drawable.ic_logo)
        logo.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setOval(0, 0, v.width, v.height)
            }
        }
        logo.clipToOutline = true
        home.put(logo, 0.05f, 0.018f, 0.11f, 0.11f)
        roundButton(home, "⋮", 0.72f, 0.018f, 0.105f) { onMenu() }
        roundButton(home, "⚙", 0.845f, 0.018f, 0.105f) { onGo(0) }

        home.onSized = { w, _ ->
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, w * 0.2f)
            sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, w * 0.037f)
            big.setTextSize(TypedValue.COMPLEX_UNIT_PX, w * 0.12f)
        }
        return home
    }

    /** Меню по кнопке с тремя точками. */
    fun showMenu(startTutorial: () -> Unit) {
        val items = arrayOf("Пройти обучение", "Включить клавиатуру", "Выбрать Pixel Keys", "Настройки")
        AlertDialog.Builder(act)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startTutorial()
                    1 -> act.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    2 -> (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                    else -> onGo(0)
                }
            }
            .show()
    }
}
