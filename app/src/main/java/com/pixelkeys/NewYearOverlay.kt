package com.pixelkeys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View

/**
 * Новогодний слой поверх клавиатуры: гирлянда с мишурой сверху, лёгкий снегопад
 * и всплески при нажатии (конфетти, снежинки, салют, звёзды, мишура).
 * Сам слой не принимает касания: они проходят к клавишам под ним.
 */
class NewYearOverlay(ctx: Context, private val pal: Palette) : View(ctx) {

    companion object {
        /** Высота полосы гирлянды в dp: на столько клавиатура становится выше. */
        const val GARLAND_DP = 20
    }

    private class P {
        var kind = 0
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var g = 0f
        var drag = 0f
        var rot = 0f
        var vr = 0f
        var size = 0f
        var life = 0f
        var max = 1f
        var color = 0
        var phase = 0f
    }

    private val d = ctx.resources.displayMetrics.density
    private val rnd = java.util.Random()
    private val ps = ArrayList<P>()
    private val snow = ArrayList<P>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var tinsel: Bitmap? = null
    private var attached = false
    private var loopOn = false
    private var last = 0L
    private var timeS = 0f
    private var segPx = 1f

    private val colors = intArrayOf(
        0xFFE53935.toInt(), 0xFFFFC107.toInt(), 0xFF43A047.toInt(),
        0xFF29B6F6.toInt(), 0xFFEC407A.toInt(), 0xFFFFFFFF.toInt()
    )
    private val bulbColors = intArrayOf(
        0xFFFF5252.toInt(), 0xFFFFD740.toInt(), 0xFF69F0AE.toInt(),
        0xFF40C4FF.toInt(), 0xFFFF80AB.toInt()
    )
    private val tinselColors = intArrayOf(
        0xFFFFD54F.toInt(), 0xFFE53935.toInt(), 0xFF43A047.toInt(), 0xFFECEFF1.toInt()
    )

    init {
        tag = "nyov"
    }

    // ---- гирлянда ----
    private fun wireY(x: Float): Float {
        val frac = (x % segPx) / segPx
        return 2f * d + 9f * d * Math.sin(Math.PI * frac).toFloat()
    }

