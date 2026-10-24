package com.example.retrocamera

/**
 * 필름 그레인. 위치(x, y)와 seed만으로 값이 정해지는 해시 노이즈라 병렬 처리해도 결과가 같고 테스트할 수 있다.
 * - 중간톤에 가장 강하고 순백/순흑 근처에서는 약해진다(필름의 실제 느낌 + 하이라이트/그림자가 더러워지지 않게).
 * - 대부분은 밝기 노이즈이고 채널별로 조금만 다르게 해서 살짝 컬러 알갱이 느낌을 낸다.
 * - 알갱이 크기는 이미지 폭에 비례해, 12MP에서도 화면에서 보이는 크기가 되게 한다.
 */
object Grain {

    /** 노이즈 진폭(0~1 기준). 0.035 = 최대 약 ±9단계 */
    const val DEFAULT_STRENGTH = 0.035f

    /** 한 알갱이가 차지하는 픽셀 변 길이. 폭 1600px당 1px 수준 */
    fun cellSize(width: Int): Int = maxOf(1, width / 1600)

    /**
     * px는 한 줄이 width인 ARGB 이미지의 일부(startRow 줄부터)이며, [from, to) 구간을 제자리에서 변환한다.
     */
    fun applyArgb(
        px: IntArray, from: Int, to: Int, width: Int, startRow: Int,
        strength: Float = DEFAULT_STRENGTH, seed: Int = 0x5EED,
    ) {
        val cell = cellSize(width)
        for (k in from until to) {
            val x = k % width
            val y = startRow + k / width
            val cx = x / cell
            val cy = y / cell

            val c = px[k]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            val t = 2f * luma - 1f
            val weight = 1f - 0.7f * t * t // 중간톤 1.0 -> 양 끝 0.3

            val base = noise(cx, cy, seed)
            val amp = strength * 255f * weight
            val dr = (base * 0.8f + noise(cx, cy, seed + 1) * 0.2f) * amp
            val dg = (base * 0.8f + noise(cx, cy, seed + 2) * 0.2f) * amp
            val db = (base * 0.8f + noise(cx, cy, seed + 3) * 0.2f) * amp

            px[k] = (c and 0xFF000000.toInt()) or
                (clamp(r + dr) shl 16) or (clamp(g + dg) shl 8) or clamp(b + db)
        }
    }

    /** -1..1, 평균 0인 균일 분포 해시 노이즈 */
    private fun noise(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return ((h and 0xFFFF) / 32767.5f) - 1f
    }

    private fun clamp(v: Float): Int {
        val i = (v + 0.5f).toInt()
        return if (i < 0) 0 else if (i > 255) 255 else i
    }
}
