package com.example.retrocamera

import com.example.retrocamera.data.FilmAdjust
import kotlin.math.abs
import kotlin.math.pow

/**
 * 라이트룸 방식 보정값(노출·대비·밝은/어두운 영역·흰색/검정·색온도·색조·생동감·채도·디헤이즈)을 색 변환 하나로 계산하고,
 * 그 변환을 33^3 3D LUT로 굽는다. LUT 하나라서 필름 스트립 칩·프리뷰·촬영본·편집 화면이 똑같은 색을 낸다.
 * (텍스처·명료도는 이웃 픽셀이 필요한 세부 처리라 LUT가 아니라 Film.texture / Film.clarity로 따로 적용한다.)
 * Android 의존성이 없어 JVM 단위 테스트가 된다.
 */
object FilmLook {

    const val SIZE = 33

    /** c[0..2] = sRGB 0..1 입력을 보정한 값으로 제자리에서 바꾼다 */
    fun apply(a: FilmAdjust, c: FloatArray) {
        if (a.isNeutral) return
        // 1) 선형광에서 화이트 밸런스와 노출. 색온도 +는 따뜻하게(빨강↑ 파랑↓), 색조 +는 자홍 쪽(초록↓)
        val t = a.temperature / 100f
        val k = a.tint / 100f
        val e = 2f.pow(a.exposure)
        var r = toLinear(c[0]) * (1f + 0.30f * t + 0.06f * k) * e
        var g = toLinear(c[1]) * (1f - 0.22f * k) * e
        var b = toLinear(c[2]) * (1f - 0.30f * t + 0.06f * k) * e

        // 2) 다시 감마 공간으로(디스플레이 값). 이후 톤 조정은 사람이 보는 밝기 기준
        r = toSrgb(r).coerceIn(0f, 1f); g = toSrgb(g).coerceIn(0f, 1f); b = toSrgb(b).coerceIn(0f, 1f)

        // 3) 밝은/어두운 영역, 흰색/검정: 밝기(L)에 따른 가중치로 세 채널에 같은 양을 더해 색이 틀어지지 않게 한다
        val l0 = luma(r, g, b)
        val hiW = smooth(0.35f, 0.95f, l0)
        val shW = 1f - smooth(0.05f, 0.65f, l0)
        val whW = l0 * l0 * l0
        val blW = (1f - l0).let { it * it * it }
        val d = a.highlights / 100f * 0.30f * hiW + a.shadows / 100f * 0.30f * shW +
            a.whites / 100f * 0.20f * whW + a.blacks / 100f * 0.20f * blW
        r = (r + d).coerceIn(0f, 1f); g = (g + d).coerceIn(0f, 1f); b = (b + d).coerceIn(0f, 1f)

        // 4) 대비: +는 S자 곡선으로 어두운 곳은 더 어둡게 밝은 곳은 더 밝게, -는 중간 회색 쪽으로 모은다
        val ct = a.contrast / 100f
        r = contrast(r, ct); g = contrast(g, ct); b = contrast(b, ct)

        // 5) 디헤이즈: +는 뿌연 막(밝은 대기광)을 걷어 내고 -는 덧씌운다. +일수록 색도 살아난다
        val dh = a.dehaze / 100f
        var satBoost = 1f
        if (dh != 0f) {
            val air = 0.88f
            if (dh > 0f) {
                val tr = 1f - 0.55f * dh
                r = ((r - air * (1f - tr)) / tr).coerceIn(0f, 1f)
                g = ((g - air * (1f - tr)) / tr).coerceIn(0f, 1f)
                b = ((b - air * (1f - tr)) / tr).coerceIn(0f, 1f)
                satBoost = 1f + 0.25f * dh
            } else {
                val tr = 1f + 0.55f * dh
                r = r * tr + air * (1f - tr); g = g * tr + air * (1f - tr); b = b * tr + air * (1f - tr)
                satBoost = 1f + 0.2f * dh
            }
        }

        // 6) 생동감(채도가 낮은 색을 더 크게)과 채도(전체)
        val l1 = luma(r, g, b)
        val chroma = maxOf(r, g, b) - minOf(r, g, b)
        val vib = 1f + a.vibrance / 100f * (1f - chroma)
        val sat = (1f + a.saturation / 100f) * satBoost * vib
        c[0] = (l1 + (r - l1) * sat).coerceIn(0f, 1f)
        c[1] = (l1 + (g - l1) * sat).coerceIn(0f, 1f)
        c[2] = (l1 + (b - l1) * sat).coerceIn(0f, 1f)
    }

    /** base 필름 LUT(없으면 보정 없는 원본)에 보정을 얹어 새 LUT로 굽는다. R이 가장 빨리 변하는 .cube 순서 */
    fun bake(base: CubeLut?, a: FilmAdjust, size: Int = SIZE): CubeLut {
        val data = FloatArray(size * size * size * 3)
        val c = FloatArray(3)
        val step = 1f / (size - 1)
        var i = 0
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            c[0] = r * step; c[1] = g * step; c[2] = b * step
            if (base != null) sample(base, c)
            apply(a, c)
            data[i++] = c[0]; data[i++] = c[1]; data[i++] = c[2]
        }
        return CubeLut(size, data)
    }

    /** LUT 삼선형 보간: c[0..2]를 읽어 같은 자리에 쓴다 */
    fun sample(lut: CubeLut, c: FloatArray) {
        val n = lut.size
        val d = lut.data
        val fr = c[0].coerceIn(0f, 1f) * (n - 1)
        val fg = c[1].coerceIn(0f, 1f) * (n - 1)
        val fb = c[2].coerceIn(0f, 1f) * (n - 1)
        val ir = minOf(fr.toInt(), n - 2); val ig = minOf(fg.toInt(), n - 2); val ib = minOf(fb.toInt(), n - 2)
        val tr = fr - ir; val tg = fg - ig; val tb = fb - ib
        var o0 = 0f; var o1 = 0f; var o2 = 0f
        for (cb in 0..1) for (cg in 0..1) for (cr in 0..1) {
            val w = (if (cr == 1) tr else 1f - tr) * (if (cg == 1) tg else 1f - tg) * (if (cb == 1) tb else 1f - tb)
            val i = ((ib + cb) * n * n + (ig + cg) * n + (ir + cr)) * 3
            o0 += w * d[i]; o1 += w * d[i + 1]; o2 += w * d[i + 2]
        }
        c[0] = o0; c[1] = o1; c[2] = o2
    }

    private fun luma(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun contrast(v: Float, ct: Float): Float =
        if (ct >= 0f) (v + (v * v * (3f - 2f * v) - v) * ct * 1.1f).coerceIn(0f, 1f)
        else 0.5f + (v - 0.5f) * (1f + 0.6f * ct)

    private fun toLinear(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

    private fun toSrgb(v: Float): Float {
        val x = abs(v)
        return if (x <= 0.0031308f) x * 12.92f else 1.055f * x.pow(1f / 2.4f) - 0.055f
    }
}