    private fun buildTinsel(w: Int): Bitmap {
        val hpx = (GARLAND_DP * d).toInt() + 2
        val bmp = Bitmap.createBitmap(w, hpx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeCap = Paint.Cap.ROUND
        // проволока
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.color = 0xFF1B5E20.toInt()
        path.reset()
        path.moveTo(0f, wireY(0f))
        var x = 0f
        while (x <= w) {
            x += 3f
            path.lineTo(x, wireY(x))
        }
        c.drawPath(path, p)
        // ворсинки мишуры
        p.strokeWidth = 1.4f * d
        var i = 0
        x = 0f
        while (x <= w) {
            val y = wireY(x)
            val ang = ((i * 53) % 90 - 45) * 0.0174533f
            val len = (3.5f + ((i * 37) % 30) / 10f) * d
            val side = if (i % 2 == 0) 1f else -1f
            p.color = tinselColors[i % tinselColors.size]
            c.drawLine(
                x, y,
                x + Math.sin(ang.toDouble()).toFloat() * len,
                y + side * Math.cos(ang.toDouble()).toFloat() * len,
                p
            )
            x += 2.4f * d
            i++
        }
        return bmp
    }

    private fun initSnow(w: Int, h: Int) {
        snow.clear()
        if (!pal.nySnow || w <= 0 || h <= 0) return
        for (i in 0 until 22) {
            val p = P()
            p.x = rnd.nextFloat() * w
            p.y = rnd.nextFloat() * h
            p.size = (1.4f + rnd.nextFloat() * 2.2f) * d
            p.vy = (16f + rnd.nextFloat() * 28f) * d
            p.phase = rnd.nextFloat() * 6.28f
            snow.add(p)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val scallops = maxOf(2, Math.round(w / (130f * d)))
        segPx = w / scallops.toFloat()
        tinsel = if (pal.nyGarland && w > 0) buildTinsel(w) else null
        initSnow(w, h)
        ensureLoop()
    }

    // ---- всплески при нажатии ----
    private fun spawn(
        kind: Int, x: Float, y: Float, vx: Float, vy: Float, g: Float, drag: Float,
        size: Float, life: Float, color: Int, vr: Float
    ) {
        val p = P()
        p.kind = kind
        p.x = x
        p.y = y
        p.vx = vx
        p.vy = vy
        p.g = g
        p.drag = drag
        p.size = size
        p.life = life
        p.max = life
        p.color = color
        p.vr = vr
        p.rot = rnd.nextFloat() * 6.28f
        p.phase = rnd.nextFloat() * 6.28f
        ps.add(p)
    }

    private fun cosD(deg: Float) = Math.cos(deg * 0.0174533).toFloat()
    private fun sinD(deg: Float) = Math.sin(deg * 0.0174533).toFloat()

    /** effect: 9 = конфетти, 10 = снежинки, 11 = салют, 12 = звёзды, 13 = мишура. */
    fun burst(x: Float, y: Float, effect: Int) {
        if (ps.size > 220) return
        when (effect) {
            9 -> for (i in 0 until 26) {
                val a = -90f + (rnd.nextFloat() - 0.5f) * 150f
                val sp = (200f + rnd.nextFloat() * 460f) * d
                spawn(
                    0, x, y, cosD(a) * sp, sinD(a) * sp, 900f * d, 1.4f,
                    (6f + rnd.nextFloat() * 5f) * d, 1.3f + rnd.nextFloat() * 0.9f,
                    colors[rnd.nextInt(colors.size)], (rnd.nextFloat() - 0.5f) * 20f
                )
            }
            10 -> for (i in 0 until 14) {
                val a = -90f + (rnd.nextFloat() - 0.5f) * 200f
                val sp = (40f + rnd.nextFloat() * 120f) * d
                spawn(
                    1, x, y, cosD(a) * sp, sinD(a) * sp, 70f * d, 0.8f,
                    (5f + rnd.nextFloat() * 4f) * d, 1.8f + rnd.nextFloat() * 1.2f,
                    if (pal.night) 0xFFFFFFFF.toInt() else 0xFF5FA8F0.toInt(), (rnd.nextFloat() - 0.5f) * 6f
                )
            }
            11 -> {
                val c1 = colors[rnd.nextInt(5)]
                val c2 = colors[rnd.nextInt(5)]
                for (i in 0 until 32) {
                    val a = rnd.nextFloat() * 360f
                    val sp = (90f + rnd.nextFloat() * 240f) * d
                    spawn(
                        2, x, y, cosD(a) * sp, sinD(a) * sp, 260f * d, 1.2f,
                        (2.2f + rnd.nextFloat() * 1.2f) * d, 0.8f + rnd.nextFloat() * 0.5f,
                        if (i % 2 == 0) c1 else c2, 0f
                    )
                }
            }
            12 -> for (i in 0 until 12) {
                val a = -90f + (rnd.nextFloat() - 0.5f) * 160f
                val sp = (90f + rnd.nextFloat() * 210f) * d
                val col = when (i % 3) {
                    0 -> 0xFFFFD54F.toInt()
                    1 -> 0xFFFFFFFF.toInt()
                    else -> 0xFFFF8A80.toInt()
                }
                spawn(
                    3, x, y, cosD(a) * sp, sinD(a) * sp, 120f * d, 1.0f,
                    (5f + rnd.nextFloat() * 4f) * d, 0.9f + rnd.nextFloat() * 0.6f,
                    col, (rnd.nextFloat() - 0.5f) * 8f
                )
            }
            13 -> for (i in 0 until 10) {
                val a = -90f + (rnd.nextFloat() - 0.5f) * 140f
                val sp = (220f + rnd.nextFloat() * 300f) * d
                spawn(
                    4, x, y, cosD(a) * sp, sinD(a) * sp, 380f * d, 1.1f,
                    0f, 0.9f + rnd.nextFloat() * 0.6f,
                    tinselColors[rnd.nextInt(tinselColors.size)], 0f
                )
            }
        }
        ensureLoop()
        invalidate()
    }

    // ---- цикл анимации ----
    private fun step(dt: Float) {
        timeS += dt
        var i = ps.size - 1
        while (i >= 0) {
            val p = ps[i]
            p.life -= dt
            if (p.life <= 0f) {
                ps.removeAt(i)
            } else {
                p.vy += p.g * dt
                val k = 1f - p.drag * dt
                p.vx *= k
                p.vy *= k
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rot += p.vr * dt
            }
            i--
        }
        for (p in snow) {
            p.y += p.vy * dt
            p.x += Math.sin((timeS * 1.3f + p.phase).toDouble()).toFloat() * 10f * d * dt
            if (p.y > height + 6f * d) {
                p.y = -6f * d
                p.x = rnd.nextFloat() * width
            }
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!attached) {
                loopOn = false
                return
            }
            val now = SystemClock.uptimeMillis()
            val dt = ((now - last) / 1000f).coerceIn(0.001f, 0.05f)
            last = now
            step(dt)
            invalidate()
            val fast = ps.isNotEmpty() || snow.isNotEmpty()
            if (fast) {
                postOnAnimation(this)
            } else if (tinsel != null) {
                postDelayed(this, 125L)
            } else {
                loopOn = false
            }
        }
    }

    private fun ensureLoop() {
        if (loopOn || !attached) return
        loopOn = true
        last = SystemClock.uptimeMillis()
        postOnAnimation(loop)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        ensureLoop()
    }

    override fun onDetachedFromWindow() {
        attached = false
        loopOn = false
        removeCallbacks(loop)
        super.onDetachedFromWindow()
    }

    // ---- рисование ----
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (snow.isNotEmpty()) {
            paint.style = Paint.Style.FILL
            paint.color = if (pal.night) 0xFFFFFFFF.toInt() else 0xFF8FB8E8.toInt()
            paint.alpha = 190
            for (p in snow) c.drawCircle(p.x, p.y, p.size, paint)
        }
        drawGarland(c)
        for (p in ps) drawParticle(c, p)
    }

