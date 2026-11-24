package com.pixelkeys

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val themes = listOf(
        "Авто (цвета обоев)", "Светлая", "Тёмная", "Серая", "Синяя",
        "Бирюзовая", "Зелёная", "Фиолетовая", "Розовая", "Оранжевая", "Свой цвет"
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var pal: Palette
    private lateinit var kbPal: Palette      // как выглядит сама клавиатура (с учётом фото)
    private var scroll: ScrollView? = null

    // ---- островок внизу и две страницы: «Управление» и «Практика» ----
    private var page = 0
    private lateinit var holder: FrameLayout
    private var island: View? = null
    private val islandItems = ArrayList<TextView>()
    private var controlsView: View? = null
    private var practice: PracticePage? = null
    private var practiceView: View? = null
    private var fallView: View? = null
    private var presetsView: View? = null
    private var fallDirty = false

    private val liveKeys = setOf("fxon", "morph", "hints", "bubble", "rainbow", "devrow", "ny", "nygar", "nyedge", "nysnow", "nytheme")
    private var cardIdx = 0
    private var spin: android.animation.ObjectAnimator? = null
    private var greetTaps = 0
    private var greetLast = 0L

    private val codeKeys = listOf(
        "theme", "height", "punct", "t9", "autocorr", "autoany", "numrow", "minimal",
        "radius", "keyalpha", "textsize", "gap", "vibrate", "sound", "fxon", "fx", "font", "shape", "morph", "hints",
        "bubble", "bpad", "hpad", "hue", "sat", "dh", "devrow", "rainbow", "fxdur", "fxpow", "aggr",
        "ny", "nygar", "nyedge", "nysnow", "nyfx", "nytheme", "fall", "fallamt", "fallspd", "fallwind", "sugstyle",
        "micmode", "micpos", "micshape", "miccolor", "micicon", "micquiet"
    )
    private val codeMax = mapOf("theme" to 10, "height" to 3, "numrow" to 2, "shape" to 2, "font" to 2, "fx" to 7, "nyfx" to 5, "fall" to 4, "sugstyle" to 1, "micmode" to 2, "micpos" to 5, "micshape" to 3, "miccolor" to 2, "micicon" to 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        prefs = getSharedPreferences("ui", MODE_PRIVATE)
        render()
    }

    override fun onPause() {
        practice?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        spin?.cancel()
        super.onDestroy()
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
        if (bold) t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
        c.background = Ui.blob(this, pal.container, cardIdx++ % 4)
        c.elevation = 0f
        c.setPadding(dp(22), dp(12), dp(22), dp(12))
        return c
    }

    private fun section(title: String): TextView {
        // заголовки капсом приводим к обычному написанию, смешанный регистр («Стиль Material 3») оставляем как есть
        val shown = if (title == title.uppercase()) title.lowercase().replaceFirstChar { it.uppercase() } else title
        val t = tv(shown, 24f, pal.text)
        Ui.display(t, 24f)
        t.setPadding(dp(8), dp(30), 0, dp(12))
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.PolarShape(pal.accent, 8, 0.12f, dp(20)), null, null, null)
        t.compoundDrawablePadding = dp(10)
        return t
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(pal.line)
        return v
    }

    private fun pill(text: String, filled: Boolean, onClick: () -> Unit): TextView {
        val t = tv(text, 16f, if (filled) pal.onAction else pal.text, true)
        t.gravity = Gravity.CENTER
        t.background = Ui.key3d(this, if (filled) pal.action else pal.tonal, pal.night, 26)
        t.setPadding(dp(16), 0, dp(16), 0)
        t.elevation = 0f
        t.setOnClickListener { onClick() }
        ripple(t, 26)
        Ui.pressable(t)
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
        if (primary) {
            t.background = Ui.key3d(this, pal.onAction, pal.night, 28)
            t.setPadding(dp(16), 0, dp(16), 0)
            t.elevation = 0f
        } else {
            t.background = round((pal.onAction and 0x00FFFFFF) or (0x26 shl 24), 28)
            t.setPadding(dp(16), 0, dp(16), 0)
        }
        t.setOnClickListener { onClick() }
        ripple(t, 28)
        Ui.pressable(t)
        return t
    }

    private fun chip(text: String): TextView {
        val t = tv(text, 12f, pal.text, true)
        t.background = Ui.raised(this, pal.tonal, pal.night, 14)
        t.elevation = 0f
        t.setPadding(dp(12), dp(5), dp(12), dp(5))
        return t
    }

    private fun featureCard(emoji: String, title: String, desc: String): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.raised(this, pal.container, pal.night, 26)
        c.elevation = 0f
        c.setPadding(dp(16), dp(16), dp(16), dp(18))
        val badge = tv(emoji, 22f, pal.text)
        badge.gravity = Gravity.CENTER
        badge.background = Ui.PolarShape(pal.tonal, if (title.length % 2 == 0) 8 else 4, if (title.length % 2 == 0) 0.1f else 0.15f)
        c.addView(badge, LinearLayout.LayoutParams(dp(58), dp(58)))
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
        ripple(r, 12)
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
        ripple(r, 12)
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(tv(title, 16f, pal.text))
        left.addView(tv(sub, 13f, pal.hint))
        r.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        sw.isChecked = prefs.getInt(key, def) == onWhen
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(pal.onAction, pal.hint))
        sw.trackTintList = ColorStateList(states, intArrayOf(pal.action, pal.line))
        sw.setOnCheckedChangeListener { _, c ->
            prefs.edit().putInt(key, if (c) onWhen else 1 - onWhen).apply()
            // переключатели, меняющие вид клавиатуры, сразу обновляют мини-клавиатуру и настройки
            if (key in liveKeys) window.decorView.post { render() }
        }
        r.addView(sw)
        r.setOnClickListener { sw.toggle() }
        return r
    }

    /** Волна при нажатии (как в Material 3): ограничена скруглённой формой элемента. */
    private fun ripple(v: View, radiusDp: Int) {
        val mask = GradientDrawable()
        mask.setColor(Color.WHITE)
        mask.cornerRadius = dp(radiusDp).toFloat()
        val tint = (pal.text and 0x00FFFFFF) or (0x1F shl 24)
        v.foreground = RippleDrawable(ColorStateList.valueOf(tint), null, mask)
    }

    private fun roundClip(v: View, radiusDp: Int) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(radiusDp).toFloat())
            }
        }
        v.clipToOutline = true
    }

    /** Мини-клавиатура: показывает выбранную тему, фон-фото, скругление, прозрачность и размер букв. */
    private fun previewCard(): View {
        val kb = kbPal
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        val photo = if (kb.hasPhoto) BgStore.load(this) else null
        if (photo != null) {
            outer.background = CropDrawable(photo)
            roundClip(outer, 28)
        } else {
            outer.background = Ui.raised(this, kb.bg, kb.night, 28)
            outer.elevation = 0f
        }
        outer.setPadding(dp(8), dp(10), dp(8), dp(10))

        val cap = LinearLayout(this)
        cap.orientation = LinearLayout.HORIZONTAL
        cap.gravity = Gravity.CENTER
        val m3 = kb.sugStyle == 1
        if (!m3) cap.background = round(kb.keyA, 20)
        val sugWords = listOf("привет", "прикольно", "приятно")
        for (i in sugWords.indices) {
            val t = tv(sugWords[i], 14f, kb.text)
            t.gravity = Gravity.CENTER
            t.maxLines = 1
            if (m3) {
                val g = round(if (i == 0) kb.tonal else Color.TRANSPARENT, 15)
                if (i != 0) g.setStroke(dp(1), kb.line)
                t.background = g
                if (i == 0) t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                val cl = LinearLayout.LayoutParams(0, dp(30), 1f)
                cl.setMargins(dp(3), 0, dp(3), 0)
                cap.addView(t, cl)
            } else {
                cap.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            }
        }
        val capLp = lp(0, dp(34))
        capLp.setMargins(dp(4), 0, dp(4), dp(4))
        outer.addView(cap, capLp)

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        for (ch in "приветик") {
            val k = Key(this, kb, Key.NORMAL)
            k.label = ch.toString()
            k.haptic = false
            k.onTap = { greetTap() }
            row1.addView(k, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        outer.addView(row1, lp(0, dp(46)))

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        val k1 = Key(this, kb, Key.SPECIAL)
        k1.label = "?123"
        k1.textSp = 15f
        k1.haptic = false
        val k2 = Key(this, kb, Key.NORMAL)
        k2.label = "Русский"
        k2.textSp = 13f
        k2.haptic = false
        val k3 = Key(this, kb, Key.ACTION)
        k3.scallop = true
        k3.icon = getDrawable(R.drawable.ic_enter)?.mutate()
        k3.haptic = false
        row2.addView(k1, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.6f))
        row2.addView(k2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 4f))
        row2.addView(k3, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.8f))
        outer.addView(row2, lp(0, dp(46)))
        if (!kb.ny && kb.fall == 0) return outer
        // новогодний режим: гирлянда, снег и всплески и в мини-клавиатуре
        if (kb.ny && kb.nyGarland) outer.setPadding(outer.paddingLeft, outer.paddingTop + dp(NewYearOverlay.GARLAND_DP), outer.paddingRight, outer.paddingBottom)
        val host = FrameLayout(this)
        host.addView(outer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        if (kb.fall != 0) {
            val fo = FallOverlay(this, kb.night)
            fo.configure(kb.fall, kb.fallAmt, kb.fallSpd, kb.fallWind)
            host.addView(fo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        if (kb.ny) host.addView(NewYearOverlay(this, kb), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return host
    }

    /** Строка с ползунком. Значение сохраняется, когда отпустишь палец. */
    private fun sliderRow(title: String, key: String, min: Int, max: Int, def: Int, unit: String, onDone: (() -> Unit)? = null): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(12), 0, dp(6))
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(tv(title, 16f, pal.text), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val valueTv = tv("", 15f, pal.accent, true)
        head.addView(valueTv)
        box.addView(head)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = prefs.getInt(key, def).coerceIn(min, max) - min
        valueTv.text = (sb.progress + min).toString() + unit
        sb.progressTintList = ColorStateList.valueOf(pal.accent)
        sb.thumbTintList = ColorStateList.valueOf(pal.accent)
        sb.progressBackgroundTintList = ColorStateList.valueOf(pal.tonal)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                valueTv.text = (progress + min).toString() + unit
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {
                prefs.edit().putInt(key, (bar?.progress ?: 0) + min).apply()
                if (onDone != null) onDone() else render()
            }
        })
        box.addView(sb, lp(4))
        return box
    }

    // ---------- фон из фото ----------

    private val reqPhoto = 41

    private fun pickPhoto() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        i.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(Intent.createChooser(i, "Выбери фото для фона"), reqPhoto)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqPhoto || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Toast.makeText(this, "Обрабатываю фото…", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = BgStore.importPhoto(this, uri) &&
                BgStore.process(this, prefs.getInt("bgblur", 40), prefs.getInt("bgdim", 30))
            runOnUiThread {
                if (!ok) Toast.makeText(this, "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
                render()
            }
        }.start()
    }

    private fun reprocess() {
        Toast.makeText(this, "Применяю…", Toast.LENGTH_SHORT).show()
        Thread {
            BgStore.process(this, prefs.getInt("bgblur", 40), prefs.getInt("bgdim", 30))
            runOnUiThread { render() }
        }.start()
    }

    private fun render() {
        val oldY = scroll?.scrollY ?: 0
        cardIdx = 0
        pal = Palette(this, prefs.getInt("theme", 0), true)
        kbPal = Palette(this, prefs.getInt("theme", 0))

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(28), dp(16), dp(110))

        // --- шапка сайта: логотип, название, версия ---
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
        val ny = prefs.getInt("ny", 1) == 0
        val verText = (if (ny) "🎄 " else "") + "v$ver" + (if (prefs.getInt("dev", 0) == 1) " · dev" else "")
        val logo = ImageView(this)
        logo.setImageResource(if (ny) R.drawable.ic_logo_ny else R.drawable.ic_logo)

        // --- главный блок (hero) ---
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.setPadding(dp(24), dp(24), dp(24), dp(24))
        hero.addView(chip(verText), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val big = tv("Pixel\nKeys", 64f, pal.onAction)
        Ui.display(big, 64f)
        big.setPadding(0, dp(40), 0, dp(10))
        hero.addView(big)
        val h1 = tv("Печатай быстро. Никому не доверяй пароли.", 17f, pal.onAction, true)
        h1.setPadding(0, 0, 0, dp(10))
        hero.addView(h1)
        hero.addView(tv("Умный T9, автоисправление, буфер обмена и 11 тем. Без доступа в интернет: набранное остаётся на телефоне.", 15f, pal.onAction))
        hero.addView(heroButton("Включить клавиатуру", true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, lp(22, dp(54)))
        hero.addView(heroButton("Выбрать Pixel Keys", false) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, lp(10, dp(54)))
        hero.addView(previewCard(), lp(20))
        // плакат: сплошной яркий блок с огромным названием и вращающимся «печеньем» с логотипом
        val heroFrame = FrameLayout(this)
        heroFrame.background = Ui.glossy(this, pal.action, pal.night, 40)
        roundClip(heroFrame, 52)
        val deco = View(this)
        deco.background = Ui.PolarShape(Ui.mix(pal.action, if (pal.night) Color.WHITE else Color.BLACK, 0.16f), 10, 0.07f)
        val dlp = FrameLayout.LayoutParams(dp(210), dp(210), Gravity.TOP or Gravity.END)
        dlp.topMargin = -dp(40)
        dlp.rightMargin = -dp(40)
        heroFrame.addView(deco, dlp)
        spin?.cancel()
        val spinAnim = android.animation.ObjectAnimator.ofFloat(deco, View.ROTATION, 0f, 360f)
        spinAnim.duration = 40000L
        spinAnim.interpolator = android.view.animation.LinearInterpolator()
        spinAnim.repeatCount = android.animation.ValueAnimator.INFINITE
        spinAnim.start()
        spin = spinAnim
        heroFrame.addView(hero, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        val llp = FrameLayout.LayoutParams(dp(64), dp(64), Gravity.TOP or Gravity.END)
        llp.topMargin = dp(33)
        llp.rightMargin = dp(33)
        heroFrame.addView(logo, llp)
        root.addView(heroFrame, lp(16))

        // --- новый год ---
        root.addView(section("Новый год 🎄"))
        val cny = card()
        cny.addView(switchRow("Новогодний режим", "Мишура, конфетти, снегопад и ёлочные цвета", "ny", 1, 0))
        if (prefs.getInt("ny", 1) == 0) {
            cny.addView(divider(), lp(0, dp(1)))
            cny.addView(switchRow("Гирлянда и мишура", "Светящаяся гирлянда над клавишами", "nygar", 0, 0))
            cny.addView(divider(), lp(0, dp(1)))
            cny.addView(switchRow("Гирлянды по краям", "Мишура, шарики и огоньки вдоль краёв экрана", "nyedge", 0, 0))
            cny.addView(divider(), lp(0, dp(1)))
            cny.addView(switchRow("Снегопад", "Лёгкий снег поверх клавиатуры", "nysnow", 0, 0))
            cny.addView(divider(), lp(0, dp(1)))
            cny.addView(switchRow("Ёлочные цвета", "Хвойный зелёный, красный и золото", "nytheme", 0, 0))
            cny.addView(divider(), lp(0, dp(1)))
            cny.addView(choiceRow("Эффект при нажатии", "nyfx", listOf("Как в настройках", "Конфетти", "Снежинки", "Салют", "Звёзды", "Мишура"), 1))
            cny.addView(tv("Эффекты летят по всей клавиатуре. Нажми на клавиши мини-клавиатуры вверху, чтобы посмотреть.", 12f, pal.hint).apply { setPadding(0, dp(4), 0, dp(10)) })
        }
        root.addView(cny, lp())

        if (prefs.getInt("dev", 0) == 1) addDevSection(root)

        // --- возможности ---
        root.addView(section("ВОЗМОЖНОСТИ"))
        root.addView(featureRow(
            featureCard("🔒", "Без интернета", "В приложении нет разрешения на сеть. Отправить нечего и некуда."),
            featureCard("✨", "Умный T9", "Подсказки и автоисправление, в том числе при неверной раскладке.")
        ), lp())
        root.addView(featureRow(
            featureCard("📋", "Буфер обмена", "История копирования с закреплением. Секретное не сохраняется."),
            featureCard("🎨", "11 тем", "Цвета из обоев Pixel или свои: серая, синяя, зелёная и другие.")
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
        field.background = round(pal.container, 22)
        field.setPadding(dp(18), dp(16), dp(18), dp(16))
        root.addView(field, lp())

        root.addView(section("ВНЕШНИЙ ВИД"))
        val c1 = card()
        c1.addView(choiceRow("Тема", "theme", themes, 0))
        if (prefs.getInt("theme", 0) == 10) {
            c1.addView(divider(), lp(0, dp(1)))
            c1.addView(sliderRow("Оттенок", "hue", 0, 359, 210, "°"))
            c1.addView(divider(), lp(0, dp(1)))
            c1.addView(sliderRow("Насыщенность", "sat", 20, 100, 100, "%"))
        }
        c1.addView(divider(), lp(0, dp(1)))
        c1.addView(choiceRow("Высота клавиш", "height", listOf("Низкая", "Средняя", "Высокая", "Очень высокая"), 1))
        c1.addView(divider(), lp(0, dp(1)))
        c1.addView(choiceRow("Ряд цифр", "numrow", listOf("Всегда", "Только вертикально", "Скрыть"), 1))
        root.addView(c1, lp())

        // --- фон из фото ---
        root.addView(section("ФОН КЛАВИАТУРЫ"))
        val cbg = card()
        val hasPhoto = BgStore.exists(this)
        if (hasPhoto) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(BgStore.load(this))
            roundClip(iv, 20)
            cbg.addView(iv, lp(12, dp(130)))
        } else {
            val hintTv = tv("Поставь своё фото на фон клавиатуры. Фото берётся из галереи и хранится только на телефоне.", 14f, pal.hint)
            hintTv.setPadding(0, dp(14), 0, dp(6))
            cbg.addView(hintTv)
        }
        cbg.addView(pill(if (hasPhoto) "Выбрать другое фото" else "Выбрать фото", true) { pickPhoto() }, lp(12, dp(50)))
        if (hasPhoto) {
            cbg.addView(sliderRow("Размытие", "bgblur", 0, 100, 40, "%") { reprocess() })
            cbg.addView(divider(), lp(0, dp(1)))
            cbg.addView(sliderRow("Затемнение", "bgdim", 0, 80, 30, "%") { reprocess() })
            cbg.addView(pill("Убрать фото", false) {
                BgStore.clear(this)
                render()
            }, lp(10, dp(50)))
        }
        root.addView(cbg, lp())

        // --- форма и размер клавиш ---
        root.addView(section("ФОРМА И РАЗМЕР КЛАВИШ"))
        val cshape = card()
        cshape.addView(sliderRow("Скругление клавиш", "radius", 0, 100, 50, "%"))
        cshape.addView(divider(), lp(0, dp(1)))
        cshape.addView(sliderRow("Прозрачность клавиш", "keyalpha", 30, 100, 100, "%"))
        cshape.addView(divider(), lp(0, dp(1)))
        cshape.addView(sliderRow("Размер букв", "textsize", 70, 140, 100, "%"))
        cshape.addView(divider(), lp(0, dp(1)))
        cshape.addView(sliderRow("Расстояние между клавишами", "gap", 0, 8, 3, " dp"))
        root.addView(cshape, lp())

        // --- кастом в стиле ма3 ---
        root.addView(section("Стиль Material 3"))
        val cm3 = card()
        cm3.addView(choiceRow("Панель подсказок", "sugstyle", listOf("Капсула", "Ма3: чипы"), 0))
        cm3.addView(divider(), lp(0, dp(1)))
        cm3.addView(choiceRow("Форма служебных клавиш", "shape", listOf("Волнистая", "Скруглённая", "Капсула"), 0))
        cm3.addView(divider(), lp(0, dp(1)))
        cm3.addView(choiceRow("Начертание букв", "font", listOf("Обычное", "Среднее", "Жирное"), 0))
        cm3.addView(divider(), lp(0, dp(1)))
        cm3.addView(switchRow("Анимация нажатия", "Клавиша на миг становится капсулой", "morph", 0, 0))
        cm3.addView(divider(), lp(0, dp(1)))
        cm3.addView(switchRow("Подсказки на клавишах", "Мелкие символы в углу: ё, ъ, ?, !", "hints", 0, 0))
        cm3.addView(divider(), lp(0, dp(1)))
        cm3.addView(switchRow("Увеличенная буква", "Показывать над клавишей при нажатии", "bubble", 0, 0))
        cm3.addView(divider(), lp(0, dp(1)))
        cm3.addView(sliderRow("Отступ снизу", "bpad", 0, 32, 8, " dp"))
        cm3.addView(divider(), lp(0, dp(1)))
        cm3.addView(sliderRow("Отступы по бокам", "hpad", 0, 16, 4, " dp"))
        root.addView(cm3, lp())

        // --- голосовой ввод ---
        root.addView(section("Голосовой ввод 🎤"))
        val cmic = card()
        cmic.addView(choiceRow("Режим", "micmode", listOf("Один раз", "Постоянно", "Удержание"), 0))
        cmic.addView(tv("Один раз: пишет, пока говоришь, и останавливается на паузе. Постоянно: нажал один раз и пишет даже в тишине, пока не нажмёшь снова. Удержание: пишет, пока держишь кнопку.", 12f, pal.hint).apply { setPadding(0, 0, 0, dp(10)) })
        cmic.addView(divider(), lp(0, dp(1)))
        cmic.addView(choiceRow("Где кнопка", "micpos", listOf("Справа от пробела", "Слева от пробела", "Вместо запятой", "Вместо точки", "Вместо глобуса", "Сверху справа"), 0))
        cmic.addView(divider(), lp(0, dp(1)))
        cmic.addView(choiceRow("Форма кнопки", "micshape", listOf("Полоска", "Круг", "Печенье", "Как клавиша"), 0))
        cmic.addView(divider(), lp(0, dp(1)))
        cmic.addView(choiceRow("Цвет кнопки", "miccolor", listOf("Тональная", "Акцентная", "Как у букв"), 0))
        cmic.addView(divider(), lp(0, dp(1)))
        cmic.addView(choiceRow("Значок", "micicon", listOf("Микрофон", "Волна"), 0))
        cmic.addView(divider(), lp(0, dp(1)))
        cmic.addView(switchRow("Тихий перезапуск", "Приглушает сигнал распознавателя между фразами", "micquiet", 0, 0))
        cmic.addView(tv("«Вместо глобуса»: язык тогда меняется долгим нажатием на пробел. «Сверху справа»: приложение открывается долгим нажатием на кнопку.", 12f, pal.hint).apply { setPadding(0, dp(4), 0, dp(10)) })
        root.addView(cmic, lp())

        // --- эффекты после нажатия ---
        root.addView(section("Эффекты нажатия"))
        val cfx2 = card()
        cfx2.addView(switchRow("Эффекты при нажатии", "Короткая анимация внутри клавиши после касания", "fxon", 1, 0))
        cfx2.addView(divider(), lp(0, dp(1)))
        val fxNames = if (prefs.getInt("dev", 0) == 1) {
            listOf("Волна", "Кольцо", "Свечение", "Пиксели", "Искры", "Радуга", "Конфетти", "Неон")
        } else {
            listOf("Волна", "Кольцо", "Свечение", "Пиксели", "Искры")
        }
        if (prefs.getInt("fx", 0) >= fxNames.size) prefs.edit().putInt("fx", fxNames.size - 1).apply()
        cfx2.addView(choiceRow("Эффект", "fx", fxNames, 0))
        cfx2.addView(tv("Нажми на клавиши мини-клавиатуры вверху, чтобы посмотреть эффект.", 13f, pal.hint).apply { setPadding(0, dp(4), 0, dp(10)) })
        root.addView(cfx2, lp())

        root.addView(section("ОТКЛИК"))
        val cfx = card()
        cfx.addView(switchRow("Вибрация", "Лёгкий отклик при нажатии", "vibrate", 0, 0))
        cfx.addView(divider(), lp(0, dp(1)))
        cfx.addView(switchRow("Звук клавиш", "Работает, если в системе включены звуки нажатий", "sound", 1, 0))
        root.addView(cfx, lp())
        root.addView(pill("Сбросить оформление", false) {
            val e = prefs.edit()
            for (k in listOf("radius", "keyalpha", "textsize", "gap", "bgblur", "bgdim", "vibrate", "sound",
                "font", "shape", "morph", "hints", "bubble", "bpad", "hpad", "hue", "sat", "fxon", "fx")) e.remove(k)
            e.apply()
            BgStore.clear(this)
            render()
        }, lp(12, dp(50)))

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
        sv.setBackgroundColor(pal.surface)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(root)
        scroll = sv
        mount(sv)
        if (oldY > 0) sv.post { sv.scrollTo(0, oldY) }
        applyBars()
    }

    /** Нажатия на слово «приветик» в мини-клавиатуре: серия нажатий переключает режим разработчика. */
    private fun greetTap() {
        val now = System.currentTimeMillis()
        greetTaps = if (now - greetLast > 2000L) 1 else greetTaps + 1
        greetLast = now
        val dev = prefs.getInt("dev", 0) == 1
        if (!dev && greetTaps >= 5) {
            greetTaps = 0
            prefs.edit().putInt("dev", 1).apply()
            Toast.makeText(this, "🧪 Режим разработчика включён", Toast.LENGTH_SHORT).show()
            window.decorView.post { render() }
        } else if (dev && greetTaps >= 3) {
            greetTaps = 0
            prefs.edit().putInt("dev", 0).apply()
            Toast.makeText(this, "Режим разработчика выключен", Toast.LENGTH_SHORT).show()
            window.decorView.post { render() }
        }
    }

    private fun addDevSection(root: LinearLayout) {
        val head = LinearLayout(this)
        head.orientation = LinearLayout.VERTICAL
        head.background = Ui.raised(this, pal.tonal, pal.night, 28)
        head.elevation = 0f
        head.setPadding(dp(20), dp(18), dp(20), dp(18))
        head.addView(tv("🧪 Режим разработчика", 20f, pal.text, true))
        head.addView(tv("Безумные настройки: точная высота, ряд программиста, радужные клавиши, ещё три эффекта, смелость автоисправления и перенос настроек на другой телефон.", 13f, pal.hint).apply { setPadding(0, dp(6), 0, 0) })
        root.addView(head, lp(14))

        root.addView(section("Клавиатура (dev)"))
        val d1 = card()
        d1.addView(sliderRow("Точная высота ряда", "dh", 0, 110, 0, " dp"))
        d1.addView(tv("0 — использовать обычную высоту (низкая, средняя, высокая, очень высокая).", 12f, pal.hint).apply { setPadding(0, 0, 0, dp(8)) })
        d1.addView(divider(), lp(0, dp(1)))
        d1.addView(switchRow("Ряд программиста", "Tab, стрелки и { } ( ) [ ] < > над буквами", "devrow", 1, 0))
        d1.addView(divider(), lp(0, dp(1)))
        d1.addView(switchRow("Радужные клавиши", "Каждая буква своего цвета", "rainbow", 1, 0))
        root.addView(d1, lp())

        root.addView(section("Эффекты (dev)"))
        val d2 = card()
        d2.addView(sliderRow("Длительность эффекта", "fxdur", 120, 1600, 420, " мс"))
        d2.addView(divider(), lp(0, dp(1)))
        d2.addView(sliderRow("Сила эффекта", "fxpow", 20, 100, 100, "%"))
        d2.addView(tv("Три новых эффекта (радуга, конфетти, неон) есть в списке эффектов ниже. Включи эффекты и выбери один.", 12f, pal.hint).apply { setPadding(0, 0, 0, dp(10)) })
        root.addView(d2, lp())

        root.addView(section("Автоисправление (dev)"))
        val d3 = card()
        d3.addView(sliderRow("Смелость исправления", "aggr", 1, 9, 5, ""))
        d3.addView(tv("5 — как обычно. Больше — исправляет смелее и чаще ошибается, меньше — осторожнее.", 12f, pal.hint).apply { setPadding(0, 0, 0, dp(10)) })
        root.addView(d3, lp())

        root.addView(section("Инструменты (dev)"))
        val d4 = card()
        d4.addView(tv("Перенос на другой телефон: здесь нажми «Скопировать код», отправь его себе в мессенджер, а на другом телефоне открой «Вставить код».", 13f, pal.hint).apply { setPadding(0, dp(12), 0, dp(10)) })
        d4.addView(pill("Скопировать код настроек", true) { copySettingsCode() }, lp(0, dp(52)))
        d4.addView(pill("Вставить код из буфера", false) { pasteSettingsCode() }, lp(10, dp(52)))
        d4.addView(pill("Сбросить все настройки", false) { confirmReset() }, lp(10, dp(52)))
        d4.addView(tv("Фон-фото и рекорды в код не входят.", 12f, pal.hint).apply { setPadding(0, dp(10), 0, dp(12)) })
        root.addView(d4, lp())

        root.addView(section("Текущие значения (dev)"))
        val d5 = card()
        val dump = tv(dumpText(), 12f, pal.text)
        dump.typeface = Typeface.MONOSPACE
        dump.setPadding(0, dp(12), 0, dp(12))
        d5.addView(dump)
        root.addView(d5, lp())
    }

    private fun copySettingsCode() {
        val sb = StringBuilder("PXK1")
        for (k in codeKeys) {
            if (prefs.contains(k)) sb.append('\n').append(k).append('=').append(prefs.getInt(k, 0))
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Pixel Keys", sb.toString()))
        Toast.makeText(this, "Код настроек скопирован", Toast.LENGTH_SHORT).show()
    }

    private fun pasteSettingsCode() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        val lines = text.trim().split("\n").map { it.trim() }
        if (lines.isEmpty() || lines[0] != "PXK1") {
            Toast.makeText(this, "В буфере нет кода настроек", Toast.LENGTH_SHORT).show()
            return
        }
        val ed = prefs.edit()
        var n = 0
        for (i in 1 until lines.size) {
            val parts = lines[i].split("=")
            if (parts.size != 2) continue
            val key = parts[0].trim()
            val v = parts[1].trim().toIntOrNull() ?: continue
            if (key in codeKeys) {
                ed.putInt(key, v.coerceIn(0, codeMax[key] ?: 1000000))
                n++
            }
        }
        ed.apply()
        Toast.makeText(this, "Применено настроек: $n", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Сбросить все настройки?")
            .setMessage("Вернутся значения по умолчанию. Фон-фото и рекорды останутся.")
            .setPositiveButton("Сбросить") { _, _ ->
                val ed = prefs.edit()
                for (k in codeKeys) ed.remove(k)
                ed.apply()
                render()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun dumpText(): String {
        val sb = StringBuilder()
        for (k in codeKeys.sorted()) {
            if (prefs.contains(k)) sb.append(k).append(" = ").append(prefs.getInt(k, 0)).append('\n')
        }
        return if (sb.isEmpty()) "Всё по умолчанию" else sb.toString().trimEnd()
    }

    /** Собирает экран: страница + плавающий островок ма3 внизу. */
    private fun mount(controls: View) {
        practice?.stop()
        practice = null          // перекрасится при открытии: цвета темы могли поменяться
        practiceView = null
        fallView = null
        presetsView = null
        controlsView = controls
        val host = FrameLayout(this)
        host.setBackgroundColor(pal.surface)
        holder = FrameLayout(this)
        host.addView(holder, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val isl = buildIsland()
        val ip = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        ip.bottomMargin = dp(16)
        host.addView(isl, ip)
        island = isl
        // новогодний слой на весь экран: гирлянды по краям и снег; касания проходят сквозь него
        if (pal.ny && (pal.nyEdge || pal.nySnow)) {
            host.addView(NewYearOverlay(this, pal, pal.nyEdge, pal.nyEdge, 26, true), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        // когда открыта клавиатура, островок прячем, чтобы не закрывал поле ввода
        host.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            host.getWindowVisibleDisplayFrame(r)
            val full = host.rootView.height
            val kb = full > 0 && (full - r.bottom) > full * 0.15f
            island?.visibility = if (kb) View.GONE else View.VISIBLE
        }
        setContentView(host)
        showPage(page)
    }

    private fun showPage(p: Int) {
        // вернулись в «Управление» после смены посыпок: пересобираем экран, чтобы мини-клавиатура обновилась
        if (p == 0 && fallDirty) {
            fallDirty = false
            page = 0
            render()
            return
        }
        if (page == 1 && p != 1) practice?.stop()
        val changed = p != page
        page = p
        holder.removeAllViews()
        if (p == 0) {
            holder.addView(controlsView)
        } else if (p == 1) {
            if (practiceView == null) {
                val pg = PracticePage(this, pal, prefs)
                practice = pg
                practiceView = pg.view()
            }
            holder.addView(practiceView)
        } else if (p == 2) {
            if (fallView == null) fallView = FallPage(this, pal, prefs) { fallDirty = true }.view()
            holder.addView(fallView)
        } else {
            // после применения пресета перерисовываем всё: могла поменяться тема
            if (presetsView == null) presetsView = PresetsPage(this, pal, prefs) { window.decorView.post { render() } }.view()
            holder.addView(presetsView)
        }
        // выразительное появление страницы: плавно и с лёгким перелётом
        val shown = holder.getChildAt(0)
        if (changed && shown != null) {
            shown.alpha = 0f
            shown.translationY = dp(18).toFloat()
            shown.animate().alpha(1f).translationY(0f).setDuration(320L)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
        }
        updateIsland()
    }

    private val islandIcons = listOf("⚙️", "🎮", "✨", "🎨")
    private val islandLabels = listOf("Управление", "Практика", "Посыпки", "Пресеты")

    private fun islandBar(): Int =
        if (pal.night) Ui.mix(pal.surface, Color.WHITE, 0.10f) else Ui.mix(pal.text, Color.WHITE, 0.06f)

    private fun buildIsland(): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(8), dp(8), dp(8), dp(8))
        box.background = Ui.raised(this, islandBar(), pal.night, 36)
        box.elevation = dp(6).toFloat()
        islandItems.clear()
        for (i in islandIcons.indices) {
            val t = TextView(this)
            t.gravity = Gravity.CENTER
            t.minWidth = dp(72)
            t.setPadding(dp(6), dp(6), dp(6), dp(6))
            t.setOnClickListener { showPage(i) }
            ripple(t, 28)
            box.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(62)))
            islandItems.add(t)
        }
        return box
    }

    /** Значок сверху, подпись под ним; выбранный пункт лежит в яркой капсуле. */
    private fun updateIsland() {
        for (i in islandItems.indices) {
            val t = islandItems[i]
            val sel = i == page
            val sp = android.text.SpannableString(islandIcons[i] + "\n" + islandLabels[i])
            sp.setSpan(android.text.style.RelativeSizeSpan(0.62f), islandIcons[i].length, sp.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            t.text = sp
            t.textSize = 20f
            t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            t.setLineSpacing(0f, 0.95f)
            if (sel) {
                t.background = Ui.raised(this, pal.action, pal.night, 28)
                t.setTextColor(pal.onAction)
            } else {
                t.background = null
                t.setTextColor(if (pal.night) pal.hint else Ui.mix(Color.WHITE, pal.text, 0.25f))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyBars() {
        window.statusBarColor = pal.surface
        window.navigationBarColor = pal.surface
        val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val f = window.decorView.systemUiVisibility
        window.decorView.systemUiVisibility = if (pal.night) f and light.inv() else f or light
    }
}
