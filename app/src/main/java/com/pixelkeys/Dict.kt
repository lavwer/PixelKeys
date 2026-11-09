package com.pixelkeys

import android.content.res.AssetManager
import java.io.InputStream

/**
 * Словарь для подсказок T9. Файлы words_ru.txt / words_en.txt лежат внутри приложения
 * (одно слово на строку, от частых к редким). Интернет не используется.
 */
class Dict {

    private class Lang(val words: Array<String>, val ranks: IntArray)

    @Volatile private var ru: Lang? = null
    @Volatile private var en: Lang? = null

    /** Слова, которые пользователь «оставил» (отменил автоисправление). Хранятся только на телефоне. */
    val user = HashSet<String>()

    fun ready() = ru != null && en != null

    /** Место слова в частотном списке (0 = самое частое) или -1, если слова нет. */
    fun rank(lang: Int, w: String): Int {
        val l = (if (lang == 0) ru else en) ?: return -1
        val i = java.util.Arrays.binarySearch(l.words, w)
        return if (i >= 0) l.ranks[i] else -1
    }

    fun known(lang: Int, w: String) = rank(lang, w) >= 0 || user.contains(w)

    fun load(am: AssetManager) {
        try { ru = read(am.open("words_ru.txt")) } catch (e: Exception) { }
        try { en = read(am.open("words_en.txt")) } catch (e: Exception) { }
    }

    fun loadStreams(ruIn: InputStream, enIn: InputStream) {
        ru = read(ruIn)
        en = read(enIn)
    }

    /**
     * Файл словаря: на каждой строке «слово<TAB>место в частотном списке», строки уже отсортированы по алфавиту
     * (поэтому при загрузке сортировать не нужно).
     */
    private fun read(input: InputStream): Lang {
        var words = arrayOfNulls<String>(1 shl 17)
        var ranks = IntArray(1 shl 17)
        var n = 0
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (ln in lines) {
                val t = ln.indexOf('\t')
                if (t <= 0) continue
                val r = ln.substring(t + 1).trim().toIntOrNull() ?: continue
                if (n == words.size) {
                    words = words.copyOf(n * 2)
                    ranks = ranks.copyOf(n * 2)
                }
                words[n] = ln.substring(0, t)
                ranks[n] = r
                n++
            }
        }
        val w = Array(n) { words[it]!! }
        val r = ranks.copyOf(n)
        // на всякий случай проверяем порядок (поиск по словарю работает только на отсортированном списке)
        var sorted = true
        for (i in 1 until n) {
            if (w[i - 1] >= w[i]) {
                sorted = false
                break
            }
        }
        if (sorted) return Lang(w, r)
        val idx = (0 until n).sortedBy { w[it] }
        return Lang(Array(n) { w[idx[it]] }, IntArray(n) { r[idx[it]] })
    }

    /** До n самых частых слов, начинающихся с prefix (само prefix не возвращается). lang: 0 = рус, 1 = англ. */
    fun suggest(lang: Int, prefix: String, n: Int, includeExact: Boolean = false): List<String> {
        val l = (if (lang == 0) ru else en) ?: return emptyList()
        val p = prefix.lowercase()
        var lo = 0
        var hi = l.words.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (l.words[mid] < p) lo = mid + 1 else hi = mid
        }
        val bw = arrayOfNulls<String>(n)
        val br = IntArray(n) { Int.MAX_VALUE }
        var i = lo
        while (i < l.words.size && l.words[i].startsWith(p)) {
            val w = l.words[i]
            val rk = l.ranks[i]
            if ((includeExact || w != p) && rk < br[n - 1]) {
                var j = n - 1
                while (j > 0 && br[j - 1] > rk) {
                    br[j] = br[j - 1]
                    bw[j] = bw[j - 1]
                    j--
                }
                br[j] = rk
                bw[j] = w
            }
            i++
        }
        return bw.filterNotNull()
    }
}
