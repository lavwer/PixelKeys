package com.pixelkeys

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View

/**
 * «Посыпки»: сверху вниз бесконечно падает то, что выбрано: снег, листья, вода или весна.
 * Частицы всё время новые: разные формы, размеры, глубина, скорость и качание,
 * ветер порывами, волны густоты. Слой не принимает касания и не влияет на размер окна.
 */
class FallOverlay(ctx: Context, private val night: Boolean) : View(ctx) {

    private class F {
        var t = 0            // подвид внутри сезона
        var x = 0f
        var y = 0f
        var vy = 0f          // скорость падения, px/с
        var size = 0f
        var rot = 0f
        var vr = 0f
        var ph = 0f          // фаза качания
        var sw = 0f          // размах качания
        var sf = 1f          // частота качания
        var color = 0
        var alpha = 1f
        var depth = 1f       // «глубина»: дальние мельче, медленнее и бледнее
        var wait = 0f        // пауза перед появлением (создаёт волны)
        var age = 0f
    }

    private val d = ctx.resources.displayMetrics.density
    private val rnd = java.util.Random()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()
    private val list = ArrayList<F>()
    private val ripples = ArrayList<FloatArray>()   // круги на воде: x, y, возраст

    private var kind = 0       // 0 = ничего, 1 = снег, 2 = листья, 3 = вода, 4 = весна
    private var amount = 50
    private var speed = 100
    private var wind = 0
    private var timeS = 0f
    private var attached = false
    private var loopOn = false
    private var last = 0L

    // контуры единичного размера
    private val pLens = Path()
    private val pMaple = Path()
    private val pRound = Path()
    private val pDrop = Path()
    private val pPetal = Path()
    private val pStar = Path()

    private val leafColors = intArrayOf(
        0xFFD84315.toInt(), 0xFFEF6C00.toInt(), 0xFFF9A825.toInt(), 0xFFBF360C.toInt(),
        0xFFFFB300.toInt(), 0xFF8D6E63.toInt(), 0xFFA1887F.toInt()
    )
    private val petalColors = intArrayOf(
        0xFFF8BBD0.toInt(), 0xFFF48FB1.toInt(), 0xFFFFCDD2.toInt(), 0xFFFCE4EC.toInt()
    )
    private val flowerColors = intArrayOf(
        0xFFF8BBD0.toInt(), 0xFFCE93D8.toInt(), 0xFFFFF176.toInt(), 0xFFFFAB91.toInt(), 0xFF90CAF9.toInt()
    )
    private val greenColors = intArrayOf(
        0xFF9CCC65.toInt(), 0xFF7CB342.toInt(), 0xFFC5E1A5.toInt(), 0xFF66BB6A.toInt()
    )
    private val butterflyColors = intArrayOf(
        0xFFFF8A65.toInt(), 0xFFFFD54F.toInt(), 0xFF4FC3F7.toInt(), 0xFFBA68C8.toInt()
    )

