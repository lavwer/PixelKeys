package com.pixelkeys

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Готовая нить гирлянды: картинка с мишурой, позиции лампочек и бликов для анимации. */
class StrandArt(val bmp: Bitmap, val bulbs: FloatArray, val glints: FloatArray)

/** Рисует подробную мишуру вдоль любой ломаной линии: скрученная проволока, пушистые двухцветные ворсинки, шарики и блики. */
object Tinsel {
    // семейства цветов мишуры: тёмный, основной, светлый тон
    private val FAMILIES = arrayOf(
        intArrayOf(0xFFB8860B.toInt(), 0xFFFFC107.toInt(), 0xFFFFF59D.toInt()),   // золото
        intArrayOf(0xFF9B1C1C.toInt(), 0xFFE53935.toInt(), 0xFFFF8A80.toInt()),   // красный
        intArrayOf(0xFF1B6B2A.toInt(), 0xFF43A047.toInt(), 0xFFA5D6A7.toInt()),   // зелёный
        intArrayOf(0xFF78909C.toInt(), 0xFFCFD8DC.toInt(), 0xFFFFFFFF.toInt())    // серебро
    )
    private val BALLS = intArrayOf(
        0xFFE53935.toInt(), 0xFFFFC107.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt()
    )

    /**
     * pts: ломаная (x0, y0, x1, y1, ...) в координатах картинки w x h.
     * hangX, hangY: куда свисают лампочки и шарики (в пикселях).
     */
    fun build(
        w: Int, h: Int, pts: FloatArray, d: Float, seed: Int,
        hangX: Float, hangY: Float, ornaments: Boolean
    ): StrandArt {
        val bmp = Bitmap.createBitmap(maxOf(1, w), maxOf(1, h), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeCap = Paint.Cap.ROUND
        val rnd = java.util.Random(seed.toLong())
        val n = pts.size / 2
        if (n < 2) return StrandArt(bmp, FloatArray(0), FloatArray(0))

        // накопленная длина вдоль линии
        val cum = FloatArray(n)
        for (i in 1 until n) {
            val dx = pts[2 * i] - pts[2 * i - 2]
            val dy = pts[2 * i + 1] - pts[2 * i - 1]
            cum[i] = cum[i - 1] + Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }
        val total = cum[n - 1]

        // точка и направление на расстоянии s (s растёт монотонно, пока не сбросим seg)
        var seg = 1
        val o = FloatArray(4)
        fun at(s: Float) {
            while (seg < n - 1 && cum[seg] < s) seg++
            val s0 = cum[seg - 1]
            val s1 = cum[seg]
            val t = if (s1 > s0) (s - s0) / (s1 - s0) else 0f
            val x0 = pts[2 * seg - 2]
            val y0 = pts[2 * seg - 1]
            val x1 = pts[2 * seg]
            val y1 = pts[2 * seg + 1]
            o[0] = x0 + (x1 - x0) * t
            o[1] = y0 + (y1 - y0) * t
            val l = Math.sqrt(((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)).toDouble()).toFloat()
            if (l > 0f) {
                o[2] = (x1 - x0) / l
                o[3] = (y1 - y0) / l
            } else {
                o[2] = 1f
                o[3] = 0f
            }
        }

        // 1. скрученная проволока: два оттенка зелёного чередуются
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.2f * d
        var s = 0f
        var px = pts[0]
        var py = pts[1]
        var k = 0
        while (s < total) {
            s = minOf(total, s + 3f * d)
            at(s)
            p.color = if (k % 2 == 0) 0xFF1B5E20.toInt() else 0xFF2E7D32.toInt()
            c.drawLine(px, py, o[0], o[1], p)
            px = o[0]
            py = o[1]
            k++
        }

        // 2. пушистые ворсинки: у основания тёмные, к кончику светлые, местами блестящие кончики
        seg = 1
        s = 0f
        val step = 1.5f * d
        while (s <= total) {
            at(s)
            val nx = -o[3]
            val ny = o[2]
            val twist = (s / (34f * d)).toInt() + seed
            for (side in 0..1) {
                val sgn = if (side == 0) 1f else -1f
                val famIdx = if (rnd.nextFloat() < 0.18f) (twist + 1 + rnd.nextInt(3)) % 4 else twist % 4
                val fam = FAMILIES[famIdx]
                val ang = ((12f + rnd.nextFloat() * 58f) * 0.0174533f).toDouble()
                val lean = if (rnd.nextBoolean()) 1f else -1f
                val cs = Math.cos(ang).toFloat()
                val sn = Math.sin(ang).toFloat()
                val dirx = nx * sgn * cs + o[2] * lean * sn
                val diry = ny * sgn * cs + o[3] * lean * sn
                val len = (3.6f + rnd.nextFloat() * 3.8f + 1.1f * Math.sin((s / (9f * d)).toDouble()).toFloat()) * d
                val mx = o[0] + dirx * len * 0.45f
                val my = o[1] + diry * len * 0.45f
                val tx = o[0] + dirx * len
                val ty = o[1] + diry * len
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.5f * d
                p.color = fam[0]
                c.drawLine(o[0], o[1], mx, my, p)
                p.strokeWidth = 1.2f * d
                p.color = if (rnd.nextFloat() < 0.35f) fam[2] else fam[1]
                c.drawLine(mx, my, tx, ty, p)
                if (rnd.nextFloat() < 0.08f) {
                    p.style = Paint.Style.FILL
                    p.color = 0xFFFFFFFF.toInt()
                    c.drawCircle(tx, ty, 0.8f * d, p)
                }
            }
            s += step
        }

        // 3. цоколи лампочек и ёлочные шарики на ниточках
        val bulbs = ArrayList<Float>()
        seg = 1
        var pos = 13f * d
        k = 0
        while (pos < total) {
            at(pos)
            val wx = o[0]
            val wy = o[1]
            if (ornaments && k % 3 == 2) {
                val bx = wx + hangX * 1.5f
                val by = wy + hangY * 1.5f
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.1f * d
                p.color = 0xFFFFD54F.toInt()
                c.drawLine(wx, wy, bx, by, p)
                p.style = Paint.Style.FILL
                p.color = BALLS[(k / 3) % BALLS.size]
                c.drawCircle(bx, by, 4f * d, p)
                p.color = 0x33000000
                c.drawCircle(bx + 1.0f * d, by + 1.0f * d, 3.0f * d, p)
                p.color = 0xB3FFFFFF.toInt()
                c.drawCircle(bx - 1.4f * d, by - 1.4f * d, 1.1f * d, p)
                p.color = 0xFFFFD54F.toInt()
                c.drawCircle(wx + hangX * 0.35f, wy + hangY * 0.35f, 1.1f * d, p)
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3.2f * d
                p.color = 0xFF263238.toInt()
                c.drawLine(wx, wy, wx + hangX * 0.42f, wy + hangY * 0.42f, p)
                bulbs.add(wx + hangX)
                bulbs.add(wy + hangY)
            }
            pos += 26f * d
            k++
        }

        // 4. места, где мишура будет поблёскивать
        val glints = ArrayList<Float>()
        seg = 1
        var gs = 6f * d
        while (gs < total) {
            at(gs)
            val off = (rnd.nextFloat() - 0.5f) * 6f * d
            glints.add(o[0] - o[3] * off)
            glints.add(o[1] + o[2] * off)
            gs += (18f + rnd.nextFloat() * 14f) * d
        }
        return StrandArt(bmp, bulbs.toFloatArray(), glints.toFloatArray())
    }
}
