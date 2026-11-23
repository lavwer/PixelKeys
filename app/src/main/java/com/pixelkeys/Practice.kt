package com.pixelkeys

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Вторая страница приложения: тренажёр набора (тест скорости, слова на время, реакция).
 * Всё считается на телефоне, сохраняются только три рекорда.
 */
class PracticePage(
    private val act: Activity,
    private val pal: Palette,
    private val prefs: SharedPreferences
) {
    private val h = Handler(Looper.getMainLooper())
    private val rnd = java.util.Random()
    private val err = if (pal.night) 0xFFF2B8B5.toInt() else 0xFFB3261E.toInt()
    private val errBg = (err and 0x00FFFFFF) or 0x55000000

    private val modeNames = listOf("Скорость", "Слова", "Реакция")
    private val langNames = listOf("Русский", "English")

    private val sentRu = listOf(
        "быстрая коричневая лиса прыгает через ленивую собаку",
        "утром я пью горячий чай и читаю новости",
        "хорошая клавиатура помогает печатать быстро и без ошибок",
        "завтра мы поедем за город встречать рассвет",
        "маленький кот тихо спит на мягком диване",
        "лучше один раз увидеть чем сто раз услышать",
        "в субботу друзья придут к нам на ужин"
    )
    private val sentEn = listOf(
        "the quick brown fox jumps over the lazy dog",
        "good typing is a habit built one letter at a time",
        "i like to drink hot tea and read the news in the morning",
        "tomorrow we will drive out of the city to watch the sunrise",
        "a small cat sleeps quietly on the soft sofa",
        "practice makes perfect so keep your hands relaxed",
        "my friends are coming over on saturday for dinner"
    )
    private val wordsRu = listOf(
        "клавиатура", "телефон", "сообщение", "привет", "погода", "дорога", "музыка", "фотография",
        "экран", "пиксель", "цвет", "скорость", "друзья", "вечер", "завтра", "работа", "стиль",
        "кнопка", "буква", "слово", "текст", "печать", "быстро", "точно", "ответ", "письмо",
        "чайник", "окно", "машина", "город", "книга", "ветер", "солнце", "молоко", "хлеб",
        "деревня", "компьютер", "интернет", "программа", "приложение"
    )
    private val wordsEn = listOf(
        "keyboard", "phone", "message", "hello", "weather", "road", "music", "photo",
        "screen", "pixel", "color", "speed", "friends", "evening", "tomorrow", "work", "style",
        "button", "letter", "word", "text", "typing", "quick", "exact", "answer", "mail",
        "kettle", "window", "machine", "city", "book", "wind", "sunshine", "milk", "bread",
        "village", "computer", "internet", "program", "application"
    )
    private val lettersRu = "йцукенгшщзхфывапролджэячсмитьбю"
    private val lettersEn = "qwertyuiopasdfghjklzxcvbnm"

    // ---- состояние ----
    private var mode = 0                 // 0 = скорость, 1 = слова, 2 = реакция
    private var lang = if (java.util.Locale.getDefault().language == "ru") 0 else 1
    private var running = false
    private var finished = false
    private var waiting = false
    private var ignoreChange = false
    private var startMs = 0L
    private var shownAt = 0L
    private var target = ""
    private var typedTotal = 0
    private var errors = 0
    private var score = 0
    private var combo = 0
    private var words = 0
    private var rIdx = 0
    private var reactSum = 0L
    private var reactMiss = 0

    // ---- виджеты ----
    private val modeBtns = ArrayList<TextView>()
    private val langBtns = ArrayList<TextView>()
    private lateinit var l1: TextView
    private lateinit var v1: TextView
    private lateinit var l2: TextView
    private lateinit var v2: TextView
    private lateinit var l3: TextView
    private lateinit var v3: TextView
    private lateinit var prev: TextView
    private lateinit var input: EditText
    private lateinit var startBtn: TextView
    private lateinit var msg: TextView
    private lateinit var bestTv: TextView

    // ---- вспомогательное оформление (ма3) ----
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

    private fun card(): LinearLayout {
        val c = LinearLayout(act)
        c.orientation = LinearLayout.VERTICAL
        c.background = Ui.raised(act, pal.container, pal.night, 28)
        c.elevation = 0f
        c.setPadding(dp(18), dp(8), dp(18), dp(8))
        return c
    }

    private fun ripple(v: View, radiusDp: Int) {
        val mask = GradientDrawable()
        mask.setColor(Color.WHITE)
        mask.cornerRadius = dp(radiusDp).toFloat()
        val tint = (pal.text and 0x00FFFFFF) or (0x1F shl 24)
        v.foreground = RippleDrawable(ColorStateList.valueOf(tint), null, mask)
    }

    private fun pillBtn(text: String, filled: Boolean, onClick: () -> Unit): TextView {
        val t = tv(text, 16f, if (filled) pal.onAction else pal.text, true)
        t.gravity = Gravity.CENTER
        t.background = Ui.key3d(act, if (filled) pal.action else pal.tonal, pal.night, 26)
        t.setPadding(dp(16), 0, dp(16), 0)
        t.elevation = 0f
        t.setOnClickListener { onClick() }
        ripple(t, 26)
        Ui.pressable(t)
        return t
    }

    private fun statBox(l: TextView, v: TextView): LinearLayout {
        val b = LinearLayout(act)
        b.orientation = LinearLayout.VERTICAL
        b.gravity = Gravity.CENTER
        b.setPadding(dp(8), dp(10), dp(8), dp(10))
        b.background = Ui.raised(act, pal.tonal, pal.night, 18)
        b.elevation = 0f
        l.gravity = Gravity.CENTER
        v.gravity = Gravity.CENTER
        b.addView(l)
        b.addView(v)
        return b
    }

    // ---- построение страницы ----
    fun view(): View {
        val sv = ScrollView(act)
        sv.setBackgroundColor(pal.surface)
        sv.isVerticalScrollBarEnabled = false
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(28), dp(16), dp(110))

        val ttl = tv("Тренажёр", 46f, pal.text)
        Ui.display(ttl, 46f)
        root.addView(ttl)
        val sub = tv("Проверь клавиатуру в деле: скорость, слова и реакция. Результаты остаются на телефоне.", 14f, pal.hint)
        sub.setPadding(0, dp(4), 0, 0)
        root.addView(sub)

        // режимы: сегментированная кнопка ма3
        val seg = LinearLayout(act)
        seg.orientation = LinearLayout.HORIZONTAL
        for (i in modeNames.indices) {
            val b = tv(modeNames[i], 14f, pal.text, true)
            b.gravity = Gravity.CENTER
            b.setOnClickListener { selectMode(i) }
            ripple(b, 22)
            val p = LinearLayout.LayoutParams(0, dp(44), 1f)
            if (i > 0) p.leftMargin = dp(6)
            seg.addView(b, p)
            modeBtns.add(b)
        }
        root.addView(seg, lp(20))

        // язык
        val seg2 = LinearLayout(act)
        seg2.orientation = LinearLayout.HORIZONTAL
        for (i in langNames.indices) {
            val b = tv(langNames[i], 13f, pal.text, true)
            b.gravity = Gravity.CENTER
            b.setOnClickListener { selectLang(i) }
            ripple(b, 19)
            val p = LinearLayout.LayoutParams(0, dp(38), 1f)
            if (i > 0) p.leftMargin = dp(6)
            seg2.addView(b, p)
            langBtns.add(b)
        }
        root.addView(seg2, lp(8))

        // главная карточка: показатели, превью, поле ввода
        val c = card()
        l1 = tv("", 11f, pal.hint)
        v1 = tv("", 18f, pal.text, true)
        l2 = tv("", 11f, pal.hint)
        v2 = tv("", 18f, pal.text, true)
        l3 = tv("", 11f, pal.hint)
        v3 = tv("", 18f, pal.text, true)
        val stats = LinearLayout(act)
        stats.orientation = LinearLayout.HORIZONTAL
        val boxes = listOf(statBox(l1, v1), statBox(l2, v2), statBox(l3, v3))
        for (i in boxes.indices) {
            val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) p.leftMargin = dp(6)
            stats.addView(boxes[i], p)
        }
        c.addView(stats, lp(14))

        prev = tv("", 22f, pal.hint)
        prev.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        prev.gravity = Gravity.CENTER
        prev.minHeight = dp(110)
        prev.setPadding(dp(4), dp(18), dp(4), dp(14))
        c.addView(prev, lp())

        input = EditText(act)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.hint = "Печатай здесь"
        input.setHintTextColor(pal.hint)
        input.setTextColor(pal.text)
        input.textSize = 18f
        input.background = Ui.inset(act, pal.surface, pal.night, 20)
        input.setPadding(dp(18), dp(14), dp(18), dp(14))
        input.addTextChangedListener(watcher)
        c.addView(input, lp(4, dp(56)))

        startBtn = pillBtn("Начать", true) { onStart() }
        c.addView(startBtn, lp(12, dp(52)))

        msg = tv("", 13f, pal.hint)
        msg.setPadding(0, dp(12), 0, dp(10))
        c.addView(msg, lp())
        root.addView(c, lp(16))

        // рекорды
        val rt = tv("Рекорды", 14f, pal.accent, true)
        rt.setPadding(dp(8), dp(24), 0, dp(8))
        root.addView(rt)
        val rc = card()
        bestTv = tv("", 15f, pal.text)
        bestTv.setLineSpacing(0f, 1.25f)
        bestTv.setPadding(0, dp(14), 0, dp(14))
        rc.addView(bestTv, lp())
        root.addView(rc, lp())

        val tip = tv("Совет: чтобы слова набирались без лишних подсказок, переключи язык клавиатуры на тот, что выбран выше (кнопка с глобусом).", 12f, pal.hint)
        tip.setPadding(dp(8), dp(14), dp(8), 0)
        root.addView(tip)

        sv.addView(root)
        reset()
        return sv
    }

    // ---- ввод ----
    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun afterTextChanged(s: Editable?) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (ignoreChange || s == null) return
            val text = s.toString()
            when (mode) {
                0 -> typedSpeed(text, start, count)
                1 -> typedWords(text, start, count)
                2 -> typedReact(text, count)
            }
        }
    }

    private fun clearInput() {
        ignoreChange = true
        input.setText("")
        ignoreChange = false
    }

    private fun postClear() {
        h.post { clearInput() }
    }

    private fun focusInput() {
        input.requestFocus()
        val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Считает набранные символы и ошибки относительно цели; возвращает число новых ошибок. */
    private fun countAdded(text: String, start: Int, count: Int): Int {
        var bad = 0
        if (count <= 0) return 0
        var i = start
        while (i < start + count && i < text.length) {
            typedTotal++
            val ch = if (i < target.length) target[i] else '\u0000'
            if (text[i] != ch) {
                errors++
                bad++
            }
            i++
        }
        return bad
    }

    /** Превью набора: верные буквы цветные, ошибки красные, следующая буква подсвечена. */
    private fun styled(t: String, typed: String): CharSequence {
        val sp = SpannableString(t)
        val n = minOf(typed.length, t.length)
        for (i in 0 until n) {
            if (typed[i] == t[i]) {
                sp.setSpan(ForegroundColorSpan(pal.accent), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                sp.setSpan(ForegroundColorSpan(err), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(BackgroundColorSpan(errBg), i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (n < t.length) {
            sp.setSpan(BackgroundColorSpan(pal.tonal), n, n + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(ForegroundColorSpan(pal.text), n, n + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sp
    }

    private fun pick(list: List<String>): String = list[rnd.nextInt(list.size)]

    // ---- режим 0: тест скорости ----
    private fun typedSpeed(text: String, start: Int, count: Int) {
        if (finished) return
        if (!running && text.isNotEmpty()) {
            running = true
            startMs = SystemClock.elapsedRealtime()
            h.post(tick)
        }
        countAdded(text, start, count)
        prev.text = styled(target, text)
        if (text.length >= target.length) finishSpeed()
    }

    private fun finishSpeed() {
        running = false
        finished = true
        h.removeCallbacks(tick)
        val el = SystemClock.elapsedRealtime() - startMs
        val sec = maxOf(0.5, el / 1000.0)
        val wpm = ((target.length / 5.0) / (sec / 60.0)).toInt()
        val acc = if (typedTotal == 0) 100 else maxOf(0, (typedTotal - errors) * 100 / typedTotal)
        v1.text = String.format("%.1f", el / 1000.0) + " с"
        v2.text = "$wpm сл/мин"
        v3.text = "$acc%"
        val old = prefs.getInt("best_wpm", 0)
        val rec = wpm > old && acc >= 85
        if (rec) prefs.edit().putInt("best_wpm", wpm).apply()
        msg.text = "Готово! $wpm сл/мин, точность $acc%." + (if (rec) " Новый рекорд!" else if (acc < 85) " Рекорд засчитывается при точности от 85%." else "")
        startBtn.text = "Ещё раз"
        updateBest()
    }

    // ---- режим 1: слова на время ----
    private fun nextWord() {
        val pool = if (lang == 0) wordsRu else wordsEn
        var w = pick(pool)
        if (w == target) w = pick(pool)
        target = w
        prev.text = styled(target, "")
    }

    private fun typedWords(text: String, start: Int, count: Int) {
        if (!running) {
            if (text.isNotEmpty()) postClear()
            return
        }
        val bad = countAdded(text, start, count)
        if (bad > 0) combo = 0
        prev.text = styled(target, text)
        if (text.equals(target, ignoreCase = true)) {
            words++
            combo++
            score += target.length + minOf(combo, 10)
            postClear()
            nextWord()
        }
        v2.text = score.toString()
        v3.text = combo.toString()
    }

    private fun finishWords() {
        running = false
        finished = true
        h.removeCallbacks(tick)
        v1.text = "0 с"
        val old = prefs.getInt("best_words", 0)
        val rec = score > old
        if (rec) prefs.edit().putInt("best_words", score).apply()
        val acc = if (typedTotal == 0) 100 else maxOf(0, (typedTotal - errors) * 100 / typedTotal)
        msg.text = "Время вышло! Слов: $words, очков: $score, точность $acc%." + (if (rec) " Новый рекорд!" else "")
        prev.text = "Конец"
        startBtn.text = "Ещё раз"
        postClear()
        updateBest()
    }

    // ---- режим 2: реакция ----
    private val showLetter = Runnable {
        val pool = if (lang == 0) lettersRu else lettersEn
        var c = pool[rnd.nextInt(pool.length)]
        if (target.isNotEmpty() && c == target[0]) c = pool[(pool.indexOf(c) + 1) % pool.length]
        target = c.toString()
        prev.text = target.uppercase()
        shownAt = SystemClock.elapsedRealtime()
        waiting = false
    }

    private fun nextLetterSoon() {
        waiting = true
        prev.text = "…"
        h.postDelayed(showLetter, (350 + rnd.nextInt(700)).toLong())
    }

    private fun typedReact(text: String, count: Int) {
        if (text.isEmpty()) return
        postClear()
        if (!running || waiting || count <= 0 || target.isEmpty()) return
        val c = text[text.length - 1]
        if (c.lowercaseChar() == target[0].lowercaseChar()) {
            val ms = SystemClock.elapsedRealtime() - shownAt
            reactSum += ms
            rIdx++
            v1.text = "$rIdx/10"
            v2.text = (reactSum / rIdx).toString() + " мс"
            if (rIdx >= 10) finishReact() else nextLetterSoon()
        } else {
            reactMiss++
            v3.text = reactMiss.toString()
        }
    }

    private fun finishReact() {
        running = false
        finished = true
        val avg = (reactSum / 10).toInt()
        v2.text = "$avg мс"
        val old = prefs.getInt("best_react", 0)
        val rec = old == 0 || avg < old
        if (rec) prefs.edit().putInt("best_react", avg).apply()
        msg.text = "Готово! Среднее $avg мс, промахов: $reactMiss." + (if (rec) " Новый рекорд!" else "")
        prev.text = "✓"
        startBtn.text = "Ещё раз"
        updateBest()
    }

    // ---- таймер ----
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val el = SystemClock.elapsedRealtime() - startMs
            if (mode == 0) {
                v1.text = String.format("%.1f", el / 1000.0) + " с"
                val len = input.text.length
                if (el > 1500 && len > 0) {
                    v2.text = ((len / 5.0) / (el / 60000.0)).toInt().toString() + " сл/мин"
                }
            } else if (mode == 1) {
                val left = 30000L - el
                if (left <= 0L) {
                    finishWords()
                    return
                }
                v1.text = ((left + 999L) / 1000L).toString() + " с"
            }
            h.postDelayed(this, 100)
        }
    }

    // ---- управление ----
    private fun selectMode(i: Int) {
        if (i == mode) return
        mode = i
        reset()
    }

    private fun selectLang(i: Int) {
        if (i == lang) return
        lang = i
        reset()
    }

    private fun onStart() {
        reset()
        if (mode == 1) {
            running = true
            startMs = SystemClock.elapsedRealtime()
            nextWord()
            h.post(tick)
            startBtn.text = "Заново"
        } else if (mode == 2) {
            running = true
            nextLetterSoon()
            startBtn.text = "Заново"
        }
        focusInput()
    }

    /** Возвращает страницу в исходное состояние выбранного режима. */
    private fun reset() {
        h.removeCallbacksAndMessages(null)
        running = false
        finished = false
        waiting = false
        typedTotal = 0
        errors = 0
        score = 0
        combo = 0
        words = 0
        rIdx = 0
        reactSum = 0L
        reactMiss = 0
        clearInput()
        when (mode) {
            0 -> {
                target = pick(if (lang == 0) sentRu else sentEn)
                prev.textSize = 22f
                prev.text = styled(target, "")
                l1.text = "Время"
                v1.text = "0,0 с"
                l2.text = "Скорость"
                v2.text = "—"
                l3.text = "Точность"
                v3.text = "—"
                msg.text = "Набери фразу целиком. Время пойдёт с первой буквы."
                startBtn.text = "Новая фраза"
            }
            1 -> {
                target = ""
                prev.textSize = 34f
                prev.text = "30 секунд"
                l1.text = "Время"
                v1.text = "30 с"
                l2.text = "Очки"
                v2.text = "0"
                l3.text = "Серия"
                v3.text = "0"
                msg.text = "Набирай слова одно за другим. Чем длиннее серия без ошибок, тем больше очков."
                startBtn.text = "Начать"
            }
            else -> {
                target = ""
                prev.textSize = 56f
                prev.text = "10 букв"
                l1.text = "Раунд"
                v1.text = "0/10"
                l2.text = "Среднее"
                v2.text = "—"
                l3.text = "Промахи"
                v3.text = "0"
                msg.text = "Нажимай ту букву, которая появилась, как можно быстрее."
                startBtn.text = "Начать"
            }
        }
        prev.setTextColor(pal.hint)
        updateBest()
        updateSegments()
    }

    private fun updateSegments() {
        for (i in modeBtns.indices) {
            val sel = i == mode
            modeBtns[i].text = (if (sel) "✓ " else "") + modeNames[i]
            modeBtns[i].background = if (sel) Ui.raised(act, Ui.inkBg(pal.night), pal.night, 22) else round(pal.container, 22)
            modeBtns[i].setTextColor(if (sel) Ui.inkFg(pal.night) else pal.hint)
            modeBtns[i].elevation = 0f
        }
        for (i in langBtns.indices) {
            val sel = i == lang
            langBtns[i].text = (if (sel) "✓ " else "") + langNames[i]
            langBtns[i].background = if (sel) Ui.raised(act, Ui.inkBg(pal.night), pal.night, 19) else round(pal.container, 19)
            langBtns[i].setTextColor(if (sel) Ui.inkFg(pal.night) else pal.hint)
            langBtns[i].elevation = 0f
        }
    }

    private fun updateBest() {
        val w = prefs.getInt("best_wpm", 0)
        val s = prefs.getInt("best_words", 0)
        val r = prefs.getInt("best_react", 0)
        val a = if (w > 0) "$w сл/мин" else "—"
        val b = if (s > 0) "$s очков" else "—"
        val c = if (r > 0) "$r мс" else "—"
        bestTv.text = "Скорость: $a\nСлова: $b\nРеакция: $c"
    }

    /** Вызывается при уходе со страницы: останавливает таймеры и убирает клавиатуру. */
    fun stop() {
        val wasActive = running || waiting
        h.removeCallbacksAndMessages(null)
        try {
            val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(input.windowToken, 0)
        } catch (e: Exception) {
        }
        if (wasActive) reset()
    }
}
