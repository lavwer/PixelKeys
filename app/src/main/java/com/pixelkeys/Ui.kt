package com.pixelkeys

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View

/**
 * Объём в стиле ма3: поверхности с мягким градиентом и светлой кромкой, подъём с тенью,
 * кнопки как клавиши с нижним бортиком, вдавленные поля ввода и «пружинка» при нажатии.
 */
object Ui {
    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /** Смешивает цвет a с цветом b: t = 0 даёт a, t = 1 даёт b. Прозрачность берётся от a. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.argb(Color.alpha(a), r, g, bl)
    }

    /** Приподнятая поверхность: светлее сверху, чуть темнее снизу, тонкая светлая кромка. */
    fun raised(ctx: Context, base: Int, night: Boolean, radiusDp: Int): GradientDrawable {
        val top = mix(base, Color.WHITE, if (night) 0.08f else 0.5f)
        val bottom = mix(base, Color.BLACK, if (night) 0.14f else 0.05f)
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, base, bottom))
        g.cornerRadius = dp(ctx, radiusDp).toFloat()
        g.setStroke(dp(ctx, 1), mix(base, Color.WHITE, if (night) 0.16f else 0.85f))
        return g
    }

    /** Глянцевая поверхность по диагонали: для главного блока. */
    fun glossy(ctx: Context, base: Int, night: Boolean, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                mix(base, Color.WHITE, if (night) 0.18f else 0.30f),
                base,
                mix(base, Color.BLACK, if (night) 0.18f else 0.10f)
            )
        )
        g.cornerRadius = dp(ctx, radiusDp).toFloat()
        g.setStroke(dp(ctx, 1), mix(base, Color.WHITE, 0.4f))
        return g
    }

    /** Вдавленная поверхность: тёмнее сверху, светлее снизу. Для полей ввода. */
    fun inset(ctx: Context, base: Int, night: Boolean, radiusDp: Int): GradientDrawable {
        val top = mix(base, Color.BLACK, if (night) 0.25f else 0.07f)
        val bottom = mix(base, Color.WHITE, if (night) 0.04f else 0.4f)
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
        g.cornerRadius = dp(ctx, radiusDp).toFloat()
        g.setStroke(dp(ctx, 1), mix(base, Color.BLACK, if (night) 0.35f else 0.10f))
        return g
    }

    /** Кнопка как клавиша: светлая грань сверху и тёмный бортик снизу. Снизу нужен отступ lipDp у содержимого. */
    fun key3d(ctx: Context, face: Int, night: Boolean, radiusDp: Int, lipDp: Int = 3): LayerDrawable {
        val r = dp(ctx, radiusDp).toFloat()
        val lip = GradientDrawable()
        lip.setColor(mix(face, Color.BLACK, if (night) 0.35f else 0.22f))
        lip.cornerRadius = r
        val top = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(mix(face, Color.WHITE, if (night) 0.18f else 0.32f), face)
        )
        top.cornerRadius = r
        val ld = LayerDrawable(arrayOf<Drawable>(lip, top))
        ld.setLayerInset(1, 0, 0, 0, dp(ctx, lipDp))
        return ld
    }

    /** Цвет тени (Android 9+): можно сделать цветное свечение под акцентными элементами. */
    fun tint(v: View, color: Int) {
        if (Build.VERSION.SDK_INT >= 28) {
            v.outlineAmbientShadowColor = color
            v.outlineSpotShadowColor = color
        }
    }

    /** Лёгкая «пружинка»: элемент чуть уменьшается, пока его держат. */
    fun pressable(v: View) {
        val down = AnimatorSet()
        down.playTogether(
            ObjectAnimator.ofFloat(v, View.SCALE_X, 0.97f),
            ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.97f)
        )
        down.duration = 80L
        val up = AnimatorSet()
        up.playTogether(
            ObjectAnimator.ofFloat(v, View.SCALE_X, 1f),
            ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f)
        )
        up.duration = 120L
        val sla = StateListAnimator()
        sla.addState(intArrayOf(android.R.attr.state_pressed), down)
        sla.addState(intArrayOf(), up)
        v.stateListAnimator = sla
    }
}
