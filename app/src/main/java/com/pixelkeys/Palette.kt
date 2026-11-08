package com.pixelkeys

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build

/** Цвета в стиле Pixel: на Android 12+ берутся из обоев (Material You), иначе — запасная палитра. */
class Palette(private val ctx: Context, mode: Int = 0) {
    // mode: 0 = как в системе, 1 = светлая, 2 = тёмная
    val night = when (mode) {
        1 -> false
        2 -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
    private val dyn = Build.VERSION.SDK_INT >= 31

    private fun c(id: Int) = ctx.getColor(id)
    private fun s(hex: String) = Color.parseColor(hex)

    val bg = if (dyn) c(if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral2_100)
             else s(if (night) "#1E2024" else "#E8ECF3")
    val key = if (dyn) c(if (night) android.R.color.system_neutral2_700 else android.R.color.system_neutral1_10)
              else s(if (night) "#3B3E45" else "#FFFFFF")
    val special = if (dyn) c(if (night) android.R.color.system_neutral2_800 else android.R.color.system_neutral2_200)
                  else s(if (night) "#2B2D33" else "#D3DAE8")
    val pressed = if (dyn) c(if (night) android.R.color.system_neutral2_600 else android.R.color.system_neutral2_300)
                  else s(if (night) "#565A63" else "#C3CCDF")
    val text = if (dyn) c(if (night) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900)
               else s(if (night) "#E3E3E8" else "#1B1B1F")
    val hint = if (dyn) c(if (night) android.R.color.system_neutral2_300 else android.R.color.system_neutral2_600)
               else s(if (night) "#9AA0AB" else "#6F7380")
    val action = if (dyn) c(android.R.color.system_accent1_200) else s("#A8C7FA")
    val onAction = if (dyn) c(android.R.color.system_accent1_900) else s("#062E6F")
    val accent = if (dyn) c(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
                 else s(if (night) "#8AB4F8" else "#1A5FC8")
}
