package com.pixelkeys

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(24), dp(20), dp(24))

        fun label(s: String, sp: Float, bold: Boolean = false) {
            val t = TextView(this)
            t.text = s
            t.textSize = sp
            if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
            t.setPadding(0, dp(6), 0, dp(6))
            root.addView(t)
        }

        fun btn(s: String, onClick: () -> Unit) {
            val b = Button(this)
            b.text = s
            b.setOnClickListener { onClick() }
            root.addView(b)
        }

        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        fun cycle(key: String, title: String, names: List<String>, def: Int) {
            val b = Button(this)
            b.text = title + ": " + names[prefs.getInt(key, def)]
            b.setOnClickListener {
                val n = (prefs.getInt(key, def) + 1) % names.size
                prefs.edit().putInt(key, n).apply()
                b.text = title + ": " + names[n]
            }
            root.addView(b)
        }

        fun choose(key: String, title: String, names: List<String>, def: Int) {
            val b = Button(this)
            b.text = title + ": " + names[prefs.getInt(key, def)]
            b.setOnClickListener {
                android.app.AlertDialog.Builder(this)
                    .setTitle(title)
                    .setItems(names.toTypedArray()) { _, which ->
                        prefs.edit().putInt(key, which).apply()
                        b.text = title + ": " + names[which]
                    }
                    .show()
            }
            root.addView(b)
        }

        label("Pixel Keys", 28f, true)
        label("Клавиатура в стиле Pixel: русская и английская раскладки, подсказки слов T9, эмодзи, история буфера обмена с закреплением.", 15f)

        btn("1. Включить клавиатуру") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        btn("2. Выбрать Pixel Keys") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        val field = EditText(this)
        field.hint = "3. Нажми сюда и проверь набор"
        root.addView(field)

        label("Настройки (применяются при следующем открытии клавиатуры)", 13f)
        choose("theme", "Тема", listOf("Авто", "Светлая", "Тёмная", "Серая", "Синяя", "Бирюзовая", "Зелёная", "Фиолетовая", "Розовая", "Оранжевая"), 0)
        cycle("height", "Высота клавиш", listOf("Низкая", "Средняя", "Высокая"), 1)
        cycle("t9", "Подсказки слов (T9)", listOf("Вкл", "Выкл"), 0)
        cycle("punct", "Кнопки запятой и точки", listOf("Показывать", "Скрыть"), 0)

        btn("Очистить историю буфера") {
            ClipStore(this).clearAll()
            Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show()
        }

        val perms = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        } catch (e: Exception) {
            null
        }
        val net = perms?.contains("android.permission.INTERNET") == true
        label(
            if (net) "⚠ Приложение запрашивает доступ в интернет"
            else "✔ Доступа в интернет нет: клавиатура физически не может ничего отправить наружу.",
            14f
        )
        label("Скопированные тексты хранятся только на этом телефоне и удаляются через 24 часа (закреплённые остаются). Тексты, которые менеджер паролей пометил как секретные, не записываются.", 13f)

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