    init {
        pLens.moveTo(0f, -1f)
        pLens.quadTo(0.95f, -0.15f, 0f, 1f)
        pLens.quadTo(-0.95f, -0.15f, 0f, -1f)
        pLens.close()

        // клён: пять лопастей с зубцами
        var first = true
        for (lobe in 0 until 5) {
            val base = lobe * 72f - 90f
            val offs = floatArrayOf(-26f, -14f, 0f, 14f, 26f)
            val rads = floatArrayOf(0.45f, 0.72f, 1.0f, 0.72f, 0.45f)
            for (j in 0 until 5) {
                val a = Math.toRadians((base + offs[j]).toDouble())
                val x = (Math.cos(a) * rads[j]).toFloat()
                val y = (Math.sin(a) * rads[j]).toFloat()
                if (first) {
                    pMaple.moveTo(x, y)
                    first = false
                } else {
                    pMaple.lineTo(x, y)
                }
            }
        }
        pMaple.close()

        tmp.set(-0.72f, -0.9f, 0.72f, 0.9f)
        pRound.addOval(tmp, Path.Direction.CW)

        pDrop.moveTo(0f, -1f)
        pDrop.cubicTo(0.55f, -0.1f, 0.7f, 0.8f, 0f, 0.9f)
        pDrop.cubicTo(-0.7f, 0.8f, -0.55f, -0.1f, 0f, -1f)
        pDrop.close()

        // лепесток с вырезом на конце
        pPetal.moveTo(0f, 0.9f)
        pPetal.cubicTo(0.9f, 0.5f, 0.8f, -0.8f, 0.15f, -0.95f)
        pPetal.lineTo(0f, -0.7f)
        pPetal.lineTo(-0.15f, -0.95f)
        pPetal.cubicTo(-0.8f, -0.8f, -0.9f, 0.5f, 0f, 0.9f)
        pPetal.close()

        pStar.moveTo(0f, -1f)
        pStar.quadTo(0f, 0f, 1f, 0f)
        pStar.quadTo(0f, 0f, 0f, 1f)
        pStar.quadTo(0f, 0f, -1f, 0f)
        pStar.quadTo(0f, 0f, 0f, -1f)
        pStar.close()
    }

    /** Размер окна определяет родитель, слой только подстраивается под него. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(w, h)
    }

    /** k: 0 = выкл, 1 = снег, 2 = листья, 3 = вода, 4 = весна. */
    fun configure(k: Int, amountPct: Int, speedPct: Int, windPct: Int) {
        val changed = k != kind
        kind = k.coerceIn(0, 4)
        amount = amountPct.coerceIn(10, 100)
        speed = speedPct.coerceIn(20, 200)
        wind = windPct.coerceIn(-100, 100)
        if (changed) {
            list.clear()
            ripples.clear()
        }
        fitCount()
        ensureLoop()
        invalidate()
    }

    private fun targetCount(): Int {
        if (kind == 0 || width <= 0 || height <= 0) return 0
        val wf = (width / (360f * d)).coerceIn(0.7f, 1.7f)
        val hf = (height / (260f * d)).coerceIn(0.6f, 1.6f)
        return (6 + amount * 0.5f * wf * hf).toInt()
    }

    private fun fitCount() {
        val n = targetCount()
        while (list.size < n) {
            val f = F()
            respawn(f, true)
            list.add(f)
        }
        while (list.size > n) list.removeAt(list.size - 1)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        fitCount()
        ensureLoop()
    }

    private fun dens() = 0.75f + 0.25f * Math.sin((timeS * 0.23f).toDouble()).toFloat()

    private fun pick(a: IntArray) = a[rnd.nextInt(a.size)]

    private fun sign() = if (rnd.nextBoolean()) 1f else -1f

