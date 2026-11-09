package com.pixelkeys

import kotlin.math.ln

/** Соответствие раскладок QWERTY и ЙЦУКЕН (для слов, набранных не в той раскладке). */
object Layout {
    private const val EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`"
    private const val RU = "йцукенгшщзхъфывапролджэячсмитьбюё"

    fun isLatinTokenChar(c: Char) = (c in 'a'..'z') || (c in 'A'..'Z') || ",.;'[]`".indexOf(c) >= 0
    fun isCyr(c: Char) = (c in 'а'..'я') || (c in 'А'..'Я') || c == 'ё' || c == 'Ё'

    fun toRu(s: String): String = map(s, EN, RU)
    fun toEn(s: String): String = map(s, RU, EN)

    private fun map(s: String, from: String, to: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val i = from.indexOf(ch.lowercaseChar())
            if (i < 0) {
                sb.append(ch)
                continue
            }
            val m = to[i]
            sb.append(if (ch.isUpperCase()) m.uppercaseChar() else m)
        }
        return sb.toString()
    }
}

/**
 * Умное автоисправление: опечатки (учитывает соседние клавиши и частоту слов)
 * и слова, набранные не в той раскладке ("ghbdtn" -> "привет"). Всё считается на телефоне.
 */
class Corrector(private val dict: Dict) {

    companion object {
        const val MARGIN = 0.8
    }

    private val ruAlpha = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
    private val enAlpha = "abcdefghijklmnopqrstuvwxyz"
    private val ruPhon = pairs("ао", "еи", "ея", "иы", "ъь", "сз", "тд", "пб", "фв", "гк", "жш", "чщ", "эе", "ёе", "ую")
    private val enPhon = pairs("ei", "ea", "ae", "ck", "ks", "oa")
    private fun pairs(vararg p: String): Set<String> {
        val out = HashSet<String>()
        for (x in p) {
            out.add(x)
            out.add("" + x[1] + x[0])
        }
        return out
    }
    private val ruNb = neighbors(listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю"), doubleArrayOf(0.0, 0.0, 1.0))
    private val enNb = neighbors(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), doubleArrayOf(0.0, 0.25, 0.75))

    private fun neighbors(rows: List<String>, offs: DoubleArray): Map<Char, String> {
        val pts = ArrayList<Triple<Char, Double, Double>>()
        for (r in rows.indices) for (c in rows[r].indices) pts.add(Triple(rows[r][c], c + offs[r], r.toDouble()))
        val out = HashMap<Char, String>()
        for (a in pts) {
            val sb = StringBuilder()
            for (b in pts) {
                if (a.first == b.first) continue
                val dx = a.second - b.second
                val dy = a.third - b.third
                if (dx * dx + dy * dy <= 1.7) sb.append(b.first)
            }
            out[a.first] = sb.toString()
        }
        return out
    }

