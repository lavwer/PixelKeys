package com.pixelkeys

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build

/**
 * Цвета клавиатуры.
 * mode: 0 = как в системе (Material You из обоев), 1 = светлая, 2 = тёмная,
 *       3..9 = цветные темы (серая, синяя, бирюзовая, зелёная, фиолетовая, розовая, оранжевая).
 * Цветные темы сами переключаются между светлым и тёмным вариантом вслед за системой.
 */
class Palette(private val ctx: Context, mode: Int = 0, ignorePhoto: Boolean = false) {

    companion object {
        // порядок совпадает с названиями в SettingsActivity (индекс = mode - 3)
        private val HUES = floatArrayOf(220f, 215f, 180f, 135f, 270f, 330f, 28f)
        private val SATS = floatArrayOf(0.12f, 1f, 1f, 1f, 1f, 1f, 1f)

        /** Подпись всего оформления: когда она меняется, клавиатура пересобирается. */
        fun signature(ctx: Context): String {
            val sp = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
            val keys = listOf("theme", "height", "radius", "keyalpha", "textsize", "gap", "vibrate", "sound", "bgblur", "bgdim")
            return keys.joinToString(",") { sp.getInt(it, -1).toString() } + "," + BgStore.stamp(ctx)
        }
    }

    private val sp = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    val hasPhoto = !ignorePhoto && BgStore.exists(ctx)
    val radiusPct = sp.getInt("radius", 30).coerceIn(0, 100)          // скругление клавиш
    val keyAlpha = sp.getInt("keyalpha", 100).coerceIn(30, 100) * 255 / 100   // прозрачность клавиш
    val textScale = sp.getInt("textsize", 100).coerceIn(70, 140) / 100f        // размер букв
    val gapDp = sp.getInt("gap", 3).coerceIn(0, 8).toFloat()                    // расстояние между клавишами
    val vibrate = sp.getInt("vibrate", 0) == 0
    val sound = sp.getInt("sound", 1) == 0

    // с фото на фоне клавиатура всегда тёмная (если не выбрана светлая тема): так буквы читаются
    val night = if (hasPhoto && mode != 1) true else when (mode) {
        1 -> false
        2 -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
    private val themed = mode >= 3
    private val dyn = Build.VERSION.SDK_INT >= 31 && !themed
    private val idx = (mode - 3).coerceIn(0, 6)
    private val hue = HUES[idx]
    private val satK = SATS[idx]

    private fun c(id: Int) = ctx.getColor(id)
    private fun s(hex: String) = Color.parseColor(hex)
    private fun hsv(sat: Float, v: Float) = Color.HSVToColor(floatArrayOf(hue, (sat * satK).coerceIn(0f, 1f), v))

    val bg = if (themed) (if (night) hsv(0.45f, 0.16f) else hsv(0.18f, 0.93f))
             else if (dyn) c(if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral2_100)
             else s(if (night) "#1E2024" else "#E8ECF3")
    val key = if (themed) (if (night) hsv(0.35f, 0.34f) else hsv(0.05f, 1.0f))
              else if (dyn) c(if (night) android.R.color.system_neutral2_700 else android.R.color.system_neutral1_10)
              else s(if (night) "#3B3E45" else "#FFFFFF")
    val keyA = Color.argb(keyAlpha, Color.red(key), Color.green(key), Color.blue(key))
    val special = if (themed) (if (night) hsv(0.45f, 0.24f) else hsv(0.25f, 0.86f))
                  else if (dyn) c(if (night) android.R.color.system_neutral2_800 else android.R.color.system_neutral2_200)
                  else s(if (night) "#2B2D33" else "#D3DAE8")
    val pressed = if (themed) (if (night) hsv(0.35f, 0.46f) else hsv(0.32f, 0.78f))
                  else if (dyn) c(if (night) android.R.color.system_neutral2_600 else android.R.color.system_neutral2_300)
                  else s(if (night) "#565A63" else "#C3CCDF")
    val text = if (themed) (if (night) hsv(0.08f, 0.95f) else hsv(0.6f, 0.15f))
               else if (dyn) c(if (night) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900)
               else s(if (night) "#E3E3E8" else "#1B1B1F")
    val hint = if (themed) (if (night) hsv(0.2f, 0.7f) else hsv(0.3f, 0.45f))
               else if (dyn) c(if (night) android.R.color.system_neutral2_300 else android.R.color.system_neutral2_600)
               else s(if (night) "#9AA0AB" else "#6F7380")
    val action = if (themed) (if (night) hsv(0.4f, 0.95f) else hsv(0.45f, 0.98f))
                 else if (dyn) c(android.R.color.system_accent1_200)
                 else s("#A8C7FA")
    val onAction = if (themed) (if (night) hsv(0.9f, 0.22f) else hsv(0.8f, 0.25f))
                   else if (dyn) c(android.R.color.system_accent1_900)
                   else s("#062E6F")
    val accent = if (themed) (if (night) hsv(0.45f, 1.0f) else hsv(0.8f, 0.65f))
                 else if (dyn) c(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
                 else s(if (night) "#8AB4F8" else "#1A5FC8")
}
