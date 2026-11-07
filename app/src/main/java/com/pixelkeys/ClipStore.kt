package com.pixelkeys

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * История буфера обмена. Хранится только в закрытой памяти приложения на самом телефоне.
 * Незакреплённые записи автоматически удаляются через TTL_MS.
 */
class ClipStore(ctx: Context) {

    class Item(val text: String, val time: Long, val pinned: Boolean)

    companion object {
        const val TTL_MS = 24L * 60 * 60 * 1000   // сколько живёт незакреплённая запись (сейчас 24 часа)
        const val MAX_ITEMS = 30                  // максимум незакреплённых записей
        const val MAX_LEN = 10_000                // слишком длинные тексты не сохраняем
    }

    private val prefs = ctx.getSharedPreferences("clips", Context.MODE_PRIVATE)

    private fun load(): MutableList<Item> {
        val out = mutableListOf<Item>()
        try {
            val arr = JSONArray(prefs.getString("items", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Item(o.getString("t"), o.getLong("ts"), o.optBoolean("p", false)))
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun save(list: List<Item>) {
        val arr = JSONArray()
        for (x in list) arr.put(JSONObject().put("t", x.text).put("ts", x.time).put("p", x.pinned))
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun all(): List<Item> {
        val now = System.currentTimeMillis()
        val list = load()
        val kept = list.filter { it.pinned || now - it.time < TTL_MS }
        if (kept.size != list.size) save(kept)
        return kept.sortedWith(compareByDescending<Item> { it.pinned }.thenByDescending { it.time })
    }

    fun add(text: String) {
        if (text.length > MAX_LEN) return
        val list = load()
        val old = list.firstOrNull { it.text == text }
        list.removeAll { it.text == text }
        list.add(0, Item(text, System.currentTimeMillis(), old?.pinned ?: false))
        var count = 0
        val trimmed = list.filter {
            if (it.pinned) true else {
                count++
                count <= MAX_ITEMS
            }
        }
        save(trimmed)
    }

    fun togglePin(text: String) {
        save(load().map { if (it.text == text) Item(it.text, it.time, !it.pinned) else it })
    }

    fun remove(text: String) {
        save(load().filter { it.text != text })
    }

    fun clearUnpinned() {
        save(load().filter { it.pinned })
    }

    fun clearAll() {
        prefs.edit().remove("items").apply()
    }
}
