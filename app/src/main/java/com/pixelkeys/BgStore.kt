package com.pixelkeys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Фон клавиатуры из фото пользователя. Фото копируется в закрытую папку приложения (и только там хранится),
 * затем из него делается готовая картинка с размытием и затемнением. Интернет и права на галерею не нужны:
 * фото выбирается через системное окно.
 */
object BgStore {

    private fun src(ctx: Context) = File(ctx.filesDir, "kbbg_src.jpg")
    private fun ready(ctx: Context) = File(ctx.filesDir, "kbbg_ready.jpg")

    fun exists(ctx: Context) = ready(ctx).exists()

    /** Меняется при каждой новой обработке: по нему клавиатура понимает, что фон обновился. */
    fun stamp(ctx: Context): Long = if (exists(ctx)) ready(ctx).lastModified() else 0L

    fun clear(ctx: Context) {
        src(ctx).delete()
        ready(ctx).delete()
    }

    fun load(ctx: Context): Bitmap? =
        if (exists(ctx)) BitmapFactory.decodeFile(ready(ctx).path) else null

    /** Копирует выбранное фото в приложение: уменьшает до 1280 px и поворачивает по EXIF. */
    fun importPhoto(ctx: Context, uri: Uri): Boolean {
        try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            var b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false
            val orient = try {
                ctx.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                } ?: 1
            } catch (e: Exception) {
                1
            }
            val deg = when (orient) {
                6 -> 90f
                3 -> 180f
                8 -> 270f
                else -> 0f
            }
            val big = maxOf(b.width, b.height)
            if (big > 1280) {
                val k = 1280f / big
                b = Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
            }
            if (deg != 0f) {
                val m = Matrix()
                m.postRotate(deg)
                b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
            }
            FileOutputStream(src(ctx)).use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Делает готовый фон: blurPct 0..100 (размытие), dimPct 0..80 (затемнение). */
    fun process(ctx: Context, blurPct: Int, dimPct: Int): Boolean {
        try {
            val f = src(ctx)
            if (!f.exists()) return false
            var b = BitmapFactory.decodeFile(f.path) ?: return false
            // чем сильнее размытие, тем меньше картинка: это быстрее, а на виде не сказывается
            val targetW = if (blurPct <= 0) 1080 else if (blurPct <= 40) 720 else 480
            if (b.width > targetW) {
                val k = targetW.toFloat() / b.width
                b = Bitmap.createScaledBitmap(b, targetW, (b.height * k).toInt().coerceAtLeast(1), true)
            }
            if (blurPct > 0) {
                val w = b.width
                val h = b.height
                val px = IntArray(w * h)
                b.getPixels(px, 0, w, 0, 0, w, h)
                Blur.box(px, w, h, maxOf(1, (blurPct * 0.45f * w / 1080f).toInt()))
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                out.setPixels(px, 0, w, 0, 0, w, h)
                b = out
            }
            if (dimPct > 0) {
                val m = b.copy(Bitmap.Config.ARGB_8888, true)
                Canvas(m).drawColor(Color.argb(dimPct * 255 / 100, 0, 0, 0))
                b = m
            }
            FileOutputStream(ready(ctx)).use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Средний цвет нижней полосы фото (им красим системную панель навигации, чтобы не было шва). */
    fun bottomColor(b: Bitmap): Int {
        return try {
            val h = (b.height * 0.12f).toInt().coerceAtLeast(1)
            val strip = Bitmap.createBitmap(b, 0, b.height - h, b.width, h)
            Bitmap.createScaledBitmap(strip, 1, 1, true).getPixel(0, 0) or (255 shl 24)
        } catch (e: Exception) {
            Color.BLACK
        }
    }
}

/** Рисует картинку так, чтобы она заполняла всю область (лишнее обрезается по краям). */
class CropDrawable(private val bmp: Bitmap) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val k = maxOf(b.width() / bmp.width.toFloat(), b.height() / bmp.height.toFloat())
        val sw = b.width() / k
        val sh = b.height() / k
        val sx = (bmp.width - sw) / 2f
        val sy = (bmp.height - sh) / 2f
        srcRect.set(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt())
        canvas.drawBitmap(bmp, srcRect, b, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.OPAQUE
}
