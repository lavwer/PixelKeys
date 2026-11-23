package com.pixelkeys

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.widget.TextView
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

    /** Фигура на полярных координатах: «печенье» (много бугров) или «клевер» (4 доли), как в референсе Expressive. */
    class PolarShape(
        private val color: Int,
        private val bumps: Int,
        private val amp: Float,
        private val sizePx: Int = -1
    ) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun draw(c: Canvas) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val rad = minOf(b.width(), b.height()) / 2f / (1f + amp)
            path.reset()
            val n = 140
            for (i in 0..n) {
                val t = i * 2.0 * Math.PI / n
                val rr = rad * (1.0 + amp * Math.cos(bumps * t))
                val x = (cx + rr * Math.cos(t)).toFloat()
                val y = (cy + rr * Math.sin(t)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            p.color = color
            c.drawPath(path, p)
        }

        override fun setAlpha(alpha: Int) {
            p.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            p.colorFilter = colorFilter
        }

        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = sizePx

        override fun getIntrinsicHeight(): Int = sizePx
    }

    /** Асимметричные скругления: большие и малые углы чередуются, поэтому карточки не похожи друг на друга. */
    fun blob(ctx: Context, base: Int, variant: Int): GradientDrawable {
        val big = dp(ctx, 56).toFloat()
        val mid = dp(ctx, 36).toFloat()
        val small = dp(ctx, 16).toFloat()
        val g = GradientDrawable()
        g.setColor(base)
        when (variant % 4) {
            0 -> g.cornerRadius = mid
            1 -> g.cornerRadii = floatArrayOf(big, big, small, small, big, big, small, small)
            2 -> g.cornerRadii = floatArrayOf(small, small, big, big, small, small, big, big)
            else -> g.cornerRadii = floatArrayOf(big, big, big, big, big, big, small, small)
        }
        return g
    }

    /** Плакатная типографика: очень жирный шрифт, плотные строки. */
    fun display(t: TextView, sp: Float) {
        t.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        t.textSize = sp
        t.setLineSpacing(0f, 0.9f)
        t.letterSpacing = -0.02f
        t.includeFontPadding = false
    }

    /** «Чернильные» цвета для выбранных элементов: тёмный на светлой теме, светлый на тёмной. */
    fun inkBg(night: Boolean): Int = Color.parseColor(if (night) "#ECE6F5" else "#1D1B24")

    fun inkFg(night: Boolean): Int = Color.parseColor(if (night) "#1D1B24" else "#F5F0FF")
}
