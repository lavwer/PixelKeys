package com.pixelkeys

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build

/**
 * Цвета клавиатуры.
 * mode: 0 = как в системе (Material You из обоев), 1 = светлая, 2 = тёмная,
 *       3..10 = цветные темы (10 = свой цвет: оттенок и насыщенность в настройках) (серая, синяя, бирюзовая, зелёная, фиолетовая, розовая, оранжевая).
 * Цветные темы сами переключаются между светлым и тёмным вариантом вслед за системой.
 */
class Palette(private val ctx: Context, mode: Int = 0, ignorePhoto: Boolean = false) {

    companion object {
        // порядок совпадает с названиями в SettingsActivity (индекс = mode - 3)
        private val HUES = floatArrayOf(220f, 215f, 180f, 135f, 270f, 330f, 28f, 210f)   // последний = свой цвет
        private val SATS = floatArrayOf(0.12f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)

        /** Подпись всего оформления: когда она меняется, клавиатура пересобирается. */
        fun signature(ctx: Context): String {
            val sp = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
            val keys = listOf("theme", "height", "radius", "keyalpha", "textsize", "gap", "vibrate", "sound", "bgblur", "bgdim",
                "font", "shape", "morph", "hints", "bubble", "bpad", "hpad", "hue", "sat", "fxon", "fx", "dev", "devrow", "dh", "rainbow", "fxdur", "fxpow", "aggr", "ny", "nygar", "nysnow", "nyfx", "nytheme")
            return keys.joinToString(",") { sp.getInt(it, -1).toString() } + "," + BgStore.stamp(ctx)
        }
    }

    private val sp = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    val hasPhoto = !ignorePhoto && BgStore.exists(ctx)
    val radiusPct = sp.getInt("radius", 50).coerceIn(0, 100)          // скругление клавиш
    val keyAlpha = sp.getInt("keyalpha", 100).coerceIn(30, 100) * 255 / 100   // прозрачность клавиш
    val textScale = sp.getInt("textsize", 100).coerceIn(70, 140) / 100f        // размер букв
    val gapDp = sp.getInt("gap", 3).coerceIn(0, 8).toFloat()                    // расстояние между клавишами
    val vibrate = sp.getInt("vibrate", 0) == 0
    val sound = sp.getInt("sound", 1) == 0

    // ---- новый год ----
    val ny = sp.getInt("ny", 1) == 0                                  // новогодний режим
    val nyGarland = ny && sp.getInt("nygar", 0) == 0                  // гирлянда и мишура сверху
    val nySnow = ny && sp.getInt("nysnow", 0) == 0                    // лёгкий снегопад
    val nyFx = if (ny) sp.getInt("nyfx", 1).coerceIn(0, 5) else 0     // 0 = как в настройках, 1..5 = новогодние эффекты
    private val festive = ny && sp.getInt("nytheme", 0) == 0          // ёлочные цвета

    // ---- кастом в стиле ма3 ----
    // эффект после нажатия: 0 = нет, 1 = волна, 2 = кольцо, 3 = свечение, 4 = пиксели, 5 = искры
    val dev = sp.getInt("dev", 0) == 1                              // режим разработчика
    val effect = if (nyFx > 0) 8 + nyFx else if (sp.getInt("fxon", 1) != 0) 0 else sp.getInt("fx", 0).coerceIn(0, if (dev) 7 else 4) + 1
    val font = sp.getInt("font", 0).coerceIn(0, 2)                 // 0 = обычное, 1 = среднее, 2 = жирное
    val keyShape = sp.getInt("shape", 0).coerceIn(0, 2)            // служебные клавиши: 0 = волнистая, 1 = скруглённая, 2 = капсула
    val morphOn = sp.getInt("morph", 0) == 0                       // клавиша при нажатии становится капсулой
    val showHints = sp.getInt("hints", 0) == 0                     // мелкие символы в углу клавиш
    val previewBubble = sp.getInt("bubble", 0) == 0                // увеличенная буква над клавишей
    val bpadDp = sp.getInt("bpad", 8).coerceIn(0, 32)              // отступ снизу
    val hpadDp = sp.getInt("hpad", 4).coerceIn(0, 16)              // отступы по бокам

    // ---- режим разработчика: всё это действует, только пока он включён ----
    val devRowOn = dev && sp.getInt("devrow", 1) == 0                // ряд программиста: Tab, стрелки, скобки
    val devRowDp = if (dev) sp.getInt("dh", 0).coerceIn(0, 110) else 0   // точная высота ряда, 0 = обычная
    val rainbow = dev && sp.getInt("rainbow", 1) == 0               // радужные клавиши
    val fxDur = if (dev) sp.getInt("fxdur", 420).coerceIn(120, 1600) else 420
    val fxPow = if (dev) sp.getInt("fxpow", 100).coerceIn(20, 100) else 100
    val aggr = if (dev) sp.getInt("aggr", 5).coerceIn(1, 9) else 5   // смелость автоисправления

    /** Шрифт клавиш: medium = служебные клавиши в ма3 всегда средние, если не выбрано жирное. */
    fun typeface(medium: Boolean): Typeface = when {
        font == 2 -> Typeface.DEFAULT_BOLD
        font == 1 || medium -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else -> Typeface.DEFAULT
    }

