package com.example.retrocamera

/**
 * 텍스처: 미세한 질감. 원본과 아주 작게 흐린 것(base)의 차이를 더하면(+) 질감이 살고, 빼면(-) 부드러워진다.
 * 명료도(넓은 영역의 중간톤 대비)와 달리 반경이 작고 경계 보호가 없다. 프리뷰 셰이더(LutRenderer)와 같은 수식이다.
 *   결과 = 원본 + (원본 - base) * amount
 */
object Texture {
    fun applyArgb(px: IntArray, base: IntArray, from: Int, to: Int, amount: Float) {
        if (amount == 0f) return
        for (i in from until to) {
            val c = px[i]
            val b = base[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val bl = c and 0xFF
            px[i] = (c and 0xFF000000.toInt()) or
                (clamp(r + (r - ((b shr 16) and 0xFF)) * amount) shl 16) or
                (clamp(g + (g - ((b shr 8) and 0xFF)) * amount) shl 8) or
                clamp(bl + (bl - (b and 0xFF)) * amount)
        }
    }

    private fun clamp(v: Float): Int {
        val i = (v + 0.5f).toInt()
        return if (i < 0) 0 else if (i > 255) 255 else i
    }
}
