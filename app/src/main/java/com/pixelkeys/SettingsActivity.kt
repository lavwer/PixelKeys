package com.pixelkeys

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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

        root.addView(tv("Pixel Keys", 34f, pal.text, true))
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
        val sub = tv("Версия $ver · клавиатура без доступа в интернет", 14f, pal.hint)
        sub.setPadding(0, dp(2), 0, dp(16))
        root.addView(sub)

        root.addView(previewCard(), lp())

        root.addView(section("ПОДКЛЮЧЕНИЕ"))
        root.addView(pill("Включить клавиатуру", true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, lp(0, dp(52)))
        root.addView(pill("Выбрать Pixel Keys", false) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, lp(10, dp(52)))

        val field = EditText(this)
        field.hint = "Нажми сюда и проверь набор"
        field.setTextColor(pal.text)
        field.setHintTextColor(pal.hint)
        field.background = round(pal.key, 22)
        field.setPadding(dp(18), dp(14), dp(18), dp(14))
        root.addView(field, lp(14))

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