    // с фото на фоне клавиатура всегда тёмная (если не выбрана светлая тема): так буквы читаются
    val night = if (hasPhoto && mode != 1) true else when (mode) {
        1 -> false
        2 -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
    private val themed = mode >= 3
    private val dyn = Build.VERSION.SDK_INT >= 31 && !themed
    private val idx = (mode - 3).coerceIn(0, 7)
    private val hue = if (mode == 10) sp.getInt("hue", 210).coerceIn(0, 359).toFloat() else HUES[idx]
    private val satK = if (mode == 10) sp.getInt("sat", 100).coerceIn(20, 100) / 100f else SATS[idx]

    private fun c(id: Int) = ctx.getColor(id)
    private fun s(hex: String) = Color.parseColor(hex)
    private fun hsv(sat: Float, v: Float) = Color.HSVToColor(floatArrayOf(hue, (sat * satK).coerceIn(0f, 1f), v))
    private fun f(dark: String, light: String) = Color.parseColor(if (night) dark else light)

    val bg = if (festive) f("#0B2B20", "#F2F8F3") else if (themed) (if (night) hsv(0.45f, 0.16f) else hsv(0.18f, 0.93f))
             else if (dyn) c(if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral2_50)
             else s(if (night) "#1E2024" else "#E8ECF3")
    val key = if (festive) f("#1E4D3A", "#FFFFFF") else if (themed) (if (night) hsv(0.35f, 0.34f) else hsv(0.05f, 1.0f))
              else if (dyn) c(if (night) android.R.color.system_neutral2_700 else android.R.color.system_neutral1_0)
              else s(if (night) "#3B3E45" else "#FFFFFF")
    val keyA = Color.argb(keyAlpha, Color.red(key), Color.green(key), Color.blue(key))
    val special = if (festive) f("#6E1B2B", "#F8D3D8") else if (themed) (if (night) hsv(0.45f, 0.24f) else hsv(0.25f, 0.86f))
                  else if (dyn) c(if (night) android.R.color.system_accent2_800 else android.R.color.system_accent2_100)
                  else s(if (night) "#2B2D33" else "#D3DAE8")
    val pressed = if (festive) f("#2D6B52", "#CBE5D3") else if (themed) (if (night) hsv(0.35f, 0.46f) else hsv(0.32f, 0.78f))
                  else if (dyn) c(if (night) android.R.color.system_neutral2_600 else android.R.color.system_neutral2_300)
                  else s(if (night) "#565A63" else "#C3CCDF")
    // нажатая клавиша: в динамической теме подкрашивается акцентным цветом (как в Gboard на Pixel)
    val keyDown = if (festive) f("#6B5A1E", "#FFE29A") else if (themed) pressed
                  else if (dyn) c(if (night) android.R.color.system_accent1_700 else android.R.color.system_accent1_100)
                  else pressed
    val text = if (festive) f("#F4FAF6", "#15301F") else if (themed) (if (night) hsv(0.08f, 0.95f) else hsv(0.6f, 0.15f))
               else if (dyn) c(if (night) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900)
               else s(if (night) "#E3E3E8" else "#1B1B1F")
    val hint = if (festive) f("#9CC9B2", "#4D7A62") else if (themed) (if (night) hsv(0.2f, 0.7f) else hsv(0.3f, 0.45f))
               else if (dyn) c(if (night) android.R.color.system_neutral2_300 else android.R.color.system_neutral2_600)
               else s(if (night) "#9AA0AB" else "#6F7380")
    val action = if (festive) f("#FFC93C", "#C62839") else if (themed) (if (night) hsv(0.4f, 0.95f) else hsv(0.45f, 0.98f))
                 else if (dyn) c(android.R.color.system_accent1_200)
                 else s("#A8C7FA")
    val onAction = if (festive) f("#3A2800", "#FFFFFF") else if (themed) (if (night) hsv(0.9f, 0.22f) else hsv(0.8f, 0.25f))
                   else if (dyn) c(android.R.color.system_accent1_900)
                   else s("#062E6F")
    val accent = if (festive) f("#FFC93C", "#B71C2C") else if (themed) (if (night) hsv(0.45f, 1.0f) else hsv(0.8f, 0.65f))
                 else if (dyn) c(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
                 else s(if (night) "#8AB4F8" else "#1A5FC8")

    // ---- роли для экрана настроек (Material 3): фон страницы, карточки, тональные элементы, линии ----
    val surface = if (festive) f("#0B2B20", "#F7FBF8") else if (themed) bg
                  else if (dyn) c(if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_10)
                  else bg
    val container = if (festive) f("#14392B", "#E7F2EA") else if (themed) key
                    else if (dyn) c(if (night) android.R.color.system_neutral1_800 else android.R.color.system_neutral2_50)
                    else key
    val tonal = if (festive) f("#6E1B2B", "#FBD9DD") else if (themed) special
                else if (dyn) c(if (night) android.R.color.system_accent2_700 else android.R.color.system_accent2_100)
                else special
    val line = if (festive) f("#2E5A47", "#C9DDD0") else if (themed) special
               else if (dyn) c(if (night) android.R.color.system_neutral2_700 else android.R.color.system_neutral2_200)
               else special
}
