package com.example.retrocamera

/**
 * 촬영본에 LUT를 적용하는 CPU 구현. 프리뷰 셰이더(3D 텍스처 선형 보간)와 같은 33^3 삼선형 보간이다.
 * Android 의존성이 없어 JVM 단위 테스트로 Python(extract_lut.apply_lut) 결과와 비교할 수 있다.
 */
object LutApplier {

    /**
     * 색감(LUT)은 흐린 이미지(base)에 입히고, 세부/노이즈(원본 - base)는 원본 크기를 넘지 못하게 제한한다.
     * LUT가 대비를 높이는 구간에서도 센서 노이즈가 커지지 않는다(노이즈 배율 <= 1.0).
     *
     * @param px 원본 ARGB, [from, to) 구간을 제자리에서 변환
     * @param base px를 흐리게(박스 블러) 한 것. px와 같은 인덱스를 쓴다.
     */
    fun applyDetailSafe(px: IntArray, base: IntArray, from: Int, to: Int, lut: CubeLut) {
        val t = Tables(lut)
        val cOut = FloatArray(3)
        val bOut = FloatArray(3)
        for (k in from until to) {
            val c = px[k]
            val b = base[k]
            eval(t, c, cOut)
            eval(t, b, bOut)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val bl = c and 0xFF
            val dIn0 = (r - ((b shr 16) and 0xFF)) / 255f
            val dIn1 = (g - ((b shr 8) and 0xFF)) / 255f
            val dIn2 = (bl - (b and 0xFF)) / 255f
            val o0 = bOut[0] + (cOut[0] - bOut[0]).coerceIn(-Math.abs(dIn0), Math.abs(dIn0))
            val o1 = bOut[1] + (cOut[1] - bOut[1]).coerceIn(-Math.abs(dIn1), Math.abs(dIn1))
            val o2 = bOut[2] + (cOut[2] - bOut[2]).coerceIn(-Math.abs(dIn2), Math.abs(dIn2))
            px[k] = (c and 0xFF000000.toInt()) or (to8(o0) shl 16) or (to8(o1) shl 8) or to8(o2)
        }
    }

    private class Tables(val lut: CubeLut) {
        val n = lut.size
        val d = lut.data
        val idx = IntArray(256)
        val frac = FloatArray(256)
        init {
            val scale = (n - 1) / 255f
            for (v in 0..255) {
                val p = v * scale
                var i = p.toInt()
                if (i > n - 2) i = n - 2
                idx[v] = i
                frac[v] = p - i
            }
        }
    }

    /** 한 픽셀의 LUT 결과(0..1 float, 반올림/클램프 전)를 out[0..2]에 쓴다 */
    private fun eval(t: Tables, c: Int, out: FloatArray) {
        val n = t.n
        val d = t.d
        val sr = 3
        val sg = 3 * n
        val sb = 3 * n * n
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val fr = t.frac[r]; val fg = t.frac[g]; val fb = t.frac[b]
        val base = t.idx[r] * sr + t.idx[g] * sg + t.idx[b] * sb
        var o0 = 0f; var o1 = 0f; var o2 = 0f
        for (cb in 0..1) {
            val wb = if (cb == 1) fb else 1f - fb
            for (cg in 0..1) {
                val wg = wb * (if (cg == 1) fg else 1f - fg)
                for (cr in 0..1) {
                    val w = wg * (if (cr == 1) fr else 1f - fr)
                    val i = base + cr * sr + cg * sg + cb * sb
                    o0 += w * d[i]; o1 += w * d[i + 1]; o2 += w * d[i + 2]
                }
            }
        }
        out[0] = o0; out[1] = o1; out[2] = o2
    }

    /** px(ARGB_8888)의 [from, to) 구간을 제자리에서 변환한다. 알파는 유지. */
    fun applyArgb(px: IntArray, from: Int, to: Int, lut: CubeLut) {
        val n = lut.size
        val d = lut.data
        val scale = (n - 1) / 255f

        // 8비트 입력은 256가지뿐이므로 격자 위치/가중치를 미리 계산
        val idx = IntArray(256)
        val frac = FloatArray(256)
        for (v in 0..255) {
            val p = v * scale
            var i = p.toInt()
            if (i > n - 2) i = n - 2
            idx[v] = i
            frac[v] = p - i
        }

        val sr = 3
        val sg = 3 * n
        val sb = 3 * n * n

        for (k in from until to) {
            val c = px[k]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val fr = frac[r]; val fg = frac[g]; val fb = frac[b]
            val base = idx[r] * sr + idx[g] * sg + idx[b] * sb

            var o0 = 0f; var o1 = 0f; var o2 = 0f
            for (cb in 0..1) {
                val wb = if (cb == 1) fb else 1f - fb
                for (cg in 0..1) {
                    val wg = wb * (if (cg == 1) fg else 1f - fg)
                    for (cr in 0..1) {
                        val w = wg * (if (cr == 1) fr else 1f - fr)
                        val i = base + cr * sr + cg * sg + cb * sb
                        o0 += w * d[i]
                        o1 += w * d[i + 1]
                        o2 += w * d[i + 2]
                    }
                }
            }
            px[k] = (c and 0xFF000000.toInt()) or
                (to8(o0) shl 16) or (to8(o1) shl 8) or to8(o2)
        }
    }

    private fun to8(v: Float): Int {
        val i = (v * 255f + 0.5f).toInt()
        return if (i < 0) 0 else if (i > 255) 255 else i
    }
}