    private fun drawGarland(c: Canvas) {
        val bmp = tinsel ?: return
        c.drawBitmap(bmp, 0f, 0f, null)
        paint.style = Paint.Style.FILL
        val step = 26f * d
        var x = step / 2f
        var k = 0
        while (x < width) {
            val y = wireY(x) + 4.5f * d
            val tw = 0.55f + 0.45f * Math.sin((timeS * 3.2f + k * 1.7f).toDouble()).toFloat()
            val col = bulbColors[k % bulbColors.size]
            paint.color = 0xFF263238.toInt()
            paint.alpha = 255
            c.drawRect(x - 1.5f * d, y - 4.5f * d, x + 1.5f * d, y - 2.5f * d, paint)
            paint.color = col
            paint.alpha = (70 * tw).toInt()
            c.drawCircle(x, y, 6.5f * d, paint)
            paint.color = col
            paint.alpha = (150 + 105 * tw).toInt()
            c.drawCircle(x, y, 3.2f * d, paint)
            x += step
            k++
        }
    }

    private fun drawParticle(c: Canvas, p: P) {
        val fade = minOf(1f, p.life / 0.35f)
        val age = p.max - p.life
        paint.color = p.color
        paint.alpha = (255 * fade).toInt()
        when (p.kind) {
            0 -> {   // конфетти
                paint.style = Paint.Style.FILL
                c.save()
                c.translate(p.x, p.y)
                c.rotate(Math.toDegrees(p.rot.toDouble()).toFloat())
                val flip = Math.cos((age * 9f + p.phase).toDouble()).toFloat()
                c.scale(1f, if (Math.abs(flip) < 0.15f) 0.15f else flip)
                c.drawRect(-p.size / 2f, -p.size * 0.28f, p.size / 2f, p.size * 0.28f, paint)
                c.restore()
            }
            1 -> {   // снежинка
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f * d
                paint.strokeCap = Paint.Cap.ROUND
                c.save()
                c.translate(p.x, p.y)
                c.rotate(Math.toDegrees(p.rot.toDouble()).toFloat())
                for (a in 0 until 3) {
                    c.drawLine(-p.size, 0f, p.size, 0f, paint)
                    c.rotate(60f)
                }
                c.restore()
            }
            2 -> {   // искра салюта
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = p.size
                paint.strokeCap = Paint.Cap.ROUND
                c.drawLine(p.x - p.vx * 0.03f, p.y - p.vy * 0.03f, p.x, p.y, paint)
            }
            3 -> {   // звезда
                paint.style = Paint.Style.FILL
                val r = p.size * (0.65f + 0.35f * Math.sin((age * 14f + p.phase).toDouble()).toFloat())
                c.save()
                c.translate(p.x, p.y)
                c.rotate(Math.toDegrees(p.rot.toDouble()).toFloat())
                path.reset()
                path.moveTo(0f, -r)
                path.quadTo(0f, 0f, r, 0f)
                path.quadTo(0f, 0f, 0f, r)
                path.quadTo(0f, 0f, -r, 0f)
                path.quadTo(0f, 0f, 0f, -r)
                path.close()
                c.drawPath(path, paint)
                c.restore()
            }
            4 -> {   // серпантин (мишура)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.6f * d
                paint.strokeCap = Paint.Cap.ROUND
                var dx = p.vx
                var dy = p.vy
                val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                if (len < 1f) {
                    dx = 0f
                    dy = -1f
                } else {
                    dx /= len
                    dy /= len
                }
                val nx = -dy
                val ny = dx
                val l = 26f * d
                path.reset()
                for (i in 0..6) {
                    val t = i / 6f
                    val off = Math.sin((t * 9f + age * 16f + p.phase).toDouble()).toFloat() * 4f * d * t
                    val x = p.x - dx * t * l + nx * off
                    val y = p.y - dy * t * l + ny * off
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                c.drawPath(path, paint)
            }
        }
    }
}
