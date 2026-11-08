package com.pixelkeys

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class PixelIME : InputMethodService() {

    private val ruRows = listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю")
    private val enRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val sym1 = listOf("@#\$_&-+()/", "*\"':;!?%=", "~|\\<>[]")
    private val sym2 = listOf("€£¥¢°•×÷±¶", "{}^`«»§√π", "™©®∞≈≠≤")
    // Долгое нажатие: показывает мелкую подсказку в углу клавиши и вводит этот символ
    private val ALT = mapOf("е" to "ё", "ь" to "ъ", "." to "?", "," to "!")

    // Эмодзи по категориям (первый символ категории служит значком вкладки)
    private val emojiCats = listOf(
        "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕",
        "👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐 🖖 👋 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💪 👀 👅 👄 🧠 👶 👦 👧 👨 👩 🧑 👴 👵 🙋 🤷 🤦 💁 🙆 🙅 💃 🕺",
        "❤️ 🧡 💛 💚 💙 💜 🖤 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥 💫 💦 💨 🔥 ✨ ⭐ 🌟 ⚡ ✅ ❌ ❓ ❗ ⚠️ 🚫 ♻️ 🔞 ➕ ➖ ➗ ✖️ 💲",
        "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🦈 🐊 🌵 🌲 🌳 🌴 🌱 🌿 🍀 🍁 🍂 🌸 🌹 🌻 🌼 🌞 🌙 🌈 ☁️ ❄️ 🌊",
        "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🍍 🥝 🍅 🥑 🥦 🥕 🌽 🍞 🧀 🍳 🥞 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🍝 🍜 🍲 🍣 🍤 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🥛 🍺 🍻 🥂 🍷 🍸 🍹",
        "⚽ 🏀 🏈 ⚾ 🎾 🏐 🎱 🏓 🥊 🎯 🎮 🎲 🎵 🎶 🎤 🎧 🎸 🎹 🎬 📷 📱 💻 ⌨️ 🖥 🎁 🎉 🎊 🏆 🥇 🚗 🚕 🚌 🚓 🚑 ✈️ 🚀 🚲 🏠 🏢 ⏰ 📌 📎 ✏️ 📚 💡 🔑 🔒 💰 💳"
    ).map { it.split(" ") }

    private lateinit var pal: Palette
    private lateinit var store: ClipStore
    private lateinit var cm: ClipboardManager
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    private var lang = 0      // 0 = русский, 1 = английский
    private var page = 0      // 0 = буквы, 1 = символы 1, 2 = символы 2
    private var shift = 0     // 0 = выкл, 1 = на одну букву, 2 = Caps Lock
    private var lastShiftTap = 0L
    private var lastSpace = 0L
    private var mode = 0      // 0 = клавиши, 1 = буфер, 2 = эмодзи
    private var emojiCat = 0
    private var themeMode = 0
    private var heightMode = 1

    private var keysBox: LinearLayout? = null
    private var clipScroll: ScrollView? = null
    private var clipList: LinearLayout? = null
    private var emojiBox: LinearLayout? = null
    private var emojiTop: LinearLayout? = null
    private var emojiGrid: LinearLayout? = null
    private var clipBtn: Key? = null
    private var emojiBtn: Key? = null
    private var clearBtn: Key? = null
    private var titleView: TextView? = null
    private var shiftKey: Key? = null
    private var enterKey: Key? = null
    private val letterKeys = mutableListOf<Key>()

    // ---------- жизненный цикл ----------

    override fun onCreate() {
        super.onCreate()
        loadPrefs()
        pal = Palette(this, themeMode)
        store = ClipStore(this)
        cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        cm.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    private fun loadPrefs() {
        val ui = getSharedPreferences("ui", Context.MODE_PRIVATE)
        themeMode = ui.getInt("theme", 0)
        heightMode = ui.getInt("height", 1)
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        loadPrefs()
        pal = Palette(this, themeMode)
        return buildRoot()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val ui = getSharedPreferences("ui", Context.MODE_PRIVATE)
        if (ui.getInt("theme", 0) != themeMode || ui.getInt("height", 1) != heightMode) {
            loadPrefs()
            pal = Palette(this, themeMode)
            setInputView(buildRoot())
        }
        if (mode != 0) setMode(0)
        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        page = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) 1 else 0
        shift = 0
        renderKeys()
        afterInput()
        styleNavBar()
    }

    @Suppress("DEPRECATION")
    private fun styleNavBar() {
        val w = window?.window ?: return
        w.navigationBarColor = pal.bg
        val v = w.decorView
        v.systemUiVisibility = if (pal.night) {
            v.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        } else {
            v.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    // ---------- построение интерфейса ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rowH(): Int {
        val base = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 40 else 46
        val scale = when (heightMode) {
            0 -> 0.88f
            2 -> 1.15f
            else -> 1f
        }
        return dp((base * scale).toInt())
    }

    private fun ico(id: Int): Drawable? = getDrawable(id)?.mutate()

    private fun buildRoot(): View {
        mode = 0
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(pal.bg)
        root.setPadding(dp(4), dp(4), dp(4), dp(8))

        // верхняя панель: буфер, эмодзи, заголовок, «Очистить»
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        val cb = Key(this, pal, Key.FLAT).apply {
            icon = ico(R.drawable.ic_clipboard)
            onTap = { setMode(1) }
        }
        clipBtn = cb
        bar.addView(cb, LinearLayout.LayoutParams(dp(52), dp(40)))

        val emb = Key(this, pal, Key.FLAT).apply {
            icon = ico(R.drawable.ic_emoji)
            onTap = { setMode(2) }
        }
        emojiBtn = emb
        bar.addView(emb, LinearLayout.LayoutParams(dp(52), dp(40)))

        val tv = TextView(this)
        tv.setTextColor(pal.text)
        tv.textSize = 16f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setPadding(dp(8), 0, 0, 0)
        titleView = tv
        bar.addView(tv, LinearLayout.LayoutParams(0, WRAP, 1f))

        val cl = Key(this, pal, Key.FLAT).apply {
            label = "Очистить"
            textSp = 14f
            visibility = View.GONE
            onTap = {
                store.clearUnpinned()
                refreshClips()
            }
        }
        clearBtn = cl
        bar.addView(cl, LinearLayout.LayoutParams(dp(96), dp(40)))

        // область под панелью: клавиши, буфер или эмодзи
        val content = FrameLayout(this)
        val kb = LinearLayout(this)
        kb.orientation = LinearLayout.VERTICAL
        keysBox = kb

        val sv = ScrollView(this)
        sv.visibility = View.GONE
        sv.isVerticalScrollBarEnabled = false
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        sv.addView(list)
        clipScroll = sv
        clipList = list

        val ebox = LinearLayout(this)
        ebox.orientation = LinearLayout.VERTICAL
        ebox.visibility = View.GONE
        val etop = LinearLayout(this)
        etop.orientation = LinearLayout.HORIZONTAL
        val esv = ScrollView(this)
        esv.isVerticalScrollBarEnabled = false
        val egrid = LinearLayout(this)
        egrid.orientation = LinearLayout.VERTICAL
        esv.addView(egrid)
        ebox.addView(etop, LinearLayout.LayoutParams(MATCH, rowH()))
        ebox.addView(esv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        emojiBox = ebox
        emojiTop = etop
        emojiGrid = egrid

        content.addView(kb, FrameLayout.LayoutParams(MATCH, MATCH))
        content.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
        content.addView(ebox, FrameLayout.LayoutParams(MATCH, MATCH))

        root.addView(bar, LinearLayout.LayoutParams(MATCH, dp(40)))
        root.addView(content, LinearLayout.LayoutParams(MATCH, rowH() * 5))
        renderKeys()
        return root
    }

    private fun addRow(box: LinearLayout, keys: List<Key>, target: Float) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        var sum = 0f
        for (k in keys) sum += k.weight
        val pad = (target - sum) / 2f
        if (pad > 0.01f) r.addView(View(this), LinearLayout.LayoutParams(0, MATCH, pad))
        for (k in keys) r.addView(k, LinearLayout.LayoutParams(0, MATCH, k.weight))
        if (pad > 0.01f) r.addView(View(this), LinearLayout.LayoutParams(0, MATCH, pad))
        box.addView(r, LinearLayout.LayoutParams(MATCH, rowH()))
    }

    private fun charKey(ch: String, letter: Boolean, sp: Float = 22f, kind: Int = 0): Key {
        val k = Key(this, pal, kind)
        k.textSp = sp
        k.tag = ch
        k.preview = true
        k.label = if (letter) shifted(ch) else ch
        val alt = ALT[ch]
        if (alt != null) {
            k.hint = if (letter) shifted(alt) else alt
            k.onLong = { type(if (letter) shifted(alt) else alt) }
        }
        k.onTap = { type(if (letter) shifted(ch) else ch) }
        if (letter) letterKeys.add(k)
        return k
    }

    private fun backKey(w: Float) = Key(this, pal, Key.SPECIAL).apply {
        weight = w
        icon = ico(R.drawable.ic_backspace)
        repeatable = true
        onTap = { backspace() }
    }

    private fun showPicker() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun renderKeys() {
        val box = keysBox ?: return
        box.removeAllViews()
        letterKeys.clear()
        shiftKey = null
        val ru = lang == 0
        val rows = if (ru) ruRows else enRows
        val tgt = if (page == 0) rows[0].length.toFloat() else 10f

        // ряд цифр
        addRow(box, "1234567890".map { charKey(it.toString(), false, 18f) }, 10f)

        if (page == 0) {
            addRow(box, rows[0].map { charKey(it.toString(), true) }, tgt)
            addRow(box, rows[1].map { charKey(it.toString(), true) }, tgt)
            val sw = (tgt - rows[2].length) / 2f
            val sk = Key(this, pal, Key.SPECIAL).apply {
                weight = sw
                onTap = { onShift() }
            }
            shiftKey = sk
            addRow(box, listOf(sk) + rows[2].map { charKey(it.toString(), true) } + backKey(sw), tgt)
        } else {
            val s = if (page == 1) sym1 else sym2
            addRow(box, s[0].map { charKey(it.toString(), false) }, 10f)
            addRow(box, s[1].map { charKey(it.toString(), false) }, 10f)
            val tk = Key(this, pal, Key.SPECIAL).apply {
                weight = 1.5f
                textSp = 15f
                label = if (page == 1) "=\\<" else "?123"
                onTap = {
                    page = if (page == 1) 2 else 1
                    renderKeys()
                }
            }
            addRow(box, listOf(tk) + s[2].map { charKey(it.toString(), false) } + backKey(1.5f), 10f)
        }

        // нижний ряд
        val sym = Key(this, pal, Key.SPECIAL).apply {
            weight = 1.5f
            textSp = 15f
            label = if (page == 0) "?123" else if (ru) "АБВ" else "ABC"
            onTap = {
                page = if (page == 0) 1 else 0
                renderKeys()
            }
        }
        val comma = charKey(",", false, 22f, Key.SPECIAL)
        val globe = Key(this, pal, Key.SPECIAL).apply {
            icon = ico(R.drawable.ic_globe)
            onTap = {
                lang = 1 - lang
                page = 0
                renderKeys()
            }
            onLong = { showPicker() }
        }
        val space = Key(this, pal).apply {
            weight = tgt - 6f
            textSp = 13f
            label = if (ru) "Русский" else "English"
            onTap = { type(" ") }
        }
        val period = charKey(".", false, 22f, Key.SPECIAL)
        val ek = Key(this, pal, Key.ACTION).apply {
            weight = 1.5f
            textSp = 14f
            onTap = { enter() }
        }
        enterKey = ek
        addRow(box, listOf(sym, comma, globe, space, period, ek), tgt)

        applyShift()
        updateEnter()
    }

    // ---------- панели: буфер и эмодзи ----------

    private fun setMode(m: Int) {
        mode = if (mode == m) 0 else m   // повторное нажатие закрывает панель
        keysBox?.visibility = if (mode == 0) View.VISIBLE else View.GONE
        clipScroll?.visibility = if (mode == 1) View.VISIBLE else View.GONE
        emojiBox?.visibility = if (mode == 2) View.VISIBLE else View.GONE
        clipBtn?.active = mode == 1
        emojiBtn?.active = mode == 2
        clearBtn?.visibility = if (mode == 1) View.VISIBLE else View.GONE
        titleView?.text = when (mode) {
            1 -> "Буфер обмена"
            2 -> "Эмодзи"
            else -> ""
        }
        if (mode == 1) refreshClips()
        if (mode == 2) renderEmoji()
    }

    private fun renderEmoji() {
        val top = emojiTop ?: return
        val grid = emojiGrid ?: return
        top.removeAllViews()
        grid.removeAllViews()

        val back = Key(this, pal, Key.SPECIAL).apply {
            weight = 1.4f
            textSp = 14f
            label = if (lang == 0) "АБВ" else "ABC"
            onTap = { setMode(0) }
        }
        top.addView(back, LinearLayout.LayoutParams(0, MATCH, back.weight))
        for (i in emojiCats.indices) {
            val tab = Key(this, pal, Key.FLAT).apply {
                textSp = 20f
                haptic = false
                label = emojiCats[i][0]
                active = i == emojiCat
                onTap = {
                    emojiCat = i
                    renderEmoji()
                }
            }
            top.addView(tab, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        val del = backKey(1.4f)
        top.addView(del, LinearLayout.LayoutParams(0, MATCH, del.weight))

        val items = emojiCats[emojiCat]
        val perRow = 8
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val end = minOf(i + perRow, items.size)
            for (j in i until end) {
                val e = items[j]
                val k = Key(this, pal, Key.FLAT).apply {
                    textSp = 24f
                    label = e
                    haptic = false
                    onTap = { currentInputConnection?.commitText(e, 1) }
                }
                row.addView(k, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
            val missing = perRow - (end - i)
            if (missing > 0) row.addView(View(this), LinearLayout.LayoutParams(0, MATCH, missing.toFloat()))
            grid.addView(row, LinearLayout.LayoutParams(MATCH, rowH()))
            i += perRow
        }
    }

    // ---------- ввод ----------

    private fun shifted(s: String) = if (shift > 0) s.uppercase() else s

    private fun type(s: String) {
        val ic = currentInputConnection
        if (ic == null) return
        if (s == " ") {
            // двойной пробел после слова = точка и пробел
            val now = System.currentTimeMillis()
            if (now - lastSpace < 500) {
                val b = ic.getTextBeforeCursor(2, 0)
                if (b != null && b.length == 2 && b[1] == ' ' && b[0].isLetterOrDigit()) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastSpace = 0L
                    afterInput()
                    return
                }
            }
            lastSpace = now
        }
        ic.commitText(s, 1)
        afterInput()
    }

    private fun backspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        afterInput()
    }

    private fun enter() {
        val ei = currentInputEditorInfo
        val ic = currentInputConnection ?: return
        val act = if (ei == null) 0 else ei.imeOptions and EditorInfo.IME_MASK_ACTION
        val plain = ei == null ||
            (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            act == EditorInfo.IME_ACTION_NONE ||
            act == EditorInfo.IME_ACTION_UNSPECIFIED
        if (plain) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) else ic.performEditorAction(act)
        afterInput()
    }

    private fun updateEnter() {
        val k = enterKey ?: return
        val ei = currentInputEditorInfo
        val multi = ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val act = if (ei == null) 0 else ei.imeOptions and EditorInfo.IME_MASK_ACTION
        val text: String? = if (multi) null else when (act) {
            EditorInfo.IME_ACTION_GO -> "Вперёд"
            EditorInfo.IME_ACTION_SEARCH -> "Поиск"
            EditorInfo.IME_ACTION_SEND -> "Отпр."
            EditorInfo.IME_ACTION_NEXT -> "Далее"
            EditorInfo.IME_ACTION_DONE -> "Готово"
            else -> null
        }
        if (text == null) {
            k.label = ""
            k.icon = ico(R.drawable.ic_enter)
        } else {
            k.icon = null
            k.label = text
        }
    }

    private fun onShift() {
        val now = System.currentTimeMillis()
        shift = when {
            shift == 2 -> 0
            shift == 1 && now - lastShiftTap < 350 -> 2   // двойное нажатие = Caps Lock
            shift == 1 -> 0
            else -> 1
        }
        lastShiftTap = now
        applyShift()
    }

    /** Автоматическая заглавная в начале предложения (как в Gboard). */
    private fun afterInput() {
        if (shift == 2) return
        val ei = currentInputEditorInfo
        val caps = if (ei != null && ei.inputType != 0) {
            currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0
        } else 0
        val want = if (caps != 0) 1 else 0
        if (want != shift) {
            shift = want
            applyShift()
        }
    }

    private fun applyShift() {
        for (k in letterKeys) {
            val base = k.tag as String
            k.label = shifted(base)
            val a = ALT[base]
            k.hint = if (a != null) shifted(a) else null
        }
        shiftKey?.let {
            it.icon = ico(
                when (shift) {
                    0 -> R.drawable.ic_shift
                    1 -> R.drawable.ic_shift_on
                    else -> R.drawable.ic_caps
                }
            )
            it.active = shift > 0
        }
    }

    // ---------- буфер обмена ----------

    private fun captureClip() {
        try {
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            // Менеджеры паролей помечают секретное — такое не сохраняем
            if (clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (text.isBlank()) return
            store.add(text)
            if (mode == 1) refreshClips()
        } catch (e: Exception) {
        }
    }

    private fun refreshClips() {
        val list = clipList ?: return
        list.removeAllViews()
        val items = store.all()
        if (items.isEmpty()) {
            val t = TextView(this)
            t.text = "Пока пусто.\nСкопируй текст — он появится здесь.\nНажми на карточку, чтобы вставить; ★ — закрепить."
            t.setTextColor(pal.hint)
            t.textSize = 14f
            t.setPadding(dp(16), dp(24), dp(16), 0)
            list.addView(t)
            return
        }
        for (item in items) {
            val lp = LinearLayout.LayoutParams(MATCH, WRAP)
            lp.setMargins(dp(4), dp(3), dp(4), dp(3))
            list.addView(card(item), lp)
        }
    }

    private fun iconBtn(s: String, color: Int, action: () -> Unit): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = 18f
        t.setTextColor(color)
        t.gravity = Gravity.CENTER
        t.setOnClickListener { action() }
        return t
    }

    private fun card(item: ClipStore.Item): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14), dp(10), dp(6), dp(10))
        val bg = GradientDrawable()
        bg.setColor(pal.key)
        bg.cornerRadius = dp(16).toFloat()
        row.background = bg
        row.setOnClickListener {
            currentInputConnection?.commitText(item.text, 1)
            setMode(0)
            afterInput()
        }

        val tv = TextView(this)
        tv.text = item.text.trim()
        tv.setTextColor(pal.text)
        tv.textSize = 15f
        tv.maxLines = 3
        tv.ellipsize = TextUtils.TruncateAt.END
        row.addView(tv, LinearLayout.LayoutParams(0, WRAP, 1f))

        row.addView(
            iconBtn(if (item.pinned) "★" else "☆", if (item.pinned) pal.accent else pal.hint) {
                store.togglePin(item.text)
                refreshClips()
            },
            LinearLayout.LayoutParams(dp(40), dp(40))
        )
        row.addView(
            iconBtn("✕", pal.hint) {
                store.remove(item.text)
                refreshClips()
            },
            LinearLayout.LayoutParams(dp(40), dp(40))
        )
        return row
    }
}