    /** Все варианты в одну правку: удаление, перестановка, замена, вставка. pen = «штраф» правки. */
    private fun edits1(w: String, alpha: String, nb: Map<Char, String>, phon: Set<String>, full: Boolean, insert: Boolean, out: (String, Double) -> Unit) {
        val n = w.length
        for (i in 0 until n) {
            // лишняя удвоенная буква («helllo») — самая частая опечатка этого типа
            val dbl = (i > 0 && w[i] == w[i - 1]) || (i + 1 < n && w[i] == w[i + 1])
            out(w.substring(0, i) + w.substring(i + 1), if (dbl) 1.0 else 2.0)
        }
        for (i in 0 until n - 1) {
            if (w[i] != w[i + 1]) out(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2), 1.0)
        }
        for (i in 0 until n) {
            val ch = w[i]
            val near = nb[ch] ?: ""
            if (full) {
                for (a in alpha) {
                    if (a == ch) continue
                    val pen = if (near.indexOf(a) >= 0) 1.0 else if (phon.contains("" + ch + a)) 1.5 else 3.5
                    out(w.substring(0, i) + a + w.substring(i + 1), pen)
                }
            } else {
                for (a in near) out(w.substring(0, i) + a + w.substring(i + 1), 1.0)
            }
        }
        if (insert) {
            for (i in 0..n) for (a in alpha) {
                // пропущена вторая из двух одинаковых букв («helo» -> «hello»)
                val dbl = (i > 0 && w[i - 1] == a) || (i < n && w[i] == a)
                out(w.substring(0, i) + a + w.substring(i), if (dbl) 0.4 else 2.0)
            }
        }
    }

    private class Cand(val word: String, val score: Double)

    /** Лучшие варианты исправления с оценкой (чем больше, тем увереннее). */
    private fun search(w: String, lang: Int, allowD2: Boolean): List<Cand> {
        val alpha = if (lang == 0) ruAlpha else enAlpha
        val nb = if (lang == 0) ruNb else enNb
        val phon = if (lang == 0) ruPhon else enPhon
        val maxRank = if (lang == 0) 110000 else 55000     // в очень редкие слова не исправляем
        val found = HashMap<String, Double>()

        fun consider(c: String, pen: Double) {
            if (c == w) return
            val r = dict.rank(lang, c)
            if (r < 0 || r > maxRank) return
            val sc = -ln(r + 20.0) - pen
            val old = found[c]
            if (old == null || sc > old) found[c] = sc
        }

        edits1(w, alpha, nb, phon, true, true) { c, p -> consider(c, p) }
        var limit = -13.5
        if (found.isEmpty() && allowD2 && w.length in 5..11) {
            val first = ArrayList<Pair<String, Double>>()
            edits1(w, alpha, nb, phon, true, true) { c, p -> first.add(Pair(c, p)) }
            for ((s1, p1) in first) {
                edits1(s1, alpha, nb, phon, false, false) { c, p -> consider(c, p1 + p + 0.7) }
            }
            limit = -13.0
        }
        return found.entries.filter { it.value > limit }.map { Cand(it.key, it.value) }.sortedByDescending { it.score }.take(5)
    }

    /** Исправление опечатки в слове (строчными буквами). null, если исправлять нечего или нет уверенности. */
    fun fix(w: String, lang: Int): String? {
        val c = search(w, lang, true)
        if (c.isEmpty()) return null
        // если второй вариант почти так же хорош, не рискуем (его покажет T9 в капсуле)
        if (c.size > 1 && c[0].score - c[1].score < MARGIN) return null
        return c[0].word
    }

    /** Варианты для капсулы T9 (быстрый поиск, только в одну правку). */
    fun candidates(w: String, lang: Int, n: Int): List<String> =
        search(w, lang, false).take(n).map { it.word }

    private fun restoreCase(typed: String, s: String): String {
        if (typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } && typed.any { it.isLetter() }) return s.uppercase()
        if (typed.isNotEmpty() && typed[0].isUpperCase()) return s.replaceFirstChar { it.uppercase() }
        return s
    }

    /** Слово набрано не в той раскладке? Возвращает исправленное слово или null. */
    fun layoutFix(token: String): String? {
        if (token.length < 3) return null
        val low = token.lowercase()
        if (low.all { Layout.isLatinTokenChar(it) }) {
            val letters = low.filter { it in 'a'..'z' }
            if (letters.length < 2) return null
            if (dict.user.contains(low) || dict.known(1, letters)) return null
            val mapped = Layout.toRu(low)
            if (mapped.all { Layout.isCyr(it) } && mapped.length >= 3) {
                val r = dict.rank(0, mapped)
                if (r in 0..30000) return restoreCase(token, mapped)
            }
        } else if (low.all { Layout.isCyr(it) }) {
            if (dict.known(0, low)) return null
            val mapped = Layout.toEn(low)
            if (mapped.all { it in 'a'..'z' } && mapped.length >= 3) {
                val r = dict.rank(1, mapped)
                if (r in 0..25000) return restoreCase(token, mapped)
            }
        }
        return null
    }
}
