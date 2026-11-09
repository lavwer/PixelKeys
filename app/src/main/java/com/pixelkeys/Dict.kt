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

    private fun read(input: InputStream): Lang {
        val list = ArrayList<String>()
        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (ln in lines) {
                val w = ln.trim()
                if (w.isNotEmpty()) list.add(w)
            }
        }
        // слова храним по алфавиту (для быстрого поиска по началу), а частоту — отдельно
        val idx = list.indices.sortedBy { list[it] }
        val words = Array(list.size) { list[idx[it]] }
        val ranks = IntArray(list.size) { idx[it] }
        return Lang(words, ranks)
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
