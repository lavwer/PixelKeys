package com.pixelkeys

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Material 3 Expressive: плоские тональные поверхности с крупными скруглениями,
 * кнопки-капсулы, у которых форма меняется при нажатии, и «пружинная» анимация.
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

    /** Тональная поверхность без обводки и градиента. Крупные формы чуть крупнее, как в Expressive. */
    fun raised(ctx: Context, base: Int, night: Boolean, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(base)
        g.cornerRadius = dp(ctx, if (radiusDp >= 18) radiusDp + 4 else radiusDp).toFloat()
        return g
    }

    /** Главный выразительный блок: сплошной яркий цвет и очень крупное скругление. */
    fun glossy(ctx: Context, base: Int, night: Boolean, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(base)
        g.cornerRadius = dp(ctx, radiusDp + 12).toFloat()
        return g
    }

    /** Заполненное поле ввода: чуть темнее (или светлее) поверхности. */
    fun inset(ctx: Context, base: Int, night: Boolean, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(mix(base, if (night) Color.WHITE else Color.BLACK, 0.06f))
        g.cornerRadius = dp(ctx, radiusDp + 4).toFloat()
        return g
    }

    /**
     * Кнопка-капсула: в покое полностью круглая, при нажатии форма «сжимается» до небольшого
     * скругления (морфинг формы) и чуть меняется цвет. Параметры radiusDp и lipDp оставлены для совместимости.
     */
    fun key3d(ctx: Context, face: Int, night: Boolean, radiusDp: Int, lipDp: Int = 0): StateListDrawable {
        val rest = GradientDrawable()
        rest.setColor(face)
        rest.cornerRadius = dp(ctx, 100).toFloat()
        val pressed = GradientDrawable()
        pressed.setColor(mix(face, if (night) Color.WHITE else Color.BLACK, 0.10f))
        pressed.cornerRadius = dp(ctx, 14).toFloat()
        val sld = StateListDrawable()
        sld.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        sld.addState(intArrayOf(), rest)
        sld.setEnterFadeDuration(90)
        sld.setExitFadeDuration(180)
        return sld
    }

    /** Цвет тени (Android 9+). */
    fun tint(v: View, color: Int) {
        if (Build.VERSION.SDK_INT >= 28) {
            v.outlineAmbientShadowColor = color
            v.outlineSpotShadowColor = color
        }
    }

    /** Пружина: при нажатии элемент заметно сжимается, при отпускании пружинит с перелётом. */
    fun pressable(v: View) {
        val down = AnimatorSet()
        down.playTogether(
            ObjectAnimator.ofFloat(v, View.SCALE_X, 0.95f),
            ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.95f)
        )
        down.duration = 90L
        down.interpolator = DecelerateInterpolator()
        val up = AnimatorSet()
        up.playTogether(
            ObjectAnimator.ofFloat(v, View.SCALE_X, 1f),
            ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f)
        )
        up.duration = 300L
        up.interpolator = OvershootInterpolator(3f)
        val sla = StateListAnimator()
        sla.addState(intArrayOf(android.R.attr.state_pressed), down)
        sla.addState(intArrayOf(), up)
        v.stateListAnimator = sla
    }
}