    /** Создаёт новую частицу случайного вида; initial = раскидать по всей высоте при запуске. */
    private fun respawn(f: F, initial: Boolean) {
        val w = if (width > 0) width.toFloat() else 360f * d
        val h = if (height > 0) height.toFloat() else 260f * d
        f.depth = 0.55f + rnd.nextFloat() * 0.6f
        f.alpha = 0.55f + 0.45f * (f.depth - 0.55f) / 0.6f
        f.age = rnd.nextFloat() * 10f
        f.ph = rnd.nextFloat() * 6.28f
        f.rot = rnd.nextFloat() * 6.28f
        f.vr = 0f
        f.sw = 0f
        f.sf = 1f
        val r = rnd.nextFloat()
        val dep = f.depth
        when (kind) {
            1 -> {   // снег
                if (r < 0.58f) {
                    f.t = 0
                    f.size = (1.2f + rnd.nextFloat() * 2.0f) * d * dep
                    f.vy = (24f + rnd.nextFloat() * 30f) * d * dep
                    f.sw = (5f + rnd.nextFloat() * 12f) * d
                    f.sf = 0.6f + rnd.nextFloat()
                } else if (r < 0.80f) {
                    f.t = 1
                    f.size = (5f + rnd.nextFloat() * 4f) * d * dep
                    f.vy = (20f + rnd.nextFloat() * 24f) * d * dep
                    f.vr = sign() * (0.4f + rnd.nextFloat() * 1.2f)
                    f.sw = (4f + rnd.nextFloat() * 10f) * d
                    f.sf = 0.5f + rnd.nextFloat()
                } else if (r < 0.94f) {
                    f.t = 2
                    f.size = (5f + rnd.nextFloat() * 5f) * d * dep
                    f.vy = (18f + rnd.nextFloat() * 22f) * d * dep
                    f.sw = (8f + rnd.nextFloat() * 10f) * d
                    f.sf = 0.4f + rnd.nextFloat() * 0.6f
                } else {
                    f.t = 3
                    f.size = (3f + rnd.nextFloat() * 2.5f) * d * dep
                    f.vy = (14f + rnd.nextFloat() * 16f) * d * dep
                    f.vr = sign() * 2f
                    f.sw = (4f + rnd.nextFloat() * 8f) * d
                }
                f.color = if (night) (if (rnd.nextBoolean()) 0xFFFFFFFF.toInt() else 0xFFCFE8FF.toInt())
                else (if (rnd.nextBoolean()) 0xFF7FB2E5.toInt() else 0xFFA5CCF2.toInt())
            }
            2 -> {   // листья
                f.t = if (r < 0.38f) 0 else if (r < 0.72f) 1 else 2
                f.size = (6f + rnd.nextFloat() * 6f) * d * dep
                f.vy = (34f + rnd.nextFloat() * 38f) * d * dep
                f.sw = (16f + rnd.nextFloat() * 26f) * d
                f.sf = 0.9f + rnd.nextFloat() * 1.3f
                f.vr = sign() * (0.8f + rnd.nextFloat() * 2.2f)
                f.color = pick(leafColors)
                f.alpha = minOf(1f, f.alpha + 0.1f)
            }
            3 -> {   // вода
                if (r < 0.52f) {
                    f.t = 0
                    f.size = (10f + rnd.nextFloat() * 14f) * d * dep        // длина струйки
                    f.vy = (560f + rnd.nextFloat() * 380f) * d * dep
                    f.color = if (night) 0xFFA5D8FF.toInt() else 0xFF3F8FD8.toInt()
                    f.alpha *= 0.75f
                } else if (r < 0.78f) {
                    f.t = 1
                    f.size = (3f + rnd.nextFloat() * 2.5f) * d * dep
                    f.vy = (240f + rnd.nextFloat() * 200f) * d * dep
                    f.sw = rnd.nextFloat() * 3f * d
                    f.color = 0xFF4FC3F7.toInt()
                } else if (r < 0.92f) {
                    f.t = 2
                    f.size = (4f + rnd.nextFloat() * 5f) * d * dep
                    f.vy = (36f + rnd.nextFloat() * 50f) * d * dep
                    f.sw = (8f + rnd.nextFloat() * 14f) * d
                    f.sf = 1f + rnd.nextFloat()
                    f.color = 0xFF81D4FA.toInt()
                } else {
                    f.t = 3
                    f.size = (0.8f + rnd.nextFloat() * 0.8f) * d
                    f.vy = (120f + rnd.nextFloat() * 140f) * d
                    f.color = if (night) 0xFFE1F5FE.toInt() else 0xFF64B5F6.toInt()
                }
            }
            else -> {   // весна
                if (r < 0.42f) {
                    f.t = 0
                    f.size = (4f + rnd.nextFloat() * 4f) * d * dep
                    f.vy = (26f + rnd.nextFloat() * 28f) * d * dep
                    f.sw = (14f + rnd.nextFloat() * 22f) * d
                    f.sf = 0.8f + rnd.nextFloat() * 1.2f
                    f.vr = sign() * (0.8f + rnd.nextFloat() * 2f)
                    f.color = pick(petalColors)
                } else if (r < 0.62f) {
                    f.t = 1
                    f.size = (5f + rnd.nextFloat() * 3.5f) * d * dep
                    f.vy = (30f + rnd.nextFloat() * 28f) * d * dep
                    f.sw = (10f + rnd.nextFloat() * 12f) * d
                    f.vr = sign() * (0.5f + rnd.nextFloat())
                    f.color = pick(flowerColors)
                } else if (r < 0.80f) {
                    f.t = 2
                    f.size = (5f + rnd.nextFloat() * 4f) * d * dep
                    f.vy = (34f + rnd.nextFloat() * 30f) * d * dep
                    f.sw = (12f + rnd.nextFloat() * 14f) * d
                    f.sf = 0.9f + rnd.nextFloat()
                    f.vr = sign() * (0.8f + rnd.nextFloat() * 1.6f)
                    f.color = pick(greenColors)
                } else if (r < 0.87f) {
                    f.t = 3
                    f.size = (7f + rnd.nextFloat() * 4f) * d * dep
                    f.vy = (22f + rnd.nextFloat() * 20f) * d * dep
                    f.sw = (26f + rnd.nextFloat() * 34f) * d
                    f.sf = 1.4f + rnd.nextFloat()
                    f.color = pick(butterflyColors)
                    f.alpha = minOf(1f, f.alpha + 0.2f)
                } else {
                    f.t = 4
                    f.size = (0.9f + rnd.nextFloat()) * d
                    f.vy = (10f + rnd.nextFloat() * 16f) * d
                    f.sw = (6f + rnd.nextFloat() * 8f) * d
                    f.color = 0xFFFFF59D.toInt()
                }
            }
        }
        f.x = rnd.nextFloat() * (w + 80f * d) - 40f * d
        if (initial) {
            f.y = rnd.nextFloat() * h
            f.wait = 0f
        } else {
            f.y = -(f.size * 2f) - rnd.nextFloat() * 30f * d
            f.wait = rnd.nextFloat() * (1.2f - dens()) * 3f
        }
    }

