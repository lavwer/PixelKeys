package com.pixelkeys

/**
 * Размытие «скользящим окном» в три прохода (по виду почти как гауссово).
 * Время работы не зависит от радиуса, поэтому быстро даже при сильном размытии.
 */
object Blur {

    /** pix = пиксели ARGB (ширина w, высота h), меняется на месте. r = радиус в пикселях. */
    fun box(pix: IntArray, w: Int, h: Int, r: Int) {
        if (r < 1 || w < 1 || h < 1) return
        val tmp = IntArray(pix.size)
        repeat(3) {
            pass(pix, tmp, w, h, r, true)
            pass(tmp, pix, w, h, r, false)
        }
    }

    private fun clamp(i: Int, len: Int) = if (i < 0) 0 else if (i >= len) len - 1 else i

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val stride = if (horizontal) 1 else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[base + clamp(i, len) * stride]
                sr += (p shr 16) and 255
                sg += (p shr 8) and 255
                sb += p and 255
            }
            for (x in 0 until len) {
                dst[base + x * stride] = (255 shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val a = src[base + clamp(x + r + 1, len) * stride]
                val s = src[base + clamp(x - r, len) * stride]
                sr += ((a shr 16) and 255) - ((s shr 16) and 255)
                sg += ((a shr 8) and 255) - ((s shr 8) and 255)
                sb += (a and 255) - (s and 255)
            }
        }
    }
}
