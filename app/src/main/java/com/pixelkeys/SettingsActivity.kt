package com.pixelkeys

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val themes = listOf(
        "Авто (цвета обоев)", "Светлая", "Тёмная", "Серая", "Синяя",
        "Бирюзовая", "Зелёная", "Фиолетовая", "Розовая", "Оранжевая"
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var pal: Palette
    private var scroll: ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        prefs = getSharedPreferences("ui", MODE_PRIVATE)
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    private fun tv(text: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun lp(top: Int = 0, height: Int = LinearLayout.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        p.topMargin = dp(top)
        return p
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = round(pal.key, 28)
        c.setPadding(dp(18), dp(8), dp(18), dp(8))
        return c
    }

    private fun section(title: String): TextView {
        val t = tv(title, 12f, pal.accent, true)
        t.setPadding(dp(8), dp(24), 0, dp(8))
        return t
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(pal.special)
        return v
    }

    private fun pill(text: String, filled: Boolean, onClick: () -> Unit): TextView {
        val t = tv(text, 16f, if (filled) pal.onAction else pal.text, true)
        t.gravity = Gravity.CENTER
        t.background = round(if (filled) pal.action else pal.special, 26)
        t.setPadding(dp(16), 0, dp(16), 0)
        t.setOnClickListener { onClick() }
        return t
    }

    /** Смещает оттенок цвета: из любого цвета темы получается красивый двухцветный градиент. */
    private fun shiftHue(color: Int, deg: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + deg) % 360f
        hsv[1] = (hsv[1] * 1.1f).coerceAtMost(1f)
        return Color.HSVToColor(hsv)
    }

    private fun gradient(c1: Int, c2: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    private fun heroButton(text: String, primary: Boolean, onClick: () -> Unit): TextView {
        val t = tv(text, 16f, if (primary) pal.action else pal.onAction, true)
        t.gravity = Gravity.CENTER
        t.background = round(if (primary) pal.onAction else Color.argb(48, 255, 255, 255), 28)
        t.setPadding(dp(16), 0, dp(16), 0)
        t.setOnClickListener { onClick() }
        return t
    }

    private fun chip(text: String): TextView {
        val t = tv(text, 12f, pal.text, true)
        t.background = round(pal.special, 14)
        t.setPadding(dp(12), dp(5), dp(12), dp(5))
        return t
    }

    private fun featureCard(emoji: String, title: String, desc: String): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = round(pal.key, 26)
        c.setPadding(dp(16), dp(16), dp(16), dp(18))
        val badge = tv(emoji, 22f, pal.text)
        badge.gravity = Gravity.CENTER
        badge.background = round(pal.special, 16)
        c.addView(badge, LinearLayout.LayoutParams(dp(46), dp(46)))
        val t = tv(title, 16f, pal.text, true)
        t.setPadding(0, dp(12), 0, dp(4))
        c.addView(t)
        c.addView(tv(desc, 13f, pal.hint))
        return c
    }

    private fun featureRow(a: View, b: View): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val la = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        la.rightMargin = dp(5)
        val lb = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        lb.leftMargin = dp(5)
        r.addView(a, la)
        r.addView(b, lb)
        return r
    }

    private fun stepRow(n: Int, text: String): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(10), 0, dp(10))
        val num = tv(n.toString(), 16f, pal.onAction, true)
        num.gravity = Gravity.CENTER
        val oval = GradientDrawable()
        oval.shape = GradientDrawable.OVAL
        oval.setColor(pal.action)
        num.background = oval
        r.addView(num, LinearLayout.LayoutParams(dp(36), dp(36)))
        val t = tv(text, 15f, pal.text)
        t.setPadding(dp(14), 0, 0, 0)
        r.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return r
    }

    private fun choiceRow(title: String, key: String, names: List<String>, def: Int): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(14), 0, dp(14))
        r.addView(tv(title, 16f, pal.text), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(tv(names[prefs.getInt(key, def).coerceIn(0, names.size - 1)], 15f, pal.accent, true))
        r.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(names.toTypedArray()) { _, which ->
                    prefs.edit().putInt(key, which).apply()
                    render()
                }
                .show()
        }
        return r
    }

    private fun switchRow(title: String, sub: String, key: String, def: Int, onWhen: Int): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(10), 0, dp(10))
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(tv(title, 16f, pal.text))
        left.addView(tv(sub, 13f, pal.hint))
        r.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        sw.isChecked = prefs.getInt(key, def) == onWhen
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(pal.onAction, pal.hint))
        sw.trackTintList = ColorStateList(states, intArrayOf(pal.action, pal.pressed))
        sw.setOnCheckedChangeListener { _, c ->
            prefs.edit().putInt(key, if (c) onWhen else 1 - onWhen).apply()
        }
        r.addView(sw)
        r.setOnClickListener { sw.toggle() }
        return r
    }

    /** Мини-клавиатура, которая показывает выбранную тему и форму клавиш. */
    private fun previewCard(): View {
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        val bg = round(pal.bg, 28)
        bg.setStroke(dp(2), pal.pressed)
        outer.background = bg
        outer.setPadding(dp(8), dp(10), dp(8), dp(10))

        val cap = LinearLayout(this)
        cap.orientation = LinearLayout.HORIZONTAL
        cap.gravity = Gravity.CENTER
        cap.background = round(pal.key, 20)
        for (w in listOf("привет", "прикольно", "приятно")) {
            val t = tv(w, 14f, pal.text)
            t.gravity = Gravity.CENTER
            t.maxLines = 1
            cap.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        val capLp = lp(0, dp(34))
        capLp.setMargins(dp(4), 0, dp(4), dp(4))
        outer.addView(cap, capLp)

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        for (ch in "приветик") {
            val k = Key(this, pal, Key.NORMAL)
            k.label = ch.toString()
            k.haptic = false
            row1.addView(k, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        outer.addView(row1, lp(0, dp(46)))

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        val k1 = Key(this, pal, Key.SPECIAL)
        k1.label = "?123"
        k1.textSp = 15f
        k1.haptic = false
        val k2 = Key(this, pal, Key.NORMAL)
        k2.label = "Русский"
        k2.textSp = 13f
        k2.haptic = false
        val k3 = Key(this, pal, Key.ACTION)
        k3.scallop = true
        k3.icon = getDrawable(R.drawable.ic_enter)?.mutate()
        k3.haptic = false
        row2.addView(k1, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.6f))
        row2.addView(k2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4f))
        row2.addView(k3, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.8f))
        outer.addView(row2, lp(0, dp(46)))
        return outer
    }

    private fun render() {
        val oldY = scroll?.scrollY ?: 0
        pal = Palette(this, prefs.getInt("theme", 0))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(28), dp(16), dp(40))

        // --- шапка сайта: логотип, название, версия ---
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        try {
            logo.setImageDrawable(packageManager.getApplicationIcon(packageName))
        } catch (e: Exception) {
        }
        nav.addView(logo, LinearLayout.LayoutParams(dp(40), dp(40)))
        val brand = tv("Pixel Keys", 20f, pal.text, true)
        brand.setPadding(dp(12), 0, 0, 0)
        nav.addView(brand, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(chip("v$ver"))
        root.addView(nav, lp(0, dp(48)))

        // --- главный блок (hero) ---
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.background = gradient(pal.action, shiftHue(pal.action, 42f), 32)
        hero.setPadding(dp(22), dp(26), dp(22), dp(22))
        hero.addView(tv("КЛАВИАТУРА", 12f, pal.onAction, true))
        val h1 = tv("Печатай быстро.\nНикому не доверяй пароли.", 30f, pal.onAction, true)
        h1.setPadding(0, dp(6), 0, dp(10))
        hero.addView(h1)
        hero.addView(tv("Умный T9, автоисправление, буфер обмена и 10 тем. Без доступа в интернет: набранное остаётся на телефоне.", 15f, pal.onAction))
        hero.addView(heroButton("Включить клавиатуру", true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, lp(22, dp(54)))
        hero.addView(heroButton("Выбрать Pixel Keys", false) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, lp(10, dp(54)))
        hero.addView(previewCard(), lp(20))
        root.addView(hero, lp(16))

        // --- возможности ---
        root.addView(section("ВОЗМОЖНОСТИ"))
        root.addView(featureRow(
            featureCard("🔒", "Без интернета", "В приложении нет разрешения на сеть. Отправить нечего и некуда."),
            featureCard("✨", "Умный T9", "Подсказки и автоисправление, в том числе при неверной раскладке.")
        ), lp())
        root.addView(featureRow(
            featureCard("📋", "Буфер обмена", "История копирования с закреплением. Секретное не сохраняется."),
            featureCard("🎨", "10 тем", "Цвета из обоев Pixel или свои: серая, синяя, зелёная и другие.")
        ), lp(10))

        // --- как подключить ---
        root.addView(section("КАК ПОДКЛЮЧИТЬ"))
        val steps = card()
        steps.addView(stepRow(1, "Нажми «Включить клавиатуру» и включи Pixel Keys в списке"))
        steps.addView(divider(), lp(0, dp(1)))
        steps.addView(stepRow(2, "Нажми «Выбрать Pixel Keys» и укажи её основной"))
        steps.addView(divider(), lp(0, dp(1)))
        steps.addView(stepRow(3, "Проверь набор в поле ниже"))
        root.addView(steps, lp())

        root.addView(section("ПОПРОБУЙ ПРЯМО ЗДЕСЬ"))
        val field = EditText(this)
        field.hint = "Нажми сюда и напиши что-нибудь"
        field.setTextColor(pal.text)
        field.setHintTextColor(pal.hint)
        field.background = round(pal.key, 22)
        field.setPadding(dp(18), dp(16), dp(18), dp(16))
        root.addView(field, lp())

        root.addView(section("ВНЕШНИЙ ВИД"))
        val c1 = card()
        c1.addView(choiceRow("Тема", "theme", themes, 0))
        c1.addView(divider(), lp(0, dp(1)))
        c1.addView(choiceRow("Высота клавиш", "height", listOf("Низкая", "Средняя", "Высокая"), 1))
        c1.addView(divider(), lp(0, dp(1)))
        c1.addView(choiceRow("Ряд цифр", "numrow", listOf("Всегда", "Только вертикально", "Скрыть"), 1))
        root.addView(c1, lp())

        root.addView(section("РАСКЛАДКА"))
        val c2 = card()
        c2.addView(switchRow("Только буквы", "Без цифр, запятой, точки и глобуса", "minimal", 0, 1))
        c2.addView(divider(), lp(0, dp(1)))
        c2.addView(switchRow("Запятая и точка", "Кнопки в нижнем ряду", "punct", 0, 0))
        root.addView(c2, lp())

        root.addView(section("ВВОД"))
        val c3 = card()
        c3.addView(switchRow("Подсказки слов (T9)", "Три слова над клавиатурой", "t9", 0, 0))
        c3.addView(divider(), lp(0, dp(1)))
        c3.addView(switchRow("Автоисправление", "Опечатки и слова не в той раскладке", "autocorr", 0, 0))
        c3.addView(divider(), lp(0, dp(1)))
        c3.addView(switchRow("Исправлять везде", "Даже там, где приложение просит не править текст", "autoany", 0, 0))
        root.addView(c3, lp())

        root.addView(section("ДАННЫЕ"))
        root.addView(pill("Очистить историю буфера", false) {
            ClipStore(this).clearAll()
            Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show()
        }, lp(0, dp(52)))
        root.addView(pill("Забыть выученные слова", false) {
            prefs.edit().remove("userwords").apply()
            Toast.makeText(this, "Готово", Toast.LENGTH_SHORT).show()
        }, lp(10, dp(52)))

        root.addView(section("ДИАГНОСТИКА"))
        val c5 = card()
        val diag = (prefs.getString("diag", "") ?: "").split("\n").filter { it.isNotBlank() }
        c5.addView(tv("В каких приложениях работают подсказки и автоисправление. Откройте клавиатуру в нужном приложении и вернитесь сюда.", 13f, pal.hint).apply { setPadding(0, dp(10), 0, dp(6)) })
        if (diag.isEmpty()) {
            c5.addView(tv("Пока пусто.", 14f, pal.text).apply { setPadding(0, dp(4), 0, dp(10)) })
        } else {
            for (d in diag) {
                c5.addView(tv(d.replace(" | ", "\n"), 12f, pal.text).apply { setPadding(0, dp(8), 0, dp(8)) })
                c5.addView(divider(), lp(0, dp(1)))
            }
        }
        root.addView(c5, lp())
        root.addView(pill("Обновить список", false) { render() }, lp(10, dp(52)))

        root.addView(section("ПРИВАТНОСТЬ"))
        val c4 = card()
        val perms = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        } catch (e: Exception) {
            null
        }
        val net = perms?.contains("android.permission.INTERNET") == true
        val status = tv(
            if (net) "⚠ Приложение запрашивает доступ в интернет"
            else "✔ Доступа в интернет нет: клавиатура физически не может ничего отправить наружу.",
            15f, pal.accent, true
        )
        status.setPadding(0, dp(12), 0, dp(6))
        c4.addView(status)
        val note = tv(
            "Скопированные тексты хранятся только на этом телефоне и удаляются через 24 часа (закреплённые остаются). " +
                "Тексты, которые менеджер паролей пометил как секретные, не записываются. " +
                "В полях паролей, почты и адресов подсказки и автоисправление выключены.",
            13f, pal.hint
        )
        note.setPadding(0, 0, 0, dp(12))
        c4.addView(note)
        root.addView(c4, lp())

        val foot = tv("Pixel Keys · версия $ver", 13f, pal.hint, true)
        foot.gravity = Gravity.CENTER
        foot.setPadding(0, dp(30), 0, dp(4))
        root.addView(foot)
        val foot2 = tv("Сделано так, чтобы набранный текст никуда не уходил.", 12f, pal.hint)
        foot2.gravity = Gravity.CENTER
        root.addView(foot2)

        val sv = ScrollView(this)
        sv.setBackgroundColor(pal.bg)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(root)
        setContentView(sv)
        scroll = sv
        if (oldY > 0) sv.post { sv.scrollTo(0, oldY) }
        applyBars()
    }

    @Suppress("DEPRECATION")
    private fun applyBars() {
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val f = window.decorView.systemUiVisibility
        window.decorView.systemUiVisibility = if (pal.night) f and light.inv() else f or light
    }
}