    // ---- движение ----
    private fun windNow(): Float {
        val t = timeS
        val gust = 0.55f * Math.sin((t * 0.37f).toDouble()).toFloat() +
            0.35f * Math.sin((t * 0.91f + 1.3f).toDouble()).toFloat() +
            0.2f * Math.sin((t * 2.3f + 0.4f).toDouble()).toFloat()
        return wind / 100f + gust * 0.35f
    }

    private fun step(dt0: Float) {
        val dt = dt0 * speed / 100f
        timeS += dt0
        val wn = windNow()
        val w = width.toFloat()
        val h = height.toFloat()
        for (f in list) {
            if (f.wait > 0f) {
                f.wait -= dt0
                continue
            }
            f.age += dt
            f.y += f.vy * dt
            f.x += wn * 60f * d * f.depth * dt
            f.rot += f.vr * dt
            if (f.x < -50f * d) f.x += w + 100f * d else if (f.x > w + 50f * d) f.x -= w + 100f * d
            if (f.y > h + f.size * 2f + 10f * d) {
                if (kind == 3 && f.t <= 1 && rnd.nextFloat() < 0.4f) {
                    ripples.add(floatArrayOf(f.x, h - 3f * d, 0f))
                }
                respawn(f, false)
            }
        }
        var i = ripples.size - 1
        while (i >= 0) {
            val rp = ripples[i]
            rp[2] += dt0
            if (rp[2] > 0.7f) ripples.removeAt(i)
            i--
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!attached || kind == 0) {
                loopOn = false
                return
            }
            val now = SystemClock.uptimeMillis()
            val dt = ((now - last) / 1000f).coerceIn(0.001f, 0.05f)
            last = now
            step(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    private fun ensureLoop() {
        if (loopOn || !attached || kind == 0) return
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
    private fun deg(r: Float) = r * 57.29578f

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (kind == 0) return
        val wn = windNow()
        for (f in list) {
            if (f.wait > 0f) continue
            val x = f.x + Math.sin((f.age * f.sf + f.ph).toDouble()).toFloat() * f.sw
            val y = f.y
            paint.color = f.color
            paint.alpha = (255 * f.alpha).toInt()
            when (kind) {
                1 -> drawSnow(c, f, x, y)
                2 -> drawLeaf(c, f, x, y)
                3 -> drawWater(c, f, x, y, wn)
                else -> drawSpring(c, f, x, y)
            }
        }
        if (kind == 3) {
            for (rp in ripples) {
                val t = rp[2] / 0.7f
                val wd = (3f + 12f * t) * d
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.1f * d
                paint.color = if (night) 0xFFA5D8FF.toInt() else 0xFF3F8FD8.toInt()
                paint.alpha = (255 * (1f - t) * 0.6f).toInt()
                tmp.set(rp[0] - wd, rp[1] - wd * 0.32f, rp[0] + wd, rp[1] + wd * 0.32f)
                c.drawOval(tmp, paint)
            }
        }
    }

    private fun drawSnow(c: Canvas, f: F, x: Float, y: Float) {
        when (f.t) {
            0 -> {
                paint.style = Paint.Style.FILL
                c.drawCircle(x, y, f.size, paint)
            }
            1 -> {   // кристалл: три луча с веточками
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = maxOf(1f, 1.1f * d)
                c.save()
                c.translate(x, y)
                c.rotate(deg(f.rot))
                val r = f.size
                for (i in 0 until 3) {
                    c.drawLine(-r, 0f, r, 0f, paint)
                    c.drawLine(r * 0.55f, 0f, r * 0.8f, -r * 0.28f, paint)
                    c.drawLine(r * 0.55f, 0f, r * 0.8f, r * 0.28f, paint)
                    c.drawLine(-r * 0.55f, 0f, -r * 0.8f, -r * 0.28f, paint)
                    c.drawLine(-r * 0.55f, 0f, -r * 0.8f, r * 0.28f, paint)
                    c.rotate(60f)
                }
                c.restore()
            }
            2 -> {   // пушистый хлопок: мягкое свечение из кругов
                paint.style = Paint.Style.FILL
                val a = paint.alpha
                paint.alpha = a / 4
                c.drawCircle(x, y, f.size, paint)
                paint.alpha = a / 3
                c.drawCircle(x, y, f.size * 0.66f, paint)
                paint.alpha = a / 2
                c.drawCircle(x, y, f.size * 0.33f, paint)
            }
            else -> {   // блёстка
                val tw = 0.5f + 0.5f * Math.sin((f.age * 6f + f.ph).toDouble()).toFloat()
                paint.style = Paint.Style.FILL
                paint.alpha = (255 * f.alpha * tw).toInt()
                c.save()
                c.translate(x, y)
                c.rotate(deg(f.rot))
                c.scale(f.size, f.size)
                c.drawPath(pStar, paint)
                c.restore()
            }
        }
    }

    private fun drawLeaf(c: Canvas, f: F, x: Float, y: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(deg(f.rot))
        val flip = Math.cos((f.age * (1.8f + f.sf) + f.ph).toDouble()).toFloat()
        val sx = if (Math.abs(flip) < 0.2f) (if (flip < 0f) -0.2f else 0.2f) else flip
        c.scale(f.size * sx, f.size)
        paint.style = Paint.Style.FILL
        when (f.t) {
            0 -> c.drawPath(pMaple, paint)
            1 -> c.drawPath(pLens, paint)
            else -> c.drawPath(pRound, paint)
        }
        // прожилка или черенок
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.09f
        paint.color = 0x66000000
        if (f.t == 0) c.drawLine(0f, 0f, 0f, 1.3f, paint) else c.drawLine(0f, -0.9f, 0f, 0.9f, paint)
        c.restore()
    }

    private fun drawWater(c: Canvas, f: F, x: Float, y: Float, wn: Float) {
        when (f.t) {
            0 -> {   // струйка дождя, наклонена ветром
                val vx = wn * 60f * d * f.depth
                val l = Math.sqrt((vx * vx + f.vy * f.vy).toDouble()).toFloat()
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = (1.0f + 0.8f * f.depth) * d
                c.drawLine(x, y, x - vx / l * f.size, y - f.vy / l * f.size, paint)
            }
            1 -> {   // капля
                paint.style = Paint.Style.FILL
                c.save()
                c.translate(x, y)
                c.scale(f.size * 0.7f, f.size * 1.4f)
                c.drawPath(pDrop, paint)
                c.restore()
            }
            2 -> {   // пузырь: кольцо, лёгкая заливка и блик
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f * d
                c.drawCircle(x, y, f.size, paint)
                paint.style = Paint.Style.FILL
                paint.alpha = (255 * f.alpha * 0.3f).toInt()
                c.drawCircle(x, y, f.size, paint)
                paint.color = 0xFFFFFFFF.toInt()
                paint.alpha = (255 * f.alpha * 0.8f).toInt()
                c.drawCircle(x - f.size * 0.35f, y - f.size * 0.35f, f.size * 0.22f, paint)
            }
            else -> {
                paint.style = Paint.Style.FILL
                c.drawCircle(x, y, f.size, paint)
            }
        }
    }

    private fun drawSpring(c: Canvas, f: F, x: Float, y: Float) {
        paint.style = Paint.Style.FILL
        when (f.t) {
            0, 2 -> {   // лепесток сакуры или молодой листик: кувыркаются
                c.save()
                c.translate(x, y)
                c.rotate(deg(f.rot))
                val flip = Math.cos((f.age * 2.4f + f.ph).toDouble()).toFloat()
                val sx = if (Math.abs(flip) < 0.25f) 0.25f else Math.abs(flip)
                c.scale(f.size * sx, f.size)
                c.drawPath(if (f.t == 0) pPetal else pLens, paint)
                c.restore()
            }
            1 -> {      // цветок: пять лепестков и серединка
                c.save()
                c.translate(x, y)
                c.rotate(deg(f.rot))
                val s = f.size
                for (i in 0 until 5) {
                    tmp.set(-0.32f * s, -s, 0.32f * s, -0.1f * s)
                    c.drawOval(tmp, paint)
                    c.rotate(72f)
                }
                paint.color = 0xFFFFEB3B.toInt()
                paint.alpha = (255 * f.alpha).toInt()
                c.drawCircle(0f, 0f, 0.3f * s, paint)
                c.restore()
            }
            3 -> {      // бабочка: крылья машут, летит зигзагом
                val flap = 0.25f + 0.75f * Math.abs(Math.sin((f.age * 14f).toDouble()).toFloat())
                val s = f.size
                c.save()
                c.translate(x, y)
                c.rotate(deg(Math.cos((f.age * f.sf + f.ph).toDouble()).toFloat() * 0.45f))
                tmp.set(-s * flap, -s * 0.9f, 0f, s * 0.1f)
                c.drawOval(tmp, paint)
                tmp.set(0f, -s * 0.9f, s * flap, s * 0.1f)
                c.drawOval(tmp, paint)
                tmp.set(-s * flap * 0.7f, 0f, 0f, s * 0.8f)
                c.drawOval(tmp, paint)
                tmp.set(0f, 0f, s * flap * 0.7f, s * 0.8f)
                c.drawOval(tmp, paint)
                paint.color = 0xFF5D4037.toInt()
                paint.alpha = (255 * f.alpha).toInt()
                c.drawRect(-0.07f * s, -0.7f * s, 0.07f * s, 0.7f * s, paint)
                c.restore()
            }
            else -> c.drawCircle(x, y, f.size, paint)   // пыльца
        }
    }
}
